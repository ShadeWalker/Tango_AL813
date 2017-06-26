package com.mediatek.email.backuprestore.common.util;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.io.IOUtils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.android.emailcommon.provider.EmailContent.Body;
import com.android.mail.utils.HtmlSanitizer;
import com.mediatek.email.backuprestore.common.entity.EmailInfo;
import com.mediatek.email.backuprestore.common.entity.EmailInfo.Account;
import com.mediatek.email.backuprestore.common.entity.EmailInfo.Attachment;

public class EmailInfoDataCollector {

    private static final String TAG = "EmailInfoDataCollector";

    private Context mContext;

    // In MIME, en_US-like date format should be used. In other words "MMM" should be encoded to
    // "Jan", not the other localized format like "Ene" (meaning January in locale es).
    public static final SimpleDateFormat DATE_FORMAT =
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);

    private static String EMAIL_PACKAGE_NAME = "com.android.email";
    private static String AUTHORITY = EMAIL_PACKAGE_NAME + ".provider";
    private static Uri BASE_CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    private static Uri MAILBOX_CONTENT_URI = Uri.parse(BASE_CONTENT_URI + "/mailbox");
    private static Uri MESSAGE_CONTENT_URI = Uri.parse(BASE_CONTENT_URI + "/message");
    private static Uri ATTACHMENT_CONTENT_URI = Uri.parse(BASE_CONTENT_URI + "/attachment");
    private static Uri ACCOUNT_CONTENT_URI = Uri.parse(BASE_CONTENT_URI + "/account");
    private static Uri BODY_CONTENT_URI = Uri.parse(BASE_CONTENT_URI + "/body");

    /** The "main" mailbox for the account, almost always referred to as "Inbox" */
    private static final int TYPE_INBOX = 0;
    private static final String TYPE = "type";
    private static final String ACCOUNT_KEY = "accountKey";
    private static final String MAILBOX_KEY = "mailboxKey";
    private static final String MESSAGE_KEY = "messageKey";

    //Account column
    private static final int ACCOUNT_ID_COLUMN = 0;
    private static final int ACCOUNT_EMAILADDRESS_COLUMN = 1;

    //Message column
    private static final int MESSAGE_SUBJECT_COLUMN = 0;
    private static final int MESSAGE_TIMESTAMP_COLUMN = 1;
    private static final int MESSAGE_FROMLIST_COLUMN = 2;
    private static final int MESSAGE_TOLIST_COLUMN = 3;
    private static final int MESSAGE_CCLIST_COLUMN = 4;
    private static final int MESSAGE_ID_COLUMN = 5;
    private static final int MESSAGE_FLAGATTACHMENT_COLUMN = 6;
    private static final int MESSAGE_REPLY_TO_LIST_COLUMN = 7;
    private static final int MESSAGE_MESSAGE_ID_COLUMN = 8;

    //Body column
    private static final int BODY_TEXT_CONTENT_COLUMN = 0;
    private static final int BODY_HTML_CONTENT_COLUMN = 1;

    //Attachment column
    private static final int ATTACHMENT_FILENAME_COLUMN = 0;
    private static final int ATTACHMENT_MIMETYPE_COLUMN = 1;
    private static final int ATTACHMENT_CONTENTURI_COLUMN = 2;
    private static final int ATTACHMENT_CONTENT_ID_COLUMN = 3;

    private static final String[] ACCOUNT_CONTENT_PROJECTION = new String[] {
        "_id", "emailAddress"
    };

    private static final String[] MAILBOX_ID_PROJECTION = new String[] {
        "_id"
    };

    private static final String[] MESSAGE_CONTENT_PROJECTION = new String[] {
        "subject", "timeStamp", "fromList", "toList", "ccList", "_id", "flagAttachment",
        "replyToList", "messageId"
    };

    private static final String[] BODY_CONTENT_PROJECTION = new String[] {
        "textContent", "htmlContent"
    };

    private static final String[] ATTACHMENT_CONTENT_PROJECTION = new String[] {
        "fileName", "mimeType", "contentUri", "contentId"
    };

    public EmailInfoDataCollector(Context context) {
        mContext = context;
    }

    /**
     * Collect all the accounts login in the Email app.
     */
    public Account[] collectAccounts() {
        // Only to get accountId and accountName
        ContentResolver resolver = mContext.getContentResolver();
        Account[] accounts = null;
        Cursor c = null;
        try {
            c = resolver.query(ACCOUNT_CONTENT_URI,
                               ACCOUNT_CONTENT_PROJECTION, null, null, null);
            if (null == c || (0 == c.getCount())) {
                Log.i(TAG, "Cursor is null or no account find");
                return null;
            }
            accounts = new Account[c.getCount()];
            int num = 0;
            while (c.moveToNext()) {
                accounts[num] = new Account();
                accounts[num].mAccountId = c.getInt(ACCOUNT_ID_COLUMN);
                accounts[num].mEmailAddress = c.getString(ACCOUNT_EMAILADDRESS_COLUMN);
                num++;
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception occurred while query account " + e);
        } finally {
            if (null != c) {
                c.close();
            }
        }
        return accounts;
    }

    /**
     * Collect all the inbox Messages in the given account.
     */
    public  EmailInfo[] collectMessagess(int accountId) {
        ContentResolver resolver = mContext.getContentResolver();
        EmailInfo[] emailInfo = null;
        Cursor c = null;
        try {
            int inboxId = getInboxIdOfAccount(accountId);
            if (-1 == inboxId) {
                Log.i(TAG, "Do not find the inbox");
                return null;
            }
            c = resolver.query(MESSAGE_CONTENT_URI,
                               MESSAGE_CONTENT_PROJECTION,
                               ACCOUNT_KEY + " = ? AND " + MAILBOX_KEY + " = ? ",
                               new String[]{String.valueOf(accountId), String.valueOf(inboxId)},
                               null);
            if (null == c || (0 == c.getCount())) {
                Log.i(TAG, "Cursor is null or account without messages");
                return null;
            }
            emailInfo = restoreEmailInfo(c);
        } catch (Exception e) {
            Log.e(TAG, "Exception occurred while query Messages " + e);
        } finally {
            if (null != c) {
                c.close();
            }
        }
        return emailInfo;
    }

    /**
     * Get the inbox id of the given account.
     */
    private int getInboxIdOfAccount(int accountId) {
        ContentResolver resolver = mContext.getContentResolver();
        Cursor c = null;
        int inboxId = -1;
        try {
            c = resolver.query(MAILBOX_CONTENT_URI,
                              MAILBOX_ID_PROJECTION,
                              ACCOUNT_KEY + " = ? AND " + TYPE + " = ?",
                              new String[]{String.valueOf(accountId), String.valueOf(TYPE_INBOX)},
                              null);
            if (null == c || (0 == c.getCount())) {
                Log.i(TAG, "No inbox mailbox find");
                return inboxId;
            }
            while (c.moveToNext()) {
                inboxId = c.getInt(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception occurred while query mailbox : " + e);
        } finally {
            if (null != c) {
                c.close();
            }
        }
        return inboxId;
    }

    /**
     * Collect all the attachments of the given message.
     */
    public static Attachment[] collectAttachments(Context context, long messageId) {
        ContentResolver resolver = context.getContentResolver();
        Attachment[] attachments = null;
        Cursor c = null;
        try {
            c = resolver.query(ATTACHMENT_CONTENT_URI,
                               ATTACHMENT_CONTENT_PROJECTION,
                               MESSAGE_KEY + " = ? ",
                               new String[]{String.valueOf(messageId)}, null);
            if (null == c || c.getCount() == 0) {
                Log.i(TAG, "Message without attachment, MessageId= " + messageId);
                return null;
            }
            attachments = new Attachment[c.getCount()];
            int num = 0;
            while (c.moveToNext()) {
                attachments[num] = new Attachment();
                attachments[num].mFileName = c.getString(ATTACHMENT_FILENAME_COLUMN);
                attachments[num].mMimeType = c.getString(ATTACHMENT_MIMETYPE_COLUMN);
                attachments[num].mContentUri = c.getString(ATTACHMENT_CONTENTURI_COLUMN);
                attachments[num].mContentId = c.getString(ATTACHMENT_CONTENT_ID_COLUMN);
                num++;
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception occurred while query Attachment " + e);
        } finally {
            if (null != c) {
                c.close();
            }
        }
        return attachments;
    }

    /**
     * Get the body(textContent,htmlContent) of the given message.
     */
    public static String[] getBodyHtmlText(Context context, long messageId) {
        ContentResolver resolver = context.getContentResolver();
        String[] info = null;

        try {

            // get body text
            final Uri textUri = Body.getBodyTextUriForMessageWithId(messageId);
            final InputStream txtIn = resolver.openInputStream(textUri);
            final String underlyingTextString;
            try {
                underlyingTextString = IOUtils.toString(txtIn);
            } finally {
                txtIn.close();
            }

            // get body HTML
            final Uri htmlUri = Body.getBodyHtmlUriForMessageWithId(messageId);
            final InputStream htmlIn = resolver.openInputStream(htmlUri);
            final String underlyingHtmlString;
            try {
                underlyingHtmlString = IOUtils.toString(htmlIn);
            } finally {
                htmlIn.close();
            }
            final String sanitizedHtml = HtmlSanitizer.sanitizeHtml(underlyingHtmlString);

            info = new String[2];
            info[BODY_TEXT_CONTENT_COLUMN] = underlyingTextString;
            info[BODY_HTML_CONTENT_COLUMN] = sanitizedHtml;

        } catch (Exception e) {
            Log.e(TAG, "Exception occurred while query Body Html and Text " + e);
        }

        return info;
    }

    private EmailInfo[] restoreEmailInfo(Cursor cursor) {
        EmailInfo[] emailInfo = new EmailInfo[cursor.getCount()];
        int num = 0;
        while (cursor.moveToNext()) {
            emailInfo[num] = new EmailInfo();
            emailInfo[num].mSubject = cursor.getString(MESSAGE_SUBJECT_COLUMN);
            emailInfo[num].mFrom = cursor.getString(MESSAGE_FROMLIST_COLUMN);
            emailInfo[num].mTo = cursor.getString(MESSAGE_TOLIST_COLUMN);
            emailInfo[num].mCc = cursor.getString(MESSAGE_CCLIST_COLUMN);

            long date = cursor.getLong(MESSAGE_TIMESTAMP_COLUMN);
            emailInfo[num].mDate = DATE_FORMAT.format(new Date(date));

            //Our messageId in the db
            emailInfo[num].mId = cursor.getInt(MESSAGE_ID_COLUMN);
            // If the flagattachment is 1, mean have attachment
            emailInfo[num].mFlagAttachment = (cursor.getInt(MESSAGE_FLAGATTACHMENT_COLUMN) == 1);
            emailInfo[num].mReplyTo = cursor.getString(MESSAGE_REPLY_TO_LIST_COLUMN);
            emailInfo[num].mMessageId = cursor.getString(MESSAGE_MESSAGE_ID_COLUMN);
            num++;
        }
        return emailInfo;
    }
}
