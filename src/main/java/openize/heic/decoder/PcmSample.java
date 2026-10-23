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


class PcmSample
{

    public PcmSample(BitStreamWithNalSupport stream, SliceSegmentHeader header, int log2CbSize)
    {
        HeicPicture picture = header.parentPicture;

        picture.pcmSampleLuma = new int[1 << (log2CbSize << 1)];

        for (int i = 0; i < 1 << (log2CbSize << 1); i++)
            picture.pcmSampleLuma[i] = stream.read(header.pps.sps.pcmSampleBitDepthLuma & 0xFF);

        picture.pcmSampleChroma = new int[((2 << (log2CbSize << 1)) / ((header.pps.sps.getSubWidthC() & 0xFF) * (header.pps.sps.getSubHeightC() & 0xFF)))];

        if ((header.pps.sps.getChromaArrayType() & 0xFFFFFFFFL) != 0)
        {
            for (int i = 0; i < ((2 << (log2CbSize << 1)) / ((header.pps.sps.getSubWidthC() & 0xFF) * (header.pps.sps.getSubHeightC() & 0xFF))); i++)
                picture.pcmSampleChroma[i] = stream.read(header.pps.sps.pcmSampleBitDepthChroma & 0xFF);
        }
    }
}
