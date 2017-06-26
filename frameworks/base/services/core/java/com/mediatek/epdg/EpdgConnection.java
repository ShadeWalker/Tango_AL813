package com.mediatek.epdg;


import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.IConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkUtils;
import android.net.RouteInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Slog;

import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.util.HexDump;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import com.mediatek.internal.telephony.ITelephonyEx;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Track the state of EPDG connection. This is done by
 * communicating with native daemon to get
 * the state of data connectivity changes.
 *
 * {@hide}
 */

class EpdgConnection extends StateMachine implements EpdgCallback {

    protected static final String ALL_MATCH_APN = "*";

    private static final String TAG = "EpdgConnection";
    private static final String NETWORKT_TYPE = "Wi-Fi";
    private static final String TELEPHONY_CONTACT = "content://telephony/carriers";
    private static final String WO_DPD_SYSNAME = "persist.net.wo.dpd_timer";
    private static final Uri CONTENT_URI =  Uri.parse(TELEPHONY_CONTACT);

    private static final String PROPERTIES_DIR = Environment.getDataDirectory() + "/misc/epdg/";

    private static final boolean DBG = true;
    private static final byte OK_AKA_RESPONSE = (byte) 0xDB;
    private static final byte SYNC_FAIL_RESPONSE = (byte) 0xDC;

    private static final int  MAX_RETRY_COUNT = 3;
    private static final int  RETRY_INTERVAL = 30 * 1000;
    private static final int  DEFAULT_DPD_INTERVAL = 120;
    private static final int  MAX_HANDOVER_GUARD_TIMER = 20 * 1000;
    private static final int  MAX_CONNECTION_GUARD_TIMER = 90 * 1000;

    private AtomicBoolean mPrivateDnsRouteSet = new AtomicBoolean(false);
    private AtomicBoolean mDefaultRouteSet = new AtomicBoolean(false);
    private AlarmManager mAlarmManager;
    private Context mContext;
    private EpdgConnector mEpdgConnector;
    private EpdgConfig mEpdgConfig;
    private Handler mCsHandler;
    private IConnectivityManager mConnService;
    private ITelephonyEx       mItelEx;
    private LinkProperties mLinkProperties;
    private NetworkInfo mNetworkInfo;
    private NetworkAgent mNetworkAgent;
    private NetworkCapabilities mNetworkCapabilities;
    private PendingIntent mEpdgPendingIntent;
    private Properties mProperties;
    private String mApnTypeName;
    private String mOuterInterface;
    private TelephonyManager mTelephonyManager;

    private final Object mRefCountLock = new Object();

    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

    private int mRefCount = 0;
    private int mApnType;
    private int mReasonCode = EpdgConstants.EPDG_RESPONSE_OK;
    private int mRetryCount = 0;
    private int mDpdSeconds = 0;

    /* The base for wifi message types */
    static final int BASE = 0x00;

    /* Reconnect to a network */
    static final int CMD_RECONNECT                        = BASE + 1;
    /* Disconnect from a network */
    static final int CMD_DISCONNECT                       = BASE + 2;

    /* Event for EPDG connected */
    static final int EVENT_CONNECTED_DONE                  = BASE + 0x10;
    /* Event for EPDG disconnected */
    static final int EVENT_DISCONNECTED_DONE               = BASE + 0x11;
    /* Event for EPDG connect failure */
    static final int EVENT_CONNECTED_FAILURE               = BASE + 0x12;
    /* Event for Wi-Fi connection is connected */
    static final int EVENT_WIFI_CONNECTED                  = BASE + 0x13;
    /* Event for Wi-Fi connection is disconnected */
    static final int EVENT_WIFI_DISCONNECTED               = BASE + 0x14;
    /* Event for guard timer expired for connect */
    static final int EVENT_CONNECT_EXPIRED                 = BASE + 0x15;


    /**
     * Create a new EpdgDataStateTracker.
     * @param netType the ConnectivityManager network type.
     * @param tag the name of this network.
     */
    EpdgConnection(int netType, EpdgConnector epdgConnector,
                Handler target, String iface) {
        super("EpdgConnection" + netType, target);
        mApnTypeName = getNetworkTypeName(netType);

        /* Configure state-machine parameters */
        setLogRecSize(100);
        setLogOnlyTransitions(true);

        /* Construct the states for state-machine */
        addState(mDefaultState);
        addState(mInactiveState, mDefaultState);
        addState(mActivatingState, mDefaultState);
        addState(mRetryingState, mDefaultState);
        addState(mActiveState, mDefaultState);
        addState(mDisconnectingState, mDefaultState);
        //addState(mDisconnectingErrorCreatingConnection, mDefaultState);
        setInitialState(mInactiveState);

        mApnType = netType;
        mOuterInterface = iface;;

        mNetworkInfo = new NetworkInfo(ConnectivityManager.TYPE_EPDG,
                        netType, NETWORKT_TYPE, mApnTypeName);

        mLinkProperties = new LinkProperties();
        mNetworkCapabilities = makeNetworkCapabilities(mApnType);
        mNetworkInfo.setIsAvailable(false);
        mEpdgConnector = epdgConnector;
    }

