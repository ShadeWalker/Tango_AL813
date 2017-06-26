/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.floatpanel;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.StringBuilderPrinter;
import android.util.Xml;

import com.android.internal.util.XmlUtils;
import com.mediatek.xlog.Xlog;

import com.android.systemui.floatpanel.FloatModel;
import com.android.systemui.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

///M:For Multi window 
import com.mediatek.multiwindow.MultiWindowProxy;
///@}

/**
 * M: The content provider for multi-window entrance databases.
 */
public class FloatWindowProvider extends ContentProvider {
    private static final String TAG = "FloatWindowProvider";
    private static final boolean DEBUG = true;

    private static final String DATABASE_NAME = "float.db";
    private static final int DATABASE_VERSION = 12;
    private static final String PREF_FIRST_LOAD_FLOAT = "mediatek_float_info";
    private static final String DB_CREATED_BUT_DEFAULT_ALLAPPS_NOT_LOADED = "com.mediatek.float.allapps.not.loaded";
    private static final String TAG_RESIDENT_PACKAGE = "residentpackages";
    protected static final String AUTHORITY = "com.android.systemui.floatwindow";
    protected static final String TABLE_NAME = "float";
    protected static final String PARAMETER_NOTIFY = "notify";
    protected static final String FLOAT_ID = "_id";
    protected static final String COMPONENT_NAME = "componentName";
    protected static final String FLOAT_INTENT = "intent";
    protected static final String FLOAT_POSITION = "position";
    protected static final String FLOAT_CONTAINER = "floatContainer";

    private static long mMaxIdInAllAppsList = -1;
    private static int mCellIndex = 0;
    private static SharedPreferences mSharedPreferences;
    // / M: Modify for scene feature.
    private static DatabaseHelper sOpenHelper;
    private static Context mContext;

	@Override
    public boolean onCreate() {
     Xlog.d(TAG, "FloatWindowProvider_onCreate**");
       mContext = getContext();
	sOpenHelper = new DatabaseHelper(mContext);
        return true;
    }

    @Override
    public String getType(Uri uri) {
        SqlArguments args = new SqlArguments(uri, null, null);
        if (TextUtils.isEmpty(args.where)) {
            return "vnd.android.cursor.dir/" + args.table;
        } else {
            return "vnd.android.cursor.item/" + args.table;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
		Xlog.d(TAG, "FloatWindowProvider_query**");

        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(args.table);

        SQLiteDatabase db = sOpenHelper.getWritableDatabase();
        Cursor result = qb.query(db, projection, args.where, args.args, null,
                null, sortOrder);
		if(result!= null)
          result.setNotificationUri(getContext().getContentResolver(), uri);

        return result;
    }

    private static long dbInsertAndCheck(DatabaseHelper helper,
            SQLiteDatabase db, String table, String nullColumnHack,
            ContentValues values) {
        if (!values.containsKey("componentName")) {
            throw new RuntimeException(
                    "Error: attempting to add item without specifying an componentName");
        }
        return db.insert(table, nullColumnHack, values);
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
    Xlog.d(TAG, "FloatWindowProvider_insert**");
        SqlArguments args = new SqlArguments(uri);

        SQLiteDatabase db = sOpenHelper.getWritableDatabase();
        final long rowId = dbInsertAndCheck(sOpenHelper, db, args.table, null,
                initialValues);
        if (rowId <= 0) {
            return null;
        }

        uri = ContentUris.withAppendedId(uri, rowId);
        sendNotify(uri);

        return uri;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);

        SQLiteDatabase db = sOpenHelper.getWritableDatabase();
        int count = db.delete(args.table, args.where, args.args);
        if (count > 0) {
            sendNotify(uri);
        }

        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
                Xlog.d(TAG, "FloatWindowProvider_update**");
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);

        SQLiteDatabase db = sOpenHelper.getWritableDatabase();
        int count = db.update(args.table, values, args.where, args.args);
        if (count > 0) {
            sendNotify(uri);
        }

