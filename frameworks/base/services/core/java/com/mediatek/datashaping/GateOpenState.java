package com.mediatek.datashaping;

import android.content.Context;
import android.content.Intent;
import android.util.Slog;

public class GateOpenState extends DataShapingState {

    private static final String TAG = "GateOpenState";

    public GateOpenState(DataShapingServiceImpl dataShapingServiceImpl, Context context){
        super(dataShapingServiceImpl, context);
    }

    @Override
    public void onLteAccessStratumStateChanged(Intent intent) {
        if (!mDataShapingUtils.isLteAccessStratumConnected(intent)) {
            turnStateFromOpenToClose();
        }
    }

    @Override
    public void onNetworkTypeChanged(Intent intent) {
        if (!mDataShapingUtils.isNetworkTypeLte(intent)) {
            turnStateFromOpenToOpenLocked();
        }
    }

    @Override
    public void onSharedDefaultApnStateChanged(Intent intent) {
        if (mDataShapingUtils.isSharedDefaultApnEstablished(intent)) {
            turnStateFromOpenToOpenLocked();
        }
    }

    @Override
    public void onScreenStateChanged(boolean isOn) {
        if (isOn) {
            turnStateFromOpenToOpenLocked();
        }
    }

    @Override
    public void onWifiTetherStateChanged(Intent intent) {
        if (mDataShapingUtils.isWifiTetheringEnabled(intent)) {
            turnStateFromOpenToOpenLocked();
        }
    }

    @Override
    public void onUsbConnectionChanged(Intent intent) {
        if (mDataShapingUtils.isUsbConnected(intent)) {
            turnStateFromOpenToOpenLocked();
        }
    }

    @Override
    public void onBTStateChanged(Intent intent) {
        if (mDataShapingUtils.isBTStateOn(intent)) {
            turnStateFromOpenToOpenLocked();
        }
    }

    private void turnStateFromOpenToOpenLocked() {
        mDataShapingManager.setCurrentState(DataShapingServiceImpl.DATA_SHAPING_STATE_OPEN_LOCKED);
    }

    private void turnStateFromOpenToClose() {
        // If music active, will not close gate.
        if (mDataShapingUtils.isMusicActive()) {
            mDataShapingUtils.setClosingDelayForMusic(true);
            Slog.d(TAG, "[turnStateFromOpenToClose] music active, so still in open state!");
            return;
        }
        // If music is not active, but getClosingDelayForMusic() is true,
        // will write down the start time of this delay and still in open state.
        if (mDataShapingUtils.getClosingDelayForMusic()) {
            mDataShapingUtils.setClosingDelayStartTime(System.currentTimeMillis());
            mDataShapingUtils.setClosingDelayForMusic(false);
            Slog.d(TAG, "[turnStateFromOpenToClose] mIsClosingDelayForMusic is true, " +
                    "so still in open state!");
            return;
        }
        // If currentTime - delayStartTime < buffer, will still in open state.
        // Every normal close (exclude music related) will always enter this if else sentence.
        if (System.currentTimeMillis() - mDataShapingUtils.getClosingDelayStartTime()
                < DataShapingUtils.CLOSING_DELAY_BUFFER_FOR_MUSIC) {
            Slog.d(TAG, "[turnStateFromOpenToClose] close delay < buffer time, " +
                    "so still in open state!");
            return;
        } else {
            mDataShapingUtils.setClosingDelayStartTime(0);
        }

        if (! mDataShapingManager.registerListener()) {
            Slog.d(TAG, "[turnStateFromOpenToClose] registerListener Failed " +
                    "so still in open state!");
            return;
        }
        if (mDataShapingUtils.setLteUplinkDataTransfer(false,
                DataShapingServiceImpl.GATE_CLOSE_SAFE_TIMER)) {
            mDataShapingManager.setCurrentState(DataShapingServiceImpl.DATA_SHAPING_STATE_CLOSE);
            mDataShapingManager.startCloseExpiredAlarm();
        } else {
            // TODO error handle
            Slog.d(TAG, "[turnStateFromOpenToClose] fail!");
        }
    }
}
