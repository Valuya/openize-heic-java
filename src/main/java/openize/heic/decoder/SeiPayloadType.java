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

enum SeiPayloadType
{
    bufferingPeriod, /*  = 0 */
    picTiming, /*  = 1 */
    panScanRect, /*  = 2 */
    fillerPayload, /*  = 3 */
    userDataRegisteredItuTT35, /*  = 4 */
    userDataUnregistered, /*  = 5 */
    recoveryPoint, /*  = 6 */
    sceneInfo, /*  = 9 */
    pictureSnapshot, /*  = 15 */
    progressiveRefinementSegmentStart, /*  = 16 */
    progressiveRefinementSegmentEnd, /*  = 17 */
    filmGrainCharacteristics, /*  = 19 */
    postFilterHint, /*  = 22 */
    toneMappingInfo, /*  = 23 */
    framePackingArrangement, /*  = 45 */
    displayOrientation, /*  = 47 */
    greenMetadata, /*  = 56 */                            // specified in ISO/IEC 23001-11
    structureOfPicturesInfo, /*  = 128 */
    activeParameterSets, /*  = 129 */
    decodingUnitInfo, /*  = 130 */
    temporalSubLayerZeroIdx, /*  = 131 */
    decodedPictureHash, /*  = 132 */
    scalableNesting, /*  = 133 */
    regionRefreshInfo, /*  = 134 */
    noDisplay, /*  = 135 */
    timeCode, /*  = 136 */
    masteringDisplayColourVolume, /*  = 137 */
    segmentedRectFramePackingArrangement, /*  = 138 */
    temporalMotionConstrainedTileSets, /*  = 139 */
    chromaResamplingFilterHint, /*  = 140 */
    kneeFunctionInfo, /*  = 141 */
    colourRemappingInfo, /*  = 142 */
    deinterlacedFieldIdentification, /*  = 143 */
    contentLightLevelInfo, /*  = 144 */
    dependentRapIndication, /*  = 145 */
    codedRegionCompletion, /*  = 146 */
    alternativeTransferCharacteristics, /*  = 147 */
    ambientViewingEnvironment, /*  = 148 */
    contentColourVolume, /*  = 149 */
    equirectangularProjection, /*  = 150 */
    cubemapProjection, /*  = 151 */
    fisheyeVideoInfo, /*  = 152 */
    sphereRotation, /*  = 154 */
    regionwisePacking, /*  = 155 */
    omniViewport, /*  = 156 */
    regionalNesting, /*  = 157 */
    mctsExtractionInfoSets, /*  = 158 */
    mctsExtractionInfoNesting, /*  = 159 */
    layersNotPresent, /*  = 160 */                       // specified in Annex F
    interLayerConstrainedTileSets, /*  = 161 */        // specified in Annex F
    bspNesting, /*  = 162 */                              // specified in Annex F
    bspInitialArrivalTime, /*  = 163 */                 // specified in Annex F
    subBitstreamProperty, /*  = 164 */                   // specified in Annex F
    alphaChannelInfo, /*  = 165 */                       // specified in Annex F
    overlayInfo, /*  = 166 */                             // specified in Annex F
    temporalMvPredictionConstraints, /*  = 167 */       // specified in Annex F
    frameFieldInfo, /*  = 168 */                         // specified in Annex F
    threeDimensionalReferenceDisplaysInfo, /*  = 176 */// specified in Annex G
    depthRepresentationInfo, /*  = 177 */                // specified in Annex G
    multiviewSceneInfo, /*  = 178 */                     // specified in Annex G
    multiviewAcquisitionInfo, /*  = 179 */               // specified in Annex G
    multiviewViewPosition, /*  = 180 */                  // specified in Annex G
    alternativeDepthInfo, /*  = 181 */                   // specified in Annex I
    seiManifest, /*  = 200 */
    seiPrefixIndication, /*  = 201 */
    annotatedRegions, /*  = 202 */
    shutterIntervalInfo, /*  = 205 */
}

