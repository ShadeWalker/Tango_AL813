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

#ifndef A_RTP_ASSEMBLER_H_

#define A_RTP_ASSEMBLER_H_

#include <media/stagefright/foundation/ABase.h>
#include <utils/List.h>
#include <utils/RefBase.h>
#ifdef MTK_AOSP_ENHANCEMENT 
#include <utils/List.h>
#include <media/stagefright/foundation/ABuffer.h>
#endif // #ifdef MTK_AOSP_ENHANCEMENT

namespace android {

struct ABuffer;
struct ARTPSource;

struct ARTPAssembler : public RefBase {
    enum AssemblyStatus {
        MALFORMED_PACKET,
        WRONG_SEQUENCE_NUMBER,
#ifdef MTK_AOSP_ENHANCEMENT 
        LARGE_SEQUENCE_GAP,
#endif // #ifdef MTK_AOSP_ENHANCEMENT
        NOT_ENOUGH_DATA,
        OK
    };

    ARTPAssembler();

    void onPacketReceived(const sp<ARTPSource> &source);
    virtual void onByeReceived() = 0;
protected:
    virtual AssemblyStatus assembleMore(const sp<ARTPSource> &source) = 0;
    virtual void packetLost() = 0;

    static void CopyTimes(const sp<ABuffer> &to, const sp<ABuffer> &from);

    static sp<ABuffer> MakeADTSCompoundFromAACFrames(
            unsigned profile,
            unsigned samplingFreqIndex,
            unsigned channelConfig,
            const List<sp<ABuffer> > &frames);

    static sp<ABuffer> MakeCompoundFromPackets(
            const List<sp<ABuffer> > &frames);

private:
    int64_t mFirstFailureTimeUs;

    DISALLOW_EVIL_CONSTRUCTORS(ARTPAssembler);
#ifdef MTK_AOSP_ENHANCEMENT 
public:
    static const uint32_t kLargeSequenceGap = 20;
    bool mbFlush;

    virtual void setFlush(bool flush);
    // do something before time established
    virtual void updatePacketReceived(const sp<ARTPSource> &source, 
            const sp<ABuffer> &buffer);
    virtual void setNextExpectedSeqNo(uint32_t rtpSeq) {rtpSeq++ ;return; };
    
protected:
    AssemblyStatus getAssembleStatus(List<sp<ABuffer> > *queue, 
            uint32_t nextExpectedSeq) {
        sp<ABuffer> buffer = *--queue->end();
        uint32_t seq = buffer->int32Data();
        return seq - nextExpectedSeq > kLargeSequenceGap ?
            LARGE_SEQUENCE_GAP : WRONG_SEQUENCE_NUMBER;
    }
    // notify ARTPSource to updateExpectedTimeoutUs, mainly for audio
    virtual void evaluateDuration(const sp<ARTPSource> &source, 
            const sp<ABuffer> &buffer) { 
            	  if(source.get()== NULL ||  buffer.get()==NULL) 
            			return; 
            	}
#endif // #ifdef MTK_AOSP_ENHANCEMENT

    
};

}  // namespace android

#endif  // A_RTP_ASSEMBLER_H_
