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

package com.android.keyguard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.media.AudioManager ;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;

import com.android.internal.telephony.IccCardConstants;

/**
 * Utilities for Keyguard.
 */
public class KeyguardUtils {
    private static final String TAG = "KeyguardUtils";
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private KeyguardUpdateMonitor mUpdateMonitor;
    private SubscriptionManager mSubscriptionManager;

    /**
     * Constructor.
     * @param context the context
     */
    public KeyguardUtils(Context context) {
        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(context);
        mSubscriptionManager = SubscriptionManager.from(context);
    }

    /**
     * Return Operator name of related subId.
     * @param phoneId id of phone
     * @param context the context
     * @return operator name.
     */
    public String getOptrNameUsingPhoneId(int phoneId, Context context) {
        int subId = getSubIdUsingPhoneId(phoneId) ;
        SubscriptionInfo info = mSubscriptionManager.getActiveSubscriptionInfo(subId);
        if (null == info) {
           if (DEBUG) {
            Log.d(TAG, "getOptrNameUsingPhoneId, return null");
           }
        } else {
           if (DEBUG) {
               Log.d(TAG, "getOptrNameUsingPhoneId mDisplayName=" + info.getDisplayName());
           }
           if (info.getDisplayName() != null) {
                return info.getDisplayName().toString();
           }
        }
        return null;
    }

    /**
     * Return Operator drawable of related subId.
     * @param phoneId id of phone
     * @param context the context
     * @return operator related drawable.
     */
    public Bitmap getOptrBitmapUsingPhoneId(int phoneId, Context context) {
        int subId = getSubIdUsingPhoneId(phoneId) ;
        Bitmap bgBitmap = null;
        SubscriptionInfo info = mSubscriptionManager.getActiveSubscriptionInfo(subId);
        if (null == info) {
            if (DEBUG) {
                Log.d(TAG, "getOptrBitmapUsingPhoneId, return null");
            }
        } else {
            bgBitmap = info.createIconBitmap(context) ;
        }
        return bgBitmap;
    }


    /********************************************************
     ** Mediatek add begin.
     ********************************************************/
    private static final boolean mIsOwnerSdcardOnlySupport =
        SystemProperties.get("ro.mtk_owner_sdcard_support").equals("1");
    private static final boolean mIsVoiceUnlockSupport =
        SystemProperties.get("ro.mtk_voice_unlock_support").equals("1");
    private static final boolean mIsPrivacyProtectionLockSupport =
        SystemProperties.get("ro.mtk_privacy_protection_lock").equals("1");
    private static final boolean mIsMediatekSimMeLockSupport =
        SystemProperties.get("ro.sim_me_lock_mode", "0").equals("0");

    public static final boolean isOwnerSdcardOnlySupport() {
        return mIsOwnerSdcardOnlySupport;
    }

    public static final boolean isVoiceUnlockSupport() {
        return mIsVoiceUnlockSupport;
    }

    public static final boolean isPrivacyProtectionLockSupport() {
        return mIsPrivacyProtectionLockSupport ;
    }

    private static final String MTK_VOW_SUPPORT_State = "MTK_VOW_SUPPORT" ;
    private static final String MTK_VOW_SUPPORT_On = "MTK_VOW_SUPPORT=true" ;
    private static final String MTK_VOW_SUPPORT_Off = "MTK_VOW_SUPPORT=false" ;
    /**
     * Return VOW Support or not.
     * @param context the context
     * @return support or not
     */
    public static final boolean isVoiceWakeupSupport(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE) ;
        if (am == null) {
            Log.d(TAG, "isVoiceWakeupSupport() - get AUDIO_SERVICE fails, return false.") ;
            return false ;
        }

