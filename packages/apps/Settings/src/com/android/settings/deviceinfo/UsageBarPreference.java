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

package com.android.settings.deviceinfo;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;

import com.android.settings.R;
import com.google.android.collect.Lists;

import java.util.Collections;
import java.util.List;
import android.widget.TextView;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.os.storage.StorageVolume;
import android.util.Log;

/**
 * Creates a percentage bar chart inside a preference.
 */
public class UsageBarPreference extends Preference {
    private static final String TAG = "StorageVolumePreferenceCategory";
    private PercentageBarChart mChart = null;
	private TextView percentage_bar_text = null;
	//chenwenshuai modify for HQ01413789 begin
	private String mUseSpace = null;
	private String mAvaSpace = null;
	//chenwenshuai modify for HQ01413789 end
	private StorageVolume mVolume;//wuhuihui add for HQ01429893

    private final List<PercentageBarChart.Entry> mEntries = Lists.newArrayList();

    public UsageBarPreference(Context context) {
        this(context, null);
		setLayoutResource(R.layout.preference_memoryusage);//chenwenshuai modify for HQ01413841
    }

    public UsageBarPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public UsageBarPreference(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs, defStyle, 0);
    }

    public UsageBarPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        //setLayoutResource(R.layout.preference_memoryusage);
    }

	//add by HQ_zhouguo at 20150915 start
	@Override
	protected View onCreateView(ViewGroup parent) {
		// TODO Auto-generated method stub
		View view = LayoutInflater.from(getContext()).inflate(
				R.layout.preference_memoryusage, parent, false);
		percentage_bar_text = (TextView) view.findViewById(R.id.percentage_bar_text);
		return view;
	}
	//add by HQ_zhouguo at 20150915 end

    public void addEntry(int order, float percentage, int color) {
        mEntries.add(PercentageBarChart.createEntry(order, percentage, color));
        Collections.sort(mEntries);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
		updateSpace(mUseSpace,mAvaSpace);//chenwenshuai modify for HQ01413841
        mChart = (PercentageBarChart) view.findViewById(R.id.percentage_bar_chart);
        mChart.setEntries(mEntries);
    }

    public void commit() {
        if (mChart != null) {
            mChart.invalidate();
        }
    }

    public void clear() {
        mEntries.clear();
    }

    //HQ_wuhuihui add for HQ01429893 start
    public void setVolume(StorageVolume volume) {
        mVolume = volume;
    }
	//HQ_wuhuihui add for HQ01429893 end 

	//add by HQ_zhouguo at 20150915 start
	public void updateSpace(String userSpace,String avaSpace){
		//chenwenshuai modify for HQ01413789 begin
		mUseSpace = userSpace;
		mAvaSpace = avaSpace;
		//chenwenshuai modify for HQ01413789 end
		//HQ_wuhuihui add for HQ01429893 start
		Log.d(TAG, "updateSpace userSpace : " + userSpace + ", avaSpace:" + avaSpace);
		String storageName = getContext().getResources().getString(R.string.total_space);
		if (mVolume == null) {
            storageName = getContext().getResources().getString(R.string.user_space);
		}
		String res = getContext().getResources().getString(R.string.space_text);
		String sFinalAge = String.format(storageName+res, userSpace, avaSpace);
		//HQ_wuhuihui add for HQ01429893 end
		if(percentage_bar_text != null){
			percentage_bar_text.setText(sFinalAge);
		}
	}
	//add by HQ_zhouguo at 20150915 end
}