    private EpdgConfig getEpdgConfig() {
        EpdgConfig config = new EpdgConfig();

        config.edpgServerAddress = mProperties.getProperty(
                EpdgConstants.EPDG_SERVER, EpdgConstants.EMPTY_DATA);

        config.apnName = ALL_MATCH_APN;

        try {
            config.authType = Integer.parseInt(mProperties.getProperty(
                EpdgConstants.EPDG_AUTH, "" + EpdgConfig.AKA_AUTH_TYPE));
            config.simIndex = Integer.parseInt(mProperties.getProperty(
                EpdgConstants.EPDG_SIM_INDEX, "1"));
            config.mobilityProtocol = Integer.parseInt(mProperties.getProperty(
                EpdgConstants.EPDG_MOBILITY_PROTOCOL, "" + EpdgConfig.DSMIPV6_PROTOCOL));
        } catch (NumberFormatException e) {
            log("error" + e);
            config.authType = EpdgConfig.AKA_AUTH_TYPE;
            config.simIndex = 1;
            config.mobilityProtocol = EpdgConfig.DSMIPV6_PROTOCOL;
        }

        config.certPath = mProperties.getProperty(
                EpdgConstants.EPDG_CERT_PATH, EpdgConstants.EMPTY_DATA);
        config.ikeaAlgo = mProperties.getProperty(
                EpdgConstants.EPDG_IKEA_ALGO, EpdgConstants.EMPTY_DATA);
        config.espAlgo = mProperties.getProperty(
                EpdgConstants.EPDG_ESP_ALGO, EpdgConstants.EMPTY_DATA);

        log("EPDG config:" + config);
        return config;
    }

    /**
     * Begin monitoring data connectivity.
     *
     * @param context is the current Android context
     * @param target is the Hander to which to return the events.
     */
    void startMonitoring(Context context, Handler target) {
        mCsHandler = target;
        mContext = context;

        mTelephonyManager = (TelephonyManager) mContext.getSystemService(
                                Context.TELEPHONY_SERVICE);

        Intent intent = new Intent(EpdgTracker.ACTION_EPDG_DPD);
        mEpdgPendingIntent = PendingIntent.getBroadcast(mContext, 0,
                                        intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);

        //Handle EPDG configuration.
        mProperties = new Properties();
        try {
            File file = new File(PROPERTIES_DIR + mApnTypeName);
            FileInputStream stream = new FileInputStream(file);
            mProperties.load(stream);
            stream.close();
        } catch (IOException e) {
            Slog.e(TAG, "Could not open configuration file: " + PROPERTIES_DIR);
            File dir = new File(PROPERTIES_DIR);
            if (!dir.exists()) {
                Slog.d(TAG, "Make epdg directory");
                dir.mkdirs();
            }
        }

        mEpdgConfig = getEpdgConfig();

        mNetworkInfo.setIsAvailable(true);
        mEpdgConnector.registerEpdgCallback(mApnType, this);

        //start EpdgConnection
        start();
    }

    private void updateDefaultSubId() {
        //Support single SIM project.
        int[] subIds = SubscriptionManager.getSubId(0);
        if ((subIds != null) && (subIds.length != 0)) {
            mSubId = subIds[0];
        }
    }

    private String getNetworkTypeName(int netType) {
        switch(netType) {
            case EpdgManager.TYPE_FAST:
                return "FAST";
            case EpdgManager.TYPE_IMS:
                return "IMS";
            case EpdgManager.TYPE_NET:
                return "NET";
            default:
                break;
        }
        return "";
    }

    int getApnType() {
        return mApnType;
    }

    void incRefCount() {
        synchronized (mRefCountLock) {
            //Debug purpose
            boolean forRun = SystemProperties.getBoolean("epdg.force.run", false);

            log("mRefCountLock(+):" + mRefCount);

            if (mRefCount++ == 0) {
                reconnect();
            } else if (forRun) {
                reconnect();
            }
        }
    }

    void decRefCount() {
        synchronized (mRefCountLock) {
            log("mRefCountLock(-)" + mRefCount);

            if (mRefCount-- == 1) {
                teardown();
            }
        }
    }

