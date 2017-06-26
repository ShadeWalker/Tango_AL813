
package com.mediatek.settings.cdma;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.sim.SimDialogActivity;
import com.mediatek.settings.FeatureOption;
import com.mediatek.settings.UtilsExt;
import com.mediatek.settings.ext.ISettingsMiscExt;
import com.mediatek.settings.ext.ISimManagementExt;
import com.mediatek.settings.sim.SimHotSwapHandler;
import com.mediatek.settings.sim.TelephonyUtils;

import java.util.Iterator;
import java.util.List;

/**
 * To show a dialog if two CDMA cards inserted.
 */
public class CdmaSimDialogActivity extends Activity {

    private static final String TAG = "CdmaSimDialogActivity";
    public static String DIALOG_TYPE_KEY = "dialog_type";
    public static String TARGET_SUBID_KEY = "target_subid";
    public static String ACTION_TYPE_KEY = "action_type";
    public static final int TWO_CDMA_CARD = 0;
    public static final int ALERT_CDMA_CARD = 1;
    public static final int INVALID_PICK = -1;

    private int mTargetSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private int mActionType = SimDialogActivity.INVALID_PICK;
    private PhoneAccountHandle mHandle = null;
    private SimHotSwapHandler mSimHotSwapHandler;
    private IntentFilter mIntentFilter;

