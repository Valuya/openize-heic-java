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
 * The protection schemes that items can refer to (ISO/IEC 14496-12, 8.11.5).
 */
public class ItemProtectionBox extends FullBox {
    private final int protectionSchemeCount;

    ItemProtectionBox(BitStreamReader stream, BoxHeader header) {
        super(stream, header);
        // the protection scheme boxes that follow are skipped: protected items are not decoded
        protectionSchemeCount = stream.read(16);
    }

    public final int getProtectionSchemeCount() {
        return protectionSchemeCount;
    }
}
