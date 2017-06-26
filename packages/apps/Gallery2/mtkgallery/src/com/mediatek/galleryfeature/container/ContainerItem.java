package com.mediatek.galleryfeature.container;

import java.io.File;
import java.util.ArrayList;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore.Images;

import com.android.gallery3d.R;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.ExtItem;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.base.MediaData.SubType;

public class ContainerItem extends ExtItem {
    private MediaData mData;
    private Context mContext;

    public ContainerItem(Context context, MediaData md) {
        super(md);
        mData = md;
        mContext = context;
    }

    @Override
    public ArrayList<SupportOperation> getSupportedOperations() {
        ArrayList<SupportOperation> res = new ArrayList<SupportOperation>();
        if (mData.subType == SubType.MOTRACK) {
            res.add(SupportOperation.EXPORT);
        }
        return res;
    }

    @Override
    public ArrayList<SupportOperation> getNotSupportedOperations() {
        ArrayList<MediaData> relateData = mData.relateData;
        if (relateData != null) {
            ArrayList<SupportOperation> nsp = new ArrayList<SupportOperation>();
            nsp.add(SupportOperation.CROP);
            nsp.add(SupportOperation.EDIT);
            nsp.add(SupportOperation.ROTATE);
            nsp.add(SupportOperation.FULL_IMAGE);
            // motion item support set as
            if (mData.subType == SubType.CONSHOT)
                nsp.add(SupportOperation.SETAS);
            return nsp;
        }
        return null;
    }

    @Override
    public void delete() {
        ArrayList<MediaData> relateData = mData.relateData;
        if (relateData != null) {
            // delete files
            for (MediaData data : relateData) {
                File file = new File(data.filePath);
                if (!file.exists()) continue;
                file.delete();
            }

            // delete record in media database
            // To avoid refresh frequently,
            // delete items in media database at a time.
            // Motion track image does not have record in media database, 
            // so do noting.
            if (mData.subType == SubType.CONSHOT)
                ContainerHelper.deleteConshotDatas(mContext, mData.groupID, mData.bucketId);
        }
    }

    @Override
    public Uri[] getContentUris() {
        ArrayList<MediaData> relateData = mData.relateData;
        if (relateData != null) {
            ArrayList<Uri> uriList = new ArrayList<Uri>();

            if (mData.subType == MediaData.SubType.MOTRACK) {
                uriList.add(Uri.parse("file:/" + mData.filePath));
            }

            for (MediaData data : relateData) {
                uriList.add(Uri.parse("file:/" + data.filePath));
            }

            if (mData.subType == MediaData.SubType.MOTRACK) {
                // internal media
                String workPath = new File(mData.filePath).getParent();
                uriList.add(Uri.parse("file:/" + workPath + "/.ConShots/InterMedia/"
                        + mData.caption + "IT"));
                // original image
                File originalDir = new File(workPath + "/.ConShots/" + mData.caption);
                if (originalDir.exists()) {
                    File[] allFiles = originalDir.listFiles();
                    if (allFiles != null) {
                        for (File file : allFiles) {
                            uriList.add(Uri.parse("file:/" + file.getPath()));
                        }
                    }
                }
            }

            Uri[] uris = uriList.toArray(new Uri[uriList.size()]);
            return uris;
        }
        return null;
    }

    @Override
    public String[] getDetails() {
        String title = null;
        if (mData.subType == MediaData.SubType.CONSHOT) {
            title = mContext.getResources()
                    .getString(R.string.m_conshots_title);
        } else if (mData.subType == MediaData.SubType.MOTRACK) {
            title = mContext.getResources()
                    .getString(R.string.m_motion_title);
        } else {
            return null;
        }
        String[] res = new String[1];
        res[0] = title;
        return res;
    }

    @Override
    public boolean isNeedToCacheThumb(ThumbType thumbType) {
        if (mData.subType == MediaData.SubType.MOTRACK)
            return false;
        return true;
    }

    @Override
    public boolean isAllowPQWhenDecodeCache(ThumbType thumbType) {
        return false;
    }
}