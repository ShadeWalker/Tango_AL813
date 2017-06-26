package com.mediatek.gallery3d.video;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import com.mediatek.galleryframework.util.MtkLog;

import java.io.File;

public class MovieTitleHelper {
    private static final String TAG = "Gallery2/VideoPlayer/MovieTitleHelper";
    private static final boolean LOG = true;

    public static String getTitleFromMediaData(final Context context, final Uri uri) {
        String title = null;
        Cursor cursor = null;
        try {
            String data = Uri.decode(uri.toString());
            data = data.replaceAll("'", "''");
            final String where = "_data LIKE '%" + data.replaceFirst("file:///", "") + "'";
            cursor = context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    new String[]{OpenableColumns.DISPLAY_NAME}, where, null, null);
            if (LOG) {
                MtkLog.v(TAG, "setInfoFromMediaData() cursor=" + (cursor == null ? "null" : cursor.getCount()));
            }
            if (cursor != null && cursor.moveToFirst()) {
                title = cursor.getString(0);
           }
        } catch (final SQLiteException ex) {
            ex.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (LOG) {
            MtkLog.v(TAG, "setInfoFromMediaData() return " + title);
        }
        return title;
    }

    public static String getTitleFromDisplayName(final Context context, final Uri uri) {
        String title = null;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                title = cursor.getString(0);
           }
        } catch (final SQLiteException ex) {
            ex.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (LOG) {
            MtkLog.v(TAG, "getTitleFromDisplayName() return " + title);
        }
        return title;
    }

    public static String getTitleFromUri(final Uri uri) {
        final String title = Uri.decode(uri.getLastPathSegment());
        if (LOG) {
            MtkLog.v(TAG, "getTitleFromUri() return " + title);
        }
        return title;
    }

    public static String getTitleFromData(final Context context, final Uri uri) {
        String title = null;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[]{"_data"}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                final File file = new File(cursor.getString(0));
                title = file.getName();
           }
        } catch (final SQLiteException ex) {
            ex.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (LOG) {
            MtkLog.v(TAG, "getTitleFromData() return " + title);
        }
        return title;
    }

    /**
     * Judge whether the uri is valid or not, the uri is valid means that the
     * file related file exist.
     *
     * @param uri Current video uri
     * @return True if the uri is valid , false otherwise.
     */
    public static boolean isUriValid(final Context context, final Uri uri) {
        if (LOG) {
            MtkLog.v(TAG, "isUriValid() entry with the uri is " + uri);
        }
        boolean isValid = false;
        if (uri != null) {
            String[] proj = {
                MediaStore.Video.Media.DATA
            };
            Cursor cursor = context.getContentResolver().query(uri, proj, null, null, null);
            if (cursor == null) {
                String data = Uri.decode(uri.toString());
                if (data == null) {
                    return false;
                }
                data = data.replaceAll("'", "''");
                final String where = "_data LIKE '%" + data.replaceFirst("file:///", "") + "'";

                cursor = context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                      proj, where, null, null);
            }
            if (cursor != null) {
                try {
                    int index = 0;
                    if (cursor.getCount() != 0) {
                        index = cursor.getColumnIndex(MediaStore.Video.Media.DATA);
                        if (index == -1) {
                            //index equals 1 means Media.DATA is not exist
                            return false;
                        }
                        cursor.moveToFirst();
                        String fileName = cursor.getString(index);
                        if (fileName != null) {
                            File file = new File(fileName);
                            if (file.exists()) {
                                isValid = true;
                            }
                        }
                    }
                } catch (final SQLiteException ex) {
                    ex.printStackTrace();
                } finally {
                    cursor.close();
                }
            }
        }
        if (LOG) {
            MtkLog.v(TAG, "isUriValid() exit with isValid is " + isValid);
        }
        return isValid;
    }

}
