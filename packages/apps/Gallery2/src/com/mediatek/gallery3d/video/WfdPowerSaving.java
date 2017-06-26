package com.mediatek.gallery3d.video;

import com.android.gallery3d.R;
import com.android.gallery3d.app.Log;
import com.android.gallery3d.app.MovieActivity;
import com.android.gallery3d.common.ApiHelper;
import com.mediatek.galleryframework.util.MtkLog;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Presentation;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.hardware.display.WifiDisplayStatus;
import android.media.MediaRouter;
import android.media.MediaRouter.RouteInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;



public class WfdPowerSaving {
    private static final String TAG = "Gallery2/WfdPowerSaving";
    private final IMtkVideoController mVideoView;
    private final View mRootView;
    private final View mVideoRoot;
    private final Handler mHandler;

    private MediaRouter mMediaRouter;
    private WfdPresentation mPresentation;
    private PowerManager mPowerManager;
    private Window mWindow;
    private float mScreenBrightness;
    private Activity mMovieActivity;
    private boolean mIsPowerSaving = false;
    private int mLastSystemUiVis = 0;
    private boolean mIsInExtensionMode = false;
    private boolean mIsPowerSavingEnable = true;
    private boolean mIsWfdConnected = true;

    private static final int POWER_SAVING_MODE_OFF = 0;
    private static final int POWER_SAVING_MODE_DIM = 1;
    private static final int POWER_SAVING_MODE_NONE = 2;
    private static final int EXTENSION_MODE_LIST_START = 10;
    private static final int EXTENSION_MODE_LIST_END = EXTENSION_MODE_LIST_START + POWER_SAVING_MODE_NONE;

    private int mPowerSavingMode;
    private int mPowerSavingTime;

    public WfdPowerSaving(final View rootView, final Activity activity,
                                final IMtkVideoController videoview, final Handler handler) {
        MtkLog.v(TAG, "WfdPowerSaving contrustor");
        mPowerManager = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);

        mWindow = activity.getWindow();
        WindowManager.LayoutParams lp = mWindow.getAttributes();
        mScreenBrightness = lp.screenBrightness;

        // Get the media router service.
        mMediaRouter = (MediaRouter) activity.getSystemService(Context.MEDIA_ROUTER_SERVICE);
        mRootView = rootView;
        mVideoRoot = rootView.findViewById(R.id.video_root);
        mVideoView = videoview;
        mMovieActivity = activity;
        mHandler = new Handler(mMovieActivity.getMainLooper());
        mPowerSavingMode = getPowerSavingMode();
        mPowerSavingTime = getPowerSavingDelay();

