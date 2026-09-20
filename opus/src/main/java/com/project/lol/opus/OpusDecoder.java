package com.project.lol.opus;

import java.io.Closeable;
import java.nio.ByteBuffer;

public final class OpusDecoder implements Closeable {

    private long nativeHandle;

    public OpusDecoder(int sampleRate, int channels) {
        this(sampleRate, channels, 0);
    }

    public OpusDecoder(int sampleRate, int channels, int gainQ8) {
        long handle = nativeCreate(sampleRate, channels, gainQ8);
        if (handle == 0) {
            throw new IllegalStateException("Initialize Opus decoder failed");
        }
        nativeHandle = handle;
    }

    public int decode(ByteBuffer packet, int length, short[] pcm, int maxFrames) {
        if (nativeHandle == 0) {
            throw new IllegalStateException("Opus decoder was closed");
        }
        return nativeDecode(nativeHandle, packet, length, pcm, maxFrames);
    }

    @Override
    public void close() {
        if (nativeHandle == 0) {
            return;
        }
        nativeDestroy(nativeHandle);
        nativeHandle = 0;
    }

    public static String version() {
        return nativeVersion();
    }

    private static native long nativeCreate(int sampleRate, int channels, int gainQ8);

    private static native int nativeDecode(long handle, ByteBuffer packet, int length,
                                           short[] pcm, int maxFrames);

    private static native void nativeDestroy(long handle);

    private static native String nativeVersion();

    static {
        System.loadLibrary("opus-lib");
    }
}
