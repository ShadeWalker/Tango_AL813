package com.mediatek.contacts.list.service;

import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.mediatek.contacts.ExtensionManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.vcard.ProcessorBase;
import com.android.internal.telephony.EncodeException;
import com.mediatek.contacts.SubContactsUtils;
import com.mediatek.contacts.model.AccountWithDataSetEx;
import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.simcontact.SubInfoUtils;
import com.mediatek.contacts.simservice.SIMServiceUtils;
import com.mediatek.contacts.util.AccountTypeUtils;
import com.mediatek.contacts.util.ErrorCause;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.util.TimingStatistics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mediatek.internal.telephony.ITelephonyEx;
import android.os.Build;

public class CopyProcessor extends ProcessorBase {
    private static final String LOG_TAG = "CopyProcessor";

    private final MultiChoiceService mService;
    private final ContentResolver mResolver;
    private final List<MultiChoiceRequest> mRequests;
    private final int mJobId;
    private final MultiChoiceHandlerListener mListener;

    private PowerManager.WakeLock mWakeLock;

    private final Account mAccountSrc;
    private final Account mAccountDst;

    private volatile boolean mCanceled;
    private volatile boolean mDone;
    private volatile boolean mIsRunning;

    private static final int MAX_OP_COUNT_IN_ONE_BATCH = 400;
    private static final int RETRYCOUNT = 20;

    private static final String[] DATA_ALLCOLUMNS = new String[] {
        Data._ID,
        Data.MIMETYPE,
        Data.IS_PRIMARY,
        Data.IS_SUPER_PRIMARY,
        Data.DATA1,
        Data.DATA2,
        Data.DATA3,
        Data.DATA4,
        Data.DATA5,
        Data.DATA6,
        Data.DATA7,
        Data.DATA8,
        Data.DATA9,
        Data.DATA10,
        Data.DATA11,
        Data.DATA12,
        Data.DATA13,
        Data.DATA14,
        Data.DATA15,
        Data.SYNC1,
        Data.SYNC2,
        Data.SYNC3,
        Data.SYNC4,
        Data.IS_ADDITIONAL_NUMBER
    };

