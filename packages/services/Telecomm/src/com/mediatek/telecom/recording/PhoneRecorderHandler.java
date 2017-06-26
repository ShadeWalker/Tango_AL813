/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.telecom.recording;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.storage.StorageVolume;
import android.util.Log;
import android.widget.Toast;

import com.android.server.telecom.R;
import com.android.server.telecom.TelecomGlobals;

public class PhoneRecorderHandler {

    private static final String LOG_TAG = "PhoneRecorderHandler";
    private static final boolean DBG = true;
    private static final boolean VDBG = true;

    private Intent mRecorderServiceIntent = new Intent(TelecomGlobals.getInstance().getContext(),
            PhoneRecorderServices.class);
    private IPhoneRecorder mPhoneRecorder;
    private int mPhoneRecorderState = PhoneRecorder.IDLE_STATE;
    private int mCustomValue;
    private int mRecordType;
    private Listener mListener;
    private String mRecordStoragePath;

    public static final long PHONE_RECORD_LOW_STORAGE_THRESHOLD = 2L * 1024L * 1024L; // unit is BYTE, totally 2MB
    public static final int PHONE_RECORDING_VOICE_CALL_CUSTOM_VALUE = 0;
    public static final int PHONE_RECORDING_TYPE_ONLY_VOICE = 2;

    //private boolean mIsStorageMounted = true;

    public interface Listener {
        /**
         *
         * @param state
         * @param customValue
         */
        void requestUpdateRecordState(final int state, final int customValue);

        void onStorageFull();
    }

