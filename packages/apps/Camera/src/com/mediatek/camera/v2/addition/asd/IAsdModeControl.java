package com.mediatek.camera.v2.addition.asd;

public interface IAsdModeControl {

    /**
     * startAsd vendor tag
     * @param isHdrMode is true means will use FULL mode
     *                  false means just use simple mode
     */
    public void startAsd(boolean isHdrMode);
    
    public void stopAsd();
}
