package com.mediatek.downloadmanager.ext;

import android.content.Context;

import com.mediatek.common.MPlugin;

public class Extensions {
    private static IDownloadProviderFeatureExt sPlugin = null;
    public static IDownloadProviderFeatureExt getDefault(Context context) {
        if (sPlugin == null) {
        	sPlugin = (IDownloadProviderFeatureExt) MPlugin.createInstance(
                    IDownloadProviderFeatureExt.class.getName(), context);
        	if (sPlugin == null) {
        		sPlugin = new DefaultDownloadProviderFeatureExt(context);
        	}
        }
        return sPlugin;
    }
}