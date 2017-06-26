package com.mediatek.gallery3d.video;

import com.mediatek.gallery3d.ext.DefaultActivityHooker;
import com.mediatek.galleryframework.util.MtkLog;

public class MovieHooker extends DefaultActivityHooker {
    private static final String TAG = "Gallery2/VideoPlayer/MovieHooker";
    private static final boolean LOG = true;
    private IMoviePlayer mPlayer;

    @Override
    public void setParameter(final String key, final Object value) {
        super.setParameter(key, value);
        if (LOG) {
            MtkLog.v(TAG, "setParameter(" + key + ", " + value + ")");
        }
        if (value instanceof IMoviePlayer) {
            mPlayer = (IMoviePlayer) value;
            onMoviePlayerChanged(mPlayer);
        }
    }

    public IMoviePlayer getPlayer() {
        return mPlayer;
    }

    public void onMoviePlayerChanged(final IMoviePlayer player){}
}
