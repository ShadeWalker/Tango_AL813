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

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.SystemProperties;
import android.os.UserHandle;

import com.mediatek.launcher2.ext.DataUtil;
import com.mediatek.launcher2.ext.LauncherLog;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


/**
 * Cache of application icons.  Icons can be made from any thread.
 */
public class IconCache {
    @SuppressWarnings("unused")
    private static final String TAG = "IconCache";

    private static final int INITIAL_ICON_CACHE_CAPACITY = 50;

    private static class CacheEntry {
        public Bitmap icon;
        public String title;
        public CharSequence contentDescription;
    }

    private static class CacheKey {
        public ComponentName componentName;
        public UserHandle user;

        CacheKey(ComponentName componentName, UserHandle user) {
            this.componentName = componentName;
            this.user = user;
        }

        @Override
        public int hashCode() {
            return componentName.hashCode() + user.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            CacheKey other = (CacheKey) o;
            return other.componentName.equals(componentName) && other.user.equals(user);
        }
    }

    private final Bitmap mDefaultIcon;
    private final Context mContext;
    private final PackageManager mPackageManager;
    private final HashMap<CacheKey, CacheEntry> mCache =
            new HashMap<CacheKey, CacheEntry>(INITIAL_ICON_CACHE_CAPACITY);
    private int mIconDpi;

    public IconCache(Context context) {
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        mContext = context;
        mPackageManager = context.getPackageManager();
        mIconDpi = activityManager.getLauncherLargeIconDensity();

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "IconCache, mIconDpi = " + mIconDpi);
        }

        // need to set mIconDpi before getting default icon
        mDefaultIcon = makeDefaultIcon();
    }

    public Drawable getFullResDefaultActivityIcon() {
        return getFullResIcon(Resources.getSystem(),
                android.R.mipmap.sym_def_app_icon, android.os.Process.myUserHandle());
    }

    public Drawable getFullResIcon(Resources resources, int iconId, UserHandle user) {
        Drawable d;
        try {
            d = resources.getDrawableForDensity(iconId, mIconDpi);
        } catch (Resources.NotFoundException e) {
            d = null;
        }

        if (d == null) {
            d = getFullResDefaultActivityIcon();
        }
        return mPackageManager.getUserBadgedIcon(d, user);
    }

    public Drawable getFullResIcon(String packageName, int iconId, UserHandle user) {
        Resources resources;
        try {
            // TODO: Check if this needs to use the user param if we support
            // shortcuts/widgets from other profiles. It won't work as is
            // for packages that are only available in a different user profile.
            resources = mPackageManager.getResourcesForApplication(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            resources = null;
        }
        if (resources != null) {
            if (iconId != 0) {
                return getFullResIcon(resources, iconId, user);
            }
        }
        return getFullResDefaultActivityIcon();
    }

    public Drawable getFullResIcon(ResolveInfo info, UserHandle user) {
        return getFullResIcon(info.activityInfo, user);
    }

    public Drawable getFullResIcon(ActivityInfo info, UserHandle user) {
        Resources resources;
        try {
            resources = mPackageManager.getResourcesForApplication(
                    info.applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            resources = null;
        }
        if (resources != null) {
            int iconId = info.getIconResource();
            if (iconId != 0) {
                return getFullResIcon(resources, iconId, user);
            }
        }
        return getFullResDefaultActivityIcon();
    }

    private Bitmap makeDefaultIcon() {
        Drawable d = getFullResDefaultActivityIcon();
        Bitmap b = Bitmap.createBitmap(Math.max(d.getIntrinsicWidth(), 1),
                Math.max(d.getIntrinsicHeight(), 1),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, b.getWidth(), b.getHeight());
        d.draw(c);
        c.setBitmap(null);
        return b;
    }

    /**
     * Remove any records for the supplied ComponentName.
     */
    public void remove(ComponentName componentName) {
        synchronized (mCache) {
            mCache.remove(componentName);
        }
    }

    /**
     * Empty out the cache.
     */
    public void flush() {
        synchronized (mCache) {
            /// M: Cause GC free memory
            /// M: ALPS01757560, use Iterator.
            /*Iterator iter = mCache.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry entry = (Map.Entry) iter.next();
                CacheEntry e = (CacheEntry) entry.getValue();
                e.icon = null;
                e.title = null;
                iter.remove();
            }*/

            mCache.clear();

            /// M: Add for smart book feature. Need to update mIconDpi when plug in/out smart book.
            if (SystemProperties.get("ro.mtk_smartbook_support").equals("1")) {
                ActivityManager activityManager =
                        (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                mIconDpi = activityManager.getLauncherLargeIconDensity();
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "flush, mIconDpi = " + mIconDpi);
                }
            }
        }

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "Flush icon cache here.");
        }
    }

    /**
     * Fill in "application" with the icon and label for "info."
     */
    public void getTitleAndIcon(ApplicationInfo application, LauncherActivityInfo info,
            HashMap<Object, CharSequence> labelCache) {

        CacheEntry entry = cacheLocked(application.componentName, info, labelCache,
            info.getUser());

        application.title = entry.title;
        application.iconBitmap = entry.icon;
        application.contentDescription = entry.contentDescription;
    }

    public Bitmap getIcon(Intent intent, UserHandle user) {
        LauncherApps launcherApps = (LauncherApps)
            mContext.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        final LauncherActivityInfo launcherActInfo =
            launcherApps.resolveActivity(intent, user);
        ComponentName component = intent.getComponent();

        if (launcherActInfo == null || component == null) {
            return mDefaultIcon;
        }

        CacheEntry entry = cacheLocked(component, launcherActInfo, null, user);
        return entry.icon;
    }

    public Bitmap getIcon(ComponentName component, LauncherActivityInfo info,
            HashMap<Object, CharSequence> labelCache) {
        if (info == null || component == null) {
            return null;
        }

        CacheEntry entry = cacheLocked(component, info, labelCache, info.getUser());
        return entry.icon;

    }

    public boolean isDefaultIcon(Bitmap icon) {
        return mDefaultIcon == icon;
    }

    private CacheEntry cacheLocked(ComponentName componentName, LauncherActivityInfo info,
            HashMap<Object, CharSequence> labelCache, UserHandle user) {
        CacheKey cacheKey = new CacheKey(componentName, user);
        CacheEntry entry = mCache.get(cacheKey);

        if (entry == null) {
            entry = new CacheEntry();

            mCache.put(cacheKey, entry);

            ComponentName key = info.getComponentName();
            if (labelCache != null && labelCache.containsKey(key)) {
                entry.title = labelCache.get(key).toString();
                if (LauncherLog.DEBUG_LOADERS) {
                    LauncherLog.d(TAG, "CacheLocked get title from cache: title = " + entry.title);
                }
            } else {
                entry.title = info.getLabel().toString();
				LauncherLog.d(TAG, "CacheLocked, info.getLabel().toString() = " + entry.title);
                if (labelCache != null) {
                    labelCache.put(key, entry.title);
                }
            }
            if (entry.title == null) {
                entry.title = info.getComponentName().getShortClassName();
				LauncherLog.d(TAG, "CacheLocked, info.getComponentName().getShortClassName() = " + entry.title);
            }
            entry.contentDescription = mPackageManager.getUserBadgedLabel(entry.title, user);
            entry.icon = DataUtil.getInstance().createIconBitmap(
                info.getBadgedIcon(mIconDpi), mContext);
        }
        return entry;
    }
}
