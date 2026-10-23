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

import java.util.HashMap;
import openize.heic.decoder.io.BitStreamWithNalSupport;

public class SeqParameterSetRbsp extends NalUnit {
    public final VideoParameterSetRbsp vps;
    private static final int MAX_PICTURE_DIMENSION = 65536;

    final byte spsVideoParameterSetId;
    final byte spsMaxSubLayersMinus1;
    final boolean spsTemporalIdNestingFlag;
    final ProfileTierLevel ProfileTierLevel;
    final byte spsSeqParameterSetId; // max - 16
    final int chromaFormatIdc;
    final int picWidthInLumaSamples;
    final int picHeightInLumaSamples;
    final boolean conformanceWindowFlag;
    final int bitDepthLumaMinus8;
    final int bitDepthChromaMinus8;
    final int log2MaxPicOrderCntLsbMinus4;
    final boolean spsSubLayerOrderingInfoPresentFlag;
    final int log2MinLumaCodingBlockSizeMinus3;
    final int log2DiffMaxMinLumaCodingBlockSize;
    final int log2MinLumaTransformBlockSizeMinus2;
    final int log2DiffMaxMinLumaTransformBlockSize;
    final int maxTransformHierarchyDepthInter;
    final int maxTransformHierarchyDepthIntra;
    final boolean scalingListEnabledFlag;
    final boolean ampEnabledFlag;
    final boolean sampleAdaptiveOffsetEnabledFlag;
    final boolean pcmEnabledFlag;
    final int numShortTermRefPicSets;
    final HashMap<Integer, StRefPicSet> stRefPicSets;
    final boolean longTermRefPicsPresentFlag;
    int numLongTermRefPicsSps;
    int[] ltRefPicPocLsbSps;
    boolean[] usedByCurrPicLtSpsFlag;
    final boolean spsTemporalMvpEnabledFlag;
    final boolean strongIntraSmoothingEnabledFlag;
    final boolean vuiParametersPresentFlag;
    final boolean spsExtensionPresentFlag;
    boolean spsRangeExtensionFlag;
    boolean spsMultilayerExtensionFlag;
    boolean sps3dExtensionFlag;
    boolean spsSccExtensionFlag;
    byte spsExtension4bits;
    final SpsRangeExtension spsRangeExt;
    SpsMultilayerExtension spsMultilayerExt;
    Sps3dExtension sps3dExt;
    final SpsSccExtension spsSccExt;
    boolean spsExtensionDataFlag;
    final VuiParameters VuiParameters;
    boolean separateColourPlaneFlag;
    int confWinLeftOffset;
    int confWinRightOffset;
    int confWinTopOffset;
    int confWinBottomOffset;
    boolean spsScalingListDataPresentFlag;
    ScalingListData ScalingListData;
    byte pcmSampleBitDepthLuma;
    byte pcmSampleBitDepthChroma;
    byte log2MinPcmLumaCodingBlockSizeMinus3;
    byte log2DiffMaxMinPcmLumaCodingBlockSize;
    boolean pcmLoopFilterDisabledFlag;

