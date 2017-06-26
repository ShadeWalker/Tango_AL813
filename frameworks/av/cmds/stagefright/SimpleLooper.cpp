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
#define LOG_TAG "SimpleLooper"
#include <utils/Log.h>

#include "SimpleLooper.h"

#include <gui/Surface.h>
#include <media/AudioTrack.h>
#include <media/ICrypto.h>
#include <media/IMediaHTTPService.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/MediaCodec.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/NativeWindowWrapper.h>
#include <media/stagefright/NuMediaExtractor.h>

namespace android {

SimpleLooper::SimpleLooper()
{
	mCounter = 0;
}

SimpleLooper::~SimpleLooper() {
}

Condition gEndCondition;
Mutex gLock;
uint32_t gEndCount = 0;

void SimpleLooper::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
        case kWhatGotMessage:
        {
			int32_t dummy = 0;
			//ALOGD("Counter %d", ++mCounter);
			++mCounter;
			msg->findInt32("dummy1", &dummy);
			msg->findInt32("dummy2", &dummy);
			msg->findInt32("dummy3", &dummy);
			if (mCounter == gEndCount){
				gEndCondition.broadcast();
				ALOGD("Recieved %d messages b4 signal", mCounter);
			}
            break;
        }

        default:
            TRESPASS();
    }
}


}  // namespace android

int main(int argc, char **argv) {
	using namespace android;

	sp<SimpleLooper> sl1 =  new SimpleLooper;
	sp<ALooper> looper1 = new ALooper;
	looper1->setName("TestLooper1");
	looper1->start(false, false, ANDROID_PRIORITY_AUDIO);
	looper1->registerHandler(sl1);
	
	uint32_t looperId = sl1->id();

	uint32_t maxCount = 1000;
	if (argc >= 2){
		maxCount = atoi(argv[1]);
	}
	gEndCount = maxCount;
	if (maxCount != 0){
		ALOGD("Ready to send %d AMessages", maxCount);
		uint32_t cnt = 0;
		for(;cnt < maxCount;++cnt){
			sp<AMessage> msg = new AMessage(kWhatGotMessage, looperId);
			int32_t dummy = 3;
			msg->setInt32("dummy1", dummy);
			msg->setInt32("dummy2", dummy);
			msg->setInt32("dummy3", dummy);
			msg->post(0);
		}
		ALOGD("Done at %d", cnt);
	}
	else{
		ALOGD("Directly return since maxCount is %d", maxCount);
	}

	//Waiting for TestLooper1 to finish
	{
		Mutex::Autolock autoLock(gLock);
		gEndCondition.wait(gLock);
	}
	ALOGD("Exit");

	return 0;
}
