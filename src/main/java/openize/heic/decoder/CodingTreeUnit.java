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

class CodingTreeUnit {
    public CodingTreeUnit(BitStreamWithNalSupport stream, SliceSegmentHeader header) {
        SeqParameterSetRbsp sps = header.pps.sps;
        HeicPicture picture = header.parentPicture;

        int xCtb = (int) ((header.getCtbAddrInRs() & 0xFFFFFFFFL) % (sps.getPicWidthInCtbsY() & 0xFFFFFFFFL))
                << (sps.getCtbLog2SizeY() & 0xFF);
        int yCtb = (int) ((header.getCtbAddrInRs() & 0xFFFFFFFFL) / (sps.getPicWidthInCtbsY() & 0xFFFFFFFFL))
                << (sps.getCtbLog2SizeY() & 0xFF);

        picture.sliceAddrRs[xCtb >> (sps.getCtbLog2SizeY() & 0xFF)][yCtb >> (sps.getCtbLog2SizeY() & 0xFF)] =
                (int) header.getSliceAddrRs();
        picture.sliceHeaderIndex.fill(xCtb, yCtb, (int) sps.getCtbSizeY(), header.sliceIndex);

        if (header.sliceSaoLumaFlag || header.sliceSaoChromaFlag)
            new Sao(stream, xCtb >> (sps.getCtbLog2SizeY() & 0xFF), yCtb >> (sps.getCtbLog2SizeY() & 0xFF), header);

        new CodingQuadtree(stream, header, xCtb, yCtb, sps.getCtbLog2SizeY() & 0xFF, 0);
    }
}
