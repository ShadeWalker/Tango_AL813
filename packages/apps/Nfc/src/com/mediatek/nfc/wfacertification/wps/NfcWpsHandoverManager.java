package com.mediatek.nfc.wfacertification.wps;

import java.util.Arrays;

import android.util.Log;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.net.Uri;

import android.widget.Toast;


import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;




import com.mediatek.nfc.Util;


import com.mediatek.nfc.handover.MtkHandoverManager;

import com.mediatek.nfc.handoverprotocol.HandoverMessage;
import com.mediatek.nfc.handoverprotocol.HandoverMessageElement;
import com.mediatek.nfc.handoverprotocol.WifiCarrierConfiguration;
import com.mediatek.nfc.handoverprotocol.WifiCarrierConfiguration.Credential;
import com.mediatek.nfc.handoverprotocol.WifiCarrierConfiguration.PasswordToken;
import com.mediatek.nfc.handoverprotocol.WpsP2pCarrierConfiguration;

import com.mediatek.nfc.wfacertification.p2p.NfcWfaSigmaHandle;
import com.mediatek.nfc.wfacertification.INfcWfaIntent.INfcWpsTestBed;


/**
 * Manages handover of NFC to other technologies.
 */
public class NfcWpsHandoverManager {

    static final String TAG = "NfcWpsHandoverManager";
    static final boolean DBG = true;

    final Context mContext;

    private static NfcWpsHandoverManager mStaticInstance = null;

    private NfcWpsBroadcastReceiver mBroadcastReceiver;

    private NdefMessage mSelWpsNdef = null;
    private NdefMessage mReqWpsNdef = null;

    public NfcWpsHandoverManager(Context context) {
        Log.i(TAG, " NfcWpsHandoverManager Construct ");
        mContext = context;

        mBroadcastReceiver = new NfcWpsBroadcastReceiver(mContext);
        mBroadcastReceiver.init();
    }

    public static void createSingleton(Context context) {
        if (mStaticInstance == null) {
            mStaticInstance = new NfcWpsHandoverManager(context);
        }
    }

    public static NfcWpsHandoverManager getInstance() {
        return mStaticInstance;
    }


    //createWpsHrM

    public NdefMessage createWiFiLegacyRequestMessage() {
        Log.d(TAG, "    createWiFiLegacyRequestMessage(): ");

        if (mReqWpsNdef == null) {
            Log.e(TAG, "Exception error mReqWpsNdef == null  return null");
            return null;
        }

        return mReqWpsNdef;
    }


        /*
        *   handle Wifi Legacy Handover Request Message
        *
        *   this function don't need to do anything because there is no Hr content include.
        *
        */
        public NdefMessage dealWifiLegacyHrM(NdefMessage WLHrM) {

            //parse Record 1 data
            //NdefRecord r1 = WLHrM.getRecords()[1];
            //NdefMessage tryNdefMessage = new NdefMessage(r1);
            NdefMessage tryNdefMessage = WifiCarrierConfiguration.getWiFiOOB(WLHrM);

            WpsP2pCarrierConfiguration ccr_wifi = WpsP2pCarrierConfiguration.tryParse(tryNdefMessage);

            //PasswordToken mPwd = ccr_wifi.getPasswordToken();

            if (ccr_wifi == null) {
                Log.e(TAG, "!!!! exception : mReceiver  MTK_WPS_NFC_TESTBED_EXTRA_Password invalid return error;");
                return null;
            }

            sendWifiLEgacyBroadcast(tryNdefMessage.toByteArray(), true);



            return createWifiLegacyHsM();
        }



    /*
    *   Create Wifi Legacy Handover Selcet Message
    *
    *
    */
    public NdefMessage createWifiLegacyHsM() { //(IFastConnectInfo connInfoWithCredential){
        NdefMessage mWLHsM = null;
        if (DBG) Log.d(TAG, "  createWifiLegacyHsM()");


        if (mSelWpsNdef == null) {
            Log.e(TAG, "Exception error mSelWpsNdef == null  return null");
            return null;
        }

        return mSelWpsNdef;
    }


