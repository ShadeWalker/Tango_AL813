package com.android.server.telecom;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.PreciseCallState;
import android.telephony.TelephonyManager;
import android.widget.Toast;

public class PreciseReceiver extends BroadcastReceiver{

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (TelephonyManager.ACTION_PRECISE_CALL_STATE_CHANGED.equals(action)) {
            int callState = intent.getIntExtra(TelephonyManager.EXTRA_RINGING_CALL_STATE,
                    PreciseCallState.PRECISE_CALL_STATE_NOT_VALID);
            if (callState == PreciseCallState.PRECISE_CALL_STATE_HOLDING) {
                Toast.makeText(context, R.string.mt_code_call_on_hold, Toast.LENGTH_SHORT).show();
            } else if (callState == PreciseCallState.PRECISE_CALL_STATE_ACTIVE) {
                Toast.makeText(context, R.string.mt_code_call_retrieved, Toast.LENGTH_SHORT).show();
            }
        }
    }

}
