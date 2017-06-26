package com.mediatek.gallery3d.video;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.Video;
import android.widget.ShareActionProvider;
import android.widget.Toast;

import com.android.gallery3d.common.BlobCache;
import com.android.gallery3d.R;
import com.android.gallery3d.util.CacheManager;
import com.android.gallery3d.util.SaveVideoFileInfo;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.storage.StorageManagerEx;

public class TranscodeVideo {
    private static final String TAG = "Gallery2/TranscodeVideo";

    private static final int TRANSCODE_COMPLETE = 3;
    private static final int UNSUPPORTED_VIDEO = 10;
    private static final int UNSUPPORTED_AUDIO = 11;
    private static final int RECORD_EVENT_BEGIN = 100;

    private static final String FILE_URI_START = "file://";
    private static final String TEMP_FOLDER_NAMER = "/.sTemp/"; //transcode tmp folder name.
    //added for hotnot share.
    private static final String EXTRA_SHARE_URIS = "com.mediatek.hotknot.extra.SHARE_URIS";
    
    private static final String BOOKMARK_CACHE_FILE = "bookmark";
    private static final String BOOKMARK_KEY = "SlowMotion";
    private static final int BOOKMARK_CACHE_MAX_ENTRIES = 100;
    private static final int BOOKMARK_CACHE_MAX_BYTES = 10 * 1024;
    private static final int BOOKMARK_CACHE_VERSION = 1;
    
    //Indicate native transcode is busy or not.
    private enum TranscodeState {
        BUSY,
        IDLE,
    }
    private static TranscodeState mState = TranscodeState.IDLE;
    
    private String mSrcVideoPath;
    private String mDstVideoPath;
    private String mDstVideoName;
    private String mSlowMotionInfo;
    private Uri mUri;
    private Uri mNewVideoUri;
    private  ProgressDialog mProgress;
    private boolean mActivityDestroyed = false;
    private Context mContext;
    private boolean mIsSaving = false;
    private Intent mIntent;
    private SlowMotionItem mItem;
    private final Handler mHandler = new Handler();
    private SlowMotionTranscode mTranscode;
    private Thread mTanscodeStopTask;
    

    private final Runnable mStartVideoRunnable = new Runnable() {
        @Override
        public void run() {
            // TODO: change trimming into a service to avoid
            // this progressDialog and add notification properly.
            MtkLog.v(TAG, "StartVideoRunnable,mActivityDestroyed:" + mActivityDestroyed);
            if (!mActivityDestroyed) {
                if (mProgress != null) {
                    mProgress.dismiss();
                    mProgress = null;
                }
                mContext.startActivity(mIntent);
            }
        }
    };

    public TranscodeVideo(Context context, Uri uri) {
        mContext = context;
        updateTranscodeInfo(uri);
    }

    public void updateUri(Uri uri) {
        updateTranscodeInfo(uri);
    }


