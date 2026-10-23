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

/**
 * A coded slice segment NAL unit (7.3.2.9): its header is read, then its coding tree blocks are decoded into the
 * current picture.
 */
class SliceSegmentLayerRbsp extends NalUnit {
    SliceSegmentLayerRbsp(BitStreamWithNalSupport stream, long startPosition, int size) {
        super(stream, startPosition, size);

        DecoderContext context = stream.getContext();
        SliceSegmentHeader header = new SliceSegmentHeader(stream, nalHeader, context.getCurrentPictureOptional());
        HeicPicture picture = header.firstSliceSegmentInPicFlag
                ? context.startPicture(header)
                : context.getCurrentPictureOptional()
                        .orElseThrow(() -> new HeicFormatException("A picture lacks its first slice segment"));

        // the slice segment data, read as RBSP bytes by the arithmetic decoder
        long endBytePosition = getEndPosition() / 8;
        int maximumLength = (int) Math.max(0, endBytePosition - stream.getBitPosition() / 8);
        byte[] sliceData = picture.workspace.byteBuffer(DecoderWorkspace.SLICE_DATA, maximumLength);
        int length = stream.copyRbsp(endBytePosition, sliceData);
        stream.getCabac().startSliceSegmentData(sliceData, length, nalHeader.type, getSize());

        new SliceSegmentDecoder(stream, header, picture).decode();
    }
}
