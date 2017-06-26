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

package com.mediatek.internal.telephony.cdma;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.net.ConnectivityManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import android.util.Log;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cdma.CDMAPhone;

import com.mediatek.internal.telephony.ltedc.LteDcPhoneProxy;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteModeController;

import java.io.BufferedOutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;


/**
 * @hide
 */
public class ViaGpsProcess extends Handler implements IGpsProcess {
    static final String LOG_TAG = "VIA_GPS";

    /* added by wsong, for gps mpc ip & port address setting notify */
    public static final String INTENT_VIA_GPS_MPC_SETTING_NOTIFY =
        "com.android.internal.telephony.via-gps-mpc-setting-notify";
    public static final String INTENT_VIA_GPS_MPC_SETTING_NOTIFY_IP_EXTRA =
        "via-gps-mpc-setting-ip";
    public static final String INTENT_VIA_GPS_MPC_SETTING_NOTIFY_PORT_EXTRA =
        "via-gps-mpc-setting-port";

    /* added by wsong, for gps mpc ip & port address setting result notify */
    public static final String INTENT_VIA_GPS_MPC_SETTING_RESULT_NOTIFY =
        "com.android.internal.telephony.via-gps-mpc-setting-result-notify";
    public static final String INTENT_VIA_GPS_MPC_SETTING_RESULT_NOTIFY_EXTRA =
        "via-gps-mpc-setting-result";

    /* added by wsong, for gps fix result notify */
    public static final String INTENT_VIA_GPS_FIX_RESULT_NOTIFY =
        "com.android.internal.telephony.via-gps-fix-result-notify";
    public static final String INTENT_VIA_GPS_FIX_RESULT_NOTIFY_EXTRA =
        "via-gps-fix-result";

    /*for agps test*/
    public static final String INTENT_VIA_SIMU_REQUEST =
        "com.android.internal.telephony.via-simu-request";
    public static final String EXTRAL_VIA_SIMU_REQUEST_PARAM =
        "com.android.internal.telephony.via-simu-request-param";

    private static final int EVENT_GPS_APPLY_WAP_SRV             = 1;
    private static final int EVENT_GPS_MPC_SET_COMPLETE          = 2;
    private static final int EVENT_DATA_CONNECT_STATUS_CHANGE    = 3;

    /* VIA GPS event */
    private static final int REQUEST_DATA_CONNECTION = 0;
    private static final int CLOSE_DATA_CONNECTION   = 1;
    private static final int GPS_START               = 2;
    private static final int GPS_FIX_RESULT          = 3;
    private static final int GPS_STOP                = 4;

    /* Instance Variables */
    private Context mContext;
    private CommandsInterface mCM;
    private CDMAPhone mPhone;

    private int mSimId;

    /* Connectivity Manager instance */
    private ConnectivityManager mConnectivityManager;
    /* This is really just for using the capability */
    private final NetworkRequest mNetworkRequest;
    /* LTE network request */
    private final NetworkRequest mLteNetworkRequest;
    /* The callback to register when we request MMS network */
    private ConnectivityManager.NetworkCallback mNetworkCallback;
    /* state reported to MD */
    private int mReportedState = -1;

    private static final int DATACALL_DISCONNECTED = 0; /*disconnected*/
    private static final int DATACALL_CONNECTED = 1; /*connected*/
    private static final int DATACALL_WIFI = 2; /*wifi connected*/
    private static final int DATACALL_OTHER = 3; /*other datacall connected*/



    /* Interface to talk with agpsd */
    private AgpsInterface mAgpsInterface;
    /* The sim slot count of the handset */
    private int mSlotCount = 0;
    /* The subscripber(phone) count of the handset */
    private int mPhoneCount = 0;
    /* Phone status listeners of all subscriper */
    private List<GpsProcessPhoneStateListener> mPhoneStateListeners
        = new ArrayList<GpsProcessPhoneStateListener>();
    /* Last mobile network type that is reported to agpsd */
    private int mNotifiedNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    /* Last C2K MD status that is reported to agpsd */
    private int mNotifiedStatus = ServiceState.STATE_OUT_OF_SERVICE;
    /* LTE DC phone proxy ID */
    private static final int LTE_DC_PHONE_PROXY_ID = 0;
    /* Telephony Manager Instance */
    private TelephonyManager mTeleMgr;
    private boolean mDataEnabled = false;
    private boolean mAgpsDataEnabled = false;

