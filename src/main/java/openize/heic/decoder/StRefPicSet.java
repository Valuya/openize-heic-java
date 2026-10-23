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

import java.util.Arrays;

/**
 * st_ref_pic_set( stRpsIdx ) (7.3.7) and the variables derived from it (7.4.8). Only P and B slices use the
 * reference pictures, but a set predicted from another needs that set's derived values to be parsed.
 */
class StRefPicSet {
    // at most 16 pictures before and 16 after the current one: the largest DPB (A.4.2)
    private static final int MAX_PICTURE_COUNT = 16;
    // abs_delta_rps_minus1, delta_poc_s0_minus1 and delta_poc_s1_minus1 (7.4.8)
    private static final int MAX_DELTA_POC_MINUS1 = (1 << 15) - 1;

    // DeltaPocS0 and UsedByCurrPicS0: the pictures before the current one, closest first
    private final int[] deltaPocS0;
    private final boolean[] usedByCurrPicS0;
    // DeltaPocS1 and UsedByCurrPicS1: the pictures after the current one, closest first
    private final int[] deltaPocS1;
    private final boolean[] usedByCurrPicS1;

    /**
     * Reads the set {@code stRpsIdx} of {@code sps}, or the set of a slice header when {@code stRpsIdx} is
     * num_short_term_ref_pic_sets. A predicted set refers to a set of {@code sps} read before.
     */
    StRefPicSet(BitStreamWithNalSupport stream, SeqParameterSetRbsp sps, int stRpsIdx) {
        boolean interRefPicSetPredictionFlag = stRpsIdx != 0 && stream.readFlag();
        if (interRefPicSetPredictionFlag) {
            // delta_idx_minus1 is only coded in a slice header, and inferred to be 0 in an SPS
            int deltaIdxMinus1 = stRpsIdx == sps.numShortTermRefPicSets ? stream.readUevAtMost(stRpsIdx - 1) : 0;
            StRefPicSet referenceSet = sps.stRefPicSets.get(stRpsIdx - (deltaIdxMinus1 + 1));
            boolean deltaRpsSign = stream.readFlag();
            int absDeltaRps = stream.readUevAtMost(MAX_DELTA_POC_MINUS1) + 1;
            int deltaRps = deltaRpsSign ? -absDeltaRps : absDeltaRps;

            // used_by_curr_pic_flag and use_delta_flag for each picture of the reference set, then for the
            // reference picture itself
            int candidateCount = referenceSet.getNumDeltaPocs() + 1;
            boolean[] usedByCurrPicFlag = new boolean[candidateCount];
            boolean[] useDeltaFlag = new boolean[candidateCount];
            for (int j = 0; j < candidateCount; j++) {
                usedByCurrPicFlag[j] = stream.readFlag();
                // inferred to be 1 when absent (7.4.8)
                useDeltaFlag[j] = usedByCurrPicFlag[j] || stream.readFlag();
            }

            PictureList before = new PictureList(candidateCount);
            PictureList after = new PictureList(candidateCount);
            referenceSet.predict(deltaRps, usedByCurrPicFlag, useDeltaFlag, before, after);
            deltaPocS0 = before.getDeltaPocs();
            usedByCurrPicS0 = before.getUsedByCurrPic();
            deltaPocS1 = after.getDeltaPocs();
            usedByCurrPicS1 = after.getUsedByCurrPic();
        } else {
            int numNegativePics = stream.readUevAtMost(MAX_PICTURE_COUNT);
            int numPositivePics = stream.readUevAtMost(MAX_PICTURE_COUNT);
            deltaPocS0 = new int[numNegativePics];
            usedByCurrPicS0 = new boolean[numNegativePics];
            readExplicitPictures(stream, -1, deltaPocS0, usedByCurrPicS0);
            deltaPocS1 = new int[numPositivePics];
            usedByCurrPicS1 = new boolean[numPositivePics];
            readExplicitPictures(stream, 1, deltaPocS1, usedByCurrPicS1);
        }

        if (deltaPocS0.length > MAX_PICTURE_COUNT || deltaPocS1.length > MAX_PICTURE_COUNT) {
            throw new HeicFormatException("A reference picture set of %d + %d pictures, more than %d of each"
                    .formatted(deltaPocS0.length, deltaPocS1.length, MAX_PICTURE_COUNT));
        }
    }

