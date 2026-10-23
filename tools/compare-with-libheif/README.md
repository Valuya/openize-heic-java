# Compare with libheif

Decodes HEIC files with Openize.HEIC and with [libheif](https://github.com/strukturag/libheif)
(through `pillow-heif`), then reports, per file, the PSNR, the largest per-channel difference
and how blocky each output is along the 8-pixel grid.

```bash
tools/compare-with-libheif/compare.sh TestsData/samples
```

The two decoders reconstruct the same YCbCr samples; what remains is the YCbCr to RGB
conversion, which rounds differently, so a maximum difference of 1 or 2 is expected. The
script exits non-zero above `MAX_DIFF` (default 2). Run it before rewriting the test
references with `mvn test -Dopenize.updateReferences=true`.
