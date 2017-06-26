package com.android.bluetooth.map;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class BluetoothMapBroadcastProxy extends BroadcastReceiver {

    private static final String TAG = "[MAP]BluetoothMapBroadcastProxy";

    public static final String ACTION_MESSAGE_SENT = "MapBroadcastProxy.action.MESSAGE_SENT";

    public static final String ACTION_MESSAGE_DELIVERY = "MapBroadcastProxy.action.MESSAGE_DELIVERY";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Uri uri = intent.getData();

        Log.d(TAG, "[onReceive]: action = "  + action + " Uri = " + uri);

        if (action.equals(ACTION_MESSAGE_SENT)) {
            Intent intentSend = new Intent(BluetoothMapContentObserver.ACTION_MESSAGE_SENT);
            long handle = Long.parseLong(uri.getLastPathSegment());
            intentSend.putExtra("HANDLE", handle);
            context.sendBroadcast(intentSend);
        } else if (action.equals(ACTION_MESSAGE_DELIVERY)) {
            Intent intentDelivery = new Intent(BluetoothMapContentObserver.ACTION_MESSAGE_DELIVERY);
            long handle = Long.parseLong(uri.getLastPathSegment());
            intentDelivery.putExtra("HANDLE", handle);
            byte[] pdu = intent.getByteArrayExtra("pdu");
            String format = intent.getStringExtra("format");
            intentDelivery.putExtra("pdu", pdu);
            intentDelivery.putExtra("format", format);
            context.sendBroadcast(intentDelivery);
        }
    }
}
