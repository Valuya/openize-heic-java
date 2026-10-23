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

import io.github.valuya.heic.isobmff.AuxiliaryTypeProperty;
import io.github.valuya.heic.isobmff.Box;
import io.github.valuya.heic.isobmff.BoxType;
import io.github.valuya.heic.isobmff.CleanApertureBox;
import io.github.valuya.heic.isobmff.ColourInformationBox;
import io.github.valuya.heic.isobmff.IlocItem;
import io.github.valuya.heic.isobmff.IlocItemExtent;
import io.github.valuya.heic.isobmff.ImageMirror;
import io.github.valuya.heic.isobmff.ImageRotation;
import io.github.valuya.heic.isobmff.ImageSpatialExtentsProperty;
import io.github.valuya.heic.isobmff.IsoBmffFormatException;
import io.github.valuya.heic.isobmff.ItemInfoEntry;
import io.github.valuya.heic.isobmff.PixelInformationProperty;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * An item of a HEIF file: an image, coded in HEVC or derived from other images, or metadata. An image is decoded
 * as displayed: cropped to its clean aperture, rotated and mirrored, with the alpha of its alpha plane.
 */
public final class HeicImageFrame {
    // iloc construction methods (ISO/IEC 14496-12, 8.11.3): the item data is in the file, or in the idat box
    private static final int FILE_OFFSET = 0;
    private static final int IDAT_OFFSET = 1;

    private final long id;
    private final HeicImage parent;
    private final ImageFrameType imageType;
    private final long itemTypeCode;
    private final boolean hidden;
    private final @Nullable BoxType derivativeType;
    private final @Nullable IlocItem location;

    // from the item's properties
    private @Nullable HEVCDecoderConfigurationRecord hvcConfig;
    private long hvcConfigOffset;
    private long hvcConfigLength;
    private int ispeWidth;
    private int ispeHeight;
    private @Nullable ColourInformationBox colr;
    // the colour information property of type nclx, when the frame has one besides or instead of an ICC profile
    private @Nullable ColourInformationBox nclxColour;
    private int channelCount;
    private int[] bitsPerChannel = new int[0];
    private AuxiliaryReferenceType auxiliaryReferenceType = AuxiliaryReferenceType.UNDEFINED;
    private @Nullable CleanApertureBox cleanAperture;
    private int rotationQuarterTurns;
    // 0 no mirroring, 1 about a vertical axis, 2 about a horizontal axis
    private int mirrorAxis;

    // the items that refer to this one
    private @Nullable HeicImageFrame alphaFrame;
    private @Nullable HeicImageFrame exifItem;
    private @Nullable ExifData exifData;

    HeicImageFrame(HeicImage parent, long id) {
        HeicHeader header = parent.getHeader();
        ItemInfoEntry itemInfo = header.getInfoBoxById(id);
        this.parent = parent;
        this.id = id;
        itemTypeCode = itemInfo.getItemType();
        imageType = ImageFrameType.codeToType(itemTypeCode);
        hidden = itemInfo.isHidden();
        derivativeType = header.getDerivedTypeOptional(id).orElse(null);
        location = header.getLocationBoxByIdOptional(id).orElse(null);
    }

    /** The item id, unique in the file. */
    public long getId() {
        return id;
    }

    public ImageFrameType getImageType() {
        return imageType;
    }

    /** The width of the image as displayed: after its crop and rotation. */
    public int getWidth() {
        return rotationQuarterTurns % 2 == 0 ? getCroppedWidth() : getCroppedHeight();
    }

    /** The height of the image as displayed: after its crop and rotation. */
    public int getHeight() {
        return rotationQuarterTurns % 2 == 0 ? getCroppedHeight() : getCroppedWidth();
    }

    /** Whether the image has an alpha plane. */
    public boolean hasAlpha() {
        return alphaFrame != null;
    }

    /** Whether the item is marked hidden: an input of a derived image, for instance. */
    public boolean isHidden() {
        return hidden;
    }

    /** Whether the item is an image, coded or derived. */
    public boolean isImage() {
        return imageType == ImageFrameType.hvc1 || isDerived();
    }

    /** Whether the item is an image derived from other images: an identity, a grid or an overlay. */
    public boolean isDerived() {
        return imageType == ImageFrameType.iden || imageType == ImageFrameType.iovl || imageType == ImageFrameType.grid;
    }

