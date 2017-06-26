package com.mediatek.nfc.handover.ui;

import android.content.Context;
import android.util.Log;

import com.mediatek.nfc.handover.FileTransfer.IClient;
import com.mediatek.nfc.handover.FileTransfer.ISenderUI;

import android.content.Intent;
import com.android.nfc.R;

/**
 * Sender UI
 *
 */
public class SenderUI extends BeamPlusUi implements ISenderUI {

    /**
     * Send UI constructor
     *
     * @param context
     * @param ISender
     */
    public SenderUI(Context context, IClient ISender) {
        super(context);
    }

    // TAG
    private static final String TAG = "SenderUI";

    /**
     * On Progress update
     */
    @Override
    public void onProgressUpdate(int id, String filename, int progress,
            int total, int position) {

        Log.d(TAG, "onProgressUpdate(), ID = " + id + ", total = "
                + total + ",posistion = " + position + "," + progress + "%");

        //ex: total:5, position : 1~5 , progress : 0~100
        float floatProgress = progress;
        if (total > 1) {
            float progressUnit = 1.0f / total;
            float testF = (float) (position - 1) / total * 100;
            float testR = (progress * progressUnit);
            Log.d(TAG, "onProgressUpdate(), testF =" + testF + "%   testR=" + testR + "%");
            floatProgress =  testF + testR;
        }

        Log.d(TAG, "onProgressUpdate(), floatProgress =" + floatProgress + "%");


        updateProgressUI(id, filename, (int) floatProgress, true);
    }

    /**
     * On a beaming start to transmit
     */
    public void onPrepared(int id, int total) {

        Log.d(TAG, "onPrepared(), ID = " + id + ", total = " + total);
        Intent cancelIntent = new Intent(ISenderUI.BROADCAST_CANCEL_BY_USER);
        cancelIntent.putExtra(ISenderUI.EXTRA_ID, id);
        /**
         *  Notice:
         *  We should use request_code (2nd parameter for PendingIntenet.getBroadcast(),
         *  otherwise the PendingIntent will be the same instance (see the SDK descriptoin)
         */
        //prepareNotification(id, "Outgoing beam sending", "Cancel", PendingIntent.getBroadcast(mContext, id, cancelIntent, 0), true);

        String sw = mContext.getString(R.string.beam_outgoing);
        //String sc = mContext.getString(R.string.cancel);
        prepareNotification(id, sw, null, null, true);


    }

    /**
     * On Transmit error
     */
    public void onError(int id, int total, int success) {
        Log.d(TAG, "onError(), ID = " + id + ", total = "
                + total + ",success = " + success);

        //completeNotification(id, success + "/" + total + " files are successfully transmited", null);

        completeNotification(id, mContext.getString(R.string.beam_failed), null);
    }

    /**
     * On file transmit complete
     */
    public void onCompleted(int id, int total, int success) {
        Log.d(TAG, "onComplete, ID = " + id + ", total = " + total
                + ",success = " + success);

        completeNotification(id, mContext.getString(R.string.beam_complete), null);
        //completeNotification(id, success + " files are successfully transmited", null);
    }

    public void onCaneceled(int id, int total, int success) {
        Log.d(TAG, "onCanecel, ID = " + id + ", total = " + total
                + ",success = " + success);

        //completeNotification(id, success + "/" + total + " files are successfully transmited", null);

        completeNotification(id, mContext.getString(R.string.beam_canceled), null);
    }

} // end of SenderUI

