package com.mediatek.galleryfeature.filter;

import com.mediatek.galleryframework.base.MediaFilter;
import com.mediatek.galleryframework.base.MediaFilter.IFilter;

import android.content.Intent;
import android.provider.MediaStore.Video.VideoColumns;

public class Video4kFilter implements IFilter {
    public void setFlagFromIntent(Intent intent, MediaFilter filter) {
        if (intent == null)
            return;
        String action = intent.getAction();
        if (Intent.ACTION_GET_CONTENT.equalsIgnoreCase(action)
                || Intent.ACTION_PICK.equalsIgnoreCase(action)
                || "com.mediatek.action.PICK_VIDEO_FOLDER"
                        .equalsIgnoreCase(action)) {
            // hide 4K video
            filter.setFlagDisable(MediaFilter.INCLUDE_4K_VIDEO);
        } else {
            // show 4K video
            filter.setFlagEnable(MediaFilter.INCLUDE_4K_VIDEO);
        }
    }

    public void setDefaultFlag(MediaFilter filter) {
        filter.setFlagEnable(MediaFilter.INCLUDE_4K_VIDEO);
    }

    public String getWhereClauseForImage(int flag, int bucketID) {
        return null;
    }

    public String getWhereClauseForVideo(int flag, int bucketID) {
        // exclude 4K video
        if ((flag & MediaFilter.INCLUDE_4K_VIDEO) == 0) {
            String str1 = MediaFilter.AND(VideoColumns.WIDTH + " <= 1920",
                    VideoColumns.HEIGHT + " <= 1920");
            String str2 = MediaFilter.OR(VideoColumns.WIDTH + " IS NULL",
                    VideoColumns.HEIGHT + " IS NULL");
            String str3 = VideoColumns.MIME_TYPE + " NOT LIKE 'video%'";
            return MediaFilter.OR(MediaFilter.OR(str1, str2), str3);
        }
        // include 4K video
        return null;
    }

    public String getWhereClause(int flag, int bucketID) {
        return getWhereClauseForVideo(flag, bucketID);
    }

    public String getDeleteWhereClauseForImage(int flag, int bucketID) {
        return null;
    }

    public String getDeleteWhereClauseForVideo(int flag, int bucketID) {
        return getWhereClauseForVideo(flag, bucketID);
    }

    public String convertFlagToString(int flag) {
        if ((flag & MediaFilter.INCLUDE_4K_VIDEO) != 0) {
            return "INCLUDE_4K_VIDEO, ";
        }
        return "";
    }
}
