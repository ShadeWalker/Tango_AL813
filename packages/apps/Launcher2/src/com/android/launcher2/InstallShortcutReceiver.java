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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import android.net.Uri;
import android.os.Parcelable;
import android.widget.Toast;

import com.android.launcher.R;

import com.mediatek.launcher2.ext.LauncherLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.json.*;

public class InstallShortcutReceiver extends BroadcastReceiver {
    private static final String TAG = "InstallShortcutReceiver";
    public static final String ACTION_INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT";
    public static final String NEW_APPS_PAGE_KEY = "apps.new.page";
    public static final String NEW_APPS_LIST_KEY = "apps.new.list";

    public static final String DATA_INTENT_KEY = "intent.data";
    public static final String LAUNCH_INTENT_KEY = "intent.launch";
    public static final String NAME_KEY = "name";
    public static final String ICON_KEY = "icon";
    public static final String ICON_RESOURCE_NAME_KEY = "iconResource";
    public static final String ICON_RESOURCE_PACKAGE_NAME_KEY = "iconResourcePackage";
    // The set of shortcuts that are pending install
    public static final String APPS_PENDING_INSTALL = "apps_to_install";

    public static final int NEW_SHORTCUT_BOUNCE_DURATION = 450;
    public static final int NEW_SHORTCUT_STAGGER_DELAY = 75;

    private static final int INSTALL_SHORTCUT_SUCCESSFUL = 0;
    private static final int INSTALL_SHORTCUT_IS_DUPLICATE = -1;
    private static final int INSTALL_SHORTCUT_NO_SPACE = -2;
    private static final int INSTALL_SHORTCUT_ADD_FAIL = -3;

    /// M: Add for Message Shortcut (batch)
    private static final String EXTRA_SHORTCUT_INTENT_ARRAY = "com.android.launcher2.extra.shortcut.array.INTENT";
    private static final String EXTRA_SHORTCUT_NAME_ARRAY = "com.android.launcher2.extra.shortcut.array.NAME";
    private static final String EXTRA_SHORTCUT_ICON_ARRAY = "com.android.launcher2.extra.shortcut.array.ICON";
    private static final String EXTRA_SHORTCUT_ICON_RESOURCE_ARRAY = "com.android.launcher2.extra.shortcut.array.ICON_RESOURCE";
    private static final String EXTRA_SHORTCUT_TOTAL_NUMBER = "com.android.launcher2.extra.shortcut.totalnumber";
    private static final String EXTRA_SHORTCUT_STEP_NUMBER = "com.android.launcher2.extra.shortcut.stepnumber";

    // A mime-type representing shortcut data
    public static final String SHORTCUT_MIMETYPE =
            "com.android.launcher/shortcut";

    private static Object sLock = new Object();

    private static void addToStringSet(SharedPreferences sharedPrefs,
            SharedPreferences.Editor editor, String key, String value) {
        Set<String> strings = sharedPrefs.getStringSet(key, null);
        if (strings == null) {
            strings = new HashSet<String>(0);
        } else {
            strings = new HashSet<String>(strings);
        }
        strings.add(value);
        editor.putStringSet(key, strings);
    }

    private static void addToInstallQueue(
            SharedPreferences sharedPrefs, PendingInstallShortcutInfo info) {
        synchronized(sLock) {
            try {
                JSONStringer json = new JSONStringer()
                    .object()
                    .key(DATA_INTENT_KEY).value(info.data.toUri(0))
                    .key(LAUNCH_INTENT_KEY).value(info.launchIntent.toUri(0))
                    .key(NAME_KEY).value(info.name);
                if (info.icon != null) {
                    byte[] iconByteArray = ItemInfo.flattenBitmap(info.icon);
                    json = json.key(ICON_KEY).value(
                        Base64.encodeToString(
                            iconByteArray, 0, iconByteArray.length, Base64.DEFAULT));
                }
                if (info.iconResource != null) {
                    json = json.key(ICON_RESOURCE_NAME_KEY).value(info.iconResource.resourceName);
                    json = json.key(ICON_RESOURCE_PACKAGE_NAME_KEY)
                        .value(info.iconResource.packageName);
                }
                json = json.endObject();
                SharedPreferences.Editor editor = sharedPrefs.edit();
                addToStringSet(sharedPrefs, editor, APPS_PENDING_INSTALL, json.toString());
                editor.commit();
            } catch (org.json.JSONException e) {
                Log.d("InstallShortcutReceiver", "Exception when adding shortcut: " + e);
            }
        }
    }

