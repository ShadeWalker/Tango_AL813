/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
package com.mediatek.settings;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telecom.TelecomManager;
import android.os.SystemProperties;

import com.android.internal.telephony.Phone;
import com.android.phone.CallFeaturesSetting;
import com.android.phone.PhoneGlobals;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.PhoneUtils;

import com.android.phone.R;
import com.android.phone.SubscriptionInfoHelper;
import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;

public class CallFeaturesSettingExt implements PhoneGlobals.SubInfoUpdateListener {

    /** M: add button. */
    private static final String BUTTON_DUAL_MIC_KEY = "button_dual_mic_key";
    private static final String BUTTON_ANC_KEY = "button_anc_key";
    private static final String BUTTON_VOICEMAIL_CATEGORY_KEY = "button_voicemail_category_key";
    private static final String BUTTON_VOICEMAIL_SETTING_KEY = "button_voicemail_setting_key";
    private static final String BUTTON_DTMF_KEY = "button_dtmf_settings";
    private static final String BUTTON_RETRY_KEY = "button_auto_retry_key";
    private static final String BUTTON_TTY_KEY = "button_tty_mode_key";
    private static final String BUTTON_HAC_KEY = "button_hac_key";
    private static final String SIP_SETTINGS_CATEGORY_PREF_KEY =
        "phone_accounts_sip_settings_category_key";
    private static final String PHONE_ACCOUNT_SETTINGS_KEY =
            "phone_account_settings_preference_screen";
    private static final String BUTTON_FDN_KEY   = "button_fdn_key";
    private static final String BUTTON_CALL_REJECTION_KEY = "button_call_rejection_key";
    private static final String BUTTON_MAGI_CONFERENCE_KEY = "button_magi_conference_key";
    private static final String BUTTON_IP_PRIFIX_KEY = "button_ip_prefix_key";
    private static final String DEFAULT_OUTGOING_ACCOUNT_KEY = "default_outgoing_account";

    /// M: For C2K project to group GSM and C2K Call Settings @{
    private static final String BUTTON_GSM_OPTION = "mtk_gsm_options_key";
    private static final String BUTTON_CDMA_OPTION = "mtk_cdma_options_key";
    private static final String PHONE_PACKAGE_NAME = "com.android.phone";
    private static final String GSM_CALL_SETTING_CLASS_NAME
            = "com.android.phone.GsmUmtsCallOptions";
    private static final String C2K_CALL_SETTING_CLAS_NAME
            = "com.mediatek.settings.cdma.CDMACallSettings";
    /// @}

    ///M: WFC  @ {
    private static final String KEY_WFC_SETTINGS = "wfc_pref";
    /// @}

    private static final boolean DBG = true;
    private static final String LOG_TAG = "CallFeaturesSettingExt";

    private CheckBoxPreference mButtonDualMic;
    /// Add for [ANC] (Active Noise Reduction)
    private CheckBoxPreference mButtonANC;
    /// Add for [MagiConference]
    private CheckBoxPreference mButtonMagiConference;
    /// Add for [HAC]
    private CheckBoxPreference mButtonHAC;

    private PreferenceActivity mPreActivity;
    private PreferenceFragment mPreferenceFragment;
    private PreferenceScreen mPrefScreen;
    private SubscriptionInfoHelper mSubscriptionInfoHelper;
    private Phone mPhone;

    public CallFeaturesSettingExt(PreferenceActivity preActivity,
            PreferenceScreen prefScreen, SubscriptionInfoHelper subscriptionInfoHelper) {
        mPreActivity = preActivity;
        mPreferenceFragment = null;
        mPrefScreen = prefScreen;
        if (subscriptionInfoHelper == null) {
            mSubscriptionInfoHelper = null;
            mPhone = null;
        } else {
            mSubscriptionInfoHelper = subscriptionInfoHelper;
            mPhone = subscriptionInfoHelper.getPhone();
        }
    }

    public CallFeaturesSettingExt(PreferenceFragment preFragment,
            PreferenceScreen prefScreen) {
        this(null, prefScreen, null);
        mPreferenceFragment = preFragment;
    }

