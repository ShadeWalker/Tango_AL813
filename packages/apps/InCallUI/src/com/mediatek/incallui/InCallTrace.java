package com.mediatek.incallui;

import android.os.Trace;

import com.android.incallui.Log;

/**
 * M: log trace for performance profile.
 * enable trace via adb command:
 * adb shell setprop debug.atrace.tags.enableflags 0x1000
 * adb shell atrace --poke_services
 */
public class InCallTrace {
    private static final String LOG_TAG = "InCallTrace";
    private static final long TRACE_TAG = Trace.TRACE_TAG_PERF;
    //modified by jinlibo for phone call performance
    public static boolean sDebug = Trace.isTagEnabled(TRACE_TAG);

    /**
     * begin the performance trace.
     * @param tag
     */
    public static void begin(String tag) {
        if (sDebug) {
            Log.d(LOG_TAG, "[begin]" + tag);
            Trace.traceBegin(TRACE_TAG, tag);
        }
    }

    /**
     * end the performance trace.
     * @param tag
     */
    public static void end(String tag) {
        if (sDebug) {
            Trace.traceEnd(TRACE_TAG);
            Log.d(LOG_TAG, "[end]" + tag);
        }
    }
}
