/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/**
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

package com.android.services.telephony;

import android.content.Context;
import android.media.ToneGenerator;
/// M: CC020: [ALPS01808788] Not send POWER OFF error description to Telecom when modem reset @{
import android.provider.Settings;
/// @}
import android.telecom.DisconnectCause;

import com.android.phone.PhoneGlobals;
import com.android.phone.common.R;
import android.os.SystemProperties;

public class DisconnectCauseUtil {

   /**
    * Converts from a disconnect code in {@link android.telephony.DisconnectCause} into a more generic
    * {@link android.telecom.DisconnectCause}.object, possibly populated with a localized message
    * and tone.
    *
    * @param context The context.
    * @param telephonyDisconnectCause The code for the reason for the disconnect.
    */
    public static DisconnectCause toTelecomDisconnectCause(int telephonyDisconnectCause) {
        return toTelecomDisconnectCause(telephonyDisconnectCause, null /* reason */);
    }

   /**
    * Converts from a disconnect code in {@link android.telephony.DisconnectCause} into a more generic
    * {@link android.telecom.DisconnectCause}.object, possibly populated with a localized message
    * and tone.
    *
    * @param context The context.
    * @param telephonyDisconnectCause The code for the reason for the disconnect.
    * @param reason Description of the reason for the disconnect, not intended for the user to see..
    */
    public static DisconnectCause toTelecomDisconnectCause(
            int telephonyDisconnectCause, String reason) {
        Context context = PhoneGlobals.getInstance();
        return new DisconnectCause(
                toTelecomDisconnectCauseCode(telephonyDisconnectCause),
                toTelecomDisconnectCauseLabel(context, telephonyDisconnectCause),
                toTelecomDisconnectCauseDescription(context, telephonyDisconnectCause),
                toTelecomDisconnectReason(telephonyDisconnectCause, reason),
                toTelecomDisconnectCauseTone(telephonyDisconnectCause));
    }

    /**
     * Convert the {@link android.telephony.DisconnectCause} disconnect code into a
     * {@link android.telecom.DisconnectCause} disconnect code.
     * @return The disconnect code as defined in {@link android.telecom.DisconnectCause}.
     */
    private static int toTelecomDisconnectCauseCode(int telephonyDisconnectCause) {
        switch (telephonyDisconnectCause) {
            case android.telephony.DisconnectCause.LOCAL:
                return DisconnectCause.LOCAL;

            case android.telephony.DisconnectCause.NORMAL:
                return DisconnectCause.REMOTE;

            case android.telephony.DisconnectCause.OUTGOING_CANCELED:
            /// M: CC021: Error message due to CellConnMgr checking @{
            case android.telephony.DisconnectCause.OUTGOING_CANCELED_BY_SERVICE:
            /// @}
                return DisconnectCause.CANCELED;

            case android.telephony.DisconnectCause.INCOMING_MISSED:
                return DisconnectCause.MISSED;

            case android.telephony.DisconnectCause.INCOMING_REJECTED:
                return DisconnectCause.REJECTED;

            case android.telephony.DisconnectCause.BUSY:
                return DisconnectCause.BUSY;

            case android.telephony.DisconnectCause.CALL_BARRED:
            case android.telephony.DisconnectCause.CDMA_ACCESS_BLOCKED:
            case android.telephony.DisconnectCause.CDMA_NOT_EMERGENCY:
            case android.telephony.DisconnectCause.CS_RESTRICTED:
            case android.telephony.DisconnectCause.CS_RESTRICTED_EMERGENCY:
            case android.telephony.DisconnectCause.CS_RESTRICTED_NORMAL:
            case android.telephony.DisconnectCause.EMERGENCY_ONLY:
            case android.telephony.DisconnectCause.FDN_BLOCKED:
            case android.telephony.DisconnectCause.LIMIT_EXCEEDED:
                return DisconnectCause.RESTRICTED;

            case android.telephony.DisconnectCause.CDMA_ACCESS_FAILURE:
            case android.telephony.DisconnectCause.CDMA_CALL_LOST:
            case android.telephony.DisconnectCause.CDMA_DROP:
            case android.telephony.DisconnectCause.CDMA_INTERCEPT:
            case android.telephony.DisconnectCause.CDMA_LOCKED_UNTIL_POWER_CYCLE:
            case android.telephony.DisconnectCause.CDMA_PREEMPTED:
            case android.telephony.DisconnectCause.CDMA_REORDER:
            case android.telephony.DisconnectCause.CDMA_RETRY_ORDER:
            case android.telephony.DisconnectCause.CDMA_SO_REJECT:
            case android.telephony.DisconnectCause.CONGESTION:
            case android.telephony.DisconnectCause.ICC_ERROR:
            case android.telephony.DisconnectCause.INVALID_CREDENTIALS:
            case android.telephony.DisconnectCause.INVALID_NUMBER:
            case android.telephony.DisconnectCause.LOST_SIGNAL:
            case android.telephony.DisconnectCause.NO_PHONE_NUMBER_SUPPLIED:
            case android.telephony.DisconnectCause.NUMBER_UNREACHABLE:
            case android.telephony.DisconnectCause.OUTGOING_FAILURE:
            case android.telephony.DisconnectCause.OUT_OF_NETWORK:
            case android.telephony.DisconnectCause.OUT_OF_SERVICE:
            case android.telephony.DisconnectCause.POWER_OFF:
            case android.telephony.DisconnectCause.SERVER_ERROR:
            case android.telephony.DisconnectCause.SERVER_UNREACHABLE:
            case android.telephony.DisconnectCause.TIMED_OUT:
            case android.telephony.DisconnectCause.UNOBTAINABLE_NUMBER:
            case android.telephony.DisconnectCause.VOICEMAIL_NUMBER_MISSING:
            case android.telephony.DisconnectCause.ERROR_UNSPECIFIED:
            /// M: CC022: Error message due to VoLTE SS checking @{
            case android.telephony.DisconnectCause.VOLTE_SS_DATA_OFF:
	//add by huangshuo for HQ01454913
	    case android.telephony.DisconnectCause.CHANNEL_NOT_AVAIL:
	    case android.telephony.DisconnectCause.NO_USER_RESPONDING:
	    case android.telephony.DisconnectCause.USER_ALERTING_NO_ANSWER:
	    case android.telephony.DisconnectCause.NORMAL_UNSPECIFIED:
	    case android.telephony.DisconnectCause.NO_CIRCUIT_AVAIL:
            /// @}
           //end by huangshuo for HQ01454913

           // / Added by guofeiyao 2015/12/12
           // For Telcel clear code
           case android.telephony.DisconnectCause.NO_ROUTE_TO_DESTINATION: //3
           case android.telephony.DisconnectCause.CHANNEL_UNACCEPTABLE: //6
           case android.telephony.DisconnectCause.OPERATOR_DETERMINED_BARRING: //8
           case android.telephony.DisconnectCause.NORMAL_CLEARING: //16
           case android.telephony.DisconnectCause.CALL_REJECTED: //21
           case android.telephony.DisconnectCause.NUMBER_CHANGED: //22
           case android.telephony.DisconnectCause.PRE_EMPTION: //25
           case android.telephony.DisconnectCause.NON_SELECTED_USER_CLEARING: //26
           case android.telephony.DisconnectCause.DESTINATION_OUT_OF_ORDER: //27
           case android.telephony.DisconnectCause.INVALID_NUMBER_FORMAT: //28
           case android.telephony.DisconnectCause.FACILITY_REJECTED: //29
           case android.telephony.DisconnectCause.STATUS_ENQUIRY: //30
           case android.telephony.DisconnectCause.NETWORK_OUT_OF_ORDER: //38
           case android.telephony.DisconnectCause.SWITCHING_CONGESTION: //42
           case android.telephony.DisconnectCause.ACCESS_INFORMATION_DISCARDED: //43
           case android.telephony.DisconnectCause.RESOURCE_UNAVAILABLE: //47
           case android.telephony.DisconnectCause.REQUESTED_FACILITY_NOT_SUBSCRIBED: //50
           case android.telephony.DisconnectCause.INCOMING_CALL_BARRED_WITHIN_CUG: //55
		   case android.telephony.DisconnectCause.BEARER_NOT_AUTHORIZED: //57
		   case android.telephony.DisconnectCause.BEARER_NOT_AVAIL: //58
		   case android.telephony.DisconnectCause.SERVICE_NOT_AVAILABLE: //63
		   case android.telephony.DisconnectCause.BEARER_NOT_IMPLEMENT: //65
		   case android.telephony.DisconnectCause.FACILITY_NOT_IMPLEMENT: //69
		   case android.telephony.DisconnectCause.RESTRICTED_BEARER_AVAILABLE: //70
		   case android.telephony.DisconnectCause.OPTION_NOT_AVAILABLE: //79
		   case android.telephony.DisconnectCause.INVALID_TRANSACTION_ID_VALUE: //81
		   case android.telephony.DisconnectCause.USER_NOT_MEMBER_OF_CUG: //87
		   case android.telephony.DisconnectCause.INCOMPATIBLE_DESTINATION: //88
		   case android.telephony.DisconnectCause.INVALID_TRANSIT_NETWORK_SELECTION: //91;
    case android.telephony.DisconnectCause.SEMANTICALLY_INCORRECT_MESSAGE: //95;
    case android.telephony.DisconnectCause.INVALID_MANDATORY_INFORMATION: //96;
    case android.telephony.DisconnectCause.MESSAGE_TYPE_NON_EXISTENT: //97;
	case android.telephony.DisconnectCause.MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROT_STATE: //98;
    case android.telephony.DisconnectCause.IE_NON_EXISTENT_OR_NOT_IMPLEMENTED: //99;
    case android.telephony.DisconnectCause.CONDITIONAL_IE_ERROR: //100;
    case android.telephony.DisconnectCause.MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE: //101;
    case android.telephony.DisconnectCause.RECOVERY_ON_TIMER_EXPIRY: //102;
    case android.telephony.DisconnectCause.PROTOCOL_ERROR_UNSPECIFIED: //111;
    case android.telephony.DisconnectCause.INTERWORKING_UNSPECIFIED: //127;
           // / End
			
                return DisconnectCause.ERROR;

            case android.telephony.DisconnectCause.DIALED_MMI:
            case android.telephony.DisconnectCause.EXITED_ECM:
            case android.telephony.DisconnectCause.MMI:
            case android.telephony.DisconnectCause.IMS_MERGED_SUCCESSFULLY:
                return DisconnectCause.OTHER;

            ///M: WFC @{
            case android.telephony.DisconnectCause.WFC_WIFI_SIGNAL_LOST:
            case android.telephony.DisconnectCause.WFC_ISP_PROBLEM:
            case android.telephony.DisconnectCause.WFC_HANDOVER_WIFI_FAIL:
            case android.telephony.DisconnectCause.WFC_HANDOVER_LTE_FAIL:
                  return DisconnectCause.WFC_CALL_ERROR;
            /// @}

            case android.telephony.DisconnectCause.NOT_VALID:
            case android.telephony.DisconnectCause.NOT_DISCONNECTED:
                return DisconnectCause.UNKNOWN;

            default:
                Log.w("DisconnectCauseUtil.toTelecomDisconnectCauseCode",
                        "Unrecognized Telephony DisconnectCause "
                        + telephonyDisconnectCause);
                return DisconnectCause.UNKNOWN;
        }
    }

    /**
     * Returns a label for to the disconnect cause to be shown to the user.
     */
    private static CharSequence toTelecomDisconnectCauseLabel(
            Context context, int telephonyDisconnectCause) {
        if (context == null ) {
            return "";
        }

        Integer resourceId = null;
        switch (telephonyDisconnectCause) {
            case android.telephony.DisconnectCause.BUSY:
                resourceId = R.string.callFailed_userBusy;
                break;

            case android.telephony.DisconnectCause.CONGESTION:
                resourceId = R.string.callFailed_congestion;
                break;

            case android.telephony.DisconnectCause.TIMED_OUT:
                resourceId = R.string.callFailed_timedOut;
                break;

            case android.telephony.DisconnectCause.SERVER_UNREACHABLE:
                resourceId = R.string.callFailed_server_unreachable;
                break;

            case android.telephony.DisconnectCause.NUMBER_UNREACHABLE:
                resourceId = R.string.callFailed_number_unreachable;
                break;

            case android.telephony.DisconnectCause.INVALID_CREDENTIALS:
                resourceId = R.string.callFailed_invalid_credentials;
                break;

            case android.telephony.DisconnectCause.SERVER_ERROR:
                resourceId = R.string.callFailed_server_error;
                break;

            case android.telephony.DisconnectCause.OUT_OF_NETWORK:
                resourceId = R.string.callFailed_out_of_network;
                break;

            case android.telephony.DisconnectCause.LOST_SIGNAL:
            case android.telephony.DisconnectCause.CDMA_DROP:
                resourceId = R.string.callFailed_noSignal;
                break;

            case android.telephony.DisconnectCause.LIMIT_EXCEEDED:
                resourceId = R.string.callFailed_limitExceeded;
                break;

            case android.telephony.DisconnectCause.POWER_OFF:
                resourceId = R.string.callFailed_powerOff;
                break;

            case android.telephony.DisconnectCause.ICC_ERROR:
                resourceId = R.string.callFailed_simError;
                break;

            case android.telephony.DisconnectCause.OUT_OF_SERVICE:
                resourceId = R.string.callFailed_outOfService;
                break;

            case android.telephony.DisconnectCause.INVALID_NUMBER:
            case android.telephony.DisconnectCause.UNOBTAINABLE_NUMBER:
                resourceId = R.string.callFailed_unobtainable_number;
                break;

            /// M: WFC @{
            case android.telephony.DisconnectCause.WFC_WIFI_SIGNAL_LOST:
                resourceId = R.string.wfc_wifi_call_drop;
                break;
            case android.telephony.DisconnectCause.WFC_ISP_PROBLEM:
                resourceId = R.string.wfc_internet_connection_lost;
                break;
            case android.telephony.DisconnectCause.WFC_HANDOVER_WIFI_FAIL:
                resourceId = R.string.wfc_wifi_call_drop;
                break;
            case android.telephony.DisconnectCause.WFC_HANDOVER_LTE_FAIL:
                resourceId = R.string.wfc_no_network;
                break;
            ///@}

            default:
                break;
        }
        return resourceId == null ? "" : context.getResources().getString(resourceId);
    }

    /**
     * Returns a description of the disconnect cause to be shown to the user.
     */
    private static CharSequence toTelecomDisconnectCauseDescription(
            Context context, int telephonyDisconnectCause) {
        if (context == null ) {
            return "";
        }
	Integer resourceId = null;
        switch (telephonyDisconnectCause) {
            case android.telephony.DisconnectCause.CALL_BARRED:
                resourceId = R.string.callFailed_cb_enabled;
                break;

            case android.telephony.DisconnectCause.FDN_BLOCKED:
                resourceId = R.string.callFailed_fdn_only;
                break;

            case android.telephony.DisconnectCause.CS_RESTRICTED:
                resourceId = R.string.callFailed_dsac_restricted;
                break;

            case android.telephony.DisconnectCause.CS_RESTRICTED_EMERGENCY:
                resourceId = R.string.callFailed_dsac_restricted_emergency;
                break;

            case android.telephony.DisconnectCause.CS_RESTRICTED_NORMAL:
                resourceId = R.string.callFailed_dsac_restricted_normal;
                break;

            case android.telephony.DisconnectCause.OUTGOING_FAILURE:
                // We couldn't successfully place the call; there was some
                // failure in the telephony layer.
                // TODO: Need UI spec for this failure case; for now just
                // show a generic error.
                resourceId = R.string.incall_error_call_failed;
                break;

            case android.telephony.DisconnectCause.POWER_OFF:
                // Radio is explictly powered off, presumably because the
                // device is in airplane mode.
                //
                // TODO: For now this UI is ultra-simple: we simply display
                // a message telling the user to turn off airplane mode.
                // But it might be nicer for the dialog to offer the option
                // to turn the radio on right there (and automatically retry
                // the call once network registration is complete.)

                /// M: CC020: Not send POWER OFF error description to Telecom when modem reset @{
                // Avoid to send power off error description (UI show turn off
                // airplane mdoe) to Telecom when modem reset because airplane mode
                // is off(power on) in Setting actually.
                if (Settings.Global.getInt(context.getContentResolver(),
                        Settings.Global.AIRPLANE_MODE_ON, 0) > 0) {
                    resourceId = R.string.incall_error_power_off;
                }
                /// @}
                break;

            case android.telephony.DisconnectCause.EMERGENCY_ONLY:
                // Only emergency numbers are allowed, but we tried to dial
                // a non-emergency number.
                resourceId = R.string.incall_error_emergency_only;
                break;

            case android.telephony.DisconnectCause.OUT_OF_SERVICE:
                // No network connection.
                resourceId = R.string.incall_error_out_of_service;
                break;

            case android.telephony.DisconnectCause.NO_PHONE_NUMBER_SUPPLIED:
                // The supplied Intent didn't contain a valid phone number.
                // (This is rare and should only ever happen with broken
                // 3rd-party apps.) For now just show a generic error.
                resourceId = R.string.incall_error_no_phone_number_supplied;
                break;

            case android.telephony.DisconnectCause.VOICEMAIL_NUMBER_MISSING:
                // TODO: Need to bring up the "Missing Voicemail Number" dialog, which
                // will ultimately take us to the Call Settings.
                resourceId = R.string.incall_error_missing_voicemail_number;
                break;

            /// M: CC022: Error message due to VoLTE SS checking @{
            case android.telephony.DisconnectCause.VOLTE_SS_DATA_OFF:
                resourceId = R.string.volte_ss_not_available_tips;
                break;
            /// @}
            /// M: CC021: Error message due to CellConnMgr checking @{
            case android.telephony.DisconnectCause.OUTGOING_CANCELED_BY_SERVICE:
            /// @}
            case android.telephony.DisconnectCause.OUTGOING_CANCELED:
                // We don't want to show any dialog for the canceled case since the call was
                // either canceled by the user explicitly (end-call button pushed immediately)
                // or some other app canceled the call and immediately issued a new CALL to
                // replace it.
            ///M: WFC @{
            case android.telephony.DisconnectCause.WFC_WIFI_SIGNAL_LOST:
                resourceId = R.string.wfc_wifi_call_drop_summary;
                break;
            case android.telephony.DisconnectCause.WFC_ISP_PROBLEM:
                resourceId = R.string.wfc_internet_lost_summary;
                break;
            case android.telephony.DisconnectCause.WFC_HANDOVER_WIFI_FAIL:
                resourceId = R.string.wfc_wifi_handover_fail;
                break;
            case android.telephony.DisconnectCause.WFC_HANDOVER_LTE_FAIL:
                resourceId = R.string.wfc_wifi_lte_handover_fail;
                break;
            /// @}
            /// M: ALPS02151583. UI doesn't show response when dialing an invalid number. @{
            case android.telephony.DisconnectCause.NUMBER_UNREACHABLE:
                resourceId = R.string.callFailed_number_unreachable;
                break;
            /// @}
            default:
                break;
        }
     //add by huangshuo for HQ01454913
	  if(SystemProperties.get("ro.hq.claro.call.clearcode").equals("1")){	
	   	Log.i("huangshuo1", "[toTelecomDisconnectCauseDescription]:telephonyDisconnectCause1" +telephonyDisconnectCause);
		if (telephonyDisconnectCause == android.telephony.DisconnectCause.UNOBTAINABLE_NUMBER) {					//1;
			resourceId = R.string.disconnection_cause_outer_1;
		} else if (telephonyDisconnectCause == android.telephony.DisconnectCause.BUSY) {							//17;
			resourceId = R.string.disconnection_cause_outer_17;
			Log.i("huangshuo1", "[toTelecomDisconnectCauseDescription]:resourceId" +context.getResources().getString(resourceId));
		} else if (telephonyDisconnectCause == android.telephony.DisconnectCause.NO_USER_RESPONDING) { 				//18;
			resourceId = R.string.disconnection_cause_outer_18;
		} else if (telephonyDisconnectCause == android.telephony.DisconnectCause.USER_ALERTING_NO_ANSWER) {			//19;
			resourceId = R.string.disconnection_cause_outer_19;
		} else if (telephonyDisconnectCause == android.telephony.DisconnectCause.NORMAL) { 				//31;
			resourceId = R.string.disconnection_cause_outer_31;
		} else if (telephonyDisconnectCause == android.telephony.DisconnectCause.NO_CIRCUIT_AVAIL) {				//34;
			resourceId = R.string.disconnection_cause_outer_34;
		} else if (telephonyDisconnectCause == android.telephony.DisconnectCause.CHANNEL_NOT_AVAIL) {				//44;
			resourceId = R.string.disconnection_cause_outer_44;
		}
	   }	
     //end by huangshuo for HQ01454913

     // / Added by guofeiyao 2015/12/12
     // For 813 Telcel clear code
     if ( SystemProperties.get("ro.hq.clear.code").equals("1") ) {
	 	  int clCode = 0;
		  if (telephonyDisconnectCause == android.telephony.DisconnectCause.UNOBTAINABLE_NUMBER) {					//1;
			   resourceId = R.string.disconnection_cause_telcel_1;
               clCode = 1;
		  } else if (telephonyDisconnectCause == android.telephony.DisconnectCause.NO_ROUTE_TO_DESTINATION) {					//3;
			   resourceId = R.string.disconnection_cause_telcel_3;
               clCode = 3;
		  } else if (telephonyDisconnectCause == android.telephony.DisconnectCause.CHANNEL_UNACCEPTABLE) {					//6;
			   resourceId = R.string.disconnection_cause_telcel_6;
               clCode = 6;
		  } else if (telephonyDisconnectCause == android.telephony.DisconnectCause.OPERATOR_DETERMINED_BARRING) {					//8;
			   resourceId = R.string.disconnection_cause_telcel_8;
               clCode = 8;
		  } else if (telephonyDisconnectCause == android.telephony.DisconnectCause.NORMAL_CLEARING) {					//16;
			   resourceId = R.string.disconnection_cause_telcel_16;
               clCode = 16;
		  } else if (telephonyDisconnectCause == android.telephony.DisconnectCause.BUSY) { //17
               resourceId = R.string.disconnection_cause_telcel_17;
			   clCode = 17;
		  } else if (telephonyDisconnectCause == android.telephony.DisconnectCause.NO_USER_RESPONDING) { //18
               resourceId = R.string.disconnection_cause_telcel_18;
			   clCode = 18;
		  } else if (telephonyDisconnectCause == android.telephony.DisconnectCause.USER_ALERTING_NO_ANSWER) { //19
               resourceId = R.string.disconnection_cause_telcel_19;
			   clCode = 19;
		  } else if (telephonyDisconnectCause == android.telephony.DisconnectCause.CALL_REJECTED) { //21
               resourceId = R.string.disconnection_cause_telcel_21;
			   clCode = 21;
		  } else if (telephonyDisconnectCause == android.telephony.DisconnectCause.NUMBER_CHANGED) { //22
               resourceId = R.string.disconnection_cause_telcel_22;
			   clCode = 22;
		  } else if (telephonyDisconnectCause == android.telephony.DisconnectCause.PRE_EMPTION) { //25
               resourceId = R.string.disconnection_cause_telcel_25;
			   clCode = 25;
		  } else if (telephonyDisconnectCause == android.telephony.DisconnectCause.NON_SELECTED_USER_CLEARING) { //26
               resourceId = R.string.disconnection_cause_telcel_26;
			   clCode = 26;
		  } else if (telephonyDisconnectCause == android.telephony.DisconnectCause.DESTINATION_OUT_OF_ORDER) { //27
               resourceId = R.string.disconnection_cause_telcel_27;
			   clCode = 27;
		  } else if (telephonyDisconnectCause == android.telephony.DisconnectCause.INVALID_NUMBER_FORMAT) { //28
               resourceId = R.string.disconnection_cause_telcel_28;
			   clCode = 28;
		  } else if (telephonyDisconnectCause == android.telephony.DisconnectCause.FACILITY_REJECTED) { //29
               resourceId = R.string.disconnection_cause_telcel_29;
			   clCode = 29;
		  }
           else if (telephonyDisconnectCause == android.telephony.DisconnectCause.STATUS_ENQUIRY) { //30
			   resourceId = R.string.disconnection_cause_telcel_30;
               clCode = 30;
			   }
           else if (telephonyDisconnectCause == android.telephony.DisconnectCause.NETWORK_OUT_OF_ORDER) { //38
			   resourceId = R.string.disconnection_cause_telcel_38;
               clCode = 38;
			   }
           else if (telephonyDisconnectCause == android.telephony.DisconnectCause.SWITCHING_CONGESTION) { //42
			   resourceId = R.string.disconnection_cause_telcel_42;
               clCode = 42;
			   }
           else if (telephonyDisconnectCause == android.telephony.DisconnectCause.ACCESS_INFORMATION_DISCARDED) { //43
			   resourceId = R.string.disconnection_cause_telcel_43;
               clCode = 43;
			   }
           else if (telephonyDisconnectCause == android.telephony.DisconnectCause.RESOURCE_UNAVAILABLE) { //47
			   resourceId = R.string.disconnection_cause_telcel_47;
               clCode = 47;
			   }
           else if (telephonyDisconnectCause == android.telephony.DisconnectCause.REQUESTED_FACILITY_NOT_SUBSCRIBED) { //50
			   resourceId = R.string.disconnection_cause_telcel_50;
               clCode = 50;
			   }
           else if (telephonyDisconnectCause == android.telephony.DisconnectCause.INCOMING_CALL_BARRED_WITHIN_CUG) { //55
			   resourceId = R.string.disconnection_cause_telcel_55;
               clCode = 55;
			   }
		   else if (telephonyDisconnectCause == android.telephony.DisconnectCause.BEARER_NOT_AUTHORIZED) { //57
			   resourceId = R.string.disconnection_cause_telcel_57;
               clCode = 57;
			   }
		   else if (telephonyDisconnectCause == android.telephony.DisconnectCause.BEARER_NOT_AVAIL) { //58
			   resourceId = R.string.disconnection_cause_telcel_58;
               clCode = 58;
			   }
		   else if (telephonyDisconnectCause == android.telephony.DisconnectCause.SERVICE_NOT_AVAILABLE) { //63
			   resourceId = R.string.disconnection_cause_telcel_63;
               clCode = 63;
			   }
		   else if (telephonyDisconnectCause == android.telephony.DisconnectCause.BEARER_NOT_IMPLEMENT) { //65
			   resourceId = R.string.disconnection_cause_telcel_65;
               clCode = 65;
			   }
		   else if (telephonyDisconnectCause == android.telephony.DisconnectCause.FACILITY_NOT_IMPLEMENT) { //69
			   resourceId = R.string.disconnection_cause_telcel_69;
               clCode = 69;
			   }
		   else if (telephonyDisconnectCause == android.telephony.DisconnectCause.RESTRICTED_BEARER_AVAILABLE) { //70
			   resourceId = R.string.disconnection_cause_telcel_70;
               clCode = 70;
			   }
		   else if (telephonyDisconnectCause == android.telephony.DisconnectCause.OPTION_NOT_AVAILABLE) { //79
			   resourceId = R.string.disconnection_cause_telcel_79;
               clCode = 79;		   
			   }
		   else if (telephonyDisconnectCause == android.telephony.DisconnectCause.INVALID_TRANSACTION_ID_VALUE) { //81
			   resourceId = R.string.disconnection_cause_telcel_81;
               clCode = 81;
			   }
		   else if (telephonyDisconnectCause == android.telephony.DisconnectCause.USER_NOT_MEMBER_OF_CUG) { //87
			   resourceId = R.string.disconnection_cause_telcel_87;
               clCode = 87;
			   }
		   else if (telephonyDisconnectCause == android.telephony.DisconnectCause.INCOMPATIBLE_DESTINATION) { //88
			   resourceId = R.string.disconnection_cause_telcel_88;
               clCode = 88;
			   }
		   else if (telephonyDisconnectCause == android.telephony.DisconnectCause.INVALID_TRANSIT_NETWORK_SELECTION) { //91;
			   resourceId = R.string.disconnection_cause_telcel_91;
               clCode = 91;
			   }
    else if (telephonyDisconnectCause == android.telephony.DisconnectCause.SEMANTICALLY_INCORRECT_MESSAGE) { //95;
			   resourceId = R.string.disconnection_cause_telcel_95;
               clCode = 95;
			   }
    else if (telephonyDisconnectCause == android.telephony.DisconnectCause.INVALID_MANDATORY_INFORMATION) { //96;
			   resourceId = R.string.disconnection_cause_telcel_96;
               clCode = 96;
			   }
    else if (telephonyDisconnectCause == android.telephony.DisconnectCause.MESSAGE_TYPE_NON_EXISTENT) { //97;
			   resourceId = R.string.disconnection_cause_telcel_97;
               clCode = 97;
			   }
	else if (telephonyDisconnectCause == android.telephony.DisconnectCause.MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROT_STATE) { //98;
			   resourceId = R.string.disconnection_cause_telcel_98;
               clCode = 98;
			   }
    else if (telephonyDisconnectCause == android.telephony.DisconnectCause.IE_NON_EXISTENT_OR_NOT_IMPLEMENTED) { //99;
			   resourceId = R.string.disconnection_cause_telcel_99;
               clCode = 99;
			   }
    else if (telephonyDisconnectCause == android.telephony.DisconnectCause.CONDITIONAL_IE_ERROR) { //100;
			   resourceId = R.string.disconnection_cause_telcel_100;
               clCode = 100;
			   }
    else if (telephonyDisconnectCause == android.telephony.DisconnectCause.MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE) { //101;
			   resourceId = R.string.disconnection_cause_telcel_101;
               clCode = 101;
			   }
    else if (telephonyDisconnectCause == android.telephony.DisconnectCause.RECOVERY_ON_TIMER_EXPIRY) { //102;
			   resourceId = R.string.disconnection_cause_telcel_102;
               clCode = 102;
			   }
    else if (telephonyDisconnectCause == android.telephony.DisconnectCause.PROTOCOL_ERROR_UNSPECIFIED) { //111;
			   resourceId = R.string.disconnection_cause_telcel_111;
               clCode = 111;
			   }
    else if (telephonyDisconnectCause == android.telephony.DisconnectCause.INTERWORKING_UNSPECIFIED) { //127;
			   resourceId = R.string.disconnection_cause_telcel_127;
               clCode = 127;
			   }
		  
		  else if (telephonyDisconnectCause == android.telephony.DisconnectCause.NORMAL) { 				//31;
			   resourceId = R.string.disconnection_cause_telcel_31;
               clCode = 31;
		  } else if (telephonyDisconnectCause == android.telephony.DisconnectCause.NO_CIRCUIT_AVAIL) {				//34;
			   resourceId = R.string.disconnection_cause_telcel_34;
               clCode = 34;
		  } else if (telephonyDisconnectCause == android.telephony.DisconnectCause.CONGESTION) { //41
			   resourceId = R.string.disconnection_cause_telcel_41;
               clCode = 41;
		  } else if (telephonyDisconnectCause == android.telephony.DisconnectCause.CHANNEL_NOT_AVAIL) {				//44;
			   resourceId = R.string.disconnection_cause_telcel_44;
               clCode = 44;
		  } else if (telephonyDisconnectCause == android.telephony.DisconnectCause.QOS_NOT_AVAIL) {				//49;
			   resourceId = R.string.disconnection_cause_telcel_49;
               clCode = 49;
		  } else if (telephonyDisconnectCause == android.telephony.DisconnectCause.LIMIT_EXCEEDED) { //68
			   resourceId = R.string.disconnection_cause_telcel_68;
               clCode = 68;
		  }
		  Log.i("duanze","813 Telcel clear code,cl code:" + clCode);
	 }
     // / End
	 
        return resourceId == null ? "" : context.getResources().getString(resourceId);
    }

    private static String toTelecomDisconnectReason(int telephonyDisconnectCause, String reason) {
        String causeAsString = android.telephony.DisconnectCause.toString(telephonyDisconnectCause);
        if (reason == null) {
            return causeAsString;
        } else {
            return reason + ", " + causeAsString;
        }
    }

    /**
     * Returns the tone to play for the disconnect cause, or UNKNOWN if none should be played.
     */
    private static int toTelecomDisconnectCauseTone(int telephonyDisconnectCause) {
        switch (telephonyDisconnectCause) {
            case android.telephony.DisconnectCause.BUSY:
                return ToneGenerator.TONE_SUP_BUSY;

            case android.telephony.DisconnectCause.CONGESTION:
                return ToneGenerator.TONE_SUP_CONGESTION;

            case android.telephony.DisconnectCause.CDMA_REORDER:
                return ToneGenerator.TONE_CDMA_REORDER;

            case android.telephony.DisconnectCause.CDMA_INTERCEPT:
                return ToneGenerator.TONE_CDMA_ABBR_INTERCEPT;

            case android.telephony.DisconnectCause.CDMA_DROP:
            case android.telephony.DisconnectCause.OUT_OF_SERVICE:
                return ToneGenerator.TONE_CDMA_CALLDROP_LITE;

            case android.telephony.DisconnectCause.UNOBTAINABLE_NUMBER:
                return ToneGenerator.TONE_SUP_ERROR;

            case android.telephony.DisconnectCause.ERROR_UNSPECIFIED:
            case android.telephony.DisconnectCause.LOCAL:
            case android.telephony.DisconnectCause.NORMAL:
                return ToneGenerator.TONE_PROP_PROMPT;

            case android.telephony.DisconnectCause.IMS_MERGED_SUCCESSFULLY:
                // Do not play any tones if disconnected because of a successful merge.
            default:
                return ToneGenerator.TONE_UNKNOWN;
        }
    }
}