        String val = am.getParameters(MTK_VOW_SUPPORT_State) ;
        return val != null && val.equalsIgnoreCase(MTK_VOW_SUPPORT_On) ;
    }

    public static final boolean isMediatekSimMeLockSupport() {
        return mIsMediatekSimMeLockSupport;
    }

    public static final boolean isTablet() {
        return ("tablet".equals(SystemProperties.get("ro.build.characteristics")));
    }

    /**
     * M : fix ALPS01564588. Refresh IME Stauts to hide ALT-BACK key correctly.
     * @param context the context
     */
    public static void requestImeStatusRefresh(Context context) {
        InputMethodManager imm =
            ((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE));
        if (imm != null) {
            if (DEBUG) {
                Log.d(TAG, "call imm.requestImeStatusRefresh()");
            }
            imm.refreshImeWindowVisibility() ;
        }
    }

    /**
     * Return AirPlane mode is on or not.
     * @param context the context
     * @return airplane mode is on or not
     */
    public static boolean isAirplaneModeOn(Context context) {
        boolean airplaneModeOn = Settings.Global.getInt(context.getContentResolver(),
                                                        Settings.Global.AIRPLANE_MODE_ON,
                                                        0) != 0;
        Log.d(TAG, "isAirplaneModeOn() = " + airplaneModeOn) ;
        return airplaneModeOn ;
    }

    /**
     * if return true, it means that Modem will turn off after entering AirPlane mode.
     * @return support or not
     */
    public static boolean isFlightModePowerOffMd() {
        boolean powerOffMd = SystemProperties.get("ro.mtk_flight_mode_power_off_md").equals("1") ;
        Log.d(TAG, "powerOffMd = " + powerOffMd) ;
        return powerOffMd ;
    }

    private static final int MAX_PHONE_COUNT = 4;
    private static int sPhoneCount = 0 ;
    /**
     * Get phone count.
     * @return phone count.
     **/
    public static int getNumOfPhone() {
        if (sPhoneCount == 0) {
            sPhoneCount = TelephonyManager.getDefault().getPhoneCount(); //hw can support in theory
            // MAX_PHONE_COUNT : in fact our ui layout max support 4. maybe update in future
            sPhoneCount = ((sPhoneCount > MAX_PHONE_COUNT) ? MAX_PHONE_COUNT : sPhoneCount);
        }
        return sPhoneCount;
    }

    /**
     * Is phone id valid.
     * @param phoneId phoneId.
     * @return valid or not.
     **/
    public static boolean isValidPhoneId(int phoneId) {
        return (phoneId != SubscriptionManager.DEFAULT_PHONE_INDEX) &&
               (0 <= phoneId) && (phoneId < getNumOfPhone());
    }

    /** get PhoneId from SubManager.
     * @param subId subId
     * @return phoneId
     */
    public static int getPhoneIdUsingSubId(int subId) {
        Log.e(TAG, "getPhoneIdUsingSubId: subId = " + subId);
        int phoneId = SubscriptionManager.getPhoneId(subId);
        if (phoneId < 0 || phoneId >= getNumOfPhone()) {
            Log.e(TAG, "getPhoneIdUsingSubId: invalid phonId = " + phoneId);
        } else {
            Log.e(TAG, "getPhoneIdUsingSubId: get phone ID = " + phoneId);
        }

        return phoneId ;
    }

    /**
     * Send phoneId to Sub-Mgr and get subId.
     * @param phoneId phoneId.
     * @return subid.
     */
    public static int getSubIdUsingPhoneId(int phoneId) {
        int subId = SubscriptionManager.getSubIdUsingPhoneId(phoneId);
        Log.d(TAG, "getSubIdUsingPhoneId(phoneId = " + phoneId + ") = " + subId) ;
        return subId;
    }

    public static final int INVALID_PHONE_ID = -1 ;

    ///M : added for SMB
    /** check if SMB plugged in.
     * @param mContext context
     * @return plug or not
     */
    public static boolean isSmartBookPluggedIn(Context mContext) {
        if (isMtkSmartBookSupport()) {
            DisplayManager mDisplayManager =
                (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);
            return mDisplayManager.isSmartBookPluggedIn();
        } else {
            return false;
        }
    }
    private static boolean sMtkSmartbookSupport =
        SystemProperties.get("ro.mtk_smartbook_support").equals("1");
    public static final boolean isMtkSmartBookSupport() {
        return sMtkSmartbookSupport;
    }

    ///M: fix ALPS02023919.
    ///   SVLTE TE FWK has problem that its SubInfo is not valid when SIM state change.
    ///   So we need to have chance to call onSimStateChange() callback to refresh UI.
    private static boolean sSVLTE =
        SystemProperties.get("ro.mtk_svlte_support").equals("1");
    /** M: workaround for SVLTE te fwk issue.
     * @param state cart status
     * @return isSvlteAndSimCardLocked or not.
     */
    public static final boolean isSvlteAndSimCardLocked(IccCardConstants.State state) {
        boolean simLocked = (state == IccCardConstants.State.PIN_REQUIRED)
                            || (state == IccCardConstants.State.PUK_REQUIRED)
                            || (state == IccCardConstants.State.NETWORK_LOCKED &&
                                isMediatekSimMeLockSupport()) ;
        return sSVLTE && simLocked ;
    }
}