        return count;
    }

    private void sendNotify(Uri uri) {
        String notify = uri.getQueryParameter(PARAMETER_NOTIFY);
        if (notify == null || "true".equals(notify)) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
    }

    public long generateNewId() {
        return sOpenHelper.generateNewId();
    }

    public static DatabaseHelper getOpenHelper() {
        return sOpenHelper;
    }

    static class DatabaseHelper extends SQLiteOpenHelper {
        private final Context mContext;
        private long mMaxId = -1;

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
			 Xlog.d(TAG, "DatabaseHelper**");
            mContext = context;
            // In the case where neither onCreate nor onUpgrade gets called, we
            // read the maxId from
            // the DB here
            if (mMaxId == -1) {
                mMaxId = initializeMaxId(getWritableDatabase());
				 Xlog.d(TAG, "DatabaseHelper_mMaxId=**"+mMaxId);
            }
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            if (DEBUG) {
                Xlog.d(TAG, "creating new float database");
            }
			Xlog.d(TAG, "DatabaseHelper_*onCreate*");

            db.execSQL("CREATE TABLE float (" + "_id INTEGER PRIMARY KEY,"
                    + "componentName TEXT," + "intent TEXT,"
                    + "position INTEGER,"
                    + "floatContainer INTEGER NOT NULL DEFAULT 1" + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // TODO Auto-generated method stub

        }

        // Generates a new ID to use for an object in your database. This method
        // should be only
        // called from the main UI thread. As an exception, we do call it when
        // we call the
        // constructor from the worker thread; however, this doesn't extend
        // until after the
        // constructor is called, and we only pass a reference to
        // LauncherProvider to LauncherApp
        // after that point
        public long generateNewId() {
            if (mMaxId < 0) {
                throw new RuntimeException("Error: max id was not initialized");
            }
            mMaxId += 1;
            return mMaxId;
        }

        private long initializeMaxId(SQLiteDatabase db) {
            Cursor c = db.rawQuery("SELECT MAX(_id) FROM float", null);

            // get the result
            final int maxIdIndex = 0;
            long id = -1;
            if (c != null && c.moveToNext()) {
                id = c.getLong(maxIdIndex);
            }
            if (c != null) {
                c.close();
            }

            if (id == -1) {
                throw new RuntimeException("Error: could not query max id");
            }

            return id;
        }

	/**
	 * @param db
	 * The database to write the values into
	 */
	public void loadDefaultAllAppsList(SQLiteDatabase db) {
            if (DEBUG) {
			Xlog.d(TAG, "loadDefaultAllAppsList begin");
            }
            Xlog.d(TAG, "DatabaseHelper_*loadDefaultAllAppsList_begin*");
		
            final Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            final PackageManager packageManager = mContext.getPackageManager();
            List<ResolveInfo> apps = null;
		
            apps = packageManager.queryIntentActivities(intent, 0);
            if (apps == null || apps.size() == 0) {
		Xlog.e(TAG, "queryIntentActivities got null or zero!");
            }
            List<ComponentName> extentComponentNames =	new ArrayList<ComponentName>();
            for (ResolveInfo info : apps) {
		final String packageName = info.activityInfo.applicationInfo.packageName;
						
		Xlog.d(TAG, "loadDefaultAllAppsList_packageName_="+packageName);
		extentComponentNames.add(new ComponentName(packageName,
			info.activityInfo.name));
	    }
		
            List<ComponentName> residentComponentNames = loadResidentComponents(db);
            extentComponentNames.removeAll(residentComponentNames);
            //remove app, if multi window not want to support,@{
            List<String> blackListPackageNames = new ArrayList<String>();
            List<String> blackListComponentNames = new ArrayList<String>();
            MultiWindowProxy mMultiWindowProxy = MultiWindowProxy.getInstance();		 
            if ((mMultiWindowProxy != null) && mMultiWindowProxy.isFeatureSupport()) {				 
		blackListPackageNames = mMultiWindowProxy.getDisableFloatPkgList();
		blackListComponentNames = mMultiWindowProxy.getDisableFloatComponentList();
		Xlog.d(TAG, "DisableFloatPkgList.size():" + blackListPackageNames.size());	
		Xlog.d(TAG, "DisableFloatComponentList.size():" + blackListComponentNames.size());	
            }
					
            for (int index = extentComponentNames.size() -1; index >= 0; index--) {
		ComponentName extent = extentComponentNames.get(index);
                if (blackListPackageNames.contains(extent.getPackageName())) {
			extentComponentNames.remove(extent);
			Xlog.d(TAG, "remove blacklist app:" + extent);
			continue;
		}
		if (blackListComponentNames.contains(extent.flattenToString())) {
		        extentComponentNames.remove(extent);
			Xlog.d(TAG, "remove blacklist app2:" + extent);
			continue;
		}
            }
            ///@}
					
            ContentValues values = new ContentValues();
            long id = -1;
            long count = 0;
            for (ComponentName componentName : extentComponentNames) {
		values.clear();
		values.put(FLOAT_POSITION, count++);
		values.put(FLOAT_CONTAINER, FloatModel.EXTENT_CONTAINER);
		id = addItemToAllAppsList(db, values, packageManager, intent,
				componentName);
		
		if (id < 0) {
		    count--;
                }
            }
		
            if (DEBUG) {
                Xlog.d(TAG, "loadDefaultAllAppsList, query PMS got extent = "
			+ extentComponentNames.size() + ", resident = "
			+ residentComponentNames.size());
            }
        }

        /**
         * M: Load the default set of float packages from an xml file.
         *
         * @param context
         * @return true if load successful.
         */
        private List<ComponentName> loadResidentComponents(SQLiteDatabase db) {
            List<ComponentName> residentComponentNames = new ArrayList<ComponentName>();

            try {
                XmlResourceParser parser = mContext.getResources().getXml(
                        R.xml.default_residentpackage);
                AttributeSet attrs = Xml.asAttributeSet(parser);
                XmlUtils.beginDocument(parser, TAG_RESIDENT_PACKAGE);

                final int depth = parser.getDepth();
                int type = -1;
                ContentValues values = new ContentValues();
                final PackageManager packageManager = mContext
                        .getPackageManager();

                while (((type = parser.next()) != XmlPullParser.END_TAG || parser
                        .getDepth() > depth)
                        && type != XmlPullParser.END_DOCUMENT) {

                    if (type != XmlPullParser.START_TAG) {
                        continue;
                    }

                    TypedArray a = mContext.obtainStyledAttributes(attrs,
                            R.styleable.ResidentPackage);
                    ComponentName componentName = new ComponentName(
                            a.getString(R.styleable.ResidentPackage_residentPackageName),
                            a.getString(R.styleable.ResidentPackage_residentClassName));
                    residentComponentNames.add(componentName);

                    final Intent intent = new Intent(Intent.ACTION_MAIN, null);
                    intent.addCategory(Intent.CATEGORY_LAUNCHER);

                    values.clear();
                    values.put(FLOAT_POSITION, a.getInt(
                            R.styleable.ResidentPackage_residentOrder, 0));
                    values.put(FLOAT_CONTAINER, FloatModel.RESIDENT_CONTAINER);
                    addItemToAllAppsList(db, values, packageManager, intent,
                            componentName);

                    Xlog.d(TAG,
                            "loadResidentPackage: packageName = "
                                    + a.getString(R.styleable.ResidentPackage_residentPackageName)
                                    + ", className = "
                                    + a.getString(R.styleable.ResidentPackage_residentClassName));

                    a.recycle();
                }
            } catch (XmlPullParserException e) {
                Xlog.w(TAG,
                        "Got XmlPullParserException while parsing toppackage.",
                        e);
            } catch (IOException e) {
                Xlog.w(TAG, "Got IOException while parsing toppackage.", e);
            }

            return residentComponentNames;
        }

        /**
         * M: add application icon to all apps database, for OP09.
         *
         * @param db
         * @param values
         * @param a
         * @param packageManager
         * @param intent
         * @return
         */
        private long addItemToAllAppsList(SQLiteDatabase db,
                ContentValues values, PackageManager packageManager,
                Intent intent, ComponentName componentName) {
            ActivityInfo activityInfo = null;
            try {
                activityInfo = packageManager.getActivityInfo(componentName, 0);
            } catch (PackageManager.NameNotFoundException nnfe) {
                Xlog.w(TAG, "Can not add such application: " + componentName);
            }
            if (activityInfo == null) {
                return -1;
            }
            intent.setComponent(componentName);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            values.put(FLOAT_ID, generateNewId());
            values.put(COMPONENT_NAME, componentName.toString());
            values.put(FLOAT_INTENT, intent.toUri(0));
            if (DEBUG) {
                Xlog.d(TAG, "Load app item ,intent = " + intent
                        + ",componentName = " + componentName);
            }
            if (dbInsertAndCheck(this, db, TABLE_NAME, null, values) < 0) {
                Xlog.w(TAG, "Insert app item (" + values
                        + ") to database failed.");
                return -1;
            }
            mCellIndex++;
            return 1;
        }
    }

    /**
     * M: load default app list from xml file and store the data to database if
     * needed, add for OP09.
     */
    public synchronized boolean loadDefaultAllAppsIfNecessary(Context context) {
       Xlog.d(TAG, "loadDefaultAllAppsIfNecessary: context = "
                + context + "this =" + this+",  context.getPackageManager()="+context.getPackageManager());
        final PackageManager packageManager = context.getPackageManager();
        final boolean isSafeMode = packageManager.isSafeMode();
     
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                PREF_FIRST_LOAD_FLOAT, 0);
        Xlog.d(TAG, "loadDefaultAllAppsIfNecessary: sharedPreferences = "
                + sharedPreferences + "this =" + this);

        final boolean loadDefault = sharedPreferences.getBoolean(
                DB_CREATED_BUT_DEFAULT_ALLAPPS_NOT_LOADED, true);
        Xlog.d(TAG, "loadDefaultAllAppsIfNecessary: loadDefault = "
                + loadDefault + ",mMaxIdInAllAppsList = " + mMaxIdInAllAppsList+ ",isSafeMode =" +isSafeMode);

        if (loadDefault||isSafeMode)
        {
            if(isSafeMode == true)
            {
              sOpenHelper.getWritableDatabase().delete(TABLE_NAME,null,null);

              // Populate all apps table with initial all app list
              SharedPreferences.Editor editor = sharedPreferences.edit();
              editor.putBoolean(DB_CREATED_BUT_DEFAULT_ALLAPPS_NOT_LOADED, true);
              // TO-DO modify the property to avoid always load default apps
              editor.commit();
            }
            else
            {
               Xlog.d(TAG, "sOpenHelpert= "+sOpenHelper+",getWritableDatabase="+sOpenHelper.getWritableDatabase());
              sOpenHelper.getWritableDatabase().delete(TABLE_NAME,null,null);
              // Populate all apps table with initial all app list
              SharedPreferences.Editor editor = sharedPreferences.edit();
              editor.putBoolean(DB_CREATED_BUT_DEFAULT_ALLAPPS_NOT_LOADED, false);
              // TO-DO modify the property to avoid always load default apps
              editor.commit();
            }
            Xlog.d(TAG, "loadDefaultAllAppsIfNecessary: loadDefaultAllAppsList = "
                + loadDefault + ",mMaxIdInAllAppsList = " + mMaxIdInAllAppsList);
            sOpenHelper.loadDefaultAllAppsList(sOpenHelper
                    .getWritableDatabase());
        }
        return loadDefault;
    }


    static class SqlArguments {
        public final String table;
        public final String where;
        public final String[] args;

        SqlArguments(Uri url, String where, String[] args) {
            if (url.getPathSegments().size() == 1) {
                this.table = url.getPathSegments().get(0);
                this.where = where;
                this.args = args;
            } else if (url.getPathSegments().size() != 2) {
                throw new IllegalArgumentException("Invalid URI: " + url);
            } else if (!TextUtils.isEmpty(where)) {
                throw new UnsupportedOperationException(
                        "WHERE clause not supported: " + url);
            } else {
                this.table = url.getPathSegments().get(0);
                this.where = "_id=" + ContentUris.parseId(url);
                this.args = null;
            }
        }

        SqlArguments(Uri url) {
            if (url.getPathSegments().size() == 1) {
                table = url.getPathSegments().get(0);
                where = null;
                args = null;
            } else {
                throw new IllegalArgumentException("Invalid URI: " + url);
            }
        }
    }
}
