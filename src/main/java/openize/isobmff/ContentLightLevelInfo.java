/*
 * Openize.IsoBmff
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of Openize.IsoBmff.
 *
 * Openize.IsoBmff is available under MIT license, which is
 * available along with Openize.IsoBmff sources.
 */

package openize.isobmff;

import openize.isobmff.io.BitStreamReader;


/**
 * <p>
 * Contains light level information about the image.
 * </p>
 */
public class ContentLightLevelInfo extends Box
{
    /**
     * <p>
     * Indicates the max picture ligth level.
     * </p>
     */
    public /*UInt16*/ int maxContentLightLevel;

    /**
     * <p>
     * Indicates the picture average ligth level.
     * </p>
     */
    public /*UInt16*/ int maxPicAverageLightLevel;

    /**
     * <p>
     * Create the box object from the bitstream and box size.
     * </p>
     *
     * @param stream File stream.
     * @param size   Box size in bytes.
     */
    public ContentLightLevelInfo(BitStreamReader stream, /*UInt64*/long size)
    {
        super(BoxType.clli, size);

        maxContentLightLevel = stream.read(16) & 0xFFFF;
        maxPicAverageLightLevel = stream.read(16) & 0xFFFF;
    }

    /**
     * <p>
     * Text summary of the box.
     * </p>
     */
    @Override
    public final String toString()
    {
        return String.format("%s Max: %d Avg: %d", type.name(), maxContentLightLevel, maxPicAverageLightLevel);
    }
}
