package com.mediatek.nfc.handover;

import android.net.Uri;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;

import android.util.Log;
import java.nio.ByteBuffer;

import com.mediatek.nfc.Util;

import com.mediatek.nfc.handoverprotocol.HandoverMessageElement;

import com.android.nfc.NfcService;
//import com.android.nfc.handover.HandoverManager;


//import com.mediatek.nfc.handover.MtkHandoverManager;
import com.mediatek.nfc.wfacertification.MtkWfaCerHandoverManager;
import com.mediatek.nfc.gsmahandset.EvtTransactionHandle;


public class MtkNfcEntry {
    private static final String TAG = "MtkNfcEntry";

    /**
     *  IMtkHandoverManager
     *      Each individual mode needs to implement this interface
     */
    public interface IMtkHandoverManager {

        public NdefMessage CreateHrMEntry(Uri[] uris);
        public NdefMessage tryMtkHandoverRequest(NdefMessage tryMessage);
        public void doHsMHandoverEntry(Uri[] uris, NdefMessage response);

        public void notifyHandoverComplete();

    }


    static final String TYPE_MTK_L_PROPRIETARY = "vendor_proprietary";


    //MTK L proprietary version 1.0 , TLV: 1 element, size:5bytes
    public static final short MTK_ATTRIBUTE_TYPE_VERSION_ID    = 0x5050;
    public static final byte  MTK_VERSION                       = 0x10;
    public static final byte  MTK_VERSION_TLV_SIZE              = 5;


    //copy from HandoverMessageElement
    public static final int SCENARIO_JB_ORIGINAL     = 0; //= "com.mediatek.nfc.handover.SCENARIO_JB_ORIGINAL";
    public static final int SCENARIO_BEAMPLUS_P2P    = 1; //= "com.mediatek.nfc.handover.SCENARIO_BEAMPLUS_P2P";
    public static final int SCENARIO_WFD             = 2; //= "com.mediatek.nfc.handover.SCENARIO_WFD";
    public static final int SCENARIO_WIFI_LEGACY     = 3; //= "com.mediatek.nfc.handover.SCENARIO_WIFI_LEGACY";
    public static final int SCENARIO_HR_COLLISION    = 4; //= "com.mediatek.nfc.handover.SCENARIO_HR_COLLISION";

    public static final int SCENARIO_WFA_P2P         = 5;
    //public static final int SCENARIO_WFA_HR_COLLISION    =6;
    public static final int SCENARIO_MTK_L_WIFI_BEAM = 7;


    public static final String ACTION_SET_HANDOVER_MODE = "com.mediatek.nfc.handover.ACTION_SET_HANDOVER_MODE";
    public static final String EXTRA_MODE_VALUE = "com.mediatek.nfc..handover.EXTRA_MODE_VALUE";

    public static final String ACTION_QUERY_HANDOVER_MODE = "com.mediatek.nfc.handover.ACTION_QUERY_HANDOVER_MODE";
    public static final String ACTION_QUERY_HANDOVER_MODE_RESULT = "com.mediatek.nfc.handover.ACTION_QUERY_HANDOVER_MODE_RESULT";

    public static final int MTK_PREF_INVALID_CASE       = -1;

    public static final int MTK_PREF_LEGACY_REQ_CASE    = 0x20;
    public static final int MTK_PREF_LEGACY_SEL_CASE    = 0x21;
    public static final int MTK_PREF_WFA_P2P_CASE       = 0x22;

    public static final String NFC_HANDOVER_SCENARIO = "nfc_handover_scenario";

    public static final int MANAGER_MODE_INVALID = -1;
    public static final int MANAGER_MODE_DEFAULT = 0;
    public static final int MANAGER_MODE_WFA_CERTIFICATION = 1;
    private static ModeChangeListener sModeChangeListener;

    private static MtkNfcEntry sStaticInstance = null;

    private Context mContext;


    //public static final String MTK_WFA_WPS_CASE  = "file:///Landing/WfaWPS/dummy";
    //public static final String MTK_WFA_P2P_CASE  = NfcAdapter.MTK_WFA_P2P_CASE;//"file:///Landing/WfaP2P/dummy";

