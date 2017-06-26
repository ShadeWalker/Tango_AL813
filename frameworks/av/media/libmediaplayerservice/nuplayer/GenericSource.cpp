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

//#define LOG_NDEBUG 0
#define LOG_TAG "GenericSource"

#include "GenericSource.h"

#include "AnotherPacketSource.h"

#include <media/IMediaHTTPService.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/FileSource.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaExtractor.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>
#include "../../libstagefright/include/DRMExtractor.h"
#include "../../libstagefright/include/NuCachedSource2.h"
#include "../../libstagefright/include/WVMExtractor.h"
#include "../../libstagefright/include/HTTPBase.h"
#ifdef MTK_AOSP_ENHANCEMENT
#include <ASessionDescription.h>
#ifdef MTK_DRM_APP
#include <drm/DrmMtkUtil.h>
#include <drm/DrmMtkDef.h>
#endif
#endif
#include <media/MtkMMLog.h>
namespace android {

static int64_t kLowWaterMarkUs = 2000000ll;  // 2secs
static int64_t kHighWaterMarkUs = 5000000ll;  // 5secs
static const ssize_t kLowWaterMarkBytes = 40000;
static const ssize_t kHighWaterMarkBytes = 200000;

NuPlayer::GenericSource::GenericSource(
        const sp<AMessage> &notify,
        bool uidValid,
        uid_t uid)
    : Source(notify),
      mAudioTimeUs(0),
      mAudioLastDequeueTimeUs(0),
      mVideoTimeUs(0),
      mVideoLastDequeueTimeUs(0),
      mFetchSubtitleDataGeneration(0),
      mFetchTimedTextDataGeneration(0),
      mDurationUs(0ll),
      mAudioIsVorbis(false),
      mIsWidevine(false),
      mIsSecure(false),
      mIsStreaming(false),
      mUIDValid(uidValid),
      mUID(uid),
      mFd(-1),
#ifdef MTK_MTKPS_PLAYBACK_SUPPORT
      mFDforSniff(-1),
#endif
      mDrmManagerClient(NULL),
      mMetaDataSize(-1ll),
      mBitrate(-1ll),
      mPollBufferingGeneration(0),
      mPendingReadBufferTypes(0),
      mBuffering(false),
#ifdef MTK_AOSP_ENHANCEMENT
	  mAudioIsRaw(false),
#endif
      mPrepareBuffering(false) {
    resetDataSource();
    DataSource::RegisterDefaultSniffers();
#ifdef MTK_AOSP_ENHANCEMENT
    mInitCheck = OK;
    mSeekTimeUs = -1;
    mSDPFormatMeta = new MetaData;
    mIsRequiresSecureBuffer= false;
    mCacheErrorNotify = true;
    mLastNotifyPercent = 0;
     mTSbuffering=false;                   // for ts
    mSeekingCount = 0;
#endif
}

void NuPlayer::GenericSource::resetDataSource() {
    mHTTPService.clear();
    mHttpSource.clear();
    mUri.clear();
    mUriHeaders.clear();
    if (mFd >= 0) {
        close(mFd);
        mFd = -1;
    }
    mOffset = 0;
    mLength = 0;
    setDrmPlaybackStatusIfNeeded(Playback::STOP, 0);
    mDecryptHandle = NULL;
    mDrmManagerClient = NULL;
    mStarted = false;
    mStopRead = true;
}

status_t NuPlayer::GenericSource::setDataSource(
        const sp<IMediaHTTPService> &httpService,
        const char *url,
        const KeyedVector<String8, String8> *headers) {
    resetDataSource();

    mHTTPService = httpService;
    mUri = url;

    if (headers) {
        mUriHeaders = *headers;
    }

    // delay data source creation to prepareAsync() to avoid blocking
    // the calling thread in setDataSource for any significant time.
    return OK;
}

status_t NuPlayer::GenericSource::setDataSource(
        int fd, int64_t offset, int64_t length) {
    resetDataSource();

#ifdef MTK_AOSP_ENHANCEMENT
		mInitCheck = OK;
#endif
    mFd = dup(fd);
    mOffset = offset;
    mLength = length;

    // delay data source creation to prepareAsync() to avoid blocking
    // the calling thread in setDataSource for any significant time.
    return OK;
}

sp<MetaData> NuPlayer::GenericSource::getFileFormatMeta() const {
    return mFileMeta;
}

status_t NuPlayer::GenericSource::initFromDataSource() {
    sp<MediaExtractor> extractor;
    String8 mimeType;
    float confidence;
    sp<AMessage> dummy;
    bool isWidevineStreaming = false;    

    CHECK(mDataSource != NULL);

    if (mIsWidevine) {
        isWidevineStreaming = SniffWVM(
                mDataSource, &mimeType, &confidence, &dummy);
        if(!isWidevineStreaming ||
               strcasecmp(
                    mimeType.string(), MEDIA_MIMETYPE_CONTAINER_WVM)) {
            ALOGE("unsupported widevine mime: %s", mimeType.string());
            return UNKNOWN_ERROR;
        }
    } else if (mIsStreaming) {
        if (mSniffedMIME.empty()) {
            if (!mDataSource->sniff(&mimeType, &confidence, &dummy)) {
                return UNKNOWN_ERROR;
            }
            mSniffedMIME = mimeType.string();
        }
        isWidevineStreaming = !strcasecmp(
                mSniffedMIME.c_str(), MEDIA_MIMETYPE_CONTAINER_WVM);
    }

    if (isWidevineStreaming) {
        // we don't want cached source for widevine streaming.
        mCachedSource.clear();
        mDataSource = mHttpSource;
        mWVMExtractor = new WVMExtractor(mDataSource);
        mWVMExtractor->setAdaptiveStreamingMode(true);
        if (mUIDValid) {
            mWVMExtractor->setUID(mUID);
        }
        extractor = mWVMExtractor;
    } else {
#ifdef MTK_MTKPS_PLAYBACK_SUPPORT
        if (mSniffedMIME.empty()) {
            String8 tmp;
            if (mDataSource->fastsniff(mFDforSniff, &tmp)) {
                extractor = MediaExtractor::Create(mDataSource, tmp.string());
            }
            else {
                extractor = MediaExtractor::Create(mDataSource, NULL);
            }
            mFDforSniff = -1;
        }
        else {
            extractor = MediaExtractor::Create(mDataSource, mSniffedMIME.c_str());
        }
#else
        extractor = MediaExtractor::Create(mDataSource,
                mSniffedMIME.empty() ? NULL: mSniffedMIME.c_str());
#endif
    }

    if (extractor == NULL) {
#ifdef MTK_AOSP_ENHANCEMENT
           ALOGE("initFromDataSource,can't create extractor!");
           mInitCheck = ERROR_UNSUPPORTED;
           if (mCachedSource != NULL) {
               status_t cache_stat = mCachedSource->getRealFinalStatus();
               bool bCacheSuccess = (cache_stat == OK || cache_stat == ERROR_END_OF_STREAM);
               if (!bCacheSuccess) {
                   return ERROR_CANNOT_CONNECT;
               }
           }
#endif
        return UNKNOWN_ERROR;
    }

#if defined (MTK_AOSP_ENHANCEMENT) && defined (MTK_DRM_APP)
    bool drmFlag = extractor->getDrmFlag();
    int32_t isDrm = 0;
    if (drmFlag == true) {
        checkDrmStatus(mDataSource);
    }
    if (mMetaData != NULL) {
		if (mMetaData->findInt32(kKeyIsDRM, &isDrm)) {
			ALOGD("mMetaData->findInt32(kKeyIsDRM, &isDrm) scuess, isDrm is %d", isDrm);
		} else {
		    ALOGD("mMetaData->findInt32(kKeyIsDRM, &isDrm) fail, then set is %d", drmFlag);
			mMetaData->setInt32(kKeyIsDRM, drmFlag);
		}
    }
#else
    if (extractor->getDrmFlag()) {
        checkDrmStatus(mDataSource);
    }
#endif

#ifdef MTK_AOSP_ENHANCEMENT   
	status_t err = initFromDataSource_checkLocalSdp(extractor)  ;
	if(err == OK) return OK;
	if(err == ERROR_UNSUPPORTED) return err;
	//else, not sdp, should continue to do the following work 
#endif
    mFileMeta = extractor->getMetaData();
    if (mFileMeta != NULL) {
        int64_t duration;
        if (mFileMeta->findInt64(kKeyDuration, &duration)) {
            mDurationUs = duration;
        }
#ifdef MTK_AOSP_ENHANCEMENT
		const char *formatMime;
		if (mFileMeta->findCString(kKeyMIMEType, &formatMime)) {
		    if (!strcasecmp(formatMime, MEDIA_MIMETYPE_CONTAINER_AVI)) {
			    extractor->finishParsing(); // avi create seektable
		    }
		}
       // mFileMeta = fileMeta;
#endif

        if (!mIsWidevine) {
            // Check mime to see if we actually have a widevine source.
            // If the data source is not URL-type (eg. file source), we
            // won't be able to tell until now.
            const char *fileMime;
            if (mFileMeta->findCString(kKeyMIMEType, &fileMime)
                    && !strncasecmp(fileMime, "video/wvm", 9)) {
                mIsWidevine = true;
            }
        }
    }

    int32_t totalBitrate = 0;

    size_t numtracks = extractor->countTracks();
    if (numtracks == 0) {
        return UNKNOWN_ERROR;
    }

    for (size_t i = 0; i < numtracks; ++i) {
#ifdef MTK_AOSP_ENHANCEMENT		
		sp<MetaData> trackMeta = extractor->getTrackMetaData(i,MediaExtractor::kIncludeInterleaveInfo);
		callback_t cb = (callback_t)updateAudioDuration;
		trackMeta->setPointer(kKeyDataSourceObserver, this);
		trackMeta->setPointer(kKeyUpdateDuraCallback, (void *)cb);
		trackMeta->setInt32(kKeyIsMtkMusic, isMtkMusic);
#endif
        sp<MediaSource> track = extractor->getTrack(i);
        if (track == NULL) {
            continue;
        }

        sp<MetaData> meta = extractor->getTrackMetaData(i);

        const char *mime;
        CHECK(meta->findCString(kKeyMIMEType, &mime));

        // Do the string compare immediately with "mime",
        // we can't assume "mime" would stay valid after another
        // extractor operation, some extractors might modify meta
        // during getTrack() and make it invalid.
        if (!strncasecmp(mime, "audio/", 6)) {
            if (mAudioTrack.mSource == NULL) {
                mAudioTrack.mIndex = i;
                mAudioTrack.mSource = track;
                mAudioTrack.mPackets =
                    new AnotherPacketSource(mAudioTrack.mSource->getFormat());
#ifdef MTK_AOSP_ENHANCEMENT
                mAudioTrack.isEOS = false;
                if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_RAW)) {
                    mAudioIsRaw = true;
                } else {
                    mAudioIsRaw = false;
                }
#endif

                if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_VORBIS)) {
                    mAudioIsVorbis = true;
                } else {
                    mAudioIsVorbis = false;
                }
            }
        } else if (!strncasecmp(mime, "video/", 6)) {
            if (mVideoTrack.mSource == NULL) {
                mVideoTrack.mIndex = i;
                mVideoTrack.mSource = track;
                mVideoTrack.mPackets =
                    new AnotherPacketSource(mVideoTrack.mSource->getFormat());

#ifdef MTK_AOSP_ENHANCEMENT
                mVideoTrack.isEOS = false;
#endif
                // check if the source requires secure buffers
                int32_t secure;
                if (meta->findInt32(kKeyRequiresSecureBuffers, &secure)
                        && secure) {
                    mIsSecure = true;
                    if (mUIDValid) {
                        extractor->setUID(mUID);
                    }
                }
            }
        }

        mSources.push(track);
        int64_t durationUs;
        if (meta->findInt64(kKeyDuration, &durationUs)) {
            if (durationUs > mDurationUs) {
                mDurationUs = durationUs;
            }
        }

        int32_t bitrate;
        if (totalBitrate >= 0 && meta->findInt32(kKeyBitRate, &bitrate)) {
            totalBitrate += bitrate;
        } else {
            totalBitrate = -1;
        }
    }

    if (mSources.size() == 0) {
        ALOGE("b/23705695");
        return UNKNOWN_ERROR;
    }

    mBitrate = totalBitrate;

