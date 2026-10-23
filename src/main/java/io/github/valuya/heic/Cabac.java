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

import java.util.Arrays;

/**
 * The CABAC parsing process (9.3) of the syntax elements of intra slice segment data: the arithmetic decoding
 * engine, the context variables and the binarization of each syntax element.
 *
 * <p>Each context variable is held as one int, {@code pStateIdx << 1 | valMps}.
 */
final class Cabac {
    // Table 9-52: rangeTabLps[pStateIdx][qRangeIdx], flattened as [pStateIdx << 2 | qRangeIdx]
    private static final int[] RANGE_TAB_LPS = {
        128, 176, 208, 240, 128, 167, 197, 227, 128, 158, 187, 216, 123, 150, 178, 205, 116, 142, 169, 195, 111, 135,
        160, 185, 105, 128, 152, 175, 100, 122, 144, 166, 95, 116, 137, 158, 90, 110, 130, 150, 85, 104, 123, 142, 81,
        99, 117, 135, 77, 94, 111, 128, 73, 89, 105, 122, 69, 85, 100, 116, 66, 80, 95, 110, 62, 76, 90, 104, 59, 72,
        86, 99, 56, 69, 81, 94, 53, 65, 77, 89, 51, 62, 73, 85, 48, 59, 69, 80, 46, 56, 66, 76, 43, 53, 63, 72, 41, 50,
        59, 69, 39, 48, 56, 65, 37, 45, 54, 62, 35, 43, 51, 59, 33, 41, 48, 56, 32, 39, 46, 53, 30, 37, 43, 50, 29, 35,
        41, 48, 27, 33, 39, 45, 26, 31, 37, 43, 24, 30, 35, 41, 23, 28, 33, 39, 22, 27, 32, 37, 21, 26, 30, 35, 20, 24,
        29, 33, 19, 23, 27, 31, 18, 22, 26, 30, 17, 21, 25, 28, 16, 20, 23, 27, 15, 19, 22, 25, 14, 18, 21, 24, 14, 17,
        20, 23, 13, 16, 19, 22, 12, 15, 18, 21, 12, 14, 17, 20, 11, 14, 16, 19, 11, 13, 15, 18, 10, 12, 15, 17, 10, 12,
        14, 16, 9, 11, 13, 15, 9, 11, 12, 14, 8, 10, 12, 14, 8, 9, 11, 13, 7, 9, 11, 12, 7, 9, 10, 12, 7, 8, 10, 11, 6,
        8, 9, 11, 6, 7, 9, 10, 6, 7, 8, 9, 2, 2, 2, 2
    };
    // Table 9-53: the state transitions after a most and a least probable symbol
    private static final int[] TRANS_IDX_MPS = {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
        31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58,
        59, 60, 61, 62, 62, 63
    };
    private static final int[] TRANS_IDX_LPS = {
        0, 0, 1, 2, 2, 4, 4, 5, 6, 7, 8, 9, 9, 11, 11, 12, 13, 13, 15, 15, 16, 16, 18, 18, 19, 19, 21, 21, 22, 22, 23,
        24, 24, 25, 26, 26, 27, 27, 28, 29, 29, 30, 30, 30, 31, 32, 32, 33, 33, 33, 34, 34, 35, 35, 35, 36, 36, 36, 37,
        37, 37, 38, 38, 63
    };
    // Table 9-50: ctxIdxMap[(yC << 2) + xC] of sig_coeff_flag in a 4x4 transform block
    private static final int[] CTX_IDX_MAP = {0, 1, 4, 5, 2, 3, 4, 5, 6, 6, 8, 8, 7, 7, 8};
    // A coeff_abs_level_remaining prefix longer than this cannot code a coefficient within the transform range
    private static final int MAX_COEFF_ABS_LEVEL_REMAINING_PREFIX = 32;
    private static final int STAT_COEFF_COUNT = 4;

    private final BitStreamWithNalSupport stream;

