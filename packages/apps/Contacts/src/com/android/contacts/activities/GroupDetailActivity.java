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
 * limitations under the License
 */

package com.android.contacts.activities;

import java.util.ArrayList;
import java.util.List;

import android.app.ActionBar;
import android.content.ContentUris;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Groups;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.contacts.ContactsActivity;
import com.android.contacts.R;
import com.android.contacts.group.GroupDetailDisplayUtils;
import com.android.contacts.group.GroupDetailFragment;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;

import com.mediatek.contacts.activities.GroupBrowseActivity.AccountCategoryInfo;
import com.mediatek.contacts.simcontact.SubInfoUtils;
import com.mediatek.contacts.util.LogUtils;

public class GroupDetailActivity extends ContactsActivity {

    private static final String TAG = "GroupDetailActivity";

    private boolean mShowGroupSourceInActionBar;

    private String mAccountTypeString;
    private String mDataSet;
    /// M:
    private static final int SUBACTIVITY_EDIT_GROUP = 1;
    private GroupDetailFragment mFragment;

    private List<String> mFamily = new ArrayList<String>();
    private List<String> mColleague = new ArrayList<String>();
    private List<String> mFriend = new ArrayList<String>();
    private List<String> mSchoolmate = new ArrayList<String>();

    @Override
    public void onCreate(Bundle savedState) {
    	int themeId = getResources().getIdentifier("androidhwext:style/Theme.Emui.WithActionBar", null, null);
		if (themeId > 0){
			setTheme(themeId);
		}
        super.onCreate(savedState);

        // TODO: Create Intent Resolver to handle the different ways users can get to this list.
        // TODO: Handle search or key down

        setContentView(R.layout.group_detail_activity);

        mShowGroupSourceInActionBar = getResources().getBoolean(
                R.bool.config_show_group_action_in_action_bar);

        mFragment = (GroupDetailFragment) getFragmentManager().findFragmentById(
                R.id.group_detail_fragment);
        mFragment.setListener(mFragmentListener);
        mFragment.setShowGroupSourceInActionBar(mShowGroupSourceInActionBar);

        /** M: New feature @{  */
        setAccountCategoryInfo();
        /** @} */
        // We want the UP affordance but no app icon.
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE,
                    ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE
                    | ActionBar.DISPLAY_SHOW_HOME);
        }

        initGroupTitle();
    }

    private void initGroupTitle() {

        mFamily.add("家人");
        mFamily.add("family");
        mFamily.add("خانواده");
        mFamily.add("العائلة");
        mFamily.add("Семья");
        mFamily.add("Famille");

        mColleague.add("同事");
        mColleague.add("colleague");
        mColleague.add("همکار");
        mColleague.add("زملاء العمل");
        mColleague.add("Коллеги");
        mColleague.add("Collègue");

        mFriend.add("朋友");
        mFriend.add("friend");
        mFriend.add("دوست");
        mFriend.add("صديق");
        mFriend.add("Друзья");
        mFriend.add("Ami");

        mSchoolmate.add("同学");
        mSchoolmate.add("schoolmate");
        mSchoolmate.add("دوست");
        mSchoolmate.add("زملاء الدراسة");
        mSchoolmate.add("Одноклассники");
        mSchoolmate.add("Camarade de classe");
    }

    private final GroupDetailFragment.Listener mFragmentListener =
            new GroupDetailFragment.Listener() {

        @Override
        public void onGroupNotFound() {
            /// M:
            finish();
        }

        @Override
        public void onGroupSizeUpdated(String size) {
            getActionBar().setSubtitle(size);
        }

        @Override
        public void onGroupTitleUpdated(String title) {
            if (title == null) {
                getActionBar().setTitle("");
                return;
            }
            if (mFamily.contains(title)) {
                getActionBar().setTitle(R.string.family_group);
            } else if (mColleague.contains(title)) {
                getActionBar().setTitle(R.string.colleague_group);
            } else if (mFriend.contains(title)) {
                getActionBar().setTitle(R.string.friend_group);
            } else if (mSchoolmate.contains(title)) {
                getActionBar().setTitle(R.string.schoolmate_group);
            } else {
                getActionBar().setTitle(title);
            }
        }

        @Override
        public void onAccountTypeUpdated(String accountTypeString, String dataSet) {
            mAccountTypeString = accountTypeString;
            mDataSet = dataSet;
            invalidateOptionsMenu();
        }

        @Override
        public void onEditRequested(Uri groupUri) {
            final Intent intent = new Intent(GroupDetailActivity.this, GroupEditorActivity.class);
            /** M: Bug Fix CR ID :ALPS000116203 @{ */
            mSubId = Integer.parseInt(groupUri.getLastPathSegment().toString());
            String grpId = groupUri.getPathSegments().get(1).toString();
            LogUtils.d(TAG, grpId + "--------grpId");
            Uri uri = Uri.parse("content://com.android.contacts/groups").buildUpon()
                    .appendPath(grpId).build();
            LogUtils.d(TAG, uri.toString() + "--------groupUri.getPath();");
            intent.setData(uri);
            intent.setAction(Intent.ACTION_EDIT);
            intent.putExtra("SIM_ID", mSubId);
            startActivityForResult(intent, SUBACTIVITY_EDIT_GROUP);
            /** @} */

        }

        @Override
        public void onContactSelected(Uri contactUri) {
            Intent intent = new Intent(Intent.ACTION_VIEW, contactUri);
            startActivity(intent);
        }

    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        if (mShowGroupSourceInActionBar) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.group_source, menu);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (!mShowGroupSourceInActionBar) {
            return false;
        }
        MenuItem groupSourceMenuItem = menu.findItem(R.id.menu_group_source);
        if (groupSourceMenuItem == null) {
            return false;
        }
        final AccountTypeManager manager = AccountTypeManager.getInstance(this);
        final AccountType accountType =
                manager.getAccountType(mAccountTypeString, mDataSet);
        if (TextUtils.isEmpty(mAccountTypeString)
                || TextUtils.isEmpty(accountType.getViewGroupActivity())) {
            groupSourceMenuItem.setVisible(false);
            return false;
        }
        View groupSourceView = GroupDetailDisplayUtils.getNewGroupSourceView(this);
        GroupDetailDisplayUtils.bindGroupSourceView(this, groupSourceView,
                mAccountTypeString, mDataSet);
        groupSourceView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final Uri uri = ContentUris.withAppendedId(Groups.CONTENT_URI,
                        mFragment.getGroupId());
                final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.setClassName(accountType.syncAdapterPackageName,
                        accountType.getViewGroupActivity());
                startActivity(intent);
            }
        });
        groupSourceMenuItem.setActionView(groupSourceView);
        groupSourceMenuItem.setVisible(true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                /// M: In L, return the prior activity. KK will return home activity.
                onBackPressed();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /** M: New feature @{  */
    private void setAccountCategoryInfo() {
        Bundle intentExtras;
        String category = null;
        String simName = null;

        intentExtras = this.getIntent().getExtras();
        final AccountCategoryInfo accountCategoryInfo = intentExtras == null ? null
                : (AccountCategoryInfo) intentExtras.getParcelable(KEY_ACCOUNT_CATEGORY);
        if (accountCategoryInfo != null) {
            category = accountCategoryInfo.mAccountCategory;
            mSubId = accountCategoryInfo.mSubId;
            simName = accountCategoryInfo.mSimName;
        }
        LogUtils.d(TAG, mSubId + "----mSubId+++++[groupDetailActivity]");
        LogUtils.d(TAG, simName + "----mSimName+++++[groupDetailActivity]");
        mFragment.loadExtras(category, mSubId, simName);

        String callBackIntent = getIntent().getStringExtra("callBackIntent");
        LogUtils.d(TAG, callBackIntent + "----callBackIntent");
        if (null != callBackIntent) {
            int subId = getIntent().getIntExtra("mSubId", -1);
            mFragment.loadExtras(subId);
            LogUtils.d(TAG, subId + "----subId");
        }

        mFragment.loadGroup(getIntent().getData());
        mFragment.closeActivityAfterDelete(false);
    }
    /** @} */

    /// M: @{
    private int mSubId = SubInfoUtils.getInvalidSubId();
    public static final String KEY_ACCOUNT_CATEGORY = "AccountCategory";
    /// @}
}
