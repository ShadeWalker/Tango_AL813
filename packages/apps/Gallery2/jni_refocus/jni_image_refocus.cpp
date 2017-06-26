/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "Gallery2_Refocus_jni_image_refocus"
#include <utils/Log.h>
#include <stdio.h>
#include "jni.h"
#include "image_refocus.h"

using namespace android;

static ImageRefocus *mImageRefocus;

static void imageRefocus(JNIEnv *env, jobject thiz, jint jpsWidth, jint jpsHeight, jint maskWidth, jint maskHeight, jint posX, jint posY, 
        jint viewWidth, jint viewHeight, jint orientation, jint mainCamPos, jint touchCoordX1st, jint touchCoordY1st) {
    ALOGI("imageRefocus");
    mImageRefocus = new ImageRefocus(jpsWidth, jpsHeight, maskWidth, maskHeight, posX, posY, 
            viewWidth, viewHeight, orientation, mainCamPos, touchCoordX1st, touchCoordY1st);
}

static jboolean initRefocusNoDepthMapTest(JNIEnv *env, jobject thiz, jstring targetFilePath, jstring depthmapSourcePath, jstring maskFilePath, jint outImgWidth, jint outImgHeight, jint imgOrientation, 
                jint inStereoImgWidth, jint inStereoImgHeight) {
    ALOGI("initRefocusNoDepthMap");
    const char *cTargetFilePath = env->GetStringUTFChars(targetFilePath,false);
    const char *cDepthmapSourcePath = env->GetStringUTFChars(depthmapSourcePath,false);
    const char *cMaskFilePath = env->GetStringUTFChars(maskFilePath,false);
    jboolean initResult;
    initResult = mImageRefocus->initRefocusNoDepthMapTest(cTargetFilePath, outImgWidth, outImgHeight, imgOrientation, cDepthmapSourcePath, inStereoImgWidth, inStereoImgHeight, cMaskFilePath);
    env->ReleaseStringUTFChars(targetFilePath,cTargetFilePath);
    env->ReleaseStringUTFChars(depthmapSourcePath,cDepthmapSourcePath);
    env->ReleaseStringUTFChars(maskFilePath,cMaskFilePath);
    return initResult;
}

static jboolean initRefocusNoDepthMapRealFileTest(JNIEnv *env, jobject thiz, jstring testSourceFilePath, jstring targetFilePath, 
        jstring jpsFilePath, jint outImgWidth, jint outImgHeight, jint imgOrientation, 
        jbyteArray jpsBuffer, jint jpsBufferSize, jint inStereoImgWidth, jint inStereoImgHeight, 
        jbyteArray maskBuffer, jint maskBufferSize, jint maskWidth, jint maskHeight) {
    ALOGI("initRefocusNoDepthMap");
    const char *cTargetFilePath = env->GetStringUTFChars(targetFilePath,false);
    const char *cTestSourceFilePath = env->GetStringUTFChars(testSourceFilePath,false);
    const char *cJpsFilePath = env->GetStringUTFChars(jpsFilePath,false);
    unsigned char* cJpsBuffer = (unsigned char*)env->GetByteArrayElements(jpsBuffer,0);
    unsigned char* cMaskBuffer = (unsigned char*)env->GetByteArrayElements(maskBuffer,0);
    jboolean initResult;
    initResult = mImageRefocus->initRefocusNoDepthMapRealFileTest(cTestSourceFilePath, cTargetFilePath, cJpsFilePath, outImgWidth, outImgHeight, imgOrientation, 
            cJpsBuffer, jpsBufferSize, inStereoImgWidth, inStereoImgHeight, 
            cMaskBuffer, maskBufferSize, maskWidth, maskHeight);
    env->ReleaseStringUTFChars(testSourceFilePath,cTestSourceFilePath);
    env->ReleaseStringUTFChars(targetFilePath,cTargetFilePath);
    env->ReleaseStringUTFChars(jpsFilePath,cJpsFilePath);
    env->ReleaseByteArrayElements(jpsBuffer, (jbyte *)cJpsBuffer, 0);
    env->ReleaseByteArrayElements(maskBuffer, (jbyte *)cMaskBuffer, 0);
    return initResult;
}

