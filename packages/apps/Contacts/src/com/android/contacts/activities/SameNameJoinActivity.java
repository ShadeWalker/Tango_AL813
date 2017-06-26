package com.android.contacts.activities;

import java.util.ArrayList;
import java.util.HashSet;





import com.android.contacts.R;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.AsyncQueryHandler;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.OperationApplicationException;
import android.database.Cursor;
//import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.ContactsActivity;
import com.android.contacts.ContactsApplication;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListFilterController;
import com.android.contacts.model.SameNameList;

@TargetApi(Build.VERSION_CODES.ECLAIR)
@SuppressLint("InlinedApi")
/**
 * 合并联系人aty
 * @author niubi tang
 *
 */
public class SameNameJoinActivity extends ContactsActivity implements
		View.OnClickListener {
	private static final String TAG = "SameNameJoinActivity";
	public static final String START_FROM_MENU = "start_from_menu";

	private static final int ST_IDLE = 0;
	private static final int ST_CHECKING = 1;
	private static final int ST_SAVING = 2;
	private static final int ST_DONE = 3;
	private static final int ST_CANCELED = 4;

	private int mStatus = ST_IDLE;
	private int TOKEN_QUERY = 10;
	private boolean mCanceled = false;
//	private QueryHandler mQueryHandler;

	private ProgressBar progress;
	private ProgressBar progressSingle;
	private TextView stTotal;
	private TextView stCheck;
	private TextView stJoin;
	private ImageView icCheck;
	private ImageView icJoin;
	private Button actionOk;
	private Button actionCancel;

	private ArrayList<SameNameList> exceptionIds = new ArrayList<SameNameList>();
	private CommitJoinThread mCommitThread;
	private PowerManager pm;
	private PowerManager.WakeLock mWakeLock;

	private static final int MSG_SAVE_PROGRESS = 100;
	private static final int MSG_SAVE_FINISHED = 101;
	private static final int MSG_SAVE_CANCELED = 102;
	QueryContactsTask queryContactsTask;
	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Bundle args = (Bundle) msg.obj;
			switch (msg.what) {
			case MSG_SAVE_PROGRESS: {
				String displayName = args.getString(Contacts.DISPLAY_NAME);
				stTotal.setText(getSpannableString(
						R.string.join_status_joining, displayName));

				int current = args.getInt("progress");
				int total = exceptionIds.size();
				Log.i(TAG, "current " + current);
				stJoin.setText(getString(R.string.join_status_join_progress,
						current, total));
				progress.setProgress(current);
				int groupNum = args.getInt("group_number");
				stCheck.setText(getString(R.string.join_status_single_joining,
						displayName, groupNum));
				break;
			}
			case MSG_SAVE_CANCELED: {
				setStatus(ST_CANCELED);
				break;
			}
			case MSG_SAVE_FINISHED: {
				setStatus(ST_DONE);
				break;
			}
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedState) {
		int themeId = getResources().getIdentifier(
				"androidhwext:style/Theme.Emui.WithActionBar", null, null);
		if (themeId > 0) {
			setTheme(themeId);
		}

		super.onCreate(savedState);

		setContentView(R.layout.same_name_join_activity);
		ActionBar actionBar = getActionBar();
		if (actionBar != null) {
			actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP
					| ActionBar.DISPLAY_SHOW_TITLE
					| ActionBar.DISPLAY_SHOW_HOME);
			actionBar.setTitle(R.string.join_same_name);
		}

		actionOk = (Button) findViewById(R.id.btn_ok);
		actionCancel = (Button) findViewById(R.id.btn_cancel);
		actionCancel.setOnClickListener(this);
		if (getIntent().getBooleanExtra(START_FROM_MENU, false)) {
			actionOk.setOnClickListener(this);
		} else {
			actionOk.setOnClickListener(this);
		}

		progress = (ProgressBar) findViewById(R.id.progress);
		progressSingle = (ProgressBar) findViewById(R.id.progress_single);
		stTotal = (TextView) findViewById(R.id.total_status);
		stCheck = (TextView) findViewById(R.id.check_status);
		stJoin = (TextView) findViewById(R.id.join_status);
		icCheck = (ImageView) findViewById(R.id.check_icon);
		icJoin = (ImageView) findViewById(R.id.join_icon);
		setStatus(ST_IDLE);
		queryContactsTask=new QueryContactsTask();
//		mQueryHandler = new QueryHandler(getContentResolver());
		//add by guojianhui for HQ01640024
		pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
				| PowerManager.ON_AFTER_RELEASE, TAG);
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onStop() {
		Log.i(TAG, "onStop");
		cancelJoining(); //remove by guojianhui for HQ01640024
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		Log.i(TAG, "onDestroy");
		//cancelJoining(); //add by guojianhui for HQ01640024
		ContactsApplication.closeContactDb();//add by tang for  sqlite leak
		super.onDestroy();
	}

	private SpannableString getSpannableString(int resTitle, String displayName) {
		String title = getString(resTitle);
		SpannableString ssb = new SpannableString(title + displayName);
		int start = title.length();
		int end = ssb.length();
		TextAppearanceSpan tas1 = new TextAppearanceSpan(this,
				R.style.SameNameMsgStyleA);
		TextAppearanceSpan tas2 = new TextAppearanceSpan(this,
				R.style.SameNameMsgStyleB);
		ssb.setSpan(tas1, 0, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		ssb.setSpan(tas2, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		return ssb;
	}

	private void updateProgressStatus() {
		int textTotal = R.string.join_status_unstarted;
		int textCheck = R.string.join_status_unstarted;
		int textJoin = R.string.join_status_unstarted;
		int check = R.drawable.status_unstarted;
		int join = R.drawable.status_unstarted;
		switch (mStatus) {
		case ST_IDLE: {
			progress.setVisibility(View.INVISIBLE);
			progressSingle.setVisibility(View.INVISIBLE);
			break;
		}
		case ST_CHECKING: {
			textCheck = R.string.join_status_waiting;
			textJoin = R.string.join_status_checking;
			textTotal = textJoin;
			break;
		}
		case ST_SAVING: {
			progress.setVisibility(View.VISIBLE);
			progressSingle.setVisibility(View.VISIBLE);
			textCheck = R.string.join_status_join_start;
			textJoin = R.string.join_status_join_start;
			textTotal = textJoin;
			check = R.drawable.status_ongoing;
			join = R.drawable.status_ongoing;
			break;
		}
		case ST_DONE: {
			progress.setVisibility(View.INVISIBLE);
			progressSingle.setVisibility(View.INVISIBLE);
			textCheck = R.string.menu_done;
			textJoin = R.string.menu_done;
			textTotal = textJoin;
			check = R.drawable.status_done;
			join = R.drawable.status_done;
			break;
		}
		case ST_CANCELED: {
			progress.setVisibility(View.INVISIBLE);
			progressSingle.setVisibility(View.INVISIBLE);
			textCheck = R.string.menu_done;
			textJoin = R.string.join_status_canceled;
			textTotal = textJoin;
			check = R.drawable.status_done;
			join = R.drawable.status_canceled;
			break;
		}
		}
		stCheck.setText(textCheck);
		stJoin.setText(textJoin);
		stTotal.setText(getSpannableString(textTotal, ""));
		icCheck.setImageResource(check);
		icJoin.setImageResource(join);
	}

	@Override
	public void onBackPressed() {
		if (mStatus == ST_IDLE || mStatus == ST_DONE || mStatus == ST_CANCELED) {
			super.onBackPressed();
		} else {
			showConfirmDialog(true);
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btn_ok: {
			if (mStatus == ST_IDLE) {
				startChecking();
			} else {
				finish();
			}
			break;
		}
		case R.id.btn_cancel: {
			showConfirmDialog(false);
			break;
		}
		}
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home: {
			finish();
			return true;
		}

		default:
			throw new IllegalArgumentException();
		}
	}

	private void showConfirmDialog(boolean backPressed) {
		if (mStatus == ST_IDLE || mStatus == ST_DONE || mStatus == ST_CANCELED)
			return;

		final boolean backPress = backPressed;
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.cancel);
		builder.setMessage(R.string.join_cancel_confirm_msg);
		builder.setPositiveButton(R.string.ok,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if (which == DialogInterface.BUTTON_POSITIVE) {
							cancelJoining();
							dialog.dismiss();
							Activity activity = SameNameJoinActivity.this;
							if (backPress && !activity.isFinishing()) {
								finish();
							}
						}
					}
				});
		builder.setNegativeButton(R.string.cancel, null);
		builder.create().show();
	}

	private void setStatus(int status) {
		mStatus = status;
		updateProgressStatus();
		Log.i(TAG, "status " + status);
		switch (mStatus) {
		case ST_CHECKING:
			actionCancel.setEnabled(false);
			actionCancel.setClickable(false);
		case ST_SAVING:
			actionCancel.setEnabled(true);
			actionCancel.setClickable(true);
			actionOk.setEnabled(false);
			actionOk.setClickable(false);
			break;
		case ST_IDLE:
			actionCancel.setEnabled(false);
			actionCancel.setClickable(false);
			actionOk.setText(R.string.join_action_start);
			actionOk.setEnabled(true);
			actionOk.setClickable(true);
			break;
		case ST_CANCELED:
		case ST_DONE: {
			actionCancel.setEnabled(false);
			actionCancel.setClickable(false);
			actionOk.setText(R.string.menu_done);
			actionOk.setEnabled(true);
			actionOk.setClickable(true);
			if (mWakeLock != null && mWakeLock.isHeld()) {
				Log.w(TAG, "WakeLock is being held.");
				mWakeLock.release();
				mWakeLock = null;  //add by guojianhui for HQ01640024
			}
			break;
		}
		}
	}

	@SuppressLint("InlinedApi")
	private void startChecking() {
//		if (mQueryHandler == null) {
//			mQueryHandler = new QueryHandler(getContentResolver());
//		}
		if (exceptionIds == null) {
			exceptionIds = new ArrayList<SameNameList>();
		} else {
			exceptionIds.clear();
		}
		//add by guojianhui for HQ01640024
		if(mWakeLock == null) {
			pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
					| PowerManager.ON_AFTER_RELEASE, TAG);
		}
		mWakeLock.acquire();
		
		mCanceled = false;
		setStatus(ST_CHECKING);