    private static int mMode;

    private int mCurrentUserId = 0;

    //constructor
    private MtkNfcEntry(Context context) {
        mContext = context;

    
        IntentFilter filter;
        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        mContext.registerReceiver(mSwitchUserReceiver, filter);

        
    }

    private static void createSingleton(Context context) {
        if (sStaticInstance == null) {
            sStaticInstance = new MtkNfcEntry(context);
        }
    }

    public static MtkNfcEntry getInstance() {
        return sStaticInstance;
    }



    public static void init(Context context /*, HandoverManager handoverManager*/) {

        Log.i(TAG, " init()  ");
        createSingleton(context);

        if (sModeChangeListener == null) {
            sModeChangeListener = new ModeChangeListener(context);
        }

        mMode = sModeChangeListener.getMode();
        Log.i(TAG, " .init()  mode:" + sModeChangeListener.describeHandoverMode(mMode));

        //if(!FeatureOption.MTK_WIFIWPSP2P_NFC_SUPPORT){//MTK_WIFIWPSP2P_NFC_SUPPORT
        //    Log.i(TAG, "FeatureOption.MTK_WIFIWPSP2P_NFC_SUPPORT == no,  set mMode to MANAGER_MODE_DEFAULT");
        //    mMode = MANAGER_MODE_DEFAULT;
        //}

        MtkHandoverManager.createSingleton(context /*, handoverManager*/);


        if (mMode == MANAGER_MODE_WFA_CERTIFICATION) {
            MtkWfaCerHandoverManager.createSingleton(context /*, handoverManager*/);
        }

        EvtTransactionHandle.createSingleton(context);


    }



    private int getMode() {
        return sModeChangeListener.getMode();

    }

    private NdefMessage createWfaCerHrM() {
        Log.i(TAG, "createWfaCerHrM()  ");
        try {

            //if(getMode() != MANAGER_MODE_WFA_CERTIFICATION){
            //    Log.i(TAG, "createWfaCerHrM() Bypass");
            //    return null;
            //}

            Uri[] uriArray = {Uri.parse("file:///L/useless")};
            return MtkWfaCerHandoverManager.getInstance().CreateHrMEntry(uriArray);
        } catch (Exception e) {
            Log.i(TAG, "createWfaCerHrM() Exception:" + e);
            e.printStackTrace();
        }
        return null;
    }

    public NdefMessage createRequestMessage() {
        Log.i(TAG, "createWfaCerHrM()  ");
        try {

            if (getMode() == MANAGER_MODE_WFA_CERTIFICATION) {
                return createWfaCerHrM();
            } else {
                if(mCurrentUserId == 0){
                return MtkHandoverManager.getInstance().CreateHrMEntry(null);
                }else{
                    Log.i(TAG, "mCurrentUserId:"+mCurrentUserId+" !!!!  use Android BT Beam !!!!");
                    return null;
                }
            }
        } catch (Exception e) {
            Log.i(TAG, "createRequestMessage() Exception:" + e);
            e.printStackTrace();
        }
        return null;
    }

    public NdefMessage composeMtkLBeamNdefMessage(NdefRecord record, NdefRecord... records) {
        Log.i(TAG, "composeMtkLBeamNdefMessage() ");

        NdefRecord[] mRecords;


        // validate
        if (record == null) throw new NullPointerException("record cannot be null");

        for (NdefRecord r : records) {
            if (r == null) {
                throw new NullPointerException("record cannot be null");
            }
        }

        mRecords = new NdefRecord[1 + records.length + 1];
        mRecords[0] = record;
        System.arraycopy(records, 0, mRecords, 1, records.length);

        mRecords[1 + records.length] = createMtkLBeamRecord();


        return new NdefMessage(mRecords);

    }

