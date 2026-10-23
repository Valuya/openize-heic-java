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

import io.github.valuya.heic.HeicImage;
import io.github.valuya.heic.PixelFormat;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Several images decoded at the same time must decode exactly as they do alone. */
class ConcurrentDecodingTest {

    @Test
    void decodesSeveralImagesConcurrently() throws InterruptedException, ExecutionException {
        List<String> sampleNames = List.of(
                "nokia/grid_960x640.heic",
                "nokia/overlay_1000x680.heic",
                "iphone_telephoto_with_scaling_list.heic",
                "gimp_with_clap.heic");

        try (ExecutorService executorService = Executors.newFixedThreadPool(sampleNames.size())) {
            List<Future<byte[]>> decodedImageFutures = sampleNames.stream()
                    .map(sampleName -> executorService.submit(() -> decode(sampleName)))
                    .toList();

            for (int sampleIndex = 0; sampleIndex < sampleNames.size(); sampleIndex++) {
                byte[] argbBytes = decodedImageFutures.get(sampleIndex).get();
                TestSamples.assertMatchesReference(sampleNames.get(sampleIndex), argbBytes);
            }
        }
    }

    private static byte[] decode(String sampleName) throws IOException {
        HeicImage heicImage = HeicImage.load(TestSamples.samplePath(sampleName));
        return heicImage.decodeBytes(PixelFormat.ARGB32);
    }
}
