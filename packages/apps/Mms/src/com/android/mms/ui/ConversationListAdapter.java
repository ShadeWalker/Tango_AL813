/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.ui;


import com.android.mms.R;
import com.android.mms.data.Conversation;
import com.android.mms.PDebug;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.CursorAdapter;
/// M:
import java.util.HashSet;
import com.android.mms.util.MmsLog;

/*HQ_zhangjing add for al812 mms ui begin*/
import huawei.android.widget.TimeAxisWidget;
import java.util.Calendar;
import android.widget.CheckBox;
/*HQ_zhangjing add for al812 mms ui end*/
/**
 * The back-end data adapter for ConversationList.
 */
//TODO: This should be public class ConversationListAdapter extends ArrayAdapter<Conversation>
public class ConversationListAdapter extends MessageCursorAdapter implements AbsListView.RecyclerListener {
    private static final String TAG = "ConversationListAdapter";
    private static final boolean LOCAL_LOGV = false;

    private final LayoutInflater mFactory;
    private OnContentChangedListener mOnContentChangedListener;

    /// M:
    private boolean mSubjectSingleLine = false;

    private static HashSet<Long> sSelectedTheadsId;
	//HQ_zhangjing add for al812 mms ui
    private boolean isMultiChoiceMode = false;
    public ConversationListAdapter(Context context, Cursor cursor) {
        super(context, cursor, false /* auto-requery */);
        mFactory = LayoutInflater.from(context);
        sSelectedTheadsId = new HashSet<Long>();
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
			/*HQ_zhangjing add for al812 mms ui begin*/
//        if (!(view instanceof ConversationListItem)) {
//            Log.e(TAG, "Unexpected bound view: " + view);
//            return;
//        }
        TimeAxisWidget taw = (TimeAxisWidget) view.findViewById(R.id.item_date);
        ConversationListItem headerView = (ConversationListItem)taw.getContent();
		/*HQ_zhangjing add for al812 mms ui end*/
        /// M: Code analyze 027, For bug ALPS00331731, set conversation cache . @{
        Conversation conv;
        if (!mIsScrolling) {
            Conversation.setNeedCacheConv(false);
            conv = Conversation.from(context, cursor);
            Conversation.setNeedCacheConv(true);
            if (mSubjectSingleLine) {
                headerView.setSubjectSingleLineMode(true);
            }
            if (conv != null) {
                conv.setIsChecked(sSelectedTheadsId.contains(conv.getThreadId()));
            }
            headerView.bind(context, conv);
        } else {
            conv = Conversation.getConvFromCache(context, cursor);
            if (conv != null) {
                conv.setIsChecked(sSelectedTheadsId.contains(conv.getThreadId()));
            }
            headerView.bindDefault(conv);
        }
        /// @}
		/*HQ_zhangjing add for al812 mms ui begin*/
        CheckBox checkBox = (CheckBox)headerView.findViewById(R.id.select_check_box);
        checkBox.setVisibility(isMultiChoiceMode?View.VISIBLE:View.GONE);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(conv.getDate());
        taw.setCalendar(cal);
		/*HQ_zhangjing add for al812 mms ui end*/
		
        PDebug.End("ConversationListAdapter.bindView");
    }

    public void onMovedToScrapHeap(View view) {
		/*HQ_zhangjing add for al812 mms ui begin*/
		//ConversationListItem headerView = (ConversationListItem)view;
        TimeAxisWidget taw = (TimeAxisWidget) view.findViewById(R.id.item_date);
        ConversationListItem headerView = (ConversationListItem)taw.getContent();
		/*HQ_zhangjing add for al812 mms ui end*/
        headerView.unbind();
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        if (LOCAL_LOGV) Log.v(TAG, "inflating new view");
		/*HQ_zhangjing add for al812 mms ui begin*/
		//return mFactory.inflate(R.layout.conversation_list_item, parent, false);
        ConversationListItem listItemView  = (ConversationListItem) mFactory.inflate(R.layout.conversation_list_item, parent, false);
        View v = mFactory.inflate(R.layout.list_item_timeaxis, parent, false);
        TimeAxisWidget taw = (TimeAxisWidget) v.findViewById(R.id.item_date);
        taw.setContent(listItemView);
        return  v;
		/*HQ_zhangjing add for al812 mms ui end*/
        
    }

    public interface OnContentChangedListener {
        void onContentChanged(ConversationListAdapter adapter);
    }

    public void setOnContentChangedListener(OnContentChangedListener l) {
        mOnContentChangedListener = l;
    }

    @Override
    protected void onContentChanged() {
        if (mCursor != null && !mCursor.isClosed()) {
            if (mOnContentChangedListener != null) {
                mOnContentChangedListener.onContentChanged(this);
            }
        }
    }

    /// M: Code analyze 026, personal use, caculate time . @{
     @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        MmsLog.i(TAG, "[Performance test][Mms] loading data end time ["
            + System.currentTimeMillis() + "]");
    }
     /// @}

    /// M: Code analyze 007, For bug ALPS00242955, If adapter data is valid . @{
    public boolean isDataValid() {
        return mDataValid;
    }
    /// @}

    /// M:
    public void setSubjectSingleLineMode(boolean value) {
        mSubjectSingleLine = value;
    }

    /// M: For ConversationList to check listener @{
    public OnContentChangedListener getOnContentChangedListener() {
        return mOnContentChangedListener;
    }
    /// @}

    public void setSelectedState(long threadid) {
        if (sSelectedTheadsId != null) {
            sSelectedTheadsId.add(threadid);
        }
    }

    public static void removeSelectedState(long threadid) {
        if (sSelectedTheadsId != null) {
            sSelectedTheadsId.remove(threadid);
        }
    }

    public boolean isContainThreadId(long threadid) {
        if (sSelectedTheadsId != null) {
            return sSelectedTheadsId.contains(threadid);
        }
        return false;
    }

    public void clearstate() {
        if (sSelectedTheadsId != null) {
            sSelectedTheadsId.clear();
        }
    }

    public HashSet<Long> getSelectedThreadsList() {
            return sSelectedTheadsId;
    }
	/*HQ_zhangjing add for al812 mms ui begin*/
    public void uncheckAll() {
        int count = getCount();
        for (int i = 0; i < count; i++) {
            Cursor cursor = (Cursor)getItem(i);
            Conversation conv = Conversation.from(mContext, cursor);
            conv.setIsChecked(false);
        }
    }
    
    public void setMultiChoiceMode(boolean b) {
        isMultiChoiceMode = b;
    }
	/*HQ_zhangjing add for al812 mms ui end*/
}
