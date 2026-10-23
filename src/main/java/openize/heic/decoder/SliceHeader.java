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
 * The slice segment header elements a slice shares between its independent slice segment and its dependent ones
 * (7.4.7.1), with the values decoding uses: only intra (I) slices are decoded.
 *
 * @param sliceAddrRs the raster address of the slice's first coding tree block, SliceAddrRs
 * @param sliceQpY SliceQpY, 26 + init_qp_minus26 + slice_qp_delta
 */
record SliceHeader(
        int sliceAddrRs,
        boolean sliceSaoLumaFlag,
        boolean sliceSaoChromaFlag,
        int sliceQpY,
        int sliceCbQpOffset,
        int sliceCrQpOffset,
        int sliceActYQpOffset,
        int sliceActCbQpOffset,
        int sliceActCrQpOffset,
        boolean cuChromaQpOffsetEnabledFlag,
        boolean sliceDeblockingFilterDisabledFlag,
        int sliceBetaOffsetDiv2,
        int sliceTcOffsetDiv2,
        boolean sliceLoopFilterAcrossSlicesEnabledFlag) {

    // Clause 9.3.2.2: initType of an I slice
    static final int INIT_TYPE = 0;
}
