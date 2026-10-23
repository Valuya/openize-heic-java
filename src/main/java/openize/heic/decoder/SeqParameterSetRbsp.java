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

import java.util.HashMap;


public class SeqParameterSetRbsp extends NalUnit
{
    public final VideoParameterSetRbsp vps;
    final byte spsVideoParameterSetId;
    final byte spsMaxSubLayersMinus1;
    final boolean spsTemporalIdNestingFlag;
    final ProfileTierLevel ProfileTierLevel;
    final byte spsSeqParameterSetId; // max - 16
    final /*UInt32*/ long chromaFormatIdc;
    final /*UInt32*/ long picWidthInLumaSamples;
    final /*UInt32*/ long picHeightInLumaSamples;
    final boolean conformanceWindowFlag;
    final byte bitDepthLumaMinus8;
    final byte bitDepthChromaMinus8;
    final byte log2MaxPicOrderCntLsbMinus4;
    final boolean spsSubLayerOrderingInfoPresentFlag;
    //final /*UInt32*/ long[] sps_max_dec_pic_buffering_minus1;
//    final /*UInt32*/ long[] sps_max_num_reorder_pics;
//    final /*UInt32*/ long[] sps_max_latency_increase_plus1;
    final byte log2MinLumaCodingBlockSizeMinus3;
    final byte log2DiffMaxMinLumaCodingBlockSize;
    final byte log2MinLumaTransformBlockSizeMinus2;
    final byte log2DiffMaxMinLumaTransformBlockSize;
    final /*UInt32*/ long maxTransformHierarchyDepthInter;
    final /*UInt32*/ long maxTransformHierarchyDepthIntra;
    final boolean scalingListEnabledFlag;
    final boolean ampEnabledFlag;
    final boolean sampleAdaptiveOffsetEnabledFlag;
    final boolean pcmEnabledFlag;
    final /*UInt32*/ long numShortTermRefPicSets;
    final HashMap<Integer, StRefPicSet> stRefPicSets;
    final boolean longTermRefPicsPresentFlag;
    /*UInt32*/ long numLongTermRefPicsSps;
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
    /*UInt32*/ long confWinLeftOffset;
    /*UInt32*/ long confWinRightOffset;
    /*UInt32*/ long confWinTopOffset;
    /*UInt32*/ long confWinBottomOffset;
    boolean spsScalingListDataPresentFlag;
    ScalingListData ScalingListData;
    byte pcmSampleBitDepthLuma;
    byte pcmSampleBitDepthChroma;
    byte log2MinPcmLumaCodingBlockSizeMinus3;
    byte log2DiffMaxMinPcmLumaCodingBlockSize;
    boolean pcmLoopFilterDisabledFlag;

