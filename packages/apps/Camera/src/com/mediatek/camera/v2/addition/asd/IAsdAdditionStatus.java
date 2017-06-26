package com.mediatek.camera.v2.addition.asd;

public interface IAsdAdditionStatus {

    /**
     * hide the current scene mode view
     */
    public void onAsdStarted();
    
    /**
     * show the detected scene mode view
     */
    public void onAsdStopped();
    
    /**
     * notify the orientation to the view
     * @param orientation
     */
    public void setOrientation(int orientation);
    
    /**
     * notify the display orientation to the view
     * @param displayOrientation
     */
    public void setDisplayOrientation(int displayOrientation);
    
    /**
     * callback to view about current ASD detected result,
     * the view will show the correct view UI
     * @param mode about which mode is detected.
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
    
    
    the icon need to be follow:
    <array name="camera_scenemode_icons" translate="false">
    257         <item>@drawable/ic_camera_asd_auto</item>
    258         <item>@drawable/ic_camera_asd_normal</item>
    259         <item>@drawable/ic_camera_asd_night</item>
    260         <item>@drawable/ic_camera_asd_sunset</item>
    261         <item>@drawable/ic_camera_asd_party</item>
    262         <item>@drawable/ic_camera_asd_portrait</item>
    263         <item>@drawable/ic_camera_asd_landscape</item>
    264         <item>@drawable/ic_camera_asd_night_portrait</item>
    265         <item>@drawable/ic_camera_asd_theatre</item>
    266         <item>@drawable/ic_camera_asd_beach</item>
    267         <item>@drawable/ic_camera_asd_snow</item>
    268         <item>@drawable/ic_camera_asd_steadyphoto</item>
    269         <item>@drawable/ic_camera_asd_fireworks</item>
    270         <item>@drawable/ic_camera_asd_sports</item>
    271         <item>@drawable/ic_camera_asd_candlelight</item>
    272         <item>@drawable/ic_camera_asd_hdr</item>
    273         <item>@drawable/ic_camera_asd_backlight_portrait</item>
    274     </array>
     */

    public void onAsdDetected(int normalMode, int hdrMode);
    
    /**
     * notify view,current ASD is closed
     */
    public void onAsdClosed();
}
