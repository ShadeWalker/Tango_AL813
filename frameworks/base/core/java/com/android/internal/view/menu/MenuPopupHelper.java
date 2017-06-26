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

package com.android.internal.view.menu;

import android.content.Context;
import android.content.res.Resources;
import android.os.Parcelable;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.ListPopupWindow;
import android.widget.PopupWindow;

import java.util.ArrayList;
/*< DTS2014101007196 guyue/00295151 20141106 begin*/
/*< DTS2013121107644 tianjing/00102012 20131203 begin*/
/* <DTS2014030403122 litao/185177 huangbangbang/191767 20140304 begin */
import android.hwcontrol.HwWidgetFactory;
import android.hwcontrol.HwWidgetFactory.HwMenuPopupHelper;
/* DTS2014030403122 litao/185177 huangbangbang/191767 20140304 end> */
/* DTS2013121107644 tianjing/00102012 20131203 end>*/
/*< DTS2014101007196 guyue/00295151 20141106 end*/
/* <DTS2013122507276 wuweibin/00107028 20140106 begin */
import android.util.Log;
/* DTS2013122507276 wuweibin/00107028 20140106 end> */
/*< DTS2014101007196 guyue/00295151 20141106 begin*/
/*< DTS2014022006646  taolan/00264981 20140226 begin*/
import android.widget.TextView;
import android.util.TypedValue;
import com.android.internal.R;
/* DTS2014022006646  taolan/00264981 20140226 end>*/
/*< DTS2014101007196 guyue/00295151 20141106 end*/
/**
 * Presents a menu as a small, simple popup anchored to another view.
 * @hide
 */
