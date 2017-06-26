package com.mediatek.email.backuprestore.common.entity;
/**
 * Email TAG used in build Email Message backup udx file.
 * The structure is like below:

    <Email>
        <EmailInfo>
            <Sequence>1</Sequence>
            <EmailField>
                <CC>Alice@sample.com</CC>
                <Data>Dec 4 Jun 2013 18:50:00</Data>
                <From>Alice@sample.com</From>
                <MIME_version>1.0</MIME_version>
                <Subject>Test Local Email Backup</Subject>
                <To>Alice@sample.com</To>
                <Content_type>multipart/mixed</Content_type>
                <!-- -->
                <EmailContent>
                    <Content_Transfer_Encoding>base64</Content_Transfer_Encoding>
                    <Content_type>text/plain</Content_type>
                    <Charset>gb2312</Charset>
                    <Data>
                        Some text data encoded.
                    </Data>
                </EmailContent>
                <!-- -->
                <EmailContent>
                    <Content_Transfer_Encoding>base64</Content_Transfer_Encoding>
                    <Content_type>text/html</Content_type>
                    <Charset>gb2312</Charset>
                    <Data>
                        Some html data encoded.
                    </Data>
                </EmailContent>
                <!-- -->
                <EmailContent>
                    <Content_Transfer_Encoding>7bit</Content_Transfer_Encoding>
                    <Content_Disposition>attachment</Content_Disposition>
                    <Content_type>text/plain</Content_type>
                    <Data>
                        Attachment content.
                    </Data>
                    <Name>attachment.txt</Name>
                    <Filename>attachment.txt</Filename>
                </EmailContent>
            </EmailField>
        </EmailInfo>
    </Email>

 */
public class EmailInfoTag {
    public static final String EMAIL = "Email";
    public static final String EMAILINFO = "EmailInfo";
    public static final String EMAILFIELD = "EmailField";
    public static final String SEQUENCE = "Sequence";
    //Special the account
    public static final String ACCOUNT = "Account";

    public static final String DATE = "Date";
    public static final String SUBJECT = "Subject";
    public static final String FROM = "From";
    public static final String TO = "To";
    public static final String CC = "CC";
    public static final String REPLY_TO = "Reply-to";
    public static final String MIME_VERSION = "MIME-version";
    public static final String MESSAGE_ID = "Message-ID";

    //Element not include, use for body and attachment's root
    public static final String EMAILCONTENT = "EmailContent";

    public static final String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
    public static final String CONTENT_DESCRIPTION = "Content-Description";
    public static final String CONTENT_DISPOSITION = "Content-Disposition";
    public static final String CONTENT_ID = "Content-ID";
    public static final String CONTENT_TYPE = "Content-type";
    public static final String CHARSET = "Charset";
    public static final String DATA = "Data";
    public static final String NAME = "Name";
    public static final String FILENAME = "FileName";

    //Current we do not use the follow five tag, for future use
    public static final String RETURN_PATH = "Return-Path";
    public static final String DELIVERED_TO = "Delivered-To";
    public static final String RECEIVED = "Received";
    public static final String BCC = "BCC";
    public static final String CONTENT_CLASS = "Content-class";

    //Head info
    public static final String DATA_EXCHANGE_INFO = "DataExchangeInfo";
    public static final String RECORD_INFO = "RecordInfo";

    public static final String VENDOR_INFO = "VendorInfo";
    public static final String DEVICE_INFO = "DeviceInfo";
    public static final String UDX_VERSION = "UdxVersion";
    public static final String USER_AGENT = "UserAgent";
    public static final String USER_INFO = "UserInfo";
    public static final String ENCODING = "Encoding";
    public static final String FILE_SIZE = "FileSize";
    public static final String DATE_HEADER = "Date";
    public static final String LANGUAGE = "Language";
    public static final String RECORD_OF_EMAIL = "RecordOfEmail";

    public static final String EMAIL_VERSION = "EmailVersion";
    public static final String EMAIL_RECORD = "EmailRecord";
    public static final String EMAIL_LENGTH = "EmailLength";
}
