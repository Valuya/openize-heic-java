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
import java.util.Arrays;

/**
 * The data of the items stored in the meta box itself, with construction method 1 (ISO/IEC 14496-12, 8.11.11).
 */
public class ItemDataBox extends Box {
    private final byte[] data;

    ItemDataBox(BitStreamReader stream, BoxHeader header) {
        super(header);
        // the box fits in the file, which bounds its size
        data = stream.readBytes((int) (getEndOffset() - stream.getBitPosition() / 8));
    }

    /**
     * The length of the data, in bytes.
     */
    public final int getDataLength() {
        return data.length;
    }

    /**
     * A copy of {@code length} bytes of the data from {@code offset}, which must be within it.
     */
    public final byte[] copyData(int offset, int length) {
        return Arrays.copyOfRange(data, offset, offset + length);
    }
}
