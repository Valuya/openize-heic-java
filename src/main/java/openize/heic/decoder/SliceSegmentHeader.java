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

import openize.MathUtils;
import openize.heic.decoder.io.BitStreamWithNalSupport;

public class SliceSegmentHeader {
    public final PicParameterSetRbsp pps;
    public int sliceIndex;
    public HeicPicture parentPicture;
    final boolean firstSliceSegmentInPicFlag;
    boolean noOutputOfPriorPicsFlag;
    final byte slicePicParameterSetId;
    boolean dependentSliceSegmentFlag;
    /*UInt32*/ int sliceSegmentAddress;
    // final boolean[] slice_reserved_flag;
    SliceType sliceType;
    boolean picOutputFlag;
    byte colourPlaneId;
    int slicePicOrderCntLsb;
    boolean shortTermRefPicSetSpsFlag;
    int shortTermRefPicSetIdx;
    /*UInt32*/ int numLongTermSps;
    /*UInt32*/ int numLongTermPics;
    boolean[] usedByCurrPicLtFlag;
    /*UInt32*/ int[] ltIdxSps;
    int[] pocLsbLt;
    boolean[] deltaPocMsbPresentFlag;
    // /*UInt32*/ long[] delta_poc_msb_cycle_lt;
    boolean sliceTemporalMvpEnabledFlag;
    boolean sliceSaoLumaFlag;
    boolean sliceSaoChromaFlag;
    boolean numRefIdxActiveOverrideFlag;
    /*UInt32*/ long numRefIdxL0ActiveMinus1;
    /*UInt32*/ long numRefIdxL1ActiveMinus1;
    boolean collocatedFromL0Flag;
    /*UInt32*/ long collocatedRefIdx;
    boolean cabacInitFlag;
    boolean mvdL1ZeroFlag;
    byte fiveMinusMaxNumMergeCand;
    boolean useIntegerMvFlag;
    int sliceQpDelta;
    int sliceCbQpOffset;
    int sliceCrQpOffset;
    int sliceActYQpOffset;
    int sliceActCbQpOffset;
    int sliceActCrQpOffset;
    boolean cuChromaQpOffsetEnabledFlag;
    boolean deblockingFilterOverrideFlag;
    boolean sliceDeblockingFilterDisabledFlag;
    int sliceBetaOffsetDiv2;
    int sliceTcOffsetDiv2;
    boolean sliceLoopFilterAcrossSlicesEnabledFlag;
    /*UInt32*/ long numEntryPointOffsets;
    int offsetLenMinus1;
    // int[] entry_point_offset_minus1;
    /*UInt32*/ int sliceSegmentHeaderExtensionLength;
    // byte[] slice_segment_header_extension_data_byte;
    // ref_pic_lists_modification ref_pic_lists_modification;
    // public int CurrRpsIdx;
    // pred_weight_table pred_weight_table;
    private /*UInt32*/ long ctbAddrInRs;
    private /*UInt32*/ long ctbAddrInTs;

