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

//#define LOG_NDEBUG 0
#define LOG_TAG "NuPlayerRenderer"
#include <utils/Log.h>

#include "NuPlayerRenderer.h"

#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/foundation/AUtils.h>
#include <media/stagefright/foundation/AWakeLock.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>

#include <VideoFrameScheduler.h>

#include <inttypes.h>
#ifdef MTK_AOSP_ENHANCEMENT
#define DUMP_PROFILE 0
#define MAX_VIDEO_EARLY_POST_US		50000ll
#include <media/AudioTrackCenter.h>
#define USE_AUDIO_TRACK_CENTER  (1)
#define ATRACE_TAG ATRACE_TAG_VIDEO
#include <utils/Trace.h>
#include <cutils/properties.h>
#endif

#include <media/MtkMMLog.h>
namespace android {

// Maximum time in paused state when offloading audio decompression. When elapsed, the AudioSink
// is closed to allow the audio DSP to power down.
static const int64_t kOffloadPauseMaxUs = 10000000ll;



#ifdef MTK_AOSP_ENHANCEMENT
static bool sFrameAVsyncByVFS = true;

static void readProperties() {
    char sync[PROPERTY_VALUE_MAX];
    if (property_get("persist.sys.media.vfs", sync, NULL)) {
        sFrameAVsyncByVFS =
            !strcmp("1", sync) || !strcasecmp("true", sync);
    }

    ALOGI("sFrameAVsyncByVFS %d",sFrameAVsyncByVFS);

}

#endif


// static
const NuPlayer::Renderer::PcmInfo NuPlayer::Renderer::AUDIO_PCMINFO_INITIALIZER = {
        AUDIO_CHANNEL_NONE,
        AUDIO_OUTPUT_FLAG_NONE,
        AUDIO_FORMAT_INVALID,
        0, // mNumChannels
        0 // mSampleRate
};

// static
const int64_t NuPlayer::Renderer::kMinPositionUpdateDelayUs = 100000ll;

NuPlayer::Renderer::Renderer(
        const sp<MediaPlayerBase::AudioSink> &sink,
        const sp<AMessage> &notify,
        uint32_t flags)
    : mAudioSink(sink),
      mNotify(notify),
      mFlags(flags),
      mNumFramesWritten(0),
      mDrainAudioQueuePending(false),
      mDrainVideoQueuePending(false),
      mAudioQueueGeneration(0),
      mVideoQueueGeneration(0),
      mAudioFirstAnchorTimeMediaUs(-1),
      mAnchorTimeMediaUs(-1),
      mAnchorTimeRealUs(-1),
      mAnchorNumFramesWritten(-1),
      mAnchorMaxMediaUs(-1),
      mVideoLateByUs(0ll),
      mHasAudio(false),
      mHasVideo(false),
      mPauseStartedTimeRealUs(-1),
      mFlushingAudio(false),
      mFlushingVideo(false),
      mNotifyCompleteAudio(false),
      mNotifyCompleteVideo(false),
      mSyncQueues(false),
      mPaused(false),
      mPausePositionMediaTimeUs(-1),
      mVideoSampleReceived(false),
      mVideoRenderingStarted(false),
      mVideoRenderingStartGeneration(0),
      mAudioRenderingStartGeneration(0),
      mAudioOffloadPauseTimeoutGeneration(0),
      mAudioOffloadTornDown(false),
      mCurrentOffloadInfo(AUDIO_INFO_INITIALIZER),
      mCurrentPcmInfo(AUDIO_PCMINFO_INITIALIZER),
      mTotalBuffersQueued(0),
      mLastAudioBufferDrained(0),
      mWakeLock(new AWakeLock()) {

#ifdef MTK_AOSP_ENHANCEMENT
#ifdef MTK_CLEARMOTION_SUPPORT
      mPausing = false;
      mMJCPauseDelay = false;
#endif
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT
	  mSMSpeed= 1;
	  mPreSMSpeed = 1;
	  mPauseSpeed = 1; 	  
	  mNeedSync = false;
	  mSMSynctime = -1;
#endif
		mUseSyncQueues = true;
    mLateVideoToDisplay = true;
    mUseFlushAudioSyncQueues = false;
    mAudioEOS = false;
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_AUDIO_CHANGE_SUPPORT) 
    mVideoEOS= false;
    mNoJudgeWhenChangeAudio = false;//for ALPS01940787
#endif
    mAudioFlushed = false;
    readProperties();
    mBufferingStartTimeRealUs = -1;
#endif
}

NuPlayer::Renderer::~Renderer() {
    if (offloadingAudio()) {
        mAudioSink->stop();
        mAudioSink->flush();
        mAudioSink->close();
    }
}

void NuPlayer::Renderer::queueBuffer(
        bool audio,
        const sp<ABuffer> &buffer,
        const sp<AMessage> &notifyConsumed) {
    sp<AMessage> msg = new AMessage(kWhatQueueBuffer, id());
    msg->setInt32("audio", static_cast<int32_t>(audio));
    msg->setBuffer("buffer", buffer);
    msg->setMessage("notifyConsumed", notifyConsumed);
    msg->post();
}

void NuPlayer::Renderer::queueEOS(bool audio, status_t finalResult) {
    MM_LOGI("queueEOS audio:%d", audio);
    CHECK_NE(finalResult, (status_t)OK);

    sp<AMessage> msg = new AMessage(kWhatQueueEOS, id());
    msg->setInt32("audio", static_cast<int32_t>(audio));
    msg->setInt32("finalResult", finalResult);
    msg->post();
}

void NuPlayer::Renderer::flush(bool audio, bool notifyComplete) {
    {
        Mutex::Autolock autoLock(mFlushLock);
        if (audio) {
            mNotifyCompleteAudio |= notifyComplete;
            if (mFlushingAudio) {
                return;
            }
            mFlushingAudio = true;
#ifdef MTK_AOSP_ENHANCEMENT            
            mAudioFlushed = true;    
#endif
        } else {
            mNotifyCompleteVideo |= notifyComplete;
            if (mFlushingVideo) {
                return;
            }
            mFlushingVideo = true;
        }
    }

    sp<AMessage> msg = new AMessage(kWhatFlush, id());
    msg->setInt32("audio", static_cast<int32_t>(audio));
    msg->post();
}

void NuPlayer::Renderer::signalTimeDiscontinuity() {
    Mutex::Autolock autoLock(mLock);
    // CHECK(mAudioQueue.empty());
    // CHECK(mVideoQueue.empty());
#ifdef MTK_AOSP_ENHANCEMENT
    if(mAudioFlushed){
    	setAudioFirstAnchorTime(-1);
    	mAudioFlushed = false;
    }
#endif
    setAnchorTime(-1, -1);
#ifdef MTK_AOSP_ENHANCEMENT
    setPauseStartedTimeRealUs(-1);
#endif
    setVideoLateByUs(0);
    
#ifdef MTK_AOSP_ENHANCEMENT
    if (!mAudioQueue.empty()) {
        ALOGE("------signalTimeDiscontinuity (audio size=%d)----", (int)mAudioQueue.size());
        //dumpQueue(&mAudioQueue, true);
    }
    if (!mVideoQueue.empty()) {
        ALOGE("------signalTimeDiscontinuity (video size=%d)----", (int)mVideoQueue.size());
        //dumpQueue(&mVideoQueue, false);
    }
#endif
      mSyncQueues = false;
}

void NuPlayer::Renderer::signalAudioSinkChanged() {
    (new AMessage(kWhatAudioSinkChanged, id()))->post();
}

void NuPlayer::Renderer::signalDisableOffloadAudio() {
    (new AMessage(kWhatDisableOffloadAudio, id()))->post();
}

void NuPlayer::Renderer::signalEnableOffloadAudio() {
    (new AMessage(kWhatEnableOffloadAudio, id()))->post();
}

void NuPlayer::Renderer::pause() {
    (new AMessage(kWhatPause, id()))->post();
}

void NuPlayer::Renderer::resume() {
    (new AMessage(kWhatResume, id()))->post();
}

void NuPlayer::Renderer::setVideoFrameRate(float fps) {
    sp<AMessage> msg = new AMessage(kWhatSetVideoFrameRate, id());
    msg->setFloat("frame-rate", fps);
    msg->post();
}

// Called on any threads, except renderer's thread.
status_t NuPlayer::Renderer::getCurrentPosition(int64_t *mediaUs) {
    {
        Mutex::Autolock autoLock(mLock);
        int64_t currentPositionUs;
        if (getCurrentPositionIfPaused_l(&currentPositionUs)) {
            *mediaUs = currentPositionUs;
            return OK;
        }
    }
    return getCurrentPositionFromAnchor(mediaUs, ALooper::GetNowUs());
}

// Called on only renderer's thread.
status_t NuPlayer::Renderer::getCurrentPositionOnLooper(int64_t *mediaUs) {
    return getCurrentPositionOnLooper(mediaUs, ALooper::GetNowUs());
}

// Called on only renderer's thread.
// Since mPaused and mPausePositionMediaTimeUs are changed only on renderer's
// thread, no need to acquire mLock.
status_t NuPlayer::Renderer::getCurrentPositionOnLooper(
        int64_t *mediaUs, int64_t nowUs, bool allowPastQueuedVideo) {
    int64_t currentPositionUs;
    if (getCurrentPositionIfPaused_l(&currentPositionUs)) {
        *mediaUs = currentPositionUs;
        return OK;
    }
    return getCurrentPositionFromAnchor(mediaUs, nowUs, allowPastQueuedVideo);
}

// Called either with mLock acquired or on renderer's thread.
bool NuPlayer::Renderer::getCurrentPositionIfPaused_l(int64_t *mediaUs) {
    if (!mPaused || mPausePositionMediaTimeUs < 0ll) {
        return false;
    }
    *mediaUs = mPausePositionMediaTimeUs;
    return true;
}

