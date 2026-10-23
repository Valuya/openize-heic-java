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

import com.drew.metadata.Directory;
import com.drew.metadata.ErrorDirectory;
import com.drew.metadata.adobe.AdobeJpegDirectory;
import com.drew.metadata.avi.AviDirectory;
import com.drew.metadata.bmp.BmpHeaderDirectory;
import com.drew.metadata.eps.EpsDirectory;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifImageDirectory;
import com.drew.metadata.exif.ExifInteropDirectory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.ExifThumbnailDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.exif.PanasonicRawDistortionDirectory;
import com.drew.metadata.exif.PanasonicRawIFD0Directory;
import com.drew.metadata.exif.PanasonicRawWbInfo2Directory;
import com.drew.metadata.exif.PanasonicRawWbInfoDirectory;
import com.drew.metadata.exif.PrintIMDirectory;
import com.drew.metadata.exif.makernotes.AppleMakernoteDirectory;
import com.drew.metadata.exif.makernotes.CanonMakernoteDirectory;
import com.drew.metadata.exif.makernotes.CasioType1MakernoteDirectory;
import com.drew.metadata.exif.makernotes.CasioType2MakernoteDirectory;
import com.drew.metadata.exif.makernotes.FujifilmMakernoteDirectory;
import com.drew.metadata.exif.makernotes.KodakMakernoteDirectory;
import com.drew.metadata.exif.makernotes.KyoceraMakernoteDirectory;
import com.drew.metadata.exif.makernotes.LeicaMakernoteDirectory;
import com.drew.metadata.exif.makernotes.LeicaType5MakernoteDirectory;
import com.drew.metadata.exif.makernotes.NikonPictureControl1Directory;
import com.drew.metadata.exif.makernotes.NikonPictureControl2Directory;
import com.drew.metadata.exif.makernotes.NikonType1MakernoteDirectory;
import com.drew.metadata.exif.makernotes.NikonType2MakernoteDirectory;
import com.drew.metadata.exif.makernotes.OlympusCameraSettingsMakernoteDirectory;
import com.drew.metadata.exif.makernotes.OlympusEquipmentMakernoteDirectory;
import com.drew.metadata.exif.makernotes.OlympusFocusInfoMakernoteDirectory;
import com.drew.metadata.exif.makernotes.OlympusImageProcessingMakernoteDirectory;
import com.drew.metadata.exif.makernotes.OlympusMakernoteDirectory;
import com.drew.metadata.exif.makernotes.OlympusRawDevelopment2MakernoteDirectory;
import com.drew.metadata.exif.makernotes.OlympusRawDevelopmentMakernoteDirectory;
import com.drew.metadata.exif.makernotes.OlympusRawInfoMakernoteDirectory;
import com.drew.metadata.exif.makernotes.PanasonicMakernoteDirectory;
import com.drew.metadata.exif.makernotes.PentaxMakernoteDirectory;
import com.drew.metadata.exif.makernotes.ReconyxHyperFire2MakernoteDirectory;
import com.drew.metadata.exif.makernotes.ReconyxHyperFireMakernoteDirectory;
import com.drew.metadata.exif.makernotes.ReconyxUltraFireMakernoteDirectory;
import com.drew.metadata.exif.makernotes.RicohMakernoteDirectory;
import com.drew.metadata.exif.makernotes.SamsungType2MakernoteDirectory;
import com.drew.metadata.exif.makernotes.SanyoMakernoteDirectory;
import com.drew.metadata.exif.makernotes.SigmaMakernoteDirectory;
import com.drew.metadata.exif.makernotes.SonyType1MakernoteDirectory;
import com.drew.metadata.exif.makernotes.SonyType6MakernoteDirectory;
import com.drew.metadata.file.FileTypeDirectory;
import com.drew.metadata.gif.GifAnimationDirectory;
import com.drew.metadata.gif.GifCommentDirectory;
import com.drew.metadata.gif.GifControlDirectory;
import com.drew.metadata.gif.GifHeaderDirectory;
import com.drew.metadata.gif.GifImageDirectory;
import com.drew.metadata.icc.IccDirectory;
import com.drew.metadata.ico.IcoDirectory;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.jfif.JfifDirectory;
import com.drew.metadata.jfxx.JfxxDirectory;
import com.drew.metadata.jpeg.HuffmanTablesDirectory;
import com.drew.metadata.jpeg.JpegCommentDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import com.drew.metadata.mp3.Mp3Directory;
import com.drew.metadata.pcx.PcxDirectory;
import com.drew.metadata.photoshop.DuckyDirectory;
import com.drew.metadata.photoshop.PhotoshopDirectory;
import com.drew.metadata.photoshop.PsdHeaderDirectory;
import com.drew.metadata.png.PngChromaticitiesDirectory;
import com.drew.metadata.png.PngDirectory;
import com.drew.metadata.webp.WebpDirectory;
import com.drew.metadata.xmp.XmpDirectory;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A directory of Exif metadata, as metadata-extractor reads it. A type without a metadata-extractor directory class
 * cannot be looked up.
 */
