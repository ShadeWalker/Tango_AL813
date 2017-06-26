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

package com.mediatek.dialer.activities;

import static com.android.dialer.CallDetailActivity.Tasks.UPDATE_PHONE_CALL_DETAILS;
import static com.android.dialer.voicemail.VoicemailPlaybackPresenter.Tasks.CHECK_FOR_CONTENT;
import static com.android.dialer.voicemail.VoicemailPlaybackPresenter.Tasks.PREPARE_MEDIA_PLAYER;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.VoicemailContract;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.Suppress;
import android.view.KeyEvent;
import android.view.Menu;
import android.widget.TextView;

import com.android.dialer.CallDetailActivity;
import com.android.dialer.R;
import com.android.dialer.util.AsyncTaskExecutors;
import com.android.dialer.util.FakeAsyncTaskExecutor;
import com.android.contacts.common.test.IntegrationTestUtils;
import com.android.dialer.util.LocaleTestUtils;
import com.android.internal.view.menu.ContextMenuBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

/**
 * Unit tests for the {@link CallDetailActivity}.
 */
@LargeTest
public class CallDetailActivityTest extends ActivityInstrumentationTestCase2<CallDetailActivity> {
    private static final String TEST_ASSET_NAME = "quick_test_recording.mp3";
    private static final String MIME_TYPE = "audio/mp3";
    private static final String CONTACT_NUMBER = "+1412555555";
    private static final String VOICEMAIL_FILE_LOCATION = "/sdcard/sadlfj893w4j23o9sfu.mp3";

    private Uri mCallLogUri;
    private Uri mVoicemailUri;
    private IntegrationTestUtils mTestUtils;
    private LocaleTestUtils mLocaleTestUtils;
    private FakeAsyncTaskExecutor mFakeAsyncTaskExecutor;
    private CallDetailActivity mActivityUnderTest;

