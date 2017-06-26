package com.mediatek.multiwindow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.content.res.Configuration;
import android.content.pm.ActivityInfo;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.app.ActivityManager;

import java.lang.reflect.Method;

import com.mediatek.sef.proxy.FeatureProxyBase;
import com.mediatek.common.multiwindow.IMWAmsCallback;
import com.mediatek.common.multiwindow.IMWPhoneWindowCallback;
import com.mediatek.common.multiwindow.IMultiWindowManager;
import com.mediatek.common.multiwindow.IMWWmsCallback;
import com.mediatek.common.multiwindow.IMWSystemUiCallback;

/**
 * Represents the local MultiWindow Proxy. Provides access to the feature on the
 * device. Applications can use the methods in this class to determine feature
 * API version, as well as to check if the feature is available on device.
 * <p>
 * Use the helper {@link #getInstance()} to get the MultiWindow Proxy. For
 * example:
 * 
 * <pre>
 * MultiWindowProxy mProxy = MultiWindowProxy.getInstance();
 * if (mProxy)
 *     return; // MultiWindow not available on this device
 * </pre>
 * 
 * User application can call {@link #getFloatingState()} to check whether the
 * application is in floating state. For example:
 * 
 * <pre>
 * boolean isFloating = mProxy.getFloatingState();
 * </pre>
 */
public final class MultiWindowProxy extends FeatureProxyBase {
    static final String TAG = "MultiWindow";
    private static final boolean DBG = false;

    private static final String MULTIWINDOW_SERVICE = "multiwindow_service_v1";

    private Context mContext;

    // Default MultiWindow service link
    private static IMultiWindowManager sDefaultService;
    // MultiWindow service link
    IMultiWindowManager mService;

    public static MultiWindowProxy sInstance;
    public static int sFeatureProperty = getFeatureProperty();

    // Add for float decor
    private ViewGroup mActionView;
    private ImageView mStickView;
    private ImageView mMaximumView;
    private boolean mSticked = false;
    private boolean mIsTranslucent;

    // Window type
    private int mWindowType = 0;
    public static final int NOT_FLOATING_WINDOW = 0;
    public static final int FLOATING_WINDOW_FULL = 1;
    public static final int FLOATING_WINDOW_DIALOG = 2;

    // Error handling result
    public static final int ERR_HANDLING_NONE = 0;
    public static final int ERR_HANDLING_CONFIG_NOT_CHANGE = 1;
    public static final int ERR_HANDLING_MINIMAX_RESTART_APP = 2;
    public static final int ERR_HANDLING_DISABLE_FLOAT = 3;

    // Locker
    private static final Object sLock = new Object();

    /**
     * Construction function for Feature Class.
     */
    private MultiWindowProxy() throws IOException {
        IBinder binder = getService(MULTIWINDOW_SERVICE);
        if (binder == null)
            throw new IOException();
        
        if (false)
            Log.d(TAG, "MultiWindowProxy constructor.");
        sDefaultService = IMultiWindowManager.Stub.asInterface(binder);
    }

    /**
     * Helpers to get the default MultiWindow Proxy.
     */
    public static MultiWindowProxy getInstance() {
        synchronized (sLock) {
            try {
                if (sInstance == null) {
                    sInstance = new MultiWindowProxy();
                    if (sDefaultService == null)
                        return null;
                    sInstance.mService = sDefaultService;
                }
            } catch (IOException e) {
                // Empty
            }
            return sInstance;
        }

    }

    // Remote API wrapper //
    /**
     * Returns the current API version on platform.
     * <p>
     * 
     * @return the API version string on platform
     */
    public String getPlatformApiVersion() throws IOException {
        return MULTIWINDOW_SERVICE;
        /*
         * try { return mIFeatureApi.getPlatformApiVersion(); } catch
         * (RemoteException e) { e.printStackTrace(); throw new
         * IOException("remote exception"); }
         */
    }

    // API extension //
    /**
     * Returns true if feature support on device
     * <p>
     * 
     * @return true - if feature support on device, false - feature not support
     *         on device
     */
    public static boolean isFeatureSupport() {
        if (sFeatureProperty == 1) {
            return true;
        } else {
            return false;
        }
    }

