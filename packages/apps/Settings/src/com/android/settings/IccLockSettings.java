/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabHost.TabSpec;
import android.widget.TabWidget;
import android.widget.Toast;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.mediatek.settings.UtilsExt;
import com.mediatek.settings.deviceinfo.UnLockSubDialog;
import com.mediatek.settings.ext.ISettingsMiscExt;
import com.mediatek.settings.ext.ISimRoamingExt;
import com.mediatek.settings.sim.Log;
import com.mediatek.settings.sim.SimHotSwapHandler;
import com.mediatek.internal.telephony.CellConnMgr;

import android.provider.Settings;
import android.database.ContentObserver;
import static android.provider.Settings.System.AIRPLANE_MODE_ON;

/**
 * Implements the preference screen to enable/disable ICC lock and
 * also the dialogs to change the ICC PIN. In the former case, enabling/disabling
 * the ICC lock will prompt the user for the current PIN.
 * In the Change PIN case, it prompts the user for old pin, new pin and new pin
 * again before attempting to change it. Calls the SimCard interface to execute
 * these operations.
 *
 */
public class IccLockSettings extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener, EditPinPreference.OnPinEnteredListener {
    private static final String TAG = "IccLockSettings";
    private static final boolean DBG = true;

    private static final int OFF_MODE = 0;
    // State when enabling/disabling ICC lock
    private static final int ICC_LOCK_MODE = 1;
    // State when entering the old pin
    private static final int ICC_OLD_MODE = 2;
    // State when entering the new pin - first time
    private static final int ICC_NEW_MODE = 3;
    // State when entering the new pin - second time
    private static final int ICC_REENTER_MODE = 4;

    // Keys in xml file
    private static final String PIN_DIALOG = "sim_pin";
    private static final String PIN_TOGGLE = "sim_toggle";
    // Keys in icicle
    private static final String DIALOG_STATE = "dialogState";
    private static final String DIALOG_PIN = "dialogPin";
    private static final String DIALOG_ERROR = "dialogError";
    private static final String ENABLE_TO_STATE = "enableState";

    // Save and restore inputted PIN code when configuration changed
    // (ex. portrait<-->landscape) during change PIN code
    private static final String OLD_PINCODE = "oldPinCode";
    private static final String NEW_PINCODE = "newPinCode";
    //add by Libeibei at 20160908 for HQ02053845	
    public static final String ACTION_CANCLE_ACTIVATION_SIM="action.cancle.activation.sim";

    private static final int MIN_PIN_LENGTH = 4;
    private static final int MAX_PIN_LENGTH = 8;
    // Which dialog to show next when popped up
    private int mDialogState = OFF_MODE;

    //add by caoxuhao 20151028
    //ture if user click the preference of "change sim pin"
    private boolean changingICCPasswordFlag = false;

    private boolean isAirplaneMode = false;

    private String mPin;
    private String mOldPin;
    private String mNewPin;
    private String mError;
    // Are we trying to enable or disable ICC lock?
    private boolean mToState;

    private TabHost mTabHost;
    private TabWidget mTabWidget;
    private ListView mListView;

    private Phone mPhone;

    private EditPinPreference mPinDialog;
    private SwitchPreference mPinToggle;

    private Resources mRes;

    ///M: add for plug in @{
    private ISimRoamingExt mExt;
    private ISettingsMiscExt mMiscExt;
    ///@}

    ///M: add for SIM hot swap @{
    SimHotSwapHandler mSimHotSwapHandler;
    ///@}

    // For async handler to identify request type
    private static final int MSG_ENABLE_ICC_PIN_COMPLETE = 100;
    private static final int MSG_CHANGE_ICC_PIN_COMPLETE = 101;
    private static final int MSG_SIM_STATE_CHANGED = 102;

	/*HQ_xionghaifeng modify for HW EMUI HQ01363881 start*/
	private StringBuffer mSubSimTabText = null;

    // For replies from IccCard interface
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            switch (msg.what) {
                case MSG_ENABLE_ICC_PIN_COMPLETE:
                    iccLockChanged(ar.exception == null, msg.arg1);
                    break;
                case MSG_CHANGE_ICC_PIN_COMPLETE:
                    iccPinChanged(ar.exception == null, msg.arg1);
                    break;
                case MSG_SIM_STATE_CHANGED:
                    updatePreferences();
                    break;
            }

