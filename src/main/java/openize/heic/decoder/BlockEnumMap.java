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
 * An enum constant per 4x4 block of luma samples; every block starts with the first constant.
 */
final class BlockEnumMap<E extends Enum<E>> {
    private final E[] constants;
    private final BlockIntMap ordinals;

    BlockEnumMap(Class<E> enumType, int width, int height) {
        constants = enumType.getEnumConstants();
        ordinals = new BlockIntMap(width, height);
    }

    E get(int x, int y) {
        return constants[ordinals.get(x, y)];
    }

    void set(int x, int y, E value) {
        ordinals.set(x, y, value.ordinal());
    }

    void fill(int x0, int y0, int size, E value) {
        ordinals.fill(x0, y0, size, value.ordinal());
    }
}
