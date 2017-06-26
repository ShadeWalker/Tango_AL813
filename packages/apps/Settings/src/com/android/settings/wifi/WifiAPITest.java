/*
 * Copyright (C) 2009 The Android Open Source Project
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

import com.android.settings.R;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.WifiManager;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;


/**
 * Provide an interface for testing out the Wifi API
 */
public class WifiAPITest extends PreferenceActivity implements
Preference.OnPreferenceClickListener {

    private static final String TAG = "WifiAPITest";
    private int netid;

    //============================
    // Preference/activity member variables
    //============================

    private static final String KEY_DISCONNECT = "disconnect";
    private static final String KEY_DISABLE_NETWORK = "disable_network";
    private static final String KEY_ENABLE_NETWORK = "enable_network";

    private Preference mWifiDisconnect;
    private Preference mWifiDisableNetwork;
    private Preference mWifiEnableNetwork;
	//add by wanghui for al812 leaked
	private AlertDialog alertDialog;

    private WifiManager mWifiManager;


    //============================
    // Activity lifecycle
    //============================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        onCreatePreferences();
        mWifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
    }
    //add by wanghui for al812 leaked
	@Override
    protected void onDestroy(){
        if(alertDialog != null){
            alertDialog.dismiss();
		}
        super.onDestroy();
	}
    private void onCreatePreferences() {
        addPreferencesFromResource(R.layout.wifi_api_test);

        final PreferenceScreen preferenceScreen = getPreferenceScreen();

        mWifiDisconnect = (Preference) preferenceScreen.findPreference(KEY_DISCONNECT);
        mWifiDisconnect.setOnPreferenceClickListener(this);

        mWifiDisableNetwork = (Preference) preferenceScreen.findPreference(KEY_DISABLE_NETWORK);
        mWifiDisableNetwork.setOnPreferenceClickListener(this);

        mWifiEnableNetwork = (Preference) preferenceScreen.findPreference(KEY_ENABLE_NETWORK);
        mWifiEnableNetwork.setOnPreferenceClickListener(this);

    }

    //============================
    // Preference callbacks
    //============================

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        super.onPreferenceTreeClick(preferenceScreen, preference);
        return false;
    }

    /**
     *  Implements OnPreferenceClickListener interface
     */
    public boolean onPreferenceClick(Preference pref) {
        if (pref == mWifiDisconnect) {
            mWifiManager.disconnect();
        } else if (pref == mWifiDisableNetwork) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle("Input");
            alert.setMessage("Enter Network ID");
            // Set an EditText view to get user input
            final EditText input = new EditText(this);
            /* HQ_jiazaizheng 2015-10-07 modified for crash HQ01425354 begin */
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            alert.setView(input);
            alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    Editable value = input.getText();
                    String str = value.toString();
                    if (!TextUtils.isEmpty(str) && str.length() <= 10) {
                       netid = Integer.parseInt(str);
                    }
                    /* HQ_jiazaizheng 2015-10-07 modified end */

                    mWifiManager.disableNetwork(netid);
                    }
                    });
            alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    // Canceled.
                    }
                    });
			alertDialog = alert.create();
            alert.show();
        } else if (pref == mWifiEnableNetwork) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle("Input");
            alert.setMessage("Enter Network ID");
            // Set an EditText view to get user input
            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            alert.setView(input);
            alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    Editable value = input.getText();
                    String str = value.toString();
                    if(!TextUtils.isEmpty(str) && str.length() <= 10) {
                    	netid =  Integer.parseInt(str.toString());
                    }
                    mWifiManager.enableNetwork(netid, false);
                    }
                    });
            alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    // Canceled.
                    }
                    });	
			alertDialog = alert.create();
            alert.show();
        }
        return true;
    }
}
