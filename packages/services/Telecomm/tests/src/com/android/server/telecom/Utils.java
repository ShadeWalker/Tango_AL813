package com.android.server.telecom;

import android.util.Log;

public class Utils {

    final static String TAG = "TelecommTest";

    public static void log(String content) {

        Log.d(TAG, content);
    }

    public static void log(String tag, String content) {
        Log.d(TAG, "[" + tag + "]" + " " + content);
    }

    public static void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