    public static int getFeatureProperty() {
        try {
            Class SystemProperties = Class
                    .forName("android.os.SystemProperties");
            Class[] paramTypes = new Class[2];
            paramTypes[0] = String.class;
            paramTypes[1] = int.class;

            Method getInt = SystemProperties.getMethod("getInt", paramTypes);
            Object[] params = new Object[2];
            params[0] = new String("persist.sys.multiwindow");
            params[1] = new Integer(0);
            int ret = (Integer) getInt.invoke(SystemProperties, params);
            return ret;
        } catch (Exception e) {
            Log.w(TAG, "getFeatureProperty error!" + e);
            return 0;
        }
    }

    // When running the floating window, the user application must call the
    // function.
    public void enableFeature() {
    }

    // Return the feature of Multi Window is enabled or not.
    public boolean isFeatureEnabled() {
        if (sDefaultService != null && sFeatureProperty == 1)
            return true;
        return false;
    }

    /**
     * User application need call this function to check whether the application
     * is in floating state
     */
    public boolean getFloatingState() {
        return mFloating;
    }

    /**
     * Set the Callback interface for MultiWindowService. Called by AMS.
     * 
     * @param cb The desired Callback interface.
     * @hide
     */
    public void setAMSCallback(IMWAmsCallback cb) {
        try {
            mService.setAMSCallback(cb);
        } catch (RemoteException e) {
            // Empty
        }
    }

    /**
     * Set the Callback interface for MultiWindowService. Called by WMS.
     *
     * @param cb The desired Callback interface.
     * @hide
     */
    public void setWMSCallback(IMWWmsCallback cb) {
        try {
            mService.setWMSCallback(cb);
        } catch (RemoteException e) {
            // Empty
        }
    }

    /**
     * Set the Callback interface for MultiWindowService. Called by SystemUI.
     *
     * @param cb The desired Callback interface.
     * @hide
     */
    public void setSystemUiCallback(IMWSystemUiCallback cb) {
        try {
            mService.setSystemUiCallback(cb);
        } catch (RemoteException e) {
            // Empty
        }
    }
    
    public void setPhoneWindowCallback(IMWPhoneWindowCallback cb) {
        mPhoneWindowCb = cb;
    } 

    /**
     * Close the window by the binder token. Called by PhoneWindow.
     *
     * @param token The Binder token referencing the Activity we want to close.
     * @hide
     */
    public void closeWindow(IBinder token) {
        try {
            mService.closeWindow(token);
        } catch (RemoteException e) {
            // Empty
        }
    }

    /**
     * Restore or max the window by the binder token. Called by PhoneWindow
     * 
     * @param token The Binder token referencing the Activity we want to
     *            restore.
     * @param toMax If true we will switch window from floating mode to normal
     *            mode.
     * @hide
     */
    public void restoreWindow(IBinder token, boolean toMax) {
        try {
            if (toMax) {
                mService.restoreWindow(token, toMax);
            } else {
                mService.restoreWindow(null, toMax);
            }
        } catch (RemoteException e) {
            // Empty
        }
    }

    /**
     * Stick the window by the binder token. Called by PhoneWindow.
     * 
     * @param token The Binder token referencing the Activity we want to stick.
     * @param isSticky If true, keep in top.
     * @hide
     */
    public void stickWindow(IBinder token, boolean isSticky) {
        try {
            mService.stickWindow(token, isSticky);
        } catch (RemoteException e) {
            // Empty
        }
    }

    /**
     * Decide the visibility of the multi window decoration (ex. control bar).
     * Called by Activity.makeVisible().
     * 
     * @param token The Binder token referencing the Activity.
     * @param visibility .
     * @hide
     */
    public void setFloatDecorVisibility(IBinder token, int visibility) {
        if (mPhoneWindowCb != null) {
            try {
                if (DBG)
                    Log.v(TAG, "setFloatDecorVisibility token = " + token);
                for (int index = 0; index < mWindowsInfoList.size(); index++) {
                    WindowsInfo windowsInfo = mWindowsInfoList.get(index);
                    if (windowsInfo.token == token) {
                        Log.v(TAG, "setFloatDecorVisibility matched");
                        windowsInfo.phoneWindowCb
                                .setFloatDecorVisibility(visibility);
                        break;
                    }
                }
            } catch (RemoteException e) {
                // Empty
            }
        }
    }

