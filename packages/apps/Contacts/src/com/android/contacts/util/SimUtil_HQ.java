package com.android.contacts.util;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.util.Log;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.contacts.ext.DefaultOp01Extension;
import com.mediatek.common.PluginImpl;
import android.os.ServiceManager;
import com.android.contacts.R;

/**
 * SIM 卡工具类
 * 
 * @author niubi tang
 * 
 */
public class SimUtil_HQ {
	public static class ShowSimCardStorageInfoTask extends
			AsyncTask<Void, Void, Void> {
		private static final String TAG = "SimUtil_HQ";
		private static SubscriptionManager mManager;
		private static ShowSimCardStorageInfoTask sInstance = null;
		private boolean mIsCancelled = false;
		private boolean mIsException = false;
		private String mDlgContent = null;
		private Context mContext = null;

		public static void showSimCardStorageInfo(Activity context) {
			Log.i(TAG, "[ShowSimCardStorageInfoTask]_beg");
			if (sInstance != null) {
				sInstance.cancel();
				sInstance = null;
			}
			mManager = SubscriptionManager.from(context);
			sInstance = new ShowSimCardStorageInfoTask(context);
			sInstance.execute();
			Log.i(TAG, "[ShowSimCardStorageInfoTask]_end");
		}

		public ShowSimCardStorageInfoTask(Context context) {
			mContext = context;
			Log.i(TAG, "[ShowSimCardStorageInfoTask] onCreate()");
		}

		@Override
		protected Void doInBackground(Void... args) {
			Log.i(TAG, "[ShowSimCardStorageInfoTask]: doInBackground_beg");
			List<SubscriptionInfo> simInfos = getSortedInsertedSimInfoList(mManager
					.getActiveSubscriptionInfoList());
			Log.i(TAG, "[ShowSimCardStorageInfoTask]: simInfos.size = "
					+ simInfos.size());
			if (!mIsCancelled && (simInfos != null) && simInfos.size() > 0) {
				StringBuilder build = new StringBuilder();
				int simId = 0;
				for (SubscriptionInfo simInfo : simInfos) {
					if (simId > 0) {
						build.append("\n\n");
					}
					simId++;
					int[] storageInfos = null;
					build.append(simInfo.getDisplayName());
					build.append(":\n");
					try {
						ITelephonyEx phoneEx = ITelephonyEx.Stub
								.asInterface(ServiceManager
										.checkService("phoneEx"));
						if (!mIsCancelled && phoneEx != null) {
							storageInfos = phoneEx.getAdnStorageInfo(simInfo
									.getSubscriptionId());
							if (storageInfos == null) {
								mIsException = true;
								Log.i(TAG, " storageInfos is null");
								return null;
							}
							Log.i(TAG, "[ShowSimCardStorageInfoTask] infos: "
									+ storageInfos.toString());
						} else {
							Log.i(TAG,
									"[ShowSimCardStorageInfoTask]: phone = null");
							mIsException = true;
							return null;
						}
					} catch (RemoteException ex) {
						Log.i(TAG, "[ShowSimCardStorageInfoTask]_exception: "
								+ ex);
						mIsException = true;
						return null;
					}
					build.append(mContext.getResources().getString(
							R.string.dlg_simstorage_content, storageInfos[1],
							storageInfos[0]));
					if (mIsCancelled) {
						return null;
					}
				}
				mDlgContent = build.toString();
				Log.i(TAG,mDlgContent);
			}
			Log.i(TAG, "[ShowSimCardStorageInfoTask]: doInBackground_end");
			return null;
		}

		public void cancel() {
			super.cancel(true);
			mIsCancelled = true;
			Log.i(TAG, "[ShowSimCardStorageInfoTask]: mIsCancelled = true");
		}

		@Override
		protected void onPostExecute(Void v) {
			if (mContext instanceof Activity) {
				Log.i(TAG, "[onPostExecute]: activity find");
				Activity activity = (Activity) mContext;
				if (activity.isFinishing()) {
					Log.i(TAG, "[onPostExecute]: activity finish");
					mIsCancelled = false;
					mIsException = false;
					sInstance = null;
					return;
				}
			}

			sInstance = null;
			mIsCancelled = false;
			mIsException = false;
		}

		public List<SubscriptionInfo> getSortedInsertedSimInfoList(
				List<SubscriptionInfo> ls) {
			Collections.sort(ls, new Comparator<SubscriptionInfo>() {
				@Override
				public int compare(SubscriptionInfo arg0, SubscriptionInfo arg1) {
					return (arg0.getSimSlotIndex() - arg1.getSimSlotIndex());
				}
			});
			return ls;
		}
	}
}
