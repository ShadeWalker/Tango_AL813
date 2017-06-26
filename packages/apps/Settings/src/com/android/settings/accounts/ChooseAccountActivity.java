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

package com.android.settings.accounts;

import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncAdapterType;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.util.Log;

import com.android.internal.util.CharSequences;
import com.android.settings.R;
import com.android.settings.Utils;

import com.google.android.collect.Maps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import android.provider.Settings;
import android.provider.Settings.System;

import static android.content.Intent.EXTRA_USER;

/**
 * Activity asking a user to select an account to be set up.
 *
 * An extra {@link UserHandle} can be specified in the intent as {@link EXTRA_USER}, if the user for
 * which the action needs to be performed is different to the one the Settings App will run in.
 */
public class ChooseAccountActivity extends PreferenceActivity {

    private static final String TAG = "ChooseAccountActivity";
    private String[] mAuthorities;
    private PreferenceGroup mAddAccountGroup;
    private final ArrayList<ProviderEntry> mProviderList = new ArrayList<ProviderEntry>();
    public HashSet<String> mAccountTypesFilter;
    private AuthenticatorDescription[] mAuthDescs;
    private HashMap<String, ArrayList<String>> mAccountTypeToAuthorities = null;
    private Map<String, AuthenticatorDescription> mTypeToAuthDescription
            = new HashMap<String, AuthenticatorDescription>();
    // The UserHandle of the user we are choosing an account for
    private UserHandle mUserHandle;
    private UserManager mUm;

    private static class ProviderEntry implements Comparable<ProviderEntry> {
        private final CharSequence name;
        private final String type;
        ProviderEntry(CharSequence providerName, String accountType) {
            name = providerName;
            type = accountType;
        }

        public int compareTo(ProviderEntry another) {
            if (name == null) {
                return -1;
            }
            if (another.name == null) {
                return +1;
            }
            return CharSequences.compareToIgnoreCase(name, another.name);
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.add_account_screen);
        addPreferencesFromResource(R.xml.add_account_settings);
        mAuthorities = getIntent().getStringArrayExtra(
                AccountPreferenceBase.AUTHORITIES_FILTER_KEY);
        String[] accountTypesFilter = getIntent().getStringArrayExtra(
                AccountPreferenceBase.ACCOUNT_TYPES_FILTER_KEY);
        if (accountTypesFilter != null) {
            mAccountTypesFilter = new HashSet<String>();
            for (String accountType : accountTypesFilter) {
                mAccountTypesFilter.add(accountType);
            }
        }
        mAddAccountGroup = getPreferenceScreen();
        mUm = UserManager.get(this);
        mUserHandle = Utils.getSecureTargetUser(getActivityToken(), mUm, null /* arguments */,
                getIntent().getExtras());
        updateAuthDescriptions();
        
        //HQ_hanchao 20150919 modify for  HQ01349030 start
        if(mAuthorities != null){
            if("com.android.calendar".equals(mAuthorities[0])){
            	finishWithAccountType("com.android.exchange");       	
            }
        }
        //HQ_hanchao 20150919 modify for  HQ01349030 end
    }

    /**
     * Updates provider icons. Subclasses should call this in onCreate()
     * and update any UI that depends on AuthenticatorDescriptions in onAuthDescriptionsUpdated().
     */
    private void updateAuthDescriptions() {
        mAuthDescs = AccountManager.get(this).getAuthenticatorTypesAsUser(
                mUserHandle.getIdentifier());
        for (int i = 0; i < mAuthDescs.length; i++) {
            mTypeToAuthDescription.put(mAuthDescs[i].type, mAuthDescs[i]);
        }
        onAuthDescriptionsUpdated();
    }

    //hanchao add begin HQ01347839
    private Drawable zoomDrawable(Drawable drawable, int w, int h) {  
        int width = drawable.getIntrinsicWidth();  
        int height = drawable.getIntrinsicHeight();  
        Bitmap oldbmp = drawableToBitmap(drawable);  
        Matrix matrix = new Matrix();  
        float scaleWidth = ((float) w / width);  
        float scaleHeight = ((float) h / height);  
        matrix.postScale(scaleWidth, scaleHeight);  
        Bitmap newbmp = Bitmap.createBitmap(oldbmp, 0, 0, width, height,  
                matrix, true);  
        return new BitmapDrawable(null, newbmp);  
    }  
      
