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
 *
 * <p>The neighbours beside the block, left, above and at the corner, precede it in z-scan order whenever they are in its
 * slice and tile: the aligned block of its size that holds them precedes it, in its coding tree block or in an earlier
 * one. Those below left, and those above right, are each in one aligned block of its size too, which precedes it or
 * not as a whole: one z-scan order comparison each.
 */
final class NeighbouringSamplesGenerator {
    private static final int GROUP_SIZE = 4;

    private final NeighbouringSamples samples;

    // the block being predicted, and the minimum transform block address in z-scan order of it
    private int x0;
    private int y0;
    private int nTbS;
    private int cIdx;
    private int subWidth;
    private int subHeight;
    private int currentBlockAddress;
    private int currentCtb;

    NeighbouringSamplesGenerator(NeighbouringSamples samples) {
        this.samples = samples;
    }

    /**
     * Fills the neighbouring samples for the {@code nTbS} block of component {@code cIdx} at {@code (x0, y0)}, in
     * samples of that component. A generator serves the blocks of a workspace one after the other, so that none is
     * allocated per block.
     */
    NeighbouringSamples generate(HeicPicture picture, int x0, int y0, int nTbS, int cIdx) {
        this.x0 = x0;
        this.y0 = y0;
        this.nTbS = nTbS;
        this.cIdx = cIdx;
        subWidth = cIdx == 0 ? 1 : picture.sps.getSubWidthC();
        subHeight = cIdx == 0 ? 1 : picture.sps.getSubHeightC();
        currentBlockAddress = minTbAddrZs(picture, x0 * subWidth, y0 * subHeight);
        currentCtb = picture.ctbAddrRsAt(x0 * subWidth, y0 * subHeight);
        gatherAndSubstitute(picture);
        return samples;
    }

    // Gathers the available neighbours a group at a time, the column left from the bottom up, the corner, then the
    // row above from the left, and gives each unavailable one the value of the one before it in that order: the first
    // available one for the first ones (8.4.4.2.2). The samples of a group are all available or none is.
    private void gatherAndSubstitute(HeicPicture picture) {
        samples.prepare(nTbS);
        int[] p = samples.array();
        int groupCount = 2 * nTbS / GROUP_SIZE;
        int left = samples.index(-1, 0);
        int top = samples.index(0, -1);

        int xLuma = x0 * subWidth;
        int yLuma = y0 * subHeight;
        int lumaSize = nTbS * subWidth;
        SeqParameterSetRbsp sps = picture.sps;
        // the neighbours inside the picture, at most 2 * nTbS below and right
        int belowCount = Math.min(2 * nTbS, ceilDivide(sps.picHeightInLumaSamples - yLuma, subHeight));
        int rightCount = Math.min(2 * nTbS, ceilDivide(sps.picWidthInLumaSamples - xLuma, subWidth));

        // groups k of the column left, y = 4k..4k + 3, and of the row above, x = 4k..4k + 3, as bits
        int leftGroups = 0;
        int topGroups = 0;
        boolean cornerAvailable = false;
        if (isInSameSliceAndTile(picture, xLuma - 1, yLuma)) {
            boolean belowLeftDecoded = belowCount > nTbS && isDecoded(picture, -1, nTbS);
            for (int k = 0; k < groupCount && k * GROUP_SIZE < belowCount; k++) {
                if (k * GROUP_SIZE < nTbS || belowLeftDecoded) {
                    leftGroups |= 1 << k;
                    readColumn(picture, p, left + k * GROUP_SIZE, x0 - 1, y0 + k * GROUP_SIZE);
                }
            }
        }
        if (isInSameSliceAndTile(picture, xLuma - 1, yLuma - 1)) {
            cornerAvailable = true;
            p[samples.index(-1, -1)] = picture.getSample(cIdx, x0 - 1, y0 - 1);
        }
        boolean availableTop = isInSameSliceAndTile(picture, xLuma, yLuma - 1);
        boolean aboveRightDecoded = rightCount > nTbS
                && isInSameSliceAndTile(picture, xLuma + lumaSize, yLuma - 1)
                && isDecoded(picture, nTbS, -1);
        for (int k = 0; k < groupCount && k * GROUP_SIZE < rightCount; k++) {
            int x = k * GROUP_SIZE;
            if (x < nTbS ? availableTop : aboveRightDecoded) {
                topGroups |= 1 << k;
                readRow(picture, p, top + x, x0 + x, y0 - 1);
            }
        }

        int allGroups = (1 << groupCount) - 1;
        if (leftGroups == allGroups && topGroups == allGroups && cornerAvailable) {
            return;
        }
        if (leftGroups == 0 && topGroups == 0 && !cornerAvailable) {
            int bitDepth = cIdx == 0 ? sps.getBitDepthY() : sps.getBitDepthC();
            samples.fill(1 << (bitDepth - 1));
            return;
        }

        // the first available sample: the bottom one of the lowest group left, the corner, or the first one above
        int value;
        if (leftGroups != 0) {
            value = p[left + (31 - Integer.numberOfLeadingZeros(leftGroups)) * GROUP_SIZE + GROUP_SIZE - 1];
        } else if (cornerAvailable) {
            value = p[samples.index(-1, -1)];
        } else {
            value = p[top + Integer.numberOfTrailingZeros(topGroups) * GROUP_SIZE];
        }
        for (int k = groupCount - 1; k >= 0; k--) {
            int groupStart = left + k * GROUP_SIZE;
            if ((leftGroups & (1 << k)) != 0) {
                value = p[groupStart];
            } else {
                Arrays.fill(p, groupStart, groupStart + GROUP_SIZE, value);
            }
        }
        if (cornerAvailable) {
            value = p[samples.index(-1, -1)];
        } else {
            p[samples.index(-1, -1)] = value;
        }
        for (int k = 0; k < groupCount; k++) {
            int groupStart = top + k * GROUP_SIZE;
            if ((topGroups & (1 << k)) != 0) {
                value = p[groupStart + GROUP_SIZE - 1];
            } else {
                Arrays.fill(p, groupStart, groupStart + GROUP_SIZE, value);
            }
        }
    }

