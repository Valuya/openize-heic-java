/*
 * Openize.HEIC
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of Openize.HEIC.
 *
 * Openize.HEIC is available under Openize license, which is
 * available along with Openize.HEIC sources.
 */

package openize.heic.decoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A decoded picture: its samples, at the coded size, and the per-block state that decoding and the in-loop filters
 * read back. Only intra-coded pictures are decoded, so every coding unit is intra.
 */
public final class HeicPicture {
    final SeqParameterSetRbsp sps;
    final PicParameterSetRbsp pps;
    private final List<SliceSegmentHeader> sliceSegmentHeaders = new ArrayList<>();

    // the samples, [cIdx][x][y]: unsigned bytes up to 8 bits, unsigned shorts above
    final byte[][][] pixels;
    final short[][][] pixelsHighColorRange;
    final boolean highColorRange;

    // ScalingFactor[sizeId][matrixId][x][y] (7.4.5) from the PPS scaling list, the SPS one, or the defaults
    final int[][][][] scalingFactor;

    // per coding tree block: SliceAddrRs, and the sample adaptive offset parameters [cIdx][rx][ry] (7.4.9.3)
    final int[][] sliceAddrRs;
    final int[][][] saoTypeIdx;
    final int[][][] saoEoClass;
    final int[][][] saoBandPosition;
    final int[][][][] saoOffsetAbs;
    final int[][][][] saoOffsetSign;

    // per coding tree block in raster scan: the index of the header of its slice segment
    private final int[] sliceSegmentIndexByCtb;

    // per 4x4 luma block
    final BlockByteMap ctDepth;
    final BlockByteMap log2CbSize;
    final BlockByteMap qpY;
    final BlockFlagMap pcmFlag;
    final BlockEnumMap<IntraPredMode> intraPredModeY;
    final BlockEnumMap<IntraPredMode> intraPredModeC;
    final BlockByteMap intraChromaPredMode;
    // one bit per transform tree depth
    final BlockByteMap cbfCb;
    final BlockByteMap cbfCr;
    final BlockByteMap cbfLuma;

    // per 4x4 luma block: LoopFilter.VERTICAL_EDGE, HORIZONTAL_EDGE and NO_FILTER bits
    final byte[][] blockFlags;

