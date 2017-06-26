package com.mediatek.nfc.wfacertification;


//import java.io.File;
//import java.nio.BufferUnderflowException;
//import java.nio.ByteBuffer;
//import java.nio.charset.Charset;
//import java.text.SimpleDateFormat;
//import java.util.ArrayList;
import java.util.Arrays;
//import java.util.Date;
//import java.util.HashMap;
//import java.util.Random;

//import android.app.Activity;
import android.app.PendingIntent;
//import android.app.PendingIntent.CanceledException;
//import android.app.Notification;
//import android.app.NotificationManager;
//import android.app.PendingIntent;
//import android.app.Notification.Builder;
//import android.bluetooth.BluetoothA2dp;
//import android.bluetooth.BluetoothAdapter;
//import android.bluetooth.BluetoothDevice;
//import android.bluetooth.BluetoothHeadset;
//import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
//import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.widget.Toast;

//import android.media.MediaScannerConnection;
import android.net.Uri;


//import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
//import android.nfc.wps.INfcWpsTestBed;
//import android.nfc.wps.WpsCredential;

//import android.os.Environment;
//import android.os.Handler;
//import android.os.Message;
//import android.os.Parcelable;
//import android.os.SystemClock;
import android.util.Log;
//import android.util.Pair;

//import com.android.nfc.P2pLinkManager;

import com.android.nfc.NfcService;

//import com.android.nfc.handover.HandoverManager;



import com.mediatek.nfc.Util;

import com.mediatek.nfc.handoverprotocol.HandoverMessageElement;

import com.mediatek.nfc.wfacertification.wps.INfcWpsAppInternal;
import com.mediatek.nfc.wfacertification.wps.NfcWpsHandoverManager;

import com.mediatek.nfc.wfacertification.p2p.NfcWfaHandoverManager;
import com.mediatek.nfc.wfacertification.p2p.INfcWfaAppInternal;
import com.mediatek.nfc.wfacertification.p2p.NfcWfaSigmaHandle;

//import com.mediatek.nfc.handover.MtkHandoverManagerFactory;
import com.mediatek.nfc.handover.MtkNfcEntry;
import com.mediatek.nfc.handover.MtkNfcEntry.IMtkHandoverManager;



/**
 * Manages handover of NFC to other technologies.
 */
public class MtkWfaCerHandoverManager implements MtkNfcEntry.IMtkHandoverManager {

    static final String TAG = "MtkWfaCerHandoverManager";
    static final boolean DBG = true;

    /** Scenario String is used on whichScenario()*/
    public static final int SCENARIO_JB_ORIGINAL     = 0; //= "com.mediatek.nfc.handover.SCENARIO_JB_ORIGINAL";
    public static final int SCENARIO_BEAMPLUS_P2P    = 1; //= "com.mediatek.nfc.handover.SCENARIO_BEAMPLUS_P2P";
    public static final int SCENARIO_WFD             = 2; //= "com.mediatek.nfc.handover.SCENARIO_WFD";
    public static final int SCENARIO_WIFI_LEGACY     = 3; //= "com.mediatek.nfc.handover.SCENARIO_WIFI_LEGACY";
    public static final int SCENARIO_HR_COLLISION    = 4; //= "com.mediatek.nfc.handover.SCENARIO_HR_COLLISION";

    public static final int SCENARIO_WFA_P2P         = 5;
    //public static final int SCENARIO_WFA_HR_COLLISION    =6;

    // values for mSendState, should the same with p2plinkmanager.java
    //static final int SEND_STATE_NOTHING_TO_SEND = 1;
    //static final int SEND_STATE_NEED_CONFIRMATION = 2;
    static final int SEND_STATE_SENDING = 3;

    public static final int MTK_PREF_INVALID_CASE       = -1;

    public static final int MTK_PREF_LEGACY_REQ_CASE    = 0x20;
    public static final int MTK_PREF_LEGACY_SEL_CASE    = 0x21;
    public static final int MTK_PREF_WFA_P2P_CASE       = 0x22;

    public static final String NFC_HANDOVER_SCENARIO = "nfc_handover_scenario";

    public static final String ACTION_CALL_P2P_SDK_API = "com.mediatek.nfc.wfacertification.CALL_P2P_SDK_API";
    public static final String CALL_SDK_API_WRONG_MODE = "setWfaP2pRequester() in Certification mode \nPlease change to Normal mode by EMUI";


