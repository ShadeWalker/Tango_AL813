package com.android.systemui.floatpanel;

import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import com.android.systemui.floatpanel.FloatAppAdapter.IconResizer;

/**
 * M: The icon data for entrance bar and more area in multi-window entrance.
 */
public class FloatAppItem {
    public ResolveInfo resolveInfo;
    public CharSequence label;
    public Drawable icon;
    public String packageName;
    public String className;
    public Bundle extras;
    public int position;
    public int container;
    public boolean visible = true;

    FloatAppItem(PackageManager pm, ResolveInfo info, IconResizer resizer,
            int pos) {
        resolveInfo = info;
        label = resolveInfo.loadLabel(pm);
        ComponentInfo ci = resolveInfo.activityInfo;
        if (ci == null) {
            ci = resolveInfo.serviceInfo;
        }

        if (label == null && ci != null) {
            label = resolveInfo.activityInfo.name;
        }

        if (resizer != null) {
            icon = resizer.createIconThumbnail(resolveInfo.loadIcon(pm));
        }
        packageName = ci.applicationInfo.packageName;
        className = ci.name;
        position = pos;
    }

    public FloatAppItem() {
    }

    public String toString() {
        return "FloatAppItem{" + packageName + "/" + className + ":vis = "
                + visible + ", pos = " + position + "}";
    }
}