    HeicPicture(SliceSegmentHeader firstSliceSegmentHeader) {
        pps = firstSliceSegmentHeader.pps;
        sps = pps.sps;

        ScalingListData scalingList = pps.ppsScalingListDataPresentFlag
                ? pps.ppsScalingListData
                : sps.spsScalingListDataPresentFlag ? sps.ScalingListData : ScalingListData.DEFAULT;
        scalingFactor = scalingList.getScalingFactor(sps.getChromaArrayType());

        // whole coding tree blocks, so that blocks straddling the right and bottom edges can be reconstructed
        int width = sps.getPicWidthInCtbsY() << sps.getCtbLog2SizeY();
        int height = sps.getPicHeightInCtbsY() << sps.getCtbLog2SizeY();
        int componentCount = sps.getChromaArrayType() != 0 ? 3 : 1;
        int widthInCtbs = sps.getPicWidthInCtbsY();
        int heightInCtbs = sps.getPicHeightInCtbsY();

        highColorRange = sps.getBitDepthY() > 8 || sps.getBitDepthC() > 8;
        pixels = highColorRange ? new byte[0][][] : new byte[componentCount][width][height];
        pixelsHighColorRange = highColorRange ? new short[componentCount][width][height] : new short[0][][];

        sliceAddrRs = new int[widthInCtbs][heightInCtbs];
        saoTypeIdx = new int[componentCount][widthInCtbs][heightInCtbs];
        saoEoClass = new int[componentCount][widthInCtbs][heightInCtbs];
        saoBandPosition = new int[componentCount][widthInCtbs][heightInCtbs];
        saoOffsetAbs = new int[componentCount][widthInCtbs][heightInCtbs][4];
        saoOffsetSign = new int[componentCount][widthInCtbs][heightInCtbs][4];

        sliceSegmentIndexByCtb = new int[widthInCtbs * heightInCtbs];
        ctDepth = new BlockByteMap(width, height);
        log2CbSize = new BlockByteMap(width, height);
        qpY = new BlockByteMap(width, height);
        pcmFlag = new BlockFlagMap(width, height);
        intraPredModeY = new BlockEnumMap<>(IntraPredMode.class, width, height);
        intraPredModeC = new BlockEnumMap<>(IntraPredMode.class, width, height);
        intraChromaPredMode = new BlockByteMap(width, height);
        cbfCb = new BlockByteMap(width, height);
        cbfCr = new BlockByteMap(width, height);
        cbfLuma = new BlockByteMap(width, height);
        blockFlags = new byte[width >> 2][height >> 2];
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

    /**
     * The header of the slice segment that holds the luma sample (x, y).
     */
    SliceSegmentHeader getSliceSegmentHeaderAt(int x, int y) {
        int ctbLog2SizeY = sps.getCtbLog2SizeY();
        int ctbAddrRs = (y >> ctbLog2SizeY) * sps.getPicWidthInCtbsY() + (x >> ctbLog2SizeY);
        return sliceSegmentHeaders.get(sliceSegmentIndexByCtb[ctbAddrRs]);
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
        return highColorRange ? pixelsHighColorRange[cIdx][x][y] & 0xFFFF : pixels[cIdx][x][y] & 0xFF;
    }

    void setSample(int cIdx, int x, int y, int value) {
        if (highColorRange) {
            pixelsHighColorRange[cIdx][x][y] = (short) value;
        } else {
            pixels[cIdx][x][y] = (byte) value;
        }
    }

    // 6.4.1 Derivation process for z-scan order block availability: whether the block covering the luma location
    // (xNbY, yNbY) is available to the block at (xCurr, yCurr)
    boolean checkZScanAvailability(int xCurr, int yCurr, int xNbY, int yNbY) {
        if (xNbY < 0 || yNbY < 0 || xNbY >= sps.picWidthInLumaSamples || yNbY >= sps.picHeightInLumaSamples) {
            return false;
        }

        int minTbLog2SizeY = sps.getMinTbLog2SizeY();
        int[][] minTbAddrZs = pps.getMinTbAddrZs();
        int minBlockAddrCurr = minTbAddrZs[xCurr >> minTbLog2SizeY][yCurr >> minTbLog2SizeY];
        int minBlockAddrN = minTbAddrZs[xNbY >> minTbLog2SizeY][yNbY >> minTbLog2SizeY];
        if (minBlockAddrN > minBlockAddrCurr) {
            return false;
        }

        int ctbLog2SizeY = sps.getCtbLog2SizeY();
        int xCurrCtb = xCurr >> ctbLog2SizeY;
        int yCurrCtb = yCurr >> ctbLog2SizeY;
        int xNbCtb = xNbY >> ctbLog2SizeY;
        int yNbCtb = yNbY >> ctbLog2SizeY;
        if (sliceAddrRs[xCurrCtb][yCurrCtb] != sliceAddrRs[xNbCtb][yNbCtb]) {
            return false;
        }

        int[] tileIdFromRs = pps.getTileIdFromRs();
        int widthInCtbs = sps.getPicWidthInCtbsY();
        return tileIdFromRs[yCurrCtb * widthInCtbs + xCurrCtb] == tileIdFromRs[yNbCtb * widthInCtbs + xNbCtb];
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
            blockFlags[x0 >> 2][(y0 >> 2) + i] |= LoopFilter.VERTICAL_EDGE;
            blockFlags[(x0 >> 2) + i][y0 >> 2] |= LoopFilter.HORIZONTAL_EDGE;
        }
    }

    // The deblocking filter and sample adaptive offset leave the samples of the coding unit at (x0, y0) untouched
    void markNoFilter(int x0, int y0, int nCbS) {
        for (int i = x0 >> 2; i < (x0 + nCbS) >> 2; i++) {
            for (int j = y0 >> 2; j < (y0 + nCbS) >> 2; j++) {
                blockFlags[i][j] |= LoopFilter.NO_FILTER;
            }
        }
    }

