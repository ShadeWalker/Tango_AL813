/*
* This Software is the property of VIA Telecom, Inc. and may only be used pursuant to a
license from VIA Telecom, Inc.
* Any unauthorized use inconsistent with the terms of such license is strictly prohibited.
* Copyright (c) 2013 -2015 VIA Telecom, Inc. All rights reserved.
*/

package com.android.internal.telephony.cdma.utk;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.Uri;

//import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Telephony;

//import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyProperties;

//import java.net.Inet4Address;
//import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;



//NFC-02011

public class BipService {
    private static final String LOG_TAG = "BipService";

    private final int BIPCHANNEL_MAX = 8;

    private final Uri APN_URI = Telephony.Carriers.CONTENT_URI;
    private final String APN_DEFFAULT = "ctwap";
    private final String APN_USER_NAME = "ctwap@mycdma.cn";
    private final String APN_PASSWORD = "vnet.mobi";
    private final String APN_ENABLE_FEATURE = Phone.FEATURE_ENABLE_SUPL;
    private final int DEFAULT_NETWORK_TYPE = ConnectivityManager.TYPE_MOBILE;

    private static BipService sInstanceSim1 = null;
    private static BipService sInstanceSim2 = null;

    private Handler mUtkService = null;
    private Context mContext = null;
    private boolean mWaitConnect = false;
    private int mPhoneId = -1;

    private ConnectivityReceiver mConnectivityReceiver = null;
    private ConnectivityManager mConnectivityManager = null;

    private List<InetAddress> mLocalIps = null;

    private HashMap<Integer, BipChannel> mBipChannelHash = null;
    private Object mBipChannelLock = new Object();

    public BipService(Context context, Handler handler, int phoneId) {

        UtkLog.d(LOG_TAG, "BipService version 1.3.1");

        mContext = context;
        mConnectivityReceiver = new ConnectivityReceiver();
        mUtkService = handler;
        mPhoneId = phoneId;

        mBipChannelHash = new HashMap<Integer, BipChannel>();

        mLocalIps = new ArrayList<InetAddress>();

        mConnectivityManager = (ConnectivityManager) mContext.getSystemService(
                                                    Context.CONNECTIVITY_SERVICE);

        IntentFilter intent = new IntentFilter();
        intent.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(mConnectivityReceiver, intent);
    }

    public void dispose() {
        UtkLog.d(this, "dispose");

        mContext.unregisterReceiver(mConnectivityReceiver);
        if (PhoneConstants.SIM_ID_1 == mPhoneId) {
            sInstanceSim1 = null;
        } else if (PhoneConstants.SIM_ID_2 == mPhoneId) {
            sInstanceSim2 = null;
        } else {
            UtkLog.d(this, "invalid dispose");
        }
    }

    public static BipService getInstance(Context context, Handler handler, int phoneId) {
        UtkLog.d(LOG_TAG, " Bip getInstance" + phoneId);
        if (PhoneConstants.SIM_ID_1 == phoneId) {
            if (sInstanceSim1 == null) {
                UtkLog.d(LOG_TAG, " new BipService" + phoneId);
                sInstanceSim1 = new BipService(context, handler, phoneId);
            }
            return sInstanceSim1;
        } else if (PhoneConstants.SIM_ID_2 == phoneId) {
            if (sInstanceSim2 == null) {
                UtkLog.d(LOG_TAG, " new BipService" + phoneId);
                sInstanceSim2 = new BipService(context, handler, phoneId);
            }
            return sInstanceSim2;
        } else {
            UtkLog.d(LOG_TAG, "Invalid phone Id and just return null");
            return null;
        }
    }

    public void sendResponseToUtk(int what, int arg1, int arg2, Object obj) {
        UtkLog.d(LOG_TAG, " sendResponseToUtk:" + what);
        Message m = mUtkService.obtainMessage(what, arg1, arg2, obj);
        mUtkService.sendMessage(m);
    }

