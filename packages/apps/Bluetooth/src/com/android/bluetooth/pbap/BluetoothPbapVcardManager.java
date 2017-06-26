/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 * Copyright (C) 2009-2012, Broadcom Corporation
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.pbap;

import android.content.ContentResolver;
import android.content.Context;
import android.database.CursorWindowAllocationException;
import android.database.Cursor;
import android.location.CountryDetector;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.RawContacts;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.android.bluetooth.R;
import com.android.vcard.VCardComposer;
import com.android.vcard.VCardConfig;
import com.android.vcard.VCardPhoneNumberTranslationCallback;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import javax.obex.ServerOperation;
import javax.obex.Operation;
import javax.obex.ResponseCodes;

import com.android.bluetooth.Utils;

public class BluetoothPbapVcardManager {
    private static final String TAG = "BluetoothPbapVcardManager";

    private static final boolean V = BluetoothPbapService.VERBOSE;

    private static final boolean D = BluetoothPbapService.DEBUG;

    private ContentResolver mResolver;

    private Context mContext;

    static final String[] PHONES_PROJECTION = new String[] {
            Data._ID, // 0
            CommonDataKinds.Phone.TYPE, // 1
            CommonDataKinds.Phone.LABEL, // 2
            CommonDataKinds.Phone.NUMBER, // 3
            Contacts.DISPLAY_NAME, // 4
    };

    private static final int PHONE_NUMBER_COLUMN_INDEX = 3;

    static final String SORT_ORDER_PHONE_NUMBER = CommonDataKinds.Phone.NUMBER + " ASC";

    static final String[] CONTACTS_PROJECTION = new String[] {
            Contacts._ID, // 0
            Contacts.DISPLAY_NAME, // 1
    };

    static final int CONTACTS_ID_COLUMN_INDEX = 0;

    static final int CONTACTS_NAME_COLUMN_INDEX = 1;

    // For support some Devices need Sim PhoneBook by only query phoneBook
    private boolean mIOTSolutionOn = false;

    // For support search name by number, can search by contain not startwith
    private boolean mMTKSearchNumber = false;

    // For support search call log
    private boolean mSearchCallLogOn = true;

    // call histories use dynamic handles, and handles should order by date; the
    // most recently one should be the first handle. In table "calls", _id and
    // date are consistent in ordering, to implement simply, we sort by _id
    // here.
    static final String CALLLOG_SORT_ORDER = Calls._ID + " DESC";

    private static final String CLAUSE_ONLY_VISIBLE = Contacts.IN_VISIBLE_GROUP + "=1";

    public BluetoothPbapVcardManager(final Context context) {
        mContext = context;
        mResolver = mContext.getContentResolver();
        mIOTSolutionOn = false;
    }

    public void setIOTSolution(boolean enabled) {
        mIOTSolutionOn = enabled;
    }

    private boolean getIOTSolution() {
        return mIOTSolutionOn;
    }
    /**
     * Create an owner vcard from the configured profile
     * @param vcardType21
     * @return
     */
    private final String getOwnerPhoneNumberVcardFromProfile(final boolean vcardType21, final byte[] filter) {
        if (D) Log.d(TAG, "[PBAP]getOwnerPhoneNumberVcardFromProfile vcardType21 = " + vcardType21);
        // Currently only support Generic Vcard 2.1 and 3.0
        int vcardType;
        if (vcardType21) {
            vcardType = VCardConfig.VCARD_TYPE_V21_GENERIC;
        } else {
            vcardType = VCardConfig.VCARD_TYPE_V30_GENERIC;
        }

        if (!BluetoothPbapConfig.includePhotosInVcard()) {
            vcardType |= VCardConfig.FLAG_REFRAIN_IMAGE_EXPORT;
        }

        return BluetoothPbapUtils.createProfileVCard(mContext, vcardType, filter);
    }

