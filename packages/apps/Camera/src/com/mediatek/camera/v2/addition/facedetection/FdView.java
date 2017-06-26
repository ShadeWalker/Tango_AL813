/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 * 
 * MediaTek Inc. (C) 2015. All rights reserved.
 * 
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

package com.mediatek.camera.v2.addition.facedetection;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.hardware.Camera.Face;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

public class FdView extends View {
    private static final String TAG = FdView.class.getSimpleName();
    private static final boolean DEBUG = false;
    private final Context mContext;
    private boolean mMirror = false;

    private int mDisplaycompensation = 0;
    private int mLastFaceNum;

    private FdUtil mFaceDetectionUtil;
    private Face[] mFaces;
    private Drawable mFaceIndicator;
    private Drawable[] mFaceStatusIndicator;

    private Point mPreviewBeginingPoint = new Point();
    private int mCropRegionLeft;
    private int mCropRegionTop;
    private int mCropRegionWidth;
    private int mCropRegionHeight;
    private int mPreviewWidth;
    private int mPreviewHeigth;
    private int mBufferWidth;
    private int mBufferHeight;
    private int mBufferCenterX;
    private int mBufferCenterY;

    private int mDisplayRotation = 0;
    private boolean mIsFbEnabled = false;

    private RectF mRect = new RectF();

    public FdView(Context context, AttributeSet set) {
        super(context, set);
        mContext = context;
        mFaceDetectionUtil = new FdUtil((Activity) context);
        mFaceStatusIndicator = mFaceDetectionUtil.getViewDrawable();
        mFaceIndicator = mFaceStatusIndicator[0];
    }
    
    public void onPreviewAreaChanged(RectF previewRect) {
        mPreviewBeginingPoint.x = Math.round(previewRect.left);
        mPreviewBeginingPoint.y = Math.round(previewRect.top);
        mBufferWidth = Math.round(previewRect.width());
        mBufferHeight = Math.round(previewRect.height());
        mPreviewWidth = Math.round(mBufferWidth + mPreviewBeginingPoint.x * 2);
        mPreviewHeigth = Math.round(mBufferHeight + mPreviewBeginingPoint.y * 2);
        mBufferCenterX = mPreviewBeginingPoint.x + mBufferWidth / 2;
        mBufferCenterY = mPreviewBeginingPoint.y + mBufferHeight / 2;
        Log.i(TAG, "[onPeviewAreaChanged],previewRect = " + previewRect.toShortString()
                + ",mPreviewBeginingPoint.x = " + mPreviewBeginingPoint.x
                + ",mPreviewBeginingPoint.y = " + mPreviewBeginingPoint.y + ",mBufferWidth = "
                + mBufferWidth + ",mBufferHeight = " + mBufferHeight + ",mPreviewWidth = "
                + mPreviewWidth + ",mPreviewHeigth = " + mPreviewHeigth + " mBufferCenterX = "
                + mBufferCenterX + " mBufferCenterY = " + mBufferCenterY);
    }

    public void onOrientationChanged(int orientation) {
        updateDisplayRotation(mContext);
    }

    public void setMirror(boolean mirror) {
        mMirror = mirror;
    }

    public void setFbEnabled(boolean fbEnabled) {
        mIsFbEnabled = fbEnabled;
    }

    public void setFaces(int[] ids, int[] landmarks, Rect[] rectangles, byte[] scores,
            Point[][] pointsInfo, Rect cropRegion) {
        int length = 0;
        if (scores != null) {
            length = scores.length;
        }
        Face[] faces = new Face[length];
        mCropRegionLeft = cropRegion.left;
        mCropRegionTop = cropRegion.top;
        mCropRegionWidth = cropRegion.width();
        mCropRegionHeight = cropRegion.height();
        // convert the API2 Face to API 1 Face
        // landmark current not use,but the value may be null when FD mode
        // is Simple
        if (scores != null && pointsInfo != null) {
            for (int i = 0; i < length; i++) {
                Face tempFace = new Face();
                if (pointsInfo[i][0] != null) {
                    tempFace.leftEye = pointsInfo[i][0];
                }
                if (pointsInfo[i][1] != null) {
                    tempFace.rightEye = pointsInfo[i][1];
                }
                if (pointsInfo[i][2] != null) {
                    tempFace.mouth = pointsInfo[i][2];
                }

                if (rectangles[i] != null) {
                    tempFace.rect = rectangles[i];
                }
                tempFace.score = scores[i];
                faces[i] = tempFace;
            }
        }
        faceDetected(faces);
    }