    private final int[] contextStates = new int[ContextTable.CONTEXT_COUNT];
    // 9.3.2.4: StatCoeff, the persistent Rice adaptation statistics
    final int[] statCoeff = new int[STAT_COEFF_COUNT];

    // 9.3.2.4: the context variables stored for wavefront parallel processing and for dependent slice segments
    private final ContextStorage wppStorage = new ContextStorage();
    private final ContextStorage dependentSliceStorage = new ContextStorage();

    private int ivlCurrRange;
    private int ivlOffset;

    Cabac(BitStreamWithNalSupport stream) {
        this.stream = stream;
    }

    // 9.3.2.5 Initialization process for the arithmetic decoding engine
    void initializeArithmeticDecoder() {
        ivlCurrRange = 510;
        ivlOffset = stream.read(9);
    }

    // 9.3.2.2 Initialization process for context variables, and the Rice statistics
    void initializeContexts(int sliceQpY) {
        int qp = MathExtra.clip3(0, 51, sliceQpY);
        for (ContextTable table : ContextTable.values()) {
            for (int contextIndex = 0; contextIndex < table.contextCount(); contextIndex++) {
                int initValue = table.initValue(contextIndex);
                int m = (initValue >> 4) * 5 - 45;
                int n = ((initValue & 15) << 3) - 16;
                int preCtxState = MathExtra.clip3(1, 126, ((m * qp) >> 4) + n);
                int valMps = preCtxState > 63 ? 1 : 0;
                int pStateIdx = valMps == 1 ? preCtxState - 64 : 63 - preCtxState;
                contextStates[table.offset() + contextIndex] = (pStateIdx << 1) | valMps;
            }
        }
        Arrays.fill(statCoeff, 0);
    }

    void storeWppContexts() {
        wppStorage.store(this);
    }

    void synchronizeWppContexts() {
        wppStorage.restore(this);
    }

    void storeDependentSliceContexts() {
        dependentSliceStorage.store(this);
    }

    void synchronizeDependentSliceContexts() {
        dependentSliceStorage.restore(this);
    }

    boolean readEndOfSliceSegmentFlag() {
        return decodeTerminate() == 1;
    }

    boolean readEndOfSubsetOneBit() {
        return decodeTerminate() == 1;
    }

    boolean readSaoMergeFlag() {
        return decodeBin(ContextTable.SAO_MERGE_FLAG.offset()) == 1;
    }

    // TR, cMax 2: the first bin with a context, the second bypass
    int readSaoTypeIdx() {
        if (decodeBin(ContextTable.SAO_TYPE_IDX.offset()) == 0) {
            return 0;
        }
        return decodeBypass() == 0 ? 1 : 2;
    }

    // TR, cMax (1 << (Min(bitDepth, 10) - 5)) - 1, bypass
    int readSaoOffsetAbs(int bitDepth) {
        int cMax = (1 << (Math.min(bitDepth, 10) - 5)) - 1;
        return decodeTruncatedUnaryBypass(cMax);
    }

    int readSaoOffsetSign() {
        return decodeBypass();
    }

    int readSaoBandPosition() {
        return decodeFixedLengthBypass(5);
    }

    int readSaoEoClass() {
        return decodeFixedLengthBypass(2);
    }

    // 9.3.4.2.2: ctxInc from the coding quadtree depth of the available left and above neighbours
    boolean readSplitCuFlag(int x0, int y0, HeicPicture picture, int cqtDepth) {
        boolean conditionLeft =
                picture.checkZScanAvailability(x0, y0, x0 - 1, y0) && picture.ctDepth.get(x0 - 1, y0) > cqtDepth;
        boolean conditionAbove =
                picture.checkZScanAvailability(x0, y0, x0, y0 - 1) && picture.ctDepth.get(x0, y0 - 1) > cqtDepth;
        int ctxInc = (conditionLeft ? 1 : 0) + (conditionAbove ? 1 : 0);
        return decodeBin(ContextTable.SPLIT_CU_FLAG.offset() + ctxInc) == 1;
    }

