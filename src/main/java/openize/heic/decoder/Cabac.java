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

public class Cabac {
    // Table 9-46 – Specification of rangeTabLps depending on the values of pStateIdx and qRangeIdx
    public static final byte[][] rangeTabLps = new byte[][] {
        {(byte) 128, (byte) 176, (byte) 208, (byte) 240},
        {(byte) 128, (byte) 167, (byte) 197, (byte) 227},
        {(byte) 128, (byte) 158, (byte) 187, (byte) 216},
        {123, (byte) 150, (byte) 178, (byte) 205},
        {116, (byte) 142, (byte) 169, (byte) 195},
        {111, (byte) 135, (byte) 160, (byte) 185},
        {105, (byte) 128, (byte) 152, (byte) 175},
        {100, 122, (byte) 144, (byte) 166},
        {95, 116, (byte) 137, (byte) 158},
        {90, 110, (byte) 130, (byte) 150},
        {85, 104, 123, (byte) 142},
        {81, 99, 117, (byte) 135},
        {77, 94, 111, (byte) 128},
        {73, 89, 105, 122},
        {69, 85, 100, 116},
        {66, 80, 95, 110},
        {62, 76, 90, 104},
        {59, 72, 86, 99},
        {56, 69, 81, 94},
        {53, 65, 77, 89},
        {51, 62, 73, 85},
        {48, 59, 69, 80},
        {46, 56, 66, 76},
        {43, 53, 63, 72},
        {41, 50, 59, 69},
        {39, 48, 56, 65},
        {37, 45, 54, 62},
        {35, 43, 51, 59},
        {33, 41, 48, 56},
        {32, 39, 46, 53},
        {30, 37, 43, 50},
        {29, 35, 41, 48},
        {27, 33, 39, 45},
        {26, 31, 37, 43},
        {24, 30, 35, 41},
        {23, 28, 33, 39},
        {22, 27, 32, 37},
        {21, 26, 30, 35},
        {20, 24, 29, 33},
        {19, 23, 27, 31},
        {18, 22, 26, 30},
        {17, 21, 25, 28},
        {16, 20, 23, 27},
        {15, 19, 22, 25},
        {14, 18, 21, 24},
        {14, 17, 20, 23},
        {13, 16, 19, 22},
        {12, 15, 18, 21},
        {12, 14, 17, 20},
        {11, 14, 16, 19},
        {11, 13, 15, 18},
        {10, 12, 15, 17},
        {10, 12, 14, 16},
        {9, 11, 13, 15},
        {9, 11, 12, 14},
        {8, 10, 12, 14},
        {8, 9, 11, 13},
        {7, 9, 11, 12},
        {7, 9, 10, 12},
        {7, 8, 10, 11},
        {6, 8, 9, 11},
        {6, 7, 9, 10},
        {6, 7, 8, 9},
        {2, 2, 2, 2}
    };
    // Table 9-47 – State transition table pStateIdx to transIdxMps
    public static final byte[] transIdxMps = new byte[] {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
        17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32,
        33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48,
        49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 62, 63
    };
    // Table 9-47 – State transition table pStateIdx to transIdxLps
    public static final byte[] transIdxLps = new byte[] {
        0, 0, 1, 2, 2, 4, 4, 5, 6, 7, 8, 9, 9, 11, 11, 12,
        13, 13, 15, 15, 16, 16, 18, 18, 19, 19, 21, 21, 22, 22, 23, 24,
        24, 25, 26, 26, 27, 27, 28, 29, 29, 30, 30, 30, 31, 32, 32, 33,
        33, 33, 34, 34, 35, 35, 35, 36, 36, 36, 37, 37, 37, 38, 38, 63
    };
    // Table 9-50 – Specification of ctxIdxMap
    public static final byte[] ctxIdxMap = {0, 1, 4, 5, 2, 3, 4, 5, 6, 6, 8, 8, 7, 7, 8};
    private static final byte[] initValueSaoMergeFlag = {(byte) 153, (byte) 153, (byte) 153};
    private static final byte[] initValueSaoTypeIdxFlag = {(byte) 200, (byte) 185, (byte) 160};
    private static final byte[][] initValueSplitCuFlag = {
        new byte[] {(byte) 139, (byte) 141, (byte) 157},
        new byte[] {107, (byte) 139, 126},
        new byte[] {107, (byte) 139, 126},
    };
    private static final byte[] initValueCuTransquantBypassFlag = {(byte) 154, (byte) 154, (byte) 154};
    private static final byte[][] initValueCuSkipFlag = {
        new byte[] {(byte) 197, (byte) 185, (byte) 201},
        new byte[] {(byte) 197, (byte) 185, (byte) 201},
    };
    private static final byte[] initValuePredModeFlag = {(byte) 149, (byte) 134};

