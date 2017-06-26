/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2013 The Android Open Source Project
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

#ifndef ANDROID_HWUI_TASK_H
#define ANDROID_HWUI_TASK_H

#if defined(HAVE_PTHREADS)
#include <sys/resource.h>
#endif

#include <utils/RefBase.h>
#include <utils/Trace.h>

#include "Future.h"

#include "Debug.h"

namespace android {
namespace uirenderer {

class TaskBase: public RefBase {
public:
    TaskBase() {
#if defined(HAVE_PTHREADS)
        mTid = -1;
#endif
    }
    virtual ~TaskBase() { }

#if defined(HAVE_PTHREADS)
    pid_t mTid;   /// M: tid for setting thread priority
#endif
};

template<typename T>
class Task: public TaskBase {
public:
    Task(): mFuture(new Future<T>()) { }
    virtual ~Task() { }

    T getResult() const {
        ScopedTrace tracer(ATRACE_TAG_VIEW, "waitForTask");

        /// M: increase hwuiTask priority to avoid blocking RenderThread
        int pri;
        bool check = getPriorityInt(mTid, pri);
        if (check) {
            check = setPriorityInt(mTid, PRIORITY_URGENT_DISPLAY);
        }

        T ret = mFuture->get();

        /// M: rollback hwuiTask priority
        if (check) {
            setPriorityInt(mTid, pri);
        }

        return ret;
    }

    void setResult(T result) {
        mFuture->produce(result);
    }

protected:
    const sp<Future<T> >& future() const {
        return mFuture;
    }

private:
    /// M: Dynamically adjust hwuiTask priority to avoid blocking RenderThread
    bool setPriorityInt(pid_t tId, int pri) const {
#if defined(HAVE_PTHREADS)
        if (tId > 0) {
            if (g_HWUI_debug_shadow || g_HWUI_debug_paths) {
                ALOGD("set task priority of %d to %d", tId, pri);
            }
            setpriority(PRIO_PROCESS, tId, pri);
            return true;
        }
#endif
        return false;
    }

    /// M: Dynamically adjust hwuiTask priority to avoid blocking RenderThread
    bool getPriorityInt(pid_t tId, int &pri) const {
#if defined(HAVE_PTHREADS)
        if (tId > 0) {
            pri = getpriority(PRIO_PROCESS, tId);
            if (g_HWUI_debug_shadow || g_HWUI_debug_paths) {
                ALOGD("get task priority of %d = %d", tId, pri);
            }
            return true;
        }
#endif
        return false;
    }

    sp<Future<T> > mFuture;
};

}; // namespace uirenderer
}; // namespace android

#endif // ANDROID_HWUI_TASK_H
