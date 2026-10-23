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
import openize.heic.decoder.io.BitStreamWithNalSupport;


class PaletteCoding
{
    private final boolean[] palettePredictorEntryReuseFlags;
    int numSignalledPaletteEntries;
    int numPaletteIndices;
    boolean copyAboveIndicesForFinalRunFlag;
    boolean paletteTransposeFlag;
    boolean paletteEscapeValPresentFlag;
    int paletteIdxIdc;
    boolean copyAbovePaletteIndicesFlag;
    int paletteRunPrefix;
    int paletteRunSuffix;
    int paletteEscapeValue;
    //final int[][] new_palette_entries;
    private int numPredictedPaletteEntries;
    private int predictorPaletteSize; // IS NOT DEFINED
    private int[] paletteIndexIdc;
    private int paletteMaxRun;
    private int paletteRun;
    private int runToEnd;
    private int[][] paletteIndexMap;
    private int[][][] paletteEscapeVal;
    private boolean[][] copyAboveIndicesFlag;
    private int currPaletteIndex;

    public PaletteCoding(BitStreamWithNalSupport stream, SliceSegmentHeader header,
                          int x0, int y0, int nCbS)
    {
        boolean palettePredictionFinished = false;
        numPredictedPaletteEntries = 0;
        palettePredictorEntryReuseFlags = new boolean[getPredictorPaletteSize()];

        for (int predictorEntryIdx = 0; predictorEntryIdx < getPredictorPaletteSize() &&
                !palettePredictionFinished && numPredictedPaletteEntries < (header.pps.sps.spsSccExt.paletteMaxSize & 0xFFFFFFFFL);
             predictorEntryIdx++)
        {
            int palettePredictorRun = stream.readAev(); // ae(v)
            if (palettePredictorRun != 1)
            {
                if (palettePredictorRun > 1)
                {
                    predictorEntryIdx += palettePredictorRun - 1;
                }

                getPalettePredictorEntryReuseFlags()[predictorEntryIdx] = true;
                numPredictedPaletteEntries++;
            }
            else
            {
                palettePredictionFinished = true;
            }
        }

        if (numPredictedPaletteEntries < (header.pps.sps.spsSccExt.paletteMaxSize & 0xFFFFFFFFL))
        {
            numSignalledPaletteEntries = stream.readAev(); // ae(v)
        }

        int numComps = ((header.pps.sps.getChromaArrayType() & 0xFFFFFFFFL) == 0) ? 1 : 3;

        //new_palette_entries = new int[numComps][];
        for (int cIdx = 0; cIdx < numComps; cIdx++)
        {
            //new_palette_entries[cIdx] = new int[num_signalled_palette_entries];

            for (int i = 0; i < numSignalledPaletteEntries; i++)
            {
                /*new_palette_entries[cIdx][i] =*/ stream.readAev(); // ae(v)
            }
        }

        if (getCurrentPaletteSize() != 0)
        {
            paletteEscapeValPresentFlag = stream.readAevFlag(); // ae(v)
        }

        if (getMaxPaletteIndex() > 0)
        {
            numPaletteIndices = stream.readAev() + 1; // ae(v)
            int adjust = 0;
            setPaletteIndexIdc(new int[numPaletteIndices - 1]);
            for (int i = 0; i <= numPaletteIndices - 1; i++)
            {
                if (getMaxPaletteIndex() - adjust > 0)
                {
                    paletteIdxIdc = stream.readAev(); // ae(v)
                    getPaletteIndexIdc()[i] = paletteIdxIdc;
                }
                adjust = 1;
            }
            copyAboveIndicesForFinalRunFlag = stream.readAevFlag(); // ae(v)
            paletteTransposeFlag = stream.readAevFlag(); // ae(v)
        }

     /*   if (palette_escape_val_present_flag)
        {
            //new delta_qp();
            //if (!cu_transquant_bypass_flag)
            //    new chroma_qp_offset();
        }*/

        int remainingNumIndices = numPaletteIndices;
        int paletteScanPos = 0;
        int log2BlockSize = MathUtils.f64S32(MathUtils.log(nCbS, 2));

        while (paletteScanPos < nCbS * nCbS)
        {
            int xC = x0 + (Scans.getScanOrder()[log2BlockSize][3][paletteScanPos][0] & 0xFF);
            int yC = y0 + (Scans.getScanOrder()[log2BlockSize][3][paletteScanPos][1] & 0xFF);

            int xcPrev = 0;
            int ycPrev = 0;

            if (paletteScanPos > 0)
            {
                xcPrev = x0 + (Scans.getScanOrder()[log2BlockSize][3][paletteScanPos - 1][0] & 0xFF);
                ycPrev = y0 + (Scans.getScanOrder()[log2BlockSize][3][paletteScanPos - 1][1] & 0xFF);
            }

            setPaletteRun(nCbS * nCbS - paletteScanPos);
            setRunToEnd(1);
            getCopyAboveIndicesFlag()[xC][yC] = false;

            if (getMaxPaletteIndex() > 0)
            {
                if (paletteScanPos >= nCbS && !getCopyAboveIndicesFlag()[xcPrev][ycPrev])
                {
                    if (remainingNumIndices > 0 && paletteScanPos < nCbS * nCbS - 1)
                    {
                        copyAbovePaletteIndicesFlag = stream.readAevFlag(); // ae(v)
                        getCopyAboveIndicesFlag()[xC][yC] = copyAbovePaletteIndicesFlag;
                    }
                    else
                    {
                        getCopyAboveIndicesFlag()[xC][yC] = paletteScanPos != nCbS * nCbS - 1 || remainingNumIndices <= 0;
                    }
                }
            }

            if (!getCopyAboveIndicesFlag()[xC][yC])
            {
                int currNumIndices = numPaletteIndices - remainingNumIndices;
                setCurrPaletteIndex(getPaletteIndexIdc()[currNumIndices]);
            }

            if (getMaxPaletteIndex() > 0)
            {
                if (!getCopyAboveIndicesFlag()[xC][yC])
                {
                    remainingNumIndices -= 1;
                }

                if (remainingNumIndices > 0 ||
                        getCopyAboveIndicesFlag()[xC][yC] != copyAboveIndicesForFinalRunFlag)
                {
                    setPaletteMaxRun(nCbS * nCbS - paletteScanPos - remainingNumIndices -
                            (copyAboveIndicesForFinalRunFlag ? 1 : 0));
                    setRunToEnd(0);

                    if (getPaletteMaxRun() - 1 > 0)
                    {
                        paletteRunPrefix = stream.readAev(); // ae(v)
                        if ((paletteRunPrefix > 1) && (getPaletteMaxRun() - 1 !=
                                (1 << (paletteRunPrefix - 1))))
                        {
                            paletteRunSuffix = stream.readAev(); // ae(v)
                        }
                    }
                }
            }

            int runPos = 0;
            while (runPos <= getPaletteRun() - 1)
            {
                int xR = x0 + (Scans.getScanOrder()[log2BlockSize][3][paletteScanPos][0] & 0xFF);
                int yR = y0 + (Scans.getScanOrder()[log2BlockSize][3][paletteScanPos][1] & 0xFF);

                if (!getCopyAboveIndicesFlag()[xC][yC])
                {
                    getCopyAboveIndicesFlag()[xR][yR] = false;
                    getPaletteIndexMap()[xR][yR] = getCurrPaletteIndex();
                }
                else
                {
                    getCopyAboveIndicesFlag()[xR][yR] = true;
                    getPaletteIndexMap()[xR][yR] = getPaletteIndexMap()[xR][yR - 1];
                }

                runPos++;
                paletteScanPos++;
            }
        }

        if (paletteEscapeValPresentFlag)
        {
            for (int cIdx = 0; cIdx < numComps; cIdx++)
            {
                for (int sPos = 0; sPos < nCbS * nCbS; sPos++)
                {
                    int xC = x0 + (Scans.getScanOrder()[log2BlockSize][3][sPos][0] & 0xFF);
                    int yC = y0 + (Scans.getScanOrder()[log2BlockSize][3][sPos][1] & 0xFF);

                    if (getPaletteIndexMap()[xC][yC] == getMaxPaletteIndex())
                    {
                        if (cIdx == 0 ||
                                (xC % 2 == 0 && yC % 2 == 0 && (header.pps.sps.getChromaArrayType() & 0xFFFFFFFFL) == 1) ||
                                (xC % 2 == 0 && !paletteTransposeFlag && (header.pps.sps.getChromaArrayType() & 0xFFFFFFFFL) == 2) ||
                                (yC % 2 == 0 && paletteTransposeFlag && (header.pps.sps.getChromaArrayType() & 0xFFFFFFFFL) == 2) ||
                                (header.pps.sps.getChromaArrayType() & 0xFFFFFFFFL) == 3)
                        {
                            paletteEscapeValue = stream.readAev(); // ae(v)
                            getPaletteEscapeVal()[cIdx][xC][yC] = paletteEscapeValue;
                        }
                    }
                }
            }
        }
    }

