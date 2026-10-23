/*
 * Openize.HEIC
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of Openize.HEIC.
 *
 * Openize.HEIC is available under Openize license, which is
 * available along with Openize.HEIC sources.
 */

package openize;

public final class MathUtils {
    private MathUtils() {}

    /**
     * The logarithm of {@code value} in base {@code base}; NaN in base 1.
     */
    public static double log(double value, double base) {
        if (base == 1.0) {
            return Double.NaN;
        }
        double result = Math.log(value) / Math.log(base);
        // no negative zero
        return result == 0.0 ? 0.0 : result;
    }

    /**
     * The smallest integer value not less than {@code value}, as {@link Math#ceil} but never negative zero.
     */
    public static double ceiling(double value) {
        if (Double.isInfinite(value) || Double.isNaN(value)) {
            return value;
        }
        double result = Math.floor(value);
        return result == value ? result : result + 1;
    }

    /**
     * {@code value} truncated to an int, or {@link Integer#MIN_VALUE} when it is NaN or outside the int range.
     */
    public static int f64S32(double value) {
        return value >= -2.147483647E9 && value <= 2.147483647E9 ? (int) value : Integer.MIN_VALUE;
    }
}
