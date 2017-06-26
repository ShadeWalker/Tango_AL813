package com.mediatek.galleryfeature.drm;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.net.Uri;

import com.mediatek.dcfdecoder.DcfDecoder;
import com.mediatek.drm.OmaDrmClient;
import com.mediatek.drm.OmaDrmStore;
import com.mediatek.drm.OmaDrmUiUtils;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.util.MtkLog;

public class DrmHelper {
    private static final String TAG = "MtkGallery2/DrmHelper";
    private static OmaDrmClient sClient = null;

    public static OmaDrmClient getOmaDrmClient(Context context) {
        if (sClient == null)
            sClient = new OmaDrmClient(context);
        return sClient;
    }

    public static boolean checkRightsStatusValid(Context context,
            String filePath, int action) {
        if (null == filePath) {
            MtkLog.e(TAG, "<checkRightsStatus> got null file path");
        }
        int valid = getOmaDrmClient(context).checkRightsStatus(filePath, action);
        return valid == OmaDrmStore.RightsStatus.RIGHTS_VALID;
    }

    public static int checkRightsStatus(Context context, String path,
            int action) {
        if (null == path) {
            MtkLog.e(TAG, "<checkRightsStatus> got null file path");
        }
        int res = getOmaDrmClient(context).checkRightsStatus(path, action);
        return res;
    }

    public static boolean hasRightsToShow(Context context, MediaData data) {
        if (data.isVideo) {
            return checkRightsStatusValid(context, data.filePath,
                    OmaDrmStore.Action.PLAY);
        } else {
            return checkRightsStatusValid(context, data.filePath,
                    OmaDrmStore.Action.DISPLAY);
        }
    }

    public static byte[] forceDecryptFile(String filePath, boolean consume) {
        if (null == filePath
                || (!filePath.toLowerCase().endsWith(".dcf") && !filePath
                        .toLowerCase().endsWith(".mudp")))
            return null;
        DcfDecoder dcfDecoder = new DcfDecoder();
        return dcfDecoder.forceDecryptFile(filePath, consume);
    }

    public static Bitmap forceDecodeDrmUri(ContentResolver cr, Uri drmUri,
            BitmapFactory.Options options, boolean consume) {
        if (null == options) {
            options = new BitmapFactory.Options();
        }
        if (options.mCancel) {
            return null;
        }
        DcfDecoder tempDcfDecoder = new DcfDecoder();
        return tempDcfDecoder.forceDecodeUri(cr, drmUri, options, consume);
    }

    // when show drm media whose type is not FL, show extra lock
    public static int getDrmIconResourceID(Context context, MediaData data) {
        if (data.drmMethod == OmaDrmStore.DrmMethod.METHOD_FL)
            return 0;
        if (DrmHelper.hasRightsToShow(context, data)) {
            return com.mediatek.internal.R.drawable.drm_green_lock;
        } else {
            return com.mediatek.internal.R.drawable.drm_red_lock;
        }
    }

    public static boolean isTimeIntervalMedia(Context context, MediaData data) {
        int action = OmaDrmStore.Action.DISPLAY;
        if (data.isVideo) {
            action = OmaDrmStore.Action.PLAY;
        }
        ContentValues values = getOmaDrmClient(context).getConstraints(data.filePath, action);
        if (null != values) {
            Integer startTime = values.getAsInteger(OmaDrmStore.ConstraintsColumns.LICENSE_START_TIME);
            Integer expiryTime = values.getAsInteger(OmaDrmStore.ConstraintsColumns.LICENSE_EXPIRY_TIME);
            return ((null != startTime && -1 != startTime.intValue())
                    || (null != expiryTime && -1 != expiryTime.intValue())); 
        } else {
            return false;
        }
    }

    public static void clearToken(Context context, String tokenKey, String token) {
        getOmaDrmClient(context).clearToken(tokenKey, token);
    }

    public static boolean isTokenValid(Context context, String tokenKey,
            String token) {
        boolean isValid = getOmaDrmClient(context).isTokenValid(tokenKey, token);
        return isValid;
    }

    public static void showProtectionInfoDialog(Context context, Uri uri) {
        OmaDrmUiUtils.showProtectionInfoDialog(context, uri);
    }

    public static void decodeBounds(String filePath, Options options, boolean consume) {
        if (null == filePath
                || (!filePath.toLowerCase().endsWith(".dcf") && !filePath
                        .toLowerCase().endsWith(".mudp"))) {
            return;
        }
        DcfDecoder dcfDecoder = new DcfDecoder();
        dcfDecoder.decodeFile(filePath, options, consume);
    }

}
