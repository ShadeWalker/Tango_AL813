package com.mediatek.galleryfeature.video;

import java.io.IOException;

import com.mediatek.gallery3d.util.TraceHelper;
import com.mediatek.galleryframework.base.Generator;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.gl.GLIdleExecuter;
import com.mediatek.galleryframework.gl.GLIdleExecuter.GLIdleCmd;
import com.mediatek.galleryframework.gl.MExtTexture;
import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.MTexture;
import com.mediatek.galleryframework.util.MtkLog;

import android.content.Context;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.opengl.GLES11Ext;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

public class ThumbnailVideoPlayer extends Player {
    private static final String TAG = "MtkGallery2/VideoPlayer";

    // Key and value for enable ClearMotion
    private static final int CLEAR_MOTION_KEY = 1700;
    private static final int CLEAR_MOTION_DISABLE = 1;

    private static final int TEXTURE_STATE_UNINITIALIZED = 0;
    private static final int TEXTURE_STATE_WORKING = 1;
    private static final int TEXTURE_STATE_RECYCLED = 2;
    private final Object textureStateObject = new Object();
    private int mTextureState = TEXTURE_STATE_UNINITIALIZED;

    private String mPlayPath;
    private ThumbType mThumbType;
    private MediaPlayer mMediaPlayer;
    private Surface mSurface;
    private GLIdleExecuter mGLIdleExecuter;
    private Generator mGenerator;

    public void setGenerator(Generator generator) {
        mGenerator = generator;
    }

