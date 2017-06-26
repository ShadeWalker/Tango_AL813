package com.mediatek.launcher3.ext;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * M: Launcher Workspace ext interface for OP customized.
 */
public interface IWorkspaceExt {

    /**
     * Whether support app list edit and hide.
     *
     * @return True for OP09 projects, else false.
     */
    boolean supportEditAndHideApps();

    /**
     * Whether support app list cycle sliding.
     *
     * @return True for OP09 projects, else false.
     */
    boolean supportAppListCycleSliding();

    /**
     * Customize workspace icon text, set text to two max lines and set the text
     * size for OP09 projects.
     *
     * @param tv TextView object.
     * @param orgTextSize default text size.
     */
    void customizeWorkSpaceIconText(TextView tv, float orgTextSize);

    /**
     * Customize compound padding for bubble text view.
     *
     * @param tv TextView object.
     * @param orgPadding default padding.
     */
    void customizeCompoundPaddingForBubbleText(TextView tv, int orgPadding);

    /**
     * Customize folder name layout params.
     *
     * @param lp LayoutParams.
     * @param iconSizePx icon Size (Px).
     * @param iconDrawablePaddingPx iconDrawablePadding (Px).
     */
    void customizeFolderNameLayoutParams(FrameLayout.LayoutParams lp,
            int iconSizePx, int iconDrawablePaddingPx);

    /**
     * Customize folder preview Y Offset.
     *
     * @param orgPreviewOffsetY org folder preview y offset.
     * @param folderBackgroundOffset folder background offset.
     * @return Customize folder preview y offset.
     */
    int customizeFolderPreviewOffsetY(int orgPreviewOffsetY, int folderBackgroundOffset);

    /**
     * Customize folder preview layout params.
     *
     * @param lp LayoutParams.
     */
    void customizeFolderPreviewLayoutParams(FrameLayout.LayoutParams lp);

    /**
     * Customize cell height for the folder.
     *
     * @param orgHeight default cell height.
     * @return folder cell height.
     */
    int customizeFolderCellHeight(int orgHeight);

    /**
     * Customize the limited screen on workspace.
     *
     * @param size the screen number of workspace.
     * @return exceed the big screen number or not.
     */
    boolean exceedLimitedScreen(int size);

    /**
     * Customize overview panel buttons.
     *
     * @param overviewPanel overview panel ViewGroup.
     * @param overviewButtons buttons.
     */
    void customizeOverviewPanel(ViewGroup overviewPanel, View[] overviewButtons);
}