    /**
     * The type of the item's reference to other items: {@code dimg} for a derived image, {@code thmb} for a
     * thumbnail, {@code auxl} for an auxiliary image, {@code cdsc} for metadata.
     */
    public Optional<BoxType> getDerivativeTypeOptional() {
        return Optional.ofNullable(derivativeType);
    }

    public AuxiliaryReferenceType getAuxiliaryReferenceType() {
        return auxiliaryReferenceType;
    }

    /** The number of channels, from the pixel information property; 0 when the item has none. */
    public int getChannelCount() {
        return channelCount;
    }

    /** The bits per channel, from the pixel information property; empty when the item has none. */
    public int[] getBitsPerChannel() {
        return bitsPerChannel.clone();
    }

    /**
     * Decodes the image to 0xAARRGGBB pixels, row by row.
     *
     * @throws HeicFormatException if the item is not an image this decoder can decode
     * @throws HeicImageTooLargeException if the image has more pixels than the decoding options allow
     */
    public int[] decodeArgb() {
        return decodeArgb(wholeImage());
    }

    /**
     * Decodes {@code region} of the image to 0xAARRGGBB pixels, row by row. Only the parts of the image the region
     * covers are decoded: the grid tiles outside it are skipped.
     *
     * @throws IllegalArgumentException if the region is not inside the image
     * @throws HeicFormatException if the item is not an image this decoder can decode
     * @throws HeicImageTooLargeException if the region has more pixels than the decoding options allow
     */
    public int[] decodeArgb(PixelRegion region) {
        requireInside(region);
        parent.getDecodingOptions().checkPixelCount("Image %d".formatted(id), region.width(), region.height());
        int[] argbPixels = new int[region.pixelCount()];
        decodeArgb(region, argbPixels, 0, region.width());
        return argbPixels;
    }

    /**
     * Decodes {@code region} of the image to 0xAARRGGBB pixels in {@code destination}: the first pixel at
     * {@code offset}, each row {@code scanlineStride} after the one before, as in the data buffer of a
     * {@code TYPE_INT_ARGB} raster.
     *
     * @throws IllegalArgumentException if the region is not inside the image, or does not fit in the destination
     * @throws HeicFormatException if the item is not an image this decoder can decode
     */
    public void decodeArgb(PixelRegion region, int[] destination, int offset, int scanlineStride) {
        requireInside(region);
        requireFits(region, destination, offset, scanlineStride);
        requireDecodableImage();
        PixelBox regionBox = new PixelBox(region.left(), region.top(), region.right(), region.bottom());
        try {
            decodeInto(
                    PixelTarget.forRegion(regionBox, destination, offset, scanlineStride),
                    false,
                    new DecoderWorkspace.Pool());
        } catch (IsoBmffFormatException exception) {
            throw new HeicFormatException(
                    "Malformed data in image %d: %s".formatted(id, exception.getMessage()), exception);
        }
    }

    /**
     * Decodes the image to pixels of {@code pixelFormat}, row by row.
     *
     * @throws HeicFormatException if the item is not an image this decoder can decode
     * @throws HeicImageTooLargeException if the image has more pixels than the decoding options allow
     */
    public byte[] decodeBytes(PixelFormat pixelFormat) {
        return decodeBytes(pixelFormat, wholeImage());
    }

    /**
     * Decodes {@code region} of the image to pixels of {@code pixelFormat}, row by row.
     *
     * @throws IllegalArgumentException if the region is not inside the image
     * @throws HeicFormatException if the item is not an image this decoder can decode
     * @throws HeicImageTooLargeException if the region has more pixels than the decoding options allow
     */
    public byte[] decodeBytes(PixelFormat pixelFormat, PixelRegion region) {
        requireInside(region);
        parent.getDecodingOptions().checkPixelCount("Image %d".formatted(id), region.width(), region.height());
        requireDecodableImage();
        byte[] output = new byte[region.pixelCount() * pixelFormat.bytesPerPixel()];
        PixelBox regionBox = new PixelBox(region.left(), region.top(), region.right(), region.bottom());
        try {
            decodeInto(PixelTarget.forRegion(regionBox, output, pixelFormat), false, new DecoderWorkspace.Pool());
        } catch (IsoBmffFormatException exception) {
            throw new HeicFormatException(
                    "Malformed data in image %d: %s".formatted(id, exception.getMessage()), exception);
        }
        return output;
    }

