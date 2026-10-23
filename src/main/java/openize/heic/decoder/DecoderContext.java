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

import java.util.HashMap;
import openize.heic.decoder.io.BitStreamWithNalSupport;

public class DecoderContext {
    public final HashMap<Integer, VideoParameterSetRbsp> getVPS() {
        return vPS;
    }

    private final HashMap<Integer, VideoParameterSetRbsp> vPS;

    public final HashMap<Integer, SeqParameterSetRbsp> getSPS() {
        return sPS;
    }

    private final HashMap<Integer, SeqParameterSetRbsp> sPS;

    public final HashMap<Integer, PicParameterSetRbsp> getPPS() {
        return pPS;
    }

    private final HashMap<Integer, PicParameterSetRbsp> pPS;

    public final HashMap<Long, HeicPicture> getPictures() {
        return pictures;
    }

    private final HashMap<Long, HeicPicture> pictures;

    // public boolean first_decoded_picture = true;
    public boolean noRaslOutputFlag = false;
    public boolean handleCraAsBlaFlag = false;
    public boolean firstAfterEndOfSequenceNAL = false;

    public int picOrderCntMsb;
    public int prevPicOrderCntLsb; // at precTid0Pic
    public int prevPicOrderCntMsb; // at precTid0Pic

    // coding_unit
    boolean cuTransquantBypassFlag;
    final boolean[] transformSkipFlag = new boolean[3];

    // CuQp
    public final boolean isCuQpDeltaCoded() {
        return isCuQpDeltaCoded;
    }
    // CuQp
    public final void setCuQpDeltaCoded(boolean value) {
        isCuQpDeltaCoded = value;
    }

    private boolean isCuQpDeltaCoded;

    public final int getCuQpDeltaVal() {
        return cuQpDeltaVal;
    }

    public final void setCuQpDeltaVal(int value) {
        cuQpDeltaVal = value;
    }

    private int cuQpDeltaVal;

    public final boolean isCuChromaQpOffsetCoded() {
        return isCuChromaQpOffsetCoded;
    }

    public final void setCuChromaQpOffsetCoded(boolean value) {
        isCuChromaQpOffsetCoded = value;
    }

    private boolean isCuChromaQpOffsetCoded;

    public final int getCuQpOffsetCb() {
        return cuQpOffsetCb;
    }

    public final void setCuQpOffsetCb(int value) {
        cuQpOffsetCb = value;
    }

    private int cuQpOffsetCb;

    public final int getCuQpOffsetCr() {
        return cuQpOffsetCr;
    }

    public final void setCuQpOffsetCr(int value) {
        cuQpOffsetCr = value;
    }

    private int cuQpOffsetCr;

    public final int getQpY() {
        return qpY;
    }

    public final void setQpY(int value) {
        qpY = value;
    }

    private int qpY;

    public final int getQpCb() {
        return qpCb;
    }

    public final void setQpCb(int value) {
        qpCb = value;
    }

    private int qpCb;

    public final int getQpCr() {
        return qpCr;
    }

    public final void setQpCr(int value) {
        qpCr = value;
    }

    private int qpCr;

    public final int getLastQpYPrev() {
        return lastQpYPrev;
    }

    public final void setLastQpYPrev(int value) {
        lastQpYPrev = value;
    }

    private int lastQpYPrev;

    public final int getCurrentQgX() {
        return currentQgX;
    }

    public final void setCurrentQgX(int value) {
        currentQgX = value;
    }

    private int currentQgX;

    public final int getCurrentQgY() {
        return currentQgY;
    }

    public final void setCurrentQgY(int value) {
        currentQgY = value;
    }

    private int currentQgY;

    public final int getCurrentQpY() {
        return currentQpY;
    }

    public final void setCurrentQpY(int value) {
        currentQpY = value;
    }

    private int currentQpY;

    public DecoderContext() {
        vPS = new HashMap<>();
        sPS = new HashMap<>();
        pPS = new HashMap<>();
        pictures = new HashMap<>();

        prevPicOrderCntLsb = 0;
        prevPicOrderCntMsb = 0;
    }

    public final void addNalContext(NalUnitType type, NalUnit nalUnit) {
        switch (type) {
            case VPS_NUT:
                getVPS().put(((VideoParameterSetRbsp) nalUnit).vpsVideoParameterSetId & 0xFF, (VideoParameterSetRbsp)
                        nalUnit);
                break;
            case SPS_NUT:
                getSPS().put(((SeqParameterSetRbsp) nalUnit).spsSeqParameterSetId & 0xFF, (SeqParameterSetRbsp)
                        nalUnit);
                break;
            case PPS_NUT:
                getPPS().put(((PicParameterSetRbsp) nalUnit).ppsPicParameterSetId & 0xFF, (PicParameterSetRbsp)
                        nalUnit);
                break;
        }
    }

