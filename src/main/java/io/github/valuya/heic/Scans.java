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
 * The scan orders of square blocks (6.5.3 to 6.5.5): ScanOrder[log2BlockSize][scanIdx][sPos][sComp], for blocks of 1
 * to 8 samples, scanIdx 0 up-right diagonal, 1 horizontal and 2 vertical, sComp 0 for x and 1 for y.
 */
final class Scans {
    private static final int MAX_LOG2_BLOCK_SIZE = 3;
    private static final byte[][][][] SCAN_ORDER = createScanOrder();

    private Scans() {}

    static byte[][][][] getScanOrder() {
        return SCAN_ORDER;
    }

    private static byte[][][][] createScanOrder() {
        byte[][][][] scanOrder = new byte[MAX_LOG2_BLOCK_SIZE + 1][][][];
        for (int log2BlockSize = 0; log2BlockSize <= MAX_LOG2_BLOCK_SIZE; log2BlockSize++) {
            int blockSize = 1 << log2BlockSize;
            scanOrder[log2BlockSize] =
                    new byte[][][] {diagonalScan(blockSize), horizontalScan(blockSize), verticalScan(blockSize)};
        }
        return scanOrder;
    }

    // 6.5.3 Up-right diagonal scan order array initialization process
    private static byte[][] diagonalScan(int blockSize) {
        byte[][] diagScan = new byte[blockSize * blockSize][];
        int i = 0;
        int x = 0;
        int y = 0;
        while (i < blockSize * blockSize) {
            while (y >= 0) {
                if (x < blockSize && y < blockSize) {
                    diagScan[i] = position(x, y);
                    i++;
                }
                y--;
                x++;
            }
            y = x;
            x = 0;
        }
        return diagScan;
    }

    // 6.5.4 Horizontal scan order array initialization process
    private static byte[][] horizontalScan(int blockSize) {
        byte[][] horScan = new byte[blockSize * blockSize][];
        int i = 0;
        for (int y = 0; y < blockSize; y++) {
            for (int x = 0; x < blockSize; x++) {
                horScan[i] = position(x, y);
                i++;
            }
        }
        return horScan;
    }

    // 6.5.5 Vertical scan order array initialization process
    private static byte[][] verticalScan(int blockSize) {
        byte[][] verScan = new byte[blockSize * blockSize][];
        int i = 0;
        for (int x = 0; x < blockSize; x++) {
            for (int y = 0; y < blockSize; y++) {
                verScan[i] = position(x, y);
                i++;
            }
        }
        return verScan;
    }

    private static byte[] position(int x, int y) {
        return new byte[] {(byte) x, (byte) y};
    }
}