// Called on any threads.
status_t NuPlayer::Renderer::getCurrentPositionFromAnchor(
        int64_t *mediaUs, int64_t nowUs, bool allowPastQueuedVideo) {
    Mutex::Autolock autoLock(mTimeLock);
    if (!mHasAudio && !mHasVideo) {
        return NO_INIT;
    }

    if (mAnchorTimeMediaUs < 0) {
        return NO_INIT;
    }

    int64_t positionUs = (nowUs - mAnchorTimeRealUs) + mAnchorTimeMediaUs;
#ifdef MTK_AOSP_ENHANCEMENT
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT
	if(mNeedSync){
		if(mPreSMSpeed >1){
			int64_t audiolatency = 1000LL * mAudioSink->latency();
			positionUs = (nowUs - mAnchorTimeRealUs + audiolatency/2)/mPreSMSpeed + mAnchorTimeMediaUs;
		}
	}else{
		if(mSMSpeed >1){
			int64_t audiolatency = 1000LL * mAudioSink->latency();
			positionUs = (nowUs - mAnchorTimeRealUs + audiolatency/2)/mSMSpeed + mAnchorTimeMediaUs ;
		}
	}
#endif	
#endif

    if (mPauseStartedTimeRealUs != -1) {
        positionUs -= (nowUs - mPauseStartedTimeRealUs);
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT		
		if((mSMSpeed >1)){
			int64_t audiolatency = 1000LL * mAudioSink->latency();
			positionUs = (mPauseStartedTimeRealUs  - mAnchorTimeRealUs+ audiolatency/2)/mSMSpeed+ mAnchorTimeMediaUs;
		}
#endif		
    }

    // limit position to the last queued media time (for video only stream
    // position will be discrete as we don't know how long each frame lasts)
    if (mAnchorMaxMediaUs >= 0 && !allowPastQueuedVideo) {
        if (positionUs > mAnchorMaxMediaUs) {
            positionUs = mAnchorMaxMediaUs;
        }
    }

    if (positionUs < mAudioFirstAnchorTimeMediaUs) {
        positionUs = mAudioFirstAnchorTimeMediaUs;
    }

    MM_LOGV("positionUs:%lld, mAnchorTimeRealUs:%lld, mAnchorTimeMediaUs:%lld, \
mPauseStartedTimeRealUs:%lld, mAnchorMaxMediaUs:%lld, mAudioFirstAnchorTimeMediaUs:%lld", \
            positionUs, mAnchorTimeRealUs, mAnchorTimeMediaUs, mPauseStartedTimeRealUs, mAnchorMaxMediaUs, \
            mAudioFirstAnchorTimeMediaUs);
    *mediaUs = (positionUs <= 0) ? 0 : positionUs;
    return OK;
}

void NuPlayer::Renderer::setHasMedia(bool audio) {
    Mutex::Autolock autoLock(mTimeLock);
    if (audio) {
        mHasAudio = true;
#ifdef MTK_AOSP_ENHANCEMENT
       mAudioEOS = false;
#endif
    } else {
        mHasVideo = true;
    }
}

void NuPlayer::Renderer::setAudioFirstAnchorTime(int64_t mediaUs) {
    Mutex::Autolock autoLock(mTimeLock);
    mAudioFirstAnchorTimeMediaUs = mediaUs;
}

void NuPlayer::Renderer::setAudioFirstAnchorTimeIfNeeded(int64_t mediaUs) {
    Mutex::Autolock autoLock(mTimeLock);
    if (mAudioFirstAnchorTimeMediaUs == -1) {
        mAudioFirstAnchorTimeMediaUs = mediaUs;
    }
}

void NuPlayer::Renderer::setAnchorTime(
        int64_t mediaUs, int64_t realUs, int64_t numFramesWritten, bool resume) {
    Mutex::Autolock autoLock(mTimeLock);
    mAnchorTimeMediaUs = mediaUs;
    mAnchorTimeRealUs = realUs;
    mAnchorNumFramesWritten = numFramesWritten;
    if (resume) {
        mPauseStartedTimeRealUs = -1;
    }
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT	
	else{
		mSMSpeed    = mPauseSpeed;
		mPreSMSpeed = mSMSpeed;
	}
	mNeedSync = false;
#endif	
}

void NuPlayer::Renderer::setVideoLateByUs(int64_t lateUs) {
    Mutex::Autolock autoLock(mTimeLock);
    mVideoLateByUs = lateUs;
}

int64_t NuPlayer::Renderer::getVideoLateByUs() {
    Mutex::Autolock autoLock(mTimeLock);
    return mVideoLateByUs;
}

void NuPlayer::Renderer::setPauseStartedTimeRealUs(int64_t realUs) {
    Mutex::Autolock autoLock(mTimeLock);
    mPauseStartedTimeRealUs = realUs;
}

status_t NuPlayer::Renderer::openAudioSink(
        const sp<AMessage> &format,
        bool offloadOnly,
        bool hasVideo,
        uint32_t flags,
        bool *isOffloaded) {
    sp<AMessage> msg = new AMessage(kWhatOpenAudioSink, id());
    msg->setMessage("format", format);
    msg->setInt32("offload-only", offloadOnly);
    msg->setInt32("has-video", hasVideo);
    msg->setInt32("flags", flags);

    sp<AMessage> response;
    msg->postAndAwaitResponse(&response);

    int32_t err;
    if (!response->findInt32("err", &err)) {
        err = INVALID_OPERATION;
    } else if (err == OK && isOffloaded != NULL) {
        int32_t offload;
        CHECK(response->findInt32("offload", &offload));
        *isOffloaded = (offload != 0);
    }
    return err;
}

void NuPlayer::Renderer::closeAudioSink() {
    sp<AMessage> msg = new AMessage(kWhatCloseAudioSink, id());

    sp<AMessage> response;
    msg->postAndAwaitResponse(&response);
}

void NuPlayer::Renderer::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
        case kWhatOpenAudioSink:
        {
            sp<AMessage> format;
            CHECK(msg->findMessage("format", &format));

            int32_t offloadOnly;
            CHECK(msg->findInt32("offload-only", &offloadOnly));

            int32_t hasVideo;
            CHECK(msg->findInt32("has-video", &hasVideo));

            uint32_t flags;
            CHECK(msg->findInt32("flags", (int32_t *)&flags));

            status_t err = onOpenAudioSink(format, offloadOnly, hasVideo, flags);

            sp<AMessage> response = new AMessage;
            response->setInt32("err", err);
            response->setInt32("offload", offloadingAudio());

            uint32_t replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));
            response->postReply(replyID);

            break;
        }

        case kWhatCloseAudioSink:
        {
            uint32_t replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            onCloseAudioSink();

            sp<AMessage> response = new AMessage;
            response->postReply(replyID);
            break;
        }

        case kWhatStopAudioSink:
        {
            mAudioSink->stop();
            break;
        }

        case kWhatDrainAudioQueue:
        {
            int32_t generation;
            CHECK(msg->findInt32("generation", &generation));
            if (generation != mAudioQueueGeneration) {
                break;
            }

            mDrainAudioQueuePending = false;

            if (onDrainAudioQueue()) {
                uint32_t numFramesPlayed;
                CHECK_EQ(mAudioSink->getPosition(&numFramesPlayed),
                         (status_t)OK);

                uint32_t numFramesPendingPlayout =
                    mNumFramesWritten - numFramesPlayed;

                // This is how long the audio sink will have data to
                // play back.
                int64_t delayUs =
                    mAudioSink->msecsPerFrame()
                        * numFramesPendingPlayout * 1000ll;

#ifdef MTK_AOSP_ENHANCEMENT
      			// half that is to large, set it to 20ms
      			if (numFramesPlayed > mNumFramesWritten) 
		           ALOGW("numFramesPlayed(%d) > mNumFramesWritten(%d), no reset @ kWhatDrainAudioQueue", 
                            numFramesPlayed, mNumFramesWritten);
      			if (delayUs > 40000)
      				 delayUs = 40000;
#endif
                // Let's give it more data after about half that time
                // has elapsed.
                // kWhatDrainAudioQueue is used for non-offloading mode,
                // and mLock is used only for offloading mode. Therefore,
                // no need to acquire mLock here.
                postDrainAudioQueue_l(delayUs / 2);
            }
            break;
        }

        case kWhatDrainVideoQueue:
        {
            int32_t generation;
            CHECK(msg->findInt32("generation", &generation));
            if (generation != mVideoQueueGeneration) {
                break;
            }

            mDrainVideoQueuePending = false;

            onDrainVideoQueue();

            Mutex::Autolock autoLock(mLock);
            postDrainVideoQueue_l();
            break;
        }

        case kWhatPostDrainVideoQueue:
        {
            int32_t generation;
            CHECK(msg->findInt32("generation", &generation));
            if (generation != mVideoQueueGeneration) {
                break;
            }

            mDrainVideoQueuePending = false;
            Mutex::Autolock autoLock(mLock);
            postDrainVideoQueue_l();
            break;
        }

        case kWhatQueueBuffer:
        {
            onQueueBuffer(msg);
            break;
        }

        case kWhatQueueEOS:
        {
            onQueueEOS(msg);
            break;
        }

        case kWhatFlush:
        {
            onFlush(msg);
            break;
        }

        case kWhatAudioSinkChanged:
        {
            onAudioSinkChanged();
            break;
        }

        case kWhatDisableOffloadAudio:
        {
            onDisableOffloadAudio();
            break;
        }

        case kWhatEnableOffloadAudio:
        {
            onEnableOffloadAudio();
            break;
        }

        case kWhatPause:
        {
#if  defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_CLEARMOTION_SUPPORT)  
            int32_t forcePause = 0;
            if(msg->findInt32("force-pause", &forcePause) && forcePause == 1) {
                if(!mPausing)
                    break;
                ALOGI("delay time arrived, try to force pause");
                mPausing = false;
                mMJCPauseDelay = false;

            }

            if (mPaused || mPausing) {
                ALOGW("NuPlayer::Renderer::onPause already paused or in pausing(%d) state", mPausing);
                break;
            }

            if( mMJCPauseDelay && !mPausing) {
                mPausing = true;
                ALOGI("need pause delay for MJC");
                msg->setInt32("force-pause", 1);
                msg->post(300000);
                break;
            }
#endif
            onPause();
            break;
        }

        case kWhatResume:
        {
            onResume();
            break;
        }

        case kWhatSetVideoFrameRate:
        {
            float fps;
            CHECK(msg->findFloat("frame-rate", &fps));
            onSetVideoFrameRate(fps);
            break;
        }

        case kWhatAudioOffloadTearDown:
        {
            onAudioOffloadTearDown(kDueToError);
            break;
        }

        case kWhatAudioOffloadPauseTimeout:
        {
            int32_t generation;
            CHECK(msg->findInt32("generation", &generation));
            if (generation != mAudioOffloadPauseTimeoutGeneration) {
                break;
            }
            ALOGV("Audio Offload tear down due to pause timeout.");
            onAudioOffloadTearDown(kDueToTimeout);
            mWakeLock->release();
            break;
        }

        default:
            TRESPASS();
            break;
    }
}

