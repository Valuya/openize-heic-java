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

import java.util.HashMap;

public class HeicPicture {
    public final HashMap<Integer, SliceSegmentHeader> getSliceHeaders() {
        return sliceHeaders;
    }

    private final HashMap<Integer, SliceSegmentHeader> sliceHeaders;

    public final HashMap<Integer, SliceSegmentData> getSliceUnits() {
        return sliceUnits;
    }

    private final HashMap<Integer, SliceSegmentData> sliceUnits;

    public final VideoParameterSetRbsp vps;
    public final SeqParameterSetRbsp sps;
    public final PicParameterSetRbsp pps;
    public final NalHeader nalHeader = new NalHeader();

    public int picOrderCntVal;
    public int pictureOrderCntLsb;

    public int[][][] saoTypeIdx;
    public int[][][][] saoOffsetVal;
    public int[][][] saoEoClass;

    public int[][][] resScaleVal;

    public int[][][][] saoOffsetAbs;
    public int[][][][] saoOffsetSign;
    public int[][][] saoBandPosition;

    public /*UInt32*/ int[][] sliceAddrRs;
    public int[][] sliceHeaderIndex;
    public IntraPredMode[][] intraPredModeY;
    public IntraPredMode[][] intraPredModeC;

    public int[][][] transCoeffLevel;

    public int[][] ctDepthByPosition;
    public PredMode[][] cuPredModeByPosition;
    public int[][] qpYByPosition;
    public int[][] log2CbSizeByPosition;
    public boolean[][] mergeFlag;

    public boolean[][] cuSkipFlag;
    public boolean[][] paletteModeFlag;
    public boolean[][] pcmFlag;
    public boolean[][] prevIntraLumaPredFlag;
    public int[][] mpmIdx;
    public IntraPredMode[][] remIntraLumaPredMode;
    public byte[][] intraChromaPredMode;

    public boolean[][] tuResidualActFlag;

    // public bool[,][] split_transform_flag;
    public boolean[][][] cbfCb;
    public boolean[][][] cbfCr;
    public boolean[][][] cbfLuma;

    // public bool[,][] transform_skip_flag;
    public boolean[][][] explicitRdpcmFlag;
    public boolean[][][] explicitRdpcmDirFlag;

    public int[] pcmSampleLuma;
    public int[] pcmSampleChroma;

    // per 4x4 luma block: LoopFilter.VERTICAL_EDGE, HORIZONTAL_EDGE and NO_FILTER bits
    public byte[][] blockFlags;

    // ScalingFactor[sizeId][matrixId][x][y] (7.4.5) from the PPS scaling list, the SPS one, or the defaults
    final int[][][][] scalingFactor;

    public /*Byte*/ byte[][][] pixels;
    public /*UInt16*/ int[][][] pixelsHighColorRange;