    private void onNetworkConnected(BipChannel ch) {
        UtkLog.d(LOG_TAG, " onNetworkConnected");

        if (ch == null) {
            return;
        }

        int ret = ch.linkEstablish();
        OpenChannelSettings p = ch.getBipChannelParams();

        if (ret == BipConstants.RESULT_SUCCESS) {
            if (p.immediateLink && !p.backgrountMode) {
                //response now
                OpenChannelResult r = new OpenChannelResult();

                r.channelStatus = ch.mChannelStatus;
                r.bearerDesc = p.bearerDesc;
                r.bufferSize = ch.mRxBufferSize;
                if (p.localAddress == null && mLocalIps.size() > 0) {
                    try {
                        r.localAddress = new OtherAddress();
                        r.localAddress.addressType = BipConstants.BIP_OTHER_ADDRESS_TYPE_IPV4;
                        r.localAddress.address = mLocalIps.get(0);
                    } catch (UnknownHostException e) {
                        UtkLog.d(LOG_TAG, " UnknownHostException");
                    }
                }

                int arg2 = BipConstants.RESULT_CODE_OK;
                if (r.bufferSize != p.bufferSize) {
                    arg2 = BipConstants.RESULT_CODE_RESULT_SUCCESS_PERFORMED_WITH_MODIFICATION;
                }

                sendResponseToUtk(UtkService.MSG_ID_OPENED_CHANNEL,
                                  BipConstants.RESULT_SUCCESS,  arg2, r);
            }
        } else {
            RemoveBipChannel(ch);
            if (p.immediateLink && !p.backgrountMode) {
                sendResponseToUtk(UtkService.MSG_ID_OPENED_CHANNEL, BipConstants.RESULT_ERROR,
                                  BipConstants.RESULT_CODE_NETWORK_CRNTLY_UNABLE_TO_PROCESS, null);
            }
        }
    }

    public void openChannel(OpenChannelSettings p) {
        UtkLog.d(LOG_TAG, " openChannel:" + p);

        if ((p.bearerDesc.bearerType != BipConstants.BEARER_TYPE_DEFAULT) &&
           (p.bearerDesc.bearerType != BipConstants.BEARER_TYPE_PACKET_DATA)) {
           UtkLog.d(LOG_TAG, " not surpot bearerType");

           sendResponseToUtk(UtkService.MSG_ID_OPENED_CHANNEL, BipConstants.RESULT_ERROR,
                             BipConstants.RESULT_CODE_BEYOND_TERMINAL_CAPABILITY, null);
           return;
        }

        mWaitConnect = false;

        //ME is busy, proccess in utk app service

        BipChannel ch = CreatNewBipChannel(p);

        if (ch == null) {
            UtkLog.d(LOG_TAG, " get null channel");
            sendResponseToUtk(UtkService.MSG_ID_OPENED_CHANNEL, BipConstants.RESULT_ERROR,
                              BipConstants.RESULT_CODE_BIP_ERROR, null);
            return;
        }

        int netConnected = -1;
        NetworkInfo ni = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        if (ni == null) {
            RemoveBipChannel(ch);
            sendResponseToUtk(UtkService.MSG_ID_OPENED_CHANNEL, BipConstants.RESULT_ERROR,
                             BipConstants.RESULT_CODE_NETWORK_CRNTLY_UNABLE_TO_PROCESS, null);
            return;
        }

        UtkLog.d(LOG_TAG, " openChannel network tate:" + ni.getState());
        if (ni.getState() == NetworkInfo.State.CONNECTED) {
            netConnected = PhoneConstants.APN_ALREADY_ACTIVE;
        }

        /*if(netConnected == -1){
            if(p.networkAccessName == null){
                //use deault apn
                UtkLog.d(LOG_TAG, " use default apn settings");
                //default parameters???
                setBipApnParams(APN_DEFFAULT, APN_USER_NAME, APN_PASSWORD);
            }else{
                UtkLog.d(LOG_TAG, " set new bip apn settings");
                setBipApnParams(p.networkAccessName, p.userName, p.userPwd);
            }

            netConnected = mConnectivityManager.startUsingNetworkFeature(
                                         ConnectivityManager.TYPE_MOBILE_SUPL, APN_ENABLE_FEATURE);

            UtkLog.d(LOG_TAG, " startUsingNetworkFeature result "+netConnected);
        }*/

        if ((netConnected != PhoneConstants.APN_ALREADY_ACTIVE) &&
           (netConnected != PhoneConstants.APN_REQUEST_STARTED)) {
           RemoveBipChannel(ch);

           sendResponseToUtk(UtkService.MSG_ID_OPENED_CHANNEL, BipConstants.RESULT_ERROR,
                             BipConstants.RESULT_CODE_NETWORK_CRNTLY_UNABLE_TO_PROCESS, null);
           return;
        }

        if ((!p.immediateLink) && (!p.backgrountMode)) {
            //ondemand
            UtkLog.d(LOG_TAG, " open channel ondemand");

            OpenChannelResult r = new OpenChannelResult();

            r.channelStatus = ch.mChannelStatus;
            r.bearerDesc = p.bearerDesc;
            r.bufferSize = ch.mRxBufferSize;

            sendResponseToUtk(UtkService.MSG_ID_OPENED_CHANNEL,
                              BipConstants.RESULT_SUCCESS, BipConstants.RESULT_CODE_OK, r);

            //will linkEstablish when send data
            return;
        }

        if (p.backgrountMode) {
            UtkLog.d(LOG_TAG, " open channel background mode");

            OpenChannelResult r = new OpenChannelResult();

            r.channelStatus = ch.mChannelStatus;
            r.bearerDesc = p.bearerDesc;
            r.bufferSize = ch.mRxBufferSize;

            int arg2 = BipConstants.RESULT_CODE_OK;
            if (r.bufferSize != p.bufferSize) {
                UtkLog.d(LOG_TAG, " bufferSize chanded " + p.bufferSize + " to " + r.bufferSize);
                arg2 = BipConstants.RESULT_CODE_RESULT_SUCCESS_PERFORMED_WITH_MODIFICATION;
            }

            sendResponseToUtk(UtkService.MSG_ID_OPENED_CHANNEL,
                              BipConstants.RESULT_SUCCESS,  arg2, r);
        }

        if (netConnected == PhoneConstants.APN_ALREADY_ACTIVE) {
            onNetworkConnected(ch);
        } else {
            mWaitConnect = true;
        }
    }

