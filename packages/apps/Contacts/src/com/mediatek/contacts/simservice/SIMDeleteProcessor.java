package com.mediatek.contacts.simservice;

import com.mediatek.contacts.simservice.SIMProcessorManager.ProcessorCompleteListener;
import com.mediatek.contacts.simcontact.SubInfoUtils;
import com.mediatek.contacts.util.LogUtils;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Contacts;
import android.util.Log;

import com.android.contacts.ContactSaveService;
import com.android.contacts.interactions.ContactDeletionInteraction;

public class SIMDeleteProcessor extends SIMProcessorBase {
    private static final String TAG = "SIMDeleteProcessor";

    private static Listener mListener = null;

    private Uri mSimUri = null;
    private Uri mLocalContactUri = null;
    private int mSimIndex = -1;
    private Context mContext;
    private Intent mIntent;
    private int mSubId = SubInfoUtils.getInvalidSubId();

    public final static String SIM_INDEX = "sim_index";
    public final static String LOCAL_CONTACT_URI = "local_contact_uri";

    public interface Listener {
        public void onSIMDeleteFailed();
        public void onSIMDeleteCompleted();
    }

    public static void registerListener(Listener listener) {
        if (listener instanceof ContactDeletionInteraction) {
            LogUtils.d(TAG, "[registerListener]listener added to SIMDeleteProcessor:" + listener);
            mListener = listener;
        }
    }

    public static void unregisterListener(Listener listener) {
        LogUtils.d(TAG, "[unregisterListener]listener removed from SIMDeleteProcessor: " + listener);
        mListener = null;
    }

    public SIMDeleteProcessor(Context context, int subId, Intent intent,
            ProcessorCompleteListener listener) {
        super(intent, listener);
        mContext = context;
        mSubId = subId;
        mIntent = intent;
    }

    @Override
    public int getType() {
        return SIMServiceUtils.SERVICE_WORK_DELETE;
    }

    @Override
    public void doWork() {
        if (isCancelled()) {
            LogUtils.w(TAG, "[dowork]cancel remove work. Thread id = " + Thread.currentThread().getId());
            return;
        }
        mSimUri = mIntent.getData();
        mSimIndex = mIntent.getIntExtra(SIM_INDEX, -1);
        mLocalContactUri = mIntent.getParcelableExtra(LOCAL_CONTACT_URI);
        if (mContext.getContentResolver().delete(mSimUri, "index = " + mSimIndex, null) <= 0) {
            LogUtils.i(TAG, "[doWork] Delete SIM contact failed");
            if (mListener != null) {
                mListener.onSIMDeleteFailed();
            }
        } else {
            LogUtils.i(TAG, "[doWork] Delete SIM contact successfully");

            mContext.startService(ContactSaveService.createDeleteContactIntent(mContext, mLocalContactUri));
            if (mListener != null) {
                mListener.onSIMDeleteCompleted();
            }
        }
    }
}
