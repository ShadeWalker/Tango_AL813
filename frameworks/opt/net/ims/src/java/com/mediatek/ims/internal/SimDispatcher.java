package com.mediatek.ims.internal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;

import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.mediatek.ims.ImsAdapter;
import com.mediatek.ims.ImsAdapter.VaSocketIO;
import com.mediatek.ims.ImsAdapter.VaEvent;
import com.mediatek.ims.ImsEventDispatcher;
import com.mediatek.ims.VaConstants;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.IPhoneSubInfo;

import com.mediatek.internal.telephony.ITelephonyEx;

/**
 * {@hide}
 *
 * Implement a SIM dispatcher to handle the request from indicated message.
 *
 */
public class SimDispatcher extends Handler implements ImsEventDispatcher.VaEventDispatcher {
    private static final String TAG = "[SimDispatcher]";

    private static final Object mLock = new Object();

    private static final int STATE_USIM_TYPE_INVALID        = 0;
    private static final int STATE_USIM_TYPE_VALID          = 1;
    private static final int STATE_ISIM_TYPE_UNKNOWN        = 2;
    private static final int STATE_ISIM_TYPE_INVALID        = 3;
    private static final int STATE_ISIM_TYPE_VALID          = 4;

    private static final int READ_USIM_COMMAND_IMSI         = 0;
    private static final int READ_USIM_COMMAND_PSISMSC      = 1;
    private static final int READ_USIM_COMMAND_SMSP         = 2;

    private static final int READ_ISIM_COMMAND_IMPI         = 0;
    private static final int READ_ISIM_COMMAND_IMPU         = 1;
    private static final int READ_ISIM_COMMAND_DOMAIN_NAME  = 2;
    private static final int READ_ISIM_COMMAND_PSISMSC      = 3;
    private static final int READ_ISIM_SERVICE_TABLE        = 4;

    private static final int READ_USIM_COMMAND_DATA_LENGTH  = 256;
    private static final int READ_ISIM_COMMAND_DATA_LENGTH  = 256;
    private static final int READ_ISIM_COMMAND_DATA_NUM     = 5;
    private static final int USIM_AUTH_IMS_AKA_COMMAND_RES_LENGTH = 256;

    private Context mContext;
    private VaSocketIO mSocket;
    private String[] mSimState = new String[TelephonyManager.getDefault().getSimCount()];
    private String[] mIsimState = new String[TelephonyManager.getDefault().getSimCount()];

