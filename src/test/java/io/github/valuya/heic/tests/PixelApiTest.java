/*
 * Openize.HEIC
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of Openize.HEIC.
 *
 * Openize.HEIC is available under Openize license, which is
 * available along with Openize.HEIC sources.
 */

package io.github.valuya.heic.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.valuya.heic.HeicImage;
import io.github.valuya.heic.PixelFormat;
import io.github.valuya.heic.PixelRegion;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PixelApiTest {
    // 430x430 with an alpha plane, so that every byte of a pixel is meaningful
    private static final String ALPHA_SAMPLE = "gimp_rgb_420_with_alpha.heic";

    @Test
    void decodesIntoARasterAtItsOffsetAndStride() throws IOException {
        HeicImage heicImage = HeicImage.load(TestSamples.samplePath(ALPHA_SAMPLE));
        PixelRegion region = new PixelRegion(100, 50, 64, 32);
        int scanlineStride = 80;
        int offset = 7;
        int[] raster = new int[offset + region.height() * scanlineStride];
        Arrays.fill(raster, 0x12345678);

        heicImage.decodeArgb(region, raster, offset, scanlineStride);

        int[] regionPixels = heicImage.decodeArgb(region);
        for (int row = 0; row < region.height(); row++) {
            int rasterRowStart = offset + row * scanlineStride;
            int[] rasterRow = Arrays.copyOfRange(raster, rasterRowStart, rasterRowStart + region.width());
            int[] regionRow = Arrays.copyOfRange(regionPixels, row * region.width(), (row + 1) * region.width());
            int[] rowPadding =
                    Arrays.copyOfRange(raster, rasterRowStart + region.width(), rasterRowStart + scanlineStride);
            assertThat(rasterRow).as("row %d", row).containsExactly(regionRow);
            assertThat(rowPadding).as("padding after row %d", row).containsOnly(0x12345678);
        }
        int[] rasterStart = Arrays.copyOfRange(raster, 0, offset);
        assertThat(rasterStart).containsOnly(0x12345678);
    }

    @Test
    void laysOutBytesInTheOrderOfThePixelFormat() throws IOException {
        HeicImage heicImage = HeicImage.load(TestSamples.samplePath(ALPHA_SAMPLE));
        PixelRegion region = new PixelRegion(300, 200, 16, 16);
        int[] argbPixels = heicImage.decodeArgb(region);

        byte[] rgbBytes = heicImage.decodeBytes(PixelFormat.RGB24, region);
        byte[] bgraBytes = heicImage.decodeBytes(PixelFormat.BGRA32, region);

        int argb = argbPixels[17];
        byte[] rgbPixel = Arrays.copyOfRange(rgbBytes, 17 * 3, 18 * 3);
        byte[] bgraPixel = Arrays.copyOfRange(bgraBytes, 17 * 4, 18 * 4);
        assertThat(rgbPixel).containsExactly((byte) (argb >> 16), (byte) (argb >> 8), (byte) argb);
        assertThat(bgraPixel)
                .containsExactly((byte) argb, (byte) (argb >> 8), (byte) (argb >> 16), (byte) (argb >>> 24));
    }

    // decodeBytes writes the bytes itself, the alpha plane's too: they are the ARGB pixels in the format, with the
    // alpha of the alpha sample and the strided rows of a rotated one
    @ParameterizedTest
    @EnumSource(PixelFormat.class)
    void decodesBytesAsTheArgbPixelsInTheFormat(PixelFormat pixelFormat) throws IOException {
        for (String sampleName : List.of(ALPHA_SAMPLE, "iphone_rotated_and_mirrored.heic")) {
            HeicImage heicImage = HeicImage.load(TestSamples.samplePath(sampleName));
            PixelRegion region = new PixelRegion(30, 20, 300, 200);
            int[] argbPixels = heicImage.decodeArgb(region);

            byte[] bytes = heicImage.decodeBytes(pixelFormat, region);

            assertThat(bytes).as(sampleName).isEqualTo(inFormat(argbPixels, pixelFormat));
        }
    }

    private static byte[] inFormat(int[] argbPixels, PixelFormat pixelFormat) {
        int bytesPerPixel = pixelFormat.bytesPerPixel();
        byte[] bytes = new byte[argbPixels.length * bytesPerPixel];
        for (int i = 0; i < argbPixels.length; i++) {
            int argb = argbPixels[i];
            int[] shifts = switch (pixelFormat) {
                case RGB24 -> new int[] {16, 8, 0};
                case RGBA32 -> new int[] {16, 8, 0, 24};
                case ARGB32 -> new int[] {24, 16, 8, 0};
                case BGRA32 -> new int[] {0, 8, 16, 24};
            };
            for (int b = 0; b < bytesPerPixel; b++) {
                bytes[i * bytesPerPixel + b] = (byte) (argb >>> shifts[b]);
            }
        }
        return bytes;
    }

    @Test
    void refusesARegionOutsideTheImage() throws IOException {
        HeicImage heicImage = HeicImage.load(TestSamples.samplePath(ALPHA_SAMPLE));
        PixelRegion pastTheRightEdge = new PixelRegion(heicImage.getWidth() - 10, 0, 20, 20);

        assertThatThrownBy(() -> heicImage.decodeArgb(pastTheRightEdge))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside");
    }

    @Test
    void refusesARasterTooSmallForTheRegion() throws IOException {
        HeicImage heicImage = HeicImage.load(TestSamples.samplePath(ALPHA_SAMPLE));
        PixelRegion region = new PixelRegion(0, 0, 64, 32);
        int[] tooSmallRaster = new int[64 * 31];

        assertThatThrownBy(() -> heicImage.decodeArgb(region, tooSmallRaster, 0, 64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not fit");
    }

    @Test
    void refusesAnEmptyOrNegativeRegion() {
        assertThatThrownBy(() -> new PixelRegion(0, 0, 0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PixelRegion(-1, 0, 10, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesARegionWhoseEdgesOverflow() {
        assertThatThrownBy(() -> new PixelRegion(Integer.MAX_VALUE, 0, 2, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PixelRegion(0, Integer.MAX_VALUE, 1, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
