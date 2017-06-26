package com.mediatek.browser.ext;

import android.content.Context;
import android.text.InputFilter;

import com.mediatek.xlog.Xlog;

public class DefaultBrowserUrlExt implements IBrowserUrlExt {

    private static final String TAG = "DefaultBrowserUrlExt";

    @Override
    public InputFilter[] checkUrlLengthLimit(final Context context) {
        Xlog.i(TAG, "Enter: " + "checkUrlLengthLimit" + " --default implement");
        return null;
    }

    @Override
    public String checkAndTrimUrl(String url) {
        Xlog.i(TAG, "Enter: " + "checkAndTrimUrl" + " --default implement");
        return url;
    }

    @Override
    public String getNavigationBarTitle(String title, String url) {
        Xlog.i(TAG, "Enter: " + "getNavigationBarTitle" + " --default implement");
        return url;
    }

    @Override
    public String getOverrideFocusContent(boolean hasFocus, String newContent,
                    String oldContent, String url) {
        Xlog.i(TAG, "Enter: " + "getOverrideFocusContent" + " --default implement");
        if (hasFocus && !newContent.equals(oldContent)) {
            return oldContent;
        } else {
            return null;
        }
    }

    @Override
    public String getOverrideFocusTitle(String title, String content) {
        Xlog.i(TAG, "Enter: " + "getOverrideFocusTitle" + " --default implement");
        return content;
    }

    @Override
    public boolean redirectCustomerUrl(String url) {
        Xlog.i(TAG, "Enter: " + "redirectCustomerUrl" + " --default implement");
        return false;
    }

}
