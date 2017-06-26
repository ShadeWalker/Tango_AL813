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

package com.android.mms.ui;

import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.PreferenceManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import com.android.mms.R;
import com.android.internal.telephony.ITelephony;
import com.mediatek.widget.AccountItemView;

public class SubSelectAdapter extends BaseAdapter {

    private LayoutInflater mInf;
    private String mPreferenceKey;
    private Context mContext;
    private List<SubscriptionInfo> mList;
    private boolean mIsRadioOn;
    private String langage;//add by wangmingyue for number display

    public static final String TAG = "SubSelectAdapter";

    public SubSelectAdapter(Context context, String preferenceKey, List<SubscriptionInfo> list) {
        mInf = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mContext = context;
        mPreferenceKey = preferenceKey;
        mList = list;
        langage = Locale.getDefault().getLanguage();//add by lipeng for number display
    }


    @Override
    public int getCount() {
        return mList == null ? 0 : mList.size();
    }

    @Override
    public Object getItem(int position) {
        return mList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    //HQ_wuruijun add for HQ01340494 start
    public static boolean isRadioOn(int subId) {
        ITelephony itele = ITelephony.Stub.asInterface(
                ServiceManager.getService(Context.TELEPHONY_SERVICE));
        boolean isOn = false;
        try {
            if (itele != null) {
                isOn = subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID ? false :
                    itele.isRadioOnForSubscriber(subId);
            } else {
                Log.d(TAG, "telephony service is null");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        Log.d(TAG, ">>>>>isOn = " + isOn + ", subId: " + subId);
        return isOn;
    }
    //HQ_wuruijun add end

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = mInf.inflate(R.layout.sub_select_item, null);
        AccountItemView accountView;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (convertView != null && (convertView instanceof AccountItemView)) {
            accountView = (AccountItemView) convertView;
        } else {
            accountView = (AccountItemView) view.findViewById(R.id.subItem);
        }
        // subView.setThemeType(SubscriptionView.LIGHT_THEME);
        SubscriptionInfo subRecord = mList.get(position);

        //HQ_wuruijun add for HQ01340494 start
        mIsRadioOn = isRadioOn(subRecord.getSubscriptionId());
        view.setClickable(!mIsRadioOn);
        if(!mIsRadioOn) {
            TextView tv1 = (TextView)view.findViewById(android.R.id.title);
            TextView tv2 = (TextView)view.findViewById(android.R.id.summary);
            tv1.setTextColor(Color.parseColor("#BFBFBF"));
            tv2.setTextColor(Color.parseColor("#BFBFBF"));
        }
        //HQ_wuruijun add end
        accountView.setAccountIcon(new BitmapDrawable(mContext.getResources(), subRecord
                .createIconBitmap(mContext)));
        accountView.setAccountName(subRecord.getDisplayName().toString());
        String accountNumber=subRecord.getNumber();
        // modify by wangmingyue for number display
     	if ((!TextUtils.isEmpty(accountNumber)) && (langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw"))) {
     		accountNumber = "\u202D"+ accountNumber+ "\u202C";
     	}
     	accountView.setAccountNumber(accountNumber);
        // end wangmingyue
        CheckBox subCheckBox = (CheckBox) view.findViewById(R.id.subCheckBox);
        if (SmsPreferenceActivity.SMS_MANAGE_SIM_MESSAGES.equals(mPreferenceKey)
                || SmsPreferenceActivity.SMS_SERVICE_CENTER.equals(mPreferenceKey)
                || SmsPreferenceActivity.SMS_SAVE_LOCATION.equals(mPreferenceKey)
                || GeneralPreferenceActivity.CELL_BROADCAST.equals(mPreferenceKey)) {
            subCheckBox.setVisibility(View.GONE);
        } else {
            subCheckBox.setChecked(isChecked(position));
            if (MmsPreferenceActivity.RETRIEVAL_DURING_ROAMING.equals(mPreferenceKey)) {
                if (prefs.getBoolean(Long.toString((mList.get(position)).getSubscriptionId()) + "_"
                        + MmsPreferenceActivity.AUTO_RETRIEVAL, true) == false) {
                    subCheckBox.setEnabled(false);
                }
            }
        }
        return view;
        //return convertView;
    }

    /**
     * get the related preference data by position to find whether
     * @param position
     * @return whether has checked
     */
    public boolean isChecked(int position) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean defaultValue = false;
        if (MmsPreferenceActivity.AUTO_RETRIEVAL.equals(mPreferenceKey)) {
            defaultValue = true;
        }
        return prefs.getBoolean(Long.toString((mList.get(position)).getSubscriptionId()) + "_"
                + mPreferenceKey,
                defaultValue);
    }

    /**
     * set the mPreferenceKey
     *
     * @param preferenceKey
     */
    public void setPreferenceKey(String preferenceKey) {
        mPreferenceKey = preferenceKey;
    }
}
