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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import openize.heic.decoder.io.BitStreamWithNalSupport;
import openize.isobmff.Box;
import openize.isobmff.BoxType;
import openize.isobmff.EntityToGroupBox;
import openize.isobmff.FileTypeBox;
import openize.isobmff.IsoBmffFormatException;
import openize.isobmff.ItemInfoEntry;
import openize.isobmff.MetaBox;

/**
 * <p>
 * Heic image class.
 * </p>
 */
public class HeicImage {
    /**
     * <p>
     * Dictionary of Heic image frames.
     * </p>
     */
    // HEVC image brands of ISO/IEC 23008-12, and the generic image and sequence brands
    private static final List<Long> HEVC_CAPABLE_BRANDS =
            List.of("heic", "heix", "heim", "heis", "hevc", "hevx", "hevm", "hevs", "mif1", "mif2", "msf1").stream()
                    .map(HeicImage::fourCharacterCode)
                    .toList();

    private final HashMap<Long, HeicImageFrame> framesById;

    private final HeicHeader header;

    private final byte[] heicData;

    private final HeicDecodingOptions decodingOptions;

    /**
     * <p>
     * Create heic image object.
     * </p>
     *
     * @param header File header.
     * @param stream File stream.
     */
    private HeicImage(
            HeicHeader header, BitStreamWithNalSupport stream, byte[] heicData, HeicDecodingOptions decodingOptions) {
        this.header = header;
        this.heicData = heicData;
        this.decodingOptions = decodingOptions;
        framesById = new HashMap<>();
        readFramesMeta(stream);
    }

