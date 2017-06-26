
package com.android.wallpaper.test;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

public class TestUtils {
    /**
     * example : tap on the all_apps_button to show Apps List. public void
     * testAllAppsButton() throws InterruptedException{ final View v =
     * mLauncher.findViewById(R.id.all_apps_button); assertNotNull(v); int
     * location[] = Launcher2TestUtils.getViewLocationOnScreen(v);
     * Launcher2TestUtils.tapOnPoint(mInst, location[0], location[1]); }
     */

    /**
     * Get the left top point screen coordinate of the view.
     *
     * @param v
     * @return
     */
    public static int[] getViewLocationOnScreen(View v) {
        int location[] = new int[2];
        v.getLocationOnScreen(location);
        return location;
    }

    /**
     * Send a tap action at point (x, y).
     *
     * @param inst
     * @param x
     * @param y
     */
    public static void tapAtPoint(Instrumentation inst, int x, int y) {
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();

        MotionEvent event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, x, y,
                0);
        inst.sendPointerSync(event);

        eventTime = SystemClock.uptimeMillis();
        event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, x, y, 0);
        inst.sendPointerSync(event);
    }

    /**
     * long Click At Point (x, y).
     *
     * @param inst
     * @param x
     * @param y
     */
    public static void longClickAtPoint(Instrumentation inst, int x, int y) {
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();

        MotionEvent event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, x, y,
                0);
        inst.sendPointerSync(event);

        try {
            Thread.sleep(600);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        eventTime = SystemClock.uptimeMillis();
        event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, x, y, 0);
        inst.sendPointerSync(event);
    }

    /**
     * slipping from point(fromX, fromY) to point(toX, to Y) in "stepCount"
     * steps.
     *
     * @param inst
     * @param fromX
     * @param toX
     * @param fromY
     * @param toY
     * @param stepCount
     */
    public static void slipping(Instrumentation inst, float fromX, float toX, float fromY,
            float toY, int stepCount) {
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        float x = fromX;
        float y = fromY;
        float yStep = (toY - fromY) / stepCount;
        float xStep = (toX - fromX) / stepCount;

        MotionEvent event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, fromX,
                fromY, 0);
        inst.sendPointerSync(event);

        for (int i = 0; i < stepCount; ++i) {
            y += yStep;
            x += xStep;
            eventTime = SystemClock.uptimeMillis();
            event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, x, y, 0);
            inst.sendPointerSync(event);
        }

        eventTime = SystemClock.uptimeMillis();
        event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, toX, toY, 0);
        inst.sendPointerSync(event);
    }

    public static void drag(Instrumentation inst, float fromX, float toX, float fromY, float toY,
            int stepCount) {
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        float x = fromX;
        float y = fromY;
        float yStep = (toY - fromY) / stepCount;
        float xStep = (toX - fromX) / stepCount;

        MotionEvent event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, fromX,
                fromY, 0);
        inst.sendPointerSync(event);
        try { // Sleep 500ms, change the event to be a long click action.
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < stepCount; ++i) {
            y += yStep;
            x += xStep;
            eventTime = SystemClock.uptimeMillis();
            event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, x, y, 0);
            inst.sendPointerSync(event);
        }

        eventTime = SystemClock.uptimeMillis();
        event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, toX, toY, 0);
        inst.sendPointerSync(event);
    }
}
