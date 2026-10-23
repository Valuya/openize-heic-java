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
 * How images are decoded.
 *
 * @param maxPixelCount the largest picture decoded, in pixels: checked against each coded picture before its buffers
 *     are allocated, and against each requested image or region before its pixel array is. It bounds the memory a
 *     small, possibly hostile, file can make the decoder allocate.
 * @param parallel whether the tiles of a grid image decode in parallel, on the common fork-join pool
 */
public record HeicDecodingOptions(long maxPixelCount, boolean parallel) {
    /**
     * 16384 x 16384 pixels, decoded in parallel.
     */
    public static final HeicDecodingOptions DEFAULT = new HeicDecodingOptions(16384L * 16384L, true);

    public HeicDecodingOptions {
        if (maxPixelCount <= 0) {
            throw new IllegalArgumentException("maxPixelCount must be positive: " + maxPixelCount);
        }
    }

    public HeicDecodingOptions withMaxPixelCount(long newMaxPixelCount) {
        return new HeicDecodingOptions(newMaxPixelCount, parallel);
    }

    public HeicDecodingOptions withParallel(boolean newParallel) {
        return new HeicDecodingOptions(maxPixelCount, newParallel);
    }

    void checkPixelCount(String what, long width, long height) {
        if (width <= 0 || height <= 0) {
            throw new HeicFormatException("%s has no pixels: %dx%d".formatted(what, width, height));
        }
        if (width * height > maxPixelCount) {
            throw new HeicImageTooLargeException(
                    "%s is %dx%d pixels, more than the limit of %d".formatted(what, width, height, maxPixelCount));
        }
    }
}
