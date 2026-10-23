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

import java.util.List;

/**
 * Where the data of an item is (ISO/IEC 14496-12, 8.11.3).
 *
 * @param itemId the item
 * @param constructionMethod how the extents locate the data: 0 as offsets in the file, 1 as offsets in the item data
 *     box, 2 as items
 * @param baseOffset added to the offset of every extent
 * @param extents the pieces of the data, in order
 */
public record IlocItem(long itemId, int constructionMethod, long baseOffset, List<IlocItemExtent> extents) {
    public IlocItem {
        extents = List.copyOf(extents);
    }
}
