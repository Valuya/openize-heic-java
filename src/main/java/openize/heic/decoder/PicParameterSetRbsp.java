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


public class PicParameterSetRbsp extends NalUnit
{
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
    //pps_3d_extension pps_3d_ext;
    //pps_multilayer_extension pps_multilayer_ext;
    final PpsRangeExtension ppsRangeExt;
    // the width of the i-th tile column in units of CTBs
    public /*UInt32*/ long[] colWidth;
    // the height of the j-th tile row in units of CTBs
    public /*UInt32*/ long[] rowHeight;
    // the location of the i-th tile column boundary in units of CTBs
    public /*UInt32*/ long[] colBd;
    // the location of the j-th tile row boundary in units of CTBs
    public /*UInt32*/ long[] rowBd;
    // the conversion from a CTB address in CTB raster scan of a picture to a CTB address in tile scan
    public /*UInt32*/ long[] ctbAddrRsToTs;
    // the conversion from a CTB address in tile scan to a CTB address in CTB raster scan of a picture
    public /*UInt32*/ long[] ctbAddrTsToRs;
    // the conversion from a CTB address in tile scan to a tile ID
    public /*UInt32*/ long[] tileIdByCtbAddrTs;
    // the conversion from a CTB address in CTB raster scan to a tile ID
    public /*UInt32*/ long[] tileIdFromRs;
    // the width of the i-th tile column in units of luma samples
//    public /*UInt32*/ long[] ColumnWidthInLumaSamples;
    // the height of the j-th tile row in units of luma samples
    //  public /*UInt32*/ long[] RowHeightInLumaSamples;
    // the conversion from a location (x, y) in units of minimum transform blocks
    // to a transform block address in z-scan order
    public /*UInt32*/ int[][] minTbAddrZs;
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

