/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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

package com.android.gallery3d.data;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory.Options;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.app.PanoramaMetadataSupport;
import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.util.ThreadPool.CancelListener;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.mediatek.galleryframework.base.ExtItem;
import com.mediatek.galleryframework.base.ExtItem.Thumbnail;
import com.mediatek.gallery3d.adapter.FeatureHelper;
import com.mediatek.gallery3d.adapter.PhotoPlayFacade;
import com.mediatek.gallery3d.adapter.MediaDataParser;
import com.mediatek.gallery3d.util.DecodeSpecLimitor;
import com.mediatek.galleryfeature.config.FeatureConfig;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

public class UriImage extends MediaItem {
    private static final String TAG = "Gallery2/UriImage";

    private static final int STATE_INIT = 0;
    private static final int STATE_DOWNLOADING = 1;
    private static final int STATE_DOWNLOADED = 2;
    private static final int STATE_ERROR = -1;

    private final Uri mUri;
    private final String mContentType;

    private DownloadCache.Entry mCacheEntry;
    private ParcelFileDescriptor mFileDescriptor;
    private int mState = STATE_INIT;
    private int mWidth;
    private int mHeight;
    private int mRotation;
    private PanoramaMetadataSupport mPanoramaMetadata = new PanoramaMetadataSupport(this);

    private GalleryApp mApplication;

    public UriImage(GalleryApp application, Path path, Uri uri, String contentType) {
        super(path, nextVersionNumber());
        mUri = uri;
        mApplication = Utils.checkNotNull(application);
        mContentType = contentType;
        Log.i(TAG, "<UriImage> mUri " + mUri + " mContentType " + mContentType);
        /// M: [FEATURE.ADD] @{
        updateMediaData();
        /// @}
    }

    @Override
    public Job<Bitmap> requestImage(int type) {
        return new BitmapJob(type);
    }

    @Override
    public Job<BitmapRegionDecoder> requestLargeImage() {
        return new RegionDecoderJob();
    }

    private void openFileOrDownloadTempFile(JobContext jc) {
        int state = openOrDownloadInner(jc);
        synchronized (this) {
            mState = state;
            if (mState != STATE_DOWNLOADED) {
                if (mFileDescriptor != null) {
                    Utils.closeSilently(mFileDescriptor);
                    mFileDescriptor = null;
                }
            }
            notifyAll();
        }
    }

