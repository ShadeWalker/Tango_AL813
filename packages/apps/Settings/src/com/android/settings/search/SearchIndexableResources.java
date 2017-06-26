/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.search;

import android.provider.SearchIndexableResource;
import com.android.settings.DateTimeSettings;
import com.android.settings.DevelopmentSettings;
import com.android.settings.DeviceInfoSettings;
import com.android.settings.DisplaySettings;
import com.android.settings.LauncherModeSetting;
import com.android.settings.HomeSettings;
import com.android.settings.PrivacySettings;
import com.android.settings.R;
import com.android.settings.ScreenlockPasswordSettings;
import com.android.settings.SecuritySettings;
import com.android.settings.WallpaperTypeSettings;
import com.android.settings.WirelessSettings;
import com.android.settings.accessibility.AccessibilitySettings;
import com.android.settings.bluetooth.BluetoothSettings;
import com.android.settings.deviceinfo.Memory;
import com.android.settings.deviceinfo.UsbSettings;
import com.android.settings.fuelgauge.BatterySaverSettings;
import com.android.settings.fuelgauge.PowerUsageSummary;
import com.android.settings.inputmethod.InputMethodAndLanguageSettings;
import com.android.settings.location.LocationSettings;
import com.android.settings.net.DataUsageMeteredSettings;
import com.android.settings.notification.NotificationSettings;
import com.android.settings.notification.OtherSoundSettings;
import com.android.settings.notification.ZenModeSettings;
import com.android.settings.sim.SimSettings;
import com.android.settings.users.UserSettings;
import com.android.settings.voice.VoiceInputSettings;
import com.android.settings.wifi.AdvancedWifiSettings;
import com.android.settings.wifi.SavedAccessPointsWifiSettings;
import com.android.settings.wifi.WifiSettings;
import com.android.settings.NoDisturbSettings;
import com.android.settings.NotificationCenterSettings;
import com.android.settings.NetAppMgrSettings;
import com.android.settings.ProtectedAppSettings;
import com.android.settings.StartupManagerSettings;
import com.android.settings.PermissionMgrSettings;
import com.android.settings.CloudServiceSettings;
import com.android.settings.DataUsageSettings;
import com.android.settings.GestureSettings;
import com.android.settings.BatterySaveSettings;
import com.android.settings.accounts.AccountSettings;
import com.android.settings.applications.ManageApplications;
import com.android.settings.KeylockGesturesSettings;
import com.android.settings.location.VirtualKeySettings;
import com.android.settings.SuspendButtonSettings;
import com.android.settings.SmartEarphoneControlSettings;
import com.android.settings.inputmethod.InputMethodAndLanguageSettings;
import com.android.settings.accounts.AccountSettings;
import com.android.settings.AutoUpdateSettings;
import com.android.settings.KeylockGesturesSettings;

import com.mediatek.audioprofile.AudioProfileSettings;
import com.mediatek.audioprofile.SoundSettings;
import com.mediatek.audioprofile.SoundEnhancement;
import com.mediatek.search.SearchExt;
import com.mediatek.settings.hotknot.HotKnotSettings;
import com.mediatek.nfc.NfcSettings;
import com.mediatek.wfc.WfcSettings;

import java.util.Collection;
import java.util.HashMap;

public final class SearchIndexableResources {

    public static int NO_DATA_RES_ID = 0;

    public static HashMap<String, SearchIndexableResource> sResMap =
            new HashMap<String, SearchIndexableResource>();

    static {
        sResMap.put(WifiSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(WifiSettings.class.getName()),
                        NO_DATA_RES_ID,
                        WifiSettings.class.getName(),
                        R.drawable.ic_settings_wireless));

