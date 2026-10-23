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

/**
 * The type of an item of a HEIF file, by its four-character code (ISO/IEC 23008-12).
 */
public enum ImageFrameType {
    /** An item of a type this decoder does not know. */
    unknown(0),
    /** An HEVC coded image. */
    hvc1(0x68766331),
    /** An identity derivation: one input image, cropped, rotated or mirrored by its properties. */
    iden(0x6964656e),
    /** An overlay of input images onto a canvas. */
    iovl(0x696f766c),
    /** A grid of input images of the same size. */
    grid(0x67726964),
    /** Exif metadata. */
    exif(0x45786966),
    /** Metadata of a MIME type, such as XMP. */
    mime(0x6d696d65),
    /** A tone map derivation, for high dynamic range rendering. */
    tmap(0x746d6170),
    /** Metadata identified by a URI. */
    uri(0x75726920);

    private final int code;

    ImageFrameType(int code) {
        this.code = code;
    }

    static ImageFrameType codeToType(long code) {
        for (ImageFrameType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return unknown;
    }

    /** The four-character code. */
    public int getCode() {
        return code;
    }
}