            return;
        }
    };


    private final BroadcastReceiver mSimStateReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_SIM_STATE_CHANGED));
            }
	    //add by Libeibei at 20160908 for HQ02053845 begain
            else if(ACTION_CANCLE_ACTIVATION_SIM.equals(action)){
		Log.e(TAG,"Received Broadcast from UnLockSubDialog for cancel  activate SimCard");
            	updatePreferences();
            }
	    //add by Libeibei at 20160908 for HQ02053845 end
        }
    };

    // For top-level settings screen to query
    static boolean isIccLockEnabled() {
        return PhoneFactory.getDefaultPhone().getIccCard().getIccLockEnabled();
    }

    static String getSummary(Context context) {
        Resources res = context.getResources();
        String summary = isIccLockEnabled()
                ? res.getString(R.string.sim_lock_on)
                : res.getString(R.string.sim_lock_off);
        return summary;
    }

    //caoxuhao add for HQ01887089 start
    private PreferenceScreen root = null;
    private ContentObserver mAirplaneModeObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                Log.d(TAG, "mAirplaneModeObserver onChanged");
                int value = Settings.System.getInt(
                        getContentResolver(), AIRPLANE_MODE_ON, 0);
                	isAirplaneMode = (value == 1)?true:false;
			if((mPinToggle != null) && isAirplaneMode)
			    mPinToggle.setEnabled(false);
            }

    };
    //caoxuhao add for HQ01887089 end

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()...");
        final Context context = getApplicationContext();
        final TelephonyManager tm =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        final int numSims = tm.getSimCount();

        if (Utils.isMonkeyRunning()) {
            finish();
        }

        addPreferencesFromResource(R.xml.sim_lock_settings);

        mPinDialog = (EditPinPreference) findPreference(PIN_DIALOG);
        mPinToggle = (SwitchPreference) findPreference(PIN_TOGGLE);
	mPinToggle.setOnPreferenceChangeListener(this);
        if (savedInstanceState != null && savedInstanceState.containsKey(DIALOG_STATE)) {
            mDialogState = savedInstanceState.getInt(DIALOG_STATE);
            mPin = savedInstanceState.getString(DIALOG_PIN);
            mError = savedInstanceState.getString(DIALOG_ERROR);
            mToState = savedInstanceState.getBoolean(ENABLE_TO_STATE);

            // Restore inputted PIN code
            switch (mDialogState) {
                case ICC_NEW_MODE:
                    mOldPin = savedInstanceState.getString(OLD_PINCODE);
                    break;

                case ICC_REENTER_MODE:
                    mOldPin = savedInstanceState.getString(OLD_PINCODE);
                    mNewPin = savedInstanceState.getString(NEW_PINCODE);
                    break;

                case ICC_LOCK_MODE:
                case ICC_OLD_MODE:
                default:
                    break;
            }
        }

        mPinDialog.setOnPinEnteredListener(this);

        // Don't need any changes to be remembered
        getPreferenceScreen().setPersistent(false);

        ///M: add for init plug in @{
        mMiscExt = UtilsExt.getMiscPlugin(this);
        ///@}
		/*HQ_xionghaifeng modify for HW EMUI HQ01363881 start*/
		mSubSimTabText = new StringBuffer();
		
        if (numSims > 1) {
            setContentView(R.layout.icc_lock_tabs);

            mTabHost = (TabHost) findViewById(android.R.id.tabhost);
            mTabWidget = (TabWidget) findViewById(android.R.id.tabs);
            mListView = (ListView) findViewById(android.R.id.list);

            mTabHost.setup();
            mTabHost.setOnTabChangedListener(mTabListener);
            mTabHost.clearAllTabs();

            for (int i = 0; i < numSims; ++i) {
				/*HQ_xionghaifeng modify for HW EMUI HQ01363881 start*/
				/*
                final SubscriptionInfo subInfo = Utils.findRecordBySlotId(this, i);
                mTabHost.addTab(buildTabSpec(String.valueOf(i),
                        String.valueOf(subInfo == null
                            ? context.getString(R.string.sim_editor_title, i + 1)
                            : subInfo.getDisplayName())));
				*/
				mSubSimTabText.delete(0, mSubSimTabText.length());
				
				final SubscriptionInfo subInfo = Utils.findRecordBySlotId(this, i);
				if (i == 0)
				{
					mSubSimTabText.append(context.getText(R.string.slot_1));
				}
				else
				{
					mSubSimTabText.append(context.getText(R.string.slot_2));
				}

				if (subInfo != null && subInfo.getDisplayName() != null)
				{
					mSubSimTabText.append(" ");
					mSubSimTabText.append(subInfo.getDisplayName());
				}
				mTabHost.addTab(buildTabSpec(String.valueOf(i), mSubSimTabText.toString()));
				/*HQ_xionghaifeng modify for HW EMUI HQ01363881 end*/
            }
            final SubscriptionInfo sir = Utils.findRecordBySlotId(getBaseContext(), 0);

            mPhone = (sir == null) ? null
                : PhoneFactory.getPhone(SubscriptionManager.getPhoneId(sir.getSubscriptionId()));
        } else {
            mPhone = PhoneFactory.getDefaultPhone();
            /**
             * M: if SIM count > 1, onTabChanged() will be invoked when addTab()
             * so no need to call changeSimTitle(). Call this API only when no tab added.
             */
            changeSimTitle();
        }
        mRes = getResources();
        updatePreferences();
        ///M: add for init plug in @{
        mExt = UtilsExt.getSimRoamingExtPlugin(this);
        ///@}

        ///M: add for SIM hot swap @{
        mSimHotSwapHandler = SimHotSwapHandler.newInstance(this);
        mSimHotSwapHandler.registerOnSubscriptionsChangedListener();
        ///@}

        ///M: add for radio status @{
