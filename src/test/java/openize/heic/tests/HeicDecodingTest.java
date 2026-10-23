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

import java.io.IOException;
import java.util.Map;
import openize.heic.decoder.HeicImage;
import openize.heic.decoder.HeicImageFrame;
import openize.heic.decoder.PixelFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Decodes the samples and compares the output with the reference digests. */
class HeicDecodingTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "iphone_photo.heic", // iPhone grid image
                "iphone_portrait_photo.heic",
                "iphone_rotated_and_mirrored.heic", // irot + imir, iPhone 14 Pro
                "iphone_log2CbSize_issue.heic", // IMAGINGNET-7509 (iPhone 15 Pro), IMAGINGNET-7450
                "iphone_telephoto_with_scaling_list.heic", // explicit scaling lists, iPhone 12 Pro
                "samsung_a71.heic", // bigger HandlerBox
                "gimp_rgb_420_with_alpha.heic", // alpha plane
                "gimp_with_clap.heic", // clean aperture crop
                "nokia/grid_960x640.heic", // derived grid image
                "nokia/overlay_1000x680.heic" // derived overlay image
            })
    void decodesPrimaryImage(String sampleName) throws IOException {
        HeicImage heicImage = HeicImage.load(TestSamples.samplePath(sampleName));
        byte[] argbBytes = heicImage.decodeBytes(PixelFormat.ARGB32);

        TestSamples.assertMatchesReference(sampleName, argbBytes);
    }

    @Test
    void decodesEveryFrameOfACollection() throws IOException {
        String sampleName = "nokia/random_collection_1440x960.heic";
        HeicImage heicImage = HeicImage.load(TestSamples.samplePath(sampleName));
        Map<Long, HeicImageFrame> framesById = heicImage.getTopLevelFrames();

        assertThat(framesById).isNotEmpty();
        framesById.forEach((frameId, heicImageFrame) -> {
            byte[] argbBytes = heicImageFrame.decodeBytes(PixelFormat.ARGB32);
            TestSamples.assertMatchesReference(sampleName + "_" + frameId, argbBytes);
        });
    }

    @Test
    void loadsIos18ImageWithToneMapAndMattes() throws IOException {
        String sampleName = "iphone_iOS18_tmap+MatteMaps.heic";
        HeicImage heicImage = HeicImage.load(TestSamples.samplePath(sampleName));

        assertThat(heicImage).extracting(HeicImage::getWidth).isEqualTo(4284);
        assertThat(heicImage).extracting(HeicImage::getHeight).isEqualTo(5712);
    }
}
