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
 * pps_range_extension( ) (7.3.2.3.2); the inferred values for a picture parameter set without one.
 */
class PpsRangeExtension {
    // chroma_qp_offset_list_len_minus1 (7.4.3.3.2)
    private static final int MAX_CHROMA_QP_OFFSET_LIST_LEN_MINUS1 = 5;

    final int log2MaxTransformSkipBlockSizeMinus2;
    final boolean crossComponentPredictionEnabledFlag;
    final boolean chromaQpOffsetListEnabledFlag;
    final int diffCuChromaQpOffsetDepth;
    // -1 without a list
    final int chromaQpOffsetListLenMinus1;
    final int[] cbQpOffsetList;
    final int[] crQpOffsetList;
    final int log2SaoOffsetScaleLuma;
    final int log2SaoOffsetScaleChroma;

    PpsRangeExtension() {
        log2MaxTransformSkipBlockSizeMinus2 = 0;
        crossComponentPredictionEnabledFlag = false;
        chromaQpOffsetListEnabledFlag = false;
        diffCuChromaQpOffsetDepth = 0;
        chromaQpOffsetListLenMinus1 = -1;
        cbQpOffsetList = new int[0];
        crQpOffsetList = new int[0];
        log2SaoOffsetScaleLuma = 0;
        log2SaoOffsetScaleChroma = 0;
    }

    PpsRangeExtension(BitStreamWithNalSupport stream, SeqParameterSetRbsp sps, boolean transformSkipEnabledFlag) {
        log2MaxTransformSkipBlockSizeMinus2 =
                transformSkipEnabledFlag ? stream.readUevAtMost(sps.getMaxTbLog2SizeY() - 2) : 0;
        crossComponentPredictionEnabledFlag = stream.readFlag();
        chromaQpOffsetListEnabledFlag = stream.readFlag();
        if (chromaQpOffsetListEnabledFlag) {
            diffCuChromaQpOffsetDepth = stream.readUevAtMost(sps.getCtbLog2SizeY() - sps.getMinCbLog2SizeY());
            chromaQpOffsetListLenMinus1 = stream.readUevAtMost(MAX_CHROMA_QP_OFFSET_LIST_LEN_MINUS1);
            cbQpOffsetList = new int[chromaQpOffsetListLenMinus1 + 1];
            crQpOffsetList = new int[chromaQpOffsetListLenMinus1 + 1];
            for (int i = 0; i <= chromaQpOffsetListLenMinus1; i++) {
                cbQpOffsetList[i] = stream.readSev();
                crQpOffsetList[i] = stream.readSev();
            }
        } else {
            diffCuChromaQpOffsetDepth = 0;
            chromaQpOffsetListLenMinus1 = -1;
            cbQpOffsetList = new int[0];
            crQpOffsetList = new int[0];
        }
        // SAO offsets scale up by at most the bit depth beyond 10 bits
        log2SaoOffsetScaleLuma = stream.readUevAtMost(Math.max(0, sps.getBitDepthY() - 10));
        log2SaoOffsetScaleChroma = stream.readUevAtMost(Math.max(0, sps.getBitDepthC() - 10));
    }
}
