package com.mediatek.incallui;

import android.app.StatusBarManager;
import android.content.Context;

public class StatusBarHelper {
    // Current desired state of status bar / system bar behavior
    private boolean mIsSystemBarNavigationEnabled = true;
    private StatusBarManager mStatusBarManager;

    public StatusBarHelper(Context context) {
        mStatusBarManager = (StatusBarManager) context.getSystemService(
                Context.STATUS_BAR_SERVICE);
    }

    /**
     * Enables or disables the navigation via the system bar (the
     * "soft buttons" at the bottom of the screen)
     *
     * (This feature is disabled while an incoming call is ringing,
     * because it's easy to accidentally touch the system bar while
     * pulling the phone out of your pocket.)
     */
    public void enableSystemBarNavigation(boolean enable) {
        if (mIsSystemBarNavigationEnabled != enable) {
            mIsSystemBarNavigationEnabled = enable;
            updateStatusBar();
        }
    }

    /**
     * Updates the status bar to reflect the current desired state.
     */
    private void updateStatusBar() {
        int state = StatusBarManager.DISABLE_NONE;

        if (!mIsSystemBarNavigationEnabled) {
            // Disable *all* possible navigation via the system bar.
            state |= StatusBarManager.DISABLE_EXPAND;
            state |= StatusBarManager.DISABLE_HOME;
            state |= StatusBarManager.DISABLE_RECENT;
            state |= StatusBarManager.DISABLE_BACK;
            state |= StatusBarManager.DISABLE_SEARCH;
        }

        mStatusBarManager.disable(state);
    }
}
