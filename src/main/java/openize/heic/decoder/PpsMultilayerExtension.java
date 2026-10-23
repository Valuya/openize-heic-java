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

import openize.heic.decoder.io.BitStreamWithNalSupport;

class PpsMultilayerExtension {
    public PpsMultilayerExtension(BitStreamWithNalSupport stream) {
        throw new HeicFormatException("The PPS multilayer extension is not supported");
    }
}
