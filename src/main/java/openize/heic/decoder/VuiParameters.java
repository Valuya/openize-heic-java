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
 * The colour description of vui_parameters( ) (E.2.1); the rest is read past.
 */
class VuiParameters {
    // aspect_ratio_idc of a sample aspect ratio coded as sar_width and sar_height (Table E.1)
    private static final int EXTENDED_SAR = 255;
    // colour_primaries and matrix_coeffs when no colour description is present: unspecified (E.3.1)
    private static final int UNSPECIFIED = 2;

    final boolean videoFullRangeFlag;
    final int colourPrimaries;
    final int matrixCoeffs;

    /**
     * The values inferred for a sequence parameter set without VUI.
     */
    VuiParameters() {
        videoFullRangeFlag = false;
        colourPrimaries = UNSPECIFIED;
        matrixCoeffs = UNSPECIFIED;
    }

    VuiParameters(BitStreamWithNalSupport stream, int spsMaxSubLayersMinus1) {
        boolean aspectRatioInfoPresentFlag = stream.readFlag();
        if (aspectRatioInfoPresentFlag) {
            int aspectRatioIdc = stream.read(8);
            if (aspectRatioIdc == EXTENDED_SAR) {
                stream.skipBits(16 + 16); // sar_width, sar_height
            }
        }

        boolean overscanInfoPresentFlag = stream.readFlag();
        if (overscanInfoPresentFlag) {
            stream.skipBits(1); // overscan_appropriate_flag
        }

        boolean fullRange = false;
        int primaries = UNSPECIFIED;
        int coefficients = UNSPECIFIED;
        boolean videoSignalTypePresentFlag = stream.readFlag();
        if (videoSignalTypePresentFlag) {
            stream.skipBits(3); // video_format
            fullRange = stream.readFlag();
            boolean colourDescriptionPresentFlag = stream.readFlag();
            if (colourDescriptionPresentFlag) {
                primaries = stream.read(8);
                stream.skipBits(8); // transfer_characteristics
                coefficients = stream.read(8);
            }
        }
        videoFullRangeFlag = fullRange;
        colourPrimaries = primaries;
        matrixCoeffs = coefficients;

        boolean chromaLocInfoPresentFlag = stream.readFlag();
        if (chromaLocInfoPresentFlag) {
            stream.readUev(); // chroma_sample_loc_type_top_field
            stream.readUev(); // chroma_sample_loc_type_bottom_field
        }

        // neutral_chroma_indication_flag, field_seq_flag, frame_field_info_present_flag
        stream.skipBits(3);
        boolean defaultDisplayWindowFlag = stream.readFlag();
        if (defaultDisplayWindowFlag) {
            // def_disp_win_left_offset, def_disp_win_right_offset, def_disp_win_top_offset, def_disp_win_bottom_offset
            for (int i = 0; i < 4; i++) {
                stream.readUev();
            }
        }

        boolean vuiTimingInfoPresentFlag = stream.readFlag();
        if (vuiTimingInfoPresentFlag) {
            stream.skipBits(32 + 32); // vui_num_units_in_tick, vui_time_scale
            boolean vuiPocProportionalToTimingFlag = stream.readFlag();
            if (vuiPocProportionalToTimingFlag) {
                stream.readUev(); // vui_num_ticks_poc_diff_one_minus1
            }
            boolean vuiHrdParametersPresentFlag = stream.readFlag();
            if (vuiHrdParametersPresentFlag) {
                HrdParameters.read(stream, spsMaxSubLayersMinus1);
            }
        }

        boolean bitstreamRestrictionFlag = stream.readFlag();
        if (bitstreamRestrictionFlag) {
            // tiles_fixed_structure_flag, motion_vectors_over_pic_boundaries_flag, restricted_ref_pic_lists_flag
            stream.skipBits(3);
            // min_spatial_segmentation_idc, max_bytes_per_pic_denom, max_bits_per_min_cu_denom,
            // log2_max_mv_length_horizontal, log2_max_mv_length_vertical
            for (int i = 0; i < 5; i++) {
                stream.readUev();
            }
        }
    }
}
