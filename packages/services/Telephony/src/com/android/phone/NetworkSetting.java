/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.phone;

import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.os.SystemProperties;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.OperatorInfo;
import com.mediatek.phone.PhoneFeatureConstants;
import com.mediatek.phone.ext.ExtensionManager;
import com.mediatek.settings.TelephonyUtils;

import java.util.HashMap;
import java.util.List;

/*add by yulifeng for searching network,get networktitle ,b*/
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import android.util.Xml;
import android.telephony.PhoneNumberUtils;
import android.os.Environment;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import com.android.internal.util.XmlUtils;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.PhoneConstants;
/*add by yulifeng for searching network ,get networktitle,e*/


/**
 * "Networks" settings UI for the Phone app.
 */
public class NetworkSetting extends PreferenceActivity
         implements DialogInterface.OnCancelListener, PhoneGlobals.SubInfoUpdateListener,
         DialogInterface.OnDismissListener, DialogInterface.OnShowListener {

    private static final String LOG_TAG = "phone";
    private static final boolean DBG = true;

    private static final int EVENT_NETWORK_SCAN_COMPLETED = 100;
    private static final int EVENT_NETWORK_SELECTION_DONE = 200;
    private static final int EVENT_AUTO_SELECT_DONE = 300;

    //dialog ids
    private static final int DIALOG_NETWORK_SELECTION = 100;
    private static final int DIALOG_NETWORK_LIST_LOAD = 200;
    private static final int DIALOG_NETWORK_AUTO_SELECT = 300;

    /// M: add for all network is forbidden
    private static final int DIALOG_ALL_FORBIDDEN = 400;
    //String keys for preference lookup
    private static final String LIST_NETWORKS_KEY = "list_networks_key";
    private static final String BUTTON_SRCH_NETWRKS_KEY = "button_srch_netwrks_key";
    private static final String BUTTON_AUTO_SELECT_KEY = "button_auto_select_key";

    /*add by yulifeng for searching network ,get networktitle,b*/
    private static final String PARTNER_NETWORKTITLE_ENTITY_CONF_PATH ="system/etc/NetworkTitle_entity.xml";
    private static final String PARTNER_NETWORKTITLE_IMSI_CONF_PATH ="system/etc/NetworkTitle_imsi.xml";
    private static final String PARTNER_NETWORKTITLE_SPN_CONF_PATH ="system/etc/NetworkTitle_spn.xml";	
    private static final String PARTNER_NETWORKTITLE_GID_CONF_PATH ="system/etc/NetworkTitle_gid.xml";
    /*add by yulifeng for searching network ,get networktitle,e*/

    //map of network controls to the network data.
    private HashMap<IconRightPreference, OperatorInfo> mNetworkMap;

    Phone mPhone;
    private int mSubId = INVALID_SUBSCRIPTION_ID;
    protected boolean mIsForeground = false;

    private UserManager mUm;
    private boolean mUnavailable;

    /** message for network selection */
    String mNetworkSelectMsg;

    //preference objects
    private PreferenceGroup mNetworkList;
    private Preference mSearchButton;
    private Preference mAutoSelect;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            switch (msg.what) {
                case EVENT_NETWORK_SCAN_COMPLETED:
                    networksListLoaded ((List<OperatorInfo>) msg.obj, msg.arg1);
                    break;

                case EVENT_NETWORK_SELECTION_DONE:
                    if (DBG) log("hideProgressPanel");
                    removeDialog(DIALOG_NETWORK_SELECTION);
                    getPreferenceScreen().setEnabled(true);

                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        if (DBG) log("manual network selection: failed!");
                        displayNetworkSelectionFailed(ar.exception);
                    } else {
                        if (DBG) log("manual network selection: succeeded!");
                        displayNetworkSelectionSucceeded();
                    }

                    // update the phone in case replaced as part of selection
                    mPhone = PhoneUtils.getPhoneUsingSubId(mSubId);

                    break;
                case EVENT_AUTO_SELECT_DONE:
                    if (DBG) log("hideProgressPanel");

                    // Always try to dismiss the dialog because activity may
                    // be moved to background after dialog is shown.
                    try {
                        dismissDialog(DIALOG_NETWORK_AUTO_SELECT);
                    } catch (IllegalArgumentException e) {
                        // "auto select" is always trigged in foreground, so "auto select" dialog
                        //  should be shown when "auto select" is trigged. Should NOT get
                        // this exception, and Log it.
                        Log.w(LOG_TAG, "[NetworksList] Fail to dismiss auto select dialog", e);
                    }
                    getPreferenceScreen().setEnabled(true);

                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        if (DBG) log("automatic network selection: failed!");
                        displayNetworkSelectionFailed(ar.exception);
                    } else {
                        if (DBG) log("automatic network selection: succeeded!");
                        displayNetworkSelectionSucceeded();
                    }

                    // update the phone in case replaced as part of selection
                    mPhone = PhoneUtils.getPhoneUsingSubId(mSubId);

                    break;
            }

            return;
        }
    };

    /**
     * Service connection code for the NetworkQueryService.
     * Handles the work of binding to a local object so that we can make
     * the appropriate service calls.
     */

    /** Local service interface */
    private INetworkQueryService mNetworkQueryService = null;

    /** Service connection */
    private final ServiceConnection mNetworkQueryServiceConnection = new ServiceConnection() {

        /** Handle the task of binding the local object to the service */
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (DBG) log("connection created, binding local service.");
            /// M: modify for plug-in, class of <service> may be difference
            /// if publishBinderDirectly return different value @{
            if (ExtensionManager.getPhoneMiscExt().publishBinderDirectly()) {
                mNetworkQueryService = INetworkQueryService.Stub.asInterface(service);
            } else {
                mNetworkQueryService = ((NetworkQueryService.LocalBinder) service).getService();
            }
            /// @}
            // as soon as it is bound, run a query.
            loadNetworksList();
        }

        /** Handle the task of cleaning up the local binding */
        public void onServiceDisconnected(ComponentName className) {
            if (DBG) log("connection disconnected, cleaning local binding.");
            mNetworkQueryService = null;
        }
    };

    /**
     * This implementation of INetworkQueryServiceCallback is used to receive
     * callback notifications from the network query service.
     */
    private final INetworkQueryServiceCallback mCallback = new INetworkQueryServiceCallback.Stub() {

        /** place the message on the looper queue upon query completion. */
        public void onQueryComplete(List<OperatorInfo> networkInfoArray, int status) {
            if (DBG) log("notifying message loop of query completion.");
            Message msg = mHandler.obtainMessage(EVENT_NETWORK_SCAN_COMPLETED,
                    status, 0, networkInfoArray);
            msg.sendToTarget();
        }
    };

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        boolean handled = false;

        if (preference == mSearchButton) {
            loadNetworksList();
            handled = true;
        } else if (preference == mAutoSelect) {
            selectNetworkAutomatic();
            handled = true;
            /// M: For CSG feature @{
        } else if (preference == mManuSelectFemtocell) {
            selectFemtocellManually();
            handled = true;
            /// @}
        } else {
            IconRightPreference selectedCarrier = (IconRightPreference)preference;

            String networkStr = selectedCarrier.getTitle().toString();
            android.util.Log.d("IconRightPreference", "networkStr is " + networkStr);
            if (DBG) {
                log("selected network: " + networkStr);
            }
            if (mNetworkMap != null) {
                if (!ExtensionManager.getNetworkSettingExt()
                        .onPreferenceTreeClick(mNetworkMap.get(selectedCarrier), mSubId)) {
                    Message msg = mHandler.obtainMessage(EVENT_NETWORK_SELECTION_DONE);
                    mPhone.selectNetworkManually(mNetworkMap.get(selectedCarrier), msg);
                    displayNetworkSeletionInProgress(networkStr);
                }
            } else {
                log("[onPreferenceTreeClick] select on PLMN, but mNetworkMap == null !!!");
            }
            handled = true;
        }

        return handled;
    }

    //implemented for DialogInterface.OnCancelListener
    public void onCancel(DialogInterface dialog) {
        // request that the service stop the query with this callback object.
        try {
            mNetworkQueryService.stopNetworkQuery(mCallback);
        } catch (RemoteException e) {
            log("onCancel: exception from stopNetworkQuery " + e);
        }
        finish();
    }

    public String getNormalizedCarrierName(OperatorInfo ni) {
        if (ni != null) {
            return ni.getOperatorAlphaLong() + " (" + ni.getOperatorNumeric() + ")";
        }
        return null;
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mUm = (UserManager) getSystemService(Context.USER_SERVICE);

        if (mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)) {
            setContentView(R.layout.telephony_disallowed_preference_screen);
            mUnavailable = true;
            return;
        }

        ///M: Add for ALPS02067026 When the phone is in call, cancel the search network action @{
        TelecomManager telecomManager = TelecomManager.from(this);
        if (telecomManager.isInCall()) {
            log("The phone is in call, so destroy.");
            displayNetworkQueryFailed(NetworkQueryService.QUERY_EXCEPTION);
            finish();
            return;
        }
        /// @}

        addPreferencesFromResource(R.xml.carrier_select);

        PhoneGlobals.getInstance().addSubInfoUpdateListener(this);
        if (icicle == null) {
            mSubscriptionInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        } else {
            Intent intent = new Intent();
            intent.putExtra(SubscriptionInfoHelper.SUB_ID_EXTRA,
                    icicle.getInt(SubscriptionInfoHelper.SUB_ID_EXTRA, INVALID_SUBSCRIPTION_ID));
            mSubscriptionInfoHelper = new SubscriptionInfoHelper(this, intent);
            Log.d(LOG_TAG, "get sub id from icicle.");
        }
        mSubId = mSubscriptionInfoHelper.getSubId();
        if (!PhoneUtils.isValidSubId(mSubId)) {
            log("mSubId is invalid,activity finish!!!");
            finish();
            return;
        }
        mPhone = PhoneUtils.getPhoneUsingSubId(mSubId);

        mNetworkList = (PreferenceGroup) getPreferenceScreen().findPreference(LIST_NETWORKS_KEY);
        mNetworkMap = new HashMap<IconRightPreference, OperatorInfo>();

        mSearchButton = getPreferenceScreen().findPreference(BUTTON_SRCH_NETWRKS_KEY);
        mAutoSelect = getPreferenceScreen().findPreference(BUTTON_AUTO_SELECT_KEY);

        // Start the Network Query service, and bind it.
        // The OS knows to start he service only once and keep the instance around (so
        // long as startService is called) until a stopservice request is made.  Since
        // we want this service to just stay in the background until it is killed, we
        // don't bother stopping it from our end.
        Intent intent = new Intent(this, NetworkQueryService.class);
        intent.putExtra(SubscriptionInfoHelper.SUB_ID_EXTRA, mSubId);
        startService(intent);
        bindService(new Intent(this, NetworkQueryService.class),
                mNetworkQueryServiceConnection, Context.BIND_AUTO_CREATE);
        /// M: Add for CSG @{
        newManuSelectFemetocellPreference(getPreferenceScreen());
        /// @}
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsForeground = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsForeground = false;
        if (!mUnavailable && mNetworkQueryService != null) {
            // unbind the service.
            try {
                mNetworkQueryService.stopNetworkQuery(mCallback);
                dismissDialog(DIALOG_NETWORK_LIST_LOAD);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            } catch (IllegalArgumentException e) {
                if (DBG) log("Fail to dismiss network load list dialog");
            }
            getPreferenceScreen().setEnabled(true);
        }
    }

    /**
     * Override onDestroy() to unbind the query service, avoiding service
     * leak exceptions.
     */
    @Override
    protected void onDestroy() {
        if (!mUnavailable && mNetworkQueryService != null) {
            try {
                // used to un-register callback
                mNetworkQueryService.unregisterCallback(mCallback);
            } catch (RemoteException e) {
                log("onDestroy: exception from unregisterCallback " + e);
            }
            unbindService(mNetworkQueryServiceConnection);
        }
        PhoneGlobals.getInstance().removeSubInfoUpdateListener(this);
        super.onDestroy();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        /// M: when all the network forbidden show dialog remind user @{
        if (id == DIALOG_ALL_FORBIDDEN) {
            int themeID = getResources().getIdentifier("androidhwext:style/Theme.Emui.Dialog", null, null);
            Builder builder = new AlertDialog.Builder(this, themeID);
            AlertDialog alertDlg;
            builder.setTitle(android.R.string.dialog_alert_title);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(R.string.network_setting_all_forbidden_dialog);
            builder.setPositiveButton(android.R.string.yes, null);
            alertDlg = builder.create();
            return alertDlg;
        }
        /// @}

        if ((id == DIALOG_NETWORK_SELECTION) || (id == DIALOG_NETWORK_LIST_LOAD) ||
                (id == DIALOG_NETWORK_AUTO_SELECT)) {
            ProgressDialog dialog = new ProgressDialog(this);
            switch (id) {
                case DIALOG_NETWORK_SELECTION:
                    // It would be more efficient to reuse this dialog by moving
                    // this setMessage() into onPreparedDialog() and NOT use
                    // removeDialog().  However, this is not possible since the
                    // message is rendered only 2 times in the ProgressDialog -
                    // after show() and before onCreate.
                    dialog.setMessage(mNetworkSelectMsg);
                    dialog.setCancelable(false);
                    dialog.setIndeterminate(true);
                    break;
                case DIALOG_NETWORK_AUTO_SELECT:
                    dialog.setMessage(getResources().getString(R.string.register_automatically));
                    dialog.setCancelable(false);
                    dialog.setIndeterminate(true);
                    break;
                case DIALOG_NETWORK_LIST_LOAD:
                    // M: ALPS01261105 Set show & dismiss listener @{
                    dialog.setOnDismissListener(this);
                    dialog.setOnShowListener(this);
                    // @}
                default:
                    // reinstate the cancelablity of the dialog.
                    dialog.setMessage(getResources().getString(R.string.load_networks_progress));
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.setOnCancelListener(this);
                    break;
            }
            return dialog;
        }
        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        if ((id == DIALOG_NETWORK_SELECTION) || (id == DIALOG_NETWORK_LIST_LOAD) ||
                (id == DIALOG_NETWORK_AUTO_SELECT)) {
            // when the dialogs come up, we'll need to indicate that
            // we're in a busy state to dissallow further input.
            getPreferenceScreen().setEnabled(false);
        }
    }

    private void displayEmptyNetworkList(boolean flag) {
        mNetworkList.setTitle(flag ? R.string.empty_networks_list : R.string.label_available);
    }

    private void displayNetworkSeletionInProgress(String networkStr) {
        // TODO: use notification manager?
        mNetworkSelectMsg = getResources().getString(R.string.register_on_network, networkStr);

        if (mIsForeground) {
            showDialog(DIALOG_NETWORK_SELECTION);
        }
    }

    private void displayNetworkQueryFailed(int error) {
        String status = getResources().getString(R.string.network_query_error);

        final PhoneGlobals app = PhoneGlobals.getInstance();
        app.notificationMgr.postTransientNotification(
                NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);
    }

    private void displayNetworkSelectionFailed(Throwable ex) {
        String status;

        if ((ex != null && ex instanceof CommandException) &&
                ((CommandException)ex).getCommandError()
                  == CommandException.Error.ILLEGAL_SIM_OR_ME)
        {
            status = getResources().getString(R.string.not_allowed);
        } else {
            status = getResources().getString(R.string.connect_later);
        }

        final PhoneGlobals app = PhoneGlobals.getInstance();
        app.notificationMgr.postTransientNotification(
                NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);
    }

    private void displayNetworkSelectionSucceeded() {
        String status = getResources().getString(R.string.registration_done);

        final PhoneGlobals app = PhoneGlobals.getInstance();
        app.notificationMgr.postTransientNotification(
                NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);

        mHandler.postDelayed(new Runnable() {
            public void run() {
                finish();
            }
        }, 3000);
    }

    private void loadNetworksList() {
        if (DBG) log("load networks list...");
		Log.d("hhq", "[loadNetworksList]...");

        if (mIsForeground) {
            showDialog(DIALOG_NETWORK_LIST_LOAD);
        }

        // delegate query request to the service.
        try {
            mNetworkQueryService.startNetworkQuery(mCallback);
        } catch (RemoteException e) {
            log("loadNetworksList: exception from startNetworkQuery " + e);
            if (mIsForeground) {
                try {
                    dismissDialog(DIALOG_NETWORK_LIST_LOAD);
                } catch (IllegalArgumentException e1) {
                    // do nothing
                }
            }
        }

        displayEmptyNetworkList(false);
    }

    /**
     * networksListLoaded has been rewritten to take an array of
     * OperatorInfo objects and a status field, instead of an
     * AsyncResult.  Otherwise, the functionality which takes the
     * OperatorInfo array and creates a list of preferences from it,
     * remains unchanged.
     */
    private void networksListLoaded(List<OperatorInfo> result, int status) {
        if (DBG) log("networks list loaded");
		Log.d("hhq", "[networksListLoaded] ");
        // used to un-register callback
        try {
        
            mNetworkQueryService.unregisterCallback(mCallback);
        } catch (RemoteException e) {
            log("networksListLoaded: exception from unregisterCallback " + e);
        }



        // update the state of the preferences.
        if (DBG) log("hideProgressPanel");
		

        /// M: Add for plug-in @{
        result = ExtensionManager.getNetworkSettingExt().customizeNetworkList(result, mSubId);
        /// @}
        

        // Always try to dismiss the dialog because activity may
        // be moved to background after dialog is shown.
        try {
            dismissDialog(DIALOG_NETWORK_LIST_LOAD);
        } catch (IllegalArgumentException e) {
            // It's not a error in following scenario, we just ignore it.
            // "Load list" dialog will not show, if NetworkQueryService is
            // connected after this activity is moved to background.
            if (DBG) log("Fail to dismiss network load list dialog");
        }

        getPreferenceScreen().setEnabled(true);
        clearList();

        if (status != NetworkQueryService.QUERY_OK) {
            if (DBG) log("error while querying available networks");
            displayNetworkQueryFailed(status);
            displayEmptyNetworkList(true);
        } else {
            if (result != null){
                displayEmptyNetworkList(false);

                // create a preference for each item in the list.
                // just use the operator name instead of the mildly
                // confusing mcc/mnc.
                /// M: add forbidden at the end of operator name
                int forbiddenCount = 0;

				boolean isTL = SystemProperties.getBoolean("ro.version.tl", false);
				Log.d("hhq", "[isTL] = " + isTL);
				
                for (OperatorInfo ni : result) {
                    String forbidden = "";
                    boolean isRedColor = false;
                    int drawableId = R.drawable.mtk_network_default;
                    if (ni.getState() == OperatorInfo.State.FORBIDDEN) {
                                forbidden = "(" + getResources().getString(
                                R.string.network_forbidden) + ")";
                                forbiddenCount++;
                                drawableId = R.drawable.mtk_network_forbidden;
                    }else if (ni.getState() == OperatorInfo.State.CURRENT) {
                    		if(!isTL){
                            	forbidden = "(" + getResources().getString(R.string.network_current) + ")";
                                drawableId = R.drawable.mtk_network_current;
                                isRedColor = true;
                    		}
                    }else if (ni.getState() == OperatorInfo.State.UNKNOWN) {
                    		if(!isTL){
                            	forbidden = "(" + getResources().getString(R.string.network_unkown) + ")";
                                drawableId = R.drawable.mtk_network_default;
                    		}
		    		}else if(ni.getState() == OperatorInfo.State.AVAILABLE) {
		    				if(!isTL){
                        		forbidden = "(" + getResources().getString(R.string.network_available) + ")";
                                drawableId = R.drawable.mtk_network_available;
		    				}
                    }

					/*add by yulifeng for searching network,get networktitle ,b*/
					TelephonyManager tm = (TelephonyManager) this.getSystemService(TELEPHONY_SERVICE);
					String networkTitle = getNetworkTitle(ni);
					String mOperatorNumeric = ni.getOperatorNumeric();	
					//String simSpn=tm.getSimOperatorName();
					//String NetworkOperator=tm.getNetworkOperatorName();
					Log.d("yulifeng", "networkTitle="+networkTitle+";mOperatorNumeric="+mOperatorNumeric);
					//Log.d("yulifeng", "simSpn="+simSpn+";NetworkOperator="+NetworkOperator);
					int slotId = SubscriptionManager.getSlotId(mSubId);
					String sim_imsi=PhoneNumberUtils.getDefaultImsi(slotId);
					String sim_gid=PhoneNumberUtils.getDefaultGID1(slotId);
					String sim_type=PhoneNumberUtils.getVirtualType(slotId).get(0);
					String sim_value=PhoneNumberUtils.getVirtualType(slotId).get(1);
					String simNumeric=PhoneNumberUtils.getSimMccMnc(slotId);
					Log.d("yulifeng", "slotId="+slotId+";sim_imsi="+sim_imsi+";sim_gid="+sim_gid+
							";sim_type="+sim_type+"sim_value="+sim_value+"simNumeric="+simNumeric);
					// sim_type IMSI 1,GID  2; SPN  4; EFFILE 8; MCCMNC = 16; 
					if(SystemProperties.get("ro.hq.network.search").equals("1")){
					    if("1".equals(sim_type)){
						    networkTitle = getNetworkTitleByImsi(mOperatorNumeric,networkTitle,sim_imsi);
					    }else if("2".equals(sim_type)){
						    networkTitle = getNetworkTitleByGid(mOperatorNumeric,simNumeric,networkTitle,sim_gid);
					    }else if("4".equals(sim_type)){
						    networkTitle = getNetworkTitleBySpn(mOperatorNumeric,networkTitle,sim_value);
					    }else if("16".equals(sim_type)){
						    networkTitle = getEntityNetworkTitle(mOperatorNumeric,simNumeric,networkTitle);
					    }
					}
					/*add by yulifeng for searching network,get networktitle ,e*/
                    /*HQ_hushunli add for HQ01683963 begin*/
                    IconRightPreference carrier = new IconRightPreference(this);
                    /*HQ_guomiao add for HQ01531556 begin*/
                    if (SystemProperties.get("ro.hq.search.network.icon").equals("1")) {
                        carrier.setIcon(drawableId);
                    }
                    /*HQ_guomiao add for HQ01531556 end*/
                    Log.d("IconRightPreference", "networkTitle  red is " + networkTitle);
                    carrier.setTitle(networkTitle + forbidden, isRedColor);
                    /*HQ_hushunli add for HQ01683963 end*/
                    carrier.setPersistent(false);
                    mNetworkList.addPreference(carrier);
                    mNetworkMap.put(carrier, ni);

                    if (DBG) {
                        log("  " + ni);
                    }
                }
                if (mIsForeground && forbiddenCount == result.size()) {
                    if (DBG) {
                        log("show DIALOG_ALL_FORBIDDEN forbiddenCount:" + forbiddenCount);
                    }
                    showDialog(DIALOG_ALL_FORBIDDEN);
                }

            } else {
                displayEmptyNetworkList(true);
            }
        }
    }

    /**
    * add by yulifeng - get Entity NetworkTitle  20150907
    * Return the title of the network obtained in the manual search.
    * @param mSimOperatorNumeric : Operator MCCMNC
    * @param simNumeric : SIM MCCMNC
    * 
    */  
    private String getEntityNetworkTitle(String mOperatorNumeric,String mSimNumeric,String networkTitle){
        Log.d("yulifeng","getEntityNetworkTitle mOperatorNumeric: "+mOperatorNumeric+
			"; networkTitle: "+networkTitle+"; mSimNumeric: "+mSimNumeric);
        FileReader networkTitleReader;
		String simNumeric = null; //SIM MCCMNC
		String operatorNumeric = null; //Operator MCCMNC
        final File networkTitleFile = new File(PARTNER_NETWORKTITLE_ENTITY_CONF_PATH);
        try {
            networkTitleReader = new FileReader(networkTitleFile);
        } catch (FileNotFoundException e) {
            Log.w("yulifeng", "Can't open " + PARTNER_NETWORKTITLE_ENTITY_CONF_PATH);
            return networkTitle;
        }
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(networkTitleReader);
            XmlUtils.beginDocument(parser, "entity");
            while (true) {
                XmlUtils.nextElement(parser);
                String name = parser.getName();
                if (name == null) {
                    break;
                }else if("simcard".equals(name)){
                    simNumeric = parser.getAttributeValue(null, "simNumeric");
                    simNumeric = ("".equals(simNumeric))?mSimNumeric:simNumeric ;
                    Log.d("yulifeng", "getEntityNetworkTitle simNumeric: "+simNumeric);
                }else if("Operator".equals(name)){
                    operatorNumeric = parser.getAttributeValue(null, "operatorNumeric");
					Log.d("yulifeng", "getEntityNetworkTitle operatorNumeric: "+operatorNumeric);
				}else if("networktitle".equals(name)){
				    if((simNumeric.equals(mSimNumeric)||(simNumeric.length() == 3 && mSimNumeric !=null && mSimNumeric.startsWith(simNumeric)))
						&& operatorNumeric.equals(mOperatorNumeric)){
                        String _2G = parser.getAttributeValue(null, "_2G");
						String _3G = parser.getAttributeValue(null, "_3G");
						String _4G = parser.getAttributeValue(null, "_4G");
						Log.d("yulifeng", "getEntityNetworkTitle _2G: "+_2G+"; _3G: "+_3G+"; _4G: "+_4G);
						if(networkTitle.contains("2G")){
							return ("".equals(_2G))?networkTitle:_2G ;					
						}else if(networkTitle.contains("3G")){
							return ("".equals(_3G))?networkTitle:_3G ;
						}else if(networkTitle.contains("4G")){
							return ("".equals(_4G))?networkTitle:_4G ;					
						}
					}   	
				}
            }
        } catch (XmlPullParserException e) {
            Log.w("yulifeng", "Exception in XmlPullParserException " + e);
        } catch (IOException e) {
            Log.w("yulifeng", "Exception in XmlPullParserException " + e);
        }finally {
            try {
                if (networkTitleReader != null) {
                    networkTitleReader.close();
                }
            } catch (IOException e) {}
        }
        return networkTitle;
    }//end


    /**
    * add by yulifeng - get virtual NetworkTitle by imsi 20150907
    * Return the title of the network obtained in the manual search.
    * @param mOperatorNumeric : Operator MCCMNC
    * @param imsi : SIM imsi
    * 
    */
	private String getNetworkTitleByImsi(String mOperatorNumeric,String networkTitle,String imsi){
        Log.d("yulifeng","getNetworkTitleByImsi mOperatorNumeric: "+mOperatorNumeric+
			"; networkTitle: "+networkTitle+"; imsi: "+imsi);
        FileReader networkTitleReader;
        String imsiNumeric = null; //SIM imsi
		String operatorNumeric = null; //Operator MCCMNC
		final File networkTitleFile = new File(PARTNER_NETWORKTITLE_IMSI_CONF_PATH);
		try {
			networkTitleReader = new FileReader(networkTitleFile);
		} catch (FileNotFoundException e) {
			Log.w("yulifeng", "Can't open " + PARTNER_NETWORKTITLE_IMSI_CONF_PATH);
			return networkTitle;
		}
		try {
			XmlPullParser parser = Xml.newPullParser();
			parser.setInput(networkTitleReader);
			XmlUtils.beginDocument(parser, "virtual");
			while (true) {
				XmlUtils.nextElement(parser);
				String name = parser.getName();
				if (name == null) {
					break;
				}else if("simcard".equals(name)){
					imsiNumeric = parser.getAttributeValue(null, "imsiNumeric");
					Log.d("yulifeng", "getNetworkTitleByImsi imsiNumeric: "+imsiNumeric);
				}else if("Operator".equals(name)){
					operatorNumeric = parser.getAttributeValue(null, "operatorNumeric");
					Log.d("yulifeng", "getNetworkTitleByImsi operatorNumeric: "+operatorNumeric);
				}else if("networktitle".equals(name)){
					if(imsi!=null && imsi.startsWith(imsiNumeric) && operatorNumeric.equals(mOperatorNumeric)){
						String _2G = parser.getAttributeValue(null, "_2G");
						String _3G = parser.getAttributeValue(null, "_3G");
						String _4G = parser.getAttributeValue(null, "_4G");
						Log.d("yulifeng", "getNetworkTitleByImsi _2G: "+_2G+"; _3G: "+_3G+"; _4G: "+_4G);
						if(networkTitle.contains("2G")){
							return ("".equals(_2G))?networkTitle:_2G ;					
						}else if(networkTitle.contains("3G")){
							return ("".equals(_3G))?networkTitle:_3G ;
						}else if(networkTitle.contains("4G")){
							return ("".equals(_4G))?networkTitle:_4G ;					
						}
					}	
				}
			}
		} catch (XmlPullParserException e) {
			Log.w("yulifeng", "Exception in XmlPullParserException " + e);
		} catch (IOException e) {
			Log.w("yulifeng", "Exception in XmlPullParserException " + e);
		}finally {
			try {
				if (networkTitleReader != null) {
					networkTitleReader.close();
				}
			} catch (IOException e) {}
		}
        return networkTitle;
	}


	/**
	 * add by yulifeng - get virtual NetworkTitle by spn 20150907
	 * Return the title of the network obtained in the manual search.
	 * @param mOperatorNumeric : Operator MCCMNC
	 * @param mSpn : SIM spn
	 * 
	 */
	 private String getNetworkTitleBySpn(String mOperatorNumeric,String networkTitle,String mSpn){
		 Log.d("yulifeng","getNetworkTitleBySpn mOperatorNumeric: "+mOperatorNumeric+
		 	"; networkTitle: "+networkTitle+"; mSpn: "+mSpn);
		 FileReader networkTitleReader;
		 String operatorNumeric = null; //Operator MCCMNC
		 String spn_value = null;  //spn
		 final File networkTitleFile = new File(PARTNER_NETWORKTITLE_SPN_CONF_PATH);
		 try {
			 networkTitleReader = new FileReader(networkTitleFile);
		 } catch (FileNotFoundException e) {
			 Log.w("yulifeng", "Can't open " + PARTNER_NETWORKTITLE_SPN_CONF_PATH);
			 return networkTitle;
		 }
		 try {
			 XmlPullParser parser = Xml.newPullParser();
			 parser.setInput(networkTitleReader);
			 XmlUtils.beginDocument(parser, "virtual");
			 while (true) {
				 XmlUtils.nextElement(parser);
				 String name = parser.getName();
				 if (name == null) {
					 break;
				 }else if("Operator".equals(name)){
					 operatorNumeric = parser.getAttributeValue(null, "operatorNumeric");
					 Log.d("yulifeng", "getNetworkTitleBySpn operatorNumeric: "+operatorNumeric);
				 }else if("spn".equals(name)){
					 spn_value = parser.getAttributeValue(null, "spn_value");
					 Log.d("yulifeng", "getNetworkTitleBySpn spn_value: "+spn_value);
				 }else if("networktitle".equals(name)){
					 if(operatorNumeric.equals(mOperatorNumeric) && spn_value.equals(mSpn)){
						 String _2G = parser.getAttributeValue(null, "_2G");
						 String _3G = parser.getAttributeValue(null, "_3G");
						 String _4G = parser.getAttributeValue(null, "_4G");
						 Log.d("yulifeng", "getNetworkTitleBySpn _2G: "+_2G+"; _3G: "+_3G+"; _4G: "+_4G);
						 if(networkTitle.contains("2G")){
							return ("".equals(_2G))?networkTitle:_2G ;					
						}else if(networkTitle.contains("3G")){
							return ("".equals(_3G))?networkTitle:_3G ;
						}else if(networkTitle.contains("4G")){
							return ("".equals(_4G))?networkTitle:_4G ;					
						}
					 }	 
				 }
			 }
		 } catch (XmlPullParserException e) {
			 Log.w("yulifeng", "Exception in XmlPullParserException " + e);
		 } catch (IOException e) {
			 Log.w("yulifeng", "Exception in XmlPullParserException " + e);
		 }finally {
			 try {
				 if (networkTitleReader != null) {
					 networkTitleReader.close();
				 }
			 } catch (IOException e) {}
		 }
         return networkTitle;
	 }



	/**
		 * add by yulifeng - get virtual NetworkTitle by gid 20150907
		 * Return the title of the network obtained in the manual search.
		 * @param mOperatorNumeric : Operator MCCMNC
		 * @param mSimNumeric : SIM mccmnc
		 * 
		 */
    private String getNetworkTitleByGid(String mOperatorNumeric,String mSimNumeric,
        String networkTitle,String mGid){
        Log.d("yulifeng","getNetworkTitleByGid mOperatorNumeric: "+mOperatorNumeric+
			 "; mSimNumeric: "+mSimNumeric+"; networkTitle: "+networkTitle+"; mGid: "+mGid);
		FileReader networkTitleReader;
		String simNumeric = null; //SIM mccmnc
		String operatorNumeric = null; //Operator MCCMNC
		String gid_value = null; //gid
		final File networkTitleFile = new File(PARTNER_NETWORKTITLE_GID_CONF_PATH);
		try {
			networkTitleReader = new FileReader(networkTitleFile);
		} catch (FileNotFoundException e) {
			Log.w("yulifeng", "Can't open " + PARTNER_NETWORKTITLE_GID_CONF_PATH);
			return networkTitle;
		}
		try {
			XmlPullParser parser = Xml.newPullParser();
			parser.setInput(networkTitleReader);
			XmlUtils.beginDocument(parser, "virtual");
			while (true) {
				XmlUtils.nextElement(parser);
				String name = parser.getName();
				if (name == null) {
					break;
				}else if("simcard".equals(name)){
					simNumeric = parser.getAttributeValue(null, "simNumeric");
					Log.d("yulifeng", "getNetworkTitleByGid simNumeric: "+simNumeric);
				}else if("Operator".equals(name)){
					operatorNumeric = parser.getAttributeValue(null, "operatorNumeric");
					Log.d("yulifeng", "getNetworkTitleByGid operatorNumeric: "+operatorNumeric);
				}else if("gid".equals(name)){
					gid_value = parser.getAttributeValue(null, "gid_value");
					Log.d("yulifeng", "getNetworkTitleByGid gid_value: "+gid_value);
				}else if("networktitle".equals(name)){
					if(simNumeric.equals(mSimNumeric) && operatorNumeric.equals(mOperatorNumeric) && gid_value.equals(mGid)){
						String _2G = parser.getAttributeValue(null, "_2G");
						String _3G = parser.getAttributeValue(null, "_3G");
						String _4G = parser.getAttributeValue(null, "_4G");
						Log.d("yulifeng", "getNetworkTitleByGid _2G: "+_2G+"; _3G: "+_3G+"; _4G: "+_4G);
						if(networkTitle.contains("2G")){
							return ("".equals(_2G))?networkTitle:_2G ;					
						}else if(networkTitle.contains("3G")){
							return ("".equals(_3G))?networkTitle:_3G ;
						}else if(networkTitle.contains("4G")){
							return ("".equals(_4G))?networkTitle:_4G ;					
						}
					}		 
				}
			}
		} catch (XmlPullParserException e) {
			Log.w("yulifeng", "Exception in XmlPullParserException " + e);
		} catch (IOException e) {
			Log.w("yulifeng", "Exception in XmlPullParserException " + e);
		}finally {
			try {
				if (networkTitleReader != null) {
					networkTitleReader.close();
				}
			} catch (IOException e) {}
		}
        return networkTitle;
	}


    /**
     * Returns the title of the network obtained in the manual search.
     *
     * @param OperatorInfo contains the information of the network.
     *
     * @return Long Name if not null/empty, otherwise Short Name if not null/empty,
     * else MCCMNC string.
     */

    private String getNetworkTitle(OperatorInfo ni) {
        if (!TextUtils.isEmpty(ni.getOperatorAlphaLong())) {
            Log.d("yulifeng", "getNetworkTitle getOperatorAlphaLong: "+ni.getOperatorAlphaLong());
            return ni.getOperatorAlphaLong();
        } else if (!TextUtils.isEmpty(ni.getOperatorAlphaShort())) {
            Log.d("yulifeng", "getNetworkTitle getOperatorAlphaShort: "+ni.getOperatorAlphaShort());
            return ni.getOperatorAlphaShort();
        } else {
            Log.d("yulifeng", "getNetworkTitle getOperatorNumeric: "+ni.getOperatorNumeric());
            return ni.getOperatorNumeric();
        }
    }

    private void clearList() {
        for (IconRightPreference p : mNetworkMap.keySet()) {
            mNetworkList.removePreference(p);
        }
        mNetworkMap.clear();
    }

    private void selectNetworkAutomatic() {
        if (DBG) log("select network automatically...");
        if (mIsForeground) {
            showDialog(DIALOG_NETWORK_AUTO_SELECT);
        }

        Message msg = mHandler.obtainMessage(EVENT_AUTO_SELECT_DONE);
        mPhone.setNetworkSelectionModeAutomatic(msg);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SubscriptionInfoHelper.SUB_ID_EXTRA, mSubId);
    }

    @Override
    public void handleSubInfoUpdate() {
        log("handleSubInfoUpdate...");
        finish();
    }

    private void log(String msg) {
        Log.d(LOG_TAG, "[NetworksList] " + msg);
    }

    // -------   ----------------------------MTK----------------------------------------------
    /// M: Add for CSG @{
    private Preference mManuSelectFemtocell;
    private SubscriptionInfoHelper mSubscriptionInfoHelper;

    private void newManuSelectFemetocellPreference(PreferenceScreen root) {
        if (PhoneFeatureConstants.FeatureOption.isMtkFemtoCellSupport() &&
                !isNetworkModeSetGsmOnly()) {
            mManuSelectFemtocell = new Preference(getApplicationContext());
            mManuSelectFemtocell.setTitle(R.string.sum_search_femtocell_networks);
            root.addPreference(mManuSelectFemtocell);
        }
    }

    /**
     * Get the network mode is GSM Only or not.
     * @return if ture is GSM only else not
     */
    private boolean isNetworkModeSetGsmOnly() {
        return Phone.NT_MODE_GSM_ONLY == android.provider.Settings.Global.getInt(
                mPhone.getContext().getContentResolver(),
                android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                Phone.PREFERRED_NT_MODE);
    }

    private void selectFemtocellManually() {
        log("selectFemtocellManually()");
        Intent intent = new Intent();
        intent.setClassName("com.android.phone", "com.mediatek.settings.FemtoPointList");
        intent.putExtra(SubscriptionInfoHelper.SUB_ID_EXTRA, mSubId);
        startActivity(intent);
    }
    /// Add for CSG @}

    @Override
    public void onShow(DialogInterface dialog) {
        /// ALPS01261105. Keep activity screen on to prevent the screen from timing out.
        // when screen time out, the system call onPause() and make the query cancel.
        Window window = getWindow();
        if (window == null) {
            Log.i(LOG_TAG, "[onShow]window is null, skip adding flags");
            return;
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        /// ALPS01261105, clear flags in order to execute system time out.
        Window window = getWindow();
        if (window == null) {
            Log.i(LOG_TAG, "[onDismiss]window is null, skip clearing flags");
            return;
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        /// @}
    }
}