#ifdef MTK_AOSP_ENHANCEMENT
	if (mVideoTrack.mSource == NULL && mAudioTrack.mSource == NULL) {
		// report unsupport video to MediaPlayerService
		return ERROR_UNSUPPORTED;
	}
#endif
    return OK;
}

status_t NuPlayer::GenericSource::startSources() {
    // Start the selected A/V tracks now before we start buffering.
    // Widevine sources might re-initialize crypto when starting, if we delay
    // this to start(), all data buffered during prepare would be wasted.
    // (We don't actually start reading until start().)
    if (mAudioTrack.mSource != NULL && mAudioTrack.mSource->start() != OK) {
        ALOGE("failed to start audio track!");
        return UNKNOWN_ERROR;
    }

    if (mVideoTrack.mSource != NULL && mVideoTrack.mSource->start() != OK) {
        ALOGE("failed to start video track!");
        return UNKNOWN_ERROR;
    }

    return OK;
}

void NuPlayer::GenericSource::checkDrmStatus(const sp<DataSource>& dataSource) {
    dataSource->getDrmInfo(mDecryptHandle, &mDrmManagerClient);
    if (mDecryptHandle != NULL) {
        CHECK(mDrmManagerClient);
        if (RightsStatus::RIGHTS_VALID != mDecryptHandle->status) {
            sp<AMessage> msg = dupNotify();
            msg->setInt32("what", kWhatDrmNoLicense);
            msg->post();
        }
    }
}

int64_t NuPlayer::GenericSource::getLastReadPosition() {
    if (mAudioTrack.mSource != NULL) {
        return mAudioTimeUs;
    } else if (mVideoTrack.mSource != NULL) {
        return mVideoTimeUs;
    } else {
        return 0;
    }
}

status_t NuPlayer::GenericSource::setBuffers(
        bool audio, Vector<MediaBuffer *> &buffers) {
    if (mIsSecure && !audio && mVideoTrack.mSource != NULL) {
        return mVideoTrack.mSource->setBuffers(buffers);
    }
    return INVALID_OPERATION;
}

NuPlayer::GenericSource::~GenericSource() {
    if (mLooper != NULL) {
        mLooper->unregisterHandler(id());
        mLooper->stop();
    }
    resetDataSource();
}

void NuPlayer::GenericSource::prepareAsync() {
    if (mLooper == NULL) {
        mLooper = new ALooper;
        mLooper->setName("generic");
        mLooper->start();

        mLooper->registerHandler(this);
    }

    sp<AMessage> msg = new AMessage(kWhatPrepareAsync, id());
    msg->post();
}

void NuPlayer::GenericSource::onPrepareAsync() {
    // delayed data source creation
    if (mDataSource == NULL) {
        // set to false first, if the extractor
        // comes back as secure, set it to true then.
        mIsSecure = false;

        if (!mUri.empty()) {
            const char* uri = mUri.c_str();
            mIsWidevine = !strncasecmp(uri, "widevine://", 11);

            if (!strncasecmp("http://", uri, 7)
                    || !strncasecmp("https://", uri, 8)
                    || mIsWidevine) {
                mHttpSource = DataSource::CreateMediaHTTP(mHTTPService);
                if (mHttpSource == NULL) {
                    ALOGE("Failed to create http source!");
                    notifyPreparedAndCleanup(UNKNOWN_ERROR);
                    return;
                }
            }

            mDataSource = DataSource::CreateFromURI(
                   mHTTPService, uri, &mUriHeaders, &mContentType,
                   static_cast<HTTPBase *>(mHttpSource.get()));
        } else {
            mIsWidevine = false;

            mDataSource = new FileSource(mFd, mOffset, mLength);
        #ifdef MTK_MTKPS_PLAYBACK_SUPPORT
            mFDforSniff = mFd;
        #endif
            mFd = -1;
        }

        if (mDataSource == NULL) {
            ALOGE("Failed to create data source!");
            notifyPreparedAndCleanup(UNKNOWN_ERROR);
            return;
        }

        if (mDataSource->flags() & DataSource::kIsCachingDataSource) {
            mCachedSource = static_cast<NuCachedSource2 *>(mDataSource.get());
        }

        // For widevine or other cached streaming cases, we need to wait for
        // enough buffering before reporting prepared.
        // Note that even when URL doesn't start with widevine://, mIsWidevine
        // could still be set to true later, if the streaming or file source
        // is sniffed to be widevine. We don't want to buffer for file source
        // in that case, so must check the flag now.
        mIsStreaming = (mIsWidevine || mCachedSource != NULL);
    }

    // check initial caching status
    status_t err = prefillCacheIfNecessary();
    if (err != OK) {
        if (err == -EAGAIN) {
            (new AMessage(kWhatPrepareAsync, id()))->post(200000);
        } else {
            ALOGE("Failed to prefill data cache!");
            notifyPreparedAndCleanup(UNKNOWN_ERROR);
        }
        return;
    }

    // init extrator from data source
    err = initFromDataSource();

    if (err != OK) {
        ALOGE("Failed to init from data source!");
        notifyPreparedAndCleanup(err);
        return;
    }

    if (mVideoTrack.mSource != NULL) {
        sp<MetaData> meta = doGetFormatMeta(false /* audio */);
        sp<AMessage> msg = new AMessage;
        err = convertMetaDataToMessage(meta, &msg);
        if(err != OK) {
            notifyPreparedAndCleanup(err);
            return;
        }
        notifyVideoSizeChanged(msg);
    }
#ifdef MTK_AOSP_ENHANCEMENT
    else {
        const char *mime;
        if (mFileMeta != NULL && mFileMeta->findCString(kKeyMIMEType, &mime)) {
            // streaming notify size 0 when playing in Gallery. It should check foramt,
            // but not video or audio. e.g. audio/3gpp would played in Gallery2 due to 
            // format is 3ggp.
            if (mCachedSource != NULL && (!strcasecmp(mime+6, "mp4") || 
                        !strcasecmp(mime+6, "quicktime") ||
                        !strcasecmp(mime+6, "3gpp") ||
                        !strcasecmp(mime+6, "mp2ts") ||
                        !strcasecmp(mime+6, "webm") ||
                        !strcasecmp(mime+6, "x-matroska") ||
                        !strcasecmp(mime+6, "avi") ||
                        !strcasecmp(mime+6, "x-flv") ||
                        !strcasecmp(mime+6, "asfff") ||
                        !strcasecmp(mime+6, "x-ms-wmv") ||
                        !strcasecmp(mime+6, "x-ms-wma"))) { 
                ALOGI("streaming notify size 0 when no video :%s", mime);
                notifyVideoSizeChanged(NULL);
            }
        }
    }
#ifdef MTK_DRM_APP
	// OMA DRM v1 implementation: consume rights.
	mIsCurrentComplete = false;
	if (mDecryptHandle != NULL) {
		ALOGD("NuPlayer, consumeRights @prepare_l()");
		// in some cases, the mFileSource may be NULL (E.g. play audio directly in File Manager)
		// We don't know, but we assume it's a OMA DRM v1 case (DecryptApiType::CONTAINER_BASED)
		if (((mDataSource->flags() & OMADrmFlag) != 0)
			|| (DecryptApiType::CONTAINER_BASED == mDecryptHandle->decryptApiType)) {
			if (!DrmMtkUtil::isTrustedVideoClient(mDrmValue)) {
				mDrmManagerClient->consumeRights(mDecryptHandle, 0x01, false);
			}
		}
	}
#endif
#ifdef  MTK_PLAYREADY_SUPPORT
    int32_t isPlayReady = 0;
    if (mFileMeta != NULL && mFileMeta->findInt32(kKeyIsPlayReady, &isPlayReady) && isPlayReady) {
        checkDrmStatus(mDataSource);
        if (mDecryptHandle != NULL) {
            ALOGD("PlayReady, consumeRights @prepare_l()");
            status_t err = mDrmManagerClient->consumeRights(mDecryptHandle, 0x01, false);
            if (err != OK) {
                ALOGE("consumeRights faile err:%d", err);
            }
        }
    }
#endif
#endif // #ifdef MTK_AOSP_ENHANCEMENT
    notifyFlagsChanged(
            (mIsSecure ? FLAG_SECURE : 0)
            | (mDecryptHandle != NULL ? FLAG_PROTECTED : 0)
            | FLAG_CAN_PAUSE
            | FLAG_CAN_SEEK_BACKWARD
            | FLAG_CAN_SEEK_FORWARD
            | FLAG_CAN_SEEK);

    if(mIsSecure) {
        // secure decoders must be instantiated before starting widevine source
        sp<AMessage> reply = new AMessage(kWhatSecureDecodersInstantiated, id());
        notifyInstantiateSecureDecoders(reply);
    } else {
        finishPrepareAsync();
    }
#ifdef MTK_AOSP_ENHANCEMENT
    if (mCachedSource != NULL) {
        // asf file, should read from begin. Or else, the cache is in the end of file.
        const char *mime;			
        if (mFileMeta != NULL && mFileMeta->findCString(kKeyMIMEType, &mime) &&
                !strcasecmp(MEDIA_MIMETYPE_VIDEO_WMV, mime)) {
            ALOGI("ASF Streaming change cache to the begining of file");
            int8_t data[1];
            mCachedSource->readAt(0, (void *)data, 1);
        }
        return;
    }
#endif
}

void NuPlayer::GenericSource::onSecureDecodersInstantiated(status_t err) {
    if (err != OK) {
        ALOGE("Failed to instantiate secure decoders!");
        notifyPreparedAndCleanup(err);
        return;
    }
    finishPrepareAsync();
}

