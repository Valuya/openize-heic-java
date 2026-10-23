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

/**
 * Reads bits, most significant first, from a file held in memory.
 *
 * <p>A reader is cheap: several of them can read the same array independently, which lets the images of a file be
 * decoded concurrently. Bits past the end of the data read as zero.
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
        this.bytePosition = Math.toIntExact(bytePosition);
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
            throw new IndexOutOfBoundsException("bitCount " + bitCount);
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
        bytePosition = Math.toIntExact(targetBitPosition / BITS_PER_BYTE);
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
        return bytePosition < data.length ? data[bytePosition] & 0xFF : 0;
    }

    /**
     * Skips the current byte entirely.
     */
    protected final void skipCurrentByte() {
        bytePosition++;
    }
}
