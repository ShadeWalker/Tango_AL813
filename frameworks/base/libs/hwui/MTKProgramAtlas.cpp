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

/// M: used for program binary
#include <cutils/ashmem.h>
#include <sys/mman.h>

#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <binder/IServiceManager.h>
#include <utils/Log.h>
#include <fcntl.h>

#include "MTKProgramAtlas.h"
#include "IProgramBinaryService.h"

#include <unistd.h>

namespace android {
namespace uirenderer {

/// M: get the entry of each program binary
ProgramAtlas::ProgramEntry* ProgramAtlas::getProgramEntry(programid key) {
    ssize_t index = mProgramEntries.indexOfKey(key);
    return index >= 0 ? mProgramEntries.valueAt(index) : NULL;
}

void ProgramAtlas::init() {
    if (mProgramBinaries) {
        ALOGI("Already had program atlas...");
        return;
    }

    ALOGI("Initializing program atlas...");

    /// M: Get program binary service
    sp<android::IServiceManager> sm = defaultServiceManager();
    sp<android::IBinder> binder;
    sp<IProgramBinaryService> pbs;

    binder = sm->getService(String16(PROGRAM_BINARY_NAME));
    if (binder == 0) {
        ALOGW("Program binary service not published, failed to get program binary.");
        mProgramBinaries = NULL;
        return;
    }

    /// M: Get program binary content through binder
    pbs = interface_cast<IProgramBinaryService>(binder);
    int fd = pbs->getFileDescriptor();
    int programMapLen = pbs->getProgramMapLen();
    mProgramMap = pbs->getProgramMapArray();
    mProgramLength = pbs->getProgramBinaryLen();
    ALOGI("Program binary detail: Binary length is %d, program map length is %d.", mProgramLength, programMapLen);

    if (fd < 0 || programMapLen < 0 || mProgramMap == NULL || mProgramLength < 0) {
        ALOGW("Program binary service is not ready.");
        return;
    }

    // M: mmap the shared memory to this process.
    int64_t result = (int64_t)mmap(NULL, mProgramLength, PROT_READ, MAP_SHARED, fd, 0);
    if (result == (int64_t)MAP_FAILED) {
        ALOGW("Failed to mmap program binaries. File descriptor is %d, error code is %d, and status is %d", fd, errno, fcntl(fd, F_GETFL));
        return;
    } else {
        /// M: Show file descriptor and mapped path
        char s[256], name[256];
        snprintf(s, 255, "/proc/%d/fd/%d", getpid(), fd);
        readlink(s, name, 255);
        ALOGI("Succeeded to mmap program binaries. File descriptor is %d, and path is %s.", fd, name);

        mProgramBinaries = (void*) result;
    }

    if (mProgramBinaries && mProgramMap) {
        /// M: program binary entries
        for (int i = 0; i < programMapLen; ) {
            programid key = static_cast<programid>(mProgramMap[i++]);
            int binaryOffset = mProgramMap[i++];
            void* binary = reinterpret_cast<void*>(reinterpret_cast<int64_t>(mProgramBinaries) + binaryOffset);
            GLint length = static_cast<GLint>(mProgramMap[i++]);
            GLenum format = static_cast<GLenum>(mProgramMap[i++]);
            ProgramEntry* entry = new ProgramEntry(key, binary, length, format);
            mProgramEntries.add(entry->programKey, entry);

            char* ubinary = static_cast<char*>(binary);
            PROGRAM_LOGD("ProgramEntry #%2d: key 0x%.8x%.8x, offset %6d, binaryLength %4d, format %d --> 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x",
                i/4, uint32_t(key >> 32), uint32_t(key & 0xffffffff), binaryOffset, length, format,
                ubinary[0], ubinary[1], ubinary[2], ubinary[3], ubinary[4], ubinary[5], ubinary[6], ubinary[7], ubinary[8], ubinary[9]);
        }
        /// M: Close file descriptor.
        if (fd >=0) {
            ALOGI("No need to use file discriptor anymore, close fd(%d).", fd);
            close(fd);
        }
    } else {
        ALOGI("Failed to setup program atlas.");
        mProgramLength = 0;
        mProgramBinaries = NULL;
        mProgramMap = NULL;
    }
}

void ProgramAtlas::terminate() {
    if (mProgramBinaries) {
        /// M: unmap address if using program binaries is enabled
        int64_t result = munmap(mProgramBinaries, mProgramLength);
        if (result < 0) ALOGW("Failed to munmap program binaries.");

        delete mProgramMap;
        mProgramBinaries = NULL;
        mProgramLength = 0;
        mProgramMap = NULL;

        /// M: Clear program entries
        for (size_t i = 0; i < mProgramEntries.size(); i++) {
            delete mProgramEntries.valueAt(i);
        }
        mProgramEntries.clear();
    }
}

}; // namespace uirenderer
}; // namespace android