    boolean readCuTransquantBypassFlag() {
        return decodeBin(ContextTable.CU_TRANSQUANT_BYPASS_FLAG.offset()) == 1;
    }

    boolean readPaletteModeFlag() {
        return decodeBin(ContextTable.PALETTE_MODE_FLAG.offset()) == 1;
    }

    // 9.3.3.7: an intra coding unit has one bin, 1 for PART_2Nx2N, 0 for PART_NxN
    boolean readPartModeIsNxN() {
        return decodeBin(ContextTable.PART_MODE.offset()) == 0;
    }

    boolean readPcmFlag() {
        return decodeTerminate() == 1;
    }

    boolean readPrevIntraLumaPredFlag() {
        return decodeBin(ContextTable.PREV_INTRA_LUMA_PRED_FLAG.offset()) == 1;
    }

    // TR, cMax 2, bypass
    int readMpmIdx() {
        return decodeTruncatedUnaryBypass(2);
    }

    int readRemIntraLumaPredMode() {
        return decodeFixedLengthBypass(5);
    }

    // 9.3.3.8: 0 codes 4, 1 followed by two bypass bins codes 0..3
    int readIntraChromaPredMode() {
        if (decodeBin(ContextTable.INTRA_CHROMA_PRED_MODE.offset()) == 0) {
            return 4;
        }
        return decodeFixedLengthBypass(2);
    }

    boolean readSplitTransformFlag(int log2TrafoSize) {
        return decodeBin(ContextTable.SPLIT_TRANSFORM_FLAG.offset() + 5 - log2TrafoSize) == 1;
    }

    boolean readCbfLuma(int trafoDepth) {
        return decodeBin(ContextTable.CBF_LUMA.offset() + (trafoDepth == 0 ? 1 : 0)) == 1;
    }

    boolean readCbfChroma(int trafoDepth) {
        return decodeBin(ContextTable.CBF_CHROMA.offset() + trafoDepth) == 1;
    }

    boolean readTuResidualActFlag() {
        return decodeBin(ContextTable.TU_RESIDUAL_ACT_FLAG.offset()) == 1;
    }

    // 9.3.3.10: a TR prefix with cMax 5, the first bin with its own context, then an EG0 suffix
    int readCuQpDeltaAbs() {
        int context = ContextTable.CU_QP_DELTA_ABS.offset();
        if (decodeBin(context) == 0) {
            return 0;
        }
        int prefix = 1;
        while (prefix < 5 && decodeBin(context + 1) == 1) {
            prefix++;
        }
        return prefix < 5 ? prefix : prefix + decodeExpGolombBypass(0);
    }

    boolean readCuQpDeltaSignFlag() {
        return decodeBypass() == 1;
    }

    boolean readCuChromaQpOffsetFlag() {
        return decodeBin(ContextTable.CU_CHROMA_QP_OFFSET_FLAG.offset()) == 1;
    }

    // TR, cMax chroma_qp_offset_list_len_minus1, every bin with the same context
    int readCuChromaQpOffsetIdx(int cMax) {
        int context = ContextTable.CU_CHROMA_QP_OFFSET_IDX.offset();
        int value = 0;
        while (value < cMax && decodeBin(context) == 1) {
            value++;
        }
        return value;
    }

    // TR, cMax 4, bin n of component c with context 4 * c + n
    int readLog2ResScaleAbsPlus1(int c) {
        int context = ContextTable.LOG2_RES_SCALE_ABS_PLUS1.offset() + 4 * c;
        int value = 0;
        while (value < 4 && decodeBin(context + value) == 1) {
            value++;
        }
        return value;
    }

    boolean readResScaleSignFlag(int c) {
        return decodeBin(ContextTable.RES_SCALE_SIGN_FLAG.offset() + c) == 1;
    }

    boolean readTransformSkipFlag(int cIdx) {
        return decodeBin(ContextTable.TRANSFORM_SKIP_FLAG.offset() + (cIdx == 0 ? 0 : 1)) == 1;
    }

