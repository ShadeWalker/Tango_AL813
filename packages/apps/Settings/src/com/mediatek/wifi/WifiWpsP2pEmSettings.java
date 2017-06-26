package com.mediatek.wifi;

import static android.net.wifi.WifiConfiguration.INVALID_NETWORK_ID;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.provider.Settings;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

import com.android.settings.R;
import com.mediatek.xlog.Xlog;

public class WifiWpsP2pEmSettings {
    private static final String TAG = "WifiWpsP2pEmSettings";
    // Wifi Wps EM
    private static final int MENU_ID_WPSPIN = Menu.FIRST + 10;
    private static final int MENU_ID_WPSPBC = Menu.FIRST + 11;
    private static final int MENU_ID_DEVPIN = Menu.FIRST + 12;
    private static final int MENU_ID_DEVPBC = Menu.FIRST + 13;
    private static final int WIFI_WPS_PIN_EM_DIALOG_ID = 4;
    private static final int WIFI_WPS_CONFIG_CONFIRM_DIALOG_ID = 5;
    private AlertDialog mWifiWpsEmDialog;
    private boolean mIsOnlyVisibilityWpsPin;
    private WifiManager.WpsCallback mWpsListener;
    private static final String WPS_WIFI_ENABLE = "wps_em_wifi_enable";
    private boolean mIsWifiWpsEmOpen;
    private WifiManager mWifiManager;
    private AccessPoint mSelectedAccessPoint;


    // Wifi P2P EM
    private static final int MENU_ID_WPS_TAG = Menu.FIRST + 2;
    private static final int MENU_ID_P2P_TAG = Menu.FIRST + 3;
    private static final int MENU_ID_OOB_DEVICE = Menu.FIRST + 4;
    private static final int MENU_ID_AUTO_GO_DEVICE = Menu.FIRST + 5;
    private static final int MENU_ID_AUTO_GO = Menu.FIRST + 6;
    private static final String WPS_P2P_ENABLE = "wps_em_p2p_enable";
    private static final String AUTONOMOUS_GO = "autonomous_go";
    private boolean mIsWifiP2pEmOpen;
    private WifiP2pManager mWifiP2pManager;
    private WifiP2pManager.Channel mChannel;

    private Context mContext;
    public static final int WIFI_WPS_EM = 0;
    public static final int WIFI_P2P_EM = 1;
    /* wps = 0, p2p = 1 */
    private int mWpsOrP2p;
    public WifiWpsP2pEmSettings(Context context, WifiP2pManager manager, WifiP2pManager.Channel channel) {
        Xlog.d(TAG, "WifiWpsP2pEmSettings, WifiP2pManager");
        mContext = context;
        mWpsOrP2p = WIFI_P2P_EM;
        mWifiP2pManager = manager;
        mChannel = channel;
    }

    public WifiWpsP2pEmSettings(Context context, WifiManager manager) {
        Xlog.d(TAG, "WifiWpsP2pEmSettings, WifiManager");
        mContext = context;
        mWpsOrP2p = WIFI_WPS_EM;
        mWifiManager = manager;
    }

    public void resume() {
        if (mWpsOrP2p == WIFI_WPS_EM) {
            // Wifi Wps EM
            mIsWifiWpsEmOpen = Settings.System.getInt(mContext.getContentResolver(), WPS_WIFI_ENABLE, 0) == 1;
        } else if (mWpsOrP2p == WIFI_P2P_EM) {
            // Wifi P2P EM
            mIsWifiP2pEmOpen = Settings.System.getInt(mContext.getContentResolver(), WPS_P2P_ENABLE, 0) == 1;
        }
    }

