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

import openize.heic.decoder.io.BitStreamWithNalSupport;
import org.jspecify.annotations.Nullable;

/**
 * pic_parameter_set_rbsp( ) (7.3.2.3), and the scan conversion tables derived from it and its SPS (6.5.1, 6.5.2).
 */
public class PicParameterSetRbsp extends NalUnit {
    // 7.4.3.3.1
    private static final int MAX_PPS_ID = 63;
    private static final int MAX_SPS_ID = 15;

    final SeqParameterSetRbsp sps;
    final int ppsPicParameterSetId;
    final boolean dependentSliceSegmentsEnabledFlag;
    final boolean outputFlagPresentFlag;
    final int numExtraSliceHeaderBits;
    final boolean signDataHidingEnabledFlag;
    final boolean cabacInitPresentFlag;
    final int initQpMinus26;
    final boolean constrainedIntraPredFlag;
    final boolean transformSkipEnabledFlag;
    final boolean cuQpDeltaEnabledFlag;
    final int diffCuQpDeltaDepth;
    final int ppsCbQpOffset;
    final int ppsCrQpOffset;
    final boolean ppsSliceChromaQpOffsetsPresentFlag;
    final boolean weightedPredFlag;
    final boolean weightedBipredFlag;
    final boolean transquantBypassEnabledFlag;
    final boolean tilesEnabledFlag;
    final boolean entropyCodingSyncEnabledFlag;
    private final int numTileColumnsMinus1;
    private final int numTileRowsMinus1;
    private final boolean uniformSpacingFlag;
    // empty unless the tiles are not uniformly spaced
    private final int[] columnWidthMinus1;
    private final int[] rowHeightMinus1;
    final boolean loopFilterAcrossTilesEnabledFlag;
    final boolean ppsLoopFilterAcrossSlicesEnabledFlag;
    final boolean deblockingFilterOverrideEnabledFlag;
    final boolean ppsDeblockingFilterDisabledFlag;
    final int ppsBetaOffsetDiv2;
    final int ppsTcOffsetDiv2;
    final boolean ppsScalingListDataPresentFlag;
    // null unless pps_scaling_list_data_present_flag
    final @Nullable ScalingListData ppsScalingListData;
    final boolean listsModificationPresentFlag;
    final boolean sliceSegmentHeaderExtensionPresentFlag;
    final PpsRangeExtension ppsRangeExt;
    final PpsSccExtension ppsSccExt;

    // Sized by the picture: built on first use by a decode, after its size has been checked, rather than for every
    // parameter set read
    private @Nullable ScanTables scanTables;

