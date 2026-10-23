/*
 * FileFormat.IsoBmff
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of FileFormat.IsoBmff.
 *
 * FileFormat.IsoBmff is available under MIT license, which is
 * available along with FileFormat.IsoBmff sources.
 */

package openize.isobmff;

import openize.isobmff.io.BitStreamReader;

/**
 * <p>
 * This box identifies the specifications to which this file complies.
 * </p>
 */
public class FileTypeBox extends Box {
    /**
     * <p>
     * A brand identifier.
     * <p>Each brand is a printable four‐character code, registered with ISO, that identifies a precise specification.</p>
     * </p>
     */
    public final /*UInt32*/ long majorBrand;

    /**
     * <p>
     * An informative integer for the minor version of the major brand.
     * <p>Each brand is a printable four‐character code, registered with ISO, that identifies a precise specification.</p>
     * </p>
     */
    public final /*UInt32*/ long minorVersion;

    /**
     * <p>
     * A list of compatible brands.
     * <p>Each brand is a printable four‐character code, registered with ISO, that identifies a precise specification.</p>
     * </p>
     */
    public final /*UInt32*/ long[] compatibleBrands;

    /**
     * <p>
     * Create the box object from the bitstream and box size.
     * </p>
     *
     * @param stream File stream.
     * @param size   Box size in bytes.
     */
    public FileTypeBox(BitStreamReader stream, /*UInt64*/ long size) {
        super(BoxType.ftyp, size);

        majorBrand = stream.read(32) & 0xFFFFFFFFL;
        minorVersion = stream.read(32) & 0xFFFFFFFFL;

        compatibleBrands = new /*UInt32*/ long[(int) (size / 4 - 4)];
        for (
        /*UInt64*/ long i = 4; i < size / 4; i++) compatibleBrands[(int) (i - 4)] = stream.read(32) & 0xFFFFFFFFL;
    }

    /**
     * <p>
     * Text summary of the box.
     * </p>
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder(50);
        str.append(String.format(
                "{%s}\nmajor: %s \nminor: %s \ncompatible:",
                this.type, uintToString(majorBrand), uintToString(minorVersion)));
        for (/*UInt32*/ long b : compatibleBrands) {
            str.append(' ').append(uintToString(b));
        }

        return str.toString();
    }

    /**
     * <p>
     * Checks if specified brand is supported.
     * </p>
     *
     * @param brand The specified brand.
     * @return True if brand is supported, false otherwise.
     */
    public final boolean isBrandSupported(/*UInt32*/ long brand) {
        if (majorBrand == brand) {
            return true;
        }

        for (long compatibleBrand : compatibleBrands) {
            if (compatibleBrand == brand) {
                return true;
            }
        }

        return false;
    }
}
