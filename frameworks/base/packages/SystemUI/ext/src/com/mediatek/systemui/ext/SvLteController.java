package com.mediatek.systemui.ext;

import android.content.Context;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.PreciseDataConnectionState;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;
import com.mediatek.systemui.statusbar.util.SIMHelper;
import com.mediatek.telephony.TelephonyManagerEx;

import java.util.HashSet;

/**
 * For SVLTE controller.
 *
 */
public class SvLteController {
    private static final String TAG = "SvLteController";
    private static final boolean DEBUG = true;

    private static final int LTE_SLOT = 0;
    // SVLTE support system property
    private static final String MTK_SVLTE_SUPPORT = "ro.mtk_svlte_support";

    /// SVLTE support AP-IRAT
    public static final String ACTION_IRAT_PS_TYPE_CHANGED =
            "com.mediatek.action.irat.ps.type.changed";
    public static final String EXTRA_PS_TYPE = "extra_ps_type";
    public static final int PS_SERVICE_UNKNOWN = -1;
    public static final int PS_SERVICE_ON_CDMA = 0;
    public static final int PS_SERVICE_ON_LTE = 1;
    private static final String[]  PROPERTY_RIL_FULL_UICC_TYPE = {
        "gsm.ril.fulluicctype",
        "gsm.ril.fulluicctype.2",
        "gsm.ril.fulluicctype.3",
        "gsm.ril.fulluicctype.4",
    };

    private final SubscriptionInfo mSubscriptionInfo;
    private ServiceState mServiceState;
    private SignalStrength mSignalStrength;
    private int mDataNetType;
    private int mDataState;
    private int mDataActivity;
    private int mPsType = PS_SERVICE_UNKNOWN;
    private HashSet<String> mPreciseDataConnectedState =
            new HashSet<String>();

    private final Context mContext;

    /**
     * Constructs a new SvLteController instance.
     *
     * @param context A Context object
     * @param info A SubscriptionInfo object
     */
    public SvLteController(Context context, SubscriptionInfo info) {
        this.mContext = context;
        this.mSubscriptionInfo = info;
    }

    public static int getSvlteSlot() {
        return LTE_SLOT;
    }

    /**
     * On SignalStrengths Changed Callback.
     * @param signalStrength The SignalStrength.
     */
    public void onSignalStrengthsChanged(SignalStrength signalStrength) {
        mSignalStrength = signalStrength;
    }

    /**
     * On ServiceState Changed Callback.
     * @param state The ServiceState.
     */
    public void onServiceStateChanged(ServiceState state) {
        mServiceState = state;
    }

    /**
     * On DataConnection State Changed Callback.
     * @param state The DataState.
     * @param networkType The DataNetType.
     */
    public void onDataConnectionStateChanged(int state, int networkType) {
        mDataState = state;
        mDataNetType = networkType;
    }

    /**
     * On DataActivity Callback.
     * @param direction The data DataActivity.
     */
    public void onDataActivity(int direction) {
        mDataActivity = direction;
    }

    /**
     * On PreciseDataConnectionStateChanged Callback.
     * @param state The PreciseDataConnectionState.
     */
    public void onPreciseDataConnectionStateChanged(PreciseDataConnectionState state) {
        Log.d(TAG, "onPreciseDataConnectionStateChanged: state = " + state.toString());
        String apnType = state.getDataConnectionAPNType();
        int dataState = state.getDataConnectionState();
        if (dataState == TelephonyManager.DATA_CONNECTED) {
            if (!mPreciseDataConnectedState.contains(apnType)) {
                mPreciseDataConnectedState.add(apnType);
                Log.d(TAG, "onPreciseDataConnectionStateChanged: put apnType: " + apnType +
                        ", dataState: " + dataState + " into mPreciseDataConnectedState");
            }
        } else {
            if (mPreciseDataConnectedState.contains(apnType)) {
                mPreciseDataConnectedState.remove(apnType);
                Log.d(TAG, "onPreciseDataConnectionStateChanged: remove apnType: " + apnType +
                        ", dataState: " + dataState + " from mPreciseDataConnectedState");
            }
        }
    }

