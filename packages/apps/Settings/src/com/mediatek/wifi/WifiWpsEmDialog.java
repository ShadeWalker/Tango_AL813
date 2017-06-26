package com.mediatek.wifi;

import static android.net.wifi.WifiConfiguration.INVALID_NETWORK_ID;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.android.settings.R;
import com.mediatek.xlog.Xlog;


public class WifiWpsEmDialog extends AlertDialog implements DialogInterface.OnClickListener, View.OnClickListener
                                                            , AdapterView.OnItemSelectedListener {
        private static final String TAG = "WifiWpsEmDialog";
        private static final int BUTTON_SUBMIT = DialogInterface.BUTTON_POSITIVE;
        //private static  final int BUTTON_NEGATIVE = DialogInterface.BUTTON_NEGATIVE;
        private final AccessPoint mAccessPoint;

        private View mView;
        private Context mContext;
        private Spinner mPinSecuritySpinner;
        EditText mSsid;
        EditText mKey;
        EditText mPinCode;
        private boolean mIsOnlyVisibilityWpsPin;
        private WifiManager.WpsCallback mWpsListener;
        private WifiManager mWifiManager;


        public WifiWpsEmDialog(Context context, AccessPoint accessPoint, boolean isOnlyVisibilityWpsPin) {
            super(context, R.style.Theme_WifiDialog);
            mAccessPoint = accessPoint;
            mContext = context;
            mIsOnlyVisibilityWpsPin = isOnlyVisibilityWpsPin;
        }


        @Override
        protected void onCreate(Bundle savedInstanceState) {
            mView = getLayoutInflater().inflate(R.layout.wifi_dialog_wps_em, null);
            setView(mView);
            if (mIsOnlyVisibilityWpsPin) {
                (mView.findViewById(R.id.wifi_wps_pin_fields)).setVisibility(View.GONE);
                (mView.findViewById(R.id.nfc_password_token)).setVisibility(View.GONE);
            }
            if (mAccessPoint != null) {
                setTitle(mAccessPoint.ssid);
            } else {
                setTitle(R.string.wifi_wps_em_reg_pin);
            }

            setButton(BUTTON_SUBMIT, mContext.getString(R.string.wifi_save), this);
            setButton(BUTTON_NEGATIVE, mContext.getString(R.string.wifi_cancel), this);
            ((CheckBox) mView.findViewById(R.id.wifi_defalut_pins_togglebox)).setOnClickListener(this);
            mPinSecuritySpinner = (Spinner) mView.findViewById(R.id.pin_security);
            mSsid = (EditText) mView.findViewById(R.id.wifi_wps_em_ssid);
            mKey = (EditText) mView.findViewById(R.id.wifi_wps_em_key);
            mPinCode = (EditText) mView.findViewById(R.id.wifi_pin_code);
            mPinSecuritySpinner.setOnItemSelectedListener(this);
            mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);

            setInverseBackgroundForced(true);

            super.onCreate(savedInstanceState);
            /* During creation, the submit button can be unavailable to determine
             * visibility. Right after creation, update button visibility */
        }

        public void onClick(View view) {
             if (view.getId() == R.id.wifi_defalut_pins_togglebox) {
                  Xlog.d(TAG, "onClick wifi_defalut_pins_togglebox clicked " + "hide PIN items");
                  if (((CheckBox) view).isChecked()) {
                      ((EditText) mView.findViewById(R.id.wifi_wps_em_ssid)).setEnabled(false);
                      ((Spinner) mView.findViewById(R.id.pin_security)).setEnabled(false);
                      ((EditText) mView.findViewById(R.id.wifi_wps_em_key)).setEnabled(false);

                  } else {
                      ((EditText) mView.findViewById(R.id.wifi_wps_em_ssid)).setEnabled(true);
                      ((Spinner) mView.findViewById(R.id.pin_security)).setEnabled(true);
                      ((EditText) mView.findViewById(R.id.wifi_wps_em_key)).setEnabled(true);
                  }
              } else if (view.getId() == R.id.nfc_password_token_togglebox) {
                  Xlog.d(TAG, "onClick nfc_password_token_togglebox clicked " + "disable PIN items");
                  Xlog.d(TAG, "nfc_password_token_togglebox is checked : " + ((CheckBox) view).isChecked());
                  ((EditText) mView.findViewById(R.id.wifi_pin_code)).setEnabled(!((CheckBox) view).isChecked());

              }

        }


        public void onClick(DialogInterface dialogInterface, int button) {
            if (button == BUTTON_SUBMIT) {
                 Xlog.d(TAG, "onClick, save configuration");
                  mWpsListener = new WifiManager.WpsCallback() {
                    public void onStarted(String pin) {
                    }
                    public void onSucceeded() {
                         if (!mIsOnlyVisibilityWpsPin && mPinSecuritySpinner.getSelectedItemPosition() == 0) {
                            Toast.makeText(mContext, R.string.wifi_open_mode, Toast.LENGTH_LONG).show();
                        }
                    }
                    public void onFailed(int reason) {
                        Xlog.d(TAG, "onFailed, the reason is :" + reason);
                        if (reason == WifiManager.WPS_INVALID_PIN) {
                            Toast.makeText(mContext, "Invalid PIN code", Toast.LENGTH_SHORT).show();
                        }
                    }
                };
                WpsInfo config = new WpsInfo();
                config.setup = config.KEYPAD;
                config.pin = mPinCode.getText().toString();
                if (mIsOnlyVisibilityWpsPin) {
                    if (mAccessPoint == null || mAccessPoint.networkId != INVALID_NETWORK_ID) {
                        Xlog.d(TAG, "startWpsExternalRegistrar, config = " + config.toString());
                        mWifiManager.startWpsExternalRegistrar(config, mWpsListener);
                    } else {
                        Xlog.d(TAG, "startWps, config = " + config.toString());
                        config.BSSID = mAccessPoint.bssid;
                        mWifiManager.startWps(config, mWpsListener);
                    }
                } else {
                    CheckBox nfcPasswordCheckBox = (CheckBox) mView.findViewById(R.id.nfc_password_token_togglebox);
                    if (!nfcPasswordCheckBox.isChecked() && (mPinCode.getText() == null || "".equals(mPinCode.getText()))) {
                        Toast.makeText(mContext, "Please enter PIN code", Toast.LENGTH_SHORT).show();

                    } else {
                        if (((CheckBox) mView.findViewById(R.id.nfc_password_token_togglebox)).isChecked()) {
                            Settings.System.putInt(mContext.getContentResolver(), "nfc_pw", 1);
                        } else {
                            Settings.System.putInt(mContext.getContentResolver(), "nfc_pw", 0);
                        }
                        config.BSSID = mAccessPoint.bssid;
                        config.key = mKey.getText().toString();
                         if (mPinSecuritySpinner.getSelectedItemPosition() == 0) {
                             config.authentication = "OPEN";
                             config.encryption = "NONE";

                        } else if (mPinSecuritySpinner.getSelectedItemPosition() == 1) {
                             config.authentication = "WPA2PSK";
                             config.encryption = "CCMP";
                        }
                         config.ssid = mSsid.getText().toString();
                         Xlog.d(TAG, "startWpsRegistrar, config = " + config.toString());
                        mWifiManager.startWpsRegistrar(config, mWpsListener);
                    }
                }
            }

        }


        public void onItemSelected(AdapterView<?> parent, View view, int position,
                  long id) {
                Xlog.d(TAG, "onItemSelected");

            if (parent == mPinSecuritySpinner) {
                if (mPinSecuritySpinner.getSelectedItemPosition() == 0) {
                    mKey.setEnabled(false);
                } else {
                    mKey.setEnabled(true);
                }

            }

        }


        public void onNothingSelected(AdapterView<?> parent) {
            // TODO Auto-generated method stub

        }
    }
