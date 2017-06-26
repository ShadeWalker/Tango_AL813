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

package com.android.server.telecom;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.telecom.TelecomManager;
import android.telephony.PreciseCallState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import java.util.ArrayList;

import com.android.internal.telephony.PhoneFactory;


public class AutotestEngine {
    static final String TAG = "AutotestEngine";

    private static AutotestEngine sEngine;

    protected Instrumentation mInstrumentation;

    BroadcastReceiver mPhoneStateReceiver;

    ArrayList<Listener> mListeners = new ArrayList<Listener>();

    boolean mInit = false;
    private Context mTargetContext;

    private AutotestEngine() {
        //
    }

    public static AutotestEngine makeInstance(Instrumentation testCase) {
        log("makeInstance start+ " + java.lang.System.currentTimeMillis());
        AutotestEngine engine = AutotestEngine.getInstance();
        engine.setInstrumentation(testCase);
        engine.init();
        testCase.waitForIdleSync();
        log("makeInstance end- " + java.lang.System.currentTimeMillis());
        return engine;
    }

    protected void init() {
        if (mInit) {
            return;
        }

        log("+init");

        mTargetContext = getInstrumentation().getTargetContext();
        try {
            waitForPhoneProcessReady();
        } catch (IllegalStateException e) {
            log("PhoneGlobal not ready!");
        }

        getInstrumentation().runOnMainSync(new Runnable() {
            public void run() {
                mPhoneStateReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        log("onPhoneStateChanged onReceive : " + intent);
                        String action = intent.getAction();
                        if (action.equals(TelephonyManager.ACTION_PRECISE_CALL_STATE_CHANGED)) {
                            onPhoneStateChanged(intent);
                        }
                    }
                };
            }
        });

        registerPhoneState();
        log("-init");
        mInit = true;
    }

    public void waitForPhoneProcessReady() throws IllegalStateException {
        // use phone account to wait
        log("waitForPhoneProcessReady");
        PhoneFactory.makeDefaultPhones(mTargetContext);
        for(int i=10;1>0;i--) {
            if(TelecomManager.from(mTargetContext).getAllPhoneAccountsCount() >0) {
                break;
            }
            Utils.sleep(1000);
        }
        if(TelecomManager.from(mTargetContext).getAllPhoneAccountsCount() == 0) {
            log("TelecomManager  init fail");
            return;
          // should notify test case this thing
        }
        for(int i=10;1>0;i--) {
            if(PhoneFactory.getPhones().length>=1) {
                break;
            }
            Utils.sleep(1000);
        }
        if(PhoneFactory.getPhones().length == 0) {
            log("PhoneFactory  init fail");
            return;
          // should notify test case this thing
        }
    }

    public void setInstrumentation(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
    }

    public Instrumentation getInstrumentation() {
        return mInstrumentation;
    }

    public static AutotestEngine getInstance() {
        if (sEngine == null) {
            sEngine = new AutotestEngine();
        }
        return sEngine;
    }

    /**
     * use this function to start command from testcase.
     * @param command the type like "Call 10086 1"
     * @return
     */
    public int execute(String command) {
        log("--------------------execute start command-------------------------- " + command);
        if (TextUtils.isEmpty(command)) {
            log("execute command is error " + command);
            return ICommand.RESULT_COMMAND_NOT_SUPPORT;
        }

        int result = ICommand.RESULT_OK;
        String name;
        String parameters = null;

        // get command name and content
        final int index = command.indexOf(' ');
        if (index > 0) {
            name = command.substring(0, index);
            parameters = command.substring(index + 1);
        } else {
            name = command;
        }

        // create command
        ICommand c = CommandFactory.getInstance().getCommand(name);
        if (c == null) {
            log("create command error name: " + name);
            return ICommand.RESULT_COMMAND_NOT_SUPPORT;
        }

        // execute command
        log("execute start: " + command);
        result = c.execute(parameters);

        log("-----------------------execute end result----------------------------- " + AutotestEngineUtils.resultToString(result));
        return result;
    }

    private void registerPhoneState() {
        // maybe should use import com.android.internal.telephony.PhoneStateIntentReceiver; to listen
        mInstrumentation.getTargetContext().registerReceiver(mPhoneStateReceiver,
                new IntentFilter(TelephonyManager.ACTION_PRECISE_CALL_STATE_CHANGED));
    }

    private void unregisterPhoneState() {
        mInstrumentation.getTargetContext().unregisterReceiver(mPhoneStateReceiver);
    }

    protected void onPhoneStateChanged(Intent intent) {
        int ringingCallState = intent.getIntExtra(TelephonyManager.EXTRA_RINGING_CALL_STATE, -1);
        int foregroundCallState = intent.getIntExtra(TelephonyManager.EXTRA_FOREGROUND_CALL_STATE,
                -1);
        int backgroundCallState = intent.getIntExtra(TelephonyManager.EXTRA_BACKGROUND_CALL_STATE,
                -1);
        int disconnectCause = intent.getIntExtra(TelephonyManager.EXTRA_DISCONNECT_CAUSE, -1);
        int preciseDisconnectCause = intent.getIntExtra(
                TelephonyManager.EXTRA_PRECISE_DISCONNECT_CAUSE, -1);
        PreciseCallState preciseCallState = new PreciseCallState(ringingCallState,
                foregroundCallState, backgroundCallState, disconnectCause, preciseDisconnectCause);
        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).onPhoneStateChanged(preciseCallState);
        }
    }

  /*  public void onSuppServiceFailed(AsyncResult result) {

        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).onSuppServiceFailed(result);
        }
    }*/

    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    static void log(String msg) {
        Utils.log(TAG, "[AutotestEngine] " + msg);
    }

    public interface Listener {
        void onPhoneStateChanged(PreciseCallState preciseCallState);

        // void onServiceStateChanged(AsyncResult r, int slot);

        // void onSuppServiceFailed(AsyncResult r);
    }

    public void release() {
        unregisterPhoneState();
        sEngine = null;
    }
    
    public Context getContext(){
        return mTargetContext;
    }
}
