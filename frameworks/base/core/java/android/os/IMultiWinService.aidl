/*< MW Integration : DTS2015041101215 Magesh/mwx209439 20150411 begin */
/*
**
** Copyright 2007, The Android Open Source Project
** Copyright (c) 2010, Code Aurora Forum. All rights reserved.
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package android.os;

import android.content.Intent;
import android.graphics.Rect;

/*< MW Integration : Launcher New Requirement mWX209439/Magesh 20150120 begin */
import android.os.IMultiWinServiceCallBack;
/*< MW Integration : Launcher New Requirement mWX209439/Magesh 20150120 end > */
/* < MW Integration: DTS2014091108716 abhishek/a00263523 20140917 begin */
import java.util.List;
/*  MW Integration: DTS2014091108716 abhishek/a00263523 20140917 end > */
/**
 * @hide
 */

interface IMultiWinService {
    boolean isMultiWin(IBinder token);
    /* <MW Integration : DTS2014092407798 : twx206827/Tapu 20140930 start */
    /*< MW Integration : DTS2014090203461 Archana/A00237416 20140912 begin */
    int registerMultiWin(IBinder token, inout Intent intent, IBinder caller);
    /* MW Integration : DTS2014090203461 Archana /A00237416 20140912 end > */
    /* <MW Integration : DTS2014092407798 : twx206827/Tapu 20140930 end> */
    void releaseMultiWin(IBinder token);
    void getMultiWinFrame(IBinder token, inout Rect vf);
    void setMultiWinResumed(IBinder token, boolean resumed);
    int updateMultiWinPosition(IBinder token, in Intent intent, IBinder caller);
    int setMultiWinSoftKeyBoardVisible(IBinder token, int height);
    void setMultiWinSoftKeyBoardInvisible(IBinder token);
    int getMultiWinCount();
    boolean isPartOfMultiWindow(int taskID);
    boolean isRootIntent(in Intent intent);
    void clearAllTokens();
    boolean isCloseChangeViewVisible();
    void setCloseChangeViewInvisible();
    void changeFrameSize(int incDec);
    int checkAndHandleLaunchOnWhiteListApp(inout Intent intent,  in Intent multiWindowIntent, IBinder token);
    /*< MW Integration : DTS2014060700188 Gauri/G00755779 20140726 begin */
    String[] getResumedActivities();
    /* MW Integration : DTS2014060700188 Gauri/G00755779 20140726 end >*/
    /*< MW Integration : DTS2014060607987 Archana/A00237416 20140806 begin */
    void setMWOtherTaskID(IBinder token, int taskID);
    /* MW Integration : DTS2014060607987 Archana/A00237416 20140806 end >*/
    /* <DTS2014090409897 mwx209439/Magesh 20140906 begin */
    /* <MW Integration : DTS2014091303612 : twx206827/Tapu 20140913 begin */
    int getFocusMultiWinFrame(inout Rect vf);
    /* <MW Integration : DTS2014091303612 : twx206827/Tapu 20140913 end> */
    /* DTS2014090409897 mwx209439/Magesh 20140906 end> */
    /* MW Integration : DTS2014090902772 mwx209439/Magesh 20140909 begin */
    boolean isLauncherVisible();
    void setLauncherVisibility(boolean visibility);
    /* MW Integration : DTS2014090902772 mwx209439/Magesh 20140909 end> */
    /* < DTS2014091507065 Syma/s71855 15092014 begin */
    boolean getMWMaintained();
    /* DTS2014091507065 Syma/s71855 15092014 end > */
    /* < MW Integration: DTS2014091108716 abhishek/a00263523 20140917 begin */
    List<String> getMWAppNames();
    List<String> getMWNonSystemAppNames();
    boolean isWhitelisted(String packageName);
    /*  MW Integration: DTS2014091108716 abhishek/a00263523 20140917 end > */
    // Task changes start - Archana 20141016
    void releaseTokens(int aTaskId);
    void moveTask(int aTaskId, boolean aTop, int aIndex);
    void removeFromTaskList(int aTaskId);
    void updateTaskList(int aTaskId, int aPosition);
    int getNextTaskAtPos(int aPosition, int aFinishingTask);
    // Task changes end - Archana 20141016
    //dayananda/D00248762 20141018 MW Task change begin
    int[] getResumedTasks();
    //dayananda/D00248762 20141018 MW Task change end
    /*< MW Integration : DTS2014103107510 Archana/A00237416 20141103 begin */
    boolean hasMwSession();
    /* MW Integration : DTS2014103107510 Archana/A00237416 20141103 end >*/
    /*< MW Integration : Launcher New Req dayananda/D00248762 20150102 begin */
    int getTaskPosition(int taskId);
    /* MW Integration : Launcher New Req dayananda/D00248762 20150102 end > */
    /*< MW Integration : DTS2014080502318 Archana A T/237416 20141114 begin */
    void updateMultiWinFrame(in Rect vf);
    void updateFrameVisibility(boolean visible, boolean isDummyFrame);
    /* MW Integration : DTS2014080502318 Archana A T/237416 20141114 end >*/
    /*< MW Integration : Launcher New Req mWX209439/Magesh 20150120 begin */
    boolean registerMWCallBack(in IMultiWinServiceCallBack aReference);
    boolean unregisterMWCallBack(in IMultiWinServiceCallBack aReference);
    /* MW Integration : Launcher New Req mWX209439/Magesh 20150120 end > */
    /*< MW Integration : DTS2015011909296 dayananda/D00248762 20150123 begin */
    Intent buildMultiWinIntent(in Intent intent);
    /* MW Integration : DTS2015011909296 dayananda/D00248762 20150123 end >*/
    /*< MW Integration : Launcher New Req mWX209439/Magesh 20150128 begin */
    int getTaskCountAtPos(int aMWPosition);
    /* MW Integration : Launcher New Req mWX209439/Magesh 20150128 end > */
    /*< DTS2015012906066 dayananda/D00248762 20150131 begin */
    void clearMultiWinTaskList();
    /* DTS2015012906066 dayananda/D00248762 20150131 end >*/
    /*< DTS2015012911135 dayananda/D00248762 20150203 begin */
    int getMultiWinFramePosition(IBinder token);
    /* DTS2015012911135 dayananda/D00248762 20150203 end >*/
    /*< DTS2015011906851 dayananda/D00248762 20150206 begin */
    boolean isPartOfMWTaskStack(int aTaskId);
    /* DTS2015011906851 dayananda/D00248762 20150206 end >*/
    /*< DTS2015022703492 dayananda/D00248762 20150227 begin */
    boolean isMultiWinNotificationIntent(int launchFlag);
    /* DTS2015022703492 dayananda/D00248762 20150227 end >*/
    /* MW Integration : DTS2015033001926  kaibalya/kwx277426 20150413 start >*/
    boolean isResumedMultiWin(IBinder token);
    /* MW Integration : DTS2015033001926  kaibalya/kwx277426 20150413 end >*/
    /* < DTS2015042904951 Magesh/mwx209439 20150506 begin */
    int getIsTopMWFlag();
    int getTopAreaLaunchFlag();
    int getRootMWFlag();
    int getFlagMW();
    /* DTS2015042904951 Magesh/mwx209439 20150506 end > */
}
/* MW Integration : DTS2015041101215 Magesh/mwx209439 20150411 end >*/
