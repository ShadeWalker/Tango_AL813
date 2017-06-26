package com.mediatek.gallery3d.video;

import android.content.Context;

import com.android.gallery3d.app.MovieActivity;

import com.mediatek.drm.OmaDrmClient;
import com.mediatek.gallery3d.ext.IActivityHooker;
import com.mediatek.gallery3d.ext.IMovieExtension;
import com.mediatek.gallery3d.ext.IRewindAndForwardExtension;
import com.mediatek.gallery3d.ext.IServerTimeoutExtension;
import com.mediatek.gallery3d.ext.DefaultMovieExtension;
import com.mediatek.gallery3d.ext.DefaultRewindAndForwardExtension;
import com.mediatek.gallery3d.ext.DefaultServerTimeoutExtension;
import com.mediatek.galleryfeature.config.FeatureConfig;

import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.common.MPlugin;


import java.util.ArrayList;
import java.util.List;

public class ExtensionHelper {
    private static final String TAG = "Gallery2/VideoPlayer/ExtensionHelper";
    private static final boolean LOG = true;

    private static List<IMovieExtension> sMovieExtensions;
    private static boolean mHasRewindAndForward;
    private static void ensureMovieExtension(final Context context) {
        MtkLog.v(TAG, "ensureMovieExtension() sMovieExtensions " + sMovieExtensions);
        if (sMovieExtensions == null) {
            sMovieExtensions = new ArrayList<IMovieExtension>();
            boolean find = false;
            final IMovieExtension ext = (IMovieExtension) MPlugin.createInstance(IMovieExtension.class.getName(), context);
            MtkLog.v(TAG, "ensureMovieExtension() ext = " + ext);
            if (ext != null) {
                sMovieExtensions.add(ext);
                find = true;
             }
            if (!find) { //add default implemetation
                sMovieExtensions.add(new DefaultMovieExtension(context));
            }
        }
    }

    public static IActivityHooker getHooker(final Context context) {
        ensureMovieExtension(context);
        final ActivityHookerGroup group = new ActivityHookerGroup();
        getServerTimeoutExtension(context);
        getRewindAndForwardExtension(context);
        group.addHooker(new StopVideoHooker()); //add it for common feature.
        group.addHooker(new LoopVideoHooker()); //add it for common feature.
        // /M:slidevideo @{
        if (context instanceof MovieActivity) {
            group.addHooker(new TrimVideoHooker());
        } // /@}
        //M: MTK_SUBTITLE_SUPPORT
        ///@{
        if (MtkVideoFeature.isSubTitleSupport()) {
            group.addHooker(new SubtitleVideoHooker());
        }
        if (MtkVideoFeature.isSlowMotionSupport() && mHasRewindAndForward) {
            group.addHooker(new SlowMotionHooker());
        }
        if (MtkVideoFeature.isHotKnotSupported() && (context instanceof MovieActivity)) {
            group.addHooker(new HotKnotHooker());
        }
        ///@}
        for (final IMovieExtension ext : sMovieExtensions) { //add other feature in plugin app
            final ArrayList<IActivityHooker> hookers = ext.getHookers(context);
            if (hookers != null) {
                for (int i = 0, size = hookers.size(); i < size; i++) {
                    IActivityHooker hooker = hookers.get(i);
                    group.addHooker(hooker);
                }
            }
        }

        if (mServerTimeoutExtension != null) {
            group.addHooker((IActivityHooker) mServerTimeoutExtension);
            }

        if (FeatureConfig.isSupportClearMotion(context)) {
            group.addHooker(new ClearMotionHooker());
        }

        if (FeatureConfig.supportPQ) {
            group.addHooker(new PqHooker());
        }

        for (int i = 0, count = group.size(); i < count; i++) {
            if (LOG) {
                MtkLog.v(TAG, "getHooker() [" + i + "]=" + group.getHooker(i));
            }
        }
        return group;
    }


    private static IMovieDrmExtension sMovieDrmExtension;
    public static IMovieDrmExtension getMovieDrmExtension(final Context context) {
        if (sMovieDrmExtension == null) {
            /*try {
                sMovieDrmExtension = (IMovieDrmExtension) PluginManager.createPluginObject(
                        context.getApplicationContext(), IMovieDrmExtension.class.getName());
            } catch (Plugin.ObjectCreationException e) {
                sMovieDrmExtension = new MovieDrmExtension();
            }*/
            //should be modified for common feature
            if (OmaDrmClient.isOmaDrmEnabled()) {
                sMovieDrmExtension = new MovieDrmExtensionImpl();
            } else {
                sMovieDrmExtension = new DefaultMovieDrmExtension();
            }
        }
        return sMovieDrmExtension;
    }
    private static IServerTimeoutExtension mServerTimeoutExtension;
    public static IServerTimeoutExtension getServerTimeoutExtension(final Context context) {
        ensureMovieExtension(context);
        for (final IMovieExtension ext : sMovieExtensions) {
            final IServerTimeoutExtension serverTimeout = ext.getServerTimeoutExtension();
            if (serverTimeout != null) {
                mServerTimeoutExtension = serverTimeout;
                return serverTimeout;
            }
        }
        mServerTimeoutExtension = new DefaultServerTimeoutExtension();
        return mServerTimeoutExtension;
    }
    public static IServerTimeoutExtension getServerTimeout() {
        return mServerTimeoutExtension;
    }
    
    public static IRewindAndForwardExtension getRewindAndForwardExtension(final Context context) {
        ensureMovieExtension(context);
        for (final IMovieExtension ext : sMovieExtensions) {
            final IRewindAndForwardExtension rewindAndForward = ext.getRewindAndForwardExtension();
            if (rewindAndForward != null) {
                if (rewindAndForward.getView() != null) {
                    mHasRewindAndForward = true;
                }
                return rewindAndForward;
            }
        }
        return new DefaultRewindAndForwardExtension();
    }
    
    public static boolean hasRewindAndForward(final Context context) {
        return mHasRewindAndForward;
    }
    public static boolean shouldEnableCheckLongSleep(final Context context) {
        ensureMovieExtension(context);
        for (final IMovieExtension ext : sMovieExtensions) {
            return ext.shouldEnableCheckLongSleep();
        }
        return true;
    }
}
