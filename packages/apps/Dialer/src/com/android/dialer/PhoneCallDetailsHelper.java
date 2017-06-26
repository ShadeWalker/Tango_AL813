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
 * limitations under the License.
 */

package com.android.dialer;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.Color;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telecom.PhoneAccount;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.testing.NeededForTesting;
import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.calllog.PhoneNumberDisplayHelper;
import com.android.dialer.calllog.PhoneNumberUtilsWrapper;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.calllog.PhoneAccountUtils;

import com.google.common.collect.Lists;
import com.mediatek.common.MPlugin;
import com.mediatek.common.telephony.ICallerInfoExt;
import com.mediatek.dialer.calllog.CallLogHighlighter;
import com.mediatek.dialer.util.DialerFeatureOptions;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.telephony.PhoneNumberUtils;
import android.os.SystemProperties;
import com.cootek.smartdialer_plugin_oem.CooTekSmartdialerOemModule;
import com.cootek.smartdialer_plugin_oem.IServiceStateCallback;
import com.cootek.smartdialer.aidl.CallerIdResult;
import com.cootek.smartdialer_plugin_oem.CooTekSmartdialerOemModule.CallerIdStrategy;
import com.android.contacts.activities.PeopleActivity;
import com.mediatek.dialer.activities.CallLogMultipleDeleteActivity;
import android.app.Activity;

// / Added by guofeiyao
import com.android.contacts.common.util.TelephonyManagerUtils;
// / End


/**
 * Helper class to fill in the views in {@link PhoneCallDetailsViews}.
 */
public class PhoneCallDetailsHelper implements IServiceStateCallback{
    /** The maximum number of icons will be shown to represent the call types in a group. */
    private static final int MAX_CALL_TYPE_ICONS = 3;

    private final Context mContext;
    private final Resources mResources;
    /** The injected current time in milliseconds since the epoch. Used only by tests. */
    private Long mCurrentTimeMillisForTest;
    // Helper classes.
    private final PhoneNumberDisplayHelper mPhoneNumberHelper;
    private final PhoneNumberUtilsWrapper mPhoneNumberUtilsWrapper;

    /**
     * List of items to be concatenated together for accessibility descriptions
     */
    private ArrayList<CharSequence> mDescriptionItems = Lists.newArrayList();
    private	List<String> CN = new ArrayList<String>();

	//add by zhangjinqiang --start
	private static CooTekSmartdialerOemModule csom;
	public  CallerIdResult mCallResult;
	//add by zjq end

    /**
     * Creates a new instance of the helper.
     * <p>
     * Generally you should have a single instance of this helper in any context.
     *
     * @param resources used to look up strings
     */
    public PhoneCallDetailsHelper(Context context, Resources resources,
            PhoneNumberUtilsWrapper phoneUtils) {
        mContext = context;
        mResources = resources;
        mPhoneNumberUtilsWrapper = phoneUtils;
        mPhoneNumberHelper = new PhoneNumberDisplayHelper(context, resources, phoneUtils);

        /// M: [CallLog Search] for CallLogSearch @{
        if (DialerFeatureOptions.CALL_LOG_SEARCH) {
            initHighlighter();
        }
        /// @}
		//HQ_wuruijun add for HQ01359274 start
		CN.add("10086");
		CN.add("10010");
		CN.add("10000");
		//HQ_wuruijun add for HQ01359274 end
		//add by zhangjinqiang --start
		Activity activity = (Activity)context;
		boolean isPeopleActivityInstance = activity  instanceof PeopleActivity;
		if(isPeopleActivityInstance){
			//PeopleActivity p=	(PeopleActivity)context;
			csom = PeopleActivity.getCsom();
		}
		boolean isLogDeleteInstance = activity instanceof CallLogMultipleDeleteActivity;
		if(isLogDeleteInstance){
			//CallLogMultipleDeleteActivity p=	(CallLogMultipleDeleteActivity)context;
			csom = CallLogMultipleDeleteActivity.getCooTekSDK();
		}
		//add by zhangjinqiang for al812--end		
    }

