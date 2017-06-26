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
package com.android.internal.widget;

import com.android.internal.R;

import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.widget.ActionMenuPresenter;
import android.widget.ActionMenuView;
import com.android.internal.view.menu.MenuBuilder;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.res.Configuration;

/**
 * @hide
 */
public class ActionBarContextView extends AbsActionBarView implements AnimatorListener {
    private static final String TAG = "ActionBarContextView";

    private CharSequence mTitle;
    private CharSequence mSubtitle;

    private View mClose;
    private View mCustomView;
    private LinearLayout mTitleLayout;
    private TextView mTitleView;
    private TextView mSubtitleView;
    private int mTitleStyleRes;
    private int mSubtitleStyleRes;
    private Drawable mSplitBackground;
    private boolean mTitleOptional;
    private int mCloseItemLayout;

    private Animator mCurrentAnimation;
    private boolean mAnimateInOnLayout;
    private int mAnimationMode;

    private static final int ANIMATE_IDLE = 0;
    private static final int ANIMATE_IN = 1;
    private static final int ANIMATE_OUT = 2;

    private static final int MSG_RELAYOUT = 0;

    private Handler mHandler;
    
    public ActionBarContextView(Context context) {
        this(context, null);
    }
    
    public ActionBarContextView(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.actionModeStyle);
    }
    
    public ActionBarContextView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ActionBarContextView(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.ActionMode, defStyleAttr, defStyleRes);
        setBackground(a.getDrawable(
                com.android.internal.R.styleable.ActionMode_background));
        mTitleStyleRes = a.getResourceId(
                com.android.internal.R.styleable.ActionMode_titleTextStyle, 0);
        mSubtitleStyleRes = a.getResourceId(
                com.android.internal.R.styleable.ActionMode_subtitleTextStyle, 0);

        mContentHeight = a.getLayoutDimension(
                com.android.internal.R.styleable.ActionMode_height, 0);

        mSplitBackground = a.getDrawable(
                com.android.internal.R.styleable.ActionMode_backgroundSplit);

        mCloseItemLayout = a.getResourceId(
                com.android.internal.R.styleable.ActionMode_closeItemLayout,
                R.layout.action_mode_close_item);

        a.recycle();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mActionMenuPresenter != null) {
            mActionMenuPresenter.hideOverflowMenu();
            mActionMenuPresenter.hideSubMenus();
        }
    }

    @Override
    public void setSplitToolbar(boolean split) {
        if (mSplitActionBar != split) {
            if (mActionMenuPresenter != null) {
                // Mode is already active; move everything over and adjust the menu itself.
                final LayoutParams layoutParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                        LayoutParams.MATCH_PARENT);
                if (!split) {
                    mMenuView = (ActionMenuView) mActionMenuPresenter.getMenuView(this);
                    mMenuView.setBackground(null);
                    final ViewGroup oldParent = (ViewGroup) mMenuView.getParent();
                    if (oldParent != null) oldParent.removeView(mMenuView);
                    addView(mMenuView, layoutParams);
                } else {
                    // Allow full screen width in split mode.
                    mActionMenuPresenter.setWidthLimit(
                            getContext().getResources().getDisplayMetrics().widthPixels, true);
                    // No limit to the item count; use whatever will fit.
                    mActionMenuPresenter.setItemLimit(Integer.MAX_VALUE);
                    // Span the whole width
                    layoutParams.width = LayoutParams.MATCH_PARENT;
                    /* < DTS2015012107859 yinwenshuai/00211458 20150126 begin */
                    layoutParams.height = getMenuViewHeight(mContentHeight);
                    /* DTS2015012107859 yinwenshuai/00211458 20150126 end > */
                    mMenuView = (ActionMenuView) mActionMenuPresenter.getMenuView(this);
                    mMenuView.setBackground(mSplitBackground);
                    final ViewGroup oldParent = (ViewGroup) mMenuView.getParent();
                    if (oldParent != null) oldParent.removeView(mMenuView);
                    /* < DTS2014120105919 guyue/00295151 20141204 begin */
                    if (mSplitView != null) {
                        mSplitView.addView(mMenuView, layoutParams);
					/* < DTS2015042301272 zhangxinming/00181848 20150429 begin */
					} else if (mIgnoreHwActionModeStyle) {
						addView(mMenuView, layoutParams);
                    }
					/*   DTS2015042301272 zhangxinming/00181848 20150429 end > */
                   /*   DTS2014120105919 guyue/00295151 20141204 end > */
                }
				/* < DTS2015042301272 zhangxinming/00181848 20150429 begin */
                setTitleLayoutVisible();
                /*   DTS2015042301272 zhangxinming/00181848 20150429 end > */
            }
            super.setSplitToolbar(split);
        }
    }

    public void setContentHeight(int height) {
        mContentHeight = height;
    }

    public void setCustomView(View view) {
        if (mCustomView != null) {
            removeView(mCustomView);
        }
        mCustomView = view;
        if (mTitleLayout != null) {
            removeView(mTitleLayout);
            mTitleLayout = null;
        }
        if (view != null) {
            addView(view);
        }
        requestLayout();
    }

    public void setTitle(CharSequence title) {
        mTitle = title;
        initTitle();
    }

    public void setSubtitle(CharSequence subtitle) {
        mSubtitle = subtitle;
        initTitle();
    }

    public CharSequence getTitle() {
        return mTitle;
    }

    public CharSequence getSubtitle() {
        return mSubtitle;
    }

    /* < DTS2014111801378 zhangxinming/00181848 20141118 begin */
    protected void initTitle() {
    /*   DTS2014111801378 zhangxinming/00181848 20141118 end > */
        if (mTitleLayout == null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            inflater.inflate(R.layout.action_bar_title_item, this);
            mTitleLayout = (LinearLayout) getChildAt(getChildCount() - 1);
            mTitleView = (TextView) mTitleLayout.findViewById(R.id.action_bar_title);
            mSubtitleView = (TextView) mTitleLayout.findViewById(R.id.action_bar_subtitle);
            if (mTitleStyleRes != 0) {
                mTitleView.setTextAppearance(mContext, mTitleStyleRes);
            }
            if (mSubtitleStyleRes != 0) {
                mSubtitleView.setTextAppearance(mContext, mSubtitleStyleRes);
            }

            if (mHandler == null) {
                mHandler = new Handler() {
                    public void handleMessage(Message msg) {
                        /// M: it may not have parent before attach window
                        ViewParent viewParent = ActionBarContextView.this.getParent();
                        if (viewParent != null) {
                            viewParent.requestLayout();
                        }
                    }
                };
            }
        }

        mTitleView.setText(mTitle);
        mSubtitleView.setText(mSubtitle);

        final boolean hasTitle = !TextUtils.isEmpty(mTitle);
        final boolean hasSubtitle = !TextUtils.isEmpty(mSubtitle);
        mSubtitleView.setVisibility(hasSubtitle ? VISIBLE : GONE);
        mTitleLayout.setVisibility(hasTitle || hasSubtitle ? VISIBLE : GONE);

        /// M: it can't re-layout during performTraversal
        mHandler.removeMessages(MSG_RELAYOUT);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_RELAYOUT));

        if (mTitleLayout.getParent() == null) {
            addView(mTitleLayout);
        }
		/* < DTS2015042301272 zhangxinming/00181848 20150429 begin */
		setTitleLayoutVisible();
		/*   DTS2015042301272 zhangxinming/00181848 20150429 end > */

    }

    public void initForMode(final ActionMode mode) {
        if (mClose == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            mClose = inflater.inflate(mCloseItemLayout, this, false);
            addView(mClose);
        } else if (mClose.getParent() == null) {
            addView(mClose);
        }

        View closeButton = mClose.findViewById(R.id.action_mode_close_button);
        closeButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mode.finish();
            }
        });

        final MenuBuilder menu = (MenuBuilder) mode.getMenu();
        if (mActionMenuPresenter != null) {
            mActionMenuPresenter.dismissPopupMenus();
        }
		//huawe fix
        //mActionMenuPresenter = new ActionMenuPresenter(mContext);
		/* < DTS2015042301272 zhangxinming/00181848 20150429 begin */
		//So tricky...,mIgnoreHwActionModeStyle used for browser only, see PhoneWindow.ignoreHwActionModeBar
		//HQ_zhangteng modified for Browser at 2015-08-31 begin
		if (mIgnoreHwActionModeStyle){//mIgnoreHwActionModeStyle) {
		        int itemStyleId = this.getResources().getIdentifier("androidhwext:layout/action_menu_item_layout_emui", null, null);
			mActionMenuPresenter = new ActionMenuPresenter(mContext, com.android.internal.R.layout.action_menu_layout,itemStyleId);
		//HQ_zhangteng modified for Browser at 2015-08-31 end
		} else {
			 mActionMenuPresenter = new ActionMenuPresenter(mContext);
		}
		/*   DTS2015042301272 zhangxinming/00181848 20150429 end > */
		//end 

		
        mActionMenuPresenter.setReserveOverflow(true);

        final LayoutParams layoutParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT);
		//huawei fix
        //if (!mSplitActionBar) {
		/* < DTS2015042301272 zhangxinming/00181848 20150429 begin */
		if (mIgnoreHwActionModeStyle) {
			//mActionMenuPresenter.setItemLimit(6);
			mClose.setVisibility(GONE);
			mActionMenuPresenter.setIgnoreHwActionModeStyle();
			//HQ_zhangteng added for Browser at 2015-09-04 begin
			mActionMenuPresenter.setWidthLimit(
                        getContext().getResources().getDisplayMetrics().widthPixels, true);
			//HQ_zhangteng added for Browser at 2015-09-04 end
			menu.addMenuPresenter(mActionMenuPresenter);
			mMenuView = (ActionMenuView) mActionMenuPresenter.getMenuView(this);
			mMenuView.setBackgroundDrawable(null);
			addView(mMenuView, layoutParams);
		/*   DTS2015042301272 zhangxinming/00181848 20150429 end > */
		} else  if (!mSplitActionBar) {
		//end
            menu.addMenuPresenter(mActionMenuPresenter, mPopupContext);
            mMenuView = (ActionMenuView) mActionMenuPresenter.getMenuView(this);
            mMenuView.setBackground(null);
            addView(mMenuView, layoutParams);
        } else {
            // Allow full screen width in split mode.
            mActionMenuPresenter.setWidthLimit(
                    getContext().getResources().getDisplayMetrics().widthPixels, true);
            // No limit to the item count; use whatever will fit.
            mActionMenuPresenter.setItemLimit(Integer.MAX_VALUE);
            // Span the whole width
            layoutParams.width = LayoutParams.MATCH_PARENT;
            layoutParams.height = mContentHeight;
            menu.addMenuPresenter(mActionMenuPresenter, mPopupContext);
            mMenuView = (ActionMenuView) mActionMenuPresenter.getMenuView(this);
            mMenuView.setBackgroundDrawable(mSplitBackground);
            /* < DTS2014111801378 zhangxinming/00181848 20141118 begin */
            if (mSplitView != null) {
                mSplitView.addView(mMenuView, layoutParams);
            }
            /*   DTS2014111801378 zhangxinming/00181848 20141118 end > */
        }

        mAnimateInOnLayout = true;
    }

    public void closeMode() {
        if (mAnimationMode == ANIMATE_OUT) {
            // Called again during close; just finish what we were doing.
            return;
        }
        if (mClose == null) {
            killMode();
            return;
        }

        finishAnimation();
        mAnimationMode = ANIMATE_OUT;
        mCurrentAnimation = makeOutAnimation();
        mCurrentAnimation.start();
    }

    private void finishAnimation() {
        final Animator a = mCurrentAnimation;
        if (a != null) {
            mCurrentAnimation = null;
            a.end();
        }
    }

    public void killMode() {
        finishAnimation();
        removeAllViews();
        if (mSplitView != null) {
            mSplitView.removeView(mMenuView);
        }
        mCustomView = null;
        mMenuView = null;
        mAnimateInOnLayout = false;
    }

    @Override
    public boolean showOverflowMenu() {
        if (mActionMenuPresenter != null) {
            return mActionMenuPresenter.showOverflowMenu();
        }
        return false;
    }

    @Override
    public boolean hideOverflowMenu() {
        if (mActionMenuPresenter != null) {
            return mActionMenuPresenter.hideOverflowMenu();
        }
        return false;
    }

    @Override
    public boolean isOverflowMenuShowing() {
        if (mActionMenuPresenter != null) {
            return mActionMenuPresenter.isOverflowMenuShowing();
        }
        return false;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        // Used by custom views if they don't supply layout params. Everything else
        // added to an ActionBarContextView should have them already.
        return new MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        if (widthMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException(getClass().getSimpleName() + " can only be used " +
                    "with android:layout_width=\"match_parent\" (or fill_parent)");
        }

        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (heightMode == MeasureSpec.UNSPECIFIED) {
            throw new IllegalStateException(getClass().getSimpleName() + " can only be used " +
                    "with android:layout_height=\"wrap_content\"");
        }
        
        final int contentWidth = MeasureSpec.getSize(widthMeasureSpec);

        int maxHeight = mContentHeight > 0 ?
                mContentHeight : MeasureSpec.getSize(heightMeasureSpec);

        final int verticalPadding = getPaddingTop() + getPaddingBottom();
        int availableWidth = contentWidth - getPaddingLeft() - getPaddingRight();
        final int height = maxHeight - verticalPadding;
        final int childSpecHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST);
        //huawei fix
		//if (mClose != null) {
		/* < DTS2015042301272 zhangxinming/00181848 20150429 begin */
		if (mClose != null && mClose.getVisibility() == VISIBLE) {
		/*   DTS2015042301272 zhangxinming/00181848 20150429 end > */
		//huawei end
            availableWidth = measureChildView(mClose, availableWidth, childSpecHeight, 0);
            MarginLayoutParams lp = (MarginLayoutParams) mClose.getLayoutParams();
            availableWidth -= lp.leftMargin + lp.rightMargin;
        }

        if (mMenuView != null && mMenuView.getParent() == this) {
            availableWidth = measureChildView(mMenuView, availableWidth,
                    childSpecHeight, 0);
        }

        if (mTitleLayout != null && mCustomView == null) {
            if (mTitleOptional) {
                final int titleWidthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                mTitleLayout.measure(titleWidthSpec, childSpecHeight);
                final int titleWidth = mTitleLayout.getMeasuredWidth();
                final boolean titleFits = titleWidth <= availableWidth;
                if (titleFits) {
                    availableWidth -= titleWidth;
                }
                mTitleLayout.setVisibility(titleFits ? VISIBLE : GONE);
            } else {
                availableWidth = measureChildView(mTitleLayout, availableWidth, childSpecHeight, 0);
            }
        }

        if (mCustomView != null) {
            ViewGroup.LayoutParams lp = mCustomView.getLayoutParams();
            final int customWidthMode = lp.width != LayoutParams.WRAP_CONTENT ?
                    MeasureSpec.EXACTLY : MeasureSpec.AT_MOST;
            final int customWidth = lp.width >= 0 ?
                    Math.min(lp.width, availableWidth) : availableWidth;
            final int customHeightMode = lp.height != LayoutParams.WRAP_CONTENT ?
                    MeasureSpec.EXACTLY : MeasureSpec.AT_MOST;
            final int customHeight = lp.height >= 0 ?
                    Math.min(lp.height, height) : height;
            mCustomView.measure(MeasureSpec.makeMeasureSpec(customWidth, customWidthMode),
                    MeasureSpec.makeMeasureSpec(customHeight, customHeightMode));
        }

        if (mContentHeight <= 0) {
            int measuredHeight = 0;
            final int count = getChildCount();
            for (int i = 0; i < count; i++) {
                View v = getChildAt(i);
                int paddedViewHeight = v.getMeasuredHeight() + verticalPadding;
                if (paddedViewHeight > measuredHeight) {
                    measuredHeight = paddedViewHeight;
                }
            }
            setMeasuredDimension(contentWidth, measuredHeight);
        } else {
            setMeasuredDimension(contentWidth, maxHeight);
        }
    }

    private Animator makeInAnimation() {
        mClose.setTranslationX(-mClose.getWidth() -
                ((MarginLayoutParams) mClose.getLayoutParams()).leftMargin);
        ObjectAnimator buttonAnimator = ObjectAnimator.ofFloat(mClose, "translationX", 0);
        buttonAnimator.setDuration(200);
        buttonAnimator.addListener(this);
        buttonAnimator.setInterpolator(new DecelerateInterpolator());

        AnimatorSet set = new AnimatorSet();
        AnimatorSet.Builder b = set.play(buttonAnimator);

        if (mMenuView != null) {
            final int count = mMenuView.getChildCount();
            if (count > 0) {
                for (int i = count - 1, j = 0; i >= 0; i--, j++) {
                    View child = mMenuView.getChildAt(i);
                    child.setScaleY(0);
                    ObjectAnimator a = ObjectAnimator.ofFloat(child, "scaleY", 0, 1);
                    a.setDuration(300);
                    b.with(a);
                }
            }
        }

        return set;
    }

    private Animator makeOutAnimation() {
        ObjectAnimator buttonAnimator = ObjectAnimator.ofFloat(mClose, "translationX",
                -mClose.getWidth() - ((MarginLayoutParams) mClose.getLayoutParams()).leftMargin);
        buttonAnimator.setDuration(200);
        buttonAnimator.addListener(this);
        buttonAnimator.setInterpolator(new DecelerateInterpolator());

        AnimatorSet set = new AnimatorSet();
        AnimatorSet.Builder b = set.play(buttonAnimator);

        if (mMenuView != null) {
            final int count = mMenuView.getChildCount();
            if (count > 0) {
                for (int i = 0; i < 0; i++) {
                    View child = mMenuView.getChildAt(i);
                    child.setScaleY(0);
                    ObjectAnimator a = ObjectAnimator.ofFloat(child, "scaleY", 0);
                    a.setDuration(300);
                    b.with(a);
                }
            }
        }

        return set;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final boolean isLayoutRtl = isLayoutRtl();
        int x = isLayoutRtl ? r - l - getPaddingRight() : getPaddingLeft();
        final int y = getPaddingTop();
        final int contentHeight = b - t - getPaddingTop() - getPaddingBottom();
        
        if (mClose != null && mClose.getVisibility() != GONE) {
            MarginLayoutParams lp = (MarginLayoutParams) mClose.getLayoutParams();
            final int startMargin = (isLayoutRtl ? lp.rightMargin : lp.leftMargin);
            final int endMargin = (isLayoutRtl ? lp.leftMargin : lp.rightMargin);
            x = next(x, startMargin, isLayoutRtl);
            x += positionChild(mClose, x, y, contentHeight, isLayoutRtl);
            x = next(x, endMargin, isLayoutRtl);

            if (mAnimateInOnLayout) {
                mAnimationMode = ANIMATE_IN;
                mCurrentAnimation = makeInAnimation();
                mCurrentAnimation.start();
                mAnimateInOnLayout = false;
            }
        }

        if (mTitleLayout != null && mCustomView == null && mTitleLayout.getVisibility() != GONE) {
            x += positionChild(mTitleLayout, x, y, contentHeight, isLayoutRtl);
        }
        
        if (mCustomView != null) {
            x += positionChild(mCustomView, x, y, contentHeight, isLayoutRtl);
        }

        x = isLayoutRtl ? getPaddingLeft() : r - l - getPaddingRight();

        if (mMenuView != null) {
            x += positionChild(mMenuView, x, y, contentHeight, !isLayoutRtl);
        }
    }

    @Override
    public void onAnimationStart(Animator animation) {
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        if (mAnimationMode == ANIMATE_OUT) {
            killMode();
        }
        mAnimationMode = ANIMATE_IDLE;
    }

    @Override
    public void onAnimationCancel(Animator animation) {
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Action mode started
            event.setSource(this);
            event.setClassName(getClass().getName());
            event.setPackageName(getContext().getPackageName());
            event.setContentDescription(mTitle);
        } else {
            super.onInitializeAccessibilityEvent(event);
        }
    }

    public void setTitleOptional(boolean titleOptional) {
        if (titleOptional != mTitleOptional) {
            requestLayout();
        }
        mTitleOptional = titleOptional;
    }

    public boolean isTitleOptional() {
        return mTitleOptional;
    }

    /* < DTS2014111801378 zhangxinming/00181848 20141118 begin */
    public TextView getTitleView() {
        return mTitleView;
    }

    public void setTitleView(TextView view) {
        mTitleView = view;
    }

    public TextView getSubtitleView() {
        return mSubtitleView;
    }

    public void setSubtitleView(TextView view) {
        mSubtitleView = view;
    }

    public LinearLayout getTitleLayout() {
        return mTitleLayout;
    }

    public void setTitleLayout(LinearLayout ll) {
        mTitleLayout = ll;
    }

    protected void setAnimationMode(int mode) {
        mAnimationMode = mode;
    }

    protected int getAnimationMode() {
        return mAnimationMode;
    }
    /*   DTS2014111801378 zhangxinming/00181848 20141118 end > */

    /* < DTS2015012107859 yinwenshuai/00211458 20150126 begin */
    protected int getMenuViewHeight(int contentHeight) {
        return contentHeight;
    }
    /* DTS2015012107859 yinwenshuai/00211458 20150126 end > */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        boolean handled = super.dispatchTouchEvent(event);
        // M: consume touch event to prevent from dispatching it to parent or siblings
        if("huawei.com.android.internal.widget.HwActionBarContextView".equals(getClass().getName()))
            return handled;
		return true;
    }
	
    /* < DTS2015042301272 zhangxinming/00181848 20150429 begin */
    public void setIgnoreHwActionModeStyle() {
        mIgnoreHwActionModeStyle = true;
    }

    protected void setTitleLayoutVisible() {
        int orientation = getContext().getResources().getConfiguration().orientation;
        boolean isLand = (orientation == Configuration.ORIENTATION_LANDSCAPE);
        if (mTitleLayout != null) {
            if (mIgnoreHwActionModeStyle && isLand) {
                mTitleLayout.setVisibility(GONE);
            } else {
                mTitleLayout.setVisibility(VISIBLE);
            }
        }
    }
    protected boolean mIgnoreHwActionModeStyle;
    /*   DTS2015042301272 zhangxinming/00181848 20150429 end > */	
}
