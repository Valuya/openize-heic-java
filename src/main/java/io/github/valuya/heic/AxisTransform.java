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
 * A transform made of axis swaps, mirrors and a translation, mapping pixel {@code (x, y)} to
 * {@code (a·x + b·y + translateX, c·x + d·y + translateY)}: what cropping, rotating by quarter turns, mirroring and
 * placing an image in a grid or overlay amount to.
 */
record AxisTransform(int a, int b, int c, int d, int translateX, int translateY) {
    static final AxisTransform IDENTITY = new AxisTransform(1, 0, 0, 1, 0, 0);

    static AxisTransform translation(int translateX, int translateY) {
        return new AxisTransform(1, 0, 0, 1, translateX, translateY);
    }

    int mapX(int x, int y) {
        return a * x + b * y + translateX;
    }

    int mapY(int x, int y) {
        return c * x + d * y + translateY;
    }

    /**
     * This transform followed by {@code next}.
     */
    AxisTransform andThen(AxisTransform next) {
        return new AxisTransform(
                next.a * a + next.b * c,
                next.a * b + next.b * d,
                next.c * a + next.d * c,
                next.c * b + next.d * d,
                next.mapX(translateX, translateY),
                next.mapY(translateX, translateY));
    }

    AxisTransform inverse() {
        // the matrix swaps and negates axes, so its inverse is its transpose
        AxisTransform transposed = new AxisTransform(a, c, b, d, 0, 0);
        return new AxisTransform(
                a, c, b, d, -transposed.mapX(translateX, translateY), -transposed.mapY(translateX, translateY));
    }

    PixelBox map(PixelBox box) {
        if (box.isEmpty()) {
            return PixelBox.EMPTY;
        }
        int firstX = mapX(box.left(), box.top());
        int firstY = mapY(box.left(), box.top());
        int lastX = mapX(box.right() - 1, box.bottom() - 1);
        int lastY = mapY(box.right() - 1, box.bottom() - 1);
        return new PixelBox(
                Math.min(firstX, lastX),
                Math.min(firstY, lastY),
                Math.max(firstX, lastX) + 1,
                Math.max(firstY, lastY) + 1);
    }
}
