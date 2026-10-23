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
import java.util.Optional;
import openize.isobmff.io.BitStreamReader;

/**
 * The properties that items are associated with by index (ISO/IEC 23008-12, 9.3).
 */
final class ItemPropertyContainerBox extends Box {
    private final List<Box> properties;

    ItemPropertyContainerBox(BitStreamReader stream, BoxHeader header) {
        super(header);

        List<Box> readProperties = new ArrayList<>();
        while (hasMoreData(stream)) {
            readProperties.add(parseBox(stream));
        }
        properties = List.copyOf(readProperties);
    }

    /**
     * The property with this index, counted from 1.
     */
    Optional<Box> findPropertyOptional(int index) {
        if (index < 1 || index > properties.size()) {
            return Optional.empty();
        }
        return Optional.of(properties.get(index - 1));
    }
}
