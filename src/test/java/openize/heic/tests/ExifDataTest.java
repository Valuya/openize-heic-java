/*
 * Openize.HEIC
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of Openize.HEIC.
 *
 * Openize.HEIC is available under Openize license, which is
 * available along with Openize.HEIC sources.
 */

package openize.heic.tests;

import com.drew.lang.Rational;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import openize.heic.decoder.ExifData;
import openize.heic.decoder.ExifDirectoryType;
import openize.heic.decoder.HeicImage;
import openize.io.IOFileStream;
import openize.io.IOMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExifDataTest {

    @Test
    void readsExifTagsOfAnIphonePhoto() {
        try (IOFileStream heicStream =
                new IOFileStream(TestSamples.samplePath("iphone_portrait_photo.heic"), IOMode.READ)) {
            HeicImage heicImage = HeicImage.load(heicStream);
            ExifData exifData = heicImage.getExif();

            Object xResolution =
                    exifData.getExifRawData(ExifDirectoryType.ExifIfd0Directory, ExifIFD0Directory.TAG_X_RESOLUTION);
            Object exposureTime = exifData.getExifRawData(
                    ExifDirectoryType.ExifSubIfdDirectory, ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
            Object exifImageWidth = exifData.getExifRawData(
                    ExifDirectoryType.ExifSubIfdDirectory, ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH);

            assertThat(xResolution).isEqualTo(new Rational(72, 1));
            assertThat(exposureTime).isEqualTo(new Rational(1, 50));
            assertThat(exifImageWidth).isEqualTo(4032L);
        }
    }
}