//HQ_jiangchao modify for HQ01320051 at 20150917 
       // handleRadioStatus();
        ///@}

        ///M: replace sim to sim/uim.
        setTitle(mMiscExt.customizeSimDisplayString(
                    getTitle().toString(),
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID));
    }

    /**
     * Replace sim to sim/uim.
     */
    private void changeSimTitle() {
        if (mPhone != null) {
            int subId = mPhone.getSubId();
            Log.d(TAG, "changeSimTitle subId = " + subId);
            ///M: replace sim to sim/uim check box title
            mPinToggle.setTitle(mMiscExt.customizeSimDisplayString(
                    getResources().getString(R.string.sim_pin_toggle), subId));

            ///M: replace sim to sim/uim pin dialog
            mPinDialog.setTitle(mMiscExt.customizeSimDisplayString(
                    getResources().getString(R.string.sim_pin_change), subId));
        }
    }

    private void updatePreferences() {
        mPinDialog.setEnabled((mPhone != null) && !isAirplaneMode);
        mPinToggle.setEnabled((mPhone != null) && !isAirplaneMode);

        //begin:liruihong2 modify for HQ01331673 at 20150918 
        if (mPhone != null) {
            mPinToggle.setChecked(mPhone.getIccCard().getIccLockEnabled());
        }else{
            mPinToggle.setChecked(false);
        }
        //end:liruihong2 modify for HQ01331673 at 20150918 
    }

    @Override
    protected void onResume() {
        super.onResume();

        // ACTION_SIM_STATE_CHANGED is sticky, so we'll receive current state after this call,
        // which will call updatePreferences().
        final IntentFilter filter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
	    //add by Libeibei at 20160908 for HQ02053845 begain
        filter.addAction(ACTION_CANCLE_ACTIVATION_SIM);
        //add by Libeibei at 20160908 for HQ02053845 end
        registerReceiver(mSimStateReceiver, filter);

        //caoxuhao add for HQ01887089 start
        getContentResolver().registerContentObserver(Settings.System.getUriFor(AIRPLANE_MODE_ON),
                false, mAirplaneModeObserver);
        //caoxuhao add for HQ01887089 end

        if (mDialogState != OFF_MODE) {
            showPinDialog();
        } else {
            // Prep for standard click on "Change PIN"
            resetDialogState();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mSimStateReceiver);
        //caoxuhao add for HQ01887089 start
        if(mAirplaneModeObserver != null){
                getContentResolver().unregisterContentObserver(mAirplaneModeObserver);
        }
        //caoxuhao add for HQ01887089 end
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        // Need to store this state for slider open/close
        // There is one case where the dialog is popped up by the preference
        // framework. In that case, let the preference framework store the
        // dialog state. In other cases, where this activity manually launches
        // the dialog, store the state of the dialog.
        if (mPinDialog.isDialogOpen()) {
            out.putInt(DIALOG_STATE, mDialogState);
            out.putString(DIALOG_PIN, mPinDialog.getEditText().getText().toString());
            out.putString(DIALOG_ERROR, mError);
            out.putBoolean(ENABLE_TO_STATE, mToState);

            // Save inputted PIN code
            switch (mDialogState) {
                case ICC_NEW_MODE:
                    out.putString(OLD_PINCODE, mOldPin);
                    break;

                case ICC_REENTER_MODE:
                    out.putString(OLD_PINCODE, mOldPin);
                    out.putString(NEW_PINCODE, mNewPin);
                    break;

                case ICC_LOCK_MODE:
                case ICC_OLD_MODE:
                default:
                    break;
            }
        } else {
            super.onSaveInstanceState(out);
        }
    }

    private void showPinDialog() {
        if (mDialogState == OFF_MODE) {
            return;
        }
        setDialogValues();

        mPinDialog.showPinDialog();
    }

    private void setDialogValues() {
        mPinDialog.setText(mPin);
        String message = "";
        switch (mDialogState) {
            case ICC_LOCK_MODE:
                message = mRes.getString(R.string.sim_enter_pin);
                mPinDialog.setDialogTitle(mToState
                        ? mRes.getString(R.string.sim_enable_sim_lock)
                        : mRes.getString(R.string.sim_disable_sim_lock));
                break;
            case ICC_OLD_MODE:
                message = mRes.getString(R.string.sim_enter_old);
                mPinDialog.setDialogTitle(mRes.getString(R.string.sim_change_pin));
                break;
            case ICC_NEW_MODE:
                message = mRes.getString(R.string.sim_enter_new);
                mPinDialog.setDialogTitle(mRes.getString(R.string.sim_change_pin));
                break;
            case ICC_REENTER_MODE:
                message = mRes.getString(R.string.sim_reenter_new);
                mPinDialog.setDialogTitle(mRes.getString(R.string.sim_change_pin));
                break;
        }
        if (mError != null) {
            message = mError + "\n" + message;
            mError = null;
        }
        Log.d(TAG, "setDialogValues mDialogState = " + mDialogState);
        mPinDialog.setDialogMessage(message);
        /// M: Replace sim to sim/uim.
        changeDialogStrings(mPinDialog.getDialogTitle().toString(), message);
    }

    /**
     * Replace sim to sim/uim.
     *
     * @param dialogTitle the string of dialog title.
     * @param dialogMessage the string of dialog message.
     */
    private void changeDialogStrings(String dialogTitle, String dialogMessage) {
        if (mPhone != null) {
            int subId = mPhone.getSubId();
            Log.d(TAG, "changeSimTitle subId = " + subId);
            mPinDialog.setDialogTitle(mMiscExt.customizeSimDisplayString(
                    dialogTitle, subId));
            mPinDialog.setDialogMessage(mMiscExt.customizeSimDisplayString(
                    dialogMessage, subId));
        }
    }

    public void onPinEntered(EditPinPreference preference, boolean positiveResult) {
        if (!positiveResult) {
            //add by HQ_caoxuhao at 20151013 HQ01442648 begin
	    //only SwitchPrefence can change the mPinToggle state
	    Log.d(TAG, "before mPinToggle.setChecked(!mToState) changingICCPasswordFlag = " + changingICCPasswordFlag);
	    if((ICC_LOCK_MODE == 1) && !changingICCPasswordFlag){
	    	mPinToggle.setChecked(!mToState);
	    }
	    changingICCPasswordFlag = false;
	    //add by HQ_caoxuhao at 20151013 HQ01442648 end
            resetDialogState();
            return;
        }

        mPin = preference.getText();
        if (!reasonablePin(mPin)) {
            // inject error message and display dialog again
            mError = mRes.getString(R.string.sim_bad_pin);
            showPinDialog();
            return;
        }
        switch (mDialogState) {
            case ICC_LOCK_MODE:
                tryChangeIccLockState();
                break;
            case ICC_OLD_MODE:
                mOldPin = mPin;
                mDialogState = ICC_NEW_MODE;
                mError = null;
                mPin = null;
                showPinDialog();
                break;
            case ICC_NEW_MODE:
                mNewPin = mPin;
                mDialogState = ICC_REENTER_MODE;
                mPin = null;
                showPinDialog();
                break;
            case ICC_REENTER_MODE:
                if (!mPin.equals(mNewPin)) {
                    mError = mRes.getString(R.string.sim_pins_dont_match);
                    mDialogState = ICC_NEW_MODE;
                    mPin = null;
                    showPinDialog();
                } else {
                    mError = null;
                    tryChangePin();
                }
                break;
        }
    }

