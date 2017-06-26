/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.settings.wifi;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.telephony.PhoneNumberUtils;
import android.os.SystemProperties;

import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.SearchIndexableRaw;

import android.util.Log;
import android.view.View;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UI to manage saved networks/access points.
 */
public class SavedAccessPointsWifiSettings extends SettingsPreferenceFragment
        implements DialogInterface.OnClickListener, Indexable {
    private static final String TAG = "SavedAccessPointsWifiSettings";
    /*add by yulifeng for eap-sim,b*/
    private static final String WIFI_AUTO_SSID = "Singtel WiFi";
    private static final String CHT_AUTO_SSID = "CHT Wi-Fi Auto";
    private static final String FET_AUTO_SSID = "FET Wi-Fi Auto";
    private static final String PERSONAL_AUTO_SSID = "Personal EAP";
	private static final String MOVISTAR_AUTO_SSID = "MOVISTAR WIFI";
	private static final String VIVO_AUTO_SSID = "VIVO-WIFI";
	private static final String WIFI_HOTSPOT_AUTO_SSID = "WiFi Hotspot";
    static final int SECURITY_EAP = 5;
    /*add by yulifeng for eap-sim,e*/

    private WifiDialog mDialog;
    private WifiManager mWifiManager;
    private AccessPoint mDlgAccessPoint;
    private Bundle mAccessPointSavedState;
    private AccessPoint mSelectedAccessPoint;

    // Instance state key
    private static final String SAVE_DIALOG_ACCESS_POINT_STATE = "wifi_ap_state";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.wifi_display_saved_access_points);
    }

    @Override
    public void onResume() {
        super.onResume();
        initPreferences();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(SAVE_DIALOG_ACCESS_POINT_STATE)) {
                mAccessPointSavedState =
                    savedInstanceState.getBundle(SAVE_DIALOG_ACCESS_POINT_STATE);
            }
        }
    }

    private void initPreferences() {
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        final Context context = getActivity();

        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        final List<AccessPoint> accessPoints = constructSavedAccessPoints(context, mWifiManager);

        preferenceScreen.removeAll();

        final int accessPointsSize = accessPoints.size();
        for (int i = 0; i < accessPointsSize; ++i){
             /*HQ_yulifeng add for eap-sim ,ssid show with card,20151017,b*/
            Log.d(TAG,"ylf[initPreferences]_accessPoints.ssid"+ accessPoints.get(i).ssid);
            if(SystemProperties.get("ro.hq.southeast.wifi.eapsim").equals("1")){
                if(isNeedPreserveSsid(accessPoints.get(i).ssid,accessPoints.get(i).security)){
                    preferenceScreen.addPreference(accessPoints.get(i));
                } 
            }else{
                preferenceScreen.addPreference(accessPoints.get(i));
            }
            /*HQ_yulifeng add for eap-sim ,ssid show with card,20151017,e*/          
        }

        if(getPreferenceScreen().getPreferenceCount() < 1) {
            Log.w(TAG, "Saved networks activity loaded, but there are no saved networks!");
        }
    }

    /*HQ_yulifeng add for eap-sim,HQ01308716,b*/
    /*
    return :
        true: need Preserve
        false: need Remove
    */
    private boolean isNeedPreserveSsid(String ssid,int security) {
        boolean isNeedPreserve = true;
        String pMccMnc = PhoneNumberUtils.getSimMccMnc(0);
        String pMccMnc2 = PhoneNumberUtils.getSimMccMnc(1);
        if(WIFI_AUTO_SSID.equals(ssid)&& SECURITY_EAP == security){
            if(("52501".equals(pMccMnc)||"52501".equals(pMccMnc2)||"52502".equals(pMccMnc)||"52502".equals(pMccMnc2))){
                isNeedPreserve = true;
            }else{
                isNeedPreserve = false;
            }
        }
        if(CHT_AUTO_SSID.equals(ssid)&& SECURITY_EAP == security){
            if("46692".equals(pMccMnc)||"46692".equals(pMccMnc2)){
                isNeedPreserve = true;
            }else{
                isNeedPreserve = false;
            }
        }
        if(FET_AUTO_SSID.equals(ssid)&& SECURITY_EAP == security){
            if("46601".equals(pMccMnc)||"46601".equals(pMccMnc2)){
                isNeedPreserve = true;
            }else{
                isNeedPreserve = false;
            }
        }
        if(PERSONAL_AUTO_SSID.equals(ssid)&& SECURITY_EAP == security){
            if(("72234".equals(pMccMnc)||"72234".equals(pMccMnc2)||"722341".equals(pMccMnc)||"722341".equals(pMccMnc2))){
                isNeedPreserve = true;
            }else{
                isNeedPreserve = false;
            }
        }
		//chenwenshuai add for HQ01508640 begin
		if(MOVISTAR_AUTO_SSID.equals(ssid)&& SECURITY_EAP == security){
			if(SystemProperties.get("ro.hq.movistar.wifi.eapsim").equals("1")){
                isNeedPreserve = true;
            }else{
                isNeedPreserve = false;
            }
        }
		if(VIVO_AUTO_SSID.equals(ssid)&& SECURITY_EAP == security){
            if(SystemProperties.get("ro.hq.movistar.wifi.eapsim").equals("1")){
                isNeedPreserve = true;
            }else{
                isNeedPreserve = false;
            }
        }
		//chenwenshuai add for HQ01508640 end
		//chenwenshuai add for HQ01544217 begin
		if(WIFI_HOTSPOT_AUTO_SSID.equals(ssid)&& SECURITY_EAP == security){
            if(("334020".equals(pMccMnc)||"334020".equals(pMccMnc2))){
                isNeedPreserve = true;
            }else{
                isNeedPreserve = false;
            }
        }
		//chenwenshuai add for HQ01544217 end
        Log.d(TAG,"ylf[isNeedPreserveSsid]_isNeedPreserve="+isNeedPreserve+";ssid="+ssid+";security="+security);
        return isNeedPreserve;
    }
    /*HQ_yulifeng add for eap-sim,HQ01308716,e*/


    private static List<AccessPoint> constructSavedAccessPoints(Context context,
            WifiManager wifiManager){
        List<AccessPoint> accessPoints = new ArrayList<AccessPoint>();
        Map<String, List<ScanResult>> resultsMap = new HashMap<String, List<ScanResult>>();

        final List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
        final List<ScanResult> scanResults = wifiManager.getScanResults();

        if (configs != null) {
            //Construct a Map for quick searching of a wifi network via ssid.
            final int scanResultsSize = scanResults.size();
            for (int i = 0; i < scanResultsSize; ++i){
                final ScanResult result = scanResults.get(i);
                List<ScanResult> res = resultsMap.get(result.SSID);

                if(res == null){
                    res = new ArrayList<ScanResult>();
                    resultsMap.put(result.SSID, res);
                }

                res.add(result);
            }

            final int configsSize = configs.size();
            for (int i = 0; i < configsSize; ++i){
                WifiConfiguration config = configs.get(i);
                if (config.selfAdded && config.numAssociation == 0) {
                    continue;
                }
                AccessPoint accessPoint = new AccessPoint(context, config);
                final List<ScanResult> results = resultsMap.get(accessPoint.ssid);

                accessPoint.setShowSummary(false);
                if(results != null){
                    final int resultsSize = results.size();
                    for (int j = 0; j < resultsSize; ++j){
                        accessPoint.update(results.get(j));
                        accessPoint.setIcon(null);
                        accessPoint.setWidgetLayoutResource(R.layout.arrow_img_layout);
                    }
                }

                accessPoints.add(accessPoint);
            }
        }

        return accessPoints;
    }

    private void showDialog(AccessPoint accessPoint, boolean edit) {
        if (mDialog != null) {
            removeDialog(WifiSettings.WIFI_DIALOG_ID);
            mDialog = null;
        }

        // Save the access point and edit mode
        mDlgAccessPoint = accessPoint;

        showDialog(WifiSettings.WIFI_DIALOG_ID);
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        switch (dialogId) {
            case WifiSettings.WIFI_DIALOG_ID:
                if (mDlgAccessPoint == null) { // For re-launch from saved state
                    mDlgAccessPoint = new AccessPoint(getActivity(), mAccessPointSavedState);
                    // Reset the saved access point data
                    mAccessPointSavedState = null;
                }
                mSelectedAccessPoint = mDlgAccessPoint;
                mDialog = new WifiDialog(getActivity(), this, mDlgAccessPoint,
                        false /* not editting */, true /* hide the submit button */);
                return mDialog;

        }
        return super.onCreateDialog(dialogId);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // If the dialog is showing, save its state.
        if (mDialog != null && mDialog.isShowing()) {
            if (mDlgAccessPoint != null) {
                mAccessPointSavedState = new Bundle();
                mDlgAccessPoint.saveWifiState(mAccessPointSavedState);
                outState.putBundle(SAVE_DIALOG_ACCESS_POINT_STATE, mAccessPointSavedState);
            }
        }
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int button) {
        if (button == WifiDialog.BUTTON_FORGET && mSelectedAccessPoint != null) {
            mWifiManager.forget(mSelectedAccessPoint.networkId, null);
            /// M: get and remove the ap that matches the unique networkId  @{
            if (findSelectedAccessPoint() != null) {
                getPreferenceScreen().removePreference(findSelectedAccessPoint());
            }
            /// @}
            //getPreferenceScreen().removePreference(mSelectedAccessPoint);
            mSelectedAccessPoint = null;
        }
    }

    /// M: extract the ap selection process  @{
    private AccessPoint findSelectedAccessPoint() {
        PreferenceScreen prefScreen = getPreferenceScreen();
        int size = prefScreen.getPreferenceCount();
        for (int i = 0; i < size; i++) {
            AccessPoint ap = (AccessPoint) prefScreen.getPreference(i);
            if (ap.networkId == mSelectedAccessPoint.networkId) {
                return ap;
            }
        }
        return null;
    }
    /// @}

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        if (preference instanceof AccessPoint) {
            showDialog((AccessPoint) preference, false);
            return true;
        } else{
            return super.onPreferenceTreeClick(screen, preference);
        }
    }

    /**
     * For search.
     */
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
        new BaseSearchIndexProvider() {
            @Override
            public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
                final List<SearchIndexableRaw> result = new ArrayList<SearchIndexableRaw>();
                final Resources res = context.getResources();
                final String title = res.getString(R.string.wifi_saved_access_points_titlebar);

                // Add fragment title
                SearchIndexableRaw data = new SearchIndexableRaw(context);
                data.title = title;
                data.screenTitle = title;
                data.enabled = enabled;
                result.add(data);

                // Add available Wi-Fi access points
                WifiManager wifiManager =
                        (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                final List<AccessPoint> accessPoints =
                        constructSavedAccessPoints(context, wifiManager);

                final int accessPointsSize = accessPoints.size();
                for (int i = 0; i < accessPointsSize; ++i){
                    data = new SearchIndexableRaw(context);
                    data.title = accessPoints.get(i).getTitle().toString();
                    data.screenTitle = title;
                    data.enabled = enabled;
                    result.add(data);
                }

                return result;
            }
        };
}
