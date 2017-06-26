package com.mediatek.datashaping;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

import android.R.integer;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.TelephonyManager;
import android.util.Slog;
import android.view.Display;
import android.view.WindowManager;

import com.mediatek.internal.telephony.ITelephonyEx;

import java.util.Set;

public class DataShapingUtils {

    public static final long CLOSING_DELAY_BUFFER_FOR_MUSIC = 5000;

    private static final String LTE_AS_STATE_UNKNOWN =
            PhoneConstants.LTE_ACCESS_STRATUM_STATE_UNKNOWN;
    private static final String LTE_AS_STATE_CONNECTED =
            PhoneConstants.LTE_ACCESS_STRATUM_STATE_CONNECTED;
    private static final String LTE_AS_STATE_IDLE =
            PhoneConstants.LTE_ACCESS_STRATUM_STATE_IDLE;

    private static final String TAG = "DataShapingUtils";
    private static DataShapingUtils sDataShapingUtils;

    private WifiManager mWifiManager;
    private UsbManager mUsbManager;
    private ConnectivityManager mConnectivityManager;
    private PowerManager mPowerManager;
    private AudioManager mAudioManager;
    private BluetoothManager mBluetoothManager;

    private Context mContext;

    private int mCurrentNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    private boolean mIsMobileConnection;

    // For music active, will not block the uplink, and will delay the closing time
    // for the possible network operations of music apps after playing music, for example,
    // query the next song's url from server.
    private boolean mIsClosingDelayForMusic;
    private long mClosingDelayStartTime;

    synchronized public static DataShapingUtils getInstance(Context context) {
        if (sDataShapingUtils == null) {
            sDataShapingUtils = new DataShapingUtils(context);
        }
        return sDataShapingUtils;
    }

    private DataShapingUtils(Context context) {
        mContext = context;
    }

    public void setLteAsReport() {
        if (mConnectivityManager == null) {
            mConnectivityManager = (ConnectivityManager) mContext
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
        }
        NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
        int networkType = ConnectivityManager.TYPE_NONE;
        if (networkInfo != null && networkInfo.isConnectedOrConnecting()) {
            networkType = networkInfo.getType();
        }
        boolean isMobile = ConnectivityManager.isNetworkTypeMobile(networkType);
        Slog.d(TAG, "[setLteAsReport] current network isMobile|" + isMobile
                + " mIsMobileConnection|" + mIsMobileConnection);
        if (isMobile != mIsMobileConnection) {
            mIsMobileConnection = isMobile;
            if (mIsMobileConnection) {
                setLteAccessStratumReport(mIsMobileConnection);
            }
        }
    }

    public void setCurrentNetworkType(Intent intent) {
        if (intent == null) {
            return;
        }
        int networkType = intent.getIntExtra(PhoneConstants.PS_NETWORK_TYPE_KEY,
                TelephonyManager.NETWORK_TYPE_UNKNOWN);
        Slog.d(TAG, "[setCurrentNetworkTypeIntent] networkType: " + networkType);
        mCurrentNetworkType = networkType;
    }

    public boolean isScreenOn() {
        if (mPowerManager == null) {
            mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        }
        Slog.d(TAG, "[isScreenOn] " + mPowerManager.isScreenOn());
        return mPowerManager.isScreenOn();
    }

    public boolean isWifiTetheringEnabled() {
        if (mWifiManager == null) {
            mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        }
        Slog.d(TAG, "[isWifiTetheringEnabled] isWifiApEnabled: " + mWifiManager.isWifiApEnabled());
        return mWifiManager.isWifiApEnabled();
    }

