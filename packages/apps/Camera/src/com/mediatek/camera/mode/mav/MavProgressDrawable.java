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
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.android.camera.R;

import com.mediatek.camera.util.Log;

public class MavProgressDrawable extends Drawable {
    private static final String TAG = "MavProgressDrawable";
    
    private View mAttachedView;
    private final Paint mPaint = new Paint();
    
    private Drawable mDotCleanBlock;
    private Drawable mDotDirtyBlock;
    private Drawable mArrowCleanBlock;
    private Drawable mArrowDirtyBlock;
    
    private int mNum = 0;
    private int mPadding = 0;
    
    public MavProgressDrawable(Context context, View view, int num, int padding) {
        Resources res = context.getResources();
        
        mAttachedView = view;
        
        mDotCleanBlock = res.getDrawable(R.drawable.mav_dot);
        mDotDirtyBlock = res.getDrawable(R.drawable.mav_dot_blue);
        mArrowCleanBlock = res.getDrawable(R.drawable.mav_arrow);
        mArrowDirtyBlock = res.getDrawable(R.drawable.mav_arrow_blue);
        
        mNum = num;
        mPadding = padding;
    }
    
    @Override
    protected boolean onLevelChange(int level) {
        Log.i(TAG, "[onLevelChange:]level = " + level);
        invalidateSelf();
        return true;
    }
    
    @Override
    public int getIntrinsicWidth() {
        int width = 0;
        int dotWidth = ((BitmapDrawable) mDotCleanBlock).getBitmap().getWidth();
        for (int i = 0, len = mNum; i < len - 1; i++) {
            width += dotWidth + mPadding;
        }
        int arrowWidth = ((BitmapDrawable) mArrowCleanBlock).getBitmap().getWidth();
        width += arrowWidth;
        Log.d(TAG, "[getIntrinsicWidth]width = " + width);
        
        return width;
    }
    
    public void setColorFilter(ColorFilter cf) {
        mPaint.setColorFilter(cf);
    }
    
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
    
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }
    
    public void draw(Canvas canvas) {
        int xoffset = 0;
        int level = getLevel();
        if (level == mNum) {
            for (int i = 0; i < mNum - 1; i++) {
                xoffset = drawBlock(canvas, mDotDirtyBlock, xoffset);
            }
            xoffset = drawBlock(canvas, mArrowDirtyBlock, xoffset);
        } else if (level == (mNum - 1)) {
            for (int i = 0; i < mNum - 1; i++) {
                xoffset = drawBlock(canvas, mDotDirtyBlock, xoffset);
            }
            xoffset = drawBlock(canvas, mArrowCleanBlock, xoffset);
        } else {
            for (int i = 0; i < level; i++) {
                xoffset = drawBlock(canvas, mDotDirtyBlock, xoffset);
            }
            
            for (int i = level; i < mNum - 1; i++) {
                xoffset = drawBlock(canvas, mDotCleanBlock, xoffset);
            }
            xoffset = drawBlock(canvas, mArrowCleanBlock, xoffset);
        }
    }
    
    private int drawBlock(Canvas canvas, Drawable drawable, int xoffset) {
        BitmapDrawable bd = (BitmapDrawable) drawable;
        int width = bd.getBitmap().getWidth();
        int height = bd.getBitmap().getHeight();
        int yoffset = (mAttachedView.getHeight() - height) / 2;
        drawable.setBounds(xoffset, yoffset, xoffset + width, yoffset + height);
        drawable.draw(canvas);
        xoffset += (width + mPadding);
        return xoffset;
    }
}
