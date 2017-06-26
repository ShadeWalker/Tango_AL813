package com.android.contacts;

import android.app.ListActivity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.text.TextUtils;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
//import com.mediatek.contacts.ContactsFeatureConstants.FeatureOption;


import static android.view.Window.PROGRESS_VISIBILITY_OFF; 
import static android.view.Window.PROGRESS_VISIBILITY_ON;


public class SDNListActivity extends ListActivity {
	public static final int MENU_CALL = 0;
	public static String SIMNUM = "SIMNUM";
	
	protected static final String TAG = "SDNList";
	protected static final boolean DBG = false;

	protected boolean mAirplaneMode = false; // used for SimContacts only for
	// now. mtk80909, 2010-10-28

	private static final String[] COLUMN_NAMES = new String[] { "name",
			"number", "emails", "additionalNumber", "groupIds" };

	protected static final int NAME_COLUMN = 0;
	protected static final int NUMBER_COLUMN = 1;
	protected static final int EMAIL_COLUMN = 2;
	protected static final int ADDITIONAL_NUMBER_COLUMN = 3;
	protected static final int GROUP_COLUMN = 4;

	private static final int[] VIEW_NAMES = new int[] { android.R.id.text1,
			android.R.id.text2 };

	protected static final int QUERY_TOKEN = 0;
	protected static final int INSERT_TOKEN = 1;
	protected static final int UPDATE_TOKEN = 2;
	protected static final int DELETE_TOKEN = 3;

	protected QueryHandler mQueryHandler;
	protected CursorAdapter mCursorAdapter;
	protected Cursor mCursor = null;

	private TextView mEmptyText;

	protected int mInitialSelection = 0;
	protected int mSimId;
	protected int mIndicate;
	//songting for the choosed sim card
	protected int mSimNum = 0;

	private final BroadcastReceiver mReceiver = new ADNListBroadcastReceiver();

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		Log.e("test", "SDNListActivity   enter");
//		getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		getWindow().requestFeature(Window.FEATURE_PROGRESS);
		setContentView(R.layout.sdn_list);
		getActionBar().setTitle(R.string.sdn_list);//modify by wangmingyue for HQ01550474
		
//		getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE,R.layout.customtitlebar);
		mEmptyText = (TextView) findViewById(android.R.id.empty);
		mQueryHandler = new QueryHandler(getContentResolver());

