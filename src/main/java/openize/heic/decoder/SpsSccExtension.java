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

/**
 * sps_scc_extension( ) (7.3.2.2.3); no screen content coding tools for a sequence parameter set without one.
 */
class SpsSccExtension {
    // PaletteMaxSize at most 64, PaletteMaxPredictorSize at most 128 (7.4.3.3.8)
    private static final int MAX_PALETTE_SIZE = 64;
    private static final int MAX_PALETTE_PREDICTOR_SIZE = 128;

    final boolean paletteModeEnabledFlag;
    final int paletteMaxSize;
    final int motionVectorResolutionControlIdc;
    final boolean intraBoundaryFilteringDisabledFlag;

    SpsSccExtension() {
        paletteModeEnabledFlag = false;
        paletteMaxSize = 0;
        motionVectorResolutionControlIdc = 0;
        intraBoundaryFilteringDisabledFlag = false;
    }

    SpsSccExtension(BitStreamWithNalSupport stream) {
        stream.skipBits(1); // sps_curr_pic_ref_enabled_flag: the PPS flag is the one slices use
        paletteModeEnabledFlag = stream.readFlag();
        if (paletteModeEnabledFlag) {
            paletteMaxSize = stream.readUevAtMost(MAX_PALETTE_SIZE);
            stream.readUevAtMost(MAX_PALETTE_PREDICTOR_SIZE - paletteMaxSize); // delta_palette_max_predictor_size
            boolean spsPalettePredictorInitializersPresentFlag = stream.readFlag();
            if (spsPalettePredictorInitializersPresentFlag) {
                // they would seed the palette predictor, which this decoder starts empty
                throw new HeicFormatException("SPS palette predictor initializers are not supported");
            }
        } else {
            paletteMaxSize = 0;
        }
        motionVectorResolutionControlIdc = stream.read(2);
        intraBoundaryFilteringDisabledFlag = stream.readFlag();
    }
}
