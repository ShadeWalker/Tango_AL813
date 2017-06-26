package com.mediatek.wifi;

import android.content.Context;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.SystemProperties;
import android.preference.Preference;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settings.wifi.WifiConfigUiBase;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PasspointUtils {
    private static final String TAG = "PasspointUtils";
    private static final String REG_HOTSPOT = "hs20=(\\d+)\\s";
    private static final String REG_CREDENTIAL_TYPE = "selectedMethod=(\\d+)\\s";
    private static final String REG_EAP_METHOD = "Phase2 method=(\\w+)\\s";

    private static final int CREDENTIAL_TYPE_TTLS = 21;
    private static final int CREDENTIAL_TYPE_SIM = 18;
    private static final int CREDENTIAL_TYPE_TLS = 13;

    private static final int TTLS_INDEX = 0;
    private static final int SIM_INDEX = 1;
    private static final int TLS_INDEX = 2;
    private static final int HOTSPOT_INDEX = 0;
    private static final int CREDENTIAL_TYPE_INDEX = 1;
    private static final int EAP_METHOD_INDEX = 2;
    private static final int PASSPOINT_INFO_ITEMS = 3;
    /*
     * setSummary: set passpoint ap's summary, when this ap is connected or not active
     */
    public static void setSummary(Preference ap, Context context, boolean supportedPasspoint, DetailedState state) {
        if (supportedPasspoint) {
            Log.d(TAG, "setSummary, ap = " + ap + ", supportedPasspoint = " + supportedPasspoint + ", state = " + state);
            StringBuilder summary = new StringBuilder();
            if (ap.getSummary() != null) {
                summary.append(ap.getSummary());
                summary.append(" ");
            }
            summary.append(context.getString(state == DetailedState.CONNECTED ?
                    R.string.passpoint_append_summary : R.string.passpoint_append_summary_not_connected));
            Log.d(TAG, "setSummary = " + summary.toString());
            ap.setSummary(summary.toString());
        }
    }

    /*
     * setTitle: set passpoint ap's title, when this ap is connected or not active
     */
    public static void setTitle(Preference ap, Context context, boolean supportedPasspoint, DetailedState state) {
        if (supportedPasspoint) {
            Log.d(TAG, "setTitle, ap = " + ap + ", supportedPasspoint = " + supportedPasspoint + ", state = " + state);
            StringBuilder title = new StringBuilder();
            if (ap.getTitle() != null) {
                title.append(ap.getTitle());
                title.append(" ");
            }
            title.append(context.getString(state == DetailedState.CONNECTED ?
                    R.string.passpoint_append_summary : R.string.passpoint_append_summary_not_connected));
            Log.d(TAG, "setTitle = " + title.toString());
            ap.setTitle(title.toString());
        }
    }

    /*
     * shouldUpdate: current connected passpoint Ap doesn't have WifiConfiguration, so we should update Accesspoint by
     * our method
     */
    public static boolean shouldUpdate(WifiInfo info, String bssid, boolean supportedPasspoint) {
        // just for debug
        if (supportedPasspoint) {
            Log.d(TAG, "shouldUpdate, info = " + info + ", bssid = " + bssid);
        }
        boolean sigmaTest = SystemProperties.getInt("mediatek.wlan.hs20.sigma", 0) == 1;
        Log.d(TAG, "shouldUpdate, sigmaTest = " + sigmaTest);
        // Passpoint ap or do sigmaTest, we should update ap's information
        return (supportedPasspoint || sigmaTest) && info != null && bssid != null && bssid.equals(info.getBSSID());
    }

    /*
     * addView: current connected passpoint ap has its own WifiDialog information
     */
    public static boolean addView(final WifiConfigUiBase configUi, DetailedState state, View view,
            boolean shouldSetDisconnectButton) {
        Log.d(TAG, "addView, shouldSetDisconnectButton = " + shouldSetDisconnectButton +
                ", state = " + state);
        if (state != null) {
            ViewGroup group = (ViewGroup) view.findViewById(R.id.info);
            final Context context = configUi.getContext();
            view.findViewById(R.id.priority_field).setVisibility(View.GONE);

            addRows(configUi, group, state);

            //marked for passpoint r2 is not needed now
            /*if (FeatureOption.MTK_PASSPOINT_R2_SUPPORT && state == DetailedState.CONNECTED) {
                // passpoint not exists forget button, and we just user it
                configUi.setForgetButton(context.getString(R.string.passpoint_credential_button));
                Button button = configUi.getForgetButton();
                button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Log.d(TAG, "change credential click");
                        final Activity activity = ((AlertDialog)configUi).getOwnerActivity();
                        if (activity instanceof PreferenceActivity) {
                            ((PreferenceActivity)activity).startPreferencePanel(
                                    PasspointCredential.class.getCanonicalName(),
                                    null,
                                    R.string.passpoint_credential_title, null,
                                    null, 0);
                        } else {
                            Log.e(TAG, "Parent isn't PreferenceActivity, thus there's no way to launch the "
                                    + "given Fragment name: " + PasspointCredential.class.getCanonicalName());
                        }
                    }
                });
            }*/
            if (shouldSetDisconnectButton) {
                configUi.setSubmitButton(context.getString(R.string.wifi_disconnect));
            }
            configUi.setCancelButton(context.getString(R.string.wifi_cancel));
            return true;
        }

        return false;
    }

    /*
     * addRows: WifiDialog add passpoint three items of information
     */
    private static void addRows(WifiConfigUiBase configUi, ViewGroup group, DetailedState state) {
        Log.d(TAG, "addRows, DetailedState = " + state);
        if (state == DetailedState.CONNECTED) {
            Context context = configUi.getContext();
            List<String> passpointInfo = new ArrayList<String>();
            getPasspointInfo(context, passpointInfo);
            if (passpointInfo.get(HOTSPOT_INDEX) != null) {
                addRow(configUi, group, R.string.passpoint_config_hotspot, context.getString(R.string.passpoint_supported));
            }


            String[] credentialType = context.getResources().getStringArray(R.array.passpoint_credential_type);
            String[] eapMethodPhase = context.getResources().getStringArray(R.array.passpoint_eap_method);

            String strCredentialType = null;
            String strEapMethodPhase1 = null;

            String type = passpointInfo.get(CREDENTIAL_TYPE_INDEX);
            if (type != null) {
                switch (Integer.parseInt(type)) {
                case CREDENTIAL_TYPE_TTLS:
                    strCredentialType = credentialType[TTLS_INDEX];
                    strEapMethodPhase1 = eapMethodPhase[TTLS_INDEX];
                    break;
                case CREDENTIAL_TYPE_SIM:
                    strCredentialType = credentialType[SIM_INDEX];
                    strEapMethodPhase1 = eapMethodPhase[SIM_INDEX];
                    break;
                case CREDENTIAL_TYPE_TLS:
                    strCredentialType = credentialType[TLS_INDEX];
                    strEapMethodPhase1 = eapMethodPhase[TLS_INDEX];
                    break;
                default:
                    Log.e(TAG, "addRows error");
                    break;
                }
                addRow(configUi, group, R.string.passpoint_config_credential_type, strCredentialType);
            }

            String strEapMethodPhase2 = passpointInfo.get(EAP_METHOD_INDEX);
            if (strEapMethodPhase1 != null && strEapMethodPhase2 != null) {
                addRow(configUi, group, R.string.passpoint_config_eap_method, strEapMethodPhase1 + strEapMethodPhase2);
            }
        }

    }

    private static void addRow(WifiConfigUiBase configUi, ViewGroup group, int name, String value) {
        View row = configUi.getLayoutInflater().inflate(R.layout.wifi_dialog_row, group, false);
        ((TextView) row.findViewById(R.id.name)).setText(name);
        ((TextView) row.findViewById(R.id.value)).setText(value);
        group.addView(row);
    }

    private static String regPasspointInfo(String reg, String info) {
        if (reg == null || info == null) {
            return null;
        }
        Pattern pattern = Pattern.compile(reg);
        Matcher matcher = pattern.matcher(info);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }


    private static void getPasspointInfo(Context context, List<String> passpointInfo) {
        String info = getPasspointInfo(context);
        passpointInfo.add(regPasspointInfo(REG_HOTSPOT, info));
        passpointInfo.add(regPasspointInfo(REG_CREDENTIAL_TYPE, info));
        passpointInfo.add(regPasspointInfo(REG_EAP_METHOD, info));
    }

    private static String getPasspointInfo(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        //framework not ready
        //String info = wifiManager.getHsStatus();
        String info = "framework not ready";
        Log.d(TAG, "getPasspointInfo = " + info);
        return info;
    }

}
