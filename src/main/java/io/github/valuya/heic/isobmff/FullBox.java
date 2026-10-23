/*
 * FileFormat.IsoBmff
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of FileFormat.IsoBmff.
 *
 * FileFormat.IsoBmff is available under MIT license, which is
 * available along with FileFormat.IsoBmff sources.
 */

package io.github.valuya.heic.isobmff;

import io.github.valuya.heic.isobmff.io.BitStreamReader;

/**
 * A box whose header is followed by a version and flags (ISO/IEC 14496-12, 4.2).
 */
public class FullBox extends Box {
    private final int version;
    private final int flags;

    FullBox(BitStreamReader stream, BoxHeader header) {
        super(header);
        version = stream.read(8);
        flags = stream.read(24);
    }

    /**
     * The version of the format of the box.
     */
    final int getVersion() {
        return version;
    }

    /**
     * The 24 flag bits, whose meaning depends on the box type.
     */
    final int getFlags() {
        return flags;
    }
}
