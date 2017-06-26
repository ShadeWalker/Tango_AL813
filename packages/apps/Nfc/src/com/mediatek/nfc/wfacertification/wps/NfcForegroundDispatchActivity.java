package com.mediatek.nfc.wfacertification.wps;

import java.io.UnsupportedEncodingException;



import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcF;
import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.SharedPreferences;

import android.util.Log;
import android.view.View;
import android.widget.TextView;


import com.mediatek.nfc.Util;

import com.android.nfc.NfcService;
import com.android.nfc.R;

import com.mediatek.nfc.wfacertification.INfcWfaIntent.INfcWpsTestBed;

import com.mediatek.nfc.handoverprotocol.WifiCarrierConfiguration;
import com.mediatek.nfc.handoverprotocol.WifiCarrierConfiguration.Credential;
import com.mediatek.nfc.handoverprotocol.WifiCarrierConfiguration.PasswordToken;

import com.mediatek.nfc.wfacertification.p2p.INfcWfaSigma;


public class NfcForegroundDispatchActivity extends Activity {
    static final String TAG = "NfcForegroundDispatchActivity";
    static final boolean DBG = true;




    public static final int MTK_PREF_LEGACY_REQ_CASE    = 0x20;
    public static final int MTK_PREF_LEGACY_SEL_CASE    = 0x21;
    public static final int MTK_PREF_WFA_P2P_CASE       = 0x22;

    public static final String NFC_HANDOVER_SCENARIO = "nfc_handover_scenario";




    NfcManager mManager;
    NfcAdapter mAdapter;
    PendingIntent pendingIntent;
    IntentFilter[] intentFiltersArray;
    String[][] techListsArray;

    int command = 0;
    byte[] mPasswordToken;
    byte[] mConfigurationToken;

    private Handler mUI_Handler = new Handler();

    private String errorHandleString;

    private boolean caseExternalRegistrar = false;

    private boolean foregroundDispatch = true;

