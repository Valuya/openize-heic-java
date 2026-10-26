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
    // A coeff_abs_level_remaining prefix longer than this cannot code a coefficient within the transform range
    private static final int MAX_COEFF_ABS_LEVEL_REMAINING_PREFIX = 32;
    private static final int STAT_COEFF_COUNT = 4;

    // the next context state after a most probable symbol, at [state + 128], and after a least probable one, at
    // [~state + 128] (Table 9-53)
    private static final int[] TRANSITIONS = new int[256];

    static {
        for (int state = 0; state < 128; state++) {
            int pStateIdx = state >> 1;
            int valMps = state & 1;
            TRANSITIONS[state + 128] = (TRANS_IDX_MPS[pStateIdx] << 1) | valMps;
            TRANSITIONS[~state + 128] = (TRANS_IDX_LPS[pStateIdx] << 1) | (pStateIdx == 0 ? 1 - valMps : valMps);
        }
    }

    private final int[] contextStates = new int[ContextTable.CONTEXT_COUNT];
    // 9.3.2.4: StatCoeff, the persistent Rice adaptation statistics
    final int[] statCoeff = new int[STAT_COEFF_COUNT];

    // 9.3.2.4: the context variables stored for wavefront parallel processing and for dependent slice segments
    private final ContextStorage wppStorage = new ContextStorage();
    private final ContextStorage dependentSliceStorage = new ContextStorage();

    // the slice segment data, as RBSP bytes, the arithmetic decoding engine reads, and the next byte it reads
    private byte[] sliceData = new byte[0];
    private int sliceDataLength;
    private int sliceDataPosition;
    // the NAL unit of the slice segment, for the error of reading past it, once one starts
    private NalUnitType nalUnitType = NalUnitType.UNSPEC48;
    private int nalUnitSize;

    // the arithmetic decoding engine (9.3.4.3): ivlCurrRange, and ivlOffset followed by the 7 bits read after it, at
    // most, the least significant ones not read yet: -bitsNeeded - 1 of them are read, and bitsNeeded is the number of
    // bits ivlOffset reads before the next byte is needed, minus 8
    private int ivlCurrRange;
    private int value;
    private int bitsNeeded;

    /**
     * Starts reading the slice segment data, {@code length} RBSP bytes of {@code sliceData}, the rest of a NAL unit of
     * {@code nalUnitType} and {@code nalUnitSize} bytes.
     */
    void startSliceSegmentData(byte[] sliceData, int length, NalUnitType nalUnitType, int nalUnitSize) {
        this.sliceData = sliceData;
        sliceDataLength = length;
        sliceDataPosition = 0;
        bitsNeeded = -1;
        this.nalUnitType = nalUnitType;
        this.nalUnitSize = nalUnitSize;
    }

    // 9.3.2.5 Initialization process for the arithmetic decoding engine, at a byte boundary: ivlOffset is the next 9
    // bits
    void initializeArithmeticDecoder() {
        ivlCurrRange = 510;
        value = (nextByte() << 8) | nextByte();
        bitsNeeded = -8;
        // ivlOffset is less than ivlCurrRange from then on; the engine relies on it
        if (value >> 7 >= 510) {
            throw new HeicFormatException(
                    "Malformed slice data: the arithmetic decoder starts at ivlOffset %d".formatted(value >> 7));
        }
    }

    // the next byte of the slice segment data: past them, the bits read are past the end of the NAL unit
    private int nextByte() {
        if (sliceDataPosition >= sliceDataLength) {
            throw NalUnit.overrun(nalUnitType, nalUnitSize);
        }
        return sliceData[sliceDataPosition++] & 0xFF;
    }

    // the bits the engine has read from the slice segment data, the bits read ahead of ivlOffset excluded
    private int bitPosition() {
        return sliceDataPosition * 8 + bitsNeeded + 1;
    }

    /** Skips the bits up to the next byte boundary: before PCM samples, and between substreams. */
    void alignToByte() {
        sliceDataPosition = (bitPosition() + 7) >> 3;
        bitsNeeded = -1;
    }

    /** Reads {@code bitCount} bits, up to 16, outside the arithmetic coding: the samples of a PCM coding unit. */
    int readBits(int bitCount) {
        int position = bitPosition();
        int bits = 0;
        for (int i = 0; i < bitCount; i++, position++) {
            if (position >> 3 >= sliceDataLength) {
                throw NalUnit.overrun(nalUnitType, nalUnitSize);
            }
            bits = (bits << 1) | ((sliceData[position >> 3] >> (7 - (position & 7))) & 1);
        }
        // as if the engine had read up to there
        sliceDataPosition = (position + 7) >> 3;
        bitsNeeded = position - sliceDataPosition * 8 - 1;
        return bits;
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

    // 9.3.4.2.4: ctxInc from whether the sub-block right or the one below has coefficients
    boolean readCodedSubBlockFlag(boolean codedNeighbour, int cIdx) {
        int ctxInc = (codedNeighbour ? 1 : 0) + (cIdx == 0 ? 0 : 2);
        return decodeBin(ContextTable.CODED_SUB_BLOCK_FLAG.offset() + ctxInc) == 1;
    }

    // with ctxInc from clause 9.3.4.2.5: 0 or 1
    int readSigCoeffFlag(int ctxInc) {
        return decodeBin(ContextTable.SIG_COEFF_FLAG.offset() + ctxInc);
    }

    // with ctxInc from clause 9.3.4.2.6: 0 or 1
    int readCoeffAbsLevelGreater1Flag(int ctxInc) {
        return decodeBin(ContextTable.COEFF_ABS_LEVEL_GREATER1_FLAG.offset() + ctxInc);
    }

    // 9.3.4.2.7: ctxInc from ctxSet
    boolean readCoeffAbsLevelGreater2Flag(int cIdx, int ctxSet) {
        int ctxInc = ctxSet + (cIdx > 0 ? 4 : 0);
        return decodeBin(ContextTable.COEFF_ABS_LEVEL_GREATER2_FLAG.offset() + ctxInc) == 1;
    }

    // 9.3.3.11: a TR prefix of cRiceParam, extended with an EGk suffix past 3, all bypass. The prefix, ones ended by a
    // zero, is read up to 8 bins at a time while the slice data has the byte those bins need; then bin by bin, so that
    // a prefix too long and the end of the data are reported at the same bin as when reading bin by bin
    int readCoeffAbsLevelRemaining(int cRiceParam) {
        int prefix = 0;
        int ones = 8;
        while (ones == 8 && sliceDataPosition < sliceDataLength) {
            ones = decodeBypassOnes();
            prefix += ones;
            if (prefix > MAX_COEFF_ABS_LEVEL_REMAINING_PREFIX) {
                throw prefixTooLong();
            }
        }
        while (ones == 8 && decodeBypass() == 1) {
            prefix++;
            if (prefix > MAX_COEFF_ABS_LEVEL_REMAINING_PREFIX) {
                throw prefixTooLong();
            }
        }

        if (prefix <= 3) {
            return (prefix << cRiceParam) + decodeFixedLengthBypass(cRiceParam);
        }
        return (((1 << (prefix - 3)) + 3 - 1) << cRiceParam) + decodeFixedLengthBypass(prefix - 3 + cRiceParam);
    }

    private static HeicFormatException prefixTooLong() {
        return new HeicFormatException("Malformed slice data: coeff_abs_level_remaining prefix too long");
    }

    /** {@code count} coeff_sign_flag, the first one read the most significant bit. */
    int readCoeffSignFlags(int count) {
        return decodeFixedLengthBypass(count);
    }

    // 9.3.3.5 Fixed-length binarization, bypass decoded
    private int decodeFixedLengthBypass(int bitCount) {
        int value = 0;
        int remaining = bitCount;
        while (remaining > 8) {
            value = (value << 8) | decodeBypassBits(8);
            remaining -= 8;
        }
        return remaining == 0 ? value : (value << remaining) | decodeBypassBits(remaining);
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

    // 9.3.4.3.2 Arithmetic decoding process for a binary decision, with the context at contextIndex. The symbol picks
    // the new range, ivlOffset and context state through a mask rather than a branch: which symbol comes is a coin toss
    // the processor would often mispredict.
    private int decodeBin(int contextIndex) {
        int state = contextStates[contextIndex];
        int ivlLpsRange = RANGE_TAB_LPS[((state >> 1) << 2) | ((ivlCurrRange >> 6) & 3)];
        int mpsRange = ivlCurrRange - ivlLpsRange;
        int scaledMpsRange = mpsRange << 7;
        // -1 for the least probable symbol: ivlOffset is at least the range of the most probable one
        int lps = (scaledMpsRange - value - 1) >> 31;
        value -= scaledMpsRange & lps;
        int range = mpsRange + ((ivlLpsRange - mpsRange) & lps);
        state ^= lps;
        contextStates[contextIndex] = TRANSITIONS[state + 128];
        renormalize(range);
        return state & 1;
    }

    // 9.3.4.3.3 Renormalization: doubles the range up to 256, and ivlOffset with it, all the bits at once
    private void renormalize(int range) {
        int shift = Integer.numberOfLeadingZeros(range) - 23;
        ivlCurrRange = range << shift;
        value <<= shift;
        bitsNeeded += shift;
        if (bitsNeeded >= 0) {
            value |= nextByte() << bitsNeeded;
            bitsNeeded -= 8;
        }
    }

    // 9.3.4.3.4 Bypass decoding process for binary decisions
    private int decodeBypass() {
        value <<= 1;
        if (++bitsNeeded == 0) {
            value |= nextByte();
            bitsNeeded = -8;
        }
        int scaledRange = ivlCurrRange << 7;
        // -1 when ivlOffset is at least ivlCurrRange
        int one = (scaledRange - value - 1) >> 31;
        value -= scaledRange & one;
        return -one;
    }

    // bitCount bypass bins at once, 1 to 8, most significant first. Each bin doubles ivlOffset, takes the next bit, and
    // is 1 when ivlOffset reaches ivlCurrRange, which it then takes off: the bins are the binary digits of the quotient
    // of the extended ivlOffset by ivlCurrRange, one division, since ivlOffset is less than ivlCurrRange
    private int decodeBypassBits(int bitCount) {
        value <<= bitCount;
        bitsNeeded += bitCount;
        if (bitsNeeded >= 0) {
            value |= nextByte() << bitsNeeded;
            bitsNeeded -= 8;
        }
        int scaledRange = ivlCurrRange << 7;
        int bins = value / scaledRange;
        value -= bins * scaledRange;
        return bins;
    }

    // The bypass bins up to the first 0, at most 8, returning the number of 1 bins. The next 8 bins are the quotient of
    // one division, as in decodeBypassBits, which needs the next byte of the slice data: the caller checks it is there.
    // Only the leading ones and the 0 that ends them are taken, their own quotient being the leading bits of that one
    private int decodeBypassOnes() {
        int nextByte = sliceData[sliceDataPosition] & 0xFF;
        int scaledRange = ivlCurrRange << 7;
        int bins = ((value << 8) | (nextByte << (bitsNeeded + 8))) / scaledRange;
        int ones = Integer.numberOfLeadingZeros(~(bins << 24));
        int taken = Math.min(ones + 1, 8);
        value <<= taken;
        bitsNeeded += taken;
        if (bitsNeeded >= 0) {
            value |= nextByte << bitsNeeded;
            sliceDataPosition++;
            bitsNeeded -= 8;
        }
        value -= (bins >> (8 - taken)) * scaledRange;
        return ones;
    }

    // 9.3.4.3.5 Decoding process for binary decisions before termination. When it decodes 1, the last bit the engine
    // read is the rbsp_stop_one_bit or alignment_bit_equal_to_one that follows, so only zero bits remain to align
    private int decodeTerminate() {
        ivlCurrRange -= 2;
        if (value >= ivlCurrRange << 7) {
            return 1;
        }
        renormalize(ivlCurrRange);
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
