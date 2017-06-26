package com.mediatek.contacts.common.test;

import android.content.Context;

public class ContactsTestHelper {

    private ContactsTestHelper() {
        // do nothing
    }

    public static void clearAllContacts(Context context) {
        new ContactsRemover(context).removeAllContactsBothInSimAndDb();
    }
}
