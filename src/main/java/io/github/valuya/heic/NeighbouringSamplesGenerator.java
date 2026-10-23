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
 * Gathers the neighbouring samples of a transform block for intra prediction, substituting the unavailable ones
 * (8.4.4.2.2). Samples are taken four at a time: a group of four never straddles blocks decoded at different times.
 */
final class NeighbouringSamplesGenerator {
    private static final int GROUP_SIZE = 4;

    private final HeicPicture picture;
    private final int x0;
    private final int y0;
    private final int nTbS;
    private final int cIdx;
    private final int subWidth;
    private final int subHeight;
    private final NeighbouringSamples samples;
    private final boolean[] available;
    // the minimum transform block address in z-scan order of the current block
    private final int currentBlockAddress;
    private int availableCount;
    // the first available sample, in the order 8.4.4.2.2 searches for one
    private int firstAvailableValue;

    private NeighbouringSamplesGenerator(
            HeicPicture picture, int x0, int y0, int nTbS, int cIdx, IntraPredictionBuffers buffers) {
        this.picture = picture;
        this.x0 = x0;
        this.y0 = y0;
        this.nTbS = nTbS;
        this.cIdx = cIdx;
        subWidth = cIdx == 0 ? 1 : picture.sps.getSubWidthC();
        subHeight = cIdx == 0 ? 1 : picture.sps.getSubHeightC();
        samples = buffers.neighbouringSamples;
        available = buffers.availability;
        currentBlockAddress = minTbAddrZs(x0 * subWidth, y0 * subHeight);
    }

    /**
     * Fills {@code buffers.neighbouringSamples} for the {@code nTbS} block of component {@code cIdx} at
     * {@code (x0, y0)}, in samples of that component.
     */
    static NeighbouringSamples generate(
            HeicPicture picture, int x0, int y0, int nTbS, int cIdx, IntraPredictionBuffers buffers) {
        NeighbouringSamplesGenerator generator = new NeighbouringSamplesGenerator(picture, x0, y0, nTbS, cIdx, buffers);
        generator.gather();
        generator.substituteUnavailable();
        return generator.samples;
    }

    private void gather() {
        samples.prepare(nTbS);
        Arrays.fill(available, 0, NeighbouringSamples.sampleCount(nTbS), false);

        int xLuma = x0 * subWidth;
        int yLuma = y0 * subHeight;
        int lumaSize = nTbS * subWidth;
        SeqParameterSetRbsp sps = picture.sps;

        // the neighbours inside the picture, at most 2 * nTbS below and right
        int belowCount = Math.min(2 * nTbS, ceilDivide(sps.picHeightInLumaSamples - yLuma, subHeight));
        int rightCount = Math.min(2 * nTbS, ceilDivide(sps.picWidthInLumaSamples - xLuma, subWidth));

        if (isInSameSliceAndTile(xLuma - 1, yLuma)) {
            for (int y = belowCount - 1; y >= 0; y -= GROUP_SIZE) {
                gatherGroup(-1, y, 0, -1);
            }
        }
        if (isInSameSliceAndTile(xLuma - 1, yLuma - 1)) {
            gatherSample(-1, -1);
        }
        boolean availableTop = isInSameSliceAndTile(xLuma, yLuma - 1);
        boolean availableTopRight =
                xLuma + lumaSize < sps.picWidthInLumaSamples && isInSameSliceAndTile(xLuma + lumaSize, yLuma - 1);
        for (int x = 0; x < rightCount; x += GROUP_SIZE) {
            if (x < nTbS ? availableTop : availableTopRight) {
                gatherGroup(x, -1, 1, 0);
            }
        }
    }

    // Whether the coding tree block covering a luma location outside the current block is inside the picture, in the
    // slice and in the tile of the current coding tree block
    private boolean isInSameSliceAndTile(int xNbLuma, int yNbLuma) {
        if (xNbLuma < 0 || yNbLuma < 0) {
            return false;
        }
        int ctbLog2Size = picture.sps.getCtbLog2SizeY();
        int widthInCtbs = picture.sps.getPicWidthInCtbsY();
        int xCurrentCtb = (x0 * subWidth) >> ctbLog2Size;
        int yCurrentCtb = (y0 * subHeight) >> ctbLog2Size;
        int xNbCtb = xNbLuma >> ctbLog2Size;
        int yNbCtb = yNbLuma >> ctbLog2Size;
        int[] tileIdFromRs = picture.pps.getTileIdFromRs();
        return picture.sliceAddrRs[xNbCtb][yNbCtb] == picture.sliceAddrRs[xCurrentCtb][yCurrentCtb]
                && tileIdFromRs[yNbCtb * widthInCtbs + xNbCtb] == tileIdFromRs[yCurrentCtb * widthInCtbs + xCurrentCtb];
    }

    // The group of four samples from (x, y), stepping by (stepX, stepY), when its first sample is already decoded
    private void gatherGroup(int x, int y, int stepX, int stepY) {
        if (!isDecoded(x, y)) {
            return;
        }
        for (int i = 0; i < GROUP_SIZE; i++) {
            take(x + i * stepX, y + i * stepY);
        }
    }

    private void gatherSample(int x, int y) {
        if (isDecoded(x, y)) {
            take(x, y);
        }
    }

    private boolean isDecoded(int x, int y) {
        return minTbAddrZs((x0 + x) * subWidth, (y0 + y) * subHeight) <= currentBlockAddress;
    }

    private void take(int x, int y) {
        int value = picture.getSample(cIdx, x0 + x, y0 + y);
        if (availableCount == 0) {
            firstAvailableValue = value;
        }
        samples.set(x, y, value);
        available[samples.index(x, y)] = true;
        availableCount++;
    }

    private void substituteUnavailable() {
        if (availableCount == NeighbouringSamples.sampleCount(nTbS)) {
            return;
        }
        if (availableCount == 0) {
            int bitDepth = cIdx == 0 ? picture.sps.getBitDepthY() : picture.sps.getBitDepthC();
            samples.fill(1 << (bitDepth - 1));
            return;
        }

        if (!available[samples.index(-1, 2 * nTbS - 1)]) {
            samples.set(-1, 2 * nTbS - 1, firstAvailableValue);
        }
        for (int y = 2 * nTbS - 2; y >= -1; y--) {
            if (!available[samples.index(-1, y)]) {
                samples.set(-1, y, samples.get(-1, y + 1));
            }
        }
        for (int x = 0; x <= 2 * nTbS - 1; x++) {
            if (!available[samples.index(x, -1)]) {
                samples.set(x, -1, samples.get(x - 1, -1));
            }
        }
    }

    private int minTbAddrZs(int xLuma, int yLuma) {
        int minTbLog2SizeY = picture.sps.getMinTbLog2SizeY();
        return picture.pps.getMinTbAddrZs()[xLuma >> minTbLog2SizeY][yLuma >> minTbLog2SizeY];
    }

    private static int ceilDivide(int dividend, int divisor) {
        return (dividend + divisor - 1) / divisor;
    }
}
