package com.android.phone.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;

import com.android.phone.common.R;

/**
 * Created by guofeiyao for EMUI style
 */
public class ArrowPreference extends Preference {


    public ArrowPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.arrow_image);
    }

    public ArrowPreference(Context context) {
        super(context);
        setWidgetLayoutResource(R.layout.arrow_image);
    }

}
