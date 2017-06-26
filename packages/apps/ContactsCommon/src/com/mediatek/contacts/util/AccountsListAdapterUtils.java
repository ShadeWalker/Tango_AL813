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

package com.mediatek.contacts.util;

import android.content.Context;
import android.os.UserHandle;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.AccountsListAdapter.AccountListFilter;

import com.mediatek.contacts.ContactsSystemProperties;
import com.mediatek.contacts.model.AccountWithDataSetEx;
import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.simcontact.SlotUtils;
import com.mediatek.contacts.simcontact.SubInfoUtils;
import com.mediatek.contacts.util.LogUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * List-Adapter for Account selection
 */
public final class AccountsListAdapterUtils {
    private static final String TAG = AccountsListAdapterUtils.class.getSimpleName();

    /**
     * @param accountTypeManager Account type manager.
     */
    public static ArrayList<AccountWithDataSet> getGroupAccount(
            AccountTypeManager accountTypeManager) {
        List<AccountWithDataSet> accountList = accountTypeManager.getGroupWritableAccounts();
        List<AccountWithDataSet> newAccountList = new ArrayList<AccountWithDataSet>();
        LogUtils.d(TAG, "[getAccounts]accountList size:" + accountList.size());
        for (AccountWithDataSet account : accountList) {
            if (account instanceof AccountWithDataSetEx) {
                int subId = ((AccountWithDataSetEx) account).getSubId();
                LogUtils.d(TAG, "[getAccounts]subId:" + subId);
                // For MTK multiuser in 3gdatasms @{
                if (ContactsSystemProperties.MTK_OWNER_SIM_SUPPORT) {
                    int userId = UserHandle.myUserId();
                    AccountType accountType = accountTypeManager.getAccountType(
                        account.type, account.dataSet);
                    if (userId != UserHandle.USER_OWNER) {
                        if (accountType != null && accountType.isIccCardAccount()) {
                            continue;
                        }
                    }
                }

                if (SimCardUtils.isSimUsimType(subId)) {
                    LogUtils.d(TAG, "[getAccounts]getUSIMGrpMaxNameLen:"
                            + SlotUtils.getUsimGroupMaxNameLength(subId));
                    if (SlotUtils.getUsimGroupMaxNameLength(subId) > 0) {
                        newAccountList.add(account);
                    }
                } else {
                    newAccountList.add(account);
                }
            } else {
                newAccountList.add(account);
            }
        }
        return new ArrayList<AccountWithDataSet>(newAccountList);
    }

    /**
     * @param accountTypeManager Account type manager
     * @param accountListFilter the filter of the account type
     */
    public static ArrayList<AccountWithDataSet> getAccountForMultiUser(
        AccountTypeManager accountTypeManager, AccountListFilter accountListFilter) {
        if (ContactsSystemProperties.MTK_OWNER_SIM_SUPPORT) {
            int userId = UserHandle.myUserId();
            if (userId != UserHandle.USER_OWNER) {
                if (accountListFilter == AccountListFilter.ACCOUNTS_CONTACT_WRITABLE) {
                    List<AccountWithDataSet> accountList = accountTypeManager
                        .getGroupWritableAccounts();
                    List<AccountWithDataSet> newAccountList = new ArrayList<AccountWithDataSet>();
                    for (AccountWithDataSet account : accountList) {
                        if (account instanceof AccountWithDataSetEx) {
                            AccountType accountType = accountTypeManager.getAccountType(
                                account.type, account.dataSet);
                            if (accountType != null && accountType.isIccCardAccount()) {
                                continue;
                            }
                            newAccountList.add(account);
                        } else {
                            newAccountList.add(account);
                        }
                    }
                    return new ArrayList<AccountWithDataSet>(newAccountList);
                }
            }
        }
        return null;
    }

    /**
     * @param context the context for resrouce
     * @param account the current account data set
     * @param accountType the current account type
     * @param text2 the sim name
     * @param icon the sim icon
     */
    public static void getViewForName(Context context, AccountWithDataSet account,
            AccountType accountType, TextView text2, ImageView icon) {
        int subId = -1;
        if (account instanceof AccountWithDataSetEx) {
            subId = ((AccountWithDataSetEx) account).getSubId();
            String displayName = ((AccountWithDataSetEx) account).getDisplayName();
            if (TextUtils.isEmpty(displayName)) {
                displayName = account.name;
            }
            text2.setText(displayName);
            LogUtils.d(TAG, "getView subId:" + subId + " displayName:" + displayName);
        } else {
            text2.setText(account.name);
        }
        if (AccountWithDataSetEx.isLocalPhone(accountType.accountType)) {
            text2.setVisibility(View.GONE);
        } else {
            text2.setVisibility(View.VISIBLE);
        }
        text2.setEllipsize(TruncateAt.MIDDLE);
        if (accountType != null && accountType.isIccCardAccount()) {
            LogUtils.d("checkphoto", "accountlistadpter subId : " + subId);
//            icon.setImageDrawable(accountType.getDisplayIconBySubId(context, subId));
        } else {
//            icon.setImageDrawable(accountType.getDisplayIcon(context));
        }
    }

}