    final Context mContext;
    //HandoverManager mHandoverManager;
    //P2pLinkManager mP2pLinkManager;

    private static MtkWfaCerHandoverManager mStaticInstance = null;
    private SharedPreferences mPrefs;

    public MtkWfaCerHandoverManager(Context context /*, HandoverManager handoverManager*/) {
        mContext = context;
        Log.i(TAG, " MtkWfaCerHandoverManager Construct ");
        //if (handoverManager != null)
        //mHandoverManager = handoverManager;

        //mP2pLinkManager = p2pLinkManager;

        mPrefs = context.getSharedPreferences(NfcService.PREF, Context.MODE_PRIVATE);

        NfcWpsHandoverManager.createSingleton(mContext);
        NfcWfaHandoverManager.createSingleton(mContext);
        NfcWfaSigmaHandle.createSingleton(mContext);

        IntentFilter filter = new IntentFilter(ACTION_CALL_P2P_SDK_API);
        context.registerReceiver(mReceiver, filter);
    }

    public static void createSingleton(Context context /*, HandoverManager handoverManager*/) {
        if (mStaticInstance == null) {
            mStaticInstance = new MtkWfaCerHandoverManager(context /*, handoverManager*/);
        }
    }

    public static IMtkHandoverManager getInstance() {
        return (IMtkHandoverManager) mStaticInstance;
    }

