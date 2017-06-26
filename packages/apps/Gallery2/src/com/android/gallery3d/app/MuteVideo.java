/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.app;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.Media;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.util.SaveVideoFileInfo;
import com.android.gallery3d.util.SaveVideoFileUtils;
import com.mediatek.galleryfeature.config.FeatureConfig;

import com.mediatek.gallery3d.video.SlowMotionItem;
import java.io.IOException;

public class MuteVideo {

    private static final String TAG = "Gallery2/MuteVideo";
    private ProgressDialog mMuteProgress;

    private String mFilePath = null;
    private Uri mUri = null;
    private SaveVideoFileInfo mDstFileInfo = null;
    private Activity mActivity = null;
    private final Handler mHandler = new Handler();
    // /M:Add mute listener for slidevideo feature
    private MuteDoneListener mMuteDoneListener;
    
    private boolean mHasPaused = false;
    private boolean mPlayMuteVideo = false;
    private boolean mIsSaving = false;

    final String TIME_STAMP_NAME = "'MUTE'_yyyyMMdd_HHmmss";
    /// M: add for show mute error toast @{
        private final Runnable mShowToastRunnable = new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mActivity.getApplicationContext(),
                        mActivity.getString(R.string.video_mute_err),
                        Toast.LENGTH_SHORT)
                        .show();
            }
        };
    // /M: // After muting is done, trigger the UI changed.@{
    private final Runnable mTriggerUiChangeRunnable = new Runnable() {
        @Override
        public void run() {
            Log.v(TAG, "[mTriggerUiChangeRunnable] HasPaused:" + mHasPaused);
            if (!mHasPaused) {
                Toast.makeText(
                        mActivity.getApplicationContext(),
                        mActivity.getString(R.string.save_into,
                                mDstFileInfo.mFolderName), Toast.LENGTH_SHORT)
                        .show();
                if (mMuteProgress != null) {
                    mMuteProgress.dismiss();
                    mMuteProgress = null;
                    
                    // / M: [FEATURE.ADD] SlideVideo@{
                    if (FeatureConfig.supportSlideVideoPlay) {
                        String videoData = Uri.decode(mDstFileInfo.mFile
                                .toString());
                        String where = null;
                        if (videoData != null) {
                            videoData = videoData.replaceAll("'", "''");
                            where = "_data LIKE '%"
                                    + videoData.replaceFirst("file:///", "")
                                    + "'";
                        }
                        Cursor cursor = null;
                        try {
                            cursor = mActivity
                                    .getContentResolver()
                                    .query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                            new String[] { Media._ID }, where,
                                            null, null);
                            if (cursor != null && cursor.moveToFirst()) {
                                long id = cursor.getLong(0);
                                Uri baseUri = Video.Media.EXTERNAL_CONTENT_URI;
                                Uri uri = baseUri.buildUpon()
                                        .appendPath(String.valueOf(id)).build();
                                if (mMuteDoneListener != null) {
                                    mMuteDoneListener.onMuteDone(uri);
                                }
                            }
                        } finally {
                            if (cursor != null) {
                                cursor.close();
                                cursor = null;
                            }
                        }
                    } else {
                        // / @}
                        // Show the result only when the activity not stopped.
                        Intent intent = new Intent(
                                android.content.Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.fromFile(mDstFileInfo.mFile),
                                "video/*");
                        intent.putExtra(MediaStore.EXTRA_FINISH_ON_COMPLETION,
                                false);
                        mActivity.startActivity(intent);
                    }
                    mPlayMuteVideo = false;
                    mIsSaving = false;
                }
            } else {
                mPlayMuteVideo = true;
            }
        }
    };
    //@}
    public MuteVideo(String filePath, Uri uri, Activity activity) {
        mUri = uri;
        mFilePath = filePath;
        mActivity = activity;
        mIsSaving = false;
    }

    public void muteInBackground() {
        Log.v(TAG, "[muteInBackground]...");
        mDstFileInfo = SaveVideoFileUtils.getDstMp4FileInfo(TIME_STAMP_NAME,
                mActivity.getContentResolver(), mUri, null, false,
                mActivity.getString(R.string.folder_download));
        mIsSaving = true;

        showProgressDialog();
        new Thread(new Runnable() {
                @Override
            public void run() {
                try {
                    boolean isMuteSuccessful = VideoUtils.startMute(mFilePath, mDstFileInfo, mMuteProgress);
                    if (!isMuteSuccessful) {
                        Log.v(TAG, "[muteInBackground] mute failed");
                        mHandler.removeCallbacks(mShowToastRunnable);
                        mHandler.post(mShowToastRunnable);
                        mIsSaving = false;
                        if (mDstFileInfo.mFile.exists()) {
                            mDstFileInfo.mFile.delete();
                        }
                        return;
                    }
                    ///M: Get new video uri.
                    Uri newVideoUri = null;
                    newVideoUri = SaveVideoFileUtils.insertContent(
                            mDstFileInfo, mActivity.getContentResolver(), mUri);
                    ///M: If current video is slow motion, update slow motion info to db.
                    updateSlowMotionInfoToDBIfNeed(mActivity, mUri, newVideoUri);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // After muting is done, trigger the UI changed.
                Log.v(TAG, "[muteInBackground] post mTriggerUiChangeRunnable");
                mHandler.post(mTriggerUiChangeRunnable);
            }
        }).start();
    }

    /*
     * M: Update slow motion info to db, if current video is slow motion video.
     */
    private void updateSlowMotionInfoToDBIfNeed(final Context context, final Uri sourceVideoUri, final Uri newVideoUri) {
        //if current trimed video is slow motion video, update slow motion info to db.
        SlowMotionItem sourceItem = new SlowMotionItem(context, sourceVideoUri);

        if (sourceItem.isSlowMotionVideo()) {
            //When slow motion end time is equal to duration, correct the slow motion end time.
            int endTime = 0;
            //Get muted file duration.
            int duration = SaveVideoFileUtils.retriveVideoDurationMs(mDstFileInfo.mFile.getPath());
            if(duration < sourceItem.getSectionEndTime()) {
                endTime = duration * sourceItem.getSectionEndTime() / sourceItem.getDuration();
            } else {
                endTime = sourceItem.getSectionEndTime();
            }
            SlowMotionItem item = new SlowMotionItem(context, newVideoUri);
            item.setSectionStartTime(sourceItem.getSectionStartTime());
            item.setSectionEndTime(endTime);
            item.setSpeed(sourceItem.getSpeed());
            item.updateItemToDB();
        }
    }

    ///M:fix google bug
    // mute video is not done, when long press power key to power off,
    // muteVideo runnable still there run after gallery activity destoryed.@{
    public void cancelMute() {
        Log.v(TAG, "[cancleMute] mMuteProgress = " + mMuteProgress);
        if (mMuteProgress != null) {
            mMuteProgress.dismiss();
            mMuteProgress = null;
        }
    }
    //@}

    private void showProgressDialog() {
        mMuteProgress = new ProgressDialog(mActivity);
        mMuteProgress.setTitle(mActivity.getString(R.string.muting));
        mMuteProgress.setMessage(mActivity.getString(R.string.please_wait));
        mMuteProgress.setCancelable(false);
        mMuteProgress.setCanceledOnTouchOutside(false);
        mMuteProgress.show();
    }
    
    public void setMuteHasPaused(boolean paused) {
        Log.v(TAG, "[setMuteHasPaused] paused = " + paused);
        mHasPaused = paused;
    }
    
    public void needPlayMuteVideo() {
        Log.v(TAG, "[needPlayMuteVideo] mIsSaving = " + mIsSaving
                + ", mMuteProgress = " + mMuteProgress + ", mPlayMuteVideo = "
                + mPlayMuteVideo);
        if (mIsSaving) {
            if (mMuteProgress == null) {
                showProgressDialog();
            }
            if (mPlayMuteVideo) {
                mHandler.post(mTriggerUiChangeRunnable);
            }
        }
    }
  
    // / M: [FEATURE.ADD] SlideVideo@{
    public interface MuteDoneListener {
        public void onMuteDone(Uri uri);
    }
    
    public void setMuteDoneListener(MuteDoneListener l) {
        mMuteDoneListener = l;
    }
    // / @}

}
