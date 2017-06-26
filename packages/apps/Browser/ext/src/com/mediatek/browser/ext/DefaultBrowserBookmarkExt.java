package com.mediatek.browser.ext;

import android.app.Activity;
import android.database.sqlite.SQLiteDatabase;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.mediatek.xlog.Xlog;

public class DefaultBrowserBookmarkExt implements IBrowserBookmarkExt {

    private static final String TAG = "DefaultBrowserBookmarkExt";

    @Override
    public int addDefaultBookmarksForCustomer(SQLiteDatabase db) {
        Xlog.i(TAG, "Enter: " + "addDefaultBookmarksForCustomer" + " --default implement");
        return 0;
    }

    @Override
    public void createBookmarksPageOptionsMenu(Menu menu, MenuInflater inflater) {
        Xlog.i(TAG, "Enter: " + "createBookmarksPageOptionsMenu" + " --default implement");
    }

    @Override
    public boolean bookmarksPageOptionsMenuItemSelected(MenuItem item, Activity activity, long folderId) {
        Xlog.i(TAG, "Enter: " + "bookmarksPageOptionsMenuItemSelected" + " --default implement");
        return false;
    }

}
