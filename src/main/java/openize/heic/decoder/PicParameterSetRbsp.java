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

public class PicParameterSetRbsp extends NalUnit {
    public final SeqParameterSetRbsp sps;
    final byte ppsPicParameterSetId; // max - 64
    final byte ppsSeqParameterSetId;
    final boolean dependentSliceSegmentsEnabledFlag;
    final boolean outputFlagPresentFlag;
    final byte numExtraSliceHeaderBits;
    final boolean signDataHidingEnabledFlag;
    final boolean cabacInitPresentFlag;
    final /*UInt32*/ long numRefIdxL0DefaultActiveMinus1;
    final /*UInt32*/ long numRefIdxL1DefaultActiveMinus1;
    final int initQpMinus26;
    final boolean constrainedIntraPredFlag;
    final boolean transformSkipEnabledFlag;
    final boolean cuQpDeltaEnabledFlag;
    final int ppsCbQpOffset;
    final int ppsCrQpOffset;
    final boolean ppsSliceChromaQpOffsetsPresentFlag;
    final boolean weightedPredFlag;
    final boolean weightedBipredFlag;
    final boolean transquantBypassEnabledFlag;
    final boolean tilesEnabledFlag;
    final boolean entropyCodingSyncEnabledFlag;
    final boolean ppsLoopFilterAcrossSlicesEnabledFlag;
    final boolean deblockingFilterControlPresentFlag;
    final boolean ppsScalingListDataPresentFlag;
    ScalingListData ppsScalingListData;
    final boolean listsModificationPresentFlag;
    final /*UInt32*/ long log2ParallelMergeLevelMinus2;
    final boolean sliceSegmentHeaderExtensionPresentFlag;
    final boolean ppsExtensionPresentFlag;
    final PpsSccExtension ppsSccExt;
    // pps_3d_extension pps_3d_ext;
    // pps_multilayer_extension pps_multilayer_ext;
    final PpsRangeExtension ppsRangeExt;
    // the width of the i-th tile column in units of CTBs
    private int[] colWidth;
    // the height of the j-th tile row in units of CTBs
    private int[] rowHeight;
    // the location of the i-th tile column boundary in units of CTBs
    private int[] colBd;
    // the location of the j-th tile row boundary in units of CTBs
    private int[] rowBd;
    // the conversion from a CTB address in CTB raster scan of a picture to a CTB address in tile scan
    private int[] ctbAddrRsToTs;
    // the conversion from a CTB address in tile scan to a CTB address in CTB raster scan of a picture
    private int[] ctbAddrTsToRs;
    // the conversion from a CTB address in tile scan to a tile ID
    private int[] tileIdByCtbAddrTs;
    // the conversion from a CTB address in CTB raster scan to a tile ID
    private int[] tileIdFromRs;
    // the width of the i-th tile column in units of luma samples
    //    public /*UInt32*/ long[] ColumnWidthInLumaSamples;
    // the height of the j-th tile row in units of luma samples
    //  public /*UInt32*/ long[] RowHeightInLumaSamples;
    // the conversion from a location (x, y) in units of minimum transform blocks
    // to a transform block address in z-scan order
    private int[][] minTbAddrZs;
    /*UInt32*/ long diffCuQpDeltaDepth;
    /*UInt32*/ long numTileColumnsMinus1;
    /*UInt32*/ long numTileRowsMinus1;
    boolean uniformSpacingFlag;
    /*UInt32*/ long[] columnWidthMinus1;
    /*UInt32*/ long[] rowHeightMinus1;
    boolean loopFilterAcrossTilesEnabledFlag;
    boolean deblockingFilterOverrideEnabledFlag;
    boolean ppsDeblockingFilterDisabledFlag;
    int ppsBetaOffsetDiv2;
    int ppsTcOffsetDiv2;
    boolean ppsRangeExtensionFlag;
    boolean ppsMultilayerExtensionFlag;
    boolean pps3dExtensionFlag;
    boolean ppsSccExtensionFlag;
    byte ppsExtension4bits;
    boolean ppsExtensionDataFlag;

