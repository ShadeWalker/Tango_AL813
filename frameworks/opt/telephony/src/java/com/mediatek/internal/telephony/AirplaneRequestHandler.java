package com.mediatek.internal.telephony;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.telephony.Rlog;

import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneProxy;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteModeController;
import com.mediatek.internal.telephony.ltedc.svlte.SvltePhoneProxy;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteRatController;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * M: The Airplane mode change request handler.
 */
public class AirplaneRequestHandler extends Handler {
    private static final String LOG_TAG = "AirplaneRequestHandler";
    private Context mContext;
    private Boolean mPendingAirplaneModeRequest;
    private int mPhoneCount;
    private boolean mNeedIgnoreMessageForChangeDone;
    private boolean mForceSwitch;
    private static final int EVENT_LTE_RADIO_CHANGE_FOR_OFF = 100;
    private static final int EVENT_CDMA_RADIO_CHANGE_FOR_OFF = 101;
    private static final int EVENT_GSM_RADIO_CHANGE_FOR_OFF = 102;
    private static final int EVENT_LTE_RADIO_CHANGE_FOR_AVALIABLE = 103;
    private static final int EVENT_CDMA_RADIO_CHANGE_FOR_AVALIABLE = 104;
    private static final int EVENT_GSM_RADIO_CHANGE_FOR_AVALIABLE = 105;
    private static final String INTENT_ACTION_AIRPLANE_CHANGE_DONE =
            "com.mediatek.intent.action.AIRPLANE_CHANGE_DONE";
    private static final String EXTRA_AIRPLANE_MODE = "airplaneMode";

    private static final int EVENT_POWER_ON_OUT_TIME = 106;
    private static final int EVENT_POWER_OFF_OUT_TIME = 107;
    private static final int EVENT_SET_DESIRED_POWERSTATE = 108;
    private static final int POWER_ON_OUT_TIME_FOR_MODEM = 50 * 1000;
    private static final int POWER_ON_OUT_TIME_FOR_RADIO = 25 * 1000;
    private static final int POWER_OFF_OUT_TIME_FOR_MODEM = 30 * 1000;
    private static final int POWER_OFF_OUT_TIME_FOR_RADIO = 15 * 1000;
    private static final int SET_DESIRED_POWERSTATE_DELAY_TIME = 3 * 1000;

    private boolean mNeedIgnoreMessageForWait;
    private static final int EVENT_WAIT_LTE_RADIO_CHANGE_FOR_AVALIABLE = 200;
    private static final int EVENT_WAIT_CDMA_RADIO_CHANGE_FOR_AVALIABLE = 201;
    private static final int EVENT_WAIT_GSM_RADIO_CHANGE_FOR_AVALIABLE = 202;

    private static AtomicBoolean mInSwitching = new AtomicBoolean(false);

    private DesiredPowerState[] mDesiredPowerStates;
    private boolean mPower;
    private boolean mIsPowerForModem;
    private PowerOutTimeMessageObj mMessageObj;

    protected boolean allowSwitching() {
        if (mInSwitching.get() && !mForceSwitch) {
            return false;
        }
        return true;
    }

    protected void pendingAirplaneModeRequest(boolean enabled){
        log("pendingAirplaneModeRequest, enabled = " + enabled);
        mPendingAirplaneModeRequest = new Boolean(enabled);
    }

    /**
     * Construct a new AirplaneRequestHandler instance.
     *
     * @param context A Context object.
     * @param phoneCount the phone count.
     */
    public AirplaneRequestHandler(Context context, int phoneCount) {
        mContext = context;
        mPhoneCount = phoneCount;
        mDesiredPowerStates = new DesiredPowerState[phoneCount];
    }