    // FL = fixed-length
    // TR = truncated Rice
    // EGk = k-th order Exp-Golomb
    private static final byte[][] initValuePartMode = {
        new byte[] {(byte) 184},
        new byte[] {(byte) 154, (byte) 139, (byte) 154, (byte) 154},
        new byte[] {(byte) 154, (byte) 139, (byte) 154, (byte) 154}
    };
    private static final byte[] initValuePrevIntraLumaPredFlag = {(byte) 184, (byte) 154, (byte) 183};
    private static final byte[] initValueIntraChromaPredMode = {63, (byte) 152, (byte) 152};
    private static final byte[] initValueRqtRootCbf = {79, 79};
    private static final byte[] initValueMergeFlag = {110, (byte) 154};
    private static final byte[] initValueMergeIdx = {122, (byte) 137};
    private static final byte[][] initValueInterPredIdc = {
        new byte[] {95, 79, 63, 31, 31},
        new byte[] {95, 79, 63, 31, 31}
    };
    private static final byte[][] initValueRefIdx = {
        new byte[] {(byte) 153, (byte) 153},
        new byte[] {(byte) 153, (byte) 153}
    };
    private static final byte[] initValueMvpFlag = {(byte) 168, (byte) 168};
    private static final byte[][] initValueSplitTransformFlag = {
        new byte[] {(byte) 153, (byte) 138, (byte) 138},
        new byte[] {124, (byte) 138, 94},
        new byte[] {(byte) 224, (byte) 167, 122}
    };
    private static final byte[][] initValueCbfLuma = {
        new byte[] {111, (byte) 141},
        new byte[] {(byte) 153, 111},
        new byte[] {(byte) 153, 111}
    };
    private static final byte[][] initValueCbfChroma = {
        new byte[] {94, (byte) 138, (byte) 182, (byte) 154, (byte) 154},
        new byte[] {(byte) 149, 107, (byte) 167, (byte) 154, (byte) 154},
        new byte[] {(byte) 149, 92, (byte) 167, (byte) 154, (byte) 154}
    };
    private static final byte[] initValueAbsMvdGreaterFlag = {(byte) 140, (byte) 198, (byte) 169, (byte) 198};
    private static final byte[][] initValueLastSigCoeffPrefix = {
        new byte[] {
            110,
            110,
            124,
            125,
            (byte) 140,
            (byte) 153,
            125,
            127,
            (byte) 140,
            109,
            111,
            (byte) 143,
            127,
            111,
            79,
            108,
            123,
            63
        },
        new byte[] {125, 110, 94, 110, 95, 79, 125, 111, 110, 78, 110, 111, 111, 95, 94, 108, 123, 108},
        new byte[] {125, 110, 124, 110, 95, 94, 125, 111, 111, 79, 125, 126, 111, 111, 79, 108, 123, 93}
    };
    private static final byte[][] initValueCodedSubBlockFlag = {
        new byte[] {91, (byte) 171, (byte) 134, (byte) 141},
        new byte[] {121, (byte) 140, 61, (byte) 154},
        new byte[] {121, (byte) 140, 61, (byte) 154}
    };
    private static final byte[][] initValueSigCoeffFlag = {
        new byte[] {
            111,
            111,
            125,
            110,
            110,
            94,
            124,
            108,
            124,
            107,
            125,
            (byte) 141,
            (byte) 179,
            (byte) 153,
            125,
            107,
            125,
            (byte) 141,
            (byte) 179,
            (byte) 153,
            125,
            107,
            125,
            (byte) 141,
            (byte) 179,
            (byte) 153,
            125,
            (byte) 140,
            (byte) 139,
            (byte) 182,
            (byte) 182,
            (byte) 152,
            (byte) 136,
            (byte) 152,
            (byte) 136,
            (byte) 153,
            (byte) 136,
            (byte) 139,
            111,
            (byte) 136,
            (byte) 139,
            111,
            (byte) 141,
            111
        },
        new byte[] {
            (byte) 155,
            (byte) 154,
            (byte) 139,
            (byte) 153,
            (byte) 139,
            123,
            123,
            63,
            (byte) 153,
            (byte) 166,
            (byte) 183,
            (byte) 140,
            (byte) 136,
            (byte) 153,
            (byte) 154,
            (byte) 166,
            (byte) 183,
            (byte) 140,
            (byte) 136,
            (byte) 153,
            (byte) 154,
            (byte) 166,
            (byte) 183,
            (byte) 140,
            (byte) 136,
            (byte) 153,
            (byte) 154,
            (byte) 170,
            (byte) 153,
            123,
            123,
            107,
            121,
            107,
            121,
            (byte) 167,
            (byte) 151,
            (byte) 183,
            (byte) 140,
            (byte) 151,
            (byte) 183,
            (byte) 140,
            (byte) 140,
            (byte) 140
        },
        new byte[] {
            (byte) 170,
            (byte) 154,
            (byte) 139,
            (byte) 153,
            (byte) 139,
            123,
            123,
            63,
            124,
            (byte) 166,
            (byte) 183,
            (byte) 140,
            (byte) 136,
            (byte) 153,
            (byte) 154,
            (byte) 166,
            (byte) 183,
            (byte) 140,
            (byte) 136,
            (byte) 153,
            (byte) 154,
            (byte) 166,
            (byte) 183,
            (byte) 140,
            (byte) 136,
            (byte) 153,
            (byte) 154,
            (byte) 170,
            (byte) 153,
            (byte) 138,
            (byte) 138,
            122,
            121,
            122,
            121,
            (byte) 167,
            (byte) 151,
            (byte) 183,
            (byte) 140,
            (byte) 151,
            (byte) 183,
            (byte) 140,
            (byte) 140,
            (byte) 140
        },
    };
    private static final byte[][] initValueCoeffAbsLevelGreater1Flag = {
        new byte[] {
            (byte) 140,
            92,
            (byte) 137,
            (byte) 138,
            (byte) 140,
            (byte) 152,
            (byte) 138,
            (byte) 139,
            (byte) 153,
            74,
            (byte) 149,
            92,
            (byte) 139,
            107,
            122,
            (byte) 152,
            (byte) 140,
            (byte) 179,
            (byte) 166,
            (byte) 182,
            (byte) 140,
            (byte) 227,
            122,
            (byte) 197
        },
        new byte[] {
            (byte) 154,
            (byte) 196,
            (byte) 196,
            (byte) 167,
            (byte) 154,
            (byte) 152,
            (byte) 167,
            (byte) 182,
            (byte) 182,
            (byte) 134,
            (byte) 149,
            (byte) 136,
            (byte) 153,
            121,
            (byte) 136,
            (byte) 137,
            (byte) 169,
            (byte) 194,
            (byte) 166,
            (byte) 167,
            (byte) 154,
            (byte) 167,
            (byte) 137,
            (byte) 182
        },
        new byte[] {
            (byte) 154,
            (byte) 196,
            (byte) 167,
            (byte) 167,
            (byte) 154,
            (byte) 152,
            (byte) 167,
            (byte) 182,
            (byte) 182,
            (byte) 134,
            (byte) 149,
            (byte) 136,
            (byte) 153,
            121,
            (byte) 136,
            122,
            (byte) 169,
            (byte) 208,
            (byte) 166,
            (byte) 167,
            (byte) 154,
            (byte) 152,
            (byte) 167,
            (byte) 182
        }
    };
    private static final byte[][] initValueCoeffAbsLevelGreater2Flag = {
        new byte[] {(byte) 138, (byte) 153, (byte) 136, (byte) 167, (byte) 152, (byte) 152},
        new byte[] {107, (byte) 167, 91, 122, 107, (byte) 167},
        new byte[] {107, (byte) 167, 91, 107, 107, (byte) 167}
    };
    private final BitStreamWithNalSupport stream;
    // sync section
    private final ContextVariable[][] contextTableSync = new ContextVariable[40][];
    final ContextVariable[][] contextTable = new ContextVariable[40][];
    final byte[] statCoeff = new byte[4];
    // not implemented
    /*UInt32*/ long predictorPaletteSize;
    /// *UInt16*/ int[][] PredictorPaletteEntries;
    int ivlCurrRange;
    // not implemented
    int ivlOffset;
    final byte[] tableStatCoeffSync = new byte[4];

