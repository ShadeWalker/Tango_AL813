package com.mediatek.ims.internal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;

import com.mediatek.ims.ImsAdapter;
import com.mediatek.ims.ImsAdapter.VaSocketIO;
import com.mediatek.ims.ImsAdapter.VaEvent;
import com.mediatek.ims.ImsEventDispatcher;
import com.mediatek.ims.VaConstants;
import com.mediatek.xlog.Xlog;


import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;

import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.DhcpResults;
import android.net.LinkProperties;
import android.net.LinkAddress;
import android.net.NetworkUtils;

import android.os.Looper;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.AsyncResult;
import android.os.ServiceManager;
import android.os.INetworkManagementService;
import android.os.RemoteException;

//import android.net.MobileDataStateTracker;
import android.net.NetworkInfo;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.internal.telephony.IPhoneSubInfo;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

//import com.android.internal.telephony.dataconnection.DcFailCause;
import com.android.internal.telephony.DctConstants;

import com.mediatek.internal.telephony.DedicateBearerProperties;
import com.mediatek.internal.telephony.DefaultBearerConfig;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.internal.telephony.PcscfInfo;
import com.mediatek.internal.telephony.QosStatus;
import com.mediatek.internal.telephony.TftStatus;
import com.mediatek.internal.telephony.PacketFilterInfo;
import com.mediatek.internal.telephony.PcscfAddr;
import com.mediatek.rns.RnsManager;

import com.android.ims.mo.ImsLboPcscf;
import com.android.ims.ImsConfig;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;

import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkRequest.Builder;
import static android.net.ConnectivityManager.TYPE_NONE;
import android.provider.Settings;



public class DataDispatcher implements ImsEventDispatcher.VaEventDispatcher {
    private static final String TAG = DataDispatcherUtil.TAG;
    private static final boolean DUMP_TRANSACTION = true;
    private static final boolean DBG = true;
    private boolean isWifiConnection;
    private boolean mPdnActive = false;
    private ImsAdapter.VaEvent mDelayedEvent = null;
    private ImsAdapter.VaEvent mDelayedEventAct = null;
    private ImsAdapter.VaEvent mDelayedEventModified = null;
    private int mCid= INVALID_CID;
    // TODO: working around for testing, need to change back to IMS later
    private static String IMS_APN = PhoneConstants.APN_TYPE_IMS;
    private static String EMERGENCY_APN = PhoneConstants.APN_TYPE_EMERGENCY;

    private static String FEATURE_ENABLE_IMS = "enableIMS";
    private static String FEATURE_ENABLE_EMERGENCY = "enableEmergency";

    public static final String REASON_BEARER_ACTIVATION = "activation";
    public static final String REASON_BEARER_DEACTIVATION = "deactivation";
    public static final String REASON_BEARER_MODIFICATION = "modification";
    public static final String REASON_BEARER_ABORT = "abort";

    private static final int IMC_CONCATENATED_MSG_TYPE_NONE = 0;
    private static final int IMC_CONCATENATED_MSG_TYPE_ACTIVATION = 1;
    private static final int IMC_CONCATENATED_MSG_TYPE_MODIFICATION = 2;
    private static final int IMC_CONCATENATED_MSG_TYPE_DELETION = 3;

    public static final String PROPERTY_MANUAL_PCSCF_ADDRESS = "ril.pcscf.addr";
    public static final String PROPERTY_MANUAL_PCSCF_PORT = "ril.pcscf.port";

    public static final int IPV4_IMS = 0;
    public static final int IPV6_IMS = 1;
    public static final int IPV4_EIMS = 2;
    public static final int IPV6_EIMS = 3;
    public static final String [] IP_KEY = {"IPV4_IMS", "IPV6_IMS", "IPV4_EIMS", "IPV6_EIMS"};

    private static final int MSG_ON_DEDICATE_CONNECTION_STATE_CHANGED = 4000;
    private static final int MSG_PCSCF_DISCOVERY_PCO_DONE = 5000;
    private static final int MSG_ON_DEFAULT_BEARER_CONNECTION_CHANGED = 6000;
    private static final int MSG_ON_DEFAULT_BEARER_CONNECTION_FAILED = 6100;
    private static final int MSG_ON_DEFAULT_BEARER_MODIFICATION = 6200;
    private static final int MSG_ON_NOTIFY_GLOBAL_IP_ADDR = 7000;
    private static final int MSG_ON_NOTIFY_USE_DHCP_IP_ADDR = 7100;
    private static final int MSG_ON_HANDOVER_STARTED = 8000;
    private static final int MSG_ON_HANDOVER_DONE = 8100;
    private static final int MSG_ON_USSD_COMPLETE = 8200; 

    private static final int PDP_ADDR_TYPE_NONE = 0x0;
    private static final int PDP_ADDR_TYPE_IPV4 = 0x21;
    private static final int PDP_ADDR_TYPE_IPV6 = 0x57;
    private static final int PDP_ADDR_TYPE_IPV4v6 = 0x8D;
    private static final int PDP_ADDR_TYPE_NULL = 0x03;

    private static final int PDP_ADDR_MASK_NONE   = 0x00;
    private static final int PDP_ADDR_MASK_IPV4   = 0x01;
    private static final int PDP_ADDR_MASK_IPV6   = 0x02;
    private static final int PDP_ADDR_MASK_IPV4v6 = 0x03;

    private static final int SIZE_DEFAULT_BEARER_RESPONSE = 38000;
    private static final int SIZE_NOTIFY_DEDICATE_BEARER_ACTIVATED = 20480;

    private static final int FAILCAUSE_NONE = 0;
    private static final int FAILCAUSE_UNKNOWN = 65536;

    private static final int REQUEST_NETWOKR_BASE_ID = 555;
    // Deactivated reason
    private static final String REASON_APN_CHANGED = "apnChanged";
    private static final String REASON_NW_TYPE_CHANGED = "nwTypeChanged";
    private static final String REASON_QUERY_PLMN = "queryPLMN";
    private static final String REASON_DATA_DETACHED = "dataDetached";

   //For USSD and Settings reset PDN
   private static final String SETTINGS_CHANGED_OR_SS_COMPLETE  = "com.mediatek.op.telephony.SETTINGS_CHANGED_OR_SS_COMPLETE";

    // Deactivated fwk cause value
    private static final int FWK_CAUSE_NONE = 0;
    private static final int FWK_CAUSE_QUERY_PLMN = 1;

    private static DataDispatcher mInstance;
    private static HashMap<Integer, String> sImsNetworkInterface = new HashMap<Integer, String>();

    private boolean mIsEnable;
    private Context mContext;
    private VaSocketIO mSocket;
    Map<Integer, Integer> mapCid = new HashMap<Integer,Integer>();

    //private Phone mPhone;
    private HashMap<Integer, TransactionParam> mTransactions = new HashMap<Integer, TransactionParam>();
    private DataDispatcherUtil mDataDispatcherUtil;
    private WfcDispatcher mWfcDispatcher;

    private PcscfDiscoveryDhcpThread mPcscfDiscoveryDhcpThread;
    private static final int INVALID_CID = -1;
    private int mEmergencyCid = INVALID_CID;
    private int mLteEmregencyCid = INVALID_CID;
    private static int sEmergencyCid = INVALID_CID;
    private int mLtemappedDefaultCid = INVALID_CID;
    private int mEimsId = INVALID_CID;
    private ConcurrentHashMap<String, Integer> mDeactivateCid = new ConcurrentHashMap<String,
                                                                                    Integer>();
    private ConcurrentHashMap<String, GlobalIpV6AddrQueryThread> mGlobalIpV6Thread = new
            ConcurrentHashMap<String, GlobalIpV6AddrQueryThread>();

    private ConcurrentHashMap<String, InetAddress> mAddressStatus = new
            ConcurrentHashMap<String, InetAddress>();

    private ConcurrentHashMap<String, VaEvent> mGlobalIPQueue = new
                ConcurrentHashMap<String, VaEvent>();

    private int [] mFailedCause = new int [] {0, 0};

    DataDispatcherNetworkRequest [] mDataNetworkRequests;
    private static final int[] APN_CAP_LIST = new int[] {NetworkCapabilities.NET_CAPABILITY_IMS,
        NetworkCapabilities.NET_CAPABILITY_EIMS};
    private List<InetAddress> mPcscfAddr = null;
    private List<LinkAddress> mAddresses = null;
    private LinkProperties mLink = null;
    private List<InetAddress> mPcscfAddrEmgr = null;
    private List<LinkAddress> mAddressesEmrg = null;
    private LinkProperties mLinkEmrg = null;
    public static int sPhoneId = 0;
    private boolean mIsBroadcastReceiverRegistered;
    private static final int IMS_PDN = 0;
    private static final int IMS_EMERGENCY_PDN = 1;

    // DHCP
    static private final int IP_DHCP_NONE = 0;
    static private final int IP_DHCP_V4 = 1;
    static private final int IP_DHCP_V6 = 2;

