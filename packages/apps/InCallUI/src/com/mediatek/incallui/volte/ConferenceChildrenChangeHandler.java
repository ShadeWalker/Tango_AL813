package com.mediatek.incallui.volte;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import com.android.incallui.Call;
import com.android.incallui.CallList;
import com.android.incallui.ContactInfoCache;
import com.android.incallui.ContactInfoCache.ContactCacheEntry;
import com.android.incallui.R;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

public class ConferenceChildrenChangeHandler {

    private static final String LOG_TAG = "ConferenceChildrenChangeHandler";
    private static ConferenceChildrenChangeHandler sInstance = new ConferenceChildrenChangeHandler();
    private Context mContext;

    public static synchronized ConferenceChildrenChangeHandler getInstance() {
        if (sInstance == null) {
            sInstance = new ConferenceChildrenChangeHandler();
        }
        return sInstance;
    }

    public void setContext(Context context) {
        mContext = context;
    }

    public void clearContext() {
        mContext = null;
    }

    public Context getContext() {
        return mContext;
    }

    public void handleChildrenChanged(List<String> oldChildrenIds, List<String> newChildrenIds) {
        // TODO: dump both list here for debug.
        log("handleChildrenChanged()...");
        // the two parameters are all ArrayList (not RandomAccess).
        // So here need feature check, whether children are really changed.(add or remove, not order change)
        List<String> remianChildrenIds = new ArrayList<String>();
        List<String> removedChildrenIds = new ArrayList<String>();
        List<String> addedChildrenIds = new ArrayList<String>();

        removedChildrenIds.addAll(oldChildrenIds);
        addedChildrenIds.addAll(newChildrenIds);

        // find childrenIds which both in removedChildrenIds and addedChildrenIds.
        for (String childId : removedChildrenIds) {
            if (addedChildrenIds.contains(childId)) {
                remianChildrenIds.add(childId);
            }
        }

        // find "added" and "removed" childrenIds
        for (String childId : remianChildrenIds) {
            removedChildrenIds.remove(childId);
            addedChildrenIds.remove(childId);
        }

        // notify those children change.
        if (mContext != null) {
            for (String childId : addedChildrenIds) {
                final ContactCacheEntry contactCache = ContactInfoCache.getInstance(mContext)
                        .getInfo(childId);
                if (contactCache != null) {
                    ChildrenChangeNotifier.notifyChildChange(mContext,
                            ChildrenChangeNotifier.NOTIFY_MEMBER_CHANGE_JOIN, contactCache.name);
                } else {
                    // handle no contactInfoCache's case
                    handleChildWithoutContactCache(childId,
                            ChildrenChangeNotifier.NOTIFY_MEMBER_CHANGE_JOIN);
                }
            }

            for (String childId : removedChildrenIds) {
                final ContactCacheEntry contactCache = ContactInfoCache.getInstance(mContext)
                        .getInfo(childId);
                if (contactCache != null) {
                    ChildrenChangeNotifier.notifyChildChange(mContext,
                            ChildrenChangeNotifier.NOTIFY_MEMBER_CHANGE_LEAVE, contactCache.name);
                } else {
                    // handle no contactInfoCache's case
                    handleChildWithoutContactCache(childId,
                            ChildrenChangeNotifier.NOTIFY_MEMBER_CHANGE_LEAVE);
                }
            }
        }
    }

    private void handleChildWithoutContactCache(String childId, int notifyType) {
        log("handleChildWithoutContactCache()...");
        Call call = CallList.getInstance().getCallById(childId);
        if (call != null && mContext != null) {
            ContactInfoCache.getInstance(mContext).findInfoEx(call, false,
                    new ContactLookupCallback(notifyType), false);
        }
    }

    public static class ContactLookupCallback implements ContactInfoCache.ContactInfoCacheCallback {
        private final int mNotifyType;

        public ContactLookupCallback(int notifyType) {
            mNotifyType = notifyType;
        }

        @Override
        public void onContactInfoComplete(String number, ContactCacheEntry entry) {
            Context context = ConferenceChildrenChangeHandler.getInstance().getContext();
            if (context != null) {
                ChildrenChangeNotifier.notifyChildChange(context, mNotifyType, entry.name);
            }
        }

        @Override
        public void onImageLoadComplete(String number, ContactCacheEntry entry) {
            // do nothing
        }
    }

    public static class ChildrenChangeNotifier {
        private static final String TAG = "ChildrenChangeNotifier";

        public static final int NOTIFY_MEMBER_CHANGE_JOIN = 300;
        public static final int NOTIFY_MEMBER_CHANGE_LEAVE = 301;
        public static final int NOTIFY_MEMBER_CHANGE_ADDING = 302;
        public static final int NOTIFY_MEMBER_CHANGE_ADD_FAILED = 303;

        public static void notifyChildChange(Context context, int notifyType, String name) {
            Log.d(TAG, "notifyMemberChange, notifyType = " + notifyType + ", name = " + name);
            String msg = "";
            switch (notifyType) {
            case NOTIFY_MEMBER_CHANGE_LEAVE:
                msg = context.getResources().getString(R.string.conference_member_leave, name);
                break;
            case NOTIFY_MEMBER_CHANGE_JOIN:
                msg = context.getResources().getString(R.string.conference_member_join, name);
                break;
            case NOTIFY_MEMBER_CHANGE_ADDING:
                msg = context.getResources().getString(R.string.conference_member_adding, name);
                break;
            case NOTIFY_MEMBER_CHANGE_ADD_FAILED:
                msg = context.getResources().getString(R.string.conference_member_add_fail, name);
                break;
            default:
                break;
            }
            if (!TextUtils.isEmpty(msg)) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
