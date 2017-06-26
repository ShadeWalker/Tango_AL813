package com.mediatek.galleryfeature.mav;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.base.Player.OutputType;
import com.mediatek.galleryframework.gl.GLIdleExecuter;
import com.mediatek.galleryframework.gl.MBitmapTexture;
import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.MTexture;
import com.mediatek.galleryframework.gl.MUploadedTexture;
import com.mediatek.galleryframework.gl.GLIdleExecuter.GLIdleCmd;
import com.mediatek.galleryfeature.drm.DrmHelper;
import com.mediatek.galleryfeature.mav.RenderThreadEx.OnDrawMavFrameListener;

import com.mediatek.galleryframework.util.MtkLog;

public class MavPlayer extends Player implements OnDrawMavFrameListener {
    private static String TAG = "MtkGallery2/MavPlayer";
    private ThumbType mType;

    public AnimationEx mAnimation = null;
    public static final int STATE_UNLOADED = 0;
    public static final int STATE_QUEUED = 1;
    public static final int STATE_LOADING_MARK_FRAME = 2;

    public static final int STATE_LOADED_MARK_FRAME = 3;
    public static final int STATE_LOADING_ALL_FRAME = 4;
    public static final int STATE_LOADED_ALL_FRAME = 5;

    public static final int STATE_RELEASE_ALL_FRAME = 6;
    public static final int STATE_RELEASE_MARK_FRAME = 7;

    public static final int STATE_ERROR_ALL_FRAME = 8;
    public static final int STATE_ERROR_MARK_FRAME = 9;

    public static final int STATE_NO_MARK_FRAME = -1;

    private int mState = STATE_UNLOADED;
    public MavListener mMavListener = null;
    public static int REAL_RESOLUTION_MAX_SIZE = 0;
    public static final int MAV_THUMBNAIL_SIZE = 256;

    public static final long MEMORY_THRESHOLD_MAV_L1 = 100 * 1024 * 1024;
    public static final long MEMORY_THRESHOLD_MAV_L2 = 50 * 1024 * 1024;
    private MavTexture mTexture;
    private MavTexture[] mTextures;
    private Bitmap[] mBitmaps = null;
    private int mMarkFrameIndex;
    private int mFrameCount = 0;
    private int mWidth = 0;
    private int mHeight = 0;
    public static RenderThreadEx sRenderThreadEx = null;
    private MpoDecoderWrapper mpoDecoderWrapper = null;
    private static final Object mLock = new Object();
    private static int number = 0;
    public static final int MSG_START = 1;
    public static final int MSG_STOP = 2;
    public static final int MSG_UPDATE_CURRENT_FRAME = 3;
    public static final int MSG_UPDATE_MAV_PROGRESS = 4;
    public static final int MSG_UPDATE_MAV_SET_STATUS = 6;
    public static final int MSG_UPDATE_MAV_SET_SEEKBAR = 7;
    private GLIdleExecuter mGLExecuter;

    public static ConcurrentHashMap<MavPlayer, Object> sHashSet = new ConcurrentHashMap<MavPlayer, Object>();
    public interface MavListener {
        public void setSeekBar(int max, int progress);

        public void setStatus(boolean isEnable);

        public void setProgress(int progress);
    }

    public static final class Config {
        private static int SLEEP_TIME_INTERVAL = 20;

        private static int getAnimationType(ThumbType type) {
            int animationType = AnimationEx.TYPE_ANIMATION_CONTINUOUS;
            switch (type) {
            case MICRO:
            case MIDDLE:
                animationType = AnimationEx.TYPE_ANIMATION_CONTINUOUS;
                break;
            default:
                throw new RuntimeException("getAnimationType for type=" + type);
            }
            return animationType;
        }

        public static int getIntervalTime() {
            return SLEEP_TIME_INTERVAL;
        }
    }

    public static class Params {

        // tell the type fo thumbnail to be retrieved
        public ThumbType inType;

        // tell how the thumbnail should be scaled
        public int inThumbnailTargetSize;

        // added for picture quality enhancement, indicating whether image
        // is enhanced
        public boolean inPQEnhance;

