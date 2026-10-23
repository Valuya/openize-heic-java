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
 * What an auxiliary image of an image holds, from its auxiliary type property ({@code auxC}).
 */
public enum AuxiliaryReferenceType {
    /** The opacity of the image's pixels. */
    ALPHA,
    /** The distance of the image's pixels from the camera. */
    DEPTH_MAP,
    /** Apple: the gain map that renders the image in high dynamic range. */
    HDR_GAIN_MAP,
    /** Apple: the matte separating the subject of a portrait. */
    PORTRAIT_EFFECTS_MATTE,
    /** Apple: the semantic segmentation matte of hair. */
    SEMANTIC_HAIR_MATTE,
    /** Apple: the semantic segmentation matte of skin. */
    SEMANTIC_SKIN_MATTE,
    /** Apple: the semantic segmentation matte of teeth. */
    SEMANTIC_TEETH_MATTE,
    /** Apple: the semantic segmentation matte of glasses. */
    SEMANTIC_GLASSES_MATTE,
    /** Apple: the semantic segmentation matte of the sky. */
    SEMANTIC_SKY_MATTE,
    /** Apple: a thumbnail in linear light. */
    LINEAR_THUMBNAIL,
    /** Apple: the photographic style applied to the image. */
    STYLE_DELTA_MAP,
    /** Not an auxiliary image, or of a type this decoder does not know. */
    UNDEFINED;

    private static final String APPLE_AUXILIARY_TYPE_PREFIX = ":aux:";

    /**
     * The type an auxiliary type URN names, with or without its terminating {@code '\0'}.
     */
    static AuxiliaryReferenceType of(String auxType) {
        String urn = auxType.endsWith("\0") ? auxType.substring(0, auxType.length() - 1) : auxType;
        return switch (urn) {
            case "urn:mpeg:hevc:2015:auxid:1", "urn:mpeg:mpegB:cicp:systems:auxiliary:alpha" -> ALPHA;
            case "urn:mpeg:hevc:2015:auxid:2", "urn:mpeg:mpegB:cicp:systems:auxiliary:depth" -> DEPTH_MAP;
            default -> ofApple(urn);
        };
    }

    // urn:com:apple:photo:2020:aux:hdrgainmap and the like
    private static AuxiliaryReferenceType ofApple(String urn) {
        int typeStart = urn.indexOf(APPLE_AUXILIARY_TYPE_PREFIX);
        if (!urn.contains("apple") || typeStart < 0) {
            return UNDEFINED;
        }
        return switch (urn.substring(typeStart + APPLE_AUXILIARY_TYPE_PREFIX.length())) {
            case "portraiteffectsmatte" -> PORTRAIT_EFFECTS_MATTE;
            case "semanticskinmatte" -> SEMANTIC_SKIN_MATTE;
            case "semantichairmatte" -> SEMANTIC_HAIR_MATTE;
            case "semanticteethmatte" -> SEMANTIC_TEETH_MATTE;
            case "semanticglassesmatte" -> SEMANTIC_GLASSES_MATTE;
            case "semanticskymatte" -> SEMANTIC_SKY_MATTE;
            case "hdrgainmap" -> HDR_GAIN_MAP;
            case "styledeltamap" -> STYLE_DELTA_MAP;
            case "linearthumbnail" -> LINEAR_THUMBNAIL;
            default -> UNDEFINED;
        };
    }
}
