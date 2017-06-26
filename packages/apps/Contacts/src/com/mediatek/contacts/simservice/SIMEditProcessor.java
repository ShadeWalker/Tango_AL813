package com.mediatek.contacts.simservice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mediatek.contacts.ExtensionManager;
import com.mediatek.contacts.SubContactsUtils.NamePhoneTypePair;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.ContactSaveService.Listener;
import com.android.contacts.activities.ConfirmAddDetailActivity;
import com.android.contacts.activities.ContactEditorActivity;
import com.android.contacts.R;

import com.mediatek.contacts.SubContactsUtils;
import com.mediatek.contacts.simservice.SIMProcessorManager.ProcessorCompleteListener;
import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.simcontact.SlotUtils;
import com.mediatek.contacts.simcontact.SubInfoUtils;
import com.mediatek.contacts.util.AccountTypeUtils;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.util.ContactsGroupUtils.USIMGroup;
import com.mediatek.contacts.util.ContactsGroupUtils.USIMGroupException;

import android.accounts.Account;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionInfo;
import android.text.TextUtils;
import android.util.Log;
import android.os.Build;

public class SIMEditProcessor extends SIMProcessorBase {
    private static final String TAG = "SIMEditProcessor";

    private static final int MODE_DEFAULT = 0;
    private static final int MODE_INSERT = 1;
    private static final int MODE_EDIT = 2;

    public static final int RESULT_CANCELED = 0;
    public static final int RESULT_OK = 1;
    public static final int RESULT_NO_DATA = 2;
    public static final int DEFAULT_ID = -1;

    public static final String EDIT_SIM_ACTION = "com.mediatek.contacts.simservice.EDIT_SIM";

    public static final String SIM_TYPE_SIM = "SIM";
    public static final String SIM_TYPE_USIM = "USIM";

    private static final String SIM_NUM_PATTERN = "[+]?[[0-9][*#pw,;]]+[[0-9][*#pw,;]]*";
    private static final String USIM_EMAIL_PATTERN = "[[0-9][a-z][A-Z][_]][[0-9][a-z][A-Z][-_.]]*@[[0-9][a-z][A-Z][-_.]]+";
    private static final String[] ADDRESS_BOOK_COLUMN_NAMES = new String[] {
            "name", "number", "emails", "additionalNumber", "groupIds"
    };

    private static String sName = null;
    private static String sEmail = null;
    private static String sPhone = null;
    private static String sOtherPhone = null;
    private static String sAfterPhone = null;
    private static String sAfterOtherPhone = null;

    private Context mContext;
    private Intent mIntent = null;
    private Uri mLookupUri = null;

    private Account mAccount = null;

    HashMap<Long, String> mGroupAddList = new HashMap<Long, String>();
    HashMap<Long, String> mOldGroupAddList = new HashMap<Long, String>();

    private ArrayList<RawContactDelta> mSimData = new ArrayList<RawContactDelta>();
    private ArrayList<RawContactDelta> mSimOldData = new ArrayList<RawContactDelta>();

    private String mAccountType = null;
    private String mAccountName = null;
    private String mOldName = null;
    private String mOldPhone = null;
    private String mOldOtherPhone = null;
    private String mOldEmail = null;
    private String mUpdateName = null;
    private String mUpdatephone = null;
    private String mUpdatemail = null;
    private String mUpdateAdditionalNumber = null;
    private String mSimType = "SIM";
    private String mSimTypeTag = "UNKNOWN";

    private int mGroupNum = 0;
    private long mSlotId = SlotUtils.getNonSlotId();
    private int mSubId = SubInfoUtils.getInvalidSubId();
    private int mSaveMode = MODE_DEFAULT;
    private int mMode = MODE_DEFAULT;
    private int mSaveFailToastStrId = -1;
    private int mContactId = 0;

    private long mIndicate = RawContacts.INDICATE_PHONE;
    private int mIndexInSim = -1;
    private long mRawContactId = -1;

    private boolean mPhbReady = false;
    private boolean mDoublePhoneNumber = false;
    private boolean mNumberInvalid = false;
    private boolean mQuitEdit = false;
    private boolean mNumberIsNull = false;
    private boolean mFixNumberInvalid = false;
    private boolean mOnBackGoing = true;

    private static List<Listener> sListeners = new ArrayList<Listener>();


    private static Map<Listener, Handler> sListenerHolder = new HashMap<Listener, Handler>();

    public interface Listener {
        public void onSIMEditCompleted(Intent callbackIntent);
    }

    public static void registerListener(Listener listener, Handler handler) {
        if (!(listener instanceof Activity)) {
            throw new ClassCastException("Only activities can be registered to"
                    + " receive callback from " + SIMProcessorService.class.getName());
        }
        LogUtils.d(TAG, "[registerListener]listener added to SIMInsertProcessor: " + listener);
        if (listener instanceof ContactEditorActivity) {
            sListeners.add(listener);
            sListenerHolder.put(listener, handler);
            Log.d(TAG, "sListenerHolder = " + listener.hashCode() + " mHandler = " + handler.hashCode());
        }
    }

    public static void unregisterListener(Listener listener) {
        LogUtils.d(TAG, "[unregisterListener]listener removed from SIMInsertProcessor: " + listener);
        Handler handler = sListenerHolder.get(listener);
        if (handler != null) {
            Log.d(TAG, "handler = " + handler.hashCode() + " listener = " + listener.hashCode());
            handler = null;
            sListeners.remove(listener);
            sListenerHolder.remove(listener);
            listener = null;
        }
    }

    /// fix ALPS01028420.register a handler for sim edit processor again
    public static boolean isNeedRegisterHandlerAgain(Handler handler) {
        LogUtils.d(TAG, "[isNeedRegisterHandlerAgain] handler: " + handler);
        for (Listener listener : sListeners) {
            if (handler.equals(sListenerHolder.get(listener))) {
                return false;
            }
        }
        return true;
    }

    public SIMEditProcessor(Context context, int subId, Intent intent,
            ProcessorCompleteListener listener) {
        super(intent, listener);
        mContext = context;
        mSubId = subId;
        mIntent = intent;
    }

    @Override
    public int getType() {
        return SIMServiceUtils.SERVICE_WORK_EDIT;
    }

