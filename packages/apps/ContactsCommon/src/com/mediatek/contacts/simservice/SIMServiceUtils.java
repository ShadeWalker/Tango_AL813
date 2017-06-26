package com.mediatek.contacts.simservice;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.UserHandle;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContacts;
import android.telephony.SubscriptionInfo;
import android.text.TextUtils;

import com.android.internal.telephony.PhoneConstants;

import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.simcontact.SubInfoUtils;
import com.mediatek.contacts.util.LogUtils;

public class SIMServiceUtils {
    private static final String TAG = "SIMServiceUtils";
    private static SIMProcessorState mSIMProcessorState;

    public static final String ACTION_PHB_LOAD_FINISHED = "com.android.contacts.ACTION_PHB_LOAD_FINISHED";

    public static final String SERVICE_SUBSCRIPTION_KEY = "subscription_key";
    public static final String SERVICE_SLOT_KEY = "which_slot";
    public static final String SERVICE_WORK_TYPE = "work_type";

    public static final int SERVICE_WORK_NONE = 0;
    public static final int SERVICE_WORK_IMPORT = 1;
    public static final int SERVICE_WORK_REMOVE = 2;
    public static final int SERVICE_WORK_EDIT = 3;
    public static final int SERVICE_WORK_DELETE = 4;
    public static final int SERVICE_WORK_UNKNOWN = -1;
    public static final int SERVICE_IDLE = 0;
    public static final int SERVICE_FORCE_REMOVE_SUB_ID = -20;

    public static final int SERVICE_DELETE_CONTACTS = 1;
    public static final int SERVICE_QUERY_SIM = 2;
    public static final int SERVICE_IMPORT_CONTACTS = 3;

    public static final int SIM_TYPE_SIM = SimCardUtils.SimType.SIM_TYPE_SIM;
    public static final int SIM_TYPE_USIM = SimCardUtils.SimType.SIM_TYPE_USIM;
    public static final int SIM_TYPE_UIM = SimCardUtils.SimType.SIM_TYPE_UIM;
    public static final int SIM_TYPE_CSIM = SimCardUtils.SimType.SIM_TYPE_CSIM;
    public static final int SIM_TYPE_UNKNOWN = SimCardUtils.SimType.SIM_TYPE_UNKNOWN;

    public static final int TYPE_IMPORT = 1;
    public static final int TYPE_REMOVE = 2;
    public static final int SERVICE_WORK_IMPORT_PRESET_CONTACTS = 5;
    public static final int SERVICE_WORK_IMPORT_SDN_CONTACTS = 6;

    

    public static class ServiceWorkData {
        public int mSubId = -1;
        public int mSimType = SIM_TYPE_UNKNOWN;
        public Cursor mSimCursor = null;

        ServiceWorkData() {
        }

        ServiceWorkData(int subId, int simType, Cursor simCursor) {
            mSubId = subId;
            mSimType = simType;
            mSimCursor = simCursor;
        }
    }

    public static void deleteSimContact(Context context, int subId) {
        ArrayList<Integer> validSubIds = new ArrayList<Integer>();
        List<SubscriptionInfo> subscriptionInfoList = SubInfoUtils.getActivatedSubInfoList();
        if (subId != SERVICE_FORCE_REMOVE_SUB_ID && subscriptionInfoList != null && subscriptionInfoList.size() > 0) {
            for (SubscriptionInfo subscriptionInfo : subscriptionInfoList) {
                if (subscriptionInfo.getSubscriptionId() != subId) {
                    validSubIds.add(subscriptionInfo.getSubscriptionId());
                }
            }
        }

        // Be going to delete the invalid sim contacts records.
        StringBuilder delSelection = new StringBuilder();
        String filter = null;
        for (int id : validSubIds) {
            delSelection.append(id).append(",");
        }
        if (delSelection.length() > 0) {
            delSelection.deleteCharAt(delSelection.length() - 1);
            filter = delSelection.toString();
        }
        filter = TextUtils.isEmpty(filter) ? RawContacts.INDICATE_PHONE_SIM + " > 0 "
                : RawContacts.INDICATE_PHONE_SIM + " > 0 " + " AND " + RawContacts.INDICATE_PHONE_SIM + " NOT IN (" + filter + ")";
        LogUtils.d(TAG, "[deleteSimContact]subId:" + subId + "|sim contacts filter:" + filter);
        int count = context.getContentResolver().delete(
                RawContacts.CONTENT_URI.buildUpon().appendQueryParameter("sim", "true").build(),
                filter, null);
        // add for ALPS01964765.
        LogUtils.d(TAG, "[deleteSimContact]the current user is: " + UserHandle.myUserId());
        LogUtils.d(TAG, "[deleteSimContact] contacts count:" + count);

        // Be going to delete the invalid usim group records.
        delSelection = new StringBuilder();
        filter = null;
        for (int id : validSubIds) {
            delSelection.append("'" + "USIM" + id + "'" + ",");
        }

        if (delSelection.length() > 0) {
            delSelection.deleteCharAt(delSelection.length() - 1);
            filter = delSelection.toString();
        }
        filter = TextUtils.isEmpty(filter) ? (Groups.ACCOUNT_TYPE + "='USIM Account'") :
            (Groups.ACCOUNT_NAME + " NOT IN " + "(" + filter + ")" + " AND "
            + Groups.ACCOUNT_TYPE + "='USIM Account'");
        LogUtils.d(TAG, "[deleteSimContact]subId:" + subId + "|usim group filter:" + filter);
        count = context.getContentResolver().delete(Groups.CONTENT_URI, filter, null);
        LogUtils.d(TAG, "[deleteSimContact] group count:" + count);

        sendFinishIntent(context, subId);
    }

    /**
     * check PhoneBook State is ready if ready, then return true.
     *
     * @param subId
     * @return
     */
    static boolean checkPhoneBookState(final int subId) {
        return SimCardUtils.isPhoneBookReady(subId);
    }

    static void sendFinishIntent(Context context, int subId) {
        LogUtils.i(TAG, "[sendFinishIntent]subId:" + subId);
        Intent intent = new Intent(ACTION_PHB_LOAD_FINISHED);
        /// M: ALPS01830685 make the key string the same as in onReceive func.
        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);
        context.sendBroadcast(intent);
    }

    public static boolean isServiceRunning(int subId) {
        if (mSIMProcessorState != null) {
            return mSIMProcessorState.isImportRemoveRunning(subId);
        }

        return false;
    }

    public static int getServiceState(int subId) {
        return 0;
    }

    public static void setSIMProcessorState(SIMProcessorState processorState) {
        mSIMProcessorState = processorState;
    }

    public interface SIMProcessorState {
        public boolean isImportRemoveRunning(int subId);
    }
}
