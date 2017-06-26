/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.mediatek.settings;

import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Bundle;
import android.os.SystemProperties;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.android.settings.R;
import com.mediatek.galleryfeature.clearmotion.ClearMotionQualityJni;
import com.mediatek.xlog.Xlog;

import java.io.IOException;

public class ClearMotionSettings extends Activity implements
        CompoundButton.OnCheckedChangeListener, OnCompletionListener, OnPreparedListener,
        SurfaceHolder.Callback {
    private static final String TAG = "ClearMotionSettingsLog";

    private static final String KEY_CLEAR_MOTION = "clearMotion";
    private static final String KEY_DISPLAY_CLEAR_MOTION = "persist.sys.display.clearMotion";
    private static final String KEY_DISPLAY_CLEAR_MOTION_DIMMED = "sys.display.clearMotion.dimmed";
    private static final String ACTION_CLEARMOTION_DIMMED = "com.mediatek.clearmotion.DIMMED_UPDATE";
    private static final String CLEAR_MOTION_VIDEO_NAME = "clear_motion_video.mp4";

    private Switch mActionBarSwitch;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private MediaPlayer mMediaPlayer;
    private View mLineView;
    private TextView mOnText;
    private TextView mOffText;

    /**
     * add for clearMotion
     */
    private BroadcastReceiver mUpdateClearMotionStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context1, Intent intent) {
            Xlog.d(TAG, "mUpdateClearMotionStatusReceiver");

            updateClearMotionStatus();
            updateClearMotionDemo(mActionBarSwitch.isChecked());
            prepareVideo();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.video_view);

        initViews();
        // WindowManager.LayoutParams winParams = getWindow().getAttributes();
        // winParams.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
        // getWindow().setAttributes(winParams);
    }

    private void initViews() {
        mLineView = findViewById(R.id.line_view);
        mOnText = (TextView) findViewById(R.id.text_on);
        mOffText = (TextView) findViewById(R.id.text_off);
        mOnText.setBackgroundColor(Color.argb(155, 0, 0, 0));
        mOffText.setBackgroundColor(Color.argb(155, 0, 0, 0));

        mActionBarSwitch = new Switch(getLayoutInflater().getContext());
        final int padding = getResources().getDimensionPixelSize(R.dimen.action_bar_switch_padding);
        mActionBarSwitch.setPaddingRelative(0, 0, padding, 0);
        getActionBar().setDisplayOptions(
                ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_CUSTOM,
                ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_CUSTOM);
        getActionBar()
                .setCustomView(
                        mActionBarSwitch,
                        new ActionBar.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT,
                                ActionBar.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL
                                        | Gravity.END));
        getActionBar().setTitle(R.string.clear_motion_title);
        mActionBarSwitch.setOnCheckedChangeListener(this);
        updateClearMotionStatus();
        mSurfaceView = (SurfaceView) findViewById(R.id.surface_view);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        getActionBar().setCustomView(null);
        super.onDestroy();
    }

    @Override
    public void onResume() {
	if(getIntent().getAction()!=null){
        	updateClearMotionDemo(mActionBarSwitch.isChecked());
        	updateClearMotionDemo(mActionBarSwitch.isChecked());
        	registerReceiver(mUpdateClearMotionStatusReceiver, new IntentFilter(
                	ACTION_CLEARMOTION_DIMMED));
	}else{
		super.onResume();
	}
    }

    private void updateClearMotionStatus() {
        if (mActionBarSwitch != null) {
            Xlog.d(TAG, "updateClearMotionStatus");
            mActionBarSwitch.setChecked(SystemProperties.get(KEY_DISPLAY_CLEAR_MOTION, "0").equals(
                    "1"));
            mActionBarSwitch.setEnabled(SystemProperties.get(KEY_DISPLAY_CLEAR_MOTION_DIMMED, "0")
                    .equals("0"));
        }
    }

    private void updateClearMotionDemo(boolean status) {
        Xlog.d(TAG, "updateClearMotionDemo status: " + status);
        mLineView.setVisibility(status ? View.VISIBLE : View.GONE);
        mOnText.setVisibility(status ? View.VISIBLE : View.GONE);
        mOffText.setVisibility(status ? View.VISIBLE : View.GONE);
        ClearMotionQualityJni.nativeSetDemoMode(status ? 3 : 0);
    }

    @Override
    public void onPause() {
        super.onPause();
	try{
        unregisterReceiver(mUpdateClearMotionStatusReceiver);
        ClearMotionQualityJni.nativeSetDemoMode(0);
	}catch(Exception e){
		e.printStackTrace();
		} 
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Xlog.d(TAG, "onCheckedChanged " + isChecked);
        SystemProperties.set(KEY_DISPLAY_CLEAR_MOTION, isChecked ? "1" : "0");
        updateClearMotionDemo(isChecked);
        prepareVideo();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Xlog.d(TAG, "surfaceCreated " + holder);
        mSurfaceHolder = holder;
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnCompletionListener(this);
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setOnErrorListener(mErrorListener);
        mMediaPlayer.setDisplay(mSurfaceHolder);
        prepareVideo();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Xlog.d(TAG, "surfaceChanged " + holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Xlog.d(TAG, "surfaceDestroyed " + holder);
        releaseMediaPlayer();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        Xlog.d(TAG, "onPrepared ");
        mMediaPlayer.start();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        Xlog.d(TAG, "onCompletion ");
        // cycle play
        mp.seekTo(0);
        mp.start();
    }

    /**
     * error listner, stop play video.
     */
    private OnErrorListener mErrorListener = new OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            Xlog.d(TAG, "play error: " + what);
            Xlog.d(TAG, "play error: " + extra);
            releaseMediaPlayer();
            return false;
        }
    };

    private void prepareVideo() {
        try {
            if (mMediaPlayer != null) {
                mMediaPlayer.reset();
                AssetFileDescriptor afd = getAssets().openFd(CLEAR_MOTION_VIDEO_NAME);
                Xlog.d(TAG, "video path = " + afd.getFileDescriptor());
                mMediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd
                        .getLength());
                afd.close();
                mMediaPlayer.prepare();
                // resizeSurfaceView();
                Xlog.d(TAG, "mMediaPlayer prepare()");
            }
        } catch (IOException e) {
            Xlog.e(TAG, "unable to open file; error: " + e.getMessage(), e);
            releaseMediaPlayer();
        } catch (IllegalStateException e) {
            Xlog.e(TAG, "media player is in illegal state; error: " + e.getMessage(), e);
            releaseMediaPlayer();
        }
    }

    private void releaseMediaPlayer() {
        Xlog.d(TAG, "releaseMediaPlayer");
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }
}