        // the two variables tells how large the decoded image will be
        // displayed on the screen. Currently, this variable is used only
        // for mpo.
        public int inTargetDisplayWidth;
        public int inTargetDisplayHeight;

        public Params() {
            inType = ThumbType.MICRO;
            inThumbnailTargetSize = 0;
            inPQEnhance = false;
            inTargetDisplayWidth = 0;
            inTargetDisplayHeight = 0;
        }
    }

    public MavPlayer(Context context, MediaData data, OutputType outputType,
            ThumbType thumbType ,GLIdleExecuter glExecuter) {
        super(context, data, outputType);
        MtkLog.d(TAG, " <MavPlayer> data=" + data.filePath + " thumbType="
                + thumbType + " " + this);
        mType = thumbType;
        mState = STATE_UNLOADED;
        if (REAL_RESOLUTION_MAX_SIZE <= 0) {
            DisplayMetrics reMetrics = new DisplayMetrics();
            ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay().getRealMetrics(reMetrics);
            MtkLog.d(TAG, "<MavPlayer>reMetrics.widthPixels=="
                    + reMetrics.widthPixels + " reMetrics.heightPixels="
                    + reMetrics.heightPixels);
            REAL_RESOLUTION_MAX_SIZE = Math.max(reMetrics.widthPixels,
                    reMetrics.heightPixels);
        }
        mWidth = mMediaData.width;
        mHeight = mMediaData.height;
        mGLExecuter = glExecuter;
    }

    @Override
    public boolean onPause() {
        return true;
    }

    @Override
    public boolean onPrepare() {
        mMavListener = new MavListener() {
            public void setStatus(boolean isEnable) {
                sendNotify(MSG_UPDATE_MAV_SET_STATUS, -1,
                        isEnable);
            }

            public void setSeekBar(int max, int progress) {
                boolean isFinishedLoading = progress > 0 ? true : false;
                sendNotify(MSG_UPDATE_MAV_SET_SEEKBAR, max, isFinishedLoading);
            }

            public void setProgress(int progress) {
                sendNotify(MSG_UPDATE_MAV_PROGRESS, progress,
                        null);
            }
        };
        return true;
    }

    @Override
    public void onRelease() {

    }

    @Override
    public boolean onStart() {
        MtkLog.d("TAG", "onStart number="+(++number));
        if (mType == ThumbType.MIDDLE) {
            sendNotify(MSG_START, 0, null);
        }
        synchronized (mLock) {
            sHashSet.put(this, "");
            if (null == sRenderThreadEx) {
                sRenderThreadEx = new RenderThreadEx(mContext,
                        new GyroSensorEx(mContext));
                sRenderThreadEx.start();
            }
            sRenderThreadEx.setOnDrawMavFrameListener(this);
        }
        Params mParams = new Params();
        initParams(mParams);
        if (mMediaData.isDRM != 0) {
            byte[] buffer = DrmHelper.forceDecryptFile(mMediaData.filePath,
                    false);
            mpoDecoderWrapper = MpoDecoderWrapper.createMpoDecoderWrapper(buffer);
        } else {
            mpoDecoderWrapper = MpoDecoderWrapper.createMpoDecoderWrapper(mMediaData.filePath);
        }
        if (mpoDecoderWrapper == null) {
            MtkLog.i(TAG, "<onStart> mpoDecoderWrapper == null, return false, file path = "
                            + mMediaData.filePath);
            return false;
        }
        mWidth = mpoDecoderWrapper.width();
        mHeight = mpoDecoderWrapper.height();
        mMarkFrameIndex = mpoDecoderWrapper.frameCount()/2;
        mFrameCount = mpoDecoderWrapper.frameCount();
        mBitmaps = new Bitmap[mFrameCount];
        mTextures = new MavTexture[mFrameCount];
        try {
            MtkLog.d(TAG, "<onStart>mMavListener=" + mMavListener + " " + this);
            mBitmaps = MpoHelper.decodeMpoFrames(mTaskCanceller, mParams,
                    mpoDecoderWrapper, mType == ThumbType.MIDDLE ? mMavListener : null);
            if (mBitmaps != null) {
                for (int i = 0; i < mFrameCount; i++) {
                    if (mBitmaps[i] != null) {
                        mTextures[i] = new MavTexture(mBitmaps[i]);
                    }
                }
            }
        } finally {
            // we must close mpo wrapper manually.
            if (null != mpoDecoderWrapper) {
                mpoDecoderWrapper.close();
            }
        }
        if (sRenderThreadEx != null) {
            sRenderThreadEx.setRenderRequester(true);
        }
        mState = STATE_LOADED_ALL_FRAME;
        return true;
    }

