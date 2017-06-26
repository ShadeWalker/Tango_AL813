/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.mms.ui;
import com.android.internal.telephony.ISms;
import com.android.internal.telephony.ITelephony;
import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.LogTag;
import com.android.mms.data.Contact;
import com.android.mms.data.Conversation;
import com.android.mms.TempFileProvider;
import com.android.mms.data.WorkingMessage;
import com.android.mms.model.MediaModel;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.ui.DeliveryReportActivity.MmsReportStatus;
import com.android.mms.util.MuteCache;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.CharacterSets;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.NotificationInd;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.pdu.RetrieveConf;
import com.google.android.mms.pdu.SendReq;

import android.database.sqlite.SqliteWrapper;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Resources;
import android.database.Cursor;
import android.media.CamcorderProfile;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.storage.StorageVolume;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Mms.Part;
import android.provider.Telephony.Sms;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.style.URLSpan;
import android.util.AndroidException;
import android.util.Log;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.RemoteViews;
import android.widget.Toast;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// M:
import com.android.internal.telephony.PhoneConstants;

import android.os.ServiceManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.location.Country;
import android.location.CountryDetector;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.os.Message;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.os.SystemProperties;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Bitmap.CompressFormat;
import android.os.StatFs;
import android.provider.BaseColumns;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;

import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.text.style.BackgroundImageSpan;

import android.text.style.StyleSpan;
import android.graphics.drawable.Drawable;

import com.android.mms.util.FeatureOption;
import com.mediatek.drm.OmaDrmStore;
import com.mediatek.drm.OmaDrmUtils;
import com.android.mms.util.MmsContentType;
import com.google.android.mms.pdu.MultimediaMessagePdu;
import com.mediatek.common.MPlugin;

import android.os.storage.StorageManager;

import com.android.mms.util.MmsLog;
import com.android.mms.R;
import com.android.mms.transaction.MessagingNotification;

import android.app.PendingIntent;

import com.android.mms.transaction.CBMessagingNotification;
import com.android.i18n.phonenumbers.AsYouTypeFormatter;
import com.android.i18n.phonenumbers.PhoneNumberUtil;


import com.android.mms.util.MessageResource;

import android.provider.Telephony;

import com.mediatek.ipmsg.util.IpMessageUtils;
import com.mediatek.mms.ext.IMmsPreferenceExt;
import com.mediatek.mms.ext.IStorageLowExt;
import com.mediatek.mms.ext.IStringReplacementExt;
import com.mediatek.mms.ext.DefaultMmsPreferenceExt;
import com.mediatek.mms.ext.DefaultStorageLowExt;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Locale;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;

/// M: add for ipmessage
import com.android.mms.MmsPluginManager;
import com.mediatek.mms.ext.IMmsUtilsExt;
import com.mediatek.mms.ipmessage.IpMessageConsts;

import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.AlertDialog.Builder;

//import com.mediatek.common.telephony.ITelephonyEx;
import com.mediatek.storage.StorageManagerEx;

import java.util.Arrays;
import java.util.Set;
import com.android.mms.HQMmsConfigOverride;//add by lipeng
import com.android.mms.HQMmsConfigOverride.MmsConfigBean;//add by lipeng
import com.android.internal.telephony.TelephonyProperties;//add by lipeng
import android.os.SystemProperties;//add by lipeng
import com.android.mms.SevenConfigOverride;//add by lipeng
import com.android.mms.SevenConfigOverride.MmsSevenConfigBean;//add by lipeng
import android.telephony.PhoneNumberUtils;//add by lipeng
import android.telephony.SubscriptionManager;//add by lipeng
import android.telephony.SubscriptionInfo;
import java.util.Locale;//add by lipeng
import java.util.regex.Pattern;//add by lipeng

/**
 * An utility class for managing messages.
 */
public class MessageUtils {
    interface ResizeImageResultCallback {
        void onResizeResult(PduPart part, boolean append);
    }

	/*HQ_zhangjing add for change default mms preference to contacts begin*/
	public static String DEFAULT_MMS_PREFERENCE_NAME = "com.android.contacts_preferences";
    private static final String TAG = LogTag.TAG;

    /// M: add for ipmessage
    private static final String IPMSG_TAG = "Mms/ipmsg";
    private static String sLocalNumber;
    private static String[] sNoSubjectStrings;

    // Cache of both groups of space-separated ids to their full
    // comma-separated display names, as well as individual ids to
    // display names.
    // TODO: is it possible for canonical address ID keys to be
    // re-used?  SQLite does reuse IDs on NULL id_ insert, but does
    // anything ever delete from the mmssms.db canonical_addresses
    // table?  Nothing that I could find.
    private static final Map<String, String> sRecipientAddress =
            new ConcurrentHashMap<String, String>(20 /* initial capacity */);


    /**
     * MMS address parsing data structures
     */
    // allowable phone number separators
    private static final char[] NUMERIC_CHARS_SUGAR = {
        '-', '.', ',', '(', ')', ' ', '/', '\\', '*', '#', '+'
    };

    /// M: add for ipmessage
    public static final String ACTION_OPEN_GUIDE = "com.mediatek.mms.action.openguide";
    public static final String ACTION_OPEN_GROUP = "com.mediatek.mms.action.opengroup";
    public static final Uri THREAD_SETTINGS_URI = Uri.parse("content://mms-sms/thread_settings/");

    private static HashMap numericSugarMap = new HashMap (NUMERIC_CHARS_SUGAR.length);

    static {
        for (int i = 0; i < NUMERIC_CHARS_SUGAR.length; i++) {
            numericSugarMap.put(NUMERIC_CHARS_SUGAR[i], NUMERIC_CHARS_SUGAR[i]);
        }
    }

    /// M: Code analyze 006, For fix bug ALPS00234739, draft can't be saved
    // after share the edited picture to the same ricipient.Remove old Mms draft
    // in conversation list instead of compose view. @{
    private static Thread mRemoveOldMmsThread;
    /// @}
    /// M: Code analyze 011, For fix bug ALPS00246535, prompt
    // "Please input SD card" after back from attachment list screen. @{
    public static final String SDCARD_1 = "/mnt/sdcard";
    public static final String SDCARD_2 = "/mnt/sdcard2";
    /// @}
    /// M: Code analyze 021, For fix bug ALPS00279524, The "JE" about "MMS"
    // pops up after we launch "Messaging" again. @{
    public static final int NUMBER_OF_RESIZE_ATTEMPTS = 4;
    /// @}
    private static SlideModel slide = null;
    private static String sLocalNumber2;

    private static final String DB_PATH = "/data/data/com.android.providers.telephony/databases/mmssms.db";
    /// M: for showing notification when view mms with video player in full screen model.
    private static final String EXTRA_FULLSCREEN_NOTIFICATION = "mediatek.intent.extra.FULLSCREEN_NOTIFICATION";
    /// M: Action for contact selection intent
    public static final String ACTION_CONTACT_SELECTION = "android.intent.action.contacts.list.PICKMULTIPHONEANDEMAILS";

    /// M: for OP09 @{
    private static IStorageLowExt sStorageLowPlugin;
    private static IMmsPreferenceExt sMmsPreference;
    /// @}
  /// M: fix bug ALPS01523754.set google+ pic as wallpaper.@{
    private static final String TEMP_WALLPAPER = "tempWallpaper";
/// @}
    public static final int SHOW_INVITE_PANEL = 1;
    public static final int UPDATE_SENDBUTTON = 2;
	private static List<SubscriptionInfo> mSubInfoList;

    public static final int JOYN_SUB_ID = 0;
    public static String defaultEncodeType = "Automatic";//add by lipeng for default encodeType
    public static final int[] SubBackgroundLightRes = new int[] {
        com.mediatek.internal.R.drawable.sim_light_blue,
        com.mediatek.internal.R.drawable.sim_light_orange,
        com.mediatek.internal.R.drawable.sim_light_green,
        com.mediatek.internal.R.drawable.sim_light_purple
    };

    private MessageUtils() {
        // Forbidden being instantiated.
    }

    /** M: google jb.mr1 patch
     * cleanseMmsSubject will take a subject that's says, "<Subject: no subject>", and return
     * a null string. Otherwise it will return the original subject string.
     * @param context a regular context so the function can grab string resources
     * @param subject the raw subject
     * @return
     */
    public static String cleanseMmsSubject(Context context, String subject) {
        if (TextUtils.isEmpty(subject)) {
            return subject;
        }
        if (sNoSubjectStrings == null) {
            sNoSubjectStrings =
                    context.getResources().getStringArray(R.array.empty_subject_strings);

        }
        final int len = sNoSubjectStrings.length;
        for (int i = 0; i < len; i++) {
            if (subject.equalsIgnoreCase(sNoSubjectStrings[i])) {
                return null;
            }
        }
        return subject;
    }

    /// M: @{
    /*public static String getMessageDetails(Context context, Cursor cursor, int size) {
        if (cursor == null) {
            return null;
        }

        if ("mms".equals(cursor.getString(MessageListAdapter.COLUMN_MSG_TYPE))) {
            int type = cursor.getInt(MessageListAdapter.COLUMN_MMS_MESSAGE_TYPE);
            switch (type) {
                case PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND:
                    return getNotificationIndDetails(context, cursor);
                case PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF:
                case PduHeaders.MESSAGE_TYPE_SEND_REQ:
                    return getMultimediaMessageDetails(context, cursor, size);
                default:
                    Log.w(TAG, "No details could be retrieved.");
                    return "";
            }
        } else {
            return getTextMessageDetails(context, cursor);
        }
    }*/

    // check if the sub is active.
    public static boolean isSmsSubIdActive(Context context, int subId) {
        List<SubscriptionInfo> subinfoList = SubscriptionManager.from(context).getActiveSubscriptionInfoList();
        for (SubscriptionInfo subInfo : subinfoList) {
            if (subInfo.getSubscriptionId() == subId) {
                return true;
            }
        }
        return false;
    }
 
    /// M: Code analyze 027, new feature, to improve the performance of Mms by Hongduo Wang. @{
    public static String getMessageDetails(Context context, MessageItem msgItem) {
        if (msgItem == null) {
            return null;
        }

        if ("mms".equals(msgItem.mType)) {
            int type = msgItem.mMessageType;
            switch (type) {
                case PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND:
                    return getNotificationIndDetails(context, msgItem);
                case PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF:
                case PduHeaders.MESSAGE_TYPE_SEND_REQ:
                    return getMultimediaMessageDetails(context, msgItem);
                default:
                    Log.w(TAG, "No details could be retrieved.");
                    return "";
            }
        } else {
            return getTextMessageDetails(context, msgItem);
        }
    }
    /// @}

    /// M: @{
    /*private static String getNotificationIndDetails(Context context, Cursor cursor) {
        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();

        long id = cursor.getLong(MessageListAdapter.COLUMN_ID);
        Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, id);
        NotificationInd nInd;

        try {
            nInd = (NotificationInd) PduPersister.getPduPersister(
                    context).load(uri);
        } catch (MmsException e) {
            Log.e(TAG, "Failed to load the message: " + uri, e);
            return context.getResources().getString(R.string.cannot_get_details);
        }

        // Message Type: Mms Notification.
        details.append(res.getString(R.string.message_type_label));
        details.append(res.getString(R.string.multimedia_notification));

        // From: ***
        String from = extractEncStr(context, nInd.getFrom());
        details.append('\n');
        details.append(res.getString(R.string.from_label));
        details.append(!TextUtils.isEmpty(from)? from:
                                 res.getString(R.string.hidden_sender_address));

        // Date: ***
        details.append('\n');
        details.append(res.getString(
                                R.string.expire_on,
                                MessageUtils.formatTimeStampString(
                                        context, nInd.getExpiry() * 1000L, true)));

        // Subject: ***
        details.append('\n');
        details.append(res.getString(R.string.subject_label));

        EncodedStringValue subject = nInd.getSubject();
        if (subject != null) {
            details.append(subject.getString());
        }

        // Message class: Personal/Advertisement/Infomational/Auto
        details.append('\n');
        details.append(res.getString(R.string.message_class_label));
        details.append(new String(nInd.getMessageClass()));

        // Message size: *** KB
        details.append('\n');
        details.append(res.getString(R.string.message_size_label));
        details.append(String.valueOf((nInd.getMessageSize() + 1023) / 1024));
        details.append(context.getString(R.string.kilobyte));

        return details.toString();
    }*/
    /// M: Code analyze 027, new feature, to improve the performance of Mms by Hongduo Wang. @{
    private static String getNotificationIndDetails(Context context, MessageItem msgItem) {
    /// @}
        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();

        /// M: Code analyze 027, new feature, to improve the performance of Mms by Hongduo Wang. @{
        Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, msgItem.mMsgId);
        /// @}
        NotificationInd nInd;

        try {
            /// M: Code analyze 027, new feature, to improve the performance of Mms by Hongduo Wang. @{
            nInd = (NotificationInd) PduPersister.getPduPersister(context).load(uri);
            /// @}
        } catch (MmsException e) {
            Log.e(TAG, "Failed to load the message: " + uri, e);
            return context.getResources().getString(R.string.cannot_get_details);
        }

        // Message Type: Mms Notification.
        details.append(res.getString(R.string.message_type_label));
        details.append(res.getString(R.string.multimedia_notification));

        /// M: Code analyze 026, new feature, show the service center number.
        // @{
        details.append('\n');
        details.append(res.getString(R.string.service_center_label));
        //modify by lipeng
        String info = msgItem.mServiceCenter;
        String langage = Locale.getDefault().getLanguage();//add by lipeng for number display
        if(info!=null && isInteger(info)&&(langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw"))){
        	info = "\u202D" + info + "\u202C";
        }
        details.append(!TextUtils.isEmpty(msgItem.mServiceCenter)? info: "");
        //end modify by lipeng
        /// @}

        // From: ***
        String from = extractEncStr(context, nInd.getFrom());
        details.append('\n');
        details.append(res.getString(R.string.from_label));
		//modify by lipeng at 2015-10-02
		if(from!=null && isInteger(from)&&(langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw"))){
			details.append(!TextUtils.isEmpty(from)? "\u202D" + from + "\u202C":
                                 res.getString(R.string.hidden_sender_address));
		} else {
        	details.append(!TextUtils.isEmpty(from)? from:
                                 res.getString(R.string.hidden_sender_address));
		}//end lipeng

        // Date: ***
        details.append('\n');
        details.append(res.getString(
                                R.string.expire_on,
                                MessageUtils.formatTimeStampString(
                                        context, nInd.getExpiry() * 1000L, true)));

        // Subject: ***
        details.append('\n');
        details.append(res.getString(R.string.subject_label));

        EncodedStringValue subject = nInd.getSubject();
        if (subject != null) {
            details.append(subject.getString());
        }

        // Message class: Personal/Advertisement/Infomational/Auto
        details.append('\n');
        details.append(res.getString(R.string.message_class_label));
        details.append(new String(nInd.getMessageClass()));

        // Message size: *** KB
        details.append('\n');
        details.append(res.getString(R.string.message_size_label));
        details.append(String.valueOf((nInd.getMessageSize() + 1023) / 1024));
        details.append(context.getString(R.string.kilobyte));

        return details.toString();
    }
    /// @}

    /// M: @{
    /// M: Code analyze 027, new feature, to improve the performance of Mms by Hongduo Wang. @{
    private static String getMultimediaMessageDetails(Context context, MessageItem msgItem) {
        if (msgItem.mMessageType == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) {
            return getNotificationIndDetails(context, msgItem);
        }

        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();

        /// M: Code analyze 027, new feature, to improve the performance of Mms by Hongduo Wang. @{
        Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, msgItem.mMsgId);
        /// @}
        MultimediaMessagePdu msg;
        IMmsUtilsExt mmsUtils = (IMmsUtilsExt) MmsPluginManager.getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_MESSAGE_UTILS);
        try {
            msg = (MultimediaMessagePdu) PduPersister.getPduPersister(
                    context).load(uri);
        } catch (MmsException e) {
            Log.e(TAG, "Failed to load the message: " + uri, e);
            return context.getResources().getString(R.string.cannot_get_details);
        }

        // Message Type: Text message.
        initializeMsgDetails(context, details, res, msg);

        // SentDate: ***
        if (msg.getDateSent() > 0
                && msgItem.mBoxId == Mms.MESSAGE_BOX_INBOX) {
            details.append('\n');
            details.append(res.getString(R.string.sent_label));
            String dateStr = "";
            /// M: For OP09 @{
            if (MmsConfig.isFormatDateAndTimeStampEnable()) {
                dateStr = mmsUtils.formatDateAndTimeStampString(context, 0,
                        msg.getDateSent() * 1000L, true, dateStr);
            /// @}
            } else {
                dateStr = MessageUtils.formatTimeStampString(context,
                        msg.getDateSent() * 1000L, true);
            }

            details.append(dateStr);
        }

        // Date: ***
        details.append('\n');
        /// M: Code analyze 027, new feature, to improve the performance of Mms by Hongduo Wang. @{
        int msgBox = msgItem.mBoxId;
        /// @}
        if (msgBox == Mms.MESSAGE_BOX_DRAFTS) {
            details.append(res.getString(R.string.saved_label));
        } else if (msgBox == Mms.MESSAGE_BOX_INBOX) {
            details.append(res.getString(R.string.received_label));
        } else {
            details.append(res.getString(R.string.sent_label));
        }
        String dateStr = "";
        /// M: For OP09 @{
        if (MmsConfig.isFormatDateAndTimeStampEnable()) {
            dateStr = mmsUtils.formatDateAndTimeStampString(context, 0, msg.getDate() * 1000L, true, dateStr);
        /// @}
        } else {
            dateStr = MessageUtils.formatTimeStampString(context, msg.getDate() * 1000L, true);
        }

