package com.mediatek.audioprofile;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.TwoStatePreference;
import android.provider.Settings;

import com.android.settings.R;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.notification.DropDownPreference;
import com.android.settings.notification.NotificationAccessSettings;
import com.android.settings.search.Indexable;

import com.mediatek.xlog.Xlog;

public class AudioProfileNotification extends SettingsPreferenceFragment implements Indexable {

    private static final String TAG = "AudioProfileNotification";

    private Context mContext;
    private PackageManager mPM;

    //Notifications -- migration from android L
    private static final String KEY_NOTIFICATION_PULSE = "notification_pulse";
    private static final String KEY_LOCK_SCREEN_NOTIFICATIONS = "lock_screen_notifications";
    private static final String KEY_NOTIFICATION_ACCESS = "manage_notification_access";
    private TwoStatePreference mNotificationPulse;
    private DropDownPreference mLockscreen;
    private Preference mNotificationAccess;
    
    private boolean mSecure;
    private int mLockscreenSelectedValue;

    /**
     * called to do the initial creation of a fragment
     *
     * @param icicle
     */
    public void onCreate(Bundle icicle) {
        Xlog.d(TAG, "onCreate");
        super.onCreate(icicle);
        mContext = getActivity();
        mPM = mContext.getPackageManager();
        mSecure = new LockPatternUtils(getActivity()).isSecure();

        PreferenceScreen root = getPreferenceScreen();
        if (root != null) {
            root.removeAll();
        }
        addPreferencesFromResource(R.xml.audioprofile_notification);

        initPulse();
        initLockscreenNotifications();

        mNotificationAccess = findPreference(KEY_NOTIFICATION_ACCESS);
        refreshNotificationListeners();
    }

    /**
     * called when the fragment is visible to the user Need to update summary
     * and active profile, register for the profile change
     */
    public void onResume() {
        Xlog.d(TAG, "onResume");
        super.onResume();
    }

    /**
     * called when the fragment is unvisible to the user unregister the profile
     * change listener
     */
    public void onPause() {
        super.onPause();
    }

    // === Pulse notification light ===

    private void initPulse() {
        mNotificationPulse = (TwoStatePreference) findPreference(KEY_NOTIFICATION_PULSE);
        if (mNotificationPulse == null) {
            Xlog.i(TAG, "Preference not found: " + KEY_NOTIFICATION_PULSE);
            return;
        }
        if (!getResources()
                .getBoolean(com.android.internal.R.bool.config_intrusiveNotificationLed)) {
            getPreferenceScreen().removePreference(mNotificationPulse);
        } else {
            updatePulse();
            mNotificationPulse.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final boolean val = (Boolean) newValue;
                    return Settings.System.putInt(getContentResolver(),
                            Settings.System.NOTIFICATION_LIGHT_PULSE,
                            val ? 1 : 0);
                }
            });
        }
    }

    private void updatePulse() {
        if (mNotificationPulse == null) {
            return;
        }
        try {
            mNotificationPulse.setChecked(Settings.System.getInt(getContentResolver(),
                    Settings.System.NOTIFICATION_LIGHT_PULSE) == 1);
        } catch (Settings.SettingNotFoundException snfe) {
            Xlog.e(TAG, Settings.System.NOTIFICATION_LIGHT_PULSE + " not found");
        }
    }

    // === Lockscreen (public / private) notifications ===
    private void initLockscreenNotifications() {
        mLockscreen = (DropDownPreference) findPreference(KEY_LOCK_SCREEN_NOTIFICATIONS);
        if (mLockscreen == null) {
            Xlog.i(TAG, "Preference not found: " + KEY_LOCK_SCREEN_NOTIFICATIONS);
            return;
        }
        mLockscreen.addItem(R.string.lock_screen_notifications_summary_show,
                R.string.lock_screen_notifications_summary_show);
        if (mSecure) {
            mLockscreen.addItem(R.string.lock_screen_notifications_summary_hide,
                    R.string.lock_screen_notifications_summary_hide);
        }
        mLockscreen.addItem(R.string.lock_screen_notifications_summary_disable,
                R.string.lock_screen_notifications_summary_disable);
        updateLockscreenNotifications();
        mLockscreen.setCallback(new DropDownPreference.Callback() {
            @Override
            public boolean onItemSelected(int pos, Object value) {
                final int val = (Integer) value;
                if (val == mLockscreenSelectedValue) {
                    return true;
                }
                final boolean enabled = val != R.string.lock_screen_notifications_summary_disable;
                final boolean show = val == R.string.lock_screen_notifications_summary_show;
                Settings.Secure.putInt(getContentResolver(),
                        Settings.Secure.LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS, show ? 1 : 0);
                Settings.Secure.putInt(getContentResolver(),
                        Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, enabled ? 1 : 0);
                mLockscreenSelectedValue = val;
                return true;
            }
        });
    }

    private void updateLockscreenNotifications() {
        if (mLockscreen == null) {
            return;
        }
        final boolean enabled = getLockscreenNotificationsEnabled();
        final boolean allowPrivate = !mSecure || getLockscreenAllowPrivateNotifications();
        mLockscreenSelectedValue = !enabled ? R.string.lock_screen_notifications_summary_disable :
                allowPrivate ? R.string.lock_screen_notifications_summary_show :
                R.string.lock_screen_notifications_summary_hide;
        mLockscreen.setSelectedValue(mLockscreenSelectedValue);
    }

    // === Notification listeners ===

    private void refreshNotificationListeners() {
        if (mNotificationAccess != null) {
            final int total = NotificationAccessSettings.getListenersCount(mPM);
            if (total == 0) {
                getPreferenceScreen().removePreference(mNotificationAccess);
            } else {
                final int n = NotificationAccessSettings.getEnabledListenersCount(mContext);
                if (n == 0) {
                    mNotificationAccess.setSummary(getResources().getString(
                            R.string.manage_notification_access_summary_zero));
                } else {
                    mNotificationAccess.setSummary(String.format(getResources().getQuantityString(
                            R.plurals.manage_notification_access_summary_nonzero,
                            n, n)));
                }
            }
        }
    }

    private boolean getLockscreenNotificationsEnabled() {
        return Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, 0) != 0;
    }

    private boolean getLockscreenAllowPrivateNotifications() {
        return Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS, 0) != 0;
    }

}
