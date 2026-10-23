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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import openize.heic.decoder.io.BitStreamWithNalSupport;
import openize.isobmff.AuxiliaryTypeProperty;
import openize.isobmff.Box;
import openize.isobmff.BoxType;
import openize.isobmff.CleanApertureBox;
import openize.isobmff.ColourInformationBox;
import openize.isobmff.IlocItem;
import openize.isobmff.ImageMirror;
import openize.isobmff.ImageRotation;
import openize.isobmff.ImageSpatialExtentsProperty;
import openize.isobmff.ItemInfoEntry;
import openize.isobmff.PixelAspectRatioBox;
import openize.isobmff.PixelInformationProperty;

/**
 * <p>
 * Heic image frame class.
 * Contains hevc coded data or metadata.
 * </p>
 */
public class HeicImageFrame {
    private static final List<String> gStringSwitchMap = Arrays.asList(
            "urn:mpeg:hevc:2015:auxid:1\u0000",
            "urn:mpeg:hevc:2015:auxid:2\u0000",
            "portraiteffectsmatte\u0000",
            "semanticskinmatte\u0000",
            "semantichairmatte\u0000",
            "semanticteethmatte\u0000",
            "semanticglassesmatte\u0000",
            "semanticskymatte\u0000",
            "hdrgainmap\u0000",
            "styledeltamap\u0000",
            "linearthumbnail\u0000");
    private final /*UInt32*/ long id;
    private final IlocItem locationBox;
    private final HeicImage parent;
    private final BitStreamWithNalSupport stream;
    private final ImageFrameType imageType;
    /**
     * <p>
     * Indicates the type of derivative content if the frame is derived.
     * </p>
     */
    private final BoxType derivativeType;
    /**
     * <p>
     * Hevc decoder configuration information from Isobmff container.
     * </p>
     */
    HEVCDecoderConfigurationRecord hvcConfig;

    /**
     * <p>
     * Raw YUV pixel data, used when a bit-depth is less or equal 8.
     * Multidimensional array: chroma or luma index, then two-dimensional array with x and y navigation.
     * </p>
     */
    private long hvcConfigOffset;

    private final long itemTypeCode;

    /**
     * <p>
     * Contains the pixel aspect ratio information.
     * This is an unused field that is created for debugging.
     * </p>
     */
    private PixelAspectRatioBox pasp = null;

    /**
     * <p>
     * Contains color information.
     * This is an unused field that is created for debugging.
     * </p>
     */
    private ColourInformationBox colr = null;

    /**
     * <p>
     * The unique identifier of the image frame.
     * </p>
     */
    public final /*UInt32*/ long getID() {
        return id;
    }

    private /*UInt32*/ long ispeWidth;
    private /*UInt32*/ long ispeHeight;
    private final boolean isHidden;

    /**
     * <p>
     * Indicates the type of auxiliary reference layer if the frame type is auxiliary.
     * </p>
     */
    private AuxiliaryReferenceType auxiliaryReferenceType;

    /**
     * <p>
     * Number of channels with color data.
     * </p>
     */
    private byte numberOfChannels;

    /**
     * <p>
     * Bits per channel with color data.
     * </p>
     */
    private byte[] bitsPerChannel;

    /**
     * <p>
     * Indicates the presence of transparency of layer.
     * </p>
     */
    private Long alphaReference = null;

    private byte imageRotationAngle;
    private byte imageMirrorAxis;
    private CleanApertureBox cleanAperture;

    /**
     * <p>
     * Create Heic image frame object.
     * </p>
     *
     * @param stream     File stream.
     * @param parent     Parent image.
     * @param id         Frame identifier.
     */
    HeicImageFrame(BitStreamWithNalSupport stream, HeicImage parent, /*UInt32*/ long id) {
        derivativeType = (parent.getHeader().getDerivedType(id));
        ItemInfoEntry informationBox = parent.getHeader().getInfoBoxById(id);
        locationBox = parent.getHeader().getLocationBoxById(id);

        this.auxiliaryReferenceType = AuxiliaryReferenceType.Undefined;
        itemTypeCode = informationBox.itemType & 0xFFFFFFFFL;
        imageType = ImageFrameType.codeToType(itemTypeCode);
        isHidden = informationBox.itemHidden;

        this.id = id;
        this.stream = stream;
        this.parent = parent;
    }

