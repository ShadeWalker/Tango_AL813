package com.mediatek.settings.cdma;


import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.TelephonyProperties;
import com.android.settings.R;
import com.mediatek.internal.telephony.ltedc.svlte.SvltePhoneProxy;

public class CdmaSimStatus {
    private static final String TAG = "CdmaSimStatus";
    
    private static final String KEY_MCC_MNC = "current_operators_mccmnc";
    private static final String KEY_SIN_NID= "current_sidnid";
    private static final String KEY_CELL_ID = "current_cellid";

    private static final int MCC_LENGTH = 3;
    
    private PreferenceActivity mActivity;
    private PreferenceScreen mPreferenceScreen;
    
    private CDMAPhone mCdmaPhone;
    private Phone mSvlteDcPhone;
    private ServiceState mServiceState;
    // Default summary for items refer to SimStatus
    private String mDefaultText;
    
    
    private TelephonyManager mTelephonyManager;
    private SubscriptionInfo mSubInfo;
    
    public CdmaSimStatus(PreferenceActivity activity, SubscriptionInfo subInfo) {
        mActivity = activity;
        mSubInfo = subInfo;
        mPreferenceScreen = activity.getPreferenceScreen();
        mTelephonyManager = (TelephonyManager)activity.getSystemService(Context.TELEPHONY_SERVICE);
        mDefaultText = activity.getString(R.string.device_info_default);
    }
    
