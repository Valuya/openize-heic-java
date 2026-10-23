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



public enum CabacType
{
    saoMergeFlag,
    saoTypeIdx,
    splitCuFlag,
    cuTransquantBypassFlag,
    cuSkipFlag,
    paletteModeFlag,
    predModeFlag,
    partMode,
    prevIntraLumaPredFlag,
    intraChromaPredMode,
    rqtRootCbf,
    mergeFlag,
    mergeIdx,
    InterPredIdc,
    refIdx,
    mvpFlag,
    splitTransformFlag,
    cbfLuma,
    cbfChroma,
    absMvdGreater0Flag,
    absMvdGreater1Flag,
    tuResidualActFlag,
    log2ResScaleAbsPlus1,
    resScaleSignFlag,
    transformSkipFlag,
    explicitRdpcmFlag,
    explicitRdpcmDirFlag,
    lastSigCoeffXPrefix,
    lastSigCoeffYPrefix,
    codedSubBlockFlag,
    sigCoeffFlag,
    coeffAbsLevelGreater1Flag,
    coeffAbsLevelGreater2Flag,
    paletteRunPrefix,
    copyAbovePaletteIndicesFlag,
    copyAboveIndicesForFinalRunFlag,
    paletteTransposeFlag,
    cuQpDeltaAbs,
    cuChromaQpOffsetFlag,
    cuChromaQpOffsetIdx;

    public static CabacType[] getValues()
    {
        return values;
    }

    private static final CabacType[] values = CabacType.values();
}


