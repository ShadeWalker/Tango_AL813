/*
 * Copyright (C) 2014 The Android Open Source Project
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2014 MediaTek Inc.
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

package com.android.internal.telephony.dataconnection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkFactory;
import android.net.NetworkRequest;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager.MultiSimVariants;
import android.util.Pair;
import android.util.SparseArray;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.dataconnection.DcSwitchAsyncChannel.RequestInfo;
import com.android.internal.util.AsyncChannel;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.dataconnection.DataSubSelector;
import com.mediatek.internal.telephony.ltedc.svlte.IratController;
import com.mediatek.internal.telephony.ltedc.svlte.IratDataSwitchHelper;
import com.mediatek.internal.telephony.ltedc.svlte.MdIratDataSwitchHelper;
import com.mediatek.internal.telephony.ltedc.svlte.SvltePhoneProxy;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
/** M: start */
import com.mediatek.internal.telephony.dataconnection.DataSubSelector;
/** M: end */

public class DctController extends Handler {
    private static final String LOG_TAG = "DctController";
    private static final boolean DBG = true;

    private static final int EVENT_PROCESS_REQUESTS = 100;
    private static final int EVENT_EXECUTE_REQUEST = 101;
    private static final int EVENT_EXECUTE_ALL_REQUESTS = 102;
    private static final int EVENT_RELEASE_REQUEST = 103;
    private static final int EVENT_RELEASE_ALL_REQUESTS = 104;

    //MTK START
    private static final int EVENT_TRANSIT_TO_ATTACHING = 200;
    private static final int EVENT_CONFIRM_PREDETACH = 201;

    private static final int EVENT_DATA_ATTACHED = 500;
    private static final int EVENT_DATA_DETACHED = 600;

    //M
    private static final int EVENT_SET_DATA_ALLOWED = 700;
    private static final int EVENT_RESTORE_PENDING = 800;

    private static final int EVENT_RADIO_AVAILABLE = 900;
    /** M: start */
    static final String PROPERTY_RIL_DATA_ICCID = "persist.radio.data.iccid";
    private String[] PROPERTY_ICCID_SIM = {
        "ril.iccid.sim1",
        "ril.iccid.sim2",
        "ril.iccid.sim3",
        "ril.iccid.sim4",
    };
    static final String PROPERTY_DATA_ALLOW_SIM = "ril.data.allow";
    static final String PROPERTY_IA_APN_SET_ICCID = "ril.ia.iccid";
    static final String ACTION_SHUTDOWN_IPO = "android.intent.action.ACTION_SHUTDOWN_IPO";
    static final String PROPERTY_TEMP_IA = "ril.radio.ia";
    static final String PROPERTY_TEMP_IA_APN = "ril.radio.ia-apn";
    /** M: end */

    private static DctController sDctController;

    // / M: [C2K][IRAT]start {@
    private boolean mSuspendNetworkRequest;
    private boolean mHasPendingDataSwitch;

    private IratDataSwitchHelper[] mIratDataSwitchHelper;
    // / M: [C2K][IRAT] end @}

    private int mPhoneNum;
    private PhoneProxy[] mPhones;
    private DcSwitchStateMachine[] mDcSwitchStateMachine;
    private DcSwitchAsyncChannel[] mDcSwitchAsyncChannel;
    private Handler[] mDcSwitchStateHandler;
    private HashMap<Integer, RequestInfo> mRequestInfos = new HashMap<Integer, RequestInfo>();
    private Context mContext;

    //M: Pre-Detach Check State
    protected int mUserCnt;
    protected int mTransactionId;

    /** Used to send us NetworkRequests from ConnectivityService.  Remeber it so we can
     * unregister on dispose. */
    private Messenger[] mNetworkFactoryMessenger;
    private NetworkFactory[] mNetworkFactory;
    private NetworkCapabilities[] mNetworkFilter;

    private RegistrantList mNotifyDataSwitchInfo = new RegistrantList();
    private SubscriptionController mSubController = SubscriptionController.getInstance();

    /** M: setup default data sub */
    private DataSubSelector mDataSubSelector;

