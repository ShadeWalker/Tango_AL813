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

package com.android.incallui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.TextView;

public class CallInfoView extends FrameLayout {

    public TextView mCallName;
    public View mCallProviderInfo;
    // M: add for OP09 plug in. @{
    public ImageView mCallProviderIcon;
    // add for OP09 plug in. @}
    public TextView mCallProviderLabel;
    public TextView mCallStatus;
    public View mCallConferenceCallIcon;

    public CallInfoView(Context context) {
        super(context);
    }

    public CallInfoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater inflate = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflate.inflate(R.layout.call_info_view, this);
        mCallName = (TextView) findViewById(R.id.callName);
        mCallProviderInfo = (View) findViewById(R.id.call_provider_info);
        // M: add for OP09 plug in. @{
        mCallProviderIcon = (ImageView) findViewById(R.id.callProviderIcon);
        // add for OP09 plug in. @}
        mCallProviderLabel = (TextView) findViewById(R.id.callProviderLabel);
        mCallStatus = (TextView) findViewById(R.id.callStatus);
        mCallConferenceCallIcon = findViewById(R.id.callConferenceCallIcon);
    }
}
