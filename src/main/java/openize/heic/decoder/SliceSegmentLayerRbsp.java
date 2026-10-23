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

import openize.heic.decoder.io.BitStreamWithNalSupport;


public class SliceSegmentLayerRbsp extends NalUnit
{
    public final SliceSegmentHeader sliceHeader;
    public SliceSegmentData data;

    public SliceSegmentLayerRbsp(BitStreamWithNalSupport stream, /*UInt64*/long startPosition, int size)
    {
        super(stream, startPosition, size);

        sliceHeader = new SliceSegmentHeader(stream, this);

        stream.getCabac().initialization(sliceHeader);

        if (sliceHeader.firstSliceSegmentInPicFlag)
        {
            HeicPicture picture = new HeicPicture(sliceHeader, nalHeader.copy());
            stream.getContext().getPictures().put(stream.getCurrentImageId(), picture);
            sliceHeader.parentPicture = picture;

            if (nalHeader.isIrapPicture())
            {
                if (nalHeader.isIdrPicture() ||
                        nalHeader.isBlaPicture() ||
                        //first_decoded_picture ||
                        stream.getContext().firstAfterEndOfSequenceNAL)
                {
                    stream.getContext().noRaslOutputFlag = true;
                    stream.getContext().firstAfterEndOfSequenceNAL = false;
                }
                else
                {
                    stream.getContext().noRaslOutputFlag = false;
                    stream.getContext().handleCraAsBlaFlag = false;
                }
            }
        }

        if (stream.getContext().getPictures().isEmpty())
        {
            throw new IndexOutOfBoundsException();
        }

        HeicPicture currentPicture = stream.getContext().getPictures().get(stream.getCurrentImageId());

        if (sliceHeader.parentPicture == null)
        {
            sliceHeader.parentPicture = currentPicture;
        }

        sliceHeader.sliceIndex = currentPicture.getSliceHeaders().size();
        currentPicture.getSliceHeaders().put(sliceHeader.sliceIndex, sliceHeader);

        stream.getContext().decodingPictureOrderCount(currentPicture);

        /*data =*/ new SliceSegmentData(stream, sliceHeader);
    }
}
