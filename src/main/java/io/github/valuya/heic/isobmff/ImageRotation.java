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
 * A rotation of an image, anti-clockwise, by a multiple of 90 degrees (ISO/IEC 23008-12, 6.5.10).
 */
public class ImageRotation extends Box {
    private final int angle;

    ImageRotation(BitStreamReader stream, BoxHeader header) {
        super(header);
        stream.skipBits(6); // reserved
        angle = stream.read(2);
    }

    /**
     * The rotation, anti-clockwise, in units of 90 degrees: 0 to 3.
     */
    public final int getAngle() {
        return angle;
    }
}
