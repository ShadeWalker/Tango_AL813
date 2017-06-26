package com.mediatek.galleryfeature.container;

import com.mediatek.galleryframework.base.MediaFilter;
import com.mediatek.galleryframework.base.MediaFilter.IFilter;

import android.content.Intent;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;

public class ConshotFilter implements IFilter {
    public void setFlagFromIntent(Intent intent, MediaFilter filter) {
    }

    public void setDefaultFlag(MediaFilter filter) {
        filter.setFlagEnable(MediaFilter.INCLUDE_CONSHOT_GROUP);
    }

    public String getWhereClauseForImage(int flag, int bucketID) {
        if ((flag & MediaFilter.INCLUDE_CONSHOT_GROUP) != 0
                && bucketID != MediaFilter.INVALID_BUCKET_ID) {
            StringBuilder sb = new StringBuilder();
            sb.append("( " + Images.Media.GROUP_ID + " = 0");
            sb.append(" OR (" + Images.Media.GROUP_ID + " IS NOT NULL"
                    + " AND " + ImageColumns.TITLE + " NOT LIKE 'IMG%CS')");
            sb.append(" OR " + Images.Media.GROUP_ID + " IS NULL)");
            sb.append(" OR ");
            sb.append("_id in (SELECT min(_id) FROM images WHERE ");
            sb.append(Images.Media.GROUP_ID + " != 0");
            sb.append(" AND " + Images.Media.TITLE + " LIKE 'IMG%CS'");
            sb.append(" AND ");
            sb.append(ImageColumns.BUCKET_ID + " = " + bucketID);
            sb.append(" GROUP BY " + Images.Media.GROUP_ID + ")");
            return sb.toString();
        }
        return null;
    }

    public String getWhereClauseForVideo(int flag, int bucketID) {
        return null;
    }

    public String getWhereClause(int flag, int bucketID) {
        return getWhereClauseForImage(flag, bucketID);
    }

    public String getDeleteWhereClauseForImage(int flag, int bucketID) {
        return null;
    }

    public String getDeleteWhereClauseForVideo(int flag, int bucketID) {
        return null;
    }

    public String convertFlagToString(int flag) {
        if ((flag & MediaFilter.INCLUDE_CONSHOT_GROUP) != 0) {
            return "INCLUDE_CONSHOT_GROUP, ";
        }
        return "";
    }
}
