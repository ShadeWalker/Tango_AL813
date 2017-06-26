package com.mediatek.gallery3d.video;

import android.content.Context;
import com.mediatek.gallery3d.ext.IMovieItem;

public class DefaultMovieDrmExtension implements IMovieDrmExtension {
    @Override
    public boolean handleDrmFile(final Context context, final IMovieItem item, final IMovieDrmCallback callback) {
        return false;
    }

    @Override
    public boolean canShare(Context context, IMovieItem item) {
        return true;
    }
}