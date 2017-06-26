package com.mediatek.galleryfeature.container;

import android.content.Context;

import com.mediatek.galleryframework.base.Layer;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryframework.base.ExtItem;
import com.mediatek.galleryframework.base.Generator;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.MediaMember;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.util.MtkLog;

public class ContainerMember extends MediaMember {
    private static final String TAG = "MtkGallery2/MediaMember";
    private Layer mLayer;

    public ContainerMember(Context context) {
        super(context);
    }

    @Override
    public boolean isMatching(MediaData md) {
        if (md.groupID != 0 && md.caption.matches("^IMG.*CS$")) {
            md.relateData = ContainerHelper.getConShotDatas(mContext, md.groupID, md.bucketId);
            if (md.relateData == null || md.relateData.size() <= 1) {
                md.relateData = null;
                return false;
            }
            md.subType = MediaData.SubType.CONSHOT;
            return true;
        }

        if (md.caption.matches("^IMG.*MT$")) {
            MtkLog.w(TAG, "match motion track");
            md.relateData = ContainerHelper.getMoTrackDatas(md, false);
            if (md.relateData == null) return false;
            md.subType = MediaData.SubType.MOTRACK;
            return true;
        }
        return false;
    }

    @Override
    public Player getPlayer(MediaData md, ThumbType type) {
        return new ContainerPlayer(mContext, md, type);
    }

    @Override
    public ExtItem getItem(MediaData md) {
        return new ContainerItem(mContext, md);
    }

    public MediaData.MediaType getMediaType() {
        return MediaData.MediaType.CONTAINER;
    }

    @Override
    public Layer getLayer() {
        if (mLayer == null)
            mLayer = new ContainerLayer();
        return mLayer;
    }

    @Override
    public Generator getGenerator() {
        return new ContainerVideoGenerator(mContext);
    }
}