    /**
    *       P2pLinkManager doGet
    *
    *       It's possible to receive three type HrM.
    *       1.BT CCR only(JB original)          HR(+AC)    + BT CCR
    *       2.BT Wifi  AUX  (P2p usage)                 HR(+AC+AC) + BT CCR + Wifi CCR(AUX only)
    *
    *       3.Wifi Legacy (WPS)                         HR(+AC)    + Wifi Type only
    *
    *       We always act as Client on WFD
    *       WFD Wifi Aux (with RTSP port)        HR(+AC)    + Wifi  Aux, (with RTSP port)
    *
    *       return null , p2pLinkManager will response SnepMessage.RESPONSE_NOT_FOUND
    */
    public NdefMessage tryMtkHandoverRequest(NdefMessage tryMessage) {
        NdefMessage result = null;
        try {
            result = tryMtkHandoverRequestImpl(tryMessage);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return result;
    }

    private NdefMessage tryMtkHandoverRequestImpl(NdefMessage tryMessage) {
        //to check Url, check return
        NdefMessage p2pHsM = null;
        boolean mWifiDisplayCase = true;

        //int NfcSendState;


        if (tryMessage == null) return null;
        //if (mBluetoothAdapter == null) return null;

        //NfcSendState = mP2pLinkManager.getP2pState();

        //byte[] tryMessageByteArray = tryMessage.toByteArray();
        if (DBG) Log.d(TAG, "tryMtkHandoverRequest(): remove Collision");
        Log.d(TAG, "  tryMessageByteArray " + Util.printNdef(tryMessage)); //length:"+ tryMessageByteArray.length + " Array::" + Util.bytesToString(tryMessageByteArray));

        /*
        *       1.parse HrM
        */

        //1.1 Try Wifi AC exist or not  , if not , BT hit  JB original
        NdefRecord r = tryMessage.getRecords()[0];
        if (r.getTnf() != NdefRecord.TNF_WELL_KNOWN) return null;
        if (!Arrays.equals(r.getType(), NdefRecord.RTD_HANDOVER_REQUEST)) return null;


        //= whichScenario(tryMessage);
        HandoverMessageElement mHandoverMsgElement = new HandoverMessageElement(tryMessage);
        int mScenario = mHandoverMsgElement.getScenario();
        Log.d(TAG, "mScenario:" + mScenario);



        switch(mScenario) {

            case SCENARIO_BEAMPLUS_P2P:
                Log.d(TAG, " should not get SCENARIO_BEAMPLUS_P2P HandoverRequestMessage   return null");
                return null;

            case SCENARIO_WFD:
                //return mHandoverManager.tryHandoverRequest(tryMessage);
                Log.d(TAG, " should not get WFD HandoverRequestMessage   return null");
                return null;
                //break;

            case SCENARIO_WIFI_LEGACY:
                return NfcWpsHandoverManager.getInstance().dealWifiLegacyHrM(tryMessage);

            case SCENARIO_WFA_P2P:
                // TODO:: REMOVE AntiCollision ,compare Random number
                /*
                if((NfcSendState == SEND_STATE_SENDING) && actAsRequester(mP2pRequesterRandom,mHandoverMsgElement.mCRArray)){
                    Log.d(TAG, "P2pLink actAsRequester return True !!!!    p2pHsM set to Specific record  return!!!!"  );
                    p2pHsM = NfcWfaHandoverManager.getInstance().createWfaP2pCollisionHsM();
                }
                else
                */
                p2pHsM = NfcWfaHandoverManager.getInstance().dealWfaP2pHrM(tryMessage);

                Log.d(TAG, "SCENARIO_WFA_P2P p2pHsM:" + Util.printNdef(p2pHsM));

                return p2pHsM;

            default:
            case SCENARIO_JB_ORIGINAL:
                Log.d(TAG, "should not enter, exception SCENARIO_JB_ORIGINAL");
                return null;//mHandoverManager.tryHandoverRequest(tryMessage);
        }


    }

    /**
        *   Requester Create Handover request Message Entry.
        *       it will dispatch to correct Create function by handoverCase
        *   <p>
        *   <p>
        *
        * @param  uris  the URIs of beamPlus files
        * @param  handoverCase  the handoverCase is used to determine Create funciton
        * @return      null
        * @see         null
        */

    public NdefMessage CreateHrMEntry(Uri[] uris /*,NdefMessage msg,String handoverCase*/) {
        Log.d(TAG, "    CreateHrMEntry()");
        NdefMessage request = null;

        int mhandoverCase;

        //Settings.Global.putInt(getApplicationContext().getContentResolver(), NFC_HANDOVER_SCENARIO, SCENARIO_WIFI_LEGACY);
        //mhandoverCase = Settings.Global.getInt(getApplicationContext().getContentResolver(), NFC_HANDOVER_SCENARIO, MTK_PREF_INVALID_CASE);

        mhandoverCase = mPrefs.getInt(NFC_HANDOVER_SCENARIO, MTK_PREF_INVALID_CASE);

        /*
        if(uris[0].toString().equalsIgnoreCase(NfcAdapter.MTK_WFA_P2P_CASE)){
            Log.e(TAG,"Send "+ACTION_CALL_P2P_SDK_API+" intent, return null");
            Intent hsIntent = new Intent(ACTION_CALL_P2P_SDK_API);
            mContext.sendBroadcast(hsIntent);
            return request;
        }
        */

        //if(mhandoverCase == MTK_PREF_INVALID_CASE)
        //   return mHandoverManager.createHandoverRequestMessage();

        if (mhandoverCase == MTK_PREF_LEGACY_REQ_CASE)
            request = NfcWpsHandoverManager.getInstance().createWiFiLegacyRequestMessage();
        else if (mhandoverCase == MTK_PREF_WFA_P2P_CASE)
            request = NfcWfaHandoverManager.getInstance().createP2pHrM();
        else
            Log.e(TAG, "Exception: CreateHrMEntry handoverCase not match  :" + describeHandoverCase(mhandoverCase));

        Log.d(TAG, "handoverCase:" + describeHandoverCase(mhandoverCase) + " HrM:" + Util.printNdef(request));

        return request;
    }


    /**
        *   Requester deal Handover select Message Entry.
        *       it will dispatch to correct doHandover function by handoverCase
        *   <p>
        *   <p>
        *
        * @param  uris  the URIs of beamPlus files
        * @param  handoverCase  the handoverCase is used to determine doHandover funciton
        * @return      null
        * @see         null
        */

    public void doHsMHandoverEntry(Uri[] uris, NdefMessage response /*,String handoverCase*/) {

        int mhandoverCase;

        //mhandoverCase = Settings.Global.getInt(NfcApplication.sContext.getContentResolver(), NFC_HANDOVER_SCENARIO, MTK_PREF_INVALID_CASE);

        mhandoverCase = mPrefs.getInt(NFC_HANDOVER_SCENARIO, MTK_PREF_INVALID_CASE);

        Log.d(TAG, "    doHsMEntry()  handoverCase:" + describeHandoverCase(mhandoverCase));


        //byte[] responseByteArray = response.toByteArray();
        Log.d(TAG, "  responseByteArray " + Util.printNdef(response));


        if (mhandoverCase == MTK_PREF_INVALID_CASE) {
            
            Log.d(TAG, "  mhandoverCase == MTK_PREF_INVALID_CASE,  return" );
            //mHandoverManager.doHandoverUri(uris, response);
            return;
        }


        if (mhandoverCase == MTK_PREF_LEGACY_REQ_CASE)
            NfcWpsHandoverManager.getInstance().doWiFiLegacyHandover(uris, response);
        else if (mhandoverCase == MTK_PREF_WFA_P2P_CASE)
            NfcWfaHandoverManager.getInstance().doWfaP2pHandover(response);
        else
            Log.e(TAG, "Exception: doHsMEntry handoverCase not match  :" + describeHandoverCase(mhandoverCase));


    }



    /**
    *   Requester deal Handover select Message Entry.
    *       it will dispatch to correct doHandover function by handoverCase
    *   <p>
    *   <p>
    *
    * @param  handoverCase  the handoverCase is used to determine doHandover funciton
    * @return      null
    * @see         null
    */
    public void closeForegroundDispatchActivity(int handoverCase) {
        PendingIntent mPendingIntent;

        Log.i(TAG, "  closeForegroundDispatchActivity() handoverCase" + describeHandoverCase(handoverCase));

        if (handoverCase == MTK_PREF_INVALID_CASE)
            return;

        if (handoverCase == MTK_PREF_LEGACY_REQ_CASE || handoverCase == MTK_PREF_LEGACY_SEL_CASE) {
            NfcWpsHandoverManager.getInstance().setInternalCmdToForegroundDispatchActivity(INfcWpsAppInternal.HANDOVER_FINISH_CMD);
        } else if (handoverCase == MTK_PREF_WFA_P2P_CASE) {
            NfcWfaHandoverManager.getInstance().sendInternalCmdToNfcWfaForegroundActivity(INfcWfaAppInternal.HANDOVER_FINISH_CMD);
        }

    }

    /**
    *   notifyHandoverComplete.
    *   <p>
    *   <p>
    *
    * @param  null
    * @return      null
    * @see         null
    */
    public void notifyHandoverComplete() {

        int mhandoverCase;

        //Settings.Global.putInt(getApplicationContext().getContentResolver(), NFC_HANDOVER_SCENARIO, SCENARIO_WIFI_LEGACY);
        //mhandoverCase = Settings.Global.getInt(NfcApplication.sContext.getContentResolver(), NFC_HANDOVER_SCENARIO, MTK_PREF_INVALID_CASE);
        mhandoverCase = mPrefs.getInt(NFC_HANDOVER_SCENARIO, MTK_PREF_INVALID_CASE);

        closeForegroundDispatchActivity(mhandoverCase);

        if (mhandoverCase != MTK_PREF_INVALID_CASE) {
            mPrefs.edit().putInt(NFC_HANDOVER_SCENARIO, MTK_PREF_INVALID_CASE).apply();
        }
    }


    public static String describeHandoverCase(int handovercase) {
        String mString = "";
        switch (handovercase) {
        case MTK_PREF_LEGACY_REQ_CASE:
            mString = "mediatek.nfc.handover.beamplus.WifiLegacy.Requester"; //NfcAdapter.MTK_WL_REQ_CASE;
            break;
        case MTK_PREF_LEGACY_SEL_CASE:
            mString = "mediatek.nfc.handover.beamplus.WifiLegacy.Selector"; //NfcAdapter.MTK_WL_SEL_CASE;
            break;
        case MTK_PREF_WFA_P2P_CASE:
            mString = "mediatek.nfc.handover.beamplus.P2P"; //NfcAdapter.MTK_P2P_CASE;
            break;
        default:
            mString = "UNKNOWN CASE";
            break;
        }
        return  handovercase + ", " + mString;
    }

    public static boolean tagDispatch(NdefMessage message) {
         Log.i(TAG, " tagDispatch()");
        return NfcWfaHandoverManager.getInstance().tryHandover(message);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            Log.d(TAG, " onReceive() An intent is coming... " + action);
            if (action == null) {
                return;
            }

            if (action.equals(ACTION_CALL_P2P_SDK_API)) {
                Log.d(TAG, "!!!!  " + CALL_SDK_API_WRONG_MODE + " !!!! ");
                Toast.makeText(context, CALL_SDK_API_WRONG_MODE, Toast.LENGTH_LONG).show();
            }

        }
    };


}

