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
package com.android.dreams.phototable;

import android.app.ListActivity;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
//HQ_wuhuihui add for screen UI start
import android.util.Log;
import android.view.View.OnClickListener;
import android.app.ActionBar.LayoutParams;
import java.util.LinkedList;
import android.app.ActionBar;
import android.view.LayoutInflater;
import android.content.Context;
import android.widget.TextView;
import android.widget.ImageView;
//HQ_wuhuihui add for screen UI end


/**
 * Settings panel for photo flipping dream.
 */
public class FlipperDreamSettings extends ListActivity {
    @SuppressWarnings("unused")
    private static final String TAG = "FlipperDreamSettings";
    public static final String PREFS_NAME = FlipperDream.TAG;

    protected SharedPreferences mSettings;

    private PhotoSourcePlexor mPhotoSource;
    private SectionedAlbumDataAdapter mAdapter;
    //HQ_wuhuihui add for screen UI start
    private ImageView mSelectAll;
    protected TextView mTitle;
    //HQ_wuhuihui add for screen UI end
    private AsyncTask<Void, Void, Void> mLoadingTask;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        mSettings = getSharedPreferences(PREFS_NAME, 0);
        init();
        //HQ_wuhuihui add for screen UI start
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // Inflate a custom action bar that contains the "done" button for saving changes
            // to the group
            LayoutInflater inflater = (LayoutInflater) getSystemService
                    (Context.LAYOUT_INFLATER_SERVICE);
            View customActionBarView = inflater.inflate(R.menu.photodream_settings_action_bar,
                    null);
            mTitle = (TextView)customActionBarView.findViewById(R.id.title);
            mSelectAll = (ImageView)customActionBarView.findViewById(R.id.all_select_menu_item);
            mSelectAll.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG,"onClick()");
                if (mAdapter != null) {
                        mAdapter.selectAll(!mAdapter.areAllSelected());
                    }
                }
            });
            updateActionItem();
            // Show the custom action bar but hide the home icon and title
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
            ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME |
                    ActionBar.DISPLAY_SHOW_TITLE);
            actionBar.setCustomView(customActionBarView, new LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }
    //HQ_wuhuihui add for screen UI end

    @Override
    protected void onResume(){
        super.onResume();
        init();
    }

    protected void init() {
        mPhotoSource = new PhotoSourcePlexor(this, mSettings);
        setContentView(R.layout.settingslist);
        if (mLoadingTask != null && mLoadingTask.getStatus() != Status.FINISHED) {
            mLoadingTask.cancel(true);
        }
        showApology(false);
        mLoadingTask = new AsyncTask<Void, Void, Void>() {
            @Override
            public Void doInBackground(Void... unused) {
                mAdapter = new SectionedAlbumDataAdapter(FlipperDreamSettings.this,
                        mSettings,
                        R.layout.header,
                        R.layout.album,
                        new LinkedList<PhotoSource.AlbumData>(mPhotoSource.findAlbums()));
                return null;
            }

           @Override
           public void onPostExecute(Void unused) {
               mAdapter.registerDataSetObserver(new DataSetObserver () {
                       @Override
                       public void onChanged() {
                           updateActionItem();
                       }
                       @Override
                       public void onInvalidated() {
                           updateActionItem();
                       }
                   });
               setListAdapter(mAdapter);
               getListView().setItemsCanFocus(true);
               updateActionItem();
               showApology(mAdapter.getCount() == 0);
           }
        };
        mLoadingTask.execute();
    }

    //HQ_wuhuihui modified for screen UI start
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.photodream_menu_all:
            if (mAdapter != null) {
                mAdapter.selectAll(!mAdapter.areAllSelected());
            }
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    private void showApology(boolean apologize) {
        View empty = findViewById(R.id.spinner);
        View sorry = findViewById(R.id.sorry);
        if (empty != null && sorry != null) {
            empty.setVisibility(apologize ? View.GONE : View.VISIBLE);
            sorry.setVisibility(apologize ? View.VISIBLE : View.GONE);
        }
    }

    private void updateActionItem() {
        if (mAdapter != null && mSelectAll != null) {
            if (mAdapter.areAllSelected()) {
                mSelectAll.setImageResource(R.drawable.ic_done_wht_24dp);
            } else {
                mSelectAll.setImageResource(R.drawable.ic_done_wht_24dp);
            }
        }
    }
    //HQ_wuhuihui modified for screen UI end
}
