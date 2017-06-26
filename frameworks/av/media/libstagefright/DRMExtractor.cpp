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

#include "include/DRMExtractor.h"

#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_PLAYREADY_SUPPORT) 
#define LOG_TAG "DRMExtractor"
#include <media/stagefright/MediaBufferGroup.h>
#include <cutils/properties.h>
#endif
#include <arpa/inet.h>
#include <utils/String8.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/Utils.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaBuffer.h>

#include <drm/drm_framework_common.h>
#include <utils/Errors.h>
#ifdef MTK_AOSP_ENHANCEMENT
// truslet
#ifdef UT_NO_SVP_DRM
#include <stdlib.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <errno.h>
#include <string.h>
#endif

#if defined(PLAYREADY_SVP_TPLAY) && defined(MTK_PLAYREADY_SUPPORT) 
#include <linux/ioctl.h>
#include <fcntl.h>
#define DISP_IOCTL_MAGIC        'x'
#define DISP_IOCTL_SET_TPLAY_HANDLE  _IOW  (DISP_IOCTL_MAGIC, 200, unsigned int)
//#include "mtkfb.h"
#endif
#ifdef MTK_DRM_APP
#include <drm/DrmMtkUtil.h>
#endif
#endif // #ifdef MTK_AOSP_ENHANCEMENT

namespace android {

class DRMSource : public MediaSource {
public:
    DRMSource(const sp<MediaSource> &mediaSource,
            const sp<DecryptHandle> &decryptHandle,
            DrmManagerClient *managerClient,
            int32_t trackId, DrmBuffer *ipmpBox);

    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();
    virtual sp<MetaData> getFormat();
    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_PLAYREADY_SUPPORT) 
    virtual status_t setBuffers(const Vector<MediaBuffer *> &buffers);
    virtual status_t playReadyRead(MediaBuffer **buffer, const ReadOptions *options = NULL); 
#endif  
protected:
    virtual ~DRMSource();

private:
    sp<MediaSource> mOriginalMediaSource;
    sp<DecryptHandle> mDecryptHandle;
    DrmManagerClient* mDrmManagerClient;
    size_t mTrackId;
    mutable Mutex mDRMLock;
    size_t mNALLengthSize;
    bool mWantsNALFragments;

#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_PLAYREADY_SUPPORT) 
    MediaBufferGroup *mGroup;
    MediaBuffer *mBuffer;
#ifdef UT_NO_SVP_DRM
    int mFd;
#endif
#if defined(PLAYREADY_SVP_TPLAY)
    int mFdtplay;
#endif
#endif   // MTK_AOSP_ENHANCEMENT
    DRMSource(const DRMSource &);
    DRMSource &operator=(const DRMSource &);
};

////////////////////////////////////////////////////////////////////////////////

DRMSource::DRMSource(const sp<MediaSource> &mediaSource,
        const sp<DecryptHandle> &decryptHandle,
        DrmManagerClient *managerClient,
        int32_t trackId, DrmBuffer *ipmpBox)
    : mOriginalMediaSource(mediaSource),
      mDecryptHandle(decryptHandle),
      mDrmManagerClient(managerClient),
      mTrackId(trackId),
      mNALLengthSize(0),
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_PLAYREADY_SUPPORT) 
      mBuffer(NULL),
      mGroup(NULL),
#endif
      mWantsNALFragments(false) {
    CHECK(mDrmManagerClient);
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_PLAYREADY_SUPPORT) 
    int32_t IsPlayReady = 0;
    if (getFormat()->findInt32(kKeyIsPlayReady, &IsPlayReady) && IsPlayReady) {
	// descritix limitation, zxy should check(?)
	ALOGI("new DRMSource trackId:%d", trackId);
	mDrmManagerClient->initializeDecryptUnit(
		mDecryptHandle, 0, ipmpBox);
    } else {
	mDrmManagerClient->initializeDecryptUnit(
		mDecryptHandle, trackId, ipmpBox);
    }
#else
    mDrmManagerClient->initializeDecryptUnit(
            mDecryptHandle, trackId, ipmpBox);
