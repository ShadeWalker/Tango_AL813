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
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;

import com.android.phone.SubscriptionInfoHelper;
import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;

public class NetworkTypePreference extends DialogPreference
            implements DialogInterface.OnMultiChoiceClickListener {
    private static final String TAG = "NetworkTypePreference";

    private int mNetworkTypeNum;
    private int mAct;
    private AlertDialog mDialog;
    private boolean[] mCheckState;
    private String[] mNetworkTypeArray;
    private int[] mActArray = {
            NetworkEditor.RIL_2G,
            NetworkEditor.RIL_3G,
            NetworkEditor.RIL_4G
    };

    public NetworkTypePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        int subId = SubscriptionInfoHelper.NO_SUB_ID;
        if (context instanceof Activity) {
            Intent intent = ((Activity) context).getIntent();
            subId = intent.getIntExtra(
                    NetworkEditor.PLMN_SUB, SubscriptionInfoHelper.NO_SUB_ID);
        }
        String[] tempArray = {"2G", "3G"};

        // add the apn type: ims for LTE
        if (TelephonyUtils.isUSIMCard(context, subId) && FeatureOption.isMtkLteSupport()) {
            mNetworkTypeArray = new String[tempArray.length + 1];
            for (int i = 0; i < tempArray.length; i++) {
                mNetworkTypeArray[i] = tempArray[i];
            }
            mNetworkTypeArray[mNetworkTypeArray.length - 1] = "4G";
        } else {
            mNetworkTypeArray = tempArray;
        }

        if (mNetworkTypeArray != null) {
            mNetworkTypeNum = mNetworkTypeArray.length;
        }
        mCheckState = new boolean[mNetworkTypeNum];
    }

    public NetworkTypePreference(Context context) {
        this(context, null);
    }

    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        super.onPrepareDialogBuilder(builder);
        builder.setMultiChoiceItems(mNetworkTypeArray, mCheckState, this);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            mAct = getTypeCheckResult();
            callChangeListener(mAct);
        } else {
            initCheckState(mAct);
        }
        mDialog = null;
    }

    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
        mCheckState[which] = isChecked;
        mDialog = (AlertDialog) getDialog();
        int act = getTypeCheckResult();
        if (mDialog != null) {
            mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(act != 0);
        }
    }

    private int getTypeCheckResult() {
        int act = 0;
        for (int i = 0; i < mNetworkTypeNum; i++) {
            if (mCheckState[i]) {
                act += mActArray[i];
            }
        }
        Log.d(TAG, "act = " + act);
        return act;
    }

    public void initCheckState(int act) {
        Log.d(TAG, "init CheckState: " + act);
        if (act > NetworkEditor.RIL_2G_3G_4G || act < NetworkEditor.RIL_2G) {
            return;
        }
        mAct = act;
        for (int i = 0; i < mNetworkTypeNum; i++) {
            mCheckState[i] = (act & mActArray[i]) != 0;
        }
    }
}
