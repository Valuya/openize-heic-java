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
 * Information on one item (ISO/IEC 14496-12, 8.11.6): its type, and whether it is hidden.
 */
public class ItemInfoEntry extends FullBox {
    private final long itemId;
    private final long itemType;

    ItemInfoEntry(BitStreamReader stream, BoxHeader header) {
        super(stream, header);

        // the names and content types that follow the fields read here are skipped with the rest of the box
        if (getVersion() < 2) {
            itemId = stream.readLong(16);
            itemType = 0;
        } else {
            itemId = stream.readLong(getVersion() == 2 ? 16 : 32);
            stream.skipBits(16); // item_protection_index
            itemType = stream.readLong(32);
        }
    }

    public final long getItemId() {
        return itemId;
    }

    /**
     * The type of the item, a four-character code such as {@code hvc1}, {@code grid} or {@code Exif}; 0 in a version
     * 0 or 1 entry, which has none.
     */
    public final long getItemType() {
        return itemType;
    }

    /**
     * Whether the item is hidden, not meant to be displayed on its own: (flags &amp; 1).
     */
    public final boolean isHidden() {
        return (getFlags() & 1) != 0;
    }
}
