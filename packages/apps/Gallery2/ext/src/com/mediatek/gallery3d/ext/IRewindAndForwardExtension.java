package com.mediatek.gallery3d.ext;

import android.view.View;

/**
 * ServerTimeout extension interface.
 */
public interface IRewindAndForwardExtension {
    /**
      *@return RewindAndForward View.
      */
    View getView();
    /**
      *@return right padding added.
      */
    int getAddedRightPadding();
    /**
      * @return the button of rewindAndForward height
      */
    int getHeight();
    /**
     * controller button position specified.
     * @return position.
     */
    int getControllerButtonPosition();
    /**
      * hide RewindAndForward View
      */
    void onHide();
    /**
      * show RewindAndForward View
      */
    void onShow();
    /**
      * start do RewindAndForward View hiding animation.
      */
    void onStartHiding();
    /**
      * cancle the hide anmiation
      */
    void onCancelHiding();
    /**
      * button response when click.
      * @param v
      */
    void onClick(View v);
    /**
      * set RewindAndForward View enabled or not
      * @param isEnabled
      */
    void setViewEnabled(boolean isEnabled);
    /**
      * onLayout of RewindAndForward View.
      * @param l
      * @param r
      * @param b
      * @param pr
      */
    void onLayout(int l, int r, int b, int pr);
    /**
      * update RewindAndForward UI.
      */
    void updateRewindAndForwardUI();

}
