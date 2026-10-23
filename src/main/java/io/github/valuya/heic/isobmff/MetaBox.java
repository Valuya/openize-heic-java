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
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * The metadata of a file (ISO/IEC 14496-12, 8.11.1): for a HEIF image, its items, and the boxes that locate,
 * describe and relate them.
 */
public class MetaBox extends FullBox {
    private final Map<BoxType, Box> boxesByType = new EnumMap<>(BoxType.class);

    MetaBox(BitStreamReader stream, BoxHeader header) {
        super(stream, header);

        // the handler box comes first; nothing reads the type of metadata it declares
        parseBox(stream, BoxType.hdlr, (handlerStream, handlerHeader) -> new Box(handlerHeader));
        while (hasMoreData(stream)) {
            Box box = parseBox(stream);
            // a later box of a type replaces an earlier one
            boxesByType.put(box.getType(), box);
        }
    }

    public final Optional<PrimaryItemBox> getPrimaryItemBoxOptional() {
        return findBoxOptional(BoxType.pitm, PrimaryItemBox.class);
    }

    public final Optional<ItemProtectionBox> getItemProtectionBoxOptional() {
        return findBoxOptional(BoxType.ipro, ItemProtectionBox.class);
    }

    public final Optional<ItemLocationBox> getItemLocationBoxOptional() {
        return findBoxOptional(BoxType.iloc, ItemLocationBox.class);
    }

    public final Optional<ItemInfoBox> getItemInfoBoxOptional() {
        return findBoxOptional(BoxType.iinf, ItemInfoBox.class);
    }

    public final Optional<ItemPropertiesBox> getItemPropertiesBoxOptional() {
        return findBoxOptional(BoxType.iprp, ItemPropertiesBox.class);
    }

    public final Optional<ItemReferenceBox> getItemReferenceBoxOptional() {
        return findBoxOptional(BoxType.iref, ItemReferenceBox.class);
    }

    public final Optional<ItemDataBox> getItemDataBoxOptional() {
        return findBoxOptional(BoxType.idat, ItemDataBox.class);
    }

    public final Optional<GroupsListBox> getGroupsListBoxOptional() {
        return findBoxOptional(BoxType.grpl, GroupsListBox.class);
    }

    private <T extends Box> Optional<T> findBoxOptional(BoxType type, Class<T> boxClass) {
        return Optional.ofNullable(boxesByType.get(type)).map(boxClass::cast);
    }
}
