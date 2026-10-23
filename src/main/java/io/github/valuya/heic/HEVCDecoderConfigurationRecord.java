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

import org.jspecify.annotations.Nullable;

/**
 * HEVCDecoderConfigurationRecord (ISO/IEC 14496-15, 8.3.3.1): parses the parameter sets of an image item into the
 * decoder context of the stream.
 */
final class HEVCDecoderConfigurationRecord {
    // the NAL units of the item data are read with 4-byte lengths (NalUnit.parseUnit)
    private static final int SUPPORTED_LENGTH_SIZE_MINUS_ONE = 3;

    private final @Nullable SeqParameterSetRbsp firstSps;

    HEVCDecoderConfigurationRecord(BitStreamWithNalSupport stream) {
        // configurationVersion, general_profile_space, general_tier_flag, general_profile_idc,
        // general_profile_compatibility_flags, general_constraint_indicator_flags, general_level_idc
        stream.skipBits(8 + 2 + 1 + 5 + 32 + 48 + 8);
        // min_spatial_segmentation_idc, parallelismType, chromaFormat, bitDepthLumaMinus8 and bitDepthChromaMinus8,
        // each after reserved bits
        stream.skipBits(4 + 12 + 6 + 2 + 6 + 2 + 5 + 3 + 5 + 3);
        // avgFrameRate, constantFrameRate, numTemporalLayers, temporalIdNested
        stream.skipBits(16 + 2 + 3 + 1);
        int lengthSizeMinusOne = stream.read(2);
        if (lengthSizeMinusOne != SUPPORTED_LENGTH_SIZE_MINUS_ONE) {
            throw new HeicFormatException(
                    "NAL unit lengths of %d bytes are not supported".formatted(lengthSizeMinusOne + 1));
        }

        SeqParameterSetRbsp sps = null;
        int numOfArrays = stream.read(8);
        for (int j = 0; j < numOfArrays; j++) {
            stream.skipBits(1 + 1); // array_completeness, reserved
            NalUnitType nalUnitType = NalUnitType.codeToType(stream.read(6));
            int numNalus = stream.read(16);
            for (int i = 0; i < numNalus; i++) {
                int nalUnitLength = stream.read(16);
                if (nalUnitLength == 0) {
                    continue;
                }
                NalUnit unit = NalUnit.parseKnownUnit(stream, nalUnitLength, nalUnitType);
                stream.getContext().addNalContext(unit);
                if (sps == null && unit instanceof SeqParameterSetRbsp sequenceParameterSet) {
                    sps = sequenceParameterSet;
                }
            }
        }
        firstSps = sps;
    }

    /**
     * The first SPS of the record.
     *
     * @throws HeicFormatException if it has none
     */
    SeqParameterSetRbsp getSPS() {
        if (firstSps == null) {
            throw new HeicFormatException("The HEVC decoder configuration has no SPS");
        }
        return firstSps;
    }
}
