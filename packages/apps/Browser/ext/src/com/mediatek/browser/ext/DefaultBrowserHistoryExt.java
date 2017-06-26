package com.mediatek.browser.ext;

import android.app.Activity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.mediatek.xlog.Xlog;

public class DefaultBrowserHistoryExt implements IBrowserHistoryExt {

    private static final String TAG = "DefaultBrowserHistoryExt";

    @Override
    public void createHistoryPageOptionsMenu(Menu menu, MenuInflater inflater) {
        Xlog.i(TAG, "Enter: " + "createHistoryPageOptionsMenu" + " --default implement");
    }

    @Override
    public void prepareHistoryPageOptionsMenuItem(Menu menu, boolean isNull, boolean isEmpty) {
        Xlog.i(TAG, "Enter: " + "prepareHistoryPageOptionsMenuItem" + " --default implement");
    }

    @Override
    public boolean historyPageOptionsMenuItemSelected(MenuItem item, Activity activity) {
        Xlog.i(TAG, "Enter: " + "historyPageOptionsMenuItemSelected" + " --default implement");
        return false;
    }

}