    public HeicPicture(SliceSegmentHeader sliceHeader, NalHeader nalHeader) {
        sliceHeaders = new HashMap<>();
        sliceUnits = new HashMap<>();

        pps = sliceHeader.pps;
        sps = pps.sps;
        vps = sps.vps;
        nalHeader.cloneTo(this.nalHeader);

        ScalingListData scalingList = pps.ppsScalingListDataPresentFlag
                ? pps.ppsScalingListData
                : sps.spsScalingListDataPresentFlag ? sps.ScalingListData : ScalingListData.DEFAULT;
        scalingFactor = scalingList.getScalingFactor(sps.getChromaArrayType());

        /*UInt32*/ long widthMax;
        /*UInt32*/ long heightMax;

        widthMax = ((sps.getPicWidthInCtbsY() & 0xFFFFFFFFL) << ((sps.getCtbLog2SizeY() & 0xFF) & 0x1F)) & 0xFFFFFFFFL;
        heightMax =
                ((sps.getPicHeightInCtbsY() & 0xFFFFFFFFL) << ((sps.getCtbLog2SizeY() & 0xFF) & 0x1F)) & 0xFFFFFFFFL;

        int chromaCount = (sps.getChromaArrayType() & 0xFFFFFFFFL) != 0 ? 3 : 1;

        saoTypeIdx = new int[chromaCount][][];
        saoEoClass = new int[chromaCount][][];
        resScaleVal = new int[chromaCount][][];
        saoOffsetVal = new int[chromaCount][][][];
        saoOffsetAbs = new int[chromaCount][][][];
        saoOffsetSign = new int[chromaCount][][][];
        saoBandPosition = new int[chromaCount][][];
        transCoeffLevel = new int[chromaCount][][];

        if ((sps.getBitDepthY() & 0xFF) > 8 || (sps.getBitDepthC() & 0xFF) > 8)
            pixelsHighColorRange = new /*UInt16*/ int[chromaCount][][];
        else pixels = new /*Byte*/ byte[chromaCount][][];

        for (int i = 0; i < chromaCount; i++) {
            saoTypeIdx[i] = new int[(int) (sps.getPicWidthInCtbsY())][(int) (sps.getPicHeightInCtbsY())];
            saoEoClass[i] = new int[(int) (sps.getPicWidthInCtbsY())][(int) (sps.getPicHeightInCtbsY())];
            resScaleVal[i] = new int[(int) (sps.getPicWidthInCtbsY())][(int) (sps.getPicHeightInCtbsY())];
            saoOffsetVal[i] = new int[(int) (sps.getPicWidthInCtbsY())][(int) (sps.getPicHeightInCtbsY())][];
            saoOffsetAbs[i] = new int[(int) (sps.getPicWidthInCtbsY())][(int) (sps.getPicHeightInCtbsY())][];
            saoOffsetSign[i] = new int[(int) (sps.getPicWidthInCtbsY())][(int) (sps.getPicHeightInCtbsY())][];
            saoBandPosition[i] = new int[(int) (sps.getPicWidthInCtbsY())][(int) (sps.getPicHeightInCtbsY())];

            transCoeffLevel[i] = new int[(int) (widthMax & 0xFFFFFFFFL)][(int) (heightMax & 0xFFFFFFFFL)];

            if ((sps.getBitDepthY() & 0xFF) > 8 || (sps.getBitDepthC() & 0xFF) > 8)
                pixelsHighColorRange[i] =
                        new /*UInt16*/ int[(int) (widthMax & 0xFFFFFFFFL)][(int) (heightMax & 0xFFFFFFFFL)];
            else pixels[i] = new /*Byte*/ byte[(int) (widthMax & 0xFFFFFFFFL)][(int) (heightMax & 0xFFFFFFFFL)];
        }

        blockFlags = new byte[(int) (widthMax >> 2)][(int) (heightMax >> 2)];
        ctDepthByPosition = new int[(int) (widthMax & 0xFFFFFFFFL)][(int) (heightMax & 0xFFFFFFFFL)];
        cuPredModeByPosition = PredMode.createArray2D((int) (widthMax & 0xFFFFFFFFL), (int) (heightMax & 0xFFFFFFFFL));
        qpYByPosition = new int[(int) (widthMax & 0xFFFFFFFFL)][(int) (heightMax & 0xFFFFFFFFL)];
        log2CbSizeByPosition = new int[(int) (widthMax & 0xFFFFFFFFL)][(int) (heightMax & 0xFFFFFFFFL)];
        intraPredModeY = IntraPredMode.createArray2D((int) (widthMax & 0xFFFFFFFFL), (int) (heightMax & 0xFFFFFFFFL));
        intraPredModeC = IntraPredMode.createArray2D((int) (widthMax & 0xFFFFFFFFL), (int) (heightMax & 0xFFFFFFFFL));

        mergeFlag = new boolean[(int) (widthMax & 0xFFFFFFFFL)][(int) (heightMax & 0xFFFFFFFFL)];
        cuSkipFlag = new boolean[(int) (widthMax & 0xFFFFFFFFL)][(int) (heightMax & 0xFFFFFFFFL)];
        paletteModeFlag = new boolean[(int) (widthMax & 0xFFFFFFFFL)][(int) (heightMax & 0xFFFFFFFFL)];
        pcmFlag = new boolean[(int) (widthMax & 0xFFFFFFFFL)][(int) (heightMax & 0xFFFFFFFFL)];
        prevIntraLumaPredFlag = new boolean[(int) (widthMax & 0xFFFFFFFFL)][(int) (heightMax & 0xFFFFFFFFL)];

        mpmIdx = new int[(int) (widthMax & 0xFFFFFFFFL)][(int) (heightMax & 0xFFFFFFFFL)];
        remIntraLumaPredMode =
                IntraPredMode.createArray2D((int) (widthMax & 0xFFFFFFFFL), (int) (heightMax & 0xFFFFFFFFL));
        intraChromaPredMode = new byte[(int) (widthMax & 0xFFFFFFFFL)][(int) (heightMax & 0xFFFFFFFFL)];

        tuResidualActFlag = new boolean[(int) (widthMax & 0xFFFFFFFFL)][(int) (heightMax & 0xFFFFFFFFL)];

        // split_transform_flag = new bool[xMax, yMax][];
        cbfCb = new boolean[(int) (widthMax & 0xFFFFFFFFL)][(int) (heightMax & 0xFFFFFFFFL)][];
        cbfCr = new boolean[(int) (widthMax & 0xFFFFFFFFL)][(int) (heightMax & 0xFFFFFFFFL)][];
        cbfLuma = new boolean[(int) (widthMax & 0xFFFFFFFFL)][(int) (heightMax & 0xFFFFFFFFL)][];

        // transform_skip_flag = new bool[xMax, yMax][];
        explicitRdpcmFlag = new boolean[(int) (widthMax & 0xFFFFFFFFL)][(int) (heightMax & 0xFFFFFFFFL)][];
        explicitRdpcmDirFlag = new boolean[(int) (widthMax & 0xFFFFFFFFL)][(int) (heightMax & 0xFFFFFFFFL)][];
    }