    public void init() {
        mButtonDualMic = (CheckBoxPreference) mPrefScreen.findPreference(BUTTON_DUAL_MIC_KEY);
        mButtonANC = (CheckBoxPreference) mPrefScreen.findPreference(BUTTON_ANC_KEY);
        mButtonMagiConference = (CheckBoxPreference) mPrefScreen.findPreference(
                BUTTON_MAGI_CONFERENCE_KEY);
        mButtonHAC = (CheckBoxPreference) mPrefScreen.findPreference(BUTTON_HAC_KEY);

        log("MicSupport: " + FeatureOption.isMtkDualMicSupport());
        log("ANCSupport: " + TelephonyUtils.isANCSupport());
        log("MagiConferenceSupport: " + TelephonyUtils.isMagiConferenceSupport());
        log("HacSupport(): " + TelephonyUtils.isHacSupport());

        if ((TelephonyUtils.isGeminiProject() && mPreActivity != null) ||
                (!TelephonyUtils.isGeminiProject() && mPreferenceFragment != null)) {
            if (mButtonDualMic != null) {
                mPrefScreen.removePreference(mButtonDualMic);
            }
            if (mButtonANC != null) {
                mPrefScreen.removePreference(mButtonANC);
            }
            if (mButtonMagiConference != null) {
                mPrefScreen.removePreference(mButtonMagiConference);
            }
            if (mButtonHAC != null) {
                mPrefScreen.removePreference(mButtonHAC);
            }
        }

        if (mButtonDualMic != null) {
            if (FeatureOption.isMtkDualMicSupport()) {
                mButtonDualMic.setChecked(TelephonyUtils.isDualMicModeEnabled());
                setListener(mButtonDualMic);
            } else {
                mPrefScreen.removePreference(mButtonDualMic);
                mButtonDualMic = null;
            }
        }

        if (mButtonANC != null) {
            if (TelephonyUtils.isANCSupport()) {
                mButtonANC.setChecked(TelephonyUtils.isANCEnabled());
                setListener(mButtonANC);
            } else {
                mPrefScreen.removePreference(mButtonANC);
                mButtonANC = null;
            }
        }

        if (mButtonMagiConference != null) {
            if (TelephonyUtils.isMagiConferenceSupport()) {
                //mButtonMagiConference.setChecked(TelephonyUtils.isMagiConferenceEnable());
                setListener(mButtonMagiConference);
            } else {
                mPrefScreen.removePreference(mButtonMagiConference);
                mButtonMagiConference = null;
            }
        }

        if (mButtonHAC != null) {
            if (TelephonyUtils.isHacSupport()) {
                ContentResolver contentResolver =
                    mPreActivity != null ? mPreActivity.getContentResolver() :
                            mPreferenceFragment.getActivity().getContentResolver();

                int hac = Settings.System.getInt(contentResolver, Settings.System.HEARING_AID, 0);
                /// Add for ALPS01973645 Mota upgrade. @{
                int hacModem = TelephonyUtils.isHacEnable();
                log("[initUi] hac : hacModem = " + hac + " : " + hacModem);
                if (hac != hacModem) {
                    Settings.System.putInt(contentResolver, Settings.System.HEARING_AID, hacModem);
                    hac = hacModem;
                }
                /// @}
                mButtonHAC.setChecked(hac != 0);
                setListener(mButtonHAC);
            } else {
                mPrefScreen.removePreference(mButtonHAC);
                mButtonHAC = null;
            }
        }

        if (mSubscriptionInfoHelper != null) {
            Preference ipPrefix = mPrefScreen.findPreference(BUTTON_IP_PRIFIX_KEY);
            if (ipPrefix != null) {
                if (SystemProperties.get("ro.hq.remove.ip.dialing").equals("1")) {//HQ_hushunli 2015-08-26 add for HQ01344290
                    mPrefScreen.removePreference(ipPrefix);
                    ipPrefix = null;
                } else {
                    ipPrefix.setIntent(mSubscriptionInfoHelper.getIntent(IpPrefixPreference.class));
                }
            }
        }
    }

