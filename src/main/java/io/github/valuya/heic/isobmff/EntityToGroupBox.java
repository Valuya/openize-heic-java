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
 * A group of entities, here items (ISO/IEC 14496-12, 8.18). The box type is the grouping type, such as
 * {@link BoxType#altr} for alternatives of which one is shown.
 */
public class EntityToGroupBox extends FullBox {
    private final long[] entityIds;

    EntityToGroupBox(BitStreamReader stream, BoxHeader header) {
        super(stream, header);

        stream.skipBits(32); // group_id
        long entityCount = stream.readLong(32);
        // each entity id takes 4 bytes
        requireEntriesFit(entityCount, 4);
        entityIds = new long[(int) entityCount];
        for (int i = 0; i < entityIds.length; i++) {
            entityIds[i] = stream.readLong(32);
        }
        // a grouping type may add fields of its own, skipped with the rest of the box
    }

    /**
     * The grouped entities, in order; a copy.
     */
    public final long[] getEntityIds() {
        return entityIds.clone();
    }
}