    /**
     * The content of a {@code mime} or {@code uri} item, such as XMP, as UTF-8 text.
     *
     * @throws HeicFormatException if the item data is malformed
     */
    public Optional<String> getTextOptional() {
        if (imageType != ImageFrameType.mime && imageType != ImageFrameType.uri) {
            return Optional.empty();
        }
        byte[] itemBytes = readItemBytes();
        int length = itemBytes.length;
        while (length > 0 && itemBytes[length - 1] == 0) {
            length--;
        }
        return Optional.of(new String(itemBytes, 0, length, StandardCharsets.UTF_8));
    }

    /**
     * The Exif metadata of this image, if the file has any for it.
     *
     * @throws HeicFormatException if the Exif item is malformed
     */
    public synchronized Optional<ExifData> getExifOptional() {
        if (exifData == null && exifItem != null) {
            exifData = new ExifData(id, exifItem.readItemBytes());
        }
        return Optional.ofNullable(exifData);
    }

    void setExifItem(HeicImageFrame exifItem) {
        this.exifItem = exifItem;
    }

    // an auxiliary image refers to this one
    private void addAuxiliaryImage(HeicImageFrame auxiliaryFrame, AuxiliaryReferenceType type) {
        if (type == AuxiliaryReferenceType.ALPHA) {
            alphaFrame = auxiliaryFrame;
        }
    }

    /**
     * Reads the properties associated with this item.
     */
    void loadProperties(List<Box> properties) {
        for (Box property : properties) {
            switch (property.getType()) {
                case hvcC -> loadHevcConfiguration(property);
                case ispe -> {
                    ImageSpatialExtentsProperty ispe = (ImageSpatialExtentsProperty) property;
                    ispeWidth = toPixelCount("width", ispe.getWidth());
                    ispeHeight = toPixelCount("height", ispe.getHeight());
                }
                case colr -> {
                    colr = (ColourInformationBox) property;
                    if (colr.isNclx()) {
                        nclxColour = colr;
                    }
                }
                case pixi -> {
                    PixelInformationProperty pixi = (PixelInformationProperty) property;
                    channelCount = pixi.getChannelCount();
                    byte[] bits = pixi.getBitsPerChannel();
                    bitsPerChannel = new int[bits.length];
                    for (int channel = 0; channel < bits.length; channel++) {
                        bitsPerChannel[channel] = bits[channel] & 0xFF;
                    }
                }
                case auxC -> loadAuxiliaryType((AuxiliaryTypeProperty) property);
                case clap -> {
                    CleanApertureBox clap = (CleanApertureBox) property;
                    if (clap.getWidthDenominator() != 0
                            && clap.getHeightDenominator() != 0
                            && clap.getHorizontalOffsetDenominator() != 0
                            && clap.getVerticalOffsetDenominator() != 0) {
                        cleanAperture = clap;
                    }
                }
                case irot -> rotationQuarterTurns = ((ImageRotation) property).getAngle();
                case imir -> mirrorAxis = ((ImageMirror) property).getAxis() + 1;
                default -> {
                    // a property decoding does not use
                }
            }
        }
    }

    // the decoder configuration: its parameter sets are read again for every decode, with the coded data
    private void loadHevcConfiguration(Box hvcC) {
        hvcConfigOffset = hvcC.getContentOffset();
        hvcConfigLength = hvcC.getContentLength();
        BitStreamWithNalSupport configStream = new BitStreamWithNalSupport(parent.getHeicData());
        configStream.getContext().setDecodingOptions(parent.getDecodingOptions());
        configStream.setBytePosition(hvcConfigOffset);
        hvcConfig = new HEVCDecoderConfigurationRecord(configStream);
    }

