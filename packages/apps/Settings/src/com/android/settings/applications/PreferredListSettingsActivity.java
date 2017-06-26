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
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.applications;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import com.android.settings.R;
import android.util.Log;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.MediaStore;
import com.android.internal.telephony.SmsApplication;
import com.android.internal.telephony.SmsApplication.SmsApplicationData;

import java.util.ArrayList;
import java.util.List;

public class PreferredListSettingsActivity extends PreferenceActivity {
    private static final String TAG = "PreferredListSettingsActivity";
    private static final String LISTPREF_HOME_KEY = "key_preference_home";
    private static final String LISTPREF_CALL_KEY = "key_preference_call";
    private static final String LISTPREF_MESSAGE_KEY = "key_preference_message";
    private static final String LISTPREF_CAMERA_KEY = "key_preference_camera";

    private static final String LISTPREF_GALLERY_KEY = "key_preference_gallery";
    private static final String LISTPREF_MUSIC_KEY = "key_preference_music";
    private static final String LISTPREF_VIDEO_KEY = "key_preference_video";
    private static final String LISTPREF_EMAIL_KEY = "key_preference_email";

    private static final String LISTPREF_BROWSER_KEY = "key_preference_browser";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferred_list_settings);
        String[] array = getResources().getStringArray(R.array.preferred_app_entries);
        List<PreferenceScreen> preferenceList = new ArrayList<PreferenceScreen>();
        PreferenceScreen homeScreen = (PreferenceScreen) findPreference(LISTPREF_HOME_KEY);
        preferenceList.add(homeScreen);
        PreferenceScreen callScreen = (PreferenceScreen) findPreference(LISTPREF_CALL_KEY);
        preferenceList.add(callScreen);
        PreferenceScreen messageScreen = (PreferenceScreen) findPreference(LISTPREF_MESSAGE_KEY);
        preferenceList.add(messageScreen);
        PreferenceScreen cameraScreen = (PreferenceScreen) findPreference(LISTPREF_CAMERA_KEY);
        preferenceList.add(cameraScreen);
        PreferenceScreen galleryScreen = (PreferenceScreen) findPreference(LISTPREF_GALLERY_KEY);
        preferenceList.add(galleryScreen);
        PreferenceScreen musicScreen = (PreferenceScreen) findPreference(LISTPREF_MUSIC_KEY);
        preferenceList.add(musicScreen);
        PreferenceScreen videoScreen = (PreferenceScreen) findPreference(LISTPREF_VIDEO_KEY);
        preferenceList.add(videoScreen);
        PreferenceScreen emailScreen = (PreferenceScreen) findPreference(LISTPREF_EMAIL_KEY);
        preferenceList.add(emailScreen);
        PreferenceScreen browserScreen = (PreferenceScreen) findPreference(LISTPREF_BROWSER_KEY);
        preferenceList.add(browserScreen);
        int size = preferenceList.size();
        for (int i=0; i<size; i++) {
            PreferenceScreen screen = preferenceList.get(i);
            screen.setTitle(array[i]);
            screen.setSummary(getPreferredSummary(screen.getKey()));
        }
    }

    public String getPreferredSummary(String key) {
        if (key == null) return null;
        String summary = null;
        Intent startIntent = null;
        if (key.equals(LISTPREF_HOME_KEY)) {
            startIntent = new Intent();
            startIntent.setAction(Intent.ACTION_MAIN);
            startIntent.addCategory(Intent.CATEGORY_HOME);
        } else if (key.equals(LISTPREF_CALL_KEY)) {
            startIntent = new Intent();
            startIntent.setAction(Intent.ACTION_DIAL);
            startIntent.setData(Uri.parse("tel:"));
        } else if (key.equals(LISTPREF_MESSAGE_KEY)) {
            startIntent = new Intent();
            startIntent.setAction(Intent.ACTION_SENDTO);
            startIntent.setData(Uri.parse("smsto:"));
            ComponentName defaultComponent = SmsApplication.getDefaultSmsApplication(this, false);
            if (defaultComponent == null) return null;//HQ_hushunli 2016-01-19 add for HQ01680052
            String packageName = defaultComponent.getPackageName();
            String smsPreferredSummary = "";
			/*HQ_liugang add for HQ01699074 20160127*/
			if(!"com.android.contacts".equals(packageName)) {
	            try {
	                smsPreferredSummary = getPackageManager().getApplicationLabel(
	                        getPackageManager().getApplicationInfo(packageName, PackageManager.GET_META_DATA)).toString();
	            } catch(NameNotFoundException e) {
	                e.printStackTrace();
	            }
			} else {            			
				Intent smsIntent = new Intent();
				ComponentName smsComponent = new ComponentName(packageName, "com.android.mms.ui.ConversationList");
				smsIntent.setComponent(smsComponent);

	        	List<ResolveInfo> smsList = getPackageManager().queryIntentActivities(smsIntent, (PackageManager.GET_INTENT_FILTERS
	                                                 | PackageManager.MATCH_DEFAULT_ONLY
	                                                 | PackageManager.GET_RESOLVED_FILTER
	                                                 ));
				smsPreferredSummary = smsList.get(0).loadLabel(getPackageManager()).toString();
					
				Log.i(TAG, "sms " + smsPreferredSummary);
			}
			/*HQ_liugang add end*/
            return smsPreferredSummary;
            
        } else if (key.equals(LISTPREF_CAMERA_KEY)) {
            startIntent = new Intent();
            startIntent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
        } else if (key.equals(LISTPREF_GALLERY_KEY)) {
            startIntent = new Intent();
            startIntent.setAction(Intent.ACTION_MAIN);
            startIntent.addCategory(Intent.CATEGORY_APP_GALLERY);
        } else if (key.equals(LISTPREF_MUSIC_KEY)) {
            startIntent = new Intent();
            startIntent.setAction(Intent.ACTION_VIEW);
            startIntent.setDataAndType(Uri.parse("file:///"), "audio/*");
        } else if (key.equals(LISTPREF_VIDEO_KEY)) {
            startIntent = new Intent();
            startIntent.setAction(Intent.ACTION_VIEW);
            startIntent.setDataAndType(Uri.parse("file:///"), "video/*");
        } else if (key.equals(LISTPREF_EMAIL_KEY)) {
            startIntent = new Intent();
            startIntent.setAction(Intent.ACTION_SENDTO);
            startIntent.setData(Uri.parse("mailto:"));
        } else if (key.equals(LISTPREF_BROWSER_KEY)) {
            startIntent = new Intent();
            startIntent.setAction(Intent.ACTION_VIEW);
            startIntent.addCategory(Intent.CATEGORY_BROWSABLE);
            startIntent.setData(Uri.parse("http://"));
        }
        List<ResolveInfo> mList = getPackageManager().queryIntentActivities(startIntent, (PackageManager.GET_INTENT_FILTERS
                                                 | PackageManager.MATCH_DEFAULT_ONLY
                                                 | PackageManager.GET_RESOLVED_FILTER
                                            ));
        if (mList != null && mList.size() > 0) {
            for (ResolveInfo resolveInfo : mList) {
                String packageName = resolveInfo.activityInfo.applicationInfo.packageName;
                String label = null;
                try {
                    label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName, PackageManager.GET_META_DATA)).toString();
                } catch(NameNotFoundException e) {
                    e.printStackTrace();
                }
                List<ComponentName> prefActList = new ArrayList<ComponentName>();
                List<IntentFilter> intentList = new ArrayList<IntentFilter>();
                getPackageManager().getPreferredActivities(intentList, prefActList, packageName);
                if (prefActList.size() > 0) {
                    //the prefActList.size() > 0 item is the default app.
                    summary = label;
                    break;
                }
            }
            //there are many queryed ResolveInfo object,but no one is default app,so use the first one's
            //application name as the summary.
            if (summary == null) {
                String firstPkName = mList.get(0).activityInfo.applicationInfo.packageName;
                String firstLabel = null;
                try {
                    firstLabel = getPackageManager().getApplicationLabel(
                            getPackageManager().getApplicationInfo(firstPkName, PackageManager.GET_META_DATA)).toString();
                } catch(NameNotFoundException e) {
                    e.printStackTrace();
                }
                summary = firstLabel;
            }
        }
        return summary;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (PreferredSettingsActivity.updatedPreferenceSummary != null
            && !getResources().getString(R.string.app_default_summary).equals(PreferredSettingsActivity.updatedPreferenceSummary)
            && PreferredSettingsActivity.updatedPreferenceSummary.contains(":")) {
            String[] keyAndLabel = PreferredSettingsActivity.updatedPreferenceSummary.split(":");
            String key = keyAndLabel[0];
            String label = keyAndLabel[1];
            PreferenceScreen root = getPreferenceScreen();
            if (root != null) {
                PreferenceScreen preference = (PreferenceScreen)root.findPreference(key);
                preference.setSummary(label);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        Intent intent = new Intent(this, PreferredSettingsActivity.class);
        intent.putExtra("title", preference.getTitle());
        intent.putExtra("key",preference.getKey());
        startActivity(intent);
        return false;
    }
}
