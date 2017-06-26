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

package com.android.contacts.common.util;

import android.content.Context;
import android.os.SystemProperties;
import android.telephony.SubscriptionInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.common.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.mediatek.contacts.model.AccountWithDataSetEx;
import com.mediatek.contacts.simcontact.SubInfoUtils;
import com.mediatek.contacts.util.AccountsListAdapterUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * List-Adapter for Account selection
 */
public final class AccountsListAdapter extends BaseAdapter {
    private final LayoutInflater mInflater;
    private final List<AccountWithDataSet> mAccounts;
    private final AccountTypeManager mAccountTypes;
    private final Context mContext;
    private final AccountListFilter mAccountListFilter;
    private final AccountWithDataSet mCurrentAccount;

    private static final String TAG = "AccountListAdapter";

	private boolean mIsAddAccount = false;

	/**
	 * Filters that affect the list of accounts that is displayed by this
	 * adapter.
	 */
	public enum AccountListFilter {
		ALL_ACCOUNTS, // All read-only and writable accounts
		ACCOUNTS_CONTACT_WRITABLE, // Only where the account type is contact
									// writable
		ACCOUNTS_GROUP_WRITABLE // Only accounts where the account type is group
								// writable
	}

    public AccountsListAdapter(Context context, AccountListFilter accountListFilter) {
        this(context, accountListFilter, null);
    }

    /**
     * @param currentAccount the Account currently selected by the user, which should come
     * first in the list. Can be null.
     */
    public AccountsListAdapter(Context context, AccountListFilter accountListFilter,
            AccountWithDataSet currentAccount) {
        mContext = context;
        mAccountTypes = AccountTypeManager.getInstance(context);
        mAccounts = getAccounts(accountListFilter);
        mAccountListFilter = accountListFilter;
        mCurrentAccount = currentAccount;
        if (currentAccount != null
                && !mAccounts.isEmpty()
                && !mAccounts.get(0).equals(currentAccount)
                && mAccounts.remove(currentAccount)) {
            mAccounts.add(0, currentAccount);
        }
        mInflater = LayoutInflater.from(context);
    }
	
	
		public AccountsListAdapter(Context context,
			AccountListFilter accountListFilter,
			AccountWithDataSet currentAccount, boolean addAccount) {
		mContext = context;
		mAccountTypes = AccountTypeManager.getInstance(context);
		mAccounts = getAccounts(accountListFilter);
		mAccountListFilter = accountListFilter;
		mCurrentAccount = currentAccount;
		if (currentAccount != null && !mAccounts.isEmpty()
				&& !mAccounts.get(0).equals(currentAccount)
				&& mAccounts.remove(currentAccount)) {
			mAccounts.add(0, currentAccount);
		}
		if (addAccount && !mAccounts.isEmpty()) {
			mIsAddAccount = true;
			AccountWithDataSet dummyAccount = mAccounts.get(0);
			mAccounts.add(dummyAccount);
		}

		mInflater = LayoutInflater.from(context);
	}

    private List<AccountWithDataSet> getAccounts(AccountListFilter accountListFilter) {
        if (accountListFilter == AccountListFilter.ACCOUNTS_GROUP_WRITABLE) {
            /// M: add for sim account
            return AccountsListAdapterUtils.getGroupAccount(mAccountTypes);
        }

        /** M: For MTK multiuser in 3gdatasms @ {  */
        ArrayList<AccountWithDataSet> multiAccountList = AccountsListAdapterUtils.
                getAccountForMultiUser(mAccountTypes, accountListFilter);
        if (multiAccountList != null && multiAccountList.size() > 0) {
            return multiAccountList;
        }
        /** @ } */

        return new ArrayList<AccountWithDataSet>(mAccountTypes.getAccounts(
                accountListFilter == AccountListFilter.ACCOUNTS_CONTACT_WRITABLE));
    }

	// / Added by guofeiyao 2015/12/07
	// For making the first sim selected when create new contact 
	// to inform the user about
    // which account the contact will be saved in
	private boolean selected = false;
	private ImageView firstSim;
	
    // / End

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final View resultView = convertView != null ? convertView
                : mInflater.inflate(R.layout.account_selector_list_item, parent, false);

        final TextView text1 = (TextView) resultView.findViewById(android.R.id.text1);
        final TextView text2 = (TextView) resultView.findViewById(android.R.id.text2);
//        final ImageView icon = (ImageView) resultView.findViewById(android.R.id.icon);

        final AccountWithDataSet account = mAccounts.get(position);
        final AccountType accountType = mAccountTypes.getAccountType(account.type, account.dataSet);

//        account.
        // For email addresses, we don't want to truncate at end, which might cut off the domain
        // name.
        /// M: add for sim name
        AccountsListAdapterUtils.getViewForName(mContext, account, accountType, text2, null);
        if (mAccountListFilter == AccountListFilter.ACCOUNTS_CONTACT_WRITABLE
                || mAccountListFilter == AccountListFilter.ACCOUNTS_GROUP_WRITABLE) {
            text2.setVisibility(View.GONE);

			// / Modified by guofeiyao 2015/12/07
	// For making the first sim selected when create new contact 
	// to inform the user about
    // which account the contact will be saved in
            
            ImageView rb = (ImageView) resultView.findViewById(R.id.radio);
            //rb.setVisibility(View.VISIBLE);
            if (mCurrentAccount != null
                && mAccounts != null
                && !mAccounts.isEmpty()
                && mAccounts.get(position).equals(mCurrentAccount)) {
                rb.setImageResource(R.drawable.btn_radio_on_emui);

				selected = true;
			} else {
				rb.setImageResource(R.drawable.btn_radio_off_emui);
			}

            if ( android.os.SystemProperties.get("ro.hq.pref.sim").equals("1") ) {
			
            if ( 0==position ) {
                 firstSim = rb;
			} else if ( mAccounts.size()-1==position ) {
                 if ( selected ) {
                      firstSim = null;
				 } else {
                      firstSim.setImageResource(R.drawable.btn_radio_on_emui);
					  firstSim = null;
				 }
			}

            }
			// / End
        }
        
        //		text1.setText(accountType.getDisplayLabel(mContext));
		if (mIsAddAccount && position == getCount() - 1) {
			text1.setText(R.string.add_new_account);
			return resultView;
		} else {
			text1.setText(accountType.getDisplayLabel(mContext));
		}
        
		if (account instanceof AccountWithDataSetEx) {
			int subId = ((AccountWithDataSetEx) account).getSubId();
            SubscriptionInfo sfr = SubInfoUtils.getSubInfoUsingSubId(subId);
            int slotId = -1;
            if (sfr != null) {
                slotId=sfr.getSimSlotIndex();
            }
			if (slotId == 0) {
				if (!SystemProperties.get("ro.mtk_gemini_support").equals("1")) {
					text1.setText(mContext.getResources()
							.getString(R.string.sim_card));
					}else {
					text1.setText(mContext.getResources()
							.getString(R.string.card_1));
				}
			} else if (slotId == 1) {
				text1.setText(mContext.getResources()
						.getString(R.string.card_2));
			}
		}

        if (AccountWithDataSetEx.isLocalPhone(accountType.accountType)) {
            text1.setText(mContext.getResources().getString(R.string.Local_phone));
        }
        
        return resultView;
    }

    @Override
    public int getCount() {
        return mAccounts.size();
    }

    @Override
    public AccountWithDataSet getItem(int position) {
        return mAccounts.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }
}