public enum ExifDirectoryType {
    /** IFD0 directory. */
    ExifIfd0Directory(ExifIFD0Directory.class),

    /** One of several Exif directories. Holds information about image IFD's in a chain after the first. */
    ExifImageDirectory(ExifImageDirectory.class),

    /** Exif interoperability tags directory. */
    ExifInteropDirectory(ExifInteropDirectory.class),

    /** SubIFD directory. */
    ExifSubIfdDirectory(ExifSubIFDDirectory.class),

    /** IFD1 directory. Holds information about an embedded thumbnail image. */
    ExifThumbnailDirectory(ExifThumbnailDirectory.class),

    /** GPS Exif tags directory. */
    GpsDirectory(GpsDirectory.class),

    /** Directory for image encoding information for DCT filters, as stored by Adobe. */
    AdobeJpegDirectory(AdobeJpegDirectory.class),

    /** Directory for tags specific to Apple cameras. */
    AppleMakernoteDirectory(AppleMakernoteDirectory.class),

    /** Directory for basic metadata for Avi files. */
    AviDirectory(AviDirectory.class),

    /** Directory for basic metadata for Bmp files. */
    BmpHeaderDirectory(BmpHeaderDirectory.class),

    /** Directory for tags specific to Canon cameras. */
    CanonMakernoteDirectory(CanonMakernoteDirectory.class),

    /** Directory for tags specific to Casio (type 1) cameras. */
    CasioType1MakernoteDirectory(CasioType1MakernoteDirectory.class),

    /** Directory for tags specific to Casio (type 2) cameras. */
    CasioType2MakernoteDirectory(CasioType2MakernoteDirectory.class),

    /** Directory for tags specific to DJI aircraft cameras. */
    DjiMakernoteDirectory,

    /** Directory for tags from Photoshop "ducky" segments. */
    DuckyDirectory(DuckyDirectory.class),

    /** Directory for basic metadata for Eps files. */
    EpsDirectory(EpsDirectory.class),

    /** Directory for the reporting of errors. */
    ErrorDirectory(ErrorDirectory.class),

    /** Directory for OS tags. */
    FileMetadataDirectory,

    /** Directory for tags derived from filename. */
    FileTypeDirectory(FileTypeDirectory.class),

    /** Directory for camera info tags specific to FLIR cameras. */
    FlirCameraInfoDirectory,

    /** Directory for header tags specific to FLIR cameras. */
    FlirHeaderDirectory,

    /** Directory for tags specific to FLIR cameras. */
    FlirMakernoteDirectory,

    /** Directory for raw tags specific to FLIR cameras. */
    FlirRawDataDirectory,

    /** Directory for tags specific to Fujifilm cameras. */
    FujifilmMakernoteDirectory(FujifilmMakernoteDirectory.class),

    /** Directory for tiff geo tags. */
    GeoTiffDirectory,

    /** Directory for animation metadata for Gif files. */
    GifAnimationDirectory(GifAnimationDirectory.class),

    /** Directory for comment metadata for Gif files. */
    GifCommentDirectory(GifCommentDirectory.class),

    /** Directory for control metadata for Gif files. */
    GifControlDirectory(GifControlDirectory.class),

    /** Directory for header metadata for Gif files. */
    GifHeaderDirectory(GifHeaderDirectory.class),

    /** Directory for basic metadata for Gif files. */
    GifImageDirectory(GifImageDirectory.class),

    /** Directory for basic metadata for Heic files. */
    HeicImagePropertiesDirectory,

    /** Directory for basic metadata for Heic thumbnails. */
    HeicThumbnailDirectory,

    /** Directory of tables for the DHT (Define Huffman Table(s)) segment. */
    HuffmanTablesDirectory(HuffmanTablesDirectory.class),

