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
 * Where the pixels of an image land: in a destination array of ARGB ints, or of bytes in a {@link PixelFormat}. A
 * target holds the part of the image that is kept ({@link #clip}, in the image's own coordinates) and the transform
 * to destination coordinates.
 *
 * <p>The destination index of a pixel is linear in the image coordinates, so writing a row is a strided loop:
 * {@code index = base() + y * stepY() + x * stepX()}, in pixels.
 */
final class PixelTarget {
    private static final int[] NO_INTS = new int[0];
    private static final byte[] NO_BYTES = new byte[0];

    private final PixelBox clip;
    private final AxisTransform transform;
    // ARGB ints, or bytes in pixelFormat when byteDestination is not empty
    private final int[] destination;
    private final byte[] byteDestination;
    private final PixelFormat pixelFormat;
    private final int destinationOffset;
    private final int destinationWidth;

    private PixelTarget(
            PixelBox clip,
            AxisTransform transform,
            int[] destination,
            byte[] byteDestination,
            PixelFormat pixelFormat,
            int destinationOffset,
            int destinationWidth) {
        this.clip = clip;
        this.transform = transform;
        this.destination = destination;
        this.byteDestination = byteDestination;
        this.pixelFormat = pixelFormat;
        this.destinationOffset = destinationOffset;
        this.destinationWidth = destinationWidth;
    }

    /**
     * A destination holding {@code region} of an image whose pixels are in the image's own coordinates, its first
     * pixel at {@code destination[offset]} and each row {@code scanlineStride} after the one before.
     */
    static PixelTarget forRegion(PixelBox region, int[] destination, int offset, int scanlineStride) {
        AxisTransform regionTransform = AxisTransform.translation(-region.left(), -region.top());
        return new PixelTarget(
                region, regionTransform, destination, NO_BYTES, PixelFormat.ARGB32, offset, scanlineStride);
    }

    /** A destination holding {@code region} of an image, row by row, as bytes in {@code pixelFormat}. */
    static PixelTarget forRegion(PixelBox region, byte[] destination, PixelFormat pixelFormat) {
        AxisTransform regionTransform = AxisTransform.translation(-region.left(), -region.top());
        return new PixelTarget(region, regionTransform, NO_INTS, destination, pixelFormat, 0, region.width());
    }

    /**
     * The target for an image whose pixels map into this target's image by {@code toThisImage}, keeping only its
     * {@code kept} part.
     */
    PixelTarget forSourceImage(AxisTransform toThisImage, PixelBox kept) {
        PixelBox clipInSource = toThisImage.inverse().map(clip).intersect(kept);
        return new PixelTarget(
                clipInSource,
                toThisImage.andThen(transform),
                destination,
                byteDestination,
                pixelFormat,
                destinationOffset,
                destinationWidth);
    }

    PixelBox clip() {
        return clip;
    }

    boolean isEmpty() {
        return clip.isEmpty();
    }

    /** Whether the destination is ARGB ints, {@link #destination()}, rather than bytes. */
    boolean holdsInts() {
        return byteDestination.length == 0;
    }

    /** Whether the destination keeps the alpha of the pixels: an alpha plane need not be decoded otherwise. */
    boolean keepsAlpha() {
        return holdsInts() || pixelFormat.alphaOffset() >= 0;
    }

    /** The ARGB destination; empty when the destination is bytes. */
    int[] destination() {
        return destination;
    }

    int stepX() {
        return transform.a() + transform.c() * destinationWidth;
    }

    int stepY() {
        return transform.b() + transform.d() * destinationWidth;
    }

    int base() {
        return destinationOffset + transform.translateX() + transform.translateY() * destinationWidth;
    }

    /** Writes the {@code count} ARGB pixels of {@code argb} to row y of the image, from x on. */
    void writeRow(int y, int x, int[] argb, int count) {
        int index = base() + y * stepY() + x * stepX();
        int stepX = stepX();
        if (holdsInts()) {
            for (int i = 0; i < count; i++) {
                destination[index + i * stepX] = argb[i];
            }
        } else {
            int bytesPerPixel = pixelFormat.bytesPerPixel();
            pixelFormat.write(argb, count, byteDestination, index * bytesPerPixel, stepX * bytesPerPixel);
        }
    }

    /** Sets the alpha of the {@code count} pixels of row y of the image, from x on, to the values of {@code alpha}. */
    void writeAlphaRow(int y, int x, int[] alpha, int count) {
        int index = base() + y * stepY() + x * stepX();
        int stepX = stepX();
        if (holdsInts()) {
            for (int i = 0; i < count; i++) {
                int pixelIndex = index + i * stepX;
                destination[pixelIndex] = (destination[pixelIndex] & 0x00FFFFFF) | (alpha[i] << 24);
            }
        } else if (pixelFormat.alphaOffset() >= 0) {
            int bytesPerPixel = pixelFormat.bytesPerPixel();
            int byteIndex = index * bytesPerPixel + pixelFormat.alphaOffset();
            for (int i = 0; i < count; i++) {
                byteDestination[byteIndex + i * stepX * bytesPerPixel] = (byte) alpha[i];
            }
        }
    }

    /**
     * Sets every kept pixel of {@code box} to {@code argb}, or only its alpha byte to {@code argb}'s red channel when
     * the image is an alpha plane.
     */
    void fill(PixelBox box, int argb, boolean alphaOnly) {
        PixelBox filledBox = box.intersect(clip);
        int[] row = new int[filledBox.width()];
        Arrays.fill(row, alphaOnly ? (argb >> 16) & 0xFF : argb);
        for (int y = filledBox.top(); y < filledBox.bottom(); y++) {
            if (alphaOnly) {
                writeAlphaRow(y, filledBox.left(), row, row.length);
            } else {
                writeRow(y, filledBox.left(), row, row.length);
            }
        }
    }
}
