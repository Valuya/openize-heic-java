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

/**
 * Parses the residual coding of a transform block (7.3.8.11) into the coefficients TransCoeffLevel of the
 * {@link ResidualBuffers} (7.4.9.11).
 */
final class ResidualCoding {
    private static final int SUB_BLOCK_SIZE = 16;
    private static final int DIAGONAL_SCAN = 0;
    private static final int HORIZONTAL_SCAN = 1;
    private static final int VERTICAL_SCAN = 2;
    // at most eight coeff_abs_level_greater1_flag per sub-block
    private static final int MAX_GREATER1_FLAGS = 8;

    private ResidualCoding() {}

    /**
     * Parses the residual of the {@code 1 << log2TrafoSize} block of component {@code cIdx} whose top-left luma
     * sample is {@code (x0, y0)}.
     */
    static void parse(
            Cabac cabac,
            ResidualBuffers residualBuffers,
            HeicPicture picture,
            int x0,
            int y0,
            int log2TrafoSize,
            int cIdx,
            boolean cuTransquantBypassFlag) {
        SeqParameterSetRbsp sps = picture.sps;
        PicParameterSetRbsp pps = picture.pps;
        residualBuffers.startBlock(1 << log2TrafoSize);

        boolean transformSkipFlag = pps.transformSkipEnabledFlag
                && !cuTransquantBypassFlag
                && log2TrafoSize <= pps.getLog2MaxTransformSkipSize()
                && cabac.readTransformSkipFlag(cIdx);
        residualBuffers.transformSkipFlag = transformSkipFlag;
        boolean transformSkipOrBypass = transformSkipFlag || cuTransquantBypassFlag;

        IntraPredMode predModeIntra =
                cIdx == 0 ? picture.intraPredModeY.get(x0, y0) : picture.intraPredModeC.get(x0, y0);
        int scanIdx = scanIdx(log2TrafoSize, cIdx, sps.getChromaArrayType(), predModeIntra.ordinal());

        // both prefixes come before both suffixes
        int lastSigCoeffXPrefix =
                cabac.readLastSigCoeffPrefix(ContextTable.LAST_SIG_COEFF_X_PREFIX, log2TrafoSize, cIdx);
        int lastSigCoeffYPrefix =
                cabac.readLastSigCoeffPrefix(ContextTable.LAST_SIG_COEFF_Y_PREFIX, log2TrafoSize, cIdx);
        int lastSignificantCoeffX = readLastSignificantCoeff(cabac, lastSigCoeffXPrefix);
        int lastSignificantCoeffY = readLastSignificantCoeff(cabac, lastSigCoeffYPrefix);
        if (scanIdx == VERTICAL_SCAN) {
            int swapped = lastSignificantCoeffX;
            lastSignificantCoeffX = lastSignificantCoeffY;
            lastSignificantCoeffY = swapped;
        }

        // the sub-block and the scan position of the last significant coefficient
        byte[][] subBlockScan = Scans.getScanOrder()[log2TrafoSize - 2][scanIdx];
        byte[][] positionScan = Scans.getScanOrder()[2][scanIdx];
        int lastSubBlock = (1 << (log2TrafoSize - 2)) * (1 << (log2TrafoSize - 2)) - 1;
        int lastScanPos = SUB_BLOCK_SIZE;
        int xC;
        int yC;
        do {
            if (lastScanPos == 0) {
                lastScanPos = SUB_BLOCK_SIZE;
                lastSubBlock--;
            }
            lastScanPos--;
            xC = (subBlockScan[lastSubBlock][0] << 2) + positionScan[lastScanPos][0];
            yC = (subBlockScan[lastSubBlock][1] << 2) + positionScan[lastScanPos][1];
        } while (xC != lastSignificantCoeffX || yC != lastSignificantCoeffY);

        boolean signHidingAllowed = pps.signDataHidingEnabledFlag
                && !cuTransquantBypassFlag
                && !(sps.spsRangeExt.implicitRdpcmEnabledFlag
                        && transformSkipFlag
                        && isHorizontalOrVertical(predModeIntra));
        boolean transformSkipContext = sps.spsRangeExt.transformSkipContextEnabledFlag && transformSkipOrBypass;
        boolean persistentRiceAdaptation = sps.spsRangeExt.persistentRiceAdaptationEnabledFlag;
        // 9.3.3.11: sbType of the StatCoeff of the block
        int sbType = (cIdx == 0 ? 2 : 0) + (transformSkipOrBypass ? 1 : 0);

        boolean[][] codedSubBlockFlag = residualBuffers.clearedCodedSubBlockFlags(log2TrafoSize);
        boolean[] sigCoeffFlag = residualBuffers.sigCoeffFlags;
        boolean[] coeffAbsLevelGreater1Flag = residualBuffers.coeffAbsLevelGreater1Flags;
        boolean[] coeffAbsLevelGreater2Flag = residualBuffers.coeffAbsLevelGreater2Flags;
        boolean[] coeffSignFlag = residualBuffers.coeffSignFlags;
        int[] coeffAbsLevelRemaining = residualBuffers.coeffAbsLevelRemainings;
        Cabac.Greater1Context greater1Context = new Cabac.Greater1Context();
        int lastSubBlockGreater1Ctx = 0;

        for (int i = lastSubBlock; i >= 0; i--) {
            int xS = subBlockScan[i][0];
            int yS = subBlockScan[i][1];
            Arrays.fill(sigCoeffFlag, false);

            // coded_sub_block_flag: inferred for the first and the last sub-blocks
            boolean inferSbDcSigCoeffFlag = false;
            if (i < lastSubBlock && i > 0) {
                codedSubBlockFlag[xS][yS] = cabac.readCodedSubBlockFlag(xS, yS, codedSubBlockFlag, cIdx, log2TrafoSize);
                inferSbDcSigCoeffFlag = true;
            } else {
                codedSubBlockFlag[xS][yS] = true;
            }

            // sig_coeff_flag
            if (i == lastSubBlock) {
                sigCoeffFlag[lastScanPos] = true;
            }
            if (codedSubBlockFlag[xS][yS]) {
                for (int n = i == lastSubBlock ? lastScanPos - 1 : SUB_BLOCK_SIZE - 1; n >= 0; n--) {
                    if (n > 0 || !inferSbDcSigCoeffFlag) {
                        int xCoeff = (xS << 2) + positionScan[n][0];
                        int yCoeff = (yS << 2) + positionScan[n][1];
                        sigCoeffFlag[n] = cabac.readSigCoeffFlag(
                                transformSkipContext, xCoeff, yCoeff, codedSubBlockFlag, cIdx, log2TrafoSize, scanIdx);
                        if (sigCoeffFlag[n]) {
                            inferSbDcSigCoeffFlag = false;
                        }
                    } else {
                        sigCoeffFlag[n] = true;
                    }
                }
            }

            // coeff_abs_level_greater1_flag, for the first eight significant coefficients
            Arrays.fill(coeffAbsLevelGreater1Flag, false);
            Arrays.fill(coeffAbsLevelGreater2Flag, false);
            Arrays.fill(coeffAbsLevelRemaining, 0);
            int firstSigScanPos = SUB_BLOCK_SIZE;
            int lastSigScanPos = -1;
            int greater1FlagCount = 0;
            int lastGreater1ScanPos = -1;
            boolean firstCoeffInSubBlock = true;
            for (int n = SUB_BLOCK_SIZE - 1; n >= 0; n--) {
                if (sigCoeffFlag[n]) {
                    if (greater1FlagCount < MAX_GREATER1_FLAGS) {
                        coeffAbsLevelGreater1Flag[n] = cabac.readCoeffAbsLevelGreater1Flag(
                                cIdx,
                                i,
                                firstCoeffInSubBlock,
                                i == lastSubBlock,
                                lastSubBlockGreater1Ctx,
                                greater1Context);
                        greater1FlagCount++;
                        if (coeffAbsLevelGreater1Flag[n] && lastGreater1ScanPos == -1) {
                            lastGreater1ScanPos = n;
                        }
                    }
                    if (lastSigScanPos == -1) {
                        lastSigScanPos = n;
                    }
                    firstSigScanPos = n;
                    firstCoeffInSubBlock = false;
                }
            }
            lastSubBlockGreater1Ctx = greater1Context.greater1Ctx;
            boolean signHidden = signHidingAllowed && lastSigScanPos - firstSigScanPos > 3;

            // coeff_abs_level_greater2_flag, for the first coefficient greater than 1
            if (lastGreater1ScanPos != -1) {
                coeffAbsLevelGreater2Flag[lastGreater1ScanPos] =
                        cabac.readCoeffAbsLevelGreater2Flag(cIdx, greater1Context.ctxSet);
            }

            // coeff_sign_flag, but for the hidden one
            Arrays.fill(coeffSignFlag, false);
            for (int n = SUB_BLOCK_SIZE - 1; n >= 0; n--) {
                if (sigCoeffFlag[n] && (!signHidden || n != firstSigScanPos)) {
                    coeffSignFlag[n] = cabac.readCoeffSignFlag();
                }
            }

            // coeff_abs_level_remaining, and the coefficients
            int initRiceValue = persistentRiceAdaptation ? cabac.statCoeff[sbType] / 4 : 0;
            int cRiceParam = initRiceValue;
            boolean firstRemainingInSubBlock = true;
            int sigCoeffCount = 0;
            int sumAbsLevel = 0;
            for (int n = SUB_BLOCK_SIZE - 1; n >= 0; n--) {
                if (!sigCoeffFlag[n]) {
                    continue;
                }
                int baseLevel = 1 + (coeffAbsLevelGreater1Flag[n] ? 1 : 0) + (coeffAbsLevelGreater2Flag[n] ? 1 : 0);
                int remainingThreshold = sigCoeffCount < MAX_GREATER1_FLAGS ? (n == lastGreater1ScanPos ? 3 : 2) : 1;
                if (baseLevel == remainingThreshold) {
                    coeffAbsLevelRemaining[n] = cabac.readCoeffAbsLevelRemaining(cRiceParam);

                    // 9.3.3.11: the Rice parameter of the next coeff_abs_level_remaining, and StatCoeff
                    if (baseLevel + coeffAbsLevelRemaining[n] > 3 * (1 << cRiceParam)) {
                        cRiceParam++;
                    }
                    if (!persistentRiceAdaptation) {
                        cRiceParam = Math.min(cRiceParam, 4);
                    } else if (firstRemainingInSubBlock) {
                        updateStatCoeff(cabac.statCoeff, sbType, coeffAbsLevelRemaining[n]);
                    }
                    firstRemainingInSubBlock = false;
                }

                int absLevel = coeffAbsLevelRemaining[n] + baseLevel;
                int transCoeffLevel = coeffSignFlag[n] ? -absLevel : absLevel;
                if (signHidden) {
                    sumAbsLevel += absLevel;
                    if (n == firstSigScanPos && sumAbsLevel % 2 == 1) {
                        transCoeffLevel = -transCoeffLevel;
                    }
                }
                residualBuffers.setCoefficient(
                        (xS << 2) + positionScan[n][0], (yS << 2) + positionScan[n][1], transCoeffLevel);
                sigCoeffCount++;
            }
        }
    }

