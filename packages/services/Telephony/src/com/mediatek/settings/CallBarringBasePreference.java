/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.BadTokenException;
import android.widget.EditText;
import android.widget.TextView;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.phone.PhoneUtils;
import com.android.phone.R;
import com.android.phone.SubscriptionInfoHelper;

import com.mediatek.phone.TimeConsumingPreferenceListener;
import com.mediatek.phone.ext.ExtensionManager;


public class CallBarringBasePreference extends CheckBoxPreference implements
        OnPreferenceClickListener {
    private static final String LOG_TAG = "Settings/CallBarringBasePreference";
    /* id is according to TimeConsumingPreferenceActivity define */
    public static final int EXCEPTION_ERROR = 300;
    public static final int PASSWORD_ERROR = 700;
    public static final int FDN_FAIL = 800;
    private static final boolean DBG = true; // (PhoneGlobals.DBG_LEVEL >= 2);

    private MyHandler mHandler = new MyHandler();
    private TimeConsumingPreferenceListener mTcpListener;
    private Context mContext = null;
    private Phone mPhone;
    private int mTitle;
    private String mFacility;
    private boolean mCurrentClickState = false;
    private static final int PASSWORD_LENGTH = 4;
    private CallBarringInterface mCallBarringInterface = null;
    private boolean mResult = true;
    private int mSubId = SubscriptionInfoHelper.NO_SUB_ID;
    private int mServiceClass = CommandsInterface.SERVICE_CLASS_VOICE;

    public CallBarringBasePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEnabled(false);
        mContext = context;
    }

    public void setRefreshInterface(CallBarringInterface i) {
        mCallBarringInterface = i;
    }

    public void setmTitle(int title) {
        mTitle = title;
    }

    public void setmFacility(String facility) {
        mFacility = facility;
    }

    @Override
    protected void onClick() {
        mCurrentClickState = !isChecked();
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        View titleView = view.findViewById(com.android.internal.R.id.title);
        if (titleView != null && titleView instanceof TextView) {
            ((TextView) titleView).setEllipsize(TextUtils.TruncateAt.valueOf("MIDDLE"));
        }
    }

    public boolean onPreferenceClick(Preference preference) {
        doPreferenceClick(mContext.getString(mTitle));
        return true;
    }

    public void setCallState(String password) {
        setCallState(mFacility, false, password);
        if (mTcpListener != null) {
            mTcpListener.onStarted(this, false);
        }
    }

    private void doPreferenceClick(final String title) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        final View textEntryView = inflater.inflate(R.layout.mtk_callbarring_option, null);

        TextView content = (TextView) textEntryView.findViewById(R.id.ViewTop);
        content.setText(mContext
                .getString(R.string.enter_callbarring_password));

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(textEntryView);
        builder.setTitle(title);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        EditText passwordText = (EditText) textEntryView
                                .findViewById(R.id.EditPassword);
                        String password = passwordText.getText().toString();

                        if (!validatePassword(password)) {
                            AlertDialog.Builder builder1 = new AlertDialog.Builder(
                                    mContext);
                            builder1.setTitle(title);
                            builder1.setMessage(mContext
                                    .getText(R.string.wrong_password));
                            builder1.setCancelable(false);
                            builder1.setPositiveButton(R.string.ok, null);
                            builder1.create().show();
                        } else {
                            if (mTcpListener != null) {
                                mTcpListener.onStarted(
                                        CallBarringBasePreference.this, false);
                                setCallState(
                                        mFacility,
                                        CallBarringBasePreference.this.mCurrentClickState,
                                        password);
                            }
                        }
                    }
                });

        AlertDialog dialog = builder.create();
        if (dialog != null) {
            Window window = dialog.getWindow();
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            dialog.show();
        }
    }

    // reason=true means get the call state when init.
    // reason=false means get the call stete after setting the state.
    private void getCallState(String facility, String password, boolean reason) {
        if (DBG) {
            Log.d(LOG_TAG, "getCallState() is called with facility is "
                    + facility + "password is " + password + "reason is "
                    + reason);
        }
        Message m;
        if (reason) {
            m = mHandler.obtainMessage(MyHandler.MESSAGE_GET_CALLBARRING_STATE,
                    0, MyHandler.MESSAGE_GET_CALLBARRING_STATE, null);
        } else {
            m = mHandler.obtainMessage(MyHandler.MESSAGE_GET_CALLBARRING_STATE,
                    0, MyHandler.MESSAGE_SET_CALLBARRING_STATE, null);
        }
        mPhone.getFacilityLock(facility, password, m);
    }

    private void setCallState(String facility, boolean enable, String password) {
        if (DBG && Build.TYPE.equals("eng")) {
            Log.d(LOG_TAG, "setCallState() is called with facility is "
                    + facility + "password is " + password + "enable is " + enable);
        }
        Message m = mHandler.obtainMessage(
                MyHandler.MESSAGE_SET_CALLBARRING_STATE, 0,
                MyHandler.MESSAGE_SET_CALLBARRING_STATE, null);

        mPhone.setFacilityLock(facility, enable, password, m);
    }

    void init(TimeConsumingPreferenceListener listener, boolean skipReading, int subId) {
        mSubId = subId;
        mPhone = PhoneUtils.getPhoneUsingSubId(mSubId);

        setOnPreferenceClickListener(this);
        mTcpListener = listener;
        if (!skipReading) {
            if (mTcpListener != null) {
                Log.d(LOG_TAG, "init() is called");
                mTcpListener.onStarted(this, true);
            }
            getCallState(mFacility, "", true);
        }
    }

    private boolean validatePassword(String password) {
        return password != null && password.length() == PASSWORD_LENGTH;
    }

    private class MyHandler extends Handler {
        private static final int MESSAGE_GET_CALLBARRING_STATE = 0;
        private static final int MESSAGE_SET_CALLBARRING_STATE = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_GET_CALLBARRING_STATE:
                handleGetCallBarringResponse(msg);
                break;
            case MESSAGE_SET_CALLBARRING_STATE:
                handleSetCallBarringResponse(msg);
                break;
            default:
                break;
            }
        }

        private void handleSetCallBarringResponse(Message msg) {
            //This will dismiss the setting dialog.
            mTcpListener.onFinished(CallBarringBasePreference.this, false);
            int errorid = EXCEPTION_ERROR;
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception != null) {
                if (ar.exception instanceof CommandException) {
                    CommandException ce = (CommandException) ar.exception;
                    if (ce.getCommandError() == CommandException.Error.PASSWORD_INCORRECT) {
                        errorid =  PASSWORD_ERROR;
                    } else if (ce.getCommandError() == CommandException.Error.FDN_CHECK_FAILURE) {
                        errorid = FDN_FAIL;
                    }
                }
                mCallBarringInterface.setErrorState(errorid);
                if (mTcpListener != null) {
                    Log.d(LOG_TAG, " handleSetCallBarringResponse: ar.exception="+ ar.exception);
                    /// M: for plug-in, to handle error dialog when exception UT_XCAP_404_NOT_FOUND or UT_XCAP_409_CONFLICT 
                    if (!ExtensionManager.getCallFeaturesSettingExt().handleErrorDialog(mContext, ar, CallBarringBasePreference.this)) {
                        mTcpListener.onError(CallBarringBasePreference.this, errorid);
                    }
                }
                if (DBG) {
                    Log.d(LOG_TAG, "handleSetCallBarringResponse: ar.exception="
                            + ar.exception);
                }
            } else {
                if (DBG) {
                    Log.d(LOG_TAG,
                            "handleSetCallBarringResponse is called without exception");
                }
                /// Add for [CMCC_VoLTE_SS] @{
                //  When set one of "CB_FACILITY_BAOC & CB_FACILITY_BAOIC & CB_FACILITY_BAOICxH",
                //  we should query them again as a whole.
                //  So does"CB_FACILITY_BAIC & CB_FACILITY_BAICr"
                if (mCallBarringInterface instanceof CallBarring) {
                    CallBarring cb = (CallBarring) mCallBarringInterface;
                    CallBarringBasePreference cbp =
                        (CallBarringBasePreference)cb.setRefreshIndex(mFacility);
                    Log.d(LOG_TAG, "[handleSetCallBarringResponse] cbp:" + cbp.getKey());
                    if (cbp != null) {
                        cbp.init(cb, false, mSubId);
                    }
                } else {
                    Log.d(LOG_TAG,"[handleSetCallBarringResponse] mCallBarringInterface error");
                }
                /// @}
            }
        }

        private void handleGetCallBarringResponse(Message msg) {
            // onFinish will always make the item enable, so we need move this here
            // Firstly enable the item, decide show or disable after get the result.
            if (msg.arg2 == MESSAGE_GET_CALLBARRING_STATE) {
                mTcpListener.onFinished(CallBarringBasePreference.this, true);
            } else {
                mTcpListener.onFinished(CallBarringBasePreference.this, false);
            }
            int errorid = EXCEPTION_ERROR;
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception != null) {
                mResult = false;
                if (ar.exception instanceof CommandException) {
                    CommandException ce = (CommandException) ar.exception;
                    if (DBG) {
                        Log.d(LOG_TAG,
                                "handleGetCallBarringResponse : Error =" + ce.getCommandError());
                    }

                    /// Add for [CMCC_VoLTE_SS] @{
                    //  Should disable CB_FACILITY_BAOC & CB_FACILITY_BAOIC &
                    //  CB_FACILITY_BAOICxH & CLIR (Caller ID)
                    if (ce.getCommandError()
                            == CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED) {
                        Log.d(LOG_TAG,"receive SPECAIL_UT_COMMAND_NOT_SUPPORTED CB!!");
                        setEnabled(false);
                        setSummary(mContext.getString(R.string.lable_disable));
                        mResult = true; // We don't think this is error, just a special status
                        return;
                    } else {
                        /// @}
                        if (ce.getCommandError() == CommandException.Error.PASSWORD_INCORRECT) {
                            errorid =  PASSWORD_ERROR;
                        } else if (ce.getCommandError() == CommandException.Error.FDN_CHECK_FAILURE) {
                            errorid = FDN_FAIL;
                        }
                    }
                } else {
                    /// Like ImsException and other exception, and we can't handle it
                    /// the same way as a CommandException.
                    if (DBG) {
                        Log.d(LOG_TAG, "Exception = " + ar.exception);
                    }
                }

                mCallBarringInterface.setErrorState(errorid);
                try {
                    mTcpListener.onError(CallBarringBasePreference.this, errorid);
                } catch (BadTokenException e) {
                    if (DBG) {
                        Log.d(LOG_TAG, "BadTokenException");
                    }
                }

            } else {
                if (DBG) {
                    Log.d(LOG_TAG,
                            "handleGetCallBarringResponse is called without exception");
                }
                CallBarringBasePreference.this.setEnabled(true);
                ///M : CRALPS02113837 @{
                ExtensionManager.getCallFeaturesSettingExt().resetImsPdnOverSSComplete(getContext(), msg.arg2);
                /// @}
                int[] ints = (int[]) ar.result;
                if (ints != null && ints.length > 0) {
                    mResult = true;
                    int value = ints[0];
                    //we consider voice service in here
                    Log.d(LOG_TAG, "Current value = " + value +
                            " Current serviceClass = " + mServiceClass);
                    value = value & mServiceClass;

                    Log.i(LOG_TAG, "After value & mServiceClass = " + value);
                    String summary = null;
                    if (DBG) {
                        Log.d(LOG_TAG, "Value is " + value);
                    }
                    if (value == 0) {
                        summary = mContext.getString(R.string.lable_disable);
                        CallBarringBasePreference.this.setChecked(false);
                    } else {
                        summary = mContext.getString(R.string.lable_enable);
                        CallBarringBasePreference.this.setChecked(true);
                        // Remove for [CMCC_VoLTE_SS]
                        //mCallBarringInterface.doCallBarringRefresh(mFacility);
                    }
                    CallBarringBasePreference.this.setSummary(summary);
                } else {
                    mResult = false;
                    if (DBG) {
                        Log.d(LOG_TAG,
                                "handleGetCallBarringResponse ar.result get error");
                     }
                }
            }
        }
    }

    public boolean isSuccess() {
        return mResult;
    }

    public void setServiceClass(int serviceClass) {
        mServiceClass = serviceClass;
    }
}
