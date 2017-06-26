/*
 * Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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
 * limitations under the License
 */

package com.android.contacts.common;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.phone.common.PhoneConstants;
import com.android.contacts.common.util.Constants;

import android.os.*;
/**
 * Utilities related to calls.
 */
public class CallUtil {
    ///M: @{
    public static final String SCHEME_SMSTO = "smsto";
    public static final String SCHEME_MAILTO = "mailto";
    private static final String TAG = "CallUtil";
    ///@}

    /**
     * Return an Intent for making a phone call. Scheme (e.g. tel, sip) will be determined
     * automatically.
     */
    public static Intent getCallIntent(String number,Context context) {
    	
    	
        number = claroSpecialOperator(number, context);
    	
        return getCallIntent(number, null, null);
    }

/**
 * 哥伦比亚claro需求，漫游的时候+57，否则保持
 * @param number
 * @param context
 * @return
 */
	public static String claroSpecialOperator(String number, Context context) {
//		Log.i("tang", "ro.hq.claro.columbia"+SystemProperties.get("ro.hq.claro.columbia"));
		String version=android.os.SystemProperties.get("ro.build.version");
		if (version.contains("L03")) {
//		if (SystemProperties.get("ro.hq.claro.columbia").equals("1")) {
			TelephonyManager telManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
			String plmn = telManager.getNetworkOperator();
//			String plmn = "722310";//732101 
			String mccmnc=PhoneNumberUtils.getSimMccMnc();
//			String mccmnc="732101";//732101
			Log.e("tang", "mccmnc:" + mccmnc + " plmn:" + plmn);
			
			if (plmn != null && mccmnc != null && mccmnc.startsWith("732")
					&& plmn.equals("732101")) {
					if (!number.startsWith("+57")) {
						number = dealWithNum(number, plmn);
					}
			}else	if (plmn != null && mccmnc != null && mccmnc.startsWith("732")
					&& !plmn.startsWith("732")) {
				if (!number.startsWith("+57")) {
					number = dealWithNum(number, plmn);
				}
			} else if (plmn != null && mccmnc != null
					&& mccmnc.startsWith("732") && plmn.startsWith("732")) {
				if (number.startsWith("+57")) {
					number = number.substring(3);
				}
			}
		}
		return number;
	}
    
    
    public static Intent getCallIntent(String number) {
        return getCallIntent(number, null, null);
    }
    

    /**
     * Return an Intent for making a phone call. A given Uri will be used as is (without any
     * sanity check).
     */
    public static Intent getCallIntent(Uri uri) {
        return getCallIntent(uri, null, null);
    }

    /**
     * A variant of {@link #getCallIntent(String)} but also accept a call origin.
     * For more information about call origin, see comments in Phone package (PhoneApp).
     */
    public static Intent getCallIntent(String number, String callOrigin) {
        return getCallIntent(getCallUri(number), callOrigin, null);
    }

    /**
     * A variant of {@link #getCallIntent(String)} but also include {@code Account}.
     */
    public static Intent getCallIntent(String number, PhoneAccountHandle accountHandle) {
        return getCallIntent(number, null, accountHandle);
    }

    /**
     * A variant of {@link #getCallIntent(android.net.Uri)} but also include {@code Account}.
     */
    public static Intent getCallIntent(Uri uri, PhoneAccountHandle accountHandle) {
        return getCallIntent(uri, null, accountHandle);
    }

    /**
     * A variant of {@link #getCallIntent(String, String)} but also include {@code Account}.
     */
    public static Intent getCallIntent(
            String number, String callOrigin, PhoneAccountHandle accountHandle) {
        return getCallIntent(getCallUri(number), callOrigin, accountHandle);
    }

    /**
     * A variant of {@link #getCallIntent(android.net.Uri)} but also accept a call
     * origin and {@code Account}.
     * For more information about call origin, see comments in Phone package (PhoneApp).
     */
    public static Intent getCallIntent(
            Uri uri, String callOrigin, PhoneAccountHandle accountHandle) {
        return getCallIntent(uri, callOrigin, accountHandle,
                VideoProfile.VideoState.AUDIO_ONLY);
    }

    /**
     * A variant of {@link #getCallIntent(String, String)} for starting a video call.
     */
    public static Intent getVideoCallIntent(String number, String callOrigin) {
        return getCallIntent(getCallUri(number), callOrigin, null,
                VideoProfile.VideoState.BIDIRECTIONAL);
    }

    /**
     * A variant of {@link #getCallIntent(String, String, android.telecom.PhoneAccountHandle)} for
     * starting a video call.
     */
    public static Intent getVideoCallIntent(
            String number, String callOrigin, PhoneAccountHandle accountHandle) {
        return getCallIntent(getCallUri(number), callOrigin, accountHandle,
                VideoProfile.VideoState.BIDIRECTIONAL);
    }

    /**
     * A variant of {@link #getCallIntent(String, String, android.telecom.PhoneAccountHandle)} for
     * starting a video call.
     */
    public static Intent getVideoCallIntent(String number, PhoneAccountHandle accountHandle) {
        return getVideoCallIntent(number, null, accountHandle);
    }

