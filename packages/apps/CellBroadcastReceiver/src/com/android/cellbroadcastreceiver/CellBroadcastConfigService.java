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

package com.android.cellbroadcastreceiver;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.telephony.CellBroadcastMessage;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.gsm.SmsCbConstants;

import static com.android.cellbroadcastreceiver.CellBroadcastReceiver.DBG;
// add for gemini
//import android.provider.Telephony.SIMInfo;
//import com.mediatek.telephony.SmsManagerEx; //L migration modify
import com.mediatek.xlog.Xlog;

/**
 * This service manages enabling and disabling ranges of message identifiers
 * that the radio should listen for. It operates independently of the other
 * services and runs at boot time and after exiting airplane mode.
 *
 * Note that the entire range of emergency channels is enabled. Test messages
 * and lower priority broadcasts are filtered out in CellBroadcastAlertService
 * if the user has not enabled them in settings.
 *
 * TODO: add notification to re-enable channels after a radio reset.
 */
public class CellBroadcastConfigService extends IntentService {
    private static final String TAG = "[ETWS]CellBroadcastConfigService";

    static final String ACTION_ENABLE_CHANNELS = "ACTION_ENABLE_CHANNELS";

    static final String EMERGENCY_BROADCAST_RANGE_GSM =
            "ro.cb.gsm.emergencyids";

    //0x1100 ETWS Message Identifier for earthquake warning message. ETWS channel: 0x1100~0x1106 4352~4359
    private final static int MESSAGE_ID_ETWS_FIRST_IDENTIFIER = SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING;
    private final static int MESSAGE_ID_ETTW_LAST_IDENTIFIER = 0x1106;

    private final static int SLOT_0 = 0;

    public CellBroadcastConfigService() {
        super(TAG);          // use class name for worker thread name
    }

