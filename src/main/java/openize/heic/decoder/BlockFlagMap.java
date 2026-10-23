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

/**
 * A flag per 4x4 block of luma samples, all clear to start with.
 */
final class BlockFlagMap {
    private final BlockIntMap flags;

    BlockFlagMap(int width, int height) {
        flags = new BlockIntMap(width, height);
    }

    boolean get(int x, int y) {
        return flags.get(x, y) != 0;
    }

    void set(int x, int y, boolean value) {
        flags.set(x, y, value ? 1 : 0);
    }

    void fill(int x0, int y0, int size, boolean value) {
        flags.fill(x0, y0, size, value ? 1 : 0);
    }
}