    /**
     * A variant of {@link #getCallIntent(android.net.Uri)} for calling Voicemail.
     */
    public static Intent getVoicemailIntent() {
        return getCallIntent(Uri.fromParts(PhoneAccount.SCHEME_VOICEMAIL, "", null));
    }

    /**
     * A variant of {@link #getCallIntent(android.net.Uri)} but also accept a call
     * origin and {@code Account} and {@code VideoCallProfile} state.
     * For more information about call origin, see comments in Phone package (PhoneApp).
     */
    public static Intent getCallIntent(
            Uri uri, String callOrigin, PhoneAccountHandle accountHandle, int videoState) {
        final Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, uri);
        intent.putExtra(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, videoState);
        if (callOrigin != null) {
            intent.putExtra(PhoneConstants.EXTRA_CALL_ORIGIN, callOrigin);
        }
        if (accountHandle != null) {
            intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle);
        }

        return intent;
    }

    /**
     * Return Uri with an appropriate scheme, accepting both SIP and usual phone call
     * numbers.
     */
    public static Uri getCallUri(String number) {
        if (PhoneNumberHelper.isUriNumber(number)) {
             return Uri.fromParts(PhoneAccount.SCHEME_SIP, number, null);
        }
        return Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
     }

    public static boolean isVideoEnabled(Context context) {
        TelecomManager telecommMgr = (TelecomManager)
                context.getSystemService(Context.TELECOM_SERVICE);
        if (telecommMgr == null) {
            return false;
        }

        // TODO: Check telecommManager for value instead.
        // return telecommMgr.isVideoEnabled();
        return false;
    }

    /**
     * M
     * @param uri
     * @param callOrigin
     * @param type
     * @return
     */
    public static Intent getCallIntent(Uri uri, String callOrigin, int type) {
        final Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, uri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (callOrigin != null) {
            intent.putExtra(PhoneConstants.EXTRA_CALL_ORIGIN, callOrigin);
        }
        if ((type & Constants.DIAL_NUMBER_INTENT_IP) != 0) {
            intent.putExtra(Constants.EXTRA_IS_IP_DIAL, true);
        }

        if ((type & Constants.DIAL_NUMBER_INTENT_VIDEO) != 0) {
            intent.putExtra(Constants.EXTRA_IS_VIDEO_CALL, true);
        }

        //M: VOLTE IMS Call feature @{
        if ((type & Constants.DIAL_NUMBER_INTENT_IMS) != 0) {
            Log.d(TAG, "VOLTE Ims Call put extra 'com.mediatek.phone.extra.ims' true.");
            intent.putExtra(Constants.EXTRA_IS_IMS_CALL, true);
        }
        ///@}
        return intent;
    }
    
	public  static String dealWithNum(String number, String plmn) {
		String newNum = number.replace(" ", "").trim();
		//Log.d("tang","the num is "+ newNum);
		if(newNum == null ){
			Log.d("tang", ">>>phone number is null 跳过, ");
			return newNum;
		}
		if (newNum.length() != 10) {// 对sim卡的联系人号码不是10位的，不做任何处理，保留原样。
			if (newNum.equals("*123") || newNum.equals("*611")) {
				//Log.d("tang","the num is "+ newNum);
				for (String Specialplmn : SpecialMccMnc) {
					if (Specialplmn.equals(plmn)) {
						Log.d("tang", ">>>>>>" + "start with *123 ");
						return  newNum;
					}
				}
				newNum = "+573103333333";
				return  newNum;
			}
			Log.d("tang", ">>>phone number lengh is not 10，continue ,the length is  "+newNum.length());
			return newNum;
		}
		for (String Specialplmn : SpecialMccMnc) {
			if (Specialplmn.equals(plmn)) {
				Log.d("tang", ">>>>>>111" + "在列表中，第一种情况 ");
				if (number.startsWith("3")) {
					Log.d("tang", ">>>>>>" + "start with 3 ");
					newNum = "+57" + number;
				} else if (number.startsWith("0")) {
					Log.d("tang", ">>>>>>" + "start with 0 ");
					newNum = "+57" + number.substring(2);
				}

			}

		}
		if (newNum.equals(number)) {// 说明不是第一种情况
			Log.d("tang", ">>>>>>222" + "不在列表中，第二种情况 ");
			if (number.startsWith("3")) {
				Log.d("tang", ">>>>>>" + "start with 3 ");
				newNum = "+57" + number;
			} else if (number.startsWith("03")) {
				Log.d("tang", ">>>>>>" + "start with 03 ");
				newNum = "+57" + number.substring(2);
			} else if (number.equals("*123") || number.equals("*611")) {
				Log.d("tang", ">>>>>>" + "start with *123 ");
				newNum = "+573103333333";
			}
		}
		return newNum;
	}
	public static final String[] SpecialMccMnc = { "732101", "722310",
		"72405", "73003", "74001", "70601","708001", "338070", "334020", "71073",
		"71403", "74402", "71610", "330110", "37002", "74810"};
	
/*	733101
	722310
	72405
	73003
	74001
	70601
	70401
	708001
	338070
	334020
	71073
	71403
	74402
	71610
	330110
	37002
	74810*/
}
