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
 * MediaTek Inc. (C) 2014. All rights reserved.
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

package com.mediatek.camera.mode.mav;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.mediatek.camera.util.Log;

public class MavEffectView extends View {
    private static final String TAG = "MavEffectView";
    
    protected final Rect mCentetRect;
    protected final Rect mTopRect;
    protected final Rect mBottomRect;
    protected final Rect mLeftRect;
    protected final Rect mRightRect;
    protected final Paint mPaint;
    protected final Paint mBackgroundPaint;
    
    private static final int RECT_STROKE_WIDTH = 6;
    
    private int mPreviewLeft;
    private int mPreviewTop;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private int mFrameWidth;
    private int mFrameHeight;
    
    public MavEffectView(Context context, int previewLeft, int previewTop, int previewWidth,
            int previewHeight, int frameWidth, int frameHeight) {
        super(context);
        Log.i(TAG, "[MavEffectView]new,previewLeft = " + previewLeft + ",previewTop = " + previewTop + 
                ",previewWidth = " + previewWidth + ",previewHeight = " + previewHeight + ",frameWidth = " + frameWidth
                + ",frameHeight = " + frameHeight);
        
        mPreviewLeft = previewLeft;
        mPreviewTop = previewTop;
        mPreviewWidth = previewWidth;
        mPreviewHeight = previewHeight;
        mFrameWidth = frameWidth;
        mFrameHeight = frameHeight;
        
        mCentetRect = getCenterRect();
        mTopRect = getTopRect();
        mBottomRect = getBottomRect();
        mLeftRect = getLeftRect();
        mRightRect = getRightRect();
        
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(0xFF0099CC);
        mPaint.setStyle(Style.STROKE);
        mPaint.setStrokeWidth((float) RECT_STROKE_WIDTH);
        
        mBackgroundPaint = new Paint();
        mBackgroundPaint.setAntiAlias(true);
        mBackgroundPaint.setColor(0x8F000000);
        mBackgroundPaint.setStyle(Style.FILL);
        mBackgroundPaint.setStrokeWidth(0);
        mBackgroundPaint.setAlpha(100);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Log.i(TAG, "[onDraw]...");
        canvas.drawRect(mCentetRect, mPaint);
        canvas.drawRect(mTopRect, mBackgroundPaint);
        canvas.drawRect(mBottomRect, mBackgroundPaint);
        canvas.drawRect(mLeftRect, mBackgroundPaint);
        canvas.drawRect(mRightRect, mBackgroundPaint);
    }
    
    private Rect getCenterRect() {
        int left = (mPreviewWidth - mFrameWidth) / 2 + mPreviewLeft;
        int top = (mPreviewHeight - mFrameHeight) / 2 + mPreviewTop;
        int right = (mPreviewWidth - mFrameWidth) / 2 + mFrameWidth + mPreviewLeft;
        int bottom = (mPreviewHeight - mFrameHeight) / 2 + mFrameHeight + mPreviewTop;
        Rect rect = new Rect(left, top, right, bottom);
        return rect;
    }
    
    private Rect getTopRect() {
        Rect rect = new Rect(mPreviewLeft, mPreviewTop, mPreviewWidth + mPreviewLeft,
                (mPreviewHeight - mFrameHeight) / 2 - RECT_STROKE_WIDTH / 2 + mPreviewTop);
        return rect;
    }
    
    private Rect getBottomRect() {
        Rect rect = new Rect(mPreviewLeft, (mPreviewHeight - mFrameHeight) / 2 + mFrameHeight
                + RECT_STROKE_WIDTH / 2 + mPreviewTop, mPreviewWidth + mPreviewLeft, mPreviewHeight
                + mPreviewTop);
        return rect;
    }
    
    private Rect getLeftRect() {
        Rect rect = new Rect(mPreviewLeft, (mPreviewHeight - mFrameHeight) / 2 - RECT_STROKE_WIDTH
                / 2 + mPreviewTop, (mPreviewWidth - mFrameWidth) / 2 - RECT_STROKE_WIDTH / 2
                + mPreviewLeft, (mPreviewHeight - mFrameHeight) / 2 + mFrameHeight
                + RECT_STROKE_WIDTH / 2 + mPreviewTop);
        return rect;
    }
    
    private Rect getRightRect() {
        Rect rect = new Rect((mPreviewWidth - mFrameWidth) / 2 + mFrameWidth + RECT_STROKE_WIDTH
                / 2 + mPreviewLeft, (mPreviewHeight - mFrameHeight) / 2 - RECT_STROKE_WIDTH / 2
                + mPreviewTop, mPreviewWidth + mPreviewLeft, (mPreviewHeight - mFrameHeight) / 2
                + mFrameHeight + RECT_STROKE_WIDTH / 2 + mPreviewTop);
        Log.i(TAG, "[getRightRect]rect = " + rect);
        return rect;
    }
}