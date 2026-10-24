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

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorShuffle;
import jdk.incubator.vector.VectorSpecies;

/**
 * The {@link Kernels} with the Vector API: 4 doubles in 256-bit vectors, 4 ints in 128-bit ones. Only
 * {@link Kernels#load} refers to this class, by name, when the {@code jdk.incubator.vector} module is there.
 *
 * <p>The JIT keeps a vector in registers only in code it compiles in one piece: a vector passed to a method it does not
 * inline, or met in code it falls back to after a trap, is boxed, which costs more than the vector saves. A kernel
 * keeps its steps small and free of branches on its data. Every int vector is of one shape: the API's methods share
 * their type profiles across kernels, and a store that saw two vector classes is not compiled into a vector store.
 */
final class VectorKernels implements Kernels {
    private static final VectorSpecies<Byte> BYTES_64 = ByteVector.SPECIES_64;
    private static final VectorSpecies<Integer> INTS_128 = IntVector.SPECIES_128;
    private static final VectorSpecies<Double> DOUBLES_256 = DoubleVector.SPECIES_256;
    private static final VectorShuffle<Integer> REVERSED = VectorShuffle.fromValues(INTS_128, 3, 2, 1, 0);
    private static final int PIXELS = 4;
    // a vector load reads 8 bytes, of which a step uses 4 luma or up to 3 chroma
    private static final int LOADED_BYTES = 8;

    // the chroma sample of each of 4 pixels from an even column, from the one of the first, when two pixels share one
    private static final VectorShuffle<Integer> CHROMA_PAIRS = VectorShuffle.fromValues(INTS_128, 0, 0, 1, 1);

    VectorKernels() {}

    /** Whether the processor has the 256-bit vectors the kernels are written for: otherwise they would be emulated. */
    static boolean isSupported() {
        return VectorShape.preferredShape().vectorBitSize() >= 256;
    }

    // The terms of YuvConverter.ChannelTables are computed with the same double expressions instead of looked up, a
    // full range sample with an offset of 0 and a scale of 1, which leave it unchanged. A loop of its own per chroma
    // layout, and no branch in it: the JIT compiles a branch it has not seen taken as a trap, and the code it falls
    // back to when a later image takes it boxes every vector
    @Override
    public int convertRgb8Row(
            byte[] lumaPlane,
            int lumaRow,
            byte[] cbPlane,
            byte[] crPlane,
            int chromaRow,
            int log2SubWidthC,
            int left,
            int right,
            int[] destination,
            int destinationRow,
            YuvConverter.ChannelTables tables) {
        return log2SubWidthC == 1
                ? convertSharingChroma(
                        lumaPlane,
                        lumaRow,
                        cbPlane,
                        crPlane,
                        chromaRow,
                        left,
                        right,
                        destination,
                        destinationRow,
                        tables)
                : convertOwnChroma(
                        lumaPlane,
                        lumaRow,
                        cbPlane,
                        crPlane,
                        chromaRow,
                        left,
                        right,
                        destination,
                        destinationRow,
                        tables);
    }

