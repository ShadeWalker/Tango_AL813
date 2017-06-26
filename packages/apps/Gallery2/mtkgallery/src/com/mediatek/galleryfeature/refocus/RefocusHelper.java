/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mediatek.galleryfeature.refocus;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.sql.Date;
import java.text.SimpleDateFormat;

import android.app.DialogFragment;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.exif.CountedDataInputStream;
import com.android.gallery3d.exif.ExifInvalidFormatException;
import com.android.gallery3d.exif.JpegHeader;
import com.mediatek.galleryframework.util.MtkLog;

public class RefocusHelper {
    public static final String TAG = "Gallery2/Refocus/RefocusHelper";
    public static int mDepthMapInfoStartPosition;
    public static int mApp15Length;
    public static int mOffsetToApp15EndFromSOF;
    private static final String DEFAULT_SAVE_DIRECTORY = "RefocusLocalImages";

    private static final String TIME_STAMP_NAME = "_yyyyMMdd_HHmmss";
    private static final String PREFIX_IMG = "IMG";
    private static final String POSTFIX_JPG = ".jpg";

    private interface ContentResolverQueryCallback {
        void onCursorResult(Cursor cursor);
    }
    
    public static boolean depthMapParse(InputStream inputStream) throws IOException, ExifInvalidFormatException {
        CountedDataInputStream dataStream = new CountedDataInputStream(inputStream);
        if (dataStream.readShort() != JpegHeader.SOI) {
            throw new ExifInvalidFormatException("Invalid JPEG format");
        }

        short marker = dataStream.readShort();
        Log.i(TAG, "seektiffdata marker start " + marker);

        while (marker != JpegHeader.EOI
                && !JpegHeader.isSofMarker(marker)) {
            int length = dataStream.readUnsignedShort();
            Log.i(TAG, "seektiffdata marker length " + length);
            // Some invalid formatted image contains multiple APP1,
            // try to find the one with Exif data.
            if (marker == JpegHeader.APP15) {
                if (length > 0) {
                    mDepthMapInfoStartPosition = dataStream.getReadByteCount();
                    mApp15Length = length;
                    mOffsetToApp15EndFromSOF = mDepthMapInfoStartPosition + mApp15Length;
                    Log.i(TAG, "JpegHeader.APP15  mTiffStartPosition " + mDepthMapInfoStartPosition + " mApp1End " + mApp15Length + " mOffsetToApp1EndFromSOF " + mOffsetToApp15EndFromSOF);
                }
                return true;
            }
            if (length < 2 || (length - 2) != dataStream.skip(length - 2)) {
                Log.w(TAG, "Invalid JPEG format.");
                return false;
            }
            marker = dataStream.readShort();
            Log.i(TAG, "seektiffdata marker end " + marker);
        }
        return false;
    }
    
    public static boolean rewriteDepthMapExif(String filename, byte[] depthMapBuffer)
            throws FileNotFoundException, IOException {
        RandomAccessFile file = null;
        try {
            File temp = new File(filename);//new File(filename);

            // Open file for memory mapping.
            file = new RandomAccessFile(temp, "rw");
            long fileLength = file.length();
            if (fileLength < mApp15Length) {
                throw new IOException("Filesize changed during operation");
            }

            // Map only exif app15 data into memory.
            ByteBuffer buf = file.getChannel().map(MapMode.READ_WRITE, mDepthMapInfoStartPosition, mApp15Length);
            int length = depthMapBuffer.length;
            // Attempt to overwrite tag values without changing lengths (avoids
            // file copy).
            for (int i = 0; i < length; i++) {
                buf.put(i, depthMapBuffer[i]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (file != null) {
                file.close();
            }
        }
        return true;
    }

    public static String getRealFilePathFromURI(Context context, Uri uri){
        String[] proj = { MediaStore.Images.Media.DATA}; 
        Cursor cursor = null;
        String filePath = null;
        try{
            cursor = context.getContentResolver().query(uri,proj,null,null,null);
            if (cursor == null) {
                return null;
            }
            int colummIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            filePath = cursor.getString(colummIndex);
            Log.i(TAG, "getImageRealPathFromURI colummIndex= " + filePath); 
        } catch (Exception e) {
            Log.e(TAG, "getImageRealPathFromURI Exception", e); 
        } finally {
            if(cursor != null){
                cursor.close();
            }
        }
        return filePath;
    }
    

    public static Uri insertContent(Context context, Uri sourceUri, File file, String saveFileName) {
        long now = System.currentTimeMillis() / 1000;

        final ContentValues values = new ContentValues();
        values.put(Images.Media.TITLE, saveFileName);
        values.put(Images.Media.DISPLAY_NAME, file.getName());
        values.put(Images.Media.MIME_TYPE, "image/jpeg");
        values.put(Images.Media.DATE_TAKEN, now);
        values.put(Images.Media.DATE_MODIFIED, now);
        values.put(Images.Media.DATE_ADDED, now);
        values.put(Images.Media.ORIENTATION, 0);
        values.put(Images.Media.DATA, file.getAbsolutePath());
//        values.put(Images.Media.DATA, "/storage/sdcard0/DCIM/imageRefocusTest.jpg");
        values.put(Images.Media.SIZE, file.length());
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int imageLength = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0);
            int imageWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0);
            values.put(Images.Media.WIDTH, imageWidth);
            values.put(Images.Media.HEIGHT, imageLength);
        } catch (IOException ex) {
            Log.w(TAG, "ExifInterface throws IOException", ex);
        }

