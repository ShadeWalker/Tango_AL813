package com.mediatek.galleryfeature.container;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;

import com.mediatek.galleryframework.base.ExtItem;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.util.MtkLog;

public class ContainerHelper {
    private static final String TAG = "MtkGallery2/ContainerHelper";

    private static final int CONTAINER_PLAY_MAX_COUNT = 10;

    public static ArrayList<MediaData> getPlayData(Context context,
            MediaData data) {
        ArrayList<MediaData> mds = data.relateData;
        int lastRepeatCount = 0;
        int maxCount = CONTAINER_PLAY_MAX_COUNT;

        if (mds == null && data.subType == MediaData.SubType.CONSHOT) {
            mds = getConShotDatas(context, data.groupID, data.bucketId);
            lastRepeatCount = 0;
        } else if (mds == null && data.subType == MediaData.SubType.MOTRACK) {
            mds = getMoTrackDatas(data, false);
            lastRepeatCount = 3;
        }
        if (mds != null) {
            return getPlayData(mds, maxCount, lastRepeatCount);
        }
        return null;
    }

    private static ArrayList<MediaData> getPlayData(ArrayList<MediaData> datas,
            int maxCount, int lastRepeatCount) {
        ArrayList<MediaData> md = new ArrayList<MediaData>();
        int num;
        int playCount;
        float space;
        MediaData tmpData = null;

        if (datas == null || datas.size() == 0 || maxCount == 0) {
            MtkLog.w(TAG, "<getPlayDatas> return null");
            return null;
        }
        num = datas.size();
        if (num <= maxCount) {
            space = 1;
            playCount = num;
        } else {
            space = (float) (num - 1) / (maxCount - 1);
            playCount = maxCount;
        }
        for (int i = 0; i < playCount; i++) {
            int index = (int) (i * space);
            // MtkLog.w(TAG, "<getPlayDatas> add index:"+index);
            tmpData = datas.get(index);
            md.add(tmpData);
        }
        for (int i = 0; i < lastRepeatCount; i++) {
            md.add(tmpData);
        }

        return md;
    }

    public static ArrayList<MediaData> getConShotDatas(Context context,
            long groupId, int bucketId) {
        Cursor cursor = getConShotsCursor(context, groupId, bucketId);
        if (cursor == null)
            return null;
        ArrayList<MediaData> list = MediaData.parseImageMediaDatas(cursor, true);
        return list;
    }

    public static void deleteConshotDatas(Context context, long groupId, int bucketId) {
        String whereClause = Images.Media.GROUP_ID + " = ?" + " AND " + Images.Media.TITLE
                + " LIKE 'IMG%CS'" + " AND " + ImageColumns.BUCKET_ID + "= ?";
        String[] whereClauseArgs = new String[] { String.valueOf(groupId), String.valueOf(bucketId) };

        Uri baseUri = Images.Media.EXTERNAL_CONTENT_URI;
        Uri uri = baseUri.buildUpon().appendQueryParameter("limit", 0 + "," + 100).build();
        ContentResolver resolver = context.getContentResolver();
        resolver.delete(baseUri, whereClause, whereClauseArgs);
    }

    public static Cursor getConShotsCursor(Context context,
            long groupId, int bucketId) {
        ContentResolver resolver = context.getContentResolver();
        String whereClause = Images.Media.GROUP_ID + " = ?" + " AND "
                + Images.Media.TITLE + " LIKE 'IMG%CS'" + " AND "
                + ImageColumns.BUCKET_ID + "= ?";
        String[] whereClauseArgs = new String[] { String.valueOf(groupId),
                String.valueOf(bucketId) };

        String orderClause = ImageColumns.GROUP_INDEX + " ASC";
        Uri baseUri = Images.Media.EXTERNAL_CONTENT_URI;
        Uri uri = baseUri.buildUpon().appendQueryParameter("limit",
                0 + "," + 100).build();
        Cursor curosr = MediaData.queryImage(context, uri,
                whereClause, whereClauseArgs, orderClause);
        return curosr;
    }

    public static ArrayList<MediaData> getMoTrackDatas(MediaData md,
            boolean original) {
        String workPath;
        String filePath = md.filePath;
        String name = md.caption;
        File motionDir;
        ArrayList<MediaData> list = new ArrayList<MediaData>();

        workPath = new File(filePath).getParent();
        if (original) {
            motionDir = new File(workPath + "/.ConShots/" + name);
        } else {
            motionDir = new File(workPath + "/.ConShots/" + name + "TK");
        }
        int motionCount = 0;
        MtkLog.w(TAG, "<getMotionTrackDatas> motionDir = " + motionDir.getPath());

        if (!motionDir.exists())
            return null;

        File[] allFiles = motionDir.listFiles();
        if (allFiles == null)
            return null;

        // sort file
        ArrayList<File> fileList = new ArrayList<File>();
        for (File file : allFiles) {
            fileList.add(file);
        }
        Collections.sort(fileList, new FileComparator());

        for (motionCount = 0; motionCount < fileList.size(); motionCount++) {
            File currFile = fileList.get(motionCount);
            MediaData data = new MediaData();
            data.filePath = currFile.getPath();
            data.caption = currFile.getName();
            data.orientation = md.orientation;
            MtkLog.w(TAG, "<getMotionTrackDatas> add file "
                    + currFile.getPath());
            list.add(data);
        }
        return list;
    }

    private static class FileComparator implements Comparator<File> {

        @Override
        public int compare(File file1, File file2) {
            return file1.getName().compareTo(file2.getName());
        }
    }

    static ArrayList<ExtItem> getExtItem(ArrayList<MediaData> datas) {
        ArrayList<ExtItem> items = new ArrayList<ExtItem>();
        for (MediaData data : datas) {
            ExtItem item = new ExtItem(data);
            items.add(item);
        }
        return items;
    }

    public static void setBestShotMark(Context context, int bestShotMark,
            long id) {
        Uri baseUri = Images.Media.EXTERNAL_CONTENT_URI;
        ContentValues cv = new ContentValues(1);
        cv.put(Images.Media.IS_BEST_SHOT, bestShotMark);
        int result = context.getContentResolver().update(baseUri, cv, "_id=?",
                new String[] { String.valueOf(id) });
        MtkLog.i(TAG, "<setIsBestShot> update isBestShot value of id[" + id
                + "] result = " + result);
    }
}