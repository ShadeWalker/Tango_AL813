package com.mediatek.galleryfeature.panorama;

import com.mediatek.galleryframework.gl.MGLView;

public interface SwitchBarView extends MGLView {
    public static final int BUTTON_NORMAL = 1;
    public static final int BUTTON_3D = 2;

    public interface OnClickListener {
        void onClick();
    }

    public void setVisibility(boolean isVisible);

    public void setOnClickListener(OnClickListener listener);

    public int getFocusButton();

    public void setFocusButton(int button, boolean fromUser);
}