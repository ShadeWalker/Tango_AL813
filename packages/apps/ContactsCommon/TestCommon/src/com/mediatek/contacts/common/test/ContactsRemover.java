package com.mediatek.contacts.common.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.SparseIntArray;

import com.mediatek.contacts.simcontact.SubInfoUtils;
import com.mediatek.contacts.util.LogUtils;

public class ContactsRemover {

    private static final String TAG = ContactsRemover.class.getSimpleName();
    private static final int ONE_BATCH_COUNT = 50;
    private Context mContext;

    public ContactsRemover(Context context) {
        mContext = context;
    }

    public void removeAllContactsBothInSimAndDb() {
        LogUtils.d(TAG, "[testDeleteAll]delete started" + timming());
        ContentResolver cr = mContext.getContentResolver();
        Cursor c = cr.query(RawContacts.CONTENT_URI, new String[] { RawContacts.CONTACT_ID, RawContacts.INDICATE_PHONE_SIM,
                RawContacts.INDEX_IN_SIM }, null, null, null);
        if (c == null || !c.moveToFirst()) {
            LogUtils.e(TAG, "[testDeleteAll]query failed: " + c);
            return;
        }
        List<RawContactData> rawContactList = new ArrayList<RawContactData>();
        do {
            long contactId = c.getLong(0);
            int simId = c.getInt(1);
            int indexInsim = c.getInt(2);
            RawContactData data = new RawContactData(contactId, simId, indexInsim);
            rawContactList.add(data);
        } while (c.moveToNext());
        LogUtils.d(TAG, "[testDeleteAll]started delete SIM contacts: " + timming());
        deleteSimContacts(rawContactList);
        LogUtils.d(TAG, "[testDeleteAll]started delete db contacts: " + timming());
        deleteContactsInDb(rawContactList);
        LogUtils.d(TAG, "[testDeleteAll]delete finished: " + timming() + ", total time: " + mTotalTime);
    }

    private long mLastTimming = -1;
    private long mTotalTime = 0;

    private long timming() {
        long time = System.currentTimeMillis();
        if (mLastTimming < 0) {
            mLastTimming = time;
        }
        mTotalTime += (time - mLastTimming);
        mLastTimming = time;
        return time;
    }

    private void deleteContactsInDb(List<RawContactData> rawContactList) {
        Set<Long> contactIds = new HashSet<Long>();
        for (RawContactData item : rawContactList) {
            contactIds.add(item.mContactId);
        }
        LogUtils.d(TAG, "[deleteContactsInDb]total contactIds: " + contactIds.size());

        List<List<Long>> splitedContactIds = new ArrayList<List<Long>>();
        List<Long> tempContactIds = null;
        for (Long id : contactIds) {
            if (tempContactIds == null) {
                tempContactIds = new ArrayList<Long>(ONE_BATCH_COUNT);
            }
            tempContactIds.add(id);
            if (tempContactIds.size() >= ONE_BATCH_COUNT) {
                splitedContactIds.add(tempContactIds);
                tempContactIds = null;
            }
        }
        if (tempContactIds != null && tempContactIds.size() > 0) {
            splitedContactIds.add(tempContactIds);
        }
        for (List<Long> contactIdList : splitedContactIds) {
            actullyBatchDelete(contactIdList);
        }
    }

    private void actullyBatchDelete(List<Long> contactIdList) {
        LogUtils.d(TAG, "[actullyDelete]actully delete batch: " + contactIdList.size());
        assert contactIdList.size() <= 50;
        final StringBuilder whereBuilder = new StringBuilder();
        final ArrayList<String> whereArgs = new ArrayList<String>();
        final String[] questionMarks = new String[contactIdList.size()];
        for (long contactId : contactIdList) {
            whereArgs.add(String.valueOf(contactId));
        }
        Arrays.fill(questionMarks, "?");
        whereBuilder.append(Contacts._ID + " IN (").append(TextUtils.join(",", questionMarks)).append(")");

        int deleteCount = mContext.getContentResolver().delete(
                Contacts.CONTENT_URI.buildUpon().appendQueryParameter("batch", "true").build(), whereBuilder.toString(),
                whereArgs.toArray(new String[0]));
        LogUtils.d(TAG, "[actullyDelete]actully deleted " + deleteCount + " contacts");
    }

    private void deleteSimContacts(List<RawContactData> rawContactList) {
        LogUtils.d(TAG, "[deleteSimContacts]delete sim contact, count: " + rawContactList.size());
        ContentResolver cr = mContext.getContentResolver();
        for (RawContactData item : rawContactList) {
            if (item.mSlotId >= 0) {
                String where = "index = " + item.mIndexInSim;
                int result = 1; //cr.delete(SlotUtils.getSlotIccUri(item.mSlotId), where, null);
                if (result != 1) {
                    LogUtils.e(TAG, "[deleteSimContacts]delete failed: " + where);
                }
            }
        }
    }

    private static SparseIntArray sSimSlotMap = new SparseIntArray();

    private static int getSlotIdCached(int simId) {
        if (simId <= 0) {
            return -1;
        }
        if (sSimSlotMap.get(simId, -1) < 0) {
            int slotId = SubInfoUtils.getSlotIdUsingSubId(simId);
            sSimSlotMap.put(simId, slotId);
        }
        return sSimSlotMap.get(simId);
    }

    private class RawContactData {
        public long mContactId;
        public int mSimInfoId;
        public int mIndexInSim;
        public int mSlotId;

        public RawContactData(long contactId, int simId, int indexInSim) {
            mContactId = contactId;
            mSimInfoId = simId;
            mIndexInSim = indexInSim;
            mSlotId = getSlotIdCached(simId);
        }

        @Override
        public String toString() {
            return "RawContactData(" + hashCode() + "): contact_id = " + mContactId + ", sim_id = " + mSimInfoId
                    + ", index_in_sim = " + mIndexInSim + ", slot_id = " + mSlotId;
        }

    }
}
