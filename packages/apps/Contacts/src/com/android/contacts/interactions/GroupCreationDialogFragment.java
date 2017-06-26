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
 * limitations under the License.
 */
package com.android.contacts.interactions;

import android.app.Activity;

import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract.Groups;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.account.AccountType;

import com.mediatek.contacts.util.AccountTypeUtils;
import com.mediatek.contacts.util.ContactsConstants;


/**
 * A dialog for creating a new group.
 */
public class GroupCreationDialogFragment extends GroupNameDialogFragment {
    private static final String ARG_ACCOUNT_TYPE = "accountType";
    private static final String ARG_ACCOUNT_NAME = "accountName";
    private static final String ARG_DATA_SET = "dataSet";

    public static final String FRAGMENT_TAG = "createGroupDialog";

    private final OnGroupCreatedListener mListener;

    /// M: change for CR ALPS00945678.
    private EditText mEditText = null;

    public interface OnGroupCreatedListener {
        public void onGroupCreated();
    }

    /** M: change for CR ALPS00784408 Added param rawContactId @{*/
    public static void show(
            FragmentManager fragmentManager, String accountType, String accountName,
            String dataSet, long rawContactId, OnGroupCreatedListener listener) {
        Bundle args = new Bundle();
        initDialogConfig(fragmentManager, accountType, accountName, dataSet, rawContactId, listener, args);
    }
    /** M: for CR ALPS01752048 @{*/
    public static void show(
            FragmentManager fragmentManager, String accountType, String accountName,
            String dataSet, long rawContactId, OnGroupCreatedListener listener, int subId) {
        Bundle args = new Bundle();
        args.putInt(ContactsConstants.SUB_ID, subId);
        initDialogConfig(fragmentManager, accountType, accountName, dataSet, rawContactId, listener, args);
    }
    /** @}*/
    private static void initDialogConfig(FragmentManager fragmentManager, String accountType,
            String accountName, String dataSet, long rawContactId, OnGroupCreatedListener listener, Bundle args) {
        GroupCreationDialogFragment dialog = new GroupCreationDialogFragment(listener);

        args.putString(ARG_ACCOUNT_TYPE, accountType);
        args.putString(ARG_ACCOUNT_NAME, accountName);
        args.putString(ARG_DATA_SET, dataSet);
        args.putLong(ContactsConstants.RAW_CONTACT_ID, rawContactId);
        dialog.setArguments(args);
        dialog.show(fragmentManager, FRAGMENT_TAG);
    }

    public GroupCreationDialogFragment() {
        super();
        mListener = null;
    }

    private GroupCreationDialogFragment(OnGroupCreatedListener listener) {
        super();
        mListener = listener;
    }

    public OnGroupCreatedListener getOnGroupCreatedListener() {
        return mListener;
    }

    @Override
    protected void initializeGroupLabelEditText(EditText editText) {
        mEditText = editText;
    }

    @Override
    protected int getTitleResourceId() {
        return R.string.create_group_dialog_title;
    }

    @Override
    protected void onCompleted(String groupLabel) {
        Bundle arguments = getArguments();
        String accountType = arguments.getString(ARG_ACCOUNT_TYPE);
        String accountName = arguments.getString(ARG_ACCOUNT_NAME);
        String dataSet = arguments.getString(ARG_DATA_SET);

        /** M:change for CR ALPS00784408 and ALPS00955866. @{ */
        long rawCotnactId = arguments.getLong(ContactsConstants.RAW_CONTACT_ID);
        int simIndex = arguments.getInt(ContactsConstants.RAW_CONTACT_SIM_INDEX, -1);
        long[] membersToAddArray = new long[RAW_CONTACT_COUNT];
        membersToAddArray[0] = rawCotnactId;
        int[] simIndexArray = new int[RAW_CONTACT_COUNT];
        simIndexArray[0] = simIndex;
        /**@}*/
        /** for CR ALPS01752048 @{*/
        int subId = arguments.getInt(ContactsConstants.SUB_ID, -1);
        /**@}*/
        // Indicate to the listener that a new group will be created.
        // If the device is rotated, mListener will become null, so that the
        // popup from GroupMembershipView will not be shown.
        if (mListener != null) {
            mListener.onGroupCreated();
        }
        /// M: add for ALPS00118978
        if (!checkName(groupLabel, accountType, accountName)) {
            Log.w(TAG, "onCompleted() checkName failed,return!");
            return;
        }
        Activity activity = getActivity();
        /** M:change for CR ALPS00784408 and ALPS00955866. */
        activity.startService(ContactSaveService.createNewGroupIntentForIcc(activity,
                new AccountWithDataSet(accountName, accountType, dataSet), groupLabel,
                membersToAddArray,
                activity.getClass(), Intent.ACTION_INSERT,
                simIndexArray, subId));
    }