    private NdefRecord createMtkLBeamRecord() {
        Log.i(TAG, "createMtkLBeamRecord() ");

        try {
            // create a byte buffer
            ByteBuffer mtkSpecific = ByteBuffer.allocate(MTK_VERSION_TLV_SIZE);

            // Version TLV
            mtkSpecific.putShort(MTK_ATTRIBUTE_TYPE_VERSION_ID); // TAG
            mtkSpecific.putShort((short) 1); // Length
            mtkSpecific.put(MTK_VERSION); // 1 byte value only

            byte[] payload = mtkSpecific.array();

            return new NdefRecord(NdefRecord.TNF_MIME_MEDIA, TYPE_MTK_L_PROPRIETARY.getBytes(), null, payload);
        } catch (Exception e) {
            Log.i(TAG, "createMtkLBeamRecord() Exception:" + e);
            e.printStackTrace();
        }
        return null;

    }


    public int parseHandoverMessage(NdefMessage handoverMessage) {

        Log.d(TAG, " parseHandoverMessage()  handoverMessage:" + Util.printNdef(handoverMessage));

        int mScenario = SCENARIO_JB_ORIGINAL;


        if(mCurrentUserId != 0){
            Log.i(TAG, "mCurrentUserId:"+mCurrentUserId+" !!!!  use Android BT Beam !!!!");
            return SCENARIO_JB_ORIGINAL;
        }

        try {

            /*
                *       1.parse HandoverMessage
                */

            //1.1 Try Wifi AC exist or not  , if not , BT hit  JB original
            NdefRecord r = handoverMessage.getRecords()[0];
            if (r.getTnf() != NdefRecord.TNF_WELL_KNOWN) return mScenario;
            //if (!Arrays.equals(r.getType(), NdefRecord.RTD_HANDOVER_REQUEST)) return null;


            //= whichScenario(tryMessage);
            HandoverMessageElement mHandoverMsgElement = new HandoverMessageElement(handoverMessage);
            mScenario = mHandoverMsgElement.getScenario();
            Log.d(TAG, "mScenario:" + mScenario);

            if (getMode() == MANAGER_MODE_WFA_CERTIFICATION) {
                return mScenario;
            } else {
                if (mScenario == SCENARIO_WIFI_LEGACY || mScenario == SCENARIO_WFA_P2P) {
                    Log.d(TAG, "!!!! Android handle, mScenario to SCENARIO_JB_ORIGINAL !!!!");
                    return SCENARIO_JB_ORIGINAL;
                }
            }


            return mScenario;

        } catch (Exception e) {
            Log.d(TAG, "Exception:" + e);
            e.printStackTrace();

            return mScenario;
        }

    }

    //Scenario == Beam+ or WFA certification
    public NdefMessage tryHandoverRequest(int scenario, NdefMessage handoverRequest) {

        Log.i(TAG, "tryHandoverRequest() scenario:" + scenario);
        try {

            switch(scenario) {
             case SCENARIO_BEAMPLUS_P2P:
                return MtkHandoverManager.getInstance().tryMtkHandoverRequest(handoverRequest);

             case SCENARIO_WIFI_LEGACY:
             case SCENARIO_WFA_P2P:
                return MtkWfaCerHandoverManager.getInstance().tryMtkHandoverRequest(handoverRequest);

             default:
                Log.i(TAG, "should not enter");
                break;
            }

        } catch (Exception e) {
            Log.d(TAG, "Exception:" + e);
            e.printStackTrace();
        }
        return null;

    }

    //Scenario == Beam+ or WFA certification
    public void doHandoverUri(int scenario, Uri[] uris,
                              NdefMessage handoverResponse) {
        Log.i(TAG, "doHandoverUri() scenario:" + scenario);

        try {

            switch(scenario) {
             case SCENARIO_BEAMPLUS_P2P:
                MtkHandoverManager.getInstance().doHsMHandoverEntry(uris, handoverResponse);
                break;
             case SCENARIO_WIFI_LEGACY:
             case SCENARIO_WFA_P2P:
                MtkWfaCerHandoverManager.getInstance().doHsMHandoverEntry(uris, handoverResponse);
                break;
             default:
                Log.i(TAG, "should not enter");
                break;
            }

        } catch (Exception e) {
            Log.d(TAG, "Exception:" + e);
            e.printStackTrace();
        }

    }