    private void sendWifiLEgacyBroadcast(byte[] byteArray, boolean isReq) {
        Intent handoverIntent = null;

        if (isReq)
            handoverIntent = new Intent(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_HR_RECEIVED_ACTION);
        else
            handoverIntent = new Intent(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_HS_RECEIVED_ACTION);

        handoverIntent.putExtra(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_EXTRA_PASSWORD, byteArray);

        Log.d(TAG, "====  sendWifiLEgacyBroadcast  sendBroadcast   byteArray:" + Util.printNdef(byteArray));

        mContext.sendBroadcast(handoverIntent);

        //Sigma usage
        NfcWfaSigmaHandle.getInstance().sendSigmaWPSBroadcast(byteArray, isReq);
        return;
    }


    //return the status of Wifi, not related to enable success or not
    static boolean powerUpWifi() {

        if (DBG) Log.d(TAG, "WPS Don't check to powerUpWifi, always return true");
            return true;
    }


    public void doWiFiLegacyHandover(Uri[] uris, NdefMessage WifiLegacyHandoverMessage) {
        //to check Url, check return
        Log.d(TAG, "  doWiFiLegacyHandover()"   );

        //1.parse HsM, verify it's Legacy record
        NdefRecord r0 = WifiLegacyHandoverMessage.getRecords()[0];
        if (r0.getTnf() != NdefRecord.TNF_WELL_KNOWN) {
            Log.e(TAG, "  r0.getTnf() != NdefRecord.TNF_WELL_KNOWN  return; ");
            return;
        }
        if (!Arrays.equals(r0.getType(), NdefRecord.RTD_HANDOVER_SELECT)) {
            Log.e(TAG, "  r0.getType() != NRTD_HANDOVER_SELECT  return; ");
            return;
        }

        //check AC exist, record 1 is vnd.wfa.wsc
        HandoverMessageElement mHandoverMsgElement = new HandoverMessageElement(WifiLegacyHandoverMessage);
        int mScenario = mHandoverMsgElement.getScenario();
        Log.d(TAG, "mScenario:" + mScenario);

        if (mScenario != MtkHandoverManager.SCENARIO_WIFI_LEGACY) {
            Log.e(TAG, "exception error -> mScenario != SCENARIO_WIFI_LEGACY  Legacy Handover Fail  return");
            return;
        }

        //parse Record 1 data
        NdefMessage tryNdefMessage = WifiCarrierConfiguration.getWiFiOOB(WifiLegacyHandoverMessage);

        WpsP2pCarrierConfiguration ccr_wifi = WpsP2pCarrierConfiguration.tryParse(tryNdefMessage);

        if (ccr_wifi == null) {
            Log.e(TAG, "!!!! exception : mReceiver  MTK_WPS_NFC_TESTBED_EXTRA_Password invalid return error;");
            return;
        }

        sendWifiLEgacyBroadcast(tryNdefMessage.toByteArray(), false);


    }


    public void setInternalCmdToForegroundDispatchActivity(int cmd) {

        Log.i(TAG, "  setInternalCmdToForegroundDispatchActivity() cmd:" + cmd);

        if (cmd == INfcWpsAppInternal.HANDOVER_FINISH_CMD) {
            mSelWpsNdef = null;

            mReqWpsNdef = null;
        }

        // start Nfc Foreground Dispatch activity with command [2]
        Intent intentHr = new Intent(mContext,
                NfcForegroundDispatchActivity.class);
        intentHr.putExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_INTERNAL_CMD, cmd);