    public final void cleanMemory() {
        saoTypeIdx = null;
        saoOffsetVal = null;
        saoEoClass = null;
        resScaleVal = null;
        saoOffsetAbs = null;
        saoOffsetSign = null;
        saoBandPosition = null;
        sliceAddrRs = null;
        sliceHeaderIndex = null;
        blockFlags = null;
        intraPredModeY = null;
        intraPredModeC = null;
        transCoeffLevel = null;
        ctDepthByPosition = null;
        cuPredModeByPosition = null;
        qpYByPosition = null;
        log2CbSizeByPosition = null;
        mergeFlag = null;
        cuSkipFlag = null;
        paletteModeFlag = null;
        pcmFlag = null;
        prevIntraLumaPredFlag = null;
        mpmIdx = null;
        remIntraLumaPredMode = null;
        intraChromaPredMode = null;
        tuResidualActFlag = null;
        cbfCb = null;
        cbfCr = null;
        cbfLuma = null;
        explicitRdpcmFlag = null;
        explicitRdpcmDirFlag = null;
        pcmSampleLuma = null;
        pcmSampleChroma = null;
    }

    // 6.4.1 Derivation process for z-scan order block availability
    //
    // The luma location ( xCurr, yCurr ) of the top-left sample of the current block
    // relative to the top-left luma sample of the current picture
    //
    // The luma location ( xNbY, yNbY ) covered by a neighbouring block relative
    // to the top-left luma sample of the current picture.
    final boolean checkZScanAvailability(int xCurr, int yCurr, int xNbY, int yNbY) {
        if (xNbY < 0 || yNbY < 0) return false;

        if (xNbY >= (sps.picWidthInLumaSamples & 0xFFFFFFFFL) || yNbY >= (sps.picHeightInLumaSamples & 0xFFFFFFFFL))
            return false;

        // The minimum luma block address in z-scan order minBlockAddrCurr of the current block
        /*UInt32*/ long minBlockAddrCurr =
                pps.minTbAddrZs[xCurr >> (sps.getMinTbLog2SizeY() & 0xFF)][yCurr >> (sps.getMinTbLog2SizeY() & 0xFF)]
                        & 0xFFFFFFFFL;

        // The minimum luma block address in z-scan order minBlockAddrN of the neighbouring block covering the location
        // (xNbY, yNbY)
        /*UInt32*/ long minBlockAddrN =
                pps.minTbAddrZs[xNbY >> (sps.getMinTbLog2SizeY() & 0xFF)][yNbY >> (sps.getMinTbLog2SizeY() & 0xFF)]
                        & 0xFFFFFFFFL;

        if ((minBlockAddrN & 0xFFFFFFFFL) > (minBlockAddrCurr & 0xFFFFFFFFL)) return false;

        // SliceAddrRs associated with the slice segment containing the current block
        int xCurrCtb = xCurr >> (sps.getCtbLog2SizeY() & 0xFF);
        int yCurrCtb = yCurr >> (sps.getCtbLog2SizeY() & 0xFF);
        int ctbCurrAddrRs = yCurrCtb * (int) sps.getPicWidthInCtbsY() + xCurrCtb;

        // SliceAddrRs associated with the slice segment containing the neighbouring block
        int xNbCtb = xNbY >> (sps.getCtbLog2SizeY() & 0xFF);
        int yNbCtb = yNbY >> (sps.getCtbLog2SizeY() & 0xFF);
        int ctbNbAddrRs = yNbCtb * (int) sps.getPicWidthInCtbsY() + xNbCtb;

        // the current block and the neighbouring block are in the same slice
        if ((sliceAddrRs[xCurrCtb][yCurrCtb] & 0xFFFFFFFFL) != (sliceAddrRs[xNbCtb][yNbCtb] & 0xFFFFFFFFL))
            return false;

        // the neighbouring block is contained in a different tile than the current block
        return (pps.tileIdFromRs[ctbCurrAddrRs] & 0xFFFFFFFFL) == (pps.tileIdFromRs[ctbNbAddrRs] & 0xFFFFFFFFL);
    }

