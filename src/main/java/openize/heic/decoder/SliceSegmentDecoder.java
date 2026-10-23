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

/**
 * Decodes the slice segment data (7.3.8) of an intra slice segment into its picture: each coding unit is parsed,
 * then reconstructed, transform block by transform block.
 */
final class SliceSegmentDecoder {
    // Table 8-10: QpC by qPi, for qPi 30..43 (ChromaArrayType 1)
    private static final int[] QP_C_BY_QPI = {29, 30, 31, 32, 33, 33, 34, 34, 35, 35, 36, 36, 37, 37};
    // 8.4.3: the chroma intra prediction mode of 4:2:2, by the mode Table 8-2 gives (Table 8-3)
    private static final int[] INTRA_PRED_MODE_C_422 = {
        0, 1, 2, 2, 2, 2, 3, 5, 7, 8, 10, 12, 13, 15, 17, 18, 19, 20, 21, 22, 23, 23, 24, 24, 25, 25, 26, 27, 27, 28,
        28, 29, 29, 30, 31
    };

    private final BitStreamWithNalSupport stream;
    private final Cabac cabac;
    private final DecoderContext context;
    private final SliceSegmentHeader header;
    private final SliceHeader slice;
    private final HeicPicture picture;
    private final SeqParameterSetRbsp sps;
    private final PicParameterSetRbsp pps;
    private final int sliceSegmentIndex;
    private final int widthInCtbs;

    // the coding tree block being decoded
    private int ctbAddrInRs;
    private int ctbAddrInTs;

    // the coding unit being decoded
    private int xCb;
    private int yCb;
    private int log2CbSize;

    SliceSegmentDecoder(BitStreamWithNalSupport stream, SliceSegmentHeader header, HeicPicture picture) {
        if (header.pps != picture.pps) {
            throw new HeicFormatException("The slice segments of a picture refer to different parameter sets");
        }
        this.stream = stream;
        this.header = header;
        this.picture = picture;
        cabac = stream.getCabac();
        context = stream.getContext();
        slice = header.slice;
        sps = header.sps;
        pps = header.pps;
        widthInCtbs = sps.getPicWidthInCtbsY();
        sliceSegmentIndex = picture.addSliceSegmentHeader(header);
        ctbAddrInRs = header.sliceSegmentAddress;
        ctbAddrInTs = pps.getCtbAddrRsToTs()[ctbAddrInRs];
    }

    // 7.3.8.1 General slice segment data syntax
    void decode() {
        if (!header.dependentSliceSegmentFlag) {
            // 8.6.1: CuQpOffsetCb and CuQpOffsetCr start at 0 in each slice
            context.cuQpOffsetCb = 0;
            context.cuQpOffsetCr = 0;
        }
        startCodingTreeBlock();
        cabac.initializeArithmeticDecoder();
        initializeContexts();

        while (true) {
            decodeCodingTreeUnit();
            storeWppContextsAfterSecondCtbOfRow();
            boolean endOfSliceSegmentFlag = cabac.readEndOfSliceSegmentFlag();
            ctbAddrInTs++;
            if (endOfSliceSegmentFlag) {
                if (pps.dependentSliceSegmentsEnabledFlag) {
                    cabac.storeDependentSliceContexts();
                }
                return;
            }
            if (ctbAddrInTs == sps.getPicSizeInCtbsY()) {
                return;
            }
            ctbAddrInRs = pps.getCtbAddrTsToRs()[ctbAddrInTs];
            startCodingTreeBlock();

            if (isFirstCtbInTile() || (pps.entropyCodingSyncEnabledFlag && isFirstCtbInTileRow())) {
                // a new substream: end_of_subset_one_bit, byte_alignment(), then CABAC starts over (9.3.1)
                if (!cabac.readEndOfSubsetOneBit()) {
                    throw new HeicFormatException("Malformed slice data: end_of_subset_one_bit is 0");
                }
                alignToByte();
                cabac.initializeArithmeticDecoder();
                initializeContexts();
            }
        }
    }

    private void startCodingTreeBlock() {
        int xCtb = ctbAddrInRs % widthInCtbs;
        int yCtb = ctbAddrInRs / widthInCtbs;
        picture.sliceAddrRs[xCtb][yCtb] = slice.sliceAddrRs();
        picture.setSliceSegmentIndex(ctbAddrInRs, sliceSegmentIndex);
    }

    // 9.3.1: the context variables a slice segment, a tile, or a wavefront row starts from
    private void initializeContexts() {
        if (isFirstCtbInTile()) {
            cabac.initializeContexts(slice.sliceQpY());
        } else if (pps.entropyCodingSyncEnabledFlag && isFirstCtbInTileRow()) {
            // from the coding tree block above and right, when it is available
            int ctbSize = sps.getCtbSizeY();
            int xCtb = (ctbAddrInRs % widthInCtbs) << sps.getCtbLog2SizeY();
            int yCtb = (ctbAddrInRs / widthInCtbs) << sps.getCtbLog2SizeY();
            if (picture.checkZScanAvailability(xCtb, yCtb, xCtb + ctbSize, yCtb - ctbSize)) {
                cabac.synchronizeWppContexts();
            } else {
                cabac.initializeContexts(slice.sliceQpY());
            }
        } else if (header.dependentSliceSegmentFlag && ctbAddrInRs == header.sliceSegmentAddress) {
            cabac.synchronizeDependentSliceContexts();
        } else {
            cabac.initializeContexts(slice.sliceQpY());
        }
    }