    /** Fills the call details views with content. */
    //modified by jinlibo for call log list scroll performance
    public void setPhoneCallDetails(PhoneCallDetailsViews views, PhoneCallDetails details, CharSequence typeOrLocation) {
        // Display up to a given number of icons.
        views.callTypeIcons.clear();
        int count = details.callTypes.length;
        boolean isVoicemail = false;
        for (int index = 0; index < count && index < MAX_CALL_TYPE_ICONS; ++index) {
            views.callTypeIcons.add(details.callTypes[index]);
            if (index == 0) {
                isVoicemail = details.callTypes[index] == Calls.VOICEMAIL_TYPE;
            }

			// / Added by guofeiyao
			// For 813 only one icon
			break;
			// / End
        }

        // Show the video icon if the call had video enabled.
        views.callTypeIcons.setShowVideo(
                (details.features & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO);
        views.callTypeIcons.requestLayout();
        views.callTypeIcons.setVisibility(View.VISIBLE);

        // Show the total call count only if there are more than the maximum number of icons.
        final Integer callCount;
        if (count > MAX_CALL_TYPE_ICONS) {
            callCount = count;
        } else {
            callCount = null;
        }
        //modified by jinlibo for call log list scroll performance
        CharSequence callLocationAndDate = getCallLocationAndDate(details, typeOrLocation);

        // Set the call count, location and date.
        setCallCountAndDate(views, callCount, callLocationAndDate);
        // Set the account label if it exists.
        String accountLabel = PhoneAccountUtils.getAccountLabel(mContext, details.accountHandle);

        if (!TextUtils.isEmpty(accountLabel)) {
			//modify by zhangjinqiang for al812--start
           // views.callAccountLabel.setVisibility(View.VISIBLE);
			//modify by zhangjinqiang for al812--end
            views.callAccountLabel.setText(accountLabel);
            int color = PhoneAccountUtils.getAccountColor(mContext, details.accountHandle);
            if (color == PhoneAccount.NO_HIGHLIGHT_COLOR) {
                int defaultColor = R.color.dialtacts_secondary_text_color;
                views.callAccountLabel.setTextColor(mContext.getResources().getColor(defaultColor));
            } else {
                views.callAccountLabel.setTextColor(color);
            }
        } else {
            views.callAccountLabel.setVisibility(View.GONE);
        }

        CharSequence nameText;
        final CharSequence displayNumber =
            mPhoneNumberHelper.getDisplayNumber(details.accountHandle, details.number,
                    details.numberPresentation, details.formattedNumber);
        if (TextUtils.isEmpty(details.name)) {
				if(csom!=null){
					mCallResult = csom.getCallerIdResult(details.number.toString(),CallerIdStrategy.OFFLINE_ONLY);
				}
				if(mCallResult!=null&&mCallResult.getName()!=null){
					nameText = mCallResult.getName();
					views.callNumber.setText(details.number.toString());
				}else{
                    nameText = displayNumber;
                  ////add by guojianhui for format calllog number start
					int nonSepNumLength = displayNumber.length();
					
					nameText = getFormatNumber(displayNumber.toString(), nonSepNumLength);
					////add by guojianhui for format calllog number end
					
                    String number = nameText.toString();
                    //maheling HQ01488449 2015.11.11 start
                    if (SystemProperties.get("ro.hq.emergency.display.latin").equals("1")) {
                        if ("911".equalsIgnoreCase(number) ||
                                "112".equalsIgnoreCase(number)) {
                            if ("73401".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc()) ||
                                    "73402".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc()) ||
                                    "73403".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc())) {
                                views.callNumber.setText("Emergencia");
                            } else {
                                views.callNumber.setText(R.string.unknow_number);
                            }
                        } else if ("171".equalsIgnoreCase(number)) {
						if ("73401".equalsIgnoreCase(PhoneNumberUtils
								.getSimMccMnc())
								|| "73404".equalsIgnoreCase(PhoneNumberUtils
										.getSimMccMnc())) {
                                views.callNumber.setText("Policia");
                            } else if ("73401".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc()) ||
                                    "73402".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc()) ||
                                    "73403".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc())) {
                                views.callNumber.setText("Emergencia Nacional");
                            } else {
                                views.callNumber.setText(R.string.unknow_number);
                            }
                        } else if ("*767".equalsIgnoreCase(number)) {
                            if ("73401".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc()) ||
                                    "73402".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc()) ||
                                    "73403".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc())) {
                                views.callNumber.setText("Emergencia 412");
                            } else {
                                views.callNumber.setText(R.string.unknow_number);
                            }
                        } else if ("190".equalsIgnoreCase(number)) {
                            if ("724".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc().substring(0, 3))) {
                                views.callNumber.setText("Polícia");
                            } else {
                                views.callNumber.setText(R.string.unknow_number);
                            }
                        } else {
                            views.callNumber.setText(R.string.unknow_number);
                        }
                    } else {
                        views.callNumber.setText(R.string.unknow_number);
                    }
                    //maheling HQ01488449 2015.11.11 end
                }
            // We have a real phone number as "nameView" so make it always LTR
            views.nameView.setTextDirection(View.TEXT_DIRECTION_LTR);
        } else {
            nameText = details.name;
            // modify by wangmingyue for number display
            String number = details.number.toString();
            
          //add by guojianhui for format calllog number start
			int nonSepNumLength = number.length();
			number = getFormatNumber(number, nonSepNumLength);
			
			////add by guojianhui for format calllog number end
            String langage = Locale.getDefault().getLanguage();
             if ((!TextUtils.isEmpty(number)) && langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw")) {
            	 number = "\u202D"+ number+ "\u202C";
             }
		   views.callNumber.setText(number);
		    // modify by wangmingyue end
        }

        /// M: [CallLog Search]for CallLog Search @{
        if (DialerFeatureOptions.CALL_LOG_SEARCH) {
            boolean onlyNumber = TextUtils.isEmpty(details.name);
            nameText = getHightlightedCallLogName(nameText.toString(),
                    mHighlightString, onlyNumber);
        }
        /// @}
            String num = displayNumber.toString();
            if(SystemProperties.get("ro.hq.custom.ecc").equals("1")){
		boolean isEmergencyNumber=PhoneNumberUtils.isEmergencyNumber(num);
		String operatorMccmnc=PhoneNumberUtils.getOperatorMccmnc();
		if(isEmergencyNumber){
            //maheling HQ01488449 2015.11.11 start
            if (SystemProperties.get("ro.hq.emergency.display.latin").equals("1")) {
                if ("911".equalsIgnoreCase(num) ||
                        "112".equalsIgnoreCase(num)) {
                    if ("73401".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc()) ||
                            "73402".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc()) ||
                            "73403".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc())) {
                        views.nameView.setText("Emergencia");
                    } else {
                        views.nameView.setText(R.string.emergency_call_dialog_number_for_display);
                    }
                } else if ("171".equalsIgnoreCase(num)) {
                    if ("73404".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc())) {
                        views.nameView.setText("Policia");
                    } else if ("73401".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc()) ||
                            "73402".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc()) ||
                            "73403".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc())) {
                        views.nameView.setText("Emergencia Nacional");
                    } else {
                        views.nameView.setText(R.string.emergency_call_dialog_number_for_display);
                    }
                } else if ("*767".equalsIgnoreCase(num)) {
                    if ("73401".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc()) ||
                            "73402".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc()) ||
                            "73403".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc())) {
                        views.nameView.setText("Emergencia 412");
                    } else {
                        views.nameView.setText(R.string.emergency_call_dialog_number_for_display);
                    }
                } else if ("190".equalsIgnoreCase(num)) {
                    if ("724".equalsIgnoreCase(PhoneNumberUtils.getSimMccMnc().substring(0, 3))) {
                        views.nameView.setText("Polícia");
                    } else {
                        views.nameView.setText(R.string.emergency_call_dialog_number_for_display);
                    }
                } else {
                	//modify by wangmingyue for HQ01617491 start
                	if ("911".equalsIgnoreCase(num) ||
                            "112".equalsIgnoreCase(num)) {
                        if ("73401".equalsIgnoreCase(operatorMccmnc) ||
                                "73402".equalsIgnoreCase(operatorMccmnc) ||
                                "73403".equalsIgnoreCase(operatorMccmnc)) {
                            views.nameView.setText("Emergencia");
                        } else {
                        	views.nameView.setText(R.string.emergency_call_dialog_number_for_display);
                        }
                	}
                    //modify by wangmingyue for HQ01617491 end
                }
            } else {
                views.nameView.setText(R.string.emergency_call_dialog_number_for_display);
            }
            //maheling HQ01488449 2015.11.11 end
			//add by huangshuo for HQ01367230 on 2015/10/02
			views.callNumber.setText(details.number);
 			//end by huangshuo for HQ01367230 on 2015/10/02
		}else{
		       views.nameView.setText(nameText);
		}
	     }
             /*
              else{
		 //modify by zhangjinqiang for al812--start
		   if(ECC.contains(num)){
			views.nameView.setText(R.string.hw_ecc);
		   }
             */
             else{
        		views.nameView.setText(nameText);
		   }
	     	/*}
        //modify by zhangjinqiang for al812--start
		/*
		String num = displayNumber.toString();
		if(ECC.contains(num)){
			views.nameView.setText(R.string.hw_ecc);
		}else{
        			views.nameView.setText(nameText);
			}
		*/
		//modify by zhangjinqiang for al812--end
		/*
		if(CN.contains(num)) {
			if (num.equals("10086")) {
				views.nameView.setText(R.string.cn_mobile);
			} else if (num.equals("10010")) {
				views.nameView.setText(R.string.cn_unicom);
			} else if (num.equals("10000")) {
				views.nameView.setText(R.string.cn_telecom);
			}
		}
		*/
		//HQ_wuruijun add for HQ01368171 start
		// / Modified by guofeiyao
		if (details.callTypes[0] == Calls.MISSED_TYPE) {
			views.nameView.setTextColor(Color.parseColor("#ff2e58"));

			int cnt = 0;
			Log.i("guofeiyao_PhoneHelper", ""+count);
			for(int i = 0;i<count;i++){
			    Log.i("guofeiyao_PhoneHelper", "cnt:"+i+" "+details.callTypes[i]);
                if(details.callTypes[i] == Calls.MISSED_TYPE) {
                   cnt++;
				}
			}
			views.nameView.setText(nameText+"("+ cnt +")");
			// / End
			
		} else {
			views.nameView.setTextColor(Color.parseColor("#333333"));
		}
		//HQ_wuruijun add end
        if (isVoicemail && !TextUtils.isEmpty(details.transcription)) {
            views.voicemailTranscriptionView.setText(details.transcription);
            views.voicemailTranscriptionView.setVisibility(View.VISIBLE);
        } else {
            views.voicemailTranscriptionView.setText(null);
            views.voicemailTranscriptionView.setVisibility(View.GONE);
        }
    }
    
    //add by guojianhui for format calllog number start 
    private String getFormatNumber(String number, int nonSepNumLength) {
    	String formatNumber = number;
    	if (SystemProperties.get("ro.hq.calllog.number.format").equals("1")) {
    		if(isSupportFormat()) { 
    			if (nonSepNumLength > 10) {
        			return  formatNumber.substring(0, 4) + " "
        					+ formatNumber.substring(4, 7) + " "
        					+ formatNumber.substring(7, 10) + " " + formatNumber.substring(10, nonSepNumLength);
        		} else if (nonSepNumLength > 7) {
        			return  formatNumber.substring(0, 4) + " "
        					+ formatNumber.substring(4, 7) + " "
        					+ formatNumber.substring(7, nonSepNumLength);
        		} else if (nonSepNumLength > 4) {
        			return  formatNumber.substring(0, 4) + " "
        					+ formatNumber.substring(4, nonSepNumLength);
        		} else {
        			return formatNumber;
        		}
    		}
    	} 
		// / Added by guofeiyao 2016/1/15
		else if ( SystemProperties.get("ro.hq.russia.fn").equals("1") ) {
		   //Log.e("duanze_PhoneCallDetailsHelper", "i18n number");
           String tmp = PhoneNumberHelper.formatNumber(formatNumber,
                                    TelephonyManagerUtils.getCurrentCountryIso(mContext, Locale.getDefault()));

		   // Really stupid, but you can't survive at that moment. :(
		   if (null!=tmp) {
		       formatNumber = tmp;
		   }
		}
		// / End
    	return formatNumber;
    }
    
    private boolean isSupportFormat() {
    	String mccmnc1 = PhoneNumberUtils.getSimMccMnc(0);
    	String mccmnc2 = PhoneNumberUtils.getSimMccMnc(1);
    	Log.d("guo", "isSupportFormat, mccmnc1: "+mccmnc1+", mccmnc2: "+mccmnc2);
    	if(mccmnc1 != null && mccmnc2 != null) {
    		if(mccmnc1.length() >= 3) {
    			if(mccmnc1.substring(0, 3).equalsIgnoreCase("505")) {
        			return true;
        		}
    		}
    		if(mccmnc2.length() >= 3) {
    			if(mccmnc2.substring(0, 3).equalsIgnoreCase("505")) {
        			return true;
        		}
    		}
    	}
    	
    	return false;
    }
  //add by guojianhui for format calllog number end
    
    /**
     * Builds a string containing the call location and date.
     * modified by jinlibo for call history list scroll performance
     * @param details The call details.
     * @return The call location and date string.
     */
    private CharSequence getCallLocationAndDate(PhoneCallDetails details, CharSequence callTypeOrLocation) {
        mDescriptionItems.clear();

        // Get type of call (ie mobile, home, etc) if known, or the caller's location.
        //modified by jinlibo for call history list scroll performance
//        CharSequence callTypeOrLocation = getCallTypeOrLocation(details);

        // Only add the call type or location if its not empty.  It will be empty for unknown
        // callers.
        if (!TextUtils.isEmpty(callTypeOrLocation)) {
            mDescriptionItems.add(callTypeOrLocation);
        }
        // The date of this call, relative to the current time.
        mDescriptionItems.add(getCallDate(details));

        // Create a comma separated list from the call type or location, and call date.
        return DialerUtils.join(mResources, mDescriptionItems);
    }

    /**
     * For a call, if there is an associated contact for the caller, return the known call type
     * (e.g. mobile, home, work).  If there is no associated contact, attempt to use the caller's
     * location if known.
     * @param details Call details to use.
     * @return Type of call (mobile/home) if known, or the location of the caller (if known).
     */
    public CharSequence getCallTypeOrLocation(PhoneCallDetails details) {
        CharSequence numberFormattedLabel = null;
        // Only show a label if the number is shown and it is not a SIP address.
        if (!TextUtils.isEmpty(details.number)
                && !PhoneNumberHelper.isUriNumber(details.number.toString())
                && !mPhoneNumberUtilsWrapper.isVoicemailNumber(details.accountHandle,
                        details.number)) {

            if (details.numberLabel == ContactInfo.GEOCODE_AS_LABEL) {
                numberFormattedLabel = details.geocode;
            } else {
                /// M: for plug-in @{
                /*
                numberFormattedLabel = Phone.getTypeLabel(mResources, details.numberType,
                        details.numberLabel);
                */
                ICallerInfoExt callerInfoExt = (ICallerInfoExt) MPlugin.createInstance(ICallerInfoExt.class.getName(),
                        DialerApplication.getDialerContext());
                final String id = details.accountHandle == null ? null : details.accountHandle.getId();
                if (callerInfoExt != null) {
                    return callerInfoExt.getTypeLabel(DialerApplication.getDialerContext(),
                            details.numberType, details.numberLabel, null, (!TextUtils.isEmpty(id))
                                    && TextUtils.isDigitsOnly(id) ? Integer.parseInt(id) : -1);
                } else {
                    return Phone.getTypeLabel(mResources, details.numberType,
                            details.numberLabel);
                }
                /// @}
            }
        }

        if (!TextUtils.isEmpty(details.name) && TextUtils.isEmpty(numberFormattedLabel)) {
            numberFormattedLabel = mPhoneNumberHelper.getDisplayNumber(details.accountHandle,
                    details.number, details.numberPresentation, details.formattedNumber);
        }
        return numberFormattedLabel;
    }

    /**
     * Get the call date/time of the call, relative to the current time.
     * e.g. 3 minutes ago
     * @param details Call details to use.
     * @return String representing when the call occurred.
     */
    public CharSequence getCallDate(PhoneCallDetails details) {
        return DateUtils.getRelativeTimeSpanString(details.date,
                getCurrentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE);
    }

    /** Sets the text of the header view for the details page of a phone call. */
    @NeededForTesting
    public void setCallDetailsHeader(TextView nameView, PhoneCallDetails details) {
        final CharSequence nameText;
        final CharSequence displayNumber =
            mPhoneNumberHelper.getDisplayNumber(details.accountHandle, details.number,
                    details.numberPresentation, mResources.getString(R.string.recentCalls_addToContact));
        if (TextUtils.isEmpty(details.name)) {
            nameText = displayNumber;
        } else {
            nameText = details.name;
        }

        nameView.setText(nameText);
    }

    @NeededForTesting
    public void setCurrentTimeForTest(long currentTimeMillis) {
        mCurrentTimeMillisForTest = currentTimeMillis;
    }

    /**
     * Returns the current time in milliseconds since the epoch.
     * <p>
     * It can be injected in tests using {@link #setCurrentTimeForTest(long)}.
     */
    private long getCurrentTimeMillis() {
        if (mCurrentTimeMillisForTest == null) {
            return System.currentTimeMillis();
        } else {
            return mCurrentTimeMillisForTest;
        }
    }

    /** Sets the call count and date. */
    private void setCallCountAndDate(PhoneCallDetailsViews views, Integer callCount,
            CharSequence dateText) {
        // Combine the count (if present) and the date.
        final CharSequence text;
        if (callCount != null) {
            text = mResources.getString(
                    R.string.call_log_item_count_and_date, callCount.intValue(), dateText);
        } else {
            text = dateText;
        }

        views.callLocationAndDate.setText(text);
    }

    //----------------------------Mediatek-------------------------------------
    /// M: [CallLog Search] for CallLog search @{
    private CallLogHighlighter mHighlighter;
    private char[] mHighlightString;

    private void initHighlighter() {
        //TypedArray a = mContext.obtainStyledAttributes(null, R.styleable.CallLog);
        /// M: TODO check the resource color available?
        /*
        mHighlighter = new CallLogHighlighter(a.getColor(
                R.styleable.ContactListItemView_list_item_prefix_highlight_color, Color.GREEN));
        a.recycle();
        */
        mHighlighter = new CallLogHighlighter(Color.GREEN);
    }

    public void setHighlightedText(char[] highlightedText) {
        mHighlightString = highlightedText;
    }

    private String getHightlightedCallLogName(String text, char[] highlightText, boolean isOnlyNumber) {
        String name = text;
        if (isOnlyNumber) {
            name = mHighlighter.applyNumber(text, highlightText).toString();
        } else {
            name = mHighlighter.applyName(text, highlightText).toString();
        }
        return name;
    }
    /// @}
    
	//add by zhangjinqiang for al812 -start		
		@Override
		public void onServiceConnected() {
	//		Toast.makeText(PeopleActivity.this, "号码助手Service通信成功！", Toast.LENGTH_SHORT).show();  
		}
		
		@Override
		public void onServiceDisconnected() {
			//Toast.makeText(PeopleActivity.this, "号码助手Service通信连接失败！！", Toast.LENGTH_LONG).show(); 
		}
	//add by zjq end
}