    // 6.4.2 Derivation process for prediction block availability
    //
    // the luma location ( xCb, yCb ) of the top-left sample of the current luma coding block
    // relative to the top-left luma sample of the current picture
    //
    // a variable nCbS specifying the size of the current luma coding block
    //
    // the luma location ( xPb, yPb ) of the top-left sample of the current luma prediction block
    // relative to the top-left luma sample of the current picture
    //
    // two variables nPbW and nPbH specifying the width and the height of the current luma prediction block
    //
    // a variable partIdx specifying the partition index of the current prediction unit within the current coding unit
    //
    // the luma location ( xNbY, yNbY ) covered by a neighbouring prediction block
    // relative to the top-left luma sample of the current picture
    //
    // Output of this process is the availability of the neighbouring prediction block covering the location ( xNbY,
    // yNbY )
    final boolean checkPredictionAvailability(
            int xCb, int yCb, int nCbS, int xPb, int yPb, int nPbW, int nPbH, int partIdx, int xNbY, int yNbY) {
        boolean sameCb = (xCb <= xNbY) && (yCb <= yNbY) && (xCb + nCbS > xNbY) && (yCb + nCbS > yNbY);

        boolean availableN;

        if (!sameCb) {
            availableN = checkZScanAvailability(xPb, yPb, xNbY, yNbY);
        } else {
            availableN =
                    nPbW << 1 != nCbS || nPbH << 1 != nCbS || partIdx != 1 || yCb + nPbH > yNbY || xCb + nPbW <= xNbY;
        }

        if (availableN && cuPredModeByPosition[xNbY][yNbY] == PredMode.MODE_INTRA) availableN = false;

        return availableN;
    }