    // the four samples of the column x from y down, to p[index..index + 3]
    private void readColumn(HeicPicture picture, int[] p, int index, int x, int y) {
        int planeWidth = picture.planeWidths[cIdx];
        int planeIndex = y * planeWidth + x;
        for (int i = 0; i < GROUP_SIZE; i++, planeIndex += planeWidth) {
            p[index + i] = picture.highColorRange
                    ? picture.highColorRangePlanes[cIdx][planeIndex] & 0xFFFF
                    : picture.planes[cIdx][planeIndex] & 0xFF;
        }
    }

    // the four samples of the row y from x right, to p[index..index + 3]
    private void readRow(HeicPicture picture, int[] p, int index, int x, int y) {
        int planeIndex = y * picture.planeWidths[cIdx] + x;
        for (int i = 0; i < GROUP_SIZE; i++) {
            p[index + i] = picture.highColorRange
                    ? picture.highColorRangePlanes[cIdx][planeIndex + i] & 0xFFFF
                    : picture.planes[cIdx][planeIndex + i] & 0xFF;
        }
    }

    // Whether the coding tree block covering a luma location outside the current block is inside the picture, in the
    // slice and in the tile of the current coding tree block
    private boolean isInSameSliceAndTile(HeicPicture picture, int xNbLuma, int yNbLuma) {
        if (xNbLuma < 0 || yNbLuma < 0) {
            return false;
        }
        int neighbourCtb = picture.ctbAddrRsAt(xNbLuma, yNbLuma);
        // a coding tree block is in one slice and one tile
        if (neighbourCtb == currentCtb) {
            return true;
        }
        int[] tileIdFromRs = picture.pps.getTileIdFromRs();
        return picture.getSliceAddrRs(neighbourCtb) == picture.getSliceAddrRs(currentCtb)
                && tileIdFromRs[neighbourCtb] == tileIdFromRs[currentCtb];
    }

    private boolean isDecoded(HeicPicture picture, int x, int y) {
        return minTbAddrZs(picture, (x0 + x) * subWidth, (y0 + y) * subHeight) <= currentBlockAddress;
    }

    private static int minTbAddrZs(HeicPicture picture, int xLuma, int yLuma) {
        int minTbLog2SizeY = picture.sps.getMinTbLog2SizeY();
        return picture.pps.minTbAddrZs(xLuma >> minTbLog2SizeY, yLuma >> minTbLog2SizeY);
    }

    private static int ceilDivide(int dividend, int divisor) {
        return (dividend + divisor - 1) / divisor;
    }
}
