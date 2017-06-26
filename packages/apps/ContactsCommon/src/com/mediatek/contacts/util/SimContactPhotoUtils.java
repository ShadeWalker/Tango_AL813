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
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
package com.mediatek.contacts.util;

import com.mediatek.contacts.ExtensionManager;
import com.mediatek.contacts.ext.IIccCardExtension;
import com.mediatek.contacts.simcontact.SubInfoUtils;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public class SimContactPhotoUtils {
    private static final String TAG = "SimContactPhotoUtils";
    /// M: modify @{
    public static final int INDICATE_PHONE_SIM_COLUMN_INDEX = 7;
    public static final int IS_SDN_CONTACT = 8;
    /// @}
    public interface SimPhotoIdAndUri {
        int DEFAULT_SIM_PHOTO_ID = -1;

        int SIM_PHOTO_ID_YELLOW_SDN = -5;
        int SIM_PHOTO_ID_ORANGE_SDN = -6;
        int SIM_PHOTO_ID_GREEN_SDN = -7;
        int SIM_PHOTO_ID_PURPLE_SDN = -8;

        int DEFAULT_SIM_PHOTO_ID_SDN = -9;

        int SIM_PHOTO_ID_YELLOW = -10;
        int SIM_PHOTO_ID_ORANGE = -11;
        int SIM_PHOTO_ID_GREEN = -12;
        int SIM_PHOTO_ID_PURPLE = -13;
        
        int SIM_PHOTO_ID_SDN_LOCKED = -14;

        String DEFAULT_SIM_PHOTO_URI = "content://sim";

        String SIM_PHOTO_URI_YELLOW_SDN = "content://sdn-5";
        String SIM_PHOTO_URI_ORANGE_SDN = "content://sdn-6";
        String SIM_PHOTO_URI_GREEN_SDN = "content://sdn-7";
        String SIM_PHOTO_URI_PURPLE_SDN = "content://sdn-8";

        String DEFAULT_SIM_PHOTO_URI_SDN = "content://sdn";

        String SIM_PHOTO_URI_YELLOW = "content://sim-10";
        String SIM_PHOTO_URI_ORANGE = "content://sim-11";
        String SIM_PHOTO_URI_GREEN = "content://sim-12";
        String SIM_PHOTO_URI_PURPLE = "content://sim-13";
    }

    public interface SimPhotoColors {
        int YELLOW = 0;
        int ORANGE = 1;
        int GREEN = 2;
        int PURPLE = 3;
    }

    public static long getPhotoIdByPhotoUri(Uri uri) {
        long id = 0;

        if (uri == null) {
            LogUtils.e(TAG, "[getPhotoIdByPhotoUri] uri is null,return 0.");
            return id;
        }

        String photoUri = uri.toString();

        if (SimPhotoIdAndUri.DEFAULT_SIM_PHOTO_URI.equals(photoUri)) {
            id = SimPhotoIdAndUri.DEFAULT_SIM_PHOTO_ID;
        } else if (SimPhotoIdAndUri.SIM_PHOTO_URI_YELLOW.equals(photoUri)) {
            id = SimPhotoIdAndUri.SIM_PHOTO_ID_YELLOW;
        } else if (SimPhotoIdAndUri.SIM_PHOTO_URI_ORANGE.equals(photoUri)) {
            id = SimPhotoIdAndUri.SIM_PHOTO_ID_ORANGE;
        } else if (SimPhotoIdAndUri.SIM_PHOTO_URI_GREEN.equals(photoUri)) {
            id = SimPhotoIdAndUri.SIM_PHOTO_ID_GREEN;
        } else if (SimPhotoIdAndUri.SIM_PHOTO_URI_PURPLE.equals(photoUri)) {
            id = SimPhotoIdAndUri.SIM_PHOTO_ID_PURPLE;
        }

        if (SimPhotoIdAndUri.DEFAULT_SIM_PHOTO_URI_SDN.equals(photoUri)
                || SimPhotoIdAndUri.SIM_PHOTO_URI_YELLOW_SDN.equals(photoUri)
                || SimPhotoIdAndUri.SIM_PHOTO_URI_ORANGE_SDN.equals(photoUri)
                || SimPhotoIdAndUri.SIM_PHOTO_URI_GREEN_SDN.equals(photoUri)
                || SimPhotoIdAndUri.SIM_PHOTO_URI_PURPLE_SDN.equals(photoUri)
                ) {
            id = SimPhotoIdAndUri.SIM_PHOTO_ID_SDN_LOCKED;
        }
        LogUtils.d(TAG, "[getPhotoIdByPhotoUri]photoUri:" + photoUri +
                ",id:" + id);

        return id;
    }

    public static long getSimContactPhotoId(int indicate, int isSdnContact) {
        long photoId = 0;
        int color = SubInfoUtils.getColorUsingSubId(indicate);

        if (photoId == -1) {
            
            photoId = new SimContactPhotoUtils().getPhotoId(isSdnContact, color);
        }
        LogUtils.d(TAG, "[getSimType] i = " + color + " | isSdnContact : " + isSdnContact);

        LogUtils.d(TAG, "[getSimType] photoId : " + photoId);
        /// M: add show sim card icon feature @{
        photoId = ExtensionManager.getInstance().getCtExtension()
                .getPhotoIdBySub(indicate, isSdnContact, photoId);
        LogUtils.d(TAG, "[getSimType] photoId : " + photoId + "indicate: " + indicate);
        return photoId;
        /// @}
    }
    

    public static boolean isSimPhotoUri(Uri uri) {
        if (null == uri) {
            LogUtils.e(TAG, "[isSimPhotoUri] uri is null");
            return false;
        }

        String photoUri = uri.toString();
        LogUtils.d(TAG, "[isSimPhotoUri] uri : " + photoUri);

        if (SimPhotoIdAndUri.DEFAULT_SIM_PHOTO_URI.equals(photoUri)
                || SimPhotoIdAndUri.DEFAULT_SIM_PHOTO_URI_SDN.equals(photoUri)
                || SimPhotoIdAndUri.SIM_PHOTO_URI_YELLOW.equals(photoUri)
                || SimPhotoIdAndUri.SIM_PHOTO_URI_YELLOW_SDN.equals(photoUri)
                || SimPhotoIdAndUri.SIM_PHOTO_URI_ORANGE.equals(photoUri)
                || SimPhotoIdAndUri.SIM_PHOTO_URI_ORANGE_SDN.equals(photoUri)
                || SimPhotoIdAndUri.SIM_PHOTO_URI_GREEN.equals(photoUri)
                || SimPhotoIdAndUri.SIM_PHOTO_URI_GREEN_SDN.equals(photoUri)
                || SimPhotoIdAndUri.SIM_PHOTO_URI_PURPLE.equals(photoUri)
                || SimPhotoIdAndUri.SIM_PHOTO_URI_PURPLE_SDN.equals(photoUri)
                ) {
            return true;
        }

        return false;
    }

    public static boolean isSimPhotoId(long photoId) {
        LogUtils.d(TAG, "[isSimPhotoId] photoId : " + photoId);
        /// M: add show sim card icon feature @{
        boolean isSim = false;
        /// @}
        if (photoId == SimPhotoIdAndUri.DEFAULT_SIM_PHOTO_ID
                || photoId == SimPhotoIdAndUri.DEFAULT_SIM_PHOTO_ID_SDN
                || photoId == SimPhotoIdAndUri.SIM_PHOTO_ID_YELLOW
                || photoId == SimPhotoIdAndUri.SIM_PHOTO_ID_YELLOW_SDN
                || photoId == SimPhotoIdAndUri.SIM_PHOTO_ID_GREEN
                || photoId == SimPhotoIdAndUri.SIM_PHOTO_ID_GREEN_SDN
                || photoId == SimPhotoIdAndUri.SIM_PHOTO_ID_ORANGE
                || photoId == SimPhotoIdAndUri.SIM_PHOTO_ID_ORANGE_SDN
                || photoId == SimPhotoIdAndUri.SIM_PHOTO_ID_PURPLE
                || photoId == SimPhotoIdAndUri.SIM_PHOTO_ID_PURPLE_SDN
                || photoId == SimPhotoIdAndUri.SIM_PHOTO_ID_SDN_LOCKED) {
            return true;
        }
        /// M: add show sim card icon feature @{
        isSim = ExtensionManager.getInstance().getCtExtension()
                .isOperatorSimPhotoId(photoId);
        return isSim;
        /// @}
    }

    public String getPhotoUri(int isSdnContact, int colorId) {
        String photoUri = null;
        boolean isSdn = (isSdnContact > 0);
        /**
         * Plug-in call @{
         */
        Bundle argsForExt = new Bundle();
        argsForExt.putBoolean(IIccCardExtension.KEY_IS_ICC_CONTACT_SDN, isSdn);
        argsForExt.putInt(IIccCardExtension.KEY_ICC_COLOR_ID, colorId);
        //M:[COMMD_FOR_OP09]
        photoUri = mIccExt.getIccPhotoUriString(argsForExt);
        if (photoUri != null) {
            LogUtils.i(TAG, "[getPhotoUri] from ext: " + photoUri);
            return photoUri;
        }
        /*** Plug-in call @}*/
        LogUtils.d(TAG, "[getPhotoUri] i = " + colorId + " | isSdnContact : " + isSdnContact
                + ",photoUri:" + photoUri);
        switch (colorId) {
            case SimContactPhotoUtils.SimPhotoColors.YELLOW:
                if (isSdn) {
                    photoUri = SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_URI_YELLOW_SDN;
                } else {
                    photoUri = SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_URI_YELLOW;
                }
                break;
            case SimContactPhotoUtils.SimPhotoColors.ORANGE:
                if (isSdn) {
                    photoUri = SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_URI_ORANGE_SDN;
                } else {
                    photoUri = SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_URI_ORANGE;
                }
                break;
            case SimContactPhotoUtils.SimPhotoColors.GREEN:
                if (isSdn) {
                    photoUri = SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_URI_GREEN_SDN;
                } else {
                    photoUri = SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_URI_GREEN;
                }
                break;
            case SimContactPhotoUtils.SimPhotoColors.PURPLE:
                if (isSdn) {
                    photoUri = SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_URI_PURPLE_SDN;
                } else {
                    photoUri = SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_URI_PURPLE;
                }
                break;
            default:
                LogUtils.i(TAG, "[getPhotoUri]no match color");
                if (isSdn) {
                    photoUri = SimContactPhotoUtils.SimPhotoIdAndUri.DEFAULT_SIM_PHOTO_URI_SDN;
                } else {
                    photoUri = SimContactPhotoUtils.SimPhotoIdAndUri.DEFAULT_SIM_PHOTO_URI;
                }
                break;
        }

        return photoUri;
    }

    public long getPhotoId(int isSdnContact, int colorId) {
        long photoId = 0;
        boolean isSdn = (isSdnContact > 0);
        /**
         * Plug-in call @{ TODO:: OP09 Plug in revise
         */
        Bundle argsForExt = new Bundle();
        argsForExt.putBoolean(IIccCardExtension.KEY_IS_ICC_CONTACT_SDN, isSdn);
        argsForExt.putInt(IIccCardExtension.KEY_ICC_COLOR_ID, colorId);
        //[COMMD_FOR_OP09]
        photoId = mIccExt.getIccPhotoId(argsForExt);
        if (photoId != 0) {
            LogUtils.i(TAG, "[getPhotoId] from ext: " + photoId);
            return photoId;
        }
        /**Plug-in call @}*/
        LogUtils.d(TAG, "[getPhotoId] i = " + colorId + " | isSdnContact : " + isSdnContact
                + ",photoId:" + photoId);
        switch (colorId) {
            case SimContactPhotoUtils.SimPhotoColors.YELLOW:
                {
                    photoId = SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_ID_YELLOW;
                }
                break;
            case SimContactPhotoUtils.SimPhotoColors.ORANGE:
                {
                    photoId = SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_ID_ORANGE;
                }
                break;
            case SimContactPhotoUtils.SimPhotoColors.GREEN:
                {
                    photoId = SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_ID_GREEN;
                }
                break;
            case SimContactPhotoUtils.SimPhotoColors.PURPLE:
                {
                    photoId = SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_ID_PURPLE;
                }
                break;
            default:             
                {
                    photoId = SimContactPhotoUtils.SimPhotoIdAndUri.DEFAULT_SIM_PHOTO_ID;
                }
                break;
        }

        /// For sdn new locked icon.
        if (isSdn) {
            LogUtils.i(TAG, "[getPhotoId]is sdn.");
            photoId = SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_ID_SDN_LOCKED;
        }

        return photoId;
    }

    /**
     * M: Bug Fix CR ID: ALPS00112776 Descriptions: sim card icon display not right.
     */
    public static long getSimContactPhotoId(Cursor cursor, long photoId) {
        int indicatePhoneSim = cursor.getInt(INDICATE_PHONE_SIM_COLUMN_INDEX);
        if (indicatePhoneSim > 0) {
            photoId = getSimContactPhotoId(
                    indicatePhoneSim, cursor.getInt(IS_SDN_CONTACT));
        }
        return photoId;
    }

    /**
     * the extension for IccCard related photo.
     */
    private IIccCardExtension mIccExt = ExtensionManager.getInstance().getIccCardExtension();     
}

