package com.mediatek.settings.ext;

import android.content.IntentFilter;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;

public class DefaultStatusExt implements IStatusExt {
    /**
     * update the operator name
     * @param p
     * @param name
     */
    public void updateOpNameFromRec(Preference p, String name){

    }

    /**
     * update the summar
     * @param p
     * @param name
     */
    public void updateServiceState(Preference p, String name) {
        p.setSummary(name);
    }

    /**
     * add the intent to the intentfilter
     * @param intent
     * @param action
     */
    public void addAction(IntentFilter intent, String action) {

    }

    /**
    * cusotmize imei & imei sv display name.
    * @param imeikey: the name of imei
    * @param imeiSvKey: the name of imei software version
    * @param parent: parent preference
    * @param slotId: slot id
    */
    public void customizeGeminiImei(String imeiKey, String imeiSvKey, PreferenceScreen parent,
            int slotId) {

   }

    /**
     * Customize the sim network type item
     * @param preference Parent preference
     * @param key Preference's key
     * @param state Service state of SIM card
     * @param subId SIM sub ID
     */
    public void customizeSimNetworkTypeItem(PreferenceScreen preference, String key,
            ServiceState state, int subId) {

    }

    /**
     * Customize the signal strength of SIM.
     * @param preference Parent preference.
     * @param key Preference's key.
     * @param strength Signal strength of SIM.
     * @param subId SIM sub ID.
     */
    public void customizeSignalItem(PreferenceScreen preference, String key,
            SignalStrength strength, int subId) {

    }

}
