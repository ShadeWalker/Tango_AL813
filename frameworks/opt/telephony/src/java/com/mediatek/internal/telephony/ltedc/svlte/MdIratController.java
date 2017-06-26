
package com.mediatek.internal.telephony.ltedc.svlte;

import android.content.Intent;
import android.os.AsyncResult;
import android.os.Message;
import android.telephony.Rlog;
import android.telephony.ServiceState;

import com.android.internal.telephony.dataconnection.DctController;

/**
 * It register for IRAT related URC and data reg state to track data rat. It
 * will notify to his registrants and send broadcasts when data rat changed.
 * IRAT status can be get from it.
 */
public class MdIratController extends IratController {
    private static final String LOG_TAG = "IRATCtrl";

    // Broadcast action and extras for IRAT.
    public static final String ACTION_IRAT_STARTED = "com.mediatek.irat.action.started";
    public static final String ACTION_IRAT_FINISHED = "com.mediatek.irat.action.finished";
    public static final String ACTION_IRAT_SUCCEEDED = "com.mediatek.irat.action.succeeded";
    public static final String ACTION_IRAT_FAILED = "com.mediatek.irat.action.failed";
    public static final String SOURCE_RAT = "extra_source_rat";
    public static final String TARGET_RAT = "extra_target_rat";

    // IRAT actions.
    public static final int IRAT_ACTION_SOURCE_STARTED = 1;
    public static final int IRAT_ACTION_SOURCE_FINISHED = 2;
    public static final int IRAT_ACTION_TARGET_STARTED = 3;
    public static final int IRAT_ACTION_TARGET_FINISHED = 4;

    // Message for events.
    private static final int EVENT_LTE_INTER_3GPP_IRAT = 100;
    private static final int EVENT_CDMA_INTER_3GPP_IRAT = 101;
    protected static final int EVENT_SYNC_DATA_CALL_LIST_DONE = 102;
    protected static final int EVENT_LTE_RADIO_NOT_AVAILABLE = 103;
    protected static final int EVENT_CDMA_RADIO_NOT_AVAILABLE = 104;

    // Rat indicator reported from modem when IRAT
    private static final int RAT_FOR_INTER_3GPP_IRAT_NOT_SPECIFIED = 0;
    private static final int RAT_FOR_INTER_3GPP_IRAT_1xRTT = 1;
    private static final int RAT_FOR_INTER_3GPP_IRAT_HRPD = 2;
    private static final int RAT_FOR_INTER_3GPP_IRAT_EHRPD = 3;
    private static final int RAT_FOR_INTER_3GPP_IRAT_LTE = 4;

    // Rat group
    private static final int RAT_GROUP_3GPP = 1;
    private static final int RAT_GROUP_3GPP2 = 2;

    // IRAT confirm flag
    private static final int IRAT_CONFIRM_ACCEPTED = 1;
    private static final int IRAT_CONFIRM_DENIED = 0;

    //For sim switch happen during irat scenario.
    //When receive radio not available event, use this irat info to notify irat finish.
    protected MdIratInfo mTempIratInfo = new MdIratInfo();

    /**
     * Constructor, register for IRAT status change and data reg state.
     * @param svltePhoneProxy SVLTE phone proxy
     */
    public MdIratController(SvltePhoneProxy svltePhoneProxy) {
        super(svltePhoneProxy);
    }

    /**
     * Unregister from all events it registered for.
     */
    public void dispose() {
        log("dispose");
        super.dispose();
    }

    @Override
    protected void registerForAllEvents() {
        super.registerForAllEvents();
        mLteCi.registerForIratStateChanged(this, EVENT_LTE_INTER_3GPP_IRAT, null);
        mLteCi.registerForNotAvailable(this, EVENT_LTE_RADIO_NOT_AVAILABLE, null);

        mCdmaCi.registerForIratStateChanged(this, EVENT_CDMA_INTER_3GPP_IRAT, null);
        mCdmaCi.registerForNotAvailable(this, EVENT_CDMA_RADIO_NOT_AVAILABLE, null);
    }

    @Override
    protected void unregisterForAllEvents() {
        super.unregisterForAllEvents();
        mLteCi.unregisterForIratStateChanged(this);
        mLteCi.unregisterForNotAvailable(this);
        mCdmaCi.unregisterForIratStateChanged(this);
        mCdmaCi.unregisterForNotAvailable(this);
    }