    // 9.3.2.4: the contexts after the second coding tree block of a row of a tile seed the next row
    private void storeWppContextsAfterSecondCtbOfRow() {
        if (!pps.entropyCodingSyncEnabledFlag) {
            return;
        }
        int[] tileIdByCtbAddrTs = pps.getTileIdByCtbAddrTs();
        boolean secondInTileRow = ctbAddrInRs % widthInCtbs == 1
                || (ctbAddrInRs > 1
                        && tileIdByCtbAddrTs[ctbAddrInTs]
                                != tileIdByCtbAddrTs[pps.getCtbAddrRsToTs()[ctbAddrInRs - 2]]);
        if (secondInTileRow) {
            cabac.storeWppContexts();
        }
    }

    private boolean isFirstCtbInTile() {
        int[] tileIdByCtbAddrTs = pps.getTileIdByCtbAddrTs();
        return ctbAddrInTs == 0 || tileIdByCtbAddrTs[ctbAddrInTs] != tileIdByCtbAddrTs[ctbAddrInTs - 1];
    }

    private boolean isFirstCtbInTileRow() {
        return ctbAddrInRs % widthInCtbs == 0
                || pps.getTileIdByCtbAddrTs()[ctbAddrInTs]
                        != pps.getTileIdByCtbAddrTs()[pps.getCtbAddrRsToTs()[ctbAddrInRs - 1]];
    }

    // 7.3.8.2 Coding tree unit syntax
    private void decodeCodingTreeUnit() {
        int rx = ctbAddrInRs % widthInCtbs;
        int ry = ctbAddrInRs / widthInCtbs;
        if (slice.sliceSaoLumaFlag() || slice.sliceSaoChromaFlag()) {
            parseSao(rx, ry);
        }
        decodeCodingQuadtree(rx << sps.getCtbLog2SizeY(), ry << sps.getCtbLog2SizeY(), sps.getCtbLog2SizeY(), 0);
    }

    // 7.3.8.3 Sample adaptive offset syntax, into the SAO parameters of the picture (7.4.9.3)
    private void parseSao(int rx, int ry) {
        int[] tileIdByCtbAddrTs = pps.getTileIdByCtbAddrTs();
        int[] ctbAddrRsToTs = pps.getCtbAddrRsToTs();
        boolean saoMergeLeftFlag = false;
        boolean saoMergeUpFlag = false;
        if (rx > 0) {
            boolean leftCtbInSliceSeg = ctbAddrInRs > slice.sliceAddrRs();
            boolean leftCtbInTile = tileIdByCtbAddrTs[ctbAddrInTs] == tileIdByCtbAddrTs[ctbAddrRsToTs[ctbAddrInRs - 1]];
            if (leftCtbInSliceSeg && leftCtbInTile) {
                saoMergeLeftFlag = cabac.readSaoMergeFlag();
            }
        }
        if (ry > 0 && !saoMergeLeftFlag) {
            boolean upCtbInSliceSeg = ctbAddrInRs - widthInCtbs >= slice.sliceAddrRs();
            boolean upCtbInTile =
                    tileIdByCtbAddrTs[ctbAddrInTs] == tileIdByCtbAddrTs[ctbAddrRsToTs[ctbAddrInRs - widthInCtbs]];
            if (upCtbInSliceSeg && upCtbInTile) {
                saoMergeUpFlag = cabac.readSaoMergeFlag();
            }
        }

        int componentCount = sps.getChromaArrayType() != 0 ? 3 : 1;
        for (int cIdx = 0; cIdx < componentCount; cIdx++) {
            if (saoMergeLeftFlag) {
                copySaoParameters(cIdx, rx - 1, ry, rx, ry);
            } else if (saoMergeUpFlag) {
                copySaoParameters(cIdx, rx, ry - 1, rx, ry);
            } else if (cIdx == 0 ? slice.sliceSaoLumaFlag() : slice.sliceSaoChromaFlag()) {
                parseSaoParameters(cIdx, rx, ry);
            } else {
                picture.saoTypeIdx[cIdx][rx][ry] = 0;
            }
        }
    }