    private SharedPreferences mPrefs;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, " onCreate():  activity.this:" + this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mtk_wps_using_nfc_foreground_dispatch);

        setTitle("NFC Foreground Dispatch");

        TextView mTextView0 = (TextView) findViewById(R.id.textView0);
        mTextView0.setVisibility(View.VISIBLE);
        mTextView0.setText("Put this device near to a NDEF tag/P2P device");

        mPrefs = getApplicationContext().getSharedPreferences(NfcService.PREF, Context.MODE_PRIVATE);

        Intent iii = this.getIntent();
        command = iii.getIntExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_CMD, INfcWpsAppInternal.UNKNOWN_CMD);

        // if command = 2, get pswToken
        if (command == INfcWpsAppInternal.WRITE_PASSWORD_TOKEN_CMD) {
            Log.d(TAG, " command == INfcWpsAppInternal.WRITE_PASSWORD_TOKEN_CMD");
            //Parcelable parcelablePswToken = (Parcelable) iii
            //      .getParcelableExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_PWD_TOKEN);
            //mPasswordToken = (PasswordToken) parcelablePswToken;
            mPasswordToken = iii.getByteArrayExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_PWD_TOKEN);

        }

        // if command = 4, get configToken
        if (command == INfcWpsAppInternal.WRITE_CONFIGURATION_TOKEN_CMD) {
            Log.d(TAG, " command == INfcWpsAppInternal.WRITE_CONFIGURATION_TOKEN_CMD");
            //Parcelable parcelableCfgToken = (Parcelable) iii
            //      .getParcelableExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_CONFIGURATION_TOKEN);
            //mConfigurationToken = (ConfigurationToken) parcelableCfgToken;
            mConfigurationToken = iii.getByteArrayExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_CONFIGURATION_TOKEN);
        }


        if (command == INfcWpsAppInternal.TEST_WRITE_HS_CONFIGURATION_TOKEN_CMD) {
            Log.d(TAG, " command == INfcWpsAppInternal.TEST_WRITE_HS_CONFIGURATION_TOKEN_CMD");
            mConfigurationToken = iii.getByteArrayExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_CONFIGURATION_TOKEN);
        }



        Log.d(TAG, " EXTRA_NFC_WPS_CMD = " + command);

        if (command == INfcWpsAppInternal.UNKNOWN_CMD) {
            Log.d(TAG, " command == INfcWpsAppInternal.UNKNOWN_CMD     activity.this:" + this);
            errorHandleString = "Please Re-Launch Activity ";

            foregroundDispatch = false;

            mUI_Handler.postDelayed(runnable_text1ErrorShow, 500);
            mUI_Handler.postDelayed(runnable_finish, 3000);
            return;
        }


        NfcManager mManager = (NfcManager) this
                .getSystemService(Context.NFC_SERVICE);
        mAdapter = mManager.getDefaultAdapter();


        if (command == INfcWpsAppInternal.WPS_HANDOVER_SELECT_CMD) {
            Log.d(TAG, " command == INfcWpsAppInternal.WPS_HANDOVER_SELECT_CMD");
            //mAdapter.setMtkWpsNdefMessage(null,this,false);
            setHandoverNeed(MTK_PREF_LEGACY_SEL_CASE, false);
            foregroundDispatch = false;
            return;

        }

        if (command == INfcWpsAppInternal.WPS_HANDOVER_REQUEST_CMD) {
            Log.d(TAG, " command == INfcWpsAppInternal.WPS_HANDOVER_REQUEST_CMD");
            setHandoverNeed(MTK_PREF_LEGACY_REQ_CASE, true);
            foregroundDispatch = false;
            return;
        }


        if (command == INfcWpsAppInternal.WPS_P2P_AUTOGO_AS_SEL_CMD) {
            Log.d(TAG, " command == INfcWpsAppInternal.WPS_P2P_AUTOGO_AS_SEL_CMD    ");

            foregroundDispatch = false;
            mTextView0.setText("Wi-Fi Direct Auto GO as Selector \nPut this device near to P2P device");
            setHandoverNeed(MTK_PREF_LEGACY_SEL_CASE, false);
            return;
        }

        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
                getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        //
        IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        try {
            ndef.addDataType("*/*"); /*
                                     * Handles all MIME based dispatches. You
                                     * should specify only the ones that you
                                     * need.
                                     */
        } catch (MalformedMimeTypeException e) {
            throw new RuntimeException("fail", e);
        }

        IntentFilter tech = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        IntentFilter tag = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);

        intentFiltersArray = new IntentFilter[] { ndef, tech, tag };
        techListsArray = new String[][] { new String[] { NfcF.class.getName() }, new String[] { Ndef.class.getName() } };
    }

    public void onRestart() {
        Log.d(TAG, " onRestart() : command:" + printWpsAppInternalCmd(command));
        super.onRestart();
    }

    public void onPause() {
        Log.d(TAG, " onPause() : command:" + printWpsAppInternalCmd(command) + "  foregroundDispatch:" + foregroundDispatch);
        super.onPause();
        if (foregroundDispatch)
            mAdapter.disableForegroundDispatch(this);
    }

    public void onResume() {
        Log.d(TAG, " onResume() : command:" + printWpsAppInternalCmd(command) + "  foregroundDispatch:" + foregroundDispatch);
        super.onResume();

        if (foregroundDispatch) {
            Log.d(TAG, " mAdapter.enableForegroundDispatch");
            mAdapter.enableForegroundDispatch(this, pendingIntent,
                    intentFiltersArray, techListsArray);
        }
    }

    public void onDestroy() {
        Log.d(TAG, " onDestroy(): command:"+printWpsAppInternalCmd(command));
        super.onDestroy();

    }

    public void onStop() {
        Log.d(TAG, " onStop(): command:"+printWpsAppInternalCmd(command));
        super.onStop();
        
        Log.d(TAG, "finish activity directly");
        finish();

    }


    public void onNewIntent(Intent intent) {
        Log.d(TAG, " onNewIntent()  command: "+printWpsAppInternalCmd(command));
        Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

        byte[] readPayload = new byte[] {};
        NdefMessage readNdef = null;

        if (tagFromIntent == null) {
            Log.d(TAG, "========================================= ");
            Log.d(TAG, "onNewIntent()   tagFromIntent == null  activity.this:" + this);
            int testCommand = intent.getIntExtra(INfcWpsAppInternal.EXTRA_NFC_WPS_INTERNAL_CMD, 0);
            Log.d(TAG, "     WPS_INTERNAL_CMD:" + testCommand);
            Log.d(TAG, "========================================= ");

            //if(testCommand == INfcWpsAppInternal.HANDOVER_REQUEST_CMD)
            //    mAdapter.setMtkLegacyPushUris(new Uri[0],this,true);
            //if(testCommand == INfcWpsAppInternal.HANDOVER_SELECT_CMD)
            //    mAdapter.setMtkLegacyPushUris(new Uri[0],this,false);
            //else

            if (testCommand == INfcWpsAppInternal.HANDOVER_FINISH_CMD)
                mUI_Handler.postDelayed(runnable_finish, 1000);

            //Wifi Legacy handle, update UI string ,close this activity
            return;
        }
        else
            Log.d(TAG, "onNewIntent()   tagFromIntent.toString() " + tagFromIntent.toString());

        switch (command) {

        case INfcWpsAppInternal.READ_CONFIGURATION_TOKEN_CMD:
            Log.d(TAG, " READ_CONFIGURATION_TOKEN_CMD :");
            // Read Configuration Token
            // 1. read a configuration token Ndef record
            readNdef = readTag(tagFromIntent);

            if (readNdef == null) {
                Log.e(TAG, " read NDEF is null");
                errorHandleString = "NDEF is null";
                mUI_Handler.postDelayed(runnable_text1ErrorShow, 500);
                mUI_Handler.postDelayed(runnable_finish, 3000);
                return;
            }

            mUI_Handler.post(runnable_readCfgTag);

            Log.d(TAG, " readNdef:" + Util.printNdef(readNdef));

            // 2. parse the token and get the Cfg, set into ConfigurationToken
            //ConfigurationToken config = parseCfgTokenNdefPayloadToConfigurationToken(readPayload);
            mUI_Handler.postDelayed(runnable_parseCfgTag, 1000);


            NdefMessage wiFiOobMsg = WifiCarrierConfiguration.getWiFiOOB(readNdef);


            WifiCarrierConfiguration ccr_wifi = WifiCarrierConfiguration.tryParse(wiFiOobMsg);

            if (ccr_wifi == null) {
                Log.e(TAG, "!!!! inValid Tag, format not match  !!!! ");
                errorHandleString = "inValid Tag, format not match";
                mUI_Handler.postDelayed(runnable_text3ErrorShow, 1000);
                mUI_Handler.postDelayed(runnable_finish, 3000);
                return;
            }


            Credential credentialData = ccr_wifi.getCredential();
            //PasswordToken pwdTokenData = ccr_wifi.getPasswordToken();

            if (credentialData == null) {
                Log.e(TAG, " read NDEF is not Configuration data ");
                errorHandleString = "read NDEF is not Configuration data";
                mUI_Handler.postDelayed(runnable_text3ErrorShow, 500);
                mUI_Handler.postDelayed(runnable_finish, 3000);
                return;
            }

            // 3. send a broadcast with Cfg
            Log.d(TAG, "==== sendBroadcast(intentReadCfgToken);");
            Intent intentRCR = new Intent(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_CONFIGURATION_RECEIVED_ACTION);

            intentRCR.putExtra(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_EXTRA_CONFIGURATION,
                    wiFiOobMsg.toByteArray());
            sendBroadcast(intentRCR);

            //{Sigma process -- send broadcast

                Log.d(TAG, "[SIGMA case] ==== sendBroadcast(intentReadCfgToken);");
                Intent intentSigmaReadCfg = new Intent(INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_CFG_TAG_RECEIVE_ACTION);

                intentSigmaReadCfg.putExtra(INfcWfaSigma.MTK_NFC_WFA_SIGMA_TAG_EXTRA_DEV_INFO,
                        wiFiOobMsg.toByteArray());
                sendBroadcast(intentSigmaReadCfg);
            //}

            mUI_Handler.postDelayed(runnable_sendCfgIntent, 2000);
            mUI_Handler.postDelayed(runnable_finish, 3000);

            break;

        case INfcWpsAppInternal.WRITE_PASSWORD_TOKEN_CMD:
            // Write Password Token
            // 1. get the PWD from the intent
            Log.d(TAG, " WRITE_PASSWORD_TOKEN_CMD :");
            //PasswordToken pwdToken = mPasswordToken;

            if (mPasswordToken == null) {
                Log.e(TAG, " mPasswordToken == null ");
                errorHandleString = "Pwd Array is null";
                mUI_Handler.postDelayed(runnable_text1ErrorShow, 500);
                mUI_Handler.postDelayed(runnable_finish, 3000);
                return;
            }

            mUI_Handler.post(runnable_getPwd);

            //Log.d(TAG, "" + new String(pwdToken.getPublicKeyHash()));

            // 2. build a NDEF record with PWD
            //byte[] payloadP = buildPswTokenNdefPayload(pwdToken.getPwdId(),
            //      pwdToken.getPublicKeyHash(), pwdToken.getDevPwd(),
            //      pwdToken.getVendorEx());
            mUI_Handler.postDelayed(runnable_buildPwdNdef, 1000);

            Log.d(TAG, "After build Ndef");


            boolean writeResult = false;
            // 3. write the PWD into a token
            try {
                writeResult = writeTag(tagFromIntent, mPasswordToken);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            if (writeResult)
            mUI_Handler.postDelayed(runnable_writePwdToken, 1500);
            else {
                errorHandleString = "!!!! Write Tag Fail !!!!";
                mUI_Handler.postDelayed(runnable_text3ErrorShow, 1500);
            }


            mUI_Handler.postDelayed(runnable_finish, 3000);

            break;

        case INfcWpsAppInternal.ER_READ_PASSWORD_TOKEN_CMD:
            caseExternalRegistrar = true;

        case INfcWpsAppInternal.READ_PASSWORD_TOKEN_CMD:
            if (caseExternalRegistrar)
                Log.d(TAG, " ER_READ_PASSWORD_TOKEN_CMD :");
            else
            Log.d(TAG, " READ_PASSWORD_TOKEN_CMD :");

            // Read Password Token
            // 1. read a password token Ndef record
            readNdef = readTag(tagFromIntent);

            Log.d(TAG, " readNdef :" + Util.printNdef(readNdef));

            if (readNdef == null) {
                Log.e(TAG, " read NDEF is null");
                errorHandleString = "NDEF is null";
                mUI_Handler.postDelayed(runnable_text1ErrorShow, 500);
                mUI_Handler.postDelayed(runnable_finish, 3000);
                return;
            }

            mUI_Handler.post(runnable_readPwdTag);

            // 2. parse the token and get the PSW, set into PasswordToken
            //PasswordToken psw = parsePwdTokenNdefPayloadToPasswordToken(readPayload);
            mUI_Handler.postDelayed(runnable_parsePwdTag, 1000);

            WifiCarrierConfiguration wifiCCR = WifiCarrierConfiguration.tryParse(readNdef);

            if (wifiCCR == null) {
                Log.e(TAG, "!!!! inValid Tag, format not match  !!!! ");
                errorHandleString = "inValid Tag, format not match";
                mUI_Handler.postDelayed(runnable_text3ErrorShow, 1000);
                mUI_Handler.postDelayed(runnable_finish, 3000);
                return;
            }


            //Credential credentialData = ccr_wifi.getCredential();
            PasswordToken pwdTokenData = wifiCCR.getPasswordToken();

            if (pwdTokenData == null) {
                Log.e(TAG, " read NDEF is not PasswordToken data ");
                errorHandleString = "read NDEF is not PasswordToken data";
                mUI_Handler.postDelayed(runnable_text3ErrorShow, 500);
                mUI_Handler.postDelayed(runnable_finish, 3000);
                return;
            }


            // 3. send a broadcast with PSW
            if (caseExternalRegistrar) {
                Log.d(TAG, "==== sendBroadcast(intent ER ReadPwdToken);");
                Intent intentRPR = new Intent(
                    INfcWpsTestBed.MTK_WPS_NFC_TESTBED_ER_PASSWORD_RECEIVED_ACTION);
                intentRPR.putExtra(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_EXTRA_PASSWORD, readNdef.toByteArray());
                sendBroadcast(intentRPR);
            }
            else {
                Log.d(TAG, "==== sendBroadcast(intentReadPwdToken);");
                Intent intentRPR = new Intent(
                    INfcWpsTestBed.MTK_WPS_NFC_TESTBED_PASSWORD_RECEIVED_ACTION);
                intentRPR.putExtra(INfcWpsTestBed.MTK_WPS_NFC_TESTBED_EXTRA_PASSWORD, readNdef.toByteArray());
                sendBroadcast(intentRPR);
            }

            mUI_Handler.postDelayed(runnable_sendPwdIntent, 2000);
            mUI_Handler.postDelayed(runnable_finish, 3000);

            break;

        case INfcWpsAppInternal.TEST_WRITE_HS_CONFIGURATION_TOKEN_CMD:
            Log.d(TAG, " TEST_WRITE_HS_CONFIGURATION_TOKEN_CMD :");

        case INfcWpsAppInternal.WRITE_CONFIGURATION_TOKEN_CMD:

            if (command == INfcWpsAppInternal.WRITE_CONFIGURATION_TOKEN_CMD)
            Log.d(TAG, " WRITE_CONFIGURATION_TOKEN_CMD :");
            // Write Configuration Token
            // 1. get the CFG from the intent

            //ConfigurationToken cfgToken = mConfigurationToken;

            if (mConfigurationToken == null) {
                Log.e(TAG, " mConfigurationToken == null ");
                errorHandleString = "Cfg Array is null";
                mUI_Handler.postDelayed(runnable_text1ErrorShow, 500);
                mUI_Handler.postDelayed(runnable_finish, 3000);
                return;
            }


            mUI_Handler.post(runnable_getCfg);

            // 2. build a NDEF record with CFG
            //byte[] payloadC = buildCfgTokenNdefPayload(
            //      cfgToken.getNetworkIndex(), cfgToken.getSSID(),
            //      cfgToken.getAuthType(), cfgToken.getEncrypType(),
            //      cfgToken.getMacAddress(), cfgToken.getNetworkKey(),
            //      cfgToken.getVendorExtension());

            mUI_Handler.postDelayed(runnable_buildCfgNdef, 1000);

            boolean writeCfgResult = false;

            // 3. write the CFG into a token
            try {
                writeCfgResult = writeTag(tagFromIntent, mConfigurationToken);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            if (writeCfgResult)
            mUI_Handler.postDelayed(runnable_writeCfgToken, 1500);
            else {
                errorHandleString = "!!!! Write Tag Fail !!!!";
                mUI_Handler.postDelayed(runnable_text3ErrorShow, 1500);
            }

            mUI_Handler.postDelayed(runnable_finish, 3000);

            break;

        default:
            Log.e(TAG, "unknown command: " + command);
            break;
        }
    }// end of onNewIntent

    // error handle show text1
    private Runnable runnable_text1ErrorShow = new Runnable() {
        public void run() {
            TextView tt1_1 = (TextView) findViewById(R.id.textView1);
            tt1_1.setVisibility(View.VISIBLE);
            if (errorHandleString == null)
                errorHandleString = "error NULL";
            tt1_1.setText(errorHandleString); //R.string.reading_Cfg_tag);
        }
    };

    // error handle show text3
    private Runnable runnable_text3ErrorShow = new Runnable() {
        public void run() {
            TextView tt1_3 = (TextView) findViewById(R.id.textView3);
            tt1_3.setVisibility(View.VISIBLE);
            tt1_3.setText(errorHandleString); //R.string.sending_Cfg_intent);
        }
    };


    // 1
    private Runnable runnable_readCfgTag = new Runnable() {
        public void run() {
            TextView tt1_1 = (TextView) findViewById(R.id.textView1);
            tt1_1.setVisibility(View.VISIBLE);
            tt1_1.setText("Reading a Cfg Tag."); //R.string.reading_Cfg_tag);
        }
    };

    private Runnable runnable_parseCfgTag = new Runnable() {
        public void run() {
            TextView tt1_2 = (TextView) findViewById(R.id.textView2);
            tt1_2.setVisibility(View.VISIBLE);
            tt1_2.setText("Parsing a Cfg Tag."); //R.string.parsing_Cfg_tag);
        }
    };

    private Runnable runnable_sendCfgIntent = new Runnable() {
        public void run() {
            TextView tt1_3 = (TextView) findViewById(R.id.textView3);
            tt1_3.setVisibility(View.VISIBLE);
            tt1_3.setText("Sending a Cfg Intent."); //R.string.sending_Cfg_intent);
        }
    };

    // 2
    private Runnable runnable_getPwd = new Runnable() {
        public void run() {
            TextView tt2_1 = (TextView) findViewById(R.id.textView1);
            tt2_1.setVisibility(View.VISIBLE);
            tt2_1.setText("Getting Pwd from Intent."); //R.string.getting_Pwd_from_intent);
        }
    };

    private Runnable runnable_buildPwdNdef = new Runnable() {
        public void run() {
            TextView tt2_2 = (TextView) findViewById(R.id.textView2);
            tt2_2.setVisibility(View.VISIBLE);
            tt2_2.setText("Building Pwd Ndef."); //R.string.building_Pwd_Ndef_record);
        }
    };

    private Runnable runnable_writePwdToken = new Runnable() {
        public void run() {
            TextView tt2_3 = (TextView) findViewById(R.id.textView3);
            tt2_3.setVisibility(View.VISIBLE);
            tt2_3.setText("Writting Pwd Token Success"); //R.string.writting_Pwd_token);
        }
    };

    // 3
    private Runnable runnable_readPwdTag = new Runnable() {
        public void run() {
            TextView tt3_1 = (TextView) findViewById(R.id.textView1);
            tt3_1.setVisibility(View.VISIBLE);
            tt3_1.setText("Reading a Pwd Tag."); //R.string.reading_Pwd_tag);
        }
    };

    private Runnable runnable_parsePwdTag = new Runnable() {
        public void run() {
            TextView tt3_2 = (TextView) findViewById(R.id.textView2);
            tt3_2.setVisibility(View.VISIBLE);
            tt3_2.setText("parsing a Pwd Tag."); //R.string.parsing_Pwd_tag);
        }
    };

    private Runnable runnable_sendPwdIntent = new Runnable() {
        public void run() {
            TextView tt3_3 = (TextView) findViewById(R.id.textView3);
            tt3_3.setVisibility(View.VISIBLE);
            tt3_3.setText("sending a Pwd Intent."); //R.string.sending_Pwd_intent);
        }
    };

    // 4
    private Runnable runnable_getCfg = new Runnable() {
        public void run() {
            TextView tt4_1 = (TextView) findViewById(R.id.textView1);
            tt4_1.setVisibility(View.VISIBLE);
            tt4_1.setText("Getting Cfg from Intent."); //R.string.getting_Cfg_from_intent);
        }
    };

    private Runnable runnable_buildCfgNdef = new Runnable() {
        public void run() {
            TextView tt4_2 = (TextView) findViewById(R.id.textView2);
            tt4_2.setVisibility(View.VISIBLE);
            tt4_2.setText("building Cfg Ndef."); //R.string.building_Cfg_Ndef_record);
        }
    };

    private Runnable runnable_writeCfgToken = new Runnable() {
        public void run() {
            TextView tt4_3 = (TextView) findViewById(R.id.textView3);
            tt4_3.setVisibility(View.VISIBLE);
            tt4_3.setText("writting Cfg Token Success"); //R.string.writting_Cfg_token);
        }
    };

    private Runnable runnable_finish = new Runnable() {
        public void run() {
            Log.d(TAG, "runnable_finish()    finish() ");
            finish();
        }
    };

    /*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.mtk_wps_using_nfc_foreground_dispatch, menu);
        return true;
    }
    */

    private boolean writeTag(Tag tag_in, byte[] payload)
            throws UnsupportedEncodingException {

        NdefMessage message = null;
        Log.d(TAG, "write tag begin...");
        //Log.d(TAG, "prepared payload = " + new String(payload));
        if (tag_in == null) {
            Log.d(TAG, "tag = null");
            return false;
        }

        Ndef tag = Ndef.get(tag_in);
        Log.d(TAG, "after get tag~");
        //NdefRecord record = new NdefRecord(NdefRecord.TNF_MIME_MEDIA,
        //      "application/vnd.wfa.wsc".getBytes(), null, payload);
        // dumpBytes(record.toByteArray());

        try {
            Log.d(TAG, "into try~~");
            //NdefRecord[] records = { record };

            try {
                message = new NdefMessage(payload);
            } catch (FormatException e) {
                Log.d(TAG, "New NdefMessage format exception:" + e);
                return false;
            }

            if (tag == null) {
                Log.d(TAG, "tag == null");
                return false;
            }

            tag.connect();
            boolean connected = tag.isConnected();
            Log.d(TAG, "into try step 2~~");
            boolean writeable = tag.isWritable();
            Log.d(TAG, "into try step 3~~");
            if (connected && writeable) {
                Log.d(TAG, "  Write message: ::" + Util.printNdef(message));
                tag.writeNdefMessage(message);
            }
            Log.d(TAG, "into try step 4~~");
            tag.close();
        } catch (Exception e) {
            // do error handling
            Log.d(TAG, "got Exception when writting this tag..." + e);
            return false;
        }
        Log.d(TAG, "==== write tag end.");
        return true;
    } // end of writeTag


    private NdefMessage readTag(Tag tag_in) {
        if (tag_in == null) {
            Log.d(TAG, "tag = null");
            return null;
        }

        Ndef tag = Ndef.get(tag_in);
        NdefMessage retMessage = null;

        if (tag == null) {
            Log.d(TAG, "tag is null   return;");

            return null;
        }


        try {
            tag.connect();
            boolean connected = tag.isConnected();
            if (connected) {

                Log.d(TAG, "readTag() into connected");
                retMessage = tag.getNdefMessage();
                //NdefRecord[] records = message.getRecords();

            }
            tag.close();
        } catch (Exception e) {
            Log.e(TAG, "readTag Exception:" + e);
            e.printStackTrace();
        }
        return retMessage;
    }// end of readTag

    private void dumpBytes(byte[] in) {
        StringBuilder builder = new StringBuilder();
        for (int counter = 0; counter < in.length; counter++) {
            builder.append(Integer.toHexString(in[counter] & 0xFF)).append(" ");
        }

        Log.d(TAG, "tag content =" + builder.toString());
        // System.out.println(builder.toString());
    }// end of dumpBytes



    public String getHexString(byte[] raw) throws UnsupportedEncodingException {
        final byte[] HEX_CHAR_TABLE = { (byte) '0', (byte) '1', (byte) '2',
                (byte) '3', (byte) '4', (byte) '5', (byte) '6', (byte) '7',
                (byte) '8', (byte) '9', (byte) 'a', (byte) 'b', (byte) 'c',
                (byte) 'd', (byte) 'e', (byte) 'f' };
        byte[] hex = new byte[2 * raw.length];
        int index = 0;

        for (byte b : raw) {
            int v = b & 0xFF;
            hex[index++] = HEX_CHAR_TABLE[v >>> 4];
            hex[index++] = HEX_CHAR_TABLE[v & 0xF];
        }
        return new String(hex, "ASCII");
    }// end of getHexString

    private void setHandoverNeed(int mtkPrefHandoverCase, boolean isRequester) {
        //Settings.Global.putInt(NfcApplication.sContext.getContentResolver(), NFC_CONTROLLER_CODE, sNfcController);
        Log.d(TAG, " setHandoverNeed:" + mtkPrefHandoverCase + "  isRequester:" + isRequester);
        //Settings.Global.putInt(getApplicationContext().getContentResolver(), NFC_HANDOVER_SCENARIO, mtkPrefHandoverCase);

        mPrefs.edit().putInt(NFC_HANDOVER_SCENARIO, mtkPrefHandoverCase).apply();

        Uri dummyUri    = Uri.parse("file:///dummy/WiFi_Legacy.txt");
        Uri[] mUriArray =   {dummyUri};

        if (isRequester) {
            mAdapter.setBeamPushUris(mUriArray, this);
        }
        showRoleString(isRequester);
    }

    private void showRoleString(boolean isRequester) {

        TextView tt1_1 = (TextView) findViewById(R.id.textView1);
        tt1_1.setVisibility(View.VISIBLE);

        if (isRequester)
            tt1_1.setText("Requester"); //R.string.sending_Cfg_intent);
        else
            tt1_1.setText("Selector"); //R.string.sending_Cfg_intent);

    }

    private String printWpsAppInternalCmd(int cmd) {
        switch (cmd) {

            
            case INfcWpsAppInternal.READ_CONFIGURATION_TOKEN_CMD:
                return "READ_CONFIGURATION_TOKEN_CMD";
            case INfcWpsAppInternal.WRITE_CONFIGURATION_TOKEN_CMD:
                return "WRITE_CONFIGURATION_TOKEN_CMD";
            case INfcWpsAppInternal.READ_PASSWORD_TOKEN_CMD:
                return "READ_PASSWORD_TOKEN_CMD";
            case INfcWpsAppInternal.WRITE_PASSWORD_TOKEN_CMD:
                return "WRITE_PASSWORD_TOKEN_CMD";
                
            case INfcWpsAppInternal.ER_READ_PASSWORD_TOKEN_CMD:
                return "ER_READ_PASSWORD_TOKEN_CMD";
            case INfcWpsAppInternal.WPS_P2P_AUTOGO_AS_SEL_CMD:
                return "WPS_P2P_AUTOGO_AS_SEL_CMD";

            case INfcWpsAppInternal.WPS_HANDOVER_REQUEST_CMD:
                return "WPS_HANDOVER_REQUEST_CMD";
            case INfcWpsAppInternal.WPS_HANDOVER_SELECT_CMD:
                return "WPS_HANDOVER_SELECT_CMD";

            case INfcWpsAppInternal.TEST_WRITE_HS_CONFIGURATION_TOKEN_CMD:
                return "TEST_WRITE_HS_CONFIGURATION_TOKEN_CMD";
                
            case INfcWpsAppInternal.UNKNOWN_CMD:
            default:
                return "<UNKNOWN_CMD>";
        }
    }

    

} // end of NfcForegrounddispatchActivity

