# HEIC decoder for Java

![Openize.HEIC](publish/openize_heic_java.png)

A HEIC decoder in pure Java: HEIF files (ISO/IEC 23008-12) of HEVC coded images, such as the photos of phones and
cameras, decoded to RGB without native libraries.

This is Valuya's fork of [Openize.HEIC](https://github.com/openize-com/openize-heic-java). It decodes like libheif,
rejects malformed files cleanly, and is written as plain modern Java. It requires Java 21 or later.

## Installation

The decoder is on Maven Central:

```xml
<dependency>
    <groupId>io.github.valuya</groupId>
    <artifactId>heic-decoder</artifactId>
    <version>1.0.0</version>
</dependency>
```

or with Gradle:

```kotlin
implementation("io.github.valuya:heic-decoder:1.0.0")
```

Its packages are `io.github.valuya.heic`, the API, and `io.github.valuya.heic.isobmff`, the file format boxes the
decoder reads; its module name is `io.github.valuya.heic`. It depends on
[metadata-extractor](https://github.com/drewnoakes/metadata-extractor) for Exif and on the JSpecify annotations.

## Supported

* HEVC intra-coded images: Main, Main 10 and the range extensions, 4:0:0, 4:2:0, 4:2:2 and 4:4:4, up to 16 bits per
  sample;
* multiple slices and slice segments, tiles, wavefront parallel processing, PCM, transform skip, scaling lists,
  deblocking and sample adaptive offset;
* grid, overlay and identity derived images, clean aperture crop, rotation and mirroring;
* alpha planes, depth maps and the other auxiliary images, thumbnails, alternative groups;
* colours from the `nclx` colour property, or from the bitstream's video usability information;
* Exif metadata (read with [metadata-extractor](https://github.com/drewnoakes/metadata-extractor)), XMP and other
  MIME items.

Decoded images match libheif within YCbCr to RGB rounding: at most 2 levels per channel. Of the 344 files of the
HEIF conformance suite and of libheif's and pillow_heif's test data, every HEVC file libheif decodes decodes the same.

Not supported: image sequences coded with P and B slices, codecs other than HEVC (AVIF, uncompressed `unci` images),
applying ICC profiles or HDR gain maps, and the screen content coding tools (palette mode, adaptive colour
transform) and cross-component prediction. Files that need them fail with `HeicFormatException`.

## Performance

The 10 photos and collections of `TestsData/samples` (0.6 to 16 MP; all but the HDR tone map and alpha test files) on
an Intel Core Ultra 9 285H, Java 21. The fork and the Rust decoder take the median of 9 decodes, best of 3 runs taken
in turn with the others; libheif the best of 5 processes:

| | one P-core | 8 E-cores | allocated for a 12 MP photo | smallest heap for it |
|---|---|---|---|---|
| this fork | 2.05 s | 0.59 s | 51 MB | 56 MB |
| [heic-decoder](https://github.com/Valuya/heic-decoder-rust) (Rust), `decode_rgb8` | 1.91 s¹ | 1.72 s¹ | | |
| libheif 1.17 (native, `heif-convert`) | 2.65 s | 2.23 s | | |
| upstream Openize.HEIC 26.5.1 | 19.0 s | 20.3 s | 2.8 GB | 768 MB |

¹ at d6dabf9, a release build with thin LTO; of 9 samples: it does not decode `overlay_1000x680.heic`. The fork takes
2.02 s and 0.54 s for the same 9.

* On one core the fork is level with the Rust decoder on the 12 MP iPhone photos (307 against 312 ms for
  `iphone_photo.heic`, faster on two others), 1.15 to 1.2 times slower on the 16 MP `samsung_a71.heic` and the
  photos coded in many small blocks, whose per-block work still costs it more, and 1.3 to 1.5 times slower on the
  two smallest samples.
* On 8 cores the Rust decoder's `decode_rgb8` decodes one row of grid tiles at a time, to bound its memory; the fork
  decodes all the tiles of a grid in parallel.
* libheif's times are per process, writing raw YCbCr: they include starting `heif-convert` and loading its decoder
  plugin, which is why the small samples take longer on 8 E-cores than on one P-core.

The photo is `iphone_photo.heic`, a grid of 512x512 tiles: 48.8 MB of the 51 MB it allocates is the decoded image
itself. The 16 MP `samsung_a71.heic` allocates 67 MB, 64 MB of it the image, and decodes in a 72 MB heap. The heaps
are G1's, decoding one tile at a time; [Memory](#memory) has the other collectors, libheif and the Rust decoder. The
Java times leave out JIT warm-up. Java 21 and 26 decode equally fast.

`DecodeBenchmark`, in the test sources, makes the measurement:

```bash
mvn -q test-compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
taskset -c 4 java -cp target/classes:target/test-classes:$(cat target/classpath.txt) \
        io.github.valuya.heic.tests.DecodeBenchmark 12 9 false \
        $(ls TestsData/samples/*.heic TestsData/samples/nokia/*.heic | grep -v -e tmap -e gimp_rgb)
```

### Memory

Peak resident memory of a process that decodes one photo, as GNU `time` reports it, in MiB, median of 3 runs on one
P-core:

| | before decoding | 12 MP `iphone_photo.heic` | 16 MP `samsung_a71.heic` |
|---|---|---|---|
| libheif 1.17, `heif-convert` to YCbCr (`.y4m`) | 5 | 77 | 75 |
| libheif 1.17, `heif-convert` to RGB (`.png`) | 5 | 77 | 98 |
| heic-decoder (Rust), `decode_rgb8`, one thread | 4 | 51 | 65 |
| this fork, `decodeBytes(RGB24)`, G1, smallest heap (44 and 56 MiB) | 41 | 105 | 119 |
| this fork, `decodeArgb`, G1, smallest heap (56 and 72 MiB) | 41 | 116 | 135 |
| this fork, `decodeArgb`, Serial GC, smallest heap (72 and 96 MiB) | 41 | 115 | 135 |
| this fork, `decodeArgb`, no `-Xmx` (Serial, the JVM's choice on one CPU) | 41 | 118 | 136 |

"Before decoding" is `heif-convert --version`, the Rust decoder reading the file only, and `DecodeBenchmark` given no
file. On 8 E-cores libheif peaks the same and the Rust decoder at 73 and 88 MiB; the fork, decoding the tiles in
parallel, needs 60 and 76 MiB of G1 heap and peaks at 138 and 154 MiB (138 and 159 MiB without `-Xmx`). The other
samples are 12 MP at most: libheif peaks within 1 MiB of the iPhone photo on them, and the fork needs no bigger heap.

The smallest heap, in 4 MiB steps, depends on the collector. Serial needs the most because the decoded image, one
`int[]` of 46.5 MiB for 12 MP, must fit in its old generation (two thirds of the heap by default) with whatever else
is still live. The JVM picks Serial when it sees one CPU or less than 1792 MB of memory, as in a small container.

What compares, and what does not:

* Neither process is only a decoder. `heif-convert` and the Rust decoder are native programs of 4 or 5 MiB; the JVM
  takes about 41 MiB before it decodes anything, and a process that decodes once also pays for compiling the
  decoder, about 20 MiB: in the same 44 MiB heap, the iPhone photo decoded to RGB24 peaks at 84 MiB with
  `-XX:TieredStopAtLevel=1` (no C2).
* A long-running JVM pays both once, shared with the rest of the application, so what one more decode costs there
  is its heap: 44 (RGB24) or 56 MiB (ARGB) for the iPhone photo and 56 or 72 MiB for the Samsung one, the image
  plus 9 to 11 MiB, the file's bytes included. The Rust decoder adds 47 and 60 MiB to its process, the image plus
  12 to 14 MiB.
  `heif-convert` adds 72 MiB for the iPhone photo, and 70 (YCbCr) or 93 MiB (RGB) for the Samsung one; libheif
  1.23.6 inside another process (through pillow-heif, decoding to RGB, peak above the Python interpreter's) adds 50
  and 73 MiB.
* The outputs differ. `decodeArgb` returns ARGB `int`s: 46.5 and 61 MiB. `decodeBytes(RGB24)` and the Rust
  decoder's `decode_rgb8` return RGB bytes: 35 and 46 MiB. `heif-convert` keeps YCbCr 4:2:0 for `.y4m` (17 and
  23 MiB) and converts it to RGB for `.png` (35 and 46 MiB), which it then compresses row by row.
* Without `-Xmx` the JVM sizes the heap from the memory it may use (a quarter of it, 13.75 GiB here) and lets
  garbage pile up before collecting: a decode that allocates little more than its image keeps that row close to
  the smallest heap's.

One cold decode per process, with the heap to try:

```bash
taskset -c 4 /usr/bin/time -f '%M KiB' java -XX:+UseG1GC -Xmx56m \
        -cp target/classes:target/test-classes:$(cat target/classpath.txt) \
        io.github.valuya.heic.tests.DecodeBenchmark 0 1 false TestsData/samples/iphone_photo.heic
```

and libheif 1.17, from Ubuntu 24.04:

```bash
docker build -t libheif-1.17 - <<'EOF'
FROM ubuntu:24.04
RUN apt-get update && apt-get install -y --no-install-recommends libheif-examples libheif-plugin-libde265 time
EOF
docker run --rm --cpuset-cpus 4 -v "$PWD/TestsData/samples:/s:ro" libheif-1.17 \
        /usr/bin/time -f '%M KiB' heif-convert --quiet /s/iphone_photo.heic /tmp/out.y4m
```

## Usage

```java
HeicImage image = HeicImage.load(Path.of("photo.heic"));

// the primary image, as displayed: cropped, rotated, with its alpha
int[] argbPixels = image.decodeArgb();

// part of it: only the grid tiles the region covers are decoded
int[] centre = image.decodeArgb(new PixelRegion(1000, 1000, 512, 512));
```

Into a `BufferedImage`, without a copy:

```java
BufferedImage bufferedImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
int[] raster = ((DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();
image.decodeArgb(PixelRegion.ofSize(image.getWidth(), image.getHeight()), raster, 0, image.getWidth());
ImageIO.write(bufferedImage, "png", new File("photo.png"));
```

Every image of the file, and the Exif metadata:

```java
for (HeicImageFrame frame : image.getTopLevelFrames().values()) {
    byte[] rgbaBytes = frame.decodeBytes(PixelFormat.RGBA32);
}

Optional<String> cameraModel = image.getExifOptional()
        .flatMap(exif -> exif.getDescriptionOptional(ExifIFD0Directory.class, ExifIFD0Directory.TAG_MODEL));
```

`HeicImage` loads the metadata only; pixels are decoded when asked for. An instance can be shared between
threads, and the tiles of a grid decode in parallel on the common fork-join pool.

### Untrusted files

A malformed or unsupported file fails with `HeicFormatException`, never with another exception or a hang. Before
anything is allocated, the size of each coded picture and of the requested image is checked against
`HeicDecodingOptions.maxPixelCount` (16384 x 16384 by default):

```java
HeicDecodingOptions options = HeicDecodingOptions.DEFAULT.withMaxPixelCount(50_000_000).withParallel(false);
HeicImage image = HeicImage.load(uploadedBytes, options);
```

## Building and testing

```bash
mvn verify
```

compiles, checks the formatting (palantir-java-format, `mvn spotless:apply` fixes it) and runs the tests. The
samples' decoded pixels are compared with SHA-256 digests in `TestsData/references`; a deliberate change of the
output regenerates them with `-Dopenize.updateReferences=true`. The other suites take options:

* `LibheifComparisonTest` decodes every sample with libheif too and requires the same pixels within 2 levels. It runs
  when `heif-dec` or `heif-convert` (Debian/Ubuntu `libheif-examples`, Homebrew `libheif`) is on the `PATH`, or with
  `-Dopenize.libheif.decoder=/path/to/heif-convert`; `-Dopenize.libheif.corpus=/dir` adds the files of a directory.
* `CorpusRobustnessTest` requires every file to decode or fail with `HeicFormatException`, in bounded time:
  `-Dopenize.corpus=/dir` runs it on a directory, such as the HEIF conformance files and libheif's test data.
* `MutationFuzzTest` does the same on corrupted copies of the samples: `-Dopenize.fuzz.iterations=50000`,
  `-Dopenize.fuzz.seed=2`.

## Releasing

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/), and the version is not
committed: the pom's `0-SNAPSHOT` is never released. Running the [release workflow](.github/workflows/release.yml) on
`main`, from the Actions tab or with `gh workflow run release.yml`, publishes a new version when a commit since the
last release changed `src/main` or `pom.xml`, other than a `build`, `chore`, `ci`, `docs`, `style` or `test` commit; a
dependency upgrade that users should get is a `fix(deps)`. [`tools/release-version.sh`](tools/release-version.sh)
computes the version with [git-cliff](https://git-cliff.org/) from the commits: a breaking change (`feat!:` or a
`BREAKING CHANGE:` footer) bumps the major version, `feat:` the minor, anything else the patch. The workflow tests,
signs and publishes the version to Maven Central, then tags it `vX.Y.Z` and creates its GitHub release, with the
commits as release notes.

## License

Openize.HEIC is available under the [Openize License](LICENSE).

> [!CAUTION]
> Openize does not and cannot grant You a patent license for the utilization of HEVC/H.265 image
> compression/decompression technologies.

The `io.github.valuya.heic.isobmff` package is distributed under the [MIT License](licenses). metadata-extractor is
distributed under the
[Apache-2.0 license](https://github.com/drewnoakes/metadata-extractor?tab=Apache-2.0-1-ov-file#readme).

## OSS Notice

The sample files in `TestsData/samples/nokia` belong to Nokia Technologies and are used according to the
[Nokia High-Efficiency Image File Format (HEIF) License](https://github.com/nokiatech/heif/blob/master/LICENSE.TXT):

> Licensed Field means the non-commercial purposes of evaluation, testing and academic research in each
> non-commercial case to use, run, modify (in a way that still complies with the Specification) and copy the
> Software to (a) generate, using one or more encoded pictures as inputs, a file complying with the Specification and
> including the one or more encoded pictures that were given as inputs; and/or (b) read a file complying with the
> Specification, resulting into one or more encoded pictures included in the file as outputs.
