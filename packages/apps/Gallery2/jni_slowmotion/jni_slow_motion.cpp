/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 * 
 * MediaTek Inc. (C) 2010. All rights reserved.
 * 
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */
#include <utils/Log.h>
#include <stdio.h>
#include <utils/String8.h>
#include <fcntl.h>
#include <linux/stat.h>
#include <utils/Errors.h>
#include "jni.h"
#include "VideoSpeedEffect.h"
#include "JNIHelp.h"

#define LOG_TAG "slowmotionjni"

using namespace android;

jobject mJobjectWeak; //weak reference to java object
jclass mJClass; //strong reference to java class env
JNIEnv *mEnv;
jmethodID post_event;
int srcfd;
int dstfd;
sp<VideoSpeedEffect> vse;

static JavaVM* jvm = 0;

static void postEventToJava(int msg, int ext1, int ext2);


/**
  *@ Description: Listener of VideoSpeedEffect,
  *@
  *@ Parameters:
  *@		msg: message type, such as events defined by speedeffect_event_type
  *@		ext1: message parameter 1
  *@ Return:
  *@ 	none
  */
class Listener : public VideoSpeedEffectListener
{
public:
  Listener(){}
  virtual ~Listener(){}
  virtual void notify(int msg, int ext1, int ext2){
    ALOGI("[notify]%d ", msg);
    if(msg == SPEEDEFFECT_COMPLETE
       ||msg == SPEEDEFFECT_UNSUPPORTED_VIDEO
       ||msg == SPEEDEFFECT_UNSUPPORTED_AUDIO
       ||msg >= SPEEDEFFECT_RECORD_EVENT_BEGIN){
      postEventToJava(msg,ext1,ext2);
    }
  }	
};

/**
  *@ Description: post VideoSpeedEffect message to Java.
  *@
  *@ Parameters:
  *@		msg: message type, such as events defined by speedeffect_event_type
  *@		ext1: message parameter 1
  *@ Return:
  *@ 	none
  */
static void postEventToJava(int msg, int ext1, int ext2) {
  ALOGI("[postEventToJava]%d , %d , %d", msg, ext1, ext2);
  JNIEnv *env;
  
  if(mJobjectWeak == NULL) {
    return;
  }
  // Attach main thread.
  if(jvm->AttachCurrentThread(&env, NULL) != JNI_OK) {
    ALOGI("[postEventToJava] Attach failed");
  }
  
  env->CallStaticVoidMethod(mJClass,post_event,mJobjectWeak,msg,ext1,ext2,NULL);
  
  if(jvm->DetachCurrentThread() != JNI_OK) {
    ALOGI("[postEventToJava] Detach failed");
  }
}

/**
  *@ Description: set speed effect parameters, such as slow motion interval, slow motion speed
  *@			     caller need call this interface before start speed effect handling
  *@ Parameters:
  *@		startPos: start position of speed effect (such as slow motion) interval
  *@		endPos:  end position of speed effect (such as slow motion) interval
  *@		params: the speed effect parameters,such as "slow-motion-speed = 4;video-framerate = 30;mute-autio = 0"
  *@ Return:
  *@		status_t type, OK indicate successful, otherwise error type will return
  */	

static jint setSpeedEffectParams(JNIEnv *env, jobject thiz, jlong startPos, jlong endPos, const jstring params) {
  if(vse != NULL) {
	String8 s8Param;
	if(params != NULL) {
	    const jchar* str = env->GetStringCritical(params,0);
	    s8Param = String8(str,env->GetStringLength(params));
	    env->ReleaseStringCritical(params,str);
	    ALOGI("[setSpeedEffectParams]%lld , %lld ", startPos, endPos);
	    ALOGI("[setSpeedEffectParams]%s", s8Param.string());
	} else {
	    jniThrowNullPointerException(env, "params must not be null.");
	}
	vse->addSpeedEffectParams(startPos,endPos,s8Param);
  	return OK; 
  } else {
	ALOGI("[setSpeedEffectParams] vse is null");
	return UNKNOWN_ERROR;
  }
}