    /**
     * @hide
     *
     * @param context context instance
     * @param phone The phone instance
     * @param ci The command interface
     * @param simId Sim id used to do data connection
     */
    public ViaGpsProcess(Context context, CDMAPhone phone, CommandsInterface ci, int simId) {
        mContext = context;
        mPhone = phone;
        mCM = ci;
        mSimId = simId;
        mConnectivityManager = (ConnectivityManager) context.getSystemService(
                                Context.CONNECTIVITY_SERVICE);
        mAgpsInterface = new AgpsInterface(phone, context);
        mTeleMgr = (TelephonyManager) context.getSystemService(
                                Context.TELEPHONY_SERVICE);

        mNetworkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                .build();
        mLteNetworkRequest = new NetworkRequest.Builder().build();
        mNetworkCallback = null;
        Log.d(LOG_TAG, "Construct ViaGpsProcess this = " + this);
    }

    /**
     * @hide
     *
     * @param context context instance
     * @param phone The phone instance
     * @param ci The command interface
     */
    public ViaGpsProcess(Context context, CDMAPhone phone, CommandsInterface ci) {
        // currently only support simid 0 (c2k sim)
        this(context, phone, ci, 0);
    }

    /**
     * @hide
     */
    public void start() {
        Log.d(LOG_TAG, "start() this = " + this);

        mCM.registerForViaGpsEvent(this, EVENT_GPS_APPLY_WAP_SRV, null);
        /* Register for GPS WAP service */
        IntentFilter intentFilter = new IntentFilter();
        //intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        //intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION_IMMEDIATE);
        intentFilter.addAction(INTENT_VIA_GPS_MPC_SETTING_NOTIFY);
        intentFilter.addAction(INTENT_VIA_SIMU_REQUEST); /*simulate test action*/
        intentFilter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        mContext.registerReceiver(mBroadcastReceiver, intentFilter);
    }

    /**
     * @hide
     */
    public void stop() {
        Log.d(LOG_TAG, "stop() this = " + this);
        mCM.unregisterForViaGpsEvent(this);
        requestAGPSTcpConnected(0, null);
        if (mNetworkCallback != null) {
            mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
        }
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;

        switch(msg.what) {
            case EVENT_GPS_APPLY_WAP_SRV: {
                ar = (AsyncResult) msg.obj;
                int[] data = (int[]) (ar.result);
                viaGpsEventHandler(data);
            }
            break;

            case EVENT_GPS_MPC_SET_COMPLETE: {
                ar = (AsyncResult) msg.obj;
                boolean success = (ar.exception == null) ? true : false;
                onGpsMpcSetComplete(success);
            }
            break;

            case EVENT_DATA_CONNECT_STATUS_CHANGE: {
                sendDataEnabledStatus();
            }
            break;

            default: {
                super.handleMessage(msg);
            }
        }
    }

    private boolean getAgpsDataEnabled() {
        int defaultSubId = SubscriptionManager.getDefaultDataSubId();
        int slotId = SvlteModeController.getActiveSvlteModeSlotId();
        if (slotId == SvlteModeController.CSFB_ON_SLOT) {
            Log.e(LOG_TAG, "getAgpsDataEnabled CSFB on slot");
            return false;
        }
        int[] subIds = SubscriptionManager.getSubId(slotId);
        if (subIds == null) {
            return false;
        }
        for (int j = 0; j < subIds.length; j++) {
            if (subIds[j] > 0) {
                Log.d(LOG_TAG, "Slot0 Sub = " + subIds[j] + ", defaultSub = " + defaultSubId);
                if (subIds[j] == defaultSubId) {
                    return true;
                }
            }
        }
        return false;
    }

