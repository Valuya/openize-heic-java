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
 * pps_scc_extension( ) (7.3.2.3.3); no screen content coding tools for a picture parameter set without one.
 */
class PpsSccExtension {
    final boolean ppsCurrPicRefEnabledFlag;
    final boolean residualAdaptiveColourTransformEnabledFlag;
    final boolean ppsSliceActQpOffsetsPresentFlag;
    final int ppsActYQpOffsetPlus5;
    final int ppsActCbQpOffsetPlus5;
    final int ppsActCrQpOffsetPlus3;

    PpsSccExtension() {
        ppsCurrPicRefEnabledFlag = false;
        residualAdaptiveColourTransformEnabledFlag = false;
        ppsSliceActQpOffsetsPresentFlag = false;
        ppsActYQpOffsetPlus5 = 0;
        ppsActCbQpOffsetPlus5 = 0;
        ppsActCrQpOffsetPlus3 = 0;
    }

    PpsSccExtension(BitStreamWithNalSupport stream) {
        ppsCurrPicRefEnabledFlag = stream.readFlag();
        residualAdaptiveColourTransformEnabledFlag = stream.readFlag();
        if (residualAdaptiveColourTransformEnabledFlag) {
            ppsSliceActQpOffsetsPresentFlag = stream.readFlag();
            ppsActYQpOffsetPlus5 = stream.readSev();
            ppsActCbQpOffsetPlus5 = stream.readSev();
            ppsActCrQpOffsetPlus3 = stream.readSev();
        } else {
            ppsSliceActQpOffsetsPresentFlag = false;
            ppsActYQpOffsetPlus5 = 0;
            ppsActCbQpOffsetPlus5 = 0;
            ppsActCrQpOffsetPlus3 = 0;
        }
        boolean ppsPalettePredictorInitializersPresentFlag = stream.readFlag();
        if (ppsPalettePredictorInitializersPresentFlag) {
            long ppsNumPalettePredictorInitializers = stream.readUev();
            if (ppsNumPalettePredictorInitializers > 0) {
                // they would seed the palette predictor, which this decoder starts empty
                throw new HeicFormatException("PPS palette predictor initializers are not supported");
            }
        }
    }
}
