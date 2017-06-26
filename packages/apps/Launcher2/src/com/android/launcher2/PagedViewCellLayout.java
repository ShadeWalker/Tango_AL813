/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.launcher2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;

import com.android.launcher.R;
import com.android.launcher2.CellLayout.CellInfo;

import com.mediatek.launcher2.ext.LauncherLog;

import java.util.HashMap;
import java.util.Stack;

/**
 * An abstraction of the original CellLayout which supports laying out items
 * which span multiple cells into a grid-like layout.  Also supports dimming
 * to give a preview of its contents.
 */
public class PagedViewCellLayout extends ViewGroup implements Page {
    static final String TAG = "PagedViewCellLayout";

    private int mCellCountX;
    private int mCellCountY;
    private int mOriginalCellWidth;
    private int mOriginalCellHeight;
    private int mCellWidth;
    private int mCellHeight;
    private int mOriginalWidthGap;
    private int mOriginalHeightGap;
    private int mWidthGap;
    private int mHeightGap;
    private int mMaxGap;
    protected PagedViewCellLayoutChildren mChildren;

    /// M: Record the original padding.
    private int mOriginalPaddingLeft;
    private int mOriginalPaddingRight;

    /// M: add for OP09.@{

    private Drawable mCrosshairsDrawable;

    private DropTarget.DragEnforcer mDragEnforcer;

    private boolean mDragging = false;

    // When a drag operation is in progress, holds the nearest cell to the touch point
    private final int[] mDragCell = new int[2];

    private final CellInfo mCellInfo = new CellInfo();

    private final Rect mRect = new Rect();

    private final int[] mTmpXY = new int[2];

    private HashMap<PagedViewCellLayout.LayoutParams, Animator> mReorderAnimators = new
            HashMap<PagedViewCellLayout.LayoutParams, Animator>();

    private boolean[][] mOccupied;
    private boolean[][] mTmpOccupied;

    private final int[] mTmpPoint = new int[2];
    private final Stack<Rect> mTempRectStack = new Stack<Rect>();

    /// M: add for OP09.}@

    public PagedViewCellLayout(Context context) {
        this(context, null);
    }

    public PagedViewCellLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagedViewCellLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setAlwaysDrawnWithCacheEnabled(false);

        // setup default cell parameters
        Resources resources = context.getResources();
        mOriginalCellWidth = mCellWidth =
            resources.getDimensionPixelSize(R.dimen.apps_customize_cell_width);
        mOriginalCellHeight = mCellHeight =
            resources.getDimensionPixelSize(R.dimen.apps_customize_cell_height);

        /// M: Record the original paddings.
        mOriginalPaddingLeft = mPaddingLeft;
        mOriginalPaddingRight = mPaddingRight;

        mCellCountX = LauncherModel.getCellCountX();
        mCellCountY = LauncherModel.getCellCountY();
        mOriginalWidthGap = mOriginalHeightGap = mWidthGap = mHeightGap = -1;
        mMaxGap = resources.getDimensionPixelSize(R.dimen.apps_customize_max_gap);

        mChildren = new PagedViewCellLayoutChildren(context);
        mChildren.setCellDimensions(mCellWidth, mCellHeight);
        mChildren.setGap(mWidthGap, mHeightGap);

        addView(mChildren);

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "Constructor: mCellCountX = " + mCellCountX + ", mCellCountY = "
                    + mCellCountY + ",mMaxGap = " + mMaxGap + ", this = " + this);
        }

        /// M: enlarge the max gap to make app list can be divided to 4 cells with same size.
        if (LauncherExtPlugin.getInstance().getOperatorCheckerExt(context).supportEditAndHideApps()) {
            mMaxGap *= 2;
        }

        mOccupied = new boolean[mCellCountX][mCellCountY];
        mTmpOccupied = new boolean[mCellCountX][mCellCountY];
        mCrosshairsDrawable = resources.getDrawable(R.drawable.ic_launcher_sort);

        mDragEnforcer = new DropTarget.DragEnforcer(context);
    }

    public int getCellWidth() {
        return mCellWidth;
    }

    public int getCellHeight() {
        return mCellHeight;
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();

        // Cancel long press for all children
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            child.cancelLongPress();
        }
    }

    public boolean addViewToCellLayout(View child, int index, int childId,
            PagedViewCellLayout.LayoutParams params) {
        final PagedViewCellLayout.LayoutParams lp = params;
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "addViewToCellLayout: index = " + index + ", child = "
                    + child.getTag() + ", this = " + this);
        }
        // Generate an id for each view, this assumes we have at most 256x256 cells
        // per workspace screen
        if (lp.cellX >= 0 && lp.cellX <= (mCellCountX - 1) &&
                lp.cellY >= 0 && (lp.cellY <= mCellCountY - 1)) {
            // If the horizontal or vertical span is set to -1, it is taken to
            // mean that it spans the extent of the CellLayout
            if (lp.cellHSpan < 0) lp.cellHSpan = mCellCountX;
            if (lp.cellVSpan < 0) lp.cellVSpan = mCellCountY;

            child.setId(childId);
            mChildren.addView(child, index, lp);

            /// M: mark the position as occupied, add for OP09.
            markCellsAsOccupiedForView(child);
            return true;
        }
        return false;
    }

    @Override
    public void removeAllViewsOnPage() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "removeAllViewsOnPage: mChildren = " + mChildren + ", this = " + this);
        }

        /// M: clear all occupied information, add for OP09.
        clearOccupiedCells();
        mChildren.removeAllViews();
        setLayerType(LAYER_TYPE_NONE, null);
    }

    @Override
    public void removeViewOnPageAt(int index) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "removeViewOnPageAt: mChildren = " + mChildren + ", index = " + index);
        }

        /// M: mark the position as vacant cell, add for OP09.
        markCellsAsUnoccupiedForView(mChildren.getChildAt(index));
        mChildren.removeViewAt(index);
    }

    /**
     * Clears all the key listeners for the individual icons.
     */
    public void resetChildrenOnKeyListeners() {
        int childCount = mChildren.getChildCount();
        for (int j = 0; j < childCount; ++j) {
            mChildren.getChildAt(j).setOnKeyListener(null);
        }
    }

    @Override
    public int getPageChildCount() {
        return mChildren.getChildCount();
    }

    public PagedViewCellLayoutChildren getChildrenLayout() {
        return mChildren;
    }

    @Override
    public View getChildOnPageAt(int i) {
        return mChildren.getChildAt(i);
    }

    @Override
    public int indexOfChildOnPage(View v) {
        return mChildren.indexOfChild(v);
    }

    public int getCellCountX() {
        return mCellCountX;
    }

    public int getCellCountY() {
        return mCellCountY;
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);

        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSpecSize =  MeasureSpec.getSize(heightMeasureSpec);

        if (widthSpecMode == MeasureSpec.UNSPECIFIED || heightSpecMode == MeasureSpec.UNSPECIFIED) {
            throw new RuntimeException("CellLayout cannot have UNSPECIFIED dimensions");
        }

        /// M: Restore the origin padding before measure process really processed
        /// because we have increased the padding by gap to fix NE issue.
        mPaddingLeft = mOriginalPaddingLeft;
        mPaddingRight = mOriginalPaddingRight;

        int numWidthGaps = mCellCountX - 1;
        int numHeightGaps = mCellCountY - 1;

        //if (mOriginalWidthGap < 0 || mOriginalHeightGap < 0) {
            int hSpace = widthSpecSize - getPaddingLeft() - getPaddingRight();
            int vSpace = heightSpecSize - getPaddingTop() - getPaddingBottom();
            int hFreeSpace = hSpace - (mCellCountX * mOriginalCellWidth);
            int vFreeSpace = vSpace - (mCellCountY * mOriginalCellHeight);
            mWidthGap = Math.min(mMaxGap, numWidthGaps > 0 ? (hFreeSpace / numWidthGaps) : 0);
            mHeightGap = Math.min(mMaxGap,numHeightGaps > 0 ? (vFreeSpace / numHeightGaps) : 0);
            if (LauncherLog.DEBUG_LAYOUT) {
                LauncherLog.d(TAG, "onMeasure 0: mMaxGap = " + mMaxGap + ", numWidthGaps = "
                        + numWidthGaps + ", hFreeSpace = " + hFreeSpace + ", mOriginalCellWidth ="
                        + mOriginalCellWidth + ", mOriginalCellHeight = " + mOriginalCellHeight
                        + ", mWidthGap = " + mWidthGap);
            }

            mChildren.setGap(mWidthGap, mHeightGap);
