package com.mediatek.nfc.wfacertification.p2p;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import android.net.Uri;
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


public class NfcWfaForegroundDispatchActivity extends Activity {
    static final String TAG = "NfcWfaForegroundDispatchActivity";
    static final boolean DBG = true;


    public static final int MTK_PREF_INVALID_CASE       = -1;

    public static final int MTK_PREF_LEGACY_REQ_CASE    = 0x20;
    public static final int MTK_PREF_LEGACY_SEL_CASE    = 0x21;
    public static final int MTK_PREF_WFA_P2P_CASE       = 0x22;

    public static final String NFC_HANDOVER_SCENARIO = "nfc_handover_scenario";


    NfcManager mManager;
    NfcAdapter mAdapter;
    PendingIntent pendingIntent;
    IntentFilter[] intentFiltersArray;
    String[][] techListsArray;

    byte[] tagInfoArray;

    private String errorHandleString;

    static int command = 0;
    //static PasswordToken mPasswordToken;
    //static ConfigurationToken mConfigurationToken;

    private Handler mUI_Handler = new Handler();
    private SharedPreferences mPrefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, " onCreate() :");
        super.onCreate(savedInstanceState);


        // TODO:: use layout of wps
        setContentView(R.layout.mtk_wps_using_nfc_foreground_dispatch);

        String activityTitle    = getString(R.string.mtk_wfa_activity_title);
        String userIndTextMsg   = getString(R.string.mtk_wfa_user_indication);


        setTitle(activityTitle);

        TextView mTextView0 = (TextView) findViewById(R.id.textView0);
        mTextView0.setVisibility(View.VISIBLE);
        mTextView0.setText(userIndTextMsg);


        Intent iii = this.getIntent();
        command = iii.getIntExtra(INfcWfaAppInternal.EXTRA_NFC_WFA_CMD, 0);

        Log.d(TAG, " EXTRA_NFC_WFA_CMD = " + printWfaAppInternalCmd(command));

        NfcManager mManager = (NfcManager) this
                .getSystemService(Context.NFC_SERVICE);
        mAdapter = mManager.getDefaultAdapter();

        mPrefs = getApplicationContext().getSharedPreferences(NfcService.PREF, Context.MODE_PRIVATE);

        // if command = 0 , P2p path
        switch(command) {

        case INfcWfaAppInternal.WFA_P2P_CMD:
        {

            Log.d(TAG, " command ==  INfcWfaAppInternal.WFA_P2P_CMD");


            setHandoverNeed(MTK_PREF_WFA_P2P_CASE);

            }
            break;

        // if command = 1, Write TAG, store this tag array info first.
        case INfcWfaAppInternal.WRITE_TAG_CMD:
        {
            Log.d(TAG, " command == INfcWfaAppInternal.WRITE_TAG_CMD");
            tagInfoArray = iii.getByteArrayExtra(INfcWfaAppInternal.EXTRA_NFC_WFA_TAG_INFO);
            Log.i(TAG, "WRITE tagInfoArray: "+ Util.printNdef(tagInfoArray));
            }
            break;

        // if command = 3, Write CFG TAG, store this tag array info first.
        case INfcWfaAppInternal.WRITE_CFG_TAG_CMD:
        {
            Log.d(TAG, " command == INfcWfaAppInternal.WRITE_CFG_TAG_CMD");
            tagInfoArray = iii.getByteArrayExtra(INfcWfaAppInternal.EXTRA_NFC_WFA_TAG_INFO);
            Log.i(TAG, "WRITE tagInfoArray: "+ Util.printNdef(tagInfoArray));
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
        Log.d(TAG, " onRestart() : command: " + printWfaAppInternalCmd(command));
        super.onRestart();
    }

    public void onPause() {
        Log.d(TAG, " onPause() : command: " + printWfaAppInternalCmd(command));
        super.onPause();

        if ((command == INfcWfaAppInternal.WRITE_TAG_CMD) || (command == INfcWfaAppInternal.WRITE_CFG_TAG_CMD))
            mAdapter.disableForegroundDispatch(this);
    }

    public void onResume() {
        Log.d(TAG, " onResume() : command: " + printWfaAppInternalCmd(command));
        super.onResume();
        if ((command == INfcWfaAppInternal.WRITE_TAG_CMD) || (command == INfcWfaAppInternal.WRITE_CFG_TAG_CMD)) {
            Log.d(TAG, " mAdapter.enableForegroundDispatch");
            mAdapter.enableForegroundDispatch(this, pendingIntent,
                    intentFiltersArray, techListsArray);
        }
    }

    public void onStop() {
        Log.d(TAG, " onStop() : command: " + printWfaAppInternalCmd(command));
        super.onStop();
        
        Log.d(TAG, "finish activity directly");
        finish();

    }

    public void onDestroy() {
        Log.d(TAG, " onDestroy() : command: " + printWfaAppInternalCmd(command));
        super.onDestroy();
    }

    public void onNewIntent(Intent intent) {
        Log.d(TAG, " onNewIntent() :");
        Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

        byte[] readPayload = new byte[] {};

        if (tagFromIntent == null) {
            Log.d(TAG, "========================================= ");
            Log.d(TAG, "onNewIntent()   tagFromIntent == null  ");
            int testCommand = intent.getIntExtra(INfcWfaAppInternal.EXTRA_NFC_WFA_INTERNAL_CMD, 0);
            Log.d(TAG, "     WPS_INTERNAL_CMD:" + testCommand);
            Log.d(TAG, "========================================= ");

            //if(testCommand == INfcWpsAppInternal.HANDOVER_REQUEST_CMD)
            //    mAdapter.setMtkLegacyPushUris(new Uri[0],this,true);
            //if(testCommand == INfcWpsAppInternal.HANDOVER_SELECT_CMD)
            //    mAdapter.setMtkLegacyPushUris(new Uri[0],this,false);
            //else

            if (testCommand == INfcWfaAppInternal.HANDOVER_FINISH_CMD) {
                Log.d(TAG, "receive finish cmd close foreground Activity ");
                mUI_Handler.postDelayed(runnable_finish, 600);
            }

            //Wifi Legacy handle, update UI string ,close this activity
            return;
        }
        else
            Log.d(TAG, "onNewIntent()   tagFromIntent.toString() " + tagFromIntent.toString());


        Log.d(TAG, " command :" + printWfaAppInternalCmd(command));


        switch (command) {

        case INfcWfaAppInternal.WRITE_CFG_TAG_CMD:
        case INfcWfaAppInternal.WRITE_TAG_CMD:
            // Write Tag
            // 1. get the PWD from the intent
            if (tagInfoArray == null)
                Log.e(TAG, " tagInfoArray == null ");

            mUI_Handler.post(runnable_getTag);


            mUI_Handler.postDelayed(runnable_buildingNdef, 1000);

            //Log.d(TAG, "After build Ndef");

            // 3. write the PWD into a token
            boolean writeResult = false;
            try {
                writeResult = writeTag(tagFromIntent, tagInfoArray);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            if (writeResult)
                mUI_Handler.postDelayed(runnable_writtingTag, 2000);
            else {
                errorHandleString = "!!!! Write Tag Fail !!!!";
                mUI_Handler.postDelayed(runnable_text3ErrorShow, 2000);
            }

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

    private Runnable runnable_writtingTag = new Runnable() {
        public void run() {
            TextView tt2_3 = (TextView) findViewById(R.id.textView3);
            tt2_3.setVisibility(View.VISIBLE);
            tt2_3.setText("Write Tag Success"); //R.string.writting_Pwd_token);
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

    byte[] buildPswTokenNdefPayload(int pwdId, byte[] publicKeyHash,
            byte[] devPwd, byte[] vendorEx) {

        Log.d(TAG, "buildPswTokenNdefPayload()");

        if (DBG) Log.d(TAG, "pwdId:" + pwdId);
        if (DBG) Log.d(TAG, "publicKeyHash length:" + publicKeyHash.length + "  []:" + Util.bytesToString(publicKeyHash));
        if (DBG) Log.d(TAG, "devPwd length:" + devPwd.length + "  []:" + Util.bytesToString(devPwd));
        if (DBG) Log.d(TAG, "vendorEx length:" + vendorEx.length + "  []:" + Util.bytesToString(vendorEx));

        int dataSize = 2 + publicKeyHash.length + devPwd.length
                + vendorEx.length + 9;
        ByteBuffer bBuf = ByteBuffer.allocateDirect(dataSize);

        return bBuf.array();
    }// end of buildPswTokenNdefPayload

    byte[] buildCfgTokenNdefPayload(byte[] networkIndex, byte[] ssid,
            byte[] authType, byte[] encrypType, byte[] macAddress,
            byte[] networkKey, byte[] vendorExtension) {

        Log.d(TAG, "buildCfgTokenNdefPayload()");

        if (DBG) Log.d(TAG, "networkIndex:" + networkIndex);
        if (DBG) Log.d(TAG, "ssid length:" + ssid.length + "  []:" + Util.bytesToString(ssid));
        if (DBG) Log.d(TAG, "authType length:" + authType.length + "  []:" + Util.bytesToString(authType));
        if (DBG) Log.d(TAG, "encrypType length:" + encrypType.length + "  []:" + Util.bytesToString(encrypType));
        if (DBG) Log.d(TAG, "macAddress length:" + macAddress.length + "  []:" + Util.bytesToString(macAddress));
        if (DBG) Log.d(TAG, "networkKey length:" + networkKey.length + "  []:" + Util.bytesToString(networkKey));
        if (DBG) Log.d(TAG, "vendorExtension length:" + vendorExtension.length + "  []:" + Util.bytesToString(vendorExtension));


        int dataSize = networkIndex.length + ssid.length + authType.length
                + encrypType.length + macAddress.length + networkKey.length
                + 33 + vendorExtension.length;
        ByteBuffer bBuf = ByteBuffer.allocateDirect(dataSize);

        return bBuf.array();
    }// end of buildCfgTokenNdefPayload

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

    private void dumpBytes(byte[] in) {
        StringBuilder builder = new StringBuilder();
        for (int counter = 0; counter < in.length; counter++) {
            builder.append(Integer.toHexString(in[counter] & 0xFF)).append(" ");
        }

        Log.d(TAG, "tag content =" + builder.toString());
        // System.out.println(builder.toString());
    }// end of dumpBytes


    private String printWfaAppInternalCmd(int cmd) {
        switch (cmd) {
            case INfcWfaAppInternal.WFA_P2P_CMD:
                return "WFA_P2P_CMD";
            case INfcWfaAppInternal.WRITE_TAG_CMD:
                return "WRITE_TAG_CMD";
            case INfcWfaAppInternal.WRITE_CFG_TAG_CMD:
                return "WRITE_CFG_TAG_CMD";

            default:
                return "<error>";
        }
    }

    private void setHandoverNeed(int mtkPrefHandoverCase) {
        //Settings.Global.putInt(NfcApplication.sContext.getContentResolver(), NFC_CONTROLLER_CODE, sNfcController);

        Log.d(TAG, " setHandoverNeed:" + mtkPrefHandoverCase);
        //Settings.Global.putInt(getApplicationContext().getContentResolver(), NFC_HANDOVER_SCENARIO, mtkPrefHandoverCase);

        mPrefs.edit().putInt(NFC_HANDOVER_SCENARIO, mtkPrefHandoverCase).apply();


        Uri dummyUri    = Uri.parse("file:///dummy/WFA_P2P.txt");
        Uri[] mUriArray =   {dummyUri};

        mAdapter.setBeamPushUris(mUriArray, this);
    }



} // end of NfcForegrounddispatchActivity

