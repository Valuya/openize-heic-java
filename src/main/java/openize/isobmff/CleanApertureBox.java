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
 * A crop of an image (ISO/IEC 14496-12, 12.1.4): the width and height of the area kept, and the offset of its centre
 * from the centre of the image, each a fraction.
 */
public class CleanApertureBox extends Box {
    private final long widthNumerator;
    private final long widthDenominator;
    private final long heightNumerator;
    private final long heightDenominator;
    private final int horizontalOffsetNumerator;
    private final long horizontalOffsetDenominator;
    private final int verticalOffsetNumerator;
    private final long verticalOffsetDenominator;

    CleanApertureBox(BitStreamReader stream, BoxHeader header) {
        super(header);
        widthNumerator = stream.readLong(32);
        widthDenominator = stream.readLong(32);
        heightNumerator = stream.readLong(32);
        heightDenominator = stream.readLong(32);
        // the offsets are signed
        horizontalOffsetNumerator = stream.read(32);
        horizontalOffsetDenominator = stream.readLong(32);
        verticalOffsetNumerator = stream.read(32);
        verticalOffsetDenominator = stream.readLong(32);
    }

    public final long getWidthNumerator() {
        return widthNumerator;
    }

    public final long getWidthDenominator() {
        return widthDenominator;
    }

    public final long getHeightNumerator() {
        return heightNumerator;
    }

    public final long getHeightDenominator() {
        return heightDenominator;
    }

    /**
     * The numerator of the horizontal offset of the centre of the clean aperture from the centre of the image,
     * {@code (width - 1) / 2}; typically 0.
     */
    public final int getHorizontalOffsetNumerator() {
        return horizontalOffsetNumerator;
    }

    public final long getHorizontalOffsetDenominator() {
        return horizontalOffsetDenominator;
    }

    /**
     * The numerator of the vertical offset of the centre of the clean aperture from the centre of the image,
     * {@code (height - 1) / 2}; typically 0.
     */
    public final int getVerticalOffsetNumerator() {
        return verticalOffsetNumerator;
    }

    public final long getVerticalOffsetDenominator() {
        return verticalOffsetDenominator;
    }
}
