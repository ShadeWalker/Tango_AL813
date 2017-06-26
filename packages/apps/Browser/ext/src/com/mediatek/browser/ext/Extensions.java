package com.mediatek.browser.ext;

import android.content.Context;

import com.mediatek.common.MPlugin;

public class Extensions {
    private static volatile IBrowserBookmarkExt sBookmarkPlugin = null;
    private static volatile IBrowserDownloadExt sDownloadPlugin = null;
    private static volatile IBrowserHistoryExt sHistoryPlugin = null;
    private static volatile IBrowserMiscExt sMiscPlugin = null;
    private static volatile IBrowserRegionalPhoneExt sRegionalPhonePlugin = null;
    private static volatile IBrowserSettingExt sSettingPlugin = null;
    private static volatile IBrowserSiteNavigationExt sSiteNavigationPlugin = null;
    private static volatile IBrowserUrlExt sUrlPlugin = null;

    private Extensions() {
    };

    public static IBrowserBookmarkExt getBookmarkPlugin(Context context) {
        if (sBookmarkPlugin == null) {
            synchronized (Extensions.class) {
                if (sBookmarkPlugin == null) {
                    sBookmarkPlugin = (IBrowserBookmarkExt) MPlugin.createInstance(
                                        IBrowserBookmarkExt.class.getName(), context);
                    if (sBookmarkPlugin == null) {
                        sBookmarkPlugin = new DefaultBrowserBookmarkExt();
                    }
                }
            }
        }
        return sBookmarkPlugin;
    }

    public static IBrowserDownloadExt getDownloadPlugin(Context context) {
        if (sDownloadPlugin == null) {
            synchronized (Extensions.class) {
                if (sDownloadPlugin == null) {
                    sDownloadPlugin = (IBrowserDownloadExt) MPlugin.createInstance(
                                        IBrowserDownloadExt.class.getName(), context);
                    if (sDownloadPlugin == null) {
                        sDownloadPlugin = new DefaultBrowserDownloadExt();
                    }
                }
            }
        }
        return sDownloadPlugin;
    }

    public static IBrowserHistoryExt getHistoryPlugin(Context context) {
        if (sHistoryPlugin == null) {
            synchronized (Extensions.class) {
                if (sHistoryPlugin == null) {
                    sHistoryPlugin = (IBrowserHistoryExt) MPlugin.createInstance(
                                        IBrowserHistoryExt.class.getName(), context);
                    if (sHistoryPlugin == null) {
                        sHistoryPlugin = new DefaultBrowserHistoryExt();
                    }
                }
            }
        }
        return sHistoryPlugin;
    }

    public static IBrowserMiscExt getMiscPlugin(Context context) {
        if (sMiscPlugin == null) {
            synchronized (Extensions.class) {
                if (sMiscPlugin == null) {
                    sMiscPlugin = (IBrowserMiscExt) MPlugin.createInstance(
                                    IBrowserMiscExt.class.getName(), context);
                    if (sMiscPlugin == null) {
                        sMiscPlugin = new DefaultBrowserMiscExt();
                    }
                }
            }
        }
        return sMiscPlugin;
    }

    public static IBrowserRegionalPhoneExt getRegionalPhonePlugin(Context context) {
        if (sRegionalPhonePlugin == null) {
            synchronized (Extensions.class) {
                if (sRegionalPhonePlugin == null) {
                    sRegionalPhonePlugin = (IBrowserRegionalPhoneExt) MPlugin.createInstance(
                                            IBrowserRegionalPhoneExt.class.getName(), context);
                    if (sRegionalPhonePlugin == null) {
                        sRegionalPhonePlugin = new DefaultBrowserRegionalPhoneExt();
                    }
                }
            }
        }
        return sRegionalPhonePlugin;
    }

    public static IBrowserSettingExt getSettingPlugin(Context context) {
        if (sSettingPlugin == null) {
            synchronized (Extensions.class) {
                if (sSettingPlugin == null) {
                    sSettingPlugin = (IBrowserSettingExt) MPlugin.createInstance(
                                        IBrowserSettingExt.class.getName(), context);
                    if (sSettingPlugin == null) {
                        sSettingPlugin = new DefaultBrowserSettingExt();
                    }
                }
            }
        }
        return sSettingPlugin;
    }

    public static IBrowserSiteNavigationExt getSiteNavigationPlugin(Context context) {
        if (sSiteNavigationPlugin == null) {
            synchronized (Extensions.class) {
                if (sSiteNavigationPlugin == null) {
                     sSiteNavigationPlugin = (IBrowserSiteNavigationExt) MPlugin.createInstance(
                                                IBrowserSiteNavigationExt.class.getName(), context);
                    if (sSiteNavigationPlugin == null) {
                        sSiteNavigationPlugin = new DefaultBrowserSiteNavigationExt();
                    }
                }
            }
        }
        return sSiteNavigationPlugin;
    }

    public static IBrowserUrlExt getUrlPlugin(Context context) {
        if (sUrlPlugin == null) {
            synchronized (Extensions.class) {
                if (sUrlPlugin == null) {
                    sUrlPlugin = (IBrowserUrlExt) MPlugin.createInstance(
                                    IBrowserUrlExt.class.getName(), context);
                    if (sUrlPlugin == null) {
                        sUrlPlugin = new DefaultBrowserUrlExt();
                    }
                }
            }
        }
        return sUrlPlugin;
    }

}