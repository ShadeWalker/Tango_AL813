package com.android.launcher2;

import android.content.Context;

import com.mediatek.launcher2.ext.LauncherLog;

public class InstallShortcutHelper {
    private static final String TAG = "InstallShortcutHelper";
    private static boolean sInstallingShortcut = false;
    private static int sInstallingCount = 0;
    private static int sSuccessCount = 0;

    /// M: Set the installing shortcut flag, some actions should be forbidden when installing shortcut,
    ///    due to this will lead to the database changing. The flag will be set when start installing and
    ///    reset when binding workspace is done or installing count is decreased to zero.
    public static void setInstallingShortcut(boolean bInstalling) {
        sInstallingShortcut = bInstalling;
        if (sInstallingShortcut == false) {
            sInstallingCount = 0;
            sSuccessCount = 0;
        }
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "setInstallingShortcut: sInstallingShortcut=" + sInstallingShortcut);
        }
    }

    /// M: Check the installing process is ongoing or not
    public static boolean isInstallingShortcut() {
        return sInstallingShortcut;
    }

    /// M: Increase the installing count
    public static void increaseInstallingCount(int count) {
        sInstallingCount += count;
        if (sInstallingCount > 0) {
            sInstallingShortcut = true;
        }
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "increaseInstallingCount: sInstallingCount = " + sInstallingCount
                    + ", sInstallingShortcut=" + sInstallingShortcut);
        }
    }

    /// M: Decrease the installing count, split the successful and failed item, and will trigger loading
    ///    database when successful items is not zero.
    public static void decreaseInstallingCount(Context context, boolean bSuccess) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "decreaseInstallingCount: sInstallingCount = " + sInstallingCount
                    + ", sInstallingShortcut = " + sInstallingShortcut);
        }

        sInstallingCount--;
        if (bSuccess) {
            sSuccessCount++;
        }

        decreaseUpdate(context);
    }

    // M: decrease the installing count with the specified count. This API is used to notify the
    // helper that the specified count of installing shortcut requests are finished and all failed.
    public static void decreaseInstallingCount(Context context, int decreaseCount) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "decreaseInstallingCount: decreaseCount = " + decreaseCount +
                    ", sInstallingCount = " + sInstallingCount +
                    ", sInstallingShortcut = " + sInstallingShortcut);
        }

        sInstallingCount -= decreaseCount;
        decreaseUpdate(context);
    }

    // M: update the helper's context after the installing count is decreased.
    private static void decreaseUpdate(Context context) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "decreaseUpdate: sInstallingCount=" + sInstallingCount
                    + ", sSuccessCount=" + sSuccessCount);
        }

        if (sInstallingCount <= 0) {
            if (sSuccessCount != 0) {
                LauncherLog.d(TAG, "decreaseUpdate: triggerLoadingDatabaseManually");
                LauncherApplication app = (LauncherApplication) context.getApplicationContext();

                // Reload model and sInstallShortcut will be reset to false after model is reloaded.
                app.triggerLoadingDatabaseManually();
            } else {
                LauncherLog.d(TAG, "decreaseUpdate: all failed, and reset sInstallingShortcut");
                sInstallingShortcut = false;
            }
            sInstallingCount = 0;
            sSuccessCount = 0;
        }

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "decreaseUpdate: sInstallingShortcut=" + sInstallingShortcut);
        }
    }
}
