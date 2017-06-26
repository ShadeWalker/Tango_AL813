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

package com.mediatek.dialer.ext;

import java.util.List;

import android.app.Activity;
import android.app.ListFragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.telecom.PhoneAccountHandle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.HorizontalScrollView;
import android.widget.ListView;

import com.android.dialer.calllog.ContactInfo;

public class DefaultCallLogExtension implements ICallLogExtension {
    private static final String TAG = "DefaultCallLogExtension";

    /**
     * for OP01 OP09
     * @param context
     * @param fragment
     */
    public void onCreateForCallLogFragment(Context context, ListFragment fragment) {
        log("onCreate");
    }

    /**
     * for OP09
     * @param view
     * @param savedInstanceState
     */
    public void onViewCreatedForCallLogFragment(View view, Bundle savedInstanceState) {
        log("onViewCreated");
    }

    /**
     * for OP09 OP01
     */
    public void onDestroyForCallLogFragment() {
        log("onDestroy");
    }

    /**
     * for OP09
     * Called when calllist item clicked
     * @param l
     * @param v
     * @param position
     * @param id
     * @return
     */
    public boolean onListItemClickForCallLogFragment(ListView l, View v, int position, long id) {
        log("onListItemClick");
        return false;
    }

    /**
     * for OP09
     * @param menu
     * @param view
     * @param menuInfo
     * @return
     */
    public boolean onCreateContextMenuForCallLogFragment(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        log("onCreateContextMenu");
        return false;
    }

    /**
     * for OP09
     * @param item
     * @return
     */
    public boolean onContextItemSelectedForCallLogFragment(MenuItem item) {
        log("onContextItemSelected");
        return false;
    }


    /**
     * for OP09
     * @param itemView
     * @param contactInfo
     * @param callDetailIntent
     * @return
     */
    public void setListItemViewTagForCallLogAdapter(View itemView, ContactInfo contactInfo, Intent callDetailIntent) {
        log("setListItemViewTag");
    }

    /**
     * for OP09
     * @param context
     * @param contactInfo
     */
    public void bindViewPreForCallLogAdapter(Context context, ContactInfo contactInfo) {
        log("bindViewPre");
    }

    /**
     *for OP09
     * get sim name by sim id
     *
     * @param simId from datebase
     * @param callDisplayName the default StringBuffer of display name plug-in should change it
     */
    public void updateSimDisplayNameById(int simId, StringBuffer callDisplayName) {
        log("getSimDisplayNameById");
    }

    /**
     * for OP09
     * get sim color drawable by sim id
     *
     * @param simId form datebases
     * @param simBackground simBackgroud[0] is the default value of sim color drawable, plugin should replace it
     */
    public void updateSimColorDrawable(int simId, Drawable[] simBackground) {
        log("getSimColorDrawableById");
    }

    /**
     *for OP09
     * set account for call log list
     *
     * @param Context context
     * @param View view
     * @param PhoneAccountHandle phoneAccountHandle
     */
    public void setCallAccountForCallLogList(Context context, View view, PhoneAccountHandle phoneAccountHandle) {
        log("setCallAccountForCallLogList");
    }

    /**
     * for op01
     * @param typeFiler current query type
     * @param builder the query selection Stringbuilder
     * @param selectionArgs the query selection args, modify to change query selection
     */
    @Override
    public void appendQuerySelection(int typeFiler, StringBuilder builder, List<String> selectionArgs) {
        log("appendQuerySelection");
    }


    /**
     * for op01
     * called when home button in actionbar clicked
     * @param pagerAdapter the view pager adapter used in activity
     * @param menu the optionsmenu itmes
     * @return true if do not need further operation in host
     */
    @Override
    public boolean onHomeButtonClick(FragmentPagerAdapter pagerAdapter, MenuItem menu) {
        log("onHomeButtonClick");
        return false;
    }

    /**
     * for op01
     * called when host create menu, to add plug-in own menu here
     * @param menu
     * @param tabs the ViewPagerTabs used in activity
     * @param callLogAction callback plug-in need if things need to be done by host
     */
    @Override
    public void createCallLogMenu(Activity activity, Menu menu, HorizontalScrollView tabs,
            ICallLogAction callLogAction) {
        log("createCallLogMenu");
    }

    /**
     * for op01
     * @param menu
     */
    @Override
    public void prepareCallLogMenu(Menu menu) {
        log("prepareCallLogMenu");
    }

    /**
     * for op01
     * called when updating tab count
     * @param count
     * @return tab count
     */
    @Override
    public int getTabCount(int count) {
       log("getTabCount");
       return count;
    }

    /**
     * for op01
     * @param savedInstanceState the save instance state
     * @param pagerAdapter the view pager adapter used in activity
     * @param tabs the ViewPagerTabs used in activity
     */
    public void restoreFragments(Context context, Bundle savedInstanceState,
            FragmentPagerAdapter pagerAdapter, HorizontalScrollView tabs) {
        log("restoreFragments");
    }

    /**
     * for op01
     * @param outState save state
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        log("onSaveInstanceState");
    }

    @Override
    public void onBackPressed(FragmentPagerAdapter pagerAdapter, ICallLogAction callLogAction) {
        log("prepareCallLogMenu");
        callLogAction.processBackPressed();
    }

    private void log(String msg) {
        Log.d(TAG, msg + " default");
    }

	/**
     * for op01
     * plug-in set position
     * @param position to set
     */
    @Override
	public void setPosition(int position) {
		//default do nothing
	}

	/**
     * for op01
     * plug-in get current position
     * @param position
     * @return get the position
     */
    @Override
	public int getPosition(int position) {
		return position;
	}

	
}
