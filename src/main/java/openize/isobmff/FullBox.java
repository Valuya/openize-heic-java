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

import java.util.BitSet;
import openize.isobmff.io.BitStreamReader;

/**
 * <p>
 * Structure for storing data in IsoBmff files with specified box version and flags.
 * </p>
 */
public class FullBox extends Box {
    /**
     * <p>
     * An integer that specifies the version of this format of the box.
     * </p>
     */
    protected final byte version;

    /**
     * <p>
     * A map of flags.
     * </p>
     */
    protected final BitSet flags = new BitSet(24);

    FullBox(BitStreamReader stream, BoxHeader header) {
        super(header);

        version = (byte) stream.read(8);

        for (int i = 0; i < 24; i++) {
            flags.set(i, stream.readFlag());
        }
    }
}
