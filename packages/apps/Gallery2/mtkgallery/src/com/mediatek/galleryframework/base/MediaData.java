package com.mediatek.galleryframework.base;

import java.util.ArrayList;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Images.ImageColumns;

import com.mediatek.galleryframework.util.MtkLog;

public class MediaData {
    private static final String TAG = "MtkGallery2/MediaData";

    // not mark best shot yet
    public static final int BEST_SHOT_NOT_MARK = 0;
    // has marked best shot, but mark as false
    public static final int BEST_SHOT_MARK_FALSE = 1;
    // has marked best shot, but mark as true
    public static final int BEST_SHOT_MARK_TRUE = 2;

    public static enum MediaType {
        INVALID, NORMAL, PANORAMA, CONTAINER, MAV, LIVEPHOTO, VIDEO, GIF, DRM , REFOCUS
    }

    public static enum SubType {
        INVALID, MOTRACK, CONSHOT,
    }

    // image
    public static final int IMAGE_INDEX_ID = 0;
    public static final int IMAGE_INDEX_CAPTION = 1;
    public static final int IMAGE_INDEX_MIME_TYPE = 2;
    public static final int IMAGE_INDEX_LATITUDE = 3;
    public static final int IMAGE_INDEX_LONGITUDE = 4;
    public static final int IMAGE_INDEX_DATE_TAKEN = 5;
    public static final int IMAGE_INDEX_DATE_ADDED = 6;
    public static final int IMAGE_INDEX_DATE_MODIFIED = 7;
    public static final int IMAGE_INDEX_DATA = 8;
    public static final int IMAGE_INDEX_ORIENTATION = 9;
    public static final int IMAGE_INDEX_BUCKET_ID = 10;
    public static final int IMAGE_INDEX_SIZE = 11;
    public static final int IMAGE_INDEX_WIDTH = 12;
    public static final int IMAGE_INDEX_HEIGHT = 13;
    public static final int IMAGE_INDEX_IS_DRM = 14;
    public static final int IMAGE_INDEX_DRM_METHOD = 15;
    public static final int IMAGE_INDEX_GROUP_ID = 16;
    public static final int IMAGE_INDEX_GROUP_INDEX = 17;
    public static final int IMAGE_INDEX_IS_BEST_SHOT = 18;
    public static final int IMAGE_INDEX_GROUP_COUNT = 19;
    public static final int IMAGE_INDEX_REFOCUS = 20;

    private static String[] IMAGE_PROJECTION = {
        ImageColumns._ID,           // 0
        ImageColumns.TITLE,         // 1
        ImageColumns.MIME_TYPE,     // 2
        ImageColumns.LATITUDE,      // 3
        ImageColumns.LONGITUDE,     // 4
        ImageColumns.DATE_TAKEN,    // 5
        ImageColumns.DATE_ADDED,    // 6
        ImageColumns.DATE_MODIFIED, // 7
        ImageColumns.DATA,          // 8
        ImageColumns.ORIENTATION,   // 9
        ImageColumns.BUCKET_ID,     // 10
        ImageColumns.SIZE,          // 11
        MediaColumns.WIDTH,         // 12
        MediaColumns.HEIGHT,        // 13
        ImageColumns.IS_DRM,        // 14
        ImageColumns.DRM_METHOD,    // 15
        ImageColumns.GROUP_ID,      // 16
        ImageColumns.GROUP_INDEX,   // 17
        ImageColumns.IS_BEST_SHOT,  // 18
        ImageColumns.GROUP_COUNT,   // 19
        ImageColumns.CAMERA_REFOCUS,// 20
    };

