
package com.mediatek.internal.telephony.ltedc.svlte;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Pair;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.dataconnection.DcTracker;

import java.util.ArrayList;
import java.util.List;

/**
 * It register for IRAT related URC and data reg state to track data rat. It
 * will notify to his registrants and send broadcasts when data rat changed.
 * IRAT status can be get from it.
 */
public abstract class IratController extends Handler {
    private static final String LOG_TAG = "CDMA";

    // Broadcast action and extras for IRAT.
    public static final String ACTION_IRAT_STARTED = "com.mediatek.irat.action.started";
    public static final String ACTION_IRAT_FINISHED = "com.mediatek.irat.action.finished";
    public static final String ACTION_IRAT_SUCCEEDED = "com.mediatek.irat.action.succeeded";
    public static final String ACTION_IRAT_FAILED = "com.mediatek.irat.action.failed";
    public static final String SOURCE_RAT = "extra_source_rat";
    public static final String TARGET_RAT = "extra_target_rat";

    // Message for events.
    private static final int EVENT_LTE_DATA_REG_STATE_OR_RAT_CHANGE = 0;
    private static final int EVENT_CDMA_DATA_REG_STATE_OR_RAT_CHANGE = 1;
    private static final int EVENT_LTE_SIM_MISSING = 2;
    private static final int EVENT_CDMA_SIM_MISSING = 3;

    protected Context mContext;

    protected CommandsInterface mLteCi;
    protected CommandsInterface mCdmaCi;
    protected CommandsInterface mPsCi;

    protected PhoneBase mLtePhone;
    protected PhoneBase mCdmaPhone;

    protected SvltePhoneProxy mSvltePhoneProxy;

    // Current data reg state
    protected int mLteRegState = ServiceState.STATE_OUT_OF_SERVICE;
    protected int mCdmaRegState = ServiceState.STATE_OUT_OF_SERVICE;
    protected int mLteRat = ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN;
    protected int mCdmaRat = ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN;

    // Current data rat
    protected int mCurrentRat = ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN;
    // Old rat of the change
    protected int mPrevRat = ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN;

    // Whether IRAT is ongoing
    protected boolean mIsDuringIrat;

    // Registrants for rat change.
    protected RegistrantList mRatChangedRegistrants = new RegistrantList();
    protected List<OnIratEventListener> mIratEventListener = new ArrayList<OnIratEventListener>();

    //SST Proxy
    protected SvlteSstProxy mSstProxy;

    protected int mPsType;
    protected boolean mIratControllerEnabled;
    protected DcTracker mDcTracker;

    /**
     * Constructor, register for IRAT status change and data reg state.
     * @param svltePhoneProxy SVLTE phone proxy
     */
    public IratController(SvltePhoneProxy svltePhoneProxy) {
        mSvltePhoneProxy = svltePhoneProxy;
        mContext = mSvltePhoneProxy.getContext();
        mLtePhone = mSvltePhoneProxy.getLtePhone();
        mCdmaPhone = mSvltePhoneProxy.getNLtePhone();
        mLteCi = mLtePhone.mCi;
        mCdmaCi = mCdmaPhone.mCi;
        mSstProxy = SvlteSstProxy.getInstance();

        registerForAllEvents();
    }

    /**
     * Unregister from all events it registered for.
     */
    public void dispose() {
        log("dispose");
        unregisterForAllEvents();
    }

    protected void registerForAllEvents() {
        log("registerForAllEvents.");
        mLtePhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(
                this, EVENT_LTE_DATA_REG_STATE_OR_RAT_CHANGE, null);
        mLteCi.registerForSimMissing(this, EVENT_LTE_SIM_MISSING, null);

        mCdmaPhone.getServiceStateTracker()
                .registerForDataRegStateOrRatChanged(this,
                        EVENT_CDMA_DATA_REG_STATE_OR_RAT_CHANGE, null);
        mCdmaCi.registerForSimMissing(this, EVENT_CDMA_SIM_MISSING, null);
    }

    protected void unregisterForAllEvents() {
        log("unregisterForAllEvents.");
        mLtePhone.getServiceStateTracker()
                .unregisterForDataRegStateOrRatChanged(this);
        mLteCi.unregisterForSimMissing(this);

        mCdmaPhone.getServiceStateTracker()
                .unregisterForDataRegStateOrRatChanged(this);
        mCdmaCi.unregisterForSimMissing(this);
    }

