/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
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
package com.mediatek.dialer.widget;

import com.android.dialer.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;

public class LocationPermissionDialogFragment extends DialogFragment {
    private static final String TAG = "LocationPermissionDialogFragment";
    private static final String DIALOG_TAG = "LocationPermission";
    private static final String PREFENCE_TAG = "location_permission_notify";
    private static final String PREFENCE_ITEM = "location_permission_item";

    private ShowCount mShowCount;

    public static LocationPermissionDialogFragment getInstance(FragmentManager manager) {
        LocationPermissionDialogFragment instance =
                (LocationPermissionDialogFragment) manager.findFragmentByTag(DIALOG_TAG);
        Log.i(TAG, "getInstance " + instance);
        if (instance == null) {
            instance = new LocationPermissionDialogFragment();
            Log.i(TAG, "create new instance " + instance + " in " + manager);
        }
        return instance;
    }

    public static void show(Activity activity, FragmentManager manager) {
        SharedPreferences sharedPrefs = activity.getSharedPreferences(
                PREFENCE_TAG, activity.MODE_WORLD_READABLE);
        final boolean allow = sharedPrefs.getBoolean(PREFENCE_ITEM, false);
        if (!allow) {
            LocationPermissionDialogFragment dialog = getInstance(manager);
            if (!dialog.isAdded()) {
                Log.d(TAG, "dialog show: " + dialog);
                dialog.show(manager, DIALOG_TAG);
                dialog.setCancelable(false);
            }
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String summary = String.format(getString(R.string.location_permission_summary), "18");
        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(getString(R.string.location_permission_title))
                .setMessage(summary)
                .setPositiveButton(R.string.location_permission_allow,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                onPositiveButtonClick();    
                            }
                        }
                )
                .setNegativeButton(R.string.location_permission_deny,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                onNegativeButtonClick();    
                            }
                        }
                )
                .create();
        return dialog;
    }

    private void onPositiveButtonClick() {
        Log.d(TAG, "onPositiveButtonClick");
        Activity activity = getActivity();
        if (activity != null && !activity.isFinishing()) {
            SharedPreferences sharedPrefs = activity.getSharedPreferences(
                    PREFENCE_TAG, activity.MODE_WORLD_READABLE);
            sharedPrefs.edit().putBoolean(PREFENCE_ITEM, true).commit();
        }
    }

    private void onNegativeButtonClick() {
        Log.d(TAG, "onNegativeButtonClick");
        finishActivitySafely();
    }

    private void finishActivitySafely() {
        Activity activity = getActivity();
        if (activity != null && !activity.isFinishing()) {
            activity.finish();
        }
    }

    @Override
    public void onResume() {        
        super.onResume();
        if (mShowCount == null) {          
            mShowCount = new ShowCount(19000, 1000);
            mShowCount.start();
            Log.d(TAG, "start timer " + this);
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (mShowCount != null) {
            mShowCount.cancel();
            mShowCount = null;
        }
        super.onDestroy();
    }

    class ShowCount extends CountDownTimer {
        private int mTimes = 19;
        public ShowCount(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        @Override
        public void onFinish() {
            Log.d(TAG, "onFinish");
            mShowCount = null;
            finishActivitySafely();
        }

        @Override
        public void onTick(long millisUntilFinished) {
            mTimes--;
            String summary = String.format(
                    getString(R.string.location_permission_summary), String.valueOf(mTimes));
            AlertDialog dialog = (AlertDialog) getDialog();
            if (dialog != null) {
                dialog.setMessage(summary.toString());
            }
        }
    }
}
