package com.mediatek.nfc.wfacertification.p2p;

import java.io.UnsupportedEncodingException;

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

import android.util.Log;
import android.view.View;
import android.widget.TextView;

//import android.nfc.FormatException;
import com.mediatek.nfc.Util;

import com.android.nfc.R;

import com.mediatek.nfc.handoverprotocol.WifiCarrierConfiguration;
import com.mediatek.nfc.handoverprotocol.WifiCarrierConfiguration.Credential;
import com.mediatek.nfc.handoverprotocol.WifiCarrierConfiguration.PasswordToken;
import com.mediatek.nfc.handoverprotocol.WfaP2pCarrierConfiguration;


public class NfcSigmaForegroundDispatchActivity extends Activity {
    static final String TAG = "NfcSigmaForegroundDispatchActivity";
    static final boolean DBG = true;

    NfcManager mManager;
    NfcAdapter mAdapter;
    PendingIntent pendingIntent;
    IntentFilter[] intentFiltersArray;
    String[][] techListsArray;

    byte[] tagInfoArray;

    private String errorHandleString;
    private String textString3;

    static int command = 0;
    //static PasswordToken mPasswordToken;
    //static ConfigurationToken mConfigurationToken;

    private Handler mUI_Handler = new Handler();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, " onCreate() :");
        super.onCreate(savedInstanceState);


        // TODO:: use layout of wps
        setContentView(R.layout.mtk_wps_using_nfc_foreground_dispatch);

        String activityTitle    = getString(R.string.mtk_wfa_sigma_activity_title);
        String userIndTextMsg   = getString(R.string.mtk_wfa_sigma_user_indication);


        setTitle(activityTitle);

        TextView mTextView0 = (TextView) findViewById(R.id.textView0);
        mTextView0.setVisibility(View.VISIBLE);
        mTextView0.setText(userIndTextMsg);


        Intent iii = this.getIntent();
        command = iii.getIntExtra(INfcWfaSigma.EXTRA_NFC_SIGMA_CMD, 0);

        Log.d(TAG, " EXTRA_NFC_WFA_CMD = " + printInternalCmd(command));

        NfcManager mManager = (NfcManager) this
                .getSystemService(Context.NFC_SERVICE);
        mAdapter = mManager.getDefaultAdapter();


        switch(command) {

        // if command = 0xa, Sigma Read ALL TAG
        case INfcWfaSigma.READ_ALL_TAG_CMD:
        {

            Log.d(TAG, " command ==  INfcWfaSigma.READ_ALL_TAG_CMD");
            NdefMessage p2pHrM = null;


            }
            break;



        default:
            Log.e(TAG, " command  <Unknown>  Should Exception !!");

            errorHandleString = "Please Re-Launch Activity ";
            mUI_Handler.postDelayed(runnable_text1ErrorShow, 500);
            mUI_Handler.postDelayed(runnable_finish, 3000);

            return;

        }

        //
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
        Log.d(TAG, " onRestart() : command:" + printInternalCmd(command));
        super.onRestart();
    }

    public void onPause() {
        Log.d(TAG, " onPause() : command:" + printInternalCmd(command));
        super.onPause();

            mAdapter.disableForegroundDispatch(this);
    }

    public void onResume() {
        Log.d(TAG, " onResume() : command:" + printInternalCmd(command));
        super.onResume();

            Log.d(TAG, " mAdapter.enableForegroundDispatch");
            mAdapter.enableForegroundDispatch(this, pendingIntent,
                    intentFiltersArray, techListsArray);

    }

    public void onStop() {
        Log.d(TAG, " onStop() : command: " + printInternalCmd(command));
        super.onStop();
        
        Log.d(TAG, "finish activity directly");
        finish();

    }

    public void onDestroy() {
        Log.d(TAG, " onDestroy() : command: " + printInternalCmd(command));
        super.onDestroy();
    }

    

    public void onNewIntent(Intent intent) {
        Log.d(TAG, " onNewIntent() :");
        Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

        byte[] readPayload = new byte[] {};

        if (tagFromIntent == null) {
            Log.d(TAG, "========================================= ");
            Log.d(TAG, "onNewIntent()   tagFromIntent == null  ");
            int testCommand = intent.getIntExtra(INfcWfaSigma.EXTRA_ON_NEW_INTENT_CMD, 0);
            Log.d(TAG, "     EXTRA_ON_NEW_INTENT_CMD:" + testCommand);
            Log.d(TAG, "========================================= ");

            //if(testCommand == INfcWpsAppInternal.HANDOVER_REQUEST_CMD)
            //    mAdapter.setMtkLegacyPushUris(new Uri[0],this,true);
            //if(testCommand == INfcWpsAppInternal.HANDOVER_SELECT_CMD)
            //    mAdapter.setMtkLegacyPushUris(new Uri[0],this,false);
            //else

            if (testCommand == INfcWfaSigma.HANDOVER_FINISH_CMD) {
                Log.d(TAG, "receive finish cmd close foreground Activity ");
                mUI_Handler.postDelayed(runnable_finish, 600);
            }

            //Wifi Legacy handle, update UI string ,close this activity
            return;
        }
        else
            Log.d(TAG, "onNewIntent()   tagFromIntent.toString() " + tagFromIntent.toString());


        Log.d(TAG, " command :" + printInternalCmd(command));


        switch (command) {

        case INfcWfaSigma.READ_ALL_TAG_CMD:

            //NdefMessage p2pDevInfoMessage = null;
            byte[] intentNdefByteArray = null;
            NdefMessage readNdef = null;

            mUI_Handler.post(runnable_getTag);

            mUI_Handler.postDelayed(runnable_buildingNdef, 800);

            // 1. read Ndef record
            readNdef = readTag(tagFromIntent);

            if (readNdef == null) {
                Log.e(TAG, " read NDEF is null");
                errorHandleString = "NDEF is null";
                mUI_Handler.postDelayed(runnable_text3ErrorShow, 1000);
                mUI_Handler.postDelayed(runnable_finish, 3000);
                return;
            }

            Log.d(TAG, "Read NDEF of TAG :" + Util.printNdef(readNdef));


            WfaP2pCarrierConfiguration mWfaP2pCCR = WfaP2pCarrierConfiguration.tryParse(readNdef);

            if (mWfaP2pCCR != null) {
                textString3 = "Read P2p Tag Success";

                intentNdefByteArray = readNdef.toByteArray();

                Log.d(TAG, "sendBroadcast MTK_NFC_WFA_SIGMA_P2P_TAG_RECEIVE_ACTION ~~");
                Intent readTagIntent = new Intent(INfcWfaSigma.MTK_NFC_WFA_SIGMA_P2P_TAG_RECEIVE_ACTION);
                readTagIntent.putExtra(INfcWfaSigma.MTK_NFC_WFA_SIGMA_P2P_TAG_EXTRA_INFO, intentNdefByteArray);
                sendBroadcast(readTagIntent);

            } else {

                WifiCarrierConfiguration wifiCCR = WifiCarrierConfiguration.tryParse(readNdef);

                if (wifiCCR == null) {
                    Log.e(TAG, "!!!! inValid Tag, format not match  !!!! ");
                    errorHandleString = "inValid Tag, format not match";
                    mUI_Handler.postDelayed(runnable_text3ErrorShow, 1000);
                    mUI_Handler.postDelayed(runnable_finish, 3000);
                    return;
                }

                Credential credentialData = wifiCCR.getCredential();

                PasswordToken pwdTokenData = wifiCCR.getPasswordToken();

                if (credentialData != null) {
                    textString3 = "Read Cfg Token Success";
                    intentNdefByteArray = readNdef.toByteArray();

                    Log.d(TAG, "sendBroadcast MTK_NFC_WFA_SIGMA_WPS_CFG_TAG_RECEIVE_ACTION ~~");
                    Intent cfgIntent = new Intent(INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_CFG_TAG_RECEIVE_ACTION);
                    cfgIntent.putExtra(INfcWfaSigma.MTK_NFC_WFA_SIGMA_TAG_EXTRA_DEV_INFO, intentNdefByteArray);
                    sendBroadcast(cfgIntent);
                }
                else if (pwdTokenData != null) {
                    textString3 = "Read Pwd Token Success";
                    intentNdefByteArray = readNdef.toByteArray();

                    Log.d(TAG, "sendBroadcast MTK_NFC_WFA_SIGMA_WPS_PWD_TAG_RECEIVE_ACTION ~~");
                    Intent pwdTagIntent = new Intent(INfcWfaSigma.MTK_NFC_WFA_SIGMA_WPS_PWD_TAG_RECEIVE_ACTION);
                    pwdTagIntent.putExtra(INfcWfaSigma.MTK_NFC_WFA_SIGMA_TAG_EXTRA_DEV_INFO, intentNdefByteArray);
                    sendBroadcast(pwdTagIntent);

                } else {
                    Log.e(TAG, "!!!! Type (vnd.wfa.wsc) match ,payload format not match  !!!! ");
                    errorHandleString = "Type (vnd.wfa.wsc) match ,payload format not match";
                    mUI_Handler.postDelayed(runnable_text3ErrorShow, 1000);
                    mUI_Handler.postDelayed(runnable_finish, 3000);
                    return;
                }

            }

            mUI_Handler.postDelayed(runnable_showText3, 1600);

            Log.d(TAG, "postDelayed ->  Close Activity");
            mUI_Handler.postDelayed(runnable_finish, 3000);
            break;


        default:
            Log.d(TAG, " unKnown command : " + command);
            break;
        }
    }// end of onNewIntent

    // 1. write Tag case
    private Runnable runnable_getTag = new Runnable() {
        public void run() {
            TextView tt2_1 = (TextView) findViewById(R.id.textView1);
            tt2_1.setVisibility(View.VISIBLE);
            tt2_1.setText("Getting Tag"); //R.string.getting_Pwd_from_intent);
        }
    };

    private Runnable runnable_buildingNdef = new Runnable() {
        public void run() {
            TextView tt2_2 = (TextView) findViewById(R.id.textView2);
            tt2_2.setVisibility(View.VISIBLE);
            tt2_2.setText("Building  Ndef."); //R.string.building_Pwd_Ndef_record);
        }
    };


    private Runnable runnable_readingTag = new Runnable() {
        public void run() {
            TextView tt2_3 = (TextView) findViewById(R.id.textView3);
            tt2_3.setVisibility(View.VISIBLE);
            tt2_3.setText("Read Tag Success"); //R.string.writting_Pwd_token);
        }
    };

    //finish
    private Runnable runnable_finish = new Runnable() {
        public void run() {
            Log.d(TAG, "runnable_finish()    finish() ");
            finish();
        }
    };

    // error handle show text1
    private Runnable runnable_text1ErrorShow = new Runnable() {
        public void run() {
            TextView tt2_1 = (TextView) findViewById(R.id.textView1);
            tt2_1.setVisibility(View.VISIBLE);
            if (errorHandleString == null)
                errorHandleString = "error string NULL";
            tt2_1.setText(errorHandleString); //R.string.reading_Cfg_tag);
        }
    };

    private Runnable runnable_text3ErrorShow = new Runnable() {
        public void run() {
            TextView tt2_3 = (TextView) findViewById(R.id.textView3);
            tt2_3.setVisibility(View.VISIBLE);
            if (errorHandleString == null)
                errorHandleString = "error string NULL";
            tt2_3.setText(errorHandleString); //R.string.writting_Pwd_token);
        }
    };

    private Runnable runnable_showText3 = new Runnable() {
        public void run() {
            TextView tt3 = (TextView) findViewById(R.id.textView3);
            tt3.setVisibility(View.VISIBLE);
            if (textString3 == null)
                textString3 = "<Text3 string NULL>";
            tt3.setText(textString3); //R.string.writting_Pwd_token);
        }
    };



    /*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.mtk_wps_using_nfc_foreground_dispatch, menu);
        return true;
    }
    */

    /**
    *   Write NDEF message to TAG
    *   we suppose payload shoud be the byte array of entire NDEF message
    *
    *   <p>
    *
    * @param  payload   the byte array of entire NDEF message
    * @return      null
    * @see         null
    */
    private boolean writeTag(Tag tag_in, byte[] payload)
            throws UnsupportedEncodingException {
        Log.d(TAG, "write tag begin...");
        //Log.d(TAG, "prepared payload = " + new String(payload));
        if (tag_in == null) {
            Log.d(TAG, "tag = null");
            return false;
        }

        Ndef tag = Ndef.get(tag_in);
        Log.d(TAG, "after get tag~");

        try {
            Log.d(TAG, "into try~~");

            NdefMessage message = new NdefMessage(payload);
            if (tag == null) {
                Log.e(TAG, "exception: tag == null");
                return false;
            }

            tag.connect();
            boolean connected = tag.isConnected();
            Log.d(TAG, "into try step 2~~  connected:" + connected);
            boolean writeable = tag.isWritable();
            Log.d(TAG, "into try step 3~~  writeable" + writeable);
            if (connected && writeable) {

                if (message == null) {
                    Log.e(TAG, "exception: message == null");
                    return false;
                }

                byte[] tryMessageByteArray = message.toByteArray();

                Log.d(TAG, "  Write message length:" + tryMessageByteArray.length);
                Log.d(TAG, "  Write message: ::" + Util.bytesToString(tryMessageByteArray));

                tag.writeNdefMessage(message);
            }
            Log.d(TAG, "into try step 4~~   done --> close");
            tag.close();
        } catch (Exception e) {
            // do error handling
            Log.d(TAG, "got Exception when writting this tag..." + e);
            return false;
        }
        Log.d(TAG, "==== write tag end.");
        return true;
    } // end of writeTag


    //Sync readTag function with NfcForegroundDispatchActivity
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

    private String printInternalCmd(int cmd) {
        switch (cmd) {
            case INfcWfaSigma.READ_ALL_TAG_CMD:
                return "READ_ALL_TAG_CMD";


            default:
                return "<error>";
        }
    }



} // end of NfcForegrounddispatchActivity