    // 8.3.1 Decoding process for picture order count
    public final void decodingPictureOrderCount(HeicPicture picture) {
        NalHeader header = picture.nalHeader.copy();
        SliceSegmentHeader sliceHeader = picture.getSliceHeaders().get(0);

        if (header.isIrapPicture() && noRaslOutputFlag) {
            picOrderCntMsb = 0;
        } else {
            int maxPicOrderCntLsb = (int) picture.sps.getMaxPicOrderCntLsb();

            if ((sliceHeader.slicePicOrderCntLsb < prevPicOrderCntLsb)
                    && ((prevPicOrderCntLsb - sliceHeader.slicePicOrderCntLsb) >= (maxPicOrderCntLsb / 2))) {
                picOrderCntMsb = prevPicOrderCntMsb + maxPicOrderCntLsb;
            } else if ((sliceHeader.slicePicOrderCntLsb > prevPicOrderCntLsb)
                    && ((sliceHeader.slicePicOrderCntLsb - prevPicOrderCntLsb) > (maxPicOrderCntLsb / 2))) {
                picOrderCntMsb = prevPicOrderCntMsb - maxPicOrderCntLsb;
            } else {
                picOrderCntMsb = prevPicOrderCntMsb;
            }
        }

        picture.picOrderCntVal = picOrderCntMsb + sliceHeader.slicePicOrderCntLsb;
        picture.pictureOrderCntLsb = sliceHeader.slicePicOrderCntLsb;

        if ((header.nuhTemporalIdPlus1 & 0xFF) == 1
                && !header.isSublayerNonReference()
                && !header.isRaslPicture()
                && !header.isRadlPicture()) {
            prevPicOrderCntLsb = sliceHeader.slicePicOrderCntLsb;
            prevPicOrderCntMsb = picOrderCntMsb;
        }
    }

