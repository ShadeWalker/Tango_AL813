package com.mediatek.galleryfeature.video;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.LayerManager.IBackwardContoller;
import com.mediatek.galleryframework.gl.BackgroundRenderer;
import com.mediatek.galleryframework.gl.MBasicTexture;
import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.BackgroundRenderer.BackgroundGLTask;
import com.mediatek.galleryframework.util.MtkLog;
// TODO
//import com.mediatek.protect.gallery3d.FrameHandler;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.EGL14;

import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

public class SlideVideoUtils {
    private static final String TAG = "MtkGallery2/SlideVideoUtils";
    private static final int IMAGE_SIZE = 320;

    private static float[] mTransformFromSurfaceTexture = new float[16];
    private static float[] sClearColor = { 1.0f, 0.f, 0.f, 0.f };
    private static EglCore mEglCore;
    private static WindowSurface mCaptureSurface;
    private static ImageReader imageReader = ImageReader.newInstance(/*slotWidth*/IMAGE_SIZE,
            /*slotHeight*/IMAGE_SIZE, PixelFormat.RGBA_8888, 1);
    private static HandlerThread ht = new HandlerThread("save_pause_frame");
    private static Surface mImgRdrSur;

    // for GL context switch
    private static EGLDisplay mSavedEglDisplay     = null;
    private static EGLSurface mSavedEglDrawSurface = null;
    private static EGLSurface mSavedEglReadSurface = null;
    private static EGLContext mSavedEglContext     = null;
    private static EGLContext glContext;
    private static EGLContext glOldContext;

    // for serializing asynchronous operations in saving texture
    private static boolean isBgSavingDone = false;
    private final static Object bgSavingLock = new Object();
    private static boolean isImageListenerDone = false;
    private final static Object imageListenerLock = new Object();

    // thread communicative variables (can be changed into function parameters)
    private static MBasicTexture extTexture = null;
    private static SurfaceTexture texture;
    private static int frameWidth;
    private static int frameHeight;

    private static IBackwardContoller sInBackwardController;
    private static MediaData sInMediaData;

    static {
        ht.start();
        mImgRdrSur = imageReader.getSurface();
    }

    private static void beginDrawSurfaceTexture() {
        saveRenderState();
        imageReader.setOnImageAvailableListener(new ImageListener(SlideVideoUtils.sInMediaData),
                new Handler(ht.getLooper()));
        if (glContext != glOldContext) {
            if (mCaptureSurface != null) {
                mEglCore.makeNothingCurrent();
                mCaptureSurface.releaseEglSurface();
                mCaptureSurface = null;
            }
            if (mEglCore != null) {
                mEglCore.release();
                mEglCore = null;
            }
        }
        if (mCaptureSurface == null) {
            MtkLog.d(TAG, "<beginDrawSurfaceTexture> with context " + SlideVideoUtils.glContext);
            SlideVideoUtils.glOldContext = SlideVideoUtils.glContext;
            mEglCore = new EglCore(SlideVideoUtils.glContext,
                    EglCore.FLAG_TRY_GLES3);
            mCaptureSurface = new WindowSurface(mEglCore, mImgRdrSur);
          }
        mCaptureSurface.makeCurrent();
    }

    private static void endDrawSurfaceTexture() {
        mCaptureSurface.swapBuffers();
        GLUtil.checkGlError("endDrawSurfaceTexture()");
        restoreRenderState();
    }

    private static void saveRenderState() {
        mSavedEglDisplay     = EGL14.eglGetCurrentDisplay();
        mSavedEglDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
        mSavedEglReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
        mSavedEglContext     = EGL14.eglGetCurrentContext();
    }

