/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/* //device/content/providers/telephony/TelephonyProvider.java
**
** Copyright 2006, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package com.android.providers.telephony;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.os.SystemProperties;
import android.provider.Telephony;
import android.util.Log;

/**
 * Mediatek customization for TelephonProvider.
 */
public class TelephonyProviderEx {
    private static final String TAG = "TelephonyProviderEx";
    private static final String MTK_SVLTE_PROPERTY = "ro.mtk_svlte_support";
    private static final String[] SVLTE_CARRIER_COLUMN_NAME = {
        Telephony.Carriers._ID,
        Telephony.Carriers.NAME,
        Telephony.Carriers.NUMERIC,
        Telephony.Carriers.MCC,
        Telephony.Carriers.MNC,
        Telephony.Carriers.APN,
        Telephony.Carriers.USER,
        Telephony.Carriers.SERVER,
        Telephony.Carriers.PASSWORD,
        Telephony.Carriers.PROXY,
        Telephony.Carriers.PORT,
        Telephony.Carriers.MMSPROXY,
        Telephony.Carriers.MMSPORT,
        Telephony.Carriers.MMSC,
        Telephony.Carriers.AUTH_TYPE,
        Telephony.Carriers.TYPE,
        Telephony.Carriers.CURRENT,
        Telephony.Carriers.SOURCE_TYPE,
        Telephony.Carriers.CSD_NUM,
        Telephony.Carriers.PROTOCOL,
        Telephony.Carriers.ROAMING_PROTOCOL,
        Telephony.Carriers.OMACPID,
        Telephony.Carriers.NAPID,
        Telephony.Carriers.PROXYID,
        Telephony.Carriers.CARRIER_ENABLED,
        Telephony.Carriers.BEARER,
        Telephony.Carriers.SPN,
        Telephony.Carriers.IMSI,
        Telephony.Carriers.PNN,
        Telephony.Carriers.PPP,
        Telephony.Carriers.MVNO_TYPE,
        Telephony.Carriers.MVNO_MATCH_DATA,
        Telephony.Carriers.SUBSCRIPTION_ID,
        Telephony.Carriers.PROFILE_ID,
        Telephony.Carriers.MODEM_COGNITIVE,
        Telephony.Carriers.MAX_CONNS,
        Telephony.Carriers.WAIT_TIME,
        Telephony.Carriers.MAX_CONNS_TIME,
        Telephony.Carriers.MTU
    };

    /**
     * ChinaTelecom SVLTE has special design: 46003 numeric cannot be used to attach LTE.
     * If user create a new APN and set as preferred APN, the APN numeric is set as current
     * operator numeric(46003 or 46011). if user create APN under 46003 CDMA network, and
     * then turn on LTE to enter 46011 LTE network, this APN cannot used to attach LTE data.
     *
     * Solution: When Data framework query APN list using specified numeric(46003/46011),
     * query all user defined 46003 and 46011 APNs and change APN numeric to data specified
     * numeric, then create a MatrixCursor to wrap these data.
     *
     * @param qb The SQLiteQueryBuilder
     * @param db The SQLiteDatabase
     * @param projectionIn The selection project
     * @param selection The selection
     * @param selectionArgs The selection argument
     * @param groupBy The groupby argument
     * @param having The having argument
     * @param sort The sort argument
     * @return The cursor contains specified apn list.
     */
    public Cursor queryApnForSvlte(SQLiteQueryBuilder qb, SQLiteDatabase db, String[] projectionIn,
            String selection, String[] selectionArgs, String groupBy, String having, String sort) {
        if (selection != null && selectionArgs == null && projectionIn == null &&
                (selection.equals("numeric = '46011'") || selection.equals("numeric = '46003'"))) {
            String numeric;
            if (selection.equals("numeric = '46011'")) {
                numeric = "46011";
                selection = "((numeric = 46011 and sourcetype = 0) or " +
                        "((numeric = 46011 or numeric = 46003) and sourcetype = 1))";
            } else {
                numeric = "46003";
                selection = "((numeric = 46003 and sourcetype = 0) or " +
                        "((numeric = 46011 or numeric = 46003) and sourcetype = 1))";
            }
            Cursor cursor = qb.query(db, projectionIn, selection, selectionArgs, null, null, sort);
            if (cursor == null || cursor.getCount() == 0) {
                return cursor;
            } else {
                MatrixCursor matrixCursor = new MatrixCursor(SVLTE_CARRIER_COLUMN_NAME, 1);
                if (cursor.moveToFirst()) {
                    do {
                        addApnRowForSvlte(matrixCursor, cursor, numeric);
                    } while (cursor.moveToNext());
                    cursor.close();
                    return matrixCursor;
                } else {
                    return cursor;
                }
            }
        } else {
            return qb.query(db, projectionIn, selection, selectionArgs, null, null, sort);
        }
    }

