/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2014. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

package com.android.server.am;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Debug;
import android.os.Message;
import android.widget.Toast;
import android.util.Log;
import com.android.server.am.ActivityStack.ActivityState;
import java.io.PrintWriter;
import java.util.ArrayList;
import android.content.pm.ActivityInfo;
import android.view.Display;
/**
 * Mutli Window Activity Monitor. The class is an ulity for AMS to block the activity that
 * can't resume with other activity at the same time.
 */
public class MwActivityMonitor {
    private final static String TAG = "MwActivityMonitor";
    static final boolean DEBUG = true;

    ArrayList<ActivityRecord> mRunningActivity = new ArrayList<ActivityRecord>();

    Context mContext;
    ActivityManagerService mService;

    static final int OK_BLOCK_ACTIVITY = 1;
    static final int NG_DONOT_BLOCK = 0;
    static final int NG_SAME_CALLER_APP = -1;
    static final int NG_SAME_COMPONENT_NAME = -2;
    static final int NG_SAME_TASK = -3;

    static final int SHOW_ONE_ACTIVITY_AT_GALLERY3D_MSG = 1;
    static final int SHOW_MAXIMUM_FRONT_FLOATING_MSG = 2;
    static final int MSG_MW_MAX_RESTORE = 3;

    private int mMaximumFrontFloatingSize;
        
    /// [ALPS02039971], For app activity start by same process , do not block it.
    private ProcessRecord mCallerApp;

    public MwActivityMonitor() {
    }

    public MwActivityMonitor(Context context, ActivityManagerService service) {
        this();
        mContext = context;
        mService = service;
        mMaximumFrontFloatingSize = mContext.getResources().getInteger(
                com.mediatek.internal.R.integer.config_mw_max_size);
    }

