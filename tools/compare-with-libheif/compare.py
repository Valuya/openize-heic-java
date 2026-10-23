"""Compare the PNGs written by Decode.java with libheif's decoding of the same files (via pillow-heif)."""
import os
import sys

import numpy as np
import pillow_heif
from PIL import Image

pillow_heif.register_heif_opener()


def blockiness(rgb):
    # mean luma gradient across 8-pixel column boundaries relative to elsewhere; ~1.0 means no visible blocks
    y = rgb @ [0.299, 0.587, 0.114]
    gx = np.abs(np.diff(y, axis=1))
    cols = np.arange(gx.shape[1])
    return gx[:, (cols % 8) == 7].mean() / gx[:, (cols % 8) != 7].mean()


def main(corpus, outdir):
    print(f"libheif {pillow_heif.libheif_version()}")
    worst = 0
    for f in sorted(os.listdir(corpus)):
        if not f.lower().endswith((".heic", ".heif")):
            continue
        name = os.path.splitext(f)[0]
        ref = Image.open(os.path.join(corpus, f))
        ref.load()
        ref = np.asarray(ref.convert("RGB"), dtype=np.float64)
        png = os.path.join(outdir, name + ".png")
        if not os.path.exists(png):
            print(f"{name}: Openize.HEIC produced no image")
            worst = 255
            continue
        out = np.asarray(Image.open(png).convert("RGB"), dtype=np.float64)
        if ref.shape != out.shape:
            print(f"{name}: size {out.shape[1]}x{out.shape[0]}, libheif {ref.shape[1]}x{ref.shape[0]}")
            worst = 255
            continue
        diff = np.abs(ref - out)
        mse = (diff ** 2).mean()
        psnr = 10 * np.log10(255 ** 2 / mse) if mse else float("inf")
        worst = max(worst, diff.max())
        print(f"{name}: {out.shape[1]}x{out.shape[0]} PSNR {psnr:.1f} dB, max diff {diff.max():.0f}, "
              f"blockiness libheif {blockiness(ref):.3f} openize {blockiness(out):.3f}")
    return worst


if __name__ == "__main__":
    max_allowed = int(os.environ.get("MAX_DIFF", "2"))
    sys.exit(0 if main(sys.argv[1], sys.argv[2]) <= max_allowed else 1)
