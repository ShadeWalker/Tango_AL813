/*
* Copyright (C) 2014 The Android Open Source Project
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

package com.mediatek.audioprofile;

import android.app.Activity;
import android.app.ListFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

import com.mediatek.widget.AccountViewAdapter;
import com.mediatek.widget.AccountViewAdapter.AccountElements;

import java.util.ArrayList;
import java.util.List;

/**
 * An list fragment to show active subscription.
 *
 */
public class SubSelectSettings extends ListFragment {

    private static final String TAG = "SubSelectSettings";
    private AccountViewAdapter mAdapter;
    private List<SubscriptionInfo> mSubInfoList;
    private List<Integer> mSlotIdList = new ArrayList<Integer>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Customize title if intent with extra
        String title = getActivity().getIntent().getStringExtra(Intent.EXTRA_TITLE);
        if (!TextUtils.isEmpty(title)) {
            getActivity().setTitle(title);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mSubInfoList = SubscriptionManager.from(getActivity()).getActiveSubscriptionInfoList();
        //Handle SIM Hot plug in/out case, when creating view possible there is no SIM available
        if (mSubInfoList != null && mSubInfoList.size() > 0) {
            setListAdapter(getAdapter(mSubInfoList));
        } else {
            getActivity().finish();
        }
    }

    private ListAdapter getAdapter(List<SubscriptionInfo> subInfoList) {
        if (mAdapter == null) {
            mAdapter = new AccountViewAdapter(getActivity(), getAccountsData(subInfoList));
        } else {
            mAdapter.updateData(getAccountsData(subInfoList));
        }
        return mAdapter;
    }

    private List<AccountElements> getAccountsData(
            List<SubscriptionInfo> subInfoList) {
        List<AccountElements> accounts = new ArrayList<AccountElements>();
        for (SubscriptionInfo record : subInfoList) {
            //FIXME Later on mSimIconRes, will be replaced by one integer, not an array
            accounts.add(new AccountElements(record.getIconTint(), record.getDisplayName().toString(),
                                             record.getNumber()));
        }
        return accounts;
    }

    @Override
    public void onListItemClick(ListView listView, View v, int position, long id) {
        Intent intent = new Intent();
        SubscriptionInfo record = mSubInfoList.get(position);
        long subId = record.getSubscriptionId();
        int slotId = record.getSimSlotIndex();
        Log.d(TAG, "onListItemClick with slotId = " + slotId + " subId = " + subId);
        intent.putExtra(PhoneConstants.SLOT_KEY, slotId);
        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);
        getActivity().setResult(Activity.RESULT_OK, intent);
        getActivity().finish();
    }
}
