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
import java.util.stream.Collectors;
import openize.heic.decoder.io.BitStreamWithNalSupport;
import openize.isobmff.Box;
import openize.isobmff.BoxType;
import openize.isobmff.EntityToGroupBox;
import openize.isobmff.FileTypeBox;
import openize.isobmff.IlocItem;
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
    private final HashMap<Long, HeicImageFrame> framesById;

    private final HeicHeader header;

    private final byte[] heicData;

    /**
     * <p>
     * Create heic image object.
     * </p>
     *
     * @param header File header.
     * @param stream File stream.
     */
    private HeicImage(HeicHeader header, BitStreamWithNalSupport stream, byte[] heicData) {
        this.header = header;
        this.heicData = heicData;
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
        return load(Files.readAllBytes(heicFile));
    }

    /**
     * Reads a HEIC image from a stream, which is read to its end but not closed.
     *
     * @throws IOException if the stream cannot be read
     * @throws HeicFormatException if it is not a HEIC image this decoder can read
     */
    public static HeicImage load(InputStream heicStream) throws IOException {
        return load(heicStream.readAllBytes());
    }

    /**
     * Reads a HEIC image held in memory. The array is not copied and must not change afterwards.
     *
     * @throws HeicFormatException if it is not a HEIC image this decoder can read
     */
    public static HeicImage load(byte[] heicData) {
        if (!canLoad(heicData)) {
            throw new HeicFormatException("Not a HEIC image.");
        }

        BitStreamWithNalSupport bitstream = new BitStreamWithNalSupport(heicData);

        Box.setExternalConstructor(BoxType.hvcC, (str, size) -> {
            if (!(str instanceof BitStreamWithNalSupport)) {
                throw new UnsupportedOperationException("Stream dedication logic error.");
            }
            BitStreamWithNalSupport nalStream = (BitStreamWithNalSupport) str;
            return new HEVCConfigurationBox(nalStream, size);
        });

        while (bitstream.moreData()) {
            Box box = Box.parseBox(bitstream);

            if (box.type == BoxType.meta) {
                return new HeicImage(new HeicHeader((MetaBox) box), bitstream, heicData);
            }
        }

        throw new HeicFormatException("Meta box not found.");
    }

    /**
     * Whether the data starts with a file type box listing the HEIC brand.
     */
    public static boolean canLoad(byte[] heicData) {
        try {
            BitStreamWithNalSupport bitstream = new BitStreamWithNalSupport(heicData);
            Box box = Box.parseBox(bitstream);

            return box instanceof FileTypeBox fileTypeBox && fileTypeBox.isBrandSupported(1751476579); // "heic"
        } catch (RuntimeException exception) {
            return false;
        }
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
    public final ExifData getExif() {
        return getDefaultFrame().exif;
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

        for (IlocItem item : getHeader().getMeta().getiloc().items) {
            long id = item.itemID;
            framesById.put(id, new HeicImageFrame(stream, this, id));
        }

        for (Map.Entry<Long, HeicImageFrame> frame : framesById.entrySet()) {
            HeicImageFrame frameValue = frame.getValue();
            long frameValueID = frameValue.getID();
            if (rawProperties.containsKey(frameValueID))
                frameValue.loadProperties(stream, rawProperties.get(frameValueID));

            if (frameValue.getImageType() == ImageFrameType.exif) {
                /*UInt32*/ long[] derived = getHeader().getDerivedList(frameValueID);
                ExifData exif = new ExifData(stream, this, frameValue);

                for (/*UInt32*/ long derivedId : derived) {
                    framesById.get(derivedId).exif = exif;
                }
            }
        }
    }
}
