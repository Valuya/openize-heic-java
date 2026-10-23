/*
 * FileFormat.IsoBmff
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of FileFormat.IsoBmff.
 *
 * FileFormat.IsoBmff is available under MIT license, which is
 * available along with FileFormat.IsoBmff sources.
 */

package openize.isobmff;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Box types, from ISO/IEC 14496-12, ISO/IEC 14496-15 and ISO/IEC 23008-12. Each is named after its four-character
 * code.
 */
public enum BoxType {
    /** A type this package has no name for. */
    unknown(0),

    /** File type and compatibility, at the top level. */
    ftyp(0x66747970),

    /** Media data, at the top level. */
    mdat(0x6d646174),

    /** Free space. */
    free(0x66726565),

    /** Free space. */
    skip(0x736b6970),

    /** Movie metadata, at the top level. */
    moov(0x6d6f6f76),

    /** Metadata, at the top level. */
    meta(0x6d657461),

    /** Handler, declaring the type of the metadata, in 'meta'. */
    hdlr(0x68646c72),

    /** Data information, in 'meta'. */
    dinf(0x64696e66),

    /** Data references, declaring the sources of items, in 'dinf'. */
    dref(0x64726566),

    /** A data reference by URL, in 'dref'. */
    url(0x75726c20),

    /** A data reference by URN, in 'dref'. */
    urn(0x75726e20),

    /** Item location, in 'meta'. */
    iloc(0x696c6f63),

    /** Item data, in 'meta'. */
    idat(0x69646174),

    /** Item information, in 'meta'. */
    iinf(0x69696e66),

    /** Item information entry, in 'iinf'. */
    infe(0x696e6665),

    /** Item references, in 'meta'. */
    iref(0x69726566),

    /** Derived image reference, in 'iref'. */
    dimg(0x64696d67),

    /** Auxiliary image reference, such as an alpha plane or a depth map, in 'iref'. */
    auxl(0x6175786c),

    /** Thumbnail reference, in 'iref'. */
    thmb(0x74686d62),

    /** Primary item, in 'meta'. */
    pitm(0x7069746d),

    /** Item properties, in 'meta'. */
    iprp(0x69707270),

    /** Item property association, in 'iprp'. */
    ipma(0x69706d61),

    /** Item property container, in 'iprp'. */
    ipco(0x6970636f),

    /** AV1 decoder configuration property, in 'ipco'. */
    av1C(0x61763143),

    /** HEVC decoder configuration property, in 'ipco'. */
    hvcC(0x68766343),

    /** Image spatial extents property, in 'ipco'. */
    ispe(0x69737065),

    /** Pixel aspect ratio property, in 'ipco'. */
    pasp(0x70617370),

    /** Colour information property, in 'ipco'. */
    colr(0x636f6c72),

    /** Content light level property, in 'ipco'. */
    clli(0x636c6c69),

    /** Pixel information property, in 'ipco'. */
    pixi(0x70697869),

    /** Relative location property, in 'ipco'. */
    rloc(0x726c6f63),

    /** Auxiliary type property, in 'ipco'. */
    auxC(0x61757843),

    /** Clean aperture property, in 'ipco'. */
    clap(0x636c6170),

    /** Image rotation property, in 'ipco'. */
    irot(0x69726f74),

    /** Layer selection property, in 'ipco'. */
    lsel(0x6c73656c),

    /** Image mirroring property, in 'ipco'. */
    imir(0x696d6972),

    /** Operating points information property, in 'ipco'. */
    oinf(0x6f696e66),

    /** User description property, in 'ipco'. */
    udes(0x75646573),

    /** Content description reference, in 'iref'. */
    cdsc(0x63647363),

    /** Groups list, in 'meta'. */
    grpl(0x6772706c),

    /** Alternatives entity group, in 'grpl'. */
    altr(0x616c7472),

    /** Stereo pair entity group, in 'grpl'. */
    ster(0x73746572),

    /** Time-synchronised capture entity group, in 'grpl'. */
    tsyn(0x7473796e),

    /** Item protection, in 'meta'. */
    ipro(0x6970726f),

    /** Protection scheme information, in 'ipro'. */
    sinf(0x73696e66),

    /** Original format, in 'sinf'. */
    frma(0x66726d61),

    /** Scheme type, in 'sinf'. */
    schm(0x7363686d),

    /** Scheme information, in 'sinf'. */
    schi(0x73636869),

    /** A box with an extended type. */
    uuid(0x75756964);

    private static final Map<Integer, BoxType> TYPES_BY_CODE =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(BoxType::getCode, Function.identity()));

    private final int code;

    BoxType(int code) {
        this.code = code;
    }

    /**
     * The type with this four-character code, or {@link #unknown}.
     */
    public static BoxType codeToType(int code) {
        return TYPES_BY_CODE.getOrDefault(code, unknown);
    }

    /**
     * The four-character code of the type, one character per byte.
     */
    public int getCode() {
        return code;
    }
}