    PicParameterSetRbsp(BitStreamWithNalSupport stream, long startPosition, int size) {
        super(stream, startPosition, size);

        ppsPicParameterSetId = stream.readUevAtMost(MAX_PPS_ID);
        int ppsSeqParameterSetId = stream.readUevAtMost(MAX_SPS_ID);
        SeqParameterSetRbsp referencedSps = stream.getContext().getSPS().get(ppsSeqParameterSetId);
        if (referencedSps == null) {
            throw new HeicFormatException(
                    "Picture parameter set %d refers to sequence parameter set %d, which is not defined"
                            .formatted(ppsPicParameterSetId, ppsSeqParameterSetId));
        }
        sps = referencedSps;

        dependentSliceSegmentsEnabledFlag = stream.readFlag();
        outputFlagPresentFlag = stream.readFlag();
        numExtraSliceHeaderBits = stream.read(3);
        signDataHidingEnabledFlag = stream.readFlag();
        cabacInitPresentFlag = stream.readFlag();
        stream.readUev(); // num_ref_idx_l0_default_active_minus1: P and B slices only
        stream.readUev(); // num_ref_idx_l1_default_active_minus1
        initQpMinus26 = stream.readSev();
        constrainedIntraPredFlag = stream.readFlag();
        transformSkipEnabledFlag = stream.readFlag();
        cuQpDeltaEnabledFlag = stream.readFlag();
        int log2DiffMaxMinLumaCodingBlockSize = sps.getCtbLog2SizeY() - sps.getMinCbLog2SizeY();
        diffCuQpDeltaDepth = cuQpDeltaEnabledFlag ? stream.readUevAtMost(log2DiffMaxMinLumaCodingBlockSize) : 0;
        ppsCbQpOffset = stream.readSev();
        ppsCrQpOffset = stream.readSev();
        ppsSliceChromaQpOffsetsPresentFlag = stream.readFlag();
        weightedPredFlag = stream.readFlag();
        weightedBipredFlag = stream.readFlag();
        transquantBypassEnabledFlag = stream.readFlag();
        tilesEnabledFlag = stream.readFlag();
        entropyCodingSyncEnabledFlag = stream.readFlag();

        if (tilesEnabledFlag) {
            numTileColumnsMinus1 = stream.readUevAtMost(sps.getPicWidthInCtbsY() - 1);
            numTileRowsMinus1 = stream.readUevAtMost(sps.getPicHeightInCtbsY() - 1);
            uniformSpacingFlag = stream.readFlag();
            if (uniformSpacingFlag) {
                columnWidthMinus1 = new int[0];
                rowHeightMinus1 = new int[0];
            } else {
                columnWidthMinus1 = readTileSizesMinus1(stream, numTileColumnsMinus1, sps.getPicWidthInCtbsY());
                rowHeightMinus1 = readTileSizesMinus1(stream, numTileRowsMinus1, sps.getPicHeightInCtbsY());
            }
            loopFilterAcrossTilesEnabledFlag = stream.readFlag();
        } else {
            numTileColumnsMinus1 = 0;
            numTileRowsMinus1 = 0;
            uniformSpacingFlag = true;
            columnWidthMinus1 = new int[0];
            rowHeightMinus1 = new int[0];
            loopFilterAcrossTilesEnabledFlag = true;
        }

        ppsLoopFilterAcrossSlicesEnabledFlag = stream.readFlag();
        boolean deblockingFilterControlPresentFlag = stream.readFlag();
        deblockingFilterOverrideEnabledFlag = deblockingFilterControlPresentFlag && stream.readFlag();
        ppsDeblockingFilterDisabledFlag = deblockingFilterControlPresentFlag && stream.readFlag();
        boolean deblockingOffsetsPresent = deblockingFilterControlPresentFlag && !ppsDeblockingFilterDisabledFlag;
        ppsBetaOffsetDiv2 = deblockingOffsetsPresent ? stream.readSev() : 0;
        ppsTcOffsetDiv2 = deblockingOffsetsPresent ? stream.readSev() : 0;

        ppsScalingListDataPresentFlag = stream.readFlag();
        ppsScalingListData = ppsScalingListDataPresentFlag ? new ScalingListData(stream) : null;

        listsModificationPresentFlag = stream.readFlag();
        stream.readUev(); // log2_parallel_merge_level_minus2: merge mode, in P and B slices only
        sliceSegmentHeaderExtensionPresentFlag = stream.readFlag();

        boolean ppsExtensionPresentFlag = stream.readFlag();
        boolean ppsRangeExtensionFlag = false;
        boolean ppsMultilayerExtensionFlag = false;
        boolean pps3dExtensionFlag = false;
        boolean ppsSccExtensionFlag = false;
        if (ppsExtensionPresentFlag) {
            ppsRangeExtensionFlag = stream.readFlag();
            ppsMultilayerExtensionFlag = stream.readFlag();
            pps3dExtensionFlag = stream.readFlag();
            ppsSccExtensionFlag = stream.readFlag();
            // pps_extension_4bits: the extension data after the extensions below is not read
            stream.skipBits(4);
        }
        ppsRangeExt = ppsRangeExtensionFlag
                ? new PpsRangeExtension(stream, sps, transformSkipEnabledFlag)
                : new PpsRangeExtension();
        // pps_multilayer_extension( ) (Annex F) and pps_3d_extension( ) (Annex I) only matter to the layers above the
        // base one, but they would have to be parsed to reach a screen content coding extension
        if ((ppsMultilayerExtensionFlag || pps3dExtensionFlag) && ppsSccExtensionFlag) {
            throw new HeicFormatException(
                    "A PPS with both multilayer or 3D and screen content coding extensions is not supported");
        }
        ppsSccExt = ppsSccExtensionFlag ? new PpsSccExtension(stream) : new PpsSccExtension();
    }

    // column_width_minus1 / row_height_minus1 of all tiles but the last, which takes what remains
    private static int[] readTileSizesMinus1(BitStreamWithNalSupport stream, int count, int pictureSizeInCtbs) {
        int[] sizesMinus1 = new int[count];
        for (int i = 0; i < count; i++) {
            sizesMinus1[i] = stream.readUevAtMost(pictureSizeInCtbs - 1);
        }
        return sizesMinus1;
    }

