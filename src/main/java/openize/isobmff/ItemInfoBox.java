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

import java.util.ArrayList;
import java.util.List;
import openize.isobmff.io.BitStreamReader;

/**
 * Information on each item of the file (ISO/IEC 14496-12, 8.11.6).
 */
public class ItemInfoBox extends FullBox {
    private final List<ItemInfoEntry> entries;

    ItemInfoBox(BitStreamReader stream, BoxHeader header) {
        super(stream, header);

        long entryCount = stream.readLong(getVersion() == 0 ? 16 : 32);
        // an entry is a box of at least 12 bytes
        requireEntriesFit(entryCount, 12);
        List<ItemInfoEntry> readEntries = new ArrayList<>((int) entryCount);
        for (long i = 0; i < entryCount; i++) {
            readEntries.add(parseBox(stream, BoxType.infe, ItemInfoEntry::new));
        }
        entries = List.copyOf(readEntries);
    }

    /**
     * The entries, by increasing item id.
     */
    public final List<ItemInfoEntry> getEntries() {
        return entries;
    }
}