void NuPlayer::Renderer::postDrainAudioQueue_l(int64_t delayUs) {
#ifdef MTK_AOSP_ENHANCEMENT
    if (mDrainAudioQueuePending || isSyncQueues() || mPaused
            || offloadingAudio()) {
        return;
    }
#else
    if (mDrainAudioQueuePending || mSyncQueues || mPaused
            || offloadingAudio()) {
        return;
    }
#endif

    if (mAudioQueue.empty()) {
        return;
    }

    mDrainAudioQueuePending = true;
    sp<AMessage> msg = new AMessage(kWhatDrainAudioQueue, id());
    msg->setInt32("generation", mAudioQueueGeneration);
    msg->post(delayUs);
}

void NuPlayer::Renderer::prepareForMediaRenderingStart() {
    mAudioRenderingStartGeneration = mAudioQueueGeneration;
    mVideoRenderingStartGeneration = mVideoQueueGeneration;
}

void NuPlayer::Renderer::notifyIfMediaRenderingStarted() {
    if (mVideoRenderingStartGeneration == mVideoQueueGeneration &&
        mAudioRenderingStartGeneration == mAudioQueueGeneration) {
        mVideoRenderingStartGeneration = -1;
        mAudioRenderingStartGeneration = -1;

        sp<AMessage> notify = mNotify->dup();
        notify->setInt32("what", kWhatMediaRenderingStart);
        notify->post();
    }
}

// static
size_t NuPlayer::Renderer::AudioSinkCallback(
        MediaPlayerBase::AudioSink * /* audioSink */,
        void *buffer,
        size_t size,
        void *cookie,
        MediaPlayerBase::AudioSink::cb_event_t event) {
    NuPlayer::Renderer *me = (NuPlayer::Renderer *)cookie;

    switch (event) {
        case MediaPlayerBase::AudioSink::CB_EVENT_FILL_BUFFER:
        {
            return me->fillAudioBuffer(buffer, size);
            break;
        }

        case MediaPlayerBase::AudioSink::CB_EVENT_STREAM_END:
        {
            me->notifyEOS(true /* audio */, ERROR_END_OF_STREAM);
            break;
        }

        case MediaPlayerBase::AudioSink::CB_EVENT_TEAR_DOWN:
        {
            me->notifyAudioOffloadTearDown();
            break;
        }
    }

    return 0;
}

size_t NuPlayer::Renderer::fillAudioBuffer(void *buffer, size_t size) {
    Mutex::Autolock autoLock(mLock);

    if (!offloadingAudio() || mPaused) {
        return 0;
    }

    bool hasEOS = false;

    size_t sizeCopied = 0;
    bool firstEntry = true;
    while (sizeCopied < size && !mAudioQueue.empty()) {
        QueueEntry *entry = &*mAudioQueue.begin();

        if (entry->mBuffer == NULL) { // EOS
            hasEOS = true;
            mAudioQueue.erase(mAudioQueue.begin());
            entry = NULL;
            break;
        }

        if (firstEntry && entry->mOffset == 0) {
            firstEntry = false;
            int64_t mediaTimeUs;
            CHECK(entry->mBuffer->meta()->findInt64("timeUs", &mediaTimeUs));
            ALOGV("rendering audio at media time %.2f secs", mediaTimeUs / 1E6);
            setAudioFirstAnchorTimeIfNeeded(mediaTimeUs);
        }

        size_t copy = entry->mBuffer->size() - entry->mOffset;
        size_t sizeRemaining = size - sizeCopied;
        if (copy > sizeRemaining) {
            copy = sizeRemaining;
        }

        memcpy((char *)buffer + sizeCopied,
               entry->mBuffer->data() + entry->mOffset,
               copy);

        entry->mOffset += copy;
        if (entry->mOffset == entry->mBuffer->size()) {
            entry->mNotifyConsumed->post();
            mAudioQueue.erase(mAudioQueue.begin());
            entry = NULL;
        }
        sizeCopied += copy;
        notifyIfMediaRenderingStarted();
    }

    if (mAudioFirstAnchorTimeMediaUs >= 0) {
        int64_t nowUs = ALooper::GetNowUs();
        setAnchorTime(mAudioFirstAnchorTimeMediaUs, nowUs - getPlayedOutAudioDurationUs(nowUs));
    }

    // we don't know how much data we are queueing for offloaded tracks
    mAnchorMaxMediaUs = -1;

    if (hasEOS) {
        (new AMessage(kWhatStopAudioSink, id()))->post();
    }

    return sizeCopied;
}

bool NuPlayer::Renderer::onDrainAudioQueue() {
    uint32_t numFramesPlayed;
#ifdef MTK_AOSP_ENHANCEMENT   //flush mutex
     Mutex::Autolock autoLock(mFlushLock);
#endif
    if (mAudioSink->getPosition(&numFramesPlayed) != OK) {
#ifdef MTK_AOSP_ENHANCEMENT
        // mtk80902: ALPS00504270
        // ACodec got an error at the beginning, if here returned
        // false directly then NuPlayer would never receive 
        // audio renderer EOS anymore.
        ALOGD("audio sink get position false.,mAudioQueue size:%d", mAudioQueue.size());
        while (!mAudioQueue.empty()) {
            QueueEntry *entry = &*mAudioQueue.begin();
            if (entry->mBuffer == NULL) {
                // EOS
                notifyEOS(true /* audio */, entry->mFinalResult);
                mAudioQueue.erase(mAudioQueue.begin());
                entry = NULL;
                mAudioEOS = true;
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT				
				if(mSMSpeed >1){
					mNeedSync = true;
				}	
#endif				
                ALOGD("audio position EOS at the beginning.");
            } else {
                mAudioQueue.erase(mAudioQueue.begin());
                entry = NULL;
            }
        }
#endif
        return false;
    }

    ssize_t numFramesAvailableToWrite =
        mAudioSink->frameCount() - (mNumFramesWritten - numFramesPlayed);

#ifdef MTK_AOSP_ENHANCEMENT
	if (numFramesPlayed > mNumFramesWritten) 
		ALOGW("numFramesPlayed(%d) > mNumFramesWritten(%d), no reset @ onDrainAudioQueue", numFramesPlayed, mNumFramesWritten);

	if (numFramesAvailableToWrite > mAudioSink->frameCount()*3/4 || mAudioQueue.size() <= 3)
		ALOGW("numFramesAvailableToWrite(%d), mAudioSink frameCount(%d), mAudioQueue size(%d), numFramesPlayed(%d), mNumFramesWritten(%d)", 
			numFramesAvailableToWrite, mAudioSink->frameCount(), mAudioQueue.size(), numFramesPlayed, mNumFramesWritten);
#endif
#if 0
    if (numFramesAvailableToWrite == mAudioSink->frameCount()) {
        ALOGI("audio sink underrun");
    } else {
        ALOGV("audio queue has %d frames left to play",
             mAudioSink->frameCount() - numFramesAvailableToWrite);
    }
#endif

    size_t numBytesAvailableToWrite =
        numFramesAvailableToWrite * mAudioSink->frameSize();

    while (numBytesAvailableToWrite > 0 && !mAudioQueue.empty()) {
        QueueEntry *entry = &*mAudioQueue.begin();

        mLastAudioBufferDrained = entry->mBufferOrdinal;

        if (entry->mBuffer == NULL) {
            // EOS
            int64_t postEOSDelayUs = 0;
            if (mAudioSink->needsTrailingPadding()) {
                postEOSDelayUs = getPendingAudioPlayoutDurationUs(ALooper::GetNowUs());
            }
            notifyEOS(true /* audio */, entry->mFinalResult, postEOSDelayUs);

            mAudioQueue.erase(mAudioQueue.begin());
            entry = NULL;
            if (mAudioSink->needsTrailingPadding()) {
                // If we're not in gapless playback (i.e. through setNextPlayer), we
                // need to stop the track here, because that will play out the last
                // little bit at the end of the file. Otherwise short files won't play.
                mAudioSink->stop();
                mNumFramesWritten = 0;
            }
#ifdef MTK_AOSP_ENHANCEMENT
            mAudioEOS = true;
#endif
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT
			if(mSMSpeed >1){
				mNeedSync = true;
			}
#endif
            ALOGD("audio position EOS");
            return false;
        }

        if (entry->mOffset == 0) {
            int64_t mediaTimeUs;
            CHECK(entry->mBuffer->meta()->findInt64("timeUs", &mediaTimeUs));
            ALOGV("rendering audio at media time %.2f secs", mediaTimeUs / 1E6);
            onNewAudioMediaTime(mediaTimeUs);
#ifdef MTK_AOSP_ENHANCEMENT
#if DUMP_PROFILE
            dumpProfile("render", mediaTimeUs);
#endif
#endif
        }

        size_t copy = entry->mBuffer->size() - entry->mOffset;
        if (copy > numBytesAvailableToWrite) {
            copy = numBytesAvailableToWrite;
        }

        ssize_t written = mAudioSink->write(entry->mBuffer->data() + entry->mOffset, copy);
        if (written < 0) {
            // An error in AudioSink write. Perhaps the AudioSink was not properly opened.
            ALOGE("AudioSink write error(%zd) when writing %zu bytes", written, copy);
            break;
        }

        entry->mOffset += written;
        if (entry->mOffset == entry->mBuffer->size()) {
            entry->mNotifyConsumed->post();
            mAudioQueue.erase(mAudioQueue.begin());

            entry = NULL;
        }

        numBytesAvailableToWrite -= written;
        size_t copiedFrames = written / mAudioSink->frameSize();
        mNumFramesWritten += copiedFrames;

        notifyIfMediaRenderingStarted();

        if (written != (ssize_t)copy) {
            // A short count was received from AudioSink::write()
            //
            // AudioSink write should block until exactly the number of bytes are delivered.
            // But it may return with a short count (without an error) when:
            //
            // 1) Size to be copied is not a multiple of the frame size. We consider this fatal.
            // 2) AudioSink is an AudioCache for data retrieval, and the AudioCache is exceeded.

            // (Case 1)
            // Must be a multiple of the frame size.  If it is not a multiple of a frame size, it
            // needs to fail, as we should not carry over fractional frames between calls.
            CHECK_EQ(copy % mAudioSink->frameSize(), 0);

            // (Case 2)
            // Return early to the caller.
            // Beware of calling immediately again as this may busy-loop if you are not careful.
            ALOGW("AudioSink write short frame count %zd < %zu", written, copy);
            break;
        }
    }
    mAnchorMaxMediaUs =
        mAnchorTimeMediaUs +
                (int64_t)(max((long long)mNumFramesWritten - mAnchorNumFramesWritten, 0LL)
                        * 1000LL * mAudioSink->msecsPerFrame());

    return !mAudioQueue.empty();
}

