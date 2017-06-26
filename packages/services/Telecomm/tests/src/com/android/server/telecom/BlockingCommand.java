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
import android.os.Handler;
import android.os.Message;
import android.telephony.PreciseCallState;

public class BlockingCommand extends DefaultCommand implements AutotestEngine.Listener {

    private static final int DEFAULT_TIME_OUT = 30000;

    protected static Handler sHandler;

    protected static int sTimeOutMsg = 10000;

    protected int mTimeOutMsg;

    private int mTimeOut = DEFAULT_TIME_OUT;

    private int mResult = ICommand.RESULT_UNKNOWN;

    protected boolean mBeforeExecuteCalled = false;

    protected boolean mAfterExecuteCalled = false;
     
  
    public void onPhoneStateChanged(PreciseCallState preciseCallState) {}

    // public void onServiceStateChanged(AsyncResult r, int slot) {}

/*    public void onSuppServiceFailed(AsyncResult r) {
        // SuppService failed, mostly means this SIM card does not support this sup-server.
        Phone.SuppService service = (Phone.SuppService) r.result;
        log("onSuppServiceFailed service: " + service);
        notify(ICommand.RESULT_COMMAND_NOT_SUPPORT);
    }*/

    public BlockingCommand() {}

    @Override
    public int execute(String content) {

        int result = ICommand.RESULT_UNKNOWN;
        log("execute parameters " + content);
        mBeforeExecuteCalled = false;
        result = beforeExecute();
        log("beforeExecute mResult: " + AutotestEngineUtils.resultToString(result));

        if (!mBeforeExecuteCalled) {
            throw new RuntimeException("super.beforeExecute must be called");
        }

        if (result == ICommand.RESULT_OK) {
            log("executeInner start content: " + content);
            result = executeInner(content);
            log("executeInner end, result: " + AutotestEngineUtils.resultToString(result));
            if(result == ICommand.RESULT_OK) {
                sendTimeOutMessage();
                log("waitForResult");
                waitForResult();
                removeTimeOutMessage();
                result = mResult;
            }
        }

        mAfterExecuteCalled = false;
        log("afterExecute");
        afterExecute();

        if (!mAfterExecuteCalled) {
            throw new RuntimeException("super.afterExecute must be called");
        }

        log("execute end result: " + AutotestEngineUtils.resultToString(result));
        return result;
    }

    @Override
    protected int beforeExecute() {
        log("beforeExecute");
        AutotestEngine.getInstance().addListener(this);
        if (sHandler == null) {
            log("init sHandler");
            final Instrumentation instrumentation = AutotestEngine.getInstance()
                    .getInstrumentation();
            instrumentation.runOnMainSync(new Runnable() {
                public void run() {
                    sHandler = new Handler() {
                        public void handleMessage(Message msg) {
                            final BlockingCommand command = (BlockingCommand) msg.obj;
                            log("-----notify RESULT_TIME_OUT-----" + command.mTag);
                            command.notify(ICommand.RESULT_TIME_OUT);
                        }
                    };
                }
            });
        }
        mBeforeExecuteCalled = true;
        return ICommand.RESULT_OK;
    }

    @Override
    protected int afterExecute() {
        log("afterExecute");
        AutotestEngine.getInstance().removeListener(this);
        mAfterExecuteCalled = true;
        return ICommand.RESULT_OK;
    }

    protected void notify(int result) {
        synchronized (this) {
            log("notify, result = " + AutotestEngineUtils.resultToString(result));
            mResult = result;
            notify();
        }
    }

    protected void waitForResult() {
        synchronized (this) {
            try {
                wait();
            } catch (InterruptedException e) {
                log(e.getMessage());
            }
        }
    }

    public void setTimeOut(int time) {
        mTimeOut = time;
    }

    protected int obtainTimeOutMessage() {
        return sTimeOutMsg++;
    }

    protected void sendTimeOutMessage() {
        final int timeOut = mTimeOut;
        mTimeOutMsg = obtainTimeOutMessage();
        if (timeOut != 0) {
            Message msg = sHandler.obtainMessage(mTimeOutMsg);
            msg.obj = this;
            sHandler.sendMessageDelayed(msg, timeOut);
        }
    }

    protected void removeTimeOutMessage() {
        if (sHandler.hasMessages(mTimeOutMsg)) {
            sHandler.removeMessages(mTimeOutMsg);
        }
    }
}
