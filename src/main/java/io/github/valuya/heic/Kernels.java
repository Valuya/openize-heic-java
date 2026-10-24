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

import org.jspecify.annotations.Nullable;

/**
 * Hot loops of the decoder written with the Vector API, in {@code VectorKernels}. The API is still incubating
 * ({@code jdk.incubator.vector}): the kernels are used only when the JVM runs with
 * {@code --add-modules jdk.incubator.vector}, vectors of at least 256 bits are available, and the system property
 * {@code io.github.valuya.heic.vector} is not {@code false}. Otherwise {@link #VECTOR} is null and the decoder runs
 * its scalar loops. A kernel computes exactly what the loop it replaces computes, bit for bit.
 *
 * <p>{@code VectorKernels} is loaded by name, so that no other class refers to the incubating module.
 */
interface Kernels {
    /** The vector kernels, or null for the scalar loops. */
    @Nullable
    Kernels VECTOR = load();

    /**
     * Converts the 8-bit colour samples of the pixels {@code left} to {@code right} (exclusive) of a row to opaque
     * ARGB, at {@code destination[destinationRow + col]}, as {@link YuvConverter} does with {@code tables}. When two
     * columns share a chroma sample, {@code left} is even.
     *
     * @param lumaRow the index of the row's first luma sample
     * @param chromaRow the index of the first chroma sample of the chroma row the row reads
     * @return the first pixel not converted: the scalar loop converts the rest of the row
     */
    int convertRgb8Row(
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
            YuvConverter.ChannelTables tables);

    /**
     * Adds the odd inputs' sums of a 16- or 32-point inverse DCT to the even inputs' outputs in
     * {@code output[0..nTbS / 2)}, giving the {@code nTbS} outputs, as {@code InverseTransform.combine} does.
     */
    void combineDctHalves(int[] source, int offset, int step, int last, int nTbS, int[] output);

    private static @Nullable Kernels load() {
        if (!Boolean.parseBoolean(System.getProperty("io.github.valuya.heic.vector", "true"))
                || ModuleLayer.boot().findModule("jdk.incubator.vector").isEmpty()) {
            return null;
        }
        try {
            Class<?> kernels = Class.forName("io.github.valuya.heic.VectorKernels");
            boolean supported =
                    (boolean) kernels.getDeclaredMethod("isSupported").invoke(null);
            return supported ? (Kernels) kernels.getDeclaredConstructor().newInstance() : null;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The vector kernels do not load", exception);
        }
    }
}