    private int openOrDownloadInner(JobContext jc) {
        String scheme = mUri.getScheme();
        if (ContentResolver.SCHEME_CONTENT.equals(scheme)
                || ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)
                || ContentResolver.SCHEME_FILE.equals(scheme)) {
            /// M: [FEATURE.ADD] <CTA Data Protection> @{
            if (mMediaData.filePath != null &&
                    mMediaData.filePath.toLowerCase().endsWith(".mudp")) {
                Log.i(TAG, "<openOrDownloadInner> return STATE_DOWNLOADED");
                return STATE_DOWNLOADED;
            }
            /// @}
            try {
                if (MIME_TYPE_JPEG.equalsIgnoreCase(mContentType)) {
                    InputStream is = mApplication.getContentResolver()
                            .openInputStream(mUri);
                    mRotation = Exif.getOrientation(is);
                    Utils.closeSilently(is);
                }
                mFileDescriptor = mApplication.getContentResolver()
                        .openFileDescriptor(mUri, "r");
                if (jc.isCancelled()) return STATE_INIT;
                return STATE_DOWNLOADED;
            } catch (FileNotFoundException e) {
                Log.w(TAG, "fail to open: " + mUri, e);
                return STATE_ERROR;
            }
        } else {
            try {
                URL url = new URI(mUri.toString()).toURL();
                /// M: [BUG.MODIFY] @{
                /*mCacheEntry = mApplication.getDownloadCache().download(jc, url);*/
                DownloadCache downloadCache = mApplication.getDownloadCache();
                if (downloadCache == null) {
                    Log.w(TAG, "<openOrDownloadInner> failed to get DownloadCache");
                    return STATE_ERROR;
                }
                mCacheEntry = downloadCache.download(jc, url);
                /// @}
                if (jc.isCancelled()) return STATE_INIT;
                if (mCacheEntry == null) {
                    Log.w(TAG, "download failed " + url);
                    return STATE_ERROR;
                }
                if (MIME_TYPE_JPEG.equalsIgnoreCase(mContentType)) {
                    InputStream is = new FileInputStream(mCacheEntry.cacheFile);
                    mRotation = Exif.getOrientation(is);
                    Utils.closeSilently(is);
                }
                mFileDescriptor = ParcelFileDescriptor.open(
                        mCacheEntry.cacheFile, ParcelFileDescriptor.MODE_READ_ONLY);
                return STATE_DOWNLOADED;
            } catch (Throwable t) {
                Log.w(TAG, "download error", t);
                return STATE_ERROR;
            }
        }
    }

    private boolean prepareInputFile(JobContext jc) {
        jc.setCancelListener(new CancelListener() {
            @Override
            public void onCancel() {
                synchronized (this) {
                    notifyAll();
                }
            }
        });

        while (true) {
            synchronized (this) {
                if (jc.isCancelled()) return false;
                if (mState == STATE_INIT) {
                    mState = STATE_DOWNLOADING;
                    // Then leave the synchronized block and continue.
                } else if (mState == STATE_ERROR) {
                    return false;
                } else if (mState == STATE_DOWNLOADED) {
                    return true;
                } else /* if (mState == STATE_DOWNLOADING) */ {
                    try {
                        wait();
                    } catch (InterruptedException ex) {
                        // ignored.
                    }
                    continue;
                }
            }
            // This is only reached for STATE_INIT->STATE_DOWNLOADING
            openFileOrDownloadTempFile(jc);
        }
    }

    private class RegionDecoderJob implements Job<BitmapRegionDecoder> {
        @Override
        public BitmapRegionDecoder run(JobContext jc) {
            if (!prepareInputFile(jc)) return null;
            BitmapRegionDecoder decoder = DecodeUtils.createBitmapRegionDecoder(
                    jc, mFileDescriptor.getFileDescriptor(), false);
            if (decoder == null) {
                return null;
            }
            mWidth = decoder.getWidth();
            mHeight = decoder.getHeight();
            /// M: [FEATURE.ADD] @{
            updateMediaData();
            /// @}
            /// M: [BUG.ADD] check decode spec @{
            if (mMediaData != null
                    && DecodeSpecLimitor.isOutOfSpecLimit(
                            getUriImageFileSize(mUri), mMediaData.width,
                            mMediaData.height, mContentType)) {
                Log.i(TAG, "<RegionDecoderJob.run> out of spec limit, abort decoding!");
                return null;
            }
            /// @}
            return decoder;
        }
    }

    private class BitmapJob implements Job<Bitmap> {
        private int mType;

        protected BitmapJob(int type) {
            mType = type;
        }

        @Override
        public Bitmap run(JobContext jc) {
            if (!prepareInputFile(jc)) return null;
            /// M: [BUG.ADD] @{
            // for images which not support RegionDecode, 
            // get width and height from decodeBounds.
            // Decode bound for Drm image.
            Options op = new Options();
            if (mFileDescriptor != null) {
                DecodeUtils.decodeBounds(jc, mFileDescriptor.getFileDescriptor(), op);
            } else {
                mExtItem.decodeBounds(op);
            }
            mWidth = op.outWidth;
            mHeight = op.outHeight;
            updateMediaData();
            /// @}
            /// M: [FEATURE.ADD] @{
            Thumbnail thumb = mExtItem.getThumbnail(FeatureHelper
                    .convertToThumbType(mType));
            if (thumb != null && thumb.mBitmap != null) {
                return thumb.mBitmap;
            }
            if (thumb != null && thumb.mBitmap == null
                    && thumb.mStillNeedDecode == false)
                return null;
            /// @}
            /// M: [BUG.ADD] check decode spec @{
            if (mMediaData != null
                    && DecodeSpecLimitor.isOutOfSpecLimit(
                            getUriImageFileSize(mUri), mMediaData.width,
                            mMediaData.height, mContentType)) {
                Log.i(TAG, "<BitmapJob.run> out of spec limit, abort decoding!");
                return null;
            }
            /// @}
            int targetSize = MediaItem.getTargetSize(mType);
            Options options = new Options();
            options.inPreferredConfig = Config.ARGB_8888;
            Bitmap bitmap = DecodeUtils.decodeThumbnail(jc,
                    mFileDescriptor.getFileDescriptor(), options, targetSize, mType);

            if (jc.isCancelled() || bitmap == null) {
                return null;
            }

            if (mType == MediaItem.TYPE_MICROTHUMBNAIL) {
                bitmap = BitmapUtils.resizeAndCropCenter(bitmap, targetSize, true);
            } else {
                bitmap = BitmapUtils.resizeDownBySideLength(bitmap, targetSize, true);
            }
            
            /// M: [BUG.ADD] @{
            // Some png bitmaps have transparent areas, so clear alpha value
            bitmap = BitmapUtils.clearAlphaValueIfPng(bitmap, mMediaData.mimeType, true);
            /// @}

            return bitmap;
        }
    }

    @Override
    public int getSupportedOperations() {
        int supported = SUPPORT_PRINT | SUPPORT_SETAS;
        if (isSharable()) supported |= SUPPORT_SHARE;
        if (BitmapUtils.isSupportedByRegionDecoder(mContentType)) {
            supported |= SUPPORT_EDIT | SUPPORT_FULL_IMAGE;
            /// M: [BUG.ADD] @{
            // if current item is not bigger than thumbnail size,
            // don't support full image display 
            FileDescriptor fd;
            try {
                mFileDescriptor = mApplication.getContentResolver()
                        .openFileDescriptor(mUri, "r");
                fd = mFileDescriptor.getFileDescriptor();
                Options options = new Options();
                options.inJustDecodeBounds = true;
                if (fd != null) {
                    BitmapFactory.decodeFileDescriptor(fd, null, options);
                    mWidth = options.outWidth;
                    mHeight = options.outHeight;
                    Log.i(TAG, "<getSupportedOperations>  mWidth " + mWidth + " mHeight " + mHeight);
                }
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            float scale = (float) sThumbnailTargetSize / Math.max(mWidth, mHeight);
            int thumbWidth = (int)(mWidth * scale);
            if (Math.max(0, Utils.ceilLog2((float) mWidth / thumbWidth)) == 0
                    || (FeatureConfig.isLowRamDevice && mWidth * mHeight > REGION_DECODER_PICTURE_SIZE_LIMIT)) {
                // 1. if current item is not bigger than thumbnail size,
                // don't support full image display 
                // 2. use high-quality screennail instead of region decoder for extremely large image
                Log.i(TAG, "<getSupportedOperations> item thumbWidth " + thumbWidth +" scale " + scale);
                Log.i(TAG, "<getSupportedOperations> item not support full image, mWidth " + mWidth +
                        " sthumbnailsize " + sThumbnailTargetSize);
                Log.i(TAG, "<getSupportedOperations> isLowRamDevice "
                        + FeatureConfig.isLowRamDevice + ", mWidth * mHeight is "
                        + mWidth * mHeight);
                supported &= ~SUPPORT_FULL_IMAGE;
            }
            /// @}
        }
        
        /// M: [FEATURE.ADD] @{
        supported = FeatureHelper.mergeSupportOperations(supported,
                mExtItem.getSupportedOperations(),
                mExtItem.getNotSupportedOperations());
        /// @}
        return supported;
    }

    @Override
    public void getPanoramaSupport(PanoramaSupportCallback callback) {
        mPanoramaMetadata.getPanoramaSupport(mApplication, callback);
    }

    @Override
    public void clearCachedPanoramaSupport() {
        mPanoramaMetadata.clearCachedValues();
    }

    private boolean isSharable() {
        // We cannot grant read permission to the receiver since we put
        // the data URI in EXTRA_STREAM instead of the data part of an intent
        // And there are issues in MediaUploader and Bluetooth file sender to
        // share a general image data. So, we only share for local file.
        return ContentResolver.SCHEME_FILE.equals(mUri.getScheme());
    }

    @Override
    public int getMediaType() {
        return MEDIA_TYPE_IMAGE;
    }

    @Override
    public Uri getContentUri() {
        return mUri;
    }

    @Override
    public MediaDetails getDetails() {
        MediaDetails details = super.getDetails();
        if (mWidth != 0 && mHeight != 0) {
            details.addDetail(MediaDetails.INDEX_WIDTH, mWidth);
            details.addDetail(MediaDetails.INDEX_HEIGHT, mHeight);
        }
        if (mContentType != null) {
            details.addDetail(MediaDetails.INDEX_MIMETYPE, mContentType);
        }
        if (ContentResolver.SCHEME_FILE.equals(mUri.getScheme())) {
            String filePath = mUri.getPath();
            details.addDetail(MediaDetails.INDEX_PATH, filePath);
            MediaDetails.extractExifInfo(details, filePath);
        }
        return details;
    }

    @Override
    public String getMimeType() {
        return mContentType;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (mFileDescriptor != null) {
                Utils.closeSilently(mFileDescriptor);
            }
        } finally {
            super.finalize();
        }
    }

    @Override
    public int getWidth() {
        /// M: [FEATURE.MARK] @{
        /*return 0;*/
        return mWidth;
        /// @}
    }

    @Override
    public int getHeight() {
        /// M: [FEATURE.MARK] @{
        /*return 0;*/
        return mHeight;
        /// @}
    }

    @Override
    public int getRotation() {
        return mRotation;
    }

    //********************************************************************
    //*                              MTK                                 *
    //********************************************************************

    public void updateMediaData() {
        synchronized (mMediaDataLock) {
            mMediaData = MediaDataParser.parseUriImageMediaData(UriImage.this);
            mExtItem = PhotoPlayFacade.getMediaCenter().getItem(mMediaData);
        }
    }

    private long getUriImageFileSize(Uri uri) {
        String[] proj = { MediaStore.Video.Media.DATA };
        Cursor cursor = null;
        long fileSize = 0;
        String nameFromURI = null;
        try {
            cursor = mApplication.getContentResolver().query(uri, proj, null,
                    null, null);
            if (cursor == null) {
                return 0;
            }
            int colummIndex = cursor
                    .getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
            cursor.moveToFirst();
            nameFromURI = cursor.getString(colummIndex);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "<getUriImageFileSize> Exception", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (nameFromURI != null) {
            File file = new File(nameFromURI);
            if (file.exists()) {
                fileSize = file.length();
            }
        }
        return fileSize;
    }
}
