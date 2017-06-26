
package com.mediatek.contacts.list;

import android.app.Activity;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.R;
import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.util.AccountFilterUtil;

import com.mediatek.contacts.util.LogUtils;
/*HQ_zhangjing modified for sysmanager for add black and white name form contact begin*/
import android.util.Log;
import java.util.ArrayList;
/*HQ_zhangjing modified for sysmanager for add black and white name form contact end*/


public abstract class DataKindPickerBaseFragment extends AbstractPickerFragment {

    private static final String TAG = DataKindPickerBaseFragment.class.getSimpleName();

    private static final String RESULTINTENTEXTRANAME = "com.mediatek.contacts.list.pickdataresult";

    // Show account filter settings
    private View mAccountFilterHeader;

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);
        mAccountFilterHeader = getView().findViewById(R.id.account_filter_header_container);
        mAccountFilterHeader.setClickable(false);
        mAccountFilterHeader.setVisibility(View.GONE);
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();
        ContactEntryListAdapter adapter = getAdapter();
        if (adapter == null) {
            return;
        }

        // Disable pinned header. It doesn't work with this fragment.
        adapter.setPinnedPartitionHeadersEnabled(false);
    }

    @Override
    public void onOptionAction() {
    	//modify by wangmingyue for HQ01609514 begin
        final long[] idArray = getCheckedItemIds();
        final int[] optionsArray = getCheckedItemOptionss();
        if (idArray == null) {
            return;
        }

        for (long item : idArray) {
            LogUtils.d(TAG, "result array: item " + item);
        }

        final Activity activity = getActivity();
        final Intent retIntent = new Intent();
        if (null == retIntent) {
            activity.setResult(Activity.RESULT_CANCELED, null);
            activity.finish();
            return;
        }
		custIntent(retIntent, idArray,optionsArray);
		retIntent.putExtra(RESULTINTENTEXTRANAME, idArray);
        activity.setResult(Activity.RESULT_OK, retIntent);
        activity.finish();
      //modify by wangmingyue for HQ01609514 end
    }
    
    protected void custIntent(Intent retIntent, long[] idArray,int[] options){
		/*HQ_zhangjing modified for sysmanager for add black and white name form contact begin*/
		ArrayList<Integer> selItemIds = new ArrayList<Integer>();
		selItemIds.clear();
		for( int i = 0;i< idArray.length;i++){
			Long lon=new Long( idArray[i]);
			int tepId = lon.intValue();			
			selItemIds.add( tepId);
			//selItemIds.add( Integer.valueOf( Integer.parseInt(idArray[i].toString()) ) );			
		}
		Log.d(TAG,"zhangjing :sysmanager DataKindPickerBaseFragment");
		retIntent.putExtra("SelItemData_KeyValue", selItemIds);
		/*HQ_zhangjing modified for sysmanager for add black and white name form contact end*/		
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        final boolean shouldShowHeader = AccountFilterUtil.updateAccountFilterTitleForPeople(
                mAccountFilterHeader, ContactListFilter
                        .createFilterWithType(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS), true);
        super.onLoadFinished(loader, data);
    }

    @Override
    public long getListItemDataId(int position) {
        final DataKindPickerBaseAdapter adapter = (DataKindPickerBaseAdapter) getAdapter();
        if (adapter != null) {
            return adapter.getDataId(position);
        }
        return -1;
    }
}
