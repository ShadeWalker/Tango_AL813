package com.gsma.services.addon;

import java.util.List;
import java.util.ArrayList;

import java.util.Iterator;

import java.lang.IllegalArgumentException;
import java.lang.String;

import android.content.Context;
import android.util.Log;

import android.os.IBinder;
import android.os.ServiceManager;
import android.os.RemoteException; 

import android.nfc.INfcAdapterGsmaExtras;
import android.nfc.INfcAdapter;


public class NfcGsmaExtra {

    static final String TAG = "NfcGsmaExtra";

    private static NfcGsmaExtra mStaticInstance = null;
    //private Context mContext;

    static INfcAdapter sService;
    static INfcAdapterGsmaExtras sNfcGsmaExtra;

    
    //constructor
    public NfcGsmaExtra() {
        Log.d(TAG, "NfcGsmaExtra()");
        //mContext = context;
    
    
        /* get a handle to NFC service */
        IBinder b = ServiceManager.getService("nfc");
        if (b == null) {
            Log.e(TAG, "nfc service not exist");
            return;
        }
        
        sService = INfcAdapter.Stub.asInterface(b);
        
        if (sService == null) {
            Log.e(TAG, "could not retrieve NFC service");
            throw new UnsupportedOperationException();
        }
        try {
            sNfcGsmaExtra = sService.getNfcAdapterGsmaExtrasInterface();
        } catch (RemoteException e) {
            Log.e(TAG, "could not retrieve NFC GSMA EXTRA service");
            throw new UnsupportedOperationException();
        }

    
    
    }

    
    public static NfcGsmaExtra getInstance() {
        if (mStaticInstance == null) {
            mStaticInstance = new NfcGsmaExtra();
        }        
        return mStaticInstance;
    }



    public int getActiveSeValue(){
        Log.d(TAG, "getActiveSeValue()");

        int activeSe = 0;
        try {
            activeSe = sNfcGsmaExtra.getActiveSeValue();//mNfcFunction.getActiveSeValue();
        } catch (RemoteException e) {
            Log.e(TAG, "getActiveSeValue() RemoteException:"+e);
            e.printStackTrace();
        }

        return activeSe;
    }

    public void setActiveSeValue(int seValue){
        Log.d(TAG, "setActiveSeValue() seValue:"+seValue);

        try {
            sNfcGsmaExtra.setActiveSeValue(seValue);//mNfcFunction.getActiveSeValue();
        } catch (RemoteException e) {
            Log.e(TAG, "getActiveSeValue() RemoteException:"+e);
            e.printStackTrace();
        }

    }

    
    public void commitRouting(){
        Log.d(TAG, "commitRouting()");
        
        try {
            sNfcGsmaExtra.commitRouting();//mNfcFunction.getActiveSeValue();
        } catch (RemoteException e) {
            Log.e(TAG, "routeAids() RemoteException:"+e);
            e.printStackTrace();
        }         
    }

    public void routeAids(String aid){
        Log.d(TAG, "routeAids() aid:"+aid);
        
        try {
            sNfcGsmaExtra.routeAids(aid);//mNfcFunction.getActiveSeValue();
        } catch (RemoteException e) {
            Log.e(TAG, "routeAids() RemoteException:"+e);
            e.printStackTrace();
        }             
        
    }

    public boolean hasHCE(){
        Log.d(TAG, "hasHCE()");
        
        boolean result = false;
        try {
            result = sNfcGsmaExtra.isHceCapable();//mNfcFunction.getActiveSeValue();
        } catch (RemoteException e) {
            Log.e(TAG, "hasHCE() RemoteException:"+e);
            e.printStackTrace();
        }             
        return result;
    }

    public boolean enableMultiEvtTransaction(){
        Log.d(TAG, "enableMultiEvtTransaction()");
        
        boolean result = false;
        try {
            result = sNfcGsmaExtra.enableMultiEvtTransaction();//mNfcFunction.getActiveSeValue();
        } catch (RemoteException e) {
            Log.e(TAG, "enableMultiEvtTransaction() RemoteException:"+e);
            e.printStackTrace();
        }        
        return result;
    }


    public synchronized boolean[] isNFCEventAllowed(String reader,
                                                        byte[] aid,
                                                        String[] packageNames){
        Log.d(TAG, "isNFCEventAllowed() reader:"+reader+" aid:"+GsmaUtil.printNdef(aid));

        boolean[] result = null;
        
        try {
            result = sNfcGsmaExtra.isNFCEventAllowed(reader,aid,packageNames);//mNfcFunction.getActiveSeValue();
        } catch (RemoteException e) {
            Log.e(TAG, "isNFCEventAllowed() RemoteException:"+e);
            e.printStackTrace();
        }        

        return result;
    }
    

    public boolean setNfcSwpActive(int simID) {
        Log.d(TAG, "setNfcSwpActive()");
        
        boolean result = false;
        try {
            result = sNfcGsmaExtra.setNfcSwpActive(simID);
        } catch (RemoteException e) {
            Log.e(TAG, "setNfcSwpActive() RemoteException:"+e);
            e.printStackTrace();
        }        
        return result;        
    }            

}

