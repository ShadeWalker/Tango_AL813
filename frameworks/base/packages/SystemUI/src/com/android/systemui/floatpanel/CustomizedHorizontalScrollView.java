package com.android.systemui.floatpanel;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.DragEvent;
import android.widget.HorizontalScrollView;

import com.mediatek.xlog.Xlog;

/**
 * M: Add for Multi-window entrance panel on edge scroll.
 */
public class CustomizedHorizontalScrollView extends HorizontalScrollView {

    private static final String TAG = "CustomizedHorizontalScrollView";
    private static final boolean DEBUG_LOG = true;

    // The default scroll distance
    private static final int SMOOTH_SCROLL_AMOUNT_AT_EDGE = 200;
    // The default delay to start a scroll
    private static final int DELAY_DRAG_SCROLL = 250;
    // The threshold of trigger a scroll
    private static final int TOUCH_SLOT = 75;
    // The threashold of force stop edge flow
    private static final int EDGE_STOP_SCROLL_SLOT = 10;

    private int mSmoothScrollAmountAtEdge;
    private int mTouchSlot;

    public CustomizedHorizontalScrollView(Context context) {
        this(context, null);
    }

    public CustomizedHorizontalScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomizedHorizontalScrollView(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
        DisplayMetrics metric = getResources().getDisplayMetrics();
        mSmoothScrollAmountAtEdge = (int) (SMOOTH_SCROLL_AMOUNT_AT_EDGE
                * metric.density + 0.5f);
        mTouchSlot = (int) (TOUCH_SLOT * metric.density + 0.5f);
        mDragDirection = 0;
        mScrollerState = SCROLL_STATE_IDLE;
        mScrollRunnable = new ScrollRunnable();
        mHandler = new Handler();
        mInDragState = false;
    }

    private Handler mHandler;
    private int mLastDraggingPositionX;
    private boolean mInDragState;
    // When user touch and drag icon to edge and wait for large distance scroll,
    // use this weight to speed up scroll
    private float mDragWeight;
    private static final int SCROLL_STATE_IDLE = 0;
    private static final int SCROLL_STATE_SCROLLING = 1;
    // Whether we are in scrolling(SCROLL_STATE_SCROLLING) state or
    // idle(SCROLL_STATE_IDLE) state,
    private int mScrollerState;
    private int mDragDirection;

    // When user drag at the edge, and finger is moving reversely, immediately
    // stop edge scroll
    private boolean mEdgeScrollForceStop;
    private ScrollRunnable mScrollRunnable;

    @Override
    public boolean dispatchDragEvent(DragEvent event) {
        final int action = event.getAction();
        final int x = Math.round(event.getX());
        final int y = Math.round(event.getY());
        if (DEBUG_LOG) {
            Log.d(TAG,
                    "dispatchDragEvent event action:"
                            + DRAG_EVENT_ACTION.get(action) + ",x:" + x + ",y:"
                            + y);
        }
        // if ScrollView is doing scrolling, do not let gridview get drag event
        boolean scrolling = false;
        switch (action) {
        case DragEvent.ACTION_DRAG_STARTED:
            mLastDraggingPositionX = x;
            // notice: init array with adapter item count, NOT child view
            // count.
            break;

        case DragEvent.ACTION_DRAG_ENTERED:
            mInDragState = true;
            mLastDraggingPositionX = x;
            break;

        case DragEvent.ACTION_DRAG_LOCATION:
            scrolling = scrollIfNeeded(mLastDraggingPositionX, x);
            mLastDraggingPositionX = x;
            break;

        case DragEvent.ACTION_DRAG_EXITED:
            mInDragState = false;
            mHandler.removeCallbacks(mScrollRunnable);
            mScrollerState = SCROLL_STATE_IDLE;
            break;

        case DragEvent.ACTION_DROP:
            break;

        case DragEvent.ACTION_DRAG_ENDED:
            mHandler.removeCallbacks(mScrollRunnable);
            mScrollerState = SCROLL_STATE_IDLE;
            break;

        default:
            break;
        }
        return scrolling ? true : super.dispatchDragEvent(event);
    }

