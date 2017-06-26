package com.mediatek.internal.telephony.ltedc.svlte.apirat;

import java.util.ArrayList;
import java.util.List;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.ServiceState;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.cdma.CdmaServiceStateTracker;
/// Customize for swip via code.@{
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.cdma.IPlusCodeUtils;
import com.mediatek.internal.telephony.cdma.ViaPolicyManager;
/// @}
import com.mediatek.internal.telephony.ltedc.LteDcPhoneProxy;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteRatController.RoamingMode;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteRatController.SvlteRatMode;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteRatController.SvlteRatModeChangedListener;
import com.mediatek.internal.telephony.uicc.SvlteUiccUtils;

/**
 * International roaming controller for ap based IR on SVLTE IRAT.
 *
 * @hide
 */
public class SvlteIrController {
    private static final int NO_SERVICE_DELAY_TIME = 15 * 1000;
    private static final int NO_SEERVICE_WATCHDOG_DELAY_TIME = 300 * 1000;
    private static final int SWITCH_RESUME_DELAY_TIME = 20 * 1000;
    private static final int FIND_NETWORK_DELAY_TIME = 30 * 1000;

    private RoamingMode mRoamingMode;
    private boolean mIsEnabled = false;
    private boolean mIsSwitchingTo3GMode = false;

    private static int sNoServiceDelayTime = NO_SERVICE_DELAY_TIME;
    private static int sWatchdogDelayTime = NO_SEERVICE_WATCHDOG_DELAY_TIME;
    private static int sSwitchModeOrResumeDelayTime = SWITCH_RESUME_DELAY_TIME;
    private static int sFindNetworkDelayTime = FIND_NETWORK_DELAY_TIME;

    private LteController mLteControllerObj;
    private CdmaController mCdmaControllerObj;
    private Strategy mSwitchStrategy;
    private PhoneBase mLtePhone;
    private PhoneBase mCdmaPhone;

    private LteDcPhoneProxy mLteDcPhoneProxy;
    private int mActivePhoneId;

    private SvlteRatModeChangedListener mModeChangedListener = new SvlteRatModeChangedListener() {
            @Override
            public void onSvlteRatModeChangeStarted(SvlteRatMode curMode, SvlteRatMode newMode) {
                logd("onSvlteRatModeChangeStarted() curMode = " + curMode + " newMode" + newMode);
                if (!isCtDualModeSimCard(mActivePhoneId)) {
                    logd("onSvlteRatModeChangeStarted() not CT 4G or 3G dual mode return");
                    return;
                }
                if ((curMode != SvlteRatMode.SVLTE_RAT_MODE_3G &&
                     newMode == SvlteRatMode.SVLTE_RAT_MODE_3G) ||
                     (curMode == SvlteRatMode.SVLTE_RAT_MODE_3G &&
                      newMode != SvlteRatMode.SVLTE_RAT_MODE_3G)) {
                    // disable IR controll while switching between AP-iRAT IR and MD-iRAT IR
                    if (!mCdmaControllerObj.isCt3gCardType()) {
                        setIfEnabled(false);
                    }
                }

                if (newMode == SvlteRatMode.SVLTE_RAT_MODE_3G) {
                    mLteControllerObj.setIfEnabled(true);
                    mCdmaControllerObj.setIfEnabled(true);
                } else {
                    mLteControllerObj.setIfEnabled(false);
                    mCdmaControllerObj.setIfEnabled(false);
                }

                if (curMode != SvlteRatMode.SVLTE_RAT_MODE_3G &&
                    newMode == SvlteRatMode.SVLTE_RAT_MODE_3G) {
                    mIsSwitchingTo3GMode = true;
                }
            }

            @Override
            public void onSvlteEctModeChangeDone(SvlteRatMode curMode, SvlteRatMode newMode) {
                logd("onSvlteEctModeChangeDone() curMode = " + curMode + " newMode" + newMode);
                if (!isCtDualModeSimCard(mActivePhoneId)) {
                    logd("onSvlteEctModeChangeDone()  not CT 4G or 3G dual mode return");
                    return;
                }
                if (newMode == SvlteRatMode.SVLTE_RAT_MODE_3G &&
                    curMode != SvlteRatMode.SVLTE_RAT_MODE_3G) {
                    setIfEnabled(true);
                }
            }

            @Override
            public void onSvlteRatModeChangeDone(SvlteRatMode preMode, SvlteRatMode curMode) {
                logd("onSvlteRatModeChangeDone() preMode = " + preMode + " curMode" + curMode);
                if (!isCtDualModeSimCard(mActivePhoneId)) {
                    logd("onSvlteRatModeChangeDone()  not CT 4G or 3G dual mode return");
                    return;
                }

                mIsSwitchingTo3GMode = false;

                if (curMode == SvlteRatMode.SVLTE_RAT_MODE_3G &&
                    preMode != SvlteRatMode.SVLTE_RAT_MODE_3G) {
                    setIfEnabled(true);
                }

                mRoamingMode = mLteDcPhoneProxy.getSvlteRatController().getRoamingMode();

                if (curMode == SvlteRatMode.SVLTE_RAT_MODE_3G) {
                    mLteControllerObj.setIfEnabled(true);
                    mCdmaControllerObj.setIfEnabled(true);
                } else {
                    mLteControllerObj.setIfEnabled(false);
                    mCdmaControllerObj.setIfEnabled(false);
                }
            }

            @Override
            public void onRoamingModeChange(RoamingMode preMode, RoamingMode curMode) {
                mRoamingMode = curMode;
            }
        };

    /**
     * Interface for Strategy to operate network controller.
     *
     * @hide
     */
    interface INetworkControllerListener {
        void onRadioStateChanged(boolean isRadioOn);
        String onPreSelectPlmn(String[] plmnList);
        void onPlmnChanged(String plmn);
        void onNetworkInfoReady(List<OperatorInfo> networkInfoArray);
        void onServiceStateChanged(ServiceType serviceType);
        void onRoamingModeSwitchDone();
    }

    /**
     * Interface for Strategy to listen network controller information.
     *
     * @hide
     */
    interface INetworkController {
        void setRoamingMode(RoamingMode roamingMode);
        void resumeNetwork();
        void findAvailabeNetwork();
        void cancelAvailableNetworks();
        void registerNetworkManually(OperatorInfo oi);
        void dispose();
        void registerListener(INetworkControllerListener listener);
        void startNewSearchRound();
        void setIfEnabled(boolean isEnabled);
    }

    private void setRoaming(RoamingMode roamingMode, Message response) {

        logd("setRoaming, roamingMode=" + roamingMode + ", mIsEnabled = " + mIsEnabled +
            " mIsSwitchingTo3GMode = " + mIsSwitchingTo3GMode);

        if (!mIsEnabled && !mIsSwitchingTo3GMode) {
            logd("setRoaming, roamingMode=" + roamingMode + ", in disabled mode");
            mRoamingMode = roamingMode;
            if (response != null) {
                response.sendToTarget();
            }
        } else {
            boolean ret;
            ret = mLteDcPhoneProxy.getSvlteRatController().setRoamingMode(roamingMode, response);
            if (ret) {
                logd("setRoaming, roamingMode=" + roamingMode);
                mRoamingMode = roamingMode;
            } else {
                logd("setRoaming, roamingMode=" + roamingMode + ",return false");
            }
        }
    }

    // SvlteRatController set the roaming mode async, so we need
    // to record the latest roaming mode by ourselves
    private RoamingMode getRoamingMode() {
        logd("getRoamingMode, mRoamingMode=" + mRoamingMode);
        return mRoamingMode;
    }

    public SvlteIrController(LteDcPhoneProxy lteDcPhoneProxy) {

        EngineerModeHandler emh = new EngineerModeHandler(lteDcPhoneProxy);

        if (emh != null && emh.processedEngineerMode()) {
            return;
        }

        if (!SystemProperties.get("persist.sys.ct.ir.switcher", "1").equals("1")) {
            return;
        }

        logd(" constructor, lteDcPhoneProxy=" + lteDcPhoneProxy);
        mLteDcPhoneProxy = lteDcPhoneProxy;

        mActivePhoneId = mLteDcPhoneProxy.getActivePhone().getPhoneId();
        logd(" constructor, mActivePhoneId=" + mActivePhoneId);

        mLtePhone =  (PhoneBase) lteDcPhoneProxy.getLtePhone();
        mCdmaPhone = (PhoneBase) lteDcPhoneProxy.getNLtePhone();
        mLteControllerObj = new LteController(this, mLtePhone, mActivePhoneId);
        mCdmaControllerObj = new CdmaController(this, mCdmaPhone, mActivePhoneId);


        mLteDcPhoneProxy.getSvlteRatController().
                               registerSvlteRatModeChangedListener(mModeChangedListener);

        String mode = SystemProperties.get("persist.sys.ct.ir.mode", "0");

        logd(" constructor, mode = " + mode);

        // for debug to adjust no service delay time
        sNoServiceDelayTime = SystemProperties.getInt("persist.sys.ct.ir.nsd",
                                                      NO_SERVICE_DELAY_TIME);

        sSwitchModeOrResumeDelayTime = SystemProperties.getInt("persist.sys.ct.ir.rnsd",
                                                      SWITCH_RESUME_DELAY_TIME);

        sFindNetworkDelayTime = SystemProperties.getInt("persist.sys.ct.ir.fnd",
                                                      FIND_NETWORK_DELAY_TIME);

        if (SystemProperties.getInt("ro.mtk_c2k_om_nw_sel_type", 0) == 1) {
            logd(" constructor, StrategyOM");
            mSwitchStrategy = new StrategyOM(this, mLteControllerObj, mCdmaControllerObj);
            setIfEnabled(true);
        } else {
            if (mode.equals("0")) { // default value
                if (SystemProperties.get("ro.mtk_svlte_lcg_support", "0").equals("1")) {
                    logd(" constructor, Strategy4M");
                    mSwitchStrategy = new Strategy4M(this, mLteControllerObj, mCdmaControllerObj);
                } else {
                    logd(" constructor, Strategy5M");
                    mSwitchStrategy = new Strategy5M(this, mLteControllerObj, mCdmaControllerObj);
                }
            } else if (mode.equals("5")) { // 5M
                logd(" constructor, Strategy5M");
                mSwitchStrategy = new Strategy5M(this, mLteControllerObj, mCdmaControllerObj);
            } else if (mode.equals("4")) { // 4M
                logd(" constructor, Strategy4M");
                mSwitchStrategy = new Strategy4M(this, mLteControllerObj, mCdmaControllerObj);
            } else {
                throw new RuntimeException("SvlteIrController() no Strategy!!!");
            }
        }

        mRoamingMode = RoamingMode.ROAMING_MODE_HOME;
    }