    protected void monitorAirplaneChangeDone() {
        mNeedIgnoreMessageForChangeDone = false;
        log("monitorAirplaneChangeDone, power=" + mPower + ",isPowerForModem=" + mIsPowerForModem
                + ",mNeedIgnoreMessageForChangeDone=" + mNeedIgnoreMessageForChangeDone);
        int phoneId = 0;
        for (int i = 0; i < mPhoneCount; i++) {
            phoneId = i;
            if (mPower) {
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                    if (phoneId == SvlteModeController.getActiveSvlteModeSlotId()) {
                        ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getLtePhone().mCi.registerForRadioStateChanged(this,
                                EVENT_LTE_RADIO_CHANGE_FOR_AVALIABLE, null);
                        ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getNLtePhone().mCi.registerForRadioStateChanged(this,
                                EVENT_CDMA_RADIO_CHANGE_FOR_AVALIABLE, null);
                    } else {
                        ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getLtePhone().mCi.registerForRadioStateChanged(this,
                                EVENT_GSM_RADIO_CHANGE_FOR_AVALIABLE, null);
                    }
                } else {
                    ((PhoneBase) (((PhoneProxy) (PhoneFactory.getPhone(phoneId))).getActivePhone())).mCi
                            .registerForRadioStateChanged(this, EVENT_GSM_RADIO_CHANGE_FOR_AVALIABLE
                            , null);
                }
            } else {
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                    if (phoneId == SvlteModeController.getActiveSvlteModeSlotId()) {
                        ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getLtePhone().mCi.registerForRadioStateChanged(this,
                                EVENT_LTE_RADIO_CHANGE_FOR_OFF, null);
                        ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getNLtePhone().mCi.registerForRadioStateChanged(this,
                                EVENT_CDMA_RADIO_CHANGE_FOR_OFF, null);
                    } else {
                        ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getLtePhone().mCi.registerForRadioStateChanged(this,
                                EVENT_GSM_RADIO_CHANGE_FOR_OFF, null);
                    }
                } else {
                    ((PhoneBase) (((PhoneProxy) (PhoneFactory.getPhone(phoneId))).getActivePhone())).mCi
                            .registerForRadioStateChanged(this,
                            EVENT_GSM_RADIO_CHANGE_FOR_OFF, null);
                }
            }
        }

        // Send OUT_TIME empty message
        final int powerOutTime = getPowerOutTime();
        mMessageObj = new PowerOutTimeMessageObj(mPower, mIsPowerForModem);
        if (mPower) {
            sendMessageDelayed(obtainMessage(EVENT_POWER_ON_OUT_TIME, mMessageObj), powerOutTime);
        } else {
            sendMessageDelayed(obtainMessage(EVENT_POWER_OFF_OUT_TIME, mMessageObj), powerOutTime);
        }
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
        case EVENT_CDMA_RADIO_CHANGE_FOR_OFF:
        case EVENT_LTE_RADIO_CHANGE_FOR_OFF:
        case EVENT_GSM_RADIO_CHANGE_FOR_OFF:
            if (!mNeedIgnoreMessageForChangeDone) {
                if (msg.what == EVENT_CDMA_RADIO_CHANGE_FOR_OFF) {
                    log("handle EVENT_CDMA_RADIO_CHANGE_FOR_OFF");
                } else if (msg.what == EVENT_LTE_RADIO_CHANGE_FOR_OFF) {
                    log("handle EVENT_LTE_RADIO_CHANGE_FOR_OFF");
                } else if (msg.what == EVENT_GSM_RADIO_CHANGE_FOR_OFF) {
                    log("handle EVENT_GSM_RADIO_CHANGE_FOR_OFF");
                }
                for (int i = 0; i < mPhoneCount; i++) {
                    int phoneId = i;
                    if (!isRadioOff(phoneId)) {
                        log("radio state change, radio not off, phoneId = " + phoneId);
                        return;
                    }
                }
                log("All radio off");
                mInSwitching.set(false);
                unMonitorAirplaneChangeDone(true);
                cleanDesiredPowerStates();
                checkPendingRequest();
            }
            break;
        case EVENT_LTE_RADIO_CHANGE_FOR_AVALIABLE:
        case EVENT_CDMA_RADIO_CHANGE_FOR_AVALIABLE:
        case EVENT_GSM_RADIO_CHANGE_FOR_AVALIABLE:
        case EVENT_SET_DESIRED_POWERSTATE:
            if (!mNeedIgnoreMessageForChangeDone) {
                if (msg.what == EVENT_LTE_RADIO_CHANGE_FOR_AVALIABLE) {
                    log("handle EVENT_LTE_RADIO_CHANGE_FOR_AVALIABLE");
                } else if (msg.what == EVENT_CDMA_RADIO_CHANGE_FOR_AVALIABLE) {
                    log("handle EVENT_CDMA_RADIO_CHANGE_FOR_AVALIABLE");
                } else if (msg.what == EVENT_GSM_RADIO_CHANGE_FOR_AVALIABLE) {
                    log("handle EVENT_GSM_RADIO_CHANGE_FOR_AVALIABLE");
                } else if (msg.what == EVENT_SET_DESIRED_POWERSTATE) {
                    log("handle EVENT_SET_DESIRED_POWERSTATE");
                }
                if (!isRadioStateReady()) {
                    return;
                }
                mInSwitching.set(false);
                unMonitorAirplaneChangeDone(false);
                cleanDesiredPowerStates();
                checkPendingRequest();
            }
            break;
        case EVENT_POWER_ON_OUT_TIME:
            if (!mNeedIgnoreMessageForChangeDone) {
                log("handle EVENT_POWER_ON_OUT_TIME");
                if (isAvailable(msg)) {
                    isRadioStateReady();
                    mInSwitching.set(false);
                    unMonitorAirplaneChangeDone(false);
                    cleanDesiredPowerStates();
                    checkPendingRequest();
                }
            }
            break;
        case EVENT_POWER_OFF_OUT_TIME:
            if (!mNeedIgnoreMessageForChangeDone) {
                log("handle EVENT_POWER_OFF_OUT_TIME");
                if (isAvailable(msg)) {
                    isRadioStateReady();
                    mInSwitching.set(false);
                    unMonitorAirplaneChangeDone(true);
                    cleanDesiredPowerStates();
                    checkPendingRequest();
                }
            }
            break;
        case EVENT_WAIT_LTE_RADIO_CHANGE_FOR_AVALIABLE:
        case EVENT_WAIT_CDMA_RADIO_CHANGE_FOR_AVALIABLE:
        case EVENT_WAIT_GSM_RADIO_CHANGE_FOR_AVALIABLE:
            if (!mNeedIgnoreMessageForWait) {
                if (msg.what == EVENT_WAIT_LTE_RADIO_CHANGE_FOR_AVALIABLE) {
                    log("handle EVENT_WAIT_LTE_RADIO_CHANGE_FOR_AVALIABLE");
                } else if (msg.what == EVENT_WAIT_CDMA_RADIO_CHANGE_FOR_AVALIABLE) {
                    log("handle EVENT_WAIT_CDMA_RADIO_CHANGE_FOR_AVALIABLE");
                } else if (msg.what == EVENT_WAIT_GSM_RADIO_CHANGE_FOR_AVALIABLE) {
                    log("handle EVENT_WAIT_GSM_RADIO_CHANGE_FOR_AVALIABLE");
                }
                if (!isRadioAvaliable()) {
                    return;
                }
                log("All radio avaliable");
                unWaitRadioAvaliable();
                mInSwitching.set(false);
                checkPendingRequest();
            }
            break;
        default:
            log("handle msg.what=" + msg.what);
            return;
        }
    }

    private boolean isRadioOff(int phoneId) {
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            if (phoneId == SvlteModeController.getActiveSvlteModeSlotId()) {
                log("phoneId = " + phoneId + " , in svlte mode "
                        + " , lte radio state = "
                        + ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getLtePhone().mCi.getRadioState()
                        + " , cdma radio state = "
                        + ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getNLtePhone().mCi.getRadioState());
                final boolean isRadioOff = ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                        .getLtePhone().mCi.getRadioState() == RadioState.RADIO_OFF
                        && ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getNLtePhone().mCi.getRadioState() == RadioState.RADIO_OFF;
                /// add for the c2k radio deep sleep in roaming.
                if ((!isRadioOff) && !RadioManager.isFlightModePowerOffModemEnabled()
                        && CdmaFeatureOptionUtils.getC2KOMNetworkSelectionType() == 0
                        && ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getSvlteRatController().getRoamingMode() ==
                                SvlteRatController.RoamingMode.ROAMING_MODE_NORMAL_ROAMING
                        && ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getSvlteRatController().getSvlteRatMode() ==
                                SvlteRatController.SvlteRatMode.SVLTE_RAT_MODE_4G
                        && ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getSvlteRatController().isCdma4GSim()) {
                    return ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                            .getLtePhone().mCi.getRadioState() == RadioState.RADIO_OFF
                            && ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                    .getNLtePhone().mCi.getRadioState() == RadioState.RADIO_ON;
                }
                return isRadioOff;
            } else {
                log("phoneId = " + phoneId + ", in csfb mode, lte radio state = "
                        + ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getLtePhone().mCi.getRadioState());
                return ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                        .getLtePhone().mCi.getRadioState() == RadioState.RADIO_OFF;
            }
        } else {
            PhoneBase mPhone = (PhoneBase)(((PhoneProxy)(PhoneFactory.getPhone(phoneId)))
                                        .getActivePhone());
            log("phoneId = " + phoneId + ", in csfb mode, lte radio state = "
                    + mPhone.mCi.getRadioState());
            return mPhone.mCi.getRadioState() == RadioState.RADIO_OFF;
        }
    }

    private void checkPendingRequest() {
        log("checkPendingRequest, mPendingAirplaneModeRequest = " + mPendingAirplaneModeRequest);
        if (mPendingAirplaneModeRequest != null) {
            Boolean pendingAirplaneModeRequest = mPendingAirplaneModeRequest;
            mPendingAirplaneModeRequest = null;
            RadioManager.getInstance().notifyAirplaneModeChange(
                    pendingAirplaneModeRequest.booleanValue());
        }
    }

    protected void unMonitorAirplaneChangeDone(boolean airplaneMode) {
        mMessageObj.isAvailable = false;
        mNeedIgnoreMessageForChangeDone = true;
        Intent intent = new Intent(INTENT_ACTION_AIRPLANE_CHANGE_DONE);
        intent.putExtra(EXTRA_AIRPLANE_MODE, airplaneMode);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        int phoneId = 0;
        for (int i = 0; i < mPhoneCount; i++) {
            phoneId = i;
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                if (phoneId == SvlteModeController.getActiveSvlteModeSlotId()) {
                    ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                            .getLtePhone().mCi.unregisterForRadioStateChanged(this);
                    ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                            .getNLtePhone().mCi
                            .unregisterForRadioStateChanged(this);
                    log("unMonitorAirplaneChangeDone, for svlte phone,  phoneId = " + phoneId);
                } else {
                    ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                            .getLtePhone().mCi.unregisterForRadioStateChanged(this);
                    log("unMonitorAirplaneChangeDone, for csfb phone,  phoneId = " + phoneId);
                }
            } else {
                PhoneBase mPhone = (PhoneBase)(((PhoneProxy)(PhoneFactory.getPhone(phoneId)))
                                            .getActivePhone());
                mPhone.mCi.unregisterForRadioStateChanged(this);
                log("unMonitorAirplaneChangeDone, for csfb phone,  phoneId = " + phoneId);
            }
        }
    }

    /**
     * Set Whether force allow airplane mode change.
     * @return true or false
     */
    public void setForceSwitch(boolean forceSwitch) {
        mForceSwitch = forceSwitch;
        log("setForceSwitch, forceSwitch =" + forceSwitch);
    }

    private static void log(String s) {
        Rlog.d(LOG_TAG, "[RadioManager] " + s);
    }

    /**
     * The callback of Airplane Change Started.
     *
     * @param power Radio on or off.
     * @param isPowerForModem Whether power for modem.
     */
    protected final void onAirplaneChangeStarted(boolean power, boolean isPowerForModem) {
        mInSwitching.set(true);
        mPower = power;
        mIsPowerForModem = isPowerForModem;
        log("onAirplaneChangeStarted, power=" + mPower + ",isPowerForModem=" + mIsPowerForModem
                + ",mNeedIgnoreMessageForChangeDone=" + mNeedIgnoreMessageForChangeDone);
    }

    /**
     * set desired power state by phone id.
     *
     * @param phoneId the phone id.
     * @param phoneType the phone type.
     * @param desiredPowerState the desired power state.
     */
    public final void setDesiredPowerState(int phoneId, int phoneType, boolean desiredPowerState) {
        if (!mInSwitching.get()) {
            return;
        }

        final int mPhoneId = SvlteUtils.getSvltePhoneIdByPhoneId(phoneId);
        log("setDesiredPowerState, phoneId=" + phoneId
                + ", mPhoneId=" + mPhoneId + ", phoneType=" + phoneType
                + ", desiredPowerState=" + desiredPowerState);
        if (mPhoneId < 0 || mPhoneId >= mPhoneCount) {
            log("setDesiredPowerState, is invalid phoneId.");
            return;
        }

        if (mDesiredPowerStates[mPhoneId] == null) {
            log("setDesiredPowerState, Construct instance.");
            mDesiredPowerStates[mPhoneId] = new DesiredPowerState(mPhoneId);
        }
        if (PhoneConstants.PHONE_TYPE_CDMA == phoneType) {
            mDesiredPowerStates[mPhoneId].mNLteState = desiredPowerState ? RadioState.RADIO_ON
                    : RadioState.RADIO_OFF;
        } else {
            mDesiredPowerStates[mPhoneId].mLteState = desiredPowerState ? RadioState.RADIO_ON
                    : RadioState.RADIO_OFF;
        }
        log("setDesiredPowerState, " + mDesiredPowerStates[mPhoneId]);

        if (mPower) {
            sendMessageDelayed(obtainMessage(EVENT_SET_DESIRED_POWERSTATE),
                    SET_DESIRED_POWERSTATE_DELAY_TIME);
        }
    }

    private final void cleanDesiredPowerStates() {
        for (int i = 0; i < mPhoneCount; i++) {
            mDesiredPowerStates[i] = null;
        }
    }

    private final int getPowerOutTime() {
        int powerOutTime = POWER_ON_OUT_TIME_FOR_MODEM;
        if (mPower) {
            if (mIsPowerForModem) {
                powerOutTime = POWER_ON_OUT_TIME_FOR_MODEM;
            } else {
                powerOutTime = POWER_ON_OUT_TIME_FOR_RADIO;
            }
        } else {
            if (mIsPowerForModem) {
                powerOutTime = POWER_OFF_OUT_TIME_FOR_MODEM;
            } else {
                powerOutTime = POWER_OFF_OUT_TIME_FOR_RADIO;
            }
        }
        log("getPowerOutTime, " + powerOutTime);
        return powerOutTime;
    }

    private final boolean isAvailable(Message msg) {
        boolean isAvailable = false;
        if (msg.obj != null) {
            log("isAvailable," + msg.obj);
            if (msg.obj == mMessageObj && mMessageObj.isAvailable(mPower, mIsPowerForModem)) {
                isAvailable = true;
            }
        }
        log("isAvailable=" + isAvailable + ", " + mMessageObj.toString());
        return isAvailable;
    }

    private final boolean isRadioStateReady() {
        for (int i = 0; i < mPhoneCount; i++) {
            int phoneId = i;
            if (mDesiredPowerStates[phoneId] != null) {
                log("isRadioStateReady, " + mDesiredPowerStates[phoneId]);
            } else {
                log("isRadioStateReady, mDesiredPowerStates[" + phoneId + "] == null");
            }
            if (!isRadioStateReady(phoneId)) {
                log("phoneId = " + phoneId + " , radio state change, radio not ready");
                return false;
            }
        }
        log("All radio is ready");
        return true;
    }

    private final boolean isRadioStateReady(int phoneId) {
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            if (phoneId == SvlteModeController.getActiveSvlteModeSlotId()) {
                log("phoneId = " + phoneId + " , in svlte mode "
                        + " , lte radio state = "
                        + ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getLtePhone().mCi.getRadioState()
                        + " , cdma radio state = "
                        + ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getNLtePhone().mCi.getRadioState());
                return mDesiredPowerStates[phoneId] != null
                        && mDesiredPowerStates[phoneId].isLteRadioReady()
                        && mDesiredPowerStates[phoneId].isNLteRadioReady();
            } else {
                log("phoneId = " + phoneId + " , in csfb mode, lte radio state = "
                        + ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getLtePhone().mCi.getRadioState());
                return mDesiredPowerStates[phoneId] != null
                        && mDesiredPowerStates[phoneId].isLteRadioReady();
            }
        } else {
            PhoneBase mPhone = (PhoneBase)(((PhoneProxy)(PhoneFactory.getPhone(phoneId)))
                                            .getActivePhone());
            log("phoneId = " + phoneId + " , in csfb mode, lte radio state = "
                    + (mPhone.mCi.getRadioState()));
            return ((mPhone.mCi.getRadioState()) != RadioState.RADIO_UNAVAILABLE);
        }
    }

    protected boolean waitRadioAvaliable(boolean enabled) {
        final boolean wait = CdmaFeatureOptionUtils.isCdmaLteDcSupport() && !isRadioAvaliable();
        log("waitRadioAvaliable, enabled=" + enabled + ", wait=" + wait);
        if (wait) {
            // pending
            pendingAirplaneModeRequest(enabled);

            // wait for radio avaliable
            mNeedIgnoreMessageForWait = false;
            mInSwitching.set(true);

            // register for radiostate changed
            int phoneId = 0;
            for (int i = 0; i < mPhoneCount; i++) {
                phoneId = i;
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                    if (phoneId == SvlteModeController.getActiveSvlteModeSlotId()) {
                        ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getLtePhone().mCi.registerForRadioStateChanged(this,
                                EVENT_WAIT_LTE_RADIO_CHANGE_FOR_AVALIABLE, null);
                        ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getNLtePhone().mCi.registerForRadioStateChanged(this,
                                EVENT_WAIT_CDMA_RADIO_CHANGE_FOR_AVALIABLE, null);
                    } else {
                        ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getLtePhone().mCi.registerForRadioStateChanged(this,
                                EVENT_WAIT_GSM_RADIO_CHANGE_FOR_AVALIABLE, null);
                    }
                } else {
                    PhoneBase mPhone = (PhoneBase) (((PhoneProxy) (PhoneFactory.getPhone(phoneId)))
                            .getActivePhone());
                    mPhone.mCi.registerForRadioStateChanged(this,
                            EVENT_WAIT_GSM_RADIO_CHANGE_FOR_AVALIABLE, null);
                }
            }
        }

        return wait;
    }

    private final void unWaitRadioAvaliable() {
        mNeedIgnoreMessageForWait = true;
        int phoneId = 0;
        for (int i = 0; i < mPhoneCount; i++) {
            phoneId = i;
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                if (phoneId == SvlteModeController.getActiveSvlteModeSlotId()) {
                    ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                            .getLtePhone().mCi.unregisterForRadioStateChanged(this);
                    ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                            .getNLtePhone().mCi
                            .unregisterForRadioStateChanged(this);
                    log("unWaitRadioAvaliable, for svlte phone,  phoneId = " + phoneId);
                } else {
                    ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                            .getLtePhone().mCi.unregisterForRadioStateChanged(this);
                    log("unWaitRadioAvaliable, for csfb phone,  phoneId = " + phoneId);
                }
            } else {
                PhoneBase mPhone = (PhoneBase) (((PhoneProxy) (PhoneFactory.getPhone(phoneId)))
                        .getActivePhone());
                mPhone.mCi.unregisterForRadioStateChanged(this);
                log("unWaitRadioAvaliable, for csfb phone,  phoneId = " + phoneId);
            }
        }
    }

    private final boolean isRadioAvaliable() {
        boolean isRadioAvaliable = true;
        for (int i = 0; i < mPhoneCount; i++) {
            int phoneId = i;
            if (!isRadioAvaliable(phoneId)) {
                isRadioAvaliable = false;
                break;
            }
        }
        return isRadioAvaliable;
    }

    private final boolean isRadioAvaliable(int phoneId) {
        boolean isAvailable = true;
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            if (phoneId == SvlteModeController.getActiveSvlteModeSlotId()) {
                log("isRadioAvaliable, phoneId = " + phoneId + " , in svlte mode "
                        + " , lte radio state = "
                        + ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getLtePhone().mCi.getRadioState()
                        + " , cdma radio state = "
                        + ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getNLtePhone().mCi.getRadioState());

                isAvailable = ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                        .getLtePhone().mCi.getRadioState().isAvailable()
                        && ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getNLtePhone().mCi.getRadioState().isAvailable();
            } else {
                log("isRadioAvaliable, phoneId = " + phoneId + ", in csfb mode, lte radio state = "
                        + ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                                .getLtePhone().mCi.getRadioState());
                isAvailable = ((SvltePhoneProxy) PhoneFactory.getPhone(phoneId))
                        .getLtePhone().mCi.getRadioState().isAvailable();
            }
        } else {
            PhoneBase mPhone = (PhoneBase) (((PhoneProxy) (PhoneFactory.getPhone(phoneId)))
                    .getActivePhone());
            log("isRadioAvaliable, phoneId = " + phoneId + ", in csfb mode, lte radio state = "
                    + mPhone.mCi.getRadioState());
            isAvailable = mPhone.mCi.getRadioState().isAvailable();
        }

        log("isRadioAvaliable, phoneId = " + phoneId + ", isAvailable = " + isAvailable);
        return isAvailable;
    }

    /**
     * Desired PowerState.
     */
    class DesiredPowerState {
        private int mPhoneId = -1;
        private RadioState mLteState = null;
        private RadioState mNLteState = null;

        /**
         * Construct a new DesiredPowerState instance.
         *
         * @param phoneId the phone Id.
         */
        public DesiredPowerState(int phoneId) {
            super();
            this.mPhoneId = phoneId;
        }

        /**
         * Whether LtePhone radio ready.
         *
         * @return true if LtePhone radio ready.
         */
        private final boolean isLteRadioReady() {
            boolean ready = false;
            RadioState radioState;
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                radioState = ((SvltePhoneProxy) PhoneFactory.getPhone(mPhoneId))
                        .getLtePhone().mCi.getRadioState();
            } else {
                PhoneBase mPhone = (PhoneBase)(((PhoneProxy)(PhoneFactory.getPhone(mPhoneId)))
                                            .getActivePhone());
                radioState = mPhone.mCi.getRadioState();
            }
            if (mLteState != null) {
                ready = radioState == mLteState;
                if (!ready) {
                    if (mPower) {
                        ready = radioState.isOn();
                    } else {
                        ready = !radioState.isOn();
                    }
                }
            } else {
                if (mPower) {
                    ready = radioState.isAvailable();
                } else {
                    ready = !radioState.isOn();
                }
            }
            log("isLteRadioAvaliable, ready=" + ready);
            return ready;
        }

        /**
         * Whether NLtePhone radio ready.
         *
         * @return true if NLtePhone radio ready.
         */
        private final boolean isNLteRadioReady() {
            boolean ready = false;
            RadioState radioState;
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                radioState = ((SvltePhoneProxy) PhoneFactory.getPhone(mPhoneId))
                        .getNLtePhone().mCi.getRadioState();
            } else {
                PhoneBase mPhone = (PhoneBase)(((PhoneProxy)(PhoneFactory.getPhone(mPhoneId)))
                                            .getActivePhone());
                radioState = mPhone.mCi.getRadioState();
            }
            if (mNLteState != null) {
                ready = radioState == mNLteState;
                if (!ready) {
                    if (mPower) {
                        ready = radioState.isOn();
                    } else {
                        ready = !radioState.isOn();
                    }
                }
            } else {
                if (mPower) {
                    ready = radioState.isAvailable();
                } else {
                    ready = !radioState.isOn();
                }
            }

            log("isNLteRadioAvaliable, ready=" + ready);
            return ready;
        }

        @Override
        public String toString() {
            return "DesiredState [mPhoneId=" + mPhoneId + ", mLteState="
                    + mLteState + ", mNLteState=" + mNLteState + "]";
        }
    }

    /**
     * Power Out Time Message object.
     */
    private final class PowerOutTimeMessageObj {
        public boolean power;
        public boolean isPowerForModem;
        public boolean isAvailable;

        private PowerOutTimeMessageObj(boolean power, boolean isPowerForModem) {
            super();
            this.power = power;
            this.isPowerForModem = isPowerForModem;
            this.isAvailable = true;
        }

        private boolean isAvailable(boolean power, boolean isPowerForModem) {
            return isAvailable && this.power == power && this.isPowerForModem == isPowerForModem;
        }

        @Override
        public String toString() {
            return "MsgObj [isPowerForModem=" + isPowerForModem + ", power=" + power
                    + ", isAvailable=" + isAvailable + "]";
        }
    }
}
