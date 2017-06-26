package com.mediatek.nfc.gsmahandset;


import java.util.List;


import android.app.Activity;
import android.app.PendingIntent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.content.pm.ResolveInfo;



//import android.media.MediaScannerConnection;




//import android.os.Environment;

import android.util.Log;









import org.simalliance.openmobileapi.service.ISmartcardServiceCallback;



/**
 * EvtTransactionHandle of GSAM NFCHandset
 */
public class EvtTransactionHandle {


    static final String TAG = "EvtTransactionHandle";
    static final boolean DBG = true;

    //static final String ACTION_BIND_SMARTCARD = "android.intent.action.BIND_SMARTCARD_SERVICE";

    final Context mContext;

    private static EvtTransactionHandle mStaticInstance = null;
    private boolean multiBroadcastFlag = false;

    //MTK EvtTransaction intent
    public static final String ACTION_EVT_TRANSACTION = "com.mediatek.nfc.ACTION_EVT_TRANSACTION";
    public static final String EXTRA_DATA = "com.mediatek.nfc.EvtTransaction.EXTRA_DATA";

    public static final String EXTRA_AID = "com.mediatek.nfc.EvtTransaction.EXTRA_AID";

    /**
     * This implementation is used to receive callbacks from backend.
     */
    private final ISmartcardServiceCallback mCallback = new ISmartcardServiceCallback.Stub() {
    };

    public EvtTransactionHandle(Context context) {
        mContext = context;
        Log.i(TAG, " EvtTransactionHandle Construct ");


        IntentFilter filter;
        filter = new IntentFilter();
        filter.addAction(ACTION_EVT_TRANSACTION);
        mContext.registerReceiver(mEvtReceiver, filter);

        AccessCheckImpl.createSingleton(mContext);


    }

    public static void createSingleton(Context context) {
        if (mStaticInstance == null) {
            mStaticInstance = new EvtTransactionHandle(context);
        }
    }

    public static EvtTransactionHandle getInstance() {
        return mStaticInstance;
    }

/*
    public void initAccessCheckImpl(){
        Intent bindSCSvcIntent = new Intent(ACTION_BIND_SMARTCARD);
        mContext.sendBroadcast(bindSCSvcIntent);

    }
*/

    public boolean processEvt(byte[] aid, byte[] data) {
        Log.i(TAG, " processEvt() ");

        List<ResolveInfo> interestedInfos;
        List<ResolveInfo> interestedInfos2;

        List<ResolveInfo> acPassedInfos  = null;
        List<ResolveInfo> acPassedInfos2 = null;

        Intent composeIntent;
        String activeSe;

        /*
        String aidString = bytesToString(aid);
        Log.i(TAG, "aidString: "+aidString);

        if(aidString.isEmpty()){
            Log.i(TAG, " aidString.isEmpty ,return false");
            return false;
        }
        */
        if (aid != null) {
            Log.i(TAG, "aid Length:" + aid.length + " byte[]:" + bytesToString(aid));
        }

        if (data != null) {
            Log.i(TAG, "data Length:" + data.length + " byte[]:" + bytesToString(data));
        }

        //1.compose intent
        composeIntent = AccessCheckImpl.getInstance().composeNewIntent(aid, data);
        if (composeIntent == null) {
            Log.i(TAG, " composeIntent == null, return false");
            return false;
        }

        //2.1 Query whather Receiver register GSMA intent
        interestedInfos = AccessCheckImpl.getInstance().getReceiverInterestedPackage(composeIntent);


        //2.2 Query whather Activity register GSMA intent
        interestedInfos2 = AccessCheckImpl.getInstance().getActivityInterestedPackage(composeIntent);

        if (interestedInfos.size() == 0 && interestedInfos2.size() == 0) {
            Log.i(TAG, "Receiver,Activity not register GSMA intent , return false ");
            return false;
        }


        //3. start accessControlCheck
        activeSe = AccessCheckImpl.getInstance().getActiveSeName();
        //if(activeSe.startsWith("SIM"))
        //{
            if (interestedInfos.size() != 0) {
            acPassedInfos = AccessCheckImpl.getInstance().accessControlCheck(aid, interestedInfos);
            }

            if (interestedInfos2.size() != 0) {
                acPassedInfos2 = AccessCheckImpl.getInstance().accessControlCheck(aid, interestedInfos2);
            }

            if (acPassedInfos == null && acPassedInfos2 == null) { //&& interestedInfos2.size() == 0
                Log.i(TAG, " acPassedInfos & 2 == null  , return false ");
                return false;
            }
        //}
        //else{
        //    Log.i(TAG, " activeSe: "+activeSe+"  ByPass AC check");
        //    acPassedInfos = interestedInfos;
        //}

        Log.i(TAG, " multiBroadcastFlag: " + multiBroadcastFlag);

        if (multiBroadcastFlag) {
            AccessCheckImpl.getInstance().multiBroadcastAction(composeIntent, acPassedInfos);
            return true;
        } else {
            //uni broadcast

            //1.Fg activity check
            if (isFgActivityRegister()) {
                // TODO:: send intent to FgActivity
                return true;
            } else {

                //2.backGround acivity check


                if (acPassedInfos != null) {
                    AccessCheckImpl.getInstance().priorityDispatch(composeIntent, acPassedInfos);
                }

                if (acPassedInfos2 != null) {
                    AccessCheckImpl.getInstance().activityDispatch(composeIntent, acPassedInfos2);
                }


                Log.i(TAG, " processEvt()  return true");
                return true;
            }
        }
    }

/*
    String getUiccName(){
        Log.i(TAG, "getUiccName TODO:: always return SIM1");
        // TODO::
        return "SIM1";
    }
*/