    /** Directory for basic metadata for Icc files. */
    IccDirectory(IccDirectory.class),

    /** Directory for basic metadata for Ico files. */
    IcoDirectory(IcoDirectory.class),

    /** Directory for tags used by the International Press Telecommunications Council (IPTC) metadata format. */
    IptcDirectory(IptcDirectory.class),

    /** Directory for basic metadata for Jfif files. */
    JfifDirectory(JfifDirectory.class),

    /** Directory for basic metadata for Jfxx files. */
    JfxxDirectory(JfxxDirectory.class),

    /** Directory for comment metadata for Jpeg files. */
    JpegCommentDirectory(JpegCommentDirectory.class),

    /** Directory for basic metadata for Jpeg files. */
    JpegDirectory(JpegDirectory.class),

    /** Directory for DNL metadata for Jpeg files. */
    JpegDnlDirectory,

    /** Directory for tags specific to Kodak cameras. */
    KodakMakernoteDirectory(KodakMakernoteDirectory.class),

    /** Directory for tags specific to Kyocera cameras. */
    KyoceraMakernoteDirectory(KyoceraMakernoteDirectory.class),

    /** Directory for tags specific to Leica cameras. */
    LeicaMakernoteDirectory(LeicaMakernoteDirectory.class),

    /** Directory for tags specific to Leica (type 5) cameras. */
    LeicaType5MakernoteDirectory(LeicaType5MakernoteDirectory.class),

    /** Directory for basic metadata for Mp3 files. */
    Mp3Directory(Mp3Directory.class),

    /** Directory for header metadata for Netpbm files. */
    NetpbmHeaderDirectory,

    /** Directory for picture control tags specific to Nikon (type 1) cameras. */
    NikonPictureControl1Directory(NikonPictureControl1Directory.class),

    /** Directory for picture control tags specific to Nikon (type 1) cameras. */
    NikonPictureControl2Directory(NikonPictureControl2Directory.class),

    /** Directory for tags specific to Nikon (type 1) cameras. */
    NikonType1MakernoteDirectory(NikonType1MakernoteDirectory.class),

    /** Directory for tags specific to Nikon (type 1) cameras. */
    NikonType2MakernoteDirectory(NikonType2MakernoteDirectory.class),

    /** Directory for camera settings tags specific to Olympus cameras (Epson, Konica, Minolta and Agfa...). */
    OlympusCameraSettingsMakernoteDirectory(OlympusCameraSettingsMakernoteDirectory.class),

    /** Directory for equipment tags specific to Olympus cameras (Epson, Konica, Minolta and Agfa...). */
    OlympusEquipmentMakernoteDirectory(OlympusEquipmentMakernoteDirectory.class),

    /** Directory for focus info tags specific to Olympus cameras (Epson, Konica, Minolta and Agfa...). */
    OlympusFocusInfoMakernoteDirectory(OlympusFocusInfoMakernoteDirectory.class),

    /** Directory for image processing tags specific to Olympus cameras (Epson, Konica, Minolta and Agfa...). */
    OlympusImageProcessingMakernoteDirectory(OlympusImageProcessingMakernoteDirectory.class),

    /** Directory for tags specific to Olympus cameras (Epson, Konica, Minolta and Agfa...). */
    OlympusMakernoteDirectory(OlympusMakernoteDirectory.class),

    /** Directory for raw development 2 makernotes specific to Olympus cameras (Epson, Konica, Minolta and Agfa...). */
    OlympusRawDevelopment2MakernoteDirectory(OlympusRawDevelopment2MakernoteDirectory.class),

    /** Directory for raw development 2 makernotes specific to Olympus cameras (Epson, Konica, Minolta and Agfa...). */
    OlympusRawDevelopmentMakernoteDirectory(OlympusRawDevelopmentMakernoteDirectory.class),

    /** Directory for raw info makernotes tags specific to Olympus cameras (Epson, Konica, Minolta and Agfa...). */
    OlympusRawInfoMakernoteDirectory(OlympusRawInfoMakernoteDirectory.class),

    /** Directory for tags specific to Panasonic cameras. */
    PanasonicMakernoteDirectory(PanasonicMakernoteDirectory.class),

    /** Directory for raw distortion tags specific to Panasonic cameras. */
    PanasonicRawDistortionDirectory(PanasonicRawDistortionDirectory.class),

