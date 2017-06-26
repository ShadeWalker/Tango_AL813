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
 * MediaTek Inc. (C) 2010. All rights reserved.
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

package com.android.launcher3;

import android.app.ActionBar;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageStats;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;


import com.mediatek.launcher3.ext.LauncherLog;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * M: this activity is used to configure hide applications, for op09.
 */
public class HideAppsActivity extends ListActivity implements AdapterView.OnItemClickListener {
    private static final String TAG = "HideAppsActivity";

    static final String ACTION_PACKAGE_CHANGED = "package_list_changed";
    static final String CHANGED_PAGES = "changedPages";

    private static final int SIZE_UNKNOWN = -1;
    private static final int SIZE_INVALID = -2;

    private ArrayList<AppInfo> mApps = new ArrayList<AppInfo>();
    //Used to recorder all apps original state when enter HideAppsActivity.
    private ArrayList<Boolean> mOriginalState = new ArrayList<Boolean>();

    private AppsAdapter mAdapter;

    private HashMap<String, AppEntry> mEntriesMap = new HashMap<String, AppEntry>();
    private HandlerThread mThread;
    private BackgroundHandler mHandler;

    private final BroadcastReceiver mPackageChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ACTION_PACKAGE_CHANGED.equals(action)) {
                init();
                mAdapter.notifyDataSetChanged();
            }
        }
    };

    /**
    * M: This class used to desript the size info of all app.
    */
    public static class SizeInfo {
        long mCacheSize;
        long mCodeSize;
        long mDataSize;
        long mExternalCodeSize;
        long mExternalDataSize;

        // This is the part of externalDataSize that is in the cache
        // section of external storage. Note that we don't just combine
        // this with cacheSize because currently the platform can't
        // automatically trim this data when needed, so it is something
        // the user may need to manage. The externalDataSize also includes
        // this value, since what this is here is really the part of
        // externalDataSize that we can just consider to be "cache" files
        // for purposes of cleaning them up in the app details UI.
        long mExternalCacheSize;
    }

    /**
    * M: This class used to desript the size info of all app.
    */
    public static class AppEntry extends SizeInfo {
        AppInfo mInfo;
        long mSize;
        long mInternalSize;
        long mExternalSize;
        String mSizeStr;
        String mInternalSizeStr;
        String mExternalSizeStr;

        AppEntry(AppInfo appInfo) {
            this.mInfo = appInfo;
            this.mSize = SIZE_UNKNOWN;
        }
    }

    /**
    * M: this handler is used to compute the size info and notify data changed.
    */
    class BackgroundHandler extends Handler {
        static final int MSG_LOAD_SIZE = 0x0001;

        final IPackageStatsObserver.Stub mStatsObserver = new IPackageStatsObserver.Stub() {
            public void onGetStatsCompleted(PackageStats stats, boolean succeeded) {
                synchronized (mEntriesMap) {
                    boolean sizeChanged = false;
                    AppEntry entry = mEntriesMap.get(stats.packageName);
                    if (entry != null) {
                        long externalCodeSize = stats.externalCodeSize + stats.externalObbSize;
                        long externalDataSize = stats.externalDataSize + stats.externalMediaSize
                                + stats.externalCacheSize;
                        long newSize = externalCodeSize + externalDataSize
                                + getTotalInternalSize(stats);
                        if (entry.mSize != newSize || entry.mCacheSize != stats.cacheSize
                                || entry.mCodeSize != stats.codeSize
                                || entry.mDataSize != stats.dataSize
                                || entry.mExternalCodeSize != externalCodeSize
                                || entry.mExternalDataSize != externalDataSize
                                || entry.mExternalCacheSize != stats.externalCacheSize) {
                            entry.mSize = newSize;
                            entry.mCacheSize = stats.cacheSize;
                            entry.mCodeSize = stats.codeSize;
                            entry.mDataSize = stats.dataSize;
                            entry.mExternalCodeSize = externalCodeSize;
                            entry.mExternalDataSize = externalDataSize;
                            entry.mExternalCacheSize = stats.externalCacheSize;
                            entry.mInternalSize = getTotalInternalSize(stats);
                            entry.mExternalSize = getTotalExternalSize(stats);
                            sizeChanged = true;
                        }
                        // Update size info
                        entry.mSizeStr = getSizeStr(entry.mSize);
                        entry.mInternalSizeStr = getSizeStr(entry.mInternalSize);
                        entry.mExternalSizeStr = getSizeStr(entry.mExternalSize);

                        if (sizeChanged) {
                            // Update the listview item text
                            notifyDataChanged();
                        }
                        // Query another one if needed
                        Message msg = mHandler.obtainMessage(MSG_LOAD_SIZE);
                        mHandler.sendMessage(msg);
                    }
                }
            }
        };

        BackgroundHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_LOAD_SIZE:
                    synchronized (mEntriesMap) {
                        String pkgName = requestComputePkgSize();
                        if (pkgName != null) {
                            HideAppsActivity.this.getPackageManager().getPackageSizeInfo(pkgName,
                                    mStatsObserver);
                        }
                    }
                    break;

                default:
                    break;
            }
        }

        String requestComputePkgSize() {
            final int appSize = mApps.size();
            for (int i = 0; i < appSize; i++) {
                String pkgName = mApps.get(i).componentName.getPackageName();
                if (pkgName != null) {
                    AppEntry entry = mEntriesMap.get(pkgName);
                    if (entry != null && entry.mSize == SIZE_UNKNOWN) {
                        // Initilized size to be shown for user
                        entry.mSize = 0;
                        return pkgName;
                    }
                }
            }
            return null;
        }
    }

    private void notifyDataChanged() {
        if (mAdapter != null) {
            getListView().post(new Runnable() {
                @Override
                public void run() {
                    mAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    private String getSizeStr(long size) {
        if (size >= 0) {
            return Formatter.formatFileSize(this, size);
        }
        return null;
    }

    private long getTotalInternalSize(PackageStats ps) {
        if (ps != null) {
            return ps.codeSize + ps.dataSize;
        }
        return SIZE_INVALID;
    }

    private long getTotalExternalSize(PackageStats ps) {
        if (ps != null) {
            return ps.externalCodeSize + ps.externalDataSize + ps.externalMediaSize
                    + ps.externalObbSize;
        }
        return SIZE_INVALID;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hide_list);
        mThread = new HandlerThread("HideAppsActivity.worker", Process.THREAD_PRIORITY_BACKGROUND);
        mThread.start();
        mHandler = new BackgroundHandler(mThread.getLooper());

        // Get all apps information, icon, title, size, show or hide?
        init();

        mAdapter = new AppsAdapter(this);
        setListAdapter(mAdapter);
        getListView().setOnItemClickListener(this);
        getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PACKAGE_CHANGED);
        registerReceiver(mPackageChangeReceiver, filter);
    }

    @Override
    protected void onPause() {
        backToHome();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onDestroy.");
        }

        unregisterReceiver(mPackageChangeReceiver);
        super.onDestroy();
    }

    private void init() {
        if (!mApps.isEmpty()) {
            mApps.clear();
        }

        if (!mEntriesMap.isEmpty()) {
            mEntriesMap.clear();
        }

        if (mThread.isAlive()) {
            mThread.interrupt();
        }

        // add App
        mApps.addAll(AppsCustomizePagedView.mApps);

        // apend the folder's app also
        if (AppsCustomizePagedView.sFolders.size() > 0) {
            for (int i = 0; i < AppsCustomizePagedView.sFolders.size(); i++) {
                ArrayList<ShortcutInfo> contents = AppsCustomizePagedView.sFolders.get(i).contents;
                for (int j = 0; j < contents.size(); j++) {
                    LauncherLog.d(TAG, "init start: i = " + i + ",j = " + j
                            + ", item = " + contents.get(j));
                    mApps.add(contents.get(j).makeAppInfo());
                }
            }
        }

        // Now all apps are present.
        int index = 0;
        final int appSize = mApps.size();
        for (int i = 0; i < appSize; i++) {
            AppInfo info = mApps.get(i);
            // For query package physical memory size quickly
            mEntriesMap.put(info.componentName.getPackageName(), new AppEntry(info));

            if (info != null) {
                info.stateChanged = false;
                mOriginalState.add(info.isVisible);
            }
        }
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "init end: appSize = " + appSize + ",mApps = " + mApps);
        }

        // Now start to query package size
        mHandler.sendEmptyMessage(BackgroundHandler.MSG_LOAD_SIZE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                backToHome();
                break;
            default:
                break;
        }
        return true;
    }

    private void backToHome() {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "backToHome: AppsCustomizePagedView.sShowAndHideApps = "
                    + AppsCustomizePagedView.sShowAndHideApps);
        }
        if (AppsCustomizePagedView.sShowAndHideApps.size() == 0) {
             getStateChangedPages();
        }

        if (AppsCustomizePagedView.sShowAndHideApps.size() > 0) {
            setResult(RESULT_OK);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    /**
     * Get pages in which there is application visible state changes.
     *
     * @return
     */
    private ArrayList<Integer> getStateChangedPages() {
        ArrayList<Integer> pages = new ArrayList<Integer>();
        int index = 0;
        final int size = mApps.size();
        for (int i = 0; i < size; i++) {
            AppInfo info = mApps.get(i);
            if (mOriginalState.get(index++) != info.isVisible) {
                AppsCustomizePagedView.sShowAndHideApps.add(info);
            }
        }
        return pages;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ViewHolder holder = (ViewHolder) view.getTag();
        holder.mHide.toggle();
        // State changes if the visible states equals the check status(hide).
        mApps.get(position).isVisible = !holder.mHide.isChecked();
    }

    @Override
    public void onBackPressed() {
        backToHome();
    }

    /**
    * M: this class use to get app view.
    */
    class AppsAdapter extends BaseAdapter {
        private final LayoutInflater mInflater;

        AppsAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
        }

        public int getCount() {
            return mApps.size();
        }

        public Object getItem(int position) {
            return mApps.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.hide_entry, parent, false);

                holder = new ViewHolder();
                holder.mIcon = (ImageView) convertView.findViewById(R.id.app_icon);
                holder.mTitle = (TextView) convertView.findViewById(R.id.app_name);
                holder.mSize = (TextView) convertView.findViewById(R.id.app_size);
                holder.mHide = (CheckBox) convertView.findViewById(R.id.hide);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final Bitmap iconBitmap = mApps.get(position).iconBitmap;
            if (iconBitmap != null) {
                holder.mIcon.setVisibility(View.VISIBLE);
                holder.mIcon.setImageBitmap(iconBitmap);
            } else {
                holder.mIcon.setVisibility(View.GONE);
            }

            holder.mTitle.setText(mApps.get(position).title);
            holder.mHide.setChecked(!mApps.get(position).isVisible);
            // Now used to display app's memory size.
            holder.mSize.setText(mEntriesMap.get(mApps.get(position).componentName
                    .getPackageName()).mSizeStr);

            return convertView;
        }
    }

    /**
    * M: this view is to describle each app info.
    */
    static class ViewHolder {
        ImageView mIcon;
        TextView mTitle;
        TextView mSize;
        CheckBox mHide;
    }
}
