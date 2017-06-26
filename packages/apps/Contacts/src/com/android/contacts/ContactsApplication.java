/*
 * Copyright (C) 2014 MediaTek Inc.
 * Modification based on code covered by the mentioned copyright
 * and/or permission notice(s).
 */
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

package com.android.contacts;

import android.app.Application;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.Contacts;
import android.util.Log;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.list.ContactListFilterController;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.testing.InjectedServices;
import com.android.contacts.common.util.Constants;
import com.android.contacts.commonbind.analytics.AnalyticsUtil;
import com.google.common.annotations.VisibleForTesting;
import com.mediatek.contacts.ContactsApplicationEx;
import com.mediatek.contacts.simcontact.SlotUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONException;
import org.json.JSONObject;


/*HQ_zhangjing add for dialer merge to contact begin*/
import com.android.contacts.common.extensions.ExtensionsFactory;
import com.mediatek.dialer.dialersearch.DialerSearchHelper;
import com.mediatek.dialer.ext.ExtensionManager;
import com.mediatek.dialer.calllog.PhoneAccountInfoHelper;
import com.android.dialer.DialerApplication;
import com.android.mms.MmsApp;
/*HQ_zhangjing add for dialer merge to contact end*/


/*HQ_zhangjing 2015-08-10 modified for MMS merge begin*/
public final class ContactsApplication extends MmsApp {
/*HQ_zhangjing 2015-08-10 modified for MMS merge end*/
	private static final boolean ENABLE_LOADER_LOG = false; // Don't submit with
															// true
	private static final boolean ENABLE_FRAGMENT_LOG = false; // Don't submit
																// with true

	private static InjectedServices sInjectedServices;
	/**
	 * Log tag for enabling/disabling StrictMode violation log. To enable: adb
	 * shell setprop log.tag.ContactsStrictMode DEBUG
	 */
	public static final String STRICT_MODE_TAG = "ContactsStrictMode";
	private ContactPhotoManager mContactPhotoManager;
	private ContactListFilterController mContactListFilterController;
	// / M: Single thread, don't simultaneously handle contacts
	// copy-delete-import-export request.
	private final ExecutorService mSingleTaskService = Executors
			.newSingleThreadExecutor();
	private static final String TAG="ContactsApplication";
/*HQ_zhangjing add for dialer merge to contact begin*/
    private static Context sContext;
/*HQ_zhangjing add for dialer merge to contact end*/
	/**
	 * Overrides the system services with mocks for testing.
	 */
	@VisibleForTesting
	public static void injectServices(InjectedServices services) {
		sInjectedServices = services;
	}

	public static InjectedServices getInjectedServices() {
		return sInjectedServices;
	}

	@Override
	public ContentResolver getContentResolver() {
		if (sInjectedServices != null) {
			ContentResolver resolver = sInjectedServices.getContentResolver();
			if (resolver != null) {
				return resolver;
			}
		}
		return super.getContentResolver();
	}

	@Override
	public SharedPreferences getSharedPreferences(String name, int mode) {
		if (sInjectedServices != null) {
			SharedPreferences prefs = sInjectedServices.getSharedPreferences();
			if (prefs != null) {
				return prefs;
			}
		}

		return super.getSharedPreferences(name, mode);
	}

	@Override
	public Object getSystemService(String name) {
		if (sInjectedServices != null) {
			Object service = sInjectedServices.getSystemService(name);
			if (service != null) {
				return service;
			}
		}

		if (ContactPhotoManager.CONTACT_PHOTO_SERVICE.equals(name)) {
			if (mContactPhotoManager == null) {
				mContactPhotoManager = ContactPhotoManager
						.createContactPhotoManager(this);
				registerComponentCallbacks(mContactPhotoManager);
				mContactPhotoManager.preloadPhotosInBackground();
			}
			return mContactPhotoManager;
		}

		return super.getSystemService(name);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
			Log.d(Constants.PERFORMANCE_TAG,
					"ContactsApplication.onCreate start");
		}

		if (ENABLE_FRAGMENT_LOG)
			FragmentManager.enableDebugLogging(true);
		if (ENABLE_LOADER_LOG)
			LoaderManager.enableDebugLogging(true);

		if (Log.isLoggable(STRICT_MODE_TAG, Log.DEBUG)) {
			StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
					.detectAll().penaltyLog().build());
		}

		// / M: Set the application context to some class and clear
		// notification.
		ContactsApplicationEx.onCreateEx(this);

		// Perform the initialization that doesn't have to finish immediately.
		// We use an async task here just to avoid creating a new thread.
		(new DelayedInitializer()).execute();

		if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
			Log.d(Constants.PERFORMANCE_TAG,
					"ContactsApplication.onCreate finish");
		}
/*HQ_zhangjing add for dialer merge to contact begin*/
        sContext = getApplicationContext();
        DialerApplication.setDialerContext(sContext);
        ExtensionsFactory.init(sContext);
        PhoneAccountInfoHelper.INSTANCE.init(sContext);
        /// M: for Plug-in @{
        ExtensionManager.getInstance().init(this);
        com.mediatek.contacts.ExtensionManager.registerApplicationContext(this);
        /// @}
        DialerSearchHelper.initContactsPreferences(sContext);
/*HQ_zhangjing add for dialer merge to contact end*/
		AnalyticsUtil.initialize(this);

	}


	private class DelayedInitializer extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			// / M: Need to refresh active usim phb infos firstly.
			SlotUtils.refreshActiveUsimPhbInfos();
			final Context context = ContactsApplication.this;

			// Warm up the preferences, the account type manager and the
			// contacts provider.
			PreferenceManager.getDefaultSharedPreferences(context);
			AccountTypeManager.getInstance(context);
			getContentResolver().getType(
					ContentUris.withAppendedId(Contacts.CONTENT_URI, 1));
			return null;
		}

		public void execute() {
			executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
		}
	}

	/**
	 * M: Get the ContactsApplication Instance.
	 */
	public static ContactsApplication getInstance() {
		return ContactsApplicationEx.getContactsApplication();
	}

	/**
	 * M: Get Application Task Sevice.
	 */
	public ExecutorService getApplicationTaskService() {
		return mSingleTaskService;
	}
	
//		add by niubi tang for contacts sqlite query start
	private static final String CONTACTS2_DB = "/data/data/com.android.providers.contacts/databases/contacts2.db";
	private static SQLiteDatabase db;

	/**
	 * 开启联系人数据库
	 * @return db
	 */
	public static SQLiteDatabase getContactsDb(){
		if (db==null||!db.isOpen()) {
			db = SQLiteDatabase
					.openDatabase(
							CONTACTS2_DB,
							null, 0);
		}
		return db;
	}
	
	/**
	 * 关闭联系人数据库
	 */
	public static void closeContactDb() {
		if(db!=null){
			if(db.isOpen()){
				db.close();
			}
		}
	}
//		add by niubi tang for contacts sqlite query end
}