static jboolean initRefocusNoDepthMap(JNIEnv *env, jobject thiz, jstring targetFilePath, jint outImgWidth, jint outImgHeight, jint imgOrientation, 
        jbyteArray jpsBuffer, jint jpsBufferSize, jint inStereoImgWidth, jint inStereoImgHeight, 
        jbyteArray maskBuffer, jint maskBufferSize, jint maskWidth, jint maskHeight) {
    ALOGI("initRefocusNoDepthMap");
    const char *cTargetFilePath = env->GetStringUTFChars(targetFilePath,false);
    unsigned char* cJpsBuffer = (unsigned char*)env->GetByteArrayElements(jpsBuffer,0);
    unsigned char* cMaskBuffer = (unsigned char*)env->GetByteArrayElements(maskBuffer,0);
    jboolean initResult;
    initResult = mImageRefocus->initRefocusNoDepthMap(cTargetFilePath, outImgWidth, outImgHeight, imgOrientation, 
            cJpsBuffer, jpsBufferSize, inStereoImgWidth, inStereoImgHeight, 
            cMaskBuffer, maskBufferSize, maskWidth, maskHeight);
    env->ReleaseStringUTFChars(targetFilePath,cTargetFilePath);
    env->ReleaseByteArrayElements(jpsBuffer, (jbyte *)cJpsBuffer, 0);
    env->ReleaseByteArrayElements(maskBuffer, (jbyte *)cMaskBuffer, 0);
    return initResult;
}

static jboolean initRefocusWithDepthMap(JNIEnv *env, jobject thiz, jstring targetFilePath, jint outImgWidth, jint outImgHeight, jint imgOrientation, 
                jbyteArray depthMapBuffer, jint depthMapBufferSize, jint inStereoImgWidth, jint inStereoImgHeight) {
    ALOGI("initRefocusWithDepthMap");
    const char *cTargetFilePath = env->GetStringUTFChars(targetFilePath,false);
    unsigned char* cDepthMapBuffer = (unsigned char*)env->GetByteArrayElements(depthMapBuffer,0);
    jboolean initResult;
    initResult = mImageRefocus->initRefocusWithDepthMap(cTargetFilePath, outImgWidth, outImgHeight, imgOrientation, cDepthMapBuffer, depthMapBufferSize, inStereoImgWidth, inStereoImgHeight);
    env->ReleaseStringUTFChars(targetFilePath,cTargetFilePath);
    env->ReleaseByteArrayElements(depthMapBuffer, (jbyte *)cDepthMapBuffer, 0);
    return initResult;
}

static jboolean generateRefocusImage(JNIEnv *env, jobject thiz, jbyteArray resultBuffer, jint touchX, jint touchY, jint depthOfField){
    ALOGI("generateRefocusImage %d, %d, %d) ", touchX, touchY, depthOfField);
    unsigned char* tempData = (unsigned char*)env->GetByteArrayElements(resultBuffer,0);
    jboolean generateResult;
    generateResult = mImageRefocus->generateRefocusImage((unsigned char*)tempData, touchX, touchY, depthOfField);
    env->ReleaseByteArrayElements(resultBuffer, (jbyte *)tempData, 0);
    return generateResult;
}

static jint getDepthBufferSize(JNIEnv *env, jobject thiz){
    ALOGI("getDepthBufferSize");
    return mImageRefocus->getDepthBufferSize();
}

static jint getDepthBufferWidth(JNIEnv *env, jobject thiz){
    ALOGI("getDepthBufferWidth");
    return mImageRefocus->getDepthBufferWidth();
}

static jint getDepthBufferHeight(JNIEnv *env, jobject thiz){
    ALOGI("getDepthBufferHeight");
    return mImageRefocus->getDepthBufferHeight();
}

static jint getXMPDepthBufferSize(JNIEnv *env, jobject thiz){
    ALOGI("getDepthBufferSize");
    return mImageRefocus->getXMPDepthBufferSize();
}

static jint getXMPDepthBufferWidth(JNIEnv *env, jobject thiz){
    ALOGI("getDepthBufferWidth");
    return mImageRefocus->getXMPDepthBufferWidth();
}

static jint getXMPDepthBufferHeight(JNIEnv *env, jobject thiz){
    ALOGI("getDepthBufferHeight");
    return mImageRefocus->getXMPDepthBufferHeight();
}

