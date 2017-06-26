/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_ETHERNET;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.PreciseDataConnectionState;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cdma.EriInfo;
import com.android.internal.util.AsyncChannel;
import com.android.systemui.DemoMode;
import com.android.systemui.R;
import com.mediatek.systemui.ext.BehaviorSet;
import com.mediatek.systemui.ext.DataType;
import com.mediatek.systemui.ext.FeatureOptionUtils;
import com.mediatek.systemui.ext.INetworkControllerExt;
import com.mediatek.systemui.ext.IconIdWrapper;
import com.mediatek.systemui.ext.NetworkType;
import com.mediatek.systemui.ext.PhoneHelper;
import com.mediatek.systemui.ext.PluginFactory;
import com.mediatek.systemui.ext.SvLteController;
import com.mediatek.systemui.statusbar.util.SIMHelper;
import com.mediatek.xlog.Xlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Platform implementation of the network controller. **/
public class NetworkControllerImpl extends BroadcastReceiver
        implements NetworkController, DemoMode {
    // debug
    static final String TAG = "NetworkController";
    static final boolean DEBUG = true; //Log.isLoggable(TAG, Log.DEBUG);
    // additional diagnostics, but not logspew
    static final boolean CHATTY = true; // Log.isLoggable(TAG + ".Chat", Log.DEBUG);
    // Save the previous SignalController.States of all SignalControllers for dumps.
    static final boolean RECORD_HISTORY = true;
    // If RECORD_HISTORY how many to save, must be a power of 2.
    static final int HISTORY_SIZE = 16;

    private static final int INET_CONDITION_THRESHOLD = 50;
    /// M: Support "WFC feature support".
    private static final int IMS_REGISTERED = ServiceState.STATE_IN_SERVICE;
    private static final int IMS_UNREGISTERED = ServiceState.STATE_OUT_OF_SERVICE;
    private static final String ACTION_IMS_CAP_CHANGE =
            "com.android.ims.IMS_CAPABILITIES_CHANGED";
    private static final String EXTRA_IMS_REG_STATE_KEY = "android:regState";
    private static final String EXTRA_PHONE_ID = "android:phone_id";
    private static final String EXTRA_IMS_ENABLE_CAP_KEY = "android:enablecap";

    private final Context mContext;
    private final TelephonyManager mPhone;
    private final WifiManager mWifiManager;
    private final ConnectivityManager mConnectivityManager;
    private final SubscriptionManager mSubscriptionManager;
    private final boolean mHasMobileDataFeature;
    private Config mConfig;

    // Subcontrollers.
    @VisibleForTesting
    final WifiSignalController mWifiSignalController;
    @VisibleForTesting
    final Map<Integer, MobileSignalController> mMobileSignalControllers =
            new HashMap<Integer, MobileSignalController>();
    // When no SIMs are around at setup, and one is added later, it seems to default to the first
    // SIM for most actions.  This may be null if there aren't any SIMs around.
    private MobileSignalController mDefaultSignalController;
    private final AccessPointControllerImpl mAccessPoints;
    private final MobileDataControllerImpl mMobileDataController;

    // Network types that replace the carrier label if the device does not support mobile data.
    private boolean mBluetoothTethered = false;
    private boolean mEthernetConnected = false;

    // state of inet connection
    private boolean mConnected = false;
    private boolean mInetCondition; // Used for Logging and demo.

    // BitSets indicating which network transport types (e.g., TRANSPORT_WIFI, TRANSPORT_MOBILE) are
    // connected and validated, respectively.
    private final BitSet mConnectedTransports = new BitSet();
    private final BitSet mValidatedTransports = new BitSet();

    // States that don't belong to a subcontroller.
    private boolean mAirplaneMode = false;
    private boolean mHasNoSims;
    private Locale mLocale = null;
    // This list holds our ordering.
    private List<SubscriptionInfo> mCurrentSubscriptions
            = new ArrayList<SubscriptionInfo>();

    // All the callbacks.
    private ArrayList<EmergencyListener> mEmergencyListeners = new ArrayList<EmergencyListener>();
    private ArrayList<CarrierLabelListener> mCarrierListeners =
            new ArrayList<CarrierLabelListener>();
    private ArrayList<SignalCluster> mSignalClusters = new ArrayList<SignalCluster>();
    private ArrayList<NetworkSignalChangedCallback> mSignalsChangedCallbacks =
            new ArrayList<NetworkSignalChangedCallback>();
    @VisibleForTesting
    boolean mListening;

    // The current user ID.
    private int mCurrentUserId;

    /// M: config for show the ! icon or not
    static final boolean mShowNormalIcon = true;

    /// M: Support "SystemUI - VoLTE icon".
    private boolean mVoLTEState = false;
    /// M: Support "SystemUI - Used VoLTE SubID".
    private int mUsedVoLTESubID;
    /// M: Support "SystemUI - VoLTE icon".
    private static int mIMSState;
    /// M: "SystemUI - VoLTE icon plug out".
    private static int mLastSIMNum = -100;
    /// M: Support "WFC support"
    private static boolean mWfcState;

    ///: M: Support for PLMN
    String[] mNetworkName;
    int mSlotCount = 0;

    /**
     * Construct this controller object and register for updates.
     */
    public NetworkControllerImpl(Context context) {
        this(context, (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE),
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE),
                (WifiManager) context.getSystemService(Context.WIFI_SERVICE),
                SubscriptionManager.from(context), Config.readConfig(context),
                new AccessPointControllerImpl(context), new MobileDataControllerImpl(context));
        registerListeners();
    }

    @VisibleForTesting
    NetworkControllerImpl(Context context, ConnectivityManager connectivityManager,
            TelephonyManager telephonyManager, WifiManager wifiManager,
            SubscriptionManager subManager, Config config,
            AccessPointControllerImpl accessPointController,
            MobileDataControllerImpl mobileDataController) {
        mContext = context;
        mConfig = config;

        mSubscriptionManager = subManager;
        mConnectivityManager = connectivityManager;
        mHasMobileDataFeature =
                mConnectivityManager.isNetworkSupported(ConnectivityManager.TYPE_MOBILE);

        /// M: config for show the ! icon or not . @{
        TelephonyIcons.initTelephonyIcon();
        WifiIcons.initWifiIcon();
        /// M: config for show the ! icon or not . @}

        /// M: Support "Operator plugin - Data activity/type, strength icon". @{
        mNetworkControllerExt = new NetworkControllerExt(mConfig);
        mSlotCount = SIMHelper.getSlotCount();
        mNetworkName = new String[mSlotCount];
        /// M: Support "Operator plugin - Data activity/type, strength icon". @}

        // telephony
        mPhone = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);

        // wifi
        mWifiManager = wifiManager;

        mLocale = mContext.getResources().getConfiguration().locale;
        mAccessPoints = accessPointController;
        mMobileDataController = mobileDataController;
        mMobileDataController.setNetworkController(this);
        // TODO: Find a way to move this into MobileDataController.
        mMobileDataController.setCallback(new MobileDataControllerImpl.Callback() {
            @Override
            public void onMobileDataEnabled(boolean enabled) {
                notifyMobileDataEnabled(enabled);
            }
        });
        mWifiSignalController = new WifiSignalController(mContext, mHasMobileDataFeature,
                mSignalsChangedCallbacks, mSignalClusters, this);

        // AIRPLANE_MODE_CHANGED is sent at boot; we've probably already missed it
        updateAirplaneMode(null, true /* force callback */);
        mAccessPoints.setNetworkController(this);
    }

    private void registerListeners() {
        for (MobileSignalController mobileSignalController : mMobileSignalControllers.values()) {
            mobileSignalController.registerListener();
        }
        mSubscriptionManager.addOnSubscriptionsChangedListener(mSubscriptionListener);

        // broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_DEFAULT_VOICE_SUBSCRIPTION_CHANGED);
        filter.addAction(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);
        filter.addAction(TelephonyIntents.ACTION_ANY_DEDICATE_DATA_CONNECTION_STATE_CHANGED);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION_IMMEDIATE);
        filter.addAction(ConnectivityManager.INET_CONDITION_ACTION);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(ACTION_IMS_CAP_CHANGE);
        /// M: Support "IMS events". @{
        /// M: Support "IMS events". @}
        /// M: Support "IPO". @{
        filter.addAction("android.intent.action.ACTION_PREBOOT_IPO");
        filter.addAction("android.intent.action.ACTION_SHUTDOWN_IPO");
        /// M: Support "IPO". @}

        /// M: Support "SystemUI - VoLTE icon".
        filter.addAction(TelephonyIntents.ACTION_IMS_STATE_CHANGED);
        /// M: Support "subinfo record update".
        filter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);

        mContext.registerReceiver(this, filter);
        mListening = true;

        /// M: Support "subinfo record update".
        updateMobileControllers(null);
    }

    private void unregisterListeners() {
        mListening = false;
        for (MobileSignalController mobileSignalController : mMobileSignalControllers.values()) {
            mobileSignalController.unregisterListener();
        }
        mSubscriptionManager.removeOnSubscriptionsChangedListener(mSubscriptionListener);
        mContext.unregisterReceiver(this);
    }

    public int getConnectedWifiLevel() {
        return mWifiSignalController.getState().level;
    }

    @Override
    public AccessPointController getAccessPointController() {
        return mAccessPoints;
    }

    @Override
    public MobileDataController getMobileDataController() {
        return mMobileDataController;
    }

    public void addEmergencyListener(EmergencyListener listener) {
        mEmergencyListeners.add(listener);
        listener.setEmergencyCallsOnly(isEmergencyOnly());
    }

    public void addCarrierLabel(CarrierLabelListener listener) {
        mCarrierListeners.add(listener);
        refreshCarrierLabel();
    }

    private void notifyMobileDataEnabled(boolean enabled) {
        final int length = mSignalsChangedCallbacks.size();
        for (int i = 0; i < length; i++) {
            mSignalsChangedCallbacks.get(i).onMobileDataEnabled(enabled);
        }
    }

    public boolean hasMobileDataFeature() {
        return mHasMobileDataFeature;
    }

    public boolean hasVoiceCallingFeature() {
        if (DEBUG) {
            Log.d(TAG, "mPhone.getPhoneType(): " + mPhone.getPhoneType());
        }
        return mPhone.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
    }

    private MobileSignalController getDataController() {
        int dataSubId = SubscriptionManager.getDefaultDataSubId();
        if (!SubscriptionManager.isValidSubscriptionId(dataSubId)) {
            if (DEBUG) Log.e(TAG, "No data sim selected");
            return mDefaultSignalController;
        }
        if (mMobileSignalControllers.containsKey(dataSubId)) {
            return mMobileSignalControllers.get(dataSubId);
        }
        if (DEBUG) Log.e(TAG, "Cannot find controller for data sub: " + dataSubId);
        return mDefaultSignalController;
    }

    public String getMobileNetworkName() {
        MobileSignalController controller = getDataController();
        return controller != null ? controller.getState().networkName : "";
    }

    public boolean isEmergencyOnly() {
        int voiceSubId = SubscriptionManager.getDefaultVoiceSubId();
        /// M: Use the existing Sub Id if only one SIM card @{
        if (mMobileSignalControllers.size() == 1 &&
            SubscriptionManager.getSlotId(voiceSubId) < 0) {
            if (DEBUG) {
                Log.e(TAG, "The VoiceSubId " + voiceSubId + " doesn't exist");
            }
            voiceSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        /// M: Use the existing Sub Id if only one SIM card @}

        if (!SubscriptionManager.isValidSubscriptionId(voiceSubId)) {
            for (MobileSignalController mobileSignalController :
                                            mMobileSignalControllers.values()) {
                if (!mobileSignalController.isEmergencyOnly()) {
                    return false;
                }
            }
        }
        if (mMobileSignalControllers.containsKey(voiceSubId)) {
            if (DEBUG) {
                Log.d(TAG, "Check isEmergencyOnly, voiceSubId: " + voiceSubId);
            }
            return mMobileSignalControllers.get(voiceSubId).isEmergencyOnly();
        }
        if (DEBUG) Log.e(TAG, "Cannot find controller for voice sub: " + voiceSubId);
        // Something is wrong, better assume we can't make calls...
        return true;
    }

    /**
     * Emergency status may have changed (triggered by MobileSignalController),
     * so we should recheck and send out the state to listeners.
     */
    void recalculateEmergency() {
        final boolean emergencyOnly = isEmergencyOnly();
        if (DEBUG) {
            Log.d(TAG, "recalculateEmergency: " + emergencyOnly);
        }
        final int length = mEmergencyListeners.size();
        for (int i = 0; i < length; i++) {
            mEmergencyListeners.get(i).setEmergencyCallsOnly(emergencyOnly);
        }
        // If the emergency has a chance to change, then so does the carrier
        // label.
        refreshCarrierLabel();
    }

    public void addSignalCluster(SignalCluster cluster) {
        mSignalClusters.add(cluster);
        cluster.setSubs(mCurrentSubscriptions);
        cluster.setIsAirplaneMode(mAirplaneMode, TelephonyIcons.FLIGHT_MODE_ICON,
                R.string.accessibility_airplane_mode);
        cluster.setNoSims(mHasNoSims);
        mWifiSignalController.notifyListeners();
        for (MobileSignalController mobileSignalController : mMobileSignalControllers.values()) {
            mobileSignalController.notifyListeners();
        }
    }

    public void addNetworkSignalChangedCallback(NetworkSignalChangedCallback cb) {
        mSignalsChangedCallbacks.add(cb);
        cb.onAirplaneModeChanged(mAirplaneMode);
        cb.onNoSimVisibleChanged(mHasNoSims);
        mWifiSignalController.notifyListeners();
        for (MobileSignalController mobileSignalController : mMobileSignalControllers.values()) {
            mobileSignalController.notifyListeners();
        }
    }

    public void removeNetworkSignalChangedCallback(NetworkSignalChangedCallback cb) {
        mSignalsChangedCallbacks.remove(cb);
    }

    @Override
    public void setWifiEnabled(final boolean enabled) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... args) {
                // Disable tethering if enabling Wifi
                final int wifiApState = mWifiManager.getWifiApState();
                if (enabled && ((wifiApState == WifiManager.WIFI_AP_STATE_ENABLING) ||
                        (wifiApState == WifiManager.WIFI_AP_STATE_ENABLED))) {
                    mWifiManager.setWifiApEnabled(null, false);
                }

                mWifiManager.setWifiEnabled(enabled);
                return null;
            }
        }.execute();
    }

    @Override
    public void onUserSwitched(int newUserId) {
        mCurrentUserId = newUserId;
        mAccessPoints.onUserSwitched(newUserId);
        updateConnectivity();
        refreshCarrierLabel();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (CHATTY) {
            Log.d(TAG, "onReceive: intent=" + intent);
        }
        final String action = intent.getAction();
        if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION_IMMEDIATE) ||
                action.equals(ConnectivityManager.INET_CONDITION_ACTION)) {
            updateConnectivity();
            refreshCarrierLabel();
        } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
            mConfig = Config.readConfig(mContext);
            handleConfigurationChanged();
        } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
            refreshLocale();
            updateAirplaneMode(intent, false);
            refreshCarrierLabel();
        } else if (action.equals(TelephonyIntents.ACTION_DEFAULT_VOICE_SUBSCRIPTION_CHANGED)) {
            // We are using different subs now, we might be able to make calls.
            recalculateEmergency();
        } else if (action.equals(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)) {
            // Notify every MobileSignalController so they can know whether they are the
            // data sim or not.
            for (MobileSignalController controller : mMobileSignalControllers.values()) {
                controller.handleBroadcast(intent);
            }
        } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
            // Might have different subscriptions now.
            /// M: Support "subinfo record update".
            updateMobileControllers(null);
            /// M: [ALPS01967373] set EmergencyCallsOnly text @{
            recalculateEmergency();
            /// M: [ALPS01967373] set EmergencyCallsOnly text @}
            /// M: Support "subinfo record update". @{
        } else if (action.equals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED)) {
            updateMobileControllers(intent);
            /// M: Support "subinfo record update". @}
        } else if (action.equals("android.intent.action.ACTION_PREBOOT_IPO")) {
            /// M: Support "IPO". @{
            refreshLocale();
            updateAirplaneMode(null, false);
        } else if (action.equals("android.intent.action.ACTION_SHUTDOWN_IPO")) {
            for (MobileSignalController controller : mMobileSignalControllers.values()) {
                controller.handleBroadcast(intent);
            }

            /// M: Force clear network information after IPO shutdown @{
            Log.d(TAG, "Force clear network information after IPO shutdown");
            modifyVoLTE();
            List<SubscriptionInfo> subscriptions = Collections.emptyList();
            setCurrentSubscriptions(subscriptions);
            updateNoSims();
            /// M: Force clear network information after IPO shutdown @}

            /// M: Support "IPO". @}
        /// M: Support "SystemUI - VoLTE icon". @{
        } else if (action.equals(TelephonyIntents.ACTION_IMS_STATE_CHANGED)) {
            // TelephonyIntents.ACTION_IMS_STATE_CHANGED = "android.intent.action.IMS_SERVICE_STATE"
            // TelephonyIntents.EXTRA_IMS_REG_STATE_KEY = "regState"
            int reg = intent.getIntExtra(EXTRA_IMS_REG_STATE_KEY, -1);
            // PhoneConstants.SUBSCRIPTION_KEY = "subscription"
            int phoneId = intent.getIntExtra(EXTRA_PHONE_ID, -1 );
            int[] subIds = SubscriptionManager.getSubId(phoneId);
            int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            if (subIds != null && subIds.length > 0) {
                subId = subIds[0];
            }
            /// M: Support "SystemUI - Used VoLTE SubID".
            mUsedVoLTESubID = subId;
            Xlog.d(TAG, "onReceive from TelephonyIntents.ACTION_IMS_STATE_CHANGED: reg=" + reg + ", subId=" + subId);
            if (reg == -1) {
                return;
            }
            if (SubscriptionManager.isValidSubscriptionId(subId)) {
                if (mMobileSignalControllers.containsKey(subId)) {
                    mMobileSignalControllers.get(subId).setIMSState(reg);
                }
            }
            /// M: Support "SystemUI - IMS Capability". @{
        } else if (action.equals(ACTION_IMS_CAP_CHANGE)) {
            int phoneId = intent.getIntExtra(EXTRA_PHONE_ID, -1);
            int[] subIds = SubscriptionManager.getSubId(phoneId);
            int[] enabledFeatures = intent.getIntArrayExtra(EXTRA_IMS_ENABLE_CAP_KEY);            
            int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            if (subIds != null && subIds.length > 0) {
                subId = subIds[0];
            }
            Xlog.d(TAG, "onReceive IMS_CAPABILITIES_CHANGE subId=" + subId);
            if (SubscriptionManager.isValidSubscriptionId(subId)) {
                if (mMobileSignalControllers.containsKey(subId)) {
                    boolean wfcCapabilities = (enabledFeatures[2] == 2);
                    mMobileSignalControllers.get(subId).setWfcState(wfcCapabilities);
                }
            }
        /// M: Support "SystemUI - IMS Capability". @}
        /// M: Support "SystemUI - VoLTE icon". @}
        ///M: Support APIRAT SVLTE.@{
        } else if (action.equals(SvLteController.ACTION_IRAT_PS_TYPE_CHANGED)) {
            final int psType = intent.getIntExtra(SvLteController.EXTRA_PS_TYPE,
                    SvLteController.PS_SERVICE_UNKNOWN);
            Xlog.d(TAG, "received broadcast, action = " + action + " psType = " + psType);
            if (psType != SvLteController.PS_SERVICE_UNKNOWN) {
                for (MobileSignalController controller : mMobileSignalControllers.values()) {
                    final int slotId = controller.mSubscriptionInfo.getSimSlotIndex();
                    if (SvLteController.isMediatekSVLteDcSupport(slotId)) {
                        controller.mSvLteController.setPsType(psType);
                        controller.updateNetworkType();
                        controller.updateTelephony();
                    }
                }
            }
        ///M: Support APIRAT SVLTE.@}
        } else {
            int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            if (SubscriptionManager.isValidSubscriptionId(subId)) {
                if (mMobileSignalControllers.containsKey(subId)) {
                    mMobileSignalControllers.get(subId).handleBroadcast(intent);
                } else {
                    if (DEBUG) {
                        Log.d(TAG, "The controller of the sub Id " + subId + " not found");
                    }

                    // Can't find this subscription...  We must be out of date.
                    /// M: Support "subinfo record update".
                    updateMobileControllers(null);

                    /// M: Use updated subinfo @{
                    if (mMobileSignalControllers.containsKey(subId)) {
                        mMobileSignalControllers.get(subId).handleBroadcast(intent);
                    }
                    /// M: Use updated subinfo @}
                }
            } else {
                // No sub id, must be for the wifi.
                mWifiSignalController.handleBroadcast(intent);
            }
        }
    }

    @VisibleForTesting
    void handleConfigurationChanged() {
        for (MobileSignalController mobileSignalController : mMobileSignalControllers.values()) {
            mobileSignalController.setConfiguration(mConfig);
        }
        refreshLocale();
        refreshCarrierLabel();
    }

    /// M: "SystemUI - VoLTE icon plug out". @{
    private void modifyVoLTE() {
        int slotID = SubscriptionManager.getSlotId(mUsedVoLTESubID);
        Log.d(TAG, "[modifyVoLTE] mUsedVoLTESubID: " + mUsedVoLTESubID + ", slotID: " + slotID);
        if (slotID < 0) { // slot id < 0 means this SIM is not inserted
            Log.d(TAG, " mUsedVoLTESubID: " + mUsedVoLTESubID + " have been removed");
            if (SubscriptionManager.isValidSubscriptionId(mUsedVoLTESubID)) {
                if (mMobileSignalControllers.containsKey(mUsedVoLTESubID)) {
                    mMobileSignalControllers.get(mUsedVoLTESubID).setIMSState(IMS_UNREGISTERED);
                }
            }
        }
    }
    /// M: "SystemUI - VoLTE icon plug out". @}

    private void updateMobileControllers(Intent intent) {
        /// M: SIMHelper update Active SubscriptionInfo
        SIMHelper.updateSIMInfos(mContext);

        /// M: Support "subinfo record update".
        int detectedType = SubscriptionManager.EXTRA_VALUE_NOCHANGE;
        if (!mListening) {
            if (DEBUG) {
                Log.d(TAG, "updateMobileControllers: it's not listening");
            }
            return;
        }
        List<SubscriptionInfo> subscriptions = mSubscriptionManager.getActiveSubscriptionInfoList();
        if (subscriptions == null) {
            subscriptions = Collections.emptyList();
        }

        /// M: "SystemUI - VoLTE icon plug out".
        modifyVoLTE();

        /// M: Support "subinfo record update". @{
        if (intent != null) {
            detectedType = intent.getIntExtra(SubscriptionManager.INTENT_KEY_DETECT_STATUS, 0);
            Log.d(TAG, "updateMobileControllers detectedType: " + detectedType);
        }
        /// M: Support "subinfo record update". @}
        /// M: Support "subinfo record update".
        // If there have been no relevant changes to any of the subscriptions, we can leave as is.
        if (hasCorrectMobileControllers(subscriptions) &&
            detectedType != SubscriptionManager.EXTRA_VALUE_REPOSITION_SIM) {
            // Even if the controllers are correct, make sure we have the right no sims state.
            // Such as on boot, don't need any controllers, because there are no sims,
            // but we still need to update the no sim state.
            updateNoSims();
            return;
        }
        setCurrentSubscriptions(subscriptions);
        updateNoSims();
    }

    @VisibleForTesting
    protected void updateNoSims() {
        boolean hasNoSims = mHasMobileDataFeature && mMobileSignalControllers.size() == 0;
        if (DEBUG) {
            Log.d(TAG, "updateNoSims: mMobileSignalControllers.size() = "
            + mMobileSignalControllers.size());
        }
        if (hasNoSims != mHasNoSims) {
            Log.d(TAG, "updateNoSims hasNoSims != mHasNoSims");
            mHasNoSims = hasNoSims;
            notifyListeners();
        }
    }

    @VisibleForTesting
    void setCurrentSubscriptions(List<SubscriptionInfo> subscriptions) {
        if (DEBUG) {
            Log.d(TAG, "setCurrentSubscriptions: " + subscriptions);
        }

        Collections.sort(subscriptions, new Comparator<SubscriptionInfo>() {
            @Override
            public int compare(SubscriptionInfo lhs, SubscriptionInfo rhs) {
                return lhs.getSimSlotIndex() == rhs.getSimSlotIndex()
                        ? lhs.getSubscriptionId() - rhs.getSubscriptionId()
                        : lhs.getSimSlotIndex() - rhs.getSimSlotIndex();
            }
        });
        final int length = mSignalClusters.size();
        for (int i = 0; i < length; i++) {
            mSignalClusters.get(i).setSubs(subscriptions);
        }
        mCurrentSubscriptions = subscriptions;

        HashMap<Integer, MobileSignalController> cachedControllers =
                new HashMap<Integer, MobileSignalController>(mMobileSignalControllers);
        mMobileSignalControllers.clear();
        if (DEBUG) {
            Log.d(TAG, "setCurrentSubscriptions: mMobileSignalControllers.clear()");
        }

        /// M: [ALPS01967373] set EmergencyCallsOnly text @{
        final int emergencyLength = mEmergencyListeners.size();
        for (int i = 0; i < emergencyLength; i++) {
            mEmergencyListeners.get(i).setEmergencyCallsOnly(false);
        }
        /// M: [ALPS01967373] set EmergencyCallsOnly text @}

        final int num = subscriptions.size();
        for (int i = 0; i < num; i++) {
            int subId = subscriptions.get(i).getSubscriptionId();
            // If we have a copy of this controller already reuse it, otherwise make a new one.
            if (cachedControllers.containsKey(subId)) {
                mMobileSignalControllers.put(subId, cachedControllers.remove(subId));
            } else {
                MobileSignalController controller = new MobileSignalController(mContext, mConfig,
                        mHasMobileDataFeature, mPhone, mSignalsChangedCallbacks, mSignalClusters,
                        this, subscriptions.get(i));
                mMobileSignalControllers.put(subId, controller);
                if (subscriptions.get(i).getSimSlotIndex() == 0) {
                    mDefaultSignalController = controller;
                }
                if (mListening) {
                    controller.registerListener();
                }
            }
        }
        if (mListening) {
            for (Integer key : cachedControllers.keySet()) {
                if (cachedControllers.get(key) == mDefaultSignalController) {
                    mDefaultSignalController = null;
                }
                cachedControllers.get(key).unregisterListener();
            }
            if (DEBUG) {
               Log.d(TAG, "setCurrentSubscriptions: cachedControllers.get(key).unregisterListener()");
            }
        }
        /// M: [ALPS01794897] refresh view when subinfo change. @{
        for (MobileSignalController controller : mMobileSignalControllers.values()) {
            controller.setInvalid(true);
        }
        /// M: [ALPS01794897] refresh view when subinfo change. @}
        // There may be new MobileSignalControllers around, make sure they get the current
        // inet condition and airplane mode.
        pushConnectivityToSignals();
        updateAirplaneMode(null, true /* force */);
    }

    @VisibleForTesting
    boolean hasCorrectMobileControllers(List<SubscriptionInfo> allSubscriptions) {
        if (allSubscriptions.size() != mMobileSignalControllers.size()) {
            if (DEBUG) {
                Log.d(TAG, "hasCorrectMobileControllers: check  : " + allSubscriptions);
                Log.d(TAG, "hasCorrectMobileControllers: current: " + mMobileSignalControllers);
            }
            return false;
        }
        for (SubscriptionInfo info : allSubscriptions) {
            if (!mMobileSignalControllers.containsKey(info.getSubscriptionId())) {
                if (DEBUG) {
                    Log.d(TAG, "hasCorrectMobileControllers: can not found "
                        + info + " in " + mMobileSignalControllers);
                }
                return false;
            }
        }
        return true;
    }
    private void updateAirplaneMode(Intent intent, boolean force) {
        boolean airplaneMode = false;

        /// M: [ALPS01794897] Get the airplane mode from intent to avoid DB delay.
        if (intent != null) {
            airplaneMode = intent.getBooleanExtra("state", false);
            Xlog.d(TAG, "updateAirplaneMode: intent state= " + airplaneMode);
        } else {
            airplaneMode = (Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) == 1);
        }
        if (airplaneMode != mAirplaneMode || force) {
            mAirplaneMode = airplaneMode;
            for (MobileSignalController mobileSignalController : mMobileSignalControllers.values()) {
                mobileSignalController.setAirplaneMode(mAirplaneMode);
            }
            if (DEBUG) {
                Log.d(TAG, "updateAirplaneMode: notifyListeners");
            }
            notifyListeners();
            refreshCarrierLabel();
        }
    }

    private void refreshLocale() {
        Locale current = mContext.getResources().getConfiguration().locale;
        if (!current.equals(mLocale)) {
            mLocale = current;
            notifyAllListeners();
        }
    }

    /**
     * Forces update of all callbacks on both SignalClusters and
     * NetworkSignalChangedCallbacks.
     */
    private void notifyAllListeners() {
        notifyListeners();
        for (MobileSignalController mobileSignalController : mMobileSignalControllers.values()) {
            mobileSignalController.notifyListeners();
        }
        mWifiSignalController.notifyListeners();
    }

    /**
     * Notifies listeners of changes in state of to the NetworkController, but
     * does not notify for any info on SignalControllers, for that call
     * notifyAllListeners.
     */
    private void notifyListeners() {
        int length = mSignalClusters.size();
        if (DEBUG) {
            Log.d(TAG, "notifyListeners: mSignalClusters.size() = " + length);
        }
        for (int i = 0; i < length; i++) {
            mSignalClusters.get(i).setIsAirplaneMode(mAirplaneMode, TelephonyIcons.FLIGHT_MODE_ICON,
                    R.string.accessibility_airplane_mode);
            mSignalClusters.get(i).setNoSims(mHasNoSims);
        }
        int signalsChangedLength = mSignalsChangedCallbacks.size();
        for (int i = 0; i < signalsChangedLength; i++) {
            mSignalsChangedCallbacks.get(i).onAirplaneModeChanged(mAirplaneMode);
            mSignalsChangedCallbacks.get(i).onNoSimVisibleChanged(mHasNoSims);
        }
    }

    /**
     * Update the Inet conditions and what network we are connected to.
     */
    private void updateConnectivity() {
        mConnectedTransports.clear();
        mValidatedTransports.clear();
        for (NetworkCapabilities nc :
                mConnectivityManager.getDefaultNetworkCapabilitiesForUser(mCurrentUserId)) {
            for (int transportType : nc.getTransportTypes()) {
                mConnectedTransports.set(transportType);
                if (nc.hasCapability(NET_CAPABILITY_VALIDATED)) {
                    mValidatedTransports.set(transportType);
                }
            }
        }

        if (CHATTY) {
            Log.d(TAG, "updateConnectivity: mConnectedTransports=" + mConnectedTransports);
            Log.d(TAG, "updateConnectivity: mValidatedTransports=" + mValidatedTransports);
        }

        mConnected = !mConnectedTransports.isEmpty();
        mInetCondition = !mValidatedTransports.isEmpty();
        mBluetoothTethered = mConnectedTransports.get(TRANSPORT_BLUETOOTH);
        mEthernetConnected = mConnectedTransports.get(TRANSPORT_ETHERNET);

        pushConnectivityToSignals();
    }

    /**
     * Pushes the current connectivity state to all SignalControllers.
     */
    private void pushConnectivityToSignals() {
        // We want to update all the icons, all at once, for any condition change
        for (MobileSignalController mobileSignalController : mMobileSignalControllers.values()) {
            mobileSignalController.setInetCondition(
                    mInetCondition ? 1 : 0,
                    mValidatedTransports.get(mobileSignalController.getTransportType()) ? 1 : 0);
        }
        mWifiSignalController.setInetCondition(
                mValidatedTransports.get(mWifiSignalController.getTransportType()) ? 1 : 0);
    }

    /**
     * Recalculate and update the carrier label.
     */
    void refreshCarrierLabel() {
        Context context = mContext;

        WifiSignalController.WifiState wifiState = mWifiSignalController.getState();
        String label = "";
        for (MobileSignalController controller : mMobileSignalControllers.values()) {
            label = controller.getLabel(label, mConnected, mHasMobileDataFeature);
        }

        // TODO Simplify this ugliness, some of the flows below shouldn't be possible anymore
        // but stay for the sake of history.
        if (mBluetoothTethered && !mHasMobileDataFeature) {
            label = mContext.getString(R.string.bluetooth_tethered);
        }

        if (mEthernetConnected && !mHasMobileDataFeature) {
            label = context.getString(R.string.ethernet_label);
        }

        if (mAirplaneMode && !isEmergencyOnly()) {
            // combined values from connected wifi take precedence over airplane mode
            if (wifiState.connected && mHasMobileDataFeature) {
                // Suppress "No internet connection." from mobile if wifi connected.
                label = "";
            } else {
                 if (!mHasMobileDataFeature) {
                      label = context.getString(
                              R.string.status_bar_settings_signal_meter_disconnected);
                 }
            }
        } else if (!isMobileDataConnected() && !wifiState.connected && !mBluetoothTethered &&
                 !mEthernetConnected && !mHasMobileDataFeature) {
            // Pretty much no connection.
            label = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
        }

        // for mobile devices, we always show mobile connection info here (SPN/PLMN)
        // for other devices, we show whatever network is connected
        // This is determined above by references to mHasMobileDataFeature.
        int length = mCarrierListeners.size();
        for (int i = 0; i < length; i++) {
            mCarrierListeners.get(i).setCarrierLabel(label);
        }

        /// M: Support "Operator plugin - Customize Carrier Label for PLMN". @{
        for (int i = 0; i < mSlotCount; i++) {
            boolean found = false;
            for (Map.Entry<Integer, MobileSignalController> entry : mMobileSignalControllers
                    .entrySet()) {
                int subId = entry.getKey();
                MobileSignalController controller = entry.getValue();

                int slotId = controller.mSubscriptionInfo.getSimSlotIndex();
                // int slotId = SubscriptionManager.getSlotId(subId);

                if (i == slotId) {
                    mNetworkName[slotId] = controller.mCurrentState.networkName;
                    PluginFactory.getStatusBarPlmnPlugin(context).updateCarrierLabel(i, true,
                            controller.hasService(), mNetworkName);
                    found = true;
                    break;
                }
            }
            // not have sub
            if (!found) {
                mNetworkName[i] = mContext
                        .getString(com.android.internal.R.string.lockscreen_carrier_default);
                PluginFactory.getStatusBarPlmnPlugin(context).updateCarrierLabel(i, false, false,
                        mNetworkName);
            }
        }
        /// M: Support "Operator plugin - Customize Carrier Label for PLMN". @}
    }

    private boolean isMobileDataConnected() {
        MobileSignalController controller = getDataController();
        return controller != null ? controller.getState().dataConnected : false;
    }

    /// M: Support "SystemUI - VoLTE icon". @{
    boolean getVoLTEState() {
        Xlog.d(TAG, "mVoLTEState=" + mVoLTEState);
        return mVoLTEState;
    }

    void setVoLTEState(boolean volteState) {
        Xlog.d(TAG, "old VoLTEState=" + mVoLTEState + ", new VoLTEState=" + volteState);
        mVoLTEState = volteState;
    }
    /// M: Support "SystemUI - VoLTE icon". @}
    /// M: Support "SystemUI - Used VoLTE SubID". @{
    int getVoLTESubID() {
        return mUsedVoLTESubID;
    }
    /// M: Support "SystemUI - Used VoLTE SubID". @}

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("NetworkController state:");

        pw.println("  - telephony ------");
        pw.print("  hasVoiceCallingFeature()=");
        pw.println(hasVoiceCallingFeature());

        pw.println("  - Bluetooth ----");
        pw.print("  mBtReverseTethered=");
        pw.println(mBluetoothTethered);

        pw.println("  - connectivity ------");
        pw.print("  mConnectedTransports=");
        pw.println(mConnectedTransports);
        pw.print("  mValidatedTransports=");
        pw.println(mValidatedTransports);
        pw.print("  mInetCondition=");
        pw.println(mInetCondition);
        pw.print("  mAirplaneMode=");
        pw.println(mAirplaneMode);
        pw.print("  mLocale=");
        pw.println(mLocale);

        for (MobileSignalController mobileSignalController : mMobileSignalControllers.values()) {
            mobileSignalController.dump(pw);
        }
        mWifiSignalController.dump(pw);
    }

    private boolean mDemoMode;
    private int mDemoInetCondition;
    private WifiSignalController.WifiState mDemoWifiState;

    @Override
    public void dispatchDemoCommand(String command, Bundle args) {
        if (!mDemoMode && command.equals(COMMAND_ENTER)) {
            if (DEBUG) Log.d(TAG, "Entering demo mode");
            unregisterListeners();
            mDemoMode = true;
            mDemoInetCondition = mInetCondition ? 1 : 0;
            mDemoWifiState = mWifiSignalController.getState();
        } else if (mDemoMode && command.equals(COMMAND_EXIT)) {
            if (DEBUG) Log.d(TAG, "Exiting demo mode");
            mDemoMode = false;
            // Update what MobileSignalControllers, because they may change
            // to set the number of sim slots.
            /// M: Support "subinfo record update".
            updateMobileControllers(null);
            for (MobileSignalController controller : mMobileSignalControllers.values()) {
                controller.resetLastState();
            }
            mWifiSignalController.resetLastState();
            registerListeners();
            notifyAllListeners();
            refreshCarrierLabel();
        } else if (mDemoMode && command.equals(COMMAND_NETWORK)) {
            String airplane = args.getString("airplane");
            if (airplane != null) {
                boolean show = airplane.equals("show");
                int length = mSignalClusters.size();
                for (int i = 0; i < length; i++) {
                    mSignalClusters.get(i).setIsAirplaneMode(show, TelephonyIcons.FLIGHT_MODE_ICON,
                            R.string.accessibility_airplane_mode);
                }
            }
            String fully = args.getString("fully");
            if (fully != null) {
                mDemoInetCondition = Boolean.parseBoolean(fully) ? 1 : 0;
                mWifiSignalController.setInetCondition(mDemoInetCondition);
                for (MobileSignalController controller : mMobileSignalControllers.values()) {
                    controller.setInetCondition(mDemoInetCondition, mDemoInetCondition);
                }
            }
            String wifi = args.getString("wifi");
            if (wifi != null) {
                boolean show = wifi.equals("show");
                String level = args.getString("level");
                if (level != null) {
                    mDemoWifiState.level = level.equals("null") ? -1
                            : Math.min(Integer.parseInt(level), WifiIcons.WIFI_LEVEL_COUNT - 1);
                    mDemoWifiState.connected = mDemoWifiState.level >= 0;
                }
                mDemoWifiState.enabled = show;
                mWifiSignalController.notifyListeners();
            }
            String sims = args.getString("sims");
            if (sims != null) {
                int num = Integer.parseInt(sims);
                List<SubscriptionInfo> subs = new ArrayList<SubscriptionInfo>();
                if (num != mMobileSignalControllers.size()) {
                    mMobileSignalControllers.clear();
                    int start = mSubscriptionManager.getActiveSubscriptionInfoCountMax();
                    for (int i = start /* get out of normal index range */; i < start + num; i++) {
                        SubscriptionInfo info = new SubscriptionInfo(i, "", i, "", "", 0, 0, "", 0,
                                null, 0, 0, "");
                        subs.add(info);
                        mMobileSignalControllers.put(i, new MobileSignalController(mContext,
                                mConfig, mHasMobileDataFeature, mPhone, mSignalsChangedCallbacks,
                                mSignalClusters, this, info));
                    }
                }
                final int n = mSignalClusters.size();
                for (int i = 0; i < n; i++) {
                    mSignalClusters.get(i).setSubs(subs);
                }
            }
            String nosim = args.getString("nosim");
            if (nosim != null) {
                boolean show = nosim.equals("show");
                final int n = mSignalClusters.size();
                for (int i = 0; i < n; i++) {
                    mSignalClusters.get(i).setNoSims(show);
                }
            }
            String mobile = args.getString("mobile");
            if (mobile != null) {
                boolean show = mobile.equals("show");
                String datatype = args.getString("datatype");
                String slotString = args.getString("slot");
                int slot = TextUtils.isEmpty(slotString) ? 0 : Integer.parseInt(slotString);
                // Hack to index linearly for easy use.
                MobileSignalController controller = mMobileSignalControllers
                        .values().toArray(new MobileSignalController[0])[slot];
                controller.getState().dataSim = datatype != null;
                if (datatype != null) {
                    controller.getState().iconGroup =
                            datatype.equals("1x") ? TelephonyIcons.ONE_X :
                            datatype.equals("3g") ? TelephonyIcons.THREE_G :
                            datatype.equals("4g") ? TelephonyIcons.FOUR_G :
                            datatype.equals("e") ? TelephonyIcons.E :
                            datatype.equals("g") ? TelephonyIcons.G :
                            datatype.equals("h") ? TelephonyIcons.H :
                            datatype.equals("lte") ? TelephonyIcons.LTE :
                            datatype.equals("roam") ? TelephonyIcons.ROAMING :
                            TelephonyIcons.UNKNOWN;
                }
                int[][] icons = TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH;
                String level = args.getString("level");
                if (level != null) {
                    controller.getState().level = level.equals("null") ? -1
                            : Math.min(Integer.parseInt(level), icons[0].length - 1);
                    controller.getState().connected = controller.getState().level >= 0;
                }
                controller.getState().enabled = show;
                controller.notifyListeners();
            }
            refreshCarrierLabel();
        }
    }

    private final OnSubscriptionsChangedListener mSubscriptionListener =
            new OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            if (DEBUG) {
                Log.d(TAG, " onSubscriptionsChanged");
            }
            /// M: Support "subinfo record update".
            updateMobileControllers(null);
        };
    };

    // TODO: Move to its own file.
    static class WifiSignalController extends
            SignalController<WifiSignalController.WifiState, SignalController.IconGroup> {
        private final WifiManager mWifiManager;
        private final AsyncChannel mWifiChannel;
        private final boolean mHasMobileData;

        /// M: Wifi activity icon
        private int mWifiActivity;

        public WifiSignalController(Context context, boolean hasMobileData,
                List<NetworkSignalChangedCallback> signalCallbacks,
                List<SignalCluster> signalClusters, NetworkControllerImpl networkController) {
            super("WifiSignalController", context, NetworkCapabilities.TRANSPORT_WIFI,
                    signalCallbacks, signalClusters, networkController);
            mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            mHasMobileData = hasMobileData;
            Handler handler = new WifiHandler();
            mWifiChannel = new AsyncChannel();
            Messenger wifiMessenger = mWifiManager.getWifiServiceMessenger();
            if (wifiMessenger != null) {
                mWifiChannel.connect(context, handler, wifiMessenger);
            }
            // WiFi only has one state.
            mCurrentState.iconGroup = mLastState.iconGroup = new IconGroup(
                    "Wi-Fi Icons",
                    WifiIcons.WIFI_SIGNAL_STRENGTH,
                    WifiIcons.QS_WIFI_SIGNAL_STRENGTH,
                    AccessibilityContentDescriptions.WIFI_CONNECTION_STRENGTH,
                    WifiIcons.WIFI_NO_NETWORK,
                    WifiIcons.QS_WIFI_NO_NETWORK,
                    WifiIcons.WIFI_NO_NETWORK,
                    WifiIcons.QS_WIFI_NO_NETWORK,
                    AccessibilityContentDescriptions.WIFI_NO_CONNECTION
                    );

            /// M: Wifi activity icon
            mWifiActivity = WifiManager.DATA_ACTIVITY_NONE;
        }

        @Override
        protected WifiState cleanState() {
            return new WifiState();
        }

        @Override
        public void notifyListeners() {
            // only show wifi in the cluster if connected or if wifi-only
            boolean wifiVisible = mCurrentState.enabled
                    && (mCurrentState.connected || !mHasMobileData);
            String wifiDesc = wifiVisible ? mCurrentState.ssid : null;
            boolean ssidPresent = wifiVisible && mCurrentState.ssid != null;
            String contentDescription = getStringIfExists(getContentDescription());
            int length = mSignalsChangedCallbacks.size();
            for (int i = 0; i < length; i++) {
                mSignalsChangedCallbacks.get(i).onWifiSignalChanged(mCurrentState.enabled,
                        mCurrentState.connected, getQsCurrentIconId(),
                        ssidPresent && mCurrentState.activityIn,
                        ssidPresent && mCurrentState.activityOut, contentDescription, wifiDesc);
            }

            int signalClustersLength = mSignalClusters.size();
            for (int i = 0; i < signalClustersLength; i++) {
                /// M: Wifi activity icon @{
                Xlog.d(mTag, "Wifi notifyListeners: level= " + mCurrentState.level + ", wifiActivity= " + mWifiActivity);
                if (mCurrentState.activityIn != false || mCurrentState.activityOut != false) {
                    mSignalClusters.get(i).setWifiIndicators(wifiVisible, WifiIcons.WIFI_SIGNAL_STRENGTH_INOUT[mCurrentState.level][mWifiActivity],
                            contentDescription);
                } else {
                /// M: Wifi activity icon @}
                    mSignalClusters.get(i).setWifiIndicators(wifiVisible, getCurrentIconId(),
                            contentDescription);
                }
            }
        }

        /**
         * Extract wifi state directly from broadcasts about changes in wifi state.
         */
        public void handleBroadcast(Intent intent) {
            String action = intent.getAction();
            if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                mCurrentState.enabled = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN) == WifiManager.WIFI_STATE_ENABLED;
            } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                final NetworkInfo networkInfo = (NetworkInfo)
                        intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                mCurrentState.connected = networkInfo != null && networkInfo.isConnected();
                // If Connected grab the signal strength and ssid.
                if (mCurrentState.connected) {
                    // try getting it out of the intent first
                    WifiInfo info = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO) != null
                            ? (WifiInfo) intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO)
                            : mWifiManager.getConnectionInfo();
                    if (info != null) {
                        mCurrentState.ssid = getSsid(info);
                    } else {
                        mCurrentState.ssid = null;
                    }
                } else if (!mCurrentState.connected) {
                    mCurrentState.ssid = null;
                }
            } else if (action.equals(WifiManager.RSSI_CHANGED_ACTION)) {
                // Default to -200 as its below WifiManager.MIN_RSSI.
                mCurrentState.rssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, -200);
                mCurrentState.level = WifiManager.calculateSignalLevel(
                        mCurrentState.rssi, WifiIcons.WIFI_LEVEL_COUNT);
            }

            notifyListenersIfNecessary();
        }

        private String getSsid(WifiInfo info) {
            String ssid = info.getSSID();
            if (ssid != null) {
                return ssid;
            }
            // OK, it's not in the connectionInfo; we have to go hunting for it
            List<WifiConfiguration> networks = mWifiManager.getConfiguredNetworks();
            int length = networks.size();
            for (int i = 0; i < length; i++) {
                if (networks.get(i).networkId == info.getNetworkId()) {
                    return networks.get(i).SSID;
                }
            }
            return null;
        }

        @VisibleForTesting
        void setActivity(int wifiActivity) {
            /// M: Wifi activity icon @{
            Xlog.d(mTag, "setActivity: wifiActivity= " + wifiActivity);
            mWifiActivity = wifiActivity;
            /// M: Wifi activity icon @}

            mCurrentState.activityIn = wifiActivity == WifiManager.DATA_ACTIVITY_INOUT
                    || wifiActivity == WifiManager.DATA_ACTIVITY_IN;
            mCurrentState.activityOut = wifiActivity == WifiManager.DATA_ACTIVITY_INOUT
                    || wifiActivity == WifiManager.DATA_ACTIVITY_OUT;
            notifyListenersIfNecessary();
        }

        /**
         * Handler to receive the data activity on wifi.
         */
        class WifiHandler extends Handler {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case AsyncChannel.CMD_CHANNEL_HALF_CONNECTED:
                        if (msg.arg1 == AsyncChannel.STATUS_SUCCESSFUL) {
                            mWifiChannel.sendMessage(Message.obtain(this,
                                    AsyncChannel.CMD_CHANNEL_FULL_CONNECTION));
                        } else {
                            Log.e(mTag, "Failed to connect to wifi");
                        }
                        break;
                    case WifiManager.DATA_ACTIVITY_NOTIFICATION:
                        setActivity(msg.arg1);
                        break;
                    default:
                        // Ignore
                        break;
                }
            }
        }

        static class WifiState extends SignalController.State {
            String ssid;

            @Override
            public void copyFrom(State s) {
                super.copyFrom(s);
                WifiState state = (WifiState) s;
                ssid = state.ssid;
            }

            @Override
            protected void toString(StringBuilder builder) {
                super.toString(builder);
                builder.append(',').append("ssid=").append(ssid);
            }

            @Override
            public boolean equals(Object o) {
                return super.equals(o)
                        && Objects.equals(((WifiState) o).ssid, ssid);
            }
        }
    }

    // TODO: Move to its own file.
    public static class MobileSignalController extends SignalController<
            MobileSignalController.MobileState, MobileSignalController.MobileIconGroup> {
        private final TelephonyManager mPhone;
        private final String mNetworkNameDefault;
        private final String mNetworkNameSeparator;
        @VisibleForTesting
        final PhoneStateListener mPhoneStateListener;
        // Save entire info for logging, we only use the id.
        private final SubscriptionInfo mSubscriptionInfo;

        // @VisibleForDemoMode
        final SparseArray<MobileIconGroup> mNetworkToIconLookup;

        // Since some pieces of the phone state are interdependent we store it locally,
        // this could potentially become part of MobileState for simplification/complication
        // of code.
        private IccCardConstants.State mSimState = IccCardConstants.State.READY;
        private int mDataNetType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        private int mDataState = TelephonyManager.DATA_DISCONNECTED;
        private ServiceState mServiceState;
        private SignalStrength mSignalStrength;
        private MobileIconGroup mDefaultIcons;
        private Config mConfig;

        /// M: Support "Service Network Type on Statusbar".
        private NetworkType mNetworkType = null;

        ///M: Support SVLTE. @{
        private SvLteController mSvLteController;
        ///M: Support SVLTE. @}

        // TODO: Reduce number of vars passed in, if we have the NetworkController, probably don't
        // need listener lists anymore.
        public MobileSignalController(Context context, Config config, boolean hasMobileData,
                TelephonyManager phone, List<NetworkSignalChangedCallback> signalCallbacks,
                List<SignalCluster> signalClusters, NetworkControllerImpl networkController,
                SubscriptionInfo info) {
            super("MobileSignalController(" + info.getSubscriptionId() + ")", context,
                    NetworkCapabilities.TRANSPORT_CELLULAR, signalCallbacks, signalClusters,
                    networkController);
            mNetworkToIconLookup = new SparseArray<>();
            mConfig = config;
            mPhone = phone;
            mSubscriptionInfo = info;
            mPhoneStateListener = new MobilePhoneStateListener(info.getSubscriptionId());
            mNetworkNameSeparator = getStringIfExists(R.string.status_bar_network_name_separator);
            mNetworkNameDefault = getStringIfExists(
                    com.android.internal.R.string.lockscreen_carrier_default);

            ///M: Support SVLTE. @{
            mSvLteController = new SvLteController(mContext, info);
            ///M: Support SVLTE. @}

            mapIconSets();

            mLastState.networkName = mCurrentState.networkName = mNetworkNameDefault;
            mLastState.enabled = mCurrentState.enabled = hasMobileData;
            mLastState.iconGroup = mCurrentState.iconGroup = mDefaultIcons;
            // Get initial data sim state.
            updateDataSim();
        }

        public void setConfiguration(Config config) {
            mConfig = config;
            mapIconSets();
            updateTelephony();
        }

        /**
         * Get (the mobile parts of) the carrier string.
         *
         * @param currentLabel can be used for concatenation, currently just empty
         * @param connected whether the device has connection to the internet at all
         * @param isMobileLabel whether to always return the network or just when data is connected
         */
        public String getLabel(String currentLabel, boolean connected, boolean isMobileLabel) {
            if (!mCurrentState.enabled) {
                return "";
            } else {
                String mobileLabel = "";
                // We want to show the carrier name if in service and either:
                // - We are connected to mobile data, or
                // - We are not connected to mobile data, as long as the *reason* packets are not
                //   being routed over that link is that we have better connectivity via wifi.
                // If data is disconnected for some other reason but wifi (or ethernet/bluetooth)
                // is connected, we show nothing.
                // Otherwise (nothing connected) we show "No internet connection".
                if (mCurrentState.dataConnected) {
                    mobileLabel = mCurrentState.networkName;
                } else if (connected || mCurrentState.isEmergency) {
                    if (mCurrentState.connected || mCurrentState.isEmergency) {
                        // The isEmergencyOnly test covers the case of a phone with no SIM
                        mobileLabel = mCurrentState.networkName;
                    }
                } else {
                    mobileLabel = mContext.getString(
                            R.string.status_bar_settings_signal_meter_disconnected);
                }

                if (currentLabel.length() != 0) {
                    currentLabel = currentLabel + mNetworkNameSeparator;
                }
                // Now for things that should only be shown when actually using mobile data.
                if (isMobileLabel) {
                    return currentLabel + mobileLabel;
                } else {
                    return currentLabel
                            + (mCurrentState.dataConnected ? mobileLabel : currentLabel);
                }
            }
        }

        public int getDataContentDescription() {
            return getIcons().mDataContentDescription;
        }

        public void setAirplaneMode(boolean airplaneMode) {
            mCurrentState.airplaneMode = airplaneMode;
            notifyListenersIfNecessary();
        }

        public void setInetCondition(int inetCondition, int inetConditionForNetwork) {
            // For mobile data, use general inet condition for phone signal indexing,
            // and network specific for data indexing (I think this might be a bug, but
            // keeping for now).
            // TODO: Update with explanation of why.
            mCurrentState.inetForNetwork = inetConditionForNetwork;
            setInetCondition(inetCondition);
        }

        /// M: Support "SystemUI - VoLTE icon". @{
        public void setIMSState(int imsState) {
            Xlog.d(mTag, "old IMSState=" + mIMSState + ", new IMSState=" + imsState);
            mIMSState = imsState;
            if (mIMSState != IMS_REGISTERED) {
               mWfcState = false; 
            }
            updateVoLTE();
        }

        public void setWfcState(boolean state) {
            Xlog.d(mTag, "old WfcState=" + mWfcState + ", new WfcState=" + state);
            mWfcState = state;
            updateVoLTE();
        }
        /// M: Support "SystemUI - VoLTE icon". @}

        /**
         * Start listening for phone state changes.
         */
        public void registerListener() {
            mPhone.listen(mPhoneStateListener,
                    PhoneStateListener.LISTEN_SERVICE_STATE
                            | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                            | PhoneStateListener.LISTEN_CALL_STATE
                            | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                            /// M: add PRECISE_DATA_CONNECTION listener
                            | PhoneStateListener.LISTEN_PRECISE_DATA_CONNECTION_STATE
                            /// @}
                            | PhoneStateListener.LISTEN_DATA_ACTIVITY);
        }

        /**
         * Stop listening for phone state changes.
         */
        public void unregisterListener() {
            mPhone.listen(mPhoneStateListener, 0);
        }

        /**
         * Produce a mapping of data network types to icon groups for simple and quick use in
         * updateTelephony.
         */
        private void mapIconSets() {
            mNetworkToIconLookup.clear();

            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyIcons.THREE_G);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyIcons.THREE_G);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyIcons.THREE_G);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyIcons.THREE_G);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_UMTS, TelephonyIcons.THREE_G);

            if (!mConfig.showAtLeast3G) {
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                        TelephonyIcons.UNKNOWN);
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EDGE, TelephonyIcons.E);
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_CDMA, TelephonyIcons.ONE_X);
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyIcons.ONE_X);

                mDefaultIcons = TelephonyIcons.G;
            } else {
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                        TelephonyIcons.THREE_G);
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EDGE,
                        TelephonyIcons.THREE_G);
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_CDMA,
                        TelephonyIcons.THREE_G);
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_1xRTT,
                        TelephonyIcons.THREE_G);
                mDefaultIcons = TelephonyIcons.THREE_G;
            }

            MobileIconGroup hGroup = TelephonyIcons.THREE_G;
            if (mConfig.hspaDataDistinguishable) {
                hGroup = TelephonyIcons.H;
            }
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_HSDPA, hGroup);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_HSUPA, hGroup);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_HSPA, hGroup);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_HSPAP, hGroup);

            if (mConfig.show4gForLte) {
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE, TelephonyIcons.FOUR_G);
            } else {
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE, TelephonyIcons.LTE);
            }
        }

        @Override
        public void notifyListeners() {
            MobileIconGroup icons = getIcons();

            String contentDescription = getStringIfExists(getContentDescription());
            String dataContentDescription = getStringIfExists(icons.mDataContentDescription);

            /// M: config for show the ! icon or not
            boolean showDataIcon = mCurrentState.dataConnected
                    && (mCurrentState.inetForNetwork != 0 || mShowNormalIcon)
                    || mCurrentState.iconGroup == TelephonyIcons.ROAMING;

            // Only send data sim callbacks to QS.
            if (mCurrentState.dataSim) {
                int qsTypeIcon = showDataIcon ? icons.mQsDataType[mCurrentState.inetForNetwork] : 0;
                int length = mSignalsChangedCallbacks.size();
                for (int i = 0; i < length; i++) {
                    mSignalsChangedCallbacks.get(i).onMobileDataSignalChanged(mCurrentState.enabled
                            && !mCurrentState.isEmergency,
                            getQsCurrentIconId(), contentDescription,
                            qsTypeIcon,
                            mCurrentState.dataConnected && mCurrentState.activityIn,
                            mCurrentState.dataConnected && mCurrentState.activityOut,
                            dataContentDescription,
                            mCurrentState.isEmergency ? null : mCurrentState.networkName,
                            // Only wide if actually showing something.
                            icons.mIsWide && qsTypeIcon != 0);
                }
            }

            int typeIcon = showDataIcon ? icons.mDataType : 0;

            /// M: Add for CT6M, add data activity icon & primary simcard icon. @{
            int dataActivity = TelephonyManager.DATA_ACTIVITY_NONE;
            if (mCurrentState.dataConnected) {
                if (mCurrentState.activityIn
                        && mCurrentState.activityOut) {
                    dataActivity = TelephonyManager.DATA_ACTIVITY_INOUT;
                } else if (mCurrentState.activityIn) {
                    dataActivity = TelephonyManager.DATA_ACTIVITY_IN;
                } else if (mCurrentState.activityOut) {
                    dataActivity = TelephonyManager.DATA_ACTIVITY_OUT;
                }
            }
            int primarySimIcon = TelephonyIcons.getPrimarySimIcon(
                    isRoaming(), mSubscriptionInfo.getSubscriptionId());
            /// @}

            /// M: Add roaming data network type icon @{
            if (typeIcon != 0 && typeIcon == TelephonyIcons.ROAMING_ICON) {
                if (mCurrentState.dataConnected &&
                    (mCurrentState.inetForNetwork != 0 || mShowNormalIcon)) {
                    if (mNetworkToIconLookup.indexOfKey(mDataNetType) >= 0) {
                        /// M: Add for CT6M, data icon not show roam.
                        if (FeatureOptionUtils.isMTK_CT6M_SUPPORT()) {
                            typeIcon = mNetworkToIconLookup.get(mDataNetType).mDataType;
                        } else {
                        /// @}
                            typeIcon = TelephonyIcons.getRoamingDataTypeIcon(
                                    mNetworkToIconLookup.get(mDataNetType).mDataType);
                            Log.d(mTag, "getRoamingDataTypeIcon=" + typeIcon
                                    + ", mDataNetType=" + mDataNetType);
                        }
                    } else {
                        /// M: Add for CT6M, data icon not show roam.
                        if (FeatureOptionUtils.isMTK_CT6M_SUPPORT()) {
                            typeIcon = mDefaultIcons.mDataType;
                        } else {
                        /// @}
                            typeIcon = TelephonyIcons.getRoamingDataTypeIcon(
                                    mDefaultIcons.mDataType);
                            Log.d(mTag, "getRoamingDataTypeIcon=" + typeIcon +
                                    ", mDataNetType=" + mDataNetType +
                                    ", mDefaultIcons=" + mDefaultIcons);
                        }
                    }
                /// M: Add for CT6M, data not connected, not show data icon. @{
                } else if (FeatureOptionUtils.isMTK_CT6M_SUPPORT() &&
                        !mCurrentState.dataConnected) {
                    typeIcon = 0;
                }
                /// @}
            }
            /// M: Add roaming data network type icon @}

            int signalClustersLength = mSignalClusters.size();
            for (int i = 0; i < signalClustersLength; i++) {
                /// M: Support "Service Network Type on Statusbar".
                mSignalClusters.get(i).setNetworkType(mNetworkType,
                        mSubscriptionInfo.getSubscriptionId());

                mSignalClusters.get(i).setMobileDataIndicators(
                        mCurrentState.enabled && !mCurrentState.airplaneMode,
                        getCurrentIconId(),
                        typeIcon,
                        /// M: Add for CT6M. add activity icon @{
                        dataActivity,
                        primarySimIcon,
                        /// @}
                        contentDescription,
                        dataContentDescription,
                        // Only wide if actually showing something.
                        icons.mIsWide && typeIcon != 0,
                        mSubscriptionInfo.getSubscriptionId());
            }
        }

        @Override
        protected MobileState cleanState() {
            return new MobileState();
        }

        private boolean hasService() {
            ///M: Support SVLTE. @{
            if (SvLteController.isMediatekSVLteDcSupport(mSubscriptionInfo)) {
                return mSvLteController.hasService();
            }
            ///M: Support SVLTE. @}

            if (mServiceState != null) {
                // Consider the device to be in service if either voice or data
                // service is available. Some SIM cards are marketed as data-only
                // and do not support voice service, and on these SIM cards, we
                // want to show signal bars for data service as well as the "no
                // service" or "emergency calls only" text that indicates that voice
                // is not available.
                switch (mServiceState.getVoiceRegState()) {
                    case ServiceState.STATE_POWER_OFF:
                        return false;
                    case ServiceState.STATE_OUT_OF_SERVICE:
                    case ServiceState.STATE_EMERGENCY_ONLY:
                        return mServiceState.getDataRegState() == ServiceState.STATE_IN_SERVICE;
                    default:
                        return true;
                }
            } else {
                return false;
            }
        }

        private boolean isCdma() {
            if (DEBUG) {
                Log.d(mTag, "isCdma: mSignalStrength = " + mSignalStrength);
            }
            return (mSignalStrength != null) && !mSignalStrength.isGsm();
        }

        public boolean isEmergencyOnly() {
            ///M: Support SVLTE. @{
            if (SvLteController.isMediatekSVLteDcSupport(mSubscriptionInfo)) {
                Xlog.d(mTag, "isEmergencyOnly(), will return mSvLteController.isEmergencyOnly()");
                return mSvLteController.isEmergencyOnly();
            }
            ///M: Support SVLTE. @}
            Xlog.d(mTag, "isEmergencyOnly(), will return mServiceState.isEmergencyOnly()");
            return (mServiceState != null && mServiceState.isEmergencyOnly());
        }

        private boolean isRoaming() {
            if (isCdma()) {
                /// M: [ALPS02013362] NullPointerException @{
                if (mServiceState == null) {
                    if (DEBUG) {
                        Log.d(mTag, "isRoaming: mServiceState is null");
                    }
                    return false;
                }
                /// M: [ALPS02013362] NullPointerException @}
                final int iconMode = mServiceState.getCdmaEriIconMode();
                final int iconIndex = mServiceState.getCdmaEriIconIndex();
                if (DEBUG) {
                    Log.d(mTag, "isRoaming: CDMA mode " + iconMode + " index " + iconIndex);
                }
                return iconIndex != EriInfo.ROAMING_INDICATOR_OFF
                        && (iconMode == EriInfo.ROAMING_ICON_MODE_NORMAL
                            || iconMode == EriInfo.ROAMING_ICON_MODE_FLASH);
            } else {
                if (DEBUG) {
                    Log.d(mTag, "isRoaming: mServiceState = " + mServiceState);
                }
                return mServiceState != null && mServiceState.getRoaming();
            }
        }

        public void handleBroadcast(Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION)) {
                updateNetworkName(intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false),
                        intent.getStringExtra(TelephonyIntents.EXTRA_SPN),
                        intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false),
                        intent.getStringExtra(TelephonyIntents.EXTRA_PLMN));
                notifyListenersIfNecessary();
            } else if (action.equals(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)) {
                updateDataSim();
            } else if (action.equals("android.intent.action.ACTION_SHUTDOWN_IPO")) {
                /// M: [ALPS01900068] Reset Phone State when IPO Shutdown
                cleanPhoneState();
                updateNetworkType();
                updateTelephony();
                updateVoLTE();
            } else if(action.equals(TelephonyIntents.ACTION_ANY_DEDICATE_DATA_CONNECTION_STATE_CHANGED)){
                updateVoLTE();
            }
        }

        private void updateDataSim() {
            int defaultDataSub = SubscriptionManager.getDefaultDataSubId();
            if (SubscriptionManager.isValidSubscriptionId(defaultDataSub)) {
                mCurrentState.dataSim = defaultDataSub == mSubscriptionInfo.getSubscriptionId();
            } else {
                // There doesn't seem to be a data sim selected, however if
                // there isn't a MobileSignalController with dataSim set, then
                // QS won't get any callbacks and will be blank.  Instead
                // lets just assume we are the data sim (which will basically
                // show one at random) in QS until one is selected.  The user
                // should pick one soon after, so we shouldn't be in this state
                // for long.
                mCurrentState.dataSim = true;
            }
            notifyListenersIfNecessary();
        }

        /**
         * Updates the network's name based on incoming spn and plmn.
         */
        void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn) {
            if (CHATTY) {
                Log.d("CarrierLabel", "updateNetworkName showSpn=" + showSpn + " spn=" + spn
                        + " showPlmn=" + showPlmn + " plmn=" + plmn + " for " + mTag);
            }
            StringBuilder str = new StringBuilder();
            if (showPlmn && plmn != null) {
                str.append(plmn);
            }
            if (showSpn && spn != null) {
                if (str.length() != 0) {
                    str.append(mNetworkNameSeparator);
                }
                str.append(spn);
            }
            if (str.length() != 0) {
                mCurrentState.networkName = str.toString();
            } else {
                mCurrentState.networkName = mNetworkNameDefault;
            }
        }

        /// M: Support "Service Network Type on Statusbar". @{
        private final int getNWTypeByPriority(int cs, int ps) {
            /// By Network Class.
            if (TelephonyManager.getNetworkClass(cs) > TelephonyManager.getNetworkClass(ps)) {
                return cs;
            } else {
                return ps;
            }
        }

        private final void updateNetworkType() {
            int tempNetworkType; //Big - switch

            if (mServiceState != null) {
                int networkTypeData = mDataNetType; //Big - Data
                if ((mDataState == TelephonyManager.DATA_UNKNOWN ||
                     mDataState == TelephonyManager.DATA_DISCONNECTED)) {
                    Xlog.d(mTag, "updateNetworkType: DataState= " + mDataState
                           + ", getDataNetworkType= " + mServiceState.getDataNetworkType());
                    networkTypeData = mServiceState.getDataNetworkType();
                }
                tempNetworkType = getNWTypeByPriority(mServiceState.getVoiceNetworkType(),
                                                      networkTypeData);
            } else {
                tempNetworkType = mDataNetType;
            }

            ///M: Support SVLTE. @{
            if (SvLteController.isMediatekSVLteDcSupport(mSubscriptionInfo)) {
                tempNetworkType = mSvLteController.getDataNetTypeWithLTEService(tempNetworkType);
            }
            ///M: Support SVLTE. @}

            Xlog.d(mTag, "updateNetworkType: DataNetType=" + mDataNetType +
                " / " + tempNetworkType);
            switch (tempNetworkType) {
                case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                    if (!mConfig.showAtLeast3G) {
                        mNetworkType = NetworkType.Type_G;
                        break;
                    }
                case TelephonyManager.NETWORK_TYPE_EDGE:
                    if (!mConfig.showAtLeast3G) {
                        mNetworkType = NetworkType.Type_E;
                        break;
                    }
                case TelephonyManager.NETWORK_TYPE_UMTS:
                    mNetworkType = NetworkType.Type_3G;
                    break;
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    mNetworkType = NetworkType.Type_3G;
                    break;
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                    mNetworkType = NetworkType.Type_1X;
                    break;
                case TelephonyManager.NETWORK_TYPE_EVDO_0: //fall through
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                    mNetworkType = NetworkType.Type_1X3G;
                    break;
                case TelephonyManager.NETWORK_TYPE_LTE:
                    mNetworkType = NetworkType.Type_4G;
                    break;
                default:
                    if (!mConfig.showAtLeast3G) {
                        mNetworkType = NetworkType.Type_G;
                    } else {
                        mNetworkType = NetworkType.Type_3G;
                    }
                    break;
            }
            if (!hasService()) {
                mNetworkType = null;
            }

            Xlog.d(mTag, "updateNetworkType: mNetworkType= " + mNetworkType);
        }
        /// M: Support "Service Network Type on Statusbar". @}

        /// M: Update data network type when DATA_UNKNOWN or DATA_DISCONNECTED @{
        private int getDataNetworkTypeFromService() {
            int dataNetType = mDataNetType;
            if (mServiceState != null) {
                final int cs = mServiceState.getVoiceNetworkType();
                final int ps = mServiceState.getDataNetworkType();
                dataNetType = getNWTypeByPriority(cs, ps);

                if (DEBUG) {
                    Log.d(mTag, "getDataNetworkTypeFromService: mNetworkType= " + mNetworkType +
                        ", DataNetType= " + mDataNetType + " / " + dataNetType +
                        ", CS=" + cs + ", PS=" + ps + ", mDataState " + mDataState);
                }
            }

            return dataNetType;
        }
        /// M: Update data network type when DATA_UNKNOWN or DATA_DISCONNECTED @}

        /**
         * Updates the current state based on mServiceState, mSignalStrength, mDataNetType,
         * mDataState, and mSimState.  It should be called any time one of these is updated.
         * This will call listeners if necessary.
         */
        private final void updateTelephony() {
            if (DEBUG) {
                Log.d(mTag, "updateTelephonySignalStrength: hasService=" + hasService()
                        + " ss=" + mSignalStrength);
            }
            mCurrentState.connected = hasService() && mSignalStrength != null;
            if (mCurrentState.connected) {
                if (!mSignalStrength.isGsm() && mConfig.alwaysShowCdmaRssi) {
                    mCurrentState.level = mSignalStrength.getCdmaLevel();
                } else {
                    mCurrentState.level = mSignalStrength.getLevel();
                }

                /// M: Support SVLTE. @{
                if (SvLteController.isMediatekSVLteDcSupport(mSubscriptionInfo)) {
                    mCurrentState.level = mSvLteController.getSignalStrengthLevel(
                        mNetworkType, mConfig.alwaysShowCdmaRssi);
                }
                /// @}
            }
            if (mNetworkToIconLookup.indexOfKey(mDataNetType) >= 0) {
                mCurrentState.iconGroup = mNetworkToIconLookup.get(mDataNetType);
            } else {
                mCurrentState.iconGroup = mDefaultIcons;
            }
            mCurrentState.dataConnected = mCurrentState.connected
                    && mDataState == TelephonyManager.DATA_CONNECTED;

            ///M: Support SVLTE. @{
            if (SvLteController.isMediatekSVLteDcSupport(mSubscriptionInfo)) {
                mCurrentState.dataConnected = mCurrentState.dataConnected
                        || mSvLteController.isDataConnected();
            }
            ///M: Support SVLTE. @}

            if (isRoaming()) {
                mCurrentState.iconGroup = TelephonyIcons.ROAMING;
            }
            if (isEmergencyOnly() != mCurrentState.isEmergency) {
                mCurrentState.isEmergency = isEmergencyOnly();
                mNetworkController.recalculateEmergency();
            }

            /// M: change network name to alpha long @{
            // Fill in the network name if we think we have it.
            if (mCurrentState.networkName == mNetworkNameDefault && mServiceState != null
                    && mServiceState.getOperatorAlphaLong() != null) {
                /// M: Do not use operator alpha long as network name when there is no service @{
                if (hasService() == true) {
                    mCurrentState.networkName = mServiceState.getOperatorAlphaLong();
                    if (DEBUG) {
                        Log.d(mTag, "change network name to alpha long = " +
                            mCurrentState.networkName);
                    }
                } else {
                    if (DEBUG) {
                        Log.d(mTag, "hasService() == false, alpha long = " +
                            mServiceState.getOperatorAlphaLong());
                    }
                }
                /// M: Do not use operator alpha long as network name when there is no service @}
            }
            /// M: change network name to alpha long @}

            /// M: Support "Network Type on state".  @{
            mCurrentState.networkType = mNetworkType;
            if (mCurrentState.networkType != mLastState.networkType) {
                Xlog.d(mTag, "[NetworkType change] " + "mCurrentState.networkType:"
                        + mCurrentState.networkType + ", mLastState.networkType:"
                        + mLastState.networkType);
            }
            /// M: Support "Network Type on state". @}

            /// M: Support "ServiceState on state".  @{
            if (PluginFactory.getStatusBarPlugin(mContext).customizeBehaviorSet()
                    == BehaviorSet.OP09_BS) {
                mCurrentState.serviceState = mServiceState;
            }
            /// M: Support "ServiceState on state".  @}

            /// M: Update data sim for the callbacks to QS @{
            int defaultDataSub = SubscriptionManager.getDefaultDataSubId();
            if (SubscriptionManager.isValidSubscriptionId(defaultDataSub)) {
                mCurrentState.dataSim = defaultDataSub == mSubscriptionInfo.getSubscriptionId();
            } else {
                // There doesn't seem to be a data sim selected, however if
                // there isn't a MobileSignalController with dataSim set, then
                // QS won't get any callbacks and will be blank.  Instead
                // lets just assume we are the data sim (which will basically
                // show one at random) in QS until one is selected.  The user
                // should pick one soon after, so we shouldn't be in this state
                // for long.
                mCurrentState.dataSim = true;
            }
            /// M: Update data sim for the callbacks to QS @}

            /// M: Support "CMCC Performance test on SystemUI Statusbar". @{
            if (mCurrentState.connected) {
                Log.i(mTag, "[CMCC Performance test][SystemUI][Statusbar] show signal end ["
                        + SystemClock.elapsedRealtime() + "]");
            }
            /// M: Support "CMCC Performance test on SystemUI Statusbar". @}

            notifyListenersIfNecessary();
        }

        /// M: [ALPS01856198] Reset the phonestate info to avoid keeping the wrong state@{
        private void cleanPhoneState() {
            Log.d(mTag, "cleanPhoneState");
            // onSignalStrengthsChanged
            mSignalStrength = null;
            // onServiceStateChanged
            mServiceState = null;
            // onDataConnectionStateChanged
            mDataNetType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
            mDataState = TelephonyManager.DATA_DISCONNECTED;
            // Network Type
            mNetworkType = null;
            // IMS state
            mIMSState = IMS_UNREGISTERED;

            ///M: IMS Wfc State. @{
            mWfcState = false;
            ///M: IMS Wfc State. @}
            ///M: Support SVLTE. @{
            mSvLteController.cleanPhoneState();
            ///M: Support SVLTE. @}
        }
        /// M: [ALPS01856198] Reset the phonestate info to avoid keeping the wrong state@}

        /// M: Support "SystemUI - VoLTE icon". @{
        private final void updateVoLTE() {
            Xlog.d(mTag, "updateVoLTE: DataNetType=" + mDataNetType +
                   ",DataState=" + mDataState +
                   ",IMSState=" + mIMSState +
                   ",mUsedVoLTESubID=" + mNetworkController.getVoLTESubID());

            /// M: Support "SystemUI - Used VoLTE SubID". @{
            if (mNetworkController.getVoLTESubID() != mSubscriptionInfo.getSubscriptionId()) {
                Log.d(mTag, "Used VoLTE SubID = " + mNetworkController.getVoLTESubID()  +
                   ",not SubID = " +  mSubscriptionInfo.getSubscriptionId());
                return;
            }
            /// M: Support "SystemUI - Used VoLTE Slot ID". @{
            int slotID = SubscriptionManager.getSlotId(mNetworkController.getVoLTESubID());
            int simNum = SubscriptionManager.from(mContext).getActiveSubscriptionInfoCount();
            Log.d(mTag, "Used VoLTE SubID = " + mNetworkController.getVoLTESubID()  +
                   ",and SlotID = " +  slotID + ",and simNum = " +  simNum);
            /// M: Support "SystemUI - Used VoLTE Slot ID". @}

            /// M: Support "SystemUI - Used VoLTE SubID". @}
            boolean isImsPdnOnLTE = false;
            if (mWfcState) {
                // Ims is Registered on Wi-Fi, Check if there is any IMS emergency PDN
                ConnectivityManager connectivityManager = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE_EMERGENCY);
                if (networkInfo!= null) {
                    int nwType = networkInfo.getType();
                    int subType = networkInfo.getSubtype();           
                    if (nwType == ConnectivityManager.TYPE_MOBILE_EMERGENCY
                        && subType == TelephonyManager.NETWORK_TYPE_LTE) {
                       //Emergency pdn is on LTE
                       isImsPdnOnLTE = true;
                       Xlog.d(mTag, "updateVoLTE: Ims Pdn on LTE for ECC");
                    }
                }                
            } else {
                Xlog.d(mTag, "updateVoLTE: Ims Pdn will be on LTE");
                isImsPdnOnLTE = true;
            }
            // VoLTE on : 4G + IMS reg
            if (isImsPdnOnLTE && mDataNetType == TelephonyManager.NETWORK_TYPE_LTE &&
                (mDataState == TelephonyManager.DATA_CONNECTED ||
                 mDataState == TelephonyManager.DATA_SUSPENDED) &&
                mIMSState == IMS_REGISTERED) {
                // previous VoLTE = off, then redraw it
                if (mNetworkController.getVoLTEState() == false || mLastSIMNum != simNum) {
                    mLastSIMNum = simNum;
                    mNetworkController.setVoLTEState(true);
                    int length = mSignalClusters.size();
                    for (int i = 0; i < length; i++) {
                        if (simNum > 1) {
                            mSignalClusters.get(i).setVoLTE(true, slotID);
                        } else {
                            mSignalClusters.get(i).setVoLTE(true);
                        }
                    }
                }
                return;
            }

            // VoLTE off case
            if (mNetworkController.getVoLTEState() == true || mLastSIMNum != simNum) {
                mLastSIMNum = simNum;
                mNetworkController.setVoLTEState(false);
                int length = mSignalClusters.size();
                for (int i = 0; i < length; i++) {
                    if (simNum > 1) {
                        mSignalClusters.get(i).setVoLTE(false, slotID);
                    } else {
                        mSignalClusters.get(i).setVoLTE(false);
                    }
                }
            }
        }
        /// M: Support "SystemUI - VoLTE icon". @}

        @VisibleForTesting
        void setActivity(int activity) {
            mCurrentState.activityIn = activity == TelephonyManager.DATA_ACTIVITY_INOUT
                    || activity == TelephonyManager.DATA_ACTIVITY_IN;
            mCurrentState.activityOut = activity == TelephonyManager.DATA_ACTIVITY_INOUT
                    || activity == TelephonyManager.DATA_ACTIVITY_OUT;
            notifyListenersIfNecessary();
        }

        @Override
        public void dump(PrintWriter pw) {
            super.dump(pw);
            pw.println("  mSubscription=" + mSubscriptionInfo + ",");
            pw.println("  mServiceState=" + mServiceState + ",");
            pw.println("  mSignalStrength=" + mSignalStrength + ",");
            pw.println("  mDataState=" + mDataState + ",");
            pw.println("  mDataNetType=" + mDataNetType + ",");
        }

        class MobilePhoneStateListener extends PhoneStateListener {
            public MobilePhoneStateListener(int subId) {
                super(subId);
            }

            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                if (DEBUG) {
                    Log.d(mTag, "onSignalStrengthsChanged signalStrength=" + signalStrength +
                            ((signalStrength == null) ? "" : (" level=" + signalStrength.getLevel())));
                }
                mSignalStrength = signalStrength;
                ///M: Support SVLTE. @{
                mSvLteController.onSignalStrengthsChanged(signalStrength);
                ///M: Support SVLTE. @}
                updateTelephony();
            }

            @Override
            public void onServiceStateChanged(ServiceState state) {
                if (DEBUG) {
                    Log.d(mTag, "onServiceStateChanged voiceState=" + state.getVoiceRegState()
                            + " dataState=" + state.getDataRegState());
                }
                mServiceState = state;
                /// M: Update data network type when DATA_UNKNOWN or DATA_DISCONNECTED @{
                if (mDataState == TelephonyManager.DATA_UNKNOWN ||
                    mDataState == TelephonyManager.DATA_DISCONNECTED) {
                    mDataNetType = getDataNetworkTypeFromService();
                }
                /// M: Update data network type when DATA_UNKNOWN or DATA_DISCONNECTED @}
                ///M: Support SVLTE. @{
                mSvLteController.onServiceStateChanged(state);
                ///M: Support SVLTE. @}
                /// M: Support "Service Network Type on Statusbar".
                updateNetworkType();
                updateTelephony();
                /// M: Support "SystemUI - VoLTE icon".
                updateVoLTE();
            }

            @Override
            public void onDataConnectionStateChanged(int state, int networkType) {
                if (DEBUG) {
                    Log.d(mTag, "onDataConnectionStateChanged: state=" + state
                            + " type=" + networkType);
                }
                mDataState = state;
                mDataNetType = networkType;
                ///M: Support SVLTE. @{
                mSvLteController.onDataConnectionStateChanged(state, networkType);
                ///M: Support SVLTE. @}
                /// M: Support "Service Network Type on Statusbar".
                updateNetworkType();
                updateTelephony();
                /// M: Support "SystemUI - VoLTE icon".
                updateVoLTE();
            }

            @Override
            public void onDataActivity(int direction) {
                if (DEBUG) {
                    Log.d(mTag, "onDataActivity: direction=" + direction);
                }
                ///M: Support SVLTE. @{
                mSvLteController.onDataActivity(direction);
                ///M: Support SVLTE. @}
                setActivity(direction);
            }

            /// M: add LISTEN_PRECISE_DATA_CONNECTION_STATE listener. @{
            @Override
            public void onPreciseDataConnectionStateChanged(PreciseDataConnectionState state) {
                /// M: Support onPreciseDataConnectionStateChanged.  @{
                if (PluginFactory.getStatusBarPlugin(mContext).customizeBehaviorSet()
                        == BehaviorSet.OP01_BS) {
                    mSvLteController.onPreciseDataConnectionStateChanged(state);
                    mCurrentState.mShowDataActivityIcon = mSvLteController.isShowDataActivityIcon();
                    notifyListenersIfNecessary();
                }
            }
            ///  @}
        };

        static class MobileIconGroup extends SignalController.IconGroup {
            final int mDataContentDescription; // mContentDescriptionDataType
            final int mDataType;
            final boolean mIsWide;
            final int[] mQsDataType;

            public MobileIconGroup(String name, int[][] sbIcons, int[][] qsIcons, int[] contentDesc,
                    int sbNullState, int qsNullState, int sbDiscState, int qsDiscState,
                    int discContentDesc, int dataContentDesc, int dataType, boolean isWide,
                    int[] qsDataType) {
                super(name, sbIcons, qsIcons, contentDesc, sbNullState, qsNullState, sbDiscState,
                        qsDiscState, discContentDesc);
                mDataContentDescription = dataContentDesc;
                mDataType = dataType;
                mIsWide = isWide;
                mQsDataType = qsDataType;
            }
        }

        static class MobileState extends SignalController.State {
            String networkName;
            boolean dataSim;
            boolean dataConnected;
            boolean isEmergency;
            boolean airplaneMode;
            int inetForNetwork;

            /// M: Support "ServiceState on state".
            ServiceState serviceState;

            @Override
            public void copyFrom(State s) {
                super.copyFrom(s);
                MobileState state = (MobileState) s;
                dataSim = state.dataSim;
                networkName = state.networkName;
                dataConnected = state.dataConnected;
                inetForNetwork = state.inetForNetwork;
                isEmergency = state.isEmergency;
                airplaneMode = state.airplaneMode;

                /// M: Support "ServiceState on state".
                if (state.serviceState != null) {
                    serviceState = new ServiceState(state.serviceState);
                } else {
                    serviceState = null;
                }
            }

            @Override
            protected void toString(StringBuilder builder) {
                super.toString(builder);
                builder.append(',');
                builder.append("dataSim=").append(dataSim).append(',');
                builder.append("networkName=").append(networkName).append(',');
                builder.append("dataConnected=").append(dataConnected).append(',');
                builder.append("inetForNetwork=").append(inetForNetwork).append(',');
                builder.append("isEmergency=").append(isEmergency).append(',');
                builder.append("airplaneMode=").append(airplaneMode);
            }

            @Override
            public boolean equals(Object o) {
                return super.equals(o)
                        && Objects.equals(((MobileState) o).networkName, networkName)
                        && ((MobileState) o).dataSim == dataSim
                        && ((MobileState) o).dataConnected == dataConnected
                        && ((MobileState) o).isEmergency == isEmergency
                        && ((MobileState) o).airplaneMode == airplaneMode
                        && ((MobileState) o).inetForNetwork == inetForNetwork
                        /// M: Support "ServiceState on state".
                        && equalsHandlesNulls(((MobileState) o).serviceState, serviceState);
            }
        }
    }

    /**
     * Common base class for handling signal for both wifi and mobile data.
     */
    static abstract class SignalController<T extends SignalController.State,
            I extends SignalController.IconGroup> {
        protected final String mTag;
        protected final T mCurrentState;
        protected final T mLastState;
        protected final int mTransportType;
        protected final Context mContext;
        // The owner of the SignalController (i.e. NetworkController will maintain the following
        // lists and call notifyListeners whenever the list has changed to ensure everyone
        // is aware of current state.
        protected final List<NetworkSignalChangedCallback> mSignalsChangedCallbacks;
        protected final List<SignalCluster> mSignalClusters;
        protected final NetworkControllerImpl mNetworkController;

        // Save the previous HISTORY_SIZE states for logging.
        private final State[] mHistory;
        // Where to copy the next state into.
        private int mHistoryIndex;

        /// M: [ALPS01794897] refresh view when subinfo change.
        boolean mInvalid = false;

        public SignalController(String tag, Context context, int type,
                List<NetworkSignalChangedCallback> signalCallbacks,
                List<SignalCluster> signalClusters, NetworkControllerImpl networkController) {
            mTag = TAG + "." + tag;
            mNetworkController = networkController;
            mTransportType = type;
            mContext = context;
            mSignalsChangedCallbacks = signalCallbacks;
            mSignalClusters = signalClusters;
            mCurrentState = cleanState();
            mLastState = cleanState();
            if (RECORD_HISTORY) {
                mHistory = new State[HISTORY_SIZE];
                for (int i = 0; i < HISTORY_SIZE; i++) {
                    mHistory[i] = cleanState();
                }
            }
        }

        public T getState() {
            return mCurrentState;
        }

        public int getTransportType() {
            return mTransportType;
        }

        public void setInetCondition(int inetCondition) {
            mCurrentState.inetCondition = inetCondition;
            notifyListenersIfNecessary();
        }

        /**
         * Used at the end of demo mode to clear out any ugly state that it has created.
         * Since we haven't had any callbacks, then isDirty will not have been triggered,
         * so we can just take the last good state directly from there.
         *
         * Used for demo mode.
         */
        void resetLastState() {
            mCurrentState.copyFrom(mLastState);
        }

        /**
         * Determines if the state of this signal controller has changed and
         * needs to trigger callbacks related to it.
         */
        public boolean isDirty() {
            Xlog.d(mTag, "isDirty : " + mInvalid);
            if (!mLastState.equals(mCurrentState) || mInvalid) {
                if (DEBUG) {
                    Log.d(mTag, "Change in state from: " + mLastState + "\n"
                            + "\tto: " + mCurrentState);
                }
                mInvalid = false;
                return true;
            }
            mInvalid = false;
            return false;
        }

        /// M: [ALPS01794897] refresh view when subinfo change. @{
        void setInvalid(boolean isValid) {
            mInvalid = isValid;
        }
        /// M: [ALPS01794897] refresh view when subinfo change. @}

        public void saveLastState() {
            if (RECORD_HISTORY) {
                recordLastState();
            }
            // Updates the current time.
            mCurrentState.time = System.currentTimeMillis();
            mLastState.copyFrom(mCurrentState);
        }

        /**
         * Gets the signal icon for QS based on current state of connected, enabled, and level.
         */
        public int getQsCurrentIconId() {
            if (mCurrentState.connected) {
                return getIcons().mQsIcons[mCurrentState.inetCondition][mCurrentState.level];
            } else if (mCurrentState.enabled) {
                return getIcons().mQsDiscState;
            } else {
                return getIcons().mQsNullState;
            }
        }

        /**
         * Gets the signal icon for SB based on current state of connected, enabled, and level.
         */
        public int getCurrentIconId() {
            if (mCurrentState.connected) {
                return getIcons().mSbIcons[mCurrentState.inetCondition][mCurrentState.level];
            } else if (mCurrentState.enabled) {
                return getIcons().mSbDiscState;
            } else {
                return getIcons().mSbNullState;
            }
        }

        /**
         * Gets the content description id for the signal based on current state of connected and
         * level.
         */
        public int getContentDescription() {
            if (mCurrentState.connected) {
                return getIcons().mContentDesc[mCurrentState.level];
            } else {
                return getIcons().mDiscContentDesc;
            }
        }

        public void notifyListenersIfNecessary() {
            if (isDirty()) {
                saveLastState();
                notifyListeners();
                mNetworkController.refreshCarrierLabel();
            }
        }

        /**
         * Returns the resource if resId is not 0, and an empty string otherwise.
         */
        protected String getStringIfExists(int resId) {
            return resId != 0 ? mContext.getString(resId) : "";
        }

        protected I getIcons() {
            return (I) mCurrentState.iconGroup;
        }

        /**
         * Saves the last state of any changes, so we can log the current
         * and last value of any state data.
         */
        protected void recordLastState() {
            mHistory[mHistoryIndex++ & (HISTORY_SIZE - 1)].copyFrom(mLastState);
        }

        public void dump(PrintWriter pw) {
            pw.println("  - " + mTag + " -----");
            pw.println("  Current State: " + mCurrentState);
            if (RECORD_HISTORY) {
                // Count up the states that actually contain time stamps, and only display those.
                int size = 0;
                for (int i = 0; i < HISTORY_SIZE; i++) {
                    if (mHistory[i].time != 0) size++;
                }
                // Print out the previous states in ordered number.
                for (int i = mHistoryIndex + HISTORY_SIZE - 1;
                        i >= mHistoryIndex + HISTORY_SIZE - size; i--) {
                    pw.println("  Previous State(" + (mHistoryIndex + HISTORY_SIZE - i) + ": "
                            + mHistory[i & (HISTORY_SIZE - 1)]);
                }
            }
        }

        /**
         * Trigger callbacks based on current state.  The callbacks should be completely
         * based on current state, and only need to be called in the scenario where
         * mCurrentState != mLastState.
         */
        public abstract void notifyListeners();

        /**
         * Generate a blank T.
         */
        protected abstract T cleanState();

        /*
         * Holds icons for a given state. Arrays are generally indexed as inet
         * state (full connectivity or not) first, and second dimension as
         * signal strength.
         */
        static class IconGroup {
            final int[][] mSbIcons;
            final int[][] mQsIcons;
            final int[] mContentDesc;
            final int mSbNullState;
            final int mQsNullState;
            final int mSbDiscState;
            final int mQsDiscState;
            final int mDiscContentDesc;
            // For logging.
            final String mName;

            public IconGroup(String name, int[][] sbIcons, int[][] qsIcons, int[] contentDesc,
                    int sbNullState, int qsNullState, int sbDiscState, int qsDiscState,
                    int discContentDesc) {
                mName = name;
                mSbIcons = sbIcons;
                mQsIcons = qsIcons;
                mContentDesc = contentDesc;
                mSbNullState = sbNullState;
                mQsNullState = qsNullState;
                mSbDiscState = sbDiscState;
                mQsDiscState = qsDiscState;
                mDiscContentDesc = discContentDesc;
            }

            @Override
            public String toString() {
                return "IconGroup(" + mName + ")";
            }
        }

        static class State {
            boolean connected;
            boolean enabled;
            boolean activityIn;
            boolean activityOut;
            int level;
            IconGroup iconGroup;
            int inetCondition;
            int rssi; // Only for logging.
            /// M: Support "Network Type on state".
            NetworkType networkType;
            /// M: whether to show data activity icon.
            boolean mShowDataActivityIcon;

            // Not used for comparison, just used for logging.
            long time;

            public void copyFrom(State state) {
                connected = state.connected;
                enabled = state.enabled;
                level = state.level;
                iconGroup = state.iconGroup;
                inetCondition = state.inetCondition;
                activityIn = state.activityIn;
                activityOut = state.activityOut;
                rssi = state.rssi;
                time = state.time;
                /// M: Support "Network Type on state".
                networkType =  state.networkType;
                /// M: whether to show data activity icon.
                mShowDataActivityIcon = state.mShowDataActivityIcon;
            }

            @Override
            public String toString() {
                if (time != 0) {
                    StringBuilder builder = new StringBuilder();
                    toString(builder);
                    return builder.toString();
                } else {
                    return "Empty " + getClass().getSimpleName();
                }
            }

            protected void toString(StringBuilder builder) {
                builder.append("connected=").append(connected).append(',')
                        .append("enabled=").append(enabled).append(',')
                        .append("level=").append(level).append(',')
                        .append("inetCondition=").append(inetCondition).append(',')
                        .append("iconGroup=").append(iconGroup).append(',')
                        .append("activityIn=").append(activityIn).append(',')
                        .append("activityOut=").append(activityOut).append(',')
                        .append("rssi=").append(rssi).append(',')
                        /// M: Support "Network Type on state".
                        .append("networkType=").append(networkType).append(',')
                        /// M: whether to show data activity icon.
                        .append("showDataActivityIcon=").append(mShowDataActivityIcon).append(',')
                        .append("lastModified=").append(DateFormat.format("MM-dd hh:mm:ss", time));
            }

            @Override
            public boolean equals(Object o) {
                if (!o.getClass().equals(getClass())) {
                    return false;
                }
                State other = (State) o;
                return other.connected == connected
                        && other.enabled == enabled
                        && other.level == level
                        && other.inetCondition == inetCondition
                        && other.iconGroup == iconGroup
                        && other.activityIn == activityIn
                        && other.activityOut == activityOut
                        && other.rssi == rssi
                        /// M: Support "Network Type on state".
                        && other.networkType == networkType
                        /// M: whether to show data activity icon.
                        && other.mShowDataActivityIcon == mShowDataActivityIcon;
            }
        }
    }

    public interface SignalCluster {
        void setWifiIndicators(boolean visible, int strengthIcon, String contentDescription);

        void setMobileDataIndicators(boolean visible, int strengthIcon, int typeIcon,
                /// M: Add for CT6M. add activity icon @{
                int dataActivity,
                int primarySimIcon,
                /// @}
                String contentDescription, String typeContentDescription, boolean isTypeIconWide,
                int subId);
        void setSubs(List<SubscriptionInfo> subs);
        void setNoSims(boolean show);

        void setIsAirplaneMode(boolean is, int airplaneIcon, int contentDescription);

        /**
         * M: setNetworkType For Network icon.
         * @param networkType : Network Type
         * @param subId : SubID
         */
        void setNetworkType(NetworkType networkType, int subId);

        /// M: Support "SystemUI - VoLTE icon".
        void setVoLTE(boolean isShowed);

        /// M: Support "SystemUI - VoLTE icon by slot".
        /**
         * M: setVoLTE For VolTE icon.
         * @param isShowed : is show VoLTE Icon
         * @param slotID : Slot ID
         */
        void setVoLTE(boolean isShowed, int slotID);

        /**
         * M: Set Default SIM Indicator.
         * @param showSimIndicator : Show SIM indicator or not
         * @param subID : subID
         */
        public void setShowSimIndicator(boolean showSimIndicator, int subID);
        /**
         * M: Set AlwaysAsk Or InternetCall icon.
         * @param selectID : AlwaysAsk or InternetCall
         */
        public void setShowAlwaysAskOrInternetCall(long selectID);
        /**
         * M: hide AlwaysAsk Or InternetCall icon.
         */
        public void setHideAlwaysAskOrInternetCall();
    }

    public interface EmergencyListener {
        void setEmergencyCallsOnly(boolean emergencyOnly);
    }

    public interface CarrierLabelListener {
        void setCarrierLabel(String label);
    }

    @VisibleForTesting
    static class Config {
        boolean showAtLeast3G = false;
        boolean alwaysShowCdmaRssi = false;
        boolean show4gForLte = false;
        boolean hspaDataDistinguishable;

        static Config readConfig(Context context) {
            Config config = new Config();
            Resources res = context.getResources();

            config.showAtLeast3G = res.getBoolean(R.bool.config_showMin3G);
            config.alwaysShowCdmaRssi =
                    res.getBoolean(com.android.internal.R.bool.config_alwaysUseCdmaRssi);
            config.show4gForLte = res.getBoolean(R.bool.config_show4GForLTE);
            config.hspaDataDistinguishable =
                    res.getBoolean(R.bool.config_hspa_data_distinguishable);
            return config;
        }
    }

    /// M: Support "Default SIM Indicator". @{
    /**
        * show SimIndicator.
        * @param subId : subID
        */
    public void showSimIndicator(int subId) {
        Xlog.d(TAG, "showSimIndicator: subID= " + subId);
        if (mMobileSignalControllers.size() > 1 &&
            SubscriptionManager.isValidSubscriptionId(subId)) {
            if (mMobileSignalControllers.containsKey(subId)) {
                for (int i = 0; i < mSignalClusters.size(); i++) {
                    mSignalClusters.get(i).setShowSimIndicator(
                       true, subId);
                }
            }
        }
    }
    /**
        * hide SimIndicator.
        */
    public void hideSimIndicator() {
        for (MobileSignalController controller : mMobileSignalControllers.values()) {
           int subId = controller.mSubscriptionInfo.getSubscriptionId();
           for (int i = 0; i < mSignalClusters.size(); i++) {
              mSignalClusters.get(i).setShowSimIndicator(
                        false, subId);
           }
        }
        for (int i = 0; i < mSignalClusters.size(); i++) {
          mSignalClusters.get(i).setHideAlwaysAskOrInternetCall();
        }
    }
    /**
        * show AlwaysAsk Or InternetCall.
        * @param selectID : AlwaysAsk or InternetCall
        */
    public void showAlwaysAskOrInternetCall(long selectID) {
        Xlog.d(TAG, "showAlwaysAskOrInternetCall= " + selectID);
        hideSimIndicator();
        for (int i = 0; i < mSignalClusters.size(); i++) {
          mSignalClusters.get(i).setShowAlwaysAskOrInternetCall(selectID);
        }
    }
    /// M: Support "Default SIM Indicator". }@

    /// M: Support "Operator plugin - Data activity/type, strength icon". @{
    private final NetworkControllerExt mNetworkControllerExt;

    private final boolean isMobileDataConnected(int subId) {
        MobileSignalController controller = getMobileSignalController(subId);
        return controller != null ? controller.getState().dataConnected : false;
    }

    private final MobileSignalController getMobileSignalController(int subId) {
        if (mMobileSignalControllers.containsKey(subId)) {
            return mMobileSignalControllers.get(subId);
        }

        Log.e(TAG, "Cannot find controller for sub: " + subId);
        return null;
    }

    /**
     * Get a INetworkControllerExt instance.
     *
     * @return INetworkControllerExt.
     */
    public final NetworkControllerExt getNetworkControllerExt() {
        return mNetworkControllerExt;
    }

    /**
     * Test whether two objects hold the same data values or both are null.
     *
     * @param a first obj
     * @param b second obj
     * @return true if two objects equal or both are null
     */
    private static final boolean equalsHandlesNulls(Object a, Object b) {
        return (a == null) ? (b == null) : a.equals(b);
    }

    /**
     * M: Operator INetworkControllerExt implements.
     */
    private class NetworkControllerExt implements INetworkControllerExt {
        private static final String TAG = "StatusBar.NetworkControllerExt";
        private boolean mDebug = !FeatureOptionUtils.isUserLoad();

        private final Config mConfig;

        public NetworkControllerExt(final Config config) {
            this.mConfig = config;
            config.hspaDataDistinguishable = PluginFactory.getStatusBarPlugin(mContext)
                    .customizeHspaDistinguishable(config.hspaDataDistinguishable);

            DataType.mapDataTypeSets(config.showAtLeast3G, config.show4gForLte,
                    config.hspaDataDistinguishable);
            NetworkType.mapNetworkTypeSets(config.showAtLeast3G, config.show4gForLte,
                    config.hspaDataDistinguishable);
        }

        @Override
        public boolean isShowAtLeast3G() {
            return mConfig.showAtLeast3G;
        }

        @Override
        public boolean isHspaDataDistinguishable() {
            return mConfig.hspaDataDistinguishable;
        }

        @Override
        public boolean hasMobileDataFeature() {
            return mHasMobileDataFeature;
        }

        @Override
        public Resources getResources() {
            return mContext.getResources();
        }

        @Override
        public void getDefaultSignalNullIcon(IconIdWrapper icon) {
            icon.setResources(mContext.getResources());
            icon.setIconId(TelephonyIcons.TELEPHONY_NO_NETWORK);
        }

        @Override
        public void getDefaultRoamingIcon(IconIdWrapper icon) {
            icon.setResources(mContext.getResources());
            icon.setIconId(TelephonyIcons.ROAMING_ICON);
        }

        @Override
        public boolean hasService(int subId) {
            final MobileSignalController controller = getMobileSignalController(subId);
            final boolean hasService = controller != null ? controller.hasService() : false;
            return hasService;
        }

        @Override
        public boolean isDataConnected(int subId) {
            final MobileSignalController controller = getMobileSignalController(subId);
            return controller != null ? controller.getState().dataConnected : false;
        }

        @Override
        public boolean isRoaming(int subId) {
            final MobileSignalController controller = getMobileSignalController(subId);
            final boolean isRoaming = controller != null ? controller.isRoaming() : false;
            return isRoaming;
        }

        @Override
        public int getSignalStrengthLevel(int subId) {
            final MobileSignalController controller = getMobileSignalController(subId);
            final int level = controller != null ? controller.mCurrentState.level
                    : SignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
            return level;
        }

        @Override
        public NetworkType getNetworkType(int subId) {
            final int dataNetType = getDataNetworkType(subId);
            return NetworkType.get(dataNetType);
        }

        @Override
        public DataType getDataType(int subId) {
            final MobileSignalController controller = getMobileSignalController(subId);
            final int dataNetType = controller != null ? controller.mDataNetType
                    : TelephonyManager.NETWORK_TYPE_UNKNOWN;
            return DataType.get(dataNetType);
        }

        @Override
        public int getDataActivity(int subId) {
            int activity = TelephonyManager.DATA_ACTIVITY_NONE;
            final MobileSignalController controller = getMobileSignalController(subId);
            if (controller != null && controller.mCurrentState.dataConnected) {
                if (controller.mCurrentState.activityIn
                        && controller.mCurrentState.activityOut) {
                    activity = TelephonyManager.DATA_ACTIVITY_INOUT;
                } else if (controller.mCurrentState.activityIn) {
                    activity = TelephonyManager.DATA_ACTIVITY_OUT;
                } else if (controller.mCurrentState.activityOut) {
                    activity = TelephonyManager.DATA_ACTIVITY_OUT;
                }
            }
            return activity;
        }

        @Override
        public boolean isLteTddSingleDataMode(int subId) {
            return false;
        }

        @Override
        public SvLteController getSvLteController(int subId) {
            final MobileSignalController controller = getMobileSignalController(subId);
            return controller != null ? controller.mSvLteController : null;
        }

        @Override
        public boolean isEmergencyOnly(int subId) {
            final MobileSignalController controller = getMobileSignalController(subId);
            boolean isOnlyEmergency = controller != null ? controller.isEmergencyOnly() : false;
            return isOnlyEmergency;
        }

        @Override
        public boolean isOffline(int subId) {
            boolean isOffline = false;
            final MobileSignalController controller = getMobileSignalController(subId);
            if (controller != null) {
                if (SvLteController.isMediatekSVLteDcSupport(controller.mSubscriptionInfo)) {
                    isOffline = controller.mSvLteController.isOffline(getNetworkType(subId));
                } else {
                    isOffline = controller.isEmergencyOnly();
                }
            }
            return isOffline;
        }

        @Override
        public boolean isRoamingGGMode() {
            boolean isRoamingGGMode = false;
            if (isBehaviorSet(BehaviorSet.OP09_BS)) {
                final SubscriptionInfo info = SIMHelper.getSubInfoBySlot(mContext,
                        PhoneConstants.SIM_ID_1);
                if (info != null) {
                    final MobileSignalController controller = getMobileSignalController(info
                            .getSubscriptionId());
                    isRoamingGGMode = controller != null ? !controller.isCdma() : false;
                }
            }
            if (mDebug) {
                Log.d(TAG, "isRoamingGGMode, slotId = " + PhoneConstants.SIM_ID_1
                        + ", isRoamingGGMode = " + isRoamingGGMode);
            }
            return isRoamingGGMode;
        }

        private final int getDataNetworkType(int subId) {
            // Big - Data
            int dataNetType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
            final MobileSignalController controller = getMobileSignalController(subId);
            if (controller != null) {
                if (mDebug) {
                    Log.d(TAG, "getDataNetworkType()"
                            + ", DataState = " + controller.mDataState
                            + ", ServiceState = " + controller.mServiceState);
                }

                int networkTypeData = controller.mDataNetType;
                final ServiceState serviceState = controller.mServiceState;
                if (serviceState != null) {
                    if (mDebug) {
                        Log.d(TAG, "getDataNetworkType: DataNetType = "
                                + controller.mDataNetType + " / "
                                + serviceState.getDataNetworkType());
                    }

                    if (controller.mDataState == TelephonyManager.DATA_UNKNOWN
                            || controller.mDataState == TelephonyManager.DATA_DISCONNECTED) {
                        networkTypeData = serviceState.getDataNetworkType();
                    }

                    final int cs = serviceState.getVoiceNetworkType();
                    final int ps = networkTypeData;
                    if (mDebug) {
                        Log.d(TAG, "getNWTypeByPriority(), CS = " + cs + ", PS = " + ps);
                    }
                    dataNetType = getNWTypeByPriority(cs, ps);
                } else {
                    dataNetType = networkTypeData;
                }

                // Support SVLTE.
                if (SvLteController.isMediatekSVLteDcSupport(controller.mSubscriptionInfo)) {
                    dataNetType = controller.mSvLteController
                            .getDataNetTypeWithLTEService(dataNetType);
                }

                Log.d(TAG, "getDataNetworkType: DataNetType="
                        + controller.mDataNetType + " / " + dataNetType);
            }

            return dataNetType;
        }

        private final int getNWTypeByPriority(int cs, int ps) {
            if (TelephonyManager.getNetworkClass(cs) > TelephonyManager.getNetworkClass(ps)) {
                return cs;
            } else {
                return ps;
            }
        }

        private final boolean isBehaviorSet(BehaviorSet behaviorSet) {
            return PluginFactory.getStatusBarPlugin(mContext).customizeBehaviorSet() == behaviorSet;
        }
    }

    /// M: Support "Operator plugin - Data activity/type, strength icon". @}
}
