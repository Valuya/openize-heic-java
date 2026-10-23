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
 * Scaling list data (7.3.4) and the scaling factors derived from it (7.4.5).
 *
 * <p>
 * Table 7-4 – Specification of matrixId according to sizeId, prediction mode and colour component:
 * matrixId = cIdx for MODE_INTRA, cIdx + 3 for MODE_INTER; sizeId 0..3 for 4x4..32x32 blocks.
 * </p>
 */
class ScalingListData {
    private static final int SIZE_ID_COUNT = 4;
    private static final int MATRIX_ID_COUNT = 6;
    // scaling_list_dc_coef_minus8 and scaling_list_delta_coef (7.4.5)
    private static final int MIN_DC_COEF_MINUS8 = -7;
    private static final int MAX_DC_COEF_MINUS8 = 247;
    private static final int MIN_DELTA_COEF = -128;
    private static final int MAX_DELTA_COEF = 127;

    // Table 7-5: every 4x4 default value is 16
    private static final int[] DEFAULT_4X4 = {16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16};

    // Table 7-6 – default values of ScalingList[1..3][0..2][i] (intra) and [1..3][3..5][i] (inter)
    private static final int[] DEFAULT_8X8_INTRA = {
        16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 17, 16, 17, 16, 17, 18,
        17, 18, 18, 17, 18, 21, 19, 20, 21, 20, 19, 21, 24, 22, 22, 24,
        24, 22, 22, 24, 25, 25, 27, 30, 27, 25, 25, 29, 31, 35, 35, 31,
        29, 36, 41, 44, 41, 36, 47, 54, 54, 47, 65, 70, 65, 88, 88, 115
    };

    private static final int[] DEFAULT_8X8_INTER = {
        16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 17, 17, 17, 17, 17, 18,
        18, 18, 18, 18, 18, 20, 20, 20, 20, 20, 20, 20, 24, 24, 24, 24,
        24, 24, 24, 24, 25, 25, 25, 25, 25, 25, 25, 28, 28, 28, 28, 28,
        28, 33, 33, 33, 33, 33, 41, 41, 41, 41, 54, 54, 54, 71, 71, 91
    };

    // Used when scaling_list_enabled_flag is set without any scaling_list_data in the SPS or PPS
    static final ScalingListData DEFAULT = new ScalingListData();

    // ScalingList[sizeId][matrixId][i], in up-right diagonal scan order; never modified, so lists can be shared
    private final int[][][] scalingList = new int[SIZE_ID_COUNT][MATRIX_ID_COUNT][];
    // scaling_list_dc_coef_minus8[sizeId - 2][matrixId] + 8: the DC factor of 16x16 and 32x32 blocks
    private final int[][] dcCoef = new int[2][MATRIX_ID_COUNT];
    // ScalingFactor[sizeId][matrixId][x][y] (7.4.5)
    private final int[][][][] scalingFactor;

    private ScalingListData() {
        for (int sizeId = 0; sizeId < SIZE_ID_COUNT; sizeId++) {
            for (int matrixId = 0; matrixId < MATRIX_ID_COUNT; matrixId++) {
                setDefault(sizeId, matrixId);
            }
        }
        scalingFactor = createScalingFactor();
    }

    /**
     * Reads scaling_list_data( ) (7.3.4).
     */
    ScalingListData(BitStreamWithNalSupport stream) {
        for (int sizeId = 0; sizeId < SIZE_ID_COUNT; sizeId++) {
            // 32x32 lists are only coded for luma, matrixId 0 and 3
            int matrixIdStep = sizeId == 3 ? 3 : 1;
            for (int matrixId = 0; matrixId < MATRIX_ID_COUNT; matrixId += matrixIdStep) {
                boolean scalingListPredModeFlag = stream.readFlag();
                if (scalingListPredModeFlag) {
                    readList(stream, sizeId, matrixId);
                } else {
                    // 0 for the default list, otherwise the distance to an earlier list of the same size
                    int scalingListPredMatrixIdDelta = stream.readUevAtMost(matrixId / matrixIdStep);
                    if (scalingListPredMatrixIdDelta == 0) {
                        setDefault(sizeId, matrixId);
                    } else {
                        copyList(sizeId, matrixId - scalingListPredMatrixIdDelta * matrixIdStep, matrixId);
                    }
                }
            }
        }
        scalingFactor = createScalingFactor();
    }

