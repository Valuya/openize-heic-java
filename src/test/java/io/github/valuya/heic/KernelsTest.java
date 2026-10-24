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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Each vector kernel against the scalar loop it replaces, bit for bit. The kernels load only in a JVM started with
 * {@code --add-modules jdk.incubator.vector}, as the build's second test run is: {@code heic.expectVectorKernels}
 * makes their absence a failure there.
 */
class KernelsTest {
    // room for the 8-byte loads of the last vector step
    private static final int PADDING = 8;

    private Kernels kernels;

    @BeforeEach
    void requireVectorKernels() {
        if (Boolean.getBoolean("heic.expectVectorKernels")) {
            assertThat(Kernels.VECTOR)
                    .as("the vector kernels, with jdk.incubator.vector")
                    .isNotNull();
        }
        Kernels vector = Kernels.VECTOR;
        assumeTrue(vector != null, "the vector kernels need --add-modules jdk.incubator.vector");
        kernels = vector;
    }

    static Stream<Arguments> colourDescriptions() {
        // BT.601 full range (unspecified matrix), BT.709 limited range, BT.2020 limited range from its primaries
        return Stream.of(
                Arguments.of(new YuvConverter.ColourDescription(2, 2, true)),
                Arguments.of(new YuvConverter.ColourDescription(1, 1, false)),
                Arguments.of(new YuvConverter.ColourDescription(9, 12, false)));
    }

    // every luma value with every pair of chroma values, a chroma pair per row
    @ParameterizedTest
    @MethodSource("colourDescriptions")
    void convertsEverySampleValueAsTheScalarLoop(YuvConverter.ColourDescription colour) {
        YuvConverter.ChannelTables tables = YuvConverter.ChannelTables.of8Bit(colour);
        byte[] luma = new byte[256 + PADDING];
        for (int value = 0; value < 256; value++) {
            luma[value] = (byte) value;
        }
        int[] vectorPixels = new int[256];
        int[] scalarPixels = new int[256];
        for (int log2SubWidthC = 0; log2SubWidthC <= 1; log2SubWidthC++) {
            int chromaLength = (256 >> log2SubWidthC) + PADDING;
            byte[] cb = new byte[chromaLength];
            byte[] cr = new byte[chromaLength];
            for (int cbValue = 0; cbValue < 256; cbValue++) {
                Arrays.fill(cb, (byte) cbValue);
                for (int crValue = 0; crValue < 256; crValue++) {
                    Arrays.fill(cr, (byte) crValue);
                    int converted =
                            kernels.convertRgb8Row(luma, 0, cb, cr, 0, log2SubWidthC, 0, 256, vectorPixels, 0, tables);
                    YuvConverter.convertPixels(
                            luma, 0, cb, cr, 0, log2SubWidthC, tables, 0, converted, scalarPixels, 0, 1);
                    assertThat(converted).isEqualTo(256);
                    if (!Arrays.equals(vectorPixels, scalarPixels)) {
                        assertThat(vectorPixels)
                                .as("cb %d, cr %d, chroma shift %d", cbValue, crValue, log2SubWidthC)
                                .containsExactly(scalarPixels);
                    }
                }
            }
        }
    }

    // rows of random samples, where the chroma changes from pixel to pixel, from every start and length
    @ParameterizedTest
    @MethodSource("colourDescriptions")
    void convertsRandomRowsAsTheScalarLoop(YuvConverter.ColourDescription colour) {
        YuvConverter.ChannelTables tables = YuvConverter.ChannelTables.of8Bit(colour);
        SplittableRandom random = new SplittableRandom(1);
        int width = 64;
        for (int log2SubWidthC = 0; log2SubWidthC <= 1; log2SubWidthC++) {
            byte[] luma = randomBytes(random, 2 * width + PADDING);
            byte[] cb = randomBytes(random, 2 * (width >> log2SubWidthC) + PADDING);
            byte[] cr = randomBytes(random, 2 * (width >> log2SubWidthC) + PADDING);
            for (int left = 0; left < width; left += 1 + log2SubWidthC) {
                for (int right = left; right <= width; right++) {
                    // the second row of the planes, into the middle of the output
                    int[] vectorPixels = new int[3 * width];
                    int[] scalarPixels = new int[3 * width];
                    int converted = kernels.convertRgb8Row(
                            luma,
                            width,
                            cb,
                            cr,
                            width >> log2SubWidthC,
                            log2SubWidthC,
                            left,
                            right,
                            vectorPixels,
                            width,
                            tables);
                    YuvConverter.convertPixels(
                            luma,
                            width,
                            cb,
                            cr,
                            width >> log2SubWidthC,
                            log2SubWidthC,
                            tables,
                            left,
                            converted,
                            scalarPixels,
                            width,
                            1);
                    assertThat(converted).isBetween(left, right);
                    assertThat(vectorPixels).as("pixels %d to %d", left, right).containsExactly(scalarPixels);
                }
            }
        }
    }

    @Test
    void combinesDctHalvesAsTheScalarLoop() {
        SplittableRandom random = new SplittableRandom(2);
        int[] source = new int[64 * 32];
        int[] vectorOutput = new int[32];
        int[] scalarOutput = new int[32];
        int[] scratch = new int[32];
        for (int iteration = 0; iteration < 20_000; iteration++) {
            int nTbS = random.nextBoolean() ? 16 : 32;
            int step = 1 + random.nextInt(32);
            int offset = random.nextInt(32);
            int last = random.nextInt(nTbS);
            for (int i = 0; i < source.length; i++) {
                source[i] = random.nextInt(-32768, 32768);
            }
            for (int i = 0; i < nTbS / 2; i++) {
                vectorOutput[i] = random.nextInt(-(1 << 22), 1 << 22);
            }
            System.arraycopy(vectorOutput, 0, scalarOutput, 0, nTbS / 2);

            kernels.combineDctHalves(source, offset, step, last, nTbS, vectorOutput);
            InverseTransform.combineScalar(source, offset, step, last, nTbS, scalarOutput, scratch);

            assertThat(Arrays.copyOf(vectorOutput, nTbS)).containsExactly(Arrays.copyOf(scalarOutput, nTbS));
        }
    }

    private static byte[] randomBytes(SplittableRandom random, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) random.nextInt(256);
        }
        return bytes;
    }
}
