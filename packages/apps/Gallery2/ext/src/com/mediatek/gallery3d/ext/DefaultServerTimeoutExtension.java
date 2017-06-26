package com.mediatek.gallery3d.ext;

import android.media.MediaPlayer;
import android.media.Metadata;
import android.os.Bundle;

public class DefaultServerTimeoutExtension extends DefaultActivityHooker implements IServerTimeoutExtension {

    @Override
    public void recordDisconnectTime() {
    }

    @Override
    public void clearServerInfo() {
    }

    @Override
    public void clearTimeoutDialog() {
    }

    @Override
    public void onRestoreInstanceState(Bundle icicle) {
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
    }

    @Override
    public boolean handleOnResume() {
        return false;
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        return false;
    }

    @Override
    public void setVideoInfo(Metadata data) {
    }

}
