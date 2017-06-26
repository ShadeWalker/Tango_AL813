/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.Trace;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.FolderIcon.FolderRingAnimator;
import com.android.launcher3.MTKUnreadLoader;
import com.android.launcher3.PagedViewCellLayout;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.LauncherActivityInfoCompat;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserHandleCompat;

import com.mediatek.launcher3.ext.AllApps;
import com.mediatek.launcher3.ext.LauncherExtPlugin;
import com.mediatek.launcher3.ext.LauncherLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A simple callback interface which also provides the results of the task.
 */
interface AsyncTaskCallback {
    void run(AppsCustomizeAsyncTask task, AsyncTaskPageData data);
}

/**
 * The data needed to perform either of the custom AsyncTasks.
 */
class AsyncTaskPageData {
    enum Type {
        LoadWidgetPreviewData
    }

    AsyncTaskPageData(int p, ArrayList<Object> l, int cw, int ch, AsyncTaskCallback bgR,
            AsyncTaskCallback postR, WidgetPreviewLoader w) {
        page = p;
        items = l;
        generatedImages = new ArrayList<Bitmap>();
        maxImageWidth = cw;
        maxImageHeight = ch;
        doInBackgroundCallback = bgR;
        postExecuteCallback = postR;
        widgetPreviewLoader = w;
    }
    void cleanup(boolean cancelled) {
        // Clean up any references to source/generated bitmaps
        if (generatedImages != null) {
            if (cancelled) {
                for (int i = 0; i < generatedImages.size(); i++) {
                    widgetPreviewLoader.recycleBitmap(items.get(i), generatedImages.get(i));
                }
            }
            generatedImages.clear();
        }
    }
    int page;
    ArrayList<Object> items;
    ArrayList<Bitmap> sourceImages;
    ArrayList<Bitmap> generatedImages;
    int maxImageWidth;
    int maxImageHeight;
    AsyncTaskCallback doInBackgroundCallback;
    AsyncTaskCallback postExecuteCallback;
    WidgetPreviewLoader widgetPreviewLoader;
}

/**
 * A generic template for an async task used in AppsCustomize.
 */
class AppsCustomizeAsyncTask extends AsyncTask<AsyncTaskPageData, Void, AsyncTaskPageData> {
    AppsCustomizeAsyncTask(int p, AsyncTaskPageData.Type ty) {
        page = p;
        threadPriority = Process.THREAD_PRIORITY_DEFAULT;
        dataType = ty;
    }
    @Override
    protected AsyncTaskPageData doInBackground(AsyncTaskPageData... params) {
        if (params.length != 1) return null;
        // Load each of the widget previews in the background
        params[0].doInBackgroundCallback.run(this, params[0]);
        return params[0];
    }
    @Override
    protected void onPostExecute(AsyncTaskPageData result) {
        // All the widget previews are loaded, so we can just callback to inflate the page
        result.postExecuteCallback.run(this, result);
    }

    void setThreadPriority(int p) {
        threadPriority = p;
    }
    void syncThreadPriority() {
        Process.setThreadPriority(threadPriority);
    }

    // The page that this async task is associated with
    AsyncTaskPageData.Type dataType;
    int page;
    int threadPriority;
}

/**
 * The Apps/Customize page that displays all the applications, widgets, and shortcuts.
 */
