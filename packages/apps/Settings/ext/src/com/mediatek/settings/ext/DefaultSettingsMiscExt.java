package com.mediatek.settings.ext;

import android.content.Context;
import android.content.ContextWrapper;
import android.preference.Preference;
import android.preference.PreferenceActivity.Header;
import android.preference.PreferenceScreen;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.HashMap;


/* Dummy implmentation , do nothing */
public class DefaultSettingsMiscExt extends ContextWrapper implements ISettingsMiscExt {

    private static final String TAG = "DefaultSettingsMiscExt";
    ///M: for changed the dashboard location and image's drawable
    public static final int TYPE_LOCATION = 1;
    public static final int TYPE_IMAGE = 2;
	private static final String CUSTOMIZE_ITEM_INDEX = "customize_item_index";
    public DefaultSettingsMiscExt(Context base) {
        super(base);
    }

    public String getTetherWifiSSID(Context ctx) {
        return ctx.getString(
                com.android.internal.R.string.wifi_tether_configure_ssid_default);
    }

    public void setTimeoutPrefTitle(Preference pref) {
    }

    public void setFactoryResetTitle(Object obj) {
    }

    public void addCustomizedItem(List<Header> target, int index) {
    }

    public String customizeSimDisplayString(String simString, int slotId) {
        return simString;
    }

    public void customizeLocationHeaderClick(Header header) {
    }

    public void initCustomizedLocationSettings(PreferenceScreen root, int order) {
    }

    public void updateCustomizedLocationSettings() {
    }

    public boolean needCustomizeHeaderIcon(Header header) {
        return false;
    }

    public void customizeHeaderIcon(ImageView iconView, Header header) {
    }

    @Override
    public void initSwitherControlers() {
    }

    @Override
    public void setEnablerSwitch(Header header, Switch switch_) {
    }

    @Override
    public boolean isSwitcherHeaderType(Header header) {
        return false;
    }

    @Override
    public void resumeEnabler() {
    }

    @Override
    public void pauseEnabler() {
    }

    @Override
    public boolean isFeatureEnable() {
        return true;
    }

    @Override
    public void addCustomizedItem(Object targetDashboardCategory, int index) {
        android.util.Log.i(TAG, "DefaultSettingsMisc addCustomizedItem method going");
    }

    @Override
    public void customizeDashboardTile(Object tile,
            ViewGroup categoryContent, View tileView,
            ImageView tileIcon, int type) {
        if (type == TYPE_LOCATION) {
            categoryContent.addView(tileView);
        } else if (type == TYPE_IMAGE) {
            tileIcon.setImageDrawable(null);
            tileIcon.setBackground(null);
        }
    }
	
    @Override
    public void updataDefaultDataConnection(Map<String, Boolean> DataEnableMap, Context context) {
    }

    @Override
    public void setDefaultDataEnable(Context context, int subid) {
    }

    @Override
    public boolean useCTTestcard() {
       return false;
    }

}

