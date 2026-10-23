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

import java.util.Arrays;
import openize.MathUtils;
import openize.heic.decoder.io.BitStreamWithNalSupport;

/*partial*/ class TransformUnit {
    private static final int[][] transMatrix4x4 = {
        {29, 55, 74, 84},
        {74, 74, 0, -74},
        {84, -29, -74, 55},
        {55, -84, 74, -29}
    };
    private static final int[][] transMatrix32x32 = {
        {
            64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
            64, 64, 64, 64, 64
        },
        {
            90, 90, 88, 85, 82, 78, 73, 67, 61, 54, 46, 38, 31, 22, 13, 4, -4, -13, -22, -31, -38, -46, -54, -61, -67,
            -73, -78, -82, -85, -88, -90, -90
        },
        {
            90, 87, 80, 70, 57, 43, 25, 9, -9, -25, -43, -57, -70, -80, -87, -90, -90, -87, -80, -70, -57, -43, -25, -9,
            9, 25, 43, 57, 70, 80, 87, 90
        },
        {
            90, 82, 67, 46, 22, -4, -31, -54, -73, -85, -90, -88, -78, -61, -38, -13, 13, 38, 61, 78, 88, 90, 85, 73,
            54, 31, 4, -22, -46, -67, -82, -90
        },
        {
            89, 75, 50, 18, -18, -50, -75, -89, -89, -75, -50, -18, 18, 50, 75, 89, 89, 75, 50, 18, -18, -50, -75, -89,
            -89, -75, -50, -18, 18, 50, 75, 89
        },
        {
            88, 67, 31, -13, -54, -82, -90, -78, -46, -4, 38, 73, 90, 85, 61, 22, -22, -61, -85, -90, -73, -38, 4, 46,
            78, 90, 82, 54, 13, -31, -67, -88
        },
        {
            87, 57, 9, -43, -80, -90, -70, -25, 25, 70, 90, 80, 43, -9, -57, -87, -87, -57, -9, 43, 80, 90, 70, 25, -25,
            -70, -90, -80, -43, 9, 57, 87
        },
        {
            85, 46, -13, -67, -90, -73, -22, 38, 82, 88, 54, -4, -61, -90, -78, -31, 31, 78, 90, 61, 4, -54, -88, -82,
            -38, 22, 73, 90, 67, 13, -46, -85
        },
        {
            83, 36, -36, -83, -83, -36, 36, 83, 83, 36, -36, -83, -83, -36, 36, 83, 83, 36, -36, -83, -83, -36, 36, 83,
            83, 36, -36, -83, -83, -36, 36, 83
        },
        {
            82, 22, -54, -90, -61, 13, 78, 85, 31, -46, -90, -67, 4, 73, 88, 38, -38, -88, -73, -4, 67, 90, 46, -31,
            -85, -78, -13, 61, 90, 54, -22, -82
        },
        {
            80, 9, -70, -87, -25, 57, 90, 43, -43, -90, -57, 25, 87, 70, -9, -80, -80, -9, 70, 87, 25, -57, -90, -43,
            43, 90, 57, -25, -87, -70, 9, 80
        },
        {
            78, -4, -82, -73, 13, 85, 67, -22, -88, -61, 31, 90, 54, -38, -90, -46, 46, 90, 38, -54, -90, -31, 61, 88,
            22, -67, -85, -13, 73, 82, 4, -78
        },
        {
            75, -18, -89, -50, 50, 89, 18, -75, -75, 18, 89, 50, -50, -89, -18, 75, 75, -18, -89, -50, 50, 89, 18, -75,
            -75, 18, 89, 50, -50, -89, -18, 75
        },
        {
            73, -31, -90, -22, 78, 67, -38, -90, -13, 82, 61, -46, -88, -4, 85, 54, -54, -85, 4, 88, 46, -61, -82, 13,
            90, 38, -67, -78, 22, 90, 31, -73
        },
        {
            70, -43, -87, 9, 90, 25, -80, -57, 57, 80, -25, -90, -9, 87, 43, -70, -70, 43, 87, -9, -90, -25, 80, 57,
            -57, -80, 25, 90, 9, -87, -43, 70
        },
        {
            67, -54, -78, 38, 85, -22, -90, 4, 90, 13, -88, -31, 82, 46, -73, -61, 61, 73, -46, -82, 31, 88, -13, -90,
            -4, 90, 22, -85, -38, 78, 54, -67
        },
        {
            64, -64, -64, 64, 64, -64, -64, 64, 64, -64, -64, 64, 64, -64, -64, 64, 64, -64, -64, 64, 64, -64, -64, 64,
            64, -64, -64, 64, 64, -64, -64, 64
        },
        {
            61, -73, -46, 82, 31, -88, -13, 90, -4, -90, 22, 85, -38, -78, 54, 67, -67, -54, 78, 38, -85, -22, 90, 4,
            -90, 13, 88, -31, -82, 46, 73, -61
        },
        {
            57, -80, -25, 90, -9, -87, 43, 70, -70, -43, 87, 9, -90, 25, 80, -57, -57, 80, 25, -90, 9, 87, -43, -70, 70,
            43, -87, -9, 90, -25, -80, 57
        },
        {
            54, -85, -4, 88, -46, -61, 82, 13, -90, 38, 67, -78, -22, 90, -31, -73, 73, 31, -90, 22, 78, -67, -38, 90,
            -13, -82, 61, 46, -88, 4, 85, -54
        },
        {
            50, -89, 18, 75, -75, -18, 89, -50, -50, 89, -18, -75, 75, 18, -89, 50, 50, -89, 18, 75, -75, -18, 89, -50,
            -50, 89, -18, -75, 75, 18, -89, 50
        },
        {
            46, -90, 38, 54, -90, 31, 61, -88, 22, 67, -85, 13, 73, -82, 4, 78, -78, -4, 82, -73, -13, 85, -67, -22, 88,
            -61, -31, 90, -54, -38, 90, -46
        },
        {
            43, -90, 57, 25, -87, 70, 9, -80, 80, -9, -70, 87, -25, -57, 90, -43, -43, 90, -57, -25, 87, -70, -9, 80,
            -80, 9, 70, -87, 25, 57, -90, 43
        },
        {
            38, -88, 73, -4, -67, 90, -46, -31, 85, -78, 13, 61, -90, 54, 22, -82, 82, -22, -54, 90, -61, -13, 78, -85,
            31, 46, -90, 67, 4, -73, 88, -38
        },
        {
            36, -83, 83, -36, -36, 83, -83, 36, 36, -83, 83, -36, -36, 83, -83, 36, 36, -83, 83, -36, -36, 83, -83, 36,
            36, -83, 83, -36, -36, 83, -83, 36
        },
        {
            31, -78, 90, -61, 4, 54, -88, 82, -38, -22, 73, -90, 67, -13, -46, 85, -85, 46, 13, -67, 90, -73, 22, 38,
            -82, 88, -54, -4, 61, -90, 78, -31
        },
        {
            25, -70, 90, -80, 43, 9, -57, 87, -87, 57, -9, -43, 80, -90, 70, -25, -25, 70, -90, 80, -43, -9, 57, -87,
            87, -57, 9, 43, -80, 90, -70, 25
        },
        {
            22, -61, 85, -90, 73, -38, -4, 46, -78, 90, -82, 54, -13, -31, 67, -88, 88, -67, 31, 13, -54, 82, -90, 78,
            -46, 4, 38, -73, 90, -85, 61, -22
        },
        {
            18, -50, 75, -89, 89, -75, 50, -18, -18, 50, -75, 89, -89, 75, -50, 18, 18, -50, 75, -89, 89, -75, 50, -18,
            -18, 50, -75, 89, -89, 75, -50, 18
        },
        {
            13, -38, 61, -78, 88, -90, 85, -73, 54, -31, 4, 22, -46, 67, -82, 90, -90, 82, -67, 46, -22, -4, 31, -54,
            73, -85, 90, -88, 78, -61, 38, -13
        },
        {
            9, -25, 43, -57, 70, -80, 87, -90, 90, -87, 80, -70, 57, -43, 25, -9, -9, 25, -43, 57, -70, 80, -87, 90,
            -90, 87, -80, 70, -57, 43, -25, 9
        },
        {
            4, -13, 22, -31, 38, -46, 54, -61, 67, -73, 78, -82, 85, -88, 90, -90, 90, -90, 88, -85, 82, -78, 73, -67,
            61, -54, 46, -38, 31, -22, 13, -4
        }
    };
    private static final int MAX_TRANSFORM_SIZE = 32;
    private static final int[] levelScale = {40, 45, 51, 57, 64, 72};
    // Table 8-5
    private static final int[] intraPredAngleTable = {
        0, 0, 32, 26, 21, 17, 13, 9, 5, 2, 0, -2, -5, -9, -13, -17, -21, -26, -32, -26, -21, -17, -13, -9, -5, -2, 0, 2,
        5, 9, 13, 17, 21, 26, 32
    };
    // Table 8-6 further specifies the mapping table between predModeIntra and the inverse angle parameter invAngle.
    private static final int[] invAngleTable = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -4096, -1638, -910, -630, -482, -390, -315, -256, -315, -390, -482, -630, -910,
        -1638, -4096
    };

    public TransformUnit(
            BitStreamWithNalSupport stream,
            SliceSegmentHeader header,
            PartMode partMode,
            int x0,
            int y0,
            int xBase,
            int yBase,
            int log2TrafoSize,
            int trafoDepth,
            int blkIdx) {
        PicParameterSetRbsp pps = header.pps;
        SeqParameterSetRbsp sps = header.pps.sps;
        HeicPicture picture = header.parentPicture;

        long chromaArrayType = header.pps.sps.getChromaArrayType();

        int log2TrafoSizeC = Math.max(2, log2TrafoSize - (chromaArrayType == 3 ? 0 : 1));
        int cbfDepthC = trafoDepth - (chromaArrayType != 3 && log2TrafoSize == 2 ? 1 : 0);
        int xC = (chromaArrayType != 3 && log2TrafoSize == 2) ? xBase : x0;
        int yC = (chromaArrayType != 3 && log2TrafoSize == 2) ? yBase : y0;

        int offset = 1 << log2TrafoSizeC;

        boolean cbfLuma = picture.cbfLuma.getBit(x0, y0, trafoDepth);
        boolean cbfChroma = picture.cbfCb.getBit(xC, yC, cbfDepthC)
                || picture.cbfCr.getBit(xC, yC, cbfDepthC)
                || (chromaArrayType == 2
                        && (picture.cbfCb.getBit(xC, yC + (offset), cbfDepthC)
                                || picture.cbfCr.getBit(xC, yC + (offset), cbfDepthC)));

        if (cbfLuma || cbfChroma) {
            int minCbLog2SizeY = sps.getMinCbLog2SizeY() & 0xFF;
            int xP = (x0 >> minCbLog2SizeY) << minCbLog2SizeY;
            int yP = (y0 >> minCbLog2SizeY) << minCbLog2SizeY;
            int nCbS = 1 << minCbLog2SizeY;

            Cabac streamCabac = stream.getCabac();
            if (pps.ppsSccExt.residualAdaptiveColourTransformEnabledFlag
                    && (picture.cuPredMode.get(x0, y0) == PredMode.MODE_INTER
                            || (partMode == PartMode.part2Nx2N && (picture.intraChromaPredMode.get(x0, y0) & 0xFF) == 4)
                            || ((picture.intraChromaPredMode.get(xP, yP) & 0xFF) == 4
                                    && (picture.intraChromaPredMode.get(xP + nCbS / 2, yP) & 0xFF) == 4
                                    && (picture.intraChromaPredMode.get(xP, yP + nCbS / 2) & 0xFF) == 4
                                    && (picture.intraChromaPredMode.get(xP + nCbS / 2, yP + nCbS / 2) & 0xFF) == 4))) {
                picture.tuResidualActFlag.set(x0, y0, streamCabac.readTuResidualActFlag());
            }

            boolean decodeQp = false;

            DecoderContext streamContext = stream.getContext();
            // delta_qp
            if (header.pps.cuQpDeltaEnabledFlag && !streamContext.isCuQpDeltaCoded()) {
                decodeQp = true;
                streamContext.setCuQpDeltaCoded(true);
                int cuQpDeltaAbs = streamCabac.readCuQpDeltaAbs();

                if (cuQpDeltaAbs != 0) {
                    boolean cuQpDeltaSignFlag = streamCabac.readCuQpDeltaSignFlag();

                    streamContext.setCuQpDeltaVal(cuQpDeltaAbs * (cuQpDeltaSignFlag ? -1 : 1));
                }
            }

            // chroma_qp_offset
            if (cbfChroma && !streamContext.cuTransquantBypassFlag) {
                if (header.cuChromaQpOffsetEnabledFlag && !streamContext.isCuChromaQpOffsetCoded()) {
                    decodeQp = true;
                    streamContext.setCuChromaQpOffsetCoded(true);
                    boolean cuChromaQpOffsetFlag = streamCabac.readCuChromaQpOffsetFlag();

                    if (cuChromaQpOffsetFlag && header.pps.ppsRangeExt.chromaQpOffsetListLenMinus1 > 0) {
                        int cuChromaQpOffsetIdx =
                                streamCabac.readCuChromaQpOffsetIdx(header.pps.ppsRangeExt.chromaQpOffsetListLenMinus1);

                        streamContext.setCuQpOffsetCb(pps.ppsRangeExt.cbQpOffsetList[cuChromaQpOffsetIdx]);
                        streamContext.setCuQpOffsetCr(pps.ppsRangeExt.crQpOffsetList[cuChromaQpOffsetIdx]);
                    } else {
                        streamContext.setCuQpOffsetCb(0);
                        streamContext.setCuQpOffsetCr(0);
                    }
                }
            }

            if (decodeQp) {
                streamContext.derivationOfQuantizationParameters(stream, picture, x0, y0, xBase, yBase);
            }
        }

        if (cbfLuma) {
            new ResidualCoding(stream, header, x0, y0, log2TrafoSize, 0);
        }

        decode(stream, header.parentPicture, x0, y0, trafoDepth, log2TrafoSize, 0);

        // if (cbfLuma || cbfChroma)
        if ((sps.chromaFormatIdc & 0xFFFFFFFFL) != 0) // this may be incorrect
        {
            if (log2TrafoSize > 2 || (chromaArrayType & 0xFFFFFFFFL) == 3) {
                boolean resScaleNeeded = cbfLuma
                        && header.pps.ppsRangeExt.crossComponentPredictionEnabledFlag
                        && (picture.cuPredMode.get(x0, y0) == PredMode.MODE_INTER
                                || (picture.intraChromaPredMode.get(x0, y0) & 0xFF) == 4);

                // Cb
                if (resScaleNeeded) {
                    requireNoCrossComponentPrediction(getResScaleVal(stream, 0));
                }

                if (picture.cbfCb.getBit(x0, y0, trafoDepth)) {
                    new ResidualCoding(stream, header, x0, y0, log2TrafoSizeC, 1);
                }

                decode(
                        stream,
                        header.parentPicture,
                        x0 / (sps.getSubWidthC() & 0xFF),
                        y0 / (sps.getSubHeightC() & 0xFF),
                        trafoDepth,
                        log2TrafoSizeC,
                        1);

                if ((chromaArrayType & 0xFFFFFFFFL) == 2) {
                    if (picture.cbfCb.getBit(x0, y0 + offset, trafoDepth)) {
                        new ResidualCoding(stream, header, x0, y0 + offset, log2TrafoSizeC, 1);
                    }

                    decode(
                            stream,
                            header.parentPicture,
                            x0 / (sps.getSubWidthC() & 0xFF),
                            y0 / (sps.getSubHeightC() & 0xFF) + offset,
                            trafoDepth,
                            log2TrafoSizeC,
                            1);
                }

                // Cr
                if (resScaleNeeded) {
                    requireNoCrossComponentPrediction(getResScaleVal(stream, 1));
                }

                if (picture.cbfCr.getBit(x0, y0, trafoDepth)) {
                    new ResidualCoding(stream, header, x0, y0, log2TrafoSizeC, 2);
                }

                decode(
                        stream,
                        header.parentPicture,
                        x0 / (sps.getSubWidthC() & 0xFF),
                        y0 / (sps.getSubHeightC() & 0xFF),
                        trafoDepth,
                        log2TrafoSizeC,
                        2);

                if ((chromaArrayType & 0xFFFFFFFFL) == 2) {
                    if (picture.cbfCr.getBit(x0, y0 + offset, trafoDepth)) {
                        new ResidualCoding(stream, header, x0, y0 + offset, log2TrafoSizeC, 2);
                    }

                    decode(
                            stream,
                            header.parentPicture,
                            x0 / (sps.getSubWidthC() & 0xFF),
                            y0 / (sps.getSubHeightC() & 0xFF) + offset,
                            trafoDepth,
                            log2TrafoSizeC,
                            2);
                }
            } else if (blkIdx == 3) {
                // Cb
                if (picture.cbfCb.getBit(xBase, yBase, trafoDepth - 1)) {
                    new ResidualCoding(stream, header, xBase, yBase, log2TrafoSize, 1);
                }

                decode(
                        stream,
                        header.parentPicture,
                        xBase / (sps.getSubWidthC() & 0xFF),
                        yBase / (sps.getSubHeightC() & 0xFF),
                        trafoDepth,
                        log2TrafoSize,
                        1);

                if (chromaArrayType == 2) {
                    if (picture.cbfCb.getBit(xBase, yBase + offset, trafoDepth - 1)) {
                        new ResidualCoding(stream, header, xBase, yBase + offset, log2TrafoSize, 1);
                    }

                    decode(
                            stream,
                            header.parentPicture,
                            xBase / (sps.getSubWidthC() & 0xFF),
                            yBase / (sps.getSubHeightC() & 0xFF) + offset,
                            trafoDepth,
                            log2TrafoSize,
                            1);
                }

                // Cr
                if (picture.cbfCr.getBit(xBase, yBase, trafoDepth - 1)) {
                    new ResidualCoding(stream, header, xBase, yBase, log2TrafoSize, 2);
                }

                decode(
                        stream,
                        header.parentPicture,
                        xBase / (sps.getSubWidthC() & 0xFF),
                        yBase / (sps.getSubHeightC() & 0xFF),
                        trafoDepth,
                        log2TrafoSize,
                        2);

                if (chromaArrayType == 2) {
                    if (picture.cbfCr.getBit(xBase, yBase + offset, trafoDepth - 1)) {
                        new ResidualCoding(stream, header, xBase, yBase + offset, log2TrafoSize, 2);
                    }

                    decode(
                            stream,
                            header.parentPicture,
                            xBase / (sps.getSubWidthC() & 0xFF),
                            yBase / (sps.getSubHeightC() & 0xFF) + offset,
                            trafoDepth,
                            log2TrafoSize,
                            2);
                }
            }
        }
    }

    private void decode(
            BitStreamWithNalSupport stream,
            HeicPicture picture,
            int x0,
            int y0,
            int trafoDepth,
            int log2TrafoSize,
            int cIdx) {
        PredMode cuPredMode = picture.cuPredMode.get(x0, y0);
        IntraPredMode intraPredMode;

        //        int residualDpcm = 0;
        int nTbS = 1 << log2TrafoSize;
        int[][] samples; // = new int[nTbS][nTbS];

        if (cuPredMode == PredMode.MODE_INTRA) {
            intraPredMode = cIdx == 0
                    ? picture.intraPredModeY.get(x0, y0)
                    : picture.intraPredModeC.get(
                            x0 * (picture.sps.getSubWidthC() & 0xFF), y0 * (picture.sps.getSubHeightC() & 0xFF));

            samples = decodeIntraPrediction(stream, picture, x0, y0, intraPredMode, nTbS, cIdx);

            //            residualDpcm = picture.sps.sps_range_ext.implicit_rdpcm_enabled_flag &&
            //                    (stream.getContext().cu_transquant_bypass_flag ||
            // stream.getContext().transform_skip_flag[cIdx]) &&
            //                    (intraPredMode.ordinal() == 10 || intraPredMode.ordinal() == 26) ? 1 : 0;
        } else {
            throw new UnsupportedOperationException();
            // MODE_INTER is valid only for animations
        }

        // if (cbf)
        // {
        //    scale_coefficients();
        // }

        ResidualBuffers residualBuffers = stream.getContext().getResidualBuffers();
        int bitDepth = ((cIdx == 0) ? picture.sps.getBitDepthY() : picture.sps.getBitDepthC()) & 0xFF;
        boolean highColorRange = (picture.sps.getBitDepthY() & 0xFF) > 8 || (picture.sps.getBitDepthC() & 0xFF) > 8;

        // a block without coded coefficients has a zero residual: the prediction is the reconstruction
        int[] residual = residualBuffers.isEmpty()
                ? null
                : scalingAndTransformation(stream, picture, x0, y0, cuPredMode, cIdx, nTbS, residualBuffers);

        for (int x = 0; x < nTbS; x++) {
            int[] predictedColumn = samples[x];
            for (int y = 0; y < nTbS; y++) {
                int reconstructed = MathExtra.clipBitDepth(
                        predictedColumn[y] + (residual == null ? 0 : residual[x * nTbS + y]), bitDepth);
                if (highColorRange) {
                    picture.pixelsHighColorRange[cIdx][x0 + x][y0 + y] = reconstructed & 0xFFFF;
                } else {
                    picture.pixels[cIdx][x0 + x][y0 + y] = (byte) reconstructed;
                }
            }
        }
        residualBuffers.clear();
    }

    // 8.4.4.2.1 General intra sample prediction
    private int[][] decodeIntraPrediction(
            BitStreamWithNalSupport stream,
            HeicPicture picture,
            int x0,
            int y0,
            IntraPredMode intraPredMode,
            int nTbS,
            int cIdx) {
        NeighbouringSamples samples = NeighbouringSamplesGenerator.generate(picture, x0, y0, nTbS, cIdx);

        if (!picture.sps.spsRangeExt.intraSmoothingDisabledFlag
                && (cIdx == 0 || (picture.sps.getChromaArrayType() & 0xFFFFFFFFL) == 3)) // chroma 4:4:4
        {
            samples = filteringOfNeighbouringSamples(stream, picture, samples, nTbS, cIdx, intraPredMode);
        }

        switch (intraPredMode) {
            case INTRA_PLANAR:
                return decodeIntraPredictionPlanar(picture, nTbS, samples);
            case INTRA_DC:
                return decodeIntraPredictionDc(picture, nTbS, cIdx, samples);
            default:
                return decodeIntraPredictionAngular(stream, picture, nTbS, cIdx, intraPredMode, samples);
        }
    }

    // 8.4.4.2.3 Filtering process of neighbouring samples
    private NeighbouringSamples filteringOfNeighbouringSamples(
            BitStreamWithNalSupport stream,
            HeicPicture picture,
            NeighbouringSamples p,
            int nTbS,
            int cIdx,
            IntraPredMode intraPredMode) {
        // Inputs to this process are:
        // – the neighbouring samples p[x][y], with x = −1, y = −1..nTbS * 2 − 1 and x = 0..nTbS * 2 − 1, y = −1,
        // – a variable nTbS specifying the transform block size.
        // Outputs of this process are the filtered samples pF[x][y], with x = −1, y = −1..nTbS * 2 − 1 and x = 0..nTbS
        // * 2 − 1, y = −1.

        boolean filterFlag;

        if (intraPredMode == IntraPredMode.INTRA_DC || nTbS == 4) {
            filterFlag = false;
        } else {
            int minDistVerHor =
                    Math.min(Math.abs(intraPredMode.ordinal() - 26), Math.abs(intraPredMode.ordinal() - 10));

            switch (nTbS) {
                case 8:
                    filterFlag = minDistVerHor > 7;
                    break;
                case 16:
                    filterFlag = minDistVerHor > 1;
                    break;
                case 32:
                    filterFlag = minDistVerHor > 0;
                    break;
                default:
                    filterFlag = false;
                    break;
            }
        }

        if (filterFlag) {
            boolean biIntFlag = picture.sps.strongIntraSmoothingEnabledFlag
                    && cIdx == 0
                    && nTbS == 32
                    && Math.abs((p.get(-1, -1) & 0xFFFF)
                                    + (p.get(nTbS * 2 - 1, -1) & 0xFFFF)
                                    - 2 * (p.get(nTbS - 1, -1) & 0xFFFF))
                            < (1 << ((picture.sps.getBitDepthY() & 0xFF) - 5))
                    && Math.abs((p.get(-1, -1) & 0xFFFF)
                                    + (p.get(-1, nTbS * 2 - 1) & 0xFFFF)
                                    - 2 * (p.get(-1, nTbS - 1) & 0xFFFF))
                            < (1 << ((picture.sps.getBitDepthY() & 0xFF) - 5));

            NeighbouringSamples pF = new NeighbouringSamples(nTbS);

            if (biIntFlag) {
                pF.set(-1, -1, p.get(-1, -1));
                pF.set(-1, 63, p.get(-1, 63));
                pF.set(63, -1, p.get(63, -1));

                for (int i = 0; i < 63; i++) {
                    pF.set(
                            -1,
                            i,
                            (((63 - i) * (p.get(-1, -1) & 0xFFFF) + (i + 1) * (p.get(-1, 63) & 0xFFFF) + 32) >> 6)
                                    & 0xFFFF);
                    pF.set(
                            i,
                            -1,
                            (((63 - i) * (p.get(-1, -1) & 0xFFFF) + (i + 1) * (p.get(63, -1) & 0xFFFF) + 32) >> 6)
                                    & 0xFFFF);
                }
            } else {
                pF.set(
                        -1,
                        -1,
                        (((p.get(-1, 0) & 0xFFFF) + 2 * (p.get(-1, -1) & 0xFFFF) + (p.get(0, -1) & 0xFFFF) + 2) >> 2)
                                & 0xFFFF);
                pF.set(-1, nTbS * 2 - 1, p.get(-1, nTbS * 2 - 1));
                pF.set(nTbS * 2 - 1, -1, p.get(nTbS * 2 - 1, -1));

                for (int i = 0; i < nTbS * 2 - 1; i++) {
                    pF.set(
                            -1,
                            i,
                            (((p.get(-1, i + 1) & 0xFFFF)
                                                    + 2 * (p.get(-1, i) & 0xFFFF)
                                                    + (p.get(-1, i - 1) & 0xFFFF)
                                                    + 2)
                                            >> 2)
                                    & 0xFFFF);
                    pF.set(
                            i,
                            -1,
                            (((p.get(i - 1, -1) & 0xFFFF)
                                                    + 2 * (p.get(i, -1) & 0xFFFF)
                                                    + (p.get(i + 1, -1) & 0xFFFF)
                                                    + 2)
                                            >> 2)
                                    & 0xFFFF);
                }
            }

            p = pF;
        }

        return p;
    }

    private int[][] decodeIntraPredictionPlanarBuffer;

    // 8.4.4.2.4 Specification of intra prediction mode INTRA_PLANAR
    private int[][] decodeIntraPredictionPlanar(HeicPicture picture, int nTbS, NeighbouringSamples p) {
        // Inputs to this process are:
        // – the neighbouring samples p[x][y], with x = -1, y = -1..nTbS * 2 - 1 and x = 0..nTbS * 2 - 1, y = -1,
        // – a variable nTbS specifying the transform block size.
        // Outputs of this process are the predicted samples predSamples[x][y], with x, y = 0..nTbS - 1.

        if (decodeIntraPredictionPlanarBuffer == null) {
            decodeIntraPredictionPlanarBuffer = new int[nTbS][nTbS];
        }

        int[][] predSamples = decodeIntraPredictionPlanarBuffer;

        int tbsShift = MathUtils.f64S32(MathUtils.log(nTbS, 2)) + 1;
        for (int x = 0; x < nTbS; x++) {
            int[] predSampleX = predSamples[x];
            int tbs1X = nTbS - 1 - x;
            for (int y = 0; y < nTbS; y++) {
                predSampleX[y] = (tbs1X * (p.get(-1, y) & 0xFFFF)
                                + (x + 1) * (p.get(nTbS, -1) & 0xFFFF)
                                + (nTbS - 1 - y) * (p.get(x, -1) & 0xFFFF)
                                + (y + 1) * (p.get(-1, nTbS) & 0xFFFF)
                                + nTbS)
                        >> tbsShift;
            }
        }

        return predSamples;
    }

    private int[][] decodeIntraPredictionDcBuffer;

    // 8.4.4.2.5 Specification of intra prediction mode INTRA_DC
    private int[][] decodeIntraPredictionDc(HeicPicture picture, int nTbS, int cIdx, NeighbouringSamples p) {
        // Inputs to this process are:
        // – the neighbouring samples p[x][y], with x = −1, y = −1..nTbS * 2 − 1 and x = 0..nTbS * 2 − 1, y = −1,
        // – a variable nTbS specifying the transform block size,
        // – a variable cIdx specifying the colour component of the current block.
        // Outputs of this process are the predicted samples predSamples[x][y], with x, y = 0..nTbS − 1.

        int dcVal = nTbS;
        for (int i = 0; i < nTbS; i++) {
            dcVal += p.get(i, -1) & 0xFFFF;
            dcVal += p.get(-1, i) & 0xFFFF;
        }
        dcVal >>= (MathUtils.f64S32(MathUtils.log(nTbS, 2)) + 1);

        if (decodeIntraPredictionDcBuffer == null) {
            decodeIntraPredictionDcBuffer = new int[nTbS][nTbS];
        }

        int[][] predSamples = decodeIntraPredictionDcBuffer;

        if (cIdx == 0 && nTbS < 32) {
            int[] predSample0 = predSamples[0];
            predSample0[0] = ((p.get(-1, 0) & 0xFFFF) + 2 * dcVal + (p.get(0, -1) & 0xFFFF) + 2) >> 2;

            for (int x = 1; x < nTbS; x++) predSamples[x][0] = ((p.get(x, -1) & 0xFFFF) + 3 * dcVal + 2) >> 2;

            for (int y = 1; y < nTbS; y++) predSample0[y] = ((p.get(-1, y) & 0xFFFF) + 3 * dcVal + 2) >> 2;

            for (int x = 1; x < nTbS; x++) {
                int[] predSampleX = predSamples[x];
                Arrays.fill(predSampleX, 1, nTbS, dcVal);
                //                for (int y = 1; y < nTbS; y++)
                //                {
                //                    predSampleX[y] = dcVal;
                //                }
            }
        } else {
            for (int x = 0; x < nTbS; x++) {
                int[] predSampleX = predSamples[x];
                Arrays.fill(predSampleX, 0, nTbS, dcVal);
                //                for (int y = 0; y < nTbS; y++)
                //                {
                //                    predSampleX[y] = dcVal;
                //                }
            }
        }

        return predSamples;
    }

    private int[] decodeIntraPredictionAngularBuffer;

    // 8.4.4.2.6 Specification of intra prediction mode INTRA_ANGULAR
    private int[][] decodeIntraPredictionAngular(
            BitStreamWithNalSupport stream,
            HeicPicture picture,
            int nTbS,
            int cIdx,
            IntraPredMode intraPredMode,
            NeighbouringSamples p) {
        // Inputs to this process are:
        // – the intra prediction mode predModeIntra,
        // – the neighbouring samples p[x][y], with x = −1, y = −1..nTbS*2−1 and x = 0..nTbS*2−1, y = −1,
        // – a variable nTbS specifying the transform block size,
        // – a variable cIdx specifying the colour component of the current block.
        // Outputs of this process are the predicted samples predSamples[x][y], with x, y = 0..nTbS − 1.

        int[][] predSamples = new int[nTbS][nTbS];

        int intraPredAngle = intraPredAngleTable[intraPredMode.ordinal()];

        boolean disableIntraBoundaryFilter = false;
        if (picture.sps.spsSccExt.intraBoundaryFilteringDisabledFlag) {
            disableIntraBoundaryFilter = true;
        } else if (picture.sps.spsRangeExt.implicitRdpcmEnabledFlag && stream.getContext().cuTransquantBypassFlag) {
            disableIntraBoundaryFilter = true;
        }

        if (decodeIntraPredictionAngularBuffer == null) {
            decodeIntraPredictionAngularBuffer = new int[3 * nTbS + 1];
        }

        int[] reference = decodeIntraPredictionAngularBuffer;

        if (intraPredMode.ordinal() >= 18) {
            for (int x = 0; x <= nTbS; x++) reference[x + nTbS] = p.get(x - 1, -1) & 0xFFFF;

            if (intraPredAngle < 0) {
                int invAngle = invAngleTable[intraPredMode.ordinal()];

                if ((nTbS * intraPredAngle) >> 5 < -1) {
                    for (int x = (nTbS * intraPredAngle) >> 5; x <= -1; x++)
                        reference[x + nTbS] = p.get(-1, -1 + ((x * invAngle + 128) >> 8)) & 0xFFFF;
                }
            } else {
                for (int x = nTbS + 1; x <= 2 * nTbS; x++) reference[x + nTbS] = p.get(x - 1, -1) & 0xFFFF;
            }

            for (int x = 0; x < nTbS; x++) {
                int[] predSampleX = predSamples[x];
                for (int y = 0; y < nTbS; y++) {
                    int iIdx = ((y + 1) * intraPredAngle) >> 5;
                    int iFact = ((y + 1) * intraPredAngle) & 31;

                    if (iFact != 0) {
                        predSampleX[y] = ((32 - iFact) * reference[x + nTbS + iIdx + 1]
                                        + iFact * reference[x + nTbS + iIdx + 2]
                                        + 16)
                                >> 5;
                    } else {
                        predSampleX[y] = reference[x + nTbS + iIdx + 1];
                    }
                }
            }

            if (intraPredMode.ordinal() == 26 && cIdx == 0 && nTbS < 32 && !disableIntraBoundaryFilter) {
                int bitDepthY = picture.sps.getBitDepthY() & 0xFF;
                int[] predSample0 = predSamples[0];
                for (int y = 0; y < nTbS; y++) {
                    predSample0[y] = MathExtra.clipBitDepth(
                            (p.get(0, -1) & 0xFFFF) + (((p.get(-1, y) & 0xFFFF) - (p.get(-1, -1) & 0xFFFF)) >> 1),
                            bitDepthY);
                }
            }
        } else {
            for (int x = 0; x <= nTbS; x++) reference[x + nTbS] = p.get(-1, x - 1) & 0xFFFF;

            if (intraPredAngle < 0) {
                int invAngle = invAngleTable[intraPredMode.ordinal()];

                if ((nTbS * intraPredAngle) >> 5 < -1) {
                    for (int x = (nTbS * intraPredAngle) >> 5; x <= -1; x++)
                        reference[x + nTbS] = p.get(-1 + ((x * invAngle + 128) >> 8), -1) & 0xFFFF;
                }
            } else {
                for (int x = nTbS + 1; x <= 2 * nTbS; x++) reference[x + nTbS] = p.get(-1, x - 1) & 0xFFFF;
            }

            for (int x = 0; x < nTbS; x++) {
                int[] predSampleX = predSamples[x];
                for (int y = 0; y < nTbS; y++) {
                    int iIdx = ((x + 1) * intraPredAngle) >> 5;
                    int iFact = ((x + 1) * intraPredAngle) & 31;

                    if (iFact != 0) {
                        predSampleX[y] = ((32 - iFact) * reference[y + nTbS + iIdx + 1]
                                        + iFact * reference[y + nTbS + iIdx + 2]
                                        + 16)
                                >> 5;
                    } else {
                        predSampleX[y] = reference[y + nTbS + iIdx + 1];
                    }
                }
            }

            if (intraPredMode.ordinal() == 10 && cIdx == 0 && nTbS < 32 && !disableIntraBoundaryFilter) {
                for (int x = 0; x < nTbS; x++)
                    predSamples[x][0] = MathExtra.clipBitDepth(
                            (p.get(-1, 0) & 0xFFFF) + (((p.get(x, -1) & 0xFFFF) - (p.get(-1, -1) & 0xFFFF)) >> 1),
                            picture.sps.getBitDepthY() & 0xFF);
            }
        }

        return predSamples;
    }

    // 8.6.2 Scaling and transformation process, from the coefficients in the residual buffers
    private int[] scalingAndTransformation(
            BitStreamWithNalSupport stream,
            HeicPicture picture,
            int xTbY,
            int yTbY,
            PredMode cuPredMode,
            int cIdx,
            int nTbS,
            ResidualBuffers residualBuffers) {
        SliceSegmentHeader sliceHeader = picture.getSliceHeaders().get(picture.sliceHeaderIndex.get(xTbY, yTbY));

        int bitDepth = ((cIdx == 0) ? picture.sps.getBitDepthY() : picture.sps.getBitDepthC()) & 0xFF;
        int bdShift = Math.max(20 - bitDepth, picture.sps.spsRangeExt.extendedPrecisionProcessingFlag ? 11 : 0);
        int log2BlockSize = Integer.numberOfTrailingZeros(nTbS);
        int tsShift = 5 + log2BlockSize;
        boolean rotateCoeffs = picture.sps.spsRangeExt.transformSkipRotationEnabledFlag
                && nTbS == 4
                && cuPredMode == PredMode.MODE_INTRA;
        int[] residual = residualBuffers.residual;

        DecoderContext streamContext = stream.getContext();
        if (streamContext.cuTransquantBypassFlag) {
            for (int x = 0; x < nTbS; x++) {
                for (int y = 0; y < nTbS; y++) {
                    residual[x * nTbS + y] = rotateCoeffs
                            ? residualBuffers.coefficient(nTbS - x - 1, nTbS - y - 1)
                            : residualBuffers.coefficient(x, y);
                }
            }
            return residual;
        }

        int qP = switch (cIdx) {
            case 1 -> streamContext.getQpCb();
            case 2 -> streamContext.getQpCr();
            default ->
                MathExtra.clip3(
                        0,
                        51 + (picture.sps.getQpBdOffsetY() & 0xFF),
                        streamContext.getQpY()
                                + (picture.tuResidualActFlag.get(xTbY, yTbY)
                                        ? picture.pps.getPpsActQpOffsetY() + sliceHeader.sliceActYQpOffset
                                        : 0));
        };

        int[] scaled = scaleCoefficients(stream, picture, cuPredMode, cIdx, nTbS, qP, residualBuffers);

        if (streamContext.transformSkipFlag[cIdx]) {
            for (int x = 0; x < nTbS; x++) {
                for (int y = 0; y < nTbS; y++) {
                    int scaledIndex = rotateCoeffs ? (nTbS - x - 1) * nTbS + (nTbS - y - 1) : x * nTbS + y;
                    residual[x * nTbS + y] = scaled[scaledIndex] << tsShift;
                }
            }
        } else {
            transformScaledCoefficients(picture, cuPredMode, cIdx, nTbS, residualBuffers);
        }

        int offset = 1 << (bdShift - 1);
        for (int index = 0; index < nTbS * nTbS; index++) {
            residual[index] = (residual[index] + offset) >> bdShift;
        }
        return residual;
    }

    // 8.6.3 Scaling process for transform coefficients
    private int[] scaleCoefficients(
            BitStreamWithNalSupport stream,
            HeicPicture picture,
            PredMode cuPredMode,
            int cIdx,
            int nTbS,
            int qP,
            ResidualBuffers residualBuffers) {
        int bitDepth = (cIdx == 0 ? picture.sps.getBitDepthY() : picture.sps.getBitDepthC()) & 0xFF;
        int log2TransformRange =
                picture.sps.spsRangeExt.extendedPrecisionProcessingFlag ? Math.max(15, bitDepth + 6) : 15;
        int log2BlockSize = Integer.numberOfTrailingZeros(nTbS);
        int bdShift = bitDepth + log2BlockSize + 10 - log2TransformRange;
        int coeffMin = cIdx == 0 ? picture.sps.getCoeffMinY() : picture.sps.getCoeffMinC();
        int coeffMax = cIdx == 0 ? picture.sps.getCoeffMaxY() : picture.sps.getCoeffMaxC();
        int roundingOffset = 1 << (bdShift - 1);

        int sizeId = log2BlockSize - 2;
        int matrixId = cIdx + (cuPredMode == PredMode.MODE_INTRA ? 0 : 3);
        int[][] scalingFactors = picture.scalingFactor[sizeId][matrixId];
        boolean flatScaling =
                !picture.sps.scalingListEnabledFlag || (stream.getContext().transformSkipFlag[cIdx] && nTbS > 4);

        int[] scaled = residualBuffers.scaled;
        for (int x = 0; x < nTbS; x++) {
            int[] scalingFactorColumn = scalingFactors[x];
            for (int y = 0; y < nTbS; y++) {
                int scalingFactor = flatScaling ? 16 : scalingFactorColumn[y];
                int coefficient = residualBuffers.coefficient(x, y);
                scaled[x * nTbS + y] = MathExtra.clip3(
                        coeffMin,
                        coeffMax,
                        ((coefficient * scalingFactor * levelScale[qP % 6] << (qP / 6)) + roundingOffset) >> bdShift);
            }
        }
        return scaled;
    }

    // 8.6.4.2 Transformation process: columns, intermediate clipping, then rows. Only the coefficients up to the last
    // non-zero one in each direction can contribute, so the sums stop there.
    private void transformScaledCoefficients(
            HeicPicture picture, PredMode cuPredMode, int cIdx, int nTbS, ResidualBuffers residualBuffers) {
        int coeffMin = cIdx == 0 ? picture.sps.getCoeffMinY() : picture.sps.getCoeffMinC();
        int coeffMax = cIdx == 0 ? picture.sps.getCoeffMaxY() : picture.sps.getCoeffMaxC();
        boolean discreteSineTransform = cuPredMode == PredMode.MODE_INTRA && nTbS == 4 && cIdx == 0;
        int[][] transMatrix = discreteSineTransform ? transMatrix4x4 : transMatrix32x32;
        int matrixRowStep = discreteSineTransform ? 1 : MAX_TRANSFORM_SIZE / nTbS;
        int lastColumn = residualBuffers.lastNonZeroX();
        int lastRow = residualBuffers.lastNonZeroY();

        int[] scaled = residualBuffers.scaled;
        int[] columnTransformed = residualBuffers.columnTransformed;
        int[] clipped = residualBuffers.clipped;
        int[] residual = residualBuffers.residual;

        for (int x = 0; x < nTbS; x++) {
            int columnStart = x * nTbS;
            for (int i = 0; i < nTbS; i++) {
                int sum = 0;
                if (x <= lastColumn) {
                    for (int j = 0; j <= lastRow; j++) {
                        sum += transMatrix[j * matrixRowStep][i] * scaled[columnStart + j];
                    }
                }
                columnTransformed[columnStart + i] = sum;
                clipped[columnStart + i] = MathExtra.clip3(coeffMin, coeffMax, (sum + 64) >> 7);
            }
        }

        for (int y = 0; y < nTbS; y++) {
            for (int i = 0; i < nTbS; i++) {
                int sum = 0;
                for (int j = 0; j <= lastColumn; j++) {
                    sum += transMatrix[j * matrixRowStep][i] * clipped[j * nTbS + y];
                }
                residual[i * nTbS + y] = sum;
            }
        }
    }

    // 8.6.4.2 Transformation process
    private int[] transformation(int nTbS, int[] x, int trType) {
        // Inputs to this process are:
        // – a variable nTbS specifying the sample size of scaled transform coefficients,
        // – a list of scaled transform coefficients x with elements x[j], with j = 0..nTbS − 1.
        // – a transform type variable trType
        // Output of this process is the list of transformed samples y with elements y[ i ], with i = 0..nTbS − 1.

        int[] y = new int[nTbS];
        int mult = 1 << (5 - MathUtils.f64S32(MathUtils.log(nTbS, 2)));

        if (trType == 1) {
            for (int i = 0; i < nTbS; i++) for (int j = 0; j < nTbS; j++) y[i] += transMatrix4x4[i][j] * x[j];
        } else {
            for (int i = 0; i < nTbS; i++) for (int j = 0; j < nTbS; j++) y[i] += transMatrix32x32[i][j * mult] * x[j];
        }

        return y;
    }

    // Cross-component prediction (7.3.8.12) is parsed but not applied: a stream that uses it cannot be decoded right
    private static void requireNoCrossComponentPrediction(int resScaleVal) {
        if (resScaleVal != 0) {
            throw new HeicFormatException("Cross-component prediction is not supported");
        }
    }

    private int getResScaleVal(BitStreamWithNalSupport stream, int c) {
        int log2ResScaleAbsPlus1 = stream.getCabac().readLog2ResScaleAbsPlus1(c);

        if (log2ResScaleAbsPlus1 == 0) {
            return 0;
        }

        boolean resScaleSignFlag = stream.getCabac().readResScaleSignFlag(c);

        return (1 << (log2ResScaleAbsPlus1 - 1)) * (resScaleSignFlag ? -1 : 1);
    }
}