    @Override
    public void doWork() {
        if (isCancelled()) {
            LogUtils.w(TAG, "[dowork]cancel remove work. Thread id = "
                    + Thread.currentThread().getId());
            return;
        }

        mSimData = mIntent.getParcelableArrayListExtra("simData");
        mSimOldData = mIntent.getParcelableArrayListExtra("simOldData");
        mAccountType = mSimData.get(0).getValues().getAsString(RawContacts.ACCOUNT_TYPE);

        if (mAccountType.equals(AccountTypeUtils.ACCOUNT_TYPE_USIM)
                || mAccountType.equals(AccountTypeUtils.ACCOUNT_TYPE_CSIM)) {
            mGroupNum = mIntent.getIntExtra("groupNum", 0);
            LogUtils.i(TAG, "[dowork]groupNum : " + mGroupNum);
        }

        //TBD, need to do refactoring: how to avoid null point exception?
        mAccountName = mSimData.get(0).getValues().getAsString(RawContacts.ACCOUNT_NAME);
        if (mAccountType != null && mAccountName != null) {
            mAccount = new Account(mAccountName, mAccountType);
        } else {
            LogUtils.i(TAG, "[dowork]accountType : " + mAccountType + "accountName:" + mAccountName);
            deliverCallbackOnError();
            return;
        }

        mIndicate = mIntent.getIntExtra(RawContacts.INDICATE_PHONE_SIM, RawContacts.INDICATE_PHONE);
        mIndexInSim = mIntent.getIntExtra("simIndex", -1);
        // 1 for new contact, 2 for existing contact
        mSaveMode = mIntent.getIntExtra("simSaveMode", MODE_DEFAULT);
        mLookupUri = mIntent.getData();
        LogUtils.d(TAG, "[dowork]the mSubId is =" + mSubId + " the mIndicate is =" + mIndicate
                + " the mSaveMode = " + mSaveMode + " the accounttype is = " + mAccountType
                + " the uri is  = " + mLookupUri + " | mIndexInSim : " + mIndexInSim);

        mSimTypeTag = SimCardUtils.getSimTypeTagBySubId(mSubId);
        if (SimCardUtils.isSimUsimType(mSubId)) {
            mSimType = SimCardUtils.SimType.SIM_TYPE_USIM_TAG;
        }

        // init static value first.
        initStaticValues();

        // the kind number
        int kindCount = mSimData.get(0).getContentValues().size();
        LogUtils.d(TAG, "[dowork]kindCount: " + kindCount);

        String[] additionalNumberBuffer = new String[2];
        String[] numberBuffer = new String[2];
        long[] groupBuffer = new long[mGroupNum];

        getRawContactDataFromIntent(kindCount, additionalNumberBuffer, numberBuffer, groupBuffer);

        // Put group id and groupName to hashmap
        if (mAccountType.equals(AccountTypeUtils.ACCOUNT_TYPE_USIM)) {
            setGroupFromIntent(groupBuffer);
        }
        // if user chose two "mobile" phone type
        if ((!TextUtils.isEmpty(additionalNumberBuffer[1]))
                  || (!TextUtils.isEmpty(numberBuffer[1]))) {
            mDoublePhoneNumber = true;
            if (setSaveFailToastText()) {
                return;
            }
        } else {
                sOtherPhone = additionalNumberBuffer[0];
                sPhone = numberBuffer[0];
        }
		if(Build.TYPE.equals("eng")){
        	LogUtils.d(TAG, "[dowork]the sName is = " + sName + " the sPhone is =" + sPhone
                + " the sOtherPhone is = " + sOtherPhone + "the email is =" + sEmail);
		}
        if (isPhoneNumberInvaild()) {
            LogUtils.i(TAG, "[dowork]isPhoneNumberInvaild is true,return.");
            deliverCallbackOnError();
            return;
        }

        if (mSaveMode == MODE_EDIT) {
            mMode = MODE_EDIT;
            if (mLookupUri != null) {
                boolean editable = isContactEditable();
                if (!editable) {
                    LogUtils.i(TAG, "[dowork]isContactEditable is false ,return.");
                    return;
                }
            } else {
                LogUtils.i(TAG, "[dowork]mLookupUri is null,return.");
                deliverCallbackOnError();
                return;
            }
        }

        boolean hasImported = SIMServiceUtils.isServiceRunning(mSubId);
        // check hasSimContactsImported in ContactsUtils
        if (hasImported) {
            LogUtils.i(TAG, "[dowork]hasImported,return.");
            showToastMessage(R.string.msg_loading_sim_contacts_toast, null);
            deliverCallbackOnError();
            return;
        }

        saveSimContact(mSaveMode);
        if(Build.TYPE.equals("eng")){
		  LogUtils.d(TAG, "[dowork]the sName is = " + sName + ",the sPhone is =" + sPhone
                + " the buffer[] is " + additionalNumberBuffer[0] + ",the sOtherPhone is = "
               + sOtherPhone + ",the email is =" + sEmail);
        }
    }

    private boolean isPhoneNumberInvaild() {
		// / Modified by guofeiyao
		if ( Build.TYPE.equals("eng") ) {
             LogUtils.d(TAG, "[isPhoneNumberInvaild]initial phone number:" + sPhone);
       	}
		// / End
		
        sAfterPhone = sPhone;
        if (!TextUtils.isEmpty(sPhone)) {
            sAfterPhone = PhoneNumberUtils.stripSeparators(sPhone);

            if (!Pattern.matches(SIM_NUM_PATTERN, /**qinglei*/extractCLIRPortion(sAfterPhone))) {
                mNumberInvalid = true;
            }
            if (setSaveFailToastText()) {
                LogUtils.i(TAG, "[isPhoneNumberInvaild]setSaveFailToastText,return true.");
                return true;
            }
        }

        // / Modified by guofeiyao
		if ( Build.TYPE.equals("eng") ) {
             LogUtils.d(TAG, "[isPhoneNumberInvaild]initial sOtherPhone number:" + sOtherPhone);
		}
		// / End
		
        sAfterOtherPhone = sOtherPhone;
        if (!TextUtils.isEmpty(sOtherPhone)) {
            sAfterOtherPhone = PhoneNumberUtils.stripSeparators(sOtherPhone);
            if (!Pattern.matches(SIM_NUM_PATTERN, /**qinglei*/extractCLIRPortion(sAfterOtherPhone))) {
                mNumberInvalid = true;
            }
            if (setSaveFailToastText()) {
                LogUtils.i(TAG, "[isPhoneNumberInvaild]setSaveFailToastText,return true.");
                return true;
            }
        }

        return false;
    }