    /** Directory for raw Ifd0 tags specific to Panasonic cameras. */
    PanasonicRawIfd0Directory(PanasonicRawIFD0Directory.class),

    /** Directory for raw info tags specific to Panasonic cameras. */
    PanasonicRawWbInfo2Directory(PanasonicRawWbInfo2Directory.class),

    /** Directory for raw info tags specific to Panasonic cameras. */
    PanasonicRawWbInfoDirectory(PanasonicRawWbInfoDirectory.class),

    /** Directory for basic metadata for Pcx files. */
    PcxDirectory(PcxDirectory.class),

    /** Directory for tags specific to Pentax cameras. */
    PentaxMakernoteDirectory(PentaxMakernoteDirectory.class),

    /** Directory for tags specific to Pentax (type 2) cameras. */
    PentaxType2MakernoteDirectory,

    /** Directory for the metadata found in the APPD segment of a JPEG file saved by Photoshop. */
    PhotoshopDirectory(PhotoshopDirectory.class),

    /** Directory for chromaticities metadata for Png files. */
    PngChromaticitiesDirectory(PngChromaticitiesDirectory.class),

    /** Directory for basic metadata for Png files. */
    PngDirectory(PngDirectory.class),

    /** Directory for Epson proprietary metadata. */
    PrintIMDirectory(PrintIMDirectory.class),

    /** Directory for header metadata for Psd files. */
    PsdHeaderDirectory(PsdHeaderDirectory.class),

    /** Directory for QuickTime file type metadata. */
    QuickTimeFileTypeDirectory,

    /** Directory for QuickTime metadata header tags. */
    QuickTimeMetadataHeaderDirectory,

    /** Directory for QuickTime movie header tags. */
    QuickTimeMovieHeaderDirectory,

    /** Directory for QuickTime track header metadata. */
    QuickTimeTrackHeaderDirectory,

    /** Directory for tags specific to Reconyx HyperFire 2 cameras. */
    ReconyxHyperFire2MakernoteDirectory(ReconyxHyperFire2MakernoteDirectory.class),

    /** Directory for tags specific to Reconyx HyperFire cameras. */
    ReconyxHyperFireMakernoteDirectory(ReconyxHyperFireMakernoteDirectory.class),

    /** Directory for tags specific to Reconyx UltraFire cameras. */
    ReconyxUltraFireMakernoteDirectory(ReconyxUltraFireMakernoteDirectory.class),

    /** Directory for tags specific to Ricoh cameras. */
    RicohMakernoteDirectory(RicohMakernoteDirectory.class),

    /** Directory for tags specific to certain 'newer' Samsung cameras. */
    SamsungType2MakernoteDirectory(SamsungType2MakernoteDirectory.class),

    /** Directory for tags specific to Sanyo cameras. */
    SanyoMakernoteDirectory(SanyoMakernoteDirectory.class),

    /** Directory for tags specific to Sigma cameras. */
    SigmaMakernoteDirectory(SigmaMakernoteDirectory.class),

    /** Directory for tags specific to Sony (type 1) cameras. */
    SonyType1MakernoteDirectory(SonyType1MakernoteDirectory.class),

    /** Directory for tags specific to Sony (type 6) cameras. */
    SonyType6MakernoteDirectory(SonyType6MakernoteDirectory.class),

    /** Directory for developer metadata for Tga files. */
    TgaDeveloperDirectory,

    /** Directory for extention metadata for Tga files. */
    TgaExtensionDirectory,

    /** Directory for header metadata for Tga files. */
    TgaHeaderDirectory,

    /** Directory for fact metadata for Wav files. */
    WavFactDirectory,

    /** Directory for format metadata for Wav files. */
    WavFormatDirectory,

    /** Directory for basic metadata for WebP files. */
    WebPDirectory(WebpDirectory.class),

    /** Directory for basic metadata for Xmp files. */
    XmpDirectory(XmpDirectory.class),

    /** Undefined directory. */
    Undefined;

    private final @Nullable Class<? extends Directory> directoryClass;

    ExifDirectoryType() {
        directoryClass = null;
    }

    ExifDirectoryType(Class<? extends Directory> directoryClass) {
        this.directoryClass = directoryClass;
    }

    /**
     * The metadata-extractor class of the directory, if there is one.
     */
    Optional<Class<? extends Directory>> getDirectoryClassOptional() {
        return Optional.ofNullable(directoryClass);
    }
}
