package com.mediatek.datashaping;

import android.content.Context;
import android.content.Intent;

public abstract class DataShapingState {

    public DataShapingUtils mDataShapingUtils;
    public DataShapingServiceImpl mDataShapingManager;

    public DataShapingState(DataShapingServiceImpl dataShapingServiceImpl, Context context) {
        mDataShapingManager = dataShapingServiceImpl;
        mDataShapingUtils = DataShapingUtils.getInstance(context);
    }

    public void onLteAccessStratumStateChanged(Intent intent) {
    }

    public void onNetworkTypeChanged(Intent intent) {
    }

    public void onAlarmManagerTrigger() {
    }

    public void onCloseTimeExpired() {
    }

    public void onScreenStateChanged(boolean isOn) {
    }

    public void onSharedDefaultApnStateChanged(Intent intent) {
    }

    public void onUsbTetherStateChanged() {
    }

    public void onWifiTetherStateChanged(Intent intent) {
    }

    public void onUsbConnectionChanged(Intent intent) {
    }

    public void onMediaButtonTrigger() {
    }
    /**
     * check if BT is on or off.
     * @param intent : Message info to get extra BluetoothAdapter.EXTRA_STATE
     */
    public void onBTStateChanged(Intent intent) {
    }
}
