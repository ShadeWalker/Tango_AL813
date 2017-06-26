package com.android.settings;

import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.TrustAgentUtils.TrustAgentComponentInfo;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.sax.StartElementListener;
import android.service.trust.TrustAgentService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.pm.ResolveInfo;
import android.provider.SettingsEx;

import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

public class ScreenlockPasswordSettings extends SettingsPreferenceFragment implements OnPreferenceChangeListener{
	
	private static final String TAG = "ScreenlockPasswordSettings";
	
	private static final String KEY_USE_PASSWORD_LOCK_ONLY = "use_password_lock_only";
	private static final String KEY_SCREEN_LOCK_STYLE = "screen_lock_style";
	private static final String KEY_LOCK_ENABLED = "screen_lock_enable";
	private static final String KEY_VISIBLE_PATTERN = "visiblepattern";
	private static final String KEY_OWNER_INFO_SETTINGS = "owner_info_settings";
	private static final String KEY_UNLOCK_SET_OR_CHANGE = "unlock_set_or_change";
	private static final String KEY_SECURITY_CATEGORY = "security_category";
	private static final String KEY_LOCK_AFTER_TIMEOUT = "lock_after_timeout";
	private static final String KEY_POWER_INSTANTLY_LOCKS = "power_button_instantly_locks";
	
	private static final String TRUST_AGENT_CLICK_INTENT = "trust_agent_click_intent";
	private static final String KEY_TRUST_AGENT = "trust_agent";
	//HQ_caoxuhao adapt for huawei keyguard wakeup_when_receive_notification
	private static final String WAKEUP_WHEN_RECEIVE_NOTIFICATION = "wakeup_when_receive_notification";
	private static final int SET_OR_CHANGE_LOCK_METHOD_REQUEST = 123;
	
	private static final String KEY_DROP_DOWN_NOTIFICATION_BAR = "drop_down_notification_bar";//HQ_jiazaizheng 20150813 modify for HQ01322784
	private static final String ENABLE_EXPAND_ON_HUAWEI_UNLOCK = "enable_expand_on_huawei_unlock";
	private static final String KEY_SHOW_DETAULED_NOTIFICATIONS = "show_detailed_notifications";
	private static final String KEY_NOTIFICATION_TURN_ON_SCREEN = "notification_turn_on_screen";

	private static final String KEY_SMART_LOCK = "Smart Lock";
	// Only allow one trust agent on the platform.
    private static final boolean ONLY_ONE_TRUST_AGENT = true;
    
    private static final Intent TRUST_AGENT_INTENT =
            new Intent(TrustAgentService.SERVICE_INTERFACE);
    
    private SwitchPreference mDropdownNotificationbar;//HQ_jiazaizheng 20150813 modify for HQ01322784
    private SwitchPreference mShowDetailedNotifications, mUsePasswordLockOnly, mPowerButtonInstantlyLocks,
    mLockEnabled, mNotificationTurnOnScreen, mVisiblePattern;
    
    private ListPreference mLockAfter;
    private PreferenceScreen mScreenLockStyle, mLockScreenSignature,mUnlockSetOrChange;
    
    private DevicePolicyManager mDPM;
	private LockPatternUtils mLockPatternUtils;
	private Intent mTrustAgentClickIntent;
	private boolean mIsPrimary;
	private ChooseLockSettingsHelper mChooseLockSettingsHelper;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
	     super.onCreate(savedInstanceState);
	     Log.i(TAG, "ScreenlockPasswordSettings onCreat");
	     mLockPatternUtils = new LockPatternUtils(getActivity());
	     mChooseLockSettingsHelper = new ChooseLockSettingsHelper(getActivity());
	     mDPM = (DevicePolicyManager)getSystemService(Context.DEVICE_POLICY_SERVICE);
	     
