package com.mediatek.settings.ext;

import android.content.Context;
import android.content.ContextWrapper;
import android.preference.PreferenceGroup;

import android.util.Log;

public class DefaultDataProtectionExt extends ContextWrapper implements IDataProtectionExt {
    private static final String TAG = "DefaultDataProectionExt";
    public DefaultDataProtectionExt(Context context) {
        super(context);
    }

    public void addDataPrf(PreferenceGroup prefGroup) {
        Log.d(TAG, "will not add data protection preference by default");
    }
}
