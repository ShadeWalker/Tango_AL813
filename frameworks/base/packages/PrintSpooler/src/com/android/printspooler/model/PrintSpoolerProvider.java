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

package com.android.printspooler.model;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Debug;
import android.os.IBinder;
import android.util.Log;
///M: @{
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
///M: @}

public class PrintSpoolerProvider implements ServiceConnection {
    private final Context mContext;
    private final Runnable mCallback;

    private PrintSpoolerService mSpooler;

    public PrintSpoolerProvider(Context context, Runnable callback) {
        mContext = context;
        mCallback = callback;
        Intent intent = new Intent(mContext, PrintSpoolerService.class);
        mContext.bindService(intent, this, 0);
    }

    public PrintSpoolerService getSpooler() {
        return mSpooler;
    }

    public void destroy() {
        if (mSpooler != null) {
            ///M: FIXME! In case all callbacks have not
            //      been called before unbind. @{
            try {
                mContext.unbindService(this);
            } catch (IllegalArgumentException ie) {
                mSpooler = null;
            }
            ///M: @}
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mSpooler = ((PrintSpoolerService.PrintSpooler) service).getService();
        ///M: Since we are checking <code>mSpooler ==  null</code>
        //      all the time, <code>mSpooler</code> should be finalize
        //      when remote service has died. @{
        try {
            service.linkToDeath(mDeathRecipient, 0);
        } catch (RemoteException re) {
            mSpooler = null;
        }
        ///M: @}
        if (mSpooler != null) {
            mCallback.run();
        }
    }

    ///M: @{
    private final DeathRecipient mDeathRecipient = new DeathRecipient() {
        @Override
        public void binderDied() {
            mSpooler = null;
        }
    };
    ///M: @}

    @Override
    public void onServiceDisconnected(ComponentName name) {
        ///M: <code>mSpooler</code> should be finalized here
        //      to avoid redundant operations @{
        mSpooler = null;
        ///M: @}
    }
}
