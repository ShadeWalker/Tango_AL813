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

package com.mediatek.settings.cdma;

import android.app.ActionBar;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.android.internal.telephony.PhoneConstants;
import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneGlobals.SubInfoUpdateListener;
import com.android.phone.PhoneUtils;
import com.android.phone.R;
import com.android.phone.SubscriptionInfoHelper;
import com.android.services.telephony.TelephonyConnectionService;

/**
 * CDMA Call Option Activity, including CDMA Call forward setting and
 * CDMA addition Call Setting.
 */
public class CdmaAdditionalCallOptions extends PreferenceActivity implements SubInfoUpdateListener {
    private static final String LOG_TAG = "Settings/CdmaAdditionalCallOptions";

    private static final String BUTTON_CW_KEY   = "button_cw_key";

    private static final int DIALOG_CW = 0;

    private static final int GET_CONTACTS = 100;
    private static final String NUM_PROJECTION[] = {Phone.NUMBER};

    private static final String[] CW_HEADERS = {
        SystemProperties.get("ro.cdma.cw.enable"), SystemProperties.get("ro.cdma.cw.disable")
    };

    private Preference mButtonCW;
    private Bundle mBundle;
    private int mSubId = -1;
    private IntentFilter mIntentFilter;
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        /**
         * Receive broadcast intents.
         *
         * @param context the current context.
         * @param intent the broadcast intent.
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                if (intent.getBooleanExtra("state", false)) {
                    finish();
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mBundle = icicle;
        addPreferencesFromResource(R.xml.mtk_cdma_additional_options);

        PreferenceScreen prefSet = getPreferenceScreen();
        mButtonCW = prefSet.findPreference(BUTTON_CW_KEY);
        SubscriptionInfoHelper subInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        mSubId = subInfoHelper.getPhone() != null ?
                subInfoHelper.getPhone().getSubId() : SubscriptionInfoHelper.NO_SUB_ID;

        Log.d(LOG_TAG, "[onCreate][mSlot = " + mSubId + "]");
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in onOptionsItemSelected()
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        // For hot swap
        PhoneGlobals.getInstance().addSubInfoUpdateListener(this);
        registerCallBacks();
        if (!PhoneUtils.isValidSubId(mSubId)) {
            finish();
            Log.d(LOG_TAG, "onCreate, mSubId is invalid = " + mSubId);
            return;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        switch (itemId) {
        case android.R.id.home:
            finish();
            return true;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mButtonCW) {
            showDialog(DIALOG_CW);
        }
        return true;
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.mtk_cdma_cf_dialog);
        dialog.setTitle(mButtonCW.getTitle());

        final RadioGroup radioGroup = (RadioGroup) dialog.findViewById(R.id.group);

        final TextView textView = (TextView) dialog.findViewById(R.id.dialog_sum);
        if (textView != null) {
            textView.setVisibility(View.GONE);
        } else {
            Log.d(LOG_TAG, "--------------[text view is null]---------------");
        }

        EditText edittext = (EditText) dialog.findViewById(R.id.EditNumber);
        if (edittext != null) {
            edittext.setVisibility(View.GONE);
        }

        ImageButton addContactBtn = (ImageButton) dialog.findViewById(R.id.select_contact);
        if (addContactBtn != null) {
            addContactBtn.setVisibility(View.GONE);
        }

        Button dialogSaveBtn = (Button) dialog.findViewById(R.id.save);
        if (dialogSaveBtn != null) {
            dialogSaveBtn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (radioGroup.getCheckedRadioButtonId() == -1) {
                        dialog.dismiss();
                        return;
                    }
                    int radioSel = radioGroup.getCheckedRadioButtonId() == R.id.enable ? 0 : 1;
                    int cfType = id * 2 + radioSel;
                    String cf = CW_HEADERS[cfType];
                    dialog.dismiss();
                    setCallForward(cf);
                }
            });
        }

        Button dialogCancelBtn = (Button) dialog.findViewById(R.id.cancel);
        if (dialogCancelBtn != null) {
                dialogCancelBtn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }
        return dialog;
    }

    private void setCallForward(String cf) {
        Log.d(LOG_TAG, "[setCallForward][cf = " + cf + "]");
        if (mSubId == -1 || cf == null || cf.isEmpty()) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:" + cf));
        ComponentName pstnConnectionServiceName =
            new ComponentName(this, TelephonyConnectionService.class);
        String id = "" + String.valueOf(mSubId);
        PhoneAccountHandle phoneAccountHandle =
            new PhoneAccountHandle(pstnConnectionServiceName, id);
        intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
        startActivity(intent);
    }

    @Override
    public void handleSubInfoUpdate() {
        Log.d(LOG_TAG, "handleSubInfoUpdate");
        finish();
    }

    /**
     * Destroy the preference activity.
     */
    @Override
    public void onDestroy() {
        unregisterReceiver(mReceiver);
        PhoneGlobals.getInstance().removeSubInfoUpdateListener(this);
        super.onDestroy();
    }

    /**
     * Register the callbacks for hot swap.
     */
    private void registerCallBacks() {
        mIntentFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        registerReceiver(mReceiver, mIntentFilter);
    }

}
