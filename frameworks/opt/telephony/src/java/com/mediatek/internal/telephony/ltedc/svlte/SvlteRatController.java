package com.mediatek.internal.telephony.ltedc.svlte;

import android.app.ActivityManagerNative;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.dataconnection.DctController;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.uicc.UiccController;
import com.mediatek.internal.telephony.RadioManager;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.ltedc.LteDcPhoneProxy;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteModeController;
import com.mediatek.internal.telephony.uicc.SvlteUiccUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SvlteRatController used to switch the SVLTE RAT mode.
 *
 * @hide
 */
public class SvlteRatController extends Handler {
    private static final String LOG_TAG_PHONE = "PHONE";

    private static final int EVENT_RADIO_AVAILABLE = 1000;
    private static final int EVENT_RADIO_NOT_AVAILABLE = 1001;
    private static final int EVENT_RADIO_OFF_OR_NOT_AVAILABLE_CDMA = 1002;
    private static final int EVENT_HANDLE_3G_RADIO_OFF = 1003;

    //For IR and Settings
    private static final int RAT_SWITCH_FOR_NORMAL = 1;
    //For card type change caused rat switch.
    private static final int RAT_SWITCH_FOR_MODE_CHANGE = 2;

    //For pending records which info this action set rat or roaming
    private static final int PENDINGINFO_SET_RAT = 0;
    private static final int PENDINGINFO_SET_ROAMING = 1;
    private static final int PENDINGINFO_SET_RAT_AND_ROAMING = 2;

    //RAT_MODE_CSFB and RAT_MODE_SVLTE only be set on c2k card type ready or change.
    public static final int RAT_MODE_CSFB_2G  = 1;
    public static final int RAT_MODE_CSFB_3G  = 2;
    public static final int RAT_MODE_CSFB_4G  = 4;
    public static final int RAT_MODE_CSFB     = 8;
    public static final int RAT_MODE_SVLTE_2G = 16;
    public static final int RAT_MODE_SVLTE_3G = 32;
    public static final int RAT_MODE_SVLTE_4G = 64;
    public static final int RAT_MODE_SVLTE    = 128;

    public static final int SVLTE_PROJ_DC_3M = 3;
    public static final int SVLTE_PROJ_DC_4M = 4;
    public static final int SVLTE_PROJ_DC_5M = 5;
    public static final int SVLTE_PROJ_DC_6M = 6;
    public static final int SVLTE_PROJ_SC_3M = 103;
    public static final int SVLTE_PROJ_SC_4M = 104;
    public static final int SVLTE_PROJ_SC_5M = 105;
    public static final int SVLTE_PROJ_SC_6M = 106;

    public static final int ENGINEER_MODE_AUTO = 0;
    public static final int ENGINEER_MODE_CDMA = 1;
    public static final int ENGINEER_MODE_CSFB = 2;
    public static final int ENGINEER_MODE_LTE  = 3;

    /**
     * Define the type of SVLTE RAT mode.
     */
    public enum SvlteRatMode {
        SVLTE_RAT_MODE_4G,
        SVLTE_RAT_MODE_3G,
        SVLTE_RAT_MODE_4G_DATA_ONLY;

        /* CDMA could on. */
        public boolean isCdmaOn() {
            return this != SVLTE_RAT_MODE_4G_DATA_ONLY;
        }

        /* LTE could on. */
        public boolean isLteOn() {
            return this != SVLTE_RAT_MODE_3G;
        }

        /* WCDMA/GSM without LTE could on. */
        public boolean isGsmOn() {
            return this == SVLTE_RAT_MODE_3G;
        }
    }

    /**
     * Define the type of roaming mode.
     */
    public enum RoamingMode {
        ROAMING_MODE_HOME,
        ROAMING_MODE_NORMAL_ROAMING,
        ROAMING_MODE_JPKR_CDMA, // only for 4M version.
        ROAMING_MODE_UNKNOWN;

        /* CDMA could on. */
        public boolean isCdmaOn() {
            return this == ROAMING_MODE_HOME || this == ROAMING_MODE_JPKR_CDMA;
        }

        /* LTE could on. */
        public boolean isLteOn() {
            if (CdmaFeatureOptionUtils.getC2KOMNetworkSelectionType() == 1) {
                return this == ROAMING_MODE_NORMAL_ROAMING;
            }
            return true; // return this != ROAMING_MODE_JPKR_CDMA;
        }

        /* WCDMA/GSM without LTE could on. */
        public boolean isGsmOn() {
            return this == ROAMING_MODE_NORMAL_ROAMING;
        }
    }

    /**
     * Define the type of engineer mode.
     */
    public enum EngineerMode {
        ENGINEER_MODE_NONE,
        ENGINEER_MODE_CDMA_ONLY,
        ENGINEER_MODE_GSM_ONLY;
    }

    public static final String INTENT_ACTION_START_SWITCH_SVLTE_RAT_MODE =
            "com.mediatek.intent.action.START_SWITCH_SVLTE_RAT_MODE";
    public static final String INTENT_ACTION_FINISH_SWITCH_SVLTE_RAT_MODE =
            "com.mediatek.intent.action.FINISH_SWITCH_SVLTE_RAT_MODE";
    public static final String EXTRA_SVLTE_RAT_MODE = "svlteRatMode";
    public static final String EXTRA_SVLTE_RAT_SWITCH_PRIORITY = "svlteRatSwitchPriority";
    public static final String INTENT_ACTION_START_SWITCH_ROAMING_MODE =
            "com.mediatek.intent.action.START_SWITCH_ROAMING_MODE";
    public static final String INTENT_ACTION_FINISH_SWITCH_ROAMING_MODE =
            "com.mediatek.intent.action.FINISH_SWITCH_ROAMING_MODE";
    public static final String EXTRA_ROAMING_MODE = "roamingMode";

    //For saving the ir roaming switching state.
    private static final String PROPERTY_RAT_SWITCHING = "ril.rat.switching";

    private LteDcPhoneProxy mLteDcPhoneProxy;
    private Context mContext;
    private PhoneBase mCdmaPhone;
    private PhoneBase mLtePhone;

    private RatSwitchHandler mRatSwitchHandler;

    private int mRadioTechMode = SvlteModeController.RADIO_TECH_MODE_SVLTE;
    private static boolean sIsFlightModePowerOffMdSupport = false;
    private SvlteRatMode mSvlteRatMode = SvlteRatMode.SVLTE_RAT_MODE_4G;
    private RoamingMode mRoamingMode = RoamingMode.ROAMING_MODE_HOME;
    private SvlteRatMode mNewSvlteRatMode = mSvlteRatMode;
    private RoamingMode mNewRoamingMode = mRoamingMode;
    private int mNewRadioTechMode = mRadioTechMode;

    private EngineerMode mEngineerMode = EngineerMode.ENGINEER_MODE_NONE;
    private int mEngMode = getEngineerMode();
    private int mSlotId;
    private boolean mRatChangeInRoaming;
    //For save the network type from radio technology.
    private int mNetworkTypeFromRadioTechnology;
    private SubscriptionManager mSubscriptionManager;

    /**
     * The listener for SvlteRatMode Changed.
     */
    public interface SvlteRatModeChangedListener {
        /**
         * The callback of SvlteRatMode Changed.
         * @param curMode the current mode.
         * @param newMode the new mode.
         */
        void onSvlteRatModeChangeStarted(SvlteRatMode curMode, SvlteRatMode newMode);

        /**
         * The callback of EctMode Changed.
         * @param curMode the current mode.
         * @param newMode the new mode.
         */
        void onSvlteEctModeChangeDone(SvlteRatMode curMode, SvlteRatMode newMode);

        /**
         * The callback of SvlteRatMode Changed.
         * @param preMode the previous mode.
         * @param curMode the current mode.
         */
        void onSvlteRatModeChangeDone(SvlteRatMode preMode, SvlteRatMode curMode);

        /**
         * The callback of RoamingMode Changed.
         * @param preMode the previous mode.
         * @param curMode the current mode.
         */
        void onRoamingModeChange(RoamingMode preMode, RoamingMode curMode);
    }

    private List<SvlteRatModeChangedListener> mSvlteRatModeChangedListeners
                                                  = new ArrayList<SvlteRatModeChangedListener>();