    /**
     * Updata PS RIL to current.
     * @param psCi Latest PS RIL.
     */
    public void updatePsCi(CommandsInterface psCi) {
        log("updatePsCi: psCi = " + psCi + ", mPsCi = " + mPsCi);
        mPsCi = psCi;
    }

    /**
     * Register IRAT event listener.
     * @param listener Listener for IRAT event.
     */
    public synchronized void addOnIratEventListener(OnIratEventListener listener) {
        log("addOnIratEventListener: listener = " + listener);
        mIratEventListener.add(listener);
    }

    /**
     * Unregister IRAT started notification.
     * @param listener Listener for IRAT event.
     */
    public synchronized void removeOnIratEventListener(
            OnIratEventListener listener) {
        log("removeOnIratEventListener: listener = " + listener);
        mIratEventListener.remove(listener);
    }

    protected void notifyRatChange(int source, int target) {
        log("notifyRatChange: source = " + source + ", target = " + target);
        mRatChangedRegistrants.notifyResult(new Pair<Integer, Integer>(source,
                target));
    }

    /**
     * Register data rat change notification.
     * @param h Handler rat change.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public void registerForRatChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mRatChangedRegistrants.add(r);
    }

    /**
     * Unregister data rat change notification.
     * @param h Handler rat change.
     */
    public void unregisterForRatChanged(Handler h) {
        mRatChangedRegistrants.remove(h);
    }

    /**
     * Whether is during IRAT.
     * @return true, if is during IRAT
     */
    public boolean isDuringIrat() {
        log("isDuringIrat: mIsDuringIrat = " + mIsDuringIrat);
        return mIsDuringIrat;
    }

    /**
     * Whether data reg state is in service.
     * @return true, if drs is in service
     */
    public boolean isDrsInService() {
        // That one of the two reg states is in service means lteDcPhone data
        // is in service.
        return mLteRegState == ServiceState.STATE_IN_SERVICE
                || mCdmaRegState == ServiceState.STATE_IN_SERVICE;
    }

    /**
     * Set DcTracker to IRAT controller.
     * @param dcTracker DcTracker shared by SvltePhoneProxy.
     */
    public void setDcTracker(DcTracker dcTracker) {
        mDcTracker = dcTracker;
    }

    /**
     * Set current PS service type.
     * @param newPsType New PS type.
     */
    public void setPsServiceType(int newPsType) {
        mPsType = newPsType;
    }

    /**
     * Get current PS service type.
     * @return current PS service type.
     */
    public int getCurrentPsType() {
        return mPsType;
    }

    /**
     * Get LTE register state.
     * @return LTE register state.
     */
    public int getLteRegState() {
        return mLteRegState;
    }

    /**
     * Get CDMA register state.
     * @return CDMA register state.
     */
    public int getCdmaRegState() {
        return mCdmaRegState;
    }

    /**
     * Get current data rat.
     * @return current data rat
     */
    public int getCurrentRat() {
        log("getCurrentRat: mCurrentRat = " + mCurrentRat);
        return mCurrentRat;
    }

    /**
     * Get current network type.
     * @return current network type.
     */
    public int getCurrentNetworkType() {
        return rilRadioTechnologyToNetworkType(mCurrentRat);
    }

    @Override
    public void handleMessage(Message msg) {
        boolean ret = processMessage(msg);
        if (!ret) {
            loge("handleMessage with unknow message: " + msg.what);
        }
    }

    protected boolean processMessage(Message msg) {
        boolean ret = false;
        AsyncResult ar = null;
        log("processMessage: msg.what = " + msg.what);
        switch (msg.what) {
            case EVENT_LTE_DATA_REG_STATE_OR_RAT_CHANGE:
                ar = (AsyncResult) msg.obj;
                Pair<Integer, Integer> lteDrsRat = (Pair<Integer, Integer>) ar.result;
                log("processMessage: EVENT_LTE_DATA_REG_STATE_OR_RAT_CHANGE newRat = "
                        + lteDrsRat.second + ", regstate = " + lteDrsRat.first);
                mLteRegState = lteDrsRat.first;
                mLteRat = lteDrsRat.second;
                onLteDataRegStateOrRatChange(lteDrsRat.first, lteDrsRat.second);
                ret = true;
                break;

            case EVENT_CDMA_DATA_REG_STATE_OR_RAT_CHANGE:
                ar = (AsyncResult) msg.obj;
                Pair<Integer, Integer> cdmaDrsRat = (Pair<Integer, Integer>) ar.result;
                log("processMessage: EVENT_CDMA_DATA_REG_STATE_OR_RAT_CHANGE newRat = "
                        + cdmaDrsRat.second + ", regstate = " + cdmaDrsRat.first);
                mCdmaRegState = cdmaDrsRat.first;
                mCdmaRat = cdmaDrsRat.second;
                onCdmaDataRegStateOrRatChange(cdmaDrsRat.first,
                        cdmaDrsRat.second);
                ret = true;
                break;

            case EVENT_LTE_SIM_MISSING:
            case EVENT_CDMA_SIM_MISSING:
                log("processMessage: EVENT_SIM_MISSING [" + msg.what + "]");
                onSimMissing();
                ret = true;
                break;
            default:
                break;
        }
        return ret;
    }