    public Cabac(BitStreamWithNalSupport stream) {
        this.stream = stream;
        Scans.initialize();
    }

    final boolean readEndOfSliceSegmentFlag() {
        // FL: cMax: 1
        // bin 1: terminate
        return decodeBeforeTermination() == 1;
    }

    final int readEndOfSubsetOneBit() {
        // FL: cMax: 1
        // bin 1: terminate
        return decodeBeforeTermination();
    }

    final boolean readSaoMergeFlag() {
        // FL: cMax: 1
        // bin 1: 0
        ContextVariable[] model = contextTable[CabacType.saoMergeFlag.ordinal()];
        return decodeGeneralForBinaryDecision(model[0]) == 1;
    }

    final int readSaoTypeIdx() {
        // TR: cMax: 2, cRiceParam: 0
        // bin 1: 0, bin 2: bypass
        ContextVariable[] model = contextTable[CabacType.saoTypeIdx.ordinal()];

        int bin0 = decodeGeneralForBinaryDecision(model[0]);

        if (bin0 == 0) {
            return 0;
        }

        int bin1 = decodeBypass();

        if (bin1 == 0) {
            return 1;
        } else {
            return 2;
        }
    }

    final int readSaoOffsetAbs(int bitDepth) {
        // TR: cMax: defined next line, cRiceParam: 0
        // bin 1-5: bypass
        int cMax = (1 << (Math.min(bitDepth, 10) - 5)) - 1;

        for (int i = 0; i < cMax; i++)
            if (decodeBypass() == 0) {
                return i;
            }

        return cMax;
    }

    final int readSaoOffsetSign() {
        // FL: cMax: 1
        // bin 1: bypass
        return decodeBypass();
    }

    final int readSaoBandPosition() {
        // FL: cMax: 31
        // bin 1-5+: bypass
        return decodeFixedLengthBypass(5);
    }

    final byte readSaoClass() {
        // FL: cMax: 3
        // bin 1-2: bypass
        return (byte) decodeFixedLengthBypass(2);
    }

    final boolean readSplitCuFlag(int x0, int y0, HeicPicture picture, int cqtDepth) {
        // FL: cMax: 1
        // bin 1: clause 9.3.4.2.2
        int index = getContextIndexFromNeighborState(x0, y0, picture, cqtDepth);
        ContextVariable[] model = contextTable[CabacType.splitCuFlag.ordinal()];
        return decodeGeneralForBinaryDecision(model[index]) == 1;
    }

    final boolean readCuTransquantBypassFlag() {
        // FL: cMax: 1
        // bin 1: 0
        ContextVariable[] model = contextTable[CabacType.cuTransquantBypassFlag.ordinal()];
        return decodeGeneralForBinaryDecision(model[0]) == 1;
    }

    final boolean readCuSkipFlag(int x0, int y0, HeicPicture picture, int cqtDepth) {
        // FL: cMax: 1
        // bin 1: clause 9.3.4.2.2
        int index = getContextIndexFromNeighborState(x0, y0, picture, cqtDepth);
        ContextVariable[] model = contextTable[CabacType.splitCuFlag.ordinal()];
        return decodeGeneralForBinaryDecision(model[index]) == 1;
    }

    final boolean readPaletteModeFlag() {
        // FL: cMax: 1
        // bin 1: 0
        ContextVariable[] model = contextTable[CabacType.paletteModeFlag.ordinal()];
        return decodeGeneralForBinaryDecision(model[0]) == 1;
    }

    final boolean readPredModeFlag() {
        // FL: cMax: 1
        // bin 1: 0
        ContextVariable[] model = contextTable[CabacType.predModeFlag.ordinal()];
        return decodeGeneralForBinaryDecision(model[0]) == 1;
    }

    // not implemented

    // The specification of the truncated Rice (TR) binarization process, the k-th order Exp-Golomb (EGk) binarization
    // process, limited k-th order Exp-Golomb(EGk) binarization process and  are
    // given in clauses 9.3.3.2 through 9.3.3.5, respectively.Other binarizations are specified in clauses 9.3.3.6
    // through 9.3.3.10.

