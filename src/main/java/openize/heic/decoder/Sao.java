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

class Sao {
    boolean saoMergeLeftFlag;
    boolean saoMergeUpFlag;

    boolean leftCtbInSliceSeg;
    boolean leftCtbInTile;

    boolean upCtbInSliceSeg;
    boolean upCtbInTile;

    byte saoTypeIdxLuma;
    byte saoTypeIdxChroma;
    byte saoEoClassLuma;
    byte saoEoClassChroma;

    public Sao(BitStreamWithNalSupport stream, int rx, int ry, SliceSegmentHeader header) {
        SeqParameterSetRbsp sps = header.pps.sps;
        HeicPicture picture = header.parentPicture;

        if (rx > 0) {
            leftCtbInSliceSeg = (header.getCtbAddrInRs() & 0xFFFFFFFFL) > (header.getSliceAddrRs() & 0xFFFFFFFFL);
            leftCtbInTile = header.pps.tileIdByCtbAddrTs[(int) (header.getCtbAddrInTs())]
                    == header.pps
                            .tileIdByCtbAddrTs[
                            (int) (header.pps
                                    .ctbAddrRsToTs[
                                    (int) (((header.getCtbAddrInRs() & 0xFFFFFFFFL) - 1) & 0xFFFFFFFFL)])];

            if (leftCtbInSliceSeg && leftCtbInTile)
                saoMergeLeftFlag = stream.getCabac().readSaoMergeFlag();
        }

        if (ry > 0 && !saoMergeLeftFlag) {
            upCtbInSliceSeg =
                    (((header.getCtbAddrInRs() & 0xFFFFFFFFL) - (sps.getPicWidthInCtbsY() & 0xFFFFFFFFL)) & 0xFFFFFFFFL)
                            >= (header.getSliceAddrRs() & 0xFFFFFFFFL);
            upCtbInTile = header.pps.tileIdByCtbAddrTs[(int) (header.getCtbAddrInTs())]
                    == header.pps
                            .tileIdByCtbAddrTs[
                            (int) (header.pps
                                    .ctbAddrRsToTs[
                                    (int) (((header.getCtbAddrInRs() & 0xFFFFFFFFL)
                                                    - (sps.getPicWidthInCtbsY() & 0xFFFFFFFFL))
                                            & 0xFFFFFFFFL)])];

            if (upCtbInSliceSeg && upCtbInTile)
                saoMergeUpFlag = stream.getCabac().readSaoMergeFlag();
        }

        int chromaCount = ((header.pps.sps.getChromaArrayType() & 0xFFFFFFFFL) != 0 ? 3 : 1);

        for (int cIdx = 0; cIdx < chromaCount; cIdx++) {
            if (picture.saoOffsetAbs[cIdx][rx][ry] == null) picture.saoOffsetAbs[cIdx][rx][ry] = new int[4];

            if (picture.saoOffsetSign[cIdx][rx][ry] == null) picture.saoOffsetSign[cIdx][rx][ry] = new int[4];

            if (picture.saoOffsetVal[cIdx][rx][ry] == null) picture.saoOffsetVal[cIdx][rx][ry] = new int[5];

            if (!saoMergeUpFlag && !saoMergeLeftFlag) {

                if ((header.sliceSaoLumaFlag && cIdx == 0) || (header.sliceSaoChromaFlag && cIdx > 0)) {
                    if (cIdx == 0) {
                        saoTypeIdxLuma = (byte) stream.getCabac().readSaoTypeIdx();
                        picture.saoTypeIdx[cIdx][rx][ry] = saoTypeIdxLuma & 0xFF;
                    } else if (cIdx == 1) {
                        saoTypeIdxChroma = (byte) stream.getCabac().readSaoTypeIdx();
                        picture.saoTypeIdx[cIdx][rx][ry] = saoTypeIdxChroma & 0xFF;
                        picture.saoTypeIdx[cIdx + 1][rx][ry] = saoTypeIdxChroma & 0xFF;
                    }

                    if (picture.saoTypeIdx[cIdx][rx][ry] != 0) {
                        for (int i = 0; i < 4; i++) {
                            picture.saoOffsetAbs[cIdx][rx][ry][i] = stream.getCabac()
                                    .readSaoOffsetAbs(
                                            (cIdx == 0 ? sps.getBitDepthY() : sps.getBitDepthC()) & 0xFF); // ae(v)
                        }

                        if (picture.saoTypeIdx[cIdx][rx][ry] == 1) // Band offset
                        {
                            for (int i = 0; i < 4; i++) {
                                if (picture.saoOffsetAbs[cIdx][rx][ry][i] != 0)
                                    picture.saoOffsetSign[cIdx][rx][ry][i] =
                                            stream.getCabac().readSaoOffsetSign(); // ae(v)
                                else picture.saoOffsetSign[cIdx][rx][ry][i] = 0;
                            }

                            picture.saoBandPosition[cIdx][rx][ry] =
                                    stream.getCabac().readSaoBandPosition(); // ae(v)
                        } else // SaoTypeIdx == 2; Edge offset
                        {
                            if (cIdx == 0) {
                                saoEoClassLuma = stream.getCabac().readSaoClass(); // ae(v)
                                picture.saoEoClass[cIdx][rx][ry] = saoEoClassLuma & 0xFF;
                            } else if (cIdx == 1) {
                                saoEoClassChroma = stream.getCabac().readSaoClass(); // ae(v)
                                picture.saoEoClass[cIdx][rx][ry] = saoEoClassChroma & 0xFF;
                                picture.saoEoClass[cIdx + 1][rx][ry] = saoEoClassChroma & 0xFF;
                            }

                            for (int i = 0; i < 4; i++) picture.saoOffsetSign[cIdx][rx][ry][i] = i < 2 ? 0 : 1;
                        }
                    }
                } else {
                    picture.saoTypeIdx[cIdx][rx][ry] = 0;
                    picture.saoEoClass[cIdx][rx][ry] = 0;
                    picture.saoBandPosition[cIdx][rx][ry] = 0;

                    for (int i = 0; i < 4; i++) {
                        picture.saoOffsetAbs[cIdx][rx][ry][i] = 0;
                        picture.saoOffsetSign[cIdx][rx][ry][i] = 0;
                    }
                }
            } else if (saoMergeLeftFlag) {
                picture.saoTypeIdx[cIdx][rx][ry] = picture.saoTypeIdx[cIdx][rx - 1][ry];
                picture.saoEoClass[cIdx][rx][ry] = picture.saoEoClass[cIdx][rx - 1][ry];
                picture.saoBandPosition[cIdx][rx][ry] = picture.saoBandPosition[cIdx][rx - 1][ry];

                for (int i = 0; i < 4; i++) {
                    picture.saoOffsetAbs[cIdx][rx][ry][i] = picture.saoOffsetAbs[cIdx][rx - 1][ry][i];
                    picture.saoOffsetSign[cIdx][rx][ry][i] = picture.saoOffsetSign[cIdx][rx - 1][ry][i];
                }
            } else if (saoMergeUpFlag) {
                picture.saoTypeIdx[cIdx][rx][ry] = picture.saoTypeIdx[cIdx][rx][ry - 1];
                picture.saoEoClass[cIdx][rx][ry] = picture.saoEoClass[cIdx][rx][ry - 1];
                picture.saoBandPosition[cIdx][rx][ry] = picture.saoBandPosition[cIdx][rx][ry - 1];

                for (int i = 0; i < 4; i++) {
                    picture.saoOffsetAbs[cIdx][rx][ry][i] = picture.saoOffsetAbs[cIdx][rx][ry - 1][i];
                    picture.saoOffsetSign[cIdx][rx][ry][i] = picture.saoOffsetSign[cIdx][rx][ry - 1][i];
                }
            }

            if (header.pps.ppsRangeExt != null) {
                int log2OffsetScale = (int)
                        (cIdx == 0
                                ? header.pps.ppsRangeExt.log2SaoOffsetScaleLuma
                                : header.pps.ppsRangeExt.log2SaoOffsetScaleChroma);

                picture.saoOffsetVal[cIdx][rx][ry][0] = 0;

                for (int i = 0; i < 4; i++)
                    picture.saoOffsetVal[cIdx][rx][ry][i + 1] = (1 - 2 * picture.saoOffsetSign[cIdx][rx][ry][i])
                                    * picture.saoOffsetAbs[cIdx][rx][ry][i]
                            << log2OffsetScale;
            }
        }
    }
}
