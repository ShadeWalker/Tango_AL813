/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

package com.mediatek.settings.cdma;

import android.app.AlertDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.WindowManager;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteModeController;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteRatController;
import com.android.phone.R;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.telephony.TelephonyManagerEx;

/**
 * Service that check Lte data only mode and whether show dialog.
 */
public class LteSearchTimeoutCheckService extends Service {
    private static final String TAG = "LteSearchTimeoutCheckService";
    public static final String ACTION_START_SELF =
        "com.mediatek.intent.action.STARTSELF_LTE_SEARCH_TIMEOUT_CHECK";
    private static final long DELAY_MILLIS_SHOW_DIALOG = 180000;
    private AlertDialog mDialog;
    private int mStartId = -1;
    private TelephonyManager mTelephonyManager;
    private boolean mIsLteInService;
    private boolean mIsWaitingCheck;
    private boolean mIsSvlteSlotInserted;
    private boolean mIsSvlteSlotRadioOn;
    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private final Handler mHandler = new Handler();
    private PhoneStateListener mPhoneStateListenerForLte;
    private Runnable mShowDialogRunnable = new Runnable() {
            @Override
            public void run() {
                showTimeoutDialog();
            }
        };

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
        createTimeoutDialog();
        mTelephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        mIsSvlteSlotInserted = TelephonyUtilsEx.isSvlteSlotInserted();
        mIsSvlteSlotRadioOn = TelephonyUtilsEx.isSvlteSlotRadioOn();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intentFilter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        registerReceiver(mReceiver, intentFilter);
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.LTE_ON_CDMA_RAT_MODE),
                true, mContentObserver);
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.MSIM_MODE_SETTING),
                true, mObserverForRadioState);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand, startId = " + startId);
        mStartId = startId;
        startCheckTimeout();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        getContentResolver().unregisterContentObserver(mContentObserver);
        getContentResolver().unregisterContentObserver(mObserverForRadioState);
        mHandler.removeCallbacks(mShowDialogRunnable);
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        if (mTelephonyManager != null && mPhoneStateListenerForLte != null) {
            mTelephonyManager.listen(mPhoneStateListenerForLte, PhoneStateListener.LISTEN_NONE);
        }
        unregisterReceiver(mReceiver);
    }

    private void startCheckTimeout() {
       Log.d(TAG, "startCheckTimeout");
        if (!checkServiceCondition()) {
            return;
        }
        mIsWaitingCheck = false;
        int[] subId  = SubscriptionManager.getSubId(SvlteModeController.getActiveSvlteModeSlotId());
        if (subId != null && subId.length > 0) {
            mSubId = subId[0];
            if (!SubscriptionManager.isValidSubscriptionId(mSubId)) {
                Log.e(TAG, "startCheckTimeout return, mSubId is invalid");
                Log.d(TAG, "mSubId is invalid, start!!");
                Intent intent = new Intent(ACTION_START_SELF);
                LteSearchTimeoutCheckService.this.sendBroadcast(intent);
                stopSelf(mStartId);
                return;
            }
        } else {
            Log.e(TAG, "startCheckTimeout return, get subId failed");
            Log.d(TAG, "fail error, start!!");
            Intent intent = new Intent(ACTION_START_SELF);
            LteSearchTimeoutCheckService.this.sendBroadcast(intent);
            stopSelf(mStartId);
            return;
        }
        if (mTelephonyManager != null && mDialog != null && !mDialog.isShowing()) {
            mHandler.removeCallbacks(mShowDialogRunnable);
            mHandler.postDelayed(mShowDialogRunnable, DELAY_MILLIS_SHOW_DIALOG);
            createPhoneStateListener();
            if (mPhoneStateListenerForLte != null) {
                mTelephonyManager.listen(mPhoneStateListenerForLte,
                    PhoneStateListener.LISTEN_SERVICE_STATE);
            }
            Log.d(TAG, "startCheckTimeout ok");
        }
    }

    private void createTimeoutDialog() {
         final AlertDialog.Builder builder = new AlertDialog.Builder(this);
         builder.setTitle(getString(R.string.lte_only_dialog_title_prompt))
                .setMessage(R.string.lte_data_only_timeout)
                .setNegativeButton(R.string.lte_only_dialog_button_no, null)
                .setPositiveButton(R.string.lte_only_dialog_button_yes,
                      new DialogInterface.OnClickListener() {
                      @Override
                      public void onClick(DialogInterface dialog, int which) {
                          Log.d(TAG, "PositiveButton onClick");
                          if (!checkServiceCondition()) {
                              return;
                          }
                          try {
                                int setvalue = 0;
                                ITelephonyEx telephonyEx = ITelephonyEx.Stub.asInterface(
                                        ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
                                int subId[] = SubscriptionManager.getSubIdUsingSlotId(
                                        CdmaFeatureOptionUtils.getExternalModemSlot());

                               if (telephonyEx != null && subId != null) {
                                   Settings.Global.putInt(getContentResolver(),
                                       TelephonyManagerEx.getDefault().getCdmaRatModeKey(subId[0]),
                                       TelephonyManagerEx.SVLTE_RAT_MODE_4G);
                                   //telephonyEx.switchSvlteRatMode(
                                   //    TelephonyManagerEx.SVLTE_RAT_MODE_4G);
                                   setvalue = SvlteRatController.RAT_MODE_SVLTE_2G
                                              | SvlteRatController.RAT_MODE_SVLTE_3G
                                              | SvlteRatController.RAT_MODE_SVLTE_4G;

                                   telephonyEx.switchRadioTechnology(setvalue);
                                   Log.d(TAG, "PositiveButton : setvalue=" + setvalue
                                                + "cdma subId = " + subId[0]);
                               } else {
                                   Log.d(TAG, "subId is null");
                               }
                          } catch (RemoteException e) {
                               e.printStackTrace();
                         } finally {
                               stopSelf(mStartId);
                         }
                      }
                  })
                  .setOnDismissListener(
                            new DialogInterface.OnDismissListener() {
                                public void onDismiss(DialogInterface dialog) {
                                    if (!checkServiceCondition() || mIsLteInService
                                            || mIsWaitingCheck) {
                                        Log.d(TAG, "OnDismiss : donothing");
                                    } else {
                                        Log.d(TAG, "OnDismiss : will restart service");
                                        Intent intent = new Intent(ACTION_START_SELF);
                                        LteSearchTimeoutCheckService.this.sendBroadcast(intent);
                                        stopSelf(mStartId);
                                    }
                                }
                            });
          mDialog = builder.create();
          mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
          mDialog.setCanceledOnTouchOutside(false);
     }

    private void showTimeoutDialog() {
        if (checkServiceCondition() && mDialog != null
                && !mDialog.isShowing() && mIsLteInService == false) {
            Log.d(TAG, "showTimeoutDialog");
            mDialog.show();
        }
    }

    private ContentObserver mContentObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (!TelephonyUtilsEx.is4GDataOnly(LteSearchTimeoutCheckService.this)) {
                Log.d(TAG, "mContentObserver update, not 4GDataOnly,stopself");
                stopSelf(mStartId);
            }
        }
    };

    private ContentObserver mObserverForRadioState = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (mIsSvlteSlotRadioOn == TelephonyUtilsEx.isSvlteSlotRadioOn()) {
                return;
            }
            mIsSvlteSlotRadioOn = !mIsSvlteSlotRadioOn;
            Log.d(TAG, "mObserverForRadioState update mIsSvlteSlotRadioOn : " + mIsSvlteSlotRadioOn);
            if (mIsSvlteSlotRadioOn) {
                startCheckTimeout();
            } else {
                stopCheck();
            }
        }
    };
    private void createPhoneStateListener() {
        if (mPhoneStateListenerForLte == null) {
            mPhoneStateListenerForLte = new PhoneStateListener(mSubId) {
                @Override
                public void onServiceStateChanged(ServiceState serviceState) {
                    Log.d(TAG, "onServiceStateChanged, mSubId : " + this.mSubId
                           + ", serviceState : " + serviceState);
                    if (serviceState.getDataRegState() == ServiceState.STATE_IN_SERVICE
                        && serviceState.getVoiceRegState() == ServiceState.STATE_OUT_OF_SERVICE
                        && serviceState.getDataNetworkType() == TelephonyManager.NETWORK_TYPE_LTE) {
                        Log.d(TAG, "LTE is in service state, cancel show dialog");
                        mIsLteInService = true;
                        mHandler.removeCallbacks(mShowDialogRunnable);
                        if (mDialog != null && mDialog.isShowing()) {
                           mDialog.dismiss();
                        }
                    } else {
                        if (mIsLteInService) {
                            mIsLteInService = false;
                            startCheckTimeout();
                        }
                    }
                }
            };
        }
    }

    private boolean checkServiceCondition() {
        return TelephonyUtilsEx.is4GDataOnly(this)
                && !TelephonyUtilsEx.isAirPlaneMode()
                && TelephonyUtilsEx.isSvlteSlotInserted()
                && TelephonyUtilsEx.isSvlteSlotRadioOn();
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive action = " + action);
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                if (intent.getBooleanExtra("state", false)) {
                    Log.d(TAG, "Action enter flight mode");
                    stopCheck();
                } else {
                    Log.d(TAG, "Action leave flight mode");
                    startCheckTimeout();
                }
            } else if (action.equals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED)) {
                if (mIsSvlteSlotInserted == TelephonyUtilsEx.isSvlteSlotInserted()) {
                    return;
                }
                mIsSvlteSlotInserted = !mIsSvlteSlotInserted;
                Log.d(TAG, "Action update mIsSvlteSlotInserted : " + mIsSvlteSlotInserted);
                if (mIsSvlteSlotInserted) {
                    startCheckTimeout();
                } else {
                    stopCheck();
                }
            }
        }
    };

    private void stopCheck() {
        Log.d(TAG, "stopCheck");
        mIsLteInService = false;
        mIsWaitingCheck = true;
        mHandler.removeCallbacks(mShowDialogRunnable);
        if (mDialog != null && !mDialog.isShowing()) {
            mDialog.dismiss();
        }
        if (mPhoneStateListenerForLte != null) {
            mTelephonyManager.listen(mPhoneStateListenerForLte,
                PhoneStateListener.LISTEN_NONE);
        }
    }

}
