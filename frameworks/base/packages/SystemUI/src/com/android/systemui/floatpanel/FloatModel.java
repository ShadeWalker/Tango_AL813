package com.android.systemui.floatpanel;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import com.android.systemui.floatpanel.FloatPanelView;
import com.android.systemui.floatpanel.FloatWindowProvider;
import com.mediatek.xlog.Xlog;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import java.util.Collections;
import java.util.Comparator;

///M:For Multi window 
import com.mediatek.multiwindow.MultiWindowProxy;
///@}

/**
 * M: Add for receive package change and update data bases for multi-window entrance.
 */
public class FloatModel extends BroadcastReceiver {
    protected static final int RESIDENT_CONTAINER = 1;
    protected static final int EXTENT_CONTAINER = 2;

    private static final String TAG = "FloatModel";
    private static final boolean DEBUG = true;
    private static final Uri CONTENT_URI = Uri.parse("content://"
            + FloatWindowProvider.AUTHORITY + "/"
            + FloatWindowProvider.TABLE_NAME + "?"
            + FloatWindowProvider.PARAMETER_NOTIFY + "=true");
    private static final HandlerThread sFloatThread = new HandlerThread(
            "float-loader");
    static {
        sFloatThread.start();
    }
    private static final Handler sFloatHandler = new Handler(
            sFloatThread.getLooper());

    private static Context mContext;
    private static FloatPanelView mFloatPanelView;
    private static FloatWindowProvider mFloatWindowProvider;
    private static List<FloatAppItem> mResidentAppList;
    private static List<FloatAppItem> mExtentAppList;

    private List<FloatAppItem> mAddedList = new ArrayList<FloatAppItem>();
    private List<FloatAppItem> mDeletedList = new ArrayList<FloatAppItem>();
    private List<FloatAppItem> mModifiedList = new ArrayList<FloatAppItem>();
    private Handler mUiHandler = new Handler();

     ///M:For Multi window
     final public static String ACTION_DISABLE_PKG_UPDATED = "action_multiwindow_disable_pkg_updated";
     ///@}

    public FloatModel() {
    }

    public FloatModel(FloatPanelView floatPanelView) {
        if (mFloatWindowProvider == null) {
            mFloatWindowProvider = new FloatWindowProvider();
        }
        mFloatPanelView = floatPanelView;
        mContext = mFloatPanelView.getContext();
    }


    /**
     * Add item to modified list if it is first edited, or else modify the
     * information of the item instead.
     *
     * @param item
     */
    protected void addItemToModifyListIfNeeded(FloatAppItem item) {
        final int size = mModifiedList.size();
        FloatAppItem appItem;
        int i;
        for (i = 0; i < size; i++) {
            appItem = mModifiedList.get(i);
            if (appItem.className.equals(item.className)) {
                appItem.position = item.position;
                appItem.container = item.container;
                break;
            }
        }

        // The item is first edited.
        if (i == size) {
            mModifiedList.add(item);
        }
    }

    /**
     * Commit all modified items in the list to database, the moidfied list
     * contains all items with position or container change since the last
     * commit.
     */
    protected void commitModify() {
        for (int i = 0, size = mModifiedList.size(); i < size; i++) {
            FloatAppItem item = mModifiedList.get(i);
            if (DEBUG) {
                Xlog.d(TAG, "Modify item: i = " + i + ", position = "
                        + item.position + ", container = " + item.container
                        + ", className = " + item.className);
            }
            modifyItemToDatabase(item);
        }
        mModifiedList.clear();
    }

    protected void modifyItemToDatabase(FloatAppItem item) {
        final ContentResolver contentResolver = mContext.getContentResolver();
        final ContentValues values = new ContentValues();

        final String componentName = "ComponentInfo{" + item.packageName + "/"
                + item.className + "}";
        values.put(FloatWindowProvider.FLOAT_POSITION, item.position);
        values.put(FloatWindowProvider.FLOAT_CONTAINER, item.container);

        if (DEBUG) {
            Xlog.d(TAG, "modifyItemToDatabase: componentName = "
                    + componentName + ", position = " + item.position
                    + ", floatContainer = " + item.container);
        }
        Runnable r = new Runnable() {
            public void run() {
                contentResolver.update(CONTENT_URI, values,
                        FloatWindowProvider.COMPONENT_NAME + " = ?",
                        new String[] { componentName });
            }
        };
        runOnWorkerThread(r);
    }

