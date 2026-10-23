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
 * Groups of entities, here items, that share a characteristic or a relationship (ISO/IEC 14496-12, 8.18).
 */
public class GroupsListBox extends Box {
    private final List<EntityToGroupBox> entityGroups;

    GroupsListBox(BitStreamReader stream, BoxHeader header) {
        super(header);

        List<EntityToGroupBox> readEntityGroups = new ArrayList<>();
        while (hasMoreData(stream)) {
            readEntityGroups.add(parseBox(stream, EntityToGroupBox::new));
        }
        entityGroups = List.copyOf(readEntityGroups);
    }

    public final List<EntityToGroupBox> getEntityGroups() {
        return entityGroups;
    }
}
