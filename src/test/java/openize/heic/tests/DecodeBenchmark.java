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

import com.sun.management.ThreadMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import openize.heic.decoder.HeicDecodingOptions;
import openize.heic.decoder.HeicImage;

/**
 * Times the decoding of HEIC files: each file is decoded a few times to warm the JIT up, then timed, and the median
 * is printed with what one decode allocates on the calling thread. Not a test: run it with
 * {@code java -cp <test and main classes, metadata-extractor> openize.heic.tests.DecodeBenchmark warmups runs
 * parallel files...}, pinned to the cores to measure with {@code taskset}.
 */
public final class DecodeBenchmark {
    private DecodeBenchmark() {}

    public static void main(String[] arguments) throws IOException {
        int warmupCount = Integer.parseInt(arguments[0]);
        int runCount = Integer.parseInt(arguments[1]);
        HeicDecodingOptions options = HeicDecodingOptions.DEFAULT.withParallel(Boolean.parseBoolean(arguments[2]));
        ThreadMXBean threadBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();

        double totalMillis = 0;
        for (String fileName : Arrays.copyOfRange(arguments, 3, arguments.length)) {
            Path file = Path.of(fileName);
            byte[] heicData = Files.readAllBytes(file);
            long[] runNanos = new long[runCount];
            long allocatedBytes = 0;
            for (int run = -warmupCount; run < runCount; run++) {
                long allocatedBefore = threadBean.getCurrentThreadAllocatedBytes();
                long start = System.nanoTime();
                HeicImage.load(heicData, options).decodeArgb();
                long elapsedNanos = System.nanoTime() - start;
                if (run >= 0) {
                    runNanos[run] = elapsedNanos;
                    allocatedBytes = threadBean.getCurrentThreadAllocatedBytes() - allocatedBefore;
                }
            }
            Arrays.sort(runNanos);
            double medianMillis = runNanos[runCount / 2] / 1e6;
            totalMillis += medianMillis;
            System.out.printf("%-45s %9.1f ms %8.1f MB%n", file.getFileName(), medianMillis, allocatedBytes / 1e6);
        }
        System.out.printf("%-45s %9.1f ms%n", "total", totalMillis);
    }
}
