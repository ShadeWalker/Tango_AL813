/* //device/content/providers/media/src/com/android/providers/media/MediaScannerService.java
 **
 ** Copyright 2007, The Android Open Source Project
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */

package com.android.providers.media;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.content.IContentProvider;
import android.database.Cursor;
import android.media.MediaInserter;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;

public class MediaScannerInserter {
    private static final String TAG = "MediaScannerInserter";

    private static final Uri AUDIO_URI = MediaStore.Audio.Media.getContentUri(MediaProvider.EXTERNAL_VOLUME);
    private static final Uri IMAGE_URI = MediaStore.Images.Media.getContentUri(MediaProvider.EXTERNAL_VOLUME);
    private static final Uri VIDEO_URI = MediaStore.Video.Media.getContentUri(MediaProvider.EXTERNAL_VOLUME);
    protected static final Uri FILE_URI  = MediaStore.Files.getContentUri(MediaProvider.EXTERNAL_VOLUME);

    private static final int MAX_INSERT_ENTRY_SIZE = 500; // bulkInsert 500 file use 2000ms, so can not insert more than 500

    private IContentProvider mMediaProvider;
    private Context mContext;
    private String mPackageName;
    private Handler mServiceHandler;
    private Handler mInsertHanlder;
    private boolean mHasStoppedInsert = false;

    private HashMap<Uri, List<ContentValues>> mNormalMap = new HashMap<Uri, List<ContentValues>>(4);

    public MediaScannerInserter(Context context, Handler serviceHanlder) {
        mContext = context;
        mServiceHandler = serviceHanlder;
        mPackageName = mContext.getPackageName();
        mMediaProvider = mContext.getContentResolver().acquireProvider("media");
        HandlerThread insertThread = new HandlerThread("MediaInserter");
        insertThread.start();
        mInsertHanlder = new MediaInsertHandler(insertThread.getLooper());
    }

    public Handler getInsertHandler() {
        return mInsertHanlder;
    }

    public void release() {
        MtkLog.v(TAG, "release MediaScannerInserter");
        if (mInsertHanlder != null) {
            mInsertHanlder.getLooper().quit();
            mInsertHanlder = null;
        }
        mServiceHandler = null;
        mContext = null;
        mMediaProvider = null;
    }

    private class MediaInsertHandler extends Handler {

