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
 * limitations under the License
 */

package com.android.contacts.common.widget;

import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.android.contacts.common.R;
import com.mediatek.telecom.TelecomManagerEx;
import android.provider.Settings;

import java.util.List;

/**
 * Dialog that allows the user to select a phone accounts for a given action. Optionally provides
 * the choice to set the phone account as default.
 */
public class SelectPhoneAccountDialogFragment extends DialogFragment {
    private int mTitleResId;
    private boolean mCanSetDefault;
    private List<PhoneAccountHandle> mAccountHandles;
    private boolean mIsSelected;
    private boolean mIsDefaultChecked;
    private TelecomManager mTelecomManager;
    private SelectPhoneAccountListener mListener;

    /**
     * Shows the account selection dialog.
     * This is the preferred way to show this dialog.
     *
     * @param fragmentManager The fragment manager.
     * @param accountHandles The {@code PhoneAccountHandle}s available to select from.
     * @param listener The listener for the results of the account selection.
     */
    public static void showAccountDialog(FragmentManager fragmentManager,
            List<PhoneAccountHandle> accountHandles, SelectPhoneAccountListener listener) {
        showAccountDialog(fragmentManager, R.string.select_account_dialog_title, false,
                accountHandles, listener);
    }

    /**
     * Shows the account selection dialog.
     * This is the preferred way to show this dialog.
     * This method also allows specifying a custom title and "set default" checkbox.
     *
     * @param fragmentManager The fragment manager.
     * @param titleResId The resource ID for the string to use in the title of the dialog.
     * @param canSetDefault {@code true} if the dialog should include an option to set the selection
     * as the default. False otherwise.
     * @param accountHandles The {@code PhoneAccountHandle}s available to select from.
     * @param listener The listener for the results of the account selection.
     */
    public static void showAccountDialog(FragmentManager fragmentManager, int titleResId,
            boolean canSetDefault, List<PhoneAccountHandle> accountHandles,
            SelectPhoneAccountListener listener) {
        SelectPhoneAccountDialogFragment fragment =
                new SelectPhoneAccountDialogFragment(
                        titleResId, canSetDefault, accountHandles, listener);
        fragment.show(fragmentManager, "selectAccount");
    }

    public SelectPhoneAccountDialogFragment(int titleResId, boolean canSetDefault,
            List<PhoneAccountHandle> accountHandles, SelectPhoneAccountListener listener) {
        super();
        mTitleResId = titleResId;
        mCanSetDefault = canSetDefault;
        mAccountHandles = accountHandles;
        mListener = listener;
    }

    public interface SelectPhoneAccountListener {
        void onPhoneAccountSelected(PhoneAccountHandle selectedAccountHandle, boolean setDefault);
        void onDialogDismissed();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mIsSelected = false;
        mIsDefaultChecked = false;
        mTelecomManager =
                (TelecomManager) getActivity().getSystemService(Context.TELECOM_SERVICE);

