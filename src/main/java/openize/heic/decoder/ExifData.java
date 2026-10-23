/*
 * Openize.HEIC
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of Openize.HEIC.
 *
 * Openize.HEIC is available under Openize license, which is
 * available along with Openize.HEIC sources.
 */

package openize.heic.decoder;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Tag;
import com.drew.metadata.file.FileTypeDirectory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import openize.heic.decoder.io.BitStreamWithNalSupport;
import openize.isobmff.IlocItem;
import openize.isobmff.IlocItemExtent;

/**
 * The Exif metadata of an image, read with metadata-extractor: raw, or tag by tag.
 */
public class ExifData {
    // the exif_tiff_header_offset field that starts an Exif item (ISO/IEC 23008-12, A.2.1)
    private static final int TIFF_HEADER_OFFSET_BYTE_COUNT = 4;

    private final List<Directory> directories;
    private final byte[] rawBytes;

    ExifData(BitStreamWithNalSupport stream, HeicImage image, HeicImageFrame exifFrame) {
        IlocItem location = image.getHeader()
                .getLocationBoxByIdOptional(exifFrame.getID())
                .filter(itemLocation -> !itemLocation.extents().isEmpty())
                .orElseThrow(() -> new HeicFormatException("Exif item %d has no data".formatted(exifFrame.getID())));
        IlocItemExtent extent = location.extents().get(0);

        stream.setBytePosition(location.baseOffset() + extent.offset());
        long itemOffset = stream.getBitPosition() / 8;
        long itemLength = extent.length();
        if (itemLength < 0 || itemLength > stream.getLength() - itemOffset) {
            throw new HeicFormatException("The %d-byte Exif item at byte %d is outside the %d-byte file"
                    .formatted(itemLength, itemOffset, stream.getLength()));
        }

        // the bytes between this field and the TIFF header, usually 6 for an "Exif\0\0" prefix
        long tiffHeaderOffset = stream.readLong(TIFF_HEADER_OFFSET_BYTE_COUNT * 8);
        if (tiffHeaderOffset > itemLength - TIFF_HEADER_OFFSET_BYTE_COUNT) {
            throw new HeicFormatException("Exif TIFF header offset %d past the end of the %d-byte item"
                    .formatted(tiffHeaderOffset, itemLength));
        }
        stream.setBytePosition(stream.getBitPosition() / 8 + tiffHeaderOffset);

        rawBytes = stream.readBytes((int) (itemLength - TIFF_HEADER_OFFSET_BYTE_COUNT - tiffHeaderOffset));
        directories = readDirectories(rawBytes);
    }

    private static List<Directory> readDirectories(byte[] exifBytes) {
        try {
            Iterable<Directory> directories = ImageMetadataReader.readMetadata(new ByteArrayInputStream(exifBytes))
                    .getDirectories();
            // the file type metadata-extractor detects describes the Exif bytes, not the image
            return StreamSupport.stream(directories.spliterator(), false)
                    .filter(directory -> !(directory instanceof FileTypeDirectory))
                    .toList();
        } catch (ImageProcessingException | IOException exception) {
            throw new HeicFormatException("Unreadable Exif metadata: %s".formatted(exception.getMessage()), exception);
        }
    }

    public final List<Directory> getDirectoriesList() {
        return directories;
    }

    /**
     * The tags of every directory.
     */
    public final List<Tag> getTagList() {
        return directories.stream()
                .flatMap(directory -> directory.getTags().stream())
                .toList();
    }

    /**
     * The Exif data from its TIFF header on; a copy.
     */
    public final byte[] getRawBytes() {
        return rawBytes.clone();
    }

    /**
     * The description of a tag of the first directory of a class, if there is one.
     */
    public final Optional<String> getExifStringOptional(Class<? extends Directory> directoryClass, int tagType) {
        return findDirectoryOptional(directoryClass).map(directory -> directory.getDescription(tagType));
    }

    /**
     * The value of a tag of the first directory of a class, if there is one.
     */
    public final Optional<Object> getExifRawDataOptional(Class<? extends Directory> directoryClass, int tagType) {
        return findDirectoryOptional(directoryClass).map(directory -> directory.getObject(tagType));
    }

    /**
     * The description of a tag of the first directory of a type, if there is one.
     *
     * @throws UnsupportedOperationException for a type without a metadata-extractor directory
     */
    public final Optional<String> getExifStringOptional(ExifDirectoryType directoryType, int tagType) {
        return getExifStringOptional(getDirectoryClass(directoryType), tagType);
    }

    /**
     * The value of a tag of the first directory of a type, if there is one.
     *
     * @throws UnsupportedOperationException for a type without a metadata-extractor directory
     */
    public final Optional<Object> getExifRawDataOptional(ExifDirectoryType directoryType, int tagType) {
        return getExifRawDataOptional(getDirectoryClass(directoryType), tagType);
    }

    private Optional<Directory> findDirectoryOptional(Class<? extends Directory> directoryClass) {
        return directories.stream().filter(directoryClass::isInstance).findFirst();
    }

    private static Class<? extends Directory> getDirectoryClass(ExifDirectoryType directoryType) {
        return directoryType
                .getDirectoryClassOptional()
                .orElseThrow(() -> new UnsupportedOperationException(
                        "No metadata-extractor directory for %s".formatted(directoryType)));
    }
}