    private PhoneRecorderHandler() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addDataScheme("file");
        TelecomGlobals.getInstance().getContext().registerReceiver(mBroadcastReceiver, intentFilter);
    }

    private static PhoneRecorderHandler sInstance = new PhoneRecorderHandler();

    public static final int EVENT_STORAGE_FULL = 0;
    public static final int EVENT_SAVE_SECCESS = 1;
    public static final int EVENT_STORAGE_UNMOUNTED = 2;
    /** M: [ALPS01969005] when error, no toast displays @{ */
    public static final int EVENT_SDCARD_ACCESS_ERROR = 3;
    public static final int EVENT_INTERNAL_ERROR = 4;
    /** @} */

    class MyHandler extends Handler {

        public MyHandler(Looper loop) {
            super(loop);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_STORAGE_FULL:
                    Toast.makeText(
                            TelecomGlobals.getInstance().getContext(),
                            TelecomGlobals.getInstance().getContext().getApplicationContext().getResources()
                                    .getText(R.string.confirm_device_info_full), Toast.LENGTH_LONG)
                            .show();
                    break;
                case EVENT_SAVE_SECCESS:
                    String path = (String) msg.obj;
                    Toast.makeText(TelecomGlobals.getInstance().getContext(), path, Toast.LENGTH_LONG).show();
                    break;
                case EVENT_STORAGE_UNMOUNTED:
                    Toast.makeText(
                            TelecomGlobals.getInstance().getContext(),
                            TelecomGlobals.getInstance().getContext().getApplicationContext()
                                    .getText(R.string.ext_media_badremoval_notification_title),
                            Toast.LENGTH_LONG).show();
                    break;
                case EVENT_SDCARD_ACCESS_ERROR:
                    Toast.makeText(
                            TelecomGlobals.getInstance().getContext(),
                            TelecomGlobals.getInstance().getContext().getResources().getString(
                                    R.string.error_sdcard_access),
                            Toast.LENGTH_LONG).show();
                    break;
                case EVENT_INTERNAL_ERROR:
                    Toast.makeText(
                            TelecomGlobals.getInstance().getContext(),
                            TelecomGlobals.getInstance().getContext().getResources().getString(
                            R.string.alert_device_error),
                            Toast.LENGTH_LONG).show();
                    break;
                default:
                    break;
            }
            if (mPhoneRecorderState == PhoneRecorder.IDLE_STATE && mPhoneRecorder != null
                    && !isRecording()) {
                log("Ready to unbind service");
                TelecomGlobals.getInstance().getContext().unbindService(mConnection);
                mPhoneRecorder = null;
            }
        }
    };

    public MyHandler mHandler = new MyHandler(Looper.getMainLooper());

    private Runnable mRecordDiskCheck = new Runnable() {
        public void run() {
            checkRecordDisk();
        }
    };

    public static synchronized PhoneRecorderHandler getInstance() {
        return sInstance;
    }

    /**
     *
     * @param listener
     */
    public void setListener(Listener listener) {
        mListener = listener;
    }

    public Listener getListener() {
        return mListener;
    }

    /**
     *
     * @param listener
     */
    public void clearListener(Listener listener) {
        if (listener == mListener) {
            mListener = null;
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mPhoneRecorder = IPhoneRecorder.Stub.asInterface(service);
            try {
                log("onServiceConnected");
                if (null != mPhoneRecorder) {
                    mPhoneRecorder.listen(mPhoneRecordStateListener);
                    mPhoneRecorder.startRecord();
                    mHandler.postDelayed(mRecordDiskCheck, 500);
                }
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "onServiceConnected: couldn't register to record service",
                        new IllegalStateException());
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            log("[onServiceDisconnected]");
            mPhoneRecorder = null;
        }
    };

    private int convertStatusToEventId(int statusCode) {
        int eventId = EVENT_INTERNAL_ERROR;
        switch (statusCode) {
            case Recorder.SDCARD_ACCESS_ERROR:
                eventId = EVENT_SDCARD_ACCESS_ERROR;
                break;
            case Recorder.SECCESS:
                eventId = EVENT_SAVE_SECCESS;
                break;
            case Recorder.STORAGE_FULL:
                eventId = EVENT_STORAGE_FULL;
                break;
            case Recorder.STORAGE_UNMOUNTED:
                eventId = EVENT_STORAGE_UNMOUNTED;
                break;
            case Recorder.INTERNAL_ERROR:
            default:
                eventId = EVENT_INTERNAL_ERROR;
                break;
        }
        return eventId;
    }
    private IPhoneRecordStateListener mPhoneRecordStateListener = new IPhoneRecordStateListener.Stub() {
        /**
         *
         * @param state
         */
        public void onStateChange(int state) {
            log("[onStateChange] state is " + state);
            mPhoneRecorderState = state;
            if (null != mListener) {
                mListener.requestUpdateRecordState(state, mCustomValue);
            }
        }


        /**
         *
         * @param iError
         */
        public void onError(int iError) {
            mHandler.sendEmptyMessage(convertStatusToEventId(iError));
            mPhoneRecorderState = PhoneRecorder.IDLE_STATE;
        }

        public void onFinished(int cause, String data) {
            if (data == null) {
                mHandler.sendEmptyMessage(convertStatusToEventId(cause));
            } else {
                Message msg = mHandler.obtainMessage();
                msg.what = convertStatusToEventId(cause);
                msg.obj = data;
                mHandler.sendMessage(msg);
            }
        }
    };

    /**
     *
     * @param customValue
     */
    public void startVoiceRecord(final int customValue) {
        /*mCustomValue = customValue;
        mRecordType = PhoneRecorderHandler.PHONE_RECORDING_TYPE_ONLY_VOICE;
        mRecordStoragePath = RecorderUtils.getExternalStorageDefaultPath();
        mPhoneRecorderState = PhoneRecorder.RECORDING_STATE;
        if (null != mRecorderServiceIntent && null == mPhoneRecorder) {
            TelecomGlobals.getInstance().getContext().bindService(mRecorderServiceIntent, mConnection,
                    Context.BIND_AUTO_CREATE);
        } else if (null != mRecorderServiceIntent && null != mPhoneRecorder) {
            try {
                mPhoneRecorder.startRecord();
                mHandler.postDelayed(mRecordDiskCheck, 500);
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "start Record failed", new IllegalStateException());
            }
        }*/
    }

    public void stopVoiceRecord() {
        if (isRecording()) {
            stopVoiceRecord(true);
        }
    }

    private void stopVoiceRecord(boolean isMount) {
        /*try {
            log("stopRecord");
            if (null != mPhoneRecorder) {
                mPhoneRecorder.stopRecord(isMount);
            }
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "stopRecord: couldn't call to record service",
                    new IllegalStateException());
        }*/
    }

//    /**
//     *
//     * @param type
//     * @param sdMaxSize
//     * @param customValue
//     */
//    public void startVideoRecord(final int type, final long sdMaxSize, final int customValue) {
//        mRecordType = type;
//        mCustomValue = customValue;
//        mRecordStoragePath = RecorderUtils.getExternalStorageDefaultPath();
//        log("- start call VTManager.startRecording() : type = " + type + " sd max size = "
//                + sdMaxSize);
//        VTManagerWrapper.getInstance().startRecording(type, sdMaxSize);
//        log("- end call VTManager.startRecording()");
//        mPhoneRecorderState = PhoneRecorder.RECORDING_STATE;
//        if (null != mListener) {
//            mListener.requestUpdateRecordState(mPhoneRecorderState, mCustomValue);
//        }
//        mHandler.postDelayed(mRecordDiskCheck, 500);
//    }
//
//    public void stopVideoRecord() {
//        log("- start call VTManager.stopRecording() : " + mRecordType);
//        VTManagerWrapper.getInstance().stopRecording();
//        log("- end call VTManager.stopRecording() : " + mRecordType);
//        mPhoneRecorderState = PhoneRecorder.IDLE_STATE;
//        if (null != mListener) {
//            mListener.requestUpdateRecordState(mPhoneRecorderState, mCustomValue);
//        }
//    }

    /**
     *
     * @return
     */
    public int getPhoneRecorderState() {
        return mPhoneRecorderState;
    }

    /**
     *
     * @param state
     */
    public void setPhoneRecorderState(final int state) {
        mPhoneRecorderState = state;
    }

    public int getCustomValue() {
        return mCustomValue;
    }

    /**
     *
     * @param customValue
     */
    public void setCustomValue(final int customValue) {
        mCustomValue = customValue;
    }

    public int getRecordType() {
        return mRecordType;
    }

    /**
     *
     * @param recordType
     */
    public void setRecordType(final int recordType) {
        mRecordType = recordType;
    }

