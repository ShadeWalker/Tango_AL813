/*
 * Copyright (C) 2011-2014 MediaTek Inc.
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

package com.mediatek.dialer.calllog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;

import com.android.internal.telephony.TelephonyIntents;
import com.mediatek.telecom.TelecomManagerEx;

import java.util.ArrayList;
import java.util.List;

public class PhoneAccountInfoHelper {
    /**
     * Key for get and save the Call Log filter value.
     */
    private static final String CALL_LOG_FILTER_KEY = "call_log_filter";

    /**
     * For Call Log Filter, means display call log from all accounts
     */
    public static final String FILTER_ALL_ACCOUNT_ID = "all_account";

    public static final PhoneAccountInfoHelper INSTANCE = new PhoneAccountInfoHelper();
    private Context mContext;
    private List<AccountInfoListener> mListeners = new ArrayList<AccountInfoListener>();

    /**
     * interface for Classes need to observe PhoneAccount info update
     */
    public interface AccountInfoListener {
        void onAccountInfoUpdate();

        void onPreferAccountChanged(String id);
    }

    /**
     * register a listener for phoneAccount info change.
     * @param listener
     */
    public void registerForAccountChange(AccountInfoListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void unRegisterForAccountChange(AccountInfoListener listener) {
        mListeners.remove(listener);
    }

    /**
     * initialize the info helper to receive PhoneAccount info update.
     * @param context  the application context
     */
    public void init(Context context) {
        mContext = context;
        IntentFilter intentFilter =
            new IntentFilter(TelecomManagerEx.ACTION_PHONE_ACCOUNT_CHANGED);
        mContext.registerReceiver(mReceiver, intentFilter);
        ensurePerferAccountAvailable(mContext);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelecomManagerEx.ACTION_PHONE_ACCOUNT_CHANGED.equals(action)) {
                notifyAccountInfoUpdate();
                ensurePerferAccountAvailable(context);
            }
        }

    };

    private void ensurePerferAccountAvailable(Context context) {
        String preferAccountId = getPreferAccountId();
        if (FILTER_ALL_ACCOUNT_ID.equals(preferAccountId)) {
            return;
        }
        boolean preferAccountRemoved = true;

        TelecomManager telecomManager = (TelecomManager) context
                .getSystemService(Context.TELECOM_SERVICE);
        List<PhoneAccountHandle> handles = telecomManager.getCallCapablePhoneAccounts();
        for (PhoneAccountHandle handle : handles) {
            if (handle.getId().equals(preferAccountId)) {
                preferAccountRemoved = false;
                break;
            }
        }
        // if the selected account is removed or only one account left, show all accounts
        if (preferAccountRemoved || !telecomManager.hasMultipleCallCapableAccounts()) {
            setPreferAccountId(FILTER_ALL_ACCOUNT_ID);
        }
    }

    /**
     * Get the filter value for Call Logs filter.
     *
     * @param context
     * @return {@link FILTER_ALL_ACCOUNT_ID}, PhoneAccount Id
     */
    public String getPreferAccountId() {
        if (mContext != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            return prefs.getString(CALL_LOG_FILTER_KEY, FILTER_ALL_ACCOUNT_ID);
        }
        return FILTER_ALL_ACCOUNT_ID;
    }

    /**
     * Save the user selected value for Call Logs filter.
     *
     * @param context
     * @param id {@link FILTER_ALL_ACCOUNT_ID}, PhoneAccount Id
     */
    public void setPreferAccountId(String id) {
        if (mContext != null && !getPreferAccountId().equals(id)) {
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(
                    mContext).edit();
            editor.putString(CALL_LOG_FILTER_KEY, id);
            editor.commit();

            notifyPreferAccountChange(id);
        }
    }

    private void notifyAccountInfoUpdate() {
        for (AccountInfoListener listener : mListeners) {
            listener.onAccountInfoUpdate();
        }
    }

    private void notifyPreferAccountChange(String id) {
        for (AccountInfoListener listener : mListeners) {
            listener.onPreferAccountChanged(id);
        }
    }

}