    private void parseSaoParameters(int cIdx, int rx, int ry) {
        // sao_type_idx_chroma and sao_eo_class_chroma are coded for Cb and hold for Cr
        if (cIdx < 2) {
            picture.saoTypeIdx[cIdx][rx][ry] = cabac.readSaoTypeIdx();
        } else {
            picture.saoTypeIdx[2][rx][ry] = picture.saoTypeIdx[1][rx][ry];
        }
        int saoTypeIdx = picture.saoTypeIdx[cIdx][rx][ry];
        if (saoTypeIdx == 0) {
            return;
        }

        int bitDepth = cIdx == 0 ? sps.getBitDepthY() : sps.getBitDepthC();
        int[] offsetAbs = picture.saoOffsetAbs[cIdx][rx][ry];
        int[] offsetSign = picture.saoOffsetSign[cIdx][rx][ry];
        for (int i = 0; i < 4; i++) {
            offsetAbs[i] = cabac.readSaoOffsetAbs(bitDepth);
        }
        if (saoTypeIdx == 1) {
            // band offset
            for (int i = 0; i < 4; i++) {
                offsetSign[i] = offsetAbs[i] != 0 ? cabac.readSaoOffsetSign() : 0;
            }
            picture.saoBandPosition[cIdx][rx][ry] = cabac.readSaoBandPosition();
        } else {
            // edge offset: the first two offsets positive, the last two negative
            if (cIdx < 2) {
                picture.saoEoClass[cIdx][rx][ry] = cabac.readSaoEoClass();
            } else {
                picture.saoEoClass[2][rx][ry] = picture.saoEoClass[1][rx][ry];
            }
            for (int i = 0; i < 4; i++) {
                offsetSign[i] = i < 2 ? 0 : 1;
            }
        }
    }

    private void copySaoParameters(int cIdx, int fromRx, int fromRy, int rx, int ry) {
        picture.saoTypeIdx[cIdx][rx][ry] = picture.saoTypeIdx[cIdx][fromRx][fromRy];
        picture.saoEoClass[cIdx][rx][ry] = picture.saoEoClass[cIdx][fromRx][fromRy];
        picture.saoBandPosition[cIdx][rx][ry] = picture.saoBandPosition[cIdx][fromRx][fromRy];
        System.arraycopy(picture.saoOffsetAbs[cIdx][fromRx][fromRy], 0, picture.saoOffsetAbs[cIdx][rx][ry], 0, 4);
        System.arraycopy(picture.saoOffsetSign[cIdx][fromRx][fromRy], 0, picture.saoOffsetSign[cIdx][rx][ry], 0, 4);
    }

    // 7.3.8.4 Coding quadtree syntax
    private void decodeCodingQuadtree(int x0, int y0, int log2Size, int cqtDepth) {
        int size = 1 << log2Size;
        boolean insidePicture = x0 + size <= sps.picWidthInLumaSamples && y0 + size <= sps.picHeightInLumaSamples;
        boolean splitCuFlag = log2Size > sps.getMinCbLog2SizeY()
                && (!insidePicture || cabac.readSplitCuFlag(x0, y0, picture, cqtDepth));

        // a new quantization group, and chroma QP offset group
        if (pps.cuQpDeltaEnabledFlag && log2Size >= pps.getLog2MinCuQpDeltaSize()) {
            context.isCuQpDeltaCoded = false;
            context.cuQpDeltaVal = 0;
        }
        if (slice.cuChromaQpOffsetEnabledFlag() && log2Size >= pps.getLog2MinCuChromaQpOffsetSize()) {
            context.isCuChromaQpOffsetCoded = false;
        }

        if (splitCuFlag) {
            int x1 = x0 + (size >> 1);
            int y1 = y0 + (size >> 1);
            decodeCodingQuadtree(x0, y0, log2Size - 1, cqtDepth + 1);
            if (x1 < sps.picWidthInLumaSamples) {
                decodeCodingQuadtree(x1, y0, log2Size - 1, cqtDepth + 1);
            }
            if (y1 < sps.picHeightInLumaSamples) {
                decodeCodingQuadtree(x0, y1, log2Size - 1, cqtDepth + 1);
            }
            if (x1 < sps.picWidthInLumaSamples && y1 < sps.picHeightInLumaSamples) {
                decodeCodingQuadtree(x1, y1, log2Size - 1, cqtDepth + 1);
            }
        } else {
            picture.setCtDepth(x0, y0, log2Size, cqtDepth);
            decodeCodingUnit(x0, y0, log2Size);
        }
    }

