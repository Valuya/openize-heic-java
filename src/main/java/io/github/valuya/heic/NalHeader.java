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
 * nal_unit_header( ) (7.3.1.2).
 */
final class NalHeader {
    final NalUnitType type;
    final int nuhTemporalIdPlus1;

    NalHeader(BitStreamWithNalSupport stream) {
        stream.skipBits(1); // forbidden_zero_bit
        type = NalUnitType.codeToType(stream.read(6));
        stream.skipBits(6); // nuh_layer_id: NalUnit skips the units of layers other than the base one
        nuhTemporalIdPlus1 = stream.read(3);
    }

    boolean isRadlPicture() {
        return type == NalUnitType.RADL_N || type == NalUnitType.RADL_R;
    }

    boolean isRaslPicture() {
        return type == NalUnitType.RASL_N || type == NalUnitType.RASL_R;
    }

    boolean isIdrPicture() {
        return type == NalUnitType.IDR_W_RADL || type == NalUnitType.IDR_N_LP;
    }

    boolean isBlaPicture() {
        return type == NalUnitType.BLA_W_LP || type == NalUnitType.BLA_W_RADL || type == NalUnitType.BLA_N_LP;
    }

    /**
     * An IDR, BLA or CRA picture: NAL unit types 16 to 21.
     */
    boolean isRapPicture() {
        return isIdrPicture() || isBlaPicture() || type == NalUnitType.CRA_NUT;
    }

    /**
     * An IRAP picture: NAL unit types 16 to 23, the reserved ones included.
     */
    boolean isIrapPicture() {
        return type.getUnitCode() >= NalUnitType.BLA_W_LP.getUnitCode()
                && type.getUnitCode() <= NalUnitType.RSV_IRAP_VCL23.getUnitCode();
    }

    /**
     * A sub-layer non-reference picture: the even NAL unit types up to 14 (Table 7-1).
     */
    boolean isSublayerNonReference() {
        return switch (type) {
            case TRAIL_N, TSA_N, STSA_N, RADL_N, RASL_N, RSV_VCL_N10, RSV_VCL_N12, RSV_VCL_N14 -> true;
            default -> false;
        };
    }
}