//    /**
//     *
//     * @return
//     */
//    public boolean isVTRecording() {
//        return Constants.PHONE_RECORDING_VIDEO_CALL_CUSTOM_VALUE == mCustomValue
//                && PhoneRecorder.RECORDING_STATE == mPhoneRecorderState;
//    }

    private void checkRecordDisk() {
        if (!RecorderUtils.diskSpaceAvailable(mRecordStoragePath, PHONE_RECORD_LOW_STORAGE_THRESHOLD)) {
            Log.e("AN: ", "Checking result, disk is full, stop recording...");
            if (isRecording()) {
                stopVoiceRecord();
                if (null != mListener) {
                    mListener.onStorageFull();
                }
            }
//            if (PhoneRecorder.isRecording() || isVTRecording()) {
//                if (PhoneRecorder.isRecording()) {
//                    stopVoiceRecord();
//                } else if (isVTRecording()) {
//                    stopVideoRecord();
//                }
//                if (null != mListener) {
//                    mListener.onStorageFull();
//                }
//            }
        } else {
            mHandler.postDelayed(mRecordDiskCheck, 50);
        }
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_MEDIA_EJECT.equals(intent.getAction())
                    || Intent.ACTION_MEDIA_UNMOUNTED.equals(intent.getAction())) {
                StorageVolume storageVolume =
                    (StorageVolume) intent.getParcelableExtra(StorageVolume.EXTRA_STORAGE_VOLUME);
                if (null == storageVolume) {
                    log("storageVolume is null");
                    return;
                }
                String currentPath = storageVolume.getPath();
                if (null == mRecordStoragePath || !currentPath.equals(mRecordStoragePath)) {
                    log("not current used storage unmount or eject");
                    return;
                }
                if (PhoneRecorder.RECORDING_STATE == mPhoneRecorderState) {
                    if (PhoneRecorderHandler.PHONE_RECORDING_TYPE_ONLY_VOICE == mRecordType) {
                        log("Current used sd card is ejected, stop voice record");
                        stopVoiceRecord(false);
                    }
//                    else if (Constants.PHONE_RECORDING_TYPE_VOICE_AND_PEER_VIDEO == mRecordType
//                            || Constants.PHONE_RECORDING_TYPE_ONLY_PEER_VIDEO == mRecordType) {
//                        log("Current used sd card is ejected, stop video record");
//                        stopVideoRecord();
//                    }
                }
            } /*else if (Intent.ACTION_MEDIA_MOUNTED.equals(intent.getAction())) {
                mIsStorageMounted = true;
            }*/
        }
    };

    public void stopRecording() {
        log("Stop record, the record custom value is "
                + PhoneRecorderHandler.getInstance().getCustomValue());
        if (PhoneRecorderHandler.PHONE_RECORDING_VOICE_CALL_CUSTOM_VALUE == PhoneRecorderHandler.getInstance()
                .getCustomValue()) {
            PhoneRecorderHandler.getInstance().stopVoiceRecord();
        }
//        else if (Constants.PHONE_RECORDING_VIDEO_CALL_CUSTOM_VALUE ==
//                PhoneRecorderHandler.getInstance().getCustomValue()) {
//            if (PhoneRecorder.isRecording()) {
//                PhoneRecorderHandler.getInstance().stopVoiceRecord();
//            }
//            else if (PhoneRecorder.RECORDING_STATE ==
//                        PhoneRecorderHandler.getInstance().getPhoneRecorderState()) {
//                PhoneRecorderHandler.getInstance().stopVideoRecord();
//            }
    }

    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    private boolean isRecording() {
        try {
            if (mPhoneRecorder != null) {
                return mPhoneRecorder.isRecording();
            }
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "stopVoiceRecord failed", new IllegalStateException());
        }
        return false;
    }
}
