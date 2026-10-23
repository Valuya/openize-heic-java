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

class SpsMultilayerExtension {
    public SpsMultilayerExtension(BitStreamWithNalSupport stream) {
        throw new HeicFormatException("The SPS multilayer extension is not supported");
    }
}
