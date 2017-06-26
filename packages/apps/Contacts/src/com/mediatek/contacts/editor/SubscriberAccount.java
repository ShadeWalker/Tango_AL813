package com.mediatek.contacts.editor;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.util.Log;
import android.widget.ListPopupWindow;

import com.android.contacts.activities.ContactEditorActivity;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.RawContact;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.RawContactDeltaList;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.editor.BaseRawContactEditorView;
import com.android.contacts.editor.ContactEditorFragment.PhotoHandler;
import com.android.contacts.util.UiClosables;

import com.google.common.collect.ImmutableList;

import com.mediatek.contacts.ExtensionManager;
import com.mediatek.contacts.model.AccountWithDataSetEx;
import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.simcontact.SlotUtils;
import com.mediatek.contacts.simcontact.SubInfoUtils;
import com.mediatek.contacts.util.AccountTypeUtils;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.util.ProgressHandler;

public class SubscriberAccount {

    public final String KEY_SLOTID = "key_slotid";
    public final String KEY_SIMID = "key_simid";
    public final String KEY_SAVEMODE_FOR_SIM = "key_savemode_for_sim";
    public final String KEY_OLDSTATE = "key_oldstate";
    public final String KEY_INDICATE_PHONE_OR_SIM = "key_indicate_phone_or_sim";
    public final String KEY_SIM_INDEX = "key_sim_index";
    /// Bug fix ALPS01585520
    public final String KEY_IS_JOIN = "key_is_join";
    public final int MODE_SIM_INSERT = 1;
    public final int MODE_SIM_EDIT = 2;

    public static final int REQUEST_CODE_SAVE_TO_SIM = 4;
    public static final String KEY_SCALE_UP_IF_NEEDED = "scaleUpIfNeeded";
    public static final int SHOW_PROGRESS_DELAY = 500;
    private static final String TAG = "ContactEditorFragment";

    private int mIndicatePhoneOrSimContact;
    private boolean mIsSaveToSim = false;
    private int mSaveModeForSim;
    private int mSlotId = -1;
    private int mSubId = -1;
    private RawContactDeltaList mOldState;
    private boolean mIsSimType = false;
    private boolean mNewSimType = false;

    private boolean mNeedFinish = false;
    private int mSimIndex = -1;
    /** add to resolve cr: ALPS01472207  */
    private boolean mIsJoin;
    /// ALPS01835410
    private ListPopupWindow mAccountSwitcherPopup;

    /**ALPS00403629 add to show inputmethod at necessary */
    private boolean mIsShowIME = true;
    ProgressHandler mProgressHandler = new ProgressHandler();



    public SubscriberAccount(){

    }

    /**
     * Change feature: AccountSwitcher.
     * Set the sim info variables of this contact editor.
     * @param account the corresponding sim account of this contact.
     */
    public void setSimInfo(AccountWithDataSetEx account) {
        mSubId = account.getSubId();
        mNewSimType = true;
        mIsSimType = true;

        ExtensionManager.getInstance().getAasExtension().setCurrentSubId(mSubId);
    }


    /**
     * Change feature: AccountSwitcher.
     * Clear the sim info variables.
     */
    public void clearSimInfo() {
        mSlotId = SlotUtils.getNonSlotId();
        mSubId = -1;
        mNewSimType = false;
        mIsSimType = false;
        ExtensionManager.getInstance().getAasExtension().setCurrentSubId(mSubId);
    }

    /**
     * Insert Raw data to sim card.
     *  @param rawContacts:
     */
   public void insertRawDataToSim(ImmutableList<RawContact> rawContacts) {
       LogUtils.i(TAG, "call bindEditorsforExistingContact");
       /*
        * New Feature by Mediatek Begin. Original Android's code: CR
        * ID:ALPS00101852 Descriptions: insert data to SIM/USIM.
        */
       mSaveModeForSim = MODE_SIM_EDIT;
       mOldState = new RawContactDeltaList();
       mOldState.addAll(rawContacts.iterator());
       ContactEditorUtilsEx.showLogContactState(mOldState);
   }


   /**
    * Get Sub Id
    * @param contact.
    */
   public void initIccCard(Contact contact) {
       mIndicatePhoneOrSimContact = contact.getIndicate();
       mSubId = contact.getIndicate();
       LogUtils.i(TAG, "[bindEditorsForExistingContact]the subId " + "is = "
               + mSubId);
   }