    public SeqParameterSetRbsp(BitStreamWithNalSupport stream, /*UInt64*/long startPosition, int size)
    {
        super(stream, startPosition, size);

        spsVideoParameterSetId = (byte) stream.read(4);                  // u(4)

        vps = stream.getContext().getVPS().get(spsVideoParameterSetId & 0xFF);

        spsMaxSubLayersMinus1 = (byte) stream.read(3);                   // u(3)
        spsTemporalIdNestingFlag = stream.readFlag();                   // u(1)

        ProfileTierLevel = new ProfileTierLevel(stream, true, spsMaxSubLayersMinus1 & 0xFF);

        spsSeqParameterSetId = (byte) stream.readUev();                       // ue(v)
        chromaFormatIdc = stream.readUev();                              // ue(v)
        if ((chromaFormatIdc & 0xFFFFFFFFL) == 3)
        {
            separateColourPlaneFlag = stream.readFlag();                // u(1)
        }

        picWidthInLumaSamples = stream.readUev();                      // ue(v)
        picHeightInLumaSamples = stream.readUev();                     // ue(v)
        conformanceWindowFlag = stream.readFlag();                       // u(1)
        if (conformanceWindowFlag)
        {
            confWinLeftOffset = stream.readUev();                       // ue(v)
            confWinRightOffset = stream.readUev();                      // ue(v)
            confWinTopOffset = stream.readUev();                        // ue(v)
            confWinBottomOffset = stream.readUev();                     // ue(v)
        }

        bitDepthLumaMinus8 = (byte) stream.readUev();                    // ue(v)
        bitDepthChromaMinus8 = (byte) stream.readUev();                  // ue(v)
        log2MaxPicOrderCntLsbMinus4 = (byte) stream.readUev();        // ue(v)
        spsSubLayerOrderingInfoPresentFlag = stream.readFlag();      // u(1)

        int layersCount = (spsSubLayerOrderingInfoPresentFlag ? 0 : spsMaxSubLayersMinus1) & 0xFF;
        //sps_max_dec_pic_buffering_minus1 = new /*UInt32*/long[(sps_max_sub_layers_minus1 & 0xFF) + 1];
//        sps_max_num_reorder_pics = new /*UInt32*/long[(sps_max_sub_layers_minus1 & 0xFF) + 1];
//        sps_max_latency_increase_plus1 = new /*UInt32*/long[(sps_max_sub_layers_minus1 & 0xFF) + 1];
        for (int i = layersCount; i <= (spsMaxSubLayersMinus1 & 0xFF); i++)
        {
            /*sps_max_dec_pic_buffering_minus1[i] =*/ stream.readUev();         // ue(v)
            /*sps_max_num_reorder_pics[i] =*/ stream.readUev();                // ue(v)
            /*sps_max_latency_increase_plus1[i] =*/ stream.readUev();          // ue(v)
        }

        log2MinLumaCodingBlockSizeMinus3 = (byte) stream.readUev();          // ue(v)
        log2DiffMaxMinLumaCodingBlockSize = (byte) stream.readUev();       // ue(v)
        log2MinLumaTransformBlockSizeMinus2 = (byte) stream.readUev();      // ue(v)
        log2DiffMaxMinLumaTransformBlockSize = (byte) stream.readUev();// ue(v)
        maxTransformHierarchyDepthInter = stream.readUev();            // ue(v)
        maxTransformHierarchyDepthIntra = stream.readUev();            // ue(v)

        scalingListEnabledFlag = stream.readFlag();               // u(1)
        if (scalingListEnabledFlag)
        {
            spsScalingListDataPresentFlag = stream.readFlag();  // u(1)
            if (spsScalingListDataPresentFlag)
            {
                ScalingListData = new ScalingListData(stream);
            }
        }

        ampEnabledFlag = stream.readFlag();                        // u(1)
        sampleAdaptiveOffsetEnabledFlag = stream.readFlag();     // u(1)

        pcmEnabledFlag = stream.readFlag();                        // u(1)
        if (pcmEnabledFlag)
        {
            pcmSampleBitDepthLuma = (byte) (stream.read(4) + 1);    // u(4)
            pcmSampleBitDepthChroma = (byte) (stream.read(4) + 1);  // u(4)
            log2MinPcmLumaCodingBlockSizeMinus3 = (byte) stream.readUev(); // ue(v)
            log2DiffMaxMinPcmLumaCodingBlockSize = (byte) stream.readUev();//ue(v)
            pcmLoopFilterDisabledFlag = stream.readFlag();       // u(1)
        }

        numShortTermRefPicSets = stream.readUev();                    // ue(v)

        stRefPicSets = new HashMap<>();
        for (/*UInt32*/long i = 0; (i & 0xFFFFFFFFL) < (numShortTermRefPicSets & 0xFFFFFFFFL); i++)
            stRefPicSets.put((int) i, new StRefPicSet(stream, this, i));

        longTermRefPicsPresentFlag = stream.readFlag();         // u(1)
        if (longTermRefPicsPresentFlag)
        {
            numLongTermRefPicsSps = stream.readUev();                 // ue(v)
            ltRefPicPocLsbSps = new int[(int) (numLongTermRefPicsSps & 0xFFFFFFFFL)];
            usedByCurrPicLtSpsFlag = new boolean[(int) (numLongTermRefPicsSps & 0xFFFFFFFFL)];
            for (int i = 0; i < (numLongTermRefPicsSps & 0xFFFFFFFFL); i++)
            {
                ltRefPicPocLsbSps[i] =
                        stream.read((log2MaxPicOrderCntLsbMinus4 & 0xFF) + 1); // u(v)
                usedByCurrPicLtSpsFlag[i] = stream.readFlag(); // u(1)
            }
        }

        spsTemporalMvpEnabledFlag = stream.readFlag();           // u(1)
        strongIntraSmoothingEnabledFlag = stream.readFlag();     // u(1)
        vuiParametersPresentFlag = stream.readFlag();             // u(1)
        if (vuiParametersPresentFlag)
        {
            VuiParameters = new VuiParameters(stream, this);
        }
        else
        {
            VuiParameters = new VuiParameters();
        }

        spsExtensionPresentFlag = stream.readFlag();              // u(1)
        if (spsExtensionPresentFlag)
        {
            spsRangeExtensionFlag = stream.readFlag();            // u(1)
            spsMultilayerExtensionFlag = stream.readFlag();       // u(1)
            sps3dExtensionFlag = stream.readFlag();               // u(1)
            spsSccExtensionFlag = stream.readFlag();              // u(1)
            spsExtension4bits = (byte) stream.read(4);                // u(4)
        }

        spsRangeExt = spsRangeExtensionFlag ?
                new SpsRangeExtension(stream) :
                new SpsRangeExtension();


        if (spsMultilayerExtensionFlag)
        {
            spsMultilayerExt = new SpsMultilayerExtension(stream); /* specified in Annex F */
        }

        if (sps3dExtensionFlag)
        {
            sps3dExt = new Sps3dExtension(stream); /* specified in Annex I */
        }

        spsSccExt = spsRangeExtensionFlag ?
                new SpsSccExtension(stream, chromaFormatIdc, bitDepthLumaMinus8, bitDepthChromaMinus8) :
                new SpsSccExtension(chromaFormatIdc);

        if ((spsExtension4bits & 0xFF) > 0)
        {
            while (stream.hasMoreRbspData(getEndPosition()))
                spsExtensionDataFlag = stream.readFlag();         // u(1)
        }

        new RbspTrailingBits(stream);
    }