    // 7.3.8.5 Coding unit syntax, for an intra coding unit
    private void decodeCodingUnit(int x0, int y0, int log2Size) {
        xCb = x0;
        yCb = y0;
        log2CbSize = log2Size;
        int nCbS = 1 << log2Size;
        picture.setLog2CbSize(x0, y0, log2Size);
        deriveQuantizationParameters();

        context.cuTransquantBypassFlag = pps.transquantBypassEnabledFlag && cabac.readCuTransquantBypassFlag();
        picture.markTransformBlock(x0, y0, nCbS);
        if (context.cuTransquantBypassFlag) {
            picture.markNoFilter(x0, y0, nCbS);
        }

        if (sps.spsSccExt.paletteModeEnabledFlag
                && log2Size <= sps.getMaxTbLog2SizeY()
                && cabac.readPaletteModeFlag()) {
            throw new HeicFormatException("Palette mode coding units (screen content coding) are not supported");
        }

        boolean intraSplitFlag = log2Size == sps.getMinCbLog2SizeY() && cabac.readPartModeIsNxN();
        boolean pcmFlag = !intraSplitFlag
                && sps.pcmEnabledFlag
                && log2Size >= sps.getLog2MinIpcmCbSizeY()
                && log2Size <= sps.getLog2MaxIpcmCbSizeY()
                && cabac.readPcmFlag();
        picture.setPcmFlag(x0, y0, nCbS, pcmFlag);

        if (pcmFlag) {
            decodePcmSamples(x0, y0, log2Size);
            return;
        }

        parseIntraPredictionModes(x0, y0, nCbS, intraSplitFlag);
        int maxTrafoDepth = sps.maxTransformHierarchyDepthIntra + (intraSplitFlag ? 1 : 0);
        decodeTransformTree(x0, y0, x0, y0, log2Size, 0, 0, maxTrafoDepth, intraSplitFlag);
    }

    // 7.3.8.7 PCM sample syntax, after pcm_alignment_zero_bit; the samples are the reconstruction (8.4.4.1)
    private void decodePcmSamples(int x0, int y0, int log2Size) {
        alignToByte();
        int nCbS = 1 << log2Size;
        int lumaShift = sps.getBitDepthY() - sps.pcmSampleBitDepthLuma;
        for (int j = 0; j < nCbS; j++) {
            for (int i = 0; i < nCbS; i++) {
                picture.setSample(0, x0 + i, y0 + j, stream.read(sps.pcmSampleBitDepthLuma) << lumaShift);
            }
        }
        if (sps.getChromaArrayType() != 0) {
            int chromaWidth = nCbS / sps.getSubWidthC();
            int chromaHeight = nCbS / sps.getSubHeightC();
            int xC = x0 / sps.getSubWidthC();
            int yC = y0 / sps.getSubHeightC();
            int chromaShift = sps.getBitDepthC() - sps.pcmSampleBitDepthChroma;
            for (int cIdx = 1; cIdx <= 2; cIdx++) {
                for (int j = 0; j < chromaHeight; j++) {
                    for (int i = 0; i < chromaWidth; i++) {
                        int sample = stream.read(sps.pcmSampleBitDepthChroma) << chromaShift;
                        picture.setSample(cIdx, xC + i, yC + j, sample);
                    }
                }
            }
        }
        // 9.3.2.5: the arithmetic decoder starts over after the PCM samples
        cabac.initializeArithmeticDecoder();
    }

    // 7.3.8.5: the luma intra prediction modes of the one or four prediction blocks, then the chroma ones (8.4.2,
    // 8.4.3)
    private void parseIntraPredictionModes(int x0, int y0, int nCbS, boolean intraSplitFlag) {
        int pbOffset = intraSplitFlag ? nCbS / 2 : nCbS;
        int predictionBlockCount = intraSplitFlag ? 4 : 1;
        boolean[] prevIntraLumaPredFlag = new boolean[predictionBlockCount];
        for (int block = 0; block < predictionBlockCount; block++) {
            prevIntraLumaPredFlag[block] = cabac.readPrevIntraLumaPredFlag();
        }

        boolean availableLeft = picture.checkZScanAvailability(x0, y0, x0 - 1, y0);
        boolean availableAbove = picture.checkZScanAvailability(x0, y0, x0, y0 - 1);
        for (int block = 0; block < predictionBlockCount; block++) {
            int i = (block & 1) * pbOffset;
            int j = (block >> 1) * pbOffset;
            int mpmIdxOrRemIntraLumaPredMode =
                    prevIntraLumaPredFlag[block] ? cabac.readMpmIdx() : cabac.readRemIntraLumaPredMode();
            IntraPredMode[] candModeList = picture.generateIntraPredModeCandidates(
                    x0 + i, y0 + j, availableLeft || i > 0, availableAbove || j > 0);
            picture.setIntraPredModeY(
                    x0 + i, y0 + j, pbOffset, candModeList, prevIntraLumaPredFlag[block], mpmIdxOrRemIntraLumaPredMode);
        }

        int chromaArrayType = sps.getChromaArrayType();
        if (chromaArrayType == 3) {
            for (int block = 0; block < predictionBlockCount; block++) {
                int i = (block & 1) * pbOffset;
                int j = (block >> 1) * pbOffset;
                setIntraChromaPredMode(x0 + i, y0 + j, pbOffset, cabac.readIntraChromaPredMode());
            }
        } else if (chromaArrayType != 0) {
            setIntraChromaPredMode(x0, y0, nCbS, cabac.readIntraChromaPredMode());
        }
    }

