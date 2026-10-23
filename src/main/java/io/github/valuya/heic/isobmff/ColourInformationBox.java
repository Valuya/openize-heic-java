/*
 * FileFormat.IsoBmff
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of FileFormat.IsoBmff.
 *
 * FileFormat.IsoBmff is available under MIT license, which is
 * available along with FileFormat.IsoBmff sources.
 */

package io.github.valuya.heic.isobmff;

import io.github.valuya.heic.isobmff.io.BitStreamReader;

/**
 * The colour space of an image (ISO/IEC 14496-12, 12.1.5). It takes precedence over the colour information of the
 * coded image. Only the coded colour parameters of an {@code nclx} box are read, not ICC profiles.
 */
public class ColourInformationBox extends Box {
    private static final long NCLX = 0x6e636c78L;

    private final long colourType;
    private final int colourPrimaries;
    private final int transferCharacteristics;
    private final int matrixCoefficients;
    private final boolean fullRange;

    ColourInformationBox(BitStreamReader stream, BoxHeader header) {
        super(header);

        colourType = stream.readLong(32);
        if (colourType == NCLX) {
            colourPrimaries = stream.read(16);
            transferCharacteristics = stream.read(16);
            matrixCoefficients = stream.read(16);
            fullRange = stream.readFlag();
            stream.skipBits(7); // reserved
        } else {
            colourPrimaries = 0;
            transferCharacteristics = 0;
            matrixCoefficients = 0;
            fullRange = false;
        }
    }

    /**
     * The type of colour information, a four-character code: {@code nclx} for coded colour parameters, {@code rICC}
     * or {@code prof} for an ICC profile.
     */
    public final String getColourType() {
        return fourCharacterCodeText(colourType);
    }

    /**
     * Whether the box describes the samples with colour primaries, transfer characteristics and matrix
     * coefficients ({@code nclx}), rather than with an ICC profile.
     */
    public final boolean isNclx() {
        return colourType == NCLX;
    }

    /**
     * The colour primaries, coded as in ISO/IEC 23091-2; 0 unless the type is {@code nclx}.
     */
    public final int getColourPrimaries() {
        return colourPrimaries;
    }

    /**
     * The transfer characteristics, coded as in ISO/IEC 23091-2; 0 unless the type is {@code nclx}.
     */
    public final int getTransferCharacteristics() {
        return transferCharacteristics;
    }

    /**
     * The matrix coefficients, coded as in ISO/IEC 23091-2; 0 unless the type is {@code nclx}.
     */
    public final int getMatrixCoefficients() {
        return matrixCoefficients;
    }

    /**
     * Whether the samples use the full range of their bit depth rather than a video range; false unless the type is
     * {@code nclx}.
     */
    public final boolean isFullRange() {
        return fullRange;
    }
}
