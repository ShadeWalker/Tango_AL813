package com.mediatek.contacts;

import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Groups;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseIntArray;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.ContactSaveService;
import com.mediatek.contacts.util.ContactsGroupUtils.USIMGroupException;
import com.mediatek.contacts.util.ContactsGroupUtils;
import com.android.contacts.editor.ContactEditorFragment;
import com.android.contacts.editor.ContactEditorFragment.SaveMode;

import java.util.ArrayList;

public class ContactSaveServiceEx {
    private static final String TAG = ContactSaveServiceEx.class.getSimpleName();
    /// add to update group infos intent keys from groupeditorfragment@{
    public static final String EXTRA_SIM_INDEX_TO_ADD = "simIndexToAdd";
    public static final String EXTRA_SIM_INDEX_TO_REMOVE = "simIndexToRemove";
    public static final String EXTRA_ORIGINAL_GROUP_NAME = "originalGroupName";
    public static final String EXTRA_SIM_INDEX_ARRAY = "simIndexArray";
    public static final String EXTRA_SUB_ID = "subId";
    // add new group name as back item of GroupCreationDialogFragment
    public static final String EXTRA_NEW_GROUP_NAME = "addGroupName";
    //@}
    
    //:[Gemini+] all possible slot error can be safely put in this sparse int array.
    private static SparseIntArray mSubIdError = new SparseIntArray();

    private static  Handler mMainHandler = new Handler(Looper.getMainLooper());

    /**
     * Shows a toast on the UI thread.
     */
    private static void showToast(final int message) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ContactsApplicationEx.getContactsApplication(), message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

   /**
    * add usim group info to create group
    * @param intent The intent used to create new group
    * @param label group label
    * @param simIndexArray sim index add to group
    * @param subId the sub id
    * @return the Intent contains usim info
    */
    public static void addIccForCreateNewGroupIntent(Intent intent, String label,
            final int[] simIndexArray, int subId) {
        intent.putExtra(EXTRA_SIM_INDEX_ARRAY, simIndexArray);
        intent.putExtra(EXTRA_SUB_ID, subId);
        Intent callbackIntent = intent.getParcelableExtra(ContactSaveService.EXTRA_CALLBACK_INTENT);
        callbackIntent.putExtra(EXTRA_SUB_ID, subId);
        callbackIntent.putExtra(EXTRA_NEW_GROUP_NAME, label);
    }

    /**
     * add Icc info for update usim group
     * @param intent The intent used to update icc group
     * @param OriginalGroupName old group name
     * @param subId 
     * @param simIndexToAddArray the sim index will add to this group
     * @param simIndexToRemoveArray the sim index will removed from this group
     * @param account group account
     */
    public static void addIccForGroupUpdateIntent(Intent intent, String OriginalGroupName,
            int subId, int[] simIndexToAddArray, int[] simIndexToRemoveArray,
            AccountWithDataSet account) {
        intent.putExtra(EXTRA_SUB_ID, subId);
        intent.putExtra(EXTRA_SIM_INDEX_TO_ADD, simIndexToAddArray);
        intent.putExtra(EXTRA_SIM_INDEX_TO_REMOVE, simIndexToRemoveArray);
        intent.putExtra(EXTRA_ORIGINAL_GROUP_NAME, OriginalGroupName);
        intent.putExtra(ContactSaveService.EXTRA_ACCOUNT_TYPE, account.type);
        intent.putExtra(ContactSaveService.EXTRA_ACCOUNT_NAME, account.name);
        intent.putExtra(ContactSaveService.EXTRA_DATA_SET, account.dataSet);
        Intent callbackIntent = intent.getParcelableExtra(ContactSaveService.EXTRA_CALLBACK_INTENT);
        callbackIntent.putExtra(EXTRA_SUB_ID, subId);
    }

    /**
     * add Icc info for delete usim group
     * @param intent The intent used to delete group
     * @param subId sub id
     * @param groupLabel the gorup name to be delete
     */
    public static void addIccForGroupDeletionIntent(Intent intent, int subId, String groupLabel) {
        intent.putExtra(EXTRA_SUB_ID, subId);
        intent.putExtra(ContactSaveService.EXTRA_GROUP_LABEL, groupLabel);
    }