    public CopyProcessor(final MultiChoiceService service,
            final MultiChoiceHandlerListener listener, final List<MultiChoiceRequest> requests,
            final int jobId, final Account sourceAccount, final Account destinationAccount) {
        mService = service;
        mResolver = mService.getContentResolver();
        mListener = listener;

        mRequests = requests;
        mJobId = jobId;
        mAccountSrc = sourceAccount;
        mAccountDst = destinationAccount;

        final PowerManager powerManager = (PowerManager) mService.getApplicationContext()
                .getSystemService("power");
        mWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
                | PowerManager.ON_AFTER_RELEASE, LOG_TAG);
    }

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        LogUtils.d(LOG_TAG, "CopyProcessor received cancel request,mDone="
                + mDone + ",mCanceled=" + mCanceled);

        if (mDone || mCanceled) {
            return false;
        }
        mCanceled = true;
        if (!mIsRunning) {
            mService.handleFinishNotification(mJobId, false);
            mListener.onCanceled(MultiChoiceService.TYPE_COPY, mJobId, -1, -1, -1);
        }
        return true;
    }

    @Override
    public int getType() {
        return MultiChoiceService.TYPE_COPY;
    }

    @Override
    public synchronized boolean isCancelled() {
        return mCanceled;
    }

    @Override
    public synchronized boolean isDone() {
        return mDone;
    }

    @Override
    public void run() {
        try {
            mIsRunning = true;
            mWakeLock.acquire();
            if (AccountTypeUtils.isAccountTypeIccCard(mAccountDst.type)) {
                copyContactsToSimWithRadioStateCheck();
            } else {
                copyContactsToAccount();
            }
        } finally {
            synchronized (this) {
                mDone = true;
            }
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
    }

    private void copyContactsToSim() {
        int errorCause = ErrorCause.NO_ERROR;

        // Process sim data, sim id or slot
        AccountWithDataSetEx account = (AccountWithDataSetEx) mAccountDst;
        LogUtils.d(LOG_TAG, "[copyContactsToSim]AccountName:" + account.name
                + "|accountType:" + account.type);
        int dstSubId = account.getSubId();
        LogUtils.d(LOG_TAG, "[copyContactsToSim]dstSlotId:" + "|dstSubId:" + dstSubId);
        // The UIM and SIM type has same property, CSIM and USIM also same.
        // So in this process, we set there has only two type 'SIM' and 'USIM'
        // Notice that, the type is not account type.
        // the type is only use for import Email/Group info or not.
        boolean isTargetUsim = SimCardUtils.isSimUsimType(dstSubId);
        String dstSimType = isTargetUsim ? "USIM" : "SIM";
        LogUtils.d(LOG_TAG, "[copyContactsToSim]dstSimType:" + dstSimType);

        if (!isPhoneBookReady(dstSubId)) {
            errorCause = ErrorCause.SIM_NOT_READY;
            mService.handleFinishNotification(mJobId, false);
            mListener.onFailed(MultiChoiceService.TYPE_COPY, mJobId, mRequests.size(),
                    0, mRequests.size(), errorCause);
            return;
        }

        ArrayList<String> numberArray = new ArrayList<String>();
        ArrayList<String> additionalNumberArray = new ArrayList<String>();
        ArrayList<String> emailArray = new ArrayList<String>();

        String targetName = null;

        ContentResolver resolver = this.mResolver;

        // Process request one by one
        int totalItems = mRequests.size();
        int successfulItems = 0;
        int currentCount = 0;
        int iccCardMaxEmailCount = SimCardUtils.getIccCardEmailCount(dstSubId);

        boolean isSimStorageFull = false;
        final ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        TimingStatistics iccProviderTiming = new TimingStatistics(CopyProcessor.class.getSimpleName());
        TimingStatistics contactsProviderTiming = new TimingStatistics(CopyProcessor.class.getSimpleName());
        TimingStatistics checkStatusTiming = new TimingStatistics(CopyProcessor.class.getSimpleName());
        for (MultiChoiceRequest request : this.mRequests) {
            if (mCanceled) {
                break;
            }
            /** M: Bug Fix for ALPS00695093 @{ */
            checkStatusTiming.timingStart();
            if (!isPhoneBookReady(dstSubId) || SIMServiceUtils.isServiceRunning(dstSubId)) {
                LogUtils.d(LOG_TAG, "copyContactsToSim run: sim not ready");
                errorCause = ErrorCause.ERROR_UNKNOWN;
                operationList.clear();
                break;
            }
            checkStatusTiming.timingEnd();

            currentCount++;
            // Notify the copy process on notification bar
            mListener.onProcessed(MultiChoiceService.TYPE_COPY, mJobId, currentCount, totalItems,
                    request.mContactName);

            // reset data
            numberArray.clear();
            additionalNumberArray.clear();

            emailArray.clear();
            targetName = null;

            int contactId = request.mContactId;

            // Query to get all src data resource.
            Uri dataUri = Uri.withAppendedPath(
                    ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId),
                    Contacts.Data.CONTENT_DIRECTORY);
            final String[] projection = new String[] {
                    Contacts._ID,
                    Contacts.Data.MIMETYPE,
                    Contacts.Data.DATA1,
                    Contacts.Data.IS_ADDITIONAL_NUMBER,
                    Contacts.NAME_RAW_CONTACT_ID,
                    Contacts.Data.RAW_CONTACT_ID
            };

            contactsProviderTiming.timingStart();
            Cursor c = resolver.query(dataUri, projection, null, null, null);
            contactsProviderTiming.timingEnd();

            if (c != null && c.moveToFirst()) {
                do {
                    String mimeType = c.getString(1);
                    if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
                        // For phone number
                        String number = c.getString(2);
                        //add by tanghuaizhe for too-long-number imported to sim 
						if(number.length()>20){
						number=number.substring(0,20);
//						LogUtils.d(LOG_TAG, "the cut copyContactsToSim number is " + number);
						}


                        int isAdditionalNumber = c.getInt(3);
                        if (isAdditionalNumber == 1) {
                            additionalNumberArray.add(number);
                        } else {
                            numberArray.add(number);
                        }
                    } else if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)
                            && c.getInt(c.getColumnIndexOrThrow(Contacts.NAME_RAW_CONTACT_ID)) == c.getInt(c
                                    .getColumnIndexOrThrow(Contacts.Data.RAW_CONTACT_ID))) {
                        // For name
                        targetName = c.getString(2);
                    }
                    if (isTargetUsim) {
                        ///M:Bug Fix for ALPS00566570,some USIM Card do not support storing Email address.
                        if (Email.CONTENT_ITEM_TYPE.equals(mimeType) && iccCardMaxEmailCount > 0) {
                            // For email
                            String email = c.getString(2);
                            emailArray.add(email);
                        }
                    }
                } while (c.moveToNext());
            }
            if (c != null) {
                c.close();
            }

            // copy new resournce to target sim or usim,
            // and insert into database if sucessful
            Uri dstSimUri = SubInfoUtils.getIccProviderUri(dstSubId);
            int maxCount = TextUtils.isEmpty(targetName) ? 0 : 1;
            /** M: Bug Fix for ALPS00557517 @{ */
            int maxAnrCount = SimCardUtils.getAnrCount(dstSubId);
            int usimMaxAnrCount = maxAnrCount;
            /** @ } */
            if (isTargetUsim) {
                int numberCount = numberArray.size();
                int additionalCount = additionalNumberArray.size();
                int emailCount = emailArray.size();

                maxCount = (maxCount > additionalCount) ? maxCount : additionalCount;
                maxCount = (maxCount > emailCount) ? maxCount : emailCount;
                int numberQuota = (int) ((numberCount + additionalCount) / (1.0 + maxAnrCount) + (float) maxAnrCount
                        / (1.0 + maxAnrCount));
                LogUtils.i(LOG_TAG, "maxAnr=" + maxAnrCount + "; numberQuota=" + numberQuota + ",additionalCount:"
                        + additionalCount);

                maxCount = maxCount > numberQuota ? maxCount : numberQuota;
            } else {
                numberArray.addAll(additionalNumberArray);
                additionalNumberArray.clear();
                int numberCount = numberArray.size();
                maxCount = maxCount > numberCount ? maxCount : numberCount;
            }
            int sameNameCount = 0;
            ContentValues values = new ContentValues();
            String simTag = null;
            String simNum = null;
            String simAnrNum = null;
            String simEmail = null;

            simTag = sameNameCount > 0 ? (targetName + sameNameCount) : targetName;
            simTag = TextUtils.isEmpty(simTag) ? "" : simTag;
            if(simTag.equals("")){//niubi tang add for  huawei HQ01479450
            	simTag=" ";
            }
            if ((simTag == null || simTag.isEmpty() || simTag.length() == 0)
                    && numberArray.isEmpty()) {
                LogUtils.e(LOG_TAG, " name and number are empty");
                errorCause = ErrorCause.ERROR_UNKNOWN;
                continue;
            }

            if(isContainsChinese(simTag)){
                if(simTag.length()>6){
                    simTag=simTag.substring(0, 6);
                }
            }/* HQ_fengsimin 2016-3-25 modified for HQ01826918 */
            else if(isContainsGreeks(simTag)){
            	if(simTag.length()>10){
                    simTag=simTag.substring(0, 10);
            	}
            }
            	else {
          
            	/* HQ_fengsimin 2016-3-5 modified for HQ01789487 begin*/
            	int[] readInfo = cutLongNameBytes(dstSubId);
            	if(readInfo.length>3){
            		LogUtils.d("fengsimin", "The sim card storage is:"+readInfo[3]);
                	simTag=trimstring(simTag, readInfo[3]);
            	}
                //add by tanghuaizhe  for  importing  long-name-contacts  to sim cards
//                if(isContainsChinese(simTag)){
//                    if(simTag.length()>6){
//                        simTag=simTag.substring(0, 6);
//                    }
//                }else {
//                	if(simTag.length()>14){
//                		simTag=simTag.substring(0, 14);
//                	}
//                	if(simTag.getBytes().length>=18){
//                        simTag=simTag.substring(0, 10);
//                	}
//                }
                //end
            }
            LogUtils.d("fengsimin", "simTag save is:"+simTag);
            /* HQ_fengsimin 2016-3-5 modified for HQ01789487 end*/
            int subContact = 0;
            for (int i = 0; i < maxCount; i++) {
                values.clear();
                values.put("tag", simTag);
				if(Build.TYPE.equals("eng")) {
					LogUtils.d(LOG_TAG, "copyContactsToSim tag is " + simTag);
				}
                simNum = null;
                simAnrNum = null;
                simEmail = null;
                if (!numberArray.isEmpty()) {
                    simNum = numberArray.remove(0);
                    simNum = TextUtils.isEmpty(simNum) ? "" : simNum.replace("-", "");
                    values.put("number", PhoneNumberUtils.stripSeparators(simNum));
					if(Build.TYPE.equals("eng")) {
						LogUtils.d(LOG_TAG, "copyContactsToSim number is " + simNum);
					}
                }

                if (isTargetUsim) {
                    LogUtils.d(LOG_TAG, "copyContactsToSim copy to USIM");
                    if (!additionalNumberArray.isEmpty()) {
                        /**
                         * M: Bug Fix for ALPS00557517
                         * origin code:
                         *  LogUtils.d(LOG_TAG, "additional number array is not empty");
                         * simAnrNum = additionalNumberArray.remove(0);
                         * simAnrNum = TextUtils.isEmpty(simAnrNum) ? "" : simAnrNum.replace("-","");
                         * values.put("anr", PhoneNumberUtils.stripSeparators(simAnrNum));
                         * LogUtils.d(LOG_TAG, "copyContactsToSim anr is " + simAnrNum);
                         * @ { */
                        int loop = additionalNumberArray.size() < usimMaxAnrCount ? additionalNumberArray.size() : usimMaxAnrCount;
                        for (int j = 0; j < loop; j++) {
                            simAnrNum = additionalNumberArray.remove(0);
                            simAnrNum = TextUtils.isEmpty(simAnrNum) ? "" : simAnrNum.replace("-", "");
                            values.put("anr", PhoneNumberUtils.stripSeparators(simAnrNum));
                        }
                        if (!additionalNumberArray.isEmpty()) {
                            numberArray.addAll(additionalNumberArray);
                            additionalNumberArray.clear();
                        }
                        /** @ } */
                    } else if (!numberArray.isEmpty()) {
                        /**
                         * M: Bug Fix for ALPS00557517
                         * origin code:
                         * LogUtils.d(LOG_TAG, "additional number array is empty and fill it with ADN number");
                         * simAnrNum = numberArray.remove(0);
                         * simAnrNum = TextUtils.isEmpty(simAnrNum) ? "" : simAnrNum.replace("-", "");
                         * values.put("anr", PhoneNumberUtils.stripSeparators(simAnrNum));
                         * LogUtils.d(LOG_TAG, "copyContactsToSim anr is " + simAnrNum);
                         * @ { */
                        int loop = numberArray.size() < usimMaxAnrCount ? numberArray.size() : usimMaxAnrCount;
                        for (int k = 0; k < loop; k++) {
                            simAnrNum = numberArray.remove(0);
                            simAnrNum = TextUtils.isEmpty(simAnrNum) ? "" : simAnrNum.replace("-", "");
                            values.put("anr", PhoneNumberUtils.stripSeparators(simAnrNum));
                        }
                        /** @ } */
                    }

                    if (!emailArray.isEmpty()) {
                        simEmail = emailArray.remove(0);
                        simEmail = TextUtils.isEmpty(simEmail) ? "" : simEmail;
                        values.put("emails", simEmail);
                        LogUtils.d(LOG_TAG, "copyContactsToSim emails is " + simEmail);
                    }
                    ExtensionManager.getInstance().getAasExtension().updateValuesforCopy(dataUri, dstSubId, mAccountDst.type, values);
                    ExtensionManager.getInstance().getSneExtension().updateValuesforCopy(dataUri, dstSubId, mAccountDst.type, values);

                }

                /** M: Bug Fix for ALPS00695093 @{ */
                /// M: change for SIM Service Refactoring
                if (!isPhoneBookReady(dstSubId) || SIMServiceUtils.isServiceRunning(dstSubId)) {
                    break;
                }
                //LogUtils.i(LOG_TAG, "Before insert Sim card. values=" + values);
                iccProviderTiming.timingStart();
                Uri retUri = resolver.insert(dstSimUri, values);
                iccProviderTiming.timingEnd();
                LogUtils.i(LOG_TAG, "After insert Sim card.");

                LogUtils.i(LOG_TAG, "retUri is " + retUri);
                if (retUri != null) {
                    List<String> checkUriPathSegs = retUri.getPathSegments();
                    if ("error".equals(checkUriPathSegs.get(0))) {
                        String errorCode = checkUriPathSegs.get(1);
                        LogUtils.i(LOG_TAG, "error code = " + errorCode);
                        printSimErrorDetails(errorCode);
                        if (errorCause != ErrorCause.ERROR_USIM_EMAIL_LOST) {
                            errorCause = ErrorCause.ERROR_UNKNOWN;
                        }
                        if ("-3".equals(checkUriPathSegs.get(1))) {
                            errorCause = ErrorCause.SIM_STORAGE_FULL;
                            isSimStorageFull = true;
                            LogUtils.e(LOG_TAG, "Fail to insert sim contacts fail"
                                    + " because sim storage is full.");
                            break;
                        } else if ("-12".equals(checkUriPathSegs.get(1))) {
                            errorCause = ErrorCause.ERROR_USIM_EMAIL_LOST;
                            LogUtils.e(LOG_TAG, "Fail to save USIM email "
                                    + " because emial slot is full in USIM.");
                            LogUtils.d(LOG_TAG, "Ignore this error and "
                                    + "remove the email address to save this item again");
                            values.remove("emails");
                            iccProviderTiming.timingStart();
                            retUri = resolver.insert(dstSimUri, values);
                            iccProviderTiming.timingEnd();
                            LogUtils.d(LOG_TAG, "[Save Again]The retUri is " + retUri);
                            if (retUri != null && ("error".equals(retUri.getPathSegments().get(0)))) {
                                if ("-3".equals(retUri.getPathSegments().get(1))) {
                                    errorCause = ErrorCause.SIM_STORAGE_FULL;
                                    isSimStorageFull = true;
                                    LogUtils.e(LOG_TAG, "Fail to insert sim contacts fail"
                                            + " because sim storage is full.");
                                    break;
                                }
                            }
                            if (retUri != null && !("error".equals(retUri.getPathSegments().get(0)))) {
                                long indexInSim = ContentUris.parseId(retUri);
                                int backRef = operationList.size();

                                SubContactsUtils.buildInsertOperation(operationList, mAccountDst, simTag, simNum,
                                        null, simAnrNum, resolver, dstSubId, dstSimType, indexInSim, null);
                                ExtensionManager.getInstance().getSneExtension().copySimSneToAccount(operationList, mAccountDst, dataUri, backRef);
                                subContact ++;
                            }
                        }
                    } else {
                        LogUtils.d(LOG_TAG, "insertUsimFlag = true");
                        long indexInSim = ContentUris.parseId(retUri);

                        int backRef = operationList.size();

                        SubContactsUtils.buildInsertOperation(operationList, mAccountDst, simTag, simNum, simEmail,
                                simAnrNum, resolver, dstSubId, dstSimType, indexInSim, null);
                        ExtensionManager.getInstance().getSneExtension().copySimSneToAccount(operationList, mAccountDst, dataUri, backRef);
                        subContact ++;
                        //successfulItems++;
                    }
                } else {
                    errorCause = ErrorCause.ERROR_UNKNOWN;
                }
                if (operationList.size() > MAX_OP_COUNT_IN_ONE_BATCH) {
                    try {
                        LogUtils.i(LOG_TAG, "Before applyBatch. ");
                        /** M: Bug Fix for ALPS00695093 @{ */
                        if (isPhoneBookReady(dstSubId)
                                && !SIMServiceUtils.isServiceRunning(dstSubId)) {
                            contactsProviderTiming.timingStart();
                            resolver.applyBatch(ContactsContract.AUTHORITY, operationList);
                            contactsProviderTiming.timingEnd();
                        }
                        LogUtils.i(LOG_TAG, "After applyBatch ");
                    } catch (android.os.RemoteException e) {
                        LogUtils.e(LOG_TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    } catch (android.content.OperationApplicationException e) {
                        LogUtils.e(LOG_TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    }
                    operationList.clear();
                }
            } // inner looper
            if (subContact > 0) {
                successfulItems++;
            }
            if (isSimStorageFull) {
                break;
            }
        }

        if (operationList.size() > 0) {
            try {
                LogUtils.i(LOG_TAG, "Before end applyBatch. ");
                /** M: Bug Fix for ALPS00695093 @{ */
                if (isPhoneBookReady(dstSubId) && !SIMServiceUtils.isServiceRunning(dstSubId)) {
                    contactsProviderTiming.timingStart();
                    resolver.applyBatch(ContactsContract.AUTHORITY, operationList);
                    contactsProviderTiming.timingEnd();
                }
                LogUtils.i(LOG_TAG, "After end applyBatch ");
            } catch (android.os.RemoteException e) {
                LogUtils.e(LOG_TAG, String.format("%s: %s", e.toString(), e.getMessage()));
            } catch (android.content.OperationApplicationException e) {
                LogUtils.e(LOG_TAG, String.format("%s: %s", e.toString(), e.getMessage()));
            }
            operationList.clear();
        }

        if (mCanceled) {
            LogUtils.d(LOG_TAG, "copyContactsToSim run: mCanceled = true");
            errorCause = ErrorCause.USER_CANCEL;
            mService.handleFinishNotification(mJobId, false);
            mListener.onCanceled(MultiChoiceService.TYPE_COPY, mJobId, totalItems,
                    successfulItems, totalItems - successfulItems);
            return;
        }

        mService.handleFinishNotification(mJobId, errorCause == ErrorCause.NO_ERROR);
        if (errorCause == ErrorCause.NO_ERROR) {
            mListener.onFinished(MultiChoiceService.TYPE_COPY, mJobId, totalItems);
        } else {
            mListener.onFailed(MultiChoiceService.TYPE_COPY, mJobId, totalItems,
                    successfulItems, totalItems - successfulItems, errorCause);
        }

        iccProviderTiming.log("copyContactsToSim():IccProviderTiming");
        contactsProviderTiming.log("copyContactsToSim():ContactsProviderTiming");
        checkStatusTiming.log("copyContactsToSim():CheckStatusTiming");
    }


    private boolean isContainsChinese(String simTag) {
        String regEx = "[\u4e00-\u9fa5]";
        Pattern pat = Pattern.compile(regEx);
        Matcher matcher = pat.matcher(simTag);
        boolean flg = false;
        if (matcher.find()) {
            flg = true;
        }
        return flg;
    }
    /**
     *  HQ_fengsimin 2016-3-25 add for HQ01826918
     * 
     */
    private boolean isContainsGreeks(String simTag) {
        String regEx = "[\u0370-\u03ff]";
        Pattern pat = Pattern.compile(regEx);
        Matcher matcher = pat.matcher(simTag);
        boolean flg = false;
        if (matcher.find()) {
            flg = true;
        }
        return flg;
    }

	private boolean isPhoneBookReady(int subId) {
        boolean result = SimCardUtils.isPhoneBookReady(subId);
        LogUtils.i(LOG_TAG, "isPhoneBookReady " + result);
        return result;
    }

    private void copyContactsToAccount() {
        LogUtils.d(LOG_TAG, "copyContactsToAccount");
        if (mCanceled) {
            return;
        }
        int successfulItems = 0;
        int currentCount = 0;

        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (MultiChoiceRequest request : this.mRequests) {
            sb.append(String.valueOf(request.mContactId));
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append(")");
        LogUtils.d(LOG_TAG, "copyContactsToAccount contactIds " + sb.toString() + " ");
        TimingStatistics contactsProviderTiming = new TimingStatistics(CopyProcessor.class.getSimpleName());
        contactsProviderTiming.timingStart();
        Cursor rawContactsCursor = mResolver.query(
                RawContacts.CONTENT_URI,
                new String[] {RawContacts._ID, RawContacts.DISPLAY_NAME_PRIMARY},
                RawContacts.CONTACT_ID + " IN " + sb.toString(),
                null, null);

        contactsProviderTiming.timingEnd();
        int totalItems = rawContactsCursor == null ? 0 : rawContactsCursor.getCount();

        final ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        // Process request one by one
        if (rawContactsCursor != null) {
            LogUtils.d(LOG_TAG, "copyContactsToAccount: rawContactsCursor.size = " + rawContactsCursor.getCount());

            long nOldRawContactId;
            while (rawContactsCursor.moveToNext()) {
                if (mCanceled) {
                    LogUtils.d(LOG_TAG, "runInternal run: mCanceled = true");
                    break;
                }
                currentCount++;
                String displayName = rawContactsCursor.getString(1);

                mListener.onProcessed(MultiChoiceService.TYPE_COPY, mJobId,
                        currentCount, totalItems, displayName);

                nOldRawContactId = rawContactsCursor.getLong(0);

                Cursor dataCursor = mResolver.query(Data.CONTENT_URI,
                        DATA_ALLCOLUMNS, Data.RAW_CONTACT_ID + "=? ",
                        new String[] { String.valueOf(nOldRawContactId) }, null);
                if (dataCursor == null) {
                    continue;
                } else if (dataCursor.getCount() <= 0) {
                    LogUtils.d(LOG_TAG, "dataCursor is empty");
                    dataCursor.close();
                    continue;
                }

                int backRef = operationList.size();
                ContentProviderOperation.Builder builder = ContentProviderOperation
                        .newInsert(RawContacts.CONTENT_URI);
                if (!TextUtils.isEmpty(mAccountDst.name) && !TextUtils.isEmpty(mAccountDst.type)) {
                    builder.withValue(RawContacts.ACCOUNT_NAME, mAccountDst.name);
                    builder.withValue(RawContacts.ACCOUNT_TYPE, mAccountDst.type);
                } else {
                    builder.withValues(new ContentValues());
                }
                builder.withValue(RawContacts.AGGREGATION_MODE,
                        RawContacts.AGGREGATION_MODE_DISABLED);
                operationList.add(builder.build());

                dataCursor.moveToPosition(-1);
                String[] columnNames = dataCursor.getColumnNames();
                while (dataCursor.moveToNext()) {
                    //do not copy group data between different account.
                    String mimeType = dataCursor.getString(dataCursor.getColumnIndex(Data.MIMETYPE));
                    LogUtils.i(LOG_TAG, "mimeType:" + mimeType);
                    if (GroupMembership.CONTENT_ITEM_TYPE.equals(mimeType)) {
                        continue;
                    }
                    builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                    generateDataBuilder(dataCursor, builder, columnNames, mimeType,
                            mAccountSrc.type);
                    builder.withValueBackReference(Data.RAW_CONTACT_ID, backRef);
                    operationList.add(builder.build());
                }
                dataCursor.close();
                successfulItems++;
                if (operationList.size() > MAX_OP_COUNT_IN_ONE_BATCH) {
                    try {
                        LogUtils.i(LOG_TAG, "Before applyBatch. ");
                        contactsProviderTiming.timingStart();
                        mResolver.applyBatch(ContactsContract.AUTHORITY, operationList);
                        contactsProviderTiming.timingEnd();
                        LogUtils.i(LOG_TAG, "After applyBatch ");
                    } catch (android.os.RemoteException e) {
                        LogUtils.e(LOG_TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    } catch (android.content.OperationApplicationException e) {
                        LogUtils.e(LOG_TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    }
                    operationList.clear();
                }
            }
            rawContactsCursor.close();
            if (operationList.size() > 0) {
                try {
                    LogUtils.i(LOG_TAG, "Before end applyBatch. ");
                    contactsProviderTiming.timingStart();
                    mResolver.applyBatch(ContactsContract.AUTHORITY, operationList);
                    contactsProviderTiming.timingEnd();
                    LogUtils.i(LOG_TAG, "After end applyBatch ");
                } catch (android.os.RemoteException e) {
                    LogUtils.e(LOG_TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                } catch (android.content.OperationApplicationException e) {
                    LogUtils.e(LOG_TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                }
                operationList.clear();
            }
            if (mCanceled) {
                LogUtils.d(LOG_TAG, "runInternal run: mCanceled = true");
                mService.handleFinishNotification(mJobId, false);
                mListener.onCanceled(MultiChoiceService.TYPE_COPY, mJobId, totalItems,
                        successfulItems, totalItems - successfulItems);
                if (rawContactsCursor != null && !rawContactsCursor.isClosed()) {
                    rawContactsCursor.close();
                }
                return;
            }
        }

        mService.handleFinishNotification(mJobId, successfulItems == totalItems);
        if (successfulItems == totalItems) {
            mListener.onFinished(MultiChoiceService.TYPE_COPY, mJobId, totalItems);
        } else {
            mListener.onFailed(MultiChoiceService.TYPE_COPY, mJobId, totalItems,
                    successfulItems, totalItems - successfulItems);
        }

        LogUtils.d(LOG_TAG, "copyContactsToAccount: end");
        contactsProviderTiming.log("copyContactsToAccount():ContactsProviderTiming");
    }

    private void cursorColumnToBuilder(Cursor cursor, String[] columnNames, int index,
            ContentProviderOperation.Builder builder) {
        switch (cursor.getType(index)) {
            case Cursor.FIELD_TYPE_NULL:
                // don't put anything in the content values
                break;
            case Cursor.FIELD_TYPE_INTEGER:
                builder.withValue(columnNames[index], cursor.getLong(index));
                break;
            case Cursor.FIELD_TYPE_STRING:
                builder.withValue(columnNames[index], cursor.getString(index));
                break;
            case Cursor.FIELD_TYPE_BLOB:
                builder.withValue(columnNames[index], cursor.getBlob(index));
                break;
            default:
                throw new IllegalStateException("Invalid or unhandled data type");
        }
    }

    private void printSimErrorDetails(String errorCode) {
        int iccError = Integer.valueOf(errorCode);
        switch (iccError) {
            case ErrorCause.SIM_NUMBER_TOO_LONG:
                LogUtils.d(LOG_TAG, "ERROR PHONE NUMBER TOO LONG");
                break;
            case ErrorCause.SIM_NAME_TOO_LONG:
                LogUtils.d(LOG_TAG, "ERROR NAME TOO LONG");
                break;
            case ErrorCause.SIM_STORAGE_FULL:
                LogUtils.d(LOG_TAG, "ERROR STORAGE FULL");
                break;
            case ErrorCause.SIM_ICC_NOT_READY:
                LogUtils.d(LOG_TAG, "ERROR ICC NOT READY");
                break;
            case ErrorCause.SIM_PASSWORD_ERROR:
                LogUtils.d(LOG_TAG, "ERROR ICC PASSWORD ERROR");
                break;
            case ErrorCause.SIM_ANR_TOO_LONG:
                LogUtils.d(LOG_TAG, "ERROR ICC ANR TOO LONG");
                break;
            case ErrorCause.SIM_GENERIC_FAILURE:
                LogUtils.d(LOG_TAG, "ERROR ICC GENERIC FAILURE");
                break;
            case ErrorCause.SIM_ADN_LIST_NOT_EXIT:
                LogUtils.d(LOG_TAG, "ERROR ICC ADN LIST NOT EXIST");
                break;
            case ErrorCause.ERROR_USIM_EMAIL_LOST:
                LogUtils.d(LOG_TAG, "ERROR ICC USIM EMAIL LOST");
                break;
            default:
                LogUtils.d(LOG_TAG, "ERROR ICC UNKNOW");
                break;
        }
    }

    private void copyContactsToSimWithRadioStateCheck() {
        if (mCanceled) {
            return;
        }

        int errorCause = ErrorCause.NO_ERROR;

        AccountWithDataSetEx account = (AccountWithDataSetEx) mAccountDst;
        LogUtils.d(LOG_TAG, "[copyContactsToSimWithRadioCheck]AccountName: " + account.name
                + " | accountType: " + account.type);
        int dstSubId = account.getSubId();

        if (!isPhoneBookReady(dstSubId)) {
            int i = 0;
            while (i++ < RETRYCOUNT) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (isPhoneBookReady(dstSubId)) {
                    break;
                }
            }
        }
        if (!isPhoneBookReady(dstSubId)) {
            errorCause = ErrorCause.SIM_NOT_READY;
            mService.handleFinishNotification(mJobId, false);
            mListener.onFailed(MultiChoiceService.TYPE_COPY, mJobId, mRequests.size(),
                    0, mRequests.size(), errorCause);
            return;
        }
        copyContactsToSim();
    }

    private void generateDataBuilder(Cursor dataCursor, Builder builder, String[] columnNames,
            String mimeType, String srcAccountType) {
        for (int i = 1; i < columnNames.length; i++) {
            /** M:AAS [COMMD_FOR_AAS]@ { */
            if (ExtensionManager.getInstance().getAasExtension().cursorColumnToBuilder(
                     dataCursor, builder,
                    srcAccountType, mimeType, ((AccountWithDataSetEx) mAccountDst).getSubId(), i)) {
                continue;
                /** M: @ } */
            }
            cursorColumnToBuilder(dataCursor, columnNames, i, builder);
        }
    }
    
    /**
     * count the length to cut depend on the storage of sim card
     * 
     */
    public static int countTheLength(char[] line, int maxlen) {
        char[] alphaId = new char[40 * 4 + 4 + 1];
        char[] temp = new char[5];
        int tmp, i, j;
        int nameLimited = maxlen;

        // pack Alpha Id
        int len = line.length;
        if ((len % 4) != 0) {
            // LOGE("The alphaId should encode using Hexdecimal: %s", line);
        } else if (len > (40 * 4)) {
            // LOGE("The alphaId shouldn't longer than RIL_MAX_PHB_NAME_LEN");
        }

        for (i = 0, j = 0; i < len; i += 4, j++) {
            temp[0] = line[i];
            temp[1] = line[i + 1];
            temp[2] = line[i + 2];
            temp[3] = line[i + 3];
            temp[4] = 0;
            tmp = rild_sms_hexCharToDecInt(temp, 4);

            if (tmp >= 128) {
                break;
            }
            alphaId[j] = (char) tmp;
            // alphaId[ril_max_phb_name_len] = '\0';
        }
        alphaId[j] = '\0';

        if (i != len) {
            len /= 4;

            if (encodeUCS2_0x81(line, alphaId, alphaId.length) > 0) {
                // try UCS2_0x81 coding
                return (nameLimited - 3);
            } else {
                // UCS2 coding
                return (nameLimited - 2) / 2;
            }
        }
        return nameLimited;
    }

    
    
    
    /**
     * cut Contact name when copy phone contact to SIM card
     * 
     */
    public static String trimstring(String simTag, int nameLimit) {
		if (simTag == null || "".equals(simTag) || nameLimit < 1) {
			return "";
		}

		int len = simTag.length();
		LogUtils.d("fengsimin", "simTag length is:"+len);
		try {
			// 7 bit string
						com.android.internal.telephony.GsmAlphabet
								.stringToGsm7BitPacked(simTag);
						if (len > nameLimit) {
							simTag = simTag.substring(0, nameLimit);
						}
		} catch (EncodeException e) {
			String temp = encodeATUCS(simTag);
			int length = countTheLength(temp.toCharArray(), nameLimit);
			LogUtils.d("fengsimin", "cut name length is:"+length);
			if (len > length) {
				simTag = simTag.substring(0, length);
			}
		}
		LogUtils.d("fengsimin", "simTag after cut is:"+simTag);
		return simTag;
	}
    
    public static int rild_sms_hexCharToDecInt(char[] hex, int length) {
        int i = 0;
        int value, digit;

        for (i = 0, value = 0; i < length && hex[i] != '\0'; i++) {
            if (hex[i] >= '0' && hex[i] <= '9') {
                digit = hex[i] - '0';
            } else if (hex[i] >= 'A' && hex[i] <= 'F') {
                digit = hex[i] - 'A' + 10;
            } else if (hex[i] >= 'a' && hex[i] <= 'f') {
                digit = hex[i] - 'a' + 10;
            } else {
                return -1;
            }
            value = value * 16 + digit;
        }

        return value;
    }
    public static int encodeUCS2_0x81(char[] src, char[] des, int maxLen) {
        int i, j, len, base = 0;
        short[] tmpAlphaId = new short[40 * 4 + 4 + 1];
        char[] temp = new char[5];

        len = src.length;
        for (i = 0, j = 0; i < len; i += 4, j++) {
            temp[0] = src[i];
            temp[1] = src[i + 1];
            temp[2] = src[i + 2];
            temp[3] = src[i + 3];
            temp[4] = 0;
            tmpAlphaId[j] = (short) rild_sms_hexCharToDecInt(temp, 4);
        }
        tmpAlphaId[j] = '\0';
        len = j;

        if (len <= 3) // at least 3 characters
            return 0;
        /// the destination buffer is not enough(include '\0')
        if (((len + 3) * 2 + 1) > maxLen)
            return 0;

        // find out the base
        for (i = 0; i < len; i++) {
            if (tmpAlphaId[i] >= 128) {
                base = tmpAlphaId[i] & 0x7f80;
                break;
            }
        }

        // Check the base and encode the String
        for (; i < len; i++) {
            if (tmpAlphaId[i] < 128)
                continue;
            tmpAlphaId[i] ^= base;
            // LOGD("0x81: alpha: %x", tmpAlphaId[i]);
            if (tmpAlphaId[i] >= 128)
                break;
            tmpAlphaId[i] |= 0x80;
        }

        if (i != len)
            return 0;

        return len;
    }

    public static String encodeATUCS(String input) {
        byte[] textPart;
        StringBuilder output;

        output = new StringBuilder();

        if (input.length() > 40) {
            input = input.substring(0, 40);
        }

        for (int i = 0; i < input.length(); i++) {
            String hexInt = Integer.toHexString(input.charAt(i));
            for (int j = 0; j < (4 - hexInt.length()); j++)
                output.append("0");
            output.append(hexInt);
        }

        return output.toString();
    }
    //HQ_wuruijun add for HQ01468981 start
    public static int[] cutLongNameBytes(int slotId) {
        ITelephonyEx phoneEx = ITelephonyEx.Stub.asInterface(ServiceManager.checkService("phoneEx"));
        int [] readInfo = null;
        // readInfo[2] = 0;  // # max length of number
        // readInfo[3] = 0;  // # max length of alpha id (name)
        if (phoneEx == null) {
            return readInfo;
        }
        try {
            readInfo =  phoneEx.getAdnStorageInfo(slotId);
            if (readInfo == null) {
                LogUtils.d(LOG_TAG,"readInfo is null");
            } else {
                LogUtils.d(LOG_TAG,"readInfo[2] = " + readInfo[2] + " , readinfo[3] = " + readInfo[3] );
            }
        } catch(RemoteException e) {
            LogUtils.d(LOG_TAG, "InterruptedException occured") ;
        }
        return readInfo;
    }
    //HQ_wuruijun add end
}
