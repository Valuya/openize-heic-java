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

// 7.4.9.11 Residual coding semantics
class ResidualCoding {
    final int lastSigCoeffXPrefix;
    final int lastSigCoeffYPrefix;
    int lastSigCoeffXSuffix;
    int lastSigCoeffYSuffix;

    @SuppressWarnings({"ReassignedVariable", "SuspiciousNameCombination"})
    public ResidualCoding(
            BitStreamWithNalSupport stream, SliceSegmentHeader header, int x0, int y0, int log2TrafoSize, int cIdx) {
        PicParameterSetRbsp pps = header.pps;
        SeqParameterSetRbsp sps = header.pps.sps;
        HeicPicture picture = header.parentPicture;

        DecoderContext streamContext = stream.getContext();
        streamContext.transformSkipFlag[cIdx] = false;
        ResidualBuffers residualBuffers = streamContext.getResidualBuffers();
        residualBuffers.startBlock(1 << log2TrafoSize);

        Cabac streamCabac = stream.getCabac();
        if (header.pps.transformSkipEnabledFlag
                && !streamContext.cuTransquantBypassFlag
                && (log2TrafoSize <= (pps.getLog2MaxTransformSkipSize() & 0xFFFFFFFFL))) {
            streamContext.transformSkipFlag[cIdx] = streamCabac.readTransformSkipFlag(cIdx);
        }

        if (picture.cuPredMode.get(x0, y0) == PredMode.MODE_INTER
                && header.pps.sps.spsRangeExt.explicitRdpcmEnabledFlag
                && (streamContext.transformSkipFlag[cIdx] || streamContext.cuTransquantBypassFlag)) {
            picture.explicitRdpcmFlag.setBit(x0, y0, cIdx, streamCabac.readExplicitRdpcmFlag(cIdx));

            if (picture.explicitRdpcmFlag.getBit(x0, y0, cIdx)) {
                picture.explicitRdpcmDirFlag.setBit(x0, y0, cIdx, streamCabac.readExplicitRdpcmDirFlag(cIdx));
            }
        }

        lastSigCoeffXPrefix = streamCabac.readLastSigCoeffPrefix(log2TrafoSize, cIdx, CabacType.lastSigCoeffXPrefix);
        lastSigCoeffYPrefix = streamCabac.readLastSigCoeffPrefix(log2TrafoSize, cIdx, CabacType.lastSigCoeffYPrefix);

        if (lastSigCoeffXPrefix > 3) {
            lastSigCoeffXSuffix = streamCabac.readLastSigCoeffSuffix(lastSigCoeffXPrefix);
        }

        if (lastSigCoeffYPrefix > 3) {
            lastSigCoeffYSuffix = streamCabac.readLastSigCoeffSuffix(lastSigCoeffYPrefix);
        }

        int lastSignificantCoeffX = (lastSigCoeffXPrefix <= 3)
                ? lastSigCoeffXPrefix
                : (1 << ((lastSigCoeffXPrefix >> 1) - 1)) * (2 + (lastSigCoeffXPrefix & 1)) + lastSigCoeffXSuffix;

        int lastSignificantCoeffY = (lastSigCoeffYPrefix <= 3)
                ? lastSigCoeffYPrefix
                : (1 << ((lastSigCoeffYPrefix >> 1) - 1)) * (2 + (lastSigCoeffYPrefix & 1)) + lastSigCoeffYSuffix;

        // define scanIdx
        int scanIdx = 0;
        if (picture.cuPredMode.get(x0, y0) == PredMode.MODE_INTRA) {
            if ((log2TrafoSize == 2)
                    || (log2TrafoSize == 3 && cIdx == 0)
                    || (log2TrafoSize == 3 && (header.pps.sps.getChromaArrayType() & 0xFFFFFFFFL) == 3)) {
                IntraPredMode predModeIntra =
                        cIdx == 0 ? picture.intraPredModeY.get(x0, y0) : picture.intraPredModeC.get(x0, y0);

                if (predModeIntra.ordinal() >= 6 && predModeIntra.ordinal() <= 14) {
                    scanIdx = 2;
                }

                if (predModeIntra.ordinal() >= 22 && predModeIntra.ordinal() <= 30) {
                    scanIdx = 1;
                }
            }
        }

        if (scanIdx == 2) {
            int tmp = lastSignificantCoeffX;
            lastSignificantCoeffX = lastSignificantCoeffY;
            lastSignificantCoeffY = tmp;
        }

        int lastScanPos = 16;
        int lastSubBlock = (1 << (log2TrafoSize - 2)) * (1 << (log2TrafoSize - 2)) - 1;

        /*UInt32*/
        long xS, yS, xC, yC;
        byte[][][][] scanOrder = Scans.getScanOrder();
        byte[][] scanOrderLogTrafoSize2ScanIdx = scanOrder[log2TrafoSize - 2][scanIdx];
        do {
            if (lastScanPos == 0) {
                lastScanPos = 16;
                lastSubBlock--;
            }

            lastScanPos--;

            xS = scanOrderLogTrafoSize2ScanIdx[lastSubBlock][0] & 0xFF;
            yS = scanOrderLogTrafoSize2ScanIdx[lastSubBlock][1] & 0xFF;

            xC = ((xS << 2) + (scanOrder[2][scanIdx][lastScanPos][0] & 0xFFL));
            yC = ((yS << 2) + (scanOrder[2][scanIdx][lastScanPos][1] & 0xFFL));
        } while (((xC & 0xFFFFFFFFL) != lastSignificantCoeffX) || ((yC & 0xFFFFFFFFL) != lastSignificantCoeffY));

        int arrSize = 1 << log2TrafoSize;
        boolean[][] codedSubBlockFlag = new boolean[arrSize][arrSize];
        boolean[][] sigCoeffFlag = new boolean[arrSize][arrSize];

        int lastSubblockGreater1Ctx = 0;

        int lastInvocationCtxSet = 0;
        int lastInvocationGreater1Ctx = 0;
        boolean lastInvocationCoeffAbsLevelGreater1Flag = false;
        boolean firstSubblock = true;
        boolean firstCoeffInSubblock;

        for (int i = lastSubBlock; i >= 0; i--) {
            xS = scanOrderLogTrafoSize2ScanIdx[i][0] & 0xFF;
            yS = scanOrderLogTrafoSize2ScanIdx[i][1] & 0xFF;

            // int escapeDataPresent = 0;
            boolean inferSbDcSigCoeffFlag = false;

            if ((i < lastSubBlock) && (i > 0)) {
                codedSubBlockFlag[(int) (xS)][(int) (yS)] =
                        streamCabac.readCodedSubBlockFlag(xS, yS, codedSubBlockFlag, cIdx, log2TrafoSize);
                inferSbDcSigCoeffFlag = true;
            } else if (i == 0 || i == lastSubBlock) {
                codedSubBlockFlag[(int) (xS)][(int) (yS)] = true;
            }

            if (i == lastSubBlock) {
                xC = ((((xS & 0xFFFFFFFFL) << 2) & 0xFFFFFFFFL) + (scanOrder[2][scanIdx][lastScanPos][0] & 0xFF))
                        & 0xFFFFFFFFL;
                yC = ((((yS & 0xFFFFFFFFL) << 2) & 0xFFFFFFFFL) + (scanOrder[2][scanIdx][lastScanPos][1] & 0xFF))
                        & 0xFFFFFFFFL;
                sigCoeffFlag[(int) (xC)][(int) (yC)] = true;
            }

            for (int n = (i == lastSubBlock) ? lastScanPos - 1 : 15; n >= 0; n--) {
                xC = ((((xS & 0xFFFFFFFFL) << 2) & 0xFFFFFFFFL) + (scanOrder[2][scanIdx][n][0] & 0xFF)) & 0xFFFFFFFFL;
                yC = ((((yS & 0xFFFFFFFFL) << 2) & 0xFFFFFFFFL) + (scanOrder[2][scanIdx][n][1] & 0xFF)) & 0xFFFFFFFFL;

                if (codedSubBlockFlag[(int) (xS)][(int) (yS)]) {
                    if (n > 0 || !inferSbDcSigCoeffFlag) {
                        sigCoeffFlag[(int) (xC)][(int) (yC)] = streamCabac.readSigCoeffFlag(
                                picture,
                                streamContext.transformSkipFlag[cIdx],
                                (int) xC,
                                (int) yC,
                                codedSubBlockFlag,
                                cIdx,
                                log2TrafoSize,
                                scanIdx);

                        if (sigCoeffFlag[(int) (xC)][(int) (yC)]) {
                            inferSbDcSigCoeffFlag = false;
                        }
                    } else {
                        sigCoeffFlag[(int) (xC)][(int) (yC)] = true;
                    }
                }
            }

            int firstSigScanPos = 16;
            int lastSigScanPos = -1;

            byte numGreater1Flag = 0;
            int lastGreater1ScanPos = -1;
            boolean[] coeffAbsLevelGreater1Flag = new boolean[16];
            boolean[] coeffAbsLevelGreater2Flag = new boolean[16];
            int[] coeffAbsLevelRemaining = new int[16];

            firstCoeffInSubblock = true;

            for (int n = 15; n >= 0; n--) {
                xC = ((((xS & 0xFFFFFFFFL) << 2) & 0xFFFFFFFFL) + (scanOrder[2][scanIdx][n][0] & 0xFF)) & 0xFFFFFFFFL;
                yC = ((((yS & 0xFFFFFFFFL) << 2) & 0xFFFFFFFFL) + (scanOrder[2][scanIdx][n][1] & 0xFF)) & 0xFFFFFFFFL;

                if (sigCoeffFlag[(int) (xC)][(int) (yC)]) {
                    if ((numGreater1Flag & 0xFF) < 8) {
                        int[] referenceToLastInvocationCtxSet = {lastInvocationCtxSet};
                        int[] referenceToLastInvocationGreater1Ctx = {lastInvocationGreater1Ctx};
                        boolean[] referenceToLastInvocationCoeffAbsLevelGreater1Flag = {
                            lastInvocationCoeffAbsLevelGreater1Flag
                        };
                        coeffAbsLevelGreater1Flag[n] = streamCabac.readCoeffAbsLevelGreater1Flag(
                                cIdx,
                                i,
                                firstCoeffInSubblock,
                                firstSubblock,
                                lastSubblockGreater1Ctx,
                                /*ref*/ referenceToLastInvocationCtxSet,
                                /*ref*/ referenceToLastInvocationGreater1Ctx,
                                /*ref*/ referenceToLastInvocationCoeffAbsLevelGreater1Flag);
                        lastInvocationCtxSet = referenceToLastInvocationCtxSet[0];
                        lastInvocationGreater1Ctx = referenceToLastInvocationGreater1Ctx[0];
                        lastInvocationCoeffAbsLevelGreater1Flag = referenceToLastInvocationCoeffAbsLevelGreater1Flag[0];

                        numGreater1Flag++;

                        if (coeffAbsLevelGreater1Flag[n] && lastGreater1ScanPos == -1) {
                            lastGreater1ScanPos = n;
                        }
                        //                        else if (coeff_abs_level_greater1_flag[n])
                        //                        {
                        //                            escapeDataPresent = 1;
                        //                        }
                    }
                    //                    else
                    //                    {
                    //                        escapeDataPresent = 1;
                    //                    }

                    if (lastSigScanPos == -1) {
                        lastSigScanPos = n;
                    }

                    firstSigScanPos = n;
                    firstCoeffInSubblock = false;
                }
            }
            firstSubblock = false;
            lastSubblockGreater1Ctx = lastInvocationGreater1Ctx;

            boolean signHidden;
            IntraPredMode predModeIntra =
                    cIdx == 0 ? picture.intraPredModeY.get(x0, y0) : picture.intraPredModeC.get(x0, y0);

            if (streamContext.cuTransquantBypassFlag
                    || (picture.cuPredMode.get(x0, y0) == PredMode.MODE_INTRA
                            && sps.spsRangeExt.implicitRdpcmEnabledFlag
                            && streamContext.transformSkipFlag[cIdx]
                            && (predModeIntra.ordinal() == 10 || predModeIntra.ordinal() == 26))
                    || picture.explicitRdpcmFlag.getBit(x0, y0, cIdx)) {
                signHidden = false;
            } else {
                signHidden = (lastSigScanPos - firstSigScanPos) > 3;
            }

            if (lastGreater1ScanPos != -1) {
                coeffAbsLevelGreater2Flag[lastGreater1ScanPos] =
                        streamCabac.readCoeffAbsLevelGreater2Flag(cIdx, lastInvocationCtxSet);
                //                if (coeff_abs_level_greater2_flag[lastGreater1ScanPos])
                //                {
                //                    escapeDataPresent = 1;
                //                }
            }

            boolean[] coeffSignFlag = new boolean[16];
            byte[][] scanOrder2ScanIdx = scanOrder[2][scanIdx];
            for (int n = 15; n >= 0; n--) {
                xC = ((xS << 2) + (scanOrder2ScanIdx[n][0] & 0xFFL));
                yC = ((yS << 2) + (scanOrder2ScanIdx[n][1] & 0xFFL));

                if (sigCoeffFlag[(int) (xC)][(int) (yC)]
                        && (!header.pps.signDataHidingEnabledFlag || !signHidden || (n != firstSigScanPos))) {
                    coeffSignFlag[n] = streamCabac.readCoeffSignFlag();
                }
            }

            int numSigCoeff = 0;
            int sumAbsLevel = 0;
            int transCoeffLevel;

            int sbType = (cIdx == 0) ? 2 : 0;
            if (streamContext.transformSkipFlag[cIdx] || streamContext.cuTransquantBypassFlag) {
                sbType++;
            }

            int initRiceValue = (streamCabac.statCoeff[sbType] & 0xFF) / 4;
            if (!sps.spsRangeExt.persistentRiceAdaptationEnabledFlag) {
                initRiceValue = 0;
            }

            int cLastAbsLevel;
            int cLastRiceParam = initRiceValue;
            boolean firstInvoke = true;

            scanOrder2ScanIdx = scanOrder[2][scanIdx];
            for (int n = 15; n >= 0; n--) {
                xC = ((xS << 2) + (scanOrder2ScanIdx[n][0] & 0xFFL));
                yC = ((yS << 2) + (scanOrder2ScanIdx[n][1] & 0xFFL));

                if (sigCoeffFlag[(int) (xC)][(int) (yC)]) {
                    int baseLevel = 1 + (coeffAbsLevelGreater1Flag[n] ? 1 : 0) + (coeffAbsLevelGreater2Flag[n] ? 1 : 0);

                    if (baseLevel == ((numSigCoeff < 8) ? ((n == lastGreater1ScanPos) ? 3 : 2) : 1)) {
                        coeffAbsLevelRemaining[n] = streamCabac.readCoeffAbsLevelRemaining(cLastRiceParam);

                        // 9.3.3.11
                        cLastAbsLevel = baseLevel + coeffAbsLevelRemaining[n];
                        cLastRiceParam += (cLastAbsLevel > (3 * (1 << cLastRiceParam)) ? 1 : 0);

                        if (!sps.spsRangeExt.persistentRiceAdaptationEnabledFlag) {
                            cLastRiceParam = Math.min(cLastRiceParam, 4);
                        }

                        if (sps.spsRangeExt.persistentRiceAdaptationEnabledFlag && firstInvoke) {
                            if (coeffAbsLevelRemaining[n] >= (3 << ((streamCabac.statCoeff[sbType] & 0xFF) / 4))) {
                                streamCabac.statCoeff[sbType]++;
                            } else if (2 * coeffAbsLevelRemaining[n]
                                            < (1 << ((streamCabac.statCoeff[sbType] & 0xFF) / 4))
                                    && (streamCabac.statCoeff[sbType] & 0xFF) > 0) {
                                streamCabac.statCoeff[sbType]--;
                            }

                            firstInvoke = false;
                        }
                    }

                    transCoeffLevel = (coeffAbsLevelRemaining[n] + baseLevel) * (coeffSignFlag[n] ? -1 : 1);

                    if (header.pps.signDataHidingEnabledFlag && signHidden) {
                        sumAbsLevel += (coeffAbsLevelRemaining[n] + baseLevel);
                        if ((n == firstSigScanPos) && ((sumAbsLevel % 2) == 1)) {
                            transCoeffLevel = -1 * transCoeffLevel;
                        }
                    }

                    residualBuffers.setCoefficient((int) xC, (int) yC, transCoeffLevel);

                    numSigCoeff++;
                }
            }
        }
    }
}
