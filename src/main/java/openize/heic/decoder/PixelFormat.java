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
 * The layout of the bytes of a pixel decoded to a byte array: 8 bits per component, in the order of the name.
 */
public enum PixelFormat {
    RGB24(3),
    RGBA32(4),
    ARGB32(4),
    BGRA32(4);

    private final int bytesPerPixel;

    PixelFormat(int bytesPerPixel) {
        this.bytesPerPixel = bytesPerPixel;
    }

    public int bytesPerPixel() {
        return bytesPerPixel;
    }

    // writes the 0xAARRGGBB pixel at output[index] in this format
    void write(int argb, byte[] output, int index) {
        byte alpha = (byte) (argb >>> 24);
        byte red = (byte) (argb >> 16);
        byte green = (byte) (argb >> 8);
        byte blue = (byte) argb;
        switch (this) {
            case RGB24 -> {
                output[index] = red;
                output[index + 1] = green;
                output[index + 2] = blue;
            }
            case RGBA32 -> {
                output[index] = red;
                output[index + 1] = green;
                output[index + 2] = blue;
                output[index + 3] = alpha;
            }
            case ARGB32 -> {
                output[index] = alpha;
                output[index + 1] = red;
                output[index + 2] = green;
                output[index + 3] = blue;
            }
            case BGRA32 -> {
                output[index] = blue;
                output[index + 1] = green;
                output[index + 2] = red;
                output[index + 3] = alpha;
            }
        }
    }
}
