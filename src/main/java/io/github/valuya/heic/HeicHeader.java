/*
 * Openize.HEIC
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of Openize.HEIC.
 *
 * Openize.HEIC is available under Openize license, which is
 * available along with Openize.HEIC sources.
 */

package io.github.valuya.heic;

import io.github.valuya.heic.isobmff.Box;
import io.github.valuya.heic.isobmff.BoxType;
import io.github.valuya.heic.isobmff.EntityToGroupBox;
import io.github.valuya.heic.isobmff.GroupsListBox;
import io.github.valuya.heic.isobmff.IlocItem;
import io.github.valuya.heic.isobmff.ItemDataBox;
import io.github.valuya.heic.isobmff.ItemInfoBox;
import io.github.valuya.heic.isobmff.ItemInfoEntry;
import io.github.valuya.heic.isobmff.ItemPropertiesBox;
import io.github.valuya.heic.isobmff.ItemProtectionBox;
import io.github.valuya.heic.isobmff.MetaBox;
import io.github.valuya.heic.isobmff.PrimaryItemBox;
import io.github.valuya.heic.isobmff.SingleItemTypeReferenceBox;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The description of the items of a HEIC file, read from its meta box: their types, locations, properties and
 * references.
 */
public class HeicHeader {
    private final MetaBox meta;
    private final List<ItemInfoEntry> itemInfoEntries;

    HeicHeader(MetaBox meta) {
        this.meta = meta;
        itemInfoEntries = meta.getItemInfoBoxOptional()
                .map(ItemInfoBox::getEntries)
                .orElseThrow(() -> new HeicFormatException("No item information box (iinf)"));
        int protectionSchemeCount = meta.getItemProtectionBoxOptional()
                .map(ItemProtectionBox::getProtectionSchemeCount)
                .orElse(0);
        if (protectionSchemeCount > 0) {
            throw new HeicFormatException("Protected items are not supported");
        }
    }

    public final MetaBox getMeta() {
        return meta;
    }

    /**
     * The id of the primary item, the image shown by default; 0 when the file names none.
     */
    public final long getPrimaryItemId() {
        return meta.getPrimaryItemBoxOptional().map(PrimaryItemBox::getItemId).orElse(0L);
    }

    /**
     * Every item, including derived images that have no data of their own.
     */
    final List<ItemInfoEntry> getItemInfoEntries() {
        return itemInfoEntries;
    }

    /**
     * The properties of each item, by item id.
     */
    final Map<Long, List<Box>> getProperties() {
        return meta.getItemPropertiesBoxOptional()
                .map(ItemPropertiesBox::getProperties)
                .orElse(Map.of());
    }

    final ItemInfoEntry getInfoBoxById(long id) {
        return itemInfoEntries.stream()
                .filter(entry -> entry.getItemId() == id)
                .findFirst()
                .orElseThrow(() -> new HeicFormatException("No item information for item %d".formatted(id)));
    }

    /**
     * Where the data of an item is; empty for an item without data of its own, such as a derived image.
     */
    final Optional<IlocItem> getLocationBoxByIdOptional(long id) {
        return meta.getItemLocationBoxOptional().stream()
                .flatMap(locationBox -> locationBox.getItems().stream())
                .filter(item -> item.itemId() == id)
                .findFirst();
    }

    /**
     * A copy of {@code length} bytes of the item data box from {@code offset}; empty when the file has no item data
     * box.
     */
    final byte[] getItemDataBoxContent(long offset, long length) {
        return meta.getItemDataBoxOptional()
                .map(itemDataBox -> copyItemData(itemDataBox, offset, length))
                .orElseGet(() -> new byte[0]);
    }

    /**
     * The items the first reference of this item refers to, whatever the reference type: the input images of a
     * derived image, the image a thumbnail or an auxiliary image belongs to, or the image Exif data describes.
     */
    final long[] getDerivedList(long id) {
        return findFirstReferenceOptional(id)
                .map(SingleItemTypeReferenceBox::getToItemIds)
                .orElse(new long[0]);
    }

    /**
     * The type of the first reference of this item, such as {@link BoxType#dimg} or {@link BoxType#thmb}.
     */
    final Optional<BoxType> getDerivedTypeOptional(long id) {
        return findFirstReferenceOptional(id).map(Box::getType);
    }

    final List<EntityToGroupBox> getEntityGroups() {
        return meta.getGroupsListBoxOptional()
                .map(GroupsListBox::getEntityGroups)
                .orElse(List.of());
    }

    private static byte[] copyItemData(ItemDataBox itemDataBox, long offset, long length) {
        int dataLength = itemDataBox.getDataLength();
        // offset and length come from 64-bit fields: compared without adding them, which could overflow
        if (offset < 0 || length < 0 || offset > dataLength || length > dataLength - offset) {
            throw new HeicFormatException("Item data at %d, %d bytes long, is outside the %d-byte idat box"
                    .formatted(offset, length, dataLength));
        }
        return itemDataBox.copyData((int) offset, (int) length);
    }

    private Optional<SingleItemTypeReferenceBox> findFirstReferenceOptional(long id) {
        return meta.getItemReferenceBoxOptional().stream()
                .flatMap(referenceBox -> referenceBox.getReferences().stream())
                .filter(reference -> reference.getFromItemId() == id)
                .findFirst();
    }
}
