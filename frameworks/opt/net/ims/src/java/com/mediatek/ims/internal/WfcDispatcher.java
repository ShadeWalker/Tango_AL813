package com.mediatek.ims.internal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import com.mediatek.ims.ImsAdapter;
import com.mediatek.ims.ImsAdapter.VaSocketIO;
import com.mediatek.ims.ImsAdapter.VaEvent;
import com.mediatek.ims.ImsEventDispatcher;
import com.mediatek.ims.VaConstants;
import com.mediatek.xlog.Xlog;

import android.net.NetworkInfo;
//import android.telephony.SubInfoRecord;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.Rlog;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

import com.android.internal.telephony.DctConstants;

import com.mediatek.internal.telephony.DedicateBearerProperties;
import com.mediatek.internal.telephony.DefaultBearerConfig;
import com.mediatek.internal.telephony.PcscfInfo;
import com.mediatek.internal.telephony.QosStatus;
import com.mediatek.internal.telephony.TftStatus;
import com.mediatek.internal.telephony.PacketFilterInfo;
import com.mediatek.internal.telephony.PcscfAddr;

import com.android.ims.mo.ImsLboPcscf;
import com.android.ims.ImsConfig;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;

import android.os.Looper;
import android.os.Handler;
import android.os.Message;

import java.util.List;
import java.util.ArrayList;

import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import static android.net.ConnectivityManager.TYPE_NONE;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.rns.RnsManager;
import android.net.ConnectivityManager;
import android.telephony.gsm.GsmCellLocation;
public class WfcDispatcher {

    private boolean mIsBroadcastReceiverRegistered;
    private DataDispatcherUtil mDataDispatcherUtil;
    private boolean mIsEnable;
    private Context mContext;
    private VaSocketIO mSocket;
    private static WfcDispatcher mInstance;
    private static boolean sIsHandoverProgress;
    private final String TAG = "WfcDispatcher";
    private static final int IMC_CONCATENATED_MSG_TYPE_MODIFICATION = 2;
    DedicateBearerProperties mDefaultBearerProperties = null;
    List<DedicateBearerProperties> mBearerProp = null ;
    private static boolean sto3gpp = false;

    /*
     * WfcDispatcher - constructor Called from ImsEventDispatcher class
     */
    public WfcDispatcher(Context context, VaSocketIO IO) {
        log("WfcDispatcher creating ...");
        mContext = context;
        mSocket = IO;
        mDataDispatcherUtil = new DataDispatcherUtil();
        mInstance = this;
    }

    public static WfcDispatcher getInstance() {
        return mInstance;
    }

    /*
     * handle the handover start event from ConnectivityService provide the
     * event - whether to LTE or WiFi - to IMCB *********************Through
     * VaEvent*******************
     */

