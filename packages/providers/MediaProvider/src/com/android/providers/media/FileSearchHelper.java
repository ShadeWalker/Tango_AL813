package com.android.providers.media;

import static com.android.providers.media.MediaUtils.LOG_QUERY;

import java.util.Arrays;
import java.util.Locale;

import android.app.SearchManager;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.media.MediaFile;
import android.mtp.MtpConstants;
import android.net.Uri;
import android.os.Binder;
import android.provider.BaseColumns;
import android.provider.MediaStore.Audio;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.MediaColumns;
import android.text.TextUtils;

/**
 * Helper class used to computes file attributes, such as file_type, file_name,
 * is_ringtone, is_notification, is_alarm, is_music and is_podcast.
 *
 */
public class FileSearchHelper {
    private static final String TAG = "FileSearchHelper";
    private static String[] sSearchFileCols = new String[] {
            android.provider.BaseColumns._ID,
            "(CASE WHEN media_type=1 THEN " + R.drawable.ic_search_category_image +
            " ELSE CASE WHEN media_type=2 THEN " + R.drawable.ic_search_category_audio +
            " ELSE CASE WHEN media_type=3 THEN " + R.drawable.ic_search_category_video +
            " ELSE CASE WHEN file_type=4 THEN " + R.drawable.ic_search_category_text +
            " ELSE CASE WHEN file_type=5 THEN " + R.drawable.ic_search_category_zip +
            " ELSE CASE WHEN file_type=6 THEN " + R.drawable.ic_search_category_apk +
            " ELSE CASE WHEN format=12289 THEN " + R.drawable.ic_search_category_folder +
            " ELSE " + R.drawable.ic_search_category_others + " END END END END END END END" +
            ") AS " + SearchManager.SUGGEST_COLUMN_ICON_1,
            FileColumns.FILE_NAME + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1,
            "_data AS " + SearchManager.SUGGEST_COLUMN_TEXT_2,
            "_data AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            "_id AS " + SearchManager.SUGGEST_COLUMN_SHORTCUT_ID
    };

    /**
     * Constants for file type indicating the file is not an image, audio, video, text, zip or apk.
     */
    public static final int FILE_TYPE_NONE = 0;
    /**
     * Constants for file type indicating the file is an image.
     */
    public static final int FILE_TYPE_IMAGE = 1;
    /**
     * Constants for file type indicating the file is an audio.
     */
    public static final int FILE_TYPE_AUDIO = 2;
    /**
     * Constants for file type indicating the file is a video.
     */
    public static final int FILE_TYPE_VIDEO = 3;
    /**
     * Constants for file type indicating the file is a text.
     */
    public static final int FILE_TYPE_TEXT = 4;
    /**
     * Constants for file type indicating the file is a zip.
     */
    public static final int FILE_TYPE_ZIP = 5;
    /**
     * Constants for file type indicating the file is an apk.
     */
    public static final int FILE_TYPE_APK = 6;

    /**
     * Searches files whose name contain some specific string.
     *
     * @param db the SQLiteDatabase instance used to query database.
     * @param qb the SQLiteQueryBuilder instance used to build sql query.
     * @param uri the uri with the search string at its last path.
     * @param limit the limit of rows returned by this query.
     * @return
     */
    public static Cursor doFileSearch(SQLiteDatabase db, SQLiteQueryBuilder qb, Uri uri, String limit) {
        if (db == null || qb == null || uri == null) {
            MtkLog.e(TAG, "doFileSearch: Param error!");
            return null;
        }

        String searchString = uri.getPath().endsWith("/") ? "" : uri.getLastPathSegment();
        searchString = Uri.decode(searchString).trim();
        if (TextUtils.isEmpty(searchString)) {
            return null;
        }

        searchString = searchString.replace("\\", "\\\\");
        searchString = searchString.replace("%", "\\%");
        searchString = searchString.replace("'", "\\'");
        searchString = "%" + searchString + "%";
        String where = FileColumns.FILE_NAME + " LIKE ? ESCAPE '\\'";
        String[] whereArgs = new String[] { searchString };
        qb.setTables("files");
        if (LOG_QUERY) {
            MtkLog.d(TAG, "doFileSearch: uri = " + uri + ", selection = " + where + ", selectionArgs = "
                    + Arrays.toString(whereArgs) + ", caller pid = " + Binder.getCallingPid());
        }
        return qb.query(db, sSearchFileCols, where, whereArgs, null, null, null, limit);
    }