    ///M: ePDG feature support
    private static final boolean WFC_FEATURE = SystemProperties.get("ro.mtk_wfc_support")
                                                            .equals("1") ? true : false;

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (TelephonyIntents.ACTION_ANY_DEDICATE_DATA_CONNECTION_STATE_CHANGED.equals(action)){
                log("onReceive, intent action is " +
                    TelephonyIntents.ACTION_ANY_DEDICATE_DATA_CONNECTION_STATE_CHANGED);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_DEDICATE_CONNECTION_STATE_CHANGED, intent));
            } else if (TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED.equals(action)) {
                String apnType = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                int imsChanged = intent.getIntExtra(PhoneConstants.DATA_IMS_CHANGED_KEY, 0);
                if (isMsgAllowed(apnType, imsChanged)) {
                    log("onReceive, apnType: " + apnType + " intent action is " +
                        TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_DEFAULT_BEARER_CONNECTION_CHANGED, intent));
                }
            } else if (TelephonyIntents.ACTION_DATA_CONNECTION_FAILED.equals(action)) {
                String apnType = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                if (isApnIMSorEmergency(apnType)) {
                    log("onReceive, apnType: " + apnType + " intent action is " +
                        TelephonyIntents.ACTION_DATA_CONNECTION_FAILED);
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_DEFAULT_BEARER_CONNECTION_FAILED, intent));
                }
            } else if (TelephonyIntents.ACTION_NOTIFY_GLOBAL_IP_ADDR.equals(action)) {
                String apnType = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                InetAddress inetAddr = (InetAddress) intent.
                                    getExtra(TelephonyIntents.EXTRA_GLOBAL_IP_ADDR_KEY);

                if (isApnIMSorEmergency(apnType) && false == isIpAddressReceived(apnType, inetAddr)) {
                log("onReceive, intent action is " +
                    TelephonyIntents.ACTION_NOTIFY_GLOBAL_IP_ADDR);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_NOTIFY_GLOBAL_IP_ADDR, intent));
                }
            } else if (TelephonyIntents.ACTION_NOTIFY_USE_DHCP_IP_ADDR.equals(action)) {
                String apnType = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                InetAddress inetAddr = (InetAddress) intent.
                                    getExtra(TelephonyIntents.EXTRA_GLOBAL_IP_ADDR_KEY);

                if (isApnIMSorEmergency(apnType) && false == isIpAddressReceived(apnType, inetAddr)) {
                    log("onReceive, intent action is " +
                        TelephonyIntents.ACTION_NOTIFY_USE_DHCP_IP_ADDR);
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_NOTIFY_USE_DHCP_IP_ADDR, intent));
                }
            } else if (TelephonyIntents.ACTION_NOTIFY_IMS_DEACTIVATED_CIDS.equals(action)) {
                log("onReceive, intent action is " + action);
                String apnType = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                if (isApnIMSorEmergency(apnType)) {
                    int [] cidArray = intent.getIntArrayExtra(TelephonyIntents.EXTRA_IMS_DEACTIVATED_CIDS);
                    setDeactivateCid(cidArray, apnType);
                }
            } else if (TelephonyIntents.ACTION_NOTIFY_IMS_DEFAULT_PDN_MODIFICATION.equals(action)) {
                log("onReceive, intent action is " + action);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_DEFAULT_BEARER_MODIFICATION,
                                intent));
            } else if (TelephonyIntents.ACTION_NOTIFY_IMS_PDN_FAILED_CAUSE.equals(action)) {
                log ("onReceive, intent action is " + action);
                String apnType = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                int failedCause = intent.getIntExtra(TelephonyIntents.EXTRA_IMS_PDN_FAILED_CAUSE, FAILCAUSE_NONE);
                setFailedCause(apnType, failedCause);
            } else if (RnsManager.CONNECTIVITY_ACTION_HANDOVER_START.equals(action)) {
                log("onReceive, intent action is " + action);
                mHandler.sendMessage(mHandler.obtainMessage(
                    MSG_ON_HANDOVER_STARTED, intent));
            } else if (RnsManager.CONNECTIVITY_ACTION_HANDOVER_END.equals(action)) {
                log("onReceive, intent action is " + action);
                mHandler.sendMessage(mHandler.obtainMessage(
                    MSG_ON_HANDOVER_DONE, intent));
            } else if (SETTINGS_CHANGED_OR_SS_COMPLETE.equals(action)) {
                log("onReceive, intent action is " + action);
                mHandler.sendMessage(mHandler.obtainMessage(
                    MSG_ON_USSD_COMPLETE, intent));
            } else {
                log("unhandled action!!");
            }
        }
    };

    private Handler mHandler;
    private Thread mHandlerThread = new Thread() {
        @Override
        public void run() {
            Looper.prepare();
            mHandler = new Handler() { //create handler here
                @Override
                synchronized public void handleMessage(Message msg) {
                    if (!mIsEnable) {
                        loge("receives message [" + msg.what + "] but DataDispatcher is not enabled, ignore");
                        return;
                    }

                    if (msg.obj instanceof VaEvent) {
                        VaEvent event = (VaEvent)msg.obj;
                        log("receives request [" + msg.what + ", " + event.getDataLen() +
                                        ", phoneId: " + event.getPhoneId() + "]");
                        switch(msg.what) {
                            case VaConstants.MSG_ID_REQUEST_DEDICATE_BEARER_ACTIVATATION:
                                handleDedicateBearerActivationRequest(event);
                                break;
                            case VaConstants.MSG_ID_REQUEST_BEARER_DEACTIVATION:
                                handleBearerDeactivationRequest(event);
                                break;
                            case VaConstants.MSG_ID_REQUEST_BEARER_MODIFICATION:
                                handleDedicateBearerModificationRequest(event);
                                break;
                            case VaConstants.MSG_ID_REQUEST_PCSCF_DISCOVERY:
                                handlePcscfDiscoveryRequest(event);
                                break;
                            case VaConstants.MSG_ID_REQUEST_BEARER_ACTIVATION:
                                handleDefaultBearerActivationRequest(event);
                                break;
                            default:
                                log("receives unhandled message [" + msg.what + "]");
                        }
                    } else {
                        log("receives request [" + msg.what + "]");
                        switch (msg.what) {
                            case MSG_ON_DEDICATE_CONNECTION_STATE_CHANGED: {
                                Intent intent = (Intent)msg.obj;
                                int ddcId = intent.getIntExtra("DdcId", -1);
                                DedicateBearerProperties property = (DedicateBearerProperties)intent.getExtra(PhoneConstants.DATA_LINK_PROPERTIES_KEY);
                                DctConstants.State state = (DctConstants.State)intent.getExtra(PhoneConstants.STATE_KEY);

                                int nfailCause = intent.getIntExtra("cause", 0);
                                String reason = intent.getStringExtra(PhoneConstants.STATE_CHANGE_REASON_KEY);
                                int nPhoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY
                                                        , SubscriptionManager.INVALID_PHONE_INDEX);

                                onDedicateDataConnectionStateChanged(ddcId, state, property,
                                                            nfailCause, nPhoneId, reason);
                                break;
                            }
                            case MSG_PCSCF_DISCOVERY_PCO_DONE: {
                                AsyncResult ar = (AsyncResult)msg.obj;
                                int transactionId = msg.arg1;
                                if (ar.exception == null) {
                                    if (ar.result == null) {
                                        loge("receives MSG_PCSCF_DISCOVERY_PCO_DONE but no PcscfInfo");
                                        rejectPcscfDiscovery(transactionId, 1);
                                    } else {
                                        responsePcscfDiscovery(transactionId, (PcscfInfo)ar.result);
                                    }
                                } else {
                                    loge("receives MSG_PCSCF_DISCOVERY_PCO_DONE but exception [" + ar.exception + "]");
                                    rejectPcscfDiscovery(transactionId, 1);
                                }
                                removeTransaction(transactionId);
                                break;
                            }
                            case MSG_ON_DEFAULT_BEARER_CONNECTION_CHANGED: {
                                Intent intent = (Intent)msg.obj;
                                String apnType = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                                onDefaultBearerDataConnStateChanged(intent, apnType);
                                break;
                            }
                            case MSG_ON_DEFAULT_BEARER_CONNECTION_FAILED: {
                                Intent intent = (Intent)msg.obj;
                                String apnType = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                                onDefaultBearerDataConnFail(intent, apnType);
                                break;
                            }
                            case MSG_ON_NOTIFY_GLOBAL_IP_ADDR: {
                                Intent intent = (Intent)msg.obj;
                                String apnType = intent.
                                    getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                                String intfName = intent.
                                    getStringExtra(PhoneConstants.DATA_IFACE_NAME_KEY);
                                InetAddress inetAddr = (InetAddress) intent.
                                    getExtra(TelephonyIntents.EXTRA_GLOBAL_IP_ADDR_KEY);
                                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY
                                                        , SubscriptionManager.INVALID_PHONE_INDEX);
                                sPhoneId = phoneId;
                                // TODO: retry mechanism [start]
                                // TODO: need to remvoe after using requestNetwork

                                NetworkInfo.State state =
                                        getImsOrEmergencyNetworkInfoState(apnType);
                                log("networkInfo state: " + state + " apnType(" + apnType + ")");
                                if (state != NetworkInfo.State.CONNECTED) {
                                    final int tryCnt = 3;
                                    int delayMsSec = 500; //retry for 0.5s, 1s, 1.5s
                                    for (int i = 0; i < tryCnt; i++) {
                                        delayForSeconds(delayMsSec * (i + 1));
                                        state = getImsOrEmergencyNetworkInfoState(apnType);
                                        log("network state: " + state +
                                            " apnType(" + apnType + ")");
                                        if (state == NetworkInfo.State.CONNECTED) {
                                            break;
                                        }
                                    }
                                }
                                // TODO: retry mechanism [end]
                                // TODO: need to remvoe after using requestNetwork
                                if (NetworkInfo.State.CONNECTED == state) {
                                    int keyIdx = getIpKeyIdx(apnType, inetAddr);
                                    if (keyIdx == -1) {
                                        loge("invalid key idx");
                                        break;
                                    }

                                    mAddressStatus.put(IP_KEY[keyIdx], inetAddr);
                                    onNotifyGlobalIpAddr(inetAddr, apnType, intfName, phoneId, IP_KEY[keyIdx]);
                                } else {
                                    log("no notify ip to va, due to state not connected!! state ("
                                        + state + ")");
                                }
                                break;
                            }
                            case MSG_ON_NOTIFY_USE_DHCP_IP_ADDR: {
                                Intent intent = (Intent)msg.obj;
                                String apnType = intent.
                                    getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                                String intfName = intent.
                                    getStringExtra(PhoneConstants.DATA_IFACE_NAME_KEY);
                                InetAddress inetAddr = (InetAddress) intent.
                                    getExtra(TelephonyIntents.EXTRA_GLOBAL_IP_ADDR_KEY);
                                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY
                                                        , SubscriptionManager.INVALID_PHONE_INDEX);
                                // TODO: retry mechanism [start]
                                // TODO: need to remvoe after using requestNetwork
                                String keyApn = "";
                                if (PhoneConstants.APN_TYPE_IMS.equals(apnType)) {
                                    keyApn = "_IMS";
                                } else {
                                    keyApn = "_EIMS";
                                }

                                NetworkInfo.State state =
                                        getImsOrEmergencyNetworkInfoState(apnType);
                                log("networkInfo state: " + state + " apnType(" + apnType + ")");
                                if (state != NetworkInfo.State.CONNECTED) {
                                    final int tryCnt = 3;
                                    int delayMsSec = 500; //retry for 0.5, 1, 1.5 s
                                    for (int i = 0; i < tryCnt; i++) {
                                        delayForSeconds(delayMsSec * (i + 1));
                                        state = getImsOrEmergencyNetworkInfoState(apnType);
                                        log("network state: " + state +
                                            " apnType(" + apnType + ")");
                                        if (state == NetworkInfo.State.CONNECTED) {
                                            break;
                                        }
                                    }
                                }

                                if (NetworkInfo.State.CONNECTED == state) {
                                    int  ipType = IP_DHCP_NONE;
                                    String ipKey = "unknown type ip";
                                    if (isIpAddressV4(inetAddr)) {
                                        ipType = IP_DHCP_V4;
                                        ipKey = "IPV4";
                                    } else if (isIpAddressV6(inetAddr)) {
                                        ipType = IP_DHCP_V6;
                                        ipKey = "IPV6";
                                    }

                                    log("Dhcp ipType: " + ipType + " address");
                                    //remove thread if running first
                                    DhcpThread dhcpThread = new DhcpThread(apnType, intfName, ipType, phoneId);
                                    dhcpThread.start();
                                    mAddressStatus.put(ipKey + keyApn, inetAddr);
                                } else {
                                    log("NetworkInfo state not connected!!");
                                }
                                break;
                            }
                            case MSG_ON_DEFAULT_BEARER_MODIFICATION: {
                                Intent intent = (Intent) msg.obj;
                                DedicateBearerProperties defaultBearerProperties =
                                (DedicateBearerProperties) intent.getExtra(
                                    TelephonyIntents.EXTRA_IMS_DEFAULT_RESPONSE_DATA_CALL);
                                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY
                                                        , SubscriptionManager.INVALID_PHONE_INDEX);

                                onNotifyDefaultBearerModification(defaultBearerProperties, phoneId);
                                break;
                            }
                            case MSG_ON_HANDOVER_STARTED: {
                                Intent intent = (Intent) msg.obj;
                                int to3gpp = intent.getIntExtra(
                                RnsManager.EXTRA_NETWORK_TYPE, ConnectivityManager.TYPE_NONE);
                                mDelayedEvent = null;
                                mDelayedEventAct = null;
                                mDelayedEventModified = null;
                                log("Handover start, reset mDelayedEvent");
                                if(mPdnActive){
                                    mWfcDispatcher.handleHandoverStarted(to3gpp);
                                    log("Handover start event received - active PDN");
                                } else {
                                    log("Handover start event received - but no active PDN connection");
                                }
                                break;
                            }
                            case MSG_ON_HANDOVER_DONE: {
                                Intent intent = (Intent) msg.obj;
                                int to3gpp = intent.getIntExtra(RnsManager.EXTRA_NETWORK_TYPE,
                                    ConnectivityManager.TYPE_NONE);
                                boolean result = intent.getBooleanExtra(
                                RnsManager.EXTRA_HANDOVER_RESULT, false);
                                if(result == true){
                                   mDelayedEvent = null;
                                   mDelayedEventAct = null;
                                   mDelayedEventModified = null;
                                   log("Handover done success, reset mDelayedEvent");
                                }  
                                if(mPdnActive){
                                    mWfcDispatcher.handleHandoverDone(to3gpp, result);
                                    log("Handover done event received - active PDN");
                                } else {
                                    log("Handover done event received - but no active PDN connection");
                                }
                                 break;

                            }
                            case MSG_ON_USSD_COMPLETE: {
                                    log("ImsAdapter.getRatType() =" +ImsAdapter.getRatType());
                                    if (ImsAdapter.getRatType() == ConnectivityManager.TYPE_EPDG){
                                        notifyWifiDataConnectionDeactivated(getWiFicid(),
                                            FAILCAUSE_UNKNOWN);
                                        releaseNwRequest(PhoneConstants.APN_TYPE_IMS);
                                    }
                                break;
                           }
                            default:
                                loge("receives unhandled message [" + msg.what + "]");
                        }
                    }
                }
            };
            Looper.loop();
        }
    };

    public DataDispatcher(Context context, VaSocketIO IO) {
        log("DataDispatcher created and use apn type [" + IMS_APN + "] as IMS APN");
        mContext = context;
        mSocket = IO;
        mDataDispatcherUtil = new DataDispatcherUtil();
        mInstance = this;
        mHandlerThread.start();
        /*Instantiate WfcDispatcher*/
        mWfcDispatcher = new WfcDispatcher(context, IO);
        createNetworkRequest();
    }

    public static DataDispatcher getInstance() {
        return mInstance;
    }

    public void enableRequest(){
        synchronized(mHandler) {
            log("receive enableRequest");
            mIsEnable = true;

            if (!mIsBroadcastReceiverRegistered) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(TelephonyIntents.ACTION_ANY_DEDICATE_DATA_CONNECTION_STATE_CHANGED);
                filter.addAction(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
                filter.addAction(TelephonyIntents.ACTION_DATA_CONNECTION_FAILED);
                filter.addAction(TelephonyIntents.ACTION_NOTIFY_GLOBAL_IP_ADDR);
                filter.addAction(TelephonyIntents.ACTION_NOTIFY_USE_DHCP_IP_ADDR);
                filter.addAction(TelephonyIntents.ACTION_NOTIFY_IMS_DEACTIVATED_CIDS);
                filter.addAction(TelephonyIntents.ACTION_NOTIFY_IMS_DEFAULT_PDN_MODIFICATION);
                filter.addAction(TelephonyIntents.ACTION_NOTIFY_IMS_PDN_FAILED_CAUSE);
                filter.addAction(RnsManager.CONNECTIVITY_ACTION_HANDOVER_START);
                filter.addAction(RnsManager.CONNECTIVITY_ACTION_HANDOVER_END);
                filter.addAction(SETTINGS_CHANGED_OR_SS_COMPLETE);
                mContext.registerReceiver(mBroadcastReceiver, filter);
                mIsBroadcastReceiverRegistered = true;
            }
        }
    }

    public void disableRequest(){
        synchronized(mHandler) {
            log("receive disableRequest");
            mIsEnable = false;

            if (mIsBroadcastReceiverRegistered) {
                mContext.unregisterReceiver(mBroadcastReceiver);
                mIsBroadcastReceiverRegistered = false;
            }

            synchronized (sImsNetworkInterface) {
                log("disableRequest to clear interface and cid map");
                sImsNetworkInterface.clear();
            }

            synchronized (mTransactions) {
                log("disableRequest to clear transactions");
                mTransactions.clear();
            }

            stopPcscfDiscoveryDhcpThread("disableRequest to interrupt dhcp thread");

            // TODO: need to use new interface insteading of legcy one for L Migration [start]
            releaseNwRequest(PhoneConstants.APN_TYPE_IMS);
            releaseNwRequest(PhoneConstants.APN_TYPE_EMERGENCY);
            // TODO: need to use new interface insteading of legcy one for L Migration [end]

            clearDeactivateCid();
            setEmergencyCid(INVALID_CID);

            for (String apnType: mGlobalIpV6Thread.keySet()) {
                stopQueryGlobalIpV6Thread(apnType);
            }
            mAddressStatus.clear();
            mGlobalIPQueue.clear();
            resetFailedCause();
            DataDispatcherUtil.clearVoLTEConnectedDedicatedBearer();
        }
    }

    public void vaEventCallback(VaEvent event) {
        //relay to main thread to keep rceiver and callback handler is working under the same thread
        mHandler.sendMessage(mHandler.obtainMessage(event.getRequestID(), event));
    }

    public void setSocket(VaSocketIO socket) {
        //this method is used for testing
        //we could set a dummy socket used to verify the response
        mSocket = socket;
    }

    private void sendVaEvent(VaEvent event) {
        log("DataDispatcher send event [" + event.getRequestID()+ ", " + event.getDataLen() + "]");
        mSocket.writeEvent(event);
    }

    private void setEmergencyCid(int cid) {
        mEmergencyCid = cid;
        sEmergencyCid = mEmergencyCid;
        log("set mEmergencyCid to: " + mEmergencyCid);
        if(cid == INVALID_CID) {
            setLteAllocatedEmergencyCid(cid);
            mEimsId = cid;
        }
    }

    private int getLteAllocatedEmergencyCid() {
        log("getLteAllocatedEmergencyCid mLteEmregencyCid "+mLteEmregencyCid );
        return mLteEmregencyCid;
    }
    private void setLteAllocatedEmergencyCid(int cid) {
        mLteEmregencyCid = cid;
    }
    private void handleDefaultBearerActivationRequest(VaEvent event) {
        // imcf_uint8                               transaction_id
        // imcf_uint8                               pad[3]
        // imc_eps_qos_struct                  ue_defined_eps_qos
        // imc_emergency_ind_enum       emergency_indidation
        // imc_pcscf_discovery_enum       pcscf_discovery_flag
        // imcf_uint8                               signaling_flag
        // imcf_uint8                               pad2[1]
        int result = -1;
        int isValid = 1;
        int isEmergencyInd = 0; //0 is general, 1 is emergency
        int nNetworkType = ConnectivityManager.TYPE_MOBILE_IMS;
        String apnType = PhoneConstants.APN_TYPE_IMS;
        int RAT_TYPE_WIFI = 6;
        int phoneId = event.getPhoneId();
        String key = "";
        DataDispatcherUtil.DefaultPdnActInd defaultPdnActInd = mDataDispatcherUtil.extractDefaultPdnActInd(event);
        TransactionParam param = new TransactionParam(defaultPdnActInd.transactionId,
                            event.getRequestID(), phoneId);
        putTransaction(param);

        log("handleDefaultBearerActivationRequest");

        // TODO: Need to convert emergency_ind here
        switch (defaultPdnActInd.emergency_ind) {
            case 1: //general
                setFailedCause(apnType, FAILCAUSE_NONE);
                key = "_IMS";
                break;
            case 2: // is emergency
                key = "_EIMS";
                isEmergencyInd = 1;
                nNetworkType = ConnectivityManager.TYPE_MOBILE_EMERGENCY;
                apnType = PhoneConstants.APN_TYPE_EMERGENCY;
                setFailedCause(apnType, FAILCAUSE_NONE);
                param.isEmergency = true;
                int endPos = mDataNetworkRequests.length;
                int pos = getNetworkRequetsPos(apnType,endPos);
                if(pos > -1 && pos < endPos){
                    NetworkCapabilities netCap = mDataNetworkRequests[pos].nwCap;
                    if(defaultPdnActInd.rat_type == RAT_TYPE_WIFI){
                        netCap.removeTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
                        netCap.addTransportType(NetworkCapabilities.TRANSPORT_EPDG);
                    }
                }
                break;
            default: //invalid or error
                rejectDefaultBearerDataConnActivation(param, FAILCAUSE_UNKNOWN, 1000);
                return;
        };

        // check ims apn exists or not
        if (nNetworkType == ConnectivityManager.TYPE_MOBILE_IMS &&
                isImsApnExists(param.phoneId) == false) {
            log("no IMS apn Exists!!");
            rejectDefaultBearerDataConnActivation(param, FAILCAUSE_UNKNOWN, 500);
            return;
        }

        DefaultBearerConfig defaultBearerConfig = new DefaultBearerConfig(isValid, defaultPdnActInd.qosStatus, isEmergencyInd,
                                                 defaultPdnActInd.pcscf_discovery,defaultPdnActInd.signalingFlag);
        setDefaultBearerConfig(networkTypeToApnType(nNetworkType), defaultBearerConfig,
                        param.phoneId);

        if (requestNwRequest(apnType, phoneId) < 0) {
            rejectDefaultBearerDataConnActivation(param, FAILCAUSE_UNKNOWN, 2000);
        }
    }

    private void rejectDefaultBearerDataConnActivation(TransactionParam param, int failCause, int delayMs) {
        if (hasTransaction(param.transactionId)) {
            //imcf_uint8 transaction_id
            //imc_ps_cause_enum ps_cause
            //imcf_uint8 pad [2]
            releaseNwRequest(param.isEmergency ? PhoneConstants.APN_TYPE_EMERGENCY:
                                    PhoneConstants.APN_TYPE_IMS);


            //prevent receiving disconnect event after receiving IMCB retrying request
            delayForSeconds(delayMs);

            removeTransaction(param.transactionId);

            sendVaEvent(makeRejectDefaultBearerEvent(param, failCause));
        } else {
            loge("rejectDefaultBearerDataConnActivation but transactionId does not existed, ignore");
        }
    }

    private void handleDefaultBearerDeactivationRequest(int requestId,
        DataDispatcherUtil.PdnDeactInd pdnDeactInd, int phoneId) {
        int result = -1;
        String networkFeature = FEATURE_ENABLE_IMS;
        int networkType = ConnectivityManager.TYPE_MOBILE_IMS;
        boolean bIsEmergency = false;
        String apnType = PhoneConstants.APN_TYPE_IMS;
        int ratType = -1;

        if (pdnDeactInd.isCidValid) {
            log("handleDefaultBearerDeactivationRequest [" + pdnDeactInd.transactionId +
                "] deactivate cid=" + pdnDeactInd.cid + ", networkFeature: " + networkFeature);
            bIsEmergency = (mEmergencyCid == pdnDeactInd.cid && mEmergencyCid != -1)
                            ? true : false;
        } else {
            log("handleDedicateBearerDeactivationRequest [" + pdnDeactInd.transactionId +
                "] abort transactionId=" + pdnDeactInd.transactionId +
                (pdnDeactInd.isCidValid ? (", cid=" + pdnDeactInd.cid)
                : (", abortTransactionId=" + pdnDeactInd.abortTransactionId)));

            TransactionParam abortParam = getTransaction(pdnDeactInd.abortTransactionId);
            if (abortParam == null)
                loge("handleDefaultBearerDeactivationRequest to do abort but no "
                    + "transaction is found");
            else
                bIsEmergency = abortParam.isEmergency;
        }

        if (bIsEmergency) {
            log("handleDefaultBearerDeactivationRequest the bearer is emergency bearer");
            networkType = ConnectivityManager.TYPE_MOBILE_EMERGENCY;
            networkFeature = FEATURE_ENABLE_EMERGENCY;
            apnType = PhoneConstants.APN_TYPE_EMERGENCY;
        }

        NetworkInfo networkInfo = getConnectivityManager().getNetworkInfo(networkType);
        NetworkInfo.State currState = networkInfo.getState();
        log("networkinfo: " + networkInfo);
        ratType = ImsAdapter.getRatType();
        result = releaseNwRequest(apnType);
        removeReceivedAddress(apnType);

        TransactionParam param = new TransactionParam(pdnDeactInd.transactionId, requestId
                                                    , phoneId);
        param.isEmergency = bIsEmergency;
        if (pdnDeactInd.isCidValid) {
            param.cid = pdnDeactInd.cid;
        } else {
            param.cid = 0; //set cid to 0 for abort transaction
        }
        putTransaction(param);

        if (result >= 0) {
            try {
                DedicateBearerProperties defaultBearerProp = getDefaultBearerProperties(apnType, phoneId);
                log("defaultBearerProp: " + defaultBearerProp);
                if ((currState == NetworkInfo.State.DISCONNECTED ||
                    currState == NetworkInfo.State.CONNECTING) ||
                    (currState == NetworkInfo.State.CONNECTED && null == defaultBearerProp)) {
                    if (!pdnDeactInd.isCidValid) {
                        synchronized (mTransactions) {
                            Integer[] transactionKeyArray = getTransactionKeyArray();
                            int delayMs = 1000;
                            for (Integer transactionId : transactionKeyArray) {
                                TransactionParam actParam = getTransaction(transactionId);
                                if (VaConstants.MSG_ID_REQUEST_BEARER_ACTIVATION
                                    == actParam.requestId) {
                                    loge("handleDefaultBearerDeactivationRequest abort activation "
                                        + "request");
                                    if (currState == NetworkInfo.State.CONNECTING) {
                                        delayMs = 2500;
                                    }
                                    rejectDefaultBearerDataConnActivation(actParam,
                                                                FAILCAUSE_UNKNOWN, delayMs);
                                }
                            }
                        }
                    }
                    loge("handleDefaultBearerDeactivationRequest and bearer is already " +
                        "deactivated");
                    responseDefaultBearerDataConnDeactivated(param);
                }else if(ratType == ConnectivityManager.TYPE_EPDG){
                    log("handleDefaultBearerDeactivationRequest active RAT is over ePDG");
                    responseDefaultBearerDataConnDeactivated(param);
                }
            } catch (NullPointerException ex) {
                loge("networkInfo is null");
                ex.printStackTrace();
            }

            if (bIsEmergency)
                setEmergencyCid(INVALID_CID);
        } else {
            //response rejection
            rejectDefaultBearerDataConnDeactivation(param, 1);
        }

    }

    private void rejectDefaultBearerDataConnDeactivation(TransactionParam param, int failCause) {
        if (hasTransaction(param.transactionId)) {
            //imcf_uint8 transaction_id
            //imc_ps_cause_enum ps_cause
            //imcf_uint8 pad [2]

            removeTransaction(param.transactionId);

            sendVaEvent(makeRejectDefaultBearerEvent(param, failCause));
        } else {
            loge("rejectDefaultBearerDataConnDeactivation but transactionId does not existed, ignore");
        }
    }

    private ImsAdapter.VaEvent makeRejectDefaultBearerEvent(TransactionParam param, int failCause) {
        //imcf_uint8 transaction_id
        //imc_ps_cause_enum ps_cause
        //imcf_uint8 pad [2]

        ImsAdapter.VaEvent event = new ImsAdapter.VaEvent(param.phoneId,
                                              VaConstants.MSG_ID_REJECT_BEARER_ACTIVATION);
        log("rejectDefaultBearerDataConnActivation param" + param + ", failCause=" + failCause);

        event.putByte(param.transactionId); //transaction id;
        event.putByte(failCause);           //cause
        event.putBytes(new byte[2]);        //padding

        return event;
    }

    private void handleDedicateBearerActivationRequest(VaEvent event) {
        //imcf_uint8                                      transaction_id
        //imcf_uint8                                      primary_context_id
        //imc_im_cn_signaling_flag_enum   signaling_flag (value: 0/1)
        //imcf_uint8                                      pad [1]
        //imc_eps_qos_struct                      ue_defined_eps_qos
        //imc_tft_info_struct                         ue_defined_tft

        int transactionId = event.getByte();
        int primaryCid = event.getByte();
        boolean signalingFlag = event.getByte() > 0;
        event.getByte(); //padding
        int phoneId = event.getPhoneId();
        QosStatus qosStatus = DataDispatcherUtil.readQos(event);
        TftStatus tftStatus = DataDispatcherUtil.readTft(event);
        log("handleDedicateBearerActivationRequest [" + transactionId + "] primaryCid=" + primaryCid + ", signalingFlag=" + signalingFlag + ", Qos" + qosStatus + ", Tft" + tftStatus);

        int ddcId = enableDedicateBearer(IMS_APN, signalingFlag, qosStatus, tftStatus, phoneId);
        TransactionParam param = new TransactionParam(transactionId,
                            event.getRequestID(), phoneId);
        param.ddcId = ddcId; //store ddcId for abort if necessary
        putTransaction(param);

        if (ddcId < 0) {
            loge("handleDedicateBearerActivationRequest [" + transactionId + "] but no ddcId is assigned");
            rejectDedicateDataConnectionActivation(param, FAILCAUSE_UNKNOWN, null);
        }
    }

    private void handleBearerDeactivationRequest(VaEvent event) {
        //imcf_uint8    transaction_id
        //imcf_uint8    abort_activate_transaction_id
        //imcf_uint8    context_id_is_valid
        //imcf_uint8    context_id
        DataDispatcherUtil.PdnDeactInd pdnDeactInd = mDataDispatcherUtil.extractPdnDeactInd(event);
        int requestId = event.getRequestID();
        int phoneId = event.getPhoneId();
        if (isDedicateBearer(pdnDeactInd.cid, phoneId)) {
            handleDedicateBearerDeactivationRequest(requestId, pdnDeactInd, phoneId);
        } else if (!pdnDeactInd.isCidValid) {
            TransactionParam param = getTransaction(pdnDeactInd.abortTransactionId);
            if (param != null) {
                if (param.requestId == VaConstants.MSG_ID_REQUEST_BEARER_ACTIVATION) {
                    log("handleBearerDeactivationRequest to default bearer activation");
                    handleDefaultBearerDeactivationRequest(requestId, pdnDeactInd, phoneId);
                } else {
                    log("handleBearerDeactivationRequest to abort dedicate bearer activation");
                    handleDedicateBearerDeactivationRequest(requestId, pdnDeactInd, phoneId);
                }
            } else {
                loge("handleBearerDeactivationRequest to abort bearer activation but no transaction found (reject request anyway)");
                rejectDataBearerDeactivation(pdnDeactInd.transactionId, 1, phoneId);
            }
        } else {
            handleDefaultBearerDeactivationRequest(requestId, pdnDeactInd, phoneId);
        }
    }

    private void handleDedicateBearerDeactivationRequest(int requestId,
        DataDispatcherUtil.PdnDeactInd pdnDeactInd, int phoneId) {
        int ddcId = -1;
        log("handleDedicateBearerDeactivationRequest PdnDeactInd" + pdnDeactInd);
        TransactionParam param = new TransactionParam(pdnDeactInd.transactionId, requestId
                                            , phoneId);
        if (pdnDeactInd.isCidValid) {
            ddcId = disableDedicateBearer(REASON_BEARER_DEACTIVATION, pdnDeactInd.cid, phoneId);
        } else {
            //try to get ddcId from transactions
            TransactionParam transaction = getTransaction(pdnDeactInd.abortTransactionId);
            if (transaction == null) {
                loge("handleDedicateBearerDeactivationRequest do abort but no transaction found " +
                    "with transactionId=" + pdnDeactInd.abortTransactionId);
                //since ddcId is -1, this request will be rejected
            } else {
                log("handleDedicateBearerDeactivationRequest do abort with ddcId="
                    + transaction.ddcId);
                ddcId = abortEnableDedicateBearer(REASON_BEARER_ABORT, transaction.ddcId
                                        , transaction.phoneId);
            }
        }

        putTransaction(param);

        if (ddcId >= 0) {
            if (pdnDeactInd.isCidValid)
                param.cid = pdnDeactInd.cid;
            else
                param.cid = -1;

            param.ddcId = ddcId;
        } else {
            log("handleDedicateBearerDeactivationRequest but no corresponding ddcId is found "
                + pdnDeactInd);
            rejectDataConnectionDeactivation(pdnDeactInd.transactionId, 1, phoneId);
        }
    }

    private void handleDedicateBearerModificationRequest(VaEvent event) {
        //imcf_uint8 transation_id
        //imcf_uint8 context_id
        //imcf_uint8 qos_mod
        //imcf_uint8 pad [1]
        //imc_eps_qos_struct ue_defined_eps_qos
        //imcf_uint8 tft_mod
        //imcf_uint8 pad2 [3]
        //imc_tft_info_struc ue_defined_tft
        int transactionId = event.getByte();
        int cid = event.getByte();
        boolean isQosModify = event.getByte() == 1;
        event.getByte(); //padding
        QosStatus qosStatus = DataDispatcherUtil.readQos(event);
        boolean isTftModify = event.getByte() == 1;
        event.getBytes(3); //padding
        TftStatus tftStatus = DataDispatcherUtil.readTft(event);
        int phoneId = event.getPhoneId();

        log("handleDedicateBearerModificationRequest [" + transactionId + ", " + cid + "] " +
            (isQosModify ? "Qos" + qosStatus : "") + (isTftModify ? " Tft" + tftStatus : ""));

        int ddcId = modifyDedicateBearer(cid, isQosModify ? qosStatus : null,
                                    isTftModify ? tftStatus : null, phoneId);
        TransactionParam param = new TransactionParam(transactionId, event.getRequestID()
                                            , phoneId);
        param.cid = cid;
        param.ddcId = ddcId;

        putTransaction(param);

        if (ddcId < 0) {
            rejectDedicateDataConnectionModification(param, FAILCAUSE_UNKNOWN, null);
        }
    }

    private void handlePcscfDiscoveryRequest(VaEvent event) {
        //imcf_uint8 transaction_id
        //imcf_uint8 context_id
        //imcf_uint8 pad [2]
        //char nw_if_name [IMC_MAXIMUM_NW_IF_NAME_STRING_SIZE]
        //imc_pcscf_acquire_method_enum pcscf_aqcuire_method
        int transactionId = event.getByte();
        int cid = event.getByte();
        event.getBytes(2); //padding

        byte[] interfaceBytes = event.getBytes(DataDispatcherUtil.IMC_MAXIMUM_NW_IF_NAME_STRING_SIZE);
        String interfaceName = null;
        try {
            interfaceName = new String(interfaceBytes, "US-ASCII");
            interfaceName = interfaceName.trim();
        } catch (java.io.UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        int method = event.getByte();

        log("handlePcscfDiscoveryRequest [" + transactionId + ", " + cid + ", "
            + interfaceName + ", " + method + ", phoneId: " + event.getPhoneId() + " ]");
        TransactionParam param = new TransactionParam(transactionId, event.getRequestID()
                                                    , event.getPhoneId());
        param.cid = cid;
        putTransaction(param);

        switch (method) {
            case PcscfInfo.IMC_PCSCF_ACQUIRE_BY_NONE:
                //invalid acquire method
                rejectPcscfDiscovery(transactionId, 1);
                break;
            case PcscfInfo.IMC_PCSCF_ACQUIRE_BY_SIM:
                handlePcscfDiscoveryRequestByISim(transactionId, event);
                break;
            case PcscfInfo.IMC_PCSCF_ACQUIRE_BY_MO:
                handlePcscfDiscoveryRequestByMo(transactionId, event);
                break;
            case PcscfInfo.IMC_PCSCF_ACQUIRE_BY_PCO:
                handlePcscfDiscoveryRequestByPco(transactionId, cid, event);
                break;
            case PcscfInfo.IMC_PCSCF_ACQUIRE_BY_DHCPv4:
            case PcscfInfo.IMC_PCSCF_ACQUIRE_BY_DHCPv6:
                handlePcscfDiscoveryRequestByDhcp(transactionId, interfaceName, method, event);
                break;
            case PcscfInfo.IMC_PCSCF_ACQUIRE_BY_MANUAL:
                handlePcscfDiscoveryRequestByManual(transactionId, event);
                break;
            default:
                loge("handlePcscfDiscoveryRequest receive unknown method [" + method + "]");
        }
    }

    private void handlePcscfDiscoveryRequestByPco(int transactionId, int cid, VaEvent event) {
        log("handlePcscfDiscoveryRequestByPco [" + transactionId + ", " + cid + "]");
        int result = pcscfDiscovery(IMS_APN, cid, event.getPhoneId(),
                    mHandler.obtainMessage(MSG_PCSCF_DISCOVERY_PCO_DONE, transactionId, cid));
        if (result < 0) {
            loge("handlePcscfDiscoveryRequestByPco failed [" + result + "]");
            rejectPcscfDiscovery(transactionId, 1);
        }
    }

    private void handlePcscfDiscoveryRequestByDhcp(int transactionId, String interfaceName
        , int method, VaEvent event) {
        log("handlePcscfDiscoveryRequestByDhcp [" + transactionId + ", " + interfaceName + "]");
        mPcscfDiscoveryDhcpThread = new PcscfDiscoveryDhcpThread(transactionId, interfaceName,
                event, method == PcscfInfo.IMC_PCSCF_ACQUIRE_BY_DHCPv4 ?
                PcscfDiscoveryDhcpThread.ACTION_GET_V4 : PcscfDiscoveryDhcpThread.ACTION_GET_V6);
        mPcscfDiscoveryDhcpThread.start();
    }

    private void handlePcscfDiscoveryRequestByISim(int transactionId, VaEvent event) {
        String [] pcscf = null;
        int subId = SubscriptionManager.getSubIdUsingPhoneId(event.getPhoneId());

        try {
            pcscf = getSubscriberInfo().getIsimPcscfForSubscriber(subId);
        } catch (RemoteException ex) {
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            // do notihing here
        }

        log("handlePcscfDiscoveryRequestByISim, subId: " + subId);
        if (pcscf == null || pcscf.length == 0) {
            loge("handlePcscfDiscoveryRequestByISim but no P-CSCF found");
            rejectPcscfDiscovery(transactionId, 1);
        } else {
             PcscfInfo pcscfInfo = new PcscfInfo(PcscfInfo.IMC_PCSCF_ACQUIRE_BY_SIM, pcscf); //port is not specified
             log("handlePcscfDiscoveryRequestByISim, pcscfInfo from getIsimPcscf: " + pcscfInfo);
             responsePcscfDiscovery(transactionId, pcscfInfo);
        }
    }

    private void handlePcscfDiscoveryRequestByMo(int transactionId, VaEvent event) {
        // TODO: currently VOLTE only support single SIM thus pass  phone Id 0
        // TODO: might need to change the code if support multi-sim
        int subId = SubscriptionManager.getSubIdUsingPhoneId(event.getPhoneId());
        ImsConfig imsConfig = getImsConfig(subId);
        log("handlePcscfDiscoveryRequestByMo, phoneId: " + event.getPhoneId() + ", subId: " +
            subId);
        if (imsConfig == null) {
            loge("handlePcscfDiscoveryRequestByMo but cannot get ImsConfig for MO");
            rejectPcscfDiscovery(transactionId, 1);
        } else {
            PcscfInfo pcscfInfo = new PcscfInfo();
            pcscfInfo.source = PcscfInfo.IMC_PCSCF_ACQUIRE_BY_MO;

            try {
                String moPcscf = imsConfig.getProvisionedStringValue(ImsConfig.
                                                        ConfigConstants.IMS_MO_PCSCF);
                if (moPcscf == null || moPcscf.length() == 0) {
                    log("handlePcscfDiscoveryRequestByMo and no MO P-CSCF is found (continue check LBO P-CSCF");
                } else {
                    log("handlePcscfDiscoveryRequestByMo and MO P-CSCF is found [" + moPcscf + "]");
                    pcscfInfo.add(moPcscf, 0); //port is not specified
                }

                ImsLboPcscf[] imsLboPcscfArray = imsConfig.getMasterLboPcscfValue();
                if (imsLboPcscfArray == null) {
                    log("handlePcscfDiscoveryRequestByMo and no LBO P-CSCF is found");
                } else {
                    for(ImsLboPcscf imsLboPcscf : imsLboPcscfArray) {
                        String lboPcscf = imsLboPcscf.getLboPcscfAddress();
                        if (lboPcscf != null && lboPcscf.length() > 0) {
                            log("handlePcscfDiscoveryRequestByMo and LBO P-CSCF is found [" + lboPcscf +  "]");
                            pcscfInfo.add(lboPcscf, 0); //port is not specified
                        }
                    }
                }
            } catch (ImsException e) {
                e.printStackTrace();
            }

            if (pcscfInfo.getPcscfAddressCount() == 0) {
                loge("handlePcscfDiscoveryRequestByMo but no any P-CSCF is found");
                rejectPcscfDiscovery(transactionId, 1);
            } else {
                responsePcscfDiscovery(transactionId, pcscfInfo);
            }
        }
    }

    private void handlePcscfDiscoveryRequestByManual(int transactionId, VaEvent event) {
        String pcscf = SystemProperties.get(PROPERTY_MANUAL_PCSCF_ADDRESS);
        if (pcscf == null || pcscf.length() == 0) {
            loge("handlePcscfDiscoveryRequest (manual) invalid P-CSCF system property");
            rejectPcscfDiscovery(transactionId, 1);
        } else {
            int port = SystemProperties.getInt(PROPERTY_MANUAL_PCSCF_PORT, 0);
            log("handlePcscfDiscoveryRequest (manual) P-CSCF system property [address=" + pcscf + ", port=" + port + "]");

            PcscfInfo pcscfInfo = new PcscfInfo();
            pcscfInfo.source = PcscfInfo.IMC_PCSCF_ACQUIRE_BY_MANUAL;
            pcscfInfo.add(pcscf, port);

            responsePcscfDiscovery(transactionId, pcscfInfo);
        }
    }

    // TODO: Default bearer [start]
    private void onDefaultBearerDataConnFail(Intent intent, String apnType) {
        log("onDefaultBearerDataConnFail apnType=" + apnType);

        boolean hasTransaction = false;
        String reason = intent.getStringExtra(PhoneConstants.STATE_CHANGE_REASON_KEY);
        int nPhoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY
                                    , SubscriptionManager.INVALID_PHONE_INDEX);

        synchronized (mTransactions) {
            Integer[] transactionKeyArray = getTransactionKeyArray();
            for (Integer transactionId : transactionKeyArray) {
                TransactionParam param = getTransaction(transactionId);
                if (VaConstants.MSG_ID_REQUEST_BEARER_ACTIVATION == param.requestId) {
                    hasTransaction = true;
                    int nfailCause = getLastDataConnectionFailCause(apnType, nPhoneId);
                    rejectDefaultBearerDataConnActivation(param, nfailCause, 800);
                }
            }
        }

        if (!hasTransaction) {
            //no matched transaction
            loge("onDefaultBearerDataConnFail but no transaction found");
        }
    }

    private void onDefaultBearerDataConnStateChanged(Intent intent, String apnType) {
        PhoneConstants.DataState state = Enum.valueOf(PhoneConstants.DataState.class, intent.getStringExtra(PhoneConstants.STATE_KEY));
        String reason = intent.getStringExtra(PhoneConstants.STATE_CHANGE_REASON_KEY);
        int nPhoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY
                                    , SubscriptionManager.INVALID_PHONE_INDEX);
        boolean hasTransaction = false;

        log("onDefaultBearerDataConnStateChanged, state: " + state + ", reason: " + reason + ", apnType: " + apnType);

        synchronized (mTransactions) {
            Integer[] transactionKeyArray = getTransactionKeyArray();
            for (Integer transactionId : transactionKeyArray) {
                TransactionParam param = getTransaction(transactionId);
                if ((IMS_APN.equals(apnType) && param.isEmergency != true) ||
                    (EMERGENCY_APN.equals(apnType) && param.isEmergency == true)) {
                    switch (param.requestId) {
                    case VaConstants.MSG_ID_REQUEST_BEARER_ACTIVATION:
                        // Do something here
                        hasTransaction = true;
                        if (state == PhoneConstants.DataState.CONNECTED) {
                            LinkProperties lp = intent.getParcelableExtra(PhoneConstants.DATA_LINK_PROPERTIES_KEY);
                            if (responseDefaultBearerDataConnActivated(param, apnType, lp)) {
                                // need to get IMS address here
                                // getIMSGlobalIpAddr(apnType, lp, param.phoneId);
                                getIMSGlobalIpAddr(apnType);
                            }
                        } else if (state == PhoneConstants.DataState.DISCONNECTED) {
                            int nFailedCause = getLastDataConnectionFailCause(apnType, nPhoneId);
                            rejectDefaultBearerDataConnActivation(param, nFailedCause, 1000);
                            removeReceivedAddress(apnType);
                        }
                        break;
                    case VaConstants.MSG_ID_REQUEST_BEARER_DEACTIVATION:
                        if (state == PhoneConstants.DataState.DISCONNECTED) {
                            hasTransaction = true;
                            int [] cidArray = getDeactivateCidArray(apnType, param.phoneId);
                            /// M: [ALPS02190947]Avoid deactivation event is not response to IMCB @
                            if (cidArray != null) {
                               log("onDefaultBearerDataConnStateChanged, cidArray[0]: "
                                + cidArray[0] + ", param.cid: " + param.cid);
                               if (cidArray[0] == param.cid || param.cid == 0) {
                                  responseDefaultBearerDataConnDeactivated(param);
                                  stopPcscfDiscoveryDhcpThread("IMS PDN is"
                                      + "deactivated and to interrupt P-CSCF discovery thread");
                               }
                            } else {
                                log("onDefaultBearerDataConnStateChanged cidArray is null");
                                if (param.isEmergency && param.cid == 0 &&
                                    mEmergencyCid != INVALID_CID) {
                                    param.cid = mEmergencyCid;
                                    log("transaction is for abort emergency pdn, param: " + param);
                                }
                                responseDefaultBearerDataConnDeactivated(param);
                            }
                            /// @}

                            if (param.isEmergency) {
                                setEmergencyCid(INVALID_CID);
                            }
                            removeReceivedAddress(apnType);
                        }
                        break;
                    default:
                        log("onDefaultBearerDataConnStateChanged received unhandled state change event [" + transactionId + " " + param.requestId + "]");
                    }
                }
            }
        }

        log("onDefaultBearerDataConnStateChanged hasTrasaction: " + hasTransaction);
        if (!hasTransaction) {
            //no matched transactions, need to notify Va the state is changed
            switch (state) {
                case DISCONNECTED:
                    {
                        boolean bReleaseNwRequest = true;
                        int [] cidArray = getDeactivateCidArray(apnType, nPhoneId);
                        int nfailCause = getLastDataConnectionFailCause(apnType, nPhoneId);
                        boolean bStopPcscfThread = true;
                        int nfwkCause = getFwkCauseFromReason(reason);
                        int nDeactivateCidSize = mDeactivateCid.size();
                        if(null != cidArray) {
                            log("deactivate cid size: " + cidArray.length);
                            for(int i = 0; i < cidArray.length; i++) {
                                notifyDefaultBearerDataConnDeactivated(cidArray[i], nfailCause,
                                    nfwkCause, nPhoneId);
                            }
                        } else if (isFaileCauseAllowedToDeatch(reason) ||
                                    isReasonAllowedToDetach(reason) || nDeactivateCidSize > 0) {
                            log("deactivate cid(s): " + mDeactivateCid + ", size: "
                                + nDeactivateCidSize);
                            Integer cid = mDeactivateCid.get(apnType);
                            if (cid != null) {
                                notifyDefaultBearerDataConnDeactivated(cid, nfailCause,
                                    nfwkCause, nPhoneId);
                            } else {
                                loge("no deactivate cid for apnType: " + apnType);
                                bReleaseNwRequest = false;
                            }
                        } else {
                            loge("can't get any cids, no response deactivated default bearer!!");
                            bStopPcscfThread = false;
                            bReleaseNwRequest = false;
                        }

                        if (bStopPcscfThread) {
                            stopPcscfDiscoveryDhcpThread("IMS PDN is deactivated and" +
                                            " to interrupt P-CSCF discovery thread");
                        }

                        stopQueryGlobalIpV6Thread(apnType);
                        removeDeactivateCid(apnType);
                        if (EMERGENCY_APN.equals(apnType)) {
                            setEmergencyCid(INVALID_CID);
                        }

                        if (WfcDispatcher.isHandoverInProgress() ||
                            (ImsAdapter.getRatType() == ConnectivityManager.TYPE_EPDG)) {
                                bReleaseNwRequest = false;
                        }
                        if (bReleaseNwRequest) {
                            releaseNwRequest(apnType);
                        }
                        removeReceivedAddress(apnType);
                    }
                    break;
                case CONNECTED:
                // In case of handover ongoing from Wifi to LTE
                log("WfcDispatcher.isHandoverInProgress() "
                        + WfcDispatcher.isHandoverInProgress()
                        + "WfcDispatcher.handoverDirection() "
                        + WfcDispatcher.handoverDirection());

                log("Handover in progress - set deafult bearer");
                    synchronized (sImsNetworkInterface) {
                        try{
                        LinkProperties lp = intent
                                .getParcelableExtra(PhoneConstants.DATA_LINK_PROPERTIES_KEY);
                        sImsNetworkInterface.put(
                                (getDefaultBearerProperties(
                                        PhoneConstants.APN_TYPE_IMS, 0)).cid,
                                lp.getInterfaceName());
                        } catch(NullPointerException ex){
                            log("onDefaultBearerDataConnStateChanged Exception ex = "+ex);
                        }
                        log("Update IMS network interface name: "
                                + sImsNetworkInterface);

                    }
                    log("Connected but currently no notify");
                    break;
                default:
                    ;
            }
        }
    }

    private boolean responseDefaultBearerDataConnActivated(TransactionParam param,
        String apnType, LinkProperties lp) {
        boolean bResponse = true;
        if (hasTransaction(param.transactionId)) {
            ImsAdapter.VaEvent event = new ImsAdapter.VaEvent(
                    param.phoneId,
                    VaConstants.MSG_ID_RESPONSE_BEARER_ACTIVATION,
                    SIZE_DEFAULT_BEARER_RESPONSE);
            int pdnCnt = 0;
            DedicateBearerProperties defaultBearerProp, defaultBearerPropEmpty, defaultBearerPropTemp;
            int ipMask = PDP_ADDR_MASK_NONE;

            log("responseDefaultBearerDataConnActivated " /*+ param + ", " + property*/);
            //imcf_uint8 transaction_id;
            //imcf_uint8 count;
            //imcf_uint8 pad[2];
            //imc_pdn_context_struct  contexts [2]

            // (member of imc_pdn_context_struct)
            //imc_pdp_addr_type_enum pdp_addr_type
            //imcf_uint8 pad2[3]
            //imc_single_concatenated_msg_struct  main_context;
            //imcf_uint8 num_of_concatenated_contexts;
            //imcf_uint8 pad2[3];
            //imc_single_concatenated_msg_struct concatenated_context[IMC_MAX_CONCATENATED_NUM];

            event.putByte(param.transactionId);
            //Check address type here
            int pdp_addr_type = PDP_ADDR_TYPE_NONE;
            if(null != lp) {
                for(LinkAddress linkAddr : lp.getLinkAddresses()) {
                    InetAddress addr = linkAddr.getAddress();
                    if (addr instanceof Inet6Address) {
                        log("ipv6 type");
                        ipMask |= PDP_ADDR_MASK_IPV6;
                    } else if (addr instanceof Inet4Address) {
                        log("ipv4 type");
                        ipMask |= PDP_ADDR_MASK_IPV4;
                    } else {
                        loge("invalid address type");
                        ipMask |= PDP_ADDR_MASK_IPV4;
                    }
                }
                log("link prop: " + lp);
            } else {
                loge("Error: get null link properties");
            }

            switch (ipMask) {
                case PDP_ADDR_MASK_IPV4v6:
                    pdp_addr_type = PDP_ADDR_TYPE_IPV4v6;
                    break;
                case PDP_ADDR_MASK_IPV6:
                    pdp_addr_type = PDP_ADDR_TYPE_IPV6;
                    break;
                case PDP_ADDR_MASK_IPV4:
                    pdp_addr_type = PDP_ADDR_TYPE_IPV4;
                case PDP_ADDR_MASK_NONE:
                    // skip // error ??? (shouldn't be this)
                default:
                    // using default ipv4 (shouldn't be this)
                    break;
            };

            //imc_pdn_context_struct here
            defaultBearerProp = getDefaultBearerProperties(apnType, param.phoneId);
            log("responseDefaultBearerDataConnActivated: " + defaultBearerProp);
            defaultBearerPropEmpty = new DedicateBearerProperties();
            defaultBearerPropTemp = new DedicateBearerProperties();
            DedicateBearerProperties [] pdnContextsForVa = {defaultBearerPropEmpty, defaultBearerPropEmpty};
            int [] msgType = {IMC_CONCATENATED_MSG_TYPE_NONE, IMC_CONCATENATED_MSG_TYPE_NONE};
           int ratInfo = ImsAdapter.getRatType();
            if (null == defaultBearerProp) {
                //error happnening
                pdnContextsForVa[0] = defaultBearerPropEmpty;
                bResponse = false;
                loge("error happenening , default breaer should not be null");
            } else if (PDP_ADDR_MASK_NONE == pdp_addr_type) {
                bResponse = false;
                loge("error link prop, addr type shouldn't be PDP_ADDR_MASK_NONE");
            } else {
                // TODO: Check defaultBearer data valid or not
                if(defaultBearerProp.interfaceId == -1 && defaultBearerProp.cid == -1) {
                    log("invalid defaultBearerProp, interface id(" + defaultBearerProp.interfaceId +
                        "), cid(" + defaultBearerProp.cid + ")");

                    bResponse = false; // need to reject to VA and let VA retry
                }

                pdnCnt++;
                for(int i = 0; i < defaultBearerProp.concatenateBearers.size(); i++) {
                    DedicateBearerProperties bearerProp = defaultBearerProp.concatenateBearers.get(i);
                    if (defaultBearerProp.defaultCid == bearerProp.defaultCid) {
                        continue;
                    }
                    pdnCnt++;
                }

                //imc_pdn_count
                event.putByte(pdnCnt);
                event.putBytes(new byte[2]);    //padding
                if(bResponse == true) {
                    synchronized (sImsNetworkInterface) {
                        sImsNetworkInterface.put(defaultBearerProp.cid, lp.getInterfaceName());
                        log("Update IMS network interface name: " + sImsNetworkInterface);
                    }
                }

                //Emergency PDN

                if((param.isEmergency) && WFC_FEATURE){

                    apnType = PhoneConstants.APN_TYPE_EMERGENCY;
                    int endPos = mDataNetworkRequests.length;
                    int pos = getNetworkRequetsPos(apnType,endPos);
                    if(pos > -1 && pos < endPos){
                        NetworkCapabilities netCap = mDataNetworkRequests[pos].nwCap;
                        if(netCap.hasTransport(NetworkCapabilities.TRANSPORT_EPDG)){
                           ratInfo = ConnectivityManager.TYPE_EPDG;
                           log("responseDefaultBearerDataConnActivated Emergency PDN over EPDG ratInfo "+ratInfo);
                        } else if(netCap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)){
                           ratInfo = ConnectivityManager.TYPE_MOBILE_IMS;
                           log("responseDefaultBearerDataConnActivated Emergency PDN over LTE ratInfo "+ratInfo);
                        } else {
                           log("responseDefaultBearerDataConnActivated Unhandled case ");
                        }
                    }
                    mapCid.put(1,defaultBearerProp.cid);
                    //set the default bearer properties in the method setMappedBearer()

                    setMappedBearer(defaultBearerProp.cid);


                    log("responseDefaultBearerDataConnActivated Emergency and connection over epdg enabled : defaultBearerProp.cid "+defaultBearerProp.cid);
                    
                    mEimsId = defaultBearerProp.cid;
                    /*Emergency*/
                    if(ImsAdapter.getRatType() != ConnectivityManager.TYPE_MOBILE_IMS) {
                        try {
                            setLteAllocatedEmergencyCid(defaultBearerProp.cid);
                            defaultBearerPropTemp.defaultCid= defaultBearerProp.defaultCid;
                            defaultBearerPropTemp.cid = defaultBearerPropTemp.cid;
                            defaultBearerProp.cid = 5;
                            defaultBearerProp.defaultCid = 5;
                        } catch (NullPointerException ex){
                            log("Exception ex "+ex);
                        }
                    }
                }
                DataDispatcherUtil.writeRatCellInfo(event,ratInfo, mContext);
                if(DataDispatcherUtil.DBG) DataDispatcherUtil.dumpPdnAckRsp(event);

                // write main_context
                msgType[0] = IMC_CONCATENATED_MSG_TYPE_ACTIVATION;
                pdnContextsForVa[0] = defaultBearerProp;
            }

            if (bResponse == true) {
                for(int i = 0; i < pdnContextsForVa.length; i++) {
                    DataDispatcherUtil.writeAllBearersProperties(event, msgType[i], pdp_addr_type, pdnContextsForVa[i], param.isEmergency);
                }


                removeTransaction(param.transactionId);
                /* WFC */
                mPdnActive = true;
                sendVaEvent(event);
            } else {
                // TODO: need to add this code back for L migration
                rejectDefaultBearerDataConnActivation(param, FAILCAUSE_UNKNOWN, 1000);
            }

            if (param.isEmergency) {
                if (bResponse) {
                    setEmergencyCid(defaultBearerProp.cid);
                    if(ImsAdapter.getRatType() != ConnectivityManager.TYPE_MOBILE_IMS) {
                        defaultBearerProp.cid = defaultBearerPropTemp.cid;
                        defaultBearerProp.defaultCid = defaultBearerPropTemp.defaultCid;
                    }
                } else {
                    setEmergencyCid(INVALID_CID);
                }
            }
        } else {
            loge("responseDefaultBearerDataConnActivated but transactionId does not existed, ignore");
            bResponse = false;
        }
        return bResponse;
    }

    private void responseDefaultBearerDataConnDeactivated(TransactionParam param) {
        log("responseDefaultBearerDataConnDeactivated");
        releaseNwRequest(param.isEmergency? PhoneConstants.APN_TYPE_EMERGENCY:
                                PhoneConstants.APN_TYPE_IMS);
        synchronized (sImsNetworkInterface) {
            sImsNetworkInterface.remove(param.cid);
        }
        responseDataConnectionDeactivated(param);
        DataDispatcherUtil.clearVoLTEConnectedDedicatedBearer();
    }

    private void notifyDefaultBearerDataConnDeactivated(int cid, int cause, int fwkCause,
        int nPhoneId) {
        log("notifyDefaultBearerDataConnDeactivated");
        synchronized (sImsNetworkInterface) {
            sImsNetworkInterface.remove(cid);
        }
        notifyDataConnectionDeactivated(cid, cause, fwkCause, nPhoneId);
        DataDispatcherUtil.clearVoLTEConnectedDedicatedBearer();
    }

    private boolean isFaileCauseAllowedToDeatch(String failedStr) {
        boolean bRet = false;
        if ("LOST_CONNECTION".equals(failedStr)) {
            bRet = true;
        }
        log("isFaileCauseAllowedToDeatch ret: " + bRet);
        return bRet;
    }

    private boolean isReasonAllowedToDetach(String reason) {
        boolean bRet = false;
        if (REASON_APN_CHANGED.equals(reason) || REASON_QUERY_PLMN.equals(reason) ||
            REASON_NW_TYPE_CHANGED.equals(reason) || REASON_DATA_DETACHED.equals(reason)
            || reason == null) {
            bRet = true;
        }
        log("isReasonAllowedToDetach ret: " + bRet);
        return bRet;
    }

    // TODO: Default bearer [end]

    private void onDedicateDataConnectionStateChanged(int ddcId, DctConstants.State state,
                DedicateBearerProperties property, int nfailCause, int phoneId, String reason) {
        log("onDedicateDataConnectionStateChanged ddcId=" + ddcId + ", state=" + state + ", failCause=" + nfailCause + ", reason=" + reason + ", properties=" + property);
        boolean hasTransaction = false;

        synchronized (mTransactions) {
            Integer[] transactionKeyArray = getTransactionKeyArray();
            for (Integer transactionId : transactionKeyArray) {
                TransactionParam param = getTransaction(transactionId);
                if (param.ddcId == ddcId) {
                    hasTransaction = true;
                    switch (param.requestId) {
                        case VaConstants.MSG_ID_REQUEST_DEDICATE_BEARER_ACTIVATATION:
                            if (state == DctConstants.State.CONNECTED) {
                                if (nfailCause == 0 /*DcFailCause.NONE*/) {
                                    //response succees
                                    DataDispatcherUtil.addVoLTEConnectedDedicatedBearer(property);
                                    responseDedicateDataConnectionActivated(param, property);
                                } else {
                                    //response reject but have concatenated bearers
                                    rejectDedicateDataConnectionActivation(param, nfailCause, property);
                                }
                            } else if (state == DctConstants.State.FAILED || state == DctConstants.State.IDLE) {
                                //reject due to the state is not connected
                                rejectDedicateDataConnectionActivation(param, nfailCause, property);
                            }
                            break;
                        case VaConstants.MSG_ID_REQUEST_BEARER_DEACTIVATION:
                            if (param.cid == property.cid) {
                                if (REASON_BEARER_ABORT.equals(reason)) {
                                    //the transaction is to do abort, use failcause to know if the abort is success or not
                                    if (nfailCause == 0 /*DcFailCause.NONE*/) {
                                        log("onDedicateDataConnectionStateChanged to response abort success");
                                        responseDataConnectionDeactivated(param);
                                    } else {
                                        log("onDedicateDataConnectionStateChanged to response "
                                            + "abort fail failcause=" + nfailCause);
                                        rejectDataConnectionDeactivation(transactionId, nfailCause
                                                                , param.phoneId);
                                    }
                                } else {
                                    if (state == DctConstants.State.IDLE) {
                                        //response succees
                                        responseDataConnectionDeactivated(param);
                                    } else {
                                        //reject due to the state is not idle
                                        rejectDataConnectionDeactivation(transactionId, nfailCause
                                                                , param.phoneId);
                                    }
                                }
                            } else {
                                if (property.cid == -1) {
                                    //since the property is already invalid and ril response success
                                    //it means that the bearer is already deactivated
                                    //(AT+GCACT response 4105)
                                    log("onDedicateDataConnectionStateChanged ddcId is equaled "
                                        + "but cid is already deactivated "
                                        + "(MSG_ID_REQUEST_BEARER_DEACTIVATION)");
                                    responseDataConnectionDeactivated(param);
                                } else {
                                    //error case, ddcId is equaled but cid is not equaled
                                    loge("onDedicateDataConnectionStateChanged ddcId is "
                                        + "equaled but cid is not equaled "
                                        + "(MSG_ID_REQUEST_BEARER_DEACTIVATION)");
                                }
                            }
                            break;
                        case VaConstants.MSG_ID_REQUEST_BEARER_MODIFICATION:
                            if (param.cid == property.cid) {
                                if (state == DctConstants.State.CONNECTED) {
                                    //response succees
                                    responseDedicateDataConnectionModified(param, property);
                                } else {
                                    //reject due to the state is not connected
                                    rejectDedicateDataConnectionModification(param
                                                                , FAILCAUSE_UNKNOWN, property);
                                }
                            } else {
                                //error case, ddcId is equaled but cid is not equaled
                                loge("onDedicateDataConnectionStateChanged ddcId is equaled but "
                                    + "cid is not equaled (MSG_ID_REQUEST_BEARER_MODIFICATION)");
                            }
                            break;
                        default:
                            log("onDedicateDataConnectionStateChanged received unhandled state "
                                + "change event [" + transactionId + " " + param.requestId + "]");
                    }
                }
            }
        }

        if (!hasTransaction) {
            //no matched transactions, need to notify Va the state is changed
            switch (state) {
                case IDLE:
                DataDispatcherUtil.removeVoLTEConntectedDedicateBearer(
                        property.cid, property.defaultCid);
                if (!WfcDispatcher.isHandoverInProgress()
                        && (ImsAdapter.getRatType() != ConnectivityManager.TYPE_EPDG)) {
                    notifyDataConnectionDeactivated(property.cid, nfailCause,
                                                                FWK_CAUSE_NONE, phoneId);
                    log("onDedicateDataConnectionStateChanged handover not ongoing ");
                } else {
                    notifyDataConnectionDeactivatedDelayed(property.cid, nfailCause,
                            FWK_CAUSE_NONE, phoneId);
                    log("onDedicateDataConnectionStateChanged  handover ongoing");
                }
                    break;
                case FAILED:
                    loge("onDedicateDataConnectionStateChanged no matched transaction "
                        + "but receive state FAIL");
                    break;
                case CONNECTED:
                if (REASON_BEARER_MODIFICATION.equals(reason)) {
                    DataDispatcherUtil.removeVoLTEConntectedDedicateBearer(property.cid,
                            property.defaultCid);
                    DataDispatcherUtil.addVoLTEConnectedDedicatedBearer(property);
                        notifyDedicateDataConnectionModified(property, phoneId);
                } else {
                    DataDispatcherUtil
                            .addVoLTEConnectedDedicatedBearer(property);
                        notifyDedicateDataConnectionActivated(property, phoneId);
                }
                    break;
                default:
                    loge("onDedicateDataConnectionStateChanged not matched to any case");
            }
        }
    }

    private void responseDedicateDataConnectionActivated(TransactionParam param
        , DedicateBearerProperties property) {
        log("responseDedicateDataConnectionActivated " + param + ", " + property);
        responseDedicateDataConnection(param, property, IMC_CONCATENATED_MSG_TYPE_ACTIVATION);
    }

    private void responseDedicateDataConnection(TransactionParam param
        , DedicateBearerProperties property, int type) {
        boolean isEmergencyCid = false;
        if (hasTransaction(param.transactionId)) {
            ImsAdapter.VaEvent event = null;
            if (type == IMC_CONCATENATED_MSG_TYPE_ACTIVATION)
                event = new ImsAdapter.VaEvent(param.phoneId,
                                        VaConstants.MSG_ID_RESPONSE_DEDICATE_BEARER_ACTIVATION);
            else
                event = new ImsAdapter.VaEvent(param.phoneId,
                                        VaConstants.MSG_ID_RESPONSE_BEARER_MODIFICATION);

            log("responseDedicateDataConnection type=" + type + ", param " + param
                + ", property" + property);
            int concatenateBearersSize = property.concatenateBearers.size();
            //imcf_uint8 transaction_id;
            //imc_ps_cause_enum ps_cause;
            //imcf_uint8 pad[2];
            //imc_single_concatenated_msg_struct  main_context;
            //imcf_uint8 num_of_concatenated_contexts;
            //imcf_uint8 pad2[3];
            //imc_single_concatenated_msg_struct concatenated_context[IMC_MAX_CONCATENATED_NUM];
            event.putByte(param.transactionId);
            event.putByte(0);
            event.putBytes(new byte[2]); //padding
            if(param.isEmergency == true)
                isEmergencyCid = true;
            
            DataDispatcherUtil.writeDedicateBearer(event, type, property, isEmergencyCid);
            event.putByte(concatenateBearersSize); // write concatenated number
            event.putBytes(new byte[3]); // padding
            for (int i = 0; i < DataDispatcherUtil.IMC_MAX_CONCATENATED_NUM; i++) { // write concatenated contexts
                if (i < concatenateBearersSize) {
                    DataDispatcherUtil.writeDedicateBearer(event, type, property.concatenateBearers.get(i),isEmergencyCid);
                } else {
                    DataDispatcherUtil.writeDedicateBearer(event, type, new DedicateBearerProperties(),isEmergencyCid);
                }
            }

            removeTransaction(param.transactionId);

            sendVaEvent(event);
        } else {
            loge("responseDedicateDataConnection but transactionId does not existed, ignore");
        }
    }

    private void notifyDedicateDataConnectionActivated(DedicateBearerProperties property
        , int phoneId) {
        log("notifyDedicateDataConnectionActivated property" + property);
        notifyDedicateDataConnection(property, IMC_CONCATENATED_MSG_TYPE_ACTIVATION
            , phoneId);
    }

    private void notifyDedicateDataConnection(DedicateBearerProperties property, int type
        , int phoneId) {
        boolean isEmergencyCid = false;
        boolean handoverOngoing = false;
        synchronized (sImsNetworkInterface) {
            if (sImsNetworkInterface.get(property.defaultCid) == null) {
                loge("notifyDedicateDataConnection but default bearer does not existed, type=" + type + ", " + property);
                return;
            }
        }

        if(WfcDispatcher.isHandoverInProgress()) {
        	log("Handover ongoing don't send dedicate bearer activate");
            handoverOngoing = true;
        }
        if(property.defaultCid == mEimsId)
            isEmergencyCid = true;

        ImsAdapter.VaEvent event = null;
        int restoreValue = 0;
        int notifyMsg;
        if (type == IMC_CONCATENATED_MSG_TYPE_ACTIVATION)
            notifyMsg = VaConstants.MSG_ID_NOTIFY_DEDICATE_BEARER_ACTIVATED;
        else
            notifyMsg = VaConstants.MSG_ID_NOTIFY_BEARER_MODIFIED;

        event = new ImsAdapter.VaEvent(phoneId, notifyMsg);
        log("notifyDedicateDataConnection type=" + type + ", property" + property + " phoneId: "
                                + phoneId);
        //imc_ps_cause_enum ps_cause
        //imcf_uint8 pad [3]
        //imc_single_concatenated_msg_struct main_context
        //imcf_uint8 num_of_concatenated_contexts
        //imcf_uint8 pad2 [3]
        //imc_single_concatenated_msg_struct concatenated_context [IMC_MAX_CONCATENATED_NUM]
        event.putByte(0);
        event.putBytes(new byte[3]); //padding
        if((getLteAllocatedEmergencyCid() != INVALID_CID) && (ImsAdapter.getRatType() != ConnectivityManager.TYPE_MOBILE_IMS) && WFC_FEATURE) {
            log("notifyDedicateDataConnection LTE allocated emergency id getLteAllocatedEmergencyCid "+getLteAllocatedEmergencyCid() +"isWifiConnection "+isWifiConnection);
            if(getLteAllocatedEmergencyCid() == property.defaultCid) {
                restoreValue = property.defaultCid;
                property.defaultCid = 5;
            }
        }
        DataDispatcherUtil.writeDedicateBearer(event, type, property, isEmergencyCid);
        event.putBytes(new byte[3]); //padding
        if (restoreValue > 0 ){
            property.defaultCid = restoreValue;

        }

        log("notifyDedicateDataConnection LTE allocated emergency id getLteAllocatedEmergencyCid "+getLteAllocatedEmergencyCid() +"isWifiConnection "+isWifiConnection);

        for (int i=0; i<DataDispatcherUtil.IMC_MAX_CONCATENATED_NUM; i++) {
            if (i<property.concatenateBearers.size()) {
                DataDispatcherUtil.writeDedicateBearer(event, type, property.concatenateBearers.get(i),isEmergencyCid);
            } else {
                DataDispatcherUtil.writeDedicateBearer(event, type, new DedicateBearerProperties(),isEmergencyCid);
            }
        }

        log("DataDispatcher send event [" + event.getRequestID()+ ", " + event.getDataLen() + "]");
        if(handoverOngoing == false){
        mSocket.writeEvent(event);
        } else {
            notifyDedicateDataConnectionActivatedDelayed(event);
        }
    }

    private void rejectDedicateDataConnectionActivation(TransactionParam param, int failCause, DedicateBearerProperties property) {
        log("rejectDedicateBearerActivation param" + param + ", failCause=" + failCause + ", property" + property);
        rejectDedicateDataConnection(param, failCause, property, IMC_CONCATENATED_MSG_TYPE_ACTIVATION);
    }

    private void rejectDedicateDataConnection(TransactionParam param, int failCause, DedicateBearerProperties property, int type) {
        if (hasTransaction(param.transactionId)) {
            ImsAdapter.VaEvent event = null;
            boolean isEmergencyCid = false;
            if (type == IMC_CONCATENATED_MSG_TYPE_ACTIVATION)
                event = new ImsAdapter.VaEvent(param.phoneId,
                                        VaConstants.MSG_ID_REJECT_DEDICATE_BEARER_ACTIVATION);
            else
                event = new ImsAdapter.VaEvent(param.phoneId,
                                        VaConstants.MSG_ID_REJECT_BEARER_MODIFICATION);

            log("rejectDedicateDataConnection type=" + type + ", param" + param + ", failCause=" + failCause + "property" + property);
            //imcf_uint8 transaction_id
            //imc_ps_cause_enum ps_cause
            //imcf_uint8 num_of_concatenated_contexts
            //imcf_uint8 pad [1]
            //imc_single_concatenated_msg_struct concatenated_context [IMC_MAX_CONCATENATED_NUM]

            event.putByte(param.transactionId);
            event.putByte(failCause); //cause
            if (property == null) {
                event.putByte(0); //concatenated number
            } else {
                event.putByte(property.concatenateBearers.size()+1);
                //add one since the property itself and its concatenated bearer should all be counted
            }

            event.putByte(0); //padding
            if(param.isEmergency == true){
               isEmergencyCid = true;
            }
            if (property == null)
                DataDispatcherUtil.writeDedicateBearer(event, type, new DedicateBearerProperties(),isEmergencyCid);
            else{
                DataDispatcherUtil.writeDedicateBearer(event, type, property,isEmergencyCid); //write property itself
            }
            for (int i=0; i<DataDispatcherUtil.IMC_MAX_CONCATENATED_NUM - 1; i++) {
                if (property == null || i >= property.concatenateBearers.size())
                    DataDispatcherUtil.writeDedicateBearer(event, type, new DedicateBearerProperties(),isEmergencyCid);
                else
                    DataDispatcherUtil.writeDedicateBearer(event, type, property.concatenateBearers.get(i),isEmergencyCid); //write its concatenate bearers
            }

            removeTransaction(param.transactionId);

            log("DataDispatcher send event [" + event.getRequestID()+ ", " + event.getDataLen() + "]");
            mSocket.writeEvent(event);
        } else {
            loge("rejectDedicateDataConnection but transactionId does not existed, ignore");
        }
    }

    private void responseDataConnectionDeactivated(TransactionParam param) {
        if (hasTransaction(param.transactionId)) {
            ImsAdapter.VaEvent event = new ImsAdapter.VaEvent(param.phoneId,
                                                 VaConstants.MSG_ID_RESPONSE_BEARER_DEACTIVATION);
            log("responseDataConnectionDeactivated param" + param);
            //imcf_uint8 transaction_id
            //imcf_uint8 context_id
            //imcf_uint8 pad [2]
            event.putByte(param.transactionId);
            event.putByte(param.cid);
            event.putBytes(new byte[2]); //padding

            removeTransaction(param.transactionId);
            DataDispatcherUtil.removeVoLTEConntectedDedicateBearer(param.cid,
                                                                param.ddcId);
            sendVaEvent(event);
        } else {
            loge("responseDataConnectionDeactivated but transactionId does not existed, ignore");
        }
    }

    private void notifyWifiDataConnectionDeactivated(int cid, int cause) {
        ImsAdapter.VaEvent event = new ImsAdapter.VaEvent(0,
                                               VaConstants.MSG_ID_NOTIFY_BEARER_DEACTIVATED);
        log("notifyWifiDedicateDataConnectionDeactivated cid=" + cid + ", cause=" + cause);
        //imcf_uint8  context_id
        //imc_ps_cause_enum   ps_cause
        //imc_fwk_cause_enum fwk_cause (0: normal, 1: QUERAY_PLMN, rest reserved for future used)
        //imcf_uint8  pad [1]
        event.putByte(DataDispatcherUtil.writeCorrectBearerId(cid));
        event.putByte(cause);
        event.putByte(0);
        event.putByte(0); //padding

        sendVaEvent(event);
    }

    private void notifyDataConnectionDeactivated(int cid, int cause, int fwkCause, int phoneId) {
        ImsAdapter.VaEvent event = new ImsAdapter.VaEvent(phoneId,
                                               VaConstants.MSG_ID_NOTIFY_BEARER_DEACTIVATED);
        log("notifyDedicateDataConnectionDeactivated cid=" + cid + ", cause=" + cause +
            ", fwkCause: " + fwkCause + " ,phoneId: " + phoneId);
        //imcf_uint8  context_id
        //imc_ps_cause_enum   ps_cause
        //imc_fwk_cause_enum fwk_cause (0: normal, 1: QUERAY_PLMN, rest reserved for future used)
        //imcf_uint8  pad [1]
        event.putByte(DataDispatcherUtil.writeCorrectBearerId(cid));
        event.putByte(cause);
        event.putByte(fwkCause);
        event.putByte(0); //padding

        if(!WfcDispatcher.isHandoverInProgress() && (ImsAdapter.getRatType() != ConnectivityManager.TYPE_EPDG)){
            log("notifyDataConnectionDeactivated message send - no handover and wifi rat");
        sendVaEvent(event);
         } else {
            log("notifyDataConnectionDeactivated event not send - Handover ongoing");
         }

        //reject pcscf discorvery if default bearer is deactivated
        synchronized (mTransactions) {
            Integer[] transactionKeyArray = getTransactionKeyArray();
            for (Integer transactionId : transactionKeyArray) {
                TransactionParam param = getTransaction(transactionId);
                if (param.requestId == VaConstants.MSG_ID_REQUEST_PCSCF_DISCOVERY &&
                    param.cid == cid) {
                    rejectPcscfDiscovery(transactionId, 1);
                }
            }
        }
    }

    private void notifyDataConnectionDeactivatedDelayed(int cid, int cause, int fwkCause, int phoneId) {
        ImsAdapter.VaEvent event = new ImsAdapter.VaEvent(phoneId,
                                               VaConstants.MSG_ID_NOTIFY_BEARER_DEACTIVATED);
        log("notifyDataConnectionDeactivatedDelayed cid=" + cid + ", cause=" + cause +
            ", fwkCause: " + fwkCause + " ,phoneId: " + phoneId);
        //imcf_uint8  context_id
        //imc_ps_cause_enum   ps_cause
        //imc_fwk_cause_enum fwk_cause (0: normal, 1: QUERAY_PLMN, rest reserved for future used)
        //imcf_uint8  pad [1]
        event.putByte(DataDispatcherUtil.writeCorrectBearerId(cid));
        event.putByte(cause);
        event.putByte(fwkCause);
        event.putByte(0); //padding
        if(mDelayedEventAct != null) {
            mDelayedEventAct = null;
           
        } else {
            mDelayedEvent = event;
        }

        //reject pcscf discorvery if default bearer is deactivated
        synchronized (mTransactions) {
            Integer[] transactionKeyArray = getTransactionKeyArray();
            for (Integer transactionId : transactionKeyArray) {
                TransactionParam param = getTransaction(transactionId);
                if (param.requestId == VaConstants.MSG_ID_REQUEST_PCSCF_DISCOVERY &&
                    param.cid == cid) {
                    rejectPcscfDiscovery(transactionId, 1);
                }
            }
        }
    }

    private void notifyDedicateDataConnectionActivatedDelayed(ImsAdapter.VaEvent event){
        if(event.getRequestID() == VaConstants.MSG_ID_NOTIFY_DEDICATE_BEARER_ACTIVATED) {
            if(mDelayedEvent != null){
                mDelayedEvent = null;
            } else {
                mDelayedEventAct = event;
            }
            log("notifyDedicateDataConnectionActivatedDelayed mDelayedEventAct ="+mDelayedEventAct);
        } else {
            mDelayedEventModified = event;
            log("notifyDedicateDataConnectionModifiedDelayed mDelayedEventModified ="+mDelayedEventModified);
        }
    }

    private void rejectDataBearerDeactivation(int transactionId, int cause, int phoneId) {
        ImsAdapter.VaEvent event = new ImsAdapter.VaEvent(phoneId,
                                                VaConstants.MSG_ID_REJECT_BEARER_DEACTIVATION);
        log("rejectDataBearerDeactivation transactionId=" + transactionId + ", cause=" + cause);
        //imcf_uint8 transaction_id
        //imc_ps_cause_enum ps_cause
        //imcf_uint8 pad[2]
        event.putByte(transactionId);
        event.putByte(cause);
        event.putBytes(new byte[2]); //padding

        removeTransaction(transactionId);
        sendVaEvent(event);
    }

    private void rejectDataConnectionDeactivation(int transactionId, int cause, int phoneId) {
        if (hasTransaction(transactionId)) {
            log("rejectBearerDeactivation transactionId=" + transactionId + ", cause=" + cause);
            rejectDataBearerDeactivation(transactionId, cause, phoneId);
        } else {
            loge("rejectDataConnectionDeactivation but transactionId does not existed, ignore");
        }
    }

    private void responseDedicateDataConnectionModified(TransactionParam param, DedicateBearerProperties property) {
        log("responseDedicateDataConnectionModified param" + param + ", property" + property);
        //typedef imsa_imcb_dedicated_bearer_act_ack_rsp_struct imsa_imcb_modify_ack_rsp_struct;
        responseDedicateDataConnection(param, property, IMC_CONCATENATED_MSG_TYPE_MODIFICATION);
    }

    private void notifyDedicateDataConnectionModified(DedicateBearerProperties property,
        int phoneId) {
        log("notifyDedicateDataConnectionModified");
        notifyBearerModified(property, phoneId);
    }

    private void notifyBearerModified(DedicateBearerProperties property, int phoneId) {
        log("notifyBearerModified [" + property + "]");
        notifyDedicateDataConnection(property, IMC_CONCATENATED_MSG_TYPE_MODIFICATION, phoneId);
    }

    private void rejectDedicateDataConnectionModification(TransactionParam param, int failCause
        , DedicateBearerProperties property) {
        log("rejectDedicateDataConnectionModification param=" + param + ", failCause="
            + failCause + ", property=" + property);
        //typedef imsa_imcb_dedicated_bearer_act_rej_rsp_struct imsa_imcb_modify_rej_rsp_struct;
        rejectDedicateDataConnection(param, failCause, property
        , IMC_CONCATENATED_MSG_TYPE_MODIFICATION);
    }

    private void responsePcscfDiscovery(int transactionId, PcscfInfo pcscfInfo) {
        if (hasTransaction(transactionId)) {
            TransactionParam param = getTransaction(transactionId);
            ImsAdapter.VaEvent event = new ImsAdapter.VaEvent(param.phoneId,
                                                    VaConstants.MSG_ID_RESPONSE_PCSCF_DISCOVERY);
            log("responsePcscfDiscovery param=" + param + ", Pcscf" + pcscfInfo);
            //imcf_uint8 transaction_id
            //imc_pcscf_acquire_method_enum pcscf_aqcuire_method
            //imcf_uint8 pad [2]
            //imc_pcscf_list_struct pcscf_list
            event.putByte(transactionId);
            event.putByte(pcscfInfo.source);
            event.putBytes(new byte[2]); //padding
            DataDispatcherUtil.writePcscf(event, pcscfInfo);

            removeTransaction(transactionId);
            sendVaEvent(event);
        } else {
            loge("responsePcscfDiscovery but transactionId does not existed, ignore");
        }
    }

    private void rejectPcscfDiscovery(int transactionId,int failCause) {
        if (hasTransaction(transactionId)) {
            TransactionParam param = getTransaction(transactionId);
            ImsAdapter.VaEvent event = new ImsAdapter.VaEvent(param.phoneId,
                                                    VaConstants.MSG_ID_REJECT_PCSCF_DISCOVERY);
            log("rejectPcscfDiscovery param=" + param + ", failCause=" + failCause);
            if (param.cid == INVALID_CID) {
                log("rejectPcscfDiscovery but cid is invalid, ignore");
            } else {
                //imcf_uint8 transaction_id
                //imc_ps_cause_enum ps_caus
                //imcf_uint8 pad [2]
                event.putByte(transactionId);
                event.putByte(failCause);
                event.putBytes(new byte[2]); //padding

                removeTransaction(transactionId);

                sendVaEvent(event);
            }
        } else {
            loge("rejectPcscfDiscovery but transactionId does not existed, ignore");
        }
    }

    private void onNotifyGlobalIpAddr(InetAddress inetIpAddr, String apnType
        , String intfName, int phoneId, String ipKey) {
        int ipAddrType;
        int cid;
        int msgId = -1;
        byte [] ipAddrByteArray = null;
        InetAddress inetAddr = inetIpAddr;
        boolean bIsNwIntfReady = true;

        if (false == isApnIMSorEmergency(apnType)) {
            loge("onNotifyGlobalIpAddr invalid apnType: " + apnType);
            return;
        }

        if (intfName.isEmpty() == true) {
            loge("onNotifyGlobalIpAddr interface name is empty");
            return;
        }

        if (inetAddr instanceof Inet6Address) {
            msgId = VaConstants.MSG_ID_NOTIFY_IPV6_GLOBAL_ADDR;
        } else if (inetAddr instanceof Inet4Address) {
            log("onNotifyGlobalIpAddr IPAddress Type ipV4");
            msgId = VaConstants.MSG_ID_NOTIFY_IPV4_GLOBAL_ADDR;
        } else {
            loge("onNotifyGlobalIpAddr unknown IPAddress Type (using IPV4)");
            // TODO: temp using ipv4 here
            msgId = VaConstants.MSG_ID_NOTIFY_IPV4_GLOBAL_ADDR;
            //return;
        }

        // get cid first
        DedicateBearerProperties defaultBearerProperties = getDefaultBearerProperties(apnType
                                                                    , phoneId);
        if (defaultBearerProperties == null) {
            loge("onNotifyGlobalIpAddr default bearer properties is null, can't get cid");
            return;
        }

        cid = defaultBearerProperties.defaultCid;

        // convert address to byte array
        ipAddrByteArray = inetAddr.getAddress();
        log("onNotifyGlobalIpAddr intfName: " + intfName + ", cid: " + cid
            + ", byte addr length: " + ipAddrByteArray.length + ", addr: " + inetAddr);

        if (ipAddrByteArray == null) {
            loge("onNotifyGlobalIpAddr invalid ipAddrByteArray (null)");
            return;
        }

        synchronized (sImsNetworkInterface) {
            if (sImsNetworkInterface.get(cid) == null) {
                loge("onNotifyGlobalIpAddr invalid CID [" + cid + "] for network interface");
                bIsNwIntfReady = false;
            }
        }

        try {
            Network network = getConnectivityManager().getNetworkForType(
                                             convertImsOrEmergencyNetworkType(apnType));
            ImsAdapter.VaEvent event;

            if((apnType).equals(PhoneConstants.APN_TYPE_EMERGENCY) && ((ImsAdapter.getRatType() != ConnectivityManager.TYPE_MOBILE_IMS))    
              && WFC_FEATURE) {
                    log("onNotifyGlobalIpAddr Emergency apn with Normal PDN on WiFi apnType "+apnType);
                    event = mDataDispatcherUtil.composeGlobalIPAddrVaEvent(msgId, 5
                                            , network.netId, ipAddrByteArray, intfName, phoneId);
                }else{
                    if((apnType).equals(PhoneConstants.APN_TYPE_IMS)) {
                        event = mDataDispatcherUtil.composeGlobalIPAddrVaEvent(msgId, DataDispatcherUtil.writeCorrectBearerId(cid)
                                            , network.netId, ipAddrByteArray, intfName, phoneId);
                    } else {
                        event = mDataDispatcherUtil.composeGlobalIPAddrVaEvent(msgId, cid
                                        , network.netId, ipAddrByteArray, intfName, phoneId);                        
                    }
            }
            if (bIsNwIntfReady) {
                sendVaEvent(event);
            } else {
                // get the ip early then response pdn activated then ims
                // queue VaEvent first
                loge("network interface not ready!!, put to queue");
                mGlobalIPQueue.put(ipKey, event);
            }
        } catch (NullPointerException e) {
            loge("null pointer exception!!");
            e.printStackTrace();
        }
    }

    private void onNotifyDefaultBearerModification(DedicateBearerProperties defaultProperties,
        int phoneId) {
        log("onNotifyDefaultBearerModification");
        notifyBearerModified(defaultProperties, phoneId);
    }

    protected boolean hasTransaction(int transactionId) {
        synchronized (mTransactions) {
            return (mTransactions.get(transactionId) != null);
        }
    }

    protected void putTransaction(TransactionParam param) {
        synchronized (mTransactions) {
            mTransactions.put(param.transactionId, param);
            if (DUMP_TRANSACTION) dumpTransactions();
        }
    }

    protected void removeTransaction(int transactionId) {
        synchronized (mTransactions) {
            mTransactions.remove(transactionId);
            if (DUMP_TRANSACTION) dumpTransactions();
        }
    }

    protected TransactionParam getTransaction(int transactionId) {
        synchronized (mTransactions) {
            return mTransactions.get(transactionId);
        }
    }

    protected Integer[] getTransactionKeyArray() {
        synchronized (mTransactions) {
            Object[] array = mTransactions.keySet().toArray();
            if (array == null) {
                return new Integer[0];
            } else {
                Integer[] intArray = new Integer[array.length];
                for (int i=0; i<array.length; i++)
                    intArray[i] = (Integer)array[i];
                Arrays.sort(intArray);
                return intArray;
            }
        }
    }

    protected void dumpTransactions() {
       if (mTransactions.size() > 0) {
           log("====Start dump [transactions]====");
           for (TransactionParam param : mTransactions.values())
               log("dump transactions" + param);
           log("====End dump [transactions]====");
       } else {
           log("====dump [transactions] but empty====");
       }
    }

    private static void log(String text) {
        Xlog.d(TAG, "[dedicate] DataDispatcher " + text);
    }

    private static void loge(String text) {
        Xlog.e(TAG, "[dedicate] DataDispatcher " + text);
    }

    private class TransactionParam {
        public int transactionId;
        public int requestId;
        public int cid = -1;
        public int ddcId = -1;
        public boolean isEmergency = false;
        public int phoneId = SubscriptionManager.INVALID_PHONE_INDEX;

        public TransactionParam() {
        }

        public TransactionParam(int tid, int reqId, int phoneId) {
          transactionId = tid;
          requestId = reqId;
          this.phoneId = phoneId;
        }

        @Override
        public String toString() {
          return "[transactionId=" + transactionId + ", request=" + requestId + ", cid=" + cid +
            ", ddcid=" + ddcId + ", phoneId=" + phoneId + "]";
        }
    }

    private class PcscfDiscoveryDhcpThread extends Thread {
        private static final int ACTION_GET_V4 = 1;
        private static final int ACTION_GET_V6 = 2;
        private static final int ACTION_CLEAR = 3;
        private final String[] SERVICE_TYPE_ARRAY = {"SIP+D2T", "SIPS+D2T", "SIP+D2U"};

        private int mTransactionId;
        private String mInterfaceName;
        private VaEvent mEvent;
        private int mAction;

        public PcscfDiscoveryDhcpThread(int transactionId, String interfaceName,
                                            VaEvent event, int action) {
            mTransactionId = transactionId;
            mInterfaceName = interfaceName;
            mEvent = event;
            mAction = action;
        }

        public String getInterfaceName() {
            return mInterfaceName;
        }

        @Override
        public void interrupt() {
            log("PCSCF discovery dhcpThread interrupt X!!");
            if(!NetworkUtils.stopSipDhcpRequest(mInterfaceName)) {
                loge("can't stopSipDhcpRequest with interfaceName: " + mInterfaceName);
            } else {
                log("stopSipDhcpRequest with interfaceName: " + mInterfaceName);
            }
            clearSipInfo();
            super.interrupt();
            log("PCSCF discovery dhcpThread interrupt E!!");
        }

        private void getSipInfo() {
            /* ==In INetworkManagementService==
             *  String[] getSipInfo(String interfaceName, String service, Sting protocol)
             * @param interfaceName input
             * @param service input (service type is "SIP+D2U", "SIP+D2T", "SIPS+D2T", for UDP/TCP/TSL service
             * @param protocol type ("v4" or "v6")
             * @return result_array output, String[0] = hostname, String[1] = port
             * @hide
             */

            if (!NetworkUtils.doSipDhcpRequest(mInterfaceName)) {
                loge("PCSCF discovery doSipDhcpRequest response fail [interface=" + mInterfaceName + "]");
                rejectPcscfDiscovery(mTransactionId, 1);
                return;
            }

            PcscfInfo pcscfInfo = null;

            for (String serviceType : SERVICE_TYPE_ARRAY) {
                String[] pcscfHost = null;
                byte[] pcscfByteArray = null;
                String pcscf = null;
                int port = 0;
                try {
                    INetworkManagementService netd = INetworkManagementService.
                        Stub.asInterface(ServiceManager.getService(
                                        Context.NETWORKMANAGEMENT_SERVICE));
                    if (ACTION_GET_V4 == mAction)
                        pcscfHost = netd.getSipInfo(mInterfaceName, serviceType, "v4");
                    else
                        pcscfHost = netd.getSipInfo(mInterfaceName, serviceType, "v6");

                } catch (RemoteException e) {
                    e.printStackTrace();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }

                if (isInterrupted()) {
                    loge("reject PCSCF discovery DHCP due to the dhcp thread is interrupted before DNS query [" + serviceType + "]");
                    rejectPcscfDiscovery(mTransactionId, 1);
                    return;
                } else if (pcscfHost != null) {
                    try {
                        log("PCSCF discovery DHCP result [host=" + pcscfHost[0] + ", port=" + pcscfHost[1] + "]");
                        InetAddress inetAddress = InetAddress.getByName(pcscfHost[0]);
                        pcscfByteArray = inetAddress.getAddress();
                        port = Integer.parseInt(pcscfHost[1]);
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                } else {
                    loge("PCSCF discovery DHCP but no SIP response [" + serviceType + "]");
                }

                if (isInterrupted()) {
                    loge("reject PCSCF discovery DHCP due to the dhcp thread is interrupted after DNS query [" + serviceType + "]");
                    rejectPcscfDiscovery(mTransactionId, 1);
                    return;
                } else if (pcscfByteArray != null && pcscfByteArray.length > 0) {
                    StringBuffer buf = new StringBuffer(pcscfByteArray.length);
                    for (int i=0; i<pcscfByteArray.length; i++) {
                        if (i == 0)
                            buf.append((int)pcscfByteArray[i]);
                        else
                            buf.append("." + (int)pcscfByteArray[i]);
                    }
                    pcscf = buf.toString();

                    if (pcscfInfo == null) {
                        //here we try to create the response when first time we need to add a P-CSCF address
                        pcscfInfo = new PcscfInfo();
                    }
                    loge("PCSCF discovery DHCP get server address [" + pcscf + ", port=" + port + ", serviceTYpe=" + serviceType + "]");
                    pcscfInfo.add(pcscf, port);
                } else {
                    loge("PCSCF discovery DHCP but empty SIP host [" + serviceType + "]");
                }
            }

            if (pcscfInfo != null)
                responsePcscfDiscovery(mTransactionId, pcscfInfo);
            else
                rejectPcscfDiscovery(mTransactionId, 1);
        }

        private void clearSipInfo() {
            /* ==In INetworkManagementService==
             * void clearSipInfo(String interfaceName)
             * @hide
             * @param interfaceName input
             */

            try {
                INetworkManagementService netd = INetworkManagementService.Stub.asInterface(
                                   ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE));
                netd.clearSipInfo(mInterfaceName);
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            //make sure there is only one thread running
            synchronized (PcscfDiscoveryDhcpThread.class) {
                if (mAction == ACTION_GET_V4 || mAction == ACTION_GET_V6) {
                    log("PCSCF discovery DHCP thread started [threadid=" + getId() + ", " + (mAction == ACTION_GET_V4 ? "ACTION_GET_V4]" : "ACTION_GET_V6]"));
                    getSipInfo();
                } else {
                    log("PCSCF discovery DHCP thread started [threadid=" + getId() + ", CLEAR]");
                    clearSipInfo();
                }

                log("PCSCF discovery DHCP thread finished [threadid=" + getId() + "]");
            }
        }
    }

    private void delayForSeconds(int seconds) {
        try {
            Thread.sleep(seconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void removeReceivedAddress(String apnType) {
        int startIdx = -1;
        if (IMS_APN.equals(apnType)) {
            startIdx = IPV4_IMS;
        } else if  (EMERGENCY_APN.equals(apnType)) {
            startIdx = IPV4_EIMS;
        }

        if (startIdx > -1 && startIdx < IPV6_EIMS) {
            for (int i = 0; i < 2; i++) {
                mAddressStatus.remove(IP_KEY[startIdx]);
                mGlobalIPQueue.remove(IP_KEY[startIdx++]);
            }
            log("removeReceivedAddress, addressStatus: " + mAddressStatus +
                "IP Queue: " + mGlobalIPQueue);
        }
    }

    private boolean isIpAddressReceived(String apnType, InetAddress addr) {
        boolean bRet = false;
        String key = "";
        int keyIdx = getIpKeyIdx(apnType, addr);

        if (keyIdx != -1) {
            try{
                key = IP_KEY[keyIdx];
                if (mAddressStatus.containsKey(key)) {
                    bRet = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        log("isIpAddressReceived Key: " + key + ", bRet: " + bRet);
        return bRet;
    }

    private int getIpKeyIdx(String apnType, InetAddress addr) {
        int keyIdx = -1;
        if (IMS_APN.equals(apnType)) {
            keyIdx = IPV4_IMS;
        } else if (EMERGENCY_APN.equals(apnType)) {
            keyIdx = IPV4_EIMS;
        }

        if (keyIdx != -1) {
            if (isIpAddressV6(addr)) {
                keyIdx += 1;
            } else if (isIpAddressV4(addr)) {
                keyIdx += 0;
            } else {
                loge("invalid ip type");
                keyIdx = -1 ;
            }
        } else {
            loge("error happened, not ims or emergency apn!!");
        }

        log("getIpKeyIdx, ret: " + keyIdx + ", apn: " + apnType);
        return keyIdx;
    }

    private boolean isIpAddressV6(InetAddress addr) {
        boolean bRet = false;
        if (addr instanceof Inet6Address) {
            log("addr: " + addr + " is IPV6");
            bRet = true;
        }
        return bRet;
    }
    private boolean isIpAddressV4(InetAddress addr) {
        boolean bRet = false;
        if (addr instanceof Inet4Address) {
            log("addr: " + addr + " is IPV4");
            bRet = true;
        }
        return bRet;
    }

    private void resetFailedCause() {
        log("resetFailedCause");
        for (int i = 0; i < mFailedCause.length; i++) {
            mFailedCause[i] = FAILCAUSE_NONE;
        }
    }

    private void setFailedCause(String apnType, int failedCause) {
        if (IMS_APN.equals(apnType)) {
            mFailedCause[IMS_PDN] = failedCause;
        } else if (EMERGENCY_APN.equals(apnType)) {
            mFailedCause[IMS_EMERGENCY_PDN] = failedCause;
        } else {
            log("unknown apn type: " + apnType);
            return;
        }

        log("set apnType: " + apnType + " failedCause: " + failedCause);
    }

    private int getFailedCause(String apnType) {
        int failedCause = FAILCAUSE_UNKNOWN;
        if (IMS_APN.equals(apnType)) {
            failedCause = mFailedCause[IMS_PDN];
        } else if (EMERGENCY_APN.equals(apnType)) {
            failedCause = mFailedCause[IMS_EMERGENCY_PDN];
        }

        log("getFailedCause: " + failedCause + " for apnType: " + apnType);
        return failedCause;
    }

    private boolean isApnIMSorEmergency(String apnType) {
        return (IMS_APN.equals(apnType) || EMERGENCY_APN.equals(apnType)) ? true : false;
    }

    private boolean isMsgAllowed(String apnType, int changed) {
        return (isApnIMSorEmergency(apnType)&& changed == 1) ? true : false;
    }

    private void removeDeactivateCid(String apnType) {
        log("removeDeactivateCid: " + mDeactivateCid + ", apnType: " + apnType);
        mDeactivateCid.remove(apnType);
    }

    private void clearDeactivateCid() {
        log("clearDeactivateCid: " + mDeactivateCid);
        mDeactivateCid.clear();
    }

    private void setDeactivateCid(int [] cidArray, String apnType) {
        log("setDeactivateCid: " + mDeactivateCid + ", apnType: " + apnType);
        if (cidArray != null) {
            for (int i = 0; i < cidArray.length; i ++) {
                mDeactivateCid.put(apnType, cidArray[i]);
            }
        }
        log("setDeactivateCid, size: " + mDeactivateCid.size() + ", cid(s): " + mDeactivateCid);
    }


    // VOLTE
   /**
     * .This function will check input cid number is dedicate bearer or not.
     * <p>
     * @param cid indicate which cid to check
     * <p>
     * @param phoneId indicate input phoneId for MSIM
     * <p>
     * @return true or false for indicating is dedicate bearer or not
     *
     */
    public boolean isDedicateBearer(int cid, int phoneId) {
        boolean bRet = false;
        try {
            bRet = getITelephonyEx().isDedicateBearer(cid, phoneId);
        } catch (RemoteException ex) {
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return bRet;
    }

    /**
    * This function will disable Dedicate bearer.
    * @param reason for indicating what reason for disabling dedicate bearer
    * @param ddcid for indicating which dedicate beare cide need to be disable
    * @param phoneId indicate input phoneId for MSIM
    * @return int return ddcid of disable dedicated bearer
    *            -1: some thing wrong
    */
    public int disableDedicateBearer(String reason, int ddcid, int phoneId) {
        int nddcid = -1;
        try {
            nddcid = getITelephonyEx().disableDedicateBearer(reason, ddcid, phoneId);
        } catch (RemoteException ex) {
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return nddcid;
    }

    /**
    * This function will enable Dedicate bearer.
    * <p>
    * @param apnType input apnType for enable dedicate bearer
    * @param signalingFlag boolean value for indicating signaling or not
    * @param qosStatus input qosStatus info
    * @param tftStatus input tftStatus info
    * @param phoneId indicate input phoneId for MSIM
    * @return int return ddcid of enable dedicated bearer
    *            -1: some thing wrong
    */
    public int enableDedicateBearer(String apnType, boolean signalingFlag,
                            QosStatus qosStatus, TftStatus tftStatus, int phoneId) {
        int ddcid = -1;
        try {
            ddcid = getITelephonyEx().enableDedicateBearer(apnType, signalingFlag,
                                            qosStatus, tftStatus, phoneId);
        } catch (RemoteException ex) {
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return ddcid;
    }

    /**
    * This function will abort Dedicate bearer.
    * @param reason for indicating what reason for abort enable dedicate bearer
    * @param ddcid for indicating which dedicate beare cide need to be abort
    * @param phoneId indicate input phoneId for MSIM
    * @return int return ddcid of abort dedicated bearer
    *            -1: some thing wrong
    */
    public int abortEnableDedicateBearer(String reason, int ddcid, int phoneId) {
        int nddcid = -1;
        try {
            nddcid = getITelephonyEx().abortEnableDedicateBearer(reason, ddcid, phoneId);
        } catch (RemoteException ex) {
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return nddcid;
    }

    /**
     * This function will modify Dedicate bearer.
     *
     * @param cid for indicating which dedicate cid to modify
     * @param qosStatus input qosStatus for modify
     * @param tftStatus input tftStatus for modify
     * @param phoneId indicate input phoneId for MSIM
     * @return int: return ddcid of modify dedicated bearer
     *            -1: some thing wrong
     */

    public int modifyDedicateBearer(int cid, QosStatus qosStatus, TftStatus tftStatus
        , int phoneId) {
        int nddcid = -1;
        try {
            nddcid = getITelephonyEx().modifyDedicateBearer(cid, qosStatus, tftStatus, phoneId);
        } catch (RemoteException ex) {
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return nddcid;
    }

    /**
     * This function will set Default Bearer Config for apnContext.
     *
     * @param apnType for indicating which apnType to set default bearer config
     * @param defaultBearerConfig config of default bearer config to be set
     * @param phoneId indicate input phoneId for MSIM
     * @return int: return success or not
     *            0: set default bearer config successfully
     */
    public int setDefaultBearerConfig(String apnType, DefaultBearerConfig defaultBearerConfig
        , int phoneId) {
        int ret = -1;
        try {
            ret = getITelephonyEx().setDefaultBearerConfig(apnType, defaultBearerConfig, phoneId);
        } catch (RemoteException ex) {
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return ret;
    }

    /**
     * This function will get Default Bearer properties for apn type.
     *
     * @param apnType input apn type for get the mapping default bearer properties
     * @param phoneId indicate input phoneId for MSIM
     * @return DedicateBearerProperties return the default beare properties for input apn type
     *                             return null if something wrong
     *
     */
    public DedicateBearerProperties getDefaultBearerProperties(String apnType, int phoneId) {
        DedicateBearerProperties defaultBearerProp = null;
        try {
            defaultBearerProp = getITelephonyEx().getDefaultBearerProperties(apnType, phoneId);
        } catch (RemoteException ex) {
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return defaultBearerProp;
    }

    /**


    /**
     * This function will get DcFailCause with int format.
     *
     * @param apnType for geting which last error of apnType
     * @param phoneId indicate input phoneId for MSIM
     * @return int: return int failCause value
     */
    public int getLastDataConnectionFailCause(String apnType, int phoneId) {
        int nErrCode = FAILCAUSE_NONE;
        boolean exFlag = true;
        try {
            nErrCode = getITelephonyEx().getLastDataConnectionFailCause(apnType, phoneId);
            exFlag = false;
        } catch (RemoteException ex) {
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }

        if (exFlag || nErrCode == FAILCAUSE_UNKNOWN) {
            // TODO: due to dcac might has been destroy at Data FWK
            nErrCode = getFailedCause(apnType);
        }

        return nErrCode;
    }

    /**
     * This function will get deactivate cids.
     *
     * @param apnType for getting which apnType deactivate cid array
     * @param phoneId indicate input phoneId for MSIM
     * @return int []: int array about cids which is(are) deactivated
     */
    public int [] getDeactivateCidArray(String apnType, int phoneId) {
        int [] cidArray = null;
        try {
            cidArray = getITelephonyEx().getDeactivateCidArray(apnType, phoneId);
        } catch (RemoteException ex) {
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return cidArray;
    }

    public boolean isImsApnExists(int phoneId) {
        boolean hasIMSApn = false;
        try {
            String operator = "";
            operator = getTelephonyManager().getSimOperatorNumericForPhone(phoneId);

            if (operator != null) {
                String selection = "numeric = '" + operator + "'";
                selection += " and type like '" + "%ims%'";
                if (DBG) log("query: selection=" + selection);
                Uri CONTENT_URI = Uri.parse("content://telephony/carriers");
                Cursor cursor = mContext.getContentResolver().query(
                        CONTENT_URI, null, selection, null, null);
    
                if (cursor != null) {
                    if (cursor.getCount() > 0) {
                        log("has ims apn!!");
                        hasIMSApn = true;
                    }
                    cursor.close();
                }
            }
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return hasIMSApn;
    }

    /**
     * This function will get link properties of input apn type.
     *
     * @param apnType input apn type for geting link properties
     * @param phoneId indicate input phoneId for MSIM
     * @return LinkProperties: return correspondent link properties with input apn type
     */
    public LinkProperties getLinkProperties(String apnType, int phoneId) {
        LinkProperties lp = null;
        try {
            lp = getITelephonyEx().getLinkProperties(apnType, phoneId);
        } catch (RemoteException ex) {
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return lp;
    }

    /**
     * This function will do pcscf Discovery.
     *
     * @param apnType input apn type for geting pcscf
     * @param cid input cid
     * @param phoneId indicate input phoneId for MSIM
     * @param onComplete for response event while pcscf discovery done
     * @return int: return 0: OK, -1: failed
     */
    public int pcscfDiscovery(String apnType, int cid, int phoneId, Message onComplete) {
        int result = -1;
        try {
            result = getITelephonyEx().pcscfDiscovery(apnType, cid, phoneId, onComplete);
        } catch (RemoteException ex) {
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return result;
    }

    private ITelephonyEx getITelephonyEx() {
        return ITelephonyEx.Stub.asInterface(
                ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
    }

    private ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    private TelephonyManager getTelephonyManager() {
        return (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
    }

    private IPhoneSubInfo getSubscriberInfo() {
        // get it each time because that process crashes a lot
        return IPhoneSubInfo.Stub.asInterface(ServiceManager.getService("iphonesubinfo"));
    }

    private NetworkInfo getImsOrEmergencyNetworkInfo(String apnType) {
        NetworkInfo networkInfo = null; //return null if not ims/emergency apn
        int networkType = convertImsOrEmergencyNetworkType(apnType);
        if (networkType == ConnectivityManager.TYPE_NONE) {
            loge("not ims or emergency apn, apn is " + apnType);
            return networkInfo;
        }

        try {
            networkInfo = getConnectivityManager().getNetworkInfo(networkType);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        return networkInfo;
    }
    private NetworkInfo.State getImsOrEmergencyNetworkInfoState(String apnType) {
        NetworkInfo.State state = NetworkInfo.State.UNKNOWN;
        try {
            state = getImsOrEmergencyNetworkInfo(apnType).getState();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        return state;
    }

    private NetworkInfo.DetailedState getImsOrEmergencyNetworkInfoDetailState(String apnType) {
        NetworkInfo.DetailedState state = NetworkInfo.DetailedState.IDLE;
        try {
            state = getImsOrEmergencyNetworkInfo(apnType).getDetailedState();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        return state;
    }

    private int getFwkCauseFromReason(String reason) {
        int fwkCause = FWK_CAUSE_NONE;
        if (REASON_QUERY_PLMN.equals(reason)) {
            fwkCause = FWK_CAUSE_QUERY_PLMN;
        }
        return fwkCause;
    }

    // L ver network request related API
    private int getNetworkRequetsPos(String requestApnType, int endPos) {
        int pos = -1;
        for (int i = 0; i < endPos; i++) {
            if (TextUtils.equals (mDataNetworkRequests[i].apnType, requestApnType)) {
                pos = i;
                break;
            }
        }
        return pos;
    }

    private int releaseNwRequest(String requestApnType) {
        int nRet = 0;
        int endPos = mDataNetworkRequests.length;
        int pos = getNetworkRequetsPos(requestApnType, endPos);

        if (pos > -1 && pos < endPos) {
            log("releaseNwRequest pos: " + pos + ", requestApnType: "
                            + requestApnType);
            NetworkCallback nwCb = mDataNetworkRequests[pos].nwCb;
            try {
                getConnectivityManager().unregisterNetworkCallback(nwCb);
            } catch (IllegalArgumentException ex) {
                loge("cb already has been released!!");
            }
            if(requestApnType.equals(PhoneConstants.APN_TYPE_IMS)){
                setMappedBearer(INVALID_CID);
                mPdnActive = false;
                ImsAdapter.setRatType(ConnectivityManager.TYPE_NONE);
                DataDispatcherUtil.resetCids();
            } else {
                setEmergencyCid(INVALID_CID);
            }
        } else {
            loge("unknown apnType: " + requestApnType + " skip requestNetwork ");
            nRet = -1;
        }

            
        return nRet;
    }
    private int requestNwRequest(String requestApnType, int phoneId) {
        int nRet = 0;
        int endPos = mDataNetworkRequests.length;
        int pos =  getNetworkRequetsPos(requestApnType, endPos);
        int subId =  SubscriptionManager.getSubIdUsingPhoneId(phoneId);

        if (pos > -1 && pos < endPos) {
            log("requestNwRequest pos: " + pos + ", requestApnType: "
                            + requestApnType + ", subId: " + subId);
            NetworkCallback nwCb = mDataNetworkRequests[pos].nwCb;

            // generator network request
            Builder builder = new NetworkRequest.Builder();
            builder.addCapability(APN_CAP_LIST[pos]);
            builder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
            if (APN_CAP_LIST[pos] != NetworkCapabilities.NET_CAPABILITY_EIMS && WFC_FEATURE) {
                builder.addTransportType(NetworkCapabilities.TRANSPORT_EPDG);
            }
            builder.setNetworkSpecifier(String.valueOf(subId));
            mDataNetworkRequests[pos].nwRequest = builder.build();
            NetworkRequest nwRequest = mDataNetworkRequests[pos].nwRequest;

            log("mDataNetworkRequests[" + pos +"]: " +  mDataNetworkRequests[pos]);

            releaseNwRequest(requestApnType);
//            getConnectivityManager().requestNetwork(nwRequest, nwCb,
//                                            ConnectivityManager.MAX_NETWORK_REQUEST_TIMEOUT_MS);
            if (SystemProperties.getInt("persist.net.wo.debug.no_ims", 0) != 1) {
               log("Into if persist.net.wo.debug.no_ims" + SystemProperties.getInt("persist.net.wo.debug.no_ims", 0));
            getConnectivityManager().requestNetwork(nwRequest, nwCb,
                                            ConnectivityManager.MAX_NETWORK_REQUEST_TIMEOUT_MS);
            }
        } else {
            loge("unknow apnType: " + requestApnType + " skip requestNetwork ");
            nRet = -1;
        }

        return nRet;
    }



    private void createNetworkRequest() {
        final int count = APN_CAP_LIST.length;
        mDataNetworkRequests = new DataDispatcherNetworkRequest[count];

        for (int i = 0; i < count; i++) {
            NetworkCapabilities netCap = new NetworkCapabilities();
            int cap = APN_CAP_LIST[i];
            netCap.addCapability(cap);
            netCap.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);

            // TODO: is emergency pdn need EPDG ????, tmp skip emergency for WFC
            if (APN_CAP_LIST[i] != NetworkCapabilities.NET_CAPABILITY_EIMS && WFC_FEATURE) {
                netCap.addTransportType(NetworkCapabilities.TRANSPORT_EPDG);
            }

            mDataNetworkRequests[i] = new DataDispatcherNetworkRequest(getNwCBbyCap(cap),
                                                                            getApnTypeByCap(cap));
            mDataNetworkRequests[i].nwCap = netCap;
            //mDataNetworkRequests[i].nwRequest = netRequest;
        }
    }

    NetworkCallback mImsNetworkCallback = new NetworkCallback() {
        int networkType;
        int disconnectCause;

        @Override
        public void onPreCheck(Network network) {
            log("onPrecheck: networInfo: "
                + getConnectivityManager().getNetworkInfo(network));
        }

        @Override
        public void onLosing(Network network, int maxMsToLive) {
            log("onLosing: networInfo: "
                + getConnectivityManager().getNetworkInfo(network)
                + " maxMsToLive: " + maxMsToLive);
            //String info = "onLosing(" + APN + ":" + network + ")";
            //Log.d(TAG, info);
            //Message msg = mHandler.obtainMessage(EVENT_ON_LOSING, (Object) network);
            //mHandler.sendMessage(msg);
        }

            @Override
            public void onAvailable(Network network) {
            log("onAvailable: networInfo: "
                    + getConnectivityManager().getNetworkInfo(network));
            try {
                int ratType = getConnectivityManager().getNetworkInfo(network)
                        .getType();
                ImsAdapter.setRatType(ratType);
                log("onAvailable ratType =" + ratType);
                if (ratType == ConnectivityManager.TYPE_EPDG) {

                    log("responseDefaultBearerWifiConnActivated called");

                    ConnectivityManager mConnMgr = (ConnectivityManager) mContext
                            .getSystemService(Context.CONNECTIVITY_SERVICE);
                    mLink = mConnMgr.getLinkProperties(network);
                    if (mLink != null) {
                        mAddresses = mLink.getLinkAddresses();
                        NetworkCapabilities cap = mConnMgr
                                .getNetworkCapabilities(network);
                        mPcscfAddr = mLink.getPcscfServers();
                        if (!mPcscfAddr.isEmpty() && mPcscfAddr.get(0) != null) {
                            // if(!addresses.isEmpty()){
                            responseDefaultBearerWifiConnActivated(IMS_APN,
                                    mPcscfAddr, mAddresses);
                        }

                    }
                }
            } catch (NullPointerException e) {
                log("Null pointer exception caught " + e);
            } catch (Exception e) {
                log("Execption in onAvailable " + e);
            }
            }

            @Override
            public void onLost(Network network) {
            log("onLost: networInfo: "
                    + getConnectivityManager().getNetworkInfo(network));

            disconnectCause = getConnectivityManager().getDisconnectCause(
                    ConnectivityManager.TYPE_MOBILE_IMS);
            sendConnectionError(disconnectCause);
            log("onLost disconnect cause =" + disconnectCause);
            log("onLost rat type =" + ImsAdapter.getRatType());
            if (ImsAdapter.getRatType() == ConnectivityManager.TYPE_EPDG){
                notifyWifiDataConnectionDeactivated(getWiFicid(),
                        FAILCAUSE_UNKNOWN);
                releaseNwRequest(PhoneConstants.APN_TYPE_IMS);
            } else if (ImsAdapter.getRatType() == ConnectivityManager.TYPE_NONE) {
                //retryPdnActivation();
            } else {
                log("Unhandled case");
                //releaseNwRequest(PhoneConstants.APN_TYPE_IMS);
            }
            ImsAdapter.setRatType(ConnectivityManager.TYPE_NONE);
            isWifiConnection = false;
            /*release network callback*/
            log("onLost() - Release the network request in onLost");

                }

        @Override
        public void onUnavailable() {
            log("onUnavailable: ");
            /*
             * getConnectivityManager().unregisterNetworkCallback(
             * mImsNetworkCallback);
             */
            disconnectCause = getConnectivityManager().getDisconnectCause(
                    ConnectivityManager.TYPE_MOBILE_IMS);
            sendConnectionError(disconnectCause);
        }

        private void sendConnectionError(int error) {
            log("sendConnectionError: " + error);
            Intent intent = new Intent(
                    TelephonyIntents.ACTION_NOTIFY_CONNECTION_ERROR);
            intent.putExtra(TelephonyIntents.EXTRA_ERROR_CODE, error);
			mContext.sendBroadcast(intent);

        }
    };

    NetworkCallback mEImsNetworkCallback = new NetworkCallback() {
        int disconnect_cause;
        @Override
        public void onPreCheck(Network network) {
            log("onPrecheck: networInfo: " + getConnectivityManager().getNetworkInfo(network));
            }

            @Override
            public void onLosing(Network network, int maxMsToLive) {
            log("onLosing: networInfo: " + getConnectivityManager().getNetworkInfo(network)
                + " maxMsToLive: " + maxMsToLive);
            //String info = "onLosing(" + APN + ":" + network + ")";
            //Log.d(TAG, info);
            //Message msg = mHandler.obtainMessage(EVENT_ON_LOSING, (Object) network);
            //mHandler.sendMessage(msg);
        }
        @Override
        public void onAvailable(Network network) {
            log("onAvailable: networInfo: "
                    + getConnectivityManager().getNetworkInfo(network));
            int ratType = getConnectivityManager().getNetworkInfo(network)
                .getType();
            //ImsAdapter.setRatType(ratType);
            log("onAvailable ratType =" + ratType);
            if (ratType == ConnectivityManager.TYPE_EPDG) {

                log("responseDefaultBearerWifiConnActivated called");

                ConnectivityManager mConnMgr = (ConnectivityManager) mContext
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                mLinkEmrg = mConnMgr.getLinkProperties(network);
                if (mLinkEmrg != null) {
                    mAddressesEmrg = mLinkEmrg.getLinkAddresses();
                    NetworkCapabilities cap = mConnMgr
                            .getNetworkCapabilities(network);
                    mPcscfAddrEmgr = mLinkEmrg.getPcscfServers();
                    if (!mPcscfAddrEmgr.isEmpty()
                            && mPcscfAddrEmgr.get(0) != null) {
                        // if(!addresses.isEmpty()){
                        responseDefaultBearerWifiConnActivated(EMERGENCY_APN,
                                mPcscfAddrEmgr, mAddressesEmrg);
                    }

                }
            }
        }
        @Override
        public void onLost(Network network) {
            log("onLost: networInfo: " + getConnectivityManager().getNetworkInfo(network));
            getConnectivityManager().unregisterNetworkCallback(mEImsNetworkCallback);
        }
        @Override
        public void onUnavailable() {
            log("onUnavailable: ");
            getConnectivityManager().unregisterNetworkCallback(mEImsNetworkCallback);
            }
        };

    private NetworkCallback getNwCBbyCap(int cap) {
        NetworkCallback nwCb = null;
        switch (cap) {
            case NetworkCapabilities.NET_CAPABILITY_IMS:
                nwCb = mImsNetworkCallback;
                break;
            case NetworkCapabilities.NET_CAPABILITY_EIMS:
                nwCb = mEImsNetworkCallback;
                break;
            default:
                loge("error: nwCB=null for invalid cap (" + cap + ")");
        }
        return nwCb;
    }

    private String getApnTypeByCap(int cap) {
        String apnType = "";
        switch (cap) {
            case NetworkCapabilities.NET_CAPABILITY_IMS:
                apnType = PhoneConstants.APN_TYPE_IMS;
                break;
            case NetworkCapabilities.NET_CAPABILITY_EIMS:
                apnType = PhoneConstants.APN_TYPE_EMERGENCY;
                break;
            default:
                loge("error: apnType=\"\" for invalid cap (" + cap + ")");
                }
        return apnType;
            }


    private int getLegacyTypeForNwCap(int capability) {
        int legacyType = ConnectivityManager.TYPE_NONE;
        switch (capability) {
        case NetworkCapabilities.NET_CAPABILITY_IMS:
            legacyType = ConnectivityManager.TYPE_MOBILE_IMS;
            break;
        case NetworkCapabilities.NET_CAPABILITY_EIMS:
            legacyType = ConnectivityManager.TYPE_MOBILE_EMERGENCY;
            break;
        default:
            loge("error: unsupport capability(" + capability + ")");
        };

        log("ret legacyType: " + legacyType);
        return legacyType;
    }

    private static class DataDispatcherNetworkRequest {
        Network currentNw;
        NetworkRequest nwRequest;
        NetworkCapabilities nwCap;
        NetworkCallback nwCb;
        String apnType = "";

        public DataDispatcherNetworkRequest(NetworkCallback nwCb, String apnType) {
            this.nwCb = nwCb;
            this.apnType = apnType;
        }

        public String toString() {
            return "apnType: " + apnType + ", nwRequest: "
                + nwRequest + ", network: " + currentNw;
        }
    }

    private int convertImsOrEmergencyNetworkType(String apnType) {
        int networkType = ConnectivityManager.TYPE_NONE;
        if (PhoneConstants.APN_TYPE_IMS.equals(apnType)) {
            networkType = ConnectivityManager.TYPE_MOBILE_IMS;
        } else if (PhoneConstants.APN_TYPE_EMERGENCY.equals(apnType)) {
            networkType = ConnectivityManager.TYPE_MOBILE_EMERGENCY;
        } else {
            log("only convert ims/emergency");
        }
        return networkType;
    }

    private static String networkTypeToApnType(int netType) {
        switch(netType) {
            case ConnectivityManager.TYPE_MOBILE:
                return PhoneConstants.APN_TYPE_DEFAULT;  // TODO - use just one of these
            case ConnectivityManager.TYPE_MOBILE_MMS:
                return PhoneConstants.APN_TYPE_MMS;
            case ConnectivityManager.TYPE_MOBILE_SUPL:
                return PhoneConstants.APN_TYPE_SUPL;
            case ConnectivityManager.TYPE_MOBILE_DUN:
                return PhoneConstants.APN_TYPE_DUN;
            case ConnectivityManager.TYPE_MOBILE_HIPRI:
                return PhoneConstants.APN_TYPE_HIPRI;
            case ConnectivityManager.TYPE_MOBILE_FOTA:
                return PhoneConstants.APN_TYPE_FOTA;
            case ConnectivityManager.TYPE_MOBILE_IMS:
                return PhoneConstants.APN_TYPE_IMS;
            case ConnectivityManager.TYPE_MOBILE_CBS:
                return PhoneConstants.APN_TYPE_CBS;
            case ConnectivityManager.TYPE_MOBILE_IA:
                return PhoneConstants.APN_TYPE_IA;
            case ConnectivityManager.TYPE_MOBILE_EMERGENCY:
                return PhoneConstants.APN_TYPE_EMERGENCY;
            default:
                loge("Error mapping networkType " + netType + " to apnType.");
                return null;
        }
    }

    private ImsConfig getImsConfig(int subId) {
        ImsConfig imsConfig = null;
        ImsManager imsManager = ImsManager.getInstance(mContext, subId);

        try {
            imsConfig = imsManager.getConfigInterface();
        } catch (ImsException e) {
            e.printStackTrace();
        }

        return imsConfig;
    }

    private void stopPcscfDiscoveryDhcpThread(String msg) {
        synchronized (this) {
            if (mPcscfDiscoveryDhcpThread != null) {
                log(msg);
                mPcscfDiscoveryDhcpThread.interrupt();
                mPcscfDiscoveryDhcpThread = null;
            }
        }
    }

    private void stopQueryGlobalIpV6Thread(String apnType) {
        try {
            mGlobalIpV6Thread.get(apnType).interrupt();
            log("previous apnType " + apnType + " getGlobalIpV6Addr thread running " +
                                    "stop first!");
            mGlobalIpV6Thread.remove(apnType);
        } catch (NullPointerException e) {
            log("no query global Ipv6 thread is running for apnType: " + apnType);
        }
    }

    private void getIMSGlobalIpAddr(String apnType) {
        log("getIMSGlobalIpAddr, apnType: " + apnType);
        String key = "";
        if (IMS_APN.equals(apnType)) {
            key = "_IMS";
        } else if (EMERGENCY_APN.equals(apnType)) {
            key = "_EIMS";
        }

        log("ip queue: " + mGlobalIPQueue);
        for(String keySet: mGlobalIPQueue.keySet()) {
            if (keySet.contains(key)) {
                VaEvent event = mGlobalIPQueue.get(keySet);
                sendVaEvent(event);
                log("send notify ip queued event to IMCB, key: " + keySet);
                mGlobalIPQueue.remove(keySet);
            }
        }
        log("ip queue: " + mGlobalIPQueue);
    }
    // TODO: VoLTE get global ip address and DHCP
    // Query DHCP
    private void getIMSGlobalIpAddr(String apnType, LinkProperties lp, int phoneId) {
        // TODO: [Notice] maximum 2 address ?? 1 ipv4 and 1 ipv6??
        int cnt = 0;
        for (InetAddress inetAddress : lp.getAddresses()) {
            if (inetAddress instanceof Inet6Address) {
                log("getIMSGlobalIpAddr, ip is IpV6");
                getGlobalIpV6Addr(apnType, lp, phoneId);
            } else if (inetAddress instanceof Inet4Address) {
                log("getIMSGlobalIpAddr, ip is IpV4");
                if (inetAddress.isAnyLocalAddress() == true) {
                    log("getIMSGlobalIpAddr, Using dhcp");
                    DhcpThread dhcpThread = new DhcpThread(apnType, lp.getInterfaceName(), IP_DHCP_V4, phoneId);
                    dhcpThread.start();
                } else {
                    log("getIMSGlobalIpAddr, send to Handler");
                    sendGlobalIPAddrToVa(inetAddress, apnType, lp, phoneId);
                }
            } else {
                loge("getIMSGlobalIpAddr, ip is unknown type, use IpV4 temporary");
                sendGlobalIPAddrToVa(inetAddress, apnType, lp, phoneId);
            }

            cnt++;
        }
        log("getIMSGlobalIpAddr, ip cnt: " + cnt);
    }

    private void sendGlobalIPAddrToVa(InetAddress inetAddress, String apnType, LinkProperties lp
        , int phoneId) {
        Intent intent = new Intent(TelephonyIntents.ACTION_NOTIFY_GLOBAL_IP_ADDR);
        intent.putExtra(TelephonyIntents.EXTRA_GLOBAL_IP_ADDR_KEY, inetAddress);
        intent.putExtra(PhoneConstants.DATA_APN_TYPE_KEY, apnType);
        intent.putExtra(PhoneConstants.DATA_IFACE_NAME_KEY, lp.getInterfaceName());
        intent.putExtra(PhoneConstants.PHONE_KEY, phoneId);

        mContext.sendBroadcast(intent);
        mGlobalIpV6Thread.remove(apnType);
    }


    private static final int[] RA_POLLING_TIMER = {1, 1, 1, 2, 3, 4, 5, 6, 7}; //30 seconds
    private static final String RESULT_RA_FAIL = "RaFail";

    private String getRaResultAddress(String prefix, LinkProperties lp) {
        String address = null;
        for (InetAddress inetAddress : lp.getAddresses()) {
            if (inetAddress instanceof Inet6Address) {
                //only IPv6 need to get RA prefix
                try {
                    byte[] ipBytes = inetAddress.getAddress();
                    byte[] prefixBytes = InetAddress.getByName(prefix).getAddress();
                    for (int j=0; j<8; j++) {
                        //replace first 64 bits if IP address with the prefix
                        ipBytes[j] = prefixBytes[j];
                    }

                    address = InetAddress.getByAddress(ipBytes).getHostAddress();
                    log("getRaResultAddress get address [" + address + "]");
                    break;
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
        }
        return address;
    }

    private void getGlobalIpV6Addr(String apnType, LinkProperties lp, int phoneId) {
        stopQueryGlobalIpV6Thread(apnType);
        GlobalIpV6AddrQueryThread queryThread = new GlobalIpV6AddrQueryThread(apnType, lp, phoneId);
        queryThread.start();
        mGlobalIpV6Thread.put(apnType, queryThread);
    }

    private class GlobalIpV6AddrQueryThread extends Thread {
        String mApnType;
        LinkProperties mLp;
        int mPhoneId;
        boolean mThreadRunning = true;

        public GlobalIpV6AddrQueryThread(String apnType, LinkProperties lp, int phoneId) {
            mApnType = apnType;
            mLp = lp;
            mPhoneId = phoneId;
        }

        private String getRaGlobalIpAddress(String apnType, LinkProperties lp) {
            String address = RESULT_RA_FAIL;
            if (lp == null) {
                loge("getRaGlobalIpAddress but no LinkProperties");
                return address;
            }

            String interfaceName = lp.getInterfaceName();
            if (interfaceName == null) {
                loge("getRaGlobalIpAddress but interface name is null");
                return address;
            }

            // Get Network state (retry 3 times, 0.5/1/1.5 secs)
            NetworkInfo.DetailedState state = getImsOrEmergencyNetworkInfoDetailState(apnType);
            if (state != NetworkInfo.DetailedState.CONNECTED) {
                final int tryCnt = 3;
                int delayMsSec = 500;
                for (int i = 0; i < tryCnt; i++) {
                    delayForSeconds(delayMsSec * (i + 1));
                    state = getImsOrEmergencyNetworkInfoDetailState(apnType);
                    log("network detailed state: " + state);
                    if (state == NetworkInfo.DetailedState.CONNECTED) {
                        break;
                    }
                }
            }

            for (int i = 0, length = RA_POLLING_TIMER.length; i < length; i++) {
                if (state != NetworkInfo.DetailedState.CONNECTED) {
                    loge("getRaGlobalIpAddress but data state is not connected ["
                        + state + "]");
                    break;
                }
                if (!mThreadRunning) {
                    loge("getRaGlobalIpAddress thread stop!!");
                    break;
                }

                // TODO: need to add code for L about using NetLinkTracker [start]
                String prefix = SystemProperties.get("net.ipv6." + interfaceName +
                                                                            ".prefix", "");
                if (prefix != null && prefix.length() > 0) {
                    //some network did not set o-bit but have prefix information in RA,
                    //so check prefix first
                    log("getRaGlobalIpAddress get prefix [" + prefix + "]");
                    return getRaResultAddress(prefix, lp);
                } else {
                    int raResult = NetworkUtils.getRaFlags(interfaceName);
                    // //0: No RA result
                    //1: check system properties "net.ipv6.ccemniX.prefix" for IP prefix
                    //2: need to do DHCPv6 for IP address
                    //4: receive RA but no M or O flag, handle as case "1"
                    //negative: error
                    log("getRaGlobalIpAddress get raResult ["
                        + raResult + "]");

                    if (raResult == 1 || raResult == 4) {
                        prefix = SystemProperties.get("net.ipv6." + interfaceName +
                                                                        ".prefix", "");
                        log("getRaGlobalIpAddress get prefix after RA result ["
                            + prefix + "]");
                        return getRaResultAddress(prefix, lp);
                    } else if (raResult == 2) {
                        //return null to trigger DHCP
                        log("getRaGlobalIpAddress need to do DHCP, return null");
                        return null;
                    } else {
                        //not 1 or 2, keep polling
                        log("getRaGlobalIpAddress keep polling [" + raResult + "]");
                    }
                }

                synchronized (this) {
                    try {
                        long waitTime = RA_POLLING_TIMER[i] * 1000;
                        log("[" + i + "] getRaGlobalIpAddress no RA result found, wait for " +
                            waitTime + " seconds");
                        wait(waitTime);
                    } catch (InterruptedException e) {
                        log("thread interrupted!!");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return address;
        }

        @Override
        public void interrupt() {
            mThreadRunning = false;
            super.interrupt();
        }

        @Override
        public void run() {
             synchronized (GlobalIpV6AddrQueryThread.class) {
                // only in IMS
                String address = getRaGlobalIpAddress(mApnType, mLp);
                //Checking the string address.. then broadcast to self
                if (address == null) {
                    if (NetworkInfo.DetailedState.CONNECTED ==
                        getImsOrEmergencyNetworkInfoDetailState(mApnType)) {
                        if (mThreadRunning) {
                            DhcpThread dhcpThread = new DhcpThread(mApnType, mLp.getInterfaceName(), IP_DHCP_V6,
                                                                                    mPhoneId);
                            dhcpThread.start();
                        }
                    }
                } else if (RESULT_RA_FAIL.equals(address)) { // Get RA address failed
                    loge("get ra address failed, no broadcast the address back!!");
                } else { // broadcast InetAddress back, get RA address ok
                    if (mThreadRunning) {
                        try {
                            InetAddress inetAddr = Inet6Address.getByName(address);
                            sendGlobalIPAddrToVa(inetAddr, mApnType, mLp, mPhoneId);
                        } catch (UnknownHostException ex) {
                            loge("Inet6Address getByName error");
                            ex.printStackTrace();
                        }
                    }
                }
                if (!mThreadRunning) {
                    loge("GlobalIpV6AddrQueryThread thread stop (do nothing)!!");
                }
            }
        }
    }

    private class DhcpThread extends Thread {
        String mApnType;
        int mIpType;
        String mIntfName;
        DhcpResults mDhcpResult;
        int mPhoneId;

        public DhcpThread(String apnType, String intfName, int ipType, int phoneId) {
            mApnType = apnType;
            mIpType = ipType;
            mIntfName = intfName;
            mPhoneId = phoneId;
        }

        private boolean stopDhcp() {
            boolean bRet = false;
            log("[DhcpThread] stopDhcp");

            switch (mIpType) {
            case IP_DHCP_V4:
                bRet = NetworkUtils.stopDhcp(mIntfName);
                break;

            case IP_DHCP_V6:
                bRet = NetworkUtils.stopDhcpv6(mIntfName);
                break;

            default:
                loge("[DhcpThread] unknown ip type: " + mIpType + " for stopDhcp!!");
                break;
            };

            return bRet;
        }

        private DhcpResults startDhcp() {
            boolean bRet = false;
            DhcpResults dhcpResult = new DhcpResults();

            log("[DhcpThread] startDhcp, ipType: " + mIpType);
            switch (mIpType) {
            case IP_DHCP_V4:
                bRet = NetworkUtils.runDhcp(mIntfName, dhcpResult);
                break;

            case IP_DHCP_V6:
                bRet = NetworkUtils.runDhcpv6(mIntfName, dhcpResult);
                break;

            default:
                loge("[DhcpThread] unknown ip type: " + mIpType + " for startDhcp!!");
                break;
            };

            if(false == bRet) {
                loge("[DhcpThread] startDhcp failed!!");
                dhcpResult = null;
            }

            return dhcpResult;
        }

        @Override
        public void run() {
            log ("[DhcpThread] start, apnType: " + mApnType);
            if(isApnIMSorEmergency(mApnType) && NetworkInfo.DetailedState.CONNECTED !=
                                            getImsOrEmergencyNetworkInfoDetailState(mApnType)) {
                // Stop DHCP first
                if(false == stopDhcp()) {
                    log("[DhcpThread] stopDhcp failed!!");
                }

                // TODO:  if interrupted thread here???? need to handle ???
                // Start DHCP
                mDhcpResult = startDhcp();
                LinkProperties dhcpLp = mDhcpResult.toLinkProperties(mIntfName);
                if (mDhcpResult != null && dhcpLp != null) {
                    Collection<LinkAddress> addresses = dhcpLp.getLinkAddresses();
                    if (addresses != null && addresses.size() > 0) {
                        Object[] lp = addresses.toArray();
                        InetAddress inetAddr = ((LinkAddress)lp[0]).getAddress();
                        sendGlobalIPAddrToVa(inetAddr, mApnType, dhcpLp, mPhoneId);
                    }
                }
            } else {
                loge("[DhcpThread] apn type is not IMS/Emergency, leave DhcpThread!!");
            }
        }
    }

    private void responseDefaultBearerWifiConnActivated(String apnType,
            List<InetAddress> PcscfAddr, List<LinkAddress> addresses) {
        log("responseDefaultBearerWifiConnActivated");
        synchronized (mTransactions) {
            log("into synchro");
            Integer[] transactionKeyArray = getTransactionKeyArray();
            log("transactionKeyArray " + transactionKeyArray);
            for (Integer transactionId : transactionKeyArray) {
                TransactionParam param = getTransaction(transactionId);
                log("param.isEmergency" + param.isEmergency);
                if ((IMS_APN.equals(apnType) && param.isEmergency != true)
                        || (EMERGENCY_APN.equals(apnType) && param.isEmergency == true)) {
                    if (param.requestId == VaConstants.MSG_ID_REQUEST_BEARER_ACTIVATION) {
                        for (Integer transactionIdCheck : transactionKeyArray) {
                            TransactionParam paramCheck = getTransaction(transactionId);                        
                            if (paramCheck.requestId == VaConstants.MSG_ID_REQUEST_BEARER_DEACTIVATION) {
                                log("onAvailable : MSG_ID_REQUEST_BEARER_DEACTIVATION pending");
                                if((IMS_APN.equals(apnType) && !paramCheck.isEmergency) || (EMERGENCY_APN.equals(apnType) &&    
                                    paramCheck.isEmergency)) {
                                    return;
                                }
                            }
                        }


                        ImsAdapter.VaEvent event = new ImsAdapter.VaEvent(
                                param.phoneId,
                                VaConstants.MSG_ID_RESPONSE_BEARER_ACTIVATION,
                                SIZE_DEFAULT_BEARER_RESPONSE);
                        DedicateBearerProperties defaultBearerProp = new DedicateBearerProperties();
                        String[] pcscf = new String[mPcscfAddr.size()];
                        for (int i = 0; i < pcscf.length; i++) {
                            pcscf[i] = transferToPcscf(mPcscfAddr.get(i));
                            log("pcscf[" + i + "]" + pcscf[i]);
                        }
                        log("LinkProperties" + mLink);
                        defaultBearerProp.pcscfInfo = new PcscfInfo(
                                pcscf.length, pcscf);
                        int pdnCnt = 0;
                        int msgType = 0;
                        int ipMask = PDP_ADDR_MASK_NONE;            
                        int pdp_addr_type = PDP_ADDR_TYPE_NONE;

                        for(LinkAddress linkAddr : mAddresses) {
                            InetAddress addr = linkAddr.getAddress();
                            if (addr instanceof Inet6Address) {
                                log("ipv6 type");
                                ipMask |= PDP_ADDR_MASK_IPV6;
                            } else if (addr instanceof Inet4Address) {
                                log("ipv4 type");
                                ipMask |= PDP_ADDR_MASK_IPV4;
                            } else {
                                loge("invalid address type");
                                ipMask |= PDP_ADDR_MASK_IPV4;
                            }
                        }


                switch (ipMask) {
                    case PDP_ADDR_MASK_IPV4v6:
                        pdp_addr_type = PDP_ADDR_TYPE_IPV4v6;
                        break;
                    case PDP_ADDR_MASK_IPV6:
                        pdp_addr_type = PDP_ADDR_TYPE_IPV6;
                        break;
                    case PDP_ADDR_MASK_IPV4:
                        pdp_addr_type = PDP_ADDR_TYPE_IPV4;
                    case PDP_ADDR_MASK_NONE:
                        // skip // error ??? (shouldn't be this)
                    default:
                        // using default ipv4 (shouldn't be this)
                        break;
                };
                        pdnCnt++;
                        log("into if else");
                        event.putByte(param.transactionId);
                        event.putByte(pdnCnt);
                        event.putBytes(new byte[2]); // padding
                        DataDispatcherUtil.writeRatCellInfo(event,
                                ConnectivityManager.TYPE_EPDG, mContext);
                        if (DataDispatcherUtil.DBG)
                            DataDispatcherUtil.dumpPdnAckRsp(event);
                        // write main_context
                        msgType = IMC_CONCATENATED_MSG_TYPE_ACTIVATION;
                        DataDispatcherUtil.writeWiFiBearerProperties(event,
                                msgType, pdp_addr_type, defaultBearerProp);
                        removeTransaction(transactionId);

                        log("Wifi bearer transaction id =" + transactionId
                                + "Pdn count =" + pdnCnt + "pdp_addr_type"
                                + pdp_addr_type);
                        log("Bearer activation for WiFi rat sendd to IMCB");
                        sendVaEvent(event);
                        sendGlobalIpAddr(IMS_APN, param.phoneId);
                        isWifiConnection = true;
                        mPdnActive = true;
                        //set default bearer
                        setMappedBearer(getWiFicid());
                    } else {
                        log("responseDefaultBearerWifiConnActivated received unhandled state change event ["
                                + transactionId + " " + param.requestId + "]");
                    }
                }
            }
        }

    }


    private boolean isUniqueLocal(InetAddress address) {
            byte[] bytes = address.getAddress();
            boolean bRet = ((bytes[0] == (byte)0xfc) || (bytes[0] == (byte)0xfd));
            log("isUniqueLocal: " + bRet + "byte[0]:" + bytes[0]);
            return bRet;
    }

    private  String transferToPcscf(InetAddress addr) {
        StringBuffer pcscf = null;

        if (addr instanceof Inet4Address){
            pcscf = new StringBuffer(addr.getHostAddress());
            log("IPv4 format : pcscf = "+pcscf);
        } else if (addr instanceof Inet6Address){
            pcscf = new StringBuffer();
            byte[] rawData = addr.getAddress();

            for ( int i = 0; i < rawData.length; i++) {
                int value = rawData[i];
                if (value < 0) value += 0x100;
                pcscf.append(value);
                if (i != rawData.length - 1) {
                    pcscf.append(".");
                }
            }
        }
        log("P-CSCF:" + pcscf);
        return pcscf.toString();
    }

    public void sendGlobalIpAddr(String apnType, int phoneId) {
        InetAddress adrv6;
        InetAddress adrv4;
        String intfName=mLink.getInterfaceName();
        byte[] ipAddrByteArray = null;
        int msgId = -1;
        log("Send global IP for wifi pdn");
        for (LinkAddress address : mAddresses) {
            if (address.getAddress() instanceof Inet4Address) {
                adrv4 = ((LinkAddress)mAddresses.get(0)).getAddress();
                msgId = VaConstants.MSG_ID_NOTIFY_IPV4_GLOBAL_ADDR;
                try {
                    Network network = getConnectivityManager().getNetworkForType(
                             convertImsOrEmergencyNetworkType(apnType));
                    ipAddrByteArray = adrv4.getAddress();
                             ImsAdapter.VaEvent event = mDataDispatcherUtil
                                .composeGlobalIPAddrVaEvent(msgId, getWiFicid(),
                        network.netId, ipAddrByteArray, intfName, phoneId);
                              sendVaEvent(event);
                } catch (NullPointerException e) {
                    loge("null pointer exception!!");
                    e.printStackTrace();
                }
            } else if ((address.getAddress() instanceof Inet6Address
                    && address.isGlobalPreferred()) || isUniqueLocal(address.getAddress())){
                adrv6 = ((LinkAddress) address).getAddress();
                msgId = VaConstants.MSG_ID_NOTIFY_IPV6_GLOBAL_ADDR;
                try {
                    Network network = getConnectivityManager().getNetworkForType(
                    convertImsOrEmergencyNetworkType(apnType));
                    ipAddrByteArray = adrv6.getAddress();
                    ImsAdapter.VaEvent event = mDataDispatcherUtil.composeGlobalIPAddrVaEvent(msgId, getWiFicid(),
                            network.netId, ipAddrByteArray, intfName, phoneId);
                    sendVaEvent(event);
                } catch (NullPointerException e) {
                    loge("null pointer exception!!");
                    e.printStackTrace();
                }
                return;
          }
        }
        return ;
    }


    public static int getWiFicid(){
        if(INVALID_CID == sEmergencyCid){
            log("getWiFicid return 1");
            return 1;
        }else {
            if(sEmergencyCid == 1) {
                log("getWiFicid return 2");        
                return 2;
            } else {
                return 1;
            }
        }
    }

/*For handling onLost without onAvailable*/
    private void retryPdnActivation() {
        log("retryPdnActivation");
        Integer[] transactionKeyArray = getTransactionKeyArray();
        for (Integer transactionId : transactionKeyArray) {
            TransactionParam actParam = getTransaction(transactionId);
            /*Check if a bearer activation request is pending*/
            if (VaConstants.MSG_ID_REQUEST_BEARER_ACTIVATION
                == actParam.requestId) {
                loge("retryPdnActivation abort activation "
                    + "request");
                releaseNwRequest(PhoneConstants.APN_TYPE_IMS);
                requestNwRequest(PhoneConstants.APN_TYPE_IMS, actParam.phoneId);
                log("retryPdnActivation : PhoneId =" + actParam.phoneId);
            }
        }
    }

    public void sendVaEventIfPending(){
        log("sendVaEventIfPending mDelayedEvent ="+mDelayedEvent+ "mDelayedEventAct ="+mDelayedEventAct);
        if(mDelayedEvent != null){
            if(mDelayedEventAct == null){
                sendVaEvent(mDelayedEvent);
            }
        }else if(mDelayedEventAct != null){
            sendVaEvent(mDelayedEventAct);
            if(mDelayedEventModified != null) {
               sendVaEvent(mDelayedEventModified); 
            }
        }
        mDelayedEvent = null;
        mDelayedEventAct =null;
        mDelayedEventModified = null;
    }

    public void setMappedBearer(int cid){
        log("setMappedBearer cid ="+cid);
        mCid = cid;
    }

    public int getMappedBearer(){
        log("getMappedBearer mCid ="+mCid);
        return mCid;
    }
    public void releaseRequest(){
        notifyWifiDataConnectionDeactivated(getWiFicid(),FAILCAUSE_UNKNOWN);
        releaseNwRequest(PhoneConstants.APN_TYPE_IMS);
    }
}
