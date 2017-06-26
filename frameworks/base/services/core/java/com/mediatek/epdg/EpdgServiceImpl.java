/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mediatek.epdg;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Slog;


import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EpdgServiceImpl handles remote Epdg operation requests.
 * By implementing the IEpdgManager interface.
 *
 * @hide
 */
public class EpdgServiceImpl extends IEpdgManager.Stub {
    private static final String TAG = "EpdgServiceImpl";

    private final Context mContext;
    private final AtomicBoolean mStarted = new AtomicBoolean(false);

    private Handler mHandler;
    private EpdgTracker mTracker;

    EpdgServiceImpl(Context context) {
        mContext = context;
        Slog.i(TAG, "Creating EpdgServiceImpl");

        mTracker = new EpdgTracker();
    }

    private void enforceAccessPermission() {
        mContext.enforceCallingOrSelfPermission(
            android.Manifest.permission.ACCESS_NETWORK_STATE,
            "EpdgService");
    }

    private void enforceChangePermission() {
        mContext.enforceCallingOrSelfPermission(
            android.Manifest.permission.CHANGE_NETWORK_STATE,
            "EpdgService");
    }

    private void enforceConnectivityInternalPermission() {
        mContext.enforceCallingOrSelfPermission(
            android.Manifest.permission.CONNECTIVITY_INTERNAL,
            "EpdgService");
    }

    /**
     *
     * Start function for service.
     */
    public void start() {
        Slog.i(TAG, "Starting EPDG service");

        HandlerThread handlerThread = new HandlerThread("EpdgServiceThread");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());

        mTracker.start(mContext, mHandler);

        mStarted.set(true);
    }

    @Override
    public int getReasonCode(int capabilityType) {
        return mTracker.getReasonCode(capabilityType);
    }

    @Override
    public EpdgConfig getConfiguration(int networkType) {
        return mTracker.getConfiguration(networkType);
    }

    @Override
    public EpdgConfig[] getAllConfiguration() {
        return mTracker.getAllConfiguration();
    }

    @Override
    public void setConfiguration(int networkType, EpdgConfig config) {
        mTracker.setConfiguration(networkType, config);
    }

    @Override
    public void setAllConfiguration(EpdgConfig[] configs) {
        mTracker.setAllConfiguration(configs);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("ePDG service status");
        mTracker.dump(fd, pw, args);
    }

}
