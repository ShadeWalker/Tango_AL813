package com.mediatek.nfc.handover;



//import com.mediatek.nfc.handover.IWifiP2pProxy.WifiP2pProxyListener;

import android.content.Context;
//import android.media.MediaScannerConnection;
import android.util.Log;



public class WifiDisplayProxy implements IWifiDisplayProxy {

    static final String TAG = "WifiDisplayProxy";
    static final boolean DBG = true;


    public WifiDisplayProxy(Context context) {
        Log.i(TAG, "  WifiDisplayProxy ");
    }
    public int getRtspPortNumber() {
        Log.i(TAG, "  getRtspPortNumber :: 7236");
        return 7236;
    }


    /*
    public IWifiP2pProxy getInstance(){
        return new testWifiProxy(null);
    }
    */



}
