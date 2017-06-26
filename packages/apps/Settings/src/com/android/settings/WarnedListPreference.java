/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.settings;

import android.view.View;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.app.AlertDialog.Builder;
import android.view.ViewGroup;

import com.android.internal.R;

public class WarnedListPreference extends ListPreference {
    private int mSingleChoiceItemLayout;
    private int mClickedDialogEntryIndex;
    private LayoutInflater mInflater;
    public WarnedListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(null,
                com.android.internal.R.styleable.AlertDialog,
                com.android.internal.R.attr.alertDialogStyle, 0);

        mSingleChoiceItemLayout = a.getResourceId(
                com.android.internal.R.styleable.AlertDialog_singleChoiceItemLayout,
                com.android.settings.R.layout.select_dialog_singlechoice);
        a.recycle();

        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    protected void onClick() {
        // Ignore this until an explicit call to click()
    }

    public void click() {
        super.onClick();
    }
    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        mClickedDialogEntryIndex = findIndexOfValue(getValue());
        builder.setSingleChoiceItems(new FontArrayAdapter(getEntries(), getEntryValues(), mSingleChoiceItemLayout), mClickedDialogEntryIndex,
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                mClickedDialogEntryIndex = which;
                WarnedListPreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                dialog.dismiss();
            }
        });

        builder.setPositiveButton(null, null);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        CharSequence[] entryValues = getEntryValues();
        if (positiveResult && mClickedDialogEntryIndex >= 0 && entryValues != null) {
            String value = entryValues[mClickedDialogEntryIndex].toString();
            if (callChangeListener(value)) {
                setValue(value);
            }
        }
    }

    class FontArrayAdapter extends BaseAdapter {
        private CharSequence[] mFontTextArray;
        private CharSequence[] mFontScaleArray;
        private int mResource;
        private float mBaseFontSise;

        public FontArrayAdapter(CharSequence[] fontTextArray, CharSequence[] fontSizeArray, int resource) {
            mFontTextArray = fontTextArray;
            mFontScaleArray = fontSizeArray;
            mResource = resource;
            //in al813 found the fontsize is 13,but in 812 is 14,so change the default value as 13.
            mBaseFontSise = 13;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            return createViewFromResource(position, convertView, parent, mResource);
        }

        private View createViewFromResource(int position, View convertView, ViewGroup parent,
                int resource) {
            View view;
            TextView text;

            if (convertView == null) {
                view = mInflater.inflate(resource, parent, false);
            } else {
                view = convertView;
            }

            text = (TextView) view.findViewById(android.R.id.text1);
            CharSequence item = mFontTextArray[position];
            text.setText(item);

            Float scale = Float.parseFloat(mFontScaleArray[position].toString());
            Float size =  mBaseFontSise * scale;
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);

            return view;
        }

        public long getItemId(int position) {
            return position;
        }

        public CharSequence getItem(int position) {
            return mFontTextArray[position];
        }

        public int getCount() {
            return mFontTextArray.length;
        }
    }
}
