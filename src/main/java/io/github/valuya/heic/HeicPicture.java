/*
 * Openize.HEIC
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of Openize.HEIC.
 *
 * Openize.HEIC is available under Openize license, which is
 * available along with Openize.HEIC sources.
 */

package io.github.valuya.heic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A decoded picture: its samples, at the coded size, and the per-block state that decoding and the in-loop filters
 * read back. Only intra-coded pictures are decoded, so every coding unit is intra.
 */
final class HeicPicture {
    final SeqParameterSetRbsp sps;
    final PicParameterSetRbsp pps;
    private final List<SliceSegmentHeader> sliceSegmentHeaders = new ArrayList<>();

    // the samples of each component, row by row, at [cIdx][y * planeWidths[cIdx] + x]: unsigned bytes up to 8 bits,
    // unsigned shorts above
    final byte[][] planes;
    final short[][] highColorRangePlanes;
    final boolean highColorRange;
    final int[] planeWidths;

    // ScalingFactor[sizeId][matrixId][x][y] (7.4.5) from the PPS scaling list, the SPS one, or the defaults
    final int[][][][] scalingFactor;

    // per coding tree block in raster scan: SliceAddrRs, and the index of the header of its slice segment
    private final int[] sliceAddrRs;
    private final int[] sliceSegmentIndexByCtb;

    // per component and coding tree block in raster scan, SAO_FIELDS values: the sample adaptive offset parameters
    // (7.4.9.3), the offsets signed and not yet scaled
    static final int SAO_TYPE_IDX = 0;
    static final int SAO_EO_CLASS = 1;
    static final int SAO_BAND_POSITION = 2;
    static final int SAO_OFFSETS = 3;
    static final int SAO_FIELDS = SAO_OFFSETS + 4;
    final int[] saoParameters;

    // per 4x4 luma block
    final BlockByteMap ctDepth;
    final BlockByteMap log2CbSize;
    final BlockByteMap qpY;
    final BlockFlagMap pcmFlag;
    final BlockByteMap intraPredModeY;
    final BlockByteMap intraPredModeC;
    final BlockByteMap intraChromaPredMode;
    // one bit per transform tree depth
    final BlockByteMap cbfCb;
    final BlockByteMap cbfCr;
    final BlockByteMap cbfLuma;

    // per 4x4 luma block: LoopFilter.VERTICAL_EDGE, HORIZONTAL_EDGE and NO_FILTER bits
    final BlockByteMap blockFlags;

    // the memory the picture's arrays come from, which its in-loop filters use too
    final DecoderWorkspace workspace;

