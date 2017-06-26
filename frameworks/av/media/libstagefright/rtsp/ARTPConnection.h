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

#ifndef A_RTP_CONNECTION_H_

#define A_RTP_CONNECTION_H_

#include <media/stagefright/foundation/AHandler.h>
#include <utils/List.h>

namespace android {

struct ABuffer;
struct ARTPSource;
struct ASessionDescription;

#ifdef MTK_AOSP_ENHANCEMENT
struct APacketSource;
struct AnotherPacketSource;

struct ARTPConnectionParam {
    int32_t mSSRC;
    sp<APacketSource> mAPacketSource;
    size_t mNaduFreq;
};
#endif

struct ARTPConnection : public AHandler {
    enum Flags {
#ifdef MTK_AOSP_ENHANCEMENT
        kFakeTimestamps      = 1,
#endif // #ifdef MTK_AOSP_ENHANCEMENT
        kRegularlyRequestFIR = 2,
    };

    ARTPConnection(uint32_t flags = 0);

    void addStream(
            int rtpSocket, int rtcpSocket,
            const sp<ASessionDescription> &sessionDesc, size_t index,
            const sp<AMessage> &notify,
#ifdef MTK_AOSP_ENHANCEMENT 
            bool injected, ARTPConnectionParam* pbitRateAdapParam);
#else
            bool injected);
#endif // #ifdef MTK_AOSP_ENHANCEMENT

    void removeStream(int rtpSocket, int rtcpSocket);

    void injectPacket(int index, const sp<ABuffer> &buffer);

    // Creates a pair of UDP datagram sockets bound to adjacent ports
    // (the rtpSocket is bound to an even port, the rtcpSocket to the
    // next higher port).
    static void MakePortPair(
#ifdef MTK_AOSP_ENHANCEMENT 
            int *rtpSocket, int *rtcpSocket, unsigned *rtpPort,
            int min = 0, int max = 65535);
#else
            int *rtpSocket, int *rtcpSocket, unsigned *rtpPort);
#endif // #ifdef MTK_AOSP_ENHANCEMENT

protected:
    virtual ~ARTPConnection();
    virtual void onMessageReceived(const sp<AMessage> &msg);

private:
    enum {
        kWhatAddStream,
        kWhatRemoveStream,
        kWhatPollStreams,
        kWhatInjectPacket,
#ifdef MTK_AOSP_ENHANCEMENT 
        kWhatInjectPollStreams,
        kWhatStartCheckAlives,
        kWhatStopCheckAlives,
        kWhatCheckAlive,
        kWhatSeqUpdate,
#endif // #ifdef MTK_AOSP_ENHANCEMENT
    };

    static const int64_t kSelectTimeoutUs;

    uint32_t mFlags;

    struct StreamInfo;
    List<StreamInfo> mStreams;

    bool mPollEventPending;
    int64_t mLastReceiverReportTimeUs;

    void onAddStream(const sp<AMessage> &msg);
    void onRemoveStream(const sp<AMessage> &msg);
    void onPollStreams();
    void onInjectPacket(const sp<AMessage> &msg);
    void onSendReceiverReports();

    status_t receive(StreamInfo *info, bool receiveRTP);

    status_t parseRTP(StreamInfo *info, const sp<ABuffer> &buffer);
    status_t parseRTCP(StreamInfo *info, const sp<ABuffer> &buffer);
    status_t parseSR(StreamInfo *info, const uint8_t *data, size_t size);
    status_t parseBYE(StreamInfo *info, const uint8_t *data, size_t size);

    sp<ARTPSource> findSource(StreamInfo *info, uint32_t id);

    void postPollEvent();

    DISALLOW_EVIL_CONSTRUCTORS(ARTPConnection);
#ifdef MTK_AOSP_ENHANCEMENT 
public:
    void startCheckAlives();
    void stopCheckAlives();
    void setHighestSeqNumber(int socket, uint32_t rtpSeq);
	void setAnotherPacketSource(int iMyHandlerTrackIndex, sp<AnotherPacketSource> pAnotherPacketSource);
	typedef KeyedVector<int, sp<AnotherPacketSource> > tAnotherPacketSourceMap;
	tAnotherPacketSourceMap mAnotherPacketSourceMap;
private:
    void setConnParam(ARTPConnectionParam* connParam, sp<AMessage> &msg);
    void setStreamInfo(const sp<AMessage> &msg, StreamInfo *info);
    void onRecvNewSsrc(StreamInfo *info, uint32_t srcId, sp<ARTPSource> source);
    int  getReadSize(StreamInfo *s, bool receiveRTP);
    void postRecvReport(StreamInfo *s, sp<ABuffer> &buffer);
    void addNADUApp(sp<ARTPSource> source, StreamInfo *s, sp<ABuffer> buffer);
    bool needSendNADU(StreamInfo *s);

    void sendRR();
    void onStartCheckAlives();
    void onStopCheckAlives();
    void onCheckAlive(const sp<AMessage> &msg);
    void onSetHighestSeqNumber(const sp<AMessage> &msg);
    void postInjectEvent();
    void onPostInjectEvent();

#endif // #ifdef MTK_AOSP_ENHANCEMENT
};

}  // namespace android

#endif  // A_RTP_CONNECTION_H_