    // 8.6.1 Derivation process for quantization parameters
    final void derivationOfQuantizationParameters(
            BitStreamWithNalSupport stream, HeicPicture picture, int xCb, int yCb, int xCUBase, int yCUBase) {
        // Input to this process is a luma location ( xCb, yCb ) specifying the top-left sample of the current luma
        // coding block relative to the top-left luma sample of the current picture.
        // In this process, the variable QpY, the luma quantization parameter Qp′Y and
        // the chroma quantization parameters Qp′Cb and Qp′Cr are derived.

        SliceSegmentHeader sliceHeader = picture.getSliceHeaders().get(picture.sliceHeaderIndex[xCb][yCb]);

        int xQg = xCb - (xCb & ((1 << (int) picture.pps.getLog2MinCuQpDeltaSize()) - 1));
        int yQg = yCb - (yCb & ((1 << (int) picture.pps.getLog2MinCuQpDeltaSize()) - 1));

        if (xQg != stream.getContext().getCurrentQgX()
                || yQg != stream.getContext().getCurrentQgY()) {
            stream.getContext().setLastQpYPrev(stream.getContext().getCurrentQpY());
            stream.getContext().setCurrentQgX(xQg);
            stream.getContext().setCurrentQgY(yQg);
        }

        int qPYPREV, qPYPRED;

        int ctbLSBMask = ((1 << (picture.sps.getCtbLog2SizeY() & 0xFF)) - 1);
        boolean firstInCTBRow = (xQg == 0 && ((yQg & ctbLSBMask) == 0));

        /*UInt32*/ long firstCtbInSliceRS = sliceHeader.getSliceAddrRs();

        /*UInt32*/ long sliceStartX =
                ((((firstCtbInSliceRS & 0xFFFFFFFFL) % (picture.sps.getPicWidthInCtbsY() & 0xFFFFFFFFL)) & 0xFFFFFFFFL)
                                * (picture.sps.getCtbSizeY() & 0xFFFFFFFFL))
                        & 0xFFFFFFFFL;
        /*UInt32*/ long sliceStartY =
                ((((firstCtbInSliceRS & 0xFFFFFFFFL) / (picture.sps.getPicWidthInCtbsY() & 0xFFFFFFFFL)) & 0xFFFFFFFFL)
                                * (picture.sps.getCtbSizeY() & 0xFFFFFFFFL))
                        & 0xFFFFFFFFL;

        boolean firstQgInSlice = ((int) sliceStartX == xQg && (int) sliceStartY == yQg);

        boolean firstQgInTile = false;

        if (picture.pps.tilesEnabledFlag) {
            // firstQgInTile recalculated
            if ((xQg & ctbLSBMask) == 0 && (yQg & ctbLSBMask) == 0) {
                int tileX = xQg >> picture.sps.getCtbLog2SizeY();
                int tileY = yQg >> picture.sps.getCtbLog2SizeY();

                firstQgInTile = picture.pps.checkIfTileIsFirst(tileX, tileY);
            }
        }

        if (firstQgInSlice || firstQgInTile || (firstInCTBRow && picture.pps.entropyCodingSyncEnabledFlag)) {
            qPYPREV = sliceHeader.getSliceQPY();
        } else {
            qPYPREV = stream.getContext().getLastQpYPrev();
        }

        int qPYA = getNeighbouringQpY(picture, sliceHeader, qPYPREV, xQg, yQg, xQg - 1, yQg);
        int qPYB = getNeighbouringQpY(picture, sliceHeader, qPYPREV, xQg, yQg, xQg, yQg - 1);

        qPYPRED = (qPYA + qPYB + 1) >> 1;

        int qpYByPosition =
                ((qPYPRED + stream.getContext().getCuQpDeltaVal() + 52 + 2 * (picture.sps.getQpBdOffsetY() & 0xFF))
                                % (52 + (picture.sps.getQpBdOffsetY() & 0xFF)))
                        - (picture.sps.getQpBdOffsetY() & 0xFF);

        stream.getContext().setQpY(qpYByPosition + (picture.sps.getQpBdOffsetY() & 0xFF));
        stream.getContext().setCurrentQpY(qpYByPosition);

        int qPiCb, qPiCr, qPCb, qPCr;
        if ((picture.sps.getChromaArrayType() & 0xFFFFFFFFL) != 0) {
            if (!picture.tuResidualActFlag[xCb][yCb]) {
                qPiCb = MathExtra.clip3(
                        -(picture.sps.getQpBdOffsetC() & 0xFF),
                        57,
                        qpYByPosition
                                + picture.pps.ppsCbQpOffset
                                + sliceHeader.sliceCbQpOffset
                                + stream.getContext().getCuQpOffsetCb());
                qPiCr = MathExtra.clip3(
                        -(picture.sps.getQpBdOffsetC() & 0xFF),
                        57,
                        qpYByPosition
                                + picture.pps.ppsCrQpOffset
                                + sliceHeader.sliceCrQpOffset
                                + stream.getContext().getCuQpOffsetCr());
            } else {
                qPiCb = MathExtra.clip3(
                        -(picture.sps.getQpBdOffsetC() & 0xFF),
                        57,
                        qpYByPosition
                                + picture.pps.getPpsActQpOffsetCb()
                                + sliceHeader.sliceActCbQpOffset
                                + stream.getContext().getCuQpOffsetCb());
                qPiCr = MathExtra.clip3(
                        -(picture.sps.getQpBdOffsetC() & 0xFF),
                        57,
                        qpYByPosition
                                + picture.pps.getPpsActQpOffsetCr()
                                + sliceHeader.sliceActCrQpOffset
                                + stream.getContext().getCuQpOffsetCr());
            }

            if ((picture.sps.getChromaArrayType() & 0xFFFFFFFFL) == 1) {
                qPCb = getChromaQpFromQPi(qPiCb);
                qPCr = getChromaQpFromQPi(qPiCr);
            } else {
                qPCb = Math.min(qPiCb, 51);
                qPCr = Math.min(qPiCr, 51);
            }

            stream.getContext().setQpCb(qPCb + (picture.sps.getQpBdOffsetC() & 0xFF));
            stream.getContext().setQpCr(qPCr + (picture.sps.getQpBdOffsetC() & 0xFF));
        }

        int log2CbSize = picture.log2CbSizeByPosition[xCUBase][yCUBase];

        if (log2CbSize < 3) log2CbSize = 3;

        picture.setQpY(xCUBase, yCUBase, log2CbSize, stream.getContext().getQpY());
    }

    private int getNeighbouringQpY(
            HeicPicture picture, SliceSegmentHeader sliceHeader, int qPYPREV, int xQg, int yQg, int xQgN, int yQgN) {
        if (!picture.checkZScanAvailability(xQg, yQg, xQgN, yQgN)) {
            return qPYPREV;
        } else {
            int xTmp = xQgN >> (picture.sps.getMinTbLog2SizeY() & 0xFF);
            int yTmp = yQgN >> (picture.sps.getMinTbLog2SizeY() & 0xFF);
            int minTbAddrA = picture.pps.minTbAddrZs[xTmp][yTmp];
            int ctbAddrA = minTbAddrA
                    >> (2 * ((picture.sps.getCtbLog2SizeY() & 0xFF) - (picture.sps.getMinTbLog2SizeY() & 0xFF)));

            if (ctbAddrA != (sliceHeader.getCtbAddrInTs() & 0xFFFFFFFFL)) {
                return qPYPREV;
            } else {
                return picture.qpYByPosition[xQgN][yQgN];
            }
        }
    }

    private int getChromaQpFromQPi(int qPi) {
        if (qPi < 30) return qPi;
        else if (qPi > 43) return qPi - 6;
        else return qPiToQpCTable[qPi - 30];
    }

    private static final int[] qPiToQpCTable = {29, 30, 31, 32, 33, 33, 34, 34, 35, 35, 36, 36, 37, 37};
}
