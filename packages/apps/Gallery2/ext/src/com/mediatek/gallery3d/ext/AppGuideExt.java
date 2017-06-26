package com.mediatek.gallery3d.ext;
import android.app.Activity;

public class AppGuideExt implements IAppGuideExt {
    /**
     * Called when the app want to show application guide
     * @param activity: The parent activity
     * @param type: The app type, such as "PHONE/CONTACTS/MMS/CAMERA"
     */
    @Override
    public void showGalleryGuide(Activity activity, String type,
            OnGuideFinishListener finishListener) {
        if (null != finishListener) {
            finishListener.onGuideFinish();
        }
    }

    @Override
    public void configurationChanged() {
    }

    @Override
    public void dismiss() {
    }
}
