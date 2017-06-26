package com.mediatek.datashaping;

import java.util.Timer;
import java.util.TimerTask;

import com.android.internal.telephony.TelephonyIntents;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.PowerManager.WakeLock;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Slog;
//+ Input
import android.view.WindowManagerInternal;
import com.android.server.LocalServices;
import android.view.InputEvent;
import android.view.InputFilter;
import android.view.InputDevice;
import android.view.KeyEvent;

import com.android.server.input.InputManagerService;
//-

public class DataShapingServiceImpl extends IDataShapingManager.Stub {

    public static final int DATA_SHAPING_STATE_OPEN_LOCKED = 1;
    public static final int DATA_SHAPING_STATE_OPEN = 2;
    public static final int DATA_SHAPING_STATE_CLOSE = 3;

    public static final long ALARM_MANAGER_OPEN_GATE_INTERVAL = 5 * 60 * 1000;
    public static final long GATE_CLOSE_EXPIRED_TIME = 5 * 60 * 1000;
    public static final int GATE_CLOSE_SAFE_TIMER = 10 *60 * 1000;

    private static final int MSG_CHECK_USER_PREFERENCE = 1;
    private static final int MSG_INIT = 2;
    private static final int MSG_STOP = 3;
    private static final int MSG_SCREEN_STATE_CHANGED = 10;
    private static final int MSG_NETWORK_TYPE_CHANGED = 11;
    private static final int MSG_WIFI_AP_STATE_CHANGED = 12;
    private static final int MSG_USB_STATE_CHANGED = 13;
    private static final int MSG_ALARM_MANAGER_TRIGGER = 14;
    private static final int MSG_LTE_AS_STATE_CHANGED = 15;
    private static final int MSG_SHARED_DEFAULT_APN_STATE_CHANGED = 16;
    private static final int MSG_GATE_CLOSE_TIMER_EXPIRED = 17;
    private static final int MSG_HEADSETHOOK_CHANGED = 18;
    private static final int MSG_BT_AP_STATE_CHANGED = 19;
    private static final int MSG_CONNECTIVITY_CHANGED = 20;

    private static final int WAKE_LOCK_TIMEOUT = 30000;
    private static final String CLOSE_TIME_EXPIRED_ACTION
                                    = "com.mediatek.datashaping.CLOSE_TIME_EXPIRED";

    private final String TAG = "DataShapingService";
    private Context mContext;
    private HandlerThread mHandlerThread;
    private DataShapingHandler mDataShapingHandler;
    private DataShapingUtils mDataShapingUtils;

    private DataShapingState mGateOpenState;
    private DataShapingState mGateOpenLockedState;
    private DataShapingState mGateCloseState;

    private DataShapingState mCurrentState;
    private long mLastAlarmTriggerSuccessTime;

    private PendingIntent mPendingIntent;
    private WakeLock mWakelock;

    // + Input
    private WindowManagerInternal mWindowManagerService;
    private InputManagerService mInputManagerService;
    private DataShapingInputFilter mInputFilter;
    private boolean mRegisterInput = false;
    private final Object mLock = new Object();
    // -

    private boolean mDataShapingEnabled;
    private BroadcastReceiver mBroadcastReceiver;

    public DataShapingServiceImpl(Context context) {
        mContext = context;
        //mHandlerThread = new HandlerThread(TAG);
        //mDataShapingHandler = new DataShapingHandler(mHandlerThread.getLooper());
        mDataShapingUtils = DataShapingUtils.getInstance(mContext);
    }

