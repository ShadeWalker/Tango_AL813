package com.mediatek.settings.ext;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.net.NetworkInfo.DetailedState;
import android.preference.ListPreference;
import android.preference.PreferenceScreen;
import android.provider.Settings.Global;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ArrayAdapter;

import com.mediatek.xlog.Xlog;

public class DefaultWifiExt implements IWifiExt {
    private static final String TAG = "DefaultWifiExt";
    private Context mContext;

    public DefaultWifiExt(Context context) {
        mContext = context;
        Xlog.d(TAG, "DefaultWifiExt");
    }
    public String getWifiApSsid() {
        return mContext.getString(
            com.android.internal.R.string.wifi_tether_configure_ssid_default);
    }
    public void setAPNetworkId(int apNetworkId) {
    }
    public void setAPPriority(int apPriority) {
    }
    public View getPriorityView() {
        //view.findViewById(priorityId).setVisibility(View.GONE);
        return null;
    }
    public void setSecurityText(TextView view) {
    }
    public String getSecurityText(String security) {
        return security;
    }

    public void addDisconnectButton(AlertDialog dialog, boolean edit, DetailedState state, int networkId) {

    }
    public int getPriority() {
        return -1;
    }
    public void closeSpinnerDialog() {
    }
    public void setProxyText(TextView view) {
    }
//advanced wifi settings
    public void initConnectView(Activity activity, PreferenceScreen screen) {
    }

    public void initNetworkInfoView(PreferenceScreen screen) {
    }
    public void refreshNetworkInfoView() {
    }
    public void initPreference(ContentResolver contentResolver) {
    }
    public int getSleepPolicy(ContentResolver contentResolver) {
        return Global.getInt(contentResolver, Global.WIFI_SLEEP_POLICY, Global.WIFI_SLEEP_POLICY_NEVER);
    }

    //access point
    public int getApOrder(String currentSsid, int currentSecurity, String otherSsid, int otherSecurity) {
        // Xlog.d(TAG,"getApOrder(),return 0");
        return 0;
    }

    public void setSleepPolicyPreference(ListPreference sleepPolicyPref, String[] sleepPolicyEntries,
            String[] sleepPolicyValues) {
    }

    public void hideWifiConfigInfo(Builder builder, Context context) {

    }
    public void setEapMethodArray(ArrayAdapter adapter, String ssid, int security) {
    }

    //wifi controller
    public int getEapMethodbySpinnerPos(int spinnerPos, String ssid, int security) {
        return spinnerPos;
    }

    public int getPosByEapMethod(int spinnerPos, String ssid, int security) {
        return spinnerPos;
    }
}
