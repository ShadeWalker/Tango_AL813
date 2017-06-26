package com.mediatek.internal.telephony.ltedc.svlte;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.Rlog;

import com.android.internal.telephony.PhoneBase;
import com.mediatek.internal.telephony.ltedc.LteDcPhoneProxy;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteRatController.RoamingMode;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteRatController.SvlteRatMode;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteRatController.SvlteRatModeChangedListener;

/**
 * SvlteRoamingController used to international roaming.
 * @hide
 */
public class SvlteRoamingController {
    private static final boolean DEBUG = true;
    private static final String LOG_TAG = "[Svlte][SvlteRoamingController]";

    private static final Object mLock = new Object();

    private LteDcPhoneProxy mLteDcPhoneProxys[];
    private IrSwitchingController mIrSwitchingController[];

    private static SvlteRoamingController sInstance;

    private SvlteRoamingController(LteDcPhoneProxy[] lteDcPhoneProxys) {
        int len = lteDcPhoneProxys != null ? lteDcPhoneProxys.length : 0;
        logd("SvlteRoamingController, constructor lteDcPhoneProxys=" + lteDcPhoneProxys
                + "lteDcPhoneProxys.length=" + len);
        mLteDcPhoneProxys = new LteDcPhoneProxy[lteDcPhoneProxys.length];
        mIrSwitchingController = new IrSwitchingController[lteDcPhoneProxys.length];
        for (int i = 0; i < mLteDcPhoneProxys.length; i++) {
            mLteDcPhoneProxys[i] = lteDcPhoneProxys[i];
            createIrSwitchingController(i);
        }
    }

    /**
     * Make the instance of the international roaming controller.
     * @param lteDcPhoneProxys the lteDcPhoneProxy.
     * @return the instance.
     */
    public static SvlteRoamingController make(LteDcPhoneProxy[] lteDcPhoneProxys) {
        synchronized (mLock) {
            if (sInstance != null) {
                throw new RuntimeException(
                        "SvlteRoamingController.make() should only be called once");
            }
            sInstance = new SvlteRoamingController(lteDcPhoneProxys);
            return sInstance;
        }
    }

    /**
     * @return the single instance of SvlteRoamingController.
     */
    public static SvlteRoamingController getInstance() {
        return sInstance;
    }

    /**
     * return IrSwitchingController instance.
     * @param index The index.
     */
    public void createIrSwitchingController(int index) {
        mIrSwitchingController[index] = IrSwitchingFactory.create(mLteDcPhoneProxys[index], index);
    }

    /**
     * IR switching factory.
     */
    private static class IrSwitchingFactory {
        /**
         * @param lteDcPhoneProxy The lte phone proxy.
         * @param phoneProxyIndex The lte phone proxy index.
         * @return The instance IrSwitchingController.
         */
        public static IrSwitchingController create(LteDcPhoneProxy lteDcPhoneProxy,
                int phoneProxyIndex) {
            switch (phoneProxyIndex) {
                case 0:
                    return new IrSwitchingControllerPhoneProxy1(lteDcPhoneProxy, phoneProxyIndex);
                case 1:
                    return new IrSwitchingControllerPhoneProxy2(lteDcPhoneProxy, phoneProxyIndex);
                default:
                    break;
            }
            return null;
        }
    }

    /**
     * IrSwitchingController define common behavior for the phone proxy switching.
     *
     */
    private static abstract class IrSwitchingController {
        private static final String MCC_CN_MAINLAND = "460";
        private static final String MCC_CN_MACCO = "455";
        private static final String MCC_JP = "440";
        private static final String MCC_KR = "450";

        private static final String TAG = "IrSwitchingController";

        protected LteDcPhoneProxy mLteDcPhoneProxy;
        protected int mPhoneProxyIndex;

        /**
         * Constructor.
         * @param lteDcPhoneProxys The ltedc Phone proxy array.
         * @param phoneProxyIndex The current phone proxy index.
         */
        public IrSwitchingController(LteDcPhoneProxy lteDcPhoneProxy, int phoneProxyIndex) {
            logdForController("constructor");
            mLteDcPhoneProxy = lteDcPhoneProxy;
            mPhoneProxyIndex = phoneProxyIndex;
            init();
        }