    public void createOptionsMenu(Menu menu) {
        if (mWpsOrP2p == WIFI_WPS_EM && mIsWifiWpsEmOpen) {
            // Wifi Wps EM
            SubMenu nfc = menu.addSubMenu(R.string.wifi_wps_add_device);
            nfc.add(Menu.NONE, MENU_ID_DEVPIN, 0, R.string.wifi_wps_em_reg_pin)
               .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            nfc.add(Menu.NONE, MENU_ID_DEVPBC, 0, R.string.wifi_wps_em_reg_pbc)
               .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        } else if (mWpsOrP2p == WIFI_P2P_EM && mIsWifiP2pEmOpen) {
            // Wifi P2P EM
            SubMenu nfc = menu.addSubMenu(R.string.wifi_p2p_nfc);
            nfc.add(Menu.NONE, MENU_ID_WPS_TAG, 0, R.string.wifi_write_wps_tag)
               .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            nfc.add(Menu.NONE, MENU_ID_P2P_TAG, 0, R.string.wifi_write_p2p_tag)
               .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            nfc.add(Menu.NONE, MENU_ID_OOB_DEVICE, 0, R.string.wifi_p2p_oob)
               .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            nfc.add(Menu.NONE, MENU_ID_AUTO_GO_DEVICE, 0, R.string.wifi_p2p_auto_go_device)
               .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(Menu.NONE, MENU_ID_AUTO_GO, 0, R.string.wifi_p2p_auto_go)
                .setCheckable(true)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
    }

    public void prepareOptionsMenu(Menu menu) {
        // Wifi P2P EM
        if (mWpsOrP2p == WIFI_P2P_EM && mIsWifiP2pEmOpen) {
            MenuItem autoGoMenu = menu.findItem(MENU_ID_AUTO_GO);
            autoGoMenu.setChecked(Settings.System.getInt(mContext.getContentResolver(), AUTONOMOUS_GO, 0) == 1);
        }
    }

    public boolean optionsItemSelected(MenuItem item) {
        if (mWpsOrP2p == WIFI_WPS_EM && mIsWifiWpsEmOpen) {
            switch (item.getItemId()) {
            // Wifi Wps EM
            case MENU_ID_DEVPIN:
                showWifiWpsEmDialog(WIFI_WPS_PIN_EM_DIALOG_ID, null, true);
                return true;
            case MENU_ID_DEVPBC:
                Xlog.d(TAG, "click menu item: WPS Register PBC");
                //showDialog(mSelectedAccessPoint, true);
                mWpsListener = new WifiManager.WpsCallback() {
                    public void onStarted(String pin) {
                    }
                    public void onSucceeded() {
                    }
                    public void onFailed(int reason) {
                    }
                };
                WpsInfo config = new WpsInfo();
                config.setup = config.PBC;
                mWifiManager.startWpsExternalRegistrar(config, mWpsListener);
                return true;

            }

        } else if (mWpsOrP2p == WIFI_P2P_EM && mIsWifiP2pEmOpen) {
            switch (item.getItemId()) {
            // Wifi P2P EM
            case MENU_ID_WPS_TAG:
                Log.d(TAG, "onOptionsItemSelected, MENU_ID_WPS_TAG");
                mWifiP2pManager.stopPeerDiscovery(mChannel, null);
                //mWifiP2pManager.generateNfcConfigurationToken(mChannel,
                mWifiP2pManager.generateNfcToken(mChannel, WifiP2pManager.GET_NFC_CONFIG_TOKEN,
                    new WifiP2pManager.ActionListener() {
                        public void onSuccess() {
                        }
                        public void onFailure(int reason) {
                        }
                    });
                return true;
            case MENU_ID_P2P_TAG:
                Log.d(TAG, "onOptionsItemSelected, MENU_ID_P2P_TAG");
                mWifiP2pManager.stopPeerDiscovery(mChannel, null);
                //mWifiP2pManager.generateNfcSelectToken(mChannel,
                mWifiP2pManager.generateNfcToken(mChannel, WifiP2pManager.GET_NFC_SELECT_TOKEN,
                    new WifiP2pManager.ActionListener() {
                        public void onSuccess() {
                        }
                        public void onFailure(int reason) {
                        }
                    });

                return true;
            case MENU_ID_OOB_DEVICE:
                Log.d(TAG, "onOptionsItemSelected, MENU_ID_OOB_DEVICE");
                mWifiP2pManager.stopPeerDiscovery(mChannel, null);
                //mWifiP2pManager.generateNfcRequestToken(mChannel,
                mWifiP2pManager.generateNfcToken(mChannel, WifiP2pManager.GET_NFC_REQUEST_TOKEN,
                        new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                            }
                            public void onFailure(int reason) {
                            }
                        });

                return true;
            case MENU_ID_AUTO_GO_DEVICE:
                Log.d(TAG, "onOptionsItemSelected, MENU_ID_AUTO_GO_DEVICE");
                mWifiP2pManager.stopPeerDiscovery(mChannel, null);
                //mWifiP2pManager.generateNfcWpsConfigurationToken(mChannel,
                mWifiP2pManager.generateNfcToken(mChannel, WifiP2pManager.GET_NFC_WPS_CONFIG_TOKEN,
                        new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                            }
                            public void onFailure(int reason) {
                            }
                        });

