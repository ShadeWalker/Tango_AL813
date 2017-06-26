package com.mediatek.wifi;

import static android.net.wifi.WifiConfiguration.INVALID_NETWORK_ID;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig.Eap;
import android.net.wifi.WifiManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import android.telephony.PhoneNumberUtils;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.wifi.WifiEnabler;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.settings.ext.IWifiSettingsExt;
import com.mediatek.settings.FeatureOption;
import com.mediatek.settings.UtilsExt;

import com.mediatek.telephony.TelephonyManagerEx;

public class WifiSettingsExt {
    private static final String TAG = "WifiSettingsExt";
    private static final int MENU_INDEX_WPS_PBC = 0;
    private static final int MENU_INDEX_ADD_NETWORK = 1;
    private static final int MENU_ID_DISCONNECT = Menu.FIRST + 100;

    private static final String WLAN_AP_AND_GPRS = "access_points_and_gprs";
    private static final String TRUST_AP = "trust_access_points";
    private static final String CONFIGED_AP = "configed_access_points";
    private static final String NEW_AP = "new_access_points";

    private ITelephonyEx mTelephonyEx;
    private TelephonyManagerEx mTelephonyManagerEx;
    private TelephonyManager mTelephonyManager;

    //HQ_yulifeng eap-sim HQ01308716 begin
    private String WIFI_AUTO_SSID = "Singtel WiFi";
    private static final String CHT_AUTO_SSID = "CHT Wi-Fi Auto";
    private static final String FET_AUTO_SSID = "FET Wi-Fi Auto";
    static final int SECURITY_EAP = 5; 
    //HQ_yulifeng eap-sim HQ01308716 end
    
    // add for plug in
    IWifiSettingsExt mExt;

    // print performance log
    private boolean mScanResultsAvailable = false;

    // Wifi Wps EM
    WifiWpsP2pEmSettings mWpsP2pEmSettings;

    private Context mActivity;

    public WifiSettingsExt(Context context) {
        mActivity = context;

    }

    public void onCreate() {
        // get plug in
        mExt = UtilsExt.getWifiSettingsPlugin(mActivity);

    }


    public void onActivityCreated(SettingsPreferenceFragment fragment, WifiManager wifiManager) {
        // get telephony manager
        if (FeatureOption.MTK_EAP_SIM_AKA) {
            mTelephonyEx = ITelephonyEx.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
            mTelephonyManagerEx = TelephonyManagerEx.getDefault();
            mTelephonyManager = (TelephonyManager) mActivity.getSystemService(Context.TELEPHONY_SERVICE);
        }

        // register priority observer
        mExt.registerPriorityObserver(mActivity.getContentResolver());

        // Wifi Wps EM
        if (FeatureOption.MTK_WIFIWPSP2P_NFC_SUPPORT) {
            mWpsP2pEmSettings = new WifiWpsP2pEmSettings(mActivity, wifiManager);
        }

        mExt.addCategories(fragment.getPreferenceScreen());

    }

    public void updatePriority() {
        // update priority after connnect AP
        Log.d(TAG, "mConnectListener or mSaveListener");
        mExt.updatePriority();
    }


    public void onResume() {
        // Wifi Wps EM
        if (FeatureOption.MTK_WIFIWPSP2P_NFC_SUPPORT && mWpsP2pEmSettings != null) {
            mWpsP2pEmSettings.resume();
        }

        //update priority when resume
        mExt.updatePriority();
    }

