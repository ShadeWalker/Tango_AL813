package com.mediatek.settings.ext;

import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.View;

/* Dummy implmentation , do nothing */
public class DefaultWfcSettingsExt implements IWfcSettingsExt {
    private static final String TAG = "DefaultWfcSettingsExt";

    public boolean showWfcTetheringAlertDialog(Context context) {
        return false;
    }

    public void customizedWfcPreference(Context context, PreferenceScreen preferenceScreen) {

    }
    public void modifyWfcPreferenceDialog(Context cntx, View dialog, Bundle dialogData) {
        Log.d(TAG, "getWfcDialogView" + " --Default implement");
    }
}
