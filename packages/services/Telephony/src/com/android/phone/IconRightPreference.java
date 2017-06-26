/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.phone;

import android.content.Context;
import android.preference.Preference;
import android.view.View;
import android.widget.TextView;

public class IconRightPreference extends Preference {

    private TextView textView = null;
    private CharSequence mTitle;
    // Whether or not the text and icon should be red color
    private boolean mIsRedColor;

    public IconRightPreference(Context context) {
        super(context);
        setLayoutResource(R.layout.preference_icon);
    }

    @Override
    public void onBindView(View view) {
        super.onBindView(view);
        textView = (TextView) view.findViewById(R.id.title);
        if (textView != null && mTitle != null) {
            android.util.Log.d("IconRightPreference", "onBindView: mTitle is " + mTitle + ", mIsRedColor is " + mIsRedColor);
            textView.setText(mTitle);
            if (mIsRedColor) {
                textView.setTextColor(android.graphics.Color.RED);
            }
        }
    }

    public void setTitle(CharSequence title, boolean isRedColor) {
        if (title == null && mTitle != null || title != null && !title.equals(mTitle)) {
            mTitle = title;
            mIsRedColor = isRedColor;
            notifyChanged();
        }
    }
    
    public CharSequence getTitle() {
        return mTitle;
    }
}