    public final String getOwnerPhoneNumberVcard(final boolean vcardType21, final byte[] filter) {
        //Owner vCard enhancement: Use "ME" profile if configured
        if (BluetoothPbapConfig.useProfileForOwnerVcard()) {
            String vcard = getOwnerPhoneNumberVcardFromProfile(vcardType21, filter);
            if (vcard != null && vcard.length() != 0) {
                if (D) Log.d(TAG, "[PBAP]getOwnerPhoneNumberVcardFromProfile get null or vcard length = 0");
                return vcard;
            }
        }
        //End enhancement

        BluetoothPbapCallLogComposer composer = new BluetoothPbapCallLogComposer(mContext);
        String name = BluetoothPbapService.getLocalPhoneName();
        String number = BluetoothPbapService.getLocalPhoneNum();
        String vcard = composer.composeVCardForPhoneOwnNumber(Phone.TYPE_MOBILE, name, number,
                vcardType21);
        return vcard;
    }

    private final String getSelectionForPhonebook(final int type) {
        String selection = null;
        if (type == BluetoothPbapObexServer.ContentType.PHONEBOOK) {
            if (!getIOTSolution()) {
                selection = RawContacts.INDICATE_PHONE_SIM + "=" + RawContacts.INDICATE_PHONE;
            } else {
                selection = "";
            }
        } else if (type == BluetoothPbapObexServer.ContentType.SIM1_PHONEBOOK) {
            if (!getIOTSolution()) {
                selection = RawContacts.INDICATE_PHONE_SIM + ">" + RawContacts.INDICATE_PHONE;
            } else {
                selection = Contacts.IN_VISIBLE_GROUP + "=" + 1000;;
            }
        } else {
            Log.e(TAG, "getSelectionForPhonebook() type is invalid");
            return null;
        }
        if (selection != null && selection.length() > 0) {
            if (type != BluetoothPbapObexServer.ContentType.SIM1_PHONEBOOK) {
                // new Change Feature, contacts not support VISIBLE for SIM
                // phonebook
                selection = "(" + selection + ")" + " AND " + "(" + CLAUSE_ONLY_VISIBLE + ")";
            }
        } else {
            if (type != BluetoothPbapObexServer.ContentType.SIM1_PHONEBOOK) {
                selection = CLAUSE_ONLY_VISIBLE;
            } else {
                selection = "";
            }
        }

        return selection;
    }

    public final int getPhonebookSize(final int type) {
        int size;
        switch (type) {
            case BluetoothPbapObexServer.ContentType.PHONEBOOK:
            case BluetoothPbapObexServer.ContentType.SIM1_PHONEBOOK:
                size = getContactsSize(type);
                break;
            default:
                size = getCallHistorySize(type);
                break;
        }
        if (V) Log.v(TAG, "getPhonebookSize size = " + size + " type = " + type);
        return size;
    }