void NuPlayer::GenericSource::finishPrepareAsync() {
    status_t err = startSources();
    if (err != OK){
        ALOGE("Failed to init start data source!");
        notifyPreparedAndCleanup(err);
        return;
    }

    if (mIsStreaming) {
        mPrepareBuffering = true;

        ensureCacheIsFetching();
        restartPollBuffering();
    } else {
        notifyPrepared();
    }
}


void NuPlayer::GenericSource::notifyPreparedAndCleanup(status_t err) {
    if (err != OK) {
        mMetaDataSize = -1ll;
        mContentType = "";
        mSniffedMIME = "";
#ifdef MTK_AOSP_ENHANCEMENT
        // disconnect must. if not do disconnect, network would problem. Ask Ryan.yu
        MM_LOGI("err:%d, then disconnect lock", err);
        disconnect(); 
#endif
        {
            sp<DataSource> dataSource = mDataSource;
            sp<NuCachedSource2> cachedSource = mCachedSource;
            sp<DataSource> httpSource = mHttpSource;
            {
                Mutex::Autolock _l(mDisconnectLock);
                mDataSource.clear();
                mDecryptHandle = NULL;
                mDrmManagerClient = NULL;
                mCachedSource.clear();
                mHttpSource.clear();
                mBitrate = -1;
            }
        }

        cancelPollBuffering();
    }
    notifyPrepared(err);
}

status_t NuPlayer::GenericSource::prefillCacheIfNecessary() {
    CHECK(mDataSource != NULL);

    if (mCachedSource == NULL) {
        // no prefill if the data source is not cached
        return OK;
    }

    // We're not doing this for streams that appear to be audio-only
    // streams to ensure that even low bandwidth streams start
    // playing back fairly instantly.
    if (!strncasecmp(mContentType.string(), "audio/", 6)) {
        return OK;
    }

    // We're going to prefill the cache before trying to instantiate
    // the extractor below, as the latter is an operation that otherwise
    // could block on the datasource for a significant amount of time.
    // During that time we'd be unable to abort the preparation phase
    // without this prefill.

    // Initially make sure we have at least 192 KB for the sniff
    // to complete without blocking.
    static const size_t kMinBytesForSniffing = 192 * 1024;
    static const size_t kDefaultMetaSize = 200000;

    status_t finalStatus;

    size_t cachedDataRemaining =
            mCachedSource->approxDataRemaining(&finalStatus);

    if (finalStatus != OK || (mMetaDataSize >= 0
            && (off64_t)cachedDataRemaining >= mMetaDataSize)) {
        ALOGV("stop caching, status %d, "
                "metaDataSize %lld, cachedDataRemaining %zu",
                finalStatus, mMetaDataSize, cachedDataRemaining);
        return OK;
    }

    ALOGV("now cached %zu bytes of data", cachedDataRemaining);

    if (mMetaDataSize < 0
            && cachedDataRemaining >= kMinBytesForSniffing) {
        String8 tmp;
        float confidence;
        sp<AMessage> meta;
        if (!mCachedSource->sniff(&tmp, &confidence, &meta)) {
            return UNKNOWN_ERROR;
        }

        // We successfully identified the file's extractor to
        // be, remember this mime type so we don't have to
        // sniff it again when we call MediaExtractor::Create()
        mSniffedMIME = tmp.string();

        if (meta == NULL
                || !meta->findInt64("meta-data-size",
                        reinterpret_cast<int64_t*>(&mMetaDataSize))) {
            mMetaDataSize = kDefaultMetaSize;
        }
#ifdef MTK_AOSP_ENHANCEMENT
        // maybe mMetaDataSize is the whole file, if moov box in the end of file,
        if(mMetaDataSize > kDefaultMetaSize){
            mMetaDataSize = kDefaultMetaSize;
        }
#endif

        if (mMetaDataSize < 0ll) {
            ALOGE("invalid metaDataSize = %lld bytes", mMetaDataSize);
            return UNKNOWN_ERROR;
        }
    }

    return -EAGAIN;
}

void NuPlayer::GenericSource::start() {
    ALOGI("start");
#ifdef MTK_AOSP_ENHANCEMENT
       mTSbuffering= true;  //cherry for ts
#endif
    mStopRead = false;
    if (mAudioTrack.mSource != NULL) {
        postReadBuffer(MEDIA_TRACK_TYPE_AUDIO);
    }

    if (mVideoTrack.mSource != NULL) {
        postReadBuffer(MEDIA_TRACK_TYPE_VIDEO);
    }

    setDrmPlaybackStatusIfNeeded(Playback::START, getLastReadPosition() / 1000);
    mStarted = true;
#ifdef MTK_AOSP_ENHANCEMENT
#ifdef MTK_DRM_APP
	// OMA DRM v1 implementation, when the playback is done and position comes to 0, consume rights.
	if (mIsCurrentComplete) { // single recursive mode
		ALOGD("NuPlayer, consumeRights @play_l()");
		// in some cases, the mFileSource may be NULL (E.g. play audio directly in File Manager)
		// We don't know, but we assume it's a OMA DRM v1 case (DecryptApiType::CONTAINER_BASED)
		if (((mDataSource->flags() & OMADrmFlag) != 0) 
			|| (DecryptApiType::CONTAINER_BASED == mDecryptHandle->decryptApiType)) {
			if (!DrmMtkUtil::isTrustedVideoClient(mDrmValue)) {
				mDrmManagerClient->consumeRights(mDecryptHandle, 0x10, false);
			}
		}
		mIsCurrentComplete = false;
	}
#endif
#endif // #ifdef MTK_AOSP_ENHANCEMENT

    (new AMessage(kWhatStart, id()))->post();
}

void NuPlayer::GenericSource::stop() {
    // nothing to do, just account for DRM playback status
    setDrmPlaybackStatusIfNeeded(Playback::STOP, 0);
    mStarted = false;
    if (mIsWidevine || mIsSecure) {
        // For widevine or secure sources we need to prevent any further reads.
        sp<AMessage> msg = new AMessage(kWhatStopWidevine, id());
        sp<AMessage> response;
        (void) msg->postAndAwaitResponse(&response);
    }
#ifdef MTK_AOSP_ENHANCEMENT
#ifdef MTK_DRM_APP
	mIsCurrentComplete = true;
#endif
#endif
}

void NuPlayer::GenericSource::pause() {
    // nothing to do, just account for DRM playback status
    setDrmPlaybackStatusIfNeeded(Playback::PAUSE, 0);
    mStarted = false;
}

void NuPlayer::GenericSource::resume() {
    // nothing to do, just account for DRM playback status
    setDrmPlaybackStatusIfNeeded(Playback::START, getLastReadPosition() / 1000);
    mStarted = true;

    (new AMessage(kWhatResume, id()))->post();
}

void NuPlayer::GenericSource::disconnect() {
    sp<DataSource> dataSource, httpSource;
    {
        Mutex::Autolock _l(mDisconnectLock);
	dataSource = mDataSource;
	httpSource = mHttpSource;
    }

    if (dataSource != NULL) {
        // disconnect data source
	if (dataSource->flags() & DataSource::kIsCachingDataSource) {
	    static_cast<NuCachedSource2 *>(dataSource.get())->disconnect();
        }
    } else if (httpSource != NULL) {
	static_cast<HTTPBase *>(httpSource.get())->disconnect();
    }
}

void NuPlayer::GenericSource::setDrmPlaybackStatusIfNeeded(int playbackStatus, int64_t position) {
    if (mDecryptHandle != NULL) {
        mDrmManagerClient->setPlaybackStatus(mDecryptHandle, playbackStatus, position);
    }
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_SUBTITLE_SUPPORT)
#else
    mSubtitleTrack.mPackets = new AnotherPacketSource(NULL);
    mTimedTextTrack.mPackets = new AnotherPacketSource(NULL);
#endif
}

status_t NuPlayer::GenericSource::feedMoreTSData() {
    return OK;
}

void NuPlayer::GenericSource::schedulePollBuffering() {
    sp<AMessage> msg = new AMessage(kWhatPollBuffering, id());
    msg->setInt32("generation", mPollBufferingGeneration);
    msg->post(1000000ll);
}

void NuPlayer::GenericSource::cancelPollBuffering() {
#ifndef MTK_AOSP_ENHANCEMENT
    // should not set mBuffering false, or else would cause buffering all the time
    mBuffering = false;
#endif
    ++mPollBufferingGeneration;
}

void NuPlayer::GenericSource::restartPollBuffering() {
    if (mIsStreaming) {
        cancelPollBuffering();
        onPollBuffering();
    }
}

void NuPlayer::GenericSource::notifyBufferingUpdate(int percentage) {
    ALOGV("notifyBufferingUpdate: buffering %d%%", percentage);

#ifdef MTK_AOSP_ENHANCEMENT
    mLastNotifyPercent = percentage;
#endif
    sp<AMessage> msg = dupNotify();
    msg->setInt32("what", kWhatBufferingUpdate);
    msg->setInt32("percentage", percentage);
    msg->post();
}

void NuPlayer::GenericSource::startBufferingIfNecessary() {
    ALOGV("startBufferingIfNecessary: mPrepareBuffering=%d, mBuffering=%d",
            mPrepareBuffering, mBuffering);

    if (mPrepareBuffering) {
        return;
    }

#ifdef MTK_AOSP_ENHANCEMENT
    Mutex::Autolock _l(mBufferingLock);
#endif
    if (!mBuffering) {
        mBuffering = true;
#ifdef MTK_AOSP_ENHANCEMENT
        mBufferingLock.unlock();
#endif

        ensureCacheIsFetching();
        sendCacheStats();

        sp<AMessage> notify = dupNotify();
        notify->setInt32("what", kWhatPauseOnBufferingStart);
        notify->post();
#ifdef MTK_AOSP_ENHANCEMENT
        mBufferingLock.lock();
#endif
    }
}

void NuPlayer::GenericSource::stopBufferingIfNecessary() {
    ALOGV("stopBufferingIfNecessary: mPrepareBuffering=%d, mBuffering=%d",
            mPrepareBuffering, mBuffering);

    if (mPrepareBuffering) {
        mPrepareBuffering = false;
        notifyPrepared();
        return;
    }

#ifdef MTK_AOSP_ENHANCEMENT
    // more thread call the function, due to dequeueAccessUnit call onPolling()
    Mutex::Autolock _l(mBufferingLock);
#endif
    if (mBuffering) {
        mBuffering = false;
#ifdef MTK_AOSP_ENHANCEMENT
        mBufferingLock.unlock();
#endif

        sendCacheStats();

        sp<AMessage> notify = dupNotify();
        notify->setInt32("what", kWhatResumeOnBufferingEnd);
        notify->post();
#ifdef MTK_AOSP_ENHANCEMENT
        mBufferingLock.lock();
#endif
    }
}