                return true;
            case MENU_ID_AUTO_GO:
                Log.d(TAG, "onOptionsItemSelected, MENU_ID_AUTO_GO");
                Log.d(TAG, "MENU_ID_AUTO_GO isChecked: " + item.isChecked());
                item.setChecked(!item.isChecked());
                Log.d(TAG, "MENU_ID_AUTO_GO isChecked: " + item.isChecked());
                if (item.isChecked()) {
                    Settings.System.putInt(mContext.getContentResolver(), AUTONOMOUS_GO, 1);
                    if (mWifiP2pManager != null) {
                        //mWifiP2pManager. setAutonomousGo(true);
                                mWifiP2pManager.createGroup(mChannel,
                                        new WifiP2pManager.ActionListener() {
                                            public void onSuccess() {
                                            }
                                            public void onFailure(int reason) {
                                            }
                                        });
                    }
                } else {
                    Settings.System.putInt(mContext.getContentResolver(), AUTONOMOUS_GO, 0);
                    if (mWifiP2pManager != null) {
                        //mWifiP2pManager. setAutonomousGo(false);
                                mWifiP2pManager.removeGroup(mChannel,
                                        new WifiP2pManager.ActionListener() {
                                            public void onSuccess() {
                                            }
                                            public void onFailure(int reason) {
                                            }
                                        });
                    }
                }
                return true;

            }

        }

        return false;
    }

    public void createContextMenu(ContextMenu menu, Object selectedAccessPoint) {
        // Wifi Wps EM
        if ((selectedAccessPoint instanceof AccessPoint) && mWpsOrP2p == WIFI_WPS_EM && mIsWifiWpsEmOpen) {
            mSelectedAccessPoint = (AccessPoint) selectedAccessPoint;
            if (mSelectedAccessPoint.wpsAvailable) {
                menu.add(Menu.NONE, MENU_ID_WPSPIN, 0, R.string.wifi_wps_em_reg_pin);
                if (mSelectedAccessPoint.networkId != INVALID_NETWORK_ID) {
                    menu.add(Menu.NONE, MENU_ID_WPSPBC, 0, R.string.wifi_wps_em_reg_pbc);
                }
            }
        }
    }

    public boolean contextItemSelected(MenuItem item) {
        if (mWpsOrP2p == WIFI_WPS_EM && mIsWifiWpsEmOpen) {
            switch (item.getItemId()) {
            case MENU_ID_WPSPIN:
                if (mSelectedAccessPoint.networkId == INVALID_NETWORK_ID) {
                    showWifiWpsEmDialog(WIFI_WPS_CONFIG_CONFIRM_DIALOG_ID, mSelectedAccessPoint, false);

                } else {
                    showWifiWpsEmDialog(WIFI_WPS_PIN_EM_DIALOG_ID, mSelectedAccessPoint, true);
                }
                return true;

            case MENU_ID_WPSPBC:
                Xlog.d(TAG, "click context item: wps_pbc");
                //showDialog(mSelectedAccessPoint, true);
                mWpsListener = new WifiManager.WpsCallback() {
                    public void onStarted(String pin) {
                    }
                    public void onSucceeded() {
                    }
                    public void onFailed(int reason) {
                    }
                };
                WpsInfo config = new WpsInfo();
                config.setup = config.PBC;
                mWifiManager.startWpsExternalRegistrar(config, mWpsListener);
                return true;
            }
        }

        return false;
    }


    // Wifi Wps EM
    private void showWifiWpsEmDialog(int dialogId, AccessPoint accessPoint, boolean isOnlyVisibilityWpsPin) {
        if (mWifiWpsEmDialog != null) {
            ((Activity) mContext).removeDialog(dialogId);
            mWifiWpsEmDialog = null;
        }

        // Save the access point
        mSelectedAccessPoint = accessPoint;
        mIsOnlyVisibilityWpsPin = isOnlyVisibilityWpsPin;

        //showDialog(dialogId); -->createDialog
        mWifiWpsEmDialog = createDialog(dialogId);
        if (mWifiWpsEmDialog != null) {
            mWifiWpsEmDialog.show();
        }
    }

    private AlertDialog createDialog(int dialogId) {
        if (mWpsOrP2p == WIFI_WPS_EM && mIsWifiWpsEmOpen) {
            // Wifi Wps EM
            switch(dialogId) {
            case WIFI_WPS_PIN_EM_DIALOG_ID:
                return new WifiWpsEmDialog(mContext, mSelectedAccessPoint, mIsOnlyVisibilityWpsPin);
            case WIFI_WPS_CONFIG_CONFIRM_DIALOG_ID:
                return new AlertDialog.Builder(mContext)
                .setMessage(R.string.wifi_confirm_config)
                .setCancelable(false)
                .setPositiveButton(R.string.yes,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        showWifiWpsEmDialog(WIFI_WPS_PIN_EM_DIALOG_ID, mSelectedAccessPoint, false);
                    }
                })
                .setNegativeButton(R.string.no,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        showWifiWpsEmDialog(WIFI_WPS_PIN_EM_DIALOG_ID, mSelectedAccessPoint, true);
                    }
                })
                .create();
            }
        }

        return null;

    }


    public void handleP2pStateChanged() {
        // Wifi P2P EM
        Settings.System.putInt(mContext.getContentResolver(), AUTONOMOUS_GO, 0);
    }

    public static AccessPoint build(String apSsid, String apBssid, int apNetworkId, boolean apWpsAvailable) {
        return new AccessPoint(apSsid, apBssid, apNetworkId, apWpsAvailable);
    }

}

class AccessPoint {
    public static final String TAG = "WifiWpsP2pEmSettings.AccessPoint";
    String ssid;
    String bssid;
    int networkId;
    boolean wpsAvailable = false;

    public AccessPoint(String apSsid, String apBssid, int apNetworkId, boolean apWpsAvailable) {
        Xlog.d(TAG, "AccessPoint, ssid = " + apSsid + " ,bssid = " + apBssid
                + " ,networkId = " + apNetworkId + " ,wpsAvailable = " + apWpsAvailable);
        ssid = apSsid;
        bssid = apBssid;
        networkId = apNetworkId;
        wpsAvailable = apWpsAvailable;
    }

}