    /**
     * Decide the window type by the binder token.
     *
     * @param token We use token to identify this activity.
     * @param windowType Should be one of {@link #FLOATING_WINDOW_DIALOG},
     *            {@link #FLOATING_WINDOW_FULL}, {@link #NOT_FLOATING_WINDOW}.
     * @hide
     */
    public void setWindowType(IBinder token, int windowType) {
        if (windowType == FLOATING_WINDOW_DIALOG) {   
            if (mPhoneWindowCb != null) {
                try {
                    mPhoneWindowCb.setWindowType(null, windowType);
                } catch (RemoteException e) {
                    // Empty
                }
            }
            return;
        }
        if (windowType == NOT_FLOATING_WINDOW) {
            mFloating = false;
            return;
        }
        mWindowType = windowType;
        if (DBG)
            Log.w(TAG, "setWindowType token = " + token);
        for (int index = 0; index < mWindowsInfoList.size(); index++) {
            WindowsInfo tempWindowsInfo = mWindowsInfoList.get(index);
            if (tempWindowsInfo.token == token) {
                Log.v(TAG, "setWindowType matched,and remove!");
                mWindowsInfoList.remove(tempWindowsInfo);
                break;
            }
        }
        WindowsInfo windowsInfo = new WindowsInfo(token, mPhoneWindowCb);
        mWindowsInfoList.add(windowsInfo);
        mFloating = true;
        if (mPhoneWindowCb != null) {
            try {
                mPhoneWindowCb.setWindowType(token, windowType);
            } catch (RemoteException e) {
                // Empty
            }
        }
    }

    /**
     * Return the window type of the token
     * 
     * @param token Activity token.
     * @hide
     */
    public int getWindowType(IBinder token) {
        return 1;
    }

    /**
     * Return the modified Intent from the input Intent
     *
     * @hide
     */
    public Intent adjustWindowIntent(Intent intent) {
        if (isFeatureEnabled()) {
            // intent.addFlags(Intent.FLAG_ACTIVITY_FLOATING);
            intent.addFlags(0x00000200);
        }
        return intent;
    }

    /**
     * Return the modified config from the input Config. Called by
     * Activity.attach().
     * <p>
     * App should load different resources to fit the floating window's size.
     * But this maybe results in JE when restore/max window, or start a 
     * floating window from extrance button.
     * 
     * @param config The orignal configuration.
     * @param info Activity to get search information from.
     * @param packageName PackageName used to decide that if it can change
     *            config.
     * @return Return new config for app to load different resources.
     * @hide
     */
    public Configuration adjustActivityConfig(Configuration config,
            ActivityInfo info, String packageName) {
        if (isFeatureEnabled()) {
            int widthDp, heightDp;
            widthDp = config.screenWidthDp;
            heightDp = config.screenHeightDp;

            config.screenLayout = Configuration.SCREENLAYOUT_SIZE_NORMAL;
            config.smallestScreenWidthDp = config.smallestScreenWidthDp / 2;
            if (info.screenOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    || info.screenOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                config.orientation = Configuration.ORIENTATION_LANDSCAPE;
                if (widthDp < heightDp) {
                    config.screenWidthDp = heightDp / 2;
                    config.screenHeightDp = widthDp / 2;
                } else {
                    config.screenWidthDp = widthDp / 2;
                    config.screenHeightDp = heightDp / 2;
                }
                // config.screenWidthDp = 533;
                // config.screenHeightDp = 320;
            } else {
                config.orientation = Configuration.ORIENTATION_PORTRAIT;
                if (widthDp < heightDp) {
                    config.screenWidthDp = widthDp / 2;
                    config.screenHeightDp = heightDp / 2;
                } else {
                    config.screenWidthDp = heightDp / 2;
                    config.screenHeightDp = widthDp / 2;
                }
                // config.screenWidthDp = 320;
                // config.screenHeightDp = 533;
            }

            Log.v(TAG, "adjustActivityConfig, apply override config="
                    + config);
            // applyOverrideConfiguration(config);
        }
        return config;
    }

