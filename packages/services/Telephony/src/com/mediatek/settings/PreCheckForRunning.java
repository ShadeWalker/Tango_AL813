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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.Log;

import com.mediatek.internal.telephony.CellConnMgr;

import java.util.ArrayList;

/**
 * class for check the sim status.
 * */
public class PreCheckForRunning {
    private CellConnMgr mCellConnMgr;
    private Activity mActivity;
    private static final String TAG = "Settings/PreCheckForRunning";
    public boolean mByPass = false;

    /**
     * class constructor.
     * @param ctx context
     */
    public PreCheckForRunning(Activity ctx) {
        mActivity = ctx;
        mCellConnMgr = new CellConnMgr(mActivity);
    }

    /**
     * start to check the cell conn status.
     * @param subId sub id
     * @param requestType the type to check
     * @return true if service is ready
     */
    public boolean checkToRun(int subId, int requestType) {
        Log.d(TAG, "checkToRun requestType:" + requestType + " mByPass:" + mByPass);
        if (mByPass) {
            return true;
        }
        return unLock(subId, requestType);
    }

    private boolean unLock(final int subId, final int requestType) {
        ArrayList<String> dialogSrings = null;
        final int state = mCellConnMgr.getCurrentState(subId, requestType);
        Log.d(TAG, "unLock state:" + state + " subId:" + subId);
        if (state == CellConnMgr.STATE_READY) {
            return true;
        } else {
            Log.d(TAG, "unLock requestType:" + requestType);
            dialogSrings = mCellConnMgr.getStringUsingState(subId, state);
            if (dialogSrings != null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                builder.setTitle(dialogSrings.get(0));
                builder.setMessage(dialogSrings.get(1));
                builder.setPositiveButton(dialogSrings.get(2), new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mCellConnMgr.handleRequest(subId, state);
                        if (mActivity.getClass().toString().contains("CallFeaturesSetting")) {
                            PreferenceScreen preferenceScreen = ((PreferenceActivity)
                                    mActivity).getPreferenceScreen();
                            Dialog targetDialog = ((PreferenceScreen) preferenceScreen
                                    .findPreference("button_voicemail_category_key")).getDialog();
                            if (targetDialog != null) {
                                targetDialog.dismiss();
                            }
                        } else {
                            mActivity.finish();
                        }
                    }
                });
                builder.setNegativeButton(dialogSrings.get(3), new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mActivity.getClass().toString().contains("CallFeaturesSetting")) {
                            PreferenceScreen preferenceScreen = ((PreferenceActivity)
                                    mActivity).getPreferenceScreen();
                            Dialog targetDialog = ((PreferenceScreen) preferenceScreen
                                    .findPreference("button_voicemail_category_key")).getDialog();
                            if (targetDialog != null) {
                                targetDialog.dismiss();
                            }
                        } else {
                            mActivity.finish();
                        }
                    }
                });
                builder.setCancelable(false);
                builder.create().show();
                Log.d(TAG, "show dialog");
            }
        }
        return false;
    }
}
