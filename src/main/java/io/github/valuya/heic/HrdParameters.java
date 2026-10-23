/*
 * Openize.HEIC
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of Openize.HEIC.
 *
 * Openize.HEIC is available under Openize license, which is
 * available along with Openize.HEIC sources.
 */

package io.github.valuya.heic;

/**
 * hrd_parameters( ) (E.2.2), read to get past it: the hypothetical reference decoder does not matter to decoding a
 * picture. Keeps the parameters common to all sub-layers, which the next set of a VPS can share.
 */
record HrdParameters(
        boolean nalHrdParametersPresentFlag, boolean vclHrdParametersPresentFlag, boolean subPicHrdParamsPresentFlag) {
    // CpbCnt is at most 32 (E.3.2)
    private static final int MAX_CPB_CNT_MINUS1 = 31;

    /**
     * Reads hrd_parameters( 1, maxNumSubLayersMinus1 ).
     */
    static HrdParameters read(BitStreamWithNalSupport stream, int maxNumSubLayersMinus1) {
        boolean nalHrdParametersPresentFlag = stream.readFlag();
        boolean vclHrdParametersPresentFlag = stream.readFlag();
        boolean subPicHrdParamsPresentFlag = false;
        if (nalHrdParametersPresentFlag || vclHrdParametersPresentFlag) {
            subPicHrdParamsPresentFlag = stream.readFlag();
            if (subPicHrdParamsPresentFlag) {
                // tick_divisor_minus2, du_cpb_removal_delay_increment_length_minus1,
                // sub_pic_cpb_params_in_pic_timing_sei_flag, dpb_output_delay_du_length_minus1
                stream.skipBits(8 + 5 + 1 + 5);
            }
            stream.skipBits(4 + 4); // bit_rate_scale, cpb_size_scale
            if (subPicHrdParamsPresentFlag) {
                stream.skipBits(4); // cpb_size_du_scale
            }
            // initial_cpb_removal_delay_length_minus1, au_cpb_removal_delay_length_minus1,
            // dpb_output_delay_length_minus1
            stream.skipBits(5 + 5 + 5);
        }
        HrdParameters hrdParameters =
                new HrdParameters(nalHrdParametersPresentFlag, vclHrdParametersPresentFlag, subPicHrdParamsPresentFlag);
        hrdParameters.skipSubLayers(stream, maxNumSubLayersMinus1);
        return hrdParameters;
    }

    /**
     * Reads the next hrd_parameters( 0, maxNumSubLayersMinus1 ) of a VPS, whose common parameters are this one's
     * (cprms_present_flag, 7.4.3.1).
     */
    HrdParameters readNextWithSameCommonParameters(BitStreamWithNalSupport stream, int maxNumSubLayersMinus1) {
        skipSubLayers(stream, maxNumSubLayersMinus1);
        return this;
    }

    private void skipSubLayers(BitStreamWithNalSupport stream, int maxNumSubLayersMinus1) {
        for (int i = 0; i <= maxNumSubLayersMinus1; i++) {
            boolean fixedPicRateGeneralFlag = stream.readFlag();
            // inferred to be 1 when fixed_pic_rate_general_flag is (E.3.2)
            boolean fixedPicRateWithinCvsFlag = fixedPicRateGeneralFlag || stream.readFlag();
            boolean lowDelayHrdFlag = false;
            if (fixedPicRateWithinCvsFlag) {
                stream.readUev(); // elemental_duration_in_tc_minus1
            } else {
                lowDelayHrdFlag = stream.readFlag();
            }
            int cpbCnt = lowDelayHrdFlag ? 1 : stream.readUevAtMost(MAX_CPB_CNT_MINUS1) + 1;
            if (nalHrdParametersPresentFlag) {
                skipSubLayerHrdParameters(stream, cpbCnt);
            }
            if (vclHrdParametersPresentFlag) {
                skipSubLayerHrdParameters(stream, cpbCnt);
            }
        }
    }

    // sub_layer_hrd_parameters( ) (E.2.3)
    private void skipSubLayerHrdParameters(BitStreamWithNalSupport stream, int cpbCnt) {
        for (int i = 0; i < cpbCnt; i++) {
            stream.readUev(); // bit_rate_value_minus1
            stream.readUev(); // cpb_size_value_minus1
            if (subPicHrdParamsPresentFlag) {
                stream.readUev(); // cpb_size_du_value_minus1
                stream.readUev(); // bit_rate_du_value_minus1
            }
            stream.skipBits(1); // cbr_flag
        }
    }
}
