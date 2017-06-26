/*
 * Copyright (C) 2014 Huawei Technologies Co., Ltd.
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

package android.util;

import android.os.SystemClock;
import java.io.PrintWriter;
import java.io.StringWriter;
import android.os.SystemProperties;
import android.app.AppGlobals;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.content.pm.PackageInfo;
import android.os.RemoteException;
import java.io.File;

/**
 * @hide
 */
public final class Jlog {
    private static long         mnUpdatePropTime = 0;
    private static boolean      mJankEnabled = SystemProperties.getBoolean("persist.config.jankenable", true);
    private static boolean      mbDisable = false;
    private static int          misBetaUser = -1;
    public static final int     JANK_UPDATE_PROP_INTERVAL = 30*1000;
    public static final int     JANK_UPDATE_PROP_DELAY    = 15*60*1000;
    /** @hide */
    public static final int     HW_LOG_ID_JANK = 6;
    public static final int     SKIPPED_FRAME_LIMIT = 5;
    public static final long    INFLATE_TIME_LIMIT_NS = 16000000;
    public static final long    OBTAINVIEW_TIME_LIMIT_NS = 16000000;
    public static final long    SETUPVIEW_TIME_LIMIT_NS = 16000000;
    public static final long    MIN_VIOLATION_DURATION_MS = 320;
    public static final long    MAX_VIOLATION_DURATION_MS = 8000;
    private static String       BETA_FILE_PATH = "/system/etc/log_collect_service_beta.xml";

    private Jlog() {
    }

    public static boolean isBetaUser() {
        if (-1 != misBetaUser) return (0 == misBetaUser);

        File f = new File(BETA_FILE_PATH);
        if (f.exists()) {
            misBetaUser = 0;
        } else {
            misBetaUser = 1;
        }
        return (0 == misBetaUser);
    }

    public static boolean isEnable() {
        if (mbDisable) return false;

        long now = SystemClock.uptimeMillis();
        if (now > JANK_UPDATE_PROP_DELAY && (now - mnUpdatePropTime) > JANK_UPDATE_PROP_INTERVAL)
        {
            mJankEnabled = SystemProperties.getBoolean("persist.config.jankenable", false);
            mnUpdatePropTime = now;
        }
        return mJankEnabled;
    }

    public static void disable() {
        mbDisable = true;
        print_janklog_native(Log.DEBUG,
                JlogConstants.JLID_MONKEY_CTS_START, "JL_MONKEY_CTS_START");
    }

    public static void enable() {
        mbDisable = false;
        print_janklog_native(Log.DEBUG,
                JlogConstants.JLID_MONKEY_CTS_END, "JL_MONKEY_CTS_END");
    }

    /* Window{1d0a93d8 u0 com.android.settings/com.android.settings.HWSettings} */
    public static String extractAppName(String msg)  {
        int     nStartPos = 0;
        int     nLen = msg.length();

        if (nLen <= 0)
            return msg;

        if (!msg.endsWith("}"))
            return msg;

        nStartPos = msg.indexOf(" u", nStartPos);
        if (nStartPos < 0)
            return msg;
        nStartPos += 2;
        nStartPos = msg.indexOf(" ", nStartPos);
        if (nStartPos < 0)
            return msg;
        nStartPos += 1;
        return msg.substring(nStartPos, nLen - 1);
    }

    public static int d(int tag, String msg) {
        if (!isEnable())
            return 0;
        return print_janklog_native(Log.DEBUG, tag, msg);
    }

    public static int d(int tag, String arg1, String msg) {
        if (!isEnable())
            return 0;
        return print_janklog_native(Log.DEBUG, tag, "#ARG1:<"+arg1+">"+msg);
    }

    public static int d(int tag, int arg2, String msg) {
        if (!isEnable())
            return 0;
        return print_janklog_native(Log.DEBUG, tag, "#ARG2:<"+arg2+">"+msg);
    }

    public static int d(int tag, String arg1, int arg2, String msg) {
        if (!isEnable())
            return 0;
        return print_janklog_native(Log.DEBUG, tag, "#ARG1:<"+arg1+">#ARG2:<"+arg2+">"+msg);
    }

    public static int v(int tag, String msg) {
        if (!isEnable())
            return 0;
        return print_janklog_native(Log.VERBOSE, tag, msg);
    }

    public static int i(int tag, String msg) {
        if (!isEnable())
            return 0;
        return print_janklog_native(Log.INFO, tag, msg);
    }

    public static int w(int tag, String msg) {
        if (!isEnable())
            return 0;
        return print_janklog_native(Log.WARN, tag, msg);
    }

    public static int e(int tag, String msg) {
        if (!isEnable())
            return 0;
        return print_janklog_native(Log.ERROR, tag, msg);
    }

    //zhuxiang 00308588 2015-03-03 adapter for Jlog begin
    /** @hide */
    //public static native int print_janklog_native(int priority, int tag, String msg);

	private static int print_janklog_native(int priority, int tag, String msg) {
		switch (priority) {
			case Log.VERBOSE:
				Log.v("Jlog", msg);
				break;
			case Log.DEBUG:
				Log.d("Jlog", msg);
				break;
			case Log.INFO:
				Log.i("Jlog", msg);
				break;
			case Log.WARN:
				Log.w("Jlog", msg);
				break;
			case Log.ERROR:
				Log.e("Jlog", msg);
				break;
			default:
				break;
		}
		return 0;
	}
	//zhuxiang 00308588 2015-03-03 adapter for Jlog end
}
