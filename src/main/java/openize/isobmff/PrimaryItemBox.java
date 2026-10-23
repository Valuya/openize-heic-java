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
 * The primary item of a file (ISO/IEC 14496-12, 8.11.4): the image shown by default.
 */
public class PrimaryItemBox extends FullBox {
    private final long itemId;

    PrimaryItemBox(BitStreamReader stream, BoxHeader header) {
        super(stream, header);
        itemId = stream.readLong(getVersion() == 0 ? 16 : 32);
    }

    public final long getItemId() {
        return itemId;
    }
}