void NuPlayer::GenericSource::sendCacheStats() {
    int32_t kbps = 0;
    status_t err = UNKNOWN_ERROR;

    if (mWVMExtractor != NULL) {
        err = mWVMExtractor->getEstimatedBandwidthKbps(&kbps);
    } else if (mCachedSource != NULL) {
        err = mCachedSource->getEstimatedBandwidthKbps(&kbps);
    }

    if (err == OK) {
        sp<AMessage> notify = dupNotify();
        notify->setInt32("what", kWhatCacheStats);
        notify->setInt32("bandwidth", kbps);
        notify->post();
    }
}

void NuPlayer::GenericSource::ensureCacheIsFetching() {
    if (mCachedSource != NULL) {
        mCachedSource->resumeFetchingIfNecessary();
    }
}
#ifdef MTK_AOSP_ENHANCEMENT
void NuPlayer::GenericSource::onPollBuffering(bool shouldNotify) {
#else
void NuPlayer::GenericSource::onPollBuffering() {
#endif
    status_t finalStatus = UNKNOWN_ERROR;
    int64_t cachedDurationUs = -1ll;
    ssize_t cachedDataRemaining = -1;

#ifdef MTK_AOSP_ENHANCEMENT
    int64_t highWaterMarkUs = kHighWaterMarkUs;
#endif
    ALOGW_IF(mWVMExtractor != NULL && mCachedSource != NULL,
            "WVMExtractor and NuCachedSource both present");

    if (mWVMExtractor != NULL) {
        cachedDurationUs =
                mWVMExtractor->getCachedDurationUs(&finalStatus);
    } else if (mCachedSource != NULL) {
        cachedDataRemaining =
                mCachedSource->approxDataRemaining(&finalStatus);

        if (finalStatus == OK) {
            off64_t size;
            int64_t bitrate = 0ll;
            if (mDurationUs > 0 && mCachedSource->getSize(&size) == OK) {
                bitrate = size * 8000000ll / mDurationUs;
            } else if (mBitrate > 0) {
                bitrate = mBitrate;
            }
            if (bitrate > 0) {
                cachedDurationUs = cachedDataRemaining * 8000000ll / bitrate;
#ifdef MTK_AOSP_ENHANCEMENT
                // handle high high bitrate case, max cache duration would smaller than highWaterMarkUs
                int64_t nMaxCacheDuration = mCachedSource->getMaxCacheSize() * 8000000ll / bitrate;
                if (nMaxCacheDuration < highWaterMarkUs) {
                    ALOGI("highwatermark = %lld, cache maxduration = %lld", highWaterMarkUs, nMaxCacheDuration);
                    highWaterMarkUs = nMaxCacheDuration;
                }
#endif
            }
        }
    }

    if (finalStatus != OK) {
        ALOGV("onPollBuffering: EOS (finalStatus = %d)", finalStatus);

#ifdef MTK_AOSP_ENHANCEMENT
        // notify error to mtk app, and notify once, or else toast too many error
        if ((finalStatus != ERROR_END_OF_STREAM) && mCacheErrorNotify) {
            ALOGD("Notify once, onBufferingUpdateCachedSource_l, finalStatus=%d", finalStatus);
            mCacheErrorNotify = false;
            sp<AMessage> msg = dupNotify();
            msg->setInt32("what", kWhatSourceError);
            msg->setInt32("err", finalStatus);
            msg->post();
        }
#endif
        if (finalStatus == ERROR_END_OF_STREAM) {
#ifdef MTK_AOSP_ENHANCEMENT
            // do not notify 100 all the time, when play complete. It would cause browser fault:ALPS01839862
            if (mAudioTrack.isEOS && mVideoTrack.isEOS && mLastNotifyPercent == 100) {
                ALOGI("do not notify 100 again");
                cancelPollBuffering();
                return;
            } else {
                if (shouldNotify)
                    notifyBufferingUpdate(100);
            }
#else
            notifyBufferingUpdate(100);
#endif
        }

        stopBufferingIfNecessary();
        return;
    } else if (cachedDurationUs >= 0ll) {
        if (mDurationUs > 0ll) {
            int64_t cachedPosUs = getLastReadPosition() + cachedDurationUs;
            int percentage = 100.0 * cachedPosUs / mDurationUs;
            if (percentage > 100) {
                percentage = 100;
            }

#ifdef MTK_AOSP_ENHANCEMENT
            if (shouldNotify)
#endif
            notifyBufferingUpdate(percentage);
        }

        ALOGV("onPollBuffering: cachedDurationUs %.1f sec",
                cachedDurationUs / 1000000.0f);

        if (cachedDurationUs < kLowWaterMarkUs) {
            startBufferingIfNecessary();
#ifdef MTK_AOSP_ENHANCEMENT
        } else if (cachedDurationUs > highWaterMarkUs) {
            stopBufferingIfNecessary();
        }
#else
        } else if (cachedDurationUs > kHighWaterMarkUs) {
            stopBufferingIfNecessary();
        }
#endif
    } else if (cachedDataRemaining >= 0) {
        ALOGV("onPollBuffering: cachedDataRemaining %d bytes",
                cachedDataRemaining);

        if (cachedDataRemaining < kLowWaterMarkBytes) {
            startBufferingIfNecessary();
        } else if (cachedDataRemaining > kHighWaterMarkBytes) {
            stopBufferingIfNecessary();
        }
    }

#ifdef MTK_AOSP_ENHANCEMENT
    if (shouldNotify)
#endif
    schedulePollBuffering();
}

#ifdef MTK_AOSP_ENHANCEMENT
//static
void NuPlayer::GenericSource::updateAudioDuration(void *observer, int64_t durationUs) {

	NuPlayer::GenericSource *me = (NuPlayer::GenericSource *)observer;
	me->notifyDurationUpdate(durationUs);
}

void NuPlayer::GenericSource::notifyDurationUpdate(int64_t durationUs) {

	sp<AMessage> msg = dupNotify();
    msg->setInt32("what", kWhatDurationUpdate);
    msg->setInt64("durationUs", durationUs);
    msg->post();
}
#endif

void NuPlayer::GenericSource::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
      case kWhatPrepareAsync:
      {
          onPrepareAsync();
          break;
      }
      case kWhatFetchSubtitleData:
      {
          fetchTextData(kWhatSendSubtitleData, MEDIA_TRACK_TYPE_SUBTITLE,
                  mFetchSubtitleDataGeneration, mSubtitleTrack.mPackets, msg);
          break;
      }

      case kWhatFetchTimedTextData:
      {
          fetchTextData(kWhatSendTimedTextData, MEDIA_TRACK_TYPE_TIMEDTEXT,
                  mFetchTimedTextDataGeneration, mTimedTextTrack.mPackets, msg);
          break;
      }

      case kWhatSendSubtitleData:
      {
          sendTextData(kWhatSubtitleData, MEDIA_TRACK_TYPE_SUBTITLE,
                  mFetchSubtitleDataGeneration, mSubtitleTrack.mPackets, msg);
          break;
      }

      case kWhatSendTimedTextData:
      {
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_SUBTITLE_SUPPORT)  
          sendTextData2(kWhatTimedTextData2, MEDIA_TRACK_TYPE_TIMEDTEXT,
                  mFetchTimedTextDataGeneration, mTimedTextTrack.mPackets, msg);
#else
          sendTextData(kWhatTimedTextData, MEDIA_TRACK_TYPE_TIMEDTEXT,
                  mFetchTimedTextDataGeneration, mTimedTextTrack.mPackets, msg);
#endif
          break;
      }

      case kWhatChangeAVSource:
      {
          int32_t trackIndex;
          CHECK(msg->findInt32("trackIndex", &trackIndex));
          const sp<MediaSource> source = mSources.itemAt(trackIndex);
          MM_LOGI("[select track]SelectTrack index:%d",trackIndex);
          Track* track;
          const char *mime;
          media_track_type trackType, counterpartType;
          sp<MetaData> meta = source->getFormat();
          meta->findCString(kKeyMIMEType, &mime);
          if (!strncasecmp(mime, "audio/", 6)) {
              track = &mAudioTrack;
              trackType = MEDIA_TRACK_TYPE_AUDIO;
              counterpartType = MEDIA_TRACK_TYPE_VIDEO;
          } else {
              CHECK(!strncasecmp(mime, "video/", 6));
              track = &mVideoTrack;
              trackType = MEDIA_TRACK_TYPE_VIDEO;
              counterpartType = MEDIA_TRACK_TYPE_AUDIO;
          }


          if (track->mSource != NULL) {
              track->mSource->stop();
          }
          track->mSource = source;
          track->mSource->start();
          track->mIndex = trackIndex;

#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_AUDIO_CHANGE_SUPPORT) 
          int64_t timeUs = mAudioTimeUs;
          int64_t actualTimeUs;    
          const bool formatChange = true;
          sp<AMessage> latestMeta = track->mPackets->getLatestEnqueuedMeta();
          if(latestMeta != NULL){
              latestMeta->findInt64("timeUs", &timeUs);
          }
          int64_t latestDequeueTimeUs = -1;
          int64_t videoTimeUs = mVideoTimeUs;
          int64_t seekTimeUs = 0;
          sp<AMessage> latestDequeueMeta = track->mPackets->getLatestDequeuedMeta();
          if(latestDequeueMeta != NULL){
              latestDequeueMeta->findInt64("timeUs", &latestDequeueTimeUs);
          }
          seekTimeUs = (videoTimeUs != 0 ? videoTimeUs:(latestDequeueTimeUs != -1 ? latestDequeueTimeUs : timeUs));
          readBuffer(trackType, seekTimeUs, &actualTimeUs, formatChange); //do seek audio
          if(counterpartType == MEDIA_TRACK_TYPE_VIDEO){
              ALOGD("video formatChange is 0 when change audio");
              readBuffer(counterpartType, -1, NULL, 0);//set 0 to ensure not send DISCONTINUITY_NONE in readBuffer function
          }else{
              readBuffer(counterpartType, -1, NULL, formatChange);
          }
          ALOGE("[select track]seektime:%lld,timeUs %lld, latestDequeueTimeUs %lld,videoTime:%lld,actualTimeUs %lld,counterpartType:%d",seekTimeUs, timeUs, latestDequeueTimeUs,videoTimeUs,actualTimeUs,counterpartType);
		  break;
#else
          int64_t timeUs, actualTimeUs;
          const bool formatChange = true;
          if (trackType == MEDIA_TRACK_TYPE_AUDIO) {
              timeUs = mAudioLastDequeueTimeUs;
          } else {
              timeUs = mVideoLastDequeueTimeUs;
          }
          readBuffer(trackType, timeUs, &actualTimeUs, formatChange);
          readBuffer(counterpartType, -1, NULL, formatChange);
          ALOGV("timeUs %lld actualTimeUs %lld", timeUs, actualTimeUs);

          break;
#endif
      }

      case kWhatStart:
      case kWhatResume:
      {
          restartPollBuffering();
          break;
      }

      case kWhatPollBuffering:
      {
          int32_t generation;
          CHECK(msg->findInt32("generation", &generation));
          if (generation == mPollBufferingGeneration) {
              onPollBuffering();
          }
          break;
      }

      case kWhatGetFormat:
      {
          onGetFormatMeta(msg);
          break;
      }

      case kWhatGetSelectedTrack:
      {
          onGetSelectedTrack(msg);
          break;
      }

      case kWhatSelectTrack:
      {
          onSelectTrack(msg);
          break;
      }

      case kWhatSeek:
      {
          onSeek(msg);
          break;
      }

      case kWhatReadBuffer:
      {
          onReadBuffer(msg);
          break;
      }

      case kWhatSecureDecodersInstantiated:
      {
          int32_t err;
          CHECK(msg->findInt32("err", &err));
          onSecureDecodersInstantiated(err);
          break;
      }

      case kWhatStopWidevine:
      {
          // mStopRead is only used for Widevine to prevent the video source
          // from being read while the associated video decoder is shutting down.
          mStopRead = true;
          if (mVideoTrack.mSource != NULL) {
              mVideoTrack.mPackets->clear();
          }
          sp<AMessage> response = new AMessage;
          uint32_t replyID;
          CHECK(msg->senderAwaitsResponse(&replyID));
          response->postReply(replyID);
          break;
      }
      default:
          Source::onMessageReceived(msg);
          break;
    }
}

