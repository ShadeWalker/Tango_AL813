package com.mediatek.launcher3.ext;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.TextView;

/**
 * M: Default IWorkspaceExt implements.
 */
public class DefaultWorkspaceExt implements IWorkspaceExt {
    private static final String TAG = "DefaultWorkspaceExt";
    protected Context mContext;

    /**
     * Constructs a new DefaultWorkspaceExt instance with Context.
     * @param context A Context object
     */
    public DefaultWorkspaceExt(Context context) {
        mContext = context;
    }

    @Override
    public boolean supportEditAndHideApps() {
        LauncherLog.d(TAG, "default supportEditAndHideApps called.");
        return false;
    }

    @Override
    public boolean supportAppListCycleSliding() {
        LauncherLog.d(TAG, "default supportAppListCycleSliding called.");
        return false;
    }

    @Override
    public void customizeWorkSpaceIconText(TextView tv, float orgTextSize) {
        LauncherLog.d(TAG, "default setWorkSpaceIconTextLine called.");
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, orgTextSize);
    }

    @Override
    public void customizeCompoundPaddingForBubbleText(TextView tv, int orgPadding) {
        tv.setCompoundDrawablePadding(orgPadding);
    }

    @Override
    public void customizeFolderNameLayoutParams(LayoutParams lp, int iconSizePx,
            int iconDrawablePaddingPx) {
        // Do nothing for default implementation.
    }

    @Override
    public int customizeFolderPreviewOffsetY(int orgPreviewOffsetY, int folderBackgroundOffset) {
        return orgPreviewOffsetY;
    }

    @Override
    public void customizeFolderPreviewLayoutParams(FrameLayout.LayoutParams lp) {
        // Do nothing for default implementation.
    }

    @Override
    public int customizeFolderCellHeight(int orgHeight) {
        return orgHeight;
    }

    //for limited screen
    @Override
    public boolean exceedLimitedScreen(int size) {
        return false;
    }

    @Override
    public void customizeOverviewPanel(ViewGroup overviewPanel, View[] overviewButtons) {
        // Do nothing for default implementation.
    }
}
