/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.mediatek.search;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.SearchIndexableRaw;
import com.mediatek.wifi.WifiUtils;

import java.util.ArrayList;
import java.util.List;


public class SearchExt implements Indexable {

    private static final String TAG = "SearchExt";

    private static final String ACTION_SCHEDULE_POWERON_OFF = "com.android.settings.SCHEDULE_POWER_ON_OFF_SETTING";

    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {
        @Override
        public List<SearchIndexableRaw> getRawDataToIndex(Context context,
                boolean enabled) {
            List<SearchIndexableRaw> indexables = new ArrayList<SearchIndexableRaw>();

            // loading Schedule Power On/Off search information
            Intent intent = new Intent(ACTION_SCHEDULE_POWERON_OFF);
            List<ResolveInfo> apps = context.getPackageManager()
                    .queryIntentActivities(intent, 0);
            if (apps != null && apps.size() != 0) {
                Log.d(TAG, "schedule power on exist");
                SearchIndexableRaw indexable = new SearchIndexableRaw(context);
                indexable.title = context
                        .getString(R.string.schedule_power_on_off_settings_title);
                indexable.intentAction = ACTION_SCHEDULE_POWERON_OFF;
                indexables.add(indexable);
            }

            return indexables;
        }
        
        @Override
        public List<String> getNonIndexableKeys(Context context) {
            final ArrayList<String> result = new ArrayList<String>();
            //M: exclude auto join feature
            boolean excluded = WifiUtils.getCMCC();
            if (!context.getResources().getBoolean(R.bool.auto_join_enable) || excluded) {
            	result.add(WifiUtils.WIFI_AP_AUTO_JOIN);
            }
            return result;
        }
    };

}
