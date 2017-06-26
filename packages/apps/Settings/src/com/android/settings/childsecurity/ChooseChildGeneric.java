/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings.childsecurity;

import android.app.Activity;
import android.app.Fragment;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.childsecurity.ConfirmChildPattern.ConfirmChildPatternFragment;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.R;

import java.util.List;


public class ChooseChildGeneric extends PreferenceActivity {
    private static final String TAG = ChooseChildLockHelper.TAG;

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, ChooseChildGenericFragment.class.getName());
        modIntent.putExtra(EXTRA_NO_HEADERS, true);
        return modIntent;
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (ChooseChildGenericFragment.class.getName().equals(fragmentName)) return true;
        return false;
    }

    public static class InternalActivity extends ChooseChildGeneric {
    }

    public static class ChooseChildGenericFragment extends SettingsPreferenceFragment {
        private static final int MIN_PASSWORD_LENGTH = 4;
        private static final int MAX_PASSWORD_LENGTH = 16;
        private static final String KEY_CHILD_SET_NONE = "child_set_none";
        private static final String KEY_CHILD_SET_PIN = "child_set_pin";
        private static final String KEY_CHILD_SET_PATTERN = "child_set_pattern";
        private static final int CONFIRM_EXISTING_REQUEST = 100;
        private static final String PASSWORD_CONFIRMED = "password_confirmed";
        private static final String CONFIRM_CREDENTIALS = "confirm_credentials";
        private static final String WAITING_FOR_CONFIRMATION = "waiting_for_confirmation";
        private static final String FINISH_PENDING = "finish_pending";

        private ChooseChildLockHelper mChooseChildLockHelper;
        private boolean mPasswordConfirmed = false;
        private boolean mWaitingForConfirmation = false;
        private boolean mFinishPending = false;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mChooseChildLockHelper = new ChooseChildLockHelper(this.getActivity());

            // Defaults to needing to confirm credentials
            final boolean confirmCredentials = getActivity().getIntent()
                .getBooleanExtra(CONFIRM_CREDENTIALS, true);
            if (getActivity() instanceof ChooseChildGeneric.InternalActivity) {
                mPasswordConfirmed = !confirmCredentials;
            }

            if (savedInstanceState != null) {
                mPasswordConfirmed = savedInstanceState.getBoolean(PASSWORD_CONFIRMED);
                mWaitingForConfirmation = savedInstanceState.getBoolean(WAITING_FOR_CONFIRMATION);
                mFinishPending = savedInstanceState.getBoolean(FINISH_PENDING);
            }

            if (mPasswordConfirmed) {
                updatePreferencesOrFinish();
            } else if (!mWaitingForConfirmation) {
                ChooseChildLockHelper helper =
                        new ChooseChildLockHelper(this.getActivity(), this);
                if (!helper.launchConfirmationActivity(CONFIRM_EXISTING_REQUEST, null, null)) {
                    mPasswordConfirmed = true; // no password set, so no need to confirm
                    updatePreferencesOrFinish();
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

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
                Preference preference) {
            final String key = preference.getKey();
            boolean handled = true;

            if (KEY_CHILD_SET_NONE.equals(key)) {
                updateUnlockMethodAndFinish(ChooseChildLockHelper.CHILD_SECURITY_MODE_NONE, false);
            } else if (KEY_CHILD_SET_PIN.equals(key)) {
                updateUnlockMethodAndFinish(ChooseChildLockHelper.CHILD_SECURITY_MODE_PIN, false);
            } else if (KEY_CHILD_SET_PATTERN.equals(key)) {
                updateUnlockMethodAndFinish(ChooseChildLockHelper.CHILD_SECURITY_MODE_PATTERN, false);
            } else {
                handled = false;
            }
            return handled;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View v = super.onCreateView(inflater, container, savedInstanceState);
            return v;
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            mWaitingForConfirmation = false;
            if (requestCode == CONFIRM_EXISTING_REQUEST && resultCode == Activity.RESULT_OK) {
                mPasswordConfirmed = true;
                updatePreferencesOrFinish();
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
        }

        private void updatePreferencesOrFinish() {
            Intent intent = getActivity().getIntent();
            int quality = intent.getIntExtra(LockPatternUtils.PASSWORD_TYPE_KEY, -1);
            if (quality == -1) {
                // If caller didn't specify password quality, show UI and allow the user to choose.
                final PreferenceScreen prefScreen = getPreferenceScreen();
                if (prefScreen != null) {
                    prefScreen.removeAll();
                }
                addPreferencesFromResource(R.xml.security_child_mode_picker);
            } else {
                updateUnlockMethodAndFinish(quality, false);
            }
        }

        /**
         * Invokes an activity to change the user's pattern, password or PIN based on given quality
         * and minimum quality specified by DevicePolicyManager. If quality is
         * {@link DevicePolicyManager#PASSWORD_QUALITY_UNSPECIFIED}, password is cleared.
         *
         * @param quality the desired quality. Ignored if DevicePolicyManager requires more security
         * @param disabled whether or not to show LockScreen at all. Only meaningful when quality is
         * {@link DevicePolicyManager#PASSWORD_QUALITY_UNSPECIFIED}
         */
        void updateUnlockMethodAndFinish(int quality, boolean disabled) {
            // Sanity check. We should never get here without confirming user's existing password.
            if (!mPasswordConfirmed) {
                throw new IllegalStateException("Tried to update password without confirming it");
            }
            Log.i(TAG, "updateUnlockMethodAndFinish " + quality);

            if (quality == ChooseChildLockHelper.CHILD_SECURITY_MODE_PIN) {
                final int minLength = MIN_PASSWORD_LENGTH;
                final int maxLength = MAX_PASSWORD_LENGTH;
                Intent intent = new Intent().setClass(getActivity(), ChooseChildPassword.class);
                intent.putExtra(LockPatternUtils.PASSWORD_TYPE_KEY, quality);
                intent.putExtra(ChooseChildPassword.PASSWORD_MIN_KEY, minLength);
                intent.putExtra(ChooseChildPassword.PASSWORD_MAX_KEY, maxLength);
                intent.putExtra(CONFIRM_CREDENTIALS, false);

                mFinishPending = true;
                intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                startActivity(intent);
            } else if (quality == ChooseChildLockHelper.CHILD_SECURITY_MODE_PATTERN) {
                Intent intent = new Intent(getActivity(), ChooseChildPattern.class);
                intent.putExtra(ChooseChildPattern.PATTERN_METHOD_TYPE, "pattern");
                intent.putExtra(CONFIRM_CREDENTIALS, false);

                mFinishPending = true;
                intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                startActivity(intent);
            } else if (quality == ChooseChildLockHelper.CHILD_SECURITY_MODE_NONE) {
                mChooseChildLockHelper.setChildLockDisabled();
                getActivity().setResult(Activity.RESULT_OK);
                finish();
            } else {
                finish();
            }
        }

        @Override
        protected int getHelpResource() {
            return R.string.help_url_choose_lockscreen;
        }

    }
}