        /**
         * Initialize.
         * URC:
         * 1.1  Unsolicited Result Code: +EGMSS
         * 1.1.1.1 Description
         * This URC is used inform AP the RAT selected by GMSS procedure
         * 1.1.1.2 Format
         * Unsolicited result code
         * +EGMSS: <rat>
         * 1.1.1.3 Field
         * <rat> Integer type
         * 0: Any RAT in 3GPP2 RAT group
         * 1: Any RAT in 3GPP RAT group
         * 2: CDMA2000 1x
         * 3: CDMA2000 HRPD
         * 4: GERAN
         * 5: UTRAN
         * 6: EUTRAN
         */
        protected void init() {
            logdForController("init");
            mLteDcPhoneProxy.getSvlteRatController().registerSvlteRatModeChangedListener(
                    mRatModeChangedListener);
        }

        protected abstract void registerGmss();

        protected abstract void unregisterGmss();

        protected void processEgmssResult(Object obj) {
            AsyncResult asyncRet = (AsyncResult) obj;
            if (asyncRet.exception == null && asyncRet.result != null) {
                int[] urcResults = (int[]) asyncRet.result;
                logdForController("processEgmssResult, urcResults=" + urcResults);
                if (urcResults != null) {
                    logdForController("processEgmssResult, urcResults.length="
                            + urcResults.length);
                    if (urcResults.length >= 3) {
                        RoamingMode targetMode = getRoamingModeByMcc(""+urcResults[1]);
                        logdForController("processEgmssResult, GMSS report code"
                                + " urcResults[0]=" + urcResults[0]
                                + ", urcResult[1]=" + urcResults[1]
                                + ", urcResults[2]=" + urcResults[2]
                                + ", targetMode=" + targetMode);
                        if (targetMode != RoamingMode.ROAMING_MODE_UNKNOWN) {
                            setRoamingMode(targetMode, null);
                        }
                    }
                }
            } else {
                logdForController("processEgmssResult, asyncRet.exception=" + asyncRet.exception
                        + " asyncRet.result=" + asyncRet.result);
            }
        }

        protected void setRoamingMode(RoamingMode targetMode, Message response) {
            mLteDcPhoneProxy.getSvlteRatController().setRoamingMode(targetMode,
                    response);
        }

        private RoamingMode getRoamingModeByMcc(String mcc) {
            RoamingMode roamingMode = RoamingMode.ROAMING_MODE_UNKNOWN;
            if (mcc != null) {
                if (SystemProperties.get("ro.mtk_svlte_lcg_support", "0").equals("1")) {
                    logdForController("checking JPKR roaming mode");
                    if (mcc.startsWith(MCC_JP) || mcc.startsWith(MCC_KR)) {
                        roamingMode = RoamingMode.ROAMING_MODE_JPKR_CDMA;
                    }
                }

                if (roamingMode == RoamingMode.ROAMING_MODE_UNKNOWN) { // still not yet set.
                    logdForController("checking normal roaming mode");
                    if (mcc.startsWith(MCC_CN_MAINLAND) || mcc.startsWith(MCC_CN_MACCO)) {
                        roamingMode = RoamingMode.ROAMING_MODE_HOME;
                    } else {
                        roamingMode = RoamingMode.ROAMING_MODE_NORMAL_ROAMING;
                    }
                }
            }

            logdForController("getRoamingModeByMcc, mcc=" + mcc + " roamingMode: " + roamingMode);

            return roamingMode;
        }

        private SvlteRatModeChangedListener mRatModeChangedListener =
                new SvlteRatModeChangedListener() {
            @Override
            public void onSvlteRatModeChangeStarted(SvlteRatMode curMode, SvlteRatMode newMode) {
                if ((curMode != SvlteRatMode.SVLTE_RAT_MODE_3G &&
                        newMode == SvlteRatMode.SVLTE_RAT_MODE_3G) ||
                        (curMode == SvlteRatMode.SVLTE_RAT_MODE_3G &&
                        newMode != SvlteRatMode.SVLTE_RAT_MODE_3G)) {
                    // disable roaming controller while switching between AP-iRAT IR
                    // and MD-iRAT IR
                    logdForController("onSvlteRatModeChangeStarted, curMode= " + curMode
                            + " newMode=" + newMode);
                    unregisterGmss();
                }
            }

            @Override
            public void onSvlteEctModeChangeDone(SvlteRatMode curMode, SvlteRatMode newMode) {
                // do nothing in current design.
            }

            @Override
            public void onSvlteRatModeChangeDone(SvlteRatMode preMode, SvlteRatMode curMode) {
                if (preMode == SvlteRatMode.SVLTE_RAT_MODE_3G &&
                        curMode != SvlteRatMode.SVLTE_RAT_MODE_3G) {
                    logdForController("onSvlteRatModeChangeDone, preMode= " + preMode
                            + " curMode=" + curMode);
                    registerGmss();
                }
            }

            @Override
            public void onRoamingModeChange(RoamingMode preMode, RoamingMode curMode) {
            }
        };