    private synchronized void notifyIratEvent(int eventType, MdIratInfo info) {
        MdIratInfo mdInfo = new MdIratInfo();

        mdInfo.sourceRat = mappingRatToRadioTech(info.sourceRat);
        mdInfo.targetRat = mappingRatToRadioTech(info.targetRat);
        mdInfo.action = info.action;
        mdInfo.type = info.type;

        for (OnIratEventListener listener : mIratEventListener) {
            log("notifyIratEvent: listener = " + listener);
            if (eventType == IRAT_ACTION_SOURCE_STARTED) {
                listener.onIratStarted(mdInfo);
            } else if (eventType == IRAT_ACTION_TARGET_FINISHED) {
                listener.onIratEnded(mdInfo);
            }
        }
    }

    @Override
    public boolean isDrsInService() {
        log("isDrsInService: mLteRegState = " + mLteRegState
                + ", mCdmaRegState = " + mCdmaRegState);
        return mLteRegState == ServiceState.STATE_IN_SERVICE
                || mCdmaRegState == ServiceState.STATE_IN_SERVICE;
    }

    @Override
    protected boolean processMessage(Message msg) {
        boolean ret = false;
        AsyncResult ar = null;
        log("processMessage, msg.what = " + msg.what);
        switch (msg.what) {
            case EVENT_LTE_INTER_3GPP_IRAT:
            case EVENT_CDMA_INTER_3GPP_IRAT:
                ar = (AsyncResult) msg.obj;
                MdIratInfo info = (MdIratInfo) ar.result;
                log("processMessage, EVENT_INTER_3GPP_IRAT[" + msg.what
                        + "] status = " + info.toString());

                if (info.action == IRAT_ACTION_SOURCE_STARTED) {
                    mSstProxy.setEnabled(false);
                    notifyIratEvent(info.action, info);
                    onIratStarted(info);
                } else if (info.action == IRAT_ACTION_TARGET_FINISHED) {
                    mSstProxy.setEnabled(true);
                    onIratFinished(info);
                    notifyIratEvent(info.action, info);
                }
                ret = true;
                break;

            case EVENT_SYNC_DATA_CALL_LIST_DONE:
                onSyncDataCallListDone((AsyncResult) msg.obj);
                ret = true;
                break;

            case EVENT_LTE_RADIO_NOT_AVAILABLE:
            case EVENT_CDMA_RADIO_NOT_AVAILABLE:
            //To hanldle sim switch happen during irat scenario
                if (mIsDuringIrat && mTempIratInfo.targetRat != mTempIratInfo.sourceRat) {
                    mTempIratInfo.targetRat = mTempIratInfo.sourceRat;
                    mTempIratInfo.action = IRAT_ACTION_TARGET_FINISHED;
                    mTempIratInfo.type = MdIratInfo.IratType.IRAT_TYPE_FAILED;
                    mSstProxy.setEnabled(true);
                    onIratFinished(mTempIratInfo);
                    notifyIratEvent(mTempIratInfo.action, mTempIratInfo);
                }
                break;

            default:
                break;
        }
        return ret || super.processMessage(msg);
    }

    // Data regist state or rat event change from SST.
    // Cover Attached or Detached event outside of IRAT procedure.
    // Skip not-IN_SERVICE event if current used phone is C2K.
    //     Use or switch to LTE MD once LTE is reported as ATTACHED.
    //     Report detached event to DctController / DcTracker if current used phone is LTE
    @Override
    protected void onLteDataRegStateOrRatChange(int drs, int rat) {
        int c2kState = getCdmaRegState();
        log("onLteDataRegStateOrRatChange, drs=" + drs
            + ", rat=" + rat
            + ", c2kState=" + c2kState);

        if (mIsDuringIrat) {
            log("Skip the unwanted LteDataRegStateOrRatChange, mIsDuringIrat is true");
            return;
        }

        boolean skip = true;
        if (drs == ServiceState.STATE_IN_SERVICE) {
            // new incoming ATTACH event has high priority
            log("onLteDataRegStateOrRatChange C1, attached");
            skip = false;
        } else { // drs == out of service
            if (rat == ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) {
                //If we receive GSM detach event, we need make sure current is in GSM.
                if (getRadioGroupByRat(mCurrentRat) == RAT_GROUP_3GPP) {
                    // Current used PS detached.
                    log("onLteDataRegStateOrRatChange C2, detached");
                    skip = false;
                } else {
                    log("onLteDataRegStateOrRatChange C3, skip");
                }
            } else {
                log("onLteDataRegStateOrRatChange C4, skip");
            }
        }

        if (!skip) {
            updateCurrentRat(rat);
        }
    }

