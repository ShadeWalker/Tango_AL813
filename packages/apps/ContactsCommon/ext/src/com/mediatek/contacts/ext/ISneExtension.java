package com.mediatek.contacts.ext;

import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.vcard.ProcessorBase;

import java.util.ArrayList;

public interface ISneExtension {
    // ----------for SIMEditProcessor.java------------//
    /**
     *
     * @param intent The intent to start SIMEditProcessor,it contains the data to be edit
     * @param preContentValues  the ContentValues insert/update to sim card last time.
     * @param indexInSim the index in sim of contact
     * @param subId sub id current edit on
     * @param rawContactId
     */
    public void editSimSne(Intent intent, long indexInSim, int subId, long rawContactId);

    /**
     * @param which processor call checkNickName()
     * @param intent The intent start SIMEditProcessor
     * @param canShowToast true can,false not show toast by plugin
     * @param subId sub id
     * @return 0 if NickName is ok,1 for empty,2 for too long
     */
    public int checkNickName(ProcessorBase processor, Intent intent, boolean canShowToast, int subId);

    /**
     * update the contentvalue
     * @param intent The intent to start SIMEditProcessor,it contains the data to be edit
     * @param subId the sub id on
     * @param contentValues the contentValues to be edit
     */
    public void updateValues(Intent intent, int subId, ContentValues contentValues);

    // -------------for SIMImportProcessor.java--------//
    /**
     * import sne data to operationList
     *
     * @param operationList
     * @param cursor
     *            The cursor have contacts data
     * @param loopCheck
     *            used as previousResult for
     *            ContentProviderOperation.withValueBackReference()
     * @return count of sne have import eg:0,1
     */
    public int importSimSne(ArrayList<ContentProviderOperation> operationList, Cursor cursor, int loopCheck);

    // --------------for CopyProcessor.java-------------//
    /**
     * copy sne data to operationList
     * @param operationList
     * @param targetAccount
     *            the target account will be copy to
     * @param sourceUri
     *            the source uri who have sne data
     * @param backRef  back references
     */
    public void copySimSneToAccount(ArrayList<ContentProviderOperation> operationList, Account targetAccount, Uri sourceUri, int backRef);

    /**
     * update the simContentValues will write to icc provide
     * @param sourceUri
     *            the source uri who have sne data
     * @param subId
     *            the subId
     * @param accountType
     * @param simContentValues
     */
    public void updateValuesforCopy(Uri sourceUri, int subId, String accountType, ContentValues simContentValues);
    // --------for ContactEditorFragment.java-----------//
    /**
     * ensure phone kind updated and exists
     * @param entity
     *            RawContactDelta in ContactEditorFragment
     * @param type
     *            The account type will show sne
     * @param subId
     *            sub id current on
     */
    public void onEditorBindEditors(RawContactDelta entity, AccountType type, int subId);
}