   /**
    * Restore Icc info.
    * @param savedState.
    */
   public void restoreSimAndSubId(Bundle savedState) {
       mSubId = savedState.getInt(KEY_SIMID);
       mSaveModeForSim = savedState.getInt(KEY_SAVEMODE_FOR_SIM);
       mIndicatePhoneOrSimContact = savedState.getInt(KEY_INDICATE_PHONE_OR_SIM);
       mOldState = savedState.<RawContactDeltaList> getParcelable(KEY_OLDSTATE);
       mSimIndex = savedState.getInt(KEY_SIM_INDEX);
       LogUtils.i(TAG, "[onCreate] mSlotid : " + mSlotId + " | mSimId : " + mSubId
               + " | mSaveModeForSim : " + mSaveModeForSim);
       ContactEditorUtilsEx.showLogContactState(mOldState);
       /// Bug fix ALPS01585520
       mIsJoin = savedState.getBoolean(KEY_IS_JOIN);
   }

   /**
    * Set saveToSim flag.
    * @param state.
    * @param context.
    */
   public boolean setIsSaveToSim(RawContactDeltaList state, Context context) {
       LogUtils.i(TAG, "[onStop] and the mIsSaveToSim is " + mIsSaveToSim + " | isSimType() : "
               + isIccAccountType(state));
       if (mIsSaveToSim || isIccAccountType(state)) {
           mIsSaveToSim = false;
           return true;
       }

       ///fix ALPS01187562 remove dialog when the activity is call onStop@{
       ContactEditorActivity activity = (ContactEditorActivity) context;
       activity.getDialogManager().removeAllDialogs();
       /// @}
       return false;
   }
   /**
    * Check account type which is sim type
    * @param state.
    */
   public boolean isIccAccountType(RawContactDeltaList state) {
       boolean checkAccount = false;
       if (!state.isEmpty()) {
           String accountType = state.get(0).getValues().getAsString(RawContacts.ACCOUNT_TYPE);
           checkAccount = AccountTypeUtils.isAccountTypeIccCard(accountType);
       }
       LogUtils.d(TAG, "[isSimType] mIsSimType : " + mIsSimType + " | mNewSimType : " + mNewSimType
               + " | checkAccount : " + checkAccount);
       return mIsSimType || mNewSimType || checkAccount;
   }
   /**
    * Get Icc account type.
    * @param state.
    */
   public void getIccAccountType(RawContactDeltaList state) {
       String accountType = state.get(0).getValues().getAsString(RawContacts.ACCOUNT_TYPE);
       mIsSimType = AccountTypeUtils.isAccountTypeIccCard(accountType);
   }
   /**
    * Set save mode and get Icc account type.
    * @param newAccountType.
    */
   public void setSimSaveMode(final AccountType newAccountType) {
       mSaveModeForSim = MODE_SIM_INSERT;
       if (newAccountType != null) {
           mIsSimType = AccountTypeUtils.isAccountTypeIccCard(newAccountType.accountType);
       }
   }

   /**
    * Disable triganle affordance.
    * @param editor.
    * @param state.
    */
   public void disableTriangleAffordance(final BaseRawContactEditorView editor, RawContactDeltaList state) {
       if (isIccAccountType(state)) {
           mIsSaveToSim = true;
           if (editor.getPhotoEditor() != null) {
               editor.getPhotoEditor().disableTriangleAffordance();
           }
       }
   }

   /**
    * Save sim state to bundle.
    * @param outState:
    */
   public void onSaveInstanceStateSim(Bundle outState) {
       outState.putInt(KEY_SLOTID, mSlotId);
       outState.putInt(KEY_SIMID, mSubId);
       outState.putInt(KEY_SAVEMODE_FOR_SIM, mSaveModeForSim);
       outState.putInt(KEY_INDICATE_PHONE_OR_SIM, mIndicatePhoneOrSimContact);
       outState.putInt(KEY_SIM_INDEX, mSimIndex);
       if (mOldState != null && mOldState.size() > 0) {
           outState.putParcelable(KEY_OLDSTATE, mOldState);
       }
       LogUtils.d(TAG, "[onSaveInstanceState mSlotId : " + mSlotId + " | mSimId : " + mSubId
               + " | mSaveModeForSim : " + mSaveModeForSim);
       LogUtils.d(TAG, "[onSaveInstanceState mOldState : " + mOldState);
       ///Bug fix ALPS01585520
       outState.putBoolean(KEY_IS_JOIN, mIsJoin);
   }

