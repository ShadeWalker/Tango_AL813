/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
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

#ifndef A_PACKET_SOURCE_H_

#define A_PACKET_SOURCE_H_

#include <media/stagefright/foundation/ABase.h>
#include <media/stagefright/MetaData.h>
#include <utils/RefBase.h>

#ifdef MTK_AOSP_ENHANCEMENT
#include <media/stagefright/MediaSource.h>
#include <utils/threads.h>
#include <utils/List.h>
#endif

namespace android {

struct ASessionDescription;

#ifdef MTK_AOSP_ENHANCEMENT
struct ABuffer;

struct APacketSource : public MediaSource {
#else
struct APacketSource : public RefBase {
#endif
    APacketSource(const sp<ASessionDescription> &sessionDesc, size_t index);

    status_t initCheck() const;

    virtual sp<MetaData> getFormat();

protected:
    virtual ~APacketSource();

private:
    status_t mInitCheck;

    sp<MetaData> mFormat;

    DISALLOW_EVIL_CONSTRUCTORS(APacketSource);
#ifdef MTK_AOSP_ENHANCEMENT
public:
    void init();
    void initAMRCheck(const char *params);
    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();
    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

    void queueAccessUnit(const sp<ABuffer> &buffer);
    void signalEOS(status_t result);

    void flushQueue();

    bool isAtEOS();

    size_t getBufQueSize(){return m_BufQueSize;} //get Whole Buffer queue size 
    size_t getTargetTime(){return m_TargetTime;}  //get target protected time of buffer queue duration for interrupt-free playback 
    bool getNSN(int32_t* uiNextSeqNum);
    size_t getFreeBufSpace();

    int64_t getQueueDurationUs(bool *eos);

private:
    Mutex mLock;
    Condition mCondition;
    List<sp<ABuffer> > mBuffers;
    status_t mEOSResult;
    // for avc nals
    bool mWantsNALFragments;
    List<sp<ABuffer> > mNALUnits;
    int64_t mAccessUnitTimeUs;

    size_t m_BufQueSize; //Whole Buffer queue size 
    size_t m_TargetTime;  // target protected time of buffer queue duration for interrupt-free playback 
    int32_t m_uiNextAduSeqNum;

    bool mIsAVC;
    bool mScanForIDR;
#endif
};

}  // namespace android

#endif  // A_PACKET_SOURCE_H_
