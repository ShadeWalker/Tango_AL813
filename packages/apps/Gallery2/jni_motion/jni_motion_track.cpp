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

#define LOG_TAG "jni_motion_track"
#include <utils/Log.h>

#include <stdio.h>

#include "jni.h"
#include "motion_track.h"
using namespace android;

static MotionTrack *mMotionTrack;

static void init(JNIEnv *env, jobject thiz, jstring workPath, jstring prefixName,
        jint inImgWidth, jint inImgHeight, jint inImgNum, jint outImgWidth, jint outImgHeight) {
    const char *cworkPath = env->GetStringUTFChars(workPath,false);
    const char *cprefixName = env->GetStringUTFChars(prefixName,false);
    ALOGI("init(%s, %s, %d, %d, %d, %d, %d)", cworkPath, cprefixName, inImgWidth, inImgHeight,
            inImgNum, outImgWidth, outImgHeight);
    mMotionTrack = new MotionTrack(cworkPath, cprefixName, inImgWidth, inImgHeight,
                             inImgNum, outImgWidth, outImgHeight);
    env->ReleaseStringUTFChars(workPath,cworkPath);
    env->ReleaseStringUTFChars(prefixName,cprefixName);
}

static jintArray getDisableArray(JNIEnv *env, jobject thiz, int query_img_id){
    int ref[MAX_IMAGE_NUM] = {0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1};
    MBOOL *u8ref;
    jintArray jref = env->NewIntArray(MAX_IMAGE_NUM);

    u8ref = mMotionTrack->getRefImage(query_img_id);
    ALOGI("getDisableArray(%d)", query_img_id);
    for(int i=0; i<MAX_IMAGE_NUM; i++)
    {
        ref[i] = u8ref[i];
        //ALOGI("ref[%d]=%d", i, ref[i]);
    }
    env->SetIntArrayRegion(jref, 0, MAX_IMAGE_NUM, ref);
    return jref;
}

static jintArray getPrevFocusArray(JNIEnv *env, jobject thiz){
    int ref[MAX_IMAGE_NUM] = {0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1};
    MBOOL *u8ref;
    jintArray jref = env->NewIntArray(MAX_IMAGE_NUM);

    u8ref = mMotionTrack->getPrevSelect();
    for(int i=0; i<MAX_IMAGE_NUM; i++)
    {
        ref[i] = u8ref[i];
        //ALOGI("prevSelect[%d]=%d", i, ref[i]);
    }
    env->SetIntArrayRegion(jref, 0, MAX_IMAGE_NUM, ref);
    return jref;
}
static jintArray getPrevDisableArray(JNIEnv *env, jobject thiz){
    int ref[MAX_IMAGE_NUM] = {0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1};
    MBOOL *u8ref;
    jintArray jref = env->NewIntArray(MAX_IMAGE_NUM);

    u8ref = mMotionTrack->getPrevRefImage();
    for(int i=0; i<MAX_IMAGE_NUM; i++)
    {
        ref[i] = u8ref[i];
    }
    env->SetIntArrayRegion(jref, 0, MAX_IMAGE_NUM, ref);
    return jref;
}

static void setManualIndexes(JNIEnv *env, jobject thiz, jintArray candImg, jint num){
    ALOGI("setIndexs(%d)", num);
    MUINT8 u8ref[MAX_IMAGE_SELECT_NUM];
    int *ccandImg = env->GetIntArrayElements(candImg, 0);

    for(int i=0; i<num; i++){
        ALOGI("candImg[%d]=%d", i, ccandImg[i]);
        u8ref[i] = ccandImg[i];
    }
    mMotionTrack->setManualIndexes(u8ref, num);
    env->ReleaseIntArrayElements(candImg, ccandImg,0);
}

static void addSelect(JNIEnv *env, jobject thiz, jint index){
    ALOGI("addSelect(%d)", index);
}

static void removeSelect(JNIEnv *env, jobject thiz, jint index){
    ALOGI("removeSelect(%d)", index);
}

static void doBlend(JNIEnv *env, jobject thiz){
    mMotionTrack->doBlending();
    ALOGI("doBlend()");
}

static void release(JNIEnv *env, jobject thiz){
    ALOGI("release()");
    mMotionTrack->reset();
    mMotionTrack->release();
};

static void test(JNIEnv *env, jobject thiz){
    ALOGI("test()");
    //mMotionTrack->test();
}

static const char *classPathName = "com/mediatek/galleryfeature/container/MotionTrack";

static JNINativeMethod methods[] = {
  {"init", "(Ljava/lang/String;Ljava/lang/String;IIIII)V", (void*)init },
  {"getDisableArray", "(I)[I", (void*)getDisableArray },
  {"getPrevFocusArray", "()[I", (void*)getPrevFocusArray },
  {"getPrevDisableArray", "()[I", (void*)getPrevDisableArray},
  {"setManualIndexes", "([II)V", (void*)setManualIndexes },
  {"doBlend", "()V", (void*)doBlend },
  {"release", "()V", (void*)release },
//  {"addSelect", "(I)V", (void*)addSelect },
//  {"removeSelect", "(I)V", (void*)removeSelect },
//  {"test", "()V", (void*)test},

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
