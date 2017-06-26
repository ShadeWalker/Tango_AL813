package com.android.systemui.floatpanel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.DragEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.android.systemui.R;

import com.mediatek.xlog.Xlog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * M: Add for Multi-window entrance panel drag icon.
 */
public class DragSortGridView extends GridView {

    private static final String TAG = "DragGridView";
    private static final boolean DEBUG_DRAG = true;
    private static final boolean DEBUG_LAYOUT = false;
    private static final int TRANSLATE_ANIM_DURATION = 120;

    private static final int VISUAL_SPACING_SLOT = 10;
	//The count of gridview child in three line
    private static final int COUNT_CHILD_SHOW_IN_TREE_LINE = 24;

    // The default scroll distance
    private static final int SMOOTH_SCROLL_AMOUNT_AT_EDGE = 200;
    // The default delay to start a scroll
    private static final int DELAY_DRAG_SCROLL = 300;
    // The threshold of trigger a scroll
    private static final int TOUCH_SLOT = 75;
    // The threashold of force stop edge flow
    private static final int EDGE_STOP_SCROLL_SLOT = 10;
    // The drag scroll state definition
    private static final int SCROLL_STATE_IDLE = 0;
    private static final int SCROLL_STATE_SCROLLING = 1;
    // The scroll direction
    private static final int SCROLL_DIRECTION_UP = 0;
    private static final int SCROLL_DIRECTION_DOWN = 1;

    private static final int DRAG_MODE_NONE = 0;
    private static final int DRAG_MODE_REORDER = 1;
    private static final int DRAG_MODE_SWITCH = 2;
    private static final int DRAG_MODE_SWITCH_REORDER = 3;
    private int mDragMode = DRAG_MODE_NONE;

    private boolean mMovingChildViews = false;
    private boolean mInternalDrag = false;
    private boolean mMoveInView = false;
    private boolean mDragFinished = false;

    // Whether there is a drag between two views, if there is a new drag in,
    // mWaitingForInsertedItem will be set to true, if there is a move out,
    // mWaitingForRemovedItem will be set to true.
    private boolean mWaitingForInsertedItem = false;
    private boolean mWaitingForRemovedItem = false;

    // Whether to support drag item in the grid.
    private boolean mDragEnabled = true;

    // Whether the reorder function is enabled for the grid.
    private boolean mReorderEnabled;

    // Whether it is a successful drag, with drop event means a successful drag.
    private boolean mDragSucceed;

    private PositionInfo mReorderingPositions;
    private int mLastDraggingPosition;
    private int mInsertedPos = -1;

    // [Temp Solution] It's better to move it to a controller to maintain these
    // two global views, use static varible for simple implementation.
    private static View sDragView = null;
    private static View sOriginDragView = null;

    private Animation mFadeOutAnimation;
    private int mAnimationCount = 0;
    private FloatAppAdapter mAdapter;
    private OnReorderingListener mOnReorderingListener;
    private int mFixedColumnNum;

    private boolean mInStrechMode;

    // Whether this GridView enabled vertical drag scroll
    private boolean mVerticalDragScrollEnable;
    // The edge drag scroll amount for each step
    private int mSmoothScrollAmountAtEdge;
    // When user touch and drag icon to edge and wait for large distance scroll,
    // use this weight to speed up scroll
    private float mDragWeight;
    private int mLastDragLocationY;
    private int mCurrentScrollState;
    private int mTouchSlot;
    // Whether we are in scrolling(SCROLL_STATE_SCROLLING) state or
    // idle(SCROLL_STATE_IDLE) state,
    private int mScrollerState;
    // The direction of current drag direction. 0 mean up, 1 mean down
    private int mDragDirection;
    // The scroll runnable, do edge drag scroll in UI thread using postdelay
    private ScrollRunnable mScrollRunnable;
    // When user drag at the edge, and finger is moving reversely, immediately
    // stop edge scroll
    private boolean mEdgeScrollForceStop;
    private Handler mHandler;
    // Indicate whether we are in dragging state, if yes, ScrollRunnable's run()
    // should check if
    // need continuous move
    private boolean mInDragState;
    // Save current gridview's current visible rect, used to compute width in
    // strech mode
    private Rect mVisibleRect;

    public DragSortGridView(Context context) {
        this(context, null);
    }

    public DragSortGridView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DragSortGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (mFadeOutAnimation == null) {
            mFadeOutAnimation = AnimationUtils.loadAnimation(getContext(),
                    R.anim.float_panel_item_fade_out);
        }

        setOnItemLongClickListener(mItemLongClickListener);
        setOnDragListener(mDragListener);
        mInStrechMode = false;
        mFixedColumnNum = context.getResources().getInteger(
                R.integer.float_panel_num_columns);

