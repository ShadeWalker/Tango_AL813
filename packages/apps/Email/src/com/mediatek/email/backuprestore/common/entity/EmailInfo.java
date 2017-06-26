package com.mediatek.email.backuprestore.common.entity;

public class EmailInfo {

    public int mSequence;
    public String mAccount;

    public String mDate;
    public String mFrom;
    public String mSubject;
    public String mTo;
    public String mReplyTo;
    public String mMimeVersion;
    public String mCc;
    public String mMessageId;

    public String mContentId;
    public String mContentType;
    public String mCharset;
    public String mData;
    public String mName;
    public String mFilename;

    public String mContentTransferEncoding;;
    public String mContentDescription;
    public String mContentDisposition;

    public String mReturnPath;
    public String mDeliverTo;
    public String mReceived;
    public String mBcc;
    public String mContentClass;

    // For get message body and attahcment
    public int mId;
    //Mark if the message has attahcment.
    public boolean mFlagAttachment = false;
    public static class Attachment {
        public String mFileName;
        public String mMimeType;
        public String mContentUri;
        public String mContentId;
    }

    public static class Account {
        public int mAccountId;
        public String mEmailAddress;
    }
}
