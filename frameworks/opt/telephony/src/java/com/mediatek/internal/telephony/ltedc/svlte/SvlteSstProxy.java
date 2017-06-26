package com.mediatek.internal.telephony.ltedc.svlte;

import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.telephony.ServiceState;
import android.util.Log;
import android.util.Pair;

import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.TelephonyProperties;
import com.mediatek.internal.telephony.ltedc.LteDcPhoneProxy;

/**
 * The SVLTE Service State Tracker Proxy.
 *
 */
public class SvlteSstProxy extends Handler {
    private static final String LOG_TAG_PHONE = "PHONE";
    private static final String TAG_PREFIX = "[SvlteSstProxy]";
    private static final boolean DBG = true;

    private static final Object mLock = new Object();
    private static SvlteSstProxy sInstance;

    private LteDcPhoneProxy mLteDcPhoneProxy;
    private ServiceStateTracker mCdmaSst;
    private ServiceStateTracker mLteSst;

    private static final int EVENT_DATA_ATTACHED = 100;
    private static final int EVENT_DATA_DETACHED = 101;
    private static final int EVENT_PS_RESTRICT_ENABLED = 102;
    private static final int EVENT_PS_RESTRICT_DISABLED = 103;
    private static final int EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED = 104;
    private static final int EVENT_DATA_ATTACHED_LTE = 105;
    private static final int EVENT_DATA_DETACHED_LTE = 106;
    private static final int EVENT_PS_RESTRICT_ENABLED_LTE = 107;
    private static final int EVENT_PS_RESTRICT_DISABLED_LTE = 108;
    private static final int EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED_LTE = 109;

    private RegistrantList mAttachedRegistrants = new RegistrantList();
    private RegistrantList mDetachedRegistrants = new RegistrantList();
    private RegistrantList mPsRestrictEnabledRegistrants = new RegistrantList();
    private RegistrantList mPsRestrictDisabledRegistrants = new RegistrantList();
    private RegistrantList mDataRegStateOrRatChangedRegistrants = new RegistrantList();

    private boolean mEnabled = true;

    private SvlteSstProxy(LteDcPhoneProxy lteDcPhoneProxy) {
        mLteDcPhoneProxy = lteDcPhoneProxy;
        mCdmaSst = ((PhoneBase) mLteDcPhoneProxy.getNLtePhone()).getServiceStateTracker();
        mLteSst = ((PhoneBase) mLteDcPhoneProxy.getLtePhone()).getServiceStateTracker();
        mCdmaSst.registerForDataConnectionAttached(this, EVENT_DATA_ATTACHED, null);
        mCdmaSst.registerForDataConnectionDetached(this, EVENT_DATA_DETACHED, null);
        mCdmaSst.registerForPsRestrictedEnabled(this, EVENT_PS_RESTRICT_ENABLED, null);
        mCdmaSst.registerForPsRestrictedDisabled(this, EVENT_PS_RESTRICT_DISABLED, null);
        mCdmaSst.registerForDataRegStateOrRatChanged(this,
                EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED, null);
        mLteSst.registerForDataConnectionAttached(this, EVENT_DATA_ATTACHED_LTE, null);
        mLteSst.registerForDataConnectionDetached(this, EVENT_DATA_DETACHED_LTE, null);
        mLteSst.registerForPsRestrictedEnabled(this, EVENT_PS_RESTRICT_ENABLED_LTE, null);
        mLteSst.registerForPsRestrictedDisabled(this, EVENT_PS_RESTRICT_DISABLED_LTE, null);
        mLteSst.registerForDataRegStateOrRatChanged(this,
                EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED_LTE, null);
    }

    /**
     * @return the single instance of RatController.
     */
    public static SvlteSstProxy getInstance() {
        synchronized (mLock) {
            if (sInstance == null) {
                throw new RuntimeException(
                        "SvlteSstProxy.getInstance can't be called before make()");
            }
            return sInstance;
        }
    }

