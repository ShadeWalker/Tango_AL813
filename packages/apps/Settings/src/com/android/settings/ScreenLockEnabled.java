package com.android.settings;

import android.security.KeyStore;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Fragment;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.util.MutableBoolean;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;

import com.android.internal.widget.LockPatternUtils;

public class ScreenLockEnabled extends SettingsActivity {
	public static final String CONFIRM_CREDENTIALS = "confirm_credentials";

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, getFragmentClass().getName());
        return modIntent;
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (ScreenLockEnabledFragment.class.getName().equals(fragmentName)) return true;
        return false;
    }

    /* package */ Class<? extends Fragment> getFragmentClass() {
        return ScreenLockEnabledFragment.class;
    }

    public static class InternalActivity extends ScreenLockEnabled {
    }
    
    public static class ScreenLockEnabledFragment extends SettingsPreferenceFragment {
    	
    	private static final String TAG = "ScreenLockEnabledFragment";
    	
    	public static final String EXTRA_SHOW_FRAGMENT_TITLE = ":settings:show_fragment_title";
    	private static final boolean ALWAY_SHOW_TUTORIAL = true;
    	
    	private static final int MIN_PASSWORD_LENGTH = 4;
        private static final String KEY_UNLOCK_BACKUP_INFO = "unlock_backup_info";
        private static final String KEY_UNLOCK_SET_OFF = "unlock_set_off";
        private static final String KEY_UNLOCK_SET_NONE = "unlock_set_none";
        private static final String KEY_UNLOCK_SET_BIOMETRIC_WEAK = "unlock_set_biometric_weak";
        private static final String KEY_UNLOCK_SET_PIN = "unlock_set_pin";
        private static final String KEY_UNLOCK_SET_PASSWORD = "unlock_set_password";
        private static final String KEY_UNLOCK_SET_PATTERN = "unlock_set_pattern";
    	
    	private static final String PASSWORD_CONFIRMED = "password_confirmed";
        private static final String WAITING_FOR_CONFIRMATION = "waiting_for_confirmation";
        private static final String FINISH_PENDING = "finish_pending";
        public static final String ENCRYPT_REQUESTED_QUALITY = "encrypt_requested_quality";
        public static final String ENCRYPT_REQUESTED_DISABLED = "encrypt_requested_disabled";
        public static final String MINIMUM_QUALITY_KEY = "minimum_quality";
        
        private static final int CONFIRM_EXISTING_REQUEST = 100;
        private static final int FALLBACK_REQUEST = 101;
        private static final int ENABLE_ENCRYPTION_REQUEST = 102;
    	
    	private ChooseLockSettingsHelper mChooseLockSettingsHelper;
    	private DevicePolicyManager mDPM;
    	private KeyStore mKeyStore;
    	
    	private boolean mPasswordConfirmed = false;
    	private boolean mWaitingForConfirmation = false;
        private boolean mFinishPending = false;
        private int mEncryptionRequestQuality;
        private boolean mEncryptionRequestDisabled;
        private boolean mRequirePassword;
        private LockPatternUtils mLockPatternUtils;
        private boolean mIsLockEnabledAllowed;
    	
    	@Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            
            mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            mKeyStore = KeyStore.getInstance();
            mChooseLockSettingsHelper = new ChooseLockSettingsHelper(this.getActivity());
            mLockPatternUtils = new LockPatternUtils(getActivity());
            
            mIsLockEnabledAllowed = isLockEnabledAllowed();

            // Defaults to needing to confirm credentials
            final boolean confirmCredentials = getActivity().getIntent()
                .getBooleanExtra(CONFIRM_CREDENTIALS, true);
            if (getActivity() instanceof ScreenLockEnabled.InternalActivity) {
                mPasswordConfirmed = !confirmCredentials;
            }

            if (savedInstanceState != null) {
                mPasswordConfirmed = savedInstanceState.getBoolean(PASSWORD_CONFIRMED);
                mWaitingForConfirmation = savedInstanceState.getBoolean(WAITING_FOR_CONFIRMATION);
                mFinishPending = savedInstanceState.getBoolean(FINISH_PENDING);
                mEncryptionRequestQuality = savedInstanceState.getInt(ENCRYPT_REQUESTED_QUALITY);
                mEncryptionRequestDisabled = savedInstanceState.getBoolean(
                        ENCRYPT_REQUESTED_DISABLED);
            }
            
            if (mPasswordConfirmed) {
            	updateNoneScreenLock();
            } else if (!mWaitingForConfirmation) {
                ChooseLockSettingsHelper helper =
                        new ChooseLockSettingsHelper(this.getActivity(), this);
                if (!helper.launchConfirmationActivity(CONFIRM_EXISTING_REQUEST, null, null)) {
                    mPasswordConfirmed = true; // no password set, so no need to confirm
                    updateNoneScreenLock(false);
                } else {
                    mWaitingForConfirmation = true;
                }
            }
        }
    	
    	@Override
        public void onResume() {
            super.onResume();
            if (mFinishPending) {
                mFinishPending = false;
                finish();
            }
        }
    	
    	private boolean isLockEnabledAllowed() {
            int speed = Settings.System.getInt(getContentResolver(), 
            		Settings.System.LOCKSCREEN_DISABLED, 0);
            if (speed == 1) {
                return true;
            } else {
                return false;
            }
        }
    	
    	@Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            mWaitingForConfirmation = false;
            if (requestCode == CONFIRM_EXISTING_REQUEST && resultCode == Activity.RESULT_OK) {
                mPasswordConfirmed = true;
                updateNoneScreenLock();
            } else if (requestCode == FALLBACK_REQUEST) {
                mChooseLockSettingsHelper.utils().deleteTempGallery();
                getActivity().setResult(resultCode);
                finish();
            } else if (requestCode == ENABLE_ENCRYPTION_REQUEST
                    && resultCode == Activity.RESULT_OK) {
                mRequirePassword = data.getBooleanExtra(
                        EncryptionInterstitial.EXTRA_REQUIRE_PASSWORD, true);
                //do something
            } else {
                getActivity().setResult(Activity.RESULT_CANCELED);
                finish();
            }
        }
    	
    	
    	@Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            // Saved so we don't force user to re-enter their password if configuration changes
            outState.putBoolean(PASSWORD_CONFIRMED, mPasswordConfirmed);
            outState.putBoolean(WAITING_FOR_CONFIRMATION, mWaitingForConfirmation);
            outState.putBoolean(FINISH_PENDING, mFinishPending);
            outState.putInt(ENCRYPT_REQUESTED_QUALITY, mEncryptionRequestQuality);
            outState.putBoolean(ENCRYPT_REQUESTED_DISABLED, mEncryptionRequestDisabled);
        }
    	
    	@Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View v = super.onCreateView(inflater, container, savedInstanceState);
            return v;
        }
    	
        private void updateNoneScreenLock() {
        	updateNoneScreenLock(true);
        }
        
        private void updateNoneScreenLock(boolean doFinish) {
        	
        	mFinishPending = true;
        	
        	final PreferenceScreen prefScreen = getPreferenceScreen();
            if (prefScreen != null) {
                prefScreen.removeAll();
            }
            
        	if (mIsLockEnabledAllowed) {
        		updateUnlockMethodAndFinish(
	                     DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED, false, doFinish);
        		addPreferencesFromResource(R.xml.security_settings_chooser);
			}else{
				updateUnlockMethodAndFinish(
                        DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED, true, doFinish);
				addPreferencesFromResource(R.xml.security_settings_lockscreen);
			}
        }
        
        void updateUnlockMethodAndFinish(int quality, boolean disabled, boolean doFinish) {
            // Sanity check. We should never get here without confirming user's existing password.
            if (!mPasswordConfirmed) {
                throw new IllegalStateException("Tried to update password without confirming it");
            }

            final boolean isFallback = getActivity().getIntent()
                .getBooleanExtra(LockPatternUtils.LOCKSCREEN_WEAK_FALLBACK, false);  //M: Modify for voice unlock

            quality = upgradeQuality(quality, null);

            final Context context = getActivity();
           
            if (quality == DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED) {
            	Log.i(TAG,"quality == DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED do finish");
                mChooseLockSettingsHelper.utils().clearLock(false);
                mChooseLockSettingsHelper.utils().setLockScreenDisabled(disabled);
                getActivity().setResult(Activity.RESULT_OK);
                if (doFinish) finish();
                
            } else {
            	Log.i(TAG,"updateUnlockMethodAndFinish do finish");
            	if (doFinish) finish();
            }
        }
    	
        
        private int upgradeQuality(int quality, MutableBoolean allowBiometric) {
            quality = upgradeQualityForDPM(quality);
            quality = upgradeQualityForKeyStore(quality);
            return quality;
        }

        private int upgradeQualityForDPM(int quality) {
            // Compare min allowed password quality
            int minQuality = mDPM.getPasswordQuality(null);
            if (quality < minQuality) {
                quality = minQuality;
            }
            return quality;
        }

        private int upgradeQualityForKeyStore(int quality) {
            if (!mKeyStore.isEmpty()) {
                if (quality < CredentialStorage.MIN_PASSWORD_QUALITY) {
                    quality = CredentialStorage.MIN_PASSWORD_QUALITY;
                }
            }
            return quality;
        }