//add by HQ_caoxuhao at 20151013 HQ01442648 begin
    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        
        if(preference == mPinToggle){
           // Get the new, preferred state
            //mToState = mPinToggle.isChecked();
	      mToState = (boolean)objValue;
            // Flip it back and pop up pin dialog
            //mPinToggle.setChecked(!mToState);
            mDialogState = ICC_LOCK_MODE;
	    //HQ_jiangchao modify for HQ01320051 at 20150917 start
  	    CellConnMgr cellConnMgr = new CellConnMgr(this);
            int state = cellConnMgr.getCurrentState(mPhone.getSubId(), CellConnMgr.STATE_SIM_LOCKED);
            if (state != CellConnMgr.STATE_READY) {
            	handleRadioStatus();
            }
            else{
           	 showPinDialog();
            }
 	    //HQ_jiangchao modify for HQ01320051 at 20150917 end
        }
	//else if(preference == mPinDialog){
	//    mDialogState = ICC_OLD_MODE;
        //    return false;
	//	}
        return true;
    }


    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
/*
        if (preference == mPinToggle) {
            // Get the new, preferred state
            mToState = mPinToggle.isChecked();
            // Flip it back and pop up pin dialog
            mPinToggle.setChecked(!mToState);
            mDialogState = ICC_LOCK_MODE;
			  //HQ_jiangchao modify for HQ01320051 at 20150917 start
  	     	  CellConnMgr cellConnMgr = new CellConnMgr(this);
            int state = cellConnMgr.getCurrentState(mPhone.getSubId(), CellConnMgr.STATE_SIM_LOCKED);
            if (state != CellConnMgr.STATE_READY) {
            	handleRadioStatus();
            	}
		    else{
           	 showPinDialog();
			}
 			//HQ_jiangchao modify for HQ01320051 at 20150917 end
        } else if (preference == mPinDialog) {
*/

	if (preference == mPinDialog) {
            mDialogState = ICC_OLD_MODE;
	    changingICCPasswordFlag = true;
            return false;
        }
        return true;
    }

