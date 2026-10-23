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
 * The header that starts every box (ISO/IEC 14496-12, 4.2).
 *
 * @param type the box type
 * @param offset where the box starts, in bytes from the start of the file
 * @param size the size of the box in bytes, header included
 * @param headerSize the size of the header in bytes
 */
record BoxHeader(BoxType type, long offset, long size, int headerSize) {
    private static final int USER_TYPE_BYTE_COUNT = 16;

    /**
     * Reads the header of the next box, which must fit in the file.
     */
    static BoxHeader read(BitStreamReader stream) {
        long offset = stream.getBitPosition() / 8;
        long size = stream.readLong(32);
        BoxType type = BoxType.codeToType(stream.read(32));

        if (size == 1) {
            size = stream.readLong(64);
        } else if (size == 0) {
            // the last box of the file, extending to its end
            size = stream.getLength() - offset;
        }
        if (type == BoxType.uuid) {
            // the extended type, which no box read here has
            stream.skipBits(USER_TYPE_BYTE_COUNT * 8);
        }

        int headerSize = (int) (stream.getBitPosition() / 8 - offset);
        // a 64-bit size reads as negative from 2^63 on
        if (size < headerSize || size > stream.getLength() - offset) {
            throw new IsoBmffFormatException("A %s box of %d bytes at byte %d does not fit the %d-byte file"
                    .formatted(type, size, offset, stream.getLength()));
        }
        return new BoxHeader(type, offset, size, headerSize);
    }

    /**
     * Reads the header of the next box, which must be of {@code expectedType} and fit in the file.
     */
    static BoxHeader read(BitStreamReader stream, BoxType expectedType) {
        BoxHeader header = read(stream);
        if (header.type != expectedType) {
            throw new IsoBmffFormatException("Expected a %s box, found %s".formatted(expectedType, header.type));
        }
        return header;
    }

    long endOffset() {
        return offset + size;
    }
}