    public void handleHandoverStarted(int network) {
        int msgId = VaConstants.MSG_ID_HANDOVER_START;
        boolean to3gpp = false;
        if (network == ConnectivityManager.TYPE_MOBILE_IMS) {
            to3gpp = true;
            ImsAdapter.VaEvent event = mDataDispatcherUtil
                    .composeHandoverStartVaEvent(msgId, to3gpp);
            /* send handover start message to IMCB */
            log("Handover start event sent to IMCB : to3gpp" + to3gpp);
            sendVaEvent(event);
            sIsHandoverProgress = true;
            sto3gpp = to3gpp;

        } else if (network == ConnectivityManager.TYPE_EPDG) {
            log("handleHandoverStarted network ConnectivityManager.TYPE_EPDG "
                    + network);

            DedicateBearerProperties defaultBear, tempBearer;// mDefaultBearerProperties
            List<DedicateBearerProperties> dedicateBearer = null;

            try {
                tempBearer = (DataDispatcherUtil.getDefaultBearerProperties(
                        PhoneConstants.APN_TYPE_IMS, DataDispatcher.sPhoneId));
                if (tempBearer != null)
                    mDefaultBearerProperties = (DedicateBearerProperties) (tempBearer
                            .clone());
                else {
                    log("Bearer is null");
                    return;
                }
            } catch (CloneNotSupportedException ex) {
                log("CloneNotSupportedException ex " + ex);
            }

            if (mDefaultBearerProperties != null) {
                dedicateBearer = DataDispatcherUtil
                        .getVoLTEConntectedDedicateBearer(mDefaultBearerProperties.defaultCid);

            }
            // props.concatenateBearers.size();
            log("dedicateBearer " + dedicateBearer);

            // Adding the default bearer
            try {
                for (int i = 0; i < dedicateBearer.size(); i++) {
                    if (dedicateBearer.get(i).cid != dedicateBearer.get(i).defaultCid) {
                        // Dedicate bearer
                        try {
                            tempBearer = dedicateBearer.get(i);
                            if (tempBearer != null) {
                                defaultBear = (DedicateBearerProperties) (tempBearer
                                        .clone());
                                mDefaultBearerProperties.concatenateBearers
                                        .add(defaultBear);
                            } else
                                log("Dedicated Bearer is null");

                            log("handleHandoverStarted dedicateBearer.cid "
                                    + dedicateBearer.get(i).cid
                                    + "dedicateBearer.defaultCid "
                                    + dedicateBearer.get(i).defaultCid
                                    + "index i=" + i);
                        } catch (CloneNotSupportedException ex) {
                            log("CloneNotSupportedException ex " + ex);
                        }
                    }
                    log("handleHandoverStarted dedicateBearer.get(" + i + ") "
                            + dedicateBearer.get(i));
                }
            } catch (Exception ex) {
                log("Exception ex" + ex);
            }
            log("handleHandoverStarted mDefaultBearerProperties ="
                    + mDefaultBearerProperties);
            log("handleHandoverStarted  mBearerProp " + mBearerProp);
            to3gpp = false;
            ImsAdapter.VaEvent event = mDataDispatcherUtil
                    .composeHandoverStartVaEvent(msgId, to3gpp);
            /* send handover start message to IMCB */
            log("Handover start event sent to IMCB : to3gpp" + to3gpp);
            sendVaEvent(event);
            sIsHandoverProgress = true;
            sto3gpp = to3gpp;

        } else {
            log("Handover start event:: Unhandled case");
            return;
        }

    }

