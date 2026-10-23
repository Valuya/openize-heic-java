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
 * video_parameter_set_rbsp( ) (7.3.2.1). Nothing in it matters to decoding the base layer: it is parsed to identify
 * it, and to check that it is well formed.
 */
class VideoParameterSetRbsp extends NalUnit {
    // 7.4.3.1
    private static final int MAX_VPS_NUM_LAYER_SETS_MINUS1 = 1023;

    final int vpsVideoParameterSetId;
    private final ProfileTierLevel profileTierLevel;

    VideoParameterSetRbsp(BitStreamWithNalSupport stream, long startPosition, int size) {
        super(stream, startPosition, size);

        vpsVideoParameterSetId = stream.read(4);
        // vps_base_layer_internal_flag, vps_base_layer_available_flag, vps_max_layers_minus1
        stream.skipBits(1 + 1 + 6);
        int vpsMaxSubLayersMinus1 = stream.read(3);
        // vps_temporal_id_nesting_flag, vps_reserved_0xffff_16bits
        stream.skipBits(1 + 16);

        profileTierLevel = ProfileTierLevel.read(stream, vpsMaxSubLayersMinus1);

        boolean vpsSubLayerOrderingInfoPresentFlag = stream.readFlag();
        int firstSubLayer = vpsSubLayerOrderingInfoPresentFlag ? 0 : vpsMaxSubLayersMinus1;
        for (int i = firstSubLayer; i <= vpsMaxSubLayersMinus1; i++) {
            stream.readUev(); // vps_max_dec_pic_buffering_minus1
            stream.readUev(); // vps_max_num_reorder_pics
            stream.readUev(); // vps_max_latency_increase_plus1
        }

        int vpsMaxLayerId = stream.read(6);
        int vpsNumLayerSetsMinus1 = stream.readUevAtMost(MAX_VPS_NUM_LAYER_SETS_MINUS1);
        // layer_id_included_flag[ i ][ j ] for each layer set but the first, and each layer id
        stream.skipBits(vpsNumLayerSetsMinus1 * (vpsMaxLayerId + 1));

        boolean vpsTimingInfoPresentFlag = stream.readFlag();
        if (vpsTimingInfoPresentFlag) {
            stream.skipBits(32 + 32); // vps_num_units_in_tick, vps_time_scale
            boolean vpsPocProportionalToTimingFlag = stream.readFlag();
            if (vpsPocProportionalToTimingFlag) {
                stream.readUev(); // vps_num_ticks_poc_diff_one_minus1
            }
            int vpsNumHrdParameters = stream.readUevAtMost(vpsNumLayerSetsMinus1 + 1);
            skipHrdParameters(stream, vpsNumHrdParameters, vpsMaxSubLayersMinus1);
        }
        // vps_extension_flag and vps_extension( ) (F.7.3.2.1.1) describe the layers above the base one
    }

    private static void skipHrdParameters(BitStreamWithNalSupport stream, int count, int maxNumSubLayersMinus1) {
        if (count == 0) {
            return;
        }
        stream.readUev(); // hrd_layer_set_idx[ 0 ]
        // cprms_present_flag[ 0 ] is inferred to be 1 (7.4.3.1)
        HrdParameters hrdParameters = HrdParameters.read(stream, maxNumSubLayersMinus1);
        for (int i = 1; i < count; i++) {
            stream.readUev(); // hrd_layer_set_idx[ i ]
            boolean cprmsPresentFlag = stream.readFlag();
            hrdParameters = cprmsPresentFlag
                    ? HrdParameters.read(stream, maxNumSubLayersMinus1)
                    : hrdParameters.readNextWithSameCommonParameters(stream, maxNumSubLayersMinus1);
        }
    }

    @Override
    public final String toString() {
        return "NAL Unit VPS %d, profile %d, level %d"
                .formatted(
                        vpsVideoParameterSetId,
                        profileTierLevel.generalProfileIdc(),
                        profileTierLevel.generalLevelIdc());
    }
}
