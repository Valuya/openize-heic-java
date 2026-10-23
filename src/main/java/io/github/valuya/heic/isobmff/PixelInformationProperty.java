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
 * The number of channels of an image and the bit depth of each (ISO/IEC 23008-12, 6.5.6).
 */
public class PixelInformationProperty extends FullBox {
    private final byte[] bitsPerChannel;

    PixelInformationProperty(BitStreamReader stream, BoxHeader header) {
        super(stream, header);
        int channelCount = stream.read(8);
        requireEntriesFit(channelCount, 1);
        bitsPerChannel = stream.readBytes(channelCount);
    }

    public final int getChannelCount() {
        return bitsPerChannel.length;
    }

    /**
     * The bit depth of each channel, an unsigned byte; a copy.
     */
    public final byte[] getBitsPerChannel() {
        return bitsPerChannel.clone();
    }
}
