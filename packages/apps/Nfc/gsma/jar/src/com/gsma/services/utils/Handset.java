package com.gsma.services.utils;



import java.lang.Exception;
import java.lang.String;

import android.util.Log;
import android.os.RemoteException;

import com.gsma.services.addon.GsmaRuntimeOptions;
import com.gsma.services.addon.GsmaUtil;
import com.gsma.services.addon.NfcGsmaExtra;

/**
 *  this class of gsma Utils
 */
public class Handset extends java.lang.Object{

    static final String TAG = "Handset";

    public static final int HCI_SWP                 = 0;
    public static final int MULTIPLE_ACTIVE_CEE     = 1;
    public static final int FELICA                  = 32;
    public static final int MIFARE_CLASSIC          = 33;
    public static final int MIFARE_DESFIRE          = 34;
    public static final int NFC_FORUM_TYPE3         = 35;
    public static final int OMAPI                   = 80;
    public static final int BATTERY_LOW_MODE        = 144;
    public static final int BATTERY_POWER_OFF_MODE  = 145;

    public static final int DEFAULT_VERSION  = 6000;
    //private INfcGsma mNfcFunction = null;



    //constructor
    public Handset() {
        Log.d(TAG, "Handset()");

    }

    /**
    *   getVersion
    *
    *   
    *    Return the version of device requirements supported.
    *
    *   
    * @param  null
    * @return   int version
    * @see         null
    */
    public int getVersion() {
        Log.d(TAG, "getVersion()");

        int version = GsmaRuntimeOptions.getGsmaVersion();
        
        Log.d(TAG, "version:"+version+"  Hex:"+Integer.toHexString(version));

        if(version == 0){
            Log.d(TAG, "return DEFAULT_VERSION: "+DEFAULT_VERSION);
            return DEFAULT_VERSION;
        }else{
            int majorVersion = ((version & 0xF0) >> 4)*1000;

            int minorVersion = (version & 0x0F);
            
            Log.d(TAG, " majorVersion:"+majorVersion+" minorVersion:"+minorVersion);
            return majorVersion+minorVersion;
        }
    }
    
    /**
    *   getProperty
    *
    *   
    *   Return handset status for the following features:
    *
    *     HCI_SWP, MULTIPLE_ACTIVE_CEE 
    *     FELICA, MIFARE_CLASSIC, MIFARE_DESFIRE, NFC_FORUM_TYPE3 
    *     OMAPI 
    *     BATTERY_LOW_MODE, BATTERY_POWER_OFF_MODE
    *
    *
    *     Parameters:
    *     feature - Requested feature 
    *     Returns:
    *     true if the feature is supported; false otherwise 
    *
    *
    *        Throws: 
    *         java.lang.IllegalArgumentException - 
    *         Indicate that a method has been passed an illegal or inappropriate argument.
    *   
    *
    * @param  feature     - Requested feature 
    * @return   boolean    true if the feature is supported; false otherwise 
    * @see         null
    */   
    public boolean getProperty(int feature) {
        Log.d(TAG, "getProperty()  feature:  "+describeFeature(feature)+":"+feature);


        if(feature != HCI_SWP && 
           feature != MULTIPLE_ACTIVE_CEE &&
           feature != FELICA &&
           feature != MIFARE_CLASSIC &&
           feature != MIFARE_DESFIRE &&
           feature != NFC_FORUM_TYPE3 &&
           feature != OMAPI &&
           feature != BATTERY_LOW_MODE &&
           feature != BATTERY_POWER_OFF_MODE) {
            Log.e(TAG, " throw new IllegalArgumentException , feature:"+feature);
            throw new IllegalArgumentException(" getProperty() feature:"+feature);
        }

   
        switch(feature){
            case OMAPI: 
            case MIFARE_DESFIRE: 
            case MIFARE_CLASSIC: 
            case FELICA:
            case HCI_SWP:
                Log.d(TAG, " return true");
                return true;

            case MULTIPLE_ACTIVE_CEE: 
                /*
                PackageManager pm = mContext.getPackageManager();
                boolean isHceCapable = pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);
                */
                return NfcGsmaExtra.getInstance().hasHCE();


                
            case NFC_FORUM_TYPE3: //TS26_NFC_REQ_092.6
                Log.d(TAG, "TS26_NFC_REQ_092.6 return false");
                return false;
                

            
            case BATTERY_LOW_MODE: 
            case BATTERY_POWER_OFF_MODE: //TS26_NFC_REQ_092.04, TS26_NFC_REQ_092.05
                Log.d(TAG, " BATTERY_LOW_MODE  BATTERY_POWER_OFF_MODE, return true");
                return true;                

     

        }
        Log.d(TAG, " out switch, return false");
        return false;
    }



    /**
    *   enableMultiEvt_transactionReception
    *
    *  Asks the system to inform "transaction events" to any authorized/registered components via BroadcastReceiver.
    *  Change SHALL not imply a power cycle and SHALL be valid until next handset reboot.
    *  
    *  Applications SHALL register to com.gsma.services.nfc.TRANSACTION_EVENT for receiving related events.
    *
    *   
    * @param  null
    * @return   null
    * @see         null
    */
    public void enableMultiEvt_transactionReception() {
        Log.d(TAG, "enableMultiEvt_transactionReception()");


    // TODO: implement Throws
/*
        Throws: 
        java.lang.SecurityException - 
        Indicate that application is not allowed to use this API.
        When UICC is the "active" SE, 
        only applications signed with certificates stored in the UICC are granted to call this API. 
        When eSE is the "active" SE, 
        only applications signed with system certificates are granted to call this API.
*/

        NfcGsmaExtra.getInstance().enableMultiEvtTransaction();


        
    }




    private String describeFeature(int feature) {
        String ret = "[unknow]";
        
        switch(feature){
            case OMAPI: 
                ret = "OMAPI";
                break;
            case NFC_FORUM_TYPE3:
                ret = "NFC_FORUM_TYPE3";
                break;                
            case FELICA:
                ret = "FELICA";
                break;                
            case HCI_SWP:
                ret = "HCI_SWP";
                break;

            case MIFARE_CLASSIC: 
                ret = "MIFARE_CLASSIC";
                break;
            case MIFARE_DESFIRE: 
                ret = "MIFARE_DESFIRE";
                break;
            case MULTIPLE_ACTIVE_CEE: 
                ret = "MULTIPLE_ACTIVE_CEE";
                break;

            
            case BATTERY_LOW_MODE: 
                ret = "BATTERY_LOW_MODE";
                break;
            case BATTERY_POWER_OFF_MODE: 
                ret = "BATTERY_POWER_OFF_MODE";
                break;
        }

        return  ret;
    }

}
