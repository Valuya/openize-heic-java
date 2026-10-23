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

class SpsRangeExtension {
    final boolean transformSkipRotationEnabledFlag;
    final boolean transformSkipContextEnabledFlag;
    final boolean implicitRdpcmEnabledFlag;
    final boolean explicitRdpcmEnabledFlag;
    final boolean extendedPrecisionProcessingFlag;
    final boolean intraSmoothingDisabledFlag;
    final boolean highPrecisionOffsetsEnabledFlag;
    final boolean persistentRiceAdaptationEnabledFlag;
    final boolean cabacBypassAlignmentEnabledFlag;

    public SpsRangeExtension(BitStreamWithNalSupport stream) {
        transformSkipRotationEnabledFlag = stream.readFlag(); // u(1)
        transformSkipContextEnabledFlag = stream.readFlag(); // u(1)
        implicitRdpcmEnabledFlag = stream.readFlag(); // u(1)
        explicitRdpcmEnabledFlag = stream.readFlag(); // u(1)
        extendedPrecisionProcessingFlag = stream.readFlag(); // u(1)
        intraSmoothingDisabledFlag = stream.readFlag(); // u(1)
        highPrecisionOffsetsEnabledFlag = stream.readFlag(); // u(1)
        persistentRiceAdaptationEnabledFlag = stream.readFlag(); // u(1)
        cabacBypassAlignmentEnabledFlag = stream.readFlag(); // u(1)
    }

    public SpsRangeExtension() {
        transformSkipRotationEnabledFlag = false;
        transformSkipContextEnabledFlag = false;
        implicitRdpcmEnabledFlag = false;
        explicitRdpcmEnabledFlag = false;
        extendedPrecisionProcessingFlag = false;
        intraSmoothingDisabledFlag = false;
        highPrecisionOffsetsEnabledFlag = false;
        persistentRiceAdaptationEnabledFlag = false;
        cabacBypassAlignmentEnabledFlag = false;
    }
}