void NuPlayer::GenericSource::fetchTextData(
        uint32_t sendWhat,
        media_track_type type,
        int32_t curGen,
        sp<AnotherPacketSource> packets,
        sp<AMessage> msg) {
    int32_t msgGeneration;
    CHECK(msg->findInt32("generation", &msgGeneration));
    if (msgGeneration != curGen) {
        // stale
        return;
    }

    int32_t avail;
    if (packets->hasBufferAvailable(&avail)) {
        return;
    }

    int64_t timeUs;
    CHECK(msg->findInt64("timeUs", &timeUs));

    int64_t subTimeUs;
    readBuffer(type, timeUs, &subTimeUs);

    int64_t delayUs = subTimeUs - timeUs;
    if (msg->what() == kWhatFetchSubtitleData) {
        const int64_t oneSecUs = 1000000ll;
        delayUs -= oneSecUs;
    }
    sp<AMessage> msg2 = new AMessage(sendWhat, id());
    msg2->setInt32("generation", msgGeneration);
    msg2->post(delayUs < 0 ? 0 : delayUs);
    MM_LOGI("delayUs:%lld, subTimeUs:%lld, timeUs:%lld", delayUs, subTimeUs, timeUs);
}

void NuPlayer::GenericSource::sendTextData(
        uint32_t what,
        media_track_type type,
        int32_t curGen,
        sp<AnotherPacketSource> packets,
        sp<AMessage> msg) {
    int32_t msgGeneration;
    CHECK(msg->findInt32("generation", &msgGeneration));
    if (msgGeneration != curGen) {
        // stale
        return;
    }

#ifdef MTK_AOSP_ENHANCEMENT
#ifdef MTK_SUBTITLE_SUPPORT
    Track *track;
    track = (MEDIA_TRACK_TYPE_TIMEDTEXT == type)?&mTimedTextTrack:&mSubtitleTrack;
    if (track->isEOS) {
        ALOGD("sendTextData2:eos ,return ");
        return;
    }
#endif
#endif  
    int64_t subTimeUs;
    if (packets->nextBufferTime(&subTimeUs) != OK) {
        return;
    }

    int64_t nextSubTimeUs;
    readBuffer(type, -1, &nextSubTimeUs);

    sp<ABuffer> buffer;
    status_t dequeueStatus = packets->dequeueAccessUnit(&buffer);
    if (dequeueStatus == OK) {
        sp<AMessage> notify = dupNotify();
        notify->setInt32("what", what);
        notify->setBuffer("buffer", buffer);
        notify->post();

        const int64_t delayUs = nextSubTimeUs - subTimeUs;
        msg->post(delayUs < 0 ? 0 : delayUs);
    }
}

#ifdef MTK_AOSP_ENHANCEMENT
#ifdef MTK_SUBTITLE_SUPPORT
void NuPlayer::GenericSource::sendTextData2(
        uint32_t what,
        media_track_type type,
        int32_t curGen,
        sp<AnotherPacketSource> packets,
        sp<AMessage> msg) {
        ALOGD("func:%s L=%d ",__func__,__LINE__);
    int32_t msgGeneration;
    CHECK(msg->findInt32("generation", &msgGeneration));
    
    if (mIs3gppSource) {
        return sendTextData(kWhatTimedTextData, MEDIA_TRACK_TYPE_TIMEDTEXT,
                  mFetchTimedTextDataGeneration, mTimedTextTrack.mPackets, msg);
    }
    
    if (msgGeneration != curGen) {
        // stale
        /*sometime the msgGeneration will be increase by read buffer,so should call fetch data again*/
        if (MEDIA_TRACK_TYPE_TIMEDTEXT == type) {
            mTimedTextTrack.mPackets->clear(true);
        }
        return;
    }
    
    if (mTimedTextSource == NULL) {
        return;
    }
    
    Track *track;
    track = (MEDIA_TRACK_TYPE_TIMEDTEXT == type)?&mTimedTextTrack:&mSubtitleTrack;
    if (track->isEOS) {
        ALOGD("sendTextData2:eos ,return ");
        return;
    }
    
    int64_t subTimeUs;
    if (packets->nextBufferTime(&subTimeUs) != OK) {
        return;
    }

    int64_t nextSubTimeUs;
    readBuffer(type, -1, &nextSubTimeUs);

    sp<ABuffer> buffer;
    status_t dequeueStatus = packets->dequeueAccessUnit(&buffer);
    if (dequeueStatus == OK) {
        int64_t endTimeUs = -1;
        sp<AMessage> notify = dupNotify();
        sp<ParcelEvent>   parcelEvent = new ParcelEvent();
        notify->setInt32("what", what);
        //notify->setBuffer("buffer", buffer);
        buffer->meta()->findInt64("endtimeUs", &endTimeUs);
        mTimedTextSource->parse(buffer->data(),
                                buffer->size(),
                                subTimeUs,
                                -1,      /*fix me,this is a output parameter*/
                                &(parcelEvent->parcel));
        notify->setObject("subtitle", parcelEvent);
        notify->setInt64("timeUs", subTimeUs);
        notify->post();

        if ((endTimeUs > 0) && (endTimeUs + 100000 < nextSubTimeUs)) {
            sp<AMessage> endNotify = dupNotify();
            sp<ParcelEvent>   parcelEvent = new ParcelEvent();
            endNotify->setInt32("what", what);
            endNotify->setObject("subtitle", parcelEvent);
            endNotify->setInt64("timeUs", endTimeUs);
            endNotify->post();
        }

        const int64_t delayUs = nextSubTimeUs - mVideoTimeUs;
        msg->post(delayUs < 0 ? 0 : delayUs);
    }
}
#endif
#endif 


sp<MetaData> NuPlayer::GenericSource::getFormatMeta(bool audio) {
    sp<AMessage> msg = new AMessage(kWhatGetFormat, id());
    msg->setInt32("audio", audio);

#ifdef MTK_AOSP_ENHANCEMENT
    if (mCachedSource != NULL) {
        sp<MetaData> format = doGetFormatMeta(audio);
        if (format == NULL) {
            return NULL;
        }
        if (!audio) {
            const char *mime;
            if (format->findCString(kKeyMIMEType, &mime) && !strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_HEVC)) { 
                format->setInt32(kKeyMaxQueueBuffer, 4);    // due to sw hevc decode, need more input buffer
                ALOGI("hevc video set max queue buffer 4");
            } else {
                format->setInt32(kKeyMaxQueueBuffer, 1);
                ALOGI("video set max queue buffer 1");
            }
        } else {
            const char *mime = NULL;			
            if (mFileMeta != NULL && mFileMeta->findCString(kKeyMIMEType, &mime) && !strcasecmp(mime, "audio/x-wav")) { 
                ALOGI("audio x-wav max queueBuffer 2");
                format->setInt32(kKeyInputBufferNum, 4);
                format->setInt32(kKeyMaxQueueBuffer, 2);
            }
        }
        if (mDecryptHandle != NULL && format != NULL) {
            format->setInt32(kKeyIsProtectDrm, 1);
        }
        if( mIsWidevine && !mIsRequiresSecureBuffer && format != NULL){				
            format->setInt32(kKeyIsProtectDrm, 1);
        }
        return format;
    }
#endif
    sp<AMessage> response;
    void *format;
    status_t err = msg->postAndAwaitResponse(&response);
    if (err == OK && response != NULL) {
        CHECK(response->findPointer("format", &format));
#ifdef MTK_AOSP_ENHANCEMENT
        if (mDecryptHandle != NULL && format != NULL) {
            MetaData *meta = (MetaData *)format;
            meta->setInt32(kKeyIsProtectDrm, 1);
        }
        if( mIsWidevine && !mIsRequiresSecureBuffer && format != NULL){				

            MetaData *meta = (MetaData *)format;
            meta->setInt32(kKeyIsProtectDrm, 1);
        }
#endif
        return (MetaData *)format;
    } else {
        return NULL;
    }
}

void NuPlayer::GenericSource::onGetFormatMeta(sp<AMessage> msg) const {
    int32_t audio;
    CHECK(msg->findInt32("audio", &audio));

    sp<AMessage> response = new AMessage;
    sp<MetaData> format = doGetFormatMeta(audio);
    response->setPointer("format", format.get());

    uint32_t replyID;
    CHECK(msg->senderAwaitsResponse(&replyID));
    response->postReply(replyID);
}

sp<MetaData> NuPlayer::GenericSource::doGetFormatMeta(bool audio) const {
    sp<MediaSource> source = audio ? mAudioTrack.mSource : mVideoTrack.mSource;
#ifdef MTK_AOSP_ENHANCEMENT
	ALOGI("GenericSource::getFormatMeta %d",audio);
	if(mRtspUri.string() && mSessionDesc.get()){
		//if is sdp
		mSDPFormatMeta->setCString(kKeyMIMEType,MEDIA_MIMETYPE_APPLICATION_SDP);
		mSDPFormatMeta->setCString(kKeyUri,mRtspUri.string());
		mSDPFormatMeta->setPointer(kKeySDP,mSessionDesc.get());

		ALOGI("GenericSource::getFormatMeta sdp meta");

		return mSDPFormatMeta;
	}
#endif
    if (source == NULL) {
        return NULL;
    }

    return source->getFormat();
}

