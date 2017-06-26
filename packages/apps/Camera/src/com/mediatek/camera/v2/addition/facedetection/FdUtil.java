package com.mediatek.camera.v2.addition.facedetection;

import android.app.Activity;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.android.camera.R;

public class FdUtil {
    
    private static final String TAG = "CAM_APP/FaceDetectionUtil";

    // For Face Detection
    public static final int FACE_DETECTION_ICON_NUM = 4;
    public static final int FACE_FOCUSING = 0;
    public static final int FACE_FOCUSED = 1;
    public static final int FACE_FOCUSFAILD = 2;
    public static final int FACE_BEAUTY = 3;
    
    private Drawable[] mFaceStatusIndicator = new Drawable[FACE_DETECTION_ICON_NUM];
    
    private static final int[] FACE_DETECTION_ICON = new int[] {
            R.drawable.ic_face_detection_focusing,
            R.drawable.ic_face_detection_focused,
            R.drawable.ic_face_detection_failed, 
            R.drawable.ic_facebeautify_frame 
    };
    
    private Activity mActivity;
    
    public FdUtil(Activity activity) {
        //do-noting
        mActivity = activity;
    }
    
    public Drawable[] getViewDrawable() {
        for (int i = 0; i < FACE_DETECTION_ICON_NUM; i++) {
            mFaceStatusIndicator[i] = mActivity.getResources().getDrawable(FACE_DETECTION_ICON[i]);
        }
        return mFaceStatusIndicator;
    }
    
    public void prepareMatrix(Matrix matrix, boolean mirror, int displayOrientation,
            int viewWidth, int viewHeight) {
        // Need mirror for front camera.
        matrix.setScale(mirror ? -1 : 1, 1);
        // This is the value for android.hardware.Camera.setDisplayOrientation.
        matrix.postRotate(displayOrientation);
        // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
        // UI coordinates range from (0, 0) to (width, height).
        // TODO current crop region is (0,0,2560;1920);
        matrix.postScale(viewWidth / 2560f, viewHeight / 1920f);
        matrix.postTranslate(viewWidth / 2f, viewHeight / 2f);
    }
    
    public void dumpRect(RectF rect, String msg) {
        Log.v(TAG, msg + "=(" + rect.left + "," + rect.top
                + "," + rect.right + "," + rect.bottom + ")");
    }
    
    public void rectFToRect(RectF rectF, Rect rect) {
        rect.left = Math.round(rectF.left);
        rect.top = Math.round(rectF.top);
        rect.right = Math.round(rectF.right);
        rect.bottom = Math.round(rectF.bottom);
    }
}
