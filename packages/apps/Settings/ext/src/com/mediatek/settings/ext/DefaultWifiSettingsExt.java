package com.mediatek.settings.ext;

import android.content.ContentResolver;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.WifiConfiguration;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.view.ContextMenu;
import java.util.ArrayList;
import java.util.List;

/* Dummy implmentation , do nothing */
public class DefaultWifiSettingsExt implements IWifiSettingsExt {
    private static final String TAG = "DefaultWifiSettingsExt";

    //HQ_yulifeng eap-sim HQ01308716 begin
    private static final String WIFI_AUTO_SSID = "Singtel WiFi";
    private static final String CHT_AUTO_SSID = "CHT Wi-Fi Auto";
    private static final String FET_AUTO_SSID = "FET Wi-Fi Auto";
	private static final String PERSONAL_AUTO_SSID = "Personal EAP";
	private static final String MOVISTAR_AUTO_SSID = "MOVISTAR WIFI";
	private static final String VIVO_AUTO_SSID = "VIVO-WIFI";
	private static final String WIFI_HOTSPOT_AUTO_SSID = "WiFi Hotspot";
    static final int SECURITY_EAP = 5; 
    //HQ_yulifeng eap-sim HQ01308716 end

    public void registerPriorityObserver(ContentResolver contentResolver) {
    }
    public void unregisterPriorityObserver(ContentResolver contentResolver) {
    }
    public void setLastConnectedConfig(WifiConfiguration config) {
    }
    public void setLastPriority(int priority) {
    }
    public void updatePriority() {
    }
    public  void updateContextMenu(ContextMenu menu, int menuId, DetailedState state) {

    }

    public void setCategory(PreferenceCategory trustPref, PreferenceCategory configedPref,
            PreferenceCategory newPref) {
    }
    public void emptyCategory(PreferenceScreen screen) {
        screen.removeAll();
    }
    public void emptyScreen(PreferenceScreen screen) {
        screen.removeAll();
    }
    public boolean isTustAP(String ssid, int security) {
        //return false;
        return isWifiAutoAp(ssid,security);//chenwenshuai for HQ01569253
    }

    //HQ_yulifeng eap-sim HQ01308716 begin
    private boolean isWifiAutoAp(String ssid, int security) {
        if ((WIFI_AUTO_SSID.equals(ssid)||CHT_AUTO_SSID.equals(ssid)||FET_AUTO_SSID.equals(ssid)||
			 PERSONAL_AUTO_SSID.equals(ssid)||MOVISTAR_AUTO_SSID.equals(ssid)||VIVO_AUTO_SSID.equals(ssid)||
			 WIFI_HOTSPOT_AUTO_SSID.equals(ssid))&& SECURITY_EAP == security) {//chenwenshuai for HQ01569253
        //if ((WIFI_AUTO_SSID.equals(ssid))&& SECURITY_EAP == security) {
            return true;
        } 
        return false;
    }//end
    
    public void refreshCategory(PreferenceScreen screen) {
    }
    public int getAccessPointsCount(PreferenceScreen screen) {
        return screen.getPreferenceCount();
    }
    public void adjustPriority() {
    }
    public void recordPriority(int selectPriority) {
    }
    public void setNewPriority(WifiConfiguration config) {
    }
    public void updatePriorityAfterSubmit(WifiConfiguration config) {
    }
    public void disconnect(int networkId) {
    }
    public void updatePriorityAfterConnect(int networkId) {
    }
    public void addPreference(PreferenceScreen screen, Preference preference, boolean isConfiged) {
        if (screen != null) {
            screen.addPreference(preference);
        }
    }

    public Preference getPreference(PreferenceScreen preferenceScreen, int index) {
        return preferenceScreen.getPreference(index);
    }

    public void addCategories(PreferenceScreen screen) {

    }

    public List<PreferenceGroup> getPreferenceCategory(PreferenceScreen screen) {
        List<PreferenceGroup> preferenceCategoryList = new ArrayList<PreferenceGroup>();
        preferenceCategoryList.add(screen);
        return preferenceCategoryList;
    }
}
