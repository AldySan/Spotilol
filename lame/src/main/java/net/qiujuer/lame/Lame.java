package net.qiujuer.lame;

import java.io.Closeable;

public class Lame implements Closeable {

    public interface LameModel {
        int STEREO = 0;
        int JOINT_STEREO = 1;
        int MONO = 3;
        int AUTO = 5;
    }

    public interface LameQuality {
        int BEST = 0;
        int NEAR_BEST = 2;
        int GOOD = 5;
        int OK = 7;
        int WORST = 9;
    }

    private long mNativeLame;
    private int mInChannels;

    public Lame(int inSampleRate, int inChannels, int outSampleRate) {
        this(inSampleRate, inChannels, outSampleRate, 32, LameModel.AUTO, LameQuality.OK);
    }

    public Lame(int inSampleRate, int inChannels, int outSampleRate, int outBitrate, int quality) {
        this(inSampleRate, inChannels, outSampleRate, outBitrate, LameModel.AUTO, quality);
    }

    public Lame(int inSampleRate, int inChannels, int outSampleRate, int outBitrate, int model, int quality) {
        if (outSampleRate > inSampleRate) {
            throw new IllegalArgumentException("outSampleRate can't be greater than inSampleRate");
        }
        if (outBitrate > 320 || outBitrate < 8) {
            throw new IllegalArgumentException("outBitrate should be between 8 and 320");
        }
        if (inChannels > 2 || inChannels < 1) {
            throw new IllegalArgumentException("inChannels must be 1 or 2");
        }
        if (model > 5 || model < 0) {
            throw new IllegalArgumentException("model should be between 0 and 5");
        }
        if (quality > 9 || quality < 0) {
            throw new IllegalArgumentException("quality should be between 0 and 9");
        }

        long ptr = nInit(inSampleRate, inChannels, outSampleRate, outBitrate, model, quality);
        if (ptr == 0) {
            throw new IllegalStateException("Initialize Lame failed");
        }

        mInChannels = inChannels;
        mNativeLame = ptr;
    }

    public int getInChannels() {
        return mInChannels;
    }

    public int getMp3bufferSize() {
        checkLame();
        return mGetMp3bufferSize(mNativeLame);
    }

    public int getMp3bufferSize(int samples) {
        checkLame();
        return mGetMp3bufferSizeWithSamples(mNativeLame, samples);
    }

    @Override
    public void close() {
        if (mNativeLame == 0) {
            return;
        }
        nClose(mNativeLame);
        mNativeLame = 0;
    }

    public int encodeInterleaved(short[] bufLR, int samples, byte[] outMp3buf) {
        checkLame();
        return nEncodeShortInterleaved(mNativeLame, bufLR, samples, outMp3buf);
    }

    public int encode(short[] bufL, short[] bufR, int samples, byte[] outMp3buf) {
        checkLame();
        return nEncodeShort(mNativeLame, bufL, bufR, samples, outMp3buf);
    }

    public int flush(byte[] outMp3buf) {
        checkLame();
        return nFlush(mNativeLame, outMp3buf);
    }

    private void checkLame() {
        if (mNativeLame == 0) {
            throw new IllegalStateException("Lame was closed");
        }
    }

    private static native long nInit(int inSampleRate, int inChannels, int outSampleRate, int outBitrate, int model, int quality);

    private static native int mGetMp3bufferSize(long lamePtr);

    private static native int mGetMp3bufferSizeWithSamples(long lamePtr, int samples);

    private static native int nEncodeShortInterleaved(long lamePtr, short[] bufLR, int samples, byte[] outMp3buf);

    private static native int nEncodeShort(long lamePtr, short[] bufL, short[] bufR, int samples, byte[] outMp3buf);

    private static native int nFlush(long lamePtr, byte[] outBuf);

    private static native void nClose(long lamePtr);

    static {
        System.loadLibrary("mp3lame-lib");
    }
}
