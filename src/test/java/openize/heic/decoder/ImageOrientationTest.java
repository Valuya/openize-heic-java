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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ImageOrientationTest {
    private static final int CROPPED_WIDTH = 5;
    private static final int CROPPED_HEIGHT = 3;

    static Stream<Arguments> orientations() {
        return IntStream.range(0, 4)
                .boxed()
                .flatMap(quarterTurns ->
                        IntStream.range(0, 3).mapToObj(mirrorAxis -> Arguments.of(quarterTurns, mirrorAxis)));
    }

    @ParameterizedTest
    @MethodSource("orientations")
    void placesEveryPixelWhereTheRotateThenMirrorDefinitionPutsIt(int quarterTurns, int mirrorAxis) {
        ImageOrientation imageOrientation = new ImageOrientation(quarterTurns, mirrorAxis);
        int orientedWidth = imageOrientation.swapsWidthAndHeight() ? CROPPED_HEIGHT : CROPPED_WIDTH;
        int orientedHeight = imageOrientation.swapsWidthAndHeight() ? CROPPED_WIDTH : CROPPED_HEIGHT;
        AxisTransform toOrientedImage = imageOrientation.toOrientedImage(orientedWidth, orientedHeight);

        for (int orientedY = 0; orientedY < orientedHeight; orientedY++) {
            for (int orientedX = 0; orientedX < orientedWidth; orientedX++) {
                int[] sourcePixel = definitionSourcePixel(
                        quarterTurns, mirrorAxis, orientedX, orientedY, orientedWidth, orientedHeight);
                int mappedX = toOrientedImage.mapX(sourcePixel[0], sourcePixel[1]);
                int mappedY = toOrientedImage.mapY(sourcePixel[0], sourcePixel[1]);

                assertThat(new int[] {mappedX, mappedY})
                        .as("cropped pixel (%d, %d)", sourcePixel[0], sourcePixel[1])
                        .containsExactly(orientedX, orientedY);
            }
        }
    }

    @Test
    void composesAndInvertsTransforms() {
        AxisTransform quarterTurn = new ImageOrientation(1, 0).toOrientedImage(CROPPED_HEIGHT, CROPPED_WIDTH);
        AxisTransform placement = AxisTransform.translation(7, -2);
        AxisTransform quarterTurnThenPlacement = quarterTurn.andThen(placement);
        AxisTransform roundTrip = quarterTurnThenPlacement.andThen(quarterTurnThenPlacement.inverse());

        assertThat(roundTrip).isEqualTo(AxisTransform.IDENTITY);
        assertThat(quarterTurnThenPlacement.map(PixelBox.ofSize(CROPPED_WIDTH, CROPPED_HEIGHT)))
                .isEqualTo(new PixelBox(7, -2, 7 + CROPPED_HEIGHT, -2 + CROPPED_WIDTH));
    }

    // The orientation as the decoder historically applied it: for each pixel of the oriented image, the cropped
    // image pixel it shows
    private static int[] definitionSourcePixel(
            int quarterTurns, int mirrorAxis, int orientedX, int orientedY, int width, int height) {
        int sourceX;
        int sourceY;
        switch (quarterTurns) {
            case 3 -> {
                sourceX = orientedY;
                sourceY = width - orientedX - 1;
            }
            case 2 -> {
                sourceX = width - orientedX - 1;
                sourceY = height - orientedY - 1;
            }
            case 1 -> {
                sourceX = height - orientedY - 1;
                sourceY = orientedX;
            }
            default -> {
                sourceX = orientedX;
                sourceY = orientedY;
            }
        }

        boolean evenQuarterTurns = quarterTurns == 0 || quarterTurns == 2;
        if (mirrorAxis == 1) {
            if (evenQuarterTurns) {
                sourceY = height - sourceY - 1;
            } else {
                sourceX = height - sourceX - 1;
            }
        } else if (mirrorAxis == 2) {
            if (evenQuarterTurns) {
                sourceX = width - sourceX - 1;
            } else {
                sourceY = width - sourceY - 1;
            }
        }
        return new int[] {sourceX, sourceY};
    }
}
