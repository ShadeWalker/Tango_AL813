/*
**
** Copyright 2008, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

//#define LOG_NDEBUG 0
#define LOG_TAG "IMetadataCallbacks"
#include <utils/Log.h>
#include <stdint.h>
#include <sys/types.h>

#include <binder/Parcel.h>
#include <gui/IGraphicBufferProducer.h>
#include <gui/Surface.h>
#include <utils/Mutex.h>

#include <camera/IMetadataCallbacks.h>
#include <camera/CameraMetadata.h>

namespace android {

enum {
    METADATA_RECEIVED = IBinder::FIRST_CALL_TRANSACTION,
    
};

class BpMetadataCallbacks: public BpInterface<IMetadataCallbacks>
{
public:
    BpMetadataCallbacks(const sp<IBinder>& impl)
        : BpInterface<IMetadataCallbacks>(impl)
    {
    }

    void onMetadataReceived(CameraMetadata& result,
            CameraMetadata& charateristic) {
        ALOGV("onMetadataReceived");
        Parcel data, reply;
        data.writeInterfaceToken(IMetadataCallbacks::getInterfaceDescriptor());
        data.writeInt32(1); // to mark presence of metadata object
        result.writeToParcel(&data);
        data.writeInt32(1); // to mark presence of CaptureResult object
        charateristic.writeToParcel(&data);
        remote()->transact(METADATA_RECEIVED, data, &reply, IBinder::FLAG_ONEWAY);
        data.writeNoException();
    }
};

IMPLEMENT_META_INTERFACE(MetadataCallbacks, "android.hardware.IMetadataCallbacks");

// ----------------------------------------------------------------------

status_t BnMetadataCallbacks::onTransact(
    uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    switch(code) {
        case METADATA_RECEIVED: {
            ALOGV("onMetadataReceived");
            CHECK_INTERFACE(IMetadataCallbacks, data, reply);
            CameraMetadata result;
            if (data.readInt32() != 0) {
                result.readFromParcel(const_cast<Parcel*>(&data));
            } else {
                ALOGW("No result metadata object is present in result");
            }
            CameraMetadata charateristic;
            if (data.readInt32() != 0) {
                charateristic.readFromParcel(const_cast<Parcel*>(&data));
            } else {
                ALOGW("No charateristic object is present in result");
            }
            onMetadataReceived(result, charateristic);
            data.readExceptionCode();
            return NO_ERROR;
        } break;
        default:
            return BBinder::onTransact(code, data, reply, flags);
    }
}

// ----------------------------------------------------------------------------

}; // namespace android
