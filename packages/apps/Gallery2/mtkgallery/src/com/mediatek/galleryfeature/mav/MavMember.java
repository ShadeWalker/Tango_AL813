package com.mediatek.galleryfeature.mav;

import android.content.Context;

import com.mediatek.galleryfeature.config.FeatureConfig;
import com.mediatek.galleryframework.base.ExtItem;
import com.mediatek.galleryframework.base.Generator;
import com.mediatek.galleryframework.base.Layer;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.MediaMember;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.gl.GLIdleExecuter;
import com.mediatek.mpodecoder.MpoDecoder;
public class MavMember extends MediaMember {
    private Layer mLayer;
    private GLIdleExecuter mGLExecuter;
    
    public MavMember(Context context, GLIdleExecuter exe) {
        super(context);
        mGLExecuter = exe;
    }
    public MavMember(Context context) {
        super(context);
    }


    @Override
    public boolean isMatching(MediaData md) {
        int mMpoSubType = -1;
        if (md.mimeType.equals("image/mpo")) {
            MpoDecoder mpoDecoder = null;
            try {
                mpoDecoder = MpoDecoder.decodeFile(md.filePath);
                if (null != mpoDecoder) {
                    mMpoSubType = mpoDecoder.getMpoSubType();
                }
            } finally {
                if (null != mpoDecoder) {
                    mpoDecoder.close();
                }
            }
        }
        return (MpoDecoder.TYPE_MAV == mMpoSubType);
    }

    @Override
    public Player getPlayer(MediaData md, ThumbType type) {
        if (type == ThumbType.MIDDLE) {
            return new MavPlayer(mContext, md, Player.OutputType.TEXTURE, type, mGLExecuter);
        } else if (type == ThumbType.MICRO || type == ThumbType.FANCY) {
            if (!FeatureConfig.supportThumbnailMAV
                    || FeatureConfig.isLowRamDevice) {
                return null;
            }
            // mav will be displayed as Micro thumbnail at fancy homepage
            return new MavPlayer(mContext, md, Player.OutputType.TEXTURE, /*type*/ThumbType.MICRO , mGLExecuter);
        }
        return null;
    }

    @Override
    public ExtItem getItem(MediaData md) {
        return new MavItem(md);
    }

    @Override
    public Layer getLayer() {
        if (mLayer == null)
            mLayer = new MavLayer();
        return mLayer;
    }

    public MediaData.MediaType getMediaType() {
        return MediaData.MediaType.MAV;
    }

    @Override
    public Generator getGenerator() {
        return new MavToVideoGenerator(mContext);
    }
}
