package com.gsma.services.nfc;



import java.lang.Exception;
import java.lang.String;

import android.nfc.NfcAdapter;

import android.util.Log;
import android.content.Context;
import android.os.Handler;
import android.os.RemoteException;

import android.provider.Settings;

//import com.mediatek.nfc.addon.MtkNfcAddonSequence;
//import android.os.IBinder;
//import android.os.ServiceManager;
import com.gsma.services.addon.NfcGsmaExtra;

//import com.gsma.services.INfcGsma;



public class SEController {

    static final String TAG = "SEController";

    private static Handler mHandler = new Handler();
    private static SEController mStaticInstance = null;
    private static Callbacks mCallback;

    private Context mContext;
    private NfcAdapter mNfcAdapter;

    //private INfcGsma mNfcFunction;

    private String EMULATION_OFF = null;

    public static final String SETTING_STR_SIM1 = "SIM1";
    public static final String SETTING_STR_SIM2 = "SIM2";
    public static final String SETTING_STR_SSD = "Smart SD card";
    public static final String SETTING_STR_ESE = "Embedded SE";
    public static final String SETTING_STR_OFF = "Off";


    private final String[] mSettingSeMap = {SETTING_STR_OFF, SETTING_STR_SIM1, SETTING_STR_SIM2, SETTING_STR_SSD, SETTING_STR_ESE};

    //GSMA define
    String noActiveSe   = "SE_DEACTIVE";
    String sim1String   = "SIM1";
    String sim2String   = "SIM2";
    String sdString     = "SD";
    String eSeString    = "eSE";

    //mActiveSe should map to SecureElementSelector.USER_SIM1:
    private final String[] mActiveSeMap = {noActiveSe, sim1String, sim2String, sdString, eSeString};

    String mActiveString;

    public interface Callbacks {
        void onGetDefaultController(SEController controller);
    }

    //constructor
    public SEController(Context context) {
        Log.d(TAG, "SEController()");
        mContext = context;

        Log.d(TAG, "NfcAdapter.getDefaultAdapter(mContext) mContext:"+mContext);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(mContext);

        if(mNfcAdapter == null) {
            Log.d(TAG, " NfcAdapter.getDefaultAdapter() return null");
        }

    

    }
    
    public static void createSingleton(Context context) {
        if (mStaticInstance == null) {
            mStaticInstance = new SEController(context);
        }
    }
    
    public static SEController getInstance() {
        return mStaticInstance;
    }

    /**
    *   getDefaultController
    *
    *   Get SEController
    *   
    *   Deprecated.  
    *         When Host Card Emulation (HCE) is supported
    *
    *  version 6.0 usqge
    *   
    * @param  context
    * @param  SEController.Callbacks 
    * @return   void
    * @see         null
    */
    public static void getDefaultController(android.content.Context context,
                        SEController.Callbacks cb){
        Log.d(TAG, "getDefaultController(context,cb)   cb:"+cb );
        
        createSingleton(context);

        if(cb != null){
            mHandler.postDelayed(runnable_callback, 20);
            //mCallbacks.add(cb);
            mCallback = cb;
        }
        
    }

    private static Runnable runnable_callback = new Runnable() {
        public void run() {
            Log.d(TAG, "excute getDefaultController(cb) callback mCallback:"+mCallback);
            if(mCallback!=null){
                mCallback.onGetDefaultController(getInstance());
            }
        }
    };

    /**
    *   getDefaultController
    *
    *   Get SEController
    *   
    *   Deprecated.  
    *         When Host Card Emulation (HCE) is supported
    *
    *  version 4.0 / 4.1 usqge
    *   
    * @param  SEName
    * @return   void
    * @see         null
    */
    public static SEController getDefaultController(android.content.Context context){
        Log.d(TAG, "getDefaultController(context) Version 4.0/4.1");
        createSingleton(context);
        return getInstance();
    }

    
    /**
    *    getActiveSecureElement
    *   
    *   Get the active SE
    *
    *   Deprecated.  
    *         When Host Card Emulation (HCE) is supported
    *
    * @param  null
    * @return   Active SE name
    * @see         null
    */
    public String getActiveSecureElement(){
        Log.d(TAG, "getActiveSecureElement()");

        int activeSe = NfcGsmaExtra.getInstance().getActiveSeValue();
        
        Log.i(TAG, "SecureElementSelector.getActiveSeValue() :" + activeSe);
        return mActiveString = mActiveSeMap[activeSe];

	}


    /**
    *   setActiveSecureElement
    *
    *   Set the active SE
    *   
    *   Deprecated.  
    *         When Host Card Emulation (HCE) is supported
    *
    *   
    * @param  SEName
    * @return   void
    * @see         null
    */
    public void setActiveSecureElement(String SEName){
        Log.d(TAG, "setActiveSecureElement() SEName:"+SEName);
        
        if(SEName == null)
            return;

        if(mNfcAdapter == null) {
            Log.e(TAG, " throw new IllegalStateException , mNfcAdapter == null");
            throw new IllegalStateException(" NFC Controller is not exist ,mNfcAdapter == null");
        }

        if(mNfcAdapter.isEnabled() == false) {
            Log.e(TAG, " throw new IllegalStateException , NFC is not enabled");
            throw new IllegalStateException(" NFC Controller is not enabled");
        }


         
        int settingIndex = translateSettingString(SEName);
        Log.d(TAG, " settingIndex:"+settingIndex);

        /*   
        //Set NFC_MULTISE_ACTIVE
        Settings.Global.putString(mContext.getContentResolver(), Settings.Global.NFC_MULTISE_ACTIVE,mSettingSeMap[settingIndex]);
       */

        NfcGsmaExtra.getInstance().setActiveSeValue(settingIndex);



        // TODO::  implement Throws
/*
    Throws: 
    java.lang.IllegalStateException - 
    Indicate that NFC Controller is not enabled. 
    java.lang.SecurityException - 
    Indicate that application SHALL be signed with a trusted certificate for using this API.
*/
        
	  
	}

    private int translateSettingString(String SEName){

        int i = 0;
        for(String compareString : mActiveSeMap){
            
            Log.d(TAG, "compare mActiveSeMap[ "+i+" ]");
            if (SEName.equals(compareString)) {
                return i;
            }
            i++;
        }
        
        return 0;
    }
    
}