    /**
     * Init the SvlteSstProxy.
     * @param lteDcPhoneProxy the LteDcPhoneProxy object.
     * @return The instance of SvlteSstProxy
     */
    public static SvlteSstProxy make(LteDcPhoneProxy lteDcPhoneProxy) {
        synchronized (mLock) {
            if (sInstance != null) {
                return sInstance;
                //throw new RuntimeException("SvlteSstProxy.make() should only be called once");
            }
            sInstance = new SvlteSstProxy(lteDcPhoneProxy);
            return sInstance;
        }
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setEnabled(boolean mEnabled) {
        this.mEnabled = mEnabled;
    }

    private boolean isLteMode() {
        return mLteDcPhoneProxy.getPsPhone().getPhoneType() != PhoneConstants.PHONE_TYPE_CDMA;
    }

    @Override
    public void handleMessage(Message msg) {
        logd("handleMessage: " + msg.what);
        if (!isLteMode()) {
            switch (msg.what) {
            case EVENT_DATA_ATTACHED:
                notifyForDataConnectionAttached();
                break;
            case EVENT_DATA_DETACHED:
                notifyForDataConnectionDetached();
                break;
            case EVENT_PS_RESTRICT_ENABLED:
                notifyForPsRestrictedEnabled();
                break;
            case EVENT_PS_RESTRICT_DISABLED:
                notifyForPsRestrictedDisabled();
                break;
            case EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED:
                notifyForDataRegStateOrRatChanged();
                break;
            default:
                break;
            }
        } else {
            switch (msg.what) {
            case EVENT_DATA_ATTACHED_LTE:
                notifyForDataConnectionAttached();
                break;
            case EVENT_DATA_DETACHED_LTE:
                notifyForDataConnectionDetached();
                break;
            case EVENT_PS_RESTRICT_ENABLED_LTE:
                notifyForPsRestrictedEnabled();
                break;
            case EVENT_PS_RESTRICT_DISABLED_LTE:
                notifyForPsRestrictedDisabled();
                break;
            case EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED_LTE:
                notifyForDataRegStateOrRatChanged();
                break;
            default:
                break;
            }
        }
    }

    /**
     * Register For Data Connection Attached.
     * @param h the hander.
     * @param what the what.
     * @param obj the object.
     */
    public void registerForDataConnectionAttached(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mAttachedRegistrants.add(r);
        if (mEnabled && ((isLteMode() ? mLteSst : mCdmaSst).getCurrentDataConnectionState()
                == ServiceState.STATE_IN_SERVICE)) {
            r.notifyRegistrant();
        }
    }

    /**
     * Unregister For Data Connection Attached.
     * @param h the hander.
     */
    public void unregisterForDataConnectionAttached(Handler h) {
        mAttachedRegistrants.remove(h);
    }

    private void notifyForDataConnectionAttached() {
        if (mEnabled) {
            mAttachedRegistrants.notifyRegistrants();
        }
    }

    /**
     * Register For Data Connection Detached.
     * @param h the hander.
     * @param what the what.
     * @param obj the object.
     */
    public void registerForDataConnectionDetached(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mDetachedRegistrants.add(r);
        if (mEnabled && ((isLteMode() ? mLteSst : mCdmaSst).getCurrentDataConnectionState()
                != ServiceState.STATE_IN_SERVICE)) {
            r.notifyRegistrant();
        }
    }

    /**
     * Unregister For Data Connection Detached.
     * @param h the hander.
     */
    public void unregisterForDataConnectionDetached(Handler h) {
        mDetachedRegistrants.remove(h);
    }

    private void notifyForDataConnectionDetached() {
        if (mEnabled) {
            mDetachedRegistrants.notifyRegistrants();
        }
    }

    /**
     * Register For PS Restricted Enabled.
     * @param h the hander.
     * @param what the what.
     * @param obj the object.
     */
    public void registerForPsRestrictedEnabled(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mPsRestrictEnabledRegistrants.add(r);
        if (mEnabled && ((isLteMode() ? mLteSst : mCdmaSst).mRestrictedState.isPsRestricted())) {
            r.notifyRegistrant();
        }
    }

    /**
     * Unregister For PS Restricted Enabled.
     * @param h the hander.
     */
    public void unregisterForPsRestrictedEnabled(Handler h) {
        mPsRestrictEnabledRegistrants.remove(h);
    }

    private void notifyForPsRestrictedEnabled() {
        if (mEnabled) {
            mPsRestrictEnabledRegistrants.notifyRegistrants();
        }
    }

    /**
     * Register For PS Restricted Disabled.
     * @param h the hander.
     * @param what the what.
     * @param obj the object.
     */
    public void registerForPsRestrictedDisabled(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mPsRestrictDisabledRegistrants.add(r);
        if (mEnabled && ((isLteMode() ? mLteSst : mCdmaSst).mRestrictedState.isPsRestricted())) {
            r.notifyRegistrant();
        }
    }

    /**
     * Unregister For PS Restricted Disabled.
     * @param h the hander.
     */
    public void unregisterForPsRestrictedDisabled(Handler h) {
        mPsRestrictDisabledRegistrants.remove(h);
    }

    private void notifyForPsRestrictedDisabled() {
        if (mEnabled) {
            mPsRestrictDisabledRegistrants.notifyRegistrants();
        }
    }

    /**
     * Register For Data RegState Or Rat Changed.
     * @param h the hander.
     * @param what the what.
     * @param obj the object.
     */
    public void registerForDataRegStateOrRatChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mDataRegStateOrRatChangedRegistrants.add(r);
        notifyForDataRegStateOrRatChanged();
    }

    /**
     * Unregister For Data RegState Or Rat Changed.
     * @param h the hander.
     */
    public void unregisterForDataRegStateOrRatChanged(Handler h) {
        mDataRegStateOrRatChangedRegistrants.remove(h);
    }

    private void notifyForDataRegStateOrRatChanged() {
        int rat = (isLteMode() ? mLteSst : mCdmaSst).mSS.getRilDataRadioTechnology();
        int drs = (isLteMode() ? mLteSst : mCdmaSst).mSS.getDataRegState();
        if (DBG) {
            logd("notifyDataRegStateRilRadioTechnologyChanged: drs=" + drs + " rat=" + rat);
        }
        SystemProperties.set(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE,
                ServiceState.rilRadioTechnologyToString(rat));
        if (mEnabled) {
            mDataRegStateOrRatChangedRegistrants.notifyResult(new Pair<Integer, Integer>(drs, rat));
        }
    }

    private static void logd(String msg) {
        if (DBG) {
            Log.d(LOG_TAG_PHONE, TAG_PREFIX + msg);
        }
    }
}
