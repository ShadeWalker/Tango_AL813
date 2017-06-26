/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.phone;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.TaskStackBuilder;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.IBluetoothHeadsetPhone;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.IBinder;
import android.os.IPowerManager;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UpdateLock;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Settings.System;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.util.Preconditions;
import com.android.phone.common.CallLogAsync;
import com.android.server.sip.SipService;
import com.google.android.collect.Sets;
import java.util.ArrayList;
import java.util.List;

import com.mediatek.internal.telephony.RadioManager;
import com.mediatek.phone.ext.ExtensionManager;

import com.mediatek.phone.PhoneInterfaceManagerEx;
import com.mediatek.settings.TelephonyUtils;

import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.ltedc.svlte.SvltePhoneProxy;

import java.util.Set;
import android.app.Dialog;
import android.widget.TextView;
import android.view.WindowManager;
import android.provider.Settings;
import android.app.AlertDialog;
import android.content.DialogInterface;
import com.android.internal.telephony.PhoneProxy;
import android.widget.Toast;
import com.mediatek.telephony.TelephonyManagerEx;


/**
 * Global state for the telephony subsystem when running in the primary
 * phone process.
 */
public class PhoneGlobals extends ContextWrapper {
    public static final String LOG_TAG = "PhoneApp";

	//add by zhangjinqiang for HW_SIMLock -start
	private PhoneProxy mPhone = null;
	private String TAG_X = "zhangjinqiang";
	private static final int EVENT_READ_SML_INFO=19;
	private static final int EVENT_READ_SIM1_INFO = 20;
	private static final int EVENT_READ_SIM2_INFO = 18;
	private static final int EVENT_GET_SML_PASSWORD_FROM_NVRAM = 21;
	private static final int EVENT_ADDLOCK_SML_NVRAM = 22;
	private static final int EVENT_UNLOCK_SML_NVRAM = 23;
	private static final int EVENT_GET_SML_RETRYCOUNTS=24;
	//private static final int EVENT_READ_SML_SHA = 25;
	private HQSIMLockReceiver apsimlockReceiver; 
	private Context mContext;
	private String[] getSIMRetryCounts = { "AT+ESMLCK", "+ESMLCK" };
	private String[] getSMLInfo = {"AT+QSMLE=1","+QSMLE"};
	private	String[] getSIM1Status = { "AT+QSIM1=1,", "+QSIM1" };
	private	String[] getSIM2Status = { "AT+QSIM2=1,", "+QSIM2" };
	//private String[] getSMLEK = {"AT+QSMLEK=1","+QSMLEK"};
	private List<SubscriptionInfo> mSubInfoList;
	private int mSubCount;
	private boolean isSIMOneShow=false;
	private boolean isSIMTwoShow = false;
	private IccNetworkDepersonalizationPanel ndpPanel,ndpPanel2; //add by yulifeng for HQ01987750,20160817
	//add by zhangjinqiang end

    /**
     * Phone app-wide debug level:
     *   0 - no debug logging
     *   1 - normal debug logging if ro.debuggable is set (which is true in
     *       "eng" and "userdebug" builds but not "user" builds)
     *   2 - ultra-verbose debug logging
     *
     * Most individual classes in the phone app have a local DBG constant,
     * typically set to
     *   (PhoneApp.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1)
     * or else
     *   (PhoneApp.DBG_LEVEL >= 2)
     * depending on the desired verbosity.
     *
     * ***** DO NOT SUBMIT WITH DBG_LEVEL > 0 *************
     */
    public static final int DBG_LEVEL = 3;

    private static final boolean DBG =
            (PhoneGlobals.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1);
    private static final boolean VDBG = (PhoneGlobals.DBG_LEVEL >= 2);

    // Message codes; see mHandler below.
    private static final int EVENT_SIM_NETWORK_LOCKED = 3;
    private static final int EVENT_SIM_STATE_CHANGED = 8;
    private static final int EVENT_DATA_ROAMING_DISCONNECTED = 10;
    private static final int EVENT_DATA_ROAMING_OK = 11;
    private static final int EVENT_UNSOL_CDMA_INFO_RECORD = 12;
    private static final int EVENT_DOCK_STATE_CHANGED = 13;
    private static final int EVENT_START_SIP_SERVICE = 14;

    // The MMI codes are also used by the InCallScreen.
    public static final int MMI_INITIATE = 51;
    public static final int MMI_COMPLETE = 52;
    public static final int MMI_CANCEL = 53;
    // Don't use message codes larger than 99 here; those are reserved for
    // the individual Activities of the Phone UI.

    private Phone[] mPhones = null;

    private static final String ACTION_SHUTDOWN_IPO = "android.intent.action.ACTION_SHUTDOWN_IPO";
    private static final String ACTION_PREBOOT_IPO = "android.intent.action.ACTION_PREBOOT_IPO";

    /**
     * Allowable values for the wake lock code.
     *   SLEEP means the device can be put to sleep.
     *   PARTIAL means wake the processor, but we display can be kept off.
     *   FULL means wake both the processor and the display.
     */
    public enum WakeState {
        SLEEP,
        PARTIAL,
        FULL
    }

    /**
     * Intent Action used for hanging up the current call from Notification bar. This will
     * choose first ringing call, first active call, or first background call (typically in
     * HOLDING state).
     */
    public static final String ACTION_HANG_UP_ONGOING_CALL =
            "com.android.phone.ACTION_HANG_UP_ONGOING_CALL";

    private static PhoneGlobals sMe;

    // A few important fields we expose to the rest of the package
    // directly (rather than thru set/get methods) for efficiency.
    CallController callController;
    CallManager mCM;
    CallNotifier notifier;
    CallerInfoCache callerInfoCache;
    NotificationMgr notificationMgr;
    PhoneInterfaceManager phoneMgr;
    PhoneInterfaceManagerEx phoneMgrEx;

    private BluetoothManager bluetoothManager;
    private CallGatewayManager callGatewayManager;
    private CallStateMonitor callStateMonitor;

    static int mDockState = Intent.EXTRA_DOCK_STATE_UNDOCKED;
    static boolean sVoiceCapable = true;

    // Internal PhoneApp Call state tracker
    CdmaPhoneCallState cdmaPhoneCallState;

    // The currently-active PUK entry activity and progress dialog.
    // Normally, these are the Emergency Dialer and the subsequent
    // progress dialog.  null if there is are no such objects in
    // the foreground.
    private Activity mPUKEntryActivity;
    private ProgressDialog mPUKEntryProgressDialog;

    private boolean mIsSimPinEnabled;
    private String mCachedSimPin;

    // True if we are beginning a call, but the phone state has not changed yet
    private boolean mBeginningCall;
    private boolean mDataDisconnectedDueToRoaming = false;

    // Last phone state seen by updatePhoneState()
    private PhoneConstants.State mLastPhoneState = PhoneConstants.State.IDLE;

    private WakeState mWakeState = WakeState.SLEEP;

    private PowerManager mPowerManager;
    private IPowerManager mPowerManagerService;
    private PowerManager.WakeLock mWakeLock;
    private PowerManager.WakeLock mPartialWakeLock;
    private KeyguardManager mKeyguardManager;

    private UpdateLock mUpdateLock;

    // Broadcast receiver for various intent broadcasts (see onCreate())
    private final BroadcastReceiver mReceiver = new PhoneAppBroadcastReceiver();

    /** boolean indicating restoring mute state on InCallScreen.onResume() */
    private boolean mShouldRestoreMuteOnInCallResume;

    /**
     * The singleton OtaUtils instance used for OTASP calls.
     *
     * The OtaUtils instance is created lazily the first time we need to
     * make an OTASP call, regardless of whether it's an interactive or
     * non-interactive OTASP call.
     */
    public OtaUtils otaUtils;

    // Following are the CDMA OTA information Objects used during OTA Call.
    // cdmaOtaProvisionData object store static OTA information that needs
    // to be maintained even during Slider open/close scenarios.
    // cdmaOtaConfigData object stores configuration info to control visiblity
    // of each OTA Screens.
    // cdmaOtaScreenState object store OTA Screen State information.
    public OtaUtils.CdmaOtaProvisionData cdmaOtaProvisionData;
    public OtaUtils.CdmaOtaConfigData cdmaOtaConfigData;
    public OtaUtils.CdmaOtaScreenState cdmaOtaScreenState;
    public OtaUtils.CdmaOtaInCallScreenUiState cdmaOtaInCallScreenUiState;