#endif

    const char *mime;
    bool success = getFormat()->findCString(kKeyMIMEType, &mime);
    CHECK(success);

    if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_AVC)) {
        uint32_t type;
        const void *data;
        size_t size;
        CHECK(getFormat()->findData(kKeyAVCC, &type, &data, &size));

        const uint8_t *ptr = (const uint8_t *)data;

        CHECK(size >= 7);
        CHECK_EQ(ptr[0], 1);  // configurationVersion == 1

        // The number of bytes used to encode the length of a NAL unit.
        mNALLengthSize = 1 + (ptr[4] & 3);

#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_PLAYREADY_SUPPORT) 
        if (IsPlayReady) {
            ALOGI("playready remove kKeyAVCC");
            getFormat()->remove(kKeyAVCC);
        }
#endif
    }
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_PLAYREADY_SUPPORT) 
#ifdef UT_NO_SVP_DRM
    mFd = open("/dev/mtk_disp", O_RDWR);
    ALOGI("use /dev/mtk_disp map (%d)", mFd);
    if (mFd < 0) {
	ALOGE("open /dev/mtk_disp fail");
    }
#endif
#if defined(PLAYREADY_SVP_TPLAY)
    mFdtplay = open("/dev/mtk_disp", O_RDONLY);
    ALOGI("use /dev/mtk_disp for tplay (%d)", mFdtplay);
    if (mFdtplay < 0) {
	ALOGE("open fail /dev/mtk_disp");
    }
#endif
#endif
}

DRMSource::~DRMSource() {
    Mutex::Autolock autoLock(mDRMLock);
    mDrmManagerClient->finalizeDecryptUnit(mDecryptHandle, mTrackId);
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_PLAYREADY_SUPPORT) 
#ifdef UT_NO_SVP_DRM
    if (mFd > 0) {
        close(mFd);
        ALOGI("close /dev/mtk_disp for mmap (%d)", mFd);
    }
#endif
#if defined(PLAYREADY_SVP_TPLAY)
    if (mFdtplay > 0) {
        close(mFdtplay);
        ALOGI("close /dev/mtk_disp for tplay (%d)", mFdtplay);
        mFdtplay = -1;
    }
#endif
    if (mBuffer != NULL) {
	mBuffer->release();
	mBuffer = NULL;
    }
    if (mGroup != NULL) {
	delete mGroup;
	mGroup = NULL;
    }
#endif
}

status_t DRMSource::start(MetaData *params) {
    int32_t val;
    if (params && params->findInt32(kKeyWantsNALFragments, &val)
        && val != 0) {
        mWantsNALFragments = true;
    } else {
        mWantsNALFragments = false;
    }
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_PLAYREADY_SUPPORT) 
#if defined(TRUSTONIC_TEE_SUPPORT) && defined(MTK_SEC_VIDEO_PATH_SUPPORT) 
    int32_t IsPlayReady = 0;
    if (getFormat()->findInt32(kKeyIsPlayReady, &IsPlayReady) && IsPlayReady) {
	mWantsNALFragments = false;
	ALOGI("SVP do not use nal fragments");
    }
#endif
    ALOGI("mWantsNALFragments :%d", mWantsNALFragments);
#endif

   return mOriginalMediaSource->start(params);
}

status_t DRMSource::stop() {
    return mOriginalMediaSource->stop();
}

