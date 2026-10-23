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

import java.util.Objects;


public /*struct*/ class NalHeader
{
    public NalUnitType type;
    public  byte nuhLayerId;
    public  byte nuhTemporalIdPlus1; // > 0
    public NalHeader()
    {
    }

    NalHeader(BitStreamWithNalSupport stream)
    {
        stream.skipBits(1); // 1
        type = NalUnitType.codeToType(stream.read(6));
        nuhLayerId = (byte) stream.read(6);
        nuhTemporalIdPlus1 = (byte) stream.read(3);
    }

    final byte getTemporalId()
    {
        return (byte) ((nuhTemporalIdPlus1 & 0xFF) - 1);
    }

    final boolean isRadlPicture()
    {
        return type == NalUnitType.RADL_N ||          // 6
                type == NalUnitType.RADL_R;
    }            // 7

    final boolean isRaslPicture()
    {
        return type == NalUnitType.RASL_N ||          // 8
                type == NalUnitType.RASL_R;
    }            // 9

    final boolean isIdrPicture()
    {
        return type == NalUnitType.IDR_W_RADL ||      // 19
                type == NalUnitType.IDR_N_LP;
    }          // 20

    final boolean isBlaPicture()
    {
        return type == NalUnitType.BLA_W_LP ||        // 16
                type == NalUnitType.BLA_W_RADL ||      // 17
                type == NalUnitType.BLA_N_LP;
    }          // 18

    final boolean isCraPicture()
    {
        return type == NalUnitType.CRA_NUT;
    }           // 21

    final boolean isRapPicture()
    {
        return isIdrPicture() || isBlaPicture() || isCraPicture();
    }   // 16..21

    final boolean isIrapPicture()
    {
        return type.getUnitCode() >= 16 &&
                type.getUnitCode() <= 23;
    }

    final boolean isReferenceNALU()
    {
        return (((type.getUnitCode() <= NalUnitType.RSV_VCL_R15.getUnitCode()) && (type.getUnitCode() % 2 != 0)) ||
                ((type.getUnitCode() >= NalUnitType.BLA_W_LP.getUnitCode()) && (type.getUnitCode() <= NalUnitType.RSV_IRAP_VCL23.getUnitCode())));
    }

    final boolean isSublayerNonReference()
    {
        switch (type)
        {
            case TRAIL_N:
            case TSA_N:
            case STSA_N:
            case RADL_N:
            case RASL_N:
            case RSV_VCL_N10:
            case RSV_VCL_N12:
            case RSV_VCL_N14:
                return true;
            default:
                return false;
        }
    }

    public void cloneTo(NalHeader that)
    {
        that.type = type;
        that.nuhLayerId = nuhLayerId;
        that.nuhTemporalIdPlus1 = nuhTemporalIdPlus1;
    }

    public NalHeader copy()
    {
        NalHeader struct = new NalHeader();
        cloneTo(struct);
        return struct;
    }

    private boolean equalsByValue(NalHeader that)
    {
        return that.type == type && that.nuhLayerId == nuhLayerId && that.nuhTemporalIdPlus1 == nuhTemporalIdPlus1;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (!(obj instanceof NalHeader))
        {
            return false;
        }

        return equalsByValue((NalHeader) obj);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(type, nuhLayerId, nuhTemporalIdPlus1);
    }
}

