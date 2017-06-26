/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

package com.mediatek.launcher3.ext;

import com.mediatek.xlog.Xlog;

/**
 * Launcher Log utils.
 */
@SuppressWarnings("checkstyle:staticvariablename")
public final class LauncherLog {

    public static boolean DEBUG = true;
    public static boolean DEBUG_DRAW = true;
    public static boolean DEBUG_DRAG = true;
    public static boolean DEBUG_EDIT = true;
    public static boolean DEBUG_KEY = true;
    public static boolean DEBUG_LAYOUT = true;
    public static boolean DEBUG_LOADER = true;
    public static boolean DEBUG_MOTION = true;
    public static boolean DEBUG_PERFORMANCE = true;
    public static boolean DEBUG_SURFACEWIDGET = true;
    public static boolean DEBUG_UNREAD = false;
    public static boolean DEBUG_LOADERS = true;
    public static boolean DEBUG_AUTOTESTCASE = true;

    private static final String MODULE_NAME = "Launcher3";
    private static final LauncherLog INSTANCE = new LauncherLog();

    /** use android properties to control debug on-ff. */
    // define system properties
    public static final String PROP_DEBUG_ALL = "launcher.debug.all";
    public static final String PROP_DEBUG = "launcher.debug";
    public static final String PROP_DEBUG_DRAW = "launcher.debug.draw";
    public static final String PROP_DEBUG_DRAG = "launcher.debug.drag";
    public static final String PROP_DEBUG_EDIT = "launcher.debug.edit";
    public static final String PROP_DEBUG_KEY = "launcher.debug.key";
    public static final String PROP_DEBUG_LAYOUT = "launcher.debug.layout";
    public static final String PROP_DEBUG_LOADER = "launcher.debug.loader";
    public static final String PROP_DEBUG_MOTION = "launcher.debug.motion";
    public static final String PROP_DEBUG_PERFORMANCE = "launcher.debug.performance";
    public static final String PROP_DEBUG_SURFACEWIDGET = "launcher.debug.surfacewidget";
    public static final String PROP_DEBUG_UNREAD = "launcher.debug.unread";
    public static final String PROP_DEBUG_LOADERS = "launcher.debug.loaders";
    public static final String PROP_DEBUG_AUTOTESTCASE = "launcher.debug.autotestcase";
    /** end */

    // should kill and restart launcher process to re-execute static block if reset properties
    // adb shell setprop launcher.debug.xxx true/false
    // adb shell stop
    // adb shell start
    static {
        if (android.os.SystemProperties.getBoolean(PROP_DEBUG_ALL, false)) {
            Xlog.d(MODULE_NAME, "enable all debug on-off");
            DEBUG = true;
            DEBUG_DRAW = true;
            DEBUG_DRAG = true;
            DEBUG_EDIT = true;
            DEBUG_KEY = true;
            DEBUG_LAYOUT = true;
            DEBUG_LOADER = true;
            DEBUG_MOTION = true;
            DEBUG_PERFORMANCE = true;
            DEBUG_SURFACEWIDGET = true;
            DEBUG_UNREAD = true;
            DEBUG_LOADERS = true;
            DEBUG_AUTOTESTCASE = true;
        } else {
            // separately set
            DEBUG = android.os.SystemProperties.getBoolean(PROP_DEBUG, true);
            DEBUG_DRAW = android.os.SystemProperties.getBoolean(PROP_DEBUG_DRAW, false);
            DEBUG_DRAG = android.os.SystemProperties.getBoolean(PROP_DEBUG_DRAG, false);
            DEBUG_EDIT = android.os.SystemProperties.getBoolean(PROP_DEBUG_EDIT, true);
            DEBUG_KEY = android.os.SystemProperties.getBoolean(PROP_DEBUG_KEY, true);
            DEBUG_LAYOUT = android.os.SystemProperties.getBoolean(PROP_DEBUG_LAYOUT, false);
            DEBUG_LOADER = android.os.SystemProperties.getBoolean(PROP_DEBUG_LOADER, true);
            DEBUG_MOTION = android.os.SystemProperties.getBoolean(PROP_DEBUG_MOTION, false);
            DEBUG_PERFORMANCE = android.os.SystemProperties
                    .getBoolean(PROP_DEBUG_PERFORMANCE, true);
            DEBUG_SURFACEWIDGET = android.os.SystemProperties.getBoolean(PROP_DEBUG_SURFACEWIDGET,
                    true);
            DEBUG_UNREAD = android.os.SystemProperties.getBoolean(PROP_DEBUG_UNREAD, false);
            DEBUG_LOADERS = android.os.SystemProperties.getBoolean(PROP_DEBUG_LOADERS, true);
            DEBUG_AUTOTESTCASE = android.os.SystemProperties.getBoolean(PROP_DEBUG_AUTOTESTCASE,
                    true);
        }
    }
    /******************** end ********************/

    /**
     * private constructor here, It is a singleton class.
     */
    private LauncherLog() {
    }

    /**
     * The FileManagerLog is a singleton class, this static method can be used
     * to obtain the unique instance of this class.
     *
     * @return The global unique instance of FileManagerLog.
     */
    public static LauncherLog getInstance() {
        return INSTANCE;
    }

    /**
     * The method prints the log, level error.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     */
    public static void e(String tag, String msg) {
        Xlog.e(MODULE_NAME, tag + ", " + msg);
    }

    /**
     * The method prints the log, level error.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     * @param t an exception to log.
     */
    public static void e(String tag, String msg, Throwable t) {
        Xlog.e(MODULE_NAME, tag + ", " + msg, t);
    }

    /**
     * The method prints the log, level warning.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     */
    public static void w(String tag, String msg) {
        Xlog.w(MODULE_NAME, tag + ", " + msg);
    }

    /**
     * The method prints the log, level warning.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     * @param t an exception to log.
     */
    public static void w(String tag, String msg, Throwable t) {
        Xlog.w(MODULE_NAME, tag + ", " + msg, t);
    }

    /**
     * The method prints the log, level debug.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     */
    public static void i(String tag, String msg) {
        Xlog.i(MODULE_NAME, tag + ", " + msg);
    }

    /**
     * The method prints the log, level debug.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     * @param t an exception to log.
     */
    public static void i(String tag, String msg, Throwable t) {
        Xlog.i(MODULE_NAME, tag + ", " + msg, t);
    }

    /**
     * The method prints the log, level debug.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     */
    public static void d(String tag, String msg) {
        Xlog.d(MODULE_NAME, tag + ", " + msg);
    }

    /**
     * The method prints the log, level debug.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     * @param t An exception to log.
     */
    public static void d(String tag, String msg, Throwable t) {
        Xlog.d(MODULE_NAME, tag + ", " + msg, t);
    }

    /**
     * The method prints the log, level debug.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     */
    public static void v(String tag, String msg) {
        Xlog.v(MODULE_NAME, tag + ", " + msg);
    }

    /**
     * The method prints the log, level debug.
     *
     * @param tag the tag of the class.
     * @param msg the message to print.
     * @param t An exception to log.
     */
    public static void v(String tag, String msg, Throwable t) {
        Xlog.v(MODULE_NAME, tag + ", " + msg, t);
    }
}