    private static void setChannelRange(SmsManager manager, String ranges, boolean enable) {
        if (DBG)log("setChannelRange: " + ranges);

        try {
            for (String channelRange : ranges.split(",")) {
                int dashIndex = channelRange.indexOf('-');
                if (dashIndex != -1) {
                    int startId = Integer.decode(channelRange.substring(0, dashIndex).trim());
                    int endId = Integer.decode(channelRange.substring(dashIndex + 1).trim());
                    if (enable) {
                        if (DBG) log("enabling emergency IDs " + startId + '-' + endId);
                        manager.enableCellBroadcastRange(startId, endId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    } else {
                        if (DBG) log("disabling emergency IDs " + startId + '-' + endId);
                        manager.disableCellBroadcastRange(startId, endId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    }
                } else {
                    int messageId = Integer.decode(channelRange.trim());
                    if (enable) {
                        if (DBG) log("enabling emergency message ID " + messageId);
                        manager.enableCellBroadcast(messageId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    } else {
                        if (DBG) log("disabling emergency message ID " + messageId);
                        manager.disableCellBroadcast(messageId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    }
                }
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Number Format Exception parsing emergency channel range", e);
        }

        // Make sure CMAS Presidential is enabled (See 3GPP TS 22.268 Section 6.2).
        if (DBG) log("setChannelRange: enabling CMAS Presidential");
        manager.enableCellBroadcast(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        // register Taiwan PWS 4383 also, by default
        manager.enableCellBroadcast(
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        manager.enableCellBroadcast(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT,
                SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
    }

    /**
     * Returns true if this is a standard or operator-defined emergency alert message.
     * This includes all ETWS and CMAS alerts, except for AMBER alerts.
     * @param message the message to test
     * @return true if the message is an emergency alert; false otherwise
     */
    static boolean isEmergencyAlertMessage(CellBroadcastMessage message) {
        if (message.isEmergencyAlertMessage()) {
            return true;
        }

        // Check for system property defining the emergency channel ranges to enable
        String emergencyIdRange = (CellBroadcastReceiver.phoneIsCdma()) ?
                "" : SystemProperties.get(EMERGENCY_BROADCAST_RANGE_GSM);

        if (TextUtils.isEmpty(emergencyIdRange)) {
            return false;
        }
        try {
            int messageId = message.getServiceCategory();
            for (String channelRange : emergencyIdRange.split(",")) {
                int dashIndex = channelRange.indexOf('-');
                if (dashIndex != -1) {
                    int startId = Integer.decode(channelRange.substring(0, dashIndex).trim());
                    int endId = Integer.decode(channelRange.substring(dashIndex + 1).trim());
                    if (messageId >= startId && messageId <= endId) {
                        return true;
                    }
                } else {
                    int emergencyMessageId = Integer.decode(channelRange.trim());
                    if (emergencyMessageId == messageId) {
                        return true;
                    }
                }
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Number Format Exception parsing emergency channel range", e);
        }
        return false;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (ACTION_ENABLE_CHANNELS.equals(intent.getAction()) || CellBroadcastReceiver.SMS_STATE_CHANGED_ACTION.equals(intent.getAction())) {
            if (SystemProperties.get("ro.mtk_gemini_support").equals("1")) {
                   onHandleIntentGemini(intent);  //L migration modify
                return;
            }
            try {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                Resources res = getResources();

                boolean enableEmergencyAlerts = prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_ETWS_ALERT, true);
                //boolean enableEtwsTestAlerts = prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_ETWS_TEST_ALERTS, true);

                TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

                boolean enableChannel50Support = res.getBoolean(R.bool.show_brazil_settings) ||
                        "br".equals(tm.getSimCountryIso());

                boolean enableChannel50Alerts = enableChannel50Support &&
                        prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_CHANNEL_50_ALERTS, true);

                // Note:  ETWS is for 3GPP only
                boolean enableEtwsTestAlerts = prefs.getBoolean(
                        CellBroadcastSettings.KEY_ENABLE_ETWS_TEST_ALERTS, false);

                boolean enableCmasExtremeAlerts = prefs.getBoolean(
                        CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, true);

                boolean enableCmasSevereAlerts = prefs.getBoolean(
                        CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, true);

                boolean enableCmasAmberAlerts = prefs.getBoolean(
                        CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, true);

                boolean enableCmasTestAlerts = prefs.getBoolean(
                        CellBroadcastSettings.KEY_ENABLE_CMAS_TEST_ALERTS, false);

                // set up broadcast ID ranges to be used for each category
                int cmasExtremeStart =
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED;
                int cmasExtremeEnd = SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY;
                int cmasSevereStart =
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED;
                int cmasSevereEnd = SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY;
                int cmasAmber = SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY;
                int cmasTestStart = SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST;
                int cmasTestEnd = SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE;
                int cmasPresident = SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL;
                int cmasTaiwanPWS = SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE;

               
                boolean isCdma = CellBroadcastReceiver.phoneIsCdma();

                // Check for system property defining the emergency channel ranges to enable
                String emergencyIdRange = isCdma ?
                        "" : SystemProperties.get(EMERGENCY_BROADCAST_RANGE_GSM);
                //SmsManager manager = SmsManager.getDefault();
                
                int subId = intent.getIntExtra("subscription",-1);
                SmsManager manager = SmsManager.getSmsManagerForSubscriptionId(subId);
                
                
                boolean isSetSuccess = false;
                if (enableEmergencyAlerts) {
                    if (DBG) log("enabling emergency cell broadcast channels");
                    //L migration modify start
                    isSetSuccess = manager.activateCellBroadcastSms(true);  
                    //isSetSuccess = SmsManagerEx.getDefault().activateCellBroadcastSms(true, SLOT_0);
                    if (isSetSuccess) {
                    
	                   manager.setEtwsConfig(0x1 | 0x4);//, SLOT_0);// enable etws
                    	 //L migration modify end
                        // No emergency channel system property, enable all emergency channels
                        // that have checkbox checked
                       manager.enableCellBroadcastRange(
                               SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                               SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING,
                               SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);

                       if (enableEtwsTestAlerts) {
                            manager.enableCellBroadcast(
                                    SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
                                    SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                       }
                        manager.enableCellBroadcastRange(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE, 
                                MESSAGE_ID_ETTW_LAST_IDENTIFIER, 
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);

                        //LTE support - Always enable CMAS SMS-B and CB channels irrespective of NW
                        if (enableCmasExtremeAlerts) {
                            manager.enableCellBroadcastRange(cmasExtremeStart, cmasExtremeEnd,
                                    SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                            manager.enableCellBroadcast(
                                    SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT,
                                    SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
                        }
                        if (enableCmasSevereAlerts) {
                            manager.enableCellBroadcastRange(cmasSevereStart, cmasSevereEnd,
                                    SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                            manager.enableCellBroadcast(
                                    SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT,
                                    SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
                        }
                        if (enableCmasAmberAlerts) {
                            manager.enableCellBroadcast(cmasAmber,
                                    SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                            manager.enableCellBroadcast(
                                    SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY,
                                    SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
                        }
                        if (enableCmasTestAlerts) {
                            manager.enableCellBroadcastRange(cmasTestStart, cmasTestEnd,
                                    SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                            manager.enableCellBroadcast(
                                    SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE,
                                    SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
                        }
                        // CMAS Presidential must be on (See 3GPP TS 22.268 Section 6.2).
                        manager.enableCellBroadcast(cmasPresident,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        manager.enableCellBroadcast(
                                SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
                        // register Taiwan PWS 4383 also, by default
                        manager.enableCellBroadcast(cmasTaiwanPWS,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);                    
                    if (DBG) log("enabled emergency cell broadcast channels");                                           
                    } else { //L migration modify 
                        if (DBG) log("enabled emergency cell broadcast channels failed!"); //L migration modify
                        return; //L migration modify 
                      } //L migration modify 
                } else {
                    // we may have enabled these channels previously, so try to disable them
                    if (DBG) log("disabling emergency cell broadcast channels");
                    if (!TextUtils.isEmpty(emergencyIdRange)) {
                        setChannelRange(manager, emergencyIdRange, false);
                    } else {
                        // No emergency channel system property, disable all emergency channels
                        // except for CMAS Presidential (See 3GPP TS 22.268 Section 6.2)

                        manager.disableCellBroadcastRange(
                                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        manager.disableCellBroadcast(
                                SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        manager.disableCellBroadcastRange(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE, 
                                MESSAGE_ID_ETTW_LAST_IDENTIFIER, 
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
						
                        //LTE support - Always disable CMAS SMS-B and CB channels irrespective of NW
                        manager.disableCellBroadcastRange(cmasExtremeStart, cmasExtremeEnd,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        manager.disableCellBroadcastRange(cmasSevereStart, cmasSevereEnd,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        manager.disableCellBroadcast(cmasAmber,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        manager.disableCellBroadcastRange(cmasTestStart, cmasTestEnd,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
						
                        manager.disableCellBroadcast(
                                SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
                        manager.disableCellBroadcast(
                                SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
                        manager.disableCellBroadcast(
                                SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
                        manager.disableCellBroadcast(
                                SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE ,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);

						//L migration modify start
                    	//SmsManagerEx.getDefault().setEtwsConfig(0, SLOT_0);// disable etws
                    	manager.setEtwsConfig(0);

						// No emergency channel system property, disable all emergency channels
                    	// except for CMAS Presidential (See 3GPP TS 22.268 Section 6.2)

                        // CMAS Presidential must be on (See 3GPP TS 22.268 Section 6.2).
                        manager.enableCellBroadcast(cmasPresident,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        manager.enableCellBroadcast(
                                SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
                        // register Taiwan PWS 4383 also, by default
                        manager.enableCellBroadcast(cmasTaiwanPWS,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    }
                    if (DBG) log("disabled emergency cell broadcast channels");
                    return;
                }

                if (enableEtwsTestAlerts) {
                    manager.enableCellBroadcast(SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
						SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                } else {
                    manager.disableCellBroadcast(SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
						SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                }
                if (!enableCmasExtremeAlerts) {
                    if (DBG) Log.d(TAG, "disabling cell broadcast CMAS extreme and severe");
                    manager.disableCellBroadcastRange(cmasExtremeStart, cmasExtremeEnd,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    manager.disableCellBroadcast(
                            SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
                }

                if (!enableCmasSevereAlerts) {
                    if (DBG) Log.d(TAG, "disabling cell broadcast CMAS severe");
                    manager.disableCellBroadcastRange(cmasSevereStart, cmasSevereEnd,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    manager.disableCellBroadcast(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
                }
                if (!enableCmasAmberAlerts) {
                    if (DBG) Log.d(TAG, "disabling cell broadcast CMAS amber");
                    manager.disableCellBroadcast(cmasAmber, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    manager.disableCellBroadcast(
                            SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
                }
                if (!enableCmasTestAlerts) {
                    if (DBG) Log.d(TAG, "disabling cell broadcast CMAS test messages");
                    manager.disableCellBroadcastRange(cmasTestStart, cmasTestEnd,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    manager.disableCellBroadcast(SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
                }
            } catch (Exception ex) {
                Log.e(TAG, "exception enabling cell broadcast channels", ex);
            }
        }
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
//L migration modify start

    //add functions for gemini
    private void setChannelRangeGemini(String ranges, boolean enable, int slotId, int subId) {
    	SmsManager manager = SmsManager.getSmsManagerForSubscriptionId(subId);
        try {
            for (String channelRange : ranges.split(",")) {
                int dashIndex = channelRange.indexOf('-');
                if (dashIndex != -1) {
                    int startId = Integer.decode(channelRange.substring(0, dashIndex));
                    int endId = Integer.decode(channelRange.substring(dashIndex + 1));
                    if (enable) {
                        if (DBG) Log.d(TAG, "enabling emergency IDs " + startId + '-' + endId);
                        manager.enableCellBroadcastRange(startId, endId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    } else {
                        if (DBG) Log.d(TAG, "disabling emergency IDs " + startId + '-' + endId);
                        manager.disableCellBroadcastRange(startId, endId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    }
                } else {
                    int messageId = Integer.decode(channelRange);
                    if (enable) {
                        if (DBG) Log.d(TAG, "enabling emergency message ID " + messageId);
                        manager.enableCellBroadcast(messageId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    } else {
                        if (DBG) Log.d(TAG, "disabling emergency message ID " + messageId);
                        manager.disableCellBroadcast(messageId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    }
                }
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Number Format Exception parsing emergency channel range", e);
        }
    }
    
    protected void onHandleIntentGemini(Intent intent) {
        Log.i(TAG, "onHandleIntentGemini ++");
        try {
            //int simId = intent.getIntExtra("sim_id", -1);
            int subId = intent.getIntExtra("subscription",-1);
            int slotId = intent.getIntExtra("slot",-1);
            SmsManager manager = SmsManager.getSmsManagerForSubscriptionId(subId);
            //SIMInfo si = SIMInfo.getSIMInfoById(this.getApplicationContext(), simId);
            //SubInfoRecord si = SubscriptionManager.getSubInfoUsingSubId(subId);
            //int slotId = 0;
            /*if (si != null) {
                slotId = si.mSlot;
            }else {
                Xlog.e(TAG, "simId:" + simId +" is not in slot!");
                return;
            } 
            Xlog.d(TAG, "simId:"+simId+",slotId:"+slotId); */

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

            boolean enableEmergencyAlerts = prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_ETWS_ALERT
                    + "_" + subId, CellBroadcastSettings.ENABLE_EMERGENCY_ALERTS_DEFAULT);
            boolean enableEtwsTestAlerts = prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_ETWS_TEST_ALERTS
                    + "_" + subId, true);

            boolean isSetSuccess = false;
            if (enableEmergencyAlerts) {
                Xlog.d(TAG, "enabling emergency cell broadcast channels");
                isSetSuccess = manager.activateCellBroadcastSms(true);
                if (isSetSuccess) {
                	manager.setEtwsConfig(0x1 | 0x4);// enable etws
                	manager.enableCellBroadcastRange(
                            SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                            SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                	manager.enableCellBroadcastRange(
                            SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                            MESSAGE_ID_ETTW_LAST_IDENTIFIER,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    Xlog.d(TAG, "enabled emergency cell broadcast channels");
                } else {
                    if (DBG) log("enabled emergency cell broadcast channels failed!");
                    return;
                }
            } else {
                // we may have enabled these channels previously, so try to disable them
                Xlog.d(TAG, "disabling emergency cell broadcast channels");
                manager.setEtwsConfig(0);// disable etws
                manager.disableCellBroadcastRange(
                        SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                        SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING,
                        SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);

                manager.disableCellBroadcastRange(
                        SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                        MESSAGE_ID_ETTW_LAST_IDENTIFIER,
                        SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                manager.disableCellBroadcast(
                        SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
                        SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                Xlog.d(TAG, "disabled emergency cell broadcast channels");
                return;
            }

            if (enableEtwsTestAlerts) {
            	manager.enableCellBroadcast(SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
					SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            } else {
            	manager.disableCellBroadcast(SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
					SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            }

        } catch (Exception ex) {
            Xlog.e(TAG, "exception enabling cell broadcast channels", ex);
        }
    } 
    //L migration modify END
}