    /**
     * Check if SVLTE is supported.
     * @return true indicate SVLTE is enabled.
     */
    public static boolean isSvlteSupport() {
        log("isSvlteSupport MTK_SVLTE_PROPERTY=" + SystemProperties.get(MTK_SVLTE_PROPERTY).equals("1"));
        return SystemProperties.get(MTK_SVLTE_PROPERTY).equals("1");
    }

    private void addApnRowForSvlte(MatrixCursor matrixCursor, Cursor cursor, String queryNumeric) {
        int sourceType =
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.SOURCE_TYPE));
        String curSorNumeric =
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.NUMERIC));
        log("addApnRow sourceType=" + sourceType + ", curSorNumeric=" + curSorNumeric);
        matrixCursor.addRow(new Object[] {
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers._ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.NAME)),
                updateApnNumeric(
                        cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.NUMERIC)),
                        Telephony.Carriers.NUMERIC, sourceType, queryNumeric),
                updateApnNumeric(
                        cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MCC)),
                        Telephony.Carriers.MCC, sourceType, queryNumeric),
                updateApnNumeric(
                        cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MNC)),
                        Telephony.Carriers.MNC, sourceType, queryNumeric),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.APN)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.USER)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.SERVER)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PASSWORD)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PROXY)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PORT)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSPROXY)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSPORT)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSC)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.AUTH_TYPE)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.TYPE)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.CURRENT)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.SOURCE_TYPE)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.CSD_NUM)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PROTOCOL)),
                cursor.getString(
                        cursor.getColumnIndexOrThrow(Telephony.Carriers.ROAMING_PROTOCOL)),
                //Should not include if OMACAP not support
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.OMACPID)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.NAPID)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PROXYID)),

                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.CARRIER_ENABLED)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.BEARER)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.SPN)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.IMSI)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PNN)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PPP)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MVNO_TYPE)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MVNO_MATCH_DATA)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.SUBSCRIPTION_ID)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.PROFILE_ID)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.MODEM_COGNITIVE)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.MAX_CONNS)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.WAIT_TIME)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.MAX_CONNS_TIME)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.MTU)),
        });
    }

    private String updateApnNumeric(String sourceString, String key,
            int sourceType, String queryNumeric) {
        log("updateApnNumeric sourceString=" + sourceString + ", key=" + key +
                ", sourceType=" + sourceType + ", queryNumeric=" + queryNumeric);
        if (Telephony.Carriers.NUMERIC.equals(key) && sourceType == 1) {
            log("updateApnNumeric return queryNumeric");
            return queryNumeric;
        } else if (Telephony.Carriers.MCC.equals(key) && sourceType == 1) {
            log("updateApnNumeric return queryNumeric 0-2");
            return queryNumeric.substring(0, 2);
        } else if (Telephony.Carriers.MNC.equals(key) && sourceType == 1) {
            log("updateApnNumeric return queryNumeric 3-4");
            return queryNumeric.substring(3, 4);
        } else {
            return sourceString;
        }
    }

    private static void log(String s) {
        Log.d(TAG, s);
    }
}