        protected void logdForController(String msg) {
            logd(TAG + " " + msg);
        }
    }

    /**
     * Ir Switching Controller for PhoneProxy1.
     *
     */
    private static class IrSwitchingControllerPhoneProxy1 extends IrSwitchingController {
        private static final String TAG = "IrSwitchingControllerPhoneProxy1";
        private static final int EVENT_GMSS_RAT_CHANGED_1 = 101;

        private Handler mIRHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                logdForController("mIRHandler--handleMessage, " + msg);
                switch(msg.what) {
                case EVENT_GMSS_RAT_CHANGED_1:
                    processEgmssResult(msg.obj);
                    break;
                default:
                    break;
                }
            }
        };

        /**
         * Constructor.
         * @param lteDcPhoneProxys The ltedc Phone proxy array.
         * @param phoneProxyIndex The currrent phone proxy index.
         */
        public IrSwitchingControllerPhoneProxy1(LteDcPhoneProxy lteDcPhoneProxy,
                int phoneProxyIndex) {
            super(lteDcPhoneProxy, phoneProxyIndex);
            registerGmss();
        }

        @Override
        protected void registerGmss() {
            logdForController("registerGmss, mIRHandler=" + mIRHandler);
            mLteDcPhoneProxy.getLtePhone().mCi.registerForGmssRatChanged(mIRHandler,
                    EVENT_GMSS_RAT_CHANGED_1, null);
        }

        @Override
        protected void unregisterGmss() {
            logdForController("unregisterGmss, mIRHandler=" + mIRHandler);
            mLteDcPhoneProxy.getLtePhone().mCi.unregisterForGmssRatChanged(mIRHandler);
        }

        protected void logdForController(String msg) {
            logd(TAG + " " + msg);
        }
    }

    /**
     * Ir Switching Controller for PhoneProxy2.
     *
     */
    private static class IrSwitchingControllerPhoneProxy2 extends IrSwitchingController {
        private static final String TAG = "IrSwitchingControllerPhoneProxy2";
        private static final int EVENT_GMSS_RAT_CHANGED_2 = 201;

        private Handler mIRHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                logdForController("mIRHandler--handleMessage, " + msg);
                switch(msg.what) {
                case EVENT_GMSS_RAT_CHANGED_2:
                    processEgmssResult(msg.obj);
                    break;
                default:
                    break;
                }
            }
        };

        /**
         * Constructor.
         * @param lteDcPhoneProxys The ltedc Phone proxy array.
         * @param phoneProxyIndex The currrent phone proxy index.
         */
        public IrSwitchingControllerPhoneProxy2(LteDcPhoneProxy lteDcPhoneProxy,
                int phoneProxyIndex) {
            super(lteDcPhoneProxy, phoneProxyIndex);
            registerGmss();
        }

        @Override
        protected void registerGmss() {
            logdForController("registerGmss, mIRHandler=" + mIRHandler);
            mLteDcPhoneProxy.getLtePhone().mCi.registerForGmssRatChanged(mIRHandler,
                    EVENT_GMSS_RAT_CHANGED_2, null);
        }

        @Override
        protected void unregisterGmss() {
            logdForController("registerGmss, mIRHandler=" + mIRHandler);
            mLteDcPhoneProxy.getLtePhone().mCi.unregisterForGmssRatChanged(mIRHandler);
        }

        protected void logdForController(String msg) {
            logd(TAG + " " + msg);
        }
    }

    private static void logd(String msg) {
        if (DEBUG) {
            Rlog.d(LOG_TAG, msg);
        }
    }
}
