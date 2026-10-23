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
 * Where the data of each item is (ISO/IEC 14496-12, 8.11.3): its extents, in this file or in the item data box.
 */
public class ItemLocationBox extends FullBox {
    private final List<IlocItem> items;

    ItemLocationBox(BitStreamReader stream, BoxHeader header) {
        super(stream, header);

        // the sizes, in bytes, of the fields that follow
        int offsetSize = stream.read(4);
        int lengthSize = stream.read(4);
        int baseOffsetSize = stream.read(4);
        int indexSize = 0;
        if (hasConstructionMethod()) {
            indexSize = stream.read(4);
        } else {
            stream.skipBits(4); // reserved
        }
        ItemFieldSizes fieldSizes = new ItemFieldSizes(offsetSize, lengthSize, baseOffsetSize, indexSize);

        // a version after 2 is not understood: it lists no item
        long itemCount = switch (getVersion()) {
            case 0, 1 -> stream.readLong(16);
            case 2 -> stream.readLong(32);
            default -> 0;
        };
        // an item takes at least 6 bytes (id, data reference index, extent count): bounds the count before allocating
        requireEntriesFit(itemCount, 6);
        List<IlocItem> readItems = new ArrayList<>((int) itemCount);
        for (long i = 0; i < itemCount; i++) {
            readItems.add(readItem(stream, fieldSizes));
        }
        items = List.copyOf(readItems);
    }

    public final List<IlocItem> getItems() {
        return items;
    }

    private IlocItem readItem(BitStreamReader stream, ItemFieldSizes fieldSizes) {
        long itemId = stream.readLong(getVersion() < 2 ? 16 : 32);
        int constructionMethod = 0;
        if (hasConstructionMethod()) {
            stream.skipBits(12); // reserved
            constructionMethod = stream.read(4);
        }
        stream.skipBits(16); // data_reference_index
        long baseOffset = stream.readLong(fieldSizes.baseOffsetSize() * 8);

        int extentCount = stream.read(16);
        requireEntriesFit(extentCount, Math.max(1, fieldSizes.extentSize()));
        List<IlocItemExtent> extents = new ArrayList<>(extentCount);
        for (int i = 0; i < extentCount; i++) {
            stream.skipBits(fieldSizes.indexSize() * 8); // extent_index
            long offset = stream.readLong(fieldSizes.offsetSize() * 8);
            long length = stream.readLong(fieldSizes.lengthSize() * 8);
            extents.add(new IlocItemExtent(offset, length));
        }
        return new IlocItem(itemId, constructionMethod, baseOffset, extents);
    }

    // versions 1 and 2 add the construction method, and the index size it may need
    private boolean hasConstructionMethod() {
        return getVersion() == 1 || getVersion() == 2;
    }

    private record ItemFieldSizes(int offsetSize, int lengthSize, int baseOffsetSize, int indexSize) {
        int extentSize() {
            return offsetSize + lengthSize + indexSize;
        }
    }
}
