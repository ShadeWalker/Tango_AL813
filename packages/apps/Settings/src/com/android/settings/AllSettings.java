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

package com.android.settings;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.INetworkManagementService;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.transition.TransitionManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SearchView;

import com.android.internal.util.ArrayUtils;
import com.android.internal.util.XmlUtils;
import com.android.settings.accessibility.AccessibilitySettings;
import com.android.settings.accessibility.CaptionPropertiesFragment;
import com.android.settings.accounts.AccountSettings;
import com.android.settings.accounts.AccountSyncSettings;
import com.android.settings.applications.InstalledAppDetails;
import com.android.settings.applications.ManageApplications;
import com.android.settings.applications.ProcessStatsUi;
import com.android.settings.bluetooth.BluetoothSettings;
import com.android.settings.dashboard.DashboardCategory;
import com.android.settings.dashboard.DashboardSummary;
import com.android.settings.dashboard.DashboardTile;
import com.android.settings.dashboard.NoHomeDialogFragment;
import com.android.settings.dashboard.SearchResultsSummary;
import com.android.settings.deviceinfo.Memory;
import com.android.settings.deviceinfo.UsbSettings;
import com.android.settings.fuelgauge.BatterySaverSettings;
import com.android.settings.fuelgauge.PowerUsageSummary;
import com.android.settings.notification.NotificationAppList;
import com.android.settings.notification.OtherSoundSettings;
import com.android.settings.quicklaunch.QuickLaunchSettings;
import com.android.settings.search.DynamicIndexableContentMonitor;
import com.android.settings.search.Index;
import com.android.settings.sim.SimSettings;
import com.android.settings.inputmethod.InputMethodAndLanguageSettings;
import com.android.settings.inputmethod.KeyboardLayoutPickerFragment;
import com.android.settings.inputmethod.SpellCheckersSettings;
import com.android.settings.inputmethod.UserDictionaryList;
import com.android.settings.location.LocationSettings;
import com.android.settings.nfc.AndroidBeam;
import com.android.settings.nfc.PaymentSettings;
import com.android.settings.notification.AppNotificationSettings;
import com.android.settings.notification.ConditionProviderSettings;
import com.android.settings.notification.NotificationAccessSettings;
import com.android.settings.notification.NotificationSettings;
import com.android.settings.notification.NotificationStation;
import com.android.settings.notification.ZenModeSettings;
import com.android.settings.print.PrintJobSettingsFragment;
import com.android.settings.print.PrintSettingsFragment;
import com.android.settings.tts.TextToSpeechSettings;
import com.android.settings.users.UserSettings;
import com.android.settings.voice.VoiceInputSettings;
import com.android.settings.vpn2.VpnSettings;
import com.android.settings.wfd.WifiDisplaySettings;
import com.android.settings.widget.SwitchBar;
import com.android.settings.wifi.AdvancedWifiSettings;
import com.android.settings.wifi.SavedAccessPointsWifiSettings;
import com.android.settings.wifi.WifiSettings;
import com.android.settings.wifi.p2p.WifiP2pSettings;

import com.mediatek.audioprofile.AudioProfileSettings;
import com.mediatek.audioprofile.SoundEnhancement;
import com.mediatek.audioprofile.SoundSettings;
import com.mediatek.audioprofile.SubSelectSettings;
import com.mediatek.beam.BeamShareHistory;
import com.mediatek.hdmi.HDMISettings;
import com.mediatek.hotknot.HotKnotAdapter;
import com.mediatek.nfc.CardEmulationSettings;
import com.mediatek.nfc.MtkAndroidBeam;
import com.mediatek.nfc.NfcSettings;
import com.mediatek.settings.ext.ISettingsMiscExt;
import com.mediatek.settings.hotknot.HotKnotSettings;
import com.mediatek.settings.UtilsExt;
import com.mediatek.settings.wfd.WfdSinkSurfaceFragment;
import com.mediatek.wifi.WifiGprsSelector;
import com.mediatek.wifi.hotspot.TetherWifiSettings;
import android.telephony.SubscriptionManager;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.content.res.Resources;
import com.android.settings.dashboard.DashboardTileView;
import com.mediatek.settings.UtilsExt;
import com.mediatek.settings.ext.ISettingsMiscExt;
import android.widget.ImageView;
import android.os.SystemProperties;
import com.android.settings.bluetooth.LocalBluetoothAdapter;
import com.android.settings.bluetooth.LocalBluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import static com.android.settings.dashboard.DashboardTile.TILE_ID_UNDEFINED;
import com.android.settings.SettingsPreferenceFragment;