    private void getApnName(String apnTypeName) {
        String operator = mTelephonyManager.getSimOperator(mSubId);

        if (operator == null) {
            loge("No operator info");
            return;
        }

        String selection = "numeric = '" + operator + "' and carrier_enabled = 1";
        log("SQL:" + selection);

        Cursor cursor = mContext.getContentResolver().query(
                        CONTENT_URI, null, selection, null, "name ASC");

        if (cursor != null) {
            if (cursor.getCount() > 0) {
                if (cursor.moveToFirst()) {
                    do {
                        String[] types = parseTypes(cursor.getString(
                                    cursor.getColumnIndexOrThrow("type")));

                        if (canHandleType(apnTypeName, types)) {
                            mEpdgConfig.apnName = cursor.getString(
                                    cursor.getColumnIndexOrThrow("apn"));

                            if (EpdgManager.TYPE_IMS != mApnType
                                && mTelephonyManager.isNetworkRoaming(mSubId)) {
                                mEpdgConfig.accessIpType = EpdgConfig.IPV4;
                            } else {
                                mEpdgConfig.accessIpType = parseIpProtocol(
                                    cursor.getString(cursor.getColumnIndexOrThrow("protocol")));
                            }
                            break;
                        }
                    } while (cursor.moveToNext());
                }
            }

            cursor.close();
        }

        if ((mEpdgConfig.apnName == null || mEpdgConfig.apnName.equals(ALL_MATCH_APN))
                    && mApnType == EpdgManager.TYPE_IMS) {
            loge("No APN info in database; Use default setting: ims");
            mEpdgConfig.apnName = "ims";
            mEpdgConfig.accessIpType = EpdgConfig.IPV6;
        } else if (mEpdgConfig.apnName == null || mEpdgConfig.apnName.equals(ALL_MATCH_APN)) {
            loge("No APN info in database; Use default setting: tmus");
            mEpdgConfig.apnName = "tmus";
            mEpdgConfig.accessIpType = EpdgConfig.IPV6;
        }
    }

    private String[] parseTypes(String types) {
        String[] result;

        if (types == null || types.equals("")) {
            result = new String[1];
            result[0] = "";
        } else {
            result = types.split(",");
        }

        return result;
    }

    private int parseIpProtocol(String protocol) {
        int protocolId = EpdgConfig.IPV4;

        if (protocol.equalsIgnoreCase("IPV4V6")) {
            protocolId = EpdgConfig.IPV4V6;
        } else if (protocol.equalsIgnoreCase("IPV6")) {
            protocolId = EpdgConfig.IPV6;
        }

        return protocolId;
    }