    public void registerReceiver() {
        Slog.d(TAG, "registerReceiver start");
        if (mBroadcastReceiver == null) {
            mBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    Slog.d(TAG, "received broadcast, action is: " + action);
                    if (Intent.ACTION_SCREEN_ON == action) {
                        mDataShapingHandler.obtainMessage(MSG_SCREEN_STATE_CHANGED, true)
                        .sendToTarget();
                    } else if (Intent.ACTION_SCREEN_OFF == action) {
                        mDataShapingHandler.obtainMessage(MSG_SCREEN_STATE_CHANGED, false)
                        .sendToTarget();
                    } else if (TelephonyIntents.ACTION_PS_NETWORK_TYPE_CHANGED == action) {
                        mDataShapingUtils.setCurrentNetworkType(intent);
                        mDataShapingHandler.obtainMessage(MSG_NETWORK_TYPE_CHANGED, intent)
                        .sendToTarget();
                    } else if (ConnectivityManager.CONNECTIVITY_ACTION == action) {
                        mDataShapingHandler.obtainMessage(MSG_CONNECTIVITY_CHANGED, intent)
                        .sendToTarget();
                    } else if (WifiManager.WIFI_AP_STATE_CHANGED_ACTION == action) {
                        mDataShapingHandler.obtainMessage(MSG_WIFI_AP_STATE_CHANGED, intent)
                        .sendToTarget();
                    } else if (UsbManager.ACTION_USB_STATE == action) {
                        mDataShapingHandler.obtainMessage(MSG_USB_STATE_CHANGED, intent)
                        .sendToTarget();
                    } else if (TelephonyIntents.ACTION_LTE_ACCESS_STRATUM_STATE_CHANGED == action) {
                        mDataShapingHandler.obtainMessage(MSG_LTE_AS_STATE_CHANGED, intent)
                        .sendToTarget();
                    } else if (TelephonyIntents.ACTION_SHARED_DEFAULT_APN_STATE_CHANGED == action) {
                        mDataShapingHandler.obtainMessage(MSG_SHARED_DEFAULT_APN_STATE_CHANGED, intent)
                        .sendToTarget();
                    } else if (CLOSE_TIME_EXPIRED_ACTION == action) {
                        getWakeLock();
                        mDataShapingHandler.obtainMessage(MSG_GATE_CLOSE_TIMER_EXPIRED).sendToTarget();
                    } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action) ||
                        BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                        mDataShapingHandler.obtainMessage(MSG_BT_AP_STATE_CHANGED, intent)
                        .sendToTarget();
                    }
                }
            };
        }
        IntentFilter eventsFilter = new IntentFilter();
        eventsFilter.addAction(Intent.ACTION_SCREEN_ON);
        eventsFilter.addAction(Intent.ACTION_SCREEN_OFF);
        eventsFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        eventsFilter.addAction(TelephonyIntents.ACTION_PS_NETWORK_TYPE_CHANGED);
        eventsFilter.addAction(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        eventsFilter.addAction(UsbManager.ACTION_USB_STATE);
        eventsFilter.addAction(TelephonyIntents.ACTION_LTE_ACCESS_STRATUM_STATE_CHANGED);
        eventsFilter.addAction(TelephonyIntents.ACTION_SHARED_DEFAULT_APN_STATE_CHANGED);
        eventsFilter.addAction(CLOSE_TIME_EXPIRED_ACTION);
        eventsFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        eventsFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        mContext.registerReceiver(mBroadcastReceiver, eventsFilter);
        Slog.d(TAG, "registerReceiver end");
    }

    //+ Input
    boolean registerListener() {
        if (mWindowManagerService == null || mInputManagerService == null) {
            Slog.d(TAG, "registerListener get WindowManager fail !");
            return false;
        }

        synchronized (mLock) {
            Slog.d(TAG, "registerListener registerInput Before: " + mRegisterInput);

            if (false == mRegisterInput &&
                !mInputManagerService.alreadyHasInputFilter()) {
                Slog.d(TAG, "registerListener!!!");
                mWindowManagerService.setInputFilter(mInputFilter);
                mRegisterInput = true;
            } else if (mRegisterInput) {
                Slog.d(TAG, "I have registered it");
            } else {
                Slog.d(TAG, "Someone registered it !!!");
            }

            Slog.d(TAG, "registerListener registerInput After: " + mRegisterInput);
        }
        return mRegisterInput;
    }

    void unregisterListener() {
        if (mWindowManagerService == null) {
            Slog.d(TAG, "unregisterListener get WindowManager fail !");
            return;
        }
        synchronized (mLock) {
            if (mRegisterInput) {
                Slog.d(TAG, "unregisterListener registerInput is TRUE , Set myself to null!");
                mWindowManagerService.setInputFilter(null);
                mRegisterInput = false;
            } else {
                Slog.d(TAG, "unregisterListener registerInput is False , Not to set to null!");
            }
        }
    }

    private class DataShapingInputFilter extends InputFilter{
        private final Context mContext;
        DataShapingInputFilter(Context context) {
            super(context.getMainLooper());
            mContext = context;
        }

        @Override
        public void onInputEvent(InputEvent event, int policyFlags) { 
            //Slog.d(TAG, "Received event: " + event + ", policyFlags=0x"
            //            + Integer.toHexString(policyFlags));
            if (event instanceof KeyEvent) {
                KeyEvent keyEvent = (KeyEvent) event;
                if (keyEvent.getAction() == KeyEvent.ACTION_DOWN ||
                    keyEvent.getAction() == KeyEvent.ACTION_UP) {
                    Slog.d(TAG, "Received event ACTION_UP or ACTION_DOWN");
                    if (mDataShapingHandler != null) {
                        mDataShapingHandler.sendEmptyMessage(MSG_HEADSETHOOK_CHANGED);
                    }
                }
            }
            super.onInputEvent(event, policyFlags);
        }

        @Override
        public void onUninstalled() {
            // System Server main thread
            Slog.d(TAG, "onUninstalled : " + mCurrentState);
            synchronized (mLock) {
                mRegisterInput = false;
                if (mCurrentState instanceof GateCloseState) {
                    mCurrentState = mGateOpenState;
                    Slog.d(TAG, "onUninstalled : Change to Gate Open");
                }
            }
        }
    }
    //-

    public void start() {
        mGateOpenState = new GateOpenState(this, mContext);
        mGateOpenLockedState = new GateOpenLockedState(this, mContext);
        mGateCloseState = new GateCloseState(this, mContext);
        setCurrentState(DATA_SHAPING_STATE_OPEN_LOCKED);

        Slog.d(TAG, "start check user preference");
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.BG_POWER_SAVING_ENABLE), true,
                mSettingsObserver);
        mSettingsObserver.onChange(false);
    }

    public void setCurrentState(int stateType) {
        switch (stateType) {
        case DATA_SHAPING_STATE_OPEN_LOCKED:
            mCurrentState = mGateOpenLockedState;
            unregisterListener();
            Slog.d(TAG, "[setCurrentState]: set to STATE_OPEN_LOCKED");
            break;
        case DATA_SHAPING_STATE_OPEN:
            mCurrentState = mGateOpenState;
            unregisterListener();
            Slog.d(TAG, "[setCurrentState]: set to STATE_OPEN");
            break;
        case DATA_SHAPING_STATE_CLOSE:
            mCurrentState = mGateCloseState;
            Slog.d(TAG, "[setCurrentState]: set to STATE_CLOSE");
            break;
        default:
            break;
        }
    }

    public void enableDataShaping() {
        // TODO for settings UI.
        Slog.d(TAG, "enableDataShaping");
    }

    public void disableDataShaping() {
        // TODO for settings UI.
        Slog.d(TAG, "disableDataShaping");
    }

    /**
     * Open the up link gate.
     * @param isForce currently this param is not usable.
     * @return true if data shaping service really handle this request.
     *         false if data shaping service doesn't handle this request.
     */
    public boolean openLteDataUpLinkGate(boolean isForce) {
        if (!mDataShapingEnabled) {
            Slog.d(TAG, "[openLteDataUpLinkGate] mDataShapingEnabled is false!");
            return false;
        }
        String supportAlarmGroup = SystemProperties.get("persist.datashaping.alarmgroup");
        Slog.d(TAG, "persist.datashaping.alarmgroup: " + supportAlarmGroup);
        if (TextUtils.isEmpty(supportAlarmGroup) || "1".equals(supportAlarmGroup)) {
            if (System.currentTimeMillis() - mLastAlarmTriggerSuccessTime
                    >= ALARM_MANAGER_OPEN_GATE_INTERVAL) {
                if (mDataShapingHandler != null) {
                    mDataShapingHandler.sendEmptyMessage(MSG_ALARM_MANAGER_TRIGGER);
                }
                mLastAlarmTriggerSuccessTime = System.currentTimeMillis();
                Slog.d(TAG, "Alarm manager openLteDataUpLinkGate: true");
                return true;
            } else {
                Slog.d(TAG, "Alarm manager openLteDataUpLinkGate: false");
                return false;
            }
        } else {
            return false;
        }
    }

    public void cancelCloseExpiredAlarm() {
        Slog.d(TAG, "[cancelCloseExpiredAlarm]");
        if (mPendingIntent != null) {
            AlarmManager alarmManager = (AlarmManager) mContext
                    .getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(mPendingIntent);
        }
    }

    public void startCloseExpiredAlarm() {
        Slog.d(TAG, "[startCloseExpiredAlarm] cancel previous alarm");
        cancelCloseExpiredAlarm();
        Slog.d(TAG, "[startCloseExpiredAlarm] start new alarm");
        AlarmManager alarmManager = (AlarmManager) mContext
                .getSystemService(Context.ALARM_SERVICE);
        if (mPendingIntent == null) {
            Intent intent = new Intent(CLOSE_TIME_EXPIRED_ACTION);
            mPendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
        }
        alarmManager.set(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + GATE_CLOSE_EXPIRED_TIME,
                mPendingIntent);
    }

    private class DataShapingHandler extends Handler {

        public DataShapingHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_CHECK_USER_PREFERENCE:
                // TODO, check user preference.
                if (true) {
                    sendEmptyMessage(MSG_INIT);
                } else {
                    sendEmptyMessage(MSG_STOP);
                }
                break;
            case MSG_INIT:
                Slog.d(TAG, "[handleMessage] msg_init");
                mWindowManagerService = LocalServices.getService(WindowManagerInternal.class);
                mInputManagerService = (InputManagerService) ServiceManager.getService(
                    mContext.INPUT_SERVICE);
                mInputFilter = new DataShapingInputFilter(mContext);
                //registerListener();
                break;
            case MSG_STOP:
                // TODO, stop self.
                break;
            case MSG_SCREEN_STATE_CHANGED:
                Slog.d(TAG, "[handleMessage] msg_screen_state_changed");
                mCurrentState.onScreenStateChanged((Boolean)msg.obj);
                break;
            case MSG_NETWORK_TYPE_CHANGED:
                Slog.d(TAG, "[handleMessage] msg_network_type_changed");
                mCurrentState.onNetworkTypeChanged((Intent)msg.obj);
                break;
            case MSG_WIFI_AP_STATE_CHANGED:
                Slog.d(TAG, "[handleMessage] msg_wifi_ap_state_changed");
                mCurrentState.onWifiTetherStateChanged((Intent)msg.obj);
                break;
            case MSG_USB_STATE_CHANGED:
                Slog.d(TAG, "[handleMessage] msg_usb_state_changed");
                mCurrentState.onUsbConnectionChanged((Intent)msg.obj);
                break;
            case MSG_ALARM_MANAGER_TRIGGER:
                Slog.d(TAG, "[handleMessage] msg_alarm_manager_trigger");
                mCurrentState.onAlarmManagerTrigger();
                break;
            case MSG_LTE_AS_STATE_CHANGED:
                Slog.d(TAG, "[handleMessage] msg_lte_as_state_changed");
                mCurrentState.onLteAccessStratumStateChanged((Intent)msg.obj);
                break;
            case MSG_SHARED_DEFAULT_APN_STATE_CHANGED:
                Slog.d(TAG, "[handleMessage] msg_shared_default_apn_state_changed");
                mCurrentState.onSharedDefaultApnStateChanged((Intent)msg.obj);
                break;
            case MSG_GATE_CLOSE_TIMER_EXPIRED:
                Slog.d(TAG, "[handleMessage] msg_gate_close_timer_expired");
                mCurrentState.onCloseTimeExpired();
                releaseWakeLock();
                break;
            case MSG_CONNECTIVITY_CHANGED:
                Slog.d(TAG, "[handleMessage] msg_connectivity_changed");
                mDataShapingUtils.setLteAsReport();
                break;
            case MSG_HEADSETHOOK_CHANGED:
                Slog.d(TAG, "[handleMessage] msg_headsethook_changed");
                mCurrentState.onMediaButtonTrigger();
                break;
            case MSG_BT_AP_STATE_CHANGED:
                Slog.d(TAG, "[handleMessage] msg_bt_ap_state_changed");
                mCurrentState.onBTStateChanged((Intent) msg.obj);
                break;
            default:
                break;
            }
        }
    }

    private void getWakeLock() {
        Slog.d(TAG, "[getWakeLock]");
        releaseWakeLock();
        if (mWakelock == null) {
            PowerManager powerManager = (PowerManager) mContext
                    .getSystemService(Context.POWER_SERVICE);
            mWakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    this.getClass().getCanonicalName());
        }
        mWakelock.acquire(WAKE_LOCK_TIMEOUT);
    }

    private void releaseWakeLock() {
        Slog.d(TAG, "[releaseWakeLock]");
        if (mWakelock != null && mWakelock.isHeld()) {
            Slog.d(TAG, "really release WakeLock");
            mWakelock.release();
            mWakelock = null;
        }
    }

    private ContentObserver mSettingsObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange) {
            final boolean dataShapingEnabled = 0 != Settings.System.getInt(
                    mContext.getContentResolver(), Settings.System.BG_POWER_SAVING_ENABLE, 0);
            if (dataShapingEnabled != mDataShapingEnabled) {
                if (dataShapingEnabled) {
                    Slog.d(TAG, "data shaping enabled, start handler thread!");
                    mHandlerThread = new HandlerThread(TAG);
                    mHandlerThread.start();
                    mDataShapingHandler = new DataShapingHandler(mHandlerThread.getLooper());
                    mDataShapingHandler.sendEmptyMessage(MSG_INIT);
                    /// M: register at main thread to avoid race condition with unregister
                    setCurrentState(DATA_SHAPING_STATE_OPEN_LOCKED);
                    registerReceiver();
                } else {
                    if (mBroadcastReceiver != null) {
                        mContext.unregisterReceiver(mBroadcastReceiver);
                    }
                    if (mHandlerThread != null) {
                        mHandlerThread.quitSafely();
                    }
                    mDataShapingUtils.reset();
                    DataShapingServiceImpl.this.reset();
                    Slog.d(TAG, "data shaping disabled, stop handler thread and reset!");
                }
                mDataShapingEnabled = dataShapingEnabled;
            }
        };
    };

    private void reset() {
        setCurrentState(DATA_SHAPING_STATE_OPEN_LOCKED);
        releaseWakeLock();
        cancelCloseExpiredAlarm();
    }
}
