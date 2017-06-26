package com.mediatek.email.backuprestore.common.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Date;

import org.apache.commons.io.IOUtils;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;

import com.mediatek.email.backuprestore.common.entity.EmailInfo;
import com.mediatek.email.backuprestore.common.entity.EmailInfo.Attachment;
import com.mediatek.email.backuprestore.common.entity.EmailInfoTag;

/**
 * The main class to construct the xml file and write it to the file
 */
public class XMLBuilder {

    private static final String TAG = "XMLBuilder";
    private Context mContext;

    private OutputStream mOutputStream;
    private Writer mWriter;

    private int mSequence = 0;
    private String mCurrentAccount;

    private String mTab = "    ";
    private int mTabCount = -1;

    public XMLBuilder(Context context) {
        mContext = context;
    }

    /**
     * Open the ouput stream to write the xml file, remember to call close when finishing writing.
     */
    public void open(File tempFile) throws IOException {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
            mOutputStream = new BufferedOutputStream(fileOutputStream);
            mWriter = new OutputStreamWriter(mOutputStream, "UTF-8");
        } catch (IOException e) {
            Log.e(TAG, "IOException occurred while open");
            throw e;
        }
    }

    /**
     * When finishing writing, close the stream
     */
    public void close() throws IOException {
        try {
            if (null != mWriter) {
                mWriter.flush();
            }
            if (null != mOutputStream) {
                mOutputStream.flush();
                mOutputStream.close();
            }
            if (null != mWriter) {
                mWriter.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException occurred while close the stream");
            throw e;
        }
    }

    /**
     * Write the xml header
     */
    public void writeHeader() throws IOException {
        String xmlHeader = "?xml version=\"1.0\" encoding=\"UTF-8\"";
        element(xmlHeader, true);
        element(EmailInfoTag.DATA_EXCHANGE_INFO, true);
        element(EmailInfoTag.RECORD_INFO, true);

        element(EmailInfoTag.VENDOR_INFO, "VendorUDX");
        element(EmailInfoTag.DEVICE_INFO, "DeviceUDX");
        element(EmailInfoTag.UDX_VERSION, "1.0");
        element(EmailInfoTag.USER_AGENT, "Android-Email");
        element(EmailInfoTag.USER_INFO, "Android-Email");
        element(EmailInfoTag.ENCODING, "UTF-8");
        // element(EmailInfoTag.FILE_SIZE, testString);
        String date = EmailInfoDataCollector.DATE_FORMAT
                .format(new Date(System.currentTimeMillis()));
        element(EmailInfoTag.DATE_HEADER, date);
        element(EmailInfoTag.LANGUAGE, "CHS");

        element(EmailInfoTag.RECORD_OF_EMAIL, true);
        element(EmailInfoTag.EMAIL_VERSION, "1.0");
        // element(EmailInfoTag.EMAIL_RECORD, "");
        // element(EmailInfoTag.EMAIL_LENGTH, "");
        element(EmailInfoTag.RECORD_OF_EMAIL, false);

        element(EmailInfoTag.RECORD_INFO, false);
        element(EmailInfoTag.DATA_EXCHANGE_INFO, false);
    }

    /**
     * The root element of the email backup file.
     * @param startTag whether to write the start tag
     */
    public void writeEmail(boolean startTag) throws IOException {
            if (startTag) {
                element(EmailInfoTag.EMAIL, true);
            } else {
                element(EmailInfoTag.EMAIL, false);
            }
    }

    /**
     * The basic root element of one message.
     * @param startTag whether to write the start tag
     */
    private void writeTopEmailInfoTag(boolean startTag) throws IOException {
        if (startTag) {
            element(EmailInfoTag.EMAILINFO, true);
            element(EmailInfoTag.SEQUENCE, String.valueOf(mSequence));
            element(EmailInfoTag.ACCOUNT, mCurrentAccount);
            element(EmailInfoTag.EMAILFIELD, true);
        } else {
            element(EmailInfoTag.EMAILFIELD, false);
            element(EmailInfoTag.EMAILINFO, false);
        }
    }

    /**
     * The basic element of one message.
     */
    private void writeBasicEmailInfo(EmailInfo info) throws IOException {
        element(EmailInfoTag.SUBJECT, info.mSubject);
        element(EmailInfoTag.DATE, info.mDate);
        element(EmailInfoTag.FROM, info.mFrom);
        element(EmailInfoTag.TO, info.mTo);
        element(EmailInfoTag.CC, info.mCc);
        element(EmailInfoTag.REPLY_TO, info.mReplyTo);
        element(EmailInfoTag.MESSAGE_ID, info.mMessageId);

        info.mMimeVersion = "1.0";
        element(EmailInfoTag.MIME_VERSION, info.mMimeVersion);
        if (info.mFlagAttachment) {
            info.mContentType = "multipart/mixed";
        } else {
            info.mContentType = "multipart/alternative";
        }
        element(EmailInfoTag.CONTENT_TYPE, info.mContentType);
    }

    /**
     * Write the body element of one EmailInfo
     */
    private void writeBody(EmailInfo info) throws IOException {
        String[] bodyText = EmailInfoDataCollector.getBodyHtmlText(mContext, info.mId);
        if (null == bodyText) {
            return;
        }
        if (!TextUtils.isEmpty(bodyText[0])) {
            element(EmailInfoTag.EMAILCONTENT, true);

            element(EmailInfoTag.CONTENT_TRANSFER_ENCODING, "base64");
            element(EmailInfoTag.CONTENT_TYPE, "text/plain");
            element(EmailInfoTag.CHARSET, "UTF-8");
            writeBody(EmailInfoTag.DATA, bodyText[0]);

            element(EmailInfoTag.EMAILCONTENT, false);
        }
        if (!TextUtils.isEmpty(bodyText[1])) {
            element(EmailInfoTag.EMAILCONTENT, true);

            element(EmailInfoTag.CONTENT_TRANSFER_ENCODING, "base64");
            element(EmailInfoTag.CONTENT_TYPE, "text/html");
            element(EmailInfoTag.CHARSET, "UTF-8");
            writeBody(EmailInfoTag.DATA, bodyText[1]);

            element(EmailInfoTag.EMAILCONTENT, false);
        }
    }

    /**
     * Write the body(textContent,htmlContent) of one EmailInfo
     */
    private void writeBody(String element, String value) throws IOException {
        try {
            mWriter.append('\n');
            tab(mTabCount);

            mWriter.append("<").append(element).append(">");
            mWriter.append('\n');

            byte[] content = value.getBytes("UTF-8");
            mWriter.flush();
            mOutputStream.write(Base64.encode(content, Base64.CRLF));
            tab(mTabCount);
            mWriter.append("</").append(element).append(">");
        } catch (IOException e) {
            Log.e(TAG, "Exception happen while writeBody Data: " + value);
            throw e;
        }
    }

    /**
     * Write attachments of a given EmailInfo
     */
    private void writeAttachments(EmailInfo info) throws IOException {
        if (!info.mFlagAttachment) {
            return;
        }
        Attachment[] attachments = EmailInfoDataCollector.collectAttachments(mContext, info.mId);
        if (null == attachments) {
            return;
        }

        for (Attachment attachment : attachments) {
            if (null == attachment.mContentUri) {
                continue;
            }
            element(EmailInfoTag.EMAILCONTENT, true);

            element(EmailInfoTag.CONTENT_TRANSFER_ENCODING, "base64");
            element(EmailInfoTag.CONTENT_DESCRIPTION, "attachment");
            element(EmailInfoTag.CONTENT_DISPOSITION, "attachment");
            element(EmailInfoTag.CONTENT_ID, attachment.mContentId);
            element(EmailInfoTag.CHARSET, "UTF-8");

            element(EmailInfoTag.CONTENT_TYPE, attachment.mMimeType);
            element(EmailInfoTag.NAME, attachment.mFileName);
            element(EmailInfoTag.FILENAME, attachment.mFileName);

            try {
                // Write attachment data
                InputStream inStream = null;
                Uri uri = Uri.parse(attachment.mContentUri);
                inStream = mContext.getContentResolver().openInputStream(uri);
                flush();
                // The out is null
                Base64OutputStream base64Out = new Base64OutputStream(mOutputStream, Base64.CRLF
                        | Base64.NO_CLOSE);
                mWriter.append('\n');
                tab(mTabCount);
                mWriter.append("<").append(EmailInfoTag.DATA).append(">");
                mWriter.append('\n');
                flush();
                IOUtils.copy(inStream, base64Out);
                base64Out.close();
                inStream.close();
                mOutputStream.flush();
                mWriter.append('\n');
                tab(mTabCount);
                mWriter.append("</").append(EmailInfoTag.DATA).append(">");

                element(EmailInfoTag.EMAILCONTENT, false);
            } catch (IOException e) {
                Log.e(TAG, "IOException occur while writeOneAttachment " + e);
                throw e;
            }
        }
    }

    /**
     * The account of current message to backup
     */
    public void setAccount(String account) {
        mCurrentAccount = account;
    }

    /**
     * The main log to write one account's inbox messages to the given xml file
     * @param emailInfo The emailInfo of ont account's inbox messages we need to write to xml file.
     * @param seq The emailInfo count has already write to xml file.
     * @throws IOException
     */
    public void writeTo(EmailInfo[] emailInfo, int seq) throws IOException {
        mSequence = seq;
        for (EmailInfo email : emailInfo) {
            try {
                mSequence++;
                writeTopEmailInfoTag(true);
                writeEmailInfoContent(email);
                writeTopEmailInfoTag(false);
                flush();
            } catch (IOException e) {
                Log.e(TAG, "IOException occur while writeTo, mSequence: " + mSequence);
                throw e;
            }
        }
    }

    /**
     * Write one EmailInfo to the xml file
     */
    private void writeEmailInfoContent(EmailInfo email) throws IOException {
        writeBasicEmailInfo(email);
        writeBody(email);
        writeAttachments(email);
    }

    private void flush() throws IOException {
        try {
            mOutputStream.flush();
            mWriter.flush();
        } catch (IOException e) {
            Log.e(TAG, "IOException occur while flush");
            throw e;
        }
    }

    /**
     * This method is used for element with some value
     * @param element
     * @param text
     * @throws IOException
     */
    private void element(String element, String value) throws IOException {
            if (TextUtils.isEmpty(value)) {
                return;
            }
            try {
                mWriter.append('\n');
                tab(mTabCount);

                mWriter.append("<").append(element).append(">");
                mWriter.append(value);
                mWriter.append("</").append(element).append(">");
            } catch (IOException e) {
                Log.e(TAG, "Exception happen while write element: " + element + " , value: " + value);
                throw e;
            }
    }

    /**
     * This method is used for element without value
     * @param element The element to write
     * @param startTag whether the element is the start tag
     * @throws IOException
     */
    private void element(String element, boolean startTag) throws IOException {
        try {
            if (mTabCount >= 0) {
                mWriter.append('\n');
                if (startTag) {
                    tab(mTabCount);
                    mTabCount++;
                } else {
                    if (-1 == mTabCount || mTabCount - 1 >= 0) {
                        mTabCount--;
                    }
                    tab(mTabCount);
                }
            } else {
                mTabCount++;
            }
            if (startTag) {
                mWriter.append("<").append(element).append(">");
            } else {
                mWriter.append("</").append(element).append(">");
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception happen while write element: " + element);
            throw e;
        }
    }

    /**
     * To tap the tab key for count times
     */
    private void tab(int count) throws IOException {
        try {
            while (count > 0) {
                mWriter.append(mTab);
                count--;
            }
        } catch (IOException e) {
            Log.e(TAG, "Write tab error");
            throw e;
        }
    }
}
