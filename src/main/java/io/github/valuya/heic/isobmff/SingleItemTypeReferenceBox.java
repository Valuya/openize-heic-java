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
 * The references of one type from one item to others (ISO/IEC 14496-12, 8.11.12). The box type is the reference
 * type, such as {@link BoxType#dimg} or {@link BoxType#thmb}.
 */
public class SingleItemTypeReferenceBox extends Box {
    private final long fromItemId;
    private final long[] toItemIds;

    SingleItemTypeReferenceBox(BitStreamReader stream, BoxHeader header, int itemIdBitCount) {
        super(header);

        fromItemId = stream.readLong(itemIdBitCount);
        int referenceCount = stream.read(16);
        requireEntriesFit(referenceCount, itemIdBitCount / 8);
        toItemIds = new long[referenceCount];
        for (int i = 0; i < referenceCount; i++) {
            toItemIds[i] = stream.readLong(itemIdBitCount);
        }
    }

    public final long getFromItemId() {
        return fromItemId;
    }

    /**
     * The referenced items, in order; a copy.
     */
    public final long[] getToItemIds() {
        return toItemIds.clone();
    }
}
