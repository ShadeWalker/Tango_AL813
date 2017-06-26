package com.mediatek.gallery3d.video;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;

import com.android.gallery3d.app.MovieControllerOverlay;
import com.android.gallery3d.ui.GestureRecognizer;

import com.mediatek.galleryframework.util.MtkLog;
public class VideoZoomController implements GestureRecognizer.Listener {

    private static final String TAG = "Gallery2/VideoZoomController";
    //relative to default display video size.
    private static final float MINI_SCALE_RATIO = 1.0f;
    // 2k/4k video max scale ratio, relative to source video size.
    private static final float MAX_SCALE_RATIO_2K_4K = 4.0f;
    // smaller than 2k videos max scale ratio, relative to source video size.
    private static final float MAX_SCALE_RATIO = 4.0f;

    //Maximum double tap zoom in ratio, relative to default display size.
    private static final float MAX_DOUBLETAP_RATIO = 4.0f;
    // minimal double tap zoom in ratio, relative to default display size.
    private static final float MINI_DOUBLETAP_RATIO = 2.0f;
    // default double tap scale ratio will equal to DOUBLETAP_DEFAULT_PERSENT times max scale ratio.
    private static final float DOUBLETAP_DEFAULT_PERSENT = 0.7f;

    //current display size relative to default display size.
    private float mDispScaleRatio = 1.0f;
    //default display size relative to source video size.
    private float mVideoScaleRatio = 1.0f;

    private Context mContext;

    private int mRootViewWdith;

    private RectF mCurrentDispRectF = new RectF();
    private RectF mCurrentDispHitRectF = new RectF();
    private RectF mTargetRectF = new RectF();
    private Matrix mCurrentDispMatrix = new Matrix();

    private MTKVideoView mVideoView;
    private View mVideoRoot;
    private View mOverlay;

    private int mVideoWidth;
    private int mVideoHeight;

    private int mDefaultPreviewWidth = 0;
    private int mDefaultPreviewHeight = 0;

    private final GestureRecognizer mGestureRecognizer;



