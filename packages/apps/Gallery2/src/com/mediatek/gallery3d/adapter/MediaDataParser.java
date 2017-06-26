package com.mediatek.gallery3d.adapter;

import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.LocalVideo;
import com.android.gallery3d.data.UriImage;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.util.Utils;

import android.content.ContentResolver;
import android.database.Cursor;

public class MediaDataParser {
    private static final String TAG = "MtkGallery2/MediaDataParser";

    public static MediaData parseLocalImageMediaData(Cursor cursor) {
        MediaData data = new MediaData();
        data.width = cursor.getInt(LocalImage.INDEX_WIDTH);
        data.height = cursor.getInt(LocalImage.INDEX_HEIGHT);
        data.orientation = cursor.getInt(LocalImage.INDEX_ORIENTATION);
        data.caption = cursor.getString(LocalImage.INDEX_CAPTION);
        data.mimeType = cursor.getString(LocalImage.INDEX_MIME_TYPE);
        data.isDRM = cursor.getInt(LocalImage.INDEX_IS_DRM);
        data.drmMethod = cursor.getInt(LocalImage.INDEX_DRM_METHOD);
        data.groupID = cursor.getLong(LocalImage.INDEX_GROUP_ID);
        data.groupCount = cursor.getInt(LocalImage.INDEX_GROUP_COUNT);
        data.groupIndex = cursor.getInt(LocalImage.INDEX_GROUP_INDEX);
        data.bestShotMark = cursor.getInt(LocalImage.INDEX_IS_BEST_SHOT);
        data.filePath = cursor.getString(LocalImage.INDEX_DATA);
        data.bucketId = cursor.getInt(LocalImage.INDEX_BUCKET_ID);
        data.id = cursor.getLong(LocalImage.INDEX_ID);
        data.fileSize = cursor.getLong(LocalImage.INDEX_SIZE);
        data.isRefocus = cursor.getInt(LocalImage.INDEX_CAMERA_REFOCUS) != 0;
        data.dateModifiedInSec = cursor.getLong(LocalImage.INDEX_DATE_MODIFIED);
        return data;
    }

    public static MediaData parseLocalVideoMediaData(LocalVideo item, Cursor cursor) {
        MediaData data = new MediaData();
        data.width = item.width;
        data.height = item.height;
        data.orientation = cursor.getInt(LocalVideo.INDEX_VIDEO_ORIENTATION);
        data.mimeType = cursor.getString(LocalVideo.INDEX_MIME_TYPE);
        data.isDRM = cursor.getInt(LocalVideo.INDEX_IS_DRM);
        data.drmMethod = cursor.getInt(LocalVideo.INDEX_DRM_METHOD);
        data.filePath = cursor.getString(LocalVideo.INDEX_DATA);
        data.isVideo = true;
        data.isLivePhoto = cursor.getInt(LocalVideo.INDEX_IS_LIVEPHOTO) != 0;
        data.isSlowMotion = Utils.parseSlowMotionFromString(cursor
                .getString(LocalVideo.INDEX_IS_SLOWMOTION));
        data.duration = cursor.getInt(LocalVideo.INDEX_DURATION);
        data.caption = cursor.getString(LocalImage.INDEX_CAPTION);
        data.dateModifiedInSec = cursor.getLong(LocalVideo.INDEX_DATE_MODIFIED);
        return data;
    }

    public static MediaData parseUriImageMediaData(UriImage item) {
        MediaData data = new MediaData();
        data.mimeType = item.getMimeType();
        data.uri = item.getContentUri();
        if (data.uri != null
                && ContentResolver.SCHEME_FILE.equals(data.uri.getScheme())) {
            data.filePath = data.uri.getPath();
        }
        data.width = item.getWidth();
        data.height = item.getHeight();
        data.orientation = item.getRotation();
        return data;
    }
    
    public static MediaData parseLocalImageMediaData(LocalImage item) {
        MediaData data = new MediaData();
        data.mimeType = item.getMimeType();
        data.filePath = item.getFilePath();
        data.uri = item.getContentUri();
        data.width = item.getWidth();
        data.height = item.getHeight();
        data.orientation = item.getRotation();
        return data;
    }
}
