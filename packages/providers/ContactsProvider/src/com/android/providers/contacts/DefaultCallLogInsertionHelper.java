/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2011 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.providers.contacts;

import android.content.ContentValues;
import android.content.Context;
import android.provider.CallLog.Calls;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import com.android.i18n.phonenumbers.NumberParseException;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.android.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder;
import com.mediatek.geocoding.GeoCodingQuery;
import com.mediatek.providers.contacts.ContactsProviderUtils;

import com.google.android.collect.Sets;

import java.util.Locale;
import java.util.Set;

import com.cootek.smartdialer_plugin_oem.CooTekSmartdialerOemModule;
import com.cootek.smartdialer_plugin_oem.IServiceStateCallback;


/**
 * Default implementation of {@link CallLogInsertionHelper}.
 * <p>
 * It added the country ISO abbreviation and the geocoded location.
 * It checks for legacy unknown numbers and updates number presentation.
 * <p>
 * It uses {@link PhoneNumberOfflineGeocoder} to compute the geocoded location of a phone number.
 */
/*package*/ class DefaultCallLogInsertionHelper implements CallLogInsertionHelper,IServiceStateCallback{
    private static DefaultCallLogInsertionHelper sInstance;

    private static final Set<String> LEGACY_UNKNOWN_NUMBERS = Sets.newHashSet("-1", "-2", "-3");

    private final CountryMonitor mCountryMonitor;
    private PhoneNumberUtil mPhoneNumberUtil;
    private PhoneNumberOfflineGeocoder mPhoneNumberOfflineGeocoder;
    private final Locale mLocale;
    /// M: @{
    private Context mContext;
    /// @}
    //private  CooTekSmartdialerOemModule csom;

    private static final String TAG = "DefaultCallLogInsertionHelper";
    public static synchronized DefaultCallLogInsertionHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new DefaultCallLogInsertionHelper(context);
        }
        return sInstance;
    }

    private DefaultCallLogInsertionHelper(Context context) {
        mCountryMonitor = new CountryMonitor(context);
        mLocale = context.getResources().getConfiguration().locale;
        /// M: @{
        mContext = context;
        /// @}
        	//add by zhangjinqiang for GeoDescription-start
        	/*
    		if(csom==null){
			csom = new CooTekSmartdialerOemModule(mContext,this);
		}
		*/
		//add by zhangjinqiang end
    }

    @Override
    public void addComputedValues(ContentValues values) {
        // Insert the current country code, so we know the country the number belongs to.
        String countryIso = getCurrentCountryIso();
        // / M: @{
        Log.d(TAG, "addComputedValues() countryIso == [" + countryIso + "]");
        Log.d(TAG, "addComputedValues() geocoded == ["
                + getGeocodedLocationFor(values.getAsString(Calls.NUMBER), countryIso) + "]");
        // / @}
        values.put(Calls.COUNTRY_ISO, countryIso);
        // Insert the geocoded location, so that we do not need to compute it on the fly.
        values.put(Calls.GEOCODED_LOCATION,
                getGeocodedLocationFor(values.getAsString(Calls.NUMBER), countryIso));

        final String number = values.getAsString(Calls.NUMBER);
        if (LEGACY_UNKNOWN_NUMBERS.contains(number)) {
            values.put(Calls.NUMBER_PRESENTATION, Calls.PRESENTATION_UNKNOWN);
            values.put(Calls.NUMBER, "");
        }

        // Check for a normalized number; if not present attempt to determine one now.
        if (!values.containsKey(Calls.CACHED_NORMALIZED_NUMBER) &&
                !TextUtils.isEmpty(number)) {
            String normalizedNumber = PhoneNumberUtils.formatNumberToE164(number, countryIso);
            if (!TextUtils.isEmpty(normalizedNumber)) {
                values.put(Calls.CACHED_NORMALIZED_NUMBER, normalizedNumber);
            }
        }
    }

    private String getCurrentCountryIso() {
        return mCountryMonitor.getCountryIso();
    }

    private synchronized PhoneNumberUtil getPhoneNumberUtil() {
        if (mPhoneNumberUtil == null) {
            mPhoneNumberUtil = PhoneNumberUtil.getInstance();
        }
        return mPhoneNumberUtil;
    }

    private PhoneNumber parsePhoneNumber(String number, String countryIso) {
        try {
            return getPhoneNumberUtil().parse(number, countryIso);
        } catch (NumberParseException e) {
            return null;
        }
    }

    private synchronized PhoneNumberOfflineGeocoder getPhoneNumberOfflineGeocoder() {
        if (mPhoneNumberOfflineGeocoder == null) {
            mPhoneNumberOfflineGeocoder = PhoneNumberOfflineGeocoder.getInstance();
        }
        return mPhoneNumberOfflineGeocoder;
    }

    @Override
    public String getGeocodedLocationFor(String number, String countryIso) {
        /// M: Comment out because it can not find GeoCodingQuery @{
        if (ContactsProviderUtils.isPhoneNumberGeo()) {
					
			//add by zhangjinqiang for GeoDescription--start
			/*
			String returnValue = csom.getPhoneAttribute(number);
			if (!TextUtils.isEmpty(returnValue)) {
                return returnValue;
             }
             */
			//add by zhangjinqiang end

			// / Modified by guofeiyao 2015/12/08
			// For latin tigo
			if ( android.os.SystemProperties.get("ro.hq.use.location").equals("1") ) {
				
                GeoCodingQuery geoCodingQuery = GeoCodingQuery.getInstance(mContext);
                String cityName = geoCodingQuery.queryByNumber(number);
                Log.d(TAG, "[GeoCodingQuery] cityName = " + cityName);
                if (!TextUtils.isEmpty(cityName)) {
                    return cityName;
                }
			
			}
			// / End
			
            return "";
        }
        /// @}
        PhoneNumber structuredPhoneNumber = parsePhoneNumber(number, countryIso);
        if (structuredPhoneNumber != null) {
            /**
             * M: Original code:
            return getPhoneNumberOfflineGeocoder().getDescriptionForNumber(
                    structuredPhoneNumber, mLocale);
             */
            return getPhoneNumberOfflineGeocoder().getDescriptionForNumber(
                    structuredPhoneNumber, mContext.getResources().getConfiguration().locale);
        } else {
            return "";
        }
    }

		//add by zhangjinqiang for GeoDescription-start
		@Override
	public void onServiceConnected() {
		//Toast.makeText(PeopleActivity.this, "号码助手Service通信成功！", Toast.LENGTH_SHORT).show();  
	}
	
	@Override
	public void onServiceDisconnected() {
		//Toast.makeText(PeopleActivity.this, "号码助手Service通信连接失败！！", Toast.LENGTH_LONG).show(); 
	}
	//add by zhangjinqiang end
}