    public boolean needChangeConfig(String packageName) {
        if (DEFAULT_CHANGE_CONFIG) {
            return !matchConfigNotChangeList(packageName);
        } else {
            return matchConfigChangeList(packageName);
        }
    }

    /**
     * Some APP can not change the config, so we keep them in the list, and not
     * change the config
     */
    public boolean matchConfigNotChangeList(String packageName) {
        try {
            return mService.matchConfigNotChangeList(packageName);
        } catch (Exception e) {
            Log.e(TAG, "matchConfigNotChangeList" + e);
        }
        return false;
    }

    /**
     * Check if the package can be floating mode by querying the black list.
     */
    public boolean matchDisableFloatPkgList(String packageName) {
        try {
            return mService.matchDisableFloatPkgList(packageName);
        } catch (Exception e) {
            Log.e(TAG, "matchDisableFloatPkgList" + e);
        }
        return false;
    }

    /**
     * Check if the component can be floating mode by querying the black list.
     */
    public boolean matchDisableFloatActivityList(String ActivityName) {
        try {
            return mService.matchDisableFloatActivityList(ActivityName);
        } catch (Exception e) {
            Log.e(TAG, "matchDisableFloatActivityList" + e);
        }
        return false;
    }

    /**
     * Check if the window can be floating mode by querying the black list.
     */
    public boolean matchDisableFloatWinList(String winName) {
        try {
            return mService.matchDisableFloatWinList(winName);
        } catch (Exception e) {
            Log.e(TAG, "matchDisableFloatWinList" + e);
        }
        return false;
    }

    /**
     * Check if the package or the win need to hide restore button. Called by
     * WindowManagerService
     *
     * @hide
     */
    public boolean needHideRestoreButton(String packageName, String winName) {
        try {
            if (mService.matchDisableFloatPkgList(packageName))
                return true;
            return mService.matchDisableFloatWinList(winName);
        } catch (Exception e) {
            Log.e(TAG, "needHideRestoreButton" + e);
        }
        return false;
    }

    /**
     * Return a list of the package name that are not allowed to be floating
     * mode.
     */
    public List<String> getDisableFloatPkgList() {
        try {
            return mService.getDisableFloatPkgList();
        } catch (Exception e) {
            Log.e(TAG, "getDisableFloatPkgList" + e);
        }
        return null;
    }

    /**
     * Return a list of the Component name that are not allowed to be floating
     * mode.
     */
    public List<String> getDisableFloatComponentList() {
        try {
            return mService.getDisableFloatComponentList();
        } catch (Exception e) {
            Log.e(TAG, "getDisableFloatComponentList" + e);
        }
        return null;
    }

    /**
     * Check if need to restart apps when doing Restore and Max by querying the
     * black list. Called by AMS.
     */
    public boolean matchMinimaxRestartList(String packageName) {
        try {
            return mService.matchMinimaxRestartList(packageName);
        } catch (Exception e) {
            Log.e(TAG, "matchMinimaxRestartList" + e);
        }
        return false;
    }

    /**
     * Some APP should change the config, so we keep they in the list, and 
     * change the config
     */
    public boolean matchConfigChangeList(String packageName) {
        try {
            return mService.matchConfigChangeList(packageName);
        } catch (Exception e) {
            Log.e(TAG, "matchConfigChangeList" + e);
        }
        return false;
    }

    public void addDisableFloatPkg(String packageName){
        try {
            mService.addDisableFloatPkg(packageName);
        } catch (RemoteException e) {
            // Empty
        }
    }
    
    public void addConfigNotChangePkg(String packageName){
        try {
            mService.addConfigNotChangePkg(packageName);
        } catch (RemoteException e) {
            // Empty
        }
    }
    
    public void addMiniMaxRestartPkg(String packageName){
        try {
            mService.addMiniMaxRestartPkg(packageName);
        } catch (RemoteException e) {
            // Empty
        }
    }

    public int appErrorHandling(String packageName, boolean inMaxOrRestore) {
        try {
            return mService.appErrorHandling(packageName, inMaxOrRestore, DEFAULT_CHANGE_CONFIG);
        } catch (RemoteException e) {
            // Empty
        }
        return ERR_HANDLING_NONE;
    }
    
