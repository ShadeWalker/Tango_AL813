package com.mediatek.contacts.common.list;

import android.content.Context;
import android.view.View;
import android.view.View.MeasureSpec;
import android.widget.CheckBox;
import android.widget.TextView;

import com.android.contacts.common.list.ContactListItemView;
import com.mediatek.contacts.ext.IViewCustomExtension.ContactListItemViewCustom;
import com.mediatek.contacts.ExtensionManager;

public class ContactListItemViewEx {
    /// M:[RCS]provide a view for customization @{
    private ViewCustomManager mViewCustomManager = new ViewCustomManager();
    private ContactListItemView mContactListItemView = null;
    /// M: New Feature: Add the check-box support for ContactListItemView.
    private CheckBox mSelectBox = null;

    public ContactListItemViewEx(ContactListItemView contactListItemView) {
        mContactListItemView = contactListItemView;
    }

    /**
     * M: Enable check-box or disable check-box
     * @param checkable is true, create check-box and set the visibility.
     */
    public void setCheckable(boolean checkable) {
        if (checkable) {
            if (mSelectBox == null) {
                getCheckBox();
            }
            mSelectBox.setVisibility(View.VISIBLE);
        } else {
            if (mSelectBox != null) {
                mSelectBox.setVisibility(View.GONE);
            }
        }
    }

    /**
     * M: Retrieve the check box view for changing its state between Checked or
     * Unchecked state
     * @return check-box view
     */
    public CheckBox getCheckBox() {
        if (mSelectBox == null) {
            mSelectBox = new CheckBox(mContactListItemView.getContext());
            mSelectBox.setClickable(false);
            mSelectBox.setFocusable(false);
            mSelectBox.setFocusableInTouchMode(false);
            mContactListItemView.addView(mSelectBox);
        }
        mSelectBox.setVisibility(View.VISIBLE);
        return mSelectBox;
    }

    /**
     * M: Measure check-box view
     */
    public void measureCheckBox() {
        if (isVisible(mSelectBox)) {
            mSelectBox.measure(0, 0);
        }
    }

    /**
     * M: Performs layout of check-box view
     * @return new left boundary
     */
    public int layoutLeftCheckBox(int leftBound, int topBound, int bottomBound) {
        if (isVisible(mSelectBox)) {
            int selectBoxWidth = mSelectBox.getMeasuredWidth();
            int selectBoxHeight = mSelectBox.getMeasuredHeight();
            mSelectBox.layout(leftBound, (bottomBound + topBound - selectBoxHeight) / 2, leftBound
                    + selectBoxWidth, (bottomBound + topBound + selectBoxHeight) / 2);
            return leftBound + selectBoxWidth;
        }
        return leftBound;
    }

    /**
     * M:RTL New Feature
     */
    public int layoutRightCheckBox(int rightBound, int topBound, int bottomBound) {
        if (isVisible(mSelectBox)) {
            int selectBoxWidth = mSelectBox.getMeasuredWidth();
            int selectBoxHeight = mSelectBox.getMeasuredHeight();
            mSelectBox.layout(rightBound - selectBoxWidth,
                    (bottomBound + topBound - selectBoxHeight) / 2, rightBound, (bottomBound
                            + topBound + selectBoxHeight) / 2);
            return rightBound - selectBoxWidth;
        }
        return rightBound;
    }

    private boolean isVisible(View view) {
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    /**
     * M: fix ALPS01758642,show ... for long string.
     */
    public void measureTextView(TextView textView) {
        if (isVisible(mSelectBox)) {
            int spec1 = MeasureSpec.makeMeasureSpec(textView.getWidth(), MeasureSpec.EXACTLY);
            int spec2 = MeasureSpec.makeMeasureSpec(textView.getHeight(), MeasureSpec.EXACTLY);
            textView.measure(spec1, spec2);
        }
    }

    ///M: for rcs @{
    public class ViewCustomManager {
        private View mCustomeView = null;
        private ContactListItemViewCustom mContactListItemViewCustom = ExtensionManager
                .getViewCustomExtension().getContactListItemViewCustom();

        public void createCustomView(long contactId) {
            if (mContactListItemViewCustom != null) {
                mCustomeView = mContactListItemViewCustom.createCustomView(contactId);
            }
        }

        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (mContactListItemViewCustom != null) {
                mContactListItemViewCustom.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }

        public void onLayout(boolean changed, int leftBound, int topBound, int rightBound,
                int bottomBound) {
            if (mContactListItemViewCustom != null) {
                mContactListItemViewCustom.onLayout(changed, leftBound, topBound, rightBound,
                        bottomBound);
            }
        }

        public void reAddView(long contactId) {
            if (mCustomeView != null) {
                mContactListItemView.removeView(mCustomeView);
                mCustomeView = null;

            }
            createCustomView(contactId);

            if (mCustomeView != null) {
                mContactListItemView.addView(mCustomeView);
            }

        }

        public int getWidthWithPadding() {
            return mCustomeView == null ? 0 : mCustomeView.getMeasuredWidth()
                    + mCustomeView.getPaddingLeft();
        }
    }

    public ViewCustomManager getRcsPlugin() {
        return mViewCustomManager;
    }
    /// @}
}
