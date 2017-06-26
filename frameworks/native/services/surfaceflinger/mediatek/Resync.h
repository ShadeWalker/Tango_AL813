#ifndef ANDROID_RESYNC_H
#define ANDROID_RESYNC_H

#include <stddef.h>

#include <utils/Mutex.h>
#include <utils/Timers.h>
#include <utils/RefBase.h>

namespace android {

class SurfaceFlinger;

class Resync : public LightRefBase<Resync>
{
public:
    Resync(SurfaceFlinger* flinger, int dpy);
    ~Resync();

    nsecs_t computeNextEventTimeLocked(nsecs_t phase, nsecs_t last, nsecs_t ref);

    void updateModelLocked(nsecs_t period, nsecs_t phase);

    void updateSyncTimeLocked(int num, nsecs_t sync);

    void setSyncSampleNumLocked(int num);

    nsecs_t queryPeriodLocked();

private:
    nsecs_t mPeriod;
    nsecs_t mPhase;
};

}

#endif // ANDROID_DISPSYNC_H
