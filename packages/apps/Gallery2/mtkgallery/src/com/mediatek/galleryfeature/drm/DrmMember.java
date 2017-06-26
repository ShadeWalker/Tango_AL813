package com.mediatek.galleryfeature.drm;

import android.content.Context;

import com.mediatek.drm.OmaDrmStore;

import com.mediatek.galleryframework.base.ExtItem;
import com.mediatek.galleryframework.base.Layer;
import com.mediatek.galleryframework.base.MediaCenter;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.MediaMember;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.base.Player.OutputType;
import com.mediatek.galleryframework.util.MtkLog;

public class DrmMember extends MediaMember {
    private static final String CTA_DATA_PROTECTION_SUFFIX = ".mudp";
    private MediaCenter mMediaCenter;
    private static final String TAG  = "MtkGallery2/DrmMember";
    public DrmMember(Context context, MediaCenter center) {
        super(context);
        mMediaCenter = center;
    }

    public boolean isMatching(MediaData md) {
        boolean isDrm = md.isDRM != 0;
        String fileName = md.filePath;
        /// all DRM file name is end with .dcf,should not modify '.dcf'
        if (isDrm && (fileName != null && !fileName.endsWith(".dcf"))) {
            MtkLog.d(TAG, "DRM fileName = "+fileName);
            return false;
        }
        boolean isCTADataProtection = md.uri != null
                && md.uri.toString().endsWith(CTA_DATA_PROTECTION_SUFFIX);
        if (isCTADataProtection) {
            md.drmMethod = OmaDrmStore.DrmMethod.METHOD_FL;
            md.isDRM = 1;
        }
        return isDrm || isCTADataProtection;
    }

    
    public Player getPlayer(MediaData md, ThumbType type) {
        if (type == ThumbType.MIDDLE)
            return new DrmPlayer(mContext, md, OutputType.TEXTURE, mMediaCenter);
        return null;
    }

    public Layer getLayer() {
        return new DrmLayer(mMediaCenter);
    }

    public ExtItem getItem(MediaData md) {
        return new DrmItem(mContext, md, mMediaCenter);
    }

    public MediaData.MediaType getMediaType() {
        return MediaData.MediaType.DRM;
    }
}