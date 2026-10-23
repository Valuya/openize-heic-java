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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.github.valuya.heic.HeicDecodingOptions;
import io.github.valuya.heic.HeicFormatException;
import io.github.valuya.heic.HeicImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * Decodes randomly corrupted copies of small samples: each must decode or fail with {@link HeicFormatException}, in
 * bounded time and memory. The corruption is deterministic, so a failing iteration reproduces from its number.
 *
 * <p>{@code -Dopenize.fuzz.iterations=N} runs more iterations, {@code -Dopenize.fuzz.seed=S} another sequence.
 */
class MutationFuzzTest {
    private static final List<String> SEED_SAMPLES =
            List.of("gimp_rgb_420_with_alpha.heic", "nokia/grid_960x640.heic", "gimp_with_clap.heic");
    private static final int DEFAULT_ITERATIONS = 300;
    private static final Duration DECODE_TIMEOUT = Duration.ofSeconds(20);
    // keeps a corrupted size from allocating gigabytes legitimately
    private static final HeicDecodingOptions FUZZ_OPTIONS =
            HeicDecodingOptions.DEFAULT.withMaxPixelCount(4_000_000).withParallel(false);

    @Test
    void decodesOrRejectsCorruptedFiles() throws IOException {
        List<byte[]> seedFiles = new ArrayList<>();
        for (String sampleName : SEED_SAMPLES) {
            seedFiles.add(Files.readAllBytes(TestSamples.samplePath(sampleName)));
        }
        int iterationCount = Integer.getInteger("openize.fuzz.iterations", DEFAULT_ITERATIONS);
        long sequenceSeed = Long.getLong("openize.fuzz.seed", 1L);

        List<String> failures = new ArrayList<>();
        for (int iteration = 0; iteration < iterationCount; iteration++) {
            SplittableRandom random = new SplittableRandom(sequenceSeed * 1_000_003L + iteration);
            byte[] seedFile = seedFiles.get(random.nextInt(seedFiles.size()));
            byte[] corruptedFile = corrupt(seedFile, random);

            Throwable decodingFailure =
                    assertTimeoutPreemptively(DECODE_TIMEOUT, () -> catchThrowable(() -> decode(corruptedFile)));

            if (decodingFailure != null && !(decodingFailure instanceof HeicFormatException)) {
                failures.add("iteration %d: %s".formatted(iteration, describe(decodingFailure)));
            }
        }

        assertThat(failures)
                .as("decoding failures other than HeicFormatException, seed %d", sequenceSeed)
                .isEmpty();
    }

    @Test
    void rejectsAnImageOfNoPixels() throws IOException {
        byte[] heicData = Files.readAllBytes(TestSamples.samplePath("gimp_rgb_420_with_alpha.heic"));
        // each ispe box: 'ispe', version and flags, then a 32-bit width and height
        byte[] ispeType = "ispe".getBytes(StandardCharsets.US_ASCII);
        for (int index = 0; index + 12 <= heicData.length; index++) {
            if (Arrays.equals(heicData, index, index + 4, ispeType, 0, 4)) {
                Arrays.fill(heicData, index + 8, index + 16, (byte) 0);
            }
        }
        HeicImage heicImage = HeicImage.load(heicData, FUZZ_OPTIONS);

        assertThatThrownBy(heicImage::decodeArgb)
                .isInstanceOf(HeicFormatException.class)
                .hasMessageContaining("0x0");
    }

    private static String describe(Throwable decodingFailure) {
        StackTraceElement origin = decodingFailure.getStackTrace().length > 0
                ? decodingFailure.getStackTrace()[0]
                : new StackTraceElement("?", "?", "?", 0);
        return "%s at %s.%s:%d (%s)"
                .formatted(
                        decodingFailure.getClass().getSimpleName(),
                        origin.getClassName(),
                        origin.getMethodName(),
                        origin.getLineNumber(),
                        decodingFailure.getMessage());
    }

    private static void decode(byte[] heicData) {
        HeicImage heicImage = HeicImage.load(heicData, FUZZ_OPTIONS);
        heicImage.decodeArgb();
    }

    // one to eight corruptions: flipped bits, overwritten bytes, a truncation, a duplicated or deleted range
    private static byte[] corrupt(byte[] original, SplittableRandom random) {
        byte[] corrupted = original.clone();
        int corruptionCount = 1 + random.nextInt(8);
        for (int corruption = 0; corruption < corruptionCount && corrupted.length > 16; corruption++) {
            corrupted = switch (random.nextInt(5)) {
                case 0 -> flipBit(corrupted, random);
                case 1 -> overwriteByte(corrupted, random);
                case 2 -> Arrays.copyOf(corrupted, 16 + random.nextInt(corrupted.length - 16));
                case 3 -> duplicateRange(corrupted, random);
                default -> deleteRange(corrupted, random);
            };
        }
        return corrupted;
    }

    private static byte[] flipBit(byte[] data, SplittableRandom random) {
        int position = random.nextInt(data.length);
        data[position] ^= (byte) (1 << random.nextInt(8));
        return data;
    }

    private static byte[] overwriteByte(byte[] data, SplittableRandom random) {
        // boundary values break sizes and counts most often
        int[] interestingValues = {0x00, 0x01, 0x7F, 0x80, 0xFF};
        int position = random.nextInt(data.length);
        data[position] = (byte) interestingValues[random.nextInt(interestingValues.length)];
        return data;
    }

    private static byte[] duplicateRange(byte[] data, SplittableRandom random) {
        int start = random.nextInt(data.length);
        int length = Math.min(1 + random.nextInt(64), data.length - start);
        byte[] longer = new byte[data.length + length];
        System.arraycopy(data, 0, longer, 0, start + length);
        System.arraycopy(data, start, longer, start + length, data.length - start);
        return longer;
    }

    private static byte[] deleteRange(byte[] data, SplittableRandom random) {
        int start = random.nextInt(data.length);
        int length = Math.min(1 + random.nextInt(64), data.length - start);
        byte[] shorter = new byte[data.length - length];
        System.arraycopy(data, 0, shorter, 0, start);
        System.arraycopy(data, start + length, shorter, start, data.length - start - length);
        return shorter;
    }
}
