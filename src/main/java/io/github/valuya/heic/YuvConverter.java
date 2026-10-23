/*
 * Openize.HEIC
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of Openize.HEIC.
 *
 * Openize.HEIC is available under Openize license, which is
 * available along with Openize.HEIC sources.
 */

package io.github.valuya.heic;

/**
 * Converts decoded YCbCr samples to 8-bit RGB with the matrix coefficients and range of a {@link ColourDescription}.
 */
final class YuvConverter {
    /**
     * The colour description of an image (Annex E): its {@code nclx} colour information property when it has one,
     * which takes precedence, otherwise the video usability information of its sequence parameter set.
     */
    record ColourDescription(int colourPrimaries, int matrixCoefficients, boolean fullRangeFlag) {
        static ColourDescription of(VuiParameters vui) {
            return new ColourDescription(vui.colourPrimaries & 0xFF, vui.matrixCoeffs & 0xFF, vui.videoFullRangeFlag);
        }
    }

    // the scale of limited ("TV") range samples, 16..235 for luma and 16..240 for chroma at 8 bits
    private static final double TV_RANGE_LUMA_SCALE = 255.0 / 219;
    private static final double TV_RANGE_CHROMA_SCALE = 255.0 / 224;

    private final SeqParameterSetRbsp sps;
    private final boolean fullRangeFlag;
    private final double crToRed;
    private final double crToGreen;
    private final double cbToGreen;
    private final double cbToBlue;

    YuvConverter(SeqParameterSetRbsp sps, ColourDescription colour) {
        this.sps = sps;
        fullRangeFlag = colour.fullRangeFlag();

        LumaWeights weights = LumaWeights.of(colour.matrixCoefficients(), colour.colourPrimaries());
        double kg = 1 - weights.kr() - weights.kb();
        crToRed = 2 * (1 - weights.kr());
        crToGreen = 2 * weights.kr() * (1 - weights.kr()) / kg;
        cbToGreen = 2 * weights.kb() * (1 - weights.kb()) / kg;
        cbToBlue = 2 * (1 - weights.kb());
    }

    /**
     * Converts the kept part of a decoded picture to 8-bit ARGB, opaque. With {@code alphaOnly}, the picture is an
     * alpha plane: only the alpha byte of the target pixels is set, to the value the red channel would have.
     */
    void convert(HeicPicture picture, PixelTarget target, boolean alphaOnly) {
        int width = sps.picWidthInLumaSamples;
        int height = sps.picHeightInLumaSamples;
        PixelBox convertedBox = target.clip().intersect(PixelBox.ofSize(width, height));

        int bitDepthY = sps.getBitDepthY();
        int bitDepthC = sps.getBitDepthC();
        boolean monochrome = sps.getChromaArrayType() == 0;
        int subWidthC = sps.getSubWidthC();
        int subHeightC = sps.getSubHeightC();
        int chromaHalfRange = 1 << (bitDepthC - 1);
        int lumaOffset = 16 << (bitDepthY - 8);
        // scale to 8 bits
        double lumaBitScale = 256.0 / (1 << bitDepthY);
        double chromaBitScale = 256.0 / (1 << bitDepthC);

        int[] destination = target.destination();
        int stepX = target.stepX();
        int stepY = target.stepY();
        int base = target.base();

        for (int row = convertedBox.top(); row < convertedBox.bottom(); row++) {
            int rowIndex = base + row * stepY;
            for (int col = convertedBox.left(); col < convertedBox.right(); col++) {
                double luma = picture.getSample(0, col, row);
                if (!fullRangeFlag) {
                    luma = TV_RANGE_LUMA_SCALE * (luma - lumaOffset);
                }

                double red;
                double green;
                double blue;
                if (monochrome) {
                    red = luma * lumaBitScale;
                    green = red;
                    blue = red;
                } else {
                    double cb = picture.getSample(1, col / subWidthC, row / subHeightC) - chromaHalfRange;
                    double cr = picture.getSample(2, col / subWidthC, row / subHeightC) - chromaHalfRange;
                    if (!fullRangeFlag) {
                        cb = TV_RANGE_CHROMA_SCALE * cb;
                        cr = TV_RANGE_CHROMA_SCALE * cr;
                    }

                    double scaledLuma = luma * lumaBitScale;
                    double scaledCb = cb * chromaBitScale;
                    double scaledCr = cr * chromaBitScale;
                    red = scaledLuma + crToRed * scaledCr;
                    green = scaledLuma - crToGreen * scaledCr - cbToGreen * scaledCb;
                    blue = scaledLuma + cbToBlue * scaledCb;
                }

                int index = rowIndex + col * stepX;
                int red8 = to8Bits(red);
                if (alphaOnly) {
                    destination[index] = (destination[index] & 0x00FFFFFF) | (red8 << 24);
                } else {
                    destination[index] = 0xFF000000 | (red8 << 16) | (to8Bits(green) << 8) | to8Bits(blue);
                }
            }
        }
    }