    public boolean onPreferenceChange(Preference preference) {
        if (preference == mButtonDualMic) {
            if (DBG) {
                log("onPreferenceChange mButtonDualmic turn on : " + mButtonDualMic.isChecked());
            }
            TelephonyUtils.setDualMicMode(mButtonDualMic.isChecked() ? "0" : "1");
            return true;
        } else if (preference == mButtonANC) {
            boolean isChecked = mButtonANC.isChecked();
            if (DBG) {
                log("onPreferenceChange mButtonANC turn on : " + isChecked);
            }
            TelephonyUtils.setANCEnable(isChecked);
            mButtonANC.setSummary(isChecked ? R.string.anc_off : R.string.anc_on);
            return true;
        } else if (preference == mButtonMagiConference) {
            boolean isChecked = mButtonMagiConference.isChecked();
            if (DBG) {
                log("onPreferenceChange mButtonMagiConference turn on : " + isChecked);
            }
            TelephonyUtils.setMagiConferenceEnable(!isChecked);
            return true;
        } else if (preference == mButtonHAC) {
            int hac = mButtonHAC.isChecked() ? 0 : 1;
            if (DBG) {
                log("onPreferenceChange mButtonHAC turn on : " + hac);
            }
            Activity activity = (mPreActivity != null
                    ? mPreActivity : mPreferenceFragment.getActivity());
            // Update HAC value in Settings database
            Settings.System.putInt(activity.getContentResolver(),
                    Settings.System.HEARING_AID, hac);

            AudioManager audioManager = (AudioManager)
                    activity.getSystemService(Context.AUDIO_SERVICE);
            // Update HAC Value in AudioManager
            audioManager.setParameter(
                    CallFeaturesSetting.HAC_KEY,
                    hac != 0 ? CallFeaturesSetting.HAC_VAL_ON : CallFeaturesSetting.HAC_VAL_OFF);
            return true;
        }
        return false;
    }

