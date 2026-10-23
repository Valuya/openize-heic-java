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
 * The item location box provides a directory of resources in this or other files, by locating their container,
 * their offset within that container, and their length.
 * </p>
 */
public class ItemLocationBox extends FullBox {
    /**
     * <p>
     * Indicates the length in bytes of the offset field.
     * </p>
     */
    public final byte offsetSize;

    /**
     * <p>
     * Indicates the length in bytes of the length field.
     * </p>
     */
    public final byte lengthSize;

    /**
     * <p>
     * Indicates the length in bytes of the base_offset field.
     * </p>
     */
    public final byte baseOffsetSize;

    /**
     * <p>
     * Indicates the length in bytes of the extent.index field.
     * </p>
     */
    public byte indexSize;

    /**
     * <p>
     * Counts the number of items in the location item array.
     * </p>
     */
    public /*UInt32*/ long itemCount;

    /**
     * <p>
     * Array of the location items.
     * </p>
     */
    public final IlocItem[] items;

    private ObservableCollection<IlocItem> children;

    /**
     * <p>
     * Create the box object from the bitstream and box size.
     * </p>
     *
     * @param stream File stream.
     * @param size   Box size in bytes.
     */
    public ItemLocationBox(BitStreamReader stream, /*UInt64*/ long size) {
        super(stream, BoxType.iloc, size);

        offsetSize = (byte) stream.read(4);
        lengthSize = (byte) stream.read(4);
        baseOffsetSize = (byte) stream.read(4);

        if (((version & 0xFF) == 1) || ((version & 0xFF) == 2)) {
            indexSize = (byte) stream.read(4);
        } else {
            stream.skipBits(4);
        }

        if ((version & 0xFF) < 2) {
            itemCount = stream.read(16) & 0xFFFFFFFFL;
        } else if ((version & 0xFF) == 2) {
            itemCount = stream.read(32) & 0xFFFFFFFFL;
        }

        items = new IlocItem[(int) (itemCount & 0xFFFFFFFFL)];

        for (int i = 0; i < (itemCount & 0xFFFFFFFFL); i++) {
            items[i] = new IlocItem();
            if ((version & 0xFF) < 2) {
                items[i].itemID = stream.read(16) & 0xFFFFFFFFL;
            } else if ((version & 0xFF) == 2) {
                items[i].itemID = stream.read(32) & 0xFFFFFFFFL;
            }

            if (((version & 0xFF) == 1) || ((version & 0xFF) == 2)) {
                stream.skipBits(12); // 0x000
                items[i].constructionMethod = (byte) stream.read(4);
            }

            items[i].dataReferenceIndex = stream.read(16) & 0xFFFFFFFFL;
            items[i].baseOffset = stream.read((baseOffsetSize & 0xFF) * 8) & 0xFFFFFFFFL;
            items[i].extentCount = stream.read(16) & 0xFFFFFFFFL;
            items[i].extents = new IlocItemExtent[(int) (items[i].extentCount)];

            for (int j = 0; j < (items[i].extentCount & 0xFFFFFFFFL); j++) {
                items[i].extents[j] = new IlocItemExtent();

                if ((((version & 0xFF) == 1) || ((version & 0xFF) == 2)) && ((indexSize & 0xFF) > 0)) {
                    items[i].extents[j].index = stream.read((indexSize & 0xFF) * 8) & 0xFFFFFFFFL;
                }

                items[i].extents[j].offset = stream.read((offsetSize & 0xFF) * 8) & 0xFFFFFFFFL;
                items[i].extents[j].length = stream.read((lengthSize & 0xFF) * 8) & 0xFFFFFFFFL;
            }
        }

        setChildren(new ObservableCollection<>(items));
    }

    /**
     * <p>
     * Text summary of the box.
     * </p>
     */
    @Override
    public String toString() {
        return String.format("%s count: %d", this.type, itemCount);
    }

    /**
     * <p>
     * Observable collection of the location items.
     * </p>
     */
    public final ObservableCollection<IlocItem> getChildren() {
        return children;
    }

    /**
     * <p>
     * Observable collection of the location items.
     * </p>
     */
    public final void setChildren(ObservableCollection<IlocItem> value) {
        children = value;
    }
}
