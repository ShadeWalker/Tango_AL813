package com.mediatek.email.backuprestore;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;

import android.content.Context;
import android.util.Log;

import com.mediatek.email.backuprestore.common.entity.EmailInfo;
import com.mediatek.email.backuprestore.common.entity.EmailInfo.Account;
import com.mediatek.email.backuprestore.common.util.EmailInfoDataCollector;
import com.mediatek.email.backuprestore.common.util.XMLBuilder;
/**
 * This class is mainly to Backup the Email message and store it
 * in the given position of the given name.
 */
public class EmailComposer {
    private static final String TAG = "EmailComposer";

    private Context mContext;
    private String mPath;
    private String mFileName;

    private int mTotalMessageCount = 0;
    private File mTempInternalFile = null;
    private File mExternalFile = null;

    private EmailInfoDataCollector mDataCollector;
    private XMLBuilder mBuilder;

    public EmailComposer(Context context, String path, String fileName) {
        mContext = context;
        mPath = path;
        mFileName = fileName;
        mDataCollector = new EmailInfoDataCollector(mContext);
    }

    /**
     * The whole logic in backup Email Messages.
     * @return True if success to backup Email Messages, otherwise false
     */
    public boolean startComposer() {
        Log.i(TAG, "Start Email compose......");
        Account[] accounts = mDataCollector.collectAccounts();
        if (null == accounts || 0 == accounts.length) {
            Log.i(TAG, "No account, report success but not make the file");
            return true;
        }

        mTotalMessageCount = 0;
        // Add some judgement to enable this in future
        boolean canUseInternalStorage = false;

        try {
            mBuilder = new XMLBuilder(mContext);

            if (!pathExist(mPath)) {
                Log.i(TAG, "Path do not exist and we can not make, report fail");
                return false;
            }
            if (canUseInternalStorage) {
                // First write to internal storage, then copy it to external storage
                File cacheDir = mContext.getCacheDir();
                mTempInternalFile = File.createTempFile("email", "udx", cacheDir);
                mBuilder.open(mTempInternalFile);
            } else {
                // Directly write to external storage
                mExternalFile = new File(mPath, mFileName);
                mBuilder.open(mExternalFile);
            }

            // Write the xml header and begin root element
            mBuilder.writeHeader();
            mBuilder.writeEmail(true);
            for (Account account : accounts) {
                EmailInfo[] emailInfoList = mDataCollector.collectMessagess(account.mAccountId);
                if ((null == emailInfoList) || (0 == emailInfoList.length)) {
                    continue;
                }
                mBuilder.setAccount(account.mEmailAddress);
                mBuilder.writeTo(emailInfoList, mTotalMessageCount);
                mTotalMessageCount += emailInfoList.length;
                Log.i(TAG, "Account and Messages: ["
                            + account.mEmailAddress + " , " + emailInfoList.length + "]");
            }
            // Write the xml file's end root element
            mBuilder.writeEmail(false);

            //Copy file from internal to external storage.
            if (canUseInternalStorage && 0 != mTotalMessageCount) {
                InputStream inStream = null;
                OutputStream outStream = null;
                try {
                    inStream = new BufferedInputStream(new FileInputStream(mTempInternalFile));
                    mExternalFile = new File(mPath, mFileName);
                    outStream = new BufferedOutputStream(new FileOutputStream(mExternalFile));
                    int copyLength = IOUtils.copy(inStream, outStream);
                    Log.i(TAG, "Copy File length: " + copyLength);
                } catch (IOException e) {
                    Log.e(TAG, "IOException while copy file from internal to external storage " + e);
                    throw e;
                } finally {
                    if (null != inStream) {
                        inStream.close();
                    }
                    if (null != outStream) {
                        outStream.close();
                    }
                }
            } else if (0 == mTotalMessageCount) {
                if (!mExternalFile.delete()) {
                    Log.i(TAG, "Failed to delete the file");
                } else {
                    Log.i(TAG, "Zero message is composed, delete the file and report success");
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to compose email " + e);
            if (null != mExternalFile) {
                if (!mExternalFile.delete()) {
                    Log.i(TAG, "Failed to delete the externalFile");
                } else {
                    Log.i(TAG, "Exception happen, delete the externalFile and report fail");
                }
            }
            return false;
        } finally {
            /**
             * Notice about delete the tempInternalFile and externalFile
             * 1.At any situation, tempInternalFile should be deleted after compose email
             * 2.externalFile should be delete when:
             *      a.We do not have account.
             *      b.We have account but do not have messages, 0 message.
             *      c.Exception happen when we backup messages
             */
            if (null != mTempInternalFile) {
                if (!mTempInternalFile.delete()) {
                    Log.i(TAG, "Failed to delete the tempInternalFile");
                } else {
                    Log.i(TAG, "Finally delete the tempInternalFile");
                }
            }
            if (null != mBuilder) {
                try {
                    mBuilder.close();
                } catch (IOException e) {
                    Log.e(TAG, "Builder.close() has error, but how could it happen, e= " + e);
                }
            }
        }
        Log.i(TAG, "End Email Compose, Total write messages count: " + mTotalMessageCount);
        return true;
    }

    private boolean pathExist(String path) {
        File file = new File(path);
        if (!file.exists()) {
            Log.i(TAG, "Path do not exist, makirs : " + path);
            return file.mkdirs();
        }
        return true;
    }

    public void releaseSource() {
        mContext = null;
        mPath = null;
        mFileName = null;
        mDataCollector = null;
        mBuilder = null;
        mTotalMessageCount = 0;
    }
}
