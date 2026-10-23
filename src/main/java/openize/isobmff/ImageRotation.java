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
 * <p>
 * The image rotation  transformative item property rotates the reconstructed image of the
 * associated image item in anti-clockwise direction in units of 90 degrees.
 * </p>
 */
public class ImageRotation extends Box {
    /**
     * <p>
     * Specifies the angle (in anti-clockwise direction) in units of 90 degrees.
     * </p>
     */
    public final byte angle;

    /**
     * <p>
     * Create the box object from the bitstream and box size.
     * </p>
     *
     * @param stream File stream.
     * @param header Box header.
     */
    ImageRotation(BitStreamReader stream, BoxHeader header) {
        super(header);

        stream.skipBits(6);
        angle = (byte) stream.read(2);
    }

    /**
     * <p>
     * Text summary of the box.
     * </p>
     */
    @Override
    public String toString() {
        return String.format("%s angle: %d", this.type, (angle & 0xFF) * 90);
    }
}
