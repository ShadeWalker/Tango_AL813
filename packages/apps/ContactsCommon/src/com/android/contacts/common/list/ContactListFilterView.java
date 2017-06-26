/*
 * Copyright (C) 2014 MediaTek Inc.
 * Modification based on code covered by the mentioned copyright
 * and/or permission notice(s).
 */
/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.contacts.common.list;

import java.util.ArrayList;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract.RawContacts;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import com.android.contacts.common.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.util.AccountFilterUtil;
import com.mediatek.contacts.model.AccountWithDataSetEx;
import com.mediatek.contacts.util.AccountTypeUtils;
import com.mediatek.contacts.util.ContactsCommonListUtils;
import com.mediatek.contacts.util.ContactsConstants;

import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;

import java.util.List;

/**
 * Contact list filter parameters.
 */
public class ContactListFilterView extends LinearLayout {

	private static final String TAG = ContactListFilterView.class
			.getSimpleName();

//	private ImageView mIcon;
	private TextView mAccountType;
	private TextView mAccountUserName;
	private RadioButton mRadioButton;
	private ContactListFilter mFilter;
	private boolean mSingleAccount;
	private TextView account_contacts_num_tv;
	int i = 0;
	private AccountType accountType;
	private Handler handler = new Handler(new Handler.Callback() {

		@Override
		public boolean handleMessage(Message msg) {
			// TODO Auto-generated method stub2130838166
				account_contacts_num_tv.setText(getResources().getString(R.string.total_contact) + i);
			
			return false;
		}
	});

	public ContactListFilterView(Context context) {
		super(context);
	}

	public ContactListFilterView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	

	public void setContactListFilter(ContactListFilter filter) {
		mFilter = filter;
	}

	public ContactListFilter getContactListFilter() {
		return mFilter;
	}

	public void setSingleAccount(boolean flag) {
		this.mSingleAccount = flag;
	}

	@Override
	public void setActivated(boolean activated) {
		super.setActivated(activated);
		if (mRadioButton != null) {
			mRadioButton.setChecked(activated);
		} else {
			// We're guarding against null-pointer exceptions,
			// but otherwise this code is not expected to work
			// properly if the button hasn't been initialized.
			Log.wtf(TAG, "radio-button cannot be activated because it is null");
		}
	}


	public void CountSimContact() {
		new Thread() {
			public void run() {
				Cursor cursor = null;
				if( mFilter.accountName==null){
					return;
				}
				ContentResolver resolver = getContext().getContentResolver();
				/* 使用工具导入的联系人 account name is null */
				if(mFilter.accountName.equals("Phone")){
					cursor =resolver.query(RawContacts.CONTENT_URI,
					          new String[]{"contact_id"},
					          "("+RawContacts.ACCOUNT_NAME + "=?" +" or "+RawContacts.ACCOUNT_NAME +" is "+ null +")"+" and contact_id is not null",
					          new String[]{mFilter.accountName}, "contact_id");
//					cursor = db
//							.rawQuery(
//									"select distinct contact_id  from view_raw_contacts  where account_name=? or account_name is null ",
//									new String[] { mFilter.accountName});
				}else {
					cursor =resolver.query(RawContacts.CONTENT_URI,
					          new String[]{"contact_id"},
					          "("+RawContacts.ACCOUNT_NAME + "=? )"+" and contact_id is not null",
					          new String[]{mFilter.accountName}, "contact_id");
				}
				if (cursor != null) {
					int lastcontact_id=-1;//代替distinct
					while(cursor.moveToNext()){
						int index = cursor.getColumnIndex("contact_id");
						if( lastcontact_id !=cursor.getInt(index)){
							i++;
						}
						lastcontact_id = cursor.getInt(index);
					}
					cursor.close();
				} else {
					i = 0;
				}
				handler.sendEmptyMessage(0);
			}
		}.start();

	}
	
	public void CountContact() {
		new Thread(){
			@Override
			public void run() {
				// TODO Auto-generated method stub
				super.run();
				Uri uri = Uri.parse("content://com.android.contacts/contacts");
				ContentResolver resolver = getContext().getContentResolver();
				Cursor cursor = resolver.query(uri, new String[] { "_id" }, null, null,
						null);
				if (cursor != null) {
					i = cursor.getCount();
					cursor.close();
				} else {
					i = 0;
				}
				handler.sendEmptyMessage(0);
			}
		}.start();
	}



