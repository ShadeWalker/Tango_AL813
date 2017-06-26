package com.mediatek.settings.deviceinfo;

import android.content.Context;
import android.content.res.Resources;
import android.os.Environment;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.util.Log;

import com.android.settings.R;
import com.mediatek.settings.FeatureOption;
import com.mediatek.settings.UtilsExt;

import java.util.Locale;

public class StorageVolumePreferenceCategoryExts {

    private static final String TAG = "StorageVolumePreferenceCategory";
    private StorageVolume mVolume;

    private boolean mIsOTGDevice;
    private String mVolumeDescription;
    private boolean mIsInternalSD;
    private Context mContext;
    private final Resources mResources;

    private static final String PROPERTY_IS_VOLUME_SWAPPING = "sys.sd.swapping";
    private static final String PROPERTY_IS_VOLUME_UNMOUNTING = "sys.sd.unmounting";
    private static final String VOLUME_NOT_UNMOUNTING_STATE = "0";

    public StorageVolumePreferenceCategoryExts(Context context, StorageVolume volume) {
        mVolume = volume;
        mContext = context;
        mResources = mContext.getResources();
        if (mVolume != null) {
            mIsOTGDevice = volume.getPath().startsWith(Environment.DIRECTORY_USBOTG);
            mVolumeDescription = volume.getDescription(mContext);
            mIsInternalSD = !volume.isRemovable();
            Log.d(TAG, "Storage description :" + mVolumeDescription
                    + ", isEmulated : " + volume.isEmulated()
                    + ", isRemovable : " + volume.isRemovable());
        }
    }

    public void setVolumeTitle(Preference preference) {
        String title = null;
        if (mVolume == null) {
            int resId  = (FeatureOption.MTK_SHARED_SDCARD && !FeatureOption.MTK_2SDCARD_SWAP) ?
                          com.android.internal.R.string.storage_phone : R.string.internal_storage;
            title = mContext.getText(resId).toString();
        } else {
            title = mVolume.getDescription(mContext);
        }
        /*HQ_xupeixin at 2015-09-17 modified settings->storage page likes p8 and special deal with the case begin*/
        Log.d(TAG, "setVolumeTitle title: " + title);
        //case language is english
        if ("Phone storage".equals(title)) {
            title = "Internal storage";
        }
        //case language is simple chinese
        if ("手机存储".equals(title)) {
            title = "内部存储";
        }
        //case language is tw
        if ("電話儲存".equals(title)) {
            title = "內部存儲";
        }
        //case language is hk
        if ("手機儲存".equals(title)) {
            title = "內部存儲";
        }
        /*HQ_xupeixin at 2015-09-17 modified end*/
        preference.setTitle(title);
    }

    private boolean isVolumeSwapping() {
        boolean isSwapping = SystemProperties.getBoolean(PROPERTY_IS_VOLUME_SWAPPING, false);
        Log.d(TAG, "SystemProperty [sys.sd.swapping] = " + isSwapping);
        boolean enable = false;
        if (FeatureOption.MTK_2SDCARD_SWAP) {
            enable = mIsOTGDevice ? false : isSwapping;
        }
        return enable;
    }

    private boolean isVolumeUnmounting() {
       String flag = SystemProperties.get(PROPERTY_IS_VOLUME_UNMOUNTING,
                         VOLUME_NOT_UNMOUNTING_STATE);
       Log.d(TAG, "SystemProperty [sys.sd.unmounting] = " + flag);
       return mVolume.getPath().equals(flag);
    }

    public boolean getUpdateProtect() {
        //Do not enable the togglePreference when volume is unmounting or swapping.
        return !isVolumeUnmounting() && !isVolumeSwapping();
    }

    public void updateUserOwnerState(UserManager userManager, Preference preference) {
        // SD card & OTG only for owner.
        if (FeatureOption.MTK_OWNER_SDCARD_ONLY_SUPPORT
                && (userManager.getUserHandle() != UserHandle.USER_OWNER)
                && !mIsInternalSD) {
            Log.d(TAG, "Not the owner, do not allow to mount / unmount");
            if (preference != null) {
                preference.setEnabled(false);
            }
        }
    }

    public void setVolume(StorageVolume volume) {
        mVolume = volume;
        if (mVolume != null) {
            mIsOTGDevice = mVolume.getPath().startsWith(Environment.DIRECTORY_USBOTG);
            mVolumeDescription = mVolume.getDescription(mContext);
            mIsInternalSD = !mVolume.isRemovable();
            Log.d(TAG, "Description :" + mVolumeDescription
                    + ", isEmulated : " + mVolume.isEmulated()
                    + ", isRemovable : " + mVolume.isRemovable());
        }
    }

    public boolean isInternalVolume() {
       return mVolume == null ||
                (UtilsExt.isSomeStorageEmulated() && mIsInternalSD);
    }

    public void initPhoneStorageMountTogglePreference(PreferenceCategory root,
           Preference mountToggle, StorageManager storageManager) {
        if (mVolume == null) return;
        String state = storageManager.getVolumeState(mVolume.getPath());
        boolean isMounted = Environment.MEDIA_MOUNTED.equals(state)
                    || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
        if (!isMounted) {
            root.addPreference(mountToggle);
            Log.d(TAG, "Phone storage not in mounted state");
        }
    }

    public String getFormatString(int resId) {
        return getString(resId, mIsOTGDevice ?
                mResources.getString(R.string.usb_ums_title) : mVolumeDescription);
    }

    public String getString(int usbResId, int resId) {
        return mIsOTGDevice ? mResources.getString(usbResId)
                : getString(resId, mVolumeDescription);
    }

    public String getString(int resId) {
        return getString(resId, mVolumeDescription);
    }

    private String getString(int resId, String description) {
        if (description == null || (!mIsInternalSD && !mIsOTGDevice)) {
            return mResources.getString(resId);
        }
        //SD card string
        String sdCardString = mResources.getString(R.string.sdcard_setting);
        String str = mResources.getString(resId).replace(sdCardString, description);
        // maybe it is in lower case, no replacement try another
        if (str != null && str.equals(mResources.getString(resId))) {
            sdCardString = sdCardString.toLowerCase();
            // restore to SD
            sdCardString = sdCardString.replace("sd", "SD");
            str = mResources.getString(resId).replace(sdCardString, description);
        }

        if (str != null && str.equals(mResources.getString(resId))) {
            str = mResources.getString(resId).replace("SD", description);
        }
        Locale tr = Locale.getDefault();
        // For chinese there is no space
        if (tr.getCountry().equals(Locale.CHINA.getCountry())
                || tr.getCountry().equals(Locale.TAIWAN.getCountry())) {
            // delete the space
            str = str.replace(" " + description, description);
        }
        return str;
    }
}
