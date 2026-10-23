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
import java.util.Arrays;
import java.util.List;
import openize.heic.decoder.io.BitStreamWithNalSupport;
import openize.isobmff.ObservableCollection;

public class HEVCDecoderConfigurationRecord {
    public final byte configurationVersion; // 1
    public final byte generalProfileSpace;
    public final boolean generalTierFlag;
    public final byte generalProfileIdc;
    // public final boolean[] general_profile_compatibility_flags;
    // public final boolean[] general_constraint_indicator_flags;
    public final byte generalLevelIdc;
    public final /*UInt16*/ int minSpatialSegmentationIdc;
    public final byte parallelismType;
    public final byte chromaFormat;
    public final byte bitDepthLumaMinus8;
    public final byte bitDepthChromaMinus8;
    public final int avgFrameRate; // takes the value 0
    public final int constantFrameRate; // takes the value 0
    public final int numTemporalLayers; // takes the value 0
    public final int temporalIdNested; // takes the value 0
    private final boolean[] arrayCompleteness;
    private final List<NalUnit> nalUnits;
    private ObservableCollection<NalUnit> children;
    private SeqParameterSetRbsp spsUnit;

    public HEVCDecoderConfigurationRecord(BitStreamWithNalSupport stream) {
        configurationVersion = (byte) stream.read(8);

        generalProfileSpace = (byte) stream.read(2);
        generalTierFlag = stream.readFlag();
        generalProfileIdc = (byte) stream.read(5);

        // general_profile_compatibility_flags = new boolean[32];
        for (int i = 0; i < 32; i++)
            /*general_profile_compatibility_flags[i] =*/
            stream.readFlag();

        // general_constraint_indicator_flags = new boolean[48];
        for (int i = 0; i < 48; i++)
            /*general_constraint_indicator_flags[i] =*/
            stream.readFlag();

        generalLevelIdc = (byte) stream.read(8);

        stream.skipBits(4); // 1111
        minSpatialSegmentationIdc = stream.read(12) & 0xFFFF;
        stream.skipBits(6); // 111111
        parallelismType = (byte) stream.read(2);
        stream.skipBits(6); // 111111
        chromaFormat = (byte) stream.read(2);
        stream.skipBits(5); // 11111
        bitDepthLumaMinus8 = (byte) stream.read(3);
        stream.skipBits(5); // 11111
        bitDepthChromaMinus8 = (byte) stream.read(3);

        avgFrameRate = stream.read(16);
        constantFrameRate = stream.read(2);
        numTemporalLayers = stream.read(3);
        temporalIdNested = stream.read(1);

        byte lengthSizeMinusOne = (byte) stream.read(2);
        if ((lengthSizeMinusOne & 0xFF) != 3) {
            throw new HeicFormatException(
                    "NAL unit lengths of %d bytes are not supported".formatted((lengthSizeMinusOne & 0xFF) + 1));
        }

        byte numOfArrays = (byte) stream.read(8);

        nalUnits = new ArrayList<>(255);
        arrayCompleteness = new boolean[numOfArrays & 0xFF];
        for (int j = 0; j < (numOfArrays & 0xFF); j++) {
            arrayCompleteness[j] = stream.readFlag();
            stream.skipBits(1); // = 0
            NalUnitType nalUnitType = NalUnitType.codeToType(stream.read(6));
            /*UInt16*/
            int numNalus = stream.read(16) & 0xFFFF;

            for (int i = 0; i < numNalus; i++) {
                /*UInt16*/
                int nalUnitLength = stream.read(16) & 0xFFFF;
                if (nalUnitLength == 0) {
                    continue;
                }
                NalUnit unit = NalUnit.parseKnownUnit(stream, nalUnitLength, nalUnitType);
                stream.getContext().addNalContext(nalUnitType, unit);
                nalUnits.add(unit);
                if (spsUnit == null && unit.nalHeader.type == NalUnitType.SPS_NUT) {
                    spsUnit = (SeqParameterSetRbsp) unit;
                }
            }
        }
        setChildren(new ObservableCollection<>(nalUnits));
    }

    @Override
    public final String toString() {
        return "HEVCDecoderConfiguration; completeness: " + Arrays.toString(arrayCompleteness);
    }

    public final ObservableCollection<NalUnit> getChildren() {
        return children;
    }

    public final void setChildren(ObservableCollection<NalUnit> value) {
        children = value;
    }

    final PicParameterSetRbsp getPPS() {
        for (NalUnit nalUnit : nalUnits) {
            if (nalUnit.nalHeader.type == NalUnitType.PPS_NUT) {
                return (PicParameterSetRbsp) nalUnit;
            }
        }
        throw new HeicFormatException("The HEVC decoder configuration has no PPS");
        //        return (pic_parameter_set_rbsp) nalUnits.stream().filter(i -> i.NalHeader.type ==
        // NalUnitType.PPS_NUT).findFirst()
        //                .orElse(null);
    }

    final SeqParameterSetRbsp getSPS() {
        if (spsUnit == null) {
            throw new HeicFormatException("The HEVC decoder configuration has no PPS");
        }
        return spsUnit;
        //        for (NalUnit nalUnit : nalUnits)
        //        {
        //            if (nalUnit.NalHeader.type == NalUnitType.SPS_NUT)
        //            {
        //                return (seq_parameter_set_rbsp) nalUnit;
        //            }
        //        }
        //        throw new IllegalStateException();
    }

    final VideoParameterSetRbsp getVPS() {
        for (NalUnit nalUnit : nalUnits) {
            if (nalUnit.nalHeader.type == NalUnitType.VPS_NUT) {
                return (VideoParameterSetRbsp) nalUnit;
            }
        }
        throw new HeicFormatException("The HEVC decoder configuration has no VPS");
        //        return (video_parameter_set_rbsp) nalUnits.stream().filter(i -> i.NalHeader.type ==
        // NalUnitType.VPS_NUT).findFirst()
        //                .orElse(null);
    }
}
