package com.mediatek.contacts.list;
/** define contact request action code */
public class ContactsRequestAction {
    /** Show all contacts and pick all of checked items. */
    public static final int ACTION_PICK_MULTIPLE_CONTACTS = 300;

    /** Show all contacts and delete all of checked items. */
    public static final int ACTION_DELETE_MULTIPLE_CONTACTS = 301;

    /** Show all contacts in one group and move all of checked items. */
    public static final int ACTION_GROUP_MOVE_MULTIPLE_CONTACTS = 302;

    /** Show all contacts and share all of checked items. */
    public static final int ACTION_SHARE_MULTIPLE_CONTACTS = 303;

    /** Show all contacts in multiple choice view and add all of checked items. */
    public static final int ACTION_GROUP_ADD_MULTIPLE_CONTACTS = 304;

    /** Show all phone numbers and pick all of checked items. */
    public static final int ACTION_PICK_MULTIPLE_PHONES = 305;

    /** Show all phone numbers & emails and pick all of checked items. */
    public static final int ACTION_PICK_MULTIPLE_PHONEANDEMAILS = 306;

    /** Show all the specified mimetypes datas and pick all of checked items. */
    public static final int ACTION_PICK_MULTIPLE_DATAS = 307;

    /** MMS add contacts. choose a dropdown list group item. */
    public static final int ACTION_MMS_ADD_GROUP_CONTACTS = 308;

    /** Show all email addresses and pick all of checked items. */
    public static final int ACTION_PICK_MULTIPLE_EMAILS = 309;

    /** Show all phone number, Internal call, Ims call adn pick all of checked items*/
    public static final int ACTION_PICK_MULTIPLE_PHONE_IMS_SIP_CALLS = 310;
}