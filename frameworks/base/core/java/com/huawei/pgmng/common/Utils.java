/*create DTS2014112601808 huangwen 00181596 20141126*/
/*
 * Copyright (C) 2012 The Huawei Android Project
 * Power Consumption Team
 * 
 */
package com.huawei.pgmng.common;

import com.huawei.pgmng.log.LogPower;
import android.os.WorkSource;
import android.util.Log;
import android.util.Slog;

public final class Utils {

    private static final String TAG = "PG Utils";

    /* huawei add for LCD function start */
    private static final int mRatioMaxBrightness = 185;
    private static final int mRatioMinBrightness = 35;

    public static int getRatioBright(int bright, double ratio) {
        if(bright < mRatioMinBrightness) {
            return bright;
        } else if(bright < mRatioMaxBrightness ) {
            bright = (int)(bright * ratio);
            if(bright < mRatioMinBrightness)
                bright = mRatioMinBrightness;
            return bright;
        } else {
            return bright;
        }
    }

    public static int getAutoAdjustBright(int bright) {
        if(bright < mRatioMinBrightness){  
            return bright;
        }
        else if(bright < 110){
            return bright-(bright-mRatioMinBrightness)*3/10;
        }
        else if(bright < mRatioMaxBrightness){
            return bright-(mRatioMaxBrightness-bright)*3/10;
        }
        else{
           return bright;
        }
    }
    /* huawei add for LCD function end */
    //frameworks\base\services\core\java\com\android\server\power\Notifier.java
    public static void noteWakelock(int flags, String tag, int ownerUid, int ownerPid,
            WorkSource workSource, int eventTag) {

        if (workSource != null) {
            int N = workSource.size();
            for (int i=0; i<N; i++) {
                ownerUid = workSource.get(i);
                if ((ownerUid != 1000) && (ownerUid != 1001)) {//discard SYSTEM_UID or PHONE_UID
                    LogPower.push(eventTag, Integer.toString(ownerUid),
                            Integer.toString(flags), Integer.toString(ownerPid), new String[] {tag});
                }
            }
        } else {
            if ((ownerUid != 1000) && (ownerUid != 1001)) {//discard SYSTEM_UID or PHONE_UID
                LogPower.push(eventTag, Integer.toString(ownerUid),
                        Integer.toString(flags), Integer.toString(ownerPid), new String[] {tag});
            }
        }
    }

}