	public void bindView(AccountTypeManager accountTypes) {
		if (mAccountType == null) {
//			mIcon = (ImageView) findViewById(R.id.icon);
			mAccountType = (TextView) findViewById(R.id.accountType);
			mAccountUserName = (TextView) findViewById(R.id.accountUserName);
			mRadioButton = (RadioButton) findViewById(R.id.radioButton);
			mRadioButton.setChecked(isActivated());
			account_contacts_num_tv = (TextView) findViewById(R.id.account_contacts_num_tv);
		}
		if (mFilter == null) {
			mAccountType.setText(R.string.contactsList);
			return;
		}

		mAccountUserName.setVisibility(View.GONE);
		switch (mFilter.filterType) {
		case ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS: {
			bindView(0, R.string.list_filter_all_accounts);
			CountContact();
			break;
		}
		case ContactListFilter.FILTER_TYPE_STARRED: {
			bindView(R.drawable.mtk_ic_menu_star_holo_light,
					R.string.list_filter_all_starred);
			break;
		}
		case ContactListFilter.FILTER_TYPE_CUSTOM: {
			bindView(R.drawable.ic_menu_settings_holo_light,
					R.string.list_filter_customize);
			break;
		}
		case ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY: {
			bindView(0, R.string.list_filter_phones);
			break;
		}
		case ContactListFilter.FILTER_TYPE_SINGLE_CONTACT: {
			bindView(0, R.string.list_filter_single);
			break;
		}
		case ContactListFilter.FILTER_TYPE_ACCOUNT: {
			mAccountUserName.setVisibility(View.VISIBLE);
/*			mIcon.setVisibility(View.VISIBLE);
			if (mFilter.icon != null) {
				mIcon.setImageDrawable(mFilter.icon);
			} else {
				mIcon.setImageResource(R.drawable.unknown_source);
			}*/
			accountType= accountTypes.getAccountType(
					mFilter.accountType, mFilter.dataSet);
			
//			getSubInfoList();
//			int subsriptionId = mSubInfoList.get(0).getSubscriptionId();
//			int slotId = SubscriptionManager.getSlotId(subsriptionId);
/*			System.out.println("the slot id is"+slotId);
*/			
			/** M: Change Feature ALPS00406553 @{ */
			if (accountType.isIccCardAccount()) {
				mAccountUserName.setText(accountType.getDisplayLabel(mContext));
			} else {
				mAccountUserName.setText(mFilter.accountName);
			}
			/** @} */
			mAccountType.setText(accountType.getDisplayLabel(getContext()));
			// / M: modify
			ContactsCommonListUtils.setAccountTypeText(getContext(),
					accountType, mAccountType, mAccountUserName, mFilter);

			// [AccountWithDataSetEx {name=SIM1, type=SIM Account, dataSet=null,
			// subId = 1}, AccountWithDataSetEx {name=USIM2, type=USIM Account,
			// dataSet=null, subId = 2}, AccountWithDataSet {name=Phone,
			// type=Local Phone Account, dataSet=null}]
//			if (accountType.accountType.equals("SIM Account")) {
//				account_contacts_num_tv.setText(getResources().getString(R.string.total_contact) + CountSimContact());
//			} else if (accountType.accountType.equals("Local Phone Account")) {
//				account_contacts_num_tv.setText(getResources().getString(R.string.total_contact) + CountLocalContact());
//			} else if (accountType.accountType.equals("USIM Account")) {
//				account_contacts_num_tv.setText(getResources().getString(R.string.total_contact) + CountUSimContact());
//			}
			CountSimContact();
			
			break;
		}
		}
	}

	private void bindView(int iconResource, int textResource) {
/*		if (iconResource != 0) {
			mIcon.setVisibility(View.VISIBLE);
			mIcon.setImageResource(iconResource);
		} else {
			mIcon.setVisibility(View.GONE);
		}*/

		mAccountType.setText(textResource);
//		mIcon.setVisibility(View.GONE);

	}
	
    private List<SubscriptionInfo> mSubInfoList;
	private int mSubCount;
	private void getSubInfoList() {
        mSubInfoList = SubscriptionManager.from(mContext).getActiveSubscriptionInfoList();
        mSubCount = (mSubInfoList != null && !mSubInfoList.isEmpty()) ? mSubInfoList.size() : 0;
    }
}