public class AppsCustomizePagedView extends PagedViewWithDraggableItems implements
        View.OnClickListener, View.OnKeyListener, DropTarget, DragSource, DragScroller,
        PagedViewWidget.ShortPressListener, DragController.DragListener,
        LauncherTransitionable {
    static final String TAG = "AppsCustomizePagedView";

    private static Rect sTmpRect = new Rect();

    /// M: Add for CT customization, new installed app icon from page 3.
    private static final int INSTALL_ICON_START_PAGE = 3;

    /**
     * The different content types that this paged view can show.
     */
    public enum ContentType {
        Applications,
        Widgets
    }
    private ContentType mContentType = ContentType.Applications;

    // Refs
    private Launcher mLauncher;
    private DragController mDragController;
    private final LayoutInflater mLayoutInflater;
    private final PackageManager mPackageManager;

    // Save and Restore
    private int mSaveInstanceStateItemIndex = -1;

    // Content
    /// M: [OP09] Modify for CT launcher.@{
    public static ArrayList<AppInfo> mApps;
    public static ArrayList<FolderInfo> sFolders;
    private ArrayList<Object> mWidgets;
    public boolean isInHideOrRemoveAppMode = false;
    private boolean mIsAppRemoved = false;
    private HashMap<Integer, RemoveAppData> mAppsArray = new HashMap<Integer, RemoveAppData>();
    /**
    *M: add class to store the appinfo, packageInfo and user information.
    */
    class RemoveAppData {
        ArrayList<AppInfo> mRmAppsInfo;
        ArrayList<String> mRmPackagesInfo;
        UserHandleCompat mUser;
    }
    //M:[OP09] }@

    /// M: Flag to record whether the app list data has been set to AppsCustomizePagedView.
    private boolean mAppsHasSet = false;

    // Caching
    private IconCache mIconCache;

    // Dimens
    private int mContentWidth, mContentHeight;
    private int mWidgetCountX, mWidgetCountY;
    private PagedViewCellLayout mWidgetSpacingLayout;
    private int mNumAppsPages;
    private int mNumWidgetPages;
    private Rect mAllAppsPadding = new Rect();

    // Previews & outlines
    ArrayList<AppsCustomizeAsyncTask> mRunningTasks;
    private static final int sPageSleepDelay = 200;

    private Runnable mInflateWidgetRunnable = null;
    private Runnable mBindWidgetRunnable = null;
    static final int WIDGET_NO_CLEANUP_REQUIRED = -1;
    static final int WIDGET_PRELOAD_PENDING = 0;
    static final int WIDGET_BOUND = 1;
    static final int WIDGET_INFLATED = 2;
    int mWidgetCleanupState = WIDGET_NO_CLEANUP_REQUIRED;
    int mWidgetLoadingId = -1;
    PendingAddWidgetInfo mCreateWidgetInfo = null;
    private boolean mDraggingWidget = false;
    boolean mPageBackgroundsVisible = true;

    private Toast mWidgetInstructionToast;

    // Deferral of loading widget previews during launcher transitions
    private boolean mInTransition;
    private ArrayList<AsyncTaskPageData> mDeferredSyncWidgetPageItems =
        new ArrayList<AsyncTaskPageData>();
    private ArrayList<Runnable> mDeferredPrepareLoadWidgetPreviewsTasks =
        new ArrayList<Runnable>();

    WidgetPreviewLoader mWidgetPreviewLoader;

    private boolean mInBulkBind;
    private boolean mNeedToUpdatePageCountsAndInvalidateData;

    /// M: For OP09 Start. @{
    /**
     * M: Is the user is dragging an item near the edge of a page?
     */
    private boolean mInScrollArea = false;
    private final int mTouchDelta = 8;

    /**
     * The CellLayout that is currently being dragged over.
     */
    private CellLayout mDragTargetLayout = null;

    /**
     * CellInfo for the cell that is currently being dragged.
     */
    private CellLayout.CellInfo mDragInfo;

    /**
     * The CellLayout which will be dropped to.
     */
    private CellLayout mDropToLayout = null;

    private float[] mDragViewVisualCenter = new float[2];

    private DropTarget.DragEnforcer mDragEnforcer;

    private boolean mAnimatingViewIntoPlace = false;

    private Matrix mTempInverseMatrix = new Matrix();

    private Alarm mReorderAlarm = new Alarm();

    private static final int REORDER_ANIMATION_DURATION = 230;

    private CellLayout mCurrentDropTarget = null;

    private CellLayout mPrevDropTarget = null;

    /**
     * Target drop area calculated during last acceptDrop call.
     */
    private int[] mTargetCell = new int[2];
    private int[] mPreviousTargetCell = new int[2];
    int[] mEmptyCell = new int[2];
    private int[] mPrevTargetCell = new int[2];
    private int[] mPrevEmptyCell = new int[2];
    private Point mDisplaySize = new Point();
    private boolean mIsDragOccuring = false;

    /**
     * M: Record the last cell info in the full page.
     */
    private View mPrevLastCell = null;
    private CellLayout.CellInfo mPrevLastCellInfo = new CellLayout.CellInfo();
    private View mLastCell = null;
    private CellLayout.CellInfo mLastCellInfo = new CellLayout.CellInfo();

    /**
     * M: Recorder all apps info for each page.
     */
    public static ArrayList<PageInfo> sAllAppsPage = new ArrayList<PageInfo>();
    /**
     * M: Recorder what apps will be hidden, what apps will be shown.
     */
    static ArrayList<AppInfo> sShowAndHideApps = new ArrayList<AppInfo>();

    private static ArrayList<AppInfo> sRemovedApps = new ArrayList<AppInfo>();

    /**
     * M: Real time reorder do or not.
     */
    private boolean mOccuredReorder = false;

    /**
     * M: whether to support edit and hide apps, only support for OP09 projects.
     */
    private boolean mSupportEditAndHideApps = false;
    private boolean mSupportCycleSliding = false;

    private Drawable mDeleteButtonDrawable = null;
    private int mDeleteMarginleft;

    /// M: STK package name and class name.
    private static final String STK_PACKAGE_NAME = "com.android.stk";
    private static final String STK_CLASS_NAME = "com.android.stk.StkLauncherActivity";
    private static final String STK2_CLASS_NAME = "com.android.stk.StkLauncherActivityII";

    /// M: For OP09 END.}@

    //M:[OP09][CF] @{
    private boolean mCreateUserFolderOnDrop = false;
    private boolean mAddToExistingFolderOnDrop = false;
    private int mDragMode = Workspace.DRAG_MODE_NONE;
    private float mMaxDistanceForFolderCreation;
    private int mLastReorderX = -1;
    private int mLastReorderY = -1;
    private int mDragOverX = -1;
    private int mDragOverY = -1;
    private View mDraggingView = null;
    private Object mDraggingInfo = null;
    //M:[OP09][CF] }@
    private Context mContext; //mtk add

    public AppsCustomizePagedView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context; //mtk add
        mLayoutInflater = LayoutInflater.from(context);
        mPackageManager = context.getPackageManager();
        mApps = new ArrayList<AppInfo>();
        mWidgets = new ArrayList<Object>();
        mIconCache = (LauncherAppState.getInstance()).getIconCache();
        mRunningTasks = new ArrayList<AppsCustomizeAsyncTask>();

        // Save the default widget preview background
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AppsCustomizePagedView, 0, 0);
        mWidgetCountX = a.getInt(R.styleable.AppsCustomizePagedView_widgetCountX, 2);
        mWidgetCountY = a.getInt(R.styleable.AppsCustomizePagedView_widgetCountY, 2);
        a.recycle();
        mWidgetSpacingLayout = new PagedViewCellLayout(getContext());

        /**M: Added to resolve the potential ANR while re-fresh the view invoked by unread feature related flow. {@**/
        /// M: whether to support eidt and add apps, add for OP09.
        mDragEnforcer = new DropTarget.DragEnforcer(context);
        /// M: whether to support eidt and add apps, add for OP09.
        mSupportEditAndHideApps = LauncherExtPlugin.getInstance().getWorkspaceExt(context)
                .supportEditAndHideApps();

        mSupportCycleSliding = LauncherExtPlugin.getInstance().getWorkspaceExt(context)
                .supportAppListCycleSliding();

        DeviceProfile grid = LauncherAppState.getInstance().getDynamicGrid().getDeviceProfile();
        /// M: whether to support eidt and add apps, add for OP09.
        if (mSupportEditAndHideApps) {
            mCellCountX = AllApps.sAppsCellCountX;
            mCellCountY = AllApps.sAppsCellCountY;
            mDeleteButtonDrawable = context.getResources().getDrawable(
                R.drawable.ic_launcher_delete_holo);

            mDeleteMarginleft = (int) context.getResources().getDimension(
                R.dimen.apps_customize_delete_margin_left);
        } else {
            mCellCountX = (int) grid.allAppsNumCols;
            mCellCountY = (int) grid.allAppsNumRows;
        }
        /**@}**/

        // The padding on the non-matched dimension for the default widget preview icons
        // (top + bottom)
        mFadeInAdjacentScreens = false;

        // Unless otherwise specified this view is important for accessibility.
        if (getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        setSinglePageInViewport();

        //M:[OP09][CF] @{
        if (mSupportEditAndHideApps) {
            sFolders = new ArrayList<FolderInfo>();
            mMaxDistanceForFolderCreation = (0.55f * grid.iconSizePx);
        }
        //M:[OP09][CF] }@
    }

    @Override
    protected void init() {
        super.init();
        mCenterPagesVertically = false;

        Context context = getContext();
        Resources r = context.getResources();
        setDragSlopeThreshold(r.getInteger(R.integer.config_appsCustomizeDragSlopeThreshold)/100f);
    }

    public void onFinishInflate() {
        super.onFinishInflate();

        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        setPadding(grid.edgeMarginPx, 2 * grid.edgeMarginPx,
                grid.edgeMarginPx, 2 * grid.edgeMarginPx);
    }

    void setAllAppsPadding(Rect r) {
        mAllAppsPadding.set(r);
    }

    void setWidgetsPageIndicatorPadding(int pageIndicatorHeight) {
        setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), pageIndicatorHeight);
    }

    WidgetPreviewLoader getWidgetPreviewLoader() {
        if (mWidgetPreviewLoader == null) {
            mWidgetPreviewLoader = new WidgetPreviewLoader(mLauncher);
        }
        return mWidgetPreviewLoader;
    }

    /** Returns the item index of the center item on this page so that we can restore to this
     *  item index when we rotate. */
    private int getMiddleComponentIndexOnCurrentPage() {
        int i = -1;
        if (getPageCount() > 0) {
            int currentPage = getCurrentPage();
            if (mContentType == ContentType.Applications) {
                AppsCustomizeCellLayout layout = (AppsCustomizeCellLayout) getPageAt(currentPage);
                ShortcutAndWidgetContainer childrenLayout = layout.getShortcutsAndWidgets();
                int numItemsPerPage = mCellCountX * mCellCountY;
                int childCount = childrenLayout.getChildCount();
                if (childCount > 0) {
                    i = (currentPage * numItemsPerPage) + (childCount / 2);
                }
            } else if (mContentType == ContentType.Widgets) {
                int numApps = mApps.size();
                PagedViewGridLayout layout = (PagedViewGridLayout) getPageAt(currentPage);
                int numItemsPerPage = mWidgetCountX * mWidgetCountY;
                int childCount = layout.getChildCount();
                if (childCount > 0) {
                    i = numApps +
                        (currentPage * numItemsPerPage) + (childCount / 2);
                }
            } else {
                throw new RuntimeException("Invalid ContentType");
            }
        }
        return i;
    }

    /** Get the index of the item to restore to if we need to restore the current page. */
    int getSaveInstanceStateIndex() {
        if (mSaveInstanceStateItemIndex == -1) {
            mSaveInstanceStateItemIndex = getMiddleComponentIndexOnCurrentPage();
        }
        return mSaveInstanceStateItemIndex;
    }

    /** Returns the page in the current orientation which is expected to contain the specified
     *  item index. */
    int getPageForComponent(int index) {
        if (index < 0) return 0;

        if (index < mApps.size()) {
            int numItemsPerPage = mCellCountX * mCellCountY;
            return (index / numItemsPerPage);
        } else {
            int numItemsPerPage = mWidgetCountX * mWidgetCountY;
            return (index - mApps.size()) / numItemsPerPage;
        }
    }

    /** Restores the page for an item at the specified index */
    void restorePageForIndex(int index) {
        if (index < 0) return;
        mSaveInstanceStateItemIndex = index;
    }

    private void updatePageCounts() {
        mNumWidgetPages = (int) Math.ceil(mWidgets.size() /
                (float) (mWidgetCountX * mWidgetCountY));
        if (mSupportEditAndHideApps) {
            mNumAppsPages = (int) Math.ceil((float) (mApps.size() + sFolders.size())
                   / (mCellCountX * mCellCountY));
        } else {
            mNumAppsPages = (int) Math.ceil((float) mApps.size() / (mCellCountX * mCellCountY));
        }

        /// M: modify to cycle sliding screen.
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "updatePageCounts: mSupportCycleSliding = " + mSupportCycleSliding
                    + ", mNumAppsPages = " + mNumAppsPages
                    + ", mNumWidgetPages = " + mNumWidgetPages);
        }

        if (mSupportCycleSliding) {
            /// M: only update related tab when cycle sliding is supported.
            AppsCustomizeTabHost tabHost = getTabHost();
            if (tabHost != null) {
                String tag = tabHost.getContentTag();
                if (tag != null) {
                    if (tag.equals(tabHost.getTabTagForContentType(ContentType.Widgets))) {
                        mNumAppsPages = 0;
                        updateWidgetsPageCounts(true);
                    } else {
                        mNumWidgetPages = 0;
                        updateAppsPageCounts();
                    }
                    return;
                }
            }
        }

        updateWidgetsPageCounts(false);
        updateAppsPageCounts();
    }

    void updateWidgetsPageCounts(boolean isWidgetTab) {
        /// M: hide all widget pages when cycle sliding is supported.
        if (mSupportCycleSliding && !isWidgetTab) {
            mNumWidgetPages = 0;
        } else {
            mNumWidgetPages = (int) Math.ceil(mWidgets.size()
                    / (float) (mWidgetCountX * mWidgetCountY));
        }

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "updateWidgetsPageCounts end: mNumWidgetPages = " + mNumWidgetPages
                    + ", mWidgets.size() = " + mWidgets.size());
        }
    }

    protected void onDataReady(int width, int height) {
        // Now that the data is ready, we can calculate the content width, the number of cells to
        // use for each page
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        if (mSupportEditAndHideApps) {
            mCellCountX = AllApps.sAppsCellCountX;
            mCellCountY = AllApps.sAppsCellCountY;
        } else {
            mCellCountX = (int) grid.allAppsNumCols;
            mCellCountY = (int) grid.allAppsNumRows;
        }
        updatePageCounts();

        // Force a measure to update recalculate the gaps
        mContentWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        mContentHeight = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
        int widthSpec = MeasureSpec.makeMeasureSpec(mContentWidth, MeasureSpec.AT_MOST);
        int heightSpec = MeasureSpec.makeMeasureSpec(mContentHeight, MeasureSpec.AT_MOST);
        mWidgetSpacingLayout.measure(widthSpec, heightSpec);

        final boolean hostIsTransitioning = getTabHost().isInTransition();
        int page = getPageForComponent(mSaveInstanceStateItemIndex);
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onDataReady: height = " + height + ", width = " + width + ", page = " + page
                    + ", hostIsTransitioning = " + hostIsTransitioning + ", mContentWidth = "
                    + mContentWidth + ", mNumAppsPages = " + mNumAppsPages + ", mNumWidgetPages = "
                    + mNumWidgetPages + ", this = " + this);
        }
        invalidatePageData(Math.max(0, page), hostIsTransitioning);
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (!isDataReady()) {
            if ((LauncherAppState.isDisableAllApps() || !mApps.isEmpty()) && !mWidgets.isEmpty()) {
                post(new Runnable() {
                    // This code triggers requestLayout so must be posted outside of the
                    // layout pass.
                    public void run() {
                        if (Utilities.isViewAttachedToWindow(AppsCustomizePagedView.this)) {
                            setDataIsReady();
                            onDataReady(getMeasuredWidth(), getMeasuredHeight());
                        }
                    }
                });
            }
        }
    }

    public void onPackagesUpdated(ArrayList<Object> widgetsAndShortcuts) {
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();

        // Get the list of widgets and shortcuts
        mWidgets.clear();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "updatePackages: widgetsAndShortcuts size = " + widgetsAndShortcuts.size());
        }
        for (Object o : widgetsAndShortcuts) {
            if (o instanceof AppWidgetProviderInfo) {
                AppWidgetProviderInfo widget = (AppWidgetProviderInfo) o;
                if (!app.shouldShowAppOrWidgetProvider(widget.provider)) {
                    continue;
                }
                if (widget.minWidth > 0 && widget.minHeight > 0) {
                    // Ensure that all widgets we show can be added on a workspace of this size
                    int[] spanXY = Launcher.getSpanForWidget(mLauncher, widget);
                    int[] minSpanXY = Launcher.getMinSpanForWidget(mLauncher, widget);
                    int minSpanX = Math.min(spanXY[0], minSpanXY[0]);
                    int minSpanY = Math.min(spanXY[1], minSpanXY[1]);
                    if (minSpanX <= (int) grid.numColumns &&
                        minSpanY <= (int) grid.numRows) {
                        mWidgets.add(widget);
                    } else {
                        Log.e(TAG, "Widget " + widget.provider + " can not fit on this device (" +
                              widget.minWidth + ", " + widget.minHeight + "), min span is (" + minSpanX + ", " + minSpanY + ")"
                              + "), span is (" + spanXY[0] + ", " + spanXY[1] + ")");
                    }
                } else {
                    Log.e(TAG, "Widget " + widget.provider + " has invalid dimensions (" +
                          widget.minWidth + ", " + widget.minHeight + ")");
                }
            } else {
                // just add shortcuts
                mWidgets.add(o);
            }
        }
        updatePageCountsAndInvalidateData();
    }

    public void setBulkBind(boolean bulkBind) {
        if (bulkBind) {
            mInBulkBind = true;
        } else {
            mInBulkBind = false;
            if (mNeedToUpdatePageCountsAndInvalidateData) {
                updatePageCountsAndInvalidateData();
            }
        }
    }

    private void updatePageCountsAndInvalidateData() {
        if (mInBulkBind) {
            mNeedToUpdatePageCountsAndInvalidateData = true;
        } else {
            updatePageCounts();
            invalidateOnDataChange();
            mNeedToUpdatePageCountsAndInvalidateData = false;
        }
    }

    @Override
    public void onClick(View v) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onClick: v = " + v + ", v.getTag() = " + v.getTag());
        }

        /// M: add systrace to analyze application launch time.
        Trace.traceBegin(Trace.TRACE_TAG_INPUT, "AppsCustomizePagedView.onClick");

        // When we have exited all apps or are in transition, disregard clicks
        if (!mLauncher.isAllAppsVisible()
                || mLauncher.getWorkspace().isSwitchingState()
                /*|| !(v instanceof PagedViewWidget)*/ || Launcher.isInEditMode()) return;

        if (v instanceof BubbleTextView) {
             // Animate some feedback to the click
             final AppInfo appInfo = (AppInfo) v.getTag();

             mLauncher.startActivitySafely(v, appInfo.intent, appInfo);
             mLauncher.getStats().recordLaunch(appInfo.intent);
             /// M: reset the drawable icon if multi window and isTablet
             /// M: because we may enter multi-window's small window,
             // then the icon may be in pressed state
             //resetDrawableStateIfPossible();
         } else if (v instanceof PagedViewWidget) {
            // Let the user know that they have to long press to add a widget
            if (mWidgetInstructionToast != null) {
                mWidgetInstructionToast.cancel();
            }
            mWidgetInstructionToast = Toast.makeText(getContext(),
                R.string.long_press_widget_to_add,
                Toast.LENGTH_SHORT);
            mWidgetInstructionToast.show();

            // Create a little animation to show that the widget can move
            float offsetY = getResources().getDimensionPixelSize(R.dimen.dragViewOffsetY);
            final ImageView p = (ImageView) v.findViewById(R.id.widget_preview);
            AnimatorSet bounce = LauncherAnimUtils.createAnimatorSet();
            ValueAnimator tyuAnim = LauncherAnimUtils.ofFloat(p, "translationY", offsetY);
            tyuAnim.setDuration(125);
            ValueAnimator tydAnim = LauncherAnimUtils.ofFloat(p, "translationY", 0f);
            tydAnim.setDuration(100);
            bounce.play(tyuAnim).before(tydAnim);
            bounce.setInterpolator(new AccelerateInterpolator());
            bounce.start();
        }

        /// M: add systrace to analyze application launch time.
        Trace.traceEnd(Trace.TRACE_TAG_INPUT);
    }

    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return FocusHelper.handleAppsCustomizeKeyEvent(v,  keyCode, event);
    }

    /*
     * PagedViewWithDraggableItems implementation
     */
    @Override
    protected void determineDraggingStart(android.view.MotionEvent ev) {
        // Disable dragging by pulling an app down for now.
    }

    private void beginDraggingApplication(View v) {
        mLauncher.getWorkspace().beginDragShared(v, this);
        /// M: Add for Edit AllAppsList for op09.
        if (Launcher.isInEditMode()) {
            View cellLayout = v;
            while (!(cellLayout instanceof CellLayout)) {
                cellLayout = (View) cellLayout.getParent();
            }

            CellLayout.CellInfo cellInfo = (CellLayout.CellInfo) cellLayout.getTag();
            // This happens when long clicking an item with the dpad/trackball
            if (cellInfo == null || cellInfo.cell == null) {
                LauncherLog.i(TAG, "cellInfo is null during dragging: cellLayout = " + cellLayout);
                return;
            }

            // Make sure the drag was started by a long press as opposed to a
            // long click.
            if (!cellInfo.cell.isInTouchMode()) {
                LauncherLog.i(TAG, "The child " + cellInfo.cell + " is not in touch mode.");
                return;
            }

            mDragInfo = cellInfo;
            mPrevEmptyCell[0] = cellInfo.cellX;
            mPrevEmptyCell[1] = cellInfo.cellY;

            mEmptyCell[0] = cellInfo.cellX;
            mEmptyCell[1] = cellInfo.cellY;

            ((CellLayout) cellLayout).removeChildView(cellInfo.cell);
            if (v.getTag() instanceof AppInfo) {
                AppInfo appInfo = (AppInfo) v.getTag();
                addOrRemoveApp(appInfo, false);
            } else if (v.getTag() instanceof FolderInfo) {
                FolderInfo folderInfo = (FolderInfo) v.getTag();
                addOrRemoveFolder(folderInfo, false);
            }
            mOccuredReorder = true;

            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "beginDraggingApplication: mEmptyCell[0] = " + mEmptyCell[0]
                        + ", mEmptyCell[1] = " + mEmptyCell[1] + ", mDragInfo = " + mDragInfo
                        + ", info = " + v.getTag() + ",cellLayout = " + cellLayout + ",v = " + v);
            }

            cellLayout.clearFocus();
            cellLayout.setPressed(false);
        }
    }

    static Bundle getDefaultOptionsForWidget(Launcher launcher, PendingAddWidgetInfo info) {
        Bundle options = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            AppWidgetResizeFrame.getWidgetSizeRanges(launcher, info.spanX, info.spanY, sTmpRect);
            Rect padding = AppWidgetHostView.getDefaultPaddingForWidget(launcher,
                    info.componentName, null);

            float density = launcher.getResources().getDisplayMetrics().density;
            int xPaddingDips = (int) ((padding.left + padding.right) / density);
            int yPaddingDips = (int) ((padding.top + padding.bottom) / density);

            options = new Bundle();
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                    sTmpRect.left - xPaddingDips);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                    sTmpRect.top - yPaddingDips);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
                    sTmpRect.right - xPaddingDips);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                    sTmpRect.bottom - yPaddingDips);
        }
        return options;
    }

    private void preloadWidget(final PendingAddWidgetInfo info) {
        final AppWidgetProviderInfo pInfo = info.info;
        final Bundle options = getDefaultOptionsForWidget(mLauncher, info);

        if (pInfo.configure != null) {
            info.bindOptions = options;
            return;
        }

        mWidgetCleanupState = WIDGET_PRELOAD_PENDING;
        mBindWidgetRunnable = new Runnable() {
            @Override
            public void run() {
                mWidgetLoadingId = mLauncher.getAppWidgetHost().allocateAppWidgetId();
                if(AppWidgetManagerCompat.getInstance(mLauncher).bindAppWidgetIdIfAllowed(
                        mWidgetLoadingId, pInfo, options)) {
                    mWidgetCleanupState = WIDGET_BOUND;
                }
            }
        };
        post(mBindWidgetRunnable);

        mInflateWidgetRunnable = new Runnable() {
            @Override
            public void run() {
                if (mWidgetCleanupState != WIDGET_BOUND) {
                    return;
                }
                AppWidgetHostView hostView = mLauncher.
                        getAppWidgetHost().createView(getContext(), mWidgetLoadingId, pInfo);
                info.boundWidget = hostView;
                mWidgetCleanupState = WIDGET_INFLATED;
                hostView.setVisibility(INVISIBLE);
                int[] unScaledSize = mLauncher.getWorkspace().estimateItemSize(info.spanX,
                        info.spanY, info, false);

                // We want the first widget layout to be the correct size. This will be important
                // for width size reporting to the AppWidgetManager.
                DragLayer.LayoutParams lp = new DragLayer.LayoutParams(unScaledSize[0],
                        unScaledSize[1]);
                lp.x = lp.y = 0;
                lp.customPosition = true;
                hostView.setLayoutParams(lp);
                mLauncher.getDragLayer().addView(hostView);
            }
        };
        post(mInflateWidgetRunnable);
    }

    @Override
    public void onShortPress(View v) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onShortcutPress v = " + v + ", v.getTag() = " + v.getTag());
        }

        // We are anticipating a long press, and we use this time to load bind and instantiate
        // the widget. This will need to be cleaned up if it turns out no long press occurs.
        if (mCreateWidgetInfo != null) {
            // Just in case the cleanup process wasn't properly executed. This shouldn't happen.
            cleanupWidgetPreloading(false);
        }
        mCreateWidgetInfo = new PendingAddWidgetInfo((PendingAddWidgetInfo) v.getTag());
        preloadWidget(mCreateWidgetInfo);
    }

    private void cleanupWidgetPreloading(boolean widgetWasAdded) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "cleanupWidgetPreloading widgetWasAdded = " + widgetWasAdded
                    + ", mCreateWidgetInfo = " + mCreateWidgetInfo + ", mWidgetLoadingId = "
                    + mWidgetLoadingId);
        }

        if (!widgetWasAdded) {
            // If the widget was not added, we may need to do further cleanup.
            PendingAddWidgetInfo info = mCreateWidgetInfo;
            mCreateWidgetInfo = null;

            if (mWidgetCleanupState == WIDGET_PRELOAD_PENDING) {
                // We never did any preloading, so just remove pending callbacks to do so
                removeCallbacks(mBindWidgetRunnable);
                removeCallbacks(mInflateWidgetRunnable);
            } else if (mWidgetCleanupState == WIDGET_BOUND) {
                 // Delete the widget id which was allocated
                if (mWidgetLoadingId != -1) {
                    mLauncher.getAppWidgetHost().deleteAppWidgetId(mWidgetLoadingId);
                }

                // We never got around to inflating the widget, so remove the callback to do so.
                removeCallbacks(mInflateWidgetRunnable);
            } else if (mWidgetCleanupState == WIDGET_INFLATED) {
                // Delete the widget id which was allocated
                if (mWidgetLoadingId != -1) {
                    mLauncher.getAppWidgetHost().deleteAppWidgetId(mWidgetLoadingId);
                }

                // The widget was inflated and added to the DragLayer -- remove it.
                AppWidgetHostView widget = info.boundWidget;
                mLauncher.getDragLayer().removeView(widget);
            }
        }
        mWidgetCleanupState = WIDGET_NO_CLEANUP_REQUIRED;
        mWidgetLoadingId = -1;
        mCreateWidgetInfo = null;
        PagedViewWidget.resetShortPressTarget();
    }

    @Override
    public void cleanUpShortPress(View v) {
        if (!mDraggingWidget) {
            cleanupWidgetPreloading(false);
        }
    }

    private boolean beginDraggingWidget(View v) {
        mDraggingWidget = true;
        // Get the widget preview as the drag representation
        ImageView image = (ImageView) v.findViewById(R.id.widget_preview);
        PendingAddItemInfo createItemInfo = (PendingAddItemInfo) v.getTag();

        if (LauncherLog.DEBUG_DRAG) {
            LauncherLog.d(TAG, "beginDraggingWidget: createItemInfo = " + createItemInfo
                    + ", v = " + v + ", image = " + image + ", this = " + this);
        }

        // If the ImageView doesn't have a drawable yet, the widget preview hasn't been loaded and
        // we abort the drag.
        if (image.getDrawable() == null) {
            mDraggingWidget = false;
            return false;
        }

        // Compose the drag image
        Bitmap preview;
        Bitmap outline;
        float scale = 1f;
        Point previewPadding = null;

        if (createItemInfo instanceof PendingAddWidgetInfo) {
            // This can happen in some weird cases involving multi-touch. We can't start dragging
            // the widget if this is null, so we break out.
            if (mCreateWidgetInfo == null) {
                return false;
            }

            PendingAddWidgetInfo createWidgetInfo = mCreateWidgetInfo;
            createItemInfo = createWidgetInfo;
            int spanX = createItemInfo.spanX;
            int spanY = createItemInfo.spanY;
            int[] size = mLauncher.getWorkspace().estimateItemSize(spanX, spanY,
                    createWidgetInfo, true);

            FastBitmapDrawable previewDrawable = (FastBitmapDrawable) image.getDrawable();
            float minScale = 1.25f;
            int maxWidth, maxHeight;
            maxWidth = Math.min((int) (previewDrawable.getIntrinsicWidth() * minScale), size[0]);
            maxHeight = Math.min((int) (previewDrawable.getIntrinsicHeight() * minScale), size[1]);

            int[] previewSizeBeforeScale = new int[1];

            preview = getWidgetPreviewLoader().generateWidgetPreview(createWidgetInfo.info,
                    spanX, spanY, maxWidth, maxHeight, null, previewSizeBeforeScale);

            // Compare the size of the drag preview to the preview in the AppsCustomize tray
            int previewWidthInAppsCustomize = Math.min(previewSizeBeforeScale[0],
                    getWidgetPreviewLoader().maxWidthForWidgetPreview(spanX));
            scale = previewWidthInAppsCustomize / (float) preview.getWidth();

            // The bitmap in the AppsCustomize tray is always the the same size, so there
            // might be extra pixels around the preview itself - this accounts for that
            if (previewWidthInAppsCustomize < previewDrawable.getIntrinsicWidth()) {
                int padding =
                        (previewDrawable.getIntrinsicWidth() - previewWidthInAppsCustomize) / 2;
                previewPadding = new Point(padding, 0);
            }
        } else {
            PendingAddShortcutInfo createShortcutInfo = (PendingAddShortcutInfo) v.getTag();
            Drawable icon = mIconCache.getFullResIcon(createShortcutInfo.shortcutActivityInfo);
            preview = Utilities.createIconBitmap(icon, mLauncher);
            createItemInfo.spanX = createItemInfo.spanY = 1;
        }

        // Don't clip alpha values for the drag outline if we're using the default widget preview
        boolean clipAlpha = !(createItemInfo instanceof PendingAddWidgetInfo &&
                (((PendingAddWidgetInfo) createItemInfo).previewImage == 0));

        // Save the preview for the outline generation, then dim the preview
        outline = Bitmap.createScaledBitmap(preview, preview.getWidth(), preview.getHeight(),
                false);

        // Start the drag
        mLauncher.lockScreenOrientation();
        mLauncher.getWorkspace().onDragStartedWithItem(createItemInfo, outline, clipAlpha);
        mDragController.startDrag(image, preview, this, createItemInfo,
                DragController.DRAG_ACTION_COPY, previewPadding, scale);
        outline.recycle();
        preview.recycle();
        return true;
    }

    @Override
    protected boolean beginDragging(final View v) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "beginDragging: v = " + v + ", this = " + this);
        }

        if (!super.beginDragging(v)) return false;

        if (v instanceof BubbleTextView) {
            beginDraggingApplication(v);
            /// M: return directly if in edit mode for op09.
            if (Launcher.isInEditMode()) {
                return true;
            }
        } else if (v instanceof PagedViewWidget) {
            if (!beginDraggingWidget(v)) {
                return false;
            }
        } else if (v instanceof FolderIcon) { //M:[OP09][CF] @{
            // M:[OP09][CF] Dragging Folder to Workspace backup TagInfo.
            if (mSupportEditAndHideApps && !Launcher.isInEditMode()) {
                if (v.getTag() instanceof FolderInfo) {
                    mDraggingView = v;
                    mDraggingInfo = v.getTag();
                    final FolderInfo folderInfo = (FolderInfo) v.getTag();
                    v.setTag(folderInfo.copy());
                }
            }

            beginDraggingApplication(v);
            /// M: return directly if in edit mode for op09.
            if (Launcher.isInEditMode()) {
                return true;
            }
        } //M:[OP09][CF] }@

        // We delay entering spring-loaded mode slightly to make sure the UI
        // thready is free of any work.
        postDelayed(new Runnable() {
            @Override
            public void run() {
                // We don't enter spring-loaded mode if the drag has been cancelled
                if(mLauncher == null){
                    return;
                }
                DragController controller = mLauncher.getDragController();
                if (controller != null && controller.isDragging()) {
                    // Go into spring loaded mode (must happen before we startDrag())
                    mLauncher.enterSpringLoadedDragMode();
                }
            }
        }, 150);

        return true;
    }

    /**
     * Clean up after dragging.
     *
     * @param target where the item was dragged to (can be null if the item was flung)
     */
    private void endDragging(View target, boolean isFlingToDelete, boolean success) {
        if (LauncherLog.DEBUG_DRAG) {
            LauncherLog.d(TAG, "endDragging: target = " + target + ", isFlingToDelete = "
                    + isFlingToDelete + ", success = " + success);
        }

        // M:[OP09][CF] Dragging Folder to Workspace reSetTagInfo. @{
        if (mSupportEditAndHideApps && !Launcher.isInEditMode()) {
            if (mDraggingView != null && mDraggingInfo != null) {
                if (mDraggingInfo instanceof FolderInfo) {
                    mDraggingView.setTag(mDraggingInfo);
                }
            }
        }
        // M:[OP09][CF] Dragging Folder to Workspace reSetTagInfo. @}

        if (isFlingToDelete || !success || (target != mLauncher.getWorkspace() &&
                !(target instanceof DeleteDropTarget) && !(target instanceof Folder))) {
            // Exit spring loaded mode if we have not successfully dropped or have not handled the
            // drop in Workspace
            mLauncher.exitSpringLoadedDragModeDelayed(true,
                    Launcher.EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT, null);
            mLauncher.unlockScreenOrientation(false);
        } else {
            mLauncher.unlockScreenOrientation(false);
        }
    }

    @Override
    public View getContent() {
        if (getChildCount() > 0) {
            return getChildAt(0);
        }
        return null;
    }

    @Override
    public void onLauncherTransitionPrepare(Launcher l, boolean animated, boolean toWorkspace) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onLauncherTransitionPrepare l = " + l + ", animated = " + animated +
                    ", toWorkspace = " + toWorkspace);
        }

        mInTransition = true;
        if (toWorkspace) {
            cancelAllTasks();
        }
    }

    @Override
    public void onLauncherTransitionStart(Launcher l, boolean animated, boolean toWorkspace) {
    }

    @Override
    public void onLauncherTransitionStep(Launcher l, float t) {
    }

    @Override
    public void onLauncherTransitionEnd(Launcher l, boolean animated, boolean toWorkspace) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onLauncherTransitionEnd l = " + l + ", animated = " + animated +
                    ", toWorkspace = " + toWorkspace);
        }

        mInTransition = false;
        for (AsyncTaskPageData d : mDeferredSyncWidgetPageItems) {
            onSyncWidgetPageItems(d, false);
        }
        mDeferredSyncWidgetPageItems.clear();
        for (Runnable r : mDeferredPrepareLoadWidgetPreviewsTasks) {
            r.run();
        }
        mDeferredPrepareLoadWidgetPreviewsTasks.clear();
        mForceDrawAllChildrenNextFrame = !toWorkspace;
    }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean isFlingToDelete,
            boolean success) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onDropCompleted: target = " + target + ", d = " + d
                    + ", isFlingToDelete = " + isFlingToDelete + ", success = " + success
                    + ", mDragInfo = "  + mDragInfo + ",mCurrentPage = " + mCurrentPage);
        }

        // Return early and wait for onFlingToDeleteCompleted if this was the result of a fling
        if (isFlingToDelete) return;

        endDragging(target, false, success);

        // Display an error message if the drag failed due to there not being enough space on the
        // target layout we were dropping on.
        if (!success) {
            boolean showOutOfSpaceMessage = false;
            if (target instanceof Workspace) {
                int currentScreen = mLauncher.getCurrentWorkspaceScreen();
                Workspace workspace = (Workspace) target;
                CellLayout layout = (CellLayout) workspace.getChildAt(currentScreen);
                ItemInfo itemInfo = (ItemInfo) d.dragInfo;
                if (layout != null) {
                    layout.calculateSpans(itemInfo);
                    showOutOfSpaceMessage =
                            !layout.findCellForSpan(null, itemInfo.spanX, itemInfo.spanY);
                }
                /// M: Display an error message if the drag failed due to exist one IMtkWidget
                /// which providerName equals the providerName of the dragInfo.
                if (d.dragInfo instanceof PendingAddWidgetInfo) {
                    PendingAddWidgetInfo info = (PendingAddWidgetInfo) d.dragInfo;
                    if (workspace.searchIMTKWidget(workspace, info.componentName.getClassName()) != null) {
                        mLauncher.showOnlyOneWidgetMessage(info);
                    }
                }
            }
            if (showOutOfSpaceMessage) {
                mLauncher.showOutOfSpaceMessage(false);
            }

            /// M: The drag failed, we need to return the item to all apps list, add for OP09.
            if (Launcher.isInEditMode()) {
                onDrop(d);
            }

            d.deferDragViewCleanupPostAnimation = false;
        }
        cleanupWidgetPreloading(success);
        mDraggingWidget = false;
    }

    @Override
    public void onFlingToDeleteCompleted() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onFlingToDeleteCompleted.");
        }

        // We just dismiss the drag when we fling, so cleanup here
        endDragging(null, true, true);
        cleanupWidgetPreloading(false);
        mDraggingWidget = false;
    }

    @Override
    public boolean supportsFlingToDelete() {
        return true;
    }

    @Override
    public boolean supportsAppInfoDropTarget() {
        return true;
    }

    @Override
    public boolean supportsDeleteDropTarget() {
        return false;
    }

    @Override
    public float getIntrinsicIconScaleFactor() {
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        return (float) grid.allAppsIconSizePx / grid.iconSizePx;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onDetachedFromWindow.");
        }
        cancelAllTasks();
    }

    @Override
    public void trimMemory() {
        super.trimMemory();
        clearAllWidgetPages();
    }

    public void clearAllWidgetPages() {
        cancelAllTasks();
        int count = getChildCount();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "clearAllPages: count = " + count);
        }

        for (int i = 0; i < count; i++) {
            View v = getPageAt(i);
            if (v instanceof PagedViewGridLayout) {
                ((PagedViewGridLayout) v).removeAllViewsOnPage();
                mDirtyPageContent.set(i, true);
            }
        }
    }

    private void cancelAllTasks() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "cancelAllTasks: mRunningTasks size = " + mRunningTasks.size());
        }

        // Clean up all the async tasks
        Iterator<AppsCustomizeAsyncTask> iter = mRunningTasks.iterator();
        while (iter.hasNext()) {
            AppsCustomizeAsyncTask task = (AppsCustomizeAsyncTask) iter.next();
            task.cancel(false);
            iter.remove();
            mDirtyPageContent.set(task.page, true);

            // We've already preallocated the views for the data to load into, so clear them as well
            View v = getPageAt(task.page);
            if (v instanceof PagedViewGridLayout) {
                ((PagedViewGridLayout) v).removeAllViewsOnPage();
            }
        }
        mDeferredSyncWidgetPageItems.clear();
        mDeferredPrepareLoadWidgetPreviewsTasks.clear();
    }

    public void setContentType(ContentType type) {
        // Widgets appear to be cleared every time you leave, always force invalidate for them
        if (mContentType != type || type == ContentType.Widgets) {
            int page = (mContentType != type) ? 0 : getCurrentPage();
            mContentType = type;
            /// M: Add for op09 Edit and Hide app icons.
            updatePageCounts();
            invalidatePageData(page, true);
        }
    }

    public ContentType getContentType() {
        return mContentType;
    }

    protected void snapToPage(int whichPage, int delta, int duration) {
        super.snapToPage(whichPage, delta, duration);
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "snapToPage: whichPage = " + whichPage + ", delta = "
                    + delta + ", duration = " + duration + ", this = " + this);
        }

        // Update the thread priorities given the direction lookahead
        Iterator<AppsCustomizeAsyncTask> iter = mRunningTasks.iterator();
        while (iter.hasNext()) {
            AppsCustomizeAsyncTask task = (AppsCustomizeAsyncTask) iter.next();
            int pageIndex = task.page;
            if ((mNextPage > mCurrentPage && pageIndex >= mCurrentPage) ||
                (mNextPage < mCurrentPage && pageIndex <= mCurrentPage)) {
                task.setThreadPriority(getThreadPriorityForPage(pageIndex));
            } else {
                task.setThreadPriority(Process.THREAD_PRIORITY_LOWEST);
            }
        }

        /// M: set current drop target for dragging, only support app drag, add for OP09.
        if (ContentType.Applications.equals(mContentType) && isSupportCycleSlidingScreen()) {
            mCurrentDropTarget = (CellLayout) getPageAt(whichPage);
        }
    }

    /*
     * Apps PagedView implementation
     */
    private void setVisibilityOnChildren(ViewGroup layout, int visibility) {
        int childCount = layout.getChildCount();
        for (int i = 0; i < childCount; ++i) {
            layout.getChildAt(i).setVisibility(visibility);
        }
    }
    private void setupPage(AppsCustomizeCellLayout layout) {
        layout.setGridSize(mCellCountX, mCellCountY);

        // Note: We force a measure here to get around the fact that when we do layout calculations
        // immediately after syncing, we don't have a proper width.  That said, we already know the
        // expected page width, so we can actually optimize by hiding all the TextView-based
        // children that are expensive to measure, and let that happen naturally later.
        setVisibilityOnChildren(layout, View.GONE);
        int widthSpec = MeasureSpec.makeMeasureSpec(mContentWidth, MeasureSpec.AT_MOST);
        int heightSpec = MeasureSpec.makeMeasureSpec(mContentHeight, MeasureSpec.AT_MOST);
        layout.measure(widthSpec, heightSpec);

        if(!Launcher.DISABLE_APPLIST_WHITE_BG) {
            Drawable bg = getContext().getResources().getDrawable(R.drawable.quantum_panel);
        if (bg != null) {
            bg.setAlpha(mPageBackgroundsVisible ? 255: 0);
            layout.setBackground(bg);
            }
        } else {
            layout.setBackground(null);
        }

        setVisibilityOnChildren(layout, View.VISIBLE);
    }

    public void setPageBackgroundsVisible(boolean visible) {
        mPageBackgroundsVisible = visible;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; ++i) {
            Drawable bg = getChildAt(i).getBackground();
            if (bg != null) {
                bg.setAlpha(visible ? 255 : 0);
            }
        }
    }

    public void syncAppsPageItems(int page, boolean immediate) {
        // ensure that we have the right number of items on the pages
        final boolean isRtl = isLayoutRtl();
        int numCells = mCellCountX * mCellCountY;
        int startIndex = page * numCells;
        int endIndex = Math.min(startIndex + numCells, mApps.size());
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "syncAppsPageItems: page = " + page + ", immediate = " + immediate
                    + ", numCells = " + numCells + ", startIndex = " + startIndex + ", endIndex = "
                    + endIndex + ", app size = " + mApps.size() + ", child count = "
                    + getChildCount() + ", this = " + this);
        }

        AppsCustomizeCellLayout layout = (AppsCustomizeCellLayout) getPageAt(page);

        layout.removeAllViewsOnPage();
        ArrayList<Object> items = new ArrayList<Object>();
        ArrayList<Bitmap> images = new ArrayList<Bitmap>();
        for (int i = startIndex; i < endIndex; ++i) {
            AppInfo info = mApps.get(i);
            BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(
                    R.layout.apps_customize_application, layout, false);
            if(Launcher.DISABLE_APPLIST_WHITE_BG) {
                icon.setTextColor(getContext().getResources().getColor(R.color.quantum_panel_transparent_bg_text_color));
            }
            icon.applyFromApplicationInfo(info);
            icon.setOnClickListener(mLauncher);
            icon.setOnLongClickListener(this);
            icon.setOnTouchListener(this);
            icon.setOnKeyListener(this);
            icon.setOnFocusChangeListener(layout.mFocusHandlerView);

            int index = i - startIndex;
            int x = index % mCellCountX;
            int y = index / mCellCountX;
            if (isRtl) {
                x = mCellCountX - x - 1;
            }
            layout.addViewToCellLayout(icon, -1, i, new CellLayout.LayoutParams(x,y, 1,1), false);

            items.add(info);
            images.add(info.iconBitmap);
        }

        enableHwLayersOnVisiblePages();
    }

    /**
     * A helper to return the priority for loading of the specified widget page.
     */
    private int getWidgetPageLoadPriority(int page) {
        // If we are snapping to another page, use that index as the target page index
        int toPage = mCurrentPage;
        if (mNextPage > -1) {
            toPage = mNextPage;
        }

        // We use the distance from the target page as an initial guess of priority, but if there
        // are no pages of higher priority than the page specified, then bump up the priority of
        // the specified page.
        Iterator<AppsCustomizeAsyncTask> iter = mRunningTasks.iterator();
        int minPageDiff = Integer.MAX_VALUE;
        while (iter.hasNext()) {
            AppsCustomizeAsyncTask task = (AppsCustomizeAsyncTask) iter.next();
            minPageDiff = Math.abs(task.page - toPage);
        }

        int rawPageDiff = Math.abs(page - toPage);
        return rawPageDiff - Math.min(rawPageDiff, minPageDiff);
    }
    /**
     * Return the appropriate thread priority for loading for a given page (we give the current
     * page much higher priority)
     */
    private int getThreadPriorityForPage(int page) {
        // TODO-APPS_CUSTOMIZE: detect number of cores and set thread priorities accordingly below
        int pageDiff = getWidgetPageLoadPriority(page);
        if (pageDiff <= 0) {
            return Process.THREAD_PRIORITY_LESS_FAVORABLE;
        } else if (pageDiff <= 1) {
            return Process.THREAD_PRIORITY_LOWEST;
        } else {
            return Process.THREAD_PRIORITY_LOWEST;
        }
    }
    private int getSleepForPage(int page) {
        int pageDiff = getWidgetPageLoadPriority(page);
        return Math.max(0, pageDiff * sPageSleepDelay);
    }
    /**
     * Creates and executes a new AsyncTask to load a page of widget previews.
     */
    private void prepareLoadWidgetPreviewsTask(int page, ArrayList<Object> widgets,
            int cellWidth, int cellHeight, int cellCountX) {

        // Prune all tasks that are no longer needed
        Iterator<AppsCustomizeAsyncTask> iter = mRunningTasks.iterator();
        while (iter.hasNext()) {
            AppsCustomizeAsyncTask task = (AppsCustomizeAsyncTask) iter.next();
            int taskPage = task.page;
            if (taskPage < getAssociatedLowerPageBound(mCurrentPage) ||
                    taskPage > getAssociatedUpperPageBound(mCurrentPage)) {
                task.cancel(false);
                iter.remove();
            } else {
                task.setThreadPriority(getThreadPriorityForPage(taskPage));
            }
        }

        // We introduce a slight delay to order the loading of side pages so that we don't thrash
        final int sleepMs = getSleepForPage(page);
        AsyncTaskPageData pageData = new AsyncTaskPageData(page, widgets, cellWidth, cellHeight,
            new AsyncTaskCallback() {
                @Override
                public void run(AppsCustomizeAsyncTask task, AsyncTaskPageData data) {
                    try {
                        try {
                            Thread.sleep(sleepMs);
                        } catch (Exception e) {}
                        loadWidgetPreviewsInBackground(task, data);
                    } finally {
                        if (task.isCancelled()) {
                            data.cleanup(true);
                        }
                    }
                }
            },
            new AsyncTaskCallback() {
                @Override
                public void run(AppsCustomizeAsyncTask task, AsyncTaskPageData data) {
                    mRunningTasks.remove(task);
                    if (task.isCancelled()) return;
                    // do cleanup inside onSyncWidgetPageItems
                    onSyncWidgetPageItems(data, false);
                }
            }, getWidgetPreviewLoader());

        // Ensure that the task is appropriately prioritized and runs in parallel
        AppsCustomizeAsyncTask t = new AppsCustomizeAsyncTask(page,
                AsyncTaskPageData.Type.LoadWidgetPreviewData);
        t.setThreadPriority(getThreadPriorityForPage(page));
        t.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, pageData);
        mRunningTasks.add(t);
    }

    /*
     * Widgets PagedView implementation
     */
    private void setupPage(PagedViewGridLayout layout) {
        // Note: We force a measure here to get around the fact that when we do layout calculations
        // immediately after syncing, we don't have a proper width.
        int widthSpec = MeasureSpec.makeMeasureSpec(mContentWidth, MeasureSpec.AT_MOST);
        int heightSpec = MeasureSpec.makeMeasureSpec(mContentHeight, MeasureSpec.AT_MOST);

        Drawable bg = getContext().getResources().getDrawable(R.drawable.quantum_panel_dark);
        if (bg != null) {
            bg.setAlpha(mPageBackgroundsVisible ? 255 : 0);
            layout.setBackground(bg);
        }
        layout.measure(widthSpec, heightSpec);
    }

    public void syncWidgetPageItems(final int page, final boolean immediate) {
        int numItemsPerPage = mWidgetCountX * mWidgetCountY;

        final PagedViewGridLayout layout = (PagedViewGridLayout) getPageAt(page);

        // Calculate the dimensions of each cell we are giving to each widget
        final ArrayList<Object> items = new ArrayList<Object>();
        
        /// M: ALPS01828917
        /// sometime the measured width or height got at first time is wrong
        /// so force get measure width and height every time as workaround {@
        mContentWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        mContentHeight = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
        if(LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "syncWidgetPageItems: mContentWidth = " + mContentWidth
                    + ", mContentHeight = " + mContentHeight);
        }
        /// @}
        
        int contentWidth = mContentWidth - layout.getPaddingLeft() - layout.getPaddingRight();
        final int cellWidth = contentWidth / mWidgetCountX;
        int contentHeight = mContentHeight - layout.getPaddingTop() - layout.getPaddingBottom();

        final int cellHeight = contentHeight / mWidgetCountY;

        // Prepare the set of widgets to load previews for in the background
        int offset = page * numItemsPerPage;
        for (int i = offset; i < Math.min(offset + numItemsPerPage, mWidgets.size()); ++i) {
            items.add(mWidgets.get(i));
        }
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "syncWidgetPageItems: page = " + page + ", immediate = " + immediate
                    + ", numItemsPerPage = " + numItemsPerPage
                    + ", contentWidth = " + contentWidth + ", cellWidth = " + cellWidth
                    + ", contentHeight = " + contentHeight + ", cellHeight = " + cellHeight
                    + ", offset = " + offset + ", this = " + this);
        }

        // Prepopulate the pages with the other widget info, and fill in the previews later
        layout.setColumnCount(layout.getCellCountX());
        LauncherLog.d(TAG, "syncWidgetPageItems: cell count x = " + layout.getCellCountX() 
                    + ", layout column count = " + layout.getColumnCount());
        for (int i = 0; i < items.size(); ++i) {
            Object rawInfo = items.get(i);
            PendingAddItemInfo createItemInfo = null;
            PagedViewWidget widget = (PagedViewWidget) mLayoutInflater.inflate(
                    R.layout.apps_customize_widget, layout, false);
            if (rawInfo instanceof AppWidgetProviderInfo) {
                // Fill in the widget information
                AppWidgetProviderInfo info = (AppWidgetProviderInfo) rawInfo;
                createItemInfo = new PendingAddWidgetInfo(info, null, null);

                // Determine the widget spans and min resize spans.
                int[] spanXY = Launcher.getSpanForWidget(mLauncher, info);
                createItemInfo.spanX = spanXY[0];
                createItemInfo.spanY = spanXY[1];
                int[] minSpanXY = Launcher.getMinSpanForWidget(mLauncher, info);
                createItemInfo.minSpanX = minSpanXY[0];
                createItemInfo.minSpanY = minSpanXY[1];

                widget.applyFromAppWidgetProviderInfo(info, -1, spanXY, getWidgetPreviewLoader());
                widget.setTag(createItemInfo);
                widget.setShortPressListener(this);
            } else if (rawInfo instanceof ResolveInfo) {
                // Fill in the shortcuts information
                ResolveInfo info = (ResolveInfo) rawInfo;
                createItemInfo = new PendingAddShortcutInfo(info.activityInfo);
                createItemInfo.itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
                createItemInfo.componentName = new ComponentName(info.activityInfo.packageName,
                        info.activityInfo.name);
                widget.applyFromResolveInfo(mPackageManager, info, getWidgetPreviewLoader());
                widget.setTag(createItemInfo);
            }
            widget.setOnClickListener(this);
            widget.setOnLongClickListener(this);
            widget.setOnTouchListener(this);
            widget.setOnKeyListener(this);

            // Layout each widget
            int ix = i % mWidgetCountX;
            int iy = i / mWidgetCountX;

            LauncherLog.d(TAG, "syncWidgetPageItems: i = " + i + 
                        ", ix = " + ix + ", iy = " + iy + ", mWidgetCountX = " + mWidgetCountX);

            if (ix > 0) {
                View border = widget.findViewById(R.id.left_border);
                border.setVisibility(View.VISIBLE);
            }
            if (ix < mWidgetCountX - 1) {
                View border = widget.findViewById(R.id.right_border);
                border.setVisibility(View.VISIBLE);
            }

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(iy, GridLayout.START),
                    GridLayout.spec(ix, GridLayout.TOP));
            lp.width = cellWidth;
            lp.height = cellHeight;
            lp.setGravity(Gravity.TOP | Gravity.START);
            layout.addView(widget, lp);
        }

        // wait until a call on onLayout to start loading, because
        // PagedViewWidget.getPreviewSize() will return 0 if it hasn't been laid out
        // TODO: can we do a measure/layout immediately?
        /**
         * M: set the older runnable of PagedViewGridLayout instance as null, it
         * will cause the specify page's older loading operation aborted. Then
         * to avoid the flush issue.@{
         */
        layout.setOnLayoutListener(null);
        /**@}**/
        layout.setOnLayoutListener(new Runnable() {
            public void run() {
                // Load the widget previews
                int maxPreviewWidth = cellWidth;
                int maxPreviewHeight = cellHeight;
                if (layout.getChildCount() > 0) {
                    PagedViewWidget w = (PagedViewWidget) layout.getChildAt(0);
                    int[] maxSize = w.getPreviewSize();
                    maxPreviewWidth = maxSize[0];
                    maxPreviewHeight = maxSize[1];
                    if ((maxPreviewWidth <= 0) || (maxPreviewHeight <= 0)) {
                        if (LauncherLog.DEBUG) {
                            LauncherLog.d(TAG, "syncWidgetPageItems: maxPreviewWidth = " + maxPreviewWidth
                                + ", maxPreviewHeight = " + maxPreviewHeight);
                        }
                    }
                }

                getWidgetPreviewLoader().setPreviewSize(
                        maxPreviewWidth, maxPreviewHeight, mWidgetSpacingLayout);
                if (immediate) {
                    AsyncTaskPageData data = new AsyncTaskPageData(page, items,
                            maxPreviewWidth, maxPreviewHeight, null, null, getWidgetPreviewLoader());
                    loadWidgetPreviewsInBackground(null, data);
                    onSyncWidgetPageItems(data, immediate);
                } else {
                    if (mInTransition) {
                        mDeferredPrepareLoadWidgetPreviewsTasks.add(this);
                    } else {
                        prepareLoadWidgetPreviewsTask(page, items,
                                maxPreviewWidth, maxPreviewHeight, mWidgetCountX);
                    }
                }
                layout.setOnLayoutListener(null);
            }
        });
    }
    private void loadWidgetPreviewsInBackground(AppsCustomizeAsyncTask task,
            AsyncTaskPageData data) {
        // loadWidgetPreviewsInBackground can be called without a task to load a set of widget
        // previews synchronously
        if (task != null) {
            // Ensure that this task starts running at the correct priority
            task.syncThreadPriority();
        }

        // Load each of the widget/shortcut previews
        ArrayList<Object> items = data.items;
        ArrayList<Bitmap> images = data.generatedImages;
        int count = items.size();
        for (int i = 0; i < count; ++i) {
            if (task != null) {
                // Ensure we haven't been cancelled yet
                if (task.isCancelled()) break;
                // Before work on each item, ensure that this task is running at the correct
                // priority
                task.syncThreadPriority();
            }

            images.add(getWidgetPreviewLoader().getPreview(items.get(i)));
        }
    }

    private void onSyncWidgetPageItems(AsyncTaskPageData data, boolean immediatelySyncItems) {
        if (!immediatelySyncItems && mInTransition) {
            mDeferredSyncWidgetPageItems.add(data);
            return;
        }
        try {
            int page = data.page;
            PagedViewGridLayout layout = (PagedViewGridLayout) getPageAt(page);

            ArrayList<Object> items = data.items;
            int count = items.size();
            for (int i = 0; i < count; ++i) {
                PagedViewWidget widget = (PagedViewWidget) layout.getChildAt(i);
                if (widget != null) {
                    Bitmap preview = data.generatedImages.get(i);
                    widget.applyPreview(new FastBitmapDrawable(preview), i);
                }
            }

            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "onSyncWidgetPageItems: page = " + page + ", layout = " + layout
                    + ", count = " + count + ", this = " + this);
            }
            enableHwLayersOnVisiblePages();

            // Update all thread priorities
            Iterator<AppsCustomizeAsyncTask> iter = mRunningTasks.iterator();
            while (iter.hasNext()) {
                AppsCustomizeAsyncTask task = (AppsCustomizeAsyncTask) iter.next();
                int pageIndex = task.page;
                task.setThreadPriority(getThreadPriorityForPage(pageIndex));
            }
        } finally {
            data.cleanup(false);
        }
    }

    @Override
    public void syncPages() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "syncPages: mNumWidgetPages = " + mNumWidgetPages + ", mNumAppsPages = "
                    + mNumAppsPages + "mContentType = " + mContentType + ", this = " + this);
        }

        disablePagedViewAnimations();

        removeAllViews();
        cancelAllTasks();

        Context context = getContext();
        if (mContentType == ContentType.Applications) {
            for (int i = 0; i < mNumAppsPages; ++i) {
                AppsCustomizeCellLayout layout = new AppsCustomizeCellLayout(context);
                setupPage(layout);
                addView(layout, new PagedView.LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT));
            }
        } else if (mContentType == ContentType.Widgets) {
            for (int j = 0; j < mNumWidgetPages; ++j) {
                PagedViewGridLayout layout = new PagedViewGridLayout(context, mWidgetCountX,
                        mWidgetCountY);
                setupPage(layout);
                addView(layout, new PagedView.LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT));
            }
        } else {
            throw new RuntimeException("Invalid ContentType");
        }

        enablePagedViewAnimations();
    }

    @Override
    public void syncPageItems(int page, boolean immediate) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "syncPageItems: page = " + page + ", immediate = " + immediate
                    + ", mContentType = " + mContentType + ", mNumAppsPages = " + mNumAppsPages
                    + ",page size = " + getChildCount());
        }

        if (mContentType == ContentType.Widgets) {
            syncWidgetPageItems(page, immediate);
        } else {
            if (!mSupportEditAndHideApps) {
                syncAppsPageItems(page, immediate);
            } else {
                if (sAllAppsPage.size() <= page) {
                    return;
                }
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "syncPageItems: AppsAndFoldersCount = "
                            + sAllAppsPage.get(page).getAppsAndFoldersCount());
                }
                /// M: Sync apps page items according to page info, add for OP09.
                syncAppsPageItems(sAllAppsPage.get(page), page, immediate);
            }
        }
    }

    // We want our pages to be z-ordered such that the further a page is to the left, the higher
    // it is in the z-order. This is important to insure touch events are handled correctly.
    View getPageAt(int index) {
        return getChildAt(indexToPage(index));
    }

    @Override
    protected int indexToPage(int index) {
        return getChildCount() - index - 1;
    }

    // In apps customize, we have a scrolling effect which emulates pulling cards off of a stack.
    @Override
    protected void screenScrolled(int screenCenter) {
        super.screenScrolled(screenCenter);
        enableHwLayersOnVisiblePages();
    }

    private void enableHwLayersOnVisiblePages() {
        final int screenCount = getChildCount();

        getVisiblePages(mTempVisiblePagesRange);
        int leftScreen = mTempVisiblePagesRange[0];
        int rightScreen = mTempVisiblePagesRange[1];
        int forceDrawScreen = -1;
        if (leftScreen == rightScreen) {
            // make sure we're caching at least two pages always
            if (rightScreen < screenCount - 1) {
                rightScreen++;
                forceDrawScreen = rightScreen;
            } else if (leftScreen > 0) {
                leftScreen--;
                forceDrawScreen = leftScreen;
            }
        } else {
            forceDrawScreen = leftScreen + 1;
        }

        for (int i = 0; i < screenCount; i++) {
            final View layout = (View) getPageAt(i);
            if (!(leftScreen <= i && i <= rightScreen &&
                    (i == forceDrawScreen || shouldDrawChild(layout)))) {
                layout.setLayerType(LAYER_TYPE_NONE, null);
            }
        }

        for (int i = 0; i < screenCount; i++) {
            final View layout = (View) getPageAt(i);

            if (leftScreen <= i && i <= rightScreen &&
                    (i == forceDrawScreen || shouldDrawChild(layout))) {
                if (layout.getLayerType() != LAYER_TYPE_HARDWARE) {
                    layout.setLayerType(LAYER_TYPE_HARDWARE, null);
                }
            }
        }
    }

    protected void overScroll(float amount) {
        dampedOverScroll(amount);
    }

    /**
     * Used by the parent to get the content width to set the tab bar to
     * @return
     */
    public int getPageContentWidth() {
        return mContentWidth;
    }

    @Override
    protected void onPageEndMoving() {
        super.onPageEndMoving();
        mForceDrawAllChildrenNextFrame = true;
        // We reset the save index when we change pages so that it will be recalculated on next
        // rotation
        mSaveInstanceStateItemIndex = -1;
    }

    /*
     * AllAppsView implementation
     */
    public void setup(Launcher launcher, DragController dragController) {
        mLauncher = launcher;
        mDragController = dragController;
    }

    /**
     * We should call thise method whenever the core data changes (mApps, mWidgets) so that we can
     * appropriately determine when to invalidate the PagedView page data.  In cases where the data
     * has yet to be set, we can requestLayout() and wait for onDataReady() to be called in the
     * next onMeasure() pass, which will trigger an invalidatePageData() itself.
     */
    private void invalidateOnDataChange() {
        if (!isDataReady()) {
            // The next layout pass will trigger data-ready if both widgets and apps are set, so
            // request a layout to trigger the page data when ready.
            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "invalidateOnDataChange : Data is not ready");
            }
            requestLayout();
        } else {
            cancelAllTasks();
            invalidatePageData();
        }
    }

    public void setApps(ArrayList<AppInfo> list) {
        if (!LauncherAppState.isDisableAllApps()) {
            mApps = list;
            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "setApps : mApps = " + mApps.size() + ", mAppsHasSet = "
                        + mAppsHasSet + ", this = " + this);
            }
            Collections.sort(mApps, LauncherModel.getAppNameComparator());
            /// M: Init all apps for all apps pages for op09.
            mAppsHasSet = true;
            if (!mSupportEditAndHideApps) {
                Collections.sort(mApps, LauncherModel.getAppNameComparator());
                reorderApps();
            } else {
                initAllAppsPage();
            }
            updatePageCountsAndInvalidateData();
        }
    }

    void setItems(
            ArrayList<AppInfo> allApps,
            ArrayList<AppInfo> apps,
            ArrayList<FolderInfo> folders) {
        if (!LauncherAppState.isDisableAllApps()) {
            mApps = apps;
            sFolders = folders;
            mAppsHasSet = true;
            // TODO sort
            initAllAppsPage();
            updatePageCountsAndInvalidateData();
        }
    }

    private void addAppsWithoutInvalidate(ArrayList<AppInfo> list) {
        // We add it in place, in alphabetical order
        int count = list.size();
        for (int i = 0; i < count; ++i) {
            AppInfo info = list.get(i);
            int index = Collections.binarySearch(mApps, info, LauncherModel.getAppNameComparator());
            if (index < 0) {
                mApps.add(-(index + 1), info);
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "addAppsWithoutInvalidate: mApps size = " + mApps.size()
                            + ", index = " + index + ", info = " + info + ", this = " + this);
                }
            }
        }
    }
    public void addApps(ArrayList<AppInfo> list) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "addApps: list = " + list + ", this = " + this);
        }

        if (!LauncherAppState.isDisableAllApps()) {
            /// M: Add for op09 Edit and Hide app icons.
            if (!mSupportEditAndHideApps) {
                addAppsWithoutInvalidate(list);
                reorderApps();
            } else {
                addAddedApps(list);
                notifyPageListChanged();
            }
            updatePageCountsAndInvalidateData();
        }
    }
    private int findAppByComponent(List<AppInfo> list, AppInfo item) {
        ComponentName removeComponent = item.intent.getComponent();
        int length = list.size();
        for (int i = 0; i < length; ++i) {
            AppInfo info = list.get(i);
            if (info.user.equals(item.user)
                    && info.intent.getComponent().equals(removeComponent)) {
                return i;
            }
        }
        return -1;
    }
    private void removeAppsWithoutInvalidate(ArrayList<AppInfo> list) {
        // loop through all the apps and remove apps that have the same component
        int length = list.size();
        for (int i = 0; i < length; ++i) {
            AppInfo info = list.get(i);
            int removeIndex = findAppByComponent(mApps, info);
            if (removeIndex > -1) {
                /// M: store the remove apps in list for op09.
                sRemovedApps.add(mApps.remove(removeIndex));
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "removeAppsWithoutInvalidate: removeIndex = " + removeIndex
                            + ", ApplicationInfo info = " + info + ", this = " + this);
                }
            }
        }
    }
    public void removeApps(ArrayList<AppInfo> appInfos) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "removeApps: appInfos = " + appInfos + ",size = " + mApps.size() + ", this = " + this);
        }

        if (!LauncherAppState.isDisableAllApps()) {
            removeAppsWithoutInvalidate(appInfos);
            /// M: Add for op09 Edit and Hide app icons.
            if (!mSupportEditAndHideApps) {
                reorderApps();
            } else {
                removeDisabledApps(sRemovedApps);
                notifyPageListChanged();
                sRemovedApps.clear();
            }
            updatePageCountsAndInvalidateData();
        }
    }
    public void updateApps(ArrayList<AppInfo> list) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "updateApps: list = " + list + ", this = " + this);
        }

        // We remove and re-add the updated applications list because it's properties may have
        // changed (ie. the title), and this will ensure that the items will be in their proper
        // place in the list.
        if (!LauncherAppState.isDisableAllApps()) {
            /// M: Add for op09 Edit and Hide app icons.
            if (!mSupportEditAndHideApps) {
                // We remove and re-add the updated applications list because it's
                // properties may have changed (ie. the title), and this will ensure
                // that the items will be in their proper place in the list.
                removeAppsWithoutInvalidate(list);
                addAppsWithoutInvalidate(list);
                reorderApps();
            }
            updatePageCountsAndInvalidateData();
        }
    }

    public void reset() {
        // If we have reset, then we should not continue to restore the previous state
        mSaveInstanceStateItemIndex = -1;

        if (mContentType != ContentType.Applications) {
            setContentType(ContentType.Applications);
        }

        if (mCurrentPage != 0) {
            invalidatePageData(0);
        }
    }

    private AppsCustomizeTabHost getTabHost() {
        return (AppsCustomizeTabHost) mLauncher.findViewById(R.id.apps_customize_pane);
    }

    public void dumpState() {
        // TODO: Dump information related to current list of Applications, Widgets, etc.
        AppInfo.dumpApplicationInfoList(TAG, "mApps", mApps);
        dumpAppWidgetProviderInfoList(TAG, "mWidgets", mWidgets);
    }

    private void dumpAppWidgetProviderInfoList(String tag, String label,
            ArrayList<Object> list) {
        Log.d(tag, label + " size=" + list.size());
        for (Object i: list) {
            if (i instanceof AppWidgetProviderInfo) {
                AppWidgetProviderInfo info = (AppWidgetProviderInfo) i;
                Log.d(tag, "   label=\"" + info.label + "\" previewImage=" + info.previewImage
                        + " resizeMode=" + info.resizeMode + " configure=" + info.configure
                        + " initialLayout=" + info.initialLayout
                        + " minWidth=" + info.minWidth + " minHeight=" + info.minHeight);
            } else if (i instanceof ResolveInfo) {
                ResolveInfo info = (ResolveInfo) i;
                Log.d(tag, "   label=\"" + info.loadLabel(mPackageManager) + "\" icon="
                        + info.icon);
            }
        }
    }

    public void surrender() {
        // TODO: If we are in the middle of any process (ie. for holographic outlines, etc) we
        // should stop this now.

        // Stop all background tasks
        cancelAllTasks();
    }

    /*
     * We load an extra page on each side to prevent flashes from scrolling and loading of the
     * widget previews in the background with the AsyncTasks.
     */
    final static int sLookBehindPageCount = 2;
    final static int sLookAheadPageCount = 2;
    protected int getAssociatedLowerPageBound(int page) {
        final int count = getChildCount();
        int windowSize = Math.min(count, sLookBehindPageCount + sLookAheadPageCount + 1);
        /// M: modify to cycle sliding screen for OP09.
        int windowMinIndex = Math.max(Math.min(page - sLookBehindPageCount, count - windowSize),
                isSupportCycleSlidingScreen() ? -1 : 0); /// M: Add for op09 SysleSlidingScreen.
        return windowMinIndex;
    }
    protected int getAssociatedUpperPageBound(int page) {
        final int count = getChildCount();
        int windowSize = Math.min(count, sLookBehindPageCount + sLookAheadPageCount + 1);
        /// M: modify to cycle sliding screen for OP09.
        int windowMaxIndex = Math.min(Math.max(page + sLookAheadPageCount, windowSize - 1),
                isSupportCycleSlidingScreen() ? count : count - 1);
        /// M: Add for op09 CycleSlidingScreen.
        return windowMaxIndex;
    }

    protected String getCurrentPageDescription() {
        int page = (mNextPage != INVALID_PAGE) ? mNextPage : mCurrentPage;
        int stringId = R.string.default_scroll_format;
        int count = 0;

        if (mContentType == ContentType.Applications) {
            stringId = R.string.apps_customize_apps_scroll_format;
            count = mNumAppsPages;
        } else if (mContentType == ContentType.Widgets) {
            stringId = R.string.apps_customize_widgets_scroll_format;
            count = mNumWidgetPages;
        } else {
            throw new RuntimeException("Invalid ContentType");
        }

        return String.format(getContext().getString(stringId), page + 1, count);
    }

        /**
     * M: Reorder apps in applist.
     */
    public void reorderApps() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "reorderApps: mApps = " + mApps + ", this = " + this);
        }
        if (AllAppsList.sTopPackages == null || mApps == null || mApps.isEmpty()
                || AllAppsList.sTopPackages.isEmpty()) {
            return;
        }

        ArrayList<AppInfo> dataReorder = new ArrayList<AppInfo>(
                AllAppsList.DEFAULT_APPLICATIONS_NUMBER);

        for (AllAppsList.TopPackage tp : AllAppsList.sTopPackages) {
            for (AppInfo ai : mApps) {
                if (ai.componentName.getPackageName().equals(tp.packageName)
                        && ai.componentName.getClassName().equals(tp.className)) {
                    mApps.remove(ai);
                    dataReorder.add(ai);
                    break;
                }
            }
        }
        LauncherLog.d(TAG, "before first split, reorderApps: mApps = " + mApps
                      + ", dataReorder = " + dataReorder);

        for (AllAppsList.TopPackage tp : AllAppsList.sTopPackages) {
            int newIndex = 0;
            for (AppInfo ai : dataReorder) {
                if (ai.componentName.getPackageName().equals(tp.packageName)
                        && ai.componentName.getClassName().equals(tp.className)) {
                    newIndex = Math.min(Math.max(tp.order, 0), mApps.size());
                    //LauncherLog.d(TAG, "reorderApps: newIndex = " + newIndex + ", ai=" + ai);
                    mApps.add(newIndex, ai);
                    break;
                }
            }
        }
        LauncherLog.d(TAG, "after second split, reorderApps: mApps = " + mApps
                       + ", dataReorder = " + dataReorder);
    }

    /**
     * M: Support cycle sliding screen or not.
     * @return true: support cycle sliding screen.
     */
    public boolean isSupportCycleSlidingScreen() {
        return mSupportCycleSliding;
    }

    ///M: Added for unread message feature.@{
    /**
     * M: Update unread number of the given component in app customize paged view
     * with the given value, first find the icon, and then update the number.
     * NOTES: since maybe not all applications are added in the customize paged
     * view, we should update the apps info at the same time.
     *
     * @param component
     * @param unreadNum
     */
    public void updateAppsUnreadChanged(ComponentName component, int unreadNum) {
        if (LauncherLog.DEBUG_UNREAD) {
            LauncherLog.d(TAG, "updateAppsUnreadChanged: component = " + component
                    + ",unreadNum = " + unreadNum + ",mNumAppsPages = " + mNumAppsPages);
        }
        updateUnreadNumInAppInfo(component, unreadNum);
        for (int i = 0; i < mNumAppsPages; i++) {
            View view = getPageAt(i);
            if(view != null && view instanceof AppsCustomizeCellLayout){
                AppsCustomizeCellLayout cl = (AppsCustomizeCellLayout)view;
                if(cl == null){
                    LauncherLog.d(TAG, "updateAppsUnreadChanged: cl == null");
                    continue;
                }
                ShortcutAndWidgetContainer container = (ShortcutAndWidgetContainer) cl.getShortcutsAndWidgets();
                if(container == null){
                    LauncherLog.d(TAG, "updateAppsUnreadChanged: container == null");
                    continue;
                }
                final int count = container.getChildCount();
                if (LauncherLog.DEBUG_UNREAD) {
                    LauncherLog.d(TAG, "updateAppsUnreadChanged: getPageChildCount() == " + count);
                }
                BubbleTextView appIcon = null;
                AppInfo appInfo = null;
                for (int j = 0; j < count; j++) {
                    appIcon = (BubbleTextView) container.getChildAt(j);
                    if (appIcon == null) {
                        continue;
                    }
                    appInfo = (AppInfo) appIcon.getTag();
                    if (LauncherLog.DEBUG_UNREAD) {
                        LauncherLog
                                .d(TAG, "updateAppsUnreadChanged: component = " + component
                                        + ", appInfo = " + appInfo.componentName + ", appIcon = "
                                        + appIcon);
                    }
                    if (appInfo != null && appInfo.componentName.equals(component)) {
                        appInfo.unreadNum = unreadNum;
                        appIcon.invalidate();
                    }
                }
            }
        }
    }

    /**
     * M: Update unread number of all application info with data in MTKUnreadLoader.
     */
    public void updateAppsUnread() {
        if (LauncherLog.DEBUG_UNREAD) {
            LauncherLog.d(TAG, "updateAppsUnreadChanged: mNumAppsPages = " + mNumAppsPages);
        }

        updateUnreadNumInAppInfo(mApps);
        // Update apps which already shown in the customized pane.
        for (int i = 0; i < mNumAppsPages; i++) {
            View view = getPageAt(i);
            if (view != null && view instanceof AppsCustomizeCellLayout) {
                AppsCustomizeCellLayout cl = (AppsCustomizeCellLayout)view;
                if(cl == null){
                    LauncherLog.d(TAG, "updateAppsUnread: cl == null");
                    continue;
                }
                View childView = cl.getChildAt(0);
                if(!(childView instanceof ShortcutAndWidgetContainer)){
                    LauncherLog.d(TAG, "updateAppsUnread: childView is not ShortcutAndWidgetContainer instance");
                    continue;
                }
                ShortcutAndWidgetContainer container = (ShortcutAndWidgetContainer) childView;
                if(container == null){
                    LauncherLog.d(TAG, "updateAppsUnread: container == null");
                    continue;
                }
                final int count = container.getChildCount();
                /* M: TODO replace PagedViewIcon
                PagedViewIcon appIcon = null;
                AppInfo appInfo = null;
                for (int j = 0; j < count; j++) {
                    appIcon = (PagedViewIcon) container.getChildAt(j);
                    if (appIcon == null) {
                        continue;
                    }
                    appInfo = (AppInfo) appIcon.getTag();
                    if(appInfo == null){
                        LauncherLog.d(TAG, "updateAppsUnread: appInfo == null");
                        continue;
                    }
                    appInfo.unreadNum = MTKUnreadLoader
                            .getUnreadNumberOfComponent(appInfo.componentName);
                    appIcon.invalidate();
                    if (LauncherLog.DEBUG_UNREAD) {
                        LauncherLog.d(TAG, "updateAppsUnreadChanged: i = " + i + ", appInfo = "
                                + appInfo.componentName + ", unreadNum = " + appInfo.unreadNum);
                    }
                }*/
            }
        }
    }

    /**
     * M: Update the unread number of the app info with given component.
     *
     * @param component
     * @param unreadNum
     */
    private void updateUnreadNumInAppInfo(ComponentName component, int unreadNum) {
        final int size = mApps.size();
        AppInfo appInfo = null;
        for (int i = 0; i < size; i++) {
            appInfo = mApps.get(i);
            if (appInfo.intent.getComponent().equals(component)) {
                appInfo.unreadNum = unreadNum;
            }
        }
    }

    /**
     * M: Update unread number of all application info with data in MTKUnreadLoader.
     *
     * @param apps
     */
    public static void updateUnreadNumInAppInfo(final ArrayList<AppInfo> apps) {
        if(apps == null){
            if (LauncherLog.DEBUG_UNREAD) {
                LauncherLog.d(TAG, "updateUnreadNumInAppInfo: apps == null");
            }
            return;
        }
        final int size = apps.size();
        AppInfo appInfo = null;
        for (int i = 0; i < size; i++) {
            appInfo = apps.get(i);
            appInfo.unreadNum = MTKUnreadLoader.getUnreadNumberOfComponent(appInfo.componentName);
        }
    }
    ///M: @}

    //M:[OP09] @{
    /**
     * M: Enter edit mode, allow user to rearrange application icons.
     */
    public void enterEditMode() {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "enterEditMode: mNumWidgetPages = " + mNumWidgetPages
                    + ", mNumAppsPages = " + mNumAppsPages + ",mCurrentPage = " + mCurrentPage);
        }
        mIsInEditMode = true;
        //resetDataIsReady();

        // A trick, we make the widget page number as 0, so we can not scroll to
        // widget page in edit mode.
        mNumWidgetPages = 0;
        // Add a new empty page at the tail.
        addNewEmptyAppsPage();
        invalidatePageData(mCurrentPage);

        // Make apps customized pane can receive drag and drop event.
        mDragController.setDragScoller(this);
        mDragController.setMoveTarget(this);
        mDragController.addDropTarget(this);
        mDragController.addDragListener(this);

        Launcher.enablePendingAppsQueue();

    }

    private boolean mIsInEditMode;

    /**
     * M: Exit edit mode.
     */
    public void exitEditMode() {
        mIsInEditMode = false;
        mNumWidgetPages = (int) Math
                .ceil(mWidgets.size() / (float) (mWidgetCountX * mWidgetCountY));
        LauncherLog.d(TAG, "exitEditMode:foreachPage");
        foreachPage();
        removeEmptyPages();

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "exitEditMode: mNumWidgetPages = " + mNumWidgetPages
                    + ", mNumAppsPages = " + mNumAppsPages + ", mCurrentPage = " + mCurrentPage);
        }

        invalidatePageData(mCurrentPage);

        // Make apps customized pane can't receive drag and drop event when exit edit mode.
        mDragController.setDragScoller(mLauncher.getWorkspace());
        mDragController.setMoveTarget(mLauncher.getWorkspace());
        mDragController.removeDropTarget(this);
        mDragController.removeDragListener(this);

        //check folder is closed or not
        mLauncher.closeFolder();
        Launcher.disableAndFlushPendingAppsQueue(this);
    }

    /**
     * M: Whether need to show the update and hide icon.
     *
     * @return True if in apps page, else false.
     */
    public boolean needShowEditAndHideIcon() {
        return (mNumAppsPages <= 0 || mCurrentPage < mNumAppsPages);
    }

    /**
     * M: When drag enter, it will be called.
     * @param d the drag info which view has been drag
     */
    @Override
    public void onDragEnter(DragObject d) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "onDragEnter: d = " + d + ", mDragTargetLayout = "
                    + mDragTargetLayout);
        }

        mDragEnforcer.onDragEnter();
        //M:[OP09][CF] @{
        mCreateUserFolderOnDrop = false;
        mAddToExistingFolderOnDrop = false;
        //M:[OP09][CF] }@

        mDropToLayout = null;
        AppsCustomizeCellLayout layout = getCurrentDropLayout();
        setCurrentDropLayout(layout);

        // Clear previous target cell.
        mPreviousTargetCell[0] = -1;
        mPreviousTargetCell[1] = -1;
    }

    /**
     * M: When drag an icon, it will be called.
     * @param d the drag info which view has been drag
     */
    @Override
    public void onDragOver(DragObject d) {
        // Skip drag over events while we are dragging over side pages
        if (mInScrollArea) {
            LauncherLog.d(TAG, "onDragOver: mInScrollArea is true.");
            return;
        }

        ItemInfo item = (ItemInfo) d.dragInfo;
        //float[] r = getDragViewVisualCenter(d.x, d.y, d.xOffset, d.yOffset, d.dragView, null);
        mDragViewVisualCenter = getDragViewVisualCenter(d.x, d.y, d.xOffset, d.yOffset,
            d.dragView, mDragViewVisualCenter);
        AppsCustomizeCellLayout layout = getCurrentDropLayout();
        mTargetCell = layout.findNearestArea((int) mDragViewVisualCenter[0],
                          (int) mDragViewVisualCenter[1], 1, 1, mTargetCell);

        final View child = (mDragInfo == null) ? null : mDragInfo.cell;
        LauncherLog.d(TAG, "onDragOver: mTargetCell[0]=" + mTargetCell[0]
            + ",mTargetCell[1]=" + mTargetCell[1]
            + ", mPreviousTargetCell[0]=" + mPreviousTargetCell[0]
            + ", mPreviousTargetCell[1]=" + mPreviousTargetCell[1]
            + ", mPrevDropTarget=" + mPrevDropTarget
            + ", mCurrentDropTarget=" + mCurrentDropTarget
            + ", mDragTargetLayout=" + mDragTargetLayout);

        // If drag icon from another page.
        if (mPrevDropTarget != null && mPrevDropTarget != mCurrentDropTarget) {
            mPreviousTargetCell[0] = -1;
            mPreviousTargetCell[1] = -1;
            mPrevDropTarget = null;

            mEmptyCell[0] = -1;
            mEmptyCell[1] = -1;

            // If the page is full, process last cell in the page.
            setCurrentDropLayout(layout);
         }

         //if empty cell is -1, then find empty cell
         findEmptyCell(layout);

        // Handle the drag over
        if (mDragTargetLayout != null) {
            setCurrentDropOverCell(mTargetCell[0], mTargetCell[1]);
            float targetCellDistance = mDragTargetLayout.getDistanceFromCell(
                    mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell);
            LauncherLog.d(TAG, "onDragOver: r[0]=" + mDragViewVisualCenter[0]
                + ",r[1]=" + mDragViewVisualCenter[1]
                + ",targetCellDistance=" + targetCellDistance
                + ",maxDistance=" + mMaxDistanceForFolderCreation);
            final View dragOverView = mDragTargetLayout.getChildAt(mTargetCell[0],
                    mTargetCell[1]);

            boolean folderCreate = manageFolderFeedback((ItemInfo) d.dragInfo,
                    mDragTargetLayout, mTargetCell,
                    targetCellDistance, dragOverView);

            if (folderCreate) {
                mReorderAlarm.cancelAlarm();
            }
            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "onDragOver: mTargetCell = (" + mTargetCell[0] + ", "
                        + mTargetCell[1] + "), mEmptyCell = ("
                        + mEmptyCell[0] + ", " + mEmptyCell[1]
                        + "), mLastXY=(" + mLastReorderX + "." + mLastReorderY
                        + "). folderCreate = " + folderCreate);
            }

            boolean nearestDropOccupied = mDragTargetLayout.isNearestDropLocationOccupied((int)
                    mDragViewVisualCenter[0], (int) mDragViewVisualCenter[1], item.spanX,
                    item.spanY, child, mTargetCell);

            if (mPreviousTargetCell[0] != mTargetCell[0] ||
                    mPreviousTargetCell[1] != mTargetCell[1]) {
                mPreviousTargetCell[0] = mTargetCell[0];
                mPreviousTargetCell[1] = mTargetCell[1];
            }

            if (!folderCreate && (mDragMode == Workspace.DRAG_MODE_NONE
                    || mDragMode == Workspace.DRAG_MODE_REORDER)
                    && !mReorderAlarm.alarmPending()
                    && (mLastReorderX != mTargetCell[0] ||
                    mLastReorderY != mTargetCell[1])) {
                    LauncherLog.d(TAG, "onDragOver realTimeReorderxx, empty = ("
                                    + mEmptyCell[0] + "." + mEmptyCell[1]
                                    + "), target = (" + mTargetCell[0]
                                    + "," + mTargetCell[1] + ")");
                    mReorderAlarm.cancelAlarm();
                    mReorderAlarm.setOnAlarmListener(mReorderAlarmListener);
                    mReorderAlarm.setAlarm(350);
            }

             if (mDragMode == Workspace.DRAG_MODE_CREATE_FOLDER
                   || mDragMode == Workspace.DRAG_MODE_ADD_TO_FOLDER ||
                    !nearestDropOccupied) {
                if (mDragTargetLayout != null) {
                    mDragTargetLayout.revertTempState();
                }
            }
        }
    }

    /**
     *M: When drag exit, it will be called.
     *@param d the drag item
     */
    @Override
    public void onDragExit(DragObject d) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "onDragExit: d = " + d);
        }

        LauncherLog.d(TAG, "onDragExit: mInScrollArea = " + mInScrollArea
             + ", mEmptyCell[0] = " + mEmptyCell[0]
             + ", mEmptyCell[1] = " + mEmptyCell[1]
             + ", mDragMode = " + mDragMode);
        mDragEnforcer.onDragExit();

        // Here we store the final page that will be dropped to, there will be a
        // drop after the exit if mInScrollArea is true, we need to make sure
        // the empty cell right.
        if (mInScrollArea) {
            mDropToLayout = getCurrentDropLayout();
            // If drag icon from another page.
            if (mPrevDropTarget != null && mPrevDropTarget != mCurrentDropTarget) {
                mPreviousTargetCell[0] = -1;
                mPreviousTargetCell[1] = -1;
                mPrevDropTarget = null;

                mEmptyCell[0] = -1;
                mEmptyCell[1] = -1;

                // If the page is full, process last cell in the page, empty
                // cell will be updated here.
                saveLastCellInFullPage(getCurrentDropLayout(), true);
            }
            mInScrollArea = false;
        } else {
            mDropToLayout = mDragTargetLayout;
        }

        if ((mEmptyCell[0] == -1 || mEmptyCell[1] == -1)
            && (mDragMode != Workspace.DRAG_MODE_CREATE_FOLDER
            && mDragMode != Workspace.DRAG_MODE_ADD_TO_FOLDER)) {
            // If the page is full, process last cell in the page, empty
            // cell will be updated here.
            saveLastCellInFullPage(getCurrentDropLayout(), true);
        }
        if (mDragMode == Workspace.DRAG_MODE_CREATE_FOLDER) {
            mCreateUserFolderOnDrop = true;
        } else if (mDragMode == Workspace.DRAG_MODE_ADD_TO_FOLDER) {
            mAddToExistingFolderOnDrop = true;
        }

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "onDragExit: drag source = " + (d != null ? d.dragSource : null)
                    + ", drag info = " + (d != null ? d.dragInfo : null) + ", mDragTargetLayout = "
                    + mDragTargetLayout + ", mIsPageMoving = " + mIsPageMoving);
        }
        setCurrentDropLayout(null);
    }

    @Override
    public void onDrop(DragObject d) {
        boolean add = false;
        AppsCustomizeCellLayout layout = null;
        boolean updateTargetCell = false;
        int screen = 0;
        mDragViewVisualCenter = getDragViewVisualCenter(d.x, d.y, d.xOffset, d.yOffset, d.dragView,
                mDragViewVisualCenter);

        AppsCustomizeTabHost host = (AppsCustomizeTabHost) getTabHost();
        CellLayout dropTargetLayout = mDropToLayout;

        // We want the point to be mapped to the dragTarget.
        if (dropTargetLayout != null) {
            mapPointFromSelfToChild(dropTargetLayout, mDragViewVisualCenter, null);
        }

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "onDrop 1:  drag view = " + d.dragView + ", mTargetCell[0] = "
                    + mTargetCell[0] + ", mTargetCell[1]  = " + mTargetCell[1]
                    + ", mEmptyCell[0] = " + mEmptyCell[0] + ", mEmptyCell[1]  = " + mEmptyCell[1]
                    + ", dragInfo = " + d.dragInfo + ", dragSource  = " + d.dragSource
                    + ", dropTargetLayout = " + dropTargetLayout + ", mDragInfo = " + mDragInfo
                    + ", this = " + this);
        }
        if (d.dragSource != this) {
             final int[] touchXY = new int[] { (int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1] };
             onDropExternal(touchXY, d.dragInfo, dropTargetLayout, false, d);
         } else if (mDragInfo != null) {
            final View cell = mDragInfo.cell;

            // If drag an icon into the scroll area, or the top layout, its
            // dropTargetLayout will be null, and find the nearest cell the
            // dragObject will be placed.
            if (dropTargetLayout == null) {
                dropTargetLayout = mCurrentDropTarget;
                mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], 1, 1, dropTargetLayout, mTargetCell);
            } else {
                updateTargetCell = true;
            }

            if (dropTargetLayout.isExceedLastPosition(mTargetCell)) {
                mTargetCell = dropTargetLayout.getLastPosition(mTargetCell);
                if (updateTargetCell) {
                    updateTargetCell = false;
                }
            }
            LauncherLog.d(TAG, "onDrop 1:  mTargetCell[0]=" + mTargetCell[0]
                 + ", mTargetCell[1]=" + mTargetCell[1]
                 + ", updateTargetCell= " + updateTargetCell);

            screen = (mTargetCell[0] < 0) ? (int) mDragInfo.screenId
                    : indexToPage(indexOfChild(dropTargetLayout));

            if (mCurrentPage != screen) {
                snapToPage(screen);
            }

            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "onDrop 2: mCurrentPage = " + mCurrentPage + ",screen = "
                        + screen + ", mTargetCell[0] = " + mTargetCell[0]
                        + ", mTargetCell[1]  = " + mTargetCell[1] + ",child count = "
                        + getChildCount() + ", this = " + this + ", add = " + add);
            }

            layout = (AppsCustomizeCellLayout) getPageAt(screen);
             //M:[OP09][CF] @{
            float distance = dropTargetLayout.getDistanceFromCell(mDragViewVisualCenter[0],
                        mDragViewVisualCenter[1], mTargetCell);
            LauncherLog.d(TAG, "onDrop 2: createUserFolderIfNecessary,mInScrollArea="
                  + mInScrollArea + ",distance=" + distance
                  + ",center[0]=" + mDragViewVisualCenter[0]
                  + ",center[1]=" + mDragViewVisualCenter[1]
                  + ", mTargetCell[0]=" + mTargetCell[0] + ",cell[1]=" + mTargetCell[1]);

            layout = (AppsCustomizeCellLayout) getPageAt(screen);
            if (!mInScrollArea && createUserFolderIfNecessary(cell,
                    dropTargetLayout, mTargetCell, distance, false, d.dragView, null)) {
                //removeExtraEmptyScreen(true, null, 0, true);
                add = true;
                LauncherLog.d(TAG, "onDrop 2: createUserFolderIfNecessary, reoder again.");
                // Realtime reorder all icons position.
                reorderForFolderCreateOrDelete(mEmptyCell, mTargetCell, layout, true);
                mOccuredReorder = false;
            }

            if (addToExistingFolderIfNecessary(cell, dropTargetLayout, mTargetCell,
                    distance, d, false)) {
                LauncherLog.d(TAG, "onDrop 2: addToExistingFolderIfNecessary, reoder again.");
                //removeExtraEmptyScreen(true, null, 0, true);
                add = true;
                // Realtime reorder all icons position.
                reorderForFolderCreateOrDelete(mEmptyCell, mTargetCell, layout, true);
                mOccuredReorder = false;
            }
            //M:[OP09][CF] }@
            LauncherLog.d(TAG, "onDrop 2: add=" + add + ",updateTargetCell=" + updateTargetCell);
            if (!add) {
                if (updateTargetCell) {
                    updateTargetCell = false;
                    mTargetCell[0] = mEmptyCell[0];
                    mTargetCell[1] = mEmptyCell[1];
                }
                View child = null;
                if (layout != null) {
                    child = layout.getChildAt(mTargetCell[0], mTargetCell[1]);
                }

                // If has the view in the target cell
                if (child != null) {
                    if ((int) mDragInfo.screenId != screen) {
                        // If the page is full, process the last cell.
                        saveLastCellInFullPage(layout, (int) mDragInfo.screenId != screen);
                    }
                    // Realtime reorder all icons position.
                    LauncherLog.d(TAG, "onDrop realTimeReorder, mEmpty[0] = "
                         + mEmptyCell[0] + "emptyY = " + mEmptyCell[1]
                         + ", targetX=" + mTargetCell[0] + ", targetY=" + mTargetCell[1]);
                    realTimeReorder(mEmptyCell, mTargetCell, layout);
                }
                // Update item position after drop.
                updateItemPositionAfterDrop(layout, cell, screen);
                mOccuredReorder = false;
                final AppsCustomizeCellLayout parent =
                       (AppsCustomizeCellLayout) cell.getParent().getParent();
                parent.onDropChild(cell);
            } else {
                // Update all apps position in the page after realTimeReorder.
                updateItemLocationsInDatabase(layout);
            }

            d.deferDragViewCleanupPostAnimation = false;
            mPrevDropTarget = null;
        }
         LauncherLog.d(TAG, "onDrop complete:  mTargetCell = (" + mTargetCell[0]
            + "." + mTargetCell[1]
            + "). mEmptyCell = (" + mEmptyCell[0]
            + "." + mEmptyCell[1] + ").");
    }

    @Override
    public boolean acceptDrop(DragObject dragObject) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "acceptDrop: edit mode = " + Launcher.isInEditMode()
                    + ",dragObject = " + dragObject);
        }

        //M:[OP09][CF] @{
        if (mSupportEditAndHideApps) {
            CellLayout dropTargetLayout = mDropToLayout;
            mDragViewVisualCenter = getDragViewVisualCenter(dragObject.x, dragObject.y,
                    dragObject.xOffset, dragObject.yOffset,
                    dragObject.dragView, mDragViewVisualCenter);
            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], 1, 1, dropTargetLayout,
                    mTargetCell);
            float distance = dropTargetLayout.getDistanceFromCell(mDragViewVisualCenter[0],
                    mDragViewVisualCenter[1], mTargetCell);
            if (mCreateUserFolderOnDrop && willCreateUserFolder((ItemInfo) dragObject.dragInfo,
                    dropTargetLayout, mTargetCell, distance, true,
                    mDragInfo, mCreateUserFolderOnDrop)) {
                return true;
            }

            if (mAddToExistingFolderOnDrop && willAddToExistingUserFolder(
                    (ItemInfo) dragObject.dragInfo,
                    dropTargetLayout, mTargetCell, distance)) {
                return true;
            }
        }
        return Launcher.isInEditMode();
    }

    @Override
    public void getHitRect(Rect outRect) {
        outRect.set(0, 0, mDisplaySize.x, mDisplaySize.y);

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "getHitRect: outRect = " + outRect);
        }
    }

    @Override
    public void getLocationInDragLayer(int[] loc) {
        mLauncher.getDragLayer().getLocationInDragLayer(getTabHost(), loc);
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "getLocationInDragLayer: loc = (" + loc[0] + "," + loc[1] + ")");
        }
    }

    @Override
    public boolean isDropEnabled() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "isDropEnabled: edit mode = " + Launcher.isInEditMode());
        }
        return Launcher.isInEditMode();
    }

    @Override
    public void onFlingToDelete(DragObject d, int x, int y, PointF vec) {
        // We don't need to fling delete items in apps customize pane.
    }

    @Override
    public void onDragStart(DragSource source, Object info, int dragAction) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onDragStart: source = " + source + ", info = " + info
                    + ", dragAction = " + dragAction + ", mDragInfo = " + mDragInfo);
        }
        mIsDragOccuring = true;
        mLauncher.lockScreenOrientation();

        if (mSupportEditAndHideApps && (source instanceof Folder)) {
            mPrevEmptyCell[0] = -1;
            mPrevEmptyCell[1] = -1;

            mEmptyCell[0] = -1;
            mEmptyCell[1] = -1;
        }
    }

    @Override
    public void onDragEnd() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onDragEnd: mIsDragOccuring = " + mIsDragOccuring);
        }
        mIsDragOccuring = false;
        mLauncher.unlockScreenOrientation(false);
    }

    @Override
    public void scrollLeft() {
        if (mSupportCycleSliding) {
            final boolean scrollFinished = mScroller.isFinished();
            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "scrollLeft: mCurrentPage = " + mCurrentPage
                        + ", mNextPage = " + mNextPage
                        + ", scrollFinished = " + scrollFinished);
            }
            if (scrollFinished) {
                if (mCurrentPage > 0) {
                    mPrevDropTarget = (AppsCustomizeCellLayout) getPageAt(mCurrentPage);
                    mCurrentDropTarget = (AppsCustomizeCellLayout) getPageAt(mCurrentPage - 1);
                } else if (isSupportCycleSlidingScreen() && mCurrentPage == 0) {
                    // We need also to roll back the reorder record of the first page
                    // when cycle sliding is supported.
                    mPrevDropTarget = (AppsCustomizeCellLayout) getPageAt(mCurrentPage);
                    mCurrentDropTarget = (AppsCustomizeCellLayout) getPageAt(getChildCount() - 1);
                }
                // Rollback the prev screen realtime reorder.
                rollbackLastScreenItemsPosition(mCurrentPage);
            } else {
                // TODO: in what condition will enter this statement.
                if (mNextPage > 0) {
                    mPrevDropTarget = (AppsCustomizeCellLayout) getPageAt(mNextPage);
                    mCurrentDropTarget = (AppsCustomizeCellLayout) getPageAt(mNextPage - 1);
                    // Rollback the prev screen realtime reorder.
                    rollbackLastScreenItemsPosition(mNextPage);
                }
            }
        }

        super.scrollLeft();
    }

    @Override
    public void scrollRight() {
        if (mSupportCycleSliding) {
            final boolean scrollFinished = mScroller.isFinished();
            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "scrollRight: mCurrentPage = " + mCurrentPage
                        + ", mNextPage = " + mNextPage
                        + ", scrollFinished = " + scrollFinished);
            }
            if (scrollFinished) {
                if (mCurrentPage < getChildCount() - 1) {
                    mPrevDropTarget = (AppsCustomizeCellLayout) getPageAt(mCurrentPage);
                    mCurrentDropTarget = (AppsCustomizeCellLayout) getPageAt(mCurrentPage + 1);
                } else if (isSupportCycleSlidingScreen() && mCurrentPage == getChildCount() - 1) {
                    // We need also to roll back the reorder record of the last page
                    // when cycle sliding is supported.
                    mPrevDropTarget = (AppsCustomizeCellLayout) getPageAt(mCurrentPage);
                    mCurrentDropTarget = (AppsCustomizeCellLayout) getPageAt(0);
                }
                // Rollback the prev screen realtime reorder.
                rollbackLastScreenItemsPosition(mCurrentPage);
            } else {
                if (mNextPage < getChildCount() - 1) {
                    mPrevDropTarget = (AppsCustomizeCellLayout) getPageAt(mNextPage);
                    mCurrentDropTarget = (AppsCustomizeCellLayout) getPageAt(mNextPage + 1);
                    // Rollback the prev screen realtime reorder.
                    rollbackLastScreenItemsPosition(mNextPage);
                }
            }
        }

        super.scrollRight();
    }

    /**
     * M: In edit mode, drag an icon into scroll area, will snap to next page.
     * @param x the touch x position
     * @param y the touch y position
     * @param direction the direction
     * @return enter scroll or not
     */
    @Override
    public boolean onEnterScrollArea(int x, int y, int direction) {
        boolean result = false;
        mInScrollArea = true;
        int page = getNextPage() + (direction == DragController.SCROLL_LEFT ? -1 : 1);
        final int childCount = getChildCount();

        // Make it can scroll circle when dragging app icon in all apps list.
        if (isSupportCycleSlidingScreen()) {
            if (direction == DragController.SCROLL_RIGHT && page == childCount) {
                page = 0;
            } else if (direction == DragController.SCROLL_LEFT && page == -1) {
                page = childCount - 1;
            }
        }

        // We always want to exit the current layout to ensure parity of enter / exit
        setCurrentDropLayout(null);
        if (0 <= page && page < childCount) {
            result = true;
        }

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "onEnterScrollArea: page = " + page + ", result = " + result);
        }

        return result;
    }

    /**
     * M: In edit mode, when drag an icon leave scroll area.
     * @return whether exit scroll area or not
     */
    @Override
    public boolean onExitScrollArea() {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "onExitScrollArea: mInScrollArea = " + mInScrollArea);
        }
        boolean result = false;
        if (mInScrollArea) {
            invalidate();
            AppsCustomizeCellLayout layout = getCurrentDropLayout();
            setCurrentDropLayout(layout);
            result = true;
            mInScrollArea = false;
        }
        return result;
    }

    @Override
    public void getHitRectRelativeToDragLayer(Rect outRect) {
        // We want the workspace to have the whole area of the display (it will find the correct
        // cell layout to drop to in the existing drag/drop logic.
        mLauncher.getDragLayer().getDescendantRectRelativeToSelf(this, outRect);
    }

    /**
     * M: Set current drop layout.
     *
     * @param layout
     */
    void setCurrentDropLayout(AppsCustomizeCellLayout layout) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "setCurrentDropLayout layout = " + layout + ", mDragTargetLayout = "
                    + mDragTargetLayout);
        }
        if (mDragTargetLayout != null) {
            mDragTargetLayout.revertTempState();
            mDragTargetLayout.onDragExit();
        }

        mDragTargetLayout = layout;

        if (mDragTargetLayout != null) {
            mDragTargetLayout.onDragEnter();
        }
        cleanupReorder(true);
        cleanupFolderCreation();
        setCurrentDropOverCell(-1, -1);
    }

    /**
     * M: Return the current {@link CellLayout}, correctly picking the
     * destination screen while a scroll is in progress.
     * @return the current drop layout
     */
    public AppsCustomizeCellLayout getCurrentDropLayout() {
        return (AppsCustomizeCellLayout) getPageAt(getNextPage());
    }

    /**
     * M: Returns a list of all the CellLayouts in the
     * AppsCustomizePagedView.
     */
    private ArrayList<AppsCustomizeCellLayout> getAppsCustomizeCellLayouts() {
        ArrayList<AppsCustomizeCellLayout> layouts = new ArrayList<AppsCustomizeCellLayout>();
        int childCount = getChildCount();
        View layout = null;
        for (int i = 0; i < childCount; i++) {
            layout = getChildAt(i);
            if (layout instanceof AppsCustomizeCellLayout) {
                layouts.add(((AppsCustomizeCellLayout) getChildAt(i)));
            }
        }
        return layouts;
    }

    /**
     * M: Returns a specific CellLayout.
     */
    private AppsCustomizeCellLayout getParentAppsCustomizeCellLayoutForView(final View v) {
        // TODO: shall we use tag to record the parent info(such as screen
        // index) of MTKAppIcon instead of search.
        ArrayList<AppsCustomizeCellLayout> layouts = getAppsCustomizeCellLayouts();
        for (AppsCustomizeCellLayout layout : layouts) {
            int index = layout.indexOfChildOnPage(v);
            if (index > -1) {
                return layout;
            }
        }
        return null;
    }

    /**
     * M: Add a view in the screen.
     *
     * @param child: The view will be added.
     * @param screen: The screen which the view will be added to.
     * @param x: The x position the view will be placed.
     * @param y: Tye y position the view will be placed.
     * @param spanX
     * @param spanY
     * @param insert
     */
    private void addInScreen(View child, int screen, int x, int y, int spanX,
                                    int spanY, boolean insert) {
        final AppsCustomizeCellLayout layout = (AppsCustomizeCellLayout) getPageAt(screen);
        child.setOnKeyListener(new IconKeyEventListener());

        ViewGroup.LayoutParams genericLp = child.getLayoutParams();
        CellLayout.LayoutParams lp;
        if (genericLp == null || !(genericLp instanceof CellLayout.LayoutParams)) {
            lp = new CellLayout.LayoutParams(x, y, spanX, spanY);
        } else {
            lp = (CellLayout.LayoutParams) genericLp;
            lp.cellX = x;
            lp.cellY = y;
            lp.cellHSpan = spanX;
            lp.cellVSpan = spanY;
        }

        int childId = lp.cellY * mCellCountX + lp.cellX;

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "addInScreen: child = " + child + ", childInfo = " + child.getTag()
                    + ", screen = " + screen + ", x = " + x + ", y = " + y + ", childId = "
                    + childId);
        }

        if (!layout.addViewToCellLayout(child, insert ? 0 : -1, childId, lp, true)) {
            // TODO: This branch occurs when the workspace is adding views
            // outside of the defined grid
            // maybe we should be deleting these items from the LauncherModel?
            Log.w(TAG, "Failed to add to item at (" + lp.cellX + "," + lp.cellY
                    + ") to AppsCustomizeCellLayout");
        }
    }

    /**
     * M: This is used to compute the visual center of the dragView. This point
     * is then used to visualize drop locations and determine where to drop an
     * item. The idea is that the visual center represents the user's
     * interpretation of where the item is, and hence is the appropriate point
     * to use when determining drop location, merge from Workspace.
     *
     * @param x
     * @param y
     * @param xOffset
     * @param yOffset
     * @param dragView
     * @param recycle
     * @return
     */
    private float[] getDragViewVisualCenter(int x, int y, int xOffset, int yOffset,
            DragView dragView, float[] recycle) {
        // NOTICE: Launcher3 add the scrollX to the x param in
        // DragController(Launcher2 don't), we need to minus the mScrollX when
        // calculating the visual center in Launcher3.
        float res[];
        if (recycle == null) {
            res = new float[2];
        } else {
            res = recycle;
        }

        // First off, the drag view has been shifted in a way that is not
        // represented in the x and y values or the x/yOffsets. Here we account
        // for that shift.
        x += getResources().getDimensionPixelSize(R.dimen.dragViewOffsetX);
        y += getResources().getDimensionPixelSize(R.dimen.dragViewOffsetY);

        // These represent the visual top and left of drag view if a dragRect
        // was provided.
        // If a dragRect was not provided, then they correspond to the actual
        // view left and top, as the dragRect is in that case taken to be the entire dragView.
        // R.dimen.dragViewOffsetY.
        int left = x - xOffset - mScrollX;
        int top = y - yOffset;

        // In order to find the visual center, we shift by half the dragRect
        res[0] = left + dragView.getDragRegion().width() / 2;
        res[1] = top + dragView.getDragRegion().height() / 2;

        return res;
    }

    /**
     * M:Convert the 2D coordinate xy from the parent View's coordinate space to
     * this CellLayout's coordinate space. The argument xy is modified with the
     * return result, merge from Workspace.
     *
     * @param v
     * @param xy
     * @param cachedInverseMatrix
     */
    private void mapPointFromSelfToChild(View v, float[] xy, Matrix cachedInverseMatrix) {
        if (cachedInverseMatrix == null) {
            v.getMatrix().invert(mTempInverseMatrix);
            cachedInverseMatrix = mTempInverseMatrix;
        }
        int scrollX = getScrollX();
        if (mNextPage != INVALID_PAGE) {
            scrollX = mScroller.getFinalX();
        }
        xy[0] = xy[0] + scrollX - v.getLeft();
        xy[1] = xy[1] + getScrollY() - v.getTop();
        cachedInverseMatrix.mapPoints(xy);
    }

    /**
     * M: Calculate the nearest cell where the given object would be dropped.
     * pixelX and pixelY should be in the coordinate system of layout.
     */
    private int[] findNearestArea(int pixelX, int pixelY, int spanX, int spanY,
            CellLayout layout, int[] recycle) {
        return layout.findNearestArea(pixelX, pixelY, spanX, spanY, recycle);
    }

    /**
     * M: Update item position after drop.
     *
     * @param layout
     * @param cell
     * @param screen
     */
    private void updateItemPositionAfterDrop(final CellLayout layout, final View cell,
            final int screen) {
        // update the item's position after drop.
        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell
                .getLayoutParams();
        lp.cellX = mTargetCell[0];
        lp.cellY = mTargetCell[1];
        lp.cellHSpan = 1;
        lp.cellVSpan = 1;

        final int childId = lp.cellY * mCellCountX + lp.cellX;
        cell.setId(childId);

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "updateItemPositionAfterDrop: layout = " + layout + ", cell = "
                    + cell + ", screen = " + screen + ", childId = " + childId);
        }

        // add the view in the screen.
        addInScreen(cell, screen, mTargetCell[0], mTargetCell[1], 1, 1, false);

        // modify app position.
        modifyAppPosition(cell, screen, lp.cellX, lp.cellY);

        // If the page is full, drop the icon from another page, the last cell
        // in the page will be placed the first position in the next page.
        if (mLastCell != null) {
            processLastCellInFullPage(screen);
        }

        // Update all apps position in the page after realTimeReorder.
        updateItemLocationsInDatabase(layout);

        // If the page is the last empty page, automatically add new empty page.
        if (screen == sAllAppsPage.size() - 1
                && sAllAppsPage.get(screen).getAppsAndFoldersCount() - 1 == 0) {
            addNewAppsPageLayout();
            invalidatePageData(screen);
        }
    }

    /**
     * M: Recorder the last cell in full page.
     *
     * @param layout
     * @param dragFromOtherScreen
     */
    private void saveLastCellInFullPage(final CellLayout layout,
            final boolean dragFromOtherScreen) {
        if (mLastCell != null) {
            LauncherLog.i(TAG, "saveLastCellInFullPage mLastCell = " + mLastCell);
            return;
        }

        final int page = indexToPage(indexOfChild(layout));
        final PageInfo pageInfo = sAllAppsPage.get(page);
        final int childCount = layout.getChildrenLayout().getChildCount();
        int index = childCount;

        // If the page is full, recorder the last cell info.
        if (pageInfo.isFull()) {
            index = childCount - 1;
            mLastCell = layout.getChildAt(mCellCountX - 1, mCellCountY - 1);
            final CellLayout.LayoutParams lp = (CellLayout.LayoutParams) mLastCell
                    .getLayoutParams();
            mLastCellInfo.cell = mLastCell;
            mLastCellInfo.cellX = lp.cellX;
            mLastCellInfo.cellY = lp.cellY;
            mLastCellInfo.mPos = mLastCellInfo.cellY * mCellCountX + mLastCellInfo.cellX;
            // If the page is full, the last cell will be invisible temporarily.
            mLastCell.setVisibility(INVISIBLE);

            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "saveLastCellInFullpage page = " + page + ", mLastCell = "
                        + mLastCell + ", mLastCellInfo = " + mLastCellInfo);
            }
        }

        // If drag icon from another page, the last cell in the page will be the
        // empty cell.
        if (dragFromOtherScreen && mEmptyCell[0] == -1
                           && mEmptyCell[1] == -1) {
            mEmptyCell[0] = index % mCellCountX;
            mEmptyCell[1] = index / mCellCountX;
            /// We need to reset mOccuredReorder to true against rollbackLastScreenItemsPosition.
            mOccuredReorder = true;
        }

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "saveLastCellInFullpage mEmptyCell[0] = " + mEmptyCell[0]
                    + ", mEmptyCell[1] = " + mEmptyCell[1]);
        }
    }

    /**
     * M: The last view in full page will be placed in the first position in the
     * next page.
     *
     * @param screen
     */
    private void processLastCellInFullPage(final int screen) {
        final int childCount = getChildCount();
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "processLastCellInFullPage: screen = " + screen + ",childCount = "
                    + childCount);
        }

        final int[] firstCell = new int[2];
        final int[] emptyCell = new int[2];
        AppsCustomizeCellLayout layout = null;

        for (int i = screen + 1; i < childCount; i++) {
            mPrevLastCell = mLastCell;
            firstCell[0] = 0;
            firstCell[1] = 0;

            layout = (AppsCustomizeCellLayout) getPageAt(i);
            layout.getLastPosition(emptyCell);

            final boolean isFull = sAllAppsPage.get(i).isFull();
            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "processLastCellInFullPage: i = " + i + ", isFull = " + isFull
                        + ",emptyCell = (" + emptyCell[0] + ", " + emptyCell[1]
                        + "), mPrevLastCell = " + mPrevLastCell);
            }
            if (isFull) {
                mLastCell = layout.getChildAt(mCellCountX - 1, mCellCountY - 1);
                final CellLayout.LayoutParams lp = (CellLayout.LayoutParams) mLastCell
                        .getLayoutParams();
                mLastCellInfo.cell = mLastCell;
                mLastCellInfo.cellX = lp.cellX;
                mLastCellInfo.cellY = lp.cellY;
                mLastCellInfo.mPos = mLastCellInfo.cellY * mCellCountX + mLastCellInfo.cellX;
                mLastCell.setVisibility(INVISIBLE);
                if (LauncherLog.DEBUG_EDIT) {
                    LauncherLog.d(TAG, "processLastCellInFullPage: mLastCell = " + mLastCell
                            + ", mLastCellInfo = " + mLastCellInfo);
                }
            }

            realTimeReorder(emptyCell, firstCell, layout);

            // Remove the last cell from the prev page.
            getParentAppsCustomizeCellLayoutForView(mPrevLastCell).removeChildView(mPrevLastCell);

            // Display the last cell.
            mPrevLastCell.setVisibility(VISIBLE);

            // Add the last cell in the cur page.
            addInScreen(mPrevLastCell, i, firstCell[0], firstCell[1], 1, 1, false);
            modifyAppPosition(mPrevLastCell, i, firstCell[0], firstCell[1]);
            updateItemLocationsInDatabase(layout);

            if (!isFull) {
                // Clear the last cell info.
                mPrevLastCell = null;
                mLastCell = null;
                break;
            }
        }

        // If no empty position, add an new empty page to place the last view.
        if (mPrevLastCell != null) {
            int newPage = addNewAppsPageLayout();
            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "processLastCellInFullPage: newPage = "
                        + newPage + ", mPrevLastCell = " + mPrevLastCell);
            }

            // Place the app in the new page.
            addAppToNewPage((AppInfo) mPrevLastCell.getTag(), newPage);
        }
    }

    /**
     * M: Modify app position.
     *
     * @param v: The app which position has changed.
     * @param screen: The screen that the app will be added to.
     * @param cellX: The cellX that the app will be placed.
     * @param cellY: The cellY that the app will be placed.
     */
    private void modifyAppPosition(final View v, final int screen, final int cellX,
                                              final int cellY) {
        ItemInfo info = (ItemInfo) v.getTag();
        info.cellX = cellX;
        info.cellY = cellY;
        long prevScreen = info.screenId;
        int prevPos = info.mPos;
        int pos = cellY * mCellCountX + cellX;

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "modifyAppPositioin v = " + v + ", info = " + info + ", screen = "
                    + screen + ", cellX = " + cellX + ", cellY = " + cellY + ", prevScreen = "
                    + prevScreen + ", prevPos = " + prevPos + ", pos = " + pos);
        }

        if (prevScreen != screen) {
            PageInfo prevPage = sAllAppsPage.get((int) prevScreen);
            if (info instanceof AppInfo) {
                prevPage.remove((AppInfo) info);
            } else if (info instanceof FolderInfo) {
                prevPage.remove((FolderInfo) info);
            }
        }

        // Add the app in the current page.
        PageInfo curPage = sAllAppsPage.get((int) screen);
        info.screenId = screen;
        info.mPos = pos;
        if (info instanceof AppInfo) {
            addOrRemoveApp((AppInfo) info, true);
        } else if (info instanceof FolderInfo) {
            addOrRemoveFolder((FolderInfo) info, true);
        }
    }

    /**
     * M: Update all items info in database.
     *
     * @param cl
     * @param useLayoutParams
     */
    void updateItemLocationsInDatabase(final CellLayout cl) {
        if (cl == null) {
            LauncherLog.e(TAG, "updateItemLocationsInDatabase cl == null!");
            return;
        }
        final int count = cl.getChildrenLayout().getChildCount();

        int screen = indexToPage(indexOfChild(cl));
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "updateItemLocationsInDatabase cl = " + cl + ", screen = " + screen);
        }

        ItemInfo info = null;
        View v = null;
        for (int i = 0; i < count; i++) {
            v = cl.getChildOnPageAt(i);
            info = (ItemInfo) v.getTag();
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) v.getLayoutParams();
            // Null check required as the AllApps button doesn't have an item info.
            if (info != null) {
                LauncherModel.moveAllAppsItemInDatabase(
                        mLauncher, info, AllApps.CONTAINER_ALLAPP, screen, lp.cellX, lp.cellY);
            }
        }
    }

    /**
     * M: Update all items info in database.
     *
     * @param page
     * @param pageInfo
     */
    void updateItemLocationsInDatabase(final int page, final PageInfo pageInfo) {
        final int count = pageInfo.getAppsAndFoldersCount();
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "updateItemLocationsInDatabase: page = " + page
                                + ",pageInfo = " + pageInfo);
        }

        ItemInfo info = null;
        final ArrayList<ItemInfo> allItmes = pageInfo.getAppsAndFolders();
        for (int i = 0; i < count; i++) {
            info = allItmes.get(i);
            // Null check required as the AllApps button doesn't have an item info.
            if (info != null) {
                LauncherModel.moveAllAppsItemInDatabase(
                        mLauncher, info, page, info.cellX, info.cellY);
            }
        }
        allItmes.clear();
    }

    /**
     * M: Update the app info in database.
     *
     * @param info: The app info which will be updated in database.
     */
    private void updateItemInDatabase(final ItemInfo info) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "updateItemLocationsInDatabase: info = " + info);
        }

        if (info != null) {
            LauncherModel.moveAllAppsItemInDatabase(
                    mLauncher, info, (int) info.screenId, info.cellX, info.cellY);
        }
    }

    /**
     * M: Add an item in database.
     *
     * @param info: The app info which will be added.
     */
    private void addItemInDatabase(final ItemInfo info) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "addItemInDatabase: info = " + info);
        }
        if (info != null) {
            LauncherModel.addAllAppsItemToDatabase(mLauncher, info, (int) info.screenId,
                    info.cellX, info.cellY, false);
        }
    }

    /**
     * M: Delete an item from database.
     *
     * @param info: The app info which will be removed.
     */
    private void deleteItemInDatabase(final ItemInfo info) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "deleteItemInDatabase: info = " + info);
        }
        if (info != null) {
            LauncherModel.deleteAllAppsItemFromDatabase(mLauncher, info);
        }
    }

    /**
     * M: The app is system app or not.
     *
     * @param info the app information
     * @return whether system app or not
     */
    public boolean isSystemApp(final AppInfo info) {
        final Intent intent = info.intent;
        final PackageManager pm = mLauncher.getPackageManager();
        final ResolveInfo resolveInfo = pm.resolveActivity(intent, 0);
        if (resolveInfo != null
                && (resolveInfo.activityInfo.applicationInfo.flags
                    & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
            return true;
        }
        return false;
    }

    /**
     * M: Rollback the last screen realtime reorder.
     *
     * @param page
     */
    private void rollbackLastScreenItemsPosition(final int page) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "rollbackLastScreenItemsPosition: page = " + page
                    + ", occuredReorder = " + mOccuredReorder);
        }
        // If the last screen doesn't realtime reorder, do not rollback.
        if (!mOccuredReorder) {
            return;
        }

        if (page != -1) {
            AppsCustomizeCellLayout layout = (AppsCustomizeCellLayout) getPageAt(page);
            PageInfo pageInfo = sAllAppsPage.get(page);
            mPrevEmptyCell[0] = mTargetCell[0];
            mPrevEmptyCell[1] = mTargetCell[1];
            int childCount = layout.getChildrenLayout().getChildCount();
            int index = childCount;

            if (pageInfo.isFull() && mLastCell != null) {
                index = childCount - 1;
                mPrevLastCell = mLastCell;
                mPrevLastCell.setVisibility(VISIBLE);
                mPrevLastCell = null;
                mLastCell = null;
            }
            mPrevTargetCell[0] = index % mCellCountX;
            mPrevTargetCell[1] = index / mCellCountX;

            realTimeReorder(mEmptyCell, mPrevTargetCell, layout);

            mPrevEmptyCell[0] = -1;
            mPrevEmptyCell[1] = -1;

            mPrevTargetCell[0] = -1;
            mPrevTargetCell[1] = -1;

            updateItemLocationsInDatabase(layout);

            mOccuredReorder = false;
        }
    }

    /**
     * M: When receive PACKAGE_CHANGED, PACKAGE_ADDED, PACKAGE_REMOVED, notify
     * HideAppsActivity, all apps data has changed.
     */
    private void notifyPageListChanged() {
        final Intent intent = new Intent(HideAppsActivity.ACTION_PACKAGE_CHANGED);
        mLauncher.sendBroadcast(intent);
    }

    /**
     * M: Init all apps for each page.
     */
    private void initAllAppsPage() {
        if (sAllAppsPage.size() > 0) {
            sAllAppsPage.clear();
        }

        //Get the number of apps page.
        if (mNumAppsPages <= 0) {
            mNumAppsPages = LauncherModel.sMaxAppsPageIndex + 1;
        }

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "initAllAppsPage mNumAppsPages = " + mNumAppsPages);
        }

        // Create new pages.
        for (int i = 0; i < mNumAppsPages; i++) {
            final PageInfo pageInfo = new PageInfo();
            sAllAppsPage.add(pageInfo);
        }

        /// M: Init all apps in all apps pages.
        for (int i = 0; i < mApps.size(); i++) {
            AppInfo info = mApps.get(i);
            long screen = info.screenId;
            if (info.screenId != -1
                && (screen >= 0 && screen < sAllAppsPage.size())
                && info.isVisible) {
                PageInfo pageInfo = sAllAppsPage.get((int) screen);
                pageInfo.add(info);
            }
        }

        /// M: Init all folders in all apps pages for OP09
        if (mSupportEditAndHideApps) {
            for (int i = 0; i < sFolders.size(); i++) {
                FolderInfo info = sFolders.get(i);
                long screen = info.screenId;
                if (info.screenId != -1) {
                    PageInfo pageInfo = sAllAppsPage.get((int) screen);
                    pageInfo.add(info);
                }
            }
        }

        HashSet<Integer> hidePages = new HashSet<Integer>();

        final int pageCount = sAllAppsPage.size();
        for (int i = 0; i < pageCount; i++) {
            final PageInfo pageInfo = sAllAppsPage.get(i);
            if (pageInfo.isEmpty()) {
                hidePages.add(i);
            }
        }

        //Some pages will be removed, update other pages location in the all apps page.
        if (hidePages.size() > 0) {
            updatePagesWhenNeedToRemove(hidePages);
        }

        //Initialize the unreadnum for folder in app list
        updateFoldersUnread();

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "initAllAppsPage end: mNumAppsPages = " + mNumAppsPages
                    + ",sAllAppsPage size = " + sAllAppsPage.size() + ",hidePages = " + hidePages);
        }
    }


    /**
    *M: add for OP09 unread feature.
    */
    private void updateFoldersUnread() {
        ShortcutInfo sci = null;
        FolderInfo fi = null;
        ComponentName componentName = null;
        ArrayList<ShortcutInfo> content = null;
        int unreadNum = 0;
        int unreadNumTotal = 0;

        for (int i = 0; i < sFolders.size(); i++) {
            fi = sFolders.get(i);
            content = fi.contents;
            unreadNumTotal = 0;
            for (int j = 0; j < content.size(); j++) {
                sci = content.get(j);
                componentName = sci.intent.getComponent();
                unreadNum = MTKUnreadLoader.getUnreadNumberOfComponent(componentName);
                if (unreadNum > 0) {
                    sci.unreadNum = unreadNum;
                }
                LauncherLog.d(TAG, "updateFolderUnreadNum end: unreadNum = " + unreadNum
                    + ", info.title = " + sci.title);
                unreadNumTotal += unreadNum;
            }
            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "updateFolderUnreadNum end: unreadNumTotal = " + unreadNumTotal);
            }
            if (unreadNum <= 0) {
                fi.unreadNum = 0;
            } else {
                fi.unreadNum = unreadNum;
            }
        }
    }

    /**
     * M: Find first empty position to place the app.
     *
     * @param info the application info.
     * @return The page index with first empty cell, -1 if no empty cell exists.
     */
    private int findFirstEmptyPosition(final AppInfo info) {
        final int pageCount = sAllAppsPage.size();
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "findFirstEmptyPosition: info = " + info + ",pageCount = "
                    + pageCount);
        }

        /// M: Add for CT customization, make new installed app icon from page 3.
        final int startPage = mSupportEditAndHideApps ?
                                    Math.min(INSTALL_ICON_START_PAGE, pageCount - 1) : 0;
        for (int i = startPage; i < pageCount; i++) {
            final PageInfo pageInfo = sAllAppsPage.get(i);
            // If the page does not full, it means the page has empty cell.
            if (!pageInfo.isFull()) {
                // Decide where the app icon should be present here.
                fillPageInfoIntoAppInfo(info, pageInfo, i);
                if (LauncherLog.DEBUG_EDIT) {
                    LauncherLog.d(TAG, "Find empty cell for " + info);
                }
                return i;
            }
        }

        return -1;
    }

    /**
     * M: Find first empty position to place the app.
     *
     * @param info the application info.
     * @return The page index with first empty cell, -1 if no empty cell exists.
     */
    private int findFirstEmptyPositionFromLastPage(final AppInfo info) {
        final int pageCount = sAllAppsPage.size();
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "findFirstEmptyPosition: info = " + info + ",pageCount = "
                    + pageCount);
        }

        final PageInfo pageInfo = sAllAppsPage.get(pageCount - 1);
        // If the page does not full, it means the page has empty cell.
        if (!pageInfo.isFull()) {
            fillPageInfoIntoAppInfo(info, pageInfo, pageCount - 1);
            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "Find empty cell for " + info);
            }
            return (pageCount - 1);
        }
        return -1;
    }

    /**
     * M: Fill page info into app info.
     */
    private void fillPageInfoIntoAppInfo(final AppInfo appInfo, final PageInfo pageInfo,
                                                    int screen) {
        // Calculate the position the apps will be placed.
        appInfo.mPos = pageInfo.mAllApps.size();
        // Update the app info, cellX, cellY, screen, state.
        appInfo.cellX = appInfo.mPos % mCellCountX;
        appInfo.cellY = appInfo.mPos / mCellCountX;
        appInfo.screenId = screen;
    }

    /**
     * M: According to the page info, sync all apps icon in the page.
     *
     * @param pageInfo the information of page
     * @param page the number of page
     * @param immediate whether to sync page or not
     */
    public void syncAppsPageItems(final PageInfo pageInfo, final int page,
                          final boolean immediate) {
        ArrayList<AppInfo> allApps = pageInfo.mAllApps;
        ArrayList<FolderInfo> allFolders = pageInfo.mAllFolders;
        LauncherLog.d(TAG, "syncAppsPageItems: start, page = " + page);
        AppsCustomizeCellLayout layout = (AppsCustomizeCellLayout) getPageAt(page);

        layout.removeAllViewsOnPage();
        ArrayList<Object> items = new ArrayList<Object>();
        ArrayList<Bitmap> images = new ArrayList<Bitmap>();

        for (int i = 0; i < allApps.size(); ++i) {
            AppInfo info = allApps.get(i);
            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "syncAppsPageItems: i = " + i
                        + ", appinfo = " + info + ", page = "
                        + page + ", systemapp = " + isSystemApp(info));
            }
            if (info != null && info.isVisible) {
                final BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(
                        R.layout.apps_customize_application, layout, false);
                if (Launcher.DISABLE_APPLIST_WHITE_BG) {
                    icon.setTextColor(getContext().getResources().getColor(
                            R.color.quantum_panel_transparent_bg_text_color));
                }
                icon.applyFromApplicationInfo(info);
                icon.setOnClickListener(this);
                icon.setOnLongClickListener(this);
                icon.setOnTouchListener(this);
                icon.setOnKeyListener(this);
                // If in the edit mode, and the app is not system app,
                // display the "Delete" button in the left-top corner of the app
                // icon.
                if (Launcher.isInEditMode() && !isSystemApp(info)) {
                    icon.setDeleteButtonVisibility(true);
                } else {
                    icon.setDeleteButtonVisibility(false);
                }

                info.mPos = info.cellY * AllApps.sAppsCellCountX + info.cellX;
                int x = info.mPos % mCellCountX;
                int y = info.mPos / mCellCountX;
                layout.addViewToCellLayout(icon, -1, info.mPos,
                        new CellLayout.LayoutParams(x, y, 1, 1), false);

                LauncherLog.d(TAG, "syncAppsPageItems, x=" + x + ", y=" + y
                                           + ", info.mPos=" + info.mPos);
                if (LauncherLog.DEBUG_EDIT) {
                    LauncherLog.d(TAG, "syncAppsPageItems info = " + info + ", page = " + page);
                }

                items.add(info);
                images.add(info.iconBitmap);
            }
        }

        for (int i = 0; i < allFolders.size(); ++i) {
            final FolderInfo info = allFolders.get(i);
            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "syncAppsPageItems: i = " + i + ", folderinfo = " + info
                        + ", page = " + page);
            }
            if (info != null) {
                // unbind listeners
                info.unbind();

                final FolderIcon icon = FolderIcon.fromXml(R.layout.folder_icon, mLauncher, layout,
                      info, mIconCache, true);
                icon.setOnClickListener(mLauncher);
                icon.setOnLongClickListener(this);
                icon.setOnTouchListener(this);
                icon.setOnKeyListener(this);

                info.mPos = info.cellY * AllApps.sAppsCellCountX + info.cellX;
                int x = info.mPos % mCellCountX;
                int y = info.mPos / mCellCountX;
                layout.addViewToCellLayout(icon, -1, info.mPos,
                        new CellLayout.LayoutParams(x, y, 1, 1), false);
            }
        }
        LauncherLog.d(TAG, "syncAppsPageItems: end, mIsAppRemoved=" + mIsAppRemoved);
        enableHwLayersOnVisiblePages();

        if (mIsAppRemoved) {
            LauncherLog.d(TAG, "syncAppsPageItems: appsize = " + mAppsArray.size());
            for (int i = 0; i < mAppsArray.size(); i ++) {
                if (mAppsArray.get(i).mRmAppsInfo != null) {
                    removeItemsInFolderByApplicationInfo(mAppsArray.get(i).mRmAppsInfo,
                        mAppsArray.get(i).mUser);
                } else if (mAppsArray.get(i).mRmPackagesInfo != null) {
                    removeItemsInFolderByPackageName(mAppsArray.get(i).mRmPackagesInfo,
                        mAppsArray.get(i).mUser);
                }
            }
            mIsAppRemoved = false;
        }
    }

    /**
     * M: Add a new empty page layout.
     *
     * @return
     */
    private int addNewAppsPageLayout() {
        Context context = getContext();
        AppsCustomizeCellLayout layout = new AppsCustomizeCellLayout(context);
        setupPage(layout);
        addView(layout);

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "addNewAppsPageLayout: mNumAppsPages = " + mNumAppsPages);
        }
        mDirtyPageContent.add(true);
        return addNewEmptyAppsPage();
    }

    /**
     * M: Add new page info in all apps tab.
     *
     * @return
     */
    private int addNewEmptyAppsPage() {
        PageInfo newPage = new PageInfo();
        newPage.mAllApps = new ArrayList<AppInfo>();
        sAllAppsPage.add(newPage);
        mNumAppsPages++;
        LauncherLog.d(TAG, "addNewEmptyAppsPage mNumAppsPages = " + mNumAppsPages, new Throwable());
        return sAllAppsPage.size() - 1;
    }

    /**
     * M: Remove all empty pages in apps customize pane, empty page means there
     * is no item in the page.
     */
    private void removeEmptyPages() {
        // Used to recorder all pages which will be removed.
        HashSet<Integer> pageNeedToRemove = new HashSet<Integer>();
        final int count = sAllAppsPage.size();
        for (int i = 0; i < count; i++) {
            final PageInfo pageInfo = sAllAppsPage.get(i);
            if (pageInfo.isEmpty()) {
                pageNeedToRemove.add(i);
            }
        }

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "removeEmptyPages: count = " + count + ", pageNeedToRemove = "
                    + pageNeedToRemove);
        }

        // If some pages will be removed, adjust other pages position in all
        // apps pages.
        if (pageNeedToRemove.size() > 0) {
            updatePagesWhenNeedToRemove(pageNeedToRemove);
        }

        // If the current page becomes widget page because we hide some pages,
        // set the current page to the last apps page instead.
        if (mCurrentPage >= mNumAppsPages) {
            setCurrentPage(mNumAppsPages - 1);
        }
    }

    /**
     * M: Add an app to new page.
     *
     * @param appInfo: The app will be added.
     * @param page: The page that the app will be added to.
     */
    private void addAppToNewPage(final AppInfo appInfo, final int page) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "addAppToNewPage: appInfo = " + appInfo + ", page = " + page);
        }
        ArrayList<AppInfo> newPage = sAllAppsPage.get(page).mAllApps;
        appInfo.screenId = page;
        appInfo.mPos = newPage.size();
        appInfo.cellX = appInfo.mPos % mCellCountX;
        appInfo.cellY = appInfo.mPos / mCellCountX;
        newPage.add(appInfo);
        addAppToList(appInfo, mApps);
    }

    /**
     * M: Remove uninstall, disabled apps.
     *
     * @param list: The apps list which will be removed.
     */
    private void removeDisabledApps(ArrayList<AppInfo> list) {
        hideOrRemoveApps(list, true);
        removeEmptyPages();
    }

    /**
     * M: Find app in apps, return the position in the list.
     *
     * @param apps The array list used to found the app.
     * @param appInfo The specified app info which will be found.
     * @return the app position in the list.
     */
    private static final int findApp(final ArrayList<AppInfo> apps, final AppInfo appInfo) {
        final ComponentName componentName = appInfo.componentName;
        final int appsCount = apps.size();
        for (int i = 0; i < appsCount; i++) {
            final AppInfo info = apps.get(i);
            if (info.componentName.equals(componentName)) {
                return i;
            }
        }
        return -1;
    }

    private static final void addAppToList(final AppInfo appInfo,
                             final ArrayList<AppInfo> apps) {
        final int pos = findApp(apps, appInfo);
        if (pos == -1) {
            apps.add(appInfo);
        } else {
            apps.set(pos, appInfo);
        }
    }

    private static final void removeAppFromList(final AppInfo appInfo,
                            final ArrayList<AppInfo> apps) {
        final int pos = findApp(apps, appInfo);
        if (pos >= 0) {
            apps.remove(pos);
        }
    }

    /**
     * M: When receive PACKAGE_ADDED.
     *
     * @param list: The list of apps added.
     */
    private void addAddedApps(final ArrayList<AppInfo> list) {
        final int length = list.size();
        for (int i = 0; i < length; i++) {
            AppInfo info = list.get(i);

            final int appIndex = findApp(mApps, info);
            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "addAddedApps: info = " + info + ",appIndex = "
                        + appIndex + ",componentName = "
                        + info.componentName);
            }

            // Avoid adding the same application to all apps list repeatly.
            if (appIndex >= 0) {
                AppInfo origApp = mApps.get(appIndex);
                if (origApp.screenId >= 0) {
                    LauncherLog.i(TAG, "App " + origApp + " already exists in all apps list!");
                    return;
                } else {
                    LauncherLog.i(TAG, "App " + origApp + " is in the pending added list!");
                    mApps.remove(origApp);
                }
            }

            info.isVisible = true;
            mApps.add(mApps.size(), info);

            addApp(info);
            addItemInDatabase(info);
        }
    }

    /**
     * M: Show apps which state changed from hide to show.
     *
     * @param showAppsList The apps list which are to be shown.
     */
    private void showApps(final ArrayList<AppInfo> showAppsList) {
        final int appsCount = showAppsList.size();
        for (int i = 0; i < appsCount; i++) {
            final AppInfo appInfo = showAppsList.get(i);
            showApp(appInfo);
            // If the app is in HideAndDiabled arraylist, add an item in
            // database
            updateItemInDatabase(appInfo);
        }
    }

    /**
     * M: Show an app.
     *
     * @param info: The app info which will be shown.
     */
    private void showApp(final AppInfo info) {
        final long prevScreen = info.screenId;

        // Find first empty position to place the app.
        int page = findFirstEmptyPosition(info);
        // If do not find an empty position, add new empty page.
        if (page == -1) {
            int newPage = addNewAppsPageLayout();
            // Place the app in the new page.
            addAppToNewPage(info, newPage);
        } else {
            addAppToPage(info, page);
        }
    }

    /**
     * M: Add an app.
     *
     * @param info: The app info which will be added.
     */
    private void addApp(final AppInfo info) {
        // Find first empty position to place the app.
        int page = -1;
        final ComponentName component = info.intent.getComponent();
        if (component.getPackageName().equals(STK_PACKAGE_NAME)
                && (component.getClassName().equals(STK_CLASS_NAME)
                || component.equals(STK2_CLASS_NAME))) {
            page = findFirstEmptyPositionFromLastPage(info);
        } else {
            page = findFirstEmptyPosition(info);
        }

        // If do not find an empty position, add new empty page.
        if (page == -1) {
            int newPage = addNewAppsPageLayout();
            // Place the app in the new page.
            addAppToNewPage(info, newPage);
        } else {
            addAppToPage(info, page);
        }
    }

    /**
     * M: Add an app.
     *
     * @param info: The app which will be added.
     * @param page: The page which the app will be added to.
     */
    private void addAppToPage(AppInfo info, long page) {
        sAllAppsPage.get((int) page).add(info);
        addAppToList(info, mApps);
    }

    protected void addAppToList(AppInfo info) {
        addAppToList(info, mApps);
    }

    /**
     * M: Back from HideAppsActivity, process the state change of apps.
     *
     */
    public void processAppsStateChanged() {
        final int count = sShowAndHideApps.size();
        if (count == 0) {
            LauncherLog.i(TAG, "processAppsStateChanged with no state changed apps.");
            return;
        }

        isInHideOrRemoveAppMode = true;

        // Used to recorder all apps which will be hidden.
        ArrayList<AppInfo> hideApps = new ArrayList<AppInfo>();
        // Used to recorder app apps which will be shown.
        ArrayList<AppInfo> showApps = new ArrayList<AppInfo>();

        for (int i = 0; i < count; i++) {
            AppInfo appInfo = sShowAndHideApps.get(i);
            if (appInfo.isVisible) {
                showApps.add(appInfo);
            } else {
                hideApps.add(appInfo);
            }
        }

        // Hide apps.
        if (hideApps.size() > 0) {
            hideOrRemoveApps(hideApps, false);
        }

        // Show apps.
        if (showApps.size() > 0) {
            showApps(showApps);
        }

        sShowAndHideApps.clear();

        // Remove all empty pages.
        removeEmptyPages();

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "processAppsStateChanged end: mNumAppsPages = "
                    + mNumAppsPages + ",mCurrentPage = " + mCurrentPage + ",showApps = " + showApps
                    + ",hideApps = " + hideApps);
        }

        invalidatePageData(mCurrentPage);

        // If the apps are hidden, the corresponding shortcuts in the homescreen
        // will be removed.
        if (hideApps.size() > 0) {
            mLauncher.getWorkspace().removeItemsByAppInfo(hideApps);
        }

        isInHideOrRemoveAppMode = false;
    }

    /**
     * M: Process pending add apps.
     *
     * @param list the add app pending list
     */
    public void processPendingAddApps(ArrayList<AppInfo> list) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "processPendingAddApps: list = " + list + ", this = " + this);
        }

        if (!mSupportEditAndHideApps) {
            addAppsWithoutInvalidate(list);
            reorderApps();
        } else {
            addAddedApps(list);
            notifyPageListChanged();
        }
    }

    /**
     * M: Process pending updated apps.
     *
     * @param list the update app pending list
     */
    public void processPendingUpdateApps(ArrayList<AppInfo> list) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "processPendingUpdateApps: list = " + list + ", this = " + this);
        }

        if (!mSupportEditAndHideApps) {
            removeAppsWithoutInvalidate(list);
            addAppsWithoutInvalidate(list);
            reorderApps();
        }
    }

    /**
     * M: Process pending removed apps.
     *
     * @param packageNames the package name which the app want to be removed
     */
    public void processPendingRemoveApps(ArrayList<String> packageNames) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "processPendingRemoveApps: packageNames = "
                        + packageNames + ", this = " + this);
        }

        removeAppsWithPackageNameWithoutInvalidate(packageNames);

        if (!mSupportEditAndHideApps) {
            reorderApps();
        } else {
            removeDisabledApps(sRemovedApps);
            notifyPageListChanged();
            sRemovedApps.clear();
        }
    }

    /**
     * M: Update page count and invalidate data after handle pending add/update/remove apps.
     */
    public void processPendingPost() {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "processPendingPost: this = " + this);
        }

        updatePageCountsAndInvalidateData();
    }

    /**
     * M: Hide or remove some apps.
     *
     * @param apps: The apps will be removed or hidden.
     * @param isRemoved: True removed, false hidden.
     */
    private void hideOrRemoveApps(ArrayList<AppInfo> apps, boolean isRemoved) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "hideOrRemoveApps: apps = " + apps + ",isRemoved = " + isRemoved);
        }

        // Used to recorder all pages which apps state changed.
        SortedSet<Integer> pageAppsStateChanged = new TreeSet<Integer>();
        final int hideAppsCount = apps.size();

        for (int i = 0; i < hideAppsCount; i++) {
            final AppInfo appInfo = apps.get(i);
            // The root cause is STK enable/disable components, that makes the
            // appInfo is not added to a real page before it removed, so the
            // screenId is invalid and JE happens. We need check the screenId.
            if (appInfo.screenId == -1) {
                LauncherLog.i(TAG, "hideOrRemoveApps: appInfo.screenId == -1 -> appInfo is "
                        + appInfo);
                continue;
            }

            long page = appInfo.screenId;
            if (appInfo.container != AllApps.CONTAINER_ALLAPP) {
                int pageIndex = 0;
                for (PageInfo pageInfo : sAllAppsPage) {
                    FolderInfo folderInfo = pageInfo.removeFromFolder(appInfo);
                    if (folderInfo != null) {
                        page = pageIndex;
                        break;
                    }
                    pageIndex++;
                }
                appInfo.container = AllApps.CONTAINER_ALLAPP;
                appInfo.screenId = page;
                addAppToList(appInfo, mApps);
            } else {
                ///M. ALPS02057272. check the page.
                if (page >= sAllAppsPage.size()) {
                    LauncherLog.d(TAG, "hideOrRemoveApps: mApps = " + mApps
                        + ",sAllAppsPage = " + sAllAppsPage
                        + ", page: " + page);
                    continue;
                }
                ///M.
                PageInfo pageInfo = sAllAppsPage.get((int) page);
                pageInfo.remove(appInfo);
            }

            pageAppsStateChanged.add((int) page);

            if (isRemoved) {
                deleteItemInDatabase(appInfo);
            } else {
                updateItemInDatabase(appInfo);
            }
        }

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "hideOrRemoveApps middle: pageAppsStateChanged = "
                + pageAppsStateChanged);
        }

        for (int page : pageAppsStateChanged) {
            final PageInfo pageInfo = sAllAppsPage.get(page);
            final int appsCount = pageInfo.getAppsAndFoldersCount();
            final ArrayList<ItemInfo> allItmes = pageInfo.getAppsAndFolders();
            ItemInfo info = null;
            for (int j = 0; j < appsCount; j++) {
                info = allItmes.get(j);
                info.mPos = j;
                info.cellX = j % mCellCountX;
                info.cellY = j / mCellCountX;
            }
            allItmes.clear();
            if (appsCount > 0) {
                updateItemLocationsInDatabase(page, pageInfo);
            }
        }
    }

    static final boolean isHideApps(AppInfo app) {
        final int pos = findApp(sShowAndHideApps, app);
        return pos >= 0 && !sShowAndHideApps.get(pos).isVisible;
    }

    /**
     * M: Find which apps will be hidden, which apps will be shown.
     *
     * @param allApps
     * @param hideApps
     * @param showApps
     * @param page
     */
    private void findHideAndShowApps(final ArrayList<AppInfo> allApps,
            final ArrayList<AppInfo> hideApps, final ArrayList<AppInfo> showApps,
            int page) {
        final int count = allApps.size();
        for (int i = 0; i < count; i++) {
            AppInfo info = allApps.get(i);
            if (page == info.screenId && info.stateChanged) {
                if (info.isVisible) {
                    info.isVisible = false;
                    hideApps.add(info);
                    // Update the other apps position if some apps will be hidden.
                    moveAppsPositionAfterHideApps(info);
                } else {
                    info.isVisible = true;
                    showApps.add(info);
                }
            }
        }
    }

    /**
     * M: Find which apps will be shown, which apps will be hidden.
     *
     * @param pageInfo
     * @param hideApps
     * @param showApps
     */
    private void findHideAndShowApps(final PageInfo pageInfo,
            final ArrayList<AppInfo> hideApps, final ArrayList<AppInfo> showApps) {
        final ArrayList<AppInfo> allApps = pageInfo.mAllApps;
        final int appSize = allApps.size();
        for (int i = 0; i < appSize; i++) {
            final AppInfo info = allApps.get(i);
            if (info.stateChanged) {
                // If state has changed and the current application icon is
                // visible, then change it to invisible.
                if (info.isVisible) {
                    info.isVisible = false;
                    hideApps.add(info);
                    // Update the other apps position if some apps will be hidden.
                    moveAppsPositionAfterHideApps(info);
                } else {
                    info.isVisible = true;
                    showApps.add(info);
                }
            }
        }
    }

    /**
     * M: All apps behind the hidden apps will move forward one position.
     *
     * @param hideAppInfo
     */
    private void moveAppsPositionAfterHideApps(final AppInfo hideAppInfo) {
        final long page = hideAppInfo.screenId;
        final int pos = hideAppInfo.mPos;

        final PageInfo pageInfo = sAllAppsPage.get((int) page);
        final ArrayList<AppInfo> allApps = pageInfo.mAllApps;
        final int childCount = allApps.size();

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "moveAppsPositionAfterHideApps: page = " + page + ",pos = " + pos
                    + ",hideAppInfo = " + hideAppInfo + ",childCount = " + childCount);
        }

        for (int i = 0; i < childCount; i++) {
            final AppInfo appInfo = allApps.get(i);
            if (appInfo.mPos > pos) {
                appInfo.mPos--;
                appInfo.cellX = appInfo.mPos % mCellCountX;
                appInfo.cellY = appInfo.mPos / mCellCountX;
                // Update app info in database which position has changed.
                updateItemInDatabase(appInfo);
            }
        }
        // Update the hideAppInfo in database.
        updateItemInDatabase(hideAppInfo);
    }

    /**
     * M: If all apps in the page are be removed or hidden, the page will be
     * removed.
     *
     * @param pages
     */
    private void updatePagesWhenNeedToRemove(final HashSet<Integer> pages) {
        removeHidePage(pages);
        updateAllAppsPage();
    }

    /**
     * M: Remove apps with package name but not invalidate.
     */
    private void removeAppsWithPackageNameWithoutInvalidate(ArrayList<String> packageNames) {
        // loop through all the package names and remove apps that have the same package name
        for (String pn : packageNames) {
            int removeIndex = findAppByPackage(mApps, pn);
            while (removeIndex > -1) {
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "removeAppsWithPName: removeIndex = " + removeIndex
                            + ", pn = " + pn + ", this = " + this);
                }
                /// M: store the remove apps in list for op09.
                sRemovedApps.add(mApps.remove(removeIndex));
                removeIndex = findAppByPackage(mApps, pn);
            }
        }
    }

    /**
     * M: Get all hidden pages and remove them in all apps list.
     */
    private void removeHidePages() {
        // Used to recorder all pages which will be removed.
        HashSet<Integer> pageNeedToRemove = new HashSet<Integer>();
        final int count = sAllAppsPage.size();
        for (int i = 0; i < count; i++) {
            PageInfo pageInfo = sAllAppsPage.get(i);
            if (pageInfo.isEmpty()) {
                pageNeedToRemove.add(i);
            }
        }

        // If some pages will be removed, adjust other pages position in all
        // apps pages.
        if (pageNeedToRemove.size() > 0) {
            updatePagesWhenNeedToRemove(pageNeedToRemove);
        }

        // If the current page becomes widget page because we hide some pages,
        // set the current page to the last apps page instead.
        if (mCurrentPage >= mNumAppsPages) {
            setCurrentPage(mNumAppsPages - 1);
        }
    }

    /**
     * M: Remove all hidden pages from all apps pages.
     */
    private void removeHidePage(final HashSet<Integer> hidePages) {
        final int hidePageSize = hidePages.size();
        final int allAppsPageSize = sAllAppsPage.size();
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "removeHidePage: hidePages = " + hidePageSize + ",allAppsPageSize = "
                    + allAppsPageSize + ",mNumAppsPages = " + mNumAppsPages + ",sAllAppsPage = "
                    + sAllAppsPage);
        }

        for (int i = allAppsPageSize - 1; i >= 0; i--) {
            final PageInfo pageInfo = sAllAppsPage.get(i);
            if (pageInfo.isEmpty() || hidePages.contains(pageInfo.getPage())) {
                sAllAppsPage.remove(i);
                // Descrease the number of apps pages.
                mNumAppsPages--;
            }
        }
        LauncherLog.d(TAG, "removeHidePage mNumAppsPages = " + mNumAppsPages, new Throwable());
    }

    /**
     * M: Update other pages position in all apps apges after remove the hidden
     * pages.
     */
    private void updateAllAppsPage() {
        final int pageSize = sAllAppsPage.size();
        // If all pages are removed, add an new empty page.
        if (pageSize == 0) {
            addNewAppsPageLayout();
        }

        // Update the screen of application info since some pages may be removed.
        for (int i = 0; i < pageSize; i++) {
            PageInfo pageInfo = sAllAppsPage.get(i);
            ArrayList<AppInfo> allApps = pageInfo.mAllApps;
            final int appCount = allApps.size();
            for (int j = 0; j < appCount; j++) {
                AppInfo info = allApps.get(j);
                info.screenId = i;
                updateItemInDatabase(info);
            }
        }
    }

    /**
     * M: invalidate app page items.
     */
    void invalidateAppPages(int currentPage, boolean immediateAndOnly) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "invalidateAppPages: currentPage = " + currentPage
                    + ", immediateAndOnly = " + immediateAndOnly);
        }
        invalidatePageData(currentPage, immediateAndOnly);
    }

    @Override
    protected float getScrollProgress(int screenCenter, View v, int page) {
        if (isSupportCycleSlidingScreen()) {
            final int halfScreenSize = getMeasuredWidth() / 2;
            final int childCount = getChildCount();
            final int firstCenter = halfScreenSize;
            final int lastCenter = getChildOffset(childCount - 1) + halfScreenSize;
            final int totalWidth = lastCenter + halfScreenSize;
            if (LauncherLog.DEBUG_DRAW) {
                LauncherLog.d(TAG, "getScrollProgress: mForceScreenScrolled = "
                        + mForceScreenScrolled + ", firstCenter = " + firstCenter
                        + ", lastCenter = " + lastCenter + ", totalWidth = " + totalWidth
                        + ", screenCenter = " + screenCenter + ", getScrollX() = " + getScrollX());
            }
            // Reset the screen center to make it in (-halfScreenSize, halfScreenSize)
            //when the page is 0,
            // and in ((totalWidth - halfScreenSize), (totalWidth + halfScreenSize))
            // when the page is the last.
            if (page == 0) {
                /// M: If only one page do nothing or first entering app
                /// list(not dragging), not adjust screen center.
                if (getScrollX() != 0 && screenCenter >= lastCenter && getChildCount() > 1) {
                    screenCenter -= totalWidth;
                }
            } else if (page == childCount - 1) {
                if (screenCenter <= firstCenter) {
                    screenCenter += totalWidth;
                }
            }
        }

        return super.getScrollProgress(screenCenter, v, page);
    }


    /**
     * M: The class used to describe the information of each page in the all
     * apps list.
     */
    class PageInfo {
        /**
         * The arraylist used to recorder all apps info in the page.
         */
        ArrayList<AppInfo> mAllApps = new ArrayList<AppInfo>();

        //M:[OP09][CF] add for folder type @{
        ArrayList<FolderInfo> mAllFolders = new ArrayList<FolderInfo>();
        //M:[OP09][CF]}@
        PageInfo() {
        }

        /**
         * M: get an app info.
         */
        public AppInfo get(final int pos) {
            return mAllApps.get(pos);
        }

        /**
         * M: add an app info.
         *
         * @param appInfo: The app info will be added.
         */
        public void add(final AppInfo appInfo) {
            if (mSupportEditAndHideApps) {
                final int pos = find(appInfo);
                LauncherLog.d("stone2", "add, pos = " + pos + ",info=" + appInfo);
                if (pos == -1) {
                    mAllApps.add(appInfo);
                } else {
                    mAllApps.set(pos, appInfo);
                }
            } else {
                mAllApps.add(appInfo);
            }
        }

        /**
         * M: remove an app from the specified pos.
         *
         * @param pos: The app in the pos will be removed.
         */
        public void remove(final int pos) {
            mAllApps.remove(pos);
        }

        /**
         * M: remove an app.
         *
         * @param appInfo
         */
        public void remove(final AppInfo appInfo) {
            final int pos = find(appInfo);
            if (pos != -1) {
                mAllApps.remove(pos);
            }
        }

        /**
         * M: remove an app from Folders.
         *
         * @param appInfo
         */
        public FolderInfo removeFromFolder(final AppInfo appInfo) {
            FolderInfo folderInfo = findFolder((int) appInfo.container);
            if (folderInfo != null) {
                removeFromFolder(appInfo, folderInfo);
            }
            return folderInfo;
        }

        private boolean removeFromFolder(final AppInfo appInfo, final FolderInfo folderInfo) {
            final ComponentName componentName = appInfo.componentName;
            // Find item
            ShortcutInfo info = null;
            for (ShortcutInfo item : folderInfo.contents) {
                if (componentName.equals(item.mComponentName)) {
                    info = item;
                    break;
                }
            }
            // remove item
            if (info != null) {
                if (LauncherLog.DEBUG_EDIT) {
                    LauncherLog.d(TAG, "removeFromFolder start, appInfo = " + appInfo
                            + ", folderInfo=" + folderInfo);
                }
                folderInfo.remove(info);
                if (LauncherLog.DEBUG_EDIT) {
                    LauncherLog.d(TAG, "removeFromFolder end");
                }
                return true;
            } else {
                return false;
            }
        }

        /**
         * M: find the specified app info.
         *
         * @param info: The app info will be found.
         * @return: If find, return the app info, if not found, return null.
         */
        public int find(final AppInfo info) {
            return findApp(mAllApps, info);
        }

        /**
         * M: if find, modify the value of the app info, if not found, add the app
         * info.
         *
         * @param info: The app info which value will be modified.
         */
        public void modify(final AppInfo info) {
            final int pos = find(info);
            if (pos != -1) {
                AppInfo appInfo = mAllApps.get(pos);
                appInfo = info;
            } else {
                mAllApps.add(info);
            }
        }

        /**
         * M: whether the page is full or not.
         *
         * @return True if the page is full, else false.
         */
        public boolean isFull() {
            if (mSupportEditAndHideApps) {
                return (mAllApps.size() + mAllFolders.size()) >= mCellCountX * mCellCountY;
            } else {
                return mAllApps.size() >= mCellCountX * mCellCountY;
            }
        }

        /**
         * M: whether the page is empty or not.
         *
         * @return True if the page is empty, else false.
         */
        public boolean isEmpty() {
            if (mSupportEditAndHideApps) {
                return (mAllApps.size() + mAllFolders.size()) == 0;
            } else {
                return mAllApps.size() == 0;
            }
        }

        /**
         * M: the count of all apps in the page.
         *
         * @return
         */
        public int getAppsAndFoldersCount() {
            return mAllApps.size() + mAllFolders.size();
        }

        /**
         * M: Get all apps and folders in the page.
         * @return
         */
        public ArrayList<ItemInfo> getAppsAndFolders() {
            final ArrayList<ItemInfo> allItmes = new ArrayList<ItemInfo>();
            allItmes.addAll(mAllApps);
            allItmes.addAll(mAllFolders);
            Collections.sort(allItmes, mPageItemPositionComparator);
            return allItmes;
        }

        /**
         * M: Get Page No.
         * @return Page No.
         */
        public int getPage() {
            int page = -1;
            if (!mAllApps.isEmpty()) {
                page = (int) mAllApps.get(0).screenId;
            } else if (!mAllFolders.isEmpty()) {
                page = (int) mAllFolders.get(0).screenId;
            }
            return page;
        }

        @Override
        public String toString() {
            if (isEmpty()) {
                return "Empty PageInfo";
            } else {
                //M:[OP09][CF] add for folder type
                if (mSupportEditAndHideApps) {
                    int page = getPage();
                    return "PageInfo{ page = " + page + ", appSize = " + mAllApps.size()
                            + ", folderSize = " + mAllFolders.size() + "}";
                } else {
                    return "PageInfo{ page = " + mAllApps.get(0).screenId + ", appSize = "
                            + mAllApps.size() + "}";
                }
            }
        }

        //M: [OP09][CF]Create folder in all app list. @{
       /**
         * M: add an folder info.
         *
         * @param appInfo: The app info will be added.
         */
        public void add(final FolderInfo folderInfo) {
            mAllFolders.add(folderInfo);
        }

        /**

        /**
         * M: remove an folder.
         *
         * @param folderInfo
         */
        public void remove(final FolderInfo folderInfo) {
            final int pos = find(folderInfo);
            if (pos != -1) {
                mAllFolders.remove(pos);
            }
        }

       /**
         * M: find the specified folder info.
         *
         * @param info: The folder info will be found.
         * @return: If find, return the folder info, if not found, return null.
         */
        public int find(final FolderInfo info) {
            for (int i = 0; i < mAllFolders.size(); i++) {
                FolderInfo folderInfo = mAllFolders.get(i);
                if (folderInfo.id == info.id) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * M: find the specified folder info by id.
         * @param id The folder info id will be found.
         * @return If find, return the folder info, if not found, return null.
         */
        public FolderInfo findFolder(final int id) {
            for (FolderInfo folderInfo : mAllFolders) {
                if (folderInfo.id == id) {
                    if (LauncherLog.DEBUG_EDIT) {
                        LauncherLog.d(TAG, "findFolder, id = " + id + ",folderInfo=" + folderInfo);
                    }
                    return folderInfo;
                }
            }
            return null;
        }

        /**
         * PageInfo Items Position Comparator.
         */
        private final Comparator<ItemInfo> mPageItemPositionComparator
                                            = new Comparator<ItemInfo>() {
            @Override
            public int compare(ItemInfo item1, ItemInfo item2) {
                if (item1.screenId > item2.screenId) {
                    return 1;
                } else if (item1.screenId < item2.screenId) {
                    return -1;
                } else {
                    final int pos1 = item1.cellY * AllApps.sAppsCellCountX + item1.cellX;
                    final int pos2 = item2.cellY * AllApps.sAppsCellCountX + item2.cellX;
                    return pos1 - pos2;
                }
            }
        };
        //M: [OP09][CF] }@
    }

    /**
     * M: Realtime reorder.
     *
     * @param empty
     * @param target
     * @param layout
     */
    private void realTimeReorder(int[] empty, int[] target, AppsCustomizeCellLayout layout) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "realTimeReorder: empty = (" + empty[0] + ", " + empty[1]
                    + "),target = (" + target[0] + ", " + target[1] + ")." + ",screen = "
                    + (layout.getTag() == null ? -1 : layout.getTag().screenId));
        }

        if (!isValidateReorder(empty, target)) {
            LauncherLog.d(TAG, "realTimeReorder: invalidate reorder, return");
            return;
        }

        boolean wrap;
        int startX;
        int endX;
        int startY;
        int delay = 0;
        float delayAmount = 30;

        if (readingOrderGreaterThan(target, empty)) {
            wrap = empty[0] >= layout.getCountX() - 1;
            startY = wrap ? empty[1] + 1 : empty[1];
            for (int y = startY; y <= target[1]; y++) {
                startX = y == empty[1] ? empty[0] + 1 : 0;
                endX = y < target[1] ? layout.getCountX() - 1 : target[0];
                for (int x = startX; x <= endX; x++) {
                    View v = layout.getChildAt(x, y);
                    if (v != null
                            && layout.animateChildToPosition(v, empty[0], empty[1],
                                    REORDER_ANIMATION_DURATION, delay, true, true)) {
                        empty[0] = x;
                        empty[1] = y;
                        delay += delayAmount;
                        delayAmount *= 0.9;
                        mOccuredReorder = true;
                    }
                }
            }
        } else {
            wrap = empty[0] == 0;
            startY = wrap ? empty[1] - 1 : empty[1];
            for (int y = startY; y >= target[1]; y--) {
                startX = y == empty[1] ? empty[0] - 1 : layout.getCountX() - 1;
                endX = y > target[1] ? 0 : target[0];
                for (int x = startX; x >= endX; x--) {
                    View v = layout.getChildAt(x, y);
                    if (v != null
                            && layout.animateChildToPosition(v, empty[0], empty[1],
                                    REORDER_ANIMATION_DURATION, delay, true, true)) {
                        empty[0] = x;
                        empty[1] = y;
                        delay += delayAmount;
                        delayAmount *= 0.9;
                        mOccuredReorder = true;
                    }
                }
            }
        }
    }

    private boolean isValidateReorder(int[] empty, int[] target) {
         LauncherLog.d(TAG, "isValidateReorder: empty = (" + empty[0] + ", " + empty[1]
                    + "),target = (" + target[0] + ", " + target[1] + ").");
        if (empty[0] == target[0] && empty[1] == target[1]) {
            return false;
        }
        if (empty[0] == -1 || empty[0] == -1) {
            return false;
        }

        if (empty[0] > AllApps.sAppsCellCountX || empty[1] > AllApps.sAppsCellCountY) {
            return false;
        }

        if (target[0] > AllApps.sAppsCellCountX || target[1] > AllApps.sAppsCellCountY) {
            return false;
        }
        return true;
    }

    /**
     * M: Calculate the order, merge from Folder.
     *
     * @param v1
     * @param v2
     * @return
     */
    private boolean readingOrderGreaterThan(int[] v1, int[] v2) {
        return (v1[1] > v2[1] || (v1[1] == v2[1] && v1[0] > v2[0]));
    }

    /**
     * M: Set reorder alarm.
     */
    private final OnAlarmListener mReorderAlarmListener = new OnAlarmListener() {
        public void onAlarm(Alarm alarm) {
            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], 1, 1, mDragTargetLayout,
                    mTargetCell);

            if (!isValidateReorder(mEmptyCell, mTargetCell)) {
                LauncherLog.d(TAG, "realTimeReorder: invalidate reorder, return");
                return;
            }

            mLastReorderX = mTargetCell[0];
            mLastReorderY = mTargetCell[1];


            if (mTargetCell[0] < 0 || mTargetCell[1] < 0) {
                mDragTargetLayout.revertTempState();
            } else {
                setDragMode(Workspace.DRAG_MODE_REORDER);
            }
            LauncherLog.d(TAG, "mReorderAlarmListener, onAlarm");
            realTimeReorder(mEmptyCell, mTargetCell, getCurrentDropLayout());
        }
    };

    /**
     * M: Support OP09 UnInstall App When click delete button In EditMode.
     * @param v the view which want to be touch
     * @param event the motion event
     * @return wether handle the motion event or not
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (LauncherLog.DEBUG_MOTION) {
            LauncherLog.d(TAG, "onTouch: v = " + v + ", event = " + event + ",this = " + this);
        }

        /// M: [OP09] Make Sure deleteButton is shown
        if (mSupportEditAndHideApps
                && v instanceof BubbleTextView
                && ((BubbleTextView) v).getDeleteButtonVisibility()
                && event.getAction() == MotionEvent.ACTION_DOWN
                && touchInDeleteArea(event)) {
                mLauncher.onClickDeleteButton(v);
            return true;
        } else {
            return super.onTouch(v, event);
        }
    }

    /**
     * M: Support OP09 Check touch in Delete Area.
     */
    private boolean touchInDeleteArea(MotionEvent event) {
        int deleteButtonWidth = mDeleteButtonDrawable.getIntrinsicWidth();
        int deleteButtonHeight = mDeleteButtonDrawable.getIntrinsicHeight();
        int eventx = (int) event.getX();
        int eventy = (int) event.getY();

        if (eventx >= (mDeleteMarginleft - mTouchDelta)
                && eventx <= (mDeleteMarginleft + deleteButtonWidth + mTouchDelta)
                && eventy + mTouchDelta >= 0
                && eventy <= (deleteButtonHeight + mTouchDelta)) {
            return true;
        }
        return false;
    }

    void updateAppsPageCounts() {
        /// M: use apps pages size as number of apps page if edit mode is supported.
        if (!mSupportEditAndHideApps) {
            mNumAppsPages = (int) Math.ceil((float) mApps.size() / (mCellCountX * mCellCountY));
        } else {
            mNumAppsPages = sAllAppsPage.size();
        }

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "updateAppsPageCounts end: mNumWidgetPages = " + mNumWidgetPages
                    + ", mNumAppsPages = "
                    + mNumAppsPages + ", mCellCountX = " + mCellCountX + ", mCellCountY = "
                    + mCellCountY + ", mApps.size() = " + mApps.size());
        }
    }

    /**
     * M: Find apps by package name.
     */
    private int findAppByPackage(List<AppInfo> list, String packageName) {
        List<LauncherActivityInfoCompat> matches = LauncherAppsCompat.getInstance(getContext())
                        .getActivityList(packageName, null);
        int length = matches.size();
        for (int i = 0; i < length; ++i) {
            /*AppInfo info = list.get(i);*/
            LauncherActivityInfoCompat info = matches.get(i);
            //if (info.getPackageName().equals(packageName)) {
                /// M: we only remove items whose component is in disable state,
                /// this is add to deal the case that there are more than one
                /// activities with LAUNCHER category, and one of them is
                /// disabled may cause all activities removed from app list.
                final boolean isComponentEnabled = Utilities.isComponentEnabled(getContext(),
                        info.getComponentName());

                // mtk add
                boolean removed = false;
                if (matches.size() > 0) {
                    final ComponentName component = info.getComponentName();
                    if (!AllAppsList.findActivity(matches, component)) {
                        removed = true;
                    }
                } else {
                    removed = true;
                }
                // mtk add

                LauncherLog.d(TAG, "findAppByPackage: i = " + i + ",name = "
                        + info.getComponentName() + ", info = "
                        + info + ",isComponentEnabled = " + isComponentEnabled
                        + ",removed = " + removed); // mtk modify
                if (!isComponentEnabled || removed) { // mtk modify
                    return i;
                } else {
                    /// M: we need to make restore the app info in data list in all apps list
                    //to make information sync.
                    //mLauncher.getModel().restoreAppInAllAppsList(info);
                }
            //}
        }
        return -1;
    }
    /// M: Add for Edit AllAppsList for op09 end.}@
    //M:[OP09] }@

    //M:[OP09][CF] @{
    FolderIcon addFolder(CellLayout layout, final long pageId, int cellX,
            int cellY) {
        final FolderInfo folderInfo = new FolderInfo();
        folderInfo.title = mContext.getText(R.string.folder_name);
        folderInfo.cellX = cellX;
        folderInfo.cellY = cellY;
        folderInfo.screenId = (int) pageId;
        folderInfo.mPos = cellY * AllApps.sAppsCellCountX + cellX;

        // Update the database and model
        LauncherModel.addFolderItemToDatabase(
                mContext, folderInfo, (int) pageId, cellX, cellY, false);
        // Create the view
        FolderIcon newFolder =
                FolderIcon.fromXml(R.layout.folder_icon, mLauncher, layout,
                folderInfo, mIconCache, true);
        newFolder.setOnClickListener(mLauncher);
        newFolder.setOnLongClickListener(this);
        newFolder.setOnTouchListener(this);
        newFolder.setOnKeyListener(this);

        int pos = cellY * AllApps.sAppsCellCountX + cellX;
        layout.addViewToCellLayout(newFolder, -1, pos,
                new CellLayout.LayoutParams(cellX, cellY, 1, 1), false);

        // Force measure the new folder icon
        layout.getShortcutsAndWidgets().measureChild(newFolder);
        addOrRemoveFolder(folderInfo, true);

        return newFolder;
    }

    //Here, the drag info comes folder or comes apps.
    void addOrRemoveAppFromFolder(ShortcutInfo info, FolderInfo folderinfo, boolean add) {
        // add or remove the dragInfo
        if (info != null) {
            if (add) {
                folderinfo.add(info);
            } else {
                folderinfo.remove(info);
            }
        }
    }

    void addOrRemoveApp(AppInfo info, boolean add) {
        LauncherLog.d(TAG, "addOrRemoveApp: add = " + add + ", size = "
            + mApps.size() + ",info = " + info);
        if (add) {
            addAppToList((AppInfo) info, mApps);
            sAllAppsPage.get((int) info.screenId).add(info);
        } else {
            removeAppFromList((AppInfo) info, mApps);
            sAllAppsPage.get((int) info.screenId).remove(info);
        }
    }

    void addOrRemoveFolder(final FolderInfo folder, boolean add) {
        LauncherLog.d(TAG, "addOrRemoveFolder: add = " + add + ", size = "
            + sFolders.size() + ",info = " + folder);
        //add folder in sFolders
        if (add) {
            sFolders.add(sFolders.size(), folder);
            sAllAppsPage.get((int) folder.screenId).add(folder);
        } else {
            sFolders.remove(folder);
            sAllAppsPage.get((int) folder.screenId).remove(folder);
        }

        for (int i = 0; i < sFolders.size(); i++) {
            LauncherLog.d(TAG, "addOrRemoveFolder: i = " + i
                  + ", info = " + sFolders.get(i));
        }
    }

    boolean createUserFolderIfNecessary(View newView, CellLayout target,
            int[] targetCell, float distance, boolean external, DragView dragView,
            Runnable postAnimationRunnable) {
        if (distance > mMaxDistanceForFolderCreation) {
            return false;
        }
        View v = target.getChildAt(targetCell[0], targetCell[1]);
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "createUserFolderIfNecessary: newView = " + newView
                    + ", mDragInfo = " + mDragInfo + ", target = "
                    + target + ", targetCell[0] = " + targetCell[0] + ", targetCell[1] = "
                    + targetCell[1] + ", external = " + external + ", dragView = " + dragView
                    + ", v = " + v + ", mCreateUserFolderOnDrop = " + mCreateUserFolderOnDrop);
        }

        boolean hasntMoved = false;
        if (mDragInfo != null) {
            AppsCustomizeCellLayout cellParent = (AppsCustomizeCellLayout)
                getPageAt((int) mDragInfo.screenId);

            boolean xNoChange = (mDragInfo.cellX != mEmptyCell[0] && mEmptyCell[0] != -1) ?
                mEmptyCell[0] == targetCell[0] : mDragInfo.cellX == targetCell[0];
            boolean yNoChange = (mDragInfo.cellY != mEmptyCell[1] && mEmptyCell[1] != -1) ?
                mEmptyCell[1] == targetCell[1] : mDragInfo.cellY == targetCell[1];
            LauncherLog.d(TAG, "createUserFolderIfNecessary: mEmpty = " + mEmptyCell[0] + "."
                + mEmptyCell[1] + ").xNoChange = " + xNoChange + ", yNoChange = " + yNoChange);
            hasntMoved = xNoChange && yNoChange
                          && (cellParent == target);
        }

        if (v == null || hasntMoved || !mCreateUserFolderOnDrop) {
            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "Do not create user folder: hasntMoved = " + hasntMoved
                        + ", mCreateUserFolderOnDrop = "
                        + mCreateUserFolderOnDrop + ", v = " + v);
            }
            return false;
        }

        mCreateUserFolderOnDrop = false;
        final long screenId = (targetCell == null) ?
            mDragInfo.screenId : indexToPage(indexOfChild(target));

        AppInfo dragAppInfo = null;
        ShortcutInfo dragTag = null;
        dragAppInfo = (AppInfo) newView.getTag();
        dragTag = dragAppInfo.makeShortcut();

        newView.setTag(dragTag);

        LauncherLog.d(TAG, "createUserFolderIfNecessary: dragTag = " + dragTag
            + ", dragAppInfo = " + dragAppInfo);

        AppInfo destAppInfo = (AppInfo) v.getTag();
        ShortcutInfo destTag = destAppInfo.makeShortcut();
        v.setTag(destTag);


        boolean aboveShortcut = (destTag instanceof ShortcutInfo);
        boolean willBecomeShortcut = (dragTag instanceof ShortcutInfo);

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "createUserFolderIfNecessary: aboveShortcut = "
                    + aboveShortcut + ", willBecomeShortcut = " + willBecomeShortcut);
        }

        if (aboveShortcut && willBecomeShortcut) {

            Rect folderLocation = new Rect();
            float scale = mLauncher.getDragLayer()
                    .getDescendantRectRelativeToSelf(v, folderLocation);
            target.removeView(v);

            FolderIcon fi = addFolder(target, screenId, targetCell[0], targetCell[1]);
            destTag.cellX = -1;
            destTag.cellY = -1;
            dragTag.cellX = -1;
            dragTag.cellY = -1;
            //before move to folder, modify the app's position.
            dragTag.mPos = -1;
            destTag.mPos = -1;

            // If the dragView is null, we can't animate
            boolean animate = dragView != null;
            if (animate) {
                fi.performCreateAnimation(destTag, v, dragTag, dragView, folderLocation, scale,
                        postAnimationRunnable);
            } else {
                fi.addItem(destTag);
                fi.addItem(dragTag);
            }
            //update the data list
            addOrRemoveApp(destAppInfo, false);
            return true;
        }
        return false;
    }

    boolean addToExistingFolderIfNecessary(View newView, CellLayout target, int[] targetCell,
            float distance, DragObject d, boolean external) {
        if (distance > mMaxDistanceForFolderCreation) {
            return false;
        }

        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "addToExistingFolderIfNecessary: newView = " + newView
                        + ", target = " + target + ", targetCell[0] = "
                        + targetCell[0] + ", targetCell[1] = "
                        + targetCell[1] + ", external = " + external + ", d = " + d
                        + ", dropOverView = " + dropOverView);
        }
        if (!mAddToExistingFolderOnDrop) {
            return false;
        }
        mAddToExistingFolderOnDrop = false;

        if (dropOverView instanceof FolderIcon) {
            FolderIcon fi = (FolderIcon) dropOverView;
            if (fi.acceptDrop(d.dragInfo)) {
                fi.onDrop(d);

                // if the drag started here, we need to remove it from the workspace
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "addToExistingFolderIfNecessary: fi = " + fi
                            + ", d = " + d);
                }
                return true;
            }
        }
        return false;
    }


    void setDragMode(int dragMode) {
        if (dragMode != mDragMode) {
            if (dragMode == Workspace.DRAG_MODE_NONE) {
                cleanupAddToFolder();
                // We don't want to cancel the re-order alarm every time the target cell changes
                // as this feels to slow / unresponsive.
                cleanupReorder(false);
                cleanupFolderCreation();
            } else if (dragMode == Workspace.DRAG_MODE_ADD_TO_FOLDER) {
                cleanupReorder(true);
                cleanupFolderCreation();
            } else if (dragMode == Workspace.DRAG_MODE_CREATE_FOLDER) {
                cleanupAddToFolder();
                cleanupReorder(true);
            } else if (dragMode == Workspace.DRAG_MODE_REORDER) {
                cleanupAddToFolder();
                cleanupFolderCreation();
            }
            mDragMode = dragMode;
        }
    }

    private void cleanupFolderCreation() {
        if (mDragFolderRingAnimator != null) {
            mDragFolderRingAnimator.animateToNaturalState();
            mDragFolderRingAnimator = null;
        }
        mFolderCreationAlarm.setOnAlarmListener(null);
        mFolderCreationAlarm.cancelAlarm();
    }

    private void cleanupAddToFolder() {
        if (mDragOverFolderIcon != null) {
            mDragOverFolderIcon.onDragExit(null);
            mDragOverFolderIcon = null;
        }
    }

    private void cleanupReorder(boolean cancelAlarm) {
        // Any pending reorders are canceled
        if (cancelAlarm) {
            mReorderAlarm.cancelAlarm();
        }
        mLastReorderX = -1;
        mLastReorderY = -1;
    }

    protected boolean manageFolderFeedback(ItemInfo info, CellLayout targetLayout,
            int[] targetCell, float distance, View dragOverView) {
        boolean userFolderPending = willCreateUserFolder(info, targetLayout, targetCell, distance,
                false, mDragInfo, mCreateUserFolderOnDrop);
        LauncherLog.d(TAG, "manageFolderFeedback, stone, userFolderPending=" + userFolderPending
            + ", mDragMode = " + mDragMode);
        if (mDragMode == Workspace.DRAG_MODE_NONE && userFolderPending &&
                !mFolderCreationAlarm.alarmPending()) {
            mFolderCreationAlarm.setOnAlarmListener(new
                    FolderCreationAlarmListener(targetLayout, targetCell[0], targetCell[1]));
            mFolderCreationAlarm.setAlarm(Workspace.FOLDER_CREATION_TIMEOUT);
            return true;
        }

        boolean willAddToFolder =
                willAddToExistingUserFolder(info, targetLayout, targetCell, distance);
        LauncherLog.d(TAG, "manageFolderFeedback, stone, willAddToFolder=" + willAddToFolder
            + ", mDragMode = " + mDragMode);

        if (willAddToFolder && mDragMode == Workspace.DRAG_MODE_NONE) {
            mDragOverFolderIcon = ((FolderIcon) dragOverView);
            mDragOverFolderIcon.onDragEnter(info);
            if (targetLayout != null) {
                targetLayout.clearDragOutlines();
            }
            setDragMode(Workspace.DRAG_MODE_ADD_TO_FOLDER);
            return true;
        }

        if (mDragMode == Workspace.DRAG_MODE_ADD_TO_FOLDER && !willAddToFolder) {
            setDragMode(Workspace.DRAG_MODE_NONE);
        }
        if (mDragMode == Workspace.DRAG_MODE_CREATE_FOLDER && !userFolderPending) {
            setDragMode(Workspace.DRAG_MODE_NONE);
        }
        LauncherLog.d(TAG, "manageFolderFeedback exit, mDragMode=" + mDragMode
            + ", userFolderPending=" + userFolderPending
            + ", willAddToFolder = " + willAddToFolder);
        return false;
    }

    private final Alarm mFolderCreationAlarm = new Alarm();
    private FolderRingAnimator mDragFolderRingAnimator = null;
    private FolderIcon mDragOverFolderIcon = null;

    /**
      *M: create alarm listener.
      */
    public class FolderCreationAlarmListener implements OnAlarmListener {
        CellLayout mLayout;
        int mCellX;
        int mCellY;

        /**
        *M: create alarm listener.
        * @param layout which layout to create folder
        * @param cellX the x position
        * @param cellY the y position
        */
        public FolderCreationAlarmListener(CellLayout layout, int cellX, int cellY) {
            this.mLayout = layout;
            this.mCellX = cellX;
            this.mCellY = cellY;
        }

        /**
        *M: when alarm is executed, we will jump this function.
        * @param alarm the alarm which to be trigged
        */
        public void onAlarm(Alarm alarm) {
            if (mDragFolderRingAnimator != null) {
                // This shouldn't happen ever, but just in case, make sure we clean up the mess.
                mDragFolderRingAnimator.animateToNaturalState();
            }
            mDragFolderRingAnimator = new FolderRingAnimator(mLauncher, null);
            mDragFolderRingAnimator.setCell(mCellX, mCellY);
            mDragFolderRingAnimator.setCellLayout(mLayout);
            mDragFolderRingAnimator.animateToAcceptState();
            mLayout.showFolderAccept(mDragFolderRingAnimator);
            mLayout.clearDragOutlines();
            LauncherLog.d(TAG, "FolderCreationAlarmListener, onAlarm");
            setDragMode(Workspace.DRAG_MODE_CREATE_FOLDER);
        }
    }


    private void onDropExternal(int[] touchXY, Object dragInfo,
            CellLayout cellLayout, boolean insertAtFirst) {
        onDropExternal(touchXY, dragInfo, cellLayout, insertAtFirst, null);
    }

    /**
     * Drop an item that didn't originate on one of the workspace screens.
     * It may have come from Launcher (e.g. from all apps or customize), or it may have
     * come from another app altogether.
     *
     * NOTE: This can also be called when we are outside of a drag event, when we want
     * to add an item to one of the workspace screens.
     */
    private void onDropExternal(final int[] touchXY, final Object dragInfo,
            CellLayout cellLayout, boolean insertAtFirst, DragObject d) {
        ItemInfo info = (ItemInfo) dragInfo;
        boolean updateTargetCell = false;
        boolean add = false;

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onDropExternal: touchXY[0] = "
                    + ((touchXY != null) ? touchXY[0] : -1) + ", touchXY[1] = "
                    + ((touchXY != null) ? touchXY[1] : -1) + ", dragInfo = " + dragInfo
                    + ",info = " + info + ", cellLayout = " + cellLayout + ", insertAtFirst = "
                    + insertAtFirst + ", dragInfo = " + d.dragInfo);
        }

        if (cellLayout == null) {
            cellLayout = mCurrentDropTarget;
        } else {
            updateTargetCell = true;
        }
        final long screenId = (cellLayout == null) ?
                  info.screenId : indexToPage(indexOfChild(cellLayout));

        if (mCurrentPage != screenId) {
            snapToPage((int) screenId);
        }

        AppsCustomizeCellLayout layout = (AppsCustomizeCellLayout) getPageAt((int) screenId);

        // This is for other drag/drop cases, like dragging from All Apps
        View view = null;

        switch (info.itemType) {
        case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
        case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
            view = LayoutInflater.from(getContext()).inflate(
                       R.layout.apps_customize_application, cellLayout, false);
            break;
        default:
            throw new IllegalStateException("Unknown item type: " + info.itemType);
        }

        AppInfo appinfo = ((ShortcutInfo) info).makeAppInfo();
        if (view != null && view instanceof BubbleTextView) {
            ((BubbleTextView) view).applyFromApplicationInfo(appinfo);
            ((BubbleTextView) view).setOnClickListener(this);
            ((BubbleTextView) view).setOnLongClickListener(this);
            ((BubbleTextView) view).setOnTouchListener(this);
            ((BubbleTextView) view).setOnKeyListener(this);
            //modify the color of app
            if (Launcher.DISABLE_APPLIST_WHITE_BG) {
                ((BubbleTextView) view).setTextColor(getContext().getResources().getColor(
                       R.color.quantum_panel_transparent_bg_text_color));
            }
            // update the delete button
            if (Launcher.isInEditMode()) {
                ((BubbleTextView) view).setDeleteButtonVisibility(!isSystemApp(appinfo));
            }
        } else {
            LauncherLog.d(TAG, "onDropExternal: return, because view = " + view);
            return ;
        }

        // First we find the cell nearest to point at which the item is
        // dropped, without any consideration to whether there is an item there.
        if (touchXY != null) {
            mTargetCell = findNearestArea((int) touchXY[0], (int) touchXY[1], 1, 1,
                    cellLayout, mTargetCell);
            float distance = cellLayout.getDistanceFromCell(mDragViewVisualCenter[0],
                    mDragViewVisualCenter[1], mTargetCell);
            if (createUserFolderIfNecessary(view, cellLayout, mTargetCell, distance,
                    true, d.dragView, d.postAnimationRunnable)) {
                add = true;
            }
            if (addToExistingFolderIfNecessary(view, cellLayout, mTargetCell, distance, d,
                    true)) {
                add = true;
            }
        }

        if (touchXY == null) {
            cellLayout.findCellForSpan(mTargetCell, 1, 1);
        }

        if (!add) {
            if (updateTargetCell) { //no place to drop
                updateTargetCell = false;
                mTargetCell[0] = mEmptyCell[0];
                mTargetCell[1] = mEmptyCell[1];
            }

            if (cellLayout.isExceedLastPosition(mTargetCell)) {
                mTargetCell = cellLayout.getLastPosition(mTargetCell);
            }

            appinfo.cellX = mTargetCell[0];
            appinfo.cellY = mTargetCell[1];
            appinfo.mPos = info.cellY * AllApps.sAppsCellCountX + info.cellX;
            ((BubbleTextView) view).applyFromApplicationInfo(appinfo);


            View child = layout.getChildAt(mTargetCell[0], mTargetCell[1]);
            LauncherLog.d(TAG, "onDropExternal: view = " + view + ", appinfo = " + appinfo
                    + ", child = " + child);
            // If has the view in the target cell
            if (child != null) { //this case rarely happen
                if (info.screenId != screenId) {
                    // If the page is full, process the last cell.
                    saveLastCellInFullPage(layout, true);
                }
                // Realtime reorder all icons position.
                LauncherLog.d(TAG, "onDropExternal realTimeReorder, mEmpty[0] = " + mEmptyCell[0]
                                + "emptyY = " + mEmptyCell[1]
                     + ", targetX=" + mTargetCell[0] + ", targetY=" + mTargetCell[1]);
                realTimeReorder(mEmptyCell, mTargetCell, layout);
            }
            // Update item position after drop.
            updateItemPositionAfterDrop(layout, view, (int) screenId);
            mOccuredReorder = false;
            layout.onDropChild(view);
        } else {
            LauncherLog.d(TAG, "onDropExternal reorderForFolderCreateOrDelete, mEmpty = ("
                     + mEmptyCell[0] + "." + mEmptyCell[1]
                     + "), target=(" + mTargetCell[0] + "." + mTargetCell[1] + ").");
            // Realtime reorder all icons position.
            reorderForFolderCreateOrDelete(mEmptyCell, mTargetCell, layout, true);
            // Update all apps position in the page after realTimeReorder.
            updateItemLocationsInDatabase(layout);
        }
        d.deferDragViewCleanupPostAnimation = false;
        mPrevDropTarget = null;
    }



    /**
     * M: Realtime reorder.
     *
     * @param empty
     * @param target
     * @param layout
     */
    void reorderForFolderCreateOrDelete(int[] empty, int[] target,
                                       AppsCustomizeCellLayout layout, boolean create) {
        int childCount = layout.getShortcutsAndWidgets().getChildCount();
        LauncherLog.d(TAG, "ReorderForFolderCreateOrDelete: empty = (" + empty[0] + ", " + empty[1]
                 + "),target = (" + target[0] + ", " + target[1]
                 + "). childCount = " + childCount);

        if (!isValidateReorder(empty, target)) {
            LauncherLog.d(TAG, "realTimeReorder: invalidate reorder, return");
            return;
        }

        int startX;
        int startY;
        int endX;
        int endY;
        boolean wrap;

        int delay = 0;
        float delayAmount = 30;

        if (create) { //for added
            wrap = empty[0] >=  AllApps.sAppsCellCountX - 1;
            startY = wrap ? empty[1] + 1 : empty[1];
            endY = (int) childCount / AllApps.sAppsCellCountX;
            LauncherLog.d(TAG, "ReorderForFolderCreateOrDelete: wrap = "
                             + wrap + ", startY = " + startY
                             + ", endY = " + endY);

            for (int y = startY; y <= endY; y++) {
                startX = y == empty[1] ? empty[0] + 1 : 0;
                if (y == endY) {
                    endX = childCount % AllApps.sAppsCellCountX;
                } else {
                    endX = AllApps.sAppsCellCountX - 1;
                }

                LauncherLog.d(TAG, "ReorderForFolderCreateOrDelete: startX = "
                             + startX + ", endX=" + endX
                             + ", startY = " + startY + ", endY = " + endY);
                for (int x = startX; x <= endX; x++) {
                    View v = layout.getChildAt(x, y);
                    if (v != null
                            && layout.animateChildToPosition(v, empty[0], empty[1],
                                    REORDER_ANIMATION_DURATION, delay, true, true)) {
                        empty[0] = x;
                        empty[1] = y;
                        LauncherLog.d(TAG, "ReorderForFolderCreateOrDelete: x = " + x
                            + ", y = " + y);
                        delay += delayAmount;
                        delayAmount *= 0.9;
                        mOccuredReorder = true;
                    }
                }
            }
        } else { //for delete
             //here, we get count, if count == full, return directly, don't reorder;
             if (childCount == AllApps.sAppsCellCountX * AllApps.sAppsCellCountY) {
                 LauncherLog.d(TAG, "ReorderForFolderCreateOrDelete: delete drop, full, return.");
                 return ;
             }
             //else, we will reorder the celllayout.
             wrap = childCount % AllApps.sAppsCellCountX == 0;
             startY = wrap ? (int) childCount / AllApps.sAppsCellCountX - 1
                                  : (int) childCount / AllApps.sAppsCellCountX;
             endY = target[1];
             LauncherLog.d(TAG, "ReorderForFolderCreateOrDelete: delete startY = " + startY
                     + ", endY = " + endY + ", wrap = " + wrap);
             for (int y = startY; y >= endY; y--) {
                 if (y == startY) {
                      startX = wrap ? AllApps.sAppsCellCountX - 1
                                    : childCount % AllApps.sAppsCellCountX - 1;
                 } else {
                      startX = AllApps.sAppsCellCountX - 1;
                 }
                 endX = y == target[1] ? target[0] : 0;
                 LauncherLog.d(TAG, "ReorderForFolderCreateOrDelete: delete startX = " + startX
                     + ", endX = " + endX + ", startY = " + startY + ", endY = " + endY);
                 for (int x = startX; x >= endX; x--) {
                     int targetIndex = x + y * AllApps.sAppsCellCountX + 1;
                     empty[0] = (targetIndex % AllApps.sAppsCellCountX == 0) ?
                                               AllApps.sAppsCellCountX - 1
                                               : targetIndex % AllApps.sAppsCellCountX;
                     empty[1] = (targetIndex % AllApps.sAppsCellCountX == 0) ? y - 1 : y;
                     View v = layout.getChildAt(x, y);
                     LauncherLog.d(TAG, "ReorderForFolderCreateOrDelete: delete x = " + x
                            + ", y = " + y + ", targetX = " + empty[0] + ", targetX = " + empty[1]);
                     if (v != null
                             && layout.animateChildToPosition(v, empty[0], empty[1],
                                     REORDER_ANIMATION_DURATION, delay, true, true)) {
                         empty[0] = x;
                         empty[1] = y;
                         LauncherLog.d(TAG, "ReorderForFolderCreateOrDelete: x = " + x
                             + ", y = " + y);
                         delay += delayAmount;
                         delayAmount *= 0.9;
                         mOccuredReorder = true;
                     }
                 }
            }
        }
    }

    boolean willCreateUserFolder(ItemInfo info, CellLayout target, int[] targetCell,
                                           float distance, boolean considerTimeout,
                                           CellLayout.CellInfo dragInfo,
                                           boolean createFolder) {
        if (distance > mMaxDistanceForFolderCreation) {
            return false;
        }
        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);

        if (dropOverView != null) {
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) dropOverView.getLayoutParams();
            if (lp.useTmpCoords && (lp.tmpCellX != lp.cellX || lp.tmpCellY != lp.tmpCellY)) {
                LauncherLog.d(TAG, "willCreateUserFolder: first return false");
                return false;
            }
        }

        boolean hasntMoved = false;
        if (mDragInfo != null) {
            if (dropOverView != null) {
                hasntMoved = dropOverView == dragInfo.cell;
            } else {
                LauncherLog.d(TAG, "willCreateUserFolder: drag.cellX =" + dragInfo.cellX
                     + ", dragInfo.cellY = " + dragInfo.cellY
                     + ", targetCell[0] = " + targetCell[0]
                     + ", targetCell[1] = " + targetCell[1]);
                hasntMoved = (dragInfo.cellX == targetCell[0]
                     && dragInfo.cellY == targetCell[1]);
            }
        }

        LauncherLog.d(TAG, "willCreateUserFolder: hasntMoved = " + hasntMoved
                + ",considerTimeout=" + considerTimeout
                + ",createFolder=" + createFolder);
        if (dropOverView == null || hasntMoved || (considerTimeout && !createFolder)) {
            LauncherLog.d(TAG, "willCreateUserFolder: second return,hasntMoved="
                + hasntMoved + ",dropOverView=" + dropOverView);
            return false;
        }

        boolean aboveShortcut = (dropOverView.getTag() instanceof ShortcutInfo ||
                              dropOverView.getTag() instanceof AppInfo);
        boolean willBecomeShortcut =
                (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                info.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT);

        LauncherLog.d(TAG, "willCreateUserFolder: aboveShortcut = " + aboveShortcut
                           + ", willBecomeShortcut = " + willBecomeShortcut);
        return (aboveShortcut && willBecomeShortcut);
    }

    boolean willAddToExistingUserFolder(Object dragInfo, CellLayout target, int[] targetCell,
            float distance) {
        if (distance > mMaxDistanceForFolderCreation) {
            return false;
        }
        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);

        if (dropOverView != null) {
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) dropOverView.getLayoutParams();
            if (lp.useTmpCoords && (lp.tmpCellX != lp.cellX || lp.tmpCellY != lp.tmpCellY)) {
                return false;
            }
        }

        if (dropOverView instanceof FolderIcon) {
            FolderIcon fi = (FolderIcon) dropOverView;
            if (fi.acceptDrop(dragInfo)) {
                return true;
            }
        }
        return false;
    }

    /// M: ALPS01577456
    // if no space in destination screen, then drag an application icon into existing
    //folder and key up quickly,the je will be occur. so add this method to avoid
    //the case: only allow add to existing folder if FolderIcon
    // can accept drop and is ring state {@
    boolean willAddToExistingUserFolderIfRingState(Object dragInfo, CellLayout target,
                              int[] targetCell, float distance) {
        if (distance > mMaxDistanceForFolderCreation) {
            return false;
        }
        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);

        LauncherLog.d(TAG, "willAddToExistingUserFolderIfRingState, dropOverView: " + dropOverView);
        if (dropOverView != null) {
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) dropOverView.getLayoutParams();
            if (lp.useTmpCoords && (lp.tmpCellX != lp.cellX || lp.tmpCellY != lp.tmpCellY)) {
                return false;
            }
        }

        if (dropOverView instanceof FolderIcon) {
            FolderIcon fi = (FolderIcon) dropOverView;
            if (fi.acceptDrop(dragInfo) && fi.isRingState()) {
                fi.resetRingState();
                return true;
            }
        }
        return false;
    }

    /**
     * M: Set reorder alarm.
     */
    private final OnAlarmListener mReorderFolderAlarmListener =
                         new OnAlarmListener() {
        public void onAlarm(Alarm alarm) {
            reorderForFolderCreateOrDelete(mEmptyCell, mTargetCell,
                getCurrentDropLayout(), true);
        }
    };


    /**
     * M: Recorder the last cell in full page.
     *
     * @param layout
     * @param dragFromOtherScreen
     */
    private void findEmptyCell(final CellLayout layout) {

        final int page = indexToPage(indexOfChild(layout));
        final PageInfo pageInfo = sAllAppsPage.get(page);
        final int childCount = layout.getChildrenLayout().getChildCount();
        int index = childCount;

        LauncherLog.i(TAG, "findEmptyCell mLastCell = " + mLastCell
             + ", full = " + pageInfo.isFull()
             + ", childCount = " + childCount + ", size = "
             + pageInfo.getAppsAndFoldersCount() + "mEmptyCell = ("
             + mEmptyCell[0] + "." + mEmptyCell[1] + ").");

        // If drag icon from another page, the last cell in the page will be the
        // empty cell.
        if (!pageInfo.isFull() && mEmptyCell[0] == -1
                           && mEmptyCell[1] == -1) {
            mEmptyCell[0] = index % mCellCountX;
            mEmptyCell[1] = index / mCellCountX;
            /// We need to reset mOccuredReorder to true against
            //rollbackLastScreenItemsPosition.
            mOccuredReorder = true;
        }

        if (childCount != pageInfo.getAppsAndFoldersCount()) {
            foreachPage();
        }

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "findEmptyCell mEmptyCell = (" + mEmptyCell[0]
                    + "." + mEmptyCell[1] + ").");
        }
    }

    void setCurrentDropOverCell(int x, int y) {
        if (x != mDragOverX || y != mDragOverY) {
            mDragOverX = x;
            mDragOverY = y;
            setDragMode(Workspace.DRAG_MODE_NONE);
        }
    }

    void foreachPage() {
        LauncherLog.d(TAG, "foreachPage mNumAppsPages=" + mNumAppsPages);
        for (int i = 0; i < mNumAppsPages; i++) {
            PageInfo pi = sAllAppsPage.get(i);
            int size = pi.mAllApps.size();
            LauncherLog.d(TAG, "foreachPage i=" + i + ",appsize=" + size + ",foldersize="
                + pi.mAllFolders.size() + ", allappsize = " + mApps.size()
                + ", allfoldersize = " + sFolders.size());

            int count = pi.mAllApps.size() + pi.mAllFolders.size();
            ItemInfo[] itemInfo  = new ItemInfo[count];

            for (int m = 0; m < pi.mAllApps.size(); m++) {
                itemInfo[m] = pi.mAllApps.get(m);
                itemInfo[m].mPos = itemInfo[m].cellX + itemInfo[m].cellY *
                    AllApps.sAppsCellCountX;
            }
            for (int j = 0; j < pi.mAllFolders.size(); j++) {
                itemInfo[size + j] = pi.mAllFolders.get(j);
                itemInfo[size + j].mPos = itemInfo[size + j].cellX + itemInfo[size + j].cellY *
                    AllApps.sAppsCellCountX;
            }

            sort(itemInfo);

            for (int k = 0; k < count; k++) {
                 LauncherLog.d(TAG, "foreachPage k=" + k + ",pos=" + itemInfo[k].mPos + ",title="
                    + (TextUtils.isEmpty(itemInfo[k].title) ? itemInfo[k].id : itemInfo[k].title));
            }
            itemInfo = null;
        }
    }

    void sort(ItemInfo[]  info) {
        ItemInfo tempInfo = null;
        for (int i = info.length - 1; i > 0; --i) {
            for (int j = 0; j < i; ++j) {
                if (info[j + 1].mPos < info[j].mPos) {
                    tempInfo = info[j];
                    info[j] = info[j + 1];
                    info[j + 1] = tempInfo;
                }
            }
        }
    }
    // Removes ALL items that match a given package name, this is usually called when a package
    // has been removed and we want to remove all components (widgets, shortcuts, apps) that
    // belong to that package.
    void removeItemsInFolderByPackageName(final ArrayList<String> packages,
        final UserHandleCompat user) {
        LauncherLog.d(TAG, "removeItemsInFolderByPackageName: mContentType = " + mContentType
             + ", size = " + mAppsArray.size());
        if (mContentType == ContentType.Widgets) {
            RemoveAppData appdata = new RemoveAppData();
            appdata.mRmPackagesInfo = packages;
            appdata.mUser = user;
            mAppsArray.put(mAppsArray.size(), appdata);
            mIsAppRemoved = true;
            return;
        }

        final HashSet<String> packageNames = new HashSet<String>();
        packageNames.addAll(packages);

        // Filter out all the ItemInfos that this is going to affect
        final HashSet<ItemInfo> infos = new HashSet<ItemInfo>();
        final HashSet<ComponentName> cns = new HashSet<ComponentName>();

        ArrayList<AppsCustomizeCellLayout> cellLayouts = getAppsCustomizeCellLayouts();
        for (CellLayout layoutParent : cellLayouts) {
            ViewGroup layout = layoutParent.getShortcutsAndWidgets();
            int childCount = layout.getChildCount();
            for (int i = 0; i < childCount; ++i) {
                View view = layout.getChildAt(i);
                infos.add((ItemInfo) view.getTag());
            }
        }
        LauncherModel.ItemInfoFilter filter = new LauncherModel.ItemInfoFilter() {
            @Override
            public boolean filterItem(ItemInfo parent, ItemInfo info,
                                      ComponentName cn) {
                if (packageNames.contains(cn.getPackageName())
                        && info.user.equals(user)) {
                    cns.add(cn);
                    return true;
                }
                return false;
            }
        };
        LauncherModel.filterItemInfos(infos, filter);

        // Remove the affected components
        removeItemsByComponentName(cns, user);
    }

    // Removes items that match the application info specified, when applications are removed
    // as a part of an update, this is called to ensure that other widgets and application
    // shortcuts are not removed.
    void removeItemsInFolderByApplicationInfo(final ArrayList<AppInfo> appInfos,
                                    UserHandleCompat user) {
        // Just create a hash table of all the specific components that this will affect
        LauncherLog.d(TAG, "removeItemsInFolderByApplicationInfo: mContentType = " + mContentType
             + ", size = " + mAppsArray.size());
        if (mContentType == ContentType.Widgets) {
            RemoveAppData appdata = new RemoveAppData();
            appdata.mRmAppsInfo = appInfos;
            appdata.mUser = user;
            mAppsArray.put(mAppsArray.size(), appdata);
            mIsAppRemoved = true;
            return;
        }

        HashSet<ComponentName> cns = new HashSet<ComponentName>();
        for (AppInfo info : appInfos) {
            cns.add(info.componentName);
        }

        // Remove all the things
        removeItemsByComponentName(cns, user);
    }

    void removeItemsByComponentName(final HashSet<ComponentName> componentNames,
            final UserHandleCompat user) {
        ArrayList<AppsCustomizeCellLayout> cellLayouts = getAppsCustomizeCellLayouts();
        for (final CellLayout layoutParent: cellLayouts) {
            final ViewGroup layout = layoutParent.getShortcutsAndWidgets();
            final HashMap<ItemInfo, View> children = new HashMap<ItemInfo, View>();
            for (int j = 0; j < layout.getChildCount(); j++) {
                final View view = layout.getChildAt(j);
                children.put((ItemInfo) view.getTag(), view);
            }

            final ArrayList<View> childrenToRemove = new ArrayList<View>();
            final HashMap<FolderInfo, ArrayList<ShortcutInfo>> folderAppsToRemove =
                    new HashMap<FolderInfo, ArrayList<ShortcutInfo>>();
            LauncherModel.ItemInfoFilter filter = new LauncherModel.ItemInfoFilter() {
                @Override
                public boolean filterItem(ItemInfo parent, ItemInfo info,
                                          ComponentName cn) {
                    if (parent instanceof FolderInfo) {
                        if (componentNames.contains(cn) && info.user.equals(user)) {
                            FolderInfo folder = (FolderInfo) parent;
                            ArrayList<ShortcutInfo> appsToRemove;
                            if (folderAppsToRemove.containsKey(folder)) {
                                appsToRemove = folderAppsToRemove.get(folder);
                            } else {
                                appsToRemove = new ArrayList<ShortcutInfo>();
                                folderAppsToRemove.put(folder, appsToRemove);
                            }
                            appsToRemove.add((ShortcutInfo) info);
                            return true;
                        }
                    }
                    return false;
                }
            };
            LauncherModel.filterItemInfos(children.keySet(), filter);

            // Remove all the apps from their folders
            for (FolderInfo folder : folderAppsToRemove.keySet()) {
                ArrayList<ShortcutInfo> appsToRemove = folderAppsToRemove.get(folder);
                for (ShortcutInfo info : appsToRemove) {
                    deleteItemInDatabase(info);
                    folder.remove(info);
                }
            }
        }
    }
    //M:[OP09][CF] }@
}