#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_PLAYREADY_SUPPORT) 
status_t DRMSource::setBuffers(const Vector<MediaBuffer *> &buffers) {
    mGroup = new MediaBufferGroup;
    for (size_t i = 0; i < buffers.size(); ++i) {
        ALOGI("mGroup add buffer:%d, 0x%08x", i, (buffers.itemAt(i))->data());
	mGroup->add_buffer(buffers.itemAt(i));
    }
    return OK;
}
#endif
sp<MetaData> DRMSource::getFormat() {
    return mOriginalMediaSource->getFormat();
}
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_PLAYREADY_SUPPORT) 
status_t DRMSource::playReadyRead(MediaBuffer **buffer, const ReadOptions *options) {
    status_t err;
        ALOGI("zxy %s(),line:%d", __FUNCTION__, __LINE__);
    if ((err = mOriginalMediaSource->read(buffer, options)) != OK) {
	ALOGI("read EOS %s(),line:%d,err:%d", __FUNCTION__, __LINE__, err);
	return err;
    }
    size_t len = (*buffer)->range_length();
    char *src = (char *)(*buffer)->data() + (*buffer)->range_offset();

    // get iv info
    const sp<MetaData> bufmeta = (*buffer)->meta_data();
    uint32_t type;
    const void *data;
    size_t size;
    CHECK(bufmeta->findData(kKeyCryptoIV, &type, &data, &size));
    uint32_t *pdwEncryptedDataSize = (uint32_t *)((char *)data+20+4);
    DrmBuffer iv((char *)data, size);

    //////// debug
    const char *mime;
    CHECK(getFormat()->findCString(kKeyMIMEType, &mime));

    if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_AVC)) {
        ALOGI("iv size:%d", size);
        uint8_t *data0 = (uint8_t *)data;
        for(int i=0; i<size/4;i++) {
            ALOGV("IV:%02x %02x %02x %02x ", data0[i+0], data0[i+1], data0[i+2], data0[i+3]);
        }
        int64_t lastBufferTimeUs;
        CHECK((*buffer)->meta_data()->findInt64(kKeyTime, &lastBufferTimeUs));
        ALOGI("len:%d, Ts:%lld, enc:%02x %02x %02x %02x %02x", len, lastBufferTimeUs, src[0], src[1], src[2], src[3], src[4]);
    }

    if (mGroup != NULL ) {           // only video would use mGroup
	err = mGroup->acquire_buffer(&mBuffer);
	if (err != OK) {
	    CHECK(mBuffer == NULL);
	    return err;
	}

	if (len > mBuffer->range_length()) {
	    ALOGE("len:%d is too large", len);
	    return -1;
	}
    }
    // decrypt 
    int decryptUnitId = 1;                    // playReady Drm  0: SCP video track  1: normal audio track
    DrmBuffer encryptedDrmBuffer(src, len);
    DrmBuffer decryptedDrmBuffer;
    decryptedDrmBuffer.length = len;
#ifndef UT_NO_SVP_DRM
    if (mGroup == NULL) {
        decryptedDrmBuffer.data = new char[len];
    } else {
        decryptedDrmBuffer.data = new char[4];
        decryptUnitId = 0;
        decryptedDrmBuffer.length = 4;
    }
#else
	decryptedDrmBuffer.data = new char[len];
#endif
#if defined(PLAYREADY_SVP_TPLAY)
	int64_t bufReleaseStart = 0;
	if (mGroup != NULL) {
	    uint32_t sec_pa_tplay = (unsigned long)(mBuffer->data());
	    ALOGI("tPlayer set pa:0x%08x", sec_pa_tplay);
	    bufReleaseStart = systemTime() / 1000;
	    int errIoctl = ioctl(mFdtplay, DISP_IOCTL_SET_TPLAY_HANDLE, &sec_pa_tplay);
	    if (errIoctl != 0) {
		ALOGI("ioctl fail:%s, mFdtplay=%d", strerror(errno), mFdtplay);
	    }
	}