    // Receiver to handle different actions
    private BroadcastReceiver mSubReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "mSubReceiver action = " + action);
            finish();
        }
    };

    private void init() {
        mSimHotSwapHandler = SimHotSwapHandler.newInstance(this);
        mSimHotSwapHandler.registerOnSubscriptionsChangedListener();
        mIntentFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        registerReceiver(mSubReceiver, mIntentFilter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG,"onCreate");
        super.onCreate(savedInstanceState);
        final Bundle extras = getIntent().getExtras();
        init();
        if (extras != null) {
            final int dialogType = extras.getInt(DIALOG_TYPE_KEY, INVALID_PICK);
            mTargetSubId = extras.getInt(TARGET_SUBID_KEY, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            mActionType = extras.getInt(ACTION_TYPE_KEY, SimDialogActivity.INVALID_PICK);
            Log.d(TAG, "dialogType: " + dialogType + " argetSubId: " + mTargetSubId
                    + " actionType: " + mActionType);
            switch (dialogType) {
                case TWO_CDMA_CARD:
                    createTwoCdmaCardDialog();
                    break;
                case ALERT_CDMA_CARD:
                    displayAlertCdmaDialog();
                    break;
                default:
                    throw new IllegalArgumentException("Invalid dialog type " + dialogType + " sent.");
            }
        } else {
            Log.e(TAG, "unexpect happend");
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSimHotSwapHandler.unregisterOnSubscriptionsChangedListener();
        unregisterReceiver(mSubReceiver);
    }

    private void createTwoCdmaCardDialog() {
        Log.d(TAG,"createTwoCdmaCardDialog...");
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage(R.string.two_cdma_dialog_msg);
        alertDialogBuilder.setPositiveButton(android.R.string.ok, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (dialog != null) {
                    dialog.dismiss();
                }
                finish();
            }

        });
        alertDialogBuilder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (dialog != null) {
                    dialog.dismiss();
                }
                finish();
            }

        });
        Dialog dialog = alertDialogBuilder.create();
        dialog.show();
    }

    private void displayAlertCdmaDialog() {
        Log.d(TAG, "displayPreferredCdmaDialog()... + c2K support: " + FeatureOption.MTK_C2K_SLOT2_SUPPORT);
        final Context context = getApplicationContext();
        if (mActionType == SimDialogActivity.CALLS_PICK) {
            final TelecomManager telecomManager = TelecomManager.from(context);
            final List<PhoneAccountHandle> phoneAccountsList = telecomManager.getCallCapablePhoneAccounts();
            mHandle = mTargetSubId < 1 ? null : phoneAccountsList.get(mTargetSubId - 1);
            mTargetSubId = TelephonyUtils.phoneAccountHandleTosubscriptionId(context, mHandle);
            Log.d(TAG, "convert " + mHandle + " to subId: " + mTargetSubId);
        } else if (mActionType == SimDialogActivity.DATA_PICK
                || mActionType == SimDialogActivity.SMS_PICK) {
            mHandle = TelephonyUtils.subscriptionIdToPhoneAccountHandle(context, mTargetSubId);
        }
        SubscriptionInfo targetSir = Utils.findRecordBySubId(context, mTargetSubId);
        SubscriptionInfo defaultSir = null;
        int[] list = SubscriptionManager.from(context).getActiveSubscriptionIdList();
        for (int i : list) {
            if (i != mTargetSubId) {
                defaultSir = Utils.findRecordBySubId(context, i);
            }
        }
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        String cdmaCardCompetionMessage= context.getResources()
                .getString(R.string.c2k_cdma_card_competion_message,
                defaultSir.getDisplayName(),
                defaultSir.getDisplayName(),
                targetSir.getDisplayName());
        String gsmCdamCardMesage = context.getResources()
                .getString(R.string.c2k_gsm_cdma_sim_message,
                        targetSir.getDisplayName(),
                        defaultSir.getDisplayName(),
                        defaultSir.getDisplayName(),
                        targetSir.getDisplayName());
        String message = FeatureOption.MTK_C2K_SLOT2_SUPPORT ? cdmaCardCompetionMessage : gsmCdamCardMesage;
        dialog.setMessage(message);
        int textIdPositive = FeatureOption.MTK_C2K_SLOT2_SUPPORT ? android.R.string.ok : R.string.yes;
        dialog.setPositiveButton(textIdPositive, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (TelecomManager.from(context).isInCall()) {
                    Toast.makeText(context, R.string.default_data_switch_err_msg1,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                final TelecomManager telecomManager = TelecomManager.from(context);
                PhoneAccountHandle phoneAccount =
                        telecomManager.getUserSelectedOutgoingPhoneAccount();
                int subIdCalls = TelephonyUtils.phoneAccountHandleTosubscriptionId(context, phoneAccount);
                int subIdSms = SubscriptionManager.getDefaultSmsSubId();
                if (mActionType == SimDialogActivity.DATA_PICK) {
                    if (SubscriptionManager.isValidSubscriptionId(subIdCalls)
                            || (!FeatureOption.MTK_C2K_SLOT2_SUPPORT)) {
                        setUserSelectedOutgoingPhoneAccount(mHandle);
                    }
                    if (SubscriptionManager.isValidSubscriptionId(subIdSms)
                            || (!FeatureOption.MTK_C2K_SLOT2_SUPPORT)) {
                        setDefaultSmsSubId(context, mTargetSubId);
                    }
                    if (SubscriptionManager.isValidSubscriptionId(mTargetSubId)) {
                        setDefaultDataSubId(context, mTargetSubId);
                    }
                } else if (mActionType == SimDialogActivity.SMS_PICK) {
                    if (SubscriptionManager.isValidSubscriptionId(subIdCalls)) {
                        setUserSelectedOutgoingPhoneAccount(mHandle);
                    }
                    if (SubscriptionManager.isValidSubscriptionId(mTargetSubId)) {
                        setDefaultSmsSubId(context, mTargetSubId);
                        setDefaultDataSubId(context, mTargetSubId);
                    }
                } else if (mActionType == SimDialogActivity.CALLS_PICK) {
                    setUserSelectedOutgoingPhoneAccount(mHandle);
                    if (SubscriptionManager.isValidSubscriptionId(subIdSms)) {
                        setDefaultSmsSubId(context, mTargetSubId);
                    }
                    if (SubscriptionManager.isValidSubscriptionId(mTargetSubId)) {
                        setDefaultDataSubId(context, mTargetSubId);
                    }
                }
                Log.d(TAG, "subIdCalls: " + subIdCalls + " subIdSms: "
                + subIdSms + " mTargetSubId: " + mTargetSubId);
                finish();
            }
        });
        int textIdNegative = FeatureOption.MTK_C2K_SLOT2_SUPPORT ? android.R.string.cancel : R.string.no;
        dialog.setNegativeButton(textIdNegative, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (dialog != null) {
                    
                    dialog.dismiss();
                }
                finish();
            }
        });
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    finish();
                }
                return true;
            }
        });
        dialog.show();
    }

    private void setDefaultDataSubId(final Context context, final int subId) {
        final SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
        subscriptionManager.setDefaultDataSubId(subId);
        if (mActionType == SimDialogActivity.DATA_PICK) {
            Toast.makeText(context, R.string.data_switch_started, Toast.LENGTH_LONG).show();
        }
    }

    private void setDefaultSmsSubId(final Context context, final int subId) {
        final SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
        subscriptionManager.setDefaultSmsSubId(subId);
    }

    private void setUserSelectedOutgoingPhoneAccount(PhoneAccountHandle phoneAccount) {
        final TelecomManager telecomManager = TelecomManager.from(this);
        telecomManager.setUserSelectedOutgoingPhoneAccount(phoneAccount);
    }
}