    private static void restoreRenderState() {
        if (!EGL14.eglMakeCurrent(
                mSavedEglDisplay,
                mSavedEglDrawSurface,
                mSavedEglReadSurface,
                mSavedEglContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    public static void saveSurfaceTexture(final SurfaceTexture surfaceTexture,
            final MBasicTexture extTexture, final int width, final int height,
            final IBackwardContoller backwardController,
            final MediaData mediaData) {
        MtkLog.d(TAG, "<saveSurfaceTexture> begin");
        sInBackwardController = backwardController;
        sInMediaData = mediaData;
        synchronized (bgSavingLock) {
            isBgSavingDone = false;
        }
        BackgroundRenderer.getInstance().addGLTask(new BackgroundGLTask() {
            @Override
            public boolean run(MGLCanvas canvas) {
                MtkLog.d(TAG, "<saveSurfaceTexture> begin in bg thread");
                SlideVideoUtils.texture = surfaceTexture;
                SlideVideoUtils.extTexture = extTexture;
                int w = width;
                int h = height;
                if (w >= h && w > IMAGE_SIZE) {
                    h = h * IMAGE_SIZE / w;
                    h = (h > 0 ? h : 1);
                    w = IMAGE_SIZE;
                } else if (w < h && h > IMAGE_SIZE) {
                    w = w * IMAGE_SIZE / h;
                    w = (w > 0 ? w : 1);
                    h = IMAGE_SIZE;
                }

                saveSurfaceTexture(canvas, w, h);
                synchronized (bgSavingLock) {
                    isBgSavingDone = true;
                    bgSavingLock.notifyAll();
                }
                MtkLog.d(TAG, "<saveSurfaceTexture> end in bg thread");
                return false;
            }
        });
        BackgroundRenderer.getInstance().requestRender();
        synchronized (bgSavingLock) {
            try {
                while (!isBgSavingDone) {
                    bgSavingLock.wait();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        MtkLog.d(TAG, "<saveSurfaceTexture> end");
    }

    private static void saveSurfaceTexture(final MGLCanvas canvas, final int slotWidth, final int slotHeight) {
        synchronized (imageListenerLock) {
            isImageListenerDone = false;
        }
        frameWidth = slotWidth;
        frameHeight = slotHeight;

        beginDrawSurfaceTexture();
        drawSurfaceTexture(canvas, slotWidth, slotHeight);
        endDrawSurfaceTexture();

        synchronized (imageListenerLock) {
            try {
                while (!isImageListenerDone) {
                    imageListenerLock.wait();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void drawSurfaceTexture(MGLCanvas canvas, int slotWidth, int slotHeight) {
            texture
                    .getTransformMatrix(mTransformFromSurfaceTexture);

                canvas.clearBuffer(sClearColor);
                canvas.save(MGLCanvas.SAVE_FLAG_MATRIX);
                canvas.setSize(slotWidth, slotHeight);
                int cx = slotWidth / 2;
                int cy = slotHeight / 2;
                canvas.translate(cx, cy);
                canvas.scale(1, -1, 1);
                canvas.translate(-cx, -cy);
                RectF sourceRect = new RectF(0, 0, slotWidth, slotHeight);
                RectF targetRect = new RectF(0, 0, slotWidth, slotHeight);
                genCononTexCoords(sourceRect, targetRect, extTexture);
                canvas.drawTexture(extTexture,
                        mTransformFromSurfaceTexture, 0, 0, slotWidth,
                        slotHeight);
                canvas.restore();
                return;
    }

    // This function changes the source coordinate to the texture coordinates.
    // It also clips the source and target coordinates if it is beyond the
    // bound of the texture.
    private static void genCononTexCoords(RectF source, RectF target,
            MBasicTexture texture) {

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

    private static class ImageListener implements ImageReader.OnImageAvailableListener {
        private final MediaData mItem;
        public ImageListener(MediaData currentReleaseItem) {
            mItem = currentReleaseItem;
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            MtkLog.d(TAG, "<onImageAvailable> begin");
            Image image = null;
            ByteBuffer imageBuffer = null;
            image = reader.acquireNextImage();
            int frameW = frameWidth;
            int frameH = frameHeight;
            FrameHandler.saveFrameImage(image, frameW, frameH, mItem.filePath);

            synchronized (imageListenerLock) {
                isImageListenerDone = true;
                imageListenerLock.notifyAll();
            }
            sInBackwardController.notifyDataChange(sInMediaData);
            sInBackwardController = null;
            sInMediaData = null;
            MtkLog.d(TAG, "<onImageAvailable> end");
        }
    }

    public static void updateGlContext() {
        SlideVideoUtils.glContext = EGL14.eglGetCurrentContext();
        MtkLog.d(TAG, "<updateGlContext> to " + SlideVideoUtils.glContext);
    }
    public static class RestoreData {
        public static boolean mIsLoop;
        public int mPosition;
        
    }

    private static Map<Uri, RestoreData> sRestoreDatas = new HashMap<Uri, RestoreData>();

    public static void setRestoreData(Uri path, RestoreData data) {
        sRestoreDatas.put(path, data);
    }

    public static RestoreData getRestoreData(Uri path) {
        return sRestoreDatas.get(path);
    }

    public static void clearRestoreDatas() {
        MtkLog.d(TAG, "<clearRestoreDatas>");
        RestoreData.mIsLoop = false;
        sRestoreDatas.clear();
    }
    // TODO
//    public static void onDirectorResume(IVideoDirector.ISecretary param) {
//        pda = param;
//        ((GLRootView) (param.getGallery().getGLRoot()))
//        .setPreserveEGLContextOnPause(true);
//    }
}
