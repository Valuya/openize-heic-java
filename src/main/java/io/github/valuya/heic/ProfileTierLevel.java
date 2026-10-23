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
 * The general profile and level of profile_tier_level( ) (7.3.3), in a VPS or SPS, where the profile is always present.
 */
record ProfileTierLevel(int generalProfileIdc, int generalLevelIdc) {
    // a profile: profile space, tier, profile_idc, 32 compatibility flags, then 48 source, constraint and reserved bits
    // whose meaning, but not number, depends on the profile
    private static final int PROFILE_BITS = 2 + 1 + 5 + 32 + 48;
    private static final int LEVEL_BITS = 8;
    private static final int MAX_SUB_LAYERS = 8;

    /**
     * Reads profile_tier_level( 1, maxNumSubLayersMinus1 ).
     */
    static ProfileTierLevel read(BitStreamWithNalSupport stream, int maxNumSubLayersMinus1) {
        stream.skipBits(3); // general_profile_space, general_tier_flag
        int generalProfileIdc = stream.read(5);
        stream.skipBits(PROFILE_BITS - 8);
        int generalLevelIdc = stream.read(LEVEL_BITS);

        boolean[] subLayerProfilePresentFlag = new boolean[maxNumSubLayersMinus1];
        boolean[] subLayerLevelPresentFlag = new boolean[maxNumSubLayersMinus1];
        for (int i = 0; i < maxNumSubLayersMinus1; i++) {
            subLayerProfilePresentFlag[i] = stream.readFlag();
            subLayerLevelPresentFlag[i] = stream.readFlag();
        }
        if (maxNumSubLayersMinus1 > 0) {
            stream.skipBits(2 * (MAX_SUB_LAYERS - maxNumSubLayersMinus1)); // reserved_zero_2bits
        }
        for (int i = 0; i < maxNumSubLayersMinus1; i++) {
            if (subLayerProfilePresentFlag[i]) {
                stream.skipBits(PROFILE_BITS);
            }
            if (subLayerLevelPresentFlag[i]) {
                stream.skipBits(LEVEL_BITS); // sub_layer_level_idc
            }
        }
        return new ProfileTierLevel(generalProfileIdc, generalLevelIdc);
    }
}
