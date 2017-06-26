package com.mediatek.settings.deviceinfo;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Build;
import android.os.UserHandle;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;
import com.android.settings.R;
import com.mediatek.settings.cdma.CdmaUtils;
import com.mediatek.settings.ext.IDeviceInfoSettingsExt;
import com.mediatek.settings.FeatureOption;
import com.mediatek.settings.UtilsExt;

import java.util.List;
import java.util.Locale;

public class DeviceInfoSettingsExts {
    private static final String TAG = "DeviceInfoSettings";

    private static final String KEY_BUILD_NUMBER = "build_number";
    private static final String KEY_DEVICE_MODEL = "device_model";
    //private static final String KEY_BASEBAND_VERSION = "baseband_version";
    private static final String KEY_MTK_SYSTEM_UPDATE = "mtk_system_update";
    private static final String KEY_MTK_SOFTWARE_UPDATE = "mtk_software_update";
    //private static final String KEY_BASEBAND_VERSION_2 = "baseband_version_2";
    private static final String KEY_CUSTOM_BUILD_VERSION = "custom_build_version";
    private static final String PROPERTY_CUSTOM_BUILD_VERSION = "ro.mediatek.version.release";
    private static final String KEY_CDMA_EPUSH = "cdma_epush";

    private IDeviceInfoSettingsExt mExt;
    private Activity mActivity;
    private PreferenceScreen mRootContainer;
    private PreferenceFragment mPreferenceFragment;
    private Resources mRes;

    public DeviceInfoSettingsExts(Activity activity, PreferenceFragment fragment) {
        mActivity = activity;
        mPreferenceFragment = fragment;
        mRootContainer = fragment.getPreferenceScreen();
        mExt = UtilsExt.getDeviceInfoSettingsPlugin(activity);
        mRes = mActivity.getResources();
    }

    public void initMTKCustomization(PreferenceGroup parentPreference) {
        // /M: System and software update.
        boolean isOwner = UserHandle.myUserId() == UserHandle.USER_OWNER;
        boolean isSystemUpdateSupport = FeatureOption.MTK_SYSTEM_UPDATE_SUPPORT
                || FeatureOption.MTK_MDM_FUMO || FeatureOption.MTK_FOTA_ENTRY;
        boolean isSoftwareUpdateSupport = FeatureOption.MTK_MDM_SCOMO
                || FeatureOption.MTK_SCOMO_ENTRY;
        Log.d(TAG, "isOwner : " + isOwner + " isSystemUpdateSupport : "
                + isSystemUpdateSupport + " isSoftwareUpdateSupport : "
                + isSoftwareUpdateSupport);
        /*if (!isOwner || !isSystemUpdateSupport) {
            removePreference(findPreference(KEY_MTK_SYSTEM_UPDATE));
        } else {
            updateTitleToActivityLabel(KEY_MTK_SYSTEM_UPDATE);
        }*/
	    removePreference(findPreference(KEY_MTK_SYSTEM_UPDATE));
        if (!isOwner || !isSoftwareUpdateSupport) {
            removePreference(findPreference(KEY_MTK_SOFTWARE_UPDATE));
        }

        // /M: Support Gemini feature and C+D two modem..
        //initBasebandVersion();

        // /M: Customize for operatort.
        mExt.updateSummary(findPreference(KEY_DEVICE_MODEL), Build.MODEL,
                getString(R.string.device_info_default));
        mExt.updateSummary(findPreference(KEY_BUILD_NUMBER), Build.DISPLAY,
                getString(R.string.device_info_default));
        //mExt.addEpushPreference(mRootContainer); //HQ_zhangpeng5 2015-11-02 modified for delete CT epush  HQ01478891

        // /M: Add custom build version.
        setValueSummary(KEY_CUSTOM_BUILD_VERSION, PROPERTY_CUSTOM_BUILD_VERSION);
    }