    /**
     * Reads a HEIC file. This reads metadata only: pixels are decoded when a frame's pixels are requested.
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
            throw new HeicFormatException("Not a HEIC image.");
        }

        try {
            return readMetadata(heicData, decodingOptions);
        } catch (IsoBmffFormatException exception) {
            throw new HeicFormatException("Malformed HEIF container: " + exception.getMessage(), exception);
        }
    }

    private static HeicImage readMetadata(byte[] heicData, HeicDecodingOptions decodingOptions) {
        BitStreamWithNalSupport bitstream = new BitStreamWithNalSupport(heicData);

        while (bitstream.moreData()) {
            Box box = Box.parseBox(bitstream);

            if (box.type == BoxType.meta) {
                HeicImage heicImage =
                        new HeicImage(new HeicHeader((MetaBox) box), bitstream, heicData, decodingOptions);
                if (heicImage.getDefaultFrame() == null) {
                    throw new HeicFormatException("No primary image");
                }
                return heicImage;
            }
        }

        throw new HeicFormatException("Meta box not found.");
    }

    /**
     * Whether the data starts with a file type box listing a brand of HEIF images that may be coded in HEVC. Whether
     * the image actually is HEVC shows when it is decoded: AVIF also uses the generic {@code mif1} brand.
     */
    public static boolean canLoad(byte[] heicData) {
        try {
            BitStreamWithNalSupport bitstream = new BitStreamWithNalSupport(heicData);
            Box box = Box.parseBox(bitstream);

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

    /**
     * <p>
     * Get pixel data of the default image frame in the format of byte array.
     * </p>
     * <p>Each three or four bytes (the count depends on the pixel format) refer to one pixel left to right top to bottom line by line.</p>
     *
     * @param pixelFormat     Pixel format that defines the order of colors and the presence of alpha byte.
     * @return Byte array, null if frame does not contain image data.
     */
    public final /*Byte*/ byte[] getByteArray(PixelFormat pixelFormat) {
        return getByteArray(pixelFormat, new Rectangle(), null);
    }

    /**
     * <p>
     * Get pixel data of the default image frame in the format of byte array.
     * </p>
     * <p>Each three or four bytes (the count depends on the pixel format) refer to one pixel left to right top to bottom line by line.</p>
     *
     * @param pixelFormat     Pixel format that defines the order of colors and the presence of alpha byte.
     * @param dstArray        Byte array for storing the pixel values. If {@code null} or its length is less than
     *                        necessary the new array will be allocated and returned.
     * @return Byte array, null if frame does not contain image data. In general, it equals to {@code dstArray}.
     */
    public final /*Byte*/ byte[] getByteArray(PixelFormat pixelFormat, byte[] dstArray) {
        return getDefaultFrame().getByteArray(pixelFormat, new Rectangle(), dstArray);
    }

    /**
     * <p>
     * Get pixel data of the default image frame in the format of byte array.
     * </p>
     * <p>Each three or four bytes (the count depends on the pixel format) refer to one pixel left to right top to bottom line by line.</p>
     *
     * @param pixelFormat     Pixel format that defines the order of colors and the presence of alpha byte.
     * @param boundsRectangle Bounds of the requested area.
     * @param dstArray        Byte array for storing the pixel values. If {@code null} or its length is less than
     *                        necessary the new array will be allocated and returned.
     * @return Byte array, null if frame does not contain image data. In general, it equals to {@code dstArray}.
     */
    public final /*Byte*/ byte[] getByteArray(PixelFormat pixelFormat, Rectangle boundsRectangle, byte[] dstArray) {
        return getDefaultFrame().getByteArray(pixelFormat, boundsRectangle, dstArray);
    }

    /**
     * <p>
     * Get pixel data of the default image frame in the format of integer array.
     * </p>
     * <p>Each int value refers to one pixel left to right top to bottom line by line.</p>
     *
     * @param pixelFormat     Pixel format that defines the order of colors.
     * @return Integer array, null if frame does not contain image data.
     */
    public final int[] getInt32Array(PixelFormat pixelFormat) {
        return getInt32Array(pixelFormat, new Rectangle(), null);
    }

    /**
     * <p>
     * Get pixel data of the default image frame in the format of integer array.
     * </p>
     * <p>Each int value refers to one pixel left to right top to bottom line by line.</p>
     *
     * @param pixelFormat     Pixel format that defines the order of colors.
     * @param dstArray        Integer array for storing the ARGB values. If {@code null} or its length is less than
     *                        necessary the new array will be allocated and returned.
     * @return Integer array, null if frame does not contain image data. In general, it equals to {@code dstArray}.
     */
    public final int[] getInt32Array(PixelFormat pixelFormat, int[] dstArray) {
        return getInt32Array(pixelFormat, new Rectangle(), dstArray);
    }

    /**
     * <p>
     * Get pixel data of the default image frame in the format of integer array.
     * </p>
     * <p>Each int value refers to one pixel left to right top to bottom line by line.</p>
     *
     * @param pixelFormat     Pixel format that defines the order of colors.
     * @param boundsRectangle Bounds of the requested area.
     * @return Integer array, null if frame does not contain image data. In general, it equals to {@code dstArray}.
     */
    public final int[] getInt32Array(PixelFormat pixelFormat, Rectangle boundsRectangle) {
        return getDefaultFrame().getInt32Array(pixelFormat, boundsRectangle, null);
    }
    /**
     * <p>
     * Get pixel data of the default image frame in the format of integer array.
     * </p>
     * <p>Each int value refers to one pixel left to right top to bottom line by line.</p>
     *
     * @param pixelFormat     Pixel format that defines the order of colors.
     * @param boundsRectangle Bounds of the requested area.
     * @param dstArray        Integer array for storing the ARGB values. If {@code null} or its length is less than
     *                        necessary the new array will be allocated and returned.
     * @return Integer array, null if frame does not contain image data. In general, it equals to {@code dstArray}.
     */
    public final int[] getInt32Array(PixelFormat pixelFormat, Rectangle boundsRectangle, int[] dstArray) {
        return getDefaultFrame().getInt32Array(pixelFormat, boundsRectangle, dstArray);
    }

    /**
     * <p>
     * HEIC image header. Grants convenient access to {@link openize.isobmff} container metadata.
     * </p>
     * @return The HEIC image header.
     */
    public final HeicHeader getHeader() {
        return header;
    }

    // The whole file: frames read their coded data from it when they decode
    byte[] getHeicData() {
        return heicData;
    }

    HeicDecodingOptions getDecodingOptions() {
        return decodingOptions;
    }

    /**
     * <p>
     * Dictionary of public HEIC image frames with access by identifier.
     * </p>
     * @return The dictionary of public HEIC image frames with access by identifier.
     */
    public final Map<Long, HeicImageFrame> getFrames() {
        Map<Long, HeicImageFrame> publicFramesById = framesById.entrySet().stream()
                .filter((f) -> !f.getValue().isHidden()
                        && f.getValue().getDerivativeType() != BoxType.thmb
                        && f.getValue().getImageType() != ImageFrameType.tmap)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        List<EntityToGroupBox> groups = this.getHeader().getGroupsIfPresent();
        if (groups == null) return publicFramesById;

        for (EntityToGroupBox grp : groups) {
            if (grp.type == BoxType.altr) {
                /*UInt32*/ long[] entities = grp.entities;
                boolean selected = false;

                for (long entity : entities) {
                    if (publicFramesById.containsKey(entity)) {
                        if (!selected) selected = true;
                        else publicFramesById.remove(entity);
                    }
                }
            }
        }

        return publicFramesById;
    }

    /**
     * <p>
     * Dictionary of all HEIC image frames with access by identifier.
     * </p>
     * @return The dictionary of all HEIC image frames with access by identifier.
     */
    public final Map<Long, HeicImageFrame> getAllFrames() {
        return framesById;
    }

    /**
     * <p>
     * Returns the default image frame, which is specified in metadata.
     * </p>
     * @return the default image frame, which is specified in metadata.
     */
    public final HeicImageFrame getDefaultFrame() {
        return framesById.get(getHeader().getDefaultFrameId());
    }

    /**
     * <p>
     * Width of the default image frame in pixels.
     * </p>
     * @return The width of the default image frame in pixels.
     */
    public final /*UInt32*/ long getWidth() {
        return getDefaultFrame().getWidth();
    }

    /**
     * <p>
     * Height of the default image frame in pixels.
     * </p>
     * @return The height of the default image frame in pixels.
     */
    public final /*UInt32*/ long getHeight() {
        return getDefaultFrame().getHeight();
    }

    /**
     * <p>
     * Exchangeable image metadata of the default image frame.
     * </p>
     */
    /**
     * The Exif metadata of the primary image, if the file has any.
     *
     * @throws HeicFormatException if the Exif item is malformed
     */
    public final Optional<ExifData> getExifOptional() {
        return getDefaultFrame().getExifOptional();
    }

    /**
     * <p>
     * Fill frames dictionary with read metadata.
     * </p>
     *
     * @param stream File stream.
     */
    private void readFramesMeta(BitStreamWithNalSupport stream) {
        Map<Long, List<Box>> rawProperties = getHeader().getProperties();

        // every item, including derived images that have no data of their own and so no location
        for (ItemInfoEntry itemInfo : getHeader().getMeta().getiinf().itemInfos) {
            long id = itemInfo.itemID;
            framesById.put(id, new HeicImageFrame(stream, this, id));
        }

        for (Map.Entry<Long, HeicImageFrame> frame : framesById.entrySet()) {
            HeicImageFrame frameValue = frame.getValue();
            long frameValueID = frameValue.getID();
            if (rawProperties.containsKey(frameValueID))
                frameValue.loadProperties(stream, rawProperties.get(frameValueID));

            if (frameValue.getImageType() == ImageFrameType.exif) {
                for (long derivedId : getHeader().getDerivedList(frameValueID)) {
                    Optional.ofNullable(framesById.get(derivedId))
                            .ifPresent(describedFrame -> describedFrame.setExifItem(frameValue));
                }
            }
        }
    }
}