    public void clear() {
        mFaces = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // use Camera3 method
        if (mFaces != null && mFaces.length > 0) {
            if (mIsFbEnabled) {
                mFaceIndicator = mFaceStatusIndicator[mFaceStatusIndicator.length - 1];
                if (DEBUG) {
                    Log.i(TAG, "[onDraw] faceIndicator will be set to face beauty");
                }
            } else {
                mFaceIndicator = mFaceStatusIndicator[0];
            }
            int width = mBufferWidth > mBufferHeight ? mBufferWidth : mBufferHeight;
            int height = mBufferWidth > mBufferHeight ? mBufferHeight : mBufferWidth;
            int translateWidthValue = 0;
            int translateHeightValue = 0;
            float previewRatio = (float) width / (float) height;
            float cropRegionRatio = (float) mCropRegionWidth / (float) mCropRegionHeight;
            float faceRatio = 0f;
            if (DEBUG) {
                Log.i(TAG, "[onDraw] width = " + width + ", height = " + height + ",mMirror = "
                        + mMirror + ",mDisplayOrientation = " + mDisplaycompensation
                        + ",previewRatio = " + previewRatio + ",cropRegionRatio = "
                        + cropRegionRatio);
            }
            if (previewRatio > cropRegionRatio) {
                faceRatio = (float) mCropRegionWidth / (float) width;
                translateHeightValue = Math.round((mCropRegionHeight - height * faceRatio) / 2);
                Log.i(TAG, "[onDraw]  preview ratio > cropRegionRatio ,faceRatio = " + faceRatio
                        + ",translateHeightValue = " + translateHeightValue);
            } else {
                faceRatio = (float) mCropRegionHeight / (float) height;
                translateWidthValue = Math.round((mCropRegionWidth - width * faceRatio) / 2);
                Log.i(TAG, "[onDraw]  preview ratio < cropRegionRatio ,faceRatio = " + faceRatio
                        + ",translateWidthValue = " + translateWidthValue);
            }

            for (int i = 0; i < mFaces.length; i++) {
                // translate the face to the buffer coordinate
                mFaces[i].rect.offset(-mCropRegionLeft, -mCropRegionTop);
                mFaces[i].rect.offset(-translateWidthValue, -translateHeightValue);
                // scale the face indicator
                mFaces[i].rect.left = (int) ((mFaces[i].rect.left) / faceRatio);
                mFaces[i].rect.top = (int) ((mFaces[i].rect.top) / faceRatio);
                mFaces[i].rect.right = (int) ((mFaces[i].rect.right) / faceRatio);
                mFaces[i].rect.bottom = (int) ((mFaces[i].rect.bottom) / faceRatio);
                mRect.set(mFaces[i].rect);
                // Rotate the position so it looks correctly in all
                // orientations. --> begin
                float rectWidth = mRect.right - mRect.left;
                float rectHeight = mRect.bottom - mRect.top;
                mFaceDetectionUtil.dumpRect(mRect, "Original rect");
                if (mDisplaycompensation == 0) {
                    float temp = mRect.left;
                    mRect.left = mPreviewWidth - mRect.bottom;
                    mRect.top = temp;
                    mRect.right = mRect.left + rectHeight;
                    mRect.bottom = mRect.top + rectWidth;
                    mRect.offset(mPreviewBeginingPoint.x, mPreviewBeginingPoint.y);
                } else if (mDisplaycompensation == 180) {
                    float temp = mRect.top;
                    mRect.left = temp;
                    mRect.top = mPreviewHeigth - mRect.right;
                    mRect.right = mRect.left + rectHeight;
                    mRect.bottom = mRect.top + rectWidth;
                    mRect.offset(mPreviewBeginingPoint.x, mPreviewBeginingPoint.y);
                } else if (mDisplaycompensation == 270) {
                    mRect.left = mPreviewWidth - mRect.right;
                    mRect.top = mPreviewHeigth - mRect.bottom;
                    mRect.right = mRect.left + rectWidth;
                    mRect.bottom = mRect.top + rectHeight;
                    mRect.offset(-mPreviewBeginingPoint.x, -mPreviewBeginingPoint.y);
                } else if (mDisplaycompensation == 90) {
                    mRect.offset(mPreviewBeginingPoint.x, mPreviewBeginingPoint.y);
                }
                // rotate the face position by the buffer center.
                if (mMirror) {
                    rectWidth = mRect.right - mRect.left;
                    rectHeight = mRect.bottom - mRect.top;
                    if (mDisplaycompensation == 90 || mDisplaycompensation == 270) {
                        mRect.left = mRect.right + 2 * (mBufferCenterX - mRect.right);
                        mRect.right = mRect.left + rectWidth;
                    } else if (mDisplaycompensation == 0 || mDisplaycompensation == 180) {
                        mRect.top = mRect.bottom + 2 * (mBufferCenterY - mRect.bottom);
                        mRect.bottom = mRect.top + rectHeight;
                    }
                }
                mFaceDetectionUtil.dumpRect(mRect, "Transformed rect");
                // Rotate the position so it looks correctly in all
                // orientations. --> end
                mFaceIndicator.setBounds(Math.round(mRect.left), Math.round(mRect.top),
                        Math.round(mRect.right), Math.round(mRect.bottom));
                mFaceIndicator.draw(canvas);
            }
            canvas.save();
            canvas.restore();
        }
        super.onDraw(canvas);
    }
    
    private void faceDetected(Face[] faces) {

        mFaces = faces;
        if (faces != null) {
            int num = mFaces.length;
            if (DEBUG) {
                Log.i(TAG, "[onFaceDetected],num of face = " + num + ",mLastFaceNum = "
                        + mLastFaceNum);
            }
            if (0 == num && 0 == mLastFaceNum) {
                return;
            }
            mLastFaceNum = num;
        }
        invalidate();
    }
    
    private void updateDisplayRotation(Context context) {
        WindowManager windowManager = (WindowManager) context
                .getSystemService(Service.WINDOW_SERVICE);
        int rotation = windowManager.getDefaultDisplay().getRotation();
        switch (rotation) {
        case Surface.ROTATION_0:
            mDisplayRotation = 0;
            break;
        case Surface.ROTATION_90:
            mDisplayRotation = 90;
            break;
        case Surface.ROTATION_180:
            mDisplayRotation = 180;
            break;
        case Surface.ROTATION_270:
            mDisplayRotation = 270;
            break;

        default:
            break;
        }
        mDisplaycompensation = mDisplayRotation;
    }
}
