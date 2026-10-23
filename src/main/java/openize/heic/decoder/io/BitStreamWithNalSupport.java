/*
 * Openize.HEIC
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of Openize.HEIC.
 *
 * Openize.HEIC is available under Openize license, which is
 * available along with Openize.HEIC sources.
 */

package openize.heic.decoder.io;

import openize.heic.decoder.Cabac;
import openize.heic.decoder.DecoderContext;
import openize.heic.decoder.HeicFormatException;
import openize.isobmff.io.BitStreamReader;

/**
 * Reads HEVC syntax: exp-Golomb codes, and NAL unit payloads with their emulation prevention bytes removed.
 */
public class BitStreamWithNalSupport extends BitStreamReader {
    // A 32-bit unsigned exp-Golomb code has at most 31 leading zero bits
    private static final int MAX_EXP_GOLOMB_LEADING_ZEROS = 31;

    private final Cabac cabac;
    private final DecoderContext context = new DecoderContext();

    // In a NAL unit payload, 0x000003 is followed by a byte that must not be read as data
    private boolean nalMode;
    private int previousByte = 0xFF;
    private int byteBeforePrevious = 0xFF;

    public BitStreamWithNalSupport(byte[] data) {
        super(data);
        cabac = new Cabac(this);
    }

    public final Cabac getCabac() {
        return cabac;
    }

    public final DecoderContext getContext() {
        return context;
    }

    public final void turnOnNalUnitMode() {
        nalMode = true;
        previousByte = 0xFF;
        byteBeforePrevious = 0xFF;
    }

    public final void turnOffNulUnitMode() {
        nalMode = false;
    }

    @Override
    protected void moveToNextByte() {
        if (nalMode) {
            byteBeforePrevious = previousByte;
            previousByte = currentByte();
        }
        super.moveToNextByte();
    }

    @Override
    protected void beforeReadingCurrentByte() {
        if (nalMode && byteBeforePrevious == 0x00 && previousByte == 0x00 && currentByte() == 0x03) {
            byteBeforePrevious = previousByte;
            previousByte = currentByte();
            skipCurrentByte();
        }
    }

    @Override
    public void skipBits(int bitCount) {
        if (!nalMode) {
            super.skipBits(bitCount);
            return;
        }

        // emulation prevention bytes have to be seen, so skipping reads
        int remainingBits = bitCount;
        while (remainingBits > 0) {
            int bitsToRead = Math.min(remainingBits, 32);
            read(bitsToRead);
            remainingBits -= bitsToRead;
        }
    }

    public final long readUev() {
        int leadingZeroBits = 0;
        while (read(1) == 0) {
            leadingZeroBits++;
            if (leadingZeroBits > MAX_EXP_GOLOMB_LEADING_ZEROS) {
                throw new HeicFormatException("Invalid exp-Golomb code at bit " + getBitPosition());
            }
        }

        return (1L << leadingZeroBits) - 1 + Integer.toUnsignedLong(read(leadingZeroBits));
    }

    /**
     * Reads an unsigned exp-Golomb value the syntax bounds to {@code maximum}, as an int.
     *
     * @throws HeicFormatException if the value is larger
     */
    public final int readUevAtMost(int maximum) {
        long value = readUev();
        if (value > maximum) {
            throw new HeicFormatException(
                    "Exp-Golomb value %d at bit %d, more than %d".formatted(value, getBitPosition(), maximum));
        }
        return (int) value;
    }

    public final int readSev() {
        long codeNum = readUev();

        boolean negative = (codeNum & 1) == 0;
        return (negative ? -1 : 1) * (int) ((codeNum + 1) / 2);
    }

    /**
     * Reads a signed exp-Golomb value the syntax bounds to {@code minimum..maximum}.
     *
     * @throws HeicFormatException if the value is outside
     */
    public final int readSevInRange(int minimum, int maximum, String elementName) {
        int value = readSev();
        if (value < minimum || value > maximum) {
            throw new HeicFormatException("%s %d at bit %d, outside %d..%d"
                    .formatted(elementName, value, getBitPosition(), minimum, maximum));
        }
        return value;
    }

    /**
     * more_rbsp_data(): whether the RBSP holds data before its rbsp_trailing_bits().
     */
    public final boolean hasMoreRbspData(long endPosition) {
        if (getBitPosition() >= endPosition) {
            throw new HeicFormatException("Read past the end of a NAL unit");
        }

        while (notByteAligned()) {
            skipBits(1);
        }

        return peek(8) != 0x80;
    }
}
