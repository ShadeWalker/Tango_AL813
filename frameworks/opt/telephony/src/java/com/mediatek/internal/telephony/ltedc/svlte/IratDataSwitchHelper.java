
package com.mediatek.internal.telephony.ltedc.svlte;

import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.PhoneBase;

import com.mediatek.internal.telephony.ltedc.LteDcConstants;
import com.mediatek.internal.telephony.uicc.SvlteUiccUtils;

/**
 * IRAT data switch helper class.
 * @hide
 */
public abstract class IratDataSwitchHelper extends Handler {
    protected static final String LOG_TAG = "[IRAT_DSH]";

    protected static final int EVENT_CDMA_DATA_ATTACHED = 0;
    protected static final int EVENT_LTE_DATA_ATTACHED = 1;
    protected static final int EVENT_CDMA_DATA_DETACHED = 2;
    protected static final int EVENT_LTE_DATA_DETACHED = 3;
    protected static final int EVENT_CDMA_DATA_ALLOW_URC = 4;
    protected static final int EVENT_LTE_DATA_ALLOW_URC = 5;
    protected static final int EVENT_CDMA_SET_DATA_ALLOW_DONE = 6;
    protected static final int EVENT_LTE_SET_DATA_ALLOW_DONE = 7;

    protected CommandsInterface mLteCi;
    protected CommandsInterface mCdmaCi;
    protected PhoneBase mLtePhone;
    protected PhoneBase mCdmaPhone;
    protected SvltePhoneProxy mSvltePhoneProxy;

    protected int mPsServiceType;

    protected Message mDataAllowResponseMessage;

    protected RegistrantList mDataAllowedRegistrants = new RegistrantList();
    protected RegistrantList mAttachedRegistrants = new RegistrantList();
    protected RegistrantList mDetachedRegistrants = new RegistrantList();

    /**
     * Create AP IRAT data switch helper.
     * @param svltePhoneProxy Instance of SvltePhoneProxy.
     */
    public IratDataSwitchHelper(SvltePhoneProxy svltePhoneProxy) {
        mSvltePhoneProxy = svltePhoneProxy;
        mLtePhone = mSvltePhoneProxy.getLtePhone();
        mCdmaPhone = mSvltePhoneProxy.getNLtePhone();
        mLteCi = mLtePhone.mCi;
        mCdmaCi = mCdmaPhone.mCi;

        registerForAllEvents();
    }

    /**
     * Dispose the component.
     */
    public void dispose() {
        unregisterForAllEvents();
    }

    private void registerForAllEvents() {
        log("registerForAllEvents: mPsServiceType = " + mPsServiceType);
        mCdmaPhone.getServiceStateTracker().registerForDataConnectionAttached(
                this, EVENT_CDMA_DATA_ATTACHED, null);
        mLtePhone.getServiceStateTracker().registerForDataConnectionAttached(
                this, EVENT_LTE_DATA_ATTACHED, null);
        mCdmaPhone.getServiceStateTracker().registerForDataConnectionDetached(
                this, EVENT_CDMA_DATA_DETACHED, null);
        mLtePhone.getServiceStateTracker().registerForDataConnectionDetached(
                this, EVENT_LTE_DATA_DETACHED, null);
        mCdmaCi.registerSetDataAllowed(this, EVENT_CDMA_DATA_ALLOW_URC, null);
        mLteCi.registerSetDataAllowed(this, EVENT_LTE_DATA_ALLOW_URC, null);
    }

    private void unregisterForAllEvents() {
        log("unregisterForAllEvents: mPsServiceType = " + mPsServiceType);
        mCdmaPhone.getServiceStateTracker()
                .unregisterForDataConnectionAttached(this);
        mLtePhone.getServiceStateTracker().unregisterForDataConnectionAttached(
                this);
        mCdmaPhone.getServiceStateTracker()
                .unregisterForDataConnectionDetached(this);
        mLtePhone.getServiceStateTracker().unregisterForDataConnectionDetached(
                this);
        mCdmaCi.unregisterSetDataAllowed(this);
        mLteCi.unregisterSetDataAllowed(this);
    }

    /**
     * Set PS service type.
     * @param psServiceType PS service type.
     */
    public void setPsServiceType(int psServiceType) {
        log("setPsServiceType: psServiceType = " + psServiceType
                + ", mPsServiceType = " + mPsServiceType);
        mPsServiceType = psServiceType;
    }

    /**
     * Register for set data allowed URC.
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public void registerSetDataAllowed(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mDataAllowedRegistrants.add(r);
    }

    /**
     * Unregister for set data allowed.
     * @param h Handler for notification message.
     */
    public void unregisterSetDataAllowed(Handler h) {
        mDataAllowedRegistrants.remove(h);
    }

    /**
     * Register for data connection attached.
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public void registerForDataConnectionAttached(Handler h, int what,
            Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mAttachedRegistrants.add(r);

        if (mPsServiceType == LteDcConstants.PS_SERVICE_ON_CDMA) {
            if (getCurrentDataConnectionState(mCdmaPhone) == ServiceState.STATE_IN_SERVICE) {
                r.notifyRegistrant();
            }
        } else if (mPsServiceType == LteDcConstants.PS_SERVICE_ON_LTE) {
            if (getCurrentDataConnectionState(mLtePhone) == ServiceState.STATE_IN_SERVICE) {
                r.notifyRegistrant();
            }
        } else if (mPsServiceType == LteDcConstants.PS_SERVICE_UNKNOWN) {
            if (getCurrentDataConnectionState(mLtePhone) == ServiceState.STATE_IN_SERVICE
                    && getCurrentDataConnectionState(mCdmaPhone) == ServiceState.STATE_IN_SERVICE) {
                r.notifyRegistrant();
            }
        }
    }

    /**
     * Unregister for data connection attached.
     * @param h Handler for notification message.
     */
    public void unregisterForDataConnectionAttached(Handler h) {
        mAttachedRegistrants.remove(h);
    }

