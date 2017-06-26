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
public class NoDisturbSettings implements Indexable {

    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
    new BaseSearchIndexProvider() {
        @Override
        public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
            final List<SearchIndexableRaw> result = new ArrayList<SearchIndexableRaw>();
            final Resources res = context.getResources();

            SearchIndexableRaw data = new SearchIndexableRaw(context);
            data.title = res.getString(R.string.no_disturbing_settings);
            data.screenTitle = res.getString(R.string.no_disturbing_settings);
            data.keywords = res.getString(R.string.no_disturbing_settings);
            data.intentAction = "android.intent.action.NO_DISTURB";
            data.intentTargetPackage = "com.huawei.systemmanager";
            data.intentTargetClass = "com.huawei.systemmanager.preventmode.PreventModeActivity";
            result.add(data);
            return result;
        }
    };

}
