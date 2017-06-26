/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

package com.mediatek.launcher3.ext;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * M: all apps columns, add for new data structure.
 */
public final class AllApps implements BaseColumns {

    public static final String OLD_AUTHORITY = "com.android.launcher2.settings";
    public static final String AUTHORITY = "com.android.launcher3.settings";
    public static final String DB_CREATED_BUT_DEFAULT_ALLAPPS_NOT_LOADED =
            "DB_CREATED_BUT_DEFAULT_ALLAPPS_NOT_LOADED";
    public static final String PARAMETER_NOTIFY = "notify";
    public static final String SHARED_PREFERENCE_KEY = "com.android.launcher3.prefs";
    public static final String TABLE_ALLAPPS = "allapps";
    public static final String TAG_APPLICATION_ITEM = "application_item";
    public static final String TABLE_FAVORITES = "favorites";
    public static final String TABLE_WORKSPACE_SCREENS = "workspaceScreens";

    public static int sAppsCellCountX = -1;
    public static int sAppsCellCountY = -1;

    /**
     * The content:// style URL for this table.
     */
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY
            + "/" + TABLE_ALLAPPS + "?" + PARAMETER_NOTIFY + "=true");

    /**
     * The content:// style URL for this table. When this Uri is used, no
     * notification is sent if the content changes.
     */
    public static final Uri CONTENT_URI_NO_NOTIFICATION = Uri
            .parse("content://" + AUTHORITY + "/" + TABLE_ALLAPPS + "?"
                    + PARAMETER_NOTIFY + "=false");

    /**
     * The content:// style URL for a given row, identified by its id.
     *
     * @param id The row id.
     * @param notify True to send a notification is the content changes.
     * @return The unique content URL for the specified row.
     */
    public static Uri getContentUri(long id, boolean notify) {
        return Uri.parse("content://" + AUTHORITY + "/" + TABLE_ALLAPPS + "/"
                + id + "?" + PARAMETER_NOTIFY + "=" + notify);
    }

    /**
     * Descriptive name of the gesture that can be displayed to the user.
     * <P>
     * Type: TEXT
     * </P>
     */
    public static final String TITLE = "title";

    /**
     * The Intent URL of the gesture, describing what it points to. This value
     * is given to {@link android.content.Intent#parseUri(String, int)} to
     * create an Intent that can be launched.
     * <P>
     * Type: TEXT
     * </P>
     */
    public static final String INTENT = "intent";

    /**
     * The type of the gesture.
     * <P>
     * Type: INTEGER
     * </P>
     */
    public static final String ITEM_TYPE = "itemType";

    /**
     * The gesture is an application.
     */
    public static final int ITEM_TYPE_APPLICATION = 0;

    /**
     * The favorite is a user created folder.
     */
    public static final int ITEM_TYPE_FOLDER = 2;

    /**
     * Package name.
     * <P>
     * Type: TEXT
     * </P>
     */
    public static final String PACKAGE_NAME = "package_name";

    /**
     * Class name.
     * <P>
     * Type: TEXT
     * </P>
     */
    public static final String CLASS_NAME = "class_name";

    /**
     * The container holding the AllApps.
     * <P>Type: INTEGER</P>
     */
    public static final String CONTAINER = "container";

    public static final int CONTAINER_ALLAPP = -1;

    /**
     * The screen holding the favorite (if container is CONTAINER_DESKTOP).
     * <P>
     * Type: INTEGER
     * </P>
     */
    public static final String SCREEN = "screen";

    /**
     * The X coordinate of the cell holding the app.
     * <P>
     * Type: INTEGER
     * </P>
     */
    public static final String CELLX = "cellX";

    /**
     * The Y coordinate of the cell holding the app.
     * <P>
     * Type: INTEGER
     * </P>
     */
    public static final String CELLY = "cellY";

    /**
     * The X span of the cell holding the app.
     * <P>Type: INTEGER</P>
     */
    public static final String SPANX = "spanX";

    /**
     * The Y span of the cell holding the app.
     * <P>Type: INTEGER</P>
     */
    public static final String SPANY = "spanY";

    /**
     * The visible flag.
     * <P>
     * Type: INTEGER
     * </P>
     */
    public static final String VISIBLE_FLAG = "visible";

    /**
     * The profile id of the item in the cell.
     * <P>
     * Type: INTEGER
     * </P>
     */
    public static final String PROFILE_ID = "profileId";
}
