package com.mediatek.dialer.calllog;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.View;

import com.android.dialer.R;
import com.android.dialer.calllog.CallLogAdapter;
import com.android.dialer.calllog.CallLogListItemViews;
import com.android.dialer.calllog.CallLogQuery;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.calllog.CallLogAdapter.CallFetcher;
import com.android.dialer.calllog.CallLogAdapter.CallItemExpandedListener;
import com.android.dialer.util.DialerUtils;

import java.util.ArrayList;

/**
 * M: [VoLTE ConfCall] The Volte Conference call member list adapter
 */
public class VolteConfCallMemberListAdapter extends CallLogAdapter{
    private final static String TAG = "VolteConfCallMemberListAdapter";

    public VolteConfCallMemberListAdapter(Context context,
            ContactInfoHelper contactInfoHelper,
            CallItemExpandedListener callItemExpandedListener) {
        super(context, new CallFetcher() {
            @Override
            public void fetchCalls() {
                // Do nothings
            }
        }, contactInfoHelper, callItemExpandedListener, null, true);
        setIsConfCallMemberList(true);
    }

    @Override
    protected void addGroups(Cursor cursor) {
        //Do nothing, no need to group the member list
    }

    @Override
    protected void bindView(View view, Cursor c, int count) {
        Log.d(TAG, "bindView(), cursor = " + c + " count = " + count);
        super.bindView(view, c, count);
        final CallLogListItemViews views = (CallLogListItemViews) view.getTag();
        long duration = c.getLong(CallLogQuery.DURATION);
        // Hide the account label
        views.phoneCallDetailsViews.callAccountLabel.setVisibility(View.GONE);
        // Add the "Missed" or "Answered"
        ArrayList<CharSequence> texts = new ArrayList<CharSequence>();
        texts.add(views.phoneCallDetailsViews.callLocationAndDate.getText());
        texts.add(mContext
                .getText(duration > 0 ? R.string.conf_call_participant_answered
                        : R.string.conf_call_participant_missed));
        views.phoneCallDetailsViews.callLocationAndDate.setText(DialerUtils
                .join(mContext.getResources(), texts));
    }
}
