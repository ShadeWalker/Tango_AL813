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

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;

import android.os.ServiceManager;
import android.os.storage.IMountService;
import android.os.RemoteException;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;


import java.net.Inet4Address;
import java.net.Inet6Address;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Locale;


import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;

import com.android.internal.widget.LockPatternUtils;
import com.mediatek.common.MPlugin;
import com.mediatek.storage.StorageManagerEx;
import com.mediatek.settings.ext.*;
import com.android.settings.R;


public class UtilsExt {
    private static final String TAG = "UtilsExt";

    // disable apps list file location
    private static final String FILE_DISABLE_APPS_LIST = "/system/etc/disableapplist.txt";
    private static ArrayList<String> mList = new ArrayList<String>();
    // read the file to get the need special disable app list
    public static  ArrayList<String> disableAppList = readFile(FILE_DISABLE_APPS_LIST);

    ///M: DHCPV6 change feature
    private static final String INTERFACE_NAME = "wlan0";
    private static final int BEGIN_INDEX = 0;
    private static final int SEPARATOR_LENGTH = 2;

    // for HetComm feature
    public static final String PKG_NAME_HETCOMM = "com.mediatek.hetcomm";
    /**
     * read the file by line
     * @param path path
     * @return ArrayList
     */
    public static ArrayList<String> readFile(String path) {
         mList.clear();
         File file = new File(path);
          FileReader fr = null;
          BufferedReader br = null;
         try {
               if (file.exists()) {
                   fr = new FileReader(file);
              } else {
                  Log.d(TAG, "file in " + path + " does not exist!");
                  return null;
             }
               br = new BufferedReader(fr);
               String line;
               while ((line = br.readLine()) != null) {
                     Log.d(TAG, " read line " + line);
                     mList.add(line);
               }
               return mList;
         } catch (IOException io) {
                Log.d(TAG, "IOException");
                 io.printStackTrace();
         } finally {
                   try {
                      if (br != null) {
                          br.close();
                         }
                      if (fr != null) {
                         fr.close();
                         }
                      } catch (IOException io) {
                         io.printStackTrace();
                      }
         }
         return null;
     }


    /**
     * Returns the WIFI IP Addresses, if any, taking into account IPv4 and IPv6 style addresses.
     * @return the formatted and comma-separated IP addresses, or null if none.
     */
    public static String getWifiIpAddresses() {
        NetworkInterface wifiNetwork = null;
        String addresses = "";
        try {
            wifiNetwork = NetworkInterface.getByName(INTERFACE_NAME);
        } catch (SocketException e) {
            e.printStackTrace();
            return null;
        }
        if (wifiNetwork == null) {
            Log.d(TAG, "wifiNetwork is null");
            return null;
        }
        Enumeration<InetAddress> enumeration = wifiNetwork.getInetAddresses();
        if (enumeration == null) {
            Log.d(TAG, "enumeration is null");
            return null;
        }
        while (enumeration.hasMoreElements()) {
            InetAddress inet = enumeration.nextElement();
            String hostAddress = inet.getHostAddress();
            if (hostAddress.contains("%")) {
                hostAddress = hostAddress.substring(BEGIN_INDEX, hostAddress.indexOf("%")); // remove %10, %wlan0
            }
            Log.d(TAG, "InetAddress = " + inet.toString());
            Log.d(TAG, "hostAddress = " + hostAddress);
            if (inet instanceof Inet6Address) {
                Log.d(TAG, "IPV6 address = " + hostAddress);
                addresses += hostAddress + "; ";
            } else if (inet instanceof Inet4Address) {
                Log.d(TAG, "IPV4 address = " + hostAddress);
                addresses = hostAddress + ", " + addresses;
            }
        }
        Log.d(TAG, "IP addresses = " + addresses);
        if (!("").equals(addresses) && (addresses.endsWith(", ") || addresses.endsWith("; "))) {
            addresses = addresses.substring(BEGIN_INDEX, addresses.length() - SEPARATOR_LENGTH);
        } else if (("").equals(addresses)) {
            addresses = null;
        }
        Log.d(TAG, "The result of IP addresses = " + addresses);
        return addresses;
    }

