/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.settings.dashboard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.content.Intent;
import android.content.ComponentName;

import android.widget.ImageView;
import android.widget.TextView;
import com.android.settings.R;
import com.android.settings.Utils;
import android.util.Log;
import android.widget.Toast;
import android.content.pm.ResolveInfo;
import java.util.List;

public class DashboardTileView extends FrameLayout implements View.OnClickListener {

    private static final int DEFAULT_COL_SPAN = 1;

    private ImageView mImageView;
    private TextView mTitleTextView;
    private TextView mStatusTextView;
    private View mDivider;
	//wuhuihui add for preference status
	private TextView mPrefStatusTextView;
	private static final String LOG_TAG = "DashboardTileView";

    private int mColSpan = DEFAULT_COL_SPAN;

    private DashboardTile mTile;

    public DashboardTileView(Context context) {
        this(context, null);
    }

    public DashboardTileView(Context context, AttributeSet attrs) {
        super(context, attrs);

        final View view = LayoutInflater.from(context).inflate(R.layout.dashboard_tile, this);

        mImageView = (ImageView) view.findViewById(R.id.icon);
        mTitleTextView = (TextView) view.findViewById(R.id.title);
        mStatusTextView = (TextView) view.findViewById(R.id.status);
        mDivider = view.findViewById(R.id.tile_divider);
		mPrefStatusTextView = (TextView) view.findViewById(R.id.pref_status);

        setOnClickListener(this);
        setBackgroundResource(R.drawable.dashboard_tile_background);
        setFocusable(true);
    }

    public TextView getTitleTextView() {
        return mTitleTextView;
    }

    public TextView getStatusTextView() {
        return mStatusTextView;
    }

	public TextView getPreferenceTextView(){
        return mPrefStatusTextView;
	}

    public ImageView getImageView() {
        return mImageView;
    }

    public void setTile(DashboardTile tile) {
        mTile = tile;
    }

    public void setDividerVisibility(boolean visible) {
        mDivider.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    void setColumnSpan(int span) {
        mColSpan = span;
    }

    int getColumnSpan() {
        return mColSpan;
    }

	private void start3rdPartyActivity(String packageName, String className) {
		Intent intent = new Intent();
        ComponentName cn = new ComponentName(packageName,className);
        intent.setAction(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(cn);
        List<ResolveInfo> apps = getContext().getPackageManager()
								   .queryIntentActivities(intent, 0);
        if (apps == null || apps.size() == 0) {
            Toast.makeText(getContext(), "Huawei application is not installed",
            Toast.LENGTH_SHORT).show();
	    } else {
            getContext().startActivity(intent);
        }//end apps
	}

    @Override
    public void onClick(View v) {
        Log.d(LOG_TAG, "onClick()");
        if (mTile.fragment != null) {
            Utils.startWithFragment(getContext(), mTile.fragment, mTile.fragmentArguments, null, 0,
                    mTile.titleRes, mTile.getTitle(getResources()));
        } else if (mTile.intent != null) {
             //HQ_wuhuihui_20150708 add for huawei battery saving app start
             Intent intent = mTile.intent;
             String action = intent.getAction();
             if ("android.intent.action.BATTERY_SAVING".equals(action)) {
			     start3rdPartyActivity("com.huawei.systemmanager", 
			   	           "com.huawei.systemmanager.power.ui.HwPowerManagerActivity");
             } else if ("android.intent.action.GESTURE_SETTINGS".equals(action)) {
                 start3rdPartyActivity("com.huawei.motionservice", 
			   	           "com.huawei.motionsettings.MotionSettings");
             } else if ("android.intent.action.NOTIFICATION_CENTER".equals(action)) {
                 start3rdPartyActivity("com.huawei.systemmanager", 
                                           "com.huawei.notificationmanager.ui.NotificationManagmentActivity");
             } else if ("android.intent.action.STARTUP_MANAGER".equals(action)) {
                 start3rdPartyActivity("com.huawei.systemmanager",
                                           "com.huawei.systemmanager.optimize.bootstart.BootStartActivity");
             } else if ("android.intent.action.PERMISSION_MANAGER".equals(action)) {
				 start3rdPartyActivity("com.huawei.systemmanager",
                                           "com.huawei.permissionmanager.ui.MainActivity");
			 } else if ("android.intent.action.NET_APP_MANAGEMENT".equals(action)) {
				 start3rdPartyActivity("com.huawei.systemmanager",
                                           "com.huawei.systemmanager.netassistant.netapp.ui.NetAppListActivity");
			 } else if ("android.intent.action.CLOUD_SERVICE".equals(action)) {
				 start3rdPartyActivity("com.huawei.android.ds",
                                           "com.huawei.android.hicloud.hisync.activity.NewHiSyncSettingActivity");
			 } else if ("android.intent.action.PROTECTED_APPS".equals(action)) {
				 start3rdPartyActivity("com.huawei.systemmanager",
                         "com.huawei.systemmanager.optimize.process.ProtectActivity");
			 } else if ("android.intent.action.NO_DISTURB".equals(action)) {
				 start3rdPartyActivity("com.huawei.systemmanager",
                         "com.huawei.systemmanager.preventmode.PreventModeActivity");
			 } else if ("android.intent.action.DATA_USAGE".equals(action)) {
				 start3rdPartyActivity("com.huawei.systemmanager",
                         "com.huawei.netassistant.ui.NetAssistantMainActivity");
			 } else if ("com.android.settings.LAUNCH_MODE".equals(action)) {
				 start3rdPartyActivity("com.android.settings",
						 "com.android.settings.LaunchModeSettingActivity");
			 }

			 else {
                getContext().startActivity(mTile.intent);
             }// HQ_wuhuihui_20150708 add for huawei battery saving app end
        }
    }
}
