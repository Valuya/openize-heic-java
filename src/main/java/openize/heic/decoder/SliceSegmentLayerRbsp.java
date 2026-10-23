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

        new SliceSegmentDecoder(stream, header, picture).decode();
    }
}