    /**
     * <p>
     * Type of image frame content.
     * </p>
     * @return The type of image frame content.
     */
    public final ImageFrameType getImageType() {
        return imageType;
    }

    /**
     * <p>
     * Width of the image frame in pixels.
     * </p>
     * @return The width of the image frame in pixels.
     */
    public final /*UInt32*/ long getWidth() {
        return (imageRotationAngle & 0xFF) % 2 == 0 ? getCroppedWidth() : getCroppedHeight();
    }

    /**
     * <p>
     * Height of the image frame in pixels.
     * </p>
     * @return The height of the image frame in pixels.
     */
    public final /*UInt32*/ long getHeight() {
        return (imageRotationAngle & 0xFF) % 2 == 0 ? getCroppedHeight() : getCroppedWidth();
    }

    // Size after the clean aperture crop, before rotation and mirroring
    private long getCroppedWidth() {
        return cleanAperture == null
                ? ispeWidth
                : roundFraction(cleanAperture.cleanApertureWidthN, cleanAperture.cleanApertureWidthD);
    }

    private long getCroppedHeight() {
        return cleanAperture == null
                ? ispeHeight
                : roundFraction(cleanAperture.cleanApertureHeightN, cleanAperture.cleanApertureHeightD);
    }

    // Left edge of the clean aperture: horizOff + (width - 1) / 2 - (cleanApertureWidth - 1) / 2, rounded down
    private long getCropLeft() {
        double left = (double) cleanAperture.horizOffN / cleanAperture.horizOffD
                + (ispeWidth - 1) / 2.0
                - ((double) cleanAperture.cleanApertureWidthN / cleanAperture.cleanApertureWidthD - 1) / 2.0;
        return clampCropOrigin((long) Math.floor(left), ispeWidth, getCroppedWidth());
    }

    // Top edge of the clean aperture, rounded to the nearest row
    private long getCropTop() {
        double top = (double) cleanAperture.vertOffN / cleanAperture.vertOffD
                + (ispeHeight - 1) / 2.0
                - ((double) cleanAperture.cleanApertureHeightN / cleanAperture.cleanApertureHeightD - 1) / 2.0;
        return clampCropOrigin((long) Math.floor(top + 0.5), ispeHeight, getCroppedHeight());
    }

    private static long clampCropOrigin(long origin, long fullSize, long croppedSize) {
        return Math.max(0, Math.min(origin, fullSize - croppedSize));
    }

    private static long roundFraction(long numerator, long denominator) {
        return (2 * numerator + denominator) / (2 * denominator);
    }

    /**
     * <p>
     * Indicates the presence of transparency of layer.
     * </p>
     *
     * @return True if frame is linked with alpha data frame, false otherwise.
     */
    public final boolean hasAlpha() {
        return alphaReference != null;
    }

    /**
     * <p>
     * Indicates the fact that frame is marked as hidden.
     * </p>
     *
     * @return True if frame is hidden, false otherwise.
     */
    public final boolean isHidden() {
        return isHidden;
    }

    /**
     * <p>
     * Indicates the fact that frame contains image data.
     * </p>
     *
     * @return True if frame is image, false otherwise.
     */
    public final boolean isImage() {
        return getImageType() == ImageFrameType.hvc1 || isDerived();
    }

    /**
     * <p>
     * Indicates the fact that frame contains image transform data and is inherited from another frame(-s).
     * </p>
     *
     * @return True if frame is derived, false otherwise.
     */
    public final boolean isDerived() {
        return getImageType() == ImageFrameType.iden
                || getImageType() == ImageFrameType.iovl
                || getImageType() == ImageFrameType.grid;
    }

