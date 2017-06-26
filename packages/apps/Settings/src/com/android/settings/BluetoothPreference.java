/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.settings;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.preference.ListPreference;
import android.preference.Preference;
import android.widget.LinearLayout;
import com.android.settings.bluetooth.LocalBluetoothAdapter;
import com.android.settings.bluetooth.LocalBluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.util.Log;
import android.content.Intent;
import android.content.IntentFilter;
import android.preference.PreferenceManager;
import android.content.BroadcastReceiver;

//public class BluetoothPreference extends ListPreference {
public class BluetoothPreference extends Preference 
         implements PreferenceManager.OnActivityStopListener{
    private static final String LOG_TAG = "BluetoothPreference";

    private Context mContext;
    private TextView mPrefStatusView;
    private final IntentFilter mIntentFilter;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final int state =
                intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
			Log.d(LOG_TAG, "onReceive state=" + state);
			Log.d(LOG_TAG, "onReceive action=" + action);

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                updateBluetoothStatus(mPrefStatusView);
            }
        }
 
    };


    public BluetoothPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mIntentFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        final LayoutInflater layoutInflater =
            (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        
        View viewGroup = layoutInflater.inflate(R.layout.preference_commonsettings_item, null);
        LinearLayout frame = (LinearLayout) viewGroup.findViewById(R.id.frame);
        mPrefStatusView = (TextView) frame.findViewById(R.id.pref_status);
        mContext.registerReceiver(mReceiver, mIntentFilter);


        return frame;
    }

	/* HQ_ChenWenshuai 2015-11-05 modified for HQ01454819 begin */
	public void unregisterReceiver(){
		if (mReceiver != null && mContext!=null) {
            mContext.unregisterReceiver(mReceiver);
        }
	}
	/*HQ_ChenWenshuai 2015-11-05 modified end */

    public void onActivityStop() {
        if (mReceiver != null && mContext!=null) {
            mContext.unregisterReceiver(mReceiver);
        }
     }

   @Override
   protected void onBindView(View view) {
      super.onBindView(view);
        updateBluetoothStatus(mPrefStatusView);       
    }

     private void updateBluetoothStatus(TextView prefStatusTextView) {
        LocalBluetoothManager manager = LocalBluetoothManager.getInstance(mContext);
	LocalBluetoothAdapter localAdapter = manager.getBluetoothAdapter();
	handleStateChanged(localAdapter.getBluetoothState(), prefStatusTextView);
     }

	 void handleStateChanged(int state, TextView prefStatusTextView) {
        switch (state) {
            case BluetoothAdapter.STATE_TURNING_ON:
            case BluetoothAdapter.STATE_ON:
		prefStatusTextView.setText(R.string.switch_on_text);
                Log.d(LOG_TAG, "turn bluetooth on");
                break;
            case BluetoothAdapter.STATE_TURNING_OFF:
            case BluetoothAdapter.STATE_OFF:
		prefStatusTextView.setText(R.string.switch_off_text);
                Log.d(LOG_TAG, "turn bluetooth off");
                /// @}
                break;
            default:
	        Log.d(LOG_TAG, "By default, turn bluetooth off");
		prefStatusTextView.setText(R.string.switch_off_text);
        }
    }

    @Override
    protected void onClick() {
        // Ignore this until an explicit call to click()
    }

    public void click() {
        super.onClick();
    }
}

