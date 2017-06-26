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

import android.app.ActionBar;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.android.contacts.ContactsActivity;
import com.android.contacts.R;
import com.android.contacts.editor.ContactEditorFragment;
import com.android.contacts.editor.ContactEditorFragment.SaveMode;
import com.android.contacts.group.GroupEditorFragment;
import com.android.contacts.util.DialogManager;
import com.android.contacts.common.vcard.VCardService;

import com.mediatek.contacts.ContactSaveServiceEx;
import com.mediatek.contacts.activities.ActivitiesUtils;
import com.mediatek.contacts.activities.GroupBrowseActivity.AccountCategoryInfo;
import com.mediatek.contacts.list.ContactsGroupMultiPickerFragment;
import com.mediatek.contacts.list.service.MultiChoiceService;
import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.simcontact.SubInfoUtils;
import com.mediatek.contacts.util.LogUtils;

public class GroupEditorActivity extends ContactsActivity
        implements DialogManager.DialogShowingViewActivity {

    private static final String TAG = "GroupEditorActivity";

    public static final String ACTION_SAVE_COMPLETED = "saveCompleted";
    public static final String ACTION_ADD_MEMBER_COMPLETED = "addMemberCompleted";
    public static final String ACTION_REMOVE_MEMBER_COMPLETED = "removeMemberCompleted";

    private static final int SUBACTIVITY_DETAIL_GROUP = 1;
    private GroupEditorFragment mFragment;

    private DialogManager mDialogManager = new DialogManager(this);

    /** M: New Feature @{ */
    private int mSubId = SubInfoUtils.getInvalidSubId();
    private Bundle mIntentExtras;
    /** @} */

    @Override
    public void onCreate(Bundle savedState) {
    	
    	int themeId = getResources().getIdentifier("androidhwext:style/Theme.Emui.WithActionBar", null, null);
		if (themeId > 0){
			setTheme(themeId);
		}
        super.onCreate(savedState);

        /** M: New feature @{ */
        String action = setAccountCategoryInfo();
        /** @} */

        if (ACTION_SAVE_COMPLETED.equals(action)) {
            LogUtils.w(TAG, "[onCreate] action is ACTION_SAVE_COMPLETED,finish activity.");
            finish();
            return;
        }
        /** M: Fixed CR ALPS00542175/ALPS01077147
         * Fix ALPS01466297 finish Activity if phb not ready @{
         */
        if (ActivitiesUtils.checkPhoneBookReady(this, savedState, mSubId)) {
            return;
        }
        /** @} */

        setContentView(R.layout.group_editor_activity);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // Inflate a custom action bar that contains the "done" button for saving changes
            // to the group
            LayoutInflater inflater = (LayoutInflater) getSystemService
                    (Context.LAYOUT_INFLATER_SERVICE);
            View customActionBarView = inflater.inflate(R.layout.editor_custom_action_bar,
                    null);
            View saveMenuItem = customActionBarView.findViewById(R.id.save_menu_item);
            saveMenuItem.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mFragment.onDoneClicked();
                }
            });
            // Show the custom action bar but hide the home icon and title
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                    ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME |
                    ActionBar.DISPLAY_SHOW_TITLE);
            actionBar.setCustomView(customActionBarView);
        }

        mFragment = (GroupEditorFragment) getFragmentManager().findFragmentById(
                R.id.group_editor_fragment);
        mFragment.setListener(mFragmentListener);
        mFragment.setContentResolver(getContentResolver());

        // NOTE The fragment will restore its state by itself after orientation changes, so
        // we need to do this only for a new instance.
        if (savedState == null) {
            Uri uri = Intent.ACTION_EDIT.equals(action) ? getIntent().getData() : null;
            /** M: New feature @{ */
            LogUtils.d(TAG, " savedState == null mSubId : " + mSubId);
            mFragment.load(action, uri, getIntent().getExtras(), mSubId);
            /** @} */

        }
    }

   @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        if (DialogManager.isManagedId(id)) {
            return mDialogManager.onCreateDialog(id, args);
        } else {
            // Nobody knows about the Dialog
            Log.w(TAG, "Unknown dialog requested, id: " + id + ", args: " + args);
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        // If the change could not be saved, then revert to the default "back" button behavior.
        /** M: New feature CR ID :ALPS00228918 @{ */
        if (!mFragment.save(SaveMode.CLOSE, false)) {
            if (!mFragment.checkOnBackPressedState()) {
                super.onBackPressed();
            }
        }
        /** @} */
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (mFragment == null) {
            LogUtils.w(TAG, "[onNewIntent] the mFragment is null,return.");
            return;
        }

        /// M: @{
        mSubId = intent.getIntExtra(ContactSaveServiceEx.EXTRA_SUB_ID, -1);
        int saveMode = intent.getIntExtra(ContactEditorFragment.SAVE_MODE_EXTRA_KEY, SaveMode.CLOSE);
        LogUtils.d(TAG, "[onNewIntent] mSubId:" + mSubId + ",saveMode:" + saveMode + ",action:" + intent.getAction());
        /// @}
        String action = intent.getAction();
        if (ACTION_SAVE_COMPLETED.equals(action)) {
            mFragment.onSaveCompleted(true, intent.getData());
            /// M: @{
            boolean isSuccess = intent.getData() != null;
            if (isSuccess && saveMode != SaveMode.RELOAD) {
                Toast.makeText(getApplicationContext(), R.string.groupSavedToast, Toast.LENGTH_SHORT).show();
            }
            /// @}
        }
    }

    private final GroupEditorFragment.Listener mFragmentListener =
            new GroupEditorFragment.Listener() {
        @Override
        public void onGroupNotFound() {
            LogUtils.w(TAG, "[onGroupNotFound] finish activity..");
            finish();
        }

        @Override
        public void onReverted() {
            finish();
        }

        @Override
        public void onAccountsNotFound() {
            LogUtils.w(TAG, "[onAccountsNotFound] finish activity..");
            finish();
        }

        @Override
        public void onSaveFinished(int resultCode, Intent resultIntent) {
            if (resultIntent != null) {
                Intent intent = new Intent(GroupEditorActivity.this, GroupDetailActivity.class);
                intent.setData(resultIntent.getData());
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                /// M: @{
                intent.putExtra("mSubId", mSubId);
                intent.putExtra("callBackIntent", "callBackIntent");
                //add the AccountCategoryInfo will be use in GroupDetailActivity
                intent.putExtra(GroupDetailActivity.KEY_ACCOUNT_CATEGORY,
                        resultIntent.getExtras().getParcelable(GroupDetailActivity.KEY_ACCOUNT_CATEGORY));
                startActivityForResult(intent, SUBACTIVITY_DETAIL_GROUP);
                /// @}
            }
            finish();
        }
    };

    @Override
    public DialogManager getDialogManager() {
        return mDialogManager;
    }

    /** M: @{ */
    private String setAccountCategoryInfo() {
        Intent intent = getIntent();
        String action = intent.getAction();
        mIntentExtras = intent.getExtras();
        LogUtils.d(TAG, " mIntentExtras : " + mIntentExtras);
        final AccountCategoryInfo accountCategoryInfo = mIntentExtras == null ? null
                : (AccountCategoryInfo) mIntentExtras.getParcelable("AccountCategory");
        if (accountCategoryInfo != null) {
            LogUtils.d(TAG, "onCrete " + accountCategoryInfo);
            mSubId = accountCategoryInfo.mSubId;
        } else {
            mSubId = intent.getIntExtra("SIM_ID", mSubId);
        }
        LogUtils.d(TAG, mSubId + "-------mSubId[oncreate]");
        return action;
    }
    /** @} */
}