    private boolean isCtDualModeSimCard(int slotId) {
        return mLteDcPhoneProxy.getSvlteRatController().isCtDualModeSimCard(slotId);
    }

    /**
     * Set if IR controller is enabled.
     * @param isEnabled if enabled
     * @hide
     */
    public void setIfEnabled(boolean isEnabled) {
        logd(" setIfEnabled, isEnabled = " + isEnabled);
        mIsEnabled = isEnabled;
        mSwitchStrategy.setIfEnabled(isEnabled);
    }

    /**
     * Enable the whole IR.
     * @param isEnabled true enable the all IR.
     * @hide
     */
    public void setEnableIr(boolean isEnabled) {
        setIfEnabled(isEnabled);
        mLteControllerObj.setIfEnabled(isEnabled);
        mCdmaControllerObj.setIfEnabled(isEnabled);
    }

    private boolean getIfEnabled() {
        return mIsEnabled;
    }

    /**
     * Set roaming mode changed by other module.
     * @param roamingMode new roaming mode
     * @hide
     */
    private void roamingModeChanged(RoamingMode roamingMode) {
        logd(" roamingModeChanged, roamingMode = " + roamingMode +
             " mIsEnabled = " + mIsEnabled);
        if (roamingMode != getRoamingMode()) {
            mRoamingMode = roamingMode;
            mLteControllerObj.startNewSearchRound();
            mCdmaControllerObj.startNewSearchRound();
        }
    }

    private void dispose() {
        mLteControllerObj.dispose();
        mCdmaControllerObj.dispose();
    }

    /**
     * Process the engineer mode.
     */
    private class EngineerModeHandler extends Handler {
        protected static final int EVENT_RADIO_AVAILABLE = 5000;

        private String mIrEngMode = SystemProperties.get("persist.radio.ct.ir.engmode", "0");

        private PhoneBase mCdmaPhoneBase;
        private LteDcPhoneProxy mLteDcPhoneProxy;

        private EngineerModeHandler(LteDcPhoneProxy lteDcPhoneProxy) {
            mLteDcPhoneProxy = lteDcPhoneProxy;
            mCdmaPhoneBase = (PhoneBase) lteDcPhoneProxy.getNLtePhone();
        }

        @Override
        public void handleMessage(Message msg) {
                logdForEngMode("handleMessage, msg.what=" + msg.what);
            switch (msg.what) {
            case EVENT_RADIO_AVAILABLE:
                dispose();
                break;
            default:
                super.handleMessage(msg);
            }
        }

        /**
         * Need to process Engineer mode.
         * @return true processed.
         */
        public boolean processedEngineerMode() {
            logdForEngMode("processedEngineerMode, sIrEngMode=" + mIrEngMode);
            if (mIrEngMode.equals("1") || mIrEngMode.equals("2")) {
                mCdmaPhoneBase.mCi.registerForAvailable(this,
                        EVENT_RADIO_AVAILABLE, null);
                return true;
            }
            return false;
        }

        /**
         * Dispose the object.
         */
        public void dispose() {
            if (mCdmaPhoneBase != null) {
                logdForEngMode("dispose, unregisterForAvailable");
                mCdmaPhoneBase.mCi.unregisterForAvailable(this);
            }
        }

        private void logdForEngMode(String msg) {
            logd(" [EngineerModeHandler], " + msg);
        }
    }

    /**
     * Base class of network controller.
     *
     * @hide
     */
    private abstract class PhoneController extends Handler implements INetworkController {
        protected static final int STATE_UNKNOWN = 0;
        protected static final int STATE_INIT = 1;
        protected static final int STATE_NO_SERVICE = 2;
        protected static final int STATE_GETTING_PLMN = 3;
        protected static final int STATE_SELECTING_NETWORK = 4;
        protected static final int STATE_NETWORK_SELECTED = 5;

        protected static final int EVENT_RADIO_NO_SERVICE = 310;
        protected static final int EVENT_SERVICE_STATE_CHANGED = 311;
        protected static final int EVENT_ROAMING_MODE_CHANGED = 312;

        protected PhoneBase mPhone;
        protected SvlteIrController mIrController;
        protected CommandsInterface mCi;

        protected ServiceType mServiceType;

        protected int mState = STATE_UNKNOWN;
        protected int mPreState = STATE_UNKNOWN;

        protected String[] mPlmns = null;

        protected INetworkControllerListener mListener;

        protected boolean mIsFirstRoundSearch = true;

        protected int mPreVoiceState = -1;

        protected int mPreDataState = -1;

        protected int mPhoneControllerActivePhoneId;

        protected PhoneController(SvlteIrController controller, PhoneBase phone
                , int activePhoneId) {
            super();
            mIrController = controller;
            mPhone = phone;
            mCi = phone.mCi;
            mPhoneControllerActivePhoneId = activePhoneId;
        }

        protected void setState(int state) {
            logdForController(" setState:" + stateToString(state)
                              + " mState = " + stateToString(mState)
                              + " mPreState = " + stateToString(mPreState));
            if (mState != state) {
                mPreState = mState;
                mState = state;

                if (state == STATE_INIT) {
                    resetToInitialState();
                }
            }
        }

        protected void resetToInitialState() {
            logdForController(" reset to initial state");
            mIsFirstRoundSearch = true;
            mPreVoiceState = -1;
            mPreDataState = -1;
            mPlmns = null;
        }

        protected int getState() {
            logdForController(" getState:" + stateToString(mState));
            return mState;
        }


        protected String msgToString(int msgWhat) {
            return "unknown";
        }

        protected void setServiceType(ServiceType serviceType) {
            logdForController(" setServiceType(" + serviceType + ") mServiceType = "
                    + mServiceType);

            if (getState() != STATE_INIT) {
                if (serviceType != ServiceType.IN_SERVICE) {
                    setState(STATE_NO_SERVICE);
                } else if (getState() == STATE_NO_SERVICE) {
                    setState(mPreState);
                }
            }

            if (mServiceType != serviceType) {
                mServiceType = serviceType;
                if (serviceType != ServiceType.OUT_OF_SERVICE) {
                    removeNoServiceMessage();
                    if (mListener != null) {
                        // if on service or searching, call listener immediaetlly
                        mListener.onServiceStateChanged(serviceType);
                    }
                } else {
                    // need delay 20s to callback no service state
                    // as the service would be back soon
                    if (enableNoSerivceDelay()) {
                        sendNoServiceMessage(sNoServiceDelayTime);
                    } else if (mListener != null) {
                        mListener.onServiceStateChanged(serviceType);
                    }
                }
            }
        }

        @Override
        public void handleMessage(Message msg) {
            logdForController(" handleMessage: " + msgToString(msg.what));
            switch (getState()) {
                case STATE_INIT:
                    processInitState(msg);
                    break;
                case STATE_NO_SERVICE:
                    processNoServiceState(msg);
                    break;
                case STATE_GETTING_PLMN:
                    processGettingPlmnState(msg);
                    break;
                case STATE_SELECTING_NETWORK:
                    processSelectingNWState(msg);
                    break;
                case STATE_NETWORK_SELECTED:
                    defaultMessageHandler(msg);
                    break;
                default:
                    break;
            }
        }

        protected void defaultMessageHandler(Message msg) {
            switch (msg.what) {
                case EVENT_ROAMING_MODE_CHANGED:
                    if (mListener != null) {
                       mListener.onRoamingModeSwitchDone();
                    }
                    break;
                default:
                    break;
            }
        }

        protected void processInitState(Message msg) {
            defaultMessageHandler(msg);
        }

        protected void processNoServiceState(Message msg) {
            switch (msg.what) {
                case EVENT_RADIO_NO_SERVICE:
                    if (mListener != null && (mServiceType != ServiceType.IN_SERVICE)) {
                        mListener.onServiceStateChanged(mServiceType);
                    }
                    break;
                default:
                    defaultMessageHandler(msg);
                    break;
            }
        }

        protected void processGettingPlmnState(Message msg) {
            defaultMessageHandler(msg);
        }

        protected void processSelectingNWState(Message msg) {
            defaultMessageHandler(msg);
        }

        @Override
        public void registerListener(INetworkControllerListener listener) {
            mListener = listener;
        }

        @Override
        public void setRoamingMode(RoamingMode roamingMode) {}

        @Override
        public void resumeNetwork() {}

        @Override
        public void dispose() {}

        @Override
        public void findAvailabeNetwork() {}

        @Override
        public void cancelAvailableNetworks() {}

        @Override
        public void registerNetworkManually(OperatorInfo oi) {}

        protected String stateToString(int state) {
            switch (state) {
                case STATE_UNKNOWN:
                    return "STATE_UNKNOWN";
                case STATE_INIT:
                    return "STATE_INIT";
                case STATE_NO_SERVICE:
                    return "STATE_NO_SERVICE";
                case STATE_GETTING_PLMN:
                    return "STATE_GETTING_PLMN";
                case STATE_SELECTING_NETWORK:
                    return "STATE_SELECTING_NETWORK";
                case STATE_NETWORK_SELECTED:
                    return "STATE_NETWORK_SELECTED";
                default:
                    return "STATE_INVALID";
            }
        }
        /**
         * Send no service message to request switch phone after the given duration.
         *
         * @param delayedTime
         */
        protected void sendNoServiceMessage(int delayedTime) {
            if (!hasMessages(EVENT_RADIO_NO_SERVICE)) {
                sendMessageDelayed(obtainMessage(EVENT_RADIO_NO_SERVICE), delayedTime);
            }
        }

