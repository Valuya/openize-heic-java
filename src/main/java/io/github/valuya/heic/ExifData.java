/*
 * Openize.HEIC
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of Openize.HEIC.
 *
 * Openize.HEIC is available under Openize license, which is
 * available along with Openize.HEIC sources.
 */

package io.github.valuya.heic;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Tag;
import com.drew.metadata.file.FileTypeDirectory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * The Exif metadata of an image, read with metadata-extractor: raw, or tag by tag, by the class of the
 * metadata-extractor directory that holds the tag, such as {@code ExifIFD0Directory} or {@code GpsDirectory}.
 */
public final class ExifData {
    // the exif_tiff_header_offset field that starts an Exif item (ISO/IEC 23008-12, A.2.1)
    private static final int TIFF_HEADER_OFFSET_BYTE_COUNT = 4;

    private final List<Directory> directories;
    private final byte[] rawBytes;

    /**
     * Reads the Exif item of image {@code imageId}: a 4-byte offset to the TIFF header, then the Exif data.
     */
    ExifData(long imageId, byte[] exifItemBytes) {
        if (exifItemBytes.length < TIFF_HEADER_OFFSET_BYTE_COUNT) {
            throw new HeicFormatException(
                    "The Exif item of image %d has %d bytes".formatted(imageId, exifItemBytes.length));
        }
        // the bytes between this field and the TIFF header, usually 6 for an "Exif\0\0" prefix
        long tiffHeaderOffset = ByteBuffer.wrap(exifItemBytes).getInt() & 0xFFFFFFFFL;
        long tiffHeaderStart = TIFF_HEADER_OFFSET_BYTE_COUNT + tiffHeaderOffset;
        if (tiffHeaderStart > exifItemBytes.length) {
            throw new HeicFormatException("Exif TIFF header offset %d past the end of the %d-byte item of image %d"
                    .formatted(tiffHeaderOffset, exifItemBytes.length, imageId));
        }

        rawBytes = Arrays.copyOfRange(exifItemBytes, (int) tiffHeaderStart, exifItemBytes.length);
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

    public List<Directory> getDirectories() {
        return directories;
    }

    /**
     * The tags of every directory.
     */
    public List<Tag> getTags() {
        return directories.stream()
                .flatMap(directory -> directory.getTags().stream())
                .toList();
    }

    /**
     * The Exif data from its TIFF header on; a copy.
     */
    public byte[] getRawBytes() {
        return rawBytes.clone();
    }

    /**
     * The description of a tag of the first directory of a class, if there is one.
     */
    public Optional<String> getDescriptionOptional(Class<? extends Directory> directoryClass, int tagType) {
        return findDirectoryOptional(directoryClass).map(directory -> directory.getDescription(tagType));
    }

    /**
     * The value of a tag of the first directory of a class, if there is one.
     */
    public Optional<Object> getValueOptional(Class<? extends Directory> directoryClass, int tagType) {
        return findDirectoryOptional(directoryClass).map(directory -> directory.getObject(tagType));
    }

    private Optional<Directory> findDirectoryOptional(Class<? extends Directory> directoryClass) {
        return directories.stream().filter(directoryClass::isInstance).findFirst();
    }
}
