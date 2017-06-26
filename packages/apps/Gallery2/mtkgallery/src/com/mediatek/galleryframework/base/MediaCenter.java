package com.mediatek.galleryframework.base;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import com.mediatek.galleryframework.base.MediaData.MediaType;
import com.mediatek.galleryframework.util.MtkLog;

public class MediaCenter {
    private static final String TAG = "MtkGallery2/MediaCenter";

    private LinkedHashMap<MediaType, MediaMember> mCreatorMap = new LinkedHashMap<MediaType, MediaMember>();
    private LinkedHashMap<MediaType, Layer> mLayerMap = new LinkedHashMap<MediaType, Layer>();

    public synchronized void registerMedias(ArrayList<MediaMember> memberList) {
        MtkLog.i(TAG, "<registerMedias> clear all at first");
        mCreatorMap.clear();
        for (MediaMember member : memberList) {
            MtkLog.i(TAG, "<registerMedias> put member = " + member);
            mCreatorMap.put(member.getMediaType(), member);
        }
    }

    public ExtItem getItem(MediaData md) {
        MediaMember mb = getMember(md);
        if (mb == null) {
            return null;
        }
        return mb.getItem(md);
    }

    public Player getPlayer(MediaData md, ThumbType type) {
        MediaMember mb = getMember(md);
        if (mb == null)
            return null;
        return mb.getPlayer(md, type);
    }

    public Generator getGenerator(MediaData md) {
        MediaMember mb = getMember(md);
        if (mb == null)
            return null;
        return mb.getGenerator();
    }

    public Layer getLayer(MediaData md) {
        MediaMember mb = getMember(md);
        if (mb == null)
            return null;
        // To ensure every media type has only one instance of layer,
        // we save layer in hash map
        Layer layer = mLayerMap.get(mb.getMediaType());
        return layer;
    }

    public ExtItem getRealItem(MediaData md) {
        MediaMember mb = getRealMember(md);
        if (mb == null) {
            return null;
        }
        return mb.getItem(md);
    }

    public Player getRealPlayer(MediaData md, ThumbType type) {
        MediaMember mb = getRealMember(md);
        if (mb == null)
            return null;
        return mb.getPlayer(md, type);
    }

    public Generator getRealGenerator(MediaData md) {
        MediaMember mb = getRealMember(md);
        if (mb == null)
            return null;
        return mb.getGenerator();
    }

    public Layer getRealLayer(MediaData md) {
        MediaMember mb = getRealMember(md);
        if (mb == null)
            return null;
        // To ensure every media type has only one instance of layer,
        // we save layer in hash map
        Layer layer = mLayerMap.get(mb.getMediaType());
        return layer;
    }

    public synchronized final LinkedHashMap<MediaType, Layer> getAllLayer() {
        mLayerMap = new LinkedHashMap<MediaType, Layer>();
        Iterator<Entry<MediaType, MediaMember>> itr = mCreatorMap.entrySet()
                .iterator();
        while (itr.hasNext()) {
            MediaMember mm = itr.next().getValue();
            mLayerMap.put(mm.getMediaType(), mm.getLayer());
        }
        return mLayerMap;
    }

    private synchronized MediaMember getMember(MediaData md) {
        if (md.mediaType != MediaData.MediaType.INVALID) {
            return mCreatorMap.get(md.mediaType);
        }
        Iterator<Entry<MediaType, MediaMember>> itr = mCreatorMap.entrySet()
                .iterator();
        while (itr.hasNext()) {
            Entry<MediaType, MediaMember> entry = itr.next();
            if (entry.getValue().isMatching(md)) {
                md.mediaType = entry.getKey();
                return entry.getValue();
            }
        }
        MtkLog.i(TAG, "<getMember> mediadata = " + md + ", return null");
        return null;
    }

    private synchronized MediaMember getRealMember(MediaData md) {
        try {
            Iterator<Entry<MediaType, MediaMember>> itr = mCreatorMap
                    .entrySet().iterator();
            while (itr.hasNext()) {
                Entry<MediaType, MediaMember> entry = itr.next();
                if (entry.getKey() != MediaData.MediaType.DRM
                        && entry.getValue().isMatching(md)) {
                    return entry.getValue();
                }
            }
        } catch (Exception e) {
            MtkLog.i(TAG, "<getRealMember> exception: " + e);
        }
        MtkLog.i(TAG, "<getRealMember> mediadata = " + md + ", return null");
        return null;
    }
}
