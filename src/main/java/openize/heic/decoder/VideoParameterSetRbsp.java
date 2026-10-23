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

public class VideoParameterSetRbsp extends NalUnit {
    final byte vpsVideoParameterSetId; // max - 16
    final boolean vpsBaseLayerInternalFlag;
    final boolean vpsBaseLayerAvailableFlag;
    final byte vpsMaxLayersMinus1;
    final byte vpsMaxSubLayersMinus1;
    final boolean vpsTemporalIdNestingFlag;
    final ProfileTierLevel ProfileTierLevel;

    final boolean vpsSubLayerOrderingInfoPresentFlag;

    //    final /*UInt32*/ long[] vps_max_dec_pic_buffering_minus1;
    //    final /*UInt32*/ long[] vps_max_num_reorder_pics;
    //    final /*UInt32*/ long[] vps_max_latency_increase_plus1;

    final byte vpsMaxLayerId;
    final /*UInt32*/ long vpsNumLayerSetsMinus1;

    // final boolean[][] layer_id_included_flag;
    final boolean vpsTimingInfoPresentFlag;
    /*UInt32*/ long vpsNumUnitsInTick;
    /*UInt32*/ long vpsTimeScale;
    boolean vpsPocProportionalToTimingFlag;
    /*UInt32*/ long vpsNumTicksPocDiffOne;
    /*UInt32*/ long vpsNumHrdParameters;

    /// *UInt32*/ long[] hrd_layer_set_idx;
    boolean[] cprmsPresentFlag;
    final boolean vpsExtensionFlag;
    boolean vpsExtensionDataFlag;
    HrdParameters HrdParameters;

    VideoParameterSetRbsp(BitStreamWithNalSupport stream, /*UInt64*/ long startPosition, int size) {
        super(stream, startPosition, size);

        vpsVideoParameterSetId = (byte) stream.read(4); // u(4)
        vpsBaseLayerInternalFlag = stream.readFlag(); // u(1)
        vpsBaseLayerAvailableFlag = stream.readFlag(); // u(1)
        vpsMaxLayersMinus1 = (byte) stream.read(6); // u(6)
        vpsMaxSubLayersMinus1 = (byte) stream.read(3); // u(3)
        vpsTemporalIdNestingFlag = stream.readFlag(); // u(1)
        stream.skipBits(16); // vps_reserved_0xffff_16bits                  // u(16)

        ProfileTierLevel = new ProfileTierLevel(stream, true, vpsMaxSubLayersMinus1 & 0xFF);

        vpsSubLayerOrderingInfoPresentFlag = stream.readFlag(); // u(1)

        byte layersCount = (byte) (vpsSubLayerOrderingInfoPresentFlag ? 0 : vpsMaxLayersMinus1);
        //        vps_max_dec_pic_buffering_minus1 = new /*UInt32*/long[(vps_max_layers_minus1 & 0xFF) + 1];
        //        vps_max_num_reorder_pics = new /*UInt32*/long[(vps_max_layers_minus1 & 0xFF) + 1];
        //        vps_max_latency_increase_plus1 = new /*UInt32*/long[(vps_max_layers_minus1 & 0xFF) + 1];

        for (int i = layersCount & 0xFF; i <= (vpsMaxLayersMinus1 & 0xFF); i++) {
            /*vps_max_dec_pic_buffering_minus1[i] =*/ stream.readUev(); // ue(v)
            /*vps_max_num_reorder_pics[i] =*/ stream.readUev(); // ue(v)
            /*vps_max_latency_increase_plus1[i] =*/ stream.readUev(); // ue(v)
        }

        vpsMaxLayerId = (byte) stream.read(6); // u(6)
        vpsNumLayerSetsMinus1 = stream.readUev(); // ue(v)

        // layer_id_included_flag = new boolean[(int) (((vps_num_layer_sets_minus1 & 0xFFFFFFFFL) + 2) &
        // 0xFFFFFFFFL)][(vps_max_layer_id & 0xFF) + 1];

        //        for (int i = 1; i <= (vps_num_layer_sets_minus1 & 0xFFFFFFFFL); i++)
        //            for (int j = 0; j <= (vps_max_layer_id & 0xFF); j++)
        //                layer_id_included_flag[i][j] = stream.readFlag();    // u(1)

        vpsTimingInfoPresentFlag = stream.readFlag(); // u(1)

        if (vpsTimingInfoPresentFlag) {
            vpsNumUnitsInTick = stream.read(32) & 0xFFFFFFFFL; // u(32)
            vpsTimeScale = stream.read(32) & 0xFFFFFFFFL; // u(32)
            vpsPocProportionalToTimingFlag = stream.readFlag(); // u(1)
            if (vpsPocProportionalToTimingFlag) {
                vpsNumTicksPocDiffOne = (((stream.readUev() & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL); // ue(v)
            }

            vpsNumHrdParameters = (stream.readUev()); // ue(v)
            if (vpsNumHrdParameters > 1024) {
                throw new HeicFormatException("%d HRD parameter sets, more than 1024".formatted(vpsNumHrdParameters));
            }
            // hrd_layer_set_idx = new /*UInt32*/long[(int) (vps_num_hrd_parameters & 0xFFFFFFFFL)];
            cprmsPresentFlag = new boolean[(int) (vpsNumHrdParameters & 0xFFFFFFFFL)];
            for (int i = 0; i < (vpsNumHrdParameters & 0xFFFFFFFFL); i++) {
                /*hrd_layer_set_idx[i] =*/ stream.readUev(); // ue(v)
                if (i > 0) {
                    cprmsPresentFlag[i] = stream.readFlag(); // u(1)
                }

                HrdParameters = new HrdParameters(stream, cprmsPresentFlag[i], vpsMaxSubLayersMinus1 & 0xFF);
            }
        }

        vpsExtensionFlag = stream.readFlag(); // u(1)
        if (vpsExtensionFlag) {
            while (stream.hasMoreRbspData(getEndPosition())) vpsExtensionDataFlag = stream.readFlag(); // u(1)
        }
        new RbspTrailingBits(stream);
    }

    @Override
    public final String toString() {
        return String.format(
                "NAL Unit VPS \nProfile: %d\nLevel: %d",
                ProfileTierLevel.generalProfileIdc, ProfileTierLevel.generalLevelIdc);
    }
}