    protected abstract void onLteDataRegStateOrRatChange(int drs, int rat);
    protected abstract void onCdmaDataRegStateOrRatChange(int drs, int rat);

    protected abstract void onSimMissing();

    /**
     * Update current RAT to latest.
     * @param newRat Latest RAT.
     */
    protected abstract void updateCurrentRat(int newRat);

    /**
     * Reset status.
     */
    public void resetStatus() {
        log("resetStatus: mPrevRat = " + mPrevRat + ", mCurrentRat = " + mCurrentRat);
        mIsDuringIrat = false;
        mCurrentRat = ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN;
        mPrevRat = ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN;
        mLteRegState = ServiceState.STATE_OUT_OF_SERVICE;
        mCdmaRegState = ServiceState.STATE_OUT_OF_SERVICE;
    }

    /**
     * Mapping RAT to network type.
     * @param rt RAT.
     * @return Network type.
     */
    public int rilRadioTechnologyToNetworkType(int rt) {
        switch(rt) {
        case ServiceState.RIL_RADIO_TECHNOLOGY_GPRS:
            return TelephonyManager.NETWORK_TYPE_GPRS;
        case ServiceState.RIL_RADIO_TECHNOLOGY_EDGE:
            return TelephonyManager.NETWORK_TYPE_EDGE;
        case ServiceState.RIL_RADIO_TECHNOLOGY_UMTS:
            return TelephonyManager.NETWORK_TYPE_UMTS;
        case ServiceState.RIL_RADIO_TECHNOLOGY_HSDPA:
            return TelephonyManager.NETWORK_TYPE_HSDPA;
        case ServiceState.RIL_RADIO_TECHNOLOGY_HSUPA:
            return TelephonyManager.NETWORK_TYPE_HSUPA;
        case ServiceState.RIL_RADIO_TECHNOLOGY_HSPA:
            return TelephonyManager.NETWORK_TYPE_HSPA;
        case ServiceState.RIL_RADIO_TECHNOLOGY_IS95A:
        case ServiceState.RIL_RADIO_TECHNOLOGY_IS95B:
            return TelephonyManager.NETWORK_TYPE_CDMA;
        case ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT:
            return TelephonyManager.NETWORK_TYPE_1xRTT;
        case ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_0:
            return TelephonyManager.NETWORK_TYPE_EVDO_0;
        case ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A:
            return TelephonyManager.NETWORK_TYPE_EVDO_A;
        case ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_B:
            return TelephonyManager.NETWORK_TYPE_EVDO_B;
        case ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD:
            return TelephonyManager.NETWORK_TYPE_EHRPD;
        case ServiceState.RIL_RADIO_TECHNOLOGY_LTE:
            return TelephonyManager.NETWORK_TYPE_LTE;
        case ServiceState.RIL_RADIO_TECHNOLOGY_HSPAP:
            return TelephonyManager.NETWORK_TYPE_HSPAP;
        case ServiceState.RIL_RADIO_TECHNOLOGY_GSM:
            return TelephonyManager.NETWORK_TYPE_GSM;
        default:
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
    }

    private void dumpStatus() {
        log("dumpStatus: mCurrentRat = " + mCurrentRat + ",mLteRegStatem = "
                + mLteRegState + ", mNlteRegState = " + mCdmaRegState
                + ", mIsDuringIrat = " + mIsDuringIrat);
    }

    protected abstract void log(String s);

    protected abstract void loge(String s);

    /**
     * Interface definition for a callback to be invoked when IRAT event
     * happens.
     */
    public interface OnIratEventListener {
        /**
         * Called when IRAT started.
         * @param info The IRAT event information.
         */
        void onIratStarted(Object info);

        /**
         * Called when IRAT ended.
         * @param info The IRAT event information.
         */
        void onIratEnded(Object info);
    }
}