    final void setCtDepth(int x0, int y0, int log2CbSize, int cqtDepth) {
        int nCbS = 1 << log2CbSize;
        for (int i = x0; i < x0 + nCbS; i++) for (int j = y0; j < y0 + nCbS; j++) ctDepthByPosition[i][j] = cqtDepth;
    }

    final void setCuPredMode(int x0, int y0, int nCbS, PredMode mode) {
        for (int i = x0; i < x0 + nCbS; i++) {
            for (int j = y0; j < y0 + nCbS; j++) {
                cuPredModeByPosition[i][j] = mode;

                if (mode == PredMode.MODE_SKIP) mergeFlag[i][j] = true;
            }
        }
    }

    final void setPcmFlag(int x0, int y0, int nCbS, boolean value) {
        for (int i = x0; i < x0 + nCbS; i++) for (int j = y0; j < y0 + nCbS; j++) pcmFlag[i][j] = value;
    }

    final void setQpY(int x0, int y0, int log2CbSize, int qpY) {
        int nCbS = 1 << log2CbSize;
        for (int i = x0; i < x0 + nCbS; i++) for (int j = y0; j < y0 + nCbS; j++) qpYByPosition[i][j] = qpY;
    }
    // Marks the left and top edges of a transform (or coding) block for the deblocking filter
    final void markTransformBlock(int x0, int y0, int size) {
        int blocks = Math.max(size >> 2, 1);
        for (int i = 0; i < blocks; i++) {
            blockFlags[x0 >> 2][(y0 >> 2) + i] |= LoopFilter.VERTICAL_EDGE;
            blockFlags[(x0 >> 2) + i][y0 >> 2] |= LoopFilter.HORIZONTAL_EDGE;
        }
    }

    // Samples of a coding unit coded with cu_transquant_bypass_flag are left untouched by the in-loop filters
    final void markNoFilter(int x0, int y0, int nCbS) {
        for (int i = x0 >> 2; i < (x0 + nCbS) >> 2; i++)
            for (int j = y0 >> 2; j < (y0 + nCbS) >> 2; j++) blockFlags[i][j] |= LoopFilter.NO_FILTER;
    }

    // Applies the deblocking filter and sample adaptive offset once every slice of the picture is reconstructed
    public final void applyLoopFilters() {
        LoopFilter.apply(this);
    }

    final void setLog2CbSize(int x0, int y0, int log2CbSize) {
        int nCbS = 1 << log2CbSize;
        for (int i = x0; i < x0 + nCbS; i++)
            for (int j = y0; j < y0 + nCbS; j++) log2CbSizeByPosition[i][j] = log2CbSize;
    }