    private Bitmap drawableToBitmap(Drawable drawable) {  
        int width = drawable.getIntrinsicWidth();  
        int height = drawable.getIntrinsicHeight();  
        Bitmap.Config config = drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888  
                : Bitmap.Config.RGB_565;  
        Bitmap bitmap = Bitmap.createBitmap(width, height, config);  
        Canvas canvas = new Canvas(bitmap);  
        drawable.setBounds(0, 0, width, height);  
        drawable.draw(canvas);  
        return bitmap;  
    }    
    //hanchao add end HQ01347839
    private void onAuthDescriptionsUpdated() {
       String HW_login = Settings.System.getString(getContentResolver(),Settings.System.HW_LOGIN);/*HQ_jiangchao modify for Huawei account login at 20151012 */
        
        // Create list of providers to show on preference screen
        for (int i = 0; i < mAuthDescs.length; i++) {
            String accountType = mAuthDescs[i].type;
            CharSequence providerName = getLabelForType(accountType);

            // Skip preferences for authorities not specified. If no authorities specified,
            // then include them all.
            ArrayList<String> accountAuths = getAuthoritiesForAccountType(accountType);
            boolean addAccountPref = true;
            if (mAuthorities != null && mAuthorities.length > 0 && accountAuths != null) {
                addAccountPref = false;
                for (int k = 0; k < mAuthorities.length; k++) {
                    if (accountAuths.contains(mAuthorities[k])) {
                        addAccountPref = true;
                        break;
                    }
                }
            }
            if (addAccountPref && mAccountTypesFilter != null
                    && !mAccountTypesFilter.contains(accountType)) {
                addAccountPref = false;
            }
            /*HQ_yuankangbo 2015-08-14 modify for hide QQ account start*/
            if ("com.tencent.mobileqq.account".equals(accountType)){
                addAccountPref = false;
            }
            /*HQ_yuankangbo 2015-08-14 modify for hide QQ account end*/
            /*HQ_jiangchao modify for Huawei account login at 20151012 start*/
            if("true".equals(HW_login) && "com.huawei.hwid".equals(accountType)){
                addAccountPref = false;
            }
            /*HQ_jiangchao modify end*/
            if (addAccountPref) {
                mProviderList.add(new ProviderEntry(providerName, accountType));
            } else {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "Skipped pref " + providerName + ": has no authority we need");
                }
            }
        }

        if (mProviderList.size() == 1) {
            // If there's only one provider that matches, just run it.
            finishWithAccountType(mProviderList.get(0).type);
        } else if (mProviderList.size() > 0) {
            Collections.sort(mProviderList);
            mAddAccountGroup.removeAll();
            for (ProviderEntry pref : mProviderList) {
                Drawable drawable = getDrawableForType(pref.type);                
                Drawable mdrawable = zoomDrawable(drawable, 150, 150);        //hanchao add HQ01347839       
                ProviderPreference p =
                        new ProviderPreference(this, pref.type, mdrawable, pref.name);
                mAddAccountGroup.addPreference(p);
            }
        } else {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                final StringBuilder auths = new StringBuilder();
                for (String a : mAuthorities) {
                    auths.append(a);
                    auths.append(' ');
                }
                Log.v(TAG, "No providers found for authorities: " + auths);
            }
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    public ArrayList<String> getAuthoritiesForAccountType(String type) {
        if (mAccountTypeToAuthorities == null) {
            mAccountTypeToAuthorities = Maps.newHashMap();
            SyncAdapterType[] syncAdapters = ContentResolver.getSyncAdapterTypesAsUser(
                    mUserHandle.getIdentifier());
            for (int i = 0, n = syncAdapters.length; i < n; i++) {
                final SyncAdapterType sa = syncAdapters[i];
                ArrayList<String> authorities = mAccountTypeToAuthorities.get(sa.accountType);
                if (authorities == null) {
                    authorities = new ArrayList<String>();
                    mAccountTypeToAuthorities.put(sa.accountType, authorities);
                }
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.d(TAG, "added authority " + sa.authority + " to accountType "
                            + sa.accountType);
                }
                authorities.add(sa.authority);
            }
        }
        return mAccountTypeToAuthorities.get(type);
    }

    /**
     * Gets an icon associated with a particular account type. If none found, return null.
     * @param accountType the type of account
     * @return a drawable for the icon or null if one cannot be found.
     */
    protected Drawable getDrawableForType(final String accountType) {
        Drawable icon = null;
        if (mTypeToAuthDescription.containsKey(accountType)) {
            try {
                AuthenticatorDescription desc = mTypeToAuthDescription.get(accountType);
                Context authContext = createPackageContextAsUser(desc.packageName, 0, mUserHandle);
                icon = getPackageManager().getUserBadgedIcon(
                        authContext.getDrawable(desc.iconId), mUserHandle);
            } catch (PackageManager.NameNotFoundException e) {
                // TODO: place holder icon for missing account icons?
                Log.w(TAG, "No icon name for account type " + accountType);
            } catch (Resources.NotFoundException e) {
                // TODO: place holder icon for missing account icons?
                Log.w(TAG, "No icon resource for account type " + accountType);
            }
        }
        return icon;
    }

    /**
     * Gets the label associated with a particular account type. If none found, return null.
     * @param accountType the type of account
     * @return a CharSequence for the label or null if one cannot be found.
     */
    protected CharSequence getLabelForType(final String accountType) {
        CharSequence label = null;
        if (mTypeToAuthDescription.containsKey(accountType)) {
            try {
                AuthenticatorDescription desc = mTypeToAuthDescription.get(accountType);
                Context authContext = createPackageContextAsUser(desc.packageName, 0, mUserHandle);
                label = authContext.getResources().getText(desc.labelId);
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "No label name for account type " + accountType);
            } catch (Resources.NotFoundException e) {
                Log.w(TAG, "No label resource for account type " + accountType);
            }
        }
        return label;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferences, Preference preference) {
        if (preference instanceof ProviderPreference) {
            ProviderPreference pref = (ProviderPreference) preference;
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Attempting to add account of type " + pref.getAccountType());
            }
            finishWithAccountType(pref.getAccountType());
        }
        return true;
    }

    private void finishWithAccountType(String accountType) {
        Intent intent = new Intent();
        intent.putExtra(AddAccountSettings.EXTRA_SELECTED_ACCOUNT, accountType);
        intent.putExtra(EXTRA_USER, mUserHandle);
        setResult(RESULT_OK, intent);
        finish();
    }
}