    // Data regist state or rat change event from SST.
    // Cover Attached or Detached event outside of IRAT procedure.
    // Skip not-IN_SERVICE event if current used phone is LTE.
    //     Use or switch to C2K MD once C2K is reported as ATTACHED.
    //     Report detached event to DctController / DcTracker if current used phone is C2K
    //     Update RAT insides C2K such as evdo to hprd or ehprd    
    @Override
    protected void onCdmaDataRegStateOrRatChange(int drs, int rat) {
        int lteState = getLteRegState();
        log("onCdmaDataRegStateOrRatChange, drs=" + drs
            + ", rat=" + rat
            + ", lteState=" + lteState);

        if (rat == ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) {
            // SST could report partial statue with new RAT but old DATA STATE
            //    Here we take it as OUT OF SERVICE if RAT is 0 UNKNOWN.            
            drs = ServiceState.STATE_OUT_OF_SERVICE;
            mCdmaRegState = drs;
        }

        if (mIsDuringIrat) {
            log("Skip the unwanted CdmaDataRegStateOrRatChange, mIsDuringIrat is true");
            return;
        }

        boolean skip = true;
        if (drs == ServiceState.STATE_IN_SERVICE) {
            // new incoming ATTACH event has high priority
            log("onCdmaDataRegStateOrRatChange C1, attached");
            skip = false;
        } else { // drs == out of service
            if (rat == ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) {
                if (mCurrentRat != ServiceState.RIL_RADIO_TECHNOLOGY_LTE
                    && mCurrentRat != ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) {
                    log("onCdmaDataRegStateOrRatChange C2, detached");
                    // Current used PS detached.
                    skip = false;
                } else {
                    log("onCdmaDataRegStateOrRatChange C3, skip");
                }
            } else {
                log("onCdmaDataRegStateOrRatChange C4, skip");
            }
        }

        if (!skip) {
            updateCurrentRat(rat);
        }
    }

    @Override
    protected void onSimMissing() {
        resetStatus();
    }

    private void onIratStarted(MdIratInfo info) {
        log("onIratStarted: info = " + info + ", mCurrentRat = " + mCurrentRat);
        mIsDuringIrat = true;
        mTempIratInfo.sourceRat = info.sourceRat;
        mTempIratInfo.targetRat = info.targetRat;
        mTempIratInfo.action = info.action;
        mTempIratInfo.type = info.type;

        suspendDataRequests();

        // confirm IRAT start
        if (info.sourceRat == RAT_FOR_INTER_3GPP_IRAT_LTE) {
            mLteCi.confirmIratChange(IRAT_CONFIRM_ACCEPTED, null);
        } else {
            mCdmaCi.confirmIratChange(IRAT_CONFIRM_ACCEPTED, null);
        }

        notifyIratStarted(info);
    }

    private void onIratFinished(MdIratInfo info) {
        log("onIratFinished: mPrevRat = " + mPrevRat + ", mCurrentRat = " + mCurrentRat
                + ", info =" + info);
        mIsDuringIrat = false;

        if (info.sourceRat != info.targetRat) {
            // We need to update RAT because +ECGREG/+CEREG may be handled after
            // IRAT finished.
            mPrevRat = mappingRatToRadioTech(info.sourceRat);
            mCurrentRat = mappingRatToRadioTech(info.targetRat);

            log("onIratFinished: mCurrentRat = "
                    + ServiceState.rilRadioTechnologyToString(mCurrentRat)
                    + ", mPrevRat = "
                    + ServiceState.rilRadioTechnologyToString(mPrevRat));
            if (getRadioGroupByRat(mPrevRat) != getRadioGroupByRat(mCurrentRat)) {
                mSvltePhoneProxy.updatePsPhone(mPrevRat, mCurrentRat);
                mPsCi = mSvltePhoneProxy.getPsPhone().mCi;

                // C2K maybe attached before IRAT
                mSvltePhoneProxy.getIratDataSwitchHelper().syncAndNotifyAttachState();
            }

            // Only get data call list in non Fallback case.
            if (info.type.isIpContinuousCase()) {
                log("onIratFinished: mPsCi = " + mPsCi);
                mPsCi.getDataCallList(obtainMessage(EVENT_SYNC_DATA_CALL_LIST_DONE));
            } else {
                sendMessage(obtainMessage(EVENT_SYNC_DATA_CALL_LIST_DONE));
            }
        } else {
            resumeDataRequests();
        }

        notifyIratFinished(info);
    }

