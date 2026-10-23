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
 * The type of an auxiliary image, such as an alpha plane or a depth map (ISO/IEC 23008-12, 6.5.8).
 */
public class AuxiliaryTypeProperty extends FullBox {
    private final String auxType;

    AuxiliaryTypeProperty(BitStreamReader stream, BoxHeader header) {
        super(stream, header);
        // aux_subtype, whose meaning depends on the type, fills the rest of the box
        auxType = stream.readString();
    }

    /**
     * The URN of the type, such as {@code urn:mpeg:hevc:2015:auxid:1} for an alpha plane, with its terminating
     * {@code '\0'}.
     */
    public final String getAuxType() {
        return auxType;
    }
}