    public PicParameterSetRbsp(BitStreamWithNalSupport stream, /*UInt64*/ long startPosition, int size) {
        super(stream, startPosition, size);

        ppsPicParameterSetId = (byte) stream.readUev(); // ue(v)
        ppsSeqParameterSetId = (byte) stream.readUev(); // ue(v)

        sps = stream.getContext().getSPS().get(ppsSeqParameterSetId & 0xFF);

        dependentSliceSegmentsEnabledFlag = stream.readFlag(); // u(1)
        outputFlagPresentFlag = stream.readFlag(); // u(1)
        numExtraSliceHeaderBits = (byte) stream.read(3); // u(3)
        signDataHidingEnabledFlag = stream.readFlag(); // u(1)
        cabacInitPresentFlag = stream.readFlag(); // u(1)

        numRefIdxL0DefaultActiveMinus1 = stream.readUev(); // ue(v)
        numRefIdxL1DefaultActiveMinus1 = stream.readUev(); // ue(v)
        initQpMinus26 = stream.readSev(); // se(v)
        constrainedIntraPredFlag = stream.readFlag(); // u(1)
        transformSkipEnabledFlag = stream.readFlag(); // u(1)
        cuQpDeltaEnabledFlag = stream.readFlag(); // u(1)
        if (cuQpDeltaEnabledFlag) {
            diffCuQpDeltaDepth = stream.readUev(); // ue(v)
        }
        ppsCbQpOffset = stream.readSev(); // se(v)
        ppsCrQpOffset = stream.readSev(); // se(v)
        ppsSliceChromaQpOffsetsPresentFlag = stream.readFlag(); // u(1)
        weightedPredFlag = stream.readFlag(); // u(1)
        weightedBipredFlag = stream.readFlag(); // u(1)
        transquantBypassEnabledFlag = stream.readFlag(); // u(1)
        tilesEnabledFlag = stream.readFlag(); // u(1)
        entropyCodingSyncEnabledFlag = stream.readFlag(); // u(1)

        if (tilesEnabledFlag) {
            numTileColumnsMinus1 = stream.readUev(); // ue(v)
            numTileRowsMinus1 = stream.readUev(); // ue(v)
            if (numTileColumnsMinus1 >= sps.getPicWidthInCtbsY() || numTileRowsMinus1 >= sps.getPicHeightInCtbsY()) {
                throw new HeicFormatException("%d x %d tiles in a picture of %d x %d CTBs"
                        .formatted(
                                numTileColumnsMinus1 + 1,
                                numTileRowsMinus1 + 1,
                                sps.getPicWidthInCtbsY(),
                                sps.getPicHeightInCtbsY()));
            }
            uniformSpacingFlag = stream.readFlag(); // u(1)

            if (!uniformSpacingFlag) {
                columnWidthMinus1 = new /*UInt32*/ long[(int) (numTileColumnsMinus1 & 0xFFFFFFFFL)];
                rowHeightMinus1 = new /*UInt32*/ long[(int) (numTileRowsMinus1 & 0xFFFFFFFFL)];

                for (int i = 0; i < (numTileColumnsMinus1 & 0xFFFFFFFFL); i++)
                    columnWidthMinus1[i] = stream.readUev(); // ue(v)

                for (int i = 0; i < (numTileRowsMinus1 & 0xFFFFFFFFL); i++)
                    rowHeightMinus1[i] = stream.readUev(); // ue(v)
            }

            loopFilterAcrossTilesEnabledFlag = stream.readFlag(); // u(1)
        }

        ppsLoopFilterAcrossSlicesEnabledFlag = stream.readFlag(); // u(1)
        deblockingFilterControlPresentFlag = stream.readFlag(); // u(1)
        if (deblockingFilterControlPresentFlag) {
            deblockingFilterOverrideEnabledFlag = stream.readFlag(); // u(1)
            ppsDeblockingFilterDisabledFlag = stream.readFlag(); // u(1)
            if (!ppsDeblockingFilterDisabledFlag) {
                ppsBetaOffsetDiv2 = stream.readSev(); // se(v)
                ppsTcOffsetDiv2 = stream.readSev(); // se(v)
            }
        }

        ppsScalingListDataPresentFlag = stream.readFlag(); // u(1)
        if (ppsScalingListDataPresentFlag) {
            ppsScalingListData = new ScalingListData(stream);
        }

        listsModificationPresentFlag = stream.readFlag(); // u(1)
        log2ParallelMergeLevelMinus2 = stream.readUev(); // ue(v)
        sliceSegmentHeaderExtensionPresentFlag = stream.readFlag(); // u(1)

        ppsExtensionPresentFlag = stream.readFlag(); // u(1)
        if (ppsExtensionPresentFlag) {
            ppsRangeExtensionFlag = stream.readFlag(); // u(1)
            ppsMultilayerExtensionFlag = stream.readFlag(); // u(1)
            pps3dExtensionFlag = stream.readFlag(); // u(1)
            ppsSccExtensionFlag = stream.readFlag(); // u(1)
            ppsExtension4bits = (byte) stream.read(4); // u(4)
        }

        ppsRangeExt = ppsRangeExtensionFlag
                ? new PpsRangeExtension(stream, transformSkipEnabledFlag)
                : new PpsRangeExtension();

        if (ppsMultilayerExtensionFlag) {
            /*pps_multilayer_ext =*/ new PpsMultilayerExtension(stream); /* specified in Annex F */
        }

        if (pps3dExtensionFlag) {
            /*pps_3d_ext =*/ new Pps3dExtension(stream); /* specified in Annex I */
        }

        ppsSccExt = ppsSccExtensionFlag ? new PpsSccExtension(stream) : new PpsSccExtension();

        if ((ppsExtension4bits & 0xFF) > 0) {
            while (stream.hasMoreRbspData(getEndPosition())) ppsExtensionDataFlag = stream.readFlag(); // u(1)
        }

        new RbspTrailingBits(stream);
    }

