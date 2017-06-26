package com.mediatek.settings.fuelgauge;

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.provider.SettingsEx.Systemex;
import android.util.Log;

import com.android.settings.R;
import com.mediatek.settings.ext.IBatteryExt;
import com.mediatek.settings.FeatureOption;
import com.mediatek.settings.UtilsExt;
import com.android.settings.SettingsPreferenceFragment;

public class PowerUsageExts extends SettingsPreferenceFragment implements 
Preference.OnPreferenceChangeListener {

    private static final String TAG = "PowerUsageSummary";

    private static final String KEY_BACKGROUND_POWER_SAVING = "background_power_saving";
    
    private static final String KEY_BACKGROUND_POWER_PERCENT = "background_power_percent";

    private Context mContext;
    private PreferenceGroup mAppListGroup;
    private SwitchPreference mBgPowerSavingPrf;
    private SwitchPreference mBgPowerPercentPrf;

    // Power saving mode feature plug in
    private IBatteryExt mBatteryExt;

    public PowerUsageExts(Context context, PreferenceGroup appListGroup) {
        mContext = context;
        mAppListGroup = appListGroup;
        // Battery plugin initialization
        mBatteryExt = UtilsExt.getBatteryExtPlugin(context);

    }

    // init power usage extends items
    public void initPowerUsageExtItems() {
        // Power saving mode for op09
        mBatteryExt.loadPreference(mContext, mAppListGroup);

        // background power saving
        /* HQ_fengyaling_2015-9-28 modified for removing the alarm grouping switch begin */
        /*if (FeatureOption.MTK_BG_POWER_SAVING_SUPPORT
                && FeatureOption.MTK_BG_POWER_SAVING_UI_SUPPORT) {
            mBgPowerSavingPrf = new SwitchPreference(mContext);
            mBgPowerSavingPrf.setKey(KEY_BACKGROUND_POWER_SAVING);
            mBgPowerSavingPrf.setTitle(R.string.bg_power_saving_title);
            mBgPowerSavingPrf.setOrder(-4);
            mBgPowerSavingPrf.setChecked(Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.BG_POWER_SAVING_ENABLE, 1) != 0);
            mAppListGroup.addPreference(mBgPowerSavingPrf);
        }*/
        /* HQ_fengyaling_2015-9-28 modified for removing the alarm grouping switch begin */
        // background power percent
        mBgPowerPercentPrf = new SwitchPreference(mContext);
        mBgPowerPercentPrf.setKey(KEY_BACKGROUND_POWER_PERCENT);
        mBgPowerPercentPrf.setTitle(R.string.bg_power_percent_title);
        mBgPowerPercentPrf.setSummary(R.string.bg_power_percent_summary);
        mBgPowerPercentPrf.setOrder(-4);
        int state = Systemex.getInt(mContext.getContentResolver(),
        			"battery_percent_switch",-1);  
        Log.d(TAG, "Systemex.getInt state = " + state);
        mBgPowerPercentPrf.setChecked(state == 1);
        mBgPowerPercentPrf.setOnPreferenceChangeListener(this);
        mAppListGroup.addPreference(mBgPowerPercentPrf);
    }
    
    // on click
    public boolean onPowerUsageExtItemsClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (KEY_BACKGROUND_POWER_SAVING.equals(preference.getKey())) {
            if (preference instanceof SwitchPreference) {
                SwitchPreference pref = (SwitchPreference) preference;
                int bgState = pref.isChecked() ? 1 : 0;
                Log.d(TAG, "background power saving state: " + bgState);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.BG_POWER_SAVING_ENABLE, bgState);
                if (mBgPowerSavingPrf != null) {
                    mBgPowerSavingPrf.setChecked(pref.isChecked());
                }
            }
            // If user click on PowerSaving preference just return here
            return true;
        } else if (KEY_BACKGROUND_POWER_PERCENT.equals(preference.getKey())) {
            if (preference instanceof SwitchPreference) {
                SwitchPreference pref = (SwitchPreference) preference;
                int bgState = pref.isChecked() ? 1 : 0;
                Log.d(TAG, "background power percent state: " + bgState);
                Systemex.putInt(mContext.getContentResolver(),
                        "battery_percent_switch", bgState); 
                if (mBgPowerPercentPrf != null) {
                	mBgPowerPercentPrf.setChecked(pref.isChecked());
                }
            }
            // If user click on PowerSaving preference just return here
            return true;
        }else if (mBatteryExt.onPreferenceTreeClick(preferenceScreen, preference)) {
            return true;
        }
        return false;
    }
    
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (mBgPowerPercentPrf == preference) {
        	SwitchPreference pref = (SwitchPreference) preference;
            int bgState = pref.isChecked() ? 1 : 0;
            Log.d(TAG, "background power percent state: " + bgState);
            Systemex.putInt(mContext.getContentResolver(),
                    "battery_percent_switch", (boolean)newValue ? 1 : 0); 
            return true;
        } 
        return false;
    }
    
}