    public final int getContactsSize(int type) {
        final Uri myUri = Contacts.CONTENT_URI;
        int size = 0;

        String selection = getSelectionForPhonebook(type);
        if (selection == null) {
            Log.e(TAG, "internal error: getContactsSize(), type is invalid");
            return 0;
        }
        Cursor contactCursor = null;
        try {
            contactCursor = mResolver.query(myUri, null, selection, null, null);
            if (contactCursor != null) {
                size = contactCursor.getCount() + 1; // always has the 0.vcf
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while getting Contacts size");
        } finally {
            if (contactCursor != null) {
                contactCursor.close();
                contactCursor = null;
            }
        }
        return size;
    }

    public final int getCallHistorySize(final int type) {
        final Uri myUri = CallLog.Calls.CONTENT_URI;
        String selection = BluetoothPbapObexServer.createSelectionPara(type);
        int size = 0;
        Cursor callCursor = null;
        try {
            callCursor = mResolver.query(myUri, null, selection, null,
                    CallLog.Calls.DEFAULT_SORT_ORDER);
            if (callCursor != null) {
                size = callCursor.getCount();
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while getting CallHistory size");
        } finally {
            if (callCursor != null) {
                callCursor.close();
                callCursor = null;
            }
        }
        return size;
    }

    public final SparseArray<String> loadCallHistoryList(final int type, final String searchVal, final String searchAttr) {
        final Uri myUri = CallLog.Calls.CONTENT_URI;
        String allSelection = BluetoothPbapObexServer.createSelectionPara(type);
        String selection = allSelection;
        if (mSearchCallLogOn) {
            if (!TextUtils.isEmpty(searchVal)) {
                if (selection != null) {
                    selection += " AND ";
                } else {
                    selection = new String();
                }
                if (searchAttr.equals("name")) {
                    selection += Calls.CACHED_NAME + " LIKE '%" + searchVal + "%'";
                } else {
                    selection += Calls.NUMBER + " LIKE '%" + searchVal + "%'";
                }
            }
        }
        String[] projection = new String[] {
                Calls.NUMBER, Calls.CACHED_NAME, Calls.NUMBER_PRESENTATION, Calls._ID
        };
        final int CALLS_NUMBER_COLUMN_INDEX = 0;
        final int CALLS_NAME_COLUMN_INDEX = 1;
        final int CALLS_NUMBER_PRESENTATION_COLUMN_INDEX = 2;
        final int CALLS_ID_COLUMN_INDEX = 3;

        Cursor callCursor = null;
        Cursor allCallsCursor = null;
        SparseArray<String> list = new SparseArray<String>();
        ArrayList<Integer> callIdList = new ArrayList<Integer>();
        try {
            callCursor = mResolver.query(myUri, projection, selection, null,
                    CALLLOG_SORT_ORDER);
            allCallsCursor = mResolver.query(myUri, projection, allSelection, null,
                    CALLLOG_SORT_ORDER);
            if (allCallsCursor != null) {
                for (allCallsCursor.moveToFirst(); !allCallsCursor.isAfterLast(); allCallsCursor
                        .moveToNext()) {
                    callIdList.add(allCallsCursor.getInt(CALLS_ID_COLUMN_INDEX));
                }
            }
            if (callCursor != null) {
                for (callCursor.moveToFirst(); !callCursor.isAfterLast();
                        callCursor.moveToNext()) {
                    String name = callCursor.getString(CALLS_NAME_COLUMN_INDEX);
                    if (TextUtils.isEmpty(name)) {
                        // name not found, use number instead
                        final int numberPresentation = callCursor.getInt(
                                CALLS_NUMBER_PRESENTATION_COLUMN_INDEX);
                        if (numberPresentation != Calls.PRESENTATION_ALLOWED) {
                            name = mContext.getString(R.string.unknownNumber);
                        } else {
                            name = callCursor.getString(CALLS_NUMBER_COLUMN_INDEX);
                        }
                    }
                    int index = callIdList.indexOf(callCursor.getInt(CALLS_ID_COLUMN_INDEX)) + 1;
                    list.put(index, name);
                }
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while loading CallHistory");
        } finally {
            if (callCursor != null) {
                callCursor.close();
                callCursor = null;
            }
        }
        return list;
    }

    public final ArrayList<String> getPhonebookNameList(final int type, final int orderByWhat) {
        ArrayList<String> nameList = new ArrayList<String>();
        //Owner vCard enhancement. Use "ME" profile if configured
        String ownerName = null;
        if (BluetoothPbapConfig.useProfileForOwnerVcard()) {
            ownerName = BluetoothPbapUtils.getProfileName(mContext);
        }
        if (ownerName == null || ownerName.length()==0) {
            ownerName = BluetoothPbapService.getLocalPhoneName();
        }
        nameList.add(ownerName);
        //End enhancement

        String selection = getSelectionForPhonebook(type);
        if (selection == null) {
            Log.e(TAG, "internal error: getPhonebookNameList(), type is invalid");
            return nameList;
        }

        final Uri myUri = Contacts.CONTENT_URI;
        Cursor contactCursor = null;
        try {
            if (orderByWhat == BluetoothPbapObexServer.ORDER_BY_INDEXED) {
                if (V) Log.v(TAG, "getPhonebookNameList, order by index");
                contactCursor = mResolver.query(myUri, CONTACTS_PROJECTION, selection,
                        null, Contacts._ID);
            } else if (orderByWhat == BluetoothPbapObexServer.ORDER_BY_ALPHABETICAL) {
                if (V) Log.v(TAG, "getPhonebookNameList, order by alpha");
                contactCursor = mResolver.query(myUri, CONTACTS_PROJECTION, selection,
                        null, Contacts.DISPLAY_NAME);
            }
            if (contactCursor != null) {
                for (contactCursor.moveToFirst(); !contactCursor.isAfterLast(); contactCursor
                        .moveToNext()) {
                    String name = contactCursor.getString(CONTACTS_NAME_COLUMN_INDEX);
                    long id = contactCursor.getLong(CONTACTS_ID_COLUMN_INDEX);
                    if (TextUtils.isEmpty(name)) {
                        name = mContext.getString(android.R.string.unknownName);
                    }
                    nameList.add(name + "," + id);
                }
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while getting Phonebook name list");
        } finally {
            if (contactCursor != null) {
                contactCursor.close();
                contactCursor = null;
            }
        }
        return nameList;
    }

    private final String getCurrentCountry() {
        String currentCountry = "";
        CountryDetector detector = (CountryDetector) mContext.getSystemService(Context.COUNTRY_DETECTOR);
        if (detector != null && detector.detectCountry() != null) {
            currentCountry = detector.detectCountry().getCountryIso();
        }
        return currentCountry;
    }

    public final ArrayList<String> getContactNamesByNumber(final int type, final String phoneNumber) {
        ArrayList<String> nameList = new ArrayList<String>();
        ArrayList<String> tempNameList = new ArrayList<String>();

        Cursor contactCursor = null;
        Uri uri = null;

        String selection = getSelectionForPhonebook(type);
        if (selection == null) {
            Log.e(TAG, "internal error: getContactNamesByNumber(), type is invalid");
            return nameList;
        }

        if (mMTKSearchNumber) {
            String country = getCurrentCountry();
            String formatedNum = PhoneNumberUtils.formatNumber(phoneNumber, phoneNumber, country);
            uri = Phone.CONTENT_URI;
            if (phoneNumber != null && phoneNumber.length() != 0) {
                if (selection != null && selection.length() == 0) {
                    selection = "(" + selection + ")" + " AND " + Phone.NUMBER + " LIKE '%"
                            + phoneNumber + "%'" + " OR " + Phone.NUMBER + " LIKE '%" + formatedNum
                            + "%'" + " OR " + Phone.NORMALIZED_NUMBER + " LIKE '%" + phoneNumber
                            + "%'";
                } else {
                    selection = Phone.NUMBER + " LIKE '%" + phoneNumber + "%'" + " OR "
                            + Phone.NUMBER + " LIKE '%" + formatedNum + "%'" + " OR "
                            + Phone.NORMALIZED_NUMBER + " LIKE '%" + phoneNumber + "%'";
                }
            }
        } else {
            if (phoneNumber != null && phoneNumber.length() == 0) {
                uri = Contacts.CONTENT_URI;
            } else {
                uri = Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI,
                    Uri.encode(phoneNumber));
            }
        }

        try {
            contactCursor = mResolver.query(uri, CONTACTS_PROJECTION, selection,
                        null, Contacts._ID);

            if (contactCursor != null) {
                for (contactCursor.moveToFirst(); !contactCursor.isAfterLast(); contactCursor
                        .moveToNext()) {
                    String name = contactCursor.getString(CONTACTS_NAME_COLUMN_INDEX);
                    long id = contactCursor.getLong(CONTACTS_ID_COLUMN_INDEX);
                    if (TextUtils.isEmpty(name)) {
                        name = mContext.getString(android.R.string.unknownName);
                    }
                    if (V) Log.v(TAG, "got name " + name + " by number " + phoneNumber + " @" + id);
                    tempNameList.add(name + "," + id);
                }
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while getting contact names");
        } finally {
            if (contactCursor != null) {
                contactCursor.close();
                contactCursor = null;
            }
        }
        int tempListSize = tempNameList.size();
        for (int index = 0; index < tempListSize; index++) {
            String object = tempNameList.get(index);
            if (!nameList.contains(object))
                nameList.add(object);
        }

        return nameList;
    }

    public final int composeAndSendCallLogVcards(final int type, Operation op,
            final int startPoint, final int endPoint, final boolean vcardType21,
            boolean ignorefilter, byte[] filter) {
        if (startPoint < 1 || startPoint > endPoint) {
            Log.e(TAG, "internal error: startPoint or endPoint is not correct.");
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }
        String typeSelection = BluetoothPbapObexServer.createSelectionPara(type);

        final Uri myUri = CallLog.Calls.CONTENT_URI;
        final String[] CALLLOG_PROJECTION = new String[] {
            CallLog.Calls._ID, // 0
        };
        final int ID_COLUMN_INDEX = 0;

        Cursor callsCursor = null;
        long startPointId = 0;
        long endPointId = 0;
        try {
            // Need test to see if order by _ID is ok here, or by date?
            callsCursor = mResolver.query(myUri, CALLLOG_PROJECTION, typeSelection, null,
                    CALLLOG_SORT_ORDER);
            if (callsCursor != null) {
                callsCursor.moveToPosition(startPoint - 1);
                startPointId = callsCursor.getLong(ID_COLUMN_INDEX);
                if (V) Log.v(TAG, "Call Log query startPointId = " + startPointId);
                if (startPoint == endPoint) {
                    endPointId = startPointId;
                } else {
                    callsCursor.moveToPosition(endPoint - 1);
                    endPointId = callsCursor.getLong(ID_COLUMN_INDEX);
                }
                if (V) Log.v(TAG, "Call log query endPointId = " + endPointId);
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while composing calllog vcards");
        } finally {
            if (callsCursor != null) {
                callsCursor.close();
                callsCursor = null;
            }
        }

        String recordSelection;
        if (startPoint == endPoint) {
            recordSelection = Calls._ID + "=" + startPointId;
        } else {
            // The query to call table is by "_id DESC" order, so change
            // correspondingly.
            recordSelection = Calls._ID + ">=" + endPointId + " AND " + Calls._ID + "<="
                    + startPointId;
        }

        String selection;
        if (typeSelection == null) {
            selection = recordSelection;
        } else {
            selection = "(" + typeSelection + ") AND (" + recordSelection + ")";
        }

        if (V) Log.v(TAG, "Call log query selection is: " + selection);

        return composeAndSendVCards(op, selection, vcardType21, null, false, ignorefilter, filter);
    }

    public final int composeAndSendPhonebookVcards(Operation op, final int type, final int startPoint,
            final int endPoint, final boolean vcardType21, String ownerVCard,
            boolean ignorefilter, byte[] filter) {
        if (startPoint < 1 || startPoint > endPoint) {
            Log.e(TAG, "internal error: startPoint or endPoint is not correct.");
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }
        final Uri myUri = Contacts.CONTENT_URI;
        String selection = getSelectionForPhonebook(type);
        if (selection == null) {
            Log.e(TAG, "internal error: composeAndSendPhonebookVcards(), type is invalid");
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }

        Cursor contactCursor = null;
        long startPointId = 0;
        long endPointId = 0;
        try {
            contactCursor = mResolver.query(myUri, CONTACTS_PROJECTION, selection, null,
                    Contacts._ID);
            if (contactCursor != null) {
                contactCursor.moveToPosition(startPoint - 1);
                startPointId = contactCursor.getLong(CONTACTS_ID_COLUMN_INDEX);
                if (V) Log.v(TAG, "Query startPointId = " + startPointId);
                if (startPoint == endPoint) {
                    endPointId = startPointId;
                } else {
                    contactCursor.moveToPosition(endPoint - 1);
                    endPointId = contactCursor.getLong(CONTACTS_ID_COLUMN_INDEX);
                }
                if (V) Log.v(TAG, "Query endPointId = " + endPointId);
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while composing phonebook vcards");
        } finally {
            if (contactCursor != null) {
                contactCursor.close();
                contactCursor = null;
            }
        }

        if (startPoint == endPoint) {
            selection = Contacts._ID + "=" + startPointId + " AND " + selection;
        } else {
            selection = Contacts._ID + ">=" + startPointId + " AND " + Contacts._ID + "<="
                    + endPointId + " AND " + selection;
        }

        if (V) Log.v(TAG, "Query selection is: " + selection);

        return composeAndSendVCards(op, selection, vcardType21, ownerVCard, true,
            ignorefilter, filter);
    }

    public final int composeAndSendPhonebookOneVcard(Operation op, final int type,
            final int offset, final boolean vcardType21, String ownerVCard, int orderByWhat,
            boolean ignorefilter, byte[] filter) {
        if (offset < 1) {
            Log.e(TAG, "Internal error: offset is not correct.");
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }
        final Uri myUri = Contacts.CONTENT_URI;
        Cursor contactCursor = null;
        String selection = getSelectionForPhonebook(type);
        long contactId = 0;

        if (selection == null) {
            Log.e(TAG, "internal error: composeAndSendPhonebookOneVcard(), type is invalid");
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }

        if (orderByWhat == BluetoothPbapObexServer.ORDER_BY_INDEXED) {
            try {
                contactCursor = mResolver.query(myUri, CONTACTS_PROJECTION, selection,
                        null, Contacts._ID);
                if (contactCursor != null) {
                    contactCursor.moveToPosition(offset - 1);
                    contactId = contactCursor.getLong(CONTACTS_ID_COLUMN_INDEX);
                    if (V) Log.v(TAG, "Query startPointId = " + contactId);
                }
            } catch (CursorWindowAllocationException e) {
                Log.e(TAG, "CursorWindowAllocationException while composing phonebook one vcard order by index");
            } finally {
                if (contactCursor != null) {
                    contactCursor.close();
                    contactCursor = null;
                }
            }
        } else if (orderByWhat == BluetoothPbapObexServer.ORDER_BY_ALPHABETICAL) {
            try {
                contactCursor = mResolver.query(myUri, CONTACTS_PROJECTION, selection,
                        null, Contacts.DISPLAY_NAME);
                if (contactCursor != null) {
                    contactCursor.moveToPosition(offset - 1);
                    contactId = contactCursor.getLong(CONTACTS_ID_COLUMN_INDEX);
                    if (V) Log.v(TAG, "Query startPointId = " + contactId);
                }
            } catch (CursorWindowAllocationException e) {
                Log.e(TAG, "CursorWindowAllocationException while composing phonebook one vcard order by alphabetical");
            } finally {
                if (contactCursor != null) {
                    contactCursor.close();
                    contactCursor = null;
                }
            }
        } else {
            Log.e(TAG, "Parameter orderByWhat is not supported!");
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }
        selection = Contacts._ID + "=" + contactId;

        if (V) Log.v(TAG, "Query selection is: " + selection);

        return composeAndSendVCards(op, selection, vcardType21, ownerVCard, true,
            ignorefilter, filter);
    }

    public final int composeAndSendVCards(Operation op, final String selection,
            final boolean vcardType21, String ownerVCard, boolean isContacts,
            boolean ignorefilter, byte[] filter) {
        long timestamp = 0;
        if (V) timestamp = System.currentTimeMillis();

        if (isContacts) {
            VCardComposer composer = null;
            VCardFilter vcardfilter= new VCardFilter(ignorefilter ? null : filter);

            HandlerForStringBuffer buffer = null;
            try {
                // Currently only support Generic Vcard 2.1 and 3.0
                int vcardType;
                if (vcardType21) {
                    vcardType = VCardConfig.VCARD_TYPE_V21_GENERIC;
                } else {
                    vcardType = VCardConfig.VCARD_TYPE_V30_GENERIC;
                }
                if (!vcardfilter.isPhotoEnabled()) {
                    vcardType |= VCardConfig.FLAG_REFRAIN_IMAGE_EXPORT;
                }

                //Enhancement: customize Vcard based on preferences/settings and input from caller
                composer = BluetoothPbapUtils.createFilteredVCardComposer(mContext, vcardType, filter);
                //End enhancement

                // BT does want PAUSE/WAIT conversion while it doesn't want the other formatting
                // done by vCard library by default.
                composer.setPhoneNumberTranslationCallback(
                        new VCardPhoneNumberTranslationCallback() {
                            public String onValueReceived(
                                    String rawValue, int type, String label, boolean isPrimary) {
                                // 'p' and 'w' are the standard characters for pause and wait
                                // (see RFC 3601)
                                // so use those when exporting phone numbers via vCard.
                                String numberWithControlSequence = rawValue
                                        .replace(PhoneNumberUtils.PAUSE, 'p')
                                        .replace(PhoneNumberUtils.WAIT, 'w');
                                return numberWithControlSequence;
                            }
                        });
                buffer = new HandlerForStringBuffer(op, ownerVCard);
                if (!composer.init(Contacts.CONTENT_URI, selection, null, Contacts._ID) ||
                        !buffer.onInit(mContext)) {
                    return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
                }

                while (!composer.isAfterLast()) {
                    if (BluetoothPbapObexServer.sIsAborted) {
                        ((ServerOperation)op).isAborted = true;
                        BluetoothPbapObexServer.sIsAborted = false;
                        break;
                    }
                    String vcard = composer.createOneEntry();
                    if (vcard == null) {
                        Log.e(TAG, "Failed to read a contact. Error reason: "
                                + composer.getErrorReason());
                        return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
                    }
                    if (V) Log.v (TAG , "vCard from composer: " + vcard);

                    vcard = vcardfilter.apply(vcard, vcardType21);
                    vcard = StripTelephoneNumber(vcard);

                    if (V) Log.v (TAG, "vCard after cleanup: " + vcard);

                    if (!buffer.onEntryCreated(vcard)) {
                        // onEntryCreate() already emits error.
                        return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
                    }
                }
            } finally {
                if (composer != null) {
                    composer.terminate();
                }
                if (buffer != null) {
                    buffer.onTerminate();
                }
            }
        } else { // CallLog
            BluetoothPbapCallLogComposer composer = null;
            HandlerForStringBuffer buffer = null;
            try {

                composer = new BluetoothPbapCallLogComposer(mContext);
                buffer = new HandlerForStringBuffer(op, ownerVCard);
                if (!composer.init(CallLog.Calls.CONTENT_URI, selection, null,
                                   CALLLOG_SORT_ORDER) ||
                                   !buffer.onInit(mContext)) {
                    return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
                }

                while (!composer.isAfterLast()) {
                    if (BluetoothPbapObexServer.sIsAborted) {
                        ((ServerOperation)op).isAborted = true;
                        BluetoothPbapObexServer.sIsAborted = false;
                        break;
                    }
                    String vcard = composer.createOneEntry(vcardType21);
                    if (vcard == null) {
                        Log.e(TAG, "Failed to read a contact. Error reason: "
                                + composer.getErrorReason());
                        return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
                    }
                    if (V) {
                        Log.v(TAG, "Vcard Entry:");
                        Log.v(TAG,vcard);
                    }

                    buffer.onEntryCreated(vcard);
                }
            } finally {
                if (composer != null) {
                    composer.terminate();
                }
                if (buffer != null) {
                    buffer.onTerminate();
                }
            }
        }

        if (V) Log.v(TAG, "Total vcard composing and sending out takes "
                    + (System.currentTimeMillis() - timestamp) + " ms");

        return ResponseCodes.OBEX_HTTP_OK;
    }

    public String StripTelephoneNumber (String vCard){
        String attr [] = vCard.split(System.getProperty("line.separator"));
        String Vcard = "";
            for (int i=0; i < attr.length; i++) {
                if(attr[i].startsWith("TEL")) {
                    attr[i] = attr[i].replace("(", "");
                    attr[i] = attr[i].replace(")", "");
                    attr[i] = attr[i].replace("-", "");
                    attr[i] = attr[i].replace(" ", "");
                }
            }

            for (int i=0; i < attr.length; i++) {
                if(!attr[i].equals("")){
                    Vcard = Vcard.concat(attr[i] + "\n");
                }
            }
        if (V) Log.v(TAG, "Vcard with stripped telephone no.: " + Vcard);
        return Vcard;
    }

    /**
     * Handler to emit vCards to PCE.
     */
    public class HandlerForStringBuffer {
        private Operation operation;

        private OutputStream outputStream;

        private String phoneOwnVCard = null;

        public HandlerForStringBuffer(Operation op, String ownerVCard) {
            operation = op;
            if (ownerVCard != null) {
                phoneOwnVCard = ownerVCard;
                if (V) Log.v(TAG, "phone own number vcard:");
                if (V) Log.v(TAG, phoneOwnVCard);
            }
        }

        private boolean write(String vCard) {
            try {
                if (vCard != null) {
                    outputStream.write(vCard.getBytes());
                    return true;
                }
            } catch (IOException e) {
                Log.e(TAG, "write outputstrem failed" + e.toString());
            }
            return false;
        }

        public boolean onInit(Context context) {
            try {
                outputStream = operation.openOutputStream();
                if (phoneOwnVCard != null) {
                    return write(phoneOwnVCard);
                }
                return true;
            } catch (IOException e) {
                Log.e(TAG, "open outputstrem failed" + e.toString());
            }
            return false;
        }

        public boolean onEntryCreated(String vcard) {
            return write(vcard);
        }

        public void onTerminate() {
            if (!BluetoothPbapObexServer.closeStream(outputStream, operation)) {
                if (V) Log.v(TAG, "CloseStream failed!");
            } else {
                if (V) Log.v(TAG, "CloseStream ok!");
            }
        }
    }

    public static class VCardFilter {
        private static enum FilterBit {
            //       bit  property    onlyCheckV21  excludeForV21
            FN (       1, "FN",       true,         false),
            PHOTO(     3, "PHOTO",    false,        false),
            BDAY(      4, "BDAY",     false,        false),
            ADR(       5, "ADR",      false,        false),
            EMAIL(     8, "EMAIL",    false,        false),
            TITLE(    12, "TITLE",    false,        false),
            ORG(      16, "ORG",      false,        false),
            NOTES(    17, "NOTES",    false,        false),
            URL(      20, "URL",      false,        false),
            NICKNAME( 23, "NICKNAME", false,        true);

            public final int pos;
            public final String prop;
            public final boolean onlyCheckV21;
            public final boolean excludeForV21;

            FilterBit(int pos, String prop, boolean onlyCheckV21, boolean excludeForV21) {
                this.pos = pos;
                this.prop = prop;
                this.onlyCheckV21 = onlyCheckV21;
                this.excludeForV21 = excludeForV21;
            }
        }

        private static final String SEPARATOR = System.getProperty("line.separator");
        private final byte[] filter;

        private boolean isFilteredOut(FilterBit bit, boolean vCardType21) {
            final int offset = (bit.pos / 8) + 1;
            final int bit_pos = bit.pos % 8;
            if (!vCardType21 && bit.onlyCheckV21) return false;
            if (vCardType21 && bit.excludeForV21) return true;
            if (filter == null || offset >= filter.length) return false;
            ///M:ALPS01944003 add log for filter id
            return ((filter[filter.length - offset] >> bit_pos) & 0x01) == 0;
            //return ((filter[filter.length - offset] >> bit_pos) & 0x01) != 0;
            //M/
        }

        VCardFilter(byte[] filter) {
            this.filter = filter;
        }

        public boolean isPhotoEnabled() {
            return !isFilteredOut(FilterBit.PHOTO, false);
        }

        public String apply(String vCard, boolean vCardType21){
            if (filter == null) return vCard;
            String lines[] = vCard.split(SEPARATOR);
            StringBuilder filteredVCard = new StringBuilder();
            boolean filteredOut = false;

            for (String line : lines) {
                // Check whether the current property is changing (ignoring multi-line properties)
                // and determine if the current property is filtered in.
                if (!Character.isWhitespace(line.charAt(0)) && !line.startsWith("=")) {
                    String currentProp = line.split("[;:]")[0];
                    filteredOut = false;

                    for (FilterBit bit : FilterBit.values()) {
                        if (bit.prop.equals(currentProp)) {
                            filteredOut = isFilteredOut(bit, vCardType21);
                            if (V) Log.v(TAG , "currentProp: " + currentProp + " filteredOut: " + filteredOut);
                            break;
                        }
                    }

                    // Since PBAP does not have filter bits for IM and SIP,
                    // exclude them by default. Easiest way is to exclude all
                    // X- fields....
                    if (currentProp.startsWith("X-")) filteredOut = true;
                }

                // Build filtered vCard
                if (!filteredOut) filteredVCard.append(line + SEPARATOR);
            }

            return filteredVCard.toString();
        }
    }
}
