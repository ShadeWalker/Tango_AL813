package com.mediatek.gallery3d.ext;

import android.content.ContextWrapper;
import android.content.Context;

import java.util.ArrayList;


public class DefaultMovieExtension extends ContextWrapper implements IMovieExtension {

    protected Context mContext;

    public DefaultMovieExtension(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public boolean shouldEnableCheckLongSleep() {
        return true;
    }
    @Override
    public ArrayList<IActivityHooker> getHookers(Context context) {
        return null;
    }

    @Override
    public IServerTimeoutExtension getServerTimeoutExtension() {
        return new DefaultServerTimeoutExtension();
    }

    @Override
    public IRewindAndForwardExtension getRewindAndForwardExtension() {
        return new DefaultRewindAndForwardExtension();
    }
}
