package com.mediatek.nfc.wfacertification.p2p;

import java.util.Arrays;

import android.util.Log;

import android.os.Handler;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;


import com.mediatek.nfc.wfacertification.INfcWfaIntent.INfcWpsTestBed;


import android.nfc.Tag;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import com.mediatek.nfc.wfacertification.INfcWfaIntent.INfcWfaP2p;


import com.mediatek.nfc.Util;
import com.mediatek.nfc.wfacertification.wps.INfcWpsAppInternal;
import com.mediatek.nfc.wfacertification.wps.NfcForegroundDispatchActivity;


/**
 * Manages handover of NFC to other technologies.
 */
public class NfcWfaSigmaHandle {

    static final String TAG = "NfcWfaSigmaHandle";
    static final boolean DBG = true;

    private static Context mContext;

    private static NfcWfaSigmaHandle mStaticInstance = null;

    private NfcWfaSigmaBroadcastReceiver mBroadcastReceiver;

    private Handler mPost_Handler = new Handler();

    private static Tag mCacheTag = null;
    private static NdefMessage mCacheMessage = null;

    private static SigmaCommandReceiver mSigmaCommandReceiver;



    public NfcWfaSigmaHandle(Context context) {
        Log.i(TAG, " NfcWfaSigmaHandle Construct ");
        mContext = context;

        mBroadcastReceiver = new NfcWfaSigmaBroadcastReceiver(mContext);
        mBroadcastReceiver.init();

        mSigmaCommandReceiver = new SigmaCommandReceiver(context);
        mSigmaCommandReceiver.start();

    }

    public static void createSingleton(Context context) {
        if (mStaticInstance == null) {
            mStaticInstance = new NfcWfaSigmaHandle(context);
        }
    }

    public static NfcWfaSigmaHandle getInstance() {
        return mStaticInstance;
    }

    public static SigmaCommandReceiver getSigmaCommandReceiverInstance() {
        return mSigmaCommandReceiver;
    }


    private static void p2pNdefCheck(byte[] p2pArray) throws FormatException {
        NdefMessage p2pHrM = null;

        if (p2pArray == null) {
            throw new FormatException("p2pNdefCheck p2pArray == null ");
        }

        try {
            p2pHrM = new NdefMessage(p2pArray);

            p2pNdefCheck(p2pHrM);
        } catch (FormatException e) {
            e.printStackTrace();
            throw new FormatException("p2pNdefCheck Exception:" + e);
        }
    }

    private static void p2pNdefCheck(NdefMessage message) throws FormatException {

        int mRecordCount = 0;
        Log.d(TAG, "p2pNdefCheck() ");


        for (NdefRecord mRecord : message.getRecords()) {
            if (mRecord != null) {

                if (mRecord.getTnf() == NdefRecord.TNF_MIME_MEDIA &&
                    Arrays.equals(mRecord.getType(), INfcWfaAppInternal.WFA_P2P_CARRIER_TYPE.getBytes())) {

                    Log.d(TAG, " find record [" + mRecordCount + "] is application/vnd.wfa.p2p");

                    return;
                }

                mRecordCount++;

            }

        }

        throw new FormatException("p2pNdefCheck EXCEPTION: mRecordCount:" + mRecordCount + " not match WFA_P2P_CARRIER_TYPE ");


    }


    public static void sendSigmaP2PBroadcast(byte[] byteArray, boolean isReq) {
        Intent handoverIntent = null;

        if (isReq) {
            handoverIntent = new Intent(INfcWfaSigma.MTK_NFC_WFA_SIGMA_P2P_HR_RECEIVE_ACTION);
            handoverIntent.putExtra(INfcWfaSigma.MTK_NFC_WFA_SIGMA_P2P_EXTRA_HR_P2P_DEV_INFO, byteArray);
        }
        else {
            handoverIntent = new Intent(INfcWfaSigma.MTK_NFC_WFA_SIGMA_P2P_HS_RECEIVE_ACTION);
            handoverIntent.putExtra(INfcWfaSigma.MTK_NFC_WFA_SIGMA_P2P_EXTRA_HS_P2P_DEV_INFO, byteArray);
        }

        Log.d(TAG, "====  sendSigmaP2PBroadcast  [" + isReq + "] sendBroadcast   byteArray:" + Util.printNdef(byteArray));

        mContext.sendBroadcast(handoverIntent);
        return;
    }



    public static void sendSigmaWPSBroadcast(byte[] byteArray, boolean isReq) {
        Intent handoverIntent = null;

        if (isReq) {
            handoverIntent = new Intent(INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_HR_RECEIVE_ACTION);
            handoverIntent.putExtra(INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_EXTRA_HR_P2P_DEV_INFO, byteArray);
        }
        else {
            handoverIntent = new Intent(INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_HS_RECEIVE_ACTION);
            handoverIntent.putExtra(INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_EXTRA_HS_P2P_DEV_INFO, byteArray);
        }

        Log.d(TAG, "====  sendSigmaWPSBroadcast  [" + isReq + "] sendBroadcast   byteArray:" + Util.printNdef(byteArray));

        mContext.sendBroadcast(handoverIntent);
        return;
    }



