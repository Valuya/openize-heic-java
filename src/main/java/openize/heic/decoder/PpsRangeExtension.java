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

class PpsRangeExtension {
    /*UInt32*/ long log2MaxTransformSkipBlockSizeMinus2;
    final boolean crossComponentPredictionEnabledFlag;
    final boolean chromaQpOffsetListEnabledFlag;
    /*UInt32*/ long diffCuChromaQpOffsetDepth;
    int chromaQpOffsetListLenMinus1;
    int[] cbQpOffsetList;
    int[] crQpOffsetList;
    final /*UInt32*/ long log2SaoOffsetScaleLuma;
    final /*UInt32*/ long log2SaoOffsetScaleChroma;

    public PpsRangeExtension(BitStreamWithNalSupport stream, boolean transformSkipEnabledFlag) {
        if (transformSkipEnabledFlag) log2MaxTransformSkipBlockSizeMinus2 = stream.readUev(); // ue(v)
        crossComponentPredictionEnabledFlag = stream.readFlag(); // u(1)
        chromaQpOffsetListEnabledFlag = stream.readFlag(); // u(1)
        if (chromaQpOffsetListEnabledFlag) {
            diffCuChromaQpOffsetDepth = stream.readUev(); // ue(v)
            chromaQpOffsetListLenMinus1 = (int) stream.readUev(); // ue(v)
            cbQpOffsetList = new int[chromaQpOffsetListLenMinus1 + 1];
            crQpOffsetList = new int[chromaQpOffsetListLenMinus1 + 1];
            for (int i = 0; i <= chromaQpOffsetListLenMinus1; i++) {
                cbQpOffsetList[i] = stream.readSev(); // se(v)
                crQpOffsetList[i] = stream.readSev(); // se(v)
            }
        }
        log2SaoOffsetScaleLuma = stream.readUev(); // ue(v)
        log2SaoOffsetScaleChroma = stream.readUev(); // ue(v)
    }

    public PpsRangeExtension() {
        log2MaxTransformSkipBlockSizeMinus2 = 0;
        crossComponentPredictionEnabledFlag = false;
        chromaQpOffsetListEnabledFlag = false;
        diffCuChromaQpOffsetDepth = 0;
        chromaQpOffsetListLenMinus1 = -1;
        log2SaoOffsetScaleLuma = 0;
        log2SaoOffsetScaleChroma = 0;
    }
}
