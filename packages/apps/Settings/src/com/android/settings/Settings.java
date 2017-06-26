/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.settings;

import com.android.settings.applications.AppOpsSummary;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;

import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.Window;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabSpec;
import android.widget.TabWidget;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import android.os.Bundle;
import android.content.Context;
import android.app.Activity;
import android.app.Fragment;


import com.android.settings.AllSettings;
import com.android.settings.CommonSettings;
import android.content.SharedPreferences;


/**
 * Top-level Settings activity
 */
public class Settings extends Activity {

    private static final String LOG_TAG = "Settings";
    public static final int COMMON_SETTINGS_TAB_INDEX = 0;
    public static final int ALL_SETTINGS_TAB_INDEX = 1;
    private int mSelectedTab;

    private ViewPager mViewPager;
    private TabsAdapter mTabsAdapter = null;
    private TabHost mTabHost;
    private SharedPreferences mSp;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_tabs);
        /* HQ_jiangchao at 2015-09-25  modified for open the tab settings from the last exit begin*/
        mSp = getSharedPreferences("commonSettings", Context.MODE_PRIVATE);
	    if(mSp == null) {
            mSelectedTab = COMMON_SETTINGS_TAB_INDEX;
	    }else {
            mSelectedTab = mSp.getInt("selectedTab", COMMON_SETTINGS_TAB_INDEX);
	    }
        /* HQ_jiangchao at 2015-09-25 modified end*/
        initViews();
    }

    private void initViews() {
        if (mTabsAdapter == null) {
            mTabHost = (TabHost)findViewById(android.R.id.tabhost);
            mTabHost.setup();
            mViewPager = (ViewPager)findViewById(R.id.pager);
            mTabsAdapter = new TabsAdapter(this, mTabHost,mViewPager);
            createTabs(mSelectedTab);
        }
    }

    private void createTabs(int selectedTab) {
        //Add common settings
        TabSpec commonTab = mTabHost.newTabSpec("common").setIndicator(getResources().getString(R.string.common_settings_title));
        mTabsAdapter.addTab(commonTab, CommonSettings.class, null, COMMON_SETTINGS_TAB_INDEX);
        //Add all settings
        TabSpec allTab = mTabHost.newTabSpec("common").setIndicator(getResources().getString(R.string.all_settings_title));
        mTabsAdapter.addTab(allTab, AllSettings.class, null, ALL_SETTINGS_TAB_INDEX);
        mTabHost.setCurrentTab(mSelectedTab);
    }

    /* HQ_jiangchao at 2015-09-25  modified for open the tab settings from the last exit begin*/
    @Override
    public void onStop() {
        super.onStop();
        if(mSp != null && mTabHost != null) {
            mSp.edit().putInt("selectedTab", mTabHost.getCurrentTab()).apply();
        }
    }
    /* HQ_jiangchao at 2015-09-25 modified end*/

    private static class TabsAdapter extends FragmentPagerAdapter 
            implements ViewPager.OnPageChangeListener, OnTabChangeListener{
        private List<TabInfo> mTabs = new ArrayList<TabInfo>();
        private Context mContext;
        private ViewPager mPager;
        private TabHost mTabHost;

        public TabsAdapter(Activity activity, TabHost tabHost, ViewPager viewPager) {
            super(activity.getFragmentManager());
            mContext = activity;
            mTabHost = tabHost;
            mTabHost.setOnTabChangedListener(this);
            mPager = viewPager;
            mPager.setAdapter(this);
            mPager.setOnPageChangeListener(this);
        }

        @Override
        public int getCount() {
            return mTabs.size();
        }

        @Override
        public Fragment getItem(int position) {
            TabInfo info = mTabs.get(position);
            Fragment f = Fragment.instantiate(mContext, info.clss.getName(), info.bundle);
            return f;
         }

         public void addTab (TabHost.TabSpec tab, Class<?>clss, Bundle bundle, int position) {
             tab.setContent(new DummyTabFactory(mContext));
             mTabHost.addTab(tab);
             TabInfo info = new TabInfo(position, clss, bundle);
             mTabs.add(info);
             notifyDataSetChanged();
         }

         static class DummyTabFactory implements TabHost.TabContentFactory {

             private final Context mContext;

             public DummyTabFactory(Context paramContext) {
                 this.mContext = paramContext;
             }

             public View createTabContent(String paramString) {
                 View localView = new View(this.mContext);
                 localView.setMinimumWidth(0);
                 localView.setMinimumHeight(0);
                 return localView;
             }
         }

         private final class TabInfo {

             private final int position;
             private final Class<?> clss;
             private final Bundle bundle;

             TabInfo (int position, Class<?>clss, Bundle bundle) {
                 this.position = position;
                 this.clss = clss;
                 this.bundle = bundle;
             }
         }

         @Override
         public void onTabChanged(String paramString) {
             int position = mTabHost.getCurrentTab();
             mPager.setCurrentItem(position);
         }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

        }

        @Override
        public void onPageSelected(int position) {
            mTabHost.setCurrentTab(position);
        }

        @Override
        public void onPageScrollStateChanged(int state) {

        }

    }


    /*
    * Settings subclasses for launching independently.
    */
    public static class BluetoothSettingsActivity extends SettingsActivity { /* empty */ }
    public static class WirelessSettingsActivity extends SettingsActivity { /* empty */ }
    public static class SimSettingsActivity extends SettingsActivity { /* empty */ }
    public static class TetherSettingsActivity extends SettingsActivity { /* empty */ }
    public static class VpnSettingsActivity extends SettingsActivity { /* empty */ }
    public static class DateTimeSettingsActivity extends SettingsActivity { /* empty */ }
    public static class StorageSettingsActivity extends SettingsActivity { /* empty */ }
    public static class WifiSettingsActivity extends SettingsActivity { /* empty */ }
    public static class WifiP2pSettingsActivity extends SettingsActivity { /* empty */ }
    public static class InputMethodAndLanguageSettingsActivity extends SettingsActivity { /* empty */ }
    public static class KeyboardLayoutPickerActivity extends SettingsActivity { /* empty */ }
    public static class InputMethodAndSubtypeEnablerActivity extends SettingsActivity { /* empty */ }
    public static class VoiceInputSettingsActivity extends SettingsActivity { /* empty */ }
    public static class SpellCheckersSettingsActivity extends SettingsActivity { /* empty */ }
    public static class LocalePickerActivity extends SettingsActivity { /* empty */ }
    public static class UserDictionarySettingsActivity extends SettingsActivity { /* empty */ }
    public static class HomeSettingsActivity extends SettingsActivity { /* empty */ }
    public static class DisplaySettingsActivity extends SettingsActivity { /* empty */ }
    public static class DeviceInfoSettingsActivity extends SettingsActivity { /* empty */ }
    public static class ApplicationSettingsActivity extends SettingsActivity { /* empty */ }
    public static class ManageApplicationsActivity extends SettingsActivity { /* empty */ }
    public static class AppOpsSummaryActivity extends SettingsActivity {
        @Override
        public boolean isValidFragment(String className) {
            if (AppOpsSummary.class.getName().equals(className)) {
                return true;
            }
            return super.isValidFragment(className);
            }
    }
    public static class StorageUseActivity extends SettingsActivity { /* empty */ }
    public static class DevelopmentSettingsActivity extends SettingsActivity { /* empty */ }
    public static class AccessibilitySettingsActivity extends SettingsActivity { /* empty */ }
    public static class CaptioningSettingsActivity extends SettingsActivity { /* empty */ }
    public static class AccessibilityInversionSettingsActivity extends SettingsActivity { /* empty */ }
    public static class AccessibilityContrastSettingsActivity extends SettingsActivity { /* empty */ }
    public static class AccessibilityDaltonizerSettingsActivity extends SettingsActivity { /* empty */ }
    public static class SecuritySettingsActivity extends SettingsActivity { /* empty */ }
    public static class ScreenLockSettingsActivity extends SettingsActivity { /* empty */ }//added by wangyi for Locked start.
    public static class FingerprintSettingsActivity extends SettingsActivity { /* empty */ } //added by xuweijie for fingerprint on 2015-06-18
    public static class ChildModeSettingsActivity extends SettingsActivity { /* empty */ } //add by lihaizhou for ChildMode at 2015-07-14
    public static class UsageAccessSettingsActivity extends SettingsActivity { /* empty */ }
    public static class LocationSettingsActivity extends SettingsActivity { /* empty */ }
    public static class PrivacySettingsActivity extends SettingsActivity { /* empty */ }
    public static class RunningServicesActivity extends SettingsActivity { /* empty */ }
    public static class ManageAccountsSettingsActivity extends SettingsActivity { /* empty */ }
    public static class PowerUsageSummaryActivity extends SettingsActivity { /* empty */ }
    public static class BatterySaverSettingsActivity extends SettingsActivity { /* empty */ }
    public static class AccountSyncSettingsActivity extends SettingsActivity { /* empty */ }
    public static class AccountSettingsActivity extends SettingsActivity { /* empty */ }
    public static class AccountSyncSettingsInAddAccountActivity extends SettingsActivity { /* empty */ }
    public static class CryptKeeperSettingsActivity extends SettingsActivity { /* empty */ }
    public static class DeviceAdminSettingsActivity extends SettingsActivity { /* empty */ }
    public static class DataUsageSummaryActivity extends SettingsActivity { /* empty */ }
    public static class CellularDataSettingsActivity extends SettingsActivity { /* empty */ }
    public static class AdvancedWifiSettingsActivity extends SettingsActivity { /* empty */ }
    public static class SavedAccessPointsSettingsActivity extends SettingsActivity { /* empty */ }
    public static class TextToSpeechSettingsActivity extends SettingsActivity { /* empty */ }
    public static class AndroidBeamSettingsActivity extends SettingsActivity { /* empty */ }
    public static class WifiDisplaySettingsActivity extends SettingsActivity { /* empty */ }
    public static class DreamSettingsActivity extends SettingsActivity { /* empty */ }
    public static class NotificationStationActivity extends SettingsActivity { /* empty */ }
    public static class UserSettingsActivity extends SettingsActivity { /* empty */ }
    public static class NotificationAccessSettingsActivity extends SettingsActivity { /* empty */ }
    public static class ConditionProviderSettingsActivity extends SettingsActivity { /* empty */ }
    public static class UsbSettingsActivity extends SettingsActivity { /* empty */ }
    public static class TrustedCredentialsSettingsActivity extends SettingsActivity { /* empty */ }
    public static class PrintSettingsActivity extends SettingsActivity { /* empty */ }
    public static class PrintJobSettingsActivity extends SettingsActivity { /* empty */ }
    public static class ZenModeSettingsActivity extends SettingsActivity { /* empty */ }
    public static class NotificationSettingsActivity extends SettingsActivity { /* empty */ }
    public static class NotificationAppListActivity extends SettingsActivity { /* empty */ }
    public static class AppNotificationSettingsActivity extends SettingsActivity { /* empty */ }
    public static class OtherSoundSettingsActivity extends SettingsActivity { /* empty */ }
    public static class QuickLaunchSettingsActivity extends SettingsActivity { /* empty */ }

    public static class TopLevelSettings extends SettingsActivity { /* empty */ }
    public static class ApnSettingsActivity extends SettingsActivity { /* empty */ }
    public static class TetherWifiSettingsActivity extends SettingsActivity { /* empty */ }
    /**M: Add new for settings activity @{*/
    public static class HDMISettingsActivity extends SettingsActivity { /* empty */ }
    public static class BeamShareHistoryActivity extends SettingsActivity { /* empty */ }
    public static class NfcSettingsActivity extends SettingsActivity { /*empty */ }
    public static class HotKnotSettingsActivity extends SettingsActivity { /*empty */ }
    public static class SubSelectActivity extends SettingsActivity { /* empty */ }
    public static class AudioProfileSettingsActivity extends SettingsActivity { /* empty */ }
    public static class SoundEnhancementActivity extends SettingsActivity { /* empty */ }
    public static class WifiDisplaySinkActivity extends SettingsActivity { /* empty */ }
    ///M: WFC @ {
    public static class WfcSettingsActivity extends SettingsActivity { /*empty */ }
    public static class WfcTutorialActivity extends SettingsActivity { /* empty */}
    public static class WifiGprsSelectorActivity extends SettingsActivity {  /*empty */  }
    public static class WfcInvalidSimDialogActivity extends SettingsActivity { /*empty */ }
    /// @}
    
    /**@}*/
    /*HQ_yuankangbo 2015-08-11 modify for suspend button settings*/
    public static class SuspendButtonSettingsActivity extends SettingsActivity {  /*empty */  }
    /*HQ_daiwenqiang 20151224 modify for HQ01588720 start*/
    public static class SmartMoreSettingsActivity extends SettingsActivity { /*empty */ }
    public static class TouchProofSettingsActivity extends SettingsActivity { /*empty */ }
    /*HQ_daiwenqiang 20151224 modify for HQ01588720 end*/
 //HQ_zhangteng added for SmartEarphoneControl at 2015-09-09
    public static class SmartEarphoneControlActivity extends SettingsActivity {  /*empty */  }
    
    public static class SoundSettingsActivity extends SettingsActivity { /* empty */ }
}
