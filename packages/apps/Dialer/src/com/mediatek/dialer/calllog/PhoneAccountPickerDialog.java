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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import android.util.Log;

import com.android.dialer.R;
import com.mediatek.dialer.calllog.PhoneAccountInfoHelper.AccountInfoListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialog use to display and pick a PhoneAccount.
 * To receive the pick result, need to implement {@link #PhoneAccountPickListener}.
 */
public class PhoneAccountPickerDialog extends DialogFragment implements AccountInfoListener {
    private final static String TAG = "PhoneAccountPickerDialog";
    // make it static in case data lost when system re-create the dialogfragment.
    private static List<PhoneAccountPickListener> sListeners = new ArrayList<PhoneAccountPickListener>();
    private static PhoneAccountPickerAdapter sAdapter;
    private static boolean sShowSelection = false;
    private static int sSelection = -1;
    private static PhoneAccountPickerDialog oldInstance = null;

    private PhoneAccountPickerDialog(Context context) {
        sAdapter = new PhoneAccountPickerAdapter(context);
        reset();
    }

    private void reset() {
        // clean listeners of the last instance
        if (sListeners != null) {
            sListeners.clear();
        }
        sShowSelection = false;
        sSelection = -1;
    }

    public PhoneAccountPickerDialog() {
    }

    /**
     * Build a PhoneAccountPickerDialog instance.
     * @param context the context to show the dialog
     * @return instance of PhoneAccountPickerDialog
     */
    public static PhoneAccountPickerDialog build(Context context) {
        /// M: ALPS01897380, dismiss redundant dialog @{
        if (oldInstance != null && oldInstance.getDialog() != null && oldInstance.getDialog().isShowing()) {
            oldInstance.getDialog().dismiss();
        }
        /// @}

        PhoneAccountPickerDialog dialogFragment = new PhoneAccountPickerDialog(context);
        oldInstance = dialogFragment;

        return dialogFragment;
    }

    /**
     * Set data to the Dialog, data is a list of {@link #AccountItem}
     * @param data items to display
     * @return the dialog itself
     */
    public PhoneAccountPickerDialog setData(List<AccountItem> data) {
        sAdapter.setItemData(data);
        return this;
    }

    /**
     * set whether to show selection use radiobutton.
     * @see #setSelection
     * @param showSelection whether to show selection
     * @return the dialog itself
     */
    public PhoneAccountPickerDialog setShowSelection(Boolean showSelection) {
        sAdapter.setShowSelection(showSelection);
        sShowSelection = showSelection;
        return this;
    }

    /**
     * set the current selection if set setShowSelection true.
     * @see #setShowSelection
     * @param selection the current selection, should be the account Id
     * @return the dialog itself
     */
    public PhoneAccountPickerDialog setSelection(int selection) {
        sSelection = selection;
        sAdapter.setSelection(selection);
        return this;
    }

    /**
     * Implement this to receive a pick result.
     *
     */
    public interface PhoneAccountPickListener {
        void onPhoneAccountPicked(String selectId);
    }

    public PhoneAccountPickerDialog addListener(PhoneAccountPickListener listener) {
        if (!sListeners.contains(listener)) {
            sListeners.add(listener);
        }
        return this;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        PhoneAccountInfoHelper.INSTANCE.registerForAccountChange(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.select_account);
        if(sShowSelection) {
            builder.setSingleChoiceItems(sAdapter, sSelection, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    sSelection = which;
                    sAdapter.setSelection(sSelection);
                    sAdapter.notifyDataSetChanged();
                }
            });
        } else {
            builder.setAdapter(sAdapter, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final String selectedId = sAdapter.getAccountId(which);
                    if (sListeners != null && !sListeners.isEmpty() && !TextUtils.isEmpty(selectedId)) {
                        for (PhoneAccountPickListener listener : sListeners) {
                            listener.onPhoneAccountPicked(String.valueOf(selectedId));
                        }
                    }
                    dialog.dismiss();
                }
            });
        }

        builder.setNegativeButton(android.R.string.cancel, null);

        // only need "ok" button when show selection 
        if (sShowSelection) {
            builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final String selectedId = sAdapter.getAccountId(sSelection);
                    if (sListeners != null && !sListeners.isEmpty()
                            && !TextUtils.isEmpty(selectedId)) {
                        for (PhoneAccountPickListener listener : sListeners) {
                            listener.onPhoneAccountPicked(String.valueOf(selectedId));
                        }
                    }
                    dialog.dismiss();
                }
            });
        }

        return builder.create();
    }

    /// CR: ALPS01890207, M: workaround for wrong view for some wired cases @{
    @Override
    public void onResume() {
        super.onResume();
        if (sAdapter == null || sAdapter.isEmpty()) {
            dismiss();
            Log.d(TAG, "dismiss the dialogs when there is no datas");
        }
    }
    /// @}

    @Override
    public void onDestroy() {
        PhoneAccountInfoHelper.INSTANCE.unRegisterForAccountChange(this);
        super.onDestroy();
    }

    /**
     * Data structure to encapsulation account info.
     */
    public static class AccountItem {
        public final PhoneAccountHandle accountHandle;
        public final int title;
        public final int type;
        public final String id;

        /**
         * create a phoneAccount type data Item it will be displayed as
         * icon/name/number
         */
        public AccountItem(PhoneAccountHandle accountHandle) {
            this.type = PhoneAccountPickerAdapter.ITEM_TYPE_ACCOUNT;
            this.accountHandle = accountHandle;
            this.id = accountHandle.getId();
            this.title = 0;
        }

        /**
         * create a text type data item it will be displayed as textview
         */
        public AccountItem(int title, String id) {
            this.type = PhoneAccountPickerAdapter.ITEM_TYPE_TEXT;
            this.title = title;
            this.id = id;
            this.accountHandle = null;
        }
    }

    @Override
    public void onAccountInfoUpdate() {
        // if PhoneAccount info changed, just dismiss the dialog.
        Dialog dialog = getDialog();
        if (dialog != null) {
            dialog.dismiss();
        }
    }

    @Override
    public void onPreferAccountChanged(String id) {
        // Do noting to satisfy AccountInfoListener
    }
}