        protected void removeNoServiceMessage() {
            removeMessages(EVENT_RADIO_NO_SERVICE);
        }

        protected void postponeNoServiceMessageIfNeeded(int delayedTime) {
            if (hasMessages(EVENT_RADIO_NO_SERVICE)) {
                removeMessages(EVENT_RADIO_NO_SERVICE);
                sendMessageDelayed(obtainMessage(EVENT_RADIO_NO_SERVICE), delayedTime);
            }
        }

        protected void logdForController(String msg) {}

        protected int convertVoiceRegState(int state) {
            int ret = state;
            switch (state) {
                case ServiceState.RIL_REG_STATE_NOT_REG_EMERGENCY_CALL_ENABLED:
                    ret = ServiceState.RIL_REG_STATE_NOT_REG;
                    break;
                case ServiceState.RIL_REG_STATE_SEARCHING_EMERGENCY_CALL_ENABLED:
                    ret =  ServiceState.RIL_REG_STATE_SEARCHING;
                    break;
                case ServiceState.RIL_REG_STATE_DENIED_EMERGENCY_CALL_ENABLED:
                    ret =  ServiceState.RIL_REG_STATE_DENIED;
                    break;
                case ServiceState.RIL_REG_STATE_UNKNOWN_EMERGENCY_CALL_ENABLED:
                    ret =  ServiceState.RIL_REG_STATE_UNKNOWN;
                    break;
                default:
                    break;
            }
            return ret;
        }

        protected boolean enableNoSerivceDelay() {
            return true;
        }

        @Override
        public void startNewSearchRound() {
            logdForController("startNewSearchRound()");
            mIsFirstRoundSearch = true;
            mPreVoiceState = -1;
            mPreDataState = -1;
        }

        @Override
        public void setIfEnabled(boolean isEnabled) {}
    }

    /**
     * Network controller of LTE.
     *
     * @hide
     */
    private class LteController extends PhoneController {
        private static final int EVENT_DUAL_PHONE_AVAILABLE = 101;
        private static final int EVENT_DUAL_PHONE_POWER_ON = 102;
        private static final int EVENT_RADIO_OFF_NOT_AVAILABLE = 103;
        private static final int EVENT_GSM_PLMN_CHANGED = 104;
        private static final int EVENT_GSM_SUSPENDED = 105;
        private static final int EVENT_GSM_GET_AVAILABLE_NETWORKS_COMPLETED = 140;
        private int mModemResumeSessionId;
        private boolean mIsFindingAvailableNW = false;

        public LteController(SvlteIrController controller, PhoneBase ltePhone, int activePhoneId) {
            super(controller, ltePhone, activePhoneId);
            registerBaseListener();
            setState(STATE_INIT);
        }

        /**
         * Dispose the the LTE controller.
         */
        @Override
        public void dispose() {
            unregisterBaseListener();
            unregisterSuspendListener();
            unregisterSpecialCasesListener();
        }

        @Override
        public void setRoamingMode(RoamingMode roamingMode) {
            logdForController(" setRoamingMode: " + roamingMode);
            if (roamingMode != mIrController.getRoamingMode()) {
                mIrController.setRoaming(roamingMode, obtainMessage(EVENT_ROAMING_MODE_CHANGED));
            } else {
                mIrController.setRoaming(roamingMode, null);
            }
            setState(STATE_NETWORK_SELECTED);
        }

        @Override
        public void findAvailabeNetwork() {
            logdForController(" findAvailabeNetwork");
            mIsFindingAvailableNW = true;
            mPhone.getAvailableNetworks(obtainMessage(EVENT_GSM_GET_AVAILABLE_NETWORKS_COMPLETED));
        }

        @Override
        public void cancelAvailableNetworks() {
            logdForController(" cancelAvailableNetworks");
            if (mIsFindingAvailableNW) {
                logdForController(" really cancelAvailableNetworks");
                mPhone.cancelAvailableNetworks(null);
                mIsFindingAvailableNW = false;
            }
        }

        @Override
        public void registerNetworkManually(OperatorInfo oi) {
            logdForController(" registerNetworkManually");
            mPhone.selectNetworkManually(oi, null);
        }

        @Override
        protected void defaultMessageHandler(Message msg) {
            if (getState() == STATE_INIT &&
                (msg.what == EVENT_SERVICE_STATE_CHANGED ||
                 msg.what == EVENT_GSM_SUSPENDED ||
                 msg.what == EVENT_GSM_PLMN_CHANGED)) {
               return;
            }
            AsyncResult ar = (AsyncResult) msg.obj;
            switch (msg.what) {
                case EVENT_DUAL_PHONE_POWER_ON:
                    removeNoServiceMessage();
                    registerSpecialCasesListener();
                    if (mListener != null) {
                        mListener.onRadioStateChanged(true);
                    }
                    mPreVoiceState = -1;
                    mPreDataState = -1;
                    mIsFindingAvailableNW = false;
                    mIsFirstRoundSearch = true;
                    break;
                case EVENT_SERVICE_STATE_CHANGED:
                    ServiceState serviceState = (ServiceState) ar.result;
                    final int regState = convertVoiceRegState(serviceState.getRilVoiceRegState());
                    final int regDataState = serviceState.getRilDataRegState();
                    logdForController(" EVENT_SERVICE_STATE_CHANGED-VoiceState: " + regState
                                      + " DataState: " + regDataState
                                      + " mIsFirstRoundSearch: " + mIsFirstRoundSearch);
                    if (regState == ServiceState.RIL_REG_STATE_HOME
                        || regState == ServiceState.RIL_REG_STATE_ROAMING
                        || regDataState == ServiceState.RIL_REG_STATE_HOME
                        || regDataState == ServiceState.RIL_REG_STATE_ROAMING) {
                        setServiceType(ServiceType.IN_SERVICE);
                        mIsFirstRoundSearch = true;
                    } else if ((isPreStateBeforeNoService(mPreVoiceState)
                                || isPreStateBeforeNoService(mPreDataState))
                            && (isNoServiceState(regState)
                                    && isNoServiceState(regDataState))) {
                        setServiceType(ServiceType.OUT_OF_SERVICE);
                        mIsFirstRoundSearch = false;
                    } else if (mIsFirstRoundSearch ||
                               (mServiceType == ServiceType.OUT_OF_SERVICE
                                && hasMessages(EVENT_RADIO_NO_SERVICE))) {
                        if (mSwitchStrategy.mIsLwgRadioOn) {
                            setServiceType(ServiceType.IN_SEARCHING);
                        }
                    }

                    mPreVoiceState = regState;
                    mPreDataState = regDataState;
                    break;
                case EVENT_GSM_SUSPENDED:
                    postponeNoServiceMessageIfNeeded(sNoServiceDelayTime);
                    setState(STATE_SELECTING_NETWORK);
                    if (ar.exception == null && ar.result != null) {
                        mModemResumeSessionId = ((int[]) ar.result)[0];
                        if (mListener != null) {
                            mListener.onPlmnChanged(selectedPlmn());
                        }
                    }
                    mPlmns = null;
                    break;
                case EVENT_GSM_PLMN_CHANGED:
                    postponeNoServiceMessageIfNeeded(sNoServiceDelayTime);
                    if (ar.exception == null && ar.result != null) {
                        mPlmns = (String[]) ar.result;
                        for (int i = 0; i < mPlmns.length; i++) {
                            logdForController("EVENT_GSM_PLMN_CHANGED: i = " + i + ", mPlmns="
                                    + mPlmns[i]);
                        }
                    }
                    setState(STATE_SELECTING_NETWORK);
                    break;
                case EVENT_RADIO_OFF_NOT_AVAILABLE:
                    removeNoServiceMessage();
                    unregisterSpecialCasesListener();
                    if (!mCi.getRadioState().isAvailable()) {
                        unregisterSuspendListener();
                        setState(STATE_INIT);
                    } else {
                        setState(STATE_GETTING_PLMN);
                    }
                    mPreVoiceState = -1;
                    mPreDataState = -1;
                    mIsFirstRoundSearch = true;
                    if (mListener != null) {
                        mListener.onRadioStateChanged(false);
                    }
                    setServiceType(ServiceType.OUT_OF_SERVICE);
                    if (hasMessages(EVENT_RADIO_NO_SERVICE)) {
                        removeMessages(EVENT_RADIO_NO_SERVICE);
                        if (mListener != null) {
                            mListener.onServiceStateChanged(ServiceType.OUT_OF_SERVICE);
                        }
                    }
                    break;
                default:
                    super.defaultMessageHandler(msg);
                    break;
            }
        }

        @Override
        protected void processInitState(Message msg) {
            switch (msg.what) {
                case EVENT_DUAL_PHONE_AVAILABLE:
                    removeNoServiceMessage();
                    if (mIrController.getIfEnabled()) {
                        enableSuspend(true);
                        resetToInitialState();
                    }
                    registerSuspendListener();
                    setState(STATE_GETTING_PLMN);
                    break;
                default:
                    super.processInitState(msg);
                    break;
            }
        }

        @Override
        protected void processSelectingNWState(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            switch (msg.what) {
                case EVENT_GSM_GET_AVAILABLE_NETWORKS_COMPLETED:
                    mIsFindingAvailableNW = false;
                    if (ar.exception == null) {
                        logdForController(" no exception while getting networks");
                        List<OperatorInfo> networkInfoArray = (List<OperatorInfo>) ar.result;
                        if (networkInfoArray != null) {
                            for (int i = 0; i < networkInfoArray.size(); i++) {
                                OperatorInfo oi = networkInfoArray.get(i);
                                if (oi != null) {
                                    logdForController("available networks: i = " + i + ", plmn="
                                            + oi.getOperatorNumeric());
                                }
                            }
                        }
                        if (mListener != null) {
                            mListener.onNetworkInfoReady(networkInfoArray);
                        }
                    } else {
                        logdForController(" exception happen while getting networks");
                        if (mListener != null) {
                            mListener.onNetworkInfoReady(null);
                        }
                    }
                    break;
                default:
                    super.processSelectingNWState(msg);
                    break;
            }
        }

