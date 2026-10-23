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
 * Where the pixels of an image land in a destination ARGB array: the part of the image that is kept ({@link #clip},
 * in the image's own coordinates) and the transform to destination coordinates.
 *
 * <p>The destination index is linear in the image coordinates, so writing a row is a strided loop:
 * {@code index = base() + y * stepY() + x * stepX()}.
 */
final class PixelTarget {
    private final PixelBox clip;
    private final AxisTransform transform;
    private final int[] destination;
    private final int destinationWidth;

    private PixelTarget(PixelBox clip, AxisTransform transform, int[] destination, int destinationWidth) {
        this.clip = clip;
        this.transform = transform;
        this.destination = destination;
        this.destinationWidth = destinationWidth;
    }

    /**
     * A destination holding {@code region} of an image whose pixels are in the image's own coordinates.
     */
    static PixelTarget forRegion(PixelBox region, int[] destination) {
        AxisTransform regionTransform = AxisTransform.translation(-region.left(), -region.top());
        return new PixelTarget(region, regionTransform, destination, region.width());
    }

    /**
     * The target for an image whose pixels map into this target's image by {@code toThisImage}, keeping only its
     * {@code kept} part.
     */
    PixelTarget forSourceImage(AxisTransform toThisImage, PixelBox kept) {
        PixelBox clipInSource = toThisImage.inverse().map(clip).intersect(kept);
        return new PixelTarget(clipInSource, toThisImage.andThen(transform), destination, destinationWidth);
    }

    PixelBox clip() {
        return clip;
    }

    boolean isEmpty() {
        return clip.isEmpty();
    }

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
        return transform.translateX() + transform.translateY() * destinationWidth;
    }

    /**
     * Sets every kept pixel of {@code box} to {@code argb}, or only its alpha byte to {@code argb}'s red channel when
     * the image is an alpha plane.
     */
    void fill(PixelBox box, int argb, boolean alphaOnly) {
        PixelBox filledBox = box.intersect(clip);
        int stepX = stepX();
        int stepY = stepY();
        int base = base();
        int alpha = (argb >> 16) & 0xFF;
        for (int y = filledBox.top(); y < filledBox.bottom(); y++) {
            int rowIndex = base + y * stepY;
            for (int x = filledBox.left(); x < filledBox.right(); x++) {
                int index = rowIndex + x * stepX;
                destination[index] = alphaOnly ? (destination[index] & 0x00FFFFFF) | (alpha << 24) : argb;
            }
        }
    }
}
