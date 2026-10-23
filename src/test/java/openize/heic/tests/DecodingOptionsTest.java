/*
 * Openize.HEIC
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of Openize.HEIC.
 *
 * Openize.HEIC is available under Openize license, which is
 * available along with Openize.HEIC sources.
 */

package openize.heic.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import openize.heic.decoder.HeicDecodingOptions;
import openize.heic.decoder.HeicImage;
import openize.heic.decoder.HeicImageTooLargeException;
import openize.heic.decoder.PixelFormat;
import openize.heic.decoder.PixelRegion;
import org.junit.jupiter.api.Test;

class DecodingOptionsTest {
    // 3024x4032, coded as a grid of 512x512 tiles
    private static final Path IPHONE_PHOTO = TestSamples.samplePath("iphone_photo.heic");

    @Test
    void refusesToDecodeAnImageLargerThanTheLimit() throws IOException {
        HeicDecodingOptions oneMegapixel = HeicDecodingOptions.DEFAULT.withMaxPixelCount(1_000_000);
        HeicImage heicImage = HeicImage.load(IPHONE_PHOTO, oneMegapixel);

        assertThatThrownBy(() -> heicImage.decodeArgb())
                .isInstanceOf(HeicImageTooLargeException.class)
                .hasMessageContaining("3024x4032");
    }

    @Test
    void decodesARegionWithinTheLimitOfALargerImage() throws IOException {
        HeicDecodingOptions oneMegapixel = HeicDecodingOptions.DEFAULT.withMaxPixelCount(1_000_000);
        HeicImage heicImage = HeicImage.load(IPHONE_PHOTO, oneMegapixel);
        PixelRegion centre = new PixelRegion(1256, 1760, 512, 512);

        int[] centrePixels = heicImage.decodeArgb(centre);
        int[] allPixels = HeicImage.load(IPHONE_PHOTO).decodeArgb();

        assertThat(centrePixels).hasSize(512 * 512);
        for (int row = 0; row < 512; row++) {
            int centreRowStart = row * 512;
            int fullRowStart = (1760 + row) * 3024 + 1256;
            for (int column = 0; column < 512; column++) {
                int centrePixel = centrePixels[centreRowStart + column];
                int fullPixel = allPixels[fullRowStart + column];
                assertThat(centrePixel).isEqualTo(fullPixel);
            }
        }
    }

    @Test
    void refusesCodedPicturesLargerThanTheLimitEvenForASmallRegion() throws IOException {
        HeicDecodingOptions smallerThanATile = HeicDecodingOptions.DEFAULT.withMaxPixelCount(100_000);
        HeicImage heicImage = HeicImage.load(IPHONE_PHOTO, smallerThanATile);
        PixelRegion smallRegion = new PixelRegion(0, 0, 100, 100);

        assertThatThrownBy(() -> heicImage.decodeArgb(smallRegion))
                .isInstanceOf(HeicImageTooLargeException.class)
                .hasMessageContaining("512x512");
    }

    @Test
    void decodesTheSameWithoutParallelism() throws IOException {
        HeicDecodingOptions sequential = HeicDecodingOptions.DEFAULT.withParallel(false);
        HeicImage heicImage = HeicImage.load(IPHONE_PHOTO, sequential);

        byte[] argbBytes = heicImage.decodeBytes(PixelFormat.ARGB32);

        TestSamples.assertMatchesReference("iphone_photo.heic", argbBytes);
    }
}