    /**
     * Register for data connection detached.
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public void registerForDataConnectionDetached(Handler h, int what,
            Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mDetachedRegistrants.add(r);

        if (mPsServiceType == LteDcConstants.PS_SERVICE_ON_CDMA) {
            if (getCurrentDataConnectionState(mCdmaPhone) != ServiceState.STATE_IN_SERVICE) {
                r.notifyRegistrant();
            }
        } else if (mPsServiceType == LteDcConstants.PS_SERVICE_ON_LTE) {
            if (getCurrentDataConnectionState(mLtePhone) != ServiceState.STATE_IN_SERVICE) {
                r.notifyRegistrant();
            }
        } else if (mPsServiceType == LteDcConstants.PS_SERVICE_UNKNOWN) {
            if (getCurrentDataConnectionState(mLtePhone) != ServiceState.STATE_IN_SERVICE
                    && getCurrentDataConnectionState(mCdmaPhone) == ServiceState.STATE_IN_SERVICE) {
                r.notifyRegistrant();
            }
        }
    }

    /**
     * Unregister for data connection detached.
     * @param h Handler for notification message.
     */
    public void unregisterForDataConnectionDetached(Handler h) {
        mDetachedRegistrants.remove(h);
    }

    protected int getCurrentDataConnectionState(PhoneBase phone) {
        return phone.getServiceStateTracker().getCurrentDataConnectionState();
    }

    @Override
    public void handleMessage(Message msg) {
        log("handleMessage: msg = " + msg.what);
        switch (msg.what) {
            case EVENT_CDMA_DATA_ATTACHED:
                onCdmaDataAttached();
                break;
            case EVENT_LTE_DATA_ATTACHED:
                onLteDataAttached();
                break;
            case EVENT_CDMA_DATA_DETACHED:
                onCdmaDataDetached();
                break;
            case EVENT_LTE_DATA_DETACHED:
                onLteDataDetached();
                break;
            case EVENT_CDMA_DATA_ALLOW_URC:
                // needed only if 3G only mode and default data is not at SIM2
                if (needCdmaDataAllowedUrc()) {
                    onCdmaDataAllowUrc();
                }
                break;
            case EVENT_LTE_DATA_ALLOW_URC:
                onGsmDataAllowUrc();
                break;
            case EVENT_CDMA_SET_DATA_ALLOW_DONE:
                onCdmaSetDataAllowedDone();
                break;
            case EVENT_LTE_SET_DATA_ALLOW_DONE:
                onGsmSetDataAllowedDone();
                break;
            default:
                log("Unhandled message with number: " + msg.what);
                break;
        }
    }

    protected boolean needCdmaDataAllowedUrc() {
        // this URC is needed only if 3G only mode and default data is not at SIM2

        // FIXME: To find a better way to get SVLTE card/phone slot
        final int svlteSlot = 0;

        // Lte is ON and not CT 3G card, LTE will report URC if needed
        if (mSvltePhoneProxy.getSvlteRatController().getSvlteRatMode().isLteOn()
                && !(SvlteUiccUtils.getInstance().isRuimCsim(svlteSlot)
                && !SvlteUiccUtils.getInstance().isUsimSim(svlteSlot))) {
            log("Lte is ON, LTE will report DATA_ALLOW_URC if needed");
            return false;
        }

        int cnt = TelephonyManager.getDefault().getPhoneCount();
        if (cnt >= 2) {
            String sim2 = SystemProperties.get("ril.iccid.sim2"); 
            String default_icc = SystemProperties.get("persist.radio.data.iccid");

            log("sim2 iccid:" + sim2);
            log("default_icc" + default_icc);

            if (sim2 != null && !sim2.equals("") && !sim2.equals("N/A")
             && default_icc != null && sim2.equals(default_icc)) {
                 log("LTE is disabled, need C2K report DATA_ALLOW_URC");
                 return false;
             }
        }

        log("Data SIM is unset or at SIM1, and LTE is disabled, pass it to DctController");
        return true;
    }

    /**
     * For MD IRAT, PS ATTACHED EVENT may be ahead RAT CHANGE EVENT. Resend
     * ATTACEHD event after first RAT change to fire DctController / DcTracker.
     *
     * For AP IRAT, since both LTE and CDMA are registered network at the same
     * time, when LTE off, CDMA will not report attach state, need to sync the
     * attch state if LTE modem off.
     */
    public abstract void syncAndNotifyAttachState();

    /**
     * Set data allowed, attach PS based on current phone mode.
     * @param allowed True to attach PS, false to detach.
     * @param result Response message.
     */
    public abstract void setDataAllowed(boolean allowed, Message result);

    protected abstract void onCdmaDataAttached();
    protected abstract void onLteDataAttached();
    protected abstract void onCdmaDataDetached();
    protected abstract void onLteDataDetached();
    protected abstract void onCdmaDataAllowUrc();
    protected abstract void onGsmDataAllowUrc();
    protected abstract void onCdmaSetDataAllowedDone();
    protected abstract void onGsmSetDataAllowedDone();
    protected abstract void log(String s);
    protected abstract void loge(String s);
}
