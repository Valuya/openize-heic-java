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

import java.util.Optional;

/**
 * A slice segment header (7.3.6.1). A dependent slice segment takes the slice-level elements of the slice segment
 * before it (7.4.7.1); only intra (I) slices are decoded.
 */
final class SliceSegmentHeader {
    // 7.4.3.3 and 7.4.7.1: ranges of the syntax elements
    private static final int MAX_PPS_ID = 63;
    private static final int MAX_CHROMA_QP_OFFSET = 12;
    private static final int MAX_DEBLOCKING_OFFSET_DIV2 = 6;
    private static final int MAX_LONG_TERM_PICTURES = 32;
    private static final int MAX_SLICE_SEGMENT_HEADER_EXTENSION_LENGTH = 256;
    private static final int MAX_OFFSET_LENGTH_MINUS1 = 31;

    final PicParameterSetRbsp pps;
    final SeqParameterSetRbsp sps;
    final boolean firstSliceSegmentInPicFlag;
    final boolean dependentSliceSegmentFlag;
    final int sliceSegmentAddress;
    final SliceHeader slice;

    /**
     * Reads the header of a slice segment of a picture whose earlier slice segments, if any, are those of
     * {@code currentPicture}.
     */
    SliceSegmentHeader(
            BitStreamWithNalSupport stream, NalHeader nalHeader, Optional<HeicPicture> currentPictureOptional) {
        firstSliceSegmentInPicFlag = stream.readFlag();
        if (nalHeader.isIrapPicture()) {
            stream.skipBits(1); // no_output_of_prior_pics_flag
        }
        pps = findPictureParameterSet(stream, stream.readUevAtMost(MAX_PPS_ID));
        sps = pps.sps;
        // the picture and the scan tables are allocated at the coded size, before any crop: bound it first
        stream.getContext()
                .getDecodingOptions()
                .checkPixelCount("A coded picture", sps.picWidthInLumaSamples, sps.picHeightInLumaSamples);
        if (sps.separateColourPlaneFlag) {
            throw new HeicFormatException("Pictures coded as separate colour planes are not supported");
        }

        if (firstSliceSegmentInPicFlag) {
            dependentSliceSegmentFlag = false;
            sliceSegmentAddress = 0;
        } else {
            dependentSliceSegmentFlag = pps.dependentSliceSegmentsEnabledFlag && stream.readFlag();
            sliceSegmentAddress = stream.read(ceilLog2(sps.getPicSizeInCtbsY()));
            if (sliceSegmentAddress >= sps.getPicSizeInCtbsY()) {
                throw new HeicFormatException("Slice segment address %d past the %d coding tree blocks of the picture"
                        .formatted(sliceSegmentAddress, sps.getPicSizeInCtbsY()));
            }
        }

        if (dependentSliceSegmentFlag) {
            slice = currentPictureOptional
                    .flatMap(HeicPicture::getLastSliceHeaderOptional)
                    .map(precedingSegment -> precedingSegment.slice)
                    .orElseThrow(() -> new HeicFormatException("A dependent slice segment starts a picture"));
        } else {
            slice = readSliceHeader(stream, nalHeader);
        }

        skipEntryPoints(stream);
        if (pps.sliceSegmentHeaderExtensionPresentFlag) {
            int extensionLength = stream.readUevAtMost(MAX_SLICE_SEGMENT_HEADER_EXTENSION_LENGTH);
            stream.skipBits(extensionLength * 8);
        }

        // byte_alignment(): alignment_bit_equal_to_one, then zero bits
        stream.skipBits(1);
        while (stream.notByteAligned()) {
            stream.skipBits(1);
        }
    }

    private static PicParameterSetRbsp findPictureParameterSet(BitStreamWithNalSupport stream, int ppsId) {
        PicParameterSetRbsp pps = stream.getContext().getPPS().get(ppsId);
        if (pps == null) {
            throw new HeicFormatException(
                    "A slice refers to picture parameter set %d, which is not defined".formatted(ppsId));
        }
        return pps;
    }