status_t NuPlayer::GenericSource::dequeueAccessUnit(
        bool audio, sp<ABuffer> *accessUnit) {
#ifdef MTK_AOSP_ENHANCEMENT
    // http Streaming check buffering status
    if (mCachedSource != NULL) {
        onPollBuffering(false /*not notify buffer percent*/);
        if (mBuffering)
            return -EWOULDBLOCK;
        {
            Mutex::Autolock _l(mSeekingLock);
            if (mSeekingCount > 0) {
                ALOGV("seeking now, return wouldblock");
                return -EWOULDBLOCK;
            }
        }
    }
#endif
    Track *track = audio ? &mAudioTrack : &mVideoTrack;

    if (track->mSource == NULL) {
        return -EWOULDBLOCK;
    }

    if (mIsWidevine && !audio) {
        // try to read a buffer as we may not have been able to the last time
        postReadBuffer(MEDIA_TRACK_TYPE_VIDEO);
    }

    status_t finalResult;
    if (!track->mPackets->hasBufferAvailable(&finalResult)) {
        if (finalResult == OK) {
            postReadBuffer(
                    audio ? MEDIA_TRACK_TYPE_AUDIO : MEDIA_TRACK_TYPE_VIDEO);
            return -EWOULDBLOCK;
        }
        return finalResult;
    }

    status_t result = track->mPackets->dequeueAccessUnit(accessUnit);

    if (!track->mPackets->hasBufferAvailable(&finalResult)) {
        postReadBuffer(audio? MEDIA_TRACK_TYPE_AUDIO : MEDIA_TRACK_TYPE_VIDEO);
    }

    if (result != OK) {
        if (mSubtitleTrack.mSource != NULL) {
            mSubtitleTrack.mPackets->clear();
            mFetchSubtitleDataGeneration++;
        }
        if (mTimedTextTrack.mSource != NULL) {
            mTimedTextTrack.mPackets->clear();
            mFetchTimedTextDataGeneration++;
        }
        return result;
    }

    int64_t timeUs;
    status_t eosResult; // ignored
    CHECK((*accessUnit)->meta()->findInt64("timeUs", &timeUs));
#ifdef MTK_AOSP_ENHANCEMENT
    ALOGD("dequeueAccessUnit audio:%d time:%lld",audio,timeUs);
#endif
    if (audio) {
        mAudioLastDequeueTimeUs = timeUs;
    } else {
        mVideoLastDequeueTimeUs = timeUs;
    }

    if (mSubtitleTrack.mSource != NULL
            && !mSubtitleTrack.mPackets->hasBufferAvailable(&eosResult)) {
        sp<AMessage> msg = new AMessage(kWhatFetchSubtitleData, id());
        msg->setInt64("timeUs", timeUs);
        msg->setInt32("generation", mFetchSubtitleDataGeneration);
        msg->post();
    }

    if (mTimedTextTrack.mSource != NULL
            && !mTimedTextTrack.mPackets->hasBufferAvailable(&eosResult)) {
        sp<AMessage> msg = new AMessage(kWhatFetchTimedTextData, id());
        msg->setInt64("timeUs", timeUs);
        msg->setInt32("generation", mFetchTimedTextDataGeneration);
        msg->post();
        MM_LOGI("TimedText seek TimeUs:%lld", timeUs);
    }

    return result;
}

status_t NuPlayer::GenericSource::getDuration(int64_t *durationUs) {
    *durationUs = mDurationUs;
    return OK;
}

size_t NuPlayer::GenericSource::getTrackCount() const {
	ALOGD("getTrackCount :%d",mSources.size());
    return mSources.size();
}

sp<AMessage> NuPlayer::GenericSource::getTrackInfo(size_t trackIndex) const {
    size_t trackCount = mSources.size();
    if (trackIndex >= trackCount) {
        return NULL;
    }

    sp<AMessage> format = new AMessage();
    sp<MetaData> meta = mSources.itemAt(trackIndex)->getFormat();

    const char *mime;
    CHECK(meta->findCString(kKeyMIMEType, &mime));

    int32_t trackType;
    if (!strncasecmp(mime, "video/", 6)) {
        trackType = MEDIA_TRACK_TYPE_VIDEO;
    } else if (!strncasecmp(mime, "audio/", 6)) {
        trackType = MEDIA_TRACK_TYPE_AUDIO;
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_SUBTITLE_SUPPORT)
    } else if (!strncasecmp(mime, "text/", 5)) {
        trackType = MEDIA_TRACK_TYPE_TIMEDTEXT;    
#else
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_TEXT_3GPP)) {
        trackType = MEDIA_TRACK_TYPE_TIMEDTEXT;
#endif
    } else {
        trackType = MEDIA_TRACK_TYPE_UNKNOWN;
    }
    format->setInt32("type", trackType);

    const char *lang;
    if (!meta->findCString(kKeyMediaLanguage, &lang)) {
        lang = "und";
    }
    format->setString("language", lang);

    if (trackType == MEDIA_TRACK_TYPE_SUBTITLE) {
        format->setString("mime", mime);

        int32_t isAutoselect = 1, isDefault = 0, isForced = 0;
        meta->findInt32(kKeyTrackIsAutoselect, &isAutoselect);
        meta->findInt32(kKeyTrackIsDefault, &isDefault);
        meta->findInt32(kKeyTrackIsForced, &isForced);

        format->setInt32("auto", !!isAutoselect);
        format->setInt32("default", !!isDefault);
        format->setInt32("forced", !!isForced);
    }

    return format;
}

ssize_t NuPlayer::GenericSource::getSelectedTrack(media_track_type type) const {
    sp<AMessage> msg = new AMessage(kWhatGetSelectedTrack, id());
    msg->setInt32("type", type);

    sp<AMessage> response;
    int32_t index;
    status_t err = msg->postAndAwaitResponse(&response);
    if (err == OK && response != NULL) {
        CHECK(response->findInt32("index", &index));
        return index;
    } else {
        return -1;
    }
}

void NuPlayer::GenericSource::onGetSelectedTrack(sp<AMessage> msg) const {
    int32_t tmpType;
    CHECK(msg->findInt32("type", &tmpType));
    media_track_type type = (media_track_type)tmpType;

    sp<AMessage> response = new AMessage;
    ssize_t index = doGetSelectedTrack(type);
    response->setInt32("index", index);

    uint32_t replyID;
    CHECK(msg->senderAwaitsResponse(&replyID));
    response->postReply(replyID);
}

ssize_t NuPlayer::GenericSource::doGetSelectedTrack(media_track_type type) const {
    const Track *track = NULL;
    switch (type) {
    case MEDIA_TRACK_TYPE_VIDEO:
        track = &mVideoTrack;
        break;
    case MEDIA_TRACK_TYPE_AUDIO:
        track = &mAudioTrack;
        break;
    case MEDIA_TRACK_TYPE_TIMEDTEXT:
        track = &mTimedTextTrack;
        break;
    case MEDIA_TRACK_TYPE_SUBTITLE:
        track = &mSubtitleTrack;
        break;
    default:
        break;
    }

    if (track != NULL && track->mSource != NULL) {
        return track->mIndex;
    }

    return -1;
}

status_t NuPlayer::GenericSource::selectTrack(size_t trackIndex, bool select, int64_t timeUs) {
    ALOGV("%s track: %zu", select ? "select" : "deselect", trackIndex);
    sp<AMessage> msg = new AMessage(kWhatSelectTrack, id());
    msg->setInt32("trackIndex", trackIndex);
    msg->setInt32("select", select);
    msg->setInt64("timeUs", timeUs);

    sp<AMessage> response;
    status_t err = msg->postAndAwaitResponse(&response);
    if (err == OK && response != NULL) {
        CHECK(response->findInt32("err", &err));
    }

    return err;
}

void NuPlayer::GenericSource::onSelectTrack(sp<AMessage> msg) {
    int32_t trackIndex, select;
    int64_t timeUs;
    CHECK(msg->findInt32("trackIndex", &trackIndex));
    CHECK(msg->findInt32("select", &select));
    CHECK(msg->findInt64("timeUs", &timeUs));

    sp<AMessage> response = new AMessage;
    status_t err = doSelectTrack(trackIndex, select, timeUs);
    response->setInt32("err", err);

    uint32_t replyID;
    CHECK(msg->senderAwaitsResponse(&replyID));
    response->postReply(replyID);
}

status_t NuPlayer::GenericSource::doSelectTrack(size_t trackIndex, bool select, int64_t timeUs) {
    if (trackIndex >= mSources.size()) {
        return BAD_INDEX;
    }

    if (!select) {
        Track* track = NULL;
        if (mSubtitleTrack.mSource != NULL && trackIndex == mSubtitleTrack.mIndex) {
            track = &mSubtitleTrack;
            mFetchSubtitleDataGeneration++;
        } else if (mTimedTextTrack.mSource != NULL && trackIndex == mTimedTextTrack.mIndex) {
            track = &mTimedTextTrack;
            mFetchTimedTextDataGeneration++;
#ifdef MTK_AOSP_ENHANCEMENT
#ifdef MTK_SUBTITLE_SUPPORT
            if(mTimedTextSource != NULL) {
                mTimedTextSource->stop();
                mTimedTextSource.clear();
                sp<AMessage> Notifymsg = dupNotify();
                sp<ParcelEvent>   parcelEvent = new ParcelEvent();
                Notifymsg->setInt32("what", kWhatTimedTextData2);
                Notifymsg->setObject("subtitle", parcelEvent);
                Notifymsg->setInt64("timeUs", 0);
                Notifymsg->post();
            }
#endif
#endif
        }
        if (track == NULL) {
			MM_LOGI("[select track]track == NULL");
            return INVALID_OPERATION;
        }
        track->mSource->stop();
        track->mSource = NULL;
        track->mPackets->clear();
        return OK;
    }

    const sp<MediaSource> source = mSources.itemAt(trackIndex);
    sp<MetaData> meta = source->getFormat();
    const char *mime;
    CHECK(meta->findCString(kKeyMIMEType, &mime));
    if (!strncasecmp(mime, "text/", 5)) {
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_SUBTITLE_SUPPORT)        
        bool isSubtitle = !((0 == strcasecmp(mime, MEDIA_MIMETYPE_TEXT_3GPP))
                         || (0 ==  strcasecmp(mime, MEDIA_MIMETYPE_TEXT_ASS))
                         || (0 ==  strcasecmp(mime, MEDIA_MIMETYPE_TEXT_SSA))
                         || (0 ==  strcasecmp(mime, MEDIA_MIMETYPE_TEXT_VOBSUB))
                         || (0 ==  strcasecmp(mime, MEDIA_MIMETYPE_TEXT_DVB))
                         || (0 ==  strcasecmp(mime, MEDIA_MIMETYPE_TEXT_TXT)));
        ALOGD("func:%s isSubtitle=%d ",__func__,isSubtitle);
        if (0 == strcasecmp(mime, MEDIA_MIMETYPE_TEXT_3GPP)) {
            mIs3gppSource = true;
        }
        else {
            mIs3gppSource = false;
        }    
#else
        bool isSubtitle = strcasecmp(mime, MEDIA_MIMETYPE_TEXT_3GPP);
#endif
        Track *track = isSubtitle ? &mSubtitleTrack : &mTimedTextTrack;
        if (track->mSource != NULL && track->mIndex == trackIndex) {
            return OK;
        }
        track->mIndex = trackIndex;
        if (track->mSource != NULL) {
            track->mSource->stop();
        }
        track->mSource = mSources.itemAt(trackIndex);
        if (track->mPackets == NULL) {
            track->mPackets = new AnotherPacketSource(track->mSource->getFormat());
        } else {
            track->mPackets->clear();
            track->mPackets->setFormat(track->mSource->getFormat());
        }
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_SUBTITLE_SUPPORT)  
        if (!isSubtitle && !mIs3gppSource) {
            mTimedTextSource = TimedTextSource::CreateTimedTextSource(track->mSource);
            mTimedTextSource->start();
        }
        else {
            track->mSource->start();
        }