    private final VideoThumbnail mRenderTarget = new VideoThumbnail() {
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            if (mRenderTarget.isWorking) {
                synchronized (mRenderTarget) {
                    mHasNewFrame = true;
                }
                if (mGLIdleExecuter != null) {
                    mGLIdleExecuter.addOnGLIdleCmd(new GLIdleCmd() {
                        public boolean onGLIdle(MGLCanvas canvas) {
                            mRenderTarget.onGLIdle(canvas, false);
                            return false;
                        }
                    });
                }
                sendFrameAvailable();
            }
        }
    };

    public ThumbnailVideoPlayer(Context context, MediaData data,
            OutputType outputType, ThumbType thumbType) {
        super(context, data, outputType);
        mThumbType = thumbType;
    }

    public void setGLIdleExecuter(GLIdleExecuter exe) {
        mGLIdleExecuter = exe;
    }

    public MTexture getTexture(MGLCanvas canvas) {
        mRenderTarget.mBornRenderingContextId = canvas.hashCode();
        synchronized (textureStateObject) {
            if (mTextureState == TEXTURE_STATE_UNINITIALIZED && mMediaPlayer != null) {
                mRenderTarget.acquireSurfaceTexture(canvas);
                SurfaceTexture texture = mRenderTarget.getSurfaceTexture();
                mSurface = new Surface(texture);
                new Thread(new Runnable() {
                    public void run() {
                        synchronized (textureStateObject) {
                            if (mTextureState != TEXTURE_STATE_WORKING) {
                                return;
                            }
                        }
                        try {
                            mMediaPlayer.setSurface(mSurface);
                        } catch (IllegalStateException e) {
                            MtkLog.v(TAG, "set surface after releasing. ignore it");
                        }
                    }
                }).start();
                mTextureState = TEXTURE_STATE_WORKING;
            }
        }
        return mRenderTarget.getFrameTexture(canvas);
    }

    // Generator cancel operation would be carried out by GeneratorCoordinator
    // @Override
    // public void onCancel() {
    //     if (mGenerator != null) {
    //         mGenerator.onCancelRequested(mMediaData, 0);
    //     }
    // }

    public boolean onPrepare() {
        // here mThumbType is in effect useless
        if ((ThumbType.MICRO == mThumbType) && (mGenerator != null)) {
            TraceHelper.traceBegin(">>>>VideoPlayer-generateVideo");
            mPlayPath = mGenerator.generateVideo(mMediaData,
                    Generator.VTYPE_THUMB);
            TraceHelper.traceEnd();
            if (mPlayPath == null) {
                return false;
            }
        } else {
            mPlayPath = mMediaData.filePath;
        }

        mMediaPlayer = new MediaPlayer();
        // after transcoding, in order to call getTexture(), notify GL to render
        sendFrameAvailable();
        mMediaPlayer.setOnVideoSizeChangedListener(mRenderTarget);
        mMediaPlayer.setLooping(true);
        mMediaPlayer.setVolume(0, 0);
        mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            public boolean onError(MediaPlayer mp, int what, int extra) {
                MtkLog.e(TAG, "error happened in video thumbnail's internal player. \n\tmay triggered by video deletion");
                MtkLog.d(TAG, "----- onError: " + mMediaData.filePath
                        + what + "," + extra);
                mRenderTarget.isWorking = false;
                mRenderTarget.isReadyForRender = false;
                return false;
            }
        });

        try {
            mMediaPlayer.reset();
            mMediaPlayer.setLooping(true);

            try {
                long debug_t = System.currentTimeMillis();
                mMediaPlayer.setDataSource(mPlayPath);
                MtkLog.d(TAG, mMediaData.caption + " setDataSource() costs: "
                        + (System.currentTimeMillis() - debug_t));

                // Disable ClearMotion
                mMediaPlayer.setParameter(CLEAR_MOTION_KEY, CLEAR_MOTION_DISABLE);

                debug_t = System.currentTimeMillis();
                mMediaPlayer.prepare();
                MtkLog.d(TAG, mMediaData.caption + " prepare() costs: "
                        + (System.currentTimeMillis() - debug_t));

                int duration = mMediaPlayer.getDuration();
                // TODO add condition: thumb type is micro if engine can distinguish thumb type
                if (duration < VideoConfig.VIDEO_DURATION_THRESHOLD) {
                    MtkLog.d(TAG, "----- duration = " + duration + ", give up playing "
                            + mMediaData.caption);
                    release();
                    return false;
                }
            } catch (IOException e) {
                handleException(e);
                return false;
            } catch (IllegalArgumentException e) {
                handleException(e);
                return false;
            } catch (SecurityException e) {
                handleException(e);
                return false;
            } catch (IllegalStateException e) {
                handleException(e);
                return false;
            }

            mRenderTarget.isWorking = true;
        } catch (IllegalStateException e) {
            MtkLog.v(TAG, "thumbnail is released by pausing, give up openning");
            return false;
        }
        return true;
    }

    private void handleException(Exception e) {
        e.printStackTrace();
        release();
    }

    private volatile boolean canReleaseMediaPlayer;
    public void onRelease() {
        mRenderTarget.isWorking = false;
        mRenderTarget.isReadyForRender = false;
        long debug_t;
        try {
            if (mMediaPlayer.isPlaying()) {
                debug_t = System.currentTimeMillis();
                mMediaPlayer.stop();
                MtkLog.d(TAG, mMediaData.caption + " stop() costs: "
                        + (System.currentTimeMillis() - debug_t));
            }
        } catch (IllegalStateException e) {
            MtkLog.v(TAG, "thumbnail is released by pausing, give up recycling once again");
        }

        boolean needTextureRelease = false;
        synchronized (textureStateObject) {
            if (mTextureState == TEXTURE_STATE_WORKING) {
                needTextureRelease = true;
            }
            mTextureState = TEXTURE_STATE_RECYCLED;
        }

        final Object releaseBarrier = new Object();
        canReleaseMediaPlayer = false;
        MtkLog.d(TAG, mMediaData.caption + "post to setOnVideoSizeChangedListener(null)");
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (releaseBarrier) {
                    mMediaPlayer.setOnVideoSizeChangedListener(null);
                    MtkLog.d(TAG, mMediaData.caption + "do setOnVideoSizeChangedListener(null)");
                    canReleaseMediaPlayer = true;
                    releaseBarrier.notifyAll();
                }
            }
        });
        synchronized (releaseBarrier) {
            while (!canReleaseMediaPlayer) {
                try {
                    releaseBarrier.wait();
                } catch (InterruptedException e) {
                    MtkLog.d(TAG, "<onRelease> interrupted when releaseBarrier.wait()");
                }
            }
        }
        MtkLog.d(TAG, mMediaData.caption + "begin release");
        debug_t = System.currentTimeMillis();
        mMediaPlayer.release();
        MtkLog.d(TAG, mMediaData.caption + " release() costs: "
                + (System.currentTimeMillis() - debug_t));
        if (needTextureRelease) {
            mSurface.release();
            mRenderTarget.releaseSurfaceTexture();
        }
    }

    public boolean onStart() {
        mMediaPlayer.start();
        return true;
    }

    public boolean onPause() {
        mMediaPlayer.pause();
        return true;
    }

    public boolean onStop() {
        // cause no defect found by forgetting calling this guy for IT period, we skip it
        // mediaPlayer.stop();
        return true;
    }

    public static abstract class VideoThumbnail implements
            SurfaceTexture.OnFrameAvailableListener,
            MediaPlayer.OnVideoSizeChangedListener {
        private class VideoFrameTexture extends MExtTexture {
            public VideoFrameTexture(MGLCanvas canvas, int target) {
                super(canvas, target, true);
            }

            public void setSize(int width, int height) {
                super.setSize(width, height);
            }

            public void draw(MGLCanvas canvas, int x, int y, int width, int height) {
                VideoThumbnail.this.draw(canvas, width, height);
            }
        }

        static final int TEXTURE_HEIGHT = 128;
        static final int TEXTURE_WIDTH = 128;

        public int mBornRenderingContextId;
        private VideoFrameTexture mVideoFrameTexture;
        private SurfaceTexture mSurfaceTexture;
        private int mWidth = TEXTURE_WIDTH;
        private int mHeight = TEXTURE_HEIGHT;
        private float[] mTransformFromSurfaceTexture = new float[16];
        private float[] mTransformForCropingCenter = new float[16];
        private float[] mTransformFinal = new float[16];
        // TODO abstract following into states (and maybe merged into player's texture states)
        private boolean mHasTexture = false;
        protected boolean mHasNewFrame = false;
        protected boolean isReadyForRender = false;
        public volatile boolean isWorking = false;

        public VideoFrameTexture getFrameTexture(MGLCanvas canvas) {
            synchronized (this) {
                if (!isReadyForRender || !mHasTexture || !isWorking) {
                    return null;
                }
            }
            return mVideoFrameTexture;
        }

        public void acquireSurfaceTexture(MGLCanvas canvas) {
            mVideoFrameTexture = new VideoFrameTexture(canvas,
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
            mVideoFrameTexture.setSize(TEXTURE_WIDTH, TEXTURE_HEIGHT);
            mSurfaceTexture = new SurfaceTexture(mVideoFrameTexture.getId());
            setDefaultBufferSize(mSurfaceTexture, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            mSurfaceTexture.setOnFrameAvailableListener(this);
            synchronized (this) {
                mHasTexture = true;
            }
        }

        private static void setDefaultBufferSize(SurfaceTexture st, int width,
                int height) {
            st.setDefaultBufferSize(width, height);
        }

        private static void releaseSurfaceTexture(SurfaceTexture st) {
            // The thread calling this function may not the same thread as create SurfaceTexture,
            // and if setOnFrameAvailableListener as null, SurfaceTexture may throw
            // nullpointer exception
            // st.setOnFrameAvailableListener(null);
            st.release();
        }

        public SurfaceTexture getSurfaceTexture() {
            return mSurfaceTexture;
        }

        public void releaseSurfaceTexture() {
            synchronized (this) {
                if (!mHasTexture)
                    return;
                mHasTexture = false;
            }
            mVideoFrameTexture.recycle();
            mVideoFrameTexture = null;
            releaseSurfaceTexture(mSurfaceTexture);
            mSurfaceTexture = null;
        }

        public void draw(MGLCanvas canvas, int slotWidth, int slotHeight) {
            synchronized (this) {
                if (!isReadyForRender || !mHasTexture || !isWorking) {
                    return;
                }

                mSurfaceTexture
                        .getTransformMatrix(mTransformFromSurfaceTexture);

                // Flip vertically.
                canvas.save(MGLCanvas.SAVE_FLAG_MATRIX);
                int cx = slotWidth / 2;
                int cy = slotHeight / 2;
                canvas.translate(cx, cy);
                canvas.scale(1, -1, 1);
                canvas.translate(-cx, -cy);
                float longSideStart;
                float longSideEnd;
                RectF sourceRect;
                RectF targetRect = new RectF(0, 0, slotWidth, slotHeight);
                // To reduce computing complexity, the following logic based on
                // the fact that slotWidth = slotHeight. Strictly, the condition should be
                // if ((float)mWidth / mHeight > (float)slotWidth / slotHeight)

                /// M: [FEATURE.MODIFY] fancy layout @{
                /*
                {
                    if (mWidth > mHeight) {
                        longSideStart = (mWidth - mHeight) * TEXTURE_WIDTH
                                / (float) ((mWidth * 2));
                        longSideEnd = TEXTURE_WIDTH - longSideStart;
                        sourceRect = new RectF(longSideStart, 0, longSideEnd,
                                TEXTURE_HEIGHT);
                    } else {
                        longSideStart = (mHeight - mWidth) * TEXTURE_HEIGHT
                                / (float) ((mHeight * 2));
                        longSideEnd = TEXTURE_HEIGHT - longSideStart;
                        sourceRect = new RectF(0, longSideStart, TEXTURE_WIDTH,
                                longSideEnd);
                    }
                }
                */
                // can not import FancyHelper, add flag temporarily
                boolean enableFancy = true; //FancyHelper.isFancyLayoutSupported()
                if (enableFancy) {
                    float mwXsh = mWidth * slotHeight;
                    float swXmh = slotWidth * mHeight;
                    if (mwXsh > swXmh) {
                        longSideStart = (TEXTURE_WIDTH >> 1) * (1 - swXmh / mwXsh);
                        longSideEnd = TEXTURE_WIDTH - longSideStart;
                        sourceRect = new RectF(longSideStart, 0, longSideEnd,
                                TEXTURE_HEIGHT);
                    } else {
                        longSideStart = (TEXTURE_HEIGHT >> 1) * (1 - mwXsh / swXmh);
                        longSideEnd = TEXTURE_HEIGHT - longSideStart;
                        sourceRect = new RectF(0, longSideStart, TEXTURE_WIDTH,
                                longSideEnd);
                    }
                } else {
                    if (mWidth > mHeight) {
                        longSideStart = (mWidth - mHeight) * TEXTURE_WIDTH
                                / (float) ((mWidth * 2));
                        longSideEnd = TEXTURE_WIDTH - longSideStart;
                        sourceRect = new RectF(longSideStart, 0, longSideEnd,
                                TEXTURE_HEIGHT);
                    } else {
                        longSideStart = (mHeight - mWidth) * TEXTURE_HEIGHT
                                / (float) ((mHeight * 2));
                        longSideEnd = TEXTURE_HEIGHT - longSideStart;
                        sourceRect = new RectF(0, longSideStart, TEXTURE_WIDTH,
                                longSideEnd);
                    }
                }
                /// @}
                genCononTexCoords(sourceRect, targetRect, mVideoFrameTexture);
                genExtTexMatForSubTile(sourceRect);
                Matrix.multiplyMM(mTransformFinal, 0,
                        mTransformFromSurfaceTexture, 0,
                        mTransformForCropingCenter, 0);
                canvas.drawTexture(mVideoFrameTexture, mTransformFinal,
                        (int) targetRect.left, (int) targetRect.top,
                        (int) targetRect.width(), (int) targetRect.height());
                canvas.restore();
            }
        }

        abstract public void onFrameAvailable(SurfaceTexture surfaceTexture);

        public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
            mWidth = width;
            mHeight = height;
        }

        // TODO this would be called in GL idle later, so keep its name
        public boolean onGLIdle(MGLCanvas canvas, boolean renderRequested) {
            int currentRenderingContextId = canvas.hashCode();
            if (mBornRenderingContextId != currentRenderingContextId) {
                MtkLog.d(TAG, "<VideoThumbnail.onGLIdle> rendering context id changed from "
                        + mBornRenderingContextId + " to "
                        + currentRenderingContextId);
                return false;
            }
            
            synchronized (this) {
                if (isWorking && mHasTexture && mHasNewFrame) {
                    if (mSurfaceTexture != null) {
                        try {
                            mSurfaceTexture.updateTexImage();
                        } catch (IllegalStateException e) {
                            MtkLog.v(TAG, "notify author that mSurfaceTexture in thumbnail released when updating tex img");
                            return false;
                        }
                    }
                    mHasNewFrame = false;
                    isReadyForRender = true;
                }
            }
            return false;
        }

        // This function changes the source coordinate to the texture coordinates.
        // It also clips the source and target coordinates if it is beyond the
        // bound of the texture.
        private static void genCononTexCoords(RectF source, RectF target,
                VideoFrameTexture texture) {

            int width = texture.getWidth();
            int height = texture.getHeight();
            int texWidth = texture.getTextureWidth();
            int texHeight = texture.getTextureHeight();
            // Convert to texture coordinates
            source.left /= texWidth;
            source.right /= texWidth;
            source.top /= texHeight;
            source.bottom /= texHeight;

            // Clip if the rendering range is beyond the bound of the texture.
            float xBound = (float) width / texWidth;
            if (source.right > xBound) {
                target.right = target.left + target.width()
                        * (xBound - source.left) / source.width();
                source.right = xBound;
            }
            float yBound = (float) height / texHeight;
            if (source.bottom > yBound) {
                target.bottom = target.top + target.height()
                        * (yBound - source.top) / source.height();
                source.bottom = yBound;
            }
        }

        private void genExtTexMatForSubTile(RectF subRange) {
            mTransformForCropingCenter[0] = subRange.right - subRange.left;
            mTransformForCropingCenter[5] = subRange.bottom - subRange.top;
            mTransformForCropingCenter[10] = 1;
            mTransformForCropingCenter[12] = subRange.left;
            mTransformForCropingCenter[13] = subRange.top;
            mTransformForCropingCenter[15] = 1;
        }
    }
}