        details.append(dateStr);

        // Subject: ***
        details.append('\n');
        details.append(res.getString(R.string.subject_label));

        /// M: Code analyze 027, new feature, to improve the performance of Mms by Hongduo Wang. @{
        int size = msgItem.mMessageSize;
        /// @}
        EncodedStringValue subject = msg.getSubject();
        if (subject != null) {
            String subStr = subject.getString();
            // Message size should include size of subject.
            // Modify ALPS00427926. Because message size has
            // include PDU header, so it should not add this size here
            //size += subStr.length();
            details.append(subStr);
        }

        // Priority: High/Normal/Low
        return formatDetails(details, context, msg, size, res);
    }

    private static void initializeMsgDetails(Context context,
            StringBuilder details, Resources res, MultimediaMessagePdu msg) {
        details.append(res.getString(R.string.message_type_label));
        details.append(res.getString(R.string.multimedia_message));
        String langage = Locale.getDefault().getLanguage();//add by lipeng for number display
        if (msg instanceof RetrieveConf) {
            // From: ***
            String from = extractEncStr(context, ((RetrieveConf) msg).getFrom());
            details.append('\n');
            details.append(res.getString(R.string.from_label));
            //add by lipeng 
            if (from!=null && MessageUtils.isInteger(from)&&(langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw"))){
            	from = "\u202D"+from+"\u202C";
            }//end by lipeng
            details.append(!TextUtils.isEmpty(from)? from:
                                  res.getString(R.string.hidden_sender_address));
        }

        // To: ***
        details.append('\n');
        details.append(res.getString(R.string.to_address_label));
        EncodedStringValue[] to = msg.getTo();
        if (to != null) {
        	//add by lipeng
        	String info = EncodedStringValue.concat(to);
        	if(info!=null && MessageUtils.isInteger(info)&&(langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw"))){
        		info = "\u202D"+info+"\u202C";
        	}//end by lipeng
            details.append(info);
        }
        else {
            Log.w(TAG, "recipient list is empty!");
        }

        // Bcc: ***
        if (msg instanceof SendReq) {
            EncodedStringValue[] values = ((SendReq) msg).getBcc();
            if ((values != null) && (values.length > 0)) {
                details.append('\n');
                details.append(res.getString(R.string.bcc_label));
                details.append(EncodedStringValue.concat(values));
            }
        }
    }

    /// @}

    /// M: @{
    /*private static String getTextMessageDetails(Context context, Cursor cursor) {
        Log.d(TAG, "getTextMessageDetails");

        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();

        // Message Type: Text message.
        details.append(res.getString(R.string.message_type_label));
        details.append(res.getString(R.string.text_message));

        // Address: ***
        details.append('\n');
        int smsType = cursor.getInt(MessageListAdapter.COLUMN_SMS_TYPE);
        if (Sms.isOutgoingFolder(smsType)) {
            details.append(res.getString(R.string.to_address_label));
        } else {
            details.append(res.getString(R.string.from_label));
        }
        details.append(cursor.getString(MessageListAdapter.COLUMN_SMS_ADDRESS));

        // Sent: ***
        if (smsType == Sms.MESSAGE_TYPE_INBOX) {
            long date_sent = cursor.getLong(MessageListAdapter.COLUMN_SMS_DATE_SENT);
            if (date_sent > 0) {
                details.append('\n');
                details.append(res.getString(R.string.sent_label));
                details.append(MessageUtils.formatTimeStampString(context, date_sent, true));
            }
        }

        // Received: ***
        details.append('\n');
        if (smsType == Sms.MESSAGE_TYPE_DRAFT) {
            details.append(res.getString(R.string.saved_label));
        } else if (smsType == Sms.MESSAGE_TYPE_INBOX) {
            details.append(res.getString(R.string.received_label));
        } else {
            details.append(res.getString(R.string.sent_label));
        }

        long date = cursor.getLong(MessageListAdapter.COLUMN_SMS_DATE);
        details.append(MessageUtils.formatTimeStampString(context, date, true));

        // Error code: ***
        int errorCode = cursor.getInt(MessageListAdapter.COLUMN_SMS_ERROR_CODE);
        if (errorCode != 0) {
            details.append('\n')
                .append(res.getString(R.string.error_code_label))
                .append(errorCode);
        }

        return details.toString();
    }*/
    
	//add by lipeng for langruage at 2015-10-02
		public static boolean isInteger(String str) {
			if(str==null){
				return false;
			}
			str = str.replaceAll(" ", "").trim();
			Pattern pattern = Pattern.compile("^[-\\+]?[\\d]*$");	  
			return pattern.matcher(str).matches();	  
		}//end by lipeng
    /// M: Code analyze 027, new feature, to improve the performance of Mms by Hongduo Wang. @{
    private static String getTextMessageDetails(Context context, MessageItem msgItem) {
        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();
        String langage = Locale.getDefault().getLanguage();//add by lipeng for number display
        // Message Type: Text message.
        details.append(res.getString(R.string.message_type_label));
        /// M: modify for ipmessage
        if (msgItem.mIpMessageId > 0) {
            details.append(IpMessageUtils.getResourceManager(context).getSingleString(IpMessageConsts.string.ipmsg_ipmsg));
        } else {
            details.append(res.getString(R.string.text_message));
        }

        // Address: ***
        details.append('\n');
        /// M: Code analyze 027, new feature, to improve the performance of Mms by Hongduo Wang. @{
        int smsType = msgItem.mBoxId;
        /// @}
        if (Sms.isOutgoingFolder(smsType)) {
            details.append(res.getString(R.string.to_address_label));
        } else {
            details.append(res.getString(R.string.from_label));
        }
        /// M: Code analyze 027, new feature, to improve the performance of Mms by Hongduo Wang. @{

        // / M: Add for ip message converaged inbox @{
        if (IpMessageUtils.getServiceManager(context).getIntegrationMode() == IpMessageConsts.IntegrationMode.CONVERGED_INBOX
                && msgItem.mAddress.startsWith(IpMessageConsts.JOYN_START)) {
            details.append(msgItem.mAddress.substring(4));
        } else {
			//modify by lipeng for langruage at 2015-10-02
			if(isInteger(msgItem.mAddress)&&(Locale.getDefault().getLanguage().equals("ar") || Locale.getDefault().getLanguage().equals("fa")|| Locale.getDefault().getLanguage().equals("iw"))){
				details.append("\u202D" + msgItem.mAddress + "\u202C");
			} else {
	        	details.append(msgItem.mAddress);
			}//end by lipeng
        }
        /// @}

        // / @}
        /// M: For OP09 @{
        MultimediaMessagePdu msg;
        IMmsUtilsExt mmsUtils = (IMmsUtilsExt) MmsPluginManager.getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_MESSAGE_UTILS);
        /// @}
        // SentDate: ***
        if (msgItem.mSmsSentDate > 0 && smsType == Sms.MESSAGE_TYPE_INBOX) {
            details.append('\n');
            details.append(res.getString(R.string.sent_label));
            String dateStr = "";
            /// M: For OP09 @{
            if (MmsConfig.isFormatDateAndTimeStampEnable()) {
                dateStr = mmsUtils.formatDateAndTimeStampString(context, 0, msgItem.mSmsSentDate, true, dateStr);
            /// @}
            } else {
                dateStr = MessageUtils.formatTimeStampString(context, msgItem.mSmsSentDate, true);
            }

            details.append(dateStr);
        }

        /// M: Code analyze 027, new feature, to improve the performance of Mms by Hongduo Wang. @{
        if (msgItem.mSmsDate > 0L) {
            details.append('\n');
            if (smsType == Sms.MESSAGE_TYPE_DRAFT) {
                details.append(res.getString(R.string.saved_label));
            } else if (smsType == Sms.MESSAGE_TYPE_INBOX) {
                details.append(res.getString(R.string.received_label));
            } else {
                details.append(res.getString(R.string.sent_label));
            }
            String dateStr = "";
            /// M: For OP09 @{
            if (MmsConfig.isFormatDateAndTimeStampEnable()) {
                dateStr = mmsUtils.formatDateAndTimeStampString(context, 0, msgItem.mSmsDate, true, dateStr);
            /// @}
            } else {
                dateStr = MessageUtils.formatTimeStampString(context, msgItem.mSmsDate, true);
            }

            details.append(dateStr);
        }
        /// @}

        /// M: Code analyze 028, new feature, add service center in Sms details. @{
        if (smsType == Sms.MESSAGE_TYPE_INBOX) {
            details.append('\n');
            details.append(res.getString(R.string.service_center_label));
            //modify by lipeng for langruage at 2015-10-02
			if(!TextUtils.isEmpty(msgItem.mServiceCenter)&&isInteger(msgItem.mServiceCenter)&&(Locale.getDefault().getLanguage().equals("ar") || Locale.getDefault().getLanguage().equals("fa")|| Locale.getDefault().getLanguage().equals("iw"))){
				details.append(msgItem.mServiceCenter == null ? "" : "\u202D" + msgItem.mServiceCenter + "\u202C");
			} else {
	        	details.append(msgItem.mServiceCenter == null ? "" : msgItem.mServiceCenter);
			}//end by lipeng
        }
        /// @}

        // Delivered: ***
        if (smsType == Sms.MESSAGE_TYPE_SENT) {
            // For sent messages with delivery reports, we stick the delivery time in the
            // date_sent column (see MessageStatusReceiver).
            /// M: long dateDelivered = cursor.getLong(MessageListAdapter.COLUMN_SMS_DATE_SENT);
        /// M: Code analyze 027, new feature, to improve the performance of Mms by Hongduo Wang. @{
            long dateDelivered = -1L;
            if (dateDelivered > 0) {
                details.append('\n');
                details.append(res.getString(R.string.delivered_label));
                details.append(MessageUtils.formatTimeStampString(context, dateDelivered, true));
            }
        }

        // Error code: ***
        int errorCode = msgItem.mErrorCode;
        /// @}
        if (errorCode != 0) {
            details.append('\n')
                .append(res.getString(R.string.error_code_label))
                .append(errorCode);
        }

        return details.toString();
    }
    /// @}

    static private String getPriorityDescription(Context context, int PriorityValue) {
        Resources res = context.getResources();
        switch(PriorityValue) {
            case PduHeaders.PRIORITY_HIGH:
                return res.getString(R.string.priority_high);
            case PduHeaders.PRIORITY_LOW:
                return res.getString(R.string.priority_low);
            case PduHeaders.PRIORITY_NORMAL:
            default:
                return res.getString(R.string.priority_normal);
        }
    }

    public static int getAttachmentType(SlideshowModel model, MultimediaMessagePdu mmp) {
        if (model == null || mmp == null) {
            return MessageItem.ATTACHMENT_TYPE_NOT_LOADED;
        }

        int numberOfSlides = model.size();
        if (numberOfSlides > 1) {
            return WorkingMessage.SLIDESHOW;
        } else if (numberOfSlides == 1) {
            // Only one slide in the slide-show.
            SlideModel slide = model.get(0);
            if (slide.hasVideo()) {
                return WorkingMessage.VIDEO;
            }

            if (slide.hasAudio() && slide.hasImage()) {
                return WorkingMessage.SLIDESHOW;
            }

            if (slide.hasAudio()) {
                return WorkingMessage.AUDIO;
            }

            if (slide.hasImage()) {
                return WorkingMessage.IMAGE;
            }

            /// M: Code analyze 004, For fix bug ALPS00242740, can't see the
            // select all/cut/copy icon. @{
            if (model.sizeOfFilesAttach() > 0) {
                return WorkingMessage.ATTACHMENT;
            }
            /// @}

            if (slide.hasText()) {
                return WorkingMessage.TEXT;
            }

            String subject = mmp.getSubject() != null ? mmp.getSubject().getString() : null;
            if (!TextUtils.isEmpty(subject)) {
                return WorkingMessage.TEXT;
            }
        }

        if (model.sizeOfFilesAttach() > 0) {
            return WorkingMessage.ATTACHMENT;
        }

        return MessageItem.ATTACHMENT_TYPE_NOT_LOADED;
    }

    public static String formatTimeStampString(Context context, long when) {
        return formatTimeStampString(context, when, false);
    }

    public static String formatTimeStampString(Context context, long when, boolean fullFormat) {
        Time then = new Time();
        then.set(when);
        Time now = new Time();
        now.setToNow();

        // Basic settings for formatDateTime() we want for all cases.
        int format_flags = DateUtils.FORMAT_NO_NOON_MIDNIGHT |
        /// M: Fix ALPS00419488 to show 12:00, so mark DateUtils.FORMAT_ABBREV_ALL
                           //DateUtils.FORMAT_ABBREV_ALL |
                           DateUtils.FORMAT_CAP_AMPM;

        // If the message is from a different year, show the date and year.
        if (then.year != now.year) {
            format_flags |= DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_DATE;
        } else if (then.yearDay != now.yearDay) {
            // If it is from a different day than today, show only the date.
            format_flags |= DateUtils.FORMAT_SHOW_DATE;
        } else {
            // Otherwise, if the message is from today, show the time.
            format_flags |= DateUtils.FORMAT_SHOW_TIME;
        }

        // If the caller has asked for full details, make sure to show the date
        // and time no matter what we've determined above (but still make showing
        // the year only happen if it is a different year from today).
        if (fullFormat) {
            format_flags |= (DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME);
        }
        /// M: For OP09 @{
        if (MmsConfig.isFormatDateAndTimeStampEnable()) {
            IMmsUtilsExt mmsUtils = (IMmsUtilsExt) MmsPluginManager
                    .getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_MESSAGE_UTILS);
            return mmsUtils.formatDateTime(context, when, format_flags);
        }
        /// @}
        return DateUtils.formatDateTime(context, when, format_flags);
    }

    public static void selectAudio(Activity activity, int requestCode) {
        /// M: Code analyze 027, new feature, to improve the performance of Mms by Hongduo Wang. @{
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(MmsContentType.AUDIO_UNSPECIFIED);
        String[] mimeTypess = new String[] {MmsContentType.AUDIO_UNSPECIFIED,
                MmsContentType.AUDIO_OGG, "application/x-ogg"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypess);
        if (FeatureOption.MTK_DRM_APP) {
            intent.putExtra(OmaDrmStore.DrmExtra.EXTRA_DRM_LEVEL,
                    OmaDrmStore.DrmExtra.LEVEL_SD);
        }
        /// @}
        activity.startActivityForResult(intent, requestCode);
    }

    public static void recordSound(Activity activity, int requestCode, long sizeLimit) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType(MmsContentType.AUDIO_AMR);
            intent.setClassName("com.android.soundrecorder",
                    "com.android.soundrecorder.SoundRecorder");
            intent.putExtra(android.provider.MediaStore.Audio.Media.EXTRA_MAX_BYTES, sizeLimit);
        activity.startActivityForResult(intent, requestCode);
    }

    public static void recordVideo(Activity activity, int requestCode, long sizeLimit) {
        // The video recorder can sometimes return a file that's larger than the max we
        // say we can handle. Try to handle that overshoot by specifying an 85% limit.
        /// M: media recoder can handle this issue,so mark it.
//        sizeLimit *= .85F;

            int durationLimit = getVideoCaptureDurationLimit();

        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("recordVideo: durationLimit: " + durationLimit +
                    " sizeLimit: " + sizeLimit);
        }

            Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
            intent.putExtra("android.intent.extra.sizeLimit", sizeLimit);
            intent.putExtra("android.intent.extra.durationLimit", 0/*durationLimit*/);
            /// M: Code analyze 009, For fix bug ALPS00241707, You can not add
            // capture video to Messaging after you preview it in Gallery. @{
            intent.putExtra(MediaStore.EXTRA_OUTPUT, TempFileProvider.SCRAP_VIDEO_URI);
            /// M: fix bug ALPS01043585
            intent.putExtra("CanShare", false);
            activity.startActivityForResult(intent, requestCode);
    }

    public static void capturePicture(Activity activity, int requestCode) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, TempFileProvider.SCRAP_CONTENT_URI);
        activity.startActivityForResult(intent, requestCode);
    }

    public static int getVideoCaptureDurationLimit() {
        CamcorderProfile camcorder = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
        return camcorder == null ? 0 : camcorder.duration;
    }

    public static void selectVideo(Context context, int requestCode) {
        selectMediaByType(context, requestCode, MmsContentType.VIDEO_UNSPECIFIED, true);
    }

    public static void selectImage(Context context, int requestCode) {
        selectMediaByType(context, requestCode, MmsContentType.IMAGE_UNSPECIFIED, false);
    }

    private static void selectMediaByType(
            Context context, int requestCode, String contentType, boolean localFilesOnly) {
         if (context instanceof Activity) {

            Intent innerIntent = new Intent(Intent.ACTION_GET_CONTENT);

            innerIntent.setType(contentType);
            /// M: @{
            if (FeatureOption.MTK_DRM_APP) {
                innerIntent.putExtra(OmaDrmStore.DrmExtra.EXTRA_DRM_LEVEL,
                        OmaDrmStore.DrmExtra.LEVEL_SD);
            }
            /// @}
            if (localFilesOnly) {
                innerIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            }

            Intent wrapperIntent = Intent.createChooser(innerIntent, null);
            ((Activity) context).startActivityForResult(wrapperIntent, requestCode);
        }
    }

    public static void viewSimpleSlideshow(Context context, SlideshowModel slideshow) {
        if (!slideshow.isSimple()) {
            throw new IllegalArgumentException(
                    "viewSimpleSlideshow() called on a non-simple slideshow");
        }
        SlideModel slide = slideshow.get(0);
        MediaModel mm = null;
        if (slide.hasImage()) {
            mm = slide.getImage();
        } else if (slide.hasVideo()) {
            mm = slide.getVideo();
        } else if (slide.hasAudio()) {
            mm = slide.getAudio();
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra("SingleItemOnly", true); // So we don't see "surrounding" images in Gallery
        /// M: CanShare: false, Hide the videopalye's share option menu.
        /// M: CanShare: ture, Show the share option menu.
        /// M: Code analyze 010, For fix bug ALPS00244046, The "JE" pops up
        // after you tap the "messaging" icon. @{
        intent.putExtra("CanShare", false);
        /// @}
        /// M: for showing notification when view mms with video player in full screen model @{
        intent.putExtra(EXTRA_FULLSCREEN_NOTIFICATION, true);
        /// @}

        String contentType = "";
        if (mm != null) {
            contentType = mm.getContentType();
            MmsLog.e(TAG, "viewSimpleSildeshow. Uri:" + mm.getUri());
            MmsLog.e(TAG, "viewSimpleSildeshow. contentType:" + contentType);
            intent.setDataAndType(mm.getUri(), contentType);
        }
        /// M: Code analyze 013, For fix bug ALPS00250939, Exception/Java(JE)-->com.android.mms.
        try {
            // M: change feature ALPS01751464
            if (mm != null && mm.hasDrmContent()) {
                MessageUtils.showDrmAlertDialog(context);
                return;
            }

            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Message msg = Message.obtain(MmsApp.getToastHandler());
            msg.what = MmsApp.MSG_MMS_CAN_NOT_OPEN;
            msg.obj = contentType;
            msg.sendToTarget();
            /// M: after user click view, and the error toast is shown,
            /// we must make it can press again. tricky code
            if (context instanceof ComposeMessageActivity) {
                ((ComposeMessageActivity) context).mClickCanResponse = true;
            }
        }
        /// @}
    }

    /// M: Code analyze 025, For fix bug ALPS00298363, The "JE" pops up
    // after you launch message again. @{
    public static void showErrorDialog(Activity activity,
            int titleId, int messageId, int mediaTypeIdForTitle, int mediaTypeIdForMsg) {
        /** M:
            the original code is replaced by the following code.
            the original code has a bug, JE may happen.
            the case is when the activity is destoried by some reason(ex:change language),
            when dismiss the dialog, it may be throw a JE. see 298363.
        */
        ErrorDialog errDialog = ErrorDialog.newInstance(titleId, messageId,
                mediaTypeIdForTitle, mediaTypeIdForMsg);
        try {
            errDialog.show(activity.getFragmentManager(), "errDialog");
        } catch (IllegalStateException e) {
            try {
                MmsLog.d(TAG, "showErrorDialog catch IllegalStateException." + e);
                FragmentTransaction transaction = activity.getFragmentManager().beginTransaction();
                transaction.add(errDialog, "errDialog");
                transaction.commitAllowingStateLoss();
            } catch (Exception e2) {
                MmsLog.d(TAG, "showErrorDialog commitAllowingStateLoss catch Exception." + e2);
            }
            return;
        }
    }
    /// @}

    /**
     * The quality parameter which is used to compress JPEG images.
     */
    public static final int IMAGE_COMPRESSION_QUALITY = 95;
    /**
     * The minimum quality parameter which is used to compress JPEG images.
     */
    /// M: Modify for ALPS00778930
    public static final int MINIMUM_IMAGE_COMPRESSION_QUALITY = 10;

    public static final int MAX_COMPRESS_TIMES = 8;

    /**
     * Message overhead that reduces the maximum image byte size.
     * 5000 is a realistic overhead number that allows for user to also include
     * a small MIDI file or a couple pages of text along with the picture.
     */
    public static final int MESSAGE_OVERHEAD = 5000;

    public static void resizeImageAsync(final Context context,
            final Uri imageUri, final Handler handler,
            final ResizeImageResultCallback cb,
            final boolean append) {

        // Show a progress toast if the resize hasn't finished
        // within one second.
        // Stash the runnable for showing it away so we can cancel
        // it later if the resize completes ahead of the deadline.
        final Runnable showProgress = new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, R.string.compressing, Toast.LENGTH_SHORT).show();
            }
        };
        // Schedule it for one second from now.
        handler.postDelayed(showProgress, 1000);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final PduPart part;
                try {
                    UriImage image = new UriImage(context, imageUri);
                    int widthLimit = MmsConfig.getMaxImageWidth();
                    int heightLimit = MmsConfig.getMaxImageHeight();
                    // In mms_config.xml, the max width has always been declared larger than the max
                    // height. Swap the width and height limits if necessary so we scale the picture
                    // as little as possible.
                    if (image.getHeight() > image.getWidth()) {
                        int temp = widthLimit;
                        widthLimit = heightLimit;
                        heightLimit = temp;
                    }

                    /// M: Code analyze 027, new feature, to improve the performance of Mms by Hongduo Wang. @{
                    /*part = image.getResizedImageAsPart(
                        widthLimit,
                        heightLimit,
                        MmsConfig.getMaxMessageSize() - MESSAGE_OVERHEAD);
                    */
                    part = image.getResizedImageAsPart(
                            widthLimit,
                            heightLimit,
                            MmsConfig.getUserSetMmsSizeLimit(true) - MESSAGE_OVERHEAD);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onResizeResult(part, append);
                        }
                    });
                } catch (IllegalArgumentException e) {
                    MmsLog.e(TAG, "Unexpected IllegalArgumentException.", e);
                } finally {
                    /// M: Cancel pending show of the progress toast if necessary.
                    handler.removeCallbacks(showProgress);
                }
                /// @}
            }
        }, "MessageUtils.resizeImageAsync").start();
    }

    /// M: Code analyze 003, For fix bug ALPS00113244, It adds a blank slide
    // when share multi folders from Gallery. @{
    public static void resizeImage(final Context context, final Uri imageUri, final Handler handler,
            final ResizeImageResultCallback cb, final boolean append, boolean showToast) {

        /** M: Show a progress toast if the resize hasn't finished
         * within one second.
         * Stash the runnable for showing it away so we can cancel
         * it later if the resize completes ahead of the deadline.
        */
        final Runnable showProgress = new Runnable() {
            public void run() {
                Toast.makeText(context, R.string.compressing, Toast.LENGTH_SHORT).show();
            }
        };
        if (showToast) {
            handler.post(showProgress);
//          handler.postDelayed(showProgress, 1000);
        }
        final PduPart part;
        try {
            UriImage image = new UriImage(context, imageUri);
            part = image.getResizedImageAsPart(MmsConfig.getMaxImageWidth(),
                    MmsConfig.getMaxImageHeight(), MmsConfig.getUserSetMmsSizeLimit(true)
                    - MESSAGE_OVERHEAD);
            cb.onResizeResult(part, append);
        } catch (IllegalArgumentException e) {
            MmsLog.e(TAG, "Unexpected IllegalArgumentException.", e);
        } finally {
            /// M: Cancel pending show of the progress toast if necessary.
            handler.removeCallbacks(showProgress);
        }
    }

    /// @}
    public static void showDiscardDraftConfirmDialog(Context context,
            OnClickListener listener) {
        String discardMessageYes = context.getResources().getString(R.string.yes);
        String discardMessageNo = context.getResources().getString(R.string.no);
        if(context.getResources().getConfiguration().locale.getCountry().equals("RU"))
        {
            discardMessageYes = context.getResources().getString(R.string.discard_message_yes);
            discardMessageNo = context.getResources().getString(R.string.discard_message_no);
        }
        new AlertDialog.Builder(context)
                /// M: Code analyze 008, new feature, Android4.1 has moved this
                // icon and title. @{
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.discard_message)
                /// @}
                .setMessage(R.string.discard_message_reason)
                .setPositiveButton(discardMessageYes, listener)
                .setNegativeButton(discardMessageNo, null)
                .show();
    }

    /// M: we[mtk] do not support reply read report when deleteing without read.
    public static void handleReadReport(final Context context,
            final Collection<Long> threadIds,
            final int status,
            final Runnable callback) {
//        StringBuilder selectionBuilder = new StringBuilder(Mms.MESSAGE_TYPE + " = "
//                + PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF
//                + " AND " + Mms.READ + " = 0"
//                + " AND " + Mms.READ_REPORT + " = " + PduHeaders.VALUE_YES);
//
//        String[] selectionArgs = null;
//        if (threadIds != null) {
//            String threadIdSelection = null;
//            StringBuilder buf = new StringBuilder();
//            selectionArgs = new String[threadIds.size()];
//            int i = 0;
//
//            for (long threadId : threadIds) {
//                if (i > 0) {
//                    buf.append(" OR ");
//                }
//                buf.append(Mms.THREAD_ID).append("=?");
//                selectionArgs[i++] = Long.toString(threadId);
//            }
//            threadIdSelection = buf.toString();
//
//            selectionBuilder.append(" AND (" + threadIdSelection + ")");
//        }
//
//        final Cursor c = SqliteWrapper.query(context, context.getContentResolver(),
//                        Mms.Inbox.CONTENT_URI, new String[] {Mms._ID, Mms.MESSAGE_ID},
//                        selectionBuilder.toString(), selectionArgs, null);
//
//        if (c == null) {
//            return;
//        }
//
//        final Map<String, String> map = new HashMap<String, String>();
//        try {
//            if (c.getCount() == 0) {
//                if (callback != null) {
//                    callback.run();
//                }
//                return;
//            }
//
//            while (c.moveToNext()) {
//                Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, c.getLong(0));
//                map.put(c.getString(1), AddressUtils.getFrom(context, uri));
//            }
//        } finally {
//            c.close();
//        }
        // we[mtk] do not support reply read report when deleteing without read.
        // the underlayer code[ReadRecTransaction.java] is modified to support another branch.
        if (callback != null) {
            callback.run();
        }
        /// M: Code analyze 016, For fix bug ALPS00274375, remove the entry of
        // sending read report when deleting mms without read. @{
        /** M:
        OnClickListener positiveListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                for (final Map.Entry<String, String> entry : map.entrySet()) {
                    MmsMessageSender.sendReadRec(context, entry.getValue(),
                                                 entry.getKey(), status);
                }

                if (callback != null) {
                    callback.run();
                }
                dialog.dismiss();
            }
        };

        OnClickListener negativeListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (callback != null) {
                    callback.run();
                }
                dialog.dismiss();
            }
        };

        OnCancelListener cancelListener = new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (callback != null) {
                    callback.run();
                }
                dialog.dismiss();
            }
        };

        confirmReadReportDialog(context, positiveListener,
                                         negativeListener,
                                         cancelListener);
        */
        /// @}
    }

    private static void confirmReadReportDialog(Context context,
            OnClickListener positiveListener, OnClickListener negativeListener,
            OnCancelListener cancelListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(true);
        builder.setTitle(R.string.confirm);
        builder.setMessage(R.string.message_send_read_report);
        builder.setPositiveButton(R.string.yes, positiveListener);
        builder.setNegativeButton(R.string.no, negativeListener);
        builder.setOnCancelListener(cancelListener);
        builder.show();
    }

    public static String extractEncStrFromCursor(Cursor cursor,
            int columnRawBytes, int columnCharset) {
        String rawBytes = cursor.getString(columnRawBytes);
        int charset = cursor.getInt(columnCharset);

        if (TextUtils.isEmpty(rawBytes)) {
            return "";
        } else if (charset == CharacterSets.ANY_CHARSET) {
            return rawBytes;
        } else {
            return new EncodedStringValue(charset, PduPersister.getBytes(rawBytes)).getString();
        }
    }

    private static String extractEncStr(Context context, EncodedStringValue value) {
        if (value != null) {
            return value.getString();
        } else {
            Log.d(TAG, "extractEncStr EncodedStringValue is null");
            return "";
        }
    }

    public static ArrayList<String> extractUris(URLSpan[] spans) {
        int size = spans.length;
        ArrayList<String> accumulator = new ArrayList<String>();

        for (int i = 0; i < size; i++) {
            /// M: Code analyze 027, new feature, to improve the performance of Mms by Hongduo Wang. @{
            if (!accumulator.contains(spans[i].getURL())) {
                accumulator.add(spans[i].getURL());
            }
            /// @
        }
        return accumulator;
    }

    /**
     * Play/view the message attachments.
     * TOOD: We need to save the draft before launching another activity to view the attachments.
     *       This is hacky though since we will do saveDraft twice and slow down the UI.
     *       We should pass the slideshow in intent extra to the view activity instead of
     *       asking it to read attachments from database.
     * @param activity
     * @param msgUri the MMS message URI in database
     * @param slideshow the slideshow to save
     * @param persister the PDU persister for updating the database
     * @param sendReq the SendReq for updating the database
     */
    public static void viewMmsMessageAttachment(Activity activity, Uri msgUri,
            SlideshowModel slideshow, AsyncDialog asyncDialog) {
        viewMmsMessageAttachment(activity, msgUri, slideshow, 0, asyncDialog);
    }


    public static void viewMmsMessageAttachment(final Activity activity, final Uri msgUri,
            final SlideshowModel slideshow, final int requestCode, AsyncDialog asyncDialog) {
        /// M: Code analyze 002, For fix bug ALPS00112553, system-server JE
        // happens and MS reboot when tap play in MMS. @{
        final boolean isSimple = (slideshow == null) ? false : slideshow.isSimple();

        if (isSimple) {
            SlideModel slideTemp = slideshow.get(0);
            // In attachment-editor mode, we only ever have one slide.
            /// M: fix bug ALPS00393187, play in gellay when simple slide only has picture or video
            if (slideTemp != null && !slideTemp.hasAudio()
                    && (!slideTemp.hasText() || slideTemp.getText().getText().length() == 0)) {
                MessageUtils.viewSimpleSlideshow(activity, slideshow);
                return;
            }
        }
        /// @}
        // M: change feature ALPS01751464
        if (isSimple) {
            SlideModel slideOne = slideshow.get(0);
            if (slideOne != null && slideOne.hasAudio()) {
                MediaModel model = slideOne.getAudio();
                if (model != null && model.hasDrmContent()) {
                    MessageUtils.showDrmAlertDialog(activity);
                    return;
                }
            }
        }

        // The user wants to view the slideshow. We have to persist the slideshow parts
        // in a background task. If the task takes longer than a half second, a progress dialog
        // is displayed. Once the PDU persisting is done, another runnable on the UI thread get
        // executed to start the SlideshowActivity.
        asyncDialog.runAsync(new Runnable() {
            @Override
            public void run() {
                // If a slideshow was provided, save it to disk first.
                if (slideshow != null) {
                    PduPersister persister = PduPersister.getPduPersister(activity);
                    try {
                        PduBody pb = slideshow.toPduBody();
                        MessageUtils.updatePartsIfNeeded(slideshow, persister, msgUri, pb, null);
                        //persister.updateParts(msgUri, pb, null);
                        slideshow.sync(pb);
                    } catch (MmsException e) {
                        Log.e(TAG, "Unable to save message for preview");
                        return;
                    }
                    slide = slideshow.get(0);
                }
            }
        } , new Runnable() {
            @Override
            public void run() {
             // Launch the slideshow activity to play/view.
                Intent intent;
                if ((isSimple && slide.hasAudio()) || (requestCode == AttachmentEditor.MSG_PLAY_AUDIO)) { //play the only audio directly
                    intent = new Intent(activity.getApplicationContext(), SlideshowActivity.class);
                } else {
                    intent = new Intent(activity.getApplicationContext(), MmsPlayerActivity.class);
                }
                intent.setData(msgUri);
                if (requestCode > 0) {
                    activity.startActivityForResult(intent, requestCode);
                } else {
                    activity.startActivity(intent);
                }
//                    // Once the above background thread is complete, this runnable is run
//                    // on the UI thread to launch the slideshow activity.
//                    launchSlideshowActivity(activity, msgUri, requestCode);

            }
        }, R.string.building_slideshow_title);

    }

    public static void launchSlideshowActivity(Context context, Uri msgUri, int requestCode) {
        // Launch the slideshow activity to play/view.
        Intent intent = new Intent(context, SlideshowActivity.class);
        intent.setData(msgUri);
        if (requestCode > 0 && context instanceof Activity) {
            ((Activity)context).startActivityForResult(intent, requestCode);
        } else {
            context.startActivity(intent);
        }
    }

    /**
     * Debugging
     */
    public static void writeHprofDataToFile(){
        String filename = Environment.getExternalStorageDirectory() + "/mms_oom_hprof_data";
        try {
            android.os.Debug.dumpHprofData(filename);
            Log.i(TAG, "##### written hprof data to " + filename);
        } catch (IOException ex) {
            Log.e(TAG, "writeHprofDataToFile: caught " + ex);
        }
    }

    // An alias (or commonly called "nickname") is:
    // Nickname must begin with a letter.
    // Only letters a-z, numbers 0-9, or . are allowed in Nickname field.
    public static boolean isAlias(String string) {
        if (!MmsConfig.isAliasEnabled()) {
            return false;
        }

        int len = string == null ? 0 : string.length();

        if (len < MmsConfig.getAliasMinChars() || len > MmsConfig.getAliasMaxChars()) {
            return false;
        }

        if (!Character.isLetter(string.charAt(0))) {    // Nickname begins with a letter
            return false;
        }
        for (int i = 1; i < len; i++) {
            char c = string.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '.')) {
                return false;
            }
        }

        return true;
    }

    /**
     * Given a phone number, return the string without syntactic sugar, meaning parens,
     * spaces, slashes, dots, dashes, etc. If the input string contains non-numeric
     * non-punctuation characters, return null.
     */
    private static String parsePhoneNumberForMms(String address) {
        StringBuilder builder = new StringBuilder();
        int len = address.length();

        for (int i = 0; i < len; i++) {
            char c = address.charAt(i);

            // accept the first '+' in the address
            if (c == '+' && builder.length() == 0) {
                builder.append(c);
                continue;
            }

            if (Character.isDigit(c)) {
                builder.append(c);
                continue;
            }

            if (numericSugarMap.get(c) == null) {
                return null;
            }
        }
        return builder.toString();
    }

    /**
     * Returns true if the address passed in is a valid MMS address.
     */
    public static boolean isValidMmsAddress(String address) {
        String retVal = parseMmsAddress(address);
        /// M: @{
        //return (retVal != null);
        return (retVal != null && !retVal.equals(""));
        /// @}
    }

    /**
     * parse the input address to be a valid MMS address.
     * - if the address is an email address, leave it as is.
     * - if the address can be parsed into a valid MMS phone number, return the parsed number.
     * - if the address is a compliant alias address, leave it as is.
     */
    public static String parseMmsAddress(String address) {
        // if it's a valid Email address, use that.
        if (Mms.isEmailAddress(address)) {
            return address;
        }

        // if we are able to parse the address to a MMS compliant phone number, take that.
        String retVal = parsePhoneNumberForMms(address);
        if (retVal != null) {
            return retVal;
        }

        // if it's an alias compliant address, use that.
        if (isAlias(address)) {
            return address;
        }

        // it's not a valid MMS address, return null
        return null;
    }

    private static void log(String msg) {
        Log.d(TAG, "[MsgUtils] " + msg);
    }

    /// M: @{
    public static Uri saveBitmapAsPart(Context context, Uri messageUri, Bitmap bitmap)
        throws MmsException {

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        bitmap.compress(CompressFormat.JPEG, IMAGE_COMPRESSION_QUALITY, os);

        PduPart part = new PduPart();

        part.setContentType("image/jpeg".getBytes());
        String contentId = "Image" + System.currentTimeMillis();
        part.setContentLocation((contentId + ".jpg").getBytes());
        part.setContentId(contentId.getBytes());
        part.setData(os.toByteArray());

        Uri retVal = PduPersister.getPduPersister(context).persistPart(part,
                            ContentUris.parseId(messageUri), null);

        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("saveBitmapAsPart: persisted part with uri=" + retVal);
        }

        return retVal;
    }

    public static String getLocalNumber() {
        return MmsApp.getApplication().getTelephonyManager().getLine1Number();
    }

    public static String getLocalNumber(int subId) {
        return MmsApp.getApplication().getTelephonyManager().getLine1NumberForSubscriber(subId);
    }

    public static boolean isLocalNumber(String number) {
        if (number == null) {
            return false;
        }

        // we don't use Mms.isEmailAddress() because it is too strict for comparing addresses like
        // "foo+caf_=6505551212=tmomail.net@gmail.com", which is the 'from' address from a forwarded email
        // message from Gmail. We don't want to treat "foo+caf_=6505551212=tmomail.net@gmail.com" and
        // "6505551212" to be the same.
        if (number.indexOf('@') >= 0) {
            return false;
        }

        List<SubscriptionInfo> subInfoList;
        subInfoList = SubscriptionManager.from(MmsApp.getApplication())
                .getActiveSubscriptionInfoList();
        if (subInfoList == null || subInfoList.size() == 0) {
            MmsLog.d(TAG, "isLocalNumber SIM not insert");
            return false;
        }        
        for (SubscriptionInfo subInfoRecord : subInfoList) {
            if (PhoneNumberUtils.compare(number, getLocalNumber(subInfoRecord.getSubscriptionId()))) {
                    return true;
                }
            }
         return false;
    }

    public static void viewMmsMessageAttachmentMini(Context context, Uri msgUri,
            SlideshowModel slideshow) {
        if (msgUri == null) {
            return;
        }
        boolean isSimple = (slideshow == null) ? false : slideshow.isSimple();
        if (slideshow != null) {
            /// M: If a slideshow was provided, save it to disk first.
            PduPersister persister = PduPersister.getPduPersister(context);
            try {
                PduBody pb = slideshow.toPduBody();
                MessageUtils.updatePartsIfNeeded(slideshow, persister, msgUri, pb, null);
                //persister.updateParts(msgUri, pb, null);
                slideshow.sync(pb);
            } catch (MmsException e) {
                MmsLog.e(TAG, "Unable to save message for preview");
                return;
            }
        }

        /// M: Launch the slideshow activity to play/view.
        Intent intent;
        if (isSimple && (slideshow != null) && slideshow.get(0).hasAudio()) {
            intent = new Intent(context, SlideshowActivity.class);
        } else {
            intent = new Intent(context, MmsPlayerActivity.class);
        }
        intent.setData(msgUri);
        context.startActivity(intent);
    }

    /// M:wappush: add this function to handle the url string,
    /// if it does not contain the http or https schema, then add http schema manually.
    public static String checkAndModifyUrl(String url) {
        if (url == null) {
            return null;
        }

        Uri uri = Uri.parse(url);
        if (uri.getScheme() != null) {
            return url;
        }

        return "http://" + url;
    }

    public static void selectRingtone(Context context, int requestCode) {
        if (context instanceof Activity) {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_INCLUDE_DRM, false);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,
                    context.getString(R.string.select_audio));
            if (FeatureOption.MTK_DRM_APP) {
                intent.putExtra(OmaDrmStore.DrmExtra.EXTRA_DRM_LEVEL,
                        OmaDrmStore.DrmExtra.LEVEL_SD);
            }
            ((Activity) context).startActivityForResult(intent, requestCode);
        }
    }

    public static Map<Integer, CharSequence> subInfoMap = new HashMap<Integer, CharSequence>();

    public static CharSequence getSubInfo(Context context, int subId) {

        MmsLog.d(TAG, "getSubInfo subId = " + subId);
        //add this code for more safty
        if (subId == -1) {
            return "";
        }
        if (subInfoMap.containsKey(subId)) {
            MmsLog.d(TAG, "MessageUtils.getSubInfo(): getCache");
            return subInfoMap.get(subId);
        }
        // get sub info
        return getSubInfoSync(context, subId, null);
    }

    public static CharSequence getJoynSubInfo(Context context, int subId, String joynName) {
        MmsLog.d(TAG, "getJoynSubInfo subId = " + subId);
        if (subInfoMap.containsKey(JOYN_SUB_ID)) {
            MmsLog.d(TAG, "MessageUtils.getSubInfo(): getCache for joyn");
            return subInfoMap.get(JOYN_SUB_ID);
        }
        if (subId == -1) {
            return "";
        }
        // get sub info
        return getSubInfoSync(context, subId, joynName);
    }

    /// M: add a new method for ipmessage apk get subinfo
    public static CharSequence getSubInfoSync(Context context, int subId, String joynName) {
        SubscriptionInfo subInfo = SubscriptionManager.from(MmsApp.getApplication())
                .getActiveSubscriptionInfo(subId);
        if (null != subInfo) {
            String displayName;
            if (joynName == null) {
                displayName = subInfo.getDisplayName().toString();
            } else {
                displayName = joynName;
            }
            MmsLog.d(TAG, "SubInfo subId=" + subInfo.getSubscriptionId() + " mDisplayName=" + displayName);

            if (null == displayName) {
                subInfoMap.put(subId, "");
                return "";
            }

            SpannableStringBuilder buf = new SpannableStringBuilder();
            buf.append("   ");
            /// M: Code analyze 007, For fix bug ALPS00238454, modify sub card
            // indicator max string length. @{
            if (displayName.length() < 7) {
                buf.append(displayName);
            } else {
                buf.append(displayName.substring(0, 4) + "..." +
                            displayName.substring(displayName.length() - 1));
                /// @}
            }
            buf.append("  ");

            //set background image
            Bitmap bitmap = subInfo.createIconBitmap(context);
            Drawable drawable = new BitmapDrawable(context.getResources(), bitmap);
            buf.setSpan(new BackgroundImageSpan(-1, drawable),
                    0,
                    buf.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            // set subInfo color
            int color = context.getResources().getColor(R.color.siminfo_color);
            buf.setSpan(new ForegroundColorSpan(color), 0, buf.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            //buf.setSpan(new StyleSpan(Typeface.BOLD),0, buf.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (joynName == null) {
                subInfoMap.put(subId, buf);
            } else {
                subInfoMap.put(JOYN_SUB_ID, buf);
            }
            return buf;
        }
        subInfoMap.put(subId, "");
        return "";
    }

    public static void addNumberOrEmailtoContact(final String numberOrEmail, final int REQUEST_CODE,
            final Activity activity) {
        if (!TextUtils.isEmpty(numberOrEmail)) {
            String message = activity.getResources().getString(R.string.add_contact_dialog_message, numberOrEmail);
            AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle(numberOrEmail).setMessage(message);
            AlertDialog dialog = builder.create();
            dialog.setButton(AlertDialog.BUTTON_POSITIVE, activity.getResources().getString(
                    R.string.add_contact_dialog_existing), new DialogInterface.OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    // TODO Auto-generated method stub
                    Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                    intent.setType(Contacts.CONTENT_ITEM_TYPE);
                    if (Mms.isEmailAddress(numberOrEmail)) {
                        intent.putExtra(ContactsContract.Intents.Insert.EMAIL, numberOrEmail);
                    } else {
                        intent.putExtra(ContactsContract.Intents.Insert.PHONE, numberOrEmail);
                    }
                    if (REQUEST_CODE > 0) {
                        activity.startActivityForResult(intent, REQUEST_CODE);
                    } else {
                        activity.startActivity(intent);
                    }
                }
            });

            dialog.setButton(AlertDialog.BUTTON_NEGATIVE, activity.getResources()
                    .getString(R.string.add_contact_dialog_new), new DialogInterface.OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    // TODO Auto-generated method stub
                    final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                    if (Mms.isEmailAddress(numberOrEmail)) {
                        intent.putExtra(ContactsContract.Intents.Insert.EMAIL, numberOrEmail);
                    } else {
                        intent.putExtra(ContactsContract.Intents.Insert.PHONE, numberOrEmail);
                    }
                    if (REQUEST_CODE > 0) {
                        activity.startActivityForResult(intent, REQUEST_CODE);
                    } else {
                        activity.startActivity(intent);
                    }
                }
            });
            dialog.show();
        }
    }

    public static boolean checkUriContainsDrm(Context context, Uri uri) {
        if (uri != null && uri.getAuthority() != null && uri.getAuthority().equals("mms")) {
            Cursor c = context.getContentResolver().query(uri, new String[] {Part._DATA}, null, null, null);
            try {
                if (c.moveToFirst()) {
                    String src = c.getString(c.getColumnIndexOrThrow(Part._DATA));
                    if (src != null && src.endsWith(".dcf")) {
                        MmsLog.d(TAG, "Media src ends with dcf, isDrmContent = true");
                        return true;
                    }
                }
            } finally {
                c.close();
            }
            MmsLog.d(TAG, "Media src does not end with dcf, isDrmContent = false");
            return false;
        }
        try {
            OmaDrmUtils.DrmProfile drmProfile = OmaDrmUtils.getDrmProfile(context, uri,
                    MmsApp.getApplication().getDrmManagerClient());
            MmsLog.d(TAG, "OmaDrmUtils Uri = " + uri + " isDrmContent = " + drmProfile.isDrm());
            return drmProfile.isDrm();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** M:
     * Return the current storage status.
     */
    public static String getStorageStatus(Context context) {
        /// M: we need count only
        final String[] PROJECTION = new String[] {
            BaseColumns._ID, Mms.MESSAGE_SIZE
        };
        final ContentResolver cr = context.getContentResolver();
        final Resources res = context.getResources();
        Cursor cursor = null;

        StringBuilder buffer = new StringBuilder();
        // Mms count
        cursor = cr.query(Mms.CONTENT_URI, PROJECTION, null, null, null);
        int mmsCount = 0;
        if (cursor != null) {
            mmsCount = cursor.getCount();
        }
        buffer.append(res.getString(R.string.storage_dialog_mms, mmsCount));
        buffer.append("\n");
        //Mms size
        long size = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    size += cursor.getInt(1);
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        buffer.append(res.getString(R.string.storage_dialog_mms_size) + getHumanReadableSize(size));
        buffer.append("\n");
        // Attachment size
        size = getAttachmentSize(context);
        Log.d(TAG, "mms attachment size = " + size);
        final String sizeTag = getHumanReadableSize(size);
        buffer.append(res.getString(R.string.storage_dialog_attachments) + sizeTag);
        buffer.append("\n");
        // Sms count
        cursor = cr.query(Sms.CONTENT_URI, PROJECTION, null, null, null);
        int smsCount = 0;
        if (cursor != null) {
            smsCount = cursor.getCount();
            cursor.close();
        }
        buffer.append(res.getString(R.string.storage_dialog_sms, smsCount));
        buffer.append("\n");
        // Database size
        final long dbsize = getDatabaseSize(context);
        buffer.append(res.getString(R.string.storage_dialog_database) + getHumanReadableSize(dbsize));
        buffer.append("\n");
        // Available space
        final StatFs datafs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        final long availableSpace = datafs.getAvailableBlocksLong() * datafs.getBlockSizeLong();
        buffer.append(res.getString(R.string.storage_dialog_available_space) + getHumanReadableSize(availableSpace));
        return buffer.toString();
    }

    /// M: Code analyze 017, For fix bug ALPS00275452, the size of Mms
    // attachment is 0B in the settings. @{
    private static long getAttachmentSize(Context context) {
        Uri uri = Uri.parse("content://mms/attachment_size");
        ContentValues insertValues = new ContentValues();
        uri = context.getContentResolver().insert(uri, insertValues);
        String size = uri.getQueryParameter("size");
        return Long.parseLong(size);
    }
    /// @}

    /// M: Get database size, for SELinux enhance, mms can not get the size directly,
    //  should get the size through phone process @{
    private static long getDatabaseSize(Context context) {
        Uri uri = Uri.parse("content://mms-sms/database_size");
        ContentValues insertValues = new ContentValues();
        uri = context.getContentResolver().insert(uri, insertValues);
        String size = uri.getQueryParameter("size");
        return Long.parseLong(size);
    }
    /// @}

    /// M: Code analyze 004, For fix bug ALPS00231349, add new feature vCard
    // support. @{
    public static String getHumanReadableSize(long size) {
        /// @}
        String tag;
        String b="B";
        String kb="KB";
        String mb="MB";
        String locale = Locale.getDefault().getLanguage();
        if (locale.startsWith("ar")) {
        	b="ب";
        	kb="كيلوبايت";
        	mb="ميغابايت";
        }else if(locale.startsWith("ru")) {
        	b=" Б";
        	kb=" КБ";
        	mb=" МБ";
        }else if(locale.startsWith("fr")) {
        	b="B";
        	kb="Кo";
        	mb="Мo";
        }
        float fsize = (float) size;
        if (size < 1024L) {
            tag = String.valueOf(size) + b;
        } else if (size < 1024L * 1024L) {
            fsize /= 1024.0f;
            tag = String.format(Locale.ENGLISH, "%.2f", fsize) + kb;
        } else {
            fsize /= 1024.0f * 1024.0f;
            tag = String.format(Locale.ENGLISH, "%.2f", fsize) + mb;
        }
        return tag;
    }

    /// M: Code analyze 001, For fix bug ALPS00101270, The address is displayed
    // as contact in the unsent message view,but the add contact icon still
    // displayed in the view. @{
    public static boolean canAddToContacts(Contact contact) {
        // There are some kind of automated messages, like STK messages, that we don't want
        // to add to contacts. These names begin with special characters, like, "*Info".
        final String name = contact.getName();
        if (!TextUtils.isEmpty(contact.getNumber())) {
            char c = contact.getNumber().charAt(0);
            if (isSpecialChar(c)) {
                return false;
            }
        }
        if (!TextUtils.isEmpty(name)) {
            char c = name.charAt(0);
            if (isSpecialChar(c)) {
                return false;
            }
        }
        if (!(Mms.isEmailAddress(name) || (Mms.isPhoneNumber(name) || isPhoneNumber(name)) ||
                MessageUtils.isLocalNumber(contact.getNumber()))) {     // Handle "Me"
            return false;
        }
        return true;
    }

    private static boolean isPhoneNumber(String num) {
        num = num.trim();
        if (TextUtils.isEmpty(num)) {
            return false;
        }
        final char[] digits = num.toCharArray();
        for (char c : digits) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSpecialChar(char c) {
        return c == '*' || c == '%' || c == '$';
    }
    /// @}

    /** M:
     * Get sms encoding type set by user.
     * @param context
     * @return encoding type
     */
    public static int getSmsEncodingType(Context context) {
        int type = SmsMessage.ENCODING_UNKNOWN;
        //add by lipeng for 7bit
        if(MessageUtils.smsEncodeCanClick()){
        	Log.d("lipeng", "true");
        	return SmsMessage.ENCODING_7BIT;
        }//end by lipeng
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String encodingType = null;
        //encodingType = prefs.getString(SmsPreferenceActivity.SMS_INPUT_MODE, "Automatic");
        encodingType = prefs.getString(SmsPreferenceActivity.SMS_INPUT_MODE, MessageUtils.defaultEncodeType);//modify by lipeng for 7bit
        if ("Unicode".equals(encodingType)) {
            type = SmsMessage.ENCODING_16BIT;
        } else if ("GSM alphabet".equals(encodingType)) {
            type = SmsMessage.ENCODING_7BIT;
        }
        return type;
    }

    /// M: Code analyze 005, For fix bug ALPS00120575, add for cmcc dir ui. @{
    public static void replyMessage(int subId, Context context, String address) {
        Intent intent = new Intent();
        intent.putExtra("address", address);
        intent.putExtra("showinput", true);
        /// M: fix alps01858266. this put sub id here. @{
        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);
        /// @}
        intent.setClassName(context, "com.android.mms.ui.ComposeMessageActivity");
        context.startActivity(intent);
    }

    public static void confirmDeleteMessage(final Activity activity, final Uri msgUri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.confirm_dialog_title);
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setCancelable(true);
        builder.setMessage(R.string.confirm_delete_message);
        builder.setPositiveButton(R.string.delete,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        /// M: fix bug ALPS00351620; for requery searchactivity.
                                        SearchActivity.setNeedRequery();
                                        SqliteWrapper.delete(activity.getApplicationContext(),
                                                                activity.getContentResolver(),
                                                                msgUri, null, null);
                                        dialog.dismiss();
                                        activity.finish();
                                    }
        });
        builder.setNegativeButton(R.string.no, null);
        builder.show();
    }

    public static String getMmsDetail(Context context, Uri uri, int size, int msgBox) {
        Resources res = context.getResources();
        MultimediaMessagePdu msg;
        try {
            msg = (MultimediaMessagePdu) PduPersister.getPduPersister(
                    context).load(uri);
        } catch (MmsException e) {
            Log.e(TAG, "Failed to load the message: " + uri, e);
            return res.getString(R.string.cannot_get_details);
        }

        StringBuilder details = new StringBuilder();
        // Message Type: Text message.
        initializeMsgDetails(context, details, res, msg);

        // SentDate: ***
        if (msg.getDateSent() > 0
                && msgBox == Mms.MESSAGE_BOX_INBOX) {
            details.append('\n');
            details.append(res.getString(R.string.sent_label));
            details.append(MessageUtils.formatTimeStampString(context,
                    msg.getDateSent() * 1000L, true));
        }

        // Date: ***
        details.append('\n');
        if (msgBox == Mms.MESSAGE_BOX_DRAFTS) {
            details.append(res.getString(R.string.saved_label));
        } else if (msgBox == Mms.MESSAGE_BOX_INBOX) {
            details.append(res.getString(R.string.received_label));
        } else {
            details.append(res.getString(R.string.sent_label));
        }

        details.append(MessageUtils.formatTimeStampString(
                context, msg.getDate() * 1000L, true));

        // Subject: ***
        details.append('\n');
        details.append(res.getString(R.string.subject_label));

        EncodedStringValue subject = msg.getSubject();
        if (subject != null) {
            String subStr = subject.getString();
            // Message size already include size of subject.
//            size += subStr.length();
            details.append(subStr);
        }
        // Priority: High/Normal/Low
        return formatDetails(details, context, msg, size, res);
    }

    private static String formatDetails(StringBuilder details, Context context,
            MultimediaMessagePdu msg, int size, Resources res) {
        details.append('\n');
        details.append(res.getString(R.string.priority_label));
        details.append(getPriorityDescription(context, msg.getPriority()));

        // Message size: *** KB
        details.append('\n');
        details.append(res.getString(R.string.message_size_label));
        details.append((size - 1) / 1024 + 1);
        details.append(res.getString(R.string.kilobyte));

        return details.toString();
    }

    public static void updateNotification(final Context context) {
        new Thread(new Runnable() {
            public void run() {
                //MessagingNotification.blockingUpdateNewMessageIndicator(context, MessagingNotification.THREAD_ALL, false);
                MessagingNotification.nonBlockingUpdateSendFailedNotification(context);
                MessagingNotification.updateDownloadFailedNotification(context);
                CBMessagingNotification.updateNewMessageIndicator(context);
            }
        }).start();
    }
    /// @}

    /// M: Code analyze 006, For fix bug ALPS00234739, draft can't be saved
    // after share the edited picture to the same ricipient.Remove old Mms draft
    // in conversation list instead of compose view. @{
    public static void addRemoveOldMmsThread(Runnable r) {
        mRemoveOldMmsThread = new Thread(r);
    }

    public static void asyncDeleteOldMms() {
        if (mRemoveOldMmsThread != null) {
            mRemoveOldMmsThread.start();
            mRemoveOldMmsThread = null;
        }
    }

    /// @}

    /// M: Code analyze 015, new feature, Unread message number of Mms, Phone,
    // Email and Calendar display in Launcher. @{
    private static int getUnreadMessageNumber(Context context) {
        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                Uri.parse("content://mms-sms/unread_count"), null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int count = cursor.getInt(0);
                    MmsLog.d(MmsApp.TXN_TAG, "unread message count: " + count);
                    return count;

                }
            } finally {
                cursor.close();
            }
        } else {
            MmsLog.d(MmsApp.TXN_TAG, "can not get unread message count.");
        }
        return 0;
    }

    /// @}

    /// M: Code analyze 012, For fix bug ALPS00245433, "Suggested" does not
    // show in select Sub dialog if te recipient number associates with Sub
    // cards. @{
    public static String detectCountry() {
        try {
            CountryDetector detector =
                (CountryDetector) MmsApp.getApplication().getSystemService(Context.COUNTRY_DETECTOR);
            final Country country = detector.detectCountry();
            if (country != null) {
                return country.getCountryIso();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /// @}

    /// M: Code analyze 023, For fix bug ALPS00284546, The video can not be
    // added and there is no prompt. @{
    public static String formatNumber(String number, Context context) {
        String countryCode = detectCountry();
        AsYouTypeFormatter mFormatter = PhoneNumberUtil.getInstance().getAsYouTypeFormatter(countryCode);
        char [] cha = number.toCharArray();
        int ii = cha.length;
        for (int num = 0; num < ii; num++) {
            number = mFormatter.inputDigit(cha[num]);
        }
        return number;
    }

    /// @}

    /// M: Code analyze 014, new feature, add new feature vCalendar @{
    public static boolean isVCalendarAvailable(Context context) {
        final Intent intent = new Intent("android.intent.action.CALENDARCHOICE");
        intent.setType("text/x-vcalendar");
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list =
                packageManager.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }
    /// @}

    /** M: Code analyze 019, For fix bug ALPS00278013, The name of the image is
     * changed to number and the suffix also disappeared when received in the email.
     * Get an unique name . It is different with the existed names.
     *
     * @param names
     * @param fileName
     * @return
     */
    public static String getUniqueName(String names[], String fileName) {
        if (names == null || names.length == 0) {
            return fileName;
        }
        int mIndex = 0;
        String tempName = "";
        String finalName = fileName;
        String extendion = "";
        String fileNamePrefix = "";
        int fileCount = 0;
        while (mIndex < names.length) {
            tempName = names[mIndex];
            if (tempName != null && tempName.equals(finalName)) {
                fileCount++;
                int tempInt = fileName.lastIndexOf(".");
                if (tempInt == -1) {
                    extendion = "";
                    fileNamePrefix = fileName;
                    finalName = fileNamePrefix + "(" + fileCount + ")" + extendion;
                } else {
                    extendion = fileName.substring(tempInt, fileName.length());
                    fileNamePrefix = fileName.substring(0, tempInt);
                    finalName = fileNamePrefix + "(" + fileCount + ")" + extendion;
                }
                mIndex = 0;
            } else {
                mIndex++;
            }
        }
        return finalName;
    }

    /** M: Code analyze 021, For fix bug ALPS00279524, The "JE" about "MMS"
     * pops up after we launch "Messaging" again.
     *
     * @param bitmap
     * @param maxWidth
     * @param maxHeight
     * @return
     */
    public static Bitmap getResizedBitmap(Bitmap bitmap, int maxWidth, int maxHeight) {
        if (bitmap == null) {
            return bitmap;
        }
        int originWidth = bitmap.getWidth();
        int originHeight = bitmap.getHeight();

        if (originWidth < maxWidth && originHeight < maxHeight) {
            return bitmap;
        }

        int width = originWidth;
        int height = originHeight;

        if (originWidth > maxWidth) {
            width = maxWidth;
            double i = originWidth * 1.0 / maxWidth;
            height = (int) Math.floor(originHeight / i);
            Bitmap mBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
            return mBitmap;
        }

        if (originHeight > maxHeight) {
            height = maxHeight;
            double i = originHeight * 1.0 / maxHeight;
            width = (int) Math.floor(originWidth / i);
            Bitmap mBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
            return mBitmap;
        }

        return bitmap;
    }

    /** M: Code analyze 022, For fix bug ALPS00281094, Can not send and receive Sms.
     * Return true iff the network portion of <code>address</code> is,
     * as far as we can tell on the device, suitable for use as an SMS
     * destination address.
     */
    public static boolean isWellFormedSmsAddress(String address) {
        //MTK-START [mtk04070][120104][ALPS00109412]Solve "can't send MMS with MSISDN in international format"
        //Merge from ALPS00089029
        if (!isDialable(address)) {
            return false;
        }
        //MTK-END [mtk04070][120104][ALPS00109412]Solve "can't send MMS with MSISDN in international format"

        String networkPortion =
                PhoneNumberUtils.extractNetworkPortion(address);

        return (!(networkPortion.equals("+")
                  || TextUtils.isEmpty(networkPortion)))
               && isDialable(networkPortion);
    }

    private static boolean isDialable(String address) {
        for (int i = 0, count = address.length(); i < count; i++) {
            if (!isDialable(address.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** M: True if c is ISO-LATIN characters 0-9, *, # , +, WILD  */
    private static boolean isDialable(char c) {
        return (c >= '0' && c <= '9') || c == '*' || c == '#' || c == '+' || c == 'N' || c == '(' || c == ')';
    }

    /// @}

    /// M: Code analyze 024, For fix bug ALPS00296775, attachments in slideshow
    // can be saved. Network service Provider will change the file name whick
    // contains unusual characters. @{
    public static String getContentType(String contentType, String fileName) {
        String finalContentType = "";
        if (contentType == null) {
            return contentType;
        }
        if (contentType.equalsIgnoreCase("application/oct-stream")
                || contentType.equalsIgnoreCase("application/octet-stream")) {
            if (fileName != null) {
                String suffix = fileName.contains(".") ?
                        fileName.substring(fileName.lastIndexOf("."), fileName.length()) : "";
                /// M: fix bug ALPS00427229, Ignore suffix Case
                if (suffix.equals("")) {
                    return contentType;
                } else if (suffix.equalsIgnoreCase(".bmp")) {
                    finalContentType = MmsContentType.IMAGE_BMP;
                } else if (suffix.equalsIgnoreCase(".jpg")) {
                    finalContentType = MmsContentType.IMAGE_JPG;
                } else if (suffix.equalsIgnoreCase(".wbmp")) {
                    finalContentType = MmsContentType.IMAGE_WBMP;
                } else if (suffix.equalsIgnoreCase(".gif")) {
                    finalContentType = MmsContentType.IMAGE_GIF;
                } else if (suffix.equalsIgnoreCase(".png")) {
                    finalContentType = MmsContentType.IMAGE_PNG;
                } else if (suffix.equalsIgnoreCase(".jpeg")) {
                    finalContentType = MmsContentType.IMAGE_JPEG;
                } else if (suffix.equalsIgnoreCase(".vcs")) {
                    finalContentType = MmsContentType.TEXT_VCALENDAR;
                } else if (suffix.equalsIgnoreCase(".vcf")) {
                    finalContentType = MmsContentType.TEXT_VCARD;
                } else if (suffix.equalsIgnoreCase(".imy")) {
                    finalContentType = MmsContentType.AUDIO_IMELODY;
                // M: fix bug ALPS00355917
                } else if (suffix.equalsIgnoreCase(".ogg")) {
                    finalContentType = MmsContentType.AUDIO_OGG;
                } else if (suffix.equalsIgnoreCase(".aac")) {
                    finalContentType = MmsContentType.AUDIO_AAC;
                } else if (suffix.equalsIgnoreCase(".mp2")) {
                    finalContentType = MmsContentType.AUDIO_MPEG;
                /// M: fix bug ALPS00444328, 3gp audio contentType will be modified
                /// when CMCC send to CU
                } else if (suffix.equalsIgnoreCase(".3gp")) {
                    finalContentType = MmsContentType.AUDIO_3GPP;
                } else {
                    String extension = fileName.contains(".")
                            ? fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length()) : "";
                    finalContentType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                    if (finalContentType == null) {
                        return contentType;
                    }
                }
                return finalContentType;
            }
        }
        return contentType;
    }

    /// @}
    public static float getPreferenceValueFloat(Context context, String key, float defaultValue) {
        SharedPreferences sp = context.getSharedPreferences(DEFAULT_MMS_PREFERENCE_NAME, Context.MODE_WORLD_READABLE);
        return sp.getFloat(key, defaultValue);
    }

    /// M: Code analyze 025, For fix bug ALPS00298363, The "JE" pops up after
    // you launch message again. @{
    public static long getAvailableBytesInFileSystemAtGivenRoot(String rootFilePath) {
        StatFs stat = new StatFs(rootFilePath);
//        final long totalBlocks = stat.getBlockCount();
        // put a bit of margin (in case creating the file grows the system by a few blocks)
        final long availableBlocks = stat.getAvailableBlocks() - 128;

        // long mTotalSize = totalBlocks * stat.getBlockSize();
        long mAvailSize = availableBlocks * stat.getBlockSize();

        Log.i(TAG, "getAvailableBytesInFileSystemAtGivenRoot(): "
                + "available space (in bytes) in filesystem rooted at: "
                + rootFilePath + " is: " + mAvailSize);
        return mAvailSize;
    }
    /// @}

    /** M: 4.1 has removed this function
    public static void viewMmsMessageAttachment(Context context, WorkingMessage msg,
            int requestCode) {
        SlideshowModel slideshow = msg.getSlideshow();
        if (slideshow == null) {
            throw new IllegalStateException("msg.getSlideshow() == null");
        }

        SlideModel slide = slideshow.get(0);
        if (slideshow.isSimple() && slide!=null && !slide.hasAudio()) {
            MessageUtils.viewSimpleSlideshow(context, slideshow);
        } else {
            Uri uri = msg.saveAsMms(false);
            if (uri != null) {
                // Pass null for the slideshow paramater, otherwise viewMmsMessageAttachment
                // will persist the slideshow to disk again (we just did that above in saveAsMms)
                viewMmsMessageAttachment(context, uri, null, requestCode);
            }
        }
    }*/

     /** M: Move this functiom from compose activity to this class.*/
     public static boolean isRestrictedType(Context context, long msgId) {
        PduBody body = PduBodyCache.getPduBody(context, ContentUris.withAppendedId(Mms.CONTENT_URI,
                msgId));
        if (body == null) {
            return false;
        }

        int partNum = body.getPartsNum();
        for (int i = 0; i < partNum; i++) {
            PduPart part = body.getPart(i);
            int width = 0;
            int height = 0;
            String type = new String(part.getContentType());

            int mediaTypeStringId;
            if (MmsContentType.isVideoType(type)) {
                mediaTypeStringId = R.string.type_video;
            } else if (MmsContentType.isAudioType(type) || "application/ogg".equalsIgnoreCase(type)) {
                mediaTypeStringId = R.string.type_audio;
            } else if (MmsContentType.isImageType(type)) {
                mediaTypeStringId = R.string.type_picture;
                InputStream input = null;
                try {
                    input = context.getContentResolver().openInputStream(part.getDataUri());
                    BitmapFactory.Options opt = new BitmapFactory.Options();
                    opt.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(input, null, opt);
                    width = opt.outWidth;
                    height = opt.outHeight;
                } catch (FileNotFoundException e) {
                    // Ignore
                    MmsLog.e(TAG, "FileNotFoundException caught while opening stream", e);
                } finally {
                    if (null != input) {
                        try {
                            input.close();
                        } catch (IOException e) {
                            // Ignore
                            MmsLog.e(TAG, "IOException caught while closing stream", e);
                        }
                    }
                }
            } else {
                continue;
            }
            if (!MmsContentType.isUnrestrictedType(type)
                    || width > MmsConfig.getMaxRestrictedImageWidth()
                    || height > MmsConfig.getMaxRestrictedImageHeight()) {
                if (WorkingMessage.sCreationMode == WorkingMessage.RESTRICTED_TYPE) {
                    Resources res = context.getResources();
                    MessageUtils.showErrorDialog((Activity) context,
                            R.string.unsupported_media_format,
                            R.string.select_different_media, mediaTypeStringId, mediaTypeStringId);
                }
                return true;
            }
        }
        return false;
    }

    /// M: new feature, SD card exist or not
    public static boolean existingSD(Context context, boolean isExternal) {
        StorageManager mStorageMamatger;
        mStorageMamatger = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        StorageVolume[] volumes = mStorageMamatger.getVolumeList();
        if (volumes == null) {
            return false;
        }
        String mountPoint = null;
        for (int i = 0; i < volumes.length; i++) {
            if (isExternal) {
                if (volumes[i].isRemovable()) {
                    mountPoint = volumes[i].getPath();
                    break;
                }
            } else {
                if (!volumes[i].isRemovable()) {
                    mountPoint = volumes[i].getPath();
                    break;
                }
            }
        }
        if (mountPoint == null) {
            return false;
        }
        String volumeState = mStorageMamatger.getVolumeState(mountPoint);
        return (volumeState != null && volumeState.equals(Environment.MEDIA_MOUNTED));
    }
    /// @}

    /// M: add for shortcut {@
    private static final String INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT";
    private static final String EXTRA_SHORTCUT_TOTAL_NUMBER = "com.android.launcher2.extra.shortcut.totalnumber";
    private static final String EXTRA_SHORTCUT_STEP_NUMBER = "com.android.launcher2.extra.shortcut.stepnumber";

    public static void addShortcutToLauncher(final Context context, final HashSet<Long> threadIds) {
        new Thread(new Runnable() {
            public void run() {
                Intent intent = null;
                int totalNumber = threadIds.size();
                int currentNumber = 0;
                for (Long threadId : threadIds) {
                    currentNumber++;
                    Conversation conv = Conversation.get(context, threadId, false);
                    if (MmsConfig.isServiceEnabled(context) && conv.getRecipients().size() == 1) {
                        String number = conv.getRecipients().get(0).getNumber();
                        if (number.startsWith(IpMessageConsts.GROUP_START)) {
                            MmsLog.d("shortcut", "create group shortcut.");
                            intent = new Intent(INSTALL_SHORTCUT);
                            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, conv.getRecipients().formatNames(","));
                            intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                                    Intent.ShortcutIconResource.fromContext(context, R.drawable.ic_launcher_smsmms));
                            Intent it = new Intent(ACTION_OPEN_GROUP);
                            it.setClass(context, TransferActivity.class);
                            ///M: Create a group chat shortcuts from conversation list,and then delete the group thread,
                            ///and finally through the shortcut can not re-enter the group chat {@
                            int contactId = IpMessageUtils.getContactManager(context).getContactIdByNumber(number);
                            it.putExtra(IpMessageConsts.RemoteActivities.KEY_CONTACT_ID, contactId);
                            it.putExtra(IpMessageConsts.RemoteActivities.KEY_THREAD_ID, -1);
                            ///@}
                            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
                                            | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, it);
                            intent.putExtra(EXTRA_SHORTCUT_TOTAL_NUMBER, totalNumber);
                            intent.putExtra(EXTRA_SHORTCUT_STEP_NUMBER, currentNumber);
                            context.sendBroadcast(intent);
                            continue;
                        }
                    }
                    if (conv.getType() != 10) {
                        MmsLog.d("shortcut", "addShortcutToLauncher(): threadId=" + threadId);
                        intent = new Intent(INSTALL_SHORTCUT);
                        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, conv.getRecipients().formatNames(","));
                        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                                Intent.ShortcutIconResource.fromContext(context, R.drawable.ic_launcher_smsmms));
                        Intent it = createIntentByThreadId(context, threadId, conv.getType());
                        it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, it);
                        intent.putExtra(EXTRA_SHORTCUT_TOTAL_NUMBER, totalNumber);
                        intent.putExtra(EXTRA_SHORTCUT_STEP_NUMBER, currentNumber);
                        context.sendBroadcast(intent);
                    } else { /// M: this is a guide thread
                        MmsLog.d("shortcut", "create guide shortcut.");
                        intent = new Intent(INSTALL_SHORTCUT);
                        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, IpMessageUtils.getResourceManager(context)
                            .getSingleString(IpMessageConsts.string.ipmsg_service_title));
                        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                                Intent.ShortcutIconResource.fromContext(context, R.drawable.ic_launcher_smsmms));
                        Intent it = new Intent(ACTION_OPEN_GUIDE);
                        it.setClass(context, TransferActivity.class);
                        it.putExtra(IpMessageConsts.RemoteActivities.KEY_THREAD_ID, threadId);
                        it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, it);
                        intent.putExtra(EXTRA_SHORTCUT_TOTAL_NUMBER, totalNumber);
                        intent.putExtra(EXTRA_SHORTCUT_STEP_NUMBER, currentNumber);
                        context.sendBroadcast(intent);
                    }
                }
            }
        }, "Add_Shortcut_Thread").start();
    }

    public static Intent createIntentByThreadId(Context context, long threadId, int type) {
        Intent intent = null;
        if (Telephony.Threads.CELL_BROADCAST_THREAD == type) {
           intent = CBMessageListActivity.createIntent(context, threadId);
           intent.putExtra("bFromLaunch", true);
        } else {
           intent = new Intent(context, ComposeMessageActivity.class);
           intent.putExtra("finish", true);
           if (threadId > 0) {
               intent.setAction(Intent.ACTION_SENDTO);
               intent.setData(Uri.parse("smsto:" + TextUtils.join(
                       ",", Conversation.get(context, threadId, true).getRecipients().getNumbers())));
           }
        }
        return intent;
    }
    /// @}

    /**
     * M: copy file from srcFile to destFile.
     *
     * @param srcFile
     * @param destFile
     * @return
     */
    public static boolean copyFile(String srcFile, String destFile) {
        InputStream is = null;
        FileOutputStream os = null;
        try {
            File inFile = new File(srcFile);
            if (!inFile.exists()) {
                return false;
            }
            is = new FileInputStream(inFile);
            File outFile = new File(destFile);
            if (!outFile.exists()) {
                outFile.createNewFile();
            }
            os = new FileOutputStream(outFile);
            byte[] buffer = new byte[2048];
            for (int len = 0; (len = is.read(buffer)) != -1; ) {
                os.write(buffer, 0, len);
            }
            return true;
        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            return false;
        } finally {
            try {
                if (null != is) {
                    is.close();
                }
                if (null != os) {
                    os.close();
                }
            } catch (IOException e) {

            }
        }
    }

    /**
     * M:
     * @param path
     * @return
     */
    public static String getFileName(String path) {
        if (path == null || path.equals("")) {
            return path;
        }
        int index = path.lastIndexOf(File.separator);
        if (index > 0) {
            path = path.substring(index + 1, path.length());
        }
        return path;
    }

    /*
     * this method is similar with formatTimeStampString, except that it can show Now/Yesterday
     * if the time is within a minute
     * obviously you need to refresh to update this String after some seconds.
     */
    public static String formatTimeStampStringExtend(Context context, long when) {
        Time then = new Time();
        then.set(when);
        Time now = new Time();
        now.setToNow();

        // Basic settings for formatDateTime() we want for all cases.
        int format_flags = DateUtils.FORMAT_NO_NOON_MIDNIGHT |
                           DateUtils.FORMAT_ABBREV_ALL |
                           DateUtils.FORMAT_CAP_AMPM;

        // If the message is from a different year, show the date and year.
        if (then.year != now.year) {
            format_flags |= DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_DATE;
        } else if (then.yearDay != now.yearDay) {
            // If it is from a different day than today, show only the date.
            if ((now.yearDay - then.yearDay) == 1) {
                return context.getString(R.string.str_ipmsg_yesterday);
            } else {
                format_flags |= DateUtils.FORMAT_SHOW_DATE;
            }
        } else if ((now.toMillis(false) - then.toMillis(false)) < 60000) {
            return context.getString(R.string.time_now);
        } else {
            // Otherwise, if the message is from today, show the time.
            format_flags |= DateUtils.FORMAT_SHOW_TIME;
        }
        /// M: For OP09 @{
        if (MmsConfig.isFormatDateAndTimeStampEnable()) {
            IMmsUtilsExt mmsUtils = (IMmsUtilsExt) MmsPluginManager
                    .getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_MESSAGE_UTILS);
            return mmsUtils.formatDateTime(context, when, format_flags);
        }
        /// @}
        return DateUtils.formatDateTime(context, when, format_flags);
    }

    private static List<String> getHomes(Context context) {
        MmsLog.d(TAG, "DialogModeActivity.getHomes");

        List<String> names = new ArrayList<String>();
        PackageManager packageManager = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> resolveInfo = packageManager.queryIntentActivities(intent,
            PackageManager.MATCH_DEFAULT_ONLY);

        for (ResolveInfo ri : resolveInfo) {
            names.add(ri.activityInfo.packageName);
            MmsLog.d(TAG, "package name=" + ri.activityInfo.packageName);
            MmsLog.d(TAG, "class name=" + ri.activityInfo.name);
        }
        return names;
    }

    public static boolean isHome(Context context) {
        List<String> homePackageNames = getHomes(context);
        String packageName = "";
        String className = "";
        boolean ret = false;

        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningTaskInfo> rti = activityManager.getRunningTasks(2);

        if (rti != null && rti.size() > 0) {
            packageName = rti.get(0).topActivity.getPackageName();
            className = rti.get(0).topActivity.getClassName();
        }

        MmsLog.d(TAG, "package0= " + packageName + " class0=" + className);

        ret = homePackageNames.contains(packageName);
        if (!ret) {
            if ("com.android.mms.ui.DialogModeActivity".equals(className)) {
                ret = true;
            }
        }

        /// M: fix bug ALPS00687923, check RunningAppProcessInfo IMPORTANCE_FOREGROUND @{
        if (!ret) {
            List<RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
            if (appProcesses == null || appProcesses.size() == 0) {
                MmsLog.d(TAG, "appProcesses == null || appProcesses.size() == 0");
                ret = false;
            } else {
                for (RunningAppProcessInfo appProcess : appProcesses) {
                    if (appProcess.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                            && appProcess.processName.equals("com.android.launcher")) {
                        MmsLog.d(TAG, "IMPORTANCE_FOREGROUND == com.android.launcher");
                        ret = true;
                    }
                }
            }
        }
        /// @}

        return ret;
    }

    public static boolean checkNeedNotify(Context context, long threadId, Cursor cursor) {
        if (threadId < 0) {
            MmsLog.w(TAG, "illegal threadId:" + threadId);
            return false;
        }
        boolean appNotificationEnabled = true;
        long appMute = 0;
        long appMuteStart = 0;
        boolean threadNotificationEnabled = true;
        long threadMute = 0;
        long threadMuteStart = 0;
        if (!checkAppSettingsNeedNotify(context)) {
            return false;
        }
        if (threadId == 0) {
            return true;
        }
        /// M: check thread settings
        Uri threadSettingsUri = ContentUris.withAppendedId(THREAD_SETTINGS_URI, (int) threadId);
        if (cursor != null) {
            threadNotificationEnabled = cursor.getInt(Conversation.NOTIFICATION_ENABLE) == 0 ? false : true;
            threadMute = cursor.getLong(Conversation.MUTE);
            threadMuteStart = cursor.getLong(Conversation.MUTE_START);
            //MmsLog.d(TAG, "before check: threadNotificationEnabled = " + threadNotificationEnabled
            //        + ", \tthreadMute = " + threadMute
            //        + ", \tthreadMuteStart = " + threadMuteStart);
        } else {
            /// M: fix bug ALPS00415754, add some useful log
            MmsLog.d(TAG, "before query threadSettingsUri in checkNeedNotify()");
            Cursor c = context.getContentResolver().query(threadSettingsUri,
                    new String[] {Telephony.ThreadSettings.NOTIFICATION_ENABLE,
                                  Telephony.ThreadSettings.MUTE,
                                  Telephony.ThreadSettings.MUTE_START,
                                  Telephony.ThreadSettings.RINGTONE,
                                  Telephony.ThreadSettings.VIBRATE},
                    null, null, null);
            MmsLog.d(TAG, "after query threadSettingsUri in checkNeedNotify()");

            if (c == null) {
                MmsLog.d(TAG, "cursor is null.");
                return true;
            }
            try {
                if (c.getCount() == 0) {
                    MmsLog.d(TAG, "cursor count is 0");
                } else {
                    c.moveToFirst();
                    threadNotificationEnabled = c.getInt(0) == 0 ? false : true;
                    threadMute = c.getLong(1);
                    threadMuteStart = c.getLong(2);

                    MmsLog.d(TAG, "before check: threadNotificationEnabled = " + threadNotificationEnabled
                            + ", \tthreadMute = " + threadMute
                            + ", \tthreadMuteStart = " + threadMuteStart);
                }
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }

        if (!threadNotificationEnabled) {
            MmsLog.d(TAG, "thread notification is disabled!");
            return false;
        }

        //MmsLog.d(TAG, "\t threadMute:" + threadMute + ", threadMute*3600=" + threadMute * 3600);
        //MmsLog.d(TAG, "\t threadMuteStart" + threadMuteStart / 1000);

        threadMute = checkThreadMuteTimeout(threadMuteStart, threadMute, threadSettingsUri, context);

        if (threadMute > 0) {
            MmsLog.d(TAG, "thread mute is set!");
            return false;
        }

        return true;
    }

    private static long checkThreadMuteTimeout(long threadMuteStart,
                                                long threadMute,
                                                Uri threadSettingsUri,
                                                Context context) {
        long mute = threadMute;
        if (threadMuteStart > 0 && threadMute > 0) {
            long currentTime = (System.currentTimeMillis() / 1000);
            MmsLog.d(TAG, "\t currentTime" + currentTime);
            if ((threadMute * 3600 + threadMuteStart / 1000) <= currentTime) {
                MmsLog.d(TAG, "thread mute timeout, reset to default.");
                threadMute = 0;
                threadMuteStart = 0;
                final Uri threadSettings = threadSettingsUri;
                final Context ct = context;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ContentValues contentValues = new ContentValues(2);
                        contentValues.put(Telephony.ThreadSettings.MUTE, 0);
                        contentValues.put(Telephony.ThreadSettings.MUTE_START, 0);
                        ct.getContentResolver().update(threadSettings, contentValues, null, null);
                        if (MmsConfig.getFolderModeEnabled()) {
                            long threadId = Long.parseLong(threadSettings.getLastPathSegment());
                            MuteCache.setMuteCache(threadId, 0, 0, true);
                        }
                    }
                }, "reset-mute-Thread").start();
                return 0;
            }
        }
        return mute;
    }

    public static boolean checkNeedNotifyForFolderMode(Context context, long threadId,
            long threadMute, long threadMuteStart, boolean threadNotificationEnabled) {
        if (threadId < 0) {
            MmsLog.w(TAG, "illegal threadId:" + threadId);
            return false;
        }
        if (!checkAppSettingsNeedNotify(context)) {
            return false;
        }

        if (threadId == 0) {
            return true;
        }
        /// M: check thread settings
        if (!threadNotificationEnabled) {
            MmsLog.d(TAG, "thread notification is disabled!");
            return false;
        }

        MmsLog.d(TAG, "\t threadMute:" + threadMute + ", threadMute*3600=" + threadMute * 3600);
        MmsLog.d(TAG, "\t threadMuteStart" + threadMuteStart / 1000);

        Uri threadSettingsUri = ContentUris.withAppendedId(
                THREAD_SETTINGS_URI, (int) threadId);
        threadMute = checkThreadMuteTimeout(threadMuteStart, threadMute, threadSettingsUri, context);
        if (threadMute > 0) {
            MmsLog.d(TAG, "thread mute is set!");
            return false;
        }

        return true;
    }

    public static boolean checkAppSettingsNeedNotify(Context context) {
        boolean appNotificationEnabled = true;
        long appMute = 0;
        long appMuteStart = 0;

        /// M: check app settings
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        appNotificationEnabled = prefs.getBoolean(NotificationPreferenceActivity.NOTIFICATION_ENABLED, true);
        if (!appNotificationEnabled) {
            MmsLog.d(TAG, "app notification set disabled!");
            return false;
        }

        String muteStr = prefs.getString(NotificationPreferenceActivity.NOTIFICATION_MUTE, Integer.toString(0));
        appMute = Integer.parseInt(muteStr);
        appMuteStart = prefs.getLong(NotificationPreferenceActivity.MUTE_START, 0);
        //MmsLog.d(TAG, "\t appmute:" + appMute + ", appMute*3600=" + appMute * 3600);
        //MmsLog.d(TAG, "\t appMuteStart" + appMuteStart / 1000);
        if (appMuteStart > 0 && appMute > 0) {
            long currentTime = (System.currentTimeMillis() / 1000);
            //MmsLog.d(TAG, "\t currentTime" + currentTime);
            if ((appMute * 3600 + appMuteStart / 1000) <= currentTime) {
                //MmsLog.d(TAG, "thread mute timeout, reset to default.");
                appMute = 0;
                appMuteStart = 0;
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
                editor.putLong(NotificationPreferenceActivity.MUTE_START, 0);
                editor.putString(NotificationPreferenceActivity.NOTIFICATION_MUTE, String.valueOf(appMute));
                editor.apply();
            }
        }

        if (appMute > 0) {
            return false;
        }
        return true;
    }


    /**
     * M: read app drawable resource and create a new file in /data/data/com.android.mms/files path.
     * @param context
     * @param fileName
     * @return
     */
    public static boolean createFileForResource(Context context, String fileName, int fileResourceId) {
        OutputStream os = null;
        InputStream ins = null;
        try {
            os = context.openFileOutput(fileName, Context.MODE_PRIVATE);
            ins = context.getResources().openRawResource(fileResourceId);
            byte[] buffer = new byte[2048];
            for (int len = 0; (len = ins.read(buffer)) != -1; ) {
                os.write(buffer, 0, len);
            }
            return true;
        } catch (FileNotFoundException e) {
            MmsLog.e(TAG, "create file failed.", e);
            return false;
        } catch (IOException e) {
            MmsLog.e(TAG, "create file failed.", e);
            return false;
        } finally {
            try {
                if (null != ins) {
                    ins.close();
                }
                if (null != os) {
                    os.close();
                }
            } catch (IOException e) {
                MmsLog.e(TAG, "createFileForResource:" + e.toString());
            }
        }
    }

    public static int getStatusResourceId(Context context, MessageItem msgItem) {
        if (msgItem == null) {
            MmsLog.e(TAG, "getStatusResourceId(): MsgItem is null!", new Exception());
            return 0;
        }
        if (msgItem.isSubMsg()) {
            MmsLog.d(TAG, "getStatusResourceId(): Sub message.");
            return 0;
        }
        MmsLog.d(TAG, "getStatusResourceId(): MsgId = " + msgItem.mMsgId);
        MmsLog.d(TAG, "getStatusResourceId(): isMms = " + msgItem.isMms()
            + ", SENTBOX = " + (msgItem.mBoxId == Mms.MESSAGE_BOX_SENT)
            + ", has read report = " + msgItem.mHasReadReport
            + ", has delivery report = " + msgItem.mHasDeliveryReport);
        if (msgItem.isMms()) {
            if (msgItem.mHasReadReport) {
                return R.drawable.im_meg_status_read;
            }
            if (msgItem.mHasDeliveryReport) {
                return R.drawable.im_meg_status_reach;
            }

            switch (msgItem.mBoxId) {
            case Mms.MESSAGE_BOX_SENT:
                return R.drawable.im_meg_status_out;

            case Mms.MESSAGE_BOX_OUTBOX:
            case Mms.MESSAGE_BOX_FAILED:
                MmsLog.d(MmsApp.TXN_TAG, "mms is sending, uri = " + msgItem.mMessageUri);
                return R.drawable.im_meg_status_sending;

            case Mms.MESSAGE_BOX_INBOX:
            case Mms.MESSAGE_BOX_DRAFTS:
            case Mms.MESSAGE_BOX_ALL:
            default:
                return 0;
            }
        } else if (msgItem.isSms()) {
            switch (msgItem.mBoxId) {
            case Sms.MESSAGE_TYPE_QUEUED:
            case Sms.MESSAGE_TYPE_OUTBOX:
                return R.drawable.im_meg_status_sending;

            case Sms.MESSAGE_TYPE_SENT:
                return R.drawable.im_meg_status_out;

            case Sms.MESSAGE_TYPE_FAILED:
                ///M: this status has been handled by common flow
            case Sms.MESSAGE_TYPE_INBOX:
            case Sms.MESSAGE_TYPE_DRAFT:
            case Sms.MESSAGE_TYPE_ALL:
            default:
                return 0;
            }
        }
        return 0;
    }

    public static List<MmsReportStatus> getMmsReportStatus(Context context, long messageId) {
        MmsLog.d(TAG, "getMmsReportStatus(): messageId = " + messageId);
        Uri uri = Uri.withAppendedPath(Mms.REPORT_STATUS_URI, String.valueOf(messageId));
        Cursor c = SqliteWrapper.query(context, context.getContentResolver(), uri,
            new String[] {Mms.Addr.ADDRESS, "delivery_status", "read_status"}, null, null, null);
        if (c == null) {
            return null;
        }
        try {
            List<MmsReportStatus> mmsReportStatusList = new ArrayList<MmsReportStatus>();
            while (c.moveToNext()) {
                int columnDeliveryStatus = 1;
                int columnReadStatus = 2;
                mmsReportStatusList.add(new MmsReportStatus(c.getInt(columnDeliveryStatus), c.getInt(columnReadStatus)));
            }
            return mmsReportStatusList;
        } finally {
            c.close();
        }
    }

    /**
     * M: For EVDO: check the sim is whether UIM or not.
     * @param subId the sim's sub id.
     * @return true: UIM; false: not UIM.
     */
    public static boolean isUSimType(int subId) {
        String phoneType = TelephonyManagerEx.getDefault().getIccCardType(subId);
        if (phoneType == null) {
            Log.d(TAG, "[isUIMType]: phoneType = null");
            return false;
        }
        Log.d(TAG, "[isUIMType]: phoneType = " + phoneType);
        return phoneType.equalsIgnoreCase("CSIM") || phoneType.equalsIgnoreCase("UIM")
            || phoneType.equalsIgnoreCase("RUIM");
    }

    /**
     * M: for check CSIM in gsm mode or not.
     * @param subId the CSIM's sub id.
     * @return true: in gsm; false: no.
     */
    public static boolean isCSIMInGsmMode(int subId) {
        if (isUSimType(subId)) {
            TelephonyManagerEx tmEx = TelephonyManagerEx.getDefault();
            int vnt = tmEx.getPhoneType(SubscriptionManager.getSlotId(subId));
            Log.d(TAG,
                "[isCSIMInGsmMode]:[NO_PHONE = 0; GSM_PHONE = 1; CDMA_PHONE = 2;]; phoneType:"
                    + vnt);
            if (vnt == TelephonyManager.PHONE_TYPE_GSM) {
                return true;
            }
        }
        return false;
    }

    public static CharSequence formatMsgContent(String subject, String body,
            String displayAddress) {
        StringBuilder buf = new StringBuilder(
                displayAddress == null
                ? ""
                : displayAddress.replace('\n', ' ').replace('\r', ' '));
        buf.append(':').append(' ');

        int offset = buf.length();
        if (!TextUtils.isEmpty(subject)) {
            subject = subject.replace('\n', ' ').replace('\r', ' ');
            buf.append(subject);
            buf.append(' ');
        }

        if (!TextUtils.isEmpty(body)) {
            body = body.replace('\n', ' ').replace('\r', ' ');
            buf.append(body);
        }

        SpannableString spanText = new SpannableString(buf.toString());
        spanText.setSpan(new StyleSpan(Typeface.BOLD), 0, offset,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return spanText;
    }

    public static boolean haveEmailContact(String emailAddress, Context context) {
        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                Uri.withAppendedPath(Email.CONTENT_LOOKUP_URI, Uri.encode(emailAddress)),
                new String[] { Contacts.DISPLAY_NAME }, null, null, null);

        if (cursor != null) {
            try {
                String name;
                while (cursor.moveToNext()) {
                    name = cursor.getString(0);
                    if (!TextUtils.isEmpty(name)) {
                        return true;
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return false;
    }

    public static CharSequence getVisualTextName(Context context,
                                                String enumName,
                                                int choiceNameResId,
                                                int choiceValueResId) {
        /// M: Modify for OP09, replace "SIM" with "UIM" @{
        IStringReplacementExt mStringReplacementPlugin = (IStringReplacementExt) MmsPluginManager.getMmsPluginObject(
                MmsPluginManager.MMS_PLUGIN_TYPE_STRING_REPLACEMENT);
        CharSequence[] visualNames = null;

        if (MmsConfig.isStringReplaceEnable()) {
            List<SubscriptionInfo> subInfoList = SubscriptionManager.from(context)
                    .getActiveSubscriptionInfoList();
            if (subInfoList != null && subInfoList.size() == 1
                && isUSimType(subInfoList.get(0).getSubscriptionId())
                && choiceNameResId == R.array.pref_sms_save_location_choices) {
                String[] location = mStringReplacementPlugin.getSaveLocationString();
                visualNames = location;
            } else {
                visualNames = context.getResources().getTextArray(choiceNameResId);
            }
        } else { /// M: @}
            visualNames = context.getResources().getTextArray(choiceNameResId);
        }

        CharSequence[] enumNames = context.getResources().getTextArray(choiceValueResId);
        // Sanity check
        if (visualNames.length != enumNames.length) {
            return "";
        }
        for (int i = 0; i < enumNames.length; i++) {
            if (enumNames[i].equals(enumName)) {
                return visualNames[i];
            }
        }
        return "";
    }

    public static File getStorageFile(String filename, Context context) {
        String dir = "";
        String path = StorageManagerEx.getDefaultPath();
        if (path == null) {
            MmsLog.e(TAG, "default path is null");
            return null;
        }
        dir = path + "/" + Environment.DIRECTORY_DOWNLOADS + "/";
        MmsLog.i(TAG, "copyPart,  file full path is " + dir + filename);
        File file = getUniqueDestination(dir + filename);

        // make sure the path is valid and directories created for this file.
        File parentFile = file.getParentFile();
        if (!parentFile.exists() && !parentFile.mkdirs()) {
            MmsLog.e(TAG, "[MMS] copyPart: mkdirs for " + parentFile.getPath()
                    + " failed!");
            return null;
        }
        return file;
    }

    public static String getMainCardDisplayName() {
        String mainSubDisplayName = "";
        Context ct = MmsApp.getApplication();
        int mainSubId = (int) Settings.System.getLong(ct.getContentResolver(),
                    Settings.System.SMS_SIM_SETTING,
                    Settings.System.DEFAULT_SIM_NOT_SET);
        if (mainSubId != Settings.System.DEFAULT_SIM_SETTING_ALWAYS_ASK
                && mainSubId != Settings.System.DEFAULT_SIM_NOT_SET) {
            SubscriptionInfo info = SubscriptionManager.from(MmsApp.getApplication())
                    .getActiveSubscriptionInfo(mainSubId);
            mainSubDisplayName = info.getDisplayName().toString();
        } else {
            SubscriptionInfo info = SubscriptionManager.from(MmsApp.getApplication())
                    .getActiveSubscriptionInfo(mainSubId);
            if (info != null && info.getSubscriptionId() > 0) {
                mainSubDisplayName = info.getDisplayName().toString();
            } else {
                info = SubscriptionManager.from(MmsApp.getApplication()).getActiveSubscriptionInfo(
                        mainSubId);
                if (info != null && info.getSubscriptionId() > 0) {
                    mainSubDisplayName = info.getDisplayName().toString();
                } else {
                    MmsLog.e(IPMSG_TAG, "error to get main sub display name");
                }
            }
        }
        return mainSubDisplayName;
    }

  //Modify ALPS00445952, if this attachment didn't include extension(index < 0),
  //fileName.substring(0, index) will throw exception, and save attachment fail.
  //So we should deal with this situation.

    public static File getUniqueDestination(String fileName) {
        File file ;
        final int index = fileName.indexOf(".");
        if (index > 0) {
            final String extension = fileName.substring(index + 1, fileName.length());
            final String base = fileName.substring(0, index);
            file = new File(base + "." + extension);
            for (int i = 2; file.exists(); i++) {
                file = new File(base + "_" + i + "." + extension);
            }
        } else {
            file = new File(fileName);
            for (int i = 2; file.exists(); i++) {
                file = new File(fileName + "_" + i);
            }
        }
        return file;
  }

    /* remove NotificationPlus notiPlus
    public static void handleNewNotification(Context context, int messageCount) {
        Intent clickIntent = new Intent(Intent.ACTION_MAIN);
        //clickIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
        //        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        //        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        clickIntent.setClassName("com.android.mms", "com.android.mms.ui.BootActivity");
        // Make a startActivity() PendingIntent for the notification.
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        EncapsulatedNotificationPlus notiPlus = new EncapsulatedNotificationPlus.EncapsulatedBuilder(context)
                .setTitle(context.getString(R.string.new_message))
                .setMessage(context.getResources().getQuantityString(
                    R.plurals.notification_multiple, messageCount, messageCount))
                .setPositiveButton(context.getString(R.string.view), pendingIntent)
                .create();
        EncapsulatedNotificationManagerPlus.notify(1, notiPlus);
    }
    */

    public static void setMmsLimitSize(Context context) {
        Context otherAppContext = null;
        SharedPreferences sp = null;
        try {
            otherAppContext = context.createPackageContext("com.android.contacts",
                    Context.CONTEXT_IGNORE_SECURITY);

        } catch (Exception e) {
            MmsLog.e(TAG, "ConversationList NotFoundContext");
        }
        if (otherAppContext != null) {
           sp = otherAppContext.
                    getSharedPreferences("com.android.contacts_preferences", Context.MODE_WORLD_READABLE);
        }
        String mSizeLimitTemp = null;
        int mMmsSizeLimit = 0;
        if (sp != null) {
            mSizeLimitTemp = sp.getString("pref_key_mms_size_limit", "300");
        }
        if (mSizeLimitTemp != null && 0 == mSizeLimitTemp.compareTo("100")) {
            mMmsSizeLimit = 100;
        } else if (mSizeLimitTemp != null && 0 == mSizeLimitTemp.compareTo("200")) {
            mMmsSizeLimit = 200;
        } else {
            mMmsSizeLimit = 300;
        }
      //add by lipeng for euro
        if(FeatureOption.HQ_MMS_APP_MMSSIZE) {
           mSubInfoList = SubscriptionManager.from(MmsApp.getApplication()).getActiveSubscriptionInfoList();
		   int slotId = -1;
		   int mSubCount = (mSubInfoList != null && !mSubInfoList.isEmpty()) ? mSubInfoList
					.size() : 0;
		if(mSubInfoList!=null && !mSubInfoList.isEmpty()){
           slotId = mSubInfoList.get(0).getSimSlotIndex();
		}
           Log.d("lipeng","activieSlot = " + slotId);
           if (mSubCount > 1) {
   			Log.d("MmsSize", "mSubCount = " + mSubCount);
   			int mmsSizeForCard1 = loadMmsSizeByMncMcc(getSimMccMnc(0));
   			int mmsSizeForCard2 = loadMmsSizeByMncMcc(getSimMccMnc(1));
   			Log.d("MmsSize", "MmsSizeNum1 = " + mmsSizeForCard1
   					+ "------" + "MmsSizeNum2 = " + mmsSizeForCard2);
   			if (mmsSizeForCard1 <= mmsSizeForCard2) {
   				mMmsSizeLimit = mmsSizeForCard1;
   			} else {
   				mMmsSizeLimit = mmsSizeForCard2;
   			}
   		} else if (mSubCount == 1) {
   			Log.d("MmsSize", "MmsSize_slotId = " + slotId);
   			mMmsSizeLimit = loadMmsSizeByMncMcc(getSimMccMnc(slotId));
   		}
           //mMmsSizeLimit = loadMmsSizeByMncMcc(getSimMccMnc(slotId));
        }//end by lipeng
        MmsConfig.setUserSetMmsSizeLimit(mMmsSizeLimit);
    }

  //add by lipeng for al813 mms
    public static String getMmsSizeForEuro() {
		int mmsSizeForCard = 300;
		mSubInfoList = SubscriptionManager.from(MmsApp.getApplication())
				.getActiveSubscriptionInfoList();
		int mSubCount = (mSubInfoList != null && !mSubInfoList.isEmpty()) ? mSubInfoList
				.size() : 0;
		int slotId = -1;
		if (mSubInfoList != null && !mSubInfoList.isEmpty()) {
			slotId = mSubInfoList.get(0).getSimSlotIndex();
		}
		Log.d("lipeng", "activieSlot = " + slotId);

		if (mSubCount > 1) {
			Log.d("MmsSize_lipeng", "mSubCount = " + mSubCount);
			int mmsSizeForCard1 = loadMmsSizeByMncMcc(getSimMccMnc(0));
			int mmsSizeForCard2 = loadMmsSizeByMncMcc(getSimMccMnc(1));
			Log.d("MmsSize_lipeng", "MmsSizeNum1 = " + mmsSizeForCard1
					+ "------" + "MmsSizeNum2 = " + mmsSizeForCard2);
			if (mmsSizeForCard1 <= mmsSizeForCard2) {
				mmsSizeForCard = mmsSizeForCard1;
			} else {
				mmsSizeForCard = mmsSizeForCard2;
			}
		} else if (mSubCount == 1) {
			Log.d("MmsSize_lipeng", "MmsSize_slotId = " + slotId);
			mmsSizeForCard = loadMmsSizeByMncMcc(getSimMccMnc(slotId));
		}
		// mmsSizeForCard = loadMmsSizeByMncMcc(getSimMccMnc(slotId));
		return mmsSizeForCard + "K";
    }
    

	public static String getSimMccMnc(int slotId) {
		ArrayList<String> simType;
		Log.d("lipeng", "[slotId_getSimMccMnc] = " + slotId);
		simType = PhoneNumberUtils.getVirtualType(slotId);
		Log.d(TAG, "MMS simcard mncmcc sim1: " + simType.get(0) + "  "
				+ simType.get(1));
		if (simType.get(0).equals("-1")) {
			return simType.get(2);
		}
		return simType.get(1); 

	}
  

    /*get mms size for param mncmcc*/
    public static int loadMmsSizeByMncMcc(String keyword) {
    	int size = 300;
    	HQMmsConfigOverride mOverride =HQMmsConfigOverride.getInstance();
    	if(mOverride.containsMccmncForMmsConfig(keyword)){
    		MmsConfigBean bean = mOverride.getMmsConfigWithSim(keyword);
    		try {
    			size = Integer.parseInt(bean.mMmsSize);
			} catch (Exception e) {
				
			}
    	}
    	Log.d("lipeng", "MMS loadMmsSizeByMncMcc: "+size);
        return size;
    }

    /*get sms to mms for param mncmcc*/
    public static int loadSmsToMmsByMncMcc(String keyword) {
    	int mSmsToMms = 5;
    	HQMmsConfigOverride mOverride =HQMmsConfigOverride.getInstance();
    	if(mOverride.containsMccmncForMmsConfig(keyword)){
    		Log.d(TAG, "MMS loadSmsToMmsByMncMcc: "+keyword);
    		MmsConfigBean bean = mOverride.getMmsConfigWithSim(keyword);
    		try {
    			mSmsToMms = Integer.parseInt(bean.mSmsToMms);
			} catch (Exception e) {
				
			}
    	}else{
    	    //Log.d(TAG, "BING1 "+keyword);
            MmsConfigBean bean1 = mOverride.getMmsConfigWithSim("default");
			//Log.d(TAG, "BING3 "+bean1.mSmsToMms);
    		try {
    			mSmsToMms = Integer.parseInt(bean1.mSmsToMms);
			} catch (Exception e) {
				Log.d(TAG, "mSmsToMms error");
			}
		}
    	Log.d(TAG, "MMS loadSmsToMmsByMncMcc: "+mSmsToMms);
    	if(mSmsToMms==-1){
    		mSmsToMms = 99999;
    		Log.d(TAG, "MMS loadSmsToMmsByMncMcc value -1 then: "+mSmsToMms);
    	}
        return mSmsToMms;
    }
    //end by lipeng
    
    
    
    /// M: fix bug ALPS604911, change MmsContentType when share multi-file from FileManager @{
    public static String getContentType(Uri uri) {
        String path = uri.getPath();

        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String extension = MimeTypeMap.getFileExtensionFromUrl(path).toLowerCase();
        if (TextUtils.isEmpty(extension)) {
            int dotPos = path.lastIndexOf('.');
            if (0 <= dotPos) {
                extension = path.substring(dotPos + 1);
                extension = extension.toLowerCase();
            }
        }

        String type = mimeTypeMap.getMimeTypeFromExtension(extension);
        return type;
    }
    /// @}

    /// M: fix bug ALPS01379798, support multi-file share from KK Download @{
    public static String queryContentType(Context context, Uri uri) {
        String type = context.getContentResolver().getType(uri);
        return type;
    }
    /// @}

    /*
     * M: If image can display return true, else return false.
     */
    public static boolean checkImageOK(Context context, Uri imageUri) {
        try {
            Bitmap bitmap = null;
            InputStream mInputStream = null;
            try {
                mInputStream = context.getContentResolver().openInputStream(imageUri);
                if (mInputStream != null) {
                    bitmap = BitmapFactory.decodeStream(mInputStream);
                }
                if (bitmap == null) {
                    return false;
                } else {
                    return true;
                }
            } catch (FileNotFoundException e) {
                bitmap = null;
            } finally {
                if (mInputStream != null) {
                    mInputStream.close();
                }
            }
        } catch (java.lang.OutOfMemoryError e) {
            Log.e(TAG, "checkImageOK(Uri): out of memory: ", e);
        } catch (IOException e) {
            Log.e(TAG, "checkImageOK(Uri): IOException: ", e);
        } catch (Exception e) {
            Log.e(TAG, "checkImageOK(Uri): Exception: ", e);
        }
        return false;
    }

    /*
     * M: If image can not display, show default broken image.
     */
    public static Bitmap getDefaultBrokenImage(Context context) {
        try {
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(),
                    R.drawable.ic_missing_thumbnail_picture);
            return bitmap;
        } catch (java.lang.OutOfMemoryError e) {
            Log.e(TAG, "getDefaultBrokenImage: out of memory: ", e);
        }
        return null;
    }

    /**
     * M: For OP09
     * @param context
     */
    public static void dealCTDeviceLowNotification(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        getStorageLowPlugin(context);
        if (MmsConfig.getCTDeviceStorageLowStatus()) {
            String notificationTitle = sStorageLowPlugin.getNotificationTitle();
            Intent viewConvIntent = new Intent(context, ConversationList.class);
            viewConvIntent.setAction(Intent.ACTION_VIEW);
            viewConvIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, viewConvIntent, 0);

            Notification notification = new Notification();
            notification.icon = R.drawable.stat_sys_no_sim;
            notification.tickerText = notificationTitle;
            notification.defaults = Notification.DEFAULT_ALL;

            RemoteViews contentView = new RemoteViews(context.getPackageName(), R.layout.status_bar_sms_rejected);
            contentView.setImageViewResource(R.id.icon, R.drawable.stat_sys_no_sim);
            contentView.setTextViewText(R.id.title, notificationTitle);
            contentView.setTextViewText(R.id.text, sStorageLowPlugin.getNotificationBody());
            notification.contentView = contentView;
            notification.contentIntent = pendingIntent;

            sStorageLowPlugin.showNotification(nm, notification);
        } else {
            sStorageLowPlugin.cancelNotification(nm);
        }
    }

    /**
     * M: For OP09
     * @param context
     */
    public static void cancelCTDeviceLowNotification(Context context) {
        getStorageLowPlugin(context);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        sStorageLowPlugin.cancelNotification(nm);
    }

    /**
     *  M: For OP09
     * @param context
     */
    private static void getStorageLowPlugin(Context context) {
        if (sStorageLowPlugin != null) {
            return;
        }

        sStorageLowPlugin = (IStorageLowExt) MPlugin.createInstance(
                    IStorageLowExt.class.getName(),context.getApplicationContext());
            MmsLog.d(MmsApp.TXN_TAG, "operator sStorageLowPlugin = " + sStorageLowPlugin);
        if(sStorageLowPlugin == null){
            sStorageLowPlugin = new DefaultStorageLowExt(context.getApplicationContext());
            MmsLog.d(MmsApp.TXN_TAG, "default sStorageLowPlugin = " + sStorageLowPlugin);
        }
    }

    public static void modifyDataRoamingStatus(Context context, Intent intent) {
        getMmsPreferencePlugin(context);
        sMmsPreference.modifyDataRoamingPreference(context, intent);
    }

    private static void getMmsPreferencePlugin(Context context) {
        if (sMmsPreference != null) {
            return;
        }

        sMmsPreference = (IMmsPreferenceExt) MPlugin.createInstance(
                    IMmsPreferenceExt.class.getName(),context.getApplicationContext());
            MmsLog.d(MmsApp.TXN_TAG, "operator sMmsPreference = " + sMmsPreference);
       if(sMmsPreference == null){
            sMmsPreference = new DefaultMmsPreferenceExt(context.getApplicationContext());
            MmsLog.d(MmsApp.TXN_TAG, "default sMmsPreference = " + sMmsPreference);
        }
    }

    public static void updatePartsIfNeeded(SlideshowModel slideshow, PduPersister persister,
            Uri uri, PduBody pb, HashMap<Uri, InputStream> preOpenedFiles) throws MmsException {
        if (!slideshow.needUpdate()) {
            return;
        }
        persister.updateParts(uri, pb, preOpenedFiles);
        slideshow.resetUpdateState();
    }

    public static int calculateWallpaperSize(Context context, int height, int width) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int currentMaxHeight = windowManager.getDefaultDisplay().getHeight();
        int currentMaxWidth = windowManager.getDefaultDisplay().getWidth();
        MmsLog.d(TAG, "CurrentMaxHeight = " + currentMaxHeight + " CurrentMaxWidth = " + currentMaxWidth);
        int ratio = 1;
        while ((height / ratio) > currentMaxHeight || (width / ratio) > currentMaxWidth) {
            ratio *= 2;
        }
        return ratio;
    }

    /// M: fix bug ALPS01505548, delete VCard temp file
    public static void deleteVCardTempFile(Context context, String filename) {
        if (filename != null && filename.endsWith(".vcf")) {
            context.deleteFile(filename);
        }
    }

    public static void deleteVCardTempFiles(Context context, ArrayList<String> files) {
        for (int i = 0; i < files.size(); i++) {
            deleteVCardTempFile(context, files.get(i));
        }
        files.clear();
    }
    /// M: fix bug ALPS01523754.set google+ pic as wallpaper.@{
    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    public static boolean isSimMessageAccessable(Context context, int... subId) {
        // First, forbid to access SIM message if this is not default MMS.
        boolean isSmsEnable = MmsConfig.isSmsEnabled(context);
        if (!isSmsEnable) {
            MmsLog.d(TAG, "isSimMessageAccessable Sms not enabled");
            return false;
        }

        // Second, check airplane mode
        boolean airplaneOn = Settings.System.getInt(context.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) == 1;
        if (airplaneOn) {
            MmsLog.d(TAG, "isSimMessageAccessable airplane is On");
            return false;
        }

        // Third, check whether has inserted SIM
        List<SubscriptionInfo> subInfoList = SubscriptionManager.from(MmsApp.getApplication())
                .getActiveSubscriptionInfoList();
        if (subInfoList == null || subInfoList.size() == 0) {
            MmsLog.d(TAG, "isSimMessageAccessable SIM not insert");
            return false;
        }

        // Forth, check sms ready
        ISms mSmsManager = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        if (mSmsManager == null) {
            MmsLog.d(TAG, "isSimMessageAccessable mSmsManager is null");
            return false;
        }
        boolean isSimReady = false;
        try {
            if (subId.length == 1) {
                isSimReady = mSmsManager.isSmsReadyForSubscriber(subId[0]);
            } else {
                for (SubscriptionInfo subInfoRecord : subInfoList) {
                    isSimReady = mSmsManager.isSmsReadyForSubscriber(subInfoRecord.getSubscriptionId());
                    if (isSimReady) {
                        break;
                    }
                }
            }
        } catch (RemoteException e) {
            MmsLog.d(TAG, "isSimMessageAccessable failed to get sms state");
            isSimReady = false;
        }

        MmsLog.d(TAG, "isSimMessageAccessable" + isSimReady);
        return isSimReady;
    }


    public static String getTempWallpaper(Context context, Uri uri) {
        InputStream is = null;
        FileOutputStream fos = null;
        String tempPath = TempFileProvider.getScrapPath(context, TEMP_WALLPAPER);
        try {
            is = context.getContentResolver().openInputStream(uri);
            File tempFile = new File(tempPath);
            if (is != null) {
                if (tempFile.exists()) {
                    tempFile.delete();
                }
                File parentFile = tempFile.getParentFile();
                if (!parentFile.exists() && !parentFile.mkdirs()) {
                    MmsLog.d(TAG, "getTempWallpaper parentFile.mkdirs fail");
                    return null;
                }
                tempFile.createNewFile();
            }
            fos = new FileOutputStream(tempFile);
            byte[] buffer = new byte[8192];
            for (int len = 0; (len = is.read(buffer)) != -1; ) {
                fos.write(buffer, 0, len);
            }
            return tempPath;
        } catch (FileNotFoundException e) {
            MmsLog.d(TAG, "getTempWallpaper FileNotFoundException");
        } catch (IOException e) {
            MmsLog.d(TAG, "getTempWallpaper IOException");
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    MmsLog.d(TAG, "getTempWallpaper IOException while closing: " + fos, e);
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    MmsLog.d(TAG, "getTempWallpaper IOException while closing: " + is, e);
                }
            }
        }
        return null;
    }
    /// @}

    /**
     * @return Whether use Sim Simulator Toll or not.
     */
    public static boolean isUseSubSimulator() {
        String enableNowSMS = SystemProperties.get("net.ENABLE_NOWSMS");
        String enableSimulator = SystemProperties.get("net.Enable_Simulator_Tool");
        if (enableNowSMS.equals("true") && enableSimulator.equals("true")) {
            return true;
        }
        return false;
    }

    public static void showDrmAlertDialog(Context context) {
        Builder builder = new Builder(context);
        builder.setMessage(R.string.drm_not_support);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        if (context instanceof ComposeMessageActivity) {
            ((ComposeMessageActivity) context).mClickCanResponse = true;
        }
    }

    ///M: WFC: utility api @ {
    public static boolean isWfcOn(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.WHEN_TO_MAKE_WIFI_CALLS,
                TelephonyManager.WifiCallingChoices.NEVER_USE)
                == TelephonyManager.WifiCallingChoices.ALWAYS_USE ? true : false;
    }

    public static int getWfcPreference(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.SELECTED_WFC_PREFERRENCE,
                TelephonyManager.WifiCallingPreferences.CELLULAR_ONLY);
    }
    
    public static boolean isSimPresent(Context context) {
        int[] subs = SubscriptionManager.from(context).getActiveSubscriptionIdList();
        if (subs.length == 0){
            MmsLog.d(TAG, "Sim not present");
            return false;
        } else {
            MmsLog.d(TAG, "Sim present");
            return true;
        }
    }
    /// @}
    //add by lipeng for 7bit sim change reset encode type
    public static void resetEncodeType(Context context){
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    	SharedPreferences.Editor editor =prefs.edit();
    	editor.remove(SmsPreferenceActivity.SMS_INPUT_MODE);
    	editor.apply();
    }
    
    /*add by lipeng for 7bit get 7bit function whether lossy for param mncmcc*/
    public static boolean loadCan7BitChangeByMncMcc(String keyword) {
    	boolean canClick = false;
    	SevenConfigOverride mSevenConfigOverride = SevenConfigOverride.getInstance();
    	if(mSevenConfigOverride.containsMccmncForSevenBit(keyword)){
                MmsSevenConfigBean bean = mSevenConfigOverride.getSevenBitWithSim(keyword);
                //int key = Integer.parseInt(bean.sevenBitConfig);
                String key = bean.sevenBitConfig;
    		Log.d("lipeng","match result:"+key); 
    		if("1".equals(key)){
    			canClick = true;
    		}
    	}
    	Log.d("lipeng", "MMS loadCan7BitChangeByMncMcc: "+canClick);
        return canClick;
    } 
    /*add by lipeng for 7bit sms code function can change */
    public static boolean smsEncodeCanClick(){
        mSubInfoList = SubscriptionManager.from(MmsApp.getApplication()).getActiveSubscriptionInfoList();
        int slotId = -1;
		if(mSubInfoList!=null && !mSubInfoList.isEmpty()){
           slotId = mSubInfoList.get(0).getSimSlotIndex();
		}
        Log.d("lipeng","activieSlot = " + slotId);
        Log.d("lipeng", "loadCan7BitChangeByMncMcc: "+loadCan7BitChangeByMncMcc(getSimMccMnc(slotId)));
    	return loadCan7BitChangeByMncMcc(getSimMccMnc(slotId));	 //getSimMccMnc(slotId)
    }
    //end by lipeng
}