#else
        track->mSource->start();
#endif


        if (isSubtitle) {
            mFetchSubtitleDataGeneration++;
        } else {
            mFetchTimedTextDataGeneration++;
        }

        status_t eosResult; // ignored
        if (mSubtitleTrack.mSource != NULL
                && !mSubtitleTrack.mPackets->hasBufferAvailable(&eosResult)) {
            sp<AMessage> msg = new AMessage(kWhatFetchSubtitleData, id());
            msg->setInt64("timeUs", timeUs);
            msg->setInt32("generation", mFetchSubtitleDataGeneration);
            msg->post();
        }

        if (mTimedTextTrack.mSource != NULL
                && !mTimedTextTrack.mPackets->hasBufferAvailable(&eosResult)) {
            sp<AMessage> msg = new AMessage(kWhatFetchTimedTextData, id());
            msg->setInt64("timeUs", timeUs);
            msg->setInt32("generation", mFetchTimedTextDataGeneration);
            msg->post();
        }

        return OK;
    } else if (!strncasecmp(mime, "audio/", 6) || !strncasecmp(mime, "video/", 6)) {
        bool audio = !strncasecmp(mime, "audio/", 6);
        Track *track = audio ? &mAudioTrack : &mVideoTrack;
        if (track->mSource != NULL && track->mIndex == trackIndex) {
            return OK;
        }

        sp<AMessage> msg = new AMessage(kWhatChangeAVSource, id());
        msg->setInt32("trackIndex", trackIndex);
        msg->post();
        return OK;
    }

    return INVALID_OPERATION;
}

status_t NuPlayer::GenericSource::seekTo(int64_t seekTimeUs) {
    sp<AMessage> msg = new AMessage(kWhatSeek, id());
    msg->setInt64("seekTimeUs", seekTimeUs);

#ifdef MTK_AOSP_ENHANCEMENT
    if (mCachedSource != NULL) {
        MM_LOGI("seeking start,mSeekingCount:%d", mSeekingCount);
        {
            Mutex::Autolock _l(mSeekingLock);
            mSeekingCount++;
        }
        msg->post();
        return OK;
    } 
#endif
    sp<AMessage> response;
    status_t err = msg->postAndAwaitResponse(&response);
    if (err == OK && response != NULL) {
        CHECK(response->findInt32("err", &err));
    }

    return err;
}

void NuPlayer::GenericSource::onSeek(sp<AMessage> msg) {
    int64_t seekTimeUs;
    CHECK(msg->findInt64("seekTimeUs", &seekTimeUs));

#ifdef MTK_AOSP_ENHANCEMENT
    if (mCachedSource != NULL) {   // http Streaming do not reply
        Mutex::Autolock _l(mSeekingLock);
        if (mSeekingCount > 1) {
            MM_LOGI("seek timeUs:%lld, miss, mSeekingCount:%d", seekTimeUs, mSeekingCount);
            mSeekingCount--;
            mSeekingLock.unlock();
            notifySeekDone(OK);
            mSeekingLock.lock();
            return;
        }
    }
    MM_LOGI("seek timeUs:%lld", seekTimeUs);
#endif
    sp<AMessage> response = new AMessage;
    status_t err = doSeek(seekTimeUs);
#ifdef MTK_AOSP_ENHANCEMENT
    if (mCachedSource != NULL) {   // http Streaming do not reply
        MM_LOGI("seek finish, mSeekingCount:%d", mSeekingCount);
        {
            Mutex::Autolock _l(mSeekingLock);
            mSeekingCount--;
        }
        notifySeekDone(OK);
        return;
    }
#endif
    response->setInt32("err", err);

    uint32_t replyID;
    CHECK(msg->senderAwaitsResponse(&replyID));
    response->postReply(replyID);
}

status_t NuPlayer::GenericSource::doSeek(int64_t seekTimeUs) {
    // If the Widevine source is stopped, do not attempt to read any
    // more buffers.
    if (mStopRead) {
        return INVALID_OPERATION;
    }
#ifdef MTK_AOSP_ENHANCEMENT
    mSeekTimeUs = seekTimeUs;
#endif
    if (mVideoTrack.mSource != NULL) {
        int64_t actualTimeUs;
        readBuffer(MEDIA_TRACK_TYPE_VIDEO, seekTimeUs, &actualTimeUs);

#ifdef  MTK_PLAYREADY_SUPPORT
        int32_t isPlayReady = 0;
        if (mFileMeta != NULL && mFileMeta->findInt32(kKeyIsPlayReady, &isPlayReady) && isPlayReady) {
            ALOGI("playready seek");
        } else {
            seekTimeUs = actualTimeUs;
        }
#else
        seekTimeUs = actualTimeUs;
#endif
        mVideoLastDequeueTimeUs = seekTimeUs;
    }

    if (mAudioTrack.mSource != NULL) {
        readBuffer(MEDIA_TRACK_TYPE_AUDIO, seekTimeUs);
        mAudioLastDequeueTimeUs = seekTimeUs;
    }

    setDrmPlaybackStatusIfNeeded(Playback::START, seekTimeUs / 1000);
    if (!mStarted) {
        setDrmPlaybackStatusIfNeeded(Playback::PAUSE, 0);
    }
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_SUBTITLE_SUPPORT)
    mSubtitleTrack.mPackets = new AnotherPacketSource(NULL);
    mTimedTextTrack.mPackets = new AnotherPacketSource(NULL);
#endif
    // If currently buffering, post kWhatBufferingEnd first, so that
    // NuPlayer resumes. Otherwise, if cache hits high watermark
    // before new polling happens, no one will resume the playback.
    stopBufferingIfNecessary();
    restartPollBuffering();

    return OK;
}

sp<ABuffer> NuPlayer::GenericSource::mediaBufferToABuffer(
        MediaBuffer* mb,
        media_track_type trackType,
#ifdef MTK_AOSP_ENHANCEMENT
        int64_t seekTimeUs,
#else
        int64_t /* seekTimeUs */,
#endif
        int64_t *actualTimeUs) {
    bool audio = trackType == MEDIA_TRACK_TYPE_AUDIO;
    size_t outLength = mb->range_length();

    if (audio && mAudioIsVorbis) {
        outLength += sizeof(int32_t);
    }

    sp<ABuffer> ab;
    if (mIsSecure && !audio) {
        // data is already provided in the buffer
        ab = new ABuffer(NULL, mb->range_length());
        mb->add_ref();
        ab->setMediaBufferBase(mb);
    } else {
        ab = new ABuffer(outLength);
        memcpy(ab->data(),
               (const uint8_t *)mb->data() + mb->range_offset(),
               mb->range_length());
    }

    if (audio && mAudioIsVorbis) {
        int32_t numPageSamples;
        if (!mb->meta_data()->findInt32(kKeyValidSamples, &numPageSamples)) {
            numPageSamples = -1;
        }

        uint8_t* abEnd = ab->data() + mb->range_length();
        memcpy(abEnd, &numPageSamples, sizeof(numPageSamples));
    }

    sp<AMessage> meta = ab->meta();

    int64_t timeUs;
    CHECK(mb->meta_data()->findInt64(kKeyTime, &timeUs));
    meta->setInt64("timeUs", timeUs);
#ifdef MTK_AOSP_ENHANCEMENT
    if (trackType == MEDIA_TRACK_TYPE_VIDEO) {
        int32_t fgInvalidTimeUs = false;
        if (mb->meta_data()->findInt32(kInvalidKeyTime, &fgInvalidTimeUs) && fgInvalidTimeUs) {
            meta->setInt32("invt", fgInvalidTimeUs);
        }
    }
#endif

#ifdef MTK_AOSP_ENHANCEMENT
    if (mHTTPService == NULL ) {
// should set resume time, or else would not set seektime to ACodec in NuPlayerDecoder
//        if (seekTimeUs > timeUs) {
	   if (seekTimeUs != -1) {
            sp<AMessage> extra = new AMessage;
            extra->setInt64("resume-at-mediaTimeUs", seekTimeUs);
            ALOGI("set resume time:%lld", seekTimeUs);
            meta->setMessage("extra", extra);
        }
    }
#else
#if 0
    // Temporarily disable pre-roll till we have a full solution to handle
    // both single seek and continous seek gracefully.
    if (seekTimeUs > timeUs) {
        sp<AMessage> extra = new AMessage;
        extra->setInt64("resume-at-mediaTimeUs", seekTimeUs);
        meta->setMessage("extra", extra);
    }
#endif
#endif
    if (trackType == MEDIA_TRACK_TYPE_TIMEDTEXT) {
        const char *mime;
        CHECK(mTimedTextTrack.mSource != NULL
                && mTimedTextTrack.mSource->getFormat()->findCString(kKeyMIMEType, &mime));
        meta->setString("mime", mime);
#ifdef MTK_AOSP_ENHANCEMENT
#ifdef MTK_SUBTITLE_SUPPORT
        /*subtitle endtime*/
        int64_t endTimeUs;
        if (mb->meta_data()->findInt64(kKeyDriftTime, &endTimeUs)) {
            meta->setInt64("endtimeUs", endTimeUs);
        } else {
            meta->setInt64("endtimeUs", -1);
        }
#endif
#endif
    }

    int64_t durationUs;
    if (mb->meta_data()->findInt64(kKeyDuration, &durationUs)) {
        meta->setInt64("durationUs", durationUs);
    }

    if (trackType == MEDIA_TRACK_TYPE_SUBTITLE) {
        meta->setInt32("trackIndex", mSubtitleTrack.mIndex);
    }

    if (actualTimeUs) {
        *actualTimeUs = timeUs;
    }

    mb->release();
    mb = NULL;

    return ab;
}

void NuPlayer::GenericSource::postReadBuffer(media_track_type trackType) {
    Mutex::Autolock _l(mReadBufferLock);

    if ((mPendingReadBufferTypes & (1 << trackType)) == 0) {
        mPendingReadBufferTypes |= (1 << trackType);
        sp<AMessage> msg = new AMessage(kWhatReadBuffer, id());
        msg->setInt32("trackType", trackType);
        msg->post();
    }
}

void NuPlayer::GenericSource::onReadBuffer(sp<AMessage> msg) {
    int32_t tmpType;
    CHECK(msg->findInt32("trackType", &tmpType));
    media_track_type trackType = (media_track_type)tmpType;
    readBuffer(trackType);
    {
        // only protect the variable change, as readBuffer may
        // take considerable time.
        Mutex::Autolock _l(mReadBufferLock);
        mPendingReadBufferTypes &= ~(1 << trackType);
    }
}