/*HQ_yangfengqing 2015-9-22 modified for wifi is connected and network enabled show ssid begin*/
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
/* HQ_yangfengqing 2015-9-22 modified end*/
/*modified by maolikui at 2015-12-29 */
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.provider.Settings;
public class AllSettings extends SettingsPreferenceFragment
        implements PreferenceManager.OnPreferenceTreeClickListener,
        PreferenceFragment.OnPreferenceStartFragmentCallback,
        ButtonBarHandler, FragmentManager.OnBackStackChangedListener,
        SearchView.OnQueryTextListener, SearchView.OnCloseListener,
        MenuItem.OnActionExpandListener {

    private static final String LOG_TAG = "Settings";
    private Activity mActivity;

    ///M: change backup reset title
    private ISettingsMiscExt mExt;

    //add by wanghui for al812 unknown ssid
    private final String defaultString = "<unknown ssid>";
    private int hwNewSystemUpdate = 0;//add by maolikui at 2015-12-29
    private ContentResolver mResolver;
    private ChangeObserver mObserver;
    // Constants for state save/restore
    private static final String SAVE_KEY_CATEGORIES = ":settings:categories";
    private static final String SAVE_KEY_SEARCH_MENU_EXPANDED = ":settings:search_menu_expanded";
    private static final String SAVE_KEY_SEARCH_QUERY = ":settings:search_query";
    private static final String SAVE_KEY_SHOW_HOME_AS_UP = ":settings:show_home_as_up";
    private static final String SAVE_KEY_SHOW_SEARCH = ":settings:show_search";
    private static final String SAVE_KEY_HOME_ACTIVITIES_COUNT = ":settings:home_activities_count";

    /**
     * When starting this activity, the invoking Intent can contain this extra
     * string to specify which fragment should be initially displayed.
     * <p/>Starting from Key Lime Pie, when this argument is passed in, the activity
     * will call isValidFragment() to confirm that the fragment class name is valid for this
     * activity.
     */
    public static final String EXTRA_SHOW_FRAGMENT = ":settings:show_fragment";

    /**
     * When starting this activity and using {@link #EXTRA_SHOW_FRAGMENT},
     * this extra can also be specified to supply a Bundle of arguments to pass
     * to that fragment when it is instantiated during the initial creation
     * of the activity.
     */
    public static final String EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":settings:show_fragment_args";

    /**
     * Fragment "key" argument passed thru {@link #EXTRA_SHOW_FRAGMENT_ARGUMENTS}
     */
    public static final String EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key";

    public static final String BACK_STACK_PREFS = ":settings:prefs";

    // extras that allow any preference activity to be launched as part of a wizard

    // show Back and Next buttons? takes boolean parameter
    // Back will then return RESULT_CANCELED and Next RESULT_OK
    protected static final String EXTRA_PREFS_SHOW_BUTTON_BAR = "extra_prefs_show_button_bar";

    // add a Skip button?
    private static final String EXTRA_PREFS_SHOW_SKIP = "extra_prefs_show_skip";

    // specify custom text for the Back or Next buttons, or cause a button to not appear
    // at all by setting it to null
    protected static final String EXTRA_PREFS_SET_NEXT_TEXT = "extra_prefs_set_next_text";
    protected static final String EXTRA_PREFS_SET_BACK_TEXT = "extra_prefs_set_back_text";

    /**
     * When starting this activity and using {@link #EXTRA_SHOW_FRAGMENT},
     * those extra can also be specify to supply the title or title res id to be shown for
     * that fragment.
     */
    public static final String EXTRA_SHOW_FRAGMENT_TITLE = ":settings:show_fragment_title";
    /**
     * The package name used to resolve the title resource id.
     */
    public static final String EXTRA_SHOW_FRAGMENT_TITLE_RES_PACKAGE_NAME =
            ":settings:show_fragment_title_res_package_name";
    public static final String EXTRA_SHOW_FRAGMENT_TITLE_RESID =
            ":settings:show_fragment_title_resid";
    public static final String EXTRA_SHOW_FRAGMENT_AS_SHORTCUT =
            ":settings:show_fragment_as_shortcut";

    public static final String EXTRA_SHOW_FRAGMENT_AS_SUBSETTING =
            ":settings:show_fragment_as_subsetting";

    private static final String META_DATA_KEY_FRAGMENT_CLASS =
        "com.android.settings.FRAGMENT_CLASS";

    private static final String EXTRA_UI_OPTIONS = "settings:ui_options";

    private static final String EMPTY_QUERY = "";

    private static final String CUSTOMIZE_ITEM_INDEX = "customize_item_index";

    private static boolean sShowNoHomeNotice = false;

    private String mFragmentClass;
    private ViewGroup mDashboard;
	private LayoutInflater mLayoutInflater;

    private CharSequence mInitialTitle;
    private int mInitialTitleResId;
    private boolean mIsWifiOnly = false;

    // Show only these settings for restricted users
    private int[] SETTINGS_FOR_RESTRICTED = {
            R.id.wireless_section,
            R.id.wifi_settings,
            R.id.bluetooth_settings,
            R.id.data_usage_settings,
            R.id.sim_settings,
            R.id.wireless_settings,
            R.id.device_section,
            R.id.notification_settings,
            R.id.display_settings,
            R.id.storage_settings,
            R.id.application_settings,
            R.id.battery_settings,
            R.id.personal_section,
            R.id.location_settings,
            R.id.security_settings,
            R.id.language_settings,
            R.id.user_settings,
            R.id.account_settings,
            R.id.system_section,
            R.id.date_time_settings,
            R.id.about_settings,
            R.id.accessibility_settings,
            //R.id.print_settings,  //hanchao remove print menu
            R.id.nfc_payment_settings,
            R.id.home_settings,
            R.id.dashboard,
//            R.id.power_settings,
            R.id.hotknot_settings
    };

    private static final String[] ENTRY_FRAGMENTS = {
            WirelessSettings.class.getName(),
            WifiSettings.class.getName(),
            AdvancedWifiSettings.class.getName(),
            SavedAccessPointsWifiSettings.class.getName(),
            BluetoothSettings.class.getName(),
            SimSettings.class.getName(),
            SubSelectSettings.class.getName(),
            TetherSettings.class.getName(),
            WifiP2pSettings.class.getName(),
            VpnSettings.class.getName(),
            DateTimeSettings.class.getName(),
            LocalePicker.class.getName(),
            InputMethodAndLanguageSettings.class.getName(),
            VoiceInputSettings.class.getName(),
            SpellCheckersSettings.class.getName(),
            UserDictionaryList.class.getName(),
            UserDictionarySettings.class.getName(),
            HomeSettings.class.getName(),
            DisplaySettings.class.getName(),
            DeviceInfoSettings.class.getName(),
            ManageApplications.class.getName(),
            ProcessStatsUi.class.getName(),
            NotificationStation.class.getName(),
            LocationSettings.class.getName(),
            SecuritySettings.class.getName(),
            UsageAccessSettings.class.getName(),
            PrivacySettings.class.getName(),
            DeviceAdminSettings.class.getName(),
            AccessibilitySettings.class.getName(),
            CaptionPropertiesFragment.class.getName(),
            com.android.settings.accessibility.ToggleDaltonizerPreferenceFragment.class.getName(),
            TextToSpeechSettings.class.getName(),
            Memory.class.getName(),
            DevelopmentSettings.class.getName(),
            UsbSettings.class.getName(),
            AndroidBeam.class.getName(),
            WifiDisplaySettings.class.getName(),
            PowerUsageSummary.class.getName(),
            AccountSyncSettings.class.getName(),
            AccountSettings.class.getName(),
            CryptKeeperSettings.class.getName(),
            DataUsageSummary.class.getName(),
            DreamSettings.class.getName(),
            UserSettings.class.getName(),
            NotificationAccessSettings.class.getName(),
            ConditionProviderSettings.class.getName(),
            PrintSettingsFragment.class.getName(),
            PrintJobSettingsFragment.class.getName(),
            TrustedCredentialsSettings.class.getName(),
            PaymentSettings.class.getName(),
            KeyboardLayoutPickerFragment.class.getName(),
            ZenModeSettings.class.getName(),
            NotificationSettings.class.getName(),
            ChooseLockPassword.ChooseLockPasswordFragment.class.getName(),
            ChooseLockPattern.ChooseLockPatternFragment.class.getName(),
            InstalledAppDetails.class.getName(),
            BatterySaverSettings.class.getName(),
            NotificationAppList.class.getName(),
            AppNotificationSettings.class.getName(),
            OtherSoundSettings.class.getName(),
            QuickLaunchSettings.class.getName(),
            ApnSettings.class.getName(),
            TetherWifiSettings.class.getName(),
            HDMISettings.class.getName(),
            NfcSettings.class.getName(),
            BeamShareHistory.class.getName(),
            CardEmulationSettings.class.getName(),
            MtkAndroidBeam.class.getName(),
            HotKnotSettings.class.getName(),
            AudioProfileSettings.class.getName(),
            WfdSinkSurfaceFragment.class.getName(),
            WifiGprsSelector.class.getName(),
            SoundEnhancement.class.getName(),
			SoundSettings.class.getName()
    };


    private static final String[] LIKE_SHORTCUT_INTENT_ACTION_ARRAY = {
            "android.settings.APPLICATION_DETAILS_SETTINGS"
    };

    private SharedPreferences mDevelopmentPreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener mDevelopmentPreferencesListener;

    private boolean mBatteryPresent = true;
    private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                boolean batteryPresent = Utils.isBatteryPresent(intent);

                if (mBatteryPresent != batteryPresent) {
                    mBatteryPresent = batteryPresent;
                    invalidateCategories(true);
                }
            }

            /*HQ_yangfengqing 2015-9-22 modified for wifi is connected and network enabled show ssid */
            //WIFI
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)
                    || WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)
                    || WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)
                    //Bluetooth
                    ||BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                Log.d(LOG_TAG, "onReceive, action: " + action);
                updateWifiOrBluetoothState();
            }
            /* HQ_yangfengqing 2015-9-22 modified end*/
        }
    };

    private final DynamicIndexableContentMonitor mDynamicIndexableContentMonitor =
            new DynamicIndexableContentMonitor();

    //private ActionBar mActionBar;
   // private SwitchBar mSwitchBar;

    private Button mNextButton;

    private boolean mDisplayHomeAsUpEnabled;
    private boolean mDisplaySearch;

    private boolean mIsShowingDashboard;
    private boolean mIsShortcut;

    private ViewGroup mContent;

    private SearchView mSearchView;
    private SearchView mSearchView2;
    private MenuItem mSearchMenuItem;
    private boolean mSearchMenuItemExpanded = false;
    private SearchResultsSummary mSearchResultsFragment;
    private String mSearchQuery;

    // Categories
    private ArrayList<DashboardCategory> mCategories = new ArrayList<DashboardCategory>();

    private static final String MSG_DATA_FORCE_REFRESH = "msg_data_force_refresh";
    private static final int MSG_BUILD_CATEGORIES = 1;
    private static final int MSG_UPDATE_WIFI_OR_BLE_STATE = 2;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_BUILD_CATEGORIES: {
                    final boolean forceRefresh = msg.getData().getBoolean(MSG_DATA_FORCE_REFRESH);
                    if (forceRefresh) {
						Log.d(LOG_TAG, "forceRefresh");
                        buildDashboardCategories(mCategories);
                    }
                } break;

                /*HQ_yangfengqing 2015-9-22 modified for wifi is connected and network enabled show ssid begin*/
                case MSG_UPDATE_WIFI_OR_BLE_STATE:
                    rebuildUI();
                    break;
                /*HQ_yangfengqing 2015-9-22 modified end*/
            }
        }
    };

    public static boolean mNeedToRevertToInitialFragment = false;
    private int mHomeActivitiesCount = 1;

    private Intent mResultIntentData;

    //public SwitchBar getSwitchBar() {
    //    return mSwitchBar;
    //}

    public List<DashboardCategory> getDashboardCategories(boolean forceRefresh) {
        if (forceRefresh || mCategories.size() == 0) {
			Log.d(LOG_TAG, "getDashboardCategories()");
            buildDashboardCategories(mCategories);
        }
        return mCategories;
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
        // Override the fragment title for Wallpaper settings
        int titleRes = pref.getTitleRes();
        if (pref.getFragment().equals(WallpaperTypeSettings.class.getName())) {
            titleRes = R.string.wallpaper_settings_fragment_title;
        } else if (pref.getFragment().equals(OwnerInfoSettings.class.getName())
                && UserHandle.myUserId() != UserHandle.USER_OWNER) {
            if (UserManager.get(mActivity).isLinkedUser()) {
                titleRes = R.string.profile_info_settings_title;
            } else {
                titleRes = R.string.user_info_settings_title;
            }
        } else if (pref.getFragment().equals(HDMISettings.class.getName())) {
            titleRes = -1; // Just use titleText, because title has changed
        }
        startPreferencePanel(pref.getFragment(), pref.getExtras(), titleRes, pref.getTitle(),
                null, 0);
        return true;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        return false;
    }

    private void invalidateCategories(boolean forceRefresh) {
        if (!mHandler.hasMessages(MSG_BUILD_CATEGORIES)) {
            Message msg = new Message();
            msg.what = MSG_BUILD_CATEGORIES;
            msg.getData().putBoolean(MSG_DATA_FORCE_REFRESH, forceRefresh);
        }
    }

    /*HQ_yangfengqing 2015-9-22 modified for wifi is connected and network enabled show ssid begin */
    private void updateWifiOrBluetoothState() {
        if (!mHandler.hasMessages(MSG_UPDATE_WIFI_OR_BLE_STATE)) {
            Message msg = new Message();
            msg.what = MSG_UPDATE_WIFI_OR_BLE_STATE;
            if (mHandler != null)
                mHandler.sendMessage(msg);
        }
    }
    /* HQ_yangfengqing 2015-9-22 modified end*/

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Index.getInstance(mActivity).update();
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState); 
    }

    @Override
    public void onAttach(Activity paramActivity)
    {
        super.onAttach(paramActivity);
        this.mActivity = paramActivity;
		Log.d(LOG_TAG, "mActivity:" + mActivity);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mNeedToRevertToInitialFragment) {
            try {
				revertToInitialFragment();
			} catch (IllegalStateException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mDisplaySearch) {
            return false;
        }

        MenuInflater inflater = mActivity.getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);

        // Cache the search query (can be overriden by the OnQueryTextListener)
        final String query = mSearchQuery;

        mSearchMenuItem = menu.findItem(R.id.search);
        mSearchView = (SearchView) mSearchMenuItem.getActionView();

        if (mSearchMenuItem == null || mSearchView == null) {
            return false;
        }

        if (mSearchResultsFragment != null) {
            mSearchResultsFragment.setSearchView(mSearchView);
        }

        mSearchMenuItem.setOnActionExpandListener(this);
        mSearchView.setOnQueryTextListener(this);
        mSearchView.setOnCloseListener(this);

        if (mSearchMenuItemExpanded) {
            mSearchMenuItem.expandActionView();
        }
        mSearchView.setQuery(query, true /* submit */);

        return true;
    }

    private static boolean isShortCutIntent(final Intent intent) {
        Set<String> categories = intent.getCategories();
        return (categories != null) && categories.contains("com.android.settings.SHORTCUT");
    }

    private static boolean isLikeShortCutIntent(final Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return false;
        }
        for (int i = 0; i < LIKE_SHORTCUT_INTENT_ACTION_ARRAY.length; i++) {
            if (LIKE_SHORTCUT_INTENT_ACTION_ARRAY[i].equals(action)) return true;
        }
        return false;
    }

	@Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onCreateView()");
        // The last two arguments ensure LayoutParams are inflated
        // properly.
        mIsShowingDashboard = true;
		mLayoutInflater = inflater;
        View rootView = inflater.inflate(mIsShowingDashboard ?
                R.layout.settings_main_dashboard : R.layout.settings_main_prefs, container, false);
		mContent =(ViewGroup) rootView.findViewById(R.id.main_content);
        mDashboard = (ViewGroup) rootView.findViewById(R.id.dashboard_container);
        mSearchView2 = (SearchView) rootView.findViewById(R.id.search_view);
        /*modified by maolikui at 2015-12-29*/
	mResolver = this.getActivity().getApplicationContext().getContentResolver();
        mObserver = new ChangeObserver(new Handler());
        hwNewSystemUpdate = Settings.System.getInt(mResolver, "hw_new_system_update", 0);
        Log.i("maolikui onCreateView hwNewSystemUpdate",hwNewSystemUpdate + "");
        mResolver.registerContentObserver(Settings.System.getUriFor("hw_new_system_update"), true,
				mObserver);
        return rootView;
    }

	  private void rebuildUI() {
        if (!isAdded()) {
            Log.w(LOG_TAG, "Cannot build the DashboardSummary UI yet as the Fragment is not added");
            return;
        }

        long start = System.currentTimeMillis();
        final Resources res = mActivity.getResources();

        mDashboard.removeAllViews();

        List<DashboardCategory> categories = getDashboardCategories(true);

        final int count = categories.size();
		Log.d(LOG_TAG, "rebuildUI get count: " + count);

        for (int n = 0; n < count; n++) {
            DashboardCategory category = categories.get(n);

            View categoryView = mLayoutInflater.inflate(R.layout.dashboard_category, mDashboard,
                    false);

            TextView categoryLabel = (TextView) categoryView.findViewById(R.id.category_title);
            categoryLabel.setText(category.getTitle(res));

            ViewGroup categoryContent =
                    (ViewGroup) categoryView.findViewById(R.id.category_content);

            final int tilesCount = category.getTilesCount();
            for (int i = 0; i < tilesCount; i++) {
                DashboardTile tile = category.getTile(i);

                DashboardTileView tileView = new DashboardTileView(mActivity);
                updateTileView(mActivity, res, tile, tileView.getImageView(),
                        tileView.getTitleTextView(), tileView.getStatusTextView(), tileView.getPreferenceTextView());

                tileView.setTile(tile);
                if(tile != null && tile.extras != null && tile.extras.containsKey(CUSTOMIZE_ITEM_INDEX)){
                    int index = tile.extras.getInt(CUSTOMIZE_ITEM_INDEX, -1);
                    categoryContent.addView(tileView, index);
                } else {
                    categoryContent.addView(tileView);
                }
            }

            // Add the category
            mDashboard.addView(categoryView);
        }
        long delta = System.currentTimeMillis() - start;
        Log.d(LOG_TAG, "rebuildUI took: " + delta + " ms");
    }
    /**
     * modified by maolikui at 2015-12-29
     * 
     */
    private class ChangeObserver extends ContentObserver{

		public ChangeObserver(Handler handler) {
			super(handler);
			// TODO Auto-generated constructor stub
		}

		@Override
		public void onChange(boolean selfChange) {
			// TODO Auto-generated method stub
			//super.onChange(selfChange);
			Log.i("maolikui onChange0 hwNewSystemUpdate",hwNewSystemUpdate + "");
			Log.i("maolikui onChange mResolver",mResolver + "");
			hwNewSystemUpdate= Settings.System.getInt(mResolver, "hw_new_system_update", 0);
                        Log.i("maolikui onChange1 hwNewSystemUpdate",hwNewSystemUpdate + "");
			rebuildUI();
			
		}
	}
     
    private void updateTileView(Context context, Resources res, DashboardTile tile,
            ImageView tileIcon, TextView tileTextView, TextView statusTextView, TextView prefStatusTextView) {

        if (tile.iconRes > 0) {
            tileIcon.setImageResource(tile.iconRes);
        } else {
            //TODO:: Ask HIll to re-do this part
            mExt.customizeDashboardTile(tile, null, null, tileIcon, 2);
        }

        if (tile.id == R.id.bluetooth_settings) {
            updateBluetoothStatus(context, tile, prefStatusTextView);
		}

		if (tile.id == R.id.wifi_settings) {
			updateWifiStatus(context, tile, prefStatusTextView);

		}
        ///M: feature replace sim to uim
        tileTextView.setText(mExt.customizeSimDisplayString(
            tile.getTitle(res).toString(), SubscriptionManager.INVALID_SUBSCRIPTION_ID));

        CharSequence summary = tile.getSummary(res);
        if (!TextUtils.isEmpty(summary)) {
            statusTextView.setVisibility(View.VISIBLE);
            statusTextView.setText(summary);
        } else {
            statusTextView.setVisibility(View.GONE);
        }
        //modified by maolikui at 2015-12-31
        if(tile.id == R.id.system_update_settings && hwNewSystemUpdate != 0){
            prefStatusTextView.setBackgroundResource(R.drawable.ic_notification);
        }
    }

    private void updateWifiStatus(Context context, DashboardTile tile, TextView prefStatusTextView) {
        WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        final int state = manager.getWifiState();
        WifiInfo wifiInfo = manager.getConnectionInfo();
        /*HQ_yangfengqing 2015-9-22 modified for wifi is connected and network enabled show ssid begin */
        ConnectivityManager connManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiWorkInfo = connManager
                .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        boolean isWifiConnSucc = (null != wifiWorkInfo)
                && (wifiWorkInfo.isAvailable()) && (wifiWorkInfo.isConnected());
        Log.d(LOG_TAG, "updateWifiStatus, isWifiConnSucc = " + isWifiConnSucc);
        handleWifiStateChanged(state, wifiInfo, prefStatusTextView, isWifiConnSucc);
        /* HQ_yangfengqing 2015-9-22 modified end*/
    }

    /*HQ_yangfengqing 2015-9-22 modified for wifi is connected and network enabled show ssid */
    private void handleWifiStateChanged(int state, WifiInfo wifiInfo, TextView prefStatusTextView, boolean isWifiConnSucc) {
        Log.d(LOG_TAG, "handleWifiStateChanged, state = " + state);
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLING:
            case WifiManager.WIFI_STATE_ENABLED:
                /*HQ_yangfengqing 2015-9-22 modified for wifi is connected and network enabled show ssid */
                if(wifiInfo != null && isWifiConnSucc) {
                    String ssid = wifiInfo.getSSID();
                    String ssidStr = ssid.replaceAll("\"","");
                    Log.d(LOG_TAG, "handleWifiStateChanged, ssid = " + ssidStr);
                    if (ssidStr != null && (!"0x".equals(ssidStr)) && (!ssidStr.equals(defaultString))) {
                        prefStatusTextView.setText(ssidStr);
                    }else {
                        prefStatusTextView.setText(R.string.wifi_setup_not_connected);
                    }
                } else {
                    prefStatusTextView.setText(R.string.wifi_setup_not_connected);
                }
                break;
            case WifiManager.WIFI_STATE_DISABLING:
            case WifiManager.WIFI_STATE_DISABLED:
            default:
                prefStatusTextView.setText(R.string.switch_off_text);
        }
    }

	private void updateBluetoothStatus(Context context, DashboardTile tile, TextView prefStatusTextView) {
        LocalBluetoothManager manager = LocalBluetoothManager.getInstance(context);
		LocalBluetoothAdapter localAdapter = manager.getBluetoothAdapter();
		handleStateChanged(localAdapter.getBluetoothState(), prefStatusTextView);
	}

	 void handleStateChanged(int state, TextView prefStatusTextView) {
        switch (state) {
            case BluetoothAdapter.STATE_TURNING_ON:
            case BluetoothAdapter.STATE_ON:
				prefStatusTextView.setText(R.string.switch_on_text);
                Log.d(LOG_TAG, "turn bluetooth on");
                break;
            case BluetoothAdapter.STATE_TURNING_OFF:
            case BluetoothAdapter.STATE_OFF:
				prefStatusTextView.setText(R.string.switch_off_text);
                Log.d(LOG_TAG, "turn bluetooth off");
                /// @}
                break;
            default:
				Log.d(LOG_TAG, "By default, turn bluetooth off");
				prefStatusTextView.setText(R.string.switch_off_text);
        }
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
		Log.d(LOG_TAG, "onCreate()");
        mExt = UtilsExt.getMiscPlugin(mActivity);
        mIsWifiOnly = Utils.isWifiOnly(mActivity);

        // Should happen before any call to getIntent()
        getMetaData();

        final Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_UI_OPTIONS)) {
            mActivity.getWindow().setUiOptions(intent.getIntExtra(EXTRA_UI_OPTIONS, 0));
        }

        mDevelopmentPreferences = mActivity.getSharedPreferences(DevelopmentSettings.PREF_FILE,
                Context.MODE_PRIVATE);

        // Getting Intent properties can only be done after the super.onCreate(...)
        final String initialFragmentName = intent.getStringExtra(EXTRA_SHOW_FRAGMENT);

        mIsShortcut = isShortCutIntent(intent) || isLikeShortCutIntent(intent) ||
                intent.getBooleanExtra(EXTRA_SHOW_FRAGMENT_AS_SHORTCUT, false);

        final ComponentName cn = intent.getComponent();
        final String className = cn.getClassName();

        //mIsShowingDashboard = className.equals(Settings.class.getName());
        mIsShowingDashboard = true;

        // This is a "Sub Settings" when:
        // - this is a real SubSettings
        // - or :settings:show_fragment_as_subsetting is passed to the Intent
        final boolean isSubSettings = className.equals(SubSettings.class.getName()) ||
                intent.getBooleanExtra(EXTRA_SHOW_FRAGMENT_AS_SUBSETTING, false);

        // If this is a sub settings, then apply the SubSettings Theme for the ActionBar content insets
        if (isSubSettings) {
            // Check also that we are not a Theme Dialog as we don't want to override them
            final int themeResId = mActivity.getThemeResId();
            if (themeResId != R.style.Theme_DialogWhenLarge &&
                    themeResId != R.style.Theme_SubSettingsDialogWhenLarge) {
                mActivity.setTheme(R.style.Theme_SubSettings);
            }
        }

        //mActivity.setContentView(mIsShowingDashboard ?
        //        R.layout.settings_main_dashboard : R.layout.settings_main_prefs);

        //mContent = (ViewGroup) mActivity.findViewById(R.id.tabcontent);

        getFragmentManager().addOnBackStackChangedListener(this);

        if (mIsShowingDashboard) {
            Index.getInstance(mActivity.getApplicationContext()).update();
        }

        if (savedState != null) {
            // We are restarting from a previous saved state; used that to initialize, instead
            // of starting fresh.
            mSearchMenuItemExpanded = savedState.getBoolean(SAVE_KEY_SEARCH_MENU_EXPANDED);
            mSearchQuery = savedState.getString(SAVE_KEY_SEARCH_QUERY);

            setTitleFromIntent(intent);

            ArrayList<DashboardCategory> categories =
                    savedState.getParcelableArrayList(SAVE_KEY_CATEGORIES);
            if (categories != null) {
                mCategories.clear();
                mCategories.addAll(categories);
                setTitleFromBackStack();
            }

            mDisplayHomeAsUpEnabled = savedState.getBoolean(SAVE_KEY_SHOW_HOME_AS_UP);
            mDisplaySearch = savedState.getBoolean(SAVE_KEY_SHOW_SEARCH);
            mHomeActivitiesCount = savedState.getInt(SAVE_KEY_HOME_ACTIVITIES_COUNT,
                    1 /* one home activity by default */);
        } else {
            if (!mIsShowingDashboard) {
                // Search is shown we are launched thru a Settings "shortcut". UP will be shown
                // only if it is a sub settings
                if (mIsShortcut) {
                    mDisplayHomeAsUpEnabled = isSubSettings;
                    mDisplaySearch = false;
                } else if (isSubSettings) {
                    mDisplayHomeAsUpEnabled = true;
                    mDisplaySearch = true;
                } else {
                    mDisplayHomeAsUpEnabled = false;
                    mDisplaySearch = false;
                }
                setTitleFromIntent(intent);

                Bundle initialArguments = intent.getBundleExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS);
                //switchToFragment(initialFragmentName, initialArguments, true, false,
                //        mInitialTitleResId, mInitialTitle, false);
            } else {
                // No UP affordance if we are displaying the main Dashboard
                mDisplayHomeAsUpEnabled = false;
                // Show Search affordance
                mDisplaySearch = true;
                mInitialTitleResId = R.string.dashboard_title;
                //switchToFragment(DashboardSummary.class.getName(), null, false, false,
                //        mInitialTitleResId, mInitialTitle, false);
            }
        }

        //mActionBar = mActivity.getActionBar();
        //if (mActionBar != null) {
        //    mActionBar.setDisplayHomeAsUpEnabled(mDisplayHomeAsUpEnabled);
        //    mActionBar.setHomeButtonEnabled(mDisplayHomeAsUpEnabled);
        //}
        //mSwitchBar = (SwitchBar) mActivity.findViewById(R.id.switch_bar);

        // see if we should show Back/Next buttons
        if (intent.getBooleanExtra(EXTRA_PREFS_SHOW_BUTTON_BAR, false)) {

            View buttonBar = mActivity.findViewById(R.id.button_bar);
            if (buttonBar != null) {
                buttonBar.setVisibility(View.VISIBLE);

                Button backButton = (Button)mActivity.findViewById(R.id.back_button);
                backButton.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        mActivity.setResult(mActivity.RESULT_CANCELED, getResultIntentData());
                        finish();
                    }
                });
                Button skipButton = (Button)mActivity.findViewById(R.id.skip_button);
                skipButton.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        mActivity.setResult(mActivity.RESULT_OK, getResultIntentData());
                        finish();
                    }
                });
                mNextButton = (Button)mActivity.findViewById(R.id.next_button);
                mNextButton.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        mActivity.setResult(mActivity.RESULT_OK, getResultIntentData());
                        finish();
                    }
                });

                // set our various button parameters
                if (intent.hasExtra(EXTRA_PREFS_SET_NEXT_TEXT)) {
                    String buttonText = intent.getStringExtra(EXTRA_PREFS_SET_NEXT_TEXT);
                    if (TextUtils.isEmpty(buttonText)) {
                        mNextButton.setVisibility(View.GONE);
                    }
                    else {
                        mNextButton.setText(buttonText);
                    }
                }
                if (intent.hasExtra(EXTRA_PREFS_SET_BACK_TEXT)) {
                    String buttonText = intent.getStringExtra(EXTRA_PREFS_SET_BACK_TEXT);
                    if (TextUtils.isEmpty(buttonText)) {
                        backButton.setVisibility(View.GONE);
                    }
                    else {
                        backButton.setText(buttonText);
                    }
                }
                if (intent.getBooleanExtra(EXTRA_PREFS_SHOW_SKIP, false)) {
                    skipButton.setVisibility(View.VISIBLE);
                }
            }
        }

        mHomeActivitiesCount = getHomeActivitiesCount();
    }

    private int getHomeActivitiesCount() {
        final ArrayList<ResolveInfo> homeApps = new ArrayList<ResolveInfo>();
        getPackageManager().getHomeActivities(homeApps);
        return homeApps.size();
    }

    private void setTitleFromIntent(Intent intent) {
        final int initialTitleResId = intent.getIntExtra(EXTRA_SHOW_FRAGMENT_TITLE_RESID, -1);
        if (initialTitleResId > 0) {
            mInitialTitle = null;
            mInitialTitleResId = initialTitleResId;

            final String initialTitleResPackageName = intent.getStringExtra(
                    EXTRA_SHOW_FRAGMENT_TITLE_RES_PACKAGE_NAME);
            if (initialTitleResPackageName != null) {
                try {
                    Context authContext = mActivity.createPackageContextAsUser(initialTitleResPackageName,
                            0 /* flags */, new UserHandle(UserHandle.myUserId()));
                    mInitialTitle = authContext.getResources().getText(mInitialTitleResId);
                    mActivity.setTitle(mInitialTitle);
                    mInitialTitleResId = -1;
                    return;
                } catch (NameNotFoundException e) {
                    Log.w(LOG_TAG, "Could not find package" + initialTitleResPackageName);
                }
            } else {
                mActivity.setTitle(mInitialTitleResId);
            }
        } else {
            mInitialTitleResId = -1;
            final String initialTitle = intent.getStringExtra(EXTRA_SHOW_FRAGMENT_TITLE);
            mInitialTitle = (initialTitle != null) ? initialTitle : mActivity.getTitle();
            mActivity.setTitle(mInitialTitle);
        }
    }

    @Override
    public void onBackStackChanged() {
        setTitleFromBackStack();
    }

    private int setTitleFromBackStack() {
        final int count = getFragmentManager().getBackStackEntryCount();

        if (count == 0) {
            if (mInitialTitleResId > 0) {
                mActivity.setTitle(mInitialTitleResId);
            } else {
                mActivity.setTitle(mInitialTitle);
            }
            return 0;
        }

        FragmentManager.BackStackEntry bse = getFragmentManager().getBackStackEntryAt(count - 1);
        setTitleFromBackStackEntry(bse);

        return count;
    }

    private void setTitleFromBackStackEntry(FragmentManager.BackStackEntry bse) {
        final CharSequence title;
        final int titleRes = bse.getBreadCrumbTitleRes();
        if (titleRes > 0) {
            title = getText(titleRes);
        } else {
            title = bse.getBreadCrumbTitle();
        }
        if (title != null) {
            mActivity.setTitle(title);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mCategories.size() > 0) {
            outState.putParcelableArrayList(SAVE_KEY_CATEGORIES, mCategories);
        }

        outState.putBoolean(SAVE_KEY_SHOW_HOME_AS_UP, mDisplayHomeAsUpEnabled);
        outState.putBoolean(SAVE_KEY_SHOW_SEARCH, mDisplaySearch);

        if (mDisplaySearch) {
            // The option menus are created if the ActionBar is visible and they are also created
            // asynchronously. If you launch Settings with an Intent action like
            // android.intent.action.POWER_USAGE_SUMMARY and at the same time your device is locked
            // thru a LockScreen, onCreateOptionsMenu() is not yet called and references to the search
            // menu item and search view are null.
            boolean isExpanded = (mSearchMenuItem != null) && mSearchMenuItem.isActionViewExpanded();
            outState.putBoolean(SAVE_KEY_SEARCH_MENU_EXPANDED, isExpanded);

            String query = (mSearchView != null) ? mSearchView.getQuery().toString() : EMPTY_QUERY;
            outState.putString(SAVE_KEY_SEARCH_QUERY, query);
        }

        outState.putInt(SAVE_KEY_HOME_ACTIVITIES_COUNT, mHomeActivitiesCount);
    }

    @Override
    public void onResume() {
        super.onResume();
		Log.d(LOG_TAG, "onResume() ,mActivity:"+mActivity);
		rebuildUI();
		mSearchView2.getSearchSrcTextView().setOnTouchListener(new View.OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						break;
					case MotionEvent.ACTION_MOVE:
						break;
					case MotionEvent.ACTION_UP:
						/*HQ_xupeixin at 2015-09-24 modified about optimize searching feature in Settings application begin*/
						Intent intent = new Intent(mActivity, SearchSettingActivity.class);;
						mActivity.startActivity(intent);
						/*HQ_xupeixin at 2015-09-24 end*/
						break;
					default:
						break;
				}
				return false;
			}
		});
		mSearchView2.getSearchSrcTextView().setHint(mActivity.getResources().getString(R.string.search_settings_hint));
        final int newHomeActivityCount = getHomeActivitiesCount();
        if (newHomeActivityCount != mHomeActivitiesCount) {
            mHomeActivitiesCount = newHomeActivityCount;
            invalidateCategories(true);
        }

        mDevelopmentPreferencesListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                invalidateCategories(true);
            }
        };
        mDevelopmentPreferences.registerOnSharedPreferenceChangeListener(
                mDevelopmentPreferencesListener);

        /*HQ_yangfengqing 2015-9-22 modified for wifi is connected and network enabled show ssid begin*/
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mActivity.registerReceiver(mBatteryInfoReceiver, intentFilter);
        /* HQ_yangfengqing 2015-9-22 modified end*/

        mDynamicIndexableContentMonitor.register(mActivity);

        if(mDisplaySearch && !TextUtils.isEmpty(mSearchQuery)) {
            onQueryTextSubmit(mSearchQuery);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        mActivity.unregisterReceiver(mBatteryInfoReceiver);
        mDynamicIndexableContentMonitor.unregister();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mDevelopmentPreferences.unregisterOnSharedPreferenceChangeListener(
                mDevelopmentPreferencesListener);
	mResolver.unregisterContentObserver(mObserver);//add by maolikui at 2015-12-28
        mDevelopmentPreferencesListener = null;
    }

    protected boolean isValidFragment(String fragmentName) {
        // Almost all fragments are wrapped in this,
        // except for a few that have their own activities.
        for (int i = 0; i < ENTRY_FRAGMENTS.length; i++) {
            if (ENTRY_FRAGMENTS[i].equals(fragmentName)) return true;
        }
        return false;
    }

    public Intent getIntent() {
        Intent superIntent = mActivity.getIntent();
        String startingFragment = getStartingFragmentClass(superIntent);
        // This is called from super.onCreate, isMultiPane() is not yet reliable
        // Do not use onIsHidingHeaders either, which relies itself on this method
        if (startingFragment != null) {
            Intent modIntent = new Intent(superIntent);
            modIntent.putExtra(EXTRA_SHOW_FRAGMENT, startingFragment);
            Bundle args = superIntent.getExtras();
            if (args != null) {
                args = new Bundle(args);
            } else {
                args = new Bundle();
            }
            args.putParcelable("intent", superIntent);
            modIntent.putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, args);
            return modIntent;
        }
        return superIntent;
    }

    /**
     * Checks if the component name in the intent is different from the Settings class and
     * returns the class name to load as a fragment.
     */
    private String getStartingFragmentClass(Intent intent) {
        if (mFragmentClass != null) return mFragmentClass;

        String intentClass = intent.getComponent().getClassName();
        if (intentClass.equals(getClass().getName())) return null;

        if ("com.android.settings.ManageApplications".equals(intentClass)
                || "com.android.settings.RunningServices".equals(intentClass)
                || "com.android.settings.applications.StorageUse".equals(intentClass)) {
            // Old names of manage apps.
            intentClass = com.android.settings.applications.ManageApplications.class.getName();
        }

        return intentClass;
    }

    /**
     * Start a new fragment containing a preference panel.  If the preferences
     * are being displayed in multi-pane mode, the given fragment class will
     * be instantiated and placed in the appropriate pane.  If running in
     * single-pane mode, a new activity will be launched in which to show the
     * fragment.
     *
     * @param fragmentClass Full name of the class implementing the fragment.
     * @param args Any desired arguments to supply to the fragment.
     * @param titleRes Optional resource identifier of the title of this
     * fragment.
     * @param titleText Optional text of the title of this fragment.
     * @param resultTo Optional fragment that result data should be sent to.
     * If non-null, resultTo.onActivityResult() will be called when this
     * preference panel is done.  The launched panel must use
     * {@link #finishPreferencePanel(Fragment, int, Intent)} when done.
     * @param resultRequestCode If resultTo is non-null, this is the caller's
     * request code to be received with the result.
     */
    public void startPreferencePanel(String fragmentClass, Bundle args, int titleRes,
            CharSequence titleText, Fragment resultTo, int resultRequestCode) {
        String title = null;
        if (titleRes < 0) {
            if (titleText != null) {
                title = titleText.toString();
            } else {
                // There not much we can do in that case
                title = "";
            }
        }
        Utils.startWithFragment(mActivity, fragmentClass, args, resultTo, resultRequestCode,
                titleRes, title, mIsShortcut);
    }

    /**
     * Start a new fragment in a new activity containing a preference panel for a given user. If the
     * preferences are being displayed in multi-pane mode, the given fragment class will be
     * instantiated and placed in the appropriate pane. If running in single-pane mode, a new
     * activity will be launched in which to show the fragment.
     *
     * @param fragmentClass Full name of the class implementing the fragment.
     * @param args Any desired arguments to supply to the fragment.
     * @param titleRes Optional resource identifier of the title of this fragment.
     * @param titleText Optional text of the title of this fragment.
     * @param userHandle The user for which the panel has to be started.
     */
    public void startPreferencePanelAsUser(String fragmentClass, Bundle args, int titleRes,
            CharSequence titleText, UserHandle userHandle) {
        String title = null;
        if (titleRes < 0) {
            if (titleText != null) {
                title = titleText.toString();
            } else {
                // There not much we can do in that case
                title = "";
            }
        }
        Utils.startWithFragmentAsUser(mActivity, fragmentClass, args,
                titleRes, title, mIsShortcut, userHandle);
    }

    /**
     * Called by a preference panel fragment to finish itself.
     *
     * @param caller The fragment that is asking to be finished.
     * @param resultCode Optional result code to send back to the original
     * launching fragment.
     * @param resultData Optional result data to send back to the original
     * launching fragment.
     */
    public void finishPreferencePanel(Fragment caller, int resultCode, Intent resultData) {
        mActivity.setResult(resultCode, resultData);
        finish();
    }

    /**
     * Start a new fragment.
     *
     * @param fragment The fragment to start
     * @param push If true, the current fragment will be pushed onto the back stack.  If false,
     * the current fragment will be replaced.
     */
    public void startPreferenceFragment(Fragment fragment, boolean push) {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        //transaction.replace(R.id.main_content, fragment);
        if (push) {
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            transaction.addToBackStack(BACK_STACK_PREFS);
        } else {
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        }
        transaction.commitAllowingStateLoss();
    }

    /**
     * Switch to a specific Fragment with taking care of validation, Title and BackStack
     */
    private Fragment switchToFragment(String fragmentName, Bundle args, boolean validate,
            boolean addToBackStack, int titleResId, CharSequence title, boolean withTransition) {
        if (validate && !isValidFragment(fragmentName)) {
            throw new IllegalArgumentException("Invalid fragment for this activity: "
                    + fragmentName);
        }
		Log.d(LOG_TAG, "switchToFragment() mActivity:" + mActivity.toString());
        Fragment f = Fragment.instantiate(mActivity, fragmentName, args);
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.main_content, f);
        if (withTransition) {
           //TransitionManager.beginDelayedTransition(mContent);
        }
        if (addToBackStack) {
            transaction.addToBackStack(SettingsActivity.BACK_STACK_PREFS);
        }
        if (titleResId > 0) {
            transaction.setBreadCrumbTitle(titleResId);
        } else if (title != null) {
            transaction.setBreadCrumbTitle(title);
        }
        transaction.commitAllowingStateLoss();
        //getFragmentManager().executePendingTransactions();
        return f;
    }

    /**
     * Called when the activity needs its list of categories/tiles built.
     *
     * @param categories The list in which to place the tiles categories.
     */
    private void buildDashboardCategories(List<DashboardCategory> categories) {
        categories.clear();
		Log.d(LOG_TAG, "buildDashboardCategories()");
        loadCategoriesFromResource(R.xml.dashboard_categories, categories);
        updateTilesList(categories);
    }

    /**
     * Parse the given XML file as a categories description, adding each
     * parsed categories and tiles into the target list.
     *
     * @param resid The XML resource to load and parse.
     * @param target The list in which the parsed categories and tiles should be placed.
     */
    private void loadCategoriesFromResource(int resid, List<DashboardCategory> target) {
        Log.d(LOG_TAG, "loadCategoriesFromResource() ,mActivity:"+mActivity);
		
        XmlResourceParser parser = null;
        try {
            parser = mActivity.getResources().getXml(resid);
            AttributeSet attrs = Xml.asAttributeSet(parser);

            int type;
            while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                    && type != XmlPullParser.START_TAG) {
                // Parse next until start tag is found
            }

            String nodeName = parser.getName();
            if (!"dashboard-categories".equals(nodeName)) {
                throw new RuntimeException(
                        "XML document must start with <preference-categories> tag; found"
                                + nodeName + " at " + parser.getPositionDescription());
            }

            Bundle curBundle = null;

            final int outerDepth = parser.getDepth();
            while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }

                nodeName = parser.getName();
                if ("dashboard-category".equals(nodeName)) {
                    DashboardCategory category = new DashboardCategory();

                    TypedArray sa = mActivity.obtainStyledAttributes(
                            attrs, com.android.internal.R.styleable.PreferenceHeader);
                    category.id = sa.getResourceId(
                            com.android.internal.R.styleable.PreferenceHeader_id,
                            (int)DashboardCategory.CAT_ID_UNDEFINED);

                    TypedValue tv = sa.peekValue(
                            com.android.internal.R.styleable.PreferenceHeader_title);
                    if (tv != null && tv.type == TypedValue.TYPE_STRING) {
                        if (tv.resourceId != 0) {
                            category.titleRes = tv.resourceId;
                        } else {
                            category.title = tv.string;
                        }
                    }
                    sa.recycle();

                    final int innerDepth = parser.getDepth();
                    while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                            && (type != XmlPullParser.END_TAG || parser.getDepth() > innerDepth)) {
                        if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                            continue;
                        }

                        String innerNodeName = parser.getName();
                        if (innerNodeName.equals("dashboard-tile")) {
                            DashboardTile tile = new DashboardTile();

                            sa = mActivity.obtainStyledAttributes(
                                    attrs, com.android.internal.R.styleable.PreferenceHeader);
                            tile.id = sa.getResourceId(
                                    com.android.internal.R.styleable.PreferenceHeader_id,
                                    (int)TILE_ID_UNDEFINED);
                            tv = sa.peekValue(
                                    com.android.internal.R.styleable.PreferenceHeader_title);
                            if (tv != null && tv.type == TypedValue.TYPE_STRING) {
                                if (tv.resourceId != 0) {
                                    tile.titleRes = tv.resourceId;
                                } else {
                                    tile.title = tv.string;
                                }
                            }
                            tv = sa.peekValue(
                                    com.android.internal.R.styleable.PreferenceHeader_summary);
                            if (tv != null && tv.type == TypedValue.TYPE_STRING) {
                                if (tv.resourceId != 0) {
                                    tile.summaryRes = tv.resourceId;
                                } else {
                                    tile.summary = tv.string;
                                }
                            }
                            tile.iconRes = sa.getResourceId(
                                    com.android.internal.R.styleable.PreferenceHeader_icon, 0);
                            tile.fragment = sa.getString(
                                    com.android.internal.R.styleable.PreferenceHeader_fragment);
                            sa.recycle();

                            if (curBundle == null) {
                                curBundle = new Bundle();
                            }

                            final int innerDepth2 = parser.getDepth();
                            while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                                    && (type != XmlPullParser.END_TAG || parser.getDepth() > innerDepth2)) {
                                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                                    continue;
                                }

                                String innerNodeName2 = parser.getName();
                                if (innerNodeName2.equals("extra")) {
                                    getResources().parseBundleExtra("extra", attrs, curBundle);
                                    XmlUtils.skipCurrentTag(parser);

                                } else if (innerNodeName2.equals("intent")) {
                                    tile.intent = Intent.parseIntent(getResources(), parser, attrs);

                                } else {
                                    XmlUtils.skipCurrentTag(parser);
                                }
                            }

                            if (curBundle.size() > 0) {
                                tile.fragmentArguments = curBundle;
                                curBundle = null;
                            }

                            // Show the SIM Cards setting if there are more than 2 SIMs installed.
                            if(tile.id != R.id.sim_settings || Utils.showSimCardTile(mActivity)){
                                category.addTile(tile);
                            }

                        } else {
                            XmlUtils.skipCurrentTag(parser);
                        }
                    }

                    target.add(category);
                } else {
                    XmlUtils.skipCurrentTag(parser);
                }
            }

        } catch (XmlPullParserException e) {
            throw new RuntimeException("Error parsing categories", e);
        } catch (IOException e) {
            throw new RuntimeException("Error parsing categories", e);
        } finally {
            if (parser != null) parser.close();
        }
    }

    private void updateTilesList(List<DashboardCategory> target) {
        final boolean showDev = mDevelopmentPreferences.getBoolean(
                DevelopmentSettings.PREF_SHOW,
                android.os.Build.TYPE.equals("eng"));

        final UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);

        final int size = target.size();
        for (int i = 0; i < size; i++) {

            DashboardCategory category = target.get(i);

            // Ids are integers, so downcasting is ok
            int id = (int) category.id;
            int n = category.getTilesCount() - 1;
            while (n >= 0) {

                DashboardTile tile = category.getTile(n);
                boolean removeTile = false;
                id = (int) tile.id;
                if (id == R.id.operator_settings || id == R.id.manufacturer_settings) {
                    if (!Utils.updateTileToSpecificActivityFromMetaDataOrRemove(mActivity, tile)) {
                        removeTile = true;
                    }
                }  else if (id == R.id.sim_settings) {
                    // Remove sim Settings if WiFi only.
                    if (mIsWifiOnly) {
                        removeTile = true;
                    }
                }else if (id == R.id.wifi_settings) {
                    // Remove WiFi Settings if WiFi service is not available.
                    if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)) {
                        removeTile = true;
                    }
                } else if (id == R.id.bluetooth_settings) {
                    // Remove Bluetooth Settings if Bluetooth service is not available.
                    if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
                        removeTile = true;
                    }
                } else if (id == R.id.data_usage_settings) {
                    /// M: add for 3g/4g switch @{
                    mExt.addCustomizedItem(category, n);
                    /// @}
                    // Remove data usage when kernel module not enabled
                    final INetworkManagementService netManager = INetworkManagementService.Stub
                            .asInterface(ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE));
                    try {
                        if (!netManager.isBandwidthControlEnabled()) {
                            removeTile = true;
                        }
                    } catch (RemoteException e) {
                        // ignored
                    }
                } else if (id == R.id.battery_settings) {
                    // Remove battery settings when battery is not available. (e.g. TV)

                    if (!mBatteryPresent) {
                        removeTile = true;
                    }
                } else if (id == R.id.home_settings) {
                    if (!updateHomeSettingTiles(tile)) {
                        removeTile = true;
                    }
                } else if (id == R.id.user_settings) {
                    //modify by majian for AFW ,remove user menu 20160130
                    /*boolean hasMultipleUsers =
                            ((UserManager) getSystemService(Context.USER_SERVICE))
                                    .getUserCount() > 1;
                    if (!UserHandle.MU_ENABLED
                            || (!UserManager.supportsMultipleUsers()
                                    && !hasMultipleUsers)
                            || Utils.isMonkeyRunning()) */
		    //{
                        removeTile = true;
                    //}
                } else if (id == R.id.nfc_payment_settings) {
                    if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC)) {
                        removeTile = true;
                    } else {
                        // Only show if NFC is on and we have the HCE feature
                        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mActivity);
                        int hceFlg = android.provider.Settings.Global.getInt(getContentResolver(),
                                    android.provider.Settings.Global.NFC_HCE_ON, 0);
                        Log.d(LOG_TAG, "NFC_HCE_ON is  " + hceFlg);
                        if (adapter == null || !adapter.isEnabled() || !getPackageManager().hasSystemFeature(
                                PackageManager.FEATURE_NFC_HOST_CARD_EMULATION) || (hceFlg != 1)) {
                            removeTile = true;
                        }
                    }
                    //hanchao remove print menu
                /*} else if (id == R.id.print_settings) {
                    boolean hasPrintingSupport = getPackageManager().hasSystemFeature(
                            PackageManager.FEATURE_PRINTING);
                    if (!hasPrintingSupport) {
                        removeTile = true;
                    }*/
                } else if (id == R.id.development_settings) {
                    if (!showDev || um.hasUserRestriction(
                            UserManager.DISALLOW_DEBUGGING_FEATURES)) {
                        removeTile = true;
                    }
                } /*else if (id == R.id.power_settings) { /// { @ Schedule power on/off
                    Intent intent = new Intent("com.android.settings.SCHEDULE_POWER_ON_OFF_SETTING");
                    List<ResolveInfo> apps = getPackageManager()
                            .queryIntentActivities(intent, 0);
                    if (apps != null && apps.size() != 0) {
                        Log.d(LOG_TAG, "apps.size()=" + apps.size());
                        if (UserHandle.myUserId() != UserHandle.USER_OWNER) {
                            category.removeTile(n);
                        }
                    } else {
                        Log.d(LOG_TAG, "apps is null or app size is 0, remove SchedulePowerOnOff");
                        category.removeTile(n);
                    } /// @}
                }*/ else if (id == R.id.hotknot_settings) {
                    //Remove Hotknot Settings if Hotknot service is not available.
                    HotKnotAdapter adapter = HotKnotAdapter.getDefaultAdapter(mActivity);
                    if (adapter == null) {
                       Log.d(LOG_TAG, "HotKnotAdapter is null, remove hotknot_settings");
                       category.removeTile(n);
                    }
                }else if (id == R.id.privacy_settings) {
                   ///M: change backup reset title @{
                   //mExt.setFactoryResetTitle(tile);//HQ_jiazaizheng 20150721 modify for HQ01266898
                   /// @}
                } else if (id == R.id.hetcomm_settings) { /// M: Hetcomm feature
                    if (!UtilsExt.isPackageExist(mActivity, UtilsExt.PKG_NAME_HETCOMM)) {
                         removeTile = true;
                    }
                }
                //hanchao remove for HQ01343952
                /*else if (id == R.id.startup_settings) {
                   String startupEnable = SystemProperties.get("ro.hw_startup_enable", "0");
                   if (!("1".equals(startupEnable))) {
                   HQ_yuankangbo 2015-07-30 modify for startup manager 
//                       removeTile = true;  
                   }
              } */else if (id == R.id.hw_cloud_service) {
                    ComponentName cn = new ComponentName("com.huawei.android.ds","com.huawei.android.hicloud.hisync.activity.NewHiSyncSettingActivity");
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setComponent(cn);
                    List<ResolveInfo> apps = getPackageManager()
                            .queryIntentActivities(intent, 0);
                    if (apps == null || apps.size() == 0) {
                        Log.d(LOG_TAG, "Huawei cloud service is not installed");
                        category.removeTile(n);
                   }

                }

                if (UserHandle.MU_ENABLED && UserHandle.myUserId() != 0
                        && !ArrayUtils.contains(SETTINGS_FOR_RESTRICTED, id)) {
                    removeTile = true;
                }

                if (removeTile && n < category.getTilesCount()) {
                    category.removeTile(n);
                }
                n--;
            }
        }
    }

    private boolean updateHomeSettingTiles(DashboardTile tile) {
        // Once we decide to show Home settings, keep showing it forever
        SharedPreferences sp = mActivity.getSharedPreferences(HomeSettings.HOME_PREFS, Context.MODE_PRIVATE);
        if (sp.getBoolean(HomeSettings.HOME_PREFS_DO_SHOW, false)) {
            return true;
        }

        try {
            mHomeActivitiesCount = getHomeActivitiesCount();
            if (mHomeActivitiesCount < 2) {
                // When there's only one available home app, omit this settings
                // category entirely at the top level UI.  If the user just
                // uninstalled the penultimate home app candidiate, we also
                // now tell them about why they aren't seeing 'Home' in the list.
                if (sShowNoHomeNotice) {
                    sShowNoHomeNotice = false;
                    NoHomeDialogFragment.show(mActivity);
                }
                return false;
            } else {
                // Okay, we're allowing the Home settings category.  Tell it, when
                // invoked via this front door, that we'll need to be told about the
                // case when the user uninstalls all but one home app.
                if (tile.fragmentArguments == null) {
                    tile.fragmentArguments = new Bundle();
                }
                tile.fragmentArguments.putBoolean(HomeSettings.HOME_SHOW_NOTICE, true);
            }
        } catch (Exception e) {
            // Can't look up the home activity; bail on configuring the icon
            Log.w(LOG_TAG, "Problem looking up home activity!", e);
        }

        sp.edit().putBoolean(HomeSettings.HOME_PREFS_DO_SHOW, true).apply();
        return true;
    }

    private void getMetaData() {
        try {
            ActivityInfo ai = getPackageManager().getActivityInfo(mActivity.getComponentName(),
                    PackageManager.GET_META_DATA);
            if (ai == null || ai.metaData == null) return;
            mFragmentClass = ai.metaData.getString(META_DATA_KEY_FRAGMENT_CLASS);
        } catch (NameNotFoundException nnfe) {
            // No recovery
            Log.d(LOG_TAG, "Cannot get Metadata for: " + mActivity.getComponentName().toString());
        }
    }

    // give subclasses access to the Next button
    public boolean hasNextButton() {
        return mNextButton != null;
    }

    public Button getNextButton() {
        return mNextButton;
    }

    public boolean shouldUpRecreateTask(Intent targetIntent) {
        return mActivity.shouldUpRecreateTask(new Intent(mActivity, SettingsActivity.class));
    }

    public static void requestHomeNotice() {
        sShowNoHomeNotice = true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        switchToSearchResultsFragmentIfNeeded();
        mSearchQuery = query;
        return mSearchResultsFragment.onQueryTextSubmit(query);
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        mSearchQuery = newText;
        if (mSearchResultsFragment == null) {
            return false;
        }
        return mSearchResultsFragment.onQueryTextChange(newText);
    }

    @Override
    public boolean onClose() {
        return false;
    }

    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        if (item.getItemId() == mSearchMenuItem.getItemId()) {
            switchToSearchResultsFragmentIfNeeded();
        }
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        if (item.getItemId() == mSearchMenuItem.getItemId()) {
            if (mSearchMenuItemExpanded) {
                revertToInitialFragment();
            }
        }
        return true;
    }

    private void switchToSearchResultsFragmentIfNeeded() {
        if (mSearchResultsFragment != null) {
            return;
        }
        //Fragment current = getFragmentManager().findFragmentById(R.id.main_content);
        Fragment current = null;
        if (current != null && current instanceof SearchResultsSummary) {
            mSearchResultsFragment = (SearchResultsSummary) current;
        } else {
            mSearchResultsFragment = (SearchResultsSummary) switchToFragment(
                    SearchResultsSummary.class.getName(), null, false, true,
                    R.string.search_results_title, null, true);
        }
        mSearchResultsFragment.setSearchView(mSearchView);
        mSearchMenuItemExpanded = true;
    }

    public void needToRevertToInitialFragment() {
        mNeedToRevertToInitialFragment = true;
    }

    private void revertToInitialFragment() {
        mNeedToRevertToInitialFragment = false;
        mSearchResultsFragment = null;
        mSearchMenuItemExpanded = false;
        getFragmentManager().popBackStackImmediate(SettingsActivity.BACK_STACK_PREFS,
                FragmentManager.POP_BACK_STACK_INCLUSIVE);
        if (mSearchMenuItem != null) {
            mSearchMenuItem.collapseActionView();
        }
    }

    public Intent getResultIntentData() {
        return mResultIntentData;
    }

    public void setResultIntentData(Intent resultIntentData) {
        mResultIntentData = resultIntentData;
    }
}