		IntentFilter intentFilter = new IntentFilter(
				Intent.ACTION_AIRPLANE_MODE_CHANGED);
		registerReceiver(mReceiver, intentFilter);
		log("-----------------------onCreate()");
		
//		registerForContextMenu(getListView());
		setItemResponse(getListView());
	}
	
	private void setItemResponse(ListView lv) {
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
					long arg3) {
				Cursor cursor = mCursorAdapter.getCursor();
				cursor.moveToPosition(arg2);
				int column = cursor.getColumnIndexOrThrow("number");
				String number = cursor.getString(column);
				Log.e("test", "the number is "+number);
//				ContactsUtils.initiateCall(SDNListActivity.this, number);
				Intent intent = new Intent("android.intent.action.CALL_PRIVILEGED", Uri.fromParts("tel", number.toString(), null));
		        startActivity(intent);
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		query();
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (mCursor != null) {
			mCursor.deactivate();
		}
	}
	
	//songting for create contextmenu
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_CALL:
                ContextMenu.ContextMenuInfo menuInfo = item.getMenuInfo();
                if (menuInfo instanceof AdapterView.AdapterContextMenuInfo) {
//                    int position = ((AdapterView.AdapterContextMenuInfo)menuInfo).position;
//                    log("------------------onContextItemSelected()---");
//
//					Cursor cursor = (Cursor) getListAdapter().getItem(position);
//					String phone = cursor.getString(NUMBER_COLUMN);				
//					ContactsUtils.dial(SDNListActivity.this,phone,ContactsUtils.DIAL_TYPE_VOICE,new ContactsUtils.OnDialCompleteListener() {
//            			public void onDialComplete(boolean dialed) {
//                		// TODO Auto-generated method stub
//                		if(dialed)
//                			SDNListActivity.this.finish();
//            			}
//        			});
//                    return true;
                }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        if (menuInfo instanceof AdapterView.AdapterContextMenuInfo) {
            AdapterView.AdapterContextMenuInfo itemInfo =
                    (AdapterView.AdapterContextMenuInfo) menuInfo;
            TextView textView = (TextView) itemInfo.targetView.findViewById(android.R.id.text1);
            if (textView != null) {	
            	CharSequence text = textView.getText();
            	if (TextUtils.isEmpty(text)) {
            		text = " ";
            	}
                menu.setHeaderTitle(text);
            }
            menu.add(0, MENU_CALL, 0, "Call");
        }
    }
	
	protected Uri resolveIntent() {
		Intent intent = getIntent();
		mSimNum = intent.getIntExtra(SIMNUM,0);
		Log.d("guo","mSimNum: "+mSimNum);
		if (intent.getData() == null) {
			//songting for
			log("-------not support gemini--------resolveIntent()");
			intent.setData(Uri.parse("content://icc/sdn"));
		}

		return intent.getData();
	}

	private void query() {
		Uri uri = resolveIntent();
		if (DBG)
			log("query: starting an async query");
		mQueryHandler.startQuery(QUERY_TOKEN, null, uri, COLUMN_NAMES, null,
				null, null);
		displayProgress(true);
	}

	private void reQuery() {
		query();
	}

	private void setAdapter() {
		// NOTE:
		// As it it written, the positioning code below is NOT working.
		// However, this current non-working state is in compliance with
		// the UI paradigm, so we can't really do much to change it.

		// In the future, if we wish to get this "positioning" correct,
		// we'll need to do the following:
		// 1. Change the layout to in the cursor adapter to:
		// android.R.layout.simple_list_item_checked
		// 2. replace the selection / focus code with:
		// getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		// getListView().setItemChecked(mInitialSelection, true);

		// Since the positioning is really only useful for the dialer's
		// SpecialCharSequence case (dialing '2#' to get to the 2nd
		// contact for instance), it doesn't make sense to mess with
		// the usability of the activity just for this case.

		// These artifacts include:
		// 1. UI artifacts (checkbox and highlight at the same time)
		// 2. Allowing the user to edit / create new SIM contacts when
		// the user is simply trying to retrieve a number into the d
		// dialer.

		if (mCursorAdapter == null) {
			mCursorAdapter = newAdapter();

			setListAdapter(mCursorAdapter);
		} else {
			mCursorAdapter.changeCursor(mCursor);
		}

		if (mInitialSelection >= 0
				&& mInitialSelection < mCursorAdapter.getCount()) {
			setSelection(mInitialSelection);
			getListView().setFocusableInTouchMode(true);
			boolean gotfocus = getListView().requestFocus();
		}
	}

	protected CursorAdapter newAdapter() {
		return new SimpleCursorAdapter(this, R.layout.sdn_list_item, mCursor,
				COLUMN_NAMES, VIEW_NAMES);
	}

	private void displayProgress(boolean flag) {
		if (DBG)
			log("displayProgress: " + flag);
		int LoadingResId;
		if (mSimId == PhoneConstants.SIM_ID_1 || mSimId == PhoneConstants.SIM_ID_2) {
			//songting
			log("displayProgress1&2: " + flag);
			
			LoadingResId = R.string.simContacts_emptyLoading_ex;
			CallerInfo info = CallerInfo.getCallerInfo(this, null,mSimId);
			String text = "";
			if (info != null && flag) {
				text = this.getResources().getString(LoadingResId,
						info.name);
				mEmptyText.setText(text);
			} else {
				mEmptyText.setText(R.string.simContacts_empty);
			}
		} else {
		//songting
		log("displayProgress: " + flag);
		
			LoadingResId = R.string.simContacts_emptyLoading;
			mEmptyText
					.setText(flag ? LoadingResId : R.string.simContacts_empty);
		}
		// mEmptyText.setText(flag ? LoadingResId: R.string.simContacts_empty);
		getWindow().setFeatureInt(Window.FEATURE_INDETERMINATE_PROGRESS,
				flag ? PROGRESS_VISIBILITY_ON : PROGRESS_VISIBILITY_OFF);
	}

	private class QueryHandler extends AsyncQueryHandler {
		public QueryHandler(ContentResolver cr) {
			super(cr);
		}

		@Override
		protected void onQueryComplete(int token, Object cookie, Cursor c) {
			if (DBG)
				log("onQueryComplete: cursor.count=" + c.getCount());
			mCursor = c;
			if (mAirplaneMode)
				mCursor = null;
			setAdapter();
			displayProgress(false);
		}

		@Override
		protected void onInsertComplete(int token, Object cookie, Uri uri) {
			if (DBG)
				log("onInsertComplete: requery");
			reQuery();
		}

		@Override
		protected void onUpdateComplete(int token, Object cookie, int result) {
			if (DBG)
				log("onUpdateComplete: requery");
			reQuery();
		}

		@Override
		protected void onDeleteComplete(int token, Object cookie, int result) {
			if (DBG)
				log("onDeleteComplete: requery");
			reQuery();
		}
	}

	protected void log(String msg) {
		Log.d(TAG, "[SDNList] " + msg);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(mReceiver);
	}

	private class ADNListBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
				finish();
			}
		}
	}
}