    protected void deleteItemToDatabase(FloatAppItem item) {
        final ContentResolver contentResolver = mContext.getContentResolver();
        final String componentName = "ComponentInfo{" + item.packageName + "/"
                + item.className + "}";

        Runnable r = new Runnable() {
            public void run() {
                contentResolver.delete(CONTENT_URI,
                        FloatWindowProvider.COMPONENT_NAME + " = ?",
                        new String[] { componentName });
            }
        };
        runOnWorkerThread(r);
        if (DEBUG) {
            Xlog.d(TAG, "deleteItemToDatabase. componentName = "
                    + componentName);
        }
    }

    protected void addItemToDatabase(FloatAppItem item) {
        final ContentResolver contentResolver = mContext.getContentResolver();
        final ContentValues values = new ContentValues();

        final String componentName = "ComponentInfo{" + item.packageName + "/"
                + item.className + "}";
        final Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(item.packageName, item.className));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        values.put(FloatWindowProvider.FLOAT_ID,
                mFloatWindowProvider.generateNewId());
        values.put(FloatWindowProvider.COMPONENT_NAME, componentName);
        values.put(FloatWindowProvider.FLOAT_CONTAINER, item.container);
        values.put(FloatWindowProvider.FLOAT_POSITION, item.position);
        values.put(FloatWindowProvider.FLOAT_INTENT, intent.toUri(0));

        Runnable r = new Runnable() {
            public void run() {
                contentResolver.insert(CONTENT_URI, values);
            }
        };
        runOnWorkerThread(r);