    private void sendDataEnabledStatus() {
        final boolean dataEnable = mTeleMgr.getDataEnabled();
        Log.d(LOG_TAG, "Data connection Enabled ?" + dataEnable);
        if (dataEnable) {
            boolean agpsDataEnable = getAgpsDataEnabled();
            if (mDataEnabled != dataEnable ||
                agpsDataEnable != mAgpsDataEnabled) {
                int status = agpsDataEnable ?
                    AgpsInterface.DATA_ENABLED_ON_LTE_DC_PHONE :
                    AgpsInterface.DATA_ENABLED_ON_OTHER_PHONE;
                mAgpsInterface.setMobileDataStatus(status);
            }
            mAgpsDataEnabled = agpsDataEnable;
            Log.d(LOG_TAG, "Agps Data connection Enabled? " + mAgpsDataEnabled);
        } else {
            if (mDataEnabled != dataEnable) {
                mAgpsInterface.setMobileDataStatus(AgpsInterface.DATA_DISABLED);
            }
        }
        mDataEnabled = dataEnable;
    }

    private void viaGpsEventHandler(int[] data) {
        int event = data[0];
        int gpsStatus = data[1];

        switch (event) {
        case REQUEST_DATA_CONNECTION:
            Log.d(LOG_TAG, "[VIA] GPS Request data connection");
            startGpsWapService();
            break;
        case CLOSE_DATA_CONNECTION:
            Log.d(LOG_TAG, "[VIA] GPS Stop data connection");
            stopGpsWapService();
            break;
        case GPS_START:
            break;
        case GPS_FIX_RESULT:
            onFixResultHandler(gpsStatus);
            break;
        case GPS_STOP:
        default:
            break;
        }
    }

    private void onFixResultHandler(int gpsStatus) {
        Log.d(LOG_TAG, "[VIA] onFixResultHandler, gpsStatus = " + gpsStatus);
        Intent intent = new Intent(INTENT_VIA_GPS_FIX_RESULT_NOTIFY);
        intent.putExtra(INTENT_VIA_GPS_FIX_RESULT_NOTIFY_EXTRA, gpsStatus);
        mContext.sendBroadcast(intent);
    }
    private void stopGpsWapService() {
        Log.d(LOG_TAG, "[VIA] stopGpsWapService() this = " + this);
        if (mNetworkCallback != null) {
            mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
        }
    }

