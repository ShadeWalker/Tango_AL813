/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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
package com.android.contacts.interactions;

import com.android.contacts.R;
import com.android.contacts.common.util.BitmapUtil;
import com.android.contacts.common.util.ContactDisplayUtils;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telecom.PhoneAccountHandle;
import android.text.BidiFormatter;
import android.text.Spannable;
import android.text.TextDirectionHeuristics;
import android.util.Log;

import com.mediatek.contacts.quickcontact.PhoneAccountUtils;
import com.mediatek.telecom.TelecomManagerEx;

/**
 * Represents a call log event interaction, wrapping the columns in
 * {@link android.provider.CallLog.Calls}.
 *
 * This class does not return log entries related to voicemail or SIP calls. Additionally,
 * this class ignores number presentation. Number presentation affects how to identify phone
 * numbers. Since, we already know the identity of the phone number owner we can ignore number
 * presentation.
 *
 * As a result of ignoring voicemail and number presentation, we don't need to worry about API
 * version.
 */
public class CallLogInteraction implements ContactInteraction {

    private static final String URI_TARGET_PREFIX = "tel:";
    private static final int CALL_LOG_ICON_RES = R.drawable.ic_phone_24dp_hq;
//    private static final int CALL_ARROW_ICON_RES = R.drawable.ic_call_arrow;
	private static final int CALL_ARROW_ICON_RES = R.drawable.ic_call_in;
    private static final int OutCALL_ARROW_ICON_RES = R.drawable.ic_call_arrow1;
    private static final int INCALL_ARROW_ICON_RES = R.drawable.ic_call_in;

    
    private static BidiFormatter sBidiFormatter = BidiFormatter.getInstance();

    /* M: add sim icon & sim name @ { */
    private static final String TAG = "CallLogInteraction";
    private static final int SIM_ICON_RES = R.drawable.sim_indicator_orange_small;
    private PhoneAccountHandle mPhoneAccountHandle ;
    /* @ } */

    private ContentValues mValues;

    public CallLogInteraction(ContentValues values) {
        mValues = values;
        initPhoneAccount();
    }

    @Override
    public Intent getIntent() {
        String number = getNumber();
        return number == null ? null : new Intent(Intent.ACTION_CALL).setData(
                Uri.parse(URI_TARGET_PREFIX + number)).putExtra(
                /// M: ALPS01999102, new feature: call log card selection suggestion.
                TelecomManagerEx.EXTRA_SUGGESTED_PHONE_ACCOUNT_HANDLE, mPhoneAccountHandle);
    }

    @Override
    public String getViewHeader(Context context) {
        return getNumber();
    }

    @Override
    public long getInteractionDate() {
        Long date = getDate();
        return date == null ? -1 : date;
    }

    @Override
    public String getViewBody(Context context) {
        Integer numberType = getCachedNumberType();
        if (numberType == null) {
            return null;
        }
        return Phone.getTypeLabel(context.getResources(), getCachedNumberType(),
                getCachedNumberLabel()).toString();
    }

    @Override
    public String getViewFooter(Context context) {
        Long date = getDate();
        return date == null ? null : ContactInteractionUtil.formatDateStringFromTimestamp(
                date, context);
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getResources().getDrawable(CALL_LOG_ICON_RES);
    }

    @Override
    public Drawable getBodyIcon(Context context) {
        return null;
    }

    @Override
    public Drawable getFooterIcon(Context context) {
        Drawable callArrow = null;
        Resources res = context.getResources();
        Integer type = getType();
        if (type == null) {
            return null;
        }
        switch (type) {
            case Calls.INCOMING_TYPE:
                callArrow = res.getDrawable(INCALL_ARROW_ICON_RES);
//                callArrow = res.getDrawable(CALL_ARROW_ICON_RES);
/*                callArrow.setColorFilter(res.getColor(R.color.call_arrow_green),
                        PorterDuff.Mode.MULTIPLY);*/
                break;
            case Calls.MISSED_TYPE:
                callArrow = res.getDrawable(CALL_ARROW_ICON_RES);
                callArrow.setColorFilter(res.getColor(R.color.call_arrow_red),
                        PorterDuff.Mode.MULTIPLY);
                break;
            case Calls.OUTGOING_TYPE:
            	callArrow = res.getDrawable(OutCALL_ARROW_ICON_RES);
//              callArrow = BitmapUtil.getRotatedDrawable(res, CALL_ARROW_ICON_RES, 180f);
/*              callArrow.setColorFilter(res.getColor(R.color.call_arrow_green),
                        PorterDuff.Mode.MULTIPLY);*/
                break;
        }
        return callArrow;
    }

