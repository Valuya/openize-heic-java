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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import openize.heic.decoder.io.BitStreamWithNalSupport;
import openize.isobmff.Box;
import openize.isobmff.BoxType;
import openize.isobmff.EntityToGroupBox;
import openize.isobmff.FileTypeBox;
import openize.isobmff.IsoBmffFormatException;
import openize.isobmff.ItemInfoEntry;
import openize.isobmff.MetaBox;

/**
 * A HEIF file of HEVC coded images, such as a HEIC photo: its items, and its primary image.
 *
 * <p>Loading reads the metadata only: an image is decoded when its pixels are asked for, as often as they are.
 * Instances can be shared between threads.
 */
public final class HeicImage {
    // HEVC image brands of ISO/IEC 23008-12, and the generic image and sequence brands
    private static final List<Long> HEVC_CAPABLE_BRANDS =
            List.of("heic", "heix", "heim", "heis", "hevc", "hevx", "hevm", "hevs", "mif1", "mif2", "msf1").stream()
                    .map(HeicImage::fourCharacterCode)
                    .toList();

    private final HeicHeader header;
    private final byte[] heicData;
    private final HeicDecodingOptions decodingOptions;
    private final Map<Long, HeicImageFrame> framesById = new LinkedHashMap<>();
    private final HeicImageFrame primaryFrame;

    private HeicImage(HeicHeader header, byte[] heicData, HeicDecodingOptions decodingOptions) {
        this.header = header;
        this.heicData = heicData;
        this.decodingOptions = decodingOptions;
        readFrames();
        primaryFrame = Optional.ofNullable(framesById.get(header.getPrimaryItemId()))
                .orElseThrow(() -> new HeicFormatException("No primary image"));
    }

    /**
     * Reads a HEIC file.
     *
     * @throws IOException if the file cannot be read
     * @throws HeicFormatException if it is not a HEIC image this decoder can read
     */
    public static HeicImage load(Path heicFile) throws IOException {
        return load(heicFile, HeicDecodingOptions.DEFAULT);
    }

    /**
     * Reads a HEIC file, to be decoded with the given options.
     *
     * @throws IOException if the file cannot be read
     * @throws HeicFormatException if it is not a HEIC image this decoder can read
     */
    public static HeicImage load(Path heicFile, HeicDecodingOptions decodingOptions) throws IOException {
        return load(Files.readAllBytes(heicFile), decodingOptions);
    }

    /**
     * Reads a HEIC image from a stream, which is read to its end but not closed.
     *
     * @throws IOException if the stream cannot be read
     * @throws HeicFormatException if it is not a HEIC image this decoder can read
     */
    public static HeicImage load(InputStream heicStream) throws IOException {
        return load(heicStream, HeicDecodingOptions.DEFAULT);
    }

    /**
     * Reads a HEIC image from a stream, which is read to its end but not closed, to be decoded with the given options.
     *
     * @throws IOException if the stream cannot be read
     * @throws HeicFormatException if it is not a HEIC image this decoder can read
     */
    public static HeicImage load(InputStream heicStream, HeicDecodingOptions decodingOptions) throws IOException {
        return load(heicStream.readAllBytes(), decodingOptions);
    }

    /**
     * Reads a HEIC image held in memory. The array is not copied and must not change afterwards.
     *
     * @throws HeicFormatException if it is not a HEIC image this decoder can read
     */
    public static HeicImage load(byte[] heicData) {
        return load(heicData, HeicDecodingOptions.DEFAULT);
    }

    /**
     * Reads a HEIC image held in memory, to be decoded with the given options. The array is not copied and must not
     * change afterwards.
     *
     * @throws HeicFormatException if it is not a HEIC image this decoder can read
     */
    public static HeicImage load(byte[] heicData, HeicDecodingOptions decodingOptions) {
        if (!canLoad(heicData)) {
            throw new HeicFormatException("Not a HEIC image");
        }
        try {
            return new HeicImage(readHeader(heicData), heicData, decodingOptions);
        } catch (IsoBmffFormatException exception) {
            throw new HeicFormatException("Malformed HEIF container: " + exception.getMessage(), exception);
        }
    }

    private static HeicHeader readHeader(byte[] heicData) {
        BitStreamWithNalSupport stream = new BitStreamWithNalSupport(heicData);
        while (stream.moreData()) {
            Box box = Box.parseBox(stream);
            if (box.getType() == BoxType.meta) {
                return new HeicHeader((MetaBox) box);
            }
        }
        throw new HeicFormatException("Meta box not found");
    }

    /**
     * Whether the data starts with a file type box listing a brand of HEIF images that may be coded in HEVC. Whether
     * the image actually is HEVC shows when it is decoded: AVIF also uses the generic {@code mif1} brand.
     */
    public static boolean canLoad(byte[] heicData) {
        try {
            Box box = Box.parseBox(new BitStreamWithNalSupport(heicData));
            return box instanceof FileTypeBox fileTypeBox
                    && HEVC_CAPABLE_BRANDS.stream().anyMatch(fileTypeBox::isBrandSupported);
        } catch (IsoBmffFormatException exception) {
            return false;
        }
    }

