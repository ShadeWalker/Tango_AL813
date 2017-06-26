/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.telecom;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

// TODO: Needed for move to system service: import com.android.internal.R;

/**
 * Used to display an error dialog from within the Telecom service when an outgoing call fails
 */
public class ErrorDialogActivity extends Activity {
    private static final String TAG = ErrorDialogActivity.class.getSimpleName();

    public static final String SHOW_MISSING_VOICEMAIL_NO_DIALOG_EXTRA = "show_missing_voicemail";
    public static final String ERROR_MESSAGE_ID_EXTRA = "error_message_id";

    /**
     * Intent action to bring up Voicemail Provider settings.
     */
    public static final String ACTION_ADD_VOICEMAIL =
            "com.android.phone.CallFeaturesSetting.ADD_VOICEMAIL";

    private AlertDialog mGeneralErrorDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    private void showGenericErrorDialog(int resid) {
        final CharSequence msg = getResources().getText(resid);
        final DialogInterface.OnClickListener clickListener;
        final DialogInterface.OnCancelListener cancelListener;

        clickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (mGeneralErrorDialog != null) {
                    mGeneralErrorDialog.dismiss();
                    mGeneralErrorDialog = null;
                }
                finish();
            }
        };

        cancelListener = new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (mGeneralErrorDialog != null) {
                    mGeneralErrorDialog.dismiss();
                    mGeneralErrorDialog = null;
                }
                finish();
            }
        };

        mGeneralErrorDialog = new AlertDialog.Builder(this)
                .setMessage(msg).setPositiveButton(android.R.string.ok, clickListener)
                        .setOnCancelListener(cancelListener).create();

        mGeneralErrorDialog.show();
    }

    private void showMissingVoicemailErrorDialog() {
        mGeneralErrorDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.no_vm_number)
                .setMessage(R.string.no_vm_number_msg)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }})
                .setNegativeButton(R.string.add_vm_number_str,
                        new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    addVoiceMailNumberPanel(dialog);
                                }})
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            finish();
                        }}).show();
    }

    private void addVoiceMailNumberPanel(DialogInterface dialog) {
        if (dialog != null) {
            dialog.dismiss();
        }

        // Navigate to the Voicemail setting in the Call Settings activity.
        Intent intent = new Intent(ACTION_ADD_VOICEMAIL);
        startActivity(intent);
        finish();
    }

    /**
     *when some other activity covered on this dialog activity
     *we simply finish it to avoid the dialog flick.
     */
    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        // Don't show the return to previous task animation to avoid showing a black screen.
        // Just dismiss the dialog and undim the previous activity immediately.
        overridePendingTransition(0, 0);
    }

    ///M: ALPS01828565/ALPS01844901 this activity launch mode is singleInstance
    // reset intent onNewIntent()
    // call showErrorDialog() in onResume() instead of onCreate()
    // because of onCreate does not always be called,ex. press home key to exit, then start it again
    // @{
    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGeneralErrorDialog != null) {
            mGeneralErrorDialog.dismiss();
        }
        showErrorDialog();
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
        final boolean showVoicemailDialog = getIntent().getBooleanExtra(
                SHOW_MISSING_VOICEMAIL_NO_DIALOG_EXTRA, false);

        /// M: for volte @{
        boolean showImsDisableDialog = getIntent().getBooleanExtra(SHOW_IMS_DISABLE_DIALOG_EXTRA, false);
        if (showImsDisableDialog) {
            showImsDisableDialog();
            return;
        }

        String errorMessage = getIntent().getStringExtra(EXTRA_ERROR_MESSAGE);
        if (!TextUtils.isEmpty(errorMessage)) {
            showGenericErrorDialog(errorMessage);
            return;
        }
        /// @}
        /// M: WFC @{
        boolean showWfcDisableDialog = getIntent().getBooleanExtra(SHOW_WIFI_UNAVAILABLE, false);
        if (showWfcDisableDialog) {
            showWifiDisableDialog();
            return;
        }
        ///@}
        if (showVoicemailDialog) {
            showMissingVoicemailErrorDialog();
        } else {
            final int error = getIntent().getIntExtra(ERROR_MESSAGE_ID_EXTRA, -1);
            if (error == -1) {
                Log.w(TAG, "ErrorDialogActivity called with no error type extra.");
                finish();
            } else {
                showGenericErrorDialog(error);
            }
        }
    }
    /// @}

    /// M: for volte @{
    // here we do not consider feature option, the entry to here should consider this.
    public static final String SHOW_IMS_DISABLE_DIALOG_EXTRA = "show_ims_disable_dialog";
    public static final String ACTION_IMS_SETTING = "android.settings.DATA_ROAMING_SETTINGS";
    public static final String EXTRA_ERROR_MESSAGE = "extra_error_message";

    private void showImsDisableDialog() {
        mGeneralErrorDialog = new AlertDialog.Builder(this).setTitle(R.string.reminder)
                .setMessage(R.string.enable_ims_dialog_message)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        gotoEnableIms();
                    }
                }).setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (dialog != null) {
                            dialog.dismiss();
                        }
                        finish();
                    }
                }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        if (dialog != null) {
                            dialog.dismiss();
                        }
                        finish();
                    }
                }).show();
    }

    private void gotoEnableIms() {
        Intent intent = new Intent();
        intent.setAction(ACTION_IMS_SETTING);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    ///M: WFC @{
    public static final String SHOW_WIFI_UNAVAILABLE= "show_wifi_unavailble_dialog";

    private void showWifiDisableDialog() {
        mGeneralErrorDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.no_network)
                .setMessage(R.string.connect_to_wifi)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (dialog != null) {
                            dialog.dismiss();
                        }
                        finish();
                   }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        if (dialog != null) {
                            dialog.dismiss();
                        }
                        finish();
                    }
                })
                .show();
    }
    ///@}
    
    /**
     * try to avoid modify google default code, here add this function.
     * main logic is just same as showGenericErrorDialog(int).
     * @param errorMessage
     */
    private void showGenericErrorDialog(String errorMessage) {
        Log.d(TAG, "showGenericErrorDialog() with error message : " + errorMessage);
        String msg = errorMessage;
        final DialogInterface.OnClickListener clickListener;
        final DialogInterface.OnCancelListener cancelListener;

        clickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (mGeneralErrorDialog != null) {
                    mGeneralErrorDialog.dismiss();
                    mGeneralErrorDialog = null;
                }
                finish();
            }
        };

        cancelListener = new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (mGeneralErrorDialog != null) {
                    mGeneralErrorDialog.dismiss();
                    mGeneralErrorDialog = null;
                }
                finish();
            }
        };

        mGeneralErrorDialog = new AlertDialog.Builder(this)
                .setMessage(msg).setPositiveButton(android.R.string.ok, clickListener)
                        .setOnCancelListener(cancelListener).create();

        mGeneralErrorDialog.show();
    }
    /// @}
}
