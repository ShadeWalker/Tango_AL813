package com.mediatek.galleryfeature.video;

import java.util.concurrent.atomic.AtomicLong;

import android.graphics.Rect;
import android.media.MediaMetadataRetriever;

import com.mediatek.galleryframework.base.Generator;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.transcode.VideoTranscode;

public class VideoToVideoGenerator extends Generator {
    private static final String TAG = "MtkGallery2/VideoToVideoGenerator";
    private static final String sDbgNoTranscodingInProcess
        = "now no transcoding in process; here we go! ";
    private static String sDbgTranscodingProcessTracking
        = sDbgNoTranscodingInProcess;
    private static long sDbgLastTranscodingStartTime = 0;

    private static final int HANDLE_CANCEL = -2;
    private static final int HANDLE_UNINITIALIZED = -1;
    private long mHandle = HANDLE_UNINITIALIZED;
    private final Object mHandleLock = new Object();

    private static Rect getTargetRect(int srcWidth, int srcHeight, int maxWidth, int maxHeight) {
        if ((srcWidth <= maxWidth) || (srcHeight <= maxHeight)) {
            return new Rect(0, 0, srcWidth, srcHeight);
        }

        float rSrc = (float) srcWidth / srcHeight;
        float rMax = (float) maxWidth / maxHeight;

        int targetWidth;
        int targetHeight;

        // crop and scale
        if (rSrc < rMax) {
            targetWidth = maxWidth;
            targetHeight = targetWidth * srcHeight / srcWidth;
        } else {
            targetHeight = maxHeight;
            targetWidth = targetHeight * srcWidth / srcHeight;
            // width must be the factor of 16, find closest but smallest factor
            if (targetWidth % 16 != 0) {
                targetWidth = (targetWidth - 15) >> 4 << 4;
                targetHeight = targetWidth * srcHeight / srcWidth;
            }
        }

        return new Rect(0, 0, targetWidth, targetHeight);
    }

    protected int generate(MediaData item, int videoType, final String targetFilePath) {
        synchronized (sDbgTranscodingProcessTracking) {
            MtkLog.d(TAG, "<generate>" + sDbgTranscodingProcessTracking + sDbgLastTranscodingStartTime);
        }
        int res;
        synchronized (VideoToVideoGenerator.class) {
            res = innerGenerate(item, videoType, targetFilePath);
        }
        return res;
    }

    protected int innerGenerate(MediaData item, int videoType, final String targetFilePath) {
        synchronized (mHandleLock) {
            if (mHandle == HANDLE_CANCEL) {
                return GENERATE_CANCEL;
            }
        }

        // the width and height stored in MediaStore may be reversed
        // here we use MediaMetadataRetriever instead to get the real width and height
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        int videoWidth;
        int videoHeight;
        try {
            MtkLog.v(TAG, "doTranscode: set retriever.setDataSource begin <"
                    + item.filePath + ">");
            retriever.setDataSource(item.filePath);
            MtkLog.v(TAG, "doTranscode: set retriever.setDataSource end");
            videoWidth = Integer
                    .parseInt(retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            videoHeight = Integer
                    .parseInt(retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        } catch (IllegalArgumentException e) {
            videoWidth = item.width;
            videoHeight = item.height;
        } catch (Exception e) {
            // native layer would throw runtime exception if setDatasource() fails
            // setDataSource() could fail because of simple matters such as "can not find a
            // corresponding parser", which is however said to be google default.
            // so it looks ok and reasonable to catch such runtime exception here
            e.printStackTrace();
            videoWidth = item.width;
            videoHeight = item.height;
        }
        retriever.release();
        retriever = null;
        Rect srcRect = new Rect(0, 0, videoWidth, videoHeight);
        Rect targetRect = getTargetRect(srcRect.width(), srcRect.height(), VideoConfig.ENCODE_WIDTH,
                                        VideoConfig.ENCODE_HEIGHT);
        MtkLog.v(TAG, "srcRect: " + srcRect + " targetRect: " + targetRect);
        // duration is not so accurate as gotten from meta retriever,
        // but it's already enough
        long duration = item.duration;
        long startTime = duration / 3;  // eh, magic number?
        long endTime = Math.min(duration, startTime + VideoConfig.MAX_THUMBNAIL_DURATION);
        startTime = Math.max(0, endTime - VideoConfig.MAX_THUMBNAIL_DURATION);

        synchronized (mHandleLock) {
            if (mHandle == HANDLE_CANCEL) {
                return GENERATE_CANCEL;
            }
        }

        long width = (long) targetRect.width();
        long height = (long) targetRect.height();

        MtkLog.v(TAG, "start transcoding: " + item.filePath + " to " + videoPath[videoType] + ", target width = " + width + ", target height = " + height);
        MtkLog.v(TAG, "starttime = " + startTime + ", endtime = " + endTime);
        long transcodeId = VideoTranscode.init();
        synchronized (mHandleLock) {
            if (mHandle == HANDLE_UNINITIALIZED) {
                mHandle = transcodeId;
            }
            // else: mHandle == HANDLE_CANCEL, let it go (transcodeAdv() would do nothing)
            //       and after transcoding, still mHandle == HANDLE_CANCEL
        }

        synchronized (sDbgTranscodingProcessTracking) {
            sDbgTranscodingProcessTracking = "now transcoding file " + item.filePath + "; please wait...";
            sDbgLastTranscodingStartTime = System.currentTimeMillis();
        }
        int result = VideoTranscode.transcodeAdv(transcodeId,
                item.filePath, targetFilePath, (long) targetRect.width(),
                (long) targetRect.height(), startTime, endTime,
                VideoConfig.TRANSCODING_BIT_RATE, VideoConfig.TRANSCODING_FRAME_RATE);
        synchronized (sDbgTranscodingProcessTracking) {
            sDbgLastTranscodingStartTime = 0;
            sDbgTranscodingProcessTracking = sDbgNoTranscodingInProcess;
        }

        synchronized (mHandleLock) {
            if (mHandle == HANDLE_CANCEL) {
                result = GENERATE_CANCEL;
            } else if (result == VideoTranscode.NO_ERROR) {
                result = GENERATE_OK;
            } else {
                result = GENERATE_ERROR;
            }
            mHandle = HANDLE_UNINITIALIZED;
        }

        MtkLog.v(TAG, "end transcoding: " + item.filePath + " to " + videoPath[videoType]);
        VideoTranscode.deinit(transcodeId);

        return result;
    }

    public void onCancelRequested(MediaData item, int videoType) {
        synchronized (mHandleLock) {
            if (mHandle != HANDLE_CANCEL) {
                if (mHandle != HANDLE_UNINITIALIZED) {
                    MtkLog.i(TAG, "<onCancelRequested> " + mHandle);
                    VideoTranscode.cancel(mHandle);
                }
                mHandle = HANDLE_CANCEL;
            }
        }
    }
}