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

class PredictionUnit {

    public PredictionUnit(
            BitStreamWithNalSupport stream, SliceSegmentHeader header, int x0, int y0, int nPbW, int nPbH) {
        throw new UnsupportedOperationException("Prediction units are not met in static image cases.");
        /* (unreachable):

        seq_parameter_set_rbsp sps = header.pps.sps;
        HeicPicture picture = header.parentPicture;*/
        /*
                    if (picture.cu_skip_flag[x0, y0])
                    {
                        if (header.MaxNumMergeCand > 1)
                            picture.merge_idx[x0, y0] = stream.read_aev();
        }
                    else
                    {
                        // MODE_INTER
                        picture.merge_flag[x0, y0] = stream.read_aev() == 1;
                        if (picture.merge_flag[x0, y0])
                        {
                            if (header.MaxNumMergeCand > 1)
                                picture.merge_idx[x0, y0] = stream.read_aev();
                        }
                        else
                        {
                            if (header.slice_type == SliceType.B)
                                picture.inter_pred_idc[x0, y0] = (inter_pred_idc)stream.read_aev();

                            if (picture.inter_pred_idc[x0, y0] != inter_pred_idc.PRED_L1)
                            {
                                if (header.num_ref_idx_l0_active_minus1 > 0)
                                    picture.ref_idx_l0[x0, y0] = stream.read_aev();

                                new mvd_coding(stream, header, x0, y0, false);

                                picture.mvp_l0_flag[x0, y0] = stream.read_aev() == 1;
                            }

                            if (picture.inter_pred_idc[x0, y0] != inter_pred_idc.PRED_L0)
                            {
                                if (header.num_ref_idx_l1_active_minus1 > 0)
                                    picture.ref_idx_l1[x0, y0] = stream.read_aev();

                                if (picture.mvd_l1_zero_flag && picture.inter_pred_idc[x0, y0] == inter_pred_idc.PRED_BI)
                                {
                                    picture.MvdL1[x0, y0][0] = 0;
                                    picture.MvdL1[x0, y0][1] = 0;
                                }
                                else
                                {
                                    new mvd_coding(stream, header, x0, y0, true);
                                }

                                picture.mvp_l1_flag[x0, y0] = stream.read_aev_flag();
                            }
                        }
                    }
                    */
    }

    private static class MvdCoding {
        final boolean[] absMvdGreater0Flag;
        final boolean[] absMvdGreater1Flag;
        final int[] absMvd; // = 1
        final boolean[] mvdSignFlag;

        public MvdCoding(BitStreamWithNalSupport stream, SliceSegmentHeader header, int x0, int y0, boolean refList) {
            HeicPicture picture = header.parentPicture;

            absMvdGreater0Flag = new boolean[2];
            absMvdGreater1Flag = new boolean[2];
            absMvd = new int[2];
            mvdSignFlag = new boolean[2];

            absMvdGreater0Flag[0] = stream.readAevFlag(); // ae(v)
            absMvdGreater0Flag[1] = stream.readAevFlag(); // ae(v)

            if (absMvdGreater0Flag[0]) {
                absMvdGreater1Flag[0] = stream.readAevFlag(); // ae(v)
            }

            if (absMvdGreater0Flag[1]) {
                absMvdGreater1Flag[1] = stream.readAevFlag(); // ae(v)
            }

            if (absMvdGreater0Flag[0]) {
                if (absMvdGreater1Flag[0]) {
                    absMvd[0] = stream.readAev() + 2; // ae(v)
                }

                mvdSignFlag[0] = stream.readAevFlag(); // ae(v)
            }

            if (absMvdGreater0Flag[1]) {
                if (absMvdGreater1Flag[1]) {
                    absMvd[1] = stream.readAev() + 2; // ae(v)
                }

                mvdSignFlag[1] = stream.readAevFlag(); // ae(v)
            }

            int[] lMvd = new int[2];
            lMvd[0] = (absMvdGreater0Flag[0] ? 1 : 0) * (absMvd[0]) * (1 - 2 * (mvdSignFlag[0] ? 1 : 0));
            lMvd[1] = (absMvdGreater0Flag[1] ? 1 : 0) * (absMvd[1]) * (1 - 2 * (mvdSignFlag[1] ? 1 : 0));

            /*if (refList)
            {
                //picture.MvdL1[x0, y0][0] = lMvd[0];
                //picture.MvdL1[x0, y0][1] = lMvd[1];
            }
            else
            {
                //picture.MvdL0[x0, y0][0] = lMvd[0];
                //picture.MvdL0[x0, y0][1] = lMvd[1];
            }*/
        }
    }
}
