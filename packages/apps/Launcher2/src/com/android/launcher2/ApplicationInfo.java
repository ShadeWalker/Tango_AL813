/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher2;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.os.UserHandle;
import android.util.Log;

import com.mediatek.launcher2.ext.AllApps;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Represents an app in AllAppsView.
 */
public class ApplicationInfo extends ItemInfo {
    private static final String TAG = "ApplicationInfo";

    /**
     * The intent used to start the application.
     */
    Intent intent;

    /**
     * A bitmap version of the application icon.
     */
    Bitmap iconBitmap;

    /**
     * The time at which the app was first installed.
     */
    long firstInstallTime;

    ComponentName componentName;

    /**
     * M: the position of the application icon in all app list page, add for
     * CT.
     */
    int pos;

    /**
     * The application icon is show or hide.
     */
    boolean isVisible = true;

    /**
     * The state of the application icon is changed or not.
     */
    boolean stateChanged;

    static final int DOWNLOADED_FLAG = 1;
    static final int UPDATED_SYSTEM_APP_FLAG = 2;

    int flags = 0;

    ApplicationInfo() {
        itemType = LauncherSettings.BaseLauncherColumns.ITEM_TYPE_SHORTCUT;
    }

    /**
     * Must not hold the Context.
     */
    public ApplicationInfo(LauncherActivityInfo info, UserHandle user, IconCache iconCache,
            HashMap<Object, CharSequence> labelCache) {

        this.componentName = info.getComponentName();
        this.container = ItemInfo.NO_ID;

        int appFlags = info.getApplicationInfo().flags;
        if ((appFlags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
            flags |= DOWNLOADED_FLAG;
        }
        if ((appFlags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
            flags |= UPDATED_SYSTEM_APP_FLAG;
        }
        firstInstallTime = info.getFirstInstallTime();

        iconCache.getTitleAndIcon(this, info, labelCache);

        intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(info.getComponentName());
        intent.putExtra(EXTRA_PROFILE, user);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        itemType = LauncherSettings.BaseLauncherColumns.ITEM_TYPE_APPLICATION;
        updateUser(intent);
    }

    public ApplicationInfo(ApplicationInfo info) {
        super(info);
        componentName = info.componentName;
        title = info.title.toString();
        intent = new Intent(info.intent);
        flags = info.flags;
        firstInstallTime = info.firstInstallTime;

        /// M: copy value from source applcation info.
        pos = info.pos;
        isVisible = info.isVisible;
        stateChanged = info.stateChanged;
    }

    /**
     * Returns the package name that the shortcut's intent will resolve to, or
     * an empty string if none exists.
     *
     * @return the package name.
     */
    String getPackageName() {
        return super.getPackageName(intent);
    }

    /**
     * Creates the application intent based on a component name and various launch flags.
     * Sets {@link #itemType} to {@link LauncherSettings.BaseLauncherColumns#ITEM_TYPE_APPLICATION}.
     *
     * @param className the class name of the component representing the intent
     * @param launchFlags the launch flags
     */
    final void setActivity(ComponentName className, int launchFlags) {
        intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(className);
        intent.setFlags(launchFlags);
        itemType = LauncherSettings.BaseLauncherColumns.ITEM_TYPE_APPLICATION;
    }

    @Override
    public String toString() {
        return "ApplicationInfo(" + Integer.toHexString(System.identityHashCode(this)) + ", title="
                + (title != null ? title.toString() : "") + " itemId = " + id + " screen = " + screen + " cellX = " + cellX
                + " cellY = " + cellY + " pos = " + pos + " isVisible = " + isVisible + " unreadNum= " + unreadNum + " P=" + user + ")";
    }

    public static void dumpApplicationInfoList(final String tag, final String label,
            final ArrayList<ApplicationInfo> list) {
        Log.d(tag, label + " size=" + list.size());
        for (ApplicationInfo info: list) {
            Log.d(tag, "   title=\"" + info.title + "\" iconBitmap="
                    + info.iconBitmap + " firstInstallTime="
                    + info.firstInstallTime);
        }
    }

    public ShortcutInfo makeShortcut() {
        return new ShortcutInfo(this);
    }


    //@Override
    void onAddToDatabase(ContentValues values) {
        String titleStr = title != null ? title.toString() : null;
        values.put(LauncherSettings.BaseLauncherColumns.TITLE, titleStr);

        String uri = intent != null ? intent.toUri(0) : null;
        values.put(AllApps.INTENT, uri);
    }

    /**
     * M: set app flag and record first install time.
     *
     * @param pm
     */
    public void setFlagAndInstallTime(final PackageManager pm) {
        final String packageName = getPackageName();
        try {
            int appFlags = pm.getApplicationInfo(packageName, 0).flags;
            if ((appFlags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                flags |= DOWNLOADED_FLAG;

                if ((appFlags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                    flags |= UPDATED_SYSTEM_APP_FLAG;
                }
            }
            firstInstallTime = pm.getPackageInfo(packageName, 0).firstInstallTime;
        } catch (NameNotFoundException e) {
            Log.d(TAG, "PackageManager.getApplicationInfo failed for " + packageName);
        }
    }
}