    private void readList(BitStreamWithNalSupport stream, int sizeId, int matrixId) {
        int nextCoef = 8;
        if (sizeId > 1) {
            nextCoef =
                    readSevInRange(stream, "scaling_list_dc_coef_minus8", MIN_DC_COEF_MINUS8, MAX_DC_COEF_MINUS8) + 8;
            dcCoef[sizeId - 2][matrixId] = nextCoef;
        }

        int coefNum = Math.min(64, 1 << (4 + (sizeId << 1)));
        int[] list = new int[coefNum];
        for (int i = 0; i < coefNum; i++) {
            int scalingListDeltaCoef =
                    readSevInRange(stream, "scaling_list_delta_coef", MIN_DELTA_COEF, MAX_DELTA_COEF);
            nextCoef = (nextCoef + scalingListDeltaCoef + 256) % 256;
            list[i] = nextCoef;
        }
        scalingList[sizeId][matrixId] = list;
    }

    private static int readSevInRange(BitStreamWithNalSupport stream, String elementName, int minimum, int maximum) {
        int value = stream.readSev();
        if (value < minimum || value > maximum) {
            throw new HeicFormatException("%s of %d, outside %d..%d".formatted(elementName, value, minimum, maximum));
        }
        return value;
    }

    private void copyList(int sizeId, int refMatrixId, int matrixId) {
        scalingList[sizeId][matrixId] = scalingList[sizeId][refMatrixId];
        if (sizeId > 1) {
            dcCoef[sizeId - 2][matrixId] = dcCoef[sizeId - 2][refMatrixId];
        }
    }

    private void setDefault(int sizeId, int matrixId) {
        if (sizeId == 0) {
            scalingList[sizeId][matrixId] = DEFAULT_4X4;
        } else {
            scalingList[sizeId][matrixId] = matrixId < 3 ? DEFAULT_8X8_INTRA : DEFAULT_8X8_INTER;
        }
        if (sizeId > 1) {
            dcCoef[sizeId - 2][matrixId] = 16;
        }
    }

    /**
     * Returns ScalingFactor[sizeId][matrixId][x][y] (7.4.5).
     */
    int[][][][] getScalingFactor() {
        return scalingFactor;
    }

    private int[][][][] createScalingFactor() {
        int[][][][] factor = new int[SIZE_ID_COUNT][MATRIX_ID_COUNT][][];
        byte[][][][] scanOrder = Scans.getScanOrder();
        byte[][] diagonal4x4 = scanOrder[2][0];
        byte[][] diagonal8x8 = scanOrder[3][0];

        for (int matrixId = 0; matrixId < MATRIX_ID_COUNT; matrixId++) {
            factor[0][matrixId] = new int[4][4];
            for (int i = 0; i < 16; i++) {
                factor[0][matrixId][diagonal4x4[i][0]][diagonal4x4[i][1]] = scalingList[0][matrixId][i];
            }

            factor[1][matrixId] = new int[8][8];
            factor[2][matrixId] = new int[16][16];
            for (int i = 0; i < 64; i++) {
                int x = diagonal8x8[i][0];
                int y = diagonal8x8[i][1];
                factor[1][matrixId][x][y] = scalingList[1][matrixId][i];
                fillSquare(factor[2][matrixId], x * 2, y * 2, 2, scalingList[2][matrixId][i]);
            }
            factor[2][matrixId][0][0] = dcCoef[0][matrixId];

            // 32x32 chroma matrices are only coded for ChromaArrayType 3, derived from the 16x16 lists
            boolean coded32x32 = matrixId == 0 || matrixId == 3;
            int[] list32x32 = coded32x32 ? scalingList[3][matrixId] : scalingList[2][matrixId];
            factor[3][matrixId] = new int[32][32];
            for (int i = 0; i < 64; i++) {
                fillSquare(factor[3][matrixId], diagonal8x8[i][0] * 4, diagonal8x8[i][1] * 4, 4, list32x32[i]);
            }
            factor[3][matrixId][0][0] = coded32x32 ? dcCoef[1][matrixId] : dcCoef[0][matrixId];
        }
        return factor;
    }

    // An 8x8 list entry scales a square of a 16x16 or 32x32 matrix
    private static void fillSquare(int[][] matrix, int x0, int y0, int size, int value) {
        for (int y = y0; y < y0 + size; y++) {
            for (int x = x0; x < x0 + size; x++) {
                matrix[x][y] = value;
            }
        }
    }
}