    void enableMultiBroadcast() {
        Log.i(TAG, "enableMultiBroadcast multiBroadcastFlag:" + multiBroadcastFlag);

        multiBroadcastFlag = true;

    }


    void disableMultiBroadcast() {
        Log.i(TAG, "disableMultiBroadcast multiBroadcastFlag:" + multiBroadcastFlag);

        multiBroadcastFlag = false;
    }




    void enableUiccForegroundDispatch(Activity fgActivity, PendingIntent pIntent, IntentFilter iFilter) {
        Log.i(TAG, "enableUiccForegroundDispatch  //TODO:");

    }

    void disableUiccForegroundDispatch(Activity fgActivity) {
        Log.i(TAG, "disableUiccForegroundDispatch  //TODO:");

    }



    boolean isFgActivityRegister() {
        Log.d(TAG, "//TODO :: isFgActivityRegister() always return false ");
        return false;

    }



    String getSelectedSE() {
            Log.i(TAG, "getSelectedSE  //TODO:return SIM1");
            return "SIM1";
    }

    void selectUicc() {
            Log.i(TAG, "selectUicc  //TODO");
    }

    String bytesToString(byte[] bytes) {
        if (bytes == null)
            return "";
        StringBuffer sb = new StringBuffer();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        String str = sb.toString();
        Log.d(TAG, "bytesToString str:(sb.toString())" + str);
        Log.d(TAG, "bytesToString  str.length():" + str.length());

        //if (str.length() > 0) {
        //    str = str.substring(0, str.length() - 1);
        //}
        return str;
    }


    private final BroadcastReceiver mEvtReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action == null) {
                Log.e(TAG, "onReceive() action == null");
                return;
            }

            if (action.equals(ACTION_EVT_TRANSACTION)) {
                Log.d(TAG, "Rec. ACTION_EVT_TRANSACTION");


                byte[] aid  = intent.getByteArrayExtra(EXTRA_AID);
                byte[] para = intent.getByteArrayExtra(EXTRA_DATA);

                processEvt(aid, para);
                //ExcuteThread mThread;

                //AccessCheckImpl.createSingleton(context);
                //mThread = new ExcuteThread("CreateSmartCardService");
                //mThread.start();

            }
        }
    };


/*
    class ExcuteThread extends Thread {
        String mThreadName;

        ExcuteThread(String threadName) {
            super(threadName);
            mThreadName = threadName;
        }

        @Override
        public void run() {
            try {
                Log.d(TAG, "ExcuteThread  call AccessCheckImpl.createSingleton()");
                AccessCheckImpl.createSingleton(mContext);
            } catch (Exception e) {
                Log.d(TAG, "ExcuteThread Exception:"+e);
                e.printStackTrace();
            }
        }
    }
*/

}