    /** M: allow data service or not, check setDataAllowed */
    private static boolean mDataAllowed = true;
    protected ConcurrentHashMap<Handler, DcStateParam> mDcSwitchStateChange
            = new ConcurrentHashMap<Handler, DcStateParam>();
    private Runnable mDataNotAllowedTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            logd("disable data service timeout and enable data service again");
            setDataAllowed(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, true, null, 0);
        }
    };

    private ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            logd("Settings change, selfChange=" + selfChange);
            // M:[C2K][IRAT] Pending release request during IRAT.
            if (isNetworkRequestSuspend()) {
                mHasPendingDataSwitch = true;
            } else {
                onSettingsChange();
            }
        }
    };

    public void updatePhoneObject(PhoneProxy phone) {
        if (phone == null) {
            loge("updatePhoneObject phone = null");
            return;
        }

        PhoneBase phoneBase = getActivePhone(phone.getPhoneId());
        if (phoneBase == null) {
            loge("updatePhoneObject phoneBase = null");
            return;
        }

        logd("updatePhoneObject:" + phone);
        for (int i = 0; i < mPhoneNum; i++) {
            if (mPhones[i] == phone) {
                updatePhoneBaseForIndex(i, phoneBase);
                break;
            }
        }
    }

    private void updatePhoneBaseForIndex(int index, PhoneBase phoneBase) {
        logd("updatePhoneBaseForIndex for phone index=" + index);

        ConnectivityManager cm = (ConnectivityManager)mPhones[index].getContext()
            .getSystemService(Context.CONNECTIVITY_SERVICE);

        if (mNetworkFactoryMessenger != null) {
            logd("unregister TelephonyNetworkFactory for phone index=" + index);
            cm.unregisterNetworkFactory(mNetworkFactoryMessenger[index]);
            mNetworkFactoryMessenger[index] = null;
            mNetworkFactory[index] = null;
            mNetworkFilter[index] = null;
        }

        mNetworkFilter[index] = new NetworkCapabilities();
        mNetworkFilter[index].addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_MMS);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_SUPL);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_DUN);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_FOTA);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_CBS);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_IA);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_RCS);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_XCAP);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_EIMS);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        /** M: start */
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_DM);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_WAP);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_NET);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_CMMAIL);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_TETHERING);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_RCSE);
        /** M: end */

        mNetworkFactory[index] = new TelephonyNetworkFactory(this.getLooper(),
                mPhones[index].getContext(), "TelephonyNetworkFactory", phoneBase,
                mNetworkFilter[index]);
        mNetworkFactory[index].setScoreFilter(50);
        mNetworkFactoryMessenger[index] = new Messenger(mNetworkFactory[index]);
        cm.registerNetworkFactory(mNetworkFactoryMessenger[index], "Telephony");

        // M: [C2K] [IRAT] register events using IRAT data switch helper
        // class and listen for IRAT flow. @{
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            logd("[IRAT_DctController] Register for RAT events.");
            mIratDataSwitchHelper[index] =
                    ((SvltePhoneProxy) mPhones[index]).getIratDataSwitchHelper();

            mIratDataSwitchHelper[index].registerForDataConnectionAttached(
                    mRspHandler, EVENT_DATA_ATTACHED + index, null);
            mIratDataSwitchHelper[index].registerForDataConnectionDetached(
                    mRspHandler, EVENT_DATA_DETACHED + index, null);
            mIratDataSwitchHelper[index].registerSetDataAllowed(mRspHandler,
                    EVENT_SET_DATA_ALLOWED + index, null);
            (((SvltePhoneProxy) mPhones[index]).getLtePhone()).mCi
                                                        .registerForSimPlugOut(mRspHandler,
                                                            EVENT_RESTORE_PENDING + index, null);
        } else {
            phoneBase.getServiceStateTracker().registerForDataConnectionAttached(mRspHandler,
                EVENT_DATA_ATTACHED + index, null);
            phoneBase.getServiceStateTracker().registerForDataConnectionDetached(mRspHandler,
                EVENT_DATA_DETACHED + index, null);
            //M: Register for Combine attach
            phoneBase.mCi.registerSetDataAllowed(mRspHandler, EVENT_SET_DATA_ALLOWED + index, null);

            phoneBase.mCi.registerForSimPlugOut(mRspHandler, EVENT_RESTORE_PENDING + index, null);
        }
        // }@

        phoneBase.mCi.registerForNotAvailable(mRspHandler, EVENT_RESTORE_PENDING + index, null);
    }

    private Handler mRspHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            if (msg.what >= EVENT_RESTORE_PENDING) {
                logd("EVENT_SIM" + (msg.what - EVENT_RESTORE_PENDING + 1) + "_RESTORE.");
                restorePendingRequest(msg.what - EVENT_RESTORE_PENDING);

            } else if (msg.what >= EVENT_SET_DATA_ALLOWED) {
                logd("EVENT_PHONE" + (msg.what - EVENT_SET_DATA_ALLOWED + 1) + "_SET_DATA_ALLOWED");
                transitToAttachingState(msg.what - EVENT_SET_DATA_ALLOWED);

            } else if (msg.what >= EVENT_DATA_DETACHED) {
                logd("EVENT_PHONE" + (msg.what - EVENT_DATA_DETACHED + 1) + "_DATA_DETACH.");
                mDcSwitchAsyncChannel[msg.what - EVENT_DATA_DETACHED].notifyDataDetached();

            } else if (msg.what >= EVENT_DATA_ATTACHED) {
                logd("EVENT_PHONE" + (msg.what - EVENT_DATA_ATTACHED + 1) + "_DATA_ATTACH.");
                mDcSwitchAsyncChannel[msg.what - EVENT_DATA_ATTACHED].notifyDataAttached();
            }
        }
    };

    public static DctController getInstance() {
       if (sDctController == null) {
        throw new RuntimeException(
            "DctController.getInstance can't be called before makeDCTController()");
        }
       return sDctController;
    }

    public static DctController makeDctController(PhoneProxy[] phones) {
        if (sDctController == null) {
            logd("makeDctController: new DctController phones.length=" + phones.length);
            sDctController = new DctController(phones);
        }
        logd("makeDctController: X sDctController=" + sDctController);
        return sDctController;
    }

    private DctController(PhoneProxy[] phones) {
        logd("DctController(): phones.length=" + phones.length);
        if (phones == null || phones.length == 0) {
            if (phones == null) {
                loge("DctController(phones): UNEXPECTED phones=null, ignore");
            } else {
                loge("DctController(phones): UNEXPECTED phones.length=0, ignore");
            }
            return;
        }
        mPhoneNum = phones.length;
        mPhones = phones;

        mDcSwitchStateMachine = new DcSwitchStateMachine[mPhoneNum];
        mDcSwitchAsyncChannel = new DcSwitchAsyncChannel[mPhoneNum];
        mDcSwitchStateHandler = new Handler[mPhoneNum];
        mNetworkFactoryMessenger = new Messenger[mPhoneNum];
        mNetworkFactory = new NetworkFactory[mPhoneNum];
        mNetworkFilter = new NetworkCapabilities[mPhoneNum];
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            mIratDataSwitchHelper = new MdIratDataSwitchHelper[mPhoneNum];
        }

        for (int i = 0; i < mPhoneNum; ++i) {
            int phoneId = i;
            mDcSwitchStateMachine[i] = new DcSwitchStateMachine(mPhones[i],
                    "DcSwitchStateMachine-" + phoneId, phoneId);
            mDcSwitchStateMachine[i].start();
            mDcSwitchAsyncChannel[i] = new DcSwitchAsyncChannel(mDcSwitchStateMachine[i], phoneId);
            mDcSwitchStateHandler[i] = new Handler();

            int status = mDcSwitchAsyncChannel[i].fullyConnectSync(mPhones[i].getContext(),
                mDcSwitchStateHandler[i], mDcSwitchStateMachine[i].getHandler());

            if (status == AsyncChannel.STATUS_SUCCESSFUL) {
                logd("DctController(phones): Connect success: " + i);
            } else {
                loge("DctController(phones): Could not connect to " + i);
            }

            // Register for radio state change
            // M: [C2K][IRAT] Change for phone ID.
            PhoneBase phoneBase = getActivePhone(phoneId);
            updatePhoneBaseForIndex(i, phoneBase);
        }

        mContext = mPhones[0].getContext();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SHUTDOWN_IPO);
        filter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        mContext.registerReceiver(mIntentReceiver, filter);
        getActivePhone(0).mCi.registerForAvailable(this, EVENT_RADIO_AVAILABLE, null);

        //Register for settings change.
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(
                Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION),
                false, mObserver);

        /** M: start */
        mDataSubSelector = new DataSubSelector(mContext, mPhoneNum);

        //Since the enter of attaching state may get instance of DctController,
        //we need make sure dctController has already been created.
        setAlwaysAttachSim();
        /** M: end */
    }

    public void dispose() {
        logd("DctController.dispose");
        for (int i = 0; i < mPhoneNum; ++i) {
            ConnectivityManager cm = (ConnectivityManager) mPhones[i].getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
            cm.unregisterNetworkFactory(mNetworkFactoryMessenger[i]);
            mNetworkFactoryMessenger[i] = null;

            // M: Register for Combine attach
            PhoneBase phoneBase = getActivePhone(i);
            phoneBase.mCi.unregisterForSimPlugOut(mRspHandler);
            phoneBase.mCi.unregisterForNotAvailable(mRspHandler);

            /// M: [C2K][IRAT] Unregister for IRAT flow.
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                mIratDataSwitchHelper[i].unregisterForDataConnectionAttached(mRspHandler);
                mIratDataSwitchHelper[i].unregisterForDataConnectionDetached(mRspHandler);
                mIratDataSwitchHelper[i].unregisterSetDataAllowed(mRspHandler);
            } else {
                phoneBase.getServiceStateTracker().unregisterForDataConnectionAttached(mRspHandler);
                phoneBase.getServiceStateTracker().unregisterForDataConnectionDetached(mRspHandler);
                phoneBase.mCi.unregisterSetDataAllowed(mRspHandler);
            }
        }


        getActivePhone(0).mCi.unregisterForAvailable(this);

        mContext.getContentResolver().unregisterContentObserver(mObserver);
    }

    @Override
    public void handleMessage(Message msg) {
        logd("handleMessage msg=" + msg);
        switch (msg.what) {
            case EVENT_PROCESS_REQUESTS:
                onProcessRequest();
                break;
            case EVENT_EXECUTE_REQUEST:
                onExecuteRequest((RequestInfo) msg.obj);
                break;
            case EVENT_EXECUTE_ALL_REQUESTS:
                onExecuteAllRequests(msg.arg1);
                break;
            case EVENT_RELEASE_REQUEST:
                onReleaseRequest((RequestInfo) msg.obj);
                break;
            case EVENT_RELEASE_ALL_REQUESTS:
                onReleaseAllRequests(msg.arg1);
                break;
            case EVENT_TRANSIT_TO_ATTACHING:
                int phoneId = (int) msg.arg1;
                logd("EVENT_TRANSIT_TO_ATTACHING: phone" + phoneId);
                transitToAttachingState(phoneId);
                break;
            case EVENT_CONFIRM_PREDETACH:
                logd("EVENT_CONFIRM_PREDETACH");
                handleConfirmPreDetach(msg);
                break;

            case EVENT_RADIO_AVAILABLE:
                if (mSubController.isReady()) {
                    logd("EVENT_RADIO_AVAILABLE, subinfo is ready");
                    onSubInfoReady();
                }
                break;

            default:
                loge("Un-handled message [" + msg.what + "]");
        }
    }

    private int requestNetwork(NetworkRequest request, int priority, int factoryId ,int gid) {
        logd("requestNetwork request=" + request + ", priority=" + priority
            + ", gid = " + gid);

        RequestInfo requestInfo = mRequestInfos.get(request.requestId);

        //For error handling.
        if (requestInfo != null) {
            String specifier = requestInfo.request.networkCapabilities.getNetworkSpecifier();
            if ((specifier == null || specifier.equals(""))
                    && requestInfo.factoryId != factoryId) {
                logd("We found the request is not empty, put back to its pending queue");
               ((DctController.TelephonyNetworkFactory) mNetworkFactory[requestInfo.factoryId])
                   .addPendingRequest(requestInfo.request);
            }
        }

        requestInfo = new RequestInfo(request, priority, gid);
        requestInfo.factoryId = factoryId;
        mRequestInfos.put(request.requestId, requestInfo);
        processRequests();

        return PhoneConstants.APN_REQUEST_STARTED;
    }

    private int releaseNetwork(NetworkRequest request) {
        RequestInfo requestInfo = mRequestInfos.get(request.requestId);
        logd("releaseNetwork request=" + request + ", requestInfo=" + requestInfo);

        mRequestInfos.remove(request.requestId);

        String specifier = request.networkCapabilities.getNetworkSpecifier();
        boolean bToAttachingState = false;
        int phoneId = -1;
        if (specifier != null && !specifier.equals("")) {
            int subId =  Integer.parseInt(specifier);
            if (subId < SubscriptionManager.INVALID_SUBSCRIPTION_ID &&
                request.networkCapabilities.
                hasCapability(NetworkCapabilities.NET_CAPABILITY_EIMS)) {
                bToAttachingState = true;
                phoneId = mSubController.getPhoneId(subId);
            }
        }

        releaseRequest(requestInfo);
        processRequests();
            
        if (bToAttachingState) {
            logd("ECC w/o SIM, disconnectAllSync to transit to attaching state: " + bToAttachingState
                 + ", Set phoneId: " + phoneId + " to attaching state");
            mDcSwitchAsyncChannel[phoneId].disconnectAllSync();
        }
        
        return PhoneConstants.APN_REQUEST_STARTED;
    }

    void processRequests() {
        logd("processRequests");
        // remove redundant messages firstly, this situation happens offen.
        removeMessages(EVENT_PROCESS_REQUESTS);
        sendMessage(obtainMessage(EVENT_PROCESS_REQUESTS));
    }

    void executeRequest(RequestInfo request) {
        logd("executeRequest, request= " + request);
        sendMessage(obtainMessage(EVENT_EXECUTE_REQUEST, request));
    }

    void executeAllRequests(int phoneId) {
        logd("executeAllRequests, phone:" + phoneId);
        sendMessage(obtainMessage(EVENT_EXECUTE_ALL_REQUESTS, phoneId, 0));
    }

    void releaseRequest(RequestInfo request) {
        logd("releaseRequest, request= " + request);
        sendMessage(obtainMessage(EVENT_RELEASE_REQUEST, request));
    }

    void releaseAllRequests(int phoneId) {
        logd("releaseAllRequests, phone:" + phoneId);
        sendMessage(obtainMessage(EVENT_RELEASE_ALL_REQUESTS, phoneId, 0));
    }

    /** M: try to do re-attach
     *  Disconnect all data connections and do detach if current state is ATTACHING or ATTACHED
     *  Once detach is done, all requests would be processed again when entering IDLE state
     *  That means re-attach will be triggered
     */
    void disconnectAll() {
        int activePhoneId = -1;
        for (int i = 0; i < mDcSwitchStateMachine.length; i++) {
            if (!mDcSwitchAsyncChannel[i].isIdleSync()) {
                activePhoneId = i;
                break;
            }
        }

        if (activePhoneId >= 0) {
            logd("disconnectAll, active phone:" + activePhoneId);
            mDcSwitchAsyncChannel[activePhoneId].disconnectAllSync();
        } else {
            logd("disconnectAll but no active phone, process requests");
        }
    }

    private void onProcessRequest() {
        for (int i = 0; i < getGroupNumbers(); i++) {
            onProcessGroup(i);
        }
    }

    private void onProcessGroup(int group) {
        //process all requests
        //1. Check all requests and find subscription of the top priority
        //   request
        //2. Is current data allowed on the selected subscription
        //2-1. If yes, execute all the requests of the sub
        //2-2. If no, set data not allow on the current PS subscription
        //2-2-1. Set data allow on the selected subscription

        int phoneId = getTopPriorityRequestPhoneId(group);
        int activePhoneId = -1;

        for (int i = 0; i < mDcSwitchStateMachine.length; i++) {
            if (getGroupId(i) == group && !mDcSwitchAsyncChannel[i].isIdleSync()) {
                activePhoneId = i;
                break;
            }
        }

        logd("onProcessGroup phoneId=" + phoneId + ", groupId=" + group
                + ", activePhoneId=" + activePhoneId);

        /** M: handle data not allowed that all state should be set to IDLD */
        if (mDataAllowed) {
            if (activePhoneId == -1 || activePhoneId == phoneId) {
                Iterator<Integer> iterator = mRequestInfos.keySet().iterator();

                if (activePhoneId == -1 && !iterator.hasNext()) {
                    logd("No active phone, set phone" + phoneId + " to attaching state");
                    transitToAttachingState(phoneId);
                }

                while (iterator.hasNext()) {
                    RequestInfo requestInfo = mRequestInfos.get(iterator.next());
                    if (getRequestPhoneId(requestInfo.request) == phoneId
                            && requestInfo.mGId == group
                            && !requestInfo.executed) {
                        mDcSwitchAsyncChannel[phoneId].connectSync(requestInfo);
                    }
                }
            } else {
                mDcSwitchAsyncChannel[activePhoneId].disconnectAllSync();
            }
        } else {
            if (activePhoneId != -1) {
                logd("onProcessRequest data is not allowed, release all requests");
                onReleaseAllRequests(activePhoneId);
            } else {
                logd("onProcessRequest data is not allowed and already in IDLE state");
            }
        }
    }

    private void onExecuteRequest(RequestInfo requestInfo) {
        logd("onExecuteRequest request=" + requestInfo);

        //MTK: Fix a timing issue if we already restore the request to pending queue.
        // Check the request which we want to execute is still in the requestInfo or not.
//        if (!requestInfo.executed) {
        if (needExecuteRequest(requestInfo)) {
            requestInfo.executed = true;
            String apn = apnForNetworkRequest(requestInfo.request);
            int phoneId = getRequestPhoneId(requestInfo.request);
            logd("onExecuteRequest apn = " + apn + " phoneId=" + phoneId);
            requestInfo.phoneId = phoneId; // remember the executed phone id.
            PhoneBase phoneBase = getActivePhone(phoneId);
            DcTrackerBase dcTracker = phoneBase.mDcTracker;
            dcTracker.incApnRefCount(apn);
        }
    }

    private void onExecuteAllRequests(int phoneId) {
        logd("onExecuteAllRequests phoneId=" + phoneId + ",request size = " + mRequestInfos.size());
        Iterator<Integer> iterator = mRequestInfos.keySet().iterator();
        while (iterator.hasNext()) {
            RequestInfo requestInfo = mRequestInfos.get(iterator.next());
            if (getRequestPhoneId(requestInfo.request) == phoneId) {
                onExecuteRequest(requestInfo);
            }
        }
    }

    private void onReleaseRequest(RequestInfo requestInfo) {
        logd("onReleaseRequest request=" + requestInfo);
        if (requestInfo != null && requestInfo.executed) {
            String apn = apnForNetworkRequest(requestInfo.request);
            //int phoneId = getRequestPhoneId(requestInfo.request);
            int phoneId = requestInfo.phoneId;
            PhoneBase phoneBase = getActivePhone(phoneId);
            DcTrackerBase dcTracker = phoneBase.mDcTracker;
            dcTracker.decApnRefCount(apn);
            requestInfo.executed = false;
        }
    }

    private void onReleaseAllRequests(int phoneId) {
        logd("onReleaseAllRequests phoneId=" + phoneId + ",request size = " + mRequestInfos.size());
        Iterator<Integer> iterator = mRequestInfos.keySet().iterator();
        while (iterator.hasNext()) {
            RequestInfo requestInfo = mRequestInfos.get(iterator.next());
            //if (getRequestPhoneId(requestInfo.request) == phoneId) {
            if (requestInfo.phoneId == phoneId) {
                String apn = apnForNetworkRequest(requestInfo.request);
                PhoneBase phoneBase = getActivePhone(phoneId);
                DcTrackerBase dcTracker = phoneBase.mDcTracker;
                if (PhoneConstants.APN_TYPE_IMS.equals(apn) &&
                    Phone.REASON_QUERY_PLMN.equals(dcTracker.mSetDataAllowedReason)) {
                    logd("onReleaseAllRequests, not release ims pdn for plmn searching");
                } else {
                    onReleaseRequest(requestInfo);
                }
            }
        }
    }

    private void onSettingsChange() {
        int dataSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        //Sub Selection
        dataSubId = mSubController.getDefaultDataSubId();

        /** M: Set data ICCID for combination attach */
        int dataPhoneId = SubscriptionManager.getPhoneId(dataSubId);
        String defaultIccid = "";
        if (dataPhoneId >= 0) {
            if (dataPhoneId >= PROPERTY_ICCID_SIM.length) {
                loge("onSettingsChange, phoneId out of boundary:" + dataPhoneId);
            } else {
                defaultIccid = SystemProperties.get(PROPERTY_ICCID_SIM[dataPhoneId]);
                logd("onSettingsChange, Iccid = " + defaultIccid + ", dataPhoneId:" + dataPhoneId);
                if (defaultIccid.equals("")) {
                    logd("onSettingsChange, get iccid fail");
                    SystemProperties.set(PROPERTY_RIL_DATA_ICCID, defaultIccid);
                    return;
                }
            }
        } else {
            logd("onSettingsChange, default data unset");
        }
        SystemProperties.set(PROPERTY_RIL_DATA_ICCID, defaultIccid);

        logd("onSettingsChange, data sub: " + dataSubId
                + ", defaultIccid: " + defaultIccid);

        int i = 0;
        Iterator<Integer> iterator = mRequestInfos.keySet().iterator();
        while (iterator.hasNext()) {
            RequestInfo requestInfo = mRequestInfos.get(iterator.next());
            String specifier = requestInfo.request.networkCapabilities.getNetworkSpecifier();
            String apn = apnForNetworkRequest(requestInfo.request);
            if (specifier == null || specifier.equals("")) {
                if (requestInfo.executed && !apn.equals(PhoneConstants.APN_TYPE_IMS)) {
                    onReleaseRequest(requestInfo);
                }

                for (i = 0; i < mPhoneNum; ++i) {
                    ((DctController.TelephonyNetworkFactory) mNetworkFactory[i])
                           .addPendingRequest(requestInfo.request);
                }
                iterator.remove();
            }
        }

        for (i = 0; i < mPhoneNum; ++i) {
            ((DctController.TelephonyNetworkFactory) mNetworkFactory[i])
                .evalPendingRequest();
        }

        processRequests();
    }

    private int getTopPriorityRequestPhoneId(int group) {
        RequestInfo retRequestInfo = null;
        int phoneId = 0;
        int priority = -1;

        //TODO: Handle SIM Switch
        for (int i = 0; i < mPhoneNum; i++) {
            Iterator<Integer> iterator = mRequestInfos.keySet().iterator();
            while (iterator.hasNext()) {
                RequestInfo requestInfo = mRequestInfos.get(iterator.next());
                logd("selectExecPhone requestInfo = " + requestInfo);
                if (getRequestPhoneId(requestInfo.request) == i
                        && priority < requestInfo.priority
                        && requestInfo.mGId == group) {
                    priority = requestInfo.priority;
                    retRequestInfo = requestInfo;
                }
            }
        }

        if (retRequestInfo != null) {
            phoneId = getRequestPhoneId(retRequestInfo.request);
        } else {
            phoneId = getPreferPhoneId(group);
        }

        logd("getTopPriorityRequestPhoneId = " + phoneId + ", priority = " + priority
                + ", gruop = " + group);

        return phoneId;
    }

    private boolean isValidPhoneId(int phoneId) {
        return phoneId >= 0 && phoneId <= mPhoneNum;
    }

    private void onSubInfoReady() {
        logd("onSubInfoReady mPhoneNum=" + mPhoneNum);
        for (int i = 0; i < mPhoneNum; ++i) {
            int subId = mPhones[i].getSubId();
            logd("onSubInfoReady handle pending requests subId=" + subId);
            mNetworkFilter[i].setNetworkSpecifier(String.valueOf(subId));
            ((DctController.TelephonyNetworkFactory) mNetworkFactory[i]).evalPendingRequest();
        }
        processRequests();
    }

    private String apnForNetworkRequest(NetworkRequest nr) {
        NetworkCapabilities nc = nr.networkCapabilities;
        // For now, ignore the bandwidth stuff
        if (nc.getTransportTypes().length > 0 &&
                nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == false) {
            return null;
        }

        // in the near term just do 1-1 matches.
        // TODO - actually try to match the set of capabilities
        int type = -1;
        String name = null;

        boolean error = false;
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_DEFAULT;
            type = ConnectivityManager.TYPE_MOBILE;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_MMS)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_MMS;
            type = ConnectivityManager.TYPE_MOBILE_MMS;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_SUPL)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_SUPL;
            type = ConnectivityManager.TYPE_MOBILE_SUPL;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_DUN)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_DUN;
            type = ConnectivityManager.TYPE_MOBILE_DUN;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_FOTA)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_FOTA;
            type = ConnectivityManager.TYPE_MOBILE_FOTA;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_IMS;
            type = ConnectivityManager.TYPE_MOBILE_IMS;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_CBS)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_CBS;
            type = ConnectivityManager.TYPE_MOBILE_CBS;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_IA)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_IA;
            type = ConnectivityManager.TYPE_MOBILE_IA;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_RCS)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_RCS;
            type = ConnectivityManager.TYPE_MOBILE_RCS;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_XCAP)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_XCAP;
            type = ConnectivityManager.TYPE_MOBILE_XCAP;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_EIMS)) {
            if (name != null) error = true;
          //M
            name = PhoneConstants.APN_TYPE_EMERGENCY;
            type = ConnectivityManager.TYPE_MOBILE_EMERGENCY;
            logd("### EIMS type tmp support");
            //name = null;
            //loge("EIMS APN type not yet supported");
        }

        /** M: start */
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_DM)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_DM;
            type = ConnectivityManager.TYPE_MOBILE_DM;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_WAP)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_WAP;
            type = ConnectivityManager.TYPE_MOBILE_WAP;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NET)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_NET;
            type = ConnectivityManager.TYPE_MOBILE_NET;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_CMMAIL)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_CMMAIL;
            type = ConnectivityManager.TYPE_MOBILE_CMMAIL;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_TETHERING)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_TETHERING;
            type = ConnectivityManager.TYPE_MOBILE_TETHERING;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_RCSE)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_RCSE;
            type = ConnectivityManager.TYPE_MOBILE_RCSE;
        }
        /** M: end */

        if (error) {
            loge("Multiple apn types specified in request - result is unspecified!");
        }
        if (type == -1 || name == null) {
            loge("Unsupported NetworkRequest in Telephony: nr=" + nr);
            return null;
        }
        return name;
    }

    /**
     * return the request slotId, i.e. the SvltePhoneProxy index/phoneId.
     */
    private int getRequestPhoneId(NetworkRequest networkRequest) {
        String specifier = networkRequest.networkCapabilities.getNetworkSpecifier();
        String apn = apnForNetworkRequest(networkRequest);
        logd("getRequestPhoneId apn = " + apn);

        int subId;
        if (specifier == null || specifier.equals("")) {
            subId = mSubController.getDefaultDataSubId();
        } else {
            subId = Integer.parseInt(specifier);
        }
        int phoneId = mSubController.getPhoneId(subId);
        logd("getRequestPhoneId:specifier=" + specifier + " subId=" + subId + " phoneId=" + phoneId);

        // Google design in the case of invalid phone id would go establish SIM1 (phoneId=0).
        // It might to handle issue like request with no specifier or use startUsingNewtork API.
        // However, this resulting issue that data icon appear and disappear again in some case.
        // Scenario:
        //     SIM A at slot1 and set it as default data,
        //     Remove SIMA and insert SIMB at slot1 and set it as default data.
        //     Re-insert SIM A back to slot1, reboot and do not click the default data pop-up.
        if (!SubscriptionManager.isValidPhoneId(phoneId)

            && !apn.equals(PhoneConstants.APN_TYPE_DEFAULT)) {

            phoneId = 0;
            if (!SubscriptionManager.isValidPhoneId(phoneId)) {
                throw new RuntimeException("Should not happen, no valid phoneId");
            }
        }

        // M: [C2K][IRAT] change phone ID for IRAT.
        logd("before mapping, getRequestPhoneId phoneId= " + phoneId);
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            phoneId = SvlteUtils.getSlotId(phoneId);
        }
        logd("getRequestPhoneId phoneId= " + phoneId);
        return phoneId;
    }

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            logd("onReceive: action=" + action);
            if (action.equals(ACTION_SHUTDOWN_IPO)) {
                logd("IPO Shutdown, clear PROPERTY_DATA_ALLOW_SIM, PROPERTY_IA_APN_SET_ICCID");
                logd("IPO Shutdown, clear TEMP_IA");
                SystemProperties.set(PROPERTY_DATA_ALLOW_SIM, "");
                SystemProperties.set(PROPERTY_IA_APN_SET_ICCID, "");
                SystemProperties.set(PROPERTY_TEMP_IA, "");
                SystemProperties.set(PROPERTY_TEMP_IA_APN, "");
            } else if (action.equals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED)) {
                onSubInfoReady();
            }
        }
    };

    private static void logv(String s) {
        if (DBG) Rlog.v(LOG_TAG, "[DctController] " + s);
    }

    private static void logd(String s) {
        if (DBG) Rlog.d(LOG_TAG, "[DctController] " + s);
    }

    private static void logw(String s) {
        if (DBG) Rlog.w(LOG_TAG, "[DctController] " + s);
    }

    private static void loge(String s) {
        if (DBG) Rlog.e(LOG_TAG, "[DctController] " + s);
    }

    private class TelephonyNetworkFactory extends NetworkFactory {
        private final SparseArray<NetworkRequest> mPendingReq = new SparseArray<NetworkRequest>();
        private Phone mPhone;
        private int mGroupId;

        public TelephonyNetworkFactory(Looper l, Context c, String TAG, Phone phone,
                NetworkCapabilities nc) {
            super(l, c, TAG, nc);
            mPhone = phone;
            mGroupId = getGroupId(mPhone.getPhoneId());
            log("NetworkCapabilities: " + nc);
        }

        @Override
        protected void needNetworkFor(NetworkRequest networkRequest, int score) {
            // figure out the apn type and enable it
            log("Cellular needs Network for " + networkRequest);

            int subId = mPhone.getSubId();
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                subId = SvlteUtils.getSvlteSubIdBySubId(subId);
            }

            if (!SubscriptionManager.isUsableSubIdValue(subId)) {
                if (networkRequest.networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_EIMS)) {
                    log("Sub Info has not been ready or during IRAT, but EIMS request, not put into pending request!!");
                } else {
                    log("Sub Info has not been ready or during IRAT, pending request.");
                    mPendingReq.put(networkRequest.requestId, networkRequest);
                    return;
                }
            }

            int requestPhoneId = getRequestPhoneId(networkRequest);
            int currentPhoneId = mPhone.getPhoneId();
            log("[IRAT_DctController] needNetworkFor: requestPhoneId = "
                    + requestPhoneId + ", currentPhoneId = " + currentPhoneId);
            // M: mapping LTE phone id to slot index
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                currentPhoneId = SvlteUtils.getSlotId(currentPhoneId);
            }
            if (requestPhoneId == currentPhoneId) {
                DcTrackerBase dcTracker = ((PhoneBase) mPhone).mDcTracker;
                String apn = apnForNetworkRequest(networkRequest);
                if (dcTracker.isApnSupported(apn)) {
                    requestNetwork(networkRequest, dcTracker.getApnPriority(apn)
                            ,currentPhoneId ,mGroupId);
                } else {
                    log("Unsupported APN");
                }
            } else {
                log("Request not send, put to pending");
                mPendingReq.put(networkRequest.requestId, networkRequest);
            }
        }

        @Override
        protected void releaseNetworkFor(NetworkRequest networkRequest) {
            log("Cellular releasing Network for " + networkRequest);

            //M: Fix google Issue
            //if (!SubscriptionManager.isUsableSubIdValue(mPhone.getSubId())) {
            if (mPendingReq.get(networkRequest.requestId) != null ) {
                log("Sub Info has not been ready, remove request.");
                mPendingReq.remove(networkRequest.requestId);
                return;
            }

            // M: [C2K][IRAT] Mapping phone ID for SVLTE during release
            int phoneId = mPhone.getPhoneId();
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                phoneId = SvlteUtils.getSlotId(phoneId);
                logd("[IRAT_DctController] releaseNetworkFor: IRAT change phone ID:" + phoneId);
            }

            if (getRequestPhoneId(networkRequest) == phoneId) {
                DcTrackerBase dcTracker = ((PhoneBase) mPhone).mDcTracker;
                String apn = apnForNetworkRequest(networkRequest);
                if (dcTracker.isApnSupported(apn)) {
                    releaseNetwork(networkRequest);
                } else {
                    log("Unsupported APN");
                }

            } else {
                log("Request not release");
            }
        }

        @Override
        protected void log(String s) {
            if (DBG) Rlog.d(LOG_TAG, "[TNF " + mPhone.getSubId() + "]" + s);
        }

        public void addPendingRequest(NetworkRequest networkRequest) {
            log("addPendingRequest, request:" + networkRequest);
            mPendingReq.put(networkRequest.requestId, networkRequest);
        }

        public void evalPendingRequest() {
            log("evalPendingRequest, pending request size is " + mPendingReq.size());
            int key = 0;
            int pendingReqSize = mPendingReq.size();
            // The use of list to keep all reqeusts
            List<NetworkRequest> processList = new ArrayList<NetworkRequest>();

            // Add requests to processList, below implementation is to avoid error while process
            // 2 or more requests in the pending queue at the same time.
            for (int i = 0; i < pendingReqSize; i++) {
                key = mPendingReq.keyAt(i);
                log("evalPendingRequest:mPendingReq= " + mPendingReq + " i=" + i + " Key = " + key);
                NetworkRequest request = mPendingReq.get(key);
                processList.add(request);
            }

            // Remove all request and needNetworkFor will add it to pending if necessary.
            mPendingReq.clear();
            log("evalPendingRequest:mPendingReq clear");

            // Remove processed request.
            for (NetworkRequest request : processList) {
                log("evalPendingRequest:ready to do needNetworkFor and request = " + request);
                needNetworkFor(request, 0);
            }
        }
    }

    //MTK START
    /** M: get acive data phone Id */
    public int getActiveDataPhoneId() {
        int activePhoneId = SubscriptionManager.INVALID_PHONE_INDEX;
        for (int i = 0; i < mDcSwitchStateMachine.length; i++) {
            if (!mDcSwitchAsyncChannel[i].isIdleSync()) {
                activePhoneId = i;
                break;
            }
        }
        return activePhoneId;
    }

    /** M: allow data service or not and can set a max timeout for setting data not allowed */
    public void setDataAllowed(long subId, boolean allowed, String reason, long timeout) {
        logd("setDataAllowed subId=" + subId + ", allowed=" + allowed + ", reason="
            + reason + ", timeout=" + timeout);
        mDataAllowed = allowed;
        if (mDataAllowed) {
            mRspHandler.removeCallbacks(mDataNotAllowedTimeoutRunnable);
        }

        setDataAllowedReasonToDct(reason);
        processRequests();

        if (!mDataAllowed && timeout > 0) {
            logd("start not allow data timer and timeout=" + timeout);
            mRspHandler.postDelayed(mDataNotAllowedTimeoutRunnable, timeout);
        }
    }

    private void setDataAllowedReasonToDct(String reason) {
        logd("setDataAllowedReasonToDct reason: " + reason);
        for (int i = 0; i < mPhoneNum; i++) {
            PhoneBase phoneBase = getActivePhone(i);
            DcTrackerBase dcTracker = phoneBase.mDcTracker;
            dcTracker.mSetDataAllowedReason = reason;
        }
    }

    public synchronized void registerForDcSwitchStateChange(Handler h, int what,
            Object obj, DcStateParam param) {

        Registrant r = new Registrant(h, what, obj);
        DcStateParam dcState = null;

        if (param == null) {
            dcState = new DcStateParam("Don't care", false);
        } else {
            dcState = param;
        }

        if (DBG) {
            logd("registerForDcSwitchStateChange: dcState = " + dcState);
        }

        dcState.mRegistrant = r;
        mDcSwitchStateChange.put(h, dcState);
    }

    public synchronized void unregisterForDcSwitchStateChange(Handler h) {
        if (DBG) {
            logd("unregisterForDcSwitchStateChange");
        }
        mDcSwitchStateChange.remove(h);
    }

    synchronized void notifyDcSwitchStateChange(String state, int phoneId, String reason) {
        mUserCnt = 0;
        mTransactionId++;

        for (DcStateParam param : mDcSwitchStateChange.values()) {
            String user = param.mUser;
            Registrant r = param.mRegistrant;
            Message msg = null;

            if (state.equals(DcSwitchStateMachine.DCSTATE_PREDETACH_CHECK) && param.mNeedCheck) {
                msg = obtainMessage(EVENT_CONFIRM_PREDETACH, mTransactionId, phoneId, user);
                mUserCnt++;
            }

            DcStateParam dcState = new DcStateParam(state, phoneId, reason, msg);
            AsyncResult ar = new AsyncResult(null, dcState, null);
            r.notifyRegistrant(ar);
        }

        if (DBG) {
            logd("notifyDcSwitchStateChange: user:" + mUserCnt + ", ID:" + mTransactionId);
        }

        if (state.equals(DcSwitchStateMachine.DCSTATE_PREDETACH_CHECK) && mUserCnt == 0) {
            obtainMessage(EVENT_CONFIRM_PREDETACH, mTransactionId, phoneId,
                    "No User").sendToTarget();
        }
    }

    private void handleConfirmPreDetach(Message msg) {
        int transAct = msg.arg1;
        int phoneId = msg.arg2;
        String user = (String) msg.obj;

        if (mTransactionId != transAct) {
            if (DBG) {
                logd("unExcept transAct: " + transAct);
            }
        }

        if (mUserCnt > 0) {
            mUserCnt--;
        }

        if (DBG) {
            logd("handleConfirmPreDetach: user:" + user + ", ID:" + transAct + ", phone" + phoneId
                + ", Remain User:" + mUserCnt);
        }

        if (mUserCnt == 0) {
            mDcSwitchAsyncChannel[phoneId].confirmPreDetachSync();
        }
    }

    public String getDcSwitchState(int phoneId) {
        /// M: [C2K][IRAT] use IRAT support slot ID instead of LTE_SUB_ID.
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            phoneId = SvlteUtils.getSvltePhoneIdByPhoneId(phoneId);
        }
        String ret = mDcSwitchAsyncChannel[phoneId].requestDcSwitchStateSync();
        logd("getDcSwitchState: Phone" + phoneId + " state = " + ret);
        return ret;
    }

    private void setAlwaysAttachSim() {
        MultiSimVariants config = TelephonyManager.getDefault().getMultiSimConfiguration();
        // We divide phones into different groups according to multi sim config.
        if (config == MultiSimVariants.DSDS
                || CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                || config == MultiSimVariants.TSTS) {
            String attachPhone = "";
            attachPhone = SystemProperties.get(PROPERTY_DATA_ALLOW_SIM, "");
            logd(" attachPhone: " + attachPhone);
            if (attachPhone != null && !attachPhone.equals("")) {
                int phoneId = Integer.parseInt(attachPhone);
                if (phoneId >= 0 && phoneId < mPhoneNum) {
                    logd("Set phone" + phoneId + " to attaching state");
                    sendMessage(obtainMessage(EVENT_TRANSIT_TO_ATTACHING, phoneId, 0));
                }
            }
        } else if (config == MultiSimVariants.DSDA) {
            //TODO: Extend to nSmA
            //FIXME: Need to get total group numbers
            for (int i = 0; i < mPhoneNum; i++) {
                sendMessage(obtainMessage(EVENT_TRANSIT_TO_ATTACHING, i, 0));
            }
        }
    }

    /** M: transit to attaching state. */
    private void transitToAttachingState(int targetPhoneId) {
        int groupId = getGroupId(targetPhoneId);
        int topPriorityPhoneId = getTopPriorityRequestPhoneId(groupId);
        int activePhoneId = -1;
        if (topPriorityPhoneId == targetPhoneId
            || (CdmaFeatureOptionUtils.isCdmaLteDcSupport() && mRequestInfos.isEmpty())) {
            for (int i = 0; i < mDcSwitchStateMachine.length; i++) {
                if (!mDcSwitchAsyncChannel[i].isIdleSync() && groupId == getGroupId(i)) {
                    activePhoneId = i;
                    break;
                }
            }
            if (activePhoneId != -1 && activePhoneId != targetPhoneId) {
                logd("transitToAttachingState: disconnect other phone");
                mDcSwitchAsyncChannel[activePhoneId].disconnectAllSync();
            } else {
                logd("transitToAttachingState: connect");
                mDcSwitchAsyncChannel[targetPhoneId].connectSync(null);
            }
        } else {
            logd("transitToAttachingState: disconnect target phone");
            mDcSwitchAsyncChannel[targetPhoneId].connectSync(null);
            mDcSwitchAsyncChannel[targetPhoneId].disconnectAllSync();
        }
    }

    protected ConcurrentHashMap<String, ApnContext> getApnContexts(int phoneId) {
        PhoneBase phoneBase = getActivePhone(phoneId);
        DcTrackerBase dcTracker = phoneBase.mDcTracker;
        ConcurrentHashMap<String, ApnContext> apnContexts = null;
        if (dcTracker != null) {
            apnContexts = dcTracker.getApnContexts();
        } else {
            loge("DcTracker is null");
        }
        return apnContexts;
    }

    private void restorePendingRequest(int phoneId) {
        Iterator<Integer> iterator = mRequestInfos.keySet().iterator();
        while (iterator.hasNext()) {
            RequestInfo requestInfo = mRequestInfos.get(iterator.next());
            logd("restorePendingRequest requestInfo = " + requestInfo);
            if (getRequestPhoneId(requestInfo.request) == phoneId) {
                ((DctController.TelephonyNetworkFactory) mNetworkFactory[phoneId])
                        .addPendingRequest(requestInfo.request);
                onReleaseRequest(requestInfo);
                iterator.remove();
            }
        }
    }

    private boolean needExecuteRequest(RequestInfo requestInfo) {
        RequestInfo checkInfo = mRequestInfos.get(requestInfo.request.requestId);
        boolean ret = false;

        if (!requestInfo.executed && checkInfo != null) {
            ret = true;
        }

        logd("needExecuteRequest return " + ret + ", checkInfo = " + checkInfo);
        
        return ret;
    }

    public boolean isActivePhone(int phoneId) {
        /// M: [C2K][IRAT] use IRAT support slot ID instead of LTE_SUB_ID.
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            phoneId = SvlteUtils.getSvltePhoneIdByPhoneId(phoneId);
        }
        return !mDcSwitchAsyncChannel[phoneId].isIdleSync();
    }

    public class DcStateParam {
        private String mState;
        private int mPhoneId;
        private Message mMessage;

        private String mUser;
        private boolean mNeedCheck;
        private Registrant mRegistrant;
        private String mReason;

        public DcStateParam(String state, int phoneId, String reason, Message msg) {
            mState = state;
            mPhoneId = phoneId;
            mReason = reason;
            mMessage = msg;
        }

        public DcStateParam(String user, boolean needCheckDisconnect) {
            mUser = user;
            mNeedCheck = needCheckDisconnect;
        }

        public String getState() {
            return mState;
        }

        public int getPhoneId() {
            return mPhoneId;
        }

        public String getReason() {
            return mReason;
        }
        public boolean confirmPreCheckDetach() {
            logd("confirmPreCheckDetach, msg = " + mMessage);
            if (mMessage == null) {
                return false;
            } else {
                mMessage.sendToTarget();
                return true;
            }
        }

        @Override
        public String toString() {
            return "[ mState=" + mState + ", mReason=" + mReason + ", mPhoneId =" + mPhoneId
                    + ", user = " + mUser + ", needCheck = " + mNeedCheck
                    + ", message = " + mMessage + ", Registrant = " + mRegistrant + "]";
        }
    }

    //Multi-Group START
    private int getGroupId(int phoneId) {
        MultiSimVariants config = TelephonyManager.getDefault().getMultiSimConfiguration();
        int groupId = 0;

        // We divide phones into different groups according to multi sim config.
        // M: [C2K] [IRAT] set group id to 0 for all phones in SVLTE.
        if (!CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                && config == MultiSimVariants.DSDA) {
            groupId = phoneId;
        }

        logd("getGroupId phone = " + phoneId + ", groupId = "  + groupId);
        return groupId;
    }

    private int getGroupNumbers() {
        MultiSimVariants config = TelephonyManager.getDefault().getMultiSimConfiguration();
        int groupNumber = 1;

        //TODO: Enhance to nSmA
        if (!CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                && config == MultiSimVariants.DSDA) {
            groupNumber = mPhoneNum;
        }

        logd("getGroupNumbers groupNumber = " + groupNumber);
        return groupNumber;
    }

    private int getPreferPhoneId(int groupId) {

        //TODO: Enhance to nSmA
        int dataPhoneId = getDefaultDataPhoneId();
        int attachPhoneId = getAttachPropertyPhone();
        if (dataPhoneId >= 0 && dataPhoneId < mPhoneNum && getGroupId(dataPhoneId) == groupId) {
            logd("getPreferPhoneId: return default data phone Id = " + dataPhoneId);
            return dataPhoneId;
        } else if (attachPhoneId >= 0 && attachPhoneId < mPhoneNum
                && getGroupId(dataPhoneId) == groupId) {
            logd("get attach phone Id = " + attachPhoneId);
            return attachPhoneId;
        } else {
            String curr3GSim = SystemProperties.get("persist.radio.simswitch", "");
            int curr3GPhoneId = -1;
            logd("current 3G Sim = " + curr3GSim);
            if (curr3GSim != null && !curr3GSim.equals("")) {
                curr3GPhoneId = Integer.parseInt(curr3GSim) - 1;
            }
            if (curr3GPhoneId != -1 && getGroupId(curr3GPhoneId) == groupId) {
                logd("getPreferPhoneId return current 3G SIM: " + curr3GSim);
                return curr3GPhoneId;
            }
        }
        logd("getPreferPhoneId: no prefer phone found, return default value: " + groupId);
        return groupId;
    }
    //Multi-Group END

    private int getDefaultDataPhoneId() {
        int dataPhoneId = mSubController.getPhoneId(mSubController.getDefaultDataSubId());
        String dataIccid = "";
        String simIccid = "";
        if (dataPhoneId < 0 || dataPhoneId > mPhoneNum) {
            logd("getDefaultDataPhoneId: invalid phone ID " + dataPhoneId + " ,find property");
            dataIccid = SystemProperties.get(PROPERTY_RIL_DATA_ICCID);
            if (dataIccid != null && !dataIccid.equals("")) {
                for (int i = 0; i < mPhoneNum; i++) {
                    simIccid = SystemProperties.get(PROPERTY_ICCID_SIM[i]);
                    if (simIccid != null && dataIccid.equals(simIccid)) {
                        logd("getDefaultDataPhoneId: Sim" + i + " iccid matched: " + simIccid);
                        dataPhoneId = i;
                        break;
                    }
                }
            }
        }
        logd("getDefaultDataPhoneId: dataPhoneId = " + dataPhoneId);

        return dataPhoneId;
    }

    private int getAttachPropertyPhone() {
        //Since the enter of attaching state may get instance of DctController,
        //we need make sure dctController has already been created.
        String attachPhone = "";
        int phoneId = -1;
        attachPhone = SystemProperties.get(PROPERTY_DATA_ALLOW_SIM, "");
        logd("attachPhone: " + attachPhone);
        if (attachPhone != null && !attachPhone.equals("")) {
            phoneId = Integer.parseInt(attachPhone);
        }

        logd("getAttachPropertyPhone: " + phoneId);

        return phoneId;
    }

    //MTK END
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("DctController:");
        try {
            for (DcSwitchStateMachine dssm : mDcSwitchStateMachine) {
                dssm.dump(fd, pw, args);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");

        try {
            for (Entry<Integer, RequestInfo> entry : mRequestInfos.entrySet()) {
                pw.println("mRequestInfos[" + entry.getKey() + "]=" + entry.getValue());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
        pw.flush();
        pw.println("TelephonyNetworkFactories:");
        for (NetworkFactory tnf : mNetworkFactory) {
            pw.println("  " + tnf);
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
        pw.flush();
    }

    // M: [C2K][IRAT] code start @{
    /**
     * return the active concrete phone object which data is used.
     * @param phoneId the index of SvltePhoneProxy array
     * @return SVLTE mode will return recorded data phone, CSFB just the one.
     */
    private PhoneBase getActivePhone(int phoneId) {
        PhoneBase psPhone = null;
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() && SvlteUtils.isActiveSvlteMode(phoneId)) {
            psPhone = (PhoneBase) ((SvltePhoneProxy) mPhones[phoneId]).getPsPhone();
        } else {
            psPhone = (PhoneBase) ((PhoneProxy) mPhones[phoneId]).getActivePhone();
        }
        return psPhone;
    }

    private boolean isNetworkRequestSuspend() {
        return mSuspendNetworkRequest;
    }

    /**
     * Suspend network request and data switch request.
     */
    public void suspendNetworkRequest() {
        logd("[IRAT_DctController] suspendNetworkRequest: mSuspendNetworkRequest = "
                + mSuspendNetworkRequest);
        mSuspendNetworkRequest = true;
    }

    /**
     * Resume network request, called in IRAT finished.
     */
    public void resumeNetworkRequest() {
        logd("[IRAT_DctController] resumeNetworkRequest: mSuspendNetworkRequest = "
                + mSuspendNetworkRequest);
        mSuspendNetworkRequest = false;

        if (mHasPendingDataSwitch) {
            onSettingsChange();
            mHasPendingDataSwitch = false;
        }
    }
    //M: [C2K][IRAT] end }@
}

