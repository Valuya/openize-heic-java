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

class SubLayerHrdParameters {
    //    private final /*UInt32*/long[] bit_rate_value_minus1;
    //    private final /*UInt32*/long[] cpb_size_value_minus1;
    //    private final /*UInt32*/long[] cpb_size_du_value_minus1;
    //    private final /*UInt32*/long[] bit_rate_du_value_minus1;
    //    private final boolean[] cbr_flag;

    public SubLayerHrdParameters(
            BitStreamWithNalSupport stream, /*UInt32*/ long cpbCnt, boolean subPicHrdParamsPresentFlag) {
        //        bit_rate_value_minus1 = new /*UInt32*/long[(int)(CpbCnt & 0xFFFFFFFFL)];
        //        cpb_size_value_minus1 = new /*UInt32*/long[(int)(CpbCnt & 0xFFFFFFFFL)];
        //        cpb_size_du_value_minus1 = new /*UInt32*/long[(int)(CpbCnt & 0xFFFFFFFFL)];
        //        bit_rate_du_value_minus1 = new /*UInt32*/long[(int)(CpbCnt & 0xFFFFFFFFL)];
        //        cbr_flag = new boolean[(int)(CpbCnt & 0xFFFFFFFFL)];

        for (int i = 0; i < (cpbCnt & 0xFFFFFFFFL); i++) {
            /*bit_rate_value_minus1[i] =*/ stream.readUev(); // ue(v)
            /*cpb_size_value_minus1[i] =*/ stream.readUev(); // ue(v)

            if (subPicHrdParamsPresentFlag) {
                /*cpb_size_du_value_minus1[i] =*/ stream.readUev(); // ue(v)
                /*bit_rate_du_value_minus1[i] =*/ stream.readUev(); // ue(v)
            }

            /*cbr_flag[i] =*/ stream.readFlag(); // u(1)
        }
    }
}