    // auxC: what this auxiliary image holds, for the images it refers to
    private void loadAuxiliaryType(AuxiliaryTypeProperty auxC) {
        auxiliaryReferenceType = AuxiliaryReferenceType.of(auxC.getAuxType());
        for (long referencedId : parent.getHeader().getDerivedList(id)) {
            HeicImageFrame referencedFrame = parent.getAllFrames().get(referencedId);
            if (referencedFrame == null) {
                throw new HeicFormatException(
                        "Auxiliary image %d refers to item %d, which is not an image".formatted(id, referencedId));
            }
            referencedFrame.addAuxiliaryImage(this, auxiliaryReferenceType);
        }
    }

    private int toPixelCount(String dimension, long pixelCount) {
        if (pixelCount > Integer.MAX_VALUE) {
            throw new HeicFormatException("Image %d has a %s of %d pixels".formatted(id, dimension, pixelCount));
        }
        return (int) pixelCount;
    }

    // Size after the clean aperture crop, before rotation and mirroring
    private int getCroppedWidth() {
        return cleanAperture == null
                ? ispeWidth
                : (int) roundFraction(cleanAperture.getWidthNumerator(), cleanAperture.getWidthDenominator());
    }

    private int getCroppedHeight() {
        return cleanAperture == null
                ? ispeHeight
                : (int) roundFraction(cleanAperture.getHeightNumerator(), cleanAperture.getHeightDenominator());
    }

    // Left edge of the clean aperture: horizOff + (width - 1) / 2 - (cleanApertureWidth - 1) / 2, rounded down
    private int getCropLeft(CleanApertureBox clap) {
        double left = (double) clap.getHorizontalOffsetNumerator() / clap.getHorizontalOffsetDenominator()
                + (ispeWidth - 1) / 2.0
                - ((double) clap.getWidthNumerator() / clap.getWidthDenominator() - 1) / 2.0;
        return clampCropOrigin((long) Math.floor(left), ispeWidth, getCroppedWidth());
    }

    // Top edge of the clean aperture, rounded to the nearest row
    private int getCropTop(CleanApertureBox clap) {
        double top = (double) clap.getVerticalOffsetNumerator() / clap.getVerticalOffsetDenominator()
                + (ispeHeight - 1) / 2.0
                - ((double) clap.getHeightNumerator() / clap.getHeightDenominator() - 1) / 2.0;
        return clampCropOrigin((long) Math.floor(top + 0.5), ispeHeight, getCroppedHeight());
    }

    private static int clampCropOrigin(long origin, int fullSize, int croppedSize) {
        return (int) Math.max(0, Math.min(origin, fullSize - croppedSize));
    }

    private static long roundFraction(long numerator, long denominator) {
        return (2 * numerator + denominator) / (2 * denominator);
    }

    // a size of 0 comes from a missing or malformed ispe or clap property
    private PixelRegion wholeImage() {
        if (getWidth() < 1 || getHeight() < 1) {
            throw new HeicFormatException("Image %d has a size of %dx%d pixels".formatted(id, getWidth(), getHeight()));
        }
        return PixelRegion.ofSize(getWidth(), getHeight());
    }

    private void requireInside(PixelRegion region) {
        if (region.right() > getWidth() || region.bottom() > getHeight()) {
            throw new IllegalArgumentException(
                    "Region %s outside image %d of %dx%d pixels".formatted(region, id, getWidth(), getHeight()));
        }
    }

