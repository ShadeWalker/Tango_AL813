package com.mediatek.gallery3d.adapter;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.graphics.Bitmap;

import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.app.AlbumPage;
import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.app.MovieControllerOverlay;
import com.android.gallery3d.app.MoviePlayer;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;

import com.mediatek.galleryfeature.SlideVideo.IVideoController;
import com.mediatek.galleryfeature.SlideVideo.IVideoHookerCtl;
import com.mediatek.galleryfeature.SlideVideo.IVideoPlayer;
import com.mediatek.galleryfeature.mav.IconView;
import com.mediatek.galleryfeature.panorama.SwitchBarView;
import com.mediatek.gallery3d.ext.IActivityHooker;
import com.mediatek.gallery3d.util.DecodeSpecLimitor;
import com.mediatek.gallery3d.video.VideoHookerCtrlImpl;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryfeature.platform.Platform;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.galleryframework.base.Work;

public class PlatformImpl implements Platform {
    private static final String TAG = "MtkGallery2/PlatformImpl";

    private ThreadPoolBridge mThreadPoolBridge;

    public PlatformImpl(GalleryAppImpl app) {
        mThreadPoolBridge = new ThreadPoolBridge<Bitmap>(app);
    }

    public boolean isOutOfDecodeSpec(long fileSize, int width, int height,
            String mimeType) {
        return DecodeSpecLimitor.isOutOfSpecLimit(fileSize, width, height, mimeType);
    }

    public void enterContainerPage(Activity activity, MediaData data, boolean getContent,
            Bundle bundleData) {
        if (!(activity instanceof AbstractGalleryActivity)) {
            MtkLog.d(TAG, "<enterContainerPage> fail");
        }
        AbstractGalleryActivity at = (AbstractGalleryActivity) activity;
        Bundle bundle = prepareBundleForContainerPage(at, data, getContent, bundleData);
        GLRoot root = at.getGLRoot();
        root.lockRenderThread();
        try {
            at.getStateManager().startState(ContainerPage.class, bundle);
        } finally {
            root.unlockRenderThread();
        }
    }

    public void switchToContainerPage(Activity activity, MediaData data, boolean getContent,
            Bundle bundleData) {
        if (!(activity instanceof AbstractGalleryActivity)) {
            MtkLog.d(TAG, "<switchToContainerPage> fail");
        }
        AbstractGalleryActivity at = (AbstractGalleryActivity) activity;
        Bundle bundle = prepareBundleForContainerPage(at, data, getContent, bundleData);
        GLRoot root = at.getGLRoot();
        root.lockRenderThread();
        try {
            at.getStateManager().switchState(at.getStateManager().getTopState(),
                    ContainerPage.class, bundle);
        } finally {
            root.unlockRenderThread();
        }
    }

    private Bundle prepareBundleForContainerPage(AbstractGalleryActivity activity, MediaData data,
            boolean getContent, Bundle bundleData) {
        Bundle bundle;
        if (bundleData != null) {
            bundle = new Bundle(bundleData);
        } else {
            bundle = new Bundle();
        }
        MediaSet set = FeatureHelper.getContainerSet(activity, data);
        bundle.putString(AlbumPage.KEY_MEDIA_PATH, set.getPath().toString());
        bundle.putBoolean(GalleryActivity.KEY_GET_CONTENT, getContent);
        if (data.subType == MediaData.SubType.MOTRACK) {
            bundle.putBoolean(ContainerPage.KEY_IN_MOTRACK_MODE, true);
        }
        return bundle;
    }

    public SwitchBarView createPanoramaSwitchBarView(Activity activity) {
        return new PanoramaSwitchBarView(activity);
    }

    public IconView createMavIconView(Context context) {
        return new MavIconView(context);
    }

    public IVideoPlayer createSVExtension(Context context, MediaData data) {
        MoviePlayer player = new MoviePlayer(context, data);
        IVideoPlayer videoPlayer = player.mSlideVideoExt;
        return player.mSlideVideoExt;
    }

    public IVideoController createController(Context context) {
        MovieControllerOverlay controllerOverlay = MovieControllerOverlay
                .getMovieController(context);
        MtkLog.d(TAG, "<createController> controllerOverlay is " + controllerOverlay);
        return controllerOverlay;
    }

    public IVideoHookerCtl createHooker(Activity activity, IActivityHooker rewindAndForwardHooker) {
        MtkLog.d(TAG, "<createHooker> ");
        return VideoHookerCtrlImpl.getVideoHookerCtrlImpl(activity, rewindAndForwardHooker);
    }

    public void submitJob(Work work) {
        mThreadPoolBridge.submit(work);
    }

    private static class ThreadPoolBridge<T> {
        private ThreadPool mThreadPool;

        public ThreadPoolBridge(GalleryAppImpl app) {
            mThreadPool = app.getThreadPool();
            MtkLog.d(TAG, "<ThreadPoolBridge> mThreadPool " + mThreadPool);
        }

        public void submit(Work work) {
            mThreadPool.submit(new BridgeJob(work));
        }

        private class BridgeJob implements Job<T> {
            private Work mWork;

            public BridgeJob(Work work) {
                mWork = work;
            }

            @Override
            public T run(JobContext jc) {
                if (mWork != null && !mWork.isCanceled())
                    return (T) (mWork.run());
                return null;
            }
        }
    }
}