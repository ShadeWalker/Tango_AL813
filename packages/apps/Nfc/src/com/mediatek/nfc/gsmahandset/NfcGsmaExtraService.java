/*
 * Copyright (C) 2011, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mediatek.nfc.gsmahandset;


//import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.os.ServiceManager;

import android.os.RemoteException; 

import android.util.Log;

import android.nfc.INfcAdapterGsmaExtras;

import com.mediatek.nfc.addon.MtkNfcAddonSequence;
import com.mediatek.nfc.Util;

import com.android.nfc.NfcService;

import org.simalliance.openmobileapi.service.ISmartcardService;
import org.simalliance.openmobileapi.service.ISmartcardServiceCallback;
import org.simalliance.openmobileapi.service.SmartcardError;

import java.lang.Class;
import java.lang.reflect.Field;

import java.lang.Exception;

import android.provider.Settings;

/**
 * The smartcard service is setup with privileges to access smart card hardware.
 * The service enforces the permission
 * 'org.simalliance.openmobileapi.service.permission.BIND'.
 */
public final class NfcGsmaExtraService extends INfcAdapterGsmaExtras.Stub {
	  static final String TAG = "nfc.NfcGsmaExtraService";

    private static NfcGsmaExtraService sSingleton = null;
    private Context mContext;
    final int mDefaultOffHostRoute;
    private ISmartcardService mService = null;

    public static final int SIM_1 = 1;
    public static final int SIM_2 = 2;
    public static final int SIM_3 = 3;
    
    private final ISmartcardServiceCallback mCallback = new ISmartcardServiceCallback.Stub() {
    };

    public static final String SETTING_STR_SIM1 = "SIM1";
    public static final String SETTING_STR_SIM2 = "SIM2";
    public static final String SETTING_STR_SSD = "Smart SD card";
    public static final String SETTING_STR_ESE = "Embedded SE";
    public static final String SETTING_STR_OFF = "Off";

    private final String[] mSettingSeMap = {SETTING_STR_OFF, SETTING_STR_SIM1, SETTING_STR_SIM2, SETTING_STR_SSD, SETTING_STR_ESE};

    
    private native int doGetDefaultOffHostRouteDestination();
    SmartcardError mSmartCardErrorResult = new SmartcardError();
    
    private NfcGsmaExtraService() {
        super();
        Log.d(TAG, "NfcGsmaExtraService()");
        mDefaultOffHostRoute = 0x01;//doGetDefaultOffHostRouteDestination();
    }
    
    public static NfcGsmaExtraService getInstance() {
        if (sSingleton == null) {
            sSingleton = new NfcGsmaExtraService();
        }
        
        return sSingleton;
    }
     
    /**
     * The INfcAdapterGsmaExtras interface implementation.
     */

    	
    @Override
    public int getActiveSeValue() throws RemoteException{
    
        Log.d(TAG, "getActiveSeValue()");
        int activeSe = MtkNfcAddonSequence.getInstance().getActiveSeValue();

        return activeSe;
    }

    @Override
    public void setActiveSeValue(int seValue) throws RemoteException{
    
        //Log.d(TAG, "setActiveSeValue() seValue:"+seValue);
        //int activeSe = MtkNfcAddonSequence.getInstance().getActiveSeValue();

        Log.d(TAG, " write NFC_MULTISE_ACTIVE  value:"+mSettingSeMap[seValue]);
        MtkNfcAddonSequence.getInstance().setActiveSeValue(mSettingSeMap[seValue]);
        
    }

    
	@Override		    
    public void commitRouting() throws RemoteException{
        Log.d(TAG, "commitRouting()");
    	NfcService.getInstance().commitRouting();
    }

    @Override		    
    public void routeAids(String aid)throws RemoteException{
        Log.d(TAG, "routeAids() , route is hard code: ");
        NfcService.getInstance().routeAids(aid, mDefaultOffHostRoute);
    }

   /**
     * Query if system has HCE Feature
     */
    @Override		    
    public boolean isHceCapable()throws RemoteException{
        Log.d(TAG, "isHceCapable()  ");
        boolean result=false;

        try{
            Class<?> clazz = NfcService.getInstance().getClass();

            Log.d(TAG, " getDeclaredField(mIsHceCapable)");

            Field isHceCapableField = clazz.getDeclaredField("mIsHceCapable");

            Log.d(TAG, "isHceCapableField:"+isHceCapableField);
            isHceCapableField.setAccessible(true);
            Log.d(TAG, " Field.setAccessible(true)");

            result = isHceCapableField.getBoolean(NfcService.getInstance());
            
            Log.d(TAG, "isHceCapable()   mIsHceCapable:"+result);
        }catch(Exception E){
            Log.d(TAG, "isHceCapable() Excception: "+E);
            E.printStackTrace();
        }
        
        return result;
            
        //return NfcService.getInstance().mIsHceCapable;
    }
		 

		    
    @Override
    public boolean enableMultiEvtTransaction() throws RemoteException{
    
        Log.d(TAG, "enableMultiEvtTransaction() , reboot to disable this function");
        EvtTransactionHandle.getInstance().enableMultiBroadcast();
        
        return true;
    }
     

    @Override
    public synchronized boolean[] isNFCEventAllowed(String reader, 
							        		byte[] aid,
							            String[] packageNames)throws RemoteException{
        Log.d(TAG, "isNFCEventAllowed() reader:"+reader+" aid:"+Util.printNdef(aid));
        boolean[] boolRet = null;


        mService = AccessCheckImpl.getInstance().getSmartCardService();

        if(mService == null){
            
            Log.e(TAG, " SmartCardService is null ");
            throw new RemoteException("SmartCardService is null ");
        }


        try {
            boolRet = mService.isNFCEventAllowed(reader,
                    aid,
                    packageNames,
                    mCallback,
                    mSmartCardErrorResult);
        } catch (RemoteException e) {
            Log.e(TAG, "mService.isNFCEventAllowed()  RemoteException:" + e);
            e.printStackTrace();
            throw new RemoteException(" isNFCEventAllowed() RemoteException "+ e);
        } catch (Exception exception) {
            Log.e(TAG, "mService.isNFCEventAllowed   Exception:" + exception);
            exception.printStackTrace();
            return null;
        }

        int pkgCount = 0;

        for (boolean bRet : boolRet) {
            Log.d(TAG, "boolean[] result: ["+ pkgCount+"]  bRet:" + bRet);
            pkgCount++;
        }

        return boolRet;
    }
    
    public boolean setNfcSwpActive(int simID) {
        return NfcService.getInstance().doActiveSecureElementById(simID);
    }


}
