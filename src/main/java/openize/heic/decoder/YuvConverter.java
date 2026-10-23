/*
 * Openize.HEIC
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of Openize.HEIC.
 *
 * Openize.HEIC is available under Openize license, which is
 * available along with Openize.HEIC sources.
 */

package openize.heic.decoder;

import openize.MathUtils;

/**
 * <p>
 * Class used to convert colors from YUV colorspace to RGB.
 * </p>
 */
class YuvConverter {
    /**
     * <p>
     * Full or TV range flag.
     * </p>
     */
    private static final double TV_RANGE_LUMA_SCALE = 255.0 / 219;

    private static final double TV_RANGE_CHROMA_SCALE = 255.0 / 224;

    private final SeqParameterSetRbsp sps;
    private final boolean fullRangeFlag;

    private double cooffCrToR;
    private double cooffCrToG;
    private double cooffCbToG;
    private double cooffCbToB;

    YuvConverter(SeqParameterSetRbsp sps) {
        this.sps = sps;
        fullRangeFlag = sps.VuiParameters.videoFullRangeFlag;
        defineCoefficients(sps.VuiParameters);
    }

    /**
     * Converts the kept part of a decoded picture to 8-bit ARGB, opaque. With {@code alphaOnly}, the picture is an
     * alpha plane: only the alpha byte of the target pixels is set, to the value the red channel would have.
     */
    void convert(HeicPicture picture, PixelTarget target, boolean alphaOnly) {
        int width = (int) sps.picWidthInLumaSamples;
        int height = (int) sps.picHeightInLumaSamples;
        PixelBox convertedBox = target.clip().intersect(PixelBox.ofSize(width, height));

        int bitDepthY = sps.getBitDepthY() & 0xFF;
        int bitDepthC = sps.getBitDepthC() & 0xFF;
        boolean highColorRange = bitDepthY > 8 || bitDepthC > 8;
        boolean monochrome = sps.getChromaArrayType() == 0;
        int subWidthC = sps.getSubWidthC() & 0xFF;
        int subHeightC = sps.getSubHeightC() & 0xFF;
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
                double luma = sample(picture, highColorRange, 0, col, row);
                if (!fullRangeFlag) {
                    luma = TV_RANGE_LUMA_SCALE * (luma - lumaOffset);
                }

                double red;
                double green;
                double blue;
                if (monochrome) {
                    red = luma;
                    green = luma;
                    blue = luma;
                } else {
                    double cb = sample(picture, highColorRange, 1, col / subWidthC, row / subHeightC) - chromaHalfRange;
                    double cr = sample(picture, highColorRange, 2, col / subWidthC, row / subHeightC) - chromaHalfRange;
                    if (!fullRangeFlag) {
                        cb = TV_RANGE_CHROMA_SCALE * cb;
                        cr = TV_RANGE_CHROMA_SCALE * cr;
                    }

                    double scaledLuma = luma * lumaBitScale;
                    double scaledCb = cb * chromaBitScale;
                    double scaledCr = cr * chromaBitScale;
                    red = scaledLuma + cooffCrToR * scaledCr;
                    green = scaledLuma - cooffCrToG * scaledCr - cooffCbToG * scaledCb;
                    blue = scaledLuma + cooffCbToB * scaledCb;
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

    private static int sample(HeicPicture picture, boolean highColorRange, int cIdx, int x, int y) {
        return highColorRange ? picture.pixelsHighColorRange[cIdx][x][y] & 0xFFFF : picture.pixels[cIdx][x][y] & 0xFF;
    }

    private static int to8Bits(double channel) {
        return MathExtra.clip3(0, 255, MathUtils.f64S32(channel));
    }

    private void defineCoefficients(VuiParameters VuiParameters) {
        // vui_parameters.video_full_range_flag;
        // vui_parameters.colour_primaries;
        // vui_parameters.transfer_characteristics;
        // vui_parameters.matrix_coeffs;

        double kr;
        double kb;
        switch (VuiParameters.matrixCoeffs) {
            case 1:
                kr = 0.2126;
                kb = 0.0722;
                break;
            case 4:
                kr = 0.30;
                kb = 0.11;
                break;
            case 5:
            case 6:
                kr = 0.299;
                kb = 0.114;
                break;
            case 7:
                kr = 0.212;
                kb = 0.087;
                break;
            case 9:
            case 10:
                kr = 0.2627;
                kb = 0.0593;
                break;

            case 12:
            case 13:
                ColorPrimaries p = ColorPrimaries.getSpecified(VuiParameters.colourPrimaries & 0xFF);

                double zR = 1 - (p.xR + p.yR);
                double zG = 1 - (p.xG + p.yG);
                double zB = 1 - (p.xB + p.yB);
                double zW = 1 - (p.xW + p.yW);

                final double tmpGmBBmG = p.yG * zB - p.yB * zG;
                final double tmpRmGGmR = p.yR * zG - p.yG * zR;
                double denom = p.yW * (p.xR * tmpGmBBmG + p.xG * (p.yB * zR - p.yR * zB) + p.xB * tmpRmGGmR);

                try {
                    kr = (p.yR * (p.xW * tmpGmBBmG + p.yW * (p.xB * zG - p.xG * zB) + zW * (p.xG * p.yB - p.xB * p.yG)))
                            / denom;
                    kb = (p.yB * (p.xW * tmpRmGGmR + p.yW * (p.xG * zR - p.xR * zG) + zW * (p.xR * p.yG - p.xG * p.yR)))
                            / denom;
                } catch (java.lang.RuntimeException e) {
                    kr = 0.299;
                    kb = 0.114;
                }
                break;

            case 0: // Identity; See Equations E-31 to E-33
            case 2: // Unspecified
            case 3: // Reserved
            case 8: // YCgCo;    See Equations E-28 to E-30
            case 11: // Y′D′ZD′X; See Equations E-59 to E-61
            case 14: // ICTCP
            default:
                kr = 0.299;
                kb = 0.114;
                break;
        }

        double kg = 1 - kr - kb;

        cooffCrToR = 2 * (1 - kr);
        cooffCrToG = 2 * kr * (1 - kr) / kg;
        cooffCbToG = 2 * kb * (1 - kb) / kg;
        cooffCbToB = 2 * (1 - kb);
    }

    static class ColorPrimaries {
        public final double xG;
        public final double yG;
        public final double xB;
        public final double yB;
        public final double xR;
        public final double yR;
        public final double xW;
        public final double yW;

        public ColorPrimaries(double xG, double yG, double xB, double yB, double xR, double yR, double xW, double yW) {
            this.xG = xG;
            this.yG = yG;
            this.xB = xB;
            this.yB = yB;
            this.xR = xR;
            this.yR = yR;
            this.xW = xW;
            this.yW = yW;
        }

        public static ColorPrimaries getSpecified(int colourPrimaries) {
            switch (colourPrimaries) {
                case 1:
                    return new ColorPrimaries(0.300, 0.600, 0.150, 0.060, 0.640, 0.330, 0.3127, 0.3290);
                case 4:
                    return new ColorPrimaries(0.210, 0.710, 0.140, 0.080, 0.670, 0.330, 0.3100, 0.3160);
                case 5:
                    return new ColorPrimaries(0.290, 0.600, 0.150, 0.060, 0.640, 0.330, 0.3127, 0.3290);
                case 6:
                case 7:
                    return new ColorPrimaries(0.310, 0.595, 0.155, 0.070, 0.630, 0.340, 0.3127, 0.3290);
                case 8:
                    return new ColorPrimaries(0.243, 0.692, 0.145, 0.049, 0.681, 0.319, 0.3100, 0.3160);
                case 9:
                    return new ColorPrimaries(0.170, 0.797, 0.131, 0.046, 0.708, 0.292, 0.3127, 0.3290);
                case 10:
                    return new ColorPrimaries(0.000, 1.000, 0.000, 0.000, 1.000, 0.000, 0.333333, 0.33333);
                case 11:
                    return new ColorPrimaries(0.265, 0.690, 0.150, 0.060, 0.680, 0.320, 0.3140, 0.3510);
                case 12:
                    return new ColorPrimaries(0.265, 0.690, 0.150, 0.060, 0.680, 0.320, 0.3127, 0.3290);
                case 22:
                    return new ColorPrimaries(0.295, 0.605, 0.155, 0.077, 0.630, 0.340, 0.3127, 0.3290);
                case 2: // Unspecified
                default: // Reserved
                    throw new UnsupportedOperationException("Unsupported colourPrimaries = " + colourPrimaries);
            }
        }
    }
}
