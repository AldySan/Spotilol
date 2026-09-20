#include <jni.h>
#include <opus.h>

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_project_lol_opus_OpusDecoder_nativeCreate(JNIEnv *env, jclass type, jint sampleRate,
                                                   jint channels, jint gainQ8) {
    int error = OPUS_OK;
    OpusDecoder *decoder = opus_decoder_create(sampleRate, channels, &error);
    if (decoder == nullptr || error != OPUS_OK) {
        if (decoder != nullptr) {
            opus_decoder_destroy(decoder);
        }
        return 0L;
    }
    if (gainQ8 != 0) {
        opus_decoder_ctl(decoder, OPUS_SET_GAIN(gainQ8));
    }
    return reinterpret_cast<jlong>(decoder);
}

JNIEXPORT jint JNICALL
Java_com_project_lol_opus_OpusDecoder_nativeDecode(JNIEnv *env, jclass type, jlong handle,
                                                   jobject packet_, jint length,
                                                   jshortArray pcm_, jint maxFrames) {
    OpusDecoder *decoder = reinterpret_cast<OpusDecoder *>(handle);
    void *packet = env->GetDirectBufferAddress(packet_);
    if (packet == nullptr) {
        return OPUS_BAD_ARG;
    }
    jshort *pcm = env->GetShortArrayElements(pcm_, NULL);
    int result = opus_decode(decoder, reinterpret_cast<const unsigned char *>(packet), length, pcm,
                             maxFrames, 0);
    env->ReleaseShortArrayElements(pcm_, pcm, 0);
    return result;
}

JNIEXPORT void JNICALL
Java_com_project_lol_opus_OpusDecoder_nativeDestroy(JNIEnv *env, jclass type, jlong handle) {
    OpusDecoder *decoder = reinterpret_cast<OpusDecoder *>(handle);
    if (decoder != nullptr) {
        opus_decoder_destroy(decoder);
    }
}

JNIEXPORT jstring JNICALL
Java_com_project_lol_opus_OpusDecoder_nativeVersion(JNIEnv *env, jclass type) {
    return env->NewStringUTF(opus_get_version_string());
}

}