    public void setPhoneInfos(Phone phone) {
        int phoneType = PhoneConstants.PHONE_TYPE_NONE;
        if (phone != null) {
            setServiceState(phone.getServiceState());
            phoneType = phone.getPhoneType();
        } else {
            Log.e(TAG, "No phone available");
        }
        Log.d(TAG,"setPhoneInfos phoneType = " + phoneType);
        if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
            mCdmaPhone = (CDMAPhone) (((PhoneProxy) phone).getActivePhone());
            if (mCdmaPhone != null && phone instanceof SvltePhoneProxy) {
                mSvlteDcPhone = ((SvltePhoneProxy) phone).getLtePhone();
                Log.d(TAG,"mSvlteDcPhone = " + mSvlteDcPhone);
            }
        }
    }
    public void setSubscriptionInfo(SubscriptionInfo subInfo) {
        mSubInfo = subInfo;
        Log.d(TAG,"setSubscriptionInfo = " + mSubInfo);
    }

    public void updateCdmaPreference(PreferenceActivity activity, SubscriptionInfo subInfo) {
        int slotId = subInfo.getSimSlotIndex();
        PreferenceScreen prefScreen = activity.getPreferenceScreen();
        Log.d(TAG,"slotId = " + slotId);
        if (CdmaUtils.getSIMCardType(slotId) != CdmaUtils.NOT_CT_SIM) {
            boolean isAdded = prefScreen.findPreference(KEY_MCC_MNC) != null;
            Log.d(TAG,"isAdded = " + isAdded);
            if (!isAdded) {
                activity.addPreferencesFromResource(R.xml.current_networkinfo_status);
            }
            setMccMnc();
            
            setCdmaSidNid();
            
            setCellId();
        } else {
            removeCdmaItems();
        }
    }
    public void setServiceState(ServiceState state) {
        Log.d(TAG,"setServiceState with state = " + state);
        mServiceState = state;
    }

    /**
     * For C2K OM, if current network is LTE, also need to show cdma cs network type name
     * @param key
     * @param networktype
     */
    public void updateNetworkType(String key, String networktype) {
        Log.d(TAG, "updateNetworkType with networktype = " + networktype);
        if (CdmaUtils.getSIMCardType(mSubInfo.getSimSlotIndex()) != CdmaUtils.NOT_CT_SIM
                && "LTE".equals(networktype) && mServiceState != null
                && mServiceState.getVoiceRegState() == ServiceState.STATE_IN_SERVICE) {
            String voiceNetworkName = TelephonyManager.getNetworkTypeName(mServiceState
                    .getVoiceNetworkType());
            voiceNetworkName = renameNetworkTypeName(voiceNetworkName);
            Log.d(TAG, "voiceNetworkName = " + voiceNetworkName);
            setSummaryText(key, voiceNetworkName + " , " + networktype);
        }
    }

    public void updateSignalStrength(SignalStrength signal, Preference preference) {
        if (CdmaUtils.getSIMCardType(mSubInfo.getSimSlotIndex()) != CdmaUtils.NOT_CT_SIM) {
            if (!signal.isGsm() && isRegisterUnderLteNetwork()) {
                /*Fix CRs ALPS02050467.
                 * When network is under LTE, and cdma network is 1x (CS), need to re-get
                 * signal strength of cdma (CS)
                */
                setCdmaSignalStrength(signal, preference);

                int lteSignalDbm = signal.getLteDbm();
                int lteSignalAsu = signal.getLteAsuLevel();
                if (-1 == lteSignalDbm) {
                    lteSignalDbm = 0;
                }

                if (-1 == lteSignalAsu) {
                    lteSignalAsu = 0;
                }

                Log.d(TAG,"lteSignalDbm = " + lteSignalDbm + " lteSignalAsu = " + lteSignalAsu);
                String lteSignal = mActivity.getString(
                        R.string.sim_signal_strength, lteSignalDbm, lteSignalAsu);
                String cdmaSignal = preference.getSummary().toString();
                Log.d(TAG,"cdmaSignal = " + cdmaSignal + " lteSignal = " + lteSignal);
                String summary = mActivity.getString(
                        R.string.status_cdma_signal_strength, cdmaSignal, lteSignal);
                Log.d(TAG,"summary = " + summary);
                preference.setSummary(summary);
            }
        }
    }
    
    private void setCdmaSignalStrength(SignalStrength signalStrength, Preference preference) {
        Log.d(TAG,"setCdmaSignalStrength() for 1x cdma network type");
        if ("CDMA 1x".equals(getNetworkType())) {
            int signalDbm = signalStrength.getCdmaDbm();
            int signalAsu = signalStrength.getCdmaAsuLevel();

            if (-1 == signalDbm) {
                signalDbm = 0;
            }

            if (-1 == signalAsu) {
                signalAsu = 0;
            }
            Log.d(TAG,"Cdma 1x signalDbm = " + signalDbm + " signalAsu = " + signalAsu);
            preference.setSummary(mActivity.getString(R.string.sim_signal_strength,
                    signalDbm, signalAsu));
        }
        
    }

    private String getNetworkType() {
        String networktype = null;
        final int actualDataNetworkType = mTelephonyManager.getDataNetworkType(
                mSubInfo.getSubscriptionId());
        final int actualVoiceNetworkType = mTelephonyManager.getVoiceNetworkType(
                mSubInfo.getSubscriptionId());
        Log.d(TAG,"actualDataNetworkType = " + actualDataNetworkType +
                  "actualVoiceNetworkType = " + actualVoiceNetworkType);
        if (TelephonyManager.NETWORK_TYPE_UNKNOWN != actualDataNetworkType) {
            networktype = mTelephonyManager.getNetworkTypeName(actualDataNetworkType);
        } else if (TelephonyManager.NETWORK_TYPE_UNKNOWN != actualVoiceNetworkType) {
            networktype = mTelephonyManager.getNetworkTypeName(actualVoiceNetworkType);
        }
        Log.d(TAG,"getNetworkType() networktype = " + networktype);
        return renameNetworkTypeName(networktype);
    }
    
    private ServiceState getServiceState() {
        ServiceState serviceState = null;
        if (mSvlteDcPhone != null) {
            serviceState = mSvlteDcPhone.getServiceState();
            Log.d(TAG,"mSvlteDcPhone serviceState = " + serviceState);
        } else if (mCdmaPhone != null) {
            serviceState = mCdmaPhone.getServiceState();
            Log.d(TAG,"mCdmaPhone serviceState = " + serviceState);
        }
        return serviceState;
    }
    
    private boolean isRegisterUnderLteNetwork() {
        ServiceState serviceState = getServiceState();
        return isRegisterUnderLteNetwork(serviceState);
    }
    private boolean isRegisterUnderLteNetwork(ServiceState serviceState) {
        Log.d(TAG,"isRegisterUnderLteNetwork with serviceState = " + serviceState);
        boolean isLteNetwork = false;
         if (serviceState != null &&
              serviceState.getDataNetworkType() == TelephonyManager.NETWORK_TYPE_LTE &&
              serviceState.getDataRegState() == ServiceState.STATE_IN_SERVICE) {
             isLteNetwork = true ;
         }
         Log.d(TAG,"isLteNetwork = " + isLteNetwork);
         return isLteNetwork;
    }

    private void removeCdmaItems() {
        removePreferenceFromScreen(KEY_MCC_MNC);
        removePreferenceFromScreen(KEY_SIN_NID);
        removePreferenceFromScreen(KEY_CELL_ID);
    }
    
    private void setMccMnc() {
        String numeric = null;
        if (isRegisterUnderLteNetwork()) {
            numeric = getMccMncProperty(mSvlteDcPhone);
        } else {
            numeric = getMccMncProperty(mCdmaPhone);
        }
        Log.d(TAG, "setMccMnc, numeric=" + numeric);
        if (numeric.length() > MCC_LENGTH) {
            String mcc = numeric.substring(0, MCC_LENGTH);
            String mnc = numeric.substring(MCC_LENGTH);
            String mccmnc = mcc + "," + mnc;
            Log.d(TAG,"mccmnc = " + mccmnc);
            setSummaryText(KEY_MCC_MNC, mccmnc);
        }
    }
    private String getMccMncProperty(Phone phone) {
        int phoneId = 0;
        if (phone != null) {
            phoneId = phone.getPhoneId();
        }
        String value = mTelephonyManager.getTelephonyProperty(phoneId,
                TelephonyProperties.PROPERTY_OPERATOR_NUMERIC,
                "");
        Log.d(TAG,"value = " + value);
        return value;
    }
    private void setCdmaSidNid() {
        if (mCdmaPhone != null) {
            String sid = mCdmaPhone.getSid();
            String nid = mCdmaPhone.getNid();
            String sidnid = sid + "," + nid;
            Log.d(TAG,"sidnid = " + sidnid);
            setSummaryText(KEY_SIN_NID, sidnid);
        }
    }
    private void setCellId() {
        if (mCdmaPhone != null) {
            CdmaCellLocation cellLocation = (CdmaCellLocation) mCdmaPhone.getCellLocation();
            String cellId = Integer.toString(cellLocation.getBaseStationId());
            Log.d(TAG,"cellId = " + cellId);
            setSummaryText(KEY_CELL_ID, cellId);
        }
    }

    private void setSummaryText(String key, String text) {
        if (TextUtils.isEmpty(text)) {
            text = mDefaultText;
        }
        // some preferences may be missing
        final Preference preference = mActivity.findPreference(key);
        if (preference != null) {
            preference.setSummary(text);
        }
    }
    /**
     * Copy from Op09 Plug in file @CurrentNetworkInfoStatus
     * CT spec requires that network type should apply to spec
     * "CDMA - EvDo rev. 0" -> "CDMA EVDO"
     * "CDMA - EvDo rev. A" -> "CDMA EVDO"
     * "CDMA - EvDo rev. B" -> "CDMA EVDO"
     * "CDMA - 1xRTT" -> "1x"
     * "GPRS" -> "GSM"
     * "HSDPA" -> "WCDMA"
     * "HSUPA" -> "WCDMA"
     * "HSPA" -> "WCDMA"
     * "HSPA+" -> "WCDMA"
     * "UMTS" -> "WCDMA"
     */
    static String renameNetworkTypeName(String netWorkTypeName) {
        Log.d(TAG, "renameNetworkTypeNameForCTSpec, netWorkTypeName=" + netWorkTypeName);
        if ("CDMA - EvDo rev. 0".equals(netWorkTypeName)
                || "CDMA - EvDo rev. A".equals(netWorkTypeName)
                || "CDMA - EvDo rev. B".equals(netWorkTypeName)) {
            return "CDMA EVDO";
        } else if ("CDMA - 1xRTT".equals(netWorkTypeName)) {
            return "CDMA 1x";
        } else if ("GPRS".equals(netWorkTypeName)
                || "EDGE".equals(netWorkTypeName)) {
            return "GSM";
        } else if ("HSDPA".equals(netWorkTypeName)
                || "HSUPA".equals(netWorkTypeName)
                || "HSPA".equals(netWorkTypeName)
                || "HSPA+".equals(netWorkTypeName)
                || "UMTS".equals(netWorkTypeName)) {
            return "WCDMA";
        } else if ("CDMA - eHRPD".equals(netWorkTypeName)) {
            return "eHRPD";
        } else {
            return netWorkTypeName;
        }
    }
    private void removePreferenceFromScreen(String key) {
        Preference pref = mActivity.findPreference(key);
        if (pref != null) {
            mPreferenceScreen.removePreference(pref);
        }
    }
}