        final DisplayMetrics metric = getResources().getDisplayMetrics();
        mSmoothScrollAmountAtEdge = (int) (SMOOTH_SCROLL_AMOUNT_AT_EDGE
                * metric.density + 0.5f);
        mTouchSlot = (int) (TOUCH_SLOT * metric.density + 0.5f);
        mHandler = new Handler();
        mScrollerState = SCROLL_STATE_IDLE;
        mScrollRunnable = new ScrollRunnable();
        mInDragState = false;
        mVerticalDragScrollEnable = false;
        mVisibleRect = new Rect();
    }

    public void setDragEnabled(boolean enable) {
        mDragEnabled = enable;
    }

    public void setReorderEnabled(boolean enable) {
        mReorderEnabled = enable;
    }

    /**
     * In strech mode, DragSortGridView will set its layoutparam width to the
     * size of all item with plus padding. So that the grandparent
     * HorizontalScrollView can hold GridView all items in one single row
     */
    public void setEnableStrechMode(boolean enable) {
        mInStrechMode = true;
    }

    public void setEnableVerticalDragScroll(boolean enable) {
        mVerticalDragScrollEnable = enable;
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        super.setAdapter(adapter);
        mAdapter = (FloatAppAdapter) adapter;
        Xlog.d(TAG, "setAdapter: count = " + mAdapter.getCount()
                + ", mAdapter=" + mAdapter + ", this=" + this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        // If the GridView is nested in a horizontal scroll view, we measure it
        // in special, make the measured with to show all items plus 2 more
        // extras.
        if (widthMode == MeasureSpec.UNSPECIFIED && mInStrechMode
                && mAdapter != null) {
            final int itemCount = mAdapter.getCount();
            final int minWidth = getResources().getDimensionPixelSize(
                    R.dimen.float_bottom_min_width);
            final int requestColumnWidth = getResources()
                    .getDimensionPixelSize(R.dimen.gridview_column_width);

            int newHorizontalSpacing = getHorizontalSpacing();
            int spaceLeftOver = minWidth
                    - ((mFixedColumnNum+2) * requestColumnWidth);  ///* M: ALPS01457523 to enlarge the columns 2 plus to display */
            Xlog.d(TAG, "onMeasure 2: newHorizontalSpacing = "
                    + newHorizontalSpacing + ", spaceLeftOver = "
                    + spaceLeftOver + ",minWidth = " + minWidth
                    + ", requestColumnWidth = " + requestColumnWidth
                    + ",columnWidth = " + getColumnWidth());
            //if (spaceLeftOver > 0)  ///* M: ALPS01457523 to enlarge the columns 2 plus to display */
            {
                newHorizontalSpacing = spaceLeftOver / (mFixedColumnNum - 1);
                setHorizontalSpacing(newHorizontalSpacing);
            }

            int newWidth = getPaddingLeft() + getPaddingRight() + itemCount
                    * requestColumnWidth + getRequestedHorizontalSpacing()
                    * (itemCount - 1) + (int) (requestColumnWidth * 2)
                    + getVerticalScrollbarWidth();
            if (minWidth > newWidth) {
                newWidth = minWidth;
            }
            widthSize = newWidth;
            widthMode = MeasureSpec.EXACTLY;
            super.onMeasure(MeasureSpec.makeMeasureSpec(widthSize, widthMode),
                    heightMeasureSpec);

            Xlog.d(TAG, "onMeasure: measuredWidth = " + getMeasuredWidth()
                    + ", measuredHeight = " + getMeasuredHeight()
                    + ", itemCount = " + itemCount + ", newWidth = " + newWidth
                    + ",minWidth = " + minWidth + ",numColumns = "
                    + getNumColumns() + ",columnWidth = " + getColumnWidth()
                    + ",newHorizontalSpacing = " + getHorizontalSpacing()
                    + ", this = " + this);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            Xlog.d(TAG, "onMeasure else: measuredWidth = " + getMeasuredWidth()
                    + ", measuredHeight = " + getMeasuredHeight()
                    + ", itemCount = " + getCount()
                    + ", newHorizontalSpacing = " + getHorizontalSpacing()
                    + ",numColumns = " + getNumColumns() + ",columnWidth = "
                    + getColumnWidth() + ", this = " + this);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (DEBUG_LAYOUT) {
            Xlog.d(TAG, "onLayout: mWaitingForInsertedItem = "
                    + mWaitingForInsertedItem + ", mWaitingForRemovedItem = "
                    + mWaitingForRemovedItem + ", changed = " + changed
                    + ", mMoveInView = " + mMoveInView
                    + ",mLastDraggingPosition = " + mLastDraggingPosition
                    + ",sDragView = " + sDragView + ", this = " + this);
        }
        // Discard to perform layout if we are dragging icons.
        if (!mMoveInView || mWaitingForInsertedItem || mWaitingForRemovedItem) {
            super.onLayout(changed, l, t, r, b);
        }

        if (mWaitingForInsertedItem) {
            sOriginDragView = sDragView;
            sDragView = getView(mLastDraggingPosition);
            if(sDragView != null)
              Xlog.d(TAG, "onLayout: sDragView vis = " + sDragView.getVisibility());
            mWaitingForInsertedItem = false;
        } else if (mWaitingForRemovedItem) {
            sDragView = sOriginDragView;
            Xlog.d(TAG,
                    "onLayout: sDragView vis = " + sDragView.getVisibility()
                            + ", tag = " + sDragView.getTag());
            mWaitingForRemovedItem = false;
        }
    }

    private View.OnDragListener mDragListener = new View.OnDragListener() {

        @Override
        public boolean onDrag(View v, DragEvent event) {
            final int action = event.getAction();
            final int x = Math.round(event.getX());
            final int y = Math.round(event.getY());
            int pos;
            int newPosition;
            if (DEBUG_DRAG) {
                Xlog.d(TAG,
                        "onDrag event action:"
                                + DRAG_EVENT_ACTION.get(action)
                                + ",x = "
                                + x
                                + ",y = "
                                + y
                                + ",mLastDraggingPosition = "
                                + mLastDraggingPosition
                                + ", mWaitingForInsertedItem = "
                                + mWaitingForInsertedItem
                                + ", mWaitingForRemovedItem = "
                                + mWaitingForRemovedItem
                                + ", mInternalDrag = "
                                + mInternalDrag
                                + ", mMoveInView = "
                                + mMoveInView
                                + ",mMovingChildViews = "
                                + mMovingChildViews
                                + ", sDragView = "
                                + ((sDragView != null) ? sDragView.getTag()
                                        : "null")
                                + ((sOriginDragView != null) ? sOriginDragView
                                        .getTag() : "null")
                                + ", drag view's vis = "
                                + ((sDragView != null) ? sDragView
                                        .getVisibility() : "null")
                                + ", origin drag view's vis = "
                                + ((sOriginDragView != null) ? sOriginDragView
                                        .getVisibility() : "null")
                                + ", this = " + DragSortGridView.this);
            }

            switch (action) {
            case DragEvent.ACTION_DRAG_STARTED:
                if (mInternalDrag) {
                    // notice: init array with adapter item count, NOT child
                    // view count.
                    mReorderingPositions = new PositionInfo(getAdapter()
                            .getCount());
                    mLastDraggingPosition = (Integer) event.getLocalState();
                    sDragView = getView(mLastDraggingPosition);
                    sOriginDragView = sDragView;
                    if(getView(mLastDraggingPosition) != null)
                    getView(mLastDraggingPosition).startAnimation(  
                            mFadeOutAnimation);
                }
                break;

            case DragEvent.ACTION_DRAG_ENTERED:
                mLastDragLocationY = y;
                mInDragState = true;
                if (mInternalDrag) {
                    mDragMode = DRAG_MODE_REORDER;
                    sDragView = sOriginDragView;

                    mDragFinished = false;
                    mAnimationCount = 0;
                } else {
                    mDragMode = DRAG_MODE_SWITCH;
                    if (getChildCount() == 0) {
                        pos = 0;
                    } else {
                        if((mAdapter.getCount() > COUNT_CHILD_SHOW_IN_TREE_LINE) 
							|| (mAdapter.getCount() == COUNT_CHILD_SHOW_IN_TREE_LINE))/*For ALPS02111552*/
                        {                           
                            pos = findInsertPosition(x, y);
                            if (pos == -1) {                                                            
                                int ChildBottom = 0;
                                final View lastChildView = DragSortGridView.this
                                        .getChildAt(getChildCount() - 1);
                                if(lastChildView != null)
                                    ChildBottom = lastChildView.getBottom();
                                Xlog.d(TAG, "ACTION_DRAG_ENTERED: ChildBottom = " + ChildBottom);
                                final int lastChildBottom = /*lastChildView.getBottom()*/ChildBottom;
                                                                
                                final int gridViewBottom = DragSortGridView.this.getBottom();
                                final int verticalSpace = DragSortGridView.this
                                       .getVerticalSpacing();
                                
                                
                                Xlog.d(TAG, "ACTION_DRAG_ENTERED: pos = " + pos
                                        + ", lastChildBottom = " + lastChildBottom
                                        + ", gridViewBottom = " + gridViewBottom
                                        + ", verticalSpace = " + verticalSpace
                                        + ", lastChildView = " + lastChildView);
                                
                                // If the visual grid is full of items, we need
                                // to insert the icon at the last visible position,
                                // which means the position last visible child, or
                                // else insert to the end of the grid.
                                
                                if (lastChildBottom < gridViewBottom
                                        && (gridViewBottom - lastChildBottom <= verticalSpace
                                                + VISUAL_SPACING_SLOT)) {
                                    pos = DragSortGridView.this.getFirstVisiblePosition()
                                            + getChildCount() - 1;
                                } else {
                                    pos = DragSortGridView.this.getFirstVisiblePosition()
                                            + getChildCount();
                                }
                                    
                                    
                            }
                        }
                        else{                       
                            pos = mAdapter.getCount();
                        }
                    }   
                    FloatAppItem floatItem = (FloatAppItem) sDragView.getTag();
                    floatItem.visible = false;
                    Xlog.d(TAG, "ACTION_DRAG_ENTERED lastPosition:"
                            + mLastDraggingPosition + ", pos = " + pos
                            + ",floatItem = " + floatItem + ",getTop() = "
                            + getTop() + ", this = " + this);
                    mAdapter.addItem((FloatAppItem) sDragView.getTag(), pos);
                    mReorderingPositions = new PositionInfo(mAdapter.getCount());
                    mInsertedPos = pos;
                    mLastDraggingPosition = pos;
                    mWaitingForInsertedItem = true;
                }
                mMoveInView = true;
                break;

            case DragEvent.ACTION_DRAG_LOCATION:
                scrollIfNeeded(mLastDragLocationY, y);
                mLastDragLocationY = y;
                if (mWaitingForInsertedItem || !mMoveInView
                        || mMovingChildViews) {
                    Xlog.d(TAG,
                            "ACTION_DRAG_LOCATION canceld: mMovingChildViews = "
                                    + mMovingChildViews + ", mMoveInView = "
                                    + mMoveInView
                                    + ",mWaitingForInsertedItem = "
                                    + mWaitingForInsertedItem + ", this = "
                                    + this);
                    break;
                }

                pos = findInsertPosition(x, y);
                newPosition = (pos == -1) ? -1 : mReorderingPositions
                        .getValueIndex(pos);
                if (DEBUG_DRAG) {
                    Xlog.d(TAG,
                            "ACTION_DRAG_LOCATION inner, mLastDraggingPosition = "
                                    + mLastDraggingPosition
                                    + ", mInternalDrag = " + mInternalDrag
                                    + ", pos = " + pos + ",newPosition:"
                                    + newPosition + ", item = "
                                    + sDragView.getTag());
                }

                if (DEBUG_LAYOUT) {
                    dumpLayoutPosition();
                }

                if (-1 != newPosition && mLastDraggingPosition != newPosition) {
                    reorderViews(mLastDraggingPosition, newPosition);
                    mReorderingPositions.reorder(mLastDraggingPosition,
                            newPosition);
                    mLastDraggingPosition = newPosition;
                    mMovingChildViews = true;
                    mDragSucceed = false;
                    Xlog.d(TAG, "reordering positions:" + mReorderingPositions);
                }
                break;

            case DragEvent.ACTION_DRAG_EXITED:
                mInDragState = false;
                mHandler.removeCallbacks(mScrollRunnable);
                mScrollerState = SCROLL_STATE_IDLE;
                mMoveInView = false;
                if (mInternalDrag) {
                    mDragMode = DRAG_MODE_SWITCH;
                } else {
                    mDragMode = DRAG_MODE_NONE;
                    mWaitingForInsertedItem = false;
                    clearAllAnimations();
                    mAdapter.removeItem(mInsertedPos);
                    mWaitingForRemovedItem = true;
                }
                break;

            case DragEvent.ACTION_DROP:
                if (DEBUG_DRAG) {
                    dumpLayoutPosition();
                }
                mDragSucceed = true;
                mHandler.removeCallbacks(mScrollRunnable);
                mScrollerState = SCROLL_STATE_IDLE;
                if (mMoveInView) {
                    mMoveInView = false;
                    final int oldPosition = (Integer) event.getLocalState();
                    pos = findInsertPosition(x, y);
                    newPosition = (pos == -1) ? 
                        (mAdapter.getCount() -1 ) /*for ALPS02070179 when pos is -1,put icon in tail */
                    : mReorderingPositions
                            .getValueIndex(pos);

                    // There may be drag a view into another grid's end and
                    // then drop quickly, the layout process is not
                    // processed yet, the view would be null, we don't clear
                    // animation for this scenario.
                    final View lastDragView = getView(mLastDraggingPosition);
                    if (lastDragView != null) {
                        lastDragView.clearAnimation();
                    }

                    if (DEBUG_DRAG) {
                        Xlog.d(TAG, "ACTION_DROP pos:" + pos + ",newPosition:"
                                + newPosition + ", oldPosition = "
                                + oldPosition + ", lastDragView = "
                                + lastDragView + ", this = " + this);
                    }

                    makeDragViewVisible();

                    if (mOnReorderingListener != null) {
                        if (mInternalDrag) {
                            if (newPosition != -1 && newPosition != oldPosition) {
                                mOnReorderingListener.onReordering(oldPosition,
                                        newPosition);
                            } else {
                                mAdapter.notifyDataSetChanged();
                            }
                        } else {
                            mOnReorderingListener.onItemSwitched(oldPosition);
                            /*
                            if (newPosition != -1
                                    && newPosition != mInsertedPos) {
                            */
                            if (newPosition != -1) {
                                mOnReorderingListener.onReordering(
                                        mInsertedPos, newPosition);
                            } else {
                                mAdapter.notifyDataSetChanged();
                            }
                        }
                    }
                }
                break;

            case DragEvent.ACTION_DRAG_ENDED:
                // Reset the position of all views if the drag is canceled.
                if (!mDragSucceed) {
                    mAdapter.notifyDataSetChanged();
                }
                makeDragViewVisible();
                if (DEBUG_DRAG) {
                    dumpLayoutPosition();
                }
                mDragMode = DRAG_MODE_NONE;
                mWaitingForInsertedItem = false;
                mWaitingForRemovedItem = false;
                mInternalDrag = false;
                mMoveInView = false;
                mDragFinished = true;
                sDragView = null;
                sOriginDragView = null;
                mInsertedPos = -1;
                clearAllAnimations();
                break;

            default:
                break;
            }
            return mDragEnabled;
        }
    };

    private void makeDragViewVisible() {
        if (sDragView != null) {
            FloatAppItem floatItem = (FloatAppItem) sDragView.getTag();
            floatItem.visible = true;
            sDragView.clearAnimation();
            if (sDragView.getVisibility() == View.INVISIBLE) {
                Xlog.d(TAG, "Reset drag view visible: floatItem = " + floatItem
                        + ",sDragView = " + sDragView);
                sDragView.setVisibility(View.VISIBLE);
            }
        }

        if (sOriginDragView != null) {
            FloatAppItem floatItem = (FloatAppItem) sOriginDragView.getTag();
            floatItem.visible = true;
            sOriginDragView.clearAnimation();
            if (sOriginDragView.getVisibility() == View.INVISIBLE) {
                Xlog.d(TAG, "Reset origin drag view visible: floatItem = "
                        + floatItem + ", sOriginDragView = " + sOriginDragView);
                sOriginDragView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void clearAllAnimations() {
        final int childrenCount = getChildCount();
        Xlog.d(TAG, "clearAllAnimations: childrenCount = " + childrenCount
                + ", this = " + this);
        for (int i = 0; i < childrenCount; i++) {
            final View child = getChildAt(i);
            if(child != null)
              child.clearAnimation();
        }
    }

    private int findInsertPosition(int x, int y) {
        Rect frame = new Rect();
        Rect tempFrame = new Rect();
        Rect tempFrame1 = new Rect();

        final int columnNum = getNumColumns();
        final int count = getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            if (child != null&&child.getVisibility() != View.GONE) {
                child.getHitRect(frame);
                // The very left item.
                if (i % columnNum == 0) {
                    // The item is not moved.
                    if (frame.left < child.getWidth()) {
                        frame.left -= getPaddingLeft();
                    }
                }

                // The first row item.
                if (i < columnNum) {
                    frame.top -= getPaddingTop();
                }

                // The very right item.
                if ((i + 1) % columnNum == 0) {
                    if(getChildAt(i - 1) != null)
                      getChildAt(i - 1).getHitRect(tempFrame);
                    // The very right item is moved to its front position.
                    if (tempFrame.left > frame.right) {
                        frame.right += getHorizontalSpacing();
                    } else {
                        frame.right = getRight();
                    }
                } else {
                    if (i + 1 < count) {
                        if(getChildAt(i + 1) != null)
                          getChildAt(i + 1).getHitRect(tempFrame);
                        // Fix drag moving issue, the current dragged view is
                        // moving right between i and i+1, or the item is moving
                        // left between i-1 and i.
                        if (tempFrame.left - frame.right >= child.getWidth()
                                || tempFrame.right < frame.left) {
                            frame.right += getHorizontalSpacing();
                        } else {
                            frame.right = tempFrame.left;
                        }
                    } else {
                        frame.right += getHorizontalSpacing();
                    }
                }

                // The very bottom item
                if (i + columnNum < count) {
                    if(getChildAt(i + columnNum) != null)
                      getChildAt(i + columnNum).getHitRect(tempFrame1);
                    if (tempFrame1.top <= frame.bottom) {
                        frame.bottom += getVerticalSpacing();
                    } else {
                        frame.bottom = tempFrame1.top;
                    }
                } else {
                    frame.bottom += getVerticalSpacing();
                }

                if (DEBUG_DRAG) {
                    Xlog.d(TAG,
                            "findInsertPosition: i = " + i + ", x = " + x
                                    + ",y = " + y + ", frame = " + frame
                                    + ",columnNum = " + columnNum + ",tag = "
                                    + child.getTag() + ",tempFrame = "
                                    + tempFrame + ", tempFrame1 = "
                                    + tempFrame1);
                }
                if (frame.contains(x, y)) {
                    return getFirstVisiblePosition() + i;
                }
            }
        }
        return INVALID_POSITION;
    }

    /**
     * Move child view from one position to another. Example: [0, 1, 2, 3, 4],
     * move from 1 to 3 results: [0, 2, 3, 1, 4]. Another: [0, 1, 2, 3, 4], move
     * from 3 to 1 results: [0, 3, 1, 2, 4].
     * 
     * @param fromPosition
     * @param toPosition
     */
    private void reorderViews(int fromPosition, int toPosition) {
        if (fromPosition == toPosition) {
            return;
        }

        final View fromChild = getView(fromPosition);
        final View toChild = getView(toPosition);
        Rect toLayout = new Rect();

        if(fromChild == null||toChild == null) return;
        // Preserve view layout on new position, for it will be changed on
        // next step.
        getLayoutRect(toChild, toLayout);

        if (DEBUG_LAYOUT) {
            Xlog.d(TAG,
                    "reorderViews old:" + fromPosition + ",new:" + toPosition
                            + ",from tag = " + fromChild.getTag()
                            + ",to tag = " + toChild.getTag() + ", toLayout = "
                            + toLayout + ",fromChild vis = "
                            + fromChild.getVisibility());
        }
        if (toPosition < fromPosition) {
            for (int i = toPosition; i < fromPosition; ++i) {
                moveView(i, i + 1);
            }
            fromChild.layout(toLayout.left, toLayout.top, toLayout.right,
                    toLayout.bottom);
        } else {
            for (int i = toPosition; i > fromPosition; --i) {
                moveView(i, i - 1);
            }
            fromChild.layout(toLayout.left, toLayout.top, toLayout.right,
                    toLayout.bottom);
        }
    }

    /**
     * Move the view from the fromPosition to the toPosition, use animation to
     * do this, the layout params of the from view will change to the to
     * position when animation ended.
     * 
     * @param fromPosition
     * @param toPosition
     */
    private void moveView(int fromPosition, int toPosition) {
        final View from = getView(fromPosition);
        final Rect fromRect = new Rect();

        if(from != null)
        getLayoutRect(from, fromRect);

        final View to = getView(toPosition);
        final Rect toRect = new Rect();
        if(to != null)
        getLayoutRect(to, toRect);

        if(from == null||to == null) return;
        
        if (DEBUG_LAYOUT) {
            Xlog.d(TAG,
                    "moveView from:" + fromPosition + ",to:" + toPosition
                            + ",from tag = " + from.getTag() + ",to tag = "
                            + to.getTag() + ", fromRect = " + fromRect
                            + ", toRect = " + toRect + ", fromView = " + from
                            + ", toView = " + to);
        }

        Animation translate = new TranslateAnimation(0, toRect.left
                - fromRect.left, 0, toRect.top - fromRect.top);
        translate.setDuration(TRANSLATE_ANIM_DURATION);
        translate.setFillEnabled(true);
        translate.setFillBefore(true);
        translate.setFillAfter(true);
        translate.setAnimationListener(new MoveViewAnimationListener(from, to
                .getLeft(), to.getTop()));

        from.startAnimation(translate);

    }

    private class MoveViewAnimationListener implements
            Animation.AnimationListener {
        private View mTarget;

        private int mNewX;

        private int mNewY;

        public MoveViewAnimationListener(View view, int x, int y) {
            mTarget = view;
            mNewX = x;
            mNewY = y;
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            mTarget.layout(mNewX, mNewY, mNewX + mTarget.getWidth(), mNewY
                    + mTarget.getHeight());
            mTarget.clearAnimation();
            mMovingChildViews = false;
            mAnimationCount--;
            if (DEBUG_LAYOUT) {
                Xlog.d(TAG, "onAnimationEnd: newX = " + mNewX + ", newY = "
                        + mNewY + ", target tag = " + mTarget.getTag()
                        + ",mAnimationCount = " + mAnimationCount
                        + ", mDragFinished = " + mDragFinished + ",target = "
                        + mTarget);
            }
            if ((mAnimationCount == 0) && mDragFinished) {
                requestLayout();
            }
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
            if (DEBUG_LAYOUT) {
                Xlog.d(TAG, "onAnimationRepeat: target = " + mTarget
                        + ", tag = " + mTarget.getTag() + ",mAnimationCount = "
                        + mAnimationCount);
            }
        }

        @Override
        public void onAnimationStart(Animation animation) {
            mAnimationCount++;
            if (DEBUG_LAYOUT) {
                Xlog.d(TAG, "onAnimationStart: target = " + mTarget
                        + ", tag = " + mTarget.getTag() + ",mAnimationCount = "
                        + mAnimationCount);
            }
        }
    }

    private void getLayoutRect(View view, Rect outRect) {
        outRect.set(view.getLeft(), view.getTop(), view.getRight(),
                view.getBottom());
    }

    public View getView(int reorderingPosition) {
        int orgPosition = mReorderingPositions.get(reorderingPosition);
        View childView = getChildAt(orgPosition - getFirstVisiblePosition());
        if (DEBUG_LAYOUT) {
            Xlog.d(TAG, "getView: reorderingPosition = " + reorderingPosition
                    + ", orgPosition = " + orgPosition + ", tag = "
                    + (childView != null ? childView.getTag() : "NULL"));
        }
        return childView;
    }

    public void setOnReorderingListener(OnReorderingListener listener) {
        mOnReorderingListener = listener;
    }

    /**
     * Only user of this view know how to update content data to change their
     * order. So they need this callback.
     */
    public interface OnReorderingListener {
        /**
         * The item of the from position is changed its position to the
         * toPosition.
         * 
         * @param fromPosition
         * @param toPosition
         */
        void onReordering(int fromPosition, int toPosition);

        /**
         * The item of the given position is switched out to another view.
         * 
         * @param switchedPosition
         */
        void onItemSwitched(int switchedPosition);
    }

    /**
     * Between {@link DragEvent.ACTION_DRAG_STARTED} and
     * {@link DragEvent.ACTION_DRAG_ENDED}, grid view children are moving around
     * and lost their original positions. So we need a method to keep track of
     * the changing positions.
     */
    private class PositionInfo {
        private int[] mPositions;

        public PositionInfo(int size) {
            mPositions = new int[size];
            for (int i = 0; i < size; ++i) {
                mPositions[i] = i;
            }
        }

        public int get(int position) {
            return mPositions[position];
        }

        /**
         * Move array value from one position to another. Example: [0, 1, 2, 3,
         * 4], move from 1 to 3 results: [0, 2, 3, 1, 4]. Another: [0, 1, 2, 3,
         * 4], move from 3 to 1 results: [0, 3, 1, 2, 4].
         */
        public void reorder(int from, int to) {
            if (from == to) {
                return;
            }

            final int[] array = mPositions;

            if (from < to) {
                int fromValue = array[from];
                for (int i = from; i < to; ++i) {
                    array[i] = array[i + 1];
                }
                array[to] = fromValue;
            } else {
                int fromValue = array[from];
                for (int i = from; i > to; --i) {
                    array[i] = array[i - 1];
                }
                array[to] = fromValue;
            }
        }

        public void insertItem(int index) {
            Xlog.d(TAG, "insertItem start: index = " + index + ",this = "
                    + this);
            int[] newData = new int[mPositions.length + 1];
            if (index >= mPositions.length) {
                System.arraycopy(mPositions, 0, newData, 0, mPositions.length);
                newData[mPositions.length] = mPositions.length + 1;
            } else {
                for (int i = 0; i < index; i++) {
                    newData[i] = mPositions[i];
                }
                newData[index] = mPositions.length + 1;
                for (int i = index; i < mPositions.length; i++) {
                    newData[i + 1] = mPositions[i];
                }
            }
            mPositions = newData;
            Xlog.d(TAG, "insertItem end: index = " + index + ",this = " + this);
        }

        public int getSize() {
            return mPositions.length;
        }

        public void removeItem(int index) {
            Xlog.d(TAG, "removeItem start: index = " + index + ",this = "
                    + this);
            if (index < 0 || index >= mPositions.length) {
                Xlog.d(TAG, "removeItem failed: index = " + index
                        + ", data.length = " + mPositions.length);
                return;
            }
            int[] newData = new int[mPositions.length - 1];
            for (int i = 0; i < index; i++) {
                newData[i] = mPositions[i];
            }
            for (int i = index; i < mPositions.length - 1; i++) {
                newData[i] = mPositions[i + 1];
            }
            mPositions = newData;
            Xlog.d(TAG, "removeItem start: index = " + index + ",this = "
                    + this);
        }

        public int getValueIndex(int value) {
            final int[] array = mPositions;
            final int size = array.length;

            for (int i = 0; i < size; ++i) {
                if (value == array[i]) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("[");

            final int[] array = mPositions;
            final int size = array.length;
            for (int i = 0; i < size; i++) {
                builder.append(Integer.toString(array[i])).append(",");
            }
            builder.append("]");
            return builder.toString();
        }
    }

    private static class FloatDragShadowBuilder extends View.DragShadowBuilder {
        private static final float SHADOW_ZOOM_RATIO = 1.4f;

        private static Drawable sShadow;

        public FloatDragShadowBuilder(View view) {
            super(view);

            sShadow = new ColorDrawable(Color.LTGRAY);
        }

        @Override
        public void onProvideShadowMetrics(Point shadowSize,
                Point shadowTouchPoint) {
            final View view = getView();
            if (view != null) {
                final int width = view.getWidth();
                final int height = view.getHeight();
                shadowSize.set((int) (width * SHADOW_ZOOM_RATIO),
                        (int) (height * SHADOW_ZOOM_RATIO));
                shadowTouchPoint.set(shadowSize.x / 2, shadowSize.y * 3 / 4);
                Xlog.d(TAG, "onProvideShadowMetrics: width = " + width
                        + ",height = " + height + ",shadowSize = " + shadowSize
                        + ",shadowTouchPoint = " + shadowTouchPoint
                        + ", view = " + getView());
            } else {
                super.onProvideShadowMetrics(shadowSize, shadowTouchPoint);
            }
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            // shadow.draw(canvas);

            final View view = getView();
            if (view != null) {
                Drawable drawable = null;
                if (view instanceof TextView) {
                    drawable = ((TextView) view).getCompoundDrawables()[1];
                }
                Xlog.d(TAG, "onDrawShadow: vis = " + view.getVisibility()
                        + ",drawable = " + drawable + ", view = " + view);
                view.draw(canvas);
                if(drawable != null)
                drawZoomedIcon(canvas, drawable, SHADOW_ZOOM_RATIO);
            } else {
                Xlog.e(TAG, "Asked to draw drag shadow but no view");
            }
        }

        private void drawZoomedIcon(Canvas canvas, Drawable drawable,
                float zoomRatio) {
            final int width = drawable.getIntrinsicWidth();
            final int height = drawable.getIntrinsicHeight();
            final Bitmap oldbmp = drawableToBitmap(drawable);
            Xlog.d(TAG, "drawZoomedIcon: width = " + width + ",height = "
                    + height + ", oldbmp = " + oldbmp + ", zoomRatio = "
                    + zoomRatio);
            Matrix matrix = new Matrix();
            matrix.postScale(zoomRatio, zoomRatio);
            Bitmap newbmp = Bitmap.createBitmap(oldbmp, 0, 0, width, height,
                    matrix, true);
            Xlog.d(TAG, "drawZoomedIcon: newbmp width = " + newbmp.getWidth()
                    + ",newbmp height = " + newbmp.getHeight());
            canvas.drawBitmap(newbmp, 0, 0, new Paint());

            if (!oldbmp.isRecycled()) {
                oldbmp.recycle();
            }
        }

        private Bitmap drawableToBitmap(Drawable drawable) {
            int width = drawable.getIntrinsicWidth();
            int height = drawable.getIntrinsicHeight();
            Bitmap.Config config = drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888
                    : Bitmap.Config.RGB_565;
            final Bitmap bitmap = Bitmap.createBitmap(width, height, config);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);
            return bitmap;
        }
    }

    private void dumpLayoutPosition() {
        final int childrenCount = this.getChildCount();
        Rect toLayout = new Rect();
        for (int i = 0; i < childrenCount; i++) {
            if (this.getChildAt(i)!=null)
            {
              getLayoutRect(this.getChildAt(i), toLayout);
              Xlog.d(TAG, "Child[" + i + "] :" + toLayout + ",tag = "
                    + this.getChildAt(i).getTag());
            }
        }
    }

    private static final SparseArray<String> DRAG_EVENT_ACTION = DEBUG_DRAG ? new SparseArray<String>()
            : null;
    static {
        if (DEBUG_DRAG) {
            DRAG_EVENT_ACTION.put(DragEvent.ACTION_DRAG_STARTED,
                    "ACTION_DRAG_STARTED");
            DRAG_EVENT_ACTION.put(DragEvent.ACTION_DRAG_ENTERED,
                    "ACTION_DRAG_ENTERED");
            DRAG_EVENT_ACTION.put(DragEvent.ACTION_DRAG_LOCATION,
                    "ACTION_DRAG_LOCATION");
            DRAG_EVENT_ACTION.put(DragEvent.ACTION_DRAG_EXITED,
                    "ACTION_DRAG_EXITED");
            DRAG_EVENT_ACTION.put(DragEvent.ACTION_DROP, "ACTION_DROP");
            DRAG_EVENT_ACTION.put(DragEvent.ACTION_DRAG_ENDED,
                    "ACTION_DRAG_ENDED");
        }
    }

    /**
     * If drag to edge, check if we need to scroll vertically. If need, then do
     * a smooth scroll to show more content
     */
    private void scrollIfNeeded(int oldY, int newY) {
        if (!mVerticalDragScrollEnable) {
            return;
        }
        if (DEBUG_DRAG) {
            Xlog.d(TAG, "scrollIfNeeded oldY=" + oldY + ", newY=" + newY
                    + ", container.top=" + getTop() + ", container.bottom="
                    + getBottom() + ", mScrollerState=" + mScrollerState
                    + ", mTouchSlot=" + mTouchSlot
                    + ", canScrollHorizontally(1)=" + canScrollHorizontally(1)
                    + ", canScrollHorizontally(0)=" + canScrollHorizontally(0));
        }
        if (newY >= 0) {
            if (newY >= oldY && newY + mTouchSlot >= getBottom()
                    && mScrollerState == SCROLL_STATE_IDLE) {
                Xlog.d(TAG, "scrollIfNeeded scroll down");
                mScrollerState = SCROLL_STATE_SCROLLING;
                mDragDirection = SCROLL_DIRECTION_DOWN;
                mHandler.postDelayed(mScrollRunnable, DELAY_DRAG_SCROLL);
            } else if (oldY >= newY && newY - mTouchSlot <= getTop()
                    && mScrollerState == SCROLL_STATE_IDLE) {
                Xlog.d(TAG, "scrollIfNeeded scroll up");
                mScrollerState = SCROLL_STATE_SCROLLING;
                mDragDirection = SCROLL_DIRECTION_UP;
                mHandler.postDelayed(mScrollRunnable, DELAY_DRAG_SCROLL);
            }
            if (mDragDirection == SCROLL_DIRECTION_DOWN) {
                mEdgeScrollForceStop = newY < oldY
                        && (newY + EDGE_STOP_SCROLL_SLOT < oldY);
            } else if (mDragDirection == SCROLL_DIRECTION_UP) {
                mEdgeScrollForceStop = newY > oldY
                        && (newY + EDGE_STOP_SCROLL_SLOT > oldY);
            }
        }
    }

    private class ScrollRunnable implements Runnable {
        @Override
        public void run() {
            int flag = mDragDirection == SCROLL_DIRECTION_DOWN ? 1 : -1;
            if (DEBUG_DRAG) {
                Xlog.d(TAG, "ScrollRunnable run, mDragDirection="
                        + mDragDirection + ", falg=" + flag
                        + ", mLastDragLocationY=" + mLastDragLocationY
                        + ", mScrollerState=" + mScrollerState
                        + ", mEdgeScrollForceStop=" + mEdgeScrollForceStop
                        + ", mInDragState=" + mInDragState);
            }
            int scrollDistance = (int) (mSmoothScrollAmountAtEdge * flag + mDragWeight
                    * mDragWeight);
            if (mScrollerState != SCROLL_STATE_IDLE) {
                mScrollerState = SCROLL_STATE_IDLE;
                if (!mEdgeScrollForceStop) {
                    Xlog.d(TAG,
                            "ScrollRunnable run smooth scroll scrollDistance="
                                    + scrollDistance);
                    DragSortGridView.this.smoothScrollBy(scrollDistance, 500);
                }
            } else {
                Xlog.d(TAG, "ScrollRunnable run idle state, mScrollerState="
                        + mScrollerState + ", mEdgeScrollForceStop="
                        + mEdgeScrollForceStop);
            }
            if (mInDragState && !mEdgeScrollForceStop) {
                scrollIfNeeded(mLastDragLocationY, mLastDragLocationY);
            }
        }
    }

    private final OnItemLongClickListener mItemLongClickListener = new OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view,
                int position, long id) {
            Xlog.d(TAG,
                    "onItemLongClick: position = " + position
                            + ", mDragEnabled = " + mDragEnabled + ", tag = "
                            + view.getTag() + ", view = " + view);
            if (mDragEnabled) {
                view.startDrag(null, new FloatDragShadowBuilder(view),
                        position, 0);
                mInternalDrag = true;
            }
            return mDragEnabled;
        }
    };
}