    // Applies the deblocking filter and sample adaptive offset once every slice of the picture is reconstructed
    void applyLoopFilters() {
        LoopFilter.apply(this);
    }

    // 8.4.2 Derivation process for luma intra prediction mode: the candidate modes from the left and above neighbours
    IntraPredMode[] generateIntraPredModeCandidates(int xPb, int yPb, boolean availableA, boolean availableB) {
        IntraPredMode candIntraPredModeA =
                !availableA || pcmFlag.get(xPb - 1, yPb) ? IntraPredMode.INTRA_DC : intraPredModeY.get(xPb - 1, yPb);

        // the neighbour above, outside the current coding tree block, is not used
        int ctbLog2SizeY = sps.getCtbLog2SizeY();
        boolean aboveInCurrentCtb = yPb - 1 >= ((yPb >> ctbLog2SizeY) << ctbLog2SizeY);
        IntraPredMode candIntraPredModeB = !availableB || pcmFlag.get(xPb, yPb - 1) || !aboveInCurrentCtb
                ? IntraPredMode.INTRA_DC
                : intraPredModeY.get(xPb, yPb - 1);

        IntraPredMode[] candModeList = new IntraPredMode[3];
        if (candIntraPredModeB == candIntraPredModeA) {
            if (candIntraPredModeA.ordinal() < 2) {
                candModeList[0] = IntraPredMode.INTRA_PLANAR;
                candModeList[1] = IntraPredMode.INTRA_DC;
                candModeList[2] = IntraPredMode.INTRA_ANGULAR26;
            } else {
                candModeList[0] = candIntraPredModeA;
                candModeList[1] = IntraPredMode.get(2 + ((candIntraPredModeA.ordinal() + 29) % 32));
                candModeList[2] = IntraPredMode.get(2 + ((candIntraPredModeA.ordinal() - 2 + 1) % 32));
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
    IntraPredMode setIntraPredModeY(
            int xPb,
            int yPb,
            int pbSize,
            IntraPredMode[] candModeList,
            boolean prevIntraLumaPredFlag,
            int mpmIdxOrRemIntraLumaPredMode) {
        IntraPredMode mode;
        if (prevIntraLumaPredFlag) {
            mode = candModeList[mpmIdxOrRemIntraLumaPredMode];
        } else {
            sortByMode(candModeList, 0, 1);
            sortByMode(candModeList, 0, 2);
            sortByMode(candModeList, 1, 2);

            int modeIndex = mpmIdxOrRemIntraLumaPredMode;
            for (IntraPredMode candidate : candModeList) {
                if (modeIndex >= candidate.ordinal()) {
                    modeIndex++;
                }
            }
            mode = IntraPredMode.get(modeIndex);
        }

        intraPredModeY.fill(xPb, yPb, pbSize, mode);
        return mode;
    }

    private static void sortByMode(IntraPredMode[] candModeList, int first, int second) {
        if (candModeList[first].ordinal() > candModeList[second].ordinal()) {
            IntraPredMode swapped = candModeList[first];
            candModeList[first] = candModeList[second];
            candModeList[second] = swapped;
        }
    }

    void setIntraPredModeC(int xPb, int yPb, int pbSize, IntraPredMode mode) {
        intraPredModeC.fill(xPb, yPb, pbSize, mode);
    }

    // 8.4.3 Derivation process for chroma intra prediction mode, Table 8-2
    static IntraPredMode deriveIntraPredModeC(IntraPredMode intraLumaPredMode, int intraChromaPredMode) {
        if (intraChromaPredMode == 4) {
            return intraLumaPredMode;
        }

        IntraPredMode candidate = switch (intraChromaPredMode) {
            case 0 -> IntraPredMode.INTRA_PLANAR;
            case 1 -> IntraPredMode.INTRA_ANGULAR26;
            case 2 -> IntraPredMode.INTRA_ANGULAR10;
            default -> IntraPredMode.INTRA_DC;
        };
        return intraLumaPredMode == candidate ? IntraPredMode.INTRA_ANGULAR34 : candidate;
    }
}