    public MediaType mediaType = MediaType.INVALID;
    public SubType subType = SubType.INVALID;
    public ArrayList<MediaData> relateData;
    public int width;
    public int height;
    public int orientation;
    public String caption = "";
    public String mimeType = "";
    public int isDRM;
    public int drmMethod;
    public long groupID;
    public int groupIndex;
    public int groupCount;
    public int bestShotMark = BEST_SHOT_NOT_MARK;
    public String filePath = "";
    public Uri uri;
    public boolean isVideo = false;
    public boolean isLivePhoto = false;
    public boolean isSlowMotion = false;
    public int bucketId;
    public long id;
    public long fileSize;
    public int duration;
    public boolean isRefocus;
    public long dateModifiedInSec;

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[mediaType = " + mediaType + ",");
        sb.append("width = " + width + ",");
        sb.append("height = " + height + ",");
        sb.append("orientation = " + orientation + ",");
        sb.append("mimeType = " + mimeType + ",");
        sb.append("isDRM = " + isDRM + ",");
        sb.append("drmMethod = " + drmMethod + ",");
        sb.append("groupID = " + groupID + ",");
        sb.append("groupIndex = " + groupIndex + ",");
        sb.append("groupCount = " + groupCount + ",");
        sb.append("bestShotMark = " + bestShotMark + ",");
        sb.append("filePath = " + filePath + ",");
        sb.append("uri = " + uri + ",");
        sb.append("isVideo = " + isVideo + ",");
        sb.append("isLivePhoto = " + isLivePhoto + ",");
        sb.append("isSlowMotion = " + isSlowMotion + ",");
        sb.append("bucketId = " + bucketId + ",");
        sb.append("id = " + id + ",");
        sb.append("fileSize = " + fileSize + ",");
        sb.append("duration = " + duration + ",");
        sb.append("relateData = " + relateData + ", ");
        sb.append("dateModifiedInSec = " + dateModifiedInSec + ", ");
        sb.append("isRefocus = " + isRefocus + "]");
        return sb.toString();
    }

    public boolean equals(MediaData data) {
        if (data == null)
            return false;

        if (this == data)
            return true;

        if (mediaType != data.mediaType
                || subType != data.subType
                || width != data.width
                || height != data.height
                || orientation != data.orientation
                || (caption == null && data.caption != null)
                || (caption != null && data.caption == null)
                || (caption != null && !caption.equals(data.caption))
                || (mimeType == null && data.mimeType != null)
                || (mimeType != null && data.mimeType == null)
                || (mimeType != null && !mimeType.equals(data.mimeType))
                || isDRM != data.isDRM
                || drmMethod != data.drmMethod
                || groupID != data.groupID
                || groupIndex != data.groupIndex
                || groupCount != data.groupCount
                || bestShotMark != data.bestShotMark
                || (filePath == null && data.filePath != null)
                || (filePath != null && data.filePath == null)
                || (filePath != null && !filePath.equals(data.filePath))
                || (uri == null && data.uri != null)
                || (uri != null && data.uri == null)
                || (uri != null && !uri.equals(data.uri))
                || isVideo != data.isVideo
                || isLivePhoto != data.isLivePhoto
                || isSlowMotion != data.isSlowMotion
                || bucketId != data.bucketId
                || id != data.id
                || fileSize != data.fileSize
                || duration != data.duration
                || isRefocus != data.isRefocus
                || (relateData == null && data.relateData != null)
                || (relateData != null && data.relateData == null)
                || (relateData != null && data.relateData != null
                        && relateData.size() != data.relateData.size())
                || (dateModifiedInSec != data.dateModifiedInSec))
            return false;
        return true;
    }