    private final SstSubscriptionsChangedListener mOnSubscriptionsChangedListener
                                                  = new SstSubscriptionsChangedListener();

    /**
     * The listener for subscription changed.
     */
    protected class SstSubscriptionsChangedListener extends
            OnSubscriptionsChangedListener {
        public final AtomicInteger mPreviousSubId = new AtomicInteger(-1);

        @Override
        public void onSubscriptionsChanged() {
            int subId = mLteDcPhoneProxy.getSubId();
            logd("SubscriptionListener.onSubscriptionInfoChanged start,mPreviousSubId= "
                    + mPreviousSubId + " ,subId=" + subId
                    + ", needReSwitch = " + SvlteModeController.
                    getInstance().getNeedReSwitch(mSlotId));
            if (mPreviousSubId.getAndSet(subId) != subId) {
                if (SubscriptionManager.isValidSubscriptionId(subId)
                        && SvlteModeController.getInstance().getNeedReSwitch(mSlotId)) {
                    setRadioTechnology(SvlteModeController.getInstance()
                            .getNetWorkTypeBySlotId(mSlotId), null);
                }
            }
        }
    }
    /**
     * Record the pending switch modes.
     */
    private class PendingSwitchRecord {
        SvlteRatMode mPendingSvlteRatMode;
        RoamingMode mPendingRoamingMode;
        Message mPendingResponse;
        boolean mForceSwitch;
        int mPriority;
        boolean mRatChangeInRoaming;
        int mPendingSetInfo;

        PendingSwitchRecord(SvlteRatMode svlteRatMode, RoamingMode roamingMode,
                Message response, boolean forceSwitch, int priority, int pendingSetInfo) {
            mPendingSvlteRatMode = svlteRatMode;
            mPendingRoamingMode = roamingMode;
            mPendingResponse = response;
            mForceSwitch = forceSwitch;
            mPriority = priority;
            mPendingSetInfo = pendingSetInfo;
        }

        @Override
        public String toString() {
            return ("PendingSwitchRecord : ratMode = " + mPendingSvlteRatMode
                    + " roamingMode = " + mPendingRoamingMode + " forceSwitch = " + mForceSwitch
                    + " priority = " + mPriority + ", pendingSetInfo = " + mPendingSetInfo);
        }
    }
    private static AtomicBoolean sInSwitching = new AtomicBoolean(false);
    private PendingSwitchRecord mPendingRecordForNormal = null;
    private PendingSwitchRecord mPendingRecordForModeSwtich = null;

    /**
     * The constructor of the SvlteRatController.
     * @param lteDcPhoneProxy The instance of LteDcPhoneProxy
     */
    public SvlteRatController(LteDcPhoneProxy lteDcPhoneProxy) {
        mLteDcPhoneProxy = lteDcPhoneProxy;
        mContext = lteDcPhoneProxy.getContext();
        mCdmaPhone = (PhoneBase) mLteDcPhoneProxy.getNLtePhone();
        mLtePhone = (PhoneBase) mLteDcPhoneProxy.getLtePhone();
        mRatSwitchHandler = new RatSwitchHandler(Looper.myLooper());
        mSlotId = mLteDcPhoneProxy.getActivePhone().getPhoneId();

        mLtePhone.mCi.registerForAvailable(this, EVENT_RADIO_AVAILABLE, null);
        mLtePhone.mCi.registerForNotAvailable(this, EVENT_RADIO_NOT_AVAILABLE, null);
        mCdmaPhone.mCi.registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_OR_NOT_AVAILABLE_CDMA,
                null);
        if ("1".equals(SystemProperties.get("ril.flightmode.poweroffMD"))) {
            sIsFlightModePowerOffMdSupport = true;
        }

        if (getSvlteProjectType() == SVLTE_PROJ_SC_3M) {
            mSvlteRatMode = SvlteRatMode.SVLTE_RAT_MODE_3G;
            mNewSvlteRatMode = mSvlteRatMode;
            Settings.Global.putInt(mContext.getContentResolver(),
                    SvlteUtils.getCdmaRatModeKey(mSlotId),
                    SvlteRatMode.SVLTE_RAT_MODE_3G.ordinal());
        }

        logd("sIsFlightModePowerOffMdSupport:" + sIsFlightModePowerOffMdSupport);

        if (!(("OP09").equals(SystemProperties.get("ro.operator.optr", "OM")))) {
            mSubscriptionManager = SubscriptionManager.from(mContext);
            mSubscriptionManager
                .addOnSubscriptionsChangedListener(mOnSubscriptionsChangedListener);

        }

