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
 * A box, the building block of ISO base media files (ISO/IEC 14496-12, 4.2). Boxes of a type this package does not
 * parse are read as a plain {@code Box}, whose content starts at {@link #getContentOffset()}.
 */
public class Box {
    private final BoxType type;
    // where the box starts, in bytes from the start of the file
    private final long offset;
    // header included
    private final long size;
    private final int headerSize;

    Box(BoxHeader header) {
        type = header.type();
        offset = header.offset();
        size = header.size();
        headerSize = header.headerSize();
    }

    /**
     * Reads the next box and leaves the stream at its end.
     */
    public static Box parseBox(BitStreamReader stream) {
        BoxHeader header = BoxHeader.read(stream);
        Box box = switch (header.type()) {
            case ftyp -> new FileTypeBox(stream, header);
            case meta -> new MetaBox(stream, header);
            case pitm -> new PrimaryItemBox(stream, header);
            case iloc -> new ItemLocationBox(stream, header);
            case ipro -> new ItemProtectionBox(stream, header);
            case iinf -> new ItemInfoBox(stream, header);
            case iref -> new ItemReferenceBox(stream, header);
            case idat -> new ItemDataBox(stream, header);
            case iprp -> new ItemPropertiesBox(stream, header);
            case grpl -> new GroupsListBox(stream, header);
            case ispe -> new ImageSpatialExtentsProperty(stream, header);
            case pasp -> new PixelAspectRatioBox(stream, header);
            case colr -> new ColourInformationBox(stream, header);
            case pixi -> new PixelInformationProperty(stream, header);
            case auxC -> new AuxiliaryTypeProperty(stream, header);
            case clap -> new CleanApertureBox(stream, header);
            case irot -> new ImageRotation(stream, header);
            case imir -> new ImageMirror(stream, header);
            default -> new Box(header);
        };
        box.skipToEnd(stream);
        return box;
    }

    /**
     * Reads the next box, which must be of {@code expectedType}, with {@code reader}, and leaves the stream at its
     * end.
     */
    static <T extends Box> T parseBox(BitStreamReader stream, BoxType expectedType, BoxReader<T> reader) {
        T box = reader.read(stream, BoxHeader.read(stream, expectedType));
        box.skipToEnd(stream);
        return box;
    }

    /**
     * Reads the next box, whatever its type, with {@code reader}, and leaves the stream at its end.
     */
    static <T extends Box> T parseBox(BitStreamReader stream, BoxReader<T> reader) {
        T box = reader.read(stream, BoxHeader.read(stream));
        box.skipToEnd(stream);
        return box;
    }

    public final BoxType getType() {
        return type;
    }

    /**
     * The size of the box in bytes, header included.
     */
    public final long getSize() {
        return size;
    }

    /**
     * Where the content of the box starts, after its header, in bytes from the start of the file.
     */
    public final long getContentOffset() {
        return offset + headerSize;
    }

    /**
     * The size of the content of the box in bytes, after its header.
     */
    public final long getContentLength() {
        return size - headerSize;
    }

    final long getEndOffset() {
        return offset + size;
    }

    /**
     * Whether the stream is still inside this box.
     */
    final boolean hasMoreData(BitStreamReader stream) {
        return stream.getBitPosition() < getEndOffset() * 8;
    }

    /**
     * Fails unless {@code count} entries of at least {@code bytesPerEntry} bytes fit in this box: a count read from the
     * file is checked this way before anything is allocated for it.
     */
    final void requireEntriesFit(long count, int bytesPerEntry) {
        if (count < 0 || count * bytesPerEntry > size) {
            throw new IsoBmffFormatException(
                    "A %s box of %d bytes cannot hold %d entries".formatted(type, size, count));
        }
    }

    /**
     * Skips to the end of the box, past any field that was not read.
     */
    final void skipToEnd(BitStreamReader stream) {
        if (stream.getBitPosition() > getEndOffset() * 8) {
            throw new IsoBmffFormatException("The %s box overruns its %d bytes".formatted(type, size));
        }
        stream.setBytePosition(getEndOffset());
    }

    /**
     * The four characters of a four-character code, such as a box type or a brand.
     */
    static String fourCharacterCodeText(long code) {
        char[] characters = new char[4];
        for (int i = 0; i < characters.length; i++) {
            characters[i] = (char) ((code >> (24 - 8 * i)) & 0xFF);
        }
        return new String(characters);
    }

    @Override
    public String toString() {
        return "%s box of %d bytes".formatted(type, size);
    }

    /**
     * Reads the content of a box whose header was read.
     */
    @FunctionalInterface
    interface BoxReader<T extends Box> {
        T read(BitStreamReader stream, BoxHeader header);
    }
}
