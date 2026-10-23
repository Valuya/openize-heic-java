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
 * The CABAC context tables of the syntax elements an intra slice codes with contexts (Table 9-4), each with the
 * init_value of its contexts for initType 0 (Tables 9-5 to 9-37). The contexts of every table are laid out one after
 * the other, from {@link #offset()}.
 */
enum ContextTable {
    SAO_MERGE_FLAG(153),
    SAO_TYPE_IDX(200),
    SPLIT_CU_FLAG(139, 141, 157),
    CU_TRANSQUANT_BYPASS_FLAG(154),
    PALETTE_MODE_FLAG(154),
    PART_MODE(184),
    PREV_INTRA_LUMA_PRED_FLAG(184),
    INTRA_CHROMA_PRED_MODE(63),
    SPLIT_TRANSFORM_FLAG(153, 138, 138),
    CBF_LUMA(111, 141),
    CBF_CHROMA(94, 138, 182, 154, 154),
    TU_RESIDUAL_ACT_FLAG(154),
    LOG2_RES_SCALE_ABS_PLUS1(154, 154, 154, 154, 154, 154, 154, 154),
    RES_SCALE_SIGN_FLAG(154, 154),
    TRANSFORM_SKIP_FLAG(139, 139),
    LAST_SIG_COEFF_X_PREFIX(110, 110, 124, 125, 140, 153, 125, 127, 140, 109, 111, 143, 127, 111, 79, 108, 123, 63),
    LAST_SIG_COEFF_Y_PREFIX(110, 110, 124, 125, 140, 153, 125, 127, 140, 109, 111, 143, 127, 111, 79, 108, 123, 63),
    CODED_SUB_BLOCK_FLAG(91, 171, 134, 141),
    SIG_COEFF_FLAG(
            111,
            111,
            125,
            110,
            110,
            94,
            124,
            108,
            124,
            107,
            125,
            141,
            179,
            153,
            125,
            107,
            125,
            141,
            179,
            153,
            125,
            107,
            125,
            141,
            179,
            153,
            125,
            140,
            139,
            182,
            182,
            152,
            136,
            152,
            136,
            153,
            136,
            139,
            111,
            136,
            139,
            111,
            // transform_skip_context_enabled_flag: luma, chroma
            141,
            111),
    COEFF_ABS_LEVEL_GREATER1_FLAG(
            140, 92, 137, 138, 140, 152, 138, 139, 153, 74, 149, 92, 139, 107, 122, 152, 140, 179, 166, 182, 140, 227,
            122, 197),
    COEFF_ABS_LEVEL_GREATER2_FLAG(138, 153, 136, 167, 152, 152),
    CU_QP_DELTA_ABS(154, 154),
    CU_CHROMA_QP_OFFSET_FLAG(154),
    CU_CHROMA_QP_OFFSET_IDX(154);

    /** The number of contexts of all tables. */
    static final int CONTEXT_COUNT;

    static {
        int offset = 0;
        for (ContextTable table : values()) {
            table.offset = offset;
            offset += table.initValues.length;
        }
        CONTEXT_COUNT = offset;
    }

    private final int[] initValues;
    private int offset;

    ContextTable(int... initValues) {
        this.initValues = initValues;
    }

    /** The index of the table's first context among the contexts of all tables. */
    int offset() {
        return offset;
    }

    int contextCount() {
        return initValues.length;
    }

    int initValue(int contextIndex) {
        return initValues[contextIndex];
    }
}
