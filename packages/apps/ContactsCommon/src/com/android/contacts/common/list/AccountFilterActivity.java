/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.app.ActionBar;
import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.Switch;

import com.android.contacts.common.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.AccountFilterUtil;
import com.google.common.collect.Lists;

import android.widget.ProgressBar;
import android.widget.TextView;

import com.mediatek.contacts.util.ContactsCommonListUtils;
import com.mediatek.contacts.ContactsSystemProperties;
import com.mediatek.contacts.model.AccountWithDataSetEx;
import com.mediatek.contacts.widget.WaitCursorView;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows a list of all available accounts, letting the user select under which account to view
 * contacts.
 */
public class AccountFilterActivity extends Activity implements AdapterView.OnItemClickListener {

    private static final String TAG = AccountFilterActivity.class.getSimpleName();

    private static final int SUBACTIVITY_CUSTOMIZE_FILTER = 0;

    public static final String KEY_EXTRA_CONTACT_LIST_FILTER = "contactListFilter";
    public static final String KEY_EXTRA_CURRENT_FILTER = "currentFilter";

    private static final int FILTER_LOADER_ID = 0;

    private ListView mListView;

    private ContactListFilter mCurrentFilter;

    private Switch mShowContactsOnlyWithNumSwitch;

    @Override
    protected void onCreate(Bundle icicle) {
       	int themeId = getResources().getIdentifier("androidhwext:style/Theme.Emui.WithActionBar", null, null);
    		if (themeId > 0){
    			setTheme(themeId);
    		}
        super.onCreate(icicle);
        /** M: Bug Fix:ALPS00115673 Descriptions: add wait cursor */
        View listLayout = getLayoutInflater().inflate(R.layout.contact_list_filter, null);
        setContentView(listLayout);

        mListView = (ListView) findViewById(android.R.id.list);
        mListView.setOnItemClickListener(this);

        mShowContactsOnlyWithNumSwitch = (Switch) findViewById(R.id.pref_switch);
        mShowContactsOnlyWithNumSwitch.setChecked(ContactListFilter.getNumberOnlyFromPreferences(getSharedPreferences()));
		mShowContactsOnlyWithNumSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				ContactListFilter.storeNumberOnlyToPreferences(getSharedPreferences(), isChecked);
			}
			
		});


        /** M: Bug Fix:ALPS00115673 Descriptions: add wait cursor */
        mWaitCursorView = ContactsCommonListUtils.initLoadingView(this, listLayout, mLoadingContainer, mLoadingContact, mProgress);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mCurrentFilter = getIntent().getParcelableExtra(KEY_EXTRA_CURRENT_FILTER);

        getLoaderManager().initLoader(FILTER_LOADER_ID, null, new MyLoaderCallbacks());
    }

    private SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    private static class FilterLoader extends AsyncTaskLoader<List<ContactListFilter>> {
        private Context mContext;

        public FilterLoader(Context context) {
            super(context);
            mContext = context;
        }

        @Override
        public List<ContactListFilter> loadInBackground() {
            return loadAccountFilters(mContext);
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        protected void onStopLoading() {
            cancelLoad();
        }

        @Override
        protected void onReset() {
            onStopLoading();
        }
    }

    private static List<ContactListFilter> loadAccountFilters(Context context) {
        final ArrayList<ContactListFilter> result = Lists.newArrayList();
        final ArrayList<ContactListFilter> accountFilters = Lists.newArrayList();
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(context);
        List<AccountWithDataSet> accounts = accountTypes.getAccounts(false);

        for (AccountWithDataSet account : accounts) {
            AccountType accountType = accountTypes.getAccountType(account.type, account.dataSet);
            if (accountType != null && accountType.isExtension() && !account.hasData(context)) {
                // Hide extensions with no raw_contacts.
                continue;
            }
            Log.d("geticon", "[accountfilteractivity] ");

            /// M: For MTK multiuser in 3gdatasms @{
            if (!ContactsCommonListUtils.isUserOwner() && accountType != null 
                && accountType.isIccCardAccount()) {
                continue;
            }
            /// @}

            /// M: Change feature for ALPS00233786. @{
            // Descriptions: cu feature change photo by slot id. 
            
            if (accountType != null && accountType.isIccCardAccount()) {
                /// M: ALPS913966 cache displayname in account filter and  push to intent.
                ContactsCommonListUtils.addToAccountFilter(context, account, accountFilters, accountType);
            } else {
                Drawable icon = accountType != null ? accountType.getDisplayIcon(context) : null;
                accountFilters.add(ContactListFilter.createAccountFilter(
                        account.type, account.name, account.dataSet, icon, null));
            }
            /// @}
            
        }

        // Always show "All", even when there's no accounts.  (We may have local contacts)
        result.add(ContactListFilter.createFilterWithType(
                ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS));

        final int count = accountFilters.size();
        if (count >= 1) {
            // If we only have one account, don't show it as "account", instead show it as "all"
//            if (count > 1) {      //modified by tanghuaizhe 
                result.addAll(accountFilters);
//            } //modified by tanghuaizhe 
            result.add(ContactListFilter.createFilterWithType(
                    ContactListFilter.FILTER_TYPE_CUSTOM));
        }
        return result;
    }

    private class MyLoaderCallbacks implements LoaderCallbacks<List<ContactListFilter>> {
        @Override
        public Loader<List<ContactListFilter>> onCreateLoader(int id, Bundle args) {

            /** M: Bug Fix ALPS00115673 Descriptions: add wait cursor @{ */
            Log.d(TAG, "onCreateLoader");
            if (mLoadingContainer != null) {
                mLoadingContainer.setVisibility(View.GONE);
            }
            if (mWaitCursorView != null) {
                mWaitCursorView.startWaitCursor();
            }
            /** @} */

            return new FilterLoader(AccountFilterActivity.this);
        }

        @Override
        public void onLoadFinished(
                Loader<List<ContactListFilter>> loader, List<ContactListFilter> data) {
          /// M:check whether the Activity's status still ok @{
            if (isActivityFinished()) {
                Log.w(TAG, "onLoadFinished(),This Activity is finishing.");
                return;
            }
            ///@}

            Log.d(TAG, "onLoadFinished");
            /** M: Bug Fix ALPS00115673 Descriptions: add wait cursor */
            mWaitCursorView.stopWaitCursor();

            if (data == null) { // Just in case...
                Log.d(TAG, "Failed to load filters");
                return;
            }
            mListView.setAdapter(
                    new FilterListAdapter(AccountFilterActivity.this, data, mCurrentFilter));
        }

        @Override
        public void onLoaderReset(Loader<List<ContactListFilter>> loader) {
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final ContactListFilter filter = (ContactListFilter) view.getTag();
        if (filter == null) {
            return; // Just in case
        }
        if (filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
            final Intent intent = new Intent(this,
                    CustomContactListFilterActivity.class);
            startActivityForResult(intent, SUBACTIVITY_CUSTOMIZE_FILTER);
        } else {
            final Intent intent = new Intent();
            intent.putExtra(KEY_EXTRA_CONTACT_LIST_FILTER, filter);
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case SUBACTIVITY_CUSTOMIZE_FILTER:
                final Intent intent = new Intent();
                ContactListFilter filter = ContactListFilter.createFilterWithType(
                        ContactListFilter.FILTER_TYPE_CUSTOM);
                intent.putExtra(KEY_EXTRA_CONTACT_LIST_FILTER, filter);
                setResult(Activity.RESULT_OK, intent);
                finish();
                break;
        }
    }

    private static class FilterListAdapter extends BaseAdapter {
        private final List<ContactListFilter> mFilters;
        private final LayoutInflater mLayoutInflater;
        private final AccountTypeManager mAccountTypes;
        private final ContactListFilter mCurrentFilter;

        public FilterListAdapter(
                Context context, List<ContactListFilter> filters, ContactListFilter current) {
            mLayoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mFilters = filters;
            mCurrentFilter = current;
            mAccountTypes = AccountTypeManager.getInstance(context);
        }

        @Override
        public int getCount() {
            return mFilters.size();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public ContactListFilter getItem(int position) {
            return mFilters.get(position);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            final ContactListFilterView view;
            if (convertView != null) {
                view = (ContactListFilterView) convertView;
            } else {
                view = (ContactListFilterView) mLayoutInflater.inflate(
                        R.layout.contact_list_filter_item, parent, false);
            }
            view.setSingleAccount(mFilters.size() == 1);
            final ContactListFilter filter = mFilters.get(position);
            view.setContactListFilter(filter);
            view.bindView(mAccountTypes);
            view.setTag(filter);
            view.setActivated(filter.equals(mCurrentFilter));
            TextView accountUserName = (TextView) view.findViewById(R.id.accountUserName);
            if (accountUserName != null) {
                accountUserName.setVisibility(View.GONE);
            }
            if (filter != null && filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
                TextView account_contacts_num_tv = (TextView) view.findViewById(R.id.account_contacts_num_tv);
                if (account_contacts_num_tv != null) {
                    account_contacts_num_tv.setVisibility(View.GONE);
                }
            }

            return view;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // We have two logical "up" Activities: People and Phone.
                // Instead of having one static "up" direction, behave like back as an
                // exceptional case.
                onBackPressed();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /** M: Bug Fix ALPS00115673 Descriptions: add wait cursor @{ */
    @Override
    protected void onDestroy() {
        mFinished = true;
//        ContactListFilterView.closeContactsDb();
        super.onDestroy();
    }

    public boolean isActivityFinished() {
        return mFinished;
    }

    private TextView mLoadingContact;
    private ProgressBar mProgress;
    private View mLoadingContainer;
    private WaitCursorView mWaitCursorView;
    private boolean mFinished = false;
    /** @} */
}
