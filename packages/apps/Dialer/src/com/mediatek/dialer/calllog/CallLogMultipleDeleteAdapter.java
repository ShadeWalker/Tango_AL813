/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.mediatek.dialer.calllog;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.CheckBox;


import com.android.dialer.R;
// / Modified by guofeiyao
import com.android.dialer.calllog.CallLogAdapterEM;
// / End
import com.android.dialer.calllog.CallLogListItemViews;
import com.android.dialer.calllog.CallLogQuery;
import com.android.dialer.calllog.ContactInfoHelper;

import java.util.ArrayList;

import huawei.android.widget.TimeAxisWidget;
import com.android.incallui.InCallApp;


// / Parent class is modified by guofeiyao
public class CallLogMultipleDeleteAdapter extends CallLogAdapterEM {

    private static final String LOG_TAG = "CallLogMultipleDeleteAdapter";
    private Cursor mCursor;


    /** M: Fix CR ALPS01569024. Save the selected call log id list. @{ */
    private final ArrayList<Integer> mSelectedCallLogIdList = new ArrayList<Integer>();
    private int mSelectedItemCount = 0;
    // A listener to listen the selected item change
    public interface SelectedItemChangeListener {
        public void onSelectedItemCountChange(int count);
    }
    private SelectedItemChangeListener mSelectedItemChangeListener;
    /** @} */

    /**
     * Construct function
     *
     * @param context context
     * @param callFetcher Callfetcher
     * @param contactInfoHelper contactinfohelper
     * @param voicemailNumber voicemailNumber
     */
    public CallLogMultipleDeleteAdapter(Context context, CallFetcher callFetcher,
            ContactInfoHelper contactInfoHelper, String voicemailNumber) {
        super(context, callFetcher, contactInfoHelper, null, null, true);
    }

    /**
     * @param cursor cursor
     */
    public void changeCursor(Cursor cursor) {
        log("changeCursor(), cursor = " + cursor);
        if (null != cursor) {
            log("cursor count = " + cursor.getCount());
        }
        if (mCursor != cursor) {
            mCursor = cursor;
        }
        super.changeCursor(cursor);

        /** M: Fix CR ALPS01569024. Reconcile the selected call log ids
         *  with the ids in the new cursor. @{ */
        reconcileSeletetedItems(cursor);
        if (mSelectedItemChangeListener != null) {
            mSelectedItemChangeListener.onSelectedItemCountChange(
                    mSelectedItemCount);
        }
        /** @} */
    }

    /**
     * Binds the views in the entry to the data in the call log.
     *
     * @param view the view corresponding to this entry
     * @param c the cursor pointing to the entry in the call log
     * @param count the number of entries in the current item, greater than 1 if it is a group
     */
    protected void bindView(View viewHW, Cursor c, int count) {
        log("bindView(), cursor = " + c + " count = " + count);

		//add by zhangjinqiang for al812--start
		 TimeAxisWidget taw=null;
		 View tView=null;
		 if(InCallApp.gIsHwUi){
			 taw = (TimeAxisWidget)viewHW.findViewById(R.id.item_date);
			 tView = taw.getContent();
	 	}else{
			tView=viewHW;
		}
		//add by zhangjinqiang for al812--end

        /** M: Fix CR ALPS01569024. Use the call log id to identify the select item. @{ */
        boolean checkState = false;
        if (mSelectedCallLogIdList.size() > 0) {
            int position = c.getPosition();
            for (int i = 0; i < count; i++) {
                if (!c.moveToPosition(position + i)) {
                    continue;
                }
                if (mSelectedCallLogIdList.contains(c.getInt(CallLogQuery.ID))) {
                    checkState = true;
                    break;
                }
            }
            c.moveToPosition(position);
        }
        /** @} */

        super.bindView(viewHW, c, count);


        ((ViewGroup) viewHW).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        // set clickable false
        final CallLogListItemViews views = (CallLogListItemViews) tView.getTag();
        views.primaryActionView.setFocusable(false);
        views.primaryActionView.setClickable(false);
        // Disable the quick contact view
        views.quickContactView.setEnabled(false);


        // add check box for call log item
        CheckBox checkBox = (CheckBox) tView.findViewById(R.id.checkbox);
        if (checkBox == null) {
            final ViewStub stub = (ViewStub) tView.findViewById(R.id.checkbox_container);
            View inflatedView = null;
            if (stub != null) {
                inflatedView = stub.inflate();
                checkBox = (CheckBox) inflatedView.findViewById(R.id.checkbox);
            }
        }
        if (checkBox != null) {
            checkBox.setFocusable(false);
            checkBox.setClickable(false);
            checkBox.setVisibility(View.VISIBLE);
            checkBox.setChecked(checkState);
        }

        // disable other action buttons when delete
        /*
        View secondaryActionIcon = view.findViewById(R.id.secondary_action_view);
        if(secondaryActionIcon != null) {
            secondaryActionIcon.setVisibility(View.GONE);
        }*/

        /// M: for LandScape UI @{
        View selectedIcon = tView.findViewById(R.id.selected_icon);
        if (selectedIcon != null) {
            selectedIcon.setVisibility(View.GONE);
        }
        /// @}
    }