    private class LayoutListener implements OnGlobalLayoutListener {
        @Override
        public void onGlobalLayout() {
            // TODO Auto-generated method stub
            if (!isVideoZoomEnabled) {
                return;
            }
            //if in wfd extension mode, we don not support video zoom
            if (isInWfdExtension()) {
                return;
            }
            int rootviewWidth = mVideoView.getRootView().getWidth();
            if (rootviewWidth != mRootViewWdith) {
                MtkLog.i(TAG, " OnGlobalLayoutListener root width  " + rootviewWidth);
                // 1 . get current display rect.
                calculateCurrentDisplayRectF();
                MtkLog.i(TAG, " OnGlobalLayoutListener mCurrentDispRectF " + mCurrentDispRectF);
                // 2. correct the position.
                int rootviewHeight = mVideoView.getRootView().getHeight();
                // 3.1 calculate default preivew size.
                // should optimize here, when play a video, default value is
                // always the same.
                calculateDefaultPreviewSize();
                // 3.2 now correct the video position and size by case.
                // case 1: preview width is shorter than default preview width.
                if (mCurrentDispRectF.width() < mDefaultPreviewWidth
                        && mCurrentDispRectF.height() < mDefaultPreviewHeight) {
                    // if current preview width and height are both shorter than
                    // default value.
                    // use default value and clear scroll value.
                    mDispScaleRatio = 1.0f;
                    mVideoRoot.setScrollX(0);
                    mVideoRoot.setScrollY(0);
                    MtkLog.i(TAG, " OnGlobalLayoutListener case 1");
                }
                // case 2: one side is longer than default value, another side
                // is shorter than screen value.
                if (mCurrentDispRectF.width() > mDefaultPreviewWidth
                        && mCurrentDispRectF.height() < rootviewHeight
                        || mCurrentDispRectF.width() < rootviewWidth
                        && mCurrentDispRectF.height() > mDefaultPreviewHeight) {
                    //if current screen mode is FULLSCREEN or CROPSCREEN,
                    //current display rect is smaller than default display rect,
                    //so change to default display rect in case 2.1.
                    if (mCurrentScreenMode == ScreenModeManager.SCREENMODE_FULLSCREEN
                            || mCurrentScreenMode == ScreenModeManager.SCREENMODE_CROPSCREEN) {
                        mDispScaleRatio = 1.0f;
                        mVideoRoot.setScrollX(0);
                        mVideoRoot.setScrollY(0);
                        MtkLog.i(TAG, " OnGlobalLayoutListener case 2.1");
                    } else {
                        if (rootviewHeight > rootviewWidth) {
                            // portrait
                            int offsetX = 0;
                            if (mCurrentDispRectF.left > 0) {
                                offsetX = -Math.round(mCurrentDispRectF.left);
                            } else if (mCurrentDispRectF.right < rootviewWidth) {
                                offsetX = rootviewWidth
                                        - Math.round(mCurrentDispRectF.right);
                            }
                            // preview will be in the center of screen.
                            mVideoRoot.setScrollY(0);
                            mVideoRoot.scrollBy(-offsetX, 0);
                            mDispScaleRatio = 1.0f * mCurrentDispRectF.width()
                                    / mDefaultPreviewWidth;
                            MtkLog.i(TAG,
                                    " OnGlobalLayoutListener case 2.2 mDispScaleRatio "
                                            + mDispScaleRatio + " offsetX "
                                            + offsetX);
                        } else {
                            // landscape
                            int offsetY = 0;
                            if (mCurrentDispRectF.top > 0) {
                                offsetY = -Math.round(mCurrentDispRectF.top);
                            } else if (mCurrentDispRectF.bottom < rootviewHeight) {
                                offsetY = rootviewHeight
                                        - Math.round(mCurrentDispRectF.bottom);
                            }
                            mVideoRoot.setScrollX(0);
                            mVideoRoot.scrollBy(0, -offsetY);
                            mDispScaleRatio = 1.0f * mCurrentDispRectF.height()
                                    / mDefaultPreviewHeight;
                            MtkLog.i(TAG,
                                    " OnGlobalLayoutListener case 2.3 mDispScaleRatio "
                                            + mDispScaleRatio + " offsetY "
                                            + offsetY);
                        }
                    }
                }
                // case 3: both side are longer than screen.
                if (mCurrentDispRectF.width() > rootviewWidth
                        && mCurrentDispRectF.height() > rootviewHeight) {
                    // correct x coordinate
                    int offsetX = 0;
                    int offsetY = 0;
                    if (mCurrentDispRectF.left > 0) {
                        offsetX = -Math.round(mCurrentDispRectF.left);
                    } else if (mCurrentDispRectF.right < rootviewWidth) {
                        offsetX = rootviewWidth - Math.round(mCurrentDispRectF.right);
                    }
                    if (mCurrentDispRectF.top > 0) {
                        offsetY = -Math.round(mCurrentDispRectF.top);
                    } else if (Math.round(mCurrentDispRectF.bottom) < rootviewHeight) {
                        offsetY = rootviewHeight - Math.round(mCurrentDispRectF.bottom);
                    }
                    mVideoRoot.scrollBy(-offsetX, -offsetY);
                    mDispScaleRatio = 1.0f * mCurrentDispRectF.height() / mDefaultPreviewHeight;
                    MtkLog.i(TAG, " OnGlobalLayoutListener case 3 mDispScaleRatio "
                            + mDispScaleRatio + " offsetX " + offsetX + " offsetY " + offsetY);
                }
                mRootViewWdith = rootviewWidth; 
                mVideoView.requestLayout();
            }
        }
    }

