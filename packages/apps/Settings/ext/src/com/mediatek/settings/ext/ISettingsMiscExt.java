package com.mediatek.settings.ext;

import android.content.Context;
import android.widget.Switch;
import android.preference.Preference;
import android.preference.PreferenceActivity.Header;
import android.preference.PreferenceScreen;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;



import java.util.List;

public interface ISettingsMiscExt {

    /**
     * Custimize the SSID of wifi tethering
     * @param ctx: parent context
     * @return tether wifi string.
     */
    String getTetherWifiSSID(Context ctx);

    /**
     * Custimize the title of screen timeout preference
     * @param pref the screen timeout preference
     */
    void setTimeoutPrefTitle(Preference pref);

    /**
     *Customize the title of factory reset settings
     * @param obj header or activity
     * @return factory reset title.
     */
    void setFactoryResetTitle(Object obj);


    /**
    * Add customize headers in settings, lick International Roaming header
    * @param target: header list in settings
    * @param index: position of customized item to be added
    */
    void addCustomizedItem(List<Header> target, int index);

    /**
    * Customize strings which contains 'SIM', replace 'SIM' by 'UIM/SIM','UIM','card' etc.
    * @param simString : the strings which contains SIM
    * @param soltId : 1 , slot1 0, slot0 , -1 means always.
    */
    String customizeSimDisplayString(String simString, int slotId);

    /**
     * Add the operator customize settings in Settings->Location
     * @param pref: the root preferenceScreen
     * @param order: the customize settings preference order
     */
    void initCustomizedLocationSettings(PreferenceScreen root, int order);

    /**
     * Update customize settings when location mode changed
     */
    void updateCustomizedLocationSettings();

    /**
     * Customize the display UI when click Settings->Location access
     * @param header: the location access header
     */
    void customizeLocationHeaderClick(Header header);

    /**
     * Whether we need to customized the header icon of operator requiement.
     *
     * @param header
     * @return
     */
    boolean needCustomizeHeaderIcon(Header header);

    /**
     * Customize head icon using operator drawable resource.
     *
     * @param iconView
     * @param header
     */
    void customizeHeaderIcon(ImageView iconView, Header header);

    /**
     * Initialize switcher controllers in plugin.
     */
    void initSwitherControlers();

    /**
     * Set Switch for specified Enabler.
     * @param header the Header of the Enabler to set switch for
     * @param switch_ the siwtch_ set to enabler
     */
    void setEnablerSwitch(Header header, Switch switch_);

    /**
     * Whether this header is switcher type
     * @param header the header to check
     *
     * @return true if the header is switcher type
     */
    boolean isSwitcherHeaderType(Header header);

    /**
     * Resume enabler
     */
    void resumeEnabler();

    /**
     * Pause enabler
     */
    void pauseEnabler();
    /**
     * A way to enable/disable some features.
     * @return True for enable else disable.
     */
    boolean isFeatureEnable();

    /**
     * Add customize item in settings.
     * @param targetDashboardCategory header list in settings,
     *  set to object so that settings.ext do not depend on settings
     * @param index position of customized item to be added
     */
    void addCustomizedItem(Object targetDashboardCategory, int index);

    /**
     * Customize add item drawable and location.
     * @param tile the new DashboardTile which create in CT will add intent.extra.
     * @param categoryContent ViewGroup,for changed the DashboardTile's location
     * @param tileView DashboardTileView,
     * @param imageView for dashboardTile imageView set the drawable
     * @param type 1 means customized the location,2 means customized drawable
     */
    void customizeDashboardTile(Object tile, ViewGroup categoryContent,
            View tileView, ImageView imageView, int type);

    void updataDefaultDataConnection(Map<String, Boolean> DataEnableMap, Context context);

    void setDefaultDataEnable(Context context, int subid);

    boolean useCTTestcard();

}