    private void setIntraChromaPredMode(int xPb, int yPb, int pbSize, int intraChromaPredMode) {
        picture.intraChromaPredMode.fill(xPb, yPb, pbSize, intraChromaPredMode);
        IntraPredMode intraPredModeC =
                HeicPicture.deriveIntraPredModeC(picture.intraPredModeY.get(xPb, yPb), intraChromaPredMode);
        if (sps.getChromaArrayType() == 2) {
            intraPredModeC = IntraPredMode.get(INTRA_PRED_MODE_C_422[intraPredModeC.ordinal()]);
        }
        picture.setIntraPredModeC(xPb, yPb, pbSize, intraPredModeC);
    }

    // 7.3.8.8 Transform tree syntax
    private void decodeTransformTree(
            int x0,
            int y0,
            int xBase,
            int yBase,
            int log2TrafoSize,
            int trafoDepth,
            int blkIdx,
            int maxTrafoDepth,
            boolean intraSplitFlag) {
        boolean intraSplit = intraSplitFlag && trafoDepth == 0;
        boolean splitTransformFlag;
        if (log2TrafoSize <= sps.getMaxTbLog2SizeY()
                && log2TrafoSize > sps.getMinTbLog2SizeY()
                && trafoDepth < maxTrafoDepth
                && !intraSplit) {
            splitTransformFlag = cabac.readSplitTransformFlag(log2TrafoSize);
        } else {
            splitTransformFlag = log2TrafoSize > sps.getMaxTbLog2SizeY() || intraSplit;
        }

        int chromaArrayType = sps.getChromaArrayType();
        if ((log2TrafoSize > 2 && chromaArrayType != 0) || chromaArrayType == 3) {
            // 4:2:2 has a second chroma block below the first one
            boolean secondChromaBlock = chromaArrayType == 2 && (!splitTransformFlag || log2TrafoSize == 3);
            int ySecond = y0 + (1 << (log2TrafoSize - 1));
            if (trafoDepth == 0 || picture.cbfCb.getBit(xBase, yBase, trafoDepth - 1)) {
                picture.cbfCb.setBit(x0, y0, trafoDepth, cabac.readCbfChroma(trafoDepth));
                if (secondChromaBlock) {
                    picture.cbfCb.setBit(x0, ySecond, trafoDepth, cabac.readCbfChroma(trafoDepth));
                }
            }
            if (trafoDepth == 0 || picture.cbfCr.getBit(xBase, yBase, trafoDepth - 1)) {
                picture.cbfCr.setBit(x0, y0, trafoDepth, cabac.readCbfChroma(trafoDepth));
                if (secondChromaBlock) {
                    picture.cbfCr.setBit(x0, ySecond, trafoDepth, cabac.readCbfChroma(trafoDepth));
                }
            }
        }

        if (splitTransformFlag) {
            int x1 = x0 + (1 << (log2TrafoSize - 1));
            int y1 = y0 + (1 << (log2TrafoSize - 1));
            int childDepth = trafoDepth + 1;
            decodeTransformTree(x0, y0, x0, y0, log2TrafoSize - 1, childDepth, 0, maxTrafoDepth, intraSplitFlag);
            decodeTransformTree(x1, y0, x0, y0, log2TrafoSize - 1, childDepth, 1, maxTrafoDepth, intraSplitFlag);
            decodeTransformTree(x0, y1, x0, y0, log2TrafoSize - 1, childDepth, 2, maxTrafoDepth, intraSplitFlag);
            decodeTransformTree(x1, y1, x0, y0, log2TrafoSize - 1, childDepth, 3, maxTrafoDepth, intraSplitFlag);
        } else {
            // cbf_luma is always coded in an intra coding unit
            picture.cbfLuma.setBit(x0, y0, trafoDepth, cabac.readCbfLuma(trafoDepth));
            picture.markTransformBlock(x0, y0, 1 << log2TrafoSize);
            decodeTransformUnit(x0, y0, xBase, yBase, log2TrafoSize, trafoDepth, blkIdx, intraSplitFlag);
        }
    }

