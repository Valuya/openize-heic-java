/*
 * Openize.HEIC
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of Openize.HEIC.
 *
 * Openize.HEIC is available under Openize license, which is
 * available along with Openize.HEIC sources.
 */

package io.github.valuya.heic.tests;

import static org.assertj.core.api.Assertions.assertThat;

import com.drew.lang.Rational;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import io.github.valuya.heic.ExifData;
import io.github.valuya.heic.HeicImage;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ExifDataTest {

    @Test
    void readsExifTagsOfAnIphonePhoto() throws IOException {
        HeicImage heicImage = HeicImage.load(TestSamples.samplePath("iphone_portrait_photo.heic"));
        ExifData exifData = heicImage.getExifOptional().orElseThrow();

        Object xResolution = exifData.getValueOptional(ExifIFD0Directory.class, ExifIFD0Directory.TAG_X_RESOLUTION)
                .orElseThrow();
        Object exposureTime = exifData.getValueOptional(
                        ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_EXPOSURE_TIME)
                .orElseThrow();
        Object exifImageWidth = exifData.getValueOptional(
                        ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH)
                .orElseThrow();

        assertThat(xResolution).isEqualTo(new Rational(72, 1));
        assertThat(exposureTime).isEqualTo(new Rational(1, 50));
        assertThat(exifImageWidth).isEqualTo(4032L);
    }

    @Test
    void describesATag() throws IOException {
        HeicImage heicImage = HeicImage.load(TestSamples.samplePath("iphone_portrait_photo.heic"));
        ExifData exifData = heicImage.getExifOptional().orElseThrow();

        String make = exifData.getDescriptionOptional(ExifIFD0Directory.class, ExifIFD0Directory.TAG_MAKE)
                .orElseThrow();

        assertThat(make).isEqualTo("Apple");
    }
}
