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
import java.util.Arrays;

/**
 * The brands of the specifications a file complies with (ISO/IEC 14496-12, 4.3). Each brand is a four-character
 * code.
 */
public class FileTypeBox extends Box {
    private final long majorBrand;
    private final long[] compatibleBrands;

    FileTypeBox(BitStreamReader stream, BoxHeader header) {
        super(header);

        majorBrand = stream.readLong(32);
        stream.skipBits(32); // minor_version

        // the compatible brands fill the rest of the box, whatever the size of its header
        long brandCount = (getEndOffset() * 8 - stream.getBitPosition()) / 32;
        if (brandCount < 0) {
            throw new IsoBmffFormatException("A ftyp box of %d bytes cannot hold its brands".formatted(getSize()));
        }
        compatibleBrands = new long[(int) brandCount];
        for (int i = 0; i < compatibleBrands.length; i++) {
            compatibleBrands[i] = stream.readLong(32);
        }
    }

    /**
     * Whether the file complies with {@code brand}, its major brand or one of its compatible brands.
     */
    public final boolean isBrandSupported(long brand) {
        return majorBrand == brand
                || Arrays.stream(compatibleBrands).anyMatch(compatibleBrand -> compatibleBrand == brand);
    }
}