    // 7.3.8.10 Transform unit syntax, each transform block reconstructed once parsed
    private void decodeTransformUnit(
            int x0,
            int y0,
            int xBase,
            int yBase,
            int log2TrafoSize,
            int trafoDepth,
            int blkIdx,
            boolean intraSplitFlag) {
        int chromaArrayType = sps.getChromaArrayType();
        // a 4x4 luma block of 4:2:0 or 4:2:2 has its chroma with the fourth block of its parent
        boolean chromaWithParent = chromaArrayType != 3 && log2TrafoSize == 2;
        int log2TrafoSizeC = Math.max(2, log2TrafoSize - (chromaArrayType == 3 ? 0 : 1));
        int cbfDepthC = chromaWithParent ? trafoDepth - 1 : trafoDepth;
        int xC = chromaWithParent ? xBase : x0;
        int yC = chromaWithParent ? yBase : y0;
        int chromaOffset = 1 << log2TrafoSizeC;

        boolean cbfLuma = picture.cbfLuma.getBit(x0, y0, trafoDepth);
        boolean cbfChroma = picture.cbfCb.getBit(xC, yC, cbfDepthC)
                || picture.cbfCr.getBit(xC, yC, cbfDepthC)
                || (chromaArrayType == 2
                        && (picture.cbfCb.getBit(xC, yC + chromaOffset, cbfDepthC)
                                || picture.cbfCr.getBit(xC, yC + chromaOffset, cbfDepthC)));

        if (cbfLuma || cbfChroma) {
            requireNoAdaptiveColourTransform(x0, y0, intraSplitFlag);
            boolean quantizationChanged = parseCuQpDelta();
            if (cbfChroma && !context.cuTransquantBypassFlag) {
                quantizationChanged |= parseCuChromaQpOffset();
            }
            if (quantizationChanged) {
                deriveQuantizationParameters();
            }
        }

        if (cbfLuma) {
            parseResidual(x0, y0, log2TrafoSize, 0);
        }
        reconstructBlock(x0, y0, log2TrafoSize, 0);

        if (chromaArrayType == 0) {
            return;
        }
        if (!chromaWithParent) {
            boolean crossComponentPrediction = cbfLuma
                    && pps.ppsRangeExt.crossComponentPredictionEnabledFlag
                    && picture.intraChromaPredMode.get(x0, y0) == 4;
            for (int cIdx = 1; cIdx <= 2; cIdx++) {
                if (crossComponentPrediction) {
                    requireNoCrossComponentPrediction(cIdx - 1);
                }
                decodeChromaBlocks(x0, y0, x0, y0, log2TrafoSizeC, trafoDepth, cIdx, chromaOffset);
            }
        } else if (blkIdx == 3) {
            for (int cIdx = 1; cIdx <= 2; cIdx++) {
                decodeChromaBlocks(xBase, yBase, xBase, yBase, log2TrafoSize, trafoDepth - 1, cIdx, chromaOffset);
            }
        }
    }

    // The chroma block of component cIdx whose cbf is at (xCbf, yCbf), at the luma location (xLuma, yLuma), and the
    // second one below it in 4:2:2
    private void decodeChromaBlocks(
            int xLuma, int yLuma, int xCbf, int yCbf, int log2SizeC, int cbfDepth, int cIdx, int chromaOffset) {
        BlockByteMap cbf = cIdx == 1 ? picture.cbfCb : picture.cbfCr;
        int xChroma = xLuma / sps.getSubWidthC();
        int yChroma = yLuma / sps.getSubHeightC();
        if (cbf.getBit(xCbf, yCbf, cbfDepth)) {
            parseResidual(xLuma, yLuma, log2SizeC, cIdx);
        }
        reconstructBlock(xChroma, yChroma, log2SizeC, cIdx);

        if (sps.getChromaArrayType() == 2) {
            if (cbf.getBit(xCbf, yCbf + chromaOffset, cbfDepth)) {
                parseResidual(xLuma, yLuma + chromaOffset, log2SizeC, cIdx);
            }
            reconstructBlock(xChroma, yChroma + chromaOffset, log2SizeC, cIdx);
        }
    }

    // tu_residual_act_flag: the adaptive colour transform is parsed but not applied, so it cannot be decoded
    private void requireNoAdaptiveColourTransform(int x0, int y0, boolean intraSplitFlag) {
        if (!pps.ppsSccExt.residualAdaptiveColourTransformEnabledFlag) {
            return;
        }
        int xP = (x0 >> sps.getMinCbLog2SizeY()) << sps.getMinCbLog2SizeY();
        int yP = (y0 >> sps.getMinCbLog2SizeY()) << sps.getMinCbLog2SizeY();
        int half = sps.getMinCbSizeY() / 2;
        BlockByteMap intraChromaPredMode = picture.intraChromaPredMode;
        boolean coded = (!intraSplitFlag && intraChromaPredMode.get(x0, y0) == 4)
                || (intraChromaPredMode.get(xP, yP) == 4
                        && intraChromaPredMode.get(xP + half, yP) == 4
                        && intraChromaPredMode.get(xP, yP + half) == 4
                        && intraChromaPredMode.get(xP + half, yP + half) == 4);
        if (coded && cabac.readTuResidualActFlag()) {
            throw new HeicFormatException("The adaptive colour transform (screen content coding) is not supported");
        }
    }

    // 7.3.8.12: cross-component prediction is parsed but not applied, so a stream that uses it cannot be decoded
    private void requireNoCrossComponentPrediction(int c) {
        int log2ResScaleAbsPlus1 = cabac.readLog2ResScaleAbsPlus1(c);
        if (log2ResScaleAbsPlus1 != 0) {
            cabac.readResScaleSignFlag(c);
            throw new HeicFormatException("Cross-component prediction is not supported");
        }
    }

