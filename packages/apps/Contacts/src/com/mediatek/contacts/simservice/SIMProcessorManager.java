package com.mediatek.contacts.simservice;

import java.util.concurrent.ConcurrentHashMap;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.android.contacts.common.vcard.ProcessorBase;
import com.mediatek.contacts.simservice.SIMServiceUtils.SIMProcessorState;
import com.mediatek.contacts.util.LogUtils;
import android.content.ContentResolver;//add by zhaizhanfeng for number match update sqlite at 150923
import android.net.Uri;//add by zhaizhanfeng for number match update sqlite at 150923
import android.content.ContentValues;//add by zhaizhanfeng for number match update sqlite at 150923

public class SIMProcessorManager implements SIMProcessorState {
	private static final String TAG = "SIMProcessorManager";

	public interface ProcessorManagerListener {
		public void addProcessor(long scheduleTime, ProcessorBase processor);

		public void onAllProcessorsFinished();
	}

	public interface ProcessorCompleteListener {
		public void onProcessorCompleted(Intent intent);
	}

	private ProcessorManagerListener mListener;
	private Handler mHandler;
	private Context mContext;//add by zhaizhanfeng for number match update sqlite at 150923
	private ConcurrentHashMap<Integer, SIMProcessorBase> mImportRemoveProcessors;
	private ConcurrentHashMap<Integer, SIMProcessorBase> mOtherProcessors;

	private static final int MSG_SEND_STOP_SERVICE = 1;

	// Out of 200ms hasn't new tasks and all tasks have completed, will stop
	// service.
	// The Backgroud broast will delayed by backgroudService,so if nothing to
	// do,
	// should stop service soon
	private static final int DELAY_MILLIS_STOP_SEVICE = 200;

	// //////////////////////////Public
	// funtion///////////////////////////////////////////////////////

