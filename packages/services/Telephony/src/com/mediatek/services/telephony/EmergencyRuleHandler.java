package com.mediatek.services.telephony;

import android.os.SystemProperties;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;

import android.util.Log;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The emergency call handler.
 * Selected the proper Phone for setting up the ecc call.
 */
public class EmergencyRuleHandler {
    static final String TAG = "EmergencyRuleHandler";
    static final boolean DBG = true;
    public static final int ECC_INVALIDE_SLOT = -1;
    public static final int ECC_SLOT_1 = 0;
    public static final int ECC_SLOT_2 = 1;

    private Phone mGsmPhone = null;
    private Phone mCdmaPhone = null;

    private Phone mPrePhone = null;

    List<RuleHandler> mRuleList;
    private String mNumber;

    void log(String s) {
        Log.d(TAG, s);
    }

    /**
     * The common interface for ECC rule.
     */
    public interface RuleHandler {
        /**
         * Handle the ecc reqeust.
         * @param number The Ecc number will be dialed.
         * @return Phone The Phone object used for ecc.
         */
        public Phone handleRequest(String number);
    }

    /**
     * Init the EmergencyRuleHandler.
     * @param number The Ecc number.
     */
    public EmergencyRuleHandler(String number) {
        mNumber = number;
        initPhone();
    }

    private void initPhone() {
        mGsmPhone = getProperPhone(PhoneConstants.PHONE_TYPE_GSM);
        mCdmaPhone = getProperPhone(PhoneConstants.PHONE_TYPE_CDMA);

        if (mGsmPhone != null) {
            Log.d(TAG, "GSM Network State == " +
                serviceStateToString(mGsmPhone.getServiceState().getState()));
        } else {
            Log.d(TAG, "No GSM Phone exist.");
        }
        if (mCdmaPhone != null) {
            Log.d(TAG, "CDMA Network State == " +
                    serviceStateToString(mCdmaPhone.getServiceState().getState()));
        } else {
            Log.d(TAG, "No CDMA Phone exist.");
        }
    }

    /**
     * Check if gsm has registered to network.
     * @return indicates the register status.
     */
    public boolean isGsmNetworkReady() {
        if (mGsmPhone != null) {
            return ServiceState.STATE_IN_SERVICE
                    == mGsmPhone.getServiceState().getState();
        }

        return false;
    }

    /**
     * Check if cdma has registered to network.
     * @return indicates the register status.
     */
    public boolean isCdmaNetworkReady() {
        if (mCdmaPhone != null) {
            return ServiceState.STATE_IN_SERVICE
                    == mCdmaPhone.getServiceState().getState();
        }

        return false;
    }

    private void generateHandlerList() {
        if (mRuleList != null) {
            mRuleList.clear();
        }
        mRuleList = new ArrayList<RuleHandler>();

        mRuleList.add(new GCUnReadyRule());
        mRuleList.add(new CdmaReadyOnlyRule());
        mRuleList.add(new GsmReadyOnlyRule());
        mRuleList.add(new CdmaAndGsmReay());
    }

    private void handleRequest() {
        for (RuleHandler rule : mRuleList) {
            Phone phone = rule.handleRequest(mNumber);
            if (phone != null) {
                mPrePhone = phone;
                log("handleRequest find prefered phone = " + mPrePhone);
                break;
            }
        }
    }

    /**
     * Get the proper Phone for ecc dial.
     * @return A object for Phone that used for setup call.
     */
    public Phone getPreferedPhone() {
        generateHandlerList();
        handleRequest();
        return mPrePhone;
    }

    /**
     * DualTalk G+C no sim insert rule
     *
     */
    class GCUnReadyRule implements RuleHandler {

        public Phone handleRequest(String number) {
            if (isGsmNetworkReady() || isCdmaNetworkReady()) {
                log("GCUnReadyRule: there are/is newtork ready.");
                return null;
            }

            if ("112".equals(number)) {
                return getProperPhone(PhoneConstants.PHONE_TYPE_GSM);
            }

            return getProperPhone(PhoneConstants.PHONE_TYPE_CDMA);
        }
    }

    /**
     * There is only gsm inserted.
     */
    class GsmReadyOnlyRule implements RuleHandler {
        public Phone handleRequest(String number) {
            log("GsmReadyOnlyRule: handleRequest...");
            if (!isGsmNetworkReady() || isCdmaNetworkReady()) {
                return null;
            }

            log("All numbers dialed from GSM");
            return getProperPhone(PhoneConstants.PHONE_TYPE_GSM);
        }
    }

