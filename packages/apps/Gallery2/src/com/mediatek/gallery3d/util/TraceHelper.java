package com.mediatek.gallery3d.util;

import android.os.Trace;

public class TraceHelper {
    private static final long TRACE_DEFAULT_TAG = Trace.TRACE_TAG_VIEW;
    private static final long TARCE_PERF_TAG = Trace.TRACE_TAG_PERF;

    private static final String TRACE_DEFAULT_COUNTER_NAME = "AppUpdate";

    public static void traceBegin(String methodName) {
        Trace.traceBegin(TRACE_DEFAULT_TAG, methodName);
    }

    public static void traceEnd() {
        Trace.traceEnd(TRACE_DEFAULT_TAG);
    }

    public static void traceCounterForLaunchPerf(int counterValue) {
        Trace.traceCounter(TARCE_PERF_TAG, TRACE_DEFAULT_COUNTER_NAME, counterValue);
    }
}