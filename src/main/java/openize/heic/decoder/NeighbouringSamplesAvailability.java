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

class NeighbouringSamplesAvailability extends NeighbouringSamplesTemplate {
    private final boolean[] data;

    public NeighbouringSamplesAvailability(int nTbS) {
        super(nTbS);
        data = new boolean[sampleCount(nTbS)];
    }

    public final boolean get(int x, int y) {
        return data[getIndex(x, y)];
    }

    public final void set(int x, int y, boolean value) {
        data[getIndex(x, y)] = value;
    }

    @Override
    void prepare(int nTbS) {
        super.prepare(nTbS);
        Arrays.fill(data, 0, sampleCount(nTbS), false);
    }
}