    @Override
    public final String toString() {
        return "NAL Unit PPS %d of SPS %d".formatted(ppsPicParameterSetId, sps.spsSeqParameterSetId);
    }

    final int getLog2MinCuQpDeltaSize() {
        return sps.getCtbLog2SizeY() - diffCuQpDeltaDepth;
    }

    final int getLog2MinCuChromaQpOffsetSize() {
        return sps.getCtbLog2SizeY() - ppsRangeExt.diffCuChromaQpOffsetDepth;
    }

    final int getLog2MaxTransformSkipSize() {
        return ppsRangeExt.log2MaxTransformSkipBlockSizeMinus2 + 2;
    }

    final int getPpsActQpOffsetY() {
        return ppsSccExt.ppsActYQpOffsetPlus5 - 5;
    }

    final int getPpsActQpOffsetCb() {
        return ppsSccExt.ppsActCbQpOffsetPlus5 - 5;
    }

    final int getPpsActQpOffsetCr() {
        return ppsSccExt.ppsActCrQpOffsetPlus3 - 3;
    }

    /**
     * Whether the CTB at column {@code ctbX} and row {@code ctbY}, in CTBs, is the first of a tile.
     */
    final boolean checkIfTileIsFirst(int ctbX, int ctbY) {
        ScanTables tables = getScanTables();
        return startsTile(tables.colBd(), ctbX) && startsTile(tables.rowBd(), ctbY);
    }

    private static boolean startsTile(int[] tileBoundaries, int ctbPosition) {
        // the last boundary is the end of the picture
        for (int i = 0; i < tileBoundaries.length - 1; i++) {
            if (tileBoundaries[i] == ctbPosition) {
                return true;
            }
        }
        return false;
    }

    final int[] getCtbAddrRsToTs() {
        return getScanTables().ctbAddrRsToTs();
    }

    final int[] getCtbAddrTsToRs() {
        return getScanTables().ctbAddrTsToRs();
    }

    final int[] getTileIdByCtbAddrTs() {
        return getScanTables().tileIdByCtbAddrTs();
    }

    final int[] getTileIdFromRs() {
        return getScanTables().tileIdFromRs();
    }

    final int[][] getMinTbAddrZs() {
        return getScanTables().minTbAddrZs();
    }

    private ScanTables getScanTables() {
        ScanTables tables = scanTables;
        if (tables == null) {
            tables = createScanTables();
            scanTables = tables;
        }
        return tables;
    }

    // 6.5.1 CTB raster and tile scanning conversion process, then 6.5.2 Z-scan order array initialization process
    private ScanTables createScanTables() {
        int picWidthInCtbs = sps.getPicWidthInCtbsY();
        int picHeightInCtbs = sps.getPicHeightInCtbsY();
        int tileColumnCount = numTileColumnsMinus1 + 1;
        int tileRowCount = numTileRowsMinus1 + 1;

        int[] colWidth = tileSizes(tileColumnCount, picWidthInCtbs, columnWidthMinus1);
        int[] rowHeight = tileSizes(tileRowCount, picHeightInCtbs, rowHeightMinus1);
        int[] colBd = tileBoundaries(colWidth);
        int[] rowBd = tileBoundaries(rowHeight);

        int picSizeInCtbs = picWidthInCtbs * picHeightInCtbs;
        int[] ctbAddrRsToTs = new int[picSizeInCtbs];
        int[] ctbAddrTsToRs = new int[picSizeInCtbs];
        for (int ctbAddrRs = 0; ctbAddrRs < picSizeInCtbs; ctbAddrRs++) {
            int tbX = ctbAddrRs % picWidthInCtbs;
            int tbY = ctbAddrRs / picWidthInCtbs;
            int tileX = tileIndex(tbX, colBd);
            int tileY = tileIndex(tbY, rowBd);

            int ctbAddrTs = 0;
            for (int i = 0; i < tileX; i++) {
                ctbAddrTs += rowHeight[tileY] * colWidth[i];
            }
            for (int j = 0; j < tileY; j++) {
                ctbAddrTs += picWidthInCtbs * rowHeight[j];
            }
            ctbAddrTs += (tbY - rowBd[tileY]) * colWidth[tileX] + tbX - colBd[tileX];

            ctbAddrRsToTs[ctbAddrRs] = ctbAddrTs;
            ctbAddrTsToRs[ctbAddrTs] = ctbAddrRs;
        }

        int[] tileIdByCtbAddrTs = new int[picSizeInCtbs];
        int[] tileIdFromRs = new int[picSizeInCtbs];
        int tileIdx = 0;
        for (int j = 0; j < tileRowCount; j++) {
            for (int i = 0; i < tileColumnCount; i++, tileIdx++) {
                for (int y = rowBd[j]; y < rowBd[j + 1]; y++) {
                    for (int x = colBd[i]; x < colBd[i + 1]; x++) {
                        int ctbAddrRs = y * picWidthInCtbs + x;
                        tileIdByCtbAddrTs[ctbAddrRsToTs[ctbAddrRs]] = tileIdx;
                        tileIdFromRs[ctbAddrRs] = tileIdx;
                    }
                }
            }
        }

        return new ScanTables(
                colBd,
                rowBd,
                ctbAddrRsToTs,
                ctbAddrTsToRs,
                tileIdByCtbAddrTs,
                tileIdFromRs,
                minTbAddrZs(ctbAddrRsToTs));
    }

