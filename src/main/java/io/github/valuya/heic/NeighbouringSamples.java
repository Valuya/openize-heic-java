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
 * The neighbouring samples p[x][y] of a transform block of size nTbS (8.4.4.2.1): the row above with its corner,
 * x = -1..2 * nTbS - 1 at y = -1, and the column left, y = 0..2 * nTbS - 1 at x = -1.
 */
final class NeighbouringSamples {
    private final int[] samples;
    private int size;

    NeighbouringSamples(int maximumSize) {
        samples = new int[sampleCount(maximumSize)];
    }

    static int sampleCount(int nTbS) {
        return 4 * nTbS + 1;
    }

    /**
     * Sets up for a transform block of {@code nTbS} samples.
     */
    void prepare(int nTbS) {
        size = nTbS;
    }

    int get(int x, int y) {
        return samples[index(x, y)];
    }

    void set(int x, int y, int value) {
        samples[index(x, y)] = value;
    }

    // 8.4.4.2.2: the value of every neighbour when none is available
    void fill(int value) {
        Arrays.fill(samples, 0, sampleCount(size), value);
    }

    /** The samples, at {@link #index}. */
    int[] array() {
        return samples;
    }

    int index(int x, int y) {
        return y == -1 ? x + 1 : 2 * size + y + 1;
    }
}
