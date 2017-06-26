package com.mediatek.contacts.simservice;

import com.mediatek.contacts.simservice.SIMProcessorManager.ProcessorCompleteListener;

import android.content.Context;
import android.content.Intent;

import com.mediatek.contacts.util.LogUtils;

public class SIMRemoveProcessor extends SIMProcessorBase {
    private static final String TAG = "SIMRemoveProcessor";
    private int mSubId;
    private Context mContext;

    public SIMRemoveProcessor(Context context, int subId, Intent intent,
            ProcessorCompleteListener listener) {
        super(intent, listener);
        mContext = context;
        mSubId = subId;
    }

    @Override
    public int getType() {
        return SIMServiceUtils.TYPE_REMOVE;
    }

    @Override
    public void doWork() {
        if (isCancelled()) {
            LogUtils.d(TAG, "[doWork]cancel remove work. Thread id=" + Thread.currentThread().getId());
            return;
        }
        SIMServiceUtils.deleteSimContact(mContext, mSubId);
    }
}