    public int hashCode() {
        int hash = 17;
        hash = 31 * hash + (mediaType == null ? 0 : mediaType.hashCode());
        hash = 31 * hash + (subType == null ? 0 : subType.hashCode());
        hash = 31 * hash + width;
        hash = 31 * hash + height;
        hash = 31 * hash + orientation;
        hash = 31 * hash + (caption == null ? 0 : caption.hashCode());
        hash = 31 * hash + (mimeType == null ? 0 : mimeType.hashCode());
        hash = 31 * hash + isDRM;
        hash = 31 * hash + drmMethod;
        hash = 31 * hash + (int) (groupID ^ (groupID >>> 32));
        hash = 31 * hash + groupIndex;
        hash = 31 * hash + groupCount;
        hash = 31 * hash + bestShotMark;
        hash = 31 * hash + (filePath == null ? 0 : filePath.hashCode());
        hash = 31 * hash + (uri == null ? 0 : uri.hashCode());
        hash = 31 * hash + (isVideo ? 0 : 1);
        hash = 31 * hash + (isLivePhoto ? 0 : 1);
        hash = 31 * hash + (isSlowMotion ? 0 : 1);
        hash = 31 * hash + bucketId;
        hash = 31 * hash + (int) (id ^ (id >>> 32));
        hash = 31 * hash + (int) (fileSize ^ (fileSize >>> 32));
        hash = 31 * hash + duration;
        hash = 31 * hash + (isRefocus ? 0 : 1);
        hash = 31 * hash + (relateData == null ? 0 : relateData.size());
        hash = 31 * hash + (int) (dateModifiedInSec ^ (dateModifiedInSec >>> 32));
        return hash;
    }

    public static ArrayList<MediaData> queryImageMediaData(Context context,
            Uri uri, String whereClause, String[] whereClauseArgs,
            String orderClause) {
        ArrayList<MediaData> list = new ArrayList<MediaData>();
        Cursor cursor = context.getContentResolver().query(uri,
                IMAGE_PROJECTION, whereClause, whereClauseArgs, orderClause);
        if (cursor == null) {
            MtkLog.w(TAG, "<queryImageMediaData> query fail: " + uri);
            return list;
        }
        try {
            while (cursor.moveToNext()) {
                MediaData data = parseImage(cursor);
                list.add(data);
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    public static Cursor queryImage(Context context,
            Uri uri, String whereClause, String[] whereClauseArgs,
            String orderClause) {
        Cursor cursor = context.getContentResolver().query(uri,
                IMAGE_PROJECTION, whereClause, whereClauseArgs, orderClause);
        if (cursor != null && cursor.moveToFirst()) {
            return cursor;
        }
        return null;
    }

    public static ArrayList<MediaData> parseImageMediaDatas(Cursor cursor,
            boolean closeCursor) {
        if (cursor == null || !cursor.moveToFirst())
            return null;
        ArrayList<MediaData> list = new ArrayList<MediaData>();
        do {
            MediaData data = parseImage(cursor);
            list.add(data);
        } while (cursor.moveToNext());
        if (closeCursor) {
            cursor.close();
        }
        return list;
    }

    public static MediaData parseImage(Cursor cursor) {
        MediaData data = new MediaData();
        data.width = cursor.getInt(IMAGE_INDEX_WIDTH);
        data.height = cursor.getInt(IMAGE_INDEX_HEIGHT);
        data.orientation = cursor.getInt(IMAGE_INDEX_ORIENTATION);
        data.caption = cursor.getString(IMAGE_INDEX_CAPTION);
        data.mimeType = cursor.getString(IMAGE_INDEX_MIME_TYPE);
        data.isDRM = cursor.getInt(IMAGE_INDEX_IS_DRM);
        data.drmMethod = cursor.getInt(IMAGE_INDEX_DRM_METHOD);
        data.groupID = cursor.getLong(IMAGE_INDEX_GROUP_ID);
        data.groupCount = cursor.getInt(IMAGE_INDEX_GROUP_COUNT);
        data.groupIndex = cursor.getInt(IMAGE_INDEX_GROUP_INDEX);
        data.bestShotMark = cursor.getInt(IMAGE_INDEX_IS_BEST_SHOT);
        data.filePath = cursor.getString(IMAGE_INDEX_DATA);
        data.bucketId = cursor.getInt(IMAGE_INDEX_BUCKET_ID);
        data.id = cursor.getLong(IMAGE_INDEX_ID);
        data.fileSize = cursor.getLong(IMAGE_INDEX_SIZE);
        data.isRefocus = cursor.getInt(IMAGE_INDEX_REFOCUS) != 0;
        data.dateModifiedInSec = cursor.getLong(IMAGE_INDEX_DATE_MODIFIED);
        return data;
    }
}
