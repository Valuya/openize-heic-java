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
 * Intra sample prediction (8.4.4.2) of a transform block from its neighbouring samples.
 */
final class IntraPrediction {
    // Table 8-5: intraPredAngle by predModeIntra
    private static final int[] INTRA_PRED_ANGLE = {
        0, 0, 32, 26, 21, 17, 13, 9, 5, 2, 0, -2, -5, -9, -13, -17, -21, -26, -32, -26, -21, -17, -13, -9, -5, -2, 0, 2,
        5, 9, 13, 17, 21, 26, 32
    };
    // Table 8-6: invAngle by predModeIntra, for the negative angles 11..25
    private static final int[] INV_ANGLE = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -4096, -1638, -910, -630, -482, -390, -315, -256, -315, -390, -482, -630, -910,
        -1638, -4096
    };
    private static final int VERTICAL = IntraPredMode.INTRA_ANGULAR26;
    private static final int HORIZONTAL = IntraPredMode.INTRA_ANGULAR10;

    private IntraPrediction() {}

    /**
     * Predicts the {@code nTbS} block of component {@code cIdx} at {@code (x0, y0)}, in samples of that component.
     *
     * @return predSamples[x][y] at [y * nTbS + x], in a buffer the next prediction overwrites
     */
    static int[] predict(
            HeicPicture picture,
            IntraPredictionBuffers buffers,
            int x0,
            int y0,
            int nTbS,
            int cIdx,
            int intraPredMode,
            boolean cuTransquantBypassFlag) {
        SeqParameterSetRbsp sps = picture.sps;
        NeighbouringSamples samples = NeighbouringSamplesGenerator.generate(picture, x0, y0, nTbS, cIdx, buffers);
        if (!sps.spsRangeExt.intraSmoothingDisabledFlag && (cIdx == 0 || sps.getChromaArrayType() == 3)) {
            samples = filterNeighbouringSamples(sps, buffers, samples, nTbS, cIdx, intraPredMode);
        }

        int bitDepth = cIdx == 0 ? sps.getBitDepthY() : sps.getBitDepthC();
        // 8.4.4.2.6: the boundary smoothing of the vertical and horizontal modes
        boolean disableIntraBoundaryFilter = sps.spsSccExt.intraBoundaryFilteringDisabledFlag
                || (sps.spsRangeExt.implicitRdpcmEnabledFlag && cuTransquantBypassFlag);
        return switch (intraPredMode) {
            case IntraPredMode.INTRA_PLANAR -> predictPlanar(buffers.predictedSamples, samples, nTbS);
            case IntraPredMode.INTRA_DC -> predictDc(buffers.predictedSamples, samples, nTbS, cIdx);
            default ->
                predictAngular(buffers, samples, nTbS, cIdx, intraPredMode, bitDepth, disableIntraBoundaryFilter);
        };
    }

    // 8.4.4.2.3 Filtering process of neighbouring samples
    private static NeighbouringSamples filterNeighbouringSamples(
            SeqParameterSetRbsp sps,
            IntraPredictionBuffers buffers,
            NeighbouringSamples p,
            int nTbS,
            int cIdx,
            int intraPredMode) {
        if (!isFiltered(intraPredMode, nTbS)) {
            return p;
        }

        NeighbouringSamples pF = buffers.filteredSamples;
        pF.prepare(nTbS);
        int last = 2 * nTbS - 1;
        int threshold = 1 << (sps.getBitDepthY() - 5);
        boolean biIntFlag = sps.strongIntraSmoothingEnabledFlag
                && cIdx == 0
                && nTbS == 32
                && Math.abs(p.get(-1, -1) + p.get(last, -1) - 2 * p.get(nTbS - 1, -1)) < threshold
                && Math.abs(p.get(-1, -1) + p.get(-1, last) - 2 * p.get(-1, nTbS - 1)) < threshold;

        if (biIntFlag) {
            // bi-linear interpolation between the corners, for a 32x32 block
            pF.set(-1, -1, p.get(-1, -1));
            pF.set(-1, 63, p.get(-1, 63));
            pF.set(63, -1, p.get(63, -1));
            for (int i = 0; i < 63; i++) {
                pF.set(-1, i, ((63 - i) * p.get(-1, -1) + (i + 1) * p.get(-1, 63) + 32) >> 6);
                pF.set(i, -1, ((63 - i) * p.get(-1, -1) + (i + 1) * p.get(63, -1) + 32) >> 6);
            }
        } else {
            // [1 2 1] smoothing, the far ends unchanged
            pF.set(-1, -1, (p.get(-1, 0) + 2 * p.get(-1, -1) + p.get(0, -1) + 2) >> 2);
            pF.set(-1, last, p.get(-1, last));
            pF.set(last, -1, p.get(last, -1));
            for (int i = 0; i < last; i++) {
                pF.set(-1, i, (p.get(-1, i + 1) + 2 * p.get(-1, i) + p.get(-1, i - 1) + 2) >> 2);
                pF.set(i, -1, (p.get(i - 1, -1) + 2 * p.get(i, -1) + p.get(i + 1, -1) + 2) >> 2);
            }
        }
        return pF;
    }

    // filterFlag: the modes farther from vertical and horizontal than intraHorVerDistThres[nTbS]
    private static boolean isFiltered(int intraPredMode, int nTbS) {
        if (intraPredMode == IntraPredMode.INTRA_DC || nTbS == 4) {
            return false;
        }
        int minDistVerHor = Math.min(Math.abs(intraPredMode - VERTICAL), Math.abs(intraPredMode - HORIZONTAL));
        return switch (nTbS) {
            case 8 -> minDistVerHor > 7;
            case 16 -> minDistVerHor > 1;
            case 32 -> minDistVerHor > 0;
            default -> false;
        };
    }

    // 8.4.4.2.4 Specification of intra prediction mode INTRA_PLANAR
    private static int[] predictPlanar(int[] predSamples, NeighbouringSamples p, int nTbS) {
        int shift = Integer.numberOfTrailingZeros(nTbS) + 1;
        int topRight = p.get(nTbS, -1);
        int bottomLeft = p.get(-1, nTbS);
        for (int y = 0; y < nTbS; y++) {
            int rowStart = y * nTbS;
            int left = p.get(-1, y);
            for (int x = 0; x < nTbS; x++) {
                predSamples[rowStart + x] = ((nTbS - 1 - x) * left
                                + (x + 1) * topRight
                                + (nTbS - 1 - y) * p.get(x, -1)
                                + (y + 1) * bottomLeft
                                + nTbS)
                        >> shift;
            }
        }
        return predSamples;
    }

    // 8.4.4.2.5 Specification of intra prediction mode INTRA_DC
    private static int[] predictDc(int[] predSamples, NeighbouringSamples p, int nTbS, int cIdx) {
        int dcVal = nTbS;
        for (int i = 0; i < nTbS; i++) {
            dcVal += p.get(i, -1) + p.get(-1, i);
        }
        dcVal >>= Integer.numberOfTrailingZeros(nTbS) + 1;

        Arrays.fill(predSamples, 0, nTbS * nTbS, dcVal);
        if (cIdx == 0 && nTbS < 32) {
            // the first row and column are smoothed towards their neighbours
            predSamples[0] = (p.get(-1, 0) + 2 * dcVal + p.get(0, -1) + 2) >> 2;
            for (int x = 1; x < nTbS; x++) {
                predSamples[x] = (p.get(x, -1) + 3 * dcVal + 2) >> 2;
            }
            for (int y = 1; y < nTbS; y++) {
                predSamples[y * nTbS] = (p.get(-1, y) + 3 * dcVal + 2) >> 2;
            }
        }
        return predSamples;
    }

    // 8.4.4.2.6 Specification of intra prediction modes INTRA_ANGULAR2..INTRA_ANGULAR34
    private static int[] predictAngular(
            IntraPredictionBuffers buffers,
            NeighbouringSamples p,
            int nTbS,
            int cIdx,
            int predModeIntra,
            int bitDepth,
            boolean disableIntraBoundaryFilter) {
        int[] predSamples = buffers.predictedSamples;
        int intraPredAngle = INTRA_PRED_ANGLE[predModeIntra];
        boolean verticalModes = predModeIntra >= 18;
        // ref[x] at reference[x + nTbS], x = -nTbS..2 * nTbS: the main row (vertical modes) or column (horizontal
        // ones), extended by projecting the other side for a negative angle
        int[] reference = buffers.angularReference;
        for (int x = 0; x <= nTbS; x++) {
            reference[x + nTbS] = verticalModes ? p.get(x - 1, -1) : p.get(-1, x - 1);
        }
        if (intraPredAngle < 0) {
            int lastProjected = (nTbS * intraPredAngle) >> 5;
            if (lastProjected < -1) {
                int invAngle = INV_ANGLE[predModeIntra];
                for (int x = lastProjected; x <= -1; x++) {
                    int projected = -1 + ((x * invAngle + 128) >> 8);
                    reference[x + nTbS] = verticalModes ? p.get(-1, projected) : p.get(projected, -1);
                }
            }
        } else {
            for (int x = nTbS + 1; x <= 2 * nTbS; x++) {
                reference[x + nTbS] = verticalModes ? p.get(x - 1, -1) : p.get(-1, x - 1);
            }
        }

        // iIdx and iFact follow the distance from the reference: y for the vertical modes, x for the horizontal ones
        if (verticalModes) {
            for (int y = 0; y < nTbS; y++) {
                int iIdx = ((y + 1) * intraPredAngle) >> 5;
                int iFact = ((y + 1) * intraPredAngle) & 31;
                interpolateLine(reference, nTbS + iIdx + 1, iFact, predSamples, y * nTbS, 1, nTbS);
            }
        } else {
            for (int x = 0; x < nTbS; x++) {
                int iIdx = ((x + 1) * intraPredAngle) >> 5;
                int iFact = ((x + 1) * intraPredAngle) & 31;
                interpolateLine(reference, nTbS + iIdx + 1, iFact, predSamples, x, nTbS, nTbS);
            }
        }

        if (cIdx == 0 && nTbS < 32 && !disableIntraBoundaryFilter) {
            if (predModeIntra == VERTICAL) {
                for (int y = 0; y < nTbS; y++) {
                    predSamples[y * nTbS] =
                            MathExtra.clipBitDepth(p.get(0, -1) + ((p.get(-1, y) - p.get(-1, -1)) >> 1), bitDepth);
                }
            } else if (predModeIntra == HORIZONTAL) {
                for (int x = 0; x < nTbS; x++) {
                    predSamples[x] =
                            MathExtra.clipBitDepth(p.get(-1, 0) + ((p.get(x, -1) - p.get(-1, -1)) >> 1), bitDepth);
                }
            }
        }
        return predSamples;
    }

    // count samples interpolated between reference[index + i] and the next one, at iFact 32ths, to
    // predSamples[start + i * step]
    private static void interpolateLine(
            int[] reference, int index, int iFact, int[] predSamples, int start, int step, int count) {
        if (iFact == 0) {
            for (int i = 0; i < count; i++) {
                predSamples[start + i * step] = reference[index + i];
            }
        } else {
            for (int i = 0; i < count; i++) {
                predSamples[start + i * step] =
                        ((32 - iFact) * reference[index + i] + iFact * reference[index + i + 1] + 16) >> 5;
            }
        }
    }
}
