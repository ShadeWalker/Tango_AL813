/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.nfcgsma_extras;

import java.util.HashMap;

import android.content.Context;

import android.nfc.NfcAdapter;
import android.os.RemoteException;
import android.util.Log;

import android.nfc.INfcAdapterGsmaExtras;


/**
 * Provides additional methods on an {@link NfcAdapter} for GSMA interface
 *
 * There is a 1-1 relationship between an {@link NfcAdapterGsmaExtras} object and
 * a {@link NfcAdapter} object.
 */
public final class NfcAdapterGsmaExtras {
    private static final String TAG = "NfcAdapterGsmaExtras";

    public static final int SIM_1 = 1;
    public static final int SIM_2 = 2;
    public static final int SIM_3 = 3;

    // protected by NfcAdapterExtras.class, and final after first construction,
    // except for attemptDeadServiceRecovery() when NFC crashes - we accept a
    // best effort recovery
    private static INfcAdapterGsmaExtras sService;
    //private static final CardEmulationRoute ROUTE_OFF =
    //        new CardEmulationRoute(CardEmulationRoute.ROUTE_OFF, null);

    // contents protected by NfcAdapterExtras.class
    private static final HashMap<NfcAdapter, NfcAdapterGsmaExtras> sNfcGsmaExtras = new HashMap();

    //private final NfcExecutionEnvironment mEmbeddedEe;
    //private final CardEmulationRoute mRouteOnWhenScreenOn;

    private final NfcAdapter mAdapter;
    final String mPackageName;

    /** get service handles */
    private static void initService(NfcAdapter adapter) {
        final INfcAdapterGsmaExtras service = adapter.getNfcAdapterGsmaExtrasInterface();
        if (service != null) {
            // Leave stale rather than receive a null value.
            sService = service;
        }
    }

    /**
     * Get the {@link NfcAdapterGsmaExtras} for the given {@link NfcAdapter}.
     *
     * <p class="note">
     * Requires the {@link android.Manifest.permission#WRITE_SECURE_SETTINGS} permission.
     *
     * @param adapter a {@link NfcAdapter}, must not be null
     * @return the {@link NfcAdapterGsmaExtras} object for the given {@link NfcAdapter}
     */
    public static NfcAdapterGsmaExtras get(NfcAdapter adapter) {
        Context context = adapter.getContext();
        if (context == null) {
            throw new UnsupportedOperationException(
                    "You must pass a context to your NfcAdapter to use the NFC extras APIs");
        }

        synchronized (NfcAdapterGsmaExtras.class) {
            if (sService == null) {
                initService(adapter);
            }
            NfcAdapterGsmaExtras extras = sNfcGsmaExtras.get(adapter);
            if (extras == null) {
                extras = new NfcAdapterGsmaExtras(adapter);
                sNfcGsmaExtras.put(adapter,  extras);
            }
            return extras;
        }
    }

    private NfcAdapterGsmaExtras(NfcAdapter adapter) {
        mAdapter = adapter;
        mPackageName = adapter.getContext().getPackageName();

    }


    /**
     * NFC service dead - attempt best effort recovery
     */
    void attemptDeadServiceRecovery(Exception e) {
        Log.e(TAG, "NFC Adapter Extras dead - attempting to recover");
        mAdapter.attemptDeadServiceRecovery(e);
        initService(mAdapter);
    }

    INfcAdapterGsmaExtras getService() {
        return sService;
    }







    /**
     * Get the routing state of this NFC EE.
     *
     * <p class="note">
     * Requires the {@link android.Manifest.permission#WRITE_SECURE_SETTINGS} permission.
     */
    public int getActiveSeValue(){
    
    Log.d(TAG, "getActiveSeValue()");
        int result = 0;
        try {
            result = sService.getActiveSeValue();
        } catch (RemoteException e) {
            attemptDeadServiceRecovery(e);
        }

        return result;
    }

    public void SetActiveSeValue(int seValue){
    
    Log.d(TAG, "SetActiveSeValue() seValue:"+seValue);
        try {
            sService.setActiveSeValue(seValue);
        } catch (RemoteException e) {
            attemptDeadServiceRecovery(e);
        }

    }

    
    public void commitRouting(){
        Log.d(TAG, "commitRouting()");
        
        try {
            sService.commitRouting();
        } catch (RemoteException e) {
            attemptDeadServiceRecovery(e);
        }
    }

    public void routeAids(String aid){
        Log.d(TAG, "routeAids() aid:"+aid);
        
        try {
            sService.routeAids(aid);
        } catch (RemoteException e) {
            attemptDeadServiceRecovery(e);
        }
    }

    public boolean enableMultiEvtTransaction(){
        Log.d(TAG, "enableMultiEvtTransaction()");
        
        boolean result = false;
        try {
            result = sService.enableMultiEvtTransaction();
        } catch (RemoteException e) {
            attemptDeadServiceRecovery(e);
        }        
        return result;
    }


    public synchronized boolean[] isNFCEventAllowed(String reader,
                                                        byte[] aid,
                                                        String[] packageNames){
        Log.d(TAG, "isNFCEventAllowed()");

        try {
            boolean[] res  = sService.isNFCEventAllowed(reader,aid,packageNames);
        } catch (RemoteException e) {
            attemptDeadServiceRecovery(e);
        }
        return null;
        
    }
    
    public boolean setNfcSwpActive(int simID) {
        Log.d(TAG, "enableMultiEvtTransaction()");
        
        boolean result = false;
        try {
            result = sService.setNfcSwpActive(simID);
        } catch (RemoteException e) {
            attemptDeadServiceRecovery(e);
        }        
        return result;        
        
    }

}