    /**
        *   Create Tag and broadcast intent
        *
        * @param  null
        * @return   NdefMessage  Beam+ P2P Collision Hs Message
        * @see         null
        */
    public boolean cacheTag(Tag tag, NdefMessage cacheMessage) {
        Log.d(TAG, " cacheTag() ");

        NdefMessage p2pDevInfoMessage = null;
        byte[] intentNdefByteArray = null;

        mCacheTag = tag;
        mCacheMessage = cacheMessage;


        return true;
    }


    public class NfcWfaSigmaBroadcastReceiver extends BroadcastReceiver {
        static final String TAG = "NfcWfaSigmaBroadcastReceiver";
        static final boolean DBG = true;



        private Context mContext;
        private IntentFilter mIntentFilter = new IntentFilter();

        //public static int nowCommand = 0;

        public NfcWfaSigmaBroadcastReceiver(Context t) {
            Log.d(TAG, "NfcWfaSigmaBroadcastReceiver()...");
            mContext = t;
        }

        public void init() {

            Log.d(TAG, "init() is called...");
            mIntentFilter.addAction(INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_HR_ACTION);
            mIntentFilter.addAction(INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_HS_ACTION);

            mIntentFilter.addAction(INfcWfaSigma.MTK_NFC_WFA_SIGMA_P2P_HR_ACTION);

            mIntentFilter.addAction(INfcWfaSigma.MTK_NFC_WFA_SIGMA_ALL_TAG_READ_ACTION);
            mIntentFilter.addAction(INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_PWD_TAG_WRITE_ACTION);



            mIntentFilter.addAction(INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_CFG_TAG_READ_ACTION);
            mIntentFilter.addAction(INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_CFG_TAG_WRITE_ACTION);

            mIntentFilter.addAction(INfcWfaSigma.MTK_NFC_WFA_SIGMA_P2P_TAG_WRITE_ACTION);
            mIntentFilter.addAction(INfcWfaSigma.MTK_NFC_WFA_SIGMA_P2P_TAG_READ_ACTION);

            mContext.registerReceiver(this, mIntentFilter);

        }

        public void deInit() {
            Log.d(TAG, "deInit() is called...");
            mContext.unregisterReceiver(this);
        }

        void arrayExceptionCheck(byte[] array)throws Exception {
            if (array == null)
                throw new Exception(" byteArray == NULL");
        }