    public boolean tagDispatch(NdefMessage message) {
        boolean result = false;

        if (getMode() != MANAGER_MODE_WFA_CERTIFICATION) {
            Log.i(TAG, "tagDispatch() mode invalid ,return false");
            return false;
        }

        try {
            result = MtkWfaCerHandoverManager.tagDispatch(message);
        } catch (Exception e) {
            Log.i(TAG, "Exception:" + e);
            e.printStackTrace();
        }

        Log.i(TAG, "tagDispatch() return:" + result);
        return result;
    }

    public void notifyHandoverComplete() {

        if (getMode() == MANAGER_MODE_WFA_CERTIFICATION) {
            MtkWfaCerHandoverManager.getInstance().notifyHandoverComplete();
        }

    }


    private final BroadcastReceiver mSwitchUserReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if(action == null){
                Log.e(TAG, "mAddonBroadcastReceiver onReceive() action == null");
                return;
            }
            
            if (action.equals(Intent.ACTION_USER_SWITCHED)) {

                Log.d(TAG, "rec. ACTION_USER_SWITCHED ,mCurrentUserId");
                int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE,-1);
                    
                Log.d(TAG, "ACTION_USER_SWITCHED , Switch to new user id:"+userId);
                if(userId != -1){
                    mCurrentUserId = userId;
                }

            } 
        }
    };


    static private class ModeChangeListener {
        static private final String PREF_MTK_HANDOVER_MANAGER_MODE = "com.mediatek.nfc.MtkHandoverManager_Mode";
        private SharedPreferences mPrefs;

        public ModeChangeListener(Context context) {
            mPrefs = context.getSharedPreferences(NfcService.PREF, Context.MODE_PRIVATE);
            IntentFilter filter = new IntentFilter(ACTION_SET_HANDOVER_MODE);
            filter.addAction(ACTION_QUERY_HANDOVER_MODE);
            context.registerReceiver(mReceiver, filter);
        }

        public int getMode() {
            synchronized (ModeChangeListener.this) {
                int mode = mPrefs.getInt(PREF_MTK_HANDOVER_MANAGER_MODE, MANAGER_MODE_INVALID);
                Log.d(TAG, "ModeChangeListener.getMode():" + describeHandoverMode(mode));
                return mode;
            }
        }

        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if(action == null){
                    Log.e(TAG, "onReceive() action == null");
                    return;
                }
                synchronized (ModeChangeListener.this) {
                    if (action.equals(ACTION_SET_HANDOVER_MODE)) {
                        int mode = intent.getIntExtra(EXTRA_MODE_VALUE, MANAGER_MODE_DEFAULT);
                        mPrefs.edit().putInt(PREF_MTK_HANDOVER_MANAGER_MODE, mode).apply();
                        Log.d(TAG, ACTION_SET_HANDOVER_MODE + ", mode = " + describeHandoverMode(mode));
                    }
                    if (action.equals(ACTION_QUERY_HANDOVER_MODE)) {
                        int mode = getMode();
                        Log.d(TAG, ACTION_QUERY_HANDOVER_MODE + ", mode = " + describeHandoverMode(mode));

                        // sendBroadcast of Query result
                        Intent queryResultIntent = new Intent(ACTION_QUERY_HANDOVER_MODE_RESULT);
                        queryResultIntent.putExtra(EXTRA_MODE_VALUE, mode);
                        context.sendBroadcast(queryResultIntent);
                    }
                }
            }
        };

        public String describeHandoverMode(int mode) {
            String status = "[]";
            switch (mode) {
            case MANAGER_MODE_DEFAULT:
                status = "DEFAULT Mode";
                break;
            case MANAGER_MODE_WFA_CERTIFICATION:
                status = "WFA CERTIFICATION Mode";
                break;
            case MANAGER_MODE_INVALID:
                status = "WFA  Mode Invalid";
                break;
            default:
                status = "UNKNOWN MODE";
                break;
            }
        return  mode + ", " + status;
    }
    }

}
