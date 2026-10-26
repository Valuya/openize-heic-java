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

import java.util.Arrays;

/**
 * In-loop filters applied to a fully reconstructed intra picture: the deblocking filter (8.7.2) followed by sample
 * adaptive offset (8.7.3).
 *
 * <p>Only intra pictures are decoded, so every coding block is intra and the boundary filtering strength of every
 * transform block edge is 2.
 */
final class LoopFilter {
    static final byte VERTICAL_EDGE = 1;
    static final byte HORIZONTAL_EDGE = 2;
    static final byte NO_FILTER = 4;

    private static final int INTRA_BS = 2;

    // Table 8-12, indexed by Q
    private static final int[] BETA_TABLE = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 20, 22, 24, 26,
        28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50, 52, 54, 56, 58, 60, 62, 64
    };

    private static final int[] TC_TABLE = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4,
        4, 5, 5, 6, 6, 7, 8, 9, 10, 11, 13, 14, 16, 18, 20, 22, 24
    };

    // Table 8-10, qPi 30..43
    private static final int[] CHROMA_QP_TABLE = {29, 30, 31, 32, 33, 33, 34, 34, 35, 35, 36, 36, 37, 37};

    // Table 8-13, edge offset neighbour positions per SaoEoClass
    private static final int[][] SAO_H_POS = {{-1, 1}, {0, 0}, {-1, 1}, {1, -1}};
    private static final int[][] SAO_V_POS = {{0, 0}, {-1, 1}, {-1, 1}, {-1, 1}};

    private final HeicPicture picture;
    private final SeqParameterSetRbsp sps;
    private final PicParameterSetRbsp pps;
    private final int width;
    private final int height;
    private final int ctbLog2Size;
    private final int chromaCount;
    private final int subWidthC;
    private final int subHeightC;

    private final boolean highColorRange;
    private final byte[][] planes;
    private final short[][] highColorRangePlanes;
    private final int[] planeWidths;
    private final int widthInCtbs;
    // the slice header of each coding tree block, by raster scan address
    private final SliceHeader[] sliceHeaders;

    // SaoOffsetVal of the coding tree block, and the offset index of each band
    private final int[] offsetVal = new int[5];
    private final int[] bandTable = new int[32];

    private LoopFilter(HeicPicture picture) {
        this.picture = picture;
        this.sps = picture.sps;
        this.pps = picture.pps;
        this.width = sps.picWidthInLumaSamples;
        this.height = sps.picHeightInLumaSamples;
        this.ctbLog2Size = sps.getCtbLog2SizeY();
        this.chromaCount = sps.getChromaArrayType() != 0 ? 3 : 1;
        this.subWidthC = sps.getSubWidthC();
        this.subHeightC = sps.getSubHeightC();
        this.highColorRange = picture.highColorRange;
        this.planes = picture.planes;
        this.highColorRangePlanes = picture.highColorRangePlanes;
        this.planeWidths = picture.planeWidths;
        widthInCtbs = sps.getPicWidthInCtbsY();
        sliceHeaders = new SliceHeader[widthInCtbs * sps.getPicHeightInCtbsY()];
        for (int ctbAddrRs = 0; ctbAddrRs < sliceHeaders.length; ctbAddrRs++) {
            sliceHeaders[ctbAddrRs] = picture.getSliceSegmentHeaderAt(
                            (ctbAddrRs % widthInCtbs) << ctbLog2Size, (ctbAddrRs / widthInCtbs) << ctbLog2Size)
                    .slice;
        }
    }

    static void apply(HeicPicture picture) {
        boolean deblock = false;
        boolean sampleAdaptiveOffset = false;
        for (SliceSegmentHeader header : picture.getSliceSegmentHeaders()) {
            deblock |= !header.slice.sliceDeblockingFilterDisabledFlag();
            sampleAdaptiveOffset |= header.slice.sliceSaoLumaFlag() || header.slice.sliceSaoChromaFlag();
        }
        if (!deblock && !sampleAdaptiveOffset) {
            return;
        }

        LoopFilter filter = new LoopFilter(picture);

        if (deblock) {
            filter.deblock(true);
            filter.deblock(false);
        }
        if (sampleAdaptiveOffset) {
            filter.sampleAdaptiveOffset();
        }
    }

    // 8.7.2 Deblocking filter process: all vertical edges of the picture, then all horizontal ones

    private void deblock(boolean vertical) {
        byte edgeFlag = vertical ? VERTICAL_EDGE : HORIZONTAL_EDGE;
        int nx = vertical ? 1 : 0;
        int ny = vertical ? 0 : 1;

        // Luma: edges on the 8x8 grid, in segments of four lines
        for (int y = vertical ? 0 : 8; y < height; y += vertical ? 4 : 8) {
            for (int x = vertical ? 8 : 0; x < width; x += vertical ? 8 : 4) {
                if ((picture.blockFlags.get(x, y) & edgeFlag) == 0) {
                    continue;
                }

                int xp = x - nx;
                int yp = y - ny;
                SliceHeader header = sliceHeaderAt(x, y);
                if (!filterEdge(header, x, y, xp, yp)) {
                    continue;
                }

                int qpL = (picture.qpY.get(x, y) + picture.qpY.get(xp, yp) + 1) >> 1;
                filterLumaEdge(x, y, nx, ny, qpL, header, noFilter(xp, yp), noFilter(x, y));
            }
        }

        if (chromaCount == 1) {
            return;
        }

        // Chroma: edges on the 8x8 chroma sample grid, filtered line by line
        int widthC = width / subWidthC;
        int heightC = height / subHeightC;
        int bitDepthC = sps.getBitDepthC();

        for (int cIdx = 1; cIdx < chromaCount; cIdx++) {
            int cQpPicOffset = cIdx == 1 ? pps.ppsCbQpOffset : pps.ppsCrQpOffset;

            for (int yc = vertical ? 0 : 8; yc < heightC; yc += vertical ? 1 : 8) {
                for (int xc = vertical ? 8 : 0; xc < widthC; xc += vertical ? 8 : 1) {
                    int x = xc * subWidthC;
                    int y = yc * subHeightC;
                    if ((picture.blockFlags.get(x, y) & edgeFlag) == 0) {
                        continue;
                    }

                    int xp = x - nx;
                    int yp = y - ny;
                    SliceHeader header = sliceHeaderAt(x, y);
                    if (!filterEdge(header, x, y, xp, yp)) {
                        continue;
                    }

                    int qpi = ((picture.qpY.get(x, y) + picture.qpY.get(xp, yp) + 1) >> 1) + cQpPicOffset;
                    int qpC = sps.getChromaArrayType() == 1 ? chromaQp(qpi) : Math.min(qpi, 51);
                    int q = MathExtra.clip3(0, 53, qpC + 2 * (INTRA_BS - 1) + (header.sliceTcOffsetDiv2() << 1));
                    int tc = TC_TABLE[q] * (1 << (bitDepthC - 8));

                    filterChromaLine(cIdx, xc, yc, nx, ny, tc, bitDepthC, noFilter(xp, yp), noFilter(x, y));
                }
            }
        }
    }

    // filterEdgeFlag (8.7.2.3): picture borders are never reached by the loops above
    private boolean filterEdge(SliceHeader header, int xq, int yq, int xp, int yp) {
        if (header.sliceDeblockingFilterDisabledFlag()) {
            return false;
        }

        if (xq >> ctbLog2Size == xp >> ctbLog2Size && yq >> ctbLog2Size == yp >> ctbLog2Size) {
            return true;
        }
        int ctbQ = ctbAddrRs(xq, yq);
        int ctbP = ctbAddrRs(xp, yp);
        if (!pps.loopFilterAcrossTilesEnabledFlag && tileId(ctbQ) != tileId(ctbP)) {
            return false;
        }
        return header.sliceLoopFilterAcrossSlicesEnabledFlag() || sliceAddr(xq, yq) == sliceAddr(xp, yp);
    }

    // 8.7.2.5.3 Decision process for luma block edges, 8.7.2.5.7 luma sample filtering
    private void filterLumaEdge(
            int x, int y, int nx, int ny, int qpL, SliceHeader header, boolean noFilterP, boolean noFilterQ) {
        int bitDepth = sps.getBitDepthY();
        int beta =
                BETA_TABLE[MathExtra.clip3(0, 51, qpL + (header.sliceBetaOffsetDiv2() << 1))] * (1 << (bitDepth - 8));
        int tc = TC_TABLE[MathExtra.clip3(0, 53, qpL + 2 * (INTRA_BS - 1) + (header.sliceTcOffsetDiv2() << 1))]
                * (1 << (bitDepth - 8));

        // the steps across the edge and along it, in the plane
        int planeWidth = planeWidths[0];
        int across = nx + ny * planeWidth;
        int along = ny + nx * planeWidth;
        int edgeIndex = y * planeWidth + x;

        // 8.7.2.5.3: the decisions read the lines 0 and 3 only, and most edges stop there
        int line3 = edgeIndex + 3 * along;
        int dp0 = secondDifference(edgeIndex - across, -across);
        int dp3 = secondDifference(line3 - across, -across);
        int dq0 = secondDifference(edgeIndex, across);
        int dq3 = secondDifference(line3, across);
        int dpq0 = dp0 + dq0;
        int dpq3 = dp3 + dq3;
        int d = dpq0 + dpq3;
        if (d >= beta) {
            return;
        }

        boolean strong = strongDecision(edgeIndex, across, 2 * dpq0, beta, tc)
                && strongDecision(line3, across, 2 * dpq3, beta, tc);
        boolean dEp = dp0 + dp3 < ((beta + (beta >> 1)) >> 3);
        boolean dEq = dq0 + dq3 < ((beta + (beta >> 1)) >> 3);
        int maxValue = (1 << bitDepth) - 1;

        for (int k = 0; k < 4; k++) {
            int lineIndex = edgeIndex + k * along;
            int p0 = get(0, lineIndex - across);
            int p1 = get(0, lineIndex - 2 * across);
            int p2 = get(0, lineIndex - 3 * across);
            int q0 = get(0, lineIndex);
            int q1 = get(0, lineIndex + across);
            int q2 = get(0, lineIndex + 2 * across);
            int np0 = p0;
            int np1 = p1;
            int np2 = p2;
            int nq0 = q0;
            int nq1 = q1;
            int nq2 = q2;

            if (strong) {
                int p3 = get(0, lineIndex - 4 * across);
                int q3 = get(0, lineIndex + 3 * across);
                np0 = MathExtra.clip3(p0 - 2 * tc, p0 + 2 * tc, (p2 + 2 * p1 + 2 * p0 + 2 * q0 + q1 + 4) >> 3);
                np1 = MathExtra.clip3(p1 - 2 * tc, p1 + 2 * tc, (p2 + p1 + p0 + q0 + 2) >> 2);
                np2 = MathExtra.clip3(p2 - 2 * tc, p2 + 2 * tc, (2 * p3 + 3 * p2 + p1 + p0 + q0 + 4) >> 3);
                nq0 = MathExtra.clip3(q0 - 2 * tc, q0 + 2 * tc, (p1 + 2 * p0 + 2 * q0 + 2 * q1 + q2 + 4) >> 3);
                nq1 = MathExtra.clip3(q1 - 2 * tc, q1 + 2 * tc, (p0 + q0 + q1 + q2 + 2) >> 2);
                nq2 = MathExtra.clip3(q2 - 2 * tc, q2 + 2 * tc, (p0 + q0 + q1 + 3 * q2 + 2 * q3 + 4) >> 3);
            } else {
                int delta = (9 * (q0 - p0) - 3 * (q1 - p1) + 8) >> 4;
                if (Math.abs(delta) >= tc * 10) {
                    continue;
                }

                delta = MathExtra.clip3(-tc, tc, delta);
                np0 = MathExtra.clip3(0, maxValue, p0 + delta);
                nq0 = MathExtra.clip3(0, maxValue, q0 - delta);

                if (dEp) {
                    int deltaP = MathExtra.clip3(-(tc >> 1), tc >> 1, (((p2 + p0 + 1) >> 1) - p1 + delta) >> 1);
                    np1 = MathExtra.clip3(0, maxValue, p1 + deltaP);
                }

                if (dEq) {
                    int deltaQ = MathExtra.clip3(-(tc >> 1), tc >> 1, (((q2 + q0 + 1) >> 1) - q1 - delta) >> 1);
                    nq1 = MathExtra.clip3(0, maxValue, q1 + deltaQ);
                }
            }

            if (!noFilterP) {
                set(0, lineIndex - across, np0);
                set(0, lineIndex - 2 * across, np1);
                set(0, lineIndex - 3 * across, np2);
            }
            if (!noFilterQ) {
                set(0, lineIndex, nq0);
                set(0, lineIndex + across, nq1);
                set(0, lineIndex + 2 * across, nq2);
            }
        }
    }

    // |s0 - 2 * s1 + s2| of the luma samples at index, index + step and index + 2 * step: dp or dq of a line
    private int secondDifference(int index, int step) {
        return Math.abs(get(0, index) - 2 * get(0, index + step) + get(0, index + 2 * step));
    }

    // 8.7.2.5.6 Decision process for a luma sample, of the line whose q0 is at index
    private boolean strongDecision(int index, int across, int dpq, int beta, int tc) {
        int p0 = get(0, index - across);
        int q0 = get(0, index);
        return dpq < (beta >> 2)
                && Math.abs(get(0, index - 4 * across) - p0) + Math.abs(q0 - get(0, index + 3 * across)) < (beta >> 3)
                && Math.abs(p0 - q0) < ((5 * tc + 1) >> 1);
    }

    // 8.7.2.5.8 Filtering process for a chroma sample
    private void filterChromaLine(
            int cIdx, int xc, int yc, int nx, int ny, int tc, int bitDepth, boolean noFilterP, boolean noFilterQ) {
        int planeWidth = planeWidths[cIdx];
        int across = nx + ny * planeWidth;
        int q0Index = yc * planeWidth + xc;
        int p0 = get(cIdx, q0Index - across);
        int p1 = get(cIdx, q0Index - 2 * across);
        int q0 = get(cIdx, q0Index);
        int q1 = get(cIdx, q0Index + across);

        int delta = MathExtra.clip3(-tc, tc, ((((q0 - p0) << 2) + p1 - q1 + 4) >> 3));
        int maxValue = (1 << bitDepth) - 1;

        if (!noFilterP) {
            set(cIdx, q0Index - across, MathExtra.clip3(0, maxValue, p0 + delta));
        }
        if (!noFilterQ) {
            set(cIdx, q0Index, MathExtra.clip3(0, maxValue, q0 - delta));
        }
    }

    private static int chromaQp(int qpi) {
        if (qpi < 30) {
            return qpi;
        }
        return qpi > 43 ? qpi - 6 : CHROMA_QP_TABLE[qpi - 30];
    }

    // 8.7.3 Sample adaptive offset process, reading the deblocked picture

    private void sampleAdaptiveOffset() {
        int widthInCtbs = sps.getPicWidthInCtbsY();
        int heightInCtbs = sps.getPicHeightInCtbsY();
        for (int cIdx = 0; cIdx < chromaCount; cIdx++) {
            DeblockedBand band = new DeblockedBand(cIdx);
            for (int ry = 0; ry < heightInCtbs; ry++) {
                band.load(ry);
                for (int rx = 0; rx < widthInCtbs; rx++) {
                    SliceHeader header = sliceHeaderAt(rx << ctbLog2Size, ry << ctbLog2Size);
                    boolean enabled = cIdx == 0 ? header.sliceSaoLumaFlag() : header.sliceSaoChromaFlag();
                    int saoType = picture.saoParameters[
                            picture.saoIndex(cIdx, ry * widthInCtbs + rx) + HeicPicture.SAO_TYPE_IDX];
                    if (enabled && saoType != 0) {
                        saoCtb(rx, ry, cIdx, saoType, band);
                    }
                }
            }
        }
    }

    /**
     * The deblocked samples of a row of coding tree blocks of one component, with the line above and the line below:
     * sample adaptive offset reads deblocked samples while it modifies the picture in place, a row at a time.
     */
    private final class DeblockedBand {
        private final int cIdx;
        private final int componentWidth;
        private final int componentHeight;
        private final int ctbHeight;
        // [(y - firstLine) * componentWidth + x]
        private final int[] samples;
        // the deblocked last line of the previous row, which sample adaptive offset has modified since
        private final int[] lineAbove;
        private int firstLine;

        DeblockedBand(int cIdx) {
            this.cIdx = cIdx;
            componentWidth = width / (cIdx == 0 ? 1 : subWidthC);
            componentHeight = height / (cIdx == 0 ? 1 : subHeightC);
            ctbHeight = (1 << ctbLog2Size) / (cIdx == 0 ? 1 : subHeightC);
            DecoderWorkspace workspace = picture.workspace;
            samples = workspace.ints(DecoderWorkspace.DEBLOCKED_BAND + cIdx, componentWidth * (ctbHeight + 2));
            lineAbove = workspace.ints(DecoderWorkspace.DEBLOCKED_LINE_ABOVE + cIdx, componentWidth);
        }

        // before the row ry is modified
        void load(int ry) {
            int yCtb = ry * ctbHeight;
            firstLine = yCtb - 1;
            int lastLine = Math.min(yCtb + ctbHeight, componentHeight - 1);
            int rowLastLine = Math.min(yCtb + ctbHeight - 1, componentHeight - 1);
            System.arraycopy(lineAbove, 0, samples, 0, componentWidth);
            int planeWidth = planeWidths[cIdx];
            for (int y = yCtb; y <= lastLine; y++) {
                int lineStart = (y - firstLine) * componentWidth;
                int planeStart = y * planeWidth;
                if (highColorRange) {
                    short[] plane = highColorRangePlanes[cIdx];
                    for (int x = 0; x < componentWidth; x++) {
                        samples[lineStart + x] = plane[planeStart + x] & 0xFFFF;
                    }
                } else {
                    byte[] plane = planes[cIdx];
                    for (int x = 0; x < componentWidth; x++) {
                        samples[lineStart + x] = plane[planeStart + x] & 0xFF;
                    }
                }
            }
            System.arraycopy(samples, (rowLastLine - firstLine) * componentWidth, lineAbove, 0, componentWidth);
        }

        int sample(int x, int y) {
            return samples[(y - firstLine) * componentWidth + x];
        }
    }

    // 8.7.3.2 CTB modification process
    private void saoCtb(int rx, int ry, int cIdx, int saoType, DeblockedBand deblocked) {
        int subW = cIdx == 0 ? 1 : subWidthC;
        int subH = cIdx == 0 ? 1 : subHeightC;
        int bitDepth = cIdx == 0 ? sps.getBitDepthY() : sps.getBitDepthC();
        int maxValue = (1 << bitDepth) - 1;
        int compWidth = width / subW;
        int compHeight = height / subH;
        int ctbWidth = (1 << ctbLog2Size) / subW;
        int ctbHeight = (1 << ctbLog2Size) / subH;
        int xCtb = rx * ctbWidth;
        int yCtb = ry * ctbHeight;
        int xEnd = Math.min(xCtb + ctbWidth, compWidth);
        int yEnd = Math.min(yCtb + ctbHeight, compHeight);

        int log2OffsetScale =
                cIdx == 0 ? pps.ppsRangeExt.log2SaoOffsetScaleLuma : pps.ppsRangeExt.log2SaoOffsetScaleChroma;

        // SaoOffsetVal (7.4.9.3)
        int[] sao = picture.saoParameters;
        int saoIndex = picture.saoIndex(cIdx, ry * sps.getPicWidthInCtbsY() + rx);
        int[] offsetVal = this.offsetVal;
        for (int i = 0; i < 4; i++) {
            offsetVal[i + 1] = sao[saoIndex + HeicPicture.SAO_OFFSETS + i] << log2OffsetScale;
        }

        int bandShift = bitDepth - 5;
        int eoClass = sao[saoIndex + HeicPicture.SAO_EO_CLASS];
        // band offset: the offset index of each of the 32 bands, 0 outside the four bands from sao_band_position
        int[] bandTable = this.bandTable;
        if (saoType == 1) {
            Arrays.fill(bandTable, 0);
            int bandPosition = sao[saoIndex + HeicPicture.SAO_BAND_POSITION];
            for (int k = 0; k < 4; k++) {
                bandTable[(k + bandPosition) & 31] = k + 1;
            }
        }

        int currentCtb = ctbAddrRs(rx << ctbLog2Size, ry << ctbLog2Size);

        for (int ys = yCtb; ys < yEnd; ys++) {
            for (int xs = xCtb; xs < xEnd; xs++) {
                if (noFilter(xs * subW, ys * subH)) {
                    continue;
                }

                int sample = deblocked.sample(xs, ys);
                int offset;

                if (saoType == 1) {
                    offset = offsetVal[bandTable[sample >> bandShift]];
                } else {
                    int edgeIdx = 2;
                    boolean skip = false;
                    for (int k = 0; k < 2; k++) {
                        int xn = xs + SAO_H_POS[eoClass][k];
                        int yn = ys + SAO_V_POS[eoClass][k];
                        if (xn < 0
                                || yn < 0
                                || xn >= compWidth
                                || yn >= compHeight
                                || !saoNeighbourUsable(currentCtb, xn * subW, yn * subH)) {
                            skip = true;
                            break;
                        }
                        edgeIdx += Integer.signum(sample - deblocked.sample(xn, yn));
                    }

                    if (skip) {
                        continue;
                    }
                    if (edgeIdx <= 2) {
                        edgeIdx = edgeIdx == 2 ? 0 : edgeIdx + 1;
                    }

                    offset = offsetVal[edgeIdx];
                }

                if (offset == 0) {
                    continue;
                }

                set(cIdx, xs, ys, MathExtra.clip3(0, maxValue, sample + offset));
            }
        }
    }

    // A neighbouring sample across a slice or tile boundary that filtering may not cross leaves the sample unmodified
    private boolean saoNeighbourUsable(int currentCtb, int xn, int yn) {
        int neighbourCtb = ctbAddrRs(xn, yn);
        if (neighbourCtb == currentCtb) {
            return true;
        }
        if (!pps.loopFilterAcrossTilesEnabledFlag && tileId(neighbourCtb) != tileId(currentCtb)) {
            return false;
        }

        int currentSlice = sliceAddrOfCtb(currentCtb);
        int neighbourSlice = sliceAddrOfCtb(neighbourCtb);
        if (currentSlice == neighbourSlice) {
            return true;
        }

        // the later slice in decoding order owns the boundary between the two
        boolean neighbourFirst = pps.getCtbAddrRsToTs()[neighbourSlice] < pps.getCtbAddrRsToTs()[currentSlice];
        int widthInCtbs = sps.getPicWidthInCtbsY();
        int laterCtb = neighbourFirst ? currentCtb : neighbourCtb;
        SliceHeader later =
                sliceHeaderAt((laterCtb % widthInCtbs) << ctbLog2Size, (laterCtb / widthInCtbs) << ctbLog2Size);
        return later.sliceLoopFilterAcrossSlicesEnabledFlag();
    }

    private boolean noFilter(int x, int y) {
        return (picture.blockFlags.get(x, y) & NO_FILTER) != 0
                || (sps.pcmLoopFilterDisabledFlag && picture.pcmFlag.get(x, y));
    }

    private SliceHeader sliceHeaderAt(int x, int y) {
        return sliceHeaders[ctbAddrRs(x, y)];
    }

    private int ctbAddrRs(int x, int y) {
        return (y >> ctbLog2Size) * widthInCtbs + (x >> ctbLog2Size);
    }

    private int tileId(int ctbAddrRs) {
        return pps.getTileIdByCtbAddrTs()[pps.getCtbAddrRsToTs()[ctbAddrRs]];
    }

    private int sliceAddr(int x, int y) {
        return picture.getSliceAddrRs(picture.ctbAddrRsAt(x, y));
    }

    private int sliceAddrOfCtb(int ctbAddrRs) {
        return picture.getSliceAddrRs(ctbAddrRs);
    }

    private int get(int cIdx, int x, int y) {
        return get(cIdx, y * planeWidths[cIdx] + x);
    }

    private int get(int cIdx, int index) {
        return highColorRange ? highColorRangePlanes[cIdx][index] & 0xFFFF : planes[cIdx][index] & 0xFF;
    }

    private void set(int cIdx, int x, int y, int value) {
        set(cIdx, y * planeWidths[cIdx] + x, value);
    }

    private void set(int cIdx, int index, int value) {
        if (highColorRange) {
            highColorRangePlanes[cIdx][index] = (short) value;
        } else {
            planes[cIdx][index] = (byte) value;
        }
    }
}
