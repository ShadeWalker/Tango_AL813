package com.mediatek.contacts.util;

import java.util.ArrayList;
import java.util.HashSet;

import android.content.AsyncQueryHandler;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.contacts.activities.SameNameJoinActivity;
import com.android.contacts.model.SameNameList;
import com.android.contacts.R;

public class JoinContactsutils {
	private ArrayList<SameNameList> exceptionIds = new ArrayList<SameNameList>();
	private CommitJoinThread mCommitThread;
	private PowerManager.WakeLock mWakeLock;
	private QueryHandler mQueryHandler;
	private Context context;

	private int mStatus = ST_IDLE;

	private static final int ST_IDLE = 0;
	private static final int ST_CHECKING = 1;
	private static final int ST_SAVING = 2;
	private static final int ST_DONE = 3;
	private static final int ST_CANCELED = 4;

	private static final int MSG_SAVE_PROGRESS = 100;
	private static final int MSG_SAVE_FINISHED = 101;
	private static final int MSG_SAVE_CANCELED = 102;
	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Bundle args = (Bundle) msg.obj;
			switch (msg.what) {
			case MSG_SAVE_PROGRESS: {
				/*
				 * String displayName = args.getString(Contacts.DISPLAY_NAME);
				 * stTotal.setText(getSpannableString(
				 * R.string.join_status_joining, displayName));
				 * 
				 * int current = args.getInt("progress"); int total =
				 * exceptionIds.size(); Log.i(TAG, "current " + current);
				 * stJoin.setText(getString(R.string.join_status_join_progress,
				 * current, total)); progress.setProgress(current); int groupNum
				 * = args.getInt("group_number");
				 * stCheck.setText(getString(R.string
				 * .join_status_single_joining, displayName, groupNum));
				 */
				break;
			}
			case MSG_SAVE_CANCELED: {
				// setStatus(ST_CANCELED);
				break;
			}
			case MSG_SAVE_FINISHED: {
				// setStatus(ST_DONE);
				break;
			}
			}
		}
	};

	public JoinContactsutils(Context context) {
		// TODO Auto-generated constructor stub
		super();
		this.context = context;
		mQueryHandler = new QueryHandler(context.getContentResolver());

	}

	private static final String TAG = "JoinContactsutils";

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
			// progress.setMax(exceptionIds.size());
			mCommitThread = new CommitJoinThread();
			mCommitThread.start();
		} else {
			setStatus(ST_DONE);
		}
	}

	private void setStatus(int status) {
		mStatus = status;
		// updateProgressStatus();
		Log.i(TAG, "status " + status);
		switch (mStatus) {
		case ST_CHECKING:
			/*
			 * actionCancel.setEnabled(false); actionCancel.setClickable(false);
			 */
		case ST_SAVING:
			/*
			 * actionCancel.setEnabled(true); actionCancel.setClickable(true);
			 * actionOk.setEnabled(false); actionOk.setClickable(false);
			 */
			break;
		case ST_IDLE:
			/*
			 * actionCancel.setEnabled(false); actionCancel.setClickable(false);
			 * actionOk.setText(R.string.join_action_start);
			 * actionOk.setEnabled(true); actionOk.setClickable(true);
			 */
			break;
		case ST_CANCELED:
		case ST_DONE: {
			/*
			 * actionCancel.setEnabled(false); actionCancel.setClickable(false);
			 * actionOk.setText(R.string.menu_done); actionOk.setEnabled(true);
			 * actionOk.setClickable(true);
			 */
			if (mWakeLock != null && mWakeLock.isHeld()) {
				Log.w(TAG, "WakeLock is being held.");
				mWakeLock.release();
			}
			break;
		}
		}
	}

	private void startChecking() {
		if (mQueryHandler == null) {
			mQueryHandler = new QueryHandler(context.getContentResolver());
		}
		if (exceptionIds == null) {
			exceptionIds = new ArrayList<SameNameList>();
		} else {
			exceptionIds.clear();
		}
		PowerManager pm = (PowerManager) context
				.getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
				| PowerManager.ON_AFTER_RELEASE, TAG);
		mWakeLock.acquire();

		// mCanceled = false;
		// setStatus(ST_CHECKING);
		final String[] PROJ = { Contacts._ID, Contacts.DISPLAY_NAME, };
		/**
		 * token A token passed into onQueryComplete to identify the query.
		 * cookie An object that gets passed into onQueryComplete uri The URI,
		 * using the content:// scheme, for the content to retrieve. projection
		 * A list of which columns to return. Passing null will return all
		 * columns, which is discouraged to prevent reading data from storage
		 * that isn't going to be used. selection A filter declaring which rows
		 * to return, formatted as an SQL WHERE clause (excluding the WHERE
		 * itself). Passing null will return all rows for the given URI.
		 * selectionArgs You may include ?s in selection, which will be replaced
		 * by the values from selectionArgs, in the order that they appear in
		 * the selection. The values will be bound as Strings. orderBy How to
		 * order the rows, formatted as an SQL ORDER BY clause (excluding the
		 * ORDER BY itself). Passing null will use the default sort order, which
		 * may be unordered.
		 */
		// mQueryHandler
		// .startQuery(
		// TOKEN_QUERY,
		// null,
		// Contacts.CONTENT_URI,
		// PROJ,
		// RawContacts.ACCOUNT_TYPE + "!=?",
		// new String[] {
		// com.mediatek.contacts.model.SimAccountType.ACCOUNT_TYPE,},
		// Contacts.DISPLAY_NAME);
		mQueryHandler.startQuery(0, null, Contacts.CONTENT_URI, PROJ, null,
				null, null);
	}

	private final class CommitJoinThread extends Thread {
		public boolean canceled;
		private Context mContext;

		public CommitJoinThread() {
			canceled = false;
			mContext = context.getApplicationContext();
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
		String SELECT = RawContacts.CONTACT_ID + " IN ("
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
				while (cursor.moveToNext()) {
					rawContactIds[i++] = cursor.getLong(0);
				}
				for (i = 0; i < rawContactIds.length; i++) {
					for (int j = 0; j < rawContactIds.length; j++) {
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

	private final class QueryHandler extends AsyncQueryHandler {
		public QueryHandler(ContentResolver resolver) {
			super(resolver);
		}

		@Override
		protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
			int token1 = token;
			if (cursor == null || cursor.getCount() == 0) {
				//Toast.makeText(context, "查询不到联系人！", Toast.LENGTH_SHORT).show();
				Toast.makeText(context, context.getResources().getString(R.string.no_contacts_query), Toast.LENGTH_SHORT).show();
				setStatus(ST_DONE);

			} else {
				startJoin(cursor);
			}
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
}
