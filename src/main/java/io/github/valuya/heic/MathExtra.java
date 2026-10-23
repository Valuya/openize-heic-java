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
 * The mathematical functions of H.265 clause 5.8.
 */
final class MathExtra {
    private MathExtra() {}

    /**
     * Clip3( x, y, z ): {@code value} limited to {@code lowest..highest}.
     */
    static int clip3(int lowest, int highest, int value) {
        if (value < lowest) {
            return lowest;
        } else if (value > highest) {
            return highest;
        } else {
            return value;
        }
    }

    /**
     * Clip1Y / Clip1C: {@code value} limited to the range of a {@code bitDepth}-bit sample.
     */
    static int clipBitDepth(int value, int bitDepth) {
        return clip3(0, (1 << bitDepth) - 1, value);
    }
}
