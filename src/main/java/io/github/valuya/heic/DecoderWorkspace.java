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

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jspecify.annotations.Nullable;

/**
 * The memory a decoder thread works in: the scratch buffers of residual coding and intra prediction, and the arrays of
 * the picture being decoded. The tiles of a grid are pictures of the same size, so the arrays of one tile are handed
 * to the next, cleared, instead of being allocated again. A workspace serves one decode at a time.
 */
final class DecoderWorkspace {
    // the arrays a picture and its in-loop filters take, by the slot each one is kept in
    static final int LUMA_PLANE = 0;
    static final int CB_PLANE = 1;
    static final int CR_PLANE = 2;
    static final int CT_DEPTH = 3;
    static final int LOG2_CB_SIZE = 4;
    static final int QP_Y = 5;
    static final int PCM_FLAG = 6;
    static final int INTRA_PRED_MODE_Y = 7;
    static final int INTRA_PRED_MODE_C = 8;
    static final int INTRA_CHROMA_PRED_MODE = 9;
    static final int CBF_CB = 10;
    static final int CBF_CR = 11;
    static final int CBF_LUMA = 12;
    static final int BLOCK_FLAGS = 13;
    static final int SLICE_ADDR_RS = 14;
    static final int SLICE_SEGMENT_INDEX = 15;
    static final int SAO_PARAMETERS = 16;
    // one per component, from these
    static final int DEBLOCKED_BAND = 17;
    static final int DEBLOCKED_LINE_ABOVE = 20;
    static final int SLICE_DATA = 23;
    static final int CONVERTED_ROW = 24;
    private static final int SLOT_COUNT = 25;

    final ResidualBuffers residualBuffers = new ResidualBuffers();
    final IntraPredictionBuffers intraPredictionBuffers = new IntraPredictionBuffers();
    // the candidate modes of 8.4.2 and prev_intra_luma_pred_flag of the four prediction blocks of a coding unit
    final int[] candModeList = new int[3];
    final boolean[] prevIntraLumaPredFlags = new boolean[4];
    // the conversion tables of the last picture converted to RGB
    YuvConverter.@Nullable ChannelTables channelTables;

    private final Object[] arrays = new Object[SLOT_COUNT];

    /** A zeroed array of {@code length} bytes, the one of the slot when it has that length. */
    byte[] bytes(int slot, int length) {
        if (arrays[slot] instanceof byte[] kept && kept.length == length) {
            Arrays.fill(kept, (byte) 0);
            return kept;
        }
        byte[] allocated = new byte[length];
        arrays[slot] = allocated;
        return allocated;
    }

    /** A zeroed array of {@code length} shorts, the one of the slot when it has that length. */
    short[] shorts(int slot, int length) {
        if (arrays[slot] instanceof short[] kept && kept.length == length) {
            Arrays.fill(kept, (short) 0);
            return kept;
        }
        short[] allocated = new short[length];
        arrays[slot] = allocated;
        return allocated;
    }

    /** A zeroed array of {@code length} ints, the one of the slot when it has that length. */
    int[] ints(int slot, int length) {
        if (arrays[slot] instanceof int[] kept && kept.length == length) {
            Arrays.fill(kept, 0);
            return kept;
        }
        int[] allocated = new int[length];
        arrays[slot] = allocated;
        return allocated;
    }

    /** An array of at least {@code length} bytes, not cleared: the one of the slot when it is long enough. */
    byte[] byteBuffer(int slot, int length) {
        if (arrays[slot] instanceof byte[] kept && kept.length >= length) {
            return kept;
        }
        byte[] allocated = new byte[length];
        arrays[slot] = allocated;
        return allocated;
    }

    /** An array of at least {@code length} ints, not cleared: the one of the slot when it is long enough. */
    int[] intBuffer(int slot, int length) {
        if (arrays[slot] instanceof int[] kept && kept.length >= length) {
            return kept;
        }
        int[] allocated = new int[length];
        arrays[slot] = allocated;
        return allocated;
    }

    /**
     * The workspaces of one decode: a tile takes one when it starts and gives it back when its pixels are written, so
     * there are no more than the tiles decoded at the same time, and they go with the decode.
     */
    static final class Pool {
        private final ConcurrentLinkedQueue<DecoderWorkspace> idle = new ConcurrentLinkedQueue<>();

        DecoderWorkspace take() {
            DecoderWorkspace workspace = idle.poll();
            return workspace != null ? workspace : new DecoderWorkspace();
        }

        void giveBack(DecoderWorkspace workspace) {
            idle.add(workspace);
        }
    }
}