/**
 * The common code about delete progress dialogs.
 */
/// M: Code analyze 018, For fix bug ALPS00278539, ANR occured when clicking
// the deleting message. @{
class DeleteProgressDialogUtil {
    /**
     * Gets a delete progress dialog.
     * @param context the activity context.
     * @return the delete progress dialog.
     */
    public static NewProgressDialog getProgressDialog(Context context) {
        NewProgressDialog dialog = new NewProgressDialog(context);
        dialog.setCancelable(false);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setMessage(context.getString(R.string.deleting));
        dialog.setMax(1); /* default is one complete */
        // ignore the search key, when deleting we do not want the search bar come out.
        dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                return (keyCode == KeyEvent.KEYCODE_SEARCH);
            }
        });
        return dialog;
    }
}

// M: fix bug ALPS00351620
class SearchProgressDialogUtil {
    /**
     * Gets a search progress dialog.
     * @param context the activity context.
     * @return the search progress dialog.
     */
    public static NewProgressDialog getProgressDialog(Context context) {
        NewProgressDialog dialog = new NewProgressDialog(context);
        dialog.setCancelable(false);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setMessage(context.getString(R.string.refreshing));
        dialog.setMax(1); /* default is one complete */
        // ignore the search key, when searching we do not want the search bar come out.
        dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                return (keyCode == KeyEvent.KEYCODE_SEARCH);
            }
        });
        return dialog;
    }
}

class NewProgressDialog extends ProgressDialog {
    private boolean mIsDismiss = false;
    public NewProgressDialog(Context context) {
        super(context);
    }

    public void dismiss() {
       if (isDismiss()) {
           super.dismiss();
       }
    }

    public synchronized void setDismiss(boolean isDismiss) {
        this.mIsDismiss = isDismiss;
    }

    public synchronized boolean isDismiss() {
        return mIsDismiss;
    }
	/*HQ_zhangjing add for al812 mms ui begin*/
	 public static	boolean isSimActivated(Context context,int subscription) {
	     if(TelephonyManager.getDefault().isMultiSimEnabled()){
    		 if(!TelephonyManager.getDefault().hasIccCard(subscription)){
    			 return false;
    		 }
		 if(TelephonyManager.getDefault().getSimState(subscription) 
		 	== TelephonyManager.SIM_STATE_READY){
		 	return true;	 	
		 }
    		return false;
	     }else{
	         return TelephonyManager.getDefault().hasIccCard();
	     }
	}
	/*HQ_zhangjing add end*/
  
}
/// @}
