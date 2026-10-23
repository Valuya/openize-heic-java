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
 * A rectangle of pixels; {@code right} and {@code bottom} are exclusive.
 */
record PixelBox(int left, int top, int right, int bottom) {
    static final PixelBox EMPTY = new PixelBox(0, 0, 0, 0);

    static PixelBox ofSize(int width, int height) {
        return new PixelBox(0, 0, width, height);
    }

    PixelBox intersect(PixelBox other) {
        int intersectionLeft = Math.max(left, other.left);
        int intersectionTop = Math.max(top, other.top);
        int intersectionRight = Math.min(right, other.right);
        int intersectionBottom = Math.min(bottom, other.bottom);
        if (intersectionLeft >= intersectionRight || intersectionTop >= intersectionBottom) {
            return EMPTY;
        }
        return new PixelBox(intersectionLeft, intersectionTop, intersectionRight, intersectionBottom);
    }

    boolean isEmpty() {
        return left >= right || top >= bottom;
    }

    int width() {
        return right - left;
    }

    int height() {
        return bottom - top;
    }
}
