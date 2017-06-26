/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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

package com.android.settings.deviceinfo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.UserManager;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.mediatek.settings.deviceinfo.UsbSettingsExts;

/* HQ_yulisuo 2015-06-10 modified for HQ01176489 */
import com.mediatek.settings.deviceinfo.UsbDebugPreference;
import android.text.method.LinkMovementMethod;
import android.app.AlertDialog;
import android.app.Dialog;
import android.text.SpannableString; 
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.View;
import android.text.TextUtils;
import android.preference.PreferenceCategory;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.provider.Settings;
import android.os.storage.StorageManager;

/**
 * USB storage settings.
 */
public class UsbSettings extends SettingsPreferenceFragment implements
         Preference.OnPreferenceChangeListener {

    private static final String TAG = "UsbSettings";

    private static final String KEY_MTP = "usb_mtp";
    private static final String KEY_PTP = "usb_ptp";

    private UsbManager mUsbManager;
    private boolean mUsbAccessoryMode;

    private UsbSettingsExts mUsbExts;
	
	/* HQ_yulisuo 2015-06-10 modified for HQ01176489 begin */
	private UsbDebugPreference mUsbDebug;
    private Dialog mAdbDialog;
    private boolean mDialogClicked;
	/*HQ_yulisuo 2015-06-10 modified end */

	/* HQ_ChenWenshuai 2015-07-22 modified for HQ01249051*/
	private StorageManager mStorageManager = null;
	
    private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {
        public void onReceive(Context content, Intent intent) {
            String action = intent.getAction();
            if (action.equals(UsbManager.ACTION_USB_STATE)) {
               mUsbAccessoryMode = intent.getBooleanExtra(UsbManager.USB_FUNCTION_ACCESSORY, false);
               Log.e(TAG, "UsbAccessoryMode " + mUsbAccessoryMode);
            }
            mUsbExts.dealWithBroadcastEvent(intent);
            if (mUsbExts.isNeedExit()) {
                finish();
            } else if (mUsbExts.isNeedUpdate()) {
                updateToggles(mUsbExts.getCurrentFunction());
            }
        }
    };

    private PreferenceScreen createPreferenceHierarchy() {
        PreferenceScreen root = getPreferenceScreen();
        if (root != null) {
            root.removeAll();
        }
        addPreferencesFromResource(R.xml.usb_settings);
        root = mUsbExts.addUsbSettingsItem(this);
		/* HQ_yulisuo 2015-06-10 modified for HQ01176489 begin */
		String summary = getActivity().getString(R.string.enable_adb_summary_text);
       int start = summary.indexOf(".",summary.indexOf(".")+1);
       SpannableString sp = new SpannableString(summary);
       mUsbDebug = new UsbDebugPreference(getActivity());
       mUsbDebug.setTitle(R.string.enable_adb);
       mUsbDebug.setSummary(sp);
       mUsbDebug.setOnPreferenceChangeListener(this);//HQ_jiazaizheng 20150807 modify for HQ01308872
       root.addPreference(mUsbDebug);
       /*HQ_yulisuo 2015-06-10 modified end */
		
        UserManager um = (UserManager) getActivity().getSystemService(Context.USER_SERVICE);
        if (um.hasUserRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER)) {
            mUsbExts.updateEnableStatus(false);
        }

        return root;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
		/* HQ_ChenWenshuai 2015-07-22 modified for HQ01249051 begin */
		if (mStorageManager == null) {
            mStorageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
            if (mStorageManager == null) {
                Log.w(TAG, "Failed to get StorageManager");
            }
		}
		/*HQ_ChenWenshuai 2015-07-22 modified end */
        mUsbManager = (UsbManager)getSystemService(Context.USB_SERVICE);
        mUsbExts = new UsbSettingsExts();
    }
	/* HQ_yulisuo 2015-06-10 modified for HQ01176489 begin */
	public  class onClickSpan extends ClickableSpan {
       private Context mContext;
       public onClickSpan(Context  context) {
               mContext = context;
       }
       public int getSpanTypeId() {
               return TextUtils.URL_SPAN;
       }
           
       public int describeContents() {
               return 0;
       }

       @Override
       public void onClick(View widget) {
               Log.e(TAG+"-","onclick");
       }
    }
	/*HQ_yulisuo 2015-06-10 modified end */
    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mStateReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Make sure we reload the preference hierarchy since some of these settings
        // depend on others...
        createPreferenceHierarchy();

        // ACTION_USB_STATE is sticky so this will call updateToggles
        getActivity().registerReceiver(mStateReceiver,
                mUsbExts.getIntentFilter());
		/* HQ_yulisuo 2015-06-10 modified for HQ01176489 begin */
 		boolean isAdbEnable = Settings.Global.getInt(getActivity().getContentResolver(), Settings.Global.ADB_ENABLED, 0) != 0;
       mUsbDebug.setChecked(isAdbEnable);
	   /*HQ_yulisuo 2015-06-10 modified end */
       if(Utils.isMonkeyRunning()) {
    	   mUsbDebug.setEnabled(false);
       }
    }

    private void updateToggles(String function) {

        mUsbExts.updateCheckedStatus(function);

        UserManager um = (UserManager) getActivity().getSystemService(Context.USER_SERVICE);
        if (um.hasUserRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER)) {
            Log.e(TAG, "USB is locked down");
            mUsbExts.updateEnableStatus(false);
        } else if (!mUsbAccessoryMode) {
            //Enable MTP and PTP switch while USB is not in Accessory Mode, otherwise disable it
            Log.e(TAG, "USB Normal Mode");
			Log.d(TAG,"updateToggles function ="+function);
			/* HQ_ChenWenshuai 2015-07-22 modified for HQ01249051 begin */
 		    
 		    try{
 		    	/* HQ_ChenWenshuai 2015-09-17 modified for sdcard unmounted can not conect the PC begin */
        		Context context = this.getActivity();
        		mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        		String storageState = mStorageManager.getVolumeState("/storage/sdcard1"); 
 		        if(function.equals("mass_storage") && !storageState.equals(android.os.Environment.MEDIA_UNMOUNTED)){
				/*HQ_ChenWenshuai 2015-09-17 modified end */
					mStorageManager.enableUsbMassStorage();
 		      }
 		   }catch (Exception e) {
 			Log.d(TAG,"updateToggles e"+e);
 		   }		   
			/*HQ_ChenWenshuai 2015-07-22 modified end */
            mUsbExts.updateEnableStatus(true);			
        } else {
            Log.e(TAG, "USB Accessory Mode");
            mUsbExts.updateEnableStatus(false);
        }

        mUsbExts.setCurrentFunction(function);
    }

    //HQ_jiazaizheng 20150807 modify for HQ01308872 start
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (Utils.isMonkeyRunning()) {
            return false;
        }
       if (preference == mUsbDebug) {
           if (mUsbDebug.isChecked()){
               Settings.Global.putInt(getActivity().getContentResolver(),
                       Settings.Global.ADB_ENABLED, 0);
               mUsbDebug.setChecked(false);
           } else{
               AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
               builder.setTitle(R.string.adb_warning_title);
               builder.setMessage(getActivity().getResources().getString(R.string.adb_warning_message));
               builder.setPositiveButton(
                       android.R.string.yes,
                       new DialogInterface.OnClickListener() {
                           @Override
                           public void onClick(DialogInterface dialog,
                                   int which) {
                               Settings.Global.putInt(getActivity().getContentResolver(),
                                       Settings.Global.ADB_ENABLED, 1);
                               mUsbDebug.setChecked(true);
                           }
                       });
               builder.setNegativeButton(
                       android.R.string.no,
                       new DialogInterface.OnClickListener() {
                           @Override
                           public void onClick(DialogInterface dialog,
                                   int which) {
                               Settings.Global.putInt(getActivity().getContentResolver(),
                                       Settings.Global.ADB_ENABLED, 0);
                               mUsbDebug.setChecked(false);
                           }
                       });
               builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                   @Override
                   public void onCancel(DialogInterface dialog) {
                       mUsbDebug.setChecked(false);
                   }
               });
               builder.show();
           }
       }
       return false;
    }
    //HQ_jiazaizheng 20150807 modify for HQ01308872 start

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
    	/* HQ_yulisuo 2015-06-10 modified for HQ01176489 begin */
		/*
        // Don't allow any changes to take effect as the USB host will be disconnected, killing
        // the monkeys
        if (Utils.isMonkeyRunning()) {
            return true;
        }
        // If this user is disallowed from using USB, don't handle their attempts to change the
        // setting.
        UserManager um = (UserManager) getActivity().getSystemService(Context.USER_SERVICE);
        if (um.hasUserRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER)) {
            return true;
        }

        String function = mUsbExts.getFunction(preference);
        boolean makeDefault = mUsbExts.isMakeDefault(preference);
        mUsbManager.setCurrentFunction(function, makeDefault);
        updateToggles(function);

        mUsbExts.setNeedUpdate(false);
        */
        if(preference != mUsbDebug){
			if (Utils.isMonkeyRunning()) {
			    return true;
			}
			// If this user is disallowed from using USB, don't handle their attempts to change the
			// setting.
			UserManager um = (UserManager) getActivity().getSystemService(Context.USER_SERVICE);
			if (um.hasUserRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER)) {
			    return true;
			}

			String function = mUsbExts.getFunction(preference);
			boolean makeDefault = mUsbExts.isMakeDefault(preference);
			mUsbManager.setCurrentFunction(function, makeDefault);
			updateToggles(function);

			mUsbExts.setNeedUpdate(false);
		}
		/*HQ_yulisuo 2015-06-10 modified end */
        return true;
    }
}
