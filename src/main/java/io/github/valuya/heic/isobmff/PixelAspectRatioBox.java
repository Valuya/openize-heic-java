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
 * The aspect ratio of a pixel, as its relative width and height (ISO/IEC 14496-12, 12.1.4).
 */
public class PixelAspectRatioBox extends Box {
    private final long horizontalSpacing;
    private final long verticalSpacing;

    PixelAspectRatioBox(BitStreamReader stream, BoxHeader header) {
        super(header);
        horizontalSpacing = stream.readLong(32);
        verticalSpacing = stream.readLong(32);
    }

    /**
     * The relative width of a pixel.
     */
    public final long getHorizontalSpacing() {
        return horizontalSpacing;
    }

    /**
     * The relative height of a pixel.
     */
    public final long getVerticalSpacing() {
        return verticalSpacing;
    }
}