    /**
     * Updates file_name and file_type.
     *
     * @param db the SQLiteDatabase instance used to update database.
     * @param tableName the name of table to be updated.
     */
    public static void updateFileNameAndType(SQLiteDatabase db, String tableName) {
        if (db == null || tableName == null) {
            MtkLog.e(TAG, "updateFileName: Param error!");
            return;
        }
        db.beginTransaction();
        try {
            String[] columns = {BaseColumns._ID, MediaColumns.DATA, FileColumns.FILE_NAME};
            Cursor cursor = db.query(tableName, columns, null, null, null, null, null);
            try {
                if (cursor != null) {
                    final int idColumnIndex = cursor.getColumnIndex(BaseColumns._ID);
                    final int dataColumnIndex = cursor.getColumnIndex(MediaColumns.DATA);
                    final int fileNameIndex = cursor.getColumnIndex(FileColumns.FILE_NAME);
                    ContentValues values = new ContentValues();
                    while (cursor.moveToNext()) {
                        String fileName = cursor.getString(fileNameIndex);
                        if (fileName == null) {
                            String data = cursor.getString(dataColumnIndex);
                            values.clear();
                            computeFileName(data, values);
                            computeFileType(data, values);
                            int rowId = cursor.getInt(idColumnIndex);
                            db.update(tableName, values, "_id=" + rowId, null);
                        }
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Computes file name from file path.
     *
     * @param path the file path.
     * @param values ContentValues instance to hold file name.
     */
    public static void computeFileName(String path, ContentValues values) {
        if (path == null || values == null) {
            MtkLog.e(TAG, "computeFileName: Param error!");
            return;
        }

        int idx = path.lastIndexOf('/');
        if (idx >= 0) {
            path = path.substring(idx + 1);
        }
        values.put(FileColumns.FILE_NAME, path);
    }

    /**
     * Computes file type from file path.
     *
     * @param path the file path.
     * @param values ContentValues instance to hold file name.
     */
    public static void computeFileType(String path, ContentValues values) {
        if (path == null || values == null) {
            MtkLog.e(TAG, "computeFileType: Param error!");
            return;
        }

        //When compute a folder's file type, just return, so sqlite will insert default value 0 as file type.
        Integer formatObject = values.getAsInteger(FileColumns.FORMAT);
        if (formatObject != null && formatObject.intValue() == MtpConstants.FORMAT_ASSOCIATION) {
            MtkLog.w(TAG, "computeFileType path is a folder, filetype must be 0");
            return;
        }

        String mimeType = MediaFile.getMimeTypeForFile(path);
        if (mimeType == null) {
            return;
        }

        mimeType = mimeType.toLowerCase();
        if (mimeType.startsWith("image/")) {
            values.put(FileColumns.FILE_TYPE, FILE_TYPE_IMAGE);
            return;
        }
        if (mimeType.startsWith("audio/")) {
            values.put(FileColumns.FILE_TYPE, FILE_TYPE_AUDIO);
            return;
        }
        if (mimeType.startsWith("video/")) {
            values.put(FileColumns.FILE_TYPE, FILE_TYPE_VIDEO);
            return;
        }
        if (mimeType.startsWith("text/")) {
            values.put(FileColumns.FILE_TYPE, FILE_TYPE_TEXT);
            return;
        }
        if (mimeType.equals("application/zip")) {
            values.put(FileColumns.FILE_TYPE, FILE_TYPE_ZIP);
            return;
        }
        if (mimeType.equals("application/vnd.android.package-archive")) {
            values.put(FileColumns.FILE_TYPE, FILE_TYPE_APK);
            return;
        }
    }

    private static final String RINGTONES_DIR = "/ringtones/";
    private static final String NOTIFICATIONS_DIR = "/notifications/";
    private static final String ALARMS_DIR = "/alarms/";
    private static final String MUSIC_DIR = "/music/";
    private static final String PODCAST_DIR = "/podcasts/";

    /**
     * Computes is_ringtone, is_notification, is_alarm, is_music and is_podcast based on file path.
     * when move files in/out Ringtone, Notification, Alarm, Podcast, we need update these columns,
     * otherwise just keep current values in database.
     *
     * @param oldPath old path.
     * @param newPath new path.
     * @param values ContentValues instance to hold values.
     */
    public static void computeRingtoneAttributes(String oldPath, String newPath, ContentValues values) {
        if (oldPath == null || newPath == null || values == null) {
            MtkLog.e(TAG, "computeRingtoneAttributes: Param error!");
            return;
        }
        int totalState = 0;
        int state = 0;
        boolean isInSpecialDir = false; // in ringtone, alarm, notification and podcast dir
        // Ringtone
        state = getState(RINGTONES_DIR, oldPath, newPath);
        if (state != 0) {
            values.put(Audio.Media.IS_RINGTONE, state > 0);
            isInSpecialDir = isInSpecialDir || state > 0;
            totalState += state;
        }
        // Notification
        state = getState(NOTIFICATIONS_DIR, oldPath, newPath);
        if (state != 0) {
            values.put(Audio.Media.IS_NOTIFICATION, state > 0);
            isInSpecialDir = isInSpecialDir || state > 0;
            totalState += state;
        }
        // Alarm
        state = getState(ALARMS_DIR, oldPath, newPath);
        if (state != 0) {
            values.put(Audio.Media.IS_ALARM, state > 0);
            isInSpecialDir = isInSpecialDir || state > 0;
            totalState += state;
        }
        // Podcast
        state = getState(PODCAST_DIR, oldPath, newPath);
        if (state != 0) {
            values.put(Audio.Media.IS_PODCAST, state > 0);
            isInSpecialDir = isInSpecialDir || state > 0;
            totalState += state;
        }
        // Music
        state = getState(MUSIC_DIR, oldPath, newPath);
        // check as below order, never change
        if (state > 0) {
            // cut to music path
            values.put(Audio.Media.IS_MUSIC, true);
        } else if (isInSpecialDir) {
            // cut to one or several of ringtone, notification, alarm and podcast path
            values.put(Audio.Media.IS_MUSIC, false);
        } else if (totalState < 0) {
            // cut out from ringtone, notification, alarm and podcast to normal path
            values.put(Audio.Media.IS_MUSIC, true);
        }
    }

    private static int getState(String dirPath, String oldPath, String newPath) {
        // if path last charactor is not "/", append it so that can match with dir path
        if (!oldPath.endsWith("/")) {
            oldPath += "/";
        }
        if (!newPath.endsWith("/")) {
            newPath += "/";
        }
        int state = 0;// never change from dir path
        int oldIndex = oldPath.toLowerCase(Locale.ENGLISH).indexOf(dirPath);
        int newIndex = newPath.toLowerCase(Locale.ENGLISH).indexOf(dirPath);
        if (newIndex > 0) {
            // new path is in dir path, need update dir state to true
            state = 1;
        } else if (oldIndex > 0) {
            // old path in dir but new is not, mean cut out from dir, need update dir state to false
            state = -1;
        } else {
            // otherwise path is still normal path(not dir path)
        }
        return state;
    }

    /**
     * Handles shortcut search.
     *
     * @param db the SQLiteDatabase instance used to query database.
     * @param qb the SQLiteQueryBuilder instance used to build sql query.
     * @param uri the uri to be queried.
     * @param limit the limit of rows returned by this query.
     * @return
     */
    public static Cursor doShortcutSearch(SQLiteDatabase db, SQLiteQueryBuilder qb, Uri uri, String limit) {
        if (db == null || qb == null || uri == null) {
            MtkLog.e(TAG, "doShortcutSearch: Param error!");
            return null;
        }

        String searchString = uri.getLastPathSegment();
        searchString = Uri.decode(searchString).trim();
        if (TextUtils.isEmpty(searchString)) {
            MtkLog.e(TAG, "doShortcutSearch: Null id!");
            return null;
        }

        String where = FileColumns._ID + "=?";
        String[] whereArgs = new String[] { searchString };
        qb.setTables("files");
        if (LOG_QUERY) {
            MtkLog.d(TAG, "doShortcutSearch: uri = " + uri + ", selection = " + where + ", selectionArgs = "
                    + Arrays.toString(whereArgs) + ", caller pid = " + Binder.getCallingPid());
        }
        return qb.query(db, sSearchFileCols, where, whereArgs, null, null, null, limit);
    }
}
