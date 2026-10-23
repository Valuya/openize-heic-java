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
 * A small value, -128 to 127, per 4x4 block of luma samples. Coding blocks are at least 8x8 and prediction and
 * transform blocks at least 4x4, so a value set for a block of the picture holds for every sample of it.
 */
final class BlockByteMap {
    private static final int LOG2_BLOCK_SIZE = 2;

    private final int widthInBlocks;
    private final byte[] values;

    BlockByteMap(int width, int height) {
        widthInBlocks = (width + 3) >> LOG2_BLOCK_SIZE;
        int heightInBlocks = (height + 3) >> LOG2_BLOCK_SIZE;
        values = new byte[widthInBlocks * heightInBlocks];
    }

    int get(int x, int y) {
        return values[index(x, y)];
    }

    void set(int x, int y, int value) {
        values[index(x, y)] = (byte) value;
    }

    /**
     * Sets the value of every block of the square of {@code size} samples at {@code (x0, y0)}.
     */
    void fill(int x0, int y0, int size, int value) {
        int sizeInBlocks = Math.max(size >> LOG2_BLOCK_SIZE, 1);
        for (int blockY = 0; blockY < sizeInBlocks; blockY++) {
            int rowStart = index(x0, y0 + (blockY << LOG2_BLOCK_SIZE));
            Arrays.fill(values, rowStart, rowStart + sizeInBlocks, (byte) value);
        }
    }

    boolean getBit(int x, int y, int bit) {
        return (get(x, y) & (1 << bit)) != 0;
    }

    void setBit(int x, int y, int bit, boolean bitValue) {
        int index = index(x, y);
        values[index] = (byte) (bitValue ? values[index] | (1 << bit) : values[index] & ~(1 << bit));
    }

    private int index(int x, int y) {
        return (y >> LOG2_BLOCK_SIZE) * widthInBlocks + (x >> LOG2_BLOCK_SIZE);
    }
}
