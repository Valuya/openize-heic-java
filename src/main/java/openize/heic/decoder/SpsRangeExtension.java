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

/**
 * sps_range_extension( ) (7.3.2.2.2); all flags 0 for a sequence parameter set without one.
 */
class SpsRangeExtension {
    final boolean transformSkipRotationEnabledFlag;
    final boolean transformSkipContextEnabledFlag;
    final boolean implicitRdpcmEnabledFlag;
    final boolean explicitRdpcmEnabledFlag;
    final boolean extendedPrecisionProcessingFlag;
    final boolean intraSmoothingDisabledFlag;
    final boolean persistentRiceAdaptationEnabledFlag;

    SpsRangeExtension() {
        transformSkipRotationEnabledFlag = false;
        transformSkipContextEnabledFlag = false;
        implicitRdpcmEnabledFlag = false;
        explicitRdpcmEnabledFlag = false;
        extendedPrecisionProcessingFlag = false;
        intraSmoothingDisabledFlag = false;
        persistentRiceAdaptationEnabledFlag = false;
    }

    SpsRangeExtension(BitStreamWithNalSupport stream) {
        transformSkipRotationEnabledFlag = stream.readFlag();
        transformSkipContextEnabledFlag = stream.readFlag();
        implicitRdpcmEnabledFlag = stream.readFlag();
        explicitRdpcmEnabledFlag = stream.readFlag();
        extendedPrecisionProcessingFlag = stream.readFlag();
        intraSmoothingDisabledFlag = stream.readFlag();
        stream.skipBits(1); // high_precision_offsets_enabled_flag: weighted prediction, in P and B slices only
        persistentRiceAdaptationEnabledFlag = stream.readFlag();
        boolean cabacBypassAlignmentEnabledFlag = stream.readFlag();
        if (cabacBypassAlignmentEnabledFlag) {
            // the bypass bins of the residual coding would need the aligned bypass decoding, not implemented
            throw new HeicFormatException(
                    "CABAC bypass alignment (cabac_bypass_alignment_enabled_flag) is not supported");
        }
    }
}
