package com.mediatek.camera.v2.addition.asd;

public interface IAsdAdditionCallback {
    /**
     * ************this callback maybe AP not use*************
     * if not need callback,he can pass a null object to AsdView
     * *********************************************************
     * *********************************************************
     * callback to AP,if the want to know current ASD Mode
     * such as ,the maybe according current mode to show or hide other view
     * @param mode which mode current is detected
     * the mode value will be one of follow:
     * TagResult.java
     * public enum AsdValue {
        mhal_ASD_DECIDER_UI_AUTO,
        mhal_ASD_DECIDER_UI_N,
        mhal_ASD_DECIDER_UI_B,
        mhal_ASD_DECIDER_UI_P,
        mhal_ASD_DECIDER_UI_L,
        mhal_ASD_DECIDER_UI_NB,
        mhal_ASD_DECIDER_UI_NP,
        mhal_ASD_DECIDER_UI_NL,
        mhal_ASD_DECIDER_UI_BP,
        mhal_ASD_DECIDER_UI_BL,
        mhal_ASD_DECIDER_UI_PL,
        mhal_ASD_DECIDER_UI_NBL,
        mhal_ASD_DECIDER_UI_NPL,
        mhal_ASD_DECIDER_UI_BPL,
        mhal_ASD_DECIDER_UI_NBPL,
        mhal_ASD_DECIDER_UI_SCENE_NUM
    };
     */
    public void onAsdDetectedScene(String scene);
    
    /**
     * notify AP current ASD is closed
     */
    public void onAsdClosed();

}