    /**
     * <p>
     * Returns the type of derivative content if the frame is derived.
     * </p>
     * @return The type of derivative content if the frame is derived.
     */
    public final BoxType getDerivativeType() {
        return derivativeType;
    }

    /**
     * <p>
     * Returns the type of auxiliary reference layer if the frame type is auxiliary.
     * </p>
     * @return The type of auxiliary reference layer if the frame type is auxiliary.
     */
    public final AuxiliaryReferenceType getAuxiliaryReferenceType() {
        return auxiliaryReferenceType;
    }

    /**
     * <p>
     * Number of channels with color data.
     * </p>
     * @return The number of channels with color data.
     */
    public final byte getNumberOfChannels() {
        return numberOfChannels;
    }

    /**
     * <p>
     * Bits per channel with color data.
     * </p>
     * @return The bits per channel with color data.
     */
    public final byte[] getBitsPerChannel() {
        return bitsPerChannel;
    }

    /**
     * <p>
     * Exchangeable image frame metadata.
     * </p>
     */
    // the Exif item describing this image, parsed the first time it is asked for
    private HeicImageFrame exifItem;

    private ExifData exifData;

    /**
     * <p>
     * Get pixel data in the format of byte array.
     * </p>
     * <p>Each three or four bytes (the count depends on the pixel format) refer to one pixel left to right top to bottom line by line.</p>
     *
     * @param pixelFormat     Pixel format that defines the order of colors and the presence of alpha byte.
     * @return Byte array, null if frame does not contain image data.
     */
    public final byte[] getByteArray(PixelFormat pixelFormat) {
        return getByteArray(pixelFormat, new Rectangle(), null);
    }

    /**
     * <p>
     * Get pixel data in the format of byte array.
     * </p>
     * <p>Each three or four bytes (the count depends on the pixel format) refer to one pixel left to right top to bottom line by line.</p>
     *
     * @param pixelFormat     Pixel format that defines the order of colors and the presence of alpha byte.
     * @param dstArray        Byte array for storing the pixel values. If {@code null} or its length is less than
     *                        necessary the new array will be allocated and returned.
     * @return Byte array, null if frame does not contain image data. In general, it equals to {@code dstArray}.
     */
    public final byte[] getByteArray(PixelFormat pixelFormat, byte[] dstArray) {
        return getByteArray(pixelFormat, new Rectangle(), dstArray);
    }

    /**
     * <p>
     * Get pixel data in the format of byte array.
     * </p>
     * <p>Each three or four bytes (the count depends on the pixel format) refer to one pixel left to right top to bottom line by line.</p>
     *
     * @param pixelFormat     Pixel format that defines the order of colors and the presence of alpha byte.
     * @param boundsRectangle Bounds of the requested area.
     * @param dstArray        Byte array for storing the pixel values. If {@code null} or its length is less than
     *                        necessary the new array will be allocated and returned.
     * @return Byte array, null if frame does not contain image data. In general, it equals to {@code dstArray}.
     */
    public final byte[] getByteArray(PixelFormat pixelFormat, Rectangle boundsRectangle, byte[] dstArray) {
        requireDecodableImage();
        int[] argbPixels = decodeArgb(validateBounds(boundsRectangle));

        int bytesPerPixel = pixelFormat == PixelFormat.Rgb24 ? 3 : 4;
        int requiredLength = argbPixels.length * bytesPerPixel;
        byte[] output = dstArray == null || dstArray.length < requiredLength ? new byte[requiredLength] : dstArray;

        int outputIndex = 0;
        for (int argb : argbPixels) {
            int alpha = argb >>> 24;
            int red = (argb >> 16) & 0xFF;
            int green = (argb >> 8) & 0xFF;
            int blue = argb & 0xFF;
            switch (pixelFormat) {
                case Argb32 -> {
                    output[outputIndex++] = (byte) alpha;
                    output[outputIndex++] = (byte) red;
                    output[outputIndex++] = (byte) green;
                    output[outputIndex++] = (byte) blue;
                }
                case Bgra32 -> {
                    output[outputIndex++] = (byte) blue;
                    output[outputIndex++] = (byte) green;
                    output[outputIndex++] = (byte) red;
                    output[outputIndex++] = (byte) alpha;
                }
                case Rgba32 -> {
                    output[outputIndex++] = (byte) red;
                    output[outputIndex++] = (byte) green;
                    output[outputIndex++] = (byte) blue;
                    output[outputIndex++] = (byte) alpha;
                }
                case Rgb24 -> {
                    output[outputIndex++] = (byte) red;
                    output[outputIndex++] = (byte) green;
                    output[outputIndex++] = (byte) blue;
                }
            }
        }
        return output;
    }

