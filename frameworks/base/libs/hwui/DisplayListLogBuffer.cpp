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

#include "DisplayListLogBuffer.h"

/// M: performance index enhancements
#include <cutils/properties.h>
#include "Debug.h"

/// M: log render tasks
#include "renderthread/RenderThread.h"
#include <utils/String8.h>

// BUFFER_SIZE size must be one more than a multiple of COMMAND_SIZE to ensure
// that mStart always points at the next command, not just the next item
#if defined(MTK_DEBUG_RENDERER)
#define NUM_COMMANDS 200
#else
#define NUM_COMMANDS 50
#endif
#define BUFFER_SIZE ((NUM_COMMANDS) + 1)

/**
 * DisplayListLogBuffer is a utility class which logs the most recent display
 * list operations in a circular buffer. The log is process-wide, because we
 * only care about the most recent operations, not the operations on a per-window
 * basis for a given activity. The purpose of the log is to provide more debugging
 * information in a bug report, by telling us not just where a process hung (which
 * generally is just reported as a stack trace at the Java level) or crashed, but
 * also what happened immediately before that hang or crash. This may help track down
 * problems in the native rendering code or driver interaction related to the display
 * list operations that led up to the hang or crash.
 *
 * The log is implemented as a circular buffer for both space and performance
 * reasons - we only care about the last several operations to give us context
 * leading up to the problem, and we don't want to constantly copy data around or do
 * additional mallocs to keep the most recent operations logged. Only numbers are
 * logged to make the operation fast. If and when the log is output, we process this
 * data into meaningful strings.
 *
 * There is an assumption about the format of the command (currently 2 ints: the
 * opcode and the nesting level). If the type of information logged changes (for example,
 * we may want to save a timestamp), then the size of the buffer and the way the
 * information is recorded in writeCommand() should change to suit.
 */

