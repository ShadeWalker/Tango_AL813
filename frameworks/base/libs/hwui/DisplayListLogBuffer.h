/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2011 The Android Open Source Project
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

#ifndef ANDROID_HWUI_DISPLAY_LIST_LOG_BUFFER_H
#define ANDROID_HWUI_DISPLAY_LIST_LOG_BUFFER_H

#include <utils/Singleton.h>

#include <stdio.h>

/// M: performance index enhancements
#include <utils/KeyedVector.h>

namespace android {
namespace uirenderer {

class OpenGLRenderer;

class DisplayListLogBuffer: public Singleton<DisplayListLogBuffer> {
    DisplayListLogBuffer();
    ~DisplayListLogBuffer();

    friend class Singleton<DisplayListLogBuffer>;

public:
    void writeCommand(int level, const char* label);
    void outputCommands(FILE *file);

    bool isEmpty() {
        return (mStart == mEnd);
    }

    struct OpLog {
        int level;
        const char* label;
        nsecs_t start;
        nsecs_t end;
    };

    /// M: performance index enhancements
    struct OpEntry {
        const char* mName;
        int mCount;
        nsecs_t mMaxDuration;
        nsecs_t mTotalDuration;
        nsecs_t mLastDuration;

        OpEntry():
           mName(NULL), mCount(0), mMaxDuration(0), mTotalDuration(0), mLastDuration(0) {
        }

        OpEntry(const char* name, int count, nsecs_t duration):
           mName(name), mCount(count), mMaxDuration(duration), mTotalDuration(duration), mLastDuration(duration) {
        }
        ~OpEntry(){}
    };

    /// M: Write before/after a command to log it's executing time,
    /// it's helpful to find who is running and to check ANR
    OpLog* writeCommandStart(int level, const char* label);
    nsecs_t writeCommandEnd(OpLog* op);
    bool checkIsAllFinished(const char* command, int timeoutNs = 0);
    void preFlush();
    void postFlush();

    /// M: ops may use current renderer to output more info
    OpenGLRenderer* currentRenderer;

private:
    OpLog* mBufferFirst; // where the memory starts
    OpLog* mStart;       // where the current command stream starts
    OpLog* mEnd;         // where the current commands end
    OpLog* mBufferLast;  // where the buffer memory ends

    /// M: performance index enhancements
    void outputCommandsInternal(FILE *file = NULL);

    KeyedVector<const char*, OpEntry> mOpBuffer;
    KeyedVector<const char*, OpEntry> mOpBufferPerFrame;
    bool mIsLogCommands;
    nsecs_t mLastCheckTime;
    Mutex mLock;
};

}; // namespace uirenderer
}; // namespace android

#endif // ANDROID_HWUI_DISPLAY_LIST_LOG_BUFFER_H