    @Override
    public String toString()
    {
        return
                "NAL Unit SPS " + String.format("\nSize: %dx%d ", picWidthInLumaSamples, picHeightInLumaSamples) +
                        String.format("\nBit depth: %d %d", (bitDepthLumaMinus8 & 0xFF) + 8, (bitDepthChromaMinus8 & 0xFF) + 8);
    }

    /**
     * <p>
     * 0 - mono, 1 - 420, 2 - 422, 3 - 444
     * </p>
     */
    public final /*UInt32*/long getChromaArrayType()
    {
        if (!separateColourPlaneFlag)
        {
            return chromaFormatIdc;
        }
        return 0;
    }

    public final /*UInt32*/long numNegativePics(int stRpsIdx)
    {
        return stRefPicSets.get(stRpsIdx).numNegativePics;
    }

    public final /*UInt32*/long numPositivePics(int stRpsIdx)
    {
        return stRefPicSets.get(stRpsIdx).numPositivePics;
    }

    public final /*UInt32*/long numDeltaPocs(int stRpsIdx)
    {
        return (((numNegativePics(stRpsIdx) & 0xFFFFFFFFL) + (numPositivePics(stRpsIdx) & 0xFFFFFFFFL)) & 0xFFFFFFFFL);
    }

    public final boolean[] usedByCurrPicS0(int stRpsIdx)
    {
        return stRefPicSets.get(stRpsIdx).usedByCurrPicS0Flag;
    }