    HeicPicture(SliceSegmentHeader firstSliceSegmentHeader, DecoderWorkspace workspace) {
        pps = firstSliceSegmentHeader.pps;
        sps = pps.sps;
        this.workspace = workspace;

        // 7.4.3.3: the PPS scaling list, the SPS one, or the default one
        ScalingListData scalingList = Optional.ofNullable(pps.ppsScalingListData)
                .or(() -> Optional.ofNullable(sps.scalingListData))
                .orElse(ScalingListData.DEFAULT);
        scalingFactor = scalingList.getScalingFactor();

        // whole coding tree blocks, so that blocks straddling the right and bottom edges can be reconstructed
        int width = sps.getPicWidthInCtbsY() << sps.getCtbLog2SizeY();
        int height = sps.getPicHeightInCtbsY() << sps.getCtbLog2SizeY();
        int componentCount = sps.getChromaArrayType() != 0 ? 3 : 1;
        int ctbCount = sps.getPicSizeInCtbsY();

        highColorRange = sps.getBitDepthY() > 8 || sps.getBitDepthC() > 8;
        planeWidths = new int[componentCount];
        planes = new byte[highColorRange ? 0 : componentCount][];
        highColorRangePlanes = new short[highColorRange ? componentCount : 0][];
        for (int cIdx = 0; cIdx < componentCount; cIdx++) {
            int planeWidth = cIdx == 0 ? width : width / sps.getSubWidthC();
            int planeHeight = cIdx == 0 ? height : height / sps.getSubHeightC();
            planeWidths[cIdx] = planeWidth;
            int slot = DecoderWorkspace.LUMA_PLANE + cIdx;
            if (highColorRange) {
                highColorRangePlanes[cIdx] = workspace.shorts(slot, planeWidth * planeHeight);
            } else {
                planes[cIdx] = workspace.bytes(slot, planeWidth * planeHeight);
            }
        }

        sliceAddrRs = workspace.ints(DecoderWorkspace.SLICE_ADDR_RS, ctbCount);
        sliceSegmentIndexByCtb = workspace.ints(DecoderWorkspace.SLICE_SEGMENT_INDEX, ctbCount);
        saoParameters = workspace.ints(DecoderWorkspace.SAO_PARAMETERS, componentCount * ctbCount * SAO_FIELDS);

        ctDepth = new BlockByteMap(width, height, workspace, DecoderWorkspace.CT_DEPTH);
        log2CbSize = new BlockByteMap(width, height, workspace, DecoderWorkspace.LOG2_CB_SIZE);
        qpY = new BlockByteMap(width, height, workspace, DecoderWorkspace.QP_Y);
        pcmFlag = new BlockFlagMap(new BlockByteMap(width, height, workspace, DecoderWorkspace.PCM_FLAG));
        intraPredModeY = new BlockByteMap(width, height, workspace, DecoderWorkspace.INTRA_PRED_MODE_Y);
        intraPredModeC = new BlockByteMap(width, height, workspace, DecoderWorkspace.INTRA_PRED_MODE_C);
        intraChromaPredMode = new BlockByteMap(width, height, workspace, DecoderWorkspace.INTRA_CHROMA_PRED_MODE);
        cbfCb = new BlockByteMap(width, height, workspace, DecoderWorkspace.CBF_CB);
        cbfCr = new BlockByteMap(width, height, workspace, DecoderWorkspace.CBF_CR);
        cbfLuma = new BlockByteMap(width, height, workspace, DecoderWorkspace.CBF_LUMA);
        blockFlags = new BlockByteMap(width, height, workspace, DecoderWorkspace.BLOCK_FLAGS);
    }

    /**
     * Adds the header of the next slice segment of the picture.
     *
     * @return its index, to set for its coding tree blocks
     */
    int addSliceSegmentHeader(SliceSegmentHeader header) {
        sliceSegmentHeaders.add(header);
        return sliceSegmentHeaders.size() - 1;
    }

    void setSliceSegmentIndex(int ctbAddrRs, int sliceSegmentIndex) {
        sliceSegmentIndexByCtb[ctbAddrRs] = sliceSegmentIndex;
    }

    void setSliceAddrRs(int ctbAddrRs, int sliceAddress) {
        sliceAddrRs[ctbAddrRs] = sliceAddress;
    }

    /** SliceAddrRs of the slice that holds the coding tree block {@code ctbAddrRs}. */
    int getSliceAddrRs(int ctbAddrRs) {
        return sliceAddrRs[ctbAddrRs];
    }

    /** The coding tree block, in raster scan, that holds the luma sample (x, y). */
    int ctbAddrRsAt(int x, int y) {
        int ctbLog2SizeY = sps.getCtbLog2SizeY();
        return (y >> ctbLog2SizeY) * sps.getPicWidthInCtbsY() + (x >> ctbLog2SizeY);
    }

    /** Where the sample adaptive offset parameters of component {@code cIdx} of a coding tree block start. */
    int saoIndex(int cIdx, int ctbAddrRs) {
        return (cIdx * sps.getPicSizeInCtbsY() + ctbAddrRs) * SAO_FIELDS;
    }

    /**
     * The header of the slice segment that holds the luma sample (x, y).
     */
    SliceSegmentHeader getSliceSegmentHeaderAt(int x, int y) {
        return sliceSegmentHeaders.get(sliceSegmentIndexByCtb[ctbAddrRsAt(x, y)]);
    }

