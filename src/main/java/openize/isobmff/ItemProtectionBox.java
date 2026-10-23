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
 * <p>
 * The item protection box provides an array of item protection information, for use by the Item Information Box.
 * </p>
 */
public class ItemProtectionBox extends FullBox {
    private final int protectionSchemeCount;

    /**
     * <p>
     * Create the box object from the bitstream and box size.
     * </p>
     *
     * @param stream File stream.
     * @param header Box header.
     */
    ItemProtectionBox(BitStreamReader stream, BoxHeader header) {
        super(stream, header);

        // the protection scheme boxes that follow are skipped: protected items are not decoded
        protectionSchemeCount = stream.read(16);
    }

    /**
     * The number of protection schemes, each applied to the items that refer to it.
     */
    public int getProtectionSchemeCount() {
        return protectionSchemeCount;
    }
}
