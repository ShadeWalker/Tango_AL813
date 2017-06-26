/*
 * Copyright (c) 2013, 2014 The Linux Foundation. All rights reserved.
 * Not a Contribution.
 * Copyright (C) 2006 The Android Open Source Project
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

package android.provider;

import android.app.ActivityThread;
import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * The Settings provider contains global system-level device preferences.
 */
 /**{@hide}*/ 
public final class ChildMode {

    public static final String   AUTHORITY                       = "childmode";
    private static final String  TAG                             = "ChildMode";
    public static final String   ON                              = "1";
    public static final String   OFF                             = "0";
    public static final String   NAME                            = "name";
    public static final String   VALUE                           = "value";
    public static Uri            COMMON_CONTENT_URI              = Uri.parse("content://"+ AUTHORITY+ "/common");
    public static Uri            APP_CONTENT_URI                 = Uri.parse("content://"+ AUTHORITY+ "/app");
    public static Uri            URL_CONTENT_URI                 = Uri.parse("content://"+ AUTHORITY+ "/url");
    public static final String   TABLE_COMMON                    = "common";
    public static final String   TABLE_APP_BLACK_LIST            = "app";
    public static final String   TABLE_WEB_WHITE_LIST            = "url";
    public static final String   CHILD_MODE_ON                   = "child_mode_on";
    public static final String   APP_BlACK_LIST_ON               = "app_black_list_on";
    public static final String   URL_WHITE_LIST_ON               = "url_white_list_on";
    public static final String   INTERNET_TIME_RESTRICTION_ON    = "internet_time_restriction_on";
    public static final String   INTERNET_TIME_RESTRICTION       = "internet_time_restriction";
    public static final String   INTERNET_TRAFFIC_RESTRICTION_ON = "internet_traffic_restriction_on";
    public static final String   INTERNET_TRAFFIC_RESTRICTION    = "internet_traffic_restriction";
    //add by lihaizhou for disable MobileData for ChildMode by begin
    public static final String   INTERNET_LIMIT_TRAFFIC_UP       = "internet_limit_traffic_up";
    public static final String   INTERNET_LIMIT_TIME_UP          = "internet_limit_time_up"; 
    //add by lihaizhou for disable MobileData for ChildMode by end 
    public static final String   FORBID_SEND_MESSAGE_ON          = "forbid_send_message_on";
    public static final String   FORBID_CALL                     = "forbid_call";
    public static final String   FORBID_WLAN                     = "forbid_wlan";
    public static final String   FORBID_DATA                     = "forbid_data";
    public static final String   FORBID_DELETE_MESSAGE           = "forbid_delete_message";
    public static final String   FORBID_INSTALL_APP              = "forbid_install_app";
    public static final String   FORBID_UNINSTALL_APP            = "forbid_uninstall_app";

    public static boolean putString(ContentResolver resolver,String name, String value) {
        try {
            ContentValues values = new ContentValues();
            values.put(NAME, name);
            values.put(VALUE, value);
            resolver.update(COMMON_CONTENT_URI, values, "name=?", new String[]{name});
            return true;
        } catch (SQLException e) {
            Log.w(TAG, "Can't set key " + name + " in " + COMMON_CONTENT_URI, e);
            return false;
        }
    }

    public static String getString(ContentResolver cr, String name) {
        Cursor c = null;
        try {
            c = cr.query( COMMON_CONTENT_URI,
                    new String[] { "value" },
                    "name=?", 
                    new String[] { name },
                    null, null);
            if (c == null) {
                Log.w(TAG, "Can't get key " + name + " from " + COMMON_CONTENT_URI);
                return null;
            }

            String value = c.moveToNext() ? c.getString(0) : null;
            return value;
        } catch (Exception e) {
            Log.w(TAG, "Can't get key " + name + " from " + COMMON_CONTENT_URI, e);
            return null;
        } finally {
            if (c != null)
                c.close();
        }
    }

    public static Uri getUriFor(Uri uri, String name) {
        return Uri.withAppendedPath(uri, name);
    }

    //begin:Added for urlwhitelist
    public static boolean isChildModeOnBase() {
        String swictOn;
        Application app= ActivityThread.currentApplication();
        swictOn = getString(app.getContentResolver(), CHILD_MODE_ON);
        return !TextUtils.isEmpty(swictOn) && swictOn.endsWith(ON);
    }
    //end:Added for urlwhitelist

    public static boolean isChildModeOn(ContentResolver cr) {
        String swictOn;
        swictOn = getString(cr, CHILD_MODE_ON);
        return !TextUtils.isEmpty(swictOn) && swictOn.endsWith(ON);
    }
    public static boolean isAppBlackListOn(ContentResolver cr) {
        String swictOn;
        swictOn = getString(cr, APP_BlACK_LIST_ON);
        return !TextUtils.isEmpty(swictOn) && swictOn.endsWith(ON);
    }
    public static List<String> getAppBlackList(ContentResolver cr) {
        List<String>appList = new ArrayList<String>();
        Cursor cursor ;
        cursor = cr.query(APP_CONTENT_URI, new String[]{"package_name"}, null, null, null);
        if (cursor != null) {
            try {
                cursor.moveToFirst();
                while (cursor.moveToNext()) {
                    String packageName = cursor.getString(0);
                    if (!TextUtils.isEmpty(packageName)) {
                        appList.add(packageName);
                    }
                } 
            } finally {
                cursor.close();
            }
        }
        return appList;
    }
    /*add for child mode begin*/
    public static boolean isInAppBlackList(ContentResolver cr,String packageName){
        Cursor cursor=null;
        int count=0;
        cursor = cr.query(APP_CONTENT_URI, new String[]{"package_name"},
                                " package_name=? ", new String[]{packageName}, null);
        if (cursor!=null) {
            try {
                count = cursor.getCount();
            } finally{
               cursor.close();
            }
        }
        return count > 0;
    }

