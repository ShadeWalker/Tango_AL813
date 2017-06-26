/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2010 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.contacts.common.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.AccountsListAdapter;
import com.android.contacts.common.util.AccountsListAdapter.AccountListFilter;

import com.android.internal.telephony.TelephonyIntents;
import com.mediatek.contacts.model.AccountWithDataSetEx;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.simcontact.SubInfoUtils;

/**
 * Shows a dialog asking the user which account to chose.
 *
 * The result is passed to {@code targetFragment} passed to {@link #show}.
 */
public final class SelectAccountDialogFragment extends DialogFragment {
    public static final String TAG = "SelectAccountDialogFragment";

    private static final String KEY_TITLE_RES_ID = "title_res_id";
    private static final String KEY_LIST_FILTER = "list_filter";
    private static final String KEY_EXTRA_ARGS = "extra_args";

    public SelectAccountDialogFragment() { // All fragments must have a public default constructor.
    }

    /**
     * Show the dialog.
     *
     * @param fragmentManager {@link FragmentManager}.
     * @param targetFragment {@link Fragment} that implements {@link Listener}.
     * @param titleResourceId resource ID to use as the title.
     * @param accountListFilter account filter.
     * @param extraArgs Extra arguments, which will later be passed to
     *     {@link Listener#onAccountChosen}.  {@code null} will be converted to
     *     {@link Bundle#EMPTY}.
     */
    public static <F extends Fragment & Listener> void show(FragmentManager fragmentManager,
            F targetFragment, int titleResourceId,
            AccountListFilter accountListFilter, Bundle extraArgs) {
        final Bundle args = new Bundle();
        args.putInt(KEY_TITLE_RES_ID, titleResourceId);
        args.putSerializable(KEY_LIST_FILTER, accountListFilter);
        args.putBundle(KEY_EXTRA_ARGS, (extraArgs == null) ? Bundle.EMPTY : extraArgs);

        final SelectAccountDialogFragment instance = new SelectAccountDialogFragment();
        instance.setArguments(args);
        instance.setTargetFragment(targetFragment, 0);
        instance.show(fragmentManager, null);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        /** M: Bug Fix for ALPS00402174 @{ */
        mActivity = getActivity();
        /** @} */
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final Bundle args = getArguments();

        final AccountListFilter filter = (AccountListFilter) args.getSerializable(KEY_LIST_FILTER);
        final AccountsListAdapter accountAdapter = new AccountsListAdapter(builder.getContext(),
                filter);

        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                /** M: New feature by Mediatek Begin @{
                 * Original Android code:
                 * onAccountSelected(accountAdapter.getItem(which));
                 */
                mAccount = accountAdapter.getItem(which);
                if (mAccount instanceof AccountWithDataSetEx) {
                    mAccount = (AccountWithDataSetEx) accountAdapter.getItem(which);
                    mSubId = ((AccountWithDataSetEx) mAccount).getSubId();
                }
                if (mSubId > 0) {
                    /** M: Change for PHB Status refactoring. @{ */
                    if (!SimCardUtils.isPhoneBookReady(mSubId)) {
                        if (mActivity != null) {
                            mActivity.finish();
                        }
                        LogUtils.w(TAG, "[onClick]PhoneBook is not ready for use");
                    } else {
                        onAccountSelected(mAccount);
                    }
                    /** @} */
                } else {
                    LogUtils.w(TAG, "[onCreateDialog]subId is invalid: mSubId = " + mSubId);
                    onAccountSelected(mAccount);
                }
                /** M: @} */
            }
        };

        builder.setTitle(args.getInt(KEY_TITLE_RES_ID));
        builder.setSingleChoiceItems(accountAdapter, 0, clickListener);
        final AlertDialog result = builder.create();
        return result;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        final Fragment targetFragment = getTargetFragment();
        if (targetFragment != null && targetFragment instanceof Listener) {
            final Listener target = (Listener) targetFragment;
            target.onAccountSelectorCancelled();
        }
    }

    /**
     * Calls {@link Listener#onAccountChosen} of {@code targetFragment}.
     */
    private void onAccountSelected(AccountWithDataSet account) {
        final Fragment targetFragment = getTargetFragment();
        if (targetFragment != null && targetFragment instanceof Listener) {
            final Listener target = (Listener) targetFragment;
            target.onAccountChosen(account, getArguments().getBundle(KEY_EXTRA_ARGS));
        }
    }

    public interface Listener {
        void onAccountChosen(AccountWithDataSet account, Bundle extraArgs);
        void onAccountSelectorCancelled();
    }

    /// M :register to listen the PHB state change @{
    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (mReceiver == null) {
            mReceiver = new SimReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(TelephonyIntents.ACTION_PHB_STATE_CHANGED);
            getActivity().registerReceiver(mReceiver, filter);
            LogUtils.i(TAG, "[onCreate]registerReceiver mReceiver");
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDetach() {
        if (mReceiver != null) {
            getActivity().unregisterReceiver(mReceiver);
            mReceiver = null;
            LogUtils.i(TAG, "[onDetach]unregisterReceiver mReceiver");
        }
        super.onDetach();
    }

    private int mSubId = -1;
    private AccountWithDataSet mAccount;
    private Activity mActivity;
    private SimReceiver mReceiver;

    class SimReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            LogUtils.i(TAG, "[onReceive]action is: " + action);
            if (TelephonyIntents.ACTION_PHB_STATE_CHANGED.equals(action)) {
                if (SubInfoUtils.getActivatedSubInfoCount() > 0) {
                    LogUtils.i(TAG, "[onReceive] activity finsh,activity is: " + getActivity());
                    getActivity().finish();
                }
            }
        }
    }
    /// M: @}
}