        final String[] projection = new String[] {
                ImageColumns.DATE_TAKEN,
                ImageColumns.LATITUDE,
                ImageColumns.LONGITUDE,
        };
        querySource(context, sourceUri, projection,
                new ContentResolverQueryCallback() {

            @Override
            public void onCursorResult(Cursor cursor) {
                values.put(Images.Media.DATE_TAKEN, cursor.getLong(0));

                double latitude = cursor.getDouble(1);
                double longitude = cursor.getDouble(2);
                // TODO: Change || to && after the default location issue is
                // fixed.
                if ((latitude != 0f) || (longitude != 0f)) {
                    values.put(Images.Media.LATITUDE, latitude);
                    values.put(Images.Media.LONGITUDE, longitude);
                }
            }
        });
        Uri insertUri = context.getContentResolver().insert(
                Images.Media.EXTERNAL_CONTENT_URI, values);
        MtkLog.i(TAG, "insertUri = " + insertUri);
        return insertUri;
//        return context.getContentResolver().insert(
//                Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    private static void querySource(Context context, Uri sourceUri, String[] projection,
            ContentResolverQueryCallback callback) {
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(sourceUri, projection, null, null,
                    null);
            if ((cursor != null) && cursor.moveToNext()) {
                callback.onCursorResult(cursor);
            }
        } catch (Exception e) {
            // Ignore error for lacking the data column from the source.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static File getNewFile(Context context, Uri sourceUri, String saveFileName) {
        File saveDirectory = getFinalSaveDirectory(context, sourceUri);
        return new File(saveDirectory, saveFileName + ".JPG");
    }

    public static File getFinalSaveDirectory(Context context, Uri sourceUri) {
        File saveDirectory = getSaveDirectory(context, sourceUri);
        if ((saveDirectory == null) || !saveDirectory.canWrite()) {
            saveDirectory = new File(Environment.getExternalStorageDirectory(),
                    DEFAULT_SAVE_DIRECTORY);
        }
        // Create the directory if it doesn't exist
        if (!saveDirectory.exists()) saveDirectory.mkdirs();
        return saveDirectory;
    }

    private static File getSaveDirectory(Context context, Uri sourceUri) {
        final File[] dir = new File[1];
        querySource(context, sourceUri, new String[] {
                ImageColumns.DATA
        },
                new ContentResolverQueryCallback() {
                    @Override
                    public void onCursorResult(Cursor cursor) {
                        dir[0] = new File(cursor.getString(0)).getParentFile();
                    }
                });
        return dir[0];
    }
    
    public static Bitmap decodeBitmap(Uri uri,Context context) {
        MtkLog.i(TAG, "uri = " + uri);
        Bitmap bitmap = null;
        if (uri == null || context == null) {
            throw new IllegalArgumentException("bad argument to loadBitmap");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = RefocusImage.INSAMPLESIZE;
        options.inScaled = true;
        InputStream is = null;
        try {
            is = context.getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            MtkLog.i(TAG, "file not found! is = " + is);
            e.printStackTrace();
        }
        bitmap = BitmapFactory.decodeStream(is, null, options);
        return bitmap;
    }

    //M: for test Image refocus 
    public static String DEPTH_PATH = "/storage/sdcard0/DCIM/imageRefocusDepthMapBuffer.bin";
    public static String XMP_DEPTH_PATH = "/storage/sdcard0/DCIM/imageRefocusXMPDepthMapBuffer.bin";
    public static int DEPTH_BUFFERSIZE= 24480;

    public static void saveDepthBufferToFileForTest(byte[] byteArray, String filePath) {
        BufferedOutputStream bos = null;
        FileOutputStream fos = null;
        File file = null;
        try {
            file = new File(filePath);
            if (!file.exists() && file.isDirectory()) {
                file.mkdirs();
            }
            fos = new FileOutputStream(file);
            bos = new BufferedOutputStream(fos);
            bos.write(byteArray);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }
    
    public static byte[] getDepthBufferFromFileForTest() {
        byte[] buffer = null;
        try {
            File file = new File(DEPTH_PATH);
            FileInputStream fis = new FileInputStream(file);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(DEPTH_BUFFERSIZE);
            byte[] b = new byte[DEPTH_BUFFERSIZE];
            int n;
            while ((n = fis.read(b)) != -1) {
                bos.write(b, 0, n);
            }
            fis.close();
            bos.close();
            buffer = bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buffer;
    }
    
    public static File getNewFile(Context context, Uri sourceUri) {
        File saveDirectory = getFinalSaveDirectory(context, sourceUri);
        String filename = new SimpleDateFormat(TIME_STAMP_NAME).format(new Date(
                System.currentTimeMillis()));
        return new File(saveDirectory, PREFIX_IMG + filename + POSTFIX_JPG);
    }
    
    public class Config {
        int jpsWidth;
        int jpsHeight;
        int maskWidth;
        int maskHeight;
        int posX;
        int posY;
        int viewWidth;
        int viewHeight;
        int orientation;
        int mainCamPos;
        int touchCoordX1st;
        int touchCoordY1st;
    }
}