    /*
     * handle the handover done event from ConnectivityServiceThis may have
     * failed or successed - result - true/false
     */
    public void handleHandoverDone(int network, boolean result) {
        int msgId = VaConstants.MSG_ID_HANDOVER_DONE;
        int notifyMsg;
        ImsAdapter.VaEvent eventModify;
        boolean to3gpp = false;
        TelephonyManager phone = (TelephonyManager) mContext
                .getSystemService(Context.TELEPHONY_SERVICE);
        final int phoneType = phone.getPhoneType();
        if(sIsHandoverProgress == false){
            return;
            //return if handover was not in progress
        }

        if (network == ConnectivityManager.TYPE_MOBILE_IMS) {
            to3gpp = true;
        } else if (network == ConnectivityManager.TYPE_EPDG) {
            to3gpp = false;
        } else {
            log("Handover done event:: Unhandled case");
            return;
        }
        
        if (result == true) {
            log("Handover success set Rat = " + network);
            ImsAdapter.setRatType(network);

        }

        ImsAdapter.VaEvent event = mDataDispatcherUtil
                .composeHandoverDoneVaEvent(msgId, to3gpp, result);
        log("handleHandovereDone to3gpp: " + to3gpp + "result: " + result);
        log("handleHandoverDone network = " + network + " result =" + result);
        if ((network == ConnectivityManager.TYPE_EPDG) && (result == true)) {
            // TODO: -
            /*
             * Handover done from LTE ==> WiFi :: create a dummy event for
             * PDN_UPDATE [TFT= match all ,QCI=255, BW=unlimit] Send PDN_UPDATE
             * to IMCB, before sending HANOVER_DONE This is beacuse WiFi does
             * not have any QCI defined - we create with pre-provisioned values
             * and sent to IMCB. In case of LTE, the bearer information will
             * come through the normal channel.
             */
            log("handoverdone to ePDG and pass");
            notifyMsg = VaConstants.MSG_ID_NOTIFY_PDN_UPDATE;
            eventModify = new ImsAdapter.VaEvent(0, notifyMsg);
            DataDispatcherUtil.pdnModifyInfo(eventModify, to3gpp, mContext,
                    mDefaultBearerProperties);
            Xlog.d(TAG,
                    "Handover from LTE->WiFi - PDN update message sent with modified information");
            log("Handover from LTE->WiFi - PDN update message sent with modified information");
            sendVaEvent(eventModify);
            DataDispatcherUtil.clearVoLTEConnectedDedicatedBearer();
        } else if ((network == ConnectivityManager.TYPE_MOBILE_IMS)
                && (result == true)) {
            log("handoverdone to Mobile and pass");
            // mDefaultBearerProperties = DataDispatcherUtil.mWifiBearerProp;
            // After Edward patch

            DedicateBearerProperties defaultBear, tempBearer;// mDefaultBearerProperties
            List<DedicateBearerProperties> dedicateBearer = null;

            try {
                tempBearer = (DataDispatcherUtil.getDefaultBearerProperties(
                        PhoneConstants.APN_TYPE_IMS, DataDispatcher.sPhoneId));
                if (tempBearer != null) {
                    mDefaultBearerProperties = (DedicateBearerProperties) (tempBearer
                            .clone());
                }
                else
                    log("Bearer is null");
            } catch (CloneNotSupportedException ex) {
                log("CloneNotSupportedException ex " + ex);
            }

            if (mDefaultBearerProperties != null) {
                dedicateBearer = DataDispatcherUtil
                        .getVoLTEConntectedDedicateBearer(mDefaultBearerProperties.defaultCid);
            }
            // props.concatenateBearers.size();
            log("dedicateBearer " + dedicateBearer);

            // Adding the default bearer
            try {
                for (int i = 0; i < dedicateBearer.size(); i++) {
                    if (dedicateBearer.get(i).cid != dedicateBearer.get(i).defaultCid) {
                        // Dedicate bearer
                        try {
                            tempBearer = dedicateBearer.get(i);
                            if (tempBearer != null) {
                                defaultBear = (DedicateBearerProperties) (tempBearer
                                        .clone());
                                mDefaultBearerProperties.concatenateBearers
                                        .add(defaultBear);
                            } else
                                log("Dedicated Bearer is null");

                            log("handleHandoverDone dedicateBearer.cid "
                                    + dedicateBearer.get(i).cid
                                    + "dedicateBearer.defaultCid "
                                    + dedicateBearer.get(i).defaultCid
                                    + "index i=" + i);
                        } catch (CloneNotSupportedException ex) {
                            log("CloneNotSupportedException ex " + ex);
                        }
                    }
                    log("handleHandoverDone dedicateBearer.get(" + i + ") "
                            + dedicateBearer.get(i));
                }
            } catch (Exception ex) {
                log("Exception ex" + ex);
            }

            log("handleHandoverDone mDefaultBearerProperties ="
                    + mDefaultBearerProperties);
            log("handleHandoverDone  mBearerProp " + mBearerProp);
            // After Edward patch

            // mDefaultBearerProperties =
            // DataDispatcherUtil.getDefaultBearerProperties(PhoneConstants.APN_TYPE_IMS,0);
            log("handoverdone to Mobile and pass mDefaultBearerProperties"
                    + mDefaultBearerProperties);
            notifyMsg = VaConstants.MSG_ID_NOTIFY_PDN_UPDATE;
            eventModify = new ImsAdapter.VaEvent(0, notifyMsg);
            DataDispatcherUtil.pdnModifyInfo(eventModify, to3gpp, mContext,
                    mDefaultBearerProperties);
            Xlog.d(TAG,
                    "Handover from LTE->WiFi - PDN update message sent with modified information");
            sendVaEvent(eventModify);
        } else if ((network == ConnectivityManager.TYPE_EPDG)
                && (result == false)) {
            // send the pending dedicate bearer deact message
            DataDispatcher.getInstance().sendVaEventIfPending();
            log("handoverdone : - network =" + network + "result =" + result
                    + "sendVaEventIfPending");
        } else {
            log("handoverdone : unhandled scenario for handover - network ="
                    + network + "result =" + result);
        }

        log("Handover done event sent to IMCB ");
        sendVaEvent(event);
        sIsHandoverProgress = false;
        mDefaultBearerProperties = null;
        mBearerProp = null;
    }

    /* sendVaEvent - write to socket for IMCB */
    private void sendVaEvent(VaEvent event) {
       Xlog.d(TAG, "WfcDispatcher send event [" + event.getRequestID() + ", "
                + event.getDataLen() + "]");
        mSocket.writeEvent(event);
    }


    public static boolean isHandoverInProgress(){
        //log("sIsHandoverProgress" + sIsHandoverProgress);
        return sIsHandoverProgress;
    }

    public static boolean handoverDirection(){
        return sto3gpp;
    }
    
    protected void log(String s) {
        Rlog.d(TAG, s);
    }

}