    private SliceHeader readSliceHeader(BitStreamWithNalSupport stream, NalHeader nalHeader) {
        stream.skipBits(pps.numExtraSliceHeaderBits); // slice_reserved_flag[i]
        SliceType sliceType = SliceType.get(stream.readUevAtMost(2));
        if (sliceType != SliceType.I) {
            throw new HeicFormatException(
                    "Inter-coded %s slices are not supported: only intra-coded pictures decode".formatted(sliceType));
        }
        if (pps.outputFlagPresentFlag) {
            stream.skipBits(1); // pic_output_flag
        }
        if (!nalHeader.isIdrPicture()) {
            skipReferencePictureSets(stream);
        }

        boolean sliceSaoLumaFlag = false;
        boolean sliceSaoChromaFlag = false;
        if (sps.sampleAdaptiveOffsetEnabledFlag) {
            sliceSaoLumaFlag = stream.readFlag();
            sliceSaoChromaFlag = sps.getChromaArrayType() != 0 && stream.readFlag();
        }

        int sliceQpY = 26 + pps.initQpMinus26 + stream.readSev();
        if (sliceQpY < -sps.getQpBdOffsetY() || sliceQpY > 51) {
            throw new HeicFormatException("Slice QP %d outside %d..51".formatted(sliceQpY, -sps.getQpBdOffsetY()));
        }

        int sliceCbQpOffset = 0;
        int sliceCrQpOffset = 0;
        if (pps.ppsSliceChromaQpOffsetsPresentFlag) {
            sliceCbQpOffset = readChromaQpOffset(stream, "slice_cb_qp_offset");
            sliceCrQpOffset = readChromaQpOffset(stream, "slice_cr_qp_offset");
        }

        int sliceActYQpOffset = 0;
        int sliceActCbQpOffset = 0;
        int sliceActCrQpOffset = 0;
        if (pps.ppsSccExt.ppsSliceActQpOffsetsPresentFlag) {
            sliceActYQpOffset = readChromaQpOffset(stream, "slice_act_y_qp_offset");
            sliceActCbQpOffset = readChromaQpOffset(stream, "slice_act_cb_qp_offset");
            sliceActCrQpOffset = readChromaQpOffset(stream, "slice_act_cr_qp_offset");
        }

        boolean cuChromaQpOffsetEnabledFlag = pps.ppsRangeExt.chromaQpOffsetListEnabledFlag && stream.readFlag();

        boolean deblockingFilterOverrideFlag = pps.deblockingFilterOverrideEnabledFlag && stream.readFlag();
        boolean sliceDeblockingFilterDisabledFlag = pps.ppsDeblockingFilterDisabledFlag;
        int sliceBetaOffsetDiv2 = pps.ppsBetaOffsetDiv2;
        int sliceTcOffsetDiv2 = pps.ppsTcOffsetDiv2;
        if (deblockingFilterOverrideFlag) {
            sliceDeblockingFilterDisabledFlag = stream.readFlag();
            if (!sliceDeblockingFilterDisabledFlag) {
                sliceBetaOffsetDiv2 = readDeblockingOffset(stream, "slice_beta_offset_div2");
                sliceTcOffsetDiv2 = readDeblockingOffset(stream, "slice_tc_offset_div2");
            }
        }

        boolean sliceLoopFilterAcrossSlicesEnabledFlag = pps.ppsLoopFilterAcrossSlicesEnabledFlag;
        if (pps.ppsLoopFilterAcrossSlicesEnabledFlag
                && (sliceSaoLumaFlag || sliceSaoChromaFlag || !sliceDeblockingFilterDisabledFlag)) {
            sliceLoopFilterAcrossSlicesEnabledFlag = stream.readFlag();
        }

        return new SliceHeader(
                sliceSegmentAddress,
                sliceSaoLumaFlag,
                sliceSaoChromaFlag,
                sliceQpY,
                sliceCbQpOffset,
                sliceCrQpOffset,
                sliceActYQpOffset,
                sliceActCbQpOffset,
                sliceActCrQpOffset,
                cuChromaQpOffsetEnabledFlag,
                sliceDeblockingFilterDisabledFlag,
                sliceBetaOffsetDiv2,
                sliceTcOffsetDiv2,
                sliceLoopFilterAcrossSlicesEnabledFlag);
    }

