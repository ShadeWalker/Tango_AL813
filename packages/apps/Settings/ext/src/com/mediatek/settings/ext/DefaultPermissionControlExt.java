package com.mediatek.settings.ext;

import android.content.Context;
import android.content.ContextWrapper;
import android.preference.PreferenceGroup;

import android.provider.SearchIndexableData;
import com.mediatek.xlog.Xlog;

import java.util.List;

public class DefaultPermissionControlExt extends ContextWrapper implements IPermissionControlExt {
    private static final String TAG = "DefaultPermissionControlExt";
    public DefaultPermissionControlExt(Context context) {
        super(context);
    }

    public void addPermSwitchPrf(PreferenceGroup prefGroup) {
        Xlog.d(TAG, "will not add permission preference");
    }

    public void enablerResume() {
        Xlog.d(TAG, "enablerResume() default");
    }

    public void enablerPause() {
        Xlog.d(TAG, "enablerPause() default");
    }

    public void addAutoBootPrf(PreferenceGroup prefGroup) {
        Xlog.d(TAG, "will not add auto boot entry preference");
    }

    public List<SearchIndexableData> getRawDataToIndex(boolean enabled) {
          Xlog.d(TAG, "default , null");
        return null;
    }

}
