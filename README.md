# Openize.HEIC for Java

![Openize.HEIC](publish/openize_heic_java.png)

A HEIC decoder in pure Java: HEIF files (ISO/IEC 23008-12) of HEVC coded images, such as the photos of phones and
cameras, decoded to RGB without native libraries.

This is Valuya's fork of [Openize.HEIC](https://github.com/openize-com/openize-heic-java). It decodes like libheif,
rejects malformed files cleanly, and is written as plain modern Java. It requires Java 21 or later.

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

The 10 photos and collections of `TestsData/samples` (0.6 to 16 MP; all but the HDR tone map and alpha test files),
median of 5 to 7 decodes, Java 21 on an Intel Core Ultra 9 285H:

| | one P-core | 8 E-cores | allocated for a 12 MP photo | smallest heap for it |
|---|---|---|---|---|
| this fork | 3.4 s | 0.87 s | 116 MB | 64 MB |
| upstream Openize.HEIC 26.5.1 | 19.0 s | 20.3 s | 2.8 GB | 768 MB |
| libheif 1.17 (native, `heif-convert`) | 2.7 s | 1.5 s | | |

The photo is `iphone_photo.heic`, a grid of 512x512 tiles: 48 MB of its 116 MB is the decoded image itself. The
16 MP `samsung_a71.heic` allocates 177 MB, 64 MB of it the image, and decodes in a 96 MB heap. The Java times leave
out JIT warm-up; libheif's are per process, writing raw YCbCr. Java 21 and 26 decode equally fast.

`DecodeBenchmark`, in the test sources, makes the measurement:

```bash
mvn -q test-compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
taskset -c 4 java -cp target/classes:target/test-classes:$(cat target/classpath.txt) \
        openize.heic.tests.DecodeBenchmark 4 7 false \
        $(ls TestsData/samples/*.heic TestsData/samples/nokia/*.heic | grep -v -e tmap -e gimp_rgb)
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

## License

Openize.HEIC is available under the [Openize License](LICENSE).

> [!CAUTION]
> Openize does not and cannot grant You a patent license for the utilization of HEVC/H.265 image
> compression/decompression technologies.

The `openize.isobmff` package is distributed under the [MIT License](licenses). metadata-extractor is distributed
under the [Apache-2.0 license](https://github.com/drewnoakes/metadata-extractor?tab=Apache-2.0-1-ov-file#readme).

## OSS Notice

The sample files in `TestsData/samples/nokia` belong to Nokia Technologies and are used according to the
[Nokia High-Efficiency Image File Format (HEIF) License](https://github.com/nokiatech/heif/blob/master/LICENSE.TXT):

> Licensed Field means the non-commercial purposes of evaluation, testing and academic research in each
> non-commercial case to use, run, modify (in a way that still complies with the Specification) and copy the
> Software to (a) generate, using one or more encoded pictures as inputs, a file complying with the Specification and
> including the one or more encoded pictures that were given as inputs; and/or (b) read a file complying with the
> Specification, resulting into one or more encoded pictures included in the file as outputs.
