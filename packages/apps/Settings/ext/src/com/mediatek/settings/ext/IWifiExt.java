package com.mediatek.settings.ext;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;

import android.net.NetworkInfo.DetailedState;
import android.preference.ListPreference;
import android.preference.PreferenceScreen;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ArrayAdapter;

public interface IWifiExt {
    /**
     * Get ssid for wifi access point enabler
     * @param context The parent context
     * @return ssid of wifi access point
     */
    String getWifiApSsid();
    /**
     * set network id for access point
     * @param apNetworkId New network id
     */
    void setAPNetworkId(int apNetworkId);
    /**
     * Set priority for access point
     * @param apPriority New priority
     */
    void setAPPriority(int apPriority);
    /**
     * Initiatlize priority UI
     * @param context The parent context
     * @param view The view which need to be initiatlize
     */
    View getPriorityView();
    /**
     * Set security text label
     * @param context The parent context
     * @param view The view which contains security label
     */
    void setSecurityText(TextView view);
    /**
     * Get security text label
     */
    String getSecurityText(String security);

    /**
     * add disconnect button for wifi dialog
     * @param dialog
     * @param edit
     * @param state
     * @param networkId
     */
    void addDisconnectButton(AlertDialog dialog, boolean edit, DetailedState state, int networkId);
    /**
     * get priority for current select access point
     */
    int getPriority();
    /**
     * close spinner dialog when rotate screen
     */
    void closeSpinnerDialog();
    /**
     * set proxy title
     */
    void setProxyText(TextView view);
    //advanced wifi settings
    /**
     * Initiatlize connect type in wifi advanced settings
     * @param screen The screen of wifi advanced settings
     * @param connectType Connect type is manual or auto
     * @param connectApType data connection change type from Cell to Wifi, manual or auto
     * @param selectSsidType data connection change type among wifi access points
     * @param listner called when this preference has changed
     */
    void initConnectView(Activity activity, PreferenceScreen screen);
    /**
     * Initiatlize gatway & netmask info in wifi advanced settings
     * @param screen The screen of wifi advanced settings
     */
    void initNetworkInfoView(PreferenceScreen screen);
    /**
     * refresh gatway & netmask info in wifi advanced settings
     */
    void refreshNetworkInfoView();
    /**
     * Initiatlize preference for wifi advanced settings
     * @param contentResolver The parent content resolver
     */
    void initPreference(ContentResolver contentResolver);
    /**
     * Get sleep policy
     * @param contentResolver The parent content resolver
     * @return sleep policy
     */
    int getSleepPolicy(ContentResolver contentResolver);

    //access point
    /**
     * Order access points
     * @param currentSsid The ssid of current access point
     * @param currentSecurity The security of current access point
     * @param otherSsid The ssid of other access point
     * @param otherSecurity The security of other access point
     * @return whether current access point is in front of another
     */
    int getApOrder(String currentSsid, int currentSecurity, String otherSsid, int otherSecurity);

    /**
     * Set sleep policy preference entries and values
     * @param listPreference Wifi sleep policy setting preference
     * @param entriesID wifi sleep policy setting entries array id
     * @param ValuesID wifi sleep policy setting values array id
     */
    void setSleepPolicyPreference(ListPreference sleepPolicyPref, String[] sleepPolicyEntries, String[] sleepPolicyValues);
    /**
     * hide edit ap info
     * @param builder access point information
     */
    void hideWifiConfigInfo(Builder builder , Context context);

    public class Builder {
        private String mSsid;
        private int mSecurity;
        private int mNetworkId;
        private boolean mEdit;
        private View mView;

        public Builder() {

        }

        public Builder setSsid(String ssid) {
            this.mSsid = ssid;
            return this;
        }

        public String getSsid() {
            return this.mSsid;
        }
        public Builder setSecurity(int security) {
            this.mSecurity = security;
            return this;
        }

        public int getSecurity() {
            return this.mSecurity;
        }
        public Builder setNetworkId(int networkId) {
            this.mNetworkId = networkId;
            return this;
        }


        public int getNetworkId() {
            return this.mNetworkId;
        }
        public Builder setEdit(boolean edit) {
            this.mEdit = edit;
            return this;
        }

        public boolean getEdit() {
            return this.mEdit;
        }
        public Builder setViews(View view) {
            this.mView = view;
            return this;
        }

        public View getViews() {
            return this.mView;
        }

    }

    void setEapMethodArray(ArrayAdapter adapter, String ssid, int security);

    //wifi controller
    /**
     * get eap method by spinner position
     * @param lists current edit access point information
     */
    int getEapMethodbySpinnerPos(int spinnerPos, String ssid, int security);

    int getPosByEapMethod(int spinnerPos, String ssid, int security);
}