namespace android {

#ifdef USE_OPENGL_RENDERER
using namespace uirenderer;
ANDROID_SINGLETON_STATIC_INSTANCE(DisplayListLogBuffer);
#endif

namespace uirenderer {

/**
 * M: performance index enhancements, dump execution time of each operation every frame
 * "1" and "0". The default value is "0".
 */
#define PROPERTY_DEBUG_COMMANDS_DURATION "debug.hwui.log.duration"

DisplayListLogBuffer::DisplayListLogBuffer() {
    mBufferFirst = (OpLog*) malloc(BUFFER_SIZE * sizeof(OpLog));
    mStart = mBufferFirst;
    mBufferLast = mBufferFirst + BUFFER_SIZE - 1;
    mEnd = mStart;
}

DisplayListLogBuffer::~DisplayListLogBuffer() {
    free(mBufferFirst);
}

/**
 * Called from DisplayListRenderer to output the current buffer into the
 * specified FILE. This only happens in a dumpsys/bugreport operation.
 */
void DisplayListLogBuffer::outputCommands(FILE *file)
{
    OpLog* tmpBufferPtr = mStart;
    while (true) {
        if (tmpBufferPtr == mEnd) {
            break;
        }

        fprintf(file, "%*s%s\n", 2 * tmpBufferPtr->level, "", tmpBufferPtr->label);

        OpLog* nextOp = tmpBufferPtr++;
        if (tmpBufferPtr > mBufferLast) {
            tmpBufferPtr = mBufferFirst;
        }
    }

    outputCommandsInternal(file);
}

/**
 * Store the given level and label in the buffer and increment/wrap the mEnd
 * and mStart values as appropriate. Label should point to static memory.
 *
 * M: Do not use this API directlly, it's not thread safe!!
 * Use writeCommandStart(level, label) instead.
 */
void DisplayListLogBuffer::writeCommand(int level, const char* label) {
    mEnd->level = level;
    mEnd->label = label;

    if (mEnd == mBufferLast) {
        mEnd = mBufferFirst;
    } else {
        mEnd++;
    }
    if (mEnd == mStart) {
        mStart++;
        if (mStart > mBufferLast) {
            mStart = mBufferFirst;
        }
    }
}

DisplayListLogBuffer::OpLog* DisplayListLogBuffer::writeCommandStart(int level, const char* label) {
    Mutex::Autolock _l(mLock);
    OpLog* op = mEnd;
    op->start = 0;
    op->end = 0;

#if defined(MTK_DEBUG_RENDERER)
    // level < 0 is a hint that we are using TIME_LOG
    if (level < 0) {
        level = 0;
        op->start = systemTime(SYSTEM_TIME_MONOTONIC);
    }
#endif

    writeCommand(level, label);
    return op;
}

nsecs_t DisplayListLogBuffer::writeCommandEnd(OpLog* op) {
    Mutex::Autolock _l(mLock);
    op->end = systemTime(SYSTEM_TIME_MONOTONIC);
    nsecs_t duration = op->end - op->start;

    KeyedVector<const char*, OpEntry>* buffers[] = {&mOpBuffer, &mOpBufferPerFrame};
    bool needUpdate[] = {true, mIsLogCommands};

    for (int i = 0; i < 2; i++) {
        if (needUpdate[i]) {
            KeyedVector<const char*, OpEntry> &buffer = *(buffers[i]);
            ssize_t index = buffer.indexOfKey(op->label);
            if (index >= 0) {
                OpEntry& item = buffer.editValueAt(index);
                if (item.mTotalDuration < INT64_MAX - duration) {
                    item.mCount++;
                    item.mMaxDuration = duration > item.mMaxDuration ? duration : item.mMaxDuration;
                    item.mTotalDuration += duration;
                } else { // avoid overflow
                    item.mCount = 1;
                    item.mMaxDuration = duration;
                    item.mTotalDuration = duration;
                }
                item.mLastDuration = duration;
            } else {
                OpEntry entry(op->label, 1, duration);
                buffer.add(op->label, entry);
            }
        }
    }

    bool blocked = (mLastCheckTime != 0 && op->end > mLastCheckTime);
    if (duration > g_HWUI_debug_anr_ns || blocked) {
        mLastCheckTime = 0;
        ALOGD("[ANR Warning] %s %" PRId64 "ms%s",
            op->label, nanoseconds_to_milliseconds(duration),
            blocked ? " blocked others" : " ");
    }
    return duration;
}

bool DisplayListLogBuffer::checkIsAllFinished(const char* command, int timeoutNs) {
    bool allFinished = true;
#if defined(MTK_DEBUG_RENDERER)
    Mutex::Autolock _l(mLock);
    mLastCheckTime = 0;
    OpLog* tmpBufferPtr = mEnd;
    nsecs_t current = systemTime(SYSTEM_TIME_MONOTONIC);
    int maxNum = 5;
    int index = 0;
    maxNum = maxNum < NUM_COMMANDS ? maxNum : NUM_COMMANDS / 10;
    while (index < maxNum) {
        if (tmpBufferPtr == mStart) {
            break;
        }

        tmpBufferPtr--;
        if (tmpBufferPtr < mBufferFirst) {
            tmpBufferPtr = mBufferLast;
        }

        if (tmpBufferPtr->start != 0 && tmpBufferPtr->end == 0) {
            nsecs_t runNano = current - tmpBufferPtr->start;
            if (runNano > timeoutNs) {
                if (allFinished == true) {
                    mLastCheckTime = current;
                    ALOGD("[ANR Warning] task (%s) is waiting for:", command);
                }
                allFinished = false;
                ALOGD("  (%s) run from %" PRId64 " to %" PRId64 " (%" PRId64 "ms) but not finished yet!!",
                    tmpBufferPtr->label, tmpBufferPtr->start, current,
                    nanoseconds_to_milliseconds(runNano));
                index++;
            }
        }
    }

    if (renderthread::RenderThread::hasInstance()) {
        String8 taskLog;
        renderthread::RenderThread::getInstance().dumpTaskQueue(taskLog, timeoutNs);
        if (taskLog.size() != 0) {
            if (allFinished)
                ALOGD("[ANR Warning] task (%s) is waiting for:", command);
            ALOGD("%s", taskLog.string());
        }
    }
#endif
    return allFinished;
}

void DisplayListLogBuffer::outputCommandsInternal(FILE *file) {
    if (mIsLogCommands || file) {
        KeyedVector<const char*, OpEntry> &ops = file == NULL ? mOpBufferPerFrame : mOpBuffer;

        size_t count = ops.size();
        if (count == 0) return;
#undef LOG_TAG
#define LOG_TAG "DisplayListLogBuffer"

        if (file)
            fprintf(file, "\n%-25s  %10s  %10s  %10s  %10s  %10s\n",
                "(ms)", "total", "count", "average", "max", "last");
        else
            ALOGD("%-25s  %10s  %10s  %10s  %10s  %10s",
                "(ms)", "total", "count", "average", "max", "last");

        Vector<OpEntry> list;
        list.add(ops.valueAt(0));

        for (size_t i = 1; i < count; i++) {
            OpEntry entry = ops.valueAt(i);
            size_t index = list.size();
            size_t size = list.size();
            for (size_t j = 0; j < size; j++) {
                OpEntry e = list.itemAt(j);
                if (entry.mTotalDuration > e.mTotalDuration) {
                    index = j;
                    break;
                }
           }
           list.insertAt(entry, index);
        }

        for (size_t i = 0; i < count; i++) {
            OpEntry entry = list.itemAt(i);
            const char* current = entry.mName;
            float total = entry.mTotalDuration / 1000000.0f;
            int count = entry.mCount;
            float max = entry.mMaxDuration / 1000000.0f;
            float average = total / count;
            float last = entry.mLastDuration / 1000000.0f;
            if (file)
                fprintf(file, "%-25s  %10.2f  %10d  %10.2f  %10.2f  %10.2f\n",
                    current, total, count, average, max, last);
            else
                ALOGD("%-25s  %10.2f  %10d  %10.2f  %10.2f  %10.2f",
                    current, total, count, average, max, last);
        }

#undef LOG_TAG
#define LOG_TAG "OpenGLRenderer"
    }
}

void DisplayListLogBuffer::preFlush() {
#if defined(MTK_DEBUG_RENDERER)
    char value[PROPERTY_VALUE_MAX];
    property_get(PROPERTY_DEBUG_COMMANDS_DURATION, value, "");
    mIsLogCommands = (strcmp(value, "1") == 0) ? true : false;
    if(mIsLogCommands) {
        mOpBufferPerFrame.clear();
    }
#endif
}

void DisplayListLogBuffer::postFlush() {
#if defined(MTK_DEBUG_RENDERER)
    outputCommandsInternal();
#endif
}

}; // namespace uirenderer
}; // namespace android
