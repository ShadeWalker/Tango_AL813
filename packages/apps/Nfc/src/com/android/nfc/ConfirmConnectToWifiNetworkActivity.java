package com.android.nfc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Toast;


/// M: @{
import android.util.Log;
/// }

public class ConfirmConnectToWifiNetworkActivity extends Activity
        implements View.OnClickListener, DialogInterface.OnDismissListener {

    /// M: @{
    static final String TAG = "NFC.ConfirmConnectToWifiNetworkActivity";
    String printableSsid;
    /// }

    public static final int ENABLE_WIFI_TIMEOUT_MILLIS = 5000;
    private WifiConfiguration mCurrentWifiConfiguration;
    private AlertDialog mAlertDialog;
    private boolean mEnableWifiInProgress;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate() " );


        Intent intent = getIntent();
        mCurrentWifiConfiguration =
                intent.getParcelableExtra(NfcWifiProtectedSetup.EXTRA_WIFI_CONFIG);

        /// M: @{
        printableSsid = mCurrentWifiConfiguration.getPrintableSsid();
        /// }
        mAlertDialog = new AlertDialog.Builder(this,  AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                .setTitle(R.string.title_connect_to_network)
                .setMessage(
                        String.format(getResources().getString(R.string.prompt_connect_to_network),
                        printableSsid))
                .setOnDismissListener(this)
                .setNegativeButton(com.android.internal.R.string.cancel, null)
                .setPositiveButton(R.string.wifi_connect, null)
                .create();

        mEnableWifiInProgress = false;
        mHandler = new Handler();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(mBroadcastReceiver, intentFilter);

        mAlertDialog.show();

        super.onCreate(savedInstanceState);

        mAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(this);
    }


    @Override
    public void onClick(View v) {
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        Log.d(TAG, "onClick() " );

        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
            mEnableWifiInProgress = true;
            Log.d(TAG, "  set mEnableWifiInProgress to true");

            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                
                Log.d(TAG, "  !!!! enable wifi timeout Runnable!!!!");
                    if (getAndClearEnableWifiInProgress()) {
                        showFailToast();
                        Log.d(TAG, "  finish()");
                        ConfirmConnectToWifiNetworkActivity.this.finish();
                    }
                }
            }, ENABLE_WIFI_TIMEOUT_MILLIS);

        } else {
            doConnect(wifiManager);
        }
        Log.d(TAG, "!!!!!!!!!!  AlertDialog.dismiss() !!!!!!!!!");

        mAlertDialog.dismiss();
    }

    private void doConnect(WifiManager wifiManager) {
        
        Log.d(TAG, "  doConnect(.) wifiManager.addNetwork(.)");

        int networkId = wifiManager.addNetwork(mCurrentWifiConfiguration);

        Log.d(TAG, "  return  networkId:"+networkId);

        if (networkId < 0) {
            showFailToast();
            /// M: @{
            Log.d(TAG, "  finish()");
            finish();
            /// }
        } else {

            Log.d(TAG, "  wifiManager.connect(..)  networkId:"+networkId);

            wifiManager.connect(networkId,
                    new WifiManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            /// M: @{ add finifsh()
                            Log.d(TAG, " onSuccess() Toast show connected ,finish()");
                            String connectingString = String.format(getResources().getString(R.string.connecting_peripheral),printableSsid);
                            Log.d(TAG, " connectingString:"+connectingString);
                            Toast.makeText(ConfirmConnectToWifiNetworkActivity.this,
                                    connectingString, Toast.LENGTH_SHORT).show();
                            finish();
                            /// }
                        }

                        @Override
                        public void onFailure(int reason) {
                            showFailToast();
                            /// M: @{
                            Log.d(TAG, " onFailure() finish()");
                            finish();
                            /// }
                        }
                    });
        }
    }


    private void showFailToast() {
        Toast.makeText(ConfirmConnectToWifiNetworkActivity.this,
                R.string.status_unable_to_connect, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
    
    Log.d(TAG, "onDismiss() mEnableWifiInProgress:"+mEnableWifiInProgress );
        if (!mEnableWifiInProgress) {
            
            Log.d(TAG, "  finish()");
            finish();
        }
    }


    @Override
    protected void onDestroy() {
    
        Log.d(TAG, "onDestroy() " );

        /// M: @{
        boolean isDialogShow = mAlertDialog.isShowing();
        Log.d(TAG, "mAlertDialog.isShowing():" + isDialogShow);

        if(mAlertDialog!=null && isDialogShow){
            Log.d(TAG, "mAlertDialog.isShowing() == true, dismiss Dialog");
            
            Log.d(TAG, "!!!!!!!!!!  AlertDialog.dismiss() !!!!!!!!!");
            mAlertDialog.dismiss();
        }
        /// }
        
        
        ConfirmConnectToWifiNetworkActivity.this.unregisterReceiver(mBroadcastReceiver);
        super.onDestroy();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            Log.d(TAG, "  onReceive() action:"+action);
            if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
                Log.d(TAG, "  wifiState:"+wifiState);
                if (mCurrentWifiConfiguration != null
                        && wifiState == WifiManager.WIFI_STATE_ENABLED) {
                    if (getAndClearEnableWifiInProgress()) {
                        doConnect(
                                (WifiManager) ConfirmConnectToWifiNetworkActivity.this
                                        .getSystemService(Context.WIFI_SERVICE));
                    }
                }
            }
        }
    };

    private boolean getAndClearEnableWifiInProgress() {
        boolean enableWifiInProgress;
        Log.d(TAG, "getAndClearEnableWifiInProgress()  mEnableWifiInProgress:"+mEnableWifiInProgress );
        Log.d(TAG, "  set mEnableWifiInProgress to false");

        synchronized (this)  {
            enableWifiInProgress = mEnableWifiInProgress;
            mEnableWifiInProgress = false;
        }

        return enableWifiInProgress;
    }
}
