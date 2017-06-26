package com.mediatek.galleryfeature.livephoto;

import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.media.CamcorderProfile;
import android.content.Context;
import android.graphics.Bitmap;

import com.mediatek.effect.effects.VideoScenarioEffect;
import com.mediatek.camcorder.CamcorderProfileEx;

import com.mediatek.galleryframework.base.Generator;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.util.Utils;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.galleryfeature.config.FeatureConfig;

public class LivePhotoToVideoGenerator extends Generator {
    private final static String TAG = "MtkGallery2/LivePhotoToVideoGenerator";
    public final static int LIVEPHOTO_CROP_WIDTH = 512; //need 16 alignment
    public final static int LIVEPHOTO_CROP_HEIGHT = 512; //need 16 alignment
    private final static int LIVEPHOTO_CROP_FPS = 15;
    private final static int LIVEPHOTO_CROP_BITRATE = FeatureConfig.isLowRamDevice ? 512 * 1024
            : 1024 * 1024;
    private final static int LIVEPHOTO_FRAME_PICTURE_DURATION = 1500;
    private final static int LIVEPHOTO_FRAME_PICTURE_TRANSITION_DURATION = 500;
    private final static int LIVEPHOTO_FRAME_PICTURE_FADEOUT_DURATION = 300;
    private final static int LIVEPHOTO_FRAME_PICTURE_TOTAL_DURATION = LIVEPHOTO_FRAME_PICTURE_DURATION
            + LIVEPHOTO_FRAME_PICTURE_TRANSITION_DURATION
            + LIVEPHOTO_FRAME_PICTURE_FADEOUT_DURATION;

    private String mInPath;
    private String mOutPath;
    private Bitmap object1; // fixed name for media filter, can not be changed.
    private boolean mIsCancelling; // use to distinguish cancel generating or process fail
    private Context mContext;
    private VideoScenarioEffect mVideoScenarioEffect;
    private static VideoScenarioEffect mStaticVideoScenarioEffect;
    private static CancelThread mCancelThread;

    public LivePhotoToVideoGenerator(Context context) {
        mContext = context;
        mIsCancelling = false;
        mVideoScenarioEffect = new VideoScenarioEffect();
        if (mCancelThread == null) {
            mCancelThread = new CancelThread();
            mCancelThread.start();
        }
        MtkLog.i(TAG, "<LivePhotoToVideoGenerator> new LivePhotoToVideoGenerator ");
    }

    @Override
    public int generate(MediaData item, int videoType, final String targetFilePath) {
        MtkLog.i(TAG, "<generate> Gallery2 Performance: enter generate()");
        if (!(item.isLivePhoto)) {
            return GENERATE_ERROR;
        }

        boolean result = false;
        long logTimeBefore = 0;
        mInPath = item.filePath;
        MtkLog.i(TAG, "<generate> inPath: " + mInPath);

        mOutPath = targetFilePath;
        object1 = createVideoFirstFramePicture(mInPath);
        if (object1 == null) {
            MtkLog.i(TAG, "<generate> object1 == null, error");
            return GENERATE_ERROR;
        }

        CamcorderProfile cp = CamcorderProfileEx.getProfile(
                FeatureConfig.isLowRamDevice ? CamcorderProfile.QUALITY_480P
                        : CamcorderProfile.QUALITY_720P);
        if (cp == null) {
            MtkLog.i(TAG, "<generate> CamcorderProfile == null, error");
            return GENERATE_ERROR;
        }

        cp.videoCodec = MediaRecorder.VideoEncoder.MPEG_4_SP;
        cp.videoBitRate = LIVEPHOTO_CROP_BITRATE;
        cp.videoFrameRate = LIVEPHOTO_CROP_FPS;
        cp.videoFrameHeight = LIVEPHOTO_CROP_HEIGHT;
        cp.videoFrameWidth = LIVEPHOTO_CROP_WIDTH;

        int duration = item.duration - LIVEPHOTO_FRAME_PICTURE_TOTAL_DURATION;

        String scenario = getGalleryScenario(cp.videoFrameWidth, cp.videoFrameHeight, duration);

        if (mIsCancelling) {
            freeResources();
            mIsCancelling = false;
            MtkLog.i(TAG, "<generate> return GENERATE_CANCEL(1)");
            return GENERATE_CANCEL;
        }

        synchronized (mVideoScenarioEffect) {
            if (!mVideoScenarioEffect.setScenario(mContext, scenario, cp, object1, object1)) {
                MtkLog.i(TAG, "<generate> setScenario(), error");
                freeResources();
                return GENERATE_ERROR;
            }

            if (mIsCancelling) {
                freeResources();
                mIsCancelling = false;
                MtkLog.i(TAG, "<generate> return GENERATE_CANCEL(2)");
                return GENERATE_CANCEL;
            }

            MtkLog.i(TAG, "<generate> Gallery2 Performance: process begin");
            logTimeBefore = System.currentTimeMillis();
            result = mVideoScenarioEffect.process();
        }

        freeResources();

        if (mIsCancelling) {
            mIsCancelling = false;
            MtkLog.i(TAG, "<generate> return GENERATE_CANCEL(3)");
            return GENERATE_CANCEL;
        }
        MtkLog.i(TAG, "<generate> Gallery2 Performance: process end | result: " + result);

        if (result) {
            MtkLog.i(TAG, "<generate> Gallery2 Performance: processing costs "
                    + (System.currentTimeMillis() - logTimeBefore) + " ms");
            return GENERATE_OK;
        } else {
            return GENERATE_ERROR;
        }
    }