//		final String[] PROJ = { Contacts._ID, Contacts.DISPLAY_NAME, };
//		mQueryHandler.startQuery(TOKEN_QUERY, null, Contacts.CONTENT_URI, PROJ,
//				RawContacts.ACCOUNT_TYPE + "!=?" + "AND"
//						+ RawContacts.ACCOUNT_TYPE + "!=?", new String[] {
//						AccountType.ACCOUNT_TYPE_SIM,
//						AccountType.ACCOUNT_TYPE_USIM }, Contacts.DISPLAY_NAME);
		//add by guojianhui for HQ01640024
		if(queryContactsTask == null) {	
			queryContactsTask=new QueryContactsTask();
		}
		queryContactsTask.execute();
	}

	private void cancelJoining() {
		mCanceled = true;
//		if (mQueryHandler != null) {
//			mQueryHandler.cancelOperation(TOKEN_QUERY);
//			mQueryHandler = null;
//		}
		if(queryContactsTask!=null){
			queryContactsTask.cancel(true);
			queryContactsTask=null;
		}
		if (mCommitThread != null) {
			mCommitThread.canceled = mCanceled;
			mCommitThread = null;
		}
		if (mWakeLock != null && mWakeLock.isHeld()) {
			Log.w(TAG, "WakeLock is being held.");
			mWakeLock.release();
			mWakeLock = null; //add by guojianhui for HQ01640024
		}
	}

	/**
	 * 开始合并操作
	 * 
	 * @param cursor
	 */
	private void startJoin(Cursor cursor) {
		Log.i(TAG, "startJoin " + cursor.getCount());
		try {
			SameNameList nameList = null;
			cursor.moveToPosition(-1);
			while (cursor.moveToNext()) {
				String displayName = cursor.getString(cursor
						.getColumnIndex(Contacts.DISPLAY_NAME));
				long id = cursor.getLong(cursor.getColumnIndex(Contacts._ID));
				if (nameList != null
						&& TextUtils.equals(displayName,
								nameList.getDisplayName())) {
					nameList.addContact(id);
				} else {
					if (nameList != null && nameList.isValid()) {
						exceptionIds.add(nameList);
					}
					nameList = new SameNameList(displayName);
					nameList.addContact(id);
				}
			}
			if (nameList != null && nameList.isValid()) {
				exceptionIds.add(nameList);
			}
		} finally {
			if (cursor != null)
				cursor.close();
		}
		if (exceptionIds.size() > 0) {
			Log.i(TAG, "exceptionIds " + exceptionIds.size());
			setStatus(ST_SAVING);
			progress.setMax(exceptionIds.size());
			mCommitThread = new CommitJoinThread();
			mCommitThread.start();
		} else {
			setStatus(ST_DONE);
		}
	}

	private final class QueryHandler extends AsyncQueryHandler {
		public QueryHandler(ContentResolver resolver) {
			super(resolver);
		}

		@Override
		protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
			int token1 = token;
			if (cursor == null || cursor.getCount() == 0) {
				Toast.makeText(SameNameJoinActivity.this, R.string.no_contact_to_display,
						Toast.LENGTH_SHORT).show();
				setStatus(ST_DONE);

			} else {
				startJoin(cursor);
			}
		}

	}

	private final class CommitJoinThread extends Thread {
		public boolean canceled;
		private Context mContext;

		public CommitJoinThread() {
			canceled = false;
			mContext = getApplicationContext();
		}

		public void run() {
			int total = exceptionIds.size();
			int current = 0;
			Log.i(TAG, "start merge " + total);
			for (SameNameList nameList : exceptionIds) {
				if (!canceled) {
					String displayName = nameList.getDisplayName();
					Message msg = Message.obtain(mHandler);
					msg.what = MSG_SAVE_PROGRESS;
					Bundle args = new Bundle();
					args.putString(Contacts.DISPLAY_NAME, displayName);
					args.putInt("progress", current++);
					args.putInt("group_number", nameList.getContactIds().size());
					msg.obj = args;
					mHandler.sendMessage(msg);
					actuallyJoinOneGroup(mContext.getContentResolver(),
							nameList);
				} else {
					Log.i(TAG, "canceled on " + current);
					mHandler.sendEmptyMessage(MSG_SAVE_CANCELED);
					break;
				}
			}
			if (!canceled) {
				mHandler.sendEmptyMessage(MSG_SAVE_FINISHED);
				Log.i(TAG, "merge done");
			}
		}
	}

	private void actuallyJoinOneGroup(ContentResolver cr, SameNameList nameList) {
		ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
		HashSet<Long> contactIds = nameList.getContactIds();
		StringBuilder sb = new StringBuilder();
		for (long id : contactIds) {
			sb.append(String.valueOf(id) + ",");
		}
		String ids = sb.toString();
        /* change from RawContacts.CONTACT_ID to RawContacts._ID by donghongjing */
		String SELECT = RawContacts._ID + " IN ("
				+ ids.substring(0, ids.length() - 1) + ")";
		Cursor cursor = null;
		try {
			cursor = cr.query(RawContacts.CONTENT_URI,
					new String[] { RawContacts._ID }, SELECT, null, null);
			final int count = cursor.getCount();
			Log.i(TAG, "total raw contacts " + count);
			if (count > 1) {
				long[] rawContactIds = new long[count];
				int i = 0;
				while (!mCanceled && cursor.moveToNext()) {
					rawContactIds[i++] = cursor.getLong(0);
				}
				for (i = 0; !mCanceled && i < rawContactIds.length; i++) {
					for (int j = 0; !mCanceled && j < rawContactIds.length; j++) {
						if (i != j) {
							buildJoinContactDiff(ops, rawContactIds[i],
									rawContactIds[j]);
						}
						if (ops.size() == 50) {
							pushOpsIntoDb(cr, ops);
							ops.clear();
						}
					}
				}
			}
		} finally {
			if (cursor != null)
				cursor.close();
		}
		if (ops.size() > 0) {
			pushOpsIntoDb(cr, ops);
			ops.clear();
		}
	}

	private void pushOpsIntoDb(ContentResolver cr,
			ArrayList<ContentProviderOperation> ops) {
		try {
			Log.i(TAG, "push into db " + ops.size());
			cr.applyBatch(ContactsContract.AUTHORITY, ops);
		} catch (OperationApplicationException e) {
			Log.w(TAG, "OperationApplicationException " + e.getMessage());
		} catch (RemoteException e) {
			Log.w(TAG, "RemoteException " + e.getMessage());
		}
	}

	private void buildJoinContactDiff(
			ArrayList<ContentProviderOperation> operations, long rawContactId1,
			long rawContactId2) {
		ContentProviderOperation.Builder builder = ContentProviderOperation
				.newUpdate(AggregationExceptions.CONTENT_URI);
		builder.withValue(AggregationExceptions.TYPE,
				AggregationExceptions.TYPE_KEEP_TOGETHER);
		builder.withValue(AggregationExceptions.RAW_CONTACT_ID1, rawContactId1);
		builder.withValue(AggregationExceptions.RAW_CONTACT_ID2, rawContactId2);
		operations.add(builder.build());
	}

	private class QueryContactsTask extends AsyncTask<Void, Cursor, Cursor> {

		@Override
		protected Cursor doInBackground(Void... params) {
			// TODO Auto-generated method stub
//			SQLiteDatabase db = ContactsApplication.getContactsDb();
            /* add deleted!=1 to qwery by donghongjing */
			ContactListFilter currentFilter = ContactListFilterController
					.getInstance(getApplicationContext()).getFilter();
			String currentFilterAccountName=currentFilter.accountName;
			Cursor cursor;
			if(currentFilterAccountName!=null){
//				cursor = db
//						.rawQuery(
//								"select _id,display_name from view_raw_contacts where ((account_type!=? and account_type!=?) or account_type is null) and (deleted!=?) and account_name =? and is_sdn_contact!=-2 order by  display_name ",
//								new String[]{"USIM Account","SIM Account","1",currentFilterAccountName});
				
				
				cursor = getContentResolver()
						.query(Uri.parse("content://com.android.contacts/raw_contacts"),
								new String[] { "_id", "display_name" },
								"((account_type!=? and account_type!=?) or account_type is null) and (deleted!=?) and account_name =? and is_sdn_contact!=-2  ",
								new String[] { "USIM Account", "SIM Account",
										"1", currentFilterAccountName },
								"display_name");
				
			}else {
//				cursor = db
//						.rawQuery(
//								"select _id,display_name from view_raw_contacts where ((account_type!=? and account_type!=?) or account_type is null) and (deleted!=?) and is_sdn_contact!=-2 order by  display_name ",
//								new String[]{"USIM Account","SIM Account","1"});
				
				
				cursor = getContentResolver()
						.query(Uri.parse("content://com.android.contacts/raw_contacts"),
								new String[] { "_id", "display_name" },
								"((account_type!=? and account_type!=?) or account_type is null) and (deleted!=?) and is_sdn_contact!=-2 ",
								new String[]{"USIM Account","SIM Account","1"},"display_name");
				
			}
			return cursor;
		}

		@Override
		protected void onPostExecute(Cursor cursor) {
			// TODO Auto-generated method stub
			super.onPostExecute(cursor);
			if (cursor == null || cursor.getCount() == 0) {
				Toast.makeText(SameNameJoinActivity.this, R.string.no_contact_to_display,
						Toast.LENGTH_SHORT).show();
				setStatus(ST_DONE);

			} else {
				startJoin(cursor);
			}
		}

	}

}