    private static void requireFits(PixelRegion region, int[] destination, int offset, int scanlineStride) {
        long lastIndex = offset + (long) (region.height() - 1) * scanlineStride + region.width() - 1;
        if (offset < 0 || scanlineStride < region.width() || lastIndex >= destination.length) {
            throw new IllegalArgumentException("A %dx%d region does not fit at %d, %d per row, in an array of %d pixels"
                    .formatted(region.width(), region.height(), offset, scanlineStride, destination.length));
        }
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

    /**
     * Writes the pixels of this frame to {@code target}, which is in the coordinates of this frame after its crop,
     * rotation and mirroring. With {@code alphaOnly}, this frame is an alpha plane and sets only the alpha bytes. Coded
     * pictures are decoded in workspaces of {@code workspaces}.
     */
    private void decodeInto(PixelTarget target, boolean alphaOnly, DecoderWorkspace.Pool workspaces) {
        PixelTarget frameTarget = target.forSourceImage(toOrientedImage(), keptBox());
        if (frameTarget.isEmpty()) {
            return;
        }

        switch (imageType) {
            case hvc1 -> {
                SeqParameterSetRbsp sps = requireHevcConfiguration().getSPS();
                // the workspace goes back once the picture, which is in it, is converted
                DecoderWorkspace workspace = workspaces.take();
                try {
                    new YuvConverter(sps, colourDescription(sps))
                            .convert(decodePicture(workspace), frameTarget, alphaOnly);
                } finally {
                    workspaces.giveBack(workspace);
                }
            }
            case iden -> getInputImage(0).decodeInto(frameTarget, alphaOnly, workspaces);
            case grid -> decodeGridInto(frameTarget, alphaOnly, workspaces);
            case iovl -> decodeOverlayInto(frameTarget, alphaOnly, workspaces);
            default ->
                throw new HeicFormatException("Item %d is coded as '%s', which this decoder does not decode"
                        .formatted(id, fourCharacterCodeText(itemTypeCode)));
        }

        // a destination without alpha needs no alpha plane
        if (!alphaOnly && alphaFrame != null && target.keepsAlpha()) {
            alphaFrame.decodeInto(target, true, workspaces);
        }
    }

    // The part of the decoded picture this frame shows: its clean aperture, or all of it
    private PixelBox keptBox() {
        if (cleanAperture == null) {
            return PixelBox.ofSize(ispeWidth, ispeHeight);
        }
        int cropLeft = getCropLeft(cleanAperture);
        int cropTop = getCropTop(cleanAperture);
        return new PixelBox(cropLeft, cropTop, cropLeft + getCroppedWidth(), cropTop + getCroppedHeight());
    }

    private AxisTransform toOrientedImage() {
        PixelBox keptBox = keptBox();
        ImageOrientation imageOrientation = new ImageOrientation(rotationQuarterTurns, mirrorAxis);
        return AxisTransform.translation(-keptBox.left(), -keptBox.top())
                .andThen(imageOrientation.toOrientedImage(getWidth(), getHeight()));
    }

    private HeicImageFrame getInputImage(int index) {
        long[] inputIds = parent.getHeader().getDerivedList(id);
        HeicImageFrame inputImage =
                index < inputIds.length ? parent.getAllFrames().get(inputIds[index]) : null;
        if (inputImage == null) {
            throw new HeicFormatException("Image %d lacks its input image %d".formatted(id, index));
        }
        return inputImage;
    }

    // A fresh reader per decode: frames decode independently of each other and of earlier decodes. The coded data of
    // an item in one extent of the file is read in place; an item in several extents, or in idat, is copied after the
    // decoder configuration first
    private HeicPicture decodePicture(DecoderWorkspace workspace) {
        requireHevcConfiguration();
        BitStreamWithNalSupport pictureStream;
        long codedDataStart;
        long codedDataEnd;
        IlocItem itemLocation = location;
        if (itemLocation != null
                && itemLocation.extents().size() == 1
                && itemLocation.constructionMethod() == FILE_OFFSET) {
            IlocItemExtent extent = itemLocation.extents().get(0);
            pictureStream = new BitStreamWithNalSupport(parent.getHeicData());
            pictureStream.setBytePosition(hvcConfigOffset);
            codedDataStart = itemLocation.baseOffset() + extent.offset();
            codedDataEnd = extent.length() == 0 ? parent.getHeicData().length : codedDataStart + extent.length();
        } else {
            byte[] itemBytes = readItemBytes();
            byte[] decoderInput = new byte[Math.toIntExact(hvcConfigLength + itemBytes.length)];
            System.arraycopy(parent.getHeicData(), (int) hvcConfigOffset, decoderInput, 0, (int) hvcConfigLength);
            System.arraycopy(itemBytes, 0, decoderInput, (int) hvcConfigLength, itemBytes.length);
            pictureStream = new BitStreamWithNalSupport(decoderInput);
            codedDataStart = hvcConfigLength;
            codedDataEnd = decoderInput.length;
        }
        pictureStream.getContext().setDecodingOptions(parent.getDecodingOptions());
        pictureStream.getContext().setWorkspace(workspace);
        new HEVCDecoderConfigurationRecord(pictureStream);

        if (codedDataStart < 0 || codedDataEnd > pictureStream.getLength()) {
            throw new HeicFormatException("The coded data of image %d is outside the file".formatted(id));
        }
        pictureStream.setBytePosition(codedDataStart);
        while (pictureStream.getBitPosition() / 8 < codedDataEnd) {
            NalUnit.parseUnit(pictureStream);
        }

        HeicPicture picture = pictureStream
                .getContext()
                .getCurrentPictureOptional()
                .orElseThrow(() -> new HeicFormatException("Image %d holds no picture".formatted(id)));
        picture.applyLoopFilters();
        return picture;
    }

    // an nclx colour information property takes precedence over the video usability information of the bitstream
    private YuvConverter.ColourDescription colourDescription(SeqParameterSetRbsp sps) {
        if (nclxColour == null) {
            return YuvConverter.ColourDescription.of(sps.vuiParameters);
        }
        return new YuvConverter.ColourDescription(
                nclxColour.getColourPrimaries(), nclxColour.getMatrixCoefficients(), nclxColour.isFullRange());
    }

    // ISO/IEC 23008-12, 6.6.2.3: rows and columns of tiles of the same size, cropped to the grid's size
    private void decodeGridInto(PixelTarget gridTarget, boolean alphaOnly, DecoderWorkspace.Pool workspaces) {
        byte[] gridDescriptor = readItemBytes();
        requireDescriptorLength("grid", gridDescriptor, 4);
        int fieldLength = ((gridDescriptor[1] & 1) + 1) * 2;
        requireDescriptorLength("grid", gridDescriptor, 4 + 2 * fieldLength);
        int rowCount = (gridDescriptor[2] & 0xFF) + 1;
        int columnCount = (gridDescriptor[3] & 0xFF) + 1;
        int gridWidth = readDescriptorField(gridDescriptor, 4, fieldLength);
        int gridHeight = readDescriptorField(gridDescriptor, 4 + fieldLength, fieldLength);

        // tiles cover disjoint pixels and decode independently, so they decode in parallel
        List<Runnable> tileDecodes = new ArrayList<>();
        for (int row = 0; row < rowCount; row++) {
            for (int column = 0; column < columnCount; column++) {
                HeicImageFrame tileFrame = getInputImage(row * columnCount + column);
                int tileX = column * tileFrame.getWidth();
                int tileY = row * tileFrame.getHeight();
                PixelBox gridBoxInTile = new PixelBox(-tileX, -tileY, gridWidth - tileX, gridHeight - tileY);
                PixelTarget tileTarget =
                        gridTarget.forSourceImage(AxisTransform.translation(tileX, tileY), gridBoxInTile);

                // tiles outside the requested region are not decoded at all
                if (!tileTarget.isEmpty()) {
                    tileDecodes.add(() -> tileFrame.decodeInto(tileTarget, alphaOnly, workspaces));
                }
            }
        }
        if (parent.getDecodingOptions().parallel()) {
            tileDecodes.parallelStream().forEach(Runnable::run);
        } else {
            tileDecodes.forEach(Runnable::run);
        }
    }

    // ISO/IEC 23008-12, 6.6.2.2: input images placed on a canvas, in order
    private void decodeOverlayInto(PixelTarget overlayTarget, boolean alphaOnly, DecoderWorkspace.Pool workspaces) {
        byte[] overlayDescriptor = readItemBytes();
        long[] inputIds = parent.getHeader().getDerivedList(id);
        requireDescriptorLength("overlay", overlayDescriptor, 2);
        int fieldLength = ((overlayDescriptor[1] & 1) + 1) * 2;
        requireDescriptorLength("overlay", overlayDescriptor, 2 + 4 * 2 + (2 + 2 * inputIds.length) * fieldLength);
        // skips canvas_fill_value: the canvas is opaque black
        int index = 2 + 4 * 2;
        int overlayWidth = readDescriptorField(overlayDescriptor, index, fieldLength);
        int overlayHeight = readDescriptorField(overlayDescriptor, index + fieldLength, fieldLength);
        index += 2 * fieldLength;

        // as an alpha plane, the opaque black canvas reads as transparent
        overlayTarget.fill(PixelBox.ofSize(overlayWidth, overlayHeight), 0xFF000000, alphaOnly);

        for (int inputIndex = 0; inputIndex < inputIds.length; inputIndex++) {
            int horizontalOffset = readDescriptorField(overlayDescriptor, index, fieldLength);
            int verticalOffset = readDescriptorField(overlayDescriptor, index + fieldLength, fieldLength);
            index += 2 * fieldLength;

            PixelBox overlayBoxInLayer = new PixelBox(
                    -horizontalOffset,
                    -verticalOffset,
                    overlayWidth - horizontalOffset,
                    overlayHeight - verticalOffset);
            PixelTarget layerTarget = overlayTarget.forSourceImage(
                    AxisTransform.translation(horizontalOffset, verticalOffset), overlayBoxInLayer);
            if (!layerTarget.isEmpty()) {
                getInputImage(inputIndex).decodeInto(layerTarget, alphaOnly, workspaces);
            }
        }
    }

    private HEVCDecoderConfigurationRecord requireHevcConfiguration() {
        if (hvcConfig == null) {
            throw new HeicFormatException("Image %d has no HEVC decoder configuration (hvcC)".formatted(id));
        }
        return hvcConfig;
    }

    private void requireDescriptorLength(String imageKind, byte[] descriptor, int requiredLength) {
        if (descriptor.length < requiredLength) {
            throw new HeicFormatException("The %s descriptor of image %d has %d bytes, %d needed"
                    .formatted(imageKind, id, descriptor.length, requiredLength));
        }
    }

    // a field of 2 or 4 bytes, most significant first
    private static int readDescriptorField(byte[] descriptor, int offset, int length) {
        int value = 0;
        for (int i = 0; i < length; i++) {
            value = (value << 8) | (descriptor[offset + i] & 0xFF);
        }
        return value;
    }

    /**
     * The data of this item: its extents, one after the other, from the file or from the idat box.
     */
    byte[] readItemBytes() {
        if (location == null || location.extents().isEmpty()) {
            throw new HeicFormatException("Item %d has no data".formatted(id));
        }
        if (location.extents().size() == 1) {
            IlocItemExtent extent = location.extents().get(0);
            return readExtent(location.constructionMethod(), location.baseOffset() + extent.offset(), extent);
        }
        ByteArrayOutputStream itemBytes = new ByteArrayOutputStream();
        for (IlocItemExtent extent : location.extents()) {
            byte[] extentBytes =
                    readExtent(location.constructionMethod(), location.baseOffset() + extent.offset(), extent);
            itemBytes.write(extentBytes, 0, extentBytes.length);
        }
        return itemBytes.toByteArray();
    }

    private byte[] readExtent(int constructionMethod, long offset, IlocItemExtent extent) {
        return switch (constructionMethod) {
            case FILE_OFFSET -> {
                byte[] heicData = parent.getHeicData();
                // an extent length of 0 runs to the end of the file
                long length = extent.length() == 0 ? heicData.length - offset : extent.length();
                if (offset < 0 || length < 0 || offset + length > heicData.length) {
                    throw new HeicFormatException("Item %d has %d bytes at byte %d, outside the %d-byte file"
                            .formatted(id, length, offset, heicData.length));
                }
                yield Arrays.copyOfRange(heicData, (int) offset, (int) (offset + length));
            }
            case IDAT_OFFSET -> parent.getHeader().getItemDataBoxContent(offset, extent.length());
            default ->
                throw new HeicFormatException("Item %d is stored with construction method %d, which is not supported"
                        .formatted(id, constructionMethod));
        };
    }

    @Override
    public String toString() {
        StringBuilder line = new StringBuilder("[%d,%s] ".formatted(id, imageType));
        if (colr != null) {
            line.append(colr.getColourType()).append(' ');
        }
        if (bitsPerChannel.length > 0) {
            line.append(Arrays.toString(bitsPerChannel)).append(' ');
        }
        if (ispeWidth > 0) {
            line.append(ispeWidth).append('x').append(ispeHeight).append(' ');
        }
        if (rotationQuarterTurns > 0) {
            line.append(rotationQuarterTurns * 90).append("° ");
        }
        if (auxiliaryReferenceType != AuxiliaryReferenceType.UNDEFINED) {
            line.append(auxiliaryReferenceType).append(' ');
        }
        if (derivativeType != null) {
            line.append(derivativeType)
                    .append(' ')
                    .append(Arrays.toString(parent.getHeader().getDerivedList(id)));
        }
        return line.toString();
    }
}
