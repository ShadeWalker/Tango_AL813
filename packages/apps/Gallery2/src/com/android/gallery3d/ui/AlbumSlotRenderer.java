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

package com.android.gallery3d.ui;

import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.app.AlbumDataLoader;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.glrenderer.ColorTexture;
import com.android.gallery3d.glrenderer.FadeInTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.Texture;
import com.android.gallery3d.glrenderer.TiledTexture;
import com.android.gallery3d.util.GalleryUtils;

import com.mediatek.galleryfeature.config.FeatureConfig;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.PlayEngine;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.gallery3d.adapter.PhotoPlayFacade;
import com.mediatek.gallery3d.adapter.FeatureHelper;

/// M: [FEATURE.MODIFY] VTSP @{
/*public class AlbumSlotRenderer extends AbstractSlotRenderer {*/
public class AlbumSlotRenderer extends AbstractSlotRenderer implements PlayEngine.OnFrameAvailableListener {
/// @}
    @SuppressWarnings("unused")
    private static final String TAG = "Gallery2/AlbumView";

    public interface SlotFilter {
        public boolean acceptSlot(int index);
    }

    private final int mPlaceholderColor;
    /// M: [BUG.MODIFY] only cache 32 thumbnail for low memory device @{
    // private static final int CACHE_SIZE = 96;
    private static final int CACHE_SIZE = GalleryUtils.Is_Low_Ram_Device ? 32 : 96;
    /// @}

    private AlbumSlidingWindow mDataWindow;
    private final AbstractGalleryActivity mActivity;
    private final ColorTexture mWaitLoadingTexture;
    private final SlotView mSlotView;
    private final SelectionManager mSelectionManager;

    private int mPressedIndex = -1;
    private boolean mAnimatePressedUp;
    private Path mHighlightItemPath = null;
    private boolean mInSelectionMode;

    private SlotFilter mSlotFilter;
    
    /// M: [PERF.ADD] add for performance test case@{
    private boolean mHasShowLog = false;
    public static boolean sPerformanceCaseRunning = false; 
    /// M: added for performance auto test
    public static long mWaitFinishedTime = 0;
    /// @}

    public AlbumSlotRenderer(AbstractGalleryActivity activity, SlotView slotView,
            SelectionManager selectionManager, int placeholderColor) {
        super(activity);
        mActivity = activity;
        mSlotView = slotView;
        mSelectionManager = selectionManager;
        mPlaceholderColor = placeholderColor;

        mWaitLoadingTexture = new ColorTexture(mPlaceholderColor);
        mWaitLoadingTexture.setSize(1, 1);
        /// M: [FEATURE.ADD] VTSP @{
        mSupportVTSP = !FeatureConfig.isLowRamDevice;
        Log.i(TAG, "<AlbumSlotRenderer> mSupportVTSP = " + mSupportVTSP);
        if (mSupportVTSP) {
            mPlayEngine = PhotoPlayFacade.createPlayEngineForThumbnail(activity);
            mPlayEngine.setOnFrameAvailableListener(this);
        }
        /// @}
    }

    public void setPressedIndex(int index) {
        if (mPressedIndex == index) return;
        mPressedIndex = index;
        mSlotView.invalidate();
    }

    public void setPressedUp() {
        if (mPressedIndex == -1) return;
        mAnimatePressedUp = true;
        mSlotView.invalidate();
    }

    public void setHighlightItemPath(Path path) {
        if (mHighlightItemPath == path) return;
        mHighlightItemPath = path;
        mSlotView.invalidate();
    }

    public void setModel(AlbumDataLoader model) {
        if (mDataWindow != null) {
            mDataWindow.setListener(null);
            mSlotView.setSlotCount(0);
            mDataWindow = null;
        }
        if (model != null) {
            mDataWindow = new AlbumSlidingWindow(mActivity, model, CACHE_SIZE);
            mDataWindow.setListener(new MyDataModelListener());
            mSlotView.setSlotCount(model.size());
        }
    }

    private static Texture checkTexture(Texture texture) {
        return (texture instanceof TiledTexture)
                && !((TiledTexture) texture).isReady()
                ? null
                : texture;
    }

    @Override
    public int renderSlot(GLCanvas canvas, int index, int pass, int width, int height) {
        if (mSlotFilter != null && !mSlotFilter.acceptSlot(index)) return 0;

        AlbumSlidingWindow.AlbumEntry entry = mDataWindow.get(index);

        int renderRequestFlags = 0;

        Texture content = checkTexture(entry.content);
        if (content == null) {
            content = mWaitLoadingTexture;
            entry.isWaitDisplayed = true;
        } else if (entry.isWaitDisplayed) {
            entry.isWaitDisplayed = false;
            /// M: [BUG.MODIFY] FadeInTexture will be transparent when launch gallery @{
            /*content = new FadeInTexture(mPlaceholderColor, entry.bitmapTexture);*/
            content = entry.bitmapTexture;
            /// @}
            entry.content = content;
            /// M: [PERF.ADD]added for performance auto test. @{
            mWaitFinishedTime = System.currentTimeMillis();
            /// @}
        }
        /// M: [FEATURE.ADD] VTSP @{
        boolean hasDraw = drawCurrentSlotDynamic(entry, canvas, index, width, height,
                entry.rotation);
        if (!hasDraw) {
            // / @}
        drawContent(canvas, content, width, height, entry.rotation);
        if ((content instanceof FadeInTexture) &&
                ((FadeInTexture) content).isAnimating()) {
            renderRequestFlags |= SlotView.RENDER_MORE_FRAME;
        }
        /// M: [FEATURE.ADD] VTSP @{
        }
        /// @}

        /// M: [FEATURE.MODIFY] Livephoto && slow motion @{
        /*
        if (entry.mediaType == MediaObject.MEDIA_TYPE_VIDEO) {
            drawVideoOverlay(canvas, width, height);
        }
        */
        if (entry.mediaType == MediaObject.MEDIA_TYPE_VIDEO
                && entry.item.getMediaData() != null
                && !(entry.item.getMediaData().isLivePhoto)
                && !(entry.item.getMediaData().isSlowMotion)) {
            drawVideoOverlay(canvas, width, height);
        }
        /// @}

        if (entry.isPanorama) {
            drawPanoramaIcon(canvas, width, height);
        }

        renderRequestFlags |= renderOverlay(canvas, index, entry, width, height);
        /// M: [FEATURE.ADD] @{
        FeatureHelper.drawMicroThumbOverLay(mActivity, canvas, width, height, entry.item);
        /// @}

        return renderRequestFlags;
    }