//        } else {
//            mWidthGap = mOriginalWidthGap;
//            mHeightGap = mOriginalHeightGap;
//        }

        // Initial values correspond to widthSpecMode == MeasureSpec.EXACTLY
        int newWidth = widthSpecSize;
        int newHeight = heightSpecSize;
        if (LauncherLog.DEBUG_LAYOUT) {
            LauncherLog.d(TAG, "onMeasure 1: newWidth = " + newWidth + ", newHeight = " + newHeight
                    + ", widthSpecMode = " + widthSpecMode + ",mPaddingLeft = " + mPaddingLeft
                    + ", mPaddingRight = " + mPaddingRight + ",mCellCountX = " + mCellCountX
                    + ", mCellWidth = " + mCellWidth + ", mWidthGap = " + mWidthGap
                    + ", mOriginalWidthGap =" + mOriginalWidthGap + ", mOriginalHeightGap = "
                    + mOriginalHeightGap + ", mOriginalCellWidth =" + mOriginalCellWidth
                    + ", mOriginalCellHeight = " + mOriginalCellHeight + ", this = " + this);
        }

        if (widthSpecMode == MeasureSpec.AT_MOST) {
            newWidth = getPaddingLeft() + getPaddingRight() + (mCellCountX * mCellWidth) +
                ((mCellCountX - 1) * mWidthGap);
            newHeight = getPaddingTop() + getPaddingBottom() + (mCellCountY * mCellHeight) +
                ((mCellCountY - 1) * mHeightGap);
            if (LauncherLog.DEBUG_LAYOUT) {
                LauncherLog.d(TAG, "onMeasure 2: newWidth = " + newWidth + ", newHeight = "
                        + newHeight + ", this = " + this);
            }

            /*
             * M: We use the remain space as left padding and right padding to make
             * the PagedViewCellLayout has the same width with the parent given
             * size, so the getVisiblePages in PagedView will not return the
             * wrong result.
             */
            if (newWidth != widthSpecSize) {
                final int halfGap = (widthSpecSize - newWidth) >> 1;
                mPaddingLeft += halfGap;
                mPaddingRight += (widthSpecSize - newWidth - halfGap);
                newWidth = widthSpecSize;
            }
            setMeasuredDimension(newWidth, newHeight);
        }

        final int count = getChildCount();
        /*
         * If user switch two tabs quickly, measure process will be delayed, the
         * newWidth(newHeight) may be 0, after minus the padding, the
         * measure width passed to child may be a negative value. When adding to
         * measureMode to get MeasureSpec, the measure mode could be changed.
         * Using 0 as the measureWidth if this happens to keep measure mode right.
         */
        final int childMeasureWidth = Math.max(0, newWidth - mPaddingLeft - mPaddingRight);
        final int childMeasureHeight = Math.max(0, newHeight - mPaddingTop - mPaddingBottom);

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(childMeasureWidth, MeasureSpec.EXACTLY);
            int childheightMeasureSpec = MeasureSpec.makeMeasureSpec(childMeasureHeight, MeasureSpec.EXACTLY);
            child.measure(childWidthMeasureSpec, childheightMeasureSpec);
        }
        if (LauncherLog.DEBUG_LAYOUT) {
            LauncherLog.d(TAG, "onMeasure 4: newWidth = " + newWidth + ", newHeight = " + newHeight
                    + ", this = " + this);
        }

        setMeasuredDimension(newWidth, newHeight);
    }

    int getContentWidth() {
        return getWidthBeforeFirstLayout() + getPaddingLeft() + getPaddingRight();
    }

    int getContentHeight() {
        if (mCellCountY > 0) {
            return mCellCountY * mCellHeight + (mCellCountY - 1) * Math.max(0, mHeightGap);
        }
        return 0;
    }

    int getWidthBeforeFirstLayout() {
        if (mCellCountX > 0) {
            return mCellCountX * mCellWidth + (mCellCountX - 1) * Math.max(0, mWidthGap);
        }
        return 0;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            child.layout(getPaddingLeft(), getPaddingTop(),
                r - l - getPaddingRight(), b - t - getPaddingBottom());
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean result = super.onTouchEvent(event);
        int count = getPageChildCount();
        if (count > 0) {
            // We only intercept the touch if we are tapping in empty space after the final row
            View child = getChildOnPageAt(count - 1);
            int bottom = child.getBottom();
            int numRows = (int) Math.ceil((float) getPageChildCount() / getCellCountX());
            if (numRows < getCellCountY()) {
                // Add a little bit of buffer if there is room for another row
                bottom += mCellHeight / 2;
            }
            result = result || (event.getY() < bottom);
        }
        return result;
    }

    public void enableCenteredContent(boolean enabled) {
        mChildren.enableCenteredContent(enabled);
    }

    @Override
    protected void setChildrenDrawingCacheEnabled(boolean enabled) {
        mChildren.setChildrenDrawingCacheEnabled(enabled);
    }

    public void setCellCount(int xCount, int yCount) {
        if (LauncherLog.DEBUG_LAYOUT) {
            LauncherLog.d(TAG, "setCellCount xCount = " + yCount + ", mCellCountX = " + mCellCountX
                    + ", mCellCountY = " + mCellCountY + ", this = " + this, new Throwable(
                    "setCellCount"));
        }
        mCellCountX = xCount;
        mCellCountY = yCount;
        mOccupied = new boolean[mCellCountX][mCellCountY];
        mTmpOccupied = new boolean[mCellCountX][mCellCountY];
        requestLayout();
    }

    public void setGap(int widthGap, int heightGap) {
        mOriginalWidthGap = mWidthGap = widthGap;
        mOriginalHeightGap = mHeightGap = heightGap;
        mChildren.setGap(widthGap, heightGap);
    }

    public int[] getCellCountForDimensions(int width, int height) {
        // Always assume we're working with the smallest span to make sure we
        // reserve enough space in both orientations
        int smallerSize = Math.min(mCellWidth, mCellHeight);

        // Always round up to next largest cell
        int spanX = (width + smallerSize) / smallerSize;
        int spanY = (height + smallerSize) / smallerSize;

        if (LauncherLog.DEBUG_LAYOUT) {
            LauncherLog.d(TAG, "getCellCountForDimensions width = " + width + ", height =" + height + ", spanX = " + spanX
                    + ", spanY = " + spanY + ", this = " + this);
        }
        return new int[] { spanX, spanY };
    }

    /**
     * Start dragging the specified child
     *
     * @param child The child that is being dragged
     */
    void onDragChild(View child) {
        PagedViewCellLayout.LayoutParams lp = (PagedViewCellLayout.LayoutParams) child.getLayoutParams();
        lp.isDragging = true;
    }

    /**
     * Estimates the number of cells that the specified width would take up.
     */
    public int estimateCellHSpan(int width) {
        // We don't show the next/previous pages any more, so we use the full width, minus the
        // padding
        int availWidth = width - (getPaddingLeft() + getPaddingRight());

        // We know that we have to fit N cells with N-1 width gaps, so we just juggle to solve for N
        int n = Math.max(1, (availWidth + mWidthGap) / (mCellWidth + mWidthGap));

        if (LauncherLog.DEBUG_LAYOUT) {
            LauncherLog.d(TAG, "estimateCellHSpan width = " + width
                    + ", availWidth = " + availWidth + ", n = " + n + ", this = " + this);
        }

        // We don't do anything fancy to determine if we squeeze another row in.
        return n;
    }

    /**
     * Estimates the number of cells that the specified height would take up.
     */
    public int estimateCellVSpan(int height) {
        // The space for a page is the height - top padding (current page) - bottom padding (current
        // page)
        int availHeight = height - (getPaddingTop() + getPaddingBottom());

        // We know that we have to fit N cells with N-1 height gaps, so we juggle to solve for N
        int n = Math.max(1, (availHeight + mHeightGap) / (mCellHeight + mHeightGap));

        if (LauncherLog.DEBUG_LAYOUT) {
            LauncherLog.d(TAG, "estimateCellVSpan width = " + height
                    + ", availHeight = " + availHeight + ", n = " + n + ", this = " + this);
        }
        // We don't do anything fancy to determine if we squeeze another row in.
        return n;
    }

    /** Returns an estimated center position of the cell at the specified index */
    public int[] estimateCellPosition(int x, int y) {
        int[] result = new int[] {
                mPaddingLeft + (x * mCellWidth) + (x * mWidthGap) + (mCellWidth / 2),
                mPaddingTop + (y * mCellHeight) + (y * mHeightGap) + (mCellHeight / 2)
        };
        if (LauncherLog.DEBUG_LAYOUT) {
            LauncherLog.d(TAG, "estimateCellPosition x = " + x + ", y = " + y
                    + ", result[0] = " + result[0] + ", result[1] = " + result[1] + ", this = " + this);
        }
        return result;
    }

    public void calculateCellCount(int width, int height, int maxCellCountX, int maxCellCountY) {
        mCellCountX = Math.min(maxCellCountX, estimateCellHSpan(width));
        mCellCountY = Math.min(maxCellCountY, estimateCellVSpan(height));
        if (LauncherLog.DEBUG_LAYOUT) {
            LauncherLog.d(TAG, "calculateCellCount width = " + width
                    + ", height = " + height + ", maxCellCountX = " + maxCellCountX
                    + ", maxCellCountY = " + maxCellCountY + ", mCellCountX = " + mCellCountX
                    + ", mCellCountY = " + mCellCountY + ", this = " + this);
        }
        requestLayout();
    }

    /**
     * Estimates the width that the number of hSpan cells will take up.
     */
    public int estimateCellWidth(int hSpan) {
        if (LauncherLog.DEBUG_LAYOUT) {
            LauncherLog.d(TAG, "estimeateCellWidth hSpan = " + hSpan
                    + ", mCellWidth = " + mCellWidth + ", this = " + this);
        }
        // TODO: we need to take widthGap into effect
        return hSpan * mCellWidth;
    }

    /**
     * Estimates the height that the number of vSpan cells will take up.
     */
    public int estimateCellHeight(int vSpan) {
        if (LauncherLog.DEBUG_LAYOUT) {
            LauncherLog.d(TAG, "estimateCellHeight sSpan = " + vSpan
                    + ", mCellHeight = " + mCellHeight + ", this = " + this);
        }
        // TODO: we need to take heightGap into effect
        return vSpan * mCellHeight;
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new PagedViewCellLayout.LayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof PagedViewCellLayout.LayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new PagedViewCellLayout.LayoutParams(p);
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);

        /// M: Record the origin padding and restore it before measure processed.
        mOriginalPaddingLeft = mPaddingLeft;
        mOriginalPaddingRight = mPaddingRight;
    }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        /**
         * Horizontal location of the item in the grid.
         */
        @ViewDebug.ExportedProperty
        public int cellX;

        /**
         * Vertical location of the item in the grid.
         */
        @ViewDebug.ExportedProperty
        public int cellY;

        /**
         * Number of cells spanned horizontally by the item.
         */
        @ViewDebug.ExportedProperty
        public int cellHSpan;

        /**
         * Number of cells spanned vertically by the item.
         */
        @ViewDebug.ExportedProperty
        public int cellVSpan;

        /**
         * Is this item currently being dragged
         */
        public boolean isDragging;

        // a data object that you can bind to this layout params
        private Object mTag;

        // X coordinate of the view in the layout.
        @ViewDebug.ExportedProperty
        int x;
        // Y coordinate of the view in the layout.
        @ViewDebug.ExportedProperty
        int y;


        /// M: Add for op09 start. @{

        /**
         * Temporary horizontal location of the item in the grid during reorder
         */
        public int tmpCellX;

        /**
         * Temporary vertical location of the item in the grid during reorder
         */
        public int tmpCellY;

        /**
         * Indicates whether the item will set its x, y, width and height parameters freely,
         * or whether these will be computed based on cellX, cellY, cellHSpan and cellVSpan.
         */
        public boolean isLockedToGrid = true;
        boolean dropped;

        /// M: Add for op09 end. }@

        public LayoutParams() {
            super(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            cellHSpan = 1;
            cellVSpan = 1;
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            cellHSpan = 1;
            cellVSpan = 1;
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
            cellHSpan = 1;
            cellVSpan = 1;
        }

        public LayoutParams(LayoutParams source) {
            super(source);
            this.cellX = source.cellX;
            this.cellY = source.cellY;
            this.cellHSpan = source.cellHSpan;
            this.cellVSpan = source.cellVSpan;
        }

        public LayoutParams(int cellX, int cellY, int cellHSpan, int cellVSpan) {
            super(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            this.cellX = cellX;
            this.cellY = cellY;
            this.cellHSpan = cellHSpan;
            this.cellVSpan = cellVSpan;
        }

        public void setup(int cellWidth, int cellHeight, int widthGap, int heightGap,
                int hStartPadding, int vStartPadding) {

            if (isLockedToGrid) {
                final int myCellHSpan = cellHSpan;
                final int myCellVSpan = cellVSpan;
                final int myCellX = cellX;
                final int myCellY = cellY;

                width = myCellHSpan * cellWidth + ((myCellHSpan - 1) * widthGap) - leftMargin
                        - rightMargin;
                height = myCellVSpan * cellHeight + ((myCellVSpan - 1) * heightGap) - topMargin
                        - bottomMargin;

                if (LauncherApplication.isScreenLarge()) {
                    x = hStartPadding + myCellX * (cellWidth + widthGap) + leftMargin;
                    y = vStartPadding + myCellY * (cellHeight + heightGap) + topMargin;
                } else {
                    x = myCellX * (cellWidth + widthGap) + leftMargin;
                    y = myCellY * (cellHeight + heightGap) + topMargin;
                }
            }
        }

        public Object getTag() {
            return mTag;
        }

        public void setTag(Object tag) {
            mTag = tag;
        }

        public String toString() {
            return "(" + this.cellX + ", " + this.cellY + ", " +
                this.cellHSpan + ", " + this.cellVSpan + ")";
        }
    }

    /// M: Add for op09 start. @{

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!Launcher.isInEditMode()) {
            return super.onInterceptTouchEvent(ev);
        }
        // First we clear the tag to ensure that on every touch down we start
        // with a fresh state, even in the case where we return early. Not
        // clearing here was causing bugs whereby on long-press we'd end up
        // picking up an item from a previous drag operation.
        final int action = ev.getAction();
        if (LauncherLog.DEBUG_MOTION) {
            LauncherLog.d(TAG, "onInterceptTouchEvent: action = " + action);
        }

        // Set set the cell info of the touch position as tag of cell layout.
        if (action == MotionEvent.ACTION_DOWN) {
            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "onInterceptTouchEvent: mCellInfo = " + mCellInfo);
            }
            clearTagCellInfo();
            setTagToCellInfoForPoint((int) ev.getX(), (int) ev.getY());
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (Launcher.isInEditMode()) {
            /// Draw cross drawable when in edit mode.
            final int countX = mCellCountX;
            final int countY = mCellCountY;

            final Drawable d = mCrosshairsDrawable;
            final int crossWidth = d.getIntrinsicWidth();
            final int crossHeight = d.getIntrinsicHeight();
            final int measuredWidth = getMeasuredWidth();
            final int measuredHeight = getMeasuredHeight();
            final int colGap = (measuredWidth - crossWidth) / mCellCountX;
            final int rowGap = (measuredHeight - crossHeight) / mCellCountY;

            int x = 0;
            for (int col = 0; col <= countX; col++) {
                int y = 0;
                for (int row = 0; row <= countY; row++) {
                    d.setBounds(x, y, x + crossWidth, y + crossHeight);
                    d.draw(canvas);
                    y += rowGap;
                }
                x += colGap;
            }
        }
        super.dispatchDraw(canvas);
    }

    /**
     * M: get child at the given position.
     *
     * @param x
     * @param y
     * @return
     */
    public View getChildAt(final int x, final int y) {
        return mChildren.getChildAt(x, y);
    }

    /**
     * M: remove child view, clear occupied cell info and remove it in child.
     *
     * @param child
     */
    public void removeChildView(final View child) {
        markCellsAsUnoccupiedForView(child);
        mChildren.removeView(child);
    }

    /**
     * M: whether the given cell exceed the last position.
     *
     * @param cell
     * @return
     */
    public boolean isExceedLastPosition(final int[] cell) {
        final int childCount = mChildren.getChildCount();
        int pos = cell[1] * mCellCountX + cell[0];
        return pos > childCount;
    }

    /**
     * M: get the indexes of last cell of the current layout.
     *
     * @param cell
     * @return
     */
    public int[] getLastPosition(final int[] cell) {
        int childCount = mChildren.getChildCount();

        if (childCount == mCellCountX * mCellCountY) {
            childCount--;
        }

        cell[0] = childCount % mCellCountX;
        cell[1] = childCount / mCellCountX;

        return cell;
    }

    /**
     * A drag event has begun over this layout. It may have begun over this
     * layout (in which case onDragChild is called first), or it may have begun
     * on another layout, merge from CellLayout.
     */
    void onDragEnter() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onDragEnter: mDragging = " + mDragging + ", this = " + this);
        }

        mDragEnforcer.onDragEnter();
        mDragging = true;
    }

    /**
     * Called when drag has left this CellLayout or has been completed
     * (successfully or not), merge from CellLayout.
     */
    void onDragExit() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onDragExit: mDragging = " + mDragging + ", this = " + this);
        }

        // This can actually be called when we aren't in a drag, e.g. when
        // adding a new item to this layout via the customize drawer.
        // Guard against that case.
        mDragEnforcer.onDragExit();
        mDragging = false;

        // Invalidate the drag data
        mDragCell[0] = -1;
        mDragCell[1] = -1;
    }

    /**
     * Mark a child as having been dropped. At the beginning of the drag
     * operation, the child may have been on another screen, but it is
     * re-parented before this method is called, merge from CellLayout.
     *
     * @param child The child that is being dropped
     */
    void onDropChild(final View child) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onDropChild: child = " + child + ", this = " + this);
        }

        if (child != null) {
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            lp.dropped = true;
            child.requestLayout();
        }
    }

    /**
     * M: animate child to the given position, merge from CellLayout.
     *
     * @param child
     * @param cellX
     * @param cellY
     * @param duration
     * @param delay
     * @param permanent
     * @param adjustOccupied
     * @return
     */
    public boolean animateChildToPosition(final View child, int cellX, int cellY, int duration,
            int delay, boolean permanent, boolean adjustOccupied) {
        PagedViewCellLayoutChildren clc = getChildrenLayout();
        boolean[][] occupied = mOccupied;
        if (!permanent) {
            occupied = mTmpOccupied;
        }

        if (clc.indexOfChild(child) != -1) {
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            final ApplicationInfo info = (ApplicationInfo) child.getTag();

            // We cancel any existing animations
            if (mReorderAnimators.containsKey(lp)) {
                mReorderAnimators.get(lp).cancel();
                mReorderAnimators.remove(lp);
            }

            final int oldX = lp.x;
            final int oldY = lp.y;
            if (adjustOccupied) {
                occupied[lp.cellX][lp.cellY] = false;
                occupied[cellX][cellY] = true;
            }
            lp.isLockedToGrid = true;
            if (permanent) {
                lp.cellX = cellX;
                info.cellX = cellX;

                lp.cellY = cellY;
                info.cellY = cellY;
                info.pos = cellY * mCellCountX + cellX;
            } else {
                lp.tmpCellX = cellX;
                lp.tmpCellY = cellY;
            }
            clc.setupLp(lp);
            lp.isLockedToGrid = false;
            final int newX = lp.x;
            final int newY = lp.y;

            lp.x = oldX;
            lp.y = oldY;

            // Exit early if we're not actually moving the view
            if (oldX == newX && oldY == newY) {
                lp.isLockedToGrid = true;
                return true;
            }

            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(duration);
            mReorderAnimators.put(lp, va);

            va.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float r = ((Float) animation.getAnimatedValue()).floatValue();
                    lp.x = (int) ((1 - r) * oldX + r * newX);
                    lp.y = (int) ((1 - r) * oldY + r * newY);
                    child.requestLayout();
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                boolean cancelled = false;
                public void onAnimationEnd(Animator animation) {
                    // If the animation was cancelled, it means that another
                    // animation has interrupted this one, and we don't want to
                    // lock the item into place just yet.
                    if (!cancelled) {
                        lp.isLockedToGrid = true;
                        child.requestLayout();
                    }
                    if (mReorderAnimators.containsKey(lp)) {
                        mReorderAnimators.remove(lp);
                    }
                }

                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }
            });
            va.setStartDelay(delay);
            va.start();
            return true;
        }
        return false;
    }

    /**
     * M: clear occupied array, mark all cell as vacant cell, add for OP09.
     */
    private void clearOccupiedCells() {
        for (int x = 0; x < mCellCountX; x++) {
            for (int y = 0; y < mCellCountY; y++) {
                mOccupied[x][y] = false;
            }
        }
    }

    /**
     * M: mark the cell of the view position as occupied.
     *
     * @param view
     */
    public void markCellsAsOccupiedForView(View view) {
        markCellsAsOccupiedForView(view, mOccupied);
    }

    /**
     * M: mark the cell of the view position as occupied.
     *
     * @param view
     * @param occupied
     */
    public void markCellsAsOccupiedForView(View view, boolean[][] occupied) {
        if (view == null || view.getParent() != mChildren) {
            return;
        }
        LayoutParams lp = (LayoutParams) view.getLayoutParams();
        markCellsForView(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan, occupied, true);
    }

    /**
     * M: mark the cell of the view position as vacant.
     *
     * @param view
     */
    public void markCellsAsUnoccupiedForView(View view) {
        markCellsAsUnoccupiedForView(view, mOccupied);
    }

    /**
     * M: mark the cell of the view position as vacant.
     *
     * @param view
     * @param occupied
     */
    public void markCellsAsUnoccupiedForView(View view, boolean occupied[][]) {
        if (view == null || view.getParent() != mChildren) {
            return;
        }
        LayoutParams lp = (LayoutParams) view.getLayoutParams();
        markCellsForView(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan, occupied, false);
    }

    /**
     * M: mark the cell of the specified position as vacant or occupied.
     *
     * @param cellX
     * @param cellY
     * @param spanX
     * @param spanY
     * @param occupied
     * @param value True if occupied, false vacant.
     */
    private void markCellsForView(int cellX, int cellY, int spanX, int spanY, boolean[][] occupied,
            boolean value) {
        if (cellX < 0 || cellY < 0) {
            return;
        }
        for (int x = cellX; x < cellX + spanX && x < mCellCountX; x++) {
            for (int y = cellY; y < cellY + spanY && y < mCellCountY; y++) {
                occupied[x][y] = value;
            }
        }
    }

    // This class stores info for two purposes:
    // 1. When dragging items (mDragInfo in Workspace), we store the View, its cellX & cellY,
    //    its spanX, spanY, and the screen it is on.
    // 2. When long clicking on an empty cell in a CellLayout, we save information about the
    //    cellX and cellY coordinates and which page was clicked. We then set this as a tag on
    //    the CellLayout that was long clicked.
    static final class CellInfo {
        View cell;
        int cellX = -1;
        int cellY = -1;
        int screen = -1;
        int pos = -1;

        @Override
        public String toString() {
            return "Cell[view=" + (cell == null ? "null" : cell.getClass()) + ", x=" + cellX
                    + ", y=" + cellY + ",screen = " + screen + ",pos = " + pos + "]";
        }
    }

    /**
     * M: lazy init temp rect stack, merge from CellLayout.
     */
    private void lazyInitTempRectStack() {
        if (mTempRectStack.isEmpty()) {
            for (int i = 0; i < mCellCountX * mCellCountY; i++) {
                mTempRectStack.push(new Rect());
            }
        }
    }

    /**
     * M: recycle used rects, merge from CellLayout.
     *
     * @param used
     */
    private void recycleTempRects(Stack<Rect> used) {
        while (!used.isEmpty()) {
            mTempRectStack.push(used.pop());
        }
    }

    /**
     * M: find a starting cell position that will fit the given bounds nearest the
     * requested cell location. Uses Euclidean distance to score multiple vacant
     * areas, merge from CellLayout.
     *
     * @param pixelX The X location at which you want to search for a vacant
     *            area.
     * @param pixelY The Y location at which you want to search for a vacant
     *            area.
     * @param spanX Horizontal span of the object.
     * @param spanY Vertical span of the object.
     * @param ignoreView Considers space occupied by this view as unoccupied
     * @param result Previously returned value to possibly recycle.
     * @return The X, Y cell of a vacant area that can contain this object,
     *         nearest the requested location.
     */
    int[] findNearestArea(int pixelX, int pixelY, int spanX, int spanY, int[] result) {
        return findNearestArea(pixelX, pixelY, spanX, spanY, null, false, result);
    }

    /**
     * Find a vacant area that will fit the given bounds nearest the requested
     * cell location. Uses Euclidean distance to score multiple vacant areas.
     *
     * @param pixelX The X location at which you want to search for a vacant
     *            area.
     * @param pixelY The Y location at which you want to search for a vacant
     *            area.
     * @param spanX Horizontal span of the object.
     * @param spanY Vertical span of the object.
     * @param ignoreOccupied If true, the result can be an occupied cell
     * @param result Array in which to place the result, or null (in which case
     *            a new array will be allocated)
     * @return The X, Y cell of a vacant area that can contain this object,
     *         nearest the requested location.
     */
    int[] findNearestArea(int pixelX, int pixelY, int spanX, int spanY, View ignoreView,
            boolean ignoreOccupied, int[] result) {
        return findNearestArea(pixelX, pixelY, spanX, spanY, spanX, spanY, ignoreView,
                ignoreOccupied, result, null, mOccupied);
    }

    /**
     * Find a vacant area that will fit the given bounds nearest the requested
     * cell location. Uses Euclidean distance to score multiple vacant areas.
     *
     * @param pixelX The X location at which you want to search for a vacant
     *            area.
     * @param pixelY The Y location at which you want to search for a vacant
     *            area.
     * @param minSpanX The minimum horizontal span required
     * @param minSpanY The minimum vertical span required
     * @param spanX Horizontal span of the object.
     * @param spanY Vertical span of the object.
     * @param ignoreOccupied If true, the result can be an occupied cell
     * @param result Array in which to place the result, or null (in which case
     *            a new array will be allocated)
     * @return The X, Y cell of a vacant area that can contain this object,
     *         nearest the requested location.
     */
    int[] findNearestArea(int pixelX, int pixelY, int minSpanX, int minSpanY, int spanX, int spanY,
            View ignoreView, boolean ignoreOccupied, int[] result, int[] resultSpan,
            boolean[][] occupied) {
        lazyInitTempRectStack();
        // mark space take by ignoreView as available.
        markCellsAsUnoccupiedForView(ignoreView, occupied);

        // For items with a spanX / spanY > 1, the passed in point (pixelX,
        // pixelY) corresponds to the center of the item, but we are searching
        // based on the top-left cell, so we translate the point over to
        // correspond to the top-left.
        pixelX -= (mCellWidth + mWidthGap) * (spanX - 1) / 2f;
        pixelY -= (mCellHeight + mHeightGap) * (spanY - 1) / 2f;

        // Keep track of best-scoring drop area
        final int[] bestXY = result != null ? result : new int[2];
        double bestDistance = Double.MAX_VALUE;
        final Rect bestRect = new Rect(-1, -1, -1, -1);
        final Stack<Rect> validRegions = new Stack<Rect>();

        final int countX = mCellCountX;
        final int countY = mCellCountY;

        if (minSpanX <= 0 || minSpanY <= 0 || spanX <= 0 || spanY <= 0 || spanX < minSpanX
                || spanY < minSpanY) {
            return bestXY;
        }

        for (int y = 0; y < countY - (minSpanY - 1); y++) {
            inner: for (int x = 0; x < countX - (minSpanX - 1); x++) {
                int ySize = -1;
                int xSize = -1;
                if (ignoreOccupied) {
                    // First, let's see if this thing fits anywhere
                    for (int i = 0; i < minSpanX; i++) {
                        for (int j = 0; j < minSpanY; j++) {
                            if (occupied[x + i][y + j]) {
                                continue inner;
                            }
                        }
                    }
                    xSize = minSpanX;
                    ySize = minSpanY;

                    // We know that the item will fit at _some_ acceptable size,
                    // now let's see
                    // how big we can make it. We'll alternate between
                    // incrementing x and y spans
                    // until we hit a limit.
                    boolean incX = true;
                    boolean hitMaxX = xSize >= spanX;
                    boolean hitMaxY = ySize >= spanY;
                    while (!(hitMaxX && hitMaxY)) {
                        if (incX && !hitMaxX) {
                            for (int j = 0; j < ySize; j++) {
                                if (x + xSize > countX - 1 || occupied[x + xSize][y + j]) {
                                    // We can't move out horizontally
                                    hitMaxX = true;
                                }
                            }
                            if (!hitMaxX) {
                                xSize++;
                            }
                        } else if (!hitMaxY) {
                            for (int i = 0; i < xSize; i++) {
                                if (y + ySize > countY - 1 || occupied[x + i][y + ySize]) {
                                    // We can't move out vertically
                                    hitMaxY = true;
                                }
                            }
                            if (!hitMaxY) {
                                ySize++;
                            }
                        }
                        hitMaxX |= xSize >= spanX;
                        hitMaxY |= ySize >= spanY;
                        incX = !incX;
                    }
                    incX = true;
                    hitMaxX = xSize >= spanX;
                    hitMaxY = ySize >= spanY;
                }
                final int[] cellXY = mTmpXY;
                cellToCenterPoint(x, y, cellXY);

                // We verify that the current rect is not a sub-rect of any of
                // our previous candidates. In this case, the current rect is
                // disqualified in favour of the containing rect.
                Rect currentRect = mTempRectStack.pop();
                currentRect.set(x, y, x + xSize, y + ySize);
                boolean contained = false;
                for (Rect r : validRegions) {
                    if (r.contains(currentRect)) {
                        contained = true;
                        break;
                    }
                }
                validRegions.push(currentRect);
                double distance = Math.sqrt(Math.pow(cellXY[0] - pixelX, 2)
                        + Math.pow(cellXY[1] - pixelY, 2));

                if ((distance <= bestDistance && !contained) || currentRect.contains(bestRect)) {
                    bestDistance = distance;
                    bestXY[0] = x;
                    bestXY[1] = y;
                    if (resultSpan != null) {
                        resultSpan[0] = xSize;
                        resultSpan[1] = ySize;
                    }
                    bestRect.set(currentRect);
                }
            }
        }
        // re-mark space taken by ignoreView as occupied
        markCellsAsOccupiedForView(ignoreView, occupied);

        // Return -1, -1 if no suitable location found
        if (bestDistance == Double.MAX_VALUE) {
            bestXY[0] = -1;
            bestXY[1] = -1;
        }
        recycleTempRects(validRegions);
        return bestXY;
    }

    /**
     * Find a vacant area that will fit the given bounds nearest the requested
     * cell location, and will also weigh in a suggested direction vector of the
     * desired location. This method computers distance based on unit grid
     * distances, not pixel distances.
     *
     * @param cellX The X cell nearest to which you want to search for a vacant
     *            area.
     * @param cellY The Y cell nearest which you want to search for a vacant
     *            area.
     * @param spanX Horizontal span of the object.
     * @param spanY Vertical span of the object.
     * @param direction The favored direction in which the views should move
     *            from x, y
     * @param exactDirectionOnly If this parameter is true, then only solutions
     *            where the direction matches exactly. Otherwise we find the
     *            best matching direction.
     * @param occoupied The array which represents which cells in the CellLayout
     *            are occupied
     * @param blockOccupied The array which represents which cells in the
     *            specified block (cellX, cellY, spanX, spanY) are occupied.
     *            This is used when try to move a group of views.
     * @param result Array in which to place the result, or null (in which case
     *            a new array will be allocated)
     * @return The X, Y cell of a vacant area that can contain this object,
     *         nearest the requested location.
     */
    private int[] findNearestArea(int cellX, int cellY, int spanX, int spanY, int[] direction,
            boolean[][] occupied, boolean blockOccupied[][], int[] result) {
        // Keep track of best-scoring drop area
        final int[] bestXY = result != null ? result : new int[2];
        float bestDistance = Float.MAX_VALUE;
        int bestDirectionScore = Integer.MIN_VALUE;

        final int countX = mCellCountX;
        final int countY = mCellCountY;

        for (int y = 0; y < countY - (spanY - 1); y++) {
            inner: for (int x = 0; x < countX - (spanX - 1); x++) {
                // First, let's see if this thing fits anywhere
                for (int i = 0; i < spanX; i++) {
                    for (int j = 0; j < spanY; j++) {
                        if (occupied[x + i][y + j]
                                && (blockOccupied == null || blockOccupied[i][j])) {
                            continue inner;
                        }
                    }
                }

                float distance = (float) Math.sqrt((x - cellX) * (x - cellX) + (y - cellY)
                        * (y - cellY));
                int[] curDirection = mTmpPoint;
                computeDirectionVector(x - cellX, y - cellY, curDirection);
                // The direction score is just the dot product of the two
                // candidate direction and that passed in.
                int curDirectionScore = direction[0] * curDirection[0] + direction[1]
                        * curDirection[1];
                boolean exactDirectionOnly = false;
                boolean directionMatches = direction[0] == curDirection[0]
                        && direction[0] == curDirection[0];
                if ((directionMatches || !exactDirectionOnly)
                        && Float.compare(distance, bestDistance) < 0
                        || (Float.compare(distance, bestDistance) == 0 && curDirectionScore > bestDirectionScore)) {
                    bestDistance = distance;
                    bestDirectionScore = curDirectionScore;
                    bestXY[0] = x;
                    bestXY[1] = y;
                }
            }
        }

        // Return -1, -1 if no suitable location found
        if (bestDistance == Float.MAX_VALUE) {
            bestXY[0] = -1;
            bestXY[1] = -1;
        }
        return bestXY;
    }

    /**
     * Returns a pair (x, y), where x,y are in {-1, 0, 1} corresponding to
     * vector between the provided point and the provided cell, merge from CellLayout.
     */
    private void computeDirectionVector(float deltaX, float deltaY, int[] result) {
        double angle = Math.atan(((float) deltaY) / deltaX);

        result[0] = 0;
        result[1] = 0;
        if (Math.abs(Math.cos(angle)) > 0.5f) {
            result[0] = (int) Math.signum(deltaX);
        }
        if (Math.abs(Math.sin(angle)) > 0.5f) {
            result[1] = (int) Math.signum(deltaY);
        }
    }

    /**
     * Given a point, return the cell that strictly encloses that point, merge
     * from CellLayout.
     *
     * @param x X coordinate of the point
     * @param y Y coordinate of the point
     * @param result Array of 2 ints to hold the x and y coordinate of the cell
     */
    void pointToCellExact(int x, int y, int[] result) {
        final int hStartPadding = getPaddingLeft();
        final int vStartPadding = getPaddingTop();

        result[0] = (x - hStartPadding) / (mCellWidth + mWidthGap);
        result[1] = (y - vStartPadding) / (mCellHeight + mHeightGap);

        final int xAxis = mCellCountX;
        final int yAxis = mCellCountY;

        if (result[0] < 0) {
            result[0] = 0;
        } else if (result[0] >= xAxis) {
            result[0] = xAxis - 1;
        }

        if (result[1] < 0) {
            result[1] = 0;
        } else if (result[1] >= yAxis) {
            result[1] = yAxis - 1;
        }
    }

    /**
     * Given a cell coordinate, return the point that represents the center of
     * the cell, merge from CellLayout.
     *
     * @param cellX X coordinate of the cell
     * @param cellY Y coordinate of the cell
     * @param result Array of 2 ints to hold the x and y coordinate of the point
     */
    void cellToCenterPoint(int cellX, int cellY, int[] result) {
        regionToCenterPoint(cellX, cellY, 1, 1, result);
    }

    /**
     * Given a cell coordinate and span return the point that represents the
     * center of the region, merge from CellLayout.
     *
     * @param cellX X coordinate of the cell
     * @param cellY Y coordinate of the cell
     * @param result Array of 2 ints to hold the x and y coordinate of the point
     */
    void regionToCenterPoint(int cellX, int cellY, int spanX, int spanY, int[] result) {
        final int hStartPadding = getPaddingLeft();
        final int vStartPadding = getPaddingTop();
        result[0] = hStartPadding + cellX * (mCellWidth + mWidthGap)
                + (spanX * mCellWidth + (spanX - 1) * mWidthGap) / 2;
        result[1] = vStartPadding + cellY * (mCellHeight + mHeightGap)
                + (spanY * mCellHeight + (spanY - 1) * mHeightGap) / 2;
    }

    /**
     * M: set the cell info of the given position as tag of the current cell
     * layout, merge from CellLayout.
     *
     * @param touchX
     * @param touchY
     */
    private void setTagToCellInfoForPoint(final int touchX, final int touchY) {
        final CellInfo cellInfo = mCellInfo;
        Rect frame = mRect;
        final int x = touchX + getScrollX();
        final int y = touchY + getScrollY();
        final int count = mChildren.getChildCount();

        boolean found = false;
        for (int i = count - 1; i >= 0; i--) {
            final View child = mChildren.getChildAt(i);
            final ApplicationInfo info = (ApplicationInfo) child.getTag();
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if ((child.getVisibility() == VISIBLE || child.getAnimation() != null)) {
                child.getHitRect(frame);

                float scale = child.getScaleX();
                frame = new Rect(child.getLeft(), child.getTop(), child.getRight(), child
                        .getBottom());
                // The child hit rect is relative to the CellLayoutChildren
                // parent, so we need to offset that by this CellLayout's
                // padding to test an (x,y) point that is relative to this view.
                frame.offset(getPaddingLeft(), getPaddingTop());
                frame.inset((int) (frame.width() * (1f - scale) / 2), (int) (frame.height()
                        * (1f - scale) / 2));

                if (frame.contains(x, y)) {
                    cellInfo.cell = child;
                    cellInfo.screen = info.screen;
                    cellInfo.cellX = lp.cellX;
                    cellInfo.cellY = lp.cellY;
                    cellInfo.pos = cellInfo.cellY * mCellCountX + cellInfo.cellX;
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            final int cellXY[] = mTmpXY;
            pointToCellExact(x, y, cellXY);

            cellInfo.cell = null;
            cellInfo.cellX = cellXY[0];
            cellInfo.cellY = cellXY[1];
            cellInfo.pos = cellInfo.cellX * mCellCountY + cellInfo.cellY;
        }
        setTag(cellInfo);
    }

    /**
     * M: reset cell info and set it as tag, merge from CellLayout.
     */
    private void clearTagCellInfo() {
        final CellInfo cellInfo = mCellInfo;
        cellInfo.cell = null;
        cellInfo.cellX = -1;
        cellInfo.cellY = -1;
        cellInfo.pos = -1;
        setTag(cellInfo);
    }

    /// M: Add for op09 end. }@
}

interface Page {
    public int getPageChildCount();
    public View getChildOnPageAt(int i);
    public void removeAllViewsOnPage();
    public void removeViewOnPageAt(int i);
    public int indexOfChildOnPage(View v);
}
