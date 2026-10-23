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

class StRefPicSet {
    final /*UInt32*/ long stRpsIdx;

    boolean interRefPicSetPredictionFlag;
    /*UInt32*/ long deltaIdxMinus1;
    byte deltaRpsSign;
    /*UInt32*/ long absDeltaRpsMinus1;
    // boolean[] used_by_curr_pic_flag;
    // boolean[] use_delta_flag;
    /*UInt32*/ long numNegativePics;
    /*UInt32*/ long numPositivePics;
    /*UInt32*/ long[] deltaPocS0Minus1;
    boolean[] usedByCurrPicS0Flag;
    /*UInt32*/ long[] deltaPocS1Minus1;
    boolean[] usedByCurrPicS1Flag;

    StRefPicSet(BitStreamWithNalSupport stream, SeqParameterSetRbsp sps, /*UInt32*/ long stRpsIdx) {
        this.stRpsIdx = stRpsIdx;

        if ((stRpsIdx & 0xFFFFFFFFL) != 0) {
            interRefPicSetPredictionFlag = stream.readFlag(); // u(1)
        }

        if (interRefPicSetPredictionFlag) {
            if (stRpsIdx == sps.numShortTermRefPicSets) {
                deltaIdxMinus1 = stream.readUev(); // ue(v)
            }

            deltaRpsSign = (byte) stream.read(1); // u(1)
            absDeltaRpsMinus1 = stream.readUev(); // ue(v)
            /*UInt32*/
            long refRpsIdx =
                    ((stRpsIdx & 0xFFFFFFFFL) - (((deltaIdxMinus1 & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL)) & 0xFFFFFFFFL;

            //            used_by_curr_pic_flag = new boolean[(int)(((sps.numDeltaPocs((int)RefRpsIdx) & 0xFFFFFFFFL) +
            // 1) & 0xFFFFFFFFL)];
            //            use_delta_flag = new boolean[(int)(((sps.numDeltaPocs((int)RefRpsIdx) & 0xFFFFFFFFL) + 1) &
            // 0xFFFFFFFFL)];

            int count = (int) sps.numDeltaPocs((int) refRpsIdx);
            for (int j = 0; j <= count; j++) {
                /*used_by_curr_pic_flag[j] =*/
                boolean isFlag = stream.readFlag(); // u(1)
                // if (!used_by_curr_pic_flag[j])
                if (!isFlag)
                /*use_delta_flag[j] =*/
                {
                    stream.readFlag(); // u(1)
                }
            }
        } else {
            numNegativePics = stream.readUev(); // ue(v)
            numPositivePics = stream.readUev(); // ue(v)
            deltaPocS0Minus1 = new /*UInt32*/ long[(int) (numNegativePics & 0xFFFFFFFFL)];
            usedByCurrPicS0Flag = new boolean[(int) (numNegativePics & 0xFFFFFFFFL)];
            deltaPocS1Minus1 = new /*UInt32*/ long[(int) (numPositivePics & 0xFFFFFFFFL)];
            usedByCurrPicS1Flag = new boolean[(int) (numPositivePics & 0xFFFFFFFFL)];

            for (int i = 0; i < (numNegativePics & 0xFFFFFFFFL); i++) {
                deltaPocS0Minus1[i] = stream.readUev(); // ue(v)
                usedByCurrPicS0Flag[i] = stream.readFlag(); // u(1)
            }

            for (int i = 0; i < (numPositivePics & 0xFFFFFFFFL); i++) {
                deltaPocS1Minus1[i] = stream.readUev(); // ue(v)
                usedByCurrPicS1Flag[i] = stream.readFlag(); // u(1)
            }
        }
    }

    public final /*UInt32*/ long getNumNegativePics() {
        return numNegativePics;
    }

    public final /*UInt32*/ long getNumPositivePics() {
        return numPositivePics;
    }

    public final /*UInt32*/ long getNumDeltaPocs() {
        return (((getNumNegativePics() & 0xFFFFFFFFL) + (getNumPositivePics() & 0xFFFFFFFFL)) & 0xFFFFFFFFL);
    }

    public final boolean[] getUsedByCurrPicS0() {
        return usedByCurrPicS0Flag;
    }

    public final boolean[] getUsedByCurrPicS1() {
        return usedByCurrPicS1Flag;
    }

    public final long deltaPocS0(int i) {
        return i == 0
                ? -(((deltaPocS0Minus1[i] & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL)
                : deltaPocS0(i - 1) - (((deltaPocS0Minus1[i] & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL);
    }

    public final long deltaPocS1(int i) {
        return i == 0
                ? -(((deltaPocS1Minus1[i] & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL)
                : deltaPocS1(i - 1) - (((deltaPocS1Minus1[i] & 0xFFFFFFFFL) + 1) & 0xFFFFFFFFL);
    }
}
