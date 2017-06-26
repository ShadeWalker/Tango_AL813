package com.mediatek.rns;

import static com.android.internal.util.Preconditions.checkNotNull;
import static com.mediatek.rns.RnsPolicy.POLICY_NAME_PREFERENCE;
import static com.mediatek.rns.RnsPolicy.POLICY_NAME_ROVE_THRESHOLD;
import static com.mediatek.rns.RnsPolicy.UserPreference.PREFERENCE_NONE;
import static com.mediatek.rns.RnsPolicy.UserPreference.PREFERENCE_WIFI_ONLY;
import static com.mediatek.rns.RnsPolicy.UserPreference.PREFERENCE_WIFI_PREFERRED;
import static com.mediatek.rns.RnsPolicy.UserPreference.PREFERENCE_CELLULAR_ONLY;
import static com.mediatek.rns.RnsPolicy.UserPreference.PREFERENCE_CELLULAR_PREFERRED;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;

import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Network;
import android.net.NetworkRequest;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.WifiManager;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemProperties;
import android.provider.Settings;

import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.ServiceState;
import android.util.Slog;

import com.android.internal.util.IndentingPrintWriter;


import java.io.FileDescriptor;
import java.io.PrintWriter;

import java.util.HashMap;

/**
 * Radio Network Selection Service.
 */
public class RnsServiceImpl extends IRnsManager.Stub {

    private final String TAG = "RnsServiceImpl";
    private final boolean DEBUG = true;
    private static final int DISCONNECT_RSSI = -127;
    private static final int WEAK_SIGNAL = -116;
    private static final int MAX_REG_WAIT = 5 * 1000; // 5 milliseconds
    private static final int DISABLE_WIFI_GUARD_TIMER = 10 * 1000;
    private Context mContext;
    private ConnectivityManager mConnMgr;
    private WifiManager mWifiMgr;
    private TelephonyManager mTeleMgr;
    private InternalHandler mHandler;
    private AsyncTask<Void, Void, Void> mWifiTask;
    private boolean mIsWifiConnected = false;
    private boolean mIsWifiEnabled = false;
    private boolean mIsSettingChanged = false;
    private boolean mIsWifiDisabling = false;
    private int mWifiDisableFlag;
    private int mAllowedRadio;
    private HashMap<String, RnsPolicy> mPolicies = new HashMap<String, RnsPolicy>();
    private int mState = RnsManager.STATE_DEFAULT;

    private boolean mIsWfcEnabled = false;
    private ServiceState mLtePhoneState;
    private WifiCallingSettingsObserver mWfcSettingsObserver;
    // sequence number of NetworkRequests
    //private int mNextNetworkRequestId = 1;
    private int mLastRssi;
    private int mLastSignalRsrp = WEAK_SIGNAL + 10;

    private int mHandoverEvent = -1;
    private long mStartTime;
    // Stamps the time after IMS pdn on LTE setup, for first IMS pdn request.
    private long mLteRegTime;
    private static final NetworkRequest REQUEST = new NetworkRequest.Builder()
    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
    .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
    .build();

    private boolean mIsDefaultRequestEnabled = false;
    private static final NetworkRequest DEFAULT_REQUEST = new NetworkRequest.Builder()
    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
    .build();