    private static ArrayList<PendingInstallShortcutInfo> getAndClearInstallQueue(
            SharedPreferences sharedPrefs) {
        synchronized(sLock) {
            Set<String> strings = sharedPrefs.getStringSet(APPS_PENDING_INSTALL, null);
            if (strings == null) {
                return new ArrayList<PendingInstallShortcutInfo>();
            }
            ArrayList<PendingInstallShortcutInfo> infos =
                new ArrayList<PendingInstallShortcutInfo>();
            for (String json : strings) {
                try {
                    JSONObject object = (JSONObject) new JSONTokener(json).nextValue();
                    Intent data = Intent.parseUri(object.getString(DATA_INTENT_KEY), 0);
                    Intent launchIntent = Intent.parseUri(object.getString(LAUNCH_INTENT_KEY), 0);
                    String name = object.getString(NAME_KEY);
                    String iconBase64 = object.optString(ICON_KEY);
                    String iconResourceName = object.optString(ICON_RESOURCE_NAME_KEY);
                    String iconResourcePackageName =
                        object.optString(ICON_RESOURCE_PACKAGE_NAME_KEY);
                    if (iconBase64 != null && !iconBase64.isEmpty()) {
                        byte[] iconArray = Base64.decode(iconBase64, Base64.DEFAULT);
                        Bitmap b = BitmapFactory.decodeByteArray(iconArray, 0, iconArray.length);
                        data.putExtra(Intent.EXTRA_SHORTCUT_ICON, b);
                    } else if (iconResourceName != null && !iconResourceName.isEmpty()) {
                        Intent.ShortcutIconResource iconResource =
                            new Intent.ShortcutIconResource();
                        iconResource.resourceName = iconResourceName;
                        iconResource.packageName = iconResourcePackageName;
                        data.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
                    }
                    data.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent);
                    PendingInstallShortcutInfo info =
                        new PendingInstallShortcutInfo(data, name, launchIntent);
                    infos.add(info);
                } catch (org.json.JSONException e) {
                    Log.d("InstallShortcutReceiver", "Exception reading shortcut to add: " + e);
                } catch (java.net.URISyntaxException e) {
                    Log.d("InstallShortcutReceiver", "Exception reading shortcut to add: " + e);
                }
            }
            sharedPrefs.edit().putStringSet(APPS_PENDING_INSTALL, new HashSet<String>()).commit();
            return infos;
        }
    }

    // Determines whether to defer installing shortcuts immediately until
    // processAllPendingInstalls() is called.
    private static boolean mUseInstallQueue = false;

    ///M: Shortcut by step
    private static Map<String, Integer> sName2TotalNumberMap = new HashMap<String, Integer>();
    private static Map<String, Integer> sName2StepNumberMap = new HashMap<String, Integer>();
    private static Map<String, Intent[]> sName2IntentArrayMap = new HashMap<String, Intent[]>();

    /// M: Record the items which are adding to database but not in database now
    private static ArrayList<ItemInfo> sItemsAddingToDatabase = new ArrayList<ItemInfo>();

    private static class PendingInstallShortcutInfo {
        Intent data;
        Intent launchIntent;
        String name;
        Bitmap icon;
        Intent.ShortcutIconResource iconResource;

        public PendingInstallShortcutInfo(Intent rawData, String shortcutName, Intent shortcutIntent) {
            data = rawData;
            name = shortcutName;
            launchIntent = shortcutIntent;
        }
    }

    public void onReceive(Context context, Intent data) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onReceive: received intent action: " + data.getAction());
        }
        if (!ACTION_INSTALL_SHORTCUT.equals(data.getAction())) {
            return;
        }

        sItemsAddingToDatabase.clear();

        /// M: Shortcut Array
        Parcelable intentArray[] = data.getParcelableArrayExtra(EXTRA_SHORTCUT_INTENT_ARRAY);
        if (intentArray == null || intentArray.length == 0) {
            int totalNumber = data.getIntExtra(EXTRA_SHORTCUT_TOTAL_NUMBER, 0);
            if (totalNumber < 1) {
                installShortcutSingle(context, data);
            } else {
                installShortcutStep(context, data);
            }
        } else {
            installShortcutArray(context, data);
        }

    }

    private static void installShortcutSingle(Context context, Intent data) {
        Intent intent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        if (intent == null) {
            return;
        }
        // This name is only used for comparisons and notifications, so fall back to activity name
        // if not supplied
        String name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        if (name == null) {
            try {
                PackageManager pm = context.getPackageManager();
                ActivityInfo info = pm.getActivityInfo(intent.getComponent(), 0);
                name = info.loadLabel(pm).toString();
            } catch (PackageManager.NameNotFoundException nnfe) {
                return;
            }
        }
        Bitmap icon = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
        Intent.ShortcutIconResource iconResource =
            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "installShortcutSingle: data = " + data + ", name = " + name
                    + ", intent = " + intent);
        }

        // Queue the item up for adding if launcher has not loaded properly yet
        boolean launcherNotLoaded = LauncherModel.getCellCountX() <= 0 ||
                LauncherModel.getCellCountY() <= 0;

        PendingInstallShortcutInfo info = new PendingInstallShortcutInfo(data, name, intent);
        info.icon = icon;
        info.iconResource = iconResource;
        if (mUseInstallQueue || launcherNotLoaded) {
            String spKey = LauncherApplication.getSharedPreferencesKey();
            SharedPreferences sp = context.getSharedPreferences(spKey, Context.MODE_PRIVATE);
            addToInstallQueue(sp, info);
        } else {
            /// M: Increase the installing shortcut count
            InstallShortcutHelper.increaseInstallingCount(1);
            processInstallShortcut(context, info);
        }
    }

    /**
     * M: Support to install shortcut by step, only invoked when intent brings
     * "total number and step" extras
     *
     * @param context
     * @param data
     *            the received intent
     */
    private static void installShortcutStep(Context context, Intent data) {
        Intent intent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        if (intent == null) {
            LauncherLog.e(TAG, "installShortcutStep: Intent is null!");
            return;
        }

        int totalNumber = data.getIntExtra(EXTRA_SHORTCUT_TOTAL_NUMBER, 0);
        if (totalNumber < 1) {
            LauncherLog.e(TAG, "installShortcutStep: total number is smaller than 1!");
            return;
        }

        int stepNumber = data.getIntExtra(EXTRA_SHORTCUT_STEP_NUMBER, 0);
        if (stepNumber < 1 || stepNumber > totalNumber) {
            LauncherLog.e(TAG, "installShortcutStep: Step number is wrong!");
            return;
        }

        // This name is only used for comparisons and notifications, so fall
        // back to activity name if not supplied
        String pkgName = null;
        Uri uri = data.getData();
        if (uri != null) {
            pkgName = uri.getEncodedSchemeSpecificPart();
            if (pkgName == null) {
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "installShortcutStep: Package name is null!");
                }
                return;
            }
        }

        if (sName2TotalNumberMap.containsKey(pkgName)) {
            if (sName2TotalNumberMap.get(pkgName) != totalNumber) {
                Intent[] intentArray = new Intent[totalNumber];
                sName2IntentArrayMap.put(pkgName, intentArray);
                sName2StepNumberMap.put(pkgName, 0);
            }
        }
        sName2TotalNumberMap.put(pkgName, totalNumber);

        if (!sName2IntentArrayMap.containsKey(pkgName)) {
            Intent[] intentArray = new Intent[totalNumber];
            sName2IntentArrayMap.put(pkgName, intentArray);
            sName2StepNumberMap.put(pkgName, 0);
        }
        sName2IntentArrayMap.get(pkgName)[stepNumber - 1] = data;
        sName2StepNumberMap.put(pkgName, stepNumber);

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "installShortcutStep: data = " + data + ", name = " + pkgName
                    + ", intent = " + intent + ", total number = " + totalNumber + ", step = "
                    + stepNumber);
        }

        // Queue the item up for adding if launcher has not loaded properly yet
        boolean launcherNotLoaded = LauncherModel.getCellCountX() <= 0 ||
                LauncherModel.getCellCountY() <= 0;

        if (mUseInstallQueue || launcherNotLoaded) {
            String name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
            if (name == null) {
                try {
                    PackageManager pm = context.getPackageManager();
                    ActivityInfo info = pm.getActivityInfo(intent.getComponent(), 0);
                    name = info.loadLabel(pm).toString();
                } catch (PackageManager.NameNotFoundException nnfe) {
                    if (LauncherLog.DEBUG) {
                        LauncherLog.d(TAG, "installShortcutStep: Activity name is not found!");
                    }
                    return;
                }
            }
            PendingInstallShortcutInfo info = new PendingInstallShortcutInfo(data, name, intent);
            Bitmap icon = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
            Intent.ShortcutIconResource iconResource =
                data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);
            info.icon = icon;
            info.iconResource = iconResource;
            String spKey = LauncherApplication.getSharedPreferencesKey();
            SharedPreferences sp = context.getSharedPreferences(spKey, Context.MODE_PRIVATE);
            addToInstallQueue(sp, info);
        } else {
            // The received total intent equals the set total number
            if (stepNumber == totalNumber) {
                if (LauncherLog.DEBUG) {
                    LauncherLog
                            .d(TAG,
                                    "installShortcutStep: Hit the total and start to install shortcut array!");
                }
                Intent[] intentArray = sName2IntentArrayMap.get(pkgName);
                for (Intent eachIntent : intentArray) {
                    if (eachIntent == null) {
                        LauncherLog.e(TAG, "installShortcutStep: IntentArray has null intent!");
                        clearMaps(pkgName);
                        return;
                    }
                }
                /// M: Increase the installing shortcut count
                InstallShortcutHelper.increaseInstallingCount(intentArray.length);
                processInstallShortcutArray(context, intentArray);
                clearMaps(pkgName);
            }
        }
    }

    private static void clearMaps(String name) {
        sName2TotalNumberMap.remove(name);
        sName2IntentArrayMap.remove(name);
        sName2StepNumberMap.remove(name);
    }

    /**
     * M: Support to install shortcut by array,only invoked when intent brings
     * "array" extras
     *
     * @param context
     * @param data
     *            the received intent
     */
    private static void installShortcutArray(Context context, Intent data) {
        Parcelable intentArray[] = data.getParcelableArrayExtra(EXTRA_SHORTCUT_INTENT_ARRAY);
        String nameArray[] = data.getStringArrayExtra(EXTRA_SHORTCUT_NAME_ARRAY);
        Parcelable iconArray[] = data.getParcelableArrayExtra(EXTRA_SHORTCUT_ICON_ARRAY);
        Parcelable iconResourceArray[] = data
                .getParcelableArrayExtra(EXTRA_SHORTCUT_ICON_RESOURCE_ARRAY);

        int size = intentArray.length;
        // Must have the same size, or can't match
        if (nameArray.length != size) {
            if (LauncherLog.DEBUG) {
                LauncherLog.e(TAG,
                        "installShortcutArray: intent array and name array have different size!");
            }
            return;
        }

        for (int i = 0; i < size; i++) {
            if ((Intent) intentArray[i] == null) {
                if (LauncherLog.DEBUG) {
                    LauncherLog.e(TAG, "installShortcutArray: intent is null with " + i);
                }
                return;
            }

            if (nameArray[i] == null) {
                try {
                    PackageManager pm = context.getPackageManager();
                    ActivityInfo info = pm.getActivityInfo(
                            ((Intent) intentArray[i]).getComponent(), 0);
                    nameArray[i] = info.loadLabel(pm).toString();
                } catch (PackageManager.NameNotFoundException nnfe) {
                    return;
                }
            }
        }

        if (iconArray != null && iconArray.length != size) {
            if (LauncherLog.DEBUG) {
                LauncherLog.e(TAG,
                        "installShortcutArray: icon array is not null but the size not match!");
            }
            return;
        }

        if (iconResourceArray != null && iconResourceArray.length != size) {
            if (LauncherLog.DEBUG) {
                LauncherLog.e(TAG, "installShortcutArray: icon resource array is not null but "
                        + "the size not match!");
            }
            return;
        }

        // Queue the item up for adding if launcher has not loaded properly yet
        boolean launcherNotLoaded = LauncherModel.getCellCountX() <= 0 ||
                LauncherModel.getCellCountY() <= 0;

        Intent dataArray[] = new Intent[size];
        for (int i = 0; i < size; i++) {
            String name = nameArray[i];
            Intent intent = new Intent();
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
            intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, intentArray[i]);
            if (iconArray != null) {
                intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, iconArray[i]);
            }

            if (iconResourceArray != null) {
                intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResourceArray[i]);
            }

            if (mUseInstallQueue || launcherNotLoaded) {
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "installShortcutArray: Add into Install Queue!");
                }
                PendingInstallShortcutInfo info = new PendingInstallShortcutInfo(intent, name,
                        (Intent) intentArray[i]);
                Bitmap icon = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
                Intent.ShortcutIconResource iconResource =
                    data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);
                info.icon = icon;
                info.iconResource = iconResource;
                String spKey = LauncherApplication.getSharedPreferencesKey();
                SharedPreferences sp = context.getSharedPreferences(spKey, Context.MODE_PRIVATE);
                addToInstallQueue(sp, info);
            }
            dataArray[i] = intent;
        }

        if (!mUseInstallQueue && !launcherNotLoaded) {
            /// M: Increase the installing shortcut count
            InstallShortcutHelper.increaseInstallingCount(dataArray.length);
            processInstallShortcutArray(context, dataArray);
        }
    }

    static void enableInstallQueue() {
        mUseInstallQueue = true;
    }

    static void disableAndFlushInstallQueue(Context context) {
        mUseInstallQueue = false;
        flushInstallQueue(context);
    }

    static void flushInstallQueue(Context context) {
        String spKey = LauncherApplication.getSharedPreferencesKey();
        SharedPreferences sp = context.getSharedPreferences(spKey, Context.MODE_PRIVATE);
        ArrayList<PendingInstallShortcutInfo> installQueue = getAndClearInstallQueue(sp);

        /// M: Increase the indicator for installing shortcut @{
        if (installQueue.size() > 0) {
            InstallShortcutHelper.increaseInstallingCount(installQueue.size());
        }
        /// M: }@
        sItemsAddingToDatabase.clear();

        for (int i = 0; i < installQueue.size(); i++) {
            if (processInstallShortcut(context, installQueue.get(i)) == false) { // stop trying
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "flushInstallQueue: there is no space for shortcut. Stop right now.");
                }

                int skipCountNoSpace = installQueue.size() - 1 - i;
                InstallShortcutHelper.decreaseInstallingCount(context, skipCountNoSpace);

                break;
            }
        }

        installQueue.clear();
    }

    private static boolean processInstallShortcut(Context context,
            PendingInstallShortcutInfo pendingInfo) {
        String spKey = LauncherApplication.getSharedPreferencesKey();
        SharedPreferences sp = context.getSharedPreferences(spKey, Context.MODE_PRIVATE);

        final Intent data = pendingInfo.data;
        final Intent intent = pendingInfo.launchIntent;
        final String name = pendingInfo.name;

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "processInstallShortcut pendingInfo = " + pendingInfo + ", data = "
                    + data + ", intent = " + intent + ", name = " + name);
        }

        // Lock on the app so that we don't try and get the items while apps are being added
        LauncherApplication app = (LauncherApplication) context.getApplicationContext();
        final int[] result = { INSTALL_SHORTCUT_SUCCESSFUL };
        boolean found = false;
        synchronized (app) {
            // Flush the LauncherModel worker thread, so that if we just did another
            // processInstallShortcut, we give it time for its shortcut to get added to the
            // database (getItemsInLocalCoordinates reads the database)
            app.getModel().flushWorkerThread();
            final ArrayList<ItemInfo> items = LauncherModel.getItemsInLocalCoordinates(context);
            final boolean exists = LauncherModel.shortcutExists(context, name, intent);

            // Try adding to the workspace screens incrementally, starting at the default or center
            // screen and alternating between +1, -1, +2, -2, etc. (using ~ ceil(i/2f)*(-1)^(i-1))
            final int screen = Launcher.DEFAULT_SCREEN;
            for (int i = 0; i < (2 * Launcher.SCREEN_COUNT) + 1 && !found; ++i) {
                int si = screen + (int) ((i / 2f) + 0.5f) * ((i % 2 == 1) ? 1 : -1);
                if (0 <= si && si < Launcher.SCREEN_COUNT) {
                    found = installShortcut(context, data, items, name, intent, si, exists, sp,
                            result);
                    if (found) {
                        break;
                    }
                }
            }
        }

        // We only report error messages (duplicate shortcut or out of space) as the add-animation
        // will provide feedback otherwise
        if (result[0] == INSTALL_SHORTCUT_NO_SPACE) {
            Toast.makeText(context, context.getString(R.string.completely_out_of_space),
                    Toast.LENGTH_SHORT).show();
        } else if (result[0] == INSTALL_SHORTCUT_IS_DUPLICATE) {
            Toast.makeText(context, context.getString(R.string.shortcut_duplicate, name),
                    Toast.LENGTH_SHORT).show();
        }

        /// M: Decrease the installed shortcut for failed items @{
        if (result[0] == INSTALL_SHORTCUT_NO_SPACE
                || result[0] == INSTALL_SHORTCUT_IS_DUPLICATE
                ||  result[0] == INSTALL_SHORTCUT_ADD_FAIL) {
            InstallShortcutHelper.decreaseInstallingCount(context, false);
        }
        /// M: }@

        return result[0] != INSTALL_SHORTCUT_NO_SPACE ? true : false; // true: continue trying
    }

    /**
     * M: install shortcut by array
     *
     * @param context
     * @param dataArray
     *            the received intent brought intent array
     */
    private static void processInstallShortcutArray(Context context, Intent[] dataArray) {
        String spKey = LauncherApplication.getSharedPreferencesKey();
        SharedPreferences sp = context.getSharedPreferences(spKey, Context.MODE_PRIVATE);

        LauncherApplication app = (LauncherApplication) context.getApplicationContext();
        ArrayList<Integer> successArray = new ArrayList<Integer>();
        ArrayList<Integer> addFailArray = new ArrayList<Integer>();
        ArrayList<Integer> noSpaceArray = new ArrayList<Integer>();
        ArrayList<Integer> duplicateArray = new ArrayList<Integer>();

        final ArrayList<ItemInfo> items = LauncherModel.getItemsInLocalCoordinates(context);

        int skipCountNoSpace = 0;

        for (int j = 0; j < dataArray.length; j++) {
            final Intent data = dataArray[j];
            final Intent intent = dataArray[j].getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
            final String name = dataArray[j].getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "processInstallShortcutArray: data = " + data + ", intent = "
                        + intent + ", name = " + name);
            }

            // Lock on the app so that we don't try and get the items while apps
            // are being added
            final int[] result = { INSTALL_SHORTCUT_SUCCESSFUL };
            boolean found = false;
            synchronized (app) {
                // Flush the LauncherModel worker thread, so that if we just did another
                // processInstallShortcut, we give it time for its shortcut to get added to the
                // database (getItemsInLocalCoordinates reads the database)
                app.getModel().flushWorkerThread();
                boolean duplicate = data.getBooleanExtra(Launcher.EXTRA_SHORTCUT_DUPLICATE, true);
                boolean exists = false;
                if (!duplicate) {
                    exists = LauncherModel.shortcutExists(context, name, intent);
                }

                // Try adding to the workspace screens incrementally, starting
                // at the default or center
                // screen and alternating between +1, -1, +2, -2, etc. (using ~
                // ceil(i/2f)*(-1)^(i-1))
                final int screen = Launcher.DEFAULT_SCREEN;
                for (int i = 0; i < (2 * Launcher.SCREEN_COUNT) + 1 && !found; ++i) {
                    int si = screen + (int) ((i / 2f) + 0.5f) * ((i % 2 == 1) ? 1 : -1);
                    if (0 <= si && si < Launcher.SCREEN_COUNT) {
                        found = installShortcut(context, data, items, name, intent, si, exists, sp,
                                result);
                        if (found) {
                            break;
                        }
                    }
                }
            }
            switch (result[0]) {
            case INSTALL_SHORTCUT_SUCCESSFUL:
                successArray.add(j);
                break;
            case INSTALL_SHORTCUT_ADD_FAIL:
                addFailArray.add(j);
                /// M: Decrease the installed shortcut cout for failed items
                InstallShortcutHelper.decreaseInstallingCount(context, false);
                break;
            case INSTALL_SHORTCUT_NO_SPACE:
                noSpaceArray.add(j);
                /// M: Decrease the installed shortcut cout for failed items
                InstallShortcutHelper.decreaseInstallingCount(context, false);
                break;
            case INSTALL_SHORTCUT_IS_DUPLICATE:
                duplicateArray.add(j);
                /// M: Decrease the installed shortcut cout for failed items
                InstallShortcutHelper.decreaseInstallingCount(context, false);
                break;
            default:
                break;
            }
            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "processInstallShortcutArray: result is " + result[0]);
            }

            if (result[0] == INSTALL_SHORTCUT_NO_SPACE) {
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "processInstallShortcutArray: there is no space for shortcut. Stop right now.");
                }

                skipCountNoSpace = dataArray.length - 1 - j;
                InstallShortcutHelper.decreaseInstallingCount(context, skipCountNoSpace);
                break;
            }
        }

        int countSuccess = successArray.size();
        int countAddFail = addFailArray.size();
        int countNoSpace = noSpaceArray.size() + skipCountNoSpace;
        int countDuplicate = duplicateArray.size();

        StringBuilder messageSuccess;
        if (countSuccess == 1) {
            messageSuccess = new StringBuilder();
            final String nameStr = dataArray[successArray.get(0)]
                    .getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
            messageSuccess.append(String.format(context.getString(R.string.shortcut_installed),
                    nameStr));
            Toast.makeText(context, messageSuccess.toString(), Toast.LENGTH_SHORT).show();
        } else if (countSuccess > 1) {
            messageSuccess = new StringBuilder();
            messageSuccess.append(String.format(
                    context.getString(R.string.shortcut_array_installed), countSuccess));
            Toast.makeText(context, messageSuccess.toString(), Toast.LENGTH_SHORT).show();
        }

        if ((countDuplicate + countAddFail + countNoSpace) == 1) {
            int index = 0;
            if (countNoSpace == 1) {
                index = noSpaceArray.get(0);
            } else if (countDuplicate == 1) {
                index = duplicateArray.get(0);
            } else if (countAddFail == 1) {
                index = addFailArray.get(0);
            }
            final String nameStr = dataArray[index].getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
            StringBuilder messageFail;
            if (countNoSpace == 1) {
                messageFail = new StringBuilder();
                messageFail.append(context.getString(R.string.out_of_space) + "\n");
                messageFail.append(String.format(
                        context.getString(R.string.shortcut_install_failed), nameStr));
                Toast.makeText(context, messageFail.toString(), Toast.LENGTH_SHORT).show();
            } else if (countDuplicate == 1) {
                messageFail = new StringBuilder();
                messageFail.append(String.format(context.getString(R.string.shortcut_duplicate),
                        nameStr));
                Toast.makeText(context, messageFail.toString(), Toast.LENGTH_SHORT).show();
            }
        } else if ((countDuplicate + countNoSpace) > 1) {
            StringBuilder messageFail;
            if (countNoSpace != 0) {
                messageFail = new StringBuilder();
                messageFail.append(context.getString(R.string.out_of_space) + "\n");
                messageFail.append(String.format(
                        context.getString(R.string.shortcut_array_install_failed), countNoSpace));
                Toast.makeText(context, messageFail.toString(), Toast.LENGTH_SHORT).show();
            }

            if (countDuplicate != 0) {
                messageFail = new StringBuilder();
                messageFail.append(String.format(
                        context.getString(R.string.shortcut_array_duplicate), countDuplicate)
                        + "\n");
                Toast.makeText(context, messageFail.toString(), Toast.LENGTH_SHORT).show();
            }

        }
    }

    private static boolean installShortcut(Context context, Intent data, ArrayList<ItemInfo> items,
            String name, final Intent intent, final int screen, boolean shortcutExists,
            final SharedPreferences sharedPrefs, int[] result) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "installShortcut data = " + data + ", items = " + items
                    + ", name = " + name + ", intent = " + intent + ", screen = " + screen
                    + ", shortcutExists = " + shortcutExists);
        }

        int[] tmpCoordinates = new int[2];
        if (findEmptyCell(context, items, tmpCoordinates, screen)) {
            if (intent != null) {
                if (intent.getAction() == null) {
                    intent.setAction(Intent.ACTION_VIEW);
                } else if (intent.getAction().equals(Intent.ACTION_MAIN) &&
                        intent.getCategories() != null &&
                        intent.getCategories().contains(Intent.CATEGORY_LAUNCHER)) {
                    intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                }

                // By default, we allow for duplicate entries (located in
                // different places)
                boolean duplicate = data.getBooleanExtra(Launcher.EXTRA_SHORTCUT_DUPLICATE, true);
                if (duplicate || !shortcutExists) {
                    new Thread("setNewAppsThread") {
                        public void run() {
                            synchronized (sLock) {
                                // If the new app is going to fall into the same page as before,
                                // then just continue adding to the current page
                                final int newAppsScreen = sharedPrefs.getInt(
                                        NEW_APPS_PAGE_KEY, screen);
                                SharedPreferences.Editor editor = sharedPrefs.edit();
                                if (newAppsScreen == -1 || newAppsScreen == screen) {
                                    addToStringSet(sharedPrefs,
                                        editor, NEW_APPS_LIST_KEY, intent.toUri(0));
                                }
                                editor.putInt(NEW_APPS_PAGE_KEY, screen);
                                editor.commit();
                            }
                        }
                    }.start();

                    // Update the Launcher db
                    LauncherApplication app = (LauncherApplication) context.getApplicationContext();
                    ShortcutInfo info = app.getModel().addShortcut(context, data,
                            LauncherSettings.Favorites.CONTAINER_DESKTOP, screen,
                            tmpCoordinates[0], tmpCoordinates[1], true);
                    if (info == null) {
                        result[0] = INSTALL_SHORTCUT_ADD_FAIL;
                        LauncherLog.w(TAG, "InstallShortcut Failed: Due to ShortcutInfo is null");
                        return false;
                    }
                    sItemsAddingToDatabase.add(info);
                    result[0] = INSTALL_SHORTCUT_SUCCESSFUL;
                    if (LauncherLog.DEBUG) {
                        LauncherLog.d(TAG, "InstallShortcut Successfully: Install the " + info.title);
                    }
                } else {
                    LauncherLog.w(TAG, "InstallShortcut Failed: Already Exist!");
                    result[0] = INSTALL_SHORTCUT_IS_DUPLICATE;
                }

                return true;
            }
        } else {
            LauncherLog.w(TAG, "InstallShortcut Failed: No Space!");
            result[0] = INSTALL_SHORTCUT_NO_SPACE;
        }

        return false;
    }

    private static boolean findEmptyCell(Context context, ArrayList<ItemInfo> items, int[] xy,
            int screen) {
        final int xCount = LauncherModel.getCellCountX();
        final int yCount = LauncherModel.getCellCountY();
        boolean[][] occupied = new boolean[xCount][yCount];

        ItemInfo item = null;
        int cellX, cellY, spanX, spanY;
        for (int i = 0; i < items.size(); ++i) {
            item = items.get(i);
            if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                if (item.screen == screen) {
                    cellX = item.cellX;
                    cellY = item.cellY;
                    spanX = item.spanX;
                    spanY = item.spanY;
                    for (int x = cellX; 0 <= x && x < cellX + spanX && x < xCount; x++) {
                        for (int y = cellY; 0 <= y && y < cellY + spanY && y < yCount; y++) {
                            occupied[x][y] = true;
                        }
                    }
                }
            }
        }

        /// M: Some items may be adding to database but not added now, so let us check these items
        ///    to avoid the different items are added into the same place @{
        for (int j = 0; j < sItemsAddingToDatabase.size(); ++j) {
            item = sItemsAddingToDatabase.get(j);
            if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                if (item.screen == screen) {
                    cellX = item.cellX;
                    cellY = item.cellY;
                    spanX = item.spanX;
                    spanY = item.spanY;
                    for (int x = cellX; 0 <= x && x < cellX + spanX && x < xCount; x++) {
                        for (int y = cellY; 0 <= y && y < cellY + spanY && y < yCount; y++) {
                            occupied[x][y] = true;
                        }
                    }
                }
            }
        }
        /// M: }@

        return CellLayout.findVacantCell(xy, 1, 1, xCount, yCount, occupied);
    }
}