/**
  *@ Description: start save the speed effect		
  *@ Parameters:
  *@ 	srcFd: File Description of the src file
  *@ 	dstFd: File Description of the dst file	  		 
  *@ Return:
  *@		status_t type, OK indicate successful, otherwise error type will return
  */
static jint startSaveSpeedEffect(JNIEnv *env, jobject thiz, jobject srcFD, jobject dstFD, jlong length) {
   if(vse !=NULL) {
	ALOGI("[startSaveSpeedEffect]");  
	if (srcFD == NULL || dstFD == NULL) {
	    jniThrowException(env, "java/lang/IllegalArgumentException", NULL);
	}
	int srcfd = jniGetFDFromFileDescriptor(env, srcFD);
	int dstfd = jniGetFDFromFileDescriptor(env, dstFD);
	ALOGI("[startSaveSpeedEffect]: srcfd %d", srcfd);
	ALOGI("[startSaveSpeedEffect]: dstfd %d", dstfd);
	int64_t srcLength = 0;	
	if (srcfd >= 0) {
	    srcLength = lseek64(srcfd, 0, SEEK_END);
	}
	ALOGI("[startSaveSpeedEffect1]: srcLength %lld", srcLength);
	sp<VideoSpeedEffectListener> listenr = new Listener();
	vse->setListener(listenr);
	vse->startSaveSpeedEffect(srcfd,0,srcLength,dstfd);
	return OK;
  } else {
	ALOGI("[startSaveSpeedEffect] vse is null");
	return UNKNOWN_ERROR;
  }
}

/**
  *@ Description: stop the speed effect opertion
  *@			     Caller can call this interface if user has cancelled the speed effect. 
  *@			     Part of the video will be transfered, caller can delete the output video if user cancel the operation
  *@ Parameters:
  *@ 	none		 
  *@ Return:
  *@		status_t type, OK indicate successful, otherwise error type will return
  */
static jint stopSaveSpeedEffect() {
  if(vse != NULL) {
       ALOGI("[stopSaveSpeedEffect]");
       vse->stopSaveSpeedEffect();
       vse = NULL;
       return OK;
  } else { 
       ALOGI("[stopSaveSpeedEffect] vse is null");
       return UNKNOWN_ERROR;
  }
}

/**
  *@ Description: Setup slowmotion jni runtime.
  *@
  *@ Parameters:
  *@		weak_this: SlowMotionTranscode Instance.
  *@ Return:
  *@ 	none
  */
static void setup(JNIEnv *env, jobject thiz, jobject weak_this) {
  ALOGI("[setup]");
  jclass clazz = env->GetObjectClass(thiz);
  if(clazz == NULL) {
    jniThrowRuntimeException(env,"can't find ... ");
  }
  mJobjectWeak = env->NewGlobalRef(weak_this);
  mJClass = (jclass)env->NewGlobalRef(clazz);
  
  vse = new VideoSpeedEffect();  
}

static const char *classPathName = "com/mediatek/gallery3d/video/SlowMotionTranscode";

static JNINativeMethod methods[] = {
  {"native_setSpeedEffectParams", "(JJLjava/lang/String;)I", (void*)setSpeedEffectParams},
  {"native_startSaveSpeedEffect",       "(Ljava/io/FileDescriptor;Ljava/io/FileDescriptor;J)I",(void *)startSaveSpeedEffect},
  {"native_stopSaveSpeedEffect", "()I", (void*)stopSaveSpeedEffect},
  {"native_setup", "(Ljava/lang/Object;)V", (void*)setup},
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
  jclass clazz = env->FindClass(classPathName);
  post_event = env->GetStaticMethodID(clazz,"postEventFromNative", 
  	   "(Ljava/lang/Object;IIILjava/lang/Object;)V");
  if(post_event == NULL) {
  	ALOGE("Can't find postEventFromNative");
	return -1;
  }
  	
  
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
    jvm = vm;

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
