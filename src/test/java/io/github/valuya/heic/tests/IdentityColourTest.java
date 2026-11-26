package io.github.valuya.heic.tests;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.valuya.heic.HeicImage;
import io.github.valuya.heic.PixelFormat;
import io.github.valuya.heic.PixelRegion;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdentityColourTest {
    // 64x64 red, green, blue and white quadrants, encoded with heif-enc --lossless (matrix_coefficients=0).
    // The 10-bit variant uses the same PNG expanded to rgb48be, then heif-enc --lossless --bit-depth 10.
    @ParameterizedTest
    @ValueSource(strings = {"generated-rgb-lossless.heic", "generated-rgb-lossless-10bit.heic"})
    void preservesRgbColoursForIntAndByteOutput(String sampleName) throws IOException {
        HeicImage image = HeicImage.load(TestSamples.samplePath(sampleName));
        int[] pixels = image.decodeArgb();
        byte[] rgbBytes = image.decodeBytes(PixelFormat.RGB24);
        int[] colours = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF};

        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int expected = colours[(y / 32) * 2 + x / 32];
                int index = y * 64 + x;
                assertThat(pixels[index]).as("ARGB at %d,%d", x, y).isEqualTo(expected);
                byte[] rgb = Arrays.copyOfRange(rgbBytes, index * 3, index * 3 + 3);
                assertThat(rgb)
                        .as("RGB at %d,%d", x, y)
                        .containsExactly((byte) (expected >> 16), (byte) (expected >> 8), (byte) expected);
            }
        }

        PixelRegion region = new PixelRegion(32, 32, 16, 16);
        int[] raster = new int[16 * 20 + 3];
        Arrays.fill(raster, 0x12345678);
        image.decodeArgb(region, raster, 3, 20);
        assertThat(raster[0]).isEqualTo(0x12345678);
        for (int row = 0; row < 16; row++) {
            int offset = 3 + row * 20;
            assertThat(Arrays.copyOfRange(raster, offset, offset + 16)).containsOnly(0xFFFFFFFF);
            assertThat(Arrays.copyOfRange(raster, offset + 16, offset + 20)).containsOnly(0x12345678);
        }
    }
}
