/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.printspooler.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;
///M: @{
import android.view.ViewGroup;
import android.animation.LayoutTransition;
///M: @}

public class EmbeddedContentContainer extends FrameLayout {
    public interface OnSizeChangeListener {
        public void onSizeChanged(int width, int height);
    }

    private OnSizeChangeListener mSizeChangeListener;

    public EmbeddedContentContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        ///M: There is a <code>RecyclerView</code> placed under this
        //      <code>FrameLayout</code>, however there are lots of
        //      limitations on scroll operation on <code>RecyclerView</code>,
        //      to leave <code>RecyclerView</code> in a safe operation, all
        //      animations should be disabled first. @{
        LayoutTransition mLayoutTransition = getLayoutTransition();
        if (mLayoutTransition != null) {
        	setLayoutTransition(null);
        }
        ///M: @}
    }

    public void setOnSizeChangeListener(OnSizeChangeListener listener) {
        mSizeChangeListener = listener;
    }

    @Override
    protected void onSizeChanged(int newWidth, int newHeight, int oldWidth, int oldHeight) {
        super.onSizeChanged(newWidth, newHeight, oldWidth, oldHeight);
        if (mSizeChangeListener != null) {
            mSizeChangeListener.onSizeChanged(newWidth, newHeight);
        }
    }
}
