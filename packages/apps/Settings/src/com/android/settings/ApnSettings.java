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

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteModeController;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.settings.FeatureOption;
import com.mediatek.settings.UtilsExt;
import com.mediatek.settings.cdma.CdmaApnSetting;
import com.mediatek.settings.deviceinfo.UnLockSubDialog;
import com.mediatek.settings.ext.IApnSettingsExt;
import com.mediatek.settings.ext.IRcseOnlyApnExtension;
import com.mediatek.settings.ext.IRcseOnlyApnExtension.OnRcseOnlyApnStateChangedListener;
import com.mediatek.settings.sim.MsimRadioValueObserver;
import com.mediatek.settings.sim.SimHotSwapHandler;
import com.mediatek.settings.sim.TelephonyUtils;
import com.mediatek.telephony.TelephonyManagerEx;
import android.telephony.PhoneNumberUtils;//add by lipeng 
import android.os.SystemProperties;//add by lipeng
import java.util.ArrayList;

public class ApnSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, MsimRadioValueObserver.Listener  {
    static final String TAG = "ApnSettings";
	///HQ_xionghaifeng 20160108 add for HQ01591823 movistar Roaming Broker start
	static final String TAG_XHFRB = "xhfRoamingBroker";
	///HQ_xionghaifeng 20160108 add for HQ01591823 movistar Roaming Broker end

    public static final String EXTRA_POSITION = "position";
    public static final String RESTORE_CARRIERS_URI =
        "content://telephony/carriers/restore";
    public static final String PREFERRED_APN_URI =
        "content://telephony/carriers/preferapn";

    public static final String APN_ID = "apn_id";

    private static final int ID_INDEX = 0;
    private static final int NAME_INDEX = 1;
    private static final int APN_INDEX = 2;
    private static final int TYPES_INDEX = 3;

    private static final int MENU_NEW = Menu.FIRST;
    private static final int MENU_RESTORE = Menu.FIRST + 1;

    private static final int EVENT_RESTORE_DEFAULTAPN_START = 1;
    private static final int EVENT_RESTORE_DEFAULTAPN_COMPLETE = 2;

    private static final int DIALOG_RESTORE_DEFAULTAPN = 1001;

    private static final Uri DEFAULTAPN_URI = Uri.parse(RESTORE_CARRIERS_URI);
    private static final Uri PREFERAPN_URI = Uri.parse(PREFERRED_APN_URI);

    private static boolean mRestoreDefaultApnMode;

    private RestoreApnUiHandler mRestoreApnUiHandler;
    private RestoreApnProcessHandler mRestoreApnProcessHandler;
    private HandlerThread mRestoreDefaultApnThread;
    private SubscriptionInfo mSubscriptionInfo;

    private UserManager mUm;

    private String mSelectedKey;

    private IntentFilter mMobileStateFilter;

    private boolean mUnavailable;

