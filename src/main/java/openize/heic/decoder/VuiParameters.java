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

import openize.heic.decoder.io.BitStreamWithNalSupport;

class VuiParameters {
    boolean aspectRatioInfoPresentFlag;
    byte aspectRatioIdc; // 17 to 254 == 0
    /*UInt16*/ int sarWidth;
    /*UInt16*/ int sarHeight;
    boolean overscanInfoPresentFlag;
    boolean overscanAppropriateFlag;

    boolean videoSignalTypePresentFlag = false;
    byte videoFormat = 5;
    boolean videoFullRangeFlag = false;

    boolean colourDescriptionPresentFlag = false;
    byte colourPrimaries = 2;
    byte transferCharacteristics = 2;
    byte matrixCoeffs = 2;

    boolean chromaLocInfoPresentFlag;
    /*UInt32*/ long chromaSampleLocTypeTopField;
    /*UInt32*/ long chromaSampleLocTypeBottomField;
    boolean neutralChromaIndicationFlag;
    boolean fieldSeqFlag;
    boolean frameFieldInfoPresentFlag;
    boolean defaultDisplayWindowFlag;
    /*UInt32*/ long defDispWinLeftOffset;
    /*UInt32*/ long defDispWinRightOffset;
    /*UInt32*/ long defDispWinTopOffset;
    /*UInt32*/ long defDispWinBottomOffset;
    boolean vuiTimingInfoPresentFlag;
    /*UInt32*/ long vuiNumUnitsInTick;
    /*UInt32*/ long vuiTimeScale;
    boolean vuiPocProportionalToTimingFlag;
    /*UInt32*/ long vuiNumTicksPocDiffOneMinus1;
    boolean vuiHrdParametersPresentFlag;
    // hrd_parameters hrd_parameters;
    boolean bitstreamRestrictionFlag;
    boolean tilesFixedStructureFlag;
    boolean motionVectorsOverPicBoundariesFlag;
    boolean restrictedRefPicListsFlag;
    /*UInt32*/ long minSpatialSegmentationIdc;
    /*UInt32*/ long maxBytesPerPicDenom;
    /*UInt32*/ long maxBitsPerMinCuDenom;
    /*UInt32*/ long log2MaxMvLengthHorizontal;
    /*UInt32*/ long log2MaxMvLengthVertical;

    public VuiParameters(BitStreamWithNalSupport stream, SeqParameterSetRbsp sps) {
        aspectRatioInfoPresentFlag = stream.readFlag(); // u(1)
        if (aspectRatioInfoPresentFlag) {
            aspectRatioIdc = (byte) stream.read(8); // u(8)
            if ((aspectRatioIdc & 0xFF) == 255) {
                sarWidth = stream.read(16) & 0xFFFF; // u(16)
                sarHeight = stream.read(16) & 0xFFFF; // u(16)
            }
        }

        overscanInfoPresentFlag = stream.readFlag(); // u(1)
        if (overscanInfoPresentFlag) {
            overscanAppropriateFlag = stream.readFlag(); // u(1)
        }

        videoSignalTypePresentFlag = stream.readFlag(); // u(1)
        if (videoSignalTypePresentFlag) {
            videoFormat = (byte) stream.read(3); // u(3)
            videoFullRangeFlag = stream.readFlag(); // u(1)
            colourDescriptionPresentFlag = stream.readFlag(); // u(1)

            if (colourDescriptionPresentFlag) {
                colourPrimaries = (byte) stream.read(8); // u(8)
                transferCharacteristics = (byte) stream.read(8); // u(8)
                matrixCoeffs = (byte) stream.read(8); // u(8)
            }
        }

        chromaLocInfoPresentFlag = stream.readFlag(); // u(1)
        if (chromaLocInfoPresentFlag) {
            chromaSampleLocTypeTopField = stream.readUev(); // ue(v)
            chromaSampleLocTypeBottomField = stream.readUev(); // ue(v)
        }

        neutralChromaIndicationFlag = stream.readFlag(); // u(1)
        fieldSeqFlag = stream.readFlag(); // u(1)
        frameFieldInfoPresentFlag = stream.readFlag(); // u(1)
        defaultDisplayWindowFlag = stream.readFlag(); // u(1)
        if (defaultDisplayWindowFlag) {
            defDispWinLeftOffset = stream.readUev(); // ue(v)
            defDispWinRightOffset = stream.readUev(); // ue(v)
            defDispWinTopOffset = stream.readUev(); // ue(v)
            defDispWinBottomOffset = stream.readUev(); // ue(v)
        }

        vuiTimingInfoPresentFlag = stream.readFlag(); // u(1)
        if (vuiTimingInfoPresentFlag) {
            vuiNumUnitsInTick = stream.read(32) & 0xFFFFFFFFL; // u(32)
            vuiTimeScale = stream.read(32) & 0xFFFFFFFFL; // u(32)
            vuiPocProportionalToTimingFlag = stream.readFlag(); // u(1)
            if (vuiPocProportionalToTimingFlag) {
                vuiNumTicksPocDiffOneMinus1 = stream.readUev(); // ue(v)
            }

            vuiHrdParametersPresentFlag = stream.readFlag(); // u(1)
            if (vuiHrdParametersPresentFlag)
            /*hrd_parameters =*/ {
                new HrdParameters(stream, true, sps.spsMaxSubLayersMinus1 & 0xFF);
            }
        }

        bitstreamRestrictionFlag = stream.readFlag(); // u(1)
        if (bitstreamRestrictionFlag) {
            tilesFixedStructureFlag = stream.readFlag(); // u(1)
            motionVectorsOverPicBoundariesFlag = stream.readFlag(); // u(1)
            restrictedRefPicListsFlag = stream.readFlag(); // u(1)
            minSpatialSegmentationIdc = stream.readUev(); // ue(v)
            maxBytesPerPicDenom = stream.readUev(); // ue(v)
            maxBitsPerMinCuDenom = stream.readUev(); // ue(v)
            log2MaxMvLengthHorizontal = stream.readUev(); // ue(v)
            log2MaxMvLengthVertical = stream.readUev(); // ue(v)
        }
    }

    public VuiParameters() {
        // Do nothing
    }
}
