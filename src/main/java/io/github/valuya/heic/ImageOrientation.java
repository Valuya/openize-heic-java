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
 * The image rotation ({@code irot}) and mirroring ({@code imir}) of a frame, applied after its clean aperture crop.
 *
 * @param quarterTurns anticlockwise rotation in quarter turns, 0 to 3
 * @param mirrorAxis 0 for none, 1 for a vertical axis (top and bottom swap), 2 for a horizontal axis (left and right
 *     swap), the axis being taken after the rotation
 */
record ImageOrientation(int quarterTurns, int mirrorAxis) {
    static final ImageOrientation NONE = new ImageOrientation(0, 0);

    boolean swapsWidthAndHeight() {
        return quarterTurns % 2 == 1;
    }

    /**
     * Maps a pixel of the cropped image to its place in the oriented image of the given size.
     */
    AxisTransform toOrientedImage(int orientedWidth, int orientedHeight) {
        // sourceX/sourceY give, for a pixel of the oriented image, the cropped image pixel it shows; that is affine,
        // so three pixels define it
        int originX = sourceX(0, 0, orientedWidth, orientedHeight);
        int originY = sourceY(0, 0, orientedWidth, orientedHeight);
        AxisTransform fromOrientedImage = new AxisTransform(
                sourceX(1, 0, orientedWidth, orientedHeight) - originX,
                sourceX(0, 1, orientedWidth, orientedHeight) - originX,
                sourceY(1, 0, orientedWidth, orientedHeight) - originY,
                sourceY(0, 1, orientedWidth, orientedHeight) - originY,
                originX,
                originY);
        return fromOrientedImage.inverse();
    }

    private int sourceX(int orientedX, int orientedY, int orientedWidth, int orientedHeight) {
        int rotatedX = switch (quarterTurns) {
            case 1 -> orientedHeight - orientedY - 1;
            case 2 -> orientedWidth - orientedX - 1;
            case 3 -> orientedY;
            default -> orientedX;
        };
        boolean mirrorsX = (mirrorAxis == 1 && swapsWidthAndHeight()) || (mirrorAxis == 2 && !swapsWidthAndHeight());
        int mirroredSize = mirrorAxis == 1 ? orientedHeight : orientedWidth;
        return mirrorsX ? mirroredSize - rotatedX - 1 : rotatedX;
    }

    private int sourceY(int orientedX, int orientedY, int orientedWidth, int orientedHeight) {
        int rotatedY = switch (quarterTurns) {
            case 1 -> orientedX;
            case 2 -> orientedHeight - orientedY - 1;
            case 3 -> orientedWidth - orientedX - 1;
            default -> orientedY;
        };
        boolean mirrorsY = (mirrorAxis == 1 && !swapsWidthAndHeight()) || (mirrorAxis == 2 && swapsWidthAndHeight());
        int mirroredSize = mirrorAxis == 1 ? orientedHeight : orientedWidth;
        return mirrorsY ? mirroredSize - rotatedY - 1 : rotatedY;
    }
}
