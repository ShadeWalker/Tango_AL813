/*create DTS2014112601808 huangwen 00181596 20141126*/
/*
 * Copyright (C) 2012 The Huawei Android Project
 * Power Consumption Team
 * 
 */
package com.huawei.pgmng.log;

import android.util.Log;
import android.os.SystemProperties;
import com.android.internal.os.RuntimeInit;

public final class LogPower {

    public static final int WEBPAGE_FINISHED = 105;
    public static final int GAMEOF3D_RESUMED = 106;
    public static final int GAMEOF3D_PAUSED = 107;
    public static final int ALL_DOWNLOAD_FINISH = 110;
    public static final int APP_RUN_FRONT = 113;
    public static final int APP_RUN_BG = 114;
    public static final int KEYBOARD_SHOW = 117;
    public static final int KEYBOARD_HIDE = 118;
    public static final int FULL_SCREEN = 120;
    public static final int ALARM_START = 121;
    public static final int NOTIFICATION_CANCEL = 123;
    public static final int START_CHG_ROTATION = 128;
    public static final int END_CHG_ROTATION = 130;

    public static final int FULL_SCREEN_END = 135;
    public static final int APP_START_SPEEDUP = 139;
    public static final int SURFACEVIEW_CREATED = 141;
    public static final int SURFACEVIEW_DESTROYED = 142;
    public static final int AUDIO_START = 147;
    public static final int ADD_VIEW = 151;
    public static final int REMOVE_VIEW = 152;
    public static final int FLING_START = 154;
    public static final int FLING_FINISH = 155;

    public static final int GPS_START = 156;
    public static final int GPS_END = 157;
    public static final int WIFI_SCAN_START = 158;
    public static final int WIFI_SCAN_END = 159;
    public static final int WAKELOCK_ACQUIRED = 160;
    public static final int WAKELOCK_RELEASED = 161;
    public static final int ENABLE_SENSOR = 143;//fengyaling modified for huawei feedback
    public static final int DISABLE_SENSOR = 144;//fengyaling modified for huawei feedback

    public static final int TEST_FOR_CHANNEL = 100000;

    //log switch control.
    private static final int TRY_MAX_NUM = 5000;
    private static int TRY_COUNT = 0;
    private static StringBuffer mMsgBuffer = new StringBuffer(256);
    private LogPower() {
    }

    public static int push(int tag) {
        return printlnPower(Log.WARN, tag, "", null, null, null);
    }
    
    public static int push(int tag, String PackageName) {
            return printlnPower(Log.WARN, tag, PackageName, null, null, null);
    }
    
    public static int push(int tag, String PackageName, String Value) {
        return printlnPower(Log.WARN, tag, PackageName, Value, null, null);
    }
    
    public static int push(int tag, String PackageName, String Value, String ClassName) {
        return printlnPower(Log.WARN, tag, PackageName, Value, ClassName, null);
    }
    
    public static int push(int tag, String PackageName, String Value, String ClassName, String[] Extend) {
        return printlnPower(Log.WARN, tag, PackageName, Value, ClassName, Extend);
    }

    private static int printlnPower(int priority, int tag, String PackageName, String Value,String ClassName, String[] Extend) {
        if (TRY_COUNT <= TRY_MAX_NUM) {
            TRY_COUNT++;
        }

        String msg = null;
        synchronized (mMsgBuffer) {
            if(PackageName != null){
                mMsgBuffer.append(PackageName);
            }
            if(Value != null){
                mMsgBuffer.append("|"+Value);
            }
            if(ClassName != null){
                if(Value == null){
                    mMsgBuffer.append("|");
                }
                mMsgBuffer.append("|"+ClassName);
            }
            if(Extend != null){
                for (int i = 0; i < Extend.length; i++) {
                    mMsgBuffer.append("|"+Extend[i]);
                }
            }
            msg = mMsgBuffer.toString();
            mMsgBuffer.delete(0, mMsgBuffer.length());
        }
		//return Log.println(priority, Integer.toString(tag), msg);
        return Log.print_powerlog_native(priority, Integer.toString(tag), msg);

       // return -1;//fengyaling removed for huawei requirment
    }

}
