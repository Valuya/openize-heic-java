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

public class SliceSegmentData {
    // private final List<coding_tree_unit> CTUs = new ArrayList<>(15);

    public SliceSegmentData(BitStreamWithNalSupport stream, SliceSegmentHeader header) {
        boolean endOfSliceSegmentFlag;
        SeqParameterSetRbsp sps = header.pps.sps;
        HeicPicture picture = header.parentPicture;

        if (picture.sliceAddrRs == null) {
            picture.sliceAddrRs =
                    new /*UInt32*/ int[(int) (sps.getPicWidthInCtbsY())][(int) (sps.getPicHeightInCtbsY())];
        }

        do {
            int xCtu = (int) ((header.getCtbAddrInRs() & 0xFFFFFFFFL) % (sps.getPicWidthInCtbsY() & 0xFFFFFFFFL));
            int yCtu = (int) ((header.getCtbAddrInRs() & 0xFFFFFFFFL) / (sps.getPicWidthInCtbsY() & 0xFFFFFFFFL));

            if (header.pps.entropyCodingSyncEnabledFlag && xCtu == 0 && yCtu > 0) {
                stream.getCabac().restoreSyncedTables();
            }

            CodingTreeUnit ctu = new CodingTreeUnit(stream, header);
            // CTUs.add(ctu);

            if (header.pps.entropyCodingSyncEnabledFlag
                    && xCtu == 1
                    && yCtu < (((sps.getPicHeightInCtbsY() & 0xFFFFFFFFL) - 1) & 0xFFFFFFFFL)) {
                stream.getCabac().syncTables();
            }

            endOfSliceSegmentFlag = stream.getCabac().readEndOfSliceSegmentFlag();

            header.setCtbAddrInTs(header.getCtbAddrInTs() + 1) /*Property++*/;
            if ((header.getCtbAddrInTs() & 0xFFFFFFFFL) < (sps.getPicSizeInCtbsY() & 0xFFFFFFFFL)) {
                header.setCtbAddrInRs(header.pps.ctbAddrTsToRs[(int) (header.getCtbAddrInTs())]);
            }

            if (header.getCtbAddrInTs() == sps.getPicSizeInCtbsY()) {
                return;
            }

            if (!endOfSliceSegmentFlag
                    && ((header.pps.tilesEnabledFlag
                                    && (header.pps.tileIdByCtbAddrTs[(int) (header.getCtbAddrInTs())] & 0xFFFFFFFFL)
                                            != (header.pps
                                                            .tileIdByCtbAddrTs[
                                                            (int) (((header.getCtbAddrInTs() & 0xFFFFFFFFL) - 1)
                                                                    & 0xFFFFFFFFL)]
                                                    & 0xFFFFFFFFL))
                            || (header.pps.entropyCodingSyncEnabledFlag
                                    && ((((header.getCtbAddrInRs() & 0xFFFFFFFFL)
                                                                    % (sps.getPicWidthInCtbsY() & 0xFFFFFFFFL))
                                                            & 0xFFFFFFFFL)
                                                    == 0
                                            || (header.pps.tileIdByCtbAddrTs[(int) (header.getCtbAddrInTs())]
                                                            & 0xFFFFFFFFL)
                                                    != (header.pps
                                                                    .tileIdByCtbAddrTs[
                                                                    (int) (header.pps
                                                                            .ctbAddrRsToTs[
                                                                            (int) (((header.getCtbAddrInRs()
                                                                                                    & 0xFFFFFFFFL)
                                                                                            - 1)
                                                                                    & 0xFFFFFFFFL)])]
                                                            & 0xFFFFFFFFL))))) {
                int one = stream.getCabac().readEndOfSubsetOneBit(); /* equal to 1; */

                if (one != 1) throw new IllegalStateException("Unexpected slice-end bit!");

                while (stream.notByteAligned()) stream.skipBits(1);

                stream.getCabac().initialization(header);
            }

        } while (!endOfSliceSegmentFlag);
    }
}