int64_t NuPlayer::Renderer::getPendingAudioPlayoutDurationUs(int64_t nowUs) {
    int64_t writtenAudioDurationUs =
        mNumFramesWritten * 1000LL * mAudioSink->msecsPerFrame();
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT
		if(mSMSpeed > 1){
			int64_t audiolatency = 1000LL * mAudioSink->latency();
			int64_t totalPending = (writtenAudioDurationUs - getPlayedOutAudioDurationUs(nowUs))*mSMSpeed + audiolatency/2;
			return totalPending;
		}
#endif	
    return writtenAudioDurationUs - getPlayedOutAudioDurationUs(nowUs);
}

int64_t NuPlayer::Renderer::getRealTimeUs(int64_t mediaTimeUs, int64_t nowUs) {
    int64_t currentPositionUs;
    if (mPaused || getCurrentPositionOnLooper(
            &currentPositionUs, nowUs, true /* allowPastQueuedVideo */) != OK) {
        // If failed to get current position, e.g. due to audio clock is not ready, then just
        // play out video immediately without delay.
        return nowUs;
    }
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT
	if(mNeedSync){
		return (mediaTimeUs - mAnchorTimeMediaUs)*mPreSMSpeed+ mAnchorTimeRealUs;
	}else{
		return (mediaTimeUs - mAnchorTimeMediaUs)*mSMSpeed+ mAnchorTimeRealUs;
	}	
#endif	
    return (mediaTimeUs - currentPositionUs) + nowUs;
}

void NuPlayer::Renderer::onNewAudioMediaTime(int64_t mediaTimeUs) {
    // TRICKY: vorbis decoder generates multiple frames with the same
    // timestamp, so only update on the first frame with a given timestamp
    if (mediaTimeUs == mAnchorTimeMediaUs) {
        return;
    }
    setAudioFirstAnchorTimeIfNeeded(mediaTimeUs);
    int64_t nowUs = ALooper::GetNowUs();
#ifdef MTK_AOSP_ENHANCEMENT
    if (mPaused)  setPauseStartedTimeRealUs(nowUs);                 // handle pause->seek position issue 
#endif
    setAnchorTime(
            mediaTimeUs, nowUs + getPendingAudioPlayoutDurationUs(nowUs), mNumFramesWritten);
}


void NuPlayer::Renderer::postDrainVideoQueue_l() {
#ifdef MTK_AOSP_ENHANCEMENT
    if (mDrainVideoQueuePending
            || isSyncQueues()
            || (mPaused && mVideoSampleReceived)) {
        return;
    }
#else

    if (mDrainVideoQueuePending
            || mSyncQueues
            || (mPaused && mVideoSampleReceived)) {
        return;
    }
#endif

    if (mVideoQueue.empty()) {
        return;
    }

    QueueEntry &entry = *mVideoQueue.begin();

    sp<AMessage> msg = new AMessage(kWhatDrainVideoQueue, id());
    msg->setInt32("generation", mVideoQueueGeneration);

    if (entry.mBuffer == NULL) {
        // EOS doesn't carry a timestamp.
        msg->post();
        mDrainVideoQueuePending = true;
        return;
    }

    int64_t delayUs;
    int64_t nowUs = ALooper::GetNowUs();
    int64_t realTimeUs;
    if (mFlags & FLAG_REAL_TIME) {
        int64_t mediaTimeUs;
        CHECK(entry.mBuffer->meta()->findInt64("timeUs", &mediaTimeUs));
        realTimeUs = mediaTimeUs;
    } else {
        int64_t mediaTimeUs;
        CHECK(entry.mBuffer->meta()->findInt64("timeUs", &mediaTimeUs));

        if (mAnchorTimeMediaUs < 0) {
            setAnchorTime(mediaTimeUs, nowUs);
            mPausePositionMediaTimeUs = mediaTimeUs;
            mAnchorMaxMediaUs = mediaTimeUs;
#ifdef MTK_AOSP_ENHANCEMENT
            if (mPaused) setPauseStartedTimeRealUs(nowUs);                  // handle pause->seek position issue 
#endif
            realTimeUs = nowUs;
        } else {
            realTimeUs = getRealTimeUs(mediaTimeUs, nowUs);
        }
#ifdef MTK_AOSP_ENHANCEMENT
        if (!mHasAudio || mAudioEOS) 
#else
       	if (!mHasAudio)  
#endif
       {     

            mAnchorMaxMediaUs = mediaTimeUs + 100000; // smooth out videos >= 10fps
        }

        // Heuristics to handle situation when media time changed without a
        // discontinuity. If we have not drained an audio buffer that was
        // received after this buffer, repost in 10 msec. Otherwise repost
        // in 500 msec.
        delayUs = realTimeUs - nowUs;
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT
		if(mSMSpeed >1){
			delayUs /=mSMSpeed;
		}
#endif		
        if (delayUs > 500000) {
            int64_t postDelayUs = 500000;
            if (mHasAudio && (mLastAudioBufferDrained - entry.mBufferOrdinal) <= 0) {
                postDelayUs = 10000;
            }
            msg->setWhat(kWhatPostDrainVideoQueue);
            msg->post(postDelayUs);
            mVideoScheduler->restart();
            ALOGI("possible video time jump of %dms, retrying in %dms",
                    (int)(delayUs / 1000), (int)(postDelayUs / 1000));
            mDrainVideoQueuePending = true;
            return;
        }
    }

#ifdef MTK_AOSP_ENHANCEMENT
  if(sFrameAVsyncByVFS){
#endif
    realTimeUs = mVideoScheduler->schedule(realTimeUs * 1000) / 1000;
    int64_t twoVsyncsUs = 2 * (mVideoScheduler->getVsyncPeriod() / 1000);

    delayUs = realTimeUs - nowUs;

    ALOGW_IF(delayUs > 500000, "unusually high delayUs: %" PRId64, delayUs);
    // post 2 display refreshes before rendering is due
#ifdef MTK_AOSP_ENHANCEMENT
#ifdef MTK_CLEARMOTION_SUPPORT
    delayUs = realTimeUs - (nowUs + MAX_VIDEO_EARLY_POST_US); //Early queue 50ms
#endif
#endif
    msg->post(delayUs > twoVsyncsUs ? delayUs - twoVsyncsUs : 0);
#ifdef MTK_AOSP_ENHANCEMENT
  }else{
	delayUs -= MAX_VIDEO_EARLY_POST_US;	
       msg->post(delayUs);
  }
#endif  

    mDrainVideoQueuePending = true;
}

void NuPlayer::Renderer::onDrainVideoQueue() {
    if (mVideoQueue.empty()) {
        return;
    }

    QueueEntry *entry = &*mVideoQueue.begin();

    if (entry->mBuffer == NULL) {
        // EOS

        notifyEOS(false /* audio */, entry->mFinalResult);

        mVideoQueue.erase(mVideoQueue.begin());
        entry = NULL;
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_AUDIO_CHANGE_SUPPORT) 
        mVideoEOS = true;
        ALOGD("mVideoEOS:%d",mVideoEOS);
#endif

#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_CLEARMOTION_SUPPORT)  
		mMJCPauseDelay = false;
#endif
        setVideoLateByUs(0);
        return;
    }

    int64_t nowUs = -1;
    int64_t realTimeUs;
    if (mFlags & FLAG_REAL_TIME) {
        CHECK(entry->mBuffer->meta()->findInt64("timeUs", &realTimeUs));
    } else {
        int64_t mediaTimeUs;
        CHECK(entry->mBuffer->meta()->findInt64("timeUs", &mediaTimeUs));

        nowUs = ALooper::GetNowUs();
        realTimeUs = getRealTimeUs(mediaTimeUs, nowUs);
    }

    bool tooLate = false;

    if (!mPaused) {
        if (nowUs == -1) {
            nowUs = ALooper::GetNowUs();
        }
        setVideoLateByUs(nowUs - realTimeUs);
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT
			 handleRenderBufferForSlowMotion(entry);
#endif		

       tooLate = (mVideoLateByUs > 40000);

#ifdef MTK_AOSP_ENHANCEMENT
			 tooLate = (mVideoLateByUs > 250000);
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT
		if(mSMSpeed >1){
			 tooLate = (mVideoLateByUs/mSMSpeed > 250000);
		}
#endif
#endif
        if (tooLate) {
            ALOGV("video late by %lld us (%.2f secs)",
                 mVideoLateByUs, mVideoLateByUs / 1E6);
        } else {
            ALOGV("rendering video at media time %.2f secs",
                    (mFlags & FLAG_REAL_TIME ? realTimeUs :
                    (realTimeUs + mAnchorTimeMediaUs - mAnchorTimeRealUs)) / 1E6);
        }
    } else {
        setVideoLateByUs(0);
        if (!mVideoSampleReceived && !mHasAudio) {
            // This will ensure that the first frame after a flush won't be used as anchor
            // when renderer is in paused state, because resume can happen any time after seek.
            setAnchorTime(-1, -1);
        }
    }

    entry->mNotifyConsumed->setInt64("timestampNs", realTimeUs * 1000ll);
#ifdef MTK_AOSP_ENHANCEMENT
    if(mLateVideoToDisplay == true) 
	tooLate = handleRenderBufferLateInfo(tooLate,realTimeUs,entry);
    ATRACE_BEGIN("RenderVideo");
#endif
    entry->mNotifyConsumed->setInt32("render", !tooLate);
    entry->mNotifyConsumed->post();
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_CLEARMOTION_SUPPORT)  
    handleForClearMotionPause(tooLate,entry);
#endif	


    mVideoQueue.erase(mVideoQueue.begin());
    entry = NULL;

    mVideoSampleReceived = true;
#ifdef MTK_AOSP_ENHANCEMENT  
    ATRACE_END( );
#endif
    if (!mPaused) {
        if (!mVideoRenderingStarted) {
            mVideoRenderingStarted = true;
            notifyVideoRenderingStart();
        }
        notifyIfMediaRenderingStarted();
    }
}