        //set android:launchMode="singleTask" at Manifest.xml
        intentHr.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intentHr.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mContext.startActivity(intentHr);
    }



    public class NfcWpsBroadcastReceiver extends BroadcastReceiver {
        static final String TAG = "NfcWpsBroadcastReceiver";
        static final boolean DBG = true;



        private Context mContext;
        //private IntentFilter mIntentFilter = new IntentFilter();

        //public static int nowCommand = 0;

        public NfcWpsBroadcastReceiver(Context t) {
            Log.d(TAG, "NfcWpsBroadcastReceiver()...");
            mContext = t;
        }

        public void init() {

            Log.d(TAG, "init() is called...");

            IntentFilter intentFilter = new IntentFilter();
            //Handover intent
            intentFilter.addAction(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_HR_ACTION);
            intentFilter.addAction(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_HS_ACTION);
            intentFilter.addAction(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_P2P_AUTOGO_AS_SEL_ACTION);

            //Tag access intent
            intentFilter.addAction(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_R_CONFIGURATION_ACTION);
            intentFilter.addAction(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_W_PASSWORD_ACTION);
            intentFilter.addAction(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_R_PASSWORD_ACTION);
            intentFilter.addAction(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_W_CONFIGURATION_ACTION);
            intentFilter.addAction(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_ER_R_PASSWORD_ACTION);
            intentFilter.addAction("mtk.wps.nfc.testbed.w.hs.configuration");

            mContext.registerReceiver(this, intentFilter);
        }

        public void deInit() {
            Log.d(TAG, "deInit() is called...");
            mContext.unregisterReceiver(this);
        }

        void arrayExceptionCheck(byte[] array)throws Exception {
            if (array == null)
                throw new Exception(" byteArray == NULL");
        }



        NdefMessage receivePwdOobIntentAction(Intent intent, boolean isRequest) {

            NdefMessage tryNdefMessage = null;
            byte[] mTmpOobArray = null;
            byte[] mReqWpsPwdArray = null;
            byte[] mSelWpsPwdArray = null;

            mTmpOobArray = intent.getByteArrayExtra(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_EXTRA_PASSWORD);

            Log.e(TAG, "isRequest:" + isRequest + " mTmpOobArray Array: " + Util.printNdef(mTmpOobArray));

            try {
                tryNdefMessage = new NdefMessage(mTmpOobArray);
            } catch (FormatException e) {
                Log.e(TAG, "new NdefMessage(pwdArray) Exception:" + e);

                if (isRequest)
                    mReqWpsNdef = null;
                else
                    mSelWpsNdef = null;

                Log.e(TAG, "    return; ");
                return null;
            }

            WpsP2pCarrierConfiguration ccr_wifi = WpsP2pCarrierConfiguration.tryParse(tryNdefMessage);

            final byte[] DEFAULT_ID = new byte[]{0x30};
            byte[] idArray;
            NdefMessage recordNdefMessage = null;

            if (ccr_wifi == null) {

                Log.e(TAG, "!!!! exception : mReceiver  MTK_WPS_NFC_TESTBED_EXTRA_PASSWORD invalid return error;");

                if (isRequest)
                    mReqWpsNdef = null;
                else
                    mSelWpsNdef = null;

                return null;
            } else {

                NdefMessage handoverMsg = null;

                NdefRecord r0 = tryNdefMessage.getRecords()[0];

                idArray = ccr_wifi.getId();
                if (idArray.length == 0) {
                    Log.d(TAG, "idArray.length == 0   , set PayloadId to  0x30 ");

                    NdefRecord hoRecord = new NdefRecord(NdefRecord.TNF_MIME_MEDIA,
                    r0.getType(), DEFAULT_ID, r0.getPayload());

                    recordNdefMessage = new NdefMessage(hoRecord);
                }
                else
                    Log.d(TAG, "idArray.length != 0  id:" + idArray[0]);


                if (isRequest) {
                    mReqWpsPwdArray = (idArray.length == 0) ? recordNdefMessage.toByteArray() : tryNdefMessage.toByteArray();
                    Log.d(TAG, "prepare  mReqWpsPwdArray: " + Util.printNdef(mReqWpsPwdArray));


                    try {
                        handoverMsg = WpsHandoverBuilderParser.createWfLegacyHrM(mReqWpsPwdArray, powerUpWifi());
                    } catch (FormatException e) {
                        Log.e(TAG, "createWfLegacyHrM   Exception: " + e);
                        e.printStackTrace();
                    }


                }
                else {
                    mSelWpsPwdArray = (idArray.length == 0) ? recordNdefMessage.toByteArray() : tryNdefMessage.toByteArray();
                    Log.d(TAG, "prepare  mSelWpsPwdArray: " + Util.printNdef(mSelWpsPwdArray));


                    try {
                        handoverMsg = WpsHandoverBuilderParser.createWfLegacyHsM(mSelWpsPwdArray, powerUpWifi());
                    } catch (FormatException e) {
                        Log.e(TAG, "createWfLegacyHsM   Exception: " + e);
                        e.printStackTrace();
                    }
                }

                return handoverMsg;


            }


        }


        @Override
        public void onReceive(Context context, Intent intent) {
            NdefMessage tryNdefMessage = null;
            String action = intent.getAction();

            Log.d(TAG, " onReceive() An intent is coming... " + intent.getAction());

            if(action == null){
                Log.e(TAG, "onReceive() action == null");
                return;
            }

            if (action.equals(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_HR_ACTION)) {
                Log.d(TAG, "mReceiver: MTK_WPS_NFC_TESTBED_HR_ACTION");

                mReqWpsNdef = receivePwdOobIntentAction(intent, true);

                Intent intentHr = new Intent(context,
                            NfcForegroundDispatchActivity.class);
                intentHr.putExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_CMD, INfcWpsAppInternal.WPS_HANDOVER_REQUEST_CMD);
                intentHr.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intentHr);

                return;
            } else if (action.equals(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_HS_ACTION)) {
                Log.d(TAG, "mReceiver: MTK_WPS_NFC_TESTBED_HS_ACTION S");


                mSelWpsNdef = receivePwdOobIntentAction(intent, false);
                Intent intentHs = new Intent(context,
                            NfcForegroundDispatchActivity.class);
                intentHs.putExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_CMD, INfcWpsAppInternal.WPS_HANDOVER_SELECT_CMD);

                intentHs.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intentHs);

                return;
            }
            else if (action.equals(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_P2P_AUTOGO_AS_SEL_ACTION)) {
                Log.d(TAG, "mReceiver: MTK_WPS_NFC_TESTBED_P2PGO_AS_SEL_ACTION ");

                mSelWpsNdef = receivePwdOobIntentAction(intent, false);

                Intent intentP2pAutoGO = new Intent(context,
                            NfcForegroundDispatchActivity.class);
                intentP2pAutoGO.putExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_CMD, INfcWpsAppInternal.WPS_P2P_AUTOGO_AS_SEL_CMD);

                intentP2pAutoGO.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intentP2pAutoGO);

            }
            //Tag Access handle below
            // [1] RC
            else if (action.equals(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_R_CONFIGURATION_ACTION)) {

                // start Nfc Foreground Dispatch activity with command [1]
                Intent intentReadCfgToken = new Intent(context,
                        NfcForegroundDispatchActivity.class);
                intentReadCfgToken.putExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_CMD, INfcWpsAppInternal.READ_CONFIGURATION_TOKEN_CMD);
                intentReadCfgToken.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intentReadCfgToken);
            }
            // [2] WP
            else if (action.equals(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_W_PASSWORD_ACTION)) {

                byte[] pwdArray =  intent.getByteArrayExtra(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_EXTRA_PASSWORD);
                Log.d(TAG, " MTK_WPS_NFC_TESTBED_EXTRA_PASSWORD:" + Util.printNdef(pwdArray));

                try {
                    tryNdefMessage = new NdefMessage(pwdArray);

                    //p2pNdefCheck(p2pHrM);
                } catch (FormatException e) {
                     Log.e(TAG, "new NdefMessage(pwdArray) Exception:" + e);
                }

                WifiCarrierConfiguration ccr_wifi = WifiCarrierConfiguration.tryParse(tryNdefMessage);

                if (ccr_wifi == null) {
                    Log.e(TAG, "!!!! inValid Tag,  not PwdToken format, toast prompt !!!! ");
                    Toast.makeText(mContext, "PwdToken format inValid, please check", Toast.LENGTH_LONG).show();
                    return;
                }


                PasswordToken pwdTokenData = ccr_wifi.getPasswordToken();

                if (pwdTokenData == null) {
                    Log.e(TAG, "!!!! exception : onReceive()  MTK_WPS_NFC_TESTBED_EXTRA_PASSWORD invalid return;");
                    Toast.makeText(mContext, "PwdToken format inValid, no PwdToken ", Toast.LENGTH_LONG).show();
                    return;
                }


                // start Nfc Foreground Dispatch activity with command [2]
                Intent intentWritePwdToken = new Intent(context,
                        NfcForegroundDispatchActivity.class);
                intentWritePwdToken.putExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_CMD, INfcWpsAppInternal.WRITE_PASSWORD_TOKEN_CMD);
                intentWritePwdToken.putExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_PWD_TOKEN, pwdArray);
                intentWritePwdToken.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intentWritePwdToken);

            }
            // [3] RP
            else if (action.equals(
                    INfcWpsTestBed.MTK_WPS_NFC_TESTBED_R_PASSWORD_ACTION)) {

                // start Nfc Foreground Dispatch activity with command [3]
                Intent intentReadPwdToken = new Intent(context,
                        NfcForegroundDispatchActivity.class);
                intentReadPwdToken.putExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_CMD, INfcWpsAppInternal.READ_PASSWORD_TOKEN_CMD);
                intentReadPwdToken.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intentReadPwdToken);
            }
            //[4] WC
            else if (action.equals(
                    INfcWpsTestBed.MTK_WPS_NFC_TESTBED_W_CONFIGURATION_ACTION)) {


                byte[] cfgArray =  intent.getByteArrayExtra(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_EXTRA_CONFIGURATION);
                Log.d(TAG, " MTK_WPS_NFC_TESTBED_EXTRA_CONFIGURATION:" + Util.printNdef(cfgArray));

                try {
                    tryNdefMessage = new NdefMessage(cfgArray);

                    //p2pNdefCheck(p2pHrM);
                } catch (FormatException e) {
                    Log.e(TAG, "new NdefMessage(cfgArray) Exception:" + e);
                }

                WifiCarrierConfiguration ccr_wifi = WifiCarrierConfiguration.tryParse(tryNdefMessage);


                if (ccr_wifi == null) {
                    Log.e(TAG, "!!!! inValid Tag,  not Credential format , toast prompt !!!! ");
                    Toast.makeText(mContext, "Credential format inValid, please check ", Toast.LENGTH_LONG).show();
                    return;
                }


                Credential credentialData = ccr_wifi.getCredential();
                //PasswordToken pwdTokenData = ccr_wifi.getPasswordToken();

                if (credentialData == null) {
                    Log.e(TAG, "!!!! exception : onReceive()  MTK_WPS_NFC_TESTBED_EXTRA_CONFIGURATION invalid return;");
                    Toast.makeText(mContext, "Credential format inValid, no Credential ", Toast.LENGTH_LONG).show();
                    return;
                }



                // start Nfc Foreground Dispatch activity with command [4]
                Intent intentWriteCfgToken = new Intent(context,
                            NfcForegroundDispatchActivity.class);
                intentWriteCfgToken.putExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_CMD, INfcWpsAppInternal.WRITE_CONFIGURATION_TOKEN_CMD);
                intentWriteCfgToken.putExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_CONFIGURATION_TOKEN, cfgArray);
                intentWriteCfgToken.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intentWriteCfgToken);

            }
            //[extra 1]  test to write Cfg token with Hs header
            else if (action.equals(
                    "mtk.wps.nfc.testbed.w.hs.configuration")) {

                final byte[] DEFAULT_ID = new byte[]{0x30};
                byte[] idArray;
                NdefMessage cfgNdefMessage = null;
                NdefMessage mWLHsCfg = null;


                byte[] cfgArray =  intent.getByteArrayExtra(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_EXTRA_CONFIGURATION);
                Log.d(TAG, "Hs MTK_WPS_NFC_TESTBED_EXTRA_CONFIGURATION:" + Util.printNdef(cfgArray));

                try {
                    tryNdefMessage = new NdefMessage(cfgArray);

                    //p2pNdefCheck(p2pHrM);
                } catch (FormatException e) {
                    Log.e(TAG, "new NdefMessage(cfgArray) Exception:" + e);
                }


                tryNdefMessage = WifiCarrierConfiguration.getWiFiOOB(tryNdefMessage);

                WifiCarrierConfiguration ccr_wifi = WifiCarrierConfiguration.tryParse(tryNdefMessage);

                if (ccr_wifi == null) {
                    Log.e(TAG, "!!!! exception : onReceive()  MTK_WPS_NFC_TESTBED_EXTRA_CONFIGURATION ccr_wifi == null invalid return;");
                    return;
                }

                Credential credentialData = ccr_wifi.getCredential();
                //PasswordToken pwdTokenData = ccr_wifi.getPasswordToken();

                if (credentialData == null) {
                    Log.e(TAG, "!!!! exception : onReceive()  MTK_WPS_NFC_TESTBED_EXTRA_CONFIGURATION invalid return;");
                    return;
                }


                NdefRecord r0 = tryNdefMessage.getRecords()[0];

                idArray = ccr_wifi.getId();
                if (idArray.length == 0) {
                    Log.d(TAG, "idArray.length == 0   , set PayloadId to " + DEFAULT_ID);

                    NdefRecord hoRecord = new NdefRecord(NdefRecord.TNF_MIME_MEDIA,
                    r0.getType(), DEFAULT_ID, r0.getPayload());

                    cfgNdefMessage = new NdefMessage(hoRecord);
                }
                else {
                    Log.d(TAG, "idArray.length != 0   , idArray:" + idArray[0]);
                    cfgNdefMessage = tryNdefMessage;
                }

                // TODO:: Wifi pwr State set to true
                mWLHsCfg = WpsHandoverBuilderParser.packWfLegacyHsM(cfgNdefMessage, true); //powerUpWifi()




                // start Nfc Foreground Dispatch activity with command [4]
                Intent intentWriteCfgToken = new Intent(context,
                            NfcForegroundDispatchActivity.class);
                intentWriteCfgToken.putExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_CMD, INfcWpsAppInternal.TEST_WRITE_HS_CONFIGURATION_TOKEN_CMD);
                intentWriteCfgToken.putExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_CONFIGURATION_TOKEN, mWLHsCfg.toByteArray());
                intentWriteCfgToken.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intentWriteCfgToken);

            }
            //ER R P
            else if (action.equals(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_ER_R_PASSWORD_ACTION)) {

                // start Nfc Foreground Dispatch activity with command [3]
                Intent intentReadPwdToken = new Intent(context,
                        NfcForegroundDispatchActivity.class);
                intentReadPwdToken.putExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_CMD, INfcWpsAppInternal.ER_READ_PASSWORD_TOKEN_CMD);
                intentReadPwdToken.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intentReadPwdToken);
            }
            else
                Log.e(TAG, "mReceiver: error Receiver case");


        }// end of onreceive

    }

    public static class WpsHandoverBuilderParser {
        static final String TAG = "WpsHandoverBuilderParser";
        static final boolean DBG = true;


    // create a entire Wps HrM r6 modify
    public static NdefMessage createWfLegacyHrM(byte[] pwdOOBArray, boolean wifiCPS)throws FormatException {
        NdefMessage wpsHrM = null;
        NdefMessage pwdOobMessage = null;

        Log.d(TAG, "createWfLegacyHrM() with pwd OOB");

        try {
            pwdOobMessage = new NdefMessage(pwdOOBArray);

            //p2pNdefCheck(p2pDevInfoMessage);
        } catch (FormatException e) {
            throw new FormatException("p2pNdefCheck Exception:" + e);
        }

        wpsHrM = packWfLegacyHrM(pwdOobMessage, wifiCPS);

        return wpsHrM;
    }


    public static NdefMessage packWfLegacyHrM(NdefMessage p2pDevInfoMessage, boolean wifiCPS) {

        byte[] WfLegacyRecordId;

        // create Wfa P2p ccR
        WpsP2pCarrierConfiguration mWfLegacyCCR = WpsP2pCarrierConfiguration.tryParse(p2pDevInfoMessage);

        WfLegacyRecordId = mWfLegacyCCR.getId();


        Log.d(TAG, "Wps RecordId.length:" + WfLegacyRecordId.length);

        if (WfLegacyRecordId.length == 0) {
            Log.d(TAG, "should Exception !! ");
        }

        Log.d(TAG, "mWfLegacyCCR.getId():" + Util.bytesToString(WfLegacyRecordId) + "   wifiCPS:" + wifiCPS);


        // create HsM with BT + WiFiCCR(with extension TLVs)
        HandoverMessage msg_mtk_request = new HandoverMessage();
        //WifiCarrierConfiguration CCR = new WifiCarrierConfiguration(credential);
        //msg_mtk_select.appendAlternativeCarrier(btCPS ? HandoverMessage.CARRIER_POWER_STATE_ACTIVE : HandoverMessage.CARRIER_POWER_STATE_ACTIVATING, btCCR);
        msg_mtk_request.appendAlternativeCarrier(wifiCPS ? HandoverMessage.CARRIER_POWER_STATE_ACTIVE : HandoverMessage.CARRIER_POWER_STATE_ACTIVATING, mWfLegacyCCR, null, WfLegacyRecordId);
        NdefMessage mtk_hr = msg_mtk_request.createHandoverRequestMessage();

        return mtk_hr;
    }


    // create a entire Wfa P2p HsM .
    public static NdefMessage createWfLegacyHsM(byte[] p2pArrayInfo, boolean wifiCPS)throws FormatException {
        NdefMessage wpsHsM = null;
        NdefMessage p2pDevInfoMessage = null;

        Log.d(TAG, "createWfLegacyHsM()");

        try {
            p2pDevInfoMessage = new NdefMessage(p2pArrayInfo);

            //p2pNdefCheck(p2pDevInfoMessage);
        } catch (FormatException e) {
            throw new FormatException("p2pNdefCheck Exception:" + e);
        }

        wpsHsM = packWfLegacyHsM(p2pDevInfoMessage, wifiCPS);

        return wpsHsM;
    }

    public static NdefMessage packWfLegacyHsM(NdefMessage p2pDevInfoMessage, boolean wifiCPS) {

        byte[] WfLegacyRecordId;

        // create Wfa P2p ccR
        WpsP2pCarrierConfiguration mWfLegacyCCR = WpsP2pCarrierConfiguration.tryParse(p2pDevInfoMessage);

        WfLegacyRecordId = mWfLegacyCCR.getId();


        Log.d(TAG, "WfLegacyRecordId.length:" + WfLegacyRecordId.length);

        if (WfLegacyRecordId.length == 0) {
            Log.d(TAG, "should Exception !! ");
        }

        Log.d(TAG, "mWfLegacyCCR.getId():" + Util.bytesToString(WfLegacyRecordId) + "   wifiCPS:" + wifiCPS);


        // create HsM with BT + WiFiCCR(with extension TLVs)
        HandoverMessage msg_mtk_select = new HandoverMessage();
        //WifiCarrierConfiguration CCR = new WifiCarrierConfiguration(credential);
        //msg_mtk_select.appendAlternativeCarrier(btCPS ? HandoverMessage.CARRIER_POWER_STATE_ACTIVE : HandoverMessage.CARRIER_POWER_STATE_ACTIVATING, btCCR);
        msg_mtk_select.appendAlternativeCarrier(wifiCPS ? HandoverMessage.CARRIER_POWER_STATE_ACTIVE : HandoverMessage.CARRIER_POWER_STATE_ACTIVATING, mWfLegacyCCR, null, WfLegacyRecordId);
        NdefMessage mtk_hs = msg_mtk_select.createHandoverSelectMessage();

        return mtk_hs;
    }



    }


}

