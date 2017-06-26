package com.mediatek.contacts.quickcontact;

import android.content.Context;
import android.provider.ContactsContract.Data;

import com.android.contacts.common.model.dataitem.DataItem;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.quickcontact.DataAction;
import com.mediatek.contacts.util.LogUtils;

public class QuickDataAction extends DataAction {
    private static final String TAG = "QuickDataAction";
    private int mSimId = 0;

    public QuickDataAction(Context context, DataItem item, boolean isDirectoryEntry, DataKind kind) {
        super(context, item, kind);
        if (isDirectoryEntry) {
            mSimId = item.getContentValues().getAsInteger(Data.SIM_ASSOCIATION_ID);
        }
        LogUtils.d(TAG, "mSimId : " + mSimId + " , isDirectoryEntry : " + isDirectoryEntry);
    }

    public int getSimId() {
        return mSimId;
    }
}
