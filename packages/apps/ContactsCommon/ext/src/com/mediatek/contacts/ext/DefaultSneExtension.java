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

public class DefaultSneExtension implements ISneExtension {
    //----------for SIMEditProcessor.java------------//
    /**
     *
     * @param intent The intent to start SIMEditProcessor,it contains the data to be edit
     * @param preContentValues  the ContentValues insert/update to sim card last time.
     * @param indexInSim the index in sim of contact
     * @param subId sub id current edit on
     * @param rawContactId
     */
    @Override
    public void editSimSne(Intent intent, long indexInSim, int subId, long rawContactId){
        //1. get sNickname like getRawContactDataFromIntent()
        //2. get mOldNickname like setOldRawContactData()
        //3.check sNickname isTextValid() like editSIMContact()
        //4.buildNicknameValueForInsert like setUpdateValues()
        //5.save to sim first---if mOldNickname is empty,need insert,else update
        //6.then save to contacts db:updateDataToDb() like editSIMContact()
        //7.for preContentValues: check It is insert/update
        //  if is insert,sames should set the value of newTag/newNumber/newEmail/newAnr same as tag/number/email/anr ?
        //  if is update,sames should set the value of tag/number/email/anr same as newTag/numNumber/newEmail/newAnr ?
    }

    /**
     * @param which processor call checkNickName()
     * @param intent The intent start SIMEditProcessor
     * @param canShowToast true can,false not show toast by plugin
     * @param subId sub id
     * @return 0 if NickName is ok,1 for empty,2 for too long
     */
    @Override
    public int checkNickName(ProcessorBase processor, Intent intent, boolean canShowToast , int subId) {
        //1.get data from intent,refer:getRawContactDataFromIntent
        //2.check if is not empty
        //3.check length and others...
        //4.if checkAlone is true,you can toast,false should not
        //5.check if instanceof SIMEditProcessor,can call backToFragment()
        //default SNE is empty,so return 1
        return 1;
    }

    /**
     * update the contentvalue
     * @param intent The intent to start SIMEditProcessor,it contains the data to be edit
     * @param subId the sub id on
     * @param contentValues the contentValues to be edit
     */
    @Override
    public void updateValues(Intent intent, int subId, ContentValues contentValues) {
        // default do-nothing
    }

    //-------------for SIMImportProcessor.java--------//
    /**
     * import sne data to operationList
     * @param operationList
     * @param cursor The cursor have contacts data
     * @param loopCheck used as previousResult for ContentProviderOperation.withValueBackReference()
     * @return count of sne have import eg:0,1
     */
    @Override
    public int importSimSne(ArrayList<ContentProviderOperation> operationList, Cursor cursor, int loopCheck) {
        //1.get sne from cursor
        //2.build it to operationList
        //default return 0
        return 0;
    }

    //--------------for CopyProcessor.java-------------//
    /**
     *
     * @param operationList
     * @param targetAccount the target account will be copy to
     * @param sourceUri the source uri who have sne data
     * @param backRef  back references
     */
    @Override
    public void copySimSneToAccount(ArrayList<ContentProviderOperation> operationList, Account targetAccount, Uri sourceUri, int backRef){
        //1.get simNickname by uri
        //2.buildInsertOperation for simNickname,like:buildOperation(account.type, operationList, null, nickname, backRef);
    }

    /**
     *
     * @param sourceUri the source uri who have sne data
     * @param subId
     * @accountType
     * @simContentValues
     */
    public void updateValuesforCopy(Uri sourceUri, int subId, String accountType, ContentValues simContentValues) {
            //1.get simNickname by uri
            //update simContentValues
    }

    //--------for ContactEditorFragment.java-----------//
    /**
     *
     * @param entity RawContactDelta in ContactEditorFragment
     * @param type The account type will show sne
     * @param subId sub id current on
     */
    @Override
    public void onEditorBindEditors(RawContactDelta entity, AccountType type, int subId) {
        // refer SneExt.java#ensureNicknameKindForEditorExt()..
        // like:UsimAccountType.updateNickname(),RawContactModifier.ensureKindExists()
    }
}
