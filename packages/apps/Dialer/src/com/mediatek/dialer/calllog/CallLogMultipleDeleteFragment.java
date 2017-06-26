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

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.ListFragment;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ListView;

import com.android.common.io.MoreCloseables;
import com.android.contacts.activities.DialerPlugIn;
import com.android.dialer.R;
import com.android.contacts.common.GeoUtil;
import com.android.dialer.calllog.CallLogQueryHandler;
// / Modified by guofeiyao
import com.android.dialer.calllog.CallLogAdapterEM;
// / End
import com.android.dialer.calllog.ContactInfoHelper;
import com.mediatek.contacts.util.VvmUtils;
import com.mediatek.dialer.activities.CallLogMultipleDeleteActivity;
//import com.mediatek.phone.SIMInfoWrapper;
import com.mediatek.dialer.util.DialerConstants;

// / "implements" is modified by guofeiyao
/**
 * Displays a list of call log entries.
 */
public class CallLogMultipleDeleteFragment extends ListFragment implements
                    CallLogQueryHandler.Listener, CallLogAdapterEM.CallFetcher {
    private static final String TAG = "CallLogMultipleDeleteFragment";

    private CallLogMultipleDeleteAdapter mAdapter;
    private CallLogQueryHandler mCallLogQueryHandler;
    private boolean mScrollToTop;
    private ProgressDialog mProgressDialog;

    private List<SubscriptionInfo> mSubInfoList;//HQ_wuruijun add for HQ01372971
    //TODO private int mCallLogMultipleChoiceTypeFilter = Constants.FILTER_TYPE_UNKNOWN;

    @Override
    public void onCreate(Bundle state) {
        log("onCreate()");
        super.onCreate(state);

        ContentResolver cr = getActivity().getContentResolver();
        mCallLogQueryHandler = new CallLogQueryHandler(cr, this);
        cr.registerContentObserver(CallLog.CONTENT_URI, true,
                mCallLogObserver);
        cr.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI, true, mContactsObserver);
    }

    /** Called by the CallLogQueryHandler when the list of calls has been fetched or updated. */
    @Override
    public boolean onCallsFetched(Cursor cursor) {
        log("onCallsFetched(), cursor = " + cursor);
        if (getActivity() == null || getActivity().isFinishing()) {
            if (null != cursor) {
                cursor.close();
            }
            return false;
        }
        mAdapter.setLoading(false);
        mAdapter.changeCursor(cursor);

        if (mScrollToTop) {
            final ListView listView = getListView();
            if (listView.getFirstVisiblePosition() > 5) {
                listView.setSelection(5);
            }
            listView.smoothScrollToPosition(0);
            mScrollToTop = false;
        }
        return true;
    }

    /** Called by the CallLogQueryHandler when the list of calls has been fetched or updated. */
    @Override
    public void onCallsDeleted() {
        log("onCallsDeleted()");
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        if (null != mProgressDialog) {
            mProgressDialog.dismiss();
        }
        // refreshData();
        getActivity().finish();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        log("onCreateView()");
        View view = inflater.inflate(R.layout.mtk_call_log_multiple_delete_fragment,
                                     container, false);
        /// M: for ALPS00918795 @{
        //TODO register simInfo. After plug out SIM slot,simIndicator will be grey.
        //SIMInfoWrapper.getDefault().registerForSimInfoUpdate(mHandler, SIM_INFO_UPDATE_MESSAGE, null);
        /// @}
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        log("onViewCreated()");
        super.onViewCreated(view, savedInstanceState);
        String currentCountryIso = GeoUtil.getCurrentCountryIso(getActivity());
        mAdapter = new CallLogMultipleDeleteAdapter(getActivity(), this,
                new ContactInfoHelper(getActivity(), currentCountryIso), "");
        setListAdapter(mAdapter);
        //getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        getListView().setItemsCanFocus(true);
        getListView().setFocusable(true);
        getListView().setFocusableInTouchMode(true);
        getListView().setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        refreshData();

        /** M: Fix CR ALPS01569024. Restore selected call log. @{ */
        mAdapter.setSelectedItemChangeListener(mSelectedItemChangeListener);
        if (savedInstanceState != null) {
            ArrayList<Integer> idList = savedInstanceState.getIntegerArrayList(SELECT_ITEM_IDS);
            if (idList != null) {
                mAdapter.setSelectedCallLogIds(idList);
            }
        }
        /** @} */

    }

    @Override
    public void onStart() {
        mScrollToTop = true;
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        //refreshData();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Kill the requests thread

        /// M: @{
        // change the CallLog query way, delete
        // mAdapter.stopRequestProcessing();
        /// @}
    }

    @Override
    public void onStop() {
        super.onStop();
        //updateOnExit();
    }

    @Override
    public void onDestroy() {
        log("onDestroy");
        super.onDestroy();
        /** M: Fix CR ALPS01569024. Remove the SelectedItemChangeListener. @{ */
        mAdapter.removeSelectedItemChangeListener();
        /** @} */
        mAdapter.changeCursor(null);
        /// M: for ALPS00918795 @{
        //TODO unregister simInfo. After plug out SIM slot,simIndicator will be grey.
        //SIMInfoWrapper.getDefault().unregisterForSimInfoUpdate(mHandler);
        /// @}
        getActivity().getContentResolver().unregisterContentObserver(mCallLogObserver);
        getActivity().getContentResolver().unregisterContentObserver(mContactsObserver);
    }

    /**
     * to do nothing
     */
    public void fetchCalls() {
//        if (mShowingVoicemailOnly) {
//            mCallLogQueryHandler.fetchVoicemailOnly();
//        } else {
        //mCallLogQueryHandler.fetchAllCalls();
//        }
    }

    /**
     * M: start call log query
     */
    public void startCallsQuery() {
        mAdapter.setLoading(true);
        Intent intent = this.getActivity().getIntent();
        int callType = intent.getIntExtra(CallLogQueryHandler.CALL_LOG_TYPE_FILTER,
                CallLogQueryHandler.CALL_TYPE_ALL);
        String accountFilter = PhoneAccountInfoHelper.INSTANCE.getPreferAccountId();
        // For deleting calllog from global search
        if ("true".equals(intent.getStringExtra(DialerConstants.IS_GOOGLE_SEARCH))) {
            String data = intent.getStringExtra(SearchManager.USER_QUERY);
            log("Is google search mode, startCallsQuery() data==" + data);
            Uri uri = Uri.withAppendedPath(DialerConstants.CALLLOG_SEARCH_URI_BASE, data);
            /// M: ALPS01903212 support search Voicemail calllog
            uri = VvmUtils.buildVvmAllowedUri(uri);
            mCallLogQueryHandler.fetchSearchCalls(uri);
        //HQ_wuruijun add for HQ01372971 start
        /*} else if (getSubInfoList() == 2) {
            SharedPreferences sharedPreferences = this.getActivity().getSharedPreferences("CallLog_type", Activity.MODE_PRIVATE);
            int mCallLog = sharedPreferences.getInt("CallLog", CallLogQueryHandler.CALL_TYPE_ALL);
            String mSimType = sharedPreferences.getString("SimType", "all_account");
            updateCallList(mCallLog, 0, mSimType);*/
        //HQ_wuruijun add for HQ01372971 end
        } else {
            mCallLogQueryHandler.fetchCalls(callType, 0, accountFilter);
        }
    }
    //HQ_wuruijun add for HQ01372971 start
    private int getSubInfoList() {
        mSubInfoList = SubscriptionManager.from(this.getActivity()).getActiveSubscriptionInfoList();
        return (mSubInfoList != null && !mSubInfoList.isEmpty()) ? mSubInfoList.size() : 0;
    }

    public void updateCallList(int filterType, long dateLimit,String accountId) {
        if(accountId.equals("all_account")){
            mCallLogQueryHandler.fetchCalls(filterType, dateLimit,accountId);
        }else{
            int[] subId = SubscriptionManager.getSubId(Integer.parseInt(accountId));
            Log.d("subId.length",subId.length+"");
            if(subId.length>0)
              mCallLogQueryHandler.fetchCalls(filterType, dateLimit,subId[0]+"");
        }
    }
    //HQ_wuruijun add for HQ01372971 end
    /**
     * get delete selection
     * @return delete selection
     */
    public String getSelections() {
        return mAdapter.getDeleteFilter();
    }

    /** Requests updates to the data to be shown. */
    public void refreshData() {
        log("refreshData()");
        startCallsQuery();
    }

    /**
     * set all item selected
     * @return selected count
     */
    public int selectAllItems() {
//        for(int i = 0; i < getListView().getCount(); ++ i) {
//            getListView().setItemChecked(i, true);
//        }
        int iCount = mAdapter.selectAllItems();
        mAdapter.notifyDataSetChanged();
        return iCount;
    }

    /**
     * cancel select all items
     */
    public void unSelectAllItems() {
//        for(int i = 0; i < getListView().getCount(); ++ i) {
//            getListView().setItemChecked(i, false);
//        }
        mAdapter.unSelectAllItems();
        mAdapter.notifyDataSetChanged();
    }

    /**
     * delete selected call log items
     */
    public void deleteSelectedCallItems() {
        if (mAdapter.getSelectedItemCount() > 0) {
            mProgressDialog = ProgressDialog.show(getActivity(), "",
                    getString(R.string.deleting_call_log));
        }
        mCallLogQueryHandler.deleteSpecifiedCalls(mAdapter.getDeleteFilter());
        //HQ_wuruijun add for HQ01432449 start
        //DialerPlugIn.mSingleCallLog = DialerPlugIn.CALL_TYPE_ALL;
        //DialerPlugIn.choiceItemId = 0;
        //HQ_wuruijun add end
    }

    /**
     * Response click the list item
     *
     * @param l listview
     * @param v view
     * @param position position
     * @param id id
     */
    public void onListItemClick(ListView l, View v, int position, long id) {

        log("onListItemClick: position:" + position);

        CheckBox checkBox = (CheckBox) v.findViewById(R.id.checkbox);
        if (null != checkBox) {
            boolean isChecked = checkBox.isChecked();
            ((CallLogMultipleDeleteActivity) getActivity()).updateSelectedItemsView(mAdapter
                    .changeSelectedStatusToMap(position));
            checkBox.setChecked(!isChecked);
            refreshSelectView();
        }
    }
    //HQ_wuruijun add for HQ01391001 start
    private void refreshSelectView() {
        boolean mIsSelectedAll = false;
        MenuItem isAllSelect = ((CallLogMultipleDeleteActivity) getActivity()).selectAll;
        if (isAllSelect != null) {
            if (isAllSelected()) {
                isAllSelect.setTitle(R.string.menu_select_none);
            }
            else {
                isAllSelect.setTitle(R.string.menu_select_all);
            }
        }
    }
    //HQ_wuruijun add end

    /**
     * to do nothing
     * @param statusCursor cursor
     */
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        /// M: ALPS01260098 @{
        // Cursor Leak check.
        MoreCloseables.closeQuietly(statusCursor);
        /// @}
    }

    /**
     * get selected item count
     * @return count
     */
    public int getSelectedItemCount() {
        return mAdapter.getSelectedItemCount();
    }

    /**
     * Get the count of the call log list items
     * @return the count of list items
     */
    public int getItemCount() {
        return mAdapter.getCount();
    }

    private void log(String log) {
        Log.i(TAG, log);
    }
    /**
     *
     * @return if all selected
     */
    public boolean isAllSelected() {
        // get total count of list items.
        int count = getListView().getAdapter().getCount();
        if (count == 0) {
            return false;
        }
        return count == getSelectedItemCount();
    }
    /// M: for ALPS00918795 @{
    // listen simInfo. After plug out SIM slot,simIndicator will be grey.
    private static final int SIM_INFO_UPDATE_MESSAGE = 100;
    private static final int MSG_DB_CHANGED = 101;

    private Handler mHandler = new Handler() {

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SIM_INFO_UPDATE_MESSAGE:
                    if (mAdapter != null) {
                        mAdapter.invalidateCache();
                    }
                    startCallsQuery();
                    break;
                case MSG_DB_CHANGED:
                    removeMessages(MSG_DB_CHANGED);
                    if (isResumed()) {
                        refreshData();
                    }
                    break;
                default:
                    break;
            }
        }
    };
    /// @}

    /// M: for ALPS01375185 @{
    // amend it for querying all CallLog on choice interface
    /* TODO Wait for account filter
    public void setCallLogMultipleChoiceTypeFilter(int typefilter){
        mCallLogMultipleChoiceTypeFilter = typefilter;
    }
    */
    /// @}

    /// M: fix ALPS01524672, save checked states of list item @{
    private final static String KEY = "KEY";
    private final static String VALUE = "VALUE";
    /** M: Fix CR ALPS01569024. Define the SelectedItemChangeListener. @{ */
    private final static String SELECT_ITEM_IDS = "select_item_ids";
    private CallLogMultipleDeleteAdapter.SelectedItemChangeListener mSelectedItemChangeListener =
            new CallLogMultipleDeleteAdapter.SelectedItemChangeListener() {
        @Override
        public void onSelectedItemCountChange(int count) {
            if (getActivity() != null) {
                ((CallLogMultipleDeleteActivity) getActivity()).updateSelectedItemsView(count);
            }
        }
    };
    /** @} */

    @Override
    public void onSaveInstanceState(Bundle outState) {
        /** M: Fix CR ALPS01569024. Save the selected call log id list. @{ */
        outState.putIntegerArrayList(SELECT_ITEM_IDS, mAdapter.getSelectedCallLogIds());
        super.onSaveInstanceState(outState);
        /** @} */
    }
    /// @}

    private final ContentObserver mCallLogObserver = new CustomContentObserver();
    private final ContentObserver mContactsObserver = new CustomContentObserver();

    private class CustomContentObserver extends ContentObserver {

        public CustomContentObserver() {
            super(mHandler);
        }
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            mHandler.sendEmptyMessageDelayed(MSG_DB_CHANGED, 1000);
        }
    }
}