    public final boolean[] usedByCurrPicS1(int stRpsIdx)
    {
        return stRefPicSets.get(stRpsIdx).usedByCurrPicS1Flag;
    }

    public final long deltaPocS0(int stRpsIdx, int i)
    {
        return i == 0 ?
                -(((stRefPicSets.get(stRpsIdx).deltaPocS0Minus1[i] & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL) :
                deltaPocS0(stRpsIdx, i - 1) - (((stRefPicSets.get(stRpsIdx).deltaPocS0Minus1[i] & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL);
    }

    public final long deltaPocS1(int stRpsIdx, int i)
    {
        return i == 0 ?
                -(((stRefPicSets.get(stRpsIdx).deltaPocS1Minus1[i] & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL) :
                deltaPocS1(stRpsIdx, i - 1) - (((stRefPicSets.get(stRpsIdx).deltaPocS1Minus1[i] & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL);
    }

    /**
     * <p>
     * 2 if 420 or 422, otherwise 1
     * </p>
     */
    public final byte getSubWidthC()
    {
        return (byte) (((chromaFormatIdc & 0xFFFFFFFFL) == 1 || (chromaFormatIdc & 0xFFFFFFFFL) == 2) ? 2 : 1);
    }

    /**
     * <p>
     * 2 if 420, otherwise 1
     * </p>
     */
    public final byte getSubHeightC()
    {
        return (byte) (((chromaFormatIdc & 0xFFFFFFFFL) == 1) ? 2 : 1);
    }

    public final byte getMinCbLog2SizeY()
    {
        return (byte) ((log2MinLumaCodingBlockSizeMinus3 & 0xFF) + 3);
    }

    public final byte getCtbLog2SizeY()
    {
        return (byte) ((getMinCbLog2SizeY() & 0xFF) + (log2DiffMaxMinLumaCodingBlockSize & 0xFF));
    }

    public final /*UInt32*/long getMinCbSizeY()
    {
        return ((1L << (getMinCbLog2SizeY() & 0xFF)) & 0xFFFFFFFFL);
    }

    public final /*UInt32*/long getCtbSizeY()
    {
        return ((1L << (getCtbLog2SizeY() & 0xFF)) & 0xFFFFFFFFL);
    }

    public final /*UInt32*/long getCtbWidthC()
    {
        return ((chromaFormatIdc & 0xFFFFFFFFL) == 0 || separateColourPlaneFlag) ? 0 : (((getCtbSizeY() & 0xFFFFFFFFL) / (getSubWidthC() & 0xFF)) & 0xFFFFFFFFL);
    }

    public final /*UInt32*/long getCtbHeightC()
    {
        return ((chromaFormatIdc & 0xFFFFFFFFL) == 0 || separateColourPlaneFlag) ? 0 : (((getCtbSizeY() & 0xFFFFFFFFL) / (getSubHeightC() & 0xFF)) & 0xFFFFFFFFL);
    }

    public final /*UInt32*/long getPicWidthInMinCbsY()
    {
        return (((picWidthInLumaSamples & 0xFFFFFFFFL) / (getMinCbSizeY() & 0xFFFFFFFFL)) & 0xFFFFFFFFL);
    }

    public final /*UInt32*/long getPicWidthInCtbsY()
    {
        return MathExtra.ceilDiv(picWidthInLumaSamples, getCtbSizeY());
    }

    public final /*UInt32*/long getPicHeightInMinCbsY()
    {
        return (((picHeightInLumaSamples & 0xFFFFFFFFL) / (getMinCbSizeY() & 0xFFFFFFFFL)) & 0xFFFFFFFFL);
    }

    public final /*UInt32*/long getPicHeightInCtbsY()
    {
        return MathExtra.ceilDiv(picHeightInLumaSamples, getCtbSizeY());
    }

    public final /*UInt32*/long getPicSizeInMinCbsY()
    {
        return (((getPicWidthInMinCbsY() & 0xFFFFFFFFL) * (getPicHeightInMinCbsY() & 0xFFFFFFFFL)) & 0xFFFFFFFFL);
    }

    public final /*UInt32*/long getPicSizeInCtbsY()
    {
        return (((getPicWidthInCtbsY() & 0xFFFFFFFFL) * (getPicHeightInCtbsY() & 0xFFFFFFFFL)) & 0xFFFFFFFFL);
    }

    public final /*UInt32*/long getPicSizeInSamplesY()
    {
        return (((picWidthInLumaSamples & 0xFFFFFFFFL) * (picHeightInLumaSamples & 0xFFFFFFFFL)) & 0xFFFFFFFFL);
    }

    public final /*UInt32*/long getPicWidthInSamplesC()
    {
        return (((picWidthInLumaSamples & 0xFFFFFFFFL) / (getSubWidthC() & 0xFF)) & 0xFFFFFFFFL);
    }

    public final /*UInt32*/long getPicHeightInSamplesC()
    {
        return (((picHeightInLumaSamples & 0xFFFFFFFFL) / (getSubHeightC() & 0xFF)) & 0xFFFFFFFFL);
    }

    public final byte getBitDepthY()
    {
        return (byte) (8 + (bitDepthLumaMinus8 & 0xFF));
    }

    public final byte getQpBdOffsetY()
    {
        return (byte) (6 * (bitDepthLumaMinus8 & 0xFF));
    }

    public final byte getBitDepthC()
    {
        return (byte) (8 + (bitDepthChromaMinus8 & 0xFF));
    }

    public final byte getQpBdOffsetC()
    {
        return (byte) (6 * (bitDepthChromaMinus8 & 0xFF));
    }

    public final byte getMinTbLog2SizeY()
    {
        return (byte) ((log2MinLumaTransformBlockSizeMinus2 & 0xFF) + 2);
    }

    public final byte getMaxTbLog2SizeY()
    {
        return (byte) ((getMinTbLog2SizeY() & 0xFF) + (log2DiffMaxMinLumaTransformBlockSize & 0xFF));
    }

    public final /*UInt32*/long getMinTbSizeY()
    {
        return ((1L << (getMinTbLog2SizeY() & 0xFF)) & 0xFFFFFFFFL);
    }

    public final /*UInt32*/long getMaxPicOrderCntLsb()
    {
        return ((1L << ((log2MaxPicOrderCntLsbMinus4 & 0xFF) + 4)) & 0xFFFFFFFFL);
    }

    public final int getLog2MinIpcmCbSizeY()
    {
        return (log2MinPcmLumaCodingBlockSizeMinus3 & 0xFF) + 3;
    }

    public final int getLog2MaxIpcmCbSizeY()
    {
        return (log2DiffMaxMinPcmLumaCodingBlockSize & 0xFF) + getLog2MinIpcmCbSizeY();
    }

    public final int getCoeffMinY()
    {
        return -(1 << (spsRangeExt.extendedPrecisionProcessingFlag ? Math.max(15, (getBitDepthY() & 0xFF) + 6) : 15));
    }

    public final int getCoeffMinC()
    {
        return -(1 << (spsRangeExt.extendedPrecisionProcessingFlag ? Math.max(15, (getBitDepthC() & 0xFF) + 6) : 15));
    }

    public final int getCoeffMaxY()
    {
        return (1 << (spsRangeExt.extendedPrecisionProcessingFlag ? Math.max(15, (getBitDepthY() & 0xFF) + 6) : 15)) - 1;
    }

    public final int getCoeffMaxC()
    {
        return (1 << (spsRangeExt.extendedPrecisionProcessingFlag ? Math.max(15, (getBitDepthC() & 0xFF) + 6) : 15)) - 1;
    }

}

