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

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * seq_parameter_set_rbsp( ) (7.3.2.2) and the variables derived from it (7.4.3.2).
 */
class SeqParameterSetRbsp extends NalUnit {
    // 7.4.3.2.1
    private static final int MAX_SPS_ID = 15;
    private static final int MAX_BIT_DEPTH_MINUS8 = 8;
    private static final int MAX_LOG2_MAX_PIC_ORDER_CNT_LSB_MINUS4 = 12;
    private static final int MAX_NUM_SHORT_TERM_REF_PIC_SETS = 64;
    private static final int MAX_NUM_LONG_TERM_REF_PICS_SPS = 32;
    // far beyond the 16888 samples of any HEVC level, and small enough for int arithmetic on sample counts
    private static final int MAX_PICTURE_DIMENSION = 65536;
    // PCM coding blocks are at most 32x32 (7.4.3.2.1)
    private static final int MAX_LOG2_IPCM_CB_SIZE = 5;

    final @Nullable VideoParameterSetRbsp vps;
    final int spsSeqParameterSetId;
    final int chromaFormatIdc;
    final boolean separateColourPlaneFlag;
    final int picWidthInLumaSamples;
    final int picHeightInLumaSamples;
    private final int bitDepthLumaMinus8;
    private final int bitDepthChromaMinus8;
    final int log2MaxPicOrderCntLsbMinus4;
    private final int log2MinLumaCodingBlockSizeMinus3;
    private final int log2DiffMaxMinLumaCodingBlockSize;
    private final int log2MinLumaTransformBlockSizeMinus2;
    private final int log2DiffMaxMinLumaTransformBlockSize;
    final int maxTransformHierarchyDepthInter;
    final int maxTransformHierarchyDepthIntra;
    final boolean scalingListEnabledFlag;
    final boolean spsScalingListDataPresentFlag;
    // null unless sps_scaling_list_data_present_flag
    final @Nullable ScalingListData scalingListData;
    final boolean ampEnabledFlag;
    final boolean sampleAdaptiveOffsetEnabledFlag;
    final boolean pcmEnabledFlag;
    // PcmBitDepthY and PcmBitDepthC
    final int pcmSampleBitDepthLuma;
    final int pcmSampleBitDepthChroma;
    private final int log2MinPcmLumaCodingBlockSizeMinus3;
    private final int log2DiffMaxMinPcmLumaCodingBlockSize;
    final boolean pcmLoopFilterDisabledFlag;
    final int numShortTermRefPicSets;
    // the short-term reference picture sets, which later sets, and slice headers, predict from
    final List<StRefPicSet> stRefPicSets = new ArrayList<>();
    final boolean longTermRefPicsPresentFlag;
    final int numLongTermRefPicsSps;
    final int[] ltRefPicPocLsbSps;
    final boolean[] usedByCurrPicLtSpsFlag;
    final boolean spsTemporalMvpEnabledFlag;
    final boolean strongIntraSmoothingEnabledFlag;
    final VuiParameters vuiParameters;
    final SpsRangeExtension spsRangeExt;
    final SpsSccExtension spsSccExt;