    public SeqParameterSetRbsp(BitStreamWithNalSupport stream, /*UInt64*/ long startPosition, int size) {
        super(stream, startPosition, size);

        spsVideoParameterSetId = (byte) stream.read(4); // u(4)

        vps = stream.getContext().getVPS().get(spsVideoParameterSetId & 0xFF);

        spsMaxSubLayersMinus1 = (byte) stream.read(3); // u(3)
        spsTemporalIdNestingFlag = stream.readFlag(); // u(1)

        ProfileTierLevel = new ProfileTierLevel(stream, true, spsMaxSubLayersMinus1 & 0xFF);

        spsSeqParameterSetId = (byte) stream.readUev(); // ue(v)
        chromaFormatIdc = stream.readUevAtMost(3); // ue(v)
        if (chromaFormatIdc == 3) {
            separateColourPlaneFlag = stream.readFlag(); // u(1)
        }

        // far beyond the 16888 samples of any HEVC level, and small enough for int arithmetic on sample counts
        picWidthInLumaSamples = stream.readUevAtMost(MAX_PICTURE_DIMENSION); // ue(v)
        picHeightInLumaSamples = stream.readUevAtMost(MAX_PICTURE_DIMENSION); // ue(v)
        conformanceWindowFlag = stream.readFlag(); // u(1)
        if (conformanceWindowFlag) {
            confWinLeftOffset = stream.readUevAtMost(MAX_PICTURE_DIMENSION); // ue(v)
            confWinRightOffset = stream.readUevAtMost(MAX_PICTURE_DIMENSION); // ue(v)
            confWinTopOffset = stream.readUevAtMost(MAX_PICTURE_DIMENSION); // ue(v)
            confWinBottomOffset = stream.readUevAtMost(MAX_PICTURE_DIMENSION); // ue(v)
        }

        bitDepthLumaMinus8 = stream.readUevAtMost(8); // ue(v)
        bitDepthChromaMinus8 = stream.readUevAtMost(8); // ue(v)
        log2MaxPicOrderCntLsbMinus4 = stream.readUevAtMost(12); // ue(v)
        spsSubLayerOrderingInfoPresentFlag = stream.readFlag(); // u(1)

        // sps_max_dec_pic_buffering_minus1, sps_max_num_reorder_pics and sps_max_latency_increase_plus1 per
        // sub-layer only matter to sequences
        int firstSubLayer = spsSubLayerOrderingInfoPresentFlag ? 0 : spsMaxSubLayersMinus1 & 0xFF;
        for (int i = firstSubLayer; i <= (spsMaxSubLayersMinus1 & 0xFF); i++) {
            stream.readUev(); // ue(v)
            stream.readUev(); // ue(v)
            stream.readUev(); // ue(v)
        }

        // CTBs of 16 to 64, coding blocks from 8, transform blocks of 4 to 32 no larger than the CTB (7.4.3.2.1)
        log2MinLumaCodingBlockSizeMinus3 = stream.readUevAtMost(3); // ue(v)
        log2DiffMaxMinLumaCodingBlockSize = stream.readUevAtMost(3 - log2MinLumaCodingBlockSizeMinus3); // ue(v)
        log2MinLumaTransformBlockSizeMinus2 = stream.readUevAtMost(3); // ue(v)
        log2DiffMaxMinLumaTransformBlockSize = stream.readUevAtMost(3 - log2MinLumaTransformBlockSizeMinus2); // ue(v)
        if (getCtbLog2SizeY() < 4
                || getMaxTbLog2SizeY() > getCtbLog2SizeY()
                || getMinTbLog2SizeY() >= getMinCbLog2SizeY()) {
            throw new HeicFormatException("Inconsistent block sizes: CTB %d, coding block %d, transform blocks %d to %d"
                    .formatted(
                            1 << getCtbLog2SizeY(),
                            1 << getMinCbLog2SizeY(),
                            1 << getMinTbLog2SizeY(),
                            1 << getMaxTbLog2SizeY()));
        }
        // the coded size is a whole number of minimum coding blocks (7.4.3.2.1), which prediction relies on
        if (picWidthInLumaSamples % getMinCbSizeY() != 0 || picHeightInLumaSamples % getMinCbSizeY() != 0) {
            throw new HeicFormatException("A %dx%d picture is not made of whole %d-sample coding blocks"
                    .formatted(picWidthInLumaSamples, picHeightInLumaSamples, getMinCbSizeY()));
        }
        maxTransformHierarchyDepthInter = stream.readUevAtMost(getCtbLog2SizeY() - getMinTbLog2SizeY()); // ue(v)
        maxTransformHierarchyDepthIntra = stream.readUevAtMost(getCtbLog2SizeY() - getMinTbLog2SizeY()); // ue(v)

        scalingListEnabledFlag = stream.readFlag(); // u(1)
        if (scalingListEnabledFlag) {
            spsScalingListDataPresentFlag = stream.readFlag(); // u(1)
            if (spsScalingListDataPresentFlag) {
                ScalingListData = new ScalingListData(stream);
            }
        }

        ampEnabledFlag = stream.readFlag(); // u(1)
        sampleAdaptiveOffsetEnabledFlag = stream.readFlag(); // u(1)

        pcmEnabledFlag = stream.readFlag(); // u(1)
        if (pcmEnabledFlag) {
            pcmSampleBitDepthLuma = (byte) (stream.read(4) + 1); // u(4)
            pcmSampleBitDepthChroma = (byte) (stream.read(4) + 1); // u(4)
            log2MinPcmLumaCodingBlockSizeMinus3 = (byte) stream.readUev(); // ue(v)
            log2DiffMaxMinPcmLumaCodingBlockSize = (byte) stream.readUev(); // ue(v)
            pcmLoopFilterDisabledFlag = stream.readFlag(); // u(1)
        }

        numShortTermRefPicSets = stream.readUevAtMost(64); // ue(v)

        stRefPicSets = new HashMap<>();
        for (int i = 0; i < numShortTermRefPicSets; i++) {
            stRefPicSets.put(i, new StRefPicSet(stream, this, i));
        }

        longTermRefPicsPresentFlag = stream.readFlag(); // u(1)
        if (longTermRefPicsPresentFlag) {
            numLongTermRefPicsSps = stream.readUevAtMost(32); // ue(v)
            ltRefPicPocLsbSps = new int[numLongTermRefPicsSps];
            usedByCurrPicLtSpsFlag = new boolean[numLongTermRefPicsSps];
            for (int i = 0; i < numLongTermRefPicsSps; i++) {
                ltRefPicPocLsbSps[i] = stream.read(log2MaxPicOrderCntLsbMinus4 + 4); // u(v)
                usedByCurrPicLtSpsFlag[i] = stream.readFlag(); // u(1)
            }
        }

        spsTemporalMvpEnabledFlag = stream.readFlag(); // u(1)
        strongIntraSmoothingEnabledFlag = stream.readFlag(); // u(1)
        vuiParametersPresentFlag = stream.readFlag(); // u(1)
        if (vuiParametersPresentFlag) {
            VuiParameters = new VuiParameters(stream, this);
        } else {
            VuiParameters = new VuiParameters();
        }

        spsExtensionPresentFlag = stream.readFlag(); // u(1)
        if (spsExtensionPresentFlag) {
            spsRangeExtensionFlag = stream.readFlag(); // u(1)
            spsMultilayerExtensionFlag = stream.readFlag(); // u(1)
            sps3dExtensionFlag = stream.readFlag(); // u(1)
            spsSccExtensionFlag = stream.readFlag(); // u(1)
            spsExtension4bits = (byte) stream.read(4); // u(4)
        }

        spsRangeExt = spsRangeExtensionFlag ? new SpsRangeExtension(stream) : new SpsRangeExtension();

        if (spsMultilayerExtensionFlag) {
            spsMultilayerExt = new SpsMultilayerExtension(stream); /* specified in Annex F */
        }

        if (sps3dExtensionFlag) {
            sps3dExt = new Sps3dExtension(stream); /* specified in Annex I */
        }

        spsSccExt = spsSccExtensionFlag
                ? new SpsSccExtension(stream, chromaFormatIdc, bitDepthLumaMinus8, bitDepthChromaMinus8)
                : new SpsSccExtension();

        if ((spsExtension4bits & 0xFF) > 0) {
            while (stream.hasMoreRbspData(getEndPosition())) spsExtensionDataFlag = stream.readFlag(); // u(1)
        }

        new RbspTrailingBits(stream);
    }