    public PicParameterSetRbsp(BitStreamWithNalSupport stream, /*UInt64*/long startPosition, int size)
    {
        super(stream, startPosition, size);

        ppsPicParameterSetId = (byte) stream.readUev();                 // ue(v)
        ppsSeqParameterSetId = (byte) stream.readUev();                 // ue(v)

        sps = stream.getContext()
                    .getSPS()
                    .get(ppsSeqParameterSetId & 0xFF);

        dependentSliceSegmentsEnabledFlag = stream.readFlag();          // u(1)
        outputFlagPresentFlag = stream.readFlag();                       // u(1)
        numExtraSliceHeaderBits = (byte) stream.read(3);                 // u(3)
        signDataHidingEnabledFlag = stream.readFlag();                  // u(1)
        cabacInitPresentFlag = stream.readFlag();                        // u(1)

        numRefIdxL0DefaultActiveMinus1 = stream.readUev();           // ue(v)
        numRefIdxL1DefaultActiveMinus1 = stream.readUev();           // ue(v)
        initQpMinus26 = stream.readSev();                                // se(v)
        constrainedIntraPredFlag = stream.readFlag();                    // u(1)
        transformSkipEnabledFlag = stream.readFlag();                    // u(1)
        cuQpDeltaEnabledFlag = stream.readFlag();                       // u(1)
        if (cuQpDeltaEnabledFlag)
        {
            diffCuQpDeltaDepth = stream.readUev();                     // ue(v)
        }
        ppsCbQpOffset = stream.readSev();                               // se(v)
        ppsCrQpOffset = stream.readSev();                               // se(v)
        ppsSliceChromaQpOffsetsPresentFlag = stream.readFlag();       // u(1)
        weightedPredFlag = stream.readFlag();                             // u(1)
        weightedBipredFlag = stream.readFlag();                           // u(1)
        transquantBypassEnabledFlag = stream.readFlag();                 // u(1)
        tilesEnabledFlag = stream.readFlag();                             // u(1)
        entropyCodingSyncEnabledFlag = stream.readFlag();               // u(1)

        if (tilesEnabledFlag)
        {
            numTileColumnsMinus1 = stream.readUev();                    // ue(v)
            numTileRowsMinus1 = stream.readUev();                       // ue(v)
            uniformSpacingFlag = stream.readFlag();                       // u(1)

            if (!uniformSpacingFlag)
            {
                columnWidthMinus1 = new /*UInt32*/long[(int) (numTileColumnsMinus1 & 0xFFFFFFFFL)];
                rowHeightMinus1 = new /*UInt32*/long[(int) (numTileRowsMinus1 & 0xFFFFFFFFL)];

                for (int i = 0; i < (numTileColumnsMinus1 & 0xFFFFFFFFL); i++)
                    columnWidthMinus1[i] = stream.readUev();             // ue(v)

                for (int i = 0; i < (numTileRowsMinus1 & 0xFFFFFFFFL); i++)
                    rowHeightMinus1[i] = stream.readUev();               // ue(v)
            }

            loopFilterAcrossTilesEnabledFlag = stream.readFlag();      // u(1)
        }

        ppsLoopFilterAcrossSlicesEnabledFlag = stream.readFlag();     // u(1)
        deblockingFilterControlPresentFlag = stream.readFlag();         // u(1)
        if (deblockingFilterControlPresentFlag)
        {
            deblockingFilterOverrideEnabledFlag = stream.readFlag();    // u(1)
            ppsDeblockingFilterDisabledFlag = stream.readFlag();        // u(1)
            if (!ppsDeblockingFilterDisabledFlag)
            {
                ppsBetaOffsetDiv2 = stream.readSev();                   // se(v)
                ppsTcOffsetDiv2 = stream.readSev();                     // se(v)
            }
        }

        ppsScalingListDataPresentFlag = stream.readFlag();             // u(1)
        if (ppsScalingListDataPresentFlag)
        {
            ppsScalingListData = new ScalingListData(stream);
        }

        listsModificationPresentFlag = stream.readFlag();                // u(1)
        log2ParallelMergeLevelMinus2 = stream.readUev();               // ue(v)
        sliceSegmentHeaderExtensionPresentFlag = stream.readFlag();    // u(1)

        ppsExtensionPresentFlag = stream.readFlag();                     // u(1)
        if (ppsExtensionPresentFlag)
        {
            ppsRangeExtensionFlag = stream.readFlag();                   // u(1)
            ppsMultilayerExtensionFlag = stream.readFlag();              // u(1)
            pps3dExtensionFlag = stream.readFlag();                      // u(1)
            ppsSccExtensionFlag = stream.readFlag();                     // u(1)
            ppsExtension4bits = (byte) stream.read(4);                     // u(4)
        }

        ppsRangeExt = ppsRangeExtensionFlag ? new PpsRangeExtension(stream, transformSkipEnabledFlag) : new PpsRangeExtension();

        if (ppsMultilayerExtensionFlag)
        {
            /*pps_multilayer_ext =*/
            new PpsMultilayerExtension(stream); /* specified in Annex F */
        }

        if (pps3dExtensionFlag)
        {
            /*pps_3d_ext =*/
            new Pps3dExtension(stream); /* specified in Annex I */
        }

        ppsSccExt = ppsSccExtensionFlag ? new PpsSccExtension(stream) : new PpsSccExtension();

        if ((ppsExtension4bits & 0xFF) > 0)
        {
            while (stream.hasMoreRbspData(getEndPosition()))
                ppsExtensionDataFlag = stream.readFlag();         // u(1)
        }

        new RbspTrailingBits(stream);

        ctbRasterAndTileScan();
        zscanInit();
    }

    @Override
    public final String toString()
    {
        return String.format("NAL Unit PPS \nNumber of l0 l1 refs: %d %d ", (numRefIdxL0DefaultActiveMinus1 & 0xFFFFFFFFL) + 1, (numRefIdxL1DefaultActiveMinus1 & 0xFFFFFFFFL) + 1);
    }

    public final /*UInt32*/long getLog2MinCuQpDeltaSize()
    {
        return (((sps.getCtbLog2SizeY() & 0xFF) - (diffCuQpDeltaDepth & 0xFFFFFFFFL)) & 0xFFFFFFFFL);
    }

    public final /*UInt32*/long getLog2MinCuChromaQpOffsetSize()
    {
        return (((sps.getCtbLog2SizeY() & 0xFF) - (ppsRangeExt.diffCuChromaQpOffsetDepth & 0xFFFFFFFFL)) & 0xFFFFFFFFL);
    }

    public final /*UInt32*/long getLog2MaxTransformSkipSize()
    {
        return (((ppsRangeExt.log2MaxTransformSkipBlockSizeMinus2 & 0xFFFFFFFFL) + 2) & 0xFFFFFFFFL);
    }

    public final int getPpsActQpOffsetY()
    {
        return ppsSccExt.ppsActYQpOffsetPlus5 - 5;
    }

    public final int getPpsActQpOffsetCb()
    {
        return ppsSccExt.ppsActCbQpOffsetPlus5 - 5;
    }

    public final int getPpsActQpOffsetCr()
    {
        return ppsSccExt.ppsActCrQpOffsetPlus3 - 3;
    }

