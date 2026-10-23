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
 * The typed references from items to other items (ISO/IEC 14496-12, 8.11.12), grouped by referring item and type.
 */
public class ItemReferenceBox extends FullBox {
    private final List<SingleItemTypeReferenceBox> references;

    ItemReferenceBox(BitStreamReader stream, BoxHeader header) {
        super(stream, header);

        int itemIdBitCount = getVersion() == 0 ? 16 : 32;
        List<SingleItemTypeReferenceBox> readReferences = new ArrayList<>();
        while (hasMoreData(stream)) {
            readReferences.add(parseBox(
                    stream,
                    (referenceStream, referenceHeader) ->
                            new SingleItemTypeReferenceBox(referenceStream, referenceHeader, itemIdBitCount)));
        }
        references = List.copyOf(readReferences);
    }

    public final List<SingleItemTypeReferenceBox> getReferences() {
        return references;
    }
}