void NuPlayer::GenericSource::readBuffer(
        media_track_type trackType, int64_t seekTimeUs, int64_t *actualTimeUs, bool formatChange) {
    // Do not read data if Widevine source is stopped
    if (mStopRead) {
        return;
    }
    Track *track;
    size_t maxBuffers = 1;
    switch (trackType) {
        case MEDIA_TRACK_TYPE_VIDEO:
            track = &mVideoTrack;
            if (mIsWidevine) {
#ifdef  MTK_PLAYREADY_SUPPORT
                int32_t isPlayReady = 0;
                if (mFileMeta != NULL && mFileMeta->findInt32(kKeyIsPlayReady, &isPlayReady) && isPlayReady) {
                    maxBuffers = 1;
                } else {
                    maxBuffers = 2;
                }
#else
                maxBuffers = 2;
#endif
            }
            break;
        case MEDIA_TRACK_TYPE_AUDIO:
            track = &mAudioTrack;
            if (mIsWidevine) {
                maxBuffers = 8;
            } else {
                maxBuffers = 64;
#ifdef MTK_AOSP_ENHANCEMENT  // ALPS01966472, when file is mpeg4 and the audio is raw, set maxBuffers = 16;
				if(mAudioIsRaw){
				   const char *mime = NULL;		
				   if (mFileMeta != NULL && mFileMeta->findCString(kKeyMIMEType, &mime) 
					   && !strcasecmp(mime, MEDIA_MIMETYPE_CONTAINER_MPEG4)) { 
						maxBuffers = 16;
				   }
				}
				ALOGI("maxBuffers:%d",maxBuffers);
#endif
            }
            break;
        case MEDIA_TRACK_TYPE_SUBTITLE:
            track = &mSubtitleTrack;
            break;
        case MEDIA_TRACK_TYPE_TIMEDTEXT:
            track = &mTimedTextTrack;
            break;
        default:
            TRESPASS();
    }

    if (track->mSource == NULL) {
        return;
    }

    if (actualTimeUs) {
        *actualTimeUs = seekTimeUs;
    }

    MediaSource::ReadOptions options;

    bool seeking = false;

    if (seekTimeUs >= 0) {
        options.setSeekTo(seekTimeUs, MediaSource::ReadOptions::SEEK_PREVIOUS_SYNC);
        seeking = true;
#ifdef MTK_AOSP_ENHANCEMENT
        if (track->isEOS) {
            ALOGI("reset EOS false");
            track->isEOS = false;
            if (mCachedSource != NULL) {
                ALOGI("re schedule Poll Buffering");
                schedulePollBuffering();
            }
        }
        MM_LOGI("seekTimeUs:%lld, trackType:%d", seekTimeUs, trackType);
#endif
    }
#ifdef MTK_AOSP_ENHANCEMENT
    if (track->isEOS) {
        return;
    }
#endif

    if (mIsWidevine) {
        options.setNonBlocking();
    }

#ifdef MTK_AOSP_ENHANCEMENT     //for ts
       if( isTS() )
       {
          if( (seeking||mTSbuffering) && (trackType==MEDIA_TRACK_TYPE_AUDIO)){
                track = &mAudioTrack;
                if (mIsWidevine) {
                    maxBuffers = 8;
                } else {
                    maxBuffers = 16;
                }
          }
       }
#endif                           //for ts
    for (size_t numBuffers = 0; numBuffers < maxBuffers; ) {
        MediaBuffer *mbuf;
        status_t err = track->mSource->read(&mbuf, &options);

        options.clearSeekTo();

        if (err == OK) {
            int64_t timeUs;
            CHECK(mbuf->meta_data()->findInt64(kKeyTime, &timeUs));
            if (trackType == MEDIA_TRACK_TYPE_AUDIO) {
                mAudioTimeUs = timeUs;
            } else if (trackType == MEDIA_TRACK_TYPE_VIDEO) {
                mVideoTimeUs = timeUs;
            }

            // formatChange && seeking: track whose source is changed during selection
            // formatChange && !seeking: track whose source is not changed during selection
            // !formatChange: normal seek
            if ((seeking || formatChange)
                    && (trackType == MEDIA_TRACK_TYPE_AUDIO
                    || trackType == MEDIA_TRACK_TYPE_VIDEO)) {
                ATSParser::DiscontinuityType type = (formatChange && seeking)
                        ? ATSParser::DISCONTINUITY_FORMATCHANGE
                        : ATSParser::DISCONTINUITY_NONE;
                track->mPackets->queueDiscontinuity( type, NULL, true /* discard */);
            }
            sp<ABuffer> buffer = mediaBufferToABuffer(
#ifdef MTK_AOSP_ENHANCEMENT
                    mbuf, trackType, seeking? mSeekTimeUs:-1, actualTimeUs);
#else
                    mbuf, trackType, seekTimeUs, actualTimeUs);
#endif
            track->mPackets->queueAccessUnit(buffer);
#ifdef MTK_AOSP_ENHANCEMENT
	     BufferingDataForTsVideo( trackType, !(formatChange | seeking  | actualTimeUs !=NULL));
#endif
            formatChange = false;
            seeking = false;
#ifdef MTK_AOSP_ENHANCEMENT
             mTSbuffering= false;  // for ts
#endif
            ++numBuffers;
        } else if (err == WOULD_BLOCK) {
            break;
        } else if (err == INFO_FORMAT_CHANGED) {
#if 0
            track->mPackets->queueDiscontinuity(
                    ATSParser::DISCONTINUITY_FORMATCHANGE,
                    NULL,
                    false /* discard */);
#endif
        } else {
#ifdef MTK_AOSP_ENHANCEMENT 
            if (seeking) {
				sp<AMessage> extra = NULL;
				extra = new AMessage;
				extra->setInt64("resume-at-mediatimeUs", mSeekTimeUs);
				ALOGI("seek to EOS, discard packets buffers and set preRoll time:%lld", mSeekTimeUs);
				track->mPackets->queueDiscontinuity(ATSParser::DISCONTINUITY_TIME, extra, true /* discard */);
            }
#endif

            track->mPackets->signalEOS(err);
#ifdef MTK_AOSP_ENHANCEMENT
            track->isEOS = true;
#endif
            break;
        }
    }
}

#ifdef MTK_AOSP_ENHANCEMENT
#ifdef MTK_DRM_APP
void NuPlayer::GenericSource::getDRMClientProc(const Parcel *request) {
	mDrmValue = request->readString8();
}
#endif

status_t NuPlayer::GenericSource::initCheck() const{
       ALOGI("GenericSource::initCheck");
	return mInitCheck;
}
status_t NuPlayer::GenericSource::getFinalStatus() const{
    status_t cache_stat = OK;
    if (mCachedSource != NULL) 
        cache_stat = mCachedSource->getRealFinalStatus();
    ALOGI("GenericSource::getFinalStatus");
    return cache_stat;
}
status_t NuPlayer::GenericSource::initFromDataSource_checkLocalSdp(const sp<MediaExtractor> extractor) {
    void *sdp = NULL;
    if (extractor->getMetaData().get()!= NULL && extractor->getMetaData()->findPointer(kKeySDP, &sdp)) {
    	  
    	 sp<ASessionDescription> pSessionDesc;
        pSessionDesc = (ASessionDescription*)sdp;
        ALOGI("initFromDataSource,is application/sdp");
        if (!pSessionDesc->isValid()){
		ALOGE("initFromDataSource,sdp file is not valid!");
		pSessionDesc.clear();
		mInitCheck = ERROR_UNSUPPORTED;
		return ERROR_UNSUPPORTED;  //notify not supported sdp
        }
        if (pSessionDesc->countTracks() == 1u){
		ALOGE("initFromDataSource,sdp file contain only root description");
		pSessionDesc.clear();
		mInitCheck = ERROR_UNSUPPORTED;
		return ERROR_UNSUPPORTED;
        }
        status_t err = pSessionDesc->getSessionUrl(mRtspUri);
        if (err != OK){
		ALOGE("initFromDataSource,can't get new url from sdp!!!");
		pSessionDesc.clear();
		mRtspUri.clear();
		mInitCheck = ERROR_UNSUPPORTED;
		return ERROR_UNSUPPORTED;
        }
	mSessionDesc = pSessionDesc;
	mInitCheck = OK;
	return  OK;
    }
    return NO_INIT;//not sdp
}

bool NuPlayer::GenericSource::isTS(){
	const char *mime = NULL;			
        if (mFileMeta != NULL && mFileMeta->findCString(kKeyMIMEType, &mime) 
	 && !strcasecmp(mime, MEDIA_MIMETYPE_CONTAINER_MPEG2TS)) { 
           return true;
        }
#ifdef MTK_MTKPS_PLAYBACK_SUPPORT
       if (mFileMeta != NULL && mFileMeta->findCString(kKeyMIMEType, &mime) 
    && !strcasecmp(mime, MEDIA_MIMETYPE_CONTAINER_MPEG2PS)) { 
          ALOGD("is ps");
          return true;
       }
#endif
	return false;
}
void NuPlayer::GenericSource::BufferingDataForTsVideo(media_track_type trackType, bool shouldBuffering){
	if(!isTS() ) return ;
      status_t finalResult =OK;
       shouldBuffering = (shouldBuffering 
			   		 &&  (trackType == MEDIA_TRACK_TYPE_AUDIO 
						&& (&mVideoTrack) != NULL 
						/*&&  !(mVideoTrack.mPackets->hasBufferAvailable(&finalResult))
						&& (finalResult == OK)*/
						&& !mVideoTrack.isEOS
						&& mVideoTrack.mSource != NULL));
	
	if(!shouldBuffering) return;
	
	void* sourceQueue = NULL	;	
	mVideoTrack.mSource->getFormat()->findPointer('anps', &sourceQueue);
	if(sourceQueue == NULL) return;

	if(!((AnotherPacketSource*)sourceQueue)->hasBufferAvailable(&finalResult)) return;

       sp<ABuffer> buffer;
	 status_t err =( (AnotherPacketSource*)sourceQueue)->dequeueAccessUnit(&buffer);
        if (err == OK) {
            #if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_AUDIO_CHANGE_SUPPORT)
            int64_t timeUs;
            CHECK(buffer->meta()->findInt64("timeUs", &timeUs));
            mVideoTimeUs = timeUs;
            #endif  
            (&mVideoTrack)->mPackets->queueAccessUnit(buffer);
            ALOGD("[TS]readbuffer:queueAccessUnit %s","video" );
	}
}

bool NuPlayer::GenericSource::hasVideo() {
    return (mVideoTrack.mSource != NULL);
}

void NuPlayer::GenericSource::notifySeekDone(status_t err) {
    sp<AMessage> msg = dupNotify();
    msg->setInt32("what", kWhatSeekDone);
    msg->setInt32("err", err);
    msg->post();
}

void NuPlayer::GenericSource::setParams(const sp<MetaData>& meta)
{
	isMtkMusic = 0;
	ALOGD("qiushi GenericSource SetParames");
	meta->findInt32(kKeyIsMtkMusic,&isMtkMusic);
	if(isMtkMusic == 1)
	{
		ALOGD("is mtk music");
	}
}
#endif
}  // namespace android
