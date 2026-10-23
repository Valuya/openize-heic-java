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

class PpsSccExtension {
    final boolean ppsCurrPicRefEnabledFlag;
    final boolean residualAdaptiveColourTransformEnabledFlag;
    final boolean ppsPalettePredictorInitializersPresentFlag;
    boolean ppsSliceActQpOffsetsPresentFlag;
    int ppsActYQpOffsetPlus5;
    int ppsActCbQpOffsetPlus5;
    int ppsActCrQpOffsetPlus3;
    /*UInt32*/ long ppsNumPalettePredictorInitializers;
    boolean monochromePaletteFlag;
    byte lumaBitDepthEntryMinus8;
    byte chromaBitDepthEntryMinus8;

    /// *UInt16*/int[][] pps_palette_predictor_initializer;

    public PpsSccExtension(BitStreamWithNalSupport stream) {
        ppsCurrPicRefEnabledFlag = stream.readFlag(); // u(1)
        residualAdaptiveColourTransformEnabledFlag = stream.readFlag(); // u(1)
        if (residualAdaptiveColourTransformEnabledFlag) {
            ppsSliceActQpOffsetsPresentFlag = stream.readFlag(); // u(1)
            ppsActYQpOffsetPlus5 = stream.readSev(); // se(v)
            ppsActCbQpOffsetPlus5 = stream.readSev(); // se(v)
            ppsActCrQpOffsetPlus3 = stream.readSev(); // se(v)
        }
        ppsPalettePredictorInitializersPresentFlag = stream.readFlag(); // u(1)
        if (ppsPalettePredictorInitializersPresentFlag) {
            ppsNumPalettePredictorInitializers = stream.readUev(); // ue(v)
            if (ppsNumPalettePredictorInitializers > 0) {
                // they would seed the palette predictor, which this decoder starts empty
                throw new HeicFormatException("PPS palette predictor initializers are not supported");
            }
            if ((ppsNumPalettePredictorInitializers & 0xFFFFFFFFL) > 0) {
                monochromePaletteFlag = stream.readFlag(); // u(1)
                lumaBitDepthEntryMinus8 = (byte) stream.readUev(); // ue(v)
                if (!monochromePaletteFlag) {
                    chromaBitDepthEntryMinus8 = (byte) stream.readUev();
                }

                int numComps = monochromePaletteFlag ? 1 : 3;
                // pps_palette_predictor_initializer = new
                // /*UInt16*/int[numComps][(int)(pps_num_palette_predictor_initializers & 0xFFFFFFFFL)];

                for (int comp = 0; comp < numComps; comp++)
                    for (int i = 0; i < (ppsNumPalettePredictorInitializers & 0xFFFFFFFFL); i++) {
                        // pps_palette_predictor_initializer[comp][i] =
                        stream.read(8
                                + ((comp == 0
                                                ? // u(v)
                                                lumaBitDepthEntryMinus8
                                                : chromaBitDepthEntryMinus8)
                                        & 0xFF)); // & 0xFFFF;
                    }
            }
        }
    }

    public PpsSccExtension() {
        ppsCurrPicRefEnabledFlag = false;
        residualAdaptiveColourTransformEnabledFlag = false;
        ppsSliceActQpOffsetsPresentFlag = false;
        ppsActYQpOffsetPlus5 = 0;
        ppsActCbQpOffsetPlus5 = 0;
        ppsActCrQpOffsetPlus3 = 0;
        ppsPalettePredictorInitializersPresentFlag = false;
        ppsNumPalettePredictorInitializers = 0;
        monochromePaletteFlag = false;
        lumaBitDepthEntryMinus8 = 0;
        chromaBitDepthEntryMinus8 = 0;
        // pps_palette_predictor_initializer = new /*UInt16*/int[3][0];
    }
}
