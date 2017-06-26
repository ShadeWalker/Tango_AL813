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

package com.android.internal.telephony.dataconnection;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.EventLog;
import android.telephony.Rlog;

import com.android.internal.R;
import com.android.internal.telephony.dataconnection.DcFailCause;
import com.android.internal.telephony.dataconnection.DcAsyncChannel;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.ArrayUtils;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.dataconnection.DataSubSelector;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.PriorityQueue;

/** M: start */
import com.android.internal.telephony.RILConstants;
import com.mediatek.common.MPlugin;
import com.mediatek.common.telephony.ITelephonyExt;
import com.mediatek.internal.telephony.ITelephonyEx;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.net.Uri;
/** M: end */


//dual imsi
import android.net.Uri;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.util.Log;
//dual imsi end

/**
 * {@hide}
 */
public abstract class DcTrackerBase extends Handler {
    protected static final boolean DBG = true;
    protected static final boolean VDBG = true; // STOPSHIP if true
    protected static final boolean VDBG_STALL = true; // STOPSHIP if true
    protected static final boolean RADIO_TESTS = false;

    static boolean mIsCleanupRequired = false;
    /**
     * Constants for the data connection activity:
     * physical link down/up
     */
    protected static final int DATA_CONNECTION_ACTIVE_PH_LINK_INACTIVE = 0;
    protected static final int DATA_CONNECTION_ACTIVE_PH_LINK_DOWN = 1;
    protected static final int DATA_CONNECTION_ACTIVE_PH_LINK_UP = 2;

    /** Delay between APN attempts.
        Note the property override mechanism is there just for testing purpose only. */
    protected static final int APN_DELAY_DEFAULT_MILLIS = 20000;

    /** Delay between APN attempts when in fail fast mode */
    protected static final int APN_FAIL_FAST_DELAY_DEFAULT_MILLIS = 3000;

    AlarmManager mAlarmManager;

    protected Object mDataEnabledLock = new Object();

    // responds to the setInternalDataEnabled call - used internally to turn off data
    // for example during emergency calls
    protected boolean mInternalDataEnabled = true;

    // responds to public (user) API to enable/disable data use
    // independent of mInternalDataEnabled and requests for APN access
    // persisted
    protected boolean mUserDataEnabled = true;

    protected boolean mPolicyDataEnabled = true;

    private boolean[] mDataEnabled = new boolean[DctConstants.APN_NUM_TYPES];

    private int mEnabledCount = 0;

    /* Currently requested APN type (TODO: This should probably be a parameter not a member) */
    protected String mRequestedApnType = PhoneConstants.APN_TYPE_DEFAULT;

    /** Retry configuration: A doubling of retry times from 5secs to 30minutes */
    protected static final String DEFAULT_DATA_RETRY_CONFIG = "default_randomization=2000,"
        + "5000,10000,20000,40000,80000:5000,160000:5000,"
        + "320000:5000,640000:5000,1280000:5000,1800000:5000";

    /** Retry configuration for secondary networks: 4 tries in 20 sec */
    protected static final String SECONDARY_DATA_RETRY_CONFIG =
            "max_retries=3, 5000, 5000, 5000";

    /** Slow poll when attempting connection recovery. */
    protected static final int POLL_NETSTAT_SLOW_MILLIS = 5000;
    /** Default max failure count before attempting to network re-registration. */
    protected static final int DEFAULT_MAX_PDP_RESET_FAIL = 3;

    /**
     * After detecting a potential connection problem, this is the max number
     * of subsequent polls before attempting recovery.
     */
    protected static final int NO_RECV_POLL_LIMIT = 24;
    // 1 sec. default polling interval when screen is on.
    protected static final int POLL_NETSTAT_MILLIS = 1000;
    // 10 min. default polling interval when screen is off.
    protected static final int POLL_NETSTAT_SCREEN_OFF_MILLIS = 1000*60*10;
    // 2 min for round trip time
    protected static final int POLL_LONGEST_RTT = 120 * 1000;
    // Default sent packets without ack which triggers initial recovery steps
    protected static final int NUMBER_SENT_PACKETS_OF_HANG = 10;
    // how long to wait before switching back to default APN
    protected static final int RESTORE_DEFAULT_APN_DELAY = 1 * 60 * 1000;
    // system property that can override the above value
    protected static final String APN_RESTORE_DELAY_PROP_NAME = "android.telephony.apn-restore";
    // represents an invalid IP address
    protected static final String NULL_IP = "0.0.0.0";

    // Default for the data stall alarm while non-aggressive stall detection
    protected static final int DATA_STALL_ALARM_NON_AGGRESSIVE_DELAY_IN_MS_DEFAULT = 1000 * 60 * 6;
    // Default for the data stall alarm for aggressive stall detection
    protected static final int DATA_STALL_ALARM_AGGRESSIVE_DELAY_IN_MS_DEFAULT = 1000 * 60;
    // If attempt is less than this value we're doing first level recovery
    protected static final int DATA_STALL_NO_RECV_POLL_LIMIT = 1;
    // Tag for tracking stale alarms
    protected static final String DATA_STALL_ALARM_TAG_EXTRA = "data.stall.alram.tag";

    protected static final boolean DATA_STALL_SUSPECTED = true;
    protected static final boolean DATA_STALL_NOT_SUSPECTED = false;

    protected String RADIO_RESET_PROPERTY = "gsm.radioreset";

    protected static final String INTENT_RECONNECT_ALARM =
            "com.android.internal.telephony.data-reconnect";
    protected static final String INTENT_RECONNECT_ALARM_EXTRA_TYPE = "reconnect_alarm_extra_type";
    protected static final String INTENT_RECONNECT_ALARM_EXTRA_REASON =
            "reconnect_alarm_extra_reason";

    protected static final String INTENT_RESTART_TRYSETUP_ALARM =
            "com.android.internal.telephony.data-restart-trysetup";
    protected static final String INTENT_RESTART_TRYSETUP_ALARM_EXTRA_TYPE =
            "restart_trysetup_alarm_extra_type";

    protected static final String INTENT_DATA_STALL_ALARM =
            "com.android.internal.telephony.data-stall";



    protected static final String DEFALUT_DATA_ON_BOOT_PROP = "net.def_data_on_boot";

    protected DcTesterFailBringUpAll mDcTesterFailBringUpAll;
    protected DcController mDcc;

    // member variables
    protected PhoneBase mPhone;
    protected UiccController mUiccController;
    protected AtomicReference<IccRecords> mIccRecords = new AtomicReference<IccRecords>();
    protected AtomicReference<UiccCardApplication> mUiccCardApplication
            = new AtomicReference<UiccCardApplication>();
    protected DctConstants.Activity mActivity = DctConstants.Activity.NONE;
    protected DctConstants.State mState = DctConstants.State.IDLE;
    protected Handler mDataConnectionTracker = null;

    protected long mTxPkts;
    protected long mRxPkts;
    protected int mNetStatPollPeriod;
    protected boolean mNetStatPollEnabled = false;

    protected TxRxSum mDataStallTxRxSum = new TxRxSum(0, 0);
    // Used to track stale data stall alarms.
    protected int mDataStallAlarmTag = (int) SystemClock.elapsedRealtime();
    // The current data stall alarm intent
    protected PendingIntent mDataStallAlarmIntent = null;
    // Number of packets sent since the last received packet
    protected long mSentSinceLastRecv;
    // Controls when a simple recovery attempt it to be tried
    protected int mNoRecvPollCount = 0;
    // Refrence counter for enabling fail fast
    protected static int sEnableFailFastRefCounter = 0;
    // True if data stall detection is enabled
    protected volatile boolean mDataStallDetectionEnabled = true;

    protected volatile boolean mFailFast = false;

    // True when in voice call
    protected boolean mInVoiceCall = false;

    // wifi connection status will be updated by sticky intent
    protected boolean mIsWifiConnected = false;

    /** Intent sent when the reconnect alarm fires. */
    protected PendingIntent mReconnectIntent = null;

    /** CID of active data connection */
    protected int mCidActive;

    // When false we will not auto attach and manually attaching is required.
    protected boolean mAutoAttachOnCreationConfig = false;
    protected boolean mAutoAttachOnCreation = false;

    // State of screen
    // (TODO: Reconsider tying directly to screen, maybe this is
    //        really a lower power mode")
    protected boolean mIsScreenOn = true;

    /** Allows the generation of unique Id's for DataConnection objects */
    protected AtomicInteger mUniqueIdGenerator = new AtomicInteger(0);

    /** The data connections. */
    protected HashMap<Integer, DataConnection> mDataConnections =
        new HashMap<Integer, DataConnection>();

    /** The data connection async channels */
    protected HashMap<Integer, DcAsyncChannel> mDataConnectionAcHashMap =
        new HashMap<Integer, DcAsyncChannel>();

    /** Convert an ApnType string to Id (TODO: Use "enumeration" instead of String for ApnType) */
    protected HashMap<String, Integer> mApnToDataConnectionId =
                                    new HashMap<String, Integer>();

    /** Phone.APN_TYPE_* ===> ApnContext */
    protected final ConcurrentHashMap<String, ApnContext> mApnContexts =
                                    new ConcurrentHashMap<String, ApnContext>();

    /** kept in sync with mApnContexts
     * Higher numbers are higher priority and sorted so highest priority is first */
   /* ALPS01555724: The implementation of PriorityQueue is incorrect, use arraylist to sort priority.
    protected final PriorityQueue<ApnContext>mPrioritySortedApnContexts =
            new PriorityQueue<ApnContext>(5,
            new Comparator<ApnContext>() {
                public int compare(ApnContext c1, ApnContext c2) {
                    return c2.priority - c1.priority;
                }
            } );
     */
    ArrayList <ApnContext> mPrioritySortedApnContexts = new ArrayList<ApnContext>();

    /* Currently active APN */
    protected ApnSetting mActiveApn;

    /** allApns holds all apns */
    protected ArrayList<ApnSetting> mAllApnSettings = null;

    /** preferred apn */
    protected ApnSetting mPreferredApn = null;

    /** Is packet service restricted by network */
    protected boolean mIsPsRestricted = false;

    /** emergency apn Setting*/
    protected ApnSetting mEmergencyApn = null;

    /* Once disposed dont handle any messages */
    protected boolean mIsDisposed = false;

    protected ContentResolver mResolver;

    /* Set to true with CMD_ENABLE_MOBILE_PROVISIONING */
    protected boolean mIsProvisioning = false;

    /* The Url passed as object parameter in CMD_ENABLE_MOBILE_PROVISIONING */
    protected String mProvisioningUrl = null;

    /* Intent for the provisioning apn alarm */
    protected static final String INTENT_PROVISIONING_APN_ALARM =
            "com.android.internal.telephony.provisioning_apn_alarm";

    /* Tag for tracking stale alarms */
    protected static final String PROVISIONING_APN_ALARM_TAG_EXTRA = "provisioning.apn.alarm.tag";

    /* Debug property for overriding the PROVISIONING_APN_ALARM_DELAY_IN_MS */
    protected static final String DEBUG_PROV_APN_ALARM =
            "persist.debug.prov_apn_alarm";

    /* Default for the provisioning apn alarm timeout */
    protected static final int PROVISIONING_APN_ALARM_DELAY_IN_MS_DEFAULT = 1000 * 60 * 15;

    /* The provision apn alarm intent used to disable the provisioning apn */
    protected PendingIntent mProvisioningApnAlarmIntent = null;

    /* Used to track stale provisioning apn alarms */
    protected int mProvisioningApnAlarmTag = (int) SystemClock.elapsedRealtime();

    protected AsyncChannel mReplyAc = new AsyncChannel();

    /** M: start */
    protected static final String PROPERTY_MOBILE_DATA_ENABLE = "persist.radio.mobile.data";
    protected static final boolean DUALTALK_SPPORT =
            SystemProperties.getInt("ro.mtk_dt_support", 0) == 1;
    protected ApnSetting mInitialAttachApnSetting;
    protected Handler mWorkerHandler;
    private static final String NO_SIM_VALUE = "N/A";
    private String[] PROPERTY_ICCID = {
        "ril.iccid.sim1",
        "ril.iccid.sim2",
        "ril.iccid.sim3",
        "ril.iccid.sim4",
    };

    /** M: end */

    private static final boolean MTK_DUAL_APN_SUPPORT =
            SystemProperties.get("ro.mtk_dtag_dual_apn_support").equals("1") ? true : false;

    // ensure Settings.Global.MOBILE_DATA is updated.
    protected int mSettingProviderRetryCount = 0;

    // M: VoLTE Start
    protected String mSetDataAllowedReason = "";
    // M: VoLTE End

    /// M: Telephony plugin
    private static final boolean BSP_PACKAGE =
            SystemProperties.getBoolean("ro.mtk_bsp_package", false);
    ITelephonyExt mTelephonyExt;

    // M: Fix google issue to support SIM hot plugging
    protected boolean mIsSubInfoNotReadyWhenRecordsLoaded = false;

    // M: [C2K][IRAT] Record initial attach APN for SVLTE, distinguish with
    // original initial attach APN.
    // TODO: move C2K logic to OP09 if it is not OM request.
    protected static final String OPERATOR_NUMERIC_CTLTE = "46011";
    protected static final String OPERATOR_NUMERIC_VODAFONE = "20404";
    protected static final String OPERATOR_NUMERIC_HUTCHISON = "45403";

    protected String mSvlteOperatorNumeric;
    protected ApnSetting mSvlteIaApnSetting;
    protected boolean mHasPendingInitialApnRequest;
    protected boolean mIsDuringIrat;

    // M: Attach APN is assigned empty but need to raise P-CSCF discovery flag
    // 26201 DTAG D1(T-Mobile)
    // 44010 DOCOMO
    private String[] PLMN_EMPTY_APN_PCSCF_SET = {
        "26201",
        "44010"
    };

    // M: [LTE][Low Power][UL traffic shaping] Start
    protected String mLteAccessStratumDataState = PhoneConstants.LTE_ACCESS_STRATUM_STATE_UNKNOWN;
    protected static final int mLteAsConnected = 1;
    protected int mNetworkType = -1;
    protected boolean mIsLte = false;
    protected boolean mSharedDefaultApn = false;
    protected int mDefaultRefCount = 0;
    // M: [LTE][Low Power][UL traffic shaping] End