//add by HQ_caoxuhao at 20151013 HQ01442648 end

    private void tryChangeIccLockState() {
        // Try to change icc lock. If it succeeds, toggle the lock state and
        // reset dialog state. Else inject error message and show dialog again.
        Message callback = Message.obtain(mHandler, MSG_ENABLE_ICC_PIN_COMPLETE);
        mPhone.getIccCard().setIccLockEnabled(mToState, mPin, callback);
        // Disable the setting till the response is received.
        mPinToggle.setEnabled(false);
    }

    private void iccLockChanged(boolean success, int attemptsRemaining) {
        if (success) {
            mPinToggle.setChecked(mToState);
            mExt.showPinToast(mToState);
        } else {
	    //add by HQ_caoxuhao at 20151013 HQ01442648 begin
	    mPinToggle.setChecked(!mToState);
	    //add by HQ_caoxuhao at 20151013 HQ01442648 end
            Toast.makeText(this, getPinPasswordErrorMessage(attemptsRemaining), Toast.LENGTH_LONG)
                    .show();
        }
        mPinToggle.setEnabled(true);
        resetDialogState();
    }

    private void iccPinChanged(boolean success, int attemptsRemaining) {
        if (!success) {
            Toast.makeText(this, getPinPasswordErrorMessage(attemptsRemaining),
                    Toast.LENGTH_LONG)
                    .show();
        } else {
            /// M: Customize SIM/UIM strings for operator spec. @{
            String successMsg = mRes.getString(R.string.sim_change_succeeded);
            successMsg = mMiscExt.customizeSimDisplayString(successMsg, mPhone.getSubId());
            Toast.makeText(this, successMsg,
                    Toast.LENGTH_SHORT)
                    .show();
            /// @}

        }
        resetDialogState();
    }

    private void tryChangePin() {
        Message callback = Message.obtain(mHandler, MSG_CHANGE_ICC_PIN_COMPLETE);
        mPhone.getIccCard().changeIccLockPassword(mOldPin,
                mNewPin, callback);
    }

    private String getPinPasswordErrorMessage(int attemptsRemaining) {
        String displayMessage;
	
        if (attemptsRemaining == 0) {
            displayMessage = mRes.getString(R.string.wrong_pin_code_pukked);
        } else if (attemptsRemaining > 0) {
            displayMessage = mRes
                    .getQuantityString(R.plurals.wrong_pin_code, attemptsRemaining,
                            attemptsRemaining);
        } else {
            displayMessage = mRes.getString(R.string.pin_failed);
        }
        Log.d(TAG, " attemptsRemaining=" + attemptsRemaining +"::::"+SystemProperties.get("gsm.slot1.num.pin1")+ "  displayMessage=" + displayMessage);
        /// M: Customize SIM/UIM string for operator spec
        displayMessage = mMiscExt.customizeSimDisplayString(displayMessage, mPhone.getSubId());
        if (DBG) Log.d(TAG, "getPinPasswordErrorMessage:"
                + " attemptsRemaining=" + attemptsRemaining + " displayMessage=" + displayMessage);
        return displayMessage;
    }

    private boolean reasonablePin(String pin) {
        if (pin == null || pin.length() < MIN_PIN_LENGTH || pin.length() > MAX_PIN_LENGTH) {
            return false;
        } else {
            return true;
        }
    }

    private void resetDialogState() {
        mError = null;
        mDialogState = ICC_OLD_MODE; // Default for when Change PIN is clicked
        mPin = "";
        setDialogValues();
        mDialogState = OFF_MODE;
    }

    private OnTabChangeListener mTabListener = new OnTabChangeListener() {
        @Override
        public void onTabChanged(String tabId) {
            final int slotId = Integer.parseInt(tabId);
            final SubscriptionInfo sir = Utils.findRecordBySlotId(getBaseContext(), slotId);

            mPhone = (sir == null) ? null
                : PhoneFactory.getPhone(SubscriptionManager.getPhoneId(sir.getSubscriptionId()));

            // The User has changed tab; update the body.
            updatePreferences();

            /// M: Replace sim to sim/uim.
            changeSimTitle();

            ///M: add for radio status @{
            Log.d(TAG, "onTabChanged()... slotId: " + slotId);
            handleRadioStatus();
            ///@}
        }
    };

    private TabContentFactory mEmptyTabContent = new TabContentFactory() {
        @Override
        public View createTabContent(String tag) {
            return new View(mTabHost.getContext());
        }
    };

    private TabSpec buildTabSpec(String tag, String title) {
        return mTabHost.newTabSpec(tag).setIndicator(title).setContent(
                mEmptyTabContent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // SIM hot swap
        if(mSimHotSwapHandler!=null){
          mSimHotSwapHandler.unregisterOnSubscriptionsChangedListener();
		}
    };

    /**
     * handle radio status(radio off, airplane mode on).
     * @param phone phone
     */
    private void handleRadioStatus() {
        Log.d(TAG, "handleSimPinLock()... mPHone is null:" + (mPhone == null));
        if (mPhone != null) {
            UnLockSubDialog.showDialog(this, mPhone.getSubId());
        } else {
            Log.d(TAG, "phone is null");
        }
    }
}