	public SIMProcessorManager(Context context,
			ProcessorManagerListener listener) {
		mListener = listener;
		mContext=context;//add by zhaizhanfeng for number match update sqlite at 150923
		mImportRemoveProcessors = new ConcurrentHashMap<Integer, SIMProcessorBase>();
		mOtherProcessors = new ConcurrentHashMap<Integer, SIMProcessorBase>();
		SIMServiceUtils.setSIMProcessorState(this);
		mHandler = new Handler(context.getMainLooper()) {
			@Override
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case MSG_SEND_STOP_SERVICE:
					LogUtils.d(TAG, "handleMessage MSG_SEND_STOP_SERVICE");
					callStopService();
					break;
				default:
					break;
				}
			}
		};
	}

	public void handleProcessor(Context context, int subId, int workType,
			Intent intent) {
		LogUtils.d(
				TAG,
				"[handleProcessor] subId=" + subId + ",time="
						+ System.currentTimeMillis());
		SIMProcessorBase processor = createProcessor(context, subId, workType,
				intent);
		if (processor != null && mListener != null) {
			LogUtils.d(TAG, "[handleProcessor]Add processor [subId=" + subId
					+ "] to threadPool.");
			mListener.addProcessor(/* 1000 + slotId * 300 */0, processor);
		}
	}

	public boolean isImportRemoveRunning(int subId) {
		if ((mImportRemoveProcessors != null)
				&& (mImportRemoveProcessors.containsKey(subId))) {
			SIMProcessorBase processor = mImportRemoveProcessors.get(subId);
			if (processor == null) {
				LogUtils.i(TAG,
						"[isImportRemoveRunning]processor is null, return false.");
				return false;
			}
			if (processor.isRunning()) {
				LogUtils.i(TAG,
						"[isImportRemoveRunning]has exist running processor, return true.");
				return true;
			}
		}

		return false;
	}

	// //////////////////////////private
	// funtion///////////////////////////////////////////////////////

	private SIMProcessorBase createProcessor(Context context, int subId,
			int workType, Intent intent) {
		SIMProcessorBase processor = null;
		/**
		 * [ALPS01224227]the mImportRemoveProcessors is likely to be accessed by
		 * main thread and sub thread at the same time, we should protect the
		 * race condition
		 */
		synchronized (mProcessorRemoveLock) {
			// The rule to check whether or not create new processor
			if (mImportRemoveProcessors.containsKey(subId)) {
				processor = mImportRemoveProcessors.get(subId);
				Log.v(TAG, "[createProcessor] processor.getType() = "
						+ processor.getType() + " workType = " + workType);
				if (processor != null
						&& (workType == SIMServiceUtils.SERVICE_WORK_IMPORT || workType == SIMServiceUtils.SERVICE_WORK_REMOVE)) {
					if (processor.isRunning()
							&& processor.getType() == workType) {
						LogUtils.d(TAG,
								"[createProcessor]has exist running processor, return null.");
						return null;
					}
					processor.cancel(false);
					mImportRemoveProcessors.remove(subId);
				}
			} else {
				LogUtils.d(TAG, "[createProcessor]no processor for subId: "
						+ subId);
			}

			processor = createProcessor(context, subId, workType, intent,
					mProcessoListener);

			if (workType == SIMServiceUtils.SERVICE_WORK_IMPORT
					|| workType == SIMServiceUtils.SERVICE_WORK_REMOVE) {
				mImportRemoveProcessors.put(subId, processor);
			} else {
				mOtherProcessors.put(subId, processor);
			}
		}

		return processor;
	}

	private SIMProcessorBase createProcessor(Context context, int subId,
			int workType, Intent intent, ProcessorCompleteListener listener) {
		Log.v(TAG, "[createProcessor] create new processor for subId: " + subId
				+ ", workType: " + workType);
		SIMProcessorBase processor = null;

		if (workType == SIMServiceUtils.SERVICE_WORK_IMPORT) {
			processor = new SIMImportProcessor(context, subId, intent, listener);
		} else if (workType == SIMServiceUtils.SERVICE_WORK_REMOVE) {
			processor = new SIMRemoveProcessor(context, subId, intent, listener);
		} else if (workType == SIMServiceUtils.SERVICE_WORK_EDIT) {
			processor = new SIMEditProcessor(context, subId, intent, listener);
		} else if (workType == SIMServiceUtils.SERVICE_WORK_DELETE) {
			processor = new SIMDeleteProcessor(context, subId, intent, listener);
		} else if (workType == SIMServiceUtils.SERVICE_WORK_IMPORT_PRESET_CONTACTS) {
			processor = new PresetContactsImportProcessor(context, subId,
					intent, listener);
		}else if (workType == SIMServiceUtils.SERVICE_WORK_IMPORT_SDN_CONTACTS) {
            processor = new SdnContactsImportProcessor(context, subId, intent, listener);
        }

		return processor;
	}

	private ProcessorCompleteListener mProcessoListener = new ProcessorCompleteListener() {

		@Override
		public void onProcessorCompleted(Intent intent) {
			if (intent != null) {
				int subId = intent.getIntExtra(
						SIMServiceUtils.SERVICE_SUBSCRIPTION_KEY, 0);
				int workType = intent.getIntExtra(
						SIMServiceUtils.SERVICE_WORK_TYPE, -1);
				LogUtils.d(TAG, "[onProcessorCompleted] subId = " + subId
						+ " time=" + System.currentTimeMillis()
						+ ", workType = " + workType);
				/**
				 * [ALPS01224227]the mImportRemoveProcessors is likely to be
				 * accessed by main thread and sub thread at the same time, we
				 * should protect the race condition
				 */
				synchronized (mProcessorRemoveLock) {
					if ((workType == SIMServiceUtils.SERVICE_WORK_IMPORT || workType == SIMServiceUtils.SERVICE_WORK_REMOVE)
							&& mImportRemoveProcessors.containsKey(subId)) {
						LogUtils.d(TAG,
								"[onProcessorCompleted] remove import/remove processor subId="
										+ subId);
						/**
						 * [ALPS01224227]when we're going to remove the
						 * processor, in seldom condition, it might have already
						 * removed and replaced with another processor. in this
						 * case, we should not remove it any more.
						 */
						if (mImportRemoveProcessors.get(subId).identifyIntent(
								intent)) {
							mImportRemoveProcessors.remove(subId);
							//add by zhaizhanfeng for number match update sqlite at 150923 start
							ContentResolver contentResolver = mContext.getContentResolver(); 
							Uri uri = Uri.parse("content://com.android.contacts/phone_lookup2"); 
							contentResolver.update(uri, new ContentValues(), null, null); 
							//add by zhaizhanfeng for number match update sqlite at 150923 end
							checkStopService();
						} else {
							LogUtils.w(
									TAG,
									"[onProcessorCompleted] race condition, current i/r processor has already removed by other thread(s)");
						}
					} else if (mOtherProcessors.containsKey(subId)) {
						Log.d(TAG,
								"[onProcessorCompleted] remove other processor subId="
										+ subId);
						/**
						 * [ALPS01224227]when we're going to remove the
						 * processor, in seldom condition, it might have already
						 * removed and replaced with another processor. in this
						 * case, we should not remove it any more.
						 */
						if (mOtherProcessors.get(subId).identifyIntent(intent)) {
							mOtherProcessors.remove(subId);
							checkStopService();
						} else {
							LogUtils.w(
									TAG,
									"[onProcessorCompleted] race condition, current other processor has already removed by other thread(s)");
						}
					} else {
						LogUtils.w(TAG,
								"[onProcessorCompleted] slotId processor not found");
					}
				}
			}
		}
	};

	private void checkStopService() {
		Log.v(TAG, "[checkStopService]");
		if (mImportRemoveProcessors.size() == 0 && mOtherProcessors.size() == 0) {
			if (mHandler != null) {
				Log.v(TAG, "[checkStopService] send stop service message.");
				mHandler.removeMessages(MSG_SEND_STOP_SERVICE);
				mHandler.sendEmptyMessageDelayed(MSG_SEND_STOP_SERVICE,
						DELAY_MILLIS_STOP_SEVICE);
			}
		}
	}

	private void callStopService() {
		LogUtils.i(TAG, "[callStopService]");
		if (mListener != null && mImportRemoveProcessors.size() == 0
				&& mOtherProcessors.size() == 0) {
			mListener.onAllProcessorsFinished();
		}
	}

	/**
	 * [ALPS01224227]the lock for synchronized
	 */
	private final Object mProcessorRemoveLock = new Object();

}