    /**
     * <p>
     * Get pixel data in the format of integer array.
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
     * Get pixel data in the format of integer array.
     * </p>
     * <p>Each int value refers to one pixel left to right top to bottom line by line.</p>
     *
     * @param pixelFormat     Pixel format that defines the order of colors.
     * @param dstArray        Integer array for storing the ARGB values. If {@code null} or its length is less than
     *                        necessary the new array will be allocated and returned.
     * @return Integer array, null if frame does not contain image data.  In general, it equals to {@code dstArray}.
     */
    public final int[] getInt32Array(PixelFormat pixelFormat, int[] dstArray) {
        return getInt32Array(pixelFormat, new Rectangle(), dstArray);
    }

    /**
     * <p>
     * Get pixel data in the format of integer array.
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
        requireDecodableImage();
        int[] argbPixels = decodeArgb(validateBounds(boundsRectangle));
        if (pixelFormat == PixelFormat.Argb32 && (dstArray == null || dstArray.length < argbPixels.length)) {
            return argbPixels;
        }

        int[] output = dstArray == null || dstArray.length < argbPixels.length ? new int[argbPixels.length] : dstArray;
        for (int pixelIndex = 0; pixelIndex < argbPixels.length; pixelIndex++) {
            int argb = argbPixels[pixelIndex];
            output[pixelIndex] = switch (pixelFormat) {
                case Argb32 -> argb;
                // Rgb24 has no 32-bit layout of its own: like Rgba32, 0xRRGGBBAA
                case Rgba32, Rgb24 -> (argb << 8) | (argb >>> 24);
                case Bgra32 -> Integer.reverseBytes(argb);
            };
        }
        return output;
    }

    private void requireDecodableImage() {
        if (!isImage()) {
            throw new HeicFormatException("Item %d is coded as '%s', which this decoder does not decode"
                    .formatted(id, fourCharacterCodeText(itemTypeCode)));
        }
    }

    private static String fourCharacterCodeText(long code) {
        StringBuilder text = new StringBuilder(4);
        for (int shift = 24; shift >= 0; shift -= 8) {
            text.append((char) ((code >> shift) & 0xFF));
        }
        return text.toString();
    }

    private Rectangle validateBounds(Rectangle boundsRectangle) {
        if (boundsRectangle == null || boundsRectangle.isEmpty()) {
            return new Rectangle(0, 0, (int) getWidth(), (int) getHeight());
        }

        if (boundsRectangle.getLeft() < 0
                || boundsRectangle.getTop() < 0
                || boundsRectangle.getRight() > (int) getWidth()
                || boundsRectangle.getBottom() > (int) getHeight()) {
            throw new IndexOutOfBoundsException(
                    "The specification of selection area cannot exceed the size of an image.");
        }

        return boundsRectangle;
    }

    /**
     * <p>
     * Get frame text data.
     * </p>
     * <p>Exists only for mime frame types.</p>
     *
     * @return The frame text data.
     */
    public final String getTextData() {
        final ImageFrameType frameType = getImageType();
        if (frameType != ImageFrameType.mime && frameType != ImageFrameType.uri) {
            return "";
        }

        IlocItem location = null;
        for (IlocItem item : parent.getHeader().getMeta().getiloc().items) {
            if (item.itemID == id) {
                location = item;
                break;
            }
        }

        if (location == null) {
            return "";
        }

        stream.setBytePosition(location.baseOffset + location.extents[0].offset);
        return stream.readString();
    }

