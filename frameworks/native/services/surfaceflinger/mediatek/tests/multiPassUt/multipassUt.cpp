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

/*
 * Copyright (C) 2010 The Android Open Source Project
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
#define LOG_TAG "test-multipass"

#include "graphics_mtk_defs.h"
#include "gralloc_mtk_defs.h"

#include <cutils/memory.h>

#include <utils/Log.h>

#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <binder/IServiceManager.h>

#include <ui/GraphicBuffer.h>
#include <gui/Surface.h>
#include <gui/ISurfaceComposer.h>
#include <gui/SurfaceComposerClient.h>
#include <cutils/properties.h>

#include <ui/DisplayInfo.h>
#include <ui/GraphicBufferExtra.h>
#include <ui/gralloc_extra.h>

using namespace android;

int gValue[3] = {128, 192, 240};
inline void changeColor() {
    static int dr[3] = {1, -1, 1};
    int ch = (rand())%3;

    gValue[ch] &= 0xFF;
    gValue[ch] += dr[ch];
    if (gValue[ch] < 5 || gValue[ch] > 250) {
        dr[ch] = 0 - dr[ch];
        gValue[ch] += (dr[ch] * 2);
    }
}

status_t showTestUiFrame(ANativeWindow *w, bool isShow, int frameCnt, int delayMs, int fw, int fh, int type) {
    char value[PROPERTY_VALUE_MAX];

    int width = fw;
    int height = fh;
    int format = HAL_PIXEL_FORMAT_RGB_888;

    // set api connection type as register
    native_window_api_connect(w, 1);

    // set buffer size
    native_window_set_buffers_dimensions(w, width, height);

    // set format
    native_window_set_buffers_format(w, format);

    // set usage software write-able and hardware texture bind-able
    int usage = GRALLOC_USAGE_SW_WRITE_OFTEN | GRALLOC_USAGE_HW_TEXTURE;

    native_window_set_usage(w, usage);

    // set scaling to match window display size
    native_window_set_scaling_mode(w, NATIVE_WINDOW_SCALING_MODE_SCALE_TO_WINDOW);

    // set buffer count
    native_window_set_buffer_count(w, 10);

    changeColor();
    for (int i = 0; i < frameCnt; i++) {
        //printf("\n\t(rnd: %d) [show: %d] ", i, isShow);

        ANativeWindowBuffer *buf;
        sp<GraphicBuffer>   gb;
        const Rect  rect(width, height);

        int err;
        int fenceFd = -1;

        err = w->dequeueBuffer(w, &buf, &fenceFd);                     // dequeue to get buffer handle
        if (err != 0) {
            printf("dequeue error :%d\n", err);
            continue;
        }
        sp<Fence> fence1(new Fence(fenceFd));
        fence1->wait(Fence::TIMEOUT_NEVER);
        if (err) {
            ALOGE("%s", strerror(-err));
        }

        // set api type
        GraphicBufferExtra::get().setBufParameter(buf->handle, GRALLOC_EXTRA_MASK_TYPE | GRALLOC_EXTRA_MASK_DIRTY, GRALLOC_EXTRA_BIT_DIRTY);
        {
            uintptr_t ptr;
            gb = new GraphicBuffer(buf, false);
            gb->lock(GRALLOC_USAGE_SW_WRITE_OFTEN | GRALLOC_USAGE_HW_TEXTURE, rect, (void**)&ptr);
            // ----------------------------------------------------------------------------
            int size;
            int width;
            int stride;
            gralloc_extra_query(gb->handle, GRALLOC_EXTRA_GET_ALLOC_SIZE, &size);
            gralloc_extra_query(gb->handle, GRALLOC_EXTRA_GET_WIDTH, &width);
            gralloc_extra_query(gb->handle, GRALLOC_EXTRA_GET_STRIDE, &stride);
            // ----------------------------------------------------------------------------
            //printf("ptr: %p // ", (void*)ptr);

            if (ptr != 0) {
                if (type == -1) {
                    changeColor();
                    for (int j = 0; j < height; j++) {
                        if (j % 10 == 0) changeColor();
                        for (int k = 0; k < stride; k++) {
                            for (int m = 0; m < 3; m++) {
                                *(unsigned char *)(ptr++) = gValue[m];
                            }
                        }
                    }
                } else {
                    memset((void*)ptr, (unsigned char)type, size);
                }
            }
            gb->unlock();
        }

        err = w->queueBuffer(w, buf, -1);                                    // queue to display
        sp<Fence> fence2(new Fence(fenceFd));
        fence2->wait(Fence::TIMEOUT_NEVER);
        if(err) {
            ALOGE("%s", strerror(-err));
        }

        property_get("debug.sftest.sleep", value, "1000");
        int delay = atoi(value);
        usleep(delay * delayMs);
    }
    // disconnect as unregister
    native_window_api_disconnect(w, 1);

    return NO_ERROR;
}

status_t main(int argc, char** argv) {

    int bgCnt = 0;
    int fgCnt = 1;
    int frameCnt = 20;
    if (argc > 1) {
        int tmp;
        sscanf(argv[1], "%d", &tmp);
        if (tmp > 0 && tmp < 5)
            bgCnt = tmp;
    }
    if (argc > 2) {
        int tmp;
        sscanf(argv[2], "%d", &tmp);
        if (tmp > 0 && tmp < 10)
            fgCnt = tmp;
    }
    if (argc > 3) {
        int tmp;
        sscanf(argv[3], "%d", &tmp);
        if (tmp > 0 && tmp < 30)
            frameCnt = tmp;
    }

    printf("\n bgCnt:%d    fgCnt:%d    frameCnt:%d", bgCnt, fgCnt, frameCnt);
    // ------------------------------------------------------------------ set up the thread-pool --
    sp<ProcessState> proc(ProcessState::self());
    ProcessState::self()->startThreadPool();

    // create a client to surfaceflinger
    sp<SurfaceComposerClient> client = new SurfaceComposerClient();
    DisplayInfo dinfo;
    sp<IBinder> display = SurfaceComposerClient::getBuiltInDisplay(
            ISurfaceComposer::eDisplayIdMain);
    SurfaceComposerClient::getDisplayInfo(display, &dinfo);
    uint32_t dispw = dinfo.w;
    uint32_t disph = dinfo.h;

    ALOGD("display (w,h):(%d,%d)", dispw, disph);

    int gap = (disph - 200) / 40;
    //-------------------------------------------------------------------------------- SurfaceBG --
    String8 surfaceNameBG("test-multipassUt BG");
    sp<SurfaceControl> surfaceControl[20];
    sp<Surface> surface[20];
    int baseLayerCnt = bgCnt;
    for (int i = 0; i < baseLayerCnt; i++) {
        int shift = i * gap;
        int width = dispw;
        int height =disph - shift * 2;
        surfaceControl[i] = client->createSurface(surfaceNameBG, width, height, PIXEL_FORMAT_RGBA_8888);
        surface[i] = surfaceControl[i]->getSurface();
        SurfaceComposerClient::openGlobalTransaction();
        surfaceControl[i]->setPosition(0, shift);
        surfaceControl[i]->setLayer(1000000 + i);
        SurfaceComposerClient::closeGlobalTransaction();
        ANativeWindow* window = surface[i].get();
        showTestUiFrame(window, true, 1, 32 , width, height, 255 - i * (256 / baseLayerCnt));
    }
    //-------------------------------------------------------------------------------- SurfaceFG --
    String8 surfaceNameFG("test-multipassUt FG");
    sp<SurfaceControl> surfaceControlFG[20];
    sp<Surface> surfaceFG[20];
    int fgLayerCnt = fgCnt;
    int width[20];
    int height[20];
    ANativeWindow* window[20];
    for (int i = 0; i < fgLayerCnt; i++) {
        int shift = (disph / 4) + i * gap;
        width[i] = dispw;
        height[i] = disph - shift * 2;
        surfaceControlFG[i] = client->createSurface(surfaceNameFG, width[i], height[i], PIXEL_FORMAT_RGBA_8888);
        surfaceFG[i] = surfaceControlFG[i]->getSurface();
        window[i] = surfaceFG[i].get();
        SurfaceComposerClient::openGlobalTransaction();
        surfaceControlFG[i]->setPosition(0, shift);
        surfaceControlFG[i]->setLayer(1000030 + i);
        SurfaceComposerClient::closeGlobalTransaction();
    }
    //-------------------------------------------------------------------------------- show FG --
    int rnd = 100000;
    for(int j = 0; j < rnd; j++) {
        for (int i = 0; i < fgLayerCnt; i++) {
            SurfaceComposerClient::openGlobalTransaction();
            surfaceControlFG[i]->show();
            SurfaceComposerClient::closeGlobalTransaction();
            showTestUiFrame(window[i], true, frameCnt, 16 , width[i], height[i], -1);
        }

        SurfaceComposerClient::openGlobalTransaction();
        for (int i = 0; i < fgLayerCnt; i++)
            surfaceControlFG[i]->hide();
        SurfaceComposerClient::closeGlobalTransaction();

        usleep(frameCnt * 16 * 1000);
    }
    //-------------------------------------------------------------------------------------------
    printf("test complete. CTRL+C to finish.\n");
    IPCThreadState::self()->joinThreadPool();
    return NO_ERROR;
}