        void startForeGroundDispatchActivity(int cmdOnCreate) {
            Intent intentP2p = new Intent(mContext,
                NfcForegroundDispatchActivity.class);
            intentP2p.putExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_CMD, cmdOnCreate);
            //intentP2p.putExtra(INfcWfaAppInternal.EXTRA_NFC_WFA_P2P_INFO, p2pHrM.toByteArray());
            intentP2p.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intentP2p);

        }


        public void sendReadAllTagAction() {

            Log.e(TAG, "sendReadAllTagAction...");

            Intent intentTag = new Intent(mContext,
            NfcSigmaForegroundDispatchActivity.class);
            intentTag.putExtra(INfcWfaSigma.EXTRA_NFC_SIGMA_CMD, INfcWfaSigma.READ_ALL_TAG_CMD);
            //intentTag.putExtra(INfcWfaAppInternal.EXTRA_NFC_WFA_P2P_INFO, p2pHrM.toByteArray());
            intentTag.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intentTag);
        }


        @Override
        public void onReceive(Context context, Intent intent) {
            NdefMessage p2pHrM = null;
            NdefMessage tryNdefMessage = null;

            Log.d(TAG, " Got Sigma command... ");
            Log.d(TAG, " onReceive() An intent is coming... " + intent.getAction());

            String action = intent.getAction();
            if(action == null){
                Log.e(TAG, "onReceive() action == null");
                return;
            }

            // [0] p2p case
            if (action.equals(
                    INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_HR_ACTION)) {

                byte[] p2pInfo = intent.getByteArrayExtra(
                    INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_EXTRA_HR_P2P_DEV_INFO);

                try {
                    arrayExceptionCheck(p2pInfo);
                } catch (Exception e) {
                     Log.e(TAG, "FATAL_ERROR : INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_EXTRA_HR_P2P_DEV_INFO == null.");
                     Log.e(TAG, "SIGMA_WPS_EXTRA_HR   Exception: " + e);
                     e.printStackTrace();
                     return;
                }

                Log.d(TAG, "Rec. length:" + p2pInfo.length + "  SIGMA_WPS_EXTRA_HR_P2P_DEV_INFO:" + Util.bytesToString(p2pInfo));

                // start Nfc Foreground Dispatch activity with command [0x0a]
                //startForeGroundDispatchActivity(INfcWpsAppInternal.SIGMA_HANDOVER_EXECUTION_CMD);


                // sendBroadcast to original handle
                Intent handoverIntent = new Intent(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_HR_ACTION);
                handoverIntent.putExtra(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_EXTRA_PASSWORD, p2pInfo);

                mContext.sendBroadcast(handoverIntent);

            }
            else if (action.equals(
                    INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_HS_ACTION)) {

                byte[] p2pInfo = intent.getByteArrayExtra(
                    INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_EXTRA_HS_P2P_DEV_INFO);

                try {
                    arrayExceptionCheck(p2pInfo);
                } catch (Exception e) {
                     Log.e(TAG, "FATAL_ERROR : INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_EXTRA_HS_P2P_DEV_INFO == null.");
                     Log.e(TAG, "SIGMA_WPS_EXTRA_HS   Exception: " + e);
                     e.printStackTrace();
                     return;
                }

                Log.d(TAG, "Rec. length:" + p2pInfo.length + " SIGMA_WPS_EXTRA_HS_P2P_DEV_INFO:" + Util.bytesToString(p2pInfo));

                // start Nfc Foreground Dispatch activity with command [0x0a]
                //startForeGroundDispatchActivity(INfcWpsAppInternal.SIGMA_HANDOVER_EXECUTION_CMD);


                // sendBroadcast to original handle
                Intent handoverIntent = new Intent(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_HS_ACTION);
                handoverIntent.putExtra(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_EXTRA_PASSWORD, p2pInfo);
                mContext.sendBroadcast(handoverIntent);


            }
            // [1] Write WPS CFG Tag
            else if (action.equals(
                        INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_CFG_TAG_WRITE_ACTION)) {

                // get configuration for EM_SendBroadcastActivity &
                byte[] tagInfo = intent.getByteArrayExtra(
                    INfcWfaSigma.MTK_NFC_WFA_SIGMA_TAG_EXTRA_DEV_INFO);


                Log.d(TAG, "SIGMA_TAG_EXTRA_DEV_INFO:" + Util.bytesToString(tagInfo));

                //relay
                Intent wcfgIntent = new Intent(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_W_CONFIGURATION_ACTION);

                wcfgIntent.putExtra(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_EXTRA_CONFIGURATION, tagInfo);

                mContext.sendBroadcast(wcfgIntent);


            }
            //Write WPS PWD Tag
            else if (action.equals(
                        INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_PWD_TAG_WRITE_ACTION)) {

                byte[] tagInfo = intent.getByteArrayExtra(
                    INfcWfaSigma.MTK_NFC_WFA_SIGMA_TAG_EXTRA_DEV_INFO);


                Log.d(TAG, "pwd SIGMA_TAG_EXTRA_DEV_INFO:" + Util.bytesToString(tagInfo));

                //relay
                Intent wpwdIntent = new Intent(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_W_PASSWORD_ACTION);

                wpwdIntent.putExtra(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_EXTRA_PASSWORD, tagInfo);

                mContext.sendBroadcast(wpwdIntent);


            }
            //WFA P2p case
            else if (action.equals(
                    INfcWfaSigma.MTK_NFC_WFA_SIGMA_P2P_HR_ACTION)) {

                byte[] p2pInfo = intent.getByteArrayExtra(
                    INfcWfaSigma.MTK_NFC_WFA_SIGMA_P2P_EXTRA_HR_P2P_DEV_INFO);


                //relay
                Intent handoverIntent = new Intent(INfcWfaP2p.MTK_NFC_WFA_P2P_HR_ACTION);

                handoverIntent.putExtra(INfcWfaP2p.MTK_NFC_WFA_P2P_EXTRA_HR_P2P_DEV_INFO, p2pInfo);

                mContext.sendBroadcast(handoverIntent);

            }
            //WFA P2p Tag Write case
            else if (action.equals(
                INfcWfaSigma.MTK_NFC_WFA_SIGMA_P2P_TAG_WRITE_ACTION)) {

                // get configuration for EM_SendBroadcastActivity &
                byte[] tagInfo = intent.getByteArrayExtra(
                    INfcWfaSigma.MTK_NFC_WFA_SIGMA_P2P_TAG_EXTRA_INFO);


                Log.d(TAG, "SIGMA_P2P_TAG_EXTRA_INFO:" + Util.bytesToString(tagInfo));

                //relay
                Intent TagIntent = new Intent(INfcWfaP2p.MTK_NFC_WFA_TAG_WRITE_ACTION);
                TagIntent.putExtra(INfcWfaP2p.MTK_NFC_WFA_TAG_EXTRA_DEV_INFO, tagInfo);
                mContext.sendBroadcast(TagIntent);


            }
            //Sigma All Tag Read case ,Read WPS CFG Tag, Read WFA P2p Tag Read case
            else if (action.equals(INfcWfaSigma.MTK_NFC_WFA_SIGMA_ALL_TAG_READ_ACTION) ||
                    action.equals(INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_CFG_TAG_READ_ACTION) ||
                    action.equals(INfcWfaSigma.MTK_NFC_WFA_SIGMA_P2P_TAG_READ_ACTION)) {

                sendReadAllTagAction();
            }
            else
                Log.e(TAG, "On Receive an unknow intent= =...");

        }// end of onreceive


    }
}