        @Override
        protected String msgToString(int msgWhat) {
            String msg = "[LteController]-";
            switch (msgWhat) {
                case EVENT_DUAL_PHONE_AVAILABLE:
                    msg += "EVENT_DUAL_PHONE_AVAILABLE";
                    break;
                case EVENT_DUAL_PHONE_POWER_ON:
                    msg += "EVENT_DUAL_PHONE_POWER_ON";
                    break;
                case EVENT_GSM_PLMN_CHANGED:
                    msg += "EVENT_GSM_PLMN_CHANGED";
                    break;
                case EVENT_GSM_SUSPENDED:
                    msg += "EVENT_GSM_SUSPENDED";
                    break;
                case EVENT_SERVICE_STATE_CHANGED:
                    msg += "EVENT_SERVICE_STATE_CHANGED";
                    break;
                case EVENT_RADIO_OFF_NOT_AVAILABLE:
                    msg += "EVENT_RADIO_OFF_NOT_AVAILABLE";
                    break;
                case EVENT_RADIO_NO_SERVICE:
                    msg += "EVENT_RADIO_NO_SERVICE";
                    break;
                case EVENT_GSM_GET_AVAILABLE_NETWORKS_COMPLETED:
                    msg += "EVENT_GSM_GET_AVAILABLE_NETWORKS_COMPLETED";
                    break;
                case EVENT_ROAMING_MODE_CHANGED:
                    msg += "EVENT_ROAMING_MODE_CHANGED";
                    break;
                default:
                    break;
            }
            return msg;
        }
        @Override
        public void resumeNetwork() {
            RoamingMode currentRoamingMode = mIrController.getRoamingMode();
            logdForController(" resumeNetwork: " + " currentRoamingMode: " + currentRoamingMode);
            mPhone.mCi.setResumeRegistration(mModemResumeSessionId, null);
        }

        private void registerBaseListener() {
            logdForController(" registerBaseListener");
            mCi.registerForAvailable(this, EVENT_DUAL_PHONE_AVAILABLE, null);
            mCi.registerForOn(this, EVENT_DUAL_PHONE_POWER_ON, null);
        }

        private void unregisterBaseListener() {
            logdForController(" unregisterBaseListener");
            mCi.unregisterForAvailable(this);
            mCi.unregisterForOn(this);
        }

        private void registerSuspendListener() {
            logdForController(" registerSuspendListener");
            mCi.setOnPlmnChangeNotification(this, EVENT_GSM_PLMN_CHANGED, null);
            mCi.setOnRegistrationSuspended(this, EVENT_GSM_SUSPENDED, null);
        }

        private void unregisterSuspendListener() {
            logdForController(" unregisterSuspendListener");
            mCi.unSetOnPlmnChangeNotification(this);
            mCi.unSetOnRegistrationSuspended(this);
        }

        private void enableSuspend(boolean enable) {
            logdForController(" enableSuspend: " + enable);
            int enableVal = enable ? 1 : 0;
            if (!isCtDualModeSimCard(mPhoneControllerActivePhoneId)) {
                logd("enableSuspend(), enable=" + enable + " not ct dual mode sim, return");
                return;
            }
            mCi.setRegistrationSuspendEnabled(enableVal, null);
        }

        private void registerSpecialCasesListener() {
            logdForController(" registerSpecialCasesListener");
            mPhone.registerForServiceStateChanged(this, EVENT_SERVICE_STATE_CHANGED, null);
            mCi.registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_NOT_AVAILABLE, null);
        }

        private void unregisterSpecialCasesListener() {
            logdForController(" unregisterSpecialCasesListener");
            mPhone.unregisterForServiceStateChanged(this);
            mCi.unregisterForOffOrNotAvailable(this);
        }

        private String selectedPlmn() {
            String ret = null;
            // ask Strategy to select a prefer plmn
            // otherwise, just select the first one
            if (mListener != null) {
                ret = mListener.onPreSelectPlmn(mPlmns);
            }

            if (ret == null) {
                ret = mPlmns[0];
            }
            return ret;
        }

        @Override
        public void setIfEnabled(boolean isEnabled) {
            enableSuspend(isEnabled);
        }

        /**
         * If the state prior to no service, the no service will be triggered.
         * @param state the NW service state.
         * @return If true and no service will trigger no service, otherwise not trigger.
         */
        private boolean isPreStateBeforeNoService(int state) {
            return (state == ServiceState.RIL_REG_STATE_SEARCHING
                    || state == ServiceState.RIL_REG_STATE_HOME
                    || state == ServiceState.RIL_REG_STATE_ROAMING);
        }

        private boolean isNoServiceState(int regState) {
            if (regState == ServiceState.RIL_REG_STATE_NOT_REG
                || regState == ServiceState.RIL_REG_STATE_UNKNOWN) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        protected void logdForController(String msg) {
            logd(" LteController, " + msg);
        }
    }

    /**
     * Network controller of CDMA.
     *
     * @hide
     */
    private class CdmaController extends PhoneController {
        private static final int EVENT_DUAL_PHONE_AVAILABLE = 201;
        private static final int EVENT_DUAL_PHONE_POWER_ON = 202;
        private static final int EVENT_CDMA_PLMN_CHANGED = 203;
        private static final int EVENT_RADIO_OFF_NOT_AVAILABLE = 207;
        private static final int EVENT_CDMA_CARD_TYPE_CHANGED = 208;

        private static final int EVENT_NO_SERVICE_DELAY = 221;

        private static final String PREF_IR_ROAMING_INFO = "mediatek_ir_roaming_info";
        private static final String PREF_IR_CDMA_NETWORK_TYPE = "com.mediatek.ir.cdma.network.type";

        private String[] mPlmn;

        private boolean mIsCT3GCardType = false;

        public CdmaController(SvlteIrController controller, PhoneBase nltePhone
                , int activePhoneId) {
            super(controller, nltePhone, activePhoneId);
            logdForController(" nltePhone=" + nltePhone);
            registerBaseListener();
            setState(STATE_INIT);
        }

        private void registerBaseListener() {
            logdForController(" registerBaseListener");
            mCi.registerForAvailable(this, EVENT_DUAL_PHONE_AVAILABLE, null);
            mCi.registerForOn(this, EVENT_DUAL_PHONE_POWER_ON, null);
            mCi.registerForCdmaCardType(this, EVENT_CDMA_CARD_TYPE_CHANGED, null);
        }

        private void unregisterBaseListener() {
            logdForController(" unregisterBaseListener");
            mCi.unregisterForAvailable(this);
            mCi.unregisterForOn(this);
        }

        private void enablePause(boolean enabled) {
            logdForController(" enablePause: " + enabled);
            if (!isCtDualModeSimCard(mPhoneControllerActivePhoneId)) {
                logd("enablePause(), enabled=" + enabled + " not ct dual mode sim, return");
                return;
            }
            mCi.setCdmaRegistrationSuspendEnabled(enabled, null);
        }

        private void registerPlmnChangedListener() {
            logdForController(" registerPlmnChangedListener");
            mCi.registerForMccMncChange(this, EVENT_CDMA_PLMN_CHANGED, null);
        }

        private void unregisterPlmnChangedListener() {
            logdForController(" unregisterPlmnChangedListener");
            mCi.unregisterForMccMncChange(null);
        }

        private void registerSpecialCasesListener() {
            logdForController(" registerSpecialCasesListener");
            mPhone.registerForServiceStateChanged(this, EVENT_SERVICE_STATE_CHANGED, null);
            mCi.registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_NOT_AVAILABLE, null);
        }

        private void unregisterSpecialCasesListener() {
            logdForController(" unregisterSpecialCasesListener");
            mPhone.unregisterForServiceStateChanged(this);
            mCi.unregisterForOffOrNotAvailable(this);
        }

        @Override
        public void dispose() {
            unregisterBaseListener();
            unregisterPlmnChangedListener();
            unregisterSpecialCasesListener();
        }