    public static int removeAppList(ContentResolver cr, String packageName) {
        int deleteRow = 0;
        try {
            deleteRow = cr.delete(APP_CONTENT_URI, " package_name=? ",
                new String[] { packageName });
        } catch (Exception e){
            Log.e(TAG, "removeAppList exception:",e);
        }
        return deleteRow;
    }
    public static Uri addAppList(ContentResolver cr, String packageName) {
        Uri uri = null;
        try {
            ContentValues values  = new ContentValues(1);
            values.put("package_name", packageName);
            uri = cr.insert(APP_CONTENT_URI, values);
        } catch (Exception e){
            Log.e(TAG, "addAppList exception:",e);
        }
        return uri;
    }
    public static boolean isUrlWhteListOn(ContentResolver cr) {
        String swictOn;
        swictOn = getString(cr, URL_WHITE_LIST_ON);
        return !TextUtils.isEmpty(swictOn) && swictOn.endsWith(ON);
    }

    //begin:Added for urlwhitelist
    public static boolean isUrlWhteListOnBase() {
        String swictOn;
        Application app= ActivityThread.currentApplication();
        swictOn = getString(app.getContentResolver(), URL_WHITE_LIST_ON);
        return !TextUtils.isEmpty(swictOn) && swictOn.endsWith(ON);
    }
    //end:Added for urlwhitelist

    public static List<String[]> getWebWhiteList(ContentResolver cr) {
        List<String[]>urlList = new ArrayList<String[]>();
        Cursor cursor ;
        //begin:modified for urlwhitelist
        cursor = cr.query(URL_CONTENT_URI, new String[]{"name","url", "_id"}, null, null, null);
        if (cursor != null) {
            try {
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    String name = cursor.getString(0);
                    String url     = cursor.getString(1);
                    String id     = cursor.getString(2);
                    if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(url) && !TextUtils.isEmpty(id)) {
                        urlList.add(new String[]{name,url,id});
                    }
                    cursor.moveToNext();
                }
          //end:modified for urlwhitelist
            } finally {
                cursor.close();
            }
        }
        return urlList;
    }
    public static boolean isInUrlWhiteList(ContentResolver cr,String url){
        Cursor cursor=null;
        int count=0;
        cursor = cr.query(URL_CONTENT_URI, new String[]{"name","url"},
                                " url=? ", new String[]{url}, null);
        if (cursor!=null) {
            try {
                count = cursor.getCount();
            } finally{
               cursor.close();
            }
        }
        return count > 0;
    }

    public static boolean isInUrlWhiteListBase(String url){
        Log.d(TAG, "isInUrlWhiteListBase url=" + url);
        Cursor cursor=null;
        int count=0;
        Application app = ActivityThread.currentApplication();
         String hostname = "";
        try {
            hostname = new URL(url).getHost();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG, "isInUrlWhiteListBase hostname=" + hostname);
        if (TextUtils.isEmpty(hostname)) {
            return true;
        }

        String domain = "";
        String[] array = hostname.split("\\.");
        if (array.length >=3) {
            domain = array[array.length-2] + "." + array[array.length-1];
        } else {
            domain = hostname;
        }

        Log.d(TAG, "isInUrlWhiteListBase domain=" + domain);
        if (TextUtils.isEmpty(domain)) {
            return true;
        }

        cursor = app.getContentResolver().query(URL_CONTENT_URI, new String[]{"name","url"},
                "url like \"%" + domain + "%\"", null, null);
        if (cursor!=null) {
            try {
                count = cursor.getCount();
            } finally{
                cursor.close();
            }
        }
        Log.d(TAG, "isInUrlWhiteListBase count=" + count);
        return count > 0;
    }
    public static int removeUrlList(ContentResolver cr, String url) {
        int deleteRow = 0;
        try {
            deleteRow = cr.delete(URL_CONTENT_URI, " url=? ",
                new String[] { url });
        } catch (Exception e){
            Log.e(TAG, "removeUrlList exception:",e);
        }
        return deleteRow;
    }
    public static Uri addUrlList(ContentResolver cr, String name,String url) {
        Uri uri = null;
        try {
            ContentValues values  = new ContentValues(2);
            values.put("name", name);
            values.put("url", url);
            uri = cr.insert(URL_CONTENT_URI, values);
        } catch (Exception e){
            Log.e(TAG, "addUrlList exception:",e);
        }
        return uri;
    }

    //begin:Added for urlwhitelist
    public static int updateUrlList(ContentResolver cr, String name,String url, String id) {
        int count = 0;
        try {
            ContentValues values  = new ContentValues(2);
            values.put("name", name);
            values.put("url", url);
            count = cr.update(URL_CONTENT_URI, values, "_id=" + id, null);
        } catch (Exception e){
            Log.e(TAG, "updateUrlList exception:",e);
        }
        return count;
    }
    //end:Added for urlwhitelist
}
