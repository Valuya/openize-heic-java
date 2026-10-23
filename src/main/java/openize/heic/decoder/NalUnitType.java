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
 * nal_unit_type (Table 7-1).
 */
enum NalUnitType {
    // coded slice segments of trailing, temporal sub-layer access and step-wise temporal sub-layer access pictures
    TRAIL_N(0),
    TRAIL_R(1),
    TSA_N(2),
    TSA_R(3),
    STSA_N(4),
    STSA_R(5),

    // coded slice segments of random access decodable and skipped leading pictures
    RADL_N(6),
    RADL_R(7),
    RASL_N(8),
    RASL_R(9),

    // reserved non-IRAP VCL types, sub-layer non-reference (even) and reference (odd)
    RSV_VCL_N10(10),
    RSV_VCL_R11(11),
    RSV_VCL_N12(12),
    RSV_VCL_R13(13),
    RSV_VCL_N14(14),
    RSV_VCL_R15(15),

    // coded slice segments of IRAP pictures: broken link access, instantaneous decoding refresh, clean random access
    BLA_W_LP(16),
    BLA_W_RADL(17),
    BLA_N_LP(18),
    IDR_W_RADL(19),
    IDR_N_LP(20),
    CRA_NUT(21),

    // reserved IRAP VCL types
    RSV_IRAP_VCL22(22),
    RSV_IRAP_VCL23(23),

    // reserved non-IRAP VCL types
    RSV_VCL24(24),
    RSV_VCL25(25),
    RSV_VCL26(26),
    RSV_VCL27(27),
    RSV_VCL28(28),
    RSV_VCL29(29),
    RSV_VCL30(30),
    RSV_VCL31(31),

    // non-VCL: parameter sets, access unit delimiter, end of sequence and bitstream, filler data, SEI
    VPS_NUT(32),
    SPS_NUT(33),
    PPS_NUT(34),
    AUD_NUT(35),
    EOS_NUT(36),
    EOB_NUT(37),
    FD_NUT(38),
    PREFIX_SEI_NUT(39),
    SUFFIX_SEI_NUT(40),

    // reserved non-VCL types
    RSV_NVCL41(41),
    RSV_NVCL42(42),
    RSV_NVCL43(43),
    RSV_NVCL44(44),
    RSV_NVCL45(45),
    RSV_NVCL46(46),
    RSV_NVCL47(47),

    // unspecified non-VCL types
    UNSPEC48(48),
    UNSPEC49(49),
    UNSPEC50(50),
    UNSPEC51(51),
    UNSPEC52(52),
    UNSPEC53(53),
    UNSPEC54(54),
    UNSPEC55(55),
    UNSPEC56(56),
    UNSPEC57(57),
    UNSPEC58(58),
    UNSPEC59(59),
    UNSPEC60(60),
    UNSPEC61(61),
    UNSPEC62(62),
    UNSPEC63(63);

    private static final NalUnitType[] TYPE_BY_CODE = typeByCode();

    private final int unitCode;

    NalUnitType(int unitCode) {
        this.unitCode = unitCode;
    }

    static NalUnitType codeToType(int unitCode) {
        if (unitCode < 0 || unitCode >= TYPE_BY_CODE.length) {
            throw new HeicFormatException("Unknown NAL unit type %d".formatted(unitCode));
        }
        return TYPE_BY_CODE[unitCode];
    }

    int getUnitCode() {
        return unitCode;
    }

    /**
     * Whether the unit is a coded slice segment (VCL NAL unit, types 0 to 31).
     */
    boolean isVcl() {
        return unitCode < VPS_NUT.unitCode;
    }

    @Override
    public String toString() {
        return "%s(%d)".formatted(name(), unitCode);
    }

    private static NalUnitType[] typeByCode() {
        NalUnitType[] types = values();
        NalUnitType[] typeByCode = new NalUnitType[types.length];
        for (NalUnitType type : types) {
            typeByCode[type.unitCode] = type;
        }
        return typeByCode;
    }
}