   /**
    * Get subId and Sim type etc.
    * @param data:
    */
   public void setAccountChangedSim(Intent data, Context context) {

       ContactEditorActivity activity = (ContactEditorActivity) context;
       /**
        * M:ALPS00403629 to show the soft inputmethod after
        * ContactEditorAccountsChangedActivity @{
        */
       mIsShowIME = true;
       ContactEditorUtilsEx.setInputMethodVisible(true, activity);
       /** @}*/
       /*
        * New Feature by Mediatek Begin. Original Android's code:
        * CR ID: ALPS00101852 Descriptions: create sim/usim contact
        */
       mSubId = data.getIntExtra("mSimId", -1);
       mNewSimType = data.getBooleanExtra("mIsSimType", false);
       LogUtils.d(TAG, "msubId: " + mSubId);
       /*
        * @}.
        */
   }

   /**
    * Set sim info to intent.
    * @param intent:
    * @param lookupUri:
    */
   public void processSaveToSim(Intent intent, Uri lookupUri) {
       if (mSaveModeForSim == MODE_SIM_INSERT) {
           intent.putExtra(RawContacts.INDICATE_PHONE_SIM,  mSubId);
       } else if (mSaveModeForSim == MODE_SIM_EDIT) {
           intent.putExtra(RawContacts.INDICATE_PHONE_SIM,  mIndicatePhoneOrSimContact);
           intent.putExtra("simIndex", mSimIndex);
           LogUtils.d(TAG, "[saveToSimCard] mIndicatePhoneOrSimContact , mSlotId , mSimIndex: "
                   + mIndicatePhoneOrSimContact + " , " + mSlotId + " , " + mSimIndex);
       }
       LogUtils.d(TAG, "[saveToSimCard] mSaveModeForSim : " + mSaveModeForSim + "subId:" + mSubId);

       intent.putExtra("simSaveMode", mSaveModeForSim);
       intent.setData(lookupUri);
   }

   /**
    * AccountSwitcher
    *
    * @param currentState:
    * @param newAccount:
    */
   public boolean setAccountSimInfo(final RawContactDelta currentState, AccountWithDataSet newAccount,
           PhotoHandler mCurrentPhotoHandler, Context context) {
       ContactEditorActivity activity = (ContactEditorActivity) context;
       if (newAccount instanceof AccountWithDataSetEx) {
           if (!SimCardUtils.checkPHBState(activity, ((AccountWithDataSetEx) newAccount).getSubId())) {
               return true;
           }
           // Remove contacts photo when switch account to SIM type.
           if (mCurrentPhotoHandler != null) {
               mCurrentPhotoHandler.removePictureChosen();
               Log.d(TAG, "remove photo as switch to sim account");
           }
           // Remove photo saved in currentState when switch account to SIM
           // type.
           if (currentState != null && currentState.hasMimeEntries(Photo.CONTENT_ITEM_TYPE)) {
               currentState.removeEntry(Photo.CONTENT_ITEM_TYPE);
               Log.d(TAG, "remove photo in currentState as switch to sim account");
           }

           setSimInfo((AccountWithDataSetEx) newAccount);
       } else {
           clearSimInfo();
       }
       // Need to clear group member ship when switch account.
       if (currentState != null && currentState.hasMimeEntries(GroupMembership.CONTENT_ITEM_TYPE)) {
           currentState.removeEntry(GroupMembership.CONTENT_ITEM_TYPE);
       }

       return false;
   }


   /**
    * Check Icc account card type.
    * @param state:
    * @return true or false.
    */
   public boolean isAccountTypeIccCard(RawContactDeltaList state) {
       if (!state.isEmpty()) {
           String accountType = state.get(0).getValues().getAsString(RawContacts.ACCOUNT_TYPE);
           ContactEditorUtilsEx.showLogContactState(state);
           if (AccountTypeUtils.isAccountTypeIccCard(accountType)) {
               return true;
           }
       }
       return false;
   }

