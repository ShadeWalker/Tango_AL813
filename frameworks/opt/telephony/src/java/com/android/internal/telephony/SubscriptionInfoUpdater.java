/*
* Copyright (C) 2014 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.internal.telephony;

import static android.Manifest.permission.READ_PHONE_STATE;

import android.app.ActivityManagerNative;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.uicc.IccCardProxy;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccController;
// MTK-START
import com.android.internal.telephony.uicc.SpnOverride;
// MTK-END
import android.text.TextUtils;

import com.mediatek.internal.telephony.DefaultSmsSimSettings;
import com.mediatek.internal.telephony.DefaultVoiceCallSubSettings;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteModeController;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteRatController;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;
import com.mediatek.internal.telephony.uicc.SvlteUiccUtils;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.List;
import android.telephony.PhoneNumberUtils;

/**
 *@hide
 */
public class SubscriptionInfoUpdater extends Handler {
    private static final String LOG_TAG = "SubscriptionInfoUpdater";
    private static final int PROJECT_SIM_NUM = TelephonyManager.getDefault().getPhoneCount();

    private static final int EVENT_SIM_LOCKED_QUERY_ICCID_DONE = 1;
    private static final int EVENT_GET_NETWORK_SELECTION_MODE_DONE = 2;
    private static final int EVENT_SIM_LOADED = 3;
    private static final int EVENT_SIM_ABSENT = 4;
    private static final int EVENT_SIM_LOCKED = 5;

    // MTK-START
    private static final int EVENT_RADIO_AVAILABLE = 101;
    private static final int EVENT_RADIO_UNAVAILABLE = 102;
    // For the feature SIM Hot Swap with Common Slot
    private static final int EVENT_SIM_NO_CHANGED = 103;
    private static final int EVENT_TRAY_PLUG_IN = 104;
    private static final int EVENT_SIM_PLUG_OUT = 105;

    private static final String COMMON_SLOT_PROPERTY = "ro.mtk_sim_hot_swap_common_slot";
    private static boolean mCommonSlotResetDone = false;
    // MTK-END

    private static final String ICCID_STRING_FOR_NO_SIM = "N/A";
    /**
     *  int[] sInsertSimState maintains all slots' SIM inserted status currently,
     *  it may contain 4 kinds of values:
     *    SIM_NOT_INSERT : no SIM inserted in slot i now
     *    SIM_CHANGED    : a valid SIM insert in slot i and is different SIM from last time
     *                     it will later become SIM_NEW or SIM_REPOSITION during update procedure
     *    SIM_NOT_CHANGE : a valid SIM insert in slot i and is the same SIM as last time
     *    SIM_NEW        : a valid SIM insert in slot i and is a new SIM
     *    SIM_REPOSITION : a valid SIM insert in slot i and is inserted in different slot last time
     *    positive integer #: index to distinguish SIM cards with the same IccId
     */
    public static final int SIM_NOT_CHANGE = 0;
    public static final int SIM_CHANGED    = -1;
    public static final int SIM_NEW        = -2;
    public static final int SIM_REPOSITION = -3;
    public static final int SIM_NOT_INSERT = -99;

    public static final int STATUS_NO_SIM_INSERTED = 0x00;
    public static final int STATUS_SIM1_INSERTED = 0x01;
    public static final int STATUS_SIM2_INSERTED = 0x02;
    public static final int STATUS_SIM3_INSERTED = 0x04;
    public static final int STATUS_SIM4_INSERTED = 0x08;

    // Key used to read/write the current IMSI. Updated on SIM_STATE_CHANGED - LOADED.
    public static final String CURR_SUBID = "curr_subid";

    private static Phone[] mPhone;
    private static Context mContext = null;
    private CommandsInterface[] mCis = null;
    private static IccFileHandler[] sFh = new IccFileHandler[PROJECT_SIM_NUM];
    private static String sIccId[] = new String[PROJECT_SIM_NUM];
    private static int[] sInsertSimState = new int[PROJECT_SIM_NUM];
    private SubscriptionManager mSubscriptionManager = null;
    private static int[] sIsUpdateAvailable = new int[PROJECT_SIM_NUM];
    // To prevent repeatedly update flow every time receiver SIM_STATE_CHANGE
    private static Intent sUpdateIntent = null;
    protected AtomicReferenceArray<IccRecords> mIccRecords
            = new AtomicReferenceArray<IccRecords>(PROJECT_SIM_NUM);
    private static final int sReadICCID_retry_time = 1000;
    private int mReadIccIdCount = 0;
    protected final Object mLock = new Object();

    static String[] PROPERTY_ICCID_SIM = {
        "ril.iccid.sim1",
        "ril.iccid.sim2",
        "ril.iccid.sim3",
        "ril.iccid.sim4",
    };

    private static boolean sIsC2kSupport = CdmaFeatureOptionUtils.isMtkC2KSupport();
    private static boolean sIsSvlteSupport = CdmaFeatureOptionUtils.isCdmaLteDcSupport();
    private int mOldSvlteSlotId = SvlteModeController.getActiveSvlteModeSlotId();

    // MTK-END
    public SubscriptionInfoUpdater(Context context, Phone[] phoneProxy, CommandsInterface[] ci) {
        logd("Constructor invoked");

        mContext = context;
        mPhone = phoneProxy;
        mSubscriptionManager = SubscriptionManager.from(mContext);
        mCis = ci;

        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            sIsUpdateAvailable[i] = -1;
            sIccId[i] = SystemProperties.get(PROPERTY_ICCID_SIM[i], "");
            if (sIccId[i].length() == 3) {
                logd("No SIM insert :" + i);
                if (sIsSvlteSupport) {
                    if (SvlteUiccUtils.getInstance().isHaveCard(i)
                        && !SvlteUiccUtils.getInstance().isUsimSim(i)) {
                        logd("CT 3G card is not ready, set iccid to null!");
                        sIccId[i] = null;
                    }
                }
            }
            logd("sIccId[" + i + "]:" + sIccId[i]);
        }

        if (isAllIccIdQueryDone()) {
            new Thread() {
                public void run() {
                    updateSubscriptionInfoByIccId();
                }
            } .start();
        }

        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        // MTK-START
        intentFilter.addAction("android.intent.action.ACTION_SHUTDOWN_IPO");
        intentFilter.addAction(TelephonyIntents.ACTION_COMMON_SLOT_NO_CHANGED);

        //yanqing add for HQ01340266
        //if ("OP09".equals(SystemProperties.get("ro.operator.optr", "OM"))) {
            intentFilter.addAction(Intent.ACTION_LOCALE_CHANGED);
        //}
        

