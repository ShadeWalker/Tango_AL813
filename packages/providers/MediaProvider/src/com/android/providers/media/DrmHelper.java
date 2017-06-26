package com.android.providers.media;

import static com.android.providers.media.MediaUtils.IS_SUPPORT_DRM;

import java.util.HashSet;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.os.Process;
import android.util.SparseIntArray;

/**
 * Helper class used to set and check out which process can access DRM files.
 *
 */
public class DrmHelper {
    private static final SparseIntArray sPermitState = new SparseIntArray(32);
    private static final HashSet<String> sDrmPermitProcessList = new HashSet<String>(16);
    private static final int STATE_PERMIT_ACCESS_DRM = 1 << 0;
    private static final int STATE_PERMIT_ALL = STATE_PERMIT_ACCESS_DRM;

    static {
        initDrmPermistProcessList();
    }

    /**
     * Check whether the given process can access DRM files.
     *
     * @param context Context
     * @param pid Process id
     * @return If the process have been permitted to access DRM files return true, otherwise false.
     */
    public static boolean isPermitedAccessDrm(Context context, int pid) {
       /// M: Add for huawei, no need drm white list check		
        return true;
        //return IS_SUPPORT_DRM && ((getPermittedState(context, pid) & STATE_PERMIT_ACCESS_DRM) > 0);
    }

    /**
     * Get permit state by pid.
     *
     * @param context
     * @param pid
     * @return permit state
     */
    private synchronized static int getPermittedState(Context context, int pid) {
        int permitState = sPermitState.get(pid, -1);
        if (permitState < 0) {
            sPermitState.clear();
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<RunningAppProcessInfo> list = am.getRunningAppProcesses();
            for (RunningAppProcessInfo runInfo : list) {
                /// Current process android.process.media have all permit
                if (runInfo.pid == Process.myPid()) {
                    sPermitState.put(runInfo.pid, STATE_PERMIT_ALL);
                    continue;
                }
                permitState = 0;
                if (sDrmPermitProcessList.contains(runInfo.processName)) {
                    permitState |= STATE_PERMIT_ACCESS_DRM;
                }
                sPermitState.put(runInfo.pid, permitState);
            }
            permitState = sPermitState.get(pid, 0);
        }
        return permitState;
    }

    private static void initDrmPermistProcessList() {
        String[] permitProcessNames = new String[] {
                "com.android.music",
                "com.android.gallery",
                "com.android.gallery:CropImage",
                "com.cooliris.media",
                "com.mediatek.videoplayer",
                "com.mediatek.videoplayer2",
                "com.android.settings",
                "com.android.gallery3d",
                "com.android.gallery3d:crop",
                "com.android.gallery3d:WidgetService",
                "com.android.deskclock",
                "com.android.mms",
                "system"
        };
        for (String processName : permitProcessNames) {
            sDrmPermitProcessList.add(processName);
        }
    }
}
