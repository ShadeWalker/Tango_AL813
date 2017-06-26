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

package com.android.systemui.floatpanel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;

import java.util.List;

import com.mediatek.xlog.Xlog;
import android.content.ActivityNotFoundException;

/**
 * M: The entrance bar and more area in multi-window entrance.
 */
public class FloatPanelView extends LinearLayout {
    private static final String TAG = "FloatPanelView";
    private static final boolean DEBUG = true;

    protected static final int MSG_COMMIT_MODIFY = 0;

    private Context mContext;
    private BaseStatusBar mBar;
    private LinearLayout mExtentContainer;
    private CustomizedHorizontalScrollView mResidentScrollView;
    private DragSortGridView mResidentView;
    private DragSortGridView mExtentView;
    private FloatModel mFloatModel;
    private FloatAppAdapter mResidentAdapter;
    private FloatAppAdapter mExtentAdapter;
    private Handler mHandler;
    private boolean mDetached;
    private boolean mInExtensionMode;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                if (DEBUG) {
                    Xlog.d(TAG,
                            "BroadcastReceiver screen off and toggleFloatPanel.");
                }
                mBar.toggleFloatPanelScreenOff(); ///* M:ALPS01454734 Icon of multi window display on lock screen,toggleFloatPanel() based*/
            }
        }
    };

    public FloatPanelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FloatPanelView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mContext = context;
        mFloatModel = new FloatModel(FloatPanelView.this);
        HandlerThread completeThread = new HandlerThread(
                "float complete commit");
        completeThread.start();
        mHandler = new Handler(completeThread.getLooper());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mDetached = false;
        Xlog.d(TAG, "onFinishInflate: this = " + this);
        mExtentContainer = (LinearLayout) findViewById(R.id.extent_panel);
        mExtentContainer.setVisibility(View.INVISIBLE);

        mResidentScrollView = (CustomizedHorizontalScrollView) findViewById(R.id.resident_container);
        mResidentView = (DragSortGridView) findViewById(R.id.resident_grid);
        mExtentView = (DragSortGridView) findViewById(R.id.extent_grid);

        mResidentAdapter = new FloatAppAdapter(mContext, mFloatModel,
                FloatModel.RESIDENT_CONTAINER);
        mResidentView.setEnableStrechMode(true);
        mResidentView.setEnableVerticalDragScroll(true);
        mResidentView.setAdapter(mResidentAdapter);

        mResidentView.setColumnWidth(mContext.getResources()
                .getDimensionPixelSize(R.dimen.gridview_column_width));
        mResidentView.setOnReorderingListener(mFloatDragSortListener);
        mResidentView.setOnItemClickListener(mResidentItemClickListener);

        mExtentAdapter = new FloatAppAdapter(mContext, mFloatModel,
                FloatModel.EXTENT_CONTAINER);
        mExtentView.setAdapter(mExtentAdapter);
        mExtentView.setOnReorderingListener(mUnFloatDragSortListener);
        mExtentView.setOnItemClickListener(mExtentItemClickListener);

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(mReceiver, filter);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        if (DEBUG) {
            Xlog.d(TAG,
                    "onConfigurationChanged : " + newConfig.orientation
                            + ", mFloatedView childcount = "
                            + mResidentView.getChildCount()
                            + ", adapter count = "
                            + mResidentAdapter.getCount() + ", mFloatedView = "
                            + mResidentView + ", vis = "
                            + mResidentView.getVisibility()
                            + ", mFloatedView parent = "
                            + mResidentView.getParent());
        }
        mResidentView.setColumnWidth(mContext.getResources()
                .getDimensionPixelSize(R.dimen.gridview_column_width));
        mExtentView.setColumnWidth(mContext.getResources()
                .getDimensionPixelSize(R.dimen.gridview_column_width));
        mExtentView.setVerticalSpacing(mContext.getResources()
                .getDimensionPixelSize(R.dimen.gridview_vertical_spacing));
       //when the screen orientation change, the float button should show consistent.
        mBar.updateFloatButtonIcon(!isShown());
        if (mInExtensionMode) {
            mBar.setExtensionButtonVisibility(View.INVISIBLE);
        }
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (DEBUG) {
            Xlog.d(TAG, "onDetachedFromWindow........");
        }
        mDetached = true;
        mContext.unregisterReceiver(mReceiver);
    }

    public void setBar(BaseStatusBar bar) {
        mBar = bar;
    }

    public void enterExtensionMode() {
        mInExtensionMode = true;
        mExtentContainer.setVisibility(View.VISIBLE);
        mBar.setExtensionButtonVisibility(View.INVISIBLE);
    }

    public void setFloatContainerVisible(int visible) {
        if (mExtentContainer != null) {
            mExtentContainer.setVisibility(visible);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        final int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            mBar.cancelCloseFloatPanel();
        } else if (action == KeyEvent.ACTION_UP) {
            mBar.postCloseFloatPanel();
        }

        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (action == KeyEvent.ACTION_DOWN) {
                Xlog.d(TAG, "Back key down, toggleFloatPanel...");
                mBar.toggleFloatPanel();
            }
            return super.dispatchKeyEvent(event);
        } else {
            return super.dispatchKeyEvent(event);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        final int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            mBar.cancelCloseFloatPanel();
        } else if (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL) {
            mBar.postCloseFloatPanel();
        }

        if (mDetached) {
            return super.dispatchTouchEvent(event);
        }

        int x = (int) event.getX();
        int y = (int) event.getY();
        if ((event.getAction() == MotionEvent.ACTION_DOWN)
                && ((x < 0) || (x >= getWidth()) || (y < 0) || (y >= getHeight()))) {
            mBar.changgeFloatPanelFocus(false);
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            mBar.changgeFloatPanelFocus(false);
            return true;
        } else {
            mBar.changgeFloatPanelFocus(true);
            return super.dispatchTouchEvent(event);
        }
    }

    @Override
    public boolean dispatchDragEvent(DragEvent event) {
        final int action = event.getAction();
        if (action == DragEvent.ACTION_DRAG_STARTED) {
            mBar.cancelCloseFloatPanel();
        } else if (action == DragEvent.ACTION_DRAG_ENDED) {
            mBar.postCloseFloatPanel();
        }

        return super.dispatchDragEvent(event);
    }

    public void refreshUI() {
        if (mResidentAdapter != null) {
            mResidentAdapter.notifyDataSetChanged();
        }
        if (mExtentAdapter != null) {
            mExtentAdapter.notifyDataSetChanged();
        }
    }

    private void startFloatActivity(FloatAppAdapter adapter, int position) {
	//M:add try-catch avoid ActivityNotFoundException	
	try { 
        Intent intent = adapter.intentForPosition(position);
		if(intent == null) return;
        String packageName = intent.getPackage();
        String className = intent.getComponent().getClassName();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
			    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED 
                | Intent.FLAG_ACTIVITY_FLOATING
                );
        Xlog.d(TAG, "onItemClick: position = " + position + ",packageName = "
                + packageName + ", className = " + className);
		Xlog.d(TAG, "onItemClick: intent = " + intent);
		/// M:ALPS01891697,setPackage(null),avoid press two times back key can exit @{
		intent.setPackage(null);
		///@}
        mContext.startActivity(intent);
        } catch (ActivityNotFoundException e) {
           Xlog.d(TAG, "startFloatActivity,ActivityNotFoundException");
        }
        mBar.closeFloatPanel();
    }

    private DragSortGridView.OnReorderingListener mFloatDragSortListener = new DragSortGridView.OnReorderingListener() {

        @Override
        public void onReordering(int fromPosition, int toPosition) {
            Xlog.w(TAG, "onReordering gridView fromPosition:" + fromPosition
                    + ",toPosition:" + toPosition);
            mResidentAdapter.reorder(fromPosition, toPosition);
            mFloatModel.commitModify();
        }

        @Override
        public void onItemSwitched(int switchedPosition) {
            Xlog.d(TAG, "onItemSwitched: switchedPosition = "
                    + switchedPosition);
            mExtentAdapter.removeItem(switchedPosition);
        }
    };

    private DragSortGridView.OnReorderingListener mUnFloatDragSortListener = new DragSortGridView.OnReorderingListener() {

        @Override
        public void onReordering(int fromPosition, int toPosition) {
            Xlog.d(TAG, "onReordering gridViewHorizontal fromPosition:"
                    + fromPosition + ",toPosition:" + toPosition);
            mExtentAdapter.reorder(fromPosition, toPosition);
            mFloatModel.commitModify();
        }

        public void onItemSwitched(int switchedPosition) {
            Xlog.d(TAG,
                    "onItemSwitched gridViewHorizontal: switchedPosition = "
                            + switchedPosition);
            mResidentAdapter.removeItem(switchedPosition);
        }
    };

    private final OnItemClickListener mResidentItemClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                long id) {
            startFloatActivity(mResidentAdapter, position);
        }
    };

    private final OnItemClickListener mExtentItemClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                long id) {
            startFloatActivity(mExtentAdapter, position);
        }
    };

    /** Add for the AT case */ 
    public boolean isAppInstall(String packageName, String className) {
        Xlog.d(TAG, "isAppInstall, packageName =" + packageName + "className ="
                + className);
        if (mExtentAdapter != null) {
            List<FloatAppItem> activityList = mExtentAdapter.getActivityList();
            for(int i = 0; i<activityList.size(); i++){
                FloatAppItem item = activityList.get(i);
                if (packageName.equals(item.packageName)
                        && className.equals(item.className)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isAppUnInstall(String packageName, String className) {
        Xlog.d(TAG, "isAppInstall, packageName =" + packageName + "className ="
                + className);
        if (mExtentAdapter != null) {
            List<FloatAppItem> activityList = mExtentAdapter.getActivityList();
            for (int i = 0; i < activityList.size(); i++) {
                FloatAppItem item = activityList.get(i);
                if (packageName.equals(item.packageName)
                        && className.equals(item.className)) {
                    return false;
                }
            }
        }
        if (mResidentAdapter != null) {
            List<FloatAppItem> activityList = mResidentAdapter
                    .getActivityList();
            for (int i = 0; i < activityList.size(); i++) {
                FloatAppItem item = activityList.get(i);
                if (packageName.equals(item.packageName)
                        && className.equals(item.className)) {
                    return false;
                }
            }
        }
        return true;
    }
    
    public int getDefaultResidentAppNum(){
        if (mResidentAdapter != null) {
            List<FloatAppItem> activityList = mResidentAdapter
                    .getActivityList();
            return activityList.size();
        }
        return -1;
    }

    public View getViewInExtent(String packageName, String className) {
        if (mExtentAdapter != null) {
            List<FloatAppItem> activityList = mExtentAdapter.getActivityList();
            for (int i = 0; i < activityList.size(); i++) {
                FloatAppItem item = activityList.get(i);
                if (packageName.equals(item.packageName)
                        && className.equals(item.className)) {
                    return mExtentView.getView(i);
                }
            }
        }
        return null;
    }

        public View getViewInResident(String packageName, String className) {
            if (mResidentAdapter != null) {
                List<FloatAppItem> activityList = mResidentAdapter
                        .getActivityList();
                for (int i = 0; i < activityList.size(); i++) {
                    FloatAppItem item = activityList.get(i);
                    if (packageName.equals(item.packageName)
                            && className.equals(item.className)) {
                        return mResidentView.getView(i);
                    }
                }
            }
            return null;
        }
}
