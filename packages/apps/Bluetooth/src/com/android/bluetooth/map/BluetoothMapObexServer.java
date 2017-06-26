/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
* Copyright (C) 2014 Samsung System LSI
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
package com.android.bluetooth.map;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import com.android.bluetooth.mapapi.BluetoothMapContract;

//import android.telephony.SubInfoRecord;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.util.Log;

import com.android.bluetooth.map.BluetoothMapUtils;
import com.android.bluetooth.map.BluetoothMapUtils.TYPE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import javax.obex.HeaderSet;
import javax.obex.Operation;
import javax.obex.ResponseCodes;
import javax.obex.ServerRequestHandler;

import com.android.bluetooth.map.BluetoothMapbMessage.vCard;
import com.android.bluetooth.R;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Data;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class BluetoothMapObexServer extends ServerRequestHandler {

    private static final String TAG = "[MAP]BluetoothMapObexServer";

    private static final boolean D = BluetoothMapService.DEBUG;
    private static final boolean V = BluetoothMapService.VERBOSE;

    private static final int UUID_LENGTH = 16;

    private static final long PROVIDER_ANR_TIMEOUT = 20 * DateUtils.SECOND_IN_MILLIS;

    /* OBEX header and value used to detect clients that support threadId in the message listing. */
    private static final int THREADED_MAIL_HEADER_ID = 0xFA;
    private static final long THREAD_MAIL_KEY = 0x534c5349;

    // 128 bit UUID for MAP
    private static final byte[] MAP_TARGET = new byte[] {
             (byte)0xBB, (byte)0x58, (byte)0x2B, (byte)0x40,
             (byte)0x42, (byte)0x0C, (byte)0x11, (byte)0xDB,
             (byte)0xB0, (byte)0xDE, (byte)0x08, (byte)0x00,
             (byte)0x20, (byte)0x0C, (byte)0x9A, (byte)0x66
             };

    /* Message types */
    private static final String TYPE_GET_FOLDER_LISTING  = "x-obex/folder-listing";
    private static final String TYPE_GET_MESSAGE_LISTING = "x-bt/MAP-msg-listing";
    private static final String TYPE_MESSAGE             = "x-bt/message";
    private static final String TYPE_SET_MESSAGE_STATUS  = "x-bt/messageStatus";
    private static final String TYPE_SET_NOTIFICATION_REGISTRATION = "x-bt/MAP-NotificationRegistration";
    private static final String TYPE_MESSAGE_UPDATE      = "x-bt/MAP-messageUpdate";

    private BluetoothMapFolderElement mCurrentFolder;

    private BluetoothMapContentObserver mObserver = null;

    private Handler mCallback = null;

    private Context mContext;

    private boolean mIsAborted = false;

    BluetoothMapContent mOutContent;

    private String mBaseEmailUriString = null;
    private long mAccountId = 0;
    private BluetoothMapEmailSettingsItem mAccount = null;
    private Uri mEmailFolderUri = null;

    private BluetoothMapSimManager mSimManager;


    private int mMasId = 0;

    private boolean mEnableSmsMms = false;
    private boolean mThreadIdSupport = false; // true if peer supports threadId in msg listing
    private String mAuthority;
    private ContentResolver mResolver;
    private ContentProviderClient mProviderClient = null;

    public BluetoothMapObexServer(Handler callback,
                                  Context context,
                                  BluetoothMapContentObserver observer,
                                  int masId,
                                  BluetoothMapEmailSettingsItem account,
                                  boolean enableSmsMms) throws RemoteException {
        super();
        mCallback = callback;
        mContext = context;
        mObserver = observer;
        mEnableSmsMms = enableSmsMms;
        mAccount = account;
        mMasId = masId;

        if(account != null && account.getProviderAuthority() != null) {
            mAccountId = account.getAccountId();
            mAuthority = account.getProviderAuthority();
            mResolver = mContext.getContentResolver();
            if (D) Log.d(TAG, "BluetoothMapObexServer(): accountId=" + mAccountId);
            mBaseEmailUriString = account.mBase_uri + "/";
            if (D) Log.d(TAG, "BluetoothMapObexServer(): emailBaseUri=" + mBaseEmailUriString);
            mEmailFolderUri = BluetoothMapContract.buildFolderUri(mAuthority,
                                                                  Long.toString(mAccountId));
            if (D) Log.d(TAG, "BluetoothMapObexServer(): mEmailFolderUri=" + mEmailFolderUri);
            mProviderClient = acquireUnstableContentProviderOrThrow();
        }

        buildFolderStructure(); /* Build the default folder structure, and set
                                   mCurrentFolder to root folder */
        mObserver.setFolderStructure(mCurrentFolder.getRoot());

        mOutContent = new BluetoothMapContent(mContext, mBaseEmailUriString);

    }

    /**
     *
     */
    private ContentProviderClient acquireUnstableContentProviderOrThrow() throws RemoteException{
        ContentProviderClient providerClient = mResolver.acquireUnstableContentProviderClient(mAuthority);
        if (providerClient == null) {
            throw new RemoteException("Failed to acquire provider for " + mAuthority);
        }
        providerClient.setDetectNotResponding(PROVIDER_ANR_TIMEOUT);
        return providerClient;
    }

    /**
     * Build the default minimal folder structure, as defined in the MAP specification.
     */
    private void buildFolderStructure() throws RemoteException{
        mCurrentFolder = new BluetoothMapFolderElement("root", null); // This will be the root element
        BluetoothMapFolderElement tmpFolder;
        tmpFolder = mCurrentFolder.addFolder("telecom"); // root/telecom
        tmpFolder = tmpFolder.addFolder("msg");          // root/telecom/msg

        addBaseFolders(tmpFolder); // Add the mandatory folders

        if(mEnableSmsMms) {
            addSmsMmsFolders(tmpFolder);
        }
        if(mEmailFolderUri != null) {
            if (D) Log.d(TAG, "buildFolderStructure(): " + mEmailFolderUri.toString());
            addEmailFolders(tmpFolder);
        }
    }

    /**
     * Add
     * @param root
     */
    private void addBaseFolders(BluetoothMapFolderElement root) {
        root.addFolder(BluetoothMapContract.FOLDER_NAME_INBOX);                    // root/telecom/msg/inbox
        root.addFolder(BluetoothMapContract.FOLDER_NAME_OUTBOX);
        root.addFolder(BluetoothMapContract.FOLDER_NAME_SENT);
        root.addFolder(BluetoothMapContract.FOLDER_NAME_DELETED);
    }


    /**
     * Add
     * @param root
     */
    private void addSmsMmsFolders(BluetoothMapFolderElement root) {
        root.addSmsMmsFolder(BluetoothMapContract.FOLDER_NAME_INBOX);                    // root/telecom/msg/inbox
        root.addSmsMmsFolder(BluetoothMapContract.FOLDER_NAME_OUTBOX);
        root.addSmsMmsFolder(BluetoothMapContract.FOLDER_NAME_SENT);
        root.addSmsMmsFolder(BluetoothMapContract.FOLDER_NAME_DELETED);
        root.addSmsMmsFolder(BluetoothMapContract.FOLDER_NAME_DRAFT);
    }


    /**
     * Recursively adds folders based on the folders in the email content provider.
     *       Add a content observer? - to refresh the folder list if any change occurs.
     *       Consider simply deleting the entire table, and then rebuild using buildFolderStructure()
     *       WARNING: there is no way to notify the client about these changes - hence
     *       we need to either keep the folder structure constant, disconnect or fail anything
     *       referring to currentFolder.
     *       It is unclear what to set as current folder to be able to go one level up...
     *       The best solution would be to keep the folder structure constant during a connection.
     * @param folder the parent folder to which subFolders needs to be added. The
     *        folder.getEmailFolderId() will be used to query sub-folders.
     *        Use a parentFolder with id -1 to get all folders from root.
     */
    private void addEmailFolders(BluetoothMapFolderElement parentFolder) throws RemoteException {
        // Select all parent folders
        BluetoothMapFolderElement newFolder;

        String where = BluetoothMapContract.FolderColumns.PARENT_FOLDER_ID +
                        " = " + parentFolder.getEmailFolderId();
        Cursor c = mProviderClient.query(mEmailFolderUri,
                        BluetoothMapContract.BT_FOLDER_PROJECTION, where, null, null);
        try {
            while (c != null && c.moveToNext()) {
                String name = c.getString(c.getColumnIndex(BluetoothMapContract.FolderColumns.NAME));
                long id = c.getLong(c.getColumnIndex(BluetoothMapContract.FolderColumns._ID));
                newFolder = parentFolder.addEmailFolder(name, id);
                addEmailFolders(newFolder); // Use recursion to add any sub folders
            }
        } finally {
            if (c != null) c.close();
        }
    }

    @Override
    public int onConnect(final HeaderSet request, HeaderSet reply) {
        if (D) Log.d(TAG, "[onConnect]: begin");
        /// M: Map Gemini Feature @{
        mSimManager = new BluetoothMapSimManager();
        mSimManager.init(mContext);
        /// @}
        if (V) logHeader(request);
        mThreadIdSupport = false; // Always assume not supported at new connect.
        notifyUpdateWakeLock();
        Long threadedMailKey = null;
        try {
            byte[] uuid = (byte[])request.getHeader(HeaderSet.TARGET);
            threadedMailKey = (Long)request.getHeader(THREADED_MAIL_HEADER_ID);
            if (uuid == null) {
                return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
            }
            if (D) Log.d(TAG, "[onConnect]: uuid=" + Arrays.toString(uuid));

            if (uuid.length != UUID_LENGTH) {
                Log.w(TAG, "[onConnect] Wrong UUID length");
                return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
            }
            for (int i = 0; i < UUID_LENGTH; i++) {
                if (uuid[i] != MAP_TARGET[i]) {
                    Log.w(TAG, "[onConnect] Wrong UUID");
                    return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
                }
            }
            reply.setHeader(HeaderSet.WHO, uuid);
        } catch (IOException e) {
            Log.e(TAG, "[onConnect] Exception during onConnect: " + e.toString());
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }

        try {
            byte[] remote = (byte[])request.getHeader(HeaderSet.WHO);
            if (remote != null) {
                if (D) Log.d(TAG, "[onConnect]: remote=" + Arrays.toString(remote));
                reply.setHeader(HeaderSet.TARGET, remote);
            }
            if(threadedMailKey != null && threadedMailKey.longValue() == THREAD_MAIL_KEY)
            {
                /* If the client provides the correct key we enable threaded e-mail support
                 * and reply to the client that we support the requested feature.
                 * This is currently an Android only feature. */
                mThreadIdSupport = true;
                reply.setHeader(THREADED_MAIL_HEADER_ID, THREAD_MAIL_KEY);
            }
        } catch (IOException e) {
            Log.e(TAG,"Exception during onConnect:", e);
            mThreadIdSupport = false;
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }

        if (V) Log.v(TAG, "[onConnect]: uuid is ok, will send out " +
                "MSG_SESSION_ESTABLISHED msg.");

        if(mCallback != null) {
            Message msg = Message.obtain(mCallback);
            msg.what = BluetoothMapService.MSG_SESSION_ESTABLISHED;
            msg.sendToTarget();
        }

        return ResponseCodes.OBEX_HTTP_OK;
    }

    @Override
    public void onDisconnect(final HeaderSet req, final HeaderSet resp) {
        if (D) Log.d(TAG, "[onDisconnect]: enter");
        mSimManager.unregisterReceiver();
        if (V) logHeader(req);
        notifyUpdateWakeLock();
        resp.responseCode = ResponseCodes.OBEX_HTTP_OK;
        if (mCallback != null) {
            Message msg = Message.obtain(mCallback);
            msg.what = BluetoothMapService.MSG_SESSION_DISCONNECTED;
            msg.sendToTarget();
            if (V) Log.v(TAG, "[onDisconnect]: msg MSG_SESSION_DISCONNECTED sent out.");
        }
    }

    @Override
    public int onAbort(HeaderSet request, HeaderSet reply) {
        if (D) Log.d(TAG, "[onAbort]: enter.");
        notifyUpdateWakeLock();
        mIsAborted = true;
        return ResponseCodes.OBEX_HTTP_OK;
    }

    @Override
    public int onPut(final Operation op) {
        if (D) Log.d(TAG, "onPut(): enter");
        mIsAborted = false;
        notifyUpdateWakeLock();
        HeaderSet request = null;
        String type, name;
        byte[] appParamRaw;
        BluetoothMapAppParams appParams = null;

        try {
            request = op.getReceivedHeader();
            type = (String)request.getHeader(HeaderSet.TYPE);

            name = (String)request.getHeader(HeaderSet.NAME);
            appParamRaw = (byte[])request.getHeader(HeaderSet.APPLICATION_PARAMETER);
            if(appParamRaw != null)
                appParams = new BluetoothMapAppParams(appParamRaw);
            if(D) Log.d(TAG,"type = " + type + ", name = " + name);
            if (type.equals(TYPE_MESSAGE_UPDATE)) {
                if(V) {
                    Log.d(TAG,"TYPE_MESSAGE_UPDATE:");
                }
                return updateInbox();
            }else if(type.equals(TYPE_SET_NOTIFICATION_REGISTRATION)) {
                if(V) {
                    Log.d(TAG,"TYPE_SET_NOTIFICATION_REGISTRATION: NotificationStatus: "
                            + appParams.getNotificationStatus());
                }
                return mObserver.setNotificationRegistration(appParams.getNotificationStatus());
            }else if(type.equals(TYPE_SET_MESSAGE_STATUS)) {
                if(V) {
                    Log.d(TAG,"TYPE_SET_MESSAGE_STATUS: StatusIndicator: "
                            + appParams.getStatusIndicator()
                            + ", StatusValue: " + appParams.getStatusValue());
                }
                return setMessageStatus(name, appParams);
            } else if (type.equals(TYPE_MESSAGE)) {
                if(V) {
                    Log.d(TAG,"TYPE_MESSAGE: Transparet: " + appParams.getTransparent()
                            + ", retry: " + appParams.getRetry()
                            + ", charset: " + appParams.getCharset());
                }
                /// M: Map Gemini Feature @{
                long subId;
                if (mSimManager.getSubCount() == 0) {
                    subId = BluetoothMapSimManager.INVALID_SUBID;
                } else if (mSimManager.getSubCount() == 1) {
                    subId = mSimManager.getSingleSubId();
                } else {
                    //long messageSubId = SubscriptionManager.getDefaultSmsSubId();
                	int messageSubId = SubscriptionManager.getDefaultSmsSubId();
                    Log.d(TAG, "[onPut] Settings messageSubId = " + messageSubId);
//                    if (messageSubId == SubscriptionManager.ASK_USER_SUB_ID
//                            || messageSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    if (messageSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        // always ask, show SUB selection dialog
                        return pushMessage(op, name, appParams, false);
                    } else if (messageSubId == Settings.System.SMS_SIM_SETTING_AUTO) {
                        return pushMessage(op, name, appParams, true);
                    } else {
                        subId = messageSubId;
                    }
                }
                /// @}
                Log.d(TAG, "[onPut] pushMessageGemini subId = " + subId);
                return pushMessageGemini(op, name, appParams, subId);
            }
        } catch (RemoteException e){
            //reload the providerClient and return error
            try {
                mProviderClient = acquireUnstableContentProviderOrThrow();
            }catch (RemoteException e2){
                //should not happen
            }
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }catch (Exception e) {

            if(D) {
                Log.e(TAG, "Exception occured while handling request",e);
            } else {
                Log.e(TAG, "Exception occured while handling request");
            }
            if(mIsAborted) {
                return ResponseCodes.OBEX_HTTP_OK;
            } else {
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        }
        return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
    }

    private int updateInbox() throws RemoteException{
        if (mAccount != null) {
            BluetoothMapFolderElement inboxFolder = mCurrentFolder.getEmailFolderByName(
                    BluetoothMapContract.FOLDER_NAME_INBOX);
            if (inboxFolder != null) {
                long accountId = mAccountId;
                if (D) Log.d(TAG,"updateInbox inbox=" + inboxFolder.getName() + "id="
                        + inboxFolder.getEmailFolderId());

                final Bundle extras = new Bundle(2);
                if (accountId != -1) {
                    if (D) Log.d(TAG,"updateInbox accountId=" + accountId);
                    extras.putLong(BluetoothMapContract.EXTRA_UPDATE_FOLDER_ID,
                            inboxFolder.getEmailFolderId());
                    extras.putLong(BluetoothMapContract.EXTRA_UPDATE_ACCOUNT_ID, accountId);
                } else {
                    // Only error code allowed on an UpdateInbox is OBEX_HTTP_NOT_IMPLEMENTED,
                    // i.e. if e.g. update not allowed on the mailbox
                    if (D) Log.d(TAG,"updateInbox accountId=0 -> OBEX_HTTP_NOT_IMPLEMENTED");
                    return ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED;
                }

                Uri emailUri = Uri.parse(mBaseEmailUriString);
                if (D) Log.d(TAG,"updateInbox in: " + emailUri.toString());
                try {
                    if (D) Log.d(TAG,"updateInbox call()...");
                    Bundle myBundle = mProviderClient.call(BluetoothMapContract.METHOD_UPDATE_FOLDER, null, extras);
                    if (myBundle != null)
                        return ResponseCodes.OBEX_HTTP_OK;
                    else {
                        if (D) Log.d(TAG,"updateInbox call failed");
                        return ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED;
                    }
                } catch (RemoteException e){
                    mProviderClient = acquireUnstableContentProviderOrThrow();
                    return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
                }catch (NullPointerException e) {
                    if(D) Log.e(TAG, "UpdateInbox - if uri or method is null", e);
                    return ResponseCodes.OBEX_HTTP_UNAVAILABLE;

                } catch (IllegalArgumentException e) {
                    if(D) Log.e(TAG, "UpdateInbox - if uri is not known", e);
                    return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
                }
            }
        }

        return ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED;
    }

     private BluetoothMapFolderElement getFolderElementFromName(String folderName) {
        BluetoothMapFolderElement folderElement = null;

        if(folderName == null || folderName.trim().isEmpty() ) {
            folderElement = mCurrentFolder;
            if(D) Log.d(TAG, "no folder name supplied, setting folder to current: "
                             + folderElement.getName());
        } else {
            folderElement = mCurrentFolder.getSubFolder(folderName);
            if (folderElement == null) {
                folderElement = mCurrentFolder.getRoot()
                        .getSubFolder("telecom").getSubFolder("msg").getSubFolder(folderName);
            }
            if(D) Log.d(TAG, "Folder name: " + folderName + " resulted in this element: "
                    + (folderElement != null ? folderElement.getName() : "null folder"));
        }
        return folderElement;
    }

    private int pushMessageGemini(final Operation op, String folderName,
            BluetoothMapAppParams appParams, long subId) {
        if(appParams.getCharset() == BluetoothMapAppParams.INVALID_VALUE_PARAMETER) {
            if(D) Log.d(TAG, "[pushMessageGemini]: Missing charset - unable to decode message content. " +
                    "appParams.getCharset() = " + appParams.getCharset());
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        }
        InputStream bMsgStream = null;
        try {
            BluetoothMapFolderElement folderElement = getFolderElementFromName(folderName);
            if(folderElement == null) {
                Log.w(TAG,"[pushMessageGemini]: folderElement == null - sending OBEX_HTTP_PRECON_FAILED");
                return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
            } else {
                folderName = folderElement.getName();
            }
            if (!folderName.equals(BluetoothMapContract.FOLDER_NAME_OUTBOX) &&
                    !folderName.equals(BluetoothMapContract.FOLDER_NAME_DRAFT)) {
                if(D) Log.d(TAG, "[pushMessageGemini]: Is only allowed to outbox and draft. " +
                        "folderName=" + folderName);
                return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
            }

            /*  - Read out the message
             *  - Decode into a bMessage
             *  - send it.
             */
            BluetoothMapbMessage message;
            bMsgStream = op.openInputStream();
            // Decode the messageBody
            message = BluetoothMapbMessage.parse(bMsgStream, appParams.getCharset());
            // Send message
            if (mObserver == null || message == null) {
                // Should not happen except at shutdown.
                if(D) Log.w(TAG, "[pushMessageGemini] mObserver or parsed message not available" );
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
            }

            if ((message.getType().equals(TYPE.EMAIL) && (folderElement.getEmailFolderId() == -1)) ||
                ((message.getType().equals(TYPE.SMS_GSM) || message.getType().equals(TYPE.SMS_CDMA) ||
                  message.getType().equals(TYPE.MMS)) && !folderElement.hasSmsMmsContent()) ) {
                if(D) Log.w(TAG, "[pushMessageGemini] Wrong message type recieved" );
                return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
            }

            long handle = mObserver.pushMessage(message, folderElement, appParams, mBaseEmailUriString, subId);
            if (D) Log.d(TAG, "[pushMessageGemini] handle: " + handle);
            if (handle < 0) {
                if(D) Log.w(TAG, "Message  handle not created" );
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE; // Should not happen.
            }
            HeaderSet replyHeaders = new HeaderSet();
            String handleStr = BluetoothMapUtils.getMapHandle(handle, message.getType());
            if (D) Log.d(TAG, "[pushMessageGemini] handleStr: " + handleStr + " message.getType(): " + message.getType());
            replyHeaders.setHeader(HeaderSet.NAME, handleStr);
            op.sendHeaders(replyHeaders);

        } catch (RemoteException e) {
            //reload the providerClient and return error
            try {
                mProviderClient = acquireUnstableContentProviderOrThrow();
            }catch (RemoteException e2){
                //should not happen
            }
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        } catch (IllegalArgumentException e) {
            if (D) Log.e(TAG, "Wrongly formatted bMessage received", e);
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        } catch (IOException e) {
            if (D) Log.e(TAG, "Exception occured: ", e);
            if(mIsAborted == true) {
                if(D) Log.d(TAG, "PushMessage Operation Aborted");
                return ResponseCodes.OBEX_HTTP_OK;
            } else {
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        } catch (Exception e) {
            if (D) Log.e(TAG, "Exception:", e);
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        } finally {
            if(bMsgStream != null) {
                try {
                    bMsgStream.close();
                } catch (IOException e) {}
            }
        }
        return ResponseCodes.OBEX_HTTP_OK;
    }

    private int setMessageStatus(String msgHandle, BluetoothMapAppParams appParams) {
        int indicator = appParams.getStatusIndicator();
        int value = appParams.getStatusValue();
        Log.d(TAG, "[setMessageStatus] indicator = " + indicator + " value = " + value);
        long handle;
        BluetoothMapUtils.TYPE msgType;

        if(indicator == BluetoothMapAppParams.INVALID_VALUE_PARAMETER ||
           value == BluetoothMapAppParams.INVALID_VALUE_PARAMETER ||
           msgHandle == null) {
            Log.d(TAG, "[setMessageStatus] INVALID_VALUE_PARAMETER || msgHandle == null");
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        }
        if (mObserver == null) {
            if(D) Log.d(TAG, "Error: no mObserver!");
            return ResponseCodes.OBEX_HTTP_UNAVAILABLE; // Should not happen.
        }

        try {
            handle = BluetoothMapUtils.getCpHandle(msgHandle);
            msgType = BluetoothMapUtils.getMsgTypeFromHandle(msgHandle);
            if(D)Log.d(TAG,"setMessageStatus. Handle:" + handle+", MsgType: "+ msgType);
        } catch (NumberFormatException e) {
            Log.w(TAG, "[setMessageStatus] Wrongly formatted message handle: " + msgHandle);
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        }

        if( indicator == BluetoothMapAppParams.STATUS_INDICATOR_DELETED) {
            if (!mObserver.setMessageStatusDeleted(handle, msgType, mCurrentFolder,
                    mBaseEmailUriString, value)) {
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
            }
        } else /* BluetoothMapAppParams.STATUS_INDICATOR_READ */ {
            try{
            if (!mObserver.setMessageStatusRead(handle, msgType, mBaseEmailUriString, value)) {
                if(D)Log.d(TAG,"not able to update the message");
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
            }
            }catch(RemoteException e) {
                if(D) Log.e(TAG,"Error in setMessageStatusRead()", e);
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
            }
        }
        Log.d(TAG, "[setMessageStatus] success");
        return ResponseCodes.OBEX_HTTP_OK;
    }

    @Override
    public int onSetPath(final HeaderSet request, final HeaderSet reply, final boolean backup,
            final boolean create) {
        String folderName;
        BluetoothMapFolderElement folder;
        notifyUpdateWakeLock();
        try {
            folderName = (String)request.getHeader(HeaderSet.NAME);
        } catch (Exception e) {
            if(D) {
                Log.e(TAG, "request headers error" , e);
            } else {
                Log.e(TAG, "request headers error");
            }
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        if (V) logHeader(request);
        if (D) Log.d(TAG, "[onSetPath] name is " + folderName + " backup: " + backup
                     + " create: " + create);

        if(backup == true){
            if (mCurrentFolder.getParent() != null) {
                mCurrentFolder = mCurrentFolder.getParent();
            } else {
                Log.d(TAG, "[onSetPath] mCurrentFolder.getParent() == null");
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        }

        if (folderName == null || folderName.trim().isEmpty()) {
            if(backup == false)
                mCurrentFolder = mCurrentFolder.getRoot();
        }
        else {
            folder = mCurrentFolder.getSubFolder(folderName);
            if (folder != null) {
                mCurrentFolder = folder;
            } else {
                Log.d(TAG, "[onSetPath] folder == null");
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        }
        if (V) Log.d(TAG, "[onSetPath] Current Folder: " + mCurrentFolder.getName());
        return ResponseCodes.OBEX_HTTP_OK;
    }

    @Override
    public void onClose() {
        Log.d(TAG, "[onClose]: enter");
        if (mCallback != null) {
            Message msg = Message.obtain(mCallback);
            msg.what = BluetoothMapService.MSG_SERVERSESSION_CLOSE;
            msg.arg1 = mMasId;
            msg.sendToTarget();
        }
        if(mProviderClient != null){
            mProviderClient.release();
            mProviderClient = null;
        }

    }

    @Override
    public int onGet(Operation op) {
        if (D) Log.d(TAG, "[onGet]: enter");
        notifyUpdateWakeLock();
        mIsAborted = false;
        HeaderSet request;
        String type;
        String name;
        byte[] appParamRaw = null;
        BluetoothMapAppParams appParams = null;
        try {
            request = op.getReceivedHeader();
            type = (String)request.getHeader(HeaderSet.TYPE);
            name = (String)request.getHeader(HeaderSet.NAME);
            appParamRaw = (byte[])request.getHeader(HeaderSet.APPLICATION_PARAMETER);
            if(appParamRaw != null)
                appParams = new BluetoothMapAppParams(appParamRaw);

            if (V) logHeader(request);
            if (D) Log.d(TAG, "[OnGet] type is " + type + " name is " + name);

            if (type == null) {
                if (V) Log.d(TAG, "[onGet] type is null?" + type);
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }

            if (type.equals(TYPE_GET_FOLDER_LISTING)) {
                if (V && appParams != null) {
                    Log.d(TAG, "[onGet] TYPE_GET_FOLDER_LISTING: MaxListCount = " + appParams.getMaxListCount() +
                              ", ListStartOffset = " + appParams.getStartOffset());
                }
                return sendFolderListingRsp(op, appParams); // Block until all packets have been send.
            } else if (type.equals(TYPE_GET_MESSAGE_LISTING)){
                if (V && appParams != null) {
                    Log.d(TAG, "[onGet] TYPE_GET_MESSAGE_LISTING: MaxListCount = " + appParams.getMaxListCount() +
                              ", ListStartOffset = " + appParams.getStartOffset());
                    Log.d(TAG, "[onGet] SubjectLength = " + appParams.getSubjectLength() + ", ParameterMask = " +
                              appParams.getParameterMask());
                    Log.d(TAG, "[onGet] FilterMessageType = " + appParams.getFilterMessageType() +
                              ", FilterPeriodBegin = " + appParams.getFilterPeriodBegin());
                    Log.d(TAG, "[onGet] FilterPeriodEnd = " + appParams.getFilterPeriodBegin() +
                              ", FilterReadStatus = " + appParams.getFilterReadStatus());
                    Log.d(TAG, "[onGet] FilterRecipient = " + appParams.getFilterRecipient() +
                              ", FilterOriginator = " + appParams.getFilterOriginator());
                    Log.d(TAG, "[onGet] FilterPriority = " + appParams.getFilterPriority());
                }
                if (name == null) {
                    name = mCurrentFolder.getName();
                }
                if (name.equalsIgnoreCase("telecom") || name.equalsIgnoreCase("msg")) {
                    Log.d(TAG, "[onGet] TYPE_GET_MESSAGE_LISTING invalid folder " + name);
                    return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
                }
                return sendMessageListingRsp(op, appParams, name); // Block until all packets have been send.
            } else if (type.equals(TYPE_MESSAGE)){
                if(V && appParams != null) {
                    Log.d(TAG,"TYPE_MESSAGE (GET): Attachment = " + appParams.getAttachment() +
                            ", Charset = " + appParams.getCharset() +
                            ", FractionRequest = " + appParams.getFractionRequest());
                }
                return sendGetMessageRsp(op, name, appParams); // Block until all packets have been send.
            } else {
                Log.w(TAG, "[onGet] unknown type request: " + type);
                return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
            }

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Exception:", e);
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        } catch (ParseException e) {
            Log.e(TAG, "Exception:", e);
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        } catch (Exception e) {
            if(D) {
                Log.e(TAG, "Exception occured while handling request",e);
            } else {
                Log.e(TAG, "Exception occured while handling request");
            }
            if(mIsAborted == true) {
                if(D) Log.d(TAG, "onGet Operation Aborted");
                return ResponseCodes.OBEX_HTTP_OK;
            } else {
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        }
    }

    /**
     * Generate and send the message listing response based on an application
     * parameter header. This function call will block until complete or aborted
     * by the peer. Fragmentation of packets larger than the obex packet size
     * will be handled by this function.
     *
     * @param op
     *            The OBEX operation.
     * @param appParams
     *            The application parameter header
     * @return {@link ResponseCodes.OBEX_HTTP_OK} on success or
     *         {@link ResponseCodes.OBEX_HTTP_BAD_REQUEST} on error.
     */
    private int sendMessageListingRsp(Operation op, BluetoothMapAppParams appParams, String folderName){
        OutputStream outStream = null;
        byte[] outBytes = null;
        int maxChunkSize, bytesToWrite, bytesWritten = 0, listSize;
        boolean hasUnread = false;
        HeaderSet replyHeaders = new HeaderSet();
        BluetoothMapAppParams outAppParams = new BluetoothMapAppParams();
        BluetoothMapMessageListing outList;
        if(appParams == null){
            appParams = new BluetoothMapAppParams();
            appParams.setMaxListCount(1024);
            appParams.setStartOffset(0);
        }

        BluetoothMapFolderElement folderToList = getFolderElementFromName(folderName);
        if(folderToList == null) {
            Log.w(TAG,"sendMessageListingRsp: folderToList == null - sending OBEX_HTTP_BAD_REQUEST");
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        // Check to see if we only need to send the size - hence no need to encode.
        try {
            // Open the OBEX body stream
            outStream = op.openOutputStream();

            if(appParams.getMaxListCount() == BluetoothMapAppParams.INVALID_VALUE_PARAMETER)
                appParams.setMaxListCount(1024);

            if(appParams.getStartOffset() == BluetoothMapAppParams.INVALID_VALUE_PARAMETER)
                appParams.setStartOffset(0);

            Log.d(TAG, "[sendMessageListingRsp]: MaxListCount = " + appParams.getMaxListCount()
                    + "folderName = " + folderName);
            if(appParams.getMaxListCount() != 0) {
                outList = mOutContent.msgListing(folderToList, appParams);
                // Generate the byte stream
                outAppParams.setMessageListingSize(outList.getCount());
                outBytes = outList.encode(mThreadIdSupport); // Include thread ID for clients that supports it.
                hasUnread = outList.hasUnread();
                Log.d(TAG, "[sendMessageListingRsp]: outList count = "
                        + outList.getCount() + " outList read = " + hasUnread);
            }
            else {
                listSize = mOutContent.msgListingSize(folderToList, appParams);
                hasUnread = mOutContent.msgListingHasUnread(folderToList, appParams);
                outAppParams.setMessageListingSize(listSize);
                op.noBodyHeader();
                Log.d(TAG, "[sendMessageListingRsp]: listSize = "
                        + listSize + " hasUnread = " + hasUnread);
            }

            // Build the application parameter header

            // let the peer know if there are unread messages in the list
            if(hasUnread) {
                outAppParams.setNewMessage(1);
            }else{
                outAppParams.setNewMessage(0);
            }

            outAppParams.setMseTime(Calendar.getInstance().getTime().getTime());
            replyHeaders.setHeader(HeaderSet.APPLICATION_PARAMETER, outAppParams.EncodeParams());
            op.sendHeaders(replyHeaders);

        } catch (IOException e) {
            Log.w(TAG, "[sendMessageListingRsp]: IOException - sending OBEX_HTTP_BAD_REQUEST", e);
            if(outStream != null) { try { outStream.close(); } catch (IOException ex) {} }
            if(mIsAborted == true) {
                if(D) Log.d(TAG, "sendMessageListingRsp Operation Aborted");
                return ResponseCodes.OBEX_HTTP_OK;
            } else {
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        } catch (IllegalArgumentException e) {
            Log.w(TAG,"sendMessageListingRsp: IllegalArgumentException - sending OBEX_HTTP_BAD_REQUEST", e);
            if(outStream != null) { try { outStream.close(); } catch (IOException ex) {} }
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        maxChunkSize = op.getMaxPacketSize(); // This must be called after setting the headers.
        if(outBytes != null) {
            try {
                while (bytesWritten < outBytes.length && mIsAborted == false) {
                    bytesToWrite = Math.min(maxChunkSize, outBytes.length - bytesWritten);
                    outStream.write(outBytes, bytesWritten, bytesToWrite);
                    bytesWritten += bytesToWrite;
                }
            } catch (IOException e) {
                if(D) Log.w(TAG,e);
                // We were probably aborted or disconnected
            } finally {
                if(outStream != null) { try { outStream.close(); } catch (IOException e) {} }
            }
            if(bytesWritten != outBytes.length && !mIsAborted) {
                Log.w(TAG,"sendMessageListingRsp: bytesWritten != outBytes.length - sending OBEX_HTTP_BAD_REQUEST");
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        } else {
            if(outStream != null) { try { outStream.close(); } catch (IOException e) {} }
        }
        Log.d(TAG, "[sendMessageListingRsp]: success");
        return ResponseCodes.OBEX_HTTP_OK;
    }

    /**
     * Generate and send the Folder listing response based on an application
     * parameter header. This function call will block until complete or aborted
     * by the peer. Fragmentation of packets larger than the obex packet size
     * will be handled by this function.
     *
     * @param op
     *            The OBEX operation.
     * @param appParams
     *            The application parameter header
     * @return {@link ResponseCodes.OBEX_HTTP_OK} on success or
     *         {@link ResponseCodes.OBEX_HTTP_BAD_REQUEST} on error.
     */
    private int sendFolderListingRsp(Operation op, BluetoothMapAppParams appParams){
        OutputStream outStream = null;
        byte[] outBytes = null;
        BluetoothMapAppParams outAppParams = new BluetoothMapAppParams();
        int maxChunkSize, bytesWritten = 0;
        HeaderSet replyHeaders = new HeaderSet();
        int bytesToWrite, maxListCount, listStartOffset;
        if(appParams == null){
            appParams = new BluetoothMapAppParams();
            appParams.setMaxListCount(1024);
        }

        if(V)
            Log.v(TAG, "[sendFolderListingRsp] for " + mCurrentFolder.getName());

        try {
            maxListCount = appParams.getMaxListCount();
            listStartOffset = appParams.getStartOffset();

            if(listStartOffset == BluetoothMapAppParams.INVALID_VALUE_PARAMETER)
                listStartOffset = 0;

            if(maxListCount == BluetoothMapAppParams.INVALID_VALUE_PARAMETER)
                maxListCount = 1024;

            if(maxListCount != 0)
            {
                outBytes = mCurrentFolder.encode(listStartOffset, maxListCount);
                outStream = op.openOutputStream();
            }

            // Build and set the application parameter header
            outAppParams.setFolderListingSize(mCurrentFolder.getSubFolderCount());
            replyHeaders.setHeader(HeaderSet.APPLICATION_PARAMETER, outAppParams.EncodeParams());
            op.sendHeaders(replyHeaders);

        } catch (IOException e1) {
            Log.w(TAG, "[sendFolderListingRsp]: IOException - sending OBEX_HTTP_BAD_REQUEST Exception:", e1);
            if(outStream != null) { try { outStream.close(); } catch (IOException e) {} }
            if(mIsAborted == true) {
                if(D) Log.d(TAG, "sendFolderListingRsp Operation Aborted");
                return ResponseCodes.OBEX_HTTP_OK;
            } else {
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        } catch (IllegalArgumentException e1) {
            Log.w(TAG, "[sendFolderListingRsp]: IllegalArgumentException - sending OBEX_HTTP_BAD_REQUEST Exception:", e1);
            if(outStream != null) { try { outStream.close(); } catch (IOException e) {} }
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        }

        maxChunkSize = op.getMaxPacketSize(); // This must be called after setting the headers.

        if(outBytes != null) {
            try {
                while (bytesWritten < outBytes.length && mIsAborted == false) {
                    bytesToWrite = Math.min(maxChunkSize, outBytes.length - bytesWritten);
                    outStream.write(outBytes, bytesWritten, bytesToWrite);
                    bytesWritten += bytesToWrite;
                }
            } catch (IOException e) {
                // We were probably aborted or disconnected
            } finally {
                if(outStream != null) { try { outStream.close(); } catch (IOException e) {} }
            }
            if(V)
                Log.v(TAG, "[sendFolderListingRsp] sent " + bytesWritten + " bytes out of " + outBytes.length);
            if(bytesWritten == outBytes.length || mIsAborted)
                return ResponseCodes.OBEX_HTTP_OK;
            else
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        return ResponseCodes.OBEX_HTTP_OK;
    }

    /**
     * Generate and send the get message response based on an application
     * parameter header and a handle.
     *
     * @param op
     *            The OBEX operation.
     * @param appParams
     *            The application parameter header
     * @param handle
     *            The handle of the requested message
     * @return {@link ResponseCodes.OBEX_HTTP_OK} on success or
     *         {@link ResponseCodes.OBEX_HTTP_BAD_REQUEST} on error.
     */
    private int sendGetMessageRsp(Operation op, String handle, BluetoothMapAppParams appParams){
        OutputStream outStream = null;
        byte[] outBytes = null;
        int maxChunkSize, bytesToWrite, bytesWritten = 0;

        try {
            outBytes = mOutContent.getMessage(handle, appParams, mCurrentFolder);
            outStream = op.openOutputStream();

            // If it is a fraction request of Email message, set header before responding
            if ((BluetoothMapUtils.getMsgTypeFromHandle(handle).equals(TYPE.EMAIL)) &&
                    (appParams.getFractionRequest() ==
                    BluetoothMapAppParams.FRACTION_REQUEST_FIRST)) {
                BluetoothMapAppParams outAppParams  = new BluetoothMapAppParams();;
                HeaderSet replyHeaders = new HeaderSet();
                outAppParams.setFractionDeliver(BluetoothMapAppParams.FRACTION_DELIVER_LAST);
                // Build and set the application parameter header
                replyHeaders.setHeader(HeaderSet.APPLICATION_PARAMETER,
                        outAppParams.EncodeParams());
                op.sendHeaders(replyHeaders);
                if(V) Log.v(TAG,"sendGetMessageRsp fractionRequest - " +
                        "set FRACTION_DELIVER_LAST header");
            }

        } catch (IOException e) {
            Log.w(TAG, "[sendGetMessageRsp]: IOException - sending OBEX_HTTP_BAD_REQUEST", e);
            if(outStream != null) { try { outStream.close(); } catch (IOException ex) {} }
            if(mIsAborted == true) {
                if(D) Log.d(TAG, "sendGetMessageRsp Operation Aborted");
                return ResponseCodes.OBEX_HTTP_OK;
            } else {
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        } catch (IllegalArgumentException e) {
            Log.w(TAG,"sendGetMessageRsp: IllegalArgumentException (e.g. invalid handle) - " +
                    "sending OBEX_HTTP_BAD_REQUEST", e);
            if(outStream != null) { try { outStream.close(); } catch (IOException ex) {} }
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        maxChunkSize = op.getMaxPacketSize(); // This must be called after setting the headers.

        if(outBytes != null) {
            try {
                while (bytesWritten < outBytes.length && mIsAborted == false) {
                    bytesToWrite = Math.min(maxChunkSize, outBytes.length - bytesWritten);
                    outStream.write(outBytes, bytesWritten, bytesToWrite);
                    bytesWritten += bytesToWrite;
                }
            } catch (IOException e) {
                // We were probably aborted or disconnected
                if(D && e.getMessage().equals("Abort Received")) {
                    Log.w(TAG, "getMessage() Aborted...", e);
                }
            } finally {
                if(outStream != null) { try { outStream.close(); } catch (IOException e) {} }
            }
            if(bytesWritten == outBytes.length || mIsAborted)
                return ResponseCodes.OBEX_HTTP_OK;
            else
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }
        Log.d(TAG, "[sendGetMessageRsp]: success");
        return ResponseCodes.OBEX_HTTP_OK;
    }

    private void notifyUpdateWakeLock() {
        if(mCallback != null) {
            Message msg = Message.obtain(mCallback);
            msg.what = BluetoothMapService.MSG_ACQUIRE_WAKE_LOCK;
            msg.sendToTarget();
        }
    }

    private static final void logHeader(HeaderSet hs) {
        Log.v(TAG, "[logHeader] Dumping HeaderSet " + hs.toString());
        try {
            Log.v(TAG, "[logHeader] CONNECTION_ID : " + hs.getHeader(HeaderSet.CONNECTION_ID));
            Log.v(TAG, "[logHeader] NAME : " + hs.getHeader(HeaderSet.NAME));
            Log.v(TAG, "[logHeader] TYPE : " + hs.getHeader(HeaderSet.TYPE));
            Log.v(TAG, "[logHeader] TARGET : " + hs.getHeader(HeaderSet.TARGET));
            Log.v(TAG, "[logHeader] WHO : " + hs.getHeader(HeaderSet.WHO));
            Log.v(TAG, "[logHeader] APPLICATION_PARAMETER : " + hs.getHeader(HeaderSet.APPLICATION_PARAMETER));
        } catch (IOException e) {
            Log.e(TAG, "[logHeader] dump HeaderSet error " + e);
        }
        Log.v(TAG, "[logHeader] NEW!!! Dumping HeaderSet END");
    }

    /// M: Map Gemini Feature @{
    private int pushMessage(final Operation op, String folderName,
            BluetoothMapAppParams appParams, boolean isAutoSelectSub) {
        if (appParams.getCharset() == BluetoothMapAppParams.INVALID_VALUE_PARAMETER) {
            if (D) Log.d(TAG, "[pushMessage] Missing charset - unable to decode message content. appParams.getCharset() = " + appParams.getCharset());
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        }
        try {
            BluetoothMapFolderElement folderElement = getFolderElementFromName(folderName);
            if(folderElement == null) {
                Log.w(TAG, "[pushMessage] folderElement == null - sending OBEX_HTTP_PRECON_FAILED");
                return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
            } else {
                folderName = folderElement.getName();
            }
            if (!folderName.equals(BluetoothMapContract.FOLDER_NAME_OUTBOX) &&
                    !folderName.equals(BluetoothMapContract.FOLDER_NAME_DRAFT)) {
                if(D) Log.d(TAG, "[pushMessage]: Is only allowed to outbox and draft. " +
                        "folderName=" + folderName);
                return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
            }
            /*  - Read out the message
             *  - Decode into a bMessage
             *  - send it.
             */
            InputStream bMsgStream;
            BluetoothMapbMessage message;
            bMsgStream = op.openInputStream();
            message = BluetoothMapbMessage.parse(bMsgStream, appParams.getCharset()); // Decode the messageBody
            Log.d(TAG, "[pushMessage] BluetoothMapbMessage.parse success");
            // Send message
            if (mObserver == null) {
                Log.d(TAG, "[pushMessage] observer == null");
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE; // Should not happen.
            }

            long subId = BluetoothMapSimManager.INVALID_SUBID;
            /// M: send direcly if message Originator == sim card number
            /*
            ArrayList<vCard> cards = message.getOriginators();
            if (cards != null && cards.size() == 1) {
                String[] origNumber = cards.get(0).getPhoneNumber();
                if (origNumber != null && origNumber.length == 1) {
                    simId = mSimManager.getSimIdFromOriginator(origNumber[0]);
                }
            }
            */

            /// M: ALPS01374857, MAP new feature: support auto-select-sim @{
            if (isAutoSelectSub && message != null) {
                /// M: only have one recipient when CE4A uploads message
                String number = message.getSingleRecipient();
                if (!TextUtils.isEmpty(number)) {
                    long threadId = mOutContent.getThreadIdByNumber(number);
                    if (threadId > 0) {
                        subId = mOutContent.getSubIdByThread(threadId);
                    } else {
                        subId = BluetoothMapSimManager.INVALID_SUBID;
                    }
                } else {
                    subId = BluetoothMapSimManager.INVALID_SUBID;
                }
            }
            /// @}

            if (subId <= BluetoothMapSimManager.INVALID_SUBID) {
                final String finalFolderName = folderName;
                final BluetoothMapAppParams finalAppParams = appParams;
                final BluetoothMapbMessage finalMessage = message;
                final BluetoothMapContentObserver finalObserver = mObserver;
                final InputStream finalMsgStream = bMsgStream;
                mCallback.post(new Runnable() {
                    @Override
                    public void run() {
                        showSubSelectDialog(op, finalFolderName, finalAppParams, finalMessage, finalObserver, finalMsgStream);
                    }
                });
            } else {
                /// M: VALID Originators subId
                return sendMessage(op, folderName, appParams, message, mObserver, bMsgStream, subId);
            }
        } catch (IllegalArgumentException e) {
            if (D) Log.w(TAG, "[pushMessage] Wrongly formatted bMessage received", e);
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        } catch (Exception e) {
            // TODO: Change to IOException after debug
            Log.e(TAG, "[pushMessage] Exception occured: ", e);
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }
        return ResponseCodes.OBEX_HTTP_OK;
    }

    private int sendMessage(final Operation op, final String folderName,
            final BluetoothMapAppParams appParams, final BluetoothMapbMessage message,
            final BluetoothMapContentObserver observer, final InputStream bMsgStream, long subId) {
        try {
            BluetoothMapFolderElement folderElement = getFolderElementFromName(folderName);
            long handle = observer.pushMessage(message, folderElement, appParams, mBaseEmailUriString, subId);
            if (D)
                Log.d(TAG, "[sendMessage] handle: " + handle);
            if (handle < 0) {
                Log.d(TAG, "[sendMessage] handle < 0");
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE; // Should not happen.
            }
            HeaderSet replyHeaders = new HeaderSet();
            String handleStr = BluetoothMapUtils.getMapHandle(handle, message.getType());
            if (D)
                Log.d(TAG, "[sendMessage] handleStr: " + handleStr + " message.getType(): " + message.getType());
            replyHeaders.setHeader(HeaderSet.NAME, handleStr);
            op.sendHeaders(replyHeaders);

            bMsgStream.close();
        } catch (IllegalArgumentException e) {
            if (D) Log.w(TAG, "[sendMessage] Wrongly formatted bMessage received", e);
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        } catch (Exception e) {
            // TODO: Change to IOException after debug
            Log.e(TAG, "[sendMessage] Exception occured: ", e);
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }
        return ResponseCodes.OBEX_HTTP_OK;
    }

    public void showSubSelectDialog(final Operation op, final String folderName,
           final BluetoothMapAppParams appParams, final  BluetoothMapbMessage message,
           final BluetoothMapContentObserver observer, final InputStream bMsgStream) {
        Log.d(TAG, "[showSubSelectDialog] enter");

        boolean isSingleRecipient = false;
        String recipentNumber = "";
        ArrayList<vCard> cards = message.getRecipients();
        if (cards != null && cards.size() == 1) {
            isSingleRecipient = true;
            String[] origNumber = cards.get(0).getPhoneNumber();
            if (origNumber != null && origNumber.length == 1) {
                recipentNumber = origNumber[0];
            }
        }
        Log.d(TAG, "[showSubSelectDialog] isSingleRecipient = " + isSingleRecipient
                + " recipentNumber = " + recipentNumber);

        //final List<SubInfoRecord> subInfoList = updateSubInfoList();
        final List<SubscriptionInfo> subInfoList = updateSubInfoList();
        final BluetoothMapSubAdapter adapter = new BluetoothMapSubAdapter(mContext, subInfoList);
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(mContext.getString(R.string.sim_selected_dialog_title));
        builder.setCancelable(false);
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @SuppressWarnings("unchecked")
            public final void onClick(DialogInterface dialog, int which) {
                //final long subId = subInfoList.get(which).subId;
            	final long subId = (long)(subInfoList.get(which).getSubscriptionId());
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "[showSubSelectDialog] sendMessage enter: subId " + subId);
                        sendMessage(op, folderName, appParams, message, observer, bMsgStream, subId);
                    }
                }).start();
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        AlertDialog subSelectDialog = builder.create();
        subSelectDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        subSelectDialog.show();
    }

    //private List<SubInfoRecord> updateSubInfoList() {
    private List<SubscriptionInfo> updateSubInfoList() {    	
        // Log.d(TAG, "updateSubInfoList Start");
        // final List<SubInfoRecord> subInfoList = new
        // ArrayList<SubInfoRecord>();
        // final List<SubInfoRecord> emptySubListInfo = new
        // ArrayList<SubInfoRecord>();
        final List<SubscriptionInfo> subInfoList = new ArrayList<SubscriptionInfo>();
        // final List<SubscriptionInfo> emptySubListInfo = new
        // ArrayList<SubscriptionInfo>();
        // int slotCount = TelephonyManager.getDefault().getSimCount();
        // subInfoList.clear();
        // emptySubListInfo.clear();
        // for (int slotId = 0; slotId < slotCount; slotId++) {
        List<SubscriptionInfo> subInfoRecordInOneSim = SubscriptionManager
                .from(mContext).getActiveSubscriptionInfoList();
        Log.d(TAG, "updateSubInfoList subInfoRecordInOneSim="
                + subInfoRecordInOneSim);
        if (subInfoRecordInOneSim != null && subInfoRecordInOneSim.size() > 0) {
            for (int i = 0; i < subInfoRecordInOneSim.size(); i++) {
                SubscriptionInfo infoRecord = subInfoRecordInOneSim.get(i);
                subInfoList.add(infoRecord);
                Log.i(TAG,
                        "updateSubInfoList name=" + infoRecord.getDisplayName());
            }
        }
        // else {
        // // SubscriptionInfo infoRecord = new SubscriptionInfo();
        // // // infoRecord.displayName = String.format(
        // // // mContext.getResources().getString(R.string.empty_sub_name),
        // // slotId + 1);
        // // // infoRecord.slotId = SubscriptionManager.SIM_NOT_INSERTED;
        // // // emptySubListInfo.add(infoRecord);
        // }
        // // }
        // for (int i = 0; i < emptySubListInfo.size(); i++) {
        // subInfoList.add(emptySubListInfo.get(i));
        // }
        return subInfoList;
    }
    /// @}
}
