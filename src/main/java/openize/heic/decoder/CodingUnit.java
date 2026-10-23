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


class CodingUnit
{
    public static final  byte[] intraPredModeChroma422Map = {
            0, 1, 2, 2, 2, 2, 3, 5, 7, 8, 10, 12, 13, 15, 17, 18, 19, 20,
            21, 22, 23, 23, 24, 24, 25, 25, 26, 27, 27, 28, 28, 29, 29, 30, 31
    };
    PartMode partMode;
    boolean intraSplitFlag;
    boolean rqtRootCbf;
    /*UInt32*/ long maxTrafoDepth;


    public CodingUnit(BitStreamWithNalSupport stream, SliceSegmentHeader header,
                       int x0, int y0, int log2CbSize)
    {
        SeqParameterSetRbsp sps = header.pps.sps;
        HeicPicture picture = header.parentPicture;
        picture.setLog2CbSize(x0, y0, log2CbSize);

        partMode = PartMode.part2Nx2N;
        intraSplitFlag = false;

        stream.getContext().derivationOfQuantizationParameters(stream, picture, x0, y0, x0, y0);

        if (header.pps.transquantBypassEnabledFlag)
        {
            stream.getContext().cuTransquantBypassFlag = stream.getCabac().readCuTransquantBypassFlag();
        }

        if (header.sliceType != SliceType.I)
        {
            picture.cuSkipFlag[x0][y0] =
                    stream.getCabac().readCuSkipFlag(x0, y0, picture, picture.ctDepthByPosition[x0][y0]);
        }

        int nCbS = (1 << log2CbSize);

        picture.markTransformBlock(x0, y0, nCbS);
        if (stream.getContext().cuTransquantBypassFlag)
            picture.markNoFilter(x0, y0, nCbS);

        if (picture.cuSkipFlag[x0][y0])
        {
            picture.setCuPredMode(x0, y0, nCbS, PredMode.MODE_SKIP);
            throw new UnsupportedOperationException();
            // new prediction_unit(stream, header, x0, y0, nCbS, nCbS);
            // need this for deblocking filter
            // picture.SetPartMode(x0, y0, PART_2Nx2N);
        }
        else
        {
            if (header.sliceType != SliceType.I)
            {
                picture.setCuPredMode(x0, y0, nCbS,
                        stream.getCabac().readPredModeFlag() ? PredMode.MODE_INTRA : PredMode.MODE_INTER);
            }
            else
            {
                picture.setCuPredMode(x0, y0, nCbS, PredMode.MODE_INTRA);
            }

            if (header.pps.sps.spsSccExt.paletteModeEnabledFlag &&
                    picture.cuPredModeByPosition[x0][y0] == PredMode.MODE_INTRA &&
                    log2CbSize <= (sps.getMaxTbLog2SizeY() & 0xFF))
            {
                picture.paletteModeFlag[x0][y0] = stream.getCabac().readPaletteModeFlag();
            }

            if (picture.paletteModeFlag[x0][y0])
            {
                new PaletteCoding(stream, header, x0, y0, nCbS);
            }
            else
            {
                if (picture.cuPredModeByPosition[x0][y0] != PredMode.MODE_INTRA ||
                        log2CbSize == (sps.getMinCbLog2SizeY() & 0xFF))
                {
                    partMode = stream.getCabac().readPartMode(picture, picture.cuPredModeByPosition[x0][y0], log2CbSize);
                    intraSplitFlag =
                            picture.cuPredModeByPosition[x0][y0] == PredMode.MODE_INTRA &&
                                    partMode == PartMode.partNxN;
                }

                if (picture.cuPredModeByPosition[x0][y0] == PredMode.MODE_INTRA)
                {
                    if (partMode == PartMode.part2Nx2N &&
                            header.pps.sps.pcmEnabledFlag &&
                            log2CbSize >= sps.getLog2MinIpcmCbSizeY() &&
                            log2CbSize <= sps.getLog2MaxIpcmCbSizeY())
                    {
                        picture.setPcmFlag(x0, y0, nCbS, stream.getCabac().readPcmFlag());
                    }

                    if (picture.pcmFlag[x0][y0])
                    {
                        while (stream.notByteAligned())
                            stream.skipBits(1);  /* pcm_alignment_zero_bit */

                        new PcmSample(stream, header, log2CbSize);
                    }
                    else
                    {
                        int pbOffset = (partMode == PartMode.partNxN) ? (nCbS / 2) : nCbS;


                        for (int j = 0; j < nCbS; j += pbOffset)
                            for (int i = 0; i < nCbS; i += pbOffset)
                                picture.prevIntraLumaPredFlag[x0 + i][y0 + j] = stream.getCabac().readPrevIntraLumaPredFlag();

                        boolean availableL0 = picture.checkZScanAvailability(x0, y0, x0 - 1, y0);
                        boolean availableA0 = picture.checkZScanAvailability(x0, y0, x0, y0 - 1);

                        int log2blkSize = (partMode == PartMode.partNxN) ? (log2CbSize - 1) : log2CbSize;
                        //int pbSize = 1 << (log2blkSize - (sps.MinCbLog2SizeY - 1));
                        int pbSize = 1 << log2blkSize;


                        for (int j = 0; j < nCbS; j += pbOffset)
                        {
                            for (int i = 0; i < nCbS; i += pbOffset)
                            {
                                if (picture.prevIntraLumaPredFlag[x0 + i][y0 + j])
                                {
                                    picture.mpmIdx[x0 + i][y0 + j] = stream.getCabac().readMpmIdx();
                                }
                                else
                                {
                                    picture.remIntraLumaPredMode[x0 + i][y0 + j] = stream.getCabac().readRemIntraLumaPredMode();
                                }


                                IntraPredMode[] intraPredModeCandidated = picture.generateIntraPredModeCandidates(
                                        x0 + i, y0 + j, (1 << ((sps.getMinCbLog2SizeY() & 0xFF) - 1)), availableL0 || i > 0, availableA0 || j > 0);

                                /*IntraPredMode luma_mode =*/
                                picture.setIntraPredModeY(x0 + i, y0 + j, pbSize, intraPredModeCandidated);
                            }
                        }

                        if ((sps.getChromaArrayType() & 0xFFFFFFFFL) == 3)
                        {
                            // chroma 4:4:4

                            for (int j = 0; j < nCbS; j += pbOffset)
                            {
                                for (int i = 0; i < nCbS; i += pbOffset)
                                {
                                    picture.intraChromaPredMode[x0 + i][y0 + j] = stream.getCabac().readIntraChromaPredMode();

                                    IntraPredMode intraPredModeC = picture.deriveIntraPredModeIndex(
                                            picture.intraPredModeY[x0 + i][y0 + j],
                                            picture.intraChromaPredMode[x0 + i][y0 + j]);

                                    picture.setIntraPredModeC(x0 + i, y0 + j, pbSize, intraPredModeC);
                                }
                            }

                        }
                        else if ((sps.getChromaArrayType() & 0xFFFFFFFFL) != 0)
                        {
                            // chroma 4:2:0 and 4:2:2

                            picture.intraChromaPredMode[x0][y0] = stream.getCabac().readIntraChromaPredMode();

                            IntraPredMode intraPredModeC = picture.deriveIntraPredModeIndex(
                                    picture.intraPredModeY[x0][y0],
                                    picture.intraChromaPredMode[x0][y0]);

                            if ((sps.getChromaArrayType() & 0xFFFFFFFFL) == 2)
                            {
                                intraPredModeC = IntraPredMode.get((intraPredModeChroma422Map[intraPredModeC.ordinal()] & 0xFF));
                            }

                            picture.setIntraPredModeC(x0, y0, 1 << log2CbSize, intraPredModeC);
                            // not sure if that's correct
                        }
                    }
                }
                else
                {
                    switch (partMode)
                    {
                        case part2Nx2N:
                            new PredictionUnit(stream, header, x0, y0, nCbS, nCbS);
                            break;
                        case part2NxN:
                            new PredictionUnit(stream, header, x0, y0, nCbS, nCbS / 2);
                            new PredictionUnit(stream, header, x0, y0 + (nCbS / 2), nCbS, nCbS / 2);
                            break;
                        case partNx2N:
                            new PredictionUnit(stream, header, x0, y0, nCbS / 2, nCbS);
                            new PredictionUnit(stream, header, x0 + (nCbS / 2), y0, nCbS / 2, nCbS);
                            break;
                        case part2NxnU:
                            new PredictionUnit(stream, header, x0, y0, nCbS, nCbS / 4);
                            new PredictionUnit(stream, header, x0, y0 + (nCbS / 4), nCbS, nCbS * 3 / 4);
                            break;
                        case part2NxnD:
                            new PredictionUnit(stream, header, x0, y0, nCbS, nCbS * 3 / 4);
                            new PredictionUnit(stream, header, x0, y0 + (nCbS * 3 / 4), nCbS, nCbS / 4);
                            break;
                        case partNLx2N:
                            new PredictionUnit(stream, header, x0, y0, nCbS / 4, nCbS);
                            new PredictionUnit(stream, header, x0 + (nCbS / 4), y0, nCbS * 3 / 4, nCbS);
                            break;
                        case partNRx2N:
                            new PredictionUnit(stream, header, x0, y0, nCbS * 3 / 4, nCbS);
                            new PredictionUnit(stream, header, x0 + (nCbS * 3 / 4), y0, nCbS / 4, nCbS);
                            break;
                        case partNxN:
                        default:
                            new PredictionUnit(stream, header, x0, y0, nCbS / 2, nCbS / 2);
                            new PredictionUnit(stream, header, x0 + (nCbS / 2), y0, nCbS / 2, nCbS / 2);
                            new PredictionUnit(stream, header, x0, y0 + (nCbS / 2), nCbS / 2, nCbS / 2);
                            new PredictionUnit(stream, header, x0 + (nCbS / 2), y0 + (nCbS / 2), nCbS / 2, nCbS / 2);
                            break;
                    }
                }

                if (!picture.pcmFlag[x0][y0])
                {
                    rqtRootCbf = true;

                    boolean mergeFlag = picture.mergeFlag[x0][y0];

                    if (picture.cuPredModeByPosition[x0][y0] != PredMode.MODE_INTRA &&
                            !(partMode == PartMode.part2Nx2N && mergeFlag))
                    {
                        rqtRootCbf = stream.getCabac().readRqtRootCbf();
                    }

                    if (rqtRootCbf)
                    {
                        maxTrafoDepth =
                                (picture.cuPredModeByPosition[x0][y0] == PredMode.MODE_INTRA ?
                                        (((header.pps.sps.maxTransformHierarchyDepthIntra & 0xFFFFFFFFL) + (intraSplitFlag ? 1 : 0)) & 0xFFFFFFFFL) :
                                        (header.pps.sps.maxTransformHierarchyDepthInter & 0xFFFFFFFFL)) & 0xFFFFFFFFL;

                        new TransformTree(stream, header, intraSplitFlag, maxTrafoDepth, partMode,
                                x0, y0, x0, y0, log2CbSize, 0, 0);
                    }
                }
            }
        }
    }
}