    // 6.5.1 CTB raster and tile scanning conversion process
    final void ctbRasterAndTileScan()
    {
        colWidth = new /*UInt32*/long[(int) (((numTileColumnsMinus1 & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL)];
        rowHeight = new /*UInt32*/long[(int) (((numTileRowsMinus1 & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL)];

        if (uniformSpacingFlag)
        {
            for (int i = 0; i <= (numTileColumnsMinus1 & 0xFFFFFFFFL); i++)
            {
                colWidth[i] = ((((i + 1) * (sps.getPicWidthInCtbsY() & 0xFFFFFFFFL)) & 0xFFFFFFFFL) / (((numTileColumnsMinus1 & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL) - ((i * (sps.getPicWidthInCtbsY() & 0xFFFFFFFFL)) & 0xFFFFFFFFL) / (((numTileColumnsMinus1 & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL)) & 0xFFFFFFFFL;
            }

            for (int j = 0; j <= (numTileRowsMinus1 & 0xFFFFFFFFL); j++)
            {
                rowHeight[j] = ((((j + 1) * (sps.getPicHeightInCtbsY() & 0xFFFFFFFFL)) & 0xFFFFFFFFL) / (((numTileRowsMinus1 & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL) - ((j * (sps.getPicHeightInCtbsY() & 0xFFFFFFFFL)) & 0xFFFFFFFFL) / (((numTileRowsMinus1 & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL)) & 0xFFFFFFFFL;
            }
        }
        else
        {
            colWidth[(int) (numTileColumnsMinus1)] = sps.getPicWidthInCtbsY();
            rowHeight[(int) (numTileRowsMinus1)] = sps.getPicHeightInCtbsY();

            for (int i = 0; i < (numTileColumnsMinus1 & 0xFFFFFFFFL); i++)
            {
                colWidth[i] = ((columnWidthMinus1[i] & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL;
                colWidth[(int) (numTileColumnsMinus1)] = ((colWidth[(int) (numTileColumnsMinus1)] & 0xFFFFFFFFL) - (colWidth[i] & 0xFFFFFFFFL)) & 0xFFFFFFFFL;
            }

            for (int j = 0; j < (numTileRowsMinus1 & 0xFFFFFFFFL); j++)
            {
                rowHeight[j] = ((rowHeightMinus1[j] & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL;
                rowHeight[(int) (numTileRowsMinus1)] = ((rowHeight[(int) (numTileRowsMinus1)] & 0xFFFFFFFFL) - (rowHeight[j] & 0xFFFFFFFFL)) & 0xFFFFFFFFL;
            }
        }


        colBd = new /*UInt32*/long[(int) (((numTileColumnsMinus1 & 0xFFFFFFFFL) + 2) & 0xFFFFFFFFL)];
        rowBd = new /*UInt32*/long[(int) (((numTileRowsMinus1 & 0xFFFFFFFFL) + 2) & 0xFFFFFFFFL)];

        colBd[0] = 0;
        rowBd[0] = 0;

        for (int i = 0; i <= (numTileColumnsMinus1 & 0xFFFFFFFFL); i++)
            colBd[i + 1] = ((colBd[i] & 0xFFFFFFFFL) + (colWidth[i] & 0xFFFFFFFFL)) & 0xFFFFFFFFL;

        for (int j = 0; j <= (numTileRowsMinus1 & 0xFFFFFFFFL); j++)
            rowBd[j + 1] = ((rowBd[j] & 0xFFFFFFFFL) + (rowHeight[j] & 0xFFFFFFFFL)) & 0xFFFFFFFFL;


        ctbAddrRsToTs = new /*UInt32*/long[(int) (sps.getPicSizeInCtbsY())];
        ctbAddrTsToRs = new /*UInt32*/long[(int) (sps.getPicSizeInCtbsY())];
        tileIdByCtbAddrTs = new /*UInt32*/long[(int) (sps.getPicSizeInCtbsY())];
        tileIdFromRs = new /*UInt32*/long[(int) (sps.getPicSizeInCtbsY())];

        /*UInt32*/
        long tbX, tbY;
        /*UInt32*/
        long tileX = 0;
        /*UInt32*/
        long tileY = 0;

        for (/*UInt32*/long ctbAddrRs = 0; (ctbAddrRs & 0xFFFFFFFFL) < (sps.getPicSizeInCtbsY() & 0xFFFFFFFFL); ctbAddrRs++)
        {
            tbX = ((ctbAddrRs & 0xFFFFFFFFL) % (sps.getPicWidthInCtbsY() & 0xFFFFFFFFL)) & 0xFFFFFFFFL;
            tbY = ((ctbAddrRs & 0xFFFFFFFFL) / (sps.getPicWidthInCtbsY() & 0xFFFFFFFFL)) & 0xFFFFFFFFL;

            for (/*UInt32*/long i = 0; (i & 0xFFFFFFFFL) <= (numTileColumnsMinus1 & 0xFFFFFFFFL); i++)
                if ((tbX & 0xFFFFFFFFL) >= (colBd[(int) (i)] & 0xFFFFFFFFL))
                {
                    tileX = i;
                }

            for (/*UInt32*/long j = 0; (j & 0xFFFFFFFFL) <= (numTileRowsMinus1 & 0xFFFFFFFFL); j++)
                if ((tbY & 0xFFFFFFFFL) >= (rowBd[(int) (j)] & 0xFFFFFFFFL))
                {
                    tileY = j;
                }

            ctbAddrRsToTs[(int) (ctbAddrRs)] = 0;

            for (int i = 0; i < (tileX & 0xFFFFFFFFL); i++)
                ctbAddrRsToTs[(int) (ctbAddrRs)] = ((ctbAddrRsToTs[(int) (ctbAddrRs)] & 0xFFFFFFFFL) + (((rowHeight[(int) (tileY)] & 0xFFFFFFFFL) * (colWidth[i] & 0xFFFFFFFFL)) & 0xFFFFFFFFL)) & 0xFFFFFFFFL;

            for (int j = 0; j < (tileY & 0xFFFFFFFFL); j++)
                ctbAddrRsToTs[(int) (ctbAddrRs)] = ((ctbAddrRsToTs[(int) (ctbAddrRs)] & 0xFFFFFFFFL) + (((sps.getPicWidthInCtbsY() & 0xFFFFFFFFL) * (rowHeight[j] & 0xFFFFFFFFL)) & 0xFFFFFFFFL)) & 0xFFFFFFFFL;

            ctbAddrRsToTs[(int) (ctbAddrRs)] = ((ctbAddrRsToTs[(int) (ctbAddrRs)] & 0xFFFFFFFFL) + (((((((((tbY & 0xFFFFFFFFL) - (rowBd[(int) (tileY)] & 0xFFFFFFFFL)) & 0xFFFFFFFFL) * (colWidth[(int) (tileX)] & 0xFFFFFFFFL)) & 0xFFFFFFFFL) + (tbX & 0xFFFFFFFFL)) & 0xFFFFFFFFL) - (colBd[(int) (tileX)] & 0xFFFFFFFFL)) & 0xFFFFFFFFL)) & 0xFFFFFFFFL;

            ctbAddrTsToRs[(int) (ctbAddrRsToTs[(int) (ctbAddrRs)])] = ctbAddrRs;
        }

        for (/*UInt32*/long j = 0, tileIdx = 0; (j & 0xFFFFFFFFL) <= (numTileRowsMinus1 & 0xFFFFFFFFL); j++)
        {
            for (/*UInt32*/long i = 0; (i & 0xFFFFFFFFL) <= (numTileColumnsMinus1 & 0xFFFFFFFFL); i++, tileIdx++)
            {
                for (/*UInt32*/long y = rowBd[(int) (j)]; (y & 0xFFFFFFFFL) < (rowBd[(int) (((j & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL)] & 0xFFFFFFFFL); y++)
                {
                    for (/*UInt32*/long x = colBd[(int) (i)]; (x & 0xFFFFFFFFL) < (colBd[(int) (((i & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL)] & 0xFFFFFFFFL); x++)
                    {
                        tileIdByCtbAddrTs[(int) (ctbAddrRsToTs[(int) (((((y & 0xFFFFFFFFL) * (sps.getPicWidthInCtbsY() & 0xFFFFFFFFL)) & 0xFFFFFFFFL) + (x & 0xFFFFFFFFL)) & 0xFFFFFFFFL)])] = tileIdx;
                        tileIdFromRs[(int) (((((y & 0xFFFFFFFFL) * (sps.getPicWidthInCtbsY() & 0xFFFFFFFFL)) & 0xFFFFFFFFL) + (x & 0xFFFFFFFFL)) & 0xFFFFFFFFL)] = tileIdx;
                    }
                }
            }
        }


//        ColumnWidthInLumaSamples = new /*UInt32*/long[(int) (((num_tile_columns_minus1 & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL)];
//        RowHeightInLumaSamples = new /*UInt32*/long[(int) (((num_tile_rows_minus1 & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL)];
//
//        for (int i = 0; i <= (num_tile_columns_minus1 & 0xFFFFFFFFL); i++)
//            ColumnWidthInLumaSamples[i] = ((colWidth[i] & 0xFFFFFFFFL) << ((sps.getCtbLog2SizeY() & 0xFF) & 0x1F)) & 0xFFFFFFFFL;
//
//        for (int i = 0; i <= (num_tile_rows_minus1 & 0xFFFFFFFFL); i++)
//            RowHeightInLumaSamples[i] = ((rowHeight[i] & 0xFFFFFFFFL) << ((sps.getCtbLog2SizeY() & 0xFF) & 0x1F)) & 0xFFFFFFFFL;
    }

    // 6.5.2 Z-scan order array initialization process
    final void zscanInit()
    {
        /*UInt32*/
        long tbX, tbY, ctbAddrRs, p, m;
        /*UInt32*/
        long xMax = ((sps.getPicWidthInCtbsY() & 0xFFFFFFFFL) << (((sps.getCtbLog2SizeY() & 0xFF) - (sps.getMinTbLog2SizeY() & 0xFF)) & 0x1F)) & 0xFFFFFFFFL;
        /*UInt32*/
        long yMax = ((sps.getPicHeightInCtbsY() & 0xFFFFFFFFL) << (((sps.getCtbLog2SizeY() & 0xFF) - (sps.getMinTbLog2SizeY() & 0xFF)) & 0x1F)) & 0xFFFFFFFFL;
        minTbAddrZs = new /*UInt32*/int[(int) (xMax & 0xFFFFFFFFL)][(int) (yMax & 0xFFFFFFFFL)];

        for (/*UInt32*/long y = 0; (y & 0xFFFFFFFFL) < (yMax & 0xFFFFFFFFL); y++)
        {
            for (/*UInt32*/long x = 0; (x & 0xFFFFFFFFL) < (xMax & 0xFFFFFFFFL); x++)
            {
                tbX = ((((x & 0xFFFFFFFFL) << ((sps.getMinTbLog2SizeY() & 0xFF) & 0x1F)) & 0xFFFFFFFFL) >> (sps.getCtbLog2SizeY() & 0xFF)) & 0xFFFFFFFFL;
                tbY = ((((y & 0xFFFFFFFFL) << ((sps.getMinTbLog2SizeY() & 0xFF) & 0x1F)) & 0xFFFFFFFFL) >> (sps.getCtbLog2SizeY() & 0xFF)) & 0xFFFFFFFFL;
                ctbAddrRs = ((((sps.getPicWidthInCtbsY() & 0xFFFFFFFFL) * (tbY & 0xFFFFFFFFL)) & 0xFFFFFFFFL) + (tbX & 0xFFFFFFFFL)) & 0xFFFFFFFFL;
                minTbAddrZs[(int) (x)][(int) (y)] = (int) ((ctbAddrRsToTs[(int) (ctbAddrRs)] & 0xFFFFFFFFL) << ((((sps.getCtbLog2SizeY() & 0xFF) - (sps.getMinTbLog2SizeY() & 0xFF)) * 2) & 0x1F));

                p = 0;
                for (byte i = 0; (i & 0xFF) < ((sps.getCtbLog2SizeY() & 0xFF) - (sps.getMinTbLog2SizeY() & 0xFF)); i++)
                {
                    m = (1L << (i & 0xFF)) & 0xFFFFFFFFL;
                    p = ((p & 0xFFFFFFFFL) + ((((m & x) & 0xFFFFFFFFL) != 0 ? (((m & 0xFFFFFFFFL) * (m & 0xFFFFFFFFL)) & 0xFFFFFFFFL) : 0) & 0xFFFFFFFFL)) & 0xFFFFFFFFL;
                    p = ((p & 0xFFFFFFFFL) + ((((m & y) & 0xFFFFFFFFL) != 0 ? ((((2 * (m & 0xFFFFFFFFL)) & 0xFFFFFFFFL) * (m & 0xFFFFFFFFL)) & 0xFFFFFFFFL) : 0) & 0xFFFFFFFFL)) & 0xFFFFFFFFL;
                }
                minTbAddrZs[(int) (x)][(int) (y)] = (int) ((minTbAddrZs[(int) (x)][(int) (y)] & 0xFFFFFFFFL) + (p & 0xFFFFFFFFL));
            }
        }
    }

    /**
     * <p>
     *     Calculation if tile is first (required in 8.6.1)
     * </p>
     */
    public boolean checkIfTileIsFirst(int tileX, int tileY)
    {
        for (int i = 0; i <= numTileColumnsMinus1; i++)
        {
            if (colBd[i] == tileX)
            {
                for (int k = 0; k <= numTileRowsMinus1; k++)
                {
                    if (rowBd[k] == tileY)
                    {
                        return true;
                    }
                }

                return false;
            }
        }

        return false;
    }
}