void NuPlayer::Renderer::notifyVideoRenderingStart() {
    sp<AMessage> notify = mNotify->dup();
    notify->setInt32("what", kWhatVideoRenderingStart);
    notify->post();
}

void NuPlayer::Renderer::notifyEOS(bool audio, status_t finalResult, int64_t delayUs) {
    sp<AMessage> notify = mNotify->dup();
    notify->setInt32("what", kWhatEOS);
    notify->setInt32("audio", static_cast<int32_t>(audio));
    notify->setInt32("finalResult", finalResult);
    notify->post(delayUs);
}

void NuPlayer::Renderer::notifyAudioOffloadTearDown() {
    (new AMessage(kWhatAudioOffloadTearDown, id()))->post();
}

void NuPlayer::Renderer::onQueueBuffer(const sp<AMessage> &msg) {
    int32_t audio;
    CHECK(msg->findInt32("audio", &audio));

    setHasMedia(audio);

    if (mHasVideo) {
        if (mVideoScheduler == NULL) {
            mVideoScheduler = new VideoFrameScheduler();
            mVideoScheduler->init();
        }
    }

    if (dropBufferWhileFlushing(audio, msg)) {
        return;
    }

    sp<ABuffer> buffer;
    CHECK(msg->findBuffer("buffer", &buffer));

    sp<AMessage> notifyConsumed;
    CHECK(msg->findMessage("notifyConsumed", &notifyConsumed));

    QueueEntry entry;
    entry.mBuffer = buffer;
    entry.mNotifyConsumed = notifyConsumed;
    entry.mOffset = 0;
    entry.mFinalResult = OK;
    entry.mBufferOrdinal = ++mTotalBuffersQueued;

    Mutex::Autolock autoLock(mLock);
    if (audio) {
        mAudioQueue.push_back(entry);
        postDrainAudioQueue_l();
    } else {
        mVideoQueue.push_back(entry);
        postDrainVideoQueue_l();
    }
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_AUDIO_CHANGE_SUPPORT) 
    if((mAudioEOS == true && mVideoEOS != true) || (mAudioEOS != true && mVideoEOS == true)){
        //ALOGD("audio or video is EOS,donot sync queue,mVideoEOS:%d,mAudioEOS:%d",mVideoEOS,mAudioEOS);
        syncQueuesDone_l();    
    }
#endif

#ifdef MTK_AOSP_ENHANCEMENT
    if (!isSyncQueues() || mAudioQueue.empty() || mVideoQueue.empty()) {
        return;
    }
#else
    if (!mSyncQueues || mAudioQueue.empty() || mVideoQueue.empty()) {
        return;
    }
#endif

    sp<ABuffer> firstAudioBuffer = (*mAudioQueue.begin()).mBuffer;
    sp<ABuffer> firstVideoBuffer = (*mVideoQueue.begin()).mBuffer;

    if (firstAudioBuffer == NULL || firstVideoBuffer == NULL) {
        // EOS signalled on either queue.
        syncQueuesDone_l();
        return;
    }

    int64_t firstAudioTimeUs;
    int64_t firstVideoTimeUs;
    CHECK(firstAudioBuffer->meta()
            ->findInt64("timeUs", &firstAudioTimeUs));
    CHECK(firstVideoBuffer->meta()
            ->findInt64("timeUs", &firstVideoTimeUs));

    int64_t diff = firstVideoTimeUs - firstAudioTimeUs;

    ALOGV("queueDiff = %.2f secs", diff / 1E6);
    MM_LOGI("queueDiff = %.2f secs", diff / 1E6);

    List<QueueEntry>::iterator it;
    if (diff > 100000ll) {
        // Audio data starts More than 0.1 secs before video.
        // Drop some audio.

        (*mAudioQueue.begin()).mNotifyConsumed->post();
        mAudioQueue.erase(mAudioQueue.begin());
#ifdef MTK_AOSP_ENHANCEMENT      
        ALOGD("rock drop a audio data");
        if(mUseFlushAudioSyncQueues){
            it =  mAudioQueue.begin();
            while(it != mAudioQueue.end()){
                
                if((*it).mBuffer == NULL){
                	 it++;
                }else{
                	CHECK((*it).mBuffer->meta()
                            ->findInt64("timeUs", &firstAudioTimeUs));

                	diff = firstVideoTimeUs - firstAudioTimeUs;
                	if(diff > 100000ll){
                    (*it).mNotifyConsumed->post();
                    it = mAudioQueue.erase(it);
                    ALOGD("rock still drop a audio data %lld us", firstAudioTimeUs);
                	}else{
                	  it++;
                    ALOGD("rock keep a audio data %lld us", firstAudioTimeUs);
                	}
               }
            }
        }
#endif        

        return;
    }
#ifdef MTK_AOSP_ENHANCEMENT
    if((diff <  -100000ll) && (mLateVideoToDisplay == true)) {
        // video data starts More than 0.1 secs before audio.
        // Drop some video.
#if defined(MTK_AUDIO_CHANGE_SUPPORT) // for CR: ALPS01981846
        it =  mVideoQueue.begin();  
        while(it != mVideoQueue.end()){
			if((*it).mBuffer == NULL){
                	 it++;
                }else{
                	CHECK((*it).mBuffer->meta()
                            ->findInt64("timeUs", &firstVideoTimeUs));

                	diff = firstVideoTimeUs - firstAudioTimeUs;
                	if(diff < -100000ll){
                        (*it).mNotifyConsumed->post();
                        it = mVideoQueue.erase(it);
                        ALOGD("rock still drop a video data %lld us", firstVideoTimeUs);
                	}else{
                	    it++;
                        ALOGD("rock keep a video data %lld us", firstVideoTimeUs);
						return;
                	}
               }
        }
		return;
#else
        ALOGE("before playback, video is early than audio drop diff = %.2f", diff / 1E6);
        (*mVideoQueue.begin()).mNotifyConsumed->post();
        mVideoQueue.erase(mVideoQueue.begin());
        return;
#endif
    }
#endif

    syncQueuesDone_l();
}

void NuPlayer::Renderer::syncQueuesDone_l() {
#ifdef MTK_AOSP_ENHANCEMENT
    if (!isSyncQueues()) {
        return;
    }
#else
    if (!mSyncQueues) {
        return;
    }
#endif

    mSyncQueues = false;

    if (!mAudioQueue.empty()) {
        postDrainAudioQueue_l();
    }

    if (!mVideoQueue.empty()) {
        postDrainVideoQueue_l();
    }
}

void NuPlayer::Renderer::onQueueEOS(const sp<AMessage> &msg) {
    int32_t audio;
    CHECK(msg->findInt32("audio", &audio));

    if (dropBufferWhileFlushing(audio, msg)) {
        return;
    }

    int32_t finalResult;
    CHECK(msg->findInt32("finalResult", &finalResult));

    QueueEntry entry;
    entry.mOffset = 0;
    entry.mFinalResult = finalResult;

    Mutex::Autolock autoLock(mLock);
    if (audio) {
#ifdef MTK_AOSP_ENHANCEMENT
        // ALPS01881347 audio EOS and video all output buffer pending in render
        // should  syncQueuesDone here to render video whether audioQueue empty
        if (/*mAudioQueue.empty() &&*/ isSyncQueues()) {
            syncQueuesDone_l();
        }
#else
        if (mAudioQueue.empty() && mSyncQueues) {
            syncQueuesDone_l();
        }
#endif
        mAudioQueue.push_back(entry);
        postDrainAudioQueue_l();
    } else {
#ifdef MTK_AOSP_ENHANCEMENT
        if (/*mVideoQueue.empty() &&*/ isSyncQueues()) {
            syncQueuesDone_l();
        }
#else
        if (mVideoQueue.empty() && mSyncQueues) {
            syncQueuesDone_l();
        }
#endif
        mVideoQueue.push_back(entry);
#ifdef MTK_AOSP_ENHANCEMENT
        if(mPaused)
        	mVideoSampleReceived = true;
#endif
        postDrainVideoQueue_l();
#ifdef MTK_AOSP_ENHANCEMENT
       // ALPS01958589: when four audio output buffer is full and video eos comes
       // then no postDrainAudioQueue_l will be called and audio in render can not drain
       if(!isSyncQueues()){
       postDrainAudioQueue_l(); 
	   }
#endif 
    }
}

void NuPlayer::Renderer::onFlush(const sp<AMessage> &msg) {
    int32_t audio, notifyComplete;
    CHECK(msg->findInt32("audio", &audio));

    {
        Mutex::Autolock autoLock(mFlushLock);
        if (audio) {
            mFlushingAudio = false;
            notifyComplete = mNotifyCompleteAudio;
            mNotifyCompleteAudio = false;
        } else {
            mFlushingVideo = false;
            notifyComplete = mNotifyCompleteVideo;
            mNotifyCompleteVideo = false;
        }
    }

    // If we're currently syncing the queues, i.e. dropping audio while
    // aligning the first audio/video buffer times and only one of the
    // two queues has data, we may starve that queue by not requesting
    // more buffers from the decoder. If the other source then encounters
    // a discontinuity that leads to flushing, we'll never find the
    // corresponding discontinuity on the other queue.
    // Therefore we'll stop syncing the queues if at least one of them
    // is flushed.
    {
         Mutex::Autolock autoLock(mLock);
#ifdef MTK_AOSP_ENHANCEMENT
         mPausePositionMediaTimeUs = -1;
         ALOGI("onflush audio %d flag %d",audio,mFlags);
#endif
        	 syncQueuesDone_l();

#ifndef MTK_AOSP_ENHANCEMENT
         setPauseStartedTimeRealUs(-1);
#endif
         setAnchorTime(-1, -1);
#ifdef MTK_AOSP_ENHANCEMENT
         mAnchorMaxMediaUs = -1;                         // handle pause->seek position issue 
#endif
    }

    ALOGV("flushing %s", audio ? "audio" : "video");
    if (audio) {
        {
            Mutex::Autolock autoLock(mLock);
#ifdef MTK_AOSP_ENHANCEMENT
            //@debug
            dumpQueue(&mAudioQueue, audio);
#endif
            flushQueue(&mAudioQueue);

            ++mAudioQueueGeneration;
            prepareForMediaRenderingStart();

            if (offloadingAudio()) {
                setAudioFirstAnchorTime(-1);
            }
        }

        mDrainAudioQueuePending = false;

        if (offloadingAudio()) {
            mAudioSink->pause();
            mAudioSink->flush();
            mAudioSink->start();
#ifdef MTK_AOSP_ENHANCEMENT
        } else {
            if (mPaused) {
                MM_LOGI("audiosink flush.");
                mAudioSink->flush();
			    mNumFramesWritten = 0;
            }
#endif
        }
    } else {
#ifdef MTK_AOSP_ENHANCEMENT
        //@debug
        dumpQueue(&mVideoQueue, audio);
#endif
        flushQueue(&mVideoQueue);

        mDrainVideoQueuePending = false;
        ++mVideoQueueGeneration;

        if (mVideoScheduler != NULL) {
            mVideoScheduler->restart();
        }

        prepareForMediaRenderingStart();
    }

    mVideoSampleReceived = false;

    if (notifyComplete) {
        notifyFlushComplete(audio);
    }
}

