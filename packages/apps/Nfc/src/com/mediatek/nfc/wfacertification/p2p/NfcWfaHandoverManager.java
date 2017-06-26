package com.mediatek.nfc.wfacertification.p2p;

import java.util.Arrays;

import android.util.Log;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;


import android.widget.Toast;


import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import com.mediatek.nfc.wfacertification.INfcWfaIntent.INfcWfaP2p;


import com.mediatek.nfc.Util;
import com.mediatek.nfc.handoverprotocol.HandoverMessage;

import com.mediatek.nfc.handoverprotocol.HandoverMessageElement;

import com.mediatek.nfc.handoverprotocol.WifiCarrierConfiguration;
import com.mediatek.nfc.handoverprotocol.WifiCarrierConfiguration.Credential;
import com.mediatek.nfc.handoverprotocol.WfaP2pCarrierConfiguration;

import android.net.wifi.p2p.WifiP2pManager;

/**
 * Manages handover of NFC to other technologies.
 */
public class NfcWfaHandoverManager {

    static final String TAG = "NfcWfaHandoverManager";
    static final boolean DBG = true;

    public static final int SCENARIO_JB_ORIGINAL     = 0; //= "com.mediatek.nfc.handover.SCENARIO_JB_ORIGINAL";
    public static final int SCENARIO_BEAMPLUS_P2P    = 1; //= "com.mediatek.nfc.handover.SCENARIO_BEAMPLUS_P2P";
    public static final int SCENARIO_WFD             = 2; //= "com.mediatek.nfc.handover.SCENARIO_WFD";
    public static final int SCENARIO_WIFI_LEGACY     = 3; //= "com.mediatek.nfc.handover.SCENARIO_WIFI_LEGACY";
    public static final int SCENARIO_HR_COLLISION    = 4; //= "com.mediatek.nfc.handover.SCENARIO_HR_COLLISION";

    public static final int SCENARIO_WFA_P2P         = 5;

    final Context mContext;
    //HandoverManager mHandoverManager;


    private static NfcWfaHandoverManager mStaticInstance = null;

    private NfcWfaBroadcastReceiver mBroadcastReceiver;

    private WifiP2pManager mWifiP2pManager;

    private NdefMessage mWfaReqNdef = null;

    public NfcWfaHandoverManager(Context context) {
        Log.i(TAG, " NfcWfaHandoverManager Construct ");
        mContext = context;

        mBroadcastReceiver = new NfcWfaBroadcastReceiver(mContext);
        mBroadcastReceiver.init();

    }

    public static void createSingleton(Context context) {
        if (mStaticInstance == null) {
            mStaticInstance = new NfcWfaHandoverManager(context);
        }
    }

    public static NfcWfaHandoverManager getInstance() {
        return mStaticInstance;
    }

    public NdefMessage createP2pHrM() {
        Log.d(TAG, "    createP2pHrM(): ");

        if (mWfaReqNdef == null) {
            Log.e(TAG, "Exception error mReqWpsNdef == null  return null");
            return null;
        }

        return mWfaReqNdef;
    }


    public void doWfaP2pHandover(NdefMessage WfaP2pHandoverMessage) {
        Log.d(TAG, "    doWfaP2pHandover(): ");

        //1.parse HsM
        NdefRecord r = WfaP2pHandoverMessage.getRecords()[0];
        if (r.getTnf() != NdefRecord.TNF_WELL_KNOWN) {
            Log.e(TAG, "  r.getTnf() != NdefRecord.TNF_WELL_KNOWN  return; ");
            return;
        }
        if (!Arrays.equals(r.getType(), NdefRecord.RTD_HANDOVER_SELECT)) {
            Log.e(TAG, "  r.getType() != NRTD_HANDOVER_SELECT  return; ");
            return;
        }


        //= whichScenario(tryMessage);
        HandoverMessageElement mHandoverMsgElement = new HandoverMessageElement(WfaP2pHandoverMessage);
        int mScenario = mHandoverMsgElement.getScenario();
        Log.d(TAG, "mScenario:" + mScenario);

        //because we will determine connway on selector so we judge mScenario on doBeamPlusHandover() for JB original device(use BT)
        switch(mScenario) {

            case SCENARIO_WFA_P2P:
                dealWfaP2pHsM(WfaP2pHandoverMessage);
                return;

            case SCENARIO_HR_COLLISION:
                //Collision happen, do nothing just block
                Log.e(TAG, " SCENARIO_HR_COLLISION  [HS end] Collision case !!!!");
                Log.d(TAG, " Return Only on WFA P2p case !! Just Block this");

                return;


            case SCENARIO_BEAMPLUS_P2P:
                Log.e(TAG, " should not get BeamPlus HandoverSelectMessage   return ");
                return;
            case SCENARIO_WFD:
                //return mHandoverManager.tryHandoverRequest(tryMessage);
                Log.e(TAG, " should not get WFD HandoverSelectMessage   return ");
                return;
            case SCENARIO_WIFI_LEGACY:
                //return dealWifiLegacyHrM(BPhandoverMessage);
                Log.e(TAG, " should not get WFL HandoverSelectMessage   return");
                return;
            default:
            case SCENARIO_JB_ORIGINAL:
               Log.e(TAG, " should not get WFL SCENARIO_JB_ORIGINAL, Default,   return");
                return;
        }


    }


