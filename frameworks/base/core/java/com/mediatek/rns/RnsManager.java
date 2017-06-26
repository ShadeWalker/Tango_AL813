package com.mediatek.rns;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.os.Messenger;
import android.os.RemoteException;

/**
 * Radio Network Selection Manager.
 */
public class RnsManager {
    private static final String TAG = "RnsManager";
    private static final boolean DBG = true;

    public static final int FACTORY_STATE_UNKNOWN = 0;
    public static final int FACTORY_STATE_CONNECTED = 1;
    public static final int FACTORY_STATE_DISCONNECTED = 2;
    public static final int FACTORY_STATE_UNAVAILABLE = 3;

    /**
     * Not allow to connect to any network.
     */
    public static final int ALLOWED_RADIO_NONE = -1;

    /**
     * Allow to connect to Wi-Fi network.
     */
    public static final int ALLOWED_RADIO_WIFI = 0;

    /**
     * Allow to connect to Mobile network.
     */
    public static final int ALLOWED_RADIO_MOBILE = 1;

    /**
     * Not Allow to connect to network due to specific reason.
     */
    public static final int ALLOWED_RADIO_DENY = 2;

    /**
     * Allow to connect to all networks.
     */
    public static final int ALLOWED_RADIO_MAX = 3;

    public static final int STATE_DEFAULT = 0;
    public static final int STATE_ROVEIN = 1;
    public static final int STATE_ROVEOUT = 2;
    public static final int STATE_PENDING = 3;

    public static final String RNS_AGENT_EPDG = "RnsAgentEpdg";
    public static final String RNS_AGENT_TELE = "RnsAgentTele";

    private final IRnsManager mService;


    /**
     * The handover procedure is triggerred to start.
     *
     * @hide
     */
    public static final String CONNECTIVITY_ACTION_HANDOVER_START =
            "android.net.conn.CONNECTIVITY_ACTION_HANDOVER_START";

    /**
     * The handover procedure is finished.
     *
     * @hide
     */
    public static final String CONNECTIVITY_ACTION_HANDOVER_END =
            "android.net.conn.CONNECTIVITY_ACTION_HANDOVER_END";

    /**
     * Network type which triggered a {@link #CONNECTIVITY_ACTION_HANDOVER_START}
     * or {@link #CONNECTIVITY_ACTION_HANDOVER_END} broadcast.
     * Can be used with {@link #getNetworkInfo(int)} to get {@link NetworkInfo}
     * state based on the calling application.
     *
     * @see android.content.Intent#getIntExtra(String, int)
     */
    public static final String EXTRA_NETWORK_TYPE = "networkType";

    /**
     * Handover result which triggered a {@link #CONNECTIVITY_ACTION_HANDOVER_END} broadcast.
     * True value means handover procedure is successfully changed to target RAT.
     * False value means handover procedure is failed changed to target RAT.
     * @see android.content.Intent#getIntExtra(String, int)
     */
    public static final String EXTRA_HANDOVER_RESULT = "handoverResult";

    /**
     * constructor of the manager.
     * @param service
     * {@hide}
     */
    public RnsManager(IRnsManager service) {
        mService = checkNotNull(service, "missing IRnsManager");
    }

    /**
     * get allowed radio to connect.
     * @param capability type of cap. for radio
     * @return selected radio type
     * {@hide}
     */
    public int getAllowedRadioList(int capability) {
        try {
            return mService.getAllowedRadioList(capability);
        } catch (RemoteException e) {
            return -1;
        }
    }

    /**
     * used by CS, if create pdn fail on one network,
     * asking Rns if should try another network.
     * @param failedNetType failed type of the connection
     * @return another type of radio to try
     * {@hide}
     */
    public int getTryAnotherRadioType(int failedNetType) {
        try {
            return mService.getTryAnotherRadioType(failedNetType);
        } catch (RemoteException e) {
            return -1;
        }
    }

    /**
     * get Rns status.
     * @return state of rns service
     * {@hide}
     */
    public int getRnsState() {
        try {
            return mService.getRnsState();
        } catch (RemoteException e) {
            return -1;
        }
    }

    /**
     * Notify RNS about Wi-Fi disable event.
     * @param flag the flag type of disable method.
     * @return the Wi-Fi should be disabled or not.
     * {@hide}
     */
    public boolean isNeedWifiConnected(int flag) {
        try {
            if (mService == null) {
                return false;
            }
            return mService.isNeedWifiConnected(flag);
        } catch (RemoteException e) {
            return false;
        }
    }
}


