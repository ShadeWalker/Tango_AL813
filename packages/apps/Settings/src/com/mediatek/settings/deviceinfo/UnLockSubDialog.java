package com.mediatek.settings.deviceinfo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.telephony.SubscriptionManager;

import com.mediatek.internal.telephony.CellConnMgr;
import com.mediatek.settings.sim.Log;

import java.util.ArrayList;
//add by Libeibei at 20160908 for HQ02053845 begain
import android.content.Intent;
import com.android.settings.IccLockSettings;
//add by Libeibei at 20160908 for HQ02053845 end
/**
 * show dialog to unlock SIM pin.
 */
public class UnLockSubDialog extends DialogFragment {

    private static final String EXTRA_SUBID = "subid";
    private static final String EXTRA_STATE = "state";
    private static final String TAG = "UnLockSubDialog";
    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private int mState = CellConnMgr.STATE_READY;
    private CellConnMgr mCellConnMgr;
    private static final int TITLE = 0;
    private static final int CONTENT = 1;
    private static final int POSITIVE_BUTTON = 2;
    private static final int NEGATIVE_BUTTON = 3;

    private static int getCurrentStateForSubId(Context context, int subId) {
        CellConnMgr cellConnMgr = new CellConnMgr(context);
        int state = cellConnMgr.getCurrentState(subId, CellConnMgr.STATE_SIM_LOCKED);
        Log.d(TAG, "getCurrentStateForSubId(), subId: " + subId + ", state: " + state);
        return state;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mCellConnMgr = new CellConnMgr(getActivity());
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        mState = getArguments().getInt(EXTRA_STATE, CellConnMgr.STATE_READY);
        mSubId = getArguments().getInt(EXTRA_SUBID, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        ArrayList<String> simStatusStrings = mCellConnMgr.getStringUsingState(mSubId, mState);
        builder.setTitle(simStatusStrings.get(TITLE));
        builder.setMessage(simStatusStrings.get(CONTENT));
        builder.setPositiveButton(simStatusStrings.get(POSITIVE_BUTTON),
                new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.d(TAG, "onClick(),mSubId: " + mSubId + ", mState: " + mState);
                        finishActivity();
                        mCellConnMgr.handleRequest(mSubId, mState);
                    }
                });
        builder.setNegativeButton(simStatusStrings.get(NEGATIVE_BUTTON), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Since not to unlock (radio on) etc, current activity finish
					//HQ_jiangchao modify for HQ01320051 at 20150917 
                //finishActivity();
		//add by Libeibei at 20160908 for HQ02053845 begain
		Intent intent=new Intent();
		intent.setAction(IccLockSettings.ACTION_CANCLE_ACTIVATION_SIM);
		getActivity().sendBroadcast(intent);
		//add by Libeibei at 20160908 for HQ02053845 end		
            }
        });
        return builder.create();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        Log.d(TAG,"onCancel");
        finishActivity();
    }

    private void finishActivity() {
        Log.d(TAG,"handleDialogDismiss");
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    /**
     * check whether show pin lock dialog.
     * @param activity activity
     * @param subId subId
     */
    public static void showDialog(Activity activity, int subId) {
        int slotId = SubscriptionManager.getSlotId(subId);
        if (SubscriptionManager.isValidSlotId(slotId)) {
            int state = getCurrentStateForSubId(activity, subId);
            Log.d(TAG, "showDialog(), subId:" + subId + ", state: " + state);
            if (state != CellConnMgr.STATE_READY) {
                final Bundle args = new Bundle();
                args.putInt(EXTRA_STATE, state);
                args.putInt(EXTRA_SUBID, subId);
                final UnLockSubDialog dialog = new UnLockSubDialog();
                dialog.setArguments(args);
                dialog.show(activity.getFragmentManager(),
                        activity.getClass().getSimpleName());
            }
        } else {
            Log.d(TAG, "slotId is invalid! slotId: " + slotId);
        }
    }
}
