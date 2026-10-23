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

import java.util.Arrays;

/**
 * The coefficients of the transform block being decoded, and the intermediate arrays of its inverse transform,
 * reused for every block of a picture. Arrays are indexed {@code [x * size + y]}.
 *
 * <p>Residual coding fills the coefficients of a block; reconstructing that block, which always comes next, reads
 * them and clears them. A block without coded coefficients finds the buffers empty.
 */
final class ResidualBuffers {
    static final int MAX_BLOCK_SIZE = 32;
    private static final int MAX_BLOCK_AREA = MAX_BLOCK_SIZE * MAX_BLOCK_SIZE;

    final int[] scaled = new int[MAX_BLOCK_AREA];
    final int[] columnTransformed = new int[MAX_BLOCK_AREA];
    final int[] clipped = new int[MAX_BLOCK_AREA];
    final int[] residual = new int[MAX_BLOCK_AREA];

    private final int[] coefficients = new int[MAX_BLOCK_AREA];
    private int blockSize;
    // bounds of the non-zero coefficients, -1 while there are none
    private int lastNonZeroX = -1;
    private int lastNonZeroY = -1;

    void startBlock(int size) {
        clear();
        blockSize = size;
    }

    void setCoefficient(int x, int y, int level) {
        coefficients[x * blockSize + y] = level;
        lastNonZeroX = Math.max(lastNonZeroX, x);
        lastNonZeroY = Math.max(lastNonZeroY, y);
    }

    boolean isEmpty() {
        return lastNonZeroX < 0;
    }

    int coefficient(int x, int y) {
        return coefficients[x * blockSize + y];
    }

    int lastNonZeroX() {
        return lastNonZeroX;
    }

    int lastNonZeroY() {
        return lastNonZeroY;
    }

    void clear() {
        for (int x = 0; x <= lastNonZeroX; x++) {
            int columnStart = x * blockSize;
            Arrays.fill(coefficients, columnStart, columnStart + lastNonZeroY + 1, 0);
        }
        lastNonZeroX = -1;
        lastNonZeroY = -1;
    }
}