    private PhoneConstants.DataState getDataConnectState() {
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            int slotId = SvlteModeController.getActiveSvlteModeSlotId();
            if (slotId == SvlteModeController.CSFB_ON_SLOT) {
                Log.e(LOG_TAG, "getDataConnectState CSFB on slot");
                return PhoneConstants.DataState.DISCONNECTED;
            }
            LteDcPhoneProxy lteDcPhoneProxy =
                (LteDcPhoneProxy) PhoneFactory.getPhone(slotId);
            if (lteDcPhoneProxy != null) {
                PhoneBase psPhone = lteDcPhoneProxy.getPsPhone();
                if (psPhone != null) {
                    return getDataConnectState(psPhone);
                } else {
                    Log.e(LOG_TAG, "Cannot get PS Phone");
                }
            } else {
                Log.e(LOG_TAG, "Cannot get LteDcPhoneProxy");
            }
        } else {
            return getDataConnectState(mPhone);
        }
        return PhoneConstants.DataState.DISCONNECTED;
    }
    private PhoneConstants.DataState getDataConnectState(PhoneBase phone) {
        PhoneConstants.DataState defaultState = phone.getDataConnectionState(
            PhoneConstants.APN_TYPE_DEFAULT);
        PhoneConstants.DataState  mmsState = phone.getDataConnectionState(
            PhoneConstants.APN_TYPE_MMS);
        PhoneConstants.DataState  suplState = phone.getDataConnectionState(
            PhoneConstants.APN_TYPE_SUPL);
        Log.d(LOG_TAG, "[VIA] getDataConnectState default: " + defaultState +
                                                   ", mms: " + mmsState +
                                                  ", supl: " + suplState +
                                                  "phoneName" + phone.getPhoneName());
        if (defaultState == PhoneConstants.DataState.CONNECTING ||
                mmsState == PhoneConstants.DataState.CONNECTING ||
                suplState == PhoneConstants.DataState.CONNECTING) {
             return PhoneConstants.DataState.CONNECTING;
        } else if (defaultState == PhoneConstants.DataState.CONNECTED ||
                mmsState == PhoneConstants.DataState.CONNECTED ||
                suplState == PhoneConstants.DataState.CONNECTED) {
           return PhoneConstants.DataState.CONNECTED;
        } else {
           return PhoneConstants.DataState.DISCONNECTED;
        }
    }
    private void startGpsWapService() {
        Log.d(LOG_TAG, "[VIA] startGpsWapService this =  " + this);
        PhoneConstants.DataState dataState = getDataConnectState();
        // not ctwap data connection has been setuped, exchange the active apn to ctwap

        // reset in case of abnormal session happens before.
        mReportedState = -1;

        if (PhoneConstants.DataState.DISCONNECTED == dataState) {
            requestAGPSTcpConnected(DATACALL_DISCONNECTED, null);
        } else {
            Log.d(LOG_TAG, "[VIA] start requestNetwork");
            mNetworkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    super.onAvailable(network);
                    Log.d(LOG_TAG, "onAvailable: network=" + network);
                    mAgpsInterface.setNetworkId(network.netId);
                    requestAGPSTcpConnected(DATACALL_CONNECTED, null);
                }

                @Override
                public void onLost(Network network) {
                    super.onLost(network);
                    Log.d(LOG_TAG, "onLost: network=" + network);
                    requestAGPSTcpConnected(DATACALL_DISCONNECTED, null);
                    stopGpsWapService();
                }
            };
            int networkType = mTeleMgr.getNetworkType();
            NetworkRequest request;
            if (networkType == TelephonyManager.NETWORK_TYPE_LTE ||
                networkType == TelephonyManager.NETWORK_TYPE_EHRPD) {
                Log.d(LOG_TAG, "Lte network request");
                request = mLteNetworkRequest;
            } else {
                request = mNetworkRequest;
            }
            mConnectivityManager.requestNetwork(request, mNetworkCallback);
        }
    }
    private void requestAGPSTcpConnected(int state, Message msg) {
        Log.d(LOG_TAG, "[VIA] requestAGPSTcpConnected(" + state + "), mReportedState: "
                        + mReportedState);
        if (mReportedState != state) {
           mReportedState = state;
           mCM.requestAGPSTcpConnected(state, null);
        }
    }
    private void onGpsMpcSetComplete(boolean success) {
        Log.d(LOG_TAG, "[VIA] onGpsMpcSetComplete, success = " + success);
        Intent intent = new Intent(INTENT_VIA_GPS_MPC_SETTING_RESULT_NOTIFY);
        intent.putExtra(INTENT_VIA_GPS_MPC_SETTING_RESULT_NOTIFY_EXTRA, success);
        mContext.sendBroadcast(intent);
    }

    private boolean isActiveCdmaPhone(CDMAPhone phone) {
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            int slotId = SvlteModeController.getActiveSvlteModeSlotId();
            if (slotId == SvlteModeController.CSFB_ON_SLOT) {
                return false;
            }
            LteDcPhoneProxy lteDcPhoneProxy =
                (LteDcPhoneProxy) PhoneFactory.getPhone(slotId);
            if (lteDcPhoneProxy != null) {
                CDMAPhone currentCDMAPhone = (CDMAPhone) lteDcPhoneProxy.getNLtePhone();
                return phone.equals(currentCDMAPhone);
            } else {
                return false;
            }
        }
        return true;
    }

    // Receiver to get message of CONNECTED, DISCONNECTED, FAILED, or NOTREADY
    // about data connection
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (INTENT_VIA_GPS_MPC_SETTING_NOTIFY.equals(action)) {
                String ip = intent.getStringExtra(INTENT_VIA_GPS_MPC_SETTING_NOTIFY_IP_EXTRA);
                String port = intent.getStringExtra(INTENT_VIA_GPS_MPC_SETTING_NOTIFY_PORT_EXTRA);

                Log.d(LOG_TAG, "[VIA] INTENT_VIA_GPS_MPC_SETTING_NOTIFY IP = " +
                      ip + ", PORT = " + port);
                mCM.requestAGPSSetMpcIpPort(ip, port, obtainMessage(EVENT_GPS_MPC_SET_COMPLETE));
            } else if (INTENT_VIA_SIMU_REQUEST.equals(action)) {
                int[] data = {-1, 0};
                data[0] = intent.getIntExtra(EXTRAL_VIA_SIMU_REQUEST_PARAM, -1);
                Log.d(LOG_TAG, "[VIA] INTENT_VIA_SIMU_REQUEST =" + data[0]);
                viaGpsEventHandler(data);
            } else if (TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED.equals(action)) {
                if (!isActiveCdmaPhone(ViaGpsProcess.this.mPhone)) {
                    Log.d(LOG_TAG, "None Active CDMAPhone this = " + ViaGpsProcess.this);
                    return;
                }
                Log.d(LOG_TAG, "ACTION_SUBINFO_RECORD_UPDATED this = " + ViaGpsProcess.this);
                for (GpsProcessPhoneStateListener listener: mPhoneStateListeners) {
                    if (listener != null) {
                        mTeleMgr.listen(listener, PhoneStateListener.LISTEN_NONE);
                    }
                }
                mPhoneStateListeners.clear();
                mSlotCount = TelephonyManager.getDefault().getPhoneCount();
                for (int i = 0; i < mSlotCount; i++) {
                    int[] subIds = SubscriptionManager.getSubId(i);
                    if (subIds == null) {
                        continue;
                    }
                    for (int j = 0; j < subIds.length; j++) {
                        if (subIds[j] > 0) {
                            Log.d(LOG_TAG,
                                "Add phone status listner slot = " + i
                                + ", sub = " + subIds[j]);
                            GpsProcessPhoneStateListener listener =
                                new GpsProcessPhoneStateListener(i, subIds[j]);
                            mPhoneStateListeners.add(listener);
                            mTeleMgr.listen(listener,
                                PhoneStateListener.LISTEN_SERVICE_STATE |
                                PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
                        }
                    }
                }

               Log.d(LOG_TAG, "Create Phone State Listener slotCount = " + mSlotCount);
            }
        }
    };

    /**
     * Class that is used to listener the status of Phone.
     */
    class GpsProcessPhoneStateListener extends PhoneStateListener {
        private int mSlotId;
        private int mSubId;

        public GpsProcessPhoneStateListener(int slotId, int subId) {
            super(subId);
            mSlotId = slotId;
            mSubId = subId;
        }

        public void onDataConnectionStateChanged(int state, int networkType) {
            Log.d(LOG_TAG, "onDataConnectionStateChanged slotId = "
                + mSlotId    + ", subId = "
                + mSubId + ", state = "
                + state + ", type="
                + TelephonyManager.getNetworkTypeName(networkType)
                + ", notifiedType = "
                + TelephonyManager.getNetworkTypeName(mNotifiedNetworkType)
                + ", this = " + ViaGpsProcess.this);
            ViaGpsProcess.this.sendEmptyMessage(EVENT_DATA_CONNECT_STATUS_CHANGE);
            if (state == TelephonyManager.DATA_CONNECTED) {
                if (mNotifiedNetworkType == networkType) {
                    return;
                }
                mAgpsInterface.setMobileNetworkType(networkType);
                mNotifiedNetworkType = networkType;
            } else if (state == TelephonyManager.DATA_DISCONNECTED ||
                       state == TelephonyManager.DATA_UNKNOWN) {
                if (mNotifiedNetworkType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                    return;
                }
                mAgpsInterface.setMobileNetworkType(TelephonyManager.NETWORK_TYPE_UNKNOWN);
                mNotifiedNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
            } else {
                return;
            }

        }
        public void onServiceStateChanged(ServiceState serviceState) {

            int cdmaPhoneState = mPhone.getServiceState().getState();
            Log.d(LOG_TAG, "onServiceStateChanged slotId = "
                + mSlotId + ", subId = "
                + mSubId + "state = "
                + serviceState.getState()
                + ", cdmaphone state=" + cdmaPhoneState
                + ", notifyState=" + mNotifiedStatus
                + ", this = " + ViaGpsProcess.this);
            if (mNotifiedStatus == cdmaPhoneState) {
                return;
            }
            mAgpsInterface.setCDMAPhoneStatus(cdmaPhoneState);
            mNotifiedStatus = cdmaPhoneState;
        }

    }

    /**
     * Class that is used to report status to agpsd.
     */
    static class AgpsInterface {

        private static final String SOCKET_ADDRESS = "c2kagpsd";
        private static final String TAG = "VIA_GPS";
        private static final int EVENT_AGPS_NETWORK_TYPE          = 1;
        private static final int EVENT_AGPS_CDMA_PHONE_STATUS     = 2;
        private static final int EVENT_AGPS_MOBILE_DATA_STATUS    = 3;
        private static final int EVENT_AGPS_SET_NETWORK_ID        = 4;
        private static final int DATA_DISABLED                = 0;
        private static final int DATA_ENABLED_ON_LTE_DC_PHONE = 1;
        private static final int DATA_ENABLED_ON_CDMA_PHONE   = 1;
        private static final int DATA_ENABLED_ON_OTHER_PHONE  = 2;
        private LocalSocket mClient;
        private BufferedOutputStream mOut;
        private CDMAPhone mPhone;


        public AgpsInterface(CDMAPhone phone, Context context) {
            mPhone = phone;
        }

        public void setNetworkId(int netId) {
            try {
                connect();
                AgpsInterface.putInt(mOut, EVENT_AGPS_SET_NETWORK_ID);
                AgpsInterface.putInt(mOut, netId);
                mOut.flush();

            } catch (IOException e) {
                Log.e(TAG, "Exception " + e);
            } finally {
                close();
            }
        }

        public void setCDMAPhoneStatus(int state) {
            try {
                connect();
                AgpsInterface.putInt(mOut, EVENT_AGPS_CDMA_PHONE_STATUS);
                AgpsInterface.putInt(mOut, state);
                mOut.flush();

            } catch (IOException e) {
                Log.e(TAG, "Exception " + e);
            } finally {
                close();
            }
        }

        public void setMobileNetworkType(int type) {
            try {
                connect();
                AgpsInterface.putInt(mOut, EVENT_AGPS_NETWORK_TYPE);
                AgpsInterface.putInt(mOut, type);
                mOut.flush();

            } catch (IOException e) {
                Log.e(TAG, "Exception " + e);
            } finally {
                close();
            }
        }

        public void setMobileDataStatus(int status) {
            try {
                connect();
                AgpsInterface.putInt(mOut, EVENT_AGPS_MOBILE_DATA_STATUS);
                AgpsInterface.putInt(mOut, status);
                mOut.flush();

            } catch (IOException e) {
                Log.e(TAG, "Exception " + e);
            } finally {
                close();
            }
        }


        private void connect() throws IOException {
            if (mClient != null) {
                mClient.close();
            }
            mClient = new LocalSocket();
            mClient.connect(
                new LocalSocketAddress(SOCKET_ADDRESS,
                                       LocalSocketAddress.Namespace.ABSTRACT));
            mClient.setSoTimeout(3000);
            mOut = new BufferedOutputStream(mClient.getOutputStream());
        }

        private void close() {
            try {
                if (mClient != null) {
                    mClient.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private static void putByte(BufferedOutputStream out, byte data) throws IOException {
            out.write(data);
        }

        private static void putShort(BufferedOutputStream out, short data) throws IOException {
            putByte(out, (byte) (data & 0xff));
            putByte(out, (byte) ((data >> 8) & 0xff));
        }

        private static void putInt(BufferedOutputStream out, int data) throws IOException {
            putShort(out, (short) (data & 0xffff));
            putShort(out, (short) ((data >> 16) & 0xffff));
        }
    }
}