    private void updateTitleToActivityLabel(String key) {
       Preference preference = findPreference(key);
       if (preference == null) {
           return ;
       }
       Intent intent = new Intent(Intent.ACTION_MAIN, null);
       if (FeatureOption.MTK_SYSTEM_UPDATE_SUPPORT) {
           intent.setClassName("com.mediatek.systemupdate", 
                           "com.mediatek.systemupdate.MainEntry");
       } else if (FeatureOption.MTK_FOTA_ENTRY) {
           intent.setClassName("com.mediatek.dm", 
                            "com.mediatek.dm.fumo.DmEntry");
       } else if (FeatureOption.MTK_MDM_FUMO) {
           ///Just for operator, maybe will phaseout in furter.
           intent.setClassName("com.mediatek.mediatekdm", 
                             "com.mediatek.mediatekdm.fumo.DmEntry");
       }

       if (intent != null) {
            // Find the activity that is in the system image
            PackageManager pm = mActivity.getPackageManager();
            List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
            int listSize = list.size();
            for (int i = 0; i < listSize; i++) {
                ResolveInfo resolveInfo = list.get(i);
                if ((resolveInfo.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM)
                        != 0) {
                    // Set the preference title to the activity's label
                    CharSequence title = resolveInfo.loadLabel(pm);
                    preference.setTitle(title);
                    Log.d(TAG, "KEY_MTK_SYSTEM_UPDATE : " + title);
                    break;
                }
            }
        }
    }

/*
    private void initBasebandVersion() {
        String baseband = "gsm.version.baseband";
        int modemSlot = CdmaUtils.getExternalModemSlot();
        // if modemSlot is -1. no external modem.
        boolean hasExternalModem = modemSlot != -1;
        if (hasExternalModem && !FeatureOption.PURE_AP_USE_EXTERNAL_MODEM && (modemSlot == 0)) {
            baseband = "gsm.version.baseband1";
        }
        Log.d(TAG, "baseband = " + baseband + "modemSlot: " + modemSlot);
        setValueSummary(KEY_BASEBAND_VERSION, baseband);

        if (hasExternalModem && !FeatureOption.PURE_AP_USE_EXTERNAL_MODEM) {
            String baseband2 = "gsm.version.baseband1";

            if (FeatureOption.MTK_C2K_SUPPORT) {
                baseband2 = "cdma.version.baseband";
            } else if (modemSlot == 0) {
                baseband2 = "gsm.version.baseband";
            }
            Log.d(TAG, "baseband2 = " + baseband2);
            setValueSummary(KEY_BASEBAND_VERSION_2, baseband2);
            updateBasebandTitle();
        } else {
            removePreference(findPreference(KEY_BASEBAND_VERSION_2));
        }
    }
*/

    private Preference findPreference(String key) {
        return mPreferenceFragment.findPreference(key);
    }

    private void removePreference(Preference preference) {
        mRootContainer.removePreference(preference);
    }

    private String getString(int id) {
        return mRes.getString(id);
    }

    private void setValueSummary(String preference, String property) {
        try {
            findPreference(preference).setSummary(
                    SystemProperties.get(property, getString(R.string.device_info_default)));
        } catch (RuntimeException e) {
            // No recovery
        }
    }

    // /M: Support gemini feature and C+D two modem.
/*    private void updateBasebandTitle() {
        String basebandversion = getString(R.string.baseband_version);
        String slot1;
        String slot2;

        if (FeatureOption.MTK_C2K_SUPPORT) {
            Locale tr = Locale.getDefault(); // For chinese there is no space
            slot1 = "GSM " + basebandversion;
            slot2 = "CDMA " + basebandversion;
            if (tr.getCountry().equals(Locale.CHINA.getCountry())
                    || tr.getCountry().equals(Locale.TAIWAN.getCountry())) {
                slot1 = slot1.replace("GSM ", "GSM");
                slot2 = slot2.replace("CDMA ", "CDMA"); // delete the space
            }
        } else {
            slot1 = basebandversion
                    + getString(R.string.status_imei_slot1).replace(
                            getString(R.string.status_imei), " ");
            slot2 = basebandversion
                    + getString(R.string.status_imei_slot2).replace(
                            getString(R.string.status_imei), " ");
        }
        if (findPreference(KEY_BASEBAND_VERSION) != null) {
            findPreference(KEY_BASEBAND_VERSION).setTitle(slot1);
        }
        if (findPreference(KEY_BASEBAND_VERSION_2) != null) {
            findPreference(KEY_BASEBAND_VERSION_2).setTitle(slot2);
        }
    }
*/

    public void onCustomizedPreferenceTreeClick(Preference preference) {
        if (preference.getKey().equals(KEY_MTK_SYSTEM_UPDATE)) {
            systemUpdateEntrance(preference);
        } else if (preference.getKey().equals(KEY_MTK_SOFTWARE_UPDATE)) {
            softwareUpdateEntrance(preference);
        } else if (preference.getKey().equals(KEY_CDMA_EPUSH)) {
            startActivity("com.ctc.epush", "com.ctc.epush.IndexActivity");
        }
    }

    private void systemUpdateEntrance(Preference preference) {
        if (FeatureOption.MTK_SYSTEM_UPDATE_SUPPORT) {
            startActivity("com.mediatek.systemupdate",
                    "com.mediatek.systemupdate.MainEntry");
        } else if (FeatureOption.MTK_MDM_FUMO || FeatureOption.MTK_FOTA_ENTRY) {
            sendBroadcast("com.mediatek.DMSWUPDATE");
        }
    }

    private void softwareUpdateEntrance(Preference preference) {
        if (FeatureOption.MTK_MDM_SCOMO) {
            startActivity("com.mediatek.mediatekdm",
                    "com.mediatek.mediatekdm.scomo.DmScomoActivity");
        } else if (FeatureOption.MTK_SCOMO_ENTRY) {
            startActivity("com.mediatek.dm",
                    "com.mediatek.dm.scomo.DmScomoActivity");
        }
    }

    private void startActivity(String className, String activityName) {
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        ComponentName cn = new ComponentName(className, activityName);
        intent.setComponent(cn);
        if (mActivity.getPackageManager().resolveActivity(intent, 0) != null) {
            mActivity.startActivity(intent);
        } else {
            Log.e(TAG, "Unable to start activity " + intent.toString());
        }
    }

    private void sendBroadcast(String actionName) {
        Intent intent = new Intent();
        intent.setAction(actionName);
        mActivity.sendBroadcast(intent);
    }
}