    //deal with Wfa P2p Handover Select Message
    //Client Get HsM response
    private void dealWfaP2pHsM(NdefMessage wfaP2pHsM) {
        Log.d(TAG, "dealWfaP2pHsM() ");
        //1.parse HsM
        try {
            p2pNdefCheck(wfaP2pHsM);
        } catch (FormatException e) {
            e.printStackTrace();
            Log.e(TAG, "WFA P2P HsM Exception: " + e);
        }

        //2.Close foreground activity (at p2plink Manager closeForegroundDispatchActivity() )

        //3.parse HsM, send Hs broadcast
        sendHsmReceiveBroadcast(wfaP2pHsM);
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
                    //if(mRecordCount == 1){
                    //    Log.d(TAG, " SUCCESS Pass p2p check");
                    //    return;
                    //}
                    return;
                }

                mRecordCount++;

            }

        }

        throw new FormatException("p2pNdefCheck EXCEPTION: mRecordCount:" + mRecordCount + " not match WFA_P2P_CARRIER_TYPE ");


    }


    private void sendHrmReceiveBroadcast(NdefMessage HrM) {
        Log.d(TAG, " sendHrmReceiveBroadcast() MTK_NFC_WFA_P2P_HR_RECEIVE_ACTION");

        NdefMessage p2pDevInfoMessage = null;
        byte[] intentNdefByteArray = null;

        WfaP2pCarrierConfiguration mWfaP2pCCR = WfaP2pCarrierConfiguration.tryParse(HrM);

        if (mWfaP2pCCR == null) {
            Log.e(TAG, "exception not to send intent MTK_NFC_WFA_P2P_HR_RECEIVE_ACTION  mWfaP2pCCR == null");
            return;
        }

        if (mWfaP2pCCR.getPayload() == null || mWfaP2pCCR.getId() == null) {
            Log.e(TAG, " Exception:  p2pDevInfoPayload " + mWfaP2pCCR.getPayload());
            Log.e(TAG, " Exception:  p2pDevInfoId " + mWfaP2pCCR.getId());
            Log.e(TAG, " not to send intent MTK_NFC_WFA_P2P_HR_RECEIVE_ACTION");
            return;
        }

         // TODO:: check createMessage()

        p2pDevInfoMessage = mWfaP2pCCR.createMessage();

        Log.d(TAG, " Send intent with WFA p2p Record:" + Util.printNdef(p2pDevInfoMessage));

        intentNdefByteArray = p2pDevInfoMessage.toByteArray();

        Intent handoverIntent = new Intent(INfcWfaP2p.MTK_NFC_WFA_P2P_HR_RECEIVE_ACTION);

        handoverIntent.putExtra(INfcWfaP2p.MTK_NFC_WFA_P2P_EXTRA_HR_P2P_DEV_INFO, intentNdefByteArray);

        mContext.sendBroadcast(handoverIntent);

        //Sigma case::
        NfcWfaSigmaHandle.getInstance().sendSigmaP2PBroadcast(intentNdefByteArray, true);

        return;
    }

    private void sendHsmReceiveBroadcast(NdefMessage HsM) {
        Log.d(TAG, " sendHsmReceiveBroadcast() MTK_NFC_WFA_P2P_HS_RECEIVE_ACTION");


        NdefMessage p2pDevInfoMessage = null;
        byte[] intentNdefByteArray = null;

        WfaP2pCarrierConfiguration mWfaP2pCCR = WfaP2pCarrierConfiguration.tryParse(HsM);

        if (mWfaP2pCCR == null) {
            Log.e(TAG, "exception not to send intent MTK_NFC_WFA_P2P_HR_RECEIVE_ACTION  mWfaP2pCCR == null");
            return;
        }

        if (mWfaP2pCCR.getPayload() == null || mWfaP2pCCR.getId() == null) {
            Log.e(TAG, " Exception:  p2pDevInfoPayload " + mWfaP2pCCR.getPayload());
            Log.e(TAG, " Exception:  p2pDevInfoId " + mWfaP2pCCR.getId());
            Log.e(TAG, " not to send intent MTK_NFC_WFA_P2P_HS_RECEIVE_ACTION !!!!");
            return;
        }

        // TODO:: check createMessage()

        p2pDevInfoMessage = mWfaP2pCCR.createMessage();

        Log.d(TAG, " Send hs intent with WFA p2p Record:" + Util.printNdef(p2pDevInfoMessage));

        intentNdefByteArray = p2pDevInfoMessage.toByteArray();


        Intent hsIntent = new Intent(INfcWfaP2p.MTK_NFC_WFA_P2P_HS_RECEIVE_ACTION);

        hsIntent.putExtra(INfcWfaP2p.MTK_NFC_WFA_P2P_EXTRA_HS_P2P_DEV_INFO, intentNdefByteArray);

        mContext.sendBroadcast(hsIntent);

        //Sigma case::
        NfcWfaSigmaHandle.getInstance().sendSigmaP2PBroadcast(intentNdefByteArray, false);

        return;
    }

    public void sendInternalCmdToNfcWfaForegroundActivity(int cmd) {

        Log.i(TAG, "  sendInternalCmdToNfcWfaForegroundActivity() cmd:" + cmd);

        if (cmd == INfcWfaAppInternal.HANDOVER_FINISH_CMD) {
            mWfaReqNdef = null;
        }


        // start Nfc Foreground Dispatch activity with command [2]
        Intent intentToFA = new Intent(mContext,
                NfcWfaForegroundDispatchActivity.class);
        intentToFA.putExtra(INfcWfaAppInternal.EXTRA_NFC_WFA_INTERNAL_CMD, cmd);

        //set android:launchMode="singleTask" at Manifest.xml
        intentToFA.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intentToFA.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mContext.startActivity(intentToFA);
    }



    public NdefMessage dealWfaP2pHrM(NdefMessage tryMessage) {
        NdefMessage p2pHsm = null;
        byte[] selP2pInfo = null;
        Log.d(TAG, "dealWfaP2pHrM() ");

        //1.Check HrM
        try {
            p2pNdefCheck(tryMessage);
        } catch (FormatException e) {
            e.printStackTrace();
            Log.e(TAG, "!! WFA P2P HrM Exception: " + e);
        }

        //2.get Selector P2p dev info
        mWifiP2pManager = (WifiP2pManager) mContext.getSystemService(Context.WIFI_P2P_SERVICE);

        if (mWifiP2pManager == null) {
            Log.e(TAG, "  mWifiP2pManager == null   return null");
            return null;
        }

        selP2pInfo = mWifiP2pManager.getNfcSelectToken();


        if (selP2pInfo == null) {
            Log.d(TAG, "[Sigma] SigmaCommandReceiverInstance().getP2pSelect() ");
            selP2pInfo = NfcWfaSigmaHandle.getSigmaCommandReceiverInstance().getP2pSelect();
        }

        Log.d(TAG, "  selP2pInfo[] :: " + Util.printNdef(selP2pInfo));

        try {
            p2pNdefCheck(selP2pInfo);
        } catch (FormatException e) {
            e.printStackTrace();
            Log.e(TAG, "!!!! getNfcSelectToken()   Data invalid");
            Log.e(TAG, "Exception:" + e);
            Log.e(TAG, "[[ return null ]]");
            return null;
        }



        //3.parse HrM ,send Hr sendBroadcast
        sendHrmReceiveBroadcast(tryMessage);


        //4.Create HsM and  return
        try {
            //p2pNdefCheck(p2pInfo);
            p2pHsm = WfaHandoverBuilderParser.createWfaP2pHsM(selP2pInfo);
        } catch (FormatException e) {
            e.printStackTrace();
            Log.e(TAG, "!!!! p2pHsm   Exception: " + e);
        }

        return p2pHsm;
    }

    /**
    *   Create Specific collision Handover Select Message when Beam+ P2P case
    *   we force return one NDEF record which include the MAC address of Selector with small Collision number
    *
    * @param  null
    * @return   NdefMessage  Beam+ P2P Collision Hs Message
    * @see         null
    */
    public NdefMessage createWfaP2pCollisionHsM() {
        Log.i(TAG, " createWfaP2pCollisionHsM  ");

        NdefMessage result;
        byte[] arrayData = {0x64, 0x75, 0x6d, 0x6d, 0x79}; //dummy string (ASCII)

        result = WfaHandoverBuilderParser.createMtkSpecificHsM(HandoverMessage.SPECIFIC_RECORD_TYPE_WFA_HANDOVER_REQUEST_COLLISION, arrayData);

       return result;
    }

    //return the status of Wifi, not related to enable success or not
    static boolean powerUpWifi() {

        if (DBG) Log.d(TAG, " Don't check to powerUpWifi, always return true");
            return true;
    }

    /**
        *   Create Specific collision Handover Select Message when Beam+ P2P case
        *   we force return one NDEF record which include the MAC address of Selector with small Collision number
        *
        * @param  null
        * @return   NdefMessage  Beam+ P2P Collision Hs Message
        * @see         null
        */
    public boolean tryHandover(NdefMessage tryMessage) {
        Log.d(TAG, " tryHandover() ");

        NdefMessage p2pDevInfoMessage = null;
        byte[] intentNdefByteArray = null;

        if (tryMessage == null) {
            Log.e(TAG, "tryHandover() message is null");
            return false;
        }

        WfaP2pCarrierConfiguration mWfaP2pCCR = WfaP2pCarrierConfiguration.tryParse(tryMessage);

        if (mWfaP2pCCR == null) {
            Log.e(TAG, "Tag not WFA P2P");
            return false;
        }

        if (mWfaP2pCCR.getPayload() == null || mWfaP2pCCR.getId() == null) {
            Log.e(TAG, " Exception:  p2pDevInfoPayload " + mWfaP2pCCR.getPayload());
            Log.e(TAG, " Exception:  p2pDevInfoId " + mWfaP2pCCR.getId());
            Log.e(TAG, " Tag Format invalid");
            return false;
        }

        p2pDevInfoMessage = mWfaP2pCCR.createMessage();

        Log.d(TAG, "[WFA P2P Tag Hit] Send intent WFA_TAG_RECEIVE_ACTION with WFA p2p Record");

        Log.d(TAG, "WFA p2p Record:" + Util.printNdef(p2pDevInfoMessage));

        intentNdefByteArray = p2pDevInfoMessage.toByteArray();

        Intent readTagIntent = new Intent(INfcWfaP2p.MTK_NFC_WFA_TAG_RECEIVE_ACTION);

        readTagIntent.putExtra(INfcWfaP2p.MTK_NFC_WFA_TAG_EXTRA_DEV_INFO, intentNdefByteArray);

        mContext.sendBroadcast(readTagIntent);

        Toast.makeText(mContext, " matched WFA P2P TAG (Static HANDOVER)", Toast.LENGTH_LONG).show();
        Log.i(TAG, "matched WFA P2P Static HANDOVER");


        return true;
    }


    public class NfcWfaBroadcastReceiver extends BroadcastReceiver {
        static final String TAG = "NfcWfaBroadcastReceiver";
        static final boolean DBG = true;



        private Context mContext;
        private IntentFilter mIntentFilter = new IntentFilter();

        //public static int nowCommand = 0;

        public NfcWfaBroadcastReceiver(Context t) {
            Log.d(TAG, "NfcWfaBroadcastReceiver()...");
            mContext = t;
        }

        public void init() {

            Log.d(TAG, "init() is called...");
            mIntentFilter.addAction(INfcWfaP2p.MTK_NFC_WFA_P2P_HR_ACTION);
            mIntentFilter.addAction(INfcWfaP2p.MTK_NFC_WFA_TAG_WRITE_ACTION);
            mIntentFilter.addAction(INfcWfaP2p.MTK_NFC_WFA_CFG_TAG_WRITE_ACTION);
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

        @Override
        public void onReceive(Context context, Intent intent) {
            //NdefMessage p2pHrM = null;


            Log.d(TAG, " onReceive() An intent is coming... " + intent.getAction());
            String action = intent.getAction();
            if(action == null){
                Log.e(TAG, "onReceive() action == null");
                return;
            }

            // [0] p2p case
            if (action.equals(
                    INfcWfaP2p.MTK_NFC_WFA_P2P_HR_ACTION)) {

                byte[] p2pInfo = intent.getByteArrayExtra(
                    INfcWfaP2p.MTK_NFC_WFA_P2P_EXTRA_HR_P2P_DEV_INFO);

                try {
                    arrayExceptionCheck(p2pInfo);
                } catch (Exception e) {
                     Log.e(TAG, "FATAL_ERROR : INfcWfaP2p.MTK_NFC_WFA_P2P_EXTRA_HR_P2P_DEV_INFO == null.");
                     Log.e(TAG, "p2pInfo   Exception: " + e);
                     e.printStackTrace();
                }



                Log.d(TAG, "Rec. EXTRA_HR_P2P_DEV_INFO:" + Util.bytesToString(p2pInfo));

                try {
                    //p2pNdefCheck(p2pInfo);
                    mWfaReqNdef = WfaHandoverBuilderParser.createWfaP2pHrM(p2pInfo);
                } catch (FormatException e) {
                    Log.e(TAG, "p2pInfo   Exception: " + e);
                    e.printStackTrace();
                }

                Log.d(TAG, "WFA p2p HrM:" + Util.printNdef(mWfaReqNdef));


                // start Nfc Foreground Dispatch activity with command [0]
                Intent intentP2p = new Intent(context,
                        NfcWfaForegroundDispatchActivity.class);
                intentP2p.putExtra(INfcWfaAppInternal.EXTRA_NFC_WFA_CMD, INfcWfaAppInternal.WFA_P2P_CMD);
                //intentP2p.putExtra(INfcWfaAppInternal.EXTRA_NFC_WFA_P2P_INFO, p2pHrM.toByteArray());
                intentP2p.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intentP2p);

            }
            // [1] Write P2P Tag
            else if (action.equals(
                        INfcWfaP2p.MTK_NFC_WFA_TAG_WRITE_ACTION)) {

                // get configuration for EM_SendBroadcastActivity &
                byte[] tagInfo = intent.getByteArrayExtra(
                    INfcWfaP2p.MTK_NFC_WFA_TAG_EXTRA_DEV_INFO);

                try {
                    arrayExceptionCheck(tagInfo);
                } catch (Exception e) {
                    Log.e(TAG, "FATAL_ERROR : INfcWfaP2p.MTK_NFC_WFA_TAG_EXTRA_DEV_INFO == null.");
                    Log.e(TAG, "tagInfo   Exception: " + e);
                    e.printStackTrace();
                }


                Log.d(TAG, "EXTRA_DEV_INFO:" + Util.bytesToString(tagInfo));

                //Byte Array to NDEF Message
                NdefMessage tryNdefMessage = null;

                try {
                    tryNdefMessage = new NdefMessage(tagInfo);

                } catch (FormatException e) {
                    Log.e(TAG, "new NdefMessage(tagInfo) Exception:" + e);
                    e.printStackTrace();
                }


                //1.parse tagInfo, verify it's WFA record or not

                WfaP2pCarrierConfiguration mWfaP2pCCR = WfaP2pCarrierConfiguration.tryParse(tryNdefMessage);

                if (mWfaP2pCCR == null) {
                    Log.e(TAG, "exception ,not to Start activity (WRITE_TAG_CMD)  mWfaP2pCCR == null");
                    return;
                }

                //total NDEF message to Write
                NdefMessage TagNdefMessage = null;
                try {
                    //p2pNdefCheck(p2pInfo);
                    TagNdefMessage = WfaHandoverBuilderParser.createWfaP2pHsM(tagInfo);
                } catch (FormatException e) {
                    Log.e(TAG, "tagInfo   Exception: " + e);
                    e.printStackTrace();
                }

                Log.e(TAG, "NdefMessage to write:" + Util.printNdef(TagNdefMessage));

                // start Nfc Foreground Dispatch activity with command [1]
                Intent intentWriteTag = new Intent(context,
                        NfcWfaForegroundDispatchActivity.class);
                intentWriteTag.putExtra(INfcWfaAppInternal.EXTRA_NFC_WFA_CMD, INfcWfaAppInternal.WRITE_TAG_CMD);
                intentWriteTag.putExtra(INfcWfaAppInternal.EXTRA_NFC_WFA_TAG_INFO, TagNdefMessage.toByteArray());
                intentWriteTag.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intentWriteTag);

            }
            //Write CFG Tag
            else if (action.equals(
                        INfcWfaP2p.MTK_NFC_WFA_CFG_TAG_WRITE_ACTION)) {

                // get configuration for EM_SendBroadcastActivity &
                byte[] tagInfo = intent.getByteArrayExtra(
                    INfcWfaP2p.MTK_NFC_WFA_TAG_EXTRA_DEV_INFO);

                try {
                    arrayExceptionCheck(tagInfo);
                } catch (Exception e) {
                    Log.e(TAG, "FATAL_ERROR : INfcWfaP2p.MTK_NFC_WFA_TAG_EXTRA_DEV_INFO == null.");
                    Log.e(TAG, "tagInfo   Exception: " + e);
                    e.printStackTrace();
                }


                Log.d(TAG, "EXTRA_DEV_INFO:" + Util.bytesToString(tagInfo));

                //Byte Array to NDEF Message
                NdefMessage tryNdefMessage = null;

                try {
                    tryNdefMessage = new NdefMessage(tagInfo);

                } catch (FormatException e) {
                    Log.e(TAG, "new NdefMessage(tagInfo) Exception:" + e);
                    e.printStackTrace();
                }


                //1.parse tagInfo, verify it's WSC record or not
                WifiCarrierConfiguration ccr_wifi = WifiCarrierConfiguration.tryParse(tryNdefMessage);

                if (ccr_wifi == null) {
                    Log.e(TAG, "!!!! inValid Tag,  not Credential format , toast prompt !!!! ");
                    Toast.makeText(mContext, "Credential format inValid, please check", Toast.LENGTH_LONG).show();
                    return;
                }


                Credential credentialData = ccr_wifi.getCredential();
                //PasswordToken pwdTokenData = ccr_wifi.getPasswordToken();

                if (credentialData == null) {
                    Log.e(TAG, "!!!! exception : onReceive()  MTK_NFC_WFA_CFG_TAG_WRITE_ACTION invalid return;");
                    Toast.makeText(mContext, "Credential format inValid, no Credential ", Toast.LENGTH_LONG).show();
                    return;
                }

                Log.d(TAG, "NdefMessage to write:" + Util.printNdef(tagInfo));

                // start Nfc Foreground Dispatch activity with command [1]
                Intent intentWriteTag = new Intent(context,
                        NfcWfaForegroundDispatchActivity.class);
                intentWriteTag.putExtra(INfcWfaAppInternal.EXTRA_NFC_WFA_CMD, INfcWfaAppInternal.WRITE_CFG_TAG_CMD);
                intentWriteTag.putExtra(INfcWfaAppInternal.EXTRA_NFC_WFA_TAG_INFO, tagInfo);
                intentWriteTag.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intentWriteTag);

            }
            else
                Log.e(TAG, "On Receive an unknow intent= =...");

        }// end of onreceive

    }

    public static class WfaHandoverBuilderParser {
        static final String TAG = "WfaHandoverBuilderParser";
        static final boolean DBG = true;

        // create a entire Wfa P2p HrM .
        public static NdefMessage createWfaP2pHrM(byte[] p2pArrayInfo)throws FormatException {
            NdefMessage wfaP2p = null;
            NdefMessage p2pDevInfoMessage = null;

            Log.d(TAG, "createWfaP2pHrM() ..");


            try {
                p2pDevInfoMessage = new NdefMessage(p2pArrayInfo);

                p2pNdefCheck(p2pDevInfoMessage);
            } catch (FormatException e) {
                e.printStackTrace();
                throw new FormatException("p2pNdefCheck Exception:" + e);

            }

            wfaP2p = packP2PHrM(p2pDevInfoMessage, powerUpWifi());

            return wfaP2p;
        }

        public static NdefMessage packP2PHrM(NdefMessage p2pDevInfoMessage, boolean wifiCPS) {

            byte[] p2pRecordId;

            // create Wfa P2p ccR
            WfaP2pCarrierConfiguration mWfaP2pCCR = WfaP2pCarrierConfiguration.tryParse(p2pDevInfoMessage);

            p2pRecordId = mWfaP2pCCR.getId();

            Log.d(TAG, "mWfaP2pCCR.getId()" + Util.bytesToString(p2pRecordId) + "   wifiCPS:" + wifiCPS);


            // create HsM with BT + WiFiCCR(with extension TLVs)
            HandoverMessage msg_mtk_request = new HandoverMessage();
            //WifiCarrierConfiguration CCR = new WifiCarrierConfiguration(credential);
            //msg_mtk_select.appendAlternativeCarrier(btCPS ? HandoverMessage.CARRIER_POWER_STATE_ACTIVE : HandoverMessage.CARRIER_POWER_STATE_ACTIVATING, btCCR);
            msg_mtk_request.appendAlternativeCarrier(wifiCPS ? HandoverMessage.CARRIER_POWER_STATE_ACTIVE : HandoverMessage.CARRIER_POWER_STATE_ACTIVATING, mWfaP2pCCR, null, p2pRecordId);
            NdefMessage mtk_hr = msg_mtk_request.createHandoverRequestMessage();

            return mtk_hr;
        }


        // create a entire Wfa P2p HsM .
        public static NdefMessage createWfaP2pHsM(byte[] p2pArrayInfo)throws FormatException {
            NdefMessage wfaP2p = null;
            NdefMessage p2pDevInfoMessage = null;

            Log.d(TAG, "createWfaP2pHsM()");

            try {
                p2pDevInfoMessage = new NdefMessage(p2pArrayInfo);

                p2pNdefCheck(p2pDevInfoMessage);
            } catch (FormatException e) {
                e.printStackTrace();
                throw new FormatException("p2pNdefCheck Exception:" + e);

            }

            wfaP2p = packP2PHsM(p2pDevInfoMessage, powerUpWifi());

            return wfaP2p;
        }

        public static NdefMessage packP2PHsM(NdefMessage p2pDevInfoMessage, boolean wifiCPS) {

            byte[] p2pRecordId;

            // create Wfa P2p ccR
            WfaP2pCarrierConfiguration mWfaP2pCCR = WfaP2pCarrierConfiguration.tryParse(p2pDevInfoMessage);

            p2pRecordId = mWfaP2pCCR.getId();

            Log.d(TAG, "mWfaP2pCCR.getId()" + Util.bytesToString(p2pRecordId) + "   wifiCPS:" + wifiCPS);


            // create HsM with BT + WiFiCCR(with extension TLVs)
            HandoverMessage msg_mtk_request = new HandoverMessage();
            //WifiCarrierConfiguration CCR = new WifiCarrierConfiguration(credential);
            //msg_mtk_select.appendAlternativeCarrier(btCPS ? HandoverMessage.CARRIER_POWER_STATE_ACTIVE : HandoverMessage.CARRIER_POWER_STATE_ACTIVATING, btCCR);
            msg_mtk_request.appendAlternativeCarrier(wifiCPS ? HandoverMessage.CARRIER_POWER_STATE_ACTIVE : HandoverMessage.CARRIER_POWER_STATE_ACTIVATING, mWfaP2pCCR, null, p2pRecordId);
            NdefMessage mtk_hr = msg_mtk_request.createHandoverSelectMessage(); //PF#1  HsM without Collision record, createWfaP2pHandoverSelectMessage();

            return mtk_hr;
        }

        // create a Error HsM .
        public static NdefMessage createMtkSpecificHsM(byte reason, byte[] arrayData) {

            HandoverMessage msg_mtk_select = new HandoverMessage();
            NdefMessage mtk_hs = msg_mtk_select.createMtkSpecificHandoverSelectMessage(reason, arrayData);

            return mtk_hs;
        }// end of createErrorHsM

    }


}