    public SliceSegmentHeader(BitStreamWithNalSupport stream, SliceSegmentLayerRbsp parent) {
        firstSliceSegmentInPicFlag = stream.readFlag();

        if (parent.nalHeader.isRapPicture()) {
            noOutputOfPriorPicsFlag = stream.readFlag();
        }

        slicePicParameterSetId = (byte) stream.readUev();

        pps = stream.getContext().getPPS().get(slicePicParameterSetId & 0xFF);
        SeqParameterSetRbsp sps = pps.sps;

        if (!firstSliceSegmentInPicFlag) {
            if (pps.dependentSliceSegmentsEnabledFlag) {
                dependentSliceSegmentFlag = stream.readFlag();
            }

            sliceSegmentAddress = (int) (stream.read(MathUtils.f64S32(
                            MathUtils.ceiling(MathUtils.log(sps.getPicSizeInCtbsY() & 0xFFFFFFFFL, 2))))
                    & 0xFFFFFFFFL);

            setCtbAddrInRs(sliceSegmentAddress);
            setCtbAddrInTs(pps.ctbAddrRsToTs[(int) (getCtbAddrInRs())]);
        }

        // int CuQpDeltaVal = 0;
        // slice_reserved_flag = new boolean[pps.num_extra_slice_header_bits];
        if (!dependentSliceSegmentFlag) {
            for (int i = 0; i < (pps.numExtraSliceHeaderBits & 0xFF); i++)
                /*slice_reserved_flag[i] =*/ stream.readFlag();

            sliceType = SliceType.get(stream.readUev());

            if (sliceType != SliceType.I) {
                throw new IllegalStateException("ALARM! NOT I FRAME");
            }

            if (pps.outputFlagPresentFlag) {
                picOutputFlag = stream.readFlag();
            }
            // else = true?

            if (sps.separateColourPlaneFlag) {
                colourPlaneId = (byte) stream.read(2);
            }

            if (parent.nalHeader.type != NalUnitType.IDR_W_RADL && parent.nalHeader.type != NalUnitType.IDR_N_LP) {

                slicePicOrderCntLsb = stream.read((sps.log2MaxPicOrderCntLsbMinus4 & 0xFF) + 4);
                shortTermRefPicSetSpsFlag = stream.readFlag();

                if (!shortTermRefPicSetSpsFlag) {

                    sps.stRefPicSets.put(
                            sps.stRefPicSets.size(), new StRefPicSet(stream, sps, sps.numShortTermRefPicSets));
                } else if ((sps.numShortTermRefPicSets & 0xFFFFFFFFL) > 1) {
                    shortTermRefPicSetIdx = stream.read(MathUtils.f64S32(
                            MathUtils.ceiling(MathUtils.log(sps.numShortTermRefPicSets & 0xFFFFFFFFL, 2))));
                }

                if (sps.longTermRefPicsPresentFlag) {
                    if ((sps.numLongTermRefPicsSps & 0xFFFFFFFFL) > 0) {
                        numLongTermSps = (int) stream.readUev();
                    }
                    numLongTermPics = (int) stream.readUev();

                    /*UInt32*/
                    int numLongTermSum = numLongTermSps + numLongTermPics;
                    usedByCurrPicLtFlag = new boolean[(int) (numLongTermSum & 0xFFFFFFFFL)];
                    ltIdxSps = new /*UInt32*/ int[(int) (numLongTermSum & 0xFFFFFFFFL)];
                    pocLsbLt = new int[(int) (numLongTermSum & 0xFFFFFFFFL)];
                    deltaPocMsbPresentFlag = new boolean[(int) (numLongTermSum & 0xFFFFFFFFL)];
                    // delta_poc_msb_cycle_lt = new /*UInt32*/long[(int) (num_long_term_sum & 0xFFFFFFFFL)];

                    for (int i = 0; i < ((numLongTermSps + numLongTermPics) & 0xFFFFFFFFL); i++) {
                        if (i < (numLongTermSps & 0xFFFFFFFFL)) {
                            if ((sps.numLongTermRefPicsSps & 0xFFFFFFFFL) > 1) {
                                ltIdxSps[i] = (int) stream.readUev();
                            }
                        } else {
                            pocLsbLt[i] = stream.read((sps.log2MaxPicOrderCntLsbMinus4 & 0xFF) + 4);
                            usedByCurrPicLtFlag[i] = stream.readFlag();
                        }

                        deltaPocMsbPresentFlag[i] = stream.readFlag();

                        if (deltaPocMsbPresentFlag[i]) {
                            /*delta_poc_msb_cycle_lt[i] =*/ stream.readUev();
                        }
                    }
                }
                if (sps.spsTemporalMvpEnabledFlag) {
                    sliceTemporalMvpEnabledFlag = stream.readFlag();
                }
            }

            if (sps.sampleAdaptiveOffsetEnabledFlag) {
                sliceSaoLumaFlag = stream.readFlag();
                if ((sps.getChromaArrayType() & 0xFFFFFFFFL) != 0) {
                    sliceSaoChromaFlag = stream.readFlag();
                }
            }

            if (sliceType == SliceType.P || sliceType == SliceType.B) {
                numRefIdxActiveOverrideFlag = stream.readFlag();
                if (numRefIdxActiveOverrideFlag) {
                    numRefIdxL0ActiveMinus1 = stream.readUev();
                    if (sliceType == SliceType.B) {
                        numRefIdxL1ActiveMinus1 = stream.readUev();
                    }
                }

                if (pps.listsModificationPresentFlag && (getNumPicTotalCurr() & 0xFFFFFFFFL) > 1) {
                    /*ref_pic_lists_modification =*/ new RefPicListsModification(
                            stream, sliceType, numRefIdxL0ActiveMinus1, numRefIdxL1ActiveMinus1, pps);
                }

                if (sliceType == SliceType.B) {
                    mvdL1ZeroFlag = stream.readFlag();
                }
                if (pps.cabacInitPresentFlag) {
                    cabacInitFlag = stream.readFlag();
                }

                if (sliceTemporalMvpEnabledFlag) {
                    if (sliceType == SliceType.B) {
                        collocatedFromL0Flag = stream.readFlag();
                    }
                    if ((collocatedFromL0Flag && (numRefIdxL1ActiveMinus1 & 0xFFFFFFFFL) > 0)
                            || (!collocatedFromL0Flag && (numRefIdxL1ActiveMinus1 & 0xFFFFFFFFL) > 0)) {
                        collocatedRefIdx = stream.readUev();
                    }
                }

                if ((pps.weightedPredFlag && sliceType == SliceType.P)
                        || (pps.weightedBipredFlag && sliceType == SliceType.B)) {
                    /*pred_weight_table =*/ new PredWeightTable(stream, this);
                }

                fiveMinusMaxNumMergeCand = (byte) stream.readUev();
                if ((sps.spsSccExt != null ? sps.spsSccExt.motionVectorResolutionControlIdc : 0) == 2) {
                    useIntegerMvFlag = stream.readFlag();
                }
            }

            sliceQpDelta = stream.readSev();
            if (pps.ppsSliceChromaQpOffsetsPresentFlag) {
                sliceCbQpOffset = stream.readSev();
                sliceCrQpOffset = stream.readSev();
            }
            boolean tmp0 = (pps.ppsSccExt != null && pps.ppsSccExt.ppsSliceActQpOffsetsPresentFlag);

            if (tmp0) {
                sliceActYQpOffset = stream.readSev();
                sliceActCbQpOffset = stream.readSev();
                sliceActCrQpOffset = stream.readSev();
            }
            boolean tmp1 = (pps.ppsRangeExt != null && pps.ppsRangeExt.chromaQpOffsetListEnabledFlag);

            if (tmp1) {
                cuChromaQpOffsetEnabledFlag = stream.readFlag();
            }

            sliceDeblockingFilterDisabledFlag = pps.ppsDeblockingFilterDisabledFlag;
            sliceBetaOffsetDiv2 = pps.ppsBetaOffsetDiv2;
            sliceTcOffsetDiv2 = pps.ppsTcOffsetDiv2;
            sliceLoopFilterAcrossSlicesEnabledFlag = pps.ppsLoopFilterAcrossSlicesEnabledFlag;

            if (pps.deblockingFilterOverrideEnabledFlag) {
                deblockingFilterOverrideFlag = stream.readFlag();
            }

            if (deblockingFilterOverrideFlag) {
                sliceDeblockingFilterDisabledFlag = stream.readFlag();
                if (!sliceDeblockingFilterDisabledFlag) {
                    sliceBetaOffsetDiv2 = stream.readSev();
                    sliceTcOffsetDiv2 = stream.readSev();
                }
            }

            if (pps.ppsLoopFilterAcrossSlicesEnabledFlag
                    && (sliceSaoLumaFlag || sliceSaoChromaFlag || !sliceDeblockingFilterDisabledFlag)) {
                sliceLoopFilterAcrossSlicesEnabledFlag = stream.readFlag();
            }
        }

        if (pps.tilesEnabledFlag || pps.entropyCodingSyncEnabledFlag) {
            numEntryPointOffsets = stream.readUev();
            if ((numEntryPointOffsets & 0xFFFFFFFFL) > 0) {
                offsetLenMinus1 = (byte) stream.readUev() & 0xFF;
                // entry_point_offset_minus1 = new int[(int) (num_entry_point_offsets & 0xFFFFFFFFL)];
                for (int i = 0; i < (numEntryPointOffsets & 0xFFFFFFFFL); i++)
                    /*entry_point_offset_minus1[i] =*/ stream.read(offsetLenMinus1 + 1);
                // entry_point_offset_minus1[i+1] += entry_point_offset_minus1[i]
            }
        }
        if (pps.sliceSegmentHeaderExtensionPresentFlag) {
            sliceSegmentHeaderExtensionLength = (int) stream.readUev();
            //            slice_segment_header_extension_data_byte =
            //                    new byte[slice_segment_header_extension_length];
            for (int i = 0; i < sliceSegmentHeaderExtensionLength; i++)
                /*slice_segment_header_extension_data_byte[i] = (byte)*/ stream.read(8);
        }

        stream.read(1); /* equal to 1; alignment_bit_equal_to_one */
        while (stream.notByteAligned()) stream.skipBits(1); /* equal to 0; alignment_bit_equal_to_zero */
    }