        if (DEBUG) {
            Xlog.d(TAG, "addItemToDatabase. componentName = " + componentName);
        }
    }

    private void runOnMainThread(Runnable r) {
        if (sFloatThread.getThreadId() == Process.myTid()) {
            // If we are on the worker thread, post onto the main handler
            mUiHandler.post(r);
        } else {
            r.run();
        }
    }

    /**
     * Runs the specified runnable immediately if called from the worker thread,
     * otherwise it is posted on the worker thread handler.
     */
    private static void runOnWorkerThread(Runnable r) {
        if (sFloatThread.getThreadId() == Process.myTid()) {
            r.run();
        } else {
            // If we are not on worker thread, post to the worker handler
            sFloatHandler.post(r);
        }
    }

    protected List<FloatAppItem> getFloatApps() {
        if (mResidentAppList == null) {
            loadAllApps();
        }
        return mResidentAppList;
    }

    protected List<FloatAppItem> getEditApps() {
        if (mExtentAppList == null) {
            loadAllApps();
        }
        return mExtentAppList;
    }

    private void loadAllApps() {
        final ContentResolver contentResolver = mContext.getContentResolver();
        final PackageManager packageManager = mContext.getPackageManager();
        final boolean loadDefault = mFloatWindowProvider
                .loadDefaultAllAppsIfNecessary(mContext);

        final Cursor cursor = contentResolver.query(CONTENT_URI, null, null,
                null, null);
		  Xlog.d(TAG, ",loadAllApps, cursor  : " + cursor);
      //  Xlog.d(TAG, ",loadAllApps, cursor count is : " + cursor.getCount());
        if (cursor != null) {
            try {
                final int componentNameIndex = cursor
                        .getColumnIndexOrThrow(FloatWindowProvider.COMPONENT_NAME);
                final int intentIndex = cursor
                        .getColumnIndexOrThrow(FloatWindowProvider.FLOAT_INTENT);
                final int positionIndex = cursor
                        .getColumnIndexOrThrow(FloatWindowProvider.FLOAT_POSITION);
                final int floatContainerIndex = cursor
                        .getColumnIndexOrThrow(FloatWindowProvider.FLOAT_CONTAINER);

                String intentDescription = null;
                Intent intent = new Intent();
                int floatPosition = 0;
                int floatContainer = 0;

                mResidentAppList = new ArrayList<FloatAppItem>();
                mExtentAppList = new ArrayList<FloatAppItem>();

                while (cursor.moveToNext()) {
                    intentDescription = cursor.getString(intentIndex);
                    String componentName = cursor.getString(componentNameIndex);
                    if (DEBUG) {
                        Xlog.d(TAG, "componentName :" + componentName);
                    }
                    try {
                        intent = Intent.parseUri(intentDescription, 0);
                    } catch (URISyntaxException e) {
                        Xlog.w(TAG, "loadAllApps, parse Intent Uri error: "
                                + intentDescription);
                        continue;
                    }

                    final ResolveInfo resolveInfo = packageManager
                            .resolveActivity(intent, 0);
                    if (intent != null) {
                        floatContainer = cursor.getInt(floatContainerIndex);
                        floatPosition = cursor.getInt(positionIndex);
                        if (floatContainer == RESIDENT_CONTAINER) {
                            mResidentAppList.add(new FloatAppItem(
                                    packageManager, resolveInfo, null,
                                    floatPosition));
                        } else if (floatContainer == EXTENT_CONTAINER) {
                            mExtentAppList.add(new FloatAppItem(packageManager,
                                    resolveInfo, null, floatPosition));
                        }
                    }
                }
                //re-position these apps according to the postion stored  ALPS01471909
                Collections.sort(mResidentAppList, new Comparator<FloatAppItem>() {
				  public int compare(FloatAppItem f1, FloatAppItem f2) {
                       return (int)(f1.position - f2.position);
                       }
                    });

                Collections.sort(mExtentAppList, new Comparator<FloatAppItem>() {
				  public int compare(FloatAppItem f1, FloatAppItem f2) {
                       return (int)(f1.position - f2.position);
                       }
                    });
            } catch (Exception e) {
             
            } finally {
                cursor.close();
            }
            Xlog.d("TAG", "Load all apps done");
        }
    }

    /**
     * Call from the handler for ACTION_PACKAGE_ADDED, ACTION_PACKAGE_REMOVED
     * and ACTION_PACKAGE_CHANGED.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (DEBUG) {
            Xlog.d(TAG, "onReceive: intent = " + intent);
        }
        mContext = context;
        if (mFloatWindowProvider == null) {
            mFloatWindowProvider = new FloatWindowProvider();
        }

        if (mResidentAppList == null && mExtentAppList == null) {
            mResidentAppList = getFloatApps();
            mExtentAppList = getEditApps();
        }

        final String action = intent.getAction();

        if (Intent.ACTION_PACKAGE_CHANGED.equals(action)
                || Intent.ACTION_PACKAGE_REMOVED.equals(action)
                || Intent.ACTION_PACKAGE_ADDED.equals(action)) {
            if(intent.getData() == null) return;

            final String packageName = intent.getData().getSchemeSpecificPart();
            final boolean replacing = intent.getBooleanExtra(
                    Intent.EXTRA_REPLACING, false);

            int op = PackageUpdatedTask.OP_NONE;

            if (packageName == null || packageName.length() == 0) {
                // they sent us a bad intent
                return;
            }

            if (Intent.ACTION_PACKAGE_CHANGED.equals(action)) {
                op = PackageUpdatedTask.OP_UPDATE;
            } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                if (!replacing) {
                    op = PackageUpdatedTask.OP_REMOVE;
                }
                // else, we are replacing the package, so a PACKAGE_ADDED will
                // be sent
                // later, we will update the package at this time
            } else if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                if (!replacing) {
                    op = PackageUpdatedTask.OP_ADD;
                } else {
                    op = PackageUpdatedTask.OP_UPDATE;
                }
            }

            if (op != PackageUpdatedTask.OP_NONE) {
                enqueuePackageUpdated(new PackageUpdatedTask(op,
                        new String[] { packageName }));
            }

        } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE.equals(action)) {
            // First, schedule to add these apps back in.
            String[] packages = intent
                    .getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
            enqueuePackageUpdated(new PackageUpdatedTask(
                    PackageUpdatedTask.OP_ADD, packages));
        } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE
                .equals(action)) {
            String[] packages = intent
                    .getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
            enqueuePackageUpdated(new PackageUpdatedTask(
                    PackageUpdatedTask.OP_UNAVAILABLE, packages));
        //Add for the language change in settings.
        } else if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            Runnable r = new Runnable() {
                public void run() {
                    loadAllApps();
                }
            };
            runOnWorkerThread(r);

            if (mFloatPanelView != null) {
                runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        mFloatPanelView.refreshUI();
                        Xlog.d(TAG, "mFloatPanelView refreshUI");
                    }
                });
            }
        }else if(ACTION_DISABLE_PKG_UPDATED.equals(action)){
                    
		    String packageName = intent.getStringExtra("packageName");
	            if (packageName == null || packageName.length() == 0) {
	                // they sent us a bad intent
	                return;
	            }		
		    Xlog.d(TAG, "DISABLE_PKG_NAME="+packageName);
		    enqueuePackageUpdated(new PackageUpdatedTask(PackageUpdatedTask.OP_REMOVE,
                        new String[] { packageName }));
        	}
    }

    void enqueuePackageUpdated(PackageUpdatedTask task) {
        sFloatHandler.post(task);
    }

    private class PackageUpdatedTask implements Runnable {
        int mOp;
        String[] mPackages;

        public static final int OP_NONE = 0;
        public static final int OP_ADD = 1;
        public static final int OP_UPDATE = 2;
        public static final int OP_REMOVE = 3;
        public static final int OP_UNAVAILABLE = 4;

        public PackageUpdatedTask(int op, String[] packages) {
            mOp = op;
            mPackages = packages;
        }

        public void run() {
            final Context context = mContext;

            final String[] packages = mPackages;
            final int N = packages.length;
            switch (mOp) {
            case OP_ADD:
                for (int i = 0; i < N; i++) {
                    if (DEBUG)
                        Xlog.d(TAG, "addPackage " + packages[i]);
                    addPackage(mContext, packages[i]);
                }
                break;
            case OP_UPDATE:
                for (int i = 0; i < N; i++) {
                    if (DEBUG)
                        Xlog.d(TAG, "updatePackage " + packages[i]);
                    updatePackage(mContext, packages[i]);
                }
                break;
            case OP_REMOVE:
            case OP_UNAVAILABLE:
                for (int i = 0; i < N; i++) {
                    if (DEBUG)
                        Xlog.d(TAG, "removePackage " + packages[i]);
                    removePackage(packages[i]);
                }
                break;
            }

            if (mFloatPanelView != null) {
                runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        mFloatPanelView.refreshUI();
                        Xlog.d(TAG, "mFloatPanelView refreshUI");
                    }
                });
            }

            if (mAddedList.size() > 0) {
                for (int i = 0; i < mAddedList.size(); i++) {
                    addItemToDatabase(mAddedList.get(i));
                }
            }
            if (mDeletedList.size() > 0) {
                for (int i = 0; i < mDeletedList.size(); i++) {
                    deleteItemToDatabase(mDeletedList.get(i));
                }
            }
        }
    }

    /**
     * Add the icons for the supplied apk called packageName.
     */
    public void addPackage(Context context, String packageName) {
        final List<ResolveInfo> matches = findActivitiesForPackage(context,
                packageName);

        if (DEBUG) {
            Xlog.d(TAG, "addPackage: packageName = " + packageName
                    + ", matches = " + matches.size());
        }
		//remove app, if multi window not want to support,@{
        List<String> blackListPackageNames = new ArrayList<String>();
        //blackSheetComponentNames.add("com.mediatek.oobe");
        //blackSheetComponentNames.add("com.viber.voip");
        //blackSheetComponentNames.add("net.flixster.android");
        //blackSheetComponentNames.add("com.mediocre.smashhit");
        //blackSheetComponentNames.add("com.ea.game.tetris2011_na");
        //blackSheetComponentNames.add("com.hulu.plus");
        //blackSheetComponentNames.add("jp.kino.whiteLine");
        //blackSheetComponentNames.add("com.tencent.mtt");
        //blackSheetComponentNames.add("com.mykj.game.ddz");
        //blackSheetComponentNames.add("com.supercell.clashofclans");
        //blackSheetComponentNames.add("com.moji.mjweather");
        //blackSheetComponentNames.add("viva.reader");
        //blackSheetComponentNames.add("org.funship.findsomething.channel_91");
        //blackSheetComponentNames.add("com.qiyi.video");
        //blackSheetComponentNames.add("com.tencent.peng");
        //blackSheetComponentNames.add("com.tencent.pao");
        //blackSheetComponentNames.add("com.imdb.mobile");
        //blackSheetComponentNames.add("org.cocos2d.fishingjoy3.ck.duoku");
        //blackSheetComponentNames.add("org.cocos2dx.FishGame");
        //blackSheetComponentNames.add("com.wistone.war2victory.gree");
        //blackSheetComponentNames.add("com.soundcloud.android");
        //blackSheetComponentNames.add("com.pinterest");
        //blackSheetComponentNames.add("com.playrix.township");
        //blackSheetComponentNames.add("com.ludia.dragons");
        //blackSheetComponentNames.add("com.splashpadmobile.flashlight");
        //blackSheetComponentNames.add("com.pandora.android");
        //blackSheetComponentNames.add("com.ludia.dragons");
        //blackSheetComponentNames.add("com.playrix.township");
        //blackSheetComponentNames.add("com.UCMobile");

	/// M: get BlackList from MultiWindowProxy @{
        List<String> blackListComponentNames = new ArrayList<String>();
	MultiWindowProxy mMultiWindowProxy =MultiWindowProxy.getInstance();		   
	if ((mMultiWindowProxy != null) && mMultiWindowProxy.isFeatureSupport()){			   
			blackListPackageNames =	mMultiWindowProxy.getDisableFloatPkgList();
			blackListComponentNames =  mMultiWindowProxy.getDisableFloatComponentList();
			Xlog.d(TAG, "DisableFloatPkgList.size():" + blackListPackageNames.size());	
			Xlog.d(TAG, "DisableFloatComponentList.size():" + blackListComponentNames.size());	
	}
        /// @}

        for (String black : blackListPackageNames) {
	       if (black.equals(packageName)) {
	            Xlog.d(TAG, "remove blacklist app:" + black);
	            return;
	       }
        }
		
        for (String black : blackListComponentNames) {
	       if (black.contains(packageName)) {
	              Xlog.d(TAG, "remove blacklist app(by DisableFloatComponentList)=" + black);
	              return;
	       }
        }
        
        if (matches.size() > 0) {
            for (ResolveInfo info : matches) {
                FloatAppItem appItem = new FloatAppItem(
                        mContext.getPackageManager(), info, null,
                        mExtentAppList.size());
                appItem.container = EXTENT_CONTAINER;
                mExtentAppList.add(appItem);
                mAddedList.add(appItem);
            }
        }
    }

    /**
     * Remove the apps for the given apk identified by packageName.
     */
    public void removePackage(String packageName) {
        if (DEBUG) {
            Xlog.d(TAG, "removePackage: packageName = " + packageName);
        }

        for (int i = 0; i < mResidentAppList.size(); i++) {
            FloatAppItem listItem = mResidentAppList.get(i);
            if (packageName.equals(listItem.packageName)) {
                mResidentAppList.remove(i);
                mDeletedList.add(listItem);
            }
        }
        for (int i = 0; i < mExtentAppList.size(); i++) {
            FloatAppItem listItem = mExtentAppList.get(i);
            if (packageName.equals(listItem.packageName)) {
                mExtentAppList.remove(i);
                mDeletedList.add(listItem);
            }
        }
    }

    /**
     * Add and remove icons for this package which has been updated.
     */
    public void updatePackage(Context context, String packageName) {
        final List<ResolveInfo> matches = findActivitiesForPackage(context,
                packageName);
        if (DEBUG) {
            Xlog.d(TAG, "updatePackage: packageName = " + packageName
                    + ", matches = " + matches.size());
        }

        if (matches.size() > 0) {
            // Find disabled/removed activities and remove them from data and
            // add them
            // to the removed list.
            for (int i = 0; i < mResidentAppList.size(); i++) {
                FloatAppItem listItem = mResidentAppList.get(i);
                if (packageName.equals(listItem.packageName)) {
                    if (!findActivity(matches, listItem.className)) {
                        mResidentAppList.remove(i);
                        mDeletedList.add(listItem);
                    }
                }
            }
            for (int i = 0; i < mExtentAppList.size(); i++) {
                FloatAppItem listItem = mExtentAppList.get(i);
                if (packageName.equals(listItem.packageName)) {
                    if (!findActivity(matches, listItem.className)) {
                        mExtentAppList.remove(i);
                        mDeletedList.add(listItem);
                    }
                }
            }

            /*
             * // Find enabled activities and add them to the adapter // Also
             * updates existing activities with new labels/icons int count =
             * matches.size(); for (int i = 0; i < count; i++) { final
             * ResolveInfo info = matches.get(i); FloatAppItem floatListItem =
             * findAppItemInfo(mResidentAppList, info); FloatAppItem
             * editListItem = findAppItemInfo(mExtentAppList, info); if
             * (floatListItem == null && floatListItem == null) {
             * mExtentAppList.add(listItem); mAddedList.add(listItem); } }
             */
        } else {
            // Remove all data for this package.
            removePackage(packageName);
        }
    }

    /**
     * Query the package manager for MAIN/LAUNCHER activities in the supplied
     * package.
     */
    private static List<ResolveInfo> findActivitiesForPackage(Context context,
            String packageName) {
        final PackageManager packageManager = context.getPackageManager();

        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mainIntent.setPackage(packageName);

        final List<ResolveInfo> apps = packageManager.queryIntentActivities(
                mainIntent, 0);
        return apps != null ? apps : new ArrayList<ResolveInfo>();
    }

    /**
     * Returns whether <em>apps</em> contains <em>component</em>.
     */
    private static boolean findActivity(List<ResolveInfo> apps, String className) {
        for (ResolveInfo info : apps) {
            final ActivityInfo activityInfo = info.activityInfo;
            if (activityInfo.name.equals(className)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find an ApplicationInfo object for the given packageName and className.
     */
    private FloatAppItem findAppItemInfo(List<FloatAppItem> appList,
            ResolveInfo info) {
        for (FloatAppItem listItem : appList) {
            if (listItem.packageName
                    .equals(info.activityInfo.applicationInfo.packageName)
                    && listItem.className.equals(info.activityInfo.name)) {
                return listItem;
            }
        }
        return null;
    }
}