    private boolean isLteImsConnected = false;
    private boolean isEpdgImsConnected = false;
    private boolean mIsImsOverLteEnabled = false;
    private boolean mIsEpdgConnectionChanged = false;
    private ImsOverLteSettingsObserver mImsOverLteSettingsObserver;
    // Tracks the First IMS pdn request after boot.
    private boolean mIsFirstRequest = false;
    private int mServiceState = 0 ;
    private int mPrevCallState = TelephonyManager.CALL_STATE_IDLE;
    private boolean mIsCallActive = false;
    /**
     * constructor of rns service.
     * @param context from system server
     */
    public RnsServiceImpl(Context context) {
        mContext = checkNotNull(context, "missing Context");
        mConnMgr = (ConnectivityManager) mContext.getSystemService(
                       Context.CONNECTIVITY_SERVICE);
        mWifiMgr = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        Slog.d(TAG, "Current RSSI on constructor: " + mWifiMgr.getConnectionInfo().getRssi());
        HandlerThread handlerThread = new HandlerThread("RnsServiceThread");
        handlerThread.start();
        Looper rnsLooper = handlerThread.getLooper();
        if (rnsLooper != null) {
            mHandler = new InternalHandler(rnsLooper);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION_IMMEDIATE);
        filter.addAction(RnsManager.CONNECTIVITY_ACTION_HANDOVER_END);
        mContext.registerReceiver(mIntentReceiver, filter);

        mTeleMgr = (TelephonyManager) mContext.getSystemService(
                                        Context.TELEPHONY_SERVICE);
        mTeleMgr.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
        mTeleMgr.listen(mPhoneSignalListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        mTeleMgr.listen(mPhoneCallStateListener, PhoneStateListener.LISTEN_CALL_STATE);

        mWfcSettingsObserver
            = new WifiCallingSettingsObserver(mHandler, EVENT_APPLY_WIFI_CALL_SETTINGS);
        mWfcSettingsObserver.observe(mContext);
        mImsOverLteSettingsObserver
            = new ImsOverLteSettingsObserver(mHandler, EVENT_APPLY_IMS_OVER_LTE_SETTINGS);
        mImsOverLteSettingsObserver.observe(mContext);
        //create default policies for UT/IT
        createDefaultPolicies();
        // Initialize to track the first IMS pdn request after boot.
        mIsFirstRequest = true;
    }

    /**
     *
     * Start function for service.
     */
    public void start() {
        mHandler.obtainMessage(EVENT_APPLY_WIFI_CALL_SETTINGS).sendToTarget();
        mHandler.obtainMessage(EVENT_APPLY_IMS_OVER_LTE_SETTINGS).sendToTarget();
        mConnMgr.registerNetworkCallback(REQUEST, mNetworkCallback);
        mStartTime = System.currentTimeMillis();
    }

    protected BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                final NetworkInfo networkInfo = (NetworkInfo)
                    intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                synchronized (this) {
                    mIsWifiConnected = (networkInfo != null && (networkInfo.isConnected()
                        || networkInfo.getDetailedState() == DetailedState.CAPTIVE_PORTAL_CHECK));
                }
                Slog.d(TAG, "onReceive: NETWORK_STATE_CHANGED_ACTION connected = "
                       + mIsWifiConnected);
                if (mIsWifiConnected == false) {
                    mLastRssi = DISCONNECT_RSSI;
                    mHandler.sendMessage(mHandler.obtainMessage(EVENT_WIFI_DISCONNECT, 0));
                } else {
                    mHandler.sendMessage(
                        mHandler.obtainMessage(EVENT_WIFI_STATE_CHANGED_ACTION, 0));
                }
            } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                mIsWifiEnabled =
                    intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN) == WifiManager.WIFI_STATE_ENABLED;
                Slog.d(TAG, "onReceive: WIFI_STATE_CHANGED_ACTION enable = " + mIsWifiEnabled);
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_WIFI_STATE_CHANGED_ACTION, 0));
            } else if (action.equals(WifiManager.RSSI_CHANGED_ACTION)) {
                int rssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, 0);
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_WIFI_RSSI_UPDATE, rssi, 0));
            } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION_IMMEDIATE)) {
                final NetworkInfo networkInfo = (NetworkInfo)
                        intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                if (networkInfo != null) {
                    Slog.d(TAG, "onReceive: CONNECTIVITY_ACTION_IMMEDIATE");
                    int nwType = networkInfo.getType();
                    String typename = networkInfo.getTypeName();
                    String subtypename =  networkInfo.getSubtypeName();
                    Slog.d(TAG, "nwType:" + nwType + " typename = "
                            + typename + " subtypename = " + subtypename);
                    if ("MOBILE_IMS".equals(typename) && "LTE".equals(subtypename)) {
                        isLteImsConnected = networkInfo.isConnected();
                        if (isLteImsConnected) {
                            handleDefaultPdnRequest();
                        }
                    } else if ("Wi-Fi".equals(typename) && "IMS".equals(subtypename)) {
                        isEpdgImsConnected = networkInfo.isConnected();
                        mIsEpdgConnectionChanged = true;
                        if (needToSendAlertWarning()) {
                            Slog.d(TAG, "send Rove Out Alert warning for connection update");
                            sendRoveOutAlert();
                        }
                        if (!isEpdgImsConnected && mIsWifiDisabling) {
                            mIsWifiDisabling = false;
                            Slog.d(TAG, "Epdg is disconnected & disable wifi");
                            mWifiMgr.setWifiDisabledByEpdg(mWifiDisableFlag);
                        }
                        mIsEpdgConnectionChanged = false;
                    }
                    Slog.d(TAG, "isLteImsConnected = " + isLteImsConnected +
                           " isEpdgImsConnected = " + isEpdgImsConnected);
                }
            } else if (action.equals(RnsManager.CONNECTIVITY_ACTION_HANDOVER_END)) {
               /* The handover procedure is finished whether the result is succeed or not.
                * Need to reset the RNS state to default to next rove-in or rove-out.
               */
               Slog.d(TAG, "Reset RNS state for handover is end");
               if (isHandoverInProgress()) {
                    mState = RnsManager.STATE_DEFAULT;
               }
            }
        }
    };

    /**
     * settings of wifi calling.
     */
    private static class WifiCallingSettingsObserver extends ContentObserver {
        private int mWhat;
        private Handler mHandler;
        WifiCallingSettingsObserver(Handler handler, int what) {
            super(handler);
            mHandler = handler;
            mWhat = what;
        }

        void observe(Context context) {
            ContentResolver resolver = context.getContentResolver();
            resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.WHEN_TO_MAKE_WIFI_CALLS),
                                          false, this);

            resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SELECTED_WFC_PREFERRENCE),
                                          false, this);

            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.RNS_WIFI_ROVE_IN_RSSI),
                                          false, this);

            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.RNS_WIFI_ROVE_OUT_RSSI),
                                          false, this);

        }

        @Override
        public void onChange(boolean selfChange) {
            mHandler.obtainMessage(mWhat).sendToTarget();
        }
    }
    /**
     *Monitor Ims OverLTE settings.
     */
    private static class ImsOverLteSettingsObserver extends ContentObserver {
        private int mWhat;
        private Handler mHandler;
        ImsOverLteSettingsObserver(Handler handler, int what) {
            super(handler);
            mHandler = handler;
            mWhat = what;
        }
        void observe(Context context) {
            ContentResolver resolver = context.getContentResolver();
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.IMS_SWITCH),
                                          false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
         mHandler.obtainMessage(mWhat).sendToTarget();
        }
    }

    private void createDefaultPolicies() {
        RnsPolicy policy;

        RnsPolicy.UserPreference preference =
            new RnsPolicy.UserPreference(PREFERENCE_WIFI_PREFERRED);
        policy = new RnsPolicy(preference);
        mPolicies.put(POLICY_NAME_PREFERENCE, policy);

        RnsPolicy.WifiRoveThreshold threshold =
            new RnsPolicy.WifiRoveThreshold(-75, -85);
        policy = new RnsPolicy(threshold);
        mPolicies.put(POLICY_NAME_ROVE_THRESHOLD, policy);
    }

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            mLtePhoneState = serviceState;
            Slog.d(TAG, "onServiceStateChanged: " + mLtePhoneState.getState());
            if (mLtePhoneState.getState() == ServiceState.STATE_OUT_OF_SERVICE) {
                isLteImsConnected = false;
                mLastSignalRsrp = WEAK_SIGNAL;
            }
            mHandler.sendMessage(mHandler.obtainMessage(EVENT_RAT_CONNECTIVITY_CHANGE, 0));
        }
    };

    private final PhoneStateListener mPhoneCallStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            Slog.d(TAG, "in onCallStateChanged state:" + state + ", prev state:" + mPrevCallState);
            switch(state){
                case TelephonyManager.CALL_STATE_IDLE:
                        if (mPrevCallState != TelephonyManager.CALL_STATE_IDLE ) {// call ended/completed/missed
                            mPrevCallState = state;
                            mIsCallActive = false;
                            decideHandover();
                        }
                    break;
                    case TelephonyManager.CALL_STATE_OFFHOOK: //answering incoming call or making outgoing call 
                    case TelephonyManager.CALL_STATE_RINGING: //incoming call 
                        mPrevCallState = state;
                        mIsCallActive = true;
                    break;
                }
            }
    };

    private final PhoneStateListener mPhoneSignalListener = new PhoneStateListener() {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {

            int newSignalRsrp = signalStrength.getLteRsrp();
            if (newSignalRsrp > 0) {
                return ;
            }
            Slog.d(TAG, "Current Signal Rsrp is:" + newSignalRsrp);
            if ((newSignalRsrp >= WEAK_SIGNAL && mLastSignalRsrp <= WEAK_SIGNAL) ||
                (newSignalRsrp <= WEAK_SIGNAL && mLastSignalRsrp >= WEAK_SIGNAL)) {
                synchronized (this) {
                    mLastSignalRsrp = newSignalRsrp;
                }
                if (!(isEpdgImsConnected || (isLteImsConnected && (newSignalRsrp > WEAK_SIGNAL)))) {
                    Slog.d(TAG, "Perform handover");
                    mHandler.sendMessage(mHandler.obtainMessage(EVENT_RAT_CONNECTIVITY_CHANGE, 0));
                }
                return;
            }
            synchronized (this) {
                mLastSignalRsrp = newSignalRsrp;
            }
        }
    };

    private void tryConnectToRadio(int radio) {

        //Use retry for pending state or no IMS PDN connection
        if (mState == RnsManager.STATE_PENDING || (!isEpdgImsConnected && !isLteImsConnected)) {
            Slog.d(TAG, "retryConnectToRadio:" + radio + ":" + mHandoverEvent);
            mConnMgr.retryConnectToRadio(radio, mHandoverEvent);
            mHandoverEvent = -1;
            return;
        } else {
            Slog.d(TAG, "tryConnectToRadio:" + radio);
            mConnMgr.connectToRadio(radio);
        }
        if (radio == ConnectivityManager.TYPE_MOBILE) {
            mState = RnsManager.STATE_ROVEOUT;
        } else if (radio == ConnectivityManager.TYPE_WIFI) {
                mState = RnsManager.STATE_ROVEIN;
        }
    }

    @Override
    public int getAllowedRadioList(int capability) {
        //TODO: make radio by capability, ims or mms ...etc
        switch (capability) {
            case ConnectivityManager.TYPE_MOBILE_IMS:
                return makeImsRadio();
        default:
                return makeMmsRadio();
        }
    }

    @Override
    public int getTryAnotherRadioType(int failedNetType) {
        int profile = PREFERENCE_NONE;
        int netType = ConnectivityManager.TYPE_NONE;
        RnsPolicy policy = mPolicies.get(POLICY_NAME_PREFERENCE);
        if (policy != null && policy.getUserPreference() != null) {
            profile = policy.getUserPreference().getMode();
        }
        //Handover case
        if (isHandoverInProgress()) {
            if (mState == RnsManager.STATE_ROVEIN &&
                    failedNetType == ConnectivityManager.TYPE_WIFI) {
                Slog.d(TAG, "RoveIn failed:" +
                            (System.currentTimeMillis() - mStartTime) + " msec.");
            } else if (mState == RnsManager.STATE_ROVEOUT &&
                       failedNetType == ConnectivityManager.TYPE_MOBILE) {
                Slog.d(TAG, "RoveOut failed:" +
                            (System.currentTimeMillis() - mStartTime) + " msec.");
            }
            mState = RnsManager.STATE_DEFAULT;
        }

        //initial connection fail and try another case
        switch (failedNetType) {
        case ConnectivityManager.TYPE_WIFI:
            if (profile == PREFERENCE_WIFI_ONLY) {
                Slog.d(TAG, "PREFERENCE_WIFI_ONLY - no need try another");
            } else if (profile == PREFERENCE_WIFI_PREFERRED) {
                Slog.d(TAG, "isLteNetworkReady " + isLteNetworkReady() +
                            " mIsWfcEnabled " + mIsWfcEnabled);
                if (isLteNetworkReady() && mIsWfcEnabled
                        && isImsOverLteEnabled()) {
                    netType = ConnectivityManager.TYPE_MOBILE;
                    mStartTime = System.currentTimeMillis();
                }
            }
            break;
        case ConnectivityManager.TYPE_MOBILE:
            if (profile == PREFERENCE_CELLULAR_ONLY) {
                Slog.d(TAG, "PREFERENCE_CELLULAR_ONLY - no need try another");
            } else if (profile == PREFERENCE_CELLULAR_PREFERRED) {
                Slog.d(TAG, "isWifiConnected " + isWifiConnected() +
                            " mIsWfcEnabled " + mIsWfcEnabled);
                if (isWifiConnected() && mIsWfcEnabled && !isNetworkReady()) {
                    netType = ConnectivityManager.TYPE_WIFI;
                    mStartTime = System.currentTimeMillis();
                }
            }
            break;
        default:
            break;
        }
        Slog.d(TAG, "getTryAnotherRadioType:New network: " + netType + " Old network: "
                + failedNetType + " profile: " + profile);
        return netType;
    }

    @Override
    public int getRnsState() {
        return mState;
    }

    private int makeImsRadio() {
        mAllowedRadio = 0;
        int profile = 0;
        RnsPolicy policy = mPolicies.get(POLICY_NAME_PREFERENCE);
        if (policy != null && policy.getUserPreference() != null) {
            profile = policy.getUserPreference().getMode();
            if (mIsWfcEnabled == false) {
                profile = PREFERENCE_CELLULAR_ONLY ;
            }
            if (mIsFirstRequest == true && profile != PREFERENCE_WIFI_PREFERRED) {
                mIsFirstRequest = false;
            }
            switch (profile) {
                case PREFERENCE_WIFI_ONLY:
                    if (isWifiConnected() && mIsWfcEnabled) {
                        addRadio(RnsManager.ALLOWED_RADIO_WIFI);
                    } else if (isNetworkReady()) {
                        addRadio(RnsManager.ALLOWED_RADIO_NONE);
                    } else {
                        addRadio(RnsManager.ALLOWED_RADIO_DENY);
                    }
                    break;
                case PREFERENCE_WIFI_PREFERRED:
                    // Select LTE if present, for first IMS pdn request after bootup.
                    if (mIsFirstRequest == true && isLteNetworkReady() && isImsOverLteEnabled()) {
                        addRadio(RnsManager.ALLOWED_RADIO_MOBILE);
                        // Initialize to track time lapse, after first LTE pdn setup request.
                        mLteRegTime = System.currentTimeMillis();
                    } else {
                        mIsFirstRequest = false;
                        if (isWifiConnected() && mIsWfcEnabled &&
                            mWifiMgr.getConnectionInfo().getRssi() >
                            mPolicies.get(POLICY_NAME_ROVE_THRESHOLD).getWifiRoveThreshold().
                            getRssiRoveIn()) {
                            addRadio(RnsManager.ALLOWED_RADIO_WIFI);
                        } else if (isLteNetworkReady() && mLastSignalRsrp > WEAK_SIGNAL
                                  && isImsOverLteEnabled()) {
                            addRadio(RnsManager.ALLOWED_RADIO_MOBILE);
                        } else if (isWifiConnected() && mIsWfcEnabled) {
                            /* This case was required to establish
                             * connection even if RSSI strength is not so strong */
                            addRadio(RnsManager.ALLOWED_RADIO_WIFI);
                            Slog.d(TAG, "Establishing connection over" +
                                    "Wifi even the RSSI strength is less than Rove in value");
                        } else if (isLteNetworkReady() && isImsOverLteEnabled()) {
                            addRadio(RnsManager.ALLOWED_RADIO_MOBILE);
                        } else if (isNetworkReady()) {
                            addRadio(RnsManager.ALLOWED_RADIO_NONE);
                        } else {
                            addRadio(RnsManager.ALLOWED_RADIO_DENY);
                        }
                    }
                    break;
                case PREFERENCE_CELLULAR_ONLY:
                    if (isLteNetworkReady()) {
                        if (isImsOverLteEnabled()) {
                            addRadio(RnsManager.ALLOWED_RADIO_MOBILE);
                        } else {
                            addRadio(RnsManager.ALLOWED_RADIO_NONE);
                        }
                    } else if (isNetworkReady()) {
                        addRadio(RnsManager.ALLOWED_RADIO_NONE);
                    } else {
                        addRadio(RnsManager.ALLOWED_RADIO_DENY);
                    }
                    break;
                case PREFERENCE_CELLULAR_PREFERRED:
                    if (isLteNetworkReady() && mLastSignalRsrp > WEAK_SIGNAL) {
                        if (isImsOverLteEnabled()) {
                            addRadio(RnsManager.ALLOWED_RADIO_MOBILE);
                        } else {
                            addRadio(RnsManager.ALLOWED_RADIO_NONE);
                        }
                    } else if (isWifiConnected() && mIsWfcEnabled && !isNetworkReady()) {
                        addRadio(RnsManager.ALLOWED_RADIO_WIFI);
                    } else if (isLteNetworkReady()) {
                        if (isImsOverLteEnabled()) {
                            addRadio(RnsManager.ALLOWED_RADIO_MOBILE);
                        } else {
                            addRadio(RnsManager.ALLOWED_RADIO_NONE);
                        }
                    } else if (isNetworkReady()) {
                        addRadio(RnsManager.ALLOWED_RADIO_NONE);
                    } else {
                        addRadio(RnsManager.ALLOWED_RADIO_DENY);
                    }
                    break;
                default:
                    break;
            }
        }
        Slog.d(TAG, "makeImsRadio: " + mAllowedRadio + "profile: " + profile);
        return transToReadableType(mAllowedRadio);
    }

    private int makeMmsRadio() {
        mAllowedRadio = 0;
        int profile = 0;
        RnsPolicy policy = mPolicies.get(POLICY_NAME_PREFERENCE);
        if (policy != null && policy.getUserPreference() != null) {
            profile = policy.getUserPreference().getMode();
            if (mIsWfcEnabled == false) {
                profile = PREFERENCE_CELLULAR_ONLY ;
            }

            switch (profile) {
                case PREFERENCE_WIFI_ONLY:
                    if (isWifiConnected()) {
                        addRadio(RnsManager.ALLOWED_RADIO_WIFI);
                    } else {
                        addRadio(RnsManager.ALLOWED_RADIO_DENY);
                    }
                    break;
                case PREFERENCE_WIFI_PREFERRED:
                    if (isWifiConnected() && mIsWfcEnabled && mWifiMgr.getConnectionInfo().getRssi() >
                        mPolicies.get(POLICY_NAME_ROVE_THRESHOLD).getWifiRoveThreshold().getRssiRoveIn()) {
                        addRadio(RnsManager.ALLOWED_RADIO_WIFI);
                    } else if (isLteNetworkReady() && mLastSignalRsrp > WEAK_SIGNAL) {
                        addRadio(RnsManager.ALLOWED_RADIO_MOBILE);
                    } else if (isWifiConnected()) {
                        /* This case was required to establish
                           connection even if RSSI strength is not so strong*/
                        addRadio(RnsManager.ALLOWED_RADIO_WIFI);
                        Slog.d(TAG, "Establishing connection over" +
                            "Wifi even the RSSI strength is less than Rove in value");
                    } else if (isLteNetworkReady() || isNetworkReady()) {
                        addRadio(RnsManager.ALLOWED_RADIO_MOBILE);
                    } else {
                        addRadio(RnsManager.ALLOWED_RADIO_DENY);
                    }
                    break;
                case PREFERENCE_CELLULAR_ONLY:
                    if (isNetworkReady() || isLteNetworkReady()) {
                        addRadio(RnsManager.ALLOWED_RADIO_MOBILE);
                    } else {
                        addRadio(RnsManager.ALLOWED_RADIO_DENY);
                    }
                    break;
                case PREFERENCE_CELLULAR_PREFERRED:
                    if (isNetworkReady() || isLteNetworkReady()) {
                        addRadio(RnsManager.ALLOWED_RADIO_MOBILE);
                    } else if (isWifiConnected()) {
                        addRadio(RnsManager.ALLOWED_RADIO_WIFI);
                    } else {
                        addRadio(RnsManager.ALLOWED_RADIO_DENY);
                    }
                    break;
                default:
                    break;
            }
        }
        Slog.d(TAG, "makeMmsRadio: " + mAllowedRadio + "profile: " + profile);
        return transToReadableType(mAllowedRadio);
    }
    private boolean isWifiConnected() {
        synchronized (this) {
        return mIsWifiEnabled && mIsWifiConnected;
    }
    }

    private boolean isLteNetworkReady() {
        boolean isLteReady = false;
        if (mLtePhoneState != null &&
            mLtePhoneState.getState() == ServiceState.STATE_IN_SERVICE &&
            isCellularNetworkAvailable()) {
            isLteReady = (mTeleMgr.getNetworkType() == TelephonyManager.NETWORK_TYPE_LTE) ;
        }
        Slog.d(TAG, "isLteNetworkReady " + isLteReady);
        if (isLteReady == false) {
            mLastSignalRsrp = WEAK_SIGNAL;
        }
        return isLteReady;
    }

    private boolean isNetworkReady() {
        if (mLtePhoneState != null &&
            mLtePhoneState.getState() == ServiceState.STATE_IN_SERVICE &&
            isCellularNetworkAvailable()) {
            int netType = getNetworkType() ;
            if (netType == 0 || netType == 1) {
                Slog.d(TAG, "isNetworkReady true");
                return true;
            }
        }
        Slog.d(TAG, "isNetworkReady false");
        return false;
    }
    /**
     * Returns current network type.
     */
    private int getNetworkType() {

    TelephonyManager mTelephonyManager = (TelephonyManager)
                mContext.getSystemService(Context.TELEPHONY_SERVICE);
        int networkType = mTelephonyManager.getNetworkType();
        switch (networkType) {
        case TelephonyManager.NETWORK_TYPE_GPRS:
        case TelephonyManager.NETWORK_TYPE_EDGE:
        case TelephonyManager.NETWORK_TYPE_CDMA:
        case TelephonyManager.NETWORK_TYPE_1xRTT:
        case TelephonyManager.NETWORK_TYPE_IDEN:
            return 0;
        case TelephonyManager.NETWORK_TYPE_UMTS:
        case TelephonyManager.NETWORK_TYPE_EVDO_0:
        case TelephonyManager.NETWORK_TYPE_EVDO_A:
        case TelephonyManager.NETWORK_TYPE_HSDPA:
        case TelephonyManager.NETWORK_TYPE_HSUPA:
        case TelephonyManager.NETWORK_TYPE_HSPA:
        case TelephonyManager.NETWORK_TYPE_EVDO_B:
        case TelephonyManager.NETWORK_TYPE_EHRPD:
        case TelephonyManager.NETWORK_TYPE_HSPAP:
            return 1;
        case TelephonyManager.NETWORK_TYPE_LTE:
            return 2;
        default:
            return -1;
        }
    }
    /**
     * Check cellular Network is available or not .
     * excluding Wifi connectivity
     * @return network available or not
     */
    private boolean isCellularNetworkAvailable() {
        boolean network = false;
        NetworkInfo[] infos = mConnMgr.getAllNetworkInfo();
        int mode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0);
        if (mode == 1) {
            return false;
        }
        for (int i = 0; i < 2; i++) {
            if (infos[i].isAvailable() && !(infos[i].getType() == ConnectivityManager.TYPE_WIFI)) {
                network = true;
            }
        }
        Slog.d(TAG, "isCellularNetworkAvailable : " + network);
        if (network) {
            return true;
        }
        return false;
    }

    private int[] enumerateBits(long val) {
        int size = Long.bitCount(val);
        int[] result = new int[size];
        int index = 0;
        int resource = 0;
        while (val > 0) {
            if ((val & 1) == 1) { result[index++] = resource; }
            val = val >> 1;
            resource++;
        }
        return result;
    }

    private int transToReadableType(int val) {
        //simple impl. here, can be extended in the future
        if (val == 1) {
            Slog.d(TAG, "make Radio = ALLOWED_RADIO_WIFI");
            return RnsManager.ALLOWED_RADIO_WIFI;
        } else if (val == 2) {
            Slog.d(TAG, "make Radio = ALLOWED_RADIO_MOBILE");
            return RnsManager.ALLOWED_RADIO_MOBILE;
        } else if (val == 4) {
            Slog.d(TAG, "make Radio = ALLOWED_RADIO_DENY");
            return RnsManager.ALLOWED_RADIO_DENY;
        }
         else if (val == 8) {
            Slog.d(TAG, "make Radio = ALLOWED_RADIO_MAX");
            return RnsManager.ALLOWED_RADIO_MAX;
        }
        Slog.d(TAG, "make Radio = ALLOWED_RADIO_NONE");
        return RnsManager.ALLOWED_RADIO_NONE;
    }

    private void addRadio(int connectionType) {
        if (connectionType < RnsManager.ALLOWED_RADIO_MAX) {
            mAllowedRadio |= 1 << connectionType;
        } else {
            throw new IllegalArgumentException("connectionType out of range");
        }
    }

    private boolean isMatchRoveIn() {
        int profile = PREFERENCE_NONE;
        RnsPolicy policy = mPolicies.get(POLICY_NAME_PREFERENCE);

        if (policy != null && policy.getUserPreference() != null) {
            profile = policy.getUserPreference().getMode();
        }

        //1. The Handover is not initiated from handset if the WFC
        //   preference is set "Cellular only" mode.
        if (profile == PREFERENCE_CELLULAR_ONLY || profile == PREFERENCE_NONE) {
            Slog.d(TAG, "isMatchRoveIn = false, cellular only/none");
            return false;
        }

        //2. RAT signal strength criteria not met.
        policy = mPolicies.get(POLICY_NAME_ROVE_THRESHOLD);
        if (policy != null && policy.getWifiRoveThreshold() != null) {

            if (mLastRssi > policy.getWifiRoveThreshold().getRssiRoveIn()) {
                Slog.d(TAG, "isMatchRoveIn signal strength criteria met");
            } else {
                Slog.d(TAG, "isMatchRoveIn = false, rssi issue");
                return false;
            }
        }

        //3. check current pdn connections status
       /* if (isEpdgImsConnected || (!isEpdgImsConnected && !isLteImsConnected)) {
            Slog.d(TAG, "isMatchRoveIn = false, check pdn connection");
            return false;
        }*/
        if (isEpdgImsConnected) {
            Slog.d(TAG, "isMatchRoveIn = false, check pdn connection");
            return false;
        }

        if (isLteImsConnected && profile == PREFERENCE_CELLULAR_PREFERRED) {
            Slog.d(TAG, "isMatchRoveIn = false, cellular preferred");
            return false;
        }

        if (isWifiConnected() && mIsWfcEnabled && System.currentTimeMillis() - mStartTime > 2000) {
            return true;
        }

        Slog.d(TAG, "isMatchRoveIn = false");
        return false;
    }

    private boolean isMatchRoveOut() {
        int profile = PREFERENCE_NONE;
        RnsPolicy policy = mPolicies.get(POLICY_NAME_PREFERENCE);

        if (policy != null && policy.getUserPreference() != null) {
            profile = policy.getUserPreference().getMode();
        }

        //1. The Handover is not initiated from handset if the WFC
        //   preference is set "Wi-Fi only" mode.
        if (profile == PREFERENCE_WIFI_ONLY || profile == PREFERENCE_NONE) {
            Slog.d(TAG, "isMatchRoveOut = false, profile issue");
            return false;
        }

        //2. RAT signal strength criteria not met.
        policy = mPolicies.get(POLICY_NAME_ROVE_THRESHOLD);
        if (policy != null && policy.getWifiRoveThreshold() != null &&
            profile != PREFERENCE_CELLULAR_PREFERRED) {
            if (mLastRssi < policy.getWifiRoveThreshold().getRssiRoveOut()) {
                Slog.d(TAG, "isMatchRoveOut signal strength criteria met");
            } else {
                Slog.d(TAG, "isMatchRoveOut = false, rssi issue");
                return false;
            }
        }

        //3. check current pdn connections status
        if (isLteImsConnected) {
            Slog.d(TAG, "isMatchRoveOut = false, check pdn connection");
            return false;
        }


        if (mIsWfcEnabled && isLteNetworkReady() &&
            System.currentTimeMillis() - mStartTime > 2000) {
            return true;
        }

        Slog.d(TAG, "isMatchRoveOut = false");
        return false;
    }

    private void decideHandover() {
        //TODO: consider a pdn is connecting case, we should not trigger hadover
        if (isHandoverInProgress()) {
            Slog.d(TAG, "decideHandover - handover in progress");
            return ;
        }
        RnsPolicy policy = mPolicies.get(POLICY_NAME_PREFERENCE);
        if (policy != null && policy.getUserPreference() != null) {
            int profile = policy.getUserPreference().getMode();
            if (mIsWfcEnabled == false) {
                profile = PREFERENCE_CELLULAR_ONLY ;
            }
            switch (profile) {
                case PREFERENCE_WIFI_ONLY:
                    if (isWifiConnected() && mIsWfcEnabled) {
                        startRoveIn();
                    } else {
                        Slog.d(TAG, "Need to inform to disconnect as for " +
                            "Wifi Only can't connect to other radio");
                        mState = RnsManager.STATE_DEFAULT;
                        mConnMgr.connectToRadio(ConnectivityManager.TYPE_NONE);
                    }
                    break;
                case PREFERENCE_WIFI_PREFERRED:
                    if (isWifiConnected() && mWifiMgr.getConnectionInfo().getRssi() >=
                        mPolicies.get(POLICY_NAME_ROVE_THRESHOLD).getWifiRoveThreshold().getRssiRoveIn()) {
                        startRoveIn();
                    } else if ((isLteNetworkReady() && mLastSignalRsrp > WEAK_SIGNAL) &&
                                (mWifiMgr.getConnectionInfo().getRssi() <
                                mPolicies.get(POLICY_NAME_ROVE_THRESHOLD).getWifiRoveThreshold().
                                getRssiRoveOut())) {
                        if (isImsOverLteEnabled()) {
                            startRoveOut();
                        } else {
                            Slog.d(TAG, "Volte disabled will not establish IMS PDN");
                            mState = RnsManager.STATE_DEFAULT;
                            mConnMgr.connectToRadio(ConnectivityManager.TYPE_NONE);
                        }
                    } else if (isWifiConnected() && mIsWfcEnabled) {
                        /* This case was required to establish
                           connection even if RSSI strength is not so strong*/
                        Slog.d(TAG, "Establishing connection over" +
                            "Wifi even the RSSI strength is less than Rove in value");
                        startRoveIn();
                    } else if (isLteNetworkReady() && isImsOverLteEnabled()) {
                        startRoveOut();
                    } else {
                        Slog.d(TAG, "Need to inform to disconnect as for " +
                            "Wifi Preferred can't connect to other radio");
                        mState = RnsManager.STATE_DEFAULT;
                        mConnMgr.connectToRadio(ConnectivityManager.TYPE_NONE);
                    }
                    break;
                case PREFERENCE_CELLULAR_ONLY:
                    if (isLteNetworkReady() && isImsOverLteEnabled()) {
                        startRoveOut();
                    } else {
                        Slog.d(TAG, "Need to inform to disconnect as for " +
                            "Cellular Only can't connect to other radio");
                        boolean isSkipImsPdn = SystemProperties.getBoolean("net.ims.skip", false);
                        if (isSkipImsPdn) {
                            Slog.d(TAG, "Ignore IMS disconnected");
                            return;
                        }
                        mState = RnsManager.STATE_DEFAULT;
                        mConnMgr.connectToRadio(ConnectivityManager.TYPE_NONE);
                    }
                    break;
                case PREFERENCE_CELLULAR_PREFERRED:
                    if ((mIsSettingChanged || mServiceState == 1 ) && isEpdgImsConnected) {
                        if (isLteNetworkReady() && isImsOverLteEnabled()) {
                                Slog.d(TAG, "Preference changed to Cell Preferred, do Roveout");
                                startRoveOut();
                            } else if (isNetworkReady()) {
                                    Slog.d(TAG, "IMS connection can't be establish for " +
                                    "Cellular Preferred as 3G/2G is available");
                                    boolean isSkipImsPdn =
                                                SystemProperties.getBoolean("net.ims.skip", false);
                                    if (isSkipImsPdn) {
                                        Slog.d(TAG, "Ignore IMS disconnected");
                                        return;
                              }
                            mState = RnsManager.STATE_DEFAULT;
                            mConnMgr.connectToRadio(ConnectivityManager.TYPE_NONE);
                        }
                    return;
                    }
                    //case 1.
                    if (isEpdgImsConnected) {
                     // condition 1.
                        if (mWifiMgr.getConnectionInfo().getRssi() <
                                mPolicies.get(POLICY_NAME_ROVE_THRESHOLD).getWifiRoveThreshold()
                                .getRssiRoveOut()) {
                            if (isLteNetworkReady() && isImsOverLteEnabled()) {
                                Slog.d(TAG, "Cell Preferred,wifi rssi drops,do Roveout");
                                startRoveOut();
                            } else if (isNetworkReady()) {
                                Slog.d(TAG, "IMS connection can't be establish for " +
                                        "Cellular Preferred as 3G/2G is available");
                                boolean isSkipImsPdn =
                                        SystemProperties.getBoolean("net.ims.skip", false);
                                if (isSkipImsPdn) {
                                    Slog.d(TAG, "Ignore IMS disconnected");
                                    return;
                                }
                                mState = RnsManager.STATE_DEFAULT;
                                mConnMgr.connectToRadio(ConnectivityManager.TYPE_NONE);
                            }
                        }
                        return;
                    }

                    // case 2.
                    if (isLteImsConnected && isLteNetworkReady()) {

                        // condition 1.
                        if (mLastSignalRsrp < WEAK_SIGNAL && isWifiConnected() && mWifiMgr
                                .getConnectionInfo().getRssi() > mPolicies
                                .get(POLICY_NAME_ROVE_THRESHOLD).getWifiRoveThreshold()
                                .getRssiRoveIn()) {
                            Slog.d(TAG, "Cell Preferred rsrp low do Rovein");
                            startRoveIn();
                        }
                        // condition 2.
                        if (!isImsOverLteEnabled()) {
                            if (isWifiConnected() && mWifiMgr.getConnectionInfo().getRssi() >
                                mPolicies.get(POLICY_NAME_ROVE_THRESHOLD)
                                .getWifiRoveThreshold().getRssiRoveIn()) {
                                Slog.d(TAG, "Cell Preferred IMS over LTE off do Rovein");
                                startRoveIn() ;
                             } else {
                                Slog.d(TAG, "Cell Preferred IMS over LTE off no wifi do fallback");
                                mState = RnsManager.STATE_DEFAULT;
                                mConnMgr.connectToRadio(ConnectivityManager.TYPE_NONE);
                            }
                        }
                        return;
                    }
                    // case 3. No IMS connection is available
                    if (isLteNetworkReady() && isImsOverLteEnabled()) {
                        Slog.d(TAG, "Cell Preferred create IMS over LTE");
                        startRoveOut();
                        return;
                    } else if (!isNetworkReady() && isWifiConnected() &&
                                mWifiMgr.getConnectionInfo().getRssi() >
                                mPolicies.get(POLICY_NAME_ROVE_THRESHOLD).getWifiRoveThreshold()
                                .getRssiRoveIn()) {
                            Slog.d(TAG, "Cell Preferred no signal except wifi good rssi do Rovein");
                            startRoveIn() ;
                            return;
                        } else if (!isNetworkReady() && isWifiConnected()) {
                            Slog.d(TAG, "Cell Preferred no signal except wifi available do Rovein");
                            startRoveIn();
                        }
                    break;
                default:
                    break;
            }

        }
    }

    /* LTE -> Wifi(ePDG) */
    private void startRoveIn() {
        Slog.d(TAG, "startRoveIn");

        //Check ePDG is active then return
        if (isEpdgImsConnected) {
            Slog.d(TAG, "No rove-in");
            if (mState == RnsManager.STATE_ROVEIN) {
                mState = RnsManager.STATE_DEFAULT;
            }
            return;
        }

        mStartTime = System.currentTimeMillis();
        synchronized (this) {
            if (mState == RnsManager.STATE_ROVEIN) {
                Slog.d(TAG, "RoveIn is in progress");
                return;
            }
            if(!(isNetworkReady() && mIsCallActive)) {
                tryConnectToRadio(ConnectivityManager.TYPE_WIFI);
            }
        }
    }

    /* Wifi(ePDG) -> LTE */
    private void startRoveOut() {
        Slog.d(TAG, "startRoveOut");

        //Check ePDG is active then return
        if (isLteImsConnected) {
            Slog.d(TAG, "No rove-out");
            if (mState == RnsManager.STATE_ROVEOUT) {
                mState = RnsManager.STATE_DEFAULT;
            }
            return;
        }

        mStartTime = System.currentTimeMillis();
        synchronized (this) {
            if (mState == RnsManager.STATE_ROVEOUT) {
                Slog.d(TAG, "RoveOut is in progress");
                return;
            }
            tryConnectToRadio(ConnectivityManager.TYPE_MOBILE);
        }
    }

    private boolean isHandoverInProgress() {
        synchronized (this) {
            return (mState == RnsManager.STATE_ROVEOUT) || (mState == RnsManager.STATE_ROVEIN);
        }
    }
    /*private synchronized int nextNetworkRequestId() {
        return mNextNetworkRequestId++;
    }*/
   private void sendRoveOutAlert() {
        Slog.d(TAG, "send RoveOut Alert");
        mConnMgr.sendRoveOutAlert();

    }
    private boolean needToSendAlertWarning() {
        RnsPolicy userPolicy = mPolicies.get(POLICY_NAME_PREFERENCE);
        int mPreference =  userPolicy.getUserPreference().getMode() ;
        RnsPolicy rovePolicy = mPolicies.get(POLICY_NAME_ROVE_THRESHOLD);
        int mDiff = 5;
        int roveoutvalue = rovePolicy.getWifiRoveThreshold().getRssiRoveOut() ;
        boolean sendAlertWarning = false ;
        if (isHandoverInProgress()) {
            return sendAlertWarning;
        }
        switch(mPreference) {
            case PREFERENCE_WIFI_ONLY:
                if ((!isEpdgImsConnected && mIsEpdgConnectionChanged) || (isWifiConnected() &&
                    mIsWfcEnabled && mLastRssi <= (roveoutvalue + mDiff))) {
                    sendAlertWarning = true ;
            }
            break ;
            case PREFERENCE_WIFI_PREFERRED:
            case PREFERENCE_CELLULAR_PREFERRED:
                if ((!isEpdgImsConnected && mIsEpdgConnectionChanged) || (isWifiConnected() &&
                    mIsWfcEnabled && mLastRssi <= (roveoutvalue + mDiff))) {
                    if (isImsOverLteEnabled() && !isLteNetworkReady()) {
                        sendAlertWarning = true ;
                    } else if (!isImsOverLteEnabled()) {
                        sendAlertWarning = true ;
                    }
                }
                break ;
            default:
                    break;
        }
        Slog.d(TAG, "sendAlertWarning = " + sendAlertWarning + " iswificonnected : " +
            isWifiConnected() + " IsWfcEnabled : " +    mIsWfcEnabled + " roveoutvalue : " +
            roveoutvalue + "LastRssi : " + mLastRssi + "isEpdgConnected : " + isEpdgImsConnected +
            " isLteNetworkReady: " + isLteNetworkReady());
        return sendAlertWarning ;

    }
    ConnectivityManager.NetworkCallback mNetworkCallback
        = new ConnectivityManager.NetworkCallback() {

        @Override
        public void onAvailable(Network network) {
            try {
                if ((mConnMgr.getNetworkInfo(network).getType() ==
                    ConnectivityManager.TYPE_MOBILE_IMS)) {
                    Slog.d(TAG, "NetworkCallback - onAvailable:" + network);
                    if (isHandoverInProgress() || (mState == RnsManager.STATE_PENDING)) {
                        mState = RnsManager.STATE_DEFAULT;
                    }
                }
            } catch (NullPointerException ne) {
                 Slog.d(TAG, "NetworkCallback not available in onAvailable:");
            }
        }

        @Override
        public void onUnavailable() {
            Slog.d(TAG, "NetworkCallback - onUnavailable");
            mState = RnsManager.STATE_PENDING;
        }

        @Override
        public void onLost(Network network) {
            try {
                if ((mConnMgr.getNetworkInfo(network).getType() ==
                    ConnectivityManager.TYPE_MOBILE_IMS)) {
                    Slog.d(TAG, "NetworkCallback - onLost:" + network);
                }
            } catch (NullPointerException e) {
                return;
            }
        }
    };

    ConnectivityManager.NetworkCallback mDefaultNetworkCallback
        = new ConnectivityManager.NetworkCallback() {
    };

    private static final int EVENT_WIFI_RSSI_UPDATE = 0;
    private static final int EVENT_REGISTER_RNS_AGENT = 1;
    private static final int EVENT_APPLY_WIFI_CALL_SETTINGS = 10;
    private static final int EVENT_WIFI_DISCONNECT = 100;
    private static final int EVENT_WIFI_DISABLE_ACTION = 101;
    private static final int EVENT_WIFI_DISABLE_EXPIRED = 102;
    private static final int EVENT_RAT_CONNECTIVITY_CHANGE = 1000;
    private static final int EVENT_WIFI_STATE_CHANGED_ACTION = 10000;
    private static final int EVENT_APPLY_IMS_OVER_LTE_SETTINGS = 100000;
    /**
     * internal handler for events.
     */
    private class InternalHandler extends Handler {
        public InternalHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            mHandoverEvent = msg.what;
            switch (msg.what) {
            case EVENT_WIFI_RSSI_UPDATE:
                handleEventWifiRssiUpdate(msg.arg1);
                break;
            case EVENT_REGISTER_RNS_AGENT:
                break;
            case EVENT_APPLY_WIFI_CALL_SETTINGS:
                handleDefaultPdnRequest();
                handleEventApplyWifiCallSettings();
                break;
            case EVENT_WIFI_DISCONNECT:
                handleEventWifiDisconnect();
                break;
            case EVENT_RAT_CONNECTIVITY_CHANGE:
                handleDefaultPdnRequest();
                handleEventRatConnectivityChange();
                break;
            case EVENT_WIFI_STATE_CHANGED_ACTION:
                handleEventWifiStateChangedAction();
                break;
            case EVENT_APPLY_IMS_OVER_LTE_SETTINGS:
                handleDefaultPdnRequest();
                handleEventImsOverLteSettings();
                break;
            case EVENT_WIFI_DISABLE_EXPIRED:
                handleWifiDisabledExpired();
                break;
            case EVENT_WIFI_DISABLE_ACTION:
                handleWifiDisableAction();
                break;
            default:
                Slog.d(TAG, "Unknown message");
                break;
            }
        }
    }

    private void handleEventWifiRssiUpdate(int newRssi) {
        if (DEBUG) {
            int testRssi = SystemProperties.getInt("persist.net.test.rssi", 0);
            if (testRssi != 0) {
                newRssi = testRssi;
            }
        }
        /* Block handover to Wifi, if LTE is selected for first time boot in case of WiFi preferred
         * till MAX_REG_WAIT is up. */
        if (mIsFirstRequest == true) {
            Slog.d(TAG, "First Request after bootup, RAT Selected = LTE " +
                    "mIsFirstRequst = " + mIsFirstRequest);
            if ((System.currentTimeMillis() - mLteRegTime) > MAX_REG_WAIT) {
                mIsFirstRequest = false;
                Slog.d(TAG, "Max wait time Up, allow handover to wifi");
            } else {
                Slog.d(TAG, "Block the handover to Wifi:: Waiting time :" +
                        (System.currentTimeMillis() - mLteRegTime) +
                        " less than MAX REG WAIT time: " + MAX_REG_WAIT);
                return;
            }
        }
        mLastRssi = newRssi;
        Slog.d(TAG, "handleEventWifiRssiUpdate: " + newRssi);
        if (!(isImsOverLteEnabled() || mIsWfcEnabled)) {
            return;
        }
        if (isWifiConnected()) {
            decideHandover();
        }

         if (needToSendAlertWarning()) {
            Slog.d(TAG, "send Rove Out Alert warning for rssi " + mLastRssi);
            sendRoveOutAlert();
         }
    }

    private void handleEventApplyWifiCallSettings() {
        mIsWfcEnabled = TelephonyManager.WifiCallingChoices.ALWAYS_USE ==
                        Settings.System.getInt(mContext.getContentResolver(),
                                               Settings.System.WHEN_TO_MAKE_WIFI_CALLS, -1);
        Slog.d(TAG, "handleEventApplyWifiCallSettings, mIsWfcEnabled = " + mIsWfcEnabled);
        RnsPolicy policy = mPolicies.get(POLICY_NAME_PREFERENCE);
        if (policy != null && policy.getUserPreference() != null) {
            policy.getUserPreference().setMode(
                Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SELECTED_WFC_PREFERRENCE, 0));
            Slog.d(TAG, " Preference = " + policy.getUserPreference().getMode());
        }

        policy = mPolicies.get(POLICY_NAME_ROVE_THRESHOLD);
        if (policy != null && policy.getWifiRoveThreshold() != null) {
            policy.getWifiRoveThreshold().setRssiRoveIn(
                Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.RNS_WIFI_ROVE_IN_RSSI, 0));

            policy.getWifiRoveThreshold().setRssiRoveOut(
                Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.RNS_WIFI_ROVE_OUT_RSSI, 0));
            Slog.d(TAG, " RoveIn = " + policy.getWifiRoveThreshold().getRssiRoveIn() +
                   " RoveOut = " + policy.getWifiRoveThreshold().getRssiRoveOut());
        }
        if (mState != RnsManager.STATE_PENDING) {
            mState = RnsManager.STATE_DEFAULT;
        }
        if (!(isImsOverLteEnabled() || mIsWfcEnabled)) {
            return;
        }
        mIsSettingChanged = true;
        decideHandover();
        mIsSettingChanged = false;
    }

    private void handleDefaultPdnRequest() {
        Slog.d(TAG, "handleDefaultPdnRequest");

        if ((mLtePhoneState != null &&
            mLtePhoneState.getState() == ServiceState.STATE_IN_SERVICE &&
            mTeleMgr.getNetworkType() == TelephonyManager.NETWORK_TYPE_LTE &&
            mIsImsOverLteEnabled) ||
            isLteImsConnected) {
            int profile = getPolicyProfile();
            if (PREFERENCE_WIFI_PREFERRED == profile ||
                PREFERENCE_CELLULAR_PREFERRED == profile) {
                try {
                    synchronized (this) {
                        if (!mIsDefaultRequestEnabled) {
                            mConnMgr.requestNetwork(DEFAULT_REQUEST, mDefaultNetworkCallback);
                            mIsDefaultRequestEnabled = true;
                            Slog.d(TAG, "Register LTE requst:" + profile);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    Slog.e(TAG, "fail to register");
                }
            }
        } else {
            try {
                synchronized (this) {
                    if (mIsDefaultRequestEnabled) {
                        mConnMgr.unregisterNetworkCallback(mDefaultNetworkCallback);
                        mIsDefaultRequestEnabled = false;
                        Slog.d(TAG, "Unregister LTE requst");
                    }
                }
            } catch (IllegalArgumentException e) {
                Slog.e(TAG, "fail to unregister");
            }
        }
    }

    private void handleEventRatConnectivityChange() {
        int profile = PREFERENCE_NONE;
        RnsPolicy policy = mPolicies.get(POLICY_NAME_PREFERENCE);

        if (policy.getUserPreference() != null) {
            profile = policy.getUserPreference().getMode();
        }
        if (mState != RnsManager.STATE_PENDING) {
            mState = RnsManager.STATE_DEFAULT ;
        }
        if (!(isImsOverLteEnabled() || mIsWfcEnabled)) {
            return;
        }
        Slog.d(TAG, "handle Event RAT Connectivity change ");
         if (mLtePhoneState.getState() == ServiceState.STATE_POWER_OFF) {
                Slog.d(TAG, "mLtePhoneState went to power off ");
                mServiceState = 2 ;
        }
        if (mLtePhoneState.getState() == ServiceState.STATE_IN_SERVICE && mServiceState == 2) {
            Slog.d(TAG, "mLtePhoneState went to power on ");
            mServiceState = 1;
        }
        if (profile != PREFERENCE_WIFI_ONLY) {
            decideHandover();
        }
        if (mServiceState == 1) {
            mServiceState = 0;
        }
    }
    public void handleEventWifiDisconnect() {

        Slog.d(TAG, "handle Event Wifi Disconnect ");
        int profile = PREFERENCE_NONE;
        RnsPolicy policy = mPolicies.get(POLICY_NAME_PREFERENCE);

        if (policy != null && policy != null && policy.getUserPreference() != null) {
            profile = policy.getUserPreference().getMode();
        }
        if (mState != RnsManager.STATE_PENDING) {
            mState = RnsManager.STATE_DEFAULT ;
        }
        if (!(isImsOverLteEnabled() || mIsWfcEnabled)) {
            return;
        }
        if (profile != PREFERENCE_CELLULAR_ONLY || mIsWfcEnabled) {
            decideHandover();
        }
    }
    /**
     * Handle Wifi Enable & Disable state.
     */
    public void handleEventWifiStateChangedAction() {
        Slog.d(TAG, "handleEventWifiStateChangedAction ");
        int profile = PREFERENCE_NONE;
        RnsPolicy policy = mPolicies.get(POLICY_NAME_PREFERENCE);

        if (policy.getUserPreference() != null) {
            profile = policy.getUserPreference().getMode();
        }
        if (mState != RnsManager.STATE_PENDING) {
            mState = RnsManager.STATE_DEFAULT ;
        }
        if (!(isImsOverLteEnabled() || mIsWfcEnabled)) {
            return;
        }
        if (profile != PREFERENCE_CELLULAR_ONLY && mIsWfcEnabled == true) {
            decideHandover();
        }
        if (mIsWfcEnabled == true && mIsWifiEnabled == false) {
            Slog.d(TAG, "send Rove Out Alert warning ");
            sendRoveOutAlert();
         }
    }
    /**
     * Handle Ims Over Lte related settings.
     */
    public void handleEventImsOverLteSettings() {
        Slog.d(TAG, "handleEventImsOverLteSettings ");
        int profile = PREFERENCE_NONE;
        RnsPolicy policy = mPolicies.get(POLICY_NAME_PREFERENCE);

        if (policy.getUserPreference() != null) {
            profile = policy.getUserPreference().getMode();
        }
        synchronized (this) {
            if (Settings.Global.getInt(mContext.getContentResolver(),
                        Settings.Global.IMS_SWITCH, 0) == 1) {
                mIsImsOverLteEnabled = true ;
            } else {
                mIsImsOverLteEnabled = false ;
            }
        }
        Slog.d(TAG, "mIsImsOverLteEnabled = " + mIsImsOverLteEnabled);
        if (!(mIsImsOverLteEnabled || mIsWfcEnabled)) {
            return;
        }
        if (profile != PREFERENCE_WIFI_ONLY) {
            decideHandover();
        }
    }

   /**
    * Handle Wi-Fi disable procedure to check handover or disconnect ePDG.
    */
   private void handleWifiDisableAction() {
        Slog.d(TAG, "handle Wifi Disable Action:");

        mIsWifiConnected = false;
        mLastRssi = DISCONNECT_RSSI;

        int profile = getPolicyProfile();

        if (profile != PREFERENCE_WIFI_ONLY) {
            if (isLteNetworkReady() && mLastSignalRsrp > WEAK_SIGNAL) {
                if (isImsOverLteEnabled()) {
                    mIsWifiDisabling = false;
                    Slog.d(TAG, "Epdg is disconnected & disable wifi");
                    mWifiMgr.setWifiDisabledByEpdg(mWifiDisableFlag);
                    tryConnectToRadio(ConnectivityManager.TYPE_MOBILE);
                    return;
                }
            }
        }

        Slog.d(TAG, "Disable ePDG connection");
        mState = RnsManager.STATE_DEFAULT;
        mConnMgr.connectToRadio(ConnectivityManager.TYPE_NONE);
    }

    private void handleWifiDisabledExpired() {
        Slog.d(TAG, "handle Wifi Disable Action:" + mIsWifiDisabling);
        if (mIsWifiDisabling) {
            mIsWifiDisabling = false;
            mWifiMgr.setWifiDisabledByEpdg(mWifiDisableFlag);
        }
    }
    /**
     * Wifi Rssi Monitor, consider to remove.
     */
    private class WifiRssiMonitor extends AsyncTask<Void, Void, Void> {

        WifiRssiMonitor() {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ie) {
                return;
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            checkWifi();
            return null;
        }

        private void checkWifi() {
            Slog.d(TAG, "checkWifi");
        }
    }
    /**
     * check Ims Over Lte is enabled or not.
     * @return ImsOverLte enabled or not
     */
    private boolean isImsOverLteEnabled() {
        Slog.d(TAG, "check is Ims over Lte Enable " + mIsImsOverLteEnabled);
        return mIsImsOverLteEnabled ;

    }
    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");

        int i = 0;
        pw.println("Policies:");
        pw.increaseIndent();
        for (String key : mPolicies.keySet()) {
            pw.println(i + "  policy[" + key + "]: " + mPolicies.get(key));
            i++;
        }
        pw.println("(none(-1)|wifi_only(0)|wifi_preferred(1)" +
                   "|cellular_only(2)|cellular_preferred(3))");

        pw.decreaseIndent();
        pw.println();
        pw.println("Status:");
        pw.increaseIndent();
        pw.println("isWifiConnected = " + isWifiConnected());
        pw.println("isWfcEnabled = " + mIsWfcEnabled);
        pw.println("isHandoverInProgress = " + isHandoverInProgress());
        pw.println("isLteNetworkReady = " + isLteNetworkReady());
        pw.println("isLteImsConnected = " + isLteImsConnected);
        pw.println("isEpdgImsConnected = " + isEpdgImsConnected);
        pw.println("isImsOverLteEnabled = " + isImsOverLteEnabled());
        pw.println("isNetworkReady = " + isNetworkReady());
        pw.println("isCellularNetworkAvailable = " + isCellularNetworkAvailable());
        pw.decreaseIndent();
        pw.println();
        pw.println("Radio Selection for IMS type connection: " + makeImsRadio());
        pw.println("Radio Selection for MMS type connection: " + makeMmsRadio());
        pw.println("none(-1)|wifi(0)|moible(1)|all(2)");
    }

    private void dump() {
        Slog.d(TAG, "--- dump ---");
        for (String key : mPolicies.keySet()) {
            Slog.d(TAG, "policy[" + key + "]:" + mPolicies.get(key));
        }
        Slog.d(TAG, "isWifiConnected = " + isWifiConnected());
        Slog.d(TAG, "isWfcEnabled = " + mIsWfcEnabled);
        Slog.d(TAG, "isLteNetworkReady = " + isLteNetworkReady());
        Slog.d(TAG, "--- end ---");
    }


    /**
     * Notify RNS about Wi-Fi disable event.
     * @param flag the flag type of disable method.
     * @return the Wi-Fi should be disabled or not.
     * {@hide}
     */
    public boolean isNeedWifiConnected(int flag) {
        Slog.d(TAG, "isNeedWifiConnected:"
                + mIsWfcEnabled + ":" + isEpdgImsConnected + ":" + flag);

        if (mIsWfcEnabled && isEpdgImsConnected) {
            mIsWifiDisabling = true;
            mWifiDisableFlag = flag;
            mHandler.sendMessageAtFrontOfQueue(
                        mHandler.obtainMessage(EVENT_WIFI_DISABLE_ACTION, 0));
            mHandler.sendEmptyMessageDelayed(EVENT_WIFI_DISABLE_EXPIRED,
                            DISABLE_WIFI_GUARD_TIMER);
            return true;
        }
        mIsWifiDisabling = false;

        return false;
    }


    private int getPolicyProfile() {
        int profile = PREFERENCE_NONE;
        RnsPolicy policy = mPolicies.get(POLICY_NAME_PREFERENCE);
        if (policy != null && policy.getUserPreference() != null) {
            profile = policy.getUserPreference().getMode();
        }
        Slog.d(TAG, "profile:" + profile);
        return profile;
    }
}