   /**
    * Get member values.
    * @return values.
    */
    public int getIndicatePhoneOrSimContact() {
        return mIndicatePhoneOrSimContact;
    }
    /**
     * set member values.
     * @param indicatePhoneOrSimContact:
     */
    public void setIndicatePhoneOrSimContact(int indicatePhoneOrSimContact) {
        mIndicatePhoneOrSimContact = indicatePhoneOrSimContact;
    }

    /**
     * Get member values.
     * @return values
     */
    public boolean getIsSaveToSim() {
        return mIsSaveToSim;
    }

    /**
     * Set member values.
     * @param isSaveToSim:
     */
    public void setIsSaveToSim(boolean isSaveToSim) {
        mIsSaveToSim = isSaveToSim;
    }

    /**
     * Get member values.
     * @return values
     */
    public int getSaveModeForSim() {
        return mSaveModeForSim;
    }

    /**
     * Set member values.
     * @param saveModeForSim:
     */
    public void setSaveModeForSim(int saveModeForSim) {
        mSaveModeForSim = saveModeForSim;
    }

    /**
     * Get member values.
     * @return values.
     */
    public int getSlotId() {
        return mSlotId;
    }

    /**
     * Set member values.
     * @param slotId:
     */
    public void setSlotId(int slotId) {
        mSlotId = slotId;
    }

    /**
     * Get member values.
     * @return values.
     */
    public int getSubId() {
        return mSubId;
    }

    /**
     * Set member values.
     * @param subId:
     */
    public void setSubId(int subId) {
        mSubId = subId;
    }

    /**
     * Get member values.
     * @return values
     */
    public RawContactDeltaList getOldState() {
        return mOldState;
    }

    /**
     * Set member values.
     * @param state:
     */
    public void setOldState(RawContactDeltaList state) {
        mOldState = state;
    }

    /**
     * Get member values.
     * @return values
     */
    public boolean getSimType() {
        return mIsSimType;
    }

    /**
     * Set member values.
     * @param simType:
     */
    public void setSimType(boolean simType) {
        mIsSimType = simType;
    }

    /**
     * Set member values.
     * @param simType:
     */
    public void setNewSimType(boolean simType) {
        mNewSimType = simType;
    }

    /**
     * Get member values.
     * @return values
     */
    public boolean getNewSimType() {
        return mNewSimType;
    }

    /**
     * Get member values.
     * @return values
     */
    public boolean getNeedFinish() {
        return mNeedFinish;
    }

    /**
     * Set member values.
     * @param needFinish:
     */
    public void setNeedFinish(boolean needFinish) {
        mNeedFinish = needFinish;
    }

    /**
     * Get member values.
     * @return values
     */
    public int getSimIndex() {
        return mSimIndex;
    }
    /**
     * Set member values.
     * @param simIndex:
     */
    public void setSimIndex(int simIndex) {
        mSimIndex = simIndex;
    }

    /**
     * Get member values.
     * @return values
     */
    public boolean getIsShowIME() {
        return mIsShowIME;
    }

    /**
     * Set member values.
     * @param isShowIME
     */
    public void setIsShowIME(boolean isShowIME) {
        mIsShowIME = isShowIME;
    }

    /**
     * Get member values.
     * @return values
     */
    public boolean getIsJoin() {
        return mIsJoin;
    }

    /**
     * Set member values.
     * @param isJoin:
     */
    public void setIsJoin(boolean isJoin) {
        mIsJoin = isJoin;
    }

    /**
     * Get progress handler.
     * @return values
     */
    public ProgressHandler getProgressHandler() {
        return mProgressHandler;
    }

    /**
     * Get account switcher popup window.
     * @return values
     */
    public ListPopupWindow getAccountSwitcherPopup() {
        return mAccountSwitcherPopup;
    }

    /**
     * Set account switcher popup window.
     * @param pop:
     */
    public void setAccountSwitcherPopup(ListPopupWindow pop) {
        mAccountSwitcherPopup = pop;
    }

    /**
     * Dismiss the account switcher pop up.
     */
    public void dismissAccountSwitcherPopup() {
        UiClosables.closeQuietly(mAccountSwitcherPopup);
        mAccountSwitcherPopup = null;
    }
}