package com.mediatek.dialer.activities;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import com.android.contacts.common.test.IntegrationTestUtils;
import com.android.dialer.R;
import com.android.dialer.util.LocaleTestUtils;
import com.mediatek.dialer.calllog.CallLogMultipleDeleteFragment;

import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.RemoteException;
import android.provider.CallLog.Calls;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Unit tests for the {@link CallLogMultipleDeleteActivity}.
 */
public class CallLogMultipleDeleteActivityTest extends
        ActivityInstrumentationTestCase2<CallLogMultipleDeleteActivity> {
    private static final String TAG = "CallLogMultipleDeleteActivityTest";

    private static final Random RNG = new Random();
    private static final int[] CALL_TYPES = new int[] { Calls.INCOMING_TYPE,
            Calls.OUTGOING_TYPE, Calls.MISSED_TYPE, };
    private IntegrationTestUtils mTestUtils;
    private CallLogMultipleDeleteActivity mActivityUnderTest;
    private CallLogMultipleDeleteFragment mCallLogMultipleDeleteFragment;
    private LocaleTestUtils mLocaleTestUtils;

    public CallLogMultipleDeleteActivityTest() {
        super(CallLogMultipleDeleteActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTestUtils = new IntegrationTestUtils(getInstrumentation());
        clearCallLogs();
        // Some of the tests rely on the text that appears on screen - safest to force a
        // specific locale.
        mLocaleTestUtils = new LocaleTestUtils(getInstrumentation().getTargetContext());
        mLocaleTestUtils.setLocale(Locale.US);
    }

    @Override
    protected void tearDown() throws Exception {
        mLocaleTestUtils.restoreLocale();
        mLocaleTestUtils = null;
        clearCallLogs();
        super.tearDown();
    }

    public void testCallLogDeletePartially() throws Throwable {
        // Create 10 call logs
        insertCallLogs();
        // Open call log multi-delete activity
        openCallLogMultiDeleteActivity();
        // Select and delete call logs
        int totalCount = mCallLogMultipleDeleteFragment.getListAdapter()
                .getCount();
        selectCallLogs(5);
        //assert has "5 selected"
        Button selectedItemsView = (Button) mActivityUnderTest.getActionBar()
                .getCustomView().findViewById(R.id.select_items);
        String text = "5 selected";
        assertNotNull("selectedItemsView should not be null", selectedItemsView);
        assertEquals("There should have been one TextView with text '" + text + "' but found "
                + selectedItemsView.getText(), text, selectedItemsView.getText());
        deleteSelectedCallLogs();
        Thread.sleep(1000);
        int deleteCount = totalCount - getCallLogCount();
        assertTrue("There should be 5 call logs deleted but it was "
                + deleteCount, deleteCount == 5);
    }

    public void testCallLogDeleteAll() throws Throwable {
        // Create 10 call logs
        insertCallLogs();
        // Open call log multi-delete activity
        openCallLogMultiDeleteActivity();
        // Select and delete call logs
        int totalCount = mCallLogMultipleDeleteFragment.getListAdapter()
                .getCount();
        // Delete all reset call logs
        selectAllCallLogs();
        Button selectedItemsView = (Button) mActivityUnderTest.getActionBar()
                .getCustomView().findViewById(R.id.select_items);
        String text = totalCount + " selected";
        assertNotNull("selectedItemsView should not be null", selectedItemsView);
        assertEquals("There should have been one TextView with text '" + text + "' but found "
                + selectedItemsView.getText(), text, selectedItemsView.getText());
        deleteSelectedCallLogs();
        Thread.sleep(1500);
        totalCount = getCallLogCount();
        assertEquals(0, totalCount);
    }

    private void insertCallLogs() throws Throwable {
        for (int index = 0; index < 10; ++index) {
            // Insert into the call log the newly generated entry.
            Context context = getInstrumentation().getTargetContext();
            String number = generateRandomNumber();
            int presentation = Calls.PRESENTATION_ALLOWED;
            int callType = CALL_TYPES[RNG.nextInt(CALL_TYPES.length)];
            int features = RNG.nextInt(1) > 0 ? Calls.FEATURES_VIDEO : 0;
            long start = Calendar.getInstance().getTimeInMillis();
            long dataUsage = (long) RNG.nextInt(52428800);
            try {
                Calls.addCall(null, context, number, presentation, callType,
                        features, getManualAccount(context), start,
                        RNG.nextInt(60 * 60), dataUsage);
                Log.d(TAG, "adding entry to call log");
            } catch (Exception e) {
                Log.d(TAG, "insert failed", e);
                throw e;
            }
        }
    }

    private void openCallLogMultiDeleteActivity() throws Throwable {
        Intent delteIntent = new Intent(
                getInstrumentation().getTargetContext(),
                CallLogMultipleDeleteActivity.class);
        setActivityIntent(delteIntent);
        assertNull(mActivityUnderTest);
        mActivityUnderTest = getActivity();
        mCallLogMultipleDeleteFragment = (CallLogMultipleDeleteFragment) mActivityUnderTest
                .getMultipleDeleteFragment();
        assertNotNull("activity should not be null", mActivityUnderTest);
        assertNotNull("fragment should not be null",
                mCallLogMultipleDeleteFragment);
    }

    private void selectCallLogs(final int count) throws Throwable {
        int totalCount = mCallLogMultipleDeleteFragment.getListAdapter()
                .getCount();
        assertTrue("There should be 10 or more call logs but it was "
                + totalCount, totalCount >= 10);
        assertTrue("The seletecd count(" + count
                + ") should smaler then totalCount " + totalCount,
                count < totalCount);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < count; i++) {
                    View view = mCallLogMultipleDeleteFragment.getListView()
                            .getChildAt(i);
                    mCallLogMultipleDeleteFragment.getListView()
                            .performItemClick(view, i, 0);
                }
            }
        });
        Thread.sleep(1000);
    }

    private void deleteSelectedCallLogs() throws Throwable {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mCallLogMultipleDeleteFragment.deleteSelectedCallItems();
                mActivityUnderTest.updateSelectedItemsView(0);
            }
        });
    }

    private void selectAllCallLogs() throws Throwable {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mActivityUnderTest.updateSelectedItemsView(mCallLogMultipleDeleteFragment.selectAllItems());
            }
        });
        Thread.sleep(1000);
        int totalCount = mCallLogMultipleDeleteFragment.getListAdapter()
                .getCount();
        int selectCount = mCallLogMultipleDeleteFragment.getSelectedItemCount();
        assertEquals(totalCount, selectCount);
    }

    private static String generateRandomNumber() {
        return String.format("5%09d", RNG.nextInt(1000000000));
    }

    private PhoneAccountHandle getManualAccount(Context context) {
        TelecomManager telecommManager = TelecomManager.from(context);
        List<PhoneAccountHandle> accountHandles = telecommManager
                .getCallCapablePhoneAccounts();
        if (accountHandles != null && accountHandles.size() > 0) {
            return accountHandles.get(RNG.nextInt(accountHandles.size()));
        } else {
            return null;
        }
    }

    private void clearCallLogs() {
        getInstrumentation().getTargetContext().getContentResolver()
                .delete(Calls.CONTENT_URI_WITH_VOICEMAIL, null, null);
    }

    private int getCallLogCount() throws Throwable {
        Cursor c = null;
        try {
            c = getInstrumentation()
                    .getTargetContext()
                    .getContentResolver()
                    .query(Calls.CONTENT_URI_WITH_VOICEMAIL,
                            new String[] { "_id" }, null, null, null);
            assertNotNull(c);
            return c.getCount();
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }
}