        sResMap.put(AdvancedWifiSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(AdvancedWifiSettings.class.getName()),
                        R.xml.wifi_advanced_settings,
                        AdvancedWifiSettings.class.getName(),
                        R.drawable.ic_settings_wireless));

        sResMap.put(SavedAccessPointsWifiSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(SavedAccessPointsWifiSettings.class.getName()),
                        R.xml.wifi_display_saved_access_points,
                        SavedAccessPointsWifiSettings.class.getName(),
                        R.drawable.ic_settings_wireless));

        sResMap.put(BluetoothSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(BluetoothSettings.class.getName()),
                        NO_DATA_RES_ID,
                        BluetoothSettings.class.getName(),
                        R.drawable.ic_settings_bluetooth2));   

        sResMap.put(SimSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(SimSettings.class.getName()),
                        NO_DATA_RES_ID,
                        SimSettings.class.getName(),
                        R.drawable.ic_settings_dual_card));

        sResMap.put(DataUsageMeteredSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(DataUsageMeteredSettings.class.getName()),
                        R.xml.data_usage_metered_prefs,
                        DataUsageMeteredSettings.class.getName(),
                        R.drawable.ic_settings_data_usage));

        sResMap.put(WirelessSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(WirelessSettings.class.getName()),
                        R.xml.wireless_settings,
                        WirelessSettings.class.getName(),
                        R.drawable.ic_settings_more));

        sResMap.put(HomeSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(HomeSettings.class.getName()),
                        NO_DATA_RES_ID,
                        HomeSettings.class.getName(),
                        R.drawable.ic_settings_home));

        sResMap.put(DisplaySettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(DisplaySettings.class.getName()),
                        R.xml.display_settings,
                        DisplaySettings.class.getName(),
                        R.drawable.ic_settings_display));

        sResMap.put(LauncherModeSetting.class.getName(),
               new SearchIndexableResource(
                        Ranking.getRankForClassName(LauncherModeSetting.class.getName()),
                        R.xml.launcher_mode_setting,
                        LauncherModeSetting.class.getName(),
                        R.drawable.ic_settings_home));

        sResMap.put(WallpaperTypeSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(WallpaperTypeSettings.class.getName()),
                        R.xml.wallpaper_settings,
                        WallpaperTypeSettings.class.getName(),
                        R.drawable.ic_settings_display));

        /*sResMap.put(NotificationSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(NotificationSettings.class.getName()),
                        NO_DATA_RES_ID,
                        NotificationSettings.class.getName(),
                        R.drawable.ic_settings_notifications));

        sResMap.put(OtherSoundSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(OtherSoundSettings.class.getName()),
                        NO_DATA_RES_ID,
                        OtherSoundSettings.class.getName(),
                        R.drawable.ic_settings_notifications));

        sResMap.put(ZenModeSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(ZenModeSettings.class.getName()),
                        NO_DATA_RES_ID,
                        ZenModeSettings.class.getName(),
                        R.drawable.ic_settings_notifications));*/

        sResMap.put(Memory.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(Memory.class.getName()),
                        NO_DATA_RES_ID,
                        Memory.class.getName(),
                        R.drawable.ic_settings_storage));

        sResMap.put(UsbSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(UsbSettings.class.getName()),
                        R.xml.usb_settings,
                        UsbSettings.class.getName(),
                        R.drawable.ic_settings_storage));

        sResMap.put(PowerUsageSummary.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(PowerUsageSummary.class.getName()),
                        R.xml.power_usage_summary,
                        PowerUsageSummary.class.getName(),
                        R.drawable.ic_settings_battery));

        sResMap.put(BatterySaverSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(BatterySaverSettings.class.getName()),
                        R.xml.battery_saver_settings,
                        BatterySaverSettings.class.getName(),
                        R.drawable.ic_settings_battery));

        sResMap.put(ScreenlockPasswordSettings.class.getName(),
                new SearchIndexableResource(
                       Ranking.getRankForClassName(ScreenlockPasswordSettings.class.getName()),
                       R.xml.screenlock_password_settings,
                       ScreenlockPasswordSettings.class.getName(),
                       R.drawable.ic_settings_unlock_screen));

        sResMap.put(UserSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(UserSettings.class.getName()),
                        NO_DATA_RES_ID,
                        UserSettings.class.getName(),
                        R.drawable.ic_settings_multiuser));

        sResMap.put(LocationSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(LocationSettings.class.getName()),
                        R.xml.location_settings,
                        LocationSettings.class.getName(),
                        R.drawable.ic_settings_location));
        /*HQ_xupeixin at 2015-10-03 modified about search all preference in settings begin*/
        sResMap.put(NoDisturbSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(NoDisturbSettings.class.getName()),
                        NO_DATA_RES_ID,
                        NoDisturbSettings.class.getName(),
                        R.drawable.ic_settings_no_disturb));

        sResMap.put(NotificationCenterSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(NotificationCenterSettings.class.getName()),
                        NO_DATA_RES_ID,
                        NotificationCenterSettings.class.getName(),
                        R.drawable.ic_settings_notification_manager));

        sResMap.put(ProtectedAppSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(ProtectedAppSettings.class.getName()),
                        NO_DATA_RES_ID,
                        ProtectedAppSettings.class.getName(),
                        R.drawable.ic_settings_no_disturb));
        /*HQ_hanchao at 2015-10-22 modified  begin*/
        /*sResMap.put(StartupManagerSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(StartupManagerSettings.class.getName()),
                        NO_DATA_RES_ID,
                        StartupManagerSettings.class.getName(),
                        R.drawable.ic_settings_startup_manager));

        sResMap.put(PermissionMgrSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(PermissionMgrSettings.class.getName()),
                        NO_DATA_RES_ID,
                        PermissionMgrSettings.class.getName(),
                        R.drawable.ic_settings_permission_manager));*/
        /*HQ_hanchao at 2015-10-22 modified  end*/
        sResMap.put(CloudServiceSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(CloudServiceSettings.class.getName()),
                        NO_DATA_RES_ID,
                        CloudServiceSettings.class.getName(),
                        R.drawable.ic_cloud_settings));

        sResMap.put(NetAppMgrSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(NetAppMgrSettings.class.getName()),
                        NO_DATA_RES_ID,
                        NetAppMgrSettings.class.getName(),
                        R.drawable.ic_networked_apps));

        sResMap.put(DataUsageSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(DataUsageSettings.class.getName()),
                        NO_DATA_RES_ID,
                        DataUsageSettings.class.getName(),
                        R.drawable.ic_settings_data_usage));

        sResMap.put(GestureSettings.class.getName(),
            new SearchIndexableResource(
                        Ranking.getRankForClassName(GestureSettings.class.getName()),
                        NO_DATA_RES_ID,
                        GestureSettings.class.getName(),
                        R.drawable.ic_settings_motion_settings));

        sResMap.put(BatterySaveSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(BatterySaveSettings.class.getName()),
                        NO_DATA_RES_ID,
                        BatterySaveSettings.class.getName(),
                        R.drawable.ic_settings_powersaving));

        sResMap.put(AccountSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(AccountSettings.class.getName()),
                        R.xml.account_settings,
                        AccountSettings.class.getName(),
                        R.drawable.ic_settings_accounts));

        sResMap.put(ManageApplications.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(ManageApplications.class.getName()),
                        R.xml.application_settings,
                        ManageApplications.class.getName(),
                        R.drawable.ic_settings_applications));

        sResMap.put(KeylockGesturesSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(KeylockGesturesSettings.class.getName()),
                        R.xml.keylockgestures_settings,
                        KeylockGesturesSettings.class.getName(),
                        R.drawable.ic_settings_motion_settings));

        sResMap.put(VirtualKeySettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(VirtualKeySettings.class.getName()),
                        R.xml.virtualkey_settings,
                        VirtualKeySettings.class.getName(),
                        R.drawable.ic_settings_navigation_bar));

        sResMap.put(SuspendButtonSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(SuspendButtonSettings.class.getName()),
                        R.xml.suspend_button_settings,
                        SuspendButtonSettings.class.getName(),
                        R.drawable.ic_settings_suspended_button));

        sResMap.put(SmartEarphoneControlSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(SmartEarphoneControlSettings.class.getName()),
                        R.xml.smart_earphone_control_settings,
                        SmartEarphoneControlSettings.class.getName(),
                        R.drawable.ic_settings_smart_earphone));

        sResMap.put(AutoUpdateSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(AutoUpdateSettings.class.getName()),
                        NO_DATA_RES_ID,
                        AutoUpdateSettings.class.getName(),
                        R.drawable.ic_settings_phone_updates));
        /*HQ_xupeixin at 2015-10-03 modified end*/
        sResMap.put(SecuritySettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(SecuritySettings.class.getName()),
                        R.xml.security_settings,
                        SecuritySettings.class.getName(),
                        R.drawable.ic_settings_security));

        sResMap.put(InputMethodAndLanguageSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(InputMethodAndLanguageSettings.class.getName()),
                        R.xml.language_settings,
                        InputMethodAndLanguageSettings.class.getName(),
                        R.drawable.ic_settings_language));

        sResMap.put(VoiceInputSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(VoiceInputSettings.class.getName()),
                        R.xml.voice_input_settings,
                        VoiceInputSettings.class.getName(),
                        R.drawable.ic_settings_language));

        sResMap.put(PrivacySettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(PrivacySettings.class.getName()),
                        R.xml.privacy_settings,
                        PrivacySettings.class.getName(),
                        R.drawable.ic_settings_backup));

        sResMap.put(DateTimeSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(DateTimeSettings.class.getName()),
                        R.xml.date_time_prefs,
                        DateTimeSettings.class.getName(),
                        R.drawable.ic_settings_date_time));

        sResMap.put(AccessibilitySettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(AccessibilitySettings.class.getName()),
                        R.xml.accessibility_settings,
                        AccessibilitySettings.class.getName(),
                        R.drawable.ic_settings_accessibility));

        sResMap.put(DevelopmentSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(DevelopmentSettings.class.getName()),
                        R.xml.development_prefs,
                        DevelopmentSettings.class.getName(),
                        R.drawable.ic_settings_development));

        sResMap.put(DeviceInfoSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(DeviceInfoSettings.class.getName()),
                        R.xml.device_info_settings,
                        DeviceInfoSettings.class.getName(),
                        R.drawable.ic_settings_about));

        /// M: add for mtk feature(Settings is an entrance , has its separate apk,
        /// such as schedule power on/off) search function {@
        sResMap.put(SearchExt.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(SearchExt.class.getName()),
                        NO_DATA_RES_ID,
                        SearchExt.class.getName(),
                        R.drawable.ic_settings_schpwronoff));//HQ_jiangchao modified for HQ01499498 at 20151111
        /// @}

        sResMap.put(SoundSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(SoundSettings.class.getName()),
                        NO_DATA_RES_ID,
                        SoundSettings.class.getName(),
                        R.drawable.ic_settings_notifications));

        ///M: WFC @ {
        sResMap.put(WfcSettings.class.getName(),
                new SearchIndexableResource(
                        Ranking.getRankForClassName(WfcSettings.class.getName()),
                        R.xml.wfc_settings,
                        WfcSettings.class.getName(),
                        R.drawable.ic_settings_more));
        /// @}
    }

    private SearchIndexableResources() {
    }

    public static int size() {
        return sResMap.size();
    }

    public static SearchIndexableResource getResourceByName(String className) {
        return sResMap.get(className);
    }

    public static Collection<SearchIndexableResource> values() {
        return sResMap.values();
    }
}
