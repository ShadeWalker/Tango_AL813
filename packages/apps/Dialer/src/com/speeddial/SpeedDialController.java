package com.speeddial;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

// / Modified by guofeiyao
import com.android.dialer.R;
import com.speeddial.provider.SpeedDial;
import com.speeddial.SpeedDialActivity;
// / End

// / Added by guofeiyao
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;

import java.util.List;
// / End
    
public class SpeedDialController {
    private static final String TAG = "SpeedDialController";
    private static SpeedDialController sMe;
    private Context mfContext;
    private Context mContext;

    // / Modified by guofeiyao
    Uri uri = Uri.parse("content://hq_speed_dial/numbers");
    // / End

    private void SpeedDialController() {
    }

    public static SpeedDialController getInstance() {
        if (sMe == null) {
            sMe = new SpeedDialController();
        }
        return sMe;
    }

    public void handleKeyLongProcess(Activity activity, Context cnx, int key) {
        mfContext = activity;
        mContext = cnx;
        Cursor cursor = mContext.getContentResolver().query(uri, new String[] {"_id", "number"}, "_id" + " = " + key, null, null);

        String number = "";
        if (cursor!= null) {
            if (cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex("number");
                if (columnIndex != -1) {
                    number = cursor.getString(columnIndex);
                }
            }
            cursor.close();
        }
        Log.i(TAG, "handleKeyLongProcess, key = " + key);
        Log.i(TAG, "handleKeyLongProcess, number = " + number);
        if (TextUtils.isEmpty(number)) {
            showSpeedDialConfirmDialog();
        } else {
            // / Modified by guofeiyao
            getSubInfoList();
            if (2==mSubCount) {
                showDialog(number);
            }else {

                final Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, Uri.fromParts("tel",
                        number, null));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mfContext.startActivity(intent);

            }
            // / End
        }
    }

    // / Added by guofeiyao
    private AlertDialog mAlertDialog;
    private List<SubscriptionInfo> mSubInfoList;
    private int mSubCount;

    private void getSubInfoList() {
        mSubInfoList = SubscriptionManager.from(mContext).getActiveSubscriptionInfoList();
        mSubCount = (mSubInfoList != null && !mSubInfoList.isEmpty()) ? mSubInfoList.size() : 0;
    }

    private void showDialog(final String number){
        final AlertDialog alertDialog = new AlertDialog.Builder(mfContext).create();
        mAlertDialog = alertDialog;

        alertDialog.show();
        Window window = alertDialog.getWindow();
        window.setContentView(R.layout.call_alert_dialog);
        String sim1 = mSubInfoList.get(0).getDisplayName().toString();
        Button btnOne = (Button)window.findViewById(R.id.btn_sim_one);
        btnOne.setText(sim1);
        btnOne.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                // TODO Auto-generated method stub
//                Intent intent = CallUtil.getCallIntent(number,
//                        (mContext instanceof PeopleActivity?
//                                ((PeopleActivity) mContext).getDialerPlugIn().getCallOrigin() : null));
//                intent.putExtra("slot_id",0);
//                DialerUtils.startActivityWithErrorToast(mContext, intent);
                final Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, Uri.fromParts("tel",
                        number, null));
                intent.putExtra("slot_id",0);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mfContext.startActivity(intent);
            }
        });

        String sim2 = mSubInfoList.get(1).getDisplayName().toString();
        Button btnTwo = (Button)window.findViewById(R.id.btn_sim_two);
        btnTwo.setText(sim2);
        btnTwo.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                // TODO Auto-generated method stub
//                Intent intent = CallUtil.getCallIntent(number,
//                        (mContext instanceof PeopleActivity ?
//                                ((PeopleActivity) mContext).getDialerPlugIn().getCallOrigin() : null));
//                intent.putExtra("slot_id", 1);
//                DialerUtils.startActivityWithErrorToast(mContext, intent);
                final Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, Uri.fromParts("tel",
                        number, null));
                intent.putExtra("slot_id",1);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mfContext.startActivity(intent);
            }
        });
    }

    public void onPause(){
        if (null != mAlertDialog && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
    }
    // / End

    public void enterSpeedDial(Context fcnx) {
        Log.i(TAG, "enterSpeedDial");

        // / Modified by guofeiyao
        /*
        final Intent intent = new Intent();
        intent.setClassName("com.mediatek.op01.plugin", "com.mediatek.dialer.plugin.speeddial.SpeedDialActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        fcnx.startActivity(intent);
        */
        SpeedDialActivity.actionStart(fcnx);
    }

    public void showSpeedDialConfirmDialog() {
        Log.i(TAG, "showSpeedDialConfirmDialog");
        AlertDialog confirmDialog = new AlertDialog.Builder(mfContext)
            .setTitle(mContext.getString(R.string.call_speed_dial))
            .setMessage(mContext.getString(R.string.dialog_no_speed_dial_number_message))
            .setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            enterSpeedDial(mfContext);
                        }
                }).setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                        }
            }).create();

        // / Added by guofeiyao
        mAlertDialog = confirmDialog;
        // / End

        confirmDialog.show();
    }
}