void NuPlayer::Renderer::flushQueue(List<QueueEntry> *queue) {
    while (!queue->empty()) {
        QueueEntry *entry = &*queue->begin();

        if (entry->mBuffer != NULL) {
            entry->mNotifyConsumed->post();
        }

        queue->erase(queue->begin());
        entry = NULL;
    }
}

void NuPlayer::Renderer::notifyFlushComplete(bool audio) {
    sp<AMessage> notify = mNotify->dup();
    notify->setInt32("what", kWhatFlushComplete);
    notify->setInt32("audio", static_cast<int32_t>(audio));
    notify->post();
}

bool NuPlayer::Renderer::dropBufferWhileFlushing(
        bool audio, const sp<AMessage> &msg) {
    bool flushing = false;

    {
        Mutex::Autolock autoLock(mFlushLock);
        if (audio) {
            flushing = mFlushingAudio;
        } else {
            flushing = mFlushingVideo;
        }
    }

    if (!flushing) {
        return false;
    }

    sp<AMessage> notifyConsumed;
    if (msg->findMessage("notifyConsumed", &notifyConsumed)) {
        notifyConsumed->post();
    }

    return true;
}

void NuPlayer::Renderer::onAudioSinkChanged() {
    if (offloadingAudio()) {
        return;
    }
    CHECK(!mDrainAudioQueuePending);
    mNumFramesWritten = 0;
    mAnchorNumFramesWritten = -1;
	#ifndef MTK_AOSP_ENHANCEMENT
    uint32_t written;
    if (mAudioSink->getFramesWritten(&written) == OK) {
        mNumFramesWritten = written;
    }
	#endif
}

void NuPlayer::Renderer::onDisableOffloadAudio() {
    Mutex::Autolock autoLock(mLock);
    mFlags &= ~FLAG_OFFLOAD_AUDIO;
    ++mAudioQueueGeneration;
}

void NuPlayer::Renderer::onEnableOffloadAudio() {
    Mutex::Autolock autoLock(mLock);
    mFlags |= FLAG_OFFLOAD_AUDIO;
    ++mAudioQueueGeneration;
}

void NuPlayer::Renderer::onPause() {
    if (mPaused) {
        ALOGW("Renderer::onPause() called while already paused!");
        return;
    }
    int64_t currentPositionUs;
    int64_t pausePositionMediaTimeUs;
    if (getCurrentPositionFromAnchor(
            &currentPositionUs, ALooper::GetNowUs()) == OK) {
        pausePositionMediaTimeUs = currentPositionUs;
    } else {
        // Set paused position to -1 (unavailabe) if we don't have anchor time
        // This could happen if client does a seekTo() immediately followed by
        // pause(). Renderer will be flushed with anchor time cleared. We don't
        // want to leave stale value in mPausePositionMediaTimeUs.
        pausePositionMediaTimeUs = -1;
    }
    {
        Mutex::Autolock autoLock(mLock);
        mPausePositionMediaTimeUs = pausePositionMediaTimeUs;
        ++mAudioQueueGeneration;
        ++mVideoQueueGeneration;
        prepareForMediaRenderingStart();
        mPaused = true;
        setPauseStartedTimeRealUs(ALooper::GetNowUs());
    }

    mDrainAudioQueuePending = false;
    mDrainVideoQueuePending = false;

    if (mHasAudio) {
        mAudioSink->pause();
        startAudioOffloadPauseTimeout();
    }

    ALOGV("now paused audio queue has %d entries, video has %d entries",
          mAudioQueue.size(), mVideoQueue.size());
}

void NuPlayer::Renderer::onResume() {
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_CLEARMOTION_SUPPORT)  
      readProperties();

    if( mPausing) {
        mPausing = false;
        return;
    }
#endif	
    if (!mPaused) {
        return;
    }

    if (mHasAudio) {
        cancelAudioOffloadPauseTimeout();
        mAudioSink->start();
    }

    Mutex::Autolock autoLock(mLock);
    mPaused = false;
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT
	mSMSpeed = mPauseSpeed;
#endif	
    if (mPauseStartedTimeRealUs != -1) {
        int64_t newAnchorRealUs =
            mAnchorTimeRealUs + ALooper::GetNowUs() - mPauseStartedTimeRealUs;
        setAnchorTime(
                mAnchorTimeMediaUs, newAnchorRealUs, mAnchorNumFramesWritten, true /* resume */);
    }

    if (!mAudioQueue.empty()) {
        postDrainAudioQueue_l();
    }

    if (!mVideoQueue.empty()) {
        postDrainVideoQueue_l();
    }
}

void NuPlayer::Renderer::onSetVideoFrameRate(float fps) {
    if (mVideoScheduler == NULL) {
        mVideoScheduler = new VideoFrameScheduler();
    }
    mVideoScheduler->init(fps);
}

// TODO: Remove unnecessary calls to getPlayedOutAudioDurationUs()
// as it acquires locks and may query the audio driver.
//
// Some calls could conceivably retrieve extrapolated data instead of
// accessing getTimestamp() or getPosition() every time a data buffer with
// a media time is received.
//
int64_t NuPlayer::Renderer::getPlayedOutAudioDurationUs(int64_t nowUs) {
#ifdef MTK_AOSP_ENHANCEMENT
#if USE_AUDIO_TRACK_CENTER
	if (!offloadingAudio()) {
		return  getPlayedOutAudioDurationUsByAudioTrackCenter();
	}
#endif        
#endif

	
    uint32_t numFramesPlayed;
    int64_t numFramesPlayedAt;
    AudioTimestamp ts;
    static const int64_t kStaleTimestamp100ms = 100000;

    status_t res = mAudioSink->getTimestamp(ts);
    if (res == OK) {                 // case 1: mixing audio tracks and offloaded tracks.
        numFramesPlayed = ts.mPosition;
        numFramesPlayedAt =
            ts.mTime.tv_sec * 1000000LL + ts.mTime.tv_nsec / 1000;
        const int64_t timestampAge = nowUs - numFramesPlayedAt;
        if (timestampAge > kStaleTimestamp100ms) {
            // This is an audio FIXME.
            // getTimestamp returns a timestamp which may come from audio mixing threads.
            // After pausing, the MixerThread may go idle, thus the mTime estimate may
            // become stale. Assuming that the MixerThread runs 20ms, with FastMixer at 5ms,
            // the max latency should be about 25ms with an average around 12ms (to be verified).
            // For safety we use 100ms.
            ALOGV("getTimestamp: returned stale timestamp nowUs(%lld) numFramesPlayedAt(%lld)",
                    (long long)nowUs, (long long)numFramesPlayedAt);
            numFramesPlayedAt = nowUs - kStaleTimestamp100ms;
        }
        //ALOGD("getTimestamp: OK %d %lld", numFramesPlayed, (long long)numFramesPlayedAt);
    } else if (res == WOULD_BLOCK) { // case 2: transitory state on start of a new track
        numFramesPlayed = 0;
        numFramesPlayedAt = nowUs;
        //ALOGD("getTimestamp: WOULD_BLOCK %d %lld",
        //        numFramesPlayed, (long long)numFramesPlayedAt);
    } else {                         // case 3: transitory at new track or audio fast tracks.
        res = mAudioSink->getPosition(&numFramesPlayed);
        CHECK_EQ(res, (status_t)OK);
        numFramesPlayedAt = nowUs;
        numFramesPlayedAt += 1000LL * mAudioSink->latency() / 2; /* XXX */
        //ALOGD("getPosition: %d %lld", numFramesPlayed, numFramesPlayedAt);
    }

    // TODO: remove the (int32_t) casting below as it may overflow at 12.4 hours.
    //CHECK_EQ(numFramesPlayed & (1 << 31), 0);  // can't be negative until 12.4 hrs, test
    int64_t durationUs = (int64_t)((int32_t)numFramesPlayed * 1000LL * mAudioSink->msecsPerFrame())
            + nowUs - numFramesPlayedAt;
    if (durationUs < 0) {
        // Occurs when numFramesPlayed position is very small and the following:
        // (1) In case 1, the time nowUs is computed before getTimestamp() is called and
        //     numFramesPlayedAt is greater than nowUs by time more than numFramesPlayed.
        // (2) In case 3, using getPosition and adding mAudioSink->latency() to
        //     numFramesPlayedAt, by a time amount greater than numFramesPlayed.
        //
        // Both of these are transitory conditions.
        ALOGV("getPlayedOutAudioDurationUs: negative duration %lld set to zero", (long long)durationUs);
        durationUs = 0;
    }
    ALOGV("getPlayedOutAudioDurationUs(%lld) nowUs(%lld) frames(%u) framesAt(%lld)",
            (long long)durationUs, (long long)nowUs, numFramesPlayed, (long long)numFramesPlayedAt);
    return durationUs;
}

void NuPlayer::Renderer::onAudioOffloadTearDown(AudioOffloadTearDownReason reason) {
    if (mAudioOffloadTornDown) {
        return;
    }
    mAudioOffloadTornDown = true;

    int64_t currentPositionUs;
    if (getCurrentPositionOnLooper(&currentPositionUs) != OK) {
        currentPositionUs = 0;
    }

    mAudioSink->stop();
    mAudioSink->flush();

    sp<AMessage> notify = mNotify->dup();
    notify->setInt32("what", kWhatAudioOffloadTearDown);
    notify->setInt64("positionUs", currentPositionUs);
    notify->setInt32("reason", reason);
    notify->post();
}

void NuPlayer::Renderer::startAudioOffloadPauseTimeout() {
    if (offloadingAudio()) {
        mWakeLock->acquire();
        sp<AMessage> msg = new AMessage(kWhatAudioOffloadPauseTimeout, id());
        msg->setInt32("generation", mAudioOffloadPauseTimeoutGeneration);
        msg->post(kOffloadPauseMaxUs);
    }
}

