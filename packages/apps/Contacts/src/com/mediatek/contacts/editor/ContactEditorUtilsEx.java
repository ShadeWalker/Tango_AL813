package com.mediatek.contacts.editor;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts.Data;
import android.telephony.SubscriptionInfo;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.activities.ContactEditorActivity;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.RawContactDeltaList;
import com.android.contacts.common.model.RawContactModifier;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.account.BaseAccountType;
import com.android.contacts.common.model.dataitem.DataItem;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.common.model.dataitem.StructuredNameDataItem;
import com.android.contacts.editor.KindSectionView;
import com.android.contacts.editor.PhoneticNameEditorView;
import com.android.contacts.editor.PhotoEditorView;
import com.android.contacts.editor.RawContactEditorView;
import com.android.contacts.editor.ViewIdGenerator;
import com.android.contacts.editor.ContactEditorFragment.PhotoHandler;
import com.android.contacts.editor.StructuredNameEditorView.SavedState;
import com.mediatek.contacts.ExtensionManager;
import com.mediatek.contacts.model.AccountWithDataSetEx;
import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.simcontact.SubInfoUtils;
import com.mediatek.contacts.simcontact.SlotUtils;
import com.mediatek.contacts.util.AccountTypeUtils;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.ext.IIccCardExtension;
import com.mediatek.contacts.ext.IAasExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContactEditorUtilsEx {

    private static String TAG = "ContactEditorUtilsEx";

    /**
     * The default length of the Field.
     */
    private static final int DEFAULT_FIELD_VIEW_MAX_LENGTH = 128;

    /**
     * The long length of the Field.
     */
    private static final int LONG_FIELD_VIEW_MAX_LENGTH = 1024;

    /**
     * The Map to store the Field Editor type & their max length can input.
     */
    private static final Map<Integer, Integer> FIELD_VIEW_MAX_LENGTH_MAP =
            new HashMap<Integer, Integer>();
    static {
        FIELD_VIEW_MAX_LENGTH_MAP.put(BaseAccountType.getTypeNote(), LONG_FIELD_VIEW_MAX_LENGTH);
        FIELD_VIEW_MAX_LENGTH_MAP.put(BaseAccountType.getTypeWebSite(),
                LONG_FIELD_VIEW_MAX_LENGTH);
    }

    /**
    * @param inputType The type of the Field Editor View, like "note" or "phone".
    * @return the max length of this Field Editor can input
    */
   public static int getFieldEditorLengthLimit(int inputType) {
       int length = FIELD_VIEW_MAX_LENGTH_MAP.containsKey(inputType) ?
               FIELD_VIEW_MAX_LENGTH_MAP.get(inputType) : DEFAULT_FIELD_VIEW_MAX_LENGTH;
       return length;
   }

    /**
     * Bug fix for ALPS00477285.
     * Restore the StructuredNameDataItem in {@link #onRestoreInstanceState(Parcelable)}.
     * If the dataItem is not an instanceof StructuredNameDataItem.
     * It will initial the item in {@link #setValues(DataKind kind, ValuesDelta entry,
     * RawContactDelta state, boolean readOnly, ViewIdGenerator vig)}
     * @param ss The SavedState the passed from
     *            {@link #onRestoreInstanceState(Parcelable)}
     * @param snapshot StructuredNameDataItem
     */
    public static void restoreStructuredNameDataItem(SavedState ss,
            StructuredNameDataItem snapshot) {
        DataItem dataItem = DataItem.createFrom(ss.mSnapshot);

        if (dataItem instanceof StructuredNameDataItem) {
            snapshot = (StructuredNameDataItem) dataItem;
        } else {
            LogUtils.w(TAG, "The dataItem is not an instance of StructuredNameDataItem!!!"
                    + " mimeType: " + ss.mSnapshot.getAsString(Data.MIMETYPE));
        }
    }

    /**
     * M: Change feature: AccountSwitcher.
     * This method is the extension of google method getDefaultAccount() after added
     * the sim/usim account.
     * If the default account is sim account, covert it to AccountWithDataSetEx.
     * Additionally, if the default account is sim account, need judge whether the sim card's state
     * is ready, if not, take the local phone account as the default account.
     * @param prefs SharedPreferences
     * @param currentWritableAccounts List<AccountWithDataSet>
     * @param keyDefaultAccount String
     * @param keyKnownAccounts String
     * @return AccountWithDataSet the default account.
     */
    public static AccountWithDataSet getDefaultAccountEx(SharedPreferences prefs,
            List<AccountWithDataSet> currentWritableAccounts, String keyDefaultAccount,
            String keyKnownAccounts) {
        final String saved = prefs.getString(keyDefaultAccount, null);
        if (TextUtils.isEmpty(saved)) {
            return null;
        }
        try {
            AccountWithDataSet defaultAccount = AccountWithDataSet.unstringify(saved);
            // final List<AccountWithDataSet> currentWritableAccounts = getWritableAccounts();
            for (AccountWithDataSet account : currentWritableAccounts) {
                if (account.equals(defaultAccount)) {
                    defaultAccount = account;
                }
            }

            if (defaultAccount instanceof AccountWithDataSetEx) {
                Log.d(TAG, "The default account is sim account: " + defaultAccount);
                if (!SimCardUtils.checkPHBState(null, ((AccountWithDataSetEx) defaultAccount)
                        .getSubId())) {
                    for (AccountWithDataSet account : currentWritableAccounts) {
                        if (AccountTypeUtils.ACCOUNT_TYPE_LOCAL_PHONE
                                .equals(account.type)) {
                            Log.d(TAG, "Sim account is not ready, " +
                                    "take the local phone account as the default account!");
                            defaultAccount = account;
                        }
                    }
                }
            }
            return defaultAccount;
        } catch (IllegalArgumentException exception) {
            Log.e(TAG, "Error with retrieving default account " + exception.toString());
            // unstringify() can throw an exception if the string is not in an expected format.
            // Hence, if the preferences file is corrupt, just reset the preference values
            prefs.edit().putString(keyKnownAccounts, "").putString(keyDefaultAccount, "").apply();
            return null;
        }
    }

    /**
     * For sim type, need add sim item.
     * @param fields ViewGroup
     * @param phoneticNameView ViewGroup
     */
    public static void simTypeSetState(ViewGroup fields, PhoneticNameEditorView phoneticNameView) {
        Log.d(TAG, "simTypeSetState count = " + fields.getChildCount());
        for (int i = 0; i < fields.getChildCount(); i++) {
            View child = fields.getChildAt(i);
            if (child instanceof KindSectionView) {
                final KindSectionView sectionView = (KindSectionView) child;
                if (sectionView.getEditorCount() > 0) {
                    continue;
                }
                DataKind kind = sectionView.getKind();
                if ((kind.typeOverallMax == 1) && sectionView.getEditorCount() != 0) {
                    continue;
                }
                if (DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME.equals(kind.mimeType)) {
                    continue;
                }

                if (DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME.equals(kind.mimeType)
                        && phoneticNameView.getVisibility() == View.VISIBLE) {
                    continue;
                }
                Log.i(TAG, "The child :" + child);

                /** M: Bug Fix for CR ALPS00328644 @{ */
                /*
                 * Original Code: ((KindSectionView) child).addItem();
                 */
                // L-mr1 disable KindSectionView.addItem() function.
                // ((KindSectionView) child).addSimItem();
                /** @} */
            }
        }
    }

    /**
     * If the passed account type is icc card account type, return true, else false.
     * @param accountType String
     * @return If icc card account type, return true, else false.
     */
    public static boolean isIccCardAccountType(String accountType) {
        if (AccountTypeUtils.isAccountTypeIccCard(accountType)) {
            return true;
        }
        return false;
    }

    /**
     * If subId is invalid, finish the passed activity.
     * @param context Context
     * @param subId Int
     * @return true if subId invalid.
     */
    public static boolean finishActivityIfInvalidSubId(Context context, int subId) {
        if (subId < 1) {
            Log.w(TAG, "[setState] mSubId < 1, may be the sim card has been plugin out!");
            Toast.makeText(context.getApplicationContext(), R.string.icc_phone_book_invalid,
                    Toast.LENGTH_SHORT).show();
            Activity activity = (Activity) context;
            activity.finish();
            return true;
        }
        return false;
    }

    /**
     * Replace icon use OP09 default drawable.
     * @param subId SubId for SubscriptionInfo.
     * @param photoEditorView PhotoEditorView
     */
    public static void setDefaultIconForEditor(int subId, PhotoEditorView photoEditorView) {
        SubscriptionInfo subscriptionInfo = SubInfoUtils.getSubInfoUsingSubId(subId);
        IIccCardExtension ext = ExtensionManager.getInstance().getIccCardExtension();
        photoEditorView.setDefaultIconDrawable(
                ext.getIconDrawableBySimInfoRecord(subscriptionInfo));
    }

    /**
     * Save group metadata to intent.
     * @param state:
     * @param intent:
     * @param mGroupMetaData:
     */
    public static void processGroupMetadataToSim(RawContactDeltaList state,
            Intent intent, Cursor mGroupMetaData) {
        int i = 0;
        if (mGroupMetaData != null) {
            int groupNum = mGroupMetaData.getPosition();
            String accountType = state.get(0).getValues().getAsString(RawContacts.ACCOUNT_TYPE);
            if ((accountType.equals(AccountTypeUtils.ACCOUNT_TYPE_USIM)
                    || AccountTypeUtils.ACCOUNT_TYPE_CSIM.equals(accountType))
                    && groupNum > 0) {
                String groupName[] = new String[groupNum];
                long groupId[] = new long[groupNum];
                mGroupMetaData.moveToPosition(-1);
                while (mGroupMetaData.moveToNext()) {
                    LogUtils.d(TAG, "THE ACCOUNT_NAME is = "
                            + mGroupMetaData.getString(GroupMetaDataLoader.ACCOUNT_NAME));
                    LogUtils.d(TAG, "THE DATA_SET is = "
                            + mGroupMetaData.getString(GroupMetaDataLoader.DATA_SET));
                    LogUtils.d(TAG, "THE GROUP_ID is = "
                            + mGroupMetaData.getLong(GroupMetaDataLoader.GROUP_ID));
                    LogUtils.d(TAG, "THE TITLE is = "
                            + mGroupMetaData.getString(GroupMetaDataLoader.TITLE));
                    groupName[i] = mGroupMetaData.getString(GroupMetaDataLoader.TITLE);
                    groupId[i] = mGroupMetaData.getLong(GroupMetaDataLoader.GROUP_ID);
                    i++;
                    LogUtils.d(TAG, "[saveToSimCard] I : " + i);
                }
                intent.putExtra("groupName", groupName);
                intent.putExtra("groupNum", groupNum);
                intent.putExtra("groupId", groupId);
                LogUtils.d(TAG, "[saveToSimCard] groupNum : " + groupNum);

            }
        }
    }

    /**
     * Show or hide input method
     * @param visible:
     * @param context:
     */
    public static void setInputMethodVisible(boolean visible, Context context) {
        ContactEditorActivity activity = (ContactEditorActivity) context;
        int mode = visible ? WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                : WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        mode = mode | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        activity.getWindow().setSoftInputMode(mode);
    }


    /**
     * Update the RawContactId of photo in mUpdatedPhotos.
     * @param oldState:
     * @param newState:
     * @param updatedPhotos:
     */
    public static void updatePhotoState(RawContactDelta oldState,
            RawContactDelta newState, Bundle updatedPhotos) {
        Log.d(TAG, "mUpdatedPhotos: " +  updatedPhotos);
        if (updatedPhotos == null || oldState == null || newState == null) {
            return;
        }

        String key = String.valueOf(oldState.getRawContactId());
        if (updatedPhotos.containsKey(key)) {
            Uri photoUri = updatedPhotos.getParcelable(key);
            updatedPhotos.remove(key);
            updatedPhotos.putParcelable(String.valueOf(newState.getRawContactId()), photoUri);
            LogUtils.d(TAG, "Update the RawContactId of photoUri contained in mUpdatedPhotos,"
                   + " from old RawContactId " + oldState.getRawContactId()
                   + " to new RawContactId " + newState.getRawContactId());
        }
    }

    /**
     * Update aas view.
     * @param state:
     * @param content:
     */
    public static void updateAasView(RawContactDeltaList state, LinearLayout content) {
        int numRawContacts = state.size();
        for (int i = 0; i < numRawContacts; i++) {
            final RawContactDelta rawContactDelta = state.get(i);
            ExtensionManager.getInstance().getAasExtension().updateView(rawContactDelta,
                    content, null, IAasExtension.VIEW_UPDATE_LABEL);
        }
    }

    /**
     * Show sim sip totast.
     * @param context:
     */
    public static void showSimSipTip(Context context) {

        ContactEditorActivity activity = (ContactEditorActivity) context;
        if (RawContactModifier.mIsSimType) {
            RawContactModifier.mIsSimType = false;
            Toast.makeText(activity, R.string.add_SIP_to_sim,
                    Toast.LENGTH_LONG).show();
            activity.finish();
        } else if (RawContactModifier.mHasSip) {
            RawContactModifier.mHasSip = false;
            Toast.makeText(activity, R.string.already_has_SIP,
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Set sim card data kind max count first for CR ALPS01447420
     * @param newAccountType:
     * @param subId:
     */
    public static void setSimDataKindCountMax(final AccountType newAccountType, int subId) {
        for (DataKind kind : newAccountType.getSortedDataKinds()) {
            if ((AccountTypeUtils.ACCOUNT_TYPE_USIM.equals(newAccountType.accountType)
                    || AccountTypeUtils.ACCOUNT_TYPE_CSIM.equals(newAccountType.accountType))
                    && kind.mimeType.equals(Phone.CONTENT_ITEM_TYPE) && kind.typeOverallMax <= 0) {
                kind.typeOverallMax = SlotUtils.getUsimAnrCount(subId) + 1;
            }
        }
    }
    /**
     * Clear all children foucs.
     * @param content:
     */
    public static void clearChildFoucs(LinearLayout content) {
        LogUtils.d(TAG, " save reload and mLookupUri is null");
        if (content != null) {
            int count = content.getChildCount();
            for (int i = 0; i < count; i++) {
                content.getChildAt(i).clearFocus();
            }
        }
    }

    /**
     * Remove all popup menu and destroy photo handler.
     * @param content:
     * @param photoHandler:
     */
    public static void removeAllPopMenu(LinearLayout content, PhotoHandler photoHandler) {
        /** M:For CR ALPS01426924 remove pop up menu when onstop @ { */
        if (photoHandler != null) {
            Log.d(TAG, "destory pop menu");
            photoHandler.destroy();
        }
        /** @} */
    }

    /**
     * show Raw contact deltalist.
     * @param state:
     */
    public static void showLogContactState(RawContactDeltaList state) {
        if (state != null) {
            LogUtils.i(TAG, "[bindEditor] state size = " + state.size());
        }
    }

}
