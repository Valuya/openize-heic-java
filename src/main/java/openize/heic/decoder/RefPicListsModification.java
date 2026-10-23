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

class RefPicListsModification {
    final boolean refPicListModificationFlagL0;
    boolean refPicListModificationFlagL1;
    // final int[] list_entry_l0;
    int[] listEntryL1;

    RefPicListsModification(
            BitStreamWithNalSupport stream,
            SliceType sliceType,
            /*UInt32*/ long numRefIdxL0ActiveMinus1,
            /*UInt32*/ long numRefIdxL1ActiveMinus1,
            PicParameterSetRbsp pps) {
        refPicListModificationFlagL0 = stream.readFlag(); // u(1)
        // list_entry_l0 = new int[(int) (((num_ref_idx_l0_active_minus1 & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL)];

        int numPicTotalCurr = getNumPicTotalCurr(pps);

        if (refPicListModificationFlagL0) {
            for (int i = 0; i <= (numRefIdxL1ActiveMinus1 & 0xFFFFFFFFL); i++)
                /*list_entry_l0[i] =*/ stream.read(MathUtils.f64S32( // u(v)
                        MathUtils.ceiling(MathUtils.log(numPicTotalCurr, 2))));
        }

        if (sliceType == SliceType.B) {
            refPicListModificationFlagL1 = stream.readFlag(); // u(1)
            listEntryL1 = new int[(int) (((numRefIdxL0ActiveMinus1 & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL)];

            if (refPicListModificationFlagL1) {
                for (int i = 0; i <= (numRefIdxL1ActiveMinus1 & 0xFFFFFFFFL); i++)
                    listEntryL1[i] = stream.read(MathUtils.f64S32( // u(v)
                            MathUtils.ceiling(MathUtils.log(numPicTotalCurr, 2))));
            }
        }
    }

    private int getNumPicTotalCurr(PicParameterSetRbsp pps) {
        throw new UnsupportedOperationException();
        /*
        int NumPicTotalCurr = 0;
        for (int i = 0; i < NumNegativePics[CurrRpsIdx]; i++)
            if (UsedByCurrPicS0[CurrRpsIdx][i])
                NumPicTotalCurr++;

        for (int i = 0; i < NumPositivePics[CurrRpsIdx]; i++)
            if (UsedByCurrPicS1[CurrRpsIdx][i])
                NumPicTotalCurr++;

        for (int i = 0; i < header.num_long_term_sps + header.num_long_term_pics; i++)
            if (UsedByCurrPicLt[i])
                NumPicTotalCurr++;

        if (pps.pps_scc_ext.pps_curr_pic_ref_enabled_flag)
            NumPicTotalCurr++;

        return NumPicTotalCurr;*/
    }
}