    /**
     * 1. fix menu bug 2. add WPS NFC feature
     * @param setupWizardMode
     * @param wifiIsEnabled
     * @param menu
     */
    public void onCreateOptionsMenu(boolean wifiIsEnabled, Menu menu) {
        // Wifi Wps EM
        if (FeatureOption.MTK_WIFIWPSP2P_NFC_SUPPORT && mWpsP2pEmSettings != null) {
            mWpsP2pEmSettings.createOptionsMenu(menu);
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (FeatureOption.MTK_WIFIWPSP2P_NFC_SUPPORT && mWpsP2pEmSettings != null) {
            return mWpsP2pEmSettings.optionsItemSelected(item);
        }
        return false;
    }

    /**
     * 1. add cmcc disconnect menu 2. WPS NFC feature
     * @param menu
     * @param state
     * @param accessPoint
     */
    public void onCreateContextMenu(ContextMenu menu, DetailedState state, AccessPoint accessPoint) {
        //current connected AP, add a disconnect option to it
        mExt.updateContextMenu(menu, MENU_ID_DISCONNECT, state);

        // Wifi Wps EM
        if (FeatureOption.MTK_WIFIWPSP2P_NFC_SUPPORT && mWpsP2pEmSettings != null) {
            mWpsP2pEmSettings.createContextMenu(menu, accessPoint);
        }
    }

    public boolean onContextItemSelected(MenuItem item, int networkId) {
        switch (item.getItemId()) {
            case MENU_ID_DISCONNECT:
                mExt.disconnect(networkId);
                return true;
             default:
                    break;
        }

        //Wifi Wps EM
        if (FeatureOption.MTK_WIFIWPSP2P_NFC_SUPPORT && mWpsP2pEmSettings != null) {
            return mWpsP2pEmSettings.contextItemSelected(item);
        }

        return false;
    }

    public static AccessPoint build(String apSsid, String apBssid, int apNetworkId, boolean apWpsAvailable) {
        return WifiWpsP2pEmSettings.build(apSsid, apBssid, apNetworkId, apWpsAvailable);
    }

    public void recordPriority(WifiConfiguration config) {
        // record priority of selected ap
        if (config != null) {
            //store the former priority value before user modification
            mExt.recordPriority(config.priority);
        } else {
            //the last added AP will have highest priority, mean all other AP's priority will be adjusted,
            //the same as adjust this new added one's priority from lowest to highest
            mExt.recordPriority(-1);
        }

    }


    public void submit(WifiConfiguration config, Preference accessPoint, int networkId, DetailedState state) {
        Log.d(TAG, "submit, config = " + config);
        if (config == null) {
            /*if (accessPoint != null && networkId != INVALID_NETWORK_ID && state != null) {
                Log.d(TAG, "submit, disconnect, networkId = " + networkId);
                mExt.disconnect(networkId);
            }*/
            Log.d(TAG, "submit, networkId = " + networkId);
        } else if (config.networkId != INVALID_NETWORK_ID && accessPoint != null) {
            // save priority
            Log.d(TAG, "submit, setNewPriority");
            mExt.setNewPriority(config);
        } else {
            // update priority
            Log.d(TAG, "submit, updatePriorityAfterSubmit");
            mExt.updatePriorityAfterSubmit(config);
        }
        // set last connected config
        Log.d(TAG, "submit, setLastConnectedConfig");
        mExt.setLastConnectedConfig(config);

    }

    public int getAccessPointsCount(PreferenceScreen screen) {
        return mExt.getAccessPointsCount(screen);

    }

    public void unregisterPriorityObserver(ContentResolver cr) {
        mExt.unregisterPriorityObserver(cr);
    }

    /**
     *  Add for EAP-SIM
     * @param config The current AP's configuration
     * @return
     */
    public boolean hasChangedSimCard(WifiConfiguration config, WifiManager wifiManager, String ssid, int security) {
        boolean result = false;
        String pMccMnc = PhoneNumberUtils.getSimMccMnc(0); //add by yulifeng
        String pMccMnc2 = PhoneNumberUtils.getSimMccMnc(1);
        if (FeatureOption.MTK_EAP_SIM_AKA && config.imsi != null && !config.imsi.equals("\"none\"")) {
            Log.d(TAG,"config = " + config.toString());
            int slot = 0;
            //Add for gemini+
            String[] simslots = config.simSlot.split("\"");
            if (simslots.length > 1) {
                slot = Integer.parseInt(simslots[1]);
            }

            String imsiStr = null;
            int eapMethod = config.enterpriseConfig.getEapMethod();
            int subId = WifiUtils.getSubId(slot);
            /*HQ_yulifeng eap-sim HQ01308716 begin*/
            if(slot==0){
                pMccMnc  = mTelephonyManager.getSimOperator(subId);
            }else if(slot==1){
                pMccMnc2 = mTelephonyManager.getSimOperator(subId);
            }
            /*HQ_yulifeng eap-sim HQ01308716 end*/
            if(subId == WifiUtils.GET_SUBID_NULL_ERROR) 
            	return false;
            if (eapMethod == Eap.SIM) {
                imsiStr = WifiConfigControllerExt.makeNAI(mTelephonyManager.getSimOperator(subId),
                    mTelephonyManager.getSubscriberId(subId), "SIM");
            } else if (eapMethod == Eap.AKA) {
                imsiStr = WifiConfigControllerExt.makeNAI(mTelephonyManager.getSimOperator(subId),
                    mTelephonyManager.getSubscriberId(subId), "AKA");
            }
            Log.d(TAG,"makeNAI() = " + imsiStr);
            Log.d(TAG,"mTelephonyManager.getSubscriberId(subId) " + mTelephonyManager.getSubscriberId(subId));
            Log.d(TAG,"makeNAI() = " + imsiStr);

            if ((config.imsi).equals(imsiStr)) {
                Log.d(TAG,"user doesn't change or remove sim card");
            } else {
                if (!mExt.isTustAP(ssid, security)) {
                    Log.d(TAG,"user change or remove sim card");
                    boolean s = wifiManager.removeNetwork(config.networkId);
                    Log.d(TAG,"removeNetwork: " + s);
                    s = wifiManager.saveConfiguration();
                    Log.d(TAG,"saveNetworks(): " + s);
                    result = true;
                }
            }
          }
		
        /*HQ_yulifeng eap-sim HQ01308716 begin*/
        /*Log.d("ylf","[WifiSettingsExt]pMccMnc"+pMccMnc);
        Log.d("ylf","[WifiSettingsExt]pMccMnc2"+pMccMnc2);		  
        Log.d("ylf","[WifiSettingsExt]_hasChangedSimCard");
          if((!"52501".equals(pMccMnc)) && (!"52501".equals(pMccMnc2))){ 
              if((!"52502".equals(pMccMnc)) && (!"52502".equals(pMccMnc2))){
                  if((!"46692".equals(pMccMnc)) && (!"46692".equals(pMccMnc2))){
                      if((!"46601".equals(pMccMnc)) && (!"46601".equals(pMccMnc2))){
                          if((WIFI_AUTO_SSID.equals(ssid)||CHT_AUTO_SSID.equals(ssid)||FET_AUTO_SSID.equals(ssid)) && SECURITY_EAP == security){
                              Log.d(TAG,"remove config ssid !");
                              result = true;
                          }
                      }
                  }
              }
          }*/
          Log.d(TAG,"ssid = " + ssid + " security = " + security + " result = " + result);
          /*HQ_yulifeng eap-sim HQ01308716 end*/
          return result;
   }
    
    /**
     * Add for EAP-SIM
     * @param wifiConfig The current AP's configuration
     * @return
     */
    public boolean hasSimAkaProblem(WifiConfiguration config, ContentResolver cr) {
        // if wifiConfig is null, indicate User connect wifi by click "Connect" from Context Menu
        Log.d(TAG,"hasSimAkaProblem, config = " + config);
        if (config != null && (config.enterpriseConfig.getEapMethod() == Eap.SIM
                                         || config.enterpriseConfig.getEapMethod() == Eap.AKA)) {
            // cannot use eap-sim/aka under airplane mode
            if (Settings.System.getInt(cr, Settings.System.AIRPLANE_MODE_ON, 0) == 1) {
                Toast.makeText(mActivity, R.string.eap_sim_aka_airplanemode, Toast.LENGTH_LONG).show();
                return true;
            }
            // cannot use eap-sim/aka without a sim card
            if (config.imsi != null && config.imsi.equals("\"error\"")) {
                Toast.makeText(mActivity, R.string.eap_sim_aka_no_sim_error, Toast.LENGTH_LONG).show();
                return true;
            }

            // cannot use eap-sim/aka if user doesn't select a sim slot
            if ((FeatureOption.MTK_GEMINI_SUPPORT) && (("\"none\"").equals(config.imsi))) {
                Toast.makeText(mActivity, R.string.eap_sim_aka_no_sim_slot_selected,Toast.LENGTH_LONG).show();
                return true;
            }
        }
        return false;
    }
    
    public void setLastPriority(int priority) {
        mExt.setLastPriority(priority);
    }

    public void addPreference(PreferenceScreen screen, Preference preference, boolean isConfiged) {
        mExt.addPreference(screen, preference, isConfiged);
    }

    public Preference getPreference(PreferenceScreen preferenceScreen, int index) {
        return mExt.getPreference(preferenceScreen, index);
    }

    public void emptyCategory(PreferenceScreen screen) {
        mExt.emptyCategory(screen);
    }

    public void emptyScreen(PreferenceScreen screen) {
        mExt.emptyScreen(screen);
    }

    public void refreshCategory(PreferenceScreen screen) {
        mExt.refreshCategory(screen);
    }


}
