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
import java.util.ArrayList;
import java.util.List;

/**
 * Associates items with properties, by their index in the property container (ISO/IEC 23008-12, 9.3).
 */
final class ItemPropertyAssociation extends FullBox {
    private final List<Entry> entries;

    ItemPropertyAssociation(BitStreamReader stream, BoxHeader header) {
        super(stream, header);

        int itemIdBitCount = getVersion() < 1 ? 16 : 32;
        // (flags & 1) selects 15-bit property indices
        int propertyIndexBitCount = (getFlags() & 1) != 0 ? 15 : 7;

        long entryCount = stream.readLong(32);
        // an entry takes at least 3 bytes (item id, association count)
        requireEntriesFit(entryCount, 3);
        List<Entry> readEntries = new ArrayList<>((int) entryCount);
        for (long i = 0; i < entryCount; i++) {
            long itemId = stream.readLong(itemIdBitCount);
            int associationCount = stream.read(8);
            requireEntriesFit(associationCount, (1 + propertyIndexBitCount) / 8);
            List<Integer> propertyIndices = new ArrayList<>(associationCount);
            for (int j = 0; j < associationCount; j++) {
                stream.skipBits(1); // essential
                propertyIndices.add(stream.read(propertyIndexBitCount));
            }
            readEntries.add(new Entry(itemId, propertyIndices));
        }
        entries = List.copyOf(readEntries);
    }

    List<Entry> getEntries() {
        return entries;
    }

    /**
     * The properties of an item, by index in the property container, in the order they apply.
     *
     * @param itemId the item
     * @param propertyIndices the indices of its properties, counted from 1; 0 associates no property
     */
    record Entry(long itemId, List<Integer> propertyIndices) {
        Entry {
            propertyIndices = List.copyOf(propertyIndices);
        }
    }
}