    final PartMode readPartMode(HeicPicture picture, PredMode predMode, int cLog2CbSize) {
        // 9.3.3.7
        // bin 1: 0
        // bin 2: 1
        // bin 3: 2 (log2CbSize == MinCbLog2SizeY)
        // bin 3: 3 (log2CbSize > MinCbLog2SizeY)
        // bin 4: bypass

        ContextVariable[] model = contextTable[CabacType.partMode.ordinal()];

        if (predMode == PredMode.MODE_INTRA) {
            int bit = decodeGeneralForBinaryDecision(model[0]);
            return bit == 1 ? PartMode.part2Nx2N : PartMode.partNxN;
        } else // MODE_INTER
        {
            SeqParameterSetRbsp sps = picture.sps;

            int bit0 = decodeGeneralForBinaryDecision(model[0]);
            if (bit0 == 1) {
                return PartMode.part2Nx2N;
            }

            int bit1 = decodeGeneralForBinaryDecision(model[1]);

            if (cLog2CbSize > (sps.getMinCbLog2SizeY() & 0xFF)) {
                if (!sps.ampEnabledFlag) {
                    return bit1 == 1 ? PartMode.part2NxN : PartMode.partNx2N;
                }

                int bit2 = decodeGeneralForBinaryDecision(model[3]);

                if (bit2 == 1) {
                    return bit1 == 1 ? PartMode.part2NxN : PartMode.partNx2N;
                }

                int bit3 = decodeBypass();
                if (bit1 == 1) {
                    return bit3 == 0 ? PartMode.part2NxnU : PartMode.part2NxnD;
                } else {
                    return bit3 == 0 ? PartMode.partNLx2N : PartMode.partNRx2N;
                }
            } else {
                if (bit1 == 1) {
                    return PartMode.part2NxN;
                }

                if (cLog2CbSize == 3) {
                    return PartMode.partNx2N;
                }

                int bit2 = decodeGeneralForBinaryDecision(model[2]);
                return bit2 == 1 ? PartMode.partNx2N : PartMode.partNxN;
            }
        }
    }

    final boolean readPcmFlag() {
        // FL: cMax: 1
        // bin 1: terminate
        return decodeBeforeTermination() == 1;
    }

    final boolean readPrevIntraLumaPredFlag() {
        // FL: cMax: 1
        // bin 1: 0
        ContextVariable[] model = contextTable[CabacType.prevIntraLumaPredFlag.ordinal()];
        return decodeGeneralForBinaryDecision(model[0]) == 1;
    }

    final int readMpmIdx() {
        // TR: cMax: 2, cRiceParam: 0
        // bin 1: bypass, bin 2: bypass

        for (int i = 0; i < 2; i++)
            if (decodeBypass() == 0) {
                return i;
            }

        return 2;
    }

    final IntraPredMode readRemIntraLumaPredMode() {
        // FL: cMax: 31
        // bin 1-5+: bypass
        return IntraPredMode.get(decodeFixedLengthBypass(5));
    }

    final byte readIntraChromaPredMode() {
        // 9.3.3.8
        // bin 1: 0
        // bin 2-3: bypass
        ContextVariable[] model = contextTable[CabacType.intraChromaPredMode.ordinal()];

        int bin0 = decodeGeneralForBinaryDecision(model[0]);

        if (bin0 == 0) {
            return (byte) 4;
        } else {
            return (byte) decodeFixedLengthBypass(2);
        }
    }

    final boolean readRqtRootCbf() {
        // FL: cMax: 1
        // bin 1: 0
        ContextVariable[] model = contextTable[CabacType.rqtRootCbf.ordinal()];
        return decodeGeneralForBinaryDecision(model[0]) == 1;
    }

    final boolean readSplitTransformFlag(int log2TrafoSize) {
        // FL: cMax: 1
        // bin 1: 5 − log2TrafoSize
        int index = 5 - log2TrafoSize;
        ContextVariable[] model = contextTable[CabacType.splitTransformFlag.ordinal()];
        return decodeGeneralForBinaryDecision(model[index]) == 1;
    }

    final boolean readCbfLuma(int trafoDepth) {
        // FL: cMax: 1
        // bin 1: trafoDepth == 0 ? 1 : 0
        ContextVariable[] model = contextTable[CabacType.cbfLuma.ordinal()];
        return decodeGeneralForBinaryDecision(model[trafoDepth == 0 ? 1 : 0]) == 1;
    }

    final boolean readCbfChroma(int trafoDepth) {
        // FL: cMax: 1
        // bin 1: trafoDepth
        ContextVariable[] model = contextTable[CabacType.cbfChroma.ordinal()];
        return decodeGeneralForBinaryDecision(model[trafoDepth]) == 1;
    }

    final boolean readTuResidualActFlag() {
        // FL: cMax: 1
        // bin 1: 0
        ContextVariable[] model = contextTable[CabacType.tuResidualActFlag.ordinal()];
        return decodeGeneralForBinaryDecision(model[0]) == 1;
    }

    final int readCuQpDeltaAbs() {
        // 9.3.3.10
        // bin 1: 0
        // bin 2-5: 1
        // bin 5+: bypass
        ContextVariable[] model = contextTable[CabacType.cuQpDeltaAbs.ordinal()];

        if (decodeGeneralForBinaryDecision(model[0]) == 0) {
            return 0;
        }

        int value = 1;
        for (int i = 0; i < 4; i++) // TR; cMax = 5 and cRiceParam = 0
        {
            if (decodeGeneralForBinaryDecision(model[1]) == 0) {
                return value;
            } else {
                value++;
            }
        }

        return value + decodeExpGolombBypass(0);
    }

    final boolean readCuQpDeltaSignFlag() {
        // FL: cMax: 1
        // bin 1: bypass
        return decodeBypass() == 1;
    }

    final boolean readCuChromaQpOffsetFlag() {
        // FL: cMax: 1
        // bin 1: 0
        ContextVariable[] model = contextTable[CabacType.cuChromaQpOffsetFlag.ordinal()];
        return decodeGeneralForBinaryDecision(model[0]) == 1;
    }

    final int readCuChromaQpOffsetIdx(int cMax) {
        // TR: cMax: chroma_qp_offset_list_len_minus1, cRiceParam = 0
        // bin 0-4: 0
        ContextVariable[] model = contextTable[CabacType.cuChromaQpOffsetIdx.ordinal()];

        for (int i = 0; i < cMax; i++)
            if (decodeGeneralForBinaryDecision(model[0]) == 0) {
                return i;
            }

        return cMax;
        // may be incorrect as not covered with tests. Another realization:
        // return DecodeGeneralForBinaryDecision(model[0]);
    }

    final int readLog2ResScaleAbsPlus1(int c) {
        // TR cMax = 4, cRiceParam = 0
        // bin n: 4 * c + n
        ContextVariable[] model = contextTable[CabacType.log2ResScaleAbsPlus1.ordinal()];
        int value = 0;

        for (int n = 0; n < 4; n++) {
            if (decodeGeneralForBinaryDecision(model[4 * c + n]) == 0) {
                break;
            }

            value++;
        }

        return value;
    }