    List<SliceSegmentHeader> getSliceSegmentHeaders() {
        return sliceSegmentHeaders;
    }

    Optional<SliceSegmentHeader> getLastSliceHeaderOptional() {
        return sliceSegmentHeaders.isEmpty()
                ? Optional.empty()
                : Optional.of(sliceSegmentHeaders.get(sliceSegmentHeaders.size() - 1));
    }

    int getSample(int cIdx, int x, int y) {
        int index = y * planeWidths[cIdx] + x;
        return highColorRange ? highColorRangePlanes[cIdx][index] & 0xFFFF : planes[cIdx][index] & 0xFF;
    }

    void setSample(int cIdx, int x, int y, int value) {
        int index = y * planeWidths[cIdx] + x;
        if (highColorRange) {
            highColorRangePlanes[cIdx][index] = (short) value;
        } else {
            planes[cIdx][index] = (byte) value;
        }
    }

    /** Sets the {@code size} x {@code size} block of component {@code cIdx} at (x0, y0) to {@code samples}, row by row. */
    void setBlock(int cIdx, int x0, int y0, int size, int[] samples) {
        int planeWidth = planeWidths[cIdx];
        for (int y = 0; y < size; y++) {
            int planeIndex = (y0 + y) * planeWidth + x0;
            int blockIndex = y * size;
            if (highColorRange) {
                short[] plane = highColorRangePlanes[cIdx];
                for (int x = 0; x < size; x++) {
                    plane[planeIndex + x] = (short) samples[blockIndex + x];
                }
            } else {
                byte[] plane = planes[cIdx];
                for (int x = 0; x < size; x++) {
                    plane[planeIndex + x] = (byte) samples[blockIndex + x];
                }
            }
        }
    }

    /**
     * Sets the {@code size} x {@code size} block of component {@code cIdx} at (x0, y0) to its prediction plus its
     * residual, both row by row, clipped to the bit depth (8.6.7).
     */
    void setReconstructedBlock(int cIdx, int x0, int y0, int size, int[] predSamples, int[] residual) {
        int planeWidth = planeWidths[cIdx];
        int maximum = (1 << (cIdx == 0 ? sps.getBitDepthY() : sps.getBitDepthC())) - 1;
        for (int y = 0; y < size; y++) {
            int planeIndex = (y0 + y) * planeWidth + x0;
            int blockIndex = y * size;
            if (highColorRange) {
                short[] plane = highColorRangePlanes[cIdx];
                for (int x = 0; x < size; x++) {
                    int sample = predSamples[blockIndex + x] + residual[blockIndex + x];
                    plane[planeIndex + x] = (short) Math.max(0, Math.min(maximum, sample));
                }
            } else {
                byte[] plane = planes[cIdx];
                for (int x = 0; x < size; x++) {
                    int sample = predSamples[blockIndex + x] + residual[blockIndex + x];
                    plane[planeIndex + x] = (byte) Math.max(0, Math.min(maximum, sample));
                }
            }
        }
    }

    // 6.4.1 Derivation process for z-scan order block availability: whether the block covering the luma location
    // (xNbY, yNbY) is available to the block at (xCurr, yCurr)
    boolean checkZScanAvailability(int xCurr, int yCurr, int xNbY, int yNbY) {
        if (xNbY < 0 || yNbY < 0 || xNbY >= sps.picWidthInLumaSamples || yNbY >= sps.picHeightInLumaSamples) {
            return false;
        }

        int minTbLog2SizeY = sps.getMinTbLog2SizeY();
        int minBlockAddrCurr = pps.minTbAddrZs(xCurr >> minTbLog2SizeY, yCurr >> minTbLog2SizeY);
        int minBlockAddrN = pps.minTbAddrZs(xNbY >> minTbLog2SizeY, yNbY >> minTbLog2SizeY);
        if (minBlockAddrN > minBlockAddrCurr) {
            return false;
        }

        int ctbAddrCurr = ctbAddrRsAt(xCurr, yCurr);
        int ctbAddrN = ctbAddrRsAt(xNbY, yNbY);
        // a coding tree block is in one slice and one tile
        if (ctbAddrN == ctbAddrCurr) {
            return true;
        }
        if (sliceAddrRs[ctbAddrCurr] != sliceAddrRs[ctbAddrN]) {
            return false;
        }

        int[] tileIdFromRs = pps.getTileIdFromRs();
        return tileIdFromRs[ctbAddrCurr] == tileIdFromRs[ctbAddrN];
    }