    private void getRawContactDataFromIntent(int kindCount, String[] additionalNumberBuffer,
            String[] numberBuffer, long[] groupBuffer) {
        LogUtils.d(TAG, "[getRawContactDataFromIntent]...");

        int additionalNumberIndex = 0;
        int groupIndex = 0;
        int numberIndex = 0;
        String mimeType = null;
        String data = null;
        for (int countIndex = 0; countIndex < kindCount; countIndex++) {
            mimeType = mSimData.get(0).getContentValues().get(countIndex).getAsString(Data.MIMETYPE);
            data = mSimData.get(0).getContentValues().get(countIndex).getAsString(Data.DATA1);
			if(Build.TYPE.equals("eng")){	
			  LogUtils.d(TAG, "[getRawContactDataFromIntent]countIndex:" + countIndex + ",mimeType:"
                  + mimeType + "data:" + data);
			}

            if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                sName = data;
            } else if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
                //AAS, store AAS index(use Data.DATA3).[COMMD_FOR_AAS]
        {
                    //TBD: 7?
                    if ("7".equals(mSimData.get(0).getContentValues().get(countIndex).getAsString(
                            Data.DATA2)) ||
             ExtensionManager.getInstance().getAasExtension().checkAASEntry(mSimData.get(0).getContentValues().get(countIndex)))  {
                        additionalNumberBuffer[additionalNumberIndex] = data;
                        additionalNumberIndex++;
                    } else {
                        numberBuffer[numberIndex] = data;
                        numberIndex++;
                    }
                }
            } else if (Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
                sEmail = data;
            } else if (GroupMembership.CONTENT_ITEM_TYPE.equals(mimeType) && (groupBuffer.length > groupIndex)) {
                // make sure that the bufferGroup exists used to store data.
                groupBuffer[groupIndex] = mSimData.get(0).getContentValues().get(countIndex)
                        .getAsLong(Data.DATA1);
                groupIndex++;
            }
        }
    }

    private void setGroupFromIntent(long[] groupBuffer) {
        String[] groupNames = new String[mGroupNum];
        long[] groupIds = new long[mGroupNum];
        groupNames = mIntent.getStringArrayExtra("groupName");
        groupIds = mIntent.getLongArrayExtra("groupId");
        LogUtils.d(TAG, "[setGroupFromIntent]groupBuffer len :" + groupBuffer.length);

        for (int groupBufferIndex = 0; groupBufferIndex < groupBuffer.length; groupBufferIndex++) {
            for (int grNum = 0; grNum < mGroupNum; grNum++) {
                if (groupBuffer[groupBufferIndex] == groupIds[grNum]) {
                    String groupName = groupNames[grNum];
                    long groupId = groupBuffer[groupBufferIndex];
                    mGroupAddList.put(groupId, groupName);
                }
            }
        }
    }

    /**
     * removing white space characters and '-' characters from the beginning and
     * end of the string.
     *
     * @param number always additional number
     * @return
     */
    private String replaceCharOnNumber(String number) {
        String trimNumber = number;
        if (!TextUtils.isEmpty(trimNumber)) {
            if(Build.TYPE.equals("eng")){LogUtils.d(TAG, "[replaceCharOnNumber]befor replaceall number : " + trimNumber);}
            trimNumber = trimNumber.replaceAll("-", "");
            trimNumber = trimNumber.replaceAll(" ", "");
            if(Build.TYPE.equals("eng")){LogUtils.d(TAG, "[replaceCharOnNumber]after replaceall number : " + trimNumber);}
        }
        return trimNumber;
    }

    private boolean setSaveFailToastText() {
        checkPhoneStatus();
        mSaveFailToastStrId = -1;
        if (!mPhbReady) {
            mSaveFailToastStrId = R.string.icc_phone_book_invalid;
            mQuitEdit = true;
        } /*else if (mAirPlaneModeOn) {
            mSaveFailToastStrId = R.string.AirPlane_mode_on;
            mAirPlaneModeOn = false;
            mQuitEdit = true;
        } else if (mFDNEnabled) {
            mSaveFailToastStrId = R.string.FDNEnabled;
            mFDNEnabled = false;
            mQuitEdit = true;
        } else if (mSIMInvalid) {
            mSaveFailToastStrId = R.string.sim_invalid;
            mSIMInvalid = false;
            mQuitEdit = true;
        }*/ else if (mNumberIsNull) {
            mSaveFailToastStrId = R.string.cannot_insert_null_number;
            mNumberIsNull = false;
        } else if (mNumberInvalid) {
            mSaveFailToastStrId = R.string.sim_invalid_number;
            mNumberInvalid = false;
        } else if (mFixNumberInvalid) {
            mSaveFailToastStrId = R.string.sim_invalid_fix_number;
            mFixNumberInvalid = false;
        } else if (mDoublePhoneNumber) {
            mSaveFailToastStrId = R.string.has_double_phone_number;
            mDoublePhoneNumber = false;
        }
        LogUtils.i(TAG, "[setSaveFailToastText]mSaveFailToastStrId is:" + mSaveFailToastStrId
                + ",mPhbReady:" + mPhbReady);
        if (mSaveFailToastStrId >= 0) {
            if (mSaveFailToastStrId == R.string.err_icc_no_phone_book) {
                String specialErrorText = mContext.getResources().getString(mSaveFailToastStrId,
                        mSimTypeTag);
                showToastMessage(DEFAULT_ID, specialErrorText);
            } else {
                showToastMessage(mSaveFailToastStrId, null);
            }
            backToFragment();
            return true;
        }

        return false;
    }

    private boolean setSaveFailToastText(Uri checkUri) {
        if (checkUri != null && "error".equals(checkUri.getPathSegments().get(0))) {
            mSaveFailToastStrId = -1;
            if ("-1".equals(checkUri.getPathSegments().get(1))) {
                mSaveFailToastStrId = R.string.number_too_long;
            } else if ("-2".equals(checkUri.getPathSegments().get(1))) {
                mSaveFailToastStrId = R.string.name_too_long;
            } else if ("-3".equals(checkUri.getPathSegments().get(1))) {
                mSaveFailToastStrId = R.string.storage_full;
                mQuitEdit = true;
            } else if ("-6".equals(checkUri.getPathSegments().get(1))) {
                mSaveFailToastStrId = R.string.fix_number_too_long;
            } else if ("-10".equals(checkUri.getPathSegments().get(1))) {
                mSaveFailToastStrId = R.string.generic_failure;
                mQuitEdit = true;
            } else if ("-11".equals(checkUri.getPathSegments().get(1))) {
                mSaveFailToastStrId = R.string.err_icc_no_phone_book;
                mQuitEdit = true;
            } else if ("-12".equals(checkUri.getPathSegments().get(1))) {
                mSaveFailToastStrId = R.string.error_save_usim_contact_email_lost;
            } else if ("-13".equals(checkUri.getPathSegments().get(1))) {
                mSaveFailToastStrId = R.string.email_too_long;
            } else if ("0".equals(checkUri.getPathSegments().get(1))) {
                mSaveFailToastStrId = R.string.fail_reason_unknown;
                mQuitEdit = true;
            }
            LogUtils.i(TAG, "setSaveFailToastText uri,mSaveFailToastStrId is:"
                    + mSaveFailToastStrId);
            if (mSaveFailToastStrId >= 0) {
                if (mSaveFailToastStrId == R.string.err_icc_no_phone_book) {
                    String specialErrorText = mContext.getResources().getString(
                            mSaveFailToastStrId, mSimTypeTag);
                    showToastMessage(DEFAULT_ID, specialErrorText);
                } else {
                    showToastMessage(mSaveFailToastStrId, null);
                }
                backToFragment();
                return true;
            }
            return false;
        }

        return !(checkUri != null);
    }

    private boolean updateFailToastText(int result) {
        mSaveFailToastStrId = -1;
        if (result == -1) {
            mSaveFailToastStrId = R.string.number_too_long;
        } else if (result == -2) {
            mSaveFailToastStrId = R.string.name_too_long;
        } else if (result == -3) {
            mSaveFailToastStrId = R.string.storage_full;
            mQuitEdit = true;
        } else if (result == -6) {
            mSaveFailToastStrId = R.string.fix_number_too_long;
        } else if (result == -10) {
            mSaveFailToastStrId = R.string.generic_failure;
            mQuitEdit = true;
        } else if (result == -11) {
            mSaveFailToastStrId = R.string.err_icc_no_phone_book;
            mQuitEdit = true;
        } else if (result == -12) {
            mSaveFailToastStrId = R.string.error_save_usim_contact_email_lost;
        } else if (result == -13) {
            mSaveFailToastStrId = R.string.email_too_long;
        }
        if (mSaveFailToastStrId >= 0) {
            showToastMessage(mSaveFailToastStrId, null);
            backToFragment();
            return true;
        }
        return false;
    }

    private void showResultToastText(int errorType) {
        LogUtils.i(TAG, "[showResultToastText]errorType :" + errorType);

        String toastMsg = null;
        if (errorType == -1) {
            toastMsg = mContext.getString(R.string.contactSavedToast);
        } else {
            toastMsg = mContext.getString(USIMGroupException.getErrorToastId(errorType));
        }

        if (errorType == -1 && compareData()) {
            LogUtils.i(TAG, "[showResultToastText]saved ,return.");
            return;
        } else if (errorType == -1 && !compareData()) {
            LogUtils.d(TAG, "[showResultToastText]showToastMessage default.");
            showToastMessage(DEFAULT_ID, toastMsg);
        } else {
            showToastMessage(DEFAULT_ID, toastMsg);
            backToFragment();
        }
    }

    public void backToFragment() {
        LogUtils.i(TAG, "[backToFragment]");
        final Intent intent = new Intent();
        intent.putParcelableArrayListExtra("simData1", mSimData);
        intent.putExtra("mQuitEdit", mQuitEdit);
        intent.putExtra("result", RESULT_CANCELED);
        intent.setAction(EDIT_SIM_ACTION);
        deliverCallbackOnUiThread(intent);
        mQuitEdit = false;
    }

    private boolean isContactEditable() {
        Intent intent = mIntent;
        ContentResolver resolver = mContext.getContentResolver();
        Uri uri = mLookupUri;;
        if (isRawContactIdInvalid(intent, resolver, uri)) {
            showToastMessage(R.string.icc_phone_book_invalid, null);
            deliverCallbackOnError();
            LogUtils.d(TAG, "[isContactEditable]isRawContactIdInvalid is true,return false.");
            return false;
        }
        ///M:fix CR ALPS01065879,sim Info Manager API Remove
        SubscriptionInfo subscriptionInfo = SubInfoUtils.getSubInfoUsingSubId((int)mIndicate);
        if (subscriptionInfo == null) {
            mSlotId = -1;
        } else {
            mSlotId = subscriptionInfo.getSimSlotIndex();
        }
        ArrayList<Long> oldbufferGroup = new ArrayList<Long>();
        setOldRawContactData(oldbufferGroup);
        LogUtils.i(TAG, "the mIndicate: " + mIndicate + " | the mSlotId : " + mSlotId);
        // put group id and title to hasmap
        if (mAccountType.equals(AccountTypeUtils.ACCOUNT_TYPE_USIM)) {
            setOldGroupAddList(intent, oldbufferGroup);
        }

        return true;
    }

    private void setOldGroupAddList(Intent intent, ArrayList<Long> oldbufferGroup) {
        String[] groupName = new String[mGroupNum];
        long[] groupId = new long[mGroupNum];
        LogUtils.i(TAG, "[getOldGroupAddList]oldbufferGroup.size() : " + oldbufferGroup.size());
        groupName = intent.getStringArrayExtra("groupName");
        groupId = intent.getLongArrayExtra("groupId");
        for (int groupIndex = 0; groupIndex < oldbufferGroup.size(); groupIndex++) {
            for (int grNum = 0; grNum < mGroupNum; grNum++) {
                if (oldbufferGroup.get(groupIndex) == groupId[grNum]) {
                    String title = groupName[grNum];
                    long groupid = oldbufferGroup.get(groupIndex);
                    mOldGroupAddList.put(groupid, title);
                }
            }
        }
    }

    private void setOldRawContactData(ArrayList<Long> oldbufferGroup) {
        int oldCount = mSimOldData.get(0).getContentValues().size();
        String mimeType =  null;
        String data = null;

        for (int oldIndex = 0; oldIndex < oldCount; oldIndex++) {
            mimeType = mSimOldData.get(0).getContentValues().get(oldIndex).getAsString(Data.MIMETYPE);
            data =  mSimOldData.get(0).getContentValues().get(oldIndex).getAsString(Data.DATA1);
            if (Build.TYPE.equals("eng")) {
                LogUtils.d(TAG, "[setOldRawContactData]Data.MIMETYPE: " + mimeType + ",data:" + data);
            }

            if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                mOldName = mSimOldData.get(0).getContentValues().get(oldIndex).getAsString(Data.DATA1);
            } else if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
{
                if (mSimOldData.get(0).getContentValues().get(oldIndex).getAsString(Data.DATA2).equals("7") ||
                    ExtensionManager.getInstance().getAasExtension().checkAASEntry(mSimOldData.get(0).getContentValues().get(oldIndex))) { // 7?
                    mOldOtherPhone = data;
                } else {
                    mOldPhone = data;
                }
                }
            } else if (Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
                mOldEmail = data;
            } else if (GroupMembership.CONTENT_ITEM_TYPE.equals(mimeType)) {
                LogUtils.d(TAG, "[setOldRawContactData] oldIndex = " + oldIndex);
                oldbufferGroup.add(mSimOldData.get(0).getContentValues().get(oldIndex).getAsLong(
                        Data.DATA1));
            }
        }
		if(Build.TYPE.equals("eng")){
        LogUtils.d(TAG, "[setOldRawContactData]The mOldName is: " + mOldName + " ,mOldOtherPhone: " + mOldOtherPhone
                + " ,mOldPhone: " + mOldPhone + " ,mOldEmail: " + mOldEmail);
		}
    }

    private boolean isRawContactIdInvalid(Intent intent, ContentResolver resolver, Uri uri) {
        final String authority = uri.getAuthority();
        final String mimeType = intent.resolveType(resolver);

        if (ContactsContract.AUTHORITY.equals(authority)) {
            if (Contacts.CONTENT_ITEM_TYPE.equals(mimeType)) {
                mRawContactId = SubContactsUtils.queryForRawContactId(resolver, ContentUris
                        .parseId(uri));
            } else if (RawContacts.CONTENT_ITEM_TYPE.equals(mimeType)) {
                mRawContactId = ContentUris.parseId(uri);
            }
        }

        LogUtils.d(TAG, "[isRawContactIdInvalid]authority:" + authority + ",mimeType:" + mimeType
                + ",mRawContactId:" + mRawContactId);

        if (mRawContactId < 1) {
            return true;
        }

        return false;
    }

    private void saveSimContact(int mode) {
        LogUtils.d(TAG, "[saveSimContact]mode: " + mode);
        Uri checkUri = null;
        int result = 0;
        final ContentResolver resolver = mContext.getContentResolver();
        mUpdateName = sName;
        mUpdatephone = sAfterPhone;
        mUpdatephone = replaceCharOnNumber(mUpdatephone);

        ContentValues values = new ContentValues();
        values.put("tag", TextUtils.isEmpty(mUpdateName) ? "" : mUpdateName);
        values.put("number", TextUtils.isEmpty(mUpdatephone) ? "" : mUpdatephone);

        if (mSimType.equals("USIM")) {
            updateUSIMSpecValues(values);
        }

        if (mode == MODE_INSERT) {
            LogUtils.i(TAG, "[saveSimContact]mode is MODE_INSERT");
            insertSIMContact(resolver, values);
        } else if (mode == MODE_EDIT) {
            LogUtils.i(TAG, "[saveSimContact]mode is MODE_EDIT");
            editSIMContact(resolver);
        }
    }

    private void insertSIMContact(final ContentResolver resolver, ContentValues values) {
        Uri checkUri;
        if (isInsertValuesInvalid()) {
            LogUtils.i(TAG, "[insertSIMContact]isInsertValuesInvalid is true,return.");
            return;
        }

        //AAS, Set all anr into usim and db. here we assume anr as "anr","anr2","anr3".., and so as aas.[COMMD_FOR_AAS]
        ExtensionManager.getInstance().getAasExtension().updateValues(mIntent, mSubId, values);
        ExtensionManager.getInstance().getSneExtension().updateValues(mIntent, mSubId, values);

        LogUtils.i(TAG, "[insertSIMContact]insert to SIM card---");
        checkUri = resolver.insert(SubInfoUtils.getIccProviderUri(mSubId), values);
        if(Build.TYPE.equals("eng")){LogUtils.i(TAG, "[insertSIMContact]values is: " + values + ",checkUri is: " + checkUri);}

        if (setSaveFailToastText(checkUri)) {
            LogUtils.i(TAG, "[insertSIMContact]setSaveFailToastText is true,return.");
            mOnBackGoing = false;
            return;
        }
        // index in SIM
        long indexFromUri = ContentUris.parseId(checkUri);
        LogUtils.i(TAG, "[insertSIMContact]insert to db,mSimType :" + mSimType);

        // USIM group begin
        int errorType = -1;
        if (mSimType.equals("USIM")) {
            errorType = insertGroupToUSIMCard(indexFromUri, errorType);
        }
        // USIM group end

        Uri lookupUri = SubContactsUtils.insertToDB(mAccount, mUpdateName, sPhone, mUpdatemail,
                sOtherPhone, resolver, mIndicate, mSimType, indexFromUri, mGroupAddList
                        .keySet()); //mAccountType,mAnrsList,sUpdateNickname
        ExtensionManager.getInstance().getSneExtension().editSimSne(mIntent, indexFromUri, mSubId, ContentUris.parseId(lookupUri));
        // Google default has toast, so hide here
        showResultToastText(errorType);
        if (errorType == -1) {
            deliverCallback(lookupUri);
        } else {
            deliverCallbackOnError();
        }
    }

    private void editSIMContact(final ContentResolver resolver) {
        int result;
        ContentValues updatevalues = new ContentValues();
        setUpdateValues(updatevalues);
        Cursor cursor = null;

        setContactId();

        if(Build.TYPE.equals("eng")){LogUtils.d(TAG, "origianl name: " + mUpdateName);}
        if (!TextUtils.isEmpty(mUpdateName)) {
            NamePhoneTypePair pair = new NamePhoneTypePair(mUpdateName);
            mUpdateName = pair.name;
            if(Build.TYPE.equals("eng")){LogUtils.d(TAG, "fixed name: " + mUpdateName);}
        }
        LogUtils.d(TAG, "[editSIMContact]mSimType:" + mSimType);
        if (SIM_TYPE_SIM.equals(mSimType)) {
            if (isSIMUpdateValuesInvalid()) {
                LogUtils.i(TAG, "[editSIMContact]isSIMUpdateValuesInvalid is true,return.");
                return;
            }
        } else if (SIM_TYPE_USIM.equals(mSimType)) {
            if (isUSIMUpdateValuesInvalid()) {
                LogUtils.i(TAG, "[editSIMContact]isUSIMUpdateValuesInvalid is true,return.");
                return;
            }
        }
        //AAS, Set all anr into usim and db. here we assume anr as "anr","anr2","anr3".., and so as aas.[COMMD_FOR_AAS]
        ExtensionManager.getInstance().getAasExtension().updateValues(mIntent, mSubId, updatevalues);
        ExtensionManager.getInstance().getSneExtension().updateValues(mIntent, mSubId, updatevalues);
        // query phone book to load contacts to cache for update

        cursor = resolver.query(SubInfoUtils.getIccProviderUri(mSubId), ADDRESS_BOOK_COLUMN_NAMES,
                null, null, null);
        /** @ } */
        if (cursor != null) {
            try {
                result = resolver.update(SubInfoUtils.getIccProviderUri(mSubId), updatevalues,
                        null, null);
                LogUtils.d(TAG, "[editSIMContact]result:" + result);
                if (updateFailToastText(result)) {
                    LogUtils.i(TAG, "[editSIMContact]updateFailToastText,return.");
                    mOnBackGoing = false;
                    return;
                }
            } finally {
                cursor.close();
            }
        }

        updateNameToDB(resolver);
        updatePhoneNumberToDB(resolver);
        int errorType = -1;
        if (SIM_TYPE_USIM.equals(mSimType)) {
            updateEmail(resolver);

            updateAdditionalNumberToDB(resolver);
            errorType = upateGroup(resolver, errorType);
            ExtensionManager.getInstance().getSneExtension().editSimSne(mIntent, mIndexInSim, mSubId, mRawContactId);
        }
        showResultToastText(errorType);       
        
        LogUtils.i(TAG, "[editSIMContact]errorType :" + errorType);
        if (errorType == -1) { 
            /// M: Bug fix for ALPS02015883 @{
            final Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, mRawContactId);
            Uri lookupUri = RawContacts.getContactLookupUri(resolver, rawContactUri);
            Log.v(TAG, "Saved contact. New URI: " + lookupUri);
            deliverCallback(lookupUri);
            /// @}
        } else {
            deliverCallbackOnError();
        }
    }

    private void setContactId() {
        Cursor c = mContext.getContentResolver().query(RawContacts.CONTENT_URI, new String[] {
            RawContacts.CONTACT_ID
        }, RawContacts._ID + "=" + mRawContactId, null, null);
        if (c != null) {
            if (c.moveToFirst()) {
                mContactId = c.getInt(0);
                LogUtils.d(TAG, "[setContactId]contactId:" + mContactId);
            }
            c.close();
        }
    }

    private void setUpdateValues(ContentValues updatevalues) {
        mUpdateAdditionalNumber = sAfterOtherPhone;
        if (!TextUtils.isEmpty(mUpdateAdditionalNumber)) {
            LogUtils.d(TAG, "[setUpdateValues] befor replaceall mUpdateAdditionalNumber : "
                    + mUpdateAdditionalNumber);

            mUpdateAdditionalNumber = mUpdateAdditionalNumber.replaceAll("-", "");
            mUpdateAdditionalNumber = mUpdateAdditionalNumber.replaceAll(" ", "");
            LogUtils.d(TAG, "[setUpdateValues] after replaceall mUpdateAdditionalNumber : "
                    + mUpdateAdditionalNumber);
        }
        // to comment old values for index in SIM
        updatevalues.put("newTag", TextUtils.isEmpty(mUpdateName) ? "" : mUpdateName);
        updatevalues.put("newNumber", TextUtils.isEmpty(mUpdatephone) ? "" : mUpdatephone);
        updatevalues.put("newAnr", TextUtils.isEmpty(mUpdateAdditionalNumber) ? "" : mUpdateAdditionalNumber);

        updatevalues.put("newEmails", TextUtils.isEmpty(mUpdatemail) ? "" : mUpdatemail);

        updatevalues.put("index", mIndexInSim);
        if(Build.TYPE.equals("eng")){LogUtils.d(TAG, "[setUpdateValues]updatevalues: " + updatevalues);}
    }

    private boolean isSIMUpdateValuesInvalid() {
        if (TextUtils.isEmpty(mUpdateName) && TextUtils.isEmpty(mUpdatephone)) {
            // if name and number is null, delete this contact
            String where;
            Uri iccUriForSim = SubInfoUtils.getIccProviderUri(mSubId);
            // empty name and phone number
            // use the new 'where' for index in SIM
            where = "index = " + mIndexInSim;
            LogUtils.d(TAG, "[isSIMUpdateValuesInvalid]where:" + where + ",iccUriForSim:" + iccUriForSim);
            int deleteDone = mContext.getContentResolver().delete(iccUriForSim, where, null);
            LogUtils.i(TAG, "[isSIMUpdateValuesInvalid]deleteDone result:" + deleteDone);
            if (deleteDone == 1) {
                Uri deleteUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, mContactId);
                int deleteDB = mContext.getContentResolver().delete(deleteUri, null, null);
                LogUtils.i(TAG, "[isSIMUpdateValuesInvalid]deleteDB result: " + deleteDB);
            }
            deliverCallbackOnError();
            return true;
        } else if (!TextUtils.isEmpty(mUpdatephone)) {
            if (!Pattern.matches(SIM_NUM_PATTERN, /**qinglei*/extractCLIRPortion(mUpdatephone))) {
                mNumberInvalid = true;
            }
        }

        LogUtils.i(TAG, "[isSIMUpdateValuesInvalid]mNumberInvalid:" + mNumberInvalid);

        if (setSaveFailToastText()) {
            LogUtils.i(TAG, "[editSIMContact]setSaveFailToastText is true,return.");
            mOnBackGoing = false;
            return true;
        }
        return false;
    }

    private boolean isUSIMUpdateValuesInvalid() {
        // if all items are empty, delete this contact
        if (TextUtils.isEmpty(mUpdatephone) && TextUtils.isEmpty(mUpdateName)
                && TextUtils.isEmpty(mUpdatemail) && TextUtils.isEmpty(mUpdateAdditionalNumber)
                && mGroupAddList.isEmpty()
                && (ExtensionManager.getInstance().getSneExtension().checkNickName(this, mIntent, false, mSubId) == 1)//sne is empty
        ) {
            String where;
            Uri iccUriForUsim = SubInfoUtils.getIccProviderUri(mSubId);
            // use the new 'where' for index in SIM
            where = "index = " + mIndexInSim;
            LogUtils.d(TAG, "[isUSIMUpdateValuesInvalid]where :" + where + ",iccUriForUsim:" + iccUriForUsim);

            int deleteDone = mContext.getContentResolver().delete(iccUriForUsim, where, null);
            LogUtils.d(TAG, "[isUSIMUpdateValuesInvalid]deleteDone result: " + deleteDone);
            if (deleteDone == 1) {
                Uri deleteUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, mContactId);
                int deleteDB = mContext.getContentResolver().delete(deleteUri, null, null);
                LogUtils.d(TAG, "[isUSIMUpdateValuesInvalid]deleteDB result:" + deleteDB);
            }
            deliverCallbackOnError();
            return true;
        } else if (TextUtils.isEmpty(mUpdatephone)
                && TextUtils.isEmpty(mUpdateName)
                && (!TextUtils.isEmpty(mUpdatemail) || !TextUtils.isEmpty(mUpdateAdditionalNumber)
                        || !mGroupAddList.isEmpty() || !mOldGroupAddList.isEmpty()
                        || (ExtensionManager.getInstance().getSneExtension().checkNickName(this, mIntent, false, mSubId) != 1)//sne not empty
                )) {
            mNumberIsNull = true;
        } else if (!TextUtils.isEmpty(mUpdatephone)) {
            if (!Pattern.matches(SIM_NUM_PATTERN, /**qinglei*/extractCLIRPortion(mUpdatephone))) {
                mNumberInvalid = true;
            }
        }
        if (!TextUtils.isEmpty(mUpdateAdditionalNumber)) {
            if (!Pattern.matches(SIM_NUM_PATTERN, /**qinglei*/extractCLIRPortion(mUpdateAdditionalNumber))) {
                mFixNumberInvalid = true;
            }
        }
        LogUtils.i(TAG, "mNumberIsNull:" + mNumberIsNull + ",mNumberInvalid:" +
                mNumberInvalid + ",mFixNumberInvalid:" + mFixNumberInvalid);

        if (setSaveFailToastText()) {
            LogUtils.i(TAG, "[editSIMContact]setSaveFailToastText is true,return.");
            mOnBackGoing = false;
            return true;
        }
        //after all check,default should be false,mean valid
        boolean invalid = false;
        //current sne may too long
        invalid = ExtensionManager.getInstance().getSneExtension().checkNickName(this, mIntent, true, mSubId) == 2;
        return invalid;
    }

    private void updateNameToDB(final ContentResolver resolver) {
        ContentValues namevalues = new ContentValues();
        String wherename = Data.RAW_CONTACT_ID + " = \'" + mRawContactId + "\'" + " AND "
                + Data.MIMETYPE + "='" + StructuredName.CONTENT_ITEM_TYPE + "'";
        if (Build.TYPE.equals("eng")) {
            LogUtils.d(TAG, "[updateNameToDB]wherename:" + wherename + ",mUpdateName:" + mUpdateName);
        }

        // update name
        if (!TextUtils.isEmpty(mUpdateName)) {
            if (!TextUtils.isEmpty(mOldName)) {
                namevalues.put(StructuredName.DISPLAY_NAME, mUpdateName);
                /** Bug Fix for ALPS00370295 @{ */
                namevalues.putNull(StructuredName.GIVEN_NAME);
                namevalues.putNull(StructuredName.FAMILY_NAME);
                namevalues.putNull(StructuredName.PREFIX);
                namevalues.putNull(StructuredName.MIDDLE_NAME);
                namevalues.putNull(StructuredName.SUFFIX);
                /** @} */
                int upRet = resolver.update(Data.CONTENT_URI, namevalues, wherename, null);
                LogUtils.d(TAG, "[updateNameToDB]update ret:" + upRet);
            } else {
                namevalues.put(StructuredName.RAW_CONTACT_ID, mRawContactId);
                namevalues.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
                namevalues.put(StructuredName.DISPLAY_NAME, mUpdateName);
                Uri insertRetUri = resolver.insert(Data.CONTENT_URI, namevalues);
                LogUtils.d(TAG, "[updateNameToDB]uri insert ret:" + insertRetUri);
            }
        } else {
            // update name is null,delete name row
            int delRet = resolver.delete(Data.CONTENT_URI, wherename, null);
            LogUtils.d(TAG, "[updateNameToDB]delete ret:" + delRet);
        }
    }

    private void updatePhoneNumberToDB(final ContentResolver resolver) {
        // update number
        ContentValues phonevalues = new ContentValues();
        String wherephone = Data.RAW_CONTACT_ID + " = \'" + mRawContactId + "\'" + " AND "
                + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'" + " AND "
                + Data.IS_ADDITIONAL_NUMBER + "= 0";
        if (Build.TYPE.equals("eng")) {
            LogUtils.d(TAG, "[updatePhoneNumberToDB]wherephone:" + wherephone
                    + ",mOldPhone:" + mOldPhone + ",mUpdatephone:" + sPhone);
        }

        if (!TextUtils.isEmpty(mUpdatephone)) {
            if (!TextUtils.isEmpty(mOldPhone)) {
                phonevalues.put(Phone.NUMBER, sPhone);
                int upRet = resolver.update(Data.CONTENT_URI, phonevalues, wherephone, null);
                LogUtils.i(TAG, "[updatePhoneNumberToDB]update ret:" + upRet);
            } else {
                phonevalues.put(Phone.RAW_CONTACT_ID, mRawContactId);
                phonevalues.put(Phone.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                phonevalues.put(Phone.NUMBER, mUpdatephone);
                phonevalues.put(Data.IS_ADDITIONAL_NUMBER, 0);
                phonevalues.put(Phone.TYPE, 2);

                Uri insertRet = resolver.insert(Data.CONTENT_URI, phonevalues);
                LogUtils.i(TAG, "[updatePhoneNumberToDB]Uri insert ret:" + insertRet);
            }
        } else {
            int delRet = resolver.delete(Data.CONTENT_URI, wherephone, null);
            LogUtils.i(TAG, "[updatePhoneNumberToDB]delete ret: " + delRet);
        }
    }

    private void updateAdditionalNumberToDB(final ContentResolver resolver) {
        //the logic of plguin & host write AdditionalNumber is not same
        if (ExtensionManager.getInstance().getAasExtension().updateAdditionalNumberToDB(mIntent, mRawContactId)) {
            Log.d(TAG, "updateAdditionalNumberToDB(),handle by plugin..");
            return ;
        }
        // update additional number
        ContentValues additionalvalues = new ContentValues();
        String whereadditional = Data.RAW_CONTACT_ID + " = \'" + mRawContactId + "\'" + " AND "
                + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'" + " AND "
                + Data.IS_ADDITIONAL_NUMBER + " =1";
        LogUtils.d(TAG, "[updateAdditionalNumberToDB]whereadditional:" + whereadditional);

        if (!TextUtils.isEmpty(mUpdateAdditionalNumber)) {
            if (!TextUtils.isEmpty(mOldOtherPhone)) {
                additionalvalues.put(Phone.NUMBER, sOtherPhone);
                int upRet = resolver.update(Data.CONTENT_URI, additionalvalues,
                        whereadditional, null);;
                LogUtils.d(TAG, "[updateAdditionalNumberToDB]update ret:" + upRet);
            } else {
                additionalvalues.put(Phone.RAW_CONTACT_ID, mRawContactId);
                additionalvalues.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                additionalvalues.put(Phone.NUMBER, mUpdateAdditionalNumber);
                additionalvalues.put(Data.IS_ADDITIONAL_NUMBER, 1);
                additionalvalues.put(Data.DATA2, 7);
                Uri insertRetUri = resolver.insert(Data.CONTENT_URI, additionalvalues);
                LogUtils.d(TAG, "[updateAdditionalNumberToDB]url insert ret:" + insertRetUri);
            }
        } else {
            int delRet = resolver.delete(Data.CONTENT_URI, whereadditional, null);
            LogUtils.d(TAG, "[updateAdditionalNumberToDB]delete ret: " + delRet);
        }
    }


    private void updateEmail(final ContentResolver resolver) {
        // update emails
        ContentValues emailvalues = new ContentValues();
        emailvalues.put(Email.TYPE, Email.TYPE_MOBILE);
        String wheremail = Data.RAW_CONTACT_ID + " = \'" + mRawContactId + "\'" + " AND "
                + Data.MIMETYPE + "='" + Email.CONTENT_ITEM_TYPE + "'";
        LogUtils.d(TAG, "[updateEmail]wheremail:" + wheremail);

        if (!TextUtils.isEmpty(mUpdatemail)) {
            if (!TextUtils.isEmpty(mOldEmail)) {
                emailvalues.put(Email.DATA, mUpdatemail);
                int upRet = resolver.update(Data.CONTENT_URI, emailvalues, wheremail, null);
                LogUtils.d(TAG, "[updateEmail]update ret:" + upRet);
            } else {
                emailvalues.put(Email.RAW_CONTACT_ID, mRawContactId);
                emailvalues.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
                emailvalues.put(Email.DATA, mUpdatemail);
                Uri insertRetUri = resolver.insert(Data.CONTENT_URI, emailvalues);
                LogUtils.d(TAG, "[updateEmail]Uri insert ret:" + insertRetUri);
            }
        } else {
            // update email is null,delete email row
            int delRet = resolver.delete(Data.CONTENT_URI, wheremail, null);
            LogUtils.d(TAG, "[updateEmail]delete ret:" + delRet);
        }
    }

    private int upateGroup(final ContentResolver resolver, int errorType) {
        if (mOldGroupAddList.size() > 0) {
            for (Entry<Long, String> entry : mOldGroupAddList.entrySet()) {
                long grpId = entry.getKey();
                String grpName = entry.getValue();
                int ugrpId = -1;
                try {
                    ugrpId = USIMGroup.hasExistGroup(mSubId, grpName);
                } catch (RemoteException e) {
                    ugrpId = -1;
                }
                if (ugrpId > 0) {
                    USIMGroup.deleteUSIMGroupMember(mSubId, mIndexInSim, ugrpId);
                }
                int delCount = resolver
                        .delete(Data.CONTENT_URI, Data.MIMETYPE + "='"
                                + GroupMembership.CONTENT_ITEM_TYPE + "' AND "
                                + Data.RAW_CONTACT_ID + "=" + mRawContactId + " AND "
                                + ContactsContract.Data.DATA1 + "=" + grpId, null);
                LogUtils.d(TAG, "[upateGroup]DB deleteCount:" + delCount);
            }
        }

        if (mGroupAddList.size() > 0) {
            Iterator<Entry<Long, String>> iter = mGroupAddList.entrySet().iterator();
            while (iter.hasNext()) {
                Entry<Long, String> entry = iter.next();
                long grpId = entry.getKey();
                String grpName = entry.getValue();
                int ugrpId = -1;
                try {
                    ugrpId = USIMGroup.syncUSIMGroupNewIfMissing(mSubId, grpName);
                } catch (RemoteException e) {
                    LogUtils.w(TAG, "[upateGroup]RemoteException:" + e.toString());
                    ugrpId = -1;
                } catch (USIMGroupException e) {
                    errorType = e.getErrorType();
                    ugrpId = -1;
                    LogUtils.w(TAG, "[upateGroup]errorType:" + errorType
                            + ",USIMGroupException:" + e.toString());
                }
                if (ugrpId > 0) {
                    boolean suFlag = USIMGroup.addUSIMGroupMember(mSubId, mIndexInSim, ugrpId);
                    LogUtils.d(TAG, "[upateGroup]addUSIMGroupMember suFlag:" + suFlag
                            + ",ugrpId:" + ugrpId);
                    // insert into contacts DB
                    ContentValues groupValues = new ContentValues();
                    groupValues.put(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
                    groupValues.put(GroupMembership.GROUP_ROW_ID, grpId);
                    groupValues.put(Data.RAW_CONTACT_ID, mRawContactId);
                    resolver.insert(Data.CONTENT_URI, groupValues);
                }
            }
        }

        return errorType;
    }

    private int insertGroupToUSIMCard(long indexFromUri, int errorType) {
        int ugrpId = -1;
        Iterator<Entry<Long, String>> iter = mGroupAddList.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<Long, String> entry = iter.next();
            long grpId = entry.getKey();
            String grpName = entry.getValue();
            try {
                ugrpId = USIMGroup.syncUSIMGroupNewIfMissing(mSubId, grpName);
            } catch (RemoteException e) {
                LogUtils.w(TAG, "[insertGroupToUSIMCard]RemoteException: " + e.toString());
                ugrpId = -1;
            } catch (USIMGroupException e) {
                errorType = e.getErrorType();
                LogUtils.w(TAG, "[insertGroupToUSIMCard]errorType:" + errorType +
                        ",USIMGroupException: " + e.toString());
                ugrpId = -1;
            }
            if (ugrpId > 0) {
                boolean suFlag = USIMGroup.addUSIMGroupMember(mSubId, (int) indexFromUri, ugrpId);
                LogUtils.d(TAG, "[insertGroupToUSIMCard]addUSIMGroupMember suFlag:" + suFlag
                        + ",ugrpId:" + ugrpId);
            } else {
                iter.remove();
            }
        }

        return errorType;
    }

    private boolean isInsertValuesInvalid() {
        if (SIM_TYPE_USIM.equals(mSimType)) {
            String fixedName = mUpdateName;
            if (!TextUtils.isEmpty(mUpdateName)) {
                NamePhoneTypePair namePhoneTypePair = new NamePhoneTypePair(mUpdateName);
                fixedName = namePhoneTypePair.name;
                if(Build.TYPE.equals("eng")){LogUtils.d(TAG, "fix name: " + fixedName);}
            }
            if (TextUtils.isEmpty(fixedName) && TextUtils.isEmpty(mUpdatephone)
                    && TextUtils.isEmpty(mUpdatemail) && TextUtils.isEmpty(mUpdateAdditionalNumber)
                    && mGroupAddList.isEmpty()
                    && (ExtensionManager.getInstance().getSneExtension().checkNickName(this, mIntent, false, mSubId) == 1)//sne is empty
            ) {
                deliverCallbackOnError();
                return true;
            } else if (TextUtils.isEmpty(mUpdatephone)
                    && TextUtils.isEmpty(mUpdateName)
                    && (!TextUtils.isEmpty(mUpdatemail)
                            || !TextUtils.isEmpty(mUpdateAdditionalNumber)
                            || !mGroupAddList.isEmpty()
                            || (ExtensionManager.getInstance().getSneExtension().checkNickName(this, mIntent, false, mSubId) != 1)//sne is not empty
                    )) {
                mNumberIsNull = true;
            } else if (!TextUtils.isEmpty(mUpdatephone)) {
                if (!Pattern.matches(SIM_NUM_PATTERN, /**qinglei*/extractCLIRPortion(mUpdatephone))) {
                    mNumberInvalid = true;
                }
            }
            if (!TextUtils.isEmpty(mUpdateAdditionalNumber)) {
                if (!Pattern.matches(SIM_NUM_PATTERN, /**qinglei*/extractCLIRPortion(mUpdateAdditionalNumber))) {
                    mFixNumberInvalid = true;
                }
            }
        } else {
            if (TextUtils.isEmpty(mUpdatephone) && TextUtils.isEmpty(mUpdateName)) {
                deliverCallback(null);
                return true;
            } else if (!TextUtils.isEmpty(mUpdatephone)) {
                if (!Pattern.matches(SIM_NUM_PATTERN, /**qinglei*/extractCLIRPortion(mUpdatephone))) {
                    mNumberInvalid = true;
                }
            }
        }

        if (setSaveFailToastText()) {
            LogUtils.i(TAG, "[isInsertValuesInvalid]setSaveFailToastText,return true.");
            mOnBackGoing = false;
            return true;
        }
        //after all check,default should be false,mean valid
        boolean invalid = false;
        //current sne may too long
        invalid = ExtensionManager.getInstance().getSneExtension().checkNickName(this, mIntent, true, mSubId) == 2;
        return invalid;
    }

    private void updateUSIMSpecValues(ContentValues values) {
        mUpdatemail = sEmail;

        mUpdateAdditionalNumber = sAfterOtherPhone;
        LogUtils.d(TAG, "[updateUSIMSpecValues]before replace, mUpdateAdditionalNumber is:"
                + mUpdateAdditionalNumber);
        if (!TextUtils.isEmpty(mUpdateAdditionalNumber)) {
            mUpdateAdditionalNumber = mUpdateAdditionalNumber.replaceAll("-", "");
            mUpdateAdditionalNumber = mUpdateAdditionalNumber.replaceAll(" ", "");
            LogUtils.i(TAG, "[updateUSIMSpecValues]after replace, mUpdateAdditionalNumber is: "
                    + mUpdateAdditionalNumber);
        }
        values.put("anr", TextUtils.isEmpty(mUpdateAdditionalNumber) ? "" : mUpdateAdditionalNumber);

        values.put("emails", TextUtils.isEmpty(mUpdatemail) ? "" : mUpdatemail);
    }

    private boolean compareData() {
        boolean compareName = false;
        if (!TextUtils.isEmpty(sName) && !TextUtils.isEmpty(mOldName)) {
            if (sName.equals(mOldName)) {
                compareName = true;
            }
        } else if (TextUtils.isEmpty(sName) && TextUtils.isEmpty(mOldName)) {
            compareName = true;
        }

        boolean comparePhone = false;
        if (!TextUtils.isEmpty(sPhone) && !TextUtils.isEmpty(mOldPhone)) {
            if (sPhone.equals(mOldPhone)) {
                comparePhone = true;
            }
        } else if (TextUtils.isEmpty(sPhone) && TextUtils.isEmpty(mOldPhone)) {
            comparePhone = true;
        }

        boolean compareEmail = false;
        if (!TextUtils.isEmpty(sEmail) && !TextUtils.isEmpty(mOldEmail)) {
            if (sEmail.equals(mOldEmail)) {
                compareEmail = true;
            }
        } else if (TextUtils.isEmpty(sEmail) && TextUtils.isEmpty(mOldEmail)) {
            compareEmail = true;
        }

        boolean compareOther = false;
        if (!TextUtils.isEmpty(sOtherPhone) && !TextUtils.isEmpty(mOldOtherPhone)) {
            if (sOtherPhone.equals(mOldOtherPhone)) {
                compareOther = true;
            }
        } else if (TextUtils.isEmpty(sOtherPhone) && TextUtils.isEmpty(mOldOtherPhone)) {
            compareOther = true;
        }

        boolean compareGroup = false;
        if (mGroupAddList != null && mOldGroupAddList != null) {
            if (mGroupAddList.equals(mOldGroupAddList)) {
                compareGroup = true;
            }
        } else if (mGroupAddList == null && mOldGroupAddList == null) {
            compareGroup = true;
        }
        LogUtils.i(TAG, "[compareData]compareName : " + compareName + " | comparePhone : "
                + comparePhone + " | compareOther : " + compareOther + " | compareEmail: "
                + compareEmail + " | compareGroup : " + compareGroup);
		if(Build.TYPE.equals("eng")){
          LogUtils.i(TAG, "[compareData] sName : " + sName + " | mOldName : " + mOldName
                + " | sEmail : " + sEmail + " | mOldEmail : " + mOldEmail + " | sPhone : " + sPhone
                + " | mOldPhone: " + mOldPhone + " | sOtherPhone : " + sOtherPhone + " | mOldOtherPhone :" +
                " | mOldOtherPhone : " + mOldOtherPhone);
		}

        return (compareName && comparePhone && compareOther && compareEmail && compareGroup);
    }

    /**
     * [Gemini+] check the phone status for given slot.
     */
    private void checkPhoneStatus() {
        mPhbReady = SimCardUtils.isPhoneBookReady(mSubId);
    }

    private void deliverCallback(Uri lookupUri) {
        LogUtils.i(TAG, "[deliverCallback]RESULT_OK---");
        final Intent intent = new Intent();
        intent.putExtra("result", RESULT_OK);
        intent.setAction(EDIT_SIM_ACTION);
        /// M: Bug fix for ALPS02015883
        intent.setData(lookupUri);
        deliverCallbackOnUiThread(intent);
    }

    void deliverCallbackOnUiThread(final Intent intent) {
        for (final Listener listener : sListeners) {
            Handler handler = sListenerHolder.get(listener);
            if (handler != null) {
                handler.post(new Runnable() {
                    public void run() {
                        listener.onSIMEditCompleted(intent);
                    }
                });
            }
        }
    }

    private void deliverCallbackOnError() {
        LogUtils.i(TAG, "[deliverCallbackOnError]RESULT_NO_DATA---");
        final Intent intent = new Intent();
        intent.putExtra("result", RESULT_NO_DATA);
        intent.setAction(EDIT_SIM_ACTION);
        deliverCallbackOnUiThread(intent);
    }

    private void showToastMessage(int resourceId, String content) {
        for (Listener listener : sListeners) {
            Handler handler = sListenerHolder.get(listener);
            if (handler != null) {
                Message msg = handler.obtainMessage();
                msg.arg1 = resourceId;
                Bundle bundle = new Bundle();
                bundle.putString("content", content);
                msg.setData(bundle);
                handler.sendMessage(msg);
                LogUtils.i(TAG, "show toast message");
            }
        }
    }

    public static String extractCLIRPortion(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }

        if (phoneNumber.startsWith("*31#") || phoneNumber.startsWith("#31#")) {
            return phoneNumber.substring(4);
        } else if (phoneNumber.indexOf(PLUS_SIGN_STRING) != -1 &&
                   phoneNumber.indexOf(PLUS_SIGN_STRING) == phoneNumber.lastIndexOf(PLUS_SIGN_STRING)) {
            Pattern p = Pattern.compile("(^[#*])(.*)([#*])(.*)(#)$");
            Matcher m = p.matcher(phoneNumber);
            if (m.matches()) {
                if ("".equals(m.group(2))) {
                    // Started with two [#*] ends with #
                    // So no dialing number and we'll just return "" a +, this handles **21#+
                    return "";
                } else {
                    String strDialNumber = m.group(4);
                    if (strDialNumber != null && strDialNumber.length() > 1 && strDialNumber.charAt(0) == PLUS_SIGN_CHAR) {
                        // Starts with [#*] and ends with #
                        // Assume group 4 is a dialing number such as *21*+1234554#
                        return strDialNumber;
                    }
                }
            } else {
                p = Pattern.compile("(^[#*])(.*)([#*])(.*)");
                m = p.matcher(phoneNumber);
                if (m.matches()) {
                    String strDialNumber = m.group(4);
                    if (strDialNumber != null && strDialNumber.length() > 1 && strDialNumber.charAt(0) == PLUS_SIGN_CHAR) {
                        // Starts with [#*] and only one other [#*]
                        // Assume the data after last [#*] is dialing number (i.e. group 4) such as *31#+11234567890.
                        // This also includes the odd ball *21#+
                        return strDialNumber;
                    }
                }
            }
        }

        return phoneNumber;
    }

    private static final char PLUS_SIGN_CHAR = '+';
    private static final String PLUS_SIGN_STRING = "+";
    private static final String NANP_IDP_STRING = "011";
    private static final int NANP_LENGTH = 10;

    private void initStaticValues() {
        sName = null;
        sEmail = null;
        sPhone = null;
        sOtherPhone = null;
        sAfterPhone = null;
        sAfterOtherPhone = null;
    }

}