    /**
     * Check if show data activity icon.
     * @return if show data activity icon.
     */
    public boolean isShowDataActivityIcon() {
        if (DEBUG) {
            for (String apn : mPreciseDataConnectedState) {
                Log.d(TAG, "isShowDataActivityIcon(),current connected apn type: " + apn);
            }
        }
        if (mPreciseDataConnectedState.size() == 1) {
            if (mPreciseDataConnectedState.contains(PhoneConstants.APN_TYPE_IMS) ||
                    mPreciseDataConnectedState.contains(PhoneConstants.APN_TYPE_EMERGENCY)) {
                Log.d(TAG, "isShowDataActivityIcon(), return false");
                return false;
            }
        } else if (mPreciseDataConnectedState.size() == 2) {
            if (mPreciseDataConnectedState.contains(PhoneConstants.APN_TYPE_IMS) &&
                    mPreciseDataConnectedState.contains(PhoneConstants.APN_TYPE_EMERGENCY)) {
                Log.d(TAG, "isShowDataActivityIcon(), return false");
                return false;
            }
        }
        Log.d(TAG, "isShowDataActivityIcon(), return true, " +
                "mPreciseDataConnectedState.size(): " + mPreciseDataConnectedState.size());
        return true;
    }

    /**
     * Reset the phonestate info to avoid keeping the wrong state.
     */
    public void cleanPhoneState() {
        // onSignalStrengthsChanged
        mSignalStrength = null;
        // onServiceStateChanged
        mServiceState = null;
        // onDataConnectionStateChanged
        mDataNetType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        mDataState = TelephonyManager.DATA_DISCONNECTED;
    }

    public int getDataState() {
        return mDataState;
    }

    public int getDataNetType() {
        return mDataNetType;
    }

    public ServiceState getServiceState() {
        return mServiceState;
    }

    public SignalStrength getSignalStrength() {
        return mSignalStrength;
    }

    /**
     * Get the signal strength level.
     * @param networkType The NetworkType.
     * @param alwaysShowCdmaRssi always show cdma rssi or not.
     * @return the signal level
     */
    public int getSignalStrengthLevel(NetworkType networkType,
                                      boolean alwaysShowCdmaRssi) {
        if (mSignalStrength == null) {
            return 0;
        }

        if (networkType == NetworkType.Type_4G) {
            return mSignalStrength.getLteLevel();
        } else {
            if (alwaysShowCdmaRssi) {
                return mSignalStrength.getCdmaLevel();
            } else if (mSignalStrength.getGsmLevel() != 0) {
                return mSignalStrength.getGsmLevel();
            } else {
                return mSignalStrength.getLevel();
            }
        }
    }

    public int getPsType() {
        return mPsType;
    }

    public void setPsType(int psType) {
        mPsType = psType;
    }

    /**
     * Whether Sim has Service in Svlte.
     *
     * @return true If Sim is in service.
     */
    public boolean hasService() {
        return hasServiceInSvlte(mServiceState);
    }

    /**
     * Whether Sim is Emergency Only in LTE.
     *
     * @return true If Sim is Emergency Only.
     */
    public boolean isEmergencyOnly() {
        final boolean isOnlyEmergency = mServiceState != null && mServiceState.isEmergencyOnly();
        if (DEBUG) {
            Log.d(TAG, "isOnlyEmergency, slotId = " + mSubscriptionInfo.getSimSlotIndex()
                    + ", isOnlyEmergency = " + isOnlyEmergency);
        }
        return isOnlyEmergency;
    }

    /**
     * Whether Sim is Offline in LTE.
     * @param networkType the the networkType.
     * @return true If Sim is Offline.
     */
    public boolean isOffline(NetworkType networkType) {
        boolean isEmergencyOnly = isEmergencyOnly();
        boolean isOffline = false;
        final boolean isRadioOn = SIMHelper.isRadioOn(mSubscriptionInfo.getSubscriptionId());
        final boolean extraSubRadioOn = mServiceState != null && mServiceState.getDataRegState()
                    != ServiceState.STATE_POWER_OFF;
        /// M: add to check show 4G only condition. @{
        if (isEmergencyOnly) {
            isOffline = true;
            if (networkType == NetworkType.Type_4G
                    && isShow4GDataOnlyForLTE()) {
                isOffline = false;
                Log.d(TAG, "SvLteController.isOffline,networkType: " + networkType +
                        ", set isOffline false");
            }
        } else {
            isOffline = !(isRadioOn || extraSubRadioOn);
        }
        /// @}
        if (DEBUG) {
            Log.d(TAG, "isOffline(), slotId = " + mSubscriptionInfo.getSimSlotIndex()
                    + ", isOffline = " + isOffline
                    + ", isEmergencyOnly = " + isEmergencyOnly
                    + ", isRadioOn = " + isRadioOn
                    + ", extraSubRadioOn = " + extraSubRadioOn
                    + ", mServiceState = " + mServiceState);
        }
        return isOffline;
    }

