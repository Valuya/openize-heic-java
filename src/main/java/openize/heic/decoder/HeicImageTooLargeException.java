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
 * The image, or one of the pictures it is coded in, has more pixels than {@link HeicDecodingOptions#maxPixelCount}.
 */
public class HeicImageTooLargeException extends HeicFormatException {
    public HeicImageTooLargeException(String message) {
        super(message);
    }
}