void NuPlayer::Renderer::cancelAudioOffloadPauseTimeout() {
    if (offloadingAudio()) {
        mWakeLock->release(true);
        ++mAudioOffloadPauseTimeoutGeneration;
    }
}

status_t NuPlayer::Renderer::onOpenAudioSink(
        const sp<AMessage> &format,
        bool offloadOnly,
        bool hasVideo,
        uint32_t flags) {
    ALOGV("openAudioSink: offloadOnly(%d) offloadingAudio(%d)",
            offloadOnly, offloadingAudio());
    bool audioSinkChanged = false;

    int32_t numChannels;
    CHECK(format->findInt32("channel-count", &numChannels));

    int32_t channelMask;
    if (!format->findInt32("channel-mask", &channelMask)) {
        // signal to the AudioSink to derive the mask from count.
        channelMask = CHANNEL_MASK_USE_CHANNEL_ORDER;
    }

    int32_t sampleRate;
    CHECK(format->findInt32("sample-rate", &sampleRate));

    if (offloadingAudio()) {
        audio_format_t audioFormat = AUDIO_FORMAT_PCM_16_BIT;
        AString mime;
        CHECK(format->findString("mime", &mime));
        status_t err = mapMimeToAudioFormat(audioFormat, mime.c_str());

        if (err != OK) {
            ALOGE("Couldn't map mime \"%s\" to a valid "
                    "audio_format", mime.c_str());
            onDisableOffloadAudio();
        } else {
            ALOGV("Mime \"%s\" mapped to audio_format 0x%x",
                    mime.c_str(), audioFormat);

            int avgBitRate = -1;
            format->findInt32("bit-rate", &avgBitRate);

            int32_t aacProfile = -1;
            if (audioFormat == AUDIO_FORMAT_AAC
                    && format->findInt32("aac-profile", &aacProfile)) {
                // Redefine AAC format as per aac profile
                mapAACProfileToAudioFormat(
                        audioFormat,
                        aacProfile);
            }

            audio_offload_info_t offloadInfo = AUDIO_INFO_INITIALIZER;
            offloadInfo.duration_us = -1;
            format->findInt64(
                    "durationUs", &offloadInfo.duration_us);
            offloadInfo.sample_rate = sampleRate;
            offloadInfo.channel_mask = channelMask;
            offloadInfo.format = audioFormat;
            offloadInfo.stream_type = AUDIO_STREAM_MUSIC;
            offloadInfo.bit_rate = avgBitRate;
            offloadInfo.has_video = hasVideo;
            offloadInfo.is_streaming = true;

            if (memcmp(&mCurrentOffloadInfo, &offloadInfo, sizeof(offloadInfo)) == 0) {
                ALOGV("openAudioSink: no change in offload mode");
                // no change from previous configuration, everything ok.
                return OK;
            }
            mCurrentPcmInfo = AUDIO_PCMINFO_INITIALIZER;

            ALOGV("openAudioSink: try to open AudioSink in offload mode");
            uint32_t offloadFlags = flags;
            offloadFlags |= AUDIO_OUTPUT_FLAG_COMPRESS_OFFLOAD;
            offloadFlags &= ~AUDIO_OUTPUT_FLAG_DEEP_BUFFER;
            audioSinkChanged = true;
            mAudioSink->close();
            err = mAudioSink->open(
                    sampleRate,
                    numChannels,
                    (audio_channel_mask_t)channelMask,
                    audioFormat,
                    8 /* bufferCount */,
                    &NuPlayer::Renderer::AudioSinkCallback,
                    this,
                    (audio_output_flags_t)offloadFlags,
                    &offloadInfo);

            if (err == OK) {
                // If the playback is offloaded to h/w, we pass
                // the HAL some metadata information.
                // We don't want to do this for PCM because it
                // will be going through the AudioFlinger mixer
                // before reaching the hardware.
                // TODO
                mCurrentOffloadInfo = offloadInfo;
                err = mAudioSink->start();
                ALOGV_IF(err == OK, "openAudioSink: offload succeeded");
            }
            if (err != OK) {
                // Clean up, fall back to non offload mode.
                mAudioSink->close();
                onDisableOffloadAudio();
                mCurrentOffloadInfo = AUDIO_INFO_INITIALIZER;
                ALOGV("openAudioSink: offload failed");
            }
        }
    }
    if (!offloadOnly && !offloadingAudio()) {
        ALOGV("openAudioSink: open AudioSink in NON-offload mode");

#ifdef MTK_AOSP_ENHANCEMENT 
	int32_t change = 0;
       if(format->findInt32("change",&change) && change ==1){
            if( (!audioFormatChange(format))
                #if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_AUDIO_CHANGE_SUPPORT)
                && (!mNoJudgeWhenChangeAudio)  //for ALPS01940787,not return OK to ensure call mAudioSink->open function
                #endif
                )  return OK;
       }
#endif
		
        uint32_t pcmFlags = flags;
        pcmFlags &= ~AUDIO_OUTPUT_FLAG_COMPRESS_OFFLOAD;

        const PcmInfo info = {
                (audio_channel_mask_t)channelMask,
                (audio_output_flags_t)pcmFlags,
                AUDIO_FORMAT_PCM_16_BIT, // TODO: change to audioFormat
                numChannels,
                sampleRate
        };
#ifdef MTK_AOSP_ENHANCEMENT
        if (memcmp(&mCurrentPcmInfo, &info, sizeof(info)) == 0) {
                ALOGV("openAudioSink: no change in pcm mode");
    #if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_AUDIO_CHANGE_SUPPORT)
                if (!mNoJudgeWhenChangeAudio) //for ALPS01940787,not return OK to ensure call mAudioSink->open function
    #endif
                return OK;
        }
#else
        if (memcmp(&mCurrentPcmInfo, &info, sizeof(info)) == 0) {
            ALOGV("openAudioSink: no change in pcm mode");
            // no change from previous configuration, everything ok.
            return OK;
        }
#endif

        audioSinkChanged = true;
        mAudioSink->close();
        mCurrentOffloadInfo = AUDIO_INFO_INITIALIZER;
        
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_HIGH_RESOLUTION_AUDIO_SUPPORT)
                int32_t bitWidth = 0;
                audio_format_t audioFormat = AUDIO_FORMAT_PCM_16_BIT;
                if (format->findInt32("bit-width", &bitWidth) && bitWidth > 16) {
                    ALOGI("bits width: %d, NuPlayer use high resolution audiotrack.", bitWidth);
                    audioFormat = AUDIO_FORMAT_PCM_8_24_BIT;
                }
                status_t err = mAudioSink->open(
                            sampleRate,
                            numChannels,
                            (audio_channel_mask_t)channelMask,
                            audioFormat,
                            8 /* bufferCount */,
                            NULL,
                            NULL,
                            (audio_output_flags_t)pcmFlags);
    #if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_AUDIO_CHANGE_SUPPORT)
                mNoJudgeWhenChangeAudio = false;
    #endif

#else
                status_t err = mAudioSink->open(
                            sampleRate,
                            numChannels,
                            (audio_channel_mask_t)channelMask,
                            AUDIO_FORMAT_PCM_16_BIT,
                            8 /* bufferCount */,
                            NULL,
                            NULL,
                            (audio_output_flags_t)pcmFlags);

#endif


        if (err != OK) {
            ALOGW("openAudioSink: non offloaded open failed status: %d", err);
            mCurrentPcmInfo = AUDIO_PCMINFO_INITIALIZER;
            return err;
        }
        mCurrentPcmInfo = info;
        mAudioSink->start();
#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT
		if(mAudioSink!=NULL){
			if(mSMSpeed == 1)
				mAudioSink->setParameters(String8("time_stretch=100"));
			else if(mSMSpeed == 2)
				mAudioSink->setParameters(String8("time_stretch=200"));
			else if(mSMSpeed == 4)
				mAudioSink->setParameters(String8("time_stretch=400"));
			else if(mSMSpeed == 16)
				mAudioSink->setParameters(String8("time_stretch=1600"));

		    extern AudioTrackCenter gAudioTrackCenter;
		    gAudioTrackCenter.setTimeStretch((uint32_t)mSMSpeed);	
		}else {
			ALOGW("mAudioSink==NULL");
		}		
#endif		

    }
    if (audioSinkChanged) {
        onAudioSinkChanged();
    }
    if (offloadingAudio()) {
        mAudioOffloadTornDown = false;
    }
    return OK;
}

void NuPlayer::Renderer::onCloseAudioSink() {
    mAudioSink->close();
    mCurrentOffloadInfo = AUDIO_INFO_INITIALIZER;
    mCurrentPcmInfo = AUDIO_PCMINFO_INITIALIZER;
}


#ifdef MTK_AOSP_ENHANCEMENT 

void NuPlayer::Renderer::notifyBufferingStart() {
    if (!mHasAudio) {
        mBufferingStartTimeRealUs = ALooper::GetNowUs();
    }
}

void NuPlayer::Renderer::notifyBufferingEnd() {
    if (!mHasAudio) {
        if (mBufferingStartTimeRealUs != -1) {
            mAnchorTimeRealUs += ALooper::GetNowUs() - mBufferingStartTimeRealUs;
        }
        mBufferingStartTimeRealUs = -1;
    }
}

void NuPlayer::Renderer::setUseSyncQueues(bool use) {
    mUseSyncQueues = use;
}

void NuPlayer::Renderer::setUseFlushAudioSyncQueues(bool use)
{
    ALOGD("set flush audio sync queue %d", use);
    mUseFlushAudioSyncQueues = use;
}
bool NuPlayer::Renderer::isSyncQueues() {
    return mUseSyncQueues && mSyncQueues;
}

//enable or disable when video is late, display one ,drop one or drop all late frames.
void NuPlayer::Renderer::setLateVideoToDisplay(bool display) {
    mLateVideoToDisplay = display;
    ALOGD("setLateVideoToDisplay = %d", mLateVideoToDisplay);
}

void NuPlayer::Renderer::dumpQueue(List<QueueEntry> *queue, bool audio) {
    List<QueueEntry>::iterator it = queue->begin();
    ALOGD("dumping current %s queue(%d fs)", audio ? "audio" : "video", queue->size());
    while (it != queue->end()) {
        QueueEntry *entry = &*it;
        if (entry->mBuffer != NULL) {
            int64_t mediaTimeUs = 0;
            CHECK(entry->mBuffer->meta()->findInt64("timeUs", &mediaTimeUs));
            ALOGD("\t\t (%.2f secs)",  mediaTimeUs / 1E6);
        } else {
            ALOGD("\t\t (null)");
        }
        it++;
    }

}

