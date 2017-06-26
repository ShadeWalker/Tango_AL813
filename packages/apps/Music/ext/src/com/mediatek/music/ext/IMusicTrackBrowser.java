/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2012. All rights reserved.
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
package com.mediatek.music.ext;

import android.view.Menu;
import android.view.MenuItem;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.app.Activity;


public interface IMusicTrackBrowser {
    /**
     * when plugin want to add
     * ADD_FOLDER_TO_PLAY,ADD_FOLDER_AS_PLAYLIST,ADD_SONG_TO_PLAY,ADD_SONG,
     * CLEAR_PLAYLIST menu item should realize this interface.
     *
     * @param menu is Menu object you want to add menu
     * @param activityName used to decide what kind of menu you want to add at
     *            this activity
     * @param playlistName used to juge which playlist shoud creat
     *            CLEAR_PLAYLIST menu item
     */
    public void onCreateOptionsMenuForPlugin(Menu menu, String activityName, Bundle options);

    /**
     * when plugin want to add
     * ADD_FOLDER_TO_PLAY,ADD_FOLDER_AS_PLAYLIST,ADD_SONG_TO_PLAY,ADD_SONG,
     * CLEAR_PLAYLIST menu item should realize this interface.
     *
     * @param menu is Menu object you want to add menu
     * @param activityName used to decide what kind of menu you want to add at
     *            this activity
     * @param currentTab used to juge which playlist shoud creat menu item
     */
    public void onPrepareOptionsMenuForPlugin(Menu menu, String activityName, Bundle options);

    /**
     * when plugin want to add
     * ADD_FOLDER_TO_PLAY,ADD_FOLDER_AS_PLAYLIST,ADD_SONG_TO_PLAY,ADD_SONG,
     * CLEAR_PLAYLIST menu item should realize this interface.
     *
     * @param context
     * @param item used to get the item info
     * @param activityName used to decide what kind of menu item selected action
     *            you shoudl deal at this activity
     */
    public boolean onOptionsItemSelectedForPlugin(Context context, MenuItem item, String activityName, Activity activity, PluginUtils.IMusicListenter musicListener, Bundle options);

    /**
     * when plugin need to handle activity result which trigge by option menu
     * selected should realize this interface.
     *
     * @param requestCode can used to decide which action should do
     * @param resultCode
     * @param intent contain useful info for plugin
     * @param context
     * @param musicListener is used to call host method by plugin
     * @param activityName used to decide what kind of menu item selected action
     *            you shoudl deal at this activity
     */
    public void onActivityResultForPlugin(int requestCode, int resultCode, Intent intent, Context context,
            PluginUtils.IMusicListenter musicListener, String activityName, Activity activity, Bundle options);
}
