package com.mediatek.galleryframework.util;

import com.mediatek.xlog.Xlog;
import android.os.SystemProperties;

/**
 * Adapter for log system.
 */
public final class MtkLog {
    public static final String TAG = "MtkGallery2/MtkLog";
    public static final boolean DBG;
    static {
        DBG = SystemProperties.getInt("Gallery_DBG", 0) == 1 ? true : false;
        Xlog.i(TAG, "DBG = " + DBG);
    }

    private MtkLog() {
    }

    public static int v(String tag, String msg) {
        return Xlog.v(tag, msg);
    }

    public static int v(String tag, String msg, Throwable tr) {
        return Xlog.v(tag, msg, tr);
    }

    public static int d(String tag, String msg) {
        return Xlog.d(tag, msg);
    }

    public static int d(String tag, String msg, Throwable tr) {
        return Xlog.d(tag, msg, tr);
    }

    public static int i(String tag, String msg) {
        return Xlog.i(tag, msg);
    }

    public static int i(String tag, String msg, Throwable tr) {
        return Xlog.i(tag, msg, tr);
    }

    public static int w(String tag, String msg) {
        return Xlog.w(tag, msg);
    }

    public static int w(String tag, String msg, Throwable tr) {
        return Xlog.w(tag, msg, tr);
    }

    public static int w(String tag, Throwable tr) {
        return Xlog.w(tag, "", tr);
    }

    public static int e(String tag, String msg) {
        return Xlog.e(tag, msg);
    }

    public static int e(String tag, String msg, Throwable tr) {
        return Xlog.e(tag, msg, tr);
    }
}
