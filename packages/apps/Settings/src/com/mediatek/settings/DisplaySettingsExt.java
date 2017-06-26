/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 */

package com.mediatek.settings;

import android.R.integer;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.hardware.display.WifiDisplayStatus;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.SuperscriptSpan;

import com.android.internal.view.RotationPolicy;
import com.android.internal.view.RotationPolicy.RotationPolicyListener;
import com.android.settings.notification.DropDownPreference;
import com.android.settings.R;

import com.mediatek.common.MPlugin;
import com.mediatek.hdmi.HdmiDef;
import com.mediatek.hdmi.IMtkHdmiManager;
import com.mediatek.keyguard.ext.IKeyguardLayer;
import com.mediatek.keyguard.ext.KeyguardLayerInfo;
import com.mediatek.settings.ext.ISettingsMiscExt;
import com.mediatek.settings.ext.IStatusBarPlmnDisplayExt;
import com.mediatek.xlog.Xlog;

import java.util.List;

public class DisplaySettingsExt implements OnPreferenceClickListener {
    private static final String TAG = "mediatek.DisplaySettings";
    private static final String KEY_HDMI_SETTINGS = "hdmi_settings";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_SCREEN_TIMEOUT = "screen_timeout";
    private static final String KEY_ACCELEROMETER = "accelerometer";
    private static final String KEY_WIFI_DISPLAY = "wifi_display";
    private static final String KEY_SCREEN_SAVER = "screensaver";
    //add by HQ_caoxuhao at 20150828 HQ01350351 begin
    private static final String KEY_LED_LIGHT = "led_light";
    private static final String KEY_DISPLAY_OPERATORNAME = "display_operatorname";
    private static final String KEY_BRIGHTNESS = "brightness";
    private static final String KEY_AUTO_ROTATE = "auto_rotate";
    //add by HQ_caoxuhao at 20150828 HQ01350351 end


    private static final String DATA_STORE_NONE = "none";


    private Preference mHDMISettings;
    private ISettingsMiscExt mExt;

    private PreferenceCategory mDisplayPerCategory;
    private PreferenceCategory mDisplayDefCategory;
    private static final String DISPLAY_PERSONALIZE = "display_personalize";
    private static final String DISPLAY_DEFAULT = "display_default";
    private static final String KEY_WALLPAPER = "wallpaper";
    private static final String KEY_MTK_WALLPAPER = "mtk_wallpaper";
    private static final String CONTACT_STRING = "&";
    private static final int PARSER_STRING_LENGTH_ZERO = 0;
    private static final int PARSER_STRING_LENGTH_ONE = 1;
    private static final int PARSER_STRING_LENGTH_TWO = 2;
    Preference mWallpaperPref;

    private static final String LOCK_SCREEN_STYLE_INTENT_PACKAGE = "com.android.settings";
    private static final String LOCK_SCREEN_STYLE_INTENT_NAME = "com.mediatek.lockscreensettings.LockScreenStyleSettings";
    private static final String KEY_LOCK_SCREEN_STYLE = "lock_screen_style";
    public static final String CURRENT_KEYGURAD_LAYER_KEY = "mtk_current_keyguard_layer";

    private Preference mLockScreenStylePref;
    private IStatusBarPlmnDisplayExt mPlmnName;
    private boolean mIsUpdateFont;
    private Context mContext;

    private Preference mScreenTimeoutPreference;
    private ListPreference mFontSizePref;

    //add by HQ_caoxuhao at 20150828 HQ01350351 begin
//    private DropDownPreference mRotatePreference;
    private SwitchPreference mRotatePreference;
    //add by HQ_caoxuhao at 20150828 HQ01350351 end

    private static final int TYPE_CATEGORY = 0;
    private static final int TYPE_PREFERENCE = 1;
    private static final int TYPE_CHECKBOX = 2;
    private static final int TYPE_LIST = 3;
    private static final int SUM_CATEGORY = 2;
    private static final int SUM_DEF_CATEGORY = 4;

    private IMtkHdmiManager mHdmiManager;

    // add for clearMotion
    private static final String KEY_CLEAR_MOTION = "clearMotion";
    private Preference mClearMotion;