    final boolean readResScaleSignFlag(int c) {
        // FL cMax = 1
        // bin 0: c
        ContextVariable[] model = contextTable[CabacType.resScaleSignFlag.ordinal()];
        return decodeGeneralForBinaryDecision(model[c]) == 1;
    }

    final boolean readTransformSkipFlag(int cIdx) {
        // FL cMax = 1
        // bin 0: 0
        int index = cIdx == 0 ? 0 : 1;
        ContextVariable[] model = contextTable[CabacType.transformSkipFlag.ordinal()];
        return decodeGeneralForBinaryDecision(model[index]) == 1;
    }

    final boolean readExplicitRdpcmFlag(int cIdx) {
        // FL cMax = 1
        // bin 0: 0
        int index = (cIdx == 0) ? 0 : 1;
        ContextVariable[] model = contextTable[CabacType.explicitRdpcmFlag.ordinal()];
        return decodeGeneralForBinaryDecision(model[index]) == 1;
    }

    final boolean readExplicitRdpcmDirFlag(int cIdx) {
        // FL cMax = 1
        // bin 0: 0
        int index = (cIdx == 0) ? 0 : 1;
        ContextVariable[] model = contextTable[CabacType.explicitRdpcmDirFlag.ordinal()];
        return decodeGeneralForBinaryDecision(model[index]) == 1;
    }

    final int readLastSigCoeffPrefix(int log2TrafoSize, int cIdx, CabacType type) {
        // TR cMax = (log2TrafoSize << 1 ) − 1, cRiceParam = 0
        // 0..17 (clause 9.3.4.2.3)
        ContextVariable[] model = contextTable[type.ordinal()];
        int cMax = (log2TrafoSize << 1) - 1;

        int ctxOffset, ctxShift;

        if (cIdx == 0) {
            ctxOffset = 3 * (log2TrafoSize - 2) + ((log2TrafoSize - 1) >> 2);
            ctxShift = (log2TrafoSize + 1) >> 2;
        } else {
            ctxOffset = 15;
            ctxShift = log2TrafoSize - 2;
        }

        int ctxInc;

        for (int binIdx = 0; binIdx < cMax; binIdx++) {
            ctxInc = (binIdx >> ctxShift) + ctxOffset;

            if (decodeGeneralForBinaryDecision(model[ctxInc]) == 0) {
                return binIdx;
            }
        }

        return cMax;
    }

    final int readLastSigCoeffSuffix(int lastSignificantCoeffPrefix) {
        // FL cMax = ( 1 << ((last_sig_coeff_x_prefix >> 1 ) − 1 ) − 1 )
        // bin 0-2: bypass
        return decodeFixedLengthBypass((lastSignificantCoeffPrefix >> 1) - 1);
    }

