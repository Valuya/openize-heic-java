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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import openize.heic.decoder.HeicFormatException;
import openize.heic.decoder.HeicImage;
import openize.heic.decoder.PixelFormat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Decodes every file of a corpus that includes malformed and unsupported files, such as the HEIF conformance suite
 * and libheif's test and fuzzing data. Each file must decode or fail with {@link HeicFormatException}, in bounded
 * time: any other exception, or a hang, is a decoder bug.
 *
 * <p>Runs on the samples, and on the {@code .heic}, {@code .heif} and {@code .hif} files under
 * {@code -Dopenize.corpus=/dir}.
 */
class CorpusRobustnessTest {
    // generous enough for a 200 MP photo on one core: the point is to catch hangs
    private static final Duration DECODE_TIMEOUT = Duration.ofSeconds(120);

    static Stream<Path> heifFiles() {
        Stream<Path> corpusFiles =
                Optional.ofNullable(System.getProperty("openize.corpus"))
                        .map(Path::of)
                        .map(TestSamples::listHeifFiles)
                        .stream()
                        .flatMap(List::stream);
        return Stream.concat(TestSamples.listSamples().stream(), corpusFiles);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("heifFiles")
    void decodesOrRejectsAsMalformed(Path heifFile) {
        Throwable decodingFailure =
                assertTimeoutPreemptively(DECODE_TIMEOUT, () -> catchThrowable(() -> decode(heifFile)));

        if (decodingFailure != null) {
            assertThat(decodingFailure).as("failure decoding %s", heifFile).isInstanceOf(HeicFormatException.class);
        }
    }

    private static void decode(Path heifFile) throws IOException {
        HeicImage heicImage = HeicImage.load(heifFile);
        int[] argbPixels = heicImage.getInt32Array(PixelFormat.Argb32);
        int pixelCount = (int) (heicImage.getWidth() * heicImage.getHeight());

        assertThat(argbPixels).hasSize(pixelCount);
    }
}
