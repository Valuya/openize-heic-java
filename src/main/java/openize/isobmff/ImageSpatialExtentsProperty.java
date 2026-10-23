/*
 * FileFormat.IsoBmff
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of FileFormat.IsoBmff.
 *
 * FileFormat.IsoBmff is available under MIT license, which is
 * available along with FileFormat.IsoBmff sources.
 */

package openize.isobmff;

import openize.isobmff.io.BitStreamReader;

/**
 * The width and height of an image, before any transformative property applies (ISO/IEC 23008-12, 6.5.3).
 */
public class ImageSpatialExtentsProperty extends FullBox {
    private final long width;
    private final long height;

    ImageSpatialExtentsProperty(BitStreamReader stream, BoxHeader header) {
        super(stream, header);
        width = stream.readLong(32);
        height = stream.readLong(32);
    }

    /**
     * The width in pixels.
     */
    public final long getWidth() {
        return width;
    }

    /**
     * The height in pixels.
     */
    public final long getHeight() {
        return height;
    }
}
