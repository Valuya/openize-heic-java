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

class ProfileTierLevel {
    byte generalProfileIdc;
    final int generalLevelIdc;

    ProfileTierLevel(BitStreamWithNalSupport stream, boolean profilePresentFlag, int maxNumSubLayersMinus1) {
        //        byte general_profile_space;
        //        boolean general_tier_flag;
        boolean[] generalProfileCompatibilityFlag;
        //        boolean general_progressive_source_flag;
        //        boolean general_interlaced_source_flag;
        //        boolean general_non_packed_constraint_flag;
        //        boolean general_frame_only_constraint_flag;
        //        boolean general_max_12bit_constraint_flag;
        //        boolean general_max_10bit_constraint_flag;
        //        boolean general_max_8bit_constraint_flag;
        //        boolean general_max_422chroma_constraint_flag;
        //        boolean general_max_420chroma_constraint_flag;
        //        boolean general_max_monochrome_constraint_flag;
        //        boolean general_intra_constraint_flag;
        //        boolean general_one_picture_only_constraint_flag;
        //        boolean general_lower_bit_rate_constraint_flag;
        //        boolean general_max_14bit_constraint_flag;
        //        boolean general_inbld_flag;
        final boolean[] subLayerProfilePresentFlag;
        final boolean[] subLayerLevelPresentFlag;
        //        final byte[] sub_layer_profile_space;
        //        final boolean[] sub_layer_tier_flag;
        final byte[] subLayerProfileIdc;
        final boolean[][] subLayerProfileCompatibilityFlag;
        //        final boolean[] sub_layer_progressive_source_flag;
        //        final boolean[] sub_layer_interlaced_source_flag;
        //        final boolean[] sub_layer_non_packed_constraint_flag;
        //        final boolean[] sub_layer_frame_only_constraint_flag;
        //        final boolean[] sub_layer_max_12bit_constraint_flag;
        //        final boolean[] sub_layer_max_10bit_constraint_flag;
        //        final boolean[] sub_layer_max_8bit_constraint_flag;
        //        final boolean[] sub_layer_max_422chroma_constraint_flag;
        //        final boolean[] sub_layer_max_420chroma_constraint_flag;
        //        final boolean[] sub_layer_max_monochrome_constraint_flag;
        //        final boolean[] sub_layer_intra_constraint_flag;
        //        final boolean[] sub_layer_one_picture_only_constraint_flag;
        //        final boolean[] sub_layer_lower_bit_rate_constraint_flag;
        //        final boolean[] sub_layer_max_14bit_constraint_flag;
        //        final boolean[] sub_layer_inbld_flag;
        //        final byte[] sub_layer_level_idc;

        if (profilePresentFlag) {
            /*general_profile_space = (byte)*/
            stream.read(2); // u(2)
            /*general_tier_flag =*/ stream.readFlag(); // u(1)
            generalProfileIdc = (byte) stream.read(5); // u(5)
            generalProfileCompatibilityFlag = new boolean[32];
            for (int j = 0; j < 32; j++) generalProfileCompatibilityFlag[j] = stream.readFlag(); // u(1)
            /*general_progressive_source_flag =*/ stream.readFlag(); // u(1)
            /*general_interlaced_source_flag =*/ stream.readFlag(); // u(1)
            /*general_non_packed_constraint_flag =*/ stream.readFlag(); // u(1)
            /*general_frame_only_constraint_flag =*/ stream.readFlag(); // u(1)

            if ((generalProfileIdc & 0xFF) == 4
                    || generalProfileCompatibilityFlag[4]
                    || (generalProfileIdc & 0xFF) == 5
                    || generalProfileCompatibilityFlag[5]
                    || (generalProfileIdc & 0xFF) == 6
                    || generalProfileCompatibilityFlag[6]
                    || (generalProfileIdc & 0xFF) == 7
                    || generalProfileCompatibilityFlag[7]
                    || (generalProfileIdc & 0xFF) == 8
                    || generalProfileCompatibilityFlag[8]
                    || (generalProfileIdc & 0xFF) == 9
                    || generalProfileCompatibilityFlag[9]
                    || (generalProfileIdc & 0xFF) == 10
                    || generalProfileCompatibilityFlag[10]
                    || (generalProfileIdc & 0xFF) == 11
                    || generalProfileCompatibilityFlag[11]) {
                /* The number of bits in this syntax structure is not affected by this condition */

                /*general_max_12bit_constraint_flag =*/ stream.readFlag(); // u(1)
                /*general_max_10bit_constraint_flag =*/ stream.readFlag(); // u(1)
                /*general_max_8bit_constraint_flag =*/ stream.readFlag(); // u(1)
                /*general_max_422chroma_constraint_flag =*/ stream.readFlag(); // u(1)
                /*general_max_420chroma_constraint_flag =*/ stream.readFlag(); // u(1)
                /*general_max_monochrome_constraint_flag =*/ stream.readFlag(); // u(1)
                /*general_intra_constraint_flag =*/ stream.readFlag(); // u(1)
                /*general_one_picture_only_constraint_flag =*/ stream.readFlag(); // u(1)
                /*general_lower_bit_rate_constraint_flag =*/ stream.readFlag(); // u(1)

                if ((generalProfileIdc & 0xFF) == 5
                        || generalProfileCompatibilityFlag[5]
                        || (generalProfileIdc & 0xFF) == 9
                        || generalProfileCompatibilityFlag[9]
                        || (generalProfileIdc & 0xFF) == 10
                        || generalProfileCompatibilityFlag[10]
                        || (generalProfileIdc & 0xFF) == 11
                        || generalProfileCompatibilityFlag[11]) {
                    /*general_max_14bit_constraint_flag =*/ stream.readFlag(); // u(1)
                    stream.skipBits(33); /* general_reserved_zero_33bits */ // u(33)
                } else {
                    stream.skipBits(34); /* general_reserved_zero_34bits */ // u(34)
                }
            } else if ((generalProfileIdc & 0xFF) == 2 || generalProfileCompatibilityFlag[2]) {
                stream.skipBits(7); /* general_reserved_zero_7bits  */ // u(7)
                /*general_one_picture_only_constraint_flag =*/ stream.readFlag(); // u(1)
                stream.skipBits(35); /* general_reserved_zero_35bits */ // u(35)
            } else {
                stream.skipBits(43); /* general_reserved_zero_43bits */ // u(43)
            }

            if ((generalProfileIdc & 0xFF) == 1
                    || generalProfileCompatibilityFlag[1]
                    || (generalProfileIdc & 0xFF) == 2
                    || generalProfileCompatibilityFlag[2]
                    || (generalProfileIdc & 0xFF) == 3
                    || generalProfileCompatibilityFlag[3]
                    || (generalProfileIdc & 0xFF) == 4
                    || generalProfileCompatibilityFlag[4]
                    || (generalProfileIdc & 0xFF) == 5
                    || generalProfileCompatibilityFlag[5]
                    || (generalProfileIdc & 0xFF) == 9
                    || generalProfileCompatibilityFlag[9]
                    || (generalProfileIdc & 0xFF) == 11
                    || generalProfileCompatibilityFlag[11]) {
                /* The number of bits in this syntax structure is not affected by this condition */
                /*general_inbld_flag =*/ stream.readFlag(); // u(1)
            } else {
                stream.skipBits(1); /* general_reserved_zero_bit */ // u(1)
            }
        }

        generalLevelIdc = stream.read(8); // u(8)
        subLayerProfilePresentFlag = new boolean[maxNumSubLayersMinus1];
        subLayerLevelPresentFlag = new boolean[maxNumSubLayersMinus1];
        for (int i = 0; i < maxNumSubLayersMinus1; i++) {
            subLayerProfilePresentFlag[i] = stream.readFlag(); // u(1)
            subLayerLevelPresentFlag[i] = stream.readFlag(); // u(1)
        }
        if (maxNumSubLayersMinus1 > 0) {
            for (int i = maxNumSubLayersMinus1; i < 8; i++) stream.skipBits(2); /* reserved_zero_2bits */ // u(2)
        }

        //        sub_layer_profile_space = new byte[maxNumSubLayersMinus1];
        //        sub_layer_tier_flag = new boolean[maxNumSubLayersMinus1];
        subLayerProfileIdc = new byte[maxNumSubLayersMinus1];
        subLayerProfileCompatibilityFlag = new boolean[maxNumSubLayersMinus1][32];
        //        sub_layer_progressive_source_flag = new boolean[maxNumSubLayersMinus1];
        //        sub_layer_interlaced_source_flag = new boolean[maxNumSubLayersMinus1];
        //        sub_layer_non_packed_constraint_flag = new boolean[maxNumSubLayersMinus1];
        //        sub_layer_frame_only_constraint_flag = new boolean[maxNumSubLayersMinus1];
        //        sub_layer_max_12bit_constraint_flag = new boolean[maxNumSubLayersMinus1];
        //        sub_layer_max_10bit_constraint_flag = new boolean[maxNumSubLayersMinus1];
        //        sub_layer_max_8bit_constraint_flag = new boolean[maxNumSubLayersMinus1];
        //        sub_layer_max_422chroma_constraint_flag = new boolean[maxNumSubLayersMinus1];
        //        sub_layer_max_420chroma_constraint_flag = new boolean[maxNumSubLayersMinus1];
        //        sub_layer_max_monochrome_constraint_flag = new boolean[maxNumSubLayersMinus1];
        //        sub_layer_intra_constraint_flag = new boolean[maxNumSubLayersMinus1];
        //        sub_layer_one_picture_only_constraint_flag = new boolean[maxNumSubLayersMinus1];
        //        sub_layer_lower_bit_rate_constraint_flag = new boolean[maxNumSubLayersMinus1];
        //        sub_layer_max_14bit_constraint_flag = new boolean[maxNumSubLayersMinus1];
        //        sub_layer_inbld_flag = new boolean[maxNumSubLayersMinus1];
        //        sub_layer_level_idc = new byte[maxNumSubLayersMinus1];

        for (int i = 0; i < maxNumSubLayersMinus1; i++) {
            if (subLayerProfilePresentFlag[i]) {
                /*sub_layer_profile_space[i] = (byte)*/
                stream.read(2); // u(2)
                /*sub_layer_tier_flag[i] =*/
                stream.readFlag(); // u(1)
                subLayerProfileIdc[i] = (byte) stream.read(5); // u(5)

                for (int j = 0; j < 32; j++) subLayerProfileCompatibilityFlag[i][j] = stream.readFlag(); // u(1)

                /*sub_layer_progressive_source_flag[i] =*/
                stream.readFlag(); // u(1)
                /*sub_layer_interlaced_source_flag[i] =*/
                stream.readFlag(); // u(1)
                /*sub_layer_non_packed_constraint_flag[i] =*/
                stream.readFlag(); // u(1)
                /*sub_layer_frame_only_constraint_flag[i] =*/
                stream.readFlag(); // u(1)
                if ((subLayerProfileIdc[i] & 0xFF) == 4
                        || subLayerProfileCompatibilityFlag[i][4]
                        || (subLayerProfileIdc[i] & 0xFF) == 5
                        || subLayerProfileCompatibilityFlag[i][5]
                        || (subLayerProfileIdc[i] & 0xFF) == 6
                        || subLayerProfileCompatibilityFlag[i][6]
                        || (subLayerProfileIdc[i] & 0xFF) == 7
                        || subLayerProfileCompatibilityFlag[i][7]
                        || (subLayerProfileIdc[i] & 0xFF) == 8
                        || subLayerProfileCompatibilityFlag[i][8]
                        || (subLayerProfileIdc[i] & 0xFF) == 9
                        || subLayerProfileCompatibilityFlag[i][9]
                        || (subLayerProfileIdc[i] & 0xFF) == 10
                        || subLayerProfileCompatibilityFlag[i][10]
                        || (subLayerProfileIdc[i] & 0xFF) == 11
                        || subLayerProfileCompatibilityFlag[i][11]) {
                    /* The number of bits in this syntax structure is not affected by this condition */

                    /*sub_layer_max_12bit_constraint_flag[i] =*/
                    stream.readFlag(); // u(1)
                    /*sub_layer_max_10bit_constraint_flag[i] =*/
                    stream.readFlag(); // u(1)
                    /*sub_layer_max_8bit_constraint_flag[i] =*/
                    stream.readFlag(); // u(1)
                    /*sub_layer_max_422chroma_constraint_flag[i] =*/
                    stream.readFlag(); // u(1)
                    /*sub_layer_max_420chroma_constraint_flag[i] =*/
                    stream.readFlag(); // u(1)
                    /*sub_layer_max_monochrome_constraint_flag[i] =*/
                    stream.readFlag(); // u(1)
                    /*sub_layer_intra_constraint_flag[i] =*/
                    stream.readFlag(); // u(1)
                    /*sub_layer_one_picture_only_constraint_flag[i] =*/
                    stream.readFlag(); // u(1)
                    /*sub_layer_lower_bit_rate_constraint_flag[i] =*/
                    stream.readFlag(); // u(1)
                    if ((subLayerProfileIdc[i] & 0xFF) == 5
                            || subLayerProfileCompatibilityFlag[i][5]
                            || (subLayerProfileIdc[i] & 0xFF) == 9
                            || subLayerProfileCompatibilityFlag[i][9]
                            || (subLayerProfileIdc[i] & 0xFF) == 10
                            || subLayerProfileCompatibilityFlag[i][10]
                            || (subLayerProfileIdc[i] & 0xFF) == 11
                            || subLayerProfileCompatibilityFlag[i][11]) {
                        /*sub_layer_max_14bit_constraint_flag[i] =*/
                        stream.readFlag(); // u(1)
                        stream.skipBits(33); /* sub_layer_reserved_zero_33bits */ // u(33)
                    } else {
                        stream.skipBits(34); /* sub_layer_reserved_zero_34bits */ // u(34)
                    }
                } else if ((subLayerProfileIdc[i] & 0xFF) == 2 || subLayerProfileCompatibilityFlag[i][2]) {
                    stream.skipBits(7); /* sub_layer_reserved_zero_7bits */ // u(7)
                    /*sub_layer_one_picture_only_constraint_flag[i] =*/
                    stream.readFlag(); // u(1)
                    stream.skipBits(35); /* sub_layer_reserved_zero_35bits */ // u(35)
                } else {
                    stream.skipBits(43); /* sub_layer_reserved_zero_43bits */ // u(43)
                }

                if ((subLayerProfileIdc[i] & 0xFF) == 1
                        || subLayerProfileCompatibilityFlag[i][1]
                        || (subLayerProfileIdc[i] & 0xFF) == 2
                        || subLayerProfileCompatibilityFlag[i][2]
                        || (subLayerProfileIdc[i] & 0xFF) == 3
                        || subLayerProfileCompatibilityFlag[i][3]
                        || (subLayerProfileIdc[i] & 0xFF) == 4
                        || subLayerProfileCompatibilityFlag[i][4]
                        || (subLayerProfileIdc[i] & 0xFF) == 5
                        || subLayerProfileCompatibilityFlag[i][5]
                        || (subLayerProfileIdc[i] & 0xFF) == 9
                        || subLayerProfileCompatibilityFlag[i][9]
                        || (subLayerProfileIdc[i] & 0xFF) == 11
                        || subLayerProfileCompatibilityFlag[i][11]) {
                    /* The number of bits in this syntax structure is not affected by this condition */
                    /*sub_layer_inbld_flag[i] =*/
                    stream.readFlag(); // u(1)
                } else {
                    stream.skipBits(1); /* sub_layer_reserved_zero_bit */ // u(1)
                }
            }
            if (subLayerLevelPresentFlag[i]) {
                /*sub_layer_level_idc[i] = (byte)*/
                stream.read(8); // u(8)
            }
        }
    }
}