    private static StorageManager sStorageManager;
    private static StorageManager getStorageManager() {
        if (sStorageManager == null) {
            try {
                sStorageManager = new StorageManager(null, null);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return sStorageManager;
    }

    private static String getDefaultPath() {
        StorageManager storageManager = getStorageManager();
        return StorageManagerEx.getDefaultPath();
    }

    private static void mkFileDir(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            MtkLog.d(TAG, "dir not exit,will create this");
            dir.mkdirs();
        }
    }

    public void onShareTargetSelected(Intent intent) {
        MtkLog.v(TAG, "onShareTargetSelected");
        mIntent = intent;
        startTranscode();
    }

    public void onHotKnotSelected(Intent intent) {
        MtkLog.v(TAG, "onHotKnotSelected intent " + intent);
        Uri uris[] = new Uri[1];
        uris[0] = Uri.parse(FILE_URI_START + mDstVideoPath + "?show=yes");
        MtkLog.v(TAG, "onHotKnotSelected uris[0] " + uris[0]);
        intent.putExtra(EXTRA_SHARE_URIS, uris);
        mIntent = intent;
        startTranscode();
    }
    
    public void onDestrory() {
        MtkLog.v(TAG, "onDestrory  mState " + mState);
        mActivityDestroyed = true;
        if (mProgress != null) {
            mProgress.dismiss();
            mProgress = null;
        }
        if(mState == TranscodeState.BUSY) {
            mTranscode.stopSaveSpeedEffect();
            mState = TranscodeState.IDLE;
        }
    }
    
    private void updateTranscodeInfo(Uri uri) {
        mUri = changeUriFormatIfNeed(uri);
        mSrcVideoPath = getVideoPath(mContext, mUri);
        final File srcFile = new File(mSrcVideoPath);
        String defaultPath = getDefaultPath() + TEMP_FOLDER_NAMER;
        MtkLog.v(TAG, "after getDefaultPath defaultPath " +  defaultPath);
        mkFileDir(defaultPath);
        mDstVideoPath = defaultPath + srcFile.getName();
        mDstVideoName = srcFile.getName();
        MtkLog.v(TAG, "updateTranscodeInfo mUri " + mUri + " mDstVideoPath " + mDstVideoPath);
    }

    //here should record three parameter:
    // 1. source video uri(the uri should use "content" format, if play a video from file manager, should to convert to "content" format)
    // 2. current video start&end time ,speed.
    // 3. target path.
    private void setBookmark(Uri srcUri, String slowmotioninfo, String dstPath, Uri dstUri) {
        MtkLog.v(TAG, "setBookmark(" + srcUri + ", " + slowmotioninfo + ", " + dstPath + "," + dstUri + ")");
        try {
            BlobCache cache = CacheManager.getCache(mContext,
                    BOOKMARK_CACHE_FILE, BOOKMARK_CACHE_MAX_ENTRIES,
                    BOOKMARK_CACHE_MAX_BYTES, BOOKMARK_CACHE_VERSION);
            if (cache == null) {
                MtkLog.v(TAG, "setBookmark(" + srcUri + ") cache=null. hashCode()=" + BOOKMARK_KEY.hashCode());
                return;
            }
            
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeUTF(srcUri.toString());
            dos.writeUTF(slowmotioninfo);
            dos.writeUTF(dstPath);
            dos.writeUTF(dstUri.toString());
            dos.flush();
            cache.insert(BOOKMARK_KEY.hashCode(), bos.toByteArray());
        } catch (IOException t) {
            MtkLog.w(TAG, "setBookmark failed", t);
        }
    }

    private void deleteVideoFile(String fileName) {
        File f = new File(fileName);
        if (!f.delete()) {
            MtkLog.v(TAG, "Could not delete " + fileName);
        }
    }
    
    private void deleteOldDBInfo(Uri uri) {
        mContext.getContentResolver().delete(uri, null, null);
    }

    private boolean isNeedTranscode(Uri uri) {
        try {
            BlobCache cache = CacheManager.getCache(mContext,
                    BOOKMARK_CACHE_FILE, BOOKMARK_CACHE_MAX_ENTRIES,
                    BOOKMARK_CACHE_MAX_BYTES, BOOKMARK_CACHE_VERSION);
            if (cache == null) {
                MtkLog.v(TAG, "isNeedTranscode(" + uri + ") cache=null. hashCode()=" + BOOKMARK_KEY.hashCode());
                return true;
            }
            
            byte[] data = cache.lookup(BOOKMARK_KEY.hashCode());
            if (data == null) {
                MtkLog.v(TAG, "isNeedTranscode(" + uri + ") data=null. hashCode()=" + BOOKMARK_KEY.hashCode());
                return true;
            }

            DataInputStream dis = new DataInputStream(
                    new ByteArrayInputStream(data));

            String uriString = DataInputStream.readUTF(dis);
            String slowmotioninfo = DataInputStream.readUTF(dis);
            String dstPath = DataInputStream.readUTF(dis);
            String dstUriString = DataInputStream.readUTF(dis);
            MtkLog.v(TAG, "isNeedTranscode(" + uri + ") uriString=" + uriString + ", slowmotioninfo="
                    + slowmotioninfo + ", dstPath=" + dstPath + ", dstUriString= " + dstUriString);
            MtkLog.v(TAG, "isNeedTranscode(" + uri + ") uri.toString()=" + uri.toString() + ", mSlowMotionInfo="
                    + mSlowMotionInfo);

            //1.  check tmp file is exit or not.
            File tmp = new File(mDstVideoPath);
            if (!tmp.exists()) {
                deleteVideoFile(dstPath);
                deleteOldDBInfo(Uri.parse(dstUriString));
                MtkLog.v(TAG, "Dst file is not exit!");
                return true;
            }
            //2. check current uri is recorded in bookmark or not.
            if (!uriString.equals(uri.toString())) {
                //if not equal, delete tmp file and return true.
                deleteVideoFile(dstPath);
                deleteOldDBInfo(Uri.parse(dstUriString));
                MtkLog.v(TAG, "Uri is not equal!");
                return true;
            }
            //3. check slowmotioninfo is equal or not.
            if (!mSlowMotionInfo.equals(slowmotioninfo)) {
                //if not equal, delete tmp file and return true.
                deleteVideoFile(dstPath);
                deleteOldDBInfo(Uri.parse(dstUriString));
                MtkLog.v(TAG, "SlowMotionInfo is not equal!");
                return true;
            }
            mNewVideoUri = Uri.parse(dstUriString);
            return false;
        } catch (IOException t) {
            MtkLog.w(TAG, "getBookmark failed", t);
        }
        return true;
    }

    private Uri changeUriFormatIfNeed(Uri srcUri) {
        Uri uri = srcUri;
        if (uri.toString().toLowerCase(Locale.ENGLISH).contains("file:///")) {
            Cursor cursor = null;
            String data = Uri.decode(uri.toString());
            data = data.replaceAll("'", "''");
            int id = 0;
            final String where = "_data LIKE '%" + data.replaceFirst("file:///", "") + "'";
            try {
                cursor = mContext.getContentResolver().query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        new String[] { MediaStore.Video.Media._ID }, where, null, null);

                if (cursor != null && cursor.moveToFirst()) {
                    id = cursor.getInt(0);
                }
                MtkLog.i(TAG, "changeUriFormatIfNeed id " + id);
                uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "" + id);
            } catch (final SQLiteException ex) {
                ex.printStackTrace();
            } catch (IllegalArgumentException e) {
                MtkLog.v(TAG, "ContentResolver query IllegalArgumentException");
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        MtkLog.i(TAG, "changeUriFormatIfNeed uri " + uri);
        return uri;
    }


    private void startTranscode() {
      MtkLog.v(TAG, "startTranscode mState " + mState);
      mItem = new SlowMotionItem(mContext, mUri);
      if (mItem.getSpeed() == SlowMotionItem.SLOW_MOTION_ONE_SIXTEENTH_SPEED) {
          Toast.makeText(mContext.getApplicationContext(),
                  mContext.getString(R.string.not_support_share_hint_for_16x), Toast.LENGTH_LONG).show();
          return;
      }
      showProgressDialog();
      mSlowMotionInfo = mItem.getSlowMotionInfo();
      //here should check whether the video has been transacted with same parameter or not.
      if (isNeedTranscode(mUri)) {
          mTranscode = new SlowMotionTranscode(mContext);
          mTranscode.setOnInfoListener(new SlowMotionTranscode.OnInfoListener() {
            @Override
            public boolean onInfo(int msg, int ext1, int ext2) {
                // TODO Auto-generated method stub
                if (TRANSCODE_COMPLETE == msg) {
                    //onInfo() is called in native thread.
                    //Should not directly call stop() here or dead lock will be happened.
                    stopTranscodeAsync();
                }
                return true;
            }
        });
        mTranscode.setSpeedEffectParams(mItem.getSectionStartTime(),
                mItem.getSectionEndTime(), "slow-motion-speed=" + mItem.getSpeed());
          try {
              mTranscode.startSaveSpeedEffect(mSrcVideoPath, mDstVideoPath);
              mState = TranscodeState.BUSY;
          } catch (IOException e) {
              e.printStackTrace();
              throw new AssertionError("startSaveSpeedEffect IOException");
          }
      } else {
          mIntent.putExtra(Intent.EXTRA_STREAM, mNewVideoUri);
          MtkLog.v(TAG, "startTranscode,mIntent:" + mIntent.getStringExtra(Intent.EXTRA_STREAM));
          mHandler.post(mStartVideoRunnable);
      }
    }


    private void stopTranscodeAsync() {
        MtkLog.v(TAG, "stopTranscodeAsync");
        
        mTanscodeStopTask =  new stopTask();
        mTanscodeStopTask.start();
    }

    private class stopTask extends Thread {
        @Override
        public void run() {
            MtkLog.v(TAG, "stopTask run");
            //stop action will take some time, do not call it in UI thread.
            mTranscode.stopSaveSpeedEffect();
            mState = TranscodeState.IDLE;
            //update new file to DB.
            updateNewFileToDB();
             //when transcode complete, should save info to bookmark.
            setBookmark(mUri, mItem.getSlowMotionInfo(), mDstVideoPath, mNewVideoUri);
            //when stop action is finish, should dismiss the progress bar.
            mIntent.putExtra(Intent.EXTRA_STREAM, mNewVideoUri);
            mHandler.post(mStartVideoRunnable);
        }
    }
    
    private void updateNewFileToDB() {
        Uri url = MediaStore.Files.getContentUri("external");
        
        int result = mContext.getContentResolver().delete(url,
                "_data=?",
                new String[] { mDstVideoPath }
                );
        MtkLog.i(TAG, "updateNewFileToDB result " + result);
        final ContentValues values = new ContentValues(1);
        values.put(Files.FileColumns.DATA, mDstVideoPath);
        values.put(Files.FileColumns.DISPLAY_NAME, mDstVideoName);
        
        mNewVideoUri = mContext.getContentResolver().insert(url, values);
        MtkLog.v(TAG, "updateNewFileToDB mNewVideoUri " + mNewVideoUri);
    }

    /// disable transcode when sdcard is full. @{
    /**
    * get available space which storage source video is in.
    * @return the available space size, -1 means max storage size.
    */
    private long getAvailableSpace(String path) {
        // Here just use one directory to stat fs.
        StatFs stat = new StatFs(path);
        return stat.getAvailableBlocks() * (long) stat.getBlockSize();
    }

    /**
    * calculate the space for video muted is enough or not
    * lowStorageThreshold is reserve space. ram optimize projec is 9M, the others is 48M.
    */
    private boolean isSpaceEnough(String srcPath) {
        long spaceNeed;
        long lowStorageThreshold;
        File srcFile = new File(srcPath);
        if (MtkVideoFeature.isGmoRAM()) {
            lowStorageThreshold = 9 * 1024 * 1024;
        } else {
            lowStorageThreshold = 48 * 1024 * 1024;
        }
        spaceNeed = srcFile.length() + lowStorageThreshold;
        if (getAvailableSpace(srcFile.getPath()) < spaceNeed) {
            MtkLog.v(TAG, "space is not enough for save tanscode video");
            return false;
        } else {
            return true;
        }
    }
    /// @}


    /// M: add for show toast @{
    private final Runnable mShowToastRunnable = new Runnable() {
        @Override
        public void run() {
            Toast.makeText(mContext.getApplicationContext(),
                    mContext.getString(R.string.can_not_trim),
                    Toast.LENGTH_SHORT)
                    .show();
        }
    };

    /**
     * Show toast when the video can't be transacted
     */
    private void showToast() {
        mHandler.removeCallbacks(mShowToastRunnable);
        mHandler.post(mShowToastRunnable);
    }

    private void showProgressDialog() {
        // create a background thread to transcode the video.
        // and show the progress.
        mProgress = new ProgressDialog(mContext);
        mProgress.setMessage(mContext.getString(R.string.please_wait));
        // TODO: make this cancelable.
        mProgress.setCancelable(false);
        mProgress.setCanceledOnTouchOutside(false);
        mProgress.show();
    }

    ///M: for rename file from filemanager case, get absolute path from uri.@{
    private String getVideoPath(final Context context, Uri uri) {
            String videoPath = null;
            Cursor cursor = null;
            MtkLog.v(TAG, "getVideoPath(" + uri + ")");
            try {
                //query from "content://....."
                cursor = context.getContentResolver().query(uri,
                        new String[] { MediaStore.Video.Media.DATA }, null, null,
                        null);
                //query from "file:///......"
                if (cursor == null) {
                    String data = Uri.decode(uri.toString());
                    data = data.replaceAll("'", "''");
                    final String where = "_data LIKE '%" + data.replaceFirst("file:///", "") + "'";
                    cursor = context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Video.Media.DATA}, where, null, null);
                }
                if (cursor != null && cursor.moveToFirst()) {
                    videoPath = cursor.getString(0);
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
            return videoPath;
        }
}