    /* M: add sim icon & sim name @ { */
    private void initPhoneAccount() {
        String accountName = mValues.getAsString(Calls.PHONE_ACCOUNT_COMPONENT_NAME);
        String accountId = mValues.getAsString(Calls.PHONE_ACCOUNT_ID);
        Log.d(TAG, "phone account component name: " + accountName);
        Log.d(TAG, "phone account accountId: " + accountId);
        mPhoneAccountHandle = PhoneAccountUtils.getAccount(accountName, accountId);
    }

    public Drawable getSimIcon(Context context) {
        Drawable accountIcon = PhoneAccountUtils.getAccountIcon(context, mPhoneAccountHandle);
        Log.d(TAG, "account icon: " + accountIcon);
        return accountIcon;
    }

    public String getSimName(Context context) {
        String accountName = PhoneAccountUtils.getAccountLabel(context, mPhoneAccountHandle);
        Log.d(TAG, "accountName: " + accountName);
        return accountName;
    }
    /* @ } */

    public String getCachedName() {
        return mValues.getAsString(Calls.CACHED_NAME);
    }

    public String getCachedNumberLabel() {
        return mValues.getAsString(Calls.CACHED_NUMBER_LABEL);
    }

    public Integer getCachedNumberType() {
        return mValues.getAsInteger(Calls.CACHED_NUMBER_TYPE);
    }

    public Long getDate() {
        return mValues.getAsLong(Calls.DATE);
    }

	@Override
    public String getDuration() {
        return mValues.getAsInteger(Calls.DURATION).toString();
    }

    public Boolean getIsRead() {
        return mValues.getAsBoolean(Calls.IS_READ);
    }

    public Integer getLimitParamKey() {
        return mValues.getAsInteger(Calls.LIMIT_PARAM_KEY);
    }

    public Boolean getNew() {
        return mValues.getAsBoolean(Calls.NEW);
    }

    public String getNumber() {
        final String number = mValues.getAsString(Calls.NUMBER);
        return number == null ? null :
            sBidiFormatter.unicodeWrap(number, TextDirectionHeuristics.LTR);
    }

    public Integer getNumberPresentation() {
        return mValues.getAsInteger(Calls.NUMBER_PRESENTATION);
    }

    public Integer getOffsetParamKey() {
        return mValues.getAsInteger(Calls.OFFSET_PARAM_KEY);
    }
	
    @Override
    public int getType() {
        return mValues.getAsInteger(Calls.TYPE);
    }
	@Override
	public int getSubscriptionId(){
		Object value = mValues.getAsInteger(Calls.PHONE_ACCOUNT_ID);
		if(value!=null)
			return mValues.getAsInteger(Calls.PHONE_ACCOUNT_ID);
		else
			return 0;
	}
    @Override
    public Spannable getContentDescription(Context context) {
        final String phoneNumber = getViewHeader(context);
        final String contentDescription = context.getResources().getString(
                R.string.content_description_recent_call,
                getCallTypeString(context), phoneNumber, getViewFooter(context));
        return ContactDisplayUtils.getTelephoneTtsSpannable(contentDescription, phoneNumber);
    }

    private String getCallTypeString(Context context) {
        String callType = "";
        Resources res = context.getResources();
        Integer type = getType();
        if (type == null) {
            return callType;
        }
        switch (type) {
            case Calls.INCOMING_TYPE:
                callType = res.getString(R.string.content_description_recent_call_type_incoming);
                break;
            case Calls.MISSED_TYPE:
                callType = res.getString(R.string.content_description_recent_call_type_missed);
                break;
            case Calls.OUTGOING_TYPE:
                callType = res.getString(R.string.content_description_recent_call_type_outgoing);
                break;
        }
        return callType;
    }

    @Override
    public int getIconResourceId() {
        return CALL_LOG_ICON_RES;
    }
}
