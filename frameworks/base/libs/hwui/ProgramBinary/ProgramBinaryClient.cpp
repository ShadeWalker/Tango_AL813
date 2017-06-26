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

#include <binder/Parcel.h>
#include <fcntl.h>
#include <utils/Log.h>

#include "IProgramBinaryService.h"

using namespace android;

IMPLEMENT_META_INTERFACE(ProgramBinaryService, PROGRAM_BINARY_NAME);

int BpProgramBinaryService::getFileDescriptor() {
    ALOGD("BpProgramBinaryService.getFileDescriptor");
    Parcel data;
    Parcel reply;
    data.writeInterfaceToken(IProgramBinaryService::getInterfaceDescriptor());
    remote()->transact(GET_FILE_DESCRIPTOR, data, &reply);
    reply.readExceptionCode();

    int fd = (int)reply.readFileDescriptor();
    if (fd >= 0) {
        int dupFd = dup(fd);
        char s[256], name[256];
        snprintf(s, 255, "/proc/%d/fd/%d", getpid(), dupFd);
        readlink(s, name, 255);
        DEBUG_LOGD("[Bp.getFileDescriptor] FD = %d, originalFd = %d, valid = %d, path = %s", dupFd, fd, fcntl(dupFd, F_GETFL) != -1,  name);
        return dupFd;
    } else {
        return fd;
    }
}

int BpProgramBinaryService::getProgramBinaryLen() {
    ALOGD("BpProgramBinaryService.getProgramBinaryLen");
    Parcel data;
    Parcel reply;
    data.writeInterfaceToken(IProgramBinaryService::getInterfaceDescriptor());
    remote()->transact(GET_PROGRAM_BIN_LENGTH, data, &reply);
    reply.readExceptionCode();
    int len = reply.readInt32();
    DEBUG_LOGD("[Bp.GetProgramBinaryLen] ProgramBinaryLen = %d", len);
    return len;
}

int BpProgramBinaryService::getProgramMapLen() {
    ALOGD("BpProgramBinaryService.getProgramMapLen");
    Parcel data;
    Parcel reply;
    data.writeInterfaceToken(IProgramBinaryService::getInterfaceDescriptor());
    remote()->transact(GET_PROGRAM_MAP_LENGTH, data, &reply);
    reply.readExceptionCode();
    int len = reply.readInt32();
    DEBUG_LOGD("[Bp.GetProgramMapLen] ProgramMapLen = %d", len);
    return len;
}

int64_t*  BpProgramBinaryService::getProgramMapArray() {
    ALOGD("BpProgramBinaryService.getProgramMapArray");
    Parcel data;
    Parcel reply;
    data.writeInterfaceToken(IProgramBinaryService::getInterfaceDescriptor());
    remote()->transact(GET_PROGRAM_MAP_ARRAY, data, &reply);
    reply.readExceptionCode();

    int len = reply.readInt32();
    if (len < 0) {
        return NULL;
    }

    int64_t* arr = new int64_t[len];
    for(int i=0; i<len; i++) {
        arr[i] = (int64_t)reply.readInt64();
    }

    for(int i=0; i<len; i += 4) {
        DEBUG_LOGD("[Bp.getProgramMapArray] ProgramEntry #%2d: key 0x%.8x%.8x, offset %d, binaryLength %d, format %d",
                i/4, uint32_t(arr[i] >> 32), uint32_t(arr[i] & 0xffffffff),
                (int)((long)arr[i+1]), static_cast<int>(arr[i+2]), static_cast<int>(arr[i+3]));
    }

    return arr;
}


