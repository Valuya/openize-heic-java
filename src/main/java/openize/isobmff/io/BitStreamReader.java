/*
 * FileFormat.IsoBmff
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of FileFormat.IsoBmff.
 *
 * FileFormat.IsoBmff is available under MIT license, which is
 * available along with FileFormat.IsoBmff sources.
 */

package openize.isobmff.io;

import openize.isobmff.IsoBmffFormatException;

/**
 * Reads bits, most significant first, from a file held in memory.
 *
 * <p>A reader is cheap: several of them can read the same array independently, which lets the images of a file be
 * decoded concurrently. Reading past the end of the data throws {@link IsoBmffFormatException}.
 */
public class BitStreamReader {
    private static final int BITS_PER_BYTE = 8;

    private final byte[] data;

    // The byte being read and how many of its bits are consumed; 8 means the next read moves to the next byte.
    private int bytePosition;
    private int bitIndex;

    public BitStreamReader(byte[] data) {
        this.data = data;
    }

    public final long getBitPosition() {
        return (long) bytePosition * BITS_PER_BYTE + bitIndex;
    }

    public final void setBytePosition(long bytePosition) {
        if (bytePosition < 0 || bytePosition > data.length) {
            throw new IsoBmffFormatException(
                    "Position %d is outside the %d-byte data".formatted(bytePosition, data.length));
        }
        this.bytePosition = (int) bytePosition;
        bitIndex = 0;
    }

    public final long getLength() {
        return data.length;
    }

    public final boolean notByteAligned() {
        return bitIndex != 0 && bitIndex != BITS_PER_BYTE;
    }

    public final boolean moreData() {
        return getBitPosition() < (long) data.length * BITS_PER_BYTE;
    }

    /**
     * Reads up to 32 bits.
     */
    public int read(int bitCount) {
        if (bitCount < 0 || bitCount > 32) {
            throw new IsoBmffFormatException("Cannot read %d bits as one value".formatted(bitCount));
        }

        // bits from the middle of a byte already being read: no byte boundary to handle
        if (bitIndex > 0 && bitCount <= BITS_PER_BYTE - bitIndex) {
            int availableBits = BITS_PER_BYTE - bitIndex;
            bitIndex += bitCount;
            return (currentByte() >> (availableBits - bitCount)) & ((1 << bitCount) - 1);
        }

        int value = 0;
        int remainingBits = bitCount;
        while (remainingBits > 0) {
            if (bitIndex == BITS_PER_BYTE) {
                moveToNextByte();
            }
            beforeReadingCurrentByte();

            int availableBits = BITS_PER_BYTE - bitIndex;
            int bitsToRead = Math.min(remainingBits, availableBits);
            int mask = (1 << bitsToRead) - 1;

            value = (value << bitsToRead) | ((currentByte() >> (availableBits - bitsToRead)) & mask);

            bitIndex += bitsToRead;
            remainingBits -= bitsToRead;
        }
        return value;
    }

    /**
     * Reads up to 64 bits as an unsigned value (a 64-bit value can come out negative).
     */
    public final long readLong(int bitCount) {
        if (bitCount < 0 || bitCount > 64) {
            throw new IsoBmffFormatException("Cannot read %d bits as one value".formatted(bitCount));
        }
        int highBitCount = Math.max(0, bitCount - 32);
        long highBits = Integer.toUnsignedLong(read(highBitCount));
        long lowBits = Integer.toUnsignedLong(read(bitCount - highBitCount));
        return (highBits << (bitCount - highBitCount)) | lowBits;
    }

    public final boolean readFlag() {
        return read(1) == 1;
    }

    /**
     * Reads a zero-terminated string; the terminating {@code '\0'} is part of the returned string.
     */
    public final String readString() {
        StringBuilder output = new StringBuilder(50);
        int character;
        do {
            character = read(8);
            output.append((char) character);
        } while (character != 0);
        return output.toString();
    }

    public final int peek(int bitCount) {
        int savedBytePosition = bytePosition;
        int savedBitIndex = bitIndex;

        int value = read(bitCount);

        bytePosition = savedBytePosition;
        bitIndex = savedBitIndex;
        return value;
    }

    public void skipBits(int bitCount) {
        long targetBitPosition = getBitPosition() + bitCount;
        if (bitCount < 0 || targetBitPosition > (long) data.length * BITS_PER_BYTE) {
            throw new IsoBmffFormatException(
                    "Skipping %d bits leaves the %d-byte data".formatted(bitCount, data.length));
        }
        bytePosition = (int) (targetBitPosition / BITS_PER_BYTE);
        bitIndex = (int) (targetBitPosition % BITS_PER_BYTE);
    }

    /**
     * Moves to the next byte once every bit of the current one is consumed.
     */
    protected void moveToNextByte() {
        bytePosition++;
        bitIndex = 0;
    }

    /**
     * Called before bits of the current byte are read, so a subclass can skip bytes that carry no payload.
     */
    protected void beforeReadingCurrentByte() {}

    protected final int currentByte() {
        if (bytePosition < 0 || bytePosition >= data.length) {
            throw new IsoBmffFormatException(
                    "Read at byte %d, outside the %d-byte data".formatted(bytePosition, data.length));
        }
        return data[bytePosition] & 0xFF;
    }

    /**
     * Skips the current byte entirely.
     */
    protected final void skipCurrentByte() {
        bytePosition++;
    }
}