    // TR, cMax (log2TrafoSize << 1) - 1, ctxInc from clause 9.3.4.2.3
    int readLastSigCoeffPrefix(ContextTable table, int log2TrafoSize, int cIdx) {
        int cMax = (log2TrafoSize << 1) - 1;
        int ctxOffset = cIdx == 0 ? 3 * (log2TrafoSize - 2) + ((log2TrafoSize - 1) >> 2) : 15;
        int ctxShift = cIdx == 0 ? (log2TrafoSize + 1) >> 2 : log2TrafoSize - 2;
        int context = table.offset() + ctxOffset;
        for (int binIdx = 0; binIdx < cMax; binIdx++) {
            if (decodeBin(context + (binIdx >> ctxShift)) == 0) {
                return binIdx;
            }
        }
        return cMax;
    }

    // FL, (prefix >> 1) - 1 bypass bins
    int readLastSigCoeffSuffix(int lastSigCoeffPrefix) {
        return decodeFixedLengthBypass((lastSigCoeffPrefix >> 1) - 1);
    }

    // 9.3.4.2.4: ctxInc from the coded_sub_block_flag of the sub-blocks right and below
    boolean readCodedSubBlockFlag(int xS, int yS, boolean[][] codedSubBlockFlag, int cIdx, int log2TrafoSize) {
        int lastSubBlock = (1 << (log2TrafoSize - 2)) - 1;
        boolean codedNeighbour = (xS < lastSubBlock && codedSubBlockFlag[xS + 1][yS])
                || (yS < lastSubBlock && codedSubBlockFlag[xS][yS + 1]);
        int ctxInc = (codedNeighbour ? 1 : 0) + (cIdx == 0 ? 0 : 2);
        return decodeBin(ContextTable.CODED_SUB_BLOCK_FLAG.offset() + ctxInc) == 1;
    }

    /**
     * Reads sig_coeff_flag, with ctxInc from clause 9.3.4.2.5.
     *
     * @param transformSkipContext transform_skip_context_enabled_flag holds and the block is transform-skipped or
     *     bypassed
     */
    boolean readSigCoeffFlag(
            boolean transformSkipContext,
            int xC,
            int yC,
            boolean[][] codedSubBlockFlag,
            int cIdx,
            int log2TrafoSize,
            int scanIdx) {
        int sigCtx;
        if (transformSkipContext) {
            sigCtx = cIdx == 0 ? 42 : 16;
        } else if (log2TrafoSize == 2) {
            sigCtx = CTX_IDX_MAP[(yC << 2) + xC];
        } else if (xC + yC == 0) {
            sigCtx = 0;
        } else {
            sigCtx = sigCtxFromNeighbours(xC, yC, codedSubBlockFlag, cIdx, log2TrafoSize, scanIdx);
        }
        int ctxInc = cIdx == 0 ? sigCtx : 27 + sigCtx;
        return decodeBin(ContextTable.SIG_COEFF_FLAG.offset() + ctxInc) == 1;
    }

    private static int sigCtxFromNeighbours(
            int xC, int yC, boolean[][] codedSubBlockFlag, int cIdx, int log2TrafoSize, int scanIdx) {
        int xS = xC >> 2;
        int yS = yC >> 2;
        int lastSubBlock = (1 << (log2TrafoSize - 2)) - 1;
        int prevCsbf = 0;
        if (xS < lastSubBlock && codedSubBlockFlag[xS + 1][yS]) {
            prevCsbf += 1;
        }
        if (yS < lastSubBlock && codedSubBlockFlag[xS][yS + 1]) {
            prevCsbf += 2;
        }

        int xP = xC & 3;
        int yP = yC & 3;
        int sigCtx = switch (prevCsbf) {
            case 0 -> xP + yP == 0 ? 2 : xP + yP < 3 ? 1 : 0;
            case 1 -> yP == 0 ? 2 : yP == 1 ? 1 : 0;
            case 2 -> xP == 0 ? 2 : xP == 1 ? 1 : 0;
            default -> 2;
        };

        if (cIdx == 0) {
            if (xS + yS > 0) {
                sigCtx += 3;
            }
            return sigCtx + (log2TrafoSize == 3 ? (scanIdx == 0 ? 9 : 15) : 21);
        }
        return sigCtx + (log2TrafoSize == 3 ? 9 : 12);
    }

