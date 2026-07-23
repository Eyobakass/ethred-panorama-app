#ifndef NATIVE_STITCHER_H
#define NATIVE_STITCHER_H

#include <jni.h>
#include <string>
#include <vector>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jobject JNICALL
Java_com_ethred_panorama_stitching_NativeStitcher_nativeStitchFrames(
        JNIEnv *env,
        jobject thiz,
        jobjectArray framePaths,
        jfloatArray yaws,
        jfloatArray pitches,
        jfloatArray rolls,
        jstring outputPath,
        jint nadirCapOption
);

#ifdef __cplusplus
}
#endif

#endif // NATIVE_STITCHER_H
