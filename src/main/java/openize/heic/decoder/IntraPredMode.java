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
 * The intra prediction modes (Table 8-1): the ordinal of each is its number, predModeIntra.
 */
enum IntraPredMode {
    INTRA_PLANAR,
    INTRA_DC,
    INTRA_ANGULAR2,
    INTRA_ANGULAR3,
    INTRA_ANGULAR4,
    INTRA_ANGULAR5,
    INTRA_ANGULAR6,
    INTRA_ANGULAR7,
    INTRA_ANGULAR8,
    INTRA_ANGULAR9,
    INTRA_ANGULAR10,
    INTRA_ANGULAR11,
    INTRA_ANGULAR12,
    INTRA_ANGULAR13,
    INTRA_ANGULAR14,
    INTRA_ANGULAR15,
    INTRA_ANGULAR16,
    INTRA_ANGULAR17,
    INTRA_ANGULAR18,
    INTRA_ANGULAR19,
    INTRA_ANGULAR20,
    INTRA_ANGULAR21,
    INTRA_ANGULAR22,
    INTRA_ANGULAR23,
    INTRA_ANGULAR24,
    INTRA_ANGULAR25,
    INTRA_ANGULAR26,
    INTRA_ANGULAR27,
    INTRA_ANGULAR28,
    INTRA_ANGULAR29,
    INTRA_ANGULAR30,
    INTRA_ANGULAR31,
    INTRA_ANGULAR32,
    INTRA_ANGULAR33,
    INTRA_ANGULAR34;

    private static final IntraPredMode[] VALUES = values();

    static IntraPredMode get(int predModeIntra) {
        return VALUES[predModeIntra];
    }
}
