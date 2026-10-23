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
 * The arrays of intra prediction (8.4.4.2), reused for every transform block of a picture: the neighbouring samples,
 * their filtered copy, the predicted block and the reference row of angular prediction.
 */
final class IntraPredictionBuffers {
    private static final int MAX_BLOCK_SIZE = 32;

    final NeighbouringSamples neighbouringSamples = new NeighbouringSamples(MAX_BLOCK_SIZE);
    final NeighbouringSamples filteredSamples = new NeighbouringSamples(MAX_BLOCK_SIZE);
    // predSamples[x][y] at [y * nTbS + x], row by row, for the current block size nTbS
    final int[] predictedSamples = new int[MAX_BLOCK_SIZE * MAX_BLOCK_SIZE];
    final int[] angularReference = new int[3 * MAX_BLOCK_SIZE + 1];
}
