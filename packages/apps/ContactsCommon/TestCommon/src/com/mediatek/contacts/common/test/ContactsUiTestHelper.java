package com.mediatek.contacts.common.test;

import android.content.Context;

import com.mediatek.contacts.util.LogUtils;

public class ContactsUiTestHelper {

    private static final String TAG = ContactsUiTestHelper.class.getSimpleName();
    private static final int ONE_MINUTE = 60000;
    private static int sCounter = 0;

    private ContactsUiTestHelper() {
        // do nothing
    }

    /**
     * in multi-select view, call this function to select-all and press "OK"
     * Notice: some operations, like Delete-all, need to confirm after "OK" pressed
     * should call {@link ContactsUiTestHelper#clickOK(Solo)} again.
     *
     * @param solo
     */
    public static void selectAllAndConfirm(SoloWrapper solo, Context targetContext) {
        solo.clickOnText("0 ");
        solo.clickOnText(TestUtils.getTargetString(targetContext, "menu_select_all"));
        screenshot(solo, "already_select_all");
        clickOK(solo);
    }

    /**
     * Call this function in PeopleActivity, it will clear all contacts.
     * Notice: the timeout is 1 minute
     * @param solo
     */
    public static void clearAllContacts(SoloWrapper solo, Context targetContext) {
        /// show all contacts
        solo.clickOnMenuItem(TestUtils.getTargetString(targetContext, "menu_contacts_filter"));
        screenshot(solo, "contacts_filter_preesed");
        solo.clickOnText(TestUtils.getTargetString(targetContext, "contactsAllLabel"));

        String noContactsString = TestUtils.getTargetString(targetContext, "noContacts");
        LogUtils.d(TAG, "the no contact string: " + noContactsString);

        /// if "no contacts", means already cleared
        if (solo.waitForText(noContactsString)) {
            screenshot(solo, "no_contacts");
            LogUtils.i(TAG, "already no contacts, skip clear");
            return;
        }
        screenshot(solo, "has_contacts");

        /// delete all visible contacts, timeout 5 seconds
        solo.clickOnMenuItem(TestUtils.getTargetString(targetContext, "menu_delete_contact"));
        screenshot(solo, "delete_all_menu_pressed");
        selectAllAndConfirm(solo, targetContext);
        solo.clickOnButton(solo.getString(android.R.string.ok));
        solo.waitForText(noContactsString);
        screenshot(solo, "already_delete_all");
    }

    /**
     * in any cases to click the "ok" button, call this function
     * @param solo
     */
    public static void clickOK(SoloWrapper solo) {
        solo.clickOnButton(solo.getString(android.R.string.ok));
    }

    public static void screenshot(SoloWrapper solo, String tag) {
        solo.takeScreenshot(getCountText() + "-ContactsSanity-" + tag.trim());
    }

    private static String getCountText() {
        return String.valueOf(sCounter++);
    }
}