    /**
     * The context state that carries from one coeff_abs_level_greater1_flag of a transform block to the next
     * (9.3.4.2.6): ctxSet and greater1Ctx of the previous invocation, and the flag it decoded.
     */
    static final class Greater1Context {
        int ctxSet;
        int greater1Ctx;
        boolean greater1Flag;
    }

    // 9.3.4.2.6: ctxInc from the previous invocations in the transform block
    boolean readCoeffAbsLevelGreater1Flag(
            int cIdx,
            int subBlockIndex,
            boolean firstCoeffInSubBlock,
            boolean firstSubBlock,
            int lastSubBlockGreater1Ctx,
            Greater1Context lastInvocation) {
        int ctxSet;
        int greater1Ctx;
        if (firstCoeffInSubBlock) {
            ctxSet = subBlockIndex == 0 || cIdx > 0 ? 0 : 2;
            int lastGreater1Ctx;
            if (firstSubBlock) {
                lastGreater1Ctx = 1;
            } else {
                lastGreater1Ctx = lastSubBlockGreater1Ctx;
                if (lastGreater1Ctx > 0) {
                    lastGreater1Ctx = lastInvocation.greater1Flag ? 0 : lastGreater1Ctx + 1;
                }
            }
            if (lastGreater1Ctx == 0) {
                ctxSet++;
            }
            greater1Ctx = 1;
        } else {
            ctxSet = lastInvocation.ctxSet;
            greater1Ctx = lastInvocation.greater1Ctx;
            if (greater1Ctx > 0) {
                greater1Ctx = lastInvocation.greater1Flag ? 0 : greater1Ctx + 1;
            }
        }

        int ctxInc = ctxSet * 4 + Math.min(3, greater1Ctx) + (cIdx > 0 ? 16 : 0);
        boolean greater1Flag = decodeBin(ContextTable.COEFF_ABS_LEVEL_GREATER1_FLAG.offset() + ctxInc) == 1;

        lastInvocation.ctxSet = ctxSet;
        lastInvocation.greater1Ctx = greater1Ctx;
        lastInvocation.greater1Flag = greater1Flag;
        return greater1Flag;
    }

    // 9.3.4.2.7: ctxInc from ctxSet
    boolean readCoeffAbsLevelGreater2Flag(int cIdx, int ctxSet) {
        int ctxInc = ctxSet + (cIdx > 0 ? 4 : 0);
        return decodeBin(ContextTable.COEFF_ABS_LEVEL_GREATER2_FLAG.offset() + ctxInc) == 1;
    }

    // 9.3.3.11: a TR prefix of cRiceParam, extended with an EGk suffix past 3, all bypass
    int readCoeffAbsLevelRemaining(int cRiceParam) {
        int prefix = 0;
        while (decodeBypass() == 1) {
            prefix++;
            if (prefix > MAX_COEFF_ABS_LEVEL_REMAINING_PREFIX) {
                throw new HeicFormatException("Malformed slice data: coeff_abs_level_remaining prefix too long");
            }
        }

        if (prefix <= 3) {
            return (prefix << cRiceParam) + decodeFixedLengthBypass(cRiceParam);
        }
        return (((1 << (prefix - 3)) + 3 - 1) << cRiceParam) + decodeFixedLengthBypass(prefix - 3 + cRiceParam);
    }

    boolean readCoeffSignFlag() {
        return decodeBypass() == 1;
    }

