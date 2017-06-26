/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2014. All rights reserved.
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

package com.mediatek.gallery3d.video;

import android.content.Intent;
import android.content.ActivityNotFoundException;

import android.net.Uri;
import android.widget.ActivityChooserView;
import android.widget.Toast;

import android.view.Menu;
import android.view.MenuItem;

import com.android.gallery3d.R;

import com.mediatek.gallery3d.ext.DefaultActivityHooker;
import com.mediatek.gallery3d.ext.IMovieItem;
import com.mediatek.gallery3d.ext.MovieUtils;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.hotknot.HotKnotAdapter;

public class HotKnotHooker extends DefaultActivityHooker {
    private static final String TAG = "Gallery2/VideoPlayer/HotKnotHooker";
    private static final int MENU_HOT_KNOT = 1;
    private MenuItem mMenutHotKnot;
    private IMovieItem mMovieItem;
    private HotKnotAdapter mHotKnotAdapter = null;
    private SlowMotionItem mSlowMotionItem;
    private TranscodeVideo mTranscodeVideo;
    private static final String ACTION_SHARE = "com.mediatek.hotknot.action.SHARE";
    private static final String EXTRA_SHARE_URIS = "com.mediatek.hotknot.extra.SHARE_URIS";

    @Override
    public void setParameter(final String key, final Object value) {
        super.setParameter(key, value);
        MtkLog.v(TAG, "setParameter(" + key + ", " + value + ")");
        if (value instanceof IMovieItem) {
            mMovieItem = (IMovieItem) value;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);
        if (MovieUtils.isLocalFile(mMovieItem.getUri(), mMovieItem.getMimeType())) {
            hotKnotUpdateMenu(menu, R.id.action_share, R.id.action_hotknot);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (mMenutHotKnot != null) {
            mMenutHotKnot.setVisible(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        super.onOptionsItemSelected(item);
        if (item.getItemId() == R.id.action_hotknot) {
            hotKnotStart();
        }
        return false;
    }
    
    @Override
    public void onDestroy() {
        if (mTranscodeVideo != null) {
            mTranscodeVideo.onDestrory();
        }
    }
    public void hotKnotUpdateMenu(Menu menu, int shareAction, int hotKnotAction) {
        if (menu == null) {
            MtkLog.v(TAG, "hotKnotUpdateMenu: menu is null");
            return;
        }
        mMenutHotKnot = menu.findItem(hotKnotAction);
        MenuItem shareItem = menu.findItem(shareAction);

        if (mMenutHotKnot != null && shareItem != null) {
            // remove the share history of shareItem from actionbar.
            ((ActivityChooserView) shareItem.getActionView())
                    .setRecentButtonEnabled(false);
            MtkLog.v(TAG, "hotKnotUpdateMenu, success");
        }
    }

    private boolean hotKnotStart() {
        MtkLog.v(TAG, "hotKnotStart");
        mSlowMotionItem = new SlowMotionItem(getContext(), mMovieItem.getUri());

        if (mSlowMotionItem.isSlowMotionVideo()) {
            if (mSlowMotionItem.getSpeed() == SlowMotionItem.SLOW_MOTION_ONE_SIXTEENTH_SPEED) {
                Toast.makeText(getContext().getApplicationContext(),
                        getContext().getString(R.string.not_support_share_hint_for_16x), Toast.LENGTH_LONG).show();
                return true;
            }
            mTranscodeVideo = new TranscodeVideo(getContext(), mMovieItem.getUri());
            Intent intent = new Intent(ACTION_SHARE);
            mTranscodeVideo.onHotKnotSelected(intent);
            return true;
        }

        Intent intent = new Intent(ACTION_SHARE);
        Uri uris[] = new Uri[1];
        uris[0] = Uri.parse(mMovieItem.getUri().toString() + "?show=yes");
        MtkLog.v(TAG, "hotKnotStart uris[0] " + uris[0]);
        intent.putExtra(EXTRA_SHARE_URIS, uris);

        try {
            getContext().startActivity(intent);
        } catch (ActivityNotFoundException e) {
            MtkLog.v(TAG, "HotKnot share activity not found!");
        }
        return true;
    }

}