    private void onSyncDataCallListDone(AsyncResult dcList) {
        log("onSyncDataCallListDone: dcList = " + dcList);
        if (dcList != null) {
            mPsCi.syncNotifyDataCallList(dcList);
        }
        resumeDataRequests();
    }

    private void suspendDataRequests() {
        log("suspendDataRequests...");
        // Suspend network request and data RIL request.
        DctController.getInstance().suspendNetworkRequest();
        mSvltePhoneProxy.getRilDcArbitrator().suspendDataRilRequest();
    }

    private void resumeDataRequests() {
        log("resumeDataRequests...");
        // Resume network request and data RIL request.
        DctController.getInstance().resumeNetworkRequest();
        mSvltePhoneProxy.getRilDcArbitrator().resumeDataRilRequest();
    }

    private void notifyIratStarted(MdIratInfo info) {
        // Send broadcast
        Intent intent = new Intent(ACTION_IRAT_STARTED);
        intent.putExtra(SOURCE_RAT, mCurrentRat);
        mContext.sendBroadcast(intent);
    }

    private void notifyIratFinished(MdIratInfo info) {
        // Send broadcast
        Intent intent = new Intent(ACTION_IRAT_FINISHED);
        intent.putExtra(SOURCE_RAT, mPrevRat);
        intent.putExtra(TARGET_RAT, mCurrentRat);
        mContext.sendBroadcast(intent);
    }

    /**
     * Mapping RAT from modem to real radio technology.
     * @param rat RAT from MD during IRAT.
     * @return Radio technology suppose to be.
     */
    private int mappingRatToRadioTech(int rat) {
        if (rat == RAT_FOR_INTER_3GPP_IRAT_LTE) {
            return ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
        } else if (rat == RAT_FOR_INTER_3GPP_IRAT_EHRPD) {
            return ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD;
        } else if (rat == RAT_FOR_INTER_3GPP_IRAT_HRPD) {
            return ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A;
        } else if (rat == RAT_FOR_INTER_3GPP_IRAT_1xRTT) {
            return ServiceState.RIL_RADIO_TECHNOLOGY_IS95A;
        }
        return ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN;
    }

    /**
     * There are three types of rat reported from MD: LTE, EHRPD and HRPD. EHRPD
     * and HRPD is in 3GPP2 rat group and LTE is in 3GPP group.
     * @param radioTech RAT for inter 3GPP IRAT
     * @return group for rat
     */
    private static int getRadioGroupByRat(int radioTech) {
        if ((radioTech >= ServiceState.RIL_RADIO_TECHNOLOGY_IS95A
                && radioTech <= ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A)
                || (radioTech >= ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_B
                && radioTech <= ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD)) {
            return RAT_GROUP_3GPP2;
        } else {
            return RAT_GROUP_3GPP;
        }
    }

    @Override
    protected void updateCurrentRat(int newRat) {
        log("updateCurrentRat: mIsDuringIrat = " + mIsDuringIrat
                + ", newRat = " + newRat + ", mCurrentRat = " + mCurrentRat);

        if (!mIsDuringIrat && newRat != mCurrentRat) {
            mPrevRat = mCurrentRat;
            mCurrentRat = newRat;

            mSvltePhoneProxy.updatePsPhone(mPrevRat, mCurrentRat);
            notifyRatChange(mPrevRat, mCurrentRat);
            // Skip sync notify attached state when RAT is UNKNOWN.
            if (newRat != ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) {
                mSvltePhoneProxy.getIratDataSwitchHelper().syncAndNotifyAttachState();
            }
        }
    }

    @Override
    protected void log(String s) {
        Rlog.d(LOG_TAG, "[" + mSvltePhoneProxy.getPhoneId() + "] " + s);
    }

    @Override
    protected void loge(String s) {
        Rlog.e(LOG_TAG, "[" + mSvltePhoneProxy.getPhoneId() + "] " + s);
    }
}
