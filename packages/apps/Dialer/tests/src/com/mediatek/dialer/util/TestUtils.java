package com.mediatek.dialer.util;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.TextUtils;
import android.util.Log;

public class TestUtils {

    private static String TAG = TestUtils.class.getSimpleName();

    public static long createRawContact(ContentResolver resolver, String name, String phoneNumber) {
        ContentValues values = new ContentValues();
        Uri rawContactUri = resolver.insert(RawContacts.CONTENT_URI, values);
        long rawContactId = ContentUris.parseId(rawContactUri);

        if (!TextUtils.isEmpty(phoneNumber)) {
            insertPhoneNumber(resolver, rawContactId, phoneNumber);
        }

        if (!TextUtils.isEmpty(name)) {
            insertStructuredName(resolver, rawContactId, name, null);
        }

        return rawContactId;
    }

    public static Uri insertPhoneNumber(ContentResolver resolver, long rawContactId, String phoneNumber) {
        ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        values.put(Phone.NUMBER, phoneNumber);
        values.put(Phone.TYPE, Phone.TYPE_HOME);
        values.put(Data.IS_PRIMARY, 0);

        Uri resultUri = resolver.insert(Data.CONTENT_URI, values);
        return resultUri;
    }

    public static Uri insertStructuredName(ContentResolver resolver, long rawContactId,
            String givenName, String familyName) {
        ContentValues values = new ContentValues();
        StringBuilder sb = new StringBuilder();
        if (givenName != null) {
            sb.append(givenName);
        }
        if (givenName != null && familyName != null) {
            sb.append(" ");
        }
        if (familyName != null) {
            sb.append(familyName);
        }
        values.put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, sb.toString());
        values.put(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, givenName);
        values.put(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, familyName);

        return insertStructuredName(resolver, rawContactId, values);
    }

    public static Uri insertStructuredName(ContentResolver resolver, long rawContactId,
            ContentValues values) {
        values.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
        values.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
        Uri resultUri = resolver.insert(ContactsContract.Data.CONTENT_URI, values);
        return resultUri;
    }

    public static void deleteContact(ContentResolver resolver, long rawContactId) {
        long contactId = queryContactId(resolver, rawContactId);
        Log.d(TAG, "contactId: " + contactId);

        if (contactId != -1) {
            Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
            Uri lookupUri = Contacts.getLookupUri(resolver, contactUri);

            int count = resolver.delete(lookupUri, null, null);
            Log.d(TAG, "count: " + count);
        } else {
            Log.d(TAG, "--- no contacts ---");
        }

    }

    public static long queryContactId(ContentResolver resolver, long rawContactId) {
        Cursor c = queryRawContact(resolver, rawContactId);
        long contactId = -1;
        if (c != null) {
            c.moveToFirst();
            contactId = c.getLong(c.getColumnIndex(RawContacts.CONTACT_ID));
            c.close();
        }

        return contactId;
    }

    public static  Cursor queryRawContact(ContentResolver resolver, long rawContactId) {
        return resolver.query(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                null, null, null, null);
    }

    /**
     * Sleep for 500ms.
     */
    public static void sleep() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Log.w(TAG, "Sleep interrupted.");
        }
    }
}
