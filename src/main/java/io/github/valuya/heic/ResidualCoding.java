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

    // 9.3.4.2.5: sigCtx of a coefficient by scanIdx and scan position in its sub-block, for each prevCsbf of the
    // sub-block (0 to 3), in a 4x4 transform block (4), and the same at every position (5)
    private static final int[][][] SIG_CTX_BY_SCAN_POS = new int[3][6][SUB_BLOCK_SIZE];
    private static final int SIG_CTX_4X4 = 4;
    private static final int SAME_SIG_CTX = 5;
    // Table 9-50: ctxIdxMap of sig_coeff_flag in a 4x4 transform block, by xC + 4 * yC; (3, 3) is never coded
    private static final int[] CTX_IDX_MAP = {0, 1, 4, 5, 2, 3, 4, 5, 6, 6, 8, 8, 7, 7, 8, 8};

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
        for (int scanIdx = 0; scanIdx < 3; scanIdx++) {
            int[][] sigCtx = SIG_CTX_BY_SCAN_POS[scanIdx];
            for (int n = 0; n < SUB_BLOCK_SIZE; n++) {
                int position = POSITIONS[scanIdx][n];
                int xP = position & 3;
                int yP = position >> 2;
                sigCtx[0][n] = xP + yP == 0 ? 2 : xP + yP < 3 ? 1 : 0;
                sigCtx[1][n] = yP == 0 ? 2 : yP == 1 ? 1 : 0;
                sigCtx[2][n] = xP == 0 ? 2 : xP == 1 ? 1 : 0;
                sigCtx[3][n] = 2;
                sigCtx[SIG_CTX_4X4][n] = CTX_IDX_MAP[position];
            }
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
        // 9.3.4.2.6: greater1Ctx after the last coeff_abs_level_greater1_flag decoded, the one the next one would
        // have in the same sub-block
        int nextGreater1Ctx = 0;

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

            // sig_coeff_flag, a bin per scan position, from the one before the last significant coefficient down to 1,
            // then the DC position, whose flag the sub-block may infer
            int sigCoeffFlags = i == lastSubBlock ? 1 << lastScanPos : 0;
            if (codedSubBlockFlag) {
                // 9.3.4.2.5: ctxInc by position, from the sub-block
                int contextBase = cIdx == 0 ? 0 : 27;
                int[] sigCtxByScanPos;
                if (transformSkipContext) {
                    contextBase += cIdx == 0 ? 42 : 16;
                    sigCtxByScanPos = SIG_CTX_BY_SCAN_POS[scanIdx][SAME_SIG_CTX];
                } else if (log2TrafoSize == 2) {
                    sigCtxByScanPos = SIG_CTX_BY_SCAN_POS[scanIdx][SIG_CTX_4X4];
                } else {
                    sigCtxByScanPos = SIG_CTX_BY_SCAN_POS[scanIdx][(codedRight ? 1 : 0) + (codedBelow ? 2 : 0)];
                    if (cIdx == 0) {
                        contextBase += (xS + yS > 0 ? 3 : 0) + (log2TrafoSize == 3 ? (scanIdx == 0 ? 9 : 15) : 21);
                    } else {
                        contextBase += log2TrafoSize == 3 ? 9 : 12;
                    }
                }
                int firstScanPos = i == lastSubBlock ? lastScanPos - 1 : SUB_BLOCK_SIZE - 1;
                // the flag goes into its bit rather than through a branch, which the processor would often mispredict
                for (int n = firstScanPos; n > 0; n--) {
                    sigCoeffFlags |= cabac.readSigCoeffFlag(contextBase + sigCtxByScanPos[n]) << n;
                }
                if (firstScanPos >= 0) {
                    if (inferSbDcSigCoeffFlag && sigCoeffFlags == 0) {
                        sigCoeffFlags = 1;
                    } else {
                        // the DC coefficient of a block bigger than 4x4 has sigCtx 0
                        boolean dc = i == 0 && log2TrafoSize > 2 && !transformSkipContext;
                        sigCoeffFlags |=
                                cabac.readSigCoeffFlag(dc ? (cIdx == 0 ? 0 : 27) : contextBase + sigCtxByScanPos[0]);
                    }
                }
            }
            int lastSigScanPos = 31 - Integer.numberOfLeadingZeros(sigCoeffFlags);
            int firstSigScanPos = Integer.numberOfTrailingZeros(sigCoeffFlags);

            // coeff_abs_level_greater1_flag, for the first eight significant coefficients, in bits like sig_coeff_flag
            int greater1Flags = 0;
            int greater1CtxSet = 0;
            if (sigCoeffFlags != 0) {
                // 9.3.4.2.6: ctxSet from the sub-block and the greater1Ctx the previous sub-block ended with
                greater1CtxSet = (i == 0 || cIdx > 0 ? 0 : 2) + (i != lastSubBlock && nextGreater1Ctx == 0 ? 1 : 0);
                int context = greater1CtxSet * 4 + (cIdx > 0 ? 16 : 0);
                int greater1Ctx = 1;
                int remaining = sigCoeffFlags;
                for (int count = Math.min(Integer.bitCount(sigCoeffFlags), MAX_GREATER1_FLAGS); count > 0; count--) {
                    int n = 31 - Integer.numberOfLeadingZeros(remaining);
                    remaining ^= 1 << n;
                    int greater1Flag = cabac.readCoeffAbsLevelGreater1Flag(context + Math.min(3, greater1Ctx));
                    greater1Flags |= greater1Flag << n;
                    // greater1Ctx grows while the flags are 0, and stays 0 after the first 1
                    greater1Ctx = (greater1Ctx + (greater1Ctx > 0 ? 1 : 0)) & (greater1Flag - 1);
                }
                nextGreater1Ctx = greater1Ctx;
            }
            // the first coefficient greater than 1, in scan order, -1 if none
            int lastGreater1ScanPos = 31 - Integer.numberOfLeadingZeros(greater1Flags);
            boolean signHidden = signHidingAllowed && lastSigScanPos - firstSigScanPos > 3;

            // coeff_abs_level_greater2_flag, for the first coefficient greater than 1
            boolean greater2Flag = greater1Flags != 0 && cabac.readCoeffAbsLevelGreater2Flag(cIdx, greater1CtxSet);

            // coeff_sign_flag, but for the hidden one, the last in scan order: a run of bypass bins, the first one the
            // most significant bit
            int signCount = Integer.bitCount(sigCoeffFlags) - (signHidden ? 1 : 0);
            int signFlags = cabac.readCoeffSignFlags(signCount);
            // the bit of the next coefficient's sign, -1 for the hidden one
            int signShift = signCount;

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
                sumAbsLevel += absLevel;
                // the sign, or the parity of the sum of the levels for the hidden one, the last, applied without a
                // branch: which it is would be a coin toss for the processor
                signShift--;
                int hidden = signShift >> 31;
                int sign = ((signFlags >>> (signShift & 31)) & 1 & ~hidden) | (sumAbsLevel & 1 & hidden);
                int transCoeffLevel = (absLevel ^ -sign) + sign;
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