    @Override
    public String toString() {
        return "NAL Unit SPS %dx%d, bit depth %d/%d"
                .formatted(picWidthInLumaSamples, picHeightInLumaSamples, getBitDepthY(), getBitDepthC());
    }
    /**
     * 0 for monochrome (or separately coded colour planes), 1 for 4:2:0, 2 for 4:2:2, 3 for 4:4:4.
     */
    public final int getChromaArrayType() {
        return separateColourPlaneFlag ? 0 : chromaFormatIdc;
    }

    public final /*UInt32*/ long numNegativePics(int stRpsIdx) {
        return stRefPicSets.get(stRpsIdx).numNegativePics;
    }

    public final /*UInt32*/ long numPositivePics(int stRpsIdx) {
        return stRefPicSets.get(stRpsIdx).numPositivePics;
    }

    public final /*UInt32*/ long numDeltaPocs(int stRpsIdx) {
        return (((numNegativePics(stRpsIdx) & 0xFFFFFFFFL) + (numPositivePics(stRpsIdx) & 0xFFFFFFFFL)) & 0xFFFFFFFFL);
    }

    public final boolean[] usedByCurrPicS0(int stRpsIdx) {
        return stRefPicSets.get(stRpsIdx).usedByCurrPicS0Flag;
    }

    public final boolean[] usedByCurrPicS1(int stRpsIdx) {
        return stRefPicSets.get(stRpsIdx).usedByCurrPicS1Flag;
    }

