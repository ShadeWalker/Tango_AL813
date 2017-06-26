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

package com.mediatek.wifi.hotspot;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.mediatek.xlog.Xlog;

public class MultiSummaryPreference extends Preference {
    private static final String TAG = "MultiSummaryPreference";

    public MultiSummaryPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        //setWidgetLayoutResource(R.layout.preference_multi_summary);
    }

    public MultiSummaryPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
	//setWidgetLayoutResource(R.layout.preference_multi_summary);
    }

    public MultiSummaryPreference(Context context) {
        super(context);
	//setWidgetLayoutResource(R.layout.preference_multi_summary);
    }


    
    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
	return view;
    }

    @Override
    protected void onBindView(View view){
	super.onBindView(view);
	TextView summary = (TextView)view.findViewById(com.android.internal.R.id.summary);
	summary.setMaxLines(15);
    }
}