    /**
     * select all items
     *
     * @return the selected items numbers
     */
    public int selectAllItems() {
        log("selectAllItems()");
        /** M: Fix CR ALPS01569024. Use the call log id to identify the select item. @{ */
        mSelectedItemCount = getCount();
        mSelectedCallLogIdList.clear();
        //add by wanghui nullpoint
        if(null == mCursor){
           return mSelectedItemCount;
		}
        mCursor.moveToPosition(-1);
        while (mCursor.moveToNext()) {
            mSelectedCallLogIdList.add(mCursor.getInt(CallLogQuery.ID));
        }
        return mSelectedItemCount;
        /** @} */
    }

    /**
     * unselect all items
     */
    public void unSelectAllItems() {
        log("unSelectAllItems()");
        /** M: Fix CR ALPS01569024. Use the call log id to identify the select item. @{ */
        mSelectedItemCount = 0;
        mSelectedCallLogIdList.clear();
        /** @} */
    }

    /**
     * get delete filter
     *
     * @return the delete selection
     */
    public String getDeleteFilter() {
        log("getDeleteFilter()");
        StringBuilder where = new StringBuilder("_id in ");
        where.append("(");
        /** M: Fix CR ALPS01569024. Use the call log id to identify the select item. @{ */
        if (mSelectedCallLogIdList.size() > 0) {
            boolean isFirst = true;
            for (int id : mSelectedCallLogIdList) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    where.append(",");
                }
                where.append("\'");
                where.append(id);
                where.append("\'");
            }
        } else {
            where.append(-1);
        }
        /** @} */

        where.append(")");
        log("getDeleteFilter() where ==  " + where.toString());
        return where.toString();
    }

    /**
     * change selected status to map
     *
     * @param listPosition position to change
     * @return int
     */
    public int changeSelectedStatusToMap(final int listPosition) {
        log("changeSelectedStatusToMap()");
        int count = 0;
        if (isGroupHeader(listPosition)) {
            count = getGroupSize(listPosition);
        } else {
            count = 1;
        }

        /** M: Fix CR ALPS01569024. Use the call log id to identify the select item. @{ */
        Cursor cursor = (Cursor) getItem(listPosition);
        if (cursor == null) {
            return mSelectedItemCount;
        }
        int position = cursor.getPosition();
        int firstId = cursor.getInt(CallLogQuery.ID);
        boolean shouldSelected = false;
        if (mSelectedCallLogIdList.contains(firstId)) {
            shouldSelected = false;
            mSelectedItemCount--;
        } else {
            shouldSelected = true;
            mSelectedItemCount++;
        }
        for (int i = 0; i < count; i++) {
            if (!cursor.moveToPosition(position + i)) {
                continue;
            }
            int id = cursor.getInt(CallLogQuery.ID);
            if (shouldSelected) {
                mSelectedCallLogIdList.add(id);
            } else {
                mSelectedCallLogIdList.remove((Integer) id);
            }
        }
        cursor.moveToPosition(position);
        return mSelectedItemCount;
        /** @} */
    }

    /**
     * get selected items count
     *
     * @return the count of selected
     */
    public int getSelectedItemCount() {
        log("getSelectedItemCount()");
        /// M: Fix CR ALPS01569024. Use the call log id to identify the select item.
        return mSelectedItemCount;
    }

    private void log(final String log) {
        Log.i(LOG_TAG, log);
    }

    /** M: Fix CR ALPS01569024. @{ */
    /**
     * Get the id list of selected call log.
     * @return the id list of selected call log.
     */
    public ArrayList<Integer> getSelectedCallLogIds() {
        return new ArrayList<Integer>(mSelectedCallLogIdList);
    }

    /**
     * Set the id list of selected call log.
     */
    public void setSelectedCallLogIds(ArrayList<Integer> idList) {
        mSelectedCallLogIdList.clear();
        mSelectedCallLogIdList.addAll(idList);
    }

    /**
     * Reconcile the selected call log ids with the ids in the new cursor.
     */
    private boolean reconcileSeletetedItems(Cursor newCursor) {
        if (mSelectedCallLogIdList.isEmpty()) {
            return false;
        }
        if (newCursor == null || newCursor.getCount() <= 0) {
            mSelectedCallLogIdList.clear();
            mSelectedItemCount = 0;
            return true;
        }
        ArrayList<Integer> idList = new ArrayList<Integer>();
        ArrayList<Integer> groupIdList = new ArrayList<Integer>();
        int newSelectedItemCount = 0;
        for (int i = 0; i < getCount(); ++i) {
            int count = 0;
            Cursor cursor = (Cursor) getItem(i);
            if (cursor == null) {
                continue;
            }
            int position = cursor.getPosition();
            if (isGroupHeader(i)) {
                count = getGroupSize(i);
            } else {
                count = 1;
            }
            boolean haveSelectedCallLog = false;
            groupIdList.clear();
            for (int j = 0; j < count; j++) {
                if (!mCursor.moveToPosition(position + j)) {
                    continue;
                }
                int id = mCursor.getInt(CallLogQuery.ID);
                groupIdList.add(id);
                if (!haveSelectedCallLog && mSelectedCallLogIdList.contains(id)) {
                    haveSelectedCallLog = true;
                }
            }
            if (haveSelectedCallLog) {
                newSelectedItemCount++;
                idList.addAll(groupIdList);
            }
        }
        mSelectedCallLogIdList.clear();
        mSelectedCallLogIdList.addAll(idList);
        mSelectedItemCount = newSelectedItemCount;
        return true;
    }

    public void setSelectedItemChangeListener(SelectedItemChangeListener listener) {
        mSelectedItemChangeListener = listener;
    }

    public void removeSelectedItemChangeListener() {
        mSelectedItemChangeListener = null;
    }
    /** @} */

    /// M:TODO Fix CR: ALPS01660914, add Contact photo view @{
    /*
    @Override
    protected void setPhoto(ImageView view, long photoId, Uri contactUri,
            String displayName, String identifier, int contactType, String number) {

        DefaultImageRequest request = new DefaultImageRequest(displayName, identifier, contactType);
        getContactPhotoManager().loadThumbnail(view, photoId, false, request);
    }

    @Override
    protected void setPhoto(ImageView view, Uri photoUri, Uri contactUri,
            String displayName, String identifier, int contactType , String number) {

        DefaultImageRequest request = new DefaultImageRequest(displayName, identifier, contactType);
        getContactPhotoManager().loadDirectoryPhoto(view, photoUri, false, request);
    }

    @Override
    public boolean isQuickContactEnabled() {
        return false;
    }
    */
    /// M: @}
}