    private void freeResources() {
        if (object1 != null) {
            MtkLog.i(TAG, "<freeResources>");
            object1.recycle();
            object1 = null;
        }
    }

    private Bitmap createVideoFirstFramePicture(String videoPath) {
        Bitmap bitmap = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(videoPath);
            bitmap = retriever.getFrameAtTime(0);
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        } finally {
            retriever.release();
        }
        return bitmap;
    }

    private class CancelThread extends Thread {
        private boolean active = true;
        private boolean dirty = false;

        @Override
        public void run() {
            //M: add thread name
            setName("LivePhoto CancelThread");
            while (active) {
                synchronized (this) {
                    if (active && !dirty) {
                        MtkLog.i(TAG, "<CancelThread.run> wait");
                        Utils.waitWithoutInterrupt(this);
                        continue;
                    }
                    dirty = false;
                }
                long logTimeBefore = System.currentTimeMillis();
                synchronized (mStaticVideoScenarioEffect) {
                    mStaticVideoScenarioEffect.cancel();
                }
                MtkLog.i(TAG, "<CancelThread.run> Gallery2 Performance: onCancelRequested() costs "
                        + (System.currentTimeMillis() - logTimeBefore) + " ms");
            }
        }
        public synchronized void notifyCancel() {
            MtkLog.i(TAG, "<CancelThread.notifyCancel>");
            dirty = true;
            notifyAll();
        }
    }

    @Override
    public void onCancelRequested(MediaData item, int videoType) {
            mIsCancelling = true;
            // update VideoScenarioEffect
            mStaticVideoScenarioEffect = mVideoScenarioEffect;
            mCancelThread.notifyCancel();
    }

    private String getGalleryScenario(int width, int height, int duration) {
        String fixBitmap = "object1";
        MtkLog.i(TAG, "<getGalleryScenario> width: " + width + ", height: " + height);

        float x = 0.165f;
        float y = 0.76f;
        float scale = 0.45f;

        String scenario =
            "<?xml version=\"1.0\"?>" +
            "<scenario>" +
            "   <size owidth=\"" + width + "\" oheight=\"" + height + "\"></size>" +
            "   <video>/system/media/video/gen30.mp4</video>" +
            "   <video init_offset=\"2050\">" + mInPath + "</video>" +  // input path
            "   <edge>/system/media/video/edge.png</edge>" +  // the edge frame
            "   <outputvideo truncate=\"1\">" + mOutPath + "</outputvideo>" +  // the output path

            "   <videoevent name=\"ve\" type=\"still\" start=\"0\" end=\"1500\">" +
            "   <background>" + fixBitmap + "</background>" +
            "   </videoevent>" +

            "   <videoevent name=\"ve\" type=\"overlay\" start=\"1500\" end=\"2000\">" +
            "   <showtime related_start=\"0\" length=\"500\"></showtime>" +
            "   <thumbnail move=\"1\" scale=\"" + scale + "\" x=\"" + x + "\" y=\"" + y + "\">" + fixBitmap + "</thumbnail>" +
            "   <background init_offset=\"2050\" still=\"1\" fade_in=\"1\">video2</background>" +
            "   </videoevent>" +

            //"   <videoevent name=\"ve\" type=\"overlay\" start=\"1900\" end=\"5000\">" +
            "   <videoevent name=\"ve\" type=\"overlay\" start=\"1900\" end=\"" + (2000 + duration) + "\">" +
            "   <showtime related_start=\"100\" length=\"" + duration + "\"></showtime>" +
            "   <thumbnail scale=\"" + scale + "\" x=\"" + x + "\" y=\"" + y + "\">" + fixBitmap + "</thumbnail>" +
            "   <background init_offset=\"2050\">video2</background>" +
            "   </videoevent>" +

            //"   <videoevent name=\"ve\" type=\"overlay\" start=\"5000\" end=\"5300\">" +
            "   <videoevent name=\"ve\" type=\"overlay\" start=\"" + (2000 + duration) + "\" end=\"" + (2300 + duration) + "\">" +
            "   <showtime related_start=\"0\" length=\"300\"></showtime>" +
            "   <thumbnail fade_out=\"1\" scale=\"" + scale + "\" x=\"" + x + "\" y=\"" + y + "\">" + fixBitmap + "</thumbnail>" +
            "   <background init_offset=\"2050\" still=\"1\">" + fixBitmap + "</background>" +
            "   </videoevent>" +

            //"   <videoevent name=\"ve\" type=\"still\" start=\"5300\" end=\"5301\">" +
            "   <videoevent name=\"ve\" type=\"still\" start=\"" + (2300 + duration) + "\" end=\"" + (2301 + duration) + "\">" +
            "   <background>" + fixBitmap + "</background>" +
            "   </videoevent>" +
            "</scenario>";

        return scenario;
    }
}
