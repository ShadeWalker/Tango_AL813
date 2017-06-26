/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2013 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.dialer;

import android.app.Application;
import android.content.Context;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.extensions.ExtensionsFactory;
import com.android.contacts.commonbind.analytics.AnalyticsUtil;
import com.mediatek.dialer.calllog.PhoneAccountInfoHelper;
import com.mediatek.dialer.dialersearch.DialerSearchHelper;
import com.mediatek.dialer.ext.ExtensionManager;
import com.mediatek.contacts.GlobalEnv;

public class DialerApplication extends Application {

    private ContactPhotoManager mContactPhotoManager;
    private static Context sContext;

    @Override
    public void onCreate() {
        super.onCreate();
        sContext = getApplicationContext();
        /// M: for ALPS01907201, init GlobalEnv
        GlobalEnv.setApplicationContext(sContext);
        ExtensionsFactory.init(sContext);
        AnalyticsUtil.initialize(this);
        PhoneAccountInfoHelper.INSTANCE.init(sContext);
        /// M: for Plug-in @{
        ExtensionManager.getInstance().init(this);
        com.mediatek.contacts.ExtensionManager.registerApplicationContext(this);
        /// @}

        /// M: for ALPS01762713 @{
        // workaround for code defect in ContactsPreferences, init it in main tread
        DialerSearchHelper.initContactsPreferences(sContext);
        /// @}
    }

    @Override
    public Object getSystemService(String name) {
        if (ContactPhotoManager.CONTACT_PHOTO_SERVICE.equals(name)) {
            if (mContactPhotoManager == null) {
                mContactPhotoManager = ContactPhotoManager.createContactPhotoManager(this);
                registerComponentCallbacks(mContactPhotoManager);
                mContactPhotoManager.preloadPhotosInBackground();
            }
            return mContactPhotoManager;
        }

        return super.getSystemService(name);
    }

    public static Context getDialerContext() {
        return sContext;
    }
/*HQ_zhangjing add for dialer merge to contact begin*/
    public static void setDialerContext(Context context){
         sContext = context;
    }	
/*HQ_zhangjing add for dialer merge to contact emd*/
}
