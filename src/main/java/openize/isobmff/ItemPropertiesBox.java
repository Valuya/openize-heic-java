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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import openize.isobmff.io.BitStreamReader;

/**
 * The properties of items (ISO/IEC 23008-12, 9.3): an indexed list of properties, and the properties each item is
 * associated with.
 */
public class ItemPropertiesBox extends Box {
    private final ItemPropertyContainerBox propertyContainer;
    private final List<ItemPropertyAssociation> associations;

    ItemPropertiesBox(BitStreamReader stream, BoxHeader header) {
        super(header);

        propertyContainer = parseBox(stream, BoxType.ipco, ItemPropertyContainerBox::new);
        List<ItemPropertyAssociation> readAssociations = new ArrayList<>();
        while (hasMoreData(stream)) {
            readAssociations.add(parseBox(stream, BoxType.ipma, ItemPropertyAssociation::new));
        }
        associations = List.copyOf(readAssociations);
    }

    /**
     * The properties of each item, by item id, in the order they apply.
     */
    public final Map<Long, List<Box>> getProperties() {
        Map<Long, List<Box>> propertiesByItemId = new HashMap<>();
        for (ItemPropertyAssociation association : associations) {
            for (ItemPropertyAssociation.Entry entry : association.getEntries()) {
                // a later association of the item replaces an earlier one
                propertiesByItemId.put(entry.itemId(), getProperties(entry));
            }
        }
        return Map.copyOf(propertiesByItemId);
    }

    private List<Box> getProperties(ItemPropertyAssociation.Entry entry) {
        List<Box> properties = new ArrayList<>();
        for (int propertyIndex : entry.propertyIndices()) {
            // index 0 associates no property
            if (propertyIndex != 0) {
                properties.add(propertyContainer
                        .findPropertyOptional(propertyIndex)
                        .orElseThrow(() -> new IsoBmffFormatException(
                                "Item %d is associated with property %d, which does not exist"
                                        .formatted(entry.itemId(), propertyIndex))));
            }
        }
        return List.copyOf(properties);
    }
}
