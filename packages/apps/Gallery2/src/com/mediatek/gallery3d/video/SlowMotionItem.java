package com.mediatek.gallery3d.video;

import java.util.Locale;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.text.TextUtils;

import com.android.gallery3d.R;
import com.mediatek.galleryframework.util.MtkLog;
public class SlowMotionItem {

    public static final int SLOW_MOTION_ONE_SIXTEENTH_SPEED = 16;  // 1/16X
    public static final int SLOW_MOTION_QUARTER_SPEED = 4; // 1/4X
    public static final int SLOW_MOTION_HALF_SPEED = 2;    // 1/2X
    public static final int SLOW_MOTION_NORMAL_SPEED = 1;  // 1X
    public static final int NORMAL_VIDEO_SPEED = 0; //if normal video ,speed recorded in DB is 0.

    //1/4x(default) -> 1/2x -> 1x -> 1/4x
    public static final int[] SPEED_RANGE = new int[] {
        SLOW_MOTION_QUARTER_SPEED, // 1/4x
        SLOW_MOTION_HALF_SPEED, // 1/2x
        SLOW_MOTION_NORMAL_SPEED // 1x
    };

    //1/4x(default) -> 1/2x -> 1x ->1/16x -> 1/4x
    public static final int[] SPEED_RANGE_16X = new int[] {
        SLOW_MOTION_QUARTER_SPEED, // 1/4x
        SLOW_MOTION_HALF_SPEED, // 1/2x
        SLOW_MOTION_NORMAL_SPEED, // 1x
        SLOW_MOTION_ONE_SIXTEENTH_SPEED // 1/16x
    };

    public static final int[] SPEED_ICON_RESOURCE = new int[] {
        R.drawable.m_ic_slowmotion_4x_speed, // 1/4x default speed
        R.drawable.m_ic_slowmotion_2x_speed, // 1/2x
        R.drawable.m_ic_slowmotion_1x_speed, // 1x
        R.drawable.m_ic_slowmotion_16x_speed, // 1/16x
    };

    private static final String TAG = "Gallery2/SlowMotionItem";

    private int mStartTime;
    private int mEndTime;
    private int mSpeed;
    private int mDuration;

    private Context mContext;
    private Uri mUri;
    private String mSlowMotionInfo;

    public SlowMotionItem(Context context, Uri uri) {
        mContext = context;
        mUri = uri;
        updateItemUri(uri);
    }

    public int getSectionStartTime() {
        return mStartTime;
    }

    public int getSectionEndTime() {
        return mEndTime;
    }

    public int getSpeed() {
        return mSpeed;
    }

    public int getDuration() {
        return mDuration;
    }

    public String getSlowMotionInfo() {
        return mSlowMotionInfo;
    }

    public void setSectionStartTime(int startTime) {
        mStartTime = startTime;
    }

    public void setSectionEndTime(int endTime) {
        mEndTime = endTime;
    }

    public void setSpeed(int speed) {
        MtkLog.i(TAG, "setSpeed speed = " + speed);
        mSpeed = speed;
    }

    public void updateItemToDB() {
        saveSlowMotionInfoToDB(mContext, mUri, mStartTime, mEndTime, mSpeed);
    }

    public void updateItemUri(Uri uri) {
        mSlowMotionInfo = getSlowMotionInfoFromDB(mContext, uri);
        int[] time = getSlowMotionSectionFromString(mSlowMotionInfo);
        if (time != null) {
            mStartTime = time[0];
            mEndTime = time[1];
        }
        mSpeed = getSlowMotionSpeedFromString(mSlowMotionInfo);
        mDuration = getVideoDurationFromDB(mContext, uri);
        mUri = uri;
    }

    public boolean isSlowMotionVideo() {
        return mSpeed != NORMAL_VIDEO_SPEED;
    }
    
    //get currentSpeed index in a disorderly array, 
    //if array is a sorted one, should use Arrays.binarySearch() instead.
    public static int getCurrentSpeedIndex(final int[] speedRange,
            int currentSpeed) {
        for (int index = 0; index < speedRange.length; index++) {
            if (currentSpeed == speedRange[index]) {
                return index;
            }
        }
        return -1;
    }

