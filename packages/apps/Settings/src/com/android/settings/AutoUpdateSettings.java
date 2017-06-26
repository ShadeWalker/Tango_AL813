package com.android.settings;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.SettingsActivity;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.SearchIndexableRaw;
import android.content.Context;
import android.content.res.Resources;

import java.util.List;
import java.util.ArrayList;

import android.util.Log;
public class AutoUpdateSettings implements Indexable {

    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
    new BaseSearchIndexProvider() {
        @Override
        public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
            final List<SearchIndexableRaw> result = new ArrayList<SearchIndexableRaw>();
            final Resources res = context.getResources();

            SearchIndexableRaw data = new SearchIndexableRaw(context);
            data.title = res.getString(R.string.system_update_settings_list_item_title);
            data.screenTitle = res.getString(R.string.system_update_settings_list_item_title);
            data.keywords = res.getString(R.string.system_update_settings_list_item_title);
            data.intentAction = "android.settings.SYSTEM_UPDATE_SETTINGS";
            data.intentTargetPackage = "com.huawei.android.hwouc";
            data.intentTargetClass = "com.huawei.android.hwouc.ui.activities.MainEntranceActivity";
            result.add(data);
            return result;
        }
    };

}