#endif
    DrmBuffer *pDecryptedDrmBuffer = &decryptedDrmBuffer;
    if ((err = mDrmManagerClient->decrypt(mDecryptHandle, decryptUnitId,       
            &encryptedDrmBuffer, &pDecryptedDrmBuffer, &iv)) != NO_ERROR) {

	if (*buffer) {
            ALOGI("decrypt fail buffer release");
            (*buffer)->release();
            *buffer = NULL;
	}
    if (mBuffer != NULL) {
        mBuffer->release();
        mBuffer = NULL;
    }
	delete [] pDecryptedDrmBuffer->data;
	decryptedDrmBuffer.data = NULL;

        return err;
    }
    CHECK(pDecryptedDrmBuffer == &decryptedDrmBuffer);
    // handle output buffer
    if (mGroup == NULL) {
	/*
	   uint32_t sec_pa = 0, sec_size = 0, align = 0;
	   int uree_err = UREE_QuerySecuremem((uint32_t)(mBuffer->data()), &sec_pa, &sec_size, &align);
	   if (uree_err != 0) {
	   ALOGE("UREE_QuerySecuremem fail");
	   return UNKNOWN_ERROR;
	   }
	 */
	memcpy(src, decryptedDrmBuffer.data, decryptedDrmBuffer.length);
	(*buffer)->set_range((*buffer)->range_offset(), decryptedDrmBuffer.length);

	if (decryptedDrmBuffer.data) {
	    delete [] decryptedDrmBuffer.data;
	    decryptedDrmBuffer.data = NULL;
	}
    } else {
#ifdef UT_NO_SVP_DRM
	unsigned long sec_pa = (unsigned long)(mBuffer->data());
    uint64_t sec_pa_64 = (uint64_t)sec_pa;

	int *map_va = (int *)mmap64(NULL, len, PROT_READ | PROT_WRITE, MAP_SHARED, mFd, sec_pa);
	ALOGI("sec_pa:0x%08x, va:0x%08x mFd:%d", sec_pa, map_va, mFd);

	if (map_va == MAP_FAILED) {
	    ALOGE("mmap fail (%s)", strerror(errno));
	    return UNKNOWN_ERROR;
	}
        
	uint8_t *src0 = (uint8_t *)(pDecryptedDrmBuffer->data);
	uint8_t *src1 = (uint8_t *)(map_va);

	ALOGI("len:%d, before map dec:%02x %02x %02x %02x %02x",len , src0[0], src0[1], src0[2], src0[3],src0[4]);
#if 0
	if (!mWantsNALFragments) {          // add nal prefix
	    memcpy((uint8_t *)map_va, "\x00\x00\x00\x01", 4);
	    memcpy(src1+4, pDecryptedDrmBuffer->data, len);
	    mBuffer->set_range(0, decryptedDrmBuffer.length+4);
	    ALOGI("len:%d, Add prefix,dec:%02x %02x %02x %02x %02x %02x %02x",mBuffer->range_length() , src1[0], src1[1], src1[2], src1[3],src1[4],src1[5],src1[6]);
	} else {
#endif
	    memcpy(src1, pDecryptedDrmBuffer->data, len);
	    mBuffer->set_range(0, decryptedDrmBuffer.length);
	    ALOGI("len:%d, dec:%02x %02x %02x %02x %02x %02x %02x",mBuffer->range_length() , src1[0], src1[1], src1[2], src1[3],src1[4],src1[5],src1[6]);
//	}
/*
	for (int i=0; i<len; i++) {
	    if (src1[4+i] != src0[i]) {
		ALOGI("map_va error :%d", src0[i]);
	    }
	}
*/

	if (decryptedDrmBuffer.data) {
	    delete [] decryptedDrmBuffer.data;
	    decryptedDrmBuffer.data = NULL;
	}

	//// dump input buffer
	char value[PROPERTY_VALUE_MAX];
	property_get("playready.dump.decbuf", value, 0);	
	bool dumpDec = atoi(value);
	if (dumpDec) {
	    FILE* fd = fopen("/sdcard/playready.264", "a+");
	    if (fd != NULL) {
                if (mWantsNALFragments) {        // no prefix
		    int32_t header = 0;
		    memcpy(&header,"\x00\x00\x00\x01", 4);
		    fwrite((void*)&header, 1, 4, fd);
		}
		fwrite((void*)src1, 1, mBuffer->range_length() , fd);
		fclose(fd);
	    } else {
                ALOGE("fopen fail");
	    }
	}
#else
	mBuffer->set_range(0, len);            // use encbuffer size
	if (decryptedDrmBuffer.data) {
	    delete [] decryptedDrmBuffer.data;
	    decryptedDrmBuffer.data = NULL;
	}
	int64_t releaseElapse = systemTime() / 1000 - bufReleaseStart;
	if (releaseElapse > 0)  {              // default 6ms
	    ALOGI("track:%d, decrypt(%d) %lld us done", mTrackId, len, releaseElapse);
	}
	//// dump input buffer
	char value[PROPERTY_VALUE_MAX];
	property_get("playready.dump.decbuf", value, 0);	
	bool dumpDec = atoi(value);
	if (dumpDec) {
	    FILE* fd = fopen("/sdcard/playready.264", "a+");
	    if (fd != NULL) {
                if (mWantsNALFragments) {        // no prefix
		    int32_t header = 0;
		    memcpy(&header,"\x00\x00\x00\x01", 4);
		    fwrite((void*)&header, 1, 4, fd);
		}
		fwrite((void*)(mBuffer->data()), 1, mBuffer->range_length() , fd);
		fclose(fd);
	    } else {
                ALOGE("fopen fail");
	    }
	}
#endif
    }
 

    if (mGroup != NULL) {
	// get meta info
	int64_t lastBufferTimeUs, targetSampleTimeUs;
	int32_t isSyncFrame;
	mBuffer->meta_data()->clear();
	CHECK((*buffer)->meta_data()->findInt64(kKeyTime, &lastBufferTimeUs));
	mBuffer->meta_data()->setInt64(kKeyTime, lastBufferTimeUs);


	if ((*buffer)->meta_data()->findInt64(kKeyTargetTime, &targetSampleTimeUs)) {
	    mBuffer->meta_data()->setInt64(
		    kKeyTargetTime, targetSampleTimeUs);
	}
	if ((*buffer)->meta_data()->findInt32(kKeyIsSyncFrame, &isSyncFrame)) {
	    mBuffer->meta_data()->setInt32(kKeyIsSyncFrame, isSyncFrame);
	}

	(*buffer)->release();
	*buffer = mBuffer;
	mBuffer = NULL;
    }

    return OK;
}
#endif
status_t DRMSource::read(MediaBuffer **buffer, const ReadOptions *options) {
    Mutex::Autolock autoLock(mDRMLock);
#if defined(MTK_AOSP_ENHANCEMENT) && defined(MTK_PLAYREADY_SUPPORT) 
    // playready read
    int32_t IsPlayReady = 0;
    if (getFormat()->findInt32(kKeyIsPlayReady, &IsPlayReady) && IsPlayReady) {
        return playReadyRead(buffer, options);
    }
#endif
    status_t err;
    if ((err = mOriginalMediaSource->read(buffer, options)) != OK) {
        return err;
    }

    size_t len = (*buffer)->range_length();

    char *src = (char *)(*buffer)->data() + (*buffer)->range_offset();

    DrmBuffer encryptedDrmBuffer(src, len);
    DrmBuffer decryptedDrmBuffer;
    decryptedDrmBuffer.length = len;
    decryptedDrmBuffer.data = new char[len];
    DrmBuffer *pDecryptedDrmBuffer = &decryptedDrmBuffer;

    if ((err = mDrmManagerClient->decrypt(mDecryptHandle, mTrackId,
            &encryptedDrmBuffer, &pDecryptedDrmBuffer)) != NO_ERROR) {

        if (decryptedDrmBuffer.data) {
            delete [] decryptedDrmBuffer.data;
            decryptedDrmBuffer.data = NULL;
        }

        return err;
    }
    CHECK(pDecryptedDrmBuffer == &decryptedDrmBuffer);

    const char *mime;
    CHECK(getFormat()->findCString(kKeyMIMEType, &mime));

    if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_AVC) && !mWantsNALFragments) {
        uint8_t *dstData = (uint8_t*)src;
        size_t srcOffset = 0;
        size_t dstOffset = 0;

        len = decryptedDrmBuffer.length;
        while (srcOffset < len) {
            CHECK(srcOffset + mNALLengthSize <= len);
            size_t nalLength = 0;
            const uint8_t* data = (const uint8_t*)(&decryptedDrmBuffer.data[srcOffset]);

            switch (mNALLengthSize) {
                case 1:
                    nalLength = *data;
                    break;
                case 2:
                    nalLength = U16_AT(data);
                    break;
                case 3:
                    nalLength = ((size_t)data[0] << 16) | U16_AT(&data[1]);
                    break;
                case 4:
                    nalLength = U32_AT(data);
                    break;
                default:
                    CHECK(!"Should not be here.");
                    break;
            }

            srcOffset += mNALLengthSize;

            size_t end = srcOffset + nalLength;
            if (end > len || end < srcOffset) {
                if (decryptedDrmBuffer.data) {
                    delete [] decryptedDrmBuffer.data;
                    decryptedDrmBuffer.data = NULL;
                }

                return ERROR_MALFORMED;
            }

            if (nalLength == 0) {
                continue;
            }

            if (dstOffset > SIZE_MAX - 4 ||
                dstOffset + 4 > SIZE_MAX - nalLength ||
                dstOffset + 4 + nalLength > (*buffer)->size()) {
                (*buffer)->release();
                (*buffer) = NULL;
                if (decryptedDrmBuffer.data) {
                    delete [] decryptedDrmBuffer.data;
                    decryptedDrmBuffer.data = NULL;
                }
                return ERROR_MALFORMED;
            }

            dstData[dstOffset++] = 0;
            dstData[dstOffset++] = 0;
            dstData[dstOffset++] = 0;
            dstData[dstOffset++] = 1;
            memcpy(&dstData[dstOffset], &decryptedDrmBuffer.data[srcOffset], nalLength);
            srcOffset += nalLength;
            dstOffset += nalLength;
        }

        CHECK_EQ(srcOffset, len);
        (*buffer)->set_range((*buffer)->range_offset(), dstOffset);

    } else {
        memcpy(src, decryptedDrmBuffer.data, decryptedDrmBuffer.length);
        (*buffer)->set_range((*buffer)->range_offset(), decryptedDrmBuffer.length);
    }

    if (decryptedDrmBuffer.data) {
        delete [] decryptedDrmBuffer.data;
        decryptedDrmBuffer.data = NULL;
    }

    return OK;
}