    public final long deltaPocS0(int stRpsIdx, int i) {
        return i == 0
                ? -(((stRefPicSets.get(stRpsIdx).deltaPocS0Minus1[i] & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL)
                : deltaPocS0(stRpsIdx, i - 1)
                        - (((stRefPicSets.get(stRpsIdx).deltaPocS0Minus1[i] & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL);
    }

    public final long deltaPocS1(int stRpsIdx, int i) {
        return i == 0
                ? -(((stRefPicSets.get(stRpsIdx).deltaPocS1Minus1[i] & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL)
                : deltaPocS1(stRpsIdx, i - 1)
                        - (((stRefPicSets.get(stRpsIdx).deltaPocS1Minus1[i] & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL);
    }

    public final int getSubWidthC() {
        return chromaFormatIdc == 1 || chromaFormatIdc == 2 ? 2 : 1;
    }

    public final int getSubHeightC() {
        return chromaFormatIdc == 1 ? 2 : 1;
    }

    public final int getMinCbLog2SizeY() {
        return log2MinLumaCodingBlockSizeMinus3 + 3;
    }

    public final int getCtbLog2SizeY() {
        return getMinCbLog2SizeY() + log2DiffMaxMinLumaCodingBlockSize;
    }

    public final int getMinCbSizeY() {
        return 1 << getMinCbLog2SizeY();
    }

    public final int getCtbSizeY() {
        return 1 << getCtbLog2SizeY();
    }

    public final int getCtbWidthC() {
        return getChromaArrayType() == 0 ? 0 : getCtbSizeY() / getSubWidthC();
    }

    public final int getCtbHeightC() {
        return getChromaArrayType() == 0 ? 0 : getCtbSizeY() / getSubHeightC();
    }

    public final int getPicWidthInMinCbsY() {
        return picWidthInLumaSamples / getMinCbSizeY();
    }

    public final int getPicWidthInCtbsY() {
        return (picWidthInLumaSamples + getCtbSizeY() - 1) / getCtbSizeY();
    }

    public final int getPicHeightInMinCbsY() {
        return picHeightInLumaSamples / getMinCbSizeY();
    }

    public final int getPicHeightInCtbsY() {
        return (picHeightInLumaSamples + getCtbSizeY() - 1) / getCtbSizeY();
    }

    public final int getPicSizeInMinCbsY() {
        return getPicWidthInMinCbsY() * getPicHeightInMinCbsY();
    }

    public final int getPicSizeInCtbsY() {
        return getPicWidthInCtbsY() * getPicHeightInCtbsY();
    }

    public final long getPicSizeInSamplesY() {
        return (long) picWidthInLumaSamples * picHeightInLumaSamples;
    }

    public final int getPicWidthInSamplesC() {
        return picWidthInLumaSamples / getSubWidthC();
    }

    public final int getPicHeightInSamplesC() {
        return picHeightInLumaSamples / getSubHeightC();
    }

    public final int getBitDepthY() {
        return 8 + bitDepthLumaMinus8;
    }

    public final int getQpBdOffsetY() {
        return 6 * bitDepthLumaMinus8;
    }

    public final int getBitDepthC() {
        return 8 + bitDepthChromaMinus8;
    }

    public final int getQpBdOffsetC() {
        return 6 * bitDepthChromaMinus8;
    }

    public final int getMinTbLog2SizeY() {
        return log2MinLumaTransformBlockSizeMinus2 + 2;
    }

    public final int getMaxTbLog2SizeY() {
        return getMinTbLog2SizeY() + log2DiffMaxMinLumaTransformBlockSize;
    }

    public final int getMinTbSizeY() {
        return 1 << getMinTbLog2SizeY();
    }

    public final int getMaxPicOrderCntLsb() {
        return 1 << (log2MaxPicOrderCntLsbMinus4 + 4);
    }

    public final int getLog2MinIpcmCbSizeY() {
        return (log2MinPcmLumaCodingBlockSizeMinus3 & 0xFF) + 3;
    }

    public final int getLog2MaxIpcmCbSizeY() {
        return (log2DiffMaxMinPcmLumaCodingBlockSize & 0xFF) + getLog2MinIpcmCbSizeY();
    }

    public final int getCoeffMinY() {
        return -(1 << (spsRangeExt.extendedPrecisionProcessingFlag ? Math.max(15, (getBitDepthY() & 0xFF) + 6) : 15));
    }

    public final int getCoeffMinC() {
        return -(1 << (spsRangeExt.extendedPrecisionProcessingFlag ? Math.max(15, (getBitDepthC() & 0xFF) + 6) : 15));
    }

    public final int getCoeffMaxY() {
        return (1 << (spsRangeExt.extendedPrecisionProcessingFlag ? Math.max(15, (getBitDepthY() & 0xFF) + 6) : 15))
                - 1;
    }

    public final int getCoeffMaxC() {
        return (1 << (spsRangeExt.extendedPrecisionProcessingFlag ? Math.max(15, (getBitDepthC() & 0xFF) + 6) : 15))
                - 1;
    }
}
