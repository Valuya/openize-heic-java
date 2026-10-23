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
 * The intra prediction modes predModeIntra of Table 8-1: 0 planar, 1 DC, 2 to 34 angular, from bottom-left to
 * top-right.
 */
final class IntraPredMode {
    static final int INTRA_PLANAR = 0;
    static final int INTRA_DC = 1;
    static final int INTRA_ANGULAR2 = 2;
    // the horizontal and vertical directions
    static final int INTRA_ANGULAR10 = 10;
    static final int INTRA_ANGULAR26 = 26;
    static final int INTRA_ANGULAR34 = 34;

    private IntraPredMode() {}
}