    // 7.3.8.14 Delta QP syntax; whether the quantization parameters change
    private boolean parseCuQpDelta() {
        if (!pps.cuQpDeltaEnabledFlag || context.isCuQpDeltaCoded) {
            return false;
        }
        context.isCuQpDeltaCoded = true;
        int cuQpDeltaAbs = cabac.readCuQpDeltaAbs();
        int cuQpDeltaVal = cuQpDeltaAbs != 0 && cabac.readCuQpDeltaSignFlag() ? -cuQpDeltaAbs : cuQpDeltaAbs;
        int halfQpBdOffsetY = sps.getQpBdOffsetY() / 2;
        if (cuQpDeltaVal < -(26 + halfQpBdOffsetY) || cuQpDeltaVal > 25 + halfQpBdOffsetY) {
            throw new HeicFormatException("CuQpDeltaVal %d out of range".formatted(cuQpDeltaVal));
        }
        context.cuQpDeltaVal = cuQpDeltaVal;
        return true;
    }

    // 7.3.8.15 Chroma QP offset syntax; whether the quantization parameters change
    private boolean parseCuChromaQpOffset() {
        if (!slice.cuChromaQpOffsetEnabledFlag() || context.isCuChromaQpOffsetCoded) {
            return false;
        }
        context.isCuChromaQpOffsetCoded = true;
        PpsRangeExtension rangeExtension = pps.ppsRangeExt;
        if (cabac.readCuChromaQpOffsetFlag()) {
            int listLengthMinus1 = rangeExtension.chromaQpOffsetListLenMinus1;
            int cuChromaQpOffsetIdx = listLengthMinus1 > 0 ? cabac.readCuChromaQpOffsetIdx(listLengthMinus1) : 0;
            context.cuQpOffsetCb = rangeExtension.cbQpOffsetList[cuChromaQpOffsetIdx];
            context.cuQpOffsetCr = rangeExtension.crQpOffsetList[cuChromaQpOffsetIdx];
        } else {
            context.cuQpOffsetCb = 0;
            context.cuQpOffsetCr = 0;
        }
        return true;
    }

    // 8.6.1 Derivation process for quantization parameters, of the current coding unit
    private void deriveQuantizationParameters() {
        int log2QgSize = pps.getLog2MinCuQpDeltaSize();
        int xQg = xCb - (xCb & ((1 << log2QgSize) - 1));
        int yQg = yCb - (yCb & ((1 << log2QgSize) - 1));
        if (xQg != context.currentQgX || yQg != context.currentQgY) {
            // qPY_PREV: the QpY of the last coding unit of the previous quantization group
            context.previousQgQpY = context.lastQpY;
            context.currentQgX = xQg;
            context.currentQgY = yQg;
        }

        int ctbLog2SizeY = sps.getCtbLog2SizeY();
        int ctbMask = (1 << ctbLog2SizeY) - 1;
        boolean firstQgInSlice = xQg == (slice.sliceAddrRs() % widthInCtbs) << ctbLog2SizeY
                && yQg == (slice.sliceAddrRs() / widthInCtbs) << ctbLog2SizeY;
        boolean firstQgInTile = pps.tilesEnabledFlag
                && (xQg & ctbMask) == 0
                && (yQg & ctbMask) == 0
                && pps.checkIfTileIsFirst(xQg >> ctbLog2SizeY, yQg >> ctbLog2SizeY);
        boolean firstQgInCtbRow = xQg == 0 && (yQg & ctbMask) == 0;
        int qPYPrev = firstQgInSlice || firstQgInTile || (firstQgInCtbRow && pps.entropyCodingSyncEnabledFlag)
                ? slice.sliceQpY()
                : context.previousQgQpY;

        int qPYA = neighbouringQpY(qPYPrev, xQg, yQg, xQg - 1, yQg);
        int qPYB = neighbouringQpY(qPYPrev, xQg, yQg, xQg, yQg - 1);
        int qPYPred = (qPYA + qPYB + 1) >> 1;

        int qpBdOffsetY = sps.getQpBdOffsetY();
        int qpY = ((qPYPred + context.cuQpDeltaVal + 52 + 2 * qpBdOffsetY) % (52 + qpBdOffsetY)) - qpBdOffsetY;
        context.lastQpY = qpY;
        context.qpPrimeY = qpY + qpBdOffsetY;
        picture.setQpY(xCb, yCb, log2CbSize, qpY);

        if (sps.getChromaArrayType() != 0) {
            int qpBdOffsetC = sps.getQpBdOffsetC();
            int qPiCb = MathExtra.clip3(
                    -qpBdOffsetC, 57, qpY + pps.ppsCbQpOffset + slice.sliceCbQpOffset() + context.cuQpOffsetCb);
            int qPiCr = MathExtra.clip3(
                    -qpBdOffsetC, 57, qpY + pps.ppsCrQpOffset + slice.sliceCrQpOffset() + context.cuQpOffsetCr);
            context.qpPrimeCb = chromaQp(qPiCb) + qpBdOffsetC;
            context.qpPrimeCr = chromaQp(qPiCr) + qpBdOffsetC;
        }
    }

