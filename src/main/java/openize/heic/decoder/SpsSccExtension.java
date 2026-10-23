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


class SpsSccExtension
{
    final boolean spsCurrPicRefEnabledFlag;
    final boolean paletteModeEnabledFlag;
    /*UInt32*/ long paletteMaxSize;
    /*UInt32*/ long deltaPaletteMaxPredictorSize;
    boolean spsPalettePredictorInitializersPresentFlag;
    /*UInt32*/ long spsNumPalettePredictorInitializersMinus1;
    // /*UInt16*/ int[][] sps_palette_predictor_initializer;
    byte motionVectorResolutionControlIdc;
    boolean intraBoundaryFilteringDisabledFlag;

    public SpsSccExtension(BitStreamWithNalSupport stream, /*UInt32*/long chromaFormatIdc, byte bitDepthLumaMinus8, byte bitDepthChromaMinus8)
    {
        spsCurrPicRefEnabledFlag = stream.readFlag();           // u(1)
        paletteModeEnabledFlag = stream.readFlag();               // u(1)
        if (paletteModeEnabledFlag)
        {
            paletteMaxSize = stream.readUev();                           // ue(v)
            deltaPaletteMaxPredictorSize = stream.readUev();           // ue(v)
            spsPalettePredictorInitializersPresentFlag = stream.readFlag(); // u(1)
            if (spsPalettePredictorInitializersPresentFlag)
            {
                spsNumPalettePredictorInitializersMinus1 = stream.readUev(); // ue(v)
                assert ((((spsNumPalettePredictorInitializersMinus1 & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL) <= (((paletteMaxSize & 0xFFFFFFFFL) + (deltaPaletteMaxPredictorSize & 0xFFFFFFFFL)) & 0xFFFFFFFFL));

                int numComps = ((chromaFormatIdc & 0xFFFFFFFFL) == 0) ? 1 : 3;

                // sps_palette_predictor_initializer = new /*UInt16*/int[numComps][(int) (((sps_num_palette_predictor_initializers_minus1 & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL)];

                for (int comp = 0; comp < numComps; comp++)
                    for (int i = 0; i <= (spsNumPalettePredictorInitializersMinus1 & 0xFFFFFFFFL); i++)
                    {
                        /*sps_palette_predictor_initializer[comp][i] =*/        // u(v)
                        stream.read(((comp == 0 ? bitDepthLumaMinus8 : bitDepthChromaMinus8) & 0xFF) + 8); // & 0xFFFF;
                    }
            }
        }
        motionVectorResolutionControlIdc = (byte) stream.read(2);   // u(2)
        intraBoundaryFilteringDisabledFlag = stream.readFlag();  // u(1)
    }

    public SpsSccExtension(/*UInt32*/long chromaFormatIdc)
    {
        spsCurrPicRefEnabledFlag = false;
        paletteModeEnabledFlag = false;
        paletteMaxSize = 0;
        deltaPaletteMaxPredictorSize = 0;
        spsPalettePredictorInitializersPresentFlag = false;
        spsNumPalettePredictorInitializersMinus1 = 0;

        // int numComps = ((chroma_format_idc & 0xFFFFFFFFL) == 0) ? 1 : 3;
        //sps_palette_predictor_initializer = new /*UInt16*/int[numComps][(int) (((sps_num_palette_predictor_initializers_minus1 & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL)];

        motionVectorResolutionControlIdc = 0;
        intraBoundaryFilteringDisabledFlag = false;
    }
}