    private static String TAG = "GroupNameDialogFragment";
    private Context mContext;
    public boolean checkName(CharSequence name, String accountType, String accountName) {
        mContext = this.getActivity();

        /** M: fixed CR ALPS00743567 @{ */
        if (mContext == null) {
            Log.w(TAG, "checkName() mContext is null,return!");
            Toast.makeText(mApplicationContext, R.string.save_group_fail, Toast.LENGTH_SHORT).show();
            return false;
        }
        /** @} */

        Log.d(TAG, "checkName begiin" + name);
        if (TextUtils.isEmpty(name)) {
            Log.d(TAG, "checkName() name is empty.");
            Toast.makeText(mContext, R.string.name_needed, Toast.LENGTH_SHORT).show();
            return false;
        }

        /// M: Modify CR ID :ALP00S110925/ALPS00118978,It should not save %,％ or / as group name
        // M: cancel this code section to support special char "/" or "%" as group name.
        // CR ID:ALPS00943770
        /*if(name.toString().contains("/") || name.toString().contains("%")) {
            Log.i(TAG, "checkName() name format error.");
            Toast.makeText(mContext, R.string.save_group_fail, Toast.LENGTH_SHORT).show();
            return false;
        }*/
        boolean nameExists = false;
        // check group name in DB
        Log.d(TAG, accountName + "--accountName");
        Log.d(TAG, accountType + "--accountType");

        /** M: fix bug for 360 import contacts start @ { */
        if (TextUtils.isEmpty(accountName) || TextUtils.isEmpty(accountType)) {
            accountName = AccountTypeUtils.ACCOUNT_NAME_LOCAL_PHONE;
            accountType = AccountTypeUtils.ACCOUNT_TYPE_LOCAL_PHONE;
        }
        /** @ } */

        if (!nameExists) {
            Cursor cursor = mContext.getContentResolver().query(
                    Groups.CONTENT_SUMMARY_URI,
                    new String[] { Groups._ID },
                    Groups.TITLE + "=? AND " + Groups.ACCOUNT_NAME + " =? AND " +
                    Groups.ACCOUNT_TYPE + "=? AND " + Groups.DELETED + "=0",
                    new String[] { name.toString(), accountName, accountType}, null);
            Log.d(TAG, cursor.getCount() + "--cursor.getCount()");
            if (cursor == null || cursor.getCount() == 0) {
                Log.d(TAG, "checkName() cursor have no data");
                if (cursor != null) {
                    cursor.close();
                }
            } else {
                cursor.close();
                nameExists = true;
            }
        }
        //If group name exists, make a toast and return false.
        if (nameExists) {
            Toast.makeText(mContext,
                    R.string.group_name_exists, Toast.LENGTH_SHORT).show();
            ///M:fix CR ALPS00945678,about click phone contact "create new group",show "the group name has exist",the keyboard does not disappear
            InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mApplicationContext = activity.getApplicationContext();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()!");
    }

    private Context mApplicationContext;

    /** M: to create new group with arguments for the rawcontact.@{ */
    private static final int RAW_CONTACT_COUNT = 1;
    /**@}*/
}