    // slice_pic_order_cnt_lsb and the reference picture sets: a still image refers to no other picture
    private void skipReferencePictureSets(BitStreamWithNalSupport stream) {
        int pocLsbBitCount = sps.log2MaxPicOrderCntLsbMinus4 + 4;
        stream.skipBits(pocLsbBitCount); // slice_pic_order_cnt_lsb

        boolean shortTermRefPicSetSpsFlag = stream.readFlag();
        if (!shortTermRefPicSetSpsFlag) {
            new StRefPicSet(stream, sps, sps.numShortTermRefPicSets);
        } else if (sps.numShortTermRefPicSets > 1) {
            stream.skipBits(ceilLog2(sps.numShortTermRefPicSets)); // short_term_ref_pic_set_idx
        }

        if (sps.longTermRefPicsPresentFlag) {
            int numLongTermSps = sps.numLongTermRefPicsSps > 0 ? stream.readUevAtMost(sps.numLongTermRefPicsSps) : 0;
            int numLongTermPics = stream.readUevAtMost(MAX_LONG_TERM_PICTURES - numLongTermSps);
            for (int i = 0; i < numLongTermSps + numLongTermPics; i++) {
                if (i < numLongTermSps) {
                    if (sps.numLongTermRefPicsSps > 1) {
                        stream.skipBits(ceilLog2(sps.numLongTermRefPicsSps)); // lt_idx_sps[i]
                    }
                } else {
                    stream.skipBits(pocLsbBitCount + 1); // poc_lsb_lt[i], used_by_curr_pic_lt_flag[i]
                }
                boolean deltaPocMsbPresentFlag = stream.readFlag();
                if (deltaPocMsbPresentFlag) {
                    stream.readUev(); // delta_poc_msb_cycle_lt[i]
                }
            }
        }

        if (sps.spsTemporalMvpEnabledFlag) {
            stream.skipBits(1); // slice_temporal_mvp_enabled_flag
        }
    }

    // The coding tree blocks are decoded in order, so the entry points into the slice data are not needed
    private void skipEntryPoints(BitStreamWithNalSupport stream) {
        if (!pps.tilesEnabledFlag && !pps.entropyCodingSyncEnabledFlag) {
            return;
        }
        int numEntryPointOffsets = stream.readUevAtMost(sps.getPicSizeInCtbsY() - 1);
        if (numEntryPointOffsets > 0) {
            int offsetBitCount = stream.readUevAtMost(MAX_OFFSET_LENGTH_MINUS1) + 1;
            for (int i = 0; i < numEntryPointOffsets; i++) {
                stream.skipBits(offsetBitCount); // entry_point_offset_minus1[i]
            }
        }
    }

    private static int readChromaQpOffset(BitStreamWithNalSupport stream, String elementName) {
        return stream.readSevInRange(-MAX_CHROMA_QP_OFFSET, MAX_CHROMA_QP_OFFSET, elementName);
    }

    private static int readDeblockingOffset(BitStreamWithNalSupport stream, String elementName) {
        return stream.readSevInRange(-MAX_DEBLOCKING_OFFSET_DIV2, MAX_DEBLOCKING_OFFSET_DIV2, elementName);
    }

    // Ceil(Log2(value)), the bit count of an index below value
    private static int ceilLog2(int value) {
        return 32 - Integer.numberOfLeadingZeros(value - 1);
    }
}