    /**
     * Move the floating window to the front Called by
     * Activity.dispatchTouchEvent()
     *
     * @param token The Binder token referencing the Activity we want to move.
     * @hide
     */
    public void moveActivityTaskToFront(IBinder token) {
        try {
            mService.moveActivityTaskToFront(token);
        } catch (RemoteException e) {
            // Empty
        }
    }

    /**
     * Check whether the stack is floating.
     *
     * @hide
     */
    public boolean isFloatingStack(int stackId) {
        try {
            return mService.isFloatingStack(stackId);
        } catch (RemoteException e) {
            // Empty
        }
        return false;
    }

    /**
     * Tell MWS that the stack is floating.
     *
     * @hide
     */
    public void setFloatingStack(int stackId) {
        try {
            mService.setFloatingStack(stackId);
        } catch (RemoteException e) {
            // Empty
        }
    }

    /**
     * Return true if the intent contains FLAG_ACTIVITY_FLOATING.
     *
     * @hide
     */
    public boolean isFloatingWindow(Intent intent) {
        if ((intent.getFlags() & 0x00000200) != 0) {
            return true;
        }
        return false;
    }

    /**
     * Check if the activity associated with the token is sticky.
     *
     * @hide
     */
    public boolean isSticky(IBinder token) {
        try {
            return mService.isSticky(token);
        } catch (RemoteException e) {
            // Empty
        }
        return false;
    }

    /**
     * Check if the given stack is sticky.
     *
     * @hide
     */
    public boolean isStickStack(int stackId) {
        try {
            return mService.isStickStack(stackId);
        } catch (RemoteException e) {
            // Empty
        }
        return true;
    }

    /**
     * Check if the given task is in mini/max status.
     *
     * @hide
     */
    public boolean isInMiniMax(int taskId) {
        try {
            return mService.isInMiniMax(taskId);
        } catch (RemoteException e) {
            // Empty
        }
        return false;
    }

    /**
     * Tell MWS that this task need to be mini/max status.
     *
     * @hide
     */
    public void miniMaxTask(int taskId) {
        try {
            mService.miniMaxTask(taskId);
        } catch (RemoteException e) {
            // Empty
        }
    }

    /**
     * Called by Window Manager Policy to move Floating Window
     *
     * @hide
     */
    public void moveFloatingWindow(int disX, int disY) {
        try {
            mService.moveFloatingWindow(disX, disY);
        } catch (RemoteException e) {
            // Empty
        }
    }

    /**
     * Called by Window Manager Policy to resize Floating Window
     *
     * @hide
     */
    public void resizeFloatingWindow(int direction, int deltaX, int deltaY) {
        try {
            mService.resizeFloatingWindow(direction, deltaX, deltaY);
        } catch (RemoteException e) {
            // Empty
        }
    }

    /**
     * Called by Window Manager Policy to enable Focus Frame
     *
     * @hide
     */
    public void enableFocusedFrame(boolean enable) {
        try {
            mService.enableFocusedFrame(enable);
        } catch (RemoteException e) {
            // Empty
        }
    }

    /**
     * Called by WindowManagerService to contorl restore button on systemUI
     * module.
     *
     * @hide
     */
    public void showRestoreButton(boolean flag) {
        try {
            mService.showRestoreButton(flag);
        } catch (RemoteException e) {
            // Empty
        }
    }

    /**
     * Called by ActivityThread to Tell the MultiWindowService we have created.
     * MultiWindowService need to do somethings for the activity association
     * with the token at this point.
     * 
     * @param token The Binder token referencing the Activity that has created
     * @hide
     */
    public void activityCreated(IBinder token) {
        try {
            mService.activityCreated(token);
        } catch (RemoteException e) {
            // Empty
        }
    }

    /**
     * Called by ActivityManagerService when task has been added.
     * MultiWindowService need to maintain the all running task infos, so, we
     * should synchronize task infos for it.
     *
     * @param taskId Unique ID of the added task.
     * @hide
     */
    public void taskAdded(int taskId) {
        try {
            mService.taskAdded(taskId);
        } catch (RemoteException e) {
            // Empty
        }
    }

