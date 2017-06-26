
package com.mediatek.contacts.list;

import android.content.Loader;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.ContactEntryListFragment;

import com.mediatek.contacts.GlobalEnv;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.util.MtkToast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * The Fragment Base class to handle the basic functions.
 */
public abstract class AbstractPickerFragment extends ContactEntryListFragment<ContactEntryListAdapter>
        implements ContactListMultiChoiceListener {
    private static final String TAG = "AbstractPickerFragment";

    private static final String KEY_CHECKEDIDS = "checkedids";

    /**
     * The default limit multi choice max count,default is 3500
     */
    public static final int DEFAULT_MULTI_CHOICE_MAX_COUNT = 3500;
	//HQ_zhangjing 2015-10-09 modified for CQ HQ01435286
	private int mSelectLimitNum = DEFAULT_MULTI_CHOICE_MAX_COUNT;

    private String mSlectedItemsFormater = null;

    private String mSearchString = null;

    // Show account filter settings
    private View mAccountFilterHeader = null;

    private TextView mEmptyView = null;

    // is or is not select all items.
    private boolean mIsSelectedAll = false;
    // is or is not select on item.
    private boolean mIsSelectedNone = true;

    private Set<Long> mCheckedIds = new HashSet<Long>();
    private Set<Integer> mCheckedIdOptions = new HashSet<Integer>();//modify by wangmingyue for HQ01609514

	/*modified for HQ02061159 by zhangzhihua begin */
	private EmptyViewCallBack emptyViewCallback;

	public interface EmptyViewCallBack{
		void isVisibility(int visibility);
	}

	public void setEmptyViewCallBack(EmptyViewCallBack callback){
		emptyViewCallback = callback;
	}
	/*modified for HQ02061159 by zhangzhihua end */

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mSlectedItemsFormater = getActivity().getString(R.string.menu_actionbar_selected_items);

        showSelectedCount(mCheckedIds.size());
        //here should disable the Ok Button,because if call #onActivityCreated()
        //it says it is first Run this Activity or the previous process has been killed
        //the start this Activity again,then it will load data from DB in #onStart()
        setOkButtonStatus(false);

        //Enable multiple choice mode.
        getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    }


    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.mtk_multichoice_contact_list, null);
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);
        mAccountFilterHeader = getView().findViewById(R.id.account_filter_header_container);

        mEmptyView = (TextView) getView().findViewById(R.id.contact_list_empty);
        if (mEmptyView != null) {
            mEmptyView.setText(R.string.noContacts);
        }
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();
        ContactEntryListAdapter adapter = getAdapter();
        if (adapter == null) {
            LogUtils.d(TAG, "[configureAdapter]adapter is null.");
            return;
        }
        adapter.setEmptyListEnabled(true);
        // Show A-Z section index.
        adapter.setSectionHeaderDisplayEnabled(true);
        adapter.setDisplayPhotos(true);
        adapter.setQuickContactEnabled(false);
        super.setPhotoLoaderEnabled(true);
        adapter.setQueryString(mSearchString);
        adapter.setIncludeProfile(false);

        // Apply MTK theme manager
        if (mAccountFilterHeader != null) {
            final TextView headerTextView = (TextView) mAccountFilterHeader
                    .findViewById(R.id.account_filter_header);

            if (headerTextView != null) {
                headerTextView.setText(R.string.mtk_contact_list_loading);
                mAccountFilterHeader.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    protected void onItemClick(int position, long id) {
        return;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        super.onItemClick(parent, view, position, id);

        int multiChoiceLimitCount = getMultiChoiceLimitCount();
        if (getListView().isItemChecked(position)) {
            if (mCheckedIds.size() >= multiChoiceLimitCount) {
                String msg = getResources().getString(R.string.multichoice_contacts_limit, multiChoiceLimitCount);
                MtkToast.toast(getActivity().getApplicationContext(), msg);
                getListView().setItemChecked(position, false);
                // Fix bug for ALPS01507616.
                // In search mode, do not set selection to position "multiChoiceLimitCount - 1",
                // because there may not have so many items in list of search results.
                // In search mode, no need this action.
                if (!getAdapter().isSearchMode()) {
                    getListView().setSelection(multiChoiceLimitCount - 1);
                }
                LogUtils.w(TAG, "[onItemClick] mCheckedIds size:" + mCheckedIds.size() + " >= limit:"
                        + multiChoiceLimitCount);
                return;
            }
            mCheckedIds.add(Long.valueOf(id));
            mCheckedIdOptions.add(position);//modify by wangmingyue for HQ01609514
        } else {
            mCheckedIds.remove(Long.valueOf(id));
            mCheckedIdOptions.remove(position);//modify by wangmingyue for HQ01609514
        }
        /// fix bug for ALPS00123809:check box not enabled
        getListView().setItemChecked(position, getListView().isItemChecked(position));

        updateSelectedItemsView(mCheckedIds.size());
		updateSelectAll(ContactListMultiChoiceActivity.instanceSelect);// HQ_xiatao modify all-selected button  
    }

    @Override
    public void onClearSelect() {
        updateCheckBoxState(false);
    }

    @Override
    public void onSelectAll() {
        updateCheckBoxState(true);
    }
	/*HQ_zhangjing 2015-10-09 modified for CQ HQ01435286 begin*/
	public void setSelectLimit( int limit ){
		mSelectLimitNum = limit;
	}
	/*HQ_zhangjing 2015-10-09 modified for CQ HQ01435286 end*/
	
    @Override
    public void restoreSavedState(Bundle savedState) {
        super.restoreSavedState(savedState);

        if (savedState == null) {
            LogUtils.i(TAG, "[restoreSavedState]saved state is null");
            return;
        }

        if (mCheckedIds == null) {
            mCheckedIds = new HashSet<Long>();
        }
        mCheckedIds.clear();

        long[] ids = savedState.getLongArray(KEY_CHECKEDIDS);
        int checkedItemSize = ids.length;
        LogUtils.d(TAG, "[restoreSavedState]restore " + checkedItemSize + " ids");
        for (int index = 0; index < checkedItemSize; ++index) {
            mCheckedIds.add(Long.valueOf(ids[index]));
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        LogUtils.d(TAG, "onLoadFinished");
        ///M:check whether the fragment still in Activity@{
        if (!isAdded()) {
            LogUtils.w(TAG, "onLoadFinished(),This Fragment is not add to the Activity now.data:" + data);
            if (data != null) {
                data.close();
            }
            return;
        }
        ///@}
        if (data != null) {
            LogUtils.w(TAG, "cursor data = " + data);
            int[] ids = data.getExtras().getIntArray("checked_ids");
            if (ids != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("[onLoadFinished] ids: ");
                for (int id : ids) {
                    sb.append(String.valueOf(id) + ",");
                }
                LogUtils.d(TAG, sb.toString());

                for (Iterator<Long> it = mCheckedIds.iterator(); it.hasNext(); ) {
                    Long id = it.next();
                    if (Arrays.binarySearch(ids, id.intValue()) < 0) {
                        it.remove();
                    }
                }
            }
        }

        if (getAdapter().isSearchMode()) {
            LogUtils.d(TAG, "[onLoadFinished]SearchMode");
            getListView().setFastScrollEnabled(false);
            getListView().setFastScrollAlwaysVisible(false);
        }

        if (data == null || (data != null && data.getCount() == 0)) {
            LogUtils.d(TAG, "[onLoadFinished]nothing loaded, empty view: " + mEmptyView);
            if (mEmptyView != null) {
                if (getAdapter().isSearchMode()) {
                    mEmptyView.setText(R.string.listFoundAllContactsZero);
                } else {
                    mEmptyView.setText(R.string.noContacts);
                }
                mEmptyView.setVisibility(View.VISIBLE);
            }
            // Disable fast scroll bar
            getListView().setFastScrollEnabled(false);
            getListView().setFastScrollAlwaysVisible(false);
        } else {
            if (mEmptyView != null) {
                if (getAdapter().isSearchMode()) {
                    mEmptyView.setText(R.string.listFoundAllContactsZero);
                } else {
                    mEmptyView.setText(R.string.noContacts);
                }
                mEmptyView.setVisibility(View.GONE);
            }
            // Enable fast scroll bar
            if (!getAdapter().isSearchMode()) {
                getListView().setFastScrollEnabled(true);
                getListView().setFastScrollAlwaysVisible(true);
            }
        }
		/*modified for HQ02061159 by zhangzhihua begin */
		emptyViewCallback.isVisibility(mEmptyView.getVisibility());
		/*modified for HQ02061159 by zhangzhihua end */

        // clear list view choices
        getListView().clearChoices();

        Set<Long> newDataSet = new HashSet<Long>();

        long dataId = -1;
        int position = 0;

        if (data != null) {
            LogUtils.d(TAG, "[onLoadFinished]query data count: " + data.getCount());
            data.moveToPosition(-1);
            while (data.moveToNext()) {
                dataId = -1;
                dataId = data.getInt(0);
                newDataSet.add(dataId);

                if (mCheckedIds.contains(dataId) || mPushedIds.contains(dataId)) {
                    getListView().setItemChecked(position, true);
                }

                ++position;

                handleCursorItem(data);
            }
        }
        if (!getAdapter().isSearchMode()) {
            for (Iterator<Long> it = mCheckedIds.iterator(); it.hasNext(); ) {
                Long id = it.next();
                if (!newDataSet.contains(id)) {
                    it.remove();
                }
            }
        }
        ///M: fix [ALPS00539605] first onLoadComplete won't load newly pushed id data.
        /// so, the pushed ids should be merged in mCheckedIds after first onLoadFinished finished.
         if (!mPushedIds.isEmpty()) {
            mCheckedIds.addAll(mPushedIds);
            mPushedIds.clear();
        }
        updateSelectedItemsView(mCheckedIds.size());

        //fix [ALPS00578162]
        // the super class will try to restore the list view state if the
        // process been killed
        // in back ground,and the state has been set to the lasted,so clear last
        // state to prevent restore.
        clearListViewLastState();

        // The super function has to be called here.
        super.onLoadFinished(loader, data);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        final int checkedItemsCount = mCheckedIds.size();
        long[] checkedIds = new long[checkedItemsCount];
        int index = 0;
        for (Long id : mCheckedIds) {
            checkedIds[index++] = id;
        }
        LogUtils.d(TAG, "[onSaveInstanceState]save " + checkedIds.length + " ids");
        outState.putLongArray(KEY_CHECKEDIDS, checkedIds);
    }
	// HQ_xiatao modify all-selected button  start
	interface ISelectAllStatus{
    	void onSelectAllChanged(boolean status);
    }
	public void updateSelectAll(ISelectAllStatus sas){
		 sas.onSelectAllChanged(mIsSelectedAll);
	}
	// HQ_xiatao modify all-selected button  end

    public void updateSelectedItemsView(int checkedItemsCount) {
        // if there is no item selected, the "OK" button disable.
        Button optionView = (Button) getActivity().getActionBar().getCustomView().findViewById(
                R.id.menu_option);
        if (checkedItemsCount == 0) {
            mIsSelectedNone = true;
        } else {
            mIsSelectedNone = false;
        }
        setOkButtonStatus(!mIsSelectedNone);

        if (getAdapter().isSearchMode()) {
            LogUtils.w(TAG, "#updateSelectedItemsView(),isSearchMonde,don't showSelectedCount:" + checkedItemsCount);
            return;
        }
		 //HQ_xiatao modify all-selected button start
        final int count = getListView().getAdapter().getCount();
        /** M: Add consideration of "0" case @{ */
        if (count != 0 && count == getListView().getCheckedItemCount() || checkedItemsCount >= getMultiChoiceLimitCount()) {
            mIsSelectedAll = true;
        } else {
            mIsSelectedAll = false;
        }
		// HQ_xiatao modify all-selected button end

        showSelectedCount(checkedItemsCount);
    }

    public void updateSelectedItemsView() {
        final ContactEntryListAdapter adapter = (ContactEntryListAdapter) getAdapter();
        final int count = getListView().getAdapter().getCount();
        final int checkCount = mCheckedIds.size();
        updateSelectedItemsView(checkCount);
        /** M: Add consideration of "0" case @{ */
        if (count != 0 && count == getListView().getCheckedItemCount() || checkCount >= getMultiChoiceLimitCount()) {
            mIsSelectedAll = true;
        } else {
            mIsSelectedAll = false;
        }
        /** @} */
    }

    public long[] getCheckedItemIds() {
        return convertSetToPrimitive(mCheckedIds);
    }
  //modify by wangmingyue for HQ01609514 begin
    public int[] getCheckedItemOptionss() {
        return convertSetToInt(mCheckedIdOptions);
    }
  //modify by wangmingyue for HQ01609514 end
    public void startSearch(String searchString) {
        // It could not meet the layout Request. So, we should not use the
        // default search function.

        // Normalize the empty query.
        if (TextUtils.isEmpty(searchString)) {
            searchString = null;
        }

        ContactEntryListAdapter adapter = (ContactEntryListAdapter) getAdapter();
        if (searchString == null) {
            if (adapter != null) {
                mSearchString = null;
                adapter.setQueryString(searchString);
                adapter.setSearchMode(false);
                reloadData();
            }
        } else if (!TextUtils.equals(mSearchString, searchString)) {
            mSearchString = searchString;
            if (adapter != null) {
                adapter.setQueryString(searchString);
                adapter.setSearchMode(true);
                reloadData();
            }
        }
    }

    public void markItemsAsSelectedForCheckedGroups(long[] ids) {
        ///M: ensure the pushed ids merged into mCheckedIds, would not exceed
        /// mAllowedMaxItems
        Set<Long> tempCheckedIds = new HashSet<Long>();
        tempCheckedIds.addAll(mCheckedIds);
        int  multiChoiceLimitCount = getMultiChoiceLimitCount();
        for (long id : ids) {
            if (tempCheckedIds.size() >= multiChoiceLimitCount) {
                String msg = getResources().getString(R.string.multichoice_contacts_limit, multiChoiceLimitCount);
                MtkToast.toast(getActivity().getApplicationContext(), msg);
                LogUtils.w(TAG, "[markItemsAsSelectedForCheckedGroups] mCheckedIds size:" + mCheckedIds.size() + " >= limit:"
                        + multiChoiceLimitCount);
                return;
            } else {
                tempCheckedIds.add(id);
                mPushedIds.add(id);
            }
        }
    }

    /**
     * @return mIsSelectedAll
     */
    public boolean isSelectedAll() {
        return mIsSelectedAll;
    }

    /**
     * @return mIsSelectedNone
     */
    public boolean isSelectedNone() {
        return mIsSelectedNone;
    }

    public abstract long getListItemDataId(int position);

    public void handleCursorItem(Cursor cursor) {
        return;
    }

////////////////////////////private function///////////////////////////////////////////
    /**
     * M: fix [ALPS00539605] It displays three contacts selectd,but displays four contacts in group view.
     */
    private Set<Long> mPushedIds = new HashSet<Long>();

    /**
     *
     * @param checkedItemsCount The count of items selected to show on the top view.
     */
    private void showSelectedCount(int checkedItemsCount) {
        TextView selectedItemsView = (TextView) getActivity().getActionBar().getCustomView()
                .findViewById(R.id.select_items);
        if (selectedItemsView == null) {
            LogUtils.e(TAG, "[showSelectedCount]Load view resource error!");
            return;
        }
        if (mSlectedItemsFormater == null) {
            LogUtils.e(TAG, "[showSelectedCount]mSlectedItemsFormater is null!");
            return;
        }
        if(checkedItemsCount <= 0) {
        	selectedItemsView.setText(getActivity().getString(R.string.menu_actionbar_not_selected_items));
        }
        else {
        	selectedItemsView.setText(String.format(mSlectedItemsFormater, String.valueOf(checkedItemsCount)));
        }
    }

    /**
     *
     * @param enable True to enable the OK button on the Top view,false to diable.
     */
    private void setOkButtonStatus(boolean enable) {
        Button optionView = (Button) getActivity().getActionBar().getCustomView().findViewById(R.id.menu_option);
        if (optionView != null) {
            if (enable) {
                optionView.setEnabled(true);
                optionView.setTextColor(getResources().getColor(R.color.background_primary));
            } else {
                optionView.setEnabled(false);
                optionView.setTextColor(Color.LTGRAY);
            }
        }
    }

    /**
     * Long array converters to primitive long array.</br>
     *
     * @param set a Long array.
     * @return a long array, or null if array is null or empty
     */
    private static long[] convertSetToPrimitive(Set<Long> set) {
        if (set == null) {
            return null;
        }

        final int arraySize = set.size();
        long[] result = new long[arraySize];

        int index = 0;
        for (Long id : set) {
            if (index >= arraySize) {
                break;
            }
            result[index++] = id.longValue();
        }

        return result;
    }
  //modify by wangmingyue for HQ01609514 begin
    private static int[] convertSetToInt(Set<Integer> set) {
        if (set == null) {
            return null;
        }

        final int arraySize = set.size();
        int[] result = new int[arraySize];

        int index = 0;
        for (int id : set) {
            if (index >= arraySize) {
                break;
            }
            result[index++] = id;
        }

        return result;
    }
  //modify by wangmingyue for HQ01609514 end
    private void updateCheckBoxState(boolean checked) {
        final int count = getListView().getAdapter().getCount();
        long dataId = -1;
        int multiChoiceLimitCount = getMultiChoiceLimitCount();
        for (int position = 0; position < count; ++position) {
            if (checked) {
                if (mCheckedIds.size() >= multiChoiceLimitCount) {
                    String msg = getResources().getString(R.string.multichoice_contacts_limit, multiChoiceLimitCount);
                    MtkToast.toast(getActivity().getApplicationContext(), msg);
                    LogUtils.w(TAG, "[updateCheckBoxState] mCheckedIds size:" + mCheckedIds.size() + " >= limit:"
                            + multiChoiceLimitCount);
                    getListView().setSelection(multiChoiceLimitCount - 1);
                    break;
                }
                if (!getListView().isItemChecked(position)) {
                    getListView().setItemChecked(position, checked);
                    dataId = getListItemDataId(position);
                    mCheckedIds.add(Long.valueOf(dataId));
                    mCheckedIdOptions.add(position);//modify by wangmingyue for HQ01609514
                    
                }
            } else {
                mCheckedIds.clear();
                mCheckedIdOptions.clear();//modify by wangmingyue for HQ01609514
                getListView().setItemChecked(position, checked);
            }
        }
        updateSelectedItemsView(mCheckedIds.size());
    }

    /**
     *
     * @return The max count of current multi choice
     */
    protected int getMultiChoiceLimitCount() {
		/*HQ_zhangjing 2015-10-09 modified for CQ HQ01435286 begin*/
        //return DEFAULT_MULTI_CHOICE_MAX_COUNT;
        return mSelectLimitNum;
		/*HQ_zhangjing 2015-10-09 modified for CQ HQ01435286 end*/
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        /** M: Bug Fix for ALPS01852524 @{ */
        if (GlobalEnv.isUsingTwoPanes()) {
            int originPadding = getListView().getPaddingLeft();
            int tmp = (int) getResources().getDimension(R.dimen.tmp_panding_value);
            Log.i(TAG, "[onConfigurationChanged] originPadding : " + originPadding + " | tmp : "
                    + tmp);
            if (tmp > 0) {
                int top = getListView().getPaddingTop();
                int bottom = getListView().getPaddingBottom();
                getListView().setPadding(tmp, top, tmp, bottom);
            }
        }
        /** @} */
    }
}
