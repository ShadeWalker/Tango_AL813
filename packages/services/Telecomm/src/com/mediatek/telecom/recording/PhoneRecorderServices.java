/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
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

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

public class PhoneRecorderServices extends Service {

    private static final String LOG_TAG = "RecorderServices";
    private static final String PHONE_VOICE_RECORD_STATE_CHANGE_MESSAGE = "com.android.phone.VoiceRecorder.STATE";
    private static final int REQUEST_START_RECORDING = 1;
    private static final int REQUEST_STOP_RECORDING = 2;
    private static final int REQUEST_QUIT = -1;

    private PhoneRecorder mPhoneRecorder;
    IPhoneRecordStateListener mStateListener;
    private HandlerThread mWorkerThread;
    private Handler mRecordHandler;

    public IBinder onBind(Intent intent) {
        Log.d(LOG_TAG, "onBind");
        mPhoneRecorder = PhoneRecorder.getInstance(this);

        if (null != mPhoneRecorder) {
            mPhoneRecorder.setOnStateChangedListener(mPhoneRecorderStateListener);
        }
        mWorkerThread = new HandlerThread("RecordWorker");
        mWorkerThread.start();
        mRecordHandler = new RecordHandler(mWorkerThread.getLooper());
        return mBinder;
    }

    public boolean onUnbind(Intent intent) {
        Log.d(LOG_TAG, "onUnbind");
        mRecordHandler.sendMessage(mRecordHandler.obtainMessage(REQUEST_STOP_RECORDING,
                new Boolean(true)));
        mRecordHandler.sendEmptyMessage(REQUEST_QUIT);
        return super.onUnbind(intent);
    }

    public void onCreate() {
        super.onCreate();
        log("onCreate");
    }

    public void onDestroy() {
        super.onDestroy();
        log("onDestroy");
        /*if (null != mBroadcastReceiver) {
            unregisterReceiver(mBroadcastReceiver);
            mBroadcastReceiver = null;
        }*/
    }

    public void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    private int mPhoneRecorderStatus = PhoneRecorder.IDLE_STATE;
    private PhoneRecorder.OnStateChangedListener mPhoneRecorderStateListener = new PhoneRecorder.OnStateChangedListener() {
        public void onStateChanged(int state) {
            log("[onStateChanged]state = " + state);
            int iPreviousStatus = PhoneRecorderServices.this.mPhoneRecorderStatus;
            PhoneRecorderServices.this.mPhoneRecorderStatus = state;
            if ((iPreviousStatus != state)) {
                Intent broadcastIntent = new Intent(PHONE_VOICE_RECORD_STATE_CHANGE_MESSAGE);
                broadcastIntent.putExtra("state", state);
                sendBroadcast(broadcastIntent);
                if (null != mStateListener) {
                    try {
                        log("[onStateChanged]notify listener");
                        mStateListener.onStateChange(state);
                    } catch (RemoteException e) {
                        Log.e(LOG_TAG, "PhoneRecordService: call listener onStateChange failed",
                                new IllegalStateException());
                    }
                }
            }
        }

        public void onError(int error) {
            log("[onError]error = " + error);
            if (null != mStateListener) {
                try {
                    log("[onError]notify listener");
                    mStateListener.onError(error);
                } catch (RemoteException e) {
                    Log.e(LOG_TAG, "PhoneRecordService: call listener onError() failed",
                            new IllegalStateException());
                }
            }
        }

        public void onFinished(int cause, String data) {
            if (null != mStateListener) {
                try {
                    log("[onFinished]notify listener");
                    mStateListener.onFinished(cause, data);
                } catch (RemoteException e) {
                    Log.e(LOG_TAG, "PhoneRecordService: call listener onError() failed",
                            new IllegalStateException());
                }
            }
        };
    };

    private final IPhoneRecorder.Stub mBinder = new IPhoneRecorder.Stub() {
        public void listen(IPhoneRecordStateListener callback) {
            log("listen");
            if (null != callback) {
                mStateListener = callback;
            }
        }

        @Deprecated
        public void remove() {
            log("remove is deprecated, do nothing. listener will be removed automatically");
        }

        public void startRecord() {
            log("startRecord");
            mRecordHandler.sendEmptyMessage(REQUEST_START_RECORDING);
        }

        public void stopRecord(boolean isMounted) {
            log("stopRecord");
            mRecordHandler.sendMessage(mRecordHandler.obtainMessage(REQUEST_STOP_RECORDING, new Boolean(isMounted)));
        }

        public boolean isRecording() {
            if(mPhoneRecorder != null) {
                return PhoneRecorder.isRecording();
            } else {
                return false;
            }
        }
    };

    /**
     * Handler base on worker thread Looper.
     * it will deal with the time consuming operations, such as start/stop recording
     */
    private class RecordHandler extends Handler {
        public RecordHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            log("[handleMessage]what = " + msg.what);
            switch (msg.what) {
                case REQUEST_START_RECORDING:
                    if (null != mPhoneRecorder) {
                        log("[handleMessage]do start recording");
                        mPhoneRecorder.startRecord();
                    }
                    break;
                case REQUEST_STOP_RECORDING:
                    Boolean isMounted = (Boolean) msg.obj;
                    if (null != mPhoneRecorder && PhoneRecorder.isRecording()) {
                        log("[handleMessage]do stop recording");
                        mPhoneRecorder.stopRecord(isMounted);
                    }
                    break;
                case REQUEST_QUIT:
                    log("[handleMessage]quit worker thread and clear handler");
                    // quit to avoid looper leakage, and make sure the pending
                    // operations can finish before really quit
                    mWorkerThread.quit();
                    break;
                default:
                    log("[handleMessage]unexpected message: " + msg.what);
            }
        }
    }
}
