package com.mediatek.galleryframework.base;

import java.util.ArrayList;

import com.mediatek.galleryframework.util.MtkLog;

import android.content.Intent;

public class MediaFilter {
    private static final String TAG = "MtkGallery2/MediaFilter";

    public static final int INVALID_BUCKET_ID = 0xFFFFFFFF;

    public static final int INCLUDE_DRM_FL = (1 << 1);
    public static final int INCLUDE_DRM_CD = (1 << 2);
    public static final int INCLUDE_DRM_SD = (1 << 3);
    public static final int INCLUDE_DRM_FLDCF = (1 << 4);
    public static final int INCLUDE_DRM_ALL = INCLUDE_DRM_FL | INCLUDE_DRM_CD
            | INCLUDE_DRM_SD | INCLUDE_DRM_FLDCF;

    public static final int INCLUDE_4K_VIDEO = (1 << 5);
    public static final int INCLUDE_CONSHOT_GROUP = (1 << 6);

    public static final int INCLUDE_ALL = INCLUDE_DRM_FL | INCLUDE_DRM_CD
            | INCLUDE_DRM_SD | INCLUDE_DRM_FLDCF | INCLUDE_4K_VIDEO
            | INCLUDE_CONSHOT_GROUP;

    public interface IFilter {
        public void setFlagFromIntent(Intent intent, MediaFilter filter);

        public void setDefaultFlag(MediaFilter filter);

        public String getWhereClauseForImage(int flag, int bucketID);

        public String getWhereClauseForVideo(int flag, int bucketID);

        public String getWhereClause(int flag, int bucketID);

        public String getDeleteWhereClauseForImage(int flag, int bucketID);

        public String getDeleteWhereClauseForVideo(int flag, int bucketID);

        public String convertFlagToString(int flag);
    }

    private static ArrayList<IFilter> sFilterArray = new ArrayList<IFilter>();
    private int mFlag;

    public static void registerFilter(IFilter filter) {
        sFilterArray.add(filter);
        MtkLog.i(TAG, "<registerFilter> filter = " + filter);
    }

    public MediaFilter() {
        for (IFilter filter : sFilterArray) {
            filter.setDefaultFlag(this);
        }
    }

    public int getFlag() {
        return mFlag;
    }

    public int hashCode() {
        return mFlag;
    }

    public boolean equals(MediaFilter filter){
        if (filter == null)
            return false;
        return mFlag == filter.getFlag();
    }

    public void setFlagEnable(int flag) {
        mFlag |= flag;
    }

    public void setFlagDisable(int flag) {
        mFlag &= ~flag;
    }

    public void setFlagFromIntent(Intent intent) {
        if (intent == null)
            return;
        for (IFilter filter : sFilterArray) {
            filter.setFlagFromIntent(intent, this);
        }
    }

    public String getExtWhereClauseForImage(String whereClause, int bucketID) {
        for (IFilter filter : sFilterArray) {
            whereClause = AND(whereClause, filter.getWhereClauseForImage(mFlag, bucketID));
        }
        return whereClause;
    }

    public String getExtWhereClauseForVideo(String whereClause, int bucketID) {
        for (IFilter filter : sFilterArray) {
            whereClause = AND(whereClause, filter.getWhereClauseForVideo(mFlag, bucketID));
        }
        return whereClause;
    }

    public String getExtWhereClause(String whereClause, int bucketID) {
        for (IFilter filter : sFilterArray) {
            whereClause = AND(whereClause, filter.getWhereClause(mFlag, bucketID));
        }
        return whereClause;
    }

    public String getExtDeleteWhereClauseForImage(
            String whereClause, int bucketID) {
        for (IFilter filter : sFilterArray) {
            whereClause = AND(whereClause, filter.getDeleteWhereClauseForImage(mFlag, bucketID));
        }
        return whereClause;
    }
    
    public String getExtDeleteWhereClauseForVideo(
            String whereClause, int bucketID) {
        for (IFilter filter : sFilterArray) {
            whereClause = AND(whereClause, filter.getDeleteWhereClauseForVideo(mFlag, bucketID));
        }
        return whereClause;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (IFilter filter : sFilterArray) {
            sb.append(filter.convertFlagToString(mFlag));
        }
        sb.append("]");
        return sb.toString();
    }

    public static String AND(String add1, String add2) {
        if ((add1 == null || add1.equals(""))
                && (add2 == null || add2.equals(""))) {
            return "";
        }
        if (add1 == null || add1.equals("")) {
            return add2;
        }
        if (add2 == null || add2.equals("")) {
            return add1;
        }
        return "(" + add1 + ") AND (" + add2 + ")";
    }

    public static String OR(String or1, String or2) {
        if ((or1 == null || or1.equals("")) && (or2 == null || or2.equals(""))) {
            return "";
        }
        if (or1 == null || or1.equals("")) {
            return or2;
        }
        if (or2 == null || or2.equals("")) {
            return or1;
        }
        return "(" + or1 + ") OR (" + or2 + ")";
    }
}
