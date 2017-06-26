package com.mediatek.phone.ext;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * Telecom account registry extension plugin for op09.
 */
public interface ITelecomAccountRegistryExt {
    /**
     * Called when need to registry phone.
     * 
     * @param subId
     *            indicator regitry phone.
     */
    void setPhoneAccountSubId(long subId);

    /**
     * Called when need to registry phone account icon.
     * 
     * @param context
     *            used to create bitmap.
     * @param iconBitmap
     *            default bitmap for phone account icon.
     * @return plug in customed phone account icon bitmap.
     */
    Bitmap getPhoneAccountIconBitmap(Context context, Bitmap iconBitmap);
}
