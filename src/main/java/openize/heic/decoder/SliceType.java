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
 * slice_type (Table 7-7): B, P or I.
 */
enum SliceType {
    B,
    P,
    I;

    static SliceType get(int sliceType) {
        return switch (sliceType) {
            case 0 -> B;
            case 1 -> P;
            case 2 -> I;
            default -> throw new HeicFormatException("Unknown slice type " + sliceType);
        };
    }
}