    /*
     * Called by ActivityManagerService when task has been removed.
     * MultiWindowService need to maintain the all running task infos, so, we
     * should synchronize task infos for it.
     *
     * @param taskId Unique ID of the removed task.
     * @hide
     */
    public void taskRemoved(int taskId) {
        try {
            mService.taskRemoved(taskId);
        } catch (RemoteException e) {
            // Empty
        }
    }

    /*
     * Default return true to add black background for some stacks, to fix some UI 
     * transparent issue for floating window. If wish to disable it, change to return true.
     *
     * <p>In some cases, apps(such as, Gallery2) has no background color, or has a
     * transparent color (such as, a SurfaceView punches a hole in its window to allow
     * its surface to be displayed). If there is nothing to show behind the window, 
     * the background will be filled with black by the underlying graphic module.
     * 
     * <p>But, in Floating mode, the underlying graphic module will not fill black color
     * for it, because there is full-screen window behind the flaoting windows. 
     *
     * <p>This solution maybe lead to create more layers that need to be composed,
     * and then consume more power energy. 
     *
     * <note> Add this interface for customization.
     */
    public boolean isStackBackgroundEnabled() {
        return true;
    }

    /*
     * Default return false to disable this solution. If need, change to return true.
     *
     * <p>If there is a floating window behind the fullscreen window, and the fullscreen
     * window has transparent regions, then the floating window need to be composed 
     * together, so it maybe displayed on the screen.
     * 
     * <p>The solution is to add black background color for the window, then it will has
     * no transparent regions.
     * 
     * <note> Add this interface for customization.
     */
    public static boolean isWindowBackgroundEnabled() {
        return false;
    }



    public boolean isAppErrorHandlingEnabled() {
        return isAppErrorHandlingEnabled;
    }

    
    static int getIntProperty(String property, int defaultValue) {
        try {
            Class SystemProperties = Class
                    .forName("android.os.SystemProperties");
            Class[] paramTypes = new Class[2];
            paramTypes[0] = String.class;
            paramTypes[1] = int.class;

            Method getInt = SystemProperties.getMethod("getInt", paramTypes);
            Object[] params = new Object[2];
            params[0] = new String(property);
            params[1] = new Integer(defaultValue);
            int ret = (Integer) getInt.invoke(SystemProperties, params);
            return ret;
        } catch (Exception e) {
            Log.w(TAG, "getIntProperty error!" + e);
            return defaultValue;
        }
    }

    static boolean getBooleanProperty(String property, boolean defaultValue) {
        try {
            Class SystemProperties = Class
                    .forName("android.os.SystemProperties");
            Class[] paramTypes = new Class[2];
            paramTypes[0] = String.class;
            paramTypes[1] = boolean.class;

            Method getBoolean = SystemProperties.getMethod("getBoolean", paramTypes);
            Object[] params = new Object[2];
            params[0] = new String(property);
            params[1] = new Boolean(defaultValue);
            boolean ret = (Boolean) getBoolean.invoke(SystemProperties, params);
            return ret;
        } catch (Exception e) {
            Log.w(TAG, "getBooleanProperty error!" + e);
            return defaultValue;
        }
    }
    
    private boolean isAppErrorHandlingEnabled = 
                getBooleanProperty("debug.mw.apperrhandling", false);

    private static boolean DEFAULT_CHANGE_CONFIG = 
                getBooleanProperty("debug.mw.changeconfig", true);
    
    private IMWAmsCallback mAMSCb;
    private IMWAmsCallback mASSCb;
    private IMWPhoneWindowCallback mPhoneWindowCb;
    private boolean mFloating = false;

    // For match window token and phonewindow callback
    public class WindowsInfo {
        public IBinder token;
        public IMWPhoneWindowCallback phoneWindowCb;

        public WindowsInfo(IBinder iBinder,
                IMWPhoneWindowCallback iMWPhoneWindowCallback) {
            token = iBinder;
            phoneWindowCb = iMWPhoneWindowCallback;
        }
    }

    private ArrayList<WindowsInfo> mWindowsInfoList = new ArrayList<WindowsInfo>();

}
