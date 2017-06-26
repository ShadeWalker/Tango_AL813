package com.mediatek.datashaping;

import android.content.Context;
import android.content.Intent;
import android.util.Slog;

public class GateCloseState extends DataShapingState {

    private static final String TAG = "GateCloseState";

    public GateCloseState(DataShapingServiceImpl dataShapingServiceImpl, Context context) {
        super(dataShapingServiceImpl, context);
    }

    @Override
    public void onLteAccessStratumStateChanged(Intent intent) {
        // Sometimes server actively send message to client, 4g data connection will be enable.
        // This will trigger to open state.
        if (mDataShapingUtils.isLteAccessStratumConnected(intent)) {
            turnStateFromCloseToOpen();
        } else {
            // TODO idle to idle? need reclocking?
        }
    }

    @Override
    public void onMediaButtonTrigger() {
        Slog.d(TAG, "[onMediaButtonTrigger]");
        //if (mDataShapingUtils.isMusicActive()) {
            turnStateFromCloseToOpen();
        //}
    }

    @Override
    public void onAlarmManagerTrigger() {
        // TODO Based on the passed value of isForce, decide to open/close the gate.
        // default open the gate and turn the state to open.
        turnStateFromCloseToOpen();
    }

    @Override
    public void onCloseTimeExpired() {
        turnStateFromCloseToOpen();
    }

    @Override
    public void onNetworkTypeChanged(Intent intent) {
        if (!mDataShapingUtils.isNetworkTypeLte(intent)) {
            turnStateFromCloseToOpenLocked();
        }
    }

    @Override
    public void onSharedDefaultApnStateChanged(Intent intent) {
        if (mDataShapingUtils.isSharedDefaultApnEstablished(intent)) {
            turnStateFromCloseToOpenLocked();
        }
    }

    @Override
    public void onScreenStateChanged(boolean isOn) {
        if (isOn) {
            turnStateFromCloseToOpenLocked();
        }
    }

    @Override
    public void onWifiTetherStateChanged(Intent intent) {
        if (mDataShapingUtils.isWifiTetheringEnabled(intent)) {
            turnStateFromCloseToOpenLocked();
        }
    }

    @Override
    public void onUsbConnectionChanged(Intent intent) {
        if (mDataShapingUtils.isUsbConnected(intent)) {
            turnStateFromCloseToOpenLocked();
        }
    }

    @Override
    public void onBTStateChanged(Intent intent) {
        if (mDataShapingUtils.isBTStateOn(intent)) {
            turnStateFromCloseToOpenLocked();
        }
    }

    private void turnStateFromCloseToOpenLocked() {
        Slog.d(TAG, "[turnStateFromCloseToOpenLocked]");
        if (mDataShapingUtils.setLteUplinkDataTransfer(true,
                DataShapingServiceImpl.GATE_CLOSE_SAFE_TIMER)) {
            mDataShapingManager
                    .setCurrentState(DataShapingServiceImpl.DATA_SHAPING_STATE_OPEN_LOCKED);
        } else {
            Slog.d(TAG, "[turnStateFromCloseToOpenLocked] fail!");
        }
        cancelCloseTimer();
    }

    private void turnStateFromCloseToOpen() {
        Slog.d(TAG, "[turnStateFromCloseToOpen]");
        if (mDataShapingUtils.setLteUplinkDataTransfer(true,
                DataShapingServiceImpl.GATE_CLOSE_SAFE_TIMER)) {
            mDataShapingManager.setCurrentState(DataShapingServiceImpl.DATA_SHAPING_STATE_OPEN);
        } else {
            Slog.d(TAG, "[turnStateFromCloseToOpen] fail!");
        }
        cancelCloseTimer();
    }

    private void cancelCloseTimer() {
        mDataShapingManager.cancelCloseExpiredAlarm();
    }
}