    /**
     * There is only cdma inserted.
     */
    class CdmaReadyOnlyRule implements RuleHandler {

        public Phone handleRequest(String number) {
            log("CdmaReadyOnlyRule: handleRequest...");
            if (!isCdmaNetworkReady() || isGsmNetworkReady()) {
                return null;
            }

            if ("112".equals(number)) {
                return getProperPhone(PhoneConstants.PHONE_TYPE_GSM);
            } else {
                return getProperPhone(PhoneConstants.PHONE_TYPE_CDMA);
            }
        }
    }

    /**
     * CDMA and GSM register to network.
     */
    class CdmaAndGsmReay implements RuleHandler {

        public Phone handleRequest(String number) {
            log("CdmaAndGsmReay: handleRequest...");
            if (!isCdmaNetworkReady() || !isGsmNetworkReady()) {
                return null;
            }

            if ("112".equals(number)) {
                return getProperPhone(PhoneConstants.PHONE_TYPE_GSM);
            }

            return null;
        }
    }

    /**
     * Handle ECC default case.
     */
    class DefaultHandler implements RuleHandler {
        public Phone handleRequest(String number) {
            log("Can't got here! something is wrong!");
            return getProperPhone(PhoneConstants.PHONE_TYPE_GSM);
        }
    }

    String serviceStateToString(int state) {
        String s = null;
        if (state < ServiceState.STATE_IN_SERVICE
                || state > ServiceState.STATE_POWER_OFF) {
            log("serviceStateToString: invalid state = " + state);
            s = "INVALIDE_STATE";
            return s;
        }

        switch (state) {
            case ServiceState.STATE_IN_SERVICE:
                s = "STATE_IN_SERVICE";
                break;
            case ServiceState.STATE_OUT_OF_SERVICE:
                s = "STATE_OUT_OF_SERVICE";
                break;
            case ServiceState.STATE_EMERGENCY_ONLY:
                s = "STATE_EMERGENCY_ONLY";
                break;
            case ServiceState.STATE_POWER_OFF:
                s = "STATE_POWER_OFF";
                break;

            default:
                    s = "UNKNOWN_STATE";
        }

        return s;
    }

    String simStateToString(int state) {
        String s = null;
        if (state < TelephonyManager.SIM_STATE_UNKNOWN
                || state > TelephonyManager.SIM_STATE_READY) {
            log("simStateToString: invalid state = " + state);
            s = "INVALIDE_STATE";
        }

        switch (state) {
            case TelephonyManager.SIM_STATE_UNKNOWN:
                s = "SIM_STATE_UNKNOWN";
                break;
            case TelephonyManager.SIM_STATE_ABSENT:
                s = "SIM_STATE_ABSENT";
                break;
            case TelephonyManager.SIM_STATE_PIN_REQUIRED:
                s = "SIM_STATE_PIN_REQUIRED";
                break;
            case TelephonyManager.SIM_STATE_PUK_REQUIRED:
                s = "SIM_STATE_PUK_REQUIRED";
                break;
            case TelephonyManager.SIM_STATE_NETWORK_LOCKED:
                s = "SIM_STATE_NETWORK_LOCKED";
                break;
            case TelephonyManager.SIM_STATE_READY:
                s = "SIM_STATE_READY";
                break;
            default:
                    s = "UNKNOWN_STATE";
        }

        return s;
    }

    Phone getProperPhone(int phoneType) {
        Phone[] phones = PhoneFactory.getPhones();
        Phone phone = null;
        log("phone list size = " + phones.length);
        for (Phone p : phones) {
            if (p.getPhoneType() == phoneType) {
                phone = p;
                break;
            }
        }
        log("getProperSlot with phoneType = " + phoneType + " and return phone = " + phone);
        return phone;
    }

    /**
     * Check if this is evdo dualtalk solution.
     * @return A boolean value indicate we can handle internal.
     */
    public static boolean canHandle() {
        /// M: SVLTE+G solution, voice dual talk only @{
        if (SystemProperties.get("ro.mtk_svlte_support").equals("1")) {
            boolean result = false;
            /** M: Bug Fix for ALPS01944336 @{ */
            // Check the CDMA phone status. In some roaming place, the CDMA
            // phone maybe is null
            if (TelephonyManager.getDefault().getPhoneCount() >= 2) {
                Phone[] phones = PhoneFactory.getPhones();
                for (Phone p : phones) {
                    if (null != p && p.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                        result = true;
                        break;
                    }
                }
            }
            /** @} */
            return result;
        } else {
        /// @}
            return (SystemProperties.get("ro.evdo_dt_support").equals("1"));
        }
    }
}
