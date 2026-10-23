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
 * The data is not a HEIC image this decoder can read: wrong signature, malformed or truncated structure, or a feature
 * it does not support.
 */
public class HeicFormatException extends RuntimeException {
    public HeicFormatException(String message) {
        super(message);
    }

    public HeicFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
