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

#include <media/stagefright/foundation/AHandler.h>
#include <media/stagefright/foundation/AString.h>
#include <utils/KeyedVector.h>

namespace android {

struct ABuffer;
struct ALooper;
struct AudioTrack;
struct IGraphicBufferProducer;
struct MediaCodec;
struct NativeWindowWrapper;
struct NuMediaExtractor;

enum {
	kWhatGotMessage = 0
};

struct SimpleLooper : public AHandler {
    SimpleLooper();

protected:
    virtual ~SimpleLooper();

    virtual void onMessageReceived(const sp<AMessage> &msg);

private:
	uint32_t mCounter;

    DISALLOW_EVIL_CONSTRUCTORS(SimpleLooper);
};

}  // namespace android