        if (("OP09".equals(SystemProperties.get("ro.operator.optr"))
                && "SEGDEFAULT".equals(SystemProperties.get("ro.operator.seg")))
                || "1".equals(SystemProperties.get("ro.ct6m_support"))) {
            intentFilter.addAction(Intent.ACTION_LOCALE_CHANGED);
        }
        if (sIsSvlteSupport) {

            intentFilter.addAction(TelephonyIntents.ACTION_SET_RADIO_TECHNOLOGY_START);
            intentFilter.addAction(TelephonyIntents.ACTION_SET_RADIO_TECHNOLOGY_DONE);
        }
        // MTK-END
        mContext.registerReceiver(sReceiver, intentFilter);
        intentFilter = new IntentFilter(IccCardProxy.ACTION_INTERNAL_SIM_STATE_CHANGED);
        mContext.registerReceiver(sReceiver, intentFilter);

        for (int i = 0; i < mCis.length; i++) {
            Integer index = new Integer(i);
            mCis[i].registerForNotAvailable(this, EVENT_RADIO_UNAVAILABLE, index);
            mCis[i].registerForAvailable(this, EVENT_RADIO_AVAILABLE, index);
            if (SystemProperties.get(COMMON_SLOT_PROPERTY).equals("1")) {
                mCis[i].registerForTrayPlugIn(this, EVENT_TRAY_PLUG_IN, index);
                mCis[i].registerForSimPlugOut(this, EVENT_SIM_PLUG_OUT, index);
            }
            if (sIsSvlteSupport) {
                logd("mCis[" + i + "]:" + mCis[i]);
                logd("svlte" + i + " lte ci:" +
                    SvlteUtils.getSvltePhoneProxy(i).getLtePhone().mCi);
                logd("svlte" + i + " nlte ci:" +
                    SvlteUtils.getSvltePhoneProxy(i).getNLtePhone().mCi);
                logd("svlte" + i + " active ci:" +
                    ((PhoneBase) SvlteUtils.getSvltePhoneProxy(i).getActivePhone()).mCi);
            }
        }
    }

    private final BroadcastReceiver sReceiver = new  BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            logd("[Receiver]+");
            String action = intent.getAction();
            logd("Action: " + action);

            int slotId = 0;
            String simStatus = "";

            if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED) ||
                action.equals(IccCardProxy.ACTION_INTERNAL_SIM_STATE_CHANGED)) {
                slotId = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                        SubscriptionManager.INVALID_SIM_SLOT_INDEX);
                logd("slotId: " + slotId);
                if (slotId == SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                    return;
                }

                simStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                logd("simStatus: " + simStatus);
            }

            if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simStatus)) {
                    sendMessage(obtainMessage(EVENT_SIM_ABSENT, slotId, -1));
                } else {
                    logd("Ignoring simStatus: " + simStatus);
                }
            } else if (action.equals(IccCardProxy.ACTION_INTERNAL_SIM_STATE_CHANGED)) {
                if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(simStatus)) {
                    String reason = intent.getStringExtra(
                        IccCardConstants.INTENT_KEY_LOCKED_REASON);
                    sendMessage(obtainMessage(EVENT_SIM_LOCKED, slotId, -1, reason));
                } else if (IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(simStatus)) {
                    sendMessage(obtainMessage(EVENT_SIM_LOADED, slotId, -1));
                    mReadIccIdCount = 10;
                } else {
                    logd("Ignoring simStatus: " + simStatus);
                }
            // MTK-START
            } else if (action.equals("android.intent.action.ACTION_SHUTDOWN_IPO")) {
                for (int i = 0; i < PROJECT_SIM_NUM; i++) {
                    clearIccId(i);
                }
                mSubscriptionManager.clearSubscriptionInfo();
                if (sUpdateIntent != null) {
                    mContext.removeStickyBroadcast(sUpdateIntent);
                }
            } else if (action.equals(Intent.ACTION_LOCALE_CHANGED)) {
                int[] subIdList = mSubscriptionManager.getActiveSubscriptionIdList();
                for (int subId : subIdList) {
                    updateSubName(subId);
                }
            } else if (action.equals(TelephonyIntents.ACTION_COMMON_SLOT_NO_CHANGED)) {
                slotId = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                        SubscriptionManager.INVALID_SIM_SLOT_INDEX);
                logd("[Common Slot] NO_CHANTED, slotId: " + slotId);
                sendMessage(obtainMessage(EVENT_SIM_NO_CHANGED, slotId, -1));
            } else if (action.equals(TelephonyIntents.ACTION_SET_RADIO_TECHNOLOGY_START)) {
                int svlteSlotId = SvlteModeController.getActiveSvlteModeSlotId();
                logd("oldSvlteSlotId:" + mOldSvlteSlotId + ", svlteSlotId:" + svlteSlotId);
                if (mOldSvlteSlotId != svlteSlotId) {
                    for (int i = 0; i < mCis.length; i++) {
                        logd("svlte" + i + " lte ci:" +
                            SvlteUtils.getSvltePhoneProxy(i).getLtePhone().mCi);
                        logd("svlte" + i + " nlte ci:" +
                            SvlteUtils.getSvltePhoneProxy(i).getNLtePhone().mCi);
                        logd("svlte" + i + " active ci:" +
                            ((PhoneBase) SvlteUtils.getSvltePhoneProxy(i).getActivePhone()).mCi);
                    }
                    for (int i = 0; i < mCis.length; i++) {
                        sIsUpdateAvailable[i] = 0;
                        mCis[i] = ((PhoneBase) SvlteUtils.getSvltePhoneProxy(i).
                                        getActivePhone()).mCi;
                        mCis[i].unregisterForNotAvailable(SubscriptionInfoUpdater.this);
                        mCis[i].unregisterForAvailable(SubscriptionInfoUpdater.this);
                        logd("unregister for mCis[" + i + "]:" + mCis[i]);
                    }
                }
            } else if (action.equals(TelephonyIntents.ACTION_SET_RADIO_TECHNOLOGY_DONE)) {
                int svlteSlotId = SvlteModeController.getActiveSvlteModeSlotId();
                logd("oldSvlteSlotId:" + mOldSvlteSlotId + ", svlteSlotId:" + svlteSlotId);
                if (mOldSvlteSlotId != svlteSlotId) {
                    for (int i = 0; i < mCis.length; i++) {
                        mCis[i] = ((PhoneBase) SvlteUtils.getSvltePhoneProxy(i).
                                        getActivePhone()).mCi;
                        Integer index = new Integer(i);
                        mCis[i].registerForNotAvailable(SubscriptionInfoUpdater.this,
                                    EVENT_RADIO_UNAVAILABLE, index);
                        mCis[i].registerForAvailable(SubscriptionInfoUpdater.this,
                                    EVENT_RADIO_AVAILABLE, index);
                        logd("mCis[" + i + "]:" + mCis[i]);
                        logd("svlte" + i + " lte ci:" +
                            SvlteUtils.getSvltePhoneProxy(i).getLtePhone().mCi);
                        logd("svlte" + i + " nlte ci:" +
                            SvlteUtils.getSvltePhoneProxy(i).getNLtePhone().mCi);
                        logd("svlte" + i + " active ci:" +
                            ((PhoneBase) SvlteUtils.getSvltePhoneProxy(i).getActivePhone()).mCi);
                    }
                    mOldSvlteSlotId = svlteSlotId;
                }
            }
            // MTK-END
            logd("[Receiver]-");
        }
    };

    private boolean isAllIccIdQueryDone() {
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            if (sIccId[i] == null || sIccId[i].equals("")) {
                logd("Wait for SIM" + (i + 1) + " IccId");
                return false;
            }
        }
        logd("All IccIds query complete");

        return true;
    }

    public void setDisplayNameForNewSub(String newSubName, int subId, int newNameSource) {
        SubscriptionInfo subInfo = mSubscriptionManager.getActiveSubscriptionInfo(subId);
        if (subInfo != null) {
            // overwrite SIM display name if it is not assigned by user
            int oldNameSource = subInfo.getNameSource();
            CharSequence oldSubName = subInfo.getDisplayName();
            logd("[setDisplayNameForNewSub] subId = " + subInfo.getSubscriptionId()
                    + ", oldSimName = " + oldSubName + ", oldNameSource = " + oldNameSource
                    + ", newSubName = " + newSubName + ", newNameSource = " + newNameSource);
            if (oldSubName == null ||
                (oldNameSource ==
                    SubscriptionManager.NAME_SOURCE_DEFAULT_SOURCE && newSubName != null) ||
                (oldNameSource == SubscriptionManager.NAME_SOURCE_SIM_SOURCE && newSubName != null
                        && !newSubName.equals(oldSubName))) {
                mSubscriptionManager.setDisplayName(newSubName, subInfo.getSubscriptionId(),
                        newNameSource);
            }
        } else {
            logd("SUB" + (subId + 1) + " SubInfo not created yet");
        }
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_SIM_LOCKED_QUERY_ICCID_DONE: {
                AsyncResult ar = (AsyncResult) msg.obj;
                QueryIccIdUserObj uObj = (QueryIccIdUserObj) ar.userObj;
                int slotId = uObj.slotId;
                logd("handleMessage : <EVENT_SIM_LOCKED_QUERY_ICCID_DONE> SIM" + (slotId + 1));
                if (ar.exception == null) {
                    if (ar.result != null) {
                        byte[] data = (byte[])ar.result;
                        sIccId[slotId] = IccUtils.parseIccIdToString(data, 0, data.length);
                    } else {
                        logd("Null ar");
                        sIccId[slotId] = ICCID_STRING_FOR_NO_SIM;
                    }
                } else {
                    if (ar.exception instanceof CommandException &&
                        ((CommandException) (ar.exception)).getCommandError() ==
                                CommandException.Error.RADIO_NOT_AVAILABLE) {
                        sIccId[slotId] = "";
                    } else {
                        sIccId[slotId] = ICCID_STRING_FOR_NO_SIM;
                    }
                    logd("Query IccId fail: " + ar.exception);
                }
                logd("sIccId[" + slotId + "] = " + sIccId[slotId]);
                // MTK-START
                //if (isAllIccIdQueryDone()) {
                //    updateSubscriptionInfoByIccId();
                //}
                //broadcastSimStateChanged(slotId, IccCardConstants.INTENT_VALUE_ICC_LOCKED,
                //                     uObj.reason);
                SubscriptionUpdatorThread updatorThread = new SubscriptionUpdatorThread(
                        new QueryIccIdUserObj(uObj.reason, slotId),
                        SubscriptionUpdatorThread.SIM_LOCKED);
                updatorThread.start();
                // MTK-END
                break;
            }
            case EVENT_RADIO_UNAVAILABLE:
                Integer index = getCiIndex(msg);
                logd("handleMessage : <EVENT_RADIO_UNAVAILABLE> SIM" + (index + 1));
                sIsUpdateAvailable[index] = 0;
                if (SystemProperties.get(COMMON_SLOT_PROPERTY).equals("1")) {
                    logd("[Common slot] reset mCommonSlotResetDone in EVENT_RADIO_AVAILABLE");
                    mCommonSlotResetDone = false;
                }
                break;
            case EVENT_RADIO_AVAILABLE:
                index = getCiIndex(msg);
                logd("handleMessage : <EVENT_RADIO_AVAILABLE> SIM" + (index + 1));
                sIsUpdateAvailable[index]++;

                if (checkIsAvailable()) {
                    if (!sIsC2kSupport) {
                        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
                            clearIccId(i);
                        }
                        mSubscriptionManager.clearSubscriptionInfo();
                    }
                    mReadIccIdCount = 0;
                    if (!readIccIdProperty()) {
                        postDelayed(mReadIccIdPropertyRunnable, sReadICCID_retry_time);
                    }
                }
                sIsUpdateAvailable[index] = 1;
                break;

            case EVENT_GET_NETWORK_SELECTION_MODE_DONE: {
                AsyncResult ar = (AsyncResult) msg.obj;
                Integer slotId = getCiIndex(msg);
                if (ar.exception == null && ar.result != null) {
                    int[] modes = (int[])ar.result;
                    if (modes[0] == 1) {  // Manual mode.
                        mPhone[slotId].setNetworkSelectionModeAutomatic(null);
                    }
                } else {
                    logd("EVENT_GET_NETWORK_SELECTION_MODE_DONE: error getting network mode.");
                }
                break;
            }
            case EVENT_SIM_LOADED: {
                // MTK-START
                //handleSimLoaded(msg.arg1);

                // Execute updateSubscriptionInfoByIccId by another thread might cause
                // broadcast intent sent before update done.
                // Need to make updateSubscriptionInfoByIccId and send broadcast as a wrapper
                // with the same thread to avoid broadcasting before update done.
                SubscriptionUpdatorThread updatorThread = new SubscriptionUpdatorThread(
                        new QueryIccIdUserObj(null, msg.arg1),
                        SubscriptionUpdatorThread.SIM_LOADED);
                updatorThread.start();
                // MTK-END
                break;
            }
            case EVENT_SIM_ABSENT: {
                // MTK-START
                //handleSimAbsent(msg.arg1);
                SubscriptionUpdatorThread updatorThread = new SubscriptionUpdatorThread(
                        new QueryIccIdUserObj(null, msg.arg1),
                        SubscriptionUpdatorThread.SIM_ABSENT);
                updatorThread.start();
                // MTK-END
                break;
            }
            case EVENT_SIM_LOCKED:
                handleSimLocked(msg.arg1, (String) msg.obj);
                break;

            // MTK-START
            case EVENT_SIM_NO_CHANGED: {
                SubscriptionUpdatorThread updatorThread = new SubscriptionUpdatorThread(
                        new QueryIccIdUserObj(null, msg.arg1),
                        SubscriptionUpdatorThread.SIM_NO_CHANGED);
                updatorThread.start();
                break;
            }

            case EVENT_TRAY_PLUG_IN: {
                logd("[Common Slot] handle EVENT_TRAY_PLUG_IN " + mCommonSlotResetDone);
                if (!mCommonSlotResetDone) {
                    mCommonSlotResetDone = true;
                    for (int i = 0; i < PROJECT_SIM_NUM; i++) {
                        sIccId[i] = "";
                    }
                }
                break;
            }

            case EVENT_SIM_PLUG_OUT: {
                logd("[Common Slot] handle EVENT_SIM_PLUG_OUT " + mCommonSlotResetDone);
                mCommonSlotResetDone = false;
                break;
            }
            // MTK-END
            default:
                logd("Unknown msg:" + msg.what);
        }
    }

    private static class QueryIccIdUserObj {
        public String reason;
        public int slotId;

        QueryIccIdUserObj(String reason, int slotId) {
            this.reason = reason;
            this.slotId = slotId;
        }
    };

    // MTK-START
    private class SubscriptionUpdatorThread extends Thread {
        public static final int SIM_ABSENT = 0;
        public static final int SIM_LOADED = 1;
        public static final int SIM_LOCKED = 2;
        public static final int SIM_NO_CHANGED = 3;

        private QueryIccIdUserObj mUserObj;
        private int mEventId;

        SubscriptionUpdatorThread(QueryIccIdUserObj userObj, int eventId) {
            mUserObj = userObj;
            mEventId = eventId;
        }

        @Override
        public void run() {
            switch (mEventId) {
                case SIM_ABSENT:
                    handleSimAbsent(mUserObj.slotId);
                    break;

                case SIM_LOADED:
                    handleSimLoaded(mUserObj.slotId);
                    break;

                case SIM_LOCKED:
                    if (isAllIccIdQueryDone()) {
                          updateSubscriptionInfoByIccId();
                    }
                    broadcastSimStateChanged(mUserObj.slotId,
                            IccCardConstants.INTENT_VALUE_ICC_LOCKED, mUserObj.reason);
                    break;
                case SIM_NO_CHANGED:
                    logd("[Common Slot]SubscriptionUpdatorThread run for SIM_NO_CHANGED.");
                    sIccId[mUserObj.slotId] = ICCID_STRING_FOR_NO_SIM;
                    if (isAllIccIdQueryDone()) {
                          updateSubscriptionInfoByIccId();
                    }
                default:
                    logd("SubscriptionUpdatorThread run with invalid event id.");
                    break;
            }
        }
    };
    // MTK-END

    private void handleSimLocked(int slotId, String reason) {
        // MTK-START
        // [ALPS01981366] Since MTK add new thread for updateSubscriptionInfoByIccId,
        // it might cause NullPointerException if we set mIccId to null without synchronized block.
        synchronized (mLock) {
        // MTK-END
        if (sIccId[slotId] != null && sIccId[slotId].equals(ICCID_STRING_FOR_NO_SIM)) {
            logd("SIM" + (slotId + 1) + " hot plug in");
            sIccId[slotId] = null;
        }

        IccFileHandler fileHandler = mPhone[slotId].getIccCard() == null ? null :
                mPhone[slotId].getIccCard().getIccFileHandler();

        if (fileHandler != null) {
            String iccId = sIccId[slotId];
            // MTK-START
            if (iccId == null || iccId.equals("")) {
                // [ALPS02006863]
                // 1.Execute updateSubscriptionInfoByIccId by another thread might cause
                //   broadcast intent sent before update done.
                //   Need to make updateSubscriptionInfoByIccId and send broadcast as a wrapper
                //   with the same thread to avoid broadcasting before update done.
                // 2.Use Icc id system property istead SIM IO to query to enhance
                //   update database performance.
                sIccId[slotId] = SystemProperties.get(PROPERTY_ICCID_SIM[slotId], "");

                if (sIccId[slotId] != null && !sIccId[slotId].equals("")) {
                    logd("Use Icc ID system property for performance enhancement");
                    SubscriptionUpdatorThread updatorThread = new SubscriptionUpdatorThread(
                            new QueryIccIdUserObj(reason, slotId),
                            SubscriptionUpdatorThread.SIM_LOCKED);
                    updatorThread.start();
                } else {
            // MTK-END
                    logd("Querying IccId");
                    fileHandler.loadEFTransparent(IccConstants.EF_ICCID,
                            obtainMessage(EVENT_SIM_LOCKED_QUERY_ICCID_DONE,
                                    new QueryIccIdUserObj(reason, slotId)));
            // MTK-START
                }
            // MTK-END
            } else {
                logd("NOT Querying IccId its already set sIccid[" + slotId + "]=" + iccId);
                // ALPS01929356 : In case PUK lock condition,
                // It will always broadcast SIM locked.
                broadcastSimStateChanged(slotId, IccCardConstants.INTENT_VALUE_ICC_LOCKED,
                        reason);
            }
        } else {
            logd("sFh[" + slotId + "] is null, ignore");
            // ALPS01929356 : In case PUK lock condition,
            // It will always broadcast SIM locked.
            broadcastSimStateChanged(slotId, IccCardConstants.INTENT_VALUE_ICC_LOCKED,
                    reason);
        }
        // MTK-START
        }
        // MTK-END
    }

    private void handleSimLoaded(int slotId) {
        logd("handleSimStateLoadedInternal: slotId: " + slotId);
        boolean needUpdate = false;

        // The SIM should be loaded at this state, but it is possible in cases such as SIM being
        // removed or a refresh RESET that the IccRecords could be null. The right behavior is to
        // not broadcast the SIM loaded.
        IccRecords records = mPhone[slotId].getIccCard().getIccRecords();
        if (records == null) {  // Possibly a race condition.
            logd("onRecieve: IccRecords null");
            return;
        }
        if (records.getIccId() == null) {
            logd("onRecieve: IccID null");
            return;
        }

        String iccId = SystemProperties.get(PROPERTY_ICCID_SIM[slotId], "");
        if (!iccId.equals(sIccId[slotId])) {
            logd("NeedUpdate");
            needUpdate = true;
            sIccId[slotId] = iccId;
        }

        //sIccId[slotId] = records.getIccId();

        if (isAllIccIdQueryDone() && needUpdate) {
            updateSubscriptionInfoByIccId();
        }

        int subId = SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;
        int[] subIds = SubscriptionController.getInstance().getSubId(slotId);
        if (subIds != null) {   // Why an array?
            subId = subIds[0];
        }

        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            String operator = records.getOperatorNumeric();
            if (operator != null) {
                if (subId == SubscriptionController.getInstance().getDefaultSubId()) {
                    MccTable.updateMccMncConfiguration(mContext, operator, false);
                }
                SubscriptionController.getInstance().setMccMnc(operator,subId);
            } else {
                logd("EVENT_RECORDS_LOADED Operator name is null");
            }
            TelephonyManager tm = TelephonyManager.getDefault();
            String msisdn = tm.getLine1NumberForSubscriber(subId);
            ContentResolver contentResolver = mContext.getContentResolver();

            if (msisdn != null) {
                SubscriptionController.getInstance().setDisplayNumber(msisdn, subId, false);
            }

            SubscriptionInfo subInfo = mSubscriptionManager.getActiveSubscriptionInfo(subId);
            String nameToSet;
            String simCarrierName = tm.getSimOperatorNameForSubscription(subId);

            if (subInfo != null && subInfo.getNameSource() !=
                    SubscriptionManager.NAME_SOURCE_USER_INPUT) {
                // MTK-START
                // Take MVNO into account.
                String simNumeric = tm.getSimOperatorNumericForSubscription(
                        subIds[0]);
                String simMvnoName = null;
                if ("20404".equals(simNumeric)
                        && mPhone[slotId].getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                    logd("[handleSimLoaded] special handle for roaming case!");
                    simMvnoName = simCarrierName;
                } else {
                    simMvnoName = SpnOverride.getInstance().lookupOperatorNameForDisplayName(
                            subIds[0], simNumeric, true, mContext);
                }
                logd("[handleSimLoaded]- simNumeric: " + simNumeric +
                            ", simMvnoName: " + simMvnoName+",simCarrierName:"+simCarrierName);
	//add by huangshuo for networkname read from the card(EF_SPN) firstly,then read from xml in phone
                if (!TextUtils.isEmpty(simCarrierName)) {
                    nameToSet = simCarrierName;
                } else {
                    if (!TextUtils.isEmpty(simMvnoName)) {
                        nameToSet = simMvnoName;
	//end  by huangshuo for networkname read from the card(EF_SPN) firstly,then read from xml in phone
                    } else {
                    // / Modified by guofeiyao 2015/12/09
                    // For 813 need, SubInfo default value
                        //nameToSet = "CARD " + Integer.toString(slotId + 1);
                        nameToSet = mContext.getResources().getString(com.hq.resource.internal.R.string.subinfo_default) + Integer.toString(slotId + 1);
					// / End
                    }
                }
		//add by huangshuo for HQ01670655 on 2016/01/29
		if (simNumeric.equals("46601")) {
                 nameToSet = mContext.getText(com.mediatek.R.string.oper_long_46601).toString();
               } else if (simNumeric.equals("46692")) {
                nameToSet = mContext.getText(com.mediatek.R.string.oper_long_46692).toString();
                } else if (simNumeric.equals("46697")) {
                 nameToSet = mContext.getText(com.mediatek.R.string.oper_long_46697).toString();
                }else if ((simNumeric.equals("46000")) || (simNumeric.equals("46002")) || (simNumeric.equals("46007")) || (simNumeric.equals("46008"))) {
                nameToSet = mContext.getText(com.mediatek.R.string.oper_long_46000).toString();
               } else if (simNumeric.equals("46001") ||simNumeric.equals("46009")){
                nameToSet = mContext.getText(com.mediatek.R.string.oper_long_46001).toString();
               } else if ((simNumeric.equals("46003")) || (simNumeric.equals("46011"))) {
                nameToSet = mContext.getText(com.mediatek.R.string.oper_long_46003).toString();
               } else if (simNumeric.equals("99998")) {
                nameToSet = mContext.getText(com.mediatek.R.string.oper_long_99998).toString();
               } else if (simNumeric.equals("99999")) {
                nameToSet = mContext.getText(com.mediatek.R.string.oper_long_99999).toString();
               } 
	      //end by huangshuo for HQ01670655 on 2016/01/29
	      
                // MTK-END
                // MTK-START
                //name.put(SubscriptionManager.DISPLAY_NAME, nameToSet);
                //logd("sim name = " + nameToSet);
                //contentResolver.update(SubscriptionManager.CONTENT_URI, name,
                //        SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID
                //        + "=" + Long.toString(subId), null);
                mSubscriptionManager.setDisplayName(nameToSet, subId);
                logd("[handleSimLoaded] subId = " + subId + ", sim name = " + nameToSet);
                // MTK-END
            }

            /* Update preferred network type and network selection mode on SIM change.
             * Storing last subId in SharedPreference for now to detect SIM change. */
            SharedPreferences sp =
                    PreferenceManager.getDefaultSharedPreferences(mContext);
            int storedSubId = sp.getInt(CURR_SUBID + slotId, -1);

            if (storedSubId != subId) {
                int networkType = Settings.Global.getInt(mPhone[slotId].getContext().getContentResolver(),
                        Settings.Global.PREFERRED_NETWORK_MODE + subId,
                        RILConstants.PREFERRED_NETWORK_MODE);

                // MTK network mode logic is central controled by GsmSST
                logd("Possibly a new IMSI. Set sub(" + subId + ") networkType to " + networkType);
                Settings.Global.putInt(mPhone[slotId].getContext().getContentResolver(),
                        Settings.Global.PREFERRED_NETWORK_MODE + subId,
                        networkType);

                ///M: Add for the 4g/tdd switch setting. @{
                int ratMode = Settings.Global.getInt(mPhone[slotId]
                        .getContext().getContentResolver(),
                        Settings.Global.LTE_ON_CDMA_RAT_MODE + subId,
                        SvlteRatController.SvlteRatMode.SVLTE_RAT_MODE_4G.ordinal());

                logd("Possibly a new IMSI. Set sub(" + subId + ") ratMode to " + ratMode);
                Settings.Global.putInt(mPhone[slotId].getContext().getContentResolver(),
                        Settings.Global.LTE_ON_CDMA_RAT_MODE + subId,
                        ratMode);
                ///@}
                // Only support automatic selection mode on SIM change.
                mPhone[slotId].getNetworkSelectionMode(
                        obtainMessage(EVENT_GET_NETWORK_SELECTION_MODE_DONE, new Integer(slotId)));

                // Update stored subId
                SharedPreferences.Editor editor = sp.edit();
                editor.putInt(CURR_SUBID + slotId, subId);
                editor.apply();
            }
        } else {
            logd("Invalid subId, could not update ContentResolver");
        }

        broadcastSimStateChanged(slotId, IccCardConstants.INTENT_VALUE_ICC_LOADED, null);
    }

    private void handleSimAbsent(int slotId) {
        if (sIccId[slotId] != null && !sIccId[slotId].equals(ICCID_STRING_FOR_NO_SIM)) {
            logd("SIM" + (slotId + 1) + " hot plug out");
        }

        // MTK-START
        // If card inserted state no changed, no need to update.
        // Return directly to avoid unneccessary update cause timing issue.
        if (sIccId[slotId] != null && sIccId[slotId].equals(ICCID_STRING_FOR_NO_SIM)) {
            logd("SIM" + (slotId + 1) + " absent - card state no changed.");
            return;
        }
        // MTK-END

        sIccId[slotId] = ICCID_STRING_FOR_NO_SIM;
        if (isAllIccIdQueryDone()) {
            updateSubscriptionInfoByIccId();
        }
    }

    /**
     * TODO: Simplify more, as no one is interested in what happened
     * only what the current list contains.
     */
    synchronized private void updateSubscriptionInfoByIccId() {
        synchronized (mLock) {
            logd("updateSubscriptionInfoByIccId:+ Start");

            // ALPS01933839 timing issue, JE after receiving IPO shutdown
            // do this update
            if (!isAllIccIdQueryDone()){
                return;
            }

            mSubscriptionManager.clearSubscriptionInfo();

            // Reset the flag because all sIccId are ready.
            mCommonSlotResetDone = false;

            for (int i = 0; i < PROJECT_SIM_NUM; i++) {
                sInsertSimState[i] = SIM_NOT_CHANGE;
            }

            int insertedSimCount = PROJECT_SIM_NUM;
            for (int i = 0; i < PROJECT_SIM_NUM; i++) {
                if (ICCID_STRING_FOR_NO_SIM.equals(sIccId[i])) {
                    insertedSimCount--;
                    sInsertSimState[i] = SIM_NOT_INSERT;
                }
            }
            logd("insertedSimCount = " + insertedSimCount);

            int index = 0;
            for (int i = 0; i < PROJECT_SIM_NUM; i++) {
                if (sInsertSimState[i] == SIM_NOT_INSERT) {
                    continue;
                }
                index = 2;
                for (int j = i + 1; j < PROJECT_SIM_NUM; j++) {
                    if (sInsertSimState[j] == SIM_NOT_CHANGE && sIccId[i].equals(sIccId[j])) {
                        sInsertSimState[i] = 1;
                        sInsertSimState[j] = index;
                        index++;
                    }
                }
            }

            ContentResolver contentResolver = mContext.getContentResolver();
            String[] oldIccId = new String[PROJECT_SIM_NUM];
            for (int i = 0; i < PROJECT_SIM_NUM; i++) {
                oldIccId[i] = null;
                List<SubscriptionInfo> oldSubInfo =
                        SubscriptionController.getInstance()
                        .getSubInfoUsingSlotIdWithCheck(i, false);
                if (oldSubInfo != null) {
                    oldIccId[i] = oldSubInfo.get(0).getIccId();
                    logd("updateSubscriptionInfoByIccId: oldSubId = "
                            + oldSubInfo.get(0).getSubscriptionId());
                    if (sInsertSimState[i] == SIM_NOT_CHANGE && !sIccId[i].equals(oldIccId[i])) {
                        sInsertSimState[i] = SIM_CHANGED;
                    }
                    if (sInsertSimState[i] != SIM_NOT_CHANGE) {
                        ContentValues value = new ContentValues(1);
                        value.put(SubscriptionManager.SIM_SLOT_INDEX,
                                SubscriptionManager.INVALID_SIM_SLOT_INDEX);
                        contentResolver.update(SubscriptionManager.CONTENT_URI, value,
                                SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID + "="
                                + Integer.toString(oldSubInfo.get(0).getSubscriptionId()), null);
                    }
                } else {
                    if (sInsertSimState[i] == SIM_NOT_CHANGE) {
                        // no SIM inserted last time, but there is one SIM inserted now
                        sInsertSimState[i] = SIM_CHANGED;
                    }
                    oldIccId[i] = ICCID_STRING_FOR_NO_SIM;
                    logd("updateSubscriptionInfoByIccId: No SIM in slot " + i + " last time");
                }
            }

            for (int i = 0; i < PROJECT_SIM_NUM; i++) {
                logd("updateSubscriptionInfoByIccId: oldIccId[" + i + "] = " + oldIccId[i] +
                        ", sIccId[" + i + "] = " + sIccId[i]);
            }

            //check if the inserted SIM is new SIM
            int nNewCardCount = 0;
            int nNewSimStatus = 0;
            for (int i = 0; i < PROJECT_SIM_NUM; i++) {
                if (sInsertSimState[i] == SIM_NOT_INSERT) {
                    logd("updateSubscriptionInfoByIccId: No SIM inserted in slot " + i + " this time");
                } else {
                    if (sInsertSimState[i] > 0) {
                        //some special SIMs may have the same IccIds, add suffix to distinguish them
                        //FIXME: addSubInfoRecord can return an error.
                        mSubscriptionManager.addSubscriptionInfoRecord(sIccId[i]
                                + Integer.toString(sInsertSimState[i]), i);
                        logd("SUB" + (i + 1) + " has invalid IccId");
                    } else /*if (sInsertSimState[i] != SIM_NOT_INSERT)*/ {
                        mSubscriptionManager.addSubscriptionInfoRecord(sIccId[i], i);
                    }
                    if (isNewSim(sIccId[i], oldIccId)) {
                        nNewCardCount++;
                        switch (i) {
                            case PhoneConstants.SUB1:
                                nNewSimStatus |= STATUS_SIM1_INSERTED;
                                break;
                            case PhoneConstants.SUB2:
                                nNewSimStatus |= STATUS_SIM2_INSERTED;
                                break;
                            case PhoneConstants.SUB3:
                                nNewSimStatus |= STATUS_SIM3_INSERTED;
                                break;
                            //case PhoneConstants.SUB3:
                            //    nNewSimStatus |= STATUS_SIM4_INSERTED;
                            //    break;
                        }

                        sInsertSimState[i] = SIM_NEW;
                    }
                }
            }

            for (int i = 0; i < PROJECT_SIM_NUM; i++) {
                if (sInsertSimState[i] == SIM_CHANGED) {
                    sInsertSimState[i] = SIM_REPOSITION;
                }
                logd("updateSubscriptionInfoByIccId: sInsertSimState[" + i + "] = "
                        + sInsertSimState[i]);
            }

            List<SubscriptionInfo> subInfos = mSubscriptionManager.getActiveSubscriptionInfoList();
            int nSubCount = (subInfos == null) ? 0 : subInfos.size();
            logd("updateSubscriptionInfoByIccId: nSubCount = " + nSubCount);
            for (int i = 0; i < nSubCount; i++) {
                SubscriptionInfo temp = subInfos.get(i);

                String msisdn = TelephonyManager.getDefault().getLine1NumberForSubscriber(
                        temp.getSubscriptionId());

                if (msisdn != null) {
                    SubscriptionController.getInstance().setDisplayNumber(msisdn,
                            temp.getSubscriptionId(), false);
                }
            }

            setAllDefaultSub(subInfos);

            // true if any slot has no SIM this time, but has SIM last time
            boolean hasSimRemoved = false;
            for (int i = 0; i < PROJECT_SIM_NUM; i++) {
                if (sIccId[i] != null && sIccId[i].equals(ICCID_STRING_FOR_NO_SIM)
                        && !oldIccId[i].equals(ICCID_STRING_FOR_NO_SIM)) {
                    hasSimRemoved = true;
                    break;
                }
            }

            if (nNewCardCount == 0) {
                int i;
                if (hasSimRemoved) {
                    // no new SIM, at least one SIM is removed, check if any SIM is repositioned
                    for (i = 0; i < PROJECT_SIM_NUM; i++) {
                        if (sInsertSimState[i] == SIM_REPOSITION) {
                            logd("No new SIM detected and SIM repositioned");
                            setUpdatedData(SubscriptionManager.EXTRA_VALUE_REPOSITION_SIM,
                                    nSubCount, nNewSimStatus);
                            break;
                        }
                    }
                    if (i == PROJECT_SIM_NUM) {
                        // no new SIM, no SIM is repositioned => at least one SIM is removed
                        logd("No new SIM detected and SIM removed");
                        setUpdatedData(SubscriptionManager.EXTRA_VALUE_REMOVE_SIM,
                                nSubCount, nNewSimStatus);
                    }
                } else {
                    // no SIM is removed, no new SIM, just check if any SIM is repositioned
                    for (i = 0; i < PROJECT_SIM_NUM; i++) {
                        if (sInsertSimState[i] == SIM_REPOSITION) {
                            logd("No new SIM detected and SIM repositioned");
                            setUpdatedData(SubscriptionManager.EXTRA_VALUE_REPOSITION_SIM,
                                    nSubCount, nNewSimStatus);
                            break;
                        }
                    }
                    if (i == PROJECT_SIM_NUM) {
                        // all status remain unchanged
                        logd("[updateSimInfoByIccId] All SIM inserted into the same slot");
                        setUpdatedData(SubscriptionManager.EXTRA_VALUE_NOCHANGE,
                                nSubCount, nNewSimStatus);
                    }
                }
            } else {
                logd("New SIM detected");
                setUpdatedData(SubscriptionManager.EXTRA_VALUE_NEW_SIM, nSubCount, nNewSimStatus);
            }

            SubscriptionController.getInstance().setReadyState(true);

            SubscriptionController.getInstance().notifySubscriptionInfoChanged();
            logd("updateSubscriptionInfoByIccId:- SsubscriptionInfo update complete");
        }
    }

    private void setUpdatedData(int detectedType, int subCount, int newSimStatus) {

        Intent intent = new Intent(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);

        logd("[setUpdatedData]+ ");

        if (detectedType == SubscriptionManager.EXTRA_VALUE_NEW_SIM) {
            intent.putExtra(SubscriptionManager.INTENT_KEY_DETECT_STATUS,
                    SubscriptionManager.EXTRA_VALUE_NEW_SIM);
            intent.putExtra(SubscriptionManager.INTENT_KEY_SIM_COUNT, subCount);
            intent.putExtra(SubscriptionManager.INTENT_KEY_NEW_SIM_SLOT, newSimStatus);
        } else if (detectedType == SubscriptionManager.EXTRA_VALUE_REPOSITION_SIM) {
            intent.putExtra(SubscriptionManager.INTENT_KEY_DETECT_STATUS,
                    SubscriptionManager.EXTRA_VALUE_REPOSITION_SIM);
            intent.putExtra(SubscriptionManager.INTENT_KEY_SIM_COUNT, subCount);
        } else if (detectedType == SubscriptionManager.EXTRA_VALUE_REMOVE_SIM) {
            intent.putExtra(SubscriptionManager.INTENT_KEY_DETECT_STATUS,
                    SubscriptionManager.EXTRA_VALUE_REMOVE_SIM);
            intent.putExtra(SubscriptionManager.INTENT_KEY_SIM_COUNT, subCount);
        } else if (detectedType == SubscriptionManager.EXTRA_VALUE_NOCHANGE) {
            intent.putExtra(SubscriptionManager.INTENT_KEY_DETECT_STATUS,
                    SubscriptionManager.EXTRA_VALUE_NOCHANGE);
        }

        logd("broadcast intent ACTION_SUBINFO_RECORD_UPDATED : [" + detectedType + ", "
                + subCount + ", " + newSimStatus + "]");
        sUpdateIntent = intent;
        mContext.sendStickyBroadcast(sUpdateIntent);
        logd("[setUpdatedData]- ");
    }

    private boolean isNewSim(String iccId, String[] oldIccId) {
        boolean newSim = true;
        for(int i = 0; i < PROJECT_SIM_NUM; i++) {
            // MTK-START
            // Modify for special SIMs have the same IccIds
            if (iccId != null && oldIccId[i] != null) {
                if (oldIccId[i].indexOf(iccId) == 0) {
                    newSim = false;
                    break;
                }
            }
            // MTK-END
        }
        logd("newSim = " + newSim);

        return newSim;
    }

    private void broadcastSimStateChanged(int slotId, String state, String reason) {
        Intent i = new Intent(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        // TODO - we'd like this intent to have a single snapshot of all sim state,
        // but until then this should not use REPLACE_PENDING or we may lose
        // information
        // i.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING
        //         | Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        i.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        i.putExtra(PhoneConstants.PHONE_NAME_KEY, "Phone");
        i.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE, state);
        i.putExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON, reason);
        SubscriptionManager.putPhoneIdAndSubIdExtra(i, slotId);
        logd("Broadcasting intent ACTION_SIM_STATE_CHANGED " +
             state + " reason " + reason +
             " for mCardIndex : " + slotId);
        ActivityManagerNative.broadcastStickyIntent(i, READ_PHONE_STATE,
                UserHandle.USER_ALL);
    }

    public void dispose() {
        logd("[dispose]");
        mContext.unregisterReceiver(sReceiver);
    }

    private void logd(String message) {
        Rlog.d(LOG_TAG, message);
    }

    private void setAllDefaultSub(List<SubscriptionInfo> subInfos) {
        logd("[setAllDefaultSub]+ ");
        DefaultSmsSimSettings.setSmsTalkDefaultSim(subInfos, mContext);
        logd("[setSmsTalkDefaultSim]- ");
        DefaultVoiceCallSubSettings.setVoiceCallDefaultSub(subInfos);
        logd("[setVoiceCallDefaultSub]- ");
    }

    private void clearIccId(int slotId) {
        synchronized (mLock) {
            logd("[clearIccId], slotId = " + slotId);
            sFh[slotId] = null;
            sIccId[slotId] = null;
        }
    }

    private Runnable mReadIccIdPropertyRunnable = new Runnable() {
        public void run() {
            ++mReadIccIdCount;
            if (mReadIccIdCount <= 10) {
                if (!readIccIdProperty()) {
                    postDelayed(mReadIccIdPropertyRunnable, sReadICCID_retry_time);
                }
            }
        }
    };

    private boolean readIccIdProperty() {
        logd("readIccIdProperty +, retry_count = " + mReadIccIdCount);
        if (!sIsC2kSupport) {
            for (int i = 0; i < PROJECT_SIM_NUM; i++) {
                if (sIccId[i] == null || sIccId[i].equals("")) {
                    sIccId[i] = SystemProperties.get(PROPERTY_ICCID_SIM[i], "");
                    if (sIccId[i].length() == 3) {
                        logd("No SIM insert :" + i);
                        if (sIsSvlteSupport) {
                            if (SvlteUiccUtils.getInstance().isHaveCard(i)
                                && !SvlteUiccUtils.getInstance().isUsimSim(i)) {
                                logd("CT 3G card is not ready, set iccid to null!");
                                sIccId[i] = null;
                            }
                        }
                    }
                    logd("sIccId[" + i + "]:" + sIccId[i]);
                }
            }
            if (isAllIccIdQueryDone()) {
                new Thread() {
                    public void run() {
                        updateSubscriptionInfoByIccId();
                    }
                } .start();
                // ALPS01934211 : Need to wait for updateSubscriptionInfoByIccId done
                synchronized (mLock) {
                    return true;
                }
            } else {
                return false;
            }
        } else {
            if (checkAllIccIdReady()) {
                if (needUpdateSubInfo()) {
                    new Thread() {
                        public void run() {
                            updateSubscriptionInfoByIccId();
                        }
                    } .start();
                    // ALPS01934211 : Need to wait for updateSubscriptionInfoByIccId done
                    synchronized (mLock) {
                        return true;
                    }
                } else {
                    Intent intent = new Intent(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
                    intent.putExtra(SubscriptionManager.INTENT_KEY_DETECT_STATUS,
                                        SubscriptionManager.EXTRA_VALUE_NOCHANGE);
                    logd("Broadcast ACTION_SUBINFO_RECORD_UPDATED with nochange");
                    sUpdateIntent = intent;
                    mContext.sendStickyBroadcast(sUpdateIntent);
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    private Integer getCiIndex(Message msg) {
        AsyncResult ar;
        Integer index = new Integer(PhoneConstants.DEFAULT_CARD_INDEX);

        /*
         * The events can be come in two ways. By explicitly sending it using
         * sendMessage, in this case the user object passed is msg.obj and from
         * the CommandsInterface, in this case the user object is msg.obj.userObj
         */
        if (msg != null) {
            if (msg.obj != null && msg.obj instanceof Integer) {
                index = (Integer) msg.obj;
            } else if (msg.obj != null && msg.obj instanceof AsyncResult) {
                ar = (AsyncResult) msg.obj;
                if (ar.userObj != null && ar.userObj instanceof Integer) {
                    index = (Integer) ar.userObj;
                }
            }
        }
        return index;
    }

    private boolean checkIsAvailable() {
        boolean result = true;
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            if (sIsUpdateAvailable[i] <= 0) {
                logd("sIsUpdateAvailable[" + i + "] = " + sIsUpdateAvailable[i]);
                result = false;
                break;
            }
        }
        logd("checkIsAvailable result = " + result);
        return result;
    }

    private void updateSubName(int subId) {
        SubscriptionInfo subInfo =
                mSubscriptionManager.getSubscriptionInfo(subId);
        if (subInfo != null
                && subInfo.getNameSource() != SubscriptionManager.NAME_SOURCE_USER_INPUT) {
            SpnOverride spnOverride = SpnOverride.getInstance();
            String nameToSet;
            String carrierName = TelephonyManager.getDefault().getSimOperator(subId);
            int slotId = SubscriptionManager.getSlotId(subId);
            logd("updateSubName, carrierName = " + carrierName + ", subId = " + subId);
            if (SubscriptionManager.isValidSlotId(slotId)) {
                if (spnOverride.containsCarrierEx(carrierName)) {

                    nameToSet = spnOverride.lookupOperatorName(subId, carrierName,
                        true, mContext);

                    logd("SPN found, name = " + nameToSet);
                } else {
                    // / Modified by guofeiyao 2015/12/09
                     // For 813 need, SubInfo default value

               //add by huangshuo for HQ01708382 on 2016/01/28
                     //nameToSet = "CARD " + Integer.toString(slotId + 1);
                  // nameToSet = mContext.getResources().getString(com.hq.resource.internal.R.string.subinfo_default) + Integer.toString(slotId + 1);
                   if (SystemProperties.get("ro.mtk_gemini_support").equals("1")) {
                             nameToSet = mContext.getResources().getString(com.hq.resource.internal.R.string.subinfo_default) + Integer.toString(slotId + 1);
                   }else{
                             nameToSet = mContext.getResources().getString(com.hq.resource.internal.R.string.subinfo_default);
                   }
                  // end by huangshuo for HQ01708382 on 2016/01/28
                     logd("SPN not found, set name to " + nameToSet);
                }
		String simNumeric=PhoneNumberUtils.getSimMccMnc(slotId);
		if (simNumeric.equals("46601")) {
                 nameToSet = mContext.getText(com.mediatek.R.string.oper_long_46601).toString();
               } else if (simNumeric.equals("46692")) {
                nameToSet = mContext.getText(com.mediatek.R.string.oper_long_46692).toString();
                } else if (simNumeric.equals("46697")) {
                 nameToSet = mContext.getText(com.mediatek.R.string.oper_long_46697).toString();
                }else if ((simNumeric.equals("46000")) || (simNumeric.equals("46002")) || (simNumeric.equals("46007")) || (simNumeric.equals("46008"))) {
                nameToSet = mContext.getText(com.mediatek.R.string.oper_long_46000).toString();
               } else if (simNumeric.equals("46001") ||simNumeric.equals("46009")){
                nameToSet = mContext.getText(com.mediatek.R.string.oper_long_46001).toString();
               } else if ((simNumeric.equals("46003")) || (simNumeric.equals("46011"))) {
                nameToSet = mContext.getText(com.mediatek.R.string.oper_long_46003).toString();
               } else if (simNumeric.equals("99998")) {
                nameToSet = mContext.getText(com.mediatek.R.string.oper_long_99998).toString();
               } else if (simNumeric.equals("99999")) {
                nameToSet = mContext.getText(com.mediatek.R.string.oper_long_99999).toString();
               } 
                mSubscriptionManager.setDisplayName(nameToSet, subId);
            }
        }
    }

    private boolean checkAllIccIdReady() {
        String iccId = "";
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            iccId = SystemProperties.get(PROPERTY_ICCID_SIM[i], "");
            if (iccId.length() == 3) {
                logd("No SIM insert :" + i);
                if (sIsSvlteSupport) {
                    if (SvlteUiccUtils.getInstance().isHaveCard(i)
                        && !SvlteUiccUtils.getInstance().isUsimSim(i)) {
                        logd("CT 3G card is not ready, set iccid to null!");
                        iccId = null;
                    }
                }
            }
            logd("iccId[" + i + "] = " + iccId);
            if (iccId == null || iccId.equals("")) {
                return false;
            }
        }
        return true;
    }

    private boolean needUpdateSubInfo() {
        boolean result = false;
        String newIccid = "";
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            newIccid = SystemProperties.get(PROPERTY_ICCID_SIM[i], "");
            if (sIccId[i] == null || !sIccId[i].equals(newIccid)) {
                logd("needUpdateSubInfo, slot[" + i + "] changes from "
                    + sIccId[i] + " to " + newIccid);
                sIccId[i] = newIccid;
                result = true;
            }
        }
        return result;
    }
}