    private boolean canHandleType(String type, String[] types) {
        for (String t : types) {
            if (t.equalsIgnoreCase(type)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Disable connectivity to a network.
     * TODO: do away with return value after making MobileDataStateTracker async
     */
    boolean teardown() {
        log("teardown");
        sendMessage(CMD_DISCONNECT);
        return true;
    }

    /**
     * Re-enable connectivity to a network after a {@link #teardown()}.
     */
    boolean reconnect() {
        log("reconnect");
        sendMessage(CMD_RECONNECT);
        return true;
    }

    private boolean connect() {
        String connectCmd = "";

        if (!mEpdgConfig.isHandOver) {
            connectCmd = EpdgCommand.getCommandByType(
                    mEpdgConfig, EpdgConstants.ATTACH_COMMMAND);
        } else {
            if (mApnType == EpdgManager.TYPE_IMS || 
                mApnType == EpdgManager.TYPE_FAST) {
                connectCmd = EpdgCommand.getCommandByType(
                    mEpdgConfig, EpdgConstants.HANDOVER_COMMMAND);
                //Enable handvoer flag when sending handover command.
                SystemProperties.set("net.handover.flag", "true");
            } else {
                updateState(DetailedState.DISCONNECTED, String.valueOf(mReasonCode));
                loge("Don't retry handover");
                return false;
            }
        }
        log("connectCmd:" + connectCmd);

        int i = 0;
        while (i <= MAX_RETRY_COUNT * 2) {
            try {
                mEpdgConnector.sendEpdgCommand(connectCmd);
                break;
            } catch (SocketException se) {
                se.printStackTrace();
                i++;
                try {
                    Thread.sleep(1000 * 5); //Sleep for 5 seconds for power up timing issue.
                } catch (InterruptedException ie) {

                }
            }
        }

        return true;
    }

    private void disconnect() {
        String disconnectCmd = EpdgCommand.getCommandByType(
                mEpdgConfig, EpdgConstants.DETACH_COMMMAND);
        log("disconnect cmd:" + disconnectCmd);

        try {
            mEpdgConnector.sendEpdgCommand(disconnectCmd);
        } catch (SocketException se) {
            se.printStackTrace();
        }
    }

    private boolean prepareConfig() {
        //woattach=<APN>, <IMSI>,<MNC>,<MCC>,<WiFi Interface>,
        //<WiFi IPv6 Addr>,<WiFi IPv4 Addr>,<ePDG IPv6 Addr>,<ePDG IPv4 Addr>,
        //<Tunnel Access Type>[,<IKEv2 Auth Type>,<SIM Index>,<Mobility Protocol>
        //[,<Certificate Path>[,<IKE Algorithm>,<ESP Algorithm>]]]

        //Update before use.
        updateDefaultSubId();

        String operator = mTelephonyManager.getSimOperator(mSubId);

        if (operator == null || operator.length() == 0) {
            if (TelephonyManager.SIM_STATE_ABSENT ==
                            SubscriptionManager.getSimStateForSubscriber(mSubId)) {
                loge("No sim is inserted");
                mReasonCode = -1;
                updateState(DetailedState.DISCONNECTED, "No SIM");
                return false;
            }
            loge("sim is not ready: " + mRetryCount);
            if (mRetryCount > MAX_RETRY_COUNT) {
                loge("Failure to establish ePDG due to SIM is not ready");
                mReasonCode = -1;
                updateState(DetailedState.DISCONNECTED, "SIM Not Ready");
                mRetryCount = 0;
                return false;
            } else if (mRetryCount == 0) {
                updateState(DetailedState.CONNECTING, "SIM Not Ready");
            }
            sendMessageDelayed(CMD_RECONNECT, RETRY_INTERVAL);
            mRetryCount++;
            return false;
        }

        mEpdgConfig.mcc = operator.substring(0, 3);
        mEpdgConfig.mnc = operator.substring(3);
        getApnName(mApnTypeName);
        mEpdgConfig.imsi = mTelephonyManager.getSubscriberId(mSubId);
        //[ToDo] Get Mobile IP address

        if (mEpdgConfig.edpgServerAddress.length() == 0) {
            mEpdgConfig.edpgServerAddress = getAutoEpdgServer();
        }

        try {
            if (mConnService == null) {
                mConnService = IConnectivityManager.Stub.asInterface(
                    ServiceManager.getService(Context.CONNECTIVITY_SERVICE));
            }

            LinkProperties linkProperty = mConnService.getLinkPropertiesForType(
                    ConnectivityManager.TYPE_WIFI);

            if (linkProperty == null) {
                loge("The link property is null");
                return false;
            }

            for (LinkAddress l : linkProperty.getLinkAddresses()) {
                if (l.getAddress() instanceof Inet4Address) {
                    mEpdgConfig.wifiIpv4Address = l.getAddress();
                    break;
                }
            }

            for (LinkAddress l : linkProperty.getLinkAddresses()) {
                if (l.getAddress() instanceof Inet6Address &&
                    !l.getAddress().isLinkLocalAddress()) {
                    mEpdgConfig.wifiIpv6Address = l.getAddress();
                    break;
                }
            }

            if (EpdgManager.TYPE_IMS == mApnType || EpdgManager.TYPE_FAST == mApnType) {
                int nwType = (mApnType == EpdgManager.TYPE_IMS)
                    ? ConnectivityManager.TYPE_MOBILE_IMS : ConnectivityManager.TYPE_MOBILE_MMS;

                NetworkInfo nwInfo = mConnService.getNetworkInfo(nwType);

                if (nwInfo == null) {
                    loge("The network info is null");
                    return false;
                }

                mEpdgConfig.isHandOver = false;
                if (nwInfo != null && nwInfo.isConnected()) {
                    LinkProperties mobileLinkProperty =
                    mConnService.getLinkPropertiesForType(nwType);

                    if (mobileLinkProperty == null) {
                        loge("The mobile link property is null");
                        return false;
                    }

                    mEpdgConfig.isHandOver = true;

                    for (LinkAddress l : mobileLinkProperty.getLinkAddresses()) {
                        if (l.getAddress() instanceof Inet4Address) {
                            mEpdgConfig.epdgIpv4Address = l.getAddress();
                            break;
                        }
                    }

                    boolean isLinkLocal = false;
                    InetAddress linkLocalAddress = null;
                    for (LinkAddress l : mobileLinkProperty.getLinkAddresses()) {
                        if (!(l.getAddress() instanceof Inet6Address)) {
                            continue;
                        }
                        if (!l.getAddress().isLinkLocalAddress()) {
                            mEpdgConfig.epdgIpv6Address = l.getAddress();
                        } else {
                            linkLocalAddress = l.getAddress();
                            isLinkLocal = true;
                        }
                        break;
                    }

                    String interfaceName = mobileLinkProperty.getInterfaceName();
                    if (isLinkLocal && interfaceName != null) {
                        log("Check interface name" + interfaceName);
                        NetworkInterface nwInterface = null;
                        try {
                            nwInterface = NetworkInterface.getByName(
                                    interfaceName);
                        } catch (SocketException se) {
                            se.printStackTrace();
                        }
                        if (nwInterface != null) {
                            Enumeration<InetAddress> inetAddresses =
                                                nwInterface.getInetAddresses();
                            for (InetAddress inetAddress : Collections.list(inetAddresses)) {
                                if (inetAddress instanceof Inet6Address &&
                                    !inetAddress.isLinkLocalAddress()) {
                                        log("found:" + inetAddress);
                                        byte[] b1 = inetAddress.getAddress();
                                        byte[] b2 = linkLocalAddress.getAddress();
                                        boolean isMatch = true;
                                        for (int j = 8; j < b1.length; j++) {
                                            log("data:" + b1[j] + "/" + b2[j]);
                                            if (b1[j] != b2[j]) {
                                                isMatch = false;
                                                break;
                                            }
                                        }
                                        if (isMatch) {
                                            log("found done");
                                            mEpdgConfig.epdgIpv6Address = inetAddress;
                                            break;
                                        }
                                }
                            }
                        }
                    }
                }
            }
        } catch (RemoteException re) {
            re.printStackTrace();
        }

        mDpdSeconds = SystemProperties.getInt(WO_DPD_SYSNAME, DEFAULT_DPD_INTERVAL);

        //Add specifier.
        mNetworkCapabilities.setNetworkSpecifier(String.valueOf(mSubId));
        return true;
    }

    private String getAutoEpdgServer() {
        //String plmn = mTelephonyManager.getNetworkOperator();
        String plmn = null;  // TBD: Check operator preferred list.

        if (plmn == null || plmn.length() == 0) {
            plmn = mTelephonyManager.getSimOperator(mSubId);
        }

        String mcc = plmn.substring(0, 3);
        String mnc = plmn.substring(3);

        if (mnc.length() == 2) {
            mnc = "0" + mnc;
        }

        String addr = String.format("epdg.epc.mnc%s.mcc%s.pub.3gppnetwork.org", mnc, mcc);
        log("EPDG Server:" + addr);
        return addr;
    }

    /**
     * Update current state, dispaching event to listeners.
     */
    private void updateState(DetailedState detailedState, String reason) {
        log("setting state=" + detailedState + ":"
                + mNetworkInfo.getDetailedState() + ", reason=" + reason);

        if (detailedState != mNetworkInfo.getDetailedState()) {
            mNetworkInfo.setDetailedState(detailedState, reason, null);

            if (detailedState == DetailedState.CONNECTING) {
                if (mNetworkAgent == null) {
                    mNetworkAgent = new EpdgNetworkAgent(mCsHandler.getLooper(), mContext,
                        "EpdgNetworkAgent", mNetworkInfo,
                        mNetworkCapabilities, mLinkProperties, EpdgConstants.NETWORK_SCORE);
                }
                mNetworkAgent.sendNetworkInfo(mNetworkInfo);
            } else if (detailedState == DetailedState.CONNECTED) {
                mNetworkAgent.sendLinkProperties(mLinkProperties);
                mNetworkAgent.sendNetworkInfo(mNetworkInfo);
            } else if (detailedState == DetailedState.DISCONNECTED) {
                log("send disconnected");
                if (mNetworkAgent == null) {
                    mNetworkAgent = new EpdgNetworkAgent(mCsHandler.getLooper(), mContext,
                        "EpdgNetworkAgent", mNetworkInfo,
                        mNetworkCapabilities, mLinkProperties, EpdgConstants.NETWORK_SCORE);
                }
                mNetworkAgent.sendLinkProperties(mLinkProperties);
                mNetworkAgent.sendNetworkInfo(mNetworkInfo);
                mNetworkAgent = null;
            } else if (detailedState == DetailedState.FAILED) {
                log("failure");
                mNetworkAgent.sendLinkProperties(mLinkProperties);
                mNetworkAgent.sendNetworkInfo(mNetworkInfo);
                mNetworkAgent = null;
            }
        }
    }

    @Override
    public void onEpdgConnected(String apn, int statusCode, String nwInterface,
            String tunnelIpv4, String tunnelIpv6,
            String pcscfIpv4Addr, String pcscfIpv6Addr, String dnsIpv4Addr, String dnsIpv6Addr) {
        log("onEpdgConnected:" + apn + ":" + mEpdgConfig.apnName + ":" + tunnelIpv4);

        if (isMatchApn(apn)) {
            LinkProperties newLp = new LinkProperties();
            newLp.setInterfaceName(nwInterface);

            if (tunnelIpv4.length() > 0) {
                //Assume point to point address
                InetAddress ia = NetworkUtils.numericToInetAddress(tunnelIpv4);
                newLp.addLinkAddress(new LinkAddress(ia, 32));
                newLp.addRoute(new RouteInfo(ia));

            }

            if (tunnelIpv6.length() > 0) {
                //Assume point to point address
                InetAddress ia = NetworkUtils.numericToInetAddress(tunnelIpv6);
                newLp.addLinkAddress(new LinkAddress(ia, 128));
                newLp.addRoute(new RouteInfo(Inet6Address.ANY));
            }

            if (dnsIpv4Addr.length() > 0) {
                newLp.addDnsServer(NetworkUtils.numericToInetAddress(dnsIpv4Addr));
            }

            if (dnsIpv6Addr.length() > 0) {
                newLp.addDnsServer(NetworkUtils.numericToInetAddress(dnsIpv6Addr));
            }

            if (pcscfIpv4Addr.length() > 0) {
                newLp.addPcscfServer(NetworkUtils.numericToInetAddress(pcscfIpv4Addr));
            }

            if (pcscfIpv6Addr.length() > 0) {
                newLp.addPcscfServer(NetworkUtils.numericToInetAddress(pcscfIpv6Addr));
            }

            if (!newLp.equals(mLinkProperties)) {
                mLinkProperties.clear();
                mLinkProperties = newLp;
            }

            log("mLinkProperties:" + mLinkProperties);
            sendMessage(EVENT_CONNECTED_DONE);
        }
    }

    @Override
    public void onEpdgConnectFailed(String apn, int statusCode) {
        if (isMatchApn(apn)) {
            mLinkProperties.clear();
            mReasonCode = statusCode;
            sendMessage(EVENT_CONNECTED_FAILURE);
        }
    }

    @Override
    public void onEpdgDisconnected(String apn) {
        if (isMatchApn(apn)) {
            mLinkProperties.clear();
            mReasonCode = EpdgConstants.EPDG_RESPONSE_OK;
            sendMessage(EVENT_DISCONNECTED_DONE);
        }
    }

    @Override
    public void onEpdgSimAuthenticate(String apn, byte[] rand, byte[] autn) {

        if (isMatchApn(apn)) {
            String cmdStr = "";
            byte[] res = null;

            try {
                mItelEx = ITelephonyEx.Stub.asInterface(
                        ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));

                byte[] response = mItelEx.simAkaAuthentication(
                        mEpdgConfig.simIndex - 1, UiccController.APP_FAM_IMS, rand, autn);

                if (response == null) {
                    Slog.e(TAG, "Can't get SIM Response");
                    cmdStr = String.format("EAUTH:%d,%d", 0x98, 0x62);
                    mEpdgConnector.sendSimCommand(cmdStr);
                    return;
                }

                int sw1 = 0x90;
                int sw2 = 0x00;
                Slog.d(TAG, "Process auth");

                if (response[0] == OK_AKA_RESPONSE || response[0] == SYNC_FAIL_RESPONSE) {
                    int resLen = response.length - 2;
                    res = new byte[resLen];
                    res = Arrays.copyOfRange(response, 0, resLen);

                    if (response.length >=  resLen + 2) {
                        sw1 = getIntFromByte(response[resLen]);
                        sw2 = getIntFromByte(response[1 + resLen]);
                    }

                    cmdStr = String.format(
                        "EAUTH:%d,%d,\"%s\"", 144, 0, HexDump.toHexString(res));
                    mEpdgConnector.sendSimCommand(cmdStr);
                } else {
                    if (response.length == 2) {
                        sw1 = getIntFromByte(response[0]);
                        sw2 = getIntFromByte(response[1]);
                        cmdStr = String.format("EAUTH:%d,%d", sw1, sw2);
                        mEpdgConnector.sendSimCommand(cmdStr);
                    } else {
                        int resLen = response.length;
                        if (resLen > 2) {
                            sw1 = getIntFromByte(response[resLen-2]);
                            sw2 = getIntFromByte(response[resLen-1]);
                            res = new byte[resLen];
                            res = Arrays.copyOfRange(response, 0, resLen - 2);
                            cmdStr = String.format(
                            "EAUTH:%d,%d,\"%s\"", sw1, sw2, HexDump.toHexString(res));
                            mEpdgConnector.sendSimCommand(cmdStr);
                        }
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isMatchApn(String apn) {
        return (apn.equalsIgnoreCase(mEpdgConfig.apnName) || apn.equalsIgnoreCase(ALL_MATCH_APN));
    }

    private int getIntFromByte(byte b) {
        int ret = 0;
        ret = b;

        if (b < 0) {
            ret = 0xFF + 1 + b;
        }

        return ret;
    }

    private NetworkCapabilities makeNetworkCapabilities(int apnType) {
        NetworkCapabilities result = new NetworkCapabilities();
        result.addTransportType(NetworkCapabilities.TRANSPORT_EPDG);

        switch (apnType) {
            case EpdgManager.TYPE_IMS:
                result.addCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
                break;
            case EpdgManager.TYPE_FAST:
                result.addCapability(NetworkCapabilities.NET_CAPABILITY_MMS);
                result.addCapability(NetworkCapabilities.NET_CAPABILITY_SUPL);
                result.addCapability(NetworkCapabilities.NET_CAPABILITY_XCAP);
                result.addCapability(NetworkCapabilities.NET_CAPABILITY_RCS);
                break;
            case EpdgManager.TYPE_NET:
                break;
            default:
                Slog.d(TAG, "Reserved:" + apnType);
                break;
        }

        return result;
    }

    /**
     * NetworkAgent for EPDG connection.
     *
     */
    private class EpdgNetworkAgent extends NetworkAgent {

        EpdgNetworkAgent(Looper l, Context c, String tag, NetworkInfo ni,
                                NetworkCapabilities nc, LinkProperties lp, int score) {
            super(l, c, tag, ni, nc, lp, score);
        }

        protected void unwanted() {
            log("unwanted in EpdgNetworkAgent");
            EpdgConnection.this.sendMessage(CMD_DISCONNECT);
        }
    }

    EpdgConfig getConfiguration() {
        return mEpdgConfig;
    }

    int getReasonCode() {
        return mReasonCode;
    }

    void setEpdgDpdAlarm() {
        if (EpdgManager.TYPE_IMS == mApnType) {
            mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + mDpdSeconds * 1000, mEpdgPendingIntent);
        }
    }

    /**
     * Notify EpdgConnection that the Wi-Fi is connected.
     *
     */
    void notifyWifiConnected() {
        log("Prepare to connect:" + mRefCount);
        if (mRefCount != 0) {
            sendMessage(EVENT_WIFI_CONNECTED);
        }
    }

    /**
     * Notify EpdgConnection that the Wi-Fi is disconnected.
     *
     */
    void notifyWifiDisconnected() {
        log("Prepare to disconnect:" + mRefCount);
        if (mRefCount != 0) {
            sendMessage(EVENT_WIFI_DISCONNECTED);
        }
    }

    void setConfiguration(EpdgConfig newConfig) {
        mEpdgConfig.edpgServerAddress = newConfig.edpgServerAddress;
        mEpdgConfig.authType = newConfig.authType;
        mEpdgConfig.simIndex = newConfig.simIndex;
        mEpdgConfig.mobilityProtocol = newConfig.mobilityProtocol;
        mEpdgConfig.certPath = newConfig.certPath;
        mEpdgConfig.ikeaAlgo = newConfig.ikeaAlgo;
        mEpdgConfig.espAlgo = newConfig.espAlgo;

        mProperties.setProperty(EpdgConstants.EPDG_SERVER, mEpdgConfig.edpgServerAddress);
        mProperties.setProperty(EpdgConstants.EPDG_AUTH, "" + mEpdgConfig.authType);
        mProperties.setProperty(EpdgConstants.EPDG_SIM_INDEX, "" + mEpdgConfig.simIndex);
        mProperties.setProperty(EpdgConstants.EPDG_MOBILITY_PROTOCOL,
                                "" + mEpdgConfig.mobilityProtocol);
        mProperties.setProperty(EpdgConstants.EPDG_CERT_PATH, mEpdgConfig.certPath);
        mProperties.setProperty(EpdgConstants.EPDG_IKEA_ALGO, mEpdgConfig.ikeaAlgo);
        mProperties.setProperty(EpdgConstants.EPDG_ESP_ALGO, mEpdgConfig.espAlgo);

        try {
            log("commit EPDG config");
            File file = new File(PROPERTIES_DIR + mApnTypeName);
            FileOutputStream stream = new FileOutputStream(file);
            mProperties.save(stream, mApnTypeName);
            stream.close();
        } catch (IOException e) {
            log("error:" + e);
        }
    }

    /**
     * The parent state for all other states.
     */
    private class EpdgDefaultState extends State {
        @Override
        public void enter() {
            if (DBG) {
                log("EpdgDefaultState: enter");
            }

        }
        @Override
        public void exit() {
            if (DBG) {
                log("EpdgDefaultState: exit");
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            boolean retVal = HANDLED;

            if (DBG) {
                log("DcDefault msg=" + msg.what);
            }
            /*
            switch (msg.what) {
                default:
                    if (DBG) {
                        log("DcDefaultState: shouldn't happen but ignore msg.what="
                                + getWhatToString(msg.what));
                    }
                    break;
            }
            */
            return retVal;
        }
    }
    private EpdgDefaultState mDefaultState = new EpdgDefaultState();

    /**
     * The state machine is inactive and expects a EVENT_CONNECT.
     */
    private class EpdgInactiveState extends State {
        @Override
        public void enter() {
            mRetryCount = 0;
        }

        @Override
        public void exit() {

        }

        @Override
        public boolean processMessage(Message msg) {
            boolean retVal = HANDLED;

            switch (msg.what) {
                case EVENT_WIFI_CONNECTED:
                case CMD_RECONNECT:
                    if (prepareConfig()) {
                        if (connect()) {
                            transitionTo(mActivatingState);
                        }
                    }
                    break;
                case CMD_DISCONNECT:
                    break;
                default:
                    return NOT_HANDLED;
            }
            return retVal;
        }
    }

    private EpdgInactiveState mInactiveState = new EpdgInactiveState();

    /**
     * The state machine is retrying and expects a EVENT_RETRY_CONNECTION.
     */
    private class EpdgRetryingState extends State {
        @Override
        public void enter() {

        }

        @Override
        public boolean processMessage(Message msg) {
                return NOT_HANDLED;
        }
    }
    private EpdgRetryingState mRetryingState = new EpdgRetryingState();

    /**
     * The state machine is activating a connection.
     */
    private class EpdgActivatingState extends State {
        @Override
        public void enter() {
            mReasonCode = EpdgConstants.EPDG_RESPONSE_OK;
            updateState(DetailedState.CONNECTING, String.valueOf(mReasonCode));
            if (mEpdgConfig.isHandOver) {
               sendMessageDelayed(EVENT_CONNECT_EXPIRED, MAX_HANDOVER_GUARD_TIMER);
            } else {
               sendMessageDelayed(EVENT_CONNECT_EXPIRED, MAX_CONNECTION_GUARD_TIMER);
            }
        }

        @Override
        public void exit() {
            removeMessages(EVENT_CONNECT_EXPIRED);
        }

        @Override
        public boolean processMessage(Message msg) {
            boolean retVal = HANDLED;

            switch (msg.what) {
                case CMD_RECONNECT:
                    deferMessage(msg);
                    break;
                case EVENT_CONNECTED_DONE:
                    transitionTo(mActiveState);
                    break;
                case EVENT_CONNECTED_FAILURE:
                    updateState(DetailedState.FAILED, String.valueOf(mReasonCode));
                    transitionTo(mInactiveState);
                    break;
                case CMD_DISCONNECT:
                    disconnect();
                case EVENT_WIFI_DISCONNECTED:
                    updateState(DetailedState.FAILED, String.valueOf(mReasonCode));
                    transitionTo(mInactiveState);
                    break;
                case EVENT_CONNECT_EXPIRED:
                    disconnect();
                    updateState(DetailedState.FAILED,
                            String.valueOf(EpdgConstants.FAILURE_CAUSE_CONNECT_EXPIRED));
                    transitionTo(mInactiveState);
                    break;
                default:
                    retVal = NOT_HANDLED;
                    break;
            }
            return retVal;
        }
    }
    private EpdgActivatingState mActivatingState = new EpdgActivatingState();


    /**
     * The state machine is activating a connection.
     */
    private class EpdgActiveState extends State {
        @Override public void enter() {
            updateState(DetailedState.CONNECTED, String.valueOf(mReasonCode));

            if (mApnType == EpdgManager.TYPE_IMS) {
                mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + mDpdSeconds * 1000, mEpdgPendingIntent);
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            boolean retVal = HANDLED;

            switch (msg.what) {
                case CMD_RECONNECT:
                    log("Activate. Do nothing");
                    break;
                case EVENT_CONNECTED_FAILURE:
                case EVENT_DISCONNECTED_DONE:
                    updateState(DetailedState.DISCONNECTED, String.valueOf(mReasonCode));
                    transitionTo(mInactiveState);
                    break;
                case CMD_DISCONNECT:
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        log("interrupted");
                    }
                case EVENT_WIFI_DISCONNECTED:
                    disconnect();
                    transitionTo(mDisconnectingState);
                    break;
                default:
                    retVal = NOT_HANDLED;
                    break;
            }
            return retVal;
        }


    }
    private EpdgActiveState mActiveState = new EpdgActiveState();

    /**
     * The state machine is disconnecting.
     */
    private class EpdgDisconnectingState extends State {

        @Override public void exit() {
            updateState(DetailedState.DISCONNECTED, String.valueOf(mReasonCode));
            if (mApnType == EpdgManager.TYPE_IMS) {
                mAlarmManager.cancel(mEpdgPendingIntent);
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            boolean retVal = HANDLED;

            switch (msg.what) {
                case CMD_RECONNECT:
                    deferMessage(msg);
                    break;
                case EVENT_CONNECTED_FAILURE:
                case EVENT_DISCONNECTED_DONE:
                    transitionTo(mInactiveState);
                    break;
                default:
                    retVal = NOT_HANDLED;
                    break;
            }
            return retVal;
        }
    }
    private EpdgDisconnectingState mDisconnectingState = new EpdgDisconnectingState();

    /**
     * Dump the current state.
     *
     * @param fd
     * @param pw
     * @param args
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        super.dump(fd, pw, args);
        pw.println();
        pw.println("EpdgConfig:[" + mApnTypeName + "]" + mEpdgConfig);
        pw.println();
        pw.println("Reference counter:" + mRefCount);
        pw.println();

        if (mNetworkCapabilities != null) {
            pw.println("NC:" + mNetworkCapabilities);
        }
        if (mNetworkInfo != null) {
            pw.println("NI:" + mNetworkInfo);
        }
        if (mLinkProperties != null) {
            pw.println("LP:" + mLinkProperties);
        }
        pw.println();
        pw.println("Reason Code:" + mReasonCode);
        pw.println();
    }
}