////////////////////////////////////////////////////////////////////////////////

DRMExtractor::DRMExtractor(const sp<DataSource> &source, const char* mime)
    : mDataSource(source),
      mDecryptHandle(NULL),
      mDrmManagerClient(NULL) {
    mOriginalExtractor = MediaExtractor::Create(source, mime);
    mOriginalExtractor->setDrmFlag(true);
    mOriginalExtractor->getMetaData()->setInt32(kKeyIsDRM, 1);

    source->getDrmInfo(mDecryptHandle, &mDrmManagerClient);
}

DRMExtractor::~DRMExtractor() {
}

size_t DRMExtractor::countTracks() {
    return mOriginalExtractor->countTracks();
}

sp<MediaSource> DRMExtractor::getTrack(size_t index) {
    sp<MediaSource> originalMediaSource = mOriginalExtractor->getTrack(index);
    originalMediaSource->getFormat()->setInt32(kKeyIsDRM, 1);

    int32_t trackID;
    CHECK(getTrackMetaData(index, 0)->findInt32(kKeyTrackID, &trackID));

    DrmBuffer ipmpBox;
    ipmpBox.data = mOriginalExtractor->getDrmTrackInfo(trackID, &(ipmpBox.length));
    CHECK(ipmpBox.length > 0);

    return new DRMSource(originalMediaSource, mDecryptHandle, mDrmManagerClient,
            trackID, &ipmpBox);
}

