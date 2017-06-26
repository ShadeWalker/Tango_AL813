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

#ifndef _IPROGRAMBINARYSERVICE_H_
#define _IPROGRAMBINARYSERVICE_H_

#include <binder/IInterface.h>
#include <binder/Binder.h>

#include <fcntl.h>


#define DEBUG_PROGRAM_BINARY 1

static bool g_debug_program_binary = 0;

#if DEBUG_PROGRAM_BINARY
    #define DEBUG_LOGD(...) \
    {                               \
        if (g_debug_program_binary) \
            ALOGD(__VA_ARGS__);     \
    }
#else
    #define DEBUG_LOGD(...)
#endif


#define PROGRAM_BINARY_NAME "program_binary"
#define PROPERTY_DEBUG_PROGRAM_BINARY "debug.program_binary"

namespace android {
/*
 * This class defines the Binder IPC interface for accessing various
 * ProgramBinary features.
 */
enum PROGRAM_BINARY_ENUM{
        GET_FILE_DESCRIPTOR=IBinder::FIRST_CALL_TRANSACTION,
        GET_PROGRAM_BIN_LENGTH,
        GET_PROGRAM_MAP_LENGTH,
        GET_PROGRAM_MAP_ARRAY
};

class IProgramBinaryService: public IInterface {
public:
    DECLARE_META_INTERFACE(ProgramBinaryService);

    /**
     * Returns the file descriptor of the program binary or null if the program binary is
     * not available yet.
     */
    virtual int getFileDescriptor() = 0;

    /**
     * Returns the length of the program binary or 0 if the atlas is
     * not available yet.
     */
    virtual int getProgramBinaryLen() = 0;

    /**
     * Returns the length of the program binary map.
     */
    virtual int getProgramMapLen() = 0;

    /**
     * Returns the map of the program binary stored in the atlas or null
     * if the atlas is not available yet.
     *
     * Each program is represented by several entries in the array:
     * long0: key, indicate the program description
     * long1: offset, offset to the binary pool's head
     * long2: length, binary length
     * long3: format, binary format
     */
    virtual int64_t* getProgramMapArray() = 0;
};

class BnProgramBinaryService : public BnInterface<IProgramBinaryService> {
public:
    virtual status_t onTransact(uint32_t code, const Parcel& data,
            Parcel* reply, uint32_t flags = 0) = 0;

    int getFileDescriptor()
    {
        return 0;
    }

    int getProgramBinaryLen()
    {
        return 0;
    }

    int getProgramMapLen()
    {
        return 0;
    }

    int64_t* getProgramMapArray()
    {
        return 0;
    }
};

class BpProgramBinaryService : public BpInterface<IProgramBinaryService> {
public:
    BpProgramBinaryService(const sp<IBinder>& impl) : BpInterface<IProgramBinaryService>(impl) {
    }

    int getFileDescriptor();
    int getProgramBinaryLen();
    int getProgramMapLen();
    int64_t* getProgramMapArray();

private:
    //int mFd;
};


};

#endif