    // The scan conversion tables (6.5.1, 6.5.2) are sized by the picture: built on first use by a decode, after its
    // size has been checked, rather than for every parameter set read
    private void ensureScanTables() {
        if (minTbAddrZs == null) {
            ctbRasterAndTileScan();
            zscanInit();
        }
    }

    int[] getColBd() {
        ensureScanTables();
        return colBd;
    }

    int[] getRowBd() {
        ensureScanTables();
        return rowBd;
    }

    int[] getCtbAddrRsToTs() {
        ensureScanTables();
        return ctbAddrRsToTs;
    }

    int[] getCtbAddrTsToRs() {
        ensureScanTables();
        return ctbAddrTsToRs;
    }

    int[] getTileIdByCtbAddrTs() {
        ensureScanTables();
        return tileIdByCtbAddrTs;
    }

    int[] getTileIdFromRs() {
        ensureScanTables();
        return tileIdFromRs;
    }

    int[][] getMinTbAddrZs() {
        ensureScanTables();
        return minTbAddrZs;
    }

    @Override
    public final String toString() {
        return String.format(
                "NAL Unit PPS \nNumber of l0 l1 refs: %d %d ",
                (numRefIdxL0DefaultActiveMinus1 & 0xFFFFFFFFL) + 1, (numRefIdxL1DefaultActiveMinus1 & 0xFFFFFFFFL) + 1);
    }

    public final /*UInt32*/ long getLog2MinCuQpDeltaSize() {
        return (((sps.getCtbLog2SizeY() & 0xFF) - (diffCuQpDeltaDepth & 0xFFFFFFFFL)) & 0xFFFFFFFFL);
    }