    /**
     * <p>
     * Add layer reference to the frame.
     * Used to link reference layers that are reverse-linked (alpha layer, depth map, hdr).
     * </p>
     *
     * @param id   A layer identifier.
     * @param type A layer type.
     */
    /**
     * The Exif metadata of this image, if the file has any for it.
     *
     * @throws HeicFormatException if the Exif item is malformed
     */
    public final synchronized Optional<ExifData> getExifOptional() {
        if (exifData == null && exifItem != null) {
            exifData = new ExifData(new BitStreamWithNalSupport(parent.getHeicData()), parent, exifItem);
        }
        return Optional.ofNullable(exifData);
    }

    final void setExifItem(HeicImageFrame exifItem) {
        this.exifItem = exifItem;
    }

    final void addLayerReference(/*UInt32*/ long id, AuxiliaryReferenceType type) {
        if (Objects.requireNonNull(type) == AuxiliaryReferenceType.Alpha) {
            this.alphaReference = id;
        }
    }

    /**
     * <p>
     * Load frame properties from Box and stream.
     * </p>
     * @param stream File stream.
     * @param properties Frame properties described in container.
     */
    final void loadProperties(BitStreamWithNalSupport stream, List<Box> properties) {
        for (Box item : properties) {
            switch (item.type) {
                case hvcC: // hvcC
                    HEVCConfigurationBox config = (HEVCConfigurationBox) item;
                    stream.createNewImageContext(id);
                    stream.setBytePosition(config.offset);
                    hvcConfigOffset = config.offset;
                    hvcConfig = new HEVCDecoderConfigurationRecord(stream);
                    config.record = hvcConfig; // gui
                    break;
                case ispe:
                    ImageSpatialExtentsProperty ispe = (ImageSpatialExtentsProperty) item;
                    ispeWidth = ispe.imageWidth;
                    ispeHeight = ispe.imageHeight;
                    break;
                case pasp:
                    pasp = (PixelAspectRatioBox) item;
                    break;
                case colr:
                    colr = (ColourInformationBox) item;
                    break;
                case pixi:
                    PixelInformationProperty pixi = (PixelInformationProperty) item;
                    this.numberOfChannels = pixi.numChannels;
                    this.bitsPerChannel = pixi.bitsPerChannel;
                    break;
                //                case rloc:
                //                    RelativeLocationProperty rloc = (RelativeLocationProperty) item;
                //                    break;
                case auxC:
                    AuxiliaryTypeProperty auxC = (AuxiliaryTypeProperty) item;

                    this.auxiliaryReferenceType = AuxiliaryReferenceType.Undefined;

                    switch (gStringSwitchMap.indexOf(auxC.auxType)) {
                        case /*"urn:mpeg:hevc:2015:auxid:1\u0000"*/ 0:
                            this.auxiliaryReferenceType = AuxiliaryReferenceType.Alpha;
                            break;
                        case /*"urn:mpeg:hevc:2015:auxid:2\u0000"*/ 1:
                            this.auxiliaryReferenceType = AuxiliaryReferenceType.DepthMap;
                            break;
                        default:
                            if (auxC.auxType.contains("apple") && auxC.auxType.contains(":aux:")) {
                                switch (gStringSwitchMap.indexOf(
                                        auxC.auxType.substring(auxC.auxType.indexOf(":aux:") + 5))) {
                                    case /*"portraiteffectsmatte\u0000"*/ 2:
                                        this.auxiliaryReferenceType = AuxiliaryReferenceType.PortraitEffectsMatte;
                                        break;
                                    case /*"semanticskinmatte\u0000"*/ 3:
                                        this.auxiliaryReferenceType = AuxiliaryReferenceType.SemanticSkinMatte;
                                        break;
                                    case /*"semantichairmatte\u0000"*/ 4:
                                        this.auxiliaryReferenceType = AuxiliaryReferenceType.SemanticHairMatte;
                                        break;
                                    case /*"semanticteethmatte\u0000"*/ 5:
                                        this.auxiliaryReferenceType = AuxiliaryReferenceType.SemanticTeethMatte;
                                        break;
                                    case /*"semanticglassesmatte\u0000"*/ 6:
                                        this.auxiliaryReferenceType = AuxiliaryReferenceType.SemanticGlassesMatte;
                                        break;
                                    case /*"semanticskymatte\u0000"*/ 7:
                                        auxiliaryReferenceType = AuxiliaryReferenceType.SemanticSkyMatte;
                                        break;
                                    case /*"hdrgainmap\u0000"*/ 8:
                                        this.auxiliaryReferenceType = AuxiliaryReferenceType.HdrGainMap;
                                        break;
                                    case /*"styledeltamap\u0000"*/ 9:
                                        this.auxiliaryReferenceType = AuxiliaryReferenceType.StyleDeltaMap;
                                        break;
                                    case /*"linearthumbnail\u0000"*/ 10:
                                        this.auxiliaryReferenceType = AuxiliaryReferenceType.LinearThumbnail;
                                        break;
                                    default:
                                    // Do nothing
                                }
                            }
                            break;
                    }

                    /*UInt32*/
                    long[] derived = parent.getHeader().getDerivedList(id);
                    for (/*UInt32*/ long derivedId : derived) {
                        HeicImageFrame referencedFrame = parent.getAllFrames().get(derivedId);
                        if (referencedFrame == null) {
                            throw new HeicFormatException("Auxiliary image %d refers to item %d, which is not an image"
                                    .formatted(id, derivedId));
                        }
                        referencedFrame.addLayerReference(id, this.auxiliaryReferenceType);
                    }
                    break;
                case clap:
                    CleanApertureBox clap = (CleanApertureBox) item;
                    if (clap.cleanApertureWidthD != 0
                            && clap.cleanApertureHeightD != 0
                            && clap.horizOffD != 0
                            && clap.vertOffD != 0) this.cleanAperture = clap;
                    break;
                case irot:
                    ImageRotation irot = (ImageRotation) item;
                    this.imageRotationAngle = irot.angle;
                    break;
                //                case lsel:
                //                    LayerSelectorProperty lsel = (LayerSelectorProperty) item;
                //                    break;
                case imir:
                    ImageMirror imir = (ImageMirror) item;
                    this.imageMirrorAxis = (/*Byte*/ byte) ((imir.axis & 0xFF) + 1);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Decodes {@code region} of this frame to 0xAARRGGBB pixels, row by row.
     */
    private int[] decodeArgb(Rectangle region) {
        PixelBox regionBox = new PixelBox(region.getLeft(), region.getTop(), region.getRight(), region.getBottom());
        int[] argbPixels = new int[regionBox.width() * regionBox.height()];
        decodeInto(PixelTarget.forRegion(regionBox, argbPixels), false);
        return argbPixels;
    }

    /**
     * Writes the pixels of this frame to {@code target}, which is in the coordinates of this frame after its crop,
     * rotation and mirroring. With {@code alphaOnly}, this frame is an alpha plane and sets only the alpha bytes.
     */
    private void decodeInto(PixelTarget target, boolean alphaOnly) {
        PixelTarget frameTarget = target.forSourceImage(toOrientedImage(), keptBox());
        if (frameTarget.isEmpty()) {
            return;
        }

        switch (getImageType()) {
            case hvc1 -> new YuvConverter(hvcConfig.getSPS()).convert(decodePicture(), frameTarget, alphaOnly);
            case iden -> getDerivedFrame(0).decodeInto(frameTarget, alphaOnly);
            case grid -> decodeGridInto(frameTarget, alphaOnly);
            case iovl -> decodeOverlayInto(frameTarget, alphaOnly);
            default -> throw new IllegalStateException("Unknown image type.");
        }

        if (!alphaOnly && alphaReference != null) {
            parent.getAllFrames().get(alphaReference).decodeInto(target, true);
        }
    }

    // The part of the decoded picture this frame shows: its clean aperture, or all of it
    private PixelBox keptBox() {
        if (cleanAperture == null) {
            return PixelBox.ofSize((int) ispeWidth, (int) ispeHeight);
        }
        int cropLeft = (int) getCropLeft();
        int cropTop = (int) getCropTop();
        return new PixelBox(cropLeft, cropTop, cropLeft + (int) getCroppedWidth(), cropTop + (int) getCroppedHeight());
    }

    private AxisTransform toOrientedImage() {
        PixelBox keptBox = keptBox();
        ImageOrientation imageOrientation = new ImageOrientation(imageRotationAngle & 0xFF, imageMirrorAxis & 0xFF);
        return AxisTransform.translation(-keptBox.left(), -keptBox.top())
                .andThen(imageOrientation.toOrientedImage((int) getWidth(), (int) getHeight()));
    }

    private HeicImageFrame getDerivedFrame(int index) {
        long[] derivedIds = parent.getHeader().getDerivedList(id);
        if (index >= derivedIds.length || parent.getAllFrames().get(derivedIds[index]) == null) {
            throw new HeicFormatException("Image %d lacks its input image %d".formatted(id, index));
        }
        return parent.getAllFrames().get(derivedIds[index]);
    }

    // A fresh reader per decode: frames decode independently of each other and of earlier decodes
    private HeicPicture decodePicture() {
        requireItemData();
        BitStreamWithNalSupport pictureStream = new BitStreamWithNalSupport(parent.getHeicData());
        pictureStream.createNewImageContext(id);
        pictureStream.setBytePosition(hvcConfigOffset);
        new HEVCDecoderConfigurationRecord(pictureStream);

        long start = locationBox.baseOffset + locationBox.extents[0].offset;
        long end = start + locationBox.extents[0].length;
        pictureStream.setBytePosition(start);
        while (pictureStream.getBitPosition() / 8 < end) {
            NalUnit.parseUnit(pictureStream);
        }

        HeicPicture picture = pictureStream.getContext().getPictures().get(id);
        if (picture == null) {
            throw new HeicFormatException("Image #%d holds no picture".formatted(id));
        }
        picture.applyLoopFilters();
        return picture;
    }

    private void decodeGridInto(PixelTarget gridTarget, boolean alphaOnly) {
        byte[] gridDescriptor = readItemData();
        int fieldLength = ((gridDescriptor[1] & 1) + 1) * 2;
        int rowCount = (gridDescriptor[2] & 0xFF) + 1;
        int columnCount = (gridDescriptor[3] & 0xFF) + 1;
        int gridWidth = getByteFromIdatField(gridDescriptor, 4, fieldLength);
        int gridHeight = getByteFromIdatField(gridDescriptor, 4 + fieldLength, fieldLength);

        // tiles cover disjoint pixels and decode independently, so they decode in parallel
        List<Runnable> tileDecodes = new ArrayList<>();
        for (int row = 0; row < rowCount; row++) {
            for (int column = 0; column < columnCount; column++) {
                HeicImageFrame tileFrame = getDerivedFrame(row * columnCount + column);
                int tileX = column * (int) tileFrame.getWidth();
                int tileY = row * (int) tileFrame.getHeight();
                PixelBox gridBoxInTile = new PixelBox(-tileX, -tileY, gridWidth - tileX, gridHeight - tileY);
                PixelTarget tileTarget =
                        gridTarget.forSourceImage(AxisTransform.translation(tileX, tileY), gridBoxInTile);

                // tiles outside the requested region are not decoded at all
                if (!tileTarget.isEmpty()) {
                    tileDecodes.add(() -> tileFrame.decodeInto(tileTarget, alphaOnly));
                }
            }
        }
        tileDecodes.parallelStream().forEach(Runnable::run);
    }

    private void decodeOverlayInto(PixelTarget overlayTarget, boolean alphaOnly) {
        byte[] overlayDescriptor = readItemData();
        long[] derivedIds = parent.getHeader().getDerivedList(id);
        int fieldLength = ((overlayDescriptor[1] & 1) + 1) * 2;
        // skips canvas_fill_value: the canvas is opaque black
        int index = 2 + 4 * 2;
        int overlayWidth = getByteFromIdatField(overlayDescriptor, index, fieldLength);
        int overlayHeight = getByteFromIdatField(overlayDescriptor, index + fieldLength, fieldLength);
        index += 2 * fieldLength;

        // as an alpha plane, the opaque black canvas reads as transparent
        overlayTarget.fill(PixelBox.ofSize(overlayWidth, overlayHeight), 0xFF000000, alphaOnly);

        for (long derivedId : derivedIds) {
            int horizontalOffset = getByteFromIdatField(overlayDescriptor, index, fieldLength);
            int verticalOffset = getByteFromIdatField(overlayDescriptor, index + fieldLength, fieldLength);
            index += 2 * fieldLength;

            PixelBox overlayBoxInLayer = new PixelBox(
                    -horizontalOffset,
                    -verticalOffset,
                    overlayWidth - horizontalOffset,
                    overlayHeight - verticalOffset);
            PixelTarget layerTarget = overlayTarget.forSourceImage(
                    AxisTransform.translation(horizontalOffset, verticalOffset), overlayBoxInLayer);
            if (!layerTarget.isEmpty()) {
                parent.getAllFrames().get(derivedId).decodeInto(layerTarget, alphaOnly);
            }
        }
    }

    private void requireItemData() {
        if (locationBox == null || locationBox.extents.length == 0) {
            throw new HeicFormatException("Item %d has no data".formatted(id));
        }
    }

    private byte[] readItemData() {
        requireItemData();
        return (locationBox.constructionMethod & 0xFF) == 1
                ? parent.getHeader()
                        .getItemDataBoxContent(
                                locationBox.baseOffset + locationBox.extents[0].offset, locationBox.extents[0].length)
                : new byte[0];
    }

    private int getByteFromIdatField(byte[] arr, int offset, int length) {
        int value = arr[offset] & 0xFF;

        for (int i = 1; i < length; i++) {
            value = (value << 8) | (arr[offset + i] & 0xFF);
        }

        return value;
    }

    @Override
    public String toString() {
        StringBuilder line = new StringBuilder(255);
        line.append('[').append(id).append(',').append(this.imageType).append("] ");

        if (colr != null) line.append(colr.getColourType()).append(' ');

        final byte[] bitsPerChannel1 = getBitsPerChannel();
        if (bitsPerChannel1 != null)
            line.append('(').append(Arrays.toString(bitsPerChannel1)).append(") ");

        if (ispeWidth > 0) line.append(ispeWidth).append('x').append(ispeHeight).append(' ');

        if (imageRotationAngle > 0) line.append(imageRotationAngle * 90).append("° ");

        if (auxiliaryReferenceType != AuxiliaryReferenceType.Undefined)
            line.append(auxiliaryReferenceType).append(' ');

        if (derivativeType != null) {
            switch (derivativeType) {
                case dimg:
                    line.append("Derived: ");
                    break;
                case auxl:
                    line.append("Auxiliary: ");
                    break;
                case cdsc:
                    line.append("Content desc.: ");
                    break;
                case thmb:
                    line.append("Thumbnail: ");
                    break;
            }

            long[] derived = parent.getHeader().getDerivedList(id);
            line.append(Arrays.toString(derived));
        }

        return line.toString();
    }
}
