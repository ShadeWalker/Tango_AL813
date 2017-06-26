package com.mediatek.contacts.ext;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.mediatek.widget.CustomAccountRemoteViews.AccountInfo;

import java.util.List;

public class DefaultCtExtension implements ICtExtension {

    private static final String TAG = "DefaultCtExtension";
    @Override
    public String formatPhoneNumber(String phoneNumber) {
        // default don't change
        return phoneNumber;
    }

    @Override
    public long getPhotoIdBySub(int subId, int sdnFlag, long photoId) {
        Log.d(TAG, "getPhotoIdBySub");
        return photoId;
    }

    @Override
    public Drawable getPhotoDrawableBySub(Resources res, int subId, Drawable photoDrawable) {
        Log.d(TAG, "getPhotoDrawableBySub");
        return photoDrawable;
    }

    @Override
    public String getString(String stringName) {
        Log.d(TAG, "getString");
        // default return null
        return null;
    }

    @Override
    public void loadSimCardIconBitmap(Resources res) {
        Log.d(TAG, "loadSimCardIconBitmap");
        // do nothing in common
    }

    @Override
    public Bitmap getOperatorIconBitmapForPhotoId(long mPhotoId,
            Bitmap resultBitmap) {
        Log.d(TAG, "getOperatorIconBitmapForPhotoId");
        return resultBitmap;
    }

    @Override
    public boolean isOperatorSimPhotoId(long mPhotoId) {
        Log.d(TAG, "isOperatorSimPhotoId");
        return false;
    }

    @Override
    public int showAlwaysAskIndicate(int defaultValue) {
        return defaultValue;
    }

}