    public void closeChannel(int chId, boolean listen) {
        UtkLog.d(LOG_TAG, " closeChannel id:" + chId + " listen:" + listen);

        BipChannel ch = FindBipChannelById(chId);
        if (ch != null) {
            if (ch.linkDisconnect(listen) == BipConstants.RESULT_SUCCESS) {
                UtkLog.d(LOG_TAG, " closeChannel OK");
                sendResponseToUtk(UtkService.MSG_ID_CLOSED_CHANNEL, BipConstants.RESULT_SUCCESS,
                                  BipConstants.RESULT_CODE_OK, null);
            } else {
                UtkLog.d(LOG_TAG, " closeChannel fail");
                sendResponseToUtk(UtkService.MSG_ID_CLOSED_CHANNEL, BipConstants.RESULT_ERROR,
                                  BipConstants.RESULT_CODE_BIP_ERROR, null);
            }

            RemoveBipChannel(ch);
        } else {
            sendResponseToUtk(UtkService.MSG_ID_CLOSED_CHANNEL, BipConstants.RESULT_ERROR,
                              BipConstants.RESULT_CODE_BIP_ERROR, null);
        }
    }

    public void receiveData(int chId, int reqDataLen) {
        UtkLog.d(LOG_TAG, " receiveData id:" + chId + " reqDataLen:" + reqDataLen);

        BipChannel ch = FindBipChannelById(chId);
        if (ch != null) {
            if (reqDataLen > BipConstants.RECEIVE_DATA_MAX_LEN) {
                reqDataLen = BipConstants.RECEIVE_DATA_MAX_LEN;
                UtkLog.d(LOG_TAG, " change reqDataLen to=" + reqDataLen);
            }

            ch.receiveData(reqDataLen);
        } else {
            sendResponseToUtk(UtkService.MSG_ID_RECEIVED_DATA, BipConstants.RESULT_ERROR,
                              BipConstants.RESULT_CODE_BIP_ERROR, null);
        }
    }

