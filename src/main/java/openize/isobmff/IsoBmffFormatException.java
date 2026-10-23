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

/**
 * The data is not a well-formed ISO base media file: a box overruns its parent, or is not the box its position
 * requires.
 */
public class IsoBmffFormatException extends RuntimeException {
    public IsoBmffFormatException(String message) {
        super(message);
    }
}
