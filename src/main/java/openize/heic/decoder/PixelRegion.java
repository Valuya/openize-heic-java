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

/**
 * A rectangle of pixels of an image, in the coordinates of the image as displayed: after its crop, rotation and
 * mirroring.
 *
 * @param left the first column
 * @param top the first row
 * @param width the number of columns, at least 1
 * @param height the number of rows, at least 1
 */
public record PixelRegion(int left, int top, int width, int height) {
    public PixelRegion {
        // right() and bottom() must not overflow
        if (left < 0
                || top < 0
                || width < 1
                || height < 1
                || (long) left + width > Integer.MAX_VALUE
                || (long) top + height > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Not a region of pixels: %d,%d %dx%d".formatted(left, top, width, height));
        }
    }

    /**
     * The whole of an image of {@code width} by {@code height} pixels.
     */
    public static PixelRegion ofSize(int width, int height) {
        return new PixelRegion(0, 0, width, height);
    }

    /** The column after the last one. */
    public int right() {
        return left + width;
    }

    /** The row after the last one. */
    public int bottom() {
        return top + height;
    }

    int pixelCount() {
        return width * height;
    }
}
