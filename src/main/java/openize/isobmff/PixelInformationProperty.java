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
 * The PixelInformationProperty descriptive item property indicates the number and bit depth of
 * colour components in the reconstructed image of the associated image item.
 * </p>
 */
public class PixelInformationProperty extends FullBox {
    /**
     * <p>
     * This field signals the number of channels by each pixel of the reconstructed image ofthe associated image item.
     * </p>
     */
    public final byte numChannels;

    /**
     * <p>
     * This field indicates the bits per channel for the pixels of the reconstructed image of the associated image item.
     * </p>
     */
    public final byte[] bitsPerChannel;

    /**
     * <p>
     * Create the box object from the bitstream and box size.
     * </p>
     *
     * @param stream File stream.
     * @param header Box header.
     */
    PixelInformationProperty(BitStreamReader stream, BoxHeader header) {
        super(stream, header);

        numChannels = (byte) stream.read(8);
        bitsPerChannel = new byte[numChannels & 0xFF];
        for (int i = 0; i < (numChannels & 0xFF); i++) bitsPerChannel[i] = (byte) stream.read(8);
    }

    /**
     * <p>
     * Text summary of the box.
     * </p>
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s ch: %d bits:", type, numChannels));
        for (byte b : bitsPerChannel) {
            sb.append(' ').append(String.format("%x", b));
        }
        return sb.toString();
    }
}
