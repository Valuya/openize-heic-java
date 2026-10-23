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
 * their filtered copy, which of them are available, the predicted block and the reference row of angular prediction.
 */
final class IntraPredictionBuffers {
    private static final int MAX_BLOCK_SIZE = 32;

    final NeighbouringSamples neighbouringSamples = new NeighbouringSamples(MAX_BLOCK_SIZE);
    final NeighbouringSamples filteredSamples = new NeighbouringSamples(MAX_BLOCK_SIZE);
    // which neighbouring samples are available, indexed like the samples
    final boolean[] availability = new boolean[NeighbouringSamples.sampleCount(MAX_BLOCK_SIZE)];
    // predSamples[x][y], valid for x, y below the current block size
    final int[][] predictedSamples = new int[MAX_BLOCK_SIZE][MAX_BLOCK_SIZE];
    final int[] angularReference = new int[3 * MAX_BLOCK_SIZE + 1];
}
