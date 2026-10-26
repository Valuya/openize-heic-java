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
 * The coefficients of the transform block being decoded, and the intermediate arrays of its inverse transform,
 * reused for every block of a picture. The coefficients and their scaled values are indexed {@code [x * size + y]}, the
 * transformed blocks row by row, {@code [y * size + x]}.
 *
 * <p>Residual coding fills the coefficients of a block; reconstructing that block, which always comes next, reads
 * them and clears them, the ones set only. A block without coded coefficients finds the buffers empty.
 */
final class ResidualBuffers {
    static final int MAX_BLOCK_SIZE = 32;
    private static final int MAX_BLOCK_AREA = MAX_BLOCK_SIZE * MAX_BLOCK_SIZE;

    final int[] scaled = new int[MAX_BLOCK_AREA];
    // the inverse transform: a one-dimensional transform, its scratch, and the clipped columns, row by row
    final int[] transformed = new int[MAX_BLOCK_SIZE];
    final int[] transformScratch = new int[MAX_BLOCK_SIZE];
    final int[] clipped = new int[MAX_BLOCK_AREA];
    // r[y * size + x], row by row
    final int[] residual = new int[MAX_BLOCK_AREA];

    // transform_skip_flag of the current block
    boolean transformSkipFlag;
    private final int[] coefficients = new int[MAX_BLOCK_AREA];
    // the indices of the coefficients set, to clear
    private final int[] setIndices = new int[MAX_BLOCK_AREA];
    private int setCount;
    private int blockSize;
    // bounds of the non-zero coefficients, -1 while there are none
    private int lastNonZeroX = -1;
    private int lastNonZeroY = -1;

    void startBlock(int size) {
        clear();
        blockSize = size;
    }

    void setCoefficient(int x, int y, int level) {
        int index = x * blockSize + y;
        coefficients[index] = level;
        setIndices[setCount++] = index;
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
        for (int i = 0; i < setCount; i++) {
            coefficients[setIndices[i]] = 0;
        }
        setCount = 0;
        lastNonZeroX = -1;
        lastNonZeroY = -1;
        transformSkipFlag = false;
    }
}
