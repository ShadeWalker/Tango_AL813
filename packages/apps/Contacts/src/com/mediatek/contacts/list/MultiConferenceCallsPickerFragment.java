package com.mediatek.contacts.list;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListItemView;
import com.mediatek.contacts.util.ContactsIntent;
import com.mediatek.telecom.TelecomManagerEx;

public class MultiConferenceCallsPickerFragment extends DataKindPickerBaseFragment {

    private static final String TAG = "MultiConferenceCallsPickerFragment";
    public static final String EXTRA_VOLTE_CONF_CALL_NUMBERS = "com.mediatek.volte.ConfCallNumbers";
    public static final String EXTRA_VOLTE_IS_CONF_CALL = "com.mediatek.volte.IsConfCall";
    public static final String FRAGMENT_ARGS = "intent";

    // for Reference call max number limited
    private int mReferenceCallMaxNumber = ContactsIntent.CONFERENCE_CALL_LIMITES;
    private ArrayList<String> mCheckedDatas = new ArrayList<String>();
    private Intent mIntent;
    private String mCallingActivity;

    public void setRefenceCallMaxNumber(int num) {
        mReferenceCallMaxNumber = num;
    }

    @Override
    protected ContactEntryListAdapter createListAdapter() {
        MultiConferenceCallsPickerAdapter adapter = new MultiConferenceCallsPickerAdapter(getActivity(),
                getListView());
        adapter.setFilter(ContactListFilter
                .createFilterWithType(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS));

        mIntent = getArguments().getParcelable(FRAGMENT_ARGS);
        mReferenceCallMaxNumber = (int) mIntent.getIntExtra(ContactsIntent.CONFERENCE_CALL_LIMIT_NUMBER, mReferenceCallMaxNumber);
        mCallingActivity = (String) mIntent.getStringExtra(ContactsIntent.CONFERENCE_SENDER);
        return adapter;
    }

    /**
    *
    * @return The max count of current multi choice
    */
   protected int getMultiChoiceLimitCount() {
       return mReferenceCallMaxNumber;
   }

   public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
       super.onItemClick(parent, view, position, id);
       TextView dataView = ((ContactListItemView) view).getDataView();
       String data = dataView.getText().toString();
        if (getListView().isItemChecked(position)) {
           if (mCheckedDatas.size() < mReferenceCallMaxNumber) {
               Log.d(TAG, "DataView is" + data);
               mCheckedDatas.add(data);
           }
        } else {
            mCheckedDatas.remove(data);
        }
   }

    @Override
    public void onOptionAction() {
        final long[] idArray = getCheckedItemIds();
        if (idArray == null || mCheckedDatas.size() == 0) {
            return;
        }

        final Activity activity = getActivity();
        activity.getCallingActivity();
        //For contacts sender
        if (ContactsIntent.CONFERENCE_CONTACTS.equals(mCallingActivity)) {
            showCheckedDatas();
            Intent confCallIntent = CallUtil.getCallIntent(mCheckedDatas.get(0));
            confCallIntent.putExtra(TelecomManagerEx.EXTRA_VOLTE_CONF_CALL_DIAL, true);
            confCallIntent.putStringArrayListExtra(TelecomManagerEx.EXTRA_VOLTE_CONF_CALL_NUMBERS, mCheckedDatas);
            activity.startActivity(confCallIntent);
        } else {
            //For Dialer sender.
            final Intent retIntent = new Intent();
            if (null == retIntent) {
                activity.setResult(Activity.RESULT_CANCELED, null);
                activity.finish();
                return;
            }
            showCheckedIds(idArray);
            retIntent.putExtra(ContactsIntent.CONFERENCE_CALL_RESULT_INTENT_EXTRANAME, idArray);
            activity.setResult(Activity.RESULT_OK, retIntent);
        }
        activity.finish();
    }

    @Override
    public void onClearSelect() {
        super.onClearSelect();
        updateCheckBoxState(false);
    }

    @Override
    public void onSelectAll() {
        super.onSelectAll();
        updateCheckBoxState(true);
    }


    private void updateCheckBoxState(boolean checked) {
        int count = getListView().getAdapter().getCount();
        String data = "";
        count = (count > mReferenceCallMaxNumber ? mReferenceCallMaxNumber : count);
        for (int position = 0; position < count; ++position) {
            if (checked) {
                if (getListView().isItemChecked(position)) {
                    ContactListItemView view =
                        (ContactListItemView) getListView().getChildAt(position);
                    TextView dataView = view.getDataView();
                    data = dataView.getText().toString();
                    Log.d(TAG, "DataView is" + data);
                    mCheckedDatas.add(data);
                }
            } else {
                mCheckedDatas.clear();
                break;
            }
        }
    }


    private void showCheckedDatas() {
        for (int i = 0; i < mCheckedDatas.size(); i++) {
            Log.d(TAG, "mCheckedDatas[" + i + "]" + mCheckedDatas.get(i));
        }
    }

    private void showCheckedIds(long[] idArray) {
        for (int i = 0; i < idArray.length; i++) {
            Log.d(TAG, "idArray[" + i + "]" + idArray[i]);
        }
    }

}
