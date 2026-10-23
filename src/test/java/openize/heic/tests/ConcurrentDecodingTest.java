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

import openize.heic.decoder.HeicImage;
import openize.heic.decoder.PixelFormat;
import openize.io.IOFileStream;
import openize.io.IOMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

    private static byte[] decode(String sampleName) {
        try (IOFileStream heicStream = new IOFileStream(TestSamples.samplePath(sampleName), IOMode.READ)) {
            HeicImage heicImage = HeicImage.load(heicStream);
            return heicImage.getByteArray(PixelFormat.Argb32);
        }
    }
}