    /**
     * {@link #checkZScanAvailability} for a neighbour left of or above a block whose size is a power of 2 and whose
     * position is a multiple of it: the aligned block of its size that holds the neighbour precedes it in z-scan order,
     * so the neighbour is available when it is in the picture, the slice and the tile.
     */
    boolean isAvailableLeftOrAbove(int xCurr, int yCurr, int xNbY, int yNbY) {
        if (xNbY < 0 || yNbY < 0) {
            return false;
        }
        int ctbAddrCurr = ctbAddrRsAt(xCurr, yCurr);
        int ctbAddrN = ctbAddrRsAt(xNbY, yNbY);
        // a coding tree block is in one slice and one tile
        if (ctbAddrN == ctbAddrCurr) {
            return true;
        }
        if (sliceAddrRs[ctbAddrCurr] != sliceAddrRs[ctbAddrN]) {
            return false;
        }
        int[] tileIdFromRs = pps.getTileIdFromRs();
        return tileIdFromRs[ctbAddrCurr] == tileIdFromRs[ctbAddrN];
    }

    void setCtDepth(int x0, int y0, int log2CbSize, int cqtDepth) {
        ctDepth.fill(x0, y0, 1 << log2CbSize, cqtDepth);
    }

    void setPcmFlag(int x0, int y0, int nCbS, boolean value) {
        pcmFlag.fill(x0, y0, nCbS, value);
    }

    void setQpY(int x0, int y0, int log2CbSize, int qpYValue) {
        qpY.fill(x0, y0, 1 << log2CbSize, qpYValue);
    }

    void setLog2CbSize(int x0, int y0, int log2CbSizeValue) {
        log2CbSize.fill(x0, y0, 1 << log2CbSizeValue, log2CbSizeValue);
    }

    // Marks the left and top edges of a transform (or coding) block for the deblocking filter
    void markTransformBlock(int x0, int y0, int size) {
        int blockCount = Math.max(size >> 2, 1);
        for (int i = 0; i < blockCount; i++) {
            blockFlags.setBits(x0, y0 + (i << 2), LoopFilter.VERTICAL_EDGE);
            blockFlags.setBits(x0 + (i << 2), y0, LoopFilter.HORIZONTAL_EDGE);
        }
    }

    // The deblocking filter and sample adaptive offset leave the samples of the coding unit at (x0, y0) untouched
    void markNoFilter(int x0, int y0, int nCbS) {
        for (int x = x0; x < x0 + nCbS; x += 4) {
            for (int y = y0; y < y0 + nCbS; y += 4) {
                blockFlags.setBits(x, y, LoopFilter.NO_FILTER);
            }
        }
    }

    // Applies the deblocking filter and sample adaptive offset once every slice of the picture is reconstructed
    void applyLoopFilters() {
        LoopFilter.apply(this);
    }