        public MediaInsertHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MediaInserter.MSG_INSERT_TO_DATABASE) {
                @SuppressWarnings("unchecked")
                List<ContentValues> insertList = (List<ContentValues>) msg.obj;
                Uri tableUri = Uri.parse(insertList.remove(0).getAsString(MediaInserter.INSERT_TABLE_URI_KEY));
                /// First insert folder entries(msg.arg1 > 0)
                /// Then insert multi-media entries
                /// Last insert normal entries
                if (msg.arg1 > 0) {
                    insertPriority(tableUri, insertList);
                } else if (AUDIO_URI.equals(tableUri) || VIDEO_URI.equals(tableUri) || IMAGE_URI.equals(tableUri)) {
                    insertMedia(tableUri, insertList);
                } else {
                    insertNormal(tableUri, insertList);
                }
            } else if (msg.what == MediaInserter.MSG_INSERT_FOLDER) {
                @SuppressWarnings("unchecked")
                /// Insert all parse out folders first if it's not exist in database, so that these folders
                /// can show soon on MTP and let do insert sub files to database quickly.
                List<ContentValues> folderList = (List<ContentValues>) msg.obj;
                List<ContentValues> insertList = new ArrayList<ContentValues>(MAX_INSERT_ENTRY_SIZE);
                for (ContentValues values : folderList) {
                    String folderPath = values.getAsString(MediaStore.Files.FileColumns.DATA);
                    if (folderPath != null && !isExistInDatabase(folderPath)) {
                        insertList.add(values);
                    }
                    if (insertList.size() >= MAX_INSERT_ENTRY_SIZE) {
                        flush(FILE_URI, insertList);
                        insertList.clear();
                    }
                }
                if (!insertList.isEmpty()) {
                    flush(FILE_URI, insertList);
                }
            } else if (msg.what == MediaInserter.MSG_INSERT_ALL) {
                MtkLog.v(TAG, "All files have been scanned, wait insert finish !!!!!!!!!!!!!!!!!!!!!!");
                flushAll();
                MtkLog.v(TAG, "All entries have been inserted, scan finished ~~~~~~~~~~~~~~~~~~~~~~~~");
                mServiceHandler.sendEmptyMessage(MediaInserter.MSG_SCAN_FINISH_WITH_THREADPOOL);
            } else if (msg.what == MediaInserter.MSG_STOP_INSERT) {
                /// Happen when quick plug in/out sdcard
                stopInsert();
            } else {
                MtkLog.w(TAG, "unsupport message " + msg.what);
            }
        };
    }

    private void insertPriority(Uri tableUri, List<ContentValues> insertList) {
        flush(tableUri, insertList);
    }

    private void insertMedia(Uri tableUri, List<ContentValues> insertList) {
        flush(tableUri, insertList);
    }

    private void insertNormal(Uri tableUri, List<ContentValues> insertList) {
        insert(tableUri, insertList);
    }

    private void insert(Uri tableUri, List<ContentValues> insertList) {
        List<ContentValues> newList = insertList;
        List<ContentValues> oldList = mNormalMap.get(tableUri);
        if (oldList == null) {
            mNormalMap.put(tableUri, insertList);
        } else {
            /// Combine as one array if small than MAX_INSERT_FILE_SIZE, otherwise insert big one and left
            /// small one to combine next time.
            int newSize = newList.size();
            int oldSize = oldList.size();
            if ((newSize + oldSize) <= MAX_INSERT_ENTRY_SIZE) {
                oldList.addAll(newList);
            } else if (newSize > oldSize) {
                flush(tableUri, newList);
                mNormalMap.put(tableUri, oldList);
            } else {
                flush(tableUri, oldList);
                mNormalMap.put(tableUri, newList);
            }
        }
    }

    private void flush(Uri tableUri, List<ContentValues> list) {
        if (mHasStoppedInsert) {
            MtkLog.d(TAG, "skip flush to database because has stopped inserting");
            return;
        }
        long start = System.currentTimeMillis();
        int size = list.size();
        ContentValues[] valuesArray = new ContentValues[size];
        valuesArray = list.toArray(valuesArray);
        try {
            mMediaProvider.bulkInsert(mPackageName, tableUri, valuesArray);
        } catch (Exception e) {
            MtkLog.e(TAG, "bulkInsert with Exception for " + tableUri, e);
        }
        long end = System.currentTimeMillis();
        MtkLog.d(TAG, "flush " + tableUri + " with size " + size + " which cost " + (end - start) + "ms");
    }

    private void flushAll() {
        for (Uri tableUri : mNormalMap.keySet()) {
            List<ContentValues> list = mNormalMap.get(tableUri);
            flush(tableUri, list);
        }
        mNormalMap.clear();
    }

    private boolean isExistInDatabase(String folderPath) {
        Cursor cursor = null;
        try {
            cursor = mMediaProvider.query(mPackageName, FILE_URI, null,
                            MediaStore.Files.FileColumns.DATA + "=?", new String[] {folderPath}, null, null);
            return cursor != null && cursor.moveToFirst();
        } catch (Exception e) {
            MtkLog.e(TAG, "Check isExistInDatabase with Exception for " + folderPath, e);
            return true;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void stopInsert() {
        MtkLog.d(TAG, "stopInsert so remove insert msg and clear insert cache");
        if (mInsertHanlder == null) {
            return;
        }
        mHasStoppedInsert = true;
        mInsertHanlder.removeCallbacksAndMessages(MediaInserter.MSG_INSERT_TO_DATABASE);
        mInsertHanlder.removeCallbacksAndMessages(MediaInserter.MSG_INSERT_FOLDER);
        mNormalMap.clear();
    }
}