    protected BroadcastReceiver mIntentReceiver = new BroadcastReceiver ()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if (DBG) log("onReceive: action=" + action);
            if (action.equals(Intent.ACTION_SCREEN_ON)) {
                mIsScreenOn = true;
                stopNetStatPoll();
                startNetStatPoll();
                restartDataStallAlarm();
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                mIsScreenOn = false;
                stopNetStatPoll();
                startNetStatPoll();
                restartDataStallAlarm();
            } else if (action.startsWith(INTENT_RECONNECT_ALARM)) {
                //int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                //        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                //if (subId == mPhone.getSubId()) {
                    if (DBG) log("Reconnect alarm. Previous state was " + mState);
                    onActionIntentReconnectAlarm(intent);
                //}
            } else if (action.startsWith(INTENT_RESTART_TRYSETUP_ALARM)) {
                if (DBG) log("Restart trySetup alarm");
                onActionIntentRestartTrySetupAlarm(intent);
            } else if (action.equals(INTENT_DATA_STALL_ALARM)) {
                onActionIntentDataStallAlarm(intent);
            } else if (action.equals(INTENT_PROVISIONING_APN_ALARM)) {
                onActionIntentProvisioningApnAlarm(intent);
            } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                final android.net.NetworkInfo networkInfo = (NetworkInfo)
                        intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                mIsWifiConnected = (networkInfo != null && networkInfo.isConnected());
                if (DBG) log("NETWORK_STATE_CHANGED_ACTION: mIsWifiConnected=" + mIsWifiConnected);
            } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                final boolean enabled = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN) == WifiManager.WIFI_STATE_ENABLED;

                if (!enabled) {
                    // when WiFi got disabled, the NETWORK_STATE_CHANGED_ACTION
                    // quit and won't report disconnected until next enabling.
                    mIsWifiConnected = false;
                }
                if (DBG) log("WIFI_STATE_CHANGED_ACTION: enabled=" + enabled
                        + " mIsWifiConnected=" + mIsWifiConnected);
            } else if (action.equals(TelephonyIntents.ACTION_CLEAR_DATA_BEARER_NOTIFY)) {
                int phoneIdForIMS = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                        mPhone.getPhoneId());
                if (!SubscriptionManager.isValidPhoneId(phoneIdForIMS)) {
                    phoneIdForIMS = 0;
                }
                if (phoneIdForIMS != mPhone.getPhoneId()) {
                    log("skip clearDataBearer(), cause phoneIdForIMS = " + phoneIdForIMS
                            + " not equal to current phoneId = " + mPhone.getPhoneId());
                } else {
                    clearDataBearer();
                }
            } else if (action.equals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED)) {
                // M: Fix google issue to support SIM hot plugging
                onActionIntentSubinfoRecordUpdated();
            }
        }
    };

    private Runnable mPollNetStat = new Runnable()
    {
        @Override
        public void run() {
            updateDataActivity();

            if (mIsScreenOn) {
                mNetStatPollPeriod = Settings.Global.getInt(mResolver,
                        Settings.Global.PDP_WATCHDOG_POLL_INTERVAL_MS, POLL_NETSTAT_MILLIS);
            } else {
                mNetStatPollPeriod = Settings.Global.getInt(mResolver,
                        Settings.Global.PDP_WATCHDOG_LONG_POLL_INTERVAL_MS,
                        POLL_NETSTAT_SCREEN_OFF_MILLIS);
            }

            if (mNetStatPollEnabled) {
                mDataConnectionTracker.postDelayed(this, mNetStatPollPeriod);
            }
        }
    };
    
    //modify 2015-09-10  FDN_modify start
    //protected static final Uri FDN_CONTENT_URI = Uri.parse("content://icc/fdn");
    //protected static final Uri FDN_CONTENT_PATH_WITH_SUB_ID = Uri.parse("content://icc/fdn/subId/");
    protected FdnChangeObserver mFdnObserver;
    
     /**
     * Handles changes of the FDN db.
     */
    private class FdnChangeObserver extends ContentObserver {
        public FdnChangeObserver() {
            super(mWorkerHandler);
        }

        @Override
        public void onChange(boolean selfChange) {
            mWorkerHandler.sendEmptyMessage(DctConstants.EVENT_CHECK_FDN_LIST);
        }
    }
    //MTK END: Support FDN
    //modify 2015-09-10  FDN_modify end
    
    private SubscriptionManager mSubscriptionManager;
    private final OnSubscriptionsChangedListener mOnSubscriptionsChangedListener =
            new OnSubscriptionsChangedListener() {
        /**
         * Callback invoked when there is any change to any SubscriptionInfo. Typically
         * this method would invoke {@link SubscriptionManager#getActiveSubscriptionInfoList}
         */
        @Override
        public void onSubscriptionsChanged() {
            if (DBG) log("#onSubscriptionsChanged# SubscriptionListener.onSubscriptionInfoChanged start");
            // Set the network type, in case the radio does not restore it.
            int subId = mPhone.getSubId();
            if (SubscriptionManager.isValidSubscriptionId(subId)) {
                if (mDataRoamingSettingObserver != null) {
                    mDataRoamingSettingObserver.unregister();
                }
                // Watch for changes to Settings.Global.DATA_ROAMING
                mDataRoamingSettingObserver = new DataRoamingSettingObserver(mPhone,
                        mPhone.getContext());
                mDataRoamingSettingObserver.register();

                // Watch for changes to fdn list
                if (mFdnContentObserver != null) {
                    mFdnContentObserver.unregister();
                }
                mFdnContentObserver = new FdnContentObserver(mPhone);
                mFdnContentObserver.register();
            }
            log("#onSubscriptionsChanged# SubscriptionListener.onSubscriptionInfoChanged end");
        }
    };

    private class DataRoamingSettingObserver extends ContentObserver {

        public DataRoamingSettingObserver(Handler handler, Context context) {
            super(handler);
            mResolver = context.getContentResolver();
        }

        public void register() {
            String contentUri;
            if (TelephonyManager.getDefault().getSimCount() == 1) {
                contentUri = Settings.Global.DATA_ROAMING;
            } else {
                int phoneSubId = mPhone.getSubId();
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                    phoneSubId = SvlteUtils.getSvlteSubIdBySubId(phoneSubId);
                }
                contentUri = Settings.Global.DATA_ROAMING + phoneSubId;
            }

            mResolver.registerContentObserver(Settings.Global.getUriFor(contentUri), false, this);
        }

        public void unregister() {
            mResolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            // already running on mPhone handler thread
            if (mPhone.getServiceState().getDataRoaming()) {
                // M: handle data roaming settings access
                sendMessage(obtainMessage(DctConstants.EVENT_ROAMING_ON));
            } else {
                sendMessage(obtainMessage(DctConstants.EVENT_ROAMING_OFF));
            }
        }
    }
    private DataRoamingSettingObserver mDataRoamingSettingObserver;

    protected static final String FDN_CONTENT_URI = "content://icc/fdn";
    protected static final String FDN_CONTENT_PATH_WITH_SUB_ID = "content://icc/fdn/subId/";

    /**
     * Handles changes of the FDN db.
     */
    private class FdnContentObserver extends ContentObserver {
        public FdnContentObserver(Handler handler) {
            super(handler);
        }

        public void register() {
            Uri fdnContentUri;
            if (TelephonyManager.getDefault().getSimCount() == 1) {
                fdnContentUri = Uri.parse(FDN_CONTENT_URI);
            } else {
                int nSubId = mPhone.getSubId();
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                    nSubId = SvlteUtils.getSvlteSubIdBySubId(nSubId);
                }
                fdnContentUri = Uri.parse(FDN_CONTENT_PATH_WITH_SUB_ID + nSubId);
            }
            mResolver.registerContentObserver(fdnContentUri, false, this);
        }

        public void unregister() {
            mResolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            sendMessage(obtainMessage(DctConstants.EVENT_FDN_CHANGED));
        }
    }
    private FdnContentObserver mFdnContentObserver = null;

    /**
     * The Initial MaxRetry sent to a DataConnection as a parameter
     * to DataConnectionAc.bringUp. This value can be defined at compile
     * time using the SystemProperty Settings.Global.DCT_INITIAL_MAX_RETRY
     * and at runtime using gservices to change Settings.Global.DCT_INITIAL_MAX_RETRY.
     */
    private static final int DEFAULT_MDC_INITIAL_RETRY = 1;
    protected int getInitialMaxRetry() {
        if (mFailFast) {
            return 0;
        }
        // Get default value from system property or use DEFAULT_MDC_INITIAL_RETRY
        int value = SystemProperties.getInt(
                Settings.Global.MDC_INITIAL_MAX_RETRY, DEFAULT_MDC_INITIAL_RETRY);

        // Check if its been overridden
        return Settings.Global.getInt(mResolver,
                Settings.Global.MDC_INITIAL_MAX_RETRY, value);
    }

    /**
     * Maintain the sum of transmit and receive packets.
     *
     * The packet counts are initialized and reset to -1 and
     * remain -1 until they can be updated.
     */
    public class TxRxSum {
        public long txPkts;
        public long rxPkts;

        public TxRxSum() {
            reset();
        }

        public TxRxSum(long txPkts, long rxPkts) {
            this.txPkts = txPkts;
            this.rxPkts = rxPkts;
        }

        public TxRxSum(TxRxSum sum) {
            txPkts = sum.txPkts;
            rxPkts = sum.rxPkts;
        }

        public void reset() {
            txPkts = -1;
            rxPkts = -1;
        }

        @Override
        public String toString() {
            return "{txSum=" + txPkts + " rxSum=" + rxPkts + "}";
        }

        public void updateTxRxSum() {
            this.txPkts = TrafficStats.getMobileTcpTxPackets();
            this.rxPkts = TrafficStats.getMobileTcpRxPackets();
        }
    }

    protected void onActionIntentReconnectAlarm(Intent intent) {
        String reason = intent.getStringExtra(INTENT_RECONNECT_ALARM_EXTRA_REASON);
        String apnType = intent.getStringExtra(INTENT_RECONNECT_ALARM_EXTRA_TYPE);

        // M: modify for svlte, if svlte, need check the real valid subId
        int phoneSubId = mPhone.getSubId();
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            phoneSubId = SvlteUtils.getSvlteSubIdBySubId(phoneSubId);
        }
        int currSubId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        log("onActionIntentReconnectAlarm: currSubId = " + currSubId + " phoneSubId=" + phoneSubId);

        // Stop reconnect if not current subId is not correct.
        // FIXME STOPSHIP - phoneSubId is coming up as -1 way after boot and failing this?
        if (!SubscriptionManager.isValidSubscriptionId(currSubId) || (currSubId != phoneSubId)) {
            log("receive ReconnectAlarm but subId incorrect, ignore");
            return;
        }

        ApnContext apnContext = mApnContexts.get(apnType);

        if (DBG) {
            log("onActionIntentReconnectAlarm: mState=" + mState + " reason=" + reason +
                    " apnType=" + apnType + " apnContext=" + apnContext +
                    " mDataConnectionAsyncChannels=" + mDataConnectionAcHashMap);
        }

        if ((apnContext != null) && (apnContext.isEnabled())) {
            apnContext.setReason(reason);
            DctConstants.State apnContextState = apnContext.getState();
            if (DBG) {
                log("onActionIntentReconnectAlarm: apnContext state=" + apnContextState);
            }
            if ((apnContextState == DctConstants.State.FAILED)
                    || (apnContextState == DctConstants.State.IDLE)) {
                if (DBG) {
                    log("onActionIntentReconnectAlarm: state is FAILED|IDLE, disassociate");
                }
                DcAsyncChannel dcac = apnContext.getDcAc();
                if (dcac != null) {
                    if (DBG) {
                        log("onActionIntentReconnectAlarm: tearDown apnContext=" + apnContext);
                    }
                    dcac.tearDown(apnContext, "", null);
                }
                apnContext.setDataConnectionAc(null);
                apnContext.setState(DctConstants.State.IDLE);
            } else {
                if (DBG) log("onActionIntentReconnectAlarm: keep associated");
            }
            // TODO: IF already associated should we send the EVENT_TRY_SETUP_DATA???
            sendMessage(obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, apnContext));

            apnContext.setReconnectIntent(null);
        }
    }

    protected void onActionIntentRestartTrySetupAlarm(Intent intent) {
        String apnType = intent.getStringExtra(INTENT_RESTART_TRYSETUP_ALARM_EXTRA_TYPE);
        ApnContext apnContext = mApnContexts.get(apnType);
        if (DBG) {
            log("onActionIntentRestartTrySetupAlarm: mState=" + mState +
                    " apnType=" + apnType + " apnContext=" + apnContext +
                    " mDataConnectionAsyncChannels=" + mDataConnectionAcHashMap);
        }
        sendMessage(obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, apnContext));
    }

    protected void onActionIntentDataStallAlarm(Intent intent) {
        if (VDBG_STALL) log("onActionIntentDataStallAlarm: action=" + intent.getAction());
        Message msg = obtainMessage(DctConstants.EVENT_DATA_STALL_ALARM,
                intent.getAction());
        msg.arg1 = intent.getIntExtra(DATA_STALL_ALARM_TAG_EXTRA, 0);
        sendMessage(msg);
    }

    // M: Fix google issue to support SIM hot plugging
    protected void onActionIntentSubinfoRecordUpdated() {
        if (mIsSubInfoNotReadyWhenRecordsLoaded) {
            if (DBG) {
                log("onActionIntentSubinfoRecordUpdated: subinfo not ready" +
                        " when records loaded before" + ", triggers EVENT_RECORDS_LOADED again");
            }
            mIsSubInfoNotReadyWhenRecordsLoaded = false;
            sendMessage(obtainMessage(DctConstants.EVENT_RECORDS_LOADED));
        }
    }

    ConnectivityManager mCm;

    /**
     * Default constructor
     */
    protected DcTrackerBase(PhoneBase phone) {
        super();
        mPhone = phone;
        if (DBG) log("DCT.constructor");
        mResolver = mPhone.getContext().getContentResolver();
        mUiccController = UiccController.getInstance();
        mUiccController.registerForIccChanged(this, DctConstants.EVENT_ICC_CHANGED, null);
        mAlarmManager =
                (AlarmManager) mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
        mCm = (ConnectivityManager) mPhone.getContext().getSystemService(
                Context.CONNECTIVITY_SERVICE);


        int phoneSubId = mPhone.getSubId();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(INTENT_DATA_STALL_ALARM);
        filter.addAction(INTENT_PROVISIONING_APN_ALARM);

        // M: Fix google issue to support SIM hot plugging
        filter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);

        mUserDataEnabled = getDataEnabled();

        notifyMobileDataChange(mUserDataEnabled ? 1 : 0);

        mPhone.getContext().registerReceiver(mIntentReceiver, filter, null, mPhone);

        // This preference tells us 1) initial condition for "dataEnabled",
        // and 2) whether the RIL will setup the baseband to auto-PS attach.

        mDataEnabled[DctConstants.APN_DEFAULT_ID] =
                SystemProperties.getBoolean(DEFALUT_DATA_ON_BOOT_PROP,true);
        if (mDataEnabled[DctConstants.APN_DEFAULT_ID]) {
            mEnabledCount++;
        }

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mPhone.getContext());
        mAutoAttachOnCreation = sp.getBoolean(PhoneBase.DATA_DISABLED_ON_BOOT_KEY, false);

        mSubscriptionManager = SubscriptionManager.from(mPhone.getContext());
        mSubscriptionManager
                .addOnSubscriptionsChangedListener(mOnSubscriptionsChangedListener);

        HandlerThread dcHandlerThread = new HandlerThread("DcHandlerThread");
        dcHandlerThread.start();
        Handler dcHandler = new Handler(dcHandlerThread.getLooper());
        mDcc = DcController.makeDcc(mPhone, this, dcHandler);
        mDcTesterFailBringUpAll = new DcTesterFailBringUpAll(mPhone, dcHandler);

        if (DBG) { log("DualApnSupport = " + MTK_DUAL_APN_SUPPORT); }

        //MTK START: Add Plug in
        if (!BSP_PACKAGE) {
            try {
                mTelephonyExt = MPlugin.createInstance(ITelephonyExt.class.getName(),
                        mPhone.getContext());
                mTelephonyExt.init(mPhone.getContext());
            } catch (Exception e) {
                if (DBG) {
                    log("mTelephonyExt init fail");
                }
                e.printStackTrace();
            }
        }
        //MTK END
    }

    public void dispose() {
        if (DBG) log("DCT.dispose");
        for (DcAsyncChannel dcac : mDataConnectionAcHashMap.values()) {
            dcac.disconnect();
        }
        mDataConnectionAcHashMap.clear();
        mIsDisposed = true;
        mPhone.getContext().unregisterReceiver(mIntentReceiver);
        mUiccController.unregisterForIccChanged(this);
        if (mDataRoamingSettingObserver != null) {
            mDataRoamingSettingObserver.unregister();
        }

        if (mFdnContentObserver != null) {
            mFdnContentObserver.unregister();
        }
        mSubscriptionManager
                .removeOnSubscriptionsChangedListener(mOnSubscriptionsChangedListener);
        mDcc.dispose();
        mDcTesterFailBringUpAll.dispose();
    }

    public long getSubId() {
        return mPhone.getSubId();
    }

    public DctConstants.Activity getActivity() {
        return mActivity;
    }

    void setActivity(DctConstants.Activity activity) {
        log("setActivity = " + activity);
        mActivity = activity;
        mPhone.notifyDataActivity();
    }

    public void incApnRefCount(String name) {

    }

    public void decApnRefCount(String name) {

    }

    public boolean isApnSupported(String name) {
        return false;
    }

    public int getApnPriority(String name) {
        return -1;
    }


    public boolean isApnTypeActive(String type) {
        // TODO: support simultaneous with List instead
        if (PhoneConstants.APN_TYPE_DUN.equals(type)) {
            ApnSetting dunApn = fetchDunApn();
            if (dunApn != null) {
                return ((mActiveApn != null) && (dunApn.toString().equals(mActiveApn.toString())));
            }
        }
        return mActiveApn != null && mActiveApn.canHandleType(type);
    }

    protected ApnSetting fetchDunApn() {
        if (SystemProperties.getBoolean("net.tethering.noprovisioning", false)) {
            log("fetchDunApn: net.tethering.noprovisioning=true ret: null");
            return null;
        }
        int bearer = -1;
        ApnSetting retDunSetting = null;
        String apnData = Settings.Global.getString(mResolver, Settings.Global.TETHER_DUN_APN);
        List<ApnSetting> dunSettings = ApnSetting.arrayFromString(apnData);
        IccRecords r = mIccRecords.get();
        for (ApnSetting dunSetting : dunSettings) {
            String operator = (r != null) ? r.getOperatorNumeric() : "";

			///HQ_xionghaifeng 20151222 add for Roaming Broker start
			operator = mapToMainPlmnIfNeeded(operator);
			///HQ_xionghaifeng 20151222 add for Roaming Broker end
		
            if (dunSetting.bearer != 0) {
                if (bearer == -1) bearer = mPhone.getServiceState().getRilDataRadioTechnology();
                if (dunSetting.bearer != bearer) continue;
            }
            if (dunSetting.numeric.equals(operator)) {
                if (dunSetting.hasMvnoParams()) {
                    if (r != null &&
                            mvnoMatches(r, dunSetting.mvnoType, dunSetting.mvnoMatchData)) {
                        if (VDBG) {
                            log("fetchDunApn: global TETHER_DUN_APN dunSetting=" + dunSetting);
                        }
                        return dunSetting;
                    }
                } else {
                    if (VDBG) log("fetchDunApn: global TETHER_DUN_APN dunSetting=" + dunSetting);
                    return dunSetting;
                }
            }
        }

        Context c = mPhone.getContext();
        String[] apnArrayData = c.getResources().getStringArray(R.array.config_tether_apndata);
        for (String apn : apnArrayData) {
            ApnSetting dunSetting = ApnSetting.fromString(apn);
            if (dunSetting != null) {
                if (dunSetting.bearer != 0) {
                    if (bearer == -1) bearer = mPhone.getServiceState().getRilDataRadioTechnology();
                    if (dunSetting.bearer != bearer) continue;
                }
                if (dunSetting.hasMvnoParams()) {
                    if (r != null &&
                            mvnoMatches(r, dunSetting.mvnoType, dunSetting.mvnoMatchData)) {
                        if (VDBG) log("fetchDunApn: config_tether_apndata mvno dunSetting="
                                + dunSetting);
                        return dunSetting;
                    }
                } else {
                    retDunSetting = dunSetting;
                }
            }
        }

        if (VDBG) log("fetchDunApn: config_tether_apndata dunSetting=" + retDunSetting);
        return retDunSetting;
    }

    public boolean hasMatchedTetherApnSetting() {
        ApnSetting matched = fetchDunApn();
        log("hasMatchedTetherApnSetting: APN=" + matched);
        return matched != null;
    }

    public String[] getActiveApnTypes() {
        String[] result;
        if (mActiveApn != null) {
            result = mActiveApn.types;
        } else {
            result = new String[1];
            result[0] = PhoneConstants.APN_TYPE_DEFAULT;
        }
        return result;
    }

    /** TODO: See if we can remove */
    public String getActiveApnString(String apnType) {
        String result = null;
        if (mActiveApn != null) {
            result = mActiveApn.apn;
        }
        return result;
    }

    /**
     * Modify {@link android.provider.Settings.Global#DATA_ROAMING} value.
     */
    public void setDataOnRoamingEnabled(boolean enabled) {
        int phoneSubId = mPhone.getSubId();
        if (getDataOnRoamingEnabled() != enabled) {
            int roaming = enabled ? 1 : 0;

            // For single SIM phones, this is a per phone property.
            if (TelephonyManager.getDefault().getSimCount() == 1) {
                Settings.Global.putInt(mResolver, Settings.Global.DATA_ROAMING, roaming);
            } else {
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                    phoneSubId = SvlteUtils.getSvlteSubIdBySubId(phoneSubId);
                }
                Settings.Global.putInt(mResolver, Settings.Global.DATA_ROAMING + phoneSubId, roaming);
            }

            mSubscriptionManager.setDataRoaming(roaming, phoneSubId);
            // will trigger handleDataOnRoamingChange() through observer
            if (DBG) {
               log("setDataOnRoamingEnabled: set phoneSubId=" + phoneSubId
                       + " isRoaming=" + enabled);
            }
        } else {
            if (DBG) {
                log("setDataOnRoamingEnabled: unchanged phoneSubId=" + phoneSubId
                        + " isRoaming=" + enabled);
             }
        }
        /*HQ_yulifeng for save roaming state, */
        int phoneId = mPhone.getPhoneId();
        boolean saveSuccess = false;
        if(phoneId == 0){
            saveSuccess = Settings.Global.putInt(mPhone.getContext().getContentResolver(),
				Settings.System.FIRST_INTERNATIONAL_ROAM_STATE_SIM1, enabled ? 1 : 0);
        }else if(phoneId == 1){
            saveSuccess = Settings.Global.putInt(mPhone.getContext().getContentResolver(),
				Settings.System.FIRST_INTERNATIONAL_ROAM_STATE_SIM2, enabled ? 1 : 0);
        }
        if (DBG)log("ylf[setDataOnRoamingEnabled]phoneId=" + phoneId + ";saveSuccess=" + saveSuccess+";enabled="+enabled);		
    }

    /**
     * Return current {@link android.provider.Settings.Global#DATA_ROAMING} value.
     */
    public boolean getDataOnRoamingEnabled() {
        boolean isDataRoamingEnabled = "true".equalsIgnoreCase(SystemProperties.get(
                "ro.com.android.dataroaming", "false"));
        int phoneSubId = mPhone.getSubId();
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            phoneSubId = SvlteUtils.getSvlteSubIdBySubId(phoneSubId);
        }

        try {
            // For single SIM phones, this is a per phone property.
            if (TelephonyManager.getDefault().getSimCount() == 1) {
                isDataRoamingEnabled = Settings.Global.getInt(mResolver,
                        Settings.Global.DATA_ROAMING, isDataRoamingEnabled ? 1 : 0) != 0;
            } else {
                isDataRoamingEnabled = TelephonyManager.getIntWithSubId(mResolver,
                        Settings.Global.DATA_ROAMING, phoneSubId) != 0;
            }
        } catch (SettingNotFoundException snfe) {
            if (DBG) log("getDataOnRoamingEnabled: SettingNofFoundException snfe=" + snfe);
        }
        if (DBG) {
            log("getDataOnRoamingEnabled: phoneSubId=" + phoneSubId +
                    " isDataRoamingEnabled=" + isDataRoamingEnabled);
        }
        return isDataRoamingEnabled;
    }

    protected boolean ignoreDataRoaming(String apnType) {
        boolean ignoreDataRoaming = false;
        try {
            ignoreDataRoaming = mTelephonyExt.ignoreDataRoaming(apnType);
        } catch (Exception e) {
            loge("get ignoreDataRoaming fail!");
            e.printStackTrace();
        }
        if (ignoreDataRoaming) {
            log("ignoreDataRoaming: " + ignoreDataRoaming + ", apnType = " + apnType);
        }
        return ignoreDataRoaming;
    }

    /**
     * Modify {@link android.provider.Settings.Global#MOBILE_DATA} value.
     */
    public void setDataEnabled(boolean enable) {
        Message msg = obtainMessage(DctConstants.CMD_SET_USER_DATA_ENABLE);
        msg.arg1 = enable ? 1 : 0;
        if (DBG) log("setDataEnabled: sendMessage: enable=" + enable);
        sendMessage(msg);

        // M: cc33 notify modem the data on/off state
        mPhone.mCi.setDataOnToMD(enable, null);
    }

    /**
     * Return current {@link android.provider.Settings.Global#MOBILE_DATA} value.
     */
    public boolean getDataEnabled() {
        int phoneSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        boolean retVal = "true".equalsIgnoreCase(SystemProperties.get(
                "ro.com.android.mobiledata", "false"));
        try {
            if (TelephonyManager.getDefault().getSimCount() == 1) {
                retVal = Settings.Global.getInt(mResolver, Settings.Global.MOBILE_DATA,
                        retVal ? 1 : 0) != 0;
            } else {
                phoneSubId = mPhone.getSubId();

                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                    phoneSubId = SvlteUtils.getSvlteSubIdBySubId(phoneSubId);
                }

                log("phoneSubId = " + phoneSubId);

                retVal = Settings.Global.getInt(mResolver,
                        Settings.Global.MOBILE_DATA + phoneSubId) != 0;
            }
            if (DBG) {
                log("getDataEnabled: getInt retVal=" + retVal);
            }
        } catch (SettingNotFoundException snfe) {
            if (!SubscriptionManager.isValidSubscriptionId(phoneSubId)
                    && !(TelephonyManager.getDefault().getSimCount() == 1)) {
                log("invalid sub id, return data disabled");
                return false;
            }
            // Not found the 'MOBILE_DATA+phoneSubId' setting, we should initialize it.
            retVal = handleMobileDataSettingNotFound(retVal);
        }
        return retVal;
    }

    private boolean handleMobileDataSettingNotFound(boolean retVal) {
        log("handleMobileDataSettingNotFound: initial retVal=" + retVal);

        int phoneSubId = mPhone.getSubId();
        //C2K: get correct sub id
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            phoneSubId = SvlteUtils.getSvlteSubIdBySubId(phoneSubId);
        }
        if (!SubscriptionManager.isValidSubscriptionId(phoneSubId)) {
            log("invalid sub id, return data disabled");
            return false;
        }

        retVal = Settings.Global.getInt(mResolver, Settings.Global.MOBILE_DATA,
                retVal ? 1 : 0) != 0;

        if (!retVal) {
            setUserDataProperty(false);
            Settings.Global.putInt(mResolver, Settings.Global.MOBILE_DATA + phoneSubId, 0);
        } else { // OP02 will have default value of MOBILE_DATA as true
            int defaultDataSubId = SubscriptionManager.getDefaultDataSubId();
            log("defaultDataSubId = " + defaultDataSubId);
            if (defaultDataSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                // 'MOTA upgrade' will go this way
                if (phoneSubId == defaultDataSubId) {
                    setUserDataProperty(true);
                    Settings.Global.putInt(mResolver,
                            Settings.Global.MOBILE_DATA + phoneSubId, 1);
                    retVal = true;
                } else {
                    setUserDataProperty(false);
                    Settings.Global.putInt(mResolver,
                            Settings.Global.MOBILE_DATA + phoneSubId, 0);
                    retVal = false;
                }
            } else {
                int insertedStatus = 0;
                for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
                    if (!NO_SIM_VALUE.equals(SystemProperties.get(PROPERTY_ICCID[i]))) {
                        insertedStatus = insertedStatus | (1 << i);
                    }
                }
                log("insertedStatus = " + insertedStatus);
                if (insertedStatus == 1 || insertedStatus == 3) {
                    if (mPhone.getPhoneId() == 0) {
                        setUserDataProperty(true);
                        Settings.Global.putInt(mResolver,
                                Settings.Global.MOBILE_DATA + phoneSubId, 1);
                        retVal = true;
                    } else {
                        setUserDataProperty(false);
                        Settings.Global.putInt(mResolver,
                                Settings.Global.MOBILE_DATA + phoneSubId, 0);
                        retVal = false;
                    }
                } else if (insertedStatus == 2) {
                    if (mPhone.getPhoneId() == 1) {
                        setUserDataProperty(true);
                        Settings.Global.putInt(mResolver,
                                Settings.Global.MOBILE_DATA + phoneSubId, 1);
                        retVal = true;
                    } else {
                        setUserDataProperty(false);
                        Settings.Global.putInt(mResolver,
                                Settings.Global.MOBILE_DATA + phoneSubId, 0);
                        retVal = false;
                    }
                }
            }
        }

        log("handleMobileDataSettingNotFound: after retVal=" + retVal);
        return retVal;
    }

    // abstract methods
    protected abstract void restartRadio();
    protected abstract void log(String s);
    protected abstract void loge(String s);
    protected abstract boolean isDataAllowed();
    protected abstract boolean isApnTypeAvailable(String type);
    public    abstract DctConstants.State getState(String apnType);
    protected abstract boolean isProvisioningApn(String apnType);
    protected abstract void setState(DctConstants.State s);
    protected abstract void gotoIdleAndNotifyDataConnection(String reason);

    protected abstract boolean onTrySetupData(String reason);
    protected abstract void onRoamingOff();
    protected abstract void onRoamingOn();
    protected abstract void onRadioAvailable();
    protected abstract void onRadioOffOrNotAvailable();
    protected abstract void onDataSetupComplete(AsyncResult ar);
    protected abstract void onDataSetupCompleteError(AsyncResult ar);
    protected abstract void onDisconnectDone(int connId, AsyncResult ar);
    protected abstract void onDisconnectDcRetrying(int connId, AsyncResult ar);
    protected abstract void onVoiceCallStarted();
    protected abstract void onVoiceCallStartedPeer();
    protected abstract void onVoiceCallEnded();
    protected abstract void onVoiceCallEndedPeer();
    protected abstract void onCleanUpConnection(boolean tearDown, int apnId, String reason);
    protected abstract void onCleanUpAllConnections(String cause);
    public abstract boolean isDataPossible(String apnType);
    protected abstract void onUpdateIcc();
    protected abstract void completeConnection(ApnContext apnContext);
    public abstract void setDataAllowed(boolean enable, Message response);
    public abstract String[] getPcscfAddress(String apnType);
    public abstract void setImsRegistrationState(boolean registered);
    protected abstract boolean mvnoMatches(IccRecords r, String mvno_type, String mvno_match_data);
    protected abstract boolean isPermanentFail(DcFailCause dcFailCause);
    public abstract void deactivatePdpByCid(int cid); // MTK

    // M: VoLTE Start
    protected abstract void onClearDataBearer();
    public abstract void clearDataBearer();
    public abstract boolean isOnlyIMSorEIMSPdnConnected();
    // M: VoLTE End

    // M: [LTE][Low Power][UL traffic shaping] Start
    protected abstract void onSharedDefaultApnState(int newDefaultRefCount);
    // M: [LTE][Low Power][UL traffic shaping] End

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case AsyncChannel.CMD_CHANNEL_DISCONNECTED: {
                log("DISCONNECTED_CONNECTED: msg=" + msg);
                DcAsyncChannel dcac = (DcAsyncChannel) msg.obj;
                mDataConnectionAcHashMap.remove(dcac.getDataConnectionIdSync());
                dcac.disconnected();
                break;
            }
            case DctConstants.EVENT_ENABLE_NEW_APN:
                onEnableApn(msg.arg1, msg.arg2);
                break;

            case DctConstants.EVENT_TRY_SETUP_DATA:
                String reason = null;
                if (msg.obj instanceof String) {
                    reason = (String) msg.obj;
                }
                onTrySetupData(reason);
                break;

            case DctConstants.EVENT_DATA_STALL_ALARM:
                onDataStallAlarm(msg.arg1);
                break;

            case DctConstants.EVENT_ROAMING_OFF:
                onRoamingOff();
                break;

            case DctConstants.EVENT_ROAMING_ON:
                onRoamingOn();
                break;

            case DctConstants.EVENT_RADIO_AVAILABLE:
                onRadioAvailable();
                break;

            case DctConstants.EVENT_RADIO_OFF_OR_NOT_AVAILABLE:
                onRadioOffOrNotAvailable();
                break;

            case DctConstants.EVENT_DATA_SETUP_COMPLETE:
                mCidActive = msg.arg1;
                onDataSetupComplete((AsyncResult) msg.obj);
                break;

            case DctConstants.EVENT_DATA_SETUP_COMPLETE_ERROR:
                onDataSetupCompleteError((AsyncResult) msg.obj);
                break;

            case DctConstants.EVENT_DISCONNECT_DONE:
                log("DataConnectionTracker.handleMessage: EVENT_DISCONNECT_DONE msg=" + msg);
                onDisconnectDone(msg.arg1, (AsyncResult) msg.obj);
                break;

            case DctConstants.EVENT_DISCONNECT_DC_RETRYING:
                log("DataConnectionTracker.handleMessage: EVENT_DISCONNECT_DC_RETRYING msg=" + msg);
                onDisconnectDcRetrying(msg.arg1, (AsyncResult) msg.obj);
                break;

            case DctConstants.EVENT_VOICE_CALL_STARTED:
                onVoiceCallStarted();
                break;

            case DctConstants.EVENT_VOICE_CALL_ENDED:
                onVoiceCallEnded();
                break;

            //M: handle peer phone call state
            case DctConstants.EVENT_VOICE_CALL_STARTED_PEER:
                log("EVENT_VOICE_CALL_STARTED_PEER");
                onVoiceCallStartedPeer();
                break;
            case DctConstants.EVENT_VOICE_CALL_ENDED_PEER:
                log("EVENT_VOICE_CALL_ENDED_PEER");
                onVoiceCallEndedPeer();
                break;

            case DctConstants.EVENT_CLEAN_UP_ALL_CONNECTIONS: {
                onCleanUpAllConnections((String) msg.obj);
                break;
            }
            case DctConstants.EVENT_CLEAN_UP_CONNECTION: {
                boolean tearDown = (msg.arg1 == 0) ? false : true;
                onCleanUpConnection(tearDown, msg.arg2, (String) msg.obj);
                break;
            }
            case DctConstants.EVENT_SET_INTERNAL_DATA_ENABLE: {
                boolean enabled = (msg.arg1 == DctConstants.ENABLED) ? true : false;
                onSetInternalDataEnabled(enabled);
                break;
            }
            case DctConstants.EVENT_RESET_DONE: {
                if (DBG) log("EVENT_RESET_DONE");
                onResetDone((AsyncResult) msg.obj);
                break;
            }
            case DctConstants.CMD_SET_USER_DATA_ENABLE: {
                final boolean enabled = (msg.arg1 == DctConstants.ENABLED) ? true : false;
                if (DBG) log("CMD_SET_USER_DATA_ENABLE enabled=" + enabled);
                onSetUserDataEnabled(enabled);
                break;
            }
            case DctConstants.CMD_SET_DEPENDENCY_MET: {
                boolean met = (msg.arg1 == DctConstants.ENABLED) ? true : false;
                if (DBG) log("CMD_SET_DEPENDENCY_MET met=" + met);
                Bundle bundle = msg.getData();
                if (bundle != null) {
                    String apnType = (String)bundle.get(DctConstants.APN_TYPE_KEY);
                    if (apnType != null) {
                        onSetDependencyMet(apnType, met);
                    }
                }
                break;
            }
            case DctConstants.CMD_SET_POLICY_DATA_ENABLE: {
                final boolean enabled = (msg.arg1 == DctConstants.ENABLED) ? true : false;
                onSetPolicyDataEnabled(enabled);
                break;
            }
            case DctConstants.CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: {
                sEnableFailFastRefCounter += (msg.arg1 == DctConstants.ENABLED) ? 1 : -1;
                if (DBG) {
                    log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: "
                            + " sEnableFailFastRefCounter=" + sEnableFailFastRefCounter);
                }
                if (sEnableFailFastRefCounter < 0) {
                    final String s = "CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: "
                            + "sEnableFailFastRefCounter:" + sEnableFailFastRefCounter + " < 0";
                    loge(s);
                    sEnableFailFastRefCounter = 0;
                }
                final boolean enabled = sEnableFailFastRefCounter > 0;
                if (DBG) {
                    log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: enabled=" + enabled
                            + " sEnableFailFastRefCounter=" + sEnableFailFastRefCounter);
                }
                if (mFailFast != enabled) {
                    mFailFast = enabled;
                    mDataStallDetectionEnabled = !enabled;
                    if (mDataStallDetectionEnabled
                            && (getOverallState() == DctConstants.State.CONNECTED)
                            && (!mInVoiceCall ||
                                    mPhone.getServiceStateTracker()
                                        .isConcurrentVoiceAndDataAllowed())) {
                        if (DBG) log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: start data stall");
                        stopDataStallAlarm();
                        startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
                    } else {
                        if (DBG) log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: stop data stall");
                        stopDataStallAlarm();
                    }
                }

                break;
            }
            case DctConstants.CMD_ENABLE_MOBILE_PROVISIONING: {
                Bundle bundle = msg.getData();
                if (bundle != null) {
                    try {
                        mProvisioningUrl = (String)bundle.get(DctConstants.PROVISIONING_URL_KEY);
                    } catch(ClassCastException e) {
                        loge("CMD_ENABLE_MOBILE_PROVISIONING: provisioning url not a string" + e);
                        mProvisioningUrl = null;
                    }
                }
                if (TextUtils.isEmpty(mProvisioningUrl)) {
                    loge("CMD_ENABLE_MOBILE_PROVISIONING: provisioning url is empty, ignoring");
                    mIsProvisioning = false;
                    mProvisioningUrl = null;
                } else {
                    loge("CMD_ENABLE_MOBILE_PROVISIONING: provisioningUrl=" + mProvisioningUrl);
                    mIsProvisioning = true;
                    startProvisioningApnAlarm();
                }
                break;
            }
            case DctConstants.EVENT_PROVISIONING_APN_ALARM: {
                if (DBG) log("EVENT_PROVISIONING_APN_ALARM");
                ApnContext apnCtx = mApnContexts.get("default");
                if (apnCtx.isProvisioningApn() && apnCtx.isConnectedOrConnecting()) {
                    if (mProvisioningApnAlarmTag == msg.arg1) {
                        if (DBG) log("EVENT_PROVISIONING_APN_ALARM: Disconnecting");
                        mIsProvisioning = false;
                        mProvisioningUrl = null;
                        stopProvisioningApnAlarm();
                        sendCleanUpConnection(true, apnCtx);
                    } else {
                        if (DBG) {
                            log("EVENT_PROVISIONING_APN_ALARM: ignore stale tag,"
                                    + " mProvisioningApnAlarmTag:" + mProvisioningApnAlarmTag
                                    + " != arg1:" + msg.arg1);
                        }
                    }
                } else {
                    if (DBG) log("EVENT_PROVISIONING_APN_ALARM: Not connected ignore");
                }
                break;
            }
            case DctConstants.CMD_IS_PROVISIONING_APN: {
                if (DBG) log("CMD_IS_PROVISIONING_APN");
                boolean isProvApn;
                try {
                    String apnType = null;
                    Bundle bundle = msg.getData();
                    if (bundle != null) {
                        apnType = (String)bundle.get(DctConstants.APN_TYPE_KEY);
                    }
                    if (TextUtils.isEmpty(apnType)) {
                        loge("CMD_IS_PROVISIONING_APN: apnType is empty");
                        isProvApn = false;
                    } else {
                        isProvApn = isProvisioningApn(apnType);
                    }
                } catch (ClassCastException e) {
                    loge("CMD_IS_PROVISIONING_APN: NO provisioning url ignoring");
                    isProvApn = false;
                }
                if (DBG) log("CMD_IS_PROVISIONING_APN: ret=" + isProvApn);
                mReplyAc.replyToMessage(msg, DctConstants.CMD_IS_PROVISIONING_APN,
                        isProvApn ? DctConstants.ENABLED : DctConstants.DISABLED);
                break;
            }
            case DctConstants.EVENT_ICC_CHANGED: {
                onUpdateIcc();
                break;
            }
            case DctConstants.EVENT_RESTART_RADIO: {
                restartRadio();
                break;
            }
            case DctConstants.CMD_NET_STAT_POLL: {
                if (msg.arg1 == DctConstants.ENABLED) {
                    handleStartNetStatPoll((DctConstants.Activity)msg.obj);
                } else if (msg.arg1 == DctConstants.DISABLED) {
                    handleStopNetStatPoll((DctConstants.Activity)msg.obj);
                }
                break;
            }
            // VOLTE
            case DctConstants.EVENT_CLEAR_DATA_BEARER: {
                onClearDataBearer();
                break;
            }
            //M: [LTE][Low Power][UL traffic shaping] Start
            case DctConstants.EVENT_DEFAULT_APN_REFERENCE_COUNT_CHANGED: {
                int newDefaultRefCount = msg.arg1;
                onSharedDefaultApnState(newDefaultRefCount);
                break;
            }
            //M: [LTE][Low Power][UL traffic shaping] End
            default:
                Rlog.e("DATA", "Unidentified event msg=" + msg);
                break;
        }
    }

    /**
     * Report on whether data connectivity is enabled
     *
     * @return {@code false} if data connectivity has been explicitly disabled,
     *         {@code true} otherwise.
     */
    public boolean getAnyDataEnabled() {
        final boolean result;
        synchronized (mDataEnabledLock) {
            result = (mInternalDataEnabled && mUserDataEnabled && mPolicyDataEnabled
                    && (mEnabledCount != 0));
        }
        if (!result && DBG) log("getAnyDataEnabled " + result);
        return result;
    }

    protected boolean isEmergency() {
        final boolean result;
        synchronized (mDataEnabledLock) {
            result = mPhone.isInEcm() || mPhone.isInEmergencyCall();
        }
        log("isEmergency: result=" + result);
        return result;
    }

    protected int apnTypeToId(String type) {
        if (TextUtils.equals(type, PhoneConstants.APN_TYPE_DEFAULT)) {
            return DctConstants.APN_DEFAULT_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_MMS)) {
            return DctConstants.APN_MMS_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_SUPL)) {
            return DctConstants.APN_SUPL_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_DUN)) {
            return DctConstants.APN_DUN_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_HIPRI)) {
            return DctConstants.APN_HIPRI_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_IMS)) {
            return DctConstants.APN_IMS_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_FOTA)) {
            return DctConstants.APN_FOTA_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_CBS)) {
            return DctConstants.APN_CBS_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_IA)) {
            return DctConstants.APN_IA_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_EMERGENCY)) {
            return DctConstants.APN_EMERGENCY_ID;
        /** M: start */
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_DM)) {
            return DctConstants.APN_DM_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_NET)) {
            return DctConstants.APN_NET_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_WAP)) {
            return DctConstants.APN_WAP_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_CMMAIL)) {
            return DctConstants.APN_CMMAIL_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_RCSE)) {
            return DctConstants.APN_RCSE_ID;
        } else if ((TextUtils.equals(type, PhoneConstants.APN_TYPE_XCAP))) {
            return DctConstants.APN_XCAP_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_RCS)) {
            return DctConstants.APN_RCS_ID;
        /** M: end */
        } else {
            return DctConstants.APN_INVALID_ID;
        }
    }

    protected String apnIdToType(int id) {
        switch (id) {
        case DctConstants.APN_DEFAULT_ID:
            return PhoneConstants.APN_TYPE_DEFAULT;
        case DctConstants.APN_MMS_ID:
            return PhoneConstants.APN_TYPE_MMS;
        case DctConstants.APN_SUPL_ID:
            return PhoneConstants.APN_TYPE_SUPL;
        case DctConstants.APN_DUN_ID:
            return PhoneConstants.APN_TYPE_DUN;
        case DctConstants.APN_HIPRI_ID:
            return PhoneConstants.APN_TYPE_HIPRI;
        case DctConstants.APN_IMS_ID:
            return PhoneConstants.APN_TYPE_IMS;
        case DctConstants.APN_FOTA_ID:
            return PhoneConstants.APN_TYPE_FOTA;
        case DctConstants.APN_CBS_ID:
            return PhoneConstants.APN_TYPE_CBS;
        case DctConstants.APN_IA_ID:
            return PhoneConstants.APN_TYPE_IA;
        case DctConstants.APN_EMERGENCY_ID:
            return PhoneConstants.APN_TYPE_EMERGENCY;
        /** M: start */
        case DctConstants.APN_DM_ID:
            return PhoneConstants.APN_TYPE_DM;
        case DctConstants.APN_NET_ID:
            return PhoneConstants.APN_TYPE_NET;
        case DctConstants.APN_WAP_ID:
            return PhoneConstants.APN_TYPE_WAP;
        case DctConstants.APN_CMMAIL_ID:
            return PhoneConstants.APN_TYPE_CMMAIL;
        case DctConstants.APN_RCSE_ID:
            return PhoneConstants.APN_TYPE_RCSE;
        case DctConstants.APN_XCAP_ID:
            return PhoneConstants.APN_TYPE_XCAP;
        case DctConstants.APN_RCS_ID:
            return PhoneConstants.APN_TYPE_RCS;
        /** M: end */
        default:
            log("Unknown id (" + id + ") in apnIdToType");
            return PhoneConstants.APN_TYPE_DEFAULT;
        }
    }

    public LinkProperties getLinkProperties(String apnType) {
        int id = apnTypeToId(apnType);

        if (isApnIdEnabled(id)) {
            DcAsyncChannel dcac = mDataConnectionAcHashMap.get(0);
            return dcac.getLinkPropertiesSync();
        } else {
            return new LinkProperties();
        }
    }

    public NetworkCapabilities getNetworkCapabilities(String apnType) {
        int id = apnTypeToId(apnType);
        if (isApnIdEnabled(id)) {
            DcAsyncChannel dcac = mDataConnectionAcHashMap.get(0);
            return dcac.getNetworkCapabilitiesSync();
        } else {
            return new NetworkCapabilities();
        }
    }

    // tell all active apns of the current condition
    protected void notifyDataConnection(String reason) {
        for (int id = 0; id < DctConstants.APN_NUM_TYPES; id++) {
            if (mDataEnabled[id]) {
                mPhone.notifyDataConnection(reason, apnIdToType(id));
            }
        }
        notifyOffApnsOfAvailability(reason);
    }

    // a new APN has gone active and needs to send events to catch up with the
    // current condition
    private void notifyApnIdUpToCurrent(String reason, int apnId) {
        switch (mState) {
            case IDLE:
                break;
            case RETRYING:
            case CONNECTING:
            case SCANNING:
                mPhone.notifyDataConnection(reason, apnIdToType(apnId),
                        PhoneConstants.DataState.CONNECTING);
                break;
            case CONNECTED:
            case DISCONNECTING:
                mPhone.notifyDataConnection(reason, apnIdToType(apnId),
                        PhoneConstants.DataState.CONNECTING);
                mPhone.notifyDataConnection(reason, apnIdToType(apnId),
                        PhoneConstants.DataState.CONNECTED);
                break;
            default:
                // Ignore
                break;
        }
    }

    // since we normally don't send info to a disconnected APN, we need to do this specially
    private void notifyApnIdDisconnected(String reason, int apnId) {
        mPhone.notifyDataConnection(reason, apnIdToType(apnId),
                PhoneConstants.DataState.DISCONNECTED);
    }

    // disabled apn's still need avail/unavail notificiations - send them out
    protected void notifyOffApnsOfAvailability(String reason) {
        if (DBG) log("notifyOffApnsOfAvailability - reason= " + reason);
        for (int id = 0; id < DctConstants.APN_NUM_TYPES; id++) {
            if (!isApnIdEnabled(id)) {
                notifyApnIdDisconnected(reason, id);
            }
        }
    }

    public boolean isApnTypeEnabled(String apnType) {
        if (apnType == null) {
            return false;
        } else {
            return isApnIdEnabled(apnTypeToId(apnType));
        }
    }

    protected synchronized boolean isApnIdEnabled(int id) {
        if (id != DctConstants.APN_INVALID_ID) {
            return mDataEnabled[id];
        }
        return false;
    }

    protected void setEnabled(int id, boolean enable) {
        if (DBG) {
            log("setEnabled(" + id + ", " + enable + ") with old state = " + mDataEnabled[id]
                    + " and enabledCount = " + mEnabledCount);
        }
        Message msg = obtainMessage(DctConstants.EVENT_ENABLE_NEW_APN);
        msg.arg1 = id;
        msg.arg2 = (enable ? DctConstants.ENABLED : DctConstants.DISABLED);
        sendMessage(msg);
    }

    protected void onEnableApn(int apnId, int enabled) {
        if (DBG) {
            log("EVENT_APN_ENABLE_REQUEST apnId=" + apnId + ", apnType=" + apnIdToType(apnId) +
                    ", enabled=" + enabled + ", dataEnabled = " + mDataEnabled[apnId] +
                    ", enabledCount = " + mEnabledCount + ", isApnTypeActive = " +
                    isApnTypeActive(apnIdToType(apnId)));
        }
        if (enabled == DctConstants.ENABLED) {
            synchronized (this) {
                if (!mDataEnabled[apnId]) {
                    mDataEnabled[apnId] = true;
                    mEnabledCount++;
                }
            }
            String type = apnIdToType(apnId);
            if (!isApnTypeActive(type)) {
                mRequestedApnType = type;
                onEnableNewApn();
            } else {
                notifyApnIdUpToCurrent(Phone.REASON_APN_SWITCHED, apnId);
            }
        } else {
            // disable
            boolean didDisable = false;
            synchronized (this) {
                if (mDataEnabled[apnId]) {
                    mDataEnabled[apnId] = false;
                    mEnabledCount--;
                    didDisable = true;
                }
            }
            if (didDisable) {
                if ((mEnabledCount == 0) || (apnId == DctConstants.APN_DUN_ID)) {
                    mRequestedApnType = PhoneConstants.APN_TYPE_DEFAULT;
                    onCleanUpConnection(true, apnId, Phone.REASON_DATA_DISABLED);
                }

                // send the disconnect msg manually, since the normal route wont send
                // it (it's not enabled)
                notifyApnIdDisconnected(Phone.REASON_DATA_DISABLED, apnId);
                if (mDataEnabled[DctConstants.APN_DEFAULT_ID] == true
                        && !isApnTypeActive(PhoneConstants.APN_TYPE_DEFAULT)) {
                    // TODO - this is an ugly way to restore the default conn - should be done
                    // by a real contention manager and policy that disconnects the lower pri
                    // stuff as enable requests come in and pops them back on as we disable back
                    // down to the lower pri stuff
                    mRequestedApnType = PhoneConstants.APN_TYPE_DEFAULT;
                    onEnableNewApn();
                }
            }
        }
    }

    /**
     * Called when we switch APNs.
     *
     * mRequestedApnType is set prior to call
     * To be overridden.
     */
    protected void onEnableNewApn() {
    }

    /**
     * Called when EVENT_RESET_DONE is received so goto
     * IDLE state and send notifications to those interested.
     *
     * TODO - currently unused.  Needs to be hooked into DataConnection cleanup
     * TODO - needs to pass some notion of which connection is reset..
     */
    protected void onResetDone(AsyncResult ar) {
        if (DBG) log("EVENT_RESET_DONE");
        String reason = null;
        if (ar.userObj instanceof String) {
            reason = (String) ar.userObj;
        }
        gotoIdleAndNotifyDataConnection(reason);
    }

    /**
     * Prevent mobile data connections from being established, or once again
     * allow mobile data connections. If the state toggles, then either tear
     * down or set up data, as appropriate to match the new state.
     *
     * @param enable indicates whether to enable ({@code true}) or disable (
     *            {@code false}) data
     * @return {@code true} if the operation succeeded
     */
    public boolean setInternalDataEnabled(boolean enable) {
        if (DBG)
            log("setInternalDataEnabled(" + enable + ")");

        Message msg = obtainMessage(DctConstants.EVENT_SET_INTERNAL_DATA_ENABLE);
        msg.arg1 = (enable ? DctConstants.ENABLED : DctConstants.DISABLED);
        sendMessage(msg);
        return true;
    }

    protected void onSetInternalDataEnabled(boolean enabled) {
        synchronized (mDataEnabledLock) {
            mInternalDataEnabled = enabled;
            if (enabled) {
                log("onSetInternalDataEnabled: changed to enabled, try to setup data call");
                onTrySetupData(Phone.REASON_DATA_ENABLED);
            } else {
                log("onSetInternalDataEnabled: changed to disabled, cleanUpAllConnections");
                cleanUpAllConnections(null);
            }
        }
    }

    public void cleanUpAllConnections(String cause) {
        Message msg = obtainMessage(DctConstants.EVENT_CLEAN_UP_ALL_CONNECTIONS);
        msg.obj = cause;
        sendMessage(msg);
    }

    public abstract boolean isDisconnected();

    protected void onSetUserDataEnabled(boolean enabled) {
        synchronized (mDataEnabledLock) {
            // M: work ard for google issue which setting value may not sync; try to sync again
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                mUserDataEnabled = getDataEnabled();
            }

            if (mUserDataEnabled != enabled) {
                mUserDataEnabled = enabled;

                // For single SIM phones, this is a per phone property.
                if (TelephonyManager.getDefault().getSimCount() == 1) {
                    Settings.Global.putInt(mResolver, Settings.Global.MOBILE_DATA, enabled ? 1 : 0);
                } else {
                    int phoneSubId = mPhone.getSubId();
                    //C2K: get correct sub id
                    if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                        phoneSubId = SvlteUtils.getSvlteSubIdBySubId(phoneSubId);
                    }
                    Settings.Global.putInt(mResolver, Settings.Global.MOBILE_DATA + phoneSubId,
                            enabled ? 1 : 0);
                    
					//为保证华为状态栏数据图标更新，特增加对 mobile_data 的设置
					//获取当前数据业务所在卡的subId
					int dataSubId = SubscriptionController.getInstance()
							.getDefaultDataSubId();
					// mobile_data 的值只须与数据业务所在卡的开关值同步即可,DataSubId >0 表示数据卡有被设置
					if (dataSubId > 0 && dataSubId == phoneSubId) {
						log("onSetUserDataEnabled(): set mobile_data value");
						Settings.Global.putInt(mResolver,
								Settings.Global.MOBILE_DATA, enabled ? 1 : 0);
					}
                }

                //M:
                setUserDataProperty(enabled);
                notifyMobileDataChange(enabled ? 1 : 0);

                // M: [C2K][IRAT] modify start. @{
                SubscriptionController subController = SubscriptionController.getInstance();
                final int defaultDataPhoneId = subController.getPhoneId(
                        subController.getDefaultDataSubId());
                int curPhoneId = mPhone.getPhoneId();

                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                    //Convert SVLTE_DC_PHONE_ID to normal id
                    curPhoneId = SvlteUtils.getSvltePhoneIdByPhoneId(curPhoneId);
                }
                if (defaultDataPhoneId != curPhoneId) {
                    log("Current phone is not default phone");
                    return;
                }
                // M: }@

                // ensure Settings.Global.MOBILE_DATA is updated.
                boolean readEnabled = false;
                if (TelephonyManager.getDefault().getSimCount() == 1) {
                    readEnabled = Settings.Global.getInt(mResolver,
                            Settings.Global.MOBILE_DATA, enabled ? 1 : 0) == 1;
                } else {
                    int phoneSubId = mPhone.getSubId();
                    //C2K: get correct sub id
                    if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                        phoneSubId = SvlteUtils.getSvlteSubIdBySubId(phoneSubId);
                    }
                    readEnabled = Settings.Global.getInt(mResolver,
                            Settings.Global.MOBILE_DATA + phoneSubId, enabled ? 1 : 0) == 1;
                }
                if (readEnabled != enabled && mSettingProviderRetryCount < 10) {
                    log("onSetUserDataEnabled(): readEnabled = " + readEnabled +
                            ", mSettingProviderRetryCount = " + mSettingProviderRetryCount);

                    // M: restore this parameter to prev state
                    log("write to setting un-sync! re-send msg CMD_SET_USER_DATA_ENABLE");
                    mUserDataEnabled = !enabled;

                    Message msg = obtainMessage(DctConstants.CMD_SET_USER_DATA_ENABLE);
                    msg.arg1 = (enabled ? DctConstants.ENABLED : DctConstants.DISABLED);
                    sendMessageDelayed(msg, 500);

                    mSettingProviderRetryCount++;
                    return;
                }
                mSettingProviderRetryCount = 0;

                if (getDataOnRoamingEnabled() == false &&
                        mPhone.getServiceState().getDataRoaming() == true) {
                    if (enabled) {
                        notifyOffApnsOfAvailability(Phone.REASON_ROAMING_ON);
                    } else {
                        notifyOffApnsOfAvailability(Phone.REASON_DATA_DISABLED);
                    }
                }

                if (enabled) {
                    onTrySetupData(Phone.REASON_DATA_ENABLED);
                } else {
                    boolean isBsp = SystemProperties.getBoolean("ro.mtk_bsp_package", false);
                    if (isBsp) {
                        onCleanUpAllConnections(Phone.REASON_DATA_SPECIFIC_DISABLED);
                    } else {
                         for (ApnContext apnContext : mApnContexts.values()) {
                            if (!isDataAllowedAsOff(apnContext.getApnType())) {
                                apnContext.setReason(Phone.REASON_DATA_SPECIFIC_DISABLED);
                                onCleanUpConnection(true, apnTypeToId(apnContext.getApnType())
                                        , Phone.REASON_DATA_SPECIFIC_DISABLED);
                            }
                        }
                    }
                }
            }
        }
    }

    protected void onSetDependencyMet(String apnType, boolean met) {
    }

    protected void onSetPolicyDataEnabled(boolean enabled) {
        synchronized (mDataEnabledLock) {
            final boolean prevEnabled = getAnyDataEnabled();
            if (mPolicyDataEnabled != enabled) {
                mPolicyDataEnabled = enabled;
                if (prevEnabled != getAnyDataEnabled()) {
                    if (!prevEnabled) {
                        onTrySetupData(Phone.REASON_DATA_ENABLED);
                    } else {
                        onCleanUpAllConnections(Phone.REASON_DATA_SPECIFIC_DISABLED);
                    }
                }
            }
        }
    }

    // M: [LTE][Low Power][UL traffic shaping] Start
    public void onSetLteAccessStratumReport(boolean enabled, Message response) {
        mPhone.mCi.setLteAccessStratumReport(enabled, response);
    }

    public void onSetLteUplinkDataTransfer(int timeMillis, Message response) {
        for(ApnContext apnContext : mApnContexts.values()) {
            if(PhoneConstants.APN_TYPE_DEFAULT.equals(apnContext.getApnType())) {
                try {
                    int interfaceId = apnContext.getDcAc().getCidSync();
                    mPhone.mCi.setLteUplinkDataTransfer(timeMillis, interfaceId, response);
                } catch (Exception e) {
                    loge("getDcAc fail!");
                    e.printStackTrace();
                    if (response != null) {
                        AsyncResult.forMessage(response, null,
                                new CommandException(CommandException.Error.GENERIC_FAILURE));
                        response.sendToTarget();
                    }
                }
            }
        }
    }

    protected void notifyLteAccessStratumChanged(int lteAccessStratumDataState) {
        mLteAccessStratumDataState = (lteAccessStratumDataState == 1) ?
                PhoneConstants.LTE_ACCESS_STRATUM_STATE_CONNECTED :
                PhoneConstants.LTE_ACCESS_STRATUM_STATE_IDLE;
        if (DBG) {
            log("notifyLteAccessStratumChanged mLteAccessStratumDataState = "
                    + mLteAccessStratumDataState);
        }
        mPhone.notifyLteAccessStratumChanged(mLteAccessStratumDataState);
    }

    protected void notifyPsNetworkTypeChanged(int newRilNwType) {
        int newNwType = mPhone.getServiceState().rilRadioTechnologyToNetworkTypeEx(newRilNwType);
        if (DBG) {
            log("notifyPsNetworkTypeChanged mNetworkType = " + mNetworkType
                    + ", newNwType = " + newNwType
                    + ", newRilNwType = " + newRilNwType);
        }
        if (newNwType != mNetworkType) {
            mNetworkType = newNwType;
            mPhone.notifyPsNetworkTypeChanged(mNetworkType);
        }
    }

    protected void notifySharedDefaultApn(boolean mSharedDefaultApn) {
    }

    public String getLteAccessStratumState() {
        return mLteAccessStratumDataState;
    }

    public boolean isSharedDefaultApn() {
        return mSharedDefaultApn;
    }
    // M: [LTE][Low Power][UL traffic shaping] End

    protected String getReryConfig(boolean forDefault) {
        int nt = mPhone.getServiceState().getNetworkType();

        if ((nt == TelephonyManager.NETWORK_TYPE_CDMA) ||
            (nt == TelephonyManager.NETWORK_TYPE_1xRTT) ||
            (nt == TelephonyManager.NETWORK_TYPE_EVDO_0) ||
            (nt == TelephonyManager.NETWORK_TYPE_EVDO_A) ||
            (nt == TelephonyManager.NETWORK_TYPE_EVDO_B) ||
            (nt == TelephonyManager.NETWORK_TYPE_EHRPD)) {
            // CDMA variant
            return SystemProperties.get("ro.cdma.data_retry_config");
        } else {
            // Use GSM varient for all others.
            if (forDefault) {
                return SystemProperties.get("ro.gsm.data_retry_config");
            } else {
                return SystemProperties.get("ro.gsm.2nd_data_retry_config");
            }
        }
    }

    protected void resetPollStats() {
        mTxPkts = -1;
        mRxPkts = -1;
        mActivity = DctConstants.Activity.NONE;
        mNetStatPollPeriod = POLL_NETSTAT_MILLIS;
    }

    protected abstract DctConstants.State getOverallState();

    void startNetStatPoll() {
        if (getOverallState() == DctConstants.State.CONNECTED
                && mNetStatPollEnabled == false) {
            if (DBG) {
                log("startNetStatPoll");
            }
            resetPollStats();
            mNetStatPollEnabled = true;
            mPollNetStat.run();
        }
        if (mPhone != null) {
            mPhone.notifyDataActivity();
        }
    }

    void stopNetStatPoll() {
        mNetStatPollEnabled = false;
        removeCallbacks(mPollNetStat);
        if (DBG) {
            log("stopNetStatPoll");
        }

        // To sync data activity icon in the case of switching data connection to send MMS.
        if (mPhone != null) {
            mPhone.notifyDataActivity();
        }
    }

    public void sendStartNetStatPoll(DctConstants.Activity activity) {
        Message msg = obtainMessage(DctConstants.CMD_NET_STAT_POLL);
        msg.arg1 = DctConstants.ENABLED;
        msg.obj = activity;
        sendMessage(msg);
    }

    protected void handleStartNetStatPoll(DctConstants.Activity activity) {
        startNetStatPoll();
        startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
        setActivity(activity);
    }

    public void sendStopNetStatPoll(DctConstants.Activity activity) {
        Message msg = obtainMessage(DctConstants.CMD_NET_STAT_POLL);
        msg.arg1 = DctConstants.DISABLED;
        msg.obj = activity;
        sendMessage(msg);
    }

    protected void handleStopNetStatPoll(DctConstants.Activity activity) {
        stopNetStatPoll();
        stopDataStallAlarm();
        setActivity(activity);
    }

    public void updateDataActivity() {
        long sent, received;

        DctConstants.Activity newActivity;

        TxRxSum preTxRxSum = new TxRxSum(mTxPkts, mRxPkts);
        TxRxSum curTxRxSum = new TxRxSum();
        curTxRxSum.updateTxRxSum();
        mTxPkts = curTxRxSum.txPkts;
        mRxPkts = curTxRxSum.rxPkts;

        if (VDBG) {
            log("updateDataActivity: curTxRxSum=" + curTxRxSum + " preTxRxSum=" + preTxRxSum);
        }

        if (mNetStatPollEnabled && (preTxRxSum.txPkts > 0 || preTxRxSum.rxPkts > 0)) {
            sent = mTxPkts - preTxRxSum.txPkts;
            received = mRxPkts - preTxRxSum.rxPkts;

            if (VDBG)
                log("updateDataActivity: sent=" + sent + " received=" + received);
            if (sent > 0 && received > 0) {
                newActivity = DctConstants.Activity.DATAINANDOUT;
            } else if (sent > 0 && received == 0) {
                newActivity = DctConstants.Activity.DATAOUT;
            } else if (sent == 0 && received > 0) {
                newActivity = DctConstants.Activity.DATAIN;
            } else {
                newActivity = (mActivity == DctConstants.Activity.DORMANT) ?
                        mActivity : DctConstants.Activity.NONE;
            }

            if (mActivity != newActivity && mIsScreenOn) {
                if (VDBG)
                    log("updateDataActivity: newActivity=" + newActivity);
                mActivity = newActivity;
                mPhone.notifyDataActivity();
            }
        }
    }

    // Recovery action taken in case of data stall
    protected static class RecoveryAction {
        public static final int GET_DATA_CALL_LIST      = 0;
        public static final int CLEANUP                 = 1;
        public static final int REREGISTER              = 2;
        public static final int RADIO_RESTART           = 3;
        public static final int RADIO_RESTART_WITH_PROP = 4;

        private static boolean isAggressiveRecovery(int value) {
            return ((value == RecoveryAction.CLEANUP) ||
                    (value == RecoveryAction.REREGISTER) ||
                    (value == RecoveryAction.RADIO_RESTART) ||
                    (value == RecoveryAction.RADIO_RESTART_WITH_PROP));
        }
    }

    public int getRecoveryAction() {
        int action = Settings.System.getInt(mResolver,
                "radio.data.stall.recovery.action", RecoveryAction.GET_DATA_CALL_LIST);
        if (VDBG_STALL) log("getRecoveryAction: " + action);
        return action;
    }
    public void putRecoveryAction(int action) {
        Settings.System.putInt(mResolver, "radio.data.stall.recovery.action", action);
        if (VDBG_STALL) log("putRecoveryAction: " + action);
    }

    protected boolean isConnected() {
        return false;
    }

    protected void doRecovery() {
        if (getOverallState() == DctConstants.State.CONNECTED) {
            // Go through a series of recovery steps, each action transitions to the next action
            int recoveryAction = getRecoveryAction();
            switch (recoveryAction) {
            case RecoveryAction.GET_DATA_CALL_LIST:
                EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_GET_DATA_CALL_LIST,
                        mSentSinceLastRecv);
                if (DBG) log("doRecovery() get data call list");
                mPhone.mCi.getDataCallList(obtainMessage(DctConstants.EVENT_DATA_STATE_CHANGED));
                putRecoveryAction(RecoveryAction.CLEANUP);
                break;
            case RecoveryAction.CLEANUP:
                EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_CLEANUP, mSentSinceLastRecv);
                if (DBG) log("doRecovery() cleanup all connections");
                cleanUpAllConnections(Phone.REASON_PDP_RESET);
                putRecoveryAction(RecoveryAction.REREGISTER);
                break;
            case RecoveryAction.REREGISTER:
                EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_REREGISTER,
                        mSentSinceLastRecv);
                if (DBG) log("doRecovery() re-register");

                /** M: re-register PS domain only
                 *  Not to use mPhone.getServiceStateTracker().reRegisterNetwork
                 *  Re-register may not be triggered by it and both CS and PS could be impacted
                 *  Let DctController disconnect all data connections and trigger re-attach
                 */
                DctController.getInstance().disconnectAll();

                putRecoveryAction(RecoveryAction.RADIO_RESTART);
                break;
            case RecoveryAction.RADIO_RESTART:
                EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_RADIO_RESTART,
                        mSentSinceLastRecv);
                if (DBG) log("restarting radio");
                putRecoveryAction(RecoveryAction.RADIO_RESTART_WITH_PROP);
                restartRadio();
                break;
            case RecoveryAction.RADIO_RESTART_WITH_PROP:
                // This is in case radio restart has not recovered the data.
                // It will set an additional "gsm.radioreset" property to tell
                // RIL or system to take further action.
                // The implementation of hard reset recovery action is up to OEM product.
                // Once RADIO_RESET property is consumed, it is expected to set back
                // to false by RIL.
                EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_RADIO_RESTART_WITH_PROP, -1);
                if (DBG) log("restarting radio with gsm.radioreset to true");
                SystemProperties.set(RADIO_RESET_PROPERTY, "true");
                // give 1 sec so property change can be notified.
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {}
                restartRadio();
                putRecoveryAction(RecoveryAction.GET_DATA_CALL_LIST);
                break;
            default:
                throw new RuntimeException("doRecovery: Invalid recoveryAction=" +
                    recoveryAction);
            }
            mSentSinceLastRecv = 0;
        }
    }

    private void updateDataStallInfo() {
        long sent, received;

        TxRxSum preTxRxSum = new TxRxSum(mDataStallTxRxSum);
        mDataStallTxRxSum.updateTxRxSum();

        if (VDBG_STALL) {
            log("updateDataStallInfo: mDataStallTxRxSum=" + mDataStallTxRxSum +
                    " preTxRxSum=" + preTxRxSum);
        }

        sent = mDataStallTxRxSum.txPkts - preTxRxSum.txPkts;
        received = mDataStallTxRxSum.rxPkts - preTxRxSum.rxPkts;

        if (RADIO_TESTS) {
            if (SystemProperties.getBoolean("radio.test.data.stall", false)) {
                log("updateDataStallInfo: radio.test.data.stall true received = 0;");
                received = 0;
            }
        }
        if ( sent > 0 && received > 0 ) {
            if (VDBG_STALL) log("updateDataStallInfo: IN/OUT");
            mSentSinceLastRecv = 0;
            putRecoveryAction(RecoveryAction.GET_DATA_CALL_LIST);
        } else if (sent > 0 && received == 0) {
            if (mPhone.getState() == PhoneConstants.State.IDLE) {
                mSentSinceLastRecv += sent;
            } else {
                mSentSinceLastRecv = 0;
            }
            if (DBG) {
                log("updateDataStallInfo: OUT sent=" + sent +
                        " mSentSinceLastRecv=" + mSentSinceLastRecv);
            }
        } else if (sent == 0 && received > 0) {
            if (VDBG_STALL) log("updateDataStallInfo: IN");
            mSentSinceLastRecv = 0;
            putRecoveryAction(RecoveryAction.GET_DATA_CALL_LIST);
        } else {
            if (VDBG_STALL) log("updateDataStallInfo: NONE");
        }
    }

    protected void onDataStallAlarm(int tag) {
        if (mDataStallAlarmTag != tag) {
            if (DBG) {
                log("onDataStallAlarm: ignore, tag=" + tag + " expecting " + mDataStallAlarmTag);
            }
            return;
        }
        updateDataStallInfo();

        int hangWatchdogTrigger = Settings.Global.getInt(mResolver,
                Settings.Global.PDP_WATCHDOG_TRIGGER_PACKET_COUNT,
                NUMBER_SENT_PACKETS_OF_HANG);

        boolean suspectedStall = DATA_STALL_NOT_SUSPECTED;
        if (mSentSinceLastRecv >= hangWatchdogTrigger) {
            if (DBG) {
                log("onDataStallAlarm: tag=" + tag + " do recovery action=" + getRecoveryAction());
            }
            if (isOnlyIMSorEIMSPdnConnected()) {
                log("only IMS or EIMS Connected, skip onDataStallAlarm");
            } else {
                suspectedStall = DATA_STALL_SUSPECTED;
                sendMessage(obtainMessage(DctConstants.EVENT_DO_RECOVERY));
            }
        } else {
            if (VDBG_STALL) {
                log("onDataStallAlarm: tag=" + tag + " Sent " + String.valueOf(mSentSinceLastRecv) +
                    " pkts since last received, < watchdogTrigger=" + hangWatchdogTrigger);
            }
        }
        startDataStallAlarm(suspectedStall);
    }

    protected void startDataStallAlarm(boolean suspectedStall) {
        int nextAction = getRecoveryAction();
        int delayInMs;

        if (mDataStallDetectionEnabled && getOverallState() == DctConstants.State.CONNECTED) {
            try {
                ITelephonyEx iTelEx = ITelephonyEx.Stub.asInterface(
                        ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));

                if (null == iTelEx) {
                    loge("startDataStallAlarm iTelEx is null");
                    return;
                }

                int slotId = SubscriptionManager.getSlotId(mPhone.getSubId());
                if (SubscriptionManager.isValidSlotId(slotId) && iTelEx.isTestIccCard(slotId)) {
                    loge("startDataStallAlarm but skip due to test SIM is detected");
                    return;
                }
            } catch (RemoteException ex) {
                loge("startDataStallAlarm test SIM detection fail");
                ex.printStackTrace();
            }

            // If screen is on or data stall is currently suspected, set the alarm
            // with an aggresive timeout.
            if (mIsScreenOn || suspectedStall || RecoveryAction.isAggressiveRecovery(nextAction)) {
                delayInMs = Settings.Global.getInt(mResolver,
                        Settings.Global.DATA_STALL_ALARM_AGGRESSIVE_DELAY_IN_MS,
                        DATA_STALL_ALARM_AGGRESSIVE_DELAY_IN_MS_DEFAULT);
            } else {
                delayInMs = Settings.Global.getInt(mResolver,
                        Settings.Global.DATA_STALL_ALARM_NON_AGGRESSIVE_DELAY_IN_MS,
                        DATA_STALL_ALARM_NON_AGGRESSIVE_DELAY_IN_MS_DEFAULT);
            }

            mDataStallAlarmTag += 1;
            if (VDBG_STALL) {
                log("startDataStallAlarm: tag=" + mDataStallAlarmTag +
                        " delay=" + (delayInMs / 1000) + "s");
            }
            Intent intent = new Intent(INTENT_DATA_STALL_ALARM);
            intent.putExtra(DATA_STALL_ALARM_TAG_EXTRA, mDataStallAlarmTag);
            intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, getSubId()); // M: add sub information

            mDataStallAlarmIntent = PendingIntent.getBroadcast(mPhone.getContext(), 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + delayInMs, mDataStallAlarmIntent);
        } else {
            if (VDBG_STALL) {
                log("startDataStallAlarm: NOT started, no connection tag=" + mDataStallAlarmTag);
            }
        }
    }

    protected void stopDataStallAlarm() {
        if (VDBG_STALL) {
            log("stopDataStallAlarm: current tag=" + mDataStallAlarmTag +
                    " mDataStallAlarmIntent=" + mDataStallAlarmIntent);
        }
        mDataStallAlarmTag += 1;
        if (mDataStallAlarmIntent != null) {
            mAlarmManager.cancel(mDataStallAlarmIntent);
            mDataStallAlarmIntent = null;
        }
    }

    protected void restartDataStallAlarm() {
        if (isConnected() == false) return;
        // To be called on screen status change.
        // Do not cancel the alarm if it is set with aggressive timeout.
        int nextAction = getRecoveryAction();

        if (RecoveryAction.isAggressiveRecovery(nextAction)) {
            if (DBG) log("restartDataStallAlarm: action is pending. not resetting the alarm.");
            return;
        }
        if (VDBG_STALL) log("restartDataStallAlarm: stop then start.");
        stopDataStallAlarm();
        startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
    }

    protected void setInitialAttachApn() {
        // M:[C2K][IRAT] Set initial attach APN for SVLTE. {@
        if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() && SvlteUtils.isActiveSvlteMode(mPhone)
                    && mSvlteOperatorNumeric != null) {
                setInitialAttachApnForSvlte();
            } else {
                log("[IRAT_DcTracker] DO NOT setInitialApn for CDMA: numeric = "
                        + mSvlteOperatorNumeric);
            }
            return;
        } else if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
            log("[IRAT_DcTracker] GSM setInitialAttachApn: numeric = "
                    + mSvlteOperatorNumeric);
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                    && SvlteUtils.isActiveSvlteMode(mPhone)) {
                if (mSvlteOperatorNumeric != null) {
                    // Since only CTLTE/Empty APN can be used to attach LTE
                    // network for CT network, only set CTLTE as initial attach.
                    if (OPERATOR_NUMERIC_CTLTE.equals(mSvlteOperatorNumeric)) {
                        setInitialAttachApnForSvlte();
                        return;
                    }
                    // Else if the SIM is not CT card(not equals 46011), follow
                    // the default flow.
                } else {
                    // Do nothing since LTE records is not loaded yet.
                    log("[IRAT_DcTracker] GSM ignore IA because SIM not loaded.");
                    IccRecords r = mIccRecords.get();
                    String operatorNumeric = (r != null) ? r.getOperatorNumeric() : "";

					///HQ_xionghaifeng 20151222 add for Roaming Broker start
					operatorNumeric = mapToMainPlmnIfNeeded(operatorNumeric);
					///HQ_xionghaifeng 20151222 add for Roaming Broker end
			
                    if (operatorNumeric == null || operatorNumeric.length() == 0) {
                        log("setInitialApn: but no operator numeric");
                        return;
                    }
                }
            }
        }
        // M: @}

        boolean isIaApn = false;
        ApnSetting previousAttachApn = mInitialAttachApnSetting;
        IccRecords r = mIccRecords.get();
        String operatorNumeric = (r != null) ? r.getOperatorNumeric() : "";

		///HQ_xionghaifeng 20151222 add for Roaming Broker start
		operatorNumeric = mapToMainPlmnIfNeeded(operatorNumeric);
		///HQ_xionghaifeng 20151222 add for Roaming Broker end
			
        if (operatorNumeric == null || operatorNumeric.length() == 0) {
            log("setInitialApn: but no operator numeric");
            return;
        }

        String[] dualApnPlmnList = null;
        if (MTK_DUAL_APN_SUPPORT == true) {
            dualApnPlmnList = mPhone.getContext().getResources()
                        .getStringArray(com.mediatek.internal.R.array.dtag_dual_apn_plmn_list);
        }

        log("setInitialApn: current attach Apn [" + mInitialAttachApnSetting + "]");
        ApnSetting iaApnSetting = null;
        ApnSetting defaultApnSetting = null;
        ApnSetting firstApnSetting = null;

        log("setInitialApn: E mPreferredApn=" + mPreferredApn);

        if (mAllApnSettings != null && !mAllApnSettings.isEmpty()) {
            firstApnSetting = mAllApnSettings.get(0);
            log("setInitialApn: firstApnSetting=" + firstApnSetting);

            // Search for Initial APN setting and the first apn that can handle default
            for (ApnSetting apn : mAllApnSettings) {
                // Can't use apn.canHandleType(), as that returns true for APNs that have no type.
                if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IA) &&
                        apn.carrierEnabled) {
                    // The Initial Attach APN is highest priority so use it if there is one
                    log("setInitialApn: iaApnSetting=" + apn);
                    iaApnSetting = apn;
                    if (ArrayUtils.contains(PLMN_EMPTY_APN_PCSCF_SET, operatorNumeric)) {
                        isIaApn = true;
                    }
                    break;
                } else if ((defaultApnSetting == null)
                        && (apn.canHandleType(PhoneConstants.APN_TYPE_DEFAULT))) {
                    // Use the first default apn if no better choice
                    log("setInitialApn: defaultApnSetting=" + apn);
                    defaultApnSetting = apn;
                }
            }
        }

        // The priority of apn candidates from highest to lowest is:
        //   1) APN_TYPE_IA (Inital Attach)
        //   2) mPreferredApn, i.e. the current preferred apn
        //   3) The first apn that than handle APN_TYPE_DEFAULT
        //   4) The first APN we can find.

        mInitialAttachApnSetting = null;
        if (iaApnSetting != null) {
            if (DBG) log("setInitialAttachApn: using iaApnSetting");
            mInitialAttachApnSetting = iaApnSetting;
        } else if (mPreferredApn != null) {
            if (DBG) log("setInitialAttachApn: using mPreferredApn");
            mInitialAttachApnSetting = mPreferredApn;
        } else if (defaultApnSetting != null) {
            if (DBG) log("setInitialAttachApn: using defaultApnSetting");
            mInitialAttachApnSetting = defaultApnSetting;
        } else if (firstApnSetting != null) {
            if (DBG) log("setInitialAttachApn: using firstApnSetting");
            mInitialAttachApnSetting = firstApnSetting;
        }

        if (mInitialAttachApnSetting == null) {
            if (operatorNumeric == null) {
                if (DBG) log("setInitialAttachApn: but no operator and no available apn");
            } else {
                if (DBG) log("setInitialAttachApn: X There in no available apn, use empty");

                if (MTK_DUAL_APN_SUPPORT == true) {
                    mPhone.mCi.setInitialAttachApn("", RILConstants.SETUP_DATA_PROTOCOL_IP, -1, "",
                            "", operatorNumeric, false, dualApnPlmnList, null);
                } else {
                mPhone.mCi.setInitialAttachApn("", RILConstants.SETUP_DATA_PROTOCOL_IP, -1, "", "",
                        operatorNumeric, false, null);
                }
            }
        } else {
            if (operatorNumeric == null) {
                if (DBG) log("setInitialAttachApn: but no operator");
            } else {
                if (DBG) log("setInitialAttachApn: X selected Apn=" + mInitialAttachApnSetting);
                String iaApn = mInitialAttachApnSetting.apn;
                if (isIaApn) {
                    if (DBG) log("setInitialAttachApn: ESM flag false, change IA APN to empty");
                    iaApn = "";
                }
                if (MTK_DUAL_APN_SUPPORT == true) {
                    mPhone.mCi.setInitialAttachApn(iaApn,
                            mInitialAttachApnSetting.protocol, mInitialAttachApnSetting.authType,
                            mInitialAttachApnSetting.user, mInitialAttachApnSetting.password,
                            operatorNumeric,
                            mInitialAttachApnSetting.canHandleType(PhoneConstants.APN_TYPE_IMS),
                            dualApnPlmnList, null);
                } else {
                    mPhone.mCi.setInitialAttachApn(iaApn,
                            mInitialAttachApnSetting.protocol, mInitialAttachApnSetting.authType,
                            mInitialAttachApnSetting.user, mInitialAttachApnSetting.password,
                            operatorNumeric,
                            mInitialAttachApnSetting.canHandleType(PhoneConstants.APN_TYPE_IMS),
                            null);
                }
            }
        }
        if (DBG) log("setInitialAttachApn: new attach Apn [" + mInitialAttachApnSetting + "]");
    }

    protected void setDataProfilesAsNeeded() {
        if (DBG) log("setDataProfilesAsNeeded");
        if (mAllApnSettings != null && !mAllApnSettings.isEmpty()) {
            ArrayList<DataProfile> dps = new ArrayList<DataProfile>();
            for (ApnSetting apn : mAllApnSettings) {
                if (apn.modemCognitive) {
                    DataProfile dp = new DataProfile(apn,
                            mPhone.getServiceState().getDataRoaming());
                    boolean isDup = false;
                    for(DataProfile dpIn : dps) {
                        if (dp.equals(dpIn)) {
                            isDup = true;
                            break;
                        }
                    }
                    if (!isDup) {
                        dps.add(dp);
                    }
                }
            }
            if(dps.size() > 0) {
                mPhone.mCi.setDataProfile(dps.toArray(new DataProfile[0]), null);
            }
        }
    }

    protected void onActionIntentProvisioningApnAlarm(Intent intent) {
        if (DBG) log("onActionIntentProvisioningApnAlarm: action=" + intent.getAction());
        Message msg = obtainMessage(DctConstants.EVENT_PROVISIONING_APN_ALARM,
                intent.getAction());
        msg.arg1 = intent.getIntExtra(PROVISIONING_APN_ALARM_TAG_EXTRA, 0);
        sendMessage(msg);
    }

    protected void startProvisioningApnAlarm() {
        int delayInMs = Settings.Global.getInt(mResolver,
                                Settings.Global.PROVISIONING_APN_ALARM_DELAY_IN_MS,
                                PROVISIONING_APN_ALARM_DELAY_IN_MS_DEFAULT);
        if (Build.IS_DEBUGGABLE) {
            // Allow debug code to use a system property to provide another value
            String delayInMsStrg = Integer.toString(delayInMs);
            delayInMsStrg = System.getProperty(DEBUG_PROV_APN_ALARM, delayInMsStrg);
            try {
                delayInMs = Integer.parseInt(delayInMsStrg);
            } catch (NumberFormatException e) {
                loge("startProvisioningApnAlarm: e=" + e);
            }
        }
        mProvisioningApnAlarmTag += 1;
        if (DBG) {
            log("startProvisioningApnAlarm: tag=" + mProvisioningApnAlarmTag +
                    " delay=" + (delayInMs / 1000) + "s");
        }
        Intent intent = new Intent(INTENT_PROVISIONING_APN_ALARM);
        intent.putExtra(PROVISIONING_APN_ALARM_TAG_EXTRA, mProvisioningApnAlarmTag);
        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, getSubId()); // M: add sub information

        mProvisioningApnAlarmIntent = PendingIntent.getBroadcast(mPhone.getContext(), 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delayInMs, mProvisioningApnAlarmIntent);
    }

    protected void stopProvisioningApnAlarm() {
        if (DBG) {
            log("stopProvisioningApnAlarm: current tag=" + mProvisioningApnAlarmTag +
                    " mProvsioningApnAlarmIntent=" + mProvisioningApnAlarmIntent);
        }
        mProvisioningApnAlarmTag += 1;
        if (mProvisioningApnAlarmIntent != null) {
            mAlarmManager.cancel(mProvisioningApnAlarmIntent);
            mProvisioningApnAlarmIntent = null;
        }
    }

    void sendCleanUpConnection(boolean tearDown, ApnContext apnContext) {
        if (DBG)log("sendCleanUpConnection: tearDown=" + tearDown + " apnContext=" + apnContext);
        Message msg = obtainMessage(DctConstants.EVENT_CLEAN_UP_CONNECTION);
        msg.arg1 = tearDown ? 1 : 0;
        msg.arg2 = 0;
        msg.obj = apnContext;
        sendMessage(msg);
    }

    void sendRestartRadio() {
        if (DBG)log("sendRestartRadio:");
        Message msg = obtainMessage(DctConstants.EVENT_RESTART_RADIO);
        sendMessage(msg);
    }

    //MTK START
    protected boolean isDataAllowedAsOff(String apnType) {
        return false;
    }

    protected void notifyMobileDataChange(int enabled) {
        log("notifyMobileDataChange, enable = " + enabled);
        Intent intent = new Intent(DataSubSelector.ACTION_MOBILE_DATA_ENABLE);
        intent.putExtra(DataSubSelector.EXTRA_MOBILE_DATA_ENABLE_REASON, enabled);
        mPhone.getContext().sendBroadcast(intent);
    }

    protected ConcurrentHashMap<String, ApnContext> getApnContexts() {
        return mApnContexts;
    }

    private void setUserDataProperty(boolean enabled) {
        int phoneId = mPhone.getPhoneId();
        String dataOnIccid = "0";

        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            phoneId = SvlteUtils.getSvltePhoneIdByPhoneId(phoneId);
        }

        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
            log("invalid phone id, don't update");
            return;
        }

        if (enabled) {
            dataOnIccid = SystemProperties.get(PROPERTY_ICCID[phoneId], "0");
        }

        log("setUserDataProperty:" + dataOnIccid);
        TelephonyManager.getDefault().setTelephonyProperty(phoneId, PROPERTY_MOBILE_DATA_ENABLE,
                dataOnIccid);
    }

    //MTK END

    // C2K IRAT START
    protected boolean isDuringIrat() {
        return mIsDuringIrat;
    }

    private void setInitialAttachApnForSvlte() {
        if (isDuringIrat()) {
            log("[IRAT_DcTracker] Pend setInitialApn due to IRAT is on-going");
            mHasPendingInitialApnRequest = true;
            return;
        }

        PhoneBase ltePhone = SvlteUtils.getSvltePhoneProxy(mPhone.getPhoneId()).getLtePhone();
        log("[IRAT_DcTracker] setInitialAttachApnForSvlte: apn = "
                + mSvlteIaApnSetting);
        if (mSvlteIaApnSetting == null) {
            ltePhone.mCi.setInitialAttachApn("",
                    RILConstants.SETUP_DATA_PROTOCOL_IP, -1, "", "",
                    mSvlteOperatorNumeric, false, null);
        } else {
            ltePhone.mCi.setInitialAttachApn(mSvlteIaApnSetting.apn,
                    mSvlteIaApnSetting.protocol, mSvlteIaApnSetting.authType,
                    mSvlteIaApnSetting.user, mSvlteIaApnSetting.password,
                    mSvlteOperatorNumeric, mSvlteIaApnSetting
                            .canHandleType(PhoneConstants.APN_TYPE_IMS), null);
        }
    }
    // C2K IRAT END

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("DcTrackerBase:");
        pw.println(" RADIO_TESTS=" + RADIO_TESTS);
        pw.println(" mInternalDataEnabled=" + mInternalDataEnabled);
        pw.println(" mUserDataEnabled=" + mUserDataEnabled);
        pw.println(" sPolicyDataEnabed=" + mPolicyDataEnabled);
        pw.println(" mDataEnabled:");
        for(int i=0; i < mDataEnabled.length; i++) {
            pw.printf("  mDataEnabled[%d]=%b\n", i, mDataEnabled[i]);
        }
        pw.flush();
        pw.println(" mEnabledCount=" + mEnabledCount);
        pw.println(" mRequestedApnType=" + mRequestedApnType);
        pw.println(" mPhone=" + mPhone.getPhoneName());
        pw.println(" mActivity=" + mActivity);
        pw.println(" mState=" + mState);
        pw.println(" mTxPkts=" + mTxPkts);
        pw.println(" mRxPkts=" + mRxPkts);
        pw.println(" mNetStatPollPeriod=" + mNetStatPollPeriod);
        pw.println(" mNetStatPollEnabled=" + mNetStatPollEnabled);
        pw.println(" mDataStallTxRxSum=" + mDataStallTxRxSum);
        pw.println(" mDataStallAlarmTag=" + mDataStallAlarmTag);
        pw.println(" mDataStallDetectionEanbled=" + mDataStallDetectionEnabled);
        pw.println(" mSentSinceLastRecv=" + mSentSinceLastRecv);
        pw.println(" mNoRecvPollCount=" + mNoRecvPollCount);
        pw.println(" mResolver=" + mResolver);
        pw.println(" mIsWifiConnected=" + mIsWifiConnected);
        pw.println(" mReconnectIntent=" + mReconnectIntent);
        pw.println(" mCidActive=" + mCidActive);
        pw.println(" mAutoAttachOnCreation=" + mAutoAttachOnCreation);
        pw.println(" mIsScreenOn=" + mIsScreenOn);
        pw.println(" mUniqueIdGenerator=" + mUniqueIdGenerator);
        pw.flush();
        pw.println(" ***************************************");
        DcController dcc = mDcc;
        if (dcc != null) {
            dcc.dump(fd, pw, args);
        } else {
            pw.println(" mDcc=null");
        }
        pw.println(" ***************************************");
        HashMap<Integer, DataConnection> dcs = mDataConnections;
        if (dcs != null) {
            Set<Entry<Integer, DataConnection> > mDcSet = mDataConnections.entrySet();
            pw.println(" mDataConnections: count=" + mDcSet.size());
            for (Entry<Integer, DataConnection> entry : mDcSet) {
                pw.printf(" *** mDataConnection[%d] \n", entry.getKey());
                entry.getValue().dump(fd, pw, args);
            }
        } else {
            pw.println("mDataConnections=null");
        }
        pw.println(" ***************************************");
        pw.flush();
        HashMap<String, Integer> apnToDcId = mApnToDataConnectionId;
        if (apnToDcId != null) {
            Set<Entry<String, Integer>> apnToDcIdSet = apnToDcId.entrySet();
            pw.println(" mApnToDataConnectonId size=" + apnToDcIdSet.size());
            for (Entry<String, Integer> entry : apnToDcIdSet) {
                pw.printf(" mApnToDataConnectonId[%s]=%d\n", entry.getKey(), entry.getValue());
            }
        } else {
            pw.println("mApnToDataConnectionId=null");
        }
        pw.println(" ***************************************");
        pw.flush();
        ConcurrentHashMap<String, ApnContext> apnCtxs = mApnContexts;
        if (apnCtxs != null) {
            Set<Entry<String, ApnContext>> apnCtxsSet = apnCtxs.entrySet();
            pw.println(" mApnContexts size=" + apnCtxsSet.size());
            for (Entry<String, ApnContext> entry : apnCtxsSet) {
                entry.getValue().dump(fd, pw, args);
            }
            pw.println(" ***************************************");
        } else {
            pw.println(" mApnContexts=null");
        }
        pw.flush();
        pw.println(" mActiveApn=" + mActiveApn);
        ArrayList<ApnSetting> apnSettings = mAllApnSettings;
        if (apnSettings != null) {
            pw.println(" mAllApnSettings size=" + apnSettings.size());
            for (int i=0; i < apnSettings.size(); i++) {
                pw.printf(" mAllApnSettings[%d]: %s\n", i, apnSettings.get(i));
            }
            pw.flush();
        } else {
            pw.println(" mAllApnSettings=null");
        }
        pw.println(" mPreferredApn=" + mPreferredApn);
        pw.println(" mIsPsRestricted=" + mIsPsRestricted);
        pw.println(" mIsDisposed=" + mIsDisposed);
        pw.println(" mIntentReceiver=" + mIntentReceiver);
        pw.println(" mDataRoamingSettingObserver=" + mDataRoamingSettingObserver);
        pw.flush();
    }

	///HQ_xionghaifeng 20151222 add for Roaming Broker start
	protected String mapToMainPlmnIfNeeded(String oldPlmn)
	{
		if(SystemProperties.get("ro.hq.sim.dual_imsi", "0").equals("1"))
		{
			String mainPlmn;
			int slotId = SubscriptionManager.getSlotId(mPhone.getSubId());
			boolean roamingBrokerActived = getDualImsiParameters("gsm.RoamingBrokerIsActivied" + slotId).equals("1");

			if (roamingBrokerActived)
			{
				mainPlmn = getDualImsiParameters("gsm.RoamingBrokerMainPLMN" + slotId);
				Log.d(TAG_XHFRB, "mapToMainPlmnIfNeeded map to" + mainPlmn);
			    if (mainPlmn == null || mainPlmn.length() == 0) {
			        Log.d(TAG_XHFRB, "dual_imsi mapToMainPlmnIfNeeded mainPlmn is not valid");
			        return oldPlmn;                
			    }
				return mainPlmn;
			}
			else
			{
				Log.d(TAG_XHFRB, "dual_imsi mapToMainPlmnIfNeeded is not changed");
				return oldPlmn;
			}
		}
		else 
		{
            Log.d(TAG_XHFRB, "not dual_imsi mapToMainPlmnIfNeeded is not changed");
			return oldPlmn;
		}
	}
    public String getDualImsiParameters(String name) {  
        String ret = Settings.System.getString(mResolver, name);

        Log.d(TAG_XHFRB, "getDualImsiParameters name = " + name+ "  ret = "+ ret);
        if (ret == null) {
            return "";
        }
        return ret;
    }  

	///HQ_xionghaifeng 20160108 add for HQ01591823 movistar Roaming Broker start
	static final String TAG_XHFRB = "xhfRoamingBroker";
	private String getCurrentIMSI()
	{ 
		String curImsi = mPhone.getSubscriberId(); 
		Log.d(TAG_XHFRB, "getCurrentIMSI:"+curImsi); 
		return curImsi; 
	} 

	private void setPreferredApnIdForDualIMSI(int imsiid , String imsi , int apnid) 
	{ 
		if(imsiid!=1 && imsiid!=2)
		{ 
			Log.d(TAG_XHFRB, "setPreferredApnIdForDualIMSI illegal dualsimid:"+imsiid); 
			return; 
		}
		
		String subId = Long.toString(mPhone.getSubId()); 
		Log.d(TAG_XHFRB, "setPreferredApnIdForDualIMSI imsiid:"+imsiid+" imsi="+imsi+" apnid="+apnid+" sub="+subId); 

		if(imsiid == 1)
		{ 
			SystemProperties.set("persist.sys.dual.imsi1."+subId, imsi); 
			SystemProperties.set("persist.sys.dual.apn1."+subId, Integer.toString(apnid)); 
		} 
		if(imsiid == 2)
		{ 
			SystemProperties.set("persist.sys.dual.imsi2."+subId, imsi); 
			SystemProperties.set("persist.sys.dual.apn2."+subId, Integer.toString(apnid)); 
		} 
	} 

	private String getImsiForDualIMSI(int imsiid , int subId) { 
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

	private int getApnIdForDualIMSI(int imsiid , int subId) 
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
		return Integer.parseInt(tempVal); 
	} 

	private void dualImsiSyncWithPreferredApn(int apnId)
	{ 
		Uri PREFERAPN_NO_UPDATE_URI_USING_SUBID = 
		Uri.parse("content://telephony/carriers/preferapn_no_update/subId/"); 

		String subId = Long.toString(mPhone.getSubId()); 
		Uri uri = Uri.withAppendedPath(PREFERAPN_NO_UPDATE_URI_USING_SUBID, subId); 
		Log.d(TAG_XHFRB, "dualImsiSyncWithPreferredApn: delete subId = " + subId); 
		ContentResolver resolver = mPhone.getContext().getContentResolver(); 
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
		IccRecords r = mIccRecords.get(); 
		String operator = (r != null) ? r.getOperatorNumeric() : "";
		
		///for SW_GLOBAL_EUROPE_163 movistar PLMN21407 HQ01591823
		if (SystemProperties.get("ro.hq.sim.dual_imsi.remapn", "0").equals("1"))
		{
			return operator.equals("21407") ? true : false; 
		}
		else
		{
			return false;
		}
	}	

	protected int mapPreferredApnIfNeeded(int oldApnId) 
	{ 
		String imsi1 = ""; 
		String imsi2 = ""; 
		int newApnId = -1; 
		int subId = mPhone.getSubId(); 
		String curImsi = getCurrentIMSI(); 

		imsi1 = getImsiForDualIMSI(1 , subId); 
		imsi2 = getImsiForDualIMSI(2 , subId); 

		Log.d(TAG_XHFRB, "mapPreferredApnIfNeeded: imsi1="+imsi1+" imsi2="+imsi2); 
		Log.d(TAG_XHFRB, "mapPreferredApnIfNeeded: curImsi="+curImsi); 
		if(imsi1.equals("N/A")) 
		{ 
			return oldApnId; 
		} 
		else 
		{ 
			if(imsi1.equals(curImsi))
			{ 
				newApnId = getApnIdForDualIMSI(1 , subId); 
				if(newApnId == -1 || newApnId == oldApnId) 
				{
					return oldApnId; 
				}
				dualImsiSyncWithPreferredApn(newApnId); 
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
						if(newApnId == -1 || newApnId == oldApnId) 
						{
							return oldApnId; 
						}
						dualImsiSyncWithPreferredApn(newApnId); 
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

	protected void updateDualImsiPreferredApnIfNeeded(int apnId) 
	{ 
		String imsi1 = ""; 
		String imsi2 = ""; 
		int subId = mPhone.getSubId(); 
		String curImsi = getCurrentIMSI(); 

		imsi1 = getImsiForDualIMSI(1 , subId); 
		imsi2 = getImsiForDualIMSI(2 , subId); 

		Log.d(TAG_XHFRB, "updateDualImsiPreferredApnIfNeeded: imsi1="+imsi1+" imsi2="+imsi2); 
		Log.d(TAG_XHFRB, "updateDualImsiPreferredApnIfNeeded: curImsi="+curImsi); 

		if(imsi1.equals("N/A") || imsi1.equals(curImsi))
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
			setPreferredApnIdForDualIMSI(2 , "N/A" , -1); 
		} 
	} 
	///HQ_xionghaifeng 20160108 add for HQ01591823 movistar Roaming Broker end
	///HQ_xionghaifeng 20151222 add for Roaming Broker end
}