    // two columns share a chroma sample, left is even
    private static int convertSharingChroma(
            byte[] lumaPlane,
            int lumaRow,
            byte[] cbPlane,
            byte[] crPlane,
            int chromaRow,
            int left,
            int right,
            int[] destination,
            int destinationRow,
            YuvConverter.ChannelTables tables) {
        double lumaOffset = tables.fullRange() ? 0 : tables.lumaOffset();
        double lumaRangeScale = tables.fullRange() ? 1 : YuvConverter.TV_RANGE_LUMA_SCALE;
        double chromaRangeScale = tables.fullRange() ? 1 : YuvConverter.TV_RANGE_CHROMA_SCALE;
        double lumaBitScale = tables.lumaBitScale();
        int chromaHalfRange = tables.chromaHalfRange();
        double chromaBitScale = tables.chromaBitScale();
        double crToRed = tables.crToRedCoefficient();
        double crToGreen = tables.crToGreenCoefficient();
        double cbToGreen = tables.cbToGreenCoefficient();
        double cbToBlue = tables.cbToBlueCoefficient();
        int col = left;
        for (; col + PIXELS <= right; col += PIXELS) {
            int lumaIndex = lumaRow + col;
            int chromaIndex = chromaRow + (col >> 1);
            if (lumaIndex + LOADED_BYTES > lumaPlane.length || chromaIndex + LOADED_BYTES > cbPlane.length) {
                break;
            }
            IntVector luma = ((IntVector) ByteVector.fromArray(BYTES_64, lumaPlane, lumaIndex)
                            .convertShape(VectorOperators.B2I, INTS_128, 0))
                    .and(0xFF);
            IntVector cb = ((IntVector) ByteVector.fromArray(BYTES_64, cbPlane, chromaIndex)
                            .convertShape(VectorOperators.B2I, INTS_128, 0))
                    .and(0xFF)
                    .sub(chromaHalfRange)
                    .rearrange(CHROMA_PAIRS);
            IntVector cr = ((IntVector) ByteVector.fromArray(BYTES_64, crPlane, chromaIndex)
                            .convertShape(VectorOperators.B2I, INTS_128, 0))
                    .and(0xFF)
                    .sub(chromaHalfRange)
                    .rearrange(CHROMA_PAIRS);

            DoubleVector scaledLuma = (DoubleVector) luma.convertShape(VectorOperators.I2D, DOUBLES_256, 0);
            DoubleVector scaledCb = (DoubleVector) cb.convertShape(VectorOperators.I2D, DOUBLES_256, 0);
            DoubleVector scaledCr = (DoubleVector) cr.convertShape(VectorOperators.I2D, DOUBLES_256, 0);
            scaledLuma = scaledLuma.sub(lumaOffset).mul(lumaRangeScale).mul(lumaBitScale);
            scaledCb = scaledCb.mul(chromaRangeScale).mul(chromaBitScale);
            scaledCr = scaledCr.mul(chromaRangeScale).mul(chromaBitScale);

            // (int) channel clipped to 0..255, as YuvConverter.to8Bits
            IntVector red = ((IntVector)
                            scaledLuma.add(scaledCr.mul(crToRed)).convertShape(VectorOperators.D2I, INTS_128, 0))
                    .max(0)
                    .min(255);
            IntVector green = ((IntVector) scaledLuma
                            .sub(scaledCr.mul(crToGreen))
                            .sub(scaledCb.mul(cbToGreen))
                            .convertShape(VectorOperators.D2I, INTS_128, 0))
                    .max(0)
                    .min(255);
            IntVector blue = ((IntVector)
                            scaledLuma.add(scaledCb.mul(cbToBlue)).convertShape(VectorOperators.D2I, INTS_128, 0))
                    .max(0)
                    .min(255);
            red.lanewise(VectorOperators.LSHL, 16)
                    .or(green.lanewise(VectorOperators.LSHL, 8))
                    .or(blue)
                    .or(0xFF000000)
                    .intoArray(destination, destinationRow + col);
        }
        return col;
    }

    // a chroma sample per column, the same steps without the shuffle
    private static int convertOwnChroma(
            byte[] lumaPlane,
            int lumaRow,
            byte[] cbPlane,
            byte[] crPlane,
            int chromaRow,
            int left,
            int right,
            int[] destination,
            int destinationRow,
            YuvConverter.ChannelTables tables) {
        double lumaOffset = tables.fullRange() ? 0 : tables.lumaOffset();
        double lumaRangeScale = tables.fullRange() ? 1 : YuvConverter.TV_RANGE_LUMA_SCALE;
        double chromaRangeScale = tables.fullRange() ? 1 : YuvConverter.TV_RANGE_CHROMA_SCALE;
        double lumaBitScale = tables.lumaBitScale();
        int chromaHalfRange = tables.chromaHalfRange();
        double chromaBitScale = tables.chromaBitScale();
        double crToRed = tables.crToRedCoefficient();
        double crToGreen = tables.crToGreenCoefficient();
        double cbToGreen = tables.cbToGreenCoefficient();
        double cbToBlue = tables.cbToBlueCoefficient();
        int col = left;
        for (; col + PIXELS <= right; col += PIXELS) {
            int lumaIndex = lumaRow + col;
            int chromaIndex = chromaRow + col;
            if (lumaIndex + LOADED_BYTES > lumaPlane.length || chromaIndex + LOADED_BYTES > cbPlane.length) {
                break;
            }
            IntVector luma = ((IntVector) ByteVector.fromArray(BYTES_64, lumaPlane, lumaIndex)
                            .convertShape(VectorOperators.B2I, INTS_128, 0))
                    .and(0xFF);
            IntVector cb = ((IntVector) ByteVector.fromArray(BYTES_64, cbPlane, chromaIndex)
                            .convertShape(VectorOperators.B2I, INTS_128, 0))
                    .and(0xFF)
                    .sub(chromaHalfRange);
            IntVector cr = ((IntVector) ByteVector.fromArray(BYTES_64, crPlane, chromaIndex)
                            .convertShape(VectorOperators.B2I, INTS_128, 0))
                    .and(0xFF)
                    .sub(chromaHalfRange);

            DoubleVector scaledLuma = (DoubleVector) luma.convertShape(VectorOperators.I2D, DOUBLES_256, 0);
            DoubleVector scaledCb = (DoubleVector) cb.convertShape(VectorOperators.I2D, DOUBLES_256, 0);
            DoubleVector scaledCr = (DoubleVector) cr.convertShape(VectorOperators.I2D, DOUBLES_256, 0);
            scaledLuma = scaledLuma.sub(lumaOffset).mul(lumaRangeScale).mul(lumaBitScale);
            scaledCb = scaledCb.mul(chromaRangeScale).mul(chromaBitScale);
            scaledCr = scaledCr.mul(chromaRangeScale).mul(chromaBitScale);

            // (int) channel clipped to 0..255, as YuvConverter.to8Bits
            IntVector red = ((IntVector)
                            scaledLuma.add(scaledCr.mul(crToRed)).convertShape(VectorOperators.D2I, INTS_128, 0))
                    .max(0)
                    .min(255);
            IntVector green = ((IntVector) scaledLuma
                            .sub(scaledCr.mul(crToGreen))
                            .sub(scaledCb.mul(cbToGreen))
                            .convertShape(VectorOperators.D2I, INTS_128, 0))
                    .max(0)
                    .min(255);
            IntVector blue = ((IntVector)
                            scaledLuma.add(scaledCb.mul(cbToBlue)).convertShape(VectorOperators.D2I, INTS_128, 0))
                    .max(0)
                    .min(255);
            red.lanewise(VectorOperators.LSHL, 16)
                    .or(green.lanewise(VectorOperators.LSHL, 8))
                    .or(blue)
                    .or(0xFF000000)
                    .intoArray(destination, destinationRow + col);
        }
        return col;
    }