    /**
     * update the screen status.
     *
     * @return true if have radio on
     */
    public boolean updateScreenStatus() {
        PreferenceScreen screen = null;
        boolean hasSub = false;
        boolean hasActiveSub = false;
        boolean isInCall = false;
        if (mPreActivity != null) {
            hasSub = SubscriptionManager.from(
                    mPreActivity).getActiveSubscriptionInfoCount() > 0;
            screen = mPreActivity.getPreferenceScreen();
            hasActiveSub = hasSub && TelephonyUtils.isRadioOn(mPhone.getSubId());
            TelecomManager manager = (TelecomManager) mPreActivity.getSystemService(
                           mPreActivity.TELECOM_SERVICE);
            isInCall = manager.isInCall();
        } else if (mPreferenceFragment != null) {
            hasSub = SubscriptionManager.from(
                    mPreferenceFragment.getActivity()).getActiveSubscriptionInfoCount() > 0;
            screen = mPreferenceFragment.getPreferenceScreen();
            /// TODO: MR1 here should check the modem is open or not.
            hasActiveSub = hasSub;
            TelecomManager manager = (TelecomManager) mPreferenceFragment.getActivity().getSystemService(
                           mPreActivity.TELECOM_SERVICE);
            isInCall = manager.isInCall();
        } else {
            log("updateScreenStatus both activities are null!!");
        }

        if (DBG) {
            log("updateScreenStatus hasActiveSub:" + hasActiveSub);
            log("isInCall" + isInCall);
        }
        Preference phoneAccountSettings = mPrefScreen.findPreference(PHONE_ACCOUNT_SETTINGS_KEY);
        Preference sipSettings = mPrefScreen.findPreference(SIP_SETTINGS_CATEGORY_PREF_KEY);
        Preference hacSetting = mPrefScreen.findPreference(BUTTON_HAC_KEY);
        Preference ttySetting = mPrefScreen.findPreference(BUTTON_TTY_KEY);
        Preference dtmfSetting = mPrefScreen.findPreference(BUTTON_DTMF_KEY);
        Preference retrySetting = mPrefScreen.findPreference(BUTTON_RETRY_KEY);
        Preference callRejectSetting = mPrefScreen.findPreference(BUTTON_CALL_REJECTION_KEY);
        Preference magiConference = mPrefScreen.findPreference(BUTTON_MAGI_CONFERENCE_KEY);
        Preference outGoingAccount = mPrefScreen.findPreference(DEFAULT_OUTGOING_ACCOUNT_KEY);
        ///M: WFC @ {
        Preference wfcSettingsPreference = mPrefScreen.findPreference(KEY_WFC_SETTINGS);
        /// @}
        /// screen shouldn't be null
        int count = screen.getPreferenceCount();
        for (int i = 0 ; i < count ; ++i) {
            Preference pref = screen.getPreference(i);
            if (hasActiveSub) {
                pref.setEnabled(true);
                if((isInCall == true) && (pref == ttySetting)){
                    if (DBG) {
                        log("dsiable TTY pref as call is ongoing");
                    }
                    pref.setEnabled(false);
                }
                    
            } else {
                if (pref != sipSettings && pref != hacSetting
                        && pref != ttySetting && pref != retrySetting
                        && pref != dtmfSetting && pref != mButtonANC
                        && pref != mButtonDualMic && pref != callRejectSetting
                        && pref != magiConference && pref != phoneAccountSettings
                        && pref != outGoingAccount && pref != wfcSettingsPreference) {
                    /// [MTK_FDN] Add this for FDN only disable when there is no sub inserted. @{
                    if (pref == mPrefScreen.findPreference(BUTTON_FDN_KEY) && hasSub) {
						// /modified by guofeiyao for HQ01319566
                        //pref.setEnabled(true);
                        pref.setEnabled(false);
						// /end
                    } else {
                    /// @}
                        pref.setEnabled(false);
                    }
                }
            }
        }
        return hasActiveSub;
    }

    /**
     * register broadcast Receiver.
     */
    public void registerCallback() {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        if (mPreActivity != null) {
            mPreActivity.registerReceiver(mReceiver, intentFilter);
        } else if (mPreferenceFragment != null) {
            mPreferenceFragment.getActivity().registerReceiver(mReceiver, intentFilter);
        } else {
            log("registerCallback both activities are null!!");
        }
        PhoneGlobals.getInstance().addSubInfoUpdateListener(this);
    }

    /**
     * update the phone associate with call feature setting.
     * @param phone the current phone
     */
    public void updatePhone(Phone phone) {
        mPhone = phone;
    }

    /**
     * update PreferenceScreen associate with call feature setting.
     * @param prefScreen the current PreferenceScreen
     */
    public void updatePrefScreen(PreferenceScreen prefScreen) {
        mPrefScreen = prefScreen;
    }

    /**
     * unregister broadcast Receiver.
     */
    public void unRegisterCallback() {
        if (mPreActivity != null) {
            mPreActivity.unregisterReceiver(mReceiver);
        } else if (mPreferenceFragment != null) {
            mPreferenceFragment.getActivity().unregisterReceiver(mReceiver);
        } else {
            log("unRegisterCallback both activities are null!!");
        }
        PhoneGlobals.getInstance().removeSubInfoUpdateListener(this);
    }

    BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            log("onReceive action:" + action);
            if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                updateScreenStatus();