    // 8.4.2 Derivation process for luma intra prediction mode: the candidate modes from the left and above neighbours
    int[] generateIntraPredModeCandidates(int xPb, int yPb, boolean availableA, boolean availableB) {
        int candIntraPredModeA =
                !availableA || pcmFlag.get(xPb - 1, yPb) ? IntraPredMode.INTRA_DC : intraPredModeY.get(xPb - 1, yPb);

        // the neighbour above, outside the current coding tree block, is not used
        int ctbLog2SizeY = sps.getCtbLog2SizeY();
        boolean aboveInCurrentCtb = yPb - 1 >= ((yPb >> ctbLog2SizeY) << ctbLog2SizeY);
        int candIntraPredModeB = !availableB || pcmFlag.get(xPb, yPb - 1) || !aboveInCurrentCtb
                ? IntraPredMode.INTRA_DC
                : intraPredModeY.get(xPb, yPb - 1);

        int[] candModeList = workspace.candModeList;
        if (candIntraPredModeB == candIntraPredModeA) {
            if (candIntraPredModeA < IntraPredMode.INTRA_ANGULAR2) {
                candModeList[0] = IntraPredMode.INTRA_PLANAR;
                candModeList[1] = IntraPredMode.INTRA_DC;
                candModeList[2] = IntraPredMode.INTRA_ANGULAR26;
            } else {
                candModeList[0] = candIntraPredModeA;
                candModeList[1] = 2 + ((candIntraPredModeA + 29) % 32);
                candModeList[2] = 2 + ((candIntraPredModeA - 2 + 1) % 32);
            }
        } else {
            candModeList[0] = candIntraPredModeA;
            candModeList[1] = candIntraPredModeB;
            if (candIntraPredModeA != IntraPredMode.INTRA_PLANAR && candIntraPredModeB != IntraPredMode.INTRA_PLANAR) {
                candModeList[2] = IntraPredMode.INTRA_PLANAR;
            } else if (candIntraPredModeA != IntraPredMode.INTRA_DC && candIntraPredModeB != IntraPredMode.INTRA_DC) {
                candModeList[2] = IntraPredMode.INTRA_DC;
            } else {
                candModeList[2] = IntraPredMode.INTRA_ANGULAR26;
            }
        }
        return candModeList;
    }

    /**
     * Sets IntraPredModeY of the prediction block at (xPb, yPb), from mpm_idx when prev_intra_luma_pred_flag is set,
     * from rem_intra_luma_pred_mode otherwise (8.4.2).
     */
    int setIntraPredModeY(
            int xPb,
            int yPb,
            int pbSize,
            int[] candModeList,
            boolean prevIntraLumaPredFlag,
            int mpmIdxOrRemIntraLumaPredMode) {
        int mode;
        if (prevIntraLumaPredFlag) {
            mode = candModeList[mpmIdxOrRemIntraLumaPredMode];
        } else {
            sortByMode(candModeList, 0, 1);
            sortByMode(candModeList, 0, 2);
            sortByMode(candModeList, 1, 2);

            int modeIndex = mpmIdxOrRemIntraLumaPredMode;
            for (int candidate : candModeList) {
                if (modeIndex >= candidate) {
                    modeIndex++;
                }
            }
            mode = modeIndex;
        }

        intraPredModeY.fill(xPb, yPb, pbSize, mode);
        return mode;
    }

    private static void sortByMode(int[] candModeList, int first, int second) {
        if (candModeList[first] > candModeList[second]) {
            int swapped = candModeList[first];
            candModeList[first] = candModeList[second];
            candModeList[second] = swapped;
        }
    }

    void setIntraPredModeC(int xPb, int yPb, int pbSize, int mode) {
        intraPredModeC.fill(xPb, yPb, pbSize, mode);
    }

    // 8.4.3 Derivation process for chroma intra prediction mode, Table 8-2
    static int deriveIntraPredModeC(int intraLumaPredMode, int intraChromaPredMode) {
        if (intraChromaPredMode == 4) {
            return intraLumaPredMode;
        }

        int candidate = switch (intraChromaPredMode) {
            case 0 -> IntraPredMode.INTRA_PLANAR;
            case 1 -> IntraPredMode.INTRA_ANGULAR26;
            case 2 -> IntraPredMode.INTRA_ANGULAR10;
            default -> IntraPredMode.INTRA_DC;
        };
        return intraLumaPredMode == candidate ? IntraPredMode.INTRA_ANGULAR34 : candidate;
    }
}
