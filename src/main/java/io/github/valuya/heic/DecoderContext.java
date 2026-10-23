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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The state of one decode: the parameter sets by id, the picture being decoded, the workspace it is decoded in, and
 * the coding unit state that carries over from one coding unit to the next, across slice segments.
 */
final class DecoderContext {
    private final Map<Integer, VideoParameterSetRbsp> videoParameterSets = new HashMap<>();
    private final Map<Integer, SeqParameterSetRbsp> sequenceParameterSets = new HashMap<>();
    private final Map<Integer, PicParameterSetRbsp> pictureParameterSets = new HashMap<>();
    // set when a picture is decoded, created then otherwise: parsing the parameter sets needs none
    private @Nullable DecoderWorkspace workspace;
    private HeicDecodingOptions decodingOptions = HeicDecodingOptions.DEFAULT;
    private @Nullable HeicPicture currentPicture;

    // cu_transquant_bypass_flag of the current coding unit, transform_skip_flag of its current transform blocks
    boolean cuTransquantBypassFlag;
    final boolean[] transformSkipFlag = new boolean[3];

    // 7.4.9.14: the cu_qp_delta and cu_chroma_qp_offset state of the current quantization group
    boolean isCuQpDeltaCoded;
    int cuQpDeltaVal;
    boolean isCuChromaQpOffsetCoded;
    int cuQpOffsetCb;
    int cuQpOffsetCr;

    // 8.6.1: the quantization group being decoded, the QpY prediction it starts from, and the last coding unit's QpY
    int currentQgX;
    int currentQgY;
    int previousQgQpY;
    int lastQpY;

    // 8.6.1: Qp'Y, Qp'Cb and Qp'Cr of the current coding unit
    int qpPrimeY;
    int qpPrimeCb;
    int qpPrimeCr;

    Map<Integer, VideoParameterSetRbsp> getVPS() {
        return videoParameterSets;
    }

    Map<Integer, SeqParameterSetRbsp> getSPS() {
        return sequenceParameterSets;
    }

    Map<Integer, PicParameterSetRbsp> getPPS() {
        return pictureParameterSets;
    }

    /**
     * Registers a parameter set; other NAL units, including those of layers that are not decoded, are ignored.
     */
    void addNalContext(NalUnit nalUnit) {
        switch (nalUnit) {
            case VideoParameterSetRbsp vps -> videoParameterSets.put(vps.vpsVideoParameterSetId & 0xFF, vps);
            case SeqParameterSetRbsp sps -> sequenceParameterSets.put(sps.spsSeqParameterSetId & 0xFF, sps);
            case PicParameterSetRbsp pps -> pictureParameterSets.put(pps.ppsPicParameterSetId & 0xFF, pps);
            default -> {
                // not a parameter set
            }
        }
    }

    Optional<HeicPicture> getCurrentPictureOptional() {
        return Optional.ofNullable(currentPicture);
    }

    /**
     * Starts the picture whose first slice segment has {@code header}.
     */
    HeicPicture startPicture(SliceSegmentHeader header) {
        currentPicture = new HeicPicture(header, getWorkspace());
        return currentPicture;
    }

    HeicDecodingOptions getDecodingOptions() {
        return decodingOptions;
    }

    void setDecodingOptions(HeicDecodingOptions decodingOptions) {
        this.decodingOptions = decodingOptions;
    }

    /** Decodes in {@code workspace}, a workspace no other decode uses meanwhile. */
    void setWorkspace(DecoderWorkspace workspace) {
        this.workspace = workspace;
    }

    private DecoderWorkspace getWorkspace() {
        DecoderWorkspace current = workspace;
        if (current == null) {
            current = new DecoderWorkspace();
            workspace = current;
        }
        return current;
    }

    ResidualBuffers getResidualBuffers() {
        return getWorkspace().residualBuffers;
    }

    IntraPredictionBuffers getIntraPredictionBuffers() {
        return getWorkspace().intraPredictionBuffers;
    }
}