    public void sendData(int chId, byte[] data, boolean sendImmediately) {
        UtkLog.d(LOG_TAG, " sendData chId:" + chId + " sendImmediately:" + sendImmediately);
        if (data == null) {
            UtkLog.d(LOG_TAG, " sendData, but no data");
            sendResponseToUtk(UtkService.MSG_ID_SENT_DATA, BipConstants.RESULT_ERROR,
                                   BipConstants.ADDITIONAL_INFO_CHANNEL_NO_DATA, null);
            return;
        }

        UtkLog.d(LOG_TAG, " sendData length:" + data.length);

        BipChannel ch = FindBipChannelById(chId);
        if (ch != null) {
            OpenChannelSettings p = ch.getBipChannelParams();
            if (!ch.isLinked()) {
                //ondemand mode
                if ((!p.immediateLink) && (!p.backgrountMode)) {
                    if (ch.linkEstablish() != BipConstants.RESULT_SUCCESS) {
                        sendResponseToUtk(UtkService.MSG_ID_SENT_DATA, BipConstants.RESULT_ERROR,
                                          BipConstants.ADDITIONAL_INFO_CHANNEL_CLOSED, null);
                        return;
                    }
                }
            }
            ch.sendData(data, sendImmediately);
        } else {
            sendResponseToUtk(UtkService.MSG_ID_SENT_DATA, BipConstants.RESULT_ERROR,
                              BipConstants.ADDITIONAL_INFO_CHANNEL_ID_NOT_AVAILABLE, null);
        }
    }

    public void getChannelStatus(int chId) {
        UtkLog.d(LOG_TAG, " getChannelStatus id:" + chId);

        BipChannel ch = FindBipChannelById(chId);
        if (ch != null) {
            ChannelStatus cs = ch.getChannelStatus();
            sendResponseToUtk(UtkService.MSG_ID_GET_CHANNEL_STATUS,
                BipConstants.RESULT_SUCCESS, BipConstants.RESULT_CODE_OK, cs);
        } else {
            sendResponseToUtk(UtkService.MSG_ID_GET_CHANNEL_STATUS, BipConstants.RESULT_ERROR,
                BipConstants.RESULT_CODE_BIP_ERROR, null);
        }
    }

    public class ConnectivityReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (context == null || intent == null) {
                return;
            }

            if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                NetworkInfo info =
                        (NetworkInfo) intent.getExtra(ConnectivityManager.EXTRA_NETWORK_INFO);

                if (info == null) {
                    //
                    return;
                }

                int type = info.getType();
                NetworkInfo.State state = info.getState();

                UtkLog.d(LOG_TAG, " CONNECTIVITY_ACTION type:" + type + " state:" + state);

