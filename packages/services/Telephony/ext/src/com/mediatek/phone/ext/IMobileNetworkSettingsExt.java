package com.mediatek.phone.ext;

import android.app.AlertDialog;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

public interface IMobileNetworkSettingsExt {

    /**
     * called in onCreate() of the Activity
     * plug-in can init itself, preparing for it's function
     * @param activity the MobileNetworkSettings activity
     */
    void initOtherMobileNetworkSettings(PreferenceActivity activity);

    /**
     * called in onCreate() of the Activity
     * plug-in can init itself, preparing for it's function
     * @param activity the MobileNetworkSettings activity
     * @param subId sub id
     */
    void initOtherMobileNetworkSettings(PreferenceActivity activity, int subId);

    /**
     * called in onCreate() of the Activity.
     * plug-in can init itself, preparing for it's function
     * @param activity the MobileNetworkSettings activity
     * @param currentTab current Tab
     */
    void initMobileNetworkSettings(PreferenceActivity activity, int currentTab);

    /**
     * Attention, returning false means nothing but telling host to go on its own flow.
     * host would never return plug-in's "false" to the caller of onPreferenceTreeClick()
     *
     * @param preferenceScreen the clicked preference screen
     * @param preference the clicked preference
     * @return true if plug-in want to skip host flow. whether return true or false, host will
     * return true to its real caller.
     */
    boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference);

    /**
     * This interface is for updating the MobileNetworkSettings' item "Preferred network type"
     * @param preference there are two cases:
     *                   1. mButtonPreferredNetworkMode in host app
     *                   2. mButtonEnabledNetworks in host app
     */
    void updateNetworkTypeSummary(ListPreference preference);

    /**
     * TODO: Clear about what is this interface for
     * @param preference
     */
    void updateLTEModeStatus(ListPreference preference);

    /**
     * Alow Plug-in to customize the AlertDialog passed.
     * This API should be called right before builder.create().
     * Plug-in should check the preference to determine how the Dialog should act.
     * @param preference the clicked preference
     * @param builder the AlertDialog.Builder passed from host app
     */
    void customizeAlertDialog(Preference preference, AlertDialog.Builder builder);


    /**
     * Updata the ButtonPreferredNetworkMode's summary and enable when sim2 is CU card.
     * @param listPreference ButtonPreferredNetworkMode
     */
    void customizePreferredNetworkMode(ListPreference listPreference, int subId);

    /**
     * Preference Change, update network preference value and summary
     * @param preference the clicked preference
     * @param objValue choose obj value
     */
    void onPreferenceChange(Preference preference, Object objValue);

    /**
     * For Plugin to update Preference.
     */
    void onResume();

    /**
     * For Plugin to pause event and listener registration.
     */
    void unRegister();

    /**
     * for CT feature , CT plugin should return true.
     * @return true,if is CT plugin
     */
    boolean isCtPlugin();

    boolean useCTTestcard();
    
    /**
     * Check if Volte setting needed for operator
     * @return false for EE and true otherwise
     */    
    boolean volteEnabledForOperator();
}
