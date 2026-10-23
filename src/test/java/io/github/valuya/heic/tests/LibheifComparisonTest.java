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

import io.github.valuya.heic.HeicImage;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentest4j.TestAbortedException;

/**
 * Decodes every sample with Openize.HEIC and with libheif's command line decoder, and requires the same dimensions
 * and pixels within YCbCr to RGB rounding.
 *
 * <p>Skipped unless libheif's decoder is available: {@code heif-dec} (libheif 1.18+) or {@code heif-convert} on the
 * PATH (Debian/Ubuntu {@code libheif-examples}, Homebrew {@code libheif}), or a command given with
 * {@code -Dopenize.libheif.decoder=/path/to/heif-dec}. {@code -Dopenize.libheif.corpus=/dir} adds the {@code .heic}
 * files of another directory.
 */
class LibheifComparisonTest {
    // YCbCr to RGB conversion rounds differently in the two decoders; the decoded samples are identical
    private static final int MAX_CHANNEL_DIFFERENCE = 2;
    private static final long DECODER_TIMEOUT_SECONDS = 120;

    static Stream<Path> heicFiles() {
        Stream<Path> extraCorpusFiles = Optional.ofNullable(System.getProperty("openize.libheif.corpus"))
                .map(Path::of)
                .map(TestSamples::listHeifFiles)
                .stream()
                .flatMap(List::stream);
        return Stream.concat(TestSamples.listSamples().stream(), extraCorpusFiles);
    }

    @ParameterizedTest
    @MethodSource("heicFiles")
    void decodesLikeLibheif(Path heicFile) throws IOException, InterruptedException {
        String libheifDecoder = findLibheifDecoderOptional()
                .orElseThrow(() -> new TestAbortedException("libheif's heif-dec / heif-convert is not installed"));
        List<BufferedImage> libheifImages = decodeWithLibheif(libheifDecoder, heicFile);

        HeicImage heicImage = HeicImage.load(heicFile);
        int width = heicImage.getWidth();
        int height = heicImage.getHeight();
        int[] openizePixels = heicImage.decodeArgb();

        // libheif writes every top-level image; the primary one has to be among them
        List<BufferedImage> sameSizeLibheifImages = libheifImages.stream()
                .filter(libheifImage -> libheifImage.getWidth() == width && libheifImage.getHeight() == height)
                .toList();
        assertThat(sameSizeLibheifImages)
                .as("libheif images of %s with Openize.HEIC's size %dx%d", heicFile.getFileName(), width, height)
                .isNotEmpty();

        int smallestDifference = sameSizeLibheifImages.stream()
                .mapToInt(libheifImage ->
                        maxChannelDifference(openizePixels, libheifImage.getRGB(0, 0, width, height, null, 0, width)))
                .min()
                .orElseThrow();
        assertThat(smallestDifference)
                .as("largest per-channel difference from libheif on %s", heicFile.getFileName())
                .isLessThanOrEqualTo(MAX_CHANNEL_DIFFERENCE);
    }

    private static Optional<String> findLibheifDecoderOptional() {
        return Optional.ofNullable(System.getProperty("openize.libheif.decoder"))
                .or(LibheifComparisonTest::findLibheifDecoderOnPathOptional);
    }

    private static Optional<String> findLibheifDecoderOnPathOptional() {
        List<String> pathDirectories = Arrays.asList(System.getenv("PATH").split(File.pathSeparator));
        return Stream.of("heif-dec", "heif-convert")
                .filter(decoder ->
                        pathDirectories.stream().anyMatch(directory -> Files.isExecutable(Path.of(directory, decoder))))
                .findFirst();
    }

    private static List<BufferedImage> decodeWithLibheif(String libheifDecoder, Path heicFile)
            throws IOException, InterruptedException {
        Path outputDirectory = Files.createTempDirectory("libheif-");
        try {
            Path logFile = outputDirectory.resolve("decoder.log");
            Path pngFile = outputDirectory.resolve("image.png");
            Process decoderProcess = new ProcessBuilder(libheifDecoder, heicFile.toString(), pngFile.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();

            boolean finished = decoderProcess.waitFor(DECODER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                decoderProcess.destroyForcibly();
            }
            assertThat(finished)
                    .as("%s finishing on %s", libheifDecoder, heicFile)
                    .isTrue();
            assertThat(decoderProcess.exitValue())
                    .as("%s exit code on %s: %s", libheifDecoder, heicFile, Files.readString(logFile))
                    .isZero();

            // image.png for a single image, image-1.png, image-2.png... for several top-level images
            List<BufferedImage> libheifImages = new ArrayList<>();
            for (Path libheifPngFile : listFiles(outputDirectory, ".png")) {
                libheifImages.add(ImageIO.read(libheifPngFile.toFile()));
            }
            return libheifImages;
        } finally {
            for (Path outputFile : listFiles(outputDirectory, "")) {
                Files.delete(outputFile);
            }
            Files.delete(outputDirectory);
        }
    }

    private static List<Path> listFiles(Path directory, String suffix) {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static int maxChannelDifference(int[] argbPixels, int[] otherArgbPixels) {
        int maxDifference = 0;
        for (int pixelIndex = 0; pixelIndex < argbPixels.length; pixelIndex++) {
            for (int shift = 0; shift <= 16; shift += 8) {
                int channel = (argbPixels[pixelIndex] >> shift) & 0xFF;
                int otherChannel = (otherArgbPixels[pixelIndex] >> shift) & 0xFF;
                maxDifference = Math.max(maxDifference, Math.abs(channel - otherChannel));
            }
        }
        return maxDifference;
    }
}
