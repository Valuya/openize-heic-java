/*
 * FileFormat.IsoBmff
 * Copyright (c) 2024-2026 Openize Pty Ltd.
 *
 * This file is part of FileFormat.IsoBmff.
 *
 * FileFormat.IsoBmff is available under MIT license, which is
 * available along with FileFormat.IsoBmff sources.
 */

package io.github.valuya.heic.isobmff;

/**
 * A piece of the data of an item (ISO/IEC 14496-12, 8.11.3).
 *
 * @param offset where the piece starts, in bytes, from the base offset of the item
 * @param length the length of the piece in bytes; 0 for the whole container
 */
public record IlocItemExtent(long offset, long length) {}