    // 7.4.9.11: LastSignificantCoeffX from last_sig_coeff_x_prefix and its suffix, or the same for y
    private static int readLastSignificantCoeff(Cabac cabac, int prefix) {
        if (prefix <= 3) {
            return prefix;
        }
        int suffix = cabac.readLastSigCoeffSuffix(prefix);
        return (1 << ((prefix >> 1) - 1)) * (2 + (prefix & 1)) + suffix;
    }

    // 7.4.9.11: scanIdx, vertical or horizontal for the small blocks of near-horizontal and near-vertical modes
    private static int scanIdx(int log2TrafoSize, int cIdx, int chromaArrayType, int predModeIntra) {
        if (log2TrafoSize == 2 || (log2TrafoSize == 3 && (cIdx == 0 || chromaArrayType == 3))) {
            if (predModeIntra >= 6 && predModeIntra <= 14) {
                return VERTICAL_SCAN;
            }
            if (predModeIntra >= 22 && predModeIntra <= 30) {
                return HORIZONTAL_SCAN;
            }
        }
        return DIAGONAL_SCAN;
    }

    private static boolean isHorizontalOrVertical(IntraPredMode predModeIntra) {
        return predModeIntra == IntraPredMode.INTRA_ANGULAR10 || predModeIntra == IntraPredMode.INTRA_ANGULAR26;
    }

    // 9.3.3.11: StatCoeff follows the first coeff_abs_level_remaining of each sub-block
    private static void updateStatCoeff(int[] statCoeff, int sbType, int coeffAbsLevelRemaining) {
        if (coeffAbsLevelRemaining >= (3 << (statCoeff[sbType] / 4))) {
            statCoeff[sbType]++;
        } else if (2 * coeffAbsLevelRemaining < (1 << (statCoeff[sbType] / 4)) && statCoeff[sbType] > 0) {
            statCoeff[sbType]--;
        }
    }
}
