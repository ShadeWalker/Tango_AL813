package com.mediatek.nfc.addon;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

//import android.provider.Settings;
import com.android.nfc.NfcService;
import com.android.nfc.dhimpl.NativeNfcManager;

import android.provider.Settings;

import android.os.SystemProperties;
import android.content.ContentResolver;

public class MtkNfcAddonSequence
    implements ISeController.Callback {
    private static final String TAG = "MtkNfcAddonSequence";

    public static final String PREF = "NfcServicePrefs";

    public static final int MODE_READER = 1;
    public static final int MODE_CARD = 2;
    public static final int MODE_P2P = 4;

    public static final int FLAG_OFF = 0;
    public static final int FLAG_ON = 1;
    public static final String PREF_MODE_READER = "nfc.pref.mode.reader";
    public static final String PREF_MODE_P2P = "nfc.pref.mode.p2p";
    public static final String PREF_MODE_CARD = "nfc.pref.mode.card";



    public static final int DISABLE_CARD_MODE_OFF   = 0;
    public static final int DISABLE_CARD_MODE_ON    = 1;
    public static final int DISABLE_AIR_PLANE_MODE  = 2;
    private int mDisableScenario = DISABLE_CARD_MODE_OFF;

    static final String PREF_NFC_ON = "nfc_on";


    //sync with EvtTransactionHandle
    public static final String ACTION_EVT_TRANSACTION = "com.mediatek.nfc.ACTION_EVT_TRANSACTION";
    public static final String EXTRA_DATA = "com.mediatek.nfc.EvtTransaction.EXTRA_DATA";
    public static final String EXTRA_AID = "com.mediatek.nfc.EvtTransaction.EXTRA_AID";

    private NativeNfcManager mNativeNfcManager;
    private SharedPreferences mPrefs;
    private SharedPreferences.Editor mPrefsEditor;
    private Context mContext;
    private ISeController mSecureElementSelector;

    private static MtkNfcAddonSequence sSingleton;
    //private MessageHandler mHandler = new MessageHandler();

    private MtkNfcAddonSequence(Context context, NativeNfcManager manager) {

        mNativeNfcManager = manager;
        mContext = context;
        mPrefs = mContext.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        mPrefsEditor = mPrefs.edit();
        //if()
        mSecureElementSelector = new SecureElementSelector(mContext, mNativeNfcManager, NfcService.getInstance(), this);

    }

    public static void createSingleton(Context context, NativeNfcManager manager) {
        sSingleton = new MtkNfcAddonSequence(context, manager);
    }

    public static MtkNfcAddonSequence getInstance() {
        return sSingleton;
    }

/*
    public void abortToDisable(){

        Log.d(TAG, "abortToDisable() " );
        NfcService.getInstance().mNfcAdapter.disable(true);

    }
*/
    public int getActiveSeValue() {
        return mSecureElementSelector.getActiveSeValue();
    }

    public void setActiveSeValue(String seString) {

    Log.d(TAG, " setActiveSeValue  seString:"+seString);
    //Set NFC_MULTISE_ACTIVE
    Settings.Global.putString(mContext.getContentResolver(), Settings.Global.NFC_MULTISE_ACTIVE,seString);
    }


    //Nfc Framework API
    public int getModeFlag(int mode, Object syncObj) {
        Log.d(TAG, "getModeFlag, mode = " + mode);
        int flag = -1;
        synchronized (syncObj) {
            if (MODE_READER == mode) {
                flag = mPrefs.getInt(PREF_MODE_READER, 1);
            } else if (MODE_P2P == mode) {
                flag = mPrefs.getInt(PREF_MODE_P2P, 1);
            } else if (MODE_CARD == mode) {
                flag = mPrefs.getInt(PREF_MODE_CARD, 1);
            }
        }
        Log.d(TAG, "return = " + flag);
        return flag;
    }

    //Nfc Framework API
    public void setModeFlag(boolean isNfcEnabled, int mode, int flag, Object syncObj) {
        Log.d(TAG, "setModeFlag, isNfcEnabled = " + isNfcEnabled + ", mode = " + mode + ", flag = " + flag + ", syncObj = " + syncObj);
        if (mode == MODE_CARD) {
            Log.d(TAG, "bypass card mode control from Setting");
            return;
        }
        synchronized (syncObj) {
            if ((mode > (MODE_READER | MODE_P2P | MODE_CARD) || mode < 0) ||
                (flag != FLAG_ON && flag != FLAG_OFF)) {
                Log.d(TAG, "incorrect mode:" + mode + " or flag:" + flag + ", return");
                return;
            }
            if ((mode & MODE_READER) != 0) {
                mPrefsEditor.putInt(PREF_MODE_READER, flag);
                mPrefsEditor.apply();
            }
            if ((mode & MODE_P2P) != 0) {
                mPrefsEditor.putInt(PREF_MODE_P2P, flag);
                mPrefsEditor.apply();
            }
            if ((mode & MODE_CARD) != 0) {
                mPrefsEditor.putInt(PREF_MODE_CARD, flag);
                mPrefsEditor.apply();
            }

            if (isNfcEnabled) {
                Log.d(TAG, "Ready for ApplyPollingLoopThread");
                new ApplyPollingLoopThread(mode, flag, new WatchDogThread("mtk_setmode_applyRouting", 10000), syncObj).start();
            }
        }
    }

    public void setScenario(int scenario) {
        Log.d(TAG, "setScenario ," + scenario);
        if (scenario >= DISABLE_CARD_MODE_OFF && scenario <= DISABLE_AIR_PLANE_MODE)
        mDisableScenario = scenario;
    }

    public void applyIpoSequence() {
        Log.d(TAG, "applyIpoSequence");
        mSecureElementSelector.applyIpoSequence();
    }

    public void applyInitializeSequence() {
        Log.d(TAG, "applyInitializeSequence ENTRY");

        PackageManager pm = mContext.getPackageManager();
        boolean isHceCapable = pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);

        boolean isHceOn;

        if(SystemProperties.get("ro.mtk_bsp_package").equals("1") && isHceCapable){
            Log.d(TAG, " BSP Package, isHceCapable==1");
            
            ContentResolver mContentResolver = mContext.getContentResolver();
            int hceFlag = Settings.Global.getInt(mContentResolver, "nfc_hce_on", 0);
            if(hceFlag == 0){
                Log.d(TAG, " Set nfc_hce_on to 1");
                Settings.Global.putInt(mContentResolver, "nfc_hce_on", 1); //init value
            }
            isHceOn = true;
        }else{
            isHceOn = Settings.Global.getInt(mContext.getContentResolver(), "nfc_hce_on", 0) != 0;
        }

        // Settings.Global.NFC_HCE_ON to "nfc_hce_on"

        if (mSecureElementSelector.init() || (isHceCapable && isHceOn)) {
            //curMode |= MODE_CARD;
            mPrefsEditor.putInt(PREF_MODE_CARD, FLAG_ON).apply();
        } else {
            mPrefsEditor.putInt(PREF_MODE_CARD, FLAG_OFF).apply();
        }

        //Log.d(TAG, "showNotification()");
        //NfcStatusNotificationUi.getInstance().showNotification();


        Log.d(TAG, "applyInitializeSequence EXIT   isHceCapable:" + isHceCapable + " isHceOn:" + isHceOn);
        //not apply Mode here -- mPrefsEditor.putInt(PREF_MODE_CARD, FLAG_OFF).apply();
    }

    public void applyDeinitializeSequence() {
        Log.d(TAG, "applyDeinitializeSequence ENTRY, mDisableScenario = " + mDisableScenario);
        int curMode = 0;
        boolean disableCardMode = true;

        if (mDisableScenario == DISABLE_AIR_PLANE_MODE) {
            disableCardMode = (NfcRuntimeOptions.isNoCardEmuInFlyMode() ? true : false);
        } else if (mPrefs.getBoolean(PREF_NFC_ON, true)) {
            disableCardMode = false;
        }

        //new EnableDisableTask(saveState).execute(TASK_DISABLE);

        if (!disableCardMode && (mPrefs.getInt(PREF_MODE_CARD, 1) == 1)) {
            curMode |=  MODE_CARD;
        }
        Log.d(TAG, "applyDeinitializeSequence disableCardMode=" + disableCardMode + "  curMode=" + curMode);

        //mNativeNfcManager.disableDiscovery();
        mSecureElementSelector.deinit(curMode == 0);
        //mNativeNfcManager.setNfcMode(curMode);
        if (curMode != 0) {
            mNativeNfcManager.setNfcModePolling(curMode, true);
        } else {
            mNativeNfcManager.setNfcModePolling(curMode, false);
        }

        //Log.d(TAG, "showNotification()");
        //NfcStatusNotificationUi.getInstance().showNotification();


        Log.d(TAG, "applyDeinitializeSequence EXIT");

    }

    public void applyEnableDiscoverySequence() {
        /// precondition: discovery is disabled
        Log.d(TAG, "applyEnableDiscoverySequence ENTRY");
        //mNativeNfcManager.disableDiscovery();
        int curMode = 0;
        curMode |= (mPrefs.getInt(PREF_MODE_READER, 1) == 1) ? MODE_READER : 0;
        curMode |= (mPrefs.getInt(PREF_MODE_P2P, 1) == 1) ? MODE_P2P : 0;
        curMode |= (mPrefs.getInt(PREF_MODE_CARD, 1) == 1) ? MODE_CARD : 0;

        boolean isHceOn = Settings.Global.getInt(mContext.getContentResolver(), "nfc_hce_on", 0) != 0;

        Log.d(TAG, "before check HCE switch, isHceOn: " + isHceOn + "curMode = " + curMode);

        if ((curMode & MODE_CARD) > 0) {
            if (!isHceOn && mSecureElementSelector.getActiveSeValue() == SecureElementSelector.USER_OFF) {
                mPrefsEditor.putInt(PREF_MODE_CARD, FLAG_OFF).apply();
                curMode &= (MODE_READER | MODE_P2P);
                Log.d(TAG, "  curMode &= 5");
            }
        } else {
            if (isHceOn || mSecureElementSelector.getActiveSeValue() != SecureElementSelector.USER_OFF) {
                mPrefsEditor.putInt(PREF_MODE_CARD, FLAG_ON).apply();
                curMode |= MODE_CARD;
            }
        }

        Log.d(TAG, " final curMode = " + curMode);

        mNativeNfcManager.setNfcModePolling(curMode, false);

        Log.d(TAG, "applyEnableDiscoverySequence EXIT");
    }


    public void applyDisableDiscoverySequence() {
        /// precondition: discovery is disabled
        Log.d(TAG, "applyDisableDiscoverySequence ENTRY");
        int curMode = 0;

        if (mPrefs.getInt(PREF_MODE_CARD, 1) == 1) {
            curMode |=  MODE_CARD;
        }
        mNativeNfcManager.setNfcModePolling(curMode, true);
        Log.d(TAG, "applyDisableDiscoverySequence EXIT");
    }

    public void evtTransactionBroadcast(byte[] aid, byte[] para) {
        Log.d(TAG, "evtTransactionBroadcast  ");


        try {

        Log.d(TAG, "aid: Length:" + aid.length + " data:" + Util.bytesToString(aid));
        Log.d(TAG, "para: Length:" + para.length + " data:" + Util.bytesToString(para));


        Intent evtIntent = new Intent(ACTION_EVT_TRANSACTION);
        evtIntent.putExtra(EXTRA_AID, aid);
        evtIntent.putExtra(EXTRA_DATA, para);
        mContext.sendBroadcast(evtIntent);


        } catch (Exception e) {
            Log.e(TAG, "Exception e:" + e);
            e.printStackTrace();
        }



    }


    class ApplyPollingLoopThread extends Thread {
        int mMode;
        int mFlag;
        WatchDogThread mWatchDog;
        Object mSync;

        ApplyPollingLoopThread(int mode, int flag, WatchDogThread watchDog, Object syncObj) {
            mMode = mode;
            mFlag = flag;
            mWatchDog = watchDog;
            mSync = syncObj;
        }

        @Override
        public void run() {
            int ret = -1;
            int curMode = 0;
            mWatchDog.start();
            synchronized (mSync) {
                try {
                    //mNativeNfcManager.disableDiscovery();
                    curMode |= (mPrefs.getInt(PREF_MODE_READER, 1) == 1) ? MODE_READER : 0;
                    curMode |= (mPrefs.getInt(PREF_MODE_P2P, 1) == 1) ? MODE_P2P : 0;
                    curMode |= (mPrefs.getInt(PREF_MODE_CARD, 1) == 1) ? MODE_CARD : 0;
                    Log.d(TAG, "ApplyPollingLoopThread curMode= " + curMode);
                    //mNativeNfcManager.setNfcMode(curMode);
                    mNativeNfcManager.setNfcModePolling(curMode, true);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {

                }
            }
            mWatchDog.cancel();
        }
    }

    class WatchDogThread extends Thread {
        final Object mCancelWaiter = new Object();
        final int mTimeout;
        boolean mCanceled = false;

        public WatchDogThread(String threadName, int timeout) {
            super(threadName);
            mTimeout = timeout;
        }

        @Override
        public void run() {
            try {
                synchronized (mCancelWaiter) {
                    mCancelWaiter.wait(mTimeout);
                    if (mCanceled) {
                        return;
                    }
                }
            } catch (InterruptedException e) {
                // Should not happen; fall-through to abort.
                Log.w(TAG, "Watchdog thread interruped.");
                interrupt();
            }
            Log.e(TAG, "Watchdog triggered, aborting.");
            mNativeNfcManager.doAbort();
        }

        public synchronized void cancel() {
            synchronized (mCancelWaiter) {
                mCanceled = true;
                mCancelWaiter.notify();
            }
        }
    }




    /// ISeController.Callback
    public void onDeselectByUser(boolean enableDiscovery) {
        int curMode = 0;
        PackageManager pm = mContext.getPackageManager();
        boolean isHceCapable = pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);
        // TODO: Settings.Global.NFC_HCE_ON
        boolean isHceOn = Settings.Global.getInt(mContext.getContentResolver(), "nfc_hce_on", 0) != 0;

        curMode |= (mPrefs.getInt(PREF_MODE_READER, 1) == 1) ? MODE_READER : 0;
        curMode |= (mPrefs.getInt(PREF_MODE_P2P, 1) == 1) ? MODE_P2P : 0;

        if (!isHceCapable || !isHceOn) {
            mPrefsEditor.putInt(PREF_MODE_CARD, FLAG_OFF).apply();
        }
        else {
            curMode |= (mPrefs.getInt(PREF_MODE_CARD, 1) == 1) ? MODE_CARD : 0;
        }
        Log.d(TAG, "onDeselectByUser curMode = " + curMode);

        //mNativeNfcManager.setNfcMode(curMode);
        if (enableDiscovery) {
            mNativeNfcManager.setNfcModePolling(curMode, true);

        }
    }

    public void onSelectByUser() {
        int curMode = MODE_CARD;
        curMode |= (mPrefs.getInt(PREF_MODE_READER, 1) == 1) ? MODE_READER : 0;
        curMode |= (mPrefs.getInt(PREF_MODE_P2P, 1) == 1) ? MODE_P2P : 0;
        mPrefsEditor.putInt(PREF_MODE_CARD, FLAG_ON).apply();
        Log.d(TAG, "onSelectByUser curMode = " + curMode);
        //mNativeNfcManager.setNfcMode(curMode);
        mNativeNfcManager.setNfcModePolling(curMode, true);

    }

/*

    static final int MSG_NOTIFY_EVT_TRANSACTION = 0x20;

    void sendMessage(int what, Object obj) {
        Message msg = mHandler.obtainMessage();
        msg.what = what;
        msg.obj = obj;
        mHandler.sendMessage(msg);
    }

    class MessageHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {

            Log.d(TAG, " MessageHandler  msg.what:" + msg.what);

            switch (msg.what) {
                case MSG_NOTIFY_EVT_TRANSACTION:
                    try{
                        Bundle EvtTranInfo = (Bundle) msg.obj;
                        byte[] aid = EvtTranInfo.getByteArray(EvtTransactionHandle.EXTRA_AID);
                        byte[] data = EvtTranInfo.getByteArray(EvtTransactionHandle.EXTRA_DATA);
                        EvtTransactionHandle.getInstance().processEvt(aid,data);
                    } catch (Exception e) {
                        Log.e(TAG, "Exception e:"+e);
                        e.printStackTrace();
                    }


                    break;
                default:
                    break;
            }
        }
    }
*/

}