sp<MetaData> DRMExtractor::getTrackMetaData(size_t index, uint32_t flags) {
    return mOriginalExtractor->getTrackMetaData(index, flags);
}

sp<MetaData> DRMExtractor::getMetaData() {
    return mOriginalExtractor->getMetaData();
}

bool SniffDRM(
    const sp<DataSource> &source, String8 *mimeType, float *confidence,
        sp<AMessage> *) {
    sp<DecryptHandle> decryptHandle = source->DrmInitialization();

    if (decryptHandle != NULL) {
        if (decryptHandle->decryptApiType == DecryptApiType::CONTAINER_BASED) {
#ifdef MTK_AOSP_ENHANCEMENT

#ifdef MTK_DRM_APP
            *mimeType =
                String8("drm+container_based+")
                + DrmMtkUtil::toCommonMime(decryptHandle->mimeType);
            // OMA DRM v1 implementation: the confidence is set to 0.01f
            //   so that it's smaller that any other confidence value
            ALOGD("SniffDRM: this is an OMA DRM v1 file");
            *confidence = 0.01f;
#else // MTK_DRM_APP
            *mimeType = String8("drm+container_based+") + decryptHandle->mimeType;
            *confidence = 10.0f;
#endif // MTK_DRM_APP

#else // #ifdef MTK_AOSP_ENHANCEMENT
            *mimeType = String8("drm+container_based+") + decryptHandle->mimeType;
            *confidence = 10.0f;
#endif // #ifdef MTK_AOSP_ENHANCEMENT

        } else if (decryptHandle->decryptApiType == DecryptApiType::ELEMENTARY_STREAM_BASED) {
            *mimeType = String8("drm+es_based+") + decryptHandle->mimeType;
            *confidence = 10.0f;
        } else {
            return false;
        }

        return true;
    }

    return false;
}
} //namespace android