        @Override
        protected void defaultMessageHandler(Message msg) {
            if (getState() == STATE_INIT &&
                (msg.what == EVENT_SERVICE_STATE_CHANGED ||
                 msg.what == EVENT_CDMA_PLMN_CHANGED)) {
               return;
            }
            AsyncResult ar = (AsyncResult) msg.obj;
            switch (msg.what) {
                case EVENT_DUAL_PHONE_POWER_ON:
                    removeNoServiceMessage();
                    registerSpecialCasesListener();
                    if (mListener != null) {
                        mListener.onRadioStateChanged(true);
                    }
                    mPreVoiceState = -1;
                    mPreDataState = -1;
                    mIsFirstRoundSearch = true;
                    break;
                case EVENT_SERVICE_STATE_CHANGED:
                    ServiceState serviceState = (ServiceState) ar.result;
                    final int regState = serviceState.getRilVoiceRegState();
                    final int regDataState = serviceState.getRilDataRegState();
                    logdForController(" EVENT_SERVICE_STATE_CHANGED-VoiceState: " + regState
                                      + " DataState: " + regDataState
                                      + " mIsFirstRoundSearch" + mIsFirstRoundSearch);
                    if (regState == ServiceState.RIL_REG_STATE_HOME
                        || regState == ServiceState.RIL_REG_STATE_ROAMING
                        || regDataState == ServiceState.RIL_REG_STATE_HOME
                        || regDataState == ServiceState.RIL_REG_STATE_ROAMING) {
                        setServiceType(ServiceType.IN_SERVICE);
                        mIsFirstRoundSearch = true;
                    } else if ((regState == ServiceState.RIL_REG_STATE_NOT_REG
                                || regState == ServiceState.RIL_REG_STATE_UNKNOWN)
                               && (regDataState == ServiceState.RIL_REG_STATE_NOT_REG
                                   || regDataState == ServiceState.RIL_REG_STATE_UNKNOWN)) {
                        setServiceType(ServiceType.OUT_OF_SERVICE);
                        mIsFirstRoundSearch = false;
                    } else {
                        setServiceType(ServiceType.IN_SEARCHING);
                    }

                    mPreVoiceState = regState;
                    mPreDataState = regDataState;
                    break;
                case EVENT_CDMA_PLMN_CHANGED:
                    postponeNoServiceMessageIfNeeded(sNoServiceDelayTime);
                    if (ar.exception == null && ar.result != null) {
                        mPlmn = new String[1];
                        mPlmn[0] = (String) ar.result;
                        enableServiceStateNotify(false);
                        setState(STATE_SELECTING_NETWORK);
                        if (mListener != null) {
                            mListener.onPlmnChanged(mPlmn[0]);
                        }
                    }
                    break;
                case EVENT_RADIO_OFF_NOT_AVAILABLE:
                    removeNoServiceMessage();
                    unregisterSpecialCasesListener();
                    if (!mCi.getRadioState().isAvailable()) {
                        unregisterPlmnChangedListener();
                        setState(STATE_INIT);
                    } else {
                        setState(STATE_GETTING_PLMN);
                    }
                    mPreVoiceState = -1;
                    mPreDataState = -1;
                    mIsFirstRoundSearch = true;
                    if (mListener != null) {
                        mListener.onRadioStateChanged(false);
                    }
                    setServiceType(ServiceType.OUT_OF_SERVICE);
                    break;
                case EVENT_CDMA_CARD_TYPE_CHANGED:
                    logdForController("EVENT_CDMA_CARD_TYPE_CHANGED");
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        int[] resultType = (int[]) ar.result;
                        if (resultType != null) {
                            if (isEarlySuspendCTCard(resultType[0])) {
                                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                                        && SvlteUtils.isActiveSvlteMode(
                                                mLteDcPhoneProxy.getActivePhone().getPhoneId())) {
                                    enablePause(true);
                                    mIrController.setEnableIr(true);
                                }
                            }
                        }
                    } else {
                        logdForController("EVENT_CDMA_CARD_TYPE_CHANGED, ar.exception="
                                + ar.exception);
                    }
                    break;
                default:
                    super.defaultMessageHandler(msg);
                    break;
            }
        }

        private boolean isEarlySuspendCTCard(int cardTypeVal) {
            boolean retSuspendCTCard = false;
            IccCardConstants.CardType cardType
                    = IccCardConstants.CardType.getCardTypeFromInt(cardTypeVal);
            if (cardType == IccCardConstants.CardType.CT_3G_UIM_CARD
                    || cardType == IccCardConstants.CardType.CT_UIM_SIM_CARD) {
                retSuspendCTCard = true;
                mIsCT3GCardType = true;
            } else {
                mIsCT3GCardType = false;
            }
            logdForController("isEarlySuspendCTCard, cardType=" + cardType
                    + " retSuspendCTCard=" + retSuspendCTCard
                    + " mIsCT3GCardType=" + mIsCT3GCardType);
            return retSuspendCTCard;
        }

        public boolean isCt3gCardType() {
            return mIsCT3GCardType;
        }

        @Override
        protected void processInitState(Message msg) {
            switch (msg.what) {
                case EVENT_DUAL_PHONE_AVAILABLE:
                    removeNoServiceMessage();
                    if (mIrController.getIfEnabled()) {
                        enablePause(true);
                        resetToInitialState();
                    }
                    registerPlmnChangedListener();
                    setState(STATE_GETTING_PLMN);
                    break;
                default:
                    super.processInitState(msg);
                    break;
            }
        }

        @Override
        protected String msgToString(int msgWhat) {
            String msg = "[CdmaController]-";
            switch (msgWhat) {
                case EVENT_DUAL_PHONE_AVAILABLE:
                    msg += "EVENT_DUAL_PHONE_AVAILABLE";
                    break;
                case EVENT_DUAL_PHONE_POWER_ON:
                    msg += "EVENT_DUAL_PHONE_POWER_ON";
                    break;
                case EVENT_CDMA_PLMN_CHANGED:
                    msg += "EVENT_CDMA_PLMN_CHANGED";
                    break;
                case EVENT_SERVICE_STATE_CHANGED:
                    msg += "EVENT_SERVICE_STATE_CHANGED";
                    break;
                case EVENT_RADIO_OFF_NOT_AVAILABLE:
                    msg += "EVENT_RADIO_OFF_NOT_AVAILABLE";
                    break;
                case EVENT_RADIO_NO_SERVICE:
                    msg += "EVENT_RADIO_NO_SERVICE";
                    break;
                case EVENT_NO_SERVICE_DELAY:
                    msg += "EVENT_NO_SERVICE_DELAY";
                    break;
                case EVENT_ROAMING_MODE_CHANGED:
                    msg += "EVENT_ROAMING_MODE_CHANGED";
                    break;
                default:
                    break;
            }
            return msg;
        }

        @Override
        public void setRoamingMode(RoamingMode roamingMode) {
            logdForController(" setRoamingMode: " + roamingMode);
            if (roamingMode != mIrController.getRoamingMode()) {
                mIrController.setRoaming(roamingMode, obtainMessage(EVENT_ROAMING_MODE_CHANGED));
            } else {
                mIrController.setRoaming(roamingMode, null);
            }
            setState(STATE_NETWORK_SELECTED);
        }

        @Override
        public void resumeNetwork() {
            RoamingMode currentRoamingMode = mIrController.getRoamingMode();
            logdForController(" resumeNetwork: " + " currentRoamingMode: " + currentRoamingMode);
            enableServiceStateNotify(true);
            mPhone.mCi.setResumeCdmaRegistration(null);
        }

        private void enableServiceStateNotify(boolean enable) {
            logdForController(" enableServiceStateNotify(" + enable + ")");
            CdmaServiceStateTracker csst = (CdmaServiceStateTracker) mPhone
                    .getServiceStateTracker();
            csst.enableServiceStateNotify(enable);
        }

        @Override
        protected boolean enableNoSerivceDelay() {
            return false;
        }

        @Override
        public void setIfEnabled(boolean isEnabled) {
            enablePause(isEnabled);
        }

        @Override
        protected void logdForController(String msg) {
            logd(" CdmaController, " + msg);
        }
    }

    /**
     * ServiceType of IR.
     *
     * @hide
     */
    private enum ServiceType {
        OUT_OF_SERVICE,
        IN_SEARCHING,
        IN_SERVICE,
    }

    /**
     * Base class of network selection strategy.
     *
     * @hide
     */
    private abstract class Strategy extends Handler {
        protected boolean mIsEnabled = false;
        protected SvlteIrController mIrController;
        protected INetworkController mLteController;
        protected INetworkController mCdmaController;

        protected boolean mIsCdmaRadioOn = false;
        protected boolean mIsLwgRadioOn = false;

        public Strategy(SvlteIrController controller,
                        INetworkController lteController,
                        INetworkController cdmaController) {
            mIrController = controller;
            mLteController = lteController;
            mCdmaController = cdmaController;
        }

        public void setIfEnabled(boolean enabled) {
            mIsEnabled = enabled;
            onSetIfEnabled(enabled);
        }

        public boolean getIfEnabled() {
            return mIsEnabled;
        }

        protected void onSetIfEnabled(boolean enabled) {}
    }

    /**
     * Network selection strategy of 5M project.
     *
     * @hide
     */
    private class Strategy5M extends Strategy {
        private static final String CHINA_TELECOM_MAINLAND_MCC = "460";
        private static final String CHINA_TELECOM_MACCO_MCC = "455";
        private static final int WATCHDOG_RETRY_DELAY_STEP = 30 * 1000; // 30s
        private static final int MAX_WATCHDOG_RETRY_DELAY = 30 * 60 * 1000; // 30m

        protected RoamingMode mCdmaRoamingMode = RoamingMode.ROAMING_MODE_UNKNOWN;
        protected String mCdmaPlmnForCriticalArea;
        protected ServiceType mLteServiceState = ServiceType.OUT_OF_SERVICE;
        protected ServiceType mCdmaServiceState = ServiceType.OUT_OF_SERVICE;
        protected static final int EVENT_NO_SEERVICE_WATCHDOG = 101;
        protected static final int EVENT_RETRY_PLMN_CHANGED = 102;
        protected static final int EVENT_ROAMING_MODE_CHANGED = 103;

        protected static final int EVENT_ROAMING_MODE_CHANGED_FORALL = 104;
        protected static final int EVENT_ROAMING_MODE_CHANGED_FORLTE = 105;
        protected static final int EVENT_ROAMING_MODE_CHANGED_FORCDMA = 106;

        protected List<OperatorInfo> mFailedNetworkInfoArray = null;
        protected long mNoServiceTimeStamp = 0;
        /// M: Customize for swip via code.
        private IPlusCodeUtils mPlusCodeUtils = ViaPolicyManager.getPlusCodeUtils();
        private long mWatchdogStartTime = 0;
        protected String mPlmnToSelectNetworkManually = null;
        private int mContinousRetryCount = 0;
        private SvlteRatModeChangedListener mRatModeChangedListener
                                                = new SvlteRatModeChangedListener() {
            @Override
            public void onSvlteRatModeChangeStarted(SvlteRatMode curMode, SvlteRatMode newMode) {
                logdForStrategy("onSvlteRatModeChangeStarted() curMode = " + curMode +
                                                              " newMode" + newMode);
            }

            @Override
            public void onSvlteEctModeChangeDone(SvlteRatMode curMode, SvlteRatMode newMode) {
                // do nothing in current design.
            }

            @Override
            public void onSvlteRatModeChangeDone(SvlteRatMode preMode, SvlteRatMode curMode) {
                logdForStrategy("onSvlteRatModeChangeDone() preMode = " + preMode +
                                                              " curMode" + curMode);
                if (getIfEnabled() &&
                    !isDualRadioOff() &&
                    isDualServiceNotInService() &&
                    preMode == SvlteRatMode.SVLTE_RAT_MODE_4G_DATA_ONLY &&
                    curMode != SvlteRatMode.SVLTE_RAT_MODE_4G_DATA_ONLY &&
                    mIrController.getRoamingMode() != RoamingMode.ROAMING_MODE_HOME) {
                    // if turned off TDD DATA only mode, always search from Home mode
                    logdForStrategy("force to switch to Home mode");
                    switchForNoService(true);
                    updateWatchdog();
                }
            }

            @Override
            public void onRoamingModeChange(RoamingMode preMode, RoamingMode curMode) {
                mRoamingMode = curMode;
            }
        };

        public Strategy5M(SvlteIrController controller,
                          INetworkController lteController,
                          INetworkController cdmaController) {
            super(controller, lteController, cdmaController);
            // for debug to adjust watchdog delay time
            sWatchdogDelayTime = SystemProperties.getInt("persist.sys.ct.ir.wd",
                                                          NO_SEERVICE_WATCHDOG_DELAY_TIME);

            mLteController.registerListener(mLteListener);
            mCdmaController.registerListener(mCdmaListener);
            controller.mLteDcPhoneProxy.getSvlteRatController().
                registerSvlteRatModeChangedListener(mRatModeChangedListener);

            mFailedNetworkInfoArray = new ArrayList<OperatorInfo>();
        }

        @Override
        public void handleMessage(Message msg) {
            logdForStrategy("handleMessage, msg=" + msg);
            switch (msg.what) {
                case EVENT_NO_SEERVICE_WATCHDOG:
                    triggerNoServiceWatchdog();
                    break;
                case EVENT_RETRY_PLMN_CHANGED:
                    retryLwgPlmnChanged();
                    break;
                case EVENT_ROAMING_MODE_CHANGED:
                    if (msg.arg1 == EVENT_ROAMING_MODE_CHANGED_FORALL) {
                        mLteController.startNewSearchRound();
                        mCdmaController.startNewSearchRound();
                    } else if (msg.arg1 == EVENT_ROAMING_MODE_CHANGED_FORCDMA) {
                        mCdmaController.startNewSearchRound();
                    } else if (msg.arg1 == EVENT_ROAMING_MODE_CHANGED_FORLTE) {
                        mLteController.startNewSearchRound();
                    }
                    break;
                default:
                    break;
            }
        }

        private RoamingMode getRoamingModeByPlmn5M(String plmn) {
            if (plmn != null) {
                // For 5m project
                if (plmn.startsWith(CHINA_TELECOM_MAINLAND_MCC)
                    || plmn.startsWith(CHINA_TELECOM_MACCO_MCC)) {
                    logdForStrategy("getRoamingModeByPlmn5M, plmn=" + plmn + " ret: " +
                                     RoamingMode.ROAMING_MODE_HOME);
                    return RoamingMode.ROAMING_MODE_HOME;
                } else {
                    logdForStrategy("getRoamingModeByPlmn5M, plmn=" + plmn + " ret: " +
                                     RoamingMode.ROAMING_MODE_NORMAL_ROAMING);
                    return RoamingMode.ROAMING_MODE_NORMAL_ROAMING;
                }
            } else {
                logdForStrategy("getRoamingModeByPlmn5M, plmn=" + plmn + " ret: " +
                                     RoamingMode.ROAMING_MODE_NORMAL_ROAMING);
                return RoamingMode.ROAMING_MODE_NORMAL_ROAMING;
            }
        }

        private boolean isDualServiceNotInService() {
            boolean ret = (mCdmaServiceState != ServiceType.IN_SERVICE)
                    && (mLteServiceState != ServiceType.IN_SERVICE);
            logdForStrategy("isDualServiceNotInService() :" + ret);
            return ret;
        }
        private boolean isDualRadioOff() {
            boolean ret = !mIsLwgRadioOn && !mIsCdmaRadioOn;
            logdForStrategy("isDualRadioOff() :" + ret);
            return ret;
        }
        protected RoamingMode getRoamingModeByPlmnCdma(String plmn) {
            return getRoamingModeByPlmn5M(plmn);
        }

        protected RoamingMode getRoamingModeByPlmnLwg(String plmn) {
            return getRoamingModeByPlmn5M(plmn);
        }

        protected boolean supportRoaming() {
            logd(" supportRoaming, slotId=" + mActivePhoneId
                    + " mLteDcPhoneProxy.getIccCard().getState()="
                    + mLteDcPhoneProxy.getIccCard().getState());
            boolean bSupportRoaming = isCtDualModeSimCard(mActivePhoneId);
            logdForStrategy("supportRoaming, slotId=" + mActivePhoneId
                    + " bSupportRoaming=" + bSupportRoaming);
            return bSupportRoaming;
        }

        protected boolean isSimReady() {
            logd(" isSimReady, slotId = " + mActivePhoneId
                    + " mLteDcPhoneProxy.getIccCard().getState()="
                    + mLteDcPhoneProxy.getIccCard().getState());
            boolean bSimReady =
                    mLteDcPhoneProxy.getIccCard().getState() == IccCardConstants.State.READY;
            logdForStrategy("isSimReady, slotId=" + mActivePhoneId + " bSimReady=" + bSimReady);
            return bSimReady;
        }

        protected boolean switchForNoService(boolean forceSwitch) {
            logdForStrategy("switchForNoService mLteServiceState: " + mLteServiceState
                            + " mCdmaServiceState: " + mCdmaServiceState);
            boolean reallySwitchForNoService = false;
            if (!isDualRadioOff() && supportRoaming() && isSimReady()) {
                long curTime = System.currentTimeMillis();
                long duration = curTime - mNoServiceTimeStamp;

                stopNoServiceWatchdog();

                // prevent no service switch happens too
                // frequently, use a time stamp
                if (mNoServiceTimeStamp == 0 ||
                    duration > sWatchdogDelayTime ||
                    forceSwitch) {
                    logdForStrategy("switchForNoService realy siwtched");

                    cancelToRetryLwgPlmnChanged();
                    mLteController.cancelAvailableNetworks();

                    Message msg = obtainMessage(EVENT_ROAMING_MODE_CHANGED);
                    msg.arg1 = EVENT_ROAMING_MODE_CHANGED_FORALL;

                    if (mIrController.getRoamingMode() == RoamingMode.ROAMING_MODE_HOME) {
                        mIrController.setRoaming(RoamingMode.ROAMING_MODE_NORMAL_ROAMING, msg);
                    } else {
                        mIrController.setRoaming(RoamingMode.ROAMING_MODE_HOME, msg);
                    }
                    mNoServiceTimeStamp = curTime;

                    mCdmaRoamingMode = RoamingMode.ROAMING_MODE_UNKNOWN; // clear cdma record
                    mCdmaPlmnForCriticalArea = null;
                    reallySwitchForNoService = true;
                } else {
                    logdForStrategy("switchForNoService delay switch");
                    sendMessageDelayed(obtainMessage(EVENT_NO_SEERVICE_WATCHDOG),
                                                 sWatchdogDelayTime - duration);
                }
            }
            logdForStrategy("switchForNoService reallySwitchForNoService="
                    + reallySwitchForNoService);
            return reallySwitchForNoService;
        }

        protected void updateWatchdog() {
            if (mCdmaServiceState == ServiceType.IN_SERVICE ||
                mLteServiceState == ServiceType.IN_SERVICE ||
                isDualRadioOff()) {
                mContinousRetryCount = 0;
            }

            if (!isDualRadioOff() && isDualServiceNotInService() && getIfEnabled()) {
                startNoServiceWatchdog();
            } else {
                stopNoServiceWatchdog();
            }
        }

        protected void onRadioStateChanged() {
            RoamingMode roamingMode = mIrController.getRoamingMode();

            logdForStrategy("onRadioStateChanged mIsLwgRadioOn: " + mIsLwgRadioOn
                            + " mIsCdmaRadioOn: " + mIsCdmaRadioOn
                            + " roamingMode:" + roamingMode);
            if (isDualRadioOff()) {
                mCdmaRoamingMode = RoamingMode.ROAMING_MODE_UNKNOWN; // clear cdma record
                mCdmaPlmnForCriticalArea = null;
            }

            updateWatchdog();
        }

        protected void triggerNoServiceWatchdog() {
            logdForStrategy("triggerNoServiceWatchdog mLteServiceState: " + mLteServiceState
                            + " mCdmaServiceState: " + mCdmaServiceState);
            if (isDualServiceNotInService()) {
                switchForNoService(false);
            }
            updateWatchdog();
        }

        protected void startNoServiceWatchdog() {
            logdForStrategy("startNoServiceWatchdog");
            if (!hasMessages(EVENT_NO_SEERVICE_WATCHDOG)) {
                sWatchdogDelayTime = SystemProperties.getInt("persist.sys.ct.ir.wd",
                                                          NO_SEERVICE_WATCHDOG_DELAY_TIME);
                sWatchdogDelayTime += (mContinousRetryCount * WATCHDOG_RETRY_DELAY_STEP);
                if (sWatchdogDelayTime > MAX_WATCHDOG_RETRY_DELAY) {
                    sWatchdogDelayTime = MAX_WATCHDOG_RETRY_DELAY;
                }
                mWatchdogStartTime = System.currentTimeMillis();
                logdForStrategy("really start watchdog sWatchdogDelayTime = " + sWatchdogDelayTime
                                + " mContinousRetryCount = " + mContinousRetryCount);
                sendMessageDelayed(obtainMessage(EVENT_NO_SEERVICE_WATCHDOG),
                                                 sWatchdogDelayTime);
                mContinousRetryCount++;
            }
        }

        protected void stopNoServiceWatchdog() {
            logdForStrategy("stopNoServiceWatchdog");
            removeMessages(EVENT_NO_SEERVICE_WATCHDOG);
            mWatchdogStartTime = 0;
        }

        protected void postponeNoServiceWatchdogIfNeeded() {
            logdForStrategy("postponeNoServiceWatchdogIfNeeded");
            if (hasMessages(EVENT_NO_SEERVICE_WATCHDOG)) {
                removeMessages(EVENT_NO_SEERVICE_WATCHDOG);
                long remainingTime = sWatchdogDelayTime -
                                     (System.currentTimeMillis() - mWatchdogStartTime);
                if (remainingTime < 0) {
                    remainingTime = 0;
                }
                int newDelay = (int) remainingTime + sSwitchModeOrResumeDelayTime;
                logdForStrategy("remainingTime = " + remainingTime + " newDelay = " + newDelay);
                sendMessageDelayed(obtainMessage(EVENT_NO_SEERVICE_WATCHDOG),
                                                 newDelay);
            }
        }

        protected void restartNoSerivceWatchdogIfNeeded() {
            logdForStrategy("restartNoSerivceWatchdogIfNeeded");
            if (hasMessages(EVENT_NO_SEERVICE_WATCHDOG)) {
                stopNoServiceWatchdog();
                if (mContinousRetryCount > 0) {
                    mContinousRetryCount--; // restarting need revert count
                }
                startNoServiceWatchdog();
            }
        }

        protected boolean isInCriticalAreaSameWithCdmaRoamingMode(OperatorInfo oi) {
            logdForStrategy("[LTE]isSameWithCdmaRatModeAndArea, oi=" + oi.toString()
                    + " mCdmaPlmnForCriticalArea=" + mCdmaPlmnForCriticalArea
                    + " mCdmaRoamingMode=" + mCdmaRoamingMode);
            if (oi.getState() != OperatorInfo.State.FORBIDDEN) {
                if (mCdmaPlmnForCriticalArea != null
                        && mCdmaPlmnForCriticalArea.startsWith(CHINA_TELECOM_MACCO_MCC)
                        && oi.getOperatorNumeric().startsWith(CHINA_TELECOM_MACCO_MCC)) {
                    //When CDMA is in Macao and LWG is MainLand network, use manual network
                    //selection to try to select Macao LWG network.
                    return true;
                } else if (mCdmaRoamingMode == getRoamingModeByPlmnLwg(oi.getOperatorNumeric())) {
                    return true;
                } else if (mCdmaRoamingMode == RoamingMode.ROAMING_MODE_UNKNOWN
                        && mIrController.getRoamingMode() == getRoamingModeByPlmnLwg(
                                oi.getOperatorNumeric())) {
                    // When Cdma is roaming but cleaned, LWG uses the roaming mode.
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        protected void tryNetworkManually(List<OperatorInfo> networkInfoArray) {

            boolean matched = false;

            if (networkInfoArray != null) {
                final int count = networkInfoArray.size();

                for (int i = 0; i < count; i++) {
                    OperatorInfo oi = networkInfoArray.get(i);
                    if (isInCriticalAreaSameWithCdmaRoamingMode(oi) && !isFailedNetwork(oi)) {
                        logdForStrategy("[LTE]registerNetworkManually: " + oi.toString());
                        mLteController.registerNetworkManually(oi);
                        mLteController.resumeNetwork();
                        mFailedNetworkInfoArray.add(oi);
                        matched = true;
                        break;
                    }
                }
            }

            if (!matched && mPlmnToSelectNetworkManually != null) {
                waitForRetryLwgPlmnChanged();
            }
        }

        private void retryLwgPlmnChanged() {
            if (mPlmnToSelectNetworkManually != null) {
                logdForStrategy("[LTE]retry check plmn: " + mPlmnToSelectNetworkManually);
                onLwgPlmnChanged(mPlmnToSelectNetworkManually);
            }
        }

        private void waitForRetryLwgPlmnChanged() {
            logdForStrategy("[LTE]waitForRetryLwgPlmnChanged");

            if (!hasMessages(EVENT_RETRY_PLMN_CHANGED)) {
                logdForStrategy("really wait for retry");
                sendMessageDelayed(obtainMessage(EVENT_RETRY_PLMN_CHANGED),
                                                 sFindNetworkDelayTime);
            }
        }

        private void cancelToRetryLwgPlmnChanged() {
            logdForStrategy("cancelToRetryLwgPlmnChanged");
            mFailedNetworkInfoArray.clear();
            removeMessages(EVENT_RETRY_PLMN_CHANGED);
        }

        private boolean isFailedNetwork(OperatorInfo oi) {
            final int count = mFailedNetworkInfoArray.size();
            boolean isFailed = false;
            for (int i = 0; i < count; i++) {
                OperatorInfo foi = mFailedNetworkInfoArray.get(i);
                String foal = foi.getOperatorAlphaLong() != null ? foi.getOperatorAlphaLong() : "";
                String foas = foi.getOperatorAlphaShort() != null ?
                                  foi.getOperatorAlphaShort() : "";
                String fon = foi.getOperatorNumeric() != null ? foi.getOperatorNumeric() : "";

                String oal = oi.getOperatorAlphaLong() != null ? oi.getOperatorAlphaLong() : "";
                String oas = oi.getOperatorAlphaShort() != null ? oi.getOperatorAlphaShort() : "";
                String on = oi.getOperatorNumeric() != null ? oi.getOperatorNumeric() : "";

                if (foal.equals(oal) && foas.equals(oas) && fon.equals(on)) {
                    logdForStrategy("found failed op: " + foal + " " + foas + " " + fon);
                    isFailed = true;
                    break;
                }
            }
            return isFailed;
        }

        private void logdForStrategy(String msg) {
            logd(" [Strategy5M], " + msg);
        }

        protected void onLwgPlmnChanged(String plmn) {
            logdForStrategy("onLwgPlmnChanged plmn: " + plmn);

            if (supportRoaming()) {
                RoamingMode targetMode = getRoamingModeByPlmnLwg(plmn);
                boolean registerManually = false;

                logdForStrategy("onLwgPlmnChanged mCdmaRoamingMode: " + mCdmaRoamingMode +
                                " targetMode" + targetMode);

                // these are OP09 IR 5M stratrgy deails
                // cdma roaming mode has higher priority
                if (mCdmaRoamingMode == RoamingMode.ROAMING_MODE_UNKNOWN) {
                    // keep initial values
                } else if (mCdmaRoamingMode == RoamingMode.ROAMING_MODE_HOME) {
                    if (targetMode == RoamingMode.ROAMING_MODE_NORMAL_ROAMING
                            || (mCdmaPlmnForCriticalArea.startsWith(CHINA_TELECOM_MACCO_MCC)
                                    && !plmn.startsWith(CHINA_TELECOM_MACCO_MCC))) {
                        targetMode = RoamingMode.ROAMING_MODE_HOME;
                        registerManually = true;
                    }
                } else if (mCdmaRoamingMode == RoamingMode.ROAMING_MODE_NORMAL_ROAMING) {
                    if (targetMode == RoamingMode.ROAMING_MODE_HOME) {
                        targetMode = RoamingMode.ROAMING_MODE_NORMAL_ROAMING;
                        registerManually = true;
                    }
                }
                if (targetMode == mIrController.getRoamingMode() &&
                    !registerManually) {
                    mLteController.resumeNetwork();
                }
                if (targetMode != mIrController.getRoamingMode()) {
                    mLteController.cancelAvailableNetworks();
                }
                mLteController.setRoamingMode(targetMode);

                if (registerManually) {
                    // cdma set mode as higher priority, so lwg need to find
                    // and select a similar roaming mode network of cdma manually
                    mPlmnToSelectNetworkManually = plmn;
                    mLteController.findAvailabeNetwork();
                }
            } else {
                mLteController.resumeNetwork();
                mLteController.setRoamingMode(RoamingMode.ROAMING_MODE_HOME);
            }
        }

        protected void onCdmaPlmnChanged(String plmn) {

            // record cdma latest roaming mode for LWG
            // to decide its roaming status
            mCdmaRoamingMode = getRoamingModeByPlmnCdma(plmn);
            mCdmaPlmnForCriticalArea = plmn;

            if (supportRoaming()) {
                // these are OP09 IR 5M stratrgy deails
                // cdma roaming mode has higher priority
                if (mCdmaRoamingMode == mIrController.getRoamingMode()
                    && mCdmaRoamingMode != RoamingMode.ROAMING_MODE_NORMAL_ROAMING) {
                    mCdmaController.resumeNetwork();
                }
                if (mCdmaRoamingMode != mIrController.getRoamingMode()) {
                    mLteController.cancelAvailableNetworks();
                }
                mCdmaController.setRoamingMode(mCdmaRoamingMode);
            } else if (SvlteUiccUtils.getInstance().isCt3gDualMode(mActivePhoneId)
                    && mCdmaRoamingMode == RoamingMode.ROAMING_MODE_NORMAL_ROAMING) {
                // Ct 3g uim card (not dual mode) + roaming = do not resume.
                return;
            } else {
                mCdmaController.resumeNetwork();
                mCdmaController.setRoamingMode(RoamingMode.ROAMING_MODE_HOME);
            }
        }

        @Override
        protected void onSetIfEnabled(boolean enabled) {
            updateWatchdog();
        }

        private INetworkControllerListener mLteListener = new INetworkControllerListener() {
            @Override
            public void onRadioStateChanged(boolean isRadioOn) {
                if (mIsLwgRadioOn != isRadioOn) {
                    logdForStrategy("[LTE]onRadioStateChanged :" + isRadioOn);
                    mIsLwgRadioOn = isRadioOn;
                    Strategy5M.this.onRadioStateChanged();
                    if (!isRadioOn) {
                        cancelToRetryLwgPlmnChanged();
                    }
                }
            }

            @Override
            public String onPreSelectPlmn(String[] plmnList) {
                for (int i = 0; i < plmnList.length; i++) {
                    // need to get a same mcc with cdma selected
                    // so use getRoamingModeByPlmnCdma() to get mapped roaming mode
                    if (mCdmaRoamingMode == getRoamingModeByPlmnCdma(plmnList[i])) {
                        return plmnList[i];
                    }
                }
                return plmnList[0];
            }

            @Override
            public void onPlmnChanged(String plmn) {
                logdForStrategy("[LTE]onPlmnChanged :" + plmn);
                RoamingMode oldMode = mIrController.getRoamingMode();

                onLwgPlmnChanged(plmn);

                if (oldMode != mIrController.getRoamingMode()) {
                    restartNoSerivceWatchdogIfNeeded();
                } else {
                    postponeNoServiceWatchdogIfNeeded();
                }
            }

            @Override
            public void onNetworkInfoReady(List<OperatorInfo> networkInfoArray) {
                logdForStrategy("[LTE]onNetworkInfoReady");
                tryNetworkManually(networkInfoArray);
            }

            @Override
            public void onServiceStateChanged(ServiceType serviceType) {
                logdForStrategy("[LTE]onServiceStateChanged(" + serviceType + ")");
                if (serviceType != mLteServiceState) {
                    mLteServiceState = serviceType;
                    if (serviceType == ServiceType.OUT_OF_SERVICE &&
                        mCdmaServiceState == ServiceType.OUT_OF_SERVICE) {
                        if (!switchForNoService(true)) {
                            Message msg = obtainMessage(EVENT_ROAMING_MODE_CHANGED);
                            msg.arg1 = EVENT_ROAMING_MODE_CHANGED_FORLTE;
                            msg.sendToTarget();
                        }
                    }
                    if (serviceType == ServiceType.IN_SERVICE) {
                        cancelToRetryLwgPlmnChanged();
                    }
                    updateWatchdog();
                }
            }

            @Override
            public void onRoamingModeSwitchDone() {
                mLteController.startNewSearchRound();
                mCdmaController.startNewSearchRound();
            }
        };

        private INetworkControllerListener mCdmaListener = new INetworkControllerListener() {

            @Override
            public void onRadioStateChanged(boolean isRadioOn) {
                if (mIsCdmaRadioOn != isRadioOn) {
                    logdForStrategy("[CDMA]onRadioStateChanged :" + isRadioOn);
                    mIsCdmaRadioOn = isRadioOn;
                    Strategy5M.this.onRadioStateChanged();
                }
            }

            @Override
            public String onPreSelectPlmn(String[] plmnList) {
                return plmnList[0];
            }

            @Override
            public void onPlmnChanged(String plmn) {
                logdForStrategy("[CDMA]onPlmnChanged :" + plmn);
                RoamingMode oldMode = mIrController.getRoamingMode();

                plmn = convertInvalidMccBySidNid(plmn);
                onCdmaPlmnChanged(plmn);

                if (oldMode != mIrController.getRoamingMode()) {
                    restartNoSerivceWatchdogIfNeeded();
                } else {
                    postponeNoServiceWatchdogIfNeeded();
                }
            }

            private String convertInvalidMccBySidNid(String plmn) {
                logdForStrategy("[CDMA] convertInvalidMccBySidNid, plmn=" + plmn);
                String convertCdmaPlmn = plmn;
                if ((plmn.startsWith("2134") && plmn.length() == 7)
                        || plmn.startsWith("0000")) {
                    // Re-get plmn for special operator which doesn't release plmn when
                    // network searched.
                    convertCdmaPlmn = mPlusCodeUtils.checkMccBySidLtmOff(plmn);
                    logdForStrategy("[CDMA] convertInvalidMccBySidNid, convertCdmaPlmn = "
                            + convertCdmaPlmn);
                }
                return convertCdmaPlmn;
            }

            @Override
            public void onNetworkInfoReady(List<OperatorInfo> networkInfoArray) {
                logdForStrategy("[CDMA]onNetworkInfoReady");
            }

            @Override
            public void onServiceStateChanged(ServiceType serviceType) {
                logdForStrategy("[CDMA]onServiceStateChanged(" + serviceType + ")");
                if (serviceType != mCdmaServiceState) {
                    mCdmaServiceState = serviceType;
                    if (serviceType == ServiceType.OUT_OF_SERVICE) {
                        if (mLteServiceState == ServiceType.OUT_OF_SERVICE) {
                            if (!switchForNoService(true)) {
                                Message msg = obtainMessage(EVENT_ROAMING_MODE_CHANGED);
                                msg.arg1 = EVENT_ROAMING_MODE_CHANGED_FORCDMA;
                                msg.sendToTarget();
                            }
                        }
                        // reset cdma roaming mode as it's in no service state
                        mCdmaRoamingMode = RoamingMode.ROAMING_MODE_UNKNOWN;
                        mCdmaPlmnForCriticalArea = null;
                    }
                    updateWatchdog();
                }
            }

            @Override
            public void onRoamingModeSwitchDone() {
                mLteController.startNewSearchRound();
                mCdmaController.startNewSearchRound();
            }
        };
    }

    private void logd(String msg) {
        Rlog.d("[IRC" + mActivePhoneId + "]", msg);
    }

    /**
     * Network selection strategy of 4M project.
     *
     * @hide
     */
    private class Strategy4M extends Strategy5M {
        private static final String JAP_MCC = "440";
        private static final String KOR_MCC = "450";

        public Strategy4M(SvlteIrController controller,
                          INetworkController lteController,
                          INetworkController cdmaController) {
            super(controller, lteController, cdmaController);
        }

        @Override
        protected RoamingMode getRoamingModeByPlmnCdma(String plmn) {
            if (plmn != null) {
                if (plmn.startsWith(JAP_MCC) || plmn.startsWith(KOR_MCC)) {
                    logdForStrategy("getRoamingModeByPlmnCdma, plmn=" + plmn + " ret: " +
                                     RoamingMode.ROAMING_MODE_JPKR_CDMA);
                    return RoamingMode.ROAMING_MODE_JPKR_CDMA;
                }
            }
            return super.getRoamingModeByPlmnCdma(plmn);
        }

        @Override
        protected RoamingMode getRoamingModeByPlmnLwg(String plmn) {
            if (plmn != null) {
                if (plmn.startsWith(JAP_MCC) || plmn.startsWith(KOR_MCC)) {
                    logdForStrategy("getRoamingModeByPlmnLwg, plmn=" + plmn + " ret: " +
                                     RoamingMode.ROAMING_MODE_JPKR_CDMA);
                    return RoamingMode.ROAMING_MODE_JPKR_CDMA;
                }
            }
            return super.getRoamingModeByPlmnLwg(plmn);
        }

        @Override
        protected void onLwgPlmnChanged(String plmn) {
            logdForStrategy("onLwgPlmnChanged plmn: " + plmn);
            if (supportRoaming()) {
                RoamingMode targetMode = getRoamingModeByPlmnLwg(plmn);
                logdForStrategy("onLwgPlmnChanged mCdmaRoamingMode: " + mCdmaRoamingMode +
                                " targetMode" + targetMode);
                if (mCdmaRoamingMode == RoamingMode.ROAMING_MODE_JPKR_CDMA ||
                    targetMode == RoamingMode.ROAMING_MODE_JPKR_CDMA) {

                    if (mCdmaRoamingMode == RoamingMode.ROAMING_MODE_NORMAL_ROAMING ||
                        mCdmaRoamingMode == RoamingMode.ROAMING_MODE_HOME) {
                        mLteController.setRoamingMode(mCdmaRoamingMode);
                        mPlmnToSelectNetworkManually = plmn;
                        mLteController.findAvailabeNetwork();
                    } else if (mCdmaRoamingMode == RoamingMode.ROAMING_MODE_JPKR_CDMA) {
                        mLteController.setRoamingMode(mCdmaRoamingMode);
                    } else if (mCdmaRoamingMode == RoamingMode.ROAMING_MODE_UNKNOWN) {
                        mLteController.setRoamingMode(RoamingMode.ROAMING_MODE_JPKR_CDMA);
                    }
                    return;
                }
            }

            // Strategy4M handle ROAMING_MODE_JPKR_CDMA related logic,
            // other mode related cases, back to super to handle
            super.onLwgPlmnChanged(plmn);
        }

        @Override
        protected void onCdmaPlmnChanged(String plmn) {

            // record cdma latest roaming mode for LWG
            // to decide its roaming status
            mCdmaRoamingMode = getRoamingModeByPlmnCdma(plmn);
            mCdmaPlmnForCriticalArea = plmn;

            if (supportRoaming()) {
                // these are OP09 IR 5M stratrgy deails
                // cdma roaming mode has higher priority
                boolean needForceResume = (mCdmaRoamingMode == RoamingMode.ROAMING_MODE_HOME
                            && mIrController.getRoamingMode()
                            == RoamingMode.ROAMING_MODE_JPKR_CDMA)
                            || (mCdmaRoamingMode == RoamingMode.ROAMING_MODE_JPKR_CDMA
                            && mIrController.getRoamingMode() == RoamingMode.ROAMING_MODE_HOME);
                if (mCdmaRoamingMode == mIrController.getRoamingMode() || needForceResume) {
                    if (mCdmaRoamingMode != RoamingMode.ROAMING_MODE_NORMAL_ROAMING) {
                        mCdmaController.resumeNetwork();
                    }
                }
                if (mCdmaRoamingMode != mIrController.getRoamingMode()) {
                    mLteController.cancelAvailableNetworks();
                }
                mCdmaController.setRoamingMode(mCdmaRoamingMode);
            } else if (SvlteUiccUtils.getInstance().isCt3gDualMode(mActivePhoneId)
                    && mCdmaRoamingMode == RoamingMode.ROAMING_MODE_NORMAL_ROAMING) {
                // Ct 3g uim card (not dual mode) + roaming(not JPKR) = do not resume.
                return;
            } else {
                mCdmaController.resumeNetwork();
                mCdmaController.setRoamingMode(RoamingMode.ROAMING_MODE_HOME);
            }
        }

        private void logdForStrategy(String msg) {
            logd(" [Strategy4M], " + msg);
        }
    }

    /**
     * Network selection strategy of OM project.
     *
     * @hide
     */
    private class StrategyOM extends Strategy5M {
        public StrategyOM(SvlteIrController controller,
                          INetworkController lteController,
                          INetworkController cdmaController) {
            super(controller, lteController, cdmaController);
        }

        @Override
        protected void onLwgPlmnChanged(String plmn) {
            logdForStrategy("onLwgPlmnChanged plmn: " + plmn);
            mLteController.resumeNetwork();
        }

        @Override
        protected void onCdmaPlmnChanged(String plmn) {
            logdForStrategy("onCdmaPlmnChanged plmn: " + plmn);
            mCdmaController.resumeNetwork();
        }

        private void logdForStrategy(String msg) {
            logd(" [StrategyOM], " + msg);
        }
    }
}