    public final int getMaxNumMergeCand() {
        return 5 - (fiveMinusMaxNumMergeCand & 0xFF);
    }

    public final int getInitType() {
        switch (sliceType) {
            case P:
                return (cabacInitFlag ? 1 : 0) + 1;
            case B:
                return 2 - (cabacInitFlag ? 1 : 0);
            case I:
            default:
                return 0;
        }
    }

    public final int getSliceQPY() {
        return 26 + pps.initQpMinus26 + sliceQpDelta;
    }

    final /*UInt32*/ long getCtbAddrInRs() {
        return ctbAddrInRs;
    }

    final void setCtbAddrInRs(/*UInt32*/ long value) {
        ctbAddrInRs = value;
    }

    final /*UInt32*/ long getCtbAddrInTs() {
        return ctbAddrInTs;
    }

    final void setCtbAddrInTs(/*UInt32*/ long value) {
        ctbAddrInTs = value;
    }

    final /*UInt32*/ long getSliceAddrRs() {
        return !dependentSliceSegmentFlag
                ? sliceSegmentAddress
                : // if not dependent slice
                pps.ctbAddrTsToRs[(int) ((pps.ctbAddrRsToTs[sliceSegmentAddress] - 1) & 0xFFFFFFFFL)];
    } // else take preceding slice segment

