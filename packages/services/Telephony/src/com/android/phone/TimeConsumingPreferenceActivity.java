package com.android.phone;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;

import com.android.internal.telephony.CommandException;
import com.mediatek.phone.ext.ExtensionManager;
import com.mediatek.phone.TimeConsumingPreferenceListener;

import java.util.ArrayList;

public class TimeConsumingPreferenceActivity extends PreferenceActivity
                        implements TimeConsumingPreferenceListener,
                        DialogInterface.OnCancelListener {
    private static final String LOG_TAG = "TimeConsumingPreferenceActivity";
    private final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    private class DismissOnClickListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
        }
    }
    private class DismissAndFinishOnClickListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            finish();
        }
    }
    private final DialogInterface.OnClickListener mDismiss = new DismissOnClickListener();
    private final DialogInterface.OnClickListener mDismissAndFinish
            = new DismissAndFinishOnClickListener();

    private static final int BUSY_READING_DIALOG = 100;
    private static final int BUSY_SAVING_DIALOG = 200;

    public static final int EXCEPTION_ERROR = 300;
    public static final int RESPONSE_ERROR = 400;
    public static final int RADIO_OFF_ERROR = 500;
    public static final int FDN_CHECK_FAILURE = 600;
    /// M: add for call barring exception
    public static final int PASSWORD_ERROR = 700;
    public static final int FDN_FAIL = 800;

    private final ArrayList<String> mBusyList = new ArrayList<String>();

    protected boolean mIsForeground = false;

    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == BUSY_READING_DIALOG || id == BUSY_SAVING_DIALOG) {
            ProgressDialog dialog = new ProgressDialog(this);
            /// M: ALPS01767285 Add customer dialog title @{
            if (!TextUtils.isEmpty(mTitle)) {
                Log.d(LOG_TAG,"onCreateDialog, use customer's dialog title:" + mTitle);
                dialog.setTitle(mTitle);
            } else {
                dialog.setTitle(getText(R.string.updating_title));
            }
            /// @}
            dialog.setIndeterminate(true);
            dialog.setCanceledOnTouchOutside(false);

            switch(id) {
                case BUSY_READING_DIALOG:
                    dialog.setCancelable(true);
                    dialog.setOnCancelListener(this);
                    dialog.setMessage(getText(R.string.reading_settings));
                    return dialog;
                case BUSY_SAVING_DIALOG:
                    dialog.setCancelable(false);
                    dialog.setMessage(getText(R.string.updating_settings));
                    return dialog;
            }
            return null;
        }

        if (id == RESPONSE_ERROR || id == RADIO_OFF_ERROR || id == EXCEPTION_ERROR
                || id == FDN_CHECK_FAILURE || id == PASSWORD_ERROR || id == FDN_FAIL) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            int msgId;
            int titleId = R.string.error_updating_title;

            switch (id) {
                case PASSWORD_ERROR:
                    msgId = com.android.internal.R.string.passwordIncorrect;
                    builder.setNeutralButton(R.string.close_dialog, mDismiss);
                    break;
                case FDN_FAIL:
                    msgId = com.mediatek.R.string.fdnFailMmi;
                    builder.setNeutralButton(R.string.close_dialog, mDismiss);
                    break;
                case RESPONSE_ERROR:
                    msgId = R.string.response_error;
                    builder.setPositiveButton(R.string.close_dialog, mDismiss);

					// / Added by guofeiyao 2015/12/14
					// For werid need to not show the error dialog
					if ( android.os.SystemProperties.getInt("ro.hq.disable.cflog", 0) == 1 ) {
                         return null;
					}
					// / End
					
                    break;
                case RADIO_OFF_ERROR:
                    msgId = R.string.radio_off_error;
                    // The error is not recoverable on dialog exit.
                    builder.setPositiveButton(R.string.close_dialog, mDismissAndFinish);
                    break;
                case FDN_CHECK_FAILURE:
                    msgId = R.string.fdn_check_failure;
                    builder.setPositiveButton(R.string.close_dialog, mDismiss);
                    break;
                case EXCEPTION_ERROR:
                default:
                    msgId = R.string.exception_error;
                    // The error is not recoverable on dialog exit.
                    builder.setPositiveButton(R.string.close_dialog, mDismiss);
                    break;
            }

            builder.setTitle(getText(titleId));
            builder.setMessage(getText(msgId));
            builder.setCancelable(false);
            AlertDialog dialog = builder.create();

            // make the dialog more obvious by blurring the background.
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

            return dialog;
        }
        return null;
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsForeground = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsForeground = false;
    }

    @Override
    public void onStarted(Preference preference, boolean reading) {
        if (DBG) dumpState();
        if (DBG) Log.d(LOG_TAG, "onStarted, preference=" + preference.getKey()
                + ", reading=" + reading);
        mBusyList.add(preference.getKey());

        if (mIsForeground) {
              if (reading) {
                  showDialog(BUSY_READING_DIALOG);
              } else {
                  showDialog(BUSY_SAVING_DIALOG);
              }
        }

    }

    @Override
    public void onFinished(Preference preference, boolean reading) {
        if (DBG) dumpState();
        if (DBG) Log.d(LOG_TAG, "onFinished, preference=" + preference.getKey()
                + ", reading=" + reading);
        mBusyList.remove(preference.getKey());

        if (mBusyList.isEmpty()) {
            if (reading) {
                dismissDialogSafely(BUSY_READING_DIALOG);
            } else {
                dismissDialogSafely(BUSY_SAVING_DIALOG);
            }
        }
        preference.setEnabled(true);
    }

    @Override
    public void onError(Preference preference, int error) {
        if (DBG) dumpState();
        if (DBG) Log.d(LOG_TAG, "onError, preference=" + preference.getKey() + ", error=" + error);

        if (mIsForeground) {
            showDialog(error);
        }
        preference.setEnabled(false);

        /// M: for plug-in, give plug-in chances to custom the status of the preference @{
        ExtensionManager.getCallFeaturesSettingExt().onError(preference);
        /// @}
    }

    @Override
    public void onException(Preference preference, CommandException exception) {
        if (exception.getCommandError() == CommandException.Error.FDN_CHECK_FAILURE) {
			// /annotated by guofeiyao for HQ01250678
            //onError(preference, FDN_CHECK_FAILURE);
            // /end
        } else {
            preference.setEnabled(false);
            onError(preference, EXCEPTION_ERROR);
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        if (DBG) dumpState();
        finish();
    }

    private void dismissDialogSafely(int id) {
        try {
            dismissDialog(id);
        } catch (IllegalArgumentException e) {
            // This is expected in the case where we were in the background
            // at the time we would normally have shown the dialog, so we didn't
            // show it.
        }
    }

    /* package */ void dumpState() {
        Log.d(LOG_TAG, "dumpState begin");
        for (String key : mBusyList) {
            Log.d(LOG_TAG, "mBusyList: key=" + key);
        }
        Log.d(LOG_TAG, "dumpState end");
    }

    //-------------------------------MTK-----------------------------
    /// M: ALPS01767285 Add customer dialog title @{
    private CharSequence mTitle = null;

    /**
     * set Dialog title before show dialog.
     * @param title
     */
    public void setDialogTitle(CharSequence title) {
        mTitle = title;
    }
    /// @}
}
