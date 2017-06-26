/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2013. All rights reserved.
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

import android.content.Context;
import android.content.Intent;

import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.MediaStore;

import android.view.Menu;
import android.view.MenuItem;

import com.android.gallery3d.R;
import com.android.gallery3d.app.TrimVideo;
import com.android.gallery3d.app.PhotoPage;

import com.mediatek.gallery3d.ext.MovieUtils;
import com.mediatek.gallery3d.ext.IMovieItem;
import com.mediatek.galleryframework.util.MtkLog;

public class TrimVideoHooker extends MovieHooker {
    private static final String TAG = "Gallery2/VideoPlayer/TrimVideoHooker";
    private static final int MENU_TRIM_VIDEO = 1;
    private MenuItem mMenutTrim;
    private IMovieItem mMovieItem;
    private static final String VIDEO_CONTENT_MEDIA = "content://media/external/video/media";

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
        mMenutTrim = menu.add(MENU_HOOKER_GROUP_ID, getMenuActivityId(MENU_TRIM_VIDEO), 0,
                R.string.trim_action);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        //when play stream, there is no need to have trim option in menu.
        //when uri can not query from media store, eg: email, mms, not show trim.
        if (MovieUtils.isLocalFile(mMovieItem.getUri(), mMovieItem.getMimeType()) &&
            !MovieUtils.isLivePhoto(getContext(), mMovieItem.getUri()) &&
            MovieTitleHelper.isUriValid(getContext(), mMovieItem.getUri()) &&
            isUriSupportTrim(mMovieItem.getUri()) &&
            !isDrmFile(getContext(), mMovieItem.getUri())) {
            mMenutTrim.setVisible(true);
        } else {
            mMenutTrim.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (getMenuOriginalId(item.getItemId())) {
        case MENU_TRIM_VIDEO:
            // start activity
            Uri original = mMovieItem.getUri();
            MtkLog.v(TAG, "original=" + original);
            MtkLog.v(TAG, "path=" + getVideoPath(getContext(), original));
            Intent intent = new Intent(getContext(), TrimVideo.class);
            intent.setData(original);
            // We need the file path to wrap this into a RandomAccessFile.
            intent.putExtra(PhotoPage.KEY_MEDIA_ITEM_PATH,
                    getVideoPath(getContext(), original));
            getContext().startActivity(intent);
            //finish MovieActivity after start another one.
            getContext().finish();
            return true;
        default:
            return false;
        }
    }

    /**
     * Get Video path from db.
     */
    private String getVideoPath(final Context context, Uri uri) {
        String videoPath = null;
        Cursor cursor = null;
        MtkLog.v(TAG, "getVideoPath(" + uri + ")");
        try {
            //query from "content://....."
            cursor = context.getContentResolver().query(uri,
                    new String[] { MediaStore.Video.Media.DATA }, null, null,
                    null);
            //query from "file:///......"
            if (cursor == null) {
                String data = Uri.decode(uri.toString());
                if (data == null) {
                    return null;
                }
                data = data.replaceAll("'", "''");
                final String where = "_data LIKE '%" + data.replaceFirst("file:///", "") + "'";
                cursor = context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Video.Media.DATA}, where, null, null);
            }
            if (cursor != null && cursor.moveToFirst()) {
                videoPath = cursor.getString(0);
            }
        } catch (final SQLiteException ex) {
            ex.printStackTrace();
        } catch (IllegalArgumentException e) {
            // if this exception happen, return false.
            MtkLog.v(TAG, "ContentResolver query IllegalArgumentException");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return videoPath;
    }

    private boolean isDrmFile(final Context context, Uri uri) {
       Cursor cursor = null;
       int result = 0;
       MtkLog.v(TAG, "isDrmFile(" + uri + ")");
       try {
              cursor = context.getContentResolver().query(uri,
                    new String[]{MediaStore.Video.Media.IS_DRM}, null, null, null);

              if (cursor == null) {
                  String data = Uri.decode(uri.toString());
                  if (data == null) {
                      return false;
                  }
                  data = data.replaceAll("'", "''");
                  final String where = "_data LIKE '%" + data.replaceFirst("file:///", "") + "'";

                  cursor = context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                      new String[]{MediaStore.Video.Media.IS_DRM}, where, null, null);
                              }

              if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getInt(0);
            }
            } catch (final SQLiteException ex) {
                ex.printStackTrace();
            } catch (IllegalArgumentException e) {
                //if this exception happen, return false.
                MtkLog.v(TAG, "isDrmFile ContentResolver query IllegalArgumentException");
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
       MtkLog.v(TAG, "result = " + result);
       // "1" means query from db, this file is drm file.
       return (1 == result);

    }
    //trim support uri type:
    // 1. content://media/external/video/media
    // 2. filemanager uri
    private boolean isUriSupportTrim(Uri uri) {
        return String.valueOf(uri).toLowerCase().startsWith(VIDEO_CONTENT_MEDIA) ||
            String.valueOf(uri).toLowerCase().startsWith("file://");
    }
}
