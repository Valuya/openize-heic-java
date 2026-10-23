/*
 * Openize.HEIC
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of Openize.HEIC.
 *
 * Openize.HEIC is available under Openize license, which is
 * available along with Openize.HEIC sources.
 */

package io.github.valuya.heic;

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

    // by scanIdx and scan position in a sub-block: the position xP + 4 * yP
    private static final int[][] POSITIONS = new int[3][SUB_BLOCK_SIZE];
    // by log2 of the sub-blocks per side, scanIdx and sub-block xS + yS << (log2 per side): the scan index, and the
    // other way round for positions
    private static final int[][][] SUB_BLOCK_INDICES = new int[4][3][];
    private static final int[][] POSITION_INDICES = new int[3][SUB_BLOCK_SIZE];

    // 9.3.4.2.5: sigCtx of a coefficient by xP + 4 * yP, for each prevCsbf of its sub-block
    private static final int[][] SIG_CTX_BY_PREV_CSBF = new int[4][SUB_BLOCK_SIZE];
    // Table 9-50: ctxIdxMap of sig_coeff_flag in a 4x4 transform block, by xC + 4 * yC; (3, 3) is never coded
    private static final int[] CTX_IDX_MAP = {0, 1, 4, 5, 2, 3, 4, 5, 6, 6, 8, 8, 7, 7, 8, 8};
    // the same sigCtx at every position
    private static final int[] SAME_SIG_CTX = new int[SUB_BLOCK_SIZE];

    static {
        byte[][][][] scanOrder = Scans.getScanOrder();
        for (int scanIdx = 0; scanIdx < 3; scanIdx++) {
            for (int n = 0; n < SUB_BLOCK_SIZE; n++) {
                byte[] position = scanOrder[2][scanIdx][n];
                POSITIONS[scanIdx][n] = position[0] + 4 * position[1];
                POSITION_INDICES[scanIdx][position[0] + 4 * position[1]] = n;
            }
            for (int log2SubBlocks = 0; log2SubBlocks < 4; log2SubBlocks++) {
                byte[][] subBlockScan = scanOrder[log2SubBlocks][scanIdx];
                int[] indices = new int[subBlockScan.length];
                for (int i = 0; i < subBlockScan.length; i++) {
                    indices[subBlockScan[i][0] + (subBlockScan[i][1] << log2SubBlocks)] = i;
                }
                SUB_BLOCK_INDICES[log2SubBlocks][scanIdx] = indices;
            }
        }
        for (int position = 0; position < SUB_BLOCK_SIZE; position++) {
            int xP = position & 3;
            int yP = position >> 2;
            SIG_CTX_BY_PREV_CSBF[0][position] = xP + yP == 0 ? 2 : xP + yP < 3 ? 1 : 0;
            SIG_CTX_BY_PREV_CSBF[1][position] = yP == 0 ? 2 : yP == 1 ? 1 : 0;
            SIG_CTX_BY_PREV_CSBF[2][position] = xP == 0 ? 2 : xP == 1 ? 1 : 0;
            SIG_CTX_BY_PREV_CSBF[3][position] = 2;
        }
    }

    private ResidualCoding() {}

    /**
     * Parses the residual of the {@code 1 << log2TrafoSize} block of component {@code cIdx} whose top-left luma
     * sample is {@code (x0, y0)}. The flags of a sub-block are bits of an int, bit n for scan position n.
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

        int predModeIntra = cIdx == 0 ? picture.intraPredModeY.get(x0, y0) : picture.intraPredModeC.get(x0, y0);
        int scanIdx = scanIdx(log2TrafoSize, cIdx, sps.getChromaArrayType(), predModeIntra);

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
        int log2SubBlocks = log2TrafoSize - 2;
        byte[][] subBlockScan = Scans.getScanOrder()[log2SubBlocks][scanIdx];
        int[] positions = POSITIONS[scanIdx];
        int lastSubBlock = SUB_BLOCK_INDICES[log2SubBlocks][scanIdx][
                (lastSignificantCoeffX >> 2) + ((lastSignificantCoeffY >> 2) << log2SubBlocks)];
        int lastScanPos = POSITION_INDICES[scanIdx][(lastSignificantCoeffX & 3) + 4 * (lastSignificantCoeffY & 3)];

        boolean signHidingAllowed = pps.signDataHidingEnabledFlag
                && !cuTransquantBypassFlag
                && !(sps.spsRangeExt.implicitRdpcmEnabledFlag
                        && transformSkipFlag
                        && isHorizontalOrVertical(predModeIntra));
        boolean transformSkipContext = sps.spsRangeExt.transformSkipContextEnabledFlag && transformSkipOrBypass;
        boolean persistentRiceAdaptation = sps.spsRangeExt.persistentRiceAdaptationEnabledFlag;
        // 9.3.3.11: sbType of the StatCoeff of the block
        int sbType = (cIdx == 0 ? 2 : 0) + (transformSkipOrBypass ? 1 : 0);

        // coded_sub_block_flag, bit xS + 8 * yS
        long codedSubBlockFlags = 0;
        int lastSubBlockOnSide = (1 << log2SubBlocks) - 1;
        // 9.3.4.2.6: the state of coeff_abs_level_greater1_flag from one invocation to the next
        int greater1CtxSet = 0;
        int greater1Ctx = 0;
        boolean greater1Flag = false;
        int lastSubBlockGreater1Ctx = 0;

        for (int i = lastSubBlock; i >= 0; i--) {
            int xS = subBlockScan[i][0];
            int yS = subBlockScan[i][1];
            int subBlockBit = xS + 8 * yS;
            // 9.3.4.2.4: whether the sub-blocks right and below have coefficients
            boolean codedRight = xS < lastSubBlockOnSide && (codedSubBlockFlags & (1L << (subBlockBit + 1))) != 0;
            boolean codedBelow = yS < lastSubBlockOnSide && (codedSubBlockFlags & (1L << (subBlockBit + 8))) != 0;

            // coded_sub_block_flag: inferred for the first and the last sub-blocks
            boolean inferSbDcSigCoeffFlag = false;
            boolean codedSubBlockFlag = true;
            if (i < lastSubBlock && i > 0) {
                codedSubBlockFlag = cabac.readCodedSubBlockFlag(codedRight || codedBelow, cIdx);
                inferSbDcSigCoeffFlag = true;
            }
            if (codedSubBlockFlag) {
                codedSubBlockFlags |= 1L << subBlockBit;
            }

            // sig_coeff_flag
            int sigCoeffFlags = i == lastSubBlock ? 1 << lastScanPos : 0;
            if (codedSubBlockFlag) {
                // 9.3.4.2.5: ctxInc by position, from the sub-block
                int contextBase = cIdx == 0 ? 0 : 27;
                int[] sigCtxByPosition;
                if (transformSkipContext) {
                    contextBase += cIdx == 0 ? 42 : 16;
                    sigCtxByPosition = SAME_SIG_CTX;
                } else if (log2TrafoSize == 2) {
                    sigCtxByPosition = CTX_IDX_MAP;
                } else {
                    sigCtxByPosition = SIG_CTX_BY_PREV_CSBF[(codedRight ? 1 : 0) + (codedBelow ? 2 : 0)];
                    if (cIdx == 0) {
                        contextBase += (xS + yS > 0 ? 3 : 0) + (log2TrafoSize == 3 ? (scanIdx == 0 ? 9 : 15) : 21);
                    } else {
                        contextBase += log2TrafoSize == 3 ? 9 : 12;
                    }
                }
                for (int n = i == lastSubBlock ? lastScanPos - 1 : SUB_BLOCK_SIZE - 1; n >= 0; n--) {
                    if (n > 0 || !inferSbDcSigCoeffFlag) {
                        // the DC coefficient of a block bigger than 4x4 has sigCtx 0
                        boolean dc = i == 0 && n == 0 && log2TrafoSize > 2 && !transformSkipContext;
                        int ctxInc = dc ? (cIdx == 0 ? 0 : 27) : contextBase + sigCtxByPosition[positions[n]];
                        if (cabac.readSigCoeffFlag(ctxInc)) {
                            sigCoeffFlags |= 1 << n;
                            inferSbDcSigCoeffFlag = false;
                        }
                    } else {
                        sigCoeffFlags |= 1;
                    }
                }
            }

            // coeff_abs_level_greater1_flag, for the first eight significant coefficients
            int greater1Flags = 0;
            int firstSigScanPos = SUB_BLOCK_SIZE;
            int lastSigScanPos = -1;
            int greater1FlagCount = 0;
            int lastGreater1ScanPos = -1;
            boolean firstCoeffInSubBlock = true;
            for (int remaining = sigCoeffFlags; remaining != 0; ) {
                int n = 31 - Integer.numberOfLeadingZeros(remaining);
                remaining &= ~(1 << n);
                if (greater1FlagCount < MAX_GREATER1_FLAGS) {
                    // 9.3.4.2.6: ctxInc from the previous invocations in the transform block
                    if (firstCoeffInSubBlock) {
                        greater1CtxSet = i == 0 || cIdx > 0 ? 0 : 2;
                        int lastGreater1Ctx;
                        if (i == lastSubBlock) {
                            lastGreater1Ctx = 1;
                        } else {
                            lastGreater1Ctx = lastSubBlockGreater1Ctx;
                            if (lastGreater1Ctx > 0) {
                                lastGreater1Ctx = greater1Flag ? 0 : lastGreater1Ctx + 1;
                            }
                        }
                        if (lastGreater1Ctx == 0) {
                            greater1CtxSet++;
                        }
                        greater1Ctx = 1;
                    } else if (greater1Ctx > 0) {
                        greater1Ctx = greater1Flag ? 0 : greater1Ctx + 1;
                    }
                    int ctxInc = greater1CtxSet * 4 + Math.min(3, greater1Ctx) + (cIdx > 0 ? 16 : 0);
                    greater1Flag = cabac.readCoeffAbsLevelGreater1Flag(ctxInc);
                    greater1FlagCount++;
                    if (greater1Flag) {
                        greater1Flags |= 1 << n;
                        if (lastGreater1ScanPos == -1) {
                            lastGreater1ScanPos = n;
                        }
                    }
                }
                if (lastSigScanPos == -1) {
                    lastSigScanPos = n;
                }
                firstSigScanPos = n;
                firstCoeffInSubBlock = false;
            }
            lastSubBlockGreater1Ctx = greater1Ctx;
            boolean signHidden = signHidingAllowed && lastSigScanPos - firstSigScanPos > 3;

            // coeff_abs_level_greater2_flag, for the first coefficient greater than 1
            boolean greater2Flag =
                    lastGreater1ScanPos != -1 && cabac.readCoeffAbsLevelGreater2Flag(cIdx, greater1CtxSet);

            // coeff_sign_flag, but for the hidden one, the last in scan order: a run of bypass bins, the first one the
            // most significant bit
            int signCount = Integer.bitCount(sigCoeffFlags) - (signHidden ? 1 : 0);
            int signFlags = cabac.readCoeffSignFlags(signCount);
            int signBit = 1 << signCount;

            // coeff_abs_level_remaining, and the coefficients
            int initRiceValue = persistentRiceAdaptation ? cabac.statCoeff[sbType] / 4 : 0;
            int cRiceParam = initRiceValue;
            boolean firstRemainingInSubBlock = true;
            int sigCoeffCount = 0;
            int sumAbsLevel = 0;
            for (int remaining = sigCoeffFlags; remaining != 0; ) {
                int n = 31 - Integer.numberOfLeadingZeros(remaining);
                remaining &= ~(1 << n);
                int baseLevel = 1 + ((greater1Flags >> n) & 1) + (greater2Flag && n == lastGreater1ScanPos ? 1 : 0);
                int remainingThreshold = sigCoeffCount < MAX_GREATER1_FLAGS ? (n == lastGreater1ScanPos ? 3 : 2) : 1;
                int coeffAbsLevelRemaining = 0;
                if (baseLevel == remainingThreshold) {
                    coeffAbsLevelRemaining = cabac.readCoeffAbsLevelRemaining(cRiceParam);

                    // 9.3.3.11: the Rice parameter of the next coeff_abs_level_remaining, and StatCoeff
                    if (baseLevel + coeffAbsLevelRemaining > 3 * (1 << cRiceParam)) {
                        cRiceParam++;
                    }
                    if (!persistentRiceAdaptation) {
                        cRiceParam = Math.min(cRiceParam, 4);
                    } else if (firstRemainingInSubBlock) {
                        updateStatCoeff(cabac.statCoeff, sbType, coeffAbsLevelRemaining);
                    }
                    firstRemainingInSubBlock = false;
                }

                int absLevel = coeffAbsLevelRemaining + baseLevel;
                boolean signHiddenHere = signHidden && n == firstSigScanPos;
                if (!signHiddenHere) {
                    signBit >>= 1;
                }
                int transCoeffLevel = !signHiddenHere && (signFlags & signBit) != 0 ? -absLevel : absLevel;
                if (signHidden) {
                    sumAbsLevel += absLevel;
                    if (signHiddenHere && sumAbsLevel % 2 == 1) {
                        transCoeffLevel = -transCoeffLevel;
                    }
                }
                int position = positions[n];
                residualBuffers.setCoefficient(
                        (xS << 2) + (position & 3), (yS << 2) + (position >> 2), transCoeffLevel);
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

    private static boolean isHorizontalOrVertical(int predModeIntra) {
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
