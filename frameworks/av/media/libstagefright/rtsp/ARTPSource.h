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

#ifndef A_RTP_SOURCE_H_

#define A_RTP_SOURCE_H_

#include <stdint.h>

#include <media/stagefright/foundation/ABase.h>
#include <utils/List.h>
#include <utils/RefBase.h>

namespace android {

struct ABuffer;
struct AMessage;
struct ARTPAssembler;
struct ASessionDescription;
#ifdef MTK_AOSP_ENHANCEMENT 
struct AString;
struct APacketSource;
struct AnotherPacketSource; //for bitrate adaptation
#endif // #ifdef MTK_AOSP_ENHANCEMENT

struct ARTPSource : public RefBase {
    ARTPSource(
            uint32_t id,
            const sp<ASessionDescription> &sessionDesc, size_t index,
            const sp<AMessage> &notify);

    void processRTPPacket(const sp<ABuffer> &buffer);
    void timeUpdate(uint32_t rtpTime, uint64_t ntpTime);
    void byeReceived();

    List<sp<ABuffer> > *queue() { return &mQueue; }

    void addReceiverReport(const sp<ABuffer> &buffer);
    void addFIR(const sp<ABuffer> &buffer);

private:
    uint32_t mID;
    uint32_t mHighestSeqNumber;
    int32_t mNumBuffersReceived;

    List<sp<ABuffer> > mQueue;
    sp<ARTPAssembler> mAssembler;

    uint64_t mLastNTPTime;
    int64_t mLastNTPTimeUpdateUs;

    bool mIssueFIRRequests;
    int64_t mLastFIRRequestUs;
    uint8_t mNextFIRSeqNo;

    sp<AMessage> mNotify;

    bool queuePacket(const sp<ABuffer> &buffer);

    DISALLOW_EVIL_CONSTRUCTORS(ARTPSource);
#ifdef MTK_AOSP_ENHANCEMENT 
public:
    void init();
    bool GetClockRate(const AString &desc, uint32_t *clockRate);
    void fakeSSRC(int ssrc, uint8_t* data);
    uint32_t extendSeqNumber(uint32_t seqNum, uint32_t mHighestSeqNumber);
    int32_t calculateArrivalJitter(const sp<ABuffer> &buffer);
    void setEstablishedStatus();
    uint8_t getFractionLost();
    uint8_t getCumulativeLost();
    void setHighestSeqNumber(uint32_t rtpSeq);
    void flushRTPPackets();
    void addSDES(const AString& cname, const sp<ABuffer> &buffer);
    void updateExpectedTimeoutUs(const int32_t& samples);
    void updateExpectedTimeoutUs(const int64_t& duration);
    int64_t getExpectedTimeoutUs() const { return mExpectedTimeoutUs; }
    static const int64_t kAccessUnitTimeoutUs = 3000000ll;
    static const size_t kVotePacketNumber = 10;
	//for stagefright
	void addNADUApp(sp<APacketSource> &pApacketSource,const sp<ABuffer> &buffer);
	//for nuplayer
	void addNADUApp(const sp<AnotherPacketSource> &pAnotherPacketSource,const sp<ABuffer> &buffer);

private:
    bool mEstablished;
    bool mHighestSeqNumberSet;
    uint32_t mClockRate;

	uint32_t mLastPacketRtpTime;
	int64_t mLastPacketRecvTimeUs; //in RTP timestamp units
	
	uint32_t mUIInterarrivalJitter;
	double mDInterarrivalJitter;
	
	uint32_t mNumLastRRPackRecv;
	uint32_t mLastRRPackRecvSeqNum;

	uint32_t mFirstPacketSeqNum;
    int64_t mExpectedTimeoutUs;
#endif // #ifdef MTK_AOSP_ENHANCEMENT
};

}  // namespace android

#endif  // A_RTP_SOURCE_H_
