package com.mediatek.browser.ext;

import com.mediatek.xlog.Xlog;

public class DefaultBrowserSiteNavigationExt implements IBrowserSiteNavigationExt {

    private static final String TAG = "DefaultBrowserSiteNavigationExt";

    @Override
    public CharSequence[] getPredefinedWebsites() {
        Xlog.i(TAG, "Enter: " + "getPredefinedWebsites" + " --default implement");
        return null;
    }

    @Override
    public int getSiteNavigationCount() {
        Xlog.i(TAG, "Enter: " + "getSiteNavigationCount" + " --default implement");
        return 0;
    }

}
