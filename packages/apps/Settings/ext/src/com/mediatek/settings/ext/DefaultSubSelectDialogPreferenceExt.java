package com.mediatek.settings.ext;

import android.content.Context;
import android.preference.Preference;
import android.telephony.SubscriptionInfo;
import android.view.View;
import android.view.ViewGroup;

import com.mediatek.xlog.Xlog;

import java.util.List;

public class DefaultSubSelectDialogPreferenceExt implements ISubSelectDialogPreferenceExt {

    private static final String TAG = "DefaultSubSelectDialogPreferenceExt";

    public DefaultSubSelectDialogPreferenceExt(Context base) {
    }

    @Override
    public void updateDefaultSimPreferenceLayout(Preference pref) {
        Xlog.d(TAG, "updateDefaultSimPreferenceLayout");
    }

    @Override
    public View onCreateView(Preference pref, ViewGroup parent) {
        return null;
    }

    @Override
    public void updateDefaultSimPreferenceSimIndicator(View viewContainer,
            List<SubscriptionInfo> subInfos, int index) {
        // TODO Auto-generated method stub
        Xlog.d(TAG, "updateDefaultSimPreferenceLayout");
    }
}