    /**
     * M: delete group in icc card,like usim
     * @param intent
     * @param groupId
     * @return true if success,false else.
     */
    public static boolean deleteGroupInIcc(ContactSaveService contactSaveService, Intent intent,
            long groupId) {
        String groupLabel = intent.getStringExtra(contactSaveService.EXTRA_GROUP_LABEL);
        int subId = intent.getIntExtra(EXTRA_SUB_ID, -1);

        if (subId <= 0 || TextUtils.isEmpty(groupLabel)) {
            Log.w(TAG, "[deleteGroupInIcc] subId:" + subId + ",groupLabel:" + groupLabel
                    + " have errors");
            return false;
        }

        // check whether group exists
        int ugrpId = -1;
        try {
            ugrpId = ContactsGroupUtils.USIMGroup.hasExistGroup(subId, groupLabel);
            Log.d(TAG, "[deleteGroupInIcc]ugrpId:" + ugrpId);
        } catch (RemoteException e) {
            e.printStackTrace();
            ugrpId = -1;
        }
        if (ugrpId > 0) {
            // fix ALPS01002380. should not use groupLabel for groupuri,because
            // groupname "/"
            // will lead to SQLite exception.
            Uri groupUri = ContentUris.withAppendedId(Contacts.CONTENT_GROUP_URI, groupId);
            Cursor c = contactSaveService.getContentResolver().query(groupUri,
                    new String[] { Contacts._ID, Contacts.INDEX_IN_SIM },
                    Contacts.INDICATE_PHONE_SIM + " = " + subId, null, null);
            Log.d(TAG, "[deleteGroupInIcc]simId:" + subId + "|member count:"
                    + (c == null ? "null" : c.getCount()));
            try {
                while (c != null && c.moveToNext()) {
                    int indexInSim = c.getInt(1);
                    boolean ret = ContactsGroupUtils.USIMGroup.deleteUSIMGroupMember(subId,
                            indexInSim, ugrpId);
                    Log.d(TAG,
                            "[deleteGroupInIcc]subId:" + subId + "ugrpId:" + ugrpId + "|simIndex:"
                                    + indexInSim + "|Result:" + ret + " | contactid : "
                                    + c.getLong(0));
                }
            } finally {
                if (c != null) {
                    c.close();
                }
            }
            // Delete USIM group
            int error = ContactsGroupUtils.USIMGroup.deleteUSIMGroup(subId, groupLabel);
            Log.d(TAG, "[deleteGroupInIcc]error:" + error);
            if (error != 0) {
                showToast(R.string.delete_group_failure);
                return false;
            }
        }
        return true;
    }

