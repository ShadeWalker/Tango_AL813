package com.mediatek.contacts.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.mediatek.contacts.ContactsSystemProperties;
import com.mediatek.drm.OmaDrmUtils;
import com.mediatek.drm.OmaDrmUtils.DrmProfile;
import com.mediatek.drm.OmaDrmClient;

public class DrmUtils {
    private static final String TAG = DrmUtils.class.getSimpleName();

    /**
     *
     * @param context
     * @param uri
     * @return true if the uri indicate a Drm image,false else
     */
    public static boolean isDrmImage(Context context, Uri uri) {
        if (!ContactsSystemProperties.MTK_DRM_SUPPORT) {
            Log.i(TAG, "[isDrmImage] not support drm...");
            return false;
        }
        OmaDrmClient drmClient = new OmaDrmClient(context);
        DrmProfile profile = OmaDrmUtils.getDrmProfile(context, uri, drmClient);
        boolean isDrm = profile.isDrm();
        drmClient.release();
        drmClient = null;
        Log.i(TAG, "[isDrmImage] isDrm:" + isDrm + ",uri:" + uri);
        return isDrm;
    }
}