    /* M: create settigns plugin object
     * @param context Context
     * @return ISettingsMiscExt
     */
    public static ISettingsMiscExt getMiscPlugin(Context context) {
        ISettingsMiscExt ext;
        ext = (ISettingsMiscExt) MPlugin.createInstance(
                     ISettingsMiscExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultSettingsMiscExt(context);
        }
        return ext;
    }

    /**
     * M: create wifi plugin object
     * @param context Context
     * @return IWifiExt
     */
    public static IWifiExt getWifiPlugin(Context context) {
        IWifiExt ext;
        ext = (IWifiExt) MPlugin.createInstance(
                     IWifiExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultWifiExt(context);
        }
        return ext;
    }
    /**
     * M: create wifi settings plugin object
     * @param context Context context
     * @return IWifiSettingsExt
     */
    public static IWifiSettingsExt getWifiSettingsPlugin(Context context) {
        IWifiSettingsExt ext;
        ext = (IWifiSettingsExt) MPlugin.createInstance(
                     IWifiSettingsExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultWifiSettingsExt();
        }
        return ext;
    }
    /**
     * M: create apn settings plugin object
     * @param context Context
     * @return IApnSettingsExt
     */
    public static IApnSettingsExt getApnSettingsPlugin(Context context) {
        IApnSettingsExt ext;
        ext = (IApnSettingsExt) MPlugin.createInstance(
                     IApnSettingsExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultApnSettingsExt();
        }
        return ext;
    }

    public static IRcseOnlyApnExtension getRcseApnPlugin(Context context) {
        IRcseOnlyApnExtension ext = null;
        ext = (IRcseOnlyApnExtension) MPlugin.createInstance(
                     IRcseOnlyApnExtension.class.getName(), context);
        if (ext == null) {
            ext = new DefaultRcseOnlyApnExt();
        }
        return ext;
    }