    // add for MiraVision
    private static final String KEY_MIRA_VISION = "mira_vision";
    private Preference mMiraVision;
    private Intent mMiraIntent = new Intent("com.android.settings.MIRA_VISION");

    public DisplaySettingsExt(Context context) {
        Xlog.d(TAG, "DisplaySettingsExt");
        mContext = context;

    }

    private BroadcastReceiver mPackageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context1, Intent intent) {
            Xlog.d(TAG, "package changed, update list");
            // add for lockScreen
            updateLockScreenStyle();
        }
    };

    /**
     *
     * @param type
     *            : 0:PreferenceCategory; 1:Preference; 2:CheckBoxPreference;
     *            3:ListPreference
     * @param titleRes
     * @param key
     * @param screen
     * @return
     */
    //add by HQ_caoxuhao at 20150828 HQ01350351 begin
    private Preference createPreference(int type, int titleRes, String key, PreferenceGroup screen){
        return createPreference(type, titleRes, key, screen, -1);
    }
    //add by HQ_caoxuhao at 20150828 HQ01350351 end

    private Preference createPreference(int type, int titleRes, String key, PreferenceGroup screen, int positon) {
        Preference preference = null;
        switch (type) {
        case TYPE_CATEGORY:
            preference = new PreferenceCategory(mContext);
            break;
        case TYPE_PREFERENCE:
            preference = new Preference(mContext);
            break;
        case TYPE_CHECKBOX:
            preference = new CheckBoxPreference(mContext);
            preference.setOnPreferenceClickListener(this);
            break;
        case TYPE_LIST:
            preference = new ListPreference(mContext);
            preference.setOnPreferenceClickListener(this);
            break;
        default:
            break;
        }
        preference.setKey(key);
        preference.setTitle(titleRes);
        if (positon != -1) {
            preference.setOrder(positon);
        }
        screen.addPreference(preference);
        return preference;
    }

    /*
     * initPreference: 1. new all mtk feature preference 2. add all google
     * default preference to mDisplayDefCategory 3. add hdmi and
     * landscapeLauncher behind font_size 4. remove google default wallpaper
     *
     * @screen : UI Screen
     */
    private void initPreference(PreferenceScreen screen) {
        mDisplayPerCategory = (PreferenceCategory) createPreference(TYPE_CATEGORY,
                R.string.display_personalize, DISPLAY_PERSONALIZE, screen);

        mDisplayDefCategory = (PreferenceCategory) createPreference(TYPE_CATEGORY,
                R.string.display_default, DISPLAY_DEFAULT, screen);

        // add for clearMotion
        mClearMotion = createPreference(TYPE_PREFERENCE, R.string.clear_motion_title,
                KEY_CLEAR_MOTION, mDisplayPerCategory);
        mClearMotion.setSummary(R.string.clear_motion_summary);
        if (mClearMotion != null && mDisplayPerCategory != null
                && !FeatureOption.MTK_CLEARMOTION_SUPPORT) {
            mDisplayPerCategory.removePreference(mClearMotion);
        }

        // add for MiraVision
        mMiraVision = createPreference(TYPE_PREFERENCE, R.string.mira_vision_title,
                KEY_MIRA_VISION, mDisplayPerCategory, 0);
        mMiraVision.setSummary(R.string.mira_vision_summary);
        mMiraVision.setWidgetLayoutResource(R.layout.arrow_img_layout);
        List<ResolveInfo> apps = mContext.getPackageManager().queryIntentActivities(mMiraIntent, 0);
        if (apps == null || apps.size() == 0) {
            Xlog.d(TAG, "No MiraVision apk");
            mDisplayPerCategory.removePreference(mMiraVision);
        }
        if (!FeatureOption.MTK_MIRAVISION_SETTING_SUPPORT) {
            Xlog.d(TAG, "No MiraVision lib so");
            mDisplayPerCategory.removePreference(mMiraVision);
        }
        if (android.os.UserHandle.myUserId() != 0
                && "tablet".equals(android.os.SystemProperties.get("ro.build.characteristics"))) {
            Xlog.d(TAG, "Only the owner can see MiraVision Settings");
            mDisplayPerCategory.removePreference(mMiraVision);
        }

        mLockScreenStylePref = createPreference(TYPE_PREFERENCE, R.string.lock_screen_style_title,
                KEY_LOCK_SCREEN_STYLE, mDisplayPerCategory);
        mLockScreenStylePref.setOnPreferenceClickListener(this);
        mLockScreenStylePref.setWidgetLayoutResource(R.layout.arrow_img_layout);

        mWallpaperPref = createPreference(TYPE_PREFERENCE, R.string.wallpaper_settings_title,
                KEY_MTK_WALLPAPER, mDisplayPerCategory, 1);
        mWallpaperPref.setWidgetLayoutResource(R.layout.arrow_img_layout);

        /*HQ_liugang delete for HQ01299750*/
        //mWallpaperPref.setFragment("com.android.settings.WallpaperTypeSettings");

        mHdmiManager = IMtkHdmiManager.Stub.asInterface(ServiceManager
                .getService(Context.HDMI_SERVICE));
        if (mHdmiManager != null) {
            mHDMISettings = createPreference(TYPE_PREFERENCE, R.string.hdmi_settings,
                    KEY_HDMI_SETTINGS, mDisplayDefCategory);
            mHDMISettings.setSummary(R.string.hdmi_settings_summary);
            mHDMISettings.setFragment("com.mediatek.hdmi.HDMISettings");
            try {
                if (mHdmiManager.getDisplayType() == HdmiDef.DISPLAY_TYPE_MHL) {
                    String hdmi = mContext.getString(R.string.hdmi_replace_hdmi);
                    String mhl = mContext.getString(R.string.hdmi_replace_mhl);
                    mHDMISettings.setTitle(mHDMISettings.getTitle().toString()
                            .replaceAll(hdmi, mhl));
                    mHDMISettings.setSummary(mHDMISettings.getSummary().toString().replaceAll(hdmi,
                            mhl));
                }
            } catch (RemoteException e) {
                Xlog.d(TAG, "getDisplayType RemoteException");
            }
        }

        // add all google default preference to mDisplayDefCategory not include
        // wallpaper
        int j = 4;
        for (int i = 0; i < screen.getPreferenceCount() - SUM_CATEGORY; i++) {
            Preference preference = screen.getPreference(i);
            //add by HQ_caoxuhao at 20150828 HQ01350351 begin
            //PefCategory
            if (KEY_FONT_SIZE.equals(preference.getKey())) {
                preference.setOrder(2);
                mDisplayPerCategory.addPreference(preference);
                // add hdmi behind font_size
                if (mHDMISettings != null) {
                    mHDMISettings.setOrder(7);
                }
            }else if (KEY_SCREEN_SAVER.equals(preference.getKey())) {
                preference.setOrder(4);
                mDisplayPerCategory.addPreference(preference);
            }else if (KEY_LED_LIGHT.equals(preference.getKey())) {
                preference.setOrder(5);
                mDisplayPerCategory.addPreference(preference);
            }else if (KEY_DISPLAY_OPERATORNAME.equals(preference.getKey())) {
                preference.setOrder(6);
                mDisplayPerCategory.addPreference(preference);
            }
            //DefCategory
            else if (KEY_BRIGHTNESS.equals(preference.getKey())) {
                preference.setOrder(0);
                mDisplayDefCategory.addPreference(preference);
            }else if (KEY_SCREEN_TIMEOUT.equals(preference.getKey())) {
                preference.setOrder(1);
                mDisplayDefCategory.addPreference(preference);
            }else if (KEY_AUTO_ROTATE.equals(preference.getKey())) {
                preference.setOrder(2);
                mDisplayDefCategory.addPreference(preference);
            }else if (KEY_WIFI_DISPLAY.equals(preference.getKey())) {
                preference.setOrder(3);
                mDisplayDefCategory.addPreference(preference);
            }else {
                preference.setOrder(j);
                mDisplayDefCategory.addPreference(preference);
                j++;
            }
            //add by HQ_caoxuhao at 20150828 HQ01350351 end
        }
        // use for plugin and EM
        mScreenTimeoutPreference = mDisplayPerCategory.findPreference(KEY_SCREEN_TIMEOUT);
        mFontSizePref = (ListPreference) mDisplayPerCategory.findPreference(KEY_FONT_SIZE);

        // remove google default wallpaper, because it move to
        // mDisplayDefCategory
        if (mDisplayDefCategory.findPreference(KEY_WALLPAPER) != null) {
            mDisplayDefCategory.removePreference(mDisplayDefCategory.findPreference(KEY_WALLPAPER));
        }
        Xlog.d(TAG, "Plugin called for adding the prefernce");
        mPlmnName = UtilsExt.getStatusBarPlmnPlugin(mContext);
        mPlmnName.createCheckBox(mDisplayPerCategory, j);

        screen.removeAll();
        screen.addPreference(mDisplayPerCategory);
        screen.addPreference(mDisplayDefCategory);

        // if wfd feature is unavailable, remove cast screen preference
        DisplayManager displayManager = (DisplayManager) mContext
                .getSystemService(Context.DISPLAY_SERVICE);
        WifiDisplayStatus status = displayManager.getWifiDisplayStatus();
        if (status.getFeatureState() == WifiDisplayStatus.FEATURE_STATE_UNAVAILABLE) {
            Xlog.d(TAG, "Wifi display feature is unavailable, remove cast screen pref");
            PreferenceScreen wfdPreferenceScreen = (PreferenceScreen) screen
                    .findPreference(KEY_WIFI_DISPLAY);
            if (wfdPreferenceScreen != null) {
                Xlog.d(TAG, "Find the wfd preference");
                mDisplayDefCategory.removePreference(wfdPreferenceScreen);
            }
        }

        // remove Daydream when MTK_GMO_RAM_OPTIMIZE is true
        if (mDisplayDefCategory.findPreference(KEY_SCREEN_SAVER) != null
                && FeatureOption.MTK_GMO_RAM_OPTIMIZE) {
            mDisplayDefCategory.removePreference(mDisplayDefCategory
                    .findPreference(KEY_SCREEN_SAVER));
        }
    }

    public void onCreate(PreferenceScreen screen) {
        Xlog.d(TAG, "onCreate");
        mExt = UtilsExt.getMiscPlugin(mContext);
        initPreference(screen);
        updateLockScreenStyle();
        mExt.setTimeoutPrefTitle(mScreenTimeoutPreference);
        // for solve a bug
        updateFontSize(mFontSizePref);

    }

    public void onResume() {
        Xlog.d(TAG, "onResume of DisplaySettings");

        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        mContext.registerReceiver(mPackageReceiver, filter);

        // add display new feature
        ContentResolver cr = mContext.getContentResolver();

        /*HQ_liugang delete for HQ01299750
        mWallpaperPref.setSummary(parseString(Settings.System.getString(cr,
                Settings.System.CURRENT_WALLPAPER_NAME)));
                */

        // add for lockScreen
        updateLockScreenStyleSummary();

        // Register the receiver: Smart book plug in/out intent
        mContext.registerReceiver(mSmartBookPlugReceiver, new IntentFilter(
                Intent.ACTION_SMARTBOOK_PLUG));

        //add by HQ_caoxuhao at 20150831 HQ01350351 begin
        ///: Add for Auto-Rotation sync.
//        if (RotationPolicy.isRotationSupported(mContext)) {
//            RotationPolicy.registerRotationPolicyListener(mContext,
//                    mRotationPolicyListener);
//        }
        //add by HQ_caoxuhao at 20150831 HQ01350351 end
    }

    public void onPause() {
        mContext.unregisterReceiver(mPackageReceiver);
        // Unregister the receiver: Smart book plug in/out intent
        mContext.unregisterReceiver(mSmartBookPlugReceiver);

        //add by HQ_caoxuhao at 20150831 HQ01350351 begin
        ///: Add for Auto-Rotation sync.
//        if (RotationPolicy.isRotationSupported(mContext)) {
//            RotationPolicy.unregisterRotationPolicyListener(mContext,
//                    mRotationPolicyListener);
//        }
        //add by HQ_caoxuhao at 20150831 HQ01350351 end
    }

    //add by HQ_caoxuhao at 20150831 HQ01350351 begin
    public void setRotatePreference(SwitchPreference preference) {
      mRotatePreference = preference;
    }

