package com.mediatek.settings.ext;

import android.content.IntentFilter;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;


public interface IStatusExt {
    /**
     * Update the summpary of Preference from receiver
     * @param p operator_name with the key
     * @param name the operator name
     */
    void updateOpNameFromRec(Preference p, String name);
    /**
     * Update the summpary of Preference with the key "operator_name"
     * @param p operator_name with the key
     * @param name the operator name
     */
    void updateServiceState(Preference p, String name);
    /**
     * add action for Intentfilter
     * @param intent the intent need to add action
     * @param action the action to add
     */
    void addAction(IntentFilter intent, String action);
    /**
    * cusotmize imei & imei sv display name.
    * @param imeikey: the name of imei
    * @param imeiSvKey: the name of imei software version
    * @param parent: parent preference
    * @param slotId: slot id
    */
    void customizeGeminiImei(String imeiKey, String imeiSvKey, PreferenceScreen parent, int slotId);
    /**
     * Customize the sim network type item
     * @param preference Parent preference
     * @param key Preference's key
     * @param state Service state of SIM card
     * @param subId SIM sub ID
     */
    void customizeSimNetworkTypeItem(PreferenceScreen preference, String key, ServiceState state, int subId);
    /**
     * Customize the signal strength of SIM.
     * @param preference Parent preference.
     * @param key Preference's key.
     * @param state Service state of SIM card.
     * @param strength Signal strength of SIM.
     * @param slotId SIM sub ID.
     */
    void customizeSignalItem(PreferenceScreen preference, String key, SignalStrength strength, int subId);
}