    // delta_poc_s0_minus1 / delta_poc_s1_minus1 and the used_by_curr_pic flags, each POC a step further (7-65 to 7-68)
    private static void readExplicitPictures(
            BitStreamWithNalSupport stream, int direction, int[] deltaPocs, boolean[] usedByCurrPic) {
        int deltaPoc = 0;
        for (int i = 0; i < deltaPocs.length; i++) {
            deltaPoc += direction * (stream.readUevAtMost(MAX_DELTA_POC_MINUS1) + 1);
            deltaPocs[i] = deltaPoc;
            usedByCurrPic[i] = stream.readFlag();
        }
    }

    // The pictures of a set predicted from this one (7-61, 7-62). Candidate j is picture j of this set, S0 then S1,
    // and the last candidate is the picture this set belongs to; each moves by deltaRps.
    private void predict(
            int deltaRps, boolean[] usedByCurrPicFlag, boolean[] useDeltaFlag, PictureList before, PictureList after) {
        int numNegativePics = getNumNegativePics();
        int numDeltaPocs = getNumDeltaPocs();

        for (int j = getNumPositivePics() - 1; j >= 0; j--) {
            before.addIfBefore(deltaPocS1[j] + deltaRps, numNegativePics + j, usedByCurrPicFlag, useDeltaFlag);
        }
        before.addIfBefore(deltaRps, numDeltaPocs, usedByCurrPicFlag, useDeltaFlag);
        for (int j = 0; j < numNegativePics; j++) {
            before.addIfBefore(deltaPocS0[j] + deltaRps, j, usedByCurrPicFlag, useDeltaFlag);
        }

        for (int j = numNegativePics - 1; j >= 0; j--) {
            after.addIfAfter(deltaPocS0[j] + deltaRps, j, usedByCurrPicFlag, useDeltaFlag);
        }
        after.addIfAfter(deltaRps, numDeltaPocs, usedByCurrPicFlag, useDeltaFlag);
        for (int j = 0; j < getNumPositivePics(); j++) {
            after.addIfAfter(deltaPocS1[j] + deltaRps, numNegativePics + j, usedByCurrPicFlag, useDeltaFlag);
        }
    }

    final int getNumNegativePics() {
        return deltaPocS0.length;
    }

    final int getNumPositivePics() {
        return deltaPocS1.length;
    }

    final int getNumDeltaPocs() {
        return getNumNegativePics() + getNumPositivePics();
    }

    final boolean[] getUsedByCurrPicS0() {
        return usedByCurrPicS0;
    }

    final boolean[] getUsedByCurrPicS1() {
        return usedByCurrPicS1;
    }

    // The pictures of one list of a predicted set, as the derivation adds them
    private static final class PictureList {
        private final int[] deltaPocs;
        private final boolean[] usedByCurrPic;
        private int count;

        PictureList(int capacity) {
            deltaPocs = new int[capacity];
            usedByCurrPic = new boolean[capacity];
        }

        void addIfBefore(int deltaPoc, int candidate, boolean[] usedByCurrPicFlag, boolean[] useDeltaFlag) {
            if (deltaPoc < 0 && useDeltaFlag[candidate]) {
                add(deltaPoc, usedByCurrPicFlag[candidate]);
            }
        }

        void addIfAfter(int deltaPoc, int candidate, boolean[] usedByCurrPicFlag, boolean[] useDeltaFlag) {
            if (deltaPoc > 0 && useDeltaFlag[candidate]) {
                add(deltaPoc, usedByCurrPicFlag[candidate]);
            }
        }

        private void add(int deltaPoc, boolean used) {
            deltaPocs[count] = deltaPoc;
            usedByCurrPic[count] = used;
            count++;
        }

        int[] getDeltaPocs() {
            return Arrays.copyOf(deltaPocs, count);
        }

        boolean[] getUsedByCurrPic() {
            return Arrays.copyOf(usedByCurrPic, count);
        }
    }
}
