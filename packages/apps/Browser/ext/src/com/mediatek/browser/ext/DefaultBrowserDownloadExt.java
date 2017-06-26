package com.mediatek.browser.ext;

import android.app.Activity;
import android.app.DownloadManager.Request;
import android.net.Uri;

import com.mediatek.xlog.Xlog;

import java.io.File;

public class DefaultBrowserDownloadExt implements IBrowserDownloadExt {

    private static final String TAG = "DefaultBrowserDownloadExt";

    @Override
    public boolean checkStorageBeforeDownload(Activity activity, String downloadPath, long contentLength) {
        Xlog.i(TAG, "Enter: " + "checkStorageBeforeDownload" + " --default implement");
        return false;
    }

    @Override
    public boolean showToastWithFileSize(Activity activity, long contentLength) {
        Xlog.i(TAG, "Enter: " + "showToastWithFileSize" + " --default implement");
        return false;
    }

    @Override
    public void setRequestDestinationDir(String downloadPath, Request request,
            String filename, String mimeType) {
        Xlog.i(TAG, "Enter: " + "setRequestDestinationDir" + " --default implement");

        String dir = "file://" + downloadPath + File.separator + filename;
        Uri pathUri = Uri.parse(dir);
        request.setDestinationUri(pathUri);
        Xlog.d(TAG, "mRequest.setDestinationUri, dir: " + dir);
    }

}