    // 8.4.2 Derivation process for luma intra prediction mode
    final IntraPredMode[] generateIntraPredModeCandidates(
            int xPb, int yPb, int pbOffset, boolean availableA, boolean availableB) {
        IntraPredMode candIntraPredModeA, candIntraPredModeB;

        if (!availableA) {
            candIntraPredModeA = IntraPredMode.INTRA_DC;
        } else if (cuPredModeByPosition[xPb - 1][yPb] != PredMode.MODE_INTRA || pcmFlag[xPb - 1][yPb]) {
            candIntraPredModeA = IntraPredMode.INTRA_DC;
        } else {
            candIntraPredModeA = intraPredModeY[xPb - 1][yPb];
        }

        if (!availableB) {
            candIntraPredModeB = IntraPredMode.INTRA_DC;
        } else if (cuPredModeByPosition[xPb][yPb - 1] != PredMode.MODE_INTRA || pcmFlag[xPb][yPb - 1]) {
            candIntraPredModeB = IntraPredMode.INTRA_DC;
        } else if (yPb - 1 < ((yPb >> (sps.getCtbLog2SizeY() & 0xFF)) << (sps.getCtbLog2SizeY() & 0xFF))) {
            candIntraPredModeB = IntraPredMode.INTRA_DC;
        } else {
            candIntraPredModeB = intraPredModeY[xPb][yPb - 1];
        }

        IntraPredMode[] candModeList = IntraPredMode.createArray(3);

        if (candIntraPredModeB == candIntraPredModeA) {
            if (candIntraPredModeA.ordinal() < 2) {
                candModeList[0] = IntraPredMode.INTRA_PLANAR;
                candModeList[1] = IntraPredMode.INTRA_DC;
                candModeList[2] = IntraPredMode.INTRA_ANGULAR26;
            } else {
                candModeList[0] = candIntraPredModeA;
                candModeList[1] = IntraPredMode.get((2 + ((candIntraPredModeA.ordinal() + 29) % 32)));
                candModeList[2] = IntraPredMode.get((2 + (((candIntraPredModeA.ordinal() - 2) + 1) % 32)));
            }
        } else {
            candModeList[0] = candIntraPredModeA;
            candModeList[1] = candIntraPredModeB;

            if (candModeList[0] != IntraPredMode.INTRA_PLANAR && candModeList[1] != IntraPredMode.INTRA_PLANAR) {
                candModeList[2] = IntraPredMode.INTRA_PLANAR;
            } else if (candModeList[0] != IntraPredMode.INTRA_DC && candModeList[1] != IntraPredMode.INTRA_DC) {
                candModeList[2] = IntraPredMode.INTRA_DC;
            } else {
                candModeList[2] = IntraPredMode.INTRA_ANGULAR26;
            }
        }

        return candModeList;
    }

    final IntraPredMode setIntraPredModeY(int xPb, int yPb, int pbSize, IntraPredMode[] candModeList) {
        IntraPredMode mode;

        if (prevIntraLumaPredFlag[xPb][yPb]) {
            mode = candModeList[mpmIdx[xPb][yPb]];
        } else {
            if (candModeList[0].ordinal() > candModeList[1].ordinal()) {
                final IntraPredMode tmp = candModeList[0];
                candModeList[0] = candModeList[1];
                candModeList[1] = tmp;
            }

            if (candModeList[0].ordinal() > candModeList[2].ordinal()) {
                final IntraPredMode tmp = candModeList[0];
                candModeList[0] = candModeList[2];
                candModeList[2] = tmp;
            }

            if (candModeList[1].ordinal() > candModeList[2].ordinal()) {
                final IntraPredMode tmp = candModeList[1];
                candModeList[1] = candModeList[2];
                candModeList[2] = tmp;
            }

            mode = remIntraLumaPredMode[xPb][yPb];

            int intMode = mode.ordinal();
            for (int i = 0; i < 3; i++) {
                if (intMode >= candModeList[i].ordinal()) intMode++;
            }
            mode = IntraPredMode.get(intMode);
        }

        for (int i = xPb; i < xPb + pbSize; i++) {
            for (int j = yPb; j < yPb + pbSize; j++) {
                intraPredModeY[i][j] = mode;
            }
        }

        return mode;
    }

    final void setIntraPredModeC(int xPb, int yPb, int pbSize, IntraPredMode mode) {
        for (int i = xPb; i < xPb + pbSize; i++) for (int j = yPb; j < yPb + pbSize; j++) intraPredModeC[i][j] = mode;
    }

    final IntraPredMode deriveIntraPredModeIndex(IntraPredMode intraLumaPredMode, byte intraChromaPredMode) {
        if ((intraChromaPredMode & 0xFF) == 4) return intraLumaPredMode;

        IntraPredMode candidate;

        switch (intraChromaPredMode) {
            case 0:
                candidate = IntraPredMode.INTRA_PLANAR;
                break;
            case 1:
                candidate = IntraPredMode.INTRA_ANGULAR26;
                break;
            case 2:
                candidate = IntraPredMode.INTRA_ANGULAR10;
                break;
            case 3:
            default:
                candidate = IntraPredMode.INTRA_DC;
                break;
        }

        if (intraLumaPredMode == candidate) return IntraPredMode.INTRA_ANGULAR34;

        return candidate;
    }
}
