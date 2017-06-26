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
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Entity;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.mediatek.contacts.simcontact.SubInfoUtils;
import com.mediatek.contacts.simservice.SIMProcessorService;
import com.mediatek.contacts.simservice.SIMServiceUtils;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.util.PhbStateHandler;
import com.mediatek.contacts.simservice.SIMDeleteProcessor;

import java.util.HashSet;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

import com.mediatek.contacts.interactions.ContactDeletionInteractionUtils;

/**
 * An interaction invoked to delete a contact.
 */
public class ContactDeletionInteraction extends Fragment implements LoaderCallbacks<Cursor>,
        OnDismissListener, SIMDeleteProcessor.Listener, PhbStateHandler.Listener {

    private static final String FRAGMENT_TAG = "deleteContact";

    private static final String KEY_ACTIVE = "active";
    private static final String KEY_CONTACT_URI = "contactUri";
    /** M: key to map sim_uri and sim_index to delete @{ */
    private static final String KEY_CONTACT_SIM_URI = "contact_sim_uri";
    private static final String KEY_CONTACT_SIM_INDEX = "contact_sim_index";
    private static final String KEY_CONTACT_SUB_ID = "contact_sub_id";
    /**@}*/
    private static final String KEY_FINISH_WHEN_DONE = "finishWhenDone";
    public static final String ARG_CONTACT_URI = "contactUri";
    public static final int RESULT_CODE_DELETED = 3;

    private static final String[] ENTITY_PROJECTION = new String[] {
        Entity.RAW_CONTACT_ID, //0
        Entity.ACCOUNT_TYPE, //1
        Entity.DATA_SET, // 2
        Entity.CONTACT_ID, // 3
        Entity.LOOKUP_KEY, // 4
        RawContacts.INDICATE_PHONE_SIM, // 5
        RawContacts.INDEX_IN_SIM, //6
    };

    private static final int COLUMN_INDEX_RAW_CONTACT_ID = 0;
    private static final int COLUMN_INDEX_ACCOUNT_TYPE = 1;
    private static final int COLUMN_INDEX_DATA_SET = 2;
    private static final int COLUMN_INDEX_CONTACT_ID = 3;
    private static final int COLUMN_INDEX_LOOKUP_KEY = 4;
    private static final int COLUMN_INDEX_INDICATE_PHONE_SIM = 5;
    private static final int COLUMN_INDEX_IN_SIM = 6;

    /// M: add for sim conatct
    private static final int ERROR_SUBID = -10;

    private boolean mActive;
    private Uri mContactUri;
    private boolean mFinishActivityWhenDone;
    private Context mContext;
    private AlertDialog mDialog;

    /** This is a wrapper around the fragment's loader manager to be used only during testing. */
    private TestLoaderManager mTestLoaderManager;

    @VisibleForTesting
    int mMessageId;

    /**
     * Starts the interaction.
     *
     * @param activity the activity within which to start the interaction
     * @param contactUri the URI of the contact to delete
     * @param finishActivityWhenDone whether to finish the activity upon completion of the
     *        interaction
     * @return the newly created interaction
     */
    public static ContactDeletionInteraction start(
            Activity activity, Uri contactUri, boolean finishActivityWhenDone) {
        Log.d(FRAGMENT_TAG, "[start] set mSimUri and mSimWhere are null");
        ContactDeletionInteraction deletion = startWithTestLoaderManager(activity, contactUri, finishActivityWhenDone, null);
        return deletion;
    }

    /**
     * Starts the interaction and optionally set up a {@link TestLoaderManager}.
     *
     * @param activity the activity within which to start the interaction
     * @param contactUri the URI of the contact to delete
     * @param finishActivityWhenDone whether to finish the activity upon completion of the
     *        interaction
     * @param testLoaderManager the {@link TestLoaderManager} to use to load the data, may be null
     *        in which case the default {@link LoaderManager} is used
     * @return the newly created interaction
     */
    @VisibleForTesting
    static ContactDeletionInteraction startWithTestLoaderManager(
            Activity activity, Uri contactUri, boolean finishActivityWhenDone,
            TestLoaderManager testLoaderManager) {
        if (contactUri == null) {
            return null;
        }

        FragmentManager fragmentManager = activity.getFragmentManager();
        ContactDeletionInteraction fragment =
                (ContactDeletionInteraction) fragmentManager.findFragmentByTag(FRAGMENT_TAG);
        if (fragment == null) {
            fragment = new ContactDeletionInteraction();
            fragment.setTestLoaderManager(testLoaderManager);
            fragment.setContactUri(contactUri);
            fragment.setFinishActivityWhenDone(finishActivityWhenDone);
            fragmentManager.beginTransaction().add(fragment, FRAGMENT_TAG)
                    .commitAllowingStateLoss();
        } else {
            fragment.setTestLoaderManager(testLoaderManager);
            fragment.setContactUri(contactUri);
            fragment.setFinishActivityWhenDone(finishActivityWhenDone);
        }
        /** M: add for sim contact @ { */
        fragment.mSimUri = null;
        fragment.mSimIndex = -1;
        /** @ } */
        return fragment;
    }

    @Override
    public LoaderManager getLoaderManager() {
        // Return the TestLoaderManager if one is set up.
        LoaderManager loaderManager = super.getLoaderManager();
        if (mTestLoaderManager != null) {
            // Set the delegate: this operation is idempotent, so let's just do it every time.
            mTestLoaderManager.setDelegate(loaderManager);
            return mTestLoaderManager;
        } else {
            return loaderManager;
        }
    }

    /** Sets the TestLoaderManager that is used to wrap the actual LoaderManager in tests. */
    private void setTestLoaderManager(TestLoaderManager mockLoaderManager) {
        mTestLoaderManager = mockLoaderManager;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        /// M: initial progress handler to show delay progressDialog
        mProgressHandler = new ContactDeletionInteractionUtils.ProgressHandler(mContext, this);

        /// M: Add for SIM Service refactory
        SIMDeleteProcessor.registerListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.setOnDismissListener(null);
            mDialog.dismiss();
            mDialog = null;
        }
        /// M: add for sim contact
        PhbStateHandler.getInstance().unRegister(this);

        /** M: Add for SIM Service refactory @{ */
        SIMDeleteProcessor.unregisterListener(this);
        /** @} */
    }

    public void setContactUri(Uri contactUri) {
        mContactUri = contactUri;
        mActive = true;
        if (isStarted()) {
            Bundle args = new Bundle();
            args.putParcelable(ARG_CONTACT_URI, mContactUri);
            getLoaderManager().restartLoader(R.id.dialog_delete_contact_loader_id, args, this);
        }
    }

    private void setFinishActivityWhenDone(boolean finishActivityWhenDone) {
        this.mFinishActivityWhenDone = finishActivityWhenDone;

    }

    /* Visible for testing */
    boolean isStarted() {
        return isAdded();
    }

    @Override
    public void onStart() {
        if (mActive) {
            Bundle args = new Bundle();
            args.putParcelable(ARG_CONTACT_URI, mContactUri);
            getLoaderManager().initLoader(R.id.dialog_delete_contact_loader_id, args, this);
        }
        /// M: add for sim contact
        PhbStateHandler.getInstance().register(this);
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mDialog != null) {
            mDialog.hide();
        }
        /// M: add for sim contact
        PhbStateHandler.getInstance().unRegister(this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri contactUri = args.getParcelable(ARG_CONTACT_URI);
        return new CursorLoader(mContext,
                Uri.withAppendedPath(contactUri, Entity.CONTENT_DIRECTORY), ENTITY_PROJECTION,
                null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {

        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }

        if (!mActive) {
            LogUtils.w(FRAGMENT_TAG, "#onLoadFinished(),the mActive is false,Cancle execute!");
            return;
        }

        long contactId = 0;
        String lookupKey = null;

        // This cursor may contain duplicate raw contacts, so we need to de-dupe them first
        HashSet<Long>  readOnlyRawContacts = Sets.newHashSet();
        HashSet<Long>  writableRawContacts = Sets.newHashSet();

        AccountTypeManager accountTypes = AccountTypeManager.getInstance(getActivity());
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            final long rawContactId = cursor.getLong(COLUMN_INDEX_RAW_CONTACT_ID);
            final String accountType = cursor.getString(COLUMN_INDEX_ACCOUNT_TYPE);
            final String dataSet = cursor.getString(COLUMN_INDEX_DATA_SET);
            contactId = cursor.getLong(COLUMN_INDEX_CONTACT_ID);
            lookupKey = cursor.getString(COLUMN_INDEX_LOOKUP_KEY);
            mSubId = cursor.getInt(COLUMN_INDEX_INDICATE_PHONE_SIM);
            mSimIndex = cursor.getInt(COLUMN_INDEX_IN_SIM);
            mSimUri = SubInfoUtils.getIccProviderUri(mSubId);
            AccountType type = accountTypes.getAccountType(accountType, dataSet);
            boolean writable = type == null || type.areContactsWritable();
            if (writable) {
                writableRawContacts.add(rawContactId);
            } else {
                readOnlyRawContacts.add(rawContactId);
            }
        }

        int readOnlyCount = readOnlyRawContacts.size();
        int writableCount = writableRawContacts.size();
        if (readOnlyCount > 0 && writableCount > 0) {
            mMessageId = R.string.readOnlyContactDeleteConfirmation;
        } else if (readOnlyCount > 0 && writableCount == 0) {
            mMessageId = R.string.readOnlyContactWarning;
        } else if (readOnlyCount == 0 && writableCount > 1) {
            mMessageId = R.string.multipleContactDeleteConfirmation;
        } else {
            mMessageId = R.string.deleteConfirmation;
        }

        final Uri contactUri = Contacts.getLookupUri(contactId, lookupKey);
        showDialog(mMessageId, contactUri);

        // We don't want onLoadFinished() calls any more, which may come when the database is
        // updating.
        getLoaderManager().destroyLoader(R.id.dialog_delete_contact_loader_id);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    private void showDialog(int messageId, final Uri contactUri) {
        mDialog = new AlertDialog.Builder(getActivity())
        /** M: Change Feature change dialog style @{ */
        .setTitle(R.string.deleteConfirmation_title)
        /** @} */
        .setIconAttribute(android.R.attr.alertDialogIcon)
        .setMessage(messageId).setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.dial_delete,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        doDeleteContact(contactUri);
                    }
                }).create();
        mDialog.setOnDismissListener(this);
        mDialog.show();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        mActive = false;
        mDialog = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_ACTIVE, mActive);
        outState.putParcelable(KEY_CONTACT_URI, mContactUri);
        /** M: to save sim_uri and sim_index to delete @{ */
        outState.putParcelable(KEY_CONTACT_SIM_URI, mSimUri);
        outState.putInt(KEY_CONTACT_SIM_INDEX, mSimIndex);
        outState.putInt(KEY_CONTACT_SUB_ID, mSubId);
        /**@}*/
        outState.putBoolean(KEY_FINISH_WHEN_DONE, mFinishActivityWhenDone);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            mActive = savedInstanceState.getBoolean(KEY_ACTIVE);
            mContactUri = savedInstanceState.getParcelable(KEY_CONTACT_URI);
            /** M: to get sim_uri and sim_index to delete @{ */
            mSimUri = savedInstanceState.getParcelable(KEY_CONTACT_SIM_URI);
            mSimIndex = savedInstanceState.getInt(KEY_CONTACT_SIM_INDEX);
            mSubId = savedInstanceState.getInt(KEY_CONTACT_SUB_ID);
            /**@}*/
            mFinishActivityWhenDone = savedInstanceState.getBoolean(KEY_FINISH_WHEN_DONE);
        }
    }

    protected void doDeleteContact(final Uri contactUri) {
        /** M: Add for SIM Contact @{ */
        if (!isAdded()) {
            LogUtils.w(FRAGMENT_TAG, "This Fragment is not add to the Activity.");
            return;
        }
        if (!ContactDeletionInteractionUtils.doDeleteSimContact(mContext,
                 contactUri, mSimUri, mSimIndex, mSubId, this)) {
            /** @} */
            mContext.startService(ContactSaveService.createDeleteContactIntent(mContext, contactUri));
            if (isAdded() && mFinishActivityWhenDone) {
                Log.d(FRAGMENT_TAG, "doDeleteContact -finished");
                getActivity().setResult(RESULT_CODE_DELETED);
                getActivity().finish();
            }
        }
    }

    private Uri mSimUri = null;
    private int mSimIndex = -1;
    /// M: change for SIM Service refactoring
    private static int mSubId = SubInfoUtils.getInvalidSubId();

    /** M: show loading when load data in back ground @{ */
    private ContactDeletionInteractionUtils.ProgressHandler mProgressHandler;
    /** @}*/

    /** M: Add for SIM Service refactory @{ */
    @Override
    public void onSIMDeleteFailed() {
        if (isAdded()) {
            getActivity().finish();
        }
        return;
    }

    @Override
    public void onSIMDeleteCompleted() {
        if (isAdded() && mFinishActivityWhenDone) {
            getActivity().setResult(RESULT_CODE_DELETED);
            getActivity().finish();
        }
        return;
    }

    @Override
    public void onPhbStateChange(int subId) {
        if (subId == mSubId) {
            Log.d(FRAGMENT_TAG , "onReceive,subId:" + subId + ",finish EditorActivity.");
            getActivity().setResult(RESULT_CODE_DELETED);
            getActivity().finish();
            return;
        }
    }
    /** @} */
}
