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
 * The scaling and transformation process (8.6.2): from the coefficients residual coding left in the
 * {@link ResidualBuffers} to the residual samples of a transform block of an intra coding unit.
 */
final class InverseTransform {
    /** The residual is not modified along a direction (8.6.8). */
    static final int NO_RESIDUAL_DPCM = -1;

    // 8.6.4.2: transMatrix of the 4x4 DST, and the 32x32 DCT whose every (32 / nTbS)th row is the nTbS one
    private static final int[][] DST_MATRIX = {
        {29, 55, 74, 84},
        {74, 74, 0, -74},
        {84, -29, -74, 55},
        {55, -84, 74, -29}
    };
    private static final int[][] DCT_MATRIX = {
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
    // 8.6.3: levelScale[qP % 6]
    private static final int[] LEVEL_SCALE = {40, 45, 51, 57, 64, 72};

    private InverseTransform() {}

    /**
     * Computes the residual of the {@code nTbS} block of component {@code cIdx}.
     *
     * @param qP the quantization parameter of the component, Qp'Y, Qp'Cb or Qp'Cr
     * @param residualDpcmDirection mDir of the directional residual modification (8.6.8), 0 horizontal, 1 vertical,
     *     or {@link #NO_RESIDUAL_DPCM}
     * @return r[x * nTbS + y], in a buffer the next block overwrites
     */
    static int[] residual(
            HeicPicture picture,
            ResidualBuffers residualBuffers,
            int cIdx,
            int nTbS,
            int qP,
            boolean cuTransquantBypassFlag,
            boolean transformSkipFlag,
            int residualDpcmDirection) {
        SeqParameterSetRbsp sps = picture.sps;
        int[] residual = residualBuffers.residual;
        boolean rotateCoeffs = sps.spsRangeExt.transformSkipRotationEnabledFlag && nTbS == 4;

        if (cuTransquantBypassFlag) {
            for (int x = 0; x < nTbS; x++) {
                for (int y = 0; y < nTbS; y++) {
                    residual[x * nTbS + y] = rotateCoeffs
                            ? residualBuffers.coefficient(nTbS - x - 1, nTbS - y - 1)
                            : residualBuffers.coefficient(x, y);
                }
            }
        } else {
            int[] scaled = scaleCoefficients(picture, residualBuffers, cIdx, nTbS, qP, transformSkipFlag);
            if (transformSkipFlag) {
                // 8.6.4.2: the residual modification process for blocks using a transform bypass
                int tsShift = 5 + Integer.numberOfTrailingZeros(nTbS);
                for (int x = 0; x < nTbS; x++) {
                    for (int y = 0; y < nTbS; y++) {
                        int scaledIndex = rotateCoeffs ? (nTbS - x - 1) * nTbS + (nTbS - y - 1) : x * nTbS + y;
                        residual[x * nTbS + y] = scaled[scaledIndex] << tsShift;
                    }
                }
            } else {
                transform(sps, residualBuffers, cIdx, nTbS);
            }

            int bitDepth = cIdx == 0 ? sps.getBitDepthY() : sps.getBitDepthC();
            int bdShift = Math.max(20 - bitDepth, sps.spsRangeExt.extendedPrecisionProcessingFlag ? 11 : 0);
            int offset = 1 << (bdShift - 1);
            for (int index = 0; index < nTbS * nTbS; index++) {
                residual[index] = (residual[index] + offset) >> bdShift;
            }
        }

        if (residualDpcmDirection != NO_RESIDUAL_DPCM) {
            accumulate(residual, nTbS, residualDpcmDirection);
        }
        return residual;
    }

    // 8.6.3 Scaling process for transform coefficients
    private static int[] scaleCoefficients(
            HeicPicture picture,
            ResidualBuffers residualBuffers,
            int cIdx,
            int nTbS,
            int qP,
            boolean transformSkipFlag) {
        SeqParameterSetRbsp sps = picture.sps;
        int bitDepth = cIdx == 0 ? sps.getBitDepthY() : sps.getBitDepthC();
        int log2TransformRange = sps.spsRangeExt.extendedPrecisionProcessingFlag ? Math.max(15, bitDepth + 6) : 15;
        int log2BlockSize = Integer.numberOfTrailingZeros(nTbS);
        int bdShift = bitDepth + log2BlockSize + 10 - log2TransformRange;
        int coeffMin = cIdx == 0 ? sps.getCoeffMinY() : sps.getCoeffMinC();
        int coeffMax = cIdx == 0 ? sps.getCoeffMaxY() : sps.getCoeffMaxC();
        int roundingOffset = 1 << (bdShift - 1);
        int levelScale = LEVEL_SCALE[qP % 6] << (qP / 6);

        // matrixId is cIdx for an intra coding unit
        int[][] scalingFactors = picture.scalingFactor[log2BlockSize - 2][cIdx];
        boolean flatScaling = !sps.scalingListEnabledFlag || (transformSkipFlag && nTbS > 4);

        int[] scaled = residualBuffers.scaled;
        for (int x = 0; x < nTbS; x++) {
            int[] scalingFactorColumn = scalingFactors[x];
            for (int y = 0; y < nTbS; y++) {
                int m = flatScaling ? 16 : scalingFactorColumn[y];
                int coefficient = residualBuffers.coefficient(x, y);
                scaled[x * nTbS + y] =
                        MathExtra.clip3(coeffMin, coeffMax, (coefficient * m * levelScale + roundingOffset) >> bdShift);
            }
        }
        return scaled;
    }

    // 8.6.4.2 Transformation process: columns, intermediate clipping, then rows. Only the coefficients up to the last
    // non-zero one in each direction can contribute, so the sums stop there.
    private static void transform(SeqParameterSetRbsp sps, ResidualBuffers residualBuffers, int cIdx, int nTbS) {
        int coeffMin = cIdx == 0 ? sps.getCoeffMinY() : sps.getCoeffMinC();
        int coeffMax = cIdx == 0 ? sps.getCoeffMaxY() : sps.getCoeffMaxC();
        // trType 1, the DST, for the 4x4 luma blocks of an intra coding unit
        boolean discreteSineTransform = nTbS == 4 && cIdx == 0;
        int[][] transMatrix = discreteSineTransform ? DST_MATRIX : DCT_MATRIX;
        int matrixRowStep = discreteSineTransform ? 1 : MAX_TRANSFORM_SIZE / nTbS;
        int lastColumn = residualBuffers.lastNonZeroX();
        int lastRow = residualBuffers.lastNonZeroY();

        int[] scaled = residualBuffers.scaled;
        int[] columnTransformed = residualBuffers.columnTransformed;
        int[] clipped = residualBuffers.clipped;
        int[] residual = residualBuffers.residual;

        // columns: each non-zero coefficient adds its scaled basis function, a contiguous loop the JIT vectorises
        Arrays.fill(clipped, 0, nTbS * nTbS, 0);
        for (int x = 0; x <= lastColumn; x++) {
            int columnStart = x * nTbS;
            Arrays.fill(columnTransformed, columnStart, columnStart + nTbS, 0);
            for (int j = 0; j <= lastRow; j++) {
                int coefficient = scaled[columnStart + j];
                if (coefficient != 0) {
                    int[] basisFunction = transMatrix[j * matrixRowStep];
                    for (int i = 0; i < nTbS; i++) {
                        columnTransformed[columnStart + i] += basisFunction[i] * coefficient;
                    }
                }
            }
            for (int i = 0; i < nTbS; i++) {
                clipped[columnStart + i] =
                        MathExtra.clip3(coeffMin, coeffMax, (columnTransformed[columnStart + i] + 64) >> 7);
            }
        }

        // rows, the same way, through a contiguous row buffer
        int[] row = residualBuffers.row;
        for (int y = 0; y < nTbS; y++) {
            Arrays.fill(row, 0, nTbS, 0);
            for (int j = 0; j <= lastColumn; j++) {
                int coefficient = clipped[j * nTbS + y];
                if (coefficient != 0) {
                    int[] basisFunction = transMatrix[j * matrixRowStep];
                    for (int i = 0; i < nTbS; i++) {
                        row[i] += basisFunction[i] * coefficient;
                    }
                }
            }
            for (int i = 0; i < nTbS; i++) {
                residual[i * nTbS + y] = row[i];
            }
        }
    }

    // 8.6.8 Directional residual modification process for blocks using a transform bypass: each sample adds the one
    // before it, along x for mDir 0, along y for mDir 1
    private static void accumulate(int[] residual, int nTbS, int mDir) {
        if (mDir == 0) {
            for (int x = 1; x < nTbS; x++) {
                for (int y = 0; y < nTbS; y++) {
                    residual[x * nTbS + y] += residual[(x - 1) * nTbS + y];
                }
            }
        } else {
            for (int x = 0; x < nTbS; x++) {
                for (int y = 1; y < nTbS; y++) {
                    residual[x * nTbS + y] += residual[x * nTbS + y - 1];
                }
            }
        }
    }
}