    /**
     * Whether the data connected in Svlte.
     * @return true data connected.
     */
    public boolean isDataConnected() {
        boolean bSvlteDataConnected = false;
        if (mServiceState != null) {
            bSvlteDataConnected = (mServiceState.getDataRegState() == ServiceState.STATE_IN_SERVICE)
                    && (mDataState == TelephonyManager.DATA_CONNECTED);
        }
        Log.d(TAG, "isSvlteDataConnected, bSvlteDataConnected=" + bSvlteDataConnected
                + " serviceState=" + mServiceState);
        return bSvlteDataConnected;
    }

    /**
     * M: For SVLTE, after compare sub one signal compare the Lte signal.
     *
     * @param oneSubDataNetType the other sub data net type result.
     * @return compare the other sub data net type.
     */
    public int getDataNetTypeWithLTEService(final int oneSubDataNetType) {
        int retDataNetType = oneSubDataNetType;
        int tempDataNetType;
        final ServiceState lteServiceState = mServiceState;
        if (lteServiceState != null) {
            tempDataNetType = getNWTypeByPriority(
                    lteServiceState.getVoiceNetworkType(),
                    lteServiceState.getDataNetworkType());
        } else {
            tempDataNetType = mDataNetType;
        }

        Log.d(TAG, "getDataNetTypeWithLTEService, lteServiceState =" + lteServiceState
                + " mDataNetType=" + mDataNetType + " tempDataNetType=" + tempDataNetType);

        retDataNetType = getNWTypeByPriority(oneSubDataNetType, tempDataNetType);

        // Ap-Irat by self
        if (lteServiceState != null && !lteServiceState.getRoaming() && isApIratSupport()) {
            if (mPsType != PS_SERVICE_UNKNOWN) {
                retDataNetType = lteServiceState.getDataNetworkType();
            }
        }

        Log.d(TAG, "getDataNetTypeWithLTEService, oneSubDataNetType="
                + oneSubDataNetType + " retDataNetType=" + retDataNetType);
        return retDataNetType;
    }

    /**
     * Whether Show 4g data only for LTE SIM.
     * @return true show 4g data only for LTE SIM card.
     */
    public boolean isShow4GDataOnlyForLTE() {
        boolean isShow4GDataOnly = false;
        if (mServiceState != null) {
            if (mServiceState.getVoiceRegState() != ServiceState.STATE_IN_SERVICE
                    && mServiceState.getDataRegState() == ServiceState.STATE_IN_SERVICE) {
                isShow4GDataOnly = true;
            } else if (mServiceState.getVoiceRegState() != ServiceState.STATE_IN_SERVICE) {
                isShow4GDataOnly = is4GDataOnlyMode(mContext);
            }
        }

        if (DEBUG) {
            Log.d(TAG, "isShow4GDataOnlyForLTE: isShow4GDataOnlyForLTE = " + isShow4GDataOnly
                    + ", mServiceState=" + mServiceState);
        }
        return isShow4GDataOnly;
    }