    // qPY_A or qPY_B: the QpY of the coding unit covering (xQgN, yQgN), when it is in the current coding tree block
    private int neighbouringQpY(int qPYPrev, int xQg, int yQg, int xQgN, int yQgN) {
        if (!picture.checkZScanAvailability(xQg, yQg, xQgN, yQgN)) {
            return qPYPrev;
        }
        int minTbLog2SizeY = sps.getMinTbLog2SizeY();
        int minTbAddrN = pps.getMinTbAddrZs()[xQgN >> minTbLog2SizeY][yQgN >> minTbLog2SizeY];
        int ctbAddrN = minTbAddrN >> (2 * (sps.getCtbLog2SizeY() - minTbLog2SizeY));
        return ctbAddrN == ctbAddrInTs ? picture.qpY.get(xQgN, yQgN) : qPYPrev;
    }

    // Table 8-10 for 4:2:0, Min(qPi, 51) for the other formats
    private int chromaQp(int qPi) {
        if (sps.getChromaArrayType() != 1) {
            return Math.min(qPi, 51);
        }
        if (qPi < 30) {
            return qPi;
        }
        return qPi > 43 ? qPi - 6 : QP_C_BY_QPI[qPi - 30];
    }

    // 7.3.8.11, for a transform block whose top-left luma location is (x0, y0)
    private void parseResidual(int x0, int y0, int log2TrafoSize, int cIdx) {
        ResidualCoding.parse(
                cabac,
                context.getResidualBuffers(),
                picture,
                x0,
                y0,
                log2TrafoSize,
                cIdx,
                context.cuTransquantBypassFlag);
    }

    // 8.4.4.1: the prediction plus the residual of the transform block of component cIdx at (x0, y0), in samples of
    // that component
    private void reconstructBlock(int x0, int y0, int log2TrafoSize, int cIdx) {
        int nTbS = 1 << log2TrafoSize;
        int xLuma = x0 * (cIdx == 0 ? 1 : sps.getSubWidthC());
        int yLuma = y0 * (cIdx == 0 ? 1 : sps.getSubHeightC());
        IntraPredMode intraPredMode =
                cIdx == 0 ? picture.intraPredModeY.get(xLuma, yLuma) : picture.intraPredModeC.get(xLuma, yLuma);
        int[][] predSamples = IntraPrediction.predict(
                picture,
                context.getIntraPredictionBuffers(),
                x0,
                y0,
                nTbS,
                cIdx,
                intraPredMode,
                context.cuTransquantBypassFlag);

        ResidualBuffers residualBuffers = context.getResidualBuffers();
        int bitDepth = cIdx == 0 ? sps.getBitDepthY() : sps.getBitDepthC();
        if (residualBuffers.isEmpty()) {
            // no coded coefficients: the prediction is the reconstruction
            for (int x = 0; x < nTbS; x++) {
                int[] predictedColumn = predSamples[x];
                for (int y = 0; y < nTbS; y++) {
                    picture.setSample(cIdx, x0 + x, y0 + y, predictedColumn[y]);
                }
            }
        } else {
            int qP = cIdx == 0 ? context.qpPrimeY : cIdx == 1 ? context.qpPrimeCb : context.qpPrimeCr;
            int[] residual = InverseTransform.residual(
                    picture,
                    residualBuffers,
                    cIdx,
                    nTbS,
                    qP,
                    context.cuTransquantBypassFlag,
                    residualBuffers.transformSkipFlag,
                    residualDpcmDirection(intraPredMode, residualBuffers.transformSkipFlag));
            for (int x = 0; x < nTbS; x++) {
                int[] predictedColumn = predSamples[x];
                for (int y = 0; y < nTbS; y++) {
                    int reconstructed = MathExtra.clipBitDepth(predictedColumn[y] + residual[x * nTbS + y], bitDepth);
                    picture.setSample(cIdx, x0 + x, y0 + y, reconstructed);
                }
            }
            residualBuffers.clear();
        }
    }

    // 8.6.2: implicit RDPCM accumulates the residual of a horizontally or vertically predicted lossless or
    // transform-skipped block along the prediction direction
    private int residualDpcmDirection(IntraPredMode intraPredMode, boolean transformSkipFlag) {
        boolean implicitRdpcm = sps.spsRangeExt.implicitRdpcmEnabledFlag
                && (context.cuTransquantBypassFlag || transformSkipFlag)
                && (intraPredMode == IntraPredMode.INTRA_ANGULAR10 || intraPredMode == IntraPredMode.INTRA_ANGULAR26);
        if (!implicitRdpcm) {
            return InverseTransform.NO_RESIDUAL_DPCM;
        }
        return intraPredMode == IntraPredMode.INTRA_ANGULAR26 ? 1 : 0;
    }

    private void alignToByte() {
        while (stream.notByteAligned()) {
            stream.skipBits(1);
        }
    }
}
