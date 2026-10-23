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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Locates the sample files and checks decoded output against the reference digests in {@code TestsData/references}.
 *
 * <p>Run the tests with {@code -Dopenize.updateReferences=true} to rewrite the digests after an intended change in
 * the decoded output; check such a change against libheif first with {@link LibheifComparisonTest}.
 */
final class TestSamples {
    private static final Path TESTS_DATA = Path.of(System.getProperty("basedir", "."), "TestsData");
    private static final Path SAMPLES = TESTS_DATA.resolve("samples");
    private static final Path REFERENCES = TESTS_DATA.resolve("references");

    private TestSamples() {}

    static Path samplePath(String sampleName) {
        return SAMPLES.resolve(sampleName);
    }

    static List<Path> listSamples() {
        return listHeicFiles(SAMPLES);
    }

    static List<Path> listHeicFiles(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(
                            path -> path.getFileName().toString().toLowerCase().endsWith(".heic"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    static void assertMatchesReference(String referenceName, byte[] decodedBytes) {
        Path referenceFile = REFERENCES.resolve(referenceName + ".sha256");
        String decodedDigest = "%d %s".formatted(decodedBytes.length, sha256(decodedBytes));

        try {
            if (Boolean.getBoolean("openize.updateReferences")) {
                Files.createDirectories(referenceFile.getParent());
                Files.writeString(referenceFile, decodedDigest + "\n", StandardCharsets.US_ASCII);
                return;
            }

            String referenceDigest =
                    Files.readString(referenceFile, StandardCharsets.US_ASCII).trim();
            assertThat(decodedDigest)
                    .as("decoded data of %s against its reference digest", referenceName)
                    .isEqualTo(referenceDigest);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