    public final int getNumPredictedPaletteEntries()
    {
        return numPredictedPaletteEntries;
    }

    public final int getPredictorPaletteSize()
    {
        return predictorPaletteSize;
    }

    public final boolean[] getPalettePredictorEntryReuseFlags()
    {
        return palettePredictorEntryReuseFlags;
    }

    public final int[] getPaletteIndexIdc()
    {
        return paletteIndexIdc;
    }

    final void setPaletteIndexIdc(int[] value)
    {
        paletteIndexIdc = value;
    }

    public final int getPaletteMaxRun()
    {
        return paletteMaxRun;
    }

    final void setPaletteMaxRun(int value)
    {
        paletteMaxRun = value;
    }

    public final int getPaletteRun()
    {
        return paletteRun;
    }

    final void setPaletteRun(int value)
    {
        paletteRun = value;
    }

    public final int getRunToEnd()
    {
        return runToEnd;
    }

    final void setRunToEnd(int value)
    {
        runToEnd = value;
    }

    public final int[][] getPaletteIndexMap()
    {
        return paletteIndexMap;
    }

    final void setPaletteIndexMap(int[][] value)
    {
        paletteIndexMap = value;
    }

    public final int[][][] getPaletteEscapeVal()
    {
        return paletteEscapeVal;
    }

    final void setPaletteEscapeVal(int[][][] value)
    {
        paletteEscapeVal = value;
    }

    public final boolean[][] getCopyAboveIndicesFlag()
    {
        return copyAboveIndicesFlag;
    }

    public final int getCurrPaletteIndex()
    {
        return currPaletteIndex;
    }

    final void setCurrPaletteIndex(int value)
    {
        currPaletteIndex = value;
    }

    final int getCurrentPaletteSize()
    {
        return numPredictedPaletteEntries + numSignalledPaletteEntries;
    }

    final int getMaxPaletteIndex()
    {
        return getCurrentPaletteSize() - 1 + (paletteEscapeValPresentFlag ? 1 : 0);
    }
}