        final DialogInterface.OnClickListener selectionListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mIsSelected = true;
                PhoneAccountHandle selectedAccountHandle = mAccountHandles.get(which);
			   //add by zhangjinqiang for call sim select --start
			   Log.d("whick",which+"");
			   Settings.System.putInt(getActivity().getApplicationContext().getContentResolver(), "slot_id", which);
			   //add by zhangjinqiang end
                mListener.onPhoneAccountSelected(selectedAccountHandle, mIsDefaultChecked);
            }
        };

        final CompoundButton.OnCheckedChangeListener checkListener =
                new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton check, boolean isChecked) {
                mIsDefaultChecked = isChecked;
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        ListAdapter selectAccountListAdapter = new SelectAccountListAdapter(
                builder.getContext(),
                R.layout.select_account_list_item,
                mAccountHandles);

        AlertDialog dialog = builder.setTitle(mTitleResId)
                .setAdapter(selectAccountListAdapter, selectionListener)
                .create();

        if (mCanSetDefault) {
            // Generate custom checkbox view
            LinearLayout checkboxLayout = (LinearLayout) getActivity()
                    .getLayoutInflater()
                    .inflate(R.layout.default_account_checkbox, null);

            CheckBox cb =
                    (CheckBox) checkboxLayout.findViewById(R.id.default_account_checkbox_view);
            cb.setOnCheckedChangeListener(checkListener);

            dialog.getListView().addFooterView(checkboxLayout);
        }

        /// M: add for ALPS01831543, dismiss the dialog if plug out/in SIM cards. @{
        registerIntentFilter();
        /// @}

        return dialog;
    }

    private class SelectAccountListAdapter extends ArrayAdapter<PhoneAccountHandle> {
        private int mResId;

        public SelectAccountListAdapter(
                Context context, int resource, List<PhoneAccountHandle> accountHandles) {
            super(context, resource, accountHandles);
            mResId = resource;
        }

        @Override
        public boolean isEnabled(int position) {
            PhoneAccount account = mTelecomManager.getPhoneAccount(getItem(position));
            if (account.hasCapabilities(PhoneAccount.CAPABILITY_UNAVAILABLE_FOR_CALL)) {
                return false;
            }
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater)
                    getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View rowView;
            final ViewHolder holder;

            if (convertView == null) {
                // Cache views for faster scrolling
                rowView = inflater.inflate(mResId, null);
                holder = new ViewHolder();
                holder.labelTextView = (TextView) rowView.findViewById(R.id.label);
                holder.numberTextView = (TextView) rowView.findViewById(R.id.number);
                holder.imageView = (ImageView) rowView.findViewById(R.id.icon);
                /// M: Added for some mtk features. @{
                holder.customView = (View) rowView.findViewById(R.id.custom_view);
                /// @}
                rowView.setTag(holder);
            }
            else {
                rowView = convertView;
                holder = (ViewHolder) rowView.getTag();
            }

            PhoneAccountHandle accountHandle = getItem(position);
            PhoneAccount account = mTelecomManager.getPhoneAccount(accountHandle);

            /// M: For ALPS01949499. @{
            // Before show dialog, the PhoneAccountHandle maybe changed and no PhoneAccount
            // for this, so do nothing at here.
            if (account == null) {
                Log.d(TAG, "getView, PhoneAccount has been removed, do nothing.");
                return rowView;
            }
            /// @}

            holder.labelTextView.setText(account.getLabel());
            if (account.getAddress() == null ||
                    TextUtils.isEmpty(account.getAddress().getSchemeSpecificPart())) {
                holder.numberTextView.setVisibility(View.GONE);
            } else {
                holder.numberTextView.setVisibility(View.VISIBLE);
                holder.numberTextView.setText(
                        PhoneNumberUtils.ttsSpanAsPhoneNumber(
                                account.getAddress().getSchemeSpecificPart()));
            }
            holder.imageView.setImageDrawable(account.createIconDrawable(getContext()));
            ///M: if has cdma account and the default data not set on this current account
            // we need to disable this account for user selection. @{
            if (account.hasCapabilities(PhoneAccount.CAPABILITY_UNAVAILABLE_FOR_CALL)) {
                rowView.setAlpha(0.45f);
                rowView.setBackgroundColor(android.R.color.darker_gray);
            } else{
                rowView.setAlpha(1.0f);
                rowView.setBackgroundColor(android.R.color.white);
            }
            /// @}

            /// M: Added for some mtk features. @{
            setCustomView(holder, account);
            ///  @}
            return rowView;
        }

        private class ViewHolder {
            TextView labelTextView;
            TextView numberTextView;
            ImageView imageView;
            /// M: Added for some mtk features. @{
            View customView;
            /// @}
        }

        /**
         * Show some custom view if needs.
         * @param holder
         * @param phoneAccount
         */
        private void setCustomView(ViewHolder holder, PhoneAccount phoneAccount) {
            PhoneAccountHandle phoneAccountHandle = phoneAccount.getAccountHandle();
            if (mSuggestedAccountHandle != null
                    && mSuggestedAccountHandle.equals(phoneAccountHandle)) {
                ImageView suggestedView = (ImageView) holder.customView
                        .findViewById(R.id.suggestedView);
                // Show suggested icon.
                holder.customView.setVisibility(View.VISIBLE);
                suggestedView.setVisibility(View.VISIBLE);
            } else {
                holder.customView.setVisibility(View.GONE);
            }
        }
        /// @}
    }

    @Override
    public void onPause() {
        if (!mIsSelected) {
            mListener.onDialogDismissed();
            /**
             * M: [ALPS01959895]if no selection, dismiss the dialog anyway.
             * The dialog will not appear again if screen rotation or press home key.
             */
            dismiss();
        }
        super.onPause();
    }

    /// --------------------------------------------- Meidatek ------------------------------------

    /**
     * For ALPS01931044.
     * Default constructor. Every fragment must have an empty constructor, so it can be instantiated
     * when restoring its activity's state.
     */
    public SelectPhoneAccountDialogFragment() {
    }

    private static final String TAG = "SelectPhoneAccountDialogFragment";
    private Context mContext;
    // Used for set suggestion PhoneAccountHandle while dialing a call from Call Log.
    private PhoneAccountHandle mSuggestedAccountHandle;

    /**
     * Register some IntentFilter for MTK feature.
     */
    private void registerIntentFilter() {
        if (getActivity() != null) {
            mContext = getActivity();
            IntentFilter intentFilter = new IntentFilter(
                    TelecomManagerEx.ACTION_PHONE_ACCOUNT_CHANGED);
            mContext.registerReceiver(mReceiver, intentFilter);
        }
    }

    /**
     * Dismiss the dialog if plug out/in SIM cards.
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // check the dialog whether dismissed
            if (getDialog() != null) {
                Log.d(TAG, "onReceive, PhoneAccount changed, dismiss current dialog.");
                getDialog().dismiss();
            }
        }
    };

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        // Unregister receiver for MTK feature.
        Log.d(TAG, "onDismiss, mContext : " + mContext);
        if (mContext != null) {
            mContext.unregisterReceiver(mReceiver);
            mContext = null;
        }

        // for suggesting phone account feature.
        mSuggestedAccountHandle = null;
    }

    /**
     * Shows the account selection dialog.
     * This is the preferred way to show this dialog.
     * This method also allows specifying a custom title and "set default" checkbox.
     *
     * @param fragmentManager The fragment manager.
     * @param titleResId The resource ID for the string to use in the title of the dialog.
     * @param canSetDefault {@code true} if the dialog should include an option to set the selection
     * as the default. False otherwise.
     * @param accountHandles The {@code PhoneAccountHandle}s available to select from.
     * @param listener The listener for the results of the account selection.
     * @param suggestedAccountHandle The suggested account.
     */
    public static void showAccountDialog(FragmentManager fragmentManager, int titleResId,
            boolean canSetDefault, List<PhoneAccountHandle> accountHandles,
            SelectPhoneAccountListener listener, PhoneAccountHandle suggestedAccountHandle) {
        SelectPhoneAccountDialogFragment fragment = new SelectPhoneAccountDialogFragment(
                titleResId, canSetDefault, accountHandles, listener, suggestedAccountHandle);
        fragment.show(fragmentManager, "selectAccount");
    }

    /**
     * Creates a select PhoneAccount dialog fragment.
     * @param titleResId The resource ID for the string to use in the title of the dialog.
     * @param canSetDefault {@code true} if the dialog should include an option to set the selection
     * as the default. False otherwise.
     * @param accountHandles The {@code PhoneAccountHandle}s available to select from.
     * @param listener The listener for the results of the account selection.
     * @param suggestedAccountHandle The suggested account.
     */
    public SelectPhoneAccountDialogFragment(int titleResId, boolean canSetDefault,
            List<PhoneAccountHandle> accountHandles, SelectPhoneAccountListener listener,
            PhoneAccountHandle suggestedAccountHandle) {
        this(titleResId, canSetDefault, accountHandles, listener);
        mSuggestedAccountHandle = suggestedAccountHandle;
    }
}