    private static int to8Bits(double channel) {
        return MathExtra.clip3(0, 255, (int) channel);
    }

    /**
     * KR and KB of the matrix coefficients (Table E.5), derived from the colour primaries for the chromaticity-derived
     * matrices (E-22 to E-27). Unspecified and unsupported matrices fall back to those of BT.601.
     */
    private record LumaWeights(double kr, double kb) {
        private static final LumaWeights BT601 = new LumaWeights(0.299, 0.114);

        static LumaWeights of(int matrixCoeffs, int colourPrimaries) {
            return switch (matrixCoeffs) {
                case 1 -> new LumaWeights(0.2126, 0.0722);
                case 4 -> new LumaWeights(0.30, 0.11);
                case 5, 6 -> BT601;
                case 7 -> new LumaWeights(0.212, 0.087);
                case 9, 10 -> new LumaWeights(0.2627, 0.0593);
                case 12, 13 -> fromPrimaries(ColourPrimaries.of(colourPrimaries));
                // 0 identity, 8 YCgCo, 11 Y'D'zD'x, 14 ICtCp and the reserved values
                default -> BT601;
            };
        }

        private static LumaWeights fromPrimaries(ColourPrimaries p) {
            double zR = 1 - (p.xR() + p.yR());
            double zG = 1 - (p.xG() + p.yG());
            double zB = 1 - (p.xB() + p.yB());
            double zW = 1 - (p.xW() + p.yW());
            double greenBlue = p.yG() * zB - p.yB() * zG;
            double redGreen = p.yR() * zG - p.yG() * zR;
            double denominator =
                    p.yW() * (p.xR() * greenBlue + p.xG() * (p.yB() * zR - p.yR() * zB) + p.xB() * redGreen);
            double kr = p.yR()
                    * (p.xW() * greenBlue
                            + p.yW() * (p.xB() * zG - p.xG() * zB)
                            + zW * (p.xG() * p.yB() - p.xB() * p.yG()))
                    / denominator;
            double kb = p.yB()
                    * (p.xW() * redGreen
                            + p.yW() * (p.xG() * zR - p.xR() * zG)
                            + zW * (p.xR() * p.yG() - p.xG() * p.yR()))
                    / denominator;
            return Double.isFinite(kr) && Double.isFinite(kb) ? new LumaWeights(kr, kb) : BT601;
        }
    }

    /**
     * The chromaticity coordinates of the green, blue and red primaries and of the white point (Table E.3).
     */
    private record ColourPrimaries(
            double xG, double yG, double xB, double yB, double xR, double yR, double xW, double yW) {
        private static final ColourPrimaries BT709 =
                new ColourPrimaries(0.300, 0.600, 0.150, 0.060, 0.640, 0.330, 0.3127, 0.3290);

        static ColourPrimaries of(int colourPrimaries) {
            return switch (colourPrimaries) {
                case 4 -> new ColourPrimaries(0.210, 0.710, 0.140, 0.080, 0.670, 0.330, 0.3100, 0.3160);
                case 5 -> new ColourPrimaries(0.290, 0.600, 0.150, 0.060, 0.640, 0.330, 0.3127, 0.3290);
                case 6, 7 -> new ColourPrimaries(0.310, 0.595, 0.155, 0.070, 0.630, 0.340, 0.3127, 0.3290);
                case 8 -> new ColourPrimaries(0.243, 0.692, 0.145, 0.049, 0.681, 0.319, 0.3100, 0.3160);
                case 9 -> new ColourPrimaries(0.170, 0.797, 0.131, 0.046, 0.708, 0.292, 0.3127, 0.3290);
                case 10 -> new ColourPrimaries(0.000, 1.000, 0.000, 0.000, 1.000, 0.000, 0.333333, 0.33333);
                case 11 -> new ColourPrimaries(0.265, 0.690, 0.150, 0.060, 0.680, 0.320, 0.3140, 0.3510);
                case 12 -> new ColourPrimaries(0.265, 0.690, 0.150, 0.060, 0.680, 0.320, 0.3127, 0.3290);
                case 22 -> new ColourPrimaries(0.295, 0.605, 0.155, 0.077, 0.630, 0.340, 0.3127, 0.3290);
                // 1, and BT.709 too for 2 (unspecified) and the reserved values
                default -> BT709;
            };
        }
    }
}