    public CallDetailActivityTest() {
        super(CallDetailActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mFakeAsyncTaskExecutor = new FakeAsyncTaskExecutor(getInstrumentation());
        AsyncTaskExecutors.setFactoryForTest(mFakeAsyncTaskExecutor.getFactory());
        // I don't like the default of focus-mode for tests, the green focus border makes the
        // screenshots look weak.
        setActivityInitialTouchMode(true);
        mTestUtils = new IntegrationTestUtils(getInstrumentation());
        // Some of the tests rely on the text that appears on screen - safest to force a
        // specific locale.
        mLocaleTestUtils = new LocaleTestUtils(getInstrumentation().getTargetContext());
        mLocaleTestUtils.setLocale(Locale.US);
    }

    @Override
    protected void tearDown() throws Exception {
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        getInstrumentation().waitForIdleSync();
        mLocaleTestUtils.restoreLocale();
        mLocaleTestUtils = null;
        cleanUpUri();
        mTestUtils = null;
        AsyncTaskExecutors.setFactoryForTest(null);
        super.tearDown();
    }

    public void testVoiceMailCallDetailActivity() throws Throwable {
        //Init call log with voicemail
        setActivityIntentForTestVoicemailEntry();
        startActivityUnderTest();
        // When the activity first starts, we will show "Fetching voicemail" on the screen.
        // The duration should not be visible.
        assertHasOneTextViewContaining("Loading voicemail");
        assertZeroTextViewsContaining("00:00");
        //Test for bug where increase rate button with invalid voicemail causes a crash.
        mTestUtils.clickButton(mActivityUnderTest, R.id.playback_start_stop);
        mTestUtils.clickButton(mActivityUnderTest, R.id.rate_increase_button);
        //Test for bug where voicemails should not have remove-from-call-log entry.
        Menu menu = new ContextMenuBuilder(mActivityUnderTest);
        mActivityUnderTest.onCreateOptionsMenu(menu);
        mActivityUnderTest.onPrepareOptionsMenu(menu);
        assertFalse(menu.findItem(R.id.menu_remove_from_call_log).isVisible());

        // There is a background check that is testing to see if we have the content available.
        // Once that task completes, we shouldn't be showing the fetching message, we should
        // be showing "Buffering".
        mFakeAsyncTaskExecutor.runTask(CHECK_FOR_CONTENT);
        assertHasOneTextViewContaining("Buffering");
        assertZeroTextViewsContaining("Loading voicemail");

        // There should be exactly one background task ready to prepare the media player.
        // Preparing the media player will have thrown an IOException since the file doesn't exist.
        // This should have put a failed to play message on screen, buffering is gone.
        mFakeAsyncTaskExecutor.runTask(PREPARE_MEDIA_PLAYER);
        assertHasOneTextViewContaining("Couldn't play voicemail");
        assertZeroTextViewsContaining("Buffering");
    }

    public void testNormailCallDetailActivity() throws Throwable {
        //Initialize a normal call log without voicemail
        setActivityIntentForTestCallEntry();
        startActivityUnderTest();
        //Test for bug where should have remove-from-call-log entry.
        Menu menu = new ContextMenuBuilder(mActivityUnderTest);
        mActivityUnderTest.onCreateOptionsMenu(menu);
        mActivityUnderTest.onPrepareOptionsMenu(menu);
        assertTrue(menu.findItem(R.id.menu_remove_from_call_log).isVisible());
        //Test the call history
        assertHasOneTextViewContaining("Incoming call");
        assertHasOneTextViewContaining("1 min 10 sec");
        //Test the quick contact
        //mTestUtils.clickButton(mActivityUnderTest, R.id.quick_contact_photo);
        //Thread.sleep(2000);
    }

    private void setActivityIntentForTestCallEntry() {
        assertNull(mCallLogUri);
        ContentResolver contentResolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(CallLog.Calls.NUMBER, CONTACT_NUMBER);
        values.put(CallLog.Calls.NUMBER_PRESENTATION, CallLog.Calls.PRESENTATION_ALLOWED);
        values.put(CallLog.Calls.TYPE, CallLog.Calls.INCOMING_TYPE);
        values.put(CallLog.Calls.DURATION, "70");
        mCallLogUri = contentResolver.insert(CallLog.Calls.CONTENT_URI, values);
        setActivityIntent(new Intent(Intent.ACTION_VIEW, mCallLogUri));
    }

    private void setActivityIntentForTestVoicemailEntry() {
        assertNull(mVoicemailUri);
        ContentResolver contentResolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(VoicemailContract.Voicemails.NUMBER, CONTACT_NUMBER);
        values.put(VoicemailContract.Voicemails.HAS_CONTENT, 1);
        values.put(VoicemailContract.Voicemails._DATA, VOICEMAIL_FILE_LOCATION);
        mVoicemailUri = contentResolver.insert(VoicemailContract.Voicemails.CONTENT_URI, values);
        Uri callLogUri = ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI_WITH_VOICEMAIL,
                ContentUris.parseId(mVoicemailUri));
        Intent intent = new Intent(Intent.ACTION_VIEW, callLogUri);
        intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_URI, mVoicemailUri);
        setActivityIntent(intent);
    }

    private void cleanUpUri() {
        if (mVoicemailUri != null) {
            getContentResolver().delete(VoicemailContract.Voicemails.CONTENT_URI,
                    "_ID = ?", new String[] { String.valueOf(ContentUris.parseId(mVoicemailUri)) });
            mVoicemailUri = null;
        }
        if (mCallLogUri != null) {
            getContentResolver().delete(CallLog.Calls.CONTENT_URI_WITH_VOICEMAIL,
                    "_ID = ?", new String[] { String.valueOf(ContentUris.parseId(mCallLogUri)) });
            mCallLogUri = null;
        }
    }

    private ContentResolver getContentResolver() {
        return getInstrumentation().getTargetContext().getContentResolver();
    }

    private TextView assertHasOneTextViewContaining(String text) throws Throwable {
        assertNotNull(mActivityUnderTest);
        List<TextView> views = mTestUtils.getTextViewsWithString(mActivityUnderTest, text);
        assertEquals("There should have been one TextView with text '" + text + "' but found "
                + views, 1, views.size());
        return views.get(0);
    }

    private void assertZeroTextViewsContaining(String text) throws Throwable {
        assertNotNull(mActivityUnderTest);
        List<TextView> views = mTestUtils.getTextViewsWithString(mActivityUnderTest, text);
        assertEquals("There should have been no TextViews with text '" + text + "' but found "
                + views, 0,  views.size());
    }

    private void startActivityUnderTest() throws Throwable {
        assertNull(mActivityUnderTest);
        mActivityUnderTest = getActivity();
        assertNotNull("activity should not be null", mActivityUnderTest);
        // We have to run all tasks, not just one.
        // This is because it seems that we can have onResume, onPause, onResume during the course
        // of a single unit test.
        mFakeAsyncTaskExecutor.runAllTasks(UPDATE_PHONE_CALL_DETAILS);
    }
}
