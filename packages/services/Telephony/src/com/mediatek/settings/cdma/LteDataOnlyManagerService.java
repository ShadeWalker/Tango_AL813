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
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.WindowManager;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.R;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.telephony.TelephonyManagerEx;

/**
 * Service that check Lte data only mode and whether show dialog.
 */
public class LteDataOnlyManagerService extends Service {
    private static final String TAG = "LteDataOnlyManagerService";
    private AlertDialog mDialog;
    private int mStartId = -1;
    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
        createPermissionDialog();

        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.MSIM_MODE_SETTING),
                true, mObserverForRadioState);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intentFilter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        registerReceiver(mReceiver, intentFilter);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mStartId = startId;
		try{
			 mSubId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
		}catch(RuntimeException e){
			e.printStackTrace();
			return START_NOT_STICKY;
		}
       
		
        Log.d(TAG, "onStartCommand, startId = " + startId + "; sub = " + mSubId);
        getContentResolver().registerContentObserver(Settings.Global.getUriFor(
                TelephonyManagerEx.getDefault().getCdmaRatModeKey(mSubId)), true, mContentObserver);
        showPermissionDialog();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        getContentResolver().unregisterContentObserver(mContentObserver);
        getContentResolver().unregisterContentObserver(mObserverForRadioState);
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        unregisterReceiver(mReceiver);
    }

    private void createPermissionDialog() {
         final AlertDialog.Builder builder = new AlertDialog.Builder(this);
         builder.setTitle(getString(R.string.lte_only_dialog_title_prompt))
                .setMessage(R.string.lte_data_only_prompt)
                .setNegativeButton(R.string.lte_only_dialog_button_yes,
                      new DialogInterface.OnClickListener() {
                      @Override
                      public void onClick(DialogInterface dialog, int which) {
                          if (!checkServiceCondition()) {
                              Log.d(TAG,
                                "PositiveButton onClick :checkServiceCondition failed, stop");
                              stopSelf(mStartId);
                              return;
                          }
                          try {
                               ITelephonyEx telephonyEx = ITelephonyEx.Stub.asInterface(
                                   ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
                               if (telephonyEx != null) {
                                   Settings.Global.putInt(getContentResolver(),
                                           TelephonyManagerEx.getDefault().getCdmaRatModeKey(mSubId),
                                           TelephonyManagerEx.SVLTE_RAT_MODE_4G);
                                   telephonyEx.switchSvlteRatMode(
                                       TelephonyManagerEx.SVLTE_RAT_MODE_4G);
                               }
                          } catch (RemoteException e) {
                               e.printStackTrace();
                         } finally {
                               stopSelf(mStartId);
                         }
                      }
                  })
			 	.setPositiveButton(R.string.lte_only_dialog_button_no,
				 new DialogInterface.OnClickListener() {
				 @Override
				 public void onClick(DialogInterface dialog, int which) {
					 stopSelf(mStartId);
				 }
			 })
                  .setOnDismissListener(
                            new DialogInterface.OnDismissListener() {
                                public void onDismiss(DialogInterface dialog) {
                                    Log.d(TAG,
                                "OnDismissListener :stopSelf(), mStartId = " + mStartId);
                                    stopSelf(mStartId);
                                }
                             });
          mDialog = builder.create();
          mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
          mDialog.setCanceledOnTouchOutside(false);
     }

    private void showPermissionDialog() {
        if (!checkServiceCondition()) {
            stopSelf(mStartId);
            return;
        }
        if (mDialog != null && !mDialog.isShowing()) {
            mDialog.show();
        }
    }

    private boolean checkServiceCondition() {
        return TelephonyUtilsEx.is4GDataOnly(this)
                && !TelephonyUtilsEx.isAirPlaneMode()
                && TelephonyUtilsEx.isSvlteSlotInserted()
                && TelephonyUtilsEx.isSvlteSlotRadioOn();
    }

     private ContentObserver mContentObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (mDialog != null && mDialog.isShowing()
                    && !TelephonyUtilsEx.is4GDataOnly(LteDataOnlyManagerService.this)) {
                mDialog.dismiss();
            }
        }
    };

    private ContentObserver mObserverForRadioState = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (mDialog != null && mDialog.isShowing() && !TelephonyUtilsEx.isSvlteSlotRadioOn()) {
                mDialog.dismiss();
            }
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive action = " + action);
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                if (intent.getBooleanExtra("state", false)) {
                    Log.d(TAG, "Action stop service");
                    stopSelf(mStartId);
                }
            } else if (action.equals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED)) {
                if (!TelephonyUtilsEx.isSvlteSlotInserted()) {
                    Log.d(TAG, "Action stop service");
                    stopSelf(mStartId);
                }
            }
        }
    };

}