    private static long fourCharacterCode(String code) {
        long value = 0;
        for (char character : code.toCharArray()) {
            value = (value << 8) | character;
        }
        return value;
    }

    /** The primary image of the file, the one to show. */
    public HeicImageFrame getPrimaryFrame() {
        return primaryFrame;
    }

    /** The width of the primary image as displayed. */
    public int getWidth() {
        return primaryFrame.getWidth();
    }

    /** The height of the primary image as displayed. */
    public int getHeight() {
        return primaryFrame.getHeight();
    }

    /**
     * Decodes the primary image to 0xAARRGGBB pixels, row by row.
     *
     * @throws HeicFormatException if the image is not one this decoder can decode
     * @throws HeicImageTooLargeException if it has more pixels than the decoding options allow
     */
    public int[] decodeArgb() {
        return primaryFrame.decodeArgb();
    }

    /**
     * Decodes {@code region} of the primary image to 0xAARRGGBB pixels, row by row.
     *
     * @see HeicImageFrame#decodeArgb(PixelRegion)
     */
    public int[] decodeArgb(PixelRegion region) {
        return primaryFrame.decodeArgb(region);
    }

    /**
     * Decodes {@code region} of the primary image to 0xAARRGGBB pixels in {@code destination}.
     *
     * @see HeicImageFrame#decodeArgb(PixelRegion, int[], int, int)
     */
    public void decodeArgb(PixelRegion region, int[] destination, int offset, int scanlineStride) {
        primaryFrame.decodeArgb(region, destination, offset, scanlineStride);
    }

    /**
     * Decodes the primary image to pixels of {@code pixelFormat}, row by row.
     *
     * @see HeicImageFrame#decodeBytes(PixelFormat)
     */
    public byte[] decodeBytes(PixelFormat pixelFormat) {
        return primaryFrame.decodeBytes(pixelFormat);
    }

    /**
     * Decodes {@code region} of the primary image to pixels of {@code pixelFormat}, row by row.
     *
     * @see HeicImageFrame#decodeBytes(PixelFormat, PixelRegion)
     */
    public byte[] decodeBytes(PixelFormat pixelFormat, PixelRegion region) {
        return primaryFrame.decodeBytes(pixelFormat, region);
    }

    /**
     * The Exif metadata of the primary image, if the file has any.
     *
     * @throws HeicFormatException if the Exif item is malformed
     */
    public Optional<ExifData> getExifOptional() {
        return primaryFrame.getExifOptional();
    }

    /** The metadata of the file, as ISO base media file format boxes. */
    public HeicHeader getHeader() {
        return header;
    }

    /**
     * The images of the file meant to be shown, by item id: neither hidden, nor thumbnails, nor tone map
     * derivations, and only the first available of a group of alternatives.
     */
    public Map<Long, HeicImageFrame> getTopLevelFrames() {
        Map<Long, HeicImageFrame> topLevelFramesById = new LinkedHashMap<>();
        framesById.forEach((frameId, frame) -> {
            boolean thumbnail = frame.getDerivativeTypeOptional()
                    .filter(BoxType.thmb::equals)
                    .isPresent();
            if (!frame.isHidden() && !thumbnail && frame.getImageType() != ImageFrameType.tmap) {
                topLevelFramesById.put(frameId, frame);
            }
        });

        // ISO/IEC 14496-12, 8.18.3.2: of alternatives, the first one in the group is preferred
        for (EntityToGroupBox group : header.getEntityGroups()) {
            if (group.getType() == BoxType.altr) {
                boolean selected = false;
                for (long entityId : group.getEntityIds()) {
                    if (topLevelFramesById.containsKey(entityId)) {
                        if (selected) {
                            topLevelFramesById.remove(entityId);
                        }
                        selected = true;
                    }
                }
            }
        }
        return Collections.unmodifiableMap(topLevelFramesById);
    }

    /** Every item of the file, images and metadata, by item id. */
    public Map<Long, HeicImageFrame> getAllFrames() {
        return Collections.unmodifiableMap(framesById);
    }

    // The whole file: frames read their coded data from it when they decode
    byte[] getHeicData() {
        return heicData;
    }

    HeicDecodingOptions getDecodingOptions() {
        return decodingOptions;
    }

    // Every item, including derived images that have no data of their own, then their properties: properties link
    // items to each other, so every item exists first
    private void readFrames() {
        for (ItemInfoEntry itemInfo : header.getItemInfoEntries()) {
            framesById.put(itemInfo.getItemId(), new HeicImageFrame(this, itemInfo.getItemId()));
        }

        Map<Long, List<Box>> propertiesByItemId = header.getProperties();
        for (HeicImageFrame frame : framesById.values()) {
            Optional.ofNullable(propertiesByItemId.get(frame.getId())).ifPresent(frame::loadProperties);
            if (frame.getImageType() == ImageFrameType.exif) {
                for (long describedId : header.getDerivedList(frame.getId())) {
                    Optional.ofNullable(framesById.get(describedId))
                            .ifPresent(describedFrame -> describedFrame.setExifItem(frame));
                }
            }
        }
    }
}
