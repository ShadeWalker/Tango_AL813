package com.mediatek.incallui.ext;

/**
 * Interface for calling back to host IInCallScreenExt
 * @see com.mediatek.incallui.ext.IRCSeInCallExt
 */
public interface IInCallScreenExt {
    /**
     * when plugin need to update UI, it would call this interface to
     * notify host update the whole InCallActivity
     */
    void requestUpdateScreen();
}
