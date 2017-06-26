
package com.mediatek.internal.telephony.ltedc.svlte;

import com.mediatek.internal.telephony.ltedc.LteDcConstants;

import android.os.Message;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.ServiceState;

/**
 * MD IRAT data switch helper class.
 * @hide
 */
public class MdIratDataSwitchHelper extends IratDataSwitchHelper {
    private static final String PROPERTY_4G_SIM = "persist.radio.simswitch";

    /**
     * Create MD IRAT data switch helper.
     * @param svltePhoneProxy Instance of SvltePhoneProxy.
     */
    public MdIratDataSwitchHelper(SvltePhoneProxy svltePhoneProxy) {
        super(svltePhoneProxy);
    }

    @Override
    protected void onCdmaDataAttached() {
        notifyDataConnectionAttached();
    }

    @Override
    protected void onLteDataAttached() {
        notifyDataConnectionAttached();
    }

    @Override
    protected void onCdmaDataDetached() {
        notifyDataConnectionDetached();
    }

    @Override
    protected void onLteDataDetached() {
        notifyDataConnectionDetached();
    }

    @Override
    protected void onCdmaDataAllowUrc() {
        log("notifyDataAllowed from CDMA: mPsServiceType = " + mPsServiceType);
        mDataAllowedRegistrants.notifyRegistrants();
    }

    @Override
    protected void onGsmDataAllowUrc() {
        log("notifyDataAllowed from GSM: mPsServiceType = " + mPsServiceType);
        mDataAllowedRegistrants.notifyRegistrants();
    }

    @Override
    protected void onCdmaSetDataAllowedDone() {
    }

    @Override
    protected void onGsmSetDataAllowedDone() {
    }

    @Override
    public void setDataAllowed(boolean allowed, Message result) {
        log("setDataAllowed: allowed = " + allowed);
        if (mPsServiceType == LteDcConstants.PS_SERVICE_ON_CDMA) {

            int capabilityPhoneId = SystemProperties.getInt(PROPERTY_4G_SIM, 0) - 1;
            int curPhoneId = SvlteUtils.getSvltePhoneIdByPhoneId(mSvltePhoneProxy.getPhoneId());
            log("CapabilityPhoneId = " + capabilityPhoneId + " CurPhoneId = " + curPhoneId);
            /// M: When in LWG+C or C+LWG mode, there is no need to setDataAllowed for L.
            if (SvlteUtils.isActiveSvlteMode(curPhoneId) && curPhoneId == capabilityPhoneId) {
                mLteCi.setDataAllowed(allowed, null);
            }

            mCdmaCi.setDataAllowed(allowed, result);
        } else {
            mLteCi.setDataAllowed(allowed, result);
            mCdmaCi.setDataAllowed(allowed, null);
        }

        // Notify data attached to trigger CDMA switch flow, since CDMA is
        // register on network already and not change happens to trigger attach
        // event, it will do nothing if PS is not registered.
        // TODO: improve this for C+G.
        if (allowed) {
            notifyDataConnectionAttached();
        }
    }

    @Override
    public void syncAndNotifyAttachState() {
        notifyDataConnectionAttached();
    }

    private void notifyDataConnectionAttached() {
        log("notifyDataConnectionAttached: mPsServiceType = " + mPsServiceType);
        if (mPsServiceType == LteDcConstants.PS_SERVICE_ON_CDMA) {
            if (getCurrentDataConnectionState(mCdmaPhone) == ServiceState.STATE_IN_SERVICE) {
                mAttachedRegistrants.notifyRegistrants();
            }
        } else if (mPsServiceType == LteDcConstants.PS_SERVICE_ON_LTE) {
            if (getCurrentDataConnectionState(mLtePhone) == ServiceState.STATE_IN_SERVICE) {
                mAttachedRegistrants.notifyRegistrants();
            }
        } else if (mPsServiceType == LteDcConstants.PS_SERVICE_UNKNOWN) {
            if (getCurrentDataConnectionState(mLtePhone) == ServiceState.STATE_IN_SERVICE
                    && getCurrentDataConnectionState(mCdmaPhone) == ServiceState.STATE_IN_SERVICE) {
                mAttachedRegistrants.notifyRegistrants();
            }
        }
    }

    private void notifyDataConnectionDetached() {
        log("notifyDataConnectionDetached: mPsServiceType = " + mPsServiceType);
        if (mPsServiceType == LteDcConstants.PS_SERVICE_ON_CDMA) {
            if (getCurrentDataConnectionState(mCdmaPhone) != ServiceState.STATE_IN_SERVICE) {
                mDetachedRegistrants.notifyRegistrants();
            }
        } else if (mPsServiceType == LteDcConstants.PS_SERVICE_ON_LTE) {
            if (getCurrentDataConnectionState(mLtePhone) != ServiceState.STATE_IN_SERVICE) {
                mDetachedRegistrants.notifyRegistrants();
            }
        } else if (mPsServiceType == LteDcConstants.PS_SERVICE_UNKNOWN) {
            if (getCurrentDataConnectionState(mLtePhone) != ServiceState.STATE_IN_SERVICE
                    && getCurrentDataConnectionState(mCdmaPhone) == ServiceState.STATE_IN_SERVICE) {
                mDetachedRegistrants.notifyRegistrants();
            }
        }
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, "[" + mSvltePhoneProxy.getPhoneId() + "] " + s);
    }

    protected void loge(String s) {
        Rlog.e(LOG_TAG, "[" + mSvltePhoneProxy.getPhoneId() + "] " + s);
    }
}
