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
 * A mirroring of an image about a vertical or a horizontal axis (ISO/IEC 23008-12, 6.5.12).
 */
public class ImageMirror extends Box {
    private final int axis;

    ImageMirror(BitStreamReader stream, BoxHeader header) {
        super(header);
        stream.skipBits(7); // reserved
        axis = stream.read(1);
    }

    /**
     * 0 to mirror about a vertical axis, swapping left and right; 1 about a horizontal axis, swapping top and bottom.
     */
    public final int getAxis() {
        return axis;
    }
}