public class MenuPopupHelper implements AdapterView.OnItemClickListener, View.OnKeyListener,
        ViewTreeObserver.OnGlobalLayoutListener, PopupWindow.OnDismissListener,
        View.OnAttachStateChangeListener, MenuPresenter {
    private static final String TAG = "MenuPopupHelper";

    static final int ITEM_LAYOUT = com.android.internal.R.layout.popup_menu_item_layout;

    private final Context mContext;
    private final LayoutInflater mInflater;
    private final MenuBuilder mMenu;
    private final MenuAdapter mAdapter;
    private final boolean mOverflowOnly;
    private final int mPopupMaxWidth;
    private final int mPopupStyleAttr;
    private final int mPopupStyleRes;

    private View mAnchorView;
    private ListPopupWindow mPopup;
    private ViewTreeObserver mTreeObserver;
    private HwMenuPopupHelper mHwMenuPopupHelper = null;
    private Callback mPresenterCallback;

    boolean mForceShowIcon;

    private ViewGroup mMeasureParent;

    /** Whether the cached content width value is valid. */
    private boolean mHasContentWidth;

    /** Cached content width from {@link #measureContentWidth}. */
    private int mContentWidth;

    private int mDropDownGravity = Gravity.NO_GRAVITY;
    /*< DTS2014101007196 guyue/00295151 20141106 begin*/
    /*< DTS2014022006646  taolan/00264981 20140226 begin*/
    private float mMenuPopupMaxFontSize;
    private float mMenuPopupMinFontSize;
    private float mFontSize;
    private int mMenuPopupMaxWidth;
    /* DTS2014022006646  taolan/00264981 20140226 end>*/
    /*< DTS2014101007196 guyue/00295151 20141106 end*/
    public MenuPopupHelper(Context context, MenuBuilder menu) {
        this(context, menu, null, false, com.android.internal.R.attr.popupMenuStyle, 0);
    }

    public MenuPopupHelper(Context context, MenuBuilder menu, View anchorView) {
        this(context, menu, anchorView, false, com.android.internal.R.attr.popupMenuStyle, 0);
    }
    /*< DTS2014101007196 guyue/00295151 20141106 begin*/
    /*< DTS2013121107644 tianjing/00102012 20131203 begin*/
    public MenuPopupHelper(Context context, MenuBuilder menu, View anchorView,
            boolean overflowOnly, int popupStyleAttr) {
        this(context, menu, anchorView, overflowOnly, popupStyleAttr, 0);
    }

    public MenuPopupHelper(Context context, MenuBuilder menu, View anchorView,
            boolean overflowOnly, int popupStyleAttr, int popupStyleRes) {
        mContext = context;
        mHwMenuPopupHelper = HwWidgetFactory.getHwMenuPopupHelper(context, this, null);
        /* DTS2013121107644 tianjing/00102012 20131203 end>*/
    /*< DTS2014101007196 guyue/00295151 20141106 end*/
        mInflater = LayoutInflater.from(context);
        mMenu = menu;
        mAdapter = new MenuAdapter(mMenu);
        mOverflowOnly = overflowOnly;
        mPopupStyleAttr = popupStyleAttr;
        mPopupStyleRes = popupStyleRes;

        final Resources res = context.getResources();
        mPopupMaxWidth = Math.max(res.getDisplayMetrics().widthPixels / 2,
                res.getDimensionPixelSize(com.android.internal.R.dimen.config_prefDialogWidth));

        mAnchorView = anchorView;

        // Present the menu using our context, not the menu builder's context.
        menu.addMenuPresenter(this, context);
        /*< DTS2014101007196 guyue/00295151 20141106 begin*/
        /*< DTS2014022006646  taolan/00264981 20140226 begin*/
        mMenuPopupMaxFontSize = (context.getResources().getDimensionPixelSize(R.dimen.menu_popup_max_font_size)/context.getResources().getDisplayMetrics().density);
        mMenuPopupMinFontSize = (context.getResources().getDimensionPixelSize(R.dimen.menu_popup_min_font_size)/context.getResources().getDisplayMetrics().density);
        mMenuPopupMaxWidth = (int)mContext.getResources().getDimensionPixelSize(R.dimen.menu_popup_max_width);
        /* DTS2014022006646  taolan/00264981 20140226 end>*/
        /*< DTS2014101007196 guyue/00295151 20141106 end*/
    }

    public void setAnchorView(View anchor) {
        mAnchorView = anchor;
    }

    public void setForceShowIcon(boolean forceShow) {
        mForceShowIcon = forceShow;
    }

    public void setGravity(int gravity) {
        mDropDownGravity = gravity;
    }

    public void show() {
        if (!tryShow()) {
            throw new IllegalStateException("MenuPopupHelper cannot be used without an anchor");
        }
    }

    public ListPopupWindow getPopup() {
        return mPopup;
    }

    public boolean tryShow() {
        /*< DTS2014101007196 guyue/00295151 20141106 begin*/
        /* < DTS2014102202787 yinwenshuai/00211458 20141030 begin */
        mPopup = HwWidgetFactory.getHwListPopupWindow(mContext, null, mPopupStyleAttr, 0);
        /* DTS2014102202787 yinwenshuai/00211458 20141030 end > */
        /*< DTS2014101007196 guyue/00295151 20141106 end*/
        /// M: manipulate multiple instances of popup window
        final ListPopupWindow savedPopup = mPopup;
        mPopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                /// M: avoid memory leaks of observer
                if (savedPopup != null) {
                    savedPopup.setAdapter(null);
                }
                MenuPopupHelper.this.onDismiss();
            }
        });
        //mPopup.setOnDismissListener(this);
        mPopup.setOnItemClickListener(this);
        mPopup.setAdapter(mAdapter);
        mPopup.setModal(true);

        ArrayList<MenuItemImpl> items = mMenu.getVisibleItems();
        int count = items.size();
        for (int i = 0; i < count; ++i) {
            Log.i("MenuPopupHelper", "tryShow: title = " + items.get(i).getTitle());
        }

        View anchor = mAnchorView;
        if (anchor != null) {
            final boolean addGlobalListener = mTreeObserver == null;
            mTreeObserver = anchor.getViewTreeObserver(); // Refresh to latest
            if (addGlobalListener) mTreeObserver.addOnGlobalLayoutListener(this);
            anchor.addOnAttachStateChangeListener(this);
            mPopup.setAnchorView(anchor);
            mPopup.setDropDownGravity(mDropDownGravity);
        } else {
            return false;
        }

        if (!mHasContentWidth) {
            mContentWidth = measureContentWidth();
            mHasContentWidth = true;
        }
        /*< DTS2014101007196 guyue/00295151 20141106 begin*/
        /*< DTS2013121107644 tianjing/00102012 20131203 begin*/
        if (mHwMenuPopupHelper != null) {
            mHwMenuPopupHelper.emuiMeasureContentWidth(mAdapter, this);
        }else{
            mPopup.setContentWidth(Math.min(measureContentWidth(), mPopupMaxWidth));
        }
        /* DTS2013121107644 tianjing/00102012 20131203 end>*/
        /*< DTS2014101007196 guyue/00295151 20141106 end*/
        mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        mPopup.show();
        mPopup.getListView().setOnKeyListener(this);
        return true;
    }

    public void dismiss() {
        if (isShowing()) {
            mPopup.dismiss();
        }
    }

    public void onDismiss() {
        mPopup = null;
        mMenu.close();
        if (mTreeObserver != null) {
            if (!mTreeObserver.isAlive()) mTreeObserver = mAnchorView.getViewTreeObserver();
            mTreeObserver.removeGlobalOnLayoutListener(this);
            mTreeObserver = null;
        }
        mAnchorView.removeOnAttachStateChangeListener(this);
    }

    public boolean isShowing() {
        return mPopup != null && mPopup.isShowing();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        MenuAdapter adapter = mAdapter;
        adapter.mAdapterMenu.performItemAction(adapter.getItem(position), 0);
    }

    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_MENU) {
            dismiss();
            return true;
        }
        return false;
    }

    private int measureContentWidth() {
        // Menus don't tend to be long, so this is more sane than it looks.
        int maxWidth = 0;
        View itemView = null;
        int itemType = 0;

        final ListAdapter adapter = mAdapter;
        final int widthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        final int heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        final int count = adapter.getCount();
        /*< DTS2014101007196 guyue/00295151 20141106 begin*/
        /*< DTS2014022006646  taolan/00264981 20140226 begin*/
        mFontSize = mMenuPopupMaxFontSize;
        /* DTS2014022006646  taolan/00264981 20140226 end>*/
        /*< DTS2014101007196 guyue/00295151 20141106 end*/
        for (int i = 0; i < count; i++) {
            final int positionType = adapter.getItemViewType(i);
            if (positionType != itemType) {
                itemType = positionType;
                itemView = null;
            }

            if (mMeasureParent == null) {
                mMeasureParent = new FrameLayout(mContext);
            }

            itemView = adapter.getView(i, itemView, mMeasureParent);
            /*< DTS2014101007196 guyue/00295151 20141106 begin*/
            /*< DTS2014022006646  taolan/00264981 20140226 begin*/
            int measuredWidth = catchMeasuredWidth(itemView, widthMeasureSpec, heightMeasureSpec);
            while (measuredWidth > mMenuPopupMaxWidth && mFontSize > mMenuPopupMinFontSize) {
                mFontSize -= 1;
                if(itemView != null){
                    measuredWidth = catchMeasuredWidth(itemView, widthMeasureSpec, heightMeasureSpec);
                }
            }
            if (0 == (mFontSize - mMenuPopupMinFontSize)) {
                maxWidth = mMenuPopupMaxWidth;
                break;
            } else {
                maxWidth = Math.max(maxWidth, measuredWidth);
            }
        }

        return maxWidth;
    }

    @Override
    public void onGlobalLayout() {
        if (isShowing()) {
            final View anchor = mAnchorView;
            if (anchor == null || !anchor.isShown()) {
                dismiss();
            } else if (isShowing()) {
                // Recompute window size and position
                mPopup.show();
            }
        }
    }

    @Override
    public void onViewAttachedToWindow(View v) {
    }

    @Override
    public void onViewDetachedFromWindow(View v) {
        if (mTreeObserver != null) {
            if (!mTreeObserver.isAlive()) mTreeObserver = v.getViewTreeObserver();
            mTreeObserver.removeGlobalOnLayoutListener(this);
        }
        v.removeOnAttachStateChangeListener(this);
    }

    @Override
    public void initForMenu(Context context, MenuBuilder menu) {
        // Don't need to do anything; we added as a presenter in the constructor.
    }

    @Override
    public MenuView getMenuView(ViewGroup root) {
        throw new UnsupportedOperationException("MenuPopupHelpers manage their own views");
    }

    @Override
    public void updateMenuView(boolean cleared) {
        mHasContentWidth = false;

        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void setCallback(Callback cb) {
        mPresenterCallback = cb;
    }

    @Override
    public boolean onSubMenuSelected(SubMenuBuilder subMenu) {
        if (subMenu.hasVisibleItems()) {
            MenuPopupHelper subPopup = new MenuPopupHelper(mContext, subMenu, mAnchorView);
            subPopup.setCallback(mPresenterCallback);

            boolean preserveIconSpacing = false;
            final int count = subMenu.size();
            for (int i = 0; i < count; i++) {
                MenuItem childItem = subMenu.getItem(i);
                if (childItem.isVisible() && childItem.getIcon() != null) {
                    preserveIconSpacing = true;
                    break;
                }
            }
            subPopup.setForceShowIcon(preserveIconSpacing);

            if (subPopup.tryShow()) {
                if (mPresenterCallback != null) {
                    mPresenterCallback.onOpenSubMenu(subMenu);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {
        // Only care about the (sub)menu we're presenting.
        if (menu != mMenu) return;

        dismiss();
        if (mPresenterCallback != null) {
            mPresenterCallback.onCloseMenu(menu, allMenusAreClosing);
        }
    }

    @Override
    public boolean flagActionItems() {
        return false;
    }

    public boolean expandItemActionView(MenuBuilder menu, MenuItemImpl item) {
        return false;
    }

    public boolean collapseItemActionView(MenuBuilder menu, MenuItemImpl item) {
        return false;
    }

    @Override
    public int getId() {
        return 0;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        return null;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
    }

    private class MenuAdapter extends BaseAdapter {
        private MenuBuilder mAdapterMenu;
        private int mExpandedIndex = -1;

        public MenuAdapter(MenuBuilder menu) {
            mAdapterMenu = menu;
            findExpandedIndex();
        }

        public int getCount() {
            ArrayList<MenuItemImpl> items = mOverflowOnly ?
                    mAdapterMenu.getNonActionItems() : mAdapterMenu.getVisibleItems();
            if (mExpandedIndex < 0) {
                return items.size();
            }
            return items.size() - 1;
        }

        public MenuItemImpl getItem(int position) {
            ArrayList<MenuItemImpl> items = mOverflowOnly ?
                    mAdapterMenu.getNonActionItems() : mAdapterMenu.getVisibleItems();
            if (mExpandedIndex >= 0 && position >= mExpandedIndex) {
                position++;
            }
            return items.get(position);
        }

        public long getItemId(int position) {
            // Since a menu item's ID is optional, we'll use the position as an
            // ID for the item in the AdapterView
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            /*< DTS2014101007196 guyue/00295151 20141106 begin*/
            /*< DTS2013121107644 tianjing/00102012 20131203 begin*/
            if (mHwMenuPopupHelper != null) {
                MenuItemImpl item = getItem(position);
                convertView = mHwMenuPopupHelper.getView(position, convertView, parent, item);
            } else {
                if (convertView == null) {
                    convertView = mInflater.inflate(ITEM_LAYOUT, parent, false);
                }

            MenuView.ItemView itemView = (MenuView.ItemView) convertView;
            if (mForceShowIcon) {
                ((ListMenuItemView) convertView).setForceShowIcon(true);
            }
            itemView.initialize(getItem(position), 0);
                /*< DTS2014101007196 guyue/00295151 20141106 begin*/
                /*< DTS2014022006646  taolan/00264981 20140226 begin*/
                TextView textView = (TextView)convertView.findViewById(com.android.internal.R.id.title);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mFontSize);
                /* DTS2014022006646  taolan/00264981 20140226 end>*/
                /*< DTS2014101007196 guyue/00295151 20141106 end*/
            }
            /* DTS2013121107644 tianjing/00102012 20131203 end>*/
            /*< DTS2014101007196 guyue/00295151 20141106 end*/
            return convertView;
        }

        void findExpandedIndex() {
            final MenuItemImpl expandedItem = mMenu.getExpandedItem();
            if (expandedItem != null) {
                final ArrayList<MenuItemImpl> items = mMenu.getNonActionItems();
                final int count = items.size();
                for (int i = 0; i < count; i++) {
                    final MenuItemImpl item = items.get(i);
                    if (item == expandedItem) {
                        mExpandedIndex = i;
                        return;
                    }
                }
            }
            mExpandedIndex = -1;
        }

        @Override
        public void notifyDataSetChanged() {
            findExpandedIndex();
            super.notifyDataSetChanged();
        }
    }
    /*< DTS2014101007196 guyue/00295151 20141106 begin*/
    /*< DTS2014022006646  taolan/00264981 20140226 begin*/
    private int catchMeasuredWidth(View itemView, int widthMeasureSpec, int heightMeasureSpec) {
        TextView textView=(TextView)itemView.findViewById(com.android.internal.R.id.title);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mFontSize);
        itemView.measure(widthMeasureSpec, heightMeasureSpec);
        return itemView.getMeasuredWidth();
    }
    /* DTS2014022006646  taolan/00264981 20140226 end>*/

    /*<DTS2014102202787 guyue/00295151 20141022 begin*/
    public ViewGroup getMeasureParent() {
        return mMeasureParent;
    }
    public void  setMeasureParent(ViewGroup setmMeasureParent) {
        mMeasureParent = setmMeasureParent;
    }
    /*<DTS2014102202787 guyue/00295151 20141022 end*/
    /*< DTS2014101007196 guyue/00295151 20141106 end*/
}
