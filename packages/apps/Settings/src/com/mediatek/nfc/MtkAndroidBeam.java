/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.mediatek.nfc;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.SettingsActivity;
import com.android.settings.widget.SwitchBar;
import com.mediatek.beam.BeamShareHistory;
import com.mediatek.settings.FeatureOption;
import com.mediatek.xlog.Xlog;

public class MtkAndroidBeam extends Fragment 
        implements SwitchBar.OnSwitchChangeListener {
    private final static int MENU_SHOW_RECEIVED_FILES = 0;
    private View mView;
    private NfcAdapter mNfcAdapter;
    private SwitchBar mSwitchBar;
    
    private CharSequence mOldActivityTitle;
    private IntentFilter mIntentFilter;
    ///M: indicate whether need to enable/disable beam or just update the preference
    private boolean mUpdateStatusOnly = false;

    private static final String TAG = "MtkAndroidBeam";

    // M: The broadcast receiver is used to handle the nfc adapter state changed
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (NfcAdapter.ACTION_ADAPTER_STATE_CHANGED.equals(action)) {
                updateSwitchButton();
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());

        mIntentFilter = new IntentFilter(
                NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        setHasOptionsMenu(true);
    }
    
    
    @Override
    public void onStart() {
        super.onStart();

        Xlog.d(TAG, "onStart, mSwitchBar addOnSwitchChangeListener ");        
        // On/off switch 
        final SettingsActivity activity = (SettingsActivity) getActivity();
        mSwitchBar = activity.getSwitchBar();
        mSwitchBar.addOnSwitchChangeListener(this);
        mSwitchBar.show();
    } 
           
    @Override
    public void onStop() {
        super.onStop();

        Xlog.d(TAG, "onStop, mSwitchBar removeOnSwitchChangeListener ");
        mSwitchBar.removeOnSwitchChangeListener(this);
        mSwitchBar.hide();
    }
        
    public void onResume() {
        super.onResume();
        Xlog.d(TAG, "onResume ");        

        // M: add a receiver a monitor nfc state change
        getActivity().registerReceiver(mReceiver, mIntentFilter);        
        //M: when resume the activity, refresh the switch button
        updateSwitchButton();

    }

    private void updateSwitchButton() {
        if(mNfcAdapter != null) {
            mUpdateStatusOnly = true;
            mSwitchBar.setChecked(mNfcAdapter.isNdefPushEnabled());
            mUpdateStatusOnly = false;
            mSwitchBar.setEnabled(mNfcAdapter.getAdapterState() == NfcAdapter.STATE_ON);
        }
    }

    public void onPause() {
        Xlog.d(TAG, "onPause ");  
        super.onPause();
        getActivity().unregisterReceiver(mReceiver);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Xlog.d(TAG, "onCreateView ");                  
        if(FeatureOption.MTK_BEAM_PLUS_SUPPORT) {
            mView = inflater.inflate(R.layout.android_beam_plus, container, false);
            Utils.prepareCustomPreferencesList(container, mView, mView, false);
        } else {
            mView = inflater.inflate(R.layout.android_beam, container, false);
        }
        
        return mView;
    }
    
    @Override
    public void onDestroyView() {
        Xlog.d(TAG, "onDestroyView,");          
        super.onDestroyView();        
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if(FeatureOption.MTK_BEAM_PLUS_SUPPORT) {
            menu.add(Menu.NONE, MENU_SHOW_RECEIVED_FILES, 0, R.string.beam_share_history_title)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);      
        }  
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == MENU_SHOW_RECEIVED_FILES) {
           ((SettingsActivity) getActivity()).startPreferencePanel(
                BeamShareHistory.class.getName(), null , 0, null, null, 0);
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onSwitchChanged(Switch switchView, boolean desiredState) {
        Xlog.d(TAG, "mUpdateStatusOnly is" + mUpdateStatusOnly);
        if(!mUpdateStatusOnly) {
            boolean success = false;
            mSwitchBar.setEnabled(false);

            if (desiredState) {
                success = mNfcAdapter.enableNdefPush();
            } else {
                success = mNfcAdapter.disableNdefPush();
            }
            if (success) {
                mSwitchBar.setChecked(desiredState);
            }
            Xlog.d(TAG, "set Ndef push " + desiredState  + " success " + success);
            mSwitchBar.setEnabled(true);
        }
    }
    
    
}