    // The odd sums stay in registers, 8 or 16 outputs, instead of a scratch array, and the second half of the outputs,
    // in reverse order, is a lane shuffle. A zero coefficient adds 0: a branch on it would make the JIT box the sums
    @Override
    public void combineDctHalves(int[] source, int offset, int step, int last, int nTbS, int[] output) {
        int rowStep = InverseTransform.MAX_TRANSFORM_SIZE / nTbS;
        if (nTbS == 16) {
            IntVector odd0 = IntVector.zero(INTS_128);
            IntVector odd1 = IntVector.zero(INTS_128);
            for (int j = 1; j <= last; j += 2) {
                int[] basisFunction = InverseTransform.DCT_MATRIX[j * rowStep];
                int coefficient = source[offset + j * step];
                odd0 = odd0.add(IntVector.fromArray(INTS_128, basisFunction, 0).mul(coefficient));
                odd1 = odd1.add(IntVector.fromArray(INTS_128, basisFunction, 4).mul(coefficient));
            }
            IntVector even0 = IntVector.fromArray(INTS_128, output, 0);
            IntVector even1 = IntVector.fromArray(INTS_128, output, 4);
            even1.sub(odd1).rearrange(REVERSED).intoArray(output, 8);
            even0.sub(odd0).rearrange(REVERSED).intoArray(output, 12);
            even0.add(odd0).intoArray(output, 0);
            even1.add(odd1).intoArray(output, 4);
            return;
        }
        IntVector odd0 = IntVector.zero(INTS_128);
        IntVector odd1 = IntVector.zero(INTS_128);
        IntVector odd2 = IntVector.zero(INTS_128);
        IntVector odd3 = IntVector.zero(INTS_128);
        for (int j = 1; j <= last; j += 2) {
            int[] basisFunction = InverseTransform.DCT_MATRIX[j * rowStep];
            int coefficient = source[offset + j * step];
            odd0 = odd0.add(IntVector.fromArray(INTS_128, basisFunction, 0).mul(coefficient));
            odd1 = odd1.add(IntVector.fromArray(INTS_128, basisFunction, 4).mul(coefficient));
            odd2 = odd2.add(IntVector.fromArray(INTS_128, basisFunction, 8).mul(coefficient));
            odd3 = odd3.add(IntVector.fromArray(INTS_128, basisFunction, 12).mul(coefficient));
        }
        IntVector even0 = IntVector.fromArray(INTS_128, output, 0);
        IntVector even1 = IntVector.fromArray(INTS_128, output, 4);
        IntVector even2 = IntVector.fromArray(INTS_128, output, 8);
        IntVector even3 = IntVector.fromArray(INTS_128, output, 12);
        even3.sub(odd3).rearrange(REVERSED).intoArray(output, 16);
        even2.sub(odd2).rearrange(REVERSED).intoArray(output, 20);
        even1.sub(odd1).rearrange(REVERSED).intoArray(output, 24);
        even0.sub(odd0).rearrange(REVERSED).intoArray(output, 28);
        even0.add(odd0).intoArray(output, 0);
        even1.add(odd1).intoArray(output, 4);
        even2.add(odd2).intoArray(output, 8);
        even3.add(odd3).intoArray(output, 12);
    }
}
