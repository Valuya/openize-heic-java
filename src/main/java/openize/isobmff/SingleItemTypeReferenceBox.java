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
 * Collects all references for one item of a specific type.
 * </p>
 */
public class SingleItemTypeReferenceBox extends Box {
    /**
     * <p>
     * The ID of the item that refers to other items.
     * </p>
     */
    public final /*UInt32*/ long fromItemID;

    /**
     * <p>
     * The number of references.
     * </p>
     */
    public final /*UInt32*/ long referenceCount;

    /**
     * <p>
     * The array of the IDs of the item referred to.
     * </p>
     */
    public final /*UInt32*/ long[] toItemID;

    /**
     * <p>
     * Create the box object from the bitstream and item id size.
     * </p>
     *
     * @param stream  File stream.
     * @param id_size Item id size in bytes.
     */
    public SingleItemTypeReferenceBox(BitStreamReader stream, int idSize) {
        super(stream);

        fromItemID = stream.read(idSize) & 0xFFFFFFFFL;
        referenceCount = stream.read(16) & 0xFFFFFFFFL;

        toItemID = new /*UInt32*/ long[(int) (referenceCount & 0xFFFFFFFFL)];

        for (int j = 0; j < (referenceCount & 0xFFFFFFFFL); j++) {
            toItemID[j] = stream.read(idSize) & 0xFFFFFFFFL;
        }
    }

    /**
     * <p>
     * Text summary of the box.
     * </p>
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder(50);
        str.append(String.format("%1$s from_item_ID: %2$d refs: ", type, fromItemID));
        for (/*UInt32*/ long id : toItemID) {
            str.append(id).append(' ');
        }

        return str.toString();
    }
}