    /**
     * If drag to edge, check if we need to scroll horizontally. If need, then
     * do a smooth scroll to show more content
     */
    private boolean scrollIfNeeded(int oldX, int newX) {
        Log.d(TAG, "scrollIfNeeded oldX=" + oldX + ", newX=" + newX
                + ", container.left=" + getLeft() + ", container.right="
                + getRight() + ", mScrollerState=" + mScrollerState
                + ", mTouchSlot=" + mTouchSlot + ", canScrollHorizontally(1)="
                + canScrollHorizontally(1) + ", canScrollHorizontally(0)="
                + canScrollHorizontally(0));
        if (newX >= 0) {
            if (newX >= oldX && newX + mTouchSlot >= getRight()
                    && mScrollerState == SCROLL_STATE_IDLE) {
                Log.d(TAG, "scrollIfNeeded scroll right");
                mScrollerState = SCROLL_STATE_SCROLLING;
                mDragDirection = 1;
                mHandler.postDelayed(mScrollRunnable, DELAY_DRAG_SCROLL);
                return true;
            } else if (oldX >= newX && newX - mTouchSlot <= getLeft()
                    && mScrollerState == SCROLL_STATE_IDLE) {
                Log.d(TAG, "scrollIfNeeded scroll left");
                mScrollerState = SCROLL_STATE_SCROLLING;
                mDragDirection = 0;
                mHandler.postDelayed(mScrollRunnable, DELAY_DRAG_SCROLL);
                return true;
            }
            if ((newX >= oldX && newX + mTouchSlot >= getRight())
                    || (oldX >= newX && newX - mTouchSlot <= getLeft())) {
                mEdgeScrollForceStop = false;
            } else {
                mEdgeScrollForceStop = true;
            }
            if (mDragDirection == 1) {
                mEdgeScrollForceStop = newX < oldX
                        && (newX + EDGE_STOP_SCROLL_SLOT < oldX);
            } else if (mDragDirection == 0) {
                mEdgeScrollForceStop = newX > oldX
                        && (newX + EDGE_STOP_SCROLL_SLOT > oldX);
            }
        }
        return false;
    }

    private static final SparseArray<String> DRAG_EVENT_ACTION = DEBUG_LOG ? new SparseArray<String>()
            : null;
    static {
        if (DEBUG_LOG) {
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

    private class ScrollRunnable implements Runnable {
        public ScrollRunnable() {
        }

        @Override
        public void run() {
            int flag = mDragDirection == 1 ? 1 : -1;
            Log.d(TAG, "ScrollRunnable run, mDragDirection=" + mDragDirection
                    + ", falg=" + flag + ", mLastDraggingPositionX="
                    + mLastDraggingPositionX + ", mScrollerState="
                    + mScrollerState + ", mEdgeScrollForceStop="
                    + mEdgeScrollForceStop + ",mInDragState = " + mInDragState);
            int scrollDistance = (int) (mSmoothScrollAmountAtEdge * flag + mDragWeight
                    * mDragWeight);
            if (mScrollerState != SCROLL_STATE_IDLE) {
                mScrollerState = SCROLL_STATE_IDLE;
                if (!mEdgeScrollForceStop) {
                    Log.d(TAG,
                            "ScrollRunnable run smooth scroll scrollDistance="
                                    + scrollDistance);
                    smoothScrollBy(scrollDistance, 0);
                }
            } else {
                Log.d(TAG, "ScrollRunnable run idle state, mScrollerState="
                        + mScrollerState + ", mEdgeScrollForceStop="
                        + mEdgeScrollForceStop);
            }
            if (mInDragState) {
                scrollIfNeeded(mLastDraggingPositionX, mLastDraggingPositionX);
            }
        }
    }
}