    // 9.3.3.5 Fixed-length binarization, bypass decoded
    private int decodeFixedLengthBypass(int bitCount) {
        int value = 0;
        for (int i = 0; i < bitCount; i++) {
            value = (value << 1) | decodeBypass();
        }
        return value;
    }

    // 9.3.3.2 Truncated Rice binarization with cRiceParam 0 (truncated unary), bypass decoded
    private int decodeTruncatedUnaryBypass(int cMax) {
        int value = 0;
        while (value < cMax && decodeBypass() == 1) {
            value++;
        }
        return value;
    }

    // 9.3.3.3 k-th order Exp-Golomb binarization, bypass decoded
    private int decodeExpGolombBypass(int k) {
        int value = 0;
        int suffixLength = k;
        while (decodeBypass() == 1) {
            value += 1 << suffixLength;
            suffixLength++;
            if (suffixLength == k + 31) {
                throw new HeicFormatException("Malformed slice data: Exp-Golomb prefix too long");
            }
        }
        return value + decodeFixedLengthBypass(suffixLength);
    }

    // 9.3.4.3.2 Arithmetic decoding process for a binary decision, with the context at contextIndex
    private int decodeBin(int contextIndex) {
        int state = contextStates[contextIndex];
        int pStateIdx = state >> 1;
        int valMps = state & 1;

        int ivlLpsRange = RANGE_TAB_LPS[(pStateIdx << 2) | ((ivlCurrRange >> 6) & 3)];
        ivlCurrRange -= ivlLpsRange;

        int binVal;
        if (ivlOffset >= ivlCurrRange) {
            binVal = 1 - valMps;
            ivlOffset -= ivlCurrRange;
            ivlCurrRange = ivlLpsRange;
            if (pStateIdx == 0) {
                valMps = 1 - valMps;
            }
            pStateIdx = TRANS_IDX_LPS[pStateIdx];
        } else {
            binVal = valMps;
            pStateIdx = TRANS_IDX_MPS[pStateIdx];
        }
        contextStates[contextIndex] = (pStateIdx << 1) | valMps;

        renormalize();
        return binVal;
    }

    // 9.3.4.3.3 Renormalization: doubles ivlCurrRange up to 256, reading a bit per doubling, all the bits at once
    private void renormalize() {
        if (ivlCurrRange < 256) {
            int shift = Integer.numberOfLeadingZeros(ivlCurrRange) - 23;
            ivlCurrRange <<= shift;
            ivlOffset = (ivlOffset << shift) | stream.read(shift);
        }
    }

    // 9.3.4.3.4 Bypass decoding process for binary decisions
    private int decodeBypass() {
        ivlOffset = (ivlOffset << 1) | stream.read(1);
        if (ivlOffset >= ivlCurrRange) {
            ivlOffset -= ivlCurrRange;
            return 1;
        }
        return 0;
    }

    // 9.3.4.3.5 Decoding process for binary decisions before termination. When it decodes 1, the last bit the engine
    // read is the rbsp_stop_one_bit or alignment_bit_equal_to_one that follows, so only zero bits remain to align
    private int decodeTerminate() {
        ivlCurrRange -= 2;
        if (ivlOffset >= ivlCurrRange) {
            return 1;
        }
        renormalize();
        return 0;
    }

    // 9.3.2.4 Storage and synchronization processes for context variables and Rice parameter statistics
    private static final class ContextStorage {
        private final int[] contextStates = new int[ContextTable.CONTEXT_COUNT];
        private final int[] statCoeff = new int[STAT_COEFF_COUNT];

        void store(Cabac cabac) {
            System.arraycopy(cabac.contextStates, 0, contextStates, 0, contextStates.length);
            System.arraycopy(cabac.statCoeff, 0, statCoeff, 0, statCoeff.length);
        }

        void restore(Cabac cabac) {
            System.arraycopy(contextStates, 0, cabac.contextStates, 0, contextStates.length);
            System.arraycopy(statCoeff, 0, cabac.statCoeff, 0, statCoeff.length);
        }
    }
}