    // colWidth / rowHeight (6-3, 6-4): uniform, or as coded with the last tile taking what remains
    private int[] tileSizes(int tileCount, int sizeInCtbs, int[] codedSizesMinus1) {
        int[] sizes = new int[tileCount];
        if (uniformSpacingFlag) {
            for (int i = 0; i < tileCount; i++) {
                sizes[i] = (i + 1) * sizeInCtbs / tileCount - i * sizeInCtbs / tileCount;
            }
            return sizes;
        }

        int remainingCtbs = sizeInCtbs;
        for (int i = 0; i < tileCount - 1; i++) {
            sizes[i] = codedSizesMinus1[i] + 1;
            remainingCtbs -= sizes[i];
        }
        if (remainingCtbs <= 0) {
            throw new HeicFormatException("Tiles wider or taller than the %d-CTB picture".formatted(sizeInCtbs));
        }
        sizes[tileCount - 1] = remainingCtbs;
        return sizes;
    }

    // colBd / rowBd (6-5, 6-6): where each tile starts, and the end of the last one
    private static int[] tileBoundaries(int[] tileSizes) {
        int[] boundaries = new int[tileSizes.length + 1];
        for (int i = 0; i < tileSizes.length; i++) {
            boundaries[i + 1] = boundaries[i] + tileSizes[i];
        }
        return boundaries;
    }

    private static int tileIndex(int ctbPosition, int[] tileBoundaries) {
        int tileIndex = 0;
        for (int i = 0; i < tileBoundaries.length - 1; i++) {
            if (ctbPosition >= tileBoundaries[i]) {
                tileIndex = i;
            }
        }
        return tileIndex;
    }

    // MinTbAddrZs[x][y]: the z-scan order address of each minimum transform block (6.5.2)
    private int[][] minTbAddrZs(int[] ctbAddrRsToTs) {
        int picWidthInCtbs = sps.getPicWidthInCtbsY();
        int ctbLog2Size = sps.getCtbLog2SizeY();
        int minTbLog2Size = sps.getMinTbLog2SizeY();
        int log2MinTbsPerCtb = ctbLog2Size - minTbLog2Size;
        int xMax = picWidthInCtbs << log2MinTbsPerCtb;
        int yMax = sps.getPicHeightInCtbsY() << log2MinTbsPerCtb;

        int[][] addresses = new int[xMax][yMax];
        for (int y = 0; y < yMax; y++) {
            for (int x = 0; x < xMax; x++) {
                int tbX = (x << minTbLog2Size) >> ctbLog2Size;
                int tbY = (y << minTbLog2Size) >> ctbLog2Size;
                int ctbAddrRs = picWidthInCtbs * tbY + tbX;

                int address = ctbAddrRsToTs[ctbAddrRs] << (log2MinTbsPerCtb * 2);
                for (int i = 0; i < log2MinTbsPerCtb; i++) {
                    int m = 1 << i;
                    address += ((m & x) != 0 ? m * m : 0) + ((m & y) != 0 ? 2 * m * m : 0);
                }
                addresses[x][y] = address;
            }
        }
        return addresses;
    }

    private record ScanTables(
            // the location of each tile column and row boundary, in CTBs
            int[] colBd,
            int[] rowBd,
            int[] ctbAddrRsToTs,
            int[] ctbAddrTsToRs,
            int[] tileIdByCtbAddrTs,
            // the tile ID of each CTB, in CTB raster scan
            int[] tileIdFromRs,
            int[][] minTbAddrZs) {}
}
