package com.mediatek.browser.ext;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.webkit.WebSettings;

import com.mediatek.search.SearchEngineManager;
import com.mediatek.common.search.SearchEngine;
import com.mediatek.storage.StorageManagerEx;
import com.mediatek.xlog.Xlog;

public class DefaultBrowserSettingExt implements IBrowserSettingExt {

    private static final String TAG = "DefaultBrowserSettingsExt";

    private static final String DEFAULT_DOWNLOAD_DIRECTORY = "/storage/sdcard0/MyFavorite";
    private static final String DEFAULT_MY_FAVORITE_FOLDER = "/MyFavorite";

    private static final String PREF_SEARCH_ENGINE = "search_engine";
    private static final String DEFAULT_SEARCH_ENGIN = "google";

    @Override
    public void customizePreference(int index, PreferenceScreen prefSc,
                    OnPreferenceChangeListener onPreferenceChangeListener,
                    SharedPreferences sharedPref, PreferenceFragment prefFrag) {
        Xlog.i(TAG, "Enter: " + "customizePreference" + " --default implement");
        return;
    }

    @Override
    public boolean updatePreferenceItem(Preference pref, Object objValue) {
        Xlog.i(TAG, "Enter: " + "updatePreferenceItem" + " --default implement");
        return false;
    }

    @Override
    public String getCustomerHomepage() {
        Xlog.i(TAG, "Enter: " + "getCustomerHomepage" + " --default implement");
        return null;
    }

    @Override
    public String getDefaultDownloadFolder() {
        Xlog.i(TAG, "Enter: " + "getDefaultDownloadFolder()" + " --default implement");
        String defaultDownloadPath = DEFAULT_DOWNLOAD_DIRECTORY;
        String defaultStorage = StorageManagerEx.getDefaultPath();
        if (null != defaultStorage) {
            defaultDownloadPath = defaultStorage + DEFAULT_MY_FAVORITE_FOLDER;
        }
        Xlog.v(TAG, "device default storage is: " + defaultStorage +
               " defaultPath is: " + defaultDownloadPath);
        return defaultDownloadPath;
    }

    @Override
    public boolean getDefaultLoadPageMode() {
        Xlog.i(TAG, "Enter: " + "getDefaultLoadPageMode" + " --default implement");
        return true;
    }

    @Override
    public String getOperatorUA(String defaultUA) {
        Xlog.i(TAG, "Enter: " + "getOperatorUA" + " --default implement");
        return null;
    }

    @Override
    public String getSearchEngine(SharedPreferences pref, Context context) {
        Xlog.i(TAG, "Enter: " + "getSearchEngine" + " --default implement");
        SearchEngineManager searchEngineManager = (SearchEngineManager)
            context.getSystemService(Context.SEARCH_ENGINE_SERVICE);
        SearchEngine info = searchEngineManager.getDefault();
        String defaultSearchEngine = DEFAULT_SEARCH_ENGIN;
        if (info != null) {
            defaultSearchEngine = info.getName();
        }
        return pref.getString(PREF_SEARCH_ENGINE, defaultSearchEngine);
    }

    @Override
    public void setOnlyLandscape(SharedPreferences pref, Activity activity) {
        Xlog.i(TAG, "Enter: " + "setOnlyLandscape" + " --default implement");
        if (activity != null) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    @Override
    public void setStandardFontFamily(WebSettings settings, SharedPreferences pref) {
        Xlog.i(TAG, "Enter: " + "setStandardFontFamily" + " --default implement");
    }

    @Override
    public void setTextEncodingChoices(ListPreference pref) {
        Xlog.i(TAG, "Enter: " + "setTextEncodingChoices" + " --default implement");
    }

}