	     if (savedInstanceState != null
	                && savedInstanceState.containsKey(TRUST_AGENT_CLICK_INTENT)) {
	            mTrustAgentClickIntent = savedInstanceState.getParcelable(TRUST_AGENT_CLICK_INTENT);
	     }
	}
	 
	@Override
	public void onResume() {
	    super.onResume();
	    
	    // Make sure we reload the preference hierarchy since some of these settings
        // depend on others...
	    createPreferenceHierarchy();
	    
	    final LockPatternUtils lockPatternUtils = mChooseLockSettingsHelper.utils();
	    if (mPowerButtonInstantlyLocks != null) {
            mPowerButtonInstantlyLocks.setChecked(lockPatternUtils.getPowerButtonInstantlyLocks());
        }
	    
	    if (mVisiblePattern != null) {
            mVisiblePattern.setChecked(lockPatternUtils.isVisiblePatternEnabled());
        }
	    
	    if (isLockEnabledAllowed()) {
			enableALLPreferences();
		}else {
			disableALLPreferences();
		}
	    
	}
	
	private PreferenceScreen createPreferenceHierarchy() {
		PreferenceScreen root = getPreferenceScreen();
        if (root != null) {
            root.removeAll();
        }
        addPreferencesFromResource(R.xml.screenlock_password_settings);
        root = getPreferenceScreen();
        
		// Add options for lock/unlock screen
        final int resid = getResIdForLockUnlockScreen(getActivity(), mLockPatternUtils);
        addPreferencesFromResource(resid);
        
        // Add options for device encryption
        mIsPrimary = UserHandle.myUserId() == UserHandle.USER_OWNER;

        if (!mIsPrimary) {
            // Rename owner info settings
            Preference ownerInfoPref = findPreference(KEY_OWNER_INFO_SETTINGS);
            if (ownerInfoPref != null) {
                if (UserManager.get(getActivity()).isLinkedUser()) {
                    ownerInfoPref.setTitle(R.string.profile_info_settings_title);
                } else {
                    ownerInfoPref.setTitle(R.string.user_info_settings_title);
                }
            }
        }
        
        PreferenceGroup securityCategory = (PreferenceGroup)
                root.findPreference(KEY_SECURITY_CATEGORY);
        Log.i(TAG, "securityCategory = " + securityCategory);
        
        if (securityCategory != null) {
            final boolean hasSecurity = mLockPatternUtils.isSecure();
            ArrayList<TrustAgentComponentInfo> agents =
                    getActiveTrustAgents(getPackageManager(), mLockPatternUtils);
            for (int i = 0; i < agents.size(); i++) {
                final TrustAgentComponentInfo agent = agents.get(i);
		
		//add for delete smart lock by HQ_caoxuhao
		if(KEY_SMART_LOCK.equals(agent.title))
			continue;
		

                Preference trustAgentPreference =
                        new Preference(securityCategory.getContext());
                trustAgentPreference.setKey(KEY_TRUST_AGENT);
                trustAgentPreference.setTitle(agent.title);
                trustAgentPreference.setSummary(agent.summary);
                // Create intent for this preference.
                Intent intent = new Intent();
                intent.setComponent(agent.componentName);
                intent.setAction(Intent.ACTION_MAIN);
                trustAgentPreference.setIntent(intent);
		Log.i(TAG,"agent.componentName = " + agent.componentName);
		Log.i(TAG,"agent.title = " + agent.title);
		Log.i(TAG,"agent.summary = " + agent.summary);
		Log.i(TAG,"==============================");
                // Add preference to the settings menu.
                securityCategory.addPreference(trustAgentPreference);
                if (!hasSecurity) {
                    trustAgentPreference.setEnabled(false);
                    trustAgentPreference.setSummary(R.string.disabled_because_no_backup_security);
                }
            }
        }
        
        mScreenLockStyle = (PreferenceScreen) root.findPreference(KEY_SCREEN_LOCK_STYLE);
        mLockScreenSignature = (PreferenceScreen) root.findPreference(KEY_OWNER_INFO_SETTINGS);
        mUnlockSetOrChange = (PreferenceScreen) root.findPreference(KEY_UNLOCK_SET_OR_CHANGE);
        
        // visible pattern
        mVisiblePattern = (SwitchPreference) root.findPreference(KEY_VISIBLE_PATTERN);
        if (mVisiblePattern != null) {
        	mVisiblePattern.setOnPreferenceChangeListener(this);
		}
        
        // lock after preference
        mLockAfter = (ListPreference) root.findPreference(KEY_LOCK_AFTER_TIMEOUT);
        if (mLockAfter != null) {
            setupLockAfterPreference();
            updateLockAfterPreferenceSummary();
        }
        
        //HQ_jiazaizheng 20150813 modify for HQ01322784 start
        mDropdownNotificationbar = (SwitchPreference) findPreference(
                KEY_DROP_DOWN_NOTIFICATION_BAR);
        if (mDropdownNotificationbar != null) {
            mDropdownNotificationbar.setOnPreferenceChangeListener(this);
            mDropdownNotificationbar.setChecked(isDropdownNotificationBarAllowed());
        }
        //HQ_jiazaizheng 20150813 modify for HQ01322784 end
        
        mShowDetailedNotifications = (SwitchPreference) findPreference(
        		KEY_SHOW_DETAULED_NOTIFICATIONS);
        if (mShowDetailedNotifications != null) {
        	mShowDetailedNotifications.setOnPreferenceChangeListener(this);
        	mShowDetailedNotifications.setChecked(isShowDetailedNotificationsAllowed());
        }
        
        mUsePasswordLockOnly = (SwitchPreference) findPreference(
        		KEY_USE_PASSWORD_LOCK_ONLY);
        if (mUsePasswordLockOnly != null) {
        	mUsePasswordLockOnly.setOnPreferenceChangeListener(this);
        	mUsePasswordLockOnly.setChecked(isUsePasswordLockOnlyAllowed());
        }
        
        
        mLockEnabled = (SwitchPreference) findPreference(
                KEY_LOCK_ENABLED);
        if (mLockEnabled != null) {
        	mLockEnabled.setOnPreferenceChangeListener(this);
        	mLockEnabled.setChecked(isLockEnabledAllowed());
        }
        
        mNotificationTurnOnScreen = (SwitchPreference) findPreference(
                KEY_NOTIFICATION_TURN_ON_SCREEN);
        if (mNotificationTurnOnScreen != null) {
        	mNotificationTurnOnScreen.setOnPreferenceChangeListener(this);
        	mNotificationTurnOnScreen.setChecked(isNotificationTurnOnScreenAllowed());
        }
        
        // lock instantly on power key press
        mPowerButtonInstantlyLocks = (SwitchPreference) root.findPreference(
                KEY_POWER_INSTANTLY_LOCKS);
        if (mPowerButtonInstantlyLocks != null) {
        	mPowerButtonInstantlyLocks.setOnPreferenceChangeListener(this);
		}
        
        
        Preference trustAgentPreference = root.findPreference(KEY_TRUST_AGENT);
        Log.i(TAG, "trustAgentPreference = " + trustAgentPreference);
        
        if (mPowerButtonInstantlyLocks != null &&
                trustAgentPreference != null &&
                trustAgentPreference.getTitle().length() > 0) {
            mPowerButtonInstantlyLocks.setSummary(getString(
                    R.string.lockpattern_settings_power_button_instantly_locks_summary,
                    trustAgentPreference.getTitle()));
        }
        
        return root;
	}
	
	private static int getResIdForLockUnlockScreen(Context context,
            LockPatternUtils lockPatternUtils) {
        int resid = 0;
        if (!lockPatternUtils.isSecure()) {
            // if there are multiple users, disable "None" setting
            UserManager mUm = (UserManager) context. getSystemService(Context.USER_SERVICE);
            List<UserInfo> users = mUm.getUsers(true);
            final boolean singleUser = users.size() == 1;

            if (singleUser && lockPatternUtils.isLockScreenDisabled()) {
                resid = R.xml.security_settings_lockscreen;
            } else {
                resid = R.xml.security_settings_chooser;
            }
        } else if (lockPatternUtils.usingBiometricWeak() &&
                lockPatternUtils.isBiometricWeakInstalled()) {
            resid = R.xml.security_settings_biometric_weak;
        } else if (lockPatternUtils.usingVoiceWeak()) {
            resid = R.xml.security_settings_voice_weak;
        } else {
            switch (lockPatternUtils.getKeyguardStoredPasswordQuality()) {
                case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
                    resid = R.xml.security_settings_pattern;
                    break;
                case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
                case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX:
                    resid = R.xml.security_settings_pin;
                    break;
                case DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC:
                case DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC:
                case DevicePolicyManager.PASSWORD_QUALITY_COMPLEX:
                    resid = R.xml.security_settings_password;
                    break;
            }
        }
        return resid;
    }
	
	private static ArrayList<TrustAgentComponentInfo> getActiveTrustAgents(
            PackageManager pm, LockPatternUtils utils) {
        ArrayList<TrustAgentComponentInfo> result = new ArrayList<TrustAgentComponentInfo>();
        List<ResolveInfo> resolveInfos = pm.queryIntentServices(TRUST_AGENT_INTENT,
                PackageManager.GET_META_DATA);
        List<ComponentName> enabledTrustAgents = utils.getEnabledTrustAgents();
        if (enabledTrustAgents != null && !enabledTrustAgents.isEmpty()) {
            for (int i = 0; i < resolveInfos.size(); i++) {
                ResolveInfo resolveInfo = resolveInfos.get(i);
                if (resolveInfo.serviceInfo == null) continue;
                if (!TrustAgentUtils.checkProvidePermission(resolveInfo, pm)) continue;
                TrustAgentComponentInfo trustAgentComponentInfo =
                        TrustAgentUtils.getSettingsComponent(pm, resolveInfo);
                if (trustAgentComponentInfo.componentName == null ||
                        !enabledTrustAgents.contains(
                                TrustAgentUtils.getComponentName(resolveInfo)) ||
                        TextUtils.isEmpty(trustAgentComponentInfo.title)) continue;
                result.add(trustAgentComponentInfo);
                if (ONLY_ONE_TRUST_AGENT) break;
            }
        }
        return result;
    }
	
	@Override
	public void onDestroy() {
	    super.onDestroy();
	}
	 
	@Override
	public void onSaveInstanceState(Bundle outState) {
	    super.onSaveInstanceState(outState);
	    if (mTrustAgentClickIntent != null) {
            outState.putParcelable(TRUST_AGENT_CLICK_INTENT, mTrustAgentClickIntent);
        }
	}
	 
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
		Log.d(TAG,"onPreferenceTreeClick() + fragment:"	+ preference.getFragment());
	    final String key = preference.getKey();
	    if (KEY_UNLOCK_SET_OR_CHANGE.equals(key)) {
            startFragment(this, "com.android.settings.ChooseLockGeneric$ChooseLockGenericFragment",
                    R.string.lock_settings_picker_title, SET_OR_CHANGE_LOCK_METHOD_REQUEST, null);
        } else if(KEY_SCREEN_LOCK_STYLE.equals(key)){
        	Activity activity = getActivity();
        	ComponentName comp=new ComponentName("com.huawei.android.thememanager",
        			"com.huawei.android.thememanager.diyresource.BaseLocalResListActivity");
        			//"com.huawei.android.thememanager.HwThemeManagerActivity");
        	Intent intent = new Intent();
        	//intent.setComponent(comp);
        	intent.setAction("huawei.intent.action.HUAWEI_UNLOCK_STYLE");
        	intent.addCategory("android.intent.category.DEFAULT");
        	activity.startActivity(intent);
        }else {
            // If we didn't handle it, let preferences handle it.
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }
	    
	    return true;
	}
	
	public boolean onPreferenceChange(Preference preference, Object value) {
        boolean result = true;
        final String key = preference.getKey();
        final LockPatternUtils lockPatternUtils = mChooseLockSettingsHelper.utils();
        
        if (KEY_LOCK_AFTER_TIMEOUT.equals(key)) {
            int timeout = Integer.parseInt((String) value);
            try {
                Settings.Secure.putInt(getContentResolver(),
                        Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT, timeout);
            } catch (NumberFormatException e) {
                Log.e("SecuritySettings", "could not persist lockAfter timeout setting", e);
            }
            updateLockAfterPreferenceSummary();
        }else if (KEY_LOCK_ENABLED.equals(key)) {
        	
        	Settings.System.putInt(getContentResolver(), Settings.System.LOCKSCREEN_DISABLED,
                    ((Boolean) value) ? 1 : 0);
        	
        	startFragment(this, "com.android.settings.ScreenLockEnabled$ScreenLockEnabledFragment",
                    R.string.lock_settings_picker_title, SET_OR_CHANGE_LOCK_METHOD_REQUEST, null);
        	
        }else if (KEY_POWER_INSTANTLY_LOCKS.equals(key)) {
            mLockPatternUtils.setPowerButtonInstantlyLocks((boolean) value);
        }else if (KEY_VISIBLE_PATTERN.equals(key)) {
        	mLockPatternUtils.setVisiblePatternEnabled((boolean) value);
        }
        //HQ_jiazaizheng 20150813 modify for HQ01322784 start
        else if (KEY_DROP_DOWN_NOTIFICATION_BAR.equals(key)) {
            mDropdownNotificationbar.setChecked((boolean)value);
            Settings.System.putInt(getContentResolver(),
                    ENABLE_EXPAND_ON_HUAWEI_UNLOCK, (boolean)value ? 1 : 0);
        }//HQ_jiazaizheng 20150813 modify for HQ01322784 end
        
        else if (KEY_SHOW_DETAULED_NOTIFICATIONS.equals(key)) {
        	mShowDetailedNotifications.setChecked((boolean)value);
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, (boolean)value ? 1 : 0);

	    //add by caoxuhao to allow or prevent the notification detial information
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS, (boolean)value ? 1 : 0);
        }
        else if (KEY_USE_PASSWORD_LOCK_ONLY.equals(key)) {
        	mUsePasswordLockOnly.setChecked((boolean)value);
            Settings.System.putInt(getContentResolver(),
            		SettingsEx.System.SHOW_HWLOCK_FIRST, (boolean)value ? 0 : 1);
        }
        else if (KEY_NOTIFICATION_TURN_ON_SCREEN.equals(key)) {
        	mNotificationTurnOnScreen.setChecked((boolean)value);
        	Settings.Secure.putInt(getContentResolver(),
            		WAKEUP_WHEN_RECEIVE_NOTIFICATION, (boolean)value ? 1 : 0);
        }
        return result;
    }
	
	//HQ_jiazaizheng 20150813 modify for HQ01322784 start
    private boolean isDropdownNotificationBarAllowed() {
        int speed = Settings.System.getInt(getContentResolver(),
                ENABLE_EXPAND_ON_HUAWEI_UNLOCK, 1);
        if (speed == 1) {
            return true;
        } else {
            return false;
        }
    }//HQ_jiazaizheng 20150813 modify for HQ01322784 end
    
    private boolean isShowDetailedNotificationsAllowed() {
        int speed = Settings.Secure.getInt(getContentResolver(),
        		Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, 0);
        if (speed == 1) {
            return true;
        } else {
            return false;
        }
    }
    
    //add by HQ_caoxuhao at 20150906 HQ01369002 begin
    private boolean isLockEnabledAllowed() {
        
    	//add by HQ_jiangchao at 20151020 HQ01367571 begin
        if(!mLockPatternUtils.isSecure()){
           if (mLockPatternUtils.isLockScreenDisabled()) {
                return false;
            }
        }
            return true;

      //add by HQ_caoxuhao at 20151020 HQ01367571 end
    }
    //add by HQ_caoxuhao at 20150906 HQ01369002 end
    
    private boolean isNotificationTurnOnScreenAllowed() {
        int speed = Settings.Secure.getInt(getContentResolver(),
        		WAKEUP_WHEN_RECEIVE_NOTIFICATION, 0);
        if (speed == 1) {
            return true;
        } else {
            return false;
        }
    }
    
    
    private boolean isUsePasswordLockOnlyAllowed() {
        int speed = Settings.System.getInt(getContentResolver(),
        		SettingsEx.System.SHOW_HWLOCK_FIRST, 1);
        if (speed == 0) {
            return true;
        } else {
            return false;
        }
    }
    
    private void setupLockAfterPreference() {
        // Compatible with pre-Froyo
        long currentTimeout = Settings.Secure.getLong(getContentResolver(),
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT, 5000);
        mLockAfter.setValue(String.valueOf(currentTimeout));
        mLockAfter.setOnPreferenceChangeListener(this);
        final long adminTimeout = (mDPM != null ? mDPM.getMaximumTimeToLock(null) : 0);
        final long displayTimeout = Math.max(0,
                Settings.System.getInt(getContentResolver(), SCREEN_OFF_TIMEOUT, 0));
        if (adminTimeout > 0) {
            // This setting is a slave to display timeout when a device policy is enforced.
            // As such, maxLockTimeout = adminTimeout - displayTimeout.
            // If there isn't enough time, shows "immediately" setting.
            disableUnusableTimeouts(Math.max(0, adminTimeout - displayTimeout));
        }
    }
    
    private void disableUnusableTimeouts(long maxTimeout) {
        final CharSequence[] entries = mLockAfter.getEntries();
        final CharSequence[] values = mLockAfter.getEntryValues();
        ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.valueOf(values[i].toString());
            if (timeout <= maxTimeout) {
                revisedEntries.add(entries[i]);
                revisedValues.add(values[i]);
            }
        }
        if (revisedEntries.size() != entries.length || revisedValues.size() != values.length) {
            mLockAfter.setEntries(
                    revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            mLockAfter.setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));
            final int userPreference = Integer.valueOf(mLockAfter.getValue());
            if (userPreference <= maxTimeout) {
                mLockAfter.setValue(String.valueOf(userPreference));
            } else {
                // There will be no highlighted selection since nothing in the list matches
                // maxTimeout. The user can still select anything less than maxTimeout.
                // TODO: maybe append maxTimeout to the list and mark selected.
            }
        }
        mLockAfter.setEnabled(revisedEntries.size() > 0);
    }
    
    private void updateLockAfterPreferenceSummary() {
        // Update summary message with current value
        long currentTimeout = Settings.Secure.getLong(getContentResolver(),
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT, 5000);
        final CharSequence[] entries = mLockAfter.getEntries();
        final CharSequence[] values = mLockAfter.getEntryValues();
        int best = 0;
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.valueOf(values[i].toString());
            if (currentTimeout >= timeout) {
                best = i;
            }
        }

        Preference preference = getPreferenceScreen().findPreference(KEY_TRUST_AGENT);
        if (preference != null && preference.getTitle().length() > 0) {
            mLockAfter.setSummary(getString(R.string.lock_after_timeout_summary_with_exception,
                    entries[best], preference.getTitle()));
        } else {
            mLockAfter.setSummary(getString(R.string.lock_after_timeout_summary, entries[best]));
        }
        mLockAfter.setValue(String.valueOf(currentTimeout));
    }
    
    private void enableALLPreferences(){
    	if (mScreenLockStyle!=null) mScreenLockStyle.setEnabled(true);
    	if (mUnlockSetOrChange!=null) mUnlockSetOrChange.setEnabled(true);
    	if (mDropdownNotificationbar!=null) mDropdownNotificationbar.setEnabled(true);
    	if (mNotificationTurnOnScreen!=null) mNotificationTurnOnScreen.setEnabled(true);
    	if (mShowDetailedNotifications!=null) mShowDetailedNotifications.setEnabled(true);
    	if (mLockScreenSignature!=null) mLockScreenSignature.setEnabled(true);
    }
    
    private void disableALLPreferences(){
    	if (mScreenLockStyle!=null) mScreenLockStyle.setEnabled(false);
    	if (mUnlockSetOrChange!=null) mUnlockSetOrChange.setEnabled(false);
    	if (mDropdownNotificationbar!=null) mDropdownNotificationbar.setEnabled(false);
    	if (mNotificationTurnOnScreen!=null) mNotificationTurnOnScreen.setEnabled(false);
    	if (mShowDetailedNotifications!=null) mShowDetailedNotifications.setEnabled(false);
    	if (mLockScreenSignature!=null) mLockScreenSignature.setEnabled(false);
    }
	 
}
