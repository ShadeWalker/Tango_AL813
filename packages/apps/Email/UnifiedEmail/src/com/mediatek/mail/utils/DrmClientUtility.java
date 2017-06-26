package com.mediatek.mail.utils;

import android.content.Context;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

import com.android.emailcommon.utility.FeatureOption;
import com.android.mail.providers.Attachment;
import com.android.mail.utils.LogUtils;

import com.mediatek.drm.OmaDrmClient;
import com.mediatek.drm.OmaDrmStore;
import com.mediatek.drm.OmaDrmUtils;

public class DrmClientUtility {
    /** M: drm single instance*/
    private static OmaDrmClient sDrmClientInstance = null;

    /**
     * M: get Drm client single instance.
     * Even it is a single instance, need release it when not use it any more.
     */
    private static synchronized OmaDrmClient getDrmClientInstance(
            Context context) {
        if (sDrmClientInstance == null) {
            LogUtils.w(LogUtils.TAG, "getDrmClientInstance, create new instance");
            sDrmClientInstance = new OmaDrmClient(context);
        }
        return sDrmClientInstance;
    }

    /**
     * M: Get the original MimeType of DRM file.
     * @param context the context to create DrmClient
     * @param uri the content URI
     * @return The original MimeType of DRM file if it was DRM file, otherwise return null.
     */
    public static String getDRMOriginalMimeType(Context context, Uri uri) {
        String type = null;
        if (FeatureOption.MTK_DRM_APP) {
            OmaDrmClient drmClient = getDrmClientInstance(context);
            OmaDrmUtils.DrmProfile profile = OmaDrmUtils.getDrmProfile(context, uri, drmClient);
            if (profile.isDrm()) {
                type = drmClient.getOriginalMimeType(uri);
                LogUtils.d(LogUtils.TAG, "The original type of [%s] is %s.", uri, type);
            }
        }
        return type;
    }

    /**
     * M: check if the attachment is drm protected.
     */
    public static boolean isDrmProtected(Context context, Uri uri) {
        boolean checkResult = false;
        if (FeatureOption.MTK_DRM_APP && uri != null) {
            OmaDrmClient drmClient = getDrmClientInstance(context);
            OmaDrmUtils.DrmProfile profile = OmaDrmUtils.getDrmProfile(context, uri, drmClient);
            // Only normal file and SD type drm file can be forwarded
            if (profile.isDrm()
                    && profile.getMethod() != OmaDrmStore.DrmMethod.METHOD_SD
                    && profile.getMethod() != OmaDrmStore.DrmMethod.METHOD_NONE) {
                LogUtils.w(LogUtils.TAG, "Not add attachment [%s], for Drm protected.", uri);
                checkResult = true;
            }
        }
        return checkResult;
    }

    /**
     * M: filter drm attachment for the given source attachments.
     */
    public static List<Attachment> filterDrmAttachments(Context context, List<Attachment> attachments) {
        if (!FeatureOption.MTK_DRM_APP || attachments == null || attachments.size() == 0) {
            return attachments;
        }
        List<Attachment> filterAttachments = new ArrayList<Attachment>();
        for (Attachment att : attachments) {
            if (isDrmProtected(context, att.contentUri)) {
                LogUtils.w(LogUtils.TAG,
                        "Not add attachment [%s], for Drm protected.",
                        att.contentUri);
            } else {
                filterAttachments.add(att);
            }
        }
        return filterAttachments;
    }
}