//    	
//        protected Intent getLockPatternIntent(Context context, final boolean isFallback,
//                final boolean requirePassword, final boolean confirmCredentials) {
//            return ScreenLockEnabled.createIntent(context, isFallback, requirePassword,
//                    confirmCredentials);
//        }
//        
//        private Intent getBiometricSensorIntent() {
//            Intent fallBackIntent = new Intent().setClass(getActivity(),
//            		ScreenLockEnabled.InternalActivity.class);
//            //M: Modify for voice unlock @{
//            fallBackIntent.putExtra(LockPatternUtils.LOCKSCREEN_WEAK_FALLBACK, true);
//            fallBackIntent.putExtra(LockPatternUtils.LOCKSCREEN_WEAK_FALLBACK_FOR,
//                    LockPatternUtils.TYPE_FACE_UNLOCK);
//            //@}
//            fallBackIntent.putExtra(CONFIRM_CREDENTIALS, false);
//            fallBackIntent.putExtra(EXTRA_SHOW_FRAGMENT_TITLE,
//                    R.string.backup_lock_settings_picker_title);
//
//            boolean showTutorial = ALWAY_SHOW_TUTORIAL ||
//                    !mChooseLockSettingsHelper.utils().isBiometricWeakEverChosen();
//            Intent intent = new Intent();
//            intent.setClassName("com.android.facelock", "com.android.facelock.SetupIntro");
//            intent.putExtra("showTutorial", showTutorial);
//            PendingIntent pending = PendingIntent.getActivity(getActivity(), 0, fallBackIntent, 0);
//            intent.putExtra("PendingIntent", pending);
//            return intent;
//        }
    }
}
