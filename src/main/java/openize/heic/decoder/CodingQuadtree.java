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

class CodingQuadtree {

    public CodingQuadtree(
            BitStreamWithNalSupport stream, SliceSegmentHeader header, int x0, int y0, int log2CbSize, int cqtDepth) {
        SeqParameterSetRbsp sps = header.pps.sps;
        HeicPicture picture = header.parentPicture;
        boolean splitCuFlag;

        if (x0 + (1L << log2CbSize) <= (header.pps.sps.picWidthInLumaSamples & 0xFFFFFFFFL)
                && y0 + (1L << log2CbSize) <= (header.pps.sps.picHeightInLumaSamples & 0xFFFFFFFFL)
                && log2CbSize > (sps.getMinCbLog2SizeY() & 0xFF)) {
            splitCuFlag = stream.getCabac().readSplitCuFlag(x0, y0, picture, cqtDepth);
        } else {
            splitCuFlag = log2CbSize > (sps.getMinCbLog2SizeY() & 0xFF);
        }

        /*UInt32*/ long log2MinCuQpDeltaSize =
                ((header.pps.sps.getCtbLog2SizeY() & 0xFF) - (header.pps.diffCuQpDeltaDepth & 0xFFFFFFFFL))
                        & 0xFFFFFFFFL;
        /*UInt32*/ long log2MinCuChromaQpOffsetSize = ((header.pps.sps.getCtbLog2SizeY() & 0xFF)
                        - (header.pps.ppsRangeExt.diffCuChromaQpOffsetDepth & 0xFFFFFFFFL))
                & 0xFFFFFFFFL;

        if (header.pps.cuQpDeltaEnabledFlag && log2CbSize >= (log2MinCuQpDeltaSize & 0xFFFFFFFFL)) {
            stream.getContext().setCuQpDeltaCoded(false);
            stream.getContext().setCuQpDeltaVal(0);
        }

        if (header.cuChromaQpOffsetEnabledFlag && log2CbSize >= (log2MinCuChromaQpOffsetSize & 0xFFFFFFFFL)) {
            stream.getContext().setCuChromaQpOffsetCoded(false);
        }

        if (splitCuFlag) {
            int x1 = x0 + (1 << (log2CbSize - 1));
            int y1 = y0 + (1 << (log2CbSize - 1));
            new CodingQuadtree(stream, header, x0, y0, log2CbSize - 1, cqtDepth + 1);

            if (x1 < (header.pps.sps.picWidthInLumaSamples & 0xFFFFFFFFL))
                new CodingQuadtree(stream, header, x1, y0, log2CbSize - 1, cqtDepth + 1);
            if (y1 < (header.pps.sps.picHeightInLumaSamples & 0xFFFFFFFFL))
                new CodingQuadtree(stream, header, x0, y1, log2CbSize - 1, cqtDepth + 1);
            if (x1 < (header.pps.sps.picWidthInLumaSamples & 0xFFFFFFFFL)
                    && y1 < (header.pps.sps.picHeightInLumaSamples & 0xFFFFFFFFL))
                new CodingQuadtree(stream, header, x1, y1, log2CbSize - 1, cqtDepth + 1);
        } else {
            picture.setCtDepth(x0, y0, log2CbSize, cqtDepth);
            new CodingUnit(stream, header, x0, y0, log2CbSize);
        }
    }
}
