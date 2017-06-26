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

package com.android.contacts.activities;

import android.app.ActionBar;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsActivity;
import com.android.contacts.R;
import com.android.contacts.editor.ContactEditorFragment;
import com.android.contacts.editor.ContactEditorFragment.SaveMode;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.util.DialogManager;
import com.android.contacts.common.vcard.VCardService;
import com.mediatek.contacts.activities.ActivitiesUtils;
import com.mediatek.contacts.list.ContactsGroupMultiPickerFragment;
import com.mediatek.contacts.list.service.MultiChoiceService;
import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.util.MtkToast;
import com.mediatek.contacts.simservice.SIMEditProcessor;

import java.util.ArrayList;
import android.view.inputmethod.InputMethodManager;

/**M: Add SIMEditProcessor.Listener*/
public class ContactEditorActivity extends ContactsActivity
        implements DialogManager.DialogShowingViewActivity , SIMEditProcessor.Listener {
    private static final String TAG = "ContactEditorActivity";

    public static final String ACTION_JOIN_COMPLETED = "joinCompleted";
    public static final String ACTION_SAVE_COMPLETED = "saveCompleted";
    /** M: Add for SIM Service refactory @{ */
    private Handler mHandler = null;
    /** @} */
    /**
     * Boolean intent key that specifies that this activity should finish itself
     * (instead of launching a new view intent) after the editor changes have been
     * saved.
     */
    public static final String INTENT_KEY_FINISH_ACTIVITY_ON_SAVE_COMPLETED =
            "finishActivityOnSaveCompleted";
    /** M: @{ */
    public static final String KEY_ACTION = "key_action";
    private String mAction;
    /** @} */
    private ContactEditorFragment mFragment;
    private boolean mFinishActivityOnSaveCompleted;

    private DialogManager mDialogManager = new DialogManager(this);


    @Override
    public void onCreate(Bundle savedState) {
		int themeId = getResources().getIdentifier("androidhwext:style/Theme.Emui.WithActionBar", null, null);
		if (themeId > 0){
			setTheme(themeId);
		}
    	
    	
        super.onCreate(savedState);
//        setTheme(R.style.EditorActivityTheme);
        LogUtils.d(TAG, "Current thread ID: " + Thread.currentThread().getId());

        /** M: Add for SIM Service refactory @{ */
        mHandler = ActivitiesUtils.initHandler(this);
        /** @} */
        /**
         * M: Bug Fix @{
         *   CR ID: ALPS00251666
         *   Descriptions: can not add contact when in delete processing
         */
        if (ActivitiesUtils.isDeleteingContact(this)) {
            return;
        }
        /** @} */

        final Intent intent = getIntent();
        final String action = intent.getAction();

        // Determine whether or not this activity should be finished after the user is done
        // editing the contact or if this activity should launch another activity to view the
        // contact's details.
	try {
        	mFinishActivityOnSaveCompleted = intent.getBooleanExtra(
                	INTENT_KEY_FINISH_ACTIVITY_ON_SAVE_COMPLETED, false);
	} catch (RuntimeException e) {
		e.printStackTrace();
		finish();
		return;
	}

        // The only situation where action could be ACTION_JOIN_COMPLETED is if the
        // user joined the contact with another and closed the activity before
        // the save operation was completed.  The activity should remain closed then.
        if (ACTION_JOIN_COMPLETED.equals(action)) {
            LogUtils.w(TAG, "[onCreate] action is ACTION_JOIN_COMPLETED,finish activity...");
            finish();
            return;
        }

        if (ACTION_SAVE_COMPLETED.equals(action)) {
            LogUtils.w(TAG, "[onCreate] action is ACTION_SAVE_COMPLETED,finish activity...");
            finish();
            return;
        }

        setContentView(R.layout.contact_editor_activity);

        ActionBar actionBar = getActionBar();
        View actionbarView = getLayoutInflater().inflate(R.layout.contact_editor_actionbar_view, null);
        if (actionBar != null) {
			TextView editContact = (TextView)actionbarView.findViewById(R.id.edit_contact_title);
            if (Intent.ACTION_EDIT.equals(action)) {
                editContact.setText(getResources().getString(
                        R.string.contact_editor_title_existing_contact));
            } else {
                editContact.setText(getResources().getString(
                        R.string.contact_editor_title_new_contact));
            }
            actionBar.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            actionBar.setCustomView(actionbarView);
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
            actionBar.setDisplayShowCustomEnabled(true);
            final android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            ImageView  undo_edit_contact = (ImageView)actionbarView.findViewById(R.id.undo_edit_contact);
            undo_edit_contact.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					// TODO Auto-generated method stub
			        
					mFragment.revert();
		            //add by  tanghuaizhe for null point
		            if (imm.isActive()) {
		               //by wangjun for HQ01975898
		               // imm.hideSoftInputFromWindow(ContactEditorActivity.this.getWindow().getCurrentFocus().getWindowToken(), 0);
		            	if(ContactEditorActivity.this.getWindow() != null && ContactEditorActivity.this.getWindow().getCurrentFocus() != null && ContactEditorActivity.this.getWindow().getCurrentFocus().getWindowToken() != null) {
							imm.hideSoftInputFromWindow(ContactEditorActivity.this.getWindow().getCurrentFocus().getWindowToken(), 0);
						}
		               //by wangjun for HQ01975898
				    }
				}
			});
            ImageView  confirm_edit_contact = (ImageView)actionbarView.findViewById(R.id.confirm_edit_contact);
            confirm_edit_contact.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					// TODO Auto-generated method stub
					mFragment.doSaveAction();
					 if (imm.isActive()) {
					 		//add by guojianhui for HQ01679471
					 		if(ContactEditorActivity.this.getWindow() != null && ContactEditorActivity.this.getWindow().getCurrentFocus() != null && ContactEditorActivity.this.getWindow().getCurrentFocus().getWindowToken() != null) {
								imm.hideSoftInputFromWindow(ContactEditorActivity.this.getWindow().getCurrentFocus().getWindowToken(), 0);
							}
			                
					    }
				}
			});
