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

class TransformTree {
    public TransformTree(
            BitStreamWithNalSupport stream,
            SliceSegmentHeader header,
            boolean intraSplitFlag, /*UInt32*/
            long maxTrafoDepth,
            PartMode partMode,
            int x0,
            int y0,
            int xBase,
            int yBase,
            int log2TrafoSize,
            int trafoDepth,
            int blkIdx) {
        SeqParameterSetRbsp sps = header.pps.sps;
        HeicPicture picture = header.parentPicture;

        boolean splitTransformFlag;
        if (log2TrafoSize <= (sps.getMaxTbLog2SizeY() & 0xFF)
                && log2TrafoSize > (sps.getMinTbLog2SizeY() & 0xFF)
                && trafoDepth < (maxTrafoDepth & 0xFFFFFFFFL)
                && !(intraSplitFlag && (trafoDepth == 0))) {
            splitTransformFlag = stream.getCabac().readSplitTransformFlag(log2TrafoSize);
        } else {
            boolean interSplitFlag = (sps.maxTransformHierarchyDepthInter & 0xFFFFFFFFL) == 0
                    && picture.cuPredMode.get(x0, y0) == PredMode.MODE_INTER
                    && partMode != PartMode.part2Nx2N
                    && trafoDepth == 0;

            splitTransformFlag = log2TrafoSize > (sps.getMaxTbLog2SizeY() & 0xFF)
                    || (intraSplitFlag && trafoDepth == 0)
                    || interSplitFlag;
        }

        if ((log2TrafoSize > 2 && (sps.getChromaArrayType() & 0xFFFFFFFFL) != 0)
                || (sps.getChromaArrayType() & 0xFFFFFFFFL) == 3) {
            if (trafoDepth == 0 || picture.cbfCb.getBit(xBase, yBase, trafoDepth - 1)) {
                picture.cbfCb.setBit(x0, y0, trafoDepth, stream.getCabac().readCbfChroma(trafoDepth));

                if ((sps.getChromaArrayType() & 0xFFFFFFFFL) == 2 && (!splitTransformFlag || log2TrafoSize == 3)) {
                    picture.cbfCb.setBit(
                            x0,
                            y0 + (1 << (log2TrafoSize - 1)),
                            trafoDepth,
                            stream.getCabac().readCbfChroma(trafoDepth));
                }
            }

            if (trafoDepth == 0 || picture.cbfCr.getBit(xBase, yBase, trafoDepth - 1)) {
                picture.cbfCr.setBit(x0, y0, trafoDepth, stream.getCabac().readCbfChroma(trafoDepth));

                if ((sps.getChromaArrayType() & 0xFFFFFFFFL) == 2 && (!splitTransformFlag || log2TrafoSize == 3)) {
                    picture.cbfCr.setBit(
                            x0,
                            y0 + (1 << (log2TrafoSize - 1)),
                            trafoDepth,
                            stream.getCabac().readCbfChroma(trafoDepth));
                }
            }
        }

        if (splitTransformFlag) {
            int x1 = x0 + (1 << (log2TrafoSize - 1));
            int y1 = y0 + (1 << (log2TrafoSize - 1));
            new TransformTree(
                    stream,
                    header,
                    intraSplitFlag,
                    maxTrafoDepth,
                    partMode,
                    x0,
                    y0,
                    x0,
                    y0,
                    log2TrafoSize - 1,
                    trafoDepth + 1,
                    0);
            new TransformTree(
                    stream,
                    header,
                    intraSplitFlag,
                    maxTrafoDepth,
                    partMode,
                    x1,
                    y0,
                    x0,
                    y0,
                    log2TrafoSize - 1,
                    trafoDepth + 1,
                    1);
            new TransformTree(
                    stream,
                    header,
                    intraSplitFlag,
                    maxTrafoDepth,
                    partMode,
                    x0,
                    y1,
                    x0,
                    y0,
                    log2TrafoSize - 1,
                    trafoDepth + 1,
                    2);
            new TransformTree(
                    stream,
                    header,
                    intraSplitFlag,
                    maxTrafoDepth,
                    partMode,
                    x1,
                    y1,
                    x0,
                    y0,
                    log2TrafoSize - 1,
                    trafoDepth + 1,
                    3);
        } else {
            if (picture.cuPredMode.get(x0, y0) == PredMode.MODE_INTRA
                    || trafoDepth != 0
                    || picture.cbfCb.getBit(x0, y0, trafoDepth)
                    || picture.cbfCr.getBit(x0, y0, trafoDepth)
                    || ((header.pps.sps.getChromaArrayType() & 0xFFFFFFFFL) == 2
                            && (picture.cbfCb.getBit(x0, y0 + (1 << (log2TrafoSize - 1)), trafoDepth)
                                    || picture.cbfCr.getBit(x0, y0 + (1 << (log2TrafoSize - 1)), trafoDepth)))) {
                picture.cbfLuma.setBit(x0, y0, trafoDepth, stream.getCabac().readCbfLuma(trafoDepth));
            }

            picture.markTransformBlock(x0, y0, 1 << log2TrafoSize);
            new TransformUnit(stream, header, partMode, x0, y0, xBase, yBase, log2TrafoSize, trafoDepth, blkIdx);
        }
    }
}