    final Handler mMwHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case SHOW_ONE_ACTIVITY_AT_GALLERY3D_MSG: {
                Toast toast = Toast
                        .makeText(
                                mContext,
                                mContext.getResources()
                                        .getString(
                                                com.mediatek.internal.R.string.gallery_camera_mutex),
                                Toast.LENGTH_SHORT);
                toast.show();
            }
                break;
            case SHOW_MAXIMUM_FRONT_FLOATING_MSG: {
                Toast toast = Toast
                        .makeText(
                                mContext,
                                mContext.getResources()
                                        .getString(
                                                com.mediatek.internal.R.string.mw_max_size_string,
                                                mMaximumFrontFloatingSize),
                                Toast.LENGTH_SHORT);
                toast.show();
            }
                break;
            case MSG_MW_MAX_RESTORE:
                final MessageObj obj = (MessageObj) msg.obj;
                synchronized (mService) {
                    if (mService.mLruProcesses.contains(obj.mAr.app)
                            && obj.mAr.app == obj.mPr) {
                        Log.v(TAG,
                                "ACT-reset the process max/restore status for app : "
                                        + obj.mAr.app);
                        obj.mAr.app.inMaxOrRestore = false;
                    } else {
                        Log.d(TAG, "obj.mAr.app != obj.mPr, obj.mAr.app:"
                                + obj.mAr.app + ", obj.mPr:" + obj.mPr);
                    }
                }
                break;
            }

        }
    };

    /**
     * When running startActivityLocked at ActivityStackSupervisor.java the function
     * should be called to check the activity should be blocked or not
     * 
     * @param processName :
     * @param callerApp :
     * @param nextIntent :
     * @param processName :
     * @param outRunningAc :
     * @return
     */
    public int blockByRunningActivity(String processName,
            ProcessRecord callerApp, Intent nextIntent,
            ActivityRecord[] outRunningAc, ActivityInfo aInfo) {
        if (DEBUG) {
            Log.d(TAG, "blockByRunningActivity processName = " + processName
                    + ", callerApp = " + callerApp + ", nextIntent = "
                    + nextIntent);
        }

        /// [ALPS02039971], For app activity start by same process , do not block it.
        mCallerApp = callerApp;
        
        String nextComponentName = nextIntent.getComponent()
                .flattenToShortString();
        int N = mRunningActivity.size();
        for (int i = N - 1; i >= 0; i--) {
            ActivityRecord runningAc = mRunningActivity.get(i);
            outRunningAc[0] = runningAc;

            int runningFlag = runningAc.intent.getFlags();
            int nextFlag = nextIntent.getFlags();
            boolean isRunningFloat = false;
            boolean isNextFloat = false;
            if ((runningFlag & Intent.FLAG_ACTIVITY_FLOATING) != 0) {
                isRunningFloat = true;
            }
            if ((nextFlag & Intent.FLAG_ACTIVITY_FLOATING) != 0) {
                isNextFloat = true;
            }

            // / if the 2 activities are not the floating window, it
            // / should be launched
            if (isNextFloat == false && isRunningFloat == false) {
                return NG_DONOT_BLOCK;
            }

            // / if the caller process is same as the monitor
            // / acitivyt, it also shoiuld be launched ALPS01457602
            if (callerApp != null
                    && runningAc.processName.equals(callerApp.processName)) {
                // / The acitivty can be launched without the new task flag
                /*
                 * if (isNextFloat == true && isMonitorFloat == true) {
                 * r.intent.setFlags(r
                 * .intent.getFlags()&~(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                 */
                Log.v(TAG, "blockByRunningActivity : "
                        + " caller apps are same " + Debug.getCallers(4));

                return NG_SAME_CALLER_APP;
            }

            if (nextComponentName.contains(runningAc.shortComponentName)) {
                Log.v(TAG,
                        "blockByRunningActivity : "
                                + " 2 shortComponentNames are same "
                                + Debug.getCallers(4));
                return NG_SAME_COMPONENT_NAME;
            }

            if (runningAc.processName != null
                    && runningAc.processName.equals(processName)
                    && runningAc.state == ActivityState.RESUMED) {
                // / M: The acitivtys' task will be moved to front.
                if (runningAc.task.stack.findActivityLocked(nextIntent, aInfo) != null) {
                    Log.v(TAG, "blockByRunningActivity : "
                            + " 3 exist same activity in resumed stack "
                            + Debug.getCallers(4));
                    return NG_SAME_TASK;

                } else {
                    // / if the above conditions are not satisfied, it
                    // / should not be launched.
                    Log.v(TAG, "blockByRunningActivity : " + processName
                            + " has activity for resumed : " + runningAc
                            + ". Therefore, don't be launched "
                            + ", callers = " + Debug.getCallers(4));
                    mMwHandler
                            .sendEmptyMessage(SHOW_ONE_ACTIVITY_AT_GALLERY3D_MSG);
                    return OK_BLOCK_ACTIVITY;
                }
            }
        }
        return NG_DONOT_BLOCK;
    }

    /**
     * When running startActivityLocked at ActivityStackSupervisor.java the function
     * should be called to check the floating activity should be blocked or not.
     * 
     * @param r : If null, identify condition only considers the stack size Otherwise,
     *            condition is more complex. Please see the code.
     * @return OK_BLOCK_ACTIVITY : Block the activity NG_DONOT_BLOCK : Don't block the
     *         activity
     */
    public int blockByFloatingStackSize(ActivityRecord r) {
        boolean blocked = true;
        ArrayList<ActivityStack> frontFloatingStacks, backFloatingStacks;
        int frontStackSize, backStackSize;
        int noTopActvityInFrontStackSize = 0;

        frontFloatingStacks = mService.mStackSupervisor.getFrontFloatStacks(Display.DEFAULT_DISPLAY);
        frontStackSize = frontFloatingStacks.size();
        backFloatingStacks = mService.mStackSupervisor.getBackFloatStacks(Display.DEFAULT_DISPLAY);
        backStackSize = backFloatingStacks.size();

        Log.d(TAG, "blockByFloatingStackSize floating stack size = "
                + frontStackSize);

        if (frontStackSize >= mMaximumFrontFloatingSize) {
            // / if r is null, we need to block the activity
            // / Otherwise, need to condier the activity is at the stack or not.
            if (r != null) {
                if ((r.intent.getFlags() & Intent.FLAG_ACTIVITY_FLOATING) != 0) {
                    // / Try to find the same activity record at the front stack size.
                    // / if found, don't block
                    for (int i = frontStackSize - 1; i >= 0; i--) {
                        ActivityRecord ar = frontFloatingStacks.get(i)
                                .findTaskLocked(r);
                        if (ar != null) {
                            blocked = false;
                            break;
                        }
                    }

                    /// [ALPS02039971] ,If APP using to activity to start it main UI,we can not block it.
                    /// e.g. Backup and Restore, MMS .
                    for (int n = frontStackSize - 1; n >= 0; n--) {
                        ActivityRecord ar = frontFloatingStacks.get(n).topActivity();
                        if (ar != null) {
                            if (r.processName.equals(ar.processName)) {
                                blocked = false;
                                break;
                            }  
                            // for specia apk, activity in different process. eg. Downloads apk.
                            if ("com.android.documentsui.DocumentsActivity".equals(r.info.name)
                                    && "com.android.providers.downloads.ui.DownloadList".equals(ar.info.name)) {
                                blocked = false;
                                break;
                            }
                        } else {
                            noTopActvityInFrontStackSize ++;        
                        }   
                    }
                    
                    /// [ALPS02063467],  If topActivity is null, activity is in pending finish list. So  its stack is not in front later.
                    if (frontStackSize - noTopActvityInFrontStackSize < mMaximumFrontFloatingSize) {
                        blocked = false;
                    }
                       
                    /// [ALPS02039971], If Caller APP and target APP in same process,do not blocking it.
                    if (mCallerApp != null && blocked
                            && r.processName.equals(mCallerApp.processName)) {
                        blocked = false;
                    }   

                    // / Try to find the same activity record at the back stack size.
                    // / if found, don't block.
                    for (int j = backStackSize - 1; j >= 0 && blocked; j--) {
                        ActivityRecord ar = backFloatingStacks.get(j)
                                .findTaskLocked(r);
                        if (ar != null) {
                            blocked = false;
                            break;
                        }
                    }
                } else {
                    // / normal activity should not be blocked.
                    blocked = false;
                }
            }
        } else {
            blocked = false;
        }

        if (blocked) {
            mMwHandler.sendEmptyMessage(SHOW_MAXIMUM_FRONT_FLOATING_MSG);
            return OK_BLOCK_ACTIVITY;
        }

        return NG_DONOT_BLOCK;
    }

    void dump(PrintWriter pw) {
        int N = mRunningActivity.size();
        if (N <= 0)
            return;

        pw.println("  MwActivityMonitor : ");
        for (int i = 0; i < N; i++) {
            ActivityRecord ac = mRunningActivity.get(i);
            pw.println("    " + ac);
        }
    }

    /**
     * When running moveActivityTaskToFloatingStackLocked or
     * moveFloatingStackToAppStackLocked at ActivityStackSupervisor.java , the function
     * should be called to reset processRecord flag inMaxOrRestore to false it will
     * delayed 15s
     * 
     * @param r : the activity`s processRecord will be restore
     * @return
     */
    public void resetProcessMiniMaxStatus(ActivityRecord r, ProcessRecord p) {
        Message msg = mMwHandler.obtainMessage(MSG_MW_MAX_RESTORE);
        msg.obj = new MessageObj(r, p);
        mMwHandler.sendMessageDelayed(msg, 15000);
    }

    /**
     * When running activityIdleInternalLocked at ActivityStackSupervisor.java , the
     * function should be called to update processRecord flag inMaxOrRestore to false when
     * this flag is true
     * 
     * @param r : the activity`s processRecord will be updated
     * @return
     */
    public void updateProcessMiniMaxStatus(ActivityRecord r) {
        mMwHandler.removeMessages(MSG_MW_MAX_RESTORE, r);
    }

    private class MessageObj {
        private ActivityRecord mAr;
        private ProcessRecord mPr;

        MessageObj(ActivityRecord activityRecord, ProcessRecord processRecord) {
            mAr = activityRecord;
            mPr = processRecord;
        }
    }
}