    private int renderOverlay(GLCanvas canvas, int index,
            AlbumSlidingWindow.AlbumEntry entry, int width, int height) {
        int renderRequestFlags = 0;
        if (mPressedIndex == index) {
            if (mAnimatePressedUp) {
                /// M: [PERF.ADD] add for performance test case@{
                if (!mHasShowLog && sPerformanceCaseRunning) {
                    Log.d(TAG, "[CMCC Performance test][Gallery2][Gallery] load 1M image time start ["
                                    + System.currentTimeMillis() + "]");
                    mHasShowLog = true;
                }
                /// @}
                drawPressedUpFrame(canvas, width, height);
                renderRequestFlags |= SlotView.RENDER_MORE_FRAME;
                if (isPressedUpFrameFinished()) {
                    mAnimatePressedUp = false;
                    mPressedIndex = -1;
                }
            } else {
                drawPressedFrame(canvas, width, height);
            }
        } else if ((entry.path != null) && (mHighlightItemPath == entry.path)) {
            drawSelectedFrame(canvas, width, height);
        } else if (mInSelectionMode && mSelectionManager.isItemSelected(entry.path)) {
            drawSelectedFrame(canvas, width, height);
        }
        return renderRequestFlags;
    }

    private class MyDataModelListener implements AlbumSlidingWindow.Listener {
        @Override
        public void onContentChanged() {
            /// M: [FEATURE.ADD] VTSP @{
            updateEngineData();
            /// @}
            mSlotView.invalidate();
        }

        @Override
        public void onSizeChanged(int size) {
            mSlotView.setSlotCount(size);
            /// M: [BUG.ADD] photo won't disappear when it has been deleted @{
            mSlotView.invalidate();
            /// @}
        }
    }

    public void resume() {
        mDataWindow.resume();
        /// M: [FEATURE.ADD] VTSP @{
        if (mSupportVTSP) {
            mPlayEngine.resume();
        }
        updateEngineData();
        /// @}
    }

    public void pause() {
        mDataWindow.pause();
        /// M: [FEATURE.ADD] VTSP @{
        if (mSupportVTSP) {
            mPlayEngine.pause();
        }
        /// @}
    }

    @Override
    public void prepareDrawing() {
        mInSelectionMode = mSelectionManager.inSelectionMode();
    }

    @Override
    public void onVisibleRangeChanged(int visibleStart, int visibleEnd) {
        if (mDataWindow != null) {
            mDataWindow.setActiveWindow(visibleStart, visibleEnd);
            /// M: [FEATURE.ADD] VTSP @{
            updateEngineData();
            /// @}
        }
    }

    @Override
    public void onSlotSizeChanged(int width, int height) {
        // Do nothing
    }

    public void setSlotFilter(SlotFilter slotFilter) {
        mSlotFilter = slotFilter;
    }
    
  //********************************************************************
  //*                              MTK                                 *
  //********************************************************************
    ///[FEATURE.ADD] VTSP 
    private boolean mSupportVTSP = false;
    // NOTICE: when mSupportVTSP is false, mPlayEngine will be null
    private PlayEngine mPlayEngine;

    private final int mPlayCount = PhotoPlayFacade.getThumbPlayCount();

    public void onFrameAvailable(int index) {
        mSlotView.invalidate();
    }

    private void updateEngineData() {
        if (!mSupportVTSP || !mDataWindow.isAllActiveSlotsFilled()) {
            return;
        }
        MediaData[] data = new MediaData[mPlayCount];
        MediaItem tempItem = null;
        int start = mDataWindow.getActiveStart();
        for (int i = 0; i < mPlayCount; i++) {
            tempItem = mDataWindow.getMediaItem(start + i);
            if (tempItem != null) {
                data[i] = tempItem.getMediaData();
            } else {
                data[i] = null;
            }
        }
        mPlayEngine.updateData(data);
    }
    
    private boolean drawCurrentSlotDynamic(AlbumSlidingWindow.AlbumEntry entry, GLCanvas canvas, int index,
            int width, int height, int rotation) {
        if (!mSupportVTSP || entry.item == null) return false;
        // draw dynamic
        canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
        if (rotation != 0) {
            canvas.translate(width / 2, height / 2);
            canvas.rotate(rotation, 0, 0, 1);
            canvas.translate(-width / 2, -height / 2);
        }
        boolean hasDraw;
        if (rotation == 90 || rotation == 270) {
            hasDraw = mPlayEngine.draw(entry.item.getMediaData(), index - mDataWindow.getActiveStart(),
                    canvas.getMGLCanvas(), height, width);
        } else {
            hasDraw = mPlayEngine.draw(entry.item.getMediaData(), index - mDataWindow.getActiveStart(),
                    canvas.getMGLCanvas(), width, height);
        }
        canvas.restore();
        return hasDraw;
    }
}