    public static int updateGroupToIcc(ContactSaveService contactSaveService, Intent intent) {
        int[] simIndexToAddArray = intent
                .getIntArrayExtra(EXTRA_SIM_INDEX_TO_ADD);
        int[] simIndexToRemoveArray = intent
                .getIntArrayExtra(EXTRA_SIM_INDEX_TO_REMOVE);
        int subId = intent.getIntExtra(EXTRA_SUB_ID, -1);
        String originalName = intent.getStringExtra(EXTRA_ORIGINAL_GROUP_NAME);
        String accountType = intent.getStringExtra(ContactSaveService.EXTRA_ACCOUNT_TYPE);
        String accountName = intent.getStringExtra(ContactSaveService.EXTRA_ACCOUNT_NAME);
        String groupName = intent.getStringExtra(ContactSaveService.EXTRA_GROUP_LABEL);
        long groupId = intent.getLongExtra(ContactSaveService.EXTRA_GROUP_ID, -1);
        int groupIdInIcc = -1;

        if (subId < 0) {
            Log.w(TAG, "[updateGroupToIcc] subId is error.subId:" + subId);
            return groupIdInIcc;
        }

        Log.d(TAG, "[updateGroupToIcc]groupName:" + groupName + " |groupId:" + groupId
                + "|originalName:" + originalName + " |subId:" + subId + " |accountName:"
                + accountName + " |accountType:" + accountType);

        try {
            groupIdInIcc = ContactsGroupUtils.USIMGroup.syncUSIMGroupUpdate(subId, originalName,
                    groupName);
            Log.d(TAG, groupIdInIcc + "---------ugrpId[updateGroup]");
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (USIMGroupException e) {
            Log.d(TAG,
                    "[SyncUSIMGroup] catched USIMGroupException." + " ErrorType: "
                            + e.getErrorType());
            mSubIdError.put(e.getErrorSubId(), e.getErrorType());
            checkAllSlotErrors();
            Intent callbackIntent = intent
                    .getParcelableExtra(ContactSaveService.EXTRA_CALLBACK_INTENT);
            if (e.getErrorType() == USIMGroupException.GROUP_NAME_OUT_OF_BOUND) {
                callbackIntent.putExtra(ContactEditorFragment.SAVE_MODE_EXTRA_KEY, SaveMode.RELOAD);
            }
            Log.d(TAG, ContactSaveService.EXTRA_CALLBACK_INTENT);
            contactSaveService.deliverCallback(callbackIntent);
            return groupIdInIcc;
        }
        if (groupIdInIcc <= 0) {
            Intent callbackIntent = intent
                    .getParcelableExtra(ContactSaveService.EXTRA_CALLBACK_INTENT);
            Log.d(TAG, ContactSaveService.EXTRA_CALLBACK_INTENT);
            contactSaveService.deliverCallback(callbackIntent);
        }
        return groupIdInIcc;
    }

    public static int createGroupToIcc(ContactSaveService contactSaveService, Intent intent) {
        String accountType = intent.getStringExtra(ContactSaveService.EXTRA_ACCOUNT_TYPE);
        String accountName = intent.getStringExtra(ContactSaveService.EXTRA_ACCOUNT_NAME);
        String groupName = intent.getStringExtra(ContactSaveService.EXTRA_GROUP_LABEL);
        int groupIdInIcc = -1;

        int subId = intent.getIntExtra(EXTRA_SUB_ID, -1);
        if (subId <= 0) {
            Log.w(TAG, "[createGroupToIcc] subId error..subId:" + subId);
            return groupIdInIcc;
        }

        try {
            groupIdInIcc = ContactsGroupUtils.USIMGroup.syncUSIMGroupNewIfMissing(subId, groupName);
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (USIMGroupException e) {
            Log.w(TAG, "[createGroupToIcc] ceate grop fail type:" + e.getErrorType()
                    + ",fail subId:" + e.getErrorSubId());
            mSubIdError.put(e.getErrorSubId(), e.getErrorType());
            checkAllSlotErrors();
            Intent callbackIntent = intent
                    .getParcelableExtra(ContactSaveService.EXTRA_CALLBACK_INTENT);
            if (e.getErrorType() == USIMGroupException.GROUP_NAME_OUT_OF_BOUND) {
                callbackIntent.putExtra(ContactEditorFragment.SAVE_MODE_EXTRA_KEY, SaveMode.RELOAD);
            }
            contactSaveService.deliverCallback(callbackIntent);
        }
        return groupIdInIcc;
    }

    public static boolean checkGroupNameExist(ContactSaveService saveService, String groupName,
            String accountName, String accountType, boolean showTips) {
        boolean nameExists = false;

        if (TextUtils.isEmpty(groupName)) {
            if (showTips) {
                showToast(R.string.name_needed);
            }
            return false;
        }
        Cursor cursor = saveService.getContentResolver().query(
                Groups.CONTENT_SUMMARY_URI,
                new String[] { Groups._ID },
                Groups.TITLE + "=? AND " + Groups.ACCOUNT_NAME + " =? AND " + Groups.ACCOUNT_TYPE
                        + "=? AND " + Groups.DELETED + "=0",
                new String[] { groupName, accountName, accountType }, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                nameExists = true;
            }
            cursor.close();
        }
        // If group name exists, make a toast and return false.
        if (nameExists) {
            if (showTips) {
                showToast(R.string.group_name_exists);
            }
            return false;
        } else {
            return true;
        }
    }

    /**
     * [Gemini+] check all slot to find whether is there any error happened
     */
    public static void checkAllSlotErrors() {
        for (int i = 0; i < mSubIdError.size(); i++) {
            int subId = mSubIdError.keyAt(i);
            int errorCode = mSubIdError.valueAt(i);
            Log.d(TAG, "[showToast] subId " + subId + " encounter a problem: " + errorCode);
            showMoveUSIMGroupErrorToast(errorCode, subId);
        }
        mSubIdError.clear();
    }

    public static void showMoveUSIMGroupErrorToast(int errCode, int subId) {
        Log.d(TAG, "[showMoveUSIMGroupErrorToast]errCode:" + errCode + "|subId:" + subId);
        /** M: Bug Fix for CR ALPS00451441 @{ */
        String toastMsg;
        if (errCode == USIMGroupException.GROUP_GENERIC_ERROR) {
            toastMsg = ContactsApplicationEx.getContactsApplication().getString(
                    R.string.save_group_fail);
        } else {
            toastMsg = ContactsApplicationEx.getContactsApplication().getString(
                    ContactsGroupUtils.USIMGroupException.getErrorToastId(errCode));
        }
        /** @} */
        final String msg = toastMsg;
        if (toastMsg != null) {
            Log.d(TAG, "[showMoveUSIMGroupErrorToast]toastMsg:" + toastMsg);
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ContactsApplicationEx.getContactsApplication(), msg,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * fix ALPS00272729
     * @param operations
     * @param resolver
     */
    public static void bufferOperations(ArrayList<ContentProviderOperation> operations,
            ContentResolver resolver) {
        try {
            Log.d(TAG, "[bufferOperatation] begin applyBatch ");
            resolver.applyBatch(ContactsContract.AUTHORITY, operations);
            Log.d(TAG, "[bufferOperatation] end applyBatch");
            operations.clear();
        } catch (RemoteException e) {
            Log.e(TAG, "[bufferOperatation]Failed to apply aggregation exception batch", e);
            showToast(R.string.contactSavedErrorToast);
        } catch (OperationApplicationException e) {
            Log.e(TAG, "[bufferOperatation]Failed to apply aggregation exception batch", e);
            showToast(R.string.contactSavedErrorToast);
        }
    }
}