    SeqParameterSetRbsp(BitStreamWithNalSupport stream, long startPosition, int size) {
        super(stream, startPosition, size);

        int spsVideoParameterSetId = stream.read(4);
        vps = stream.getContext().getVPS().get(spsVideoParameterSetId);
        int spsMaxSubLayersMinus1 = stream.read(3);
        stream.skipBits(1); // sps_temporal_id_nesting_flag
        ProfileTierLevel.read(stream, spsMaxSubLayersMinus1);

        spsSeqParameterSetId = stream.readUevAtMost(MAX_SPS_ID);
        chromaFormatIdc = stream.readUevAtMost(3);
        separateColourPlaneFlag = chromaFormatIdc == 3 && stream.readFlag();
        picWidthInLumaSamples = stream.readUevAtMost(MAX_PICTURE_DIMENSION);
        picHeightInLumaSamples = stream.readUevAtMost(MAX_PICTURE_DIMENSION);
        boolean conformanceWindowFlag = stream.readFlag();
        if (conformanceWindowFlag) {
            // conf_win_left_offset, conf_win_right_offset, conf_win_top_offset, conf_win_bottom_offset
            for (int i = 0; i < 4; i++) {
                stream.readUev();
            }
        }

        bitDepthLumaMinus8 = stream.readUevAtMost(MAX_BIT_DEPTH_MINUS8);
        bitDepthChromaMinus8 = stream.readUevAtMost(MAX_BIT_DEPTH_MINUS8);
        log2MaxPicOrderCntLsbMinus4 = stream.readUevAtMost(MAX_LOG2_MAX_PIC_ORDER_CNT_LSB_MINUS4);

        boolean spsSubLayerOrderingInfoPresentFlag = stream.readFlag();
        int firstSubLayer = spsSubLayerOrderingInfoPresentFlag ? 0 : spsMaxSubLayersMinus1;
        for (int i = firstSubLayer; i <= spsMaxSubLayersMinus1; i++) {
            stream.readUev(); // sps_max_dec_pic_buffering_minus1
            stream.readUev(); // sps_max_num_reorder_pics
            stream.readUev(); // sps_max_latency_increase_plus1
        }

        // CTBs of 16 to 64, coding blocks from 8, transform blocks of 4 to 32 no larger than the CTB (7.4.3.2.1)
        log2MinLumaCodingBlockSizeMinus3 = stream.readUevAtMost(3);
        log2DiffMaxMinLumaCodingBlockSize = stream.readUevAtMost(3 - log2MinLumaCodingBlockSizeMinus3);
        log2MinLumaTransformBlockSizeMinus2 = stream.readUevAtMost(3);
        log2DiffMaxMinLumaTransformBlockSize = stream.readUevAtMost(3 - log2MinLumaTransformBlockSizeMinus2);
        checkBlockSizes();
        maxTransformHierarchyDepthInter = stream.readUevAtMost(getCtbLog2SizeY() - getMinTbLog2SizeY());
        maxTransformHierarchyDepthIntra = stream.readUevAtMost(getCtbLog2SizeY() - getMinTbLog2SizeY());

        scalingListEnabledFlag = stream.readFlag();
        spsScalingListDataPresentFlag = scalingListEnabledFlag && stream.readFlag();
        scalingListData = spsScalingListDataPresentFlag ? new ScalingListData(stream) : null;

        ampEnabledFlag = stream.readFlag();
        sampleAdaptiveOffsetEnabledFlag = stream.readFlag();

        pcmEnabledFlag = stream.readFlag();
        if (pcmEnabledFlag) {
            pcmSampleBitDepthLuma = stream.read(4) + 1;
            pcmSampleBitDepthChroma = stream.read(4) + 1;
            int maxLog2IpcmCbSize = Math.min(getCtbLog2SizeY(), MAX_LOG2_IPCM_CB_SIZE);
            log2MinPcmLumaCodingBlockSizeMinus3 = stream.readUevAtMost(maxLog2IpcmCbSize - 3);
            log2DiffMaxMinPcmLumaCodingBlockSize = stream.readUevAtMost(maxLog2IpcmCbSize - getLog2MinIpcmCbSizeY());
            pcmLoopFilterDisabledFlag = stream.readFlag();
            if (pcmSampleBitDepthLuma > getBitDepthY() || pcmSampleBitDepthChroma > getBitDepthC()) {
                throw new HeicFormatException("PCM samples of %d/%d bits, deeper than the %d/%d-bit picture"
                        .formatted(pcmSampleBitDepthLuma, pcmSampleBitDepthChroma, getBitDepthY(), getBitDepthC()));
            }
        } else {
            pcmSampleBitDepthLuma = 0;
            pcmSampleBitDepthChroma = 0;
            log2MinPcmLumaCodingBlockSizeMinus3 = 0;
            log2DiffMaxMinPcmLumaCodingBlockSize = 0;
            pcmLoopFilterDisabledFlag = false;
        }

        numShortTermRefPicSets = stream.readUevAtMost(MAX_NUM_SHORT_TERM_REF_PIC_SETS);
        for (int i = 0; i < numShortTermRefPicSets; i++) {
            stRefPicSets.add(new StRefPicSet(stream, this, i));
        }

        longTermRefPicsPresentFlag = stream.readFlag();
        numLongTermRefPicsSps = longTermRefPicsPresentFlag ? stream.readUevAtMost(MAX_NUM_LONG_TERM_REF_PICS_SPS) : 0;
        ltRefPicPocLsbSps = new int[numLongTermRefPicsSps];
        usedByCurrPicLtSpsFlag = new boolean[numLongTermRefPicsSps];
        for (int i = 0; i < numLongTermRefPicsSps; i++) {
            ltRefPicPocLsbSps[i] = stream.read(log2MaxPicOrderCntLsbMinus4 + 4);
            usedByCurrPicLtSpsFlag[i] = stream.readFlag();
        }

        spsTemporalMvpEnabledFlag = stream.readFlag();
        strongIntraSmoothingEnabledFlag = stream.readFlag();
        boolean vuiParametersPresentFlag = stream.readFlag();
        vuiParameters =
                vuiParametersPresentFlag ? new VuiParameters(stream, spsMaxSubLayersMinus1) : new VuiParameters();

        boolean spsExtensionPresentFlag = stream.readFlag();
        boolean spsRangeExtensionFlag = false;
        boolean spsMultilayerExtensionFlag = false;
        boolean sps3dExtensionFlag = false;
        boolean spsSccExtensionFlag = false;
        if (spsExtensionPresentFlag) {
            spsRangeExtensionFlag = stream.readFlag();
            spsMultilayerExtensionFlag = stream.readFlag();
            sps3dExtensionFlag = stream.readFlag();
            spsSccExtensionFlag = stream.readFlag();
            // sps_extension_4bits: the extension data after the extensions below is not read
            stream.skipBits(4);
        }
        spsRangeExt = spsRangeExtensionFlag ? new SpsRangeExtension(stream) : new SpsRangeExtension();
        if (spsMultilayerExtensionFlag) {
            stream.skipBits(1); // sps_multilayer_extension( ) (Annex F): inter_view_mv_vert_constraint_flag
        }
        // sps_3d_extension( ) (Annex I) only matters to the layers above the base one, but it would have to be parsed
        // to reach a screen content coding extension
        if (sps3dExtensionFlag && spsSccExtensionFlag) {
            throw new HeicFormatException("An SPS with both 3D and screen content coding extensions is not supported");
        }
        spsSccExt = spsSccExtensionFlag ? new SpsSccExtension(stream) : new SpsSccExtension();
    }

