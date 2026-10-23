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
 * <p>
 * An entity group is a grouping of items, which may also group tracks. The entities in an entity group
 * share a particular characteristic or have a particular relationship, as indicated by the grouping type.
 * </p>
 */
public class GroupsListBox extends Box {
    /**
     * <p>
     * List of nested boxes.
     * </p>
     */
    public final List<EntityToGroupBox> boxes;

    /**
     * <p>
     *     Observable collection of the nested boxes.
     * </p>
     */
    private ObservableCollection<EntityToGroupBox> children;

    /**
     * <p>
     * Create the box object from the bitstream, box size and start position.
     * </p>
     *
     * @param stream   File stream.
     * @param header   Box header.
     */
    GroupsListBox(BitStreamReader stream, BoxHeader header) {
        super(header);

        boxes = new ArrayList<>();

        while (hasMoreData(stream)) {
            boxes.add(Box.parseBox(stream, EntityToGroupBox::new));
        }

        setChildren(new ObservableCollection<>(boxes));
    }

    /**
     * <p>
     * Text summary of the box.
     * </p>
     */
    @Override
    public String toString() {
        return this.type.name();
    }

    /**
     * <p>
     * Observable collection of the nested boxes.
     * </p>
     */
    public final ObservableCollection<EntityToGroupBox> getChildren() {
        return children;
    }

    /**
     * <p>
     * Observable collection of the nested boxes.
     * </p>
     */
    public final void setChildren(ObservableCollection<EntityToGroupBox> value) {
        children = value;
    }
}
