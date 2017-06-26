package com.android.mms.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.CollapsibleActionView;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SearchView;

import com.android.mms.MmsConfig;
import com.android.mms.R;

/**
 * M: AdvanceSearchView ; Add for OP09;
 *
 */
public class AdvancedSearchView extends LinearLayout implements CollapsibleActionView {
    private SearchView mSearchView;
    private ImageButton mImageSearchBtn;

    public AdvancedSearchView(Context context) {
        this(context, null);
    }

    public AdvancedSearchView(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.advanced_search_view, this, true);
        mSearchView = (SearchView) findViewById(R.id.search_view);
        mImageSearchBtn = (ImageButton) findViewById(R.id.image_search_btn);
    }

    public SearchView getSearchView() {
        return mSearchView;
    }

    public ImageButton getImageSearchBtn() {
        return mImageSearchBtn;
    }

    @Override
    public void onActionViewCollapsed() {
        if (mSearchView != null) {
            mSearchView.onActionViewCollapsed();
        }
    }

    @Override
    public void onActionViewExpanded() {
        if (mSearchView != null) {
            mSearchView.onActionViewExpanded();
        }
    }
}
