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

/**
 * A NAL unit: its header, and where it lies in the stream. The subclasses parse the payloads the decoder uses; the
 * others, SEI included, are skipped.
 */
class NalUnit {
    final NalHeader nalHeader;
    // in bits, from the start of the stream
    private final long startPosition;
    // in bytes, emulation prevention bytes included
    private final int size;

    NalUnit(BitStreamWithNalSupport stream, long startPosition, int size) {
        nalHeader = new NalHeader(stream);
        this.startPosition = startPosition;
        this.size = size;
    }

    /**
     * Reads a length-prefixed NAL unit, as image items store them. Zero-length units, which some writers use as
     * padding, are skipped.
     */
    static void parseUnit(BitStreamWithNalSupport stream) {
        int size = stream.read(32);
        if (size == 0) {
            return;
        }
        long remainingBytes = stream.getLength() - stream.getBitPosition() / 8;
        if (size < 0 || size > remainingBytes) {
            throw new HeicFormatException("A NAL unit of %d bytes, more than the %d left in the file"
                    .formatted(Integer.toUnsignedLong(size), remainingBytes));
        }
        NalUnitType type = NalUnitType.codeToType((stream.peek(8) >> 1) & 0x3F);
        parseKnownUnit(stream, size, type);
    }

    /**
     * Parses a NAL unit of {@code size} bytes, which starts on a byte boundary, and leaves the stream after it. A
     * payload is parsed up to the last syntax element the decoder needs: what follows, such as extension data and
     * rbsp_trailing_bits( ), is skipped.
     */
    static NalUnit parseKnownUnit(BitStreamWithNalSupport stream, int size, NalUnitType type) {
        long startPosition = stream.getBitPosition();
        stream.turnOnNalUnitMode();
        NalUnit nalUnit = parsePayload(stream, startPosition, size, type);
        stream.turnOffNulUnitMode();

        // emulation prevention bytes are read but not returned: the size, which counts them, is the reference
        long endPosition = nalUnit.getEndPosition();
        if (stream.getBitPosition() > endPosition) {
            throw overrun(type, size);
        }
        stream.setBytePosition(endPosition / 8);
        return nalUnit;
    }

    private static NalUnit parsePayload(
            BitStreamWithNalSupport stream, long startPosition, int size, NalUnitType type) {
        // only the base layer is decoded: NAL units of other layers (nuh_layer_id > 0, multi-layer HEVC) are skipped
        int nuhLayerId = (stream.peek(16) >> 3) & 0x3F;
        if (nuhLayerId > 0) {
            return new NalUnit(stream, startPosition, size);
        }
        if (type.isVcl()) {
            return new SliceSegmentLayerRbsp(stream, startPosition, size);
        }
        return switch (type) {
            case VPS_NUT -> new VideoParameterSetRbsp(stream, startPosition, size);
            case SPS_NUT -> new SeqParameterSetRbsp(stream, startPosition, size);
            case PPS_NUT -> new PicParameterSetRbsp(stream, startPosition, size);
            default -> new NalUnit(stream, startPosition, size);
        };
    }

    /** The exception for a payload whose parsing reads past the unit's {@code size} bytes. */
    static HeicFormatException overrun(NalUnitType type, int size) {
        return new HeicFormatException("A %s NAL unit overruns its %d bytes".formatted(type, size));
    }

    /** The size of the unit in bytes, emulation prevention bytes included. */
    final int getSize() {
        return size;
    }

    /**
     * The bit position just after the unit.
     */
    final long getEndPosition() {
        return startPosition + size * 8L;
    }

    @Override
    public String toString() {
        return "NAL Unit %s".formatted(nalHeader.type);
    }
}