    private String getSlowMotionInfoFromDB(final Context context, final Uri uri) {
        String data = null;
        Cursor cursor = null;
        try {
            String str = Uri.decode(uri.toString());
            str = str.replaceAll("'", "''");
            final String where = "_data LIKE '%" + str.replaceFirst("file:///", "") + "'";
            cursor = context.getContentResolver().query(uri,
                    new String[] { Video.Media.SLOW_MOTION_SPEED }, null, null, null);

            if (cursor == null) {
                cursor = context.getContentResolver().query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        new String[] { Video.Media.SLOW_MOTION_SPEED }, where, null, null);
            }
            MtkLog.v(TAG, "getSlowMotionInfoFromDB() cursor="
                            + (cursor == null ? "null" : cursor.getCount()));
            if (cursor != null && cursor.moveToFirst()) {
                data = cursor.getString(0);
            }
        } catch (final SQLiteException ex) {
            ex.printStackTrace();
        } catch (IllegalArgumentException ex) {
            // if this exception happen, return false.
            ex.printStackTrace();
            MtkLog.v(TAG, "ContentResolver query IllegalArgumentException");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return data;
    }

    private  int getSlowMotionSpeedFromString(String str) {
        MtkLog.i(TAG, "getSpeedFromStr str = " + str);
        if (str == null || str.charAt(0) != '(') {
            MtkLog.e(TAG, "Invalid string=" + str);
            return 0;
        }
        int pos = str.indexOf('x');
        if (pos != -1) {
            String speed = str.substring(pos + 1);
            MtkLog.i(TAG, "getSpeedFromStr speed " + speed);
            try {
                return Integer.parseInt(speed);
            } catch(NumberFormatException ex) {
                ex.printStackTrace();
            }
        }
        return 0;
    }

    private  int[] getSlowMotionSectionFromString(String str) {
        MtkLog.i(TAG, "getTimeFromStr str = " + str);
        if (str == null || str.charAt(0) != '(') {
            MtkLog.e(TAG, "Invalid string=" + str);
            return null;
        }
        int[] range = new int[2];
        int endIndex, fromIndex = 1;
        endIndex = str.indexOf(')', fromIndex);
        splitInt(str.substring(fromIndex, endIndex), range);
        MtkLog.i(TAG, "getTimeFromStr startTime = " + range[0] + " endTime = " + range[1]);
        return range;
    }

    private static void splitInt(String str, int[] output) {
        if (str == null)
            return;
        TextUtils.StringSplitter splitter = new TextUtils.SimpleStringSplitter(',');
        splitter.setString(str);
        int index = 0;
        for (String s : splitter) {
            output[index++] = Integer.parseInt(s);
        }
    }

    private  int getVideoDurationFromDB(final Context context, final Uri uri) {
        int duration = 0;
        Cursor cursor = null;
        try {
            String str = Uri.decode(uri.toString());
            str = str.replaceAll("'", "''");
            final String where = "_data LIKE '%" + str.replaceFirst("file:///", "") + "'";
            cursor = context.getContentResolver().query(uri,
                    new String[] { Video.Media.DURATION }, null, null, null);

            if (cursor == null) {
                cursor = context.getContentResolver().query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        new String[] { Video.Media.DURATION }, where, null, null);
            }
            MtkLog.v(TAG,
                    "getDurationFromDB() cursor=" + (cursor == null ? "null" : cursor.getCount()));
            if (cursor != null && cursor.moveToFirst()) {
                duration = cursor.getInt(0);
            }
        } catch (final SQLiteException ex) {
            ex.printStackTrace();
        } catch (IllegalArgumentException e) {
            // if this exception happen, return false.
            MtkLog.v(TAG, "ContentResolver query IllegalArgumentException");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return duration;
    }

    private  void saveSlowMotionInfoToDB(final Context context, final Uri uri, final int startTime,
            final int endTime, final int speed) {
        MtkLog.i(TAG, "saveSlowMotionInfoToDB uri " + uri);
        MtkLog.i(TAG, "startTime " + startTime + " endTime " + endTime + " speed " + speed);
        ContentValues values = new ContentValues(1);
        Cursor cursor = null;
        try {
        values.put(Video.Media.SLOW_MOTION_SPEED, "(" + startTime + "," + endTime + ")" + "x"
                + speed);
        if (uri.toString().toLowerCase(Locale.ENGLISH).contains("file:///")) {

            String data = Uri.decode(uri.toString());
            data = data.replaceAll("'", "''");
            String id = null;
            final String where = "_data LIKE '%" + data.replaceFirst("file:///", "") + "'";

            cursor = context.getContentResolver().query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    new String[] { MediaStore.Video.Media._ID }, where, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                id = cursor.getString(0);
            }
            MtkLog.i(TAG, "refreshSlowMotionSpeed id " + id);
            Uri tmp = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "" + id);
            context.getContentResolver().update(tmp, values, null, null);
        } else {
            context.getContentResolver().update(uri, values, null, null);
            }
        } catch (final SQLiteException ex) {
            ex.printStackTrace();
        } catch (IllegalArgumentException ex) {
            // if this exception happen, return false.
            MtkLog.v(TAG, "ContentResolver query IllegalArgumentException");
            ex.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}