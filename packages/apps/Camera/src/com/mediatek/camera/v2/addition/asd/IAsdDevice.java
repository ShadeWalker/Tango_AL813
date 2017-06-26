package com.mediatek.camera.v2.addition.asd;

import com.mediatek.camera.v2.addition.IAdditionCaptureObserver;

public interface IAsdDevice {

    /**
     * startAsd vendor tag
     * @param isHdrMode is true means will use FULL mode
     *                  false means just use simple mode
     */
    public void startAsd(boolean isHdrMode);
    
    public void stopAsd();
    
    /**
     * Get CaptureObserver.
     * 
     * @return The IAdditionCaptureObserver used to register
     */
    public IAdditionCaptureObserver getCaptureObserver();
}