    public VideoZoomController(Context context, View videoroot, IMtkVideoController videoview,
            View overlay) {
        mContext = context;
        mVideoView = (MTKVideoView) videoview;
        mOverlay = overlay; //MovieControllerOverlay instance.
        mVideoRoot = videoroot;
        mRootViewWdith = mVideoRoot.getRootView().getWidth();
        mVideoView.setVideoZoomController(this);

        mGestureRecognizer = new GestureRecognizer(mContext, this);

        mVideoRoot.getRootView().setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
              mGestureRecognizer.onTouchEvent(event);
              return true;
            }
        });
        ViewTreeObserver vto = mVideoRoot.getRootView().getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new LayoutListener());
    }
    /**
     * Reset video display area to default.
     */
    public void reset() {
        setScaleRatio(MINI_SCALE_RATIO);
        mVideoRoot.setScrollX(0);
        mVideoRoot.setScrollY(0);
    }
    
    public boolean isInWfdExtension() {
        return false;
    }
    public void updateWfdStatus() {
        if (isInWfdExtension()) {
            reset();
        }
    }

    @Override
    public boolean onSingleTapUp(float x, float y) {
        MtkLog.i(TAG, "onSingleTapUp");
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(float x, float y) {
        MtkLog.i(TAG, "onSingleTapConfirmed");
        ((MovieControllerOverlay) mOverlay).show();
        return true;
    }

    @Override
    public boolean onDoubleTap(float x, float y) {
        if (!isVideoZoomEnabled) {
            return true;
        }
        //if in wfd extension mode, we don not support video zoom
        if (isInWfdExtension()) {
            return true;
        }
        
        if (isAtMinimalScale()) {
            Rect r = new Rect();
            mVideoView.getHitRect(r);
            MtkLog.i(TAG, " onDoubleTap getHitRect " + r);

            //get current video scale ration, relative to file size.
            float maxScale = mMaxVideoScale / mVideoScaleRatio;
            MtkLog.i(TAG, " onDoubleTap maxScale " + maxScale);
            //if max scale ratio is smaller than 1.0f, then ignore the event.
            if (maxScale < MINI_SCALE_RATIO) {
                return true;
            }
            //calculate scale rate.
            //after double-click zoom in, should has ability to zoom in by pinch.
            float rate = maxScale * DOUBLETAP_DEFAULT_PERSENT;
            //scale ratio should bigger than MINI_SCALE_RATIO.
            rate = Math.max(rate, MINI_SCALE_RATIO);
            //scale ratio should smaller than MINI_DOUBLETAP_RATIO.
            rate = Math.min(rate, MINI_DOUBLETAP_RATIO);
            //if the result is almost equal MINI_SCALE_RATIO, use max scale ratio.
            if (isAlmostEqual(rate, MINI_SCALE_RATIO)) {
                rate = maxScale;
            }
            MtkLog.i(TAG, " onDoubleTap rate " + rate);
            //scale to the target rate and move to the right position.
            zoomToPoint(rate, x, y, mVideoRoot);
        } else {
            setScaleRatio(MINI_SCALE_RATIO);
            mVideoRoot.setScrollX(0);
            mVideoRoot.setScrollY(0);
        }
        return true;
    }

    private void calculateCurrentDisplayRectF() {
        Rect r = new Rect();
        // 1. get current disp hit rect.
        mVideoView.getHitRect(r);
        mCurrentDispHitRectF.set(r);
        // 2. get current scroll value.
        int scrollX = mVideoRoot.getScrollX();
        int scrollY = mVideoRoot.getScrollY();
        // 3. get current disp rect. relative to hit rect.
        mCurrentDispMatrix.reset();
        mCurrentDispMatrix.postTranslate(-scrollX, -scrollY);
        // 4. get current display rect.
        mCurrentDispMatrix.mapRect(mCurrentDispRectF, mCurrentDispHitRectF);
    }

    @Override
    public boolean onScroll(float dx, float dy, float totalX, float totalY) {
        if (!isVideoZoomEnabled) {
            return true;
        }
        //if in wfd extension mode, we don not support video zoom
        if (isInWfdExtension()) {
            return true;
        }
        
        if (getDispScaleRatio() > MINI_SCALE_RATIO) {
            //1. we want to movie x and y distance.
            float offsetX = -dx;
            float offsetY = -dy;
            // 2. get current display rect.
            calculateCurrentDisplayRectF();
            if (offsetX >= 0) {
                //will move to right,find max offset we can.
                float maxOffsetX = 0;
                if (mCurrentDispRectF.left >= 0) {
                    maxOffsetX = 0;
                } else {
                    maxOffsetX = -mCurrentDispRectF.left;
                }
                //max offset is found, correct the offsetX value.
                if (offsetX > maxOffsetX) {
                    offsetX = maxOffsetX;
                }
            } else {
                //will move to left, find max offset we can.
                float maxOffsetX = 0;
                if (mCurrentDispRectF.right <= mVideoRoot.getWidth()) {
                    maxOffsetX = 0;
                } else {
                    //remember offset is negative.
                    maxOffsetX = mVideoRoot.getWidth() - mCurrentDispRectF.right;
                }
                //max offset is found, correct the offsetX value.
                //remember offset is negative.
                if (offsetX < maxOffsetX) {
                    offsetX = maxOffsetX;
                }
            }

            //second correct y coordinate
            if (offsetY >= 0) {
                //will move to bottom, find max offset we can.
                float maxOffsetY = 0;
                if (mCurrentDispRectF.top >= 0) {
                    maxOffsetY = 0;
                } else {
                    maxOffsetY = -mCurrentDispRectF.top;
                }
                //max offset is found, correct the offsetY value.
                if (offsetY > maxOffsetY) {
                    offsetY = maxOffsetY;
                }
            } else {
                //will move to top, find max offset we can,
                float maxOffsetY = 0;
                if (mCurrentDispRectF.bottom <= mVideoRoot.getHeight()) {
                    maxOffsetY = 0;
                } else {
                    //remember offset is negative.
                    maxOffsetY = mVideoRoot.getHeight() - mCurrentDispRectF.bottom;
                }
                //max offset is found, correct the offsetX value.
                //remember offset is negative.
                if (offsetY < maxOffsetY) {
                    offsetY = maxOffsetY;
                }
            }
            //movie to right position.
            mVideoRoot.scrollBy(-(int) offsetX, -(int) offsetY);
        }
        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    private float mFocusX;
    private float mFocusY;
    @Override
    public boolean onScaleBegin(float focusX, float focusY) {
        if (!isVideoZoomEnabled) {
            return true;
        }
        //if in wfd extension mode, we don not support video zoom
        if (isInWfdExtension()) {
            return true;
        }
        
        MtkLog.i(TAG, "onScaleBegin");
        mScaleMatrix.reset();
        mFocusX = focusX;
        mFocusY = focusY;
        return true;
    }

    @Override
    public boolean onScale(float focusX, float focusY, float scale) {
        if (!isVideoZoomEnabled) {
            return true;
        }
        //if in wfd extension mode, we don not support video zoom
        if (isInWfdExtension()) {
            return true;
        }
        
        MtkLog.i(TAG, " onScale focusX " + focusX + " focusY "  + focusY);
        float accScale = getDispScaleRatio();
        accScale *= scale;
        //if accscale is bigger than max, use max value.
        float maxScale = mMaxVideoScale / mVideoScaleRatio;
        MtkLog.i(TAG, " onScale maxScale " + maxScale);
        accScale = Math.max(1.0f, Math.min(accScale, maxScale));
        MtkLog.i(TAG, " onScale accScale " + accScale);
        //if current scale ratio is max, return
        if (accScale == getDispScaleRatio()) {
            return true;
        }

        if (isAlmostEqual(accScale, MINI_SCALE_RATIO)) {
            mDispScaleRatio = 1.0f;
            return true;
        };
        // 1. get current display rect.
        calculateCurrentDisplayRectF();
        MtkLog.i(TAG, " onScale mCurrentDispRect " + mCurrentDispRectF);
        // 2. calculate the target disp rect.
        // reset matrix
        mScaleMatrix.reset();
        // calculate the transform matrix, relative to previous disp rect.
        mScaleMatrix.postScale(scale, scale, mFocusX, mFocusY);
        // focus center should move to the new position.
        mScaleMatrix.postTranslate(focusX - mFocusX, focusY - mFocusY);
        mScaleMatrix.mapRect(mTargetRectF, mCurrentDispRectF);
        MtkLog.i(TAG, " onScale mTargetRectF " + mTargetRectF);
        // 3. calculate x,y coordinate offset value.
        //
        float offsetX = mTargetRectF.centerX() - mCurrentDispHitRectF.centerX();
        float offsetY = mTargetRectF.centerY() - mCurrentDispHitRectF.centerY();
        MtkLog.i(TAG, " onScale  offsetX " + offsetX + " offsetY " + offsetY);

        // 4.
        setScaleRatio(accScale);
        mVideoRoot.scrollTo(-Math.round(offsetX), -Math.round(offsetY));
        mFocusX = focusX;
        mFocusY = focusY;
        return true;
    }

    @Override
    public void onScaleEnd() {
      if (!isVideoZoomEnabled) {
          return;
      }
      //if in wfd extension mode, we don not support video zoom
      if (isInWfdExtension()) {
          return;
      }
      
      MtkLog.i(TAG, "onScaleEnd");
      if (isAtMinimalScale()) {
          mVideoRoot.setScrollX(0);
          mVideoRoot.setScrollY(0);
          mVideoView.requestLayout();
          return;
      }
      //1. get current display rect.
      calculateCurrentDisplayRectF();
      MtkLog.i(TAG, " onScaleEnd mCurrentDispRectF " + mCurrentDispRectF);

      //2. if video width is smaller than screen width, move video to the center of x coordinate.
      if (mCurrentDispRectF.left > 0 || mCurrentDispRectF.right < mVideoRoot.getWidth()) {
          mVideoRoot.setScrollX(0);
      }

      //3. if video height is smaller than screen height, move video to the center of y coordinate.
      if (mCurrentDispRectF.top > 0 || mCurrentDispRectF.bottom < mVideoRoot.getHeight()) {
          mVideoRoot.setScrollY(0);
      }

      //4. if video size is smaller than default size, use default value.
      // for example, screen rotate.
      //TODO: how to get default preview size.
      //TODO: check onMeasure  default size is useful or not.
        MtkLog.i(TAG, " onScaleEnd mDefaultPreviewWidth " + mDefaultPreviewWidth
                + " mDefaultPreviewHeight " + mDefaultPreviewHeight);
        if (mCurrentDispRectF.width() < mDefaultPreviewWidth
                && mCurrentDispRectF.height() < mDefaultPreviewHeight) {
            // if current preview width and height are both shorter than default
            // value. use default value and clear scroll value.
            mDispScaleRatio = 1.0f;
            mVideoView.requestLayout();
            mVideoRoot.setScrollX(0);
            mVideoRoot.setScrollY(0);
        }

    }

    @Override
    public void onDown(float x, float y) {
        MtkLog.i(TAG, "onDown");
        onDownEvent();
    }

    @Override
    public void onUp() {
        MtkLog.i(TAG, "onUp");
    }

    @Override
    public void onLongPress(float x, float y) {
    }
    
    public void onDownEvent(){
    }

    private void setScaleRatio(final float ratio) {
        MtkLog.i(TAG, "setScaleRatio ratio = " + ratio);
        mDispScaleRatio = ratio;
        mVideoView.requestLayout();
    }

    //BIGSCREEN is screen mode's default value.
    private int mCurrentScreenMode = ScreenModeManager.SCREENMODE_BIGSCREEN;
    public void setScreenMode(int screenmode) {
        if (!isVideoZoomEnabled) {
            return;
        }
        //if in wfd extension mode, we don not support video zoom
        if (isInWfdExtension()) {
            return;
        }
        
        if (screenmode != mCurrentScreenMode) {
            //mode has changed.
            resizeToDefaultSize();
            mCurrentScreenMode = screenmode;
            calculateDefaultPreviewSize();
        }
    }

    private void calculateDefaultPreviewSize() {
        int width = mVideoRoot.getWidth();
        int height = mVideoRoot.getHeight();

        switch (mCurrentScreenMode) {
        case ScreenModeManager.SCREENMODE_BIGSCREEN:
            if (mVideoWidth > 0 && mVideoHeight > 0) {
                if (mVideoWidth * height  > width * mVideoHeight) {
                    //Log.i("@@@", "image too tall, correcting");
                    height = width * mVideoHeight / mVideoWidth;
                } else if (mVideoWidth * height  < width * mVideoHeight) {
                    //Log.i("@@@", "image too wide, correcting");
                    width = height * mVideoWidth / mVideoHeight;
                }
            }
            break;
        case ScreenModeManager.SCREENMODE_FULLSCREEN:
            break;
        case ScreenModeManager.SCREENMODE_CROPSCREEN:
            if (mVideoWidth > 0 && mVideoHeight > 0) {
                if (mVideoWidth * height  > width * mVideoHeight) {
                    //extend width to be cropped
                    width = height * mVideoWidth / mVideoHeight;
                } else if (mVideoWidth * height  < width * mVideoHeight) {
                    //extend height to be cropped
                    height = width * mVideoHeight / mVideoWidth;
                }
            }
            break;
        default:
            MtkLog.w(TAG, "wrong screen mode : " + mCurrentScreenMode);
            break;
        }
        mDefaultPreviewWidth = width;
        mDefaultPreviewHeight = height;
        MtkLog.i(TAG, " mDefaultPreviewWidth " + width + " mDefaultPreviewHeight " + height);

        mVideoScaleRatio = getVideoScaleRatio(mDefaultPreviewWidth, mDefaultPreviewHeight,
                mVideoWidth, mVideoHeight);
        MtkLog.i(TAG, " calculateDefaultPreviewSize mVideoScaleRatio " + mVideoScaleRatio);
    }

    public boolean isAtMinimalScale() {
        return isAlmostEqual(mDispScaleRatio, MINI_SCALE_RATIO);
    }

    private static boolean isAlmostEqual(float a, float b) {
        float diff = a - b;
        return (diff < 0 ? -diff : diff) < 0.02f;
    }

    private static float getVideoScaleRatio(int defaultDispW, int defaultDispH, int videoW, int videoH) {
        return Math.min(1.0f * defaultDispW / videoW, 1.0f * defaultDispH / videoH);
    }

    public boolean isInZoomState() {
        return getDispScaleRatio() > MINI_SCALE_RATIO;
    }

    private float getDispScaleRatio() {
        MtkLog.i(TAG, " getScaleRatio mDispScaleRatio " + mDispScaleRatio);
        return mDispScaleRatio;
    }

    public int getDispScaleWidth() {
        return Math.round(1.0f * getDispScaleHeight() * mDefaultPreviewWidth / mDefaultPreviewHeight);
    }

    public int getDispScaleHeight() {
        return Math.round(mTargetRectF.height());
    }

    public void resizeToDefaultSize() {
        mDispScaleRatio = 1.0f;
        mVideoRoot.setScrollX(0);
        mVideoRoot.setScrollY(0);
    }

    //if current playing video's resolution is smaller than 320x240, disable video zoom feature.
    private boolean isVideoZoomEnabled;
    private float mMaxVideoScale;

    public void setVideoSize(int width, int height) {
        MtkLog.i(TAG, "setVideoSize width " + width + " height " + height);
      //if in wfd extension mode, we don not support video zoom
        if (isInWfdExtension()) {
            setScaleRatio(MINI_SCALE_RATIO);
            mVideoRoot.setScrollX(0);
            mVideoRoot.setScrollY(0);
            return;
        }
        if (width > 0 && height > 0) {
            mVideoWidth = width;
            mVideoHeight = height;
            isVideoZoomEnabled = ((mVideoWidth >= 320) && (mVideoHeight >= 240) || (mVideoWidth >= 240)
                    && (mVideoHeight >= 320));
            //for streaming, the video size maybe change when playing.
            if (!isVideoZoomEnabled && isInZoomState()) {
                reset();
            }
            if (isVideoZoomEnabled) {
                if ((mVideoWidth >= 1920) && (mVideoHeight >= 1080) || (mVideoWidth >= 1080)
                        && (mVideoHeight >= 1920)) {
                    mMaxVideoScale = MAX_SCALE_RATIO_2K_4K;
                } else {
                    mMaxVideoScale = MAX_SCALE_RATIO;
                }
                calculateDefaultPreviewSize();
            }
        }
    }

    private Matrix mScaleMatrix = new Matrix();
    private void zoomToPoint(float scale, float x, float y, final View videoroot) {

        //1. get current disp hit rect.
        Rect r = new Rect();
        mVideoView.getHitRect(r);
        mCurrentDispHitRectF.set(r);
        //2. calculate the target disp rect.
        //reset matrix
        MtkLog.i(TAG, " zoomToPoint mCurrentDispHitRect " + mCurrentDispHitRectF);
        mScaleMatrix.reset();
        //calculate the transform matrix, relative to previous disp rect.
        mScaleMatrix.postScale(scale, scale, mCurrentDispHitRectF.centerX(),
                mCurrentDispHitRectF.centerY());
        mScaleMatrix.mapRect(mTargetRectF, mCurrentDispHitRectF);
        MtkLog.i(TAG, " zoomToPoint targetRectF " + mTargetRectF);
        //3. get clicked position in magnified rect.
        float[] screenTouchPos = {x, y};
        float[] magnifiedTouchPos = {0, 0};
        mScaleMatrix.mapPoints(magnifiedTouchPos, screenTouchPos);
        MtkLog.i(TAG, " zoomToPoint  screenTouchPos[0] " + screenTouchPos[0]
                + " screenTouchPos[1] " + screenTouchPos[1]);
        MtkLog.i(TAG, " zoomToPoint  magnifiedTouchPos[0] " + magnifiedTouchPos[0]
                + " magnifiedTouchPos[1] " + magnifiedTouchPos[1]);

        // 4. we want to movie the magnified touch position to the center of
        // screen, so we calculate the offsetX and offsetY.
        float offsetX = mCurrentDispHitRectF.centerX() - magnifiedTouchPos[0]; // should
                                                                  // reverse
        float offsetY = mCurrentDispHitRectF.centerY() - magnifiedTouchPos[1];

        MtkLog.i(TAG, " zoomToPoint offsetX " + offsetX + " offsetY " + offsetY);
        //5. step 4cal result may be not right, we will correct it.
        //first we will correct x coordinate.
        if (offsetX >= 0) {
            //will move to right,find max offset we can.
            float maxOffsetX = 0;
            if (mTargetRectF.left >= 0) {
                maxOffsetX = 0;
            } else {
                maxOffsetX = -mTargetRectF.left;
            }
            //max offset is found, correct the offsetX value.
            if (offsetX > maxOffsetX) {
                offsetX = maxOffsetX;
            }
        } else {
            //will move to left, find max offset we can.
            float maxOffsetX = 0;
            if (mTargetRectF.right <= videoroot.getWidth()) {
                maxOffsetX = 0;
            } else {
                //remember offset is negative.
                maxOffsetX = videoroot.getWidth() - mTargetRectF.right;
            }
            //max offset is found, correct the offsetX value.
            //remember offset is negative.
            if (offsetX < maxOffsetX) {
                offsetX = maxOffsetX;
            }
        }

        //second correct y coordinate
        if (offsetY >= 0) {
            //will move to bottom, find max offset we can.
            float maxOffsetY = 0;
            if (mTargetRectF.top >= 0) {
                maxOffsetY = 0;
            } else {
                maxOffsetY = -mTargetRectF.top;
            }
            //max offset is found, correct the offsetY value.
            if (offsetY > maxOffsetY) {
                offsetY = maxOffsetY;
            }
        } else {
            //will move to top, find max offset we can,
            float maxOffsetY = 0;
            if (mTargetRectF.bottom <= videoroot.getHeight()) {
                maxOffsetY = 0;
            } else {
                //remember offset is negative.
                maxOffsetY = videoroot.getHeight() - mTargetRectF.bottom;
            }
            //max offset is found, correct the offsetX value.
            //remember offset is negative.
            if (offsetY < maxOffsetY) {
                offsetY = maxOffsetY;
            }

        }

        MtkLog.i(TAG, " zoomToPoint after correct offsetX " + offsetX + " offsetY " + offsetY);
        MtkLog.i(TAG, " zoomToPoint videoRoot scrollX " + videoroot.getScrollX() + " scrollY"
                + videoroot.getScrollY());
        //6. magnified video and move to the right position.
        setScaleRatio(scale);
        videoroot.scrollTo(-(int) offsetX, -(int) offsetY);
    }
}