    final /*UInt32*/ long getCurrRpsIdx() {
        return shortTermRefPicSetSpsFlag ? (shortTermRefPicSetIdx & 0xFFFFFFFFL) : pps.sps.numShortTermRefPicSets;
    }

    public final /*UInt32*/ long getNumPicTotalCurr() {
        /*UInt32*/
        long numPicTotalCurr = 0;

        for (int i = 0; i < (pps.sps.numNegativePics((int) getCurrRpsIdx()) & 0xFFFFFFFFL); i++)
            if (pps.sps.usedByCurrPicS0((int) getCurrRpsIdx())[i]) {
                numPicTotalCurr++;
            }

        for (int i = 0; i < (pps.sps.numPositivePics((int) getCurrRpsIdx()) & 0xFFFFFFFFL); i++)
            if (pps.sps.usedByCurrPicS1((int) getCurrRpsIdx())[i]) {
                numPicTotalCurr++;
            }

        for (int i = 0; i < (((numLongTermSps & 0xFFFFFFFFL) + (numLongTermPics & 0xFFFFFFFFL)) & 0xFFFFFFFFL); i++)
            if (usedByCurrPicLt(i)) {
                numPicTotalCurr++;
            }

        if (pps.ppsSccExt.ppsCurrPicRefEnabledFlag) {
            numPicTotalCurr++;
        }

        return numPicTotalCurr;
    }

    public final int pocLsbLt(int i) {
        return (i < (numLongTermSps & 0xFFFFFFFFL)) ? pps.sps.ltRefPicPocLsbSps[ltIdxSps[i]] : pocLsbLt[i];
    }

    public final boolean usedByCurrPicLt(int i) {
        return (i < (numLongTermSps & 0xFFFFFFFFL))
                ? pps.sps.usedByCurrPicLtSpsFlag[ltIdxSps[i]]
                : usedByCurrPicLtFlag[i];
    }
}