    public static IReplaceApnProfileExt getReplaceApnPlugin(Context context) {
        IReplaceApnProfileExt ext;
        ext = (IReplaceApnProfileExt) MPlugin.createInstance(
                     IReplaceApnProfileExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultReplaceApnProfile();
        }
        return ext;
    }
    /**
     * M: create wifi ap dialog plugin object
     * @param context Context
     * @return IWifiApDialogExt
     */
    public static IWifiApDialogExt getWifiApDialogPlugin(Context context) {
        IWifiApDialogExt ext;
        ext = (IWifiApDialogExt) MPlugin.createInstance(
                     IWifiApDialogExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultWifiApDialogExt();
        }
        return ext;
    }
    /**
     * M: Add for Shared SD card.
     * In general, phone storage is primary volume, but this may be shift by SD swap.
     * So we add this API to verify is there any storage emulated.
     * @return Return true if there is at least one storage emulated
     */
    public static boolean isSomeStorageEmulated() {
        boolean isExistEmulatedStorage = false;
        try {
            IMountService mountService = IMountService.Stub.asInterface(
                ServiceManager.getService("mount"));
            if (mountService != null) {
                isExistEmulatedStorage = mountService.isExternalStorageEmulated();
            } else {
                Log.e(TAG, "MountService return null");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException happens, couldn't talk to MountService");
        }
        Log.d(TAG, "isExistEmulatedStorage : " + isExistEmulatedStorage);
        return isExistEmulatedStorage;
    }

    /**
     * M: Add for MTK_2SDCARD_SWAP.
     * @return Return true if external SdCard is inserted.
     */
    public static boolean isExSdcardInserted() {
       boolean isExSdcardInserted = StorageManagerEx.getSdSwapState();
       Log.d(TAG, "isExSdcardInserted : " + isExSdcardInserted);
       return isExSdcardInserted;
    }

 /**
     * M: create DateTimeSettings plugin object
     * @param context Context
     * @return IDateTimeSettingsExt
     */
    public static IDateTimeSettingsExt getDateTimeSettingsPlugin(Context context) {
        IDateTimeSettingsExt ext;
        ext = (IDateTimeSettingsExt) MPlugin.createInstance(
                     IDateTimeSettingsExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultDateTimeSettingsExt();
        }
        return ext;
    }

    /**
     * M: Create battery plugin object
     * @param context Context
     * @return IBatteryExt
     */
    public static IBatteryExt getBatteryExtPlugin(Context context) {
        IBatteryExt ext;
        ext = (IBatteryExt) MPlugin.createInstance(
                     IBatteryExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultBatteryExt();
        }
        return ext;
    }

    /**
     * M: Create factory plugin object
     * @param context Context
     * @return IFactoryExt
     */
    public static IFactoryExt getFactoryPlugin(Context context) {
        IFactoryExt ext;
        ext = (IFactoryExt) MPlugin.createInstance(
                     IFactoryExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultFactoryExt(context);
        }
        return ext;
    }

    //M: for mtk in house permission control
    public static IPermissionControlExt getPermControlExtPlugin(Context context) {
        IPermissionControlExt ext;
        ext = (IPermissionControlExt) MPlugin.createInstance(
                     IPermissionControlExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultPermissionControlExt(context);
            Log.d(TAG, "getPermControlExtPlugin(), can't find PermissionControlExt");
        }
        return ext;
    }

    //M: for Privacy Protection Lock Settings Entry
    public static IPplSettingsEntryExt getPrivacyProtectionLockExtPlugin(Context context) {
        IPplSettingsEntryExt ext;
        ext = (IPplSettingsEntryExt) MPlugin.createInstance(
                     IPplSettingsEntryExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultPplSettingsEntryExt(context);
        }
        return ext;
    }

    //M: for  MediatekDM permission control
    public static IMdmPermissionControlExt getMdmPermControlExtPlugin(Context context) {
        IMdmPermissionControlExt ext;
        ext = (IMdmPermissionControlExt) MPlugin.createInstance(
                     IMdmPermissionControlExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultMdmPermControlExt(context);
        }
        return ext;
    }

    public static String getVolumeDescription(Context context) {
        StorageManager mStorageManager = (StorageManager) context.getSystemService(
                Context.STORAGE_SERVICE);
        String volumeDescription = null;
        StorageVolume[] volumes = mStorageManager.getVolumeList();
        for (int i = 0; i < volumes.length; i++) {
            if (!volumes[i].isRemovable()) {
                volumeDescription = volumes[i].getDescription(context);
                volumeDescription = volumeDescription.toLowerCase();
                break;
            }
        }
        Log.d(TAG, "volumeDescription = " + volumeDescription);
        return volumeDescription;
    }

    public static String getVolumeString(int stringId, String volumeDescription, Context context) {
        if (volumeDescription == null) { // no volume description
            Log.d(TAG, "+volumeDescription is null and use default string");
            return context.getString(stringId);
        }
        //SD card string
        String sdCardString = context.getString(R.string.sdcard_setting);
        Log.d(TAG, "sdCardString=" + sdCardString);
        String str = context.getString(stringId).replace(sdCardString,
                volumeDescription);
        // maybe it is in lower case, no replacement try another
        if (str != null && str.equals(context.getString(stringId))) {
            sdCardString = sdCardString.toLowerCase();
            // restore to SD
            sdCardString = sdCardString.replace("sd", "SD");
            Log.d(TAG, "sdCardString" + sdCardString);
            str = context.getString(stringId).replace(sdCardString, volumeDescription);
            Log.d(TAG, "str" + str);
        }
        if (str != null && str.equals(context.getString(stringId))) {
            str = context.getString(stringId).replace("SD", volumeDescription);
            Log.d(TAG, "Not any available then replase key word sd str=" + str);
        }
        Locale tr = Locale.getDefault();
        // For chinese there is no space
        if (tr.getCountry().equals(Locale.CHINA.getCountry())
                || tr.getCountry().equals(Locale.TAIWAN.getCountry())) {
            // delete the space
            str = str.replace(" " + volumeDescription, volumeDescription);
        }
        return str;
    }

      /**
     * M: for update status of operator name
     * @param context Context
     * @return IStatusExt
     */
    public static IStatusBarPlmnDisplayExt getStatusBarPlmnPlugin(Context context) {
        IStatusBarPlmnDisplayExt ext;
        ext = (IStatusBarPlmnDisplayExt) MPlugin.createInstance(
                     IStatusBarPlmnDisplayExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultStatusBarPlmnDisplayExt(context);
        }
        return ext;
    }

    /**
     * M: create device info settings plugin object
     * @param context Context
     * @return IDeviceInfoSettingsExt
     */
    public static IDeviceInfoSettingsExt getDeviceInfoSettingsPlugin(Context context) {
        IDeviceInfoSettingsExt ext;
        ext = (IDeviceInfoSettingsExt) MPlugin.createInstance(
                     IDeviceInfoSettingsExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultDeviceInfoSettingsExt();
        }
        return ext;
    }


    /**
     * M: create audio provile plugin object
     * @param context Context
     * @return IAudioProfileExt
     */
    public static IAudioProfileExt getAudioProfilePlgin(Context context) {
        IAudioProfileExt ext;
        ext = (IAudioProfileExt) MPlugin.createInstance(
                     IAudioProfileExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultAudioProfileExt(context);
        }
        return ext;
    }

    //M: for mtk in house data protection
    public static IDataProtectionExt getDataProectExtPlugin(Context context) {
        IDataProtectionExt ext;
        ext = (IDataProtectionExt) MPlugin.createInstance(
                     IDataProtectionExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultDataProtectionExt(context);
        }
        return ext;
    }

    /**
     * @param context Context
     * @return ISimRoamingExt
     */
    public static ISimRoamingExt getSimRoamingExtPlugin(Context context) {
        ISimRoamingExt ext;
        ext = (ISimRoamingExt) MPlugin.createInstance(
                     ISimRoamingExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultSimRoamingExt();
        }
        return ext;
    }

    /**
     * for update status of operator name
     * @param context Context
     * @return IStatusExt
     */
    public static IStatusExt getStatusExtPlugin(Context context) {
        IStatusExt ext;
        ext = (IStatusExt) MPlugin.createInstance(
                     IStatusExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultStatusExt();
        }
        return ext;
    }

    /**
     * M: create DataUsageSummary plugin object
     * @param context Context
     * @return IDataUsageSummaryExt
     */
    public static IDataUsageSummaryExt getDataUsageSummaryPlugin(Context context) {
        IDataUsageSummaryExt ext;
        ext = (IDataUsageSummaryExt) MPlugin.createInstance(
                     IDataUsageSummaryExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultDataUsageSummaryExt(context);
        }
        return ext;
    }

    /**
     * to judge the packageName apk is installed or not
     * @param context Context
     * @param packageName name of package
     * @return true if the package is exist
     */
    public static boolean isPackageExist(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName,
                    PackageManager.GET_ACTIVITIES);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * M: for sim management update preference
     * @param context Context
     * @return ISimManagementExt
     */
    public static ISimManagementExt getSimManagmentExtPlugin(Context context) {
        ISimManagementExt ext;
        ext = (ISimManagementExt) MPlugin.createInstance(
                     ISimManagementExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultSimManagementExt();
        }
        return ext;
    }

    public static CharSequence getConfirmationDescription(Context context) {
        CharSequence description = null;
        LockPatternUtils utils = new LockPatternUtils(context);
        switch (utils.getKeyguardStoredPasswordQuality()) {
            case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
                description = context.getText(R.string.lockpassword_confirm_your_pattern_header);
                break;
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
                description = context.getText(R.string.lockpassword_confirm_your_pin_header);
                break;
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX:
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC:
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_COMPLEX:
                description = context.getText(R.string.lockpassword_confirm_your_password_header);
                break;
        }
        return description;
    }

    //M: for Development Settings
    public static IDevExt getDevExtPlugin(Context context) {
        IDevExt ext;
        ext = (IDevExt) MPlugin.createInstance(
                IDevExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultDevExt(context);
        }
        return ext;
    }

    ///M: WFC  @ {
    public static IWfcSettingsExt getWfcSettingsExtPlugin(Context context) {
        IWfcSettingsExt ext;
        ext = (IWfcSettingsExt) MPlugin.createInstance(
                IWfcSettingsExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultWfcSettingsExt();
        }
        return ext;
    }
    /// @}
}