    /**
     * SimDispatcher constructor.
     *
     * @param context the indicated context
     * @param io socket IO
     *
     */
    public SimDispatcher(Context context, VaSocketIO io) {
        mContext = context;
        mSocket = io;

        log("creating SimDispatcher...");

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        intentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED_MULTI_APPLICATION);
        mContext.registerReceiver(mReceiver, intentFilter);
    }

    /**
     * enableRequest API - currently, do nothing.
     *
     */
    public void enableRequest() {
        log("enableRequest()");
    }

    /**
     * disableRequest API - currently, do nothing.
     *
     */
    public void disableRequest() {
        log("disableRequest()");
    }

    @Override
    public void handleMessage(Message msg) {
        synchronized (mLock) {
            AsyncResult ar = (AsyncResult) msg.obj;
            switch (msg.what) {
                default:
                    log(" Unknown Event " + msg.what);
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int slotId = 0;
            int phoneId = 0;
            int simType = 0;
            int eventId = 0;
            boolean needToNotify = false;

            if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                slotId = intent.getIntExtra(PhoneConstants.SLOT_KEY, 0);
                phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY, 0);
                mSimState[slotId] = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                log("[BroadcastReceiver] receving ACTION_SIM_STATE_CHANGED" +
                        " " + mSimState[slotId]);
                if (mSimState[slotId].equals(IccCardConstants.INTENT_VALUE_ICC_LOADED)) {
                    eventId = VaConstants.MSG_ID_NOTIFY_SIM_READY;
                    simType = STATE_USIM_TYPE_VALID;
                    needToNotify = true;
                } else if (mSimState[slotId].equals(IccCardConstants.INTENT_VALUE_ICC_ABSENT)) {
                    eventId = VaConstants.MSG_ID_NOTIFY_SIM_ERROR;
                    simType = STATE_USIM_TYPE_INVALID;
                    needToNotify = true;
                }
            } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED_MULTI_APPLICATION)) {
                slotId = intent.getIntExtra(PhoneConstants.SLOT_KEY, 0);
                phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY, 0);
                mIsimState[slotId] = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                log("[BroadcastReceiver] receving ACTION_SIM_STATE_CHANGED_MULTI_APPLICATION" +
                        " " + mIsimState[slotId]);
                if (mIsimState[slotId].equals(IccCardConstants.INTENT_VALUE_ICC_LOADED)) {
                    eventId = VaConstants.MSG_ID_NOTIFY_SIM_READY;
                    simType = STATE_ISIM_TYPE_VALID;
                    needToNotify = true;
                } else if (mIsimState[slotId].equals(IccCardConstants.INTENT_VALUE_ICC_ABSENT)) {
                    eventId = VaConstants.MSG_ID_NOTIFY_SIM_READY;
                    simType = STATE_ISIM_TYPE_INVALID;
                    needToNotify = true;
                }
            }

            log("[BroadcastReceiver] eventId:" + eventId +
                    ", phoneId: " + phoneId +
                    ", simType:" + simType +
                    ", needToNotify:" + needToNotify);

            if (needToNotify) {
                ImsAdapter.VaEvent event = new ImsAdapter.VaEvent(phoneId, eventId);

                //SIM state
                event.putByte(simType);

                //Session ID
                event.putByte(0);

                //Pad
                event.putByte(0);
                event.putByte(0);

                log("[BroadcastReceiver] Notify VA for SIM state.");

                // send the event to va
                mSocket.writeEvent(event);
            }
        }
    };

    /**
     * vaEventCallback API - handle request from socket interface.
     *
     * @param event event object from Imsadpater
     *
     */
    public void vaEventCallback(VaEvent event) {
        try {
            int transactionId;
            int requestId = event.getRequestID();
            int phoneId = event.getPhoneId();
            //FIXME: asssue phone id is equals to subscription id
            int slotId = phoneId;
            log("[vaEventCallback]requestId = " + requestId + ", phoneId = " + phoneId);

            switch (requestId) {
                case VaConstants.MSG_ID_REQUEST_DO_AKA_AUTHENTICATION:
                    int randLen = 0;
                    int autnLen = 0;
                    int family = 0;
                    /*--- Step1. parse request data --- */
                    transactionId = event.getByte();
                    randLen = event.getByte();
                    autnLen = event.getByte();
                    // isIsimPrefer: 0 for USIM prefer, 1 for ISIM prefer
                    // UiccController.APP_FAM_3GPP =  1; //SIM/USIM
                    // UiccController.APP_FAM_3GPP2 = 2; //RUIM/CSIM
                    // UiccController.APP_FAM_IMS   = 3; //ISIM
                    family = ((event.getByte() == 1) ? 3 : 1);

                    log("[vaEventCallback]transaction_id: " + transactionId);
                    log("[vaEventCallback]Rand Len =  " + randLen + "Autn Len = " + autnLen);

                    byte byteRand[] = event.getBytes(32);
                    byte byteAutn[] = event.getBytes(32);

                    byte rand[] = new byte[randLen];
                    System.arraycopy(byteRand, 0, rand, 0, ((randLen < 32) ? randLen : 32));
                    byte autn[] = new byte[autnLen];
                    System.arraycopy(byteAutn, 0, autn, 0, ((autnLen < 32) ? autnLen : 32));

                    log("[vaEventCallback]SIM auth:RAND = " + rand + ", AUTN = " + autn);

                    /*--- Step2. do authentication ---*/
                    // Do authentication by ITelephony interface
                    // Return data: payload + sw1 + sw2 (need to notify user)
                    // FIXME: ANR issue?
                    byte[] response = null;

                    try {
                        response = getITelephonyEx().simAkaAuthentication(
                                slotId, family, rand, autn);
                    } catch (RemoteException ex) {
                        ex.printStackTrace();
                    } catch (NullPointerException ex) {
                        // This could happen before phone restarts due to crashing
                        ex.printStackTrace();
                    }

                    /*--- Step3. send response data ---*/
                    VaEvent resEvent = new ImsAdapter.VaEvent(
                            phoneId, VaConstants.MSG_ID_RESPONSE_DO_AKA_AUTHENTICATION);

                    //transaction_id
                    resEvent.putByte((byte) transactionId);

                    //isSuccess
                    resEvent.putByte((response == null) ? 0 : 1);

                    //Pad[2]
                    resEvent.putByte(0);
                    resEvent.putByte(0);

                    //Data
                    byte resData[] = new byte[USIM_AUTH_IMS_AKA_COMMAND_RES_LENGTH];
                    if (response != null) {
                        System.arraycopy(response, 0, resData, 0, response.length);
                        resEvent.putBytes(resData);
                        log("[vaEventCallback]AKA resData = " + bytesToHexString(resData));
                    } else {
                        resEvent.putBytes(resData);
                    }

                    mSocket.writeEvent(resEvent);

                    log("[vaEventCallback]DO_AKA_AUTHENTICATION response is " + resData);
                    break;

                case VaConstants.MSG_ID_REQUEST_READ_USIM_FILE:
                    transactionId = event.getByte();
                    readUsimData(phoneId, event.getByte(), transactionId);
                    break;

                case VaConstants.MSG_ID_REQUEST_READ_ISIM_FILE:
                    transactionId = event.getByte();
                    readIsimData(phoneId, event.getByte(), transactionId);
                    break;

                case VaConstants.MSG_ID_REQUEST_QUERY_SIM_STATUS:
                    VaEvent responseEvent = new ImsAdapter.VaEvent(
                            phoneId, VaConstants.MSG_ID_RESPONSE_QUERY_SIM_STATUS);

                    transactionId = event.getByte();
                    log("[vaEventCallback]transaction_id: " + transactionId);

                    //transaction_id
                    responseEvent.putByte(transactionId);

                    log("[vaEventCallback]SimState: " + mSimState[slotId] +
                            ", ISIMState: " +  mIsimState[slotId]);
                    //USIM Type
                    if (mSimState[slotId].equals(IccCardConstants.INTENT_VALUE_ICC_LOADED)) {
                        responseEvent.putByte(STATE_USIM_TYPE_VALID);
                    } else {
                        responseEvent.putByte(STATE_USIM_TYPE_INVALID);
                    }

                    //ISIM Type
                    if (mIsimState[slotId].equals(IccCardConstants.INTENT_VALUE_ICC_LOADED)) {
                        responseEvent.putByte(STATE_ISIM_TYPE_VALID);
                    } else if (mIsimState[slotId].equals(IccCardConstants.INTENT_VALUE_ICC_ABSENT)) {
                        responseEvent.putByte(STATE_ISIM_TYPE_INVALID);
                    } else {
                        responseEvent.putByte(STATE_ISIM_TYPE_UNKNOWN);
                    }

                    //Session ID
                    responseEvent.putByte(0);

                    //Pad
                    responseEvent.putByte(0);

                    //send the event to va
                    mSocket.writeEvent(responseEvent);

                    break;
                default:
                    log("Unknown request: " + requestId);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void readUsimData(int phoneId, int type, int transactionId) {
        VaEvent event = new ImsAdapter.VaEvent(phoneId, VaConstants.MSG_ID_RESPONSE_READ_USIM_FILE);
        byte resData[] = new byte[READ_USIM_COMMAND_DATA_LENGTH];
        int subId = SubscriptionManager.getSubIdUsingPhoneId(phoneId);
        log("[readUsimData]transaction_id = " + transactionId +
                ", phoneId = " + phoneId +
                ", subId = " + subId +
                ", type = " + type);

        switch (type) {
            case READ_USIM_COMMAND_IMSI:
                String imsi;
                int mncLength = 0;
                imsi = TelephonyManager.getDefault().getSubscriberId(subId);
                log("[readUsimData]imsi = " + imsi);

                try {
                    mncLength = getSubscriberInfo().getMncLengthForSubscriber(subId);
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                } catch (NullPointerException ex) {
                    // This could happen before phone restarts due to crashing
                    ex.printStackTrace();
                }

                log("[readUsimData]MNC length = " + mncLength);

                if (imsi == null || mncLength <= 0 || mncLength > 15) {
                    readUsimDataFail(phoneId, type, transactionId);
                    return;
                }

                //transaction_id
                event.putByte(transactionId);

                //is_success
                event.putByte(1);

                //data_type
                event.putByte(type);

                //num_of_data
                event.putByte(1);

                //mncLength
                event.putByte((byte) mncLength);

                //Pad
                event.putByte(0);
                event.putByte(0);
                event.putByte(0);

                //sim_usim_data
                //len
                log("[readUsimData]imsi.length = " + imsi.length());
                event.putInt(imsi.length());
                //data
                event.putString(imsi, READ_USIM_COMMAND_DATA_LENGTH);

                break;
            case READ_USIM_COMMAND_PSISMSC:
                byte efPsismsc[] = null;

                try {
                    efPsismsc = getSubscriberInfo().getUsimPsismscForSubscriber(subId);
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                } catch (NullPointerException ex) {
                    // This could happen before phone restarts due to crashing
                    ex.printStackTrace();
                }

                if (efPsismsc == null) {
                    readUsimDataFail(phoneId, type, transactionId);
                    return;
                }

                log("[readUsimData]EF_PSISMSC = " + bytesToHexString(efPsismsc));

                //transaction_id
                event.putByte(transactionId);

                //is_success
                event.putByte(1);

                //data_type
                event.putByte(type);

                //num_of_data
                event.putByte(1);

                //mncLength
                event.putByte(0);

                //Pad
                event.putByte(0);
                event.putByte(0);
                event.putByte(0);

                //sim_usim_data
                //len
                int psismscLen = ((efPsismsc.length > READ_USIM_COMMAND_DATA_LENGTH)
                        ? READ_USIM_COMMAND_DATA_LENGTH : efPsismsc.length);
                log("[readUsimData]efPsismsc.length = " + efPsismsc.length
                        + ", max len = " + READ_USIM_COMMAND_DATA_LENGTH);
                event.putInt(psismscLen);
                //data
                System.arraycopy(efPsismsc, 0, resData, 0, psismscLen);
                event.putBytes(resData);

                break;
            case READ_USIM_COMMAND_SMSP:
                byte efSmsp[] = null;

                try {
                    efSmsp = getSubscriberInfo().getUsimSmspForSubscriber(subId);
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                } catch (NullPointerException ex) {
                    // This could happen before phone restarts due to crashing
                    ex.printStackTrace();
                }

                if (efSmsp == null) {
                    readUsimDataFail(phoneId, type, transactionId);
                    return;
                }

                log("[readUsimData]EF_SMSP = " + bytesToHexString(efSmsp));

                //transaction_id
                event.putByte(transactionId);

                //is_success
                event.putByte(1);

                //data_type
                event.putByte(type);

                //num_of_data
                event.putByte(1);

                //mncLength
                event.putByte(0);

                //Pad
                event.putByte(0);
                event.putByte(0);
                event.putByte(0);

                //sim_usim_data
                //len
                int smspLen = ((efSmsp.length > READ_USIM_COMMAND_DATA_LENGTH)
                        ? READ_USIM_COMMAND_DATA_LENGTH : efSmsp.length);
                log("[readUsimData]efSmsp.length = " + efSmsp.length
                        + ", max len = " + READ_USIM_COMMAND_DATA_LENGTH);
                event.putInt(smspLen);
                //data
                System.arraycopy(efSmsp, 0, resData, 0, smspLen);
                event.putBytes(resData);
                break;
            default:
                log("[readUsimData]unknown type = " + type);
                break;
            }

        // send the event to va
        mSocket.writeEvent(event);
    }

    private void readUsimDataFail(int phoneId, int type, int transactionId) {
        VaEvent event = new ImsAdapter.VaEvent(phoneId, VaConstants.MSG_ID_RESPONSE_READ_USIM_FILE);
        byte pad[] = new byte[READ_USIM_COMMAND_DATA_LENGTH];
        event.putByte(transactionId);

        //is_success
        event.putByte(0);

        //data_type
        event.putByte(type);

        //num_of_data
        event.putByte(0);

        //mncLength
        event.putByte(0);

        //Pad
        event.putByte(0);
        event.putByte(0);
        event.putByte(0);

        //sim_usim_data
        //len
        event.putInt(0);
        //data
        event.putBytes(pad);

        // send the event to va
        mSocket.writeEvent(event);

        log("[readUsimDataFail]transactionId = " + transactionId +
            ", type = " + type +
            ", phoneId =" + phoneId);
    }


    private void readIsimData(int phoneId, int type, int transactionId) {
        VaEvent event = new ImsAdapter.VaEvent(phoneId, VaConstants.MSG_ID_RESPONSE_READ_ISIM_FILE);
        byte resData[] = new byte[READ_ISIM_COMMAND_DATA_LENGTH];
        int subId = SubscriptionManager.getSubIdUsingPhoneId(phoneId);
        log("[readIsimData]transaction_id = " + transactionId +
                ", phoneId = " + phoneId +
                ", subId = " + subId +
                ", type = " + type);

        switch (type) {
            case READ_ISIM_COMMAND_IMPI:
                String impi = "";

                try {
                    impi = getSubscriberInfo().getIsimImpiForSubscriber(subId);
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                } catch (NullPointerException ex) {
                    // This could happen before phone restarts due to crashing
                    ex.printStackTrace();
                }

                log("[readIsimData]impi = " + impi);

                if (impi == null) {
                    readIsimDataFail(phoneId, type, transactionId);
                    return;
                }

                //transaction_id
                event.putByte(transactionId);

                //is_success
                event.putByte(1);

                //data_type
                event.putByte(type);

                //num_of_data
                event.putByte(1);

                //ims_isim_data
                //len
                log("[readIsimData]impi.length = " + impi.length());
                event.putInt(((impi.length() > READ_ISIM_COMMAND_DATA_LENGTH)
                        ? READ_ISIM_COMMAND_DATA_LENGTH : impi.length()));
                //data
                event.putString(impi, READ_ISIM_COMMAND_DATA_LENGTH);

                for (int i = 0; i < (READ_ISIM_COMMAND_DATA_NUM - 1); i++) {
                    //ims_isim_data[n], Pad
                    //len
                    event.putInt(0);
                    //data
                    event.putBytes(new byte[READ_ISIM_COMMAND_DATA_LENGTH]);
                }

                break;

            case READ_ISIM_COMMAND_IMPU:
                String[] impu = null;

                try {
                    impu = getSubscriberInfo().getIsimImpuForSubscriber(subId);
                    log("[readIsimData]impu = " + impu);
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                } catch (NullPointerException ex) {
                    // This could happen before phone restarts due to crashing
                    ex.printStackTrace();
                }

                if (impu == null) {
                    readIsimDataFail(phoneId, type, transactionId);
                    return;
                }

                //transaction_id
                event.putByte(transactionId);

                //is_success
                event.putByte(1);

                //data_type
                event.putByte(type);

                //num_of_data
                event.putByte(((impu.length > 5) ? 5 : impu.length));

                //ims_isim_data
                for (int i = 0; i < READ_ISIM_COMMAND_DATA_NUM; i++) {
                    if (i < impu.length && impu[i] != null) {
                        //len
                        log("[readIsimData]impu[" + i + "].length = " + impu[i].length() + ", " + impu[i]);
                        event.putInt(((impu[i].length() > READ_ISIM_COMMAND_DATA_LENGTH)
                                ? READ_ISIM_COMMAND_DATA_LENGTH : impu[i].length()));
                        //data
                        event.putString(impu[i], READ_ISIM_COMMAND_DATA_LENGTH);
                    } else {
                        //len
                        event.putInt(0);
                        //data
                        event.putBytes(new byte[READ_ISIM_COMMAND_DATA_LENGTH]);
                    }
                }

                break;

            case READ_ISIM_COMMAND_DOMAIN_NAME:
                String domain = "";

                try {
                    domain = getSubscriberInfo().getIsimDomainForSubscriber(subId);
                    log("[readIsimData]domain = " + domain);
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                } catch (NullPointerException ex) {
                    // This could happen before phone restarts due to crashing
                    ex.printStackTrace();
                }

                if (domain == null) {
                    readIsimDataFail(phoneId, type, transactionId);
                    return;
                }

                //transaction_id
                event.putByte(transactionId);

                //is_success
                event.putByte(1);

                //data_type
                event.putByte(type);

                //num_of_data
                event.putByte(1);

                //ims_isim_data
                //len
                log("[readIsimData]domain.length = " + domain.length());
                event.putInt(((domain.length() > READ_ISIM_COMMAND_DATA_LENGTH)
                        ? READ_ISIM_COMMAND_DATA_LENGTH : domain.length()));
                //data
                event.putString(domain, READ_ISIM_COMMAND_DATA_LENGTH);

                for (int i = 0; i < (READ_ISIM_COMMAND_DATA_NUM - 1); i++) {
                    //ims_isim_data[n], Pad
                    //len
                    event.putInt(0);
                    //data
                    event.putBytes(new byte[READ_ISIM_COMMAND_DATA_LENGTH]);
                }

                break;

            case READ_ISIM_COMMAND_PSISMSC:
                byte efPsismsc[] = null;

                try {
                    efPsismsc = getSubscriberInfo().getIsimPsismscForSubscriber(subId);
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                } catch (NullPointerException ex) {
                    // This could happen before phone restarts due to crashing
                    ex.printStackTrace();
                }

                if (efPsismsc == null) {
                    readIsimDataFail(phoneId, type, transactionId);
                    return;
                }

                log("[readIsimData]EF_PSISMSC = " + bytesToHexString(efPsismsc));

                //transaction_id
                event.putByte(transactionId);

                //is_success
                event.putByte(1);

                //data_type
                event.putByte(type);

                //num_of_data
                event.putByte(1);

                //ims_isim_data
                //len
                int psismscLen = ((efPsismsc.length > READ_ISIM_COMMAND_DATA_LENGTH)
                        ? READ_ISIM_COMMAND_DATA_LENGTH : efPsismsc.length);
                log("[readIsimData]efPsismsc.length = " + efPsismsc.length
                        + ", max len = " + READ_ISIM_COMMAND_DATA_LENGTH);
                event.putInt(psismscLen);
                //data
                System.arraycopy(efPsismsc, 0, resData, 0, psismscLen);
                event.putBytes(resData);

                for (int i = 0; i < (READ_ISIM_COMMAND_DATA_NUM - 1); i++) {
                    //ims_isim_data[n], Pad
                    //len
                    event.putInt(0);
                    //data
                    event.putBytes(new byte[READ_ISIM_COMMAND_DATA_LENGTH]);
                }

                break;
            case READ_ISIM_SERVICE_TABLE:
                String ist = "";

                try {
                    ist = getSubscriberInfo().getIsimIstForSubscriber(subId);
                    log("[readIsimData]ist = " + ist);
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                } catch (NullPointerException ex) {
                    // This could happen before phone restarts due to crashing
                    ex.printStackTrace();
                }

                if (ist == null) {
                    readIsimDataFail(phoneId, type, transactionId);
                    return;
                }

                byte istBytes[] = hexStringToBytes(ist);

                //transaction_id
                event.putByte(transactionId);

                //is_success
                event.putByte(1);

                //data_type
                event.putByte(type);

                //num_of_data
                event.putByte(1);

                //ims_isim_data
                //len
                int istLen = ((istBytes.length > READ_ISIM_COMMAND_DATA_LENGTH)
                        ? READ_ISIM_COMMAND_DATA_LENGTH : istBytes.length);
                log( "[readIsimData]istBytes.length = " + istBytes.length
                        + ", max len = " + READ_ISIM_COMMAND_DATA_LENGTH);
                event.putInt(istLen);
                //data
                System.arraycopy(istBytes, 0, resData, 0, istLen);
                event.putBytes(resData);

                for (int i = 0; i < (READ_ISIM_COMMAND_DATA_NUM - 1); i++) {
                    //ims_isim_data[n], Pad
                    //len
                    event.putInt(0);
                    //data
                    event.putBytes(new byte[READ_ISIM_COMMAND_DATA_LENGTH]);
                }

                break;
            default:
                log("[readIsimData]readIsimData unknown type = " + type);
                break;
            }

        // send the event to va
        mSocket.writeEvent(event);
    }

    private void readIsimDataFail(int phoneId, int type, int transactionId) {
        VaEvent event = new ImsAdapter.VaEvent(phoneId, VaConstants.MSG_ID_RESPONSE_READ_ISIM_FILE);
        byte pad[] = new byte[READ_ISIM_COMMAND_DATA_LENGTH];

        //transaction_id
        event.putByte(transactionId);

        //is_success
        event.putByte(0);

        //data_type
        event.putByte(type);

        //num_of_data
        event.putByte(0);

        //ims_isim_data
        for (int i = 0; i < READ_ISIM_COMMAND_DATA_NUM; i++) {
            //len
            event.putInt(0);
            //data
            event.putBytes(pad);
        }

        // send the event to va
        mSocket.writeEvent(event);
        log("[readIsimDataFail]transactionId = " + transactionId +
            ", type = " + type +
            ", phoneId =" + phoneId);
    }


    protected void log(String s) {
        Rlog.d(TAG, s);
    }

    private ITelephonyEx getITelephonyEx() {
        return ITelephonyEx.Stub.asInterface(
                ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
    }

    private IPhoneSubInfo getSubscriberInfo() {
        // get it each time because that process crashes a lot
        return IPhoneSubInfo.Stub.asInterface(ServiceManager.getService("iphonesubinfo"));
    }

    private int hexCharToInt(char c) {
        if (c >= '0' && c <= '9') {
            return (c - '0');
        }
        if (c >= 'A' && c <= 'F') {
            return (c - 'A' + 10);
        }
        if (c >= 'a' && c <= 'f') {
            return (c - 'a' + 10);
        }

        throw new RuntimeException ("invalid hex char '" + c + "'");
    }

    /**
     * Converts a hex String to a byte array.
     *
     * @param s A string of hexadecimal characters, must be an even number of
     *          chars long
     *
     * @return byte array representation
     *
     * @throws RuntimeException on invalid format
     */
    private byte[] hexStringToBytes(String s) {
        byte[] ret;

        if (s == null) {
            return null;
        }

        int sz = s.length();

        ret = new byte[sz/2];

        for (int i=0 ; i <sz ; i+=2) {
            ret[i/2] = (byte) ((hexCharToInt(s.charAt(i)) << 4)
                                | hexCharToInt(s.charAt(i+1)));
        }

        return ret;
    }

    /**
     * Converts a byte array into a String of hexadecimal characters.
     *
     * @param bytes an array of bytes
     *
     * @return hex string representation of bytes array
     */
    private static String bytesToHexString(byte[] bytes) {
        if (bytes == null) return "";

        StringBuilder ret = new StringBuilder(2 * bytes.length);

        for (int i = 0 ; i < bytes.length ; i++) {
            int b;

            b = 0x0f & (bytes[i] >> 4);

            ret.append("0123456789abcdef".charAt(b));

            b = 0x0f & bytes[i];

            ret.append("0123456789abcdef".charAt(b));
        }

        return ((ret.toString() == null) ? "" : ret.toString());
    }
}