    /// M: add for SIM hot swap @{
    private SimHotSwapHandler mSimHotSwapHandler;
    /// @}
    private final BroadcastReceiver mMobileStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED.equals(action)) {
                PhoneConstants.DataState state = getMobileDataState(intent);
                switch (state) {
                case CONNECTED:
                    if (!mRestoreDefaultApnMode) {
                        fillList();
                    } else {
                        showDialog(DIALOG_RESTORE_DEFAULTAPN);
                    }
                    break;
                }
                String apnType  = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                Log.d(TAG, "Receiver,send MMS status, get type = " + apnType);
                if (PhoneConstants.APN_TYPE_MMS.equals(apnType)) {
                    getPreferenceScreen().setEnabled(
                            mExt.getScreenEnableState(mSubscriptionInfo.getSubscriptionId(),
                                    getActivity()));
                }
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                //M: Update the screen since airplane mode on need to disable the whole screen include menu {
                getPreferenceScreen().setEnabled(
                        mExt.getScreenEnableState(mSubscriptionInfo.getSubscriptionId(),
                                getActivity()));
                getActivity().invalidateOptionsMenu();
                //@}
            }
        }
    };

    private OnRcseOnlyApnStateChangedListener mListener = new OnRcseOnlyApnStateChangedListener() {
        @Override
        public void onRcseOnlyApnStateChanged(boolean isEnabled) {
            Log.d(TAG, "onRcseOnlyApnStateChanged()-current state is " + isEnabled);
            if (mSubscriptionInfo.getSubscriptionId() !=
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                fillList();
            }
        }
    };

    private static PhoneConstants.DataState getMobileDataState(Intent intent) {
        String str = intent.getStringExtra(PhoneConstants.STATE_KEY);
        if (str != null) {
            return Enum.valueOf(PhoneConstants.DataState.class, str);
        } else {
            return PhoneConstants.DataState.DISCONNECTED;
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        final Activity activity = getActivity();
        final int subId = activity.getIntent().getIntExtra("sub_id",
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        mUm = (UserManager) getSystemService(Context.USER_SERVICE);

        mExt = UtilsExt.getApnSettingsPlugin(getActivity());
        mExt.initTetherField(this);
        mRcseExt = UtilsExt.getRcseApnPlugin(getActivity());
        mRcseExt.addRcseOnlyApnStateChanged(mListener);

        mMobileStateFilter = mExt.getIntentFilter();
        if (!mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)) {
            setHasOptionsMenu(true);
        }

        mSubscriptionInfo = Utils.findRecordBySubId(activity, subId);
        /// M: @{
        if (mSubscriptionInfo == null) {
            Log.d(TAG, "onCreate()... Invalid subId: " + subId);
            getActivity().finish();
        }
        mRadioValueObserver = new MsimRadioValueObserver(getActivity());
        /// @}
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        TextView empty = (TextView) getView().findViewById(android.R.id.empty);
        if (empty != null) {
            empty.setText(R.string.apn_settings_not_available);
            getListView().setEmptyView(empty);
        }

        if (mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)) {
            mUnavailable = true;
            setPreferenceScreen(new PreferenceScreen(getActivity(), null));
            return;
        }

        addPreferencesFromResource(R.xml.apn_settings);

        getListView().setItemsCanFocus(true);

        mSimHotSwapHandler = SimHotSwapHandler.newInstance(getActivity());
        mSimHotSwapHandler.registerOnSubscriptionsChangedListener();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mUnavailable) {
            return;
        }
		//add by zhangjinqiang for HQ01493260-start
		if(mSubscriptionInfo!=null){
			UnLockSubDialog.showDialog(getActivity(), mSubscriptionInfo.getSubscriptionId());
		}
		//add by zjq end
        getActivity().registerReceiver(mExt.getBroadcastReceiver(mMobileStateReceiver),
                mMobileStateFilter);
        /// M: @{
        mRadioValueObserver.registerMsimObserver(this);
        /// @}
        if (!mRestoreDefaultApnMode) {
            fillList();
            // M: In case dialog not dismiss as activity is in background, so when resume back, need to 
            // remove the dialog {
            removeDialog(DIALOG_RESTORE_DEFAULTAPN);
            //@}
        }

        mExt.updateTetherState(getActivity());
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mUnavailable) {
            return;
        }
        getActivity().unregisterReceiver(mExt.getBroadcastReceiver(mMobileStateReceiver));
        mRadioValueObserver.ungisterMsimObserver();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSimHotSwapHandler.unregisterOnSubscriptionsChangedListener();

        if (mRestoreDefaultApnThread != null) {
            mRestoreDefaultApnThread.quit();
        }
        mRcseExt.removeRcseOnlyApnStateChanged(mListener);
    }

    ///HQ_xionghaifeng 20151222 add for Roaming Broker start
    protected String mapToMainPlmnIfNeeded(String oldPlmn, int phoneId)
    {
         if(SystemProperties.get("ro.hq.sim.dual_imsi", "0").equals("1"))
         {
            String mainPlmn;
            int slotId = phoneId;
            boolean roamingBrokerActived = getDualImsiParameters("gsm.RoamingBrokerIsActivied" + slotId).equals("1");

            if (roamingBrokerActived)
            {
                mainPlmn = getDualImsiParameters("gsm.RoamingBrokerMainPLMN" + slotId);
                Log.d(TAG, "mapToMainPlmnIfNeeded map to" + mainPlmn);
                if (mainPlmn == null || mainPlmn.length() == 0) {
                    Log.d(TAG, "dual_imsi mapToMainPlmnIfNeeded mainPlmn is not valid");
                    return oldPlmn;                
                }
                return mainPlmn;
            }
            else
            {
                Log.d(TAG, "dual_imsi mapToMainPlmnIfNeeded is not changed");
                return oldPlmn;
            }
        }
        else {
                Log.d(TAG, "not dual_imsi mapToMainPlmnIfNeeded is not changed");
                return oldPlmn;
        }
    }
    public String getDualImsiParameters(String name) {  
        //if (mContext == null) return "";

        String ret = Settings.System.getString(getContentResolver(),
                    name);

        Log.d("xhfRoamingBroker", "getDualImsiParameters name = " + name+ "  ret = "+ ret);
        if (ret == null) {
            return "";
        }
        return ret;
    }  
    ///HQ_xionghaifeng 20151222 add for Roaming Broker end

    private void fillList() {
        final TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String mccmnc = mSubscriptionInfo == null ? ""
            : tm.getSimOperator(mSubscriptionInfo.getSubscriptionId());
        Log.d(TAG, "mccmnc = " + mccmnc);
        if (mccmnc != null) {
            String mapPlmn = mapToMainPlmnIfNeeded(mccmnc, 
                   SubscriptionManager.getSlotId(mSubscriptionInfo.getSubscriptionId()));
            if (!mapPlmn.equals(mccmnc)) {
                mccmnc = mapPlmn;
                Log.d("xhfRoamingBroker", "fillList mccmnc change to " + mccmnc);
            }
        }


        String where = mExt.getFillListQuery(mccmnc, mSubscriptionInfo.getSubscriptionId());
        where = CdmaApnSetting.customizeQuerySelection(
                mExt, mccmnc, mSubscriptionInfo.getSubscriptionId(), where);
        where += " AND NOT (type='ia' AND (apn=\'\' OR apn IS NULL))";
        /// M: for non-volte project,do not show ims apn @{
        if (!FeatureOption.MTK_VOLTE_SUPPORT) {
            where += " AND NOT type='ims'";
        }
        /// @}
        Log.d(TAG, "fillList where: " + where);

        Cursor cursor = getContentResolver().query(
                Telephony.Carriers.CONTENT_URI, new String[] {
                "_id", "name", "apn", "type", "sourcetype"}, where, null, null);
        cursor = mExt.customizeQueryResult(
                getActivity(), cursor, Telephony.Carriers.CONTENT_URI, mccmnc);

        if (cursor != null) {
            Log.d(TAG, "fillList cursor count: " + cursor.getCount());
            PreferenceGroup apnList = (PreferenceGroup) findPreference("apn_list");
            apnList.removeAll();
            String apnName = "";
            ArrayList<Preference> mmsApnList = new ArrayList<Preference>();

            mSelectedKey = getSelectedApnKey();
            Log.d(TAG, "fillList getSelectedApnKey: " + mSelectedKey);
            // M: define tmp select key
            String selectedKey = null;
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                String name = cursor.getString(NAME_INDEX);
                String apn = cursor.getString(APN_INDEX);
                String key = cursor.getString(ID_INDEX);
                String type = cursor.getString(TYPES_INDEX);
                int sourcetype = cursor.getInt(SOURCE_TYPE_INDEX);

                if (mExt.isSkipApn(type, mRcseExt)) {
                    cursor.moveToNext();
                    continue;
                }

                name = mExt.updateAPNName(name, sourcetype);
                apnName = name;
                ApnPreference pref = new ApnPreference(getActivity());

                pref.setKey(key);
                pref.setTitle(name);
                pref.setSummary(apn);
                pref.setPersistent(false);
                pref.setOnPreferenceChangeListener(this);

                //pref.setApnEditable(mExt.isAllowEditPresetApn(type, apn, mccmnc, sourcetype));
                // add by lipeng for APN not edit
				String pMccMnc = PhoneNumberUtils.getSimMccMnc(0);
				String pMccMnc2 = PhoneNumberUtils.getSimMccMnc(1);
				Log.d("ApnSettings", "pMccMnc=" + pMccMnc + ";" + "pMccMnc2=" + pMccMnc2);
				if (SystemProperties.get("ro.hq.apns.noteditable").equals("1")) {//730-09,730-01,730-10,730-04     730-02  37001 37002 37004
					if ((pMccMnc != null && pMccMnc.length()>=0 && "37001".equals(pMccMnc)||"37002".equals(pMccMnc)||"37004".equals(pMccMnc)||"73009".equals(pMccMnc)||"73001".equals(pMccMnc)||"73010".equals(pMccMnc)||"73004".equals(pMccMnc))||(pMccMnc2 != null && pMccMnc2.length()>=0 && "37001".equals(pMccMnc2)||"37002".equals(pMccMnc2)||"37004".equals(pMccMnc2)||"73009".equals(pMccMnc2)||"73001".equals(pMccMnc2)||"73010".equals(pMccMnc2)||"73004".equals(pMccMnc2))) {
						pref.setApnEditable(false);
					}
				}else if (SystemProperties.get("ro.hq.apns.perunotedit").equals("1")) {//71606
					if ((pMccMnc != null && pMccMnc.length()>=0 && "71606".equals(pMccMnc))||(pMccMnc2 != null && pMccMnc2.length()>=0 && "71606".equals(pMccMnc2))) {
						pref.setApnEditable(false);
					}
				}else if (SystemProperties.get("ro.hq.apns.mexiconotedit").equals("1")) {//mcc==334
					if ((pMccMnc != null && pMccMnc.length()>=0 && pMccMnc.startsWith("334"))||(pMccMnc2 != null && pMccMnc2.length()>=0 && pMccMnc2.startsWith("334"))) {
						pref.setApnEditable(false);
					}
				}else {
					pref.setApnEditable(mExt.isAllowEditPresetApn(type, apn,
							mccmnc, sourcetype));
				}//end by lipeng
                pref.setSubId(mSubscriptionInfo.getSubscriptionId());

                /// M: All tether apn will be selectable for otthers , mms will not be selectable.
                boolean selectable = mExt.isSelectable(type);
                pref.setSelectable(selectable);
                Log.d(TAG,"mSelectedKey = " + mSelectedKey + " key = " + key + " name = " + name);
                if (selectable) {
                    if (selectedKey == null) {
                        pref.setChecked();
                        selectedKey = key;
                    }
                    if ((mSelectedKey != null) && mSelectedKey.equals(key)) {
                        pref.setChecked();
                        selectedKey = mSelectedKey;
                    }
                    if (!(SystemProperties.get("ro.hq.att.apn.hide").equals("1") && ((mccmnc.equals("334090") && apnName.equals("Localización")) || (mccmnc.equals("334050") && apnName.equals("SUPL"))))) {//HQ_hushunli 2015-12-30 add for HQ01597852,HQ01597859
                        apnList.addPreference(pref);
                    }
                } else {
                    mmsApnList.add(pref);
                    CdmaApnSetting.customizeUnselectablePreferences(
                            mmsApnList, mSubscriptionInfo.getSubscriptionId());
                    mExt.customizeUnselectableApn(
                            mmsApnList, mSubscriptionInfo.getSubscriptionId());
                }
                cursor.moveToNext();
            }
            cursor.close();

            if (selectedKey != null && selectedKey != mSelectedKey) {
                setSelectedApnKey(selectedKey);
            }

            for (Preference preference : mmsApnList) {
                apnList.addPreference(preference);
            }
            getPreferenceScreen().setEnabled(mExt.getScreenEnableState(
                    mSubscriptionInfo.getSubscriptionId(), getActivity()));
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!mUnavailable) {
            menu.add(0, MENU_NEW, 0,
                    getResources().getString(R.string.menu_new))
                    .setIcon(R.drawable.ic_menu_add)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(0, MENU_RESTORE, 0,
                    getResources().getString(R.string.menu_restore))
                    .setIcon(android.R.drawable.ic_menu_upload);
        }
        mExt.updateMenu(menu, MENU_NEW, MENU_RESTORE,
                TelephonyManager.getDefault().getSimOperator(
                mSubscriptionInfo != null ? mSubscriptionInfo.getSubscriptionId()
                        : SubscriptionManager.INVALID_SUBSCRIPTION_ID));

        super.onCreateOptionsMenu(menu, inflater);
    }
    
    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        int size = menu.size();
        boolean isOn = TelephonyUtils.isAirplaneModeOn(getActivity()); 
        Log.d(TAG,"onPrepareOptionsMenu isOn = " + isOn);
        // When airplane mode on need to disable options menu
        for (int i = 0;i< size;i++) {
            menu.getItem(i).setEnabled(!isOn);
        }
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_NEW:
            addNewApn();
            return true;

        case MENU_RESTORE:
            restoreDefaultApn();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void addNewApn() {
        Intent intent = new Intent(Intent.ACTION_INSERT, Telephony.Carriers.CONTENT_URI);
        int subId = mSubscriptionInfo != null ? mSubscriptionInfo.getSubscriptionId()
                : SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        intent.putExtra("sub_id", subId);
        /// M: add for custom the intent @{
        mExt.addApnTypeExtra(intent);
        /// @}
        startActivity(intent);
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Log.d(TAG, "onPreferenceChange(): Preference - " + preference
                + ", newValue - " + newValue + ", newValue type - "
                + newValue.getClass());
        if (newValue instanceof String) {
            setSelectedApnKey((String) newValue);
        }

        return true;
    }

    private void setSelectedApnKey(String key) {
        mSelectedKey = key;
        ContentResolver resolver = getContentResolver();

        ContentValues values = new ContentValues();
        values.put(APN_ID, mSelectedKey);
        /// M: SVLTE maintain 2 phone(CDMAPhone and LteDcPhone), need to set/get preferred apn
        /// using correct sub id, LteDcPhone uses special sub id. @{
        if (FeatureOption.MTK_SVLTE_SUPPORT) {
            Cursor cursor = getContentResolver().query(
                    Telephony.Carriers.CONTENT_URI,
                    new String[] { "_id", "sourcetype" },
                    "_id = " + key,
                    null,
                    null);

            Log.d(TAG, "setSelectedApnKey cursor.getCount() - " + cursor.getCount());
            if (cursor.getCount() > 0) {
                int c2kSlot = SvlteModeController.getActiveSvlteModeSlotId();
                Log.d(TAG, "setSelectedApnKey c2kSlot" + c2kSlot);
                Log.d(TAG, "setSelectedApnKey mSubscriptionInfo getSubscriptionId" + mSubscriptionInfo.getSubscriptionId());
                int currentSlot = SubscriptionManager.getSlotId(mSubscriptionInfo.getSubscriptionId());
                Log.d(TAG, "setSelectedApnKey currentSlot" + currentSlot);
                cursor.moveToFirst();
                int sourceType = cursor.getInt(1);
                // If user-created apn set as preferred apn,
                // we will set it to both LTE and C2K Phone.
                if (sourceType == 1 && c2kSlot == currentSlot) {
                    resolver.update(mExt.getRestoreCarrierUri(
                        mSubscriptionInfo.getSubscriptionId()), values, null, null);
                    Log.d(TAG, "setSelectedApnKey sourceType == 1 && c2kSlot == currentSlot");
                    resolver.update(mExt.getRestoreCarrierUri(SvlteUtils.getLteDcSubId(c2kSlot)),
                            values, null, null);
                } else {
                    int current_sub = -1;
                    ContentValues values_t = new ContentValues();
                    values_t.put(APN_ID, -1);

                    Log.d(TAG, "setSelectedApnKey not sourceType == 1 && c2kSlot == currentSlot");
                    Log.d(TAG, "setSelectedApnKey CdmaApnSetting.getPreferredSubId subid=" + CdmaApnSetting.getPreferredSubId(getActivity(),
                              mSubscriptionInfo.getSubscriptionId()));

                    current_sub = CdmaApnSetting.getPreferredSubId(getActivity(),
                        mSubscriptionInfo.getSubscriptionId());
                    Log.d(TAG, "setSelectedApnKey current_sub =" + 
                        CdmaApnSetting.getPreferredSubId(getActivity(),
                        mSubscriptionInfo.getSubscriptionId()));

                    resolver.update(mExt.getRestoreCarrierUri(current_sub), values, null, null);
                    
                    if (current_sub == SvlteUtils.getLteDcSubId(c2kSlot)) {
                        resolver.update(mExt.getRestoreCarrierUri(
                            mSubscriptionInfo.getSubscriptionId()), values_t, null, null);
                    } else {
                        resolver.update(
                            mExt.getRestoreCarrierUri(SvlteUtils.getLteDcSubId(c2kSlot)),
                            values_t, null, null);
                    }
                }
            }
            cursor.close();
        } else {
            resolver.update(mExt.getRestoreCarrierUri(
                CdmaApnSetting.getPreferredSubId(getActivity(),
                mSubscriptionInfo.getSubscriptionId())), values, null, null);
        }
        /// @}

		///HQ_xionghaifeng 20160108 add for HQ01591823 movistar Roaming Broker start
		if(isDualImsiPreferredApnEnabled())
		{ 
			updateDualImsiPreferredApnIfNeeded(key); 
		} 
		///HQ_xionghaifeng 20160108 add for HQ01591823 movistar Roaming Broker end
    }

    private String getSelectedApnKey() {
        String key = null;

        /// M: SVLTE maintain 2 phone(CDMAPhone and LteDcPhone), need to set/get preferred apn
        /// using correct sub id, LteDcPhone uses special sub id. @{
        Cursor cursor = getContentResolver().query(
                mExt.getRestoreCarrierUri(
                        CdmaApnSetting.getPreferredSubId(getActivity(),
                                mSubscriptionInfo.getSubscriptionId())), new String[] {"_id"},
                null, null, Telephony.Carriers.DEFAULT_SORT_ORDER);
        /// @}
        Log.d(TAG, "getSelectedApnKey cursor.getCount " + cursor.getCount());
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            key = cursor.getString(ID_INDEX);
        }
        cursor.close();
        Log.d(TAG,"getSelectedApnKey key = " + key);

		///HQ_xionghaifeng 20160108 add for HQ01591823 movistar Roaming Broker start
		if (isDualImsiPreferredApnEnabled())
		{ 
			key = mapPreferredApnIfNeeded(key);	
		} 
		///HQ_xionghaifeng 20160108 add for HQ01591823 movistar Roaming Broker end
        return key;
    }

    private boolean restoreDefaultApn() {
        showDialog(DIALOG_RESTORE_DEFAULTAPN);
        mRestoreDefaultApnMode = true;

        if (mRestoreApnUiHandler == null) {
            mRestoreApnUiHandler = new RestoreApnUiHandler();
        }

        if (mRestoreApnProcessHandler == null ||
            mRestoreDefaultApnThread == null) {
            mRestoreDefaultApnThread = new HandlerThread(
                    "Restore default APN Handler: Process Thread");
            mRestoreDefaultApnThread.start();
            mRestoreApnProcessHandler = new RestoreApnProcessHandler(
                    mRestoreDefaultApnThread.getLooper(), mRestoreApnUiHandler);
        }

        mRestoreApnProcessHandler
                .sendEmptyMessage(EVENT_RESTORE_DEFAULTAPN_START);
        return true;
    }

    private class RestoreApnUiHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_RESTORE_DEFAULTAPN_COMPLETE:
                    Activity activity = getActivity();
                    if (activity == null) {
                        mRestoreDefaultApnMode = false;
                        return;
                    }
                    fillList();
                    getPreferenceScreen().setEnabled(true);
                    mRestoreDefaultApnMode = false;
                    removeDialog(DIALOG_RESTORE_DEFAULTAPN);
                    Toast.makeText(
                        activity,
                        getResources().getString(
                                R.string.restore_default_apn_completed),
                        Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }

    //M: ALPS01760966 In case the dialog intend to dismiss in background and i will cause JE, so
    // the dialog will not be removed in background, but to remove on resume if it is showing { 
    @Override
    protected void removeDialog(int dialogId) {
        if (this.isResumed() && isDialogShowing(dialogId)) {
            super.removeDialog(dialogId);
        }
    }
    //@}
    private class RestoreApnProcessHandler extends Handler {
        private Handler mRestoreApnUiHandler;

        public RestoreApnProcessHandler(Looper looper, Handler restoreApnUiHandler) {
            super(looper);
            this.mRestoreApnUiHandler = restoreApnUiHandler;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_RESTORE_DEFAULTAPN_START:
                    ContentResolver resolver = getContentResolver();
                    resolver.delete(getUri(DEFAULTAPN_URI), null, null);
                    mRestoreApnUiHandler
                        .sendEmptyMessage(EVENT_RESTORE_DEFAULTAPN_COMPLETE);
                    break;
            }
        }
    }

    @Override
    public Dialog onCreateDialog(int id) {
        if (id == DIALOG_RESTORE_DEFAULTAPN) {
            ProgressDialog dialog = new ProgressDialog(getActivity());
            dialog.setMessage(getResources().getString(R.string.restore_default_apn));
            dialog.setCancelable(false);
            return dialog;
        }
        return null;
    }

    private Uri getUri(Uri uri) {
        return Uri.withAppendedPath(uri, "/subId/" + mSubscriptionInfo.getSubscriptionId());
    }

    /// M: add for mediatek Plugin @{
    private static final int SOURCE_TYPE_INDEX = 4;
    private IApnSettingsExt mExt;
    private IRcseOnlyApnExtension mRcseExt;
    /// @}

    private MsimRadioValueObserver mRadioValueObserver;

    @Override
    public void onChange(int msimModevalue, boolean selfChange) {
        getPreferenceScreen().setEnabled(
                mExt.getScreenEnableState(mSubscriptionInfo.getSubscriptionId(),
                        getActivity()));
        getActivity().invalidateOptionsMenu();
    }

	///HQ_xionghaifeng 20160108 add for HQ01591823 movistar Roaming Broker start
	private String getCurrentIMSI()
	{ 
		String curImsi = ""; 

		TelephonyManager telephonyEx = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE); 
		if (telephonyEx != null)
		{ 
			int subid = mSubscriptionInfo.getSubscriptionId(); 
			int slot = SubscriptionManager.getSlotId(subid); 
			Log.d(TAG_XHFRB,"getCurrentIMSI slot:"+slot+" subid:"+subid); 
			curImsi = telephonyEx.getSubscriberId(subid); 
		} 
		Log.d(TAG_XHFRB,"getCurrentIMSI return:"+curImsi); 
		return curImsi; 
	} 

	private void setPreferredApnIdForDualIMSI(int imsiid , String imsi , String apnid) 
	{ 
		if(imsiid!=1 && imsiid!=2)
		{ 
			Log.d(TAG_XHFRB, "setPreferredApnIdForDualIMSI illegal dualsimid:"+imsiid); 
			return; 
		}	
		String subId = Long.toString(mSubscriptionInfo.getSubscriptionId()); 

		Log.d(TAG_XHFRB, "setPreferredApnIdForDualIMSI imsiid:"+imsiid+" imsi="+imsi+" apnid="+apnid+" sub="+subId); 

		if(imsiid == 1)
		{ 
			SystemProperties.set("persist.sys.dual.imsi1."+subId, imsi); 
			SystemProperties.set("persist.sys.dual.apn1."+subId, apnid); 
		} 
		if(imsiid == 2)
		{ 
			SystemProperties.set("persist.sys.dual.imsi2."+subId, imsi); 
			SystemProperties.set("persist.sys.dual.apn2."+subId, apnid); 
		} 
	} 

	private String getImsiForDualIMSI(int imsiid , int subId) 
	{ 
		String tempVal = "N/A"; 

		if(imsiid == 1)
		{	
			tempVal = SystemProperties.get("persist.sys.dual.imsi1."+subId , "N/A"); 
		} 
		else if(imsiid == 2)
		{ 
			tempVal = SystemProperties.get("persist.sys.dual.imsi2."+subId , "N/A"); 
		}	
		else
		{ 
			Log.d(TAG_XHFRB, "getImsiForDualIMSI illegal imsiid:"+imsiid); 
		}	

		Log.d(TAG_XHFRB, "getImsiForDualIMSI imsiid:"+imsiid+" return val:"+tempVal); 
		return tempVal; 
	} 

	private String getApnIdForDualIMSI(int imsiid , int subId) 
	{ 
		String tempVal = "-1"; 

		if(imsiid == 1)
		{	
			tempVal = SystemProperties.get("persist.sys.dual.apn1."+subId , "-1"); 
		} 
		else if(imsiid == 2)
		{ 
			tempVal = SystemProperties.get("persist.sys.dual.apn2."+subId , "-1"); 
		}	
		else
		{ 
			Log.d(TAG_XHFRB, "getApnIdForDualIMSI illegal imsiid:"+imsiid); 
		} 

		Log.d(TAG_XHFRB, "getApnIdForDualIMSI imsiid:"+imsiid+" return val:"+tempVal); 
		return tempVal; 
	} 

	private void dualImsiSyncWithPreferredApn(int apnId)
	{ 
		Uri PREFERAPN_NO_UPDATE_URI_USING_SUBID = 
		Uri.parse("content://telephony/carriers/preferapn_no_update/subId/"); 

		String subId = Long.toString(mSubscriptionInfo.getSubscriptionId()); 
		Uri uri = Uri.withAppendedPath(PREFERAPN_NO_UPDATE_URI_USING_SUBID, subId); 
		Log.d(TAG_XHFRB, "dualImsiSyncWithPreferredApn: delete subId = " + subId); 
		ContentResolver resolver = getContentResolver(); 
		resolver.delete(uri, null, null); 

		if (apnId >= 0) 
		{ 
			Log.d(TAG_XHFRB, "dualImsiSyncWithPreferredApn: insert pos = " + apnId + ",subId =" + subId); 
			ContentValues values = new ContentValues(); 
			values.put("apn_id", apnId); 
			resolver.insert(uri, values); 
		} 
	} 

	protected boolean isDualImsiPreferredApnEnabled()
	{ 
		final TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE); 
		String mccmnc = mSubscriptionInfo == null ? "" 
		: tm.getSimOperator(mSubscriptionInfo.getSubscriptionId()); 
		Log.d(TAG_XHFRB , "isDualImsiPreferredApnEnabled :"+mccmnc); 

		///for SW_GLOBAL_EUROPE_163 movistar PLMN21407 HQ01591823
		if (SystemProperties.get("ro.hq.sim.dual_imsi.remapn", "0").equals("1"))
		{
			return mccmnc.equals("21407") ? true : false;
		}
		else
		{
			return false;
		}
	}	

	protected String mapPreferredApnIfNeeded(String oldApnId) 
	{ 
		String imsi1 = ""; 
		String imsi2 = ""; 
		String newApnId = "-1"; 
		int subId = mSubscriptionInfo.getSubscriptionId(); 
		String curImsi = getCurrentIMSI(); 

		imsi1 = getImsiForDualIMSI(1 , subId); 
		imsi2 = getImsiForDualIMSI(2 , subId); 

		Log.d(TAG_XHFRB, "mapPreferredApnIfNeeded: imsi1="+imsi1+" imsi2="+imsi2); 
		Log.d(TAG_XHFRB, "mapPreferredApnIfNeeded: curImsi="+curImsi); 
		if (imsi1.equals("N/A")) 
		{ 
			return oldApnId; 
		} 
		else 
		{ 
			if(imsi1.equals(curImsi))
			{ 
				newApnId = getApnIdForDualIMSI(1 , subId); 
				if(newApnId.equals("-1") || newApnId.equals(oldApnId)) 
				{
					return oldApnId; 
				}
				dualImsiSyncWithPreferredApn(Integer.parseInt(newApnId)); 
				return newApnId; 
			} 
			else
			{ 
				if(imsi2.equals("N/A"))
				{ 
					return oldApnId; 
				} 
				else
				{ 
					if(imsi2.equals(curImsi))
					{ 
						newApnId = getApnIdForDualIMSI(2 , subId); 
						if(newApnId.equals("-1") || newApnId.equals(oldApnId)) 
						{
							return oldApnId; 
						}
						dualImsiSyncWithPreferredApn(Integer.parseInt(newApnId)); 
						return newApnId; 
					} 
					else
					{ 
						return oldApnId; 
					} 
				} 
			} 
		} 
	} 

	protected void updateDualImsiPreferredApnIfNeeded(String apnId) 
	{ 
		String imsi1 = ""; 
		String imsi2 = ""; 
		int subId = mSubscriptionInfo.getSubscriptionId(); 
		String curImsi = getCurrentIMSI(); 

		imsi1 = getImsiForDualIMSI(1 , subId); 
		imsi2 = getImsiForDualIMSI(2 , subId); 

		Log.d(TAG_XHFRB, "updateDualImsiPreferredApnIfNeeded: imsi1="+imsi1+" imsi2="+imsi2); 
		Log.d(TAG_XHFRB, "updateDualImsiPreferredApnIfNeeded: curImsi="+curImsi); 
		if (imsi1.equals("N/A") || imsi1.equals(curImsi))
		{ 
			setPreferredApnIdForDualIMSI(1 , curImsi , apnId); 
		}
		else if(imsi2.equals("N/A") || imsi2.equals(curImsi))
		{ 
			setPreferredApnIdForDualIMSI(2 , curImsi , apnId); 
		}
		else
		{ 
			//maybe it's a new card? 
			setPreferredApnIdForDualIMSI(1 , curImsi , apnId); 
			setPreferredApnIdForDualIMSI(2 , "N/A" , "-1"); 
		} 
	} 	
	///HQ_xionghaifeng 20160108 add for HQ01591823 movistar Roaming Broker end
}