/*            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);*/
        }

        mFragment = (ContactEditorFragment) getFragmentManager().findFragmentById(
                R.id.contact_editor_fragment);
        mFragment.setListener(mFragmentListener);
        Uri uri = Intent.ACTION_EDIT.equals(action) ? getIntent().getData() : null;
        mFragment.load(action, uri, getIntent().getExtras());
    }

    /// M: Fix ALPS01028420.Need to register a handler for sim edit processor if necessary. @{
    @Override
    public void onResume() {
        super.onResume();
        SimCardUtils.ShowSimCardStorageInfoTask.showSimCardStorageInfo(this, false);
        LogUtils.d(TAG, "[onResume]");
        if (SIMEditProcessor.isNeedRegisterHandlerAgain(mHandler)) {
            LogUtils.d(TAG, " [onResume] register a handler again! Handler: " + mHandler);
            SIMEditProcessor.registerListener(ContactEditorActivity.this, mHandler);
        }
    }
    /// @}

    /** M: Add for SIM Service refactory @{ */
    @Override
    public void onSIMEditCompleted(Intent callbackIntent) {
        LogUtils.d(TAG, "---onSIMEditCompleted---");
        onNewIntent(callbackIntent);
    }
    /** @} */

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (mFragment == null) {
            LogUtils.w(TAG, "[onNewIntent] mFragment is null...");
            return;
        }

        String action = intent.getAction();
        LogUtils.d(TAG, "action: " + action);

        if (Intent.ACTION_EDIT.equals(action)) {
            mFragment.setIntentExtras(intent.getExtras());
        } else if (ACTION_SAVE_COMPLETED.equals(action)) {
            mFragment.onSaveCompleted(true,
                    intent.getIntExtra(ContactEditorFragment.SAVE_MODE_EXTRA_KEY, SaveMode.CLOSE),
                    intent.getBooleanExtra(ContactSaveService.EXTRA_SAVE_SUCCEEDED, false),
                    intent.getData());
        } else if (ACTION_JOIN_COMPLETED.equals(action)) {
            mFragment.onJoinCompleted(intent.getData());
        /** M: Add for SIM Service refactory @{ */
        } else if (SIMEditProcessor.EDIT_SIM_ACTION.equals(action)) {
            mFragment.onEditSIMContactCompleted(intent);
        }
        /** @} */
    }

    /** M: Bug Fix CR ID: ALPS00251666 @{
     * Description:Can't open the Join Contact Activity when change screen orientation.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (!TextUtils.isEmpty(mAction)) {
            outState.putString(KEY_ACTION, mAction);
        }
        super.onSaveInstanceState(outState);
    }
    /** @} */

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        if (DialogManager.isManagedId(id)) return mDialogManager.onCreateDialog(id, args);

        // Nobody knows about the Dialog
        Log.w(TAG, "Unknown dialog requested, id: " + id + ", args: " + args);
        return null;
    }

    @Override
    public void onBackPressed() {
        /** M: Change Feature Description: change save mode when backpressed */
            //mFragment.doSaveAction();
            mFragment.revert();
    }

    private void hideSoftKeyboard() {
        if (getCurrentFocus() != null) {
            InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    private final ContactEditorFragment.Listener mFragmentListener =
            new ContactEditorFragment.Listener() {

        @Override
        public void onDeleteRequested(Uri contactUri) {
            hideSoftKeyboard();
            ContactDeletionInteraction.start(ContactEditorActivity.this, contactUri, true);
        }

        @Override
        public void onReverted() {
            hideSoftKeyboard();
            finish();
        }

        @Override
        public void onSaveFinished(Intent resultIntent) {
            LogUtils.d(TAG, "[onSaveFinished] mFinishActivityOnSaveCompleted:" + mFinishActivityOnSaveCompleted);
            if (mFinishActivityOnSaveCompleted) {
                setResult(resultIntent == null ? RESULT_CANCELED : RESULT_OK, resultIntent);
            } else if (resultIntent != null) {
                startActivity(resultIntent);
            }
            hideSoftKeyboard();
            finish();
        }

        @Override
        public void onContactSplit(Uri newLookupUri) {
            finish();
        }

        @Override
        public void onContactNotFound() {
            LogUtils.w(TAG, "[onContactNotFound] finish activity..");
            finish();
        }

        @Override
        public void onEditOtherContactRequested(
                Uri contactLookupUri, ArrayList<ContentValues> values) {
            Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
            intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    | Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            intent.putExtra(ContactEditorFragment.INTENT_EXTRA_ADD_TO_DEFAULT_DIRECTORY, "");

            // Pass on all the data that has been entered so far
            if (values != null && values.size() != 0) {
                intent.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, values);
            }

            startActivity(intent);
            finish();
        }

        @Override
        public void onCustomCreateContactActivityRequested(AccountWithDataSet account,
                Bundle intentExtras) {
            final AccountTypeManager accountTypes =
                    AccountTypeManager.getInstance(ContactEditorActivity.this);
            final AccountType accountType = accountTypes.getAccountType(
                    account.type, account.dataSet);

            Intent intent = new Intent();
            intent.setClassName(accountType.syncAdapterPackageName,
                    accountType.getCreateContactActivityClassName());
            intent.setAction(Intent.ACTION_INSERT);
            intent.setType(Contacts.CONTENT_ITEM_TYPE);
            if (intentExtras != null) {
                intent.putExtras(intentExtras);
            }
            intent.putExtra(RawContacts.ACCOUNT_NAME, account.name);
            intent.putExtra(RawContacts.ACCOUNT_TYPE, account.type);
            intent.putExtra(RawContacts.DATA_SET, account.dataSet);
            intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    | Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            startActivity(intent);
            finish();
        }

        @Override
        public void onCustomEditContactActivityRequested(AccountWithDataSet account,
                Uri rawContactUri, Bundle intentExtras, boolean redirect) {
            final AccountTypeManager accountTypes =
                    AccountTypeManager.getInstance(ContactEditorActivity.this);
            final AccountType accountType = accountTypes.getAccountType(
                    account.type, account.dataSet);

            Intent intent = new Intent();
            intent.setClassName(accountType.syncAdapterPackageName,
                    accountType.getEditContactActivityClassName());
            intent.setAction(Intent.ACTION_EDIT);
            intent.setData(rawContactUri);
            if (intentExtras != null) {
                intent.putExtras(intentExtras);
            }

            if (redirect) {
                intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        | Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                startActivity(intent);
                finish();
            } else {
                startActivity(intent);
            }
        }
    };

    @Override
    public DialogManager getDialogManager() {
        return mDialogManager;
    }

    @Override
    protected void onDestroy() {
        /** M: Add for SIM Service refactory @{ */
        SIMEditProcessor.unregisterListener(this);
        /** @} */
        super.onDestroy();
    }
}
