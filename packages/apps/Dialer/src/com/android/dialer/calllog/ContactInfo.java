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

package com.android.dialer.calllog;

import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;

import com.android.contacts.common.util.UriUtils;
import com.google.common.base.Objects;

/**
 * Information for a contact as needed by the Call Log.
 */
public class ContactInfo {
    public Uri lookupUri;
    public String lookupKey;
    public String name;
    public int type;
    public String label;
    public String number;
    public String formattedNumber;
    public String normalizedNumber;
    /** The photo for the contact, if available. */
    public long photoId;
    /** The high-res photo for the contact, if available. */
    public Uri photoUri;
    public boolean isBadData;
    public String objectId;

    public static ContactInfo EMPTY = new ContactInfo();

    public static String GEOCODE_AS_LABEL = "";

    public int sourceType = 0;

    @Override
    public int hashCode() {
        // Uses only name and contactUri to determine hashcode.
        // This should be sufficient to have a reasonable distribution of hash codes.
        // Moreover, there should be no two people with the same lookupUri.
        final int prime = 31;
        int result = 1;
        result = prime * result + ((lookupUri == null) ? 0 : lookupUri.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        ContactInfo other = (ContactInfo) obj;
        if (!UriUtils.areEqual(lookupUri, other.lookupUri)) return false;
        if (!TextUtils.equals(name, other.name)) return false;
        if (type != other.type) return false;
        if (!TextUtils.equals(label, other.label)) return false;
        if (!TextUtils.equals(number, other.number)) return false;
        if (!TextUtils.equals(formattedNumber, other.formattedNumber)) return false;
        if (!TextUtils.equals(normalizedNumber, other.normalizedNumber)) return false;
        if (photoId != other.photoId) return false;
        if (!UriUtils.areEqual(photoUri, other.photoUri)) return false;
        if (!TextUtils.equals(objectId, other.objectId)) return false;
        return true;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("lookupUri", lookupUri).add("name", name).add(
                "type", type).add("label", label).add("number", number).add("formattedNumber",
                formattedNumber).add("normalizedNumber", normalizedNumber).add("photoId", photoId)
                .add("photoUri", photoUri).add("objectId", objectId).toString();
    }

    ///---------------------------------------Mediatek-------------------------
    ///[Union Query] for MTK calllog query
    //-1 indicates phone contacts, >0 indicates sim id for sim contacts.
    public int contactSimId;
    public String ipPrefix;
    public int rawContactId;
    public int contactId;
    public int isSdnContact;

    public static ContactInfo getContactInfofromCursor(Cursor c) {
        if (null == c) {
            new Exception("ContactInfo.fromCursor(c) - c is null").printStackTrace();
            return null;
        }
        ContactInfo newContactInfo = new ContactInfo();
        if (null != newContactInfo) {
            try {
                newContactInfo.number = c.getString(CallLogQuery.NUMBER);
                newContactInfo.type = c.getInt(CallLogQuery.CALLS_JOIN_DATA_VIEW_CALL_NUMBER_TYPE_ID);
                newContactInfo.name = c.getString(CallLogQuery.CALLS_JOIN_DATA_VIEW_DISPLAY_NAME);
                newContactInfo.label = c
                        .getString(CallLogQuery.CALLS_JOIN_DATA_VIEW_CALL_NUMBER_TYPE);
                newContactInfo.photoId = c.getLong(CallLogQuery.CALLS_JOIN_DATA_VIEW_PHOTO_ID);
                String photo = c.getString(CallLogQuery.CALLS_JOIN_DATA_VIEW_PHOTO_URI);
                newContactInfo.photoUri = (null == photo) ? null : Uri.parse(photo);
                newContactInfo.normalizedNumber = newContactInfo.number; // TODO format number
                newContactInfo.formattedNumber = newContactInfo.number; // TODO format number
                newContactInfo.contactSimId = c
                        .getInt(CallLogQuery.CALLS_JOIN_DATA_VIEW_INDICATE_PHONE_SIM);
                newContactInfo.contactId = c.getInt(CallLogQuery.CALLS_JOIN_DATA_VIEW_CONTACT_ID);
                String lookUp = c.getString(CallLogQuery.CALLS_JOIN_DATA_VIEW_LOOKUP_KEY);
                newContactInfo.lookupUri = (newContactInfo.contactId == 0) ? null : Contacts.getLookupUri(
                        newContactInfo.contactId, lookUp);
                newContactInfo.ipPrefix = c.getString(CallLogQuery.CALLS_JOIN_DATA_VIEW_IP_PREFIX);
                newContactInfo.rawContactId = c.getInt(CallLogQuery.CALLS_JOIN_DATA_VIEW_RAW_CONTACT_ID);
                newContactInfo.isSdnContact = c.getInt(CallLogQuery.CALLS_JOIN_DATA_VIEW_IS_SDN_CONTACT);
            } catch (SQLiteException e) {
                e.printStackTrace();
            }
        }

        return newContactInfo;
    }
}