        /// M: Add for airplane mode changed @{
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mContext.registerReceiver(mReceiver, filter);
        mAirplaneMode = isAirplaneModeFromSetting();
        /// M: @}
    }

    /// M: Add for airplane mode changed @{
     private boolean mBlockByRadioPowerOff = false;
     private boolean mAirplaneMode = false;
     private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
         @Override
         public void onReceive(Context context, Intent intent) {
             if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(intent.getAction())) {
                 logd("onReceive: ACTION_AIRPLANE_MODE_CHANGED");
                 updateAirplaneMode(intent, null);
             }
         }
     };

     private final void updateAirplaneMode(Intent intent, Message response) {
         boolean airplaneMode = false;

         if (intent != null) {
             airplaneMode = intent.getBooleanExtra("state", false);
             logd("updateAirplaneMode: intent state= " + airplaneMode);
         } else {
             airplaneMode = isAirplaneModeFromSetting();
         }

         logd("updateAirplaneMode,mAirplaneMode=" + mAirplaneMode
               + ",airplaneMode=" + airplaneMode);

         if (airplaneMode != mAirplaneMode) {
             mAirplaneMode = airplaneMode;
             if (!mAirplaneMode && mBlockByRadioPowerOff) {
                 if (!sIsFlightModePowerOffMdSupport) {
                     logd("updateAirplaneMode: setSvlteRatMode");
                     setSvlteRatMode(mNewSvlteRatMode, mNewRoamingMode,
                        PENDINGINFO_SET_RAT_AND_ROAMING, response);
                 }
             }
         }
     }

     private final boolean isAirplaneModeFromSetting() {
         final boolean airplaneMode = (Settings.Global.getInt(mContext.getContentResolver(),
                 Settings.Global.AIRPLANE_MODE_ON, 0) == 1);
         logd("isAirplaneModeFromSetting: airplaneMode= " + airplaneMode);
         return airplaneMode;
     }
     /// Add for airplane mode changed @}

    private void setRadioTechMode(SvlteRatMode svlteRatMode,
            RoamingMode roamingMode) {
        if (isUseNetworkTypeDirectly()
            && ((mNetworkTypeFromRadioTechnology & RAT_MODE_SVLTE) != 0)) {
            mNewRadioTechMode = SvlteModeController.RADIO_TECH_MODE_SVLTE;
            logd("setRadioTechMode, mNetworkTypeFromRadioTechnology="
                    + mNetworkTypeFromRadioTechnology + " return");
            return;
        }

        boolean hasCdmaApp = containsCdmaApp();
        if (hasCdmaApp) {
            if (isCdma4GSim()
                    && svlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_4G_DATA_ONLY
                    || roamingMode == RoamingMode.ROAMING_MODE_NORMAL_ROAMING) {
                mNewRadioTechMode = SvlteModeController.RADIO_TECH_MODE_CSFB;
            } else if (roamingMode == RoamingMode.ROAMING_MODE_HOME) {
                mNewRadioTechMode = SvlteModeController.RADIO_TECH_MODE_SVLTE;
            }
        } else if (!isFixedSvlteMode()) {
            mNewRadioTechMode = SvlteModeController.RADIO_TECH_MODE_CSFB;
        }
        logd("setRadioTechMode, hasCdmaApp = " + hasCdmaApp
                + " svlteRatMode = " + svlteRatMode + "roamingMode = "
                + roamingMode + "mNewRadioTechMode = " + mNewRadioTechMode);
    }

    private boolean isLteOn() {
        // radio power
        boolean lteOn = (mNewSvlteRatMode.isLteOn() && mNewRoamingMode.isLteOn()) ||
                (mNewSvlteRatMode.isGsmOn() && mNewRoamingMode.isGsmOn());
        // for gsm sim card.
        if (mNewRadioTechMode == SvlteModeController.RADIO_TECH_MODE_CSFB) {
            lteOn = true;
        }
        logd("lteOn = " + lteOn);
        return lteOn;
    }

    private boolean isCdmaOn() {
        boolean cdmaOn = mNewSvlteRatMode.isCdmaOn() && mNewRoamingMode.isCdmaOn();
        if (mNewRadioTechMode == SvlteModeController.RADIO_TECH_MODE_CSFB) {
            cdmaOn = false;
            //modem limitation for roaming.
            if (CdmaFeatureOptionUtils.getC2KOMNetworkSelectionType() == 0
                && isCdma4GSim()
                && mNewSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_4G) {
                cdmaOn = true;
            }
        }
        logd("cdmaOn = " + cdmaOn);
        return cdmaOn;
    }
    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
        case EVENT_RADIO_AVAILABLE:
            if (mEngMode == ENGINEER_MODE_CDMA) {
                logd("EM: CDMA only mode");
                RadioManager.getInstance().setRadioPower(false,
                        mLtePhone.getPhoneId());
            } else if (mEngMode == ENGINEER_MODE_CSFB) {
                logd("EM: CSFB only mode");
                mNewRadioTechMode = SvlteModeController.RADIO_TECH_MODE_CSFB;
                setSvlteRatMode(SvlteRatMode.SVLTE_RAT_MODE_4G,
                        RoamingMode.ROAMING_MODE_NORMAL_ROAMING, true,
                        RAT_SWITCH_FOR_NORMAL, PENDINGINFO_SET_RAT_AND_ROAMING, null);
            } else if (mEngMode == ENGINEER_MODE_LTE) {
                logd("EM: LTE only mode");
                mNewRadioTechMode = SvlteModeController.RADIO_TECH_MODE_CSFB;
                mLteDcPhoneProxy.toggleActivePhone(mNewRadioTechMode);
            } else {
                logd("EVENT_RADIO_AVAILABLE, Auto mode, Reset to roaming mode to Home"
                        + ", mSvlteRatMode=" + mSvlteRatMode
                        + ", mRoamingMode:" + mRoamingMode
                        + ", mNewRadioTechMode:" + mNewRadioTechMode);
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                      && (SvlteUtils.isActiveSvlteMode(
                              mLteDcPhoneProxy.getActivePhone().getPhoneId())
                              || isCtDualModeSimCard(mSlotId))
                      && mSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_3G) {
                    mRoamingMode = RoamingMode.ROAMING_MODE_HOME;
                }
            }
            mLtePhone.mCi.unregisterForAvailable(this);
            break;
        case EVENT_RADIO_NOT_AVAILABLE:
            logd("EVENT_RADIO_NOT_AVAILABLE, RadioState=" + mLtePhone.mCi.getRadioState());
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                    && (SvlteUtils.isActiveSvlteMode(mLteDcPhoneProxy.getActivePhone()
                            .getPhoneId()) || isCtDualModeSimCard(mSlotId))) {
                mLtePhone.mCi.registerForAvailable(this, EVENT_RADIO_AVAILABLE, null);
            }
            break;
        case EVENT_RADIO_OFF_OR_NOT_AVAILABLE_CDMA:
            logd("EVENT_RADIO_OFF_OR_NOT_AVAILABLE_CDMA");
            if (!sIsFlightModePowerOffMdSupport) {
                if (mCdmaPhone.mCi.getRadioState() == CommandsInterface.RadioState.RADIO_OFF
                        && mSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_3G
                        && mNewSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_3G
                        && mRadioTechMode == SvlteModeController.RADIO_TECH_MODE_SVLTE) {
                    logd("Sending EVDOMODE=0");
                    mCdmaPhone.mCi.configEvdoMode(0,
                            obtainMessage(EVENT_HANDLE_3G_RADIO_OFF));
                }
            }
            break;
        case EVENT_HANDLE_3G_RADIO_OFF:
            logd("EVENT_HANDLE_3G_RADIO_OFF. Sending ECTMODE=2");
            mLtePhone.mCi.setSvlteRatMode(
                    SvlteModeController.RADIO_TECH_MODE_SVLTE,
                    mSvlteRatMode.ordinal(),
                    SvlteRatMode.SVLTE_RAT_MODE_3G.ordinal(),
                    mRoamingMode.ordinal(),
                    mRoamingMode.ordinal(),
                    SvlteUiccUtils.getInstance().isCt3gDualMode(mSlotId), null);
            break;
        default:
            break;
        }
    }

    /**
     * @return the single instance of RatController.
     */
    //TODO:temp method, will be phase out later.
    public static SvlteRatController getInstance() {
        return ((LteDcPhoneProxy) PhoneFactory
                .getPhone(PhoneConstants.SIM_ID_1)).getSvlteRatController();
    }

    /**
     * Get the svlte rat mode.
     * @return svlte mode.
     */
    public SvlteRatMode getSvlteRatMode() {
        return mSvlteRatMode;
    }

    /**
     * Set SVLTE RAT mode.
     * @param svlteRatMode SVLTE RAT mode index.
     * @param response the responding message.
     * @return return if switch successfully.
     */
    public boolean setSvlteRatMode(int svlteRatMode, Message response) {
        return setSvlteRatMode(SvlteRatMode.values()[svlteRatMode], response);
    }

    /**
     * Set SVLTE RAT mode.
     * @param svlteRatMode SVLTE RAT mode.
     * @param response the responding message.
     * @return return if switch successfully.
     */
    public boolean setSvlteRatMode(SvlteRatMode svlteRatMode, Message response) {
        RoamingMode mode;
        if (mPendingRecordForNormal != null) {
            mode = mPendingRecordForNormal.mPendingRoamingMode;
            mPendingRecordForNormal = null;
        } else {
            mode = mNewRoamingMode;
        }
        return setSvlteRatMode(svlteRatMode, mode, PENDINGINFO_SET_RAT, response);
    }

    /**
     * Get the roaming mode.
     * @return roaming mode.
     */
    public RoamingMode getRoamingMode() {
        return mRoamingMode;
    }

    /**
     * Set if on roaming.
     * @param roaming The roaming mode.
     * @param response The responding message.
     * @return return if switch successfully.
     */
    public boolean setRoamingMode(boolean roaming, Message response) {
        RoamingMode mode = roaming ? RoamingMode.ROAMING_MODE_NORMAL_ROAMING
                : RoamingMode.ROAMING_MODE_HOME;
        return setRoamingMode(mode, response);
    }

    /**
     * Set if on roaming.
     * @param roamingMode The roaming mode.
     * @param response The responding message.
     * @return return if switch successfully.
     */
    public boolean setRoamingMode(RoamingMode roamingMode, Message response) {
        if (mEngMode != ENGINEER_MODE_AUTO) {
            logd("[setRoamingMode] In engineer mode:" + mEngMode);
            return false;
        } else {
            logd("[setRoamingMode] roamingMode:" + roamingMode);
        }

        SvlteRatMode mode;
        if (mPendingRecordForModeSwtich != null) {
            mode = mPendingRecordForModeSwtich.mPendingSvlteRatMode;
            mPendingRecordForModeSwtich = null;
        } else if (mPendingRecordForNormal != null) {
            mode = mPendingRecordForNormal.mPendingSvlteRatMode;
            mPendingRecordForNormal = null;
        } else {
            mode = mNewSvlteRatMode;
        }
        setRadioTechMode(mode, roamingMode);
        // radio off firstly by the condition.
        // radio maybe on by SIM On/flight mode, will result network searching and selecting again.

        resetRadioPowerOff(mode, roamingMode);
        // set the new roaming mode.
        return setSvlteRatMode(mode, roamingMode, PENDINGINFO_SET_ROAMING, response);
    }

    /**
     * Set SVLTE RAT mode and ROAMING mode.
     * @param svlteRatMode SVLTE RAT mode.
     * @param roamingMode The roaming mode.
     * @param response he responding message.
     * @return return if switch successfully.
     */
    private boolean setSvlteRatMode(SvlteRatMode svlteRatMode,
            RoamingMode roamingMode, int setInfo, Message response) {
        logd("[setSvlteRatMode] svlteRatMode: " + svlteRatMode + " mRoamingMode: "
                + mRoamingMode);
        return setSvlteRatMode(svlteRatMode, roamingMode, false,
                    RAT_SWITCH_FOR_NORMAL, setInfo, response);
    }

    /**
     * Set radio technology.
     * @param networkType network type.
     * @param response responding message.
     * @return return if setting is applied.
     */
    public boolean setRadioTechnology(int networkType, Message response) {
        logd("[setRadioTechnology] networkType:" + networkType);

        if (mEngMode != ENGINEER_MODE_AUTO) {
            logd("[setRadioTechnology] In engineer mode:" + mEngMode);
            if ((networkType & RAT_MODE_CSFB) > 0
                    || (networkType & RAT_MODE_SVLTE) > 0) {
                finishSwitchMode(true, false, false, RAT_SWITCH_FOR_MODE_CHANGE);
            } else {
                finishSwitchMode(true, false, false, RAT_SWITCH_FOR_NORMAL);
            }
            return false;
        }

        // Save the network type.
        if (isUseNetworkTypeDirectly()
                && ((networkType & RAT_MODE_SVLTE) > 0)) {
            mNetworkTypeFromRadioTechnology = networkType;
        }

        SvlteRatMode svlteRatMode = mNewSvlteRatMode;
        boolean forceSwitch = false;
        if ((networkType & RAT_MODE_CSFB) > 0
                || (networkType & RAT_MODE_SVLTE) > 0) {
            forceSwitch = true;
        }
        if (networkType >= RAT_MODE_SVLTE_2G) {
            if (((networkType & RAT_MODE_SVLTE_2G) > 0)
                    && ((networkType & RAT_MODE_SVLTE_3G) > 0)
                    && ((networkType & RAT_MODE_SVLTE_4G) > 0)) {
                svlteRatMode = SvlteRatMode.SVLTE_RAT_MODE_4G;
            } else if (((networkType & RAT_MODE_SVLTE_2G) > 0)
                    && ((networkType & RAT_MODE_SVLTE_3G) > 0)) {
                svlteRatMode = SvlteRatMode.SVLTE_RAT_MODE_3G;
                // For ct card switch 2g/3g in roaming. @{
                if (mRoamingMode == RoamingMode.ROAMING_MODE_NORMAL_ROAMING) {
                    mRatChangeInRoaming = true;
                }
                /// @}
            } else if ((networkType & RAT_MODE_SVLTE_4G) > 0) {
                svlteRatMode = SvlteRatMode.SVLTE_RAT_MODE_4G_DATA_ONLY;
            } else {
                logd("[setRadioTechnology] mode not supported: " + networkType);
            }
        }
        int recordPriority = RAT_SWITCH_FOR_NORMAL;
        if ((networkType & RAT_MODE_CSFB) > 0
                || (networkType & RAT_MODE_SVLTE) > 0) {
            recordPriority = RAT_SWITCH_FOR_MODE_CHANGE;
        }

        mPendingRecordForModeSwtich = null;
        if (mPendingRecordForNormal != null) {
            mRoamingMode = mPendingRecordForNormal.mPendingRoamingMode;
            mPendingRecordForNormal = null;
        }

        //ALPS02095114
        if (svlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_4G_DATA_ONLY) {
            int capabilityPhoneId = Integer.valueOf(
                    SystemProperties.get(PhoneConstants.PROPERTY_CAPABILITY_SWITCH, "1")) - 1;
            logd("[setRadioTechnology] capabilityPhoneId: "
                    + capabilityPhoneId + " mSlotId: " + mSlotId);

            if (capabilityPhoneId != mSlotId) {
                svlteRatMode = SvlteRatMode.SVLTE_RAT_MODE_4G;
            }
        }

        logd("[setRadioTechnology] networkType: " + networkType + " svlteRatMode: "
                + svlteRatMode + " mRoamingMode: " + mRoamingMode
                + " forceSwitch: " + forceSwitch + " recordPriority: "
                + recordPriority);
        return setSvlteRatMode(svlteRatMode, mRoamingMode, forceSwitch,
                   recordPriority, PENDINGINFO_SET_RAT, response);
    }

    private boolean setSvlteRatMode(SvlteRatMode svlteRatMode,
            RoamingMode roamingMode, boolean forceSwitch, int recordPriority,
            int pendingSetInfo, Message response) {
        if (blockByRadioPowerOff()) {
            /// M: Add for airplane mode changed @{
            mBlockByRadioPowerOff = true;
            if (!sIsFlightModePowerOffMdSupport) {
                mNewSvlteRatMode = svlteRatMode;
                mNewRoamingMode = roamingMode;
            }
            /// M: @}
            finishSwitchMode(true, false, false, recordPriority);
            logd("setSvlteRatMode(), block by radio power off now.");
            return false;
        }

        // For CT Card, check the eng mode.
        if (!forceSwitch && !mRatChangeInRoaming) {
            if (mEngMode != ENGINEER_MODE_AUTO) {
                logd("setSvlteRatMode(), In engineer mode:" + mEngMode);
                return false;
            }
            if (mSvlteRatMode == svlteRatMode && mRoamingMode == roamingMode
                    && mNewRadioTechMode == mRadioTechMode) {
                finishSwitchMode(true, false, false, recordPriority);
                logd("setSvlteRatMode(), already in desired mode -> no need to switch, "
                        + "send finish broadcast");
                return false;
            }
        }

        logd("setSvlteRatMode(), radioTechMode from " + mRadioTechMode + " to "
                + mNewRadioTechMode);
        logd("setSvlteRatMode(), SvlteRatMode from " + mSvlteRatMode + " to " + svlteRatMode);
        logd("setSvlteRatMode(), RoamingMode from " + mRoamingMode + " to " + roamingMode);
        logd("setSvlteRatMode(), sInSwitching=" + sInSwitching.get());
        if (sInSwitching.get()) {
            if (recordPriority == RAT_SWITCH_FOR_MODE_CHANGE) {
                mPendingRecordForModeSwtich = new PendingSwitchRecord(svlteRatMode,
                        roamingMode, response, forceSwitch, recordPriority, pendingSetInfo);
            } else {
                mPendingRecordForNormal = new PendingSwitchRecord(svlteRatMode,
                        roamingMode, response, forceSwitch, recordPriority, pendingSetInfo);
                if (mRatChangeInRoaming) {
                    mPendingRecordForNormal.mRatChangeInRoaming = true;
                    mRatChangeInRoaming = false;
                }
            }
            return true;
        }
        if (mRatChangeInRoaming) {
            setRadioTechMode(svlteRatMode, mRoamingMode);
            int preferNetworkType = RILConstants.NETWORK_MODE_WCDMA_PREF;
            if (SystemProperties.get("ro.mtk_svlte_lcg_support", "0").equals("1")) {
                logd(" setSvlteRatMode(), 4M mRatChangeInRoaming=" + mRatChangeInRoaming
                        + " set to NETWORK_MODE_GSM_ONLY");
                preferNetworkType = RILConstants.NETWORK_MODE_GSM_ONLY;
            } else {
                logd(" setSvlteRatMode(), NOT 4M! mRatChangeInRoaming=" + mRatChangeInRoaming
                        + " set to NETWORK_MODE_WCDMA_PREF");
            }

            mLtePhone.setPreferredNetworkType(preferNetworkType, response);
            logd("setSvlteRatMode, rat change in roaming, rat: "
                    + preferNetworkType
                    + " mNewRadioTechMode: " + mNewRadioTechMode
                    + " svlteRatMode: " + svlteRatMode + " mRoamingMode: "
                    + mRoamingMode);
            mNewSvlteRatMode = SvlteRatMode.SVLTE_RAT_MODE_3G;
            mSvlteRatMode = SvlteRatMode.SVLTE_RAT_MODE_3G;
            finishSwitchMode(true, true, false, recordPriority);
            mRatChangeInRoaming = false;
            return true;
        }
        mNewSvlteRatMode = svlteRatMode;
        mNewRoamingMode = roamingMode;
        startSwitchMode(mSvlteRatMode != svlteRatMode, mRoamingMode != roamingMode);

        mRatSwitchHandler.doSwitch(forceSwitch, response, recordPriority);
        return true;
    }

    private int getCardType() {
        int[] cardType = UiccController.getInstance().getC2KWPCardType();
        logd("[getCardType]: SIM" + mSlotId + " type: " + cardType[mSlotId]);
        return cardType[mSlotId];
    }

    private boolean containsCdmaApp() {
        if (isCtCard(mSlotId)) {
            int capabilitySlotId = SystemProperties.getInt(
                    PhoneConstants.PROPERTY_CAPABILITY_SWITCH, 1) - 1;
            if (isDualCtCard() && mSlotId != capabilitySlotId) {
                return false;
            }
            return true;
        }
        return false;
    }

    private boolean isDualCtCard() {
        int[] cardType = UiccController.getInstance().getC2KWPCardType();
        for (int i = 0; i < cardType.length; i++) {
            if (!isCtCard(i)) {
                logd("isDualCtCard slotId=" + i + " return false");
                return false;
            }
        }
        logd("isDualCtCard true ");
        return true;
    }

    private boolean isCtCard(int slotId) {
        int[] cardType = UiccController.getInstance().getC2KWPCardType();
        boolean retCtCard = ((cardType[slotId] & UiccController.CARD_TYPE_RUIM) > 0)
                || ((cardType[slotId] & UiccController.CARD_TYPE_CSIM) > 0)
                || SvlteUiccUtils.getInstance().isCt3gDualMode(slotId);
        logd("isCtCard, slotId=" + slotId + " retCtCard=" + retCtCard);
        return retCtCard;
    }

    private boolean containsUsimApp(int cardType) {
        if ((cardType & UiccController.CARD_TYPE_USIM) > 0) {
            return true;
        }
        return false;
    }

    /**
     * Get the current sim is cdma 4g sim card or not.
     * @return true if the current sim is cdma 4g sim card.
     */
    public boolean isCdma4GSim() {
        int cardType = getCardType();
        if (containsCdmaApp() && containsUsimApp(cardType)) {
            return true;
        }
        return false;
    }

    private boolean isUseNetworkTypeDirectly() {
        int[] cardType = UiccController.getInstance().getC2KWPCardType();
        // If slot0 has no SIM card and slot1 has no CT card (including no SIM inserted).
        boolean slot0NoSimAndSlot1NoCTInserted = false;
        if (TelephonyManager.getDefault().getPhoneCount() > 1) {
            if (mSlotId == 0 && (cardType[0] == UiccController.CARD_TYPE_NONE)
                    && !isCtCard(1)) {
                slot0NoSimAndSlot1NoCTInserted = true;
            }
        }

        logd("isUseModeNetworkTypeDirectly slot0NoSimAndSlot1NoCTInserted="
                + slot0NoSimAndSlot1NoCTInserted);
        return slot0NoSimAndSlot1NoCTInserted;
    }

    public static int getEngineerMode() {
        int mode = SystemProperties.getInt("persist.radio.ct.ir.engmode", 0);

        return mode;
    }

    private void resetRadioPowerOff(SvlteRatMode svlteRatMode, RoamingMode roamingMode) {
        if (!isLteOn() && mLtePhone.mCi.getRadioState().isOn()) {
            RadioManager.getInstance().setRadioPower(false, mLtePhone.getPhoneId());
            }
        if (!isCdmaOn() && mCdmaPhone.mCi.getRadioState().isOn()) {
            RadioManager.getInstance().setRadioPower(false,  mCdmaPhone.getPhoneId());
        }
    }

    private boolean blockByRadioPowerOff() {
        int airplaneMode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0);
        if (airplaneMode == 1) {
            logd("blockByRadioPowerOff(), airplaneMode=" + airplaneMode);
            return true;
        }
        return false;
    }

    /**
     * Register the listener for SvlteRatMode Changed.
     * @param listener the SvlteRatModeChangedListener instance.
     */
    public void registerSvlteRatModeChangedListener(SvlteRatModeChangedListener listener) {
        if (!mSvlteRatModeChangedListeners.contains(listener)) {
            mSvlteRatModeChangedListeners.add(listener);
        }
    }

    /**
     * Unregister the listener for SvlteRatMode Changed.
     * @param listener listener which want to register
     */
    public void unregisterSvlteRatModeChangedListener(SvlteRatModeChangedListener listener) {
        mSvlteRatModeChangedListeners.remove(listener);
    }

    private void startSwitchMode(boolean svlteRatChanged, boolean roamingChanged) {
        sInSwitching.set(true);
        PhoneBase phoneBase = (PhoneBase) (mLteDcPhoneProxy.getActivePhone());
        if (svlteRatChanged) {
            Intent intent = new Intent(INTENT_ACTION_START_SWITCH_SVLTE_RAT_MODE);
            intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, phoneBase.getPhoneId());
            ActivityManagerNative.broadcastStickyIntent(intent, null, UserHandle.USER_ALL);
        }
        if (roamingChanged) {
            Intent intent = new Intent(INTENT_ACTION_START_SWITCH_ROAMING_MODE);
            intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, phoneBase.getPhoneId());
            ActivityManagerNative.broadcastStickyIntent(intent, null, UserHandle.USER_ALL);
            SystemProperties.set(PROPERTY_RAT_SWITCHING, "1");
        }
    }

    private void finishSwitchMode(boolean forceFinish, boolean svlteRatChanged,
            boolean roamingChanged, int priority) {
        logd("finishSwitchMode, forceFinish = " + forceFinish
                + ", svlteRatChanged = " + svlteRatChanged
                + ", roamingChanged = " + roamingChanged
                + ", priority = " + priority);

        PhoneBase phoneBase = (PhoneBase) (mLteDcPhoneProxy.getActivePhone());
        if (forceFinish || svlteRatChanged) {
            Intent intent = new Intent(INTENT_ACTION_FINISH_SWITCH_SVLTE_RAT_MODE);
            intent.putExtra(EXTRA_SVLTE_RAT_MODE, mSvlteRatMode.ordinal());
            intent.putExtra(EXTRA_SVLTE_RAT_SWITCH_PRIORITY, priority);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, phoneBase.getPhoneId());
            ActivityManagerNative.broadcastStickyIntent(intent, null, UserHandle.USER_ALL);
        }
        if (roamingChanged) {
            Intent intent = new Intent(INTENT_ACTION_FINISH_SWITCH_ROAMING_MODE);
            intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
            intent.putExtra(EXTRA_ROAMING_MODE, mRoamingMode.ordinal());
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, phoneBase.getPhoneId());
            ActivityManagerNative.broadcastStickyIntent(intent, null, UserHandle.USER_ALL);
            SystemProperties.set(PROPERTY_RAT_SWITCHING, "0");
        }

        sInSwitching.set(false);

        executePendingSwitchRecord();
    }

    private final void executePendingSwitchRecord() {
        logd("executePendingSwitchRecord, mPendingCardTypeSwitchRecord ="
                + mPendingRecordForModeSwtich + " mPendingModeSwitchRecord = "
                + mPendingRecordForNormal);

        PendingSwitchRecord pendingRecord = null;
        if (mPendingRecordForModeSwtich != null) {
            pendingRecord = mPendingRecordForModeSwtich;
            mPendingRecordForModeSwtich = null;
        } else if (mPendingRecordForNormal != null) {
            pendingRecord = mPendingRecordForNormal;
            mPendingRecordForNormal = null;
        }

        // update the last switch result, because some status maybe change during one switch
        logd("executePendingSwitchRecord, mSvlteRatMode = " + mSvlteRatMode
                + ", mRoamingMode = " + mRoamingMode);
        if (pendingRecord != null) {
            if (pendingRecord.mPendingSetInfo == PENDINGINFO_SET_RAT) {
                pendingRecord.mPendingRoamingMode = mRoamingMode;
            } else if (pendingRecord.mPendingSetInfo == PENDINGINFO_SET_ROAMING) {
                pendingRecord.mPendingSvlteRatMode = mSvlteRatMode;
            } else {
                logd("executePendingSwitchRecord, no need to update any rat or roaming info");
            }

            logd("executePendingSwitchRecord, " + pendingRecord);

            setSvlteRatMode(pendingRecord.mPendingSvlteRatMode,
                    pendingRecord.mPendingRoamingMode,
                    pendingRecord.mForceSwitch,
                    pendingRecord.mPriority,
                    pendingRecord.mPendingSetInfo,
                    pendingRecord.mPendingResponse);
            mRatChangeInRoaming = pendingRecord.mRatChangeInRoaming;
        } else {
            final int phoneCount = TelephonyManager.getDefault().getPhoneCount();
            for (int slotId = 0; slotId < phoneCount; slotId++) {
                if (slotId != mSlotId) {
                    final Phone phone = PhoneFactory.getPhone(slotId);
                    if (phone != null && phone instanceof LteDcPhoneProxy) {
                        final SvlteRatController ratController = ((LteDcPhoneProxy) phone)
                                .getSvlteRatController();
                        if (ratController.hasPendingSwitchRecord()) {
                            logd("executePendingSwitchRecord, notify slotId = " + slotId);
                            ratController.executePendingSwitchRecord();
                        }
                    }
                }
            }
        }
    }

    private final boolean hasPendingSwitchRecord() {
        return mPendingRecordForModeSwtich != null || mPendingRecordForNormal != null;
    }

    /**
     * Handler used to enable/disable each dual connection.
     */
    private class RatSwitchHandler extends Handler {
        private static final int EVENT_SWITCH_SVLTE_MODE = 101;
        private static final int EVENT_DEACTIVE_PDP = 102;
        private static final int EVENT_CONFIG_EVDO_MODE = 103;
        private static final int EVENT_LTE_RADIO_ON = 104;
        private static final int EVENT_LTE_RADIO_OFF = 105;
        private static final int EVENT_CDMA_RADIO_ON = 106;
        private static final int EVENT_CDMA_RADIO_OFF = 107;
        private static final int EVENT_SWITCH_SVLTE_MODE_DONE = 108;
        private static final int EVENT_ACTIVE_PDP = 109;
        private static final int EVENT_ACTIVE_PDP_DONE = 110;
        private static final int EVENT_CHECK_RADIO_CHANGE_DONE = 111;
        private static final int EVENT_NOTIFY_MODE_CHANGED = 112;
        private static final int EVENT_ECTMODE_CHANGED = 113;
        private static final int EVENT_ALL_DATA_DISCONNECTED = 114;

        private boolean mByEngineerMode;
        private Message mResponseMessage;
        private boolean mSvlteRatChanged;
        private boolean mRoamingChanged;
        private boolean mNeedAllRadioChange;
        private boolean mLteRadioChanged;
        private boolean mCdmaRadioChanged;
        private int mRecordPriority;

        public RatSwitchHandler(Looper looper) {
            super(looper);
        }

        private boolean isEnableOrDisable4GSwitch() {
            return mNewSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_3G
                    || mSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_3G;
        }

        //Add for 4g/4g TDD only switch frequently change case.
        private boolean registerRadioOnOff() {
            logd("registerRadioOnOff, mRecordPriority = " + mRecordPriority);
            if (mRecordPriority == RAT_SWITCH_FOR_MODE_CHANGE) {
                return false;
            }

            logd("registerRadioOnOff, lteon = " + mLtePhone.mCi.getRadioState().isOn()
                    + " cdmaon= " + mCdmaPhone.mCi.getRadioState().isOn());

            if (isCdma4GSim() && mSvlteRatMode != mNewSvlteRatMode) {
                if (mNewSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_3G) {
                    if (mLtePhone.mCi.getRadioState().isOn()) {
                        // disable 4G
                        mLtePhone.mCi.registerForOffOrNotAvailable(this, EVENT_LTE_RADIO_OFF, null);
                        if (mSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_4G_DATA_ONLY) {
                            mNeedAllRadioChange = true;
                        } else {
                            return true;
                        }
                    }
                } else if (mSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_3G) {
                    if (!mLtePhone.mCi.getRadioState().isOn()) {
                        // enable 4G
                        mLtePhone.mCi.registerForOn(this, EVENT_LTE_RADIO_ON, null);
                        // 3g->Tdd data only.
                        if (mNewSvlteRatMode != SvlteRatMode.SVLTE_RAT_MODE_4G_DATA_ONLY) {
                            return true;
                        }
                    }
                }
            }
            if (mNewSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_4G_DATA_ONLY) {
                if (mCdmaPhone.mCi.getRadioState().isOn()) {
                    // enable 4g tdd
                    mCdmaPhone.mCi.registerForOffOrNotAvailable(this, EVENT_CDMA_RADIO_OFF, null);
                    if (mSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_3G) {
                        mNeedAllRadioChange = true;
                    }
                    return true;
                } else if (mSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_3G) {
                    return true;
                }
            } else if (mSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_4G_DATA_ONLY
                    && RadioManager.getInstance().isSvlteTestSimAllowPowerOn(
                            mCdmaPhone.getPhoneId())) {
                if (!mCdmaPhone.mCi.getRadioState().isOn()) {
                    // disable 4g tdd
                    mCdmaPhone.mCi.registerForOn(this, EVENT_CDMA_RADIO_ON, null);
                    return true;
                }
            }
            return false;
        }

        public void doSwitch(boolean forceSwitch, Message response , int recordPriority) {
            mRecordPriority = recordPriority;
            if (!mSvlteRatModeChangedListeners.isEmpty()) {
                final int count = mSvlteRatModeChangedListeners.size();
                for (int i = 0; i < count; i++) {
                    SvlteRatModeChangedListener lis = mSvlteRatModeChangedListeners.get(i);
                    if (lis != null) {
                        lis.onRoamingModeChange(mRoamingMode, mNewRoamingMode);
                        lis.onSvlteRatModeChangeStarted(mSvlteRatMode, mNewSvlteRatMode);
                    }
                }
            }
            setRadioTechMode(mNewSvlteRatMode, mNewRoamingMode);
            logd("[doSwitch] mNewRadioTechMode=" + mNewRadioTechMode
                    + ", mNewSvlteRatMode=" + mNewSvlteRatMode
                    + ", mNewRoamingMode=" + mNewRoamingMode);
            if (mResponseMessage != null) {
                logd("[doSwitch] mResponseMessage= " + mResponseMessage);
            } else {
                logd("[doSwitch] mResponseMessage= null");
            }
            mResponseMessage = response;
            // radio off for expected result firstly.
            resetRadioPowerOff(mNewSvlteRatMode, mNewRoamingMode);
            if (isUseNetworkTypeDirectly()) {
                obtainMessage(RatSwitchHandler.EVENT_SWITCH_SVLTE_MODE).sendToTarget();
            } else if (mNewRadioTechMode != SvlteModeController.RADIO_TECH_MODE_CSFB
                    && isEnableOrDisable4GSwitch()) {
                obtainMessage(RatSwitchHandler.EVENT_DEACTIVE_PDP).sendToTarget();
            } else {
                // 4g Tdd ->4g
                if (mNewRadioTechMode == SvlteModeController.RADIO_TECH_MODE_SVLTE
                        && mNewSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_4G) {
                    obtainMessage(RatSwitchHandler.EVENT_CONFIG_EVDO_MODE).sendToTarget();
                } else {
                    obtainMessage(RatSwitchHandler.EVENT_SWITCH_SVLTE_MODE).sendToTarget();
                }
            }
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case EVENT_DEACTIVE_PDP:
                logd("EVENT_DEACTIVE_PDP" + ", mNewSvlteRatMode="
                        + mNewSvlteRatMode + ", mSvlteRatMode=" + mSvlteRatMode);
                // enable or disable 4G, deactive pdp
                if (mSvlteRatMode != mNewSvlteRatMode) {
                    if (mSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_3G) {
                        ((CDMAPhone) mCdmaPhone).registerForAllDataDisconnected(this,
                                EVENT_ALL_DATA_DISCONNECTED, null);
                        DctController.getInstance().setDataAllowed(
                                mCdmaPhone.getSubId(), false, "SvlteRatControll",
                                330000);
                    } else if (mNewSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_3G) {
                        ((GSMPhone) mLtePhone).registerForAllDataDisconnected(this,
                                EVENT_ALL_DATA_DISCONNECTED, null);
                        DctController.getInstance().setDataAllowed(
                                mLtePhone.getSubId(), false, "SvlteRatControll",
                                330000);
                    } else {
                        sendMessageDelayed(obtainMessage(EVENT_CONFIG_EVDO_MODE), 2000);
                    }
                } else {
                    sendMessageDelayed(obtainMessage(EVENT_CONFIG_EVDO_MODE), 2000);
                }
                break;

            case EVENT_ALL_DATA_DISCONNECTED:
                logd("event EVENT_ALL_DATA_DISCONNECTED");
                if (mSvlteRatMode != mNewSvlteRatMode) {
                    if (mSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_3G) {
                        ((CDMAPhone) mCdmaPhone).unregisterForAllDataDisconnected(this);
                    } else if (mNewSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_3G) {
                        ((GSMPhone) mLtePhone).unregisterForAllDataDisconnected(this);
                    }
                }
                sendMessageDelayed(obtainMessage(EVENT_CONFIG_EVDO_MODE), 2000);
                break;

            case EVENT_CONFIG_EVDO_MODE:
                // config eHPRD
                logd("EVENT_CONFIG_EVDO_MODE.config eHPRD");
                // if 3G->TDD data only, no need to config the evdo mode.
                if (mNewSvlteRatMode != SvlteRatMode.SVLTE_RAT_MODE_4G_DATA_ONLY) {
                    mCdmaPhone.mCi.configEvdoMode(
                        (mNewSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_4G) ? 1 : 0,
                        obtainMessage(EVENT_SWITCH_SVLTE_MODE));
                } else {
                    //send empty message
                    obtainMessage(EVENT_SWITCH_SVLTE_MODE).sendToTarget();
                }
                break;

            case EVENT_SWITCH_SVLTE_MODE:
                logd("EVENT_SWITCH_SVLTE_MODE.");
                // Set SVLTE RAT mode
                if (mNewSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_4G_DATA_ONLY) {
                    //3g->4g tdd/4g->4g tdd
                    mLtePhone.mCi.setSvlteRatMode(mNewRadioTechMode, mSvlteRatMode.ordinal(),
                            mNewSvlteRatMode.ordinal(), mRoamingMode.ordinal(),
                            mNewRoamingMode.ordinal(), false, obtainMessage(EVENT_ECTMODE_CHANGED));
                } else if (containsCdmaApp() && isEnableOrDisable4GSwitch()) {
                    // 3g->4g/4g->3g/4g tdd->3g
                    if (SvlteUiccUtils.getInstance().isCt3gDualMode(mSlotId)) {
                        mCdmaPhone.mCi.setSvlteRatMode(mNewRadioTechMode, mSvlteRatMode.ordinal(),
                                mNewSvlteRatMode.ordinal(),
                                mRoamingMode.ordinal(),
                                mNewRoamingMode.ordinal(), true,
                                null);
                    }
                    mLtePhone.mCi.setSvlteRatMode(mNewRadioTechMode, mSvlteRatMode.ordinal(),
                            mNewSvlteRatMode.ordinal(), mRoamingMode.ordinal(),
                            mNewRoamingMode.ordinal(),
                            SvlteUiccUtils.getInstance().isCt3gDualMode(mSlotId),
                            obtainMessage(EVENT_ECTMODE_CHANGED));
                } else {
                    int cardType = getCardType();
                    boolean hasCdmaApp = containsCdmaApp();
                    boolean hasUsimApp = containsUsimApp(cardType);
                    if (isFixedSvlteMode() || hasCdmaApp) {
                        if (hasUsimApp) {
                            logd("cardType: CDMA CSIM, handled by GMSS");
                            mLtePhone.mCi.setSvlteRatMode(mNewRadioTechMode,
                                    mSvlteRatMode.ordinal(),
                                    mNewSvlteRatMode.ordinal(),
                                    mRoamingMode.ordinal(),
                                    mNewRoamingMode.ordinal(), false,
                                    obtainMessage(EVENT_ECTMODE_CHANGED));
                        } else {
                            logd("cardType: CDMA UIM");
                            if (SvlteUiccUtils.getInstance().isCt3gDualMode(
                                    mSlotId)) {
                                mCdmaPhone.mCi.setSvlteRatMode(
                                        mNewRadioTechMode,
                                        mSvlteRatMode.ordinal(),
                                        mNewSvlteRatMode.ordinal(),
                                        mRoamingMode.ordinal(),
                                        mNewRoamingMode.ordinal(), true, null);
                            }

                            mLtePhone.mCi.setSvlteRatMode(mNewRadioTechMode,
                                    mSvlteRatMode.ordinal(), mNewSvlteRatMode
                                            .ordinal(), mRoamingMode.ordinal(),
                                    mNewRoamingMode.ordinal(),
                                    SvlteUiccUtils.getInstance()
                                            .isCt3gDualMode(mSlotId),
                                    obtainMessage(EVENT_ECTMODE_CHANGED));
                        }
                    } else {
                        if (hasUsimApp) {
                            logd("cardType: USIM");
                        } else {
                            logd("cardType: SIM");
                        }
                        //send empty message
                        obtainMessage(EVENT_ACTIVE_PDP_DONE).sendToTarget();
                    }
                }
                // Switch STK/UTK mode
                if (mNewRadioTechMode == SvlteModeController.RADIO_TECH_MODE_CSFB) {
                    mLtePhone.mCi.setStkSwitchMode(0);
                } else {
                    mLtePhone.mCi.setStkSwitchMode(1);
                }
                break;

            case EVENT_ACTIVE_PDP:
                logd("EVENT_ACTIVE_PDP.");
                // enable or disable 4G, active pdp
                if (mSvlteRatMode != mNewSvlteRatMode) {
                    DctController.getInstance().setDataAllowed(
                            mCdmaPhone.getSubId(), true, null, 0);
                }
                sendMessageDelayed(obtainMessage(EVENT_ACTIVE_PDP_DONE), 0);
                break;

            case EVENT_ACTIVE_PDP_DONE:
                logd("EVENT_ACTIVE_PDP_DONE.");
                // switch action phone
                mLteDcPhoneProxy.toggleActivePhone(mNewRadioTechMode);
                if (!registerRadioOnOff()) {
                    obtainMessage(EVENT_SWITCH_SVLTE_MODE_DONE).sendToTarget();
                }

                mSvlteRatChanged = mSvlteRatMode != mNewSvlteRatMode;
                mRoamingChanged = mRoamingMode != mNewRoamingMode;

                Message notifyModeMsg = obtainMessage(RatSwitchHandler.EVENT_NOTIFY_MODE_CHANGED);
                notifyModeMsg.arg1 = mSvlteRatMode.ordinal();
                notifyModeMsg.arg2 = mNewSvlteRatMode.ordinal();
                notifyModeMsg.sendToTarget();

                if (!mByEngineerMode) {
                    mSvlteRatMode = mNewSvlteRatMode;
                    mRoamingMode = mNewRoamingMode;
                    mRadioTechMode = mNewRadioTechMode;
                }
                logd("update values. mSvlteRatMode = " + mSvlteRatMode + ", mRoamingMode = " + mRoamingMode);
                if (!blockByRadioPowerOff()) {
                    logd("EVENT_SWITCH_SVLTE_MODE, not in airplane mode, set radio power");
                    // radio power
                    RadioManager.getInstance().setRadioPower(isLteOn(), mLtePhone.getPhoneId());
                    RadioManager.getInstance().setRadioPower(isCdmaOn(), mCdmaPhone.getPhoneId());
                }
                break;

            case EVENT_LTE_RADIO_OFF:
                logd("EVENT_LTE_RADIO_OFF.");
                mLteRadioChanged = true;
                mLtePhone.mCi.unregisterForOffOrNotAvailable(this);
                obtainMessage(EVENT_CHECK_RADIO_CHANGE_DONE).sendToTarget();
                break;

            case EVENT_LTE_RADIO_ON:
                logd("EVENT_LTE_RADIO_ON.");
                mLteRadioChanged = true;
                mLtePhone.mCi.unregisterForOn(this);
                obtainMessage(EVENT_CHECK_RADIO_CHANGE_DONE).sendToTarget();
                break;

            case EVENT_CDMA_RADIO_OFF:
                logd("EVENT_CDMA_RADIO_OFF.");
                mCdmaRadioChanged = true;
                mCdmaPhone.mCi.unregisterForOffOrNotAvailable(this);
                obtainMessage(EVENT_CHECK_RADIO_CHANGE_DONE).sendToTarget();
                break;

            case EVENT_CDMA_RADIO_ON:
                logd("EVENT_CDMA_RADIO_ON.");
                mCdmaRadioChanged = true;
                mCdmaPhone.mCi.unregisterForOn(this);
                obtainMessage(EVENT_CHECK_RADIO_CHANGE_DONE).sendToTarget();
                break;

            case EVENT_CHECK_RADIO_CHANGE_DONE:
                logd("EVENT_CHECK_RADIO_CHANGE_DONE. mNeedAllRadioChange = "
                        + mNeedAllRadioChange + "mCdmaRadioChanged = "
                        + mCdmaRadioChanged + " mLteRadioChanged = "
                        + mLteRadioChanged);
                if (mNeedAllRadioChange) {
                    if (mCdmaRadioChanged && mLteRadioChanged) {
                        obtainMessage(EVENT_SWITCH_SVLTE_MODE_DONE).sendToTarget();
                    }
                } else {
                    obtainMessage(EVENT_SWITCH_SVLTE_MODE_DONE).sendToTarget();
                }
                break;

            case EVENT_SWITCH_SVLTE_MODE_DONE:
                logd("EVENT_SWITCH_SVLTE_MODE_DONE.");
                mNeedAllRadioChange = false;
                mCdmaRadioChanged = false;
                mLteRadioChanged = false;
                // invoke responding message.
                Message message = mResponseMessage;
                if (message != null && message.getTarget() != null) {
                    message.sendToTarget();
                }
                // invoke done
                final int recordPriority = mRecordPriority;
                mRecordPriority = RAT_SWITCH_FOR_NORMAL;
                finishSwitchMode(true, mSvlteRatChanged, mRoamingChanged, recordPriority);
                break;
            case EVENT_NOTIFY_MODE_CHANGED:
                logd("EVENT_NOTIFY_MODE_CHANGED.");
                if (!mSvlteRatModeChangedListeners.isEmpty()) {
                    SvlteRatMode preMode = SvlteRatMode.values()[msg.arg1];
                    SvlteRatMode curMode = SvlteRatMode.values()[msg.arg2];
                    final int count = mSvlteRatModeChangedListeners.size();
                    for (int i = 0; i < count; i++) {
                        SvlteRatModeChangedListener lis = mSvlteRatModeChangedListeners.get(i);
                        if (lis != null) {
                            lis.onSvlteRatModeChangeDone(preMode, curMode);
                        }
                    }
                }
                break;
            case EVENT_ECTMODE_CHANGED:
                logd("EVENT_ECTMODE_CHANGED.");
                if (!mSvlteRatModeChangedListeners.isEmpty()) {
                    final int count = mSvlteRatModeChangedListeners.size();
                    for (int i = 0; i < count; i++) {
                        SvlteRatModeChangedListener lis = mSvlteRatModeChangedListeners.get(i);
                        if (lis != null) {
                            lis.onSvlteEctModeChangeDone(mSvlteRatMode, mNewSvlteRatMode);
                        }
                    }
                }
                if (mNewSvlteRatMode == SvlteRatMode.SVLTE_RAT_MODE_4G_DATA_ONLY) {
                    obtainMessage(EVENT_ACTIVE_PDP_DONE).sendToTarget();
                } else if (isEnableOrDisable4GSwitch()) {
                    obtainMessage(EVENT_ACTIVE_PDP).sendToTarget();
                } else {
                    obtainMessage(EVENT_ACTIVE_PDP_DONE).sendToTarget();
                }
                break;
            default:
                break;
            }
        }
    }

    /**
     * Whether allow the radio power on request.
     * @param phoneId The phoneId request to power on radio.
     * @return Whether allow the radio power on request or not.
     */
    public boolean allowRadioPowerOn(int phoneId) {
        Phone phone = mLteDcPhoneProxy.getPhoneById(phoneId);
        if (!CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                || phone == null
                || !(SvlteUtils.isActiveSvlteMode(phone)
                || isCtDualModeSimCard(SvlteUtils.getSlotId(phoneId)))) {
            logd("allowRadioPowerOn return for non-svlte slot.");
            return true;
        }

        int capabilitySlotId = SystemProperties.getInt(
                PhoneConstants.PROPERTY_CAPABILITY_SWITCH, 1) - 1;
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() && isDualCtCard()
                && capabilitySlotId != SvlteUtils.getSvltePhoneIdByPhoneId(phoneId)) {
            logd("allowRadioPowerOn return for dual ct card non-svlte slot.");
            return true;
        }

        if (mEngMode == ENGINEER_MODE_CDMA && mLtePhone.getPhoneId() == phoneId) {
            logd("allowRadioPowerOn=false on LtePhone in CDMA Only for engineer mode.");
            return false;
        }

        SvlteRatMode ratMode = mNewSvlteRatMode;
        RoamingMode roamingMode = mNewRoamingMode;

        //M: Here, for India5M Project, isGmssOn is always false;
        boolean isGmssOn = false;
        if (CdmaFeatureOptionUtils.getC2KOMNetworkSelectionType() == 0) {
            isGmssOn = isCdma4GSim() && (ratMode == SvlteRatMode.SVLTE_RAT_MODE_4G);
        }
        boolean lwgOn = (ratMode.isLteOn() && roamingMode.isLteOn()) ||
                (ratMode.isGsmOn() && roamingMode.isGsmOn());
        boolean cdmaOn = (ratMode.isCdmaOn() && roamingMode.isCdmaOn()) || isGmssOn;
        logd("allowRadioPowerOn lwgOn="+lwgOn+", cdmaOn="+cdmaOn+", isGmssOn="+isGmssOn);
        int phoneType = mLteDcPhoneProxy.getPhoneById(phoneId).getPhoneType();

        logd("allowRadioPowerOn, phoneId=" + phoneId + ", phoneType=" + phoneType
                + ", ratMode=" + ratMode + ", roamingMode=" + roamingMode);

        if (phoneType == PhoneConstants.PHONE_TYPE_CDMA && !cdmaOn) {
            logd("allowRadioPowerOn=false on C2K radio.");
            return false;
        } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM && !lwgOn) {
            logd("allowRadioPowerOn=false on LWG radio.");
            return false;
        }
        return true;
    }


    /**
     * Whether it is CT dual mode SIM card.
     * @param slotId the slot Id.
     * @return true is CT dual mode SIM card.
     */
    public boolean isCtDualModeSimCard(int slotId) {
        logd(" isCtDualModeSimCard, start");
        boolean ctDualModeSimCard = isCdma4GSim()
                || SvlteUiccUtils.getInstance().isCt3gDualMode(slotId);
        logd(" isCtDualModeSimCard, ctDualModeSimCard = " + ctDualModeSimCard);
        return ctDualModeSimCard;
    }

    private boolean isFixedSvlteMode() {
        boolean fixedSvlteMode = (mSlotId == PhoneConstants.SIM_ID_1) &&
                ("OP09").equals(SystemProperties.get("ro.operator.optr", "OM"));
        logd(" fixedSvlteMode = " + fixedSvlteMode);
        return fixedSvlteMode;
    }

    /**
     * getSvlte project by Systemproperties.
     * @return the project type.
     */
    public static int getSvlteProjectType() {
        int type = SVLTE_PROJ_DC_6M;
        int nwSelType = CdmaFeatureOptionUtils.getC2KOMNetworkSelectionType();
        String omMode = SystemProperties.get("ro.mtk.c2k.om.mode", "cllwtg");

        if (nwSelType == 1) {
            if (omMode.equals("cllwtg")) {
                type = SVLTE_PROJ_SC_6M;
            } else if (omMode.equals("cllwg")) {
                type = SVLTE_PROJ_SC_5M;
            } else if (omMode.equals("cllg")) {
                type = SVLTE_PROJ_SC_4M;
            } else if (omMode.equals("cwg")) {
                type = SVLTE_PROJ_SC_3M;
            }
        } else {
            if (omMode.equals("cllwtg")) {
                type = SVLTE_PROJ_DC_6M;
            } else if (omMode.equals("cllwg")) {
                type = SVLTE_PROJ_DC_5M;
            } else if (omMode.equals("cllg")) {
                type = SVLTE_PROJ_DC_4M;
            } else if (omMode.equals("cwg")) {
                type = SVLTE_PROJ_DC_3M;
            }
        }
        //logd("omMode=" + omMode + ", nwSelType=" + nwSelType + ", type=" + type);
        return type;
    }

    private void logd(String msg) {
        Rlog.d(LOG_TAG_PHONE, "[SRC" + mSlotId + "]" + msg);
    }
}