void NuPlayer::Renderer::dumpProfile(const char* tag, int64_t timeUs) {
    ALOGD("[dump] %s %s %.2f", "audio", tag, timeUs / 1E6);
}

void NuPlayer::Renderer::dumpBuffer(const char* fileName, char* p, size_t size) {
    FILE *fp;
    fp = fopen(fileName, "a+");
    if (fp == NULL) {
        ALOGE("error when create dump file %s", fileName);
        return;
    }
    fwrite(p, sizeof(char), size, fp);
    fclose(fp);

}
int64_t NuPlayer::Renderer::getAudioPendingPlayoutUsByAudioTrackCenter() {
		uint32_t numFramesPlayed;
		numFramesPlayed = getNumFramesPlayedByAudioTrackCenter();
		uint32_t numFramesPendingPlayout = mNumFramesWritten - numFramesPlayed;
		return numFramesPendingPlayout * mAudioSink->msecsPerFrame() * 1000;
}
uint32_t NuPlayer::Renderer::getNumFramesPlayedByAudioTrackCenter() {
			uint32_t numFramesPlayed;
      extern AudioTrackCenter gAudioTrackCenter;
      intptr_t trackId = 0;
      static int64_t lastPlayedUs = 0;
      static int64_t lastNowUs = 0;

      trackId = gAudioTrackCenter.getTrackId(NULL, mAudioSink.get());
      if (trackId) {
          int64_t framePlayed = 0;
          CHECK_EQ(gAudioTrackCenter.getRealTimePosition(trackId, &framePlayed), (status_t)OK);
          if (framePlayed > 0xffffffff) 
              ALOGW("warning!!!, getRealTimePosition framePlayed = %lld", framePlayed);
          numFramesPlayed = (uint32_t)framePlayed;
      } else { 
          CHECK_EQ(mAudioSink->getPosition(&numFramesPlayed), (status_t)OK);
      }

      int64_t nowPlayedUs  = (int64_t)(((int64_t)numFramesPlayed)* mAudioSink->msecsPerFrame()*1000ll);
      int64_t nowUs =  ALooper::GetNowUs();

	  ALOGD("audio played time(%lld us), system time(%lld us),[S-A] (%lld ms),@ mAnchorMediaTimeUs (%lld us),mAnchorRealTimeUs (%lld us)", 
	  nowPlayedUs ,nowUs,((nowUs-lastNowUs) - (nowPlayedUs-lastPlayedUs))/1000ll,
	  mAnchorTimeMediaUs,mAnchorTimeRealUs);

		if (numFramesPlayed > mNumFramesWritten) {
			numFramesPlayed = mNumFramesWritten;
			ALOGW("numFramesPlayed(%dus) > mNumFramesWritten(%dus), reset numFramesPlayed",   
					numFramesPlayed, mNumFramesWritten);
		}

	  if(lastPlayedUs > 0 && lastNowUs >0){   
			  ATRACE_INT64("System-Audio[ms]",((nowUs-lastNowUs) - (nowPlayedUs-lastPlayedUs))/1000ll);
	  }
	  lastPlayedUs= nowPlayedUs;
	  lastNowUs = nowUs;
		
		return numFramesPlayed;

}

int64_t  NuPlayer::Renderer::getPlayedOutAudioDurationUsByAudioTrackCenter() {
	  uint32_t numFramesPlayed;
	  numFramesPlayed = getNumFramesPlayedByAudioTrackCenter();
	  return  (int64_t)(((int64_t)numFramesPlayed)* mAudioSink->msecsPerFrame()*1000ll);


}

bool NuPlayer::Renderer::handleRenderBufferLateInfo(bool tooLate,int64_t realTimeUs,QueueEntry *processBufferEntry){
    // if preformance not ok, show one ,then drop one
    int64_t mediaTimeUs; 
    CHECK(processBufferEntry->mBuffer->meta()->findInt64("timeUs", &mediaTimeUs));
    static int32_t SinceLastDropped = 0;
    static int64_t lastRealTimeUs = -1;
    if(tooLate)
    {
        if (SinceLastDropped > 0)
        {
            //drop
            ALOGE("we're late dropping one timeUs %lld ms after %d frames",mediaTimeUs/1000ll,SinceLastDropped);
            SinceLastDropped = 0;
        }else{
            //not drop
            tooLate = false;
            SinceLastDropped ++;
        }
    }else{
        SinceLastDropped ++;
    }
    if(!sFrameAVsyncByVFS){
	    processBufferEntry->mNotifyConsumed->setInt64("realtimeus", realTimeUs);
	    processBufferEntry->mNotifyConsumed->setInt64("delaytimeus", -mVideoLateByUs);
    }
    processBufferEntry->mNotifyConsumed->setInt64("AvSyncRefTimeUs", ALooper::GetNowUs()-mAnchorTimeRealUs + mAnchorTimeMediaUs);
    ALOGD("[%s buffer] ACodec delay time(%lld us), video mediaTimeUs(%lld us), realtimeUs(%lld us) ,not Render %d", 
		(mVideoLateByUs > 0)?"late":"early",-mVideoLateByUs, mediaTimeUs, realTimeUs,tooLate);
    
   if(lastRealTimeUs > 0){
   	ATRACE_INT64("realTimeDelta",(realTimeUs-lastRealTimeUs)/1000ll);
   }
   lastRealTimeUs = realTimeUs;
   return tooLate;
  
}

void NuPlayer::Renderer::handleForClearMotionPause(bool tooLate,QueueEntry *processBufferEntry){
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_CLEARMOTION_SUPPORT)  
    int32_t isInterpolateFrame = 0;
    if(!processBufferEntry->mBuffer->meta()->findInt32("interpolateframe", &isInterpolateFrame)) {
        ALOGV("no key:interpolateframe in meta");
        isInterpolateFrame = 0;
    }

    if(mPausing) {
        ALOGD("rendering frame when pausing, tooLate(%s), InterpolateFrame(%s)", 
                tooLate?"true":"false", isInterpolateFrame?"true":"false");
    }

    if( !tooLate ) {
        if( isInterpolateFrame )
            mMJCPauseDelay = true;
        else {
            mMJCPauseDelay = false;
            if(mPausing) {
                mPausing = false;
                onPause();
                ALOGI("paused after rendering an uninterpolated frame");
            }
        }
    }
#endif
}

void NuPlayer::Renderer::setFlags(uint32_t flag, bool setting) {
    if(flag & FLAG_HAS_VIDEO_AUDIO) {
	    if (setting) {
            mSyncQueues = true;
            ALOGI("turn on sync queue ");   
	    } else {
            mSyncQueues = false;
            ALOGI("turn off sync queue ");   
	    }
   } 
}

bool NuPlayer::Renderer::audioFormatChange(sp<AMessage> format){

	bool audioSinkChanged = false;
	int32_t numChannels;

	CHECK(format->findInt32("channel-count", &numChannels));

	int32_t channelMask;
	if (!format->findInt32("channel-mask", &channelMask)) {
                // signal to the AudioSink to derive the mask from count.
                channelMask = CHANNEL_MASK_USE_CHANNEL_ORDER;
	}

	int32_t sampleRate;
	CHECK(format->findInt32("sample-rate", &sampleRate));



	int32_t bitWidth = 0;
	audio_format_t audioFormat = AUDIO_FORMAT_PCM_16_BIT;
	if (format->findInt32("bit-width", &bitWidth) && bitWidth > 16) {
                ALOGI("bits width: %d, NuPlayer use high resolution audiotrack.", bitWidth);
                audioFormat = AUDIO_FORMAT_PCM_8_24_BIT;
	}

	if((mAudioSink->getSampleRate() != sampleRate) 
	                ||(mAudioSink->channelCount() != (uint32_t)numChannels) ) {
		ALOGD("samplerate, channelcount differ: %u/%u Hz, %u/%d ch",
		  mAudioSink->getSampleRate(), sampleRate,
		  mAudioSink->channelCount(), numChannels);
		audioSinkChanged = true;
	} 

	return audioSinkChanged;


}

#ifdef MTK_SLOW_MOTION_VIDEO_SUPPORT
status_t NuPlayer::Renderer::setsmspeed(int32_t speed){
	if(speed != mSMSpeed){
		mNeedSync = true;
		if(mPaused){
			mPauseSpeed = speed;
		}else{
			mPauseSpeed = speed;
			mSMSpeed = speed;
		}
	}
	
	if(mAudioSink!=NULL){
		if(mPauseSpeed == 1)
			mAudioSink->setParameters(String8("time_stretch=100"));
		else if(mPauseSpeed == 2)
			mAudioSink->setParameters(String8("time_stretch=200"));
		else if(mPauseSpeed == 4)
			mAudioSink->setParameters(String8("time_stretch=400"));
		else if(mPauseSpeed == 16)
			mAudioSink->setParameters(String8("time_stretch=1600"));

	    extern AudioTrackCenter gAudioTrackCenter;
	    gAudioTrackCenter.setTimeStretch((uint32_t)mPauseSpeed);	
		return OK;
	}else {
		ALOGW("mAudioSink==NULL");
		return NO_INIT;
	}
	
}

void NuPlayer::Renderer::handleRenderBufferForSlowMotion(QueueEntry *processBufferEntry){
	int64_t mediaTimeUs; 
	CHECK(processBufferEntry->mBuffer->meta()->findInt64("timeUs", &mediaTimeUs));
	if((mNeedSync)&& ((!mHasAudio)||(mAudioEOS))){
		mAnchorTimeMediaUs = mediaTimeUs;		
		mAnchorTimeRealUs = ALooper::GetNowUs();
		mNeedSync = false;
		mPreSMSpeed = mSMSpeed;
	}
	if((mSMSpeed >0)&&(!mHasAudio||mAudioEOS)&&(!mNeedSync)){
		mVideoLateByUs = ALooper::GetNowUs()- mAnchorTimeRealUs - (mediaTimeUs - mAnchorTimeMediaUs)/mSMSpeed;
	}
}
#endif
#endif

#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_AUDIO_CHANGE_SUPPORT)  
void NuPlayer::Renderer::changeAudio(){
   mNoJudgeWhenChangeAudio = true; //for ALPS01940787
}
#endif

}  // namespace android

