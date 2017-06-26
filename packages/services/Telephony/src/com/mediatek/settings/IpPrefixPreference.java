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

import android.app.ActionBar;
import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;

import com.android.internal.telephony.Phone;
import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneGlobals.SubInfoUpdateListener;
import com.android.phone.R;
import com.android.phone.SubscriptionInfoHelper;
// /added by guofeiyao for HQ01307563
import android.preference.SwitchPreference;
import android.widget.Toast;

import java.lang.Integer;
import java.lang.String;
// /end

public class IpPrefixPreference extends PreferenceActivity
        implements OnPreferenceChangeListener, TextWatcher,
        SubInfoUpdateListener {
    private static final String IP_PREFIX_NUMBER_EDIT_KEY = "button_ip_prefix_edit_key";
    private static final String TAG = "IpPrefixPreference";
    private EditTextPreference mButtonIpPrefix = null;
    private SubscriptionInfoHelper mSubscriptionInfoHelper;
    private Phone mPhone;

    // / Added by guofeiyao
    private static final String G = "_guofeiyao";
    private static final String IP_SWITCH_KEY = "button_ip_switch_key";
    public static final String IP_RADIO = "ip_radio";
    private SwitchPreference ipSwitch;
    // / End

    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.mtk_ip_prefix_setting);
        mButtonIpPrefix = (EditTextPreference) this.findPreference(IP_PREFIX_NUMBER_EDIT_KEY);
        mButtonIpPrefix.setOnPreferenceChangeListener(this);
        // / Added by guofeiyao
        ipSwitch = (SwitchPreference) this.findPreference(IP_SWITCH_KEY);
        ipSwitch.setOnPreferenceChangeListener(this);
        // / End

        mSubscriptionInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        mPhone = mSubscriptionInfoHelper.getPhone();
        if (mPhone == null || (mPhone != null && !TelephonyUtils.isRadioOn(mPhone.getSubId()))) {
            Log.d(TAG, "onCreate, Phone is null, or radio is off, so finish!!!");
            finish();
            return;
        }

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in onOptionsItemSelected()
            actionBar.setDisplayHomeAsUpEnabled(true);
            mSubscriptionInfoHelper.setActionBarTitle(
                    actionBar, getResources(), R.string.ip_prefix_setting_lable);
        }

        PhoneGlobals.getInstance().addSubInfoUpdateListener(this);
        updateIpPrefix();
        // / Added by guofeiyao
        updateIpSwitch();
        // / End
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {

        if (mButtonIpPrefix == preference) {
            mButtonIpPrefix.setSummary(newValue.toString());
            mButtonIpPrefix.setText(newValue.toString());
            if (newValue == null || "".equals(newValue)) {
                mButtonIpPrefix.setSummary(R.string.ip_prefix_edit_default_sum);
            }
            saveIpPrefix(newValue.toString());
        }
        // / Added by guofeiyao
        else if (ipSwitch == preference) {
            Log.d(TAG + G, "IP_SWITCH_checked~~~: " + (boolean) newValue);
            if ((boolean) newValue) {
                String prefix = getIpPrefix();
                if (null == prefix || prefix.length() == 0) {
                    Toast.makeText(this, R.string.ip_prefix_null, Toast.LENGTH_SHORT).show();
                } else {
                    changeIpSwitchValue(true);
                }
            } else {
                changeIpSwitchValue(false);
            }
        }
        // / End
        return false;
    }

    // / Added by guofeiyao
    private void changeIpSwitchValue(boolean b) {
        if (null == ipSwitch) return;
        ipSwitch.setChecked(b);
        saveIpSwitchValue(b);
    }

    private void updateIpSwitch() {
        boolean b = getIpSwitchValue(this, mPhone.getSubId());
        Log.d(TAG + G, "ipswitch_value: " + b);
        ipSwitch.setChecked(b);
    }

    public static boolean getIpSwitchValue(Context context, int subId) {
        String key = IP_SWITCH_KEY + subId;
        Log.d(TAG + G, "IP_SWITCH_KEY~~~: " + key);
        return 1 == Settings.System.getInt(context.getContentResolver(), key, 0);
    }

    private void saveIpSwitchValue(boolean b) {
        Log.d(TAG + G, "ip_switch_save boolean:" + b);
        String key = IP_SWITCH_KEY + mPhone.getSubId();
        Log.d(TAG + G, "ip_switch_save key:" + key);
        int v = 0;
        if (b) {
            v = 1;
        }
        if (!Settings.System.putInt(this.getContentResolver(), key, v)) {
            Log.d(TAG + G, "Store ip prefix error!");
        }
    }
    // / End

    private void updateIpPrefix() {
        String preFix = getIpPrefix();
        Log.d(TAG, "preFix: " + preFix);
        if ((preFix != null) && (!"".equals(preFix))) {
            mButtonIpPrefix.setSummary(preFix);
            mButtonIpPrefix.setText(preFix);
        } else {
            mButtonIpPrefix.setSummary(R.string.ip_prefix_edit_default_sum);
            mButtonIpPrefix.setText("");
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

    private void saveIpPrefix(String str) {
        Log.d(TAG, "save str: " + str);
        String key = getIpPrefixKey();
        if (!Settings.System.putString(this.getContentResolver(), key, str)) {
            Log.d(TAG, "Store ip prefix error!");
        }
    }

    private String getIpPrefix() {
        String key = getIpPrefixKey();
        return Settings.System.getString(this.getContentResolver(), key);
    }

    public void beforeTextChanged(CharSequence s, int start,
            int count, int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    public void afterTextChanged(Editable s) {
    }

    @Override
    protected void onDestroy() {
        PhoneGlobals.getInstance().removeSubInfoUpdateListener(this);
        super.onDestroy();
    }

    /**
     * the prefix value key depends on simId.
     * @return
     */
    private String getIpPrefixKey() {
        String key = "ipprefix";
        key += Integer.valueOf(mPhone.getSubId()).toString();
        Log.d(TAG, "getIpPrefixKey key : " + key);
        return key;
    }

    @Override
    public void handleSubInfoUpdate() {
        finish();
    }
}