    public final /*UInt32*/ long getLog2MinCuChromaQpOffsetSize() {
        return (((sps.getCtbLog2SizeY() & 0xFF) - (ppsRangeExt.diffCuChromaQpOffsetDepth & 0xFFFFFFFFL)) & 0xFFFFFFFFL);
    }

    public final /*UInt32*/ long getLog2MaxTransformSkipSize() {
        return (((ppsRangeExt.log2MaxTransformSkipBlockSizeMinus2 & 0xFFFFFFFFL) + 2) & 0xFFFFFFFFL);
    }

    public final int getPpsActQpOffsetY() {
        return ppsSccExt.ppsActYQpOffsetPlus5 - 5;
    }

    public final int getPpsActQpOffsetCb() {
        return ppsSccExt.ppsActCbQpOffsetPlus5 - 5;
    }

    public final int getPpsActQpOffsetCr() {
        return ppsSccExt.ppsActCrQpOffsetPlus3 - 3;
    }

    // 6.5.1 CTB raster and tile scanning conversion process
    private void ctbRasterAndTileScan() {
        int picWidthInCtbs = (int) sps.getPicWidthInCtbsY();
        int picHeightInCtbs = (int) sps.getPicHeightInCtbsY();
        int tileColumnCount = (int) numTileColumnsMinus1 + 1;
        int tileRowCount = (int) numTileRowsMinus1 + 1;

        colWidth = tileSizes(tileColumnCount, picWidthInCtbs, columnWidthMinus1);
        rowHeight = tileSizes(tileRowCount, picHeightInCtbs, rowHeightMinus1);
        colBd = tileBoundaries(colWidth);
        rowBd = tileBoundaries(rowHeight);

        int picSizeInCtbs = picWidthInCtbs * picHeightInCtbs;
        ctbAddrRsToTs = new int[picSizeInCtbs];
        ctbAddrTsToRs = new int[picSizeInCtbs];
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

        tileIdByCtbAddrTs = new int[picSizeInCtbs];
        tileIdFromRs = new int[picSizeInCtbs];
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
    }

    // colWidth / rowHeight (6-3, 6-4): uniform, or as coded with the last tile taking what remains
    private int[] tileSizes(int tileCount, int sizeInCtbs, long[] codedSizesMinus1) {
        int[] sizes = new int[tileCount];
        if (uniformSpacingFlag) {
            for (int i = 0; i < tileCount; i++) {
                sizes[i] = (i + 1) * sizeInCtbs / tileCount - i * sizeInCtbs / tileCount;
            }
            return sizes;
        }

        int remainingCtbs = sizeInCtbs;
        for (int i = 0; i < tileCount - 1; i++) {
            sizes[i] = (int) codedSizesMinus1[i] + 1;
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

    // 6.5.2 Z-scan order array initialization process
    private void zscanInit() {
        int picWidthInCtbs = (int) sps.getPicWidthInCtbsY();
        int ctbLog2Size = sps.getCtbLog2SizeY() & 0xFF;
        int minTbLog2Size = sps.getMinTbLog2SizeY() & 0xFF;
        int log2MinTbsPerCtb = ctbLog2Size - minTbLog2Size;
        int xMax = picWidthInCtbs << log2MinTbsPerCtb;
        int yMax = (int) sps.getPicHeightInCtbsY() << log2MinTbsPerCtb;

        minTbAddrZs = new int[xMax][yMax];
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
                minTbAddrZs[x][y] = address;
            }
        }
    }

    /**
     * <p>
     *     Calculation if tile is first (required in 8.6.1)
     * </p>
     */
    public boolean checkIfTileIsFirst(int tileX, int tileY) {
        ensureScanTables();
        for (int i = 0; i <= numTileColumnsMinus1; i++) {
            if (colBd[i] == tileX) {
                for (int k = 0; k <= numTileRowsMinus1; k++) {
                    if (rowBd[k] == tileY) {
                        return true;
                    }
                }

                return false;
            }
        }

        return false;
    }
}