//    public void setRotatePreference(DropDownPreference preference) {
//        mRotatePreference = preference;
//    }


//      private RotationPolicyListener mRotationPolicyListener = new RotationPolicyListener() {
//          @Override
//          public void onChange() {
//              if (mRotatePreference != null) {
//                  mRotatePreference.setSelectedItem(RotationPolicy.isRotationLocked(mContext) ?
//                        1 : 0);
//              }
//          }
//      };
    //add by HQ_caoxuhao at 20150831 HQ01350351 end

    public void removePreference(Preference preference) {
        if (mDisplayDefCategory != null && preference != null) {
            mDisplayDefCategory.removePreference(preference);
        }
        if (mDisplayPerCategory != null && preference != null) {
            mDisplayPerCategory.removePreference(preference);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mLockScreenStylePref) {
            Intent intent = new Intent();
            ComponentName comName = new ComponentName("com.android.settings",
                    "com.mediatek.lockscreensettings.LockScreenStyleSettings");
            intent.setComponent(comName);
            mContext.startActivity(intent);

        } else if (preference == mClearMotion) {
            // add for clearMotion
            Intent intent = new Intent();
            intent.setClass(mContext, ClearMotionSettings.class);
            mContext.startActivity(intent);
        } else if (preference == mMiraVision) {
            // add for MiraVision
            mContext.startActivity(mMiraIntent);
        } else if ( preference == mWallpaperPref){
            /*HQ_liugang add for HQ01299750*/
            Intent intent = new Intent("com.huawei.launcher.wallpaper_setting");
            mContext.startActivity(intent);
            /*HQ_liugang add*/
        }
        return true;
    }

    // add for lockScreen
    private void updateLockScreenStyle() {
        Intent intent = new Intent();
        ComponentName comName = new ComponentName(LOCK_SCREEN_STYLE_INTENT_PACKAGE,
                LOCK_SCREEN_STYLE_INTENT_NAME);
        intent.setComponent(comName);
        List<ResolveInfo> lockScreenStyleApps = mContext.getPackageManager().queryIntentActivities(
                intent, 0);
        boolean hasPlugin = queryPluginKeyguardLayers();
        Xlog.d(TAG, "hasPlugin = " + hasPlugin);
        if (lockScreenStyleApps != null && lockScreenStyleApps.size() != 0 && hasPlugin) {
            Xlog.d(TAG, "lockScreenStyleApps.size()=" + lockScreenStyleApps.size());
            if (mDisplayPerCategory != null && mLockScreenStylePref != null) {
                mDisplayPerCategory.addPreference(mLockScreenStylePref);
            }
        } else {
            Xlog.d(TAG, "lock screen style query return null or size 0 ");
            // There is no lock screen style installed , remove the preference.
            if (mDisplayPerCategory != null && mLockScreenStylePref != null) {
                mDisplayPerCategory.removePreference(mLockScreenStylePref);
            }
            return;
        }

        updateLockScreenStyleSummary();

    }

    /**
     * Get key guard layers from system, a key guard layer should implement IKeyguardLayer interface. Plugin app should make
     * sure the data is valid.
     */
    private boolean queryPluginKeyguardLayers() {
        IKeyguardLayer ext;
        ext = (IKeyguardLayer) MPlugin.createInstance(
                IKeyguardLayer.class.getName(), mContext);
        return ext != null;
    }

    private void updateLockScreenStyleSummary() {
        String lockScreenStyleSummary = parseString(Settings.System.getString(mContext
                .getContentResolver(), CURRENT_KEYGURAD_LAYER_KEY));
        if (lockScreenStyleSummary.equals("")) {
            Xlog.d(TAG, "lockScreenStyleSummary = " + lockScreenStyleSummary);
            mLockScreenStylePref.setSummary(R.string.default_name);
        } else {
            mLockScreenStylePref.setSummary(lockScreenStyleSummary);
        }

    }

    // add display new featue
    public String parseString(final String decodeStr) {
        if (decodeStr == null) {
            Xlog.w(TAG, "parseString error as decodeStr is null");
            return mContext.getString(R.string.default_name);
        }
        String ret = decodeStr;
        String[] tokens = decodeStr.split(CONTACT_STRING);
        int tokenSize = tokens.length;
        if (tokenSize > PARSER_STRING_LENGTH_ONE) {
            PackageManager pm = mContext.getPackageManager();
            Resources resources;
            try {
                resources = pm.getResourcesForApplication(tokens[PARSER_STRING_LENGTH_ZERO]);
            } catch (PackageManager.NameNotFoundException e) {
                Xlog.w(TAG, "parseString can not find pakcage: "
                        + tokens[PARSER_STRING_LENGTH_ZERO]);
                return ret;
            }
            int resId;
            try {
                resId = Integer.parseInt(tokens[PARSER_STRING_LENGTH_ONE]);
            } catch (NumberFormatException e) {
                Xlog
                        .w(TAG, "Invalid format of propery string: "
                                + tokens[PARSER_STRING_LENGTH_ONE]);
                return ret;
            }
            if (tokenSize == PARSER_STRING_LENGTH_TWO) {
                ret = resources.getString(resId);
            } else {
                ret = resources.getString(resId, tokens[PARSER_STRING_LENGTH_TWO]);
            }
        }

        Xlog.d(TAG, "parseString return string: " + ret);
        return ret;
    }

    /**
     * Update font size from EM Add by mtk54043
     */
    private void updateFontSize(ListPreference fontSizePreference) {
        Xlog.d(TAG, "update font size ");

        final CharSequence[] values = fontSizePreference.getEntryValues();

        float small = Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.FONT_SCALE_SMALL, -1);
        float large = Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.FONT_SCALE_LARGE, -1);
        float extraLarge = Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.FONT_SCALE_EXTRALARGE, -1);
        Xlog.d(TAG, "update font size small = " + small);
        Xlog.d(TAG, "update font size large = " + large);
        Xlog.d(TAG, "update font size extraLarge = " + extraLarge);
        if (small != -1 || large != -1 || extraLarge != -1) {

            if (null != values[0] && small != -1) {
                values[0] = small + "";
                Xlog.d(TAG, "update font size : " + values[0]);
            }
            if (null != values[2] && large != -1) {
                values[2] = large + "";
                Xlog.d(TAG, "update font size : " + values[2]);
            }
            if (null != values[3] && extraLarge != -1) {
                values[3] = extraLarge + "";
                Xlog.d(TAG, "update font size : " + values[3]);
            }

            if (null != values) {
                fontSizePreference.setEntryValues(values);
            }

            mIsUpdateFont = true;
        }
    }

    public int floatToIndex(ListPreference fontSizePreference, float val) {
        Xlog.d(TAG, "floatToIndex enter val = " + val);
        int res = -1;
        if (mIsUpdateFont) {
            final CharSequence[] indicesEntry = fontSizePreference.getEntryValues();
            Xlog.d(TAG, "current font size : " + val);
            for (int i = 0; i < indicesEntry.length; i++) {
                float thisVal = Float.parseFloat(indicesEntry[i].toString());
                if (val == thisVal) {
                    Xlog.d(TAG, "Select : " + i);
                    res = i;
                }
            }
            if (res == -1) {
                res = 1;
            }
        }

        Xlog.d(TAG, "floatToIndex, res = " + res);
        return res;
    }

    // Smart book plug in/out receiver {@
    private BroadcastReceiver mSmartBookPlugReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context1, Intent intent) {
            Boolean isSmartBookPluggedIn = intent.getBooleanExtra(
                    Intent.EXTRA_SMARTBOOK_PLUG_STATE, false);
            Xlog.d(TAG, "smartbook plug:" + isSmartBookPluggedIn);
            // if has smart book plug in, HDMI item should gone
            if (isSmartBookPluggedIn || mHdmiManager == null) {
                mDisplayDefCategory.removePreference(mHDMISettings);
            } else {
                mDisplayDefCategory.addPreference(mHDMISettings);
            }
        }
    };

    // @}

    /**
     * Add for trade mark style of ClearMotion^TM and MiraVision^TM
     */
    private SpannableString getTradeMarkStyle(int resId) {
        String title = mContext.getString(resId);
        SpannableString spanText = new SpannableString(title);
        int strLen = spanText.length();
        spanText.setSpan(new SuperscriptSpan(), strLen - 2, strLen,
                Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        spanText.setSpan(new RelativeSizeSpan(0.6f), strLen - 2, strLen,
                Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        return spanText;
    }
}
