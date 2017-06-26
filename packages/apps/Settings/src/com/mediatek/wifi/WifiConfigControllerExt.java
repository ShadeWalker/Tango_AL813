package com.mediatek.wifi;

import static android.net.wifi.WifiConfiguration.INVALID_NETWORK_ID;

import android.app.AlertDialog;
import android.content.Context;
import android.net.StaticIpConfiguration;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.GroupCipher;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiConfiguration.PairwiseCipher;
import android.net.wifi.WifiConfiguration.Protocol;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.security.Credentials;
import android.security.KeyStore;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.internal.telephony.PhoneConstants;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.android.settings.R;
import com.android.settings.wifi.WifiConfigController;
import com.android.settings.wifi.WifiConfigUiBase;
//import com.mediatek.common.telephony.ITelephonyEx;
import com.mediatek.settings.ext.IWifiExt;
import com.mediatek.settings.FeatureOption;
import com.mediatek.settings.UtilsExt;
import com.mediatek.telephony.TelephonyManagerEx;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class WifiConfigControllerExt {
    private static final String TAG = "WifiConfigControllerExt";

    //add transmit key spinner
    private Spinner mWEPKeyIndex;
    private Spinner mWEPKeyType;
    //sim/aka
    public static final int WIFI_EAP_METHOD_SIM = 4;
    public static final int WIFI_EAP_METHOD_AKA = 5;
    //fast
    public static final int WIFI_EAP_METHOD_FAST = 6;
    private static final int BUFFER_LENGTH = 40;
    private static final int MNC_SUB_BEG = 3;
    private static final int MNC_SUB_END = 5;
    private static final int MCC_SUB_BEG = 0;
    private static final int MCC_MNC_LENGTH = 5;

    //add for EAP_SIM/AKA
    private Spinner mSimSlot;
    private TelephonyManager mTelephonyManager;
    private ITelephonyEx mTelephonyEx;
    private TelephonyManagerEx mTelephonyManagerEx;

    // add for WAPI
    private Spinner mWapiAsCert;
    private Spinner mWapiClientCert;
    private boolean mHex;
    private static final String WLAN_PROP_KEY = "persist.sys.wlan";
    private static final String WIFI = "wifi";
    private static final String WAPI = "wapi";
    private static final String WIFI_WAPI = "wifi-wapi";
    private static final String DEFAULT_WLAN_PROP = WIFI_WAPI;
    // add for DHCPV6
    private static final int IPV4_ADDRESS_LENGTH = 4;
    private static final int IPV6_ADDRESS_LENGTH = 16;
    // add for plug in
    private IWifiExt mExt;

    private Context mContext;
    private View mView;
    private WifiConfigUiBase mConfigUi;
    private WifiConfigController mController;

    public WifiConfigControllerExt(WifiConfigController controller, WifiConfigUiBase configUi, View view) {
        mController = controller;
        mConfigUi = configUi;
        mContext = mConfigUi.getContext();
        mView = view;
        mExt = UtilsExt.getWifiPlugin(mContext);
        // get telephonyManager and telephonyManagerEx
        if (FeatureOption.MTK_EAP_SIM_AKA) {
            mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            mTelephonyEx = ITelephonyEx.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
            mTelephonyManagerEx = TelephonyManagerEx.getDefault();
        }

    }

    public boolean getSetDisconnectButton() {
        return /*mExt.shouldSetDisconnectButton()*/false;
    }

    public void addWifiConfigView(boolean edit) {

        if (mController.getAccessPoint() == null) {
            //set security text
            TextView securityText = (TextView) mView.findViewById(R.id.security_text);
            mExt.setSecurityText(securityText);

            // set array for wifi security
            int viewId = R.id.security;
            if (FeatureOption.MTK_WAPI_SUPPORT) {
                  String type = SystemProperties.get(WLAN_PROP_KEY,
                              DEFAULT_WLAN_PROP);
                  if (type.equals(WIFI_WAPI)) {
                        if (AccessPointExt.isWFATestSupported()) {
                              viewId = R.id.security_wfa; // WIFI + WAPI, support
                              // separate WPA2 PSK
                              // security
                        } else {
                              viewId = R.id.security; // WIFI + WAPI
                        }
                  } else if (type.equals(WIFI)) {
                        if (AccessPointExt.isWFATestSupported()) {
                              viewId = R.id.wpa_security_wfa; // WIFI only, support
                              // separate WPA2 PSK
                              // security
                        } else {
                              viewId = R.id.wpa_security; // WIFI only
                        }
                  } else if (type.equals(WAPI)) {
                        viewId = R.id.wapi_security; // WAPI only
                  }
            } else {
                  if (AccessPointExt.isWFATestSupported()) {
                        viewId = R.id.wpa_security_wfa; // WIFI only, support
                        // separate WPA and WPA2 PSK
                        // security
                  } else {
                        viewId = R.id.wpa_security; // WIFI only
                  }
            }
            switchWlanSecuritySpinner((Spinner) mView.findViewById(viewId));
        } else {
            int networkId = mController.getAccessPointNetworkId();
            WifiConfiguration config = mController.getAccessPointConfig();
            Log.d(TAG, "addWifiConfigView, networkId = " + networkId);
            Log.d(TAG, "addWifiConfigView, config = " + config);
            // get plug in,whether to show access point priority select
            // spinner.
            mExt.setAPNetworkId(networkId);
            if (networkId != -1 && config != null) {
                  Log.d(TAG, "priority=" + config.priority);
                  mExt.setAPPriority(config.priority);
            }
            LinearLayout priorityLayout = (LinearLayout) mView
                        .findViewById(R.id.priority_field);
            View priorityView = mExt.getPriorityView();
            if (priorityView != null) {
                  priorityLayout.addView(priorityView, new LinearLayout.LayoutParams(
                              LinearLayout.LayoutParams.MATCH_PARENT,
                              LinearLayout.LayoutParams.WRAP_CONTENT));
            }

            if (networkId != INVALID_NETWORK_ID) {
                if (!edit) {
                    priorityLayout.setVisibility(View.GONE);
                }
            }

        }
        mExt.addDisconnectButton((AlertDialog) mConfigUi, edit, mController.getAccessPointState(),
                mController.getAccessPointNetworkId());

        //for CMCC-AUTO ignore some config information
        if (mController.getAccessPoint() != null && mView != null) {
                mExt.hideWifiConfigInfo(new IWifiExt.Builder()
                            .setSsid(mController.getAccessPointSsid())
                            .setSecurity(mController.getAccessPointSecurity())
                            .setNetworkId(mController.getAccessPointNetworkId())
                            .setEdit(edit)
                            .setViews(mView), mConfigUi.getContext());
        }
    }

    public void addViews(WifiConfigUiBase configUi, String security) {
        ViewGroup group = (ViewGroup) mView.findViewById(R.id.info);
        //add security information
        View row = configUi.getLayoutInflater().inflate(
                    R.layout.wifi_dialog_row, group, false);
        ((TextView) row.findViewById(R.id.name)).setText(mExt
                    .getSecurityText(configUi.getContext().getString(R.string.wifi_security)));
        ((TextView) row.findViewById(R.id.value)).setText(security);
        group.addView(row);
    }

    /**
     *switch WLAN security spinner
     */
    private void switchWlanSecuritySpinner(Spinner securitySpinner) {
          ((Spinner) mView.findViewById(R.id.security)).setVisibility(View.GONE);
          ((Spinner) mView.findViewById(R.id.wapi_security))
                      .setVisibility(View.GONE);
          ((Spinner) mView.findViewById(R.id.wpa_security))
                      .setVisibility(View.GONE);
          ((Spinner) mView.findViewById(R.id.security_wfa))
                      .setVisibility(View.GONE);
          ((Spinner) mView.findViewById(R.id.wpa_security_wfa))
                      .setVisibility(View.GONE);

          securitySpinner.setVisibility(View.VISIBLE);
          securitySpinner.setOnItemSelectedListener(mController);
    }

    /**
     *make NAI
     * @param simOperator mnc+mcc
     * @param imsi eapMethod
     * @return the string of NAI
     */
    public static String makeNAI(String simOperator, String imsi, String eapMethod) {

          // airplane mode & select wrong sim slot
          if (imsi == null) {
                return addQuote("error");
          }

          StringBuffer NAI = new StringBuffer(BUFFER_LENGTH);
          // s = sb.append("a = ").append(a).append("!").toString();
          System.out.println("".length());

          if (eapMethod.equals("SIM")) {
                NAI.append("1");
          } else if (eapMethod.equals("AKA")) {
                NAI.append("0");
          }

          // add imsi
          NAI.append(imsi);
          NAI.append("@wlan.mnc");
          // add mnc
          // for some operator
          Log.i(TAG, "simOperator = " + simOperator);
          if (simOperator.length() == MCC_MNC_LENGTH) {
              NAI.append("0");
              NAI.append(imsi.substring(MNC_SUB_BEG, MNC_SUB_END));
          } else {
              NAI.append(imsi.substring(MNC_SUB_BEG, MNC_SUB_END + 1));
          }
          NAI.append(".mcc");
          // add mcc
          NAI.append(imsi.substring(MCC_SUB_BEG, MNC_SUB_BEG));

          // NAI.append(imsi.substring(5));
          NAI.append(".3gppnetwork.org");
          Log.d(TAG, NAI.toString());
          Log.d(TAG, "\"" + NAI.toString() + "\"");
          return addQuote(NAI.toString());
    }

    /**
     *add quote for strings
     * @param string
     * @return add quote to the string
     */
    public static String addQuote(String s) {
          return "\"" + s + "\"";
    }

    public boolean enableSubmitIfAppropriate(TextView passwordView, int accessPointSecurity, boolean pwInvalid) {
        boolean passwordInvalid = pwInvalid;

        if (passwordView != null
                && ((accessPointSecurity == AccessPointExt.SECURITY_WEP && !isWEPKeyValid(passwordView.getText().toString()))
                           || ((accessPointSecurity == AccessPointExt.SECURITY_PSK
                               || accessPointSecurity == AccessPointExt.SECURITY_WPA_PSK
                               || accessPointSecurity == AccessPointExt.SECURITY_WPA2_PSK) && passwordView.length() < 8)
                           || (accessPointSecurity == AccessPointExt.SECURITY_WAPI_PSK && (passwordView.length() < 8
                           || 64 < passwordView.length() || (mHex && !passwordView
                                   .getText().toString().matches("[0-9A-Fa-f]*")))))) {
                 passwordInvalid = true;
           }

        //verify WAPI information
        if (accessPointSecurity == AccessPointExt.SECURITY_WAPI_CERT
                    && (mWapiAsCert != null
                                && mWapiAsCert.getSelectedItemPosition() == 0 || mWapiClientCert != null
                                && mWapiClientCert.getSelectedItemPosition() == 0)) {
              passwordInvalid = true;
        }

        return passwordInvalid;

    }
    /**
     * verify password check whether we have got a valid WEP key
     *
     * @param password
     * @return
     */
    private boolean isWEPKeyValid(String password) {
          if (password == null || password.length() == 0) {
                return false;
          }
          int keyType = 0; // password: auto, ASCII or Hex
          if (mWEPKeyType != null
                      && mWEPKeyType.getSelectedItemPosition() != AdapterView.INVALID_POSITION) {
                keyType = mWEPKeyType.getSelectedItemPosition();
          }
          int keyLength = password.length();
          if ((keyLength == 10 || keyLength == 26 || keyLength == 32)
                      && password.matches("[0-9A-Fa-f]*")
                      && (keyType == 0 || keyType == 2)) {
                return true;
          } else if ((keyLength == 5 || keyLength == 13 || keyLength == 16)
                      && (keyType == 0 || keyType == 1)) {
                return true;
          }
          return false;
    }

    public void setConfig(WifiConfiguration config, int accessPointSecurity, TextView passwordView,
            Spinner eapMethodSpinner) {
        // init eap information
        if (FeatureOption.MTK_EAP_SIM_AKA) {
              config.imsi = addQuote("none");
              config.simSlot = addQuote("-1");
              config.pcsc = addQuote("none");
        }

        //get priority of configuration
        if (mExt.getPriority() >= 0) {
              config.priority = mExt.getPriority();
        }

        switch (accessPointSecurity) {
            case AccessPointExt.SECURITY_WEP:
                if (passwordView.length() != 0) {
                    int length = passwordView.length();
                    String password = passwordView.getText().toString();
                    // get selected WEP key index
                    int keyIndex = 0; // selected password index, 0~3
                    if (mWEPKeyIndex != null
                                && mWEPKeyIndex.getSelectedItemPosition() != AdapterView.INVALID_POSITION) {
                          keyIndex = mWEPKeyIndex.getSelectedItemPosition();
                    }
                    // WEP-40, WEP-104, and 256-bit WEP (WEP-232?)
                    if ((length == 10 || length == 26 || length == 32)
                                && password.matches("[0-9A-Fa-f]*")) {
                          //hex password
                          config.wepKeys[keyIndex] = password;
                    } else {
                          //ASCII password
                          config.wepKeys[keyIndex] = '"' + password + '"';
                    }
                    // set wep index to configuration
                    config.wepTxKeyIndex = keyIndex;
              }
                break;
            case AccessPointExt.SECURITY_WPA_PSK:
            case AccessPointExt.SECURITY_WPA2_PSK:
                  config.allowedKeyManagement.set(KeyMgmt.WPA_PSK);
                  if (passwordView.length() != 0) {
                        String password = passwordView.getText().toString();
                        if (password.matches("[0-9A-Fa-f]{64}")) {
                              config.preSharedKey = password;
                        } else {
                              config.preSharedKey = '"' + password + '"';
                        }
                  }
                  break;
            case AccessPointExt.SECURITY_EAP:
                if ("AKA".equals((String) eapMethodSpinner.getSelectedItem())
                        || "SIM".equals((String) eapMethodSpinner.getSelectedItem())) {
                    eapSimAkaConfig(config, eapMethodSpinner);
                    Log.d(TAG, "eap-sim/aka, config.toString(): " + config.toString());
                }
                break;

                // add WAPI_PSK & WAPI_CERT
            case AccessPointExt.SECURITY_WAPI_PSK:
                  config.allowedKeyManagement.set(KeyMgmt.WAPI_PSK);
                  config.allowedProtocols.set(Protocol.WAPI);
                  config.allowedPairwiseCiphers.set(PairwiseCipher.SMS4);
                  config.allowedGroupCiphers.set(GroupCipher.SMS4);
                  if (passwordView.length() != 0) {
                        String password = passwordView.getText().toString();
                        Log.v(TAG, "getConfig(), mHex=" + mHex);
                        if (mHex) { /* Hexadecimal */
                              config.preSharedKey = password;
                        } else { /* ASCII */
                              config.preSharedKey = '"' + password + '"';
                        }
                  }
                  break;

            case AccessPointExt.SECURITY_WAPI_CERT:
                  config.allowedKeyManagement.set(KeyMgmt.WAPI_CERT);
                  config.allowedProtocols.set(Protocol.WAPI);
                  config.allowedPairwiseCiphers.set(PairwiseCipher.SMS4);
                  config.allowedGroupCiphers.set(GroupCipher.SMS4);
                  config.enterpriseConfig.setCaCertificateWapiAlias((mWapiAsCert.getSelectedItemPosition() == 0) ? ""
                                          : (String) mWapiAsCert.getSelectedItem());
                  config.enterpriseConfig.setClientCertificateWapiAlias((mWapiClientCert.getSelectedItemPosition() == 0) ? ""
                                          : (String) mWapiClientCert.getSelectedItem());
                  break;
            default:
                  break;

        }

    }


    /**
     * Geminu plus
     */
     private void eapSimAkaConfig(WifiConfiguration config, Spinner eapMethodSpinner) {
       if (mSimSlot == null) {
           Log.d(TAG, "mSimSlot is null");
           mSimSlot = (Spinner) mView.findViewById(R.id.sim_slot);
       }
       String strSimAka = (String) eapMethodSpinner.getSelectedItem();
       if (FeatureOption.MTK_EAP_SIM_AKA) {
           if (FeatureOption.MTK_GEMINI_SUPPORT) {
               Log.d(TAG, "((String) mSimSlot.getSelectedItem()) " + ((String) mSimSlot.getSelectedItem()));
               //Log.d(TAG, "R.string.eap_sim_slot_0 " + context.getString(R.string.eap_sim_slot_0));
               simSlotConfig(config, strSimAka);
               Log.d(TAG, "eap-sim, choose sim_slot" + (String) mSimSlot.getSelectedItem());
           } else {
               config.imsi = makeNAI(mTelephonyManager.getSimOperator(), mTelephonyManager.getSubscriberId(), strSimAka);
               Log.d(TAG, "config.imsi: " + config.imsi);
               config.simSlot = addQuote("0");
               config.pcsc = addQuote("rild");
           }
           Log.d(TAG, "eap-sim, config.imsi: " + config.imsi);
           Log.d(TAG, "eap-sim, config.simSlot: " + config.simSlot);
       }
   }
     
   /**
    *  Geminu plus
    */
   private void simSlotConfig(WifiConfiguration config, String strSimAka) {
       int simSlot = mSimSlot.getSelectedItemPosition()-1;
       if (simSlot > -1) {
           int subId = WifiUtils.getSubId(simSlot);
           config.imsi = makeNAI(mTelephonyManager.getSimOperator(subId),
            	mTelephonyManager.getSubscriberId(subId), strSimAka);
           Log.d(TAG, "config.imsi: " + config.imsi);
           config.simSlot = addQuote("" + simSlot);
           Log.d(TAG, "config.simSlot " + addQuote("" + simSlot));
           config.pcsc = addQuote("rild");
           Log.d(TAG, "config.pcsc: " + addQuote("rild"));
       } 
   }
   
   //M:added by Parish Li for setting Spinner adapter triggers onItemSelected() everytime 
   public void setEapmethodSpinnerAdapter() {
	   Spinner eapMethodSpinner = (Spinner) mView.findViewById(R.id.method);
       
       //set array for eap method spinner. show simslot in gemini
       // load
        Log.d(TAG, "showSecurityFields, FeatureOption.MTK_EAP_SIM_AKA =  " + FeatureOption.MTK_EAP_SIM_AKA);
        Log.d(TAG, "showSecurityFields, FeatureOption.MTK_TC1_FEATURE =  " + FeatureOption.MTK_TC1_FEATURE);
        if (FeatureOption.MTK_EAP_SIM_AKA || FeatureOption.MTK_TC1_FEATURE) {
              int spinnerId = R.array.wifi_eap_method;
              if (FeatureOption.MTK_EAP_SIM_AKA && FeatureOption.MTK_TC1_FEATURE) {
                  spinnerId = R.array.wifi_eap_method_fast_sim_aka;
              } else if (FeatureOption.MTK_EAP_SIM_AKA) {
                  spinnerId = R.array.wifi_eap_method_sim_aka;
              } else if (FeatureOption.MTK_TC1_FEATURE) {
                  spinnerId = R.array.wifi_eap_method_fast;
              }
             Context context = mConfigUi.getContext();
             String[] eapString = context.getResources().getStringArray(spinnerId);
             ArrayList<String> eapList = new ArrayList<String>(Arrays.asList(eapString));
             final ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                       context, android.R.layout.simple_spinner_item, eapList);
             if (mController.getAccessPoint() != null) {
                 mExt.setEapMethodArray(
                     adapter,
                     mController.getAccessPointSsid(),
                     mController.getAccessPointSecurity());
             }
             adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
             //M:added by Parish Li for triggering onItemSelected
             eapMethodSpinner.setAdapter(adapter);
       }
   }
   
   //M:added by Parish Li for hidding the fields when eap-fast,sim and aka
   public void setEapMethodFields(boolean edit) {
	   Spinner eapMethodSpinner = (Spinner) mView.findViewById(R.id.method);
	   
	   // eap-sim/aka
       int eapMethod = convertEapMethod(eapMethodSpinner.getSelectedItemPosition(), 1);
       //for CMCC-AUTO eap Method config information
       if (mController.getAccessPoint() != null) {
           eapMethod = mExt.getEapMethodbySpinnerPos(
               eapMethod,
               mController.getAccessPointSsid(),
               mController.getAccessPointSecurity());
       }
       Log.d(TAG, "showSecurityFields modify method = " + eapMethod);
       
       if (eapMethod >= WIFI_EAP_METHOD_SIM) {
           mView.findViewById(R.id.l_phase2).setVisibility(View.GONE);
           mView.findViewById(R.id.l_ca_cert).setVisibility(View.GONE);
           mView.findViewById(R.id.l_user_cert).setVisibility(View.GONE);
           mView.findViewById(R.id.l_anonymous).setVisibility(View.GONE);
       }

       if (mController.getAccessPoint() != null && mView != null) {
           mExt.hideWifiConfigInfo(new IWifiExt.Builder()
                           .setSsid(mController.getAccessPointSsid())
                           .setSecurity(mController.getAccessPointSecurity())
                           .setNetworkId(mController.getAccessPointNetworkId())
                           .setEdit(edit)
                           .setViews(mView), mConfigUi.getContext());
       }
   }
   
   //M:added by Parish Li
   public void setGEMINI() {
       Spinner eapMethodSpinner = (Spinner) mView.findViewById(R.id.method);
       TextView eapAnonymousView = (TextView) mView.findViewById(R.id.anonymous);
       TextView passwordView  = (TextView) mView.findViewById(R.id.password);
       TextView eapIdentityView = (TextView) mView.findViewById(R.id.identity);
       
       int eapMethod = convertEapMethod(eapMethodSpinner.getSelectedItemPosition(), 1);
       //for CMCC-AUTO eap Method config information
       if (mController.getAccessPoint() != null) {
           eapMethod = mExt.getEapMethodbySpinnerPos(
               eapMethod,
               mController.getAccessPointSsid(),
               mController.getAccessPointSecurity());
       }

       if (eapMethod == WIFI_EAP_METHOD_SIM
               || eapMethod == WIFI_EAP_METHOD_AKA) {
           eapIdentityView.setEnabled(false);
           passwordView.setEnabled(false);
           ((CheckBox) mView.findViewById(R.id.show_password))
                     .setEnabled(false);
           if (FeatureOption.MTK_GEMINI_SUPPORT) {
               mView.findViewById(R.id.sim_slot_fields).setVisibility(View.VISIBLE);
               mSimSlot = (Spinner) mView.findViewById(R.id.sim_slot);
               //Geminu plus
               Context context = mConfigUi.getContext();
               String[] tempSimAkaMethods = context.getResources().getStringArray(R.array.sim_slot);
               //L:MARK NEED CONFIRM         int sum = PhoneConstants.GEMINI_SIM_NUM + 1;
               int sum = mTelephonyManager.getSimCount();
               Log.d(TAG, "the num of sim slot is :" + sum);
               String[] simAkaMethods = new String[sum+1];
               for (int i = 0; i < (sum+1); i++) {
                   if (i < tempSimAkaMethods.length) {
                       simAkaMethods[i] = tempSimAkaMethods[i];
                   } else {
                       simAkaMethods[i] = tempSimAkaMethods[1].replaceAll("1", "" + i);
                   }
                }
               final ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, 
                       android.R.layout.simple_spinner_item,simAkaMethods);
               adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
               mSimSlot.setAdapter(adapter);

               //setting had selected simslot
               if (mController.getAccessPoint() != null
                       && mController.getAccessPointNetworkId() != INVALID_NETWORK_ID) {
                   WifiConfiguration config = mController.getAccessPointConfig();
                   if (config != null && config.simSlot != null) {
                	   String[] simslots = config.simSlot.split("\"");
                       if (simslots.length > 1) {
                    	   int slot = Integer.parseInt(simslots[1]) + 1;
                    	   mSimSlot.setSelection(slot);
                       }
                   }  
               }
           }
       } else {
    	   eapIdentityView.setEnabled(true);
    	   passwordView.setEnabled(true);
    	   ((CheckBox) mView.findViewById(R.id.show_password))
                     .setEnabled(true);
    	   if (FeatureOption.MTK_GEMINI_SUPPORT) {
               mView.findViewById(R.id.sim_slot_fields).setVisibility(
                           View.GONE);
    	   }
       }
   
       //eap method changed, and current eap method not equals config's eap method@{
       if (mController.getAccessPoint() != null && mController.getAccessPointNetworkId() != INVALID_NETWORK_ID
           && eapMethodSpinner != null && eapAnonymousView != null) {
    	   ArrayAdapter<String> adapter = (ArrayAdapter<String>) eapMethodSpinner.getAdapter();
    	   WifiConfiguration config = mController.getAccessPointConfig();
    	   int i = convertEapMethod(eapMethodSpinner.getSelectedItemPosition(), 1);
    	   if (mController.getAccessPoint() != null) {
    		   i = mExt.getEapMethodbySpinnerPos(i, mController.getAccessPointSsid(), mController.getAccessPointSecurity());
    	   }
    	   Log.d(TAG, "showSecurityFields set Anonymous to null method = " + i);
    	   if (config.enterpriseConfig != null
               && adapter != null && !(config.enterpriseConfig.getEapMethod() == i)) {
    		   eapAnonymousView.setText(null);
    	   }
       }
   }
   
   public boolean showSecurityFields(int accessPointSecurity, boolean edit) {
       Log.d(TAG, "showSecurityFields, accessPointSecurity = " + accessPointSecurity);
       Log.d(TAG, "showSecurityFields, edit = " + edit);
       Spinner eapMethodSpinner = (Spinner) mView.findViewById(R.id.method);
       TextView eapAnonymousView = (TextView) mView.findViewById(R.id.anonymous);
       TextView passwordView  = (TextView) mView.findViewById(R.id.password);
       TextView eapIdentityView = (TextView) mView.findViewById(R.id.identity);

       if (accessPointSecurity != AccessPointExt.SECURITY_EAP) {
           ((TextView) mView.findViewById(R.id.identity)).setEnabled(true);
           ((CheckBox) mView.findViewById(R.id.show_password)).setEnabled(true);
           //hide eap fileds
           mView.findViewById(R.id.eap).setVisibility(View.GONE);
           mView.findViewById(R.id.eap_identity).setVisibility(View.GONE);
       }

       // Hexadecimal checkbox only for WAPI_PSK
       mView.findViewById(R.id.hex_password).setVisibility(View.GONE);
       if (accessPointSecurity == AccessPointExt.SECURITY_WAPI_PSK) {
             mView.findViewById(R.id.hex_password).setVisibility(View.VISIBLE);
             ((CheckBox) mView.findViewById(R.id.hex_password)).setChecked(mHex);
       }

       // WEP transmit key & keytype
       if (accessPointSecurity == AccessPointExt.SECURITY_WEP
                   && FeatureOption.WIFI_WEP_KEY_ID_SET) {
             mView.findViewById(R.id.wep).setVisibility(View.VISIBLE);
             mWEPKeyType = (Spinner) mView.findViewById(R.id.wep_key_type);
             mWEPKeyIndex = (Spinner) mView.findViewById(R.id.wep_key_index);
             if (mWEPKeyType != null) {
                   mWEPKeyType.setOnItemSelectedListener(mController);
             }
       }

       // show WAPI CERT field
       if (accessPointSecurity == AccessPointExt.SECURITY_WAPI_CERT) {
             mView.findViewById(R.id.security_fields).setVisibility(View.GONE);
             mView.findViewById(R.id.wapi_cert_fields).setVisibility(
                         View.VISIBLE);
             mWapiAsCert = (Spinner) mView.findViewById(R.id.wapi_as_cert);
             mWapiClientCert = (Spinner) mView.findViewById(R.id.wapi_user_cert);
             mWapiAsCert.setOnItemSelectedListener(mController);
             mWapiClientCert.setOnItemSelectedListener(mController);
             loadCertificates(mWapiAsCert, Credentials.WAPI_SERVER_CERTIFICATE);
             loadCertificates(mWapiClientCert, Credentials.WAPI_USER_CERTIFICATE);

             if (mController.getAccessPoint() != null && mController.getAccessPointNetworkId() != -1) {
                   WifiConfiguration config = mController.getAccessPointConfig();
                   setCertificate(mWapiAsCert, Credentials.WAPI_SERVER_CERTIFICATE,
                           config.enterpriseConfig.getCaCertificateWapiAlias());
                   setCertificate(mWapiClientCert,
                               Credentials.WAPI_USER_CERTIFICATE, config.enterpriseConfig.getClientCertificateWapiAlias());
             }
             return true;
       }

       // set setOnClickListener for hex password
       setHexCheckBoxListener();

       if (accessPointSecurity != AccessPointExt.SECURITY_EAP) {
           // return false for not run below code which is used for eap method
           return false;
       }

       // show eap identity field
       mView.findViewById(R.id.eap_identity).setVisibility(View.VISIBLE);
       View advancedView = mView.findViewById(R.id.wifi_advanced_toggle);
       if (mController.getAccessPoint() == null && !advancedView.isShown()) {
             Log.d(TAG, "add network,Security is AccessPoint.SECURITY_EAP");
             mView.findViewById(R.id.wifi_advanced_toggle).setVisibility(
                         View.VISIBLE);
             ((CheckBox) mView.findViewById(R.id.wifi_advanced_togglebox))
                         .setOnCheckedChangeListener(mController);
             ((CheckBox) mView.findViewById(R.id.wifi_advanced_togglebox))
                         .setChecked(false);
             mView.findViewById(R.id.wifi_advanced_fields).setVisibility(
                         View.GONE);
       }
       
       // eap-sim/aka
       int eapMethod = convertEapMethod(eapMethodSpinner.getSelectedItemPosition(), 1);
       //for CMCC-AUTO eap Method config information
       if (mController.getAccessPoint() != null) {
           eapMethod = mExt.getEapMethodbySpinnerPos(
               eapMethod,
               mController.getAccessPointSsid(),
               mController.getAccessPointSecurity());
       }

       //for CMCC-AUTO ignore some config information
       if (mController.getAccessPoint() != null && mView != null) {
           mExt.hideWifiConfigInfo(new IWifiExt.Builder()
                           .setSsid(mController.getAccessPointSsid())
                           .setSecurity(mController.getAccessPointSecurity())
                           .setNetworkId(mController.getAccessPointNetworkId())
                           .setEdit(edit)
                           .setViews(mView), mConfigUi.getContext());
       }
       
       return false;
   }

    public void setWapiCertSpinnerInvisible(int accessPointSecurity) {
        if (accessPointSecurity != AccessPointExt.SECURITY_WAPI_CERT) {
            /// M: hide WAPI_CERT fileds
            mView.findViewById(R.id.wapi_cert_fields).setVisibility(View.GONE);
        }
    }
    
    public void setHexCheckBoxListener() {
        // set setOnClickListener for hex password
        ((CheckBox) mView.findViewById(R.id.hex_password)).setOnCheckedChangeListener(mController);
    }

    private void setCertificate(Spinner spinner, String prefix, String cert) {
        if (cert != null && cert.startsWith(prefix)) {
            setSelection(spinner, cert.substring(prefix.length()));
        }
    }

     private void setSelection(Spinner spinner, String value) {
           if (value != null) {
                 @SuppressWarnings("unchecked")
                 ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinner
                             .getAdapter();
                 for (int i = adapter.getCount() - 1; i >= 0; --i) {
                       if (value.equals(adapter.getItem(i))) {
                             spinner.setSelection(i);
                             break;
                       }
                 }
           }
     }

     private void loadCertificates(Spinner spinner, String prefix) {
         final Context context = mConfigUi.getContext();
         String unspecifiedCert = context.getString(R.string.wifi_unspecified);

         String[] certs = KeyStore.getInstance().saw(prefix, android.os.Process.WIFI_UID);
         if (certs == null || certs.length == 0) {
             certs = new String[] {unspecifiedCert};
         } else {
             final String[] array = new String[certs.length + 1];
             array[0] = unspecifiedCert;
             System.arraycopy(certs, 0, array, 1, certs.length);
             certs = array;
         }

         final ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                 context, android.R.layout.simple_spinner_item, certs);
         adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
         spinner.setAdapter(adapter);
     }

     private void addRow(ViewGroup group, int name, String value) {
         View row = mConfigUi.getLayoutInflater().inflate(R.layout.wifi_dialog_row, group, false);
         ((TextView) row.findViewById(R.id.name)).setText(name);
         ((TextView) row.findViewById(R.id.value)).setText(value);
         group.addView(row);
     }

     public void setHex(boolean hexEnabled) {
         mHex = hexEnabled;
     }

     public int getSecurity(int accessPointSecurity) {
         Log.d(TAG, "getSecurity, accessPointSecurity = " + accessPointSecurity);
         // only WPAI supported
         if (FeatureOption.MTK_WAPI_SUPPORT &&
                 (SystemProperties.get(WLAN_PROP_KEY, DEFAULT_WLAN_PROP).equals(WAPI))) {
           /*
            * Need to shift only when persist.sys.wlan=="wapi". Only need
            * to shift if WAPI_SUPPORT=yes && persist.sys.wlan=="wapi"
            */
           if (0 < accessPointSecurity) {
                 accessPointSecurity += AccessPointExt.SECURITY_WAPI_PSK
                             - AccessPointExt.SECURITY_WEP;
           }
         } else if (!AccessPointExt.isWFATestSupported()) {
               if (accessPointSecurity > AccessPointExt.SECURITY_PSK) {
                     accessPointSecurity += 2;
               }
         } else {
               if (accessPointSecurity > AccessPointExt.SECURITY_WEP) {
                     accessPointSecurity += 1;
               }
         }
         Log.d(TAG, "getSecurity, accessPointSecurity = " + accessPointSecurity);
         return accessPointSecurity;

     }

     public int getEapMethod(int eapMethod) {
         Log.d(TAG, "getEapMethod, eapMethod = " + eapMethod);
         int result = eapMethod;
         if (mController.getAccessPoint() != null) {
             result = mExt.getEapMethodbySpinnerPos(
                 eapMethod, mController.getAccessPointSsid(), mController.getAccessPointSecurity());
         }
         Log.d(TAG, "getEapMethod, result = " + result);
         return result;
     }

     public void setEapMethodSelection(Spinner eapMethodSpinner, int eapMethod) {
         int eapMethodPos = eapMethod;
         if (mController.getAccessPoint() != null) {
             eapMethodPos = mExt.getPosByEapMethod(eapMethod,
                     mController.getAccessPointSsid(), mController.getAccessPointSecurity());
         }
         eapMethodSpinner.setSelection(convertEapMethod(eapMethodPos, 0));
         Log.d(TAG, "[skyfyx]showSecurityFields modify pos = " + eapMethodPos);
         Log.d(TAG, "[skyfyx]showSecurityFields modify method = " + eapMethod);

     }

     public int getEapMethodbySpinnerPos(int pos) {
         int method = pos;
         if (mController.getAccessPoint() != null) {
             method = mExt.getEapMethodbySpinnerPos(method,
                     mController.getAccessPointSsid(), mController.getAccessPointSecurity());
         }
         Log.d(TAG, "showSecurityFields modify pos = " + pos);
         Log.d(TAG, "showSecurityFields modify method = " + method);

         return method;
     }

     public void setProxyText(View view) {
         //set text of proxy exclusion list
         TextView proxyText = (TextView) view.findViewById(R.id.proxy_exclusionlist_text);
         mExt.setProxyText(proxyText);
     }

     public void restrictIpv4View(WifiConfiguration config) {
         TextView ipAddressView = (TextView) mView.findViewById(R.id.ipaddress);
         TextView gatewayView = (TextView) mView.findViewById(R.id.gateway);
         TextView networkPrefixLengthView = (TextView) mView.findViewById(R.id.network_prefix_length);
         TextView dns1View = (TextView) mView.findViewById(R.id.dns1);
         TextView dns2View = (TextView) mView.findViewById(R.id.dns2);
         //restrict static IP to IPv4
         StaticIpConfiguration staticConfig = config.getStaticIpConfiguration();
         Log.d(TAG, "staticConfig = " + staticConfig);
         if (staticConfig != null) {
             Log.d(TAG, "IpAddressView = " + staticConfig.ipAddress);
             if (staticConfig.ipAddress != null && (staticConfig.ipAddress.getAddress() instanceof Inet4Address)) {
                 ipAddressView.setText(
                         staticConfig.ipAddress.getAddress().getHostAddress());
                 networkPrefixLengthView.setText(Integer.toString(staticConfig.ipAddress
                         .getNetworkPrefixLength()));
             }

             Log.d(TAG, "gatewayView = " + staticConfig.gateway);
             if (staticConfig.gateway != null && (staticConfig.gateway instanceof Inet4Address)) {
                 gatewayView.setText(staticConfig.gateway.getHostAddress());
             }

             Iterator<InetAddress> dnsIterator = staticConfig.dnsServers.iterator();
             while (dnsIterator.hasNext()) {
                 InetAddress dsn1 = dnsIterator.next();
                 Log.d(TAG, "dsn1 = " + dsn1);
                 if (dsn1 instanceof Inet4Address) {
                     dns1View.setText(dsn1.getHostAddress());
                     break;
                 }
             }
             while (dnsIterator.hasNext()) {
                 InetAddress dsn2 = dnsIterator.next();
                 Log.d(TAG, "dsn2 = " + dsn2);
               if (dsn2 instanceof Inet4Address) {
                   dns2View.setText(dsn2.getHostAddress());
                     break;
               }
             }

         }
     }
 /**
  * because eap-fast , eap-sim and eap-aka use feature option, so not always show in UI
  * @param eapMethod
  * @param getOrSet 0: get eapMethod from framework; 1: UI will set eapMethod to framework
  * @return convert index
  */
 public int convertEapMethod(int eapMethod, int getOrSet) {
     Log.d(TAG, "convertEapMethod, eapMethod =  " + eapMethod);
     Log.d(TAG, "convertEapMethod, FeatureOption.MTK_EAP_SIM_AKA =  " + FeatureOption.MTK_EAP_SIM_AKA);
     Log.d(TAG, "convertEapMethod, FeatureOption.MTK_TC1_FEATURE =  " + FeatureOption.MTK_TC1_FEATURE);
     int convertIndex = eapMethod;
     if (getOrSet == 0) {
         if (eapMethod >= WIFI_EAP_METHOD_SIM) {
             if (FeatureOption.MTK_EAP_SIM_AKA && FeatureOption.MTK_TC1_FEATURE) {
                 convertIndex = eapMethod;
             } else if (FeatureOption.MTK_EAP_SIM_AKA) {
                 if (eapMethod >= WIFI_EAP_METHOD_AKA) {
                     convertIndex = eapMethod - 1;                      
                 }
             } else if (FeatureOption.MTK_TC1_FEATURE) {
                 if (eapMethod >= WIFI_EAP_METHOD_AKA) {
                     Log.e(TAG, "convertEapMethod, eapMethod is wrong, and we set eap-sim to adapt");
                     convertIndex = WIFI_EAP_METHOD_SIM;                      
                 }
             }           
             
         }
     } else if (getOrSet == 1) {
         if (eapMethod >= WIFI_EAP_METHOD_SIM) {
             if (FeatureOption.MTK_EAP_SIM_AKA && FeatureOption.MTK_TC1_FEATURE) {
                 convertIndex = eapMethod;
             } else if (FeatureOption.MTK_EAP_SIM_AKA) {
                 if (eapMethod >= WIFI_EAP_METHOD_SIM) {
                     convertIndex = eapMethod;                      
                 }
             } else if (FeatureOption.MTK_TC1_FEATURE) {
                 if (eapMethod >= WIFI_EAP_METHOD_AKA) {
                     Log.e(TAG, "convertEapMethod, eapMethod is wrong, and we set eap-fast to adapt");
                     convertIndex = WIFI_EAP_METHOD_FAST;                      
                 }
             }           
         } 
     }

     Log.d(TAG, "convertEapMethod, convertIndex =  " + convertIndex);
     return convertIndex;
 }

}
