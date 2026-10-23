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

class SpsSccExtension {
    final boolean spsCurrPicRefEnabledFlag;
    final boolean paletteModeEnabledFlag;
    int paletteMaxSize;
    int deltaPaletteMaxPredictorSize;
    boolean spsPalettePredictorInitializersPresentFlag;
    byte motionVectorResolutionControlIdc;
    boolean intraBoundaryFilteringDisabledFlag;

    public SpsSccExtension(
            BitStreamWithNalSupport stream, int chromaFormatIdc, int bitDepthLumaMinus8, int bitDepthChromaMinus8) {
        spsCurrPicRefEnabledFlag = stream.readFlag(); // u(1)
        paletteModeEnabledFlag = stream.readFlag(); // u(1)
        if (paletteModeEnabledFlag) {
            // PaletteMaxSize at most 64, PaletteMaxPredictorSize at most 128 (7.4.3.3.8)
            paletteMaxSize = stream.readUevAtMost(64); // ue(v)
            deltaPaletteMaxPredictorSize = stream.readUevAtMost(128 - paletteMaxSize); // ue(v)
            spsPalettePredictorInitializersPresentFlag = stream.readFlag(); // u(1)
            if (spsPalettePredictorInitializersPresentFlag) {
                // they would seed the palette predictor, which this decoder starts empty
                throw new HeicFormatException("SPS palette predictor initializers are not supported");
            }
        }
        motionVectorResolutionControlIdc = (byte) stream.read(2); // u(2)
        intraBoundaryFilteringDisabledFlag = stream.readFlag(); // u(1)
    }

    /**
     * The extension of a sequence parameter set without one: no screen content coding tools.
     */
    public SpsSccExtension() {
        spsCurrPicRefEnabledFlag = false;
        paletteModeEnabledFlag = false;
    }
}