                if (type == ConnectivityManager.TYPE_MOBILE ||
                    type == ConnectivityManager.TYPE_MOBILE_SUPL) {
                    //user stop packet data service activation
                    //
                    //
                    if (state == NetworkInfo.State.CONNECTED ||
                        state == NetworkInfo.State.DISCONNECTED) {
                        if (state == NetworkInfo.State.CONNECTED) {
                            LinkProperties link = mConnectivityManager.getActiveLinkProperties();
                            if (link != null) {
                                mLocalIps.clear();
                                Iterator<InetAddress> iter = link.getAddresses().iterator();
                                while (iter.hasNext()) {
                                    InetAddress ad = iter.next();
                                    if (ad != null) {
                                        mLocalIps.add(ad);
                                        UtkLog.d(LOG_TAG, " local ip:" + ad.getHostAddress());
                                    }
                                }
                            }
                        }

                        if (!mWaitConnect) {
                            return;
                        }

                        UtkLog.d(LOG_TAG, " network status changed");

                        mWaitConnect = false;

                        ArrayList<BipChannel> chs = FindBipChannelToLink();
                        if (chs != null && chs.size() > 0) {
                            for (int i = 0; i < chs.size(); i++) {
                              onNetworkConnected(chs.get(i));
                            }
                        }

                        if (chs != null) {
                            chs.clear();
                        }
                    }
                }
            }
        }
    }

    ////////
    private BipChannel CreatNewBipChannel(OpenChannelSettings p) {
        UtkLog.d(LOG_TAG, " CreatNewBipChannel");

        BipChannel ch = null;
        int id;

        //id is 1---8;
        for (id = 1; id <= BIPCHANNEL_MAX; id++) {
            synchronized (mBipChannelLock) {
                ch = mBipChannelHash.get(id);
            }
            if (null == ch) {
                break;
            }
        }

        if (id > BIPCHANNEL_MAX) {
            UtkLog.d(LOG_TAG, " no free channel");
            return null;
        }

        if (p.transportLevel.protocolType ==
              BipConstants.TRANSPORT_TYPE_TCP_CLIENT_REMOTE) {
            ch = new TcpClientChannel(this, p, id);

            mConnectivityManager.requestRouteToHostAddress(
                    DEFAULT_NETWORK_TYPE, p.destAddress.address);
        } else if (p.transportLevel.protocolType ==
              BipConstants.TRANSPORT_TYPE_UDP_CLIENT_REMOTE) {
            ch = new UdpClientChannel(this, p, id);

            mConnectivityManager.requestRouteToHostAddress(
                    DEFAULT_NETWORK_TYPE, p.destAddress.address);
        } else if (p.transportLevel.protocolType ==
              BipConstants.TRANSPORT_TYPE_TCP_SERVER) {
            UtkLog.d(LOG_TAG, " tcp server, immediateLink=" + p.immediateLink +
                              " backgrountMode=" + p.backgrountMode);
            ch = new TcpServerChannel(this, p, id);
        } else {
            UtkLog.d(LOG_TAG, " channel type not support");
            return null;
        }

        synchronized (mBipChannelLock) {
            mBipChannelHash.put(id, ch);
        }

        return ch;
    }

    private ArrayList<BipChannel> FindBipChannelToLink() {
        UtkLog.d(LOG_TAG, " FindBipChannelToLink");

        ArrayList<BipChannel> chs = new ArrayList<BipChannel>();

        for (int id = 1; id < BIPCHANNEL_MAX; id++) {
            BipChannel ch;
            synchronized (mBipChannelLock) {
                ch = mBipChannelHash.get(id);
            }
            if (ch != null && ch.isBackgroudModOrImmediate() && (!ch.isLinked())) {
                UtkLog.d(LOG_TAG, " id:" + ch.getBipChannelId());
                chs.add(ch);
            }
        }

        return chs;
    }

    private BipChannel FindBipChannelById(int id) {
        BipChannel ch;
        synchronized (mBipChannelLock) {
            ch = mBipChannelHash.get(id);
        }

        UtkLog.d(LOG_TAG, " FindBipChannelById:" + id + " channel:" + ch);

        return ch;
    }

    private void RemoveBipChannel(BipChannel ch) {
        if (ch == null) {
            UtkLog.d(LOG_TAG, " RemoveBipChannel null ch");
            return;
        }

        UtkLog.d(LOG_TAG, " RemoveBipChannel:" + ch.getBipChannelId());

        synchronized (mBipChannelLock) {
            mBipChannelHash.remove(ch.getBipChannelId());
        }
    }

    private void setBipApnParams(String apn, String user, String pwd) {

        UtkLog.d(LOG_TAG, " setBipApnParams:" + apn + " user name:" + user + " passwd:" + pwd);

        String numeric = null;
        String mcc = null;
        String mnc = null;
        String apnType = "supl";

        numeric = SystemProperties.get(TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC);

        if (numeric.length() >= 4) {
            Cursor cursor = null;
            mcc = numeric.substring(0, 3);
            mnc = numeric.substring(3);

            UtkLog.d(LOG_TAG, " mcc: " + mcc + " mnc: " + mnc);

            String selection = "name = 'BIP' and numeric = '" + mcc + mnc + "'";

            cursor = mContext.getContentResolver().query(APN_URI, null, selection, null, null);
            if (cursor != null) {
                ContentValues values = new ContentValues();
                values.put(Telephony.Carriers.NAME, "BIP");
                values.put(Telephony.Carriers.APN, apn);
                values.put(Telephony.Carriers.USER, user);
                values.put(Telephony.Carriers.PASSWORD, pwd);
                values.put(Telephony.Carriers.TYPE, apnType);
                values.put(Telephony.Carriers.MCC, mcc);
                values.put(Telephony.Carriers.MNC, mnc);
                values.put(Telephony.Carriers.NUMERIC, mcc + mnc);

                if (cursor.getCount() == 0) {
                    Uri row = mContext.getContentResolver().insert(APN_URI, values);
                    if (row != null) {
                        UtkLog.d(LOG_TAG, " insert a new record");
                    } else {
                        UtkLog.d(LOG_TAG, " fail to insert a new record");
                    }
                } else {
                    UtkLog.d(LOG_TAG, " update " + apnType + " record");
                    mContext.getContentResolver().update(APN_URI, values, selection, null);
                }
                cursor.close();
            }
        }
    }
}