    public boolean isWifiTetheringEnabled(Intent intent) {
        if (intent == null) {
            return false;
        }
        int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_AP_STATE,
                WifiManager.WIFI_AP_STATE_DISABLED);
        Slog.d(TAG, "[isWifiTetheringEnabledIntent] state: " + state);
        if (state == WifiManager.WIFI_AP_STATE_ENABLED
                || state == WifiManager.WIFI_AP_STATE_ENABLING) {
            return true;
        }
        return false;
    }

    /**
     * Do usb connected state contained the usb tethering?
     * @return true, if usb connected.
     *         false, if usb disconnected.
     */
    public boolean isUsbConnected() {
        if (mUsbManager == null) {
            mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
        }
        Slog.d(TAG, "[isUsbConnected] isUsbConneted: " + (mUsbManager.getCurrentState() == 1));
        return mUsbManager.getCurrentState() == 1;
    }

    public boolean isUsbConnected(Intent intent) {
        if (intent == null) {
            return false;
        }
        boolean isUsbConnected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
        Slog.d(TAG, "[isUsbConnectedIntent] isUsbConnected: " + isUsbConnected);
        return intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
    }

    /**
     * Get the network type of current connection.
     * @return true, only the current network is connected and network type is lte.
     *         false, for other network types or it's in connecting state.
     */
    public boolean isNetworkTypeLte() {
        Slog.d(TAG, "[isNetworkTypeLte] mCurrentNetworkType: " + mCurrentNetworkType);
        if (mCurrentNetworkType == TelephonyManager.NETWORK_TYPE_LTE) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isNetworkTypeLte(Intent intent) {
        if (intent == null) {
            return false;
        }
        int networkType = intent.getIntExtra(PhoneConstants.PS_NETWORK_TYPE_KEY,
                TelephonyManager.NETWORK_TYPE_UNKNOWN);
        Slog.d(TAG, "[isNetworkTypeLteIntent] networkType: " + networkType);
        if (networkType == TelephonyManager.NETWORK_TYPE_LTE) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * check if BT is connected by other device by ACL Connection intent.
     * @param intent : Message info to get action :ACTION_ACL_CONNECTED, ACTION_ACL_DISCONNECTED
     * @return: false:BT is not connected by any device/true:BT is connected by at least one device
     */
    public boolean isBTStateOn(Intent intent) {
        if (intent == null) {
            return false;
        }
        // If someone is Connected, Gate OpenLocked
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) {
            Slog.d(TAG, "[isBTStateOn] BT ACTION_ACL_CONNECTED !");
            return true;
        }
        // If someone is disconnected, check others
        return isBTStateOn();
    }

    /**
     * check if BT is connected by other device by ACL Connection.
     * @return: false:BT is not connected by any device/true:BT is connected by at least one device
     */
    public boolean isBTStateOn() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) mContext
                .getSystemService(Context.BLUETOOTH_SERVICE);
        }
        if (mBluetoothManager == null) {
            Slog.d(TAG, "BluetoothManager is null");
            return false;
        }
        BluetoothAdapter mAdapter = mBluetoothManager.getAdapter();
        if (mAdapter == null) {
            Slog.d(TAG, "BluetoothAdapter is null");
            return false;
        }
        int state = mAdapter.getState();
        // 1. If BT is off , retrun False directly
        if (BluetoothAdapter.STATE_OFF == state) {
            Slog.d(TAG, "[isBTStateOn] BT is Off");
            return false;
        }

        Set<BluetoothDevice> bondedDevices = mAdapter.getBondedDevices();
        // 2. No bonded Device, retrun False directly
        if (bondedDevices == null) {
           Slog.d(TAG, "[isBTStateOn] No bonded Devices");
           return false;
        }

        //  If BT is on , Get the bonded device and get the ACL connection state
        for (BluetoothDevice device : bondedDevices) {
            if (device.isConnected()) {
                int deviceType = device.getBluetoothClass().getDeviceClass();
                Slog.d(TAG, "[isBTStateOn] Connected Device = " + device.getName() +
                       ", DeviceType = " + deviceType);
                // If the connected devicetype is headset, skip it.
                if (BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET == deviceType) {
                    Slog.d(TAG, "Connected Device is AUDIO_VIDEO_WEARABLE_HEADSET");
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean canTurnFromLockedToOpen() {
        boolean isNetworkTypeLte = isNetworkTypeLte();
        boolean isScreenOn = isScreenOn();
        boolean isSharedDefaultApnEstablished = isSharedDefaultApnEstablished();
        boolean isUsbConnected = isUsbConnected();
        boolean isWifiTetheringEnabled = isWifiTetheringEnabled();
        Slog.d(TAG, "[canTurnFromLockedToOpen] isNetworkTypeLte|" + isNetworkTypeLte
                + " isScreenOn|" + isScreenOn
                + " isSharedDefaultApnEstablised|" + isSharedDefaultApnEstablished
                + " isUsbConnected|" + isUsbConnected
                + " isWifiTetheringEnabled|" + isWifiTetheringEnabled);
        boolean isReady = isNetworkTypeLte && !isScreenOn && !isSharedDefaultApnEstablished
                && !isUsbConnected && !isWifiTetheringEnabled;
        // If all symptoms are ready to turn from OpenLocked to Open, Check BT status.
        if (isReady) {
            boolean isBTStateOn = isBTStateOn();
            Slog.d(TAG, "[canTurnFromLockedToOpen] isBTStateOn|" + isBTStateOn);
            isReady = !isBTStateOn;
        }
        Slog.d(TAG, "[canTurnFromLockedToOpen]: " + isReady);
        return isReady;
    }

    public boolean isLteAccessStratumConnected(Intent intent) {
        if (intent == null) {
            // Default return true, because we do not incline to close the gate.
            return true;
        }
        String lteAsState = intent.getStringExtra(PhoneConstants.LTE_ACCESS_STRATUM_STATE_KEY);
        Slog.d(TAG, "[isLteAccessStratumConnectedIntent] lteAsState: " + lteAsState);
        if (LTE_AS_STATE_CONNECTED.equalsIgnoreCase(lteAsState)) {
            return true;
        } else if (LTE_AS_STATE_UNKNOWN.equalsIgnoreCase(lteAsState)) {
            setLteAccessStratumReport(true);
            // Treat the unknown state as connecting state.
            return true;
        } else if (LTE_AS_STATE_IDLE.equalsIgnoreCase(lteAsState)) {
            return false;
        }
        return true;
    }

    public boolean isLteAccessStratumConnected() {
        ITelephonyEx telephonyExService = ITelephonyEx.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICE_EX));
        if (telephonyExService == null) {
            Slog.d(TAG, "[isLteAccessStratumConnected] mTelephonyExService is null!");
            return true;
        }
        String state = null;
        try {
            state = telephonyExService.getLteAccessStratumState();
        } catch (RemoteException remoteException) {
            Slog.d(TAG, "[isLteAccessStratumConnected] remoteException: " + remoteException);
        }
        Slog.d(TAG, "[isLteAccessStratumConnected] state: " + state);
        if (LTE_AS_STATE_CONNECTED.equalsIgnoreCase(state)) {
            return true;
        } else if (LTE_AS_STATE_UNKNOWN.equalsIgnoreCase(state)) {
            setLteAccessStratumReport(true);
            // Treat the unknown state as connecting state.
            return true;
        } else if (LTE_AS_STATE_IDLE.equalsIgnoreCase(state)) {
            return false;
        }
        return true;
    }

    public boolean isSharedDefaultApnEstablished() {
        ITelephonyEx telephonyExService = ITelephonyEx.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICE_EX));
        if (telephonyExService == null) {
            Slog.d(TAG, "[isSharedDefaultApnEstablished] mTelephonyExService is null!");
            // Default return true, because we do not incline to close the gate.
            return true;
        }
        boolean isEstablished = true;
        try {
            isEstablished = telephonyExService.isSharedDefaultApn();
        } catch (RemoteException remoteException) {
            Slog.d(TAG, "[isSharedDefaultApnEstablished] remoteException: " + remoteException);
        }
        Slog.d(TAG, "[isSharedDefaultApnEstablished]: " + isEstablished);
        return isEstablished;
    }

    public boolean isSharedDefaultApnEstablished(Intent intent) {
        if (intent == null) {
            // Default return true, because we do not incline to close the gate.
            return true;
        }
        boolean isSharedDefaultApn = intent.getBooleanExtra(PhoneConstants.SHARED_DEFAULT_APN_KEY,
                true);
        Slog.d(TAG, "[isSharedDefaultApnEstablishedIntent]: " + isSharedDefaultApn);
        return isSharedDefaultApn;
    }

    public boolean setLteUplinkDataTransfer(boolean isOn, int safeTimer) {
        Slog.d(TAG, "[setLteUplinkDataTransfer] isOn: " + isOn);
        ITelephonyEx telephonyExService = ITelephonyEx.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICE_EX));
        if (telephonyExService == null) {
            Slog.d(TAG, "[setLteUplinkDataTransfer] mTelephonyExService is null!");
            return false;
        }
        boolean isSuccess = false;
        try {
            isSuccess = telephonyExService.setLteUplinkDataTransfer(isOn, safeTimer);
        } catch (RemoteException remoteException) {
            Slog.d(TAG, "[setLteUplinkDataTransfer] remoteException: " + remoteException);
        }
        Slog.d(TAG, "[setLteUplinkDataTransfer] TelephonyManager return set result: "
                 + isSuccess);
        return isSuccess;
    }

    public boolean setLteAccessStratumReport(boolean isEnable) {
        Slog.d(TAG, "[setLteAccessStratumReport] enable: " + isEnable);
        ITelephonyEx telephonyExService = ITelephonyEx.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICE_EX));
        if (telephonyExService == null) {
            Slog.d(TAG, "[setLteAccessStratumReport] mTelephonyExService is null!");
            return false;
        }
        boolean isSuccess = false;
        try {
            isSuccess = telephonyExService.setLteAccessStratumReport(isEnable);
        } catch (RemoteException remoteException) {
            Slog.d(TAG, "[setLteAccessStratumReport] remoteException: " + remoteException);
        }
        Slog.d(TAG, "[setLteAccessStratumReport] TelephonyManager return set result: "
                + isSuccess);
        return isSuccess;
    }

    public boolean isMusicActive() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        }
        boolean isMusicActive = mAudioManager.isMusicActive();
        Slog.d(TAG, "[isMusicActive] isMusicActive: " + isMusicActive);
        return isMusicActive;
    }

    public void setClosingDelayForMusic(boolean isClosingDelay) {
        mIsClosingDelayForMusic = isClosingDelay;
    }

    public boolean getClosingDelayForMusic() {
        return mIsClosingDelayForMusic;
    }

    public void setClosingDelayStartTime(long timeMillis) {
        mClosingDelayStartTime = timeMillis;
    }

    public long getClosingDelayStartTime() {
        return mClosingDelayStartTime;
    }

    public void reset() {
        Slog.d(TAG, "reset");
        mCurrentNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        mIsMobileConnection = false;
        mIsClosingDelayForMusic = false;
        mClosingDelayStartTime = 0;

        setLteUplinkDataTransfer(true, DataShapingServiceImpl.GATE_CLOSE_SAFE_TIMER);
        setLteAccessStratumReport(false);
    }
}