                if (mPreActivity != null) {
                    PreferenceScreen voicemailSettings = (PreferenceScreen) mPrefScreen.findPreference(
                            BUTTON_VOICEMAIL_SETTING_KEY);
                    log("mVoicemailProviders != null");
                    if (voicemailSettings != null && voicemailSettings.getDialog() != null) {
                        log("getDialog() != null");
                        voicemailSettings.getDialog().dismiss();
                    }
                    PreferenceScreen voicemailCategory = (PreferenceScreen) mPrefScreen.findPreference(
                            BUTTON_VOICEMAIL_CATEGORY_KEY);
                    if (voicemailCategory != null && voicemailCategory.getDialog() != null) {
                        voicemailCategory.getDialog().dismiss();
                    }
                }
            }
        }
    };

    @Override
    public void handleSubInfoUpdate() {

        if (mPreActivity != null) {
            PreferenceScreen voicemailSettings = (PreferenceScreen) mPrefScreen.findPreference(
                    BUTTON_VOICEMAIL_SETTING_KEY);
            log("mVoicemailProviders != null");
            if (voicemailSettings != null && voicemailSettings.getDialog() != null) {
                log("getDialog() != null");
                voicemailSettings.getDialog().dismiss();
            }
            PreferenceScreen voicemailCategory = (PreferenceScreen) mPrefScreen.findPreference(
                    BUTTON_VOICEMAIL_CATEGORY_KEY);
            if (voicemailCategory != null && voicemailCategory.getDialog() != null) {
                voicemailCategory.getDialog().dismiss();
            }
            final TelephonyManager telephonyManager =
                    (TelephonyManager) mPreActivity.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager.getPhoneCount() <= 1) {
                updateScreenStatus();
            } else {
                mPreActivity.finish();
            }
        } else if (mPreferenceFragment != null) {
            mPreferenceFragment.getActivity().finish();
        }
    }

    /**
     * Add this for ANC DualMic MagiConference feature, Set different listener
     * for different project.
     * @param preference
     */
    private void setListener(CheckBoxPreference preference) {
        if (TelephonyUtils.isGeminiProject()) {
            preference.setOnPreferenceChangeListener(
                    (OnPreferenceChangeListener) mPreferenceFragment);
        } else {
            preference.setOnPreferenceChangeListener(
                    (OnPreferenceChangeListener) mPreActivity);
        }
    }

    private void log(String log) {
        android.util.Log.d(LOG_TAG, log);
    }
    
        /**
     * M: For C2K project to group GSM and C2K Call Settings. @{
     * @param prefSet The PreferenceScreen to add Call Options.
     */
    public void showCallOption(PreferenceScreen prefSet) {
        log("showCallOption");
        if (PhoneUtils.hasPhoneType(PhoneConstants.PHONE_TYPE_CDMA)) {
			log("showCallOption  PhoneConstants.PHONE_TYPE_CDMA");
            /// show CDMA call option
            PreferenceScreen newCdmaOptions = new PreferenceScreen(mPreActivity, null);
            newCdmaOptions.setKey("mtk_cdma_options_key");
            newCdmaOptions.setTitle(R.string.labelCDMAMore);
            Intent cdmaOptionsIntent = new Intent();
            cdmaOptionsIntent.setClassName(PHONE_PACKAGE_NAME, C2K_CALL_SETTING_CLAS_NAME);
            newCdmaOptions.setIntent(cdmaOptionsIntent);
            prefSet.addPreference(newCdmaOptions);
            //newCdmaOptions.setEnabled(TelephonyUtils.getCdmaSubList().size() > 0);
            newCdmaOptions.setEnabled(true);
        }
        if (PhoneUtils.hasPhoneType(PhoneConstants.PHONE_TYPE_GSM)) {
            if (mPreActivity.getResources().getBoolean(
                    R.bool.config_additional_call_setting)) {
                PreferenceScreen newGsmOptions = new PreferenceScreen(mPreActivity, null);
                newGsmOptions.setKey("mtk_gsm_options_key");
                newGsmOptions.setTitle(R.string.labelGSMMore);
                Intent gsmOptionsIntent = new Intent();
                gsmOptionsIntent.setClassName(PHONE_PACKAGE_NAME, GSM_CALL_SETTING_CLASS_NAME);
                newGsmOptions.setIntent(gsmOptionsIntent);
                prefSet.addPreference(newGsmOptions);
                //newGsmOptions.setEnabled(TelephonyUtils.getGsmSubList().size() > 0);
                newGsmOptions.setEnabled(true);
            }
        }
    }
    /** @} */                       
}