        if (isInExtensionMode()) {
            mVideoView.stopPlayback();
            ((ViewGroup) mVideoRoot).removeView((View) mVideoView);
            updatePresentation();
            setOnSystemUiVisibilityChangeListener();
        }
    }

    private void unregisterOnSystemUiVisibilityChangeListener() {
        mRootView.setOnSystemUiVisibilityChangeListener(null);
        restoreSystemUiListener();
    }

    public boolean isInExtensionMode() {
        return mIsInExtensionMode;
    }

    public Activity getCurrentActivity() {
        return mMovieActivity;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setOnSystemUiVisibilityChangeListener() {
        if (!ApiHelper.HAS_VIEW_SYSTEM_UI_FLAG_HIDE_NAVIGATION) return;
        // When the user touches the screen or uses some hard key, the framework
        // will change system ui visibility from invisible to visible. We show
        // the media control and enable system UI (e.g. ActionBar) to be visible at this point
        MtkLog.v(TAG, "setOnSystemUiVisibilityChangeListener");
        mRootView.setOnSystemUiVisibilityChangeListener(
                new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                int diff = mLastSystemUiVis ^ visibility;
                MtkLog.v(TAG, "onSystemUiVisibilityChange(" + mLastSystemUiVis + ")");
                mLastSystemUiVis = visibility;
                if ((diff & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0
                        && (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0
                        || (diff & View.SYSTEM_UI_FLAG_LOW_PROFILE) != 0
                        && (visibility & View.SYSTEM_UI_FLAG_LOW_PROFILE) == 0) {
                    showController();
                }
                MtkLog.v(TAG, "onSystemUiVisibilityChange(" + visibility + ")");
            }
        });
    }


    public void setSystemUiVisibility(int flag) {
        if (isInExtensionMode()) {
            mRootView.setSystemUiVisibility(flag);
        }
    }

    private final Runnable mWfdPowerSavingRunnable = new Runnable() {
        @Override
        public void run() {
            MtkLog.v(TAG, "mWFDPowerSavingRunnable run");
            enterPowerSavingMode();
        }
   };

    public void showController() {
    }
    public void restoreSystemUiListener() {
        
    }

    public void release() {
        cancelCountDown();
        if (isInExtensionMode()) {
            Toast.makeText(mMovieActivity.getApplicationContext(),
                    mMovieActivity.getString(R.string.wfd_disconnected), Toast.LENGTH_LONG).show();
            if (mPresentation != null) {
                mPresentation.dismiss();
                mPresentation = null;
                unregisterOnSystemUiVisibilityChangeListener();
                mMediaRouter.removeCallback(mMediaRouterCallback);
            }
            mMovieActivity.finish();
      }
    }

    public boolean needShowController() {
        MtkLog.v(TAG, "needShowController()  " + mPowerSavingMode);
        return isInExtensionMode();
    }

    public void enableWfdPowerSaving() {
        mIsPowerSavingEnable = true;
    }

    public void disableWfdPowerSaving() {
        mIsPowerSavingEnable = false;
    }

    public boolean isPowerSavingEnable() {
        MtkLog.v(TAG, "isPowerSavingEnable()  " + mIsPowerSavingEnable + " && " + mIsWfdConnected);
        return mIsPowerSavingEnable && mIsWfdConnected;
    }

    private int getPowerSavingMode() {
       ContentResolver resolver = mMovieActivity.getContentResolver();
       int mode = Settings.Global.getInt(resolver, Settings.Global.WIFI_DISPLAY_POWER_SAVING_OPTION, 0);
       MtkLog.v(TAG, "getPowerSavingMode mode = " + mode);

        if ((mode >= EXTENSION_MODE_LIST_START) && (mode <= EXTENSION_MODE_LIST_END)) {
            mode = mode - EXTENSION_MODE_LIST_START;
            if (mMovieActivity instanceof MovieActivity) {
                mIsInExtensionMode = true;
            } else {
                //if in Gallery, not support extension mode.
                mIsInExtensionMode = false;
            }
        } else {
            mIsInExtensionMode = false;
        }
        return mode;
    }

    private int getPowerSavingDelay() {
        ContentResolver resolver = mMovieActivity.getContentResolver();
        int delayTime = Settings.Global.getInt(resolver, Settings.Global.WIFI_DISPLAY_POWER_SAVING_DELAY, 0);
        MtkLog.v(TAG, "getPowerSavingDelay delayTime = " + delayTime);
        return delayTime * 1000;
    }

    public void refreshPowerSavingPara() {
        MtkLog.v(TAG, "refreshPowerSavingPara");
        //when come back from backgroud, if WFD is connected, should update connect flag. 
        mIsWfdConnected = true;
        mPowerSavingMode = getPowerSavingMode();
        mPowerSavingTime = getPowerSavingDelay();
        if (isInExtensionMode()) {
            if (mPresentation  == null) {
                mVideoView.stopPlayback();
                ((ViewGroup) mVideoRoot).removeView((View) mVideoView);
                updatePresentation();
                //set a default value when come from background.
                mLastSystemUiVis = 7;
                setOnSystemUiVisibilityChangeListener();
            }
      } else {
          if (mPresentation != null) {
                mPresentation.removeSurfaceView();
                mPresentation.dismiss();
                mPresentation = null;
                ((ViewGroup) mVideoRoot).addView((View) mVideoView, 0);
                unregisterOnSystemUiVisibilityChangeListener();
                mMediaRouter.removeCallback(mMediaRouterCallback);
          }
      }
    }

    public void dismissPresentaion() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mPresentation != null) {
                    mPresentation.removeSurfaceView();
                    mPresentation.dismiss();
                    mPresentation = null;
                    ((ViewGroup) mVideoRoot).addView((View) mVideoView, 0);
                    unregisterOnSystemUiVisibilityChangeListener();
                    mMediaRouter.removeCallback(mMediaRouterCallback);
                }
            }
        });
    }


    public void startCountDown() {
            MtkLog.v(TAG, "startCountDown", new Throwable());
            mHandler.removeCallbacks(mWfdPowerSavingRunnable);
            mHandler.postDelayed(mWfdPowerSavingRunnable, mPowerSavingTime);
    }


    public void cancelCountDown() {
        MtkLog.v(TAG, "cancelCountDown");
        mHandler.removeCallbacks(mWfdPowerSavingRunnable);
        leavePowerSavingMode();
    }


    private void enterPowerSavingMode() {
        MtkLog.v(TAG, "enterPowerSavingMode " + mPowerSavingMode);
        if(!isPowerSavingEnable()) {
            //If wfd is not enabled, do nothing.
            return;
        }
        switch(mPowerSavingMode) {
        case POWER_SAVING_MODE_OFF:
            mMovieActivity.closeOptionsMenu();
            mPowerManager.setBacklightOffForWfd(true);
            mIsPowerSaving = true;
            break;
        case POWER_SAVING_MODE_DIM:
            mMovieActivity.closeOptionsMenu();
            WindowManager.LayoutParams lp = mWindow.getAttributes();
            //When support supper dimming, do not set 0, otherwise backlight brightness will too dark.
            //The backlight brightness level rang is 1~255 in DisPlayPowerController module.
            //Adjust the backlight brightness in dim mode by modify the lp.screenBrightness value.
            if(MtkVideoFeature.isSupperDimmingSupport()) {
                lp.screenBrightness = 10 / 255.0f;
            } else {
                lp.screenBrightness = 0 / 255.0f;
            }
            mWindow.setAttributes(lp);
            mIsPowerSaving = true;
            break;
        case POWER_SAVING_MODE_NONE:
            break;
        default:
            break;
        }
    }

    private void leavePowerSavingMode() {
        MtkLog.v(TAG, "leavePowerSavingMode " + mPowerSavingMode + " mIsPowerSaving = " + mIsPowerSaving);
        if (isInExtensionMode()) {
            showController();
        }
        if (!mIsPowerSaving) {
            return;
        }
        switch(mPowerSavingMode) {
        case POWER_SAVING_MODE_OFF:
            mPowerManager.setBacklightOffForWfd(false);
            mIsPowerSaving = false;
            break;
        case POWER_SAVING_MODE_DIM:
            if (mMovieActivity instanceof MovieActivity) {
                WindowManager.LayoutParams lp = mWindow.getAttributes();
                lp.screenBrightness = mScreenBrightness;
                mWindow.setAttributes(lp);
                mIsPowerSaving = false;
            } else {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        WindowManager.LayoutParams lp = mWindow.getAttributes();
                        lp.screenBrightness = mScreenBrightness;
                        mWindow.setAttributes(lp);
                        mIsPowerSaving = false;
                    }
                });
            }
            break;
        case POWER_SAVING_MODE_NONE:
            break;
        default:
            break;
        }
    }

   /**
    * M:register wfd receiver, when wfd  not connect, cancel power saving.
    */
    private BroadcastReceiver mWfdReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            MtkLog.v(TAG, "mWfdReceiver onReceive action: " + action);
            if (action != null
                    && action.equals(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED)) {
                WifiDisplayStatus status = (WifiDisplayStatus) intent
                        .getParcelableExtra(DisplayManager.EXTRA_WIFI_DISPLAY_STATUS);
                if (status != null) {
                    int state = status.getActiveDisplayState();
                    MtkLog.v(TAG,
                            "mWfdReceiver onReceive wfd ActiveDisplayState: "
                                    + state);
                    if (state == WifiDisplayStatus.DISPLAY_STATE_NOT_CONNECTED) {
                        mIsWfdConnected = false;
                        release();
                    } else if (state == WifiDisplayStatus.DISPLAY_STATE_CONNECTED) {
                        mIsWfdConnected = true;
                        if (mVideoView.isPlaying()) {
                            startCountDown();
                        }
                    }
                }
            }
        };
    };

       public void registerReceiver() {
           MtkLog.v(TAG, "registerReceiver");
           ///M:register wfd receiver.
           IntentFilter filter = new IntentFilter(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED);
           mMovieActivity.registerReceiver(mWfdReceiver, filter);
           if (isInExtensionMode()) {
               setOnSystemUiVisibilityChangeListener();
           }
       }

       public void unregisterReceiver() {
           MtkLog.v(TAG, "unregisterReceiver");
           mMovieActivity.unregisterReceiver(mWfdReceiver);
           if (isInExtensionMode()) {
               unregisterOnSystemUiVisibilityChangeListener();
           }
       }

     private void updatePresentation() {
           // Get the current route and its presentation display.
           MediaRouter.RouteInfo route = mMediaRouter.getSelectedRoute(
                   MediaRouter.ROUTE_TYPE_LIVE_VIDEO);
           Display presentationDisplay = route != null ? route.getPresentationDisplay() : null;

           // Dismiss the current presentation if the display has changed.
           if (mPresentation != null && mPresentation.getDisplay() != presentationDisplay) {
               Log.i(TAG, "Dismissing presentation because the current route no longer "
                       + "has a presentation display.");
               mPresentation.removeSurfaceView();
               mPresentation.dismiss();
               mPresentation = null;
               ((ViewGroup) mVideoRoot).addView((View) mVideoView, 0);
               unregisterOnSystemUiVisibilityChangeListener();
               mMediaRouter.removeCallback(mMediaRouterCallback);
           }

           // Show a new presentation if needed.
           if (mPresentation == null && presentationDisplay != null) {
               Log.i(TAG, "Showing presentation on display: " + presentationDisplay);
               if (((View) mVideoView).getParent() != null) {
                   mVideoView.stopPlayback();
                   ((ViewGroup) ((View) mVideoView).getParent()).removeView((View) mVideoView);
               }
               mPresentation = new WfdPresentation(mMovieActivity, presentationDisplay, mVideoView);
               mPresentation.setOnDismissListener(mOnDismissListener);
               mMediaRouter.addCallback(MediaRouter.ROUTE_TYPE_LIVE_VIDEO, mMediaRouterCallback);
               try {
                   mPresentation.show();
               } catch (WindowManager.InvalidDisplayException ex) {
                   Log.w(TAG, "Couldn't show presentation!  Display was removed in "
                           + "the meantime.", ex);
                   mPresentation = null;
                   mMediaRouter.removeCallback(mMediaRouterCallback);
               }
           }
       }


     private final MediaRouter.SimpleCallback mMediaRouterCallback =
         new MediaRouter.SimpleCallback() {
     @Override
     public void onRouteSelected(MediaRouter router, int type, RouteInfo info) {
         Log.d(TAG, "onRouteSelected: type=" + type + ", info=" + info);
         Log.d(TAG, "mMovieActivity.isFinishing()  =" + mMovieActivity.isFinishing());
         if (mMovieActivity.isFinishing()) {
             return;
         }
         updatePresentation();
     }

     @Override
     public void onRouteUnselected(MediaRouter router, int type, RouteInfo info) {
         Log.d(TAG, "onRouteUnselected: type=" + type + ", info=" + info);
         Log.d(TAG, "mMovieActivity.isFinishing()  =" + mMovieActivity.isFinishing());
         if (mMovieActivity.isFinishing()) {
             return;
         }
         updatePresentation();
     }

     @Override
     public void onRoutePresentationDisplayChanged(MediaRouter router, RouteInfo info) {
         Log.d(TAG, "onRoutePresentationDisplayChanged: info=" + info);
         Log.d(TAG, "mMovieActivity.isFinishing()  =" + mMovieActivity.isFinishing());
         if (mMovieActivity.isFinishing()) {
             return;
         }
         updatePresentation();
     }
    };

    /**
    * Listens for when presentations are dismissed.
    */
    private final DialogInterface.OnDismissListener mOnDismissListener =
         new DialogInterface.OnDismissListener() {
     @Override
     public void onDismiss(DialogInterface dialog) {
         if (dialog == mPresentation) {
             Log.i(TAG, "Presentation was dismissed.");
             mPresentation = null;
             mMediaRouter.removeCallback(mMediaRouterCallback);
         }
     }
    };

    /**
    * The presentation to show on the secondary display.
    * <p>
    * Note that this display may have different metrics from the display on which
    * the main activity is showing so we must be careful to use the presentation's
    * own {@link Context} whenever we load resources.
    * </p>
    */
    private final  class WfdPresentation extends Presentation {
     private IMtkVideoController mSurfaceView;
     private TextView mTextView;
     private View mRootView;
     private RelativeLayout mRoot;

     public WfdPresentation(Context context, Display display) {
         super(context, display);
     }

     public WfdPresentation(Context context, Display display, IMtkVideoController surfaceview) {
         super(context, display);
         mSurfaceView = surfaceview;
     }

     @Override
     protected void onCreate(Bundle savedInstanceState) {
         // Be sure to call the super class.
         super.onCreate(savedInstanceState);
         Log.d(TAG, "WfdPresentation onCreate");
         // Inflate the layout.
         setContentView(R.layout.m_presentation_with_media_router_content);
         mRoot = (RelativeLayout) findViewById(R.id.view_root);
         RelativeLayout.LayoutParams wrapContent = new RelativeLayout.LayoutParams(
                 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                 wrapContent.addRule(RelativeLayout.CENTER_IN_PARENT);
         mRoot.addView((View) mSurfaceView, wrapContent);
     }

     @Override
     protected void onStart() {

     }

     public void removeSurfaceView() {
         mSurfaceView.stopPlayback();
        ((ViewGroup) mRoot).removeView((View) mSurfaceView);
     }
    }
}
