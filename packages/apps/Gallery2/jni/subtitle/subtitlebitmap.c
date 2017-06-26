/*
 * Copyright (C) 2012 The Android Open Source Project
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

#include <math.h>
#include <sys/mman.h>
#include <errno.h>
#include <fcntl.h>
#include "subtitlebitmap.h"

#define PIXEL_SIZE (4)
#define  ALOGE(...)  __android_log_print(ANDROID_LOG_ERROR,"subtitleBitmap",__VA_ARGS__)
 void Java_com_mediatek_gallery3d_video_SubTitleView_nativeGetBm(JNIEnv* env, jobject obj, jobject bitmap_in, jint width, jint height, jobject bitmap_out)
 {
    char* source = 0;
    char* destination = 0;
    AndroidBitmap_lockPixels(env, bitmap_in, (void**) &source);
    AndroidBitmap_lockPixels(env, bitmap_out, (void**) &destination);
    unsigned char * rgb_in = (unsigned char * )source;
    unsigned char * rgb_out = (unsigned char * )destination;

    memcpy(rgb_out, rgb_in, width*height*4);
    AndroidBitmap_unlockPixels(env, bitmap_in);
    AndroidBitmap_unlockPixels(env, bitmap_out);
 }
 
  void Java_com_mediatek_gallery3d_video_SubTitleView_nativeGetBmFromAddr(JNIEnv* env, jobject obj, jint fd, jint width, jint height, jobject bitmap_out)
 { 
    char* source = 0;
    char* destination = 0;
    ALOGE("jni_nativeGetBmFromAddr audioAndSubtitle source : %#x,w=%d,h =%d", fd,width,height);
    void *mBitmapData;
    char filePath[256];
    sprintf(filePath,"/sdcard/IdxSubBitmapBuffer_%d.tmp",fd);
    int fd01 = open(filePath, O_RDWR);
    if (-1 == fd01)
    {
    	ALOGE("jni_nativeGetBmFromAddr, audioAndSubtitle open fd01 failed, errno=%d", errno);
    	close(fd01);
    	return;
    }
    ALOGE("jni_nativeGetBmFromAddr, audioAndSubtitle open fd01 succeeded, fd01=0x%x", fd01);
    
    mBitmapData = mmap(0, width*height*PIXEL_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED , fd01, 0);
    
     if (MAP_FAILED == mBitmapData)
    {
        ALOGE("jni_nativeGetBmFromAddr, audioAndSubtitle mmap failed, errno=%d:%s", errno, strerror(errno)); 
        close(fd01);
        return ;
    }
    ALOGE("jni_nativeGetBmFromAddr, audioAndSubtitle mmap fd01 succeeded, fd01=0x%x", fd01);
    source = mBitmapData;
    ALOGE("jni_nativeGetBmFromAddr audioAndSubtitle source02 : %#x", source);
   
    AndroidBitmap_lockPixels(env, bitmap_out, (void**) &destination);
    unsigned char * rgb_in = (unsigned char * )source;
    unsigned char * rgb_out = (unsigned char * )destination;
	ALOGE("cxd audioAndSubtitle rgb_in : %#x", rgb_in);
	ALOGE("cxd audioAndSubtitle rgb_out : %#x", rgb_out);
    memcpy(rgb_out, rgb_in, width*height*PIXEL_SIZE);
    close(fd01);

    AndroidBitmap_unlockPixels(env, bitmap_out);
 }