    /**
     * Whether the svlte feature support.
     *
     * @return the svlte feature support or not
     */
    public static final boolean isMediatekSVLteDcSupport() {
        if ("1".equals(SystemProperties.get(MTK_SVLTE_SUPPORT))) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Whether the svlte feature support for the slotId.
     *
     * @param slotId slotId.
     * @return the svlte feature support or not
     */
    public static final boolean isMediatekSVLteDcSupport(int slotId) {
        return isMediatekSVLteDcSupport() && isSvlteSlot(slotId);
    }

    /**
     * Whether the svlte feature support for the SubscriptionInfo.
     *
     * @param info The SubscriptionInfo.
     * @return the svlte feature support or not
     */
    public static final boolean isMediatekSVLteDcSupport(final SubscriptionInfo info) {
        return info != null && isMediatekSVLteDcSupport() && isSvlteSlot(info.getSimSlotIndex());
    }

    /**
     * Whether the SVLTE AP-IRAT Support.
     * @return true if ap-irat support.
     */
    public static final boolean isApIratSupport() {
        if (SystemProperties.get("ro.mtk_svlte_support").equals("1")
                && !SystemProperties.get("ro.c2k.md.irat.support").equals("1")) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Whether it is SvlteSlot.
     * @param slotId slotId.
     * @return true is SvlteSlot.
     */
    public final static boolean isSvlteSlot(int slotId) {
        return slotId == getSvlteSlot();
    }

    /**
     * Whether the ServiceState has Service in Svlte.
     *
     * @param ss The service state.
     * @return true If Sim is in service.
     */
    public static final boolean hasServiceInSvlte(ServiceState ss) {
        if (ss != null) {
            // Consider the device to be in service if either voice or data service is available.
            // Some SIM cards are marketed as data-only and do not support voice service, and on
            // these SIM cards, we want to show signal bars for data service as well as the "no
            // service" or "emergency calls only" text that indicates that voice is not available.
            switch (ss.getVoiceRegState()) {
                case ServiceState.STATE_POWER_OFF:
                case ServiceState.STATE_OUT_OF_SERVICE:
                case ServiceState.STATE_EMERGENCY_ONLY:
                    return ss.getDataRegState() == ServiceState.STATE_IN_SERVICE;
                default:
                    return true;
            }
        } else {
            return false;
        }
    }

    /**
     * Whether is 4G Data Enabled.
     * @param context A Context object
     * @return true if is 4G Data Enabled.
     */
    public final boolean is4GDataEnabled(Context context) {
        final int svlteRatMode = getSvlteRatMode(context);
        final boolean is4GDataEnabled = svlteRatMode == TelephonyManagerEx.SVLTE_RAT_MODE_4G
                || svlteRatMode == TelephonyManagerEx.SVLTE_RAT_MODE_4G_DATA_ONLY;
        if (DEBUG) {
            Log.d(TAG, "is4GDataEnabled(), is4GDataEnabled=" + is4GDataEnabled);
        }
        return is4GDataEnabled;
    }

    /**
     * Whether is 4G Data Only Mode.
     * @param context A Context object
     * @return true if is 4G Data Only Mode.
     */
    public final boolean is4GDataOnlyMode(Context context) {
        final boolean is4GDataOnly = getSvlteRatMode(context)
                == TelephonyManagerEx.SVLTE_RAT_MODE_4G_DATA_ONLY;
        if (DEBUG) {
            Log.d(TAG, "is4GDataOnlyMode, is4GDataOnly=" + is4GDataOnly);
        }
        return is4GDataOnly;
    }

    private final int getSvlteRatMode(Context context) {
        final int svlteRatMode = Settings.Global.getInt(
                context.getContentResolver(),
                TelephonyManagerEx.getDefault().getCdmaRatModeKey(
                    mSubscriptionInfo.getSubscriptionId()),
                TelephonyManagerEx.SVLTE_RAT_MODE_4G);
        if (DEBUG) {
            Log.d(TAG, "getSvlteRatMode(), svlteRatMode = " + svlteRatMode);
        }
        return svlteRatMode;
    }

    private static final boolean is4GUiccCard() {
        boolean is4GUiccCard = false;
        final String cardType = SystemProperties.get(PROPERTY_RIL_FULL_UICC_TYPE[0]);
        final String appType[] = cardType.split(",");
        for (int i = 0; i < appType.length; i++) {
            if ("USIM".equals(appType[i])) {
                is4GUiccCard = true;
                break;
            }
        }

        Log.d(TAG, "is4GUiccCard cardType=" + cardType + ", is4GUiccCard=" + is4GUiccCard);
        return is4GUiccCard;
    }

    private static final int getNWTypeByPriority(int cs, int ps) {
        /// By Network Class.
        if (TelephonyManager.getNetworkClass(cs) > TelephonyManager.getNetworkClass(ps)) {
            return cs;
        } else {
            return ps;
        }
    }

    /**
     * Whether it is the specific BehaviorSet.
     * @param behaviorSet the specific BehaviorSet.
     * @return true is the specific BehaviorSet.
     */
    private final boolean isBehaviorSet(BehaviorSet behaviorSet) {
        return PluginFactory.getStatusBarPlugin(mContext).customizeBehaviorSet() == behaviorSet;
    }
}
