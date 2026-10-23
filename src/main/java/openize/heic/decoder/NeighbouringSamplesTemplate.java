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

class NeighbouringSamplesTemplate {
    protected int size;

    public NeighbouringSamplesTemplate(int nTbS) {
        size = nTbS;
    }

    /**
     * Sets up for a transform block of {@code nTbS} samples: 2 * nTbS + 1 above (with the corner), 2 * nTbS left.
     */
    void prepare(int nTbS) {
        size = nTbS;
    }

    static int sampleCount(int nTbS) {
        return 4 * nTbS + 1;
    }

    protected final int getIndex(int x, int y) {
        if (y == -1) {
            return x + 1;
        } else {
            return 2 * size + y + 1;
        }
    }
}
