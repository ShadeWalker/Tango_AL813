/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.email;

import android.app.Application;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Intent;

import android.content.res.Configuration;
import android.os.Build;
import android.os.Trace;

import com.android.email.activity.setup.EmailPreferenceActivity;
import com.android.email.preferences.EmailPreferenceMigrator;
import com.android.email.provider.EmailProvider;
import com.android.mail.browse.ConversationMessage;
import com.android.mail.browse.InlineAttachmentViewIntentBuilder;
import com.android.mail.browse.InlineAttachmentViewIntentBuilderCreator;
import com.android.mail.browse.InlineAttachmentViewIntentBuilderCreatorHolder;
import com.android.email2.ui.MailActivityEmail;
import com.android.mail.preferences.BasePreferenceMigrator;
import com.android.mail.preferences.PreferenceMigratorHolder;
import com.android.mail.preferences.PreferenceMigratorHolder.PreferenceMigratorCreator;
import com.android.mail.providers.Account;
import com.android.mail.ui.settings.PublicPreferenceActivity;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.StorageLowState;

import com.mediatek.email.extension.OPExtensionFactory;
import com.mediatek.email.util.EmailLowStorageHandler;
import com.mediatek.mail.vip.VipMemberCache;

public class EmailApplication extends Application {
    private static final String LOG_TAG = "Email";

    static {
        LogTag.setLogTag(LOG_TAG);

        PreferenceMigratorHolder.setPreferenceMigratorCreator(new PreferenceMigratorCreator() {
            @Override
            public BasePreferenceMigrator createPreferenceMigrator() {
                return new EmailPreferenceMigrator();
            }
        });

        InlineAttachmentViewIntentBuilderCreatorHolder.setInlineAttachmentViewIntentCreator(
                new InlineAttachmentViewIntentBuilderCreator() {
                    @Override
                    public InlineAttachmentViewIntentBuilder
                    createInlineAttachmentViewIntentBuilder(Account account, long conversationId) {
                        return new InlineAttachmentViewIntentBuilder() {
                            @Override
                            public Intent createInlineAttachmentViewIntent(Context context,
                                    String url, ConversationMessage message) {
                                return null;
                            }
                        };
                    }
                });

        PublicPreferenceActivity.sPreferenceActivityClass = EmailPreferenceActivity.class;

        NotificationControllerCreatorHolder.setNotificationControllerCreator(
                new NotificationControllerCreator() {
                    @Override
                    public NotificationController getInstance(Context context){
                        return EmailNotificationController.getInstance(context);
                    }
                });
    }

    /**
     * M: Monitor the configuration change, and update the plugin's context.
     * @see android.app.Application#onConfigurationChanged(android.content.res.Configuration)
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        OPExtensionFactory.resetAllPluginObject(getApplicationContext());
    }

    @Override
    public void onCreate() {
        /// M: add trace with AMS tag, for AMS bindApplication process.
        Trace.beginSection("+logEmailApplicationLaunchTime : onCreate");
        super.onCreate();
        /**
         * M: Enable services after Instrumentation.onCreate to avoid potential Service ANR
         * Reason:
         *     BindApplication will be called as AttachApplication->InstallProviders->
         *     Instrumentation.onCreate->Application.onCreate.
         *     It is tricky that Instrumentation.onCreate may take a long time(10s-30s+) to
         *     load classes from cached dexfile when running InstrumentationTest.
         * Solution:
         *      Move services enable from EmailProvider.onCreate to EmailApplication.onCreate
         *      to make sure we start service after Insturmentation.onCreate.
         */
        EmailProvider.setServicesEnabledAsync(this);

        // M: Init the Vip member cache
        VipMemberCache.init(this);
        /// M: Set low storage handler for email.
        StorageLowState.registerHandler(new EmailLowStorageHandler(this));
        /// M: Should active to check the storage state when we register handler to
        //  avoid email launched behind the low storage broadcast.
        StorageLowState.checkStorageLowMode(this);
        /// M: for debugging fragment issue.
        FragmentManager.enableDebugLogging(Build.TYPE.equals("eng"));
        /// M: for debuging loader issue.
        LoaderManager.enableDebugLogging(Build.TYPE.equals("eng"));
        /// M: add trace with AMS tag, for AMS bindApplication process.
        Trace.endSection();
    }
}