    final boolean readCodedSubBlockFlag(
            /*UInt32*/ long xS, /*UInt32*/ long yS, boolean[][] codedSubBlockFlag, int cIdx, int log2TrafoSize) {
        // FL cMax = 1
        // bin 0: 0..3 (clause 9.3.4.2.4)
        ContextVariable[] model = contextTable[CabacType.codedSubBlockFlag.ordinal()];

        int csbfCtx = 0;

        if ((xS & 0xFFFFFFFFL) < (1L << (log2TrafoSize - 2)) - 1) {
            csbfCtx += codedSubBlockFlag[(int) (((xS & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL)][(int) (yS)] ? 1 : 0;
        }

        if ((yS & 0xFFFFFFFFL) < (1L << (log2TrafoSize - 2)) - 1) {
            csbfCtx += codedSubBlockFlag[(int) (xS)][(int) (((yS & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL)] ? 1 : 0;
        }

        int ctxInc = Math.min(csbfCtx, 1);

        if (cIdx != 0) {
            ctxInc += 2;
        }

        return decodeGeneralForBinaryDecision(model[ctxInc]) == 1;
    }

    final boolean readSigCoeffFlag(
            HeicPicture picture,
            boolean transformSkipFlag,
            int xC,
            int yC,
            boolean[][] codedSubBlockFlag,
            int cIdx,
            int log2TrafoSize,
            int scanIdx) {
        // FL cMax = 1
        // bin 0: 0..43 (clause 9.3.4.2.5)
        SeqParameterSetRbsp sps = picture.sps;

        int sigCtx;

        if (sps.spsRangeExt.transformSkipContextEnabledFlag
                && (transformSkipFlag || stream.getContext().cuTransquantBypassFlag)) {
            sigCtx = (cIdx == 0) ? 42 : 16;
        } else if (log2TrafoSize == 2) {
            sigCtx = ctxIdxMap[(yC << 2) + xC] & 0xFF;
        } else if (xC + yC == 0) {
            sigCtx = 0;
        } else {
            int xS = xC >> 2;
            int yS = yC >> 2;

            int prevCsbf = 0;
            if (xS < (1 << (log2TrafoSize - 2)) - 1) {
                prevCsbf += codedSubBlockFlag[xS + 1][yS] ? 1 : 0;
            }
            if (yS < (1 << (log2TrafoSize - 2)) - 1) {
                prevCsbf += codedSubBlockFlag[xS][yS + 1] ? 2 : 0;
            }

            int xP = xC & 3;
            int yP = yC & 3;

            switch (prevCsbf) {
                case 0:
                    sigCtx = (xP + yP == 0) ? 2 : (xP + yP < 3) ? 1 : 0;
                    break;
                case 1:
                    sigCtx = (yP == 0) ? 2 : (yP == 1) ? 1 : 0;
                    break;
                case 2:
                    sigCtx = (xP == 0) ? 2 : (xP == 1) ? 1 : 0;
                    break;
                default:
                    sigCtx = 2;
                    break;
            }

            if (cIdx == 0) {
                if (xS + yS > 0) {
                    sigCtx += 3;
                }

                if (log2TrafoSize == 3) {
                    sigCtx += (scanIdx == 0) ? 9 : 15;
                } else {
                    sigCtx += 21;
                }
            } else {
                if (log2TrafoSize == 3) {
                    sigCtx += 9;
                } else {
                    sigCtx += 12;
                }
            }
        }

        int ctxInc = sigCtx + (cIdx == 0 ? 0 : 27);

        ContextVariable[] model = contextTable[CabacType.sigCoeffFlag.ordinal()];
        return decodeGeneralForBinaryDecision(model[ctxInc]) == 1;
    }

    final boolean readCoeffAbsLevelGreater1Flag(
            int cIdx,
            int i,
            boolean firstCoeffInSubblock,
            boolean firstSubblock,
            int lastSubblockGreater1Ctx,
            /*ref*/ int[] lastInvocationCtxSet,
            /*ref*/ int[] lastInvocationGreater1Ctx,
            /*ref*/ boolean[] lastInvocationCoeffAbsLevelGreater1Flag) {
        // FL cMax = 1
        // bin 0: 0..23 (clause 9.3.4.2.6)

        int lastGreater1Ctx;
        int greater1Ctx;
        int ctxSet;

        if (firstCoeffInSubblock) {
            ctxSet = (i == 0 || cIdx > 0) ? 0 : 2;

            if (firstSubblock) {
                lastGreater1Ctx = 1;
            } else {
                lastGreater1Ctx = lastSubblockGreater1Ctx;

                if (lastGreater1Ctx > 0) {
                    if (lastInvocationCoeffAbsLevelGreater1Flag[0]) {
                        lastGreater1Ctx = 0;
                    } else {
                        lastGreater1Ctx++;
                    }
                }
            }

            if (lastGreater1Ctx == 0) {
                ctxSet++;
            }

            greater1Ctx = 1;
        } else {
            ctxSet = lastInvocationCtxSet[0];

            greater1Ctx = lastInvocationGreater1Ctx[0];

            if (greater1Ctx > 0) {
                if (lastInvocationCoeffAbsLevelGreater1Flag[0]) {
                    greater1Ctx = 0;
                } else {
                    greater1Ctx++;
                }
            }
        }

        int ctxIdxInc = (ctxSet * 4) + Math.min(3, greater1Ctx);

        if (cIdx > 0) {
            ctxIdxInc += 16;
        }

        ContextVariable[] model = contextTable[CabacType.coeffAbsLevelGreater1Flag.ordinal()];
        boolean value = decodeGeneralForBinaryDecision(model[ctxIdxInc]) == 1;

        lastInvocationGreater1Ctx[0] = greater1Ctx;
        lastInvocationCoeffAbsLevelGreater1Flag[0] = value;
        lastInvocationCtxSet[0] = ctxSet;

        return value;
    }

    final boolean readCoeffAbsLevelGreater2Flag(int cIdx, int ctxSet) {
        // FL cMax = 1
        // bin 0: 0..5 (clause 9.3.4.2.7)
        ContextVariable[] model = contextTable[CabacType.coeffAbsLevelGreater2Flag.ordinal()];
        int index = ctxSet + (cIdx > 0 ? 4 : 0);
        return decodeGeneralForBinaryDecision(model[index]) == 1;
    }

    final int readCoeffAbsLevelRemaining(int cRiceParam) {
        // 9.3.3.11 current sub-block scan index i, baseLevel
        // all bins: bypass

        int prefix = -1;

        while (prefix <= 64) {
            prefix++;

            if (decodeBypass() == 0) {
                break;
            }
        }

        if (prefix > 64) // 8x8 block
        {
            throw new IllegalStateException("Parser logic error!");
        }

        if (prefix <= 3) {
            return (prefix << cRiceParam) + decodeFixedLengthBypass(cRiceParam);
        }

        return (((1 << (prefix - 3)) + 3 - 1) << cRiceParam) + decodeFixedLengthBypass(prefix - 3 + cRiceParam);
    }

    final boolean readCoeffSignFlag() {
        // FL cMax = 1
        // bin 0: bypass
        return decodeBypass() == 1;
    }

    // 9.3.3.5 the fixed-length(FL) binarization process with bypass only
    private int decodeFixedLengthBypass(int nBits) {
        int value = 0;

        while (nBits > 0) {
            nBits--;
            value <<= 1;
            value |= decodeBypass();
        }

        return value;
    }

    // 9.3.3.3 k-th order Exp-Golomb binarization process with bypass only
    private int decodeExpGolombBypass(int k) {
        int value = 0;
        int i = k;

        while (decodeBypass() != 0) {
            value += 1 << i;
            i++;

            if (i == k + 32) {
                throw new IllegalStateException("Parser logic error!");
            }
        }

        int suffix = decodeFixedLengthBypass(i);
        return value + suffix;
    }

    // 9.3.4.3.2 Arithmetic decoding process for a binary decision
    // ContextVariable[] model = contextVariables[(int)cabacType];
    private int decodeGeneralForBinaryDecision(ContextVariable model) {
        // Inputs to this process are the variables ctxTable, ctxIdx, ivlCurrRange and ivlOffset.
        // Outputs of this process are the decoded value binVal and the updated variables ivlCurrRange and ivlOffset

        int qRangeIdx = (ivlCurrRange >> 6) & 3;
        int ivlLpsRange = rangeTabLps[model.pStateIndex][qRangeIdx] & 0xFF;
        ivlCurrRange -= ivlLpsRange;

        boolean binVal;

        if (ivlOffset >= ivlCurrRange) {
            // LPS
            binVal = !model.valMps;
            ivlOffset -= ivlCurrRange;
            ivlCurrRange = ivlLpsRange;

            if (model.pStateIndex == 0) {
                model.valMps = !model.valMps;
            }

            model.pStateIndex = transIdxLps[model.pStateIndex] & 0xFF;
        } else {
            // MPS
            binVal = model.valMps;

            model.pStateIndex = transIdxMps[model.pStateIndex] & 0xFF;
        }

        renormalization();

        return binVal ? 1 : 0;
    }

    // 9.3.4.3.3 Renormalization process in the arithmetic decoding engine
    private void renormalization() {
        while (ivlCurrRange < 256) {
            ivlCurrRange <<= 1;
            ivlOffset <<= 1;
            ivlOffset |= stream.read(1);
        }
    }

    // 9.3.4.3.4 Bypass decoding process for binary decisions
    private int decodeBypass() {
        ivlOffset <<= 1;
        ivlOffset |= stream.read(1);

        if (ivlOffset >= ivlCurrRange) {
            ivlOffset -= ivlCurrRange;
            return 1;
        } else {
            return 0;
        }
        // returns binVal
    }

    // 9.3.4.3.5 Decoding process for binary decisions before termination
    private int decodeBeforeTermination() {
        ivlCurrRange -= 2;

        if (ivlOffset >= ivlCurrRange) {
            return 1;
        } else {
            renormalization();
            return 0;
        }
        // returns binVal
    }

    // 9.3.4.2.2 Derivation process of ctxInc using left and above syntax elements
    private int getContextIndexFromNeighborState(int x0, int y0, HeicPicture picture, int cqtDepth) {

        boolean availableL = picture.checkZScanAvailability(x0, y0, x0 - 1, y0);
        boolean availableA = picture.checkZScanAvailability(x0, y0, x0, y0 - 1);

        boolean condL = x0 > 0 && picture.ctDepthByPosition[x0 - 1][y0] > cqtDepth;
        boolean condA = y0 > 0 && picture.ctDepthByPosition[x0][y0 - 1] > cqtDepth;

        return (condL && availableL ? 1 : 0) + (condA && availableA ? 1 : 0);
    }

    public final void syncTables() {
        for (int cabacType = 0; cabacType < 40; cabacType++) {
            contextTableSync[cabacType] = new ContextVariable[contextTable[cabacType].length];

            for (int i = 0; i < contextTable[cabacType].length; i++) {
                contextTableSync[cabacType][i] = new ContextVariable();
                contextTableSync[cabacType][i].pStateIndex = contextTable[cabacType][i].pStateIndex;
                contextTableSync[cabacType][i].valMps = contextTable[cabacType][i].valMps;
            }
        }

        System.arraycopy(statCoeff, 0, tableStatCoeffSync, 0, 4);
    }

    public final void restoreSyncedTables() {
        for (int cabacType = 0; cabacType < 40; cabacType++) {
            contextTable[cabacType] = new ContextVariable[contextTableSync[cabacType].length];

            for (int i = 0; i < contextTableSync[cabacType].length; i++) {
                contextTable[cabacType][i] = new ContextVariable();
                contextTable[cabacType][i].pStateIndex = contextTableSync[cabacType][i].pStateIndex;
                contextTable[cabacType][i].valMps = contextTableSync[cabacType][i].valMps;
            }
        }

        System.arraycopy(tableStatCoeffSync, 0, statCoeff, 0, 4);
    }

    // 9.3.2.6 Initialization process for the arithmetic decoding engine
    public final void initialization(SliceSegmentHeader sliceHeader) {

        // The bitstream shall not contain data that result in a value of ivlOffset being equal to 510 or 511.
        ivlCurrRange = 510;
        ivlOffset = stream.read(9);

        int sliceQpY = sliceHeader.getSliceQPY();
        int initType = sliceHeader.getInitType();

        initializationOfContextVariables(sliceQpY, initType);

        statCoeff[0] = 0;
        statCoeff[1] = 0;
        statCoeff[2] = 0;
        statCoeff[3] = 0;

        initializationOfPalettePredictorEntries(sliceHeader);
    }

    public final void resetStreamState() {
        ivlCurrRange = 510;
        ivlOffset = stream.read(9);
    }

    // LPS = Least Probable Symbol
    // MPS = Most Probable Symbol

    private void initializationOfPalettePredictorEntries(SliceSegmentHeader sliceHeader) {
        // Outputs of this process are the initialized palette predictor variables
        // PredictorPaletteSize and PredictorPaletteEntries

        PicParameterSetRbsp pps = sliceHeader.pps;
        SeqParameterSetRbsp sps = pps.sps;

        byte numComps = ((int) sps.getChromaArrayType() == 0) ? (byte) 1 : (byte) 3;

        boolean tmp0 = (pps.ppsSccExt != null && pps.ppsSccExt.ppsPalettePredictorInitializersPresentFlag);

        if (tmp0) {
            predictorPaletteSize = pps.ppsSccExt.ppsNumPalettePredictorInitializers;

            //            PredictorPaletteEntries = new /*UInt16*/int[numComps & 0xFF][(int) (PredictorPaletteSize &
            // 0xFFFFFFFFL)];
            //
            //            for (int comp = 0; comp < (numComps & 0xFF); comp++)
            //                for (int i = 0; i < (PredictorPaletteSize & 0xFFFFFFFFL); i++)
            //                    PredictorPaletteEntries[comp][i] =
            // pps.pps_scc_ext.pps_palette_predictor_initializer[comp][i];
        } else if ((sps.spsSccExt != null && sps.spsSccExt.spsPalettePredictorInitializersPresentFlag)) {
            predictorPaletteSize =
                    ((sps.spsSccExt.spsNumPalettePredictorInitializersMinus1 & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL;

            //            PredictorPaletteEntries = new /*UInt16*/int[numComps & 0xFF][(int) (PredictorPaletteSize &
            // 0xFFFFFFFFL)];
            //
            //            for (int comp = 0; comp < (numComps & 0xFF); comp++)
            //                for (int i = 0; i < (PredictorPaletteSize & 0xFFFFFFFFL); i++)
            //                    PredictorPaletteEntries[comp][i] =
            // sps.sps_scc_ext.sps_palette_predictor_initializer[comp][i];
        } else {
            predictorPaletteSize = 0;
            //            PredictorPaletteEntries = new /*UInt16*/int[numComps & 0xFF][0];
        }
    }

    private void initializationOfContextVariables(int sliceQpY, int initType) {
        final CabacType[] cabacTypes = CabacType.getValues();
        for (int cabacType = 0; cabacType < 40; cabacType++) {
            byte[] initValue = getinitValueByType(cabacTypes[cabacType], initType);
            contextTable[cabacType] = new ContextVariable[initValue.length];

            for (int i = 0; i < initValue.length; i++) {
                contextTable[cabacType][i] = initContextVariable(sliceQpY, initValue[i]);
            }
        }
    }

    private ContextVariable initContextVariable(int sliceQpY, byte initValue) {
        // Outputs of this process are the initialized CABAC context variables indexed by ctxTable and ctxIdx.
        //
        // Table 9-5 to Table 9-37 contain the values of the 8 bit variable init_value used in the initialization
        // of context variables that are assigned to all syntax elements in clauses 7.3.8.1 through 7.3.8.12,
        // except end_of_slice_segment_flag, end_of_subset_one_bit and pcm_flag.

        byte slopeIdx = (byte) ((initValue & 0xFF) >> 4); // 4 bit

        byte offsetIdx = (byte) ((initValue & 0xFF) & 15); // 4 bit
        byte m = (byte) ((slopeIdx & 0xFF) * 5 - 45);
        byte n = (byte) (((offsetIdx & 0xFF) << 3) - 16);

        int preCtxState = MathExtra.clip3(1, 126, ((m * MathExtra.clip3(0, 51, sliceQpY)) >> 4) + n);

        // the value of the most probable symbol as further described in clause 9.3.4.3
        boolean valMps = preCtxState > 63;
        // a probability state index

        byte pStateIdx = (byte) (valMps ? (preCtxState - 64) : (63 - preCtxState));
        ContextVariable tmp0 = new ContextVariable();
        tmp0.pStateIndex = pStateIdx & 0xFF;
        tmp0.valMps = valMps; // 4 bit

        return tmp0;
    }

    private byte[] getinitValueByType(CabacType type, int initType) {
        if (initType == 0) {
            if (type == CabacType.predModeFlag
                    || type == CabacType.rqtRootCbf
                    || type == CabacType.mergeFlag
                    || type == CabacType.mergeIdx
                    || type == CabacType.mvpFlag
                    || type == CabacType.absMvdGreater0Flag
                    || type == CabacType.absMvdGreater1Flag
                    || type == CabacType.cuSkipFlag
                    || type == CabacType.InterPredIdc
                    || type == CabacType.refIdx
                    || type == CabacType.explicitRdpcmFlag
                    || type == CabacType.explicitRdpcmDirFlag) {
                return new byte[0];
            }
        }

        switch (type) {
            // variables

            case saoMergeFlag:
                return new byte[] {initValueSaoMergeFlag[initType]};
            case saoTypeIdx:
                return new byte[] {initValueSaoTypeIdxFlag[initType]};
            case cuTransquantBypassFlag:
                return new byte[] {initValueCuTransquantBypassFlag[initType]};
            case predModeFlag:
                return new byte[] {initValuePredModeFlag[initType - 1]};
            case prevIntraLumaPredFlag:
                return new byte[] {initValuePrevIntraLumaPredFlag[initType]};
            case intraChromaPredMode:
                return new byte[] {initValueIntraChromaPredMode[initType]};
            case rqtRootCbf:
                return new byte[] {initValueRqtRootCbf[initType - 1]};
            case mergeFlag:
                return new byte[] {initValueMergeFlag[initType - 1]};
            case mergeIdx:
                return new byte[] {initValueMergeIdx[initType - 1]};
            case mvpFlag:
                return new byte[] {initValueMvpFlag[initType - 1]};
            case absMvdGreater0Flag:
                return new byte[] {initValueAbsMvdGreaterFlag[(initType - 1) * 2]};
            case absMvdGreater1Flag:
                return new byte[] {initValueAbsMvdGreaterFlag[(initType - 1) * 2 + 1]};
            case transformSkipFlag:
            case explicitRdpcmFlag:
            case explicitRdpcmDirFlag:
                return new byte[] {(byte) 139, (byte) 139};
            case paletteModeFlag:
            case tuResidualActFlag:
            case copyAbovePaletteIndicesFlag:
            case copyAboveIndicesForFinalRunFlag:
            case paletteTransposeFlag:
            case cuChromaQpOffsetFlag:
            case cuChromaQpOffsetIdx:
                return new byte[] {(byte) 154};

            // arrays

            case splitCuFlag:
                return initValueSplitCuFlag[initType];
            case cuSkipFlag:
                return initValueCuSkipFlag[initType - 1];
            case partMode:
                return initValuePartMode[initType];
            case InterPredIdc:
                return initValueInterPredIdc[initType - 1];
            case refIdx:
                return initValueRefIdx[initType - 1];
            case splitTransformFlag:
                return initValueSplitTransformFlag[initType];
            case cbfLuma:
                return initValueCbfLuma[initType];
            case cbfChroma:
                return initValueCbfChroma[initType];
            case lastSigCoeffXPrefix:
            case lastSigCoeffYPrefix:
                return initValueLastSigCoeffPrefix[initType];
            case codedSubBlockFlag:
                return initValueCodedSubBlockFlag[initType];
            case sigCoeffFlag:
                return initValueSigCoeffFlag[initType];
            case coeffAbsLevelGreater1Flag:
                return initValueCoeffAbsLevelGreater1Flag[initType];
            case coeffAbsLevelGreater2Flag:
                return initValueCoeffAbsLevelGreater2Flag[initType];
            case paletteRunPrefix:
            case log2ResScaleAbsPlus1:
                return new byte[] {
                    (byte) 154, (byte) 154, (byte) 154, (byte) 154, (byte) 154, (byte) 154, (byte) 154, (byte) 154
                };
            case cuQpDeltaAbs:
            case resScaleSignFlag:
                return new byte[] {(byte) 154, (byte) 154};
            default:
                throw new IndexOutOfBoundsException("type");
        }
    }
}
