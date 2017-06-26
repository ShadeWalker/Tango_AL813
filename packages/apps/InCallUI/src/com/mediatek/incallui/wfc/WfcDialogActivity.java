package com.mediatek.incallui.wfc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.CheckBox;

import android.view.WindowManager;
import android.view.LayoutInflater;
import android.view.View;

import com.android.incallui.Log;
import com.android.incallui.R;


///M: WFC <To Display WFC related dialogs>
public class WfcDialogActivity extends Activity {
    private static final String TAG = "WfcDialogActivity";
    private AlertDialog mGeneralErrorDialog;
    private ToneGenerator mToneGenerator;
    private static final int DIAL_TONE_STREAM_TYPE = AudioManager.STREAM_VOICE_CALL;
    private static final int TONE_RELATIVE_VOLUME = 80;
    public static int sCount = 0;
    public static boolean sIsShowing = false;
    public static final String WFC_ERROR_LABEL = "label";
    public static final String WFC_ERROR_DECRIPTION = "description";
    private CheckBox checkBox ;

    public static final String SHOW_CONGRATS_POPUP = "show congrats popup";
    public static final String SHOW_WFC_CALL_ERROR_POPUP = "show wfc call error popup";
    public static final String SHOW_WFC_ROVE_OUT_POPUP = "show wfc rove out popup";
    public static final String ACTION_IMS_SETTING = "android.settings.WIFI_SETTINGS";
    private static final String KEY_IS_FIRST_WIFI_CALL = "key_first_wifi_call";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new ColorDrawable(0));
    }

    /**
     *when some other activity covered on this dialog activity
     *we simply finish it to avoid the dialog flick.
     */
    @Override
    protected void onPause() {
       super.onPause();
       finish();
       stopTone();
    }

    @Override
    public void finish() {
        super.finish();
        // Don't show the return to previous task animation to avoid showing a black screen.
        // Just dismiss the dialog and undim the previous activity immediately.
        overridePendingTransition(0, 0);
        sIsShowing = false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGeneralErrorDialog == null) {
            showErrorDialog();
            sIsShowing = true;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGeneralErrorDialog != null) {
            mGeneralErrorDialog.dismiss();
            mGeneralErrorDialog = null;
        }
    }
    private void showErrorDialog() {
        boolean showWfcCongratsPopup = getIntent().getBooleanExtra(SHOW_CONGRATS_POPUP, false);
        if (showWfcCongratsPopup) {
            showCongratsPopup();
            return;
        }
        boolean showWfcRoveOutPopup = getIntent().getBooleanExtra(SHOW_WFC_ROVE_OUT_POPUP, false);
        if (showWfcRoveOutPopup) {
            showWfcRoveOutError();
            return;
        }
        boolean showWfcCallErrorPopup = getIntent().getBooleanExtra(SHOW_WFC_CALL_ERROR_POPUP, false);
        if (showWfcCallErrorPopup) {
            showWfcErrorDialog();
            return;
        }
    }

    private void showWfcRoveOutError() {
        if (mGeneralErrorDialog == null) {
            mGeneralErrorDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.call_drop)
                    .setMessage(R.string.call_drop_message)
                    .setPositiveButton(R.string.ok, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            onDialogDismissed();
                            finish();
                        }
                    })
                    .setOnCancelListener(new OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            onDialogDismissed();
                            finish();
                        }
                     })
                    .create();
            mGeneralErrorDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            Log.d(TAG, "[WFC]in showWfcRoveOutError sCount" + sCount);
            mGeneralErrorDialog.show();
            playTone();
            sCount++;
        }
    }

    private void showCongratsPopup() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        LayoutInflater adbInflater = LayoutInflater.from(this);
        View checkBoxView = adbInflater.inflate(R.layout.mtk_first_wifi_call_ends, null);
        alert.setTitle(R.string.wifi_title);
        alert.setView(checkBoxView);
        checkBox = (CheckBox) checkBoxView.findViewById(R.id.checkbox);
        alert.setPositiveButton(R.string.ok, new OnClickListener() {
             @Override
             public void onClick(DialogInterface dialog, int which) {
                 Log.i(TAG, "[WFC] in onClick showCongratsPopup checkBox.isChecked()"  + checkBox.isChecked());
                 if (checkBox.isChecked()) {
                     Log.i(TAG, "[WFC]showCongratsPopup checked True");
                     SharedPreferences  pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                     SharedPreferences.Editor editor = pref.edit();
                     editor.putBoolean(KEY_IS_FIRST_WIFI_CALL, false);
                     editor.commit();
                 }
                 onDialogDismissed();
                 finish();
              }
         });
         alert.setOnCancelListener(new OnCancelListener() {
             @Override
             public void onCancel(DialogInterface dialog) {
                 onDialogDismissed();
                 finish();
             }
         });
         mGeneralErrorDialog = alert.create();
         mGeneralErrorDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
         Log.i(TAG, "[WFC]showCongratsPopup mGeneralErrorDialog.show");
         mGeneralErrorDialog.show();

    }

    /* For Showing wifi related popups on wfc call disconnects with error*/
    private void showWfcErrorDialog() {
        int id = 0;
        CharSequence label = getIntent().getCharSequenceExtra(WFC_ERROR_LABEL);
        CharSequence description = getIntent().getCharSequenceExtra(WFC_ERROR_DECRIPTION);
        CharSequence wfcText = this.getResources().getString(R.string.wfc_wifi_call_drop_summary);
        if (wfcText.equals(description)) {
            id = R.string.no_thanks;
        } else {
           id = R.string.close;         
        }
        Log.i(TAG, "showWfcErrorDialog " + label);
        Log.i(TAG, "showWfcErrorDialog " + description);
        mGeneralErrorDialog = new AlertDialog.Builder(this)
                .setTitle(label)
                .setMessage(description)
                .setPositiveButton(R.string.view_networks, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        enableWifi();
                 }})
                  .setNegativeButton(id, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onDialogDismissed();
                        finish();
                    } })
                .create();
        mGeneralErrorDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        mGeneralErrorDialog.show();
     }

    private void enableWifi() {
        Intent intent = new Intent();
        intent.setAction(ACTION_IMS_SETTING);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        this.startActivity(intent);
        finish();
    }
    private void playTone() {
        if (mToneGenerator == null) {
            try {
                mToneGenerator = new ToneGenerator(DIAL_TONE_STREAM_TYPE, TONE_RELATIVE_VOLUME);
            } catch (RuntimeException e) {
                Log.w(TAG, "[WFC]Exception caught while creating local tone generator: " + e);
                mToneGenerator = null;
                return;
            }
        }
        mToneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, -1);
    }

    private void stopTone() {
        if (mToneGenerator != null) {
            mToneGenerator.stopTone();
            mToneGenerator.release();
            mToneGenerator = null;
            Log.i(this, "[WFC]onPause tonegenrator stopped ");
        }
    }
    private  void onDialogDismissed() {
        if (mGeneralErrorDialog != null) {
            mGeneralErrorDialog.dismiss();
            mGeneralErrorDialog = null;
        }
    }
}