    /**
     * Set the restore mute state flag. Used when we are setting the mute state
     * OUTSIDE of user interaction {@link PhoneUtils#startNewCall(Phone)}
     */
    /*package*/void setRestoreMuteOnInCallResume (boolean mode) {
        mShouldRestoreMuteOnInCallResume = mode;
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            PhoneConstants.State phoneState;
            switch (msg.what) {
                // Starts the SIP service. It's a no-op if SIP API is not supported
                // on the deivce.
                // TODO: Having the phone process host the SIP service is only
                // temporary. Will move it to a persistent communication process
                // later.
                case EVENT_START_SIP_SERVICE:
                    SipService.start(getApplicationContext());
                    break;

                // TODO: This event should be handled by the lock screen, just
                // like the "SIM missing" and "Sim locked" cases (bug 1804111).
                case EVENT_SIM_NETWORK_LOCKED:
					android.util.Log.d(TAG_X,"handle EVENT_SIM_NETWORK_LOCKED");
					 int phoneid = ((PhoneProxy)(((AsyncResult)msg.obj).userObj)).getPhoneId();
					 android.util.Log.d("zhangjinqiang","phoneid1:"+phoneid);
					 //HQ_yulifeng delete for simlock,modify to onCreat(),20160905
					 //getSIMStatus(phoneid);
                    if (getResources().getBoolean(R.bool.ignore_sim_network_locked_events)) {
                        // Some products don't have the concept of a "SIM network lock"
                        Log.i(LOG_TAG, "Ignoring EVENT_SIM_NETWORK_LOCKED event; "
                              + "not showing 'SIM network unlock' PIN entry screen");
                    } else {
                        // Normal case: show the "SIM network unlock" PIN entry screen.
                        // The user won't be able to do anything else until
                        // they enter a valid SIM network PIN.
                        //add by zhangjinqiang for HW_SIMLock-start
					  int smlNeedPop = Settings.System.getInt(getContentResolver(), "sml_need_pop", 1);
					  if(smlNeedPop==1){
					  		initIccPanel(phoneid);
					  }
					   //add by zhangjinqiang end
                    }
                    break;

                case EVENT_DATA_ROAMING_DISCONNECTED:
                    notificationMgr.showDataDisconnectedRoaming();
                    break;

                case EVENT_DATA_ROAMING_OK:
                    notificationMgr.hideDataDisconnectedRoaming();
                    break;

                case MMI_COMPLETE:
                    onMMIComplete((AsyncResult) msg.obj);
                    break;

                case MMI_CANCEL:
                    Log.d(LOG_TAG, "handle MMI_CANCEL ...");
                    PhoneUtils.cancelMmiCode(mCM.getFgPhone());
                    PhoneUtils.dismissUssdDialog();
                    break;

                case EVENT_SIM_STATE_CHANGED:
                    // Marks the event where the SIM goes into ready state.
                    // Right now, this is only used for the PUK-unlocking
                    // process.
                    if (msg.obj.equals(IccCardConstants.INTENT_VALUE_ICC_READY)) {
                        // when the right event is triggered and there
                        // are UI objects in the foreground, we close
                        // them to display the lock panel.
                        if (mPUKEntryActivity != null) {
                            mPUKEntryActivity.finish();
                            mPUKEntryActivity = null;
                        }
                        if (mPUKEntryProgressDialog != null) {
                            mPUKEntryProgressDialog.dismiss();
                            mPUKEntryProgressDialog = null;
                        }
                    }
                    break;

                case EVENT_UNSOL_CDMA_INFO_RECORD:
                    //TODO: handle message here;
                    break;

                case EVENT_DOCK_STATE_CHANGED:
                    // If the phone is docked/undocked during a call, and no wired or BT headset
                    // is connected: turn on/off the speaker accordingly.
                    boolean inDockMode = false;
                    if (mDockState != Intent.EXTRA_DOCK_STATE_UNDOCKED) {
                        inDockMode = true;
                    }
                    if (VDBG) Log.d(LOG_TAG, "received EVENT_DOCK_STATE_CHANGED. Phone inDock = "
                            + inDockMode);

                    phoneState = mCM.getState();
                    if (phoneState == PhoneConstants.State.OFFHOOK &&
                            !bluetoothManager.isBluetoothHeadsetAudioOn()) {
                        PhoneUtils.turnOnSpeaker(getApplicationContext(), inDockMode, true);
                    }
                    break;
            }
        }
    };

    public PhoneGlobals(Context context) {
        super(context);
        sMe = this;
    }

    public void onCreate() {
        if (VDBG) Log.v(LOG_TAG, "onCreate()...");

        ContentResolver resolver = getContentResolver();

        // Cache the "voice capable" flag.
        // This flag currently comes from a resource (which is
        // overrideable on a per-product basis):
        sVoiceCapable =
                getResources().getBoolean(com.android.internal.R.bool.config_voice_capable);
        // ...but this might eventually become a PackageManager "system
        // feature" instead, in which case we'd do something like:
        // sVoiceCapable =
        //   getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY_VOICE_CALLS);

        if (mCM == null) {
            // Initialize the telephony framework
            PhoneFactory.makeDefaultPhones(this);

            // Start TelephonyDebugService After the default phone is created.
            Intent intent = new Intent(this, TelephonyDebugService.class);
            startService(intent);

            mCM = CallManager.getInstance();
            boolean hasCdmaPhoneType = false;
            for (Phone phone : PhoneFactory.getPhones()) {
                mCM.registerPhone(phone);
                if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                    hasCdmaPhoneType = true;
                }
            }

            // Create the NotificationMgr singleton, which is used to display
            // status bar icons and control other status bar behavior.
            notificationMgr = NotificationMgr.init(this);

            mHandler.sendEmptyMessage(EVENT_START_SIP_SERVICE);

            if (hasCdmaPhoneType) {
                // Create an instance of CdmaPhoneCallState and initialize it to IDLE
                cdmaPhoneCallState = new CdmaPhoneCallState();
                cdmaPhoneCallState.CdmaPhoneCallStateInit();
            }

            // before registering for phone state changes
            mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = mPowerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, LOG_TAG);
            // lock used to keep the processor awake, when we don't care for the display.
            mPartialWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK
                    | PowerManager.ON_AFTER_RELEASE, LOG_TAG);

            mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

            // get a handle to the service so that we can use it later when we
            // want to set the poke lock.
            mPowerManagerService = IPowerManager.Stub.asInterface(
                    ServiceManager.getService("power"));

            // Get UpdateLock to suppress system-update related events (e.g. dialog show-up)
            // during phone calls.
            mUpdateLock = new UpdateLock("phone");

            if (DBG) Log.d(LOG_TAG, "onCreate: mUpdateLock: " + mUpdateLock);

            CallLogger callLogger = new CallLogger(this, new CallLogAsync());

            callGatewayManager = CallGatewayManager.getInstance();

            // Create the CallController singleton, which is the interface
            // to the telephony layer for user-initiated telephony functionality
            // (like making outgoing calls.)
            callController = CallController.init(this, callLogger, callGatewayManager);

            // Create the CallerInfoCache singleton, which remembers custom ring tone and
            // send-to-voicemail settings.
            //
            // The asynchronous caching will start just after this call.
            callerInfoCache = CallerInfoCache.init(this);

            // Monitors call activity from the telephony layer
            callStateMonitor = new CallStateMonitor(mCM);

            // Bluetooth manager
            bluetoothManager = new BluetoothManager();

            phoneMgr = PhoneInterfaceManager.init(this, PhoneFactory.getDefaultPhone());
            phoneMgrEx = PhoneInterfaceManagerEx.init(this, PhoneFactory.getDefaultPhone());

            // Create the CallNotifer singleton, which handles
            // asynchronous events from the telephony layer (like
            // launching the incoming-call UI when an incoming call comes
            // in.)
            notifier = CallNotifier.init(this, PhoneFactory.getDefaultPhone(), callLogger, callStateMonitor,
                    bluetoothManager);

			//add by zhangjinqiang for ATCommand before EVENT_SIM_NETWORK_LOCKED  start
			setSimLockAction(getSMLInfo, EVENT_READ_SML_INFO);

			/*HQ_yulifeng for simlock ,query simstate,20160906,start*/
			boolean gemini = "1".equals(SystemProperties.get("ro.mtk_gemini_support"));
			if(gemini){
			    getSIMStatus(0);
			    getSIMStatus(1);
			}else{
			    getSIMStatus(0);
			}
			/*HQ_yulifeng for simlock ,query simstate,20160906,end*/

			//setSimLockAction(getSMLEK, EVENT_READ_SML_SHA);
			//add by zhangjinqiang end

            // register for ICC status
            PhoneUtils.registerIccStatus(mHandler, EVENT_SIM_NETWORK_LOCKED);

            // register for MMI/USSD
            mCM.registerForMmiComplete(mHandler, MMI_COMPLETE, null);

            // register connection tracking to PhoneUtils
            PhoneUtils.initializeConnectionHandler(mCM);

            // Register for misc other intent broadcasts.
            IntentFilter intentFilter =
                    new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
            intentFilter.addAction(Intent.ACTION_DOCK_EVENT);
            intentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
            intentFilter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
            ///M: add for update SPN(Service provider name) @{
            intentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            /// @}

            intentFilter.addAction(Intent.ACTION_MSIM_MODE_CHANGED);
            intentFilter.addAction(ACTION_SHUTDOWN_IPO);
            intentFilter.addAction(ACTION_PREBOOT_IPO);

            registerReceiver(mReceiver, intentFilter);

            // register for subscriptions change
            SubscriptionManager.from(this)
                    .addOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
            mSubscriptionInfos = PhoneUtils.getActiveSubInfoList();

            // To prevent PhoneGlobals init too long, lose the first AIRPLANE_MODE_CHANGED intent
            boolean isAirplaneMode = System.getInt(getContentResolver(), System.AIRPLANE_MODE_ON, 0) != 0;
            if (DBG) {
                Log.d(LOG_TAG, "Notify RadioManager with airplane mode:" + isAirplaneMode);
            }
            RadioManager.getInstance().notifyAirplaneModeChange(isAirplaneMode);

            //set the default values for the preferences in the phone.
            PreferenceManager.setDefaultValues(this, R.xml.network_setting, false);

            PreferenceManager.setDefaultValues(this, R.xml.call_feature_setting, false);

            // Make sure the audio mode (along with some
            // audio-mode-related state of our own) is initialized
            // correctly, given the current state of the phone.
            PhoneUtils.setAudioMode(mCM);
        }

        cdmaOtaProvisionData = new OtaUtils.CdmaOtaProvisionData();
        cdmaOtaConfigData = new OtaUtils.CdmaOtaConfigData();
        cdmaOtaScreenState = new OtaUtils.CdmaOtaScreenState();
        cdmaOtaInCallScreenUiState = new OtaUtils.CdmaOtaInCallScreenUiState();

        // XXX pre-load the SimProvider so that it's ready
        resolver.getType(Uri.parse(ADNList.ICC_ADN_URI));

        // start with the default value to set the mute state.
        mShouldRestoreMuteOnInCallResume = false;

        // TODO: Register for Cdma Information Records
        // phone.registerCdmaInformationRecord(mHandler, EVENT_UNSOL_CDMA_INFO_RECORD, null);

        // Read HAC settings and configure audio hardware
        if (TelephonyUtils.isHacSupport()) {
            int hac = android.provider.Settings.System.getInt(
                    getPhone().getContext().getContentResolver(),
                    android.provider.Settings.System.HEARING_AID, 0);

            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioManager.setParameter(CallFeaturesSetting.HAC_KEY, hac != 0 ?
                                      CallFeaturesSetting.HAC_VAL_ON :
                                      CallFeaturesSetting.HAC_VAL_OFF);
        }

		//add by zhangjinqiang for SIMLock
		apsimlockReceiver = new HQSIMLockReceiver();
		IntentFilter filterSimlock = new IntentFilter();
		android.util.Log.i(TAG_X, "registerReceiver  unlock sml  in PhoneGlobals.java");
		filterSimlock.addAction("android.intent.action.simlock.unlock");
		filterSimlock.addAction("android.intent.action.simlock.lock");
		filterSimlock.addAction("android.intent.action.simlock.getpassword");
		filterSimlock.addAction("com.android.huawei.simlock.unlock");
		filterSimlock.addAction("android.intent.action.noSimCard_unLock");
		filterSimlock.addAction("com.android.huawei.sim.iccpanel.show");
		filterSimlock.addAction("com.android.huawei.simlock.getSIM1Status");
		filterSimlock.addAction("com.android.huawei.simlock.getSIM2Status");
		filterSimlock.addAction("android.intent.action.LOCALE_CHANGED"); //add by yulifeng for HQ01987750,20160817
		registerReceiver(apsimlockReceiver, filterSimlock);	
		if(SystemProperties.get("ro.hq.telcel.simlock").equals("1")){
			setSimLockAction(getSIM1Status,EVENT_READ_SIM1_INFO);
		}
		//add by zjq end
    }

    /**
     * Returns the singleton instance of the PhoneApp.
     */
    public static PhoneGlobals getInstance() {
        if (sMe == null) {
            throw new IllegalStateException("No PhoneGlobals here!");
        }
        return sMe;
    }

    /**
     * Returns the singleton instance of the PhoneApp if running as the
     * primary user, otherwise null.
     */
    static PhoneGlobals getInstanceIfPrimary() {
        return sMe;
    }

    /**
     * Returns the default phone.
     *
     * WARNING: This method should be used carefully, now that there may be multiple phones.
     */
    public static Phone getPhone() {
        return PhoneFactory.getDefaultPhone();
    }

    public static Phone getPhone(int subId) {
        return PhoneFactory.getPhone(SubscriptionManager.getPhoneId(subId));
    }

    /* package */ BluetoothManager getBluetoothManager() {
        return bluetoothManager;
    }

    /* package */ CallManager getCallManager() {
        return mCM;
    }

    /**
     * Returns PendingIntent for hanging up ongoing phone call. This will typically be used from
     * Notification context.
     */
    /* package */ static PendingIntent createHangUpOngoingCallPendingIntent(Context context) {
        Intent intent = new Intent(PhoneGlobals.ACTION_HANG_UP_ONGOING_CALL, null,
                context, NotificationBroadcastReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    boolean isSimPinEnabled() {
        return mIsSimPinEnabled;
    }

    boolean authenticateAgainstCachedSimPin(String pin) {
        return (mCachedSimPin != null && mCachedSimPin.equals(pin));
    }

    void setCachedSimPin(String pin) {
        mCachedSimPin = pin;
    }

    /**
     * Handles OTASP-related events from the telephony layer.
     *
     * While an OTASP call is active, the CallNotifier forwards
     * OTASP-related telephony events to this method.
     */
    void handleOtaspEvent(Message msg) {
        if (DBG) Log.d(LOG_TAG, "handleOtaspEvent(message " + msg + ")...");

        if (otaUtils == null) {
            // We shouldn't be getting OTASP events without ever
            // having started the OTASP call in the first place!
            Log.w(LOG_TAG, "handleOtaEvents: got an event but otaUtils is null! "
                  + "message = " + msg);
            return;
        }

        otaUtils.onOtaProvisionStatusChanged((AsyncResult) msg.obj);
    }

    /**
     * Similarly, handle the disconnect event of an OTASP call
     * by forwarding it to the OtaUtils instance.
     */
    /* package */ void handleOtaspDisconnect() {
        if (DBG) Log.d(LOG_TAG, "handleOtaspDisconnect()...");

        if (otaUtils == null) {
            // We shouldn't be getting OTASP events without ever
            // having started the OTASP call in the first place!
            Log.w(LOG_TAG, "handleOtaspDisconnect: otaUtils is null!");
            return;
        }

        otaUtils.onOtaspDisconnect();
    }

    /**
     * Sets the activity responsible for un-PUK-blocking the device
     * so that we may close it when we receive a positive result.
     * mPUKEntryActivity is also used to indicate to the device that
     * we are trying to un-PUK-lock the phone. In other words, iff
     * it is NOT null, then we are trying to unlock and waiting for
     * the SIM to move to READY state.
     *
     * @param activity is the activity to close when PUK has
     * finished unlocking. Can be set to null to indicate the unlock
     * or SIM READYing process is over.
     */
    void setPukEntryActivity(Activity activity) {
        mPUKEntryActivity = activity;
    }

    Activity getPUKEntryActivity() {
        return mPUKEntryActivity;
    }

    /**
     * Sets the dialog responsible for notifying the user of un-PUK-
     * blocking - SIM READYing progress, so that we may dismiss it
     * when we receive a positive result.
     *
     * @param dialog indicates the progress dialog informing the user
     * of the state of the device.  Dismissed upon completion of
     * READYing process
     */
    void setPukEntryProgressDialog(ProgressDialog dialog) {
        mPUKEntryProgressDialog = dialog;
    }

    ProgressDialog getPUKEntryProgressDialog() {
        return mPUKEntryProgressDialog;
    }

    /**
     * Controls whether or not the screen is allowed to sleep.
     *
     * Once sleep is allowed (WakeState is SLEEP), it will rely on the
     * settings for the poke lock to determine when to timeout and let
     * the device sleep {@link PhoneGlobals#setScreenTimeout}.
     *
     * @param ws tells the device to how to wake.
     */
    /* package */ void requestWakeState(WakeState ws) {
        if (VDBG) Log.d(LOG_TAG, "requestWakeState(" + ws + ")...");
        synchronized (this) {
            if (mWakeState != ws) {
                switch (ws) {
                    case PARTIAL:
                        // acquire the processor wake lock, and release the FULL
                        // lock if it is being held.
                        mPartialWakeLock.acquire();
                        if (mWakeLock.isHeld()) {
                            mWakeLock.release();
                        }
                        break;
                    case FULL:
                        // acquire the full wake lock, and release the PARTIAL
                        // lock if it is being held.
                        mWakeLock.acquire();
                        if (mPartialWakeLock.isHeld()) {
                            mPartialWakeLock.release();
                        }
                        break;
                    case SLEEP:
                    default:
                        // release both the PARTIAL and FULL locks.
                        if (mWakeLock.isHeld()) {
                            mWakeLock.release();
                        }
                        if (mPartialWakeLock.isHeld()) {
                            mPartialWakeLock.release();
                        }
                        break;
                }
                mWakeState = ws;
            }
        }
    }

    /**
     * If we are not currently keeping the screen on, then poke the power
     * manager to wake up the screen for the user activity timeout duration.
     */
    /* package */ void wakeUpScreen() {
        synchronized (this) {
            if (mWakeState == WakeState.SLEEP) {
                if (DBG) Log.d(LOG_TAG, "pulse screen lock");
                mPowerManager.wakeUp(SystemClock.uptimeMillis());
            }
        }
    }

    /**
     * Sets the wake state and screen timeout based on the current state
     * of the phone, and the current state of the in-call UI.
     *
     * This method is a "UI Policy" wrapper around
     * {@link PhoneGlobals#requestWakeState} and {@link PhoneGlobals#setScreenTimeout}.
     *
     * It's safe to call this method regardless of the state of the Phone
     * (e.g. whether or not it's idle), and regardless of the state of the
     * Phone UI (e.g. whether or not the InCallScreen is active.)
     */
    /* package */ void updateWakeState() {
        PhoneConstants.State state = mCM.getState();

        // True if the speakerphone is in use.  (If so, we *always* use
        // the default timeout.  Since the user is obviously not holding
        // the phone up to his/her face, we don't need to worry about
        // false touches, and thus don't need to turn the screen off so
        // aggressively.)
        // Note that we need to make a fresh call to this method any
        // time the speaker state changes.  (That happens in
        // PhoneUtils.turnOnSpeaker().)
        boolean isSpeakerInUse = (state == PhoneConstants.State.OFFHOOK) && PhoneUtils.isSpeakerOn(this);

        // TODO (bug 1440854): The screen timeout *might* also need to
        // depend on the bluetooth state, but this isn't as clear-cut as
        // the speaker state (since while using BT it's common for the
        // user to put the phone straight into a pocket, in which case the
        // timeout should probably still be short.)

        // Decide whether to force the screen on or not.
        //
        // Force the screen to be on if the phone is ringing or dialing,
        // or if we're displaying the "Call ended" UI for a connection in
        // the "disconnected" state.
        // However, if the phone is disconnected while the user is in the
        // middle of selecting a quick response message, we should not force
        // the screen to be on.
        //
        boolean isRinging = (state == PhoneConstants.State.RINGING);
        boolean isDialing = (mCM.getFgPhone().getForegroundCall().getState() == Call.State.DIALING);
        boolean keepScreenOn = isRinging || isDialing;
        // keepScreenOn == true means we'll hold a full wake lock:
        requestWakeState(keepScreenOn ? WakeState.FULL : WakeState.SLEEP);
    }

    /**
     * Manually pokes the PowerManager's userActivity method.  Since we
     * set the {@link WindowManager.LayoutParams#INPUT_FEATURE_DISABLE_USER_ACTIVITY}
     * flag while the InCallScreen is active when there is no proximity sensor,
     * we need to do this for touch events that really do count as user activity
     * (like pressing any onscreen UI elements.)
     */
    /* package */ void pokeUserActivity() {
        if (VDBG) Log.d(LOG_TAG, "pokeUserActivity()...");
        mPowerManager.userActivity(SystemClock.uptimeMillis(), false);
    }

    /**
     * Notifies the phone app when the phone state changes.
     *
     * This method will updates various states inside Phone app (e.g. update-lock state, etc.)
     */
    /* package */ void updatePhoneState(PhoneConstants.State state) {
        if (state != mLastPhoneState) {
            mLastPhoneState = state;

            // Try to acquire or release UpdateLock.
            //
            // Watch out: we don't release the lock here when the screen is still in foreground.
            // At that time InCallScreen will release it on onPause().
            if (state != PhoneConstants.State.IDLE) {
                // UpdateLock is a recursive lock, while we may get "acquire" request twice and
                // "release" request once for a single call (RINGING + OFFHOOK and IDLE).
                // We need to manually ensure the lock is just acquired once for each (and this
                // will prevent other possible buggy situations too).
                if (!mUpdateLock.isHeld()) {
                    mUpdateLock.acquire();
                }
            } else {
                if (mUpdateLock.isHeld()) {
                    mUpdateLock.release();
                }
            }
        }
    }

    /* package */ PhoneConstants.State getPhoneState() {
        return mLastPhoneState;
    }

    KeyguardManager getKeyguardManager() {
        return mKeyguardManager;
    }

    private void onMMIComplete(AsyncResult r) {
        if (VDBG) Log.d(LOG_TAG, "onMMIComplete()...");
        MmiCode mmiCode = (MmiCode) r.result;
        
        final Message message = Message.obtain(mHandler, MMI_CANCEL);
        
        PhoneUtils.displayMMIComplete(mmiCode.getPhone(), getInstance(), mmiCode, message, null);
    }

    private void initForNewRadioTechnology(int phoneId) {
        if (DBG) Log.d(LOG_TAG, "initForNewRadioTechnology...");

        final Phone phone = PhoneFactory.getPhone(phoneId);

        if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            // Create an instance of CdmaPhoneCallState and initialize it to IDLE
            cdmaPhoneCallState = new CdmaPhoneCallState();
            cdmaPhoneCallState.CdmaPhoneCallStateInit();
        }
        if (!TelephonyCapabilities.supportsOtasp(phone)) {
            //Clean up OTA data in GSM/UMTS. It is valid only for CDMA
            clearOtaState();
        }

        notifier.updateCallNotifierRegistrationsAfterRadioTechnologyChange();
        callStateMonitor.updateAfterRadioTechnologyChange();

        // Update registration for ICC status after radio technology change
        IccCard sim = phone.getIccCard();
        if (sim != null) {
            if (DBG) Log.d(LOG_TAG, "Update registration for ICC status...");

            //Register all events new to the new active phone
            sim.registerForNetworkLocked(mHandler, EVENT_SIM_NETWORK_LOCKED, phone);
        }
    }

    /**
     * Receiver for misc intent broadcasts the Phone app cares about.
     */
    private class PhoneAppBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            /// M: @{
            if (ExtensionManager.getPhoneMiscExt().onPhoneGlobalsBroadcastReceive(context, intent)) {
                return;
            }
            /// @}
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                /// M: Modify Google code. @{
                /*
                boolean enabled = System.getInt(getContentResolver(),
                        System.AIRPLANE_MODE_ON, 0) == 0;
                */
                boolean enabled = intent.getBooleanExtra("state", false);
                //if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                boolean forceChanged = intent.getBooleanExtra("forceChanged", false);
                RadioManager.getInstance().forceAllowAirplaneModeChange(forceChanged);
                Log.d(LOG_TAG, "airplane change, forceChanged = " + forceChanged);
                //}
                Log.d(LOG_TAG, "mReceiver: ACTION_AIRPLANE_MODE_CHANGED, enabled = " + enabled);
                /// @}
                RadioManager.getInstance().notifyAirplaneModeChange(enabled);

                if (RadioManager.getInstance().isPowerOnFeatureAllClosed()) {
                    PhoneUtils.setRadioPower(!enabled);
                }
            } else if (action.equals(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED)) {
                int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                int phoneId = SubscriptionManager.getPhoneId(subId);
                String state = intent.getStringExtra(PhoneConstants.STATE_KEY);
                if (VDBG) {
                    Log.d(LOG_TAG, "mReceiver: ACTION_ANY_DATA_CONNECTION_STATE_CHANGED");
                    Log.d(LOG_TAG, "- state: " + state);
                    Log.d(LOG_TAG, "- reason: "
                    + intent.getStringExtra(PhoneConstants.STATE_CHANGE_REASON_KEY));
                    Log.d(LOG_TAG, "- subId: " + subId);
                    Log.d(LOG_TAG, "- phoneId: " + phoneId);
                }
                Phone phone = SubscriptionManager.isValidPhoneId(phoneId) ?
                        PhoneFactory.getPhone(phoneId) : PhoneFactory.getDefaultPhone();
                // The "data disconnected due to roaming" notification is shown
                // if (a) you have the "data roaming" feature turned off, and
                // (b) you just lost data connectivity because you're roaming.
                boolean disconnectedDueToRoaming =
                        !phone.getDataRoamingEnabled()
                        && PhoneConstants.DataState.DISCONNECTED.equals(state)
                        && Phone.REASON_ROAMING_ON.equals(
                            intent.getStringExtra(PhoneConstants.STATE_CHANGE_REASON_KEY));
                if (mDataDisconnectedDueToRoaming != disconnectedDueToRoaming) {
                    mDataDisconnectedDueToRoaming = disconnectedDueToRoaming;
                    mHandler.sendEmptyMessage(disconnectedDueToRoaming
                            ? EVENT_DATA_ROAMING_DISCONNECTED : EVENT_DATA_ROAMING_OK);
                }
            } else if ((action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) &&
                    (mPUKEntryActivity != null)) {
                // if an attempt to un-PUK-lock the device was made, while we're
                // receiving this state change notification, notify the handler.
                // NOTE: This is ONLY triggered if an attempt to un-PUK-lock has
                // been attempted.
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_SIM_STATE_CHANGED,
                        intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE)));
            } else if (action.equals(TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED)) {
                String newPhone = intent.getStringExtra(PhoneConstants.PHONE_NAME_KEY);
                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                        SubscriptionManager.INVALID_PHONE_INDEX);
                Log.d(LOG_TAG, "Radio technology switched. Now " + newPhone + " (" + phoneId
                        + ") is active.");
                initForNewRadioTechnology(phoneId);
            } else if (action.equals(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED)) {
                handleServiceStateChanged(intent);
            } else if (action.equals(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED)) {
                /// M: For G+C project, only check the foreground phone not enough @{
                //if (TelephonyCapabilities.supportsEcm(mCM.getFgPhone())) {
                    Log.d(LOG_TAG, "Emergency Callback Mode arrived in PhoneApp.");
                    // Start Emergency Callback Mode service
                    if (intent.getBooleanExtra("phoneinECMState", false)) {
                        context.startService(new Intent(context,
                                EmergencyCallbackModeService.class));
                    }
                //} else {
                    // It doesn't make sense to get ACTION_EMERGENCY_CALLBACK_MODE_CHANGED
                    // on a device that doesn't support ECM in the first place.
                    //Log.e(LOG_TAG, "Got ACTION_EMERGENCY_CALLBACK_MODE_CHANGED, "
                            //+ "but ECM isn't supported for phone: "
                            //+ mCM.getFgPhone().getPhoneName());
                //}
                /// @}
            } else if (action.equals(Intent.ACTION_DOCK_EVENT)) {
                mDockState = intent.getIntExtra(Intent.EXTRA_DOCK_STATE,
                        Intent.EXTRA_DOCK_STATE_UNDOCKED);
                if (VDBG) Log.d(LOG_TAG, "ACTION_DOCK_EVENT -> mDockState = " + mDockState);
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_DOCK_STATE_CHANGED, 0));
                /// M: add for update SPN(Service provider name) @{
            } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                for (Phone item : PhoneFactory.getPhones()) {
                    if (item != null) {
                        Log.d(LOG_TAG, "phone = " + item);
                        //[ALPS02051538]-statr
                        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                                && item instanceof SvltePhoneProxy) {
                            Log.d(LOG_TAG, "C2K refreshSpnDisplay");
                            item.refreshSpnDisplay();
                            ((SvltePhoneProxy)item).getLtePhone().refreshSpnDisplay();
                        } else {
                            item.refreshSpnDisplay();
                        }
                        //[ALPS02051538]-end
                    }
                }
            }
            /// @}
            else if (action.equals(Intent.ACTION_MSIM_MODE_CHANGED)) {
                int mode = intent.getIntExtra(Intent.EXTRA_MSIM_MODE, -1);
                RadioManager.getInstance().notifyMSimModeChange(mode);
            } else if (action.equals(ACTION_SHUTDOWN_IPO)) {
                Log.d(LOG_TAG, "notify RadioManager of IPO shutdown");
                RadioManager.getInstance().notifyIpoShutDown();
            } else if (action.equals(ACTION_PREBOOT_IPO)) {
                RadioManager.getInstance().notifyIpoPreBoot();
            }
        }
    }

    /**
     * Accepts broadcast Intents which will be prepared by {@link NotificationMgr} and thus
     * sent from framework's notification mechanism (which is outside Phone context).
     * This should be visible from outside, but shouldn't be in "exported" state.
     *
     * TODO: If possible merge this into PhoneAppBroadcastReceiver.
     */
    public static class NotificationBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // TODO: use "if (VDBG)" here.
            Log.d(LOG_TAG, "Broadcast from Notification: " + action);

            if (action.equals(ACTION_HANG_UP_ONGOING_CALL)) {
                PhoneUtils.hangup(PhoneGlobals.getInstance().mCM);
            } else {
                Log.w(LOG_TAG, "Received hang-up request from notification,"
                        + " but there's no call the system can hang up.");
            }
        }
    }

    private void handleServiceStateChanged(Intent intent) {
        /**
         * This used to handle updating EriTextWidgetProvider this routine
         * and and listening for ACTION_SERVICE_STATE_CHANGED intents could
         * be removed. But leaving just in case it might be needed in the near
         * future.
         */

        // If service just returned, start sending out the queued messages
        ServiceState ss = ServiceState.newFromBundle(intent.getExtras());

        if (ss != null) {
            int state = ss.getState();
            notificationMgr.updateNetworkSelection(state);
        }
    }

    public boolean isOtaCallInActiveState() {
        boolean otaCallActive = false;
        if (VDBG) Log.d(LOG_TAG, "- isOtaCallInActiveState " + otaCallActive);
        return otaCallActive;
    }

    public boolean isOtaCallInEndState() {
        boolean otaCallEnded = false;
        if (VDBG) Log.d(LOG_TAG, "- isOtaCallInEndState " + otaCallEnded);
        return otaCallEnded;
    }

    // it is safe to call clearOtaState() even if the InCallScreen isn't active
    public void clearOtaState() {
        if (DBG) Log.d(LOG_TAG, "- clearOtaState ...");
        if (otaUtils != null) {
            otaUtils.cleanOtaScreen(true);
            if (DBG) Log.d(LOG_TAG, "  - clearOtaState clears OTA screen");
        }
    }

    // it is safe to call dismissOtaDialogs() even if the InCallScreen isn't active
    public void dismissOtaDialogs() {
        if (DBG) Log.d(LOG_TAG, "- dismissOtaDialogs ...");
        if (otaUtils != null) {
            otaUtils.dismissAllOtaDialogs();
            if (DBG) Log.d(LOG_TAG, "  - dismissOtaDialogs clears OTA dialogs");
        }
    }

    /**
     * Triggers a refresh of the message waiting (voicemail) indicator.
     *
     * @param subId the subscription id we should refresh the notification for.
     */
    public void refreshMwiIndicator(int subId) {
        notificationMgr.refreshMwi(subId);
    }

    /**
     * "Call origin" may be used by Contacts app to specify where the phone call comes from.
     * Currently, the only permitted value for this extra is {@link #ALLOWED_EXTRA_CALL_ORIGIN}.
     * Any other value will be ignored, to make sure that malicious apps can't trick the in-call
     * UI into launching some random other app after a call ends.
     *
     * TODO: make this more generic. Note that we should let the "origin" specify its package
     * while we are now assuming it is "com.android.contacts"
     */
    public static final String EXTRA_CALL_ORIGIN = "com.android.phone.CALL_ORIGIN";
    private static final String DEFAULT_CALL_ORIGIN_PACKAGE = "com.android.dialer";
    private static final String ALLOWED_EXTRA_CALL_ORIGIN =
            "com.android.dialer.DialtactsActivity";
    /**
     * Used to determine if the preserved call origin is fresh enough.
     */
    private static final long CALL_ORIGIN_EXPIRATION_MILLIS = 30 * 1000;
    // Listen sim hot swap related change.
    private final Set<SubInfoUpdateListener> mSubInfoUpdateListeners = Sets.newArraySet();
    private List<SubscriptionInfo> mSubscriptionInfos;

    /***
     * The listener for hot swap event.
     */
    public interface SubInfoUpdateListener {
        public void handleSubInfoUpdate();
    }

    /**
     * Add listener to update screen if need.
     * @param listener to monitor the change
     */
    public void addSubInfoUpdateListener(SubInfoUpdateListener listener) {
        Preconditions.checkNotNull(listener);
        mSubInfoUpdateListeners.add(listener);
    }

    /**
     * Remove listener that used update screen.
     * @param listener to monitor the change
     */
    public void removeSubInfoUpdateListener(SubInfoUpdateListener listener) {
        Preconditions.checkNotNull(listener);
        mSubInfoUpdateListeners.remove(listener);
    }

    private final SubscriptionManager.OnSubscriptionsChangedListener mOnSubscriptionsChangeListener
            = new SubscriptionManager.OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            Log.d(LOG_TAG, "onSubscriptionsChanged start");
            if (TelephonyUtils.isHotSwapHanppened(
                    mSubscriptionInfos, PhoneUtils.getActiveSubInfoList())) {
                mSubscriptionInfos = PhoneUtils.getActiveSubInfoList();
                for (SubInfoUpdateListener listener : mSubInfoUpdateListeners) {
                    listener.handleSubInfoUpdate();
                }
            }
            Log.d(LOG_TAG, "onSubscriptionsChanged end");
        }
    };

    public boolean isAllowAirplaneModeChange() {
        return RadioManager.getInstance().isAllowAirplaneModeChange();
    }

	//add by zhangjinqiang for HW_SIMLOCK-start	
	private String parseData(String[] data) 
	{
		String gid1Flag = null;
		Log.i(TAG_X, "parseData() content[0]: " + data[0]);
		if (data[0] != null && data[0].startsWith("+GID1R")) 
		{
			Log.d(TAG_X, "parseData " + data[0]);
			data[0].trim();
		}
		String[] str =  data[0].split(":");
		gid1Flag = str[1];
		return gid1Flag;
	}

	/*
		formatData like this:
		+ESMLCK: (0,2,255,0,0,50,1),(1,2,255,0,0,30,1),
		(2,2,255,0,0,30,1),(3,2,255,0,0,30,0),(4,2,255,0,0,30,0),
		(5,2,255,0,0,10,1),(6,1,5,0,1,10,1),"460011700510286",0,255,0,255,2
	*/
	private String formatData(String[] data){
		String retryData="0";
		if(data[0]!=null&&data[0].startsWith("+ESMLCK:")){
			data[0].trim();
		}
		String[] str = data[0].split("\\),");

		for(int i=0;i<str.length-1;i++){
			String[] temp = str[i].split(",");
			if(Integer.parseInt(temp[1])==1){
				retryData = temp[2];
			}
		}
		return retryData;
	}

	/*
		formatData like this:+QSIM1:01;01;46003f;21436587(lock)
		+QSIM1:01;02;34505580;05;
		+QSIM1:01;01;467552;09721338;ff;00;
		+QSIM1:01;01;4906008251435832;09721338;ff;04;
		+QSIM1:01;01;46000f;09721338;04;00;
		+QSIM1:00;(unlock)
	*/
	private String[] parseSIMData(String[] data) {
		String[] currentData = null;
		String passWord = null;
		if (data[0] != null && (data[0].startsWith("+QSIM1:")||data[0].startsWith("+QSIM2:"))) {
			data[0].trim();
		}
		String[] str = data[0].split(":");
		String[] temp = str[1].split(";");
		/*01;01;46003f;21436587*/
		if(temp.length >= 4)
		{
			currentData = new String[4];
			int index = Integer.parseInt(temp[1], 16);
			passWord = temp[index + 2];
			currentData[1] = passWord;
			currentData[0] = temp[0];
			currentData[2] = temp[temp.length-1];
			currentData[3] = temp[temp.length-2];
		}else if(temp.length == 1){
			currentData = new String[1];
			currentData[0] = temp[0];
		}
		return currentData;
	}
	
	/*
		formatData like this: +QSMLE:00;00,00;00;00,00,00,00;00
										  +QSMLE:02;01,00;01;00,00,00,00;00
										  +QSMLE:00;01,00;01;05,00,00,00;00;00;de9115e5;
		00:sml_type
		00,00:need_pop
		00:can_cancel_pop     
		00,00,00,00:unlock_time=unlock_time[0] + unlock_time[1] + unlock_time[2] + unlock_time[3]
		00:unlock_pop
	*/
	private int[] parseSIMLockInfoData(String[] data) {
		int[] arrNum = null;
		android.util.Log.d(TAG_X, "parseData() content[0]: " + data[0]);
		if (data[0] != null && data[0].startsWith("+QSMLE:")) {
			data[0].trim();
		}
		String[] str = data[0].split(":");
		String[] temp = str[1].split(";");
		if(temp.length > 1){
			arrNum = new int[temp.length+1];
		     int k=0;
			arrNum = new int[temp.length+1];
			for(int i=0;i<temp.length;i++){
				String[] tempStr = temp[i].split(",");
				if(i==1){
					for(int j=0;j<tempStr.length;j++){
						int num = Integer.parseInt(tempStr[j], 16);
						arrNum[k]=num;
						k++;
					}
				}else if(i==3){
					int sum =0;
					for(int j=0;j<tempStr.length;j++){
						int num = Integer.parseInt(tempStr[j], 16);
						sum += num;
					}
					arrNum[k]=sum;
					k++;
				}else if(i==temp.length-1){
					android.util.Log.d("zhangjinqiang","temp["+i+"]:"+temp[i]);
					Settings.System.putString(getApplicationContext().getContentResolver(), "sml_sha", temp[i]);
				}else{
					int num = Integer.parseInt(temp[i], 16);
					arrNum[k]=num;
					k++;
				}
			}
		}
		return arrNum;
	}

	/*
	private String getSMLEK(String[] data){
		if (data[0] != null && data[0].startsWith("+QSMLEK:")) {
			data[0].trim();
			String[] str = data[0].split(":");
			return str[1];
		}
		return null;
	}
	*/
	
	private Handler mResponseHander = new Handler() {
		@Override
		public void handleMessage(Message msg) 
		{
			AsyncResult ar;
			Log.i(TAG_X,"mResponseHander Handler : "+msg.what);
			switch (msg.what) 
			{
				case EVENT_GET_SML_PASSWORD_FROM_NVRAM:
				{
					android.util.Log.d(TAG_X,"getpassword callback");
					ar = (AsyncResult) msg.obj;
					if(ar.exception == null)
					{
						String[] data = (String[])ar.result;
						android.util.Log.d(TAG_X,"read password successfully  ==>>>" + data.length);
						String pData = null;
						if(data.length > 0)
						{
							pData = parseData(data);
							android.util.Log.d(TAG_X,"pData is   ==>>>" + pData);
							char[] chs= pData.toCharArray();
							StringBuffer sb = new StringBuffer();
							for(int i=0 ; i < (chs.length - 1); i=i+2)
							{
								sb.append(chs[i + 1]);
								sb.append(chs[i]);
							}
							pData = sb.toString(); 
						} 
						android.util.Log.d(TAG_X,"pData is   ==>>>" + pData);
						
						Settings.System.putString(getApplicationContext().getContentResolver(), "telcel_lock_password", pData);
					}
					else
					{
						android.util.Log.d(TAG_X,"get sml password fail");
					}
					break;
				}

				case EVENT_ADDLOCK_SML_NVRAM:
				{
					ar = (AsyncResult) msg.obj;
					if(ar.exception == null)
					{
						String title = null;
            			String message = mContext.getString(R.string.simlock_enabled);
						AlertDialog dialog = hq_createDialog(title, message);
            			dialog.show();
						Log.d(TAG_X,"addlock sml nvram successfully");
						Settings.System.putInt(getApplicationContext().getContentResolver(), "telcel_lock_left_count", 5);
					}
					else
					{
						Log.d(TAG_X,"addlock sml nvram fail");
					}
					break;
				}
				
				case EVENT_UNLOCK_SML_NVRAM:
				{
					ar = (AsyncResult) msg.obj;
					if(ar.exception == null)
					{
						Log.d(TAG_X,"unlock sml nvram successfully");
						Settings.System.putInt(getApplicationContext().getContentResolver(), "telcel_lock_status", 2);
					}
					else
					{
						Log.d(TAG_X,"unlock sml nvram fail");
					}
					break;
				}

				case EVENT_GET_SML_RETRYCOUNTS:
				{
					ar = (AsyncResult) msg.obj;
                      if (ar.exception != null) { //Query exception occurs
                          android.util.Log.d("zhangjinqiang","Query network lock fail");
                      } else {
                          	String[] LockState = (String []) ar.result;
						android.util.Log.d("zhangjinqiang",LockState[0]);
					  	String retryCounts =	formatData(LockState);
					  	android.util.Log.d("zhangjinqiang","retryCounts="+retryCounts);
					  	Settings.System.putInt(getContentResolver(), "left_unlock_counts", Integer.parseInt(retryCounts));	
                      }
                      break;
				}

				case EVENT_READ_SIM1_INFO:
				{
					ar = (AsyncResult)msg.obj;
					if(ar.exception == null){
						String[] datas = (String[])ar.result;
						android.util.Log.d(TAG_X,"read SIM1_INFO status successfully  ==>>>" + datas.length);
						//delete by yulifeng for HQ02052570,20160823
						//android.util.Log.d(TAG_X,"zhangjinqiang ==>>>" + datas[0]);
						String[] pData = null;
						if(datas.length > 0)				
						{					
							pData = parseSIMData(datas);				
						}
						if(pData!=null&&pData.length>= 4){
							int lockStatus = Integer.parseInt(pData[0],16);
							Settings.System.putInt(getApplicationContext().getContentResolver(), "sml_lock_status", lockStatus);
							int lockType = Integer.parseInt(pData[2],16);
							Settings.System.putInt(getApplicationContext().getContentResolver(), "sml_lock_type", lockType);
							int retryCounts = Integer.parseInt(pData[3],16);
							Settings.System.putInt(getApplicationContext().getContentResolver(), "sml_retry_counts", retryCounts);
							Log.d(TAG_X,"EVENT_GET_SML_STATUS ==>>>  pData : " + pData.toString() + " lockStatus : " + lockStatus);
							if(lockStatus != 0){
								char[] chs = pData[1].toCharArray();
								StringBuffer sb = new StringBuffer();
								for(int i=0 ; i < (chs.length - 1); i=i+2) 
	                            	{ 
	                                 sb.append(chs[i + 1]); 
	                                 sb.append(chs[i]); 
	                            	}
								pData[1] = sb.toString();
								Settings.System.putString(getApplicationContext().getContentResolver(), "telcel_lock_password", pData[1]);
								//delete by yulifeng for HQ02052570,20160823
								//Log.d(TAG_X,"EVENT_GET_SML_STATUS ==>>>  password: "  + " telcel_lock_password : " +  pData[1]);
							}
						}else if(pData!=null&&pData.length== 1){
							int lockStatus = Integer.parseInt(pData[0],16);
							Settings.System.putInt(getApplicationContext().getContentResolver(), "sml_lock_status", lockStatus);
						}
					}else{
						android.util.Log.d(TAG_X,"get sml status fail");
					}
					break;
				}

				case EVENT_READ_SIM2_INFO:
				{
					ar = (AsyncResult)msg.obj;
					if(ar.exception == null){
						String[] datas = (String[])ar.result;
						android.util.Log.d(TAG_X,"read SIM2_INFO status successfully  ==>>>" + datas.length);
						//delete by yulifeng for HQ02052570,20160823
						//android.util.Log.d(TAG_X,"zhangjinqiang ==>>>" + datas[0]);
						String[] pData = null;
						if(datas.length > 0)				
						{					
							pData = parseSIMData(datas);				
						}
						if(pData!=null&&pData.length>= 4){
							int lockStatus = Integer.parseInt(pData[0],16);
							Settings.System.putInt(getApplicationContext().getContentResolver(), "sml_sim2_lock_status", lockStatus);
							int lockType = Integer.parseInt(pData[2],16);
							Settings.System.putInt(getApplicationContext().getContentResolver(), "sml_sim2_lock_type", lockType);
							int retryCounts = Integer.parseInt(pData[3],16);
							Settings.System.putInt(getApplicationContext().getContentResolver(), "sml_sim2_retry_counts", retryCounts);
							Log.d(TAG_X,"EVENT_GET_SML2_STATUS ==>>>  pData : " + pData.toString() + " lockStatus : " + lockStatus);
							if(lockStatus != 0){
								char[] chs = pData[1].toCharArray();
								StringBuffer sb = new StringBuffer();
								for(int i=0 ; i < (chs.length - 1); i=i+2) 
	                            	{ 
	                                 sb.append(chs[i + 1]); 
	                                 sb.append(chs[i]); 
	                            	}
								pData[1] = sb.toString();
								Settings.System.putString(getApplicationContext().getContentResolver(), "sim2_lock_password", pData[1]);
								//delete by yulifeng for HQ02052570,20160823
								//Log.d(TAG_X,"EVENT_GET_SML_STATUS ==>>>  password: "  + " sim2_lock_password : " +  pData[1]);
							}
						}
					}else{
						Log.d(TAG_X,"get sml status fail");
					}
					break;
				}

				case EVENT_READ_SML_INFO:{
					//+QSMLE:00;0000;00;00000000;00
					ar = (AsyncResult) msg.obj;
                      if (ar.exception != null) { //Query exception occurs
                          android.util.Log.d("zhangjinqiang","Query network lock fail");
                      } else {
                          	String[] datas = (String[])ar.result;
						Log.d(TAG_X,"read status successfully  ==>>>" + datas.length);
						Log.d(TAG_X,"read status successfully  ==>>>" + datas[0]);
						if(datas.length > 0)				
						{					
							int[] pData = parseSIMLockInfoData(datas);		
							Settings.System.putInt(getApplicationContext().getContentResolver(), "sml_sim_relate", pData[0]);
							Settings.System.putInt(getApplicationContext().getContentResolver(), "sml_need_pop", pData[1]);
							Settings.System.putInt(getApplicationContext().getContentResolver(), "sml_need_pop_top", pData[2]);
							Settings.System.putInt(getApplicationContext().getContentResolver(), "sml_can_cancel_pop", pData[3]);
							Settings.System.putInt(getApplicationContext().getContentResolver(), "sml_unlock_time", pData[4]);
							Settings.System.putInt(getApplicationContext().getContentResolver(), "sml_unlock_code", pData[5]);
						}
                      }
                      break;
				}

				/*
				case EVENT_READ_SML_SHA:{
					ar = (AsyncResult) msg.obj;
                      if (ar.exception != null) { //Query exception occurs
                          android.util.Log.d("zhangjinqiang","Query sml_sha fail");
                      } else {
						String[] datas = (String[])ar.result;
						android.util.Log.d("zhangjinqiang","SML_SHA:"+datas[0]);
						if(datas.length>0){
							String SMLEK = getSMLEK(datas);
							android.util.Log.d("zhangjinqiang","SMLEK:"+SMLEK);
							Settings.System.putString(getApplicationContext().getContentResolver(), "sml_sha", SMLEK);
						}
					}
					break;
				}
				*/
				
				default:
					break;
			}
		}
	};

	private void getDefaultPhone()
	{
		Log.i(TAG_X,"getDefaultPhone");
		mPhone = (PhoneProxy) PhoneFactory.getDefaultPhone();
	}

	private void setSimLockAction(String[] str , int action){
		Log.v(TAG_X, "send at cmd "+ str[0]);
		getDefaultPhone();
		Log.v(TAG_X, "phone is ====>>>"+ mPhone);
		mPhone.invokeOemRilRequestStrings(str, mResponseHander.obtainMessage(action));
	}
	
	private class HQSIMLockReceiver extends BroadcastReceiver 
	{
		 @Override
		 public void onReceive(Context context, Intent intent) 
		 {
			mContext = context;		 
			String action = intent.getAction();
			Log.i(TAG_X,"HQSIMLockReceiver action is ===>>" + action);
			if(action.equals("android.intent.action.simlock.unlock"))
			{
				String[] unLockString = { "AT+SMLUN=1,", "+SMLUN" };
				setSimLockAction(unLockString, EVENT_UNLOCK_SML_NVRAM);
		 	}
			else if (action.equals("android.intent.action.simlock.lock"))
			{
			 	String[] addLockString = { "AT+SMLADD=1,", "+SMLADD" };
				setSimLockAction(addLockString,EVENT_ADDLOCK_SML_NVRAM);
			}
			else if (action.equals("android.intent.action.simlock.getpassword"))
			{
			 	String[] getPasswordString = { "AT+ASMLR=1,", "+ASMLR" };
				setSimLockAction(getPasswordString,EVENT_GET_SML_PASSWORD_FROM_NVRAM);
			}else if(action.equals("android.intent.action.noSimCard_unLock")){
			    int isLock = Settings.System.getInt(getApplicationContext().getContentResolver(), "sml_lock_status", 1);
				if(isLock!=0){
					IccNetworkDepersonalizationPanel ndpPanel = new IccNetworkDepersonalizationPanel(PhoneGlobals.getInstance(),
						PhoneFactory.getDefaultPhone());
               		ndpPanel.show();
				}else{
					Toast.makeText(getApplicationContext(), R.string.unlocked_tips, Toast.LENGTH_LONG).show();
				}
			}
			else if(action.equals("com.android.huawei.simlock.unlock")){
				int sml_subId = intent.getIntExtra("sml_slotID",0);
				android.util.Log.d("zhangjinqiang","subIdstr1:"+sml_subId);
				//add by chiguoqing for HQ01670805
				int smlStatus = 0;
				if(sml_subId==0){
					smlStatus = Settings.System.getInt(getApplicationContext().getContentResolver(), "sml_lock_status", -1);
				}else if(sml_subId==1){
					smlStatus = Settings.System.getInt(getApplicationContext().getContentResolver(), "sml_sim2_lock_status", -1);
				}
				if(smlStatus==1){
					showICCPanel(sml_subId);
				}
			}else if(action.equals("com.android.huawei.sim.iccpanel.show")){
				int mSIMNum = Settings.System.getInt(getApplicationContext().getContentResolver(), "sml_sim", -1);
				if(mSIMNum==1||mSIMNum==0){
					showICCPanel(mSIMNum);
					Settings.System.putInt(getApplicationContext().getContentResolver(), "sml_sim",-1);
				}
			}else if(action.equals("com.android.huawei.simlock.getSIM1Status")){
				setSimLockAction(getSIM1Status,EVENT_READ_SIM1_INFO);
			}else if(action.equals("com.android.huawei.simlock.getSIM2Status")){
				setSimLockAction(getSIM2Status,EVENT_READ_SIM2_INFO);
			}else if(action.equals("android.intent.action.LOCALE_CHANGED")){  //add by yulifeng for HQ01670805 20160817
				if(ndpPanel!=null){
					ndpPanel = null;  
				}
				if(ndpPanel2!=null){
					ndpPanel2=null;
				}
			}
		 }
	}

	private AlertDialog hq_createDialog(String title, String message) 
	{
        final AlertDialog dialog  =  new AlertDialog.Builder(mContext)
            .setTitle(title)
            .setCancelable(false)
            .setMessage(message)
            .setNegativeButton(R.string.ok, new DialogInterface.OnClickListener() 
            {
                public void onClick(DialogInterface dialog, int which)
				{
					int lockStatus = Settings.System.getInt(mContext.getContentResolver(), "telcel_lock_status", -1);
					if (1 != lockStatus)
					{
						hq_phone_reboot();
					}
                }
            } )
            .create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
        return dialog;
    }

	public void hq_phone_reboot() 
	{
       Intent intent = new Intent(Intent.ACTION_REBOOT);
       intent.putExtra("nowait", 1);
       intent.putExtra("interval", 1);
       intent.putExtra("window", 0);
       mContext.sendBroadcast(intent);
    }

	private  void getSIMStatus(int phoneId){
		if(phoneId==0){
			setSimLockAction(getSIM1Status,EVENT_READ_SIM1_INFO);
		}else if(phoneId==1){
			setSimLockAction(getSIM2Status,EVENT_READ_SIM2_INFO);
		}
	}

	private void initIccPanel(int phoneid){
		int subid = SubscriptionManager.getDefaultDataSubId();
 		int mDefaultPhoneID = SubscriptionManager.getPhoneId(subid);
		if (mDefaultPhoneID == -1){
				TelephonyManager mTelephonyManager =(TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
				if (mTelephonyManager.hasIccCard(0)){
					mDefaultPhoneID=0;
				}
				else{
					mDefaultPhoneID=1;
				}
			}
		android.util.Log.d("zhangjinqiang","DefaultPhoneID:"+mDefaultPhoneID);
		
		if(mDefaultPhoneID==0){
			  if(phoneid==0){
	           		showICCPanel(phoneid);
				  	isSIMOneShow=true;
			  }else if(phoneid==1&&!isSIMOneShow){
					 showICCPanel(phoneid);
			  }else if(phoneid==1&&isSIMOneShow){
					 Settings.System.putInt(getApplicationContext().getContentResolver(), "sml_sim",phoneid);
			  }
		}else if(mDefaultPhoneID==1){
			if(phoneid==1){
	           		showICCPanel(phoneid);
				  	isSIMTwoShow=true;
			  }else if(phoneid==0&&!isSIMTwoShow){
					showICCPanel(phoneid);
			  }else if(phoneid==0&&isSIMTwoShow){
					Settings.System.putInt(getApplicationContext().getContentResolver(), "sml_sim",phoneid);
			  }
		}
	}

	private void showICCPanel(int phoneid){ 
        //modify by yulifeng for HQ01987750 20160817,start
        if(phoneid==0){
			if(ndpPanel==null){
				ndpPanel = new IccNetworkDepersonalizationPanel(PhoneGlobals.getInstance(),
							PhoneFactory.getPhone(phoneid),phoneid);
			}
			ndpPanel.show();
		}
		if(phoneid==1){
			if(ndpPanel2==null){
				ndpPanel2 = new IccNetworkDepersonalizationPanel(PhoneGlobals.getInstance(),
						PhoneFactory.getPhone(phoneid),phoneid);
			}
			ndpPanel2.show();
		}
		/*
        if(ndpPanel!=null){
            android.util.Log.d("zhangjinqiang","showICCPanel-if:"+phoneid);
            ndpPanel.show();  
        }else{
        	ndpPanel = new IccNetworkDepersonalizationPanel(PhoneGlobals.getInstance(),
							PhoneFactory.getPhone(phoneid),phoneid);
		 	android.util.Log.d("zhangjinqiang","showICCPanel:"+phoneid);
	     	ndpPanel.show();
        }
        */
        //modify by yulifeng for HQ01987750 20160817,end
	}
	//add by zhangjinqinag end
}