    @Override
    public boolean onStop() {
        if (mType == ThumbType.MIDDLE) {
            sendNotify(MSG_STOP, 0, null);
        }
        synchronized (mLock) {
            sHashSet.remove(this);
            if (sHashSet.size() == 0) {
                sRenderThreadEx.setRenderRequester(false);
                sRenderThreadEx.quit();
                sRenderThreadEx = null;
            }
        }
        mState = STATE_RELEASE_ALL_FRAME;
        mTexture = null;
        if (mGLExecuter != null) {
            mGLExecuter.addOnGLIdleCmd(new GLIdleCmd() {
                public boolean onGLIdle(MGLCanvas canvas) {
                    MtkLog.v(TAG, "<GLIdleCmd> prepare onGLIdle  mState = "+mState);
                    if (mState == STATE_RELEASE_ALL_FRAME) {
                        freeResource();
                        MtkLog.v(TAG, "<GLIdleCmd> finish freeResouce mState = "+mState);
                        return false;
                    } else {
                        return true;
                    }
                }
            });
        } else {
            freeResource();
        }
        return true;
    }

    private void freeResource() {
        MtkLog.d(TAG, "<freeResource> freeResouce  mGLExecuter = "+mGLExecuter);
        if (mBitmaps != null) {
            int length = mBitmaps.length;
            for (int i = 0; i < length; i++) {
                Bitmap bitmap = mBitmaps[i];
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
            mBitmaps = null;
        }
        
        if (mTextures != null) {
            int length = mTextures.length;
            for (int i = 0; i < length; i++) {
                MavTexture texture = mTextures[i];
                if (texture != null ) {
                    texture.recycle();
                }
            }
            mTextures = null;
        }
    }
    
    public int getOutputWidth() {
        if (mTexture != null)
            return mWidth;
        return 0;
    }

    public int getOutputHeight() {
        if (mTexture != null)
            return mHeight;
        return 0;
    }

    public boolean setCurrentFrame() {
        if (mTextures != null && mAnimation != null) {
            MavTexture texture = mTextures[mAnimation.getCurrentFrame()];
            if (texture != null && texture != mTexture) {
                mTexture = texture;
                return true;
            }
        }
        return false;
    }

    public void setMavListener(MavListener mavListener) {
        mMavListener = mavListener;
    }

    private void initParams(Params params) {
        params.inType = mType;
        if (mType == ThumbType.MICRO) {
            params.inThumbnailTargetSize = mType.getTargetSize() > MAV_THUMBNAIL_SIZE ? MAV_THUMBNAIL_SIZE
                    : mType.getTargetSize();
            return;
        } 
        // get windows size
        Display defaultDisplay = ((Activity) mContext).getWindowManager()
                .getDefaultDisplay();
        int displaywidth = defaultDisplay.getWidth();
        int displayHeight = defaultDisplay.getHeight();
        int useMemoryForFullDisplay = 100 * (displaywidth * displayHeight);

        int parameter = (useMemoryForFullDisplay > MEMORY_THRESHOLD_MAV_L1) ? 2 : 1;
        long availableMemory = availableMemoryForMavPlayback(mContext);
        if (availableMemory > MEMORY_THRESHOLD_MAV_L1) {
            params.inTargetDisplayHeight = displayHeight / parameter;
            params.inTargetDisplayWidth = displaywidth / parameter;
        } else if (availableMemory <= MEMORY_THRESHOLD_MAV_L1
                && availableMemory > MEMORY_THRESHOLD_MAV_L2) {
            MtkLog.d(TAG, "<getTargetSize>no enough memory, degrade sample rate to 1/2 of parameter");
            params.inTargetDisplayHeight = displayHeight / (parameter * 2);
            params.inTargetDisplayWidth = displaywidth / (parameter * 2);
        } else if (availableMemory <= MEMORY_THRESHOLD_MAV_L2) {
            MtkLog.d(TAG, "<getTargetSize>no enough memory, degrade sample rate to 1/4 of parameter");
            params.inTargetDisplayHeight = displayHeight / (parameter * 4);
            params.inTargetDisplayWidth = displaywidth / (parameter * 4);
        }
        params.inPQEnhance = true;
    }

    private static long availableMemoryForMavPlayback(Context mContext) {
        ActivityManager am = (ActivityManager) (((Activity) mContext)
                .getSystemService(Context.ACTIVITY_SERVICE));
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long availableMemory = mi.availMem;
        MtkLog.d(TAG,
                "<availableMemoryForMavPlayback>current available memory: "
                        + availableMemory);
        return availableMemory;
    }

    public MTexture getTexture(MGLCanvas canvas) {
        if (mState == STATE_LOADED_ALL_FRAME && mTexture != null && mTexture.onBind(canvas)) {
            return (MTexture) mTexture; 
        } else {
            return null;
        }
    }

    public int getCurrentState() {
        return mState;
    }

    public int getFrameCount() {
        return mFrameCount;
    }

    public AnimationEx createAnimation() {
        int endframe;
        if (Config.getAnimationType(mType) == AnimationEx.TYPE_ANIMATION_PLAYBACK) {
            endframe = mMarkFrameIndex > mFrameCount / 2 ? 0
                    : mFrameCount - 1;
        } else {
            endframe = mMarkFrameIndex;
        }
        return new AnimationEx(mFrameCount, mMarkFrameIndex, endframe,
                Config.getAnimationType(mType), Config.getIntervalTime());
    }

    public void draw() {
        boolean requestrender = false;
        MavPlayer player = null;
        Iterator<Entry<MavPlayer, Object>> it = sHashSet.entrySet().iterator();
        int currentFrame = -1;
        while (it != null && it.hasNext()) {
            player = it.next().getKey();
            if (player.getCurrentState() == STATE_LOADED_ALL_FRAME
                    && player.mAnimation != null) {
                requestrender |= player.setCurrentFrame();
                currentFrame = player.mAnimation.getCurrentFrame();
            }
        }
        if (requestrender) {
            if (mType == ThumbType.MIDDLE) {
                sendNotify(MSG_UPDATE_MAV_PROGRESS, currentFrame, null);
            }
            sendFrameAvailable();
        }
    }

    public boolean advanceAnimation(int targetFrame, int type) {
        boolean isfinished = true;
        Iterator<Entry<MavPlayer, Object>> it = sHashSet.entrySet().iterator();
        while (it != null && it.hasNext()) {
            MavPlayer player = it.next().getKey();
            if (player.mAnimation == null
                    && player.getCurrentState() == STATE_LOADED_ALL_FRAME) {
                player.mAnimation = player.createAnimation();
                isfinished &= false;
            } else if (player.getCurrentState() == STATE_LOADED_ALL_FRAME) {
                isfinished &= player.mAnimation.advanceAnimation();
            }
        }
        return isfinished;
    }

    public void changeAnimationType() {

    }

    public void drawMavFrame() {
        draw();
    }

    public int getSleepTime() {
        return Config.SLEEP_TIME_INTERVAL;
    }

    public void initAnimation(int targetFrame, int type) {

        Iterator<Entry<MavPlayer, Object>> it = sHashSet.entrySet().iterator();
        while (it != null && it.hasNext()) {
            Entry<MavPlayer, Object> player = it.next();
            if (player.getKey().getCurrentState() == STATE_LOADED_ALL_FRAME
                    && player.getKey().mAnimation != null) {
                player.getKey().mAnimation.initAnimation(targetFrame, type);
            }
        }
        if (sRenderThreadEx != null) {
            sRenderThreadEx.setRenderRequester(true);
        }
    }

}