static void saveDepthMapInfo(JNIEnv *env, jobject thiz, jbyteArray depthBuffer, jbyteArray xmpDepthBuffer){
    ALOGI("saveDepthMapInfo");
    unsigned char* depthData = (unsigned char*)env->GetByteArrayElements(depthBuffer,0);
    unsigned char* xmpDepthData = (unsigned char*)env->GetByteArrayElements(xmpDepthBuffer,0);
    mImageRefocus->saveDepthMapInfo((unsigned char*)depthData, (unsigned char*)xmpDepthData);
    env->ReleaseByteArrayElements(depthBuffer, (jbyte *)depthData, 0);
    env->ReleaseByteArrayElements(xmpDepthBuffer, (jbyte *)xmpDepthData, 0);
}

static void saveRefocusImage(JNIEnv *env, jobject thiz, jstring saveFileName, jint inSampleSize){
    ALOGI("saveRefocusImage %d", inSampleSize);
    const char *cSaveFileName = env->GetStringUTFChars(saveFileName,false);
    mImageRefocus->saveRefocusImage(cSaveFileName, inSampleSize);
    env->ReleaseStringUTFChars(saveFileName,cSaveFileName);
}

static void release(JNIEnv *env, jobject thiz){
    ALOGI("release()");
    mImageRefocus->deinit();
}

static const char *classPathName = "com/mediatek/galleryfeature/refocus/RefocusImageJNI";

static JNINativeMethod methods[] = {
  {"imageRefocus", "(IIIIIIIIIIII)V", (void*)imageRefocus },
  {"initRefocusNoDepthMapTest", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IIIII)Z", (void*)initRefocusNoDepthMapTest },
  {"initRefocusNoDepthMapRealFileTest", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;III[BIII[BIII)Z", (void*)initRefocusNoDepthMapRealFileTest },
  {"initRefocusNoDepthMap", "(Ljava/lang/String;III[BIII[BIII)Z", (void*)initRefocusNoDepthMap},
  {"initRefocusWithDepthMap", "(Ljava/lang/String;III[BIII)Z", (void*)initRefocusWithDepthMap },
  {"generateRefocusImage", "([BIII)Z", (void*)generateRefocusImage },
  {"getDepthBufferSize", "()I", (void*)getDepthBufferSize },
  {"getDepthBufferWidth", "()I", (void*)getDepthBufferWidth },
  {"getDepthBufferHeight", "()I", (void*)getDepthBufferHeight },
  {"getXMPDepthBufferSize", "()I", (void*)getXMPDepthBufferSize },
  {"getXMPDepthBufferWidth", "()I", (void*)getXMPDepthBufferWidth },
  {"getXMPDepthBufferHeight", "()I", (void*)getXMPDepthBufferHeight },
  {"saveDepthMapInfo", "([B[B)V", (void*)saveDepthMapInfo },
  {"saveRefocusImage", "(Ljava/lang/String;I)V", (void*)saveRefocusImage },
  {"release", "()V", (void*)release },
};

/*
 * Register several native methods for one class.
 */
static int registerNativeMethods(JNIEnv* env, const char* className,
    JNINativeMethod* gMethods, int numMethods)
{
    jclass clazz;

    clazz = env->FindClass(className);
    if (clazz == NULL) {
        ALOGE("Native registration unable to find class '%s'", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        ALOGE("RegisterNatives failed for '%s'", className);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/*
 * Register native methods for all classes we know about.
 *
 * returns JNI_TRUE on success.
 */
static int registerNatives(JNIEnv* env)
{
  if (!registerNativeMethods(env, classPathName,
                 methods, sizeof(methods) / sizeof(methods[0]))) {
    return JNI_FALSE;
  }

  return JNI_TRUE;
}


// ----------------------------------------------------------------------------

/*
 * This is called by the VM when the shared library is first loaded.
 */

typedef union {
    JNIEnv* env;
    void* venv;
} UnionJNIEnvToVoid;

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    UnionJNIEnvToVoid uenv;
    uenv.venv = NULL;
    jint result = -1;
    JNIEnv* env = NULL;

    ALOGI("JNI_OnLoad");

    if (vm->GetEnv(&uenv.venv, JNI_VERSION_1_4) != JNI_OK) {
        ALOGE("ERROR: GetEnv failed");
        goto bail;
    }
    env = uenv.env;

    if (registerNatives(env) != JNI_TRUE) {
        ALOGE("ERROR: registerNatives failed");
        goto bail;
    }

    result = JNI_VERSION_1_4;

bail:
    return result;
}