    private void checkBlockSizes() {
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
        int minCbSizeY = 1 << getMinCbLog2SizeY();
        if (picWidthInLumaSamples % minCbSizeY != 0 || picHeightInLumaSamples % minCbSizeY != 0) {
            throw new HeicFormatException("A %dx%d picture is not made of whole %d-sample coding blocks"
                    .formatted(picWidthInLumaSamples, picHeightInLumaSamples, minCbSizeY));
        }
    }

    @Override
    public String toString() {
        return "NAL Unit SPS %dx%d, bit depth %d/%d"
                .formatted(picWidthInLumaSamples, picHeightInLumaSamples, getBitDepthY(), getBitDepthC());
    }

    /**
     * 0 for monochrome (or separately coded colour planes), 1 for 4:2:0, 2 for 4:2:2, 3 for 4:4:4.
     */
    final int getChromaArrayType() {
        return separateColourPlaneFlag ? 0 : chromaFormatIdc;
    }

    final int getSubWidthC() {
        return chromaFormatIdc == 1 || chromaFormatIdc == 2 ? 2 : 1;
    }

    final int getSubHeightC() {
        return chromaFormatIdc == 1 ? 2 : 1;
    }

    final int getMinCbLog2SizeY() {
        return log2MinLumaCodingBlockSizeMinus3 + 3;
    }

    final int getMinCbSizeY() {
        return 1 << getMinCbLog2SizeY();
    }

    final int getCtbLog2SizeY() {
        return getMinCbLog2SizeY() + log2DiffMaxMinLumaCodingBlockSize;
    }

    final int getCtbSizeY() {
        return 1 << getCtbLog2SizeY();
    }

    final int getPicWidthInCtbsY() {
        return (picWidthInLumaSamples + getCtbSizeY() - 1) / getCtbSizeY();
    }

    final int getPicHeightInCtbsY() {
        return (picHeightInLumaSamples + getCtbSizeY() - 1) / getCtbSizeY();
    }

    final int getPicSizeInCtbsY() {
        return getPicWidthInCtbsY() * getPicHeightInCtbsY();
    }

    final int getBitDepthY() {
        return 8 + bitDepthLumaMinus8;
    }

    final int getQpBdOffsetY() {
        return 6 * bitDepthLumaMinus8;
    }

    final int getBitDepthC() {
        return 8 + bitDepthChromaMinus8;
    }

    final int getQpBdOffsetC() {
        return 6 * bitDepthChromaMinus8;
    }

    final int getMinTbLog2SizeY() {
        return log2MinLumaTransformBlockSizeMinus2 + 2;
    }

    final int getMaxTbLog2SizeY() {
        return getMinTbLog2SizeY() + log2DiffMaxMinLumaTransformBlockSize;
    }

    final int getMaxPicOrderCntLsb() {
        return 1 << (log2MaxPicOrderCntLsbMinus4 + 4);
    }

    final int getLog2MinIpcmCbSizeY() {
        return log2MinPcmLumaCodingBlockSizeMinus3 + 3;
    }

    final int getLog2MaxIpcmCbSizeY() {
        return getLog2MinIpcmCbSizeY() + log2DiffMaxMinPcmLumaCodingBlockSize;
    }

    final int getCoeffMinY() {
        return -(1 << getCoeffLog2Range(getBitDepthY()));
    }

    final int getCoeffMinC() {
        return -(1 << getCoeffLog2Range(getBitDepthC()));
    }

    final int getCoeffMaxY() {
        return (1 << getCoeffLog2Range(getBitDepthY())) - 1;
    }

    final int getCoeffMaxC() {
        return (1 << getCoeffLog2Range(getBitDepthC())) - 1;
    }

    // CoeffMinY and CoeffMinC are -(1 << this), CoeffMaxY and CoeffMaxC (1 << this) - 1
    private int getCoeffLog2Range(int bitDepth) {
        return spsRangeExt.extendedPrecisionProcessingFlag ? Math.max(15, bitDepth + 6) : 15;
    }
}
