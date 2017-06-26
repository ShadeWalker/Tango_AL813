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

import static android.content.res.Configuration.KEYBOARDHIDDEN_NO;
import static com.android.mms.transaction.ProgressCallbackEntity.PROGRESS_ABORT;
import static com.android.mms.transaction.ProgressCallbackEntity.PROGRESS_COMPLETE;
import static com.android.mms.transaction.ProgressCallbackEntity.PROGRESS_START;
import static com.android.mms.transaction.ProgressCallbackEntity.PROGRESS_STATUS_ACTION;
import static com.android.mms.ui.MessageListAdapter.COLUMN_ID;
import static com.android.mms.ui.MessageListAdapter.COLUMN_MSG_TYPE;
import static com.android.mms.ui.MessageListAdapter.PROJECTION;
import android.provider.ChildMode;//add by lihaizhou for ChildMode at 2015-08-04
import com.android.mtkex.chips.MTKRecipientEditTextView;
import com.android.mtkex.chips.MTKRecipientEditTextView.ChipProcessListener;
import com.android.mtkex.chips.RecipientEntry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import android.os.UserHandle;
import java.lang.ref.WeakReference;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SqliteWrapper;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.os.SystemProperties;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.provider.Settings;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsMessage;
import android.content.ClipboardManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputFilter.LengthFilter;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.TextKeyListener;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewStub;
import android.view.Window;
import android.view.WindowManager;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnKeyListener;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;

import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.PhoneConstants;
import com.android.mms.LogTag;
import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.TempFileProvider;
import com.android.mms.data.Contact;
import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;
import com.android.mms.data.Conversation.ConversationQueryHandler;
import com.android.mms.data.WorkingMessage;
import com.android.mms.data.WorkingMessage.MessageStatusListener;
import com.android.mms.draft.DraftManager;
import com.android.mms.draft.DraftService;
import com.android.mms.drm.DrmUtils;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.pdu.SendReq;
import com.android.mms.model.MediaModel;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.model.VCalendarModel;
import com.android.mms.model.VCardModel;
import com.android.mms.transaction.MessagingNotification;
import com.android.mms.ui.MessageUtils.ResizeImageResultCallback;
import com.android.mms.ui.RecipientsEditor.RecipientContextMenuInfo;
import com.android.mms.ui.SubSelectDialog.SubClickAndDismissListener;
import com.android.mms.util.PhoneNumberFormatter;
import com.android.mms.util.SendingProgressTokenManager;
import com.android.mms.util.StatusBarSelectorCreator;
import com.android.mms.util.ThumbnailManager;



/// M: import @{
import android.view.MotionEvent;
import android.view.View.OnTouchListener;
import android.view.LayoutInflater;
import android.os.SystemClock;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.provider.Browser;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Data;
import android.provider.MediaStore.Audio;
import android.telephony.TelephonyManager;
import android.telephony.SmsManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.preference.PreferenceManager;
import android.widget.LinearLayout;
import android.provider.MediaStore;
import android.util.AndroidException;

import com.android.mms.ExceedMessageSizeException;
import com.android.mms.util.ThreadCountManager;
import com.android.mms.util.MessageResource;
//import com.android.internal.telephony.Phone;

import com.android.mms.util.FeatureOption;
import com.mediatek.mms.ext.IMmsComposeExt;
import com.mediatek.mms.ext.DefaultMmsComposeExt;
import com.android.mms.MmsPluginManager;
import com.mediatek.mms.ext.IMmsDialogNotifyExt;
import com.mediatek.mms.ext.IMmsTextSizeAdjustExt;
import com.mediatek.mms.ext.IMmsUtilsExt;
import com.mediatek.mms.ext.IMmsTextSizeAdjustHost;
import com.mediatek.mms.ext.IStringReplacementExt;
import com.mediatek.mms.ext.ViewOnClickListener;
import com.mediatek.telephony.TelephonyManagerEx;
import com.android.mms.transaction.MmsSystemEventReceiver.OnShutDownListener;
import com.android.mms.transaction.MmsSystemEventReceiver.OnSubInforChangedListener;
import com.android.mms.transaction.SmsReceiverService;
import com.android.mms.util.MmsLog;
import com.android.mms.transaction.TransactionBundle;
import com.android.mms.transaction.TransactionService;
import com.android.mms.transaction.Transaction;
import com.google.android.mms.pdu.PduHeaders;
import com.android.mms.model.FileAttachmentModel;
import com.android.mms.model.TextModel;
import com.android.mms.util.DraftCache;
import com.android.mms.widget.MmsWidgetProvider;
import com.mediatek.common.MPlugin;
//import android.telephony.SmsMessageEx;

import com.mediatek.drm.OmaDrmStore;
import com.mediatek.ipmsg.util.IpMessageUtils;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;


/// @}
/// M: add for ip message
import android.media.ThumbnailUtils;
import android.provider.MediaStore.Video.Thumbnails;
import android.text.style.ImageSpan;

import com.android.mms.util.MessageConsts;
import com.android.mms.model.AudioModel;
import com.android.mms.model.ImageModel;
import com.android.mms.model.VideoModel;
import com.mediatek.ipmsg.ui.SharePanel;
import com.mediatek.mms.ipmessage.INotificationsListener;
import com.mediatek.mms.ipmessage.message.IpAttachMessage;
import com.mediatek.mms.ipmessage.message.IpImageMessage;
import com.mediatek.mms.ipmessage.message.IpMessage;
import com.mediatek.mms.ipmessage.IpMessageConsts;
import com.mediatek.mms.ipmessage.IpMessageConsts.ContactStatus;
import com.mediatek.mms.ipmessage.IpMessageConsts.FeatureId;
import com.mediatek.mms.ipmessage.IpMessageConsts.IpMessageMediaTypeFlag;
import com.mediatek.mms.ipmessage.IpMessageConsts.IpMessageSendMode;
import com.mediatek.mms.ipmessage.IpMessageConsts.IpMessageStatus;
import com.mediatek.mms.ipmessage.IpMessageConsts.IpMessageType;
import com.mediatek.mms.ipmessage.IpMessageConsts.ReminderType;
import com.mediatek.mms.ipmessage.IpMessageConsts.RemoteActivities;
import com.mediatek.mms.ipmessage.IpMessageConsts.SelectContactType;
import com.mediatek.mms.ipmessage.message.IpTextMessage;
import com.mediatek.mms.ipmessage.message.IpVCalendarMessage;
import com.mediatek.mms.ipmessage.message.IpVCardMessage;
import com.mediatek.mms.ipmessage.message.IpVideoMessage;
import com.mediatek.mms.ipmessage.message.IpVoiceMessage;
/// M: import @{

//add for attachment enhance
import com.android.mms.util.MmsContentType;
import com.mediatek.mms.ext.IMmsAttachmentEnhanceExt;

import android.text.Spannable;
///@}


//add for forward sms with sender
import com.mediatek.mms.ext.IMmsPreferenceExt;
import com.mediatek.storage.StorageManagerEx;
import com.mediatek.internal.telephony.CellConnMgr;

import com.android.mms.util.StatusBarSelectorReceiver;
import com.mediatek.internal.telephony.DefaultSmsSimSettings;

/*HQ_zhangjing add for al812 on2015-07-03 for resend the failed sms begin*/
import android.database.sqlite.SQLiteDiskIOException; 
import android.provider.Telephony.Sms.Outbox; 
import com.android.mms.transaction.SmsReceiver; 
/*HQ_zhangjing add end*/

/*HQ_zhangjing add for al812 mms ui begin*/
import huawei.android.widget.TimeAxisWidget;
import android.view.ContextThemeWrapper;
import com.huawei.android.app.ActionBarEx;
/*HQ_zhangjing add for al812 mms ui end*/

/*HQ_zhangjing 2015-08-10 modified for MMS merge begin*/
import android.app.AlertDialog.Builder;
/*HQ_zhangjing 2015-08-10 modified for MMS merge end*/
/*HQ_sunli 2015-09-16 HQ01359292 begin*/
import com.huawei.harassmentinterception.service.IHarassmentInterceptionService;
import com.huawei.harassmentinterception.service.IHarassmentInterceptionService.Stub;
/*HQ_sunli 2015-09-16 HQ01359292 end*/
/* modified by zhenghao HQ01445978 2015-10-26*/
import android.provider.ContactsContract.QuickContact;
import android.graphics.Rect;
import com.android.contacts.quickcontact.QuickContactActivity;
/* modified by zhenghao end */


/**/
import java.util.Locale;//add by lipeng

/**
 * This is the main UI for:
 * 1. Composing a new message;
 * 2. Viewing/managing message history of a conversation.
 *
 * This activity can handle following parameters from the intent
 * by which it's launched.
 * thread_id long Identify the conversation to be viewed. When creating a
 *         new message, this parameter shouldn't be present.
 * msg_uri Uri The message which should be opened for editing in the editor.
 * address String The addresses of the recipients in current conversation.
 * exit_on_sent boolean Exit this activity after the message is sent.
 */
public class ComposeMessageActivity extends Activity
        implements View.OnClickListener, TextView.OnEditorActionListener,
        MessageStatusListener, Contact.UpdateListener, OnShutDownListener,
        INotificationsListener,
 OnSubInforChangedListener,
        IMmsTextSizeAdjustHost, SubClickAndDismissListener {
    public static final int REQUEST_CODE_ATTACH_IMAGE     = 100;
    public static final int REQUEST_CODE_TAKE_PICTURE     = 101;
    public static final int REQUEST_CODE_ATTACH_VIDEO     = 102;
    public static final int REQUEST_CODE_TAKE_VIDEO       = 103;
    public static final int REQUEST_CODE_ATTACH_SOUND     = 104;
    public static final int REQUEST_CODE_RECORD_SOUND     = 105;
    public static final int REQUEST_CODE_CREATE_SLIDESHOW = 106;
    public static final int REQUEST_CODE_ECM_EXIT_DIALOG  = 107;
    public static final int REQUEST_CODE_ADD_CONTACT      = 108;
    public static final int REQUEST_CODE_PICK             = 109;
    // For op09 cc feature.
    public static final int REQUEST_CODE_PICK_CC             = 131;

    /// M: fix bug ALPS00490684, update group mms state from GROUP_PARTICIPANTS to setting @{
    public static final int REQUEST_CODE_GROUP_PARTICIPANTS = 130;
    /// @}
    /// M: fix bug ALPS00448677, update or delete Contact Chip
    public static final int REQUEST_CODE_VIEW_CONTACT     = 111;
    private Contact mInViewContact;
    /// @}

    private static final String TAG = "Mms/compose";
    /// M: add for ip message
    private static final String TAG_DIVIDER = "Mms/divider";

    private static final boolean DEBUG = false;
    private static final boolean TRACE = false;
    private static final boolean LOCAL_LOGV = false;

    // Menu ID
    private static final int MENU_ADD_SUBJECT           = 0;
    private static final int MENU_DELETE_THREAD         = 1;
    private static final int MENU_ADD_ATTACHMENT        = 2;
    private static final int MENU_DISCARD               = 3;
    private static final int MENU_SEND                  = 4;
    private static final int MENU_CALL_RECIPIENT        = 5;
    private static final int MENU_CONVERSATION_LIST     = 6;
    private static final int MENU_DEBUG_DUMP            = 7;
    /// M: add for ip message, option menu
    private static final int MENU_INVITE_FRIENDS_TO_CHAT = 100;
    private static final int MENU_SELECT_MESSAGE        = 101;
    private static final int MENU_MARK_AS_SPAM          = 102;
    private static final int MENU_REMOVE_SPAM           = 103;
    private static final int MENU_VIEW_ALL_MEDIA        = 105;
    private static final int MENU_INVITE_FRIENDS_TO_IPMSG = 107;
    private static final int MENU_SEND_BY_SMS_IN_JOYN_MODE = 108;
    private static final int MENU_SEND_BY_JOYN_IN_SMS_MODE = 109;
    /// M: add for CT feature menu
    private static final int MENU_ADD_MMS_CC            = 1000;

    // Context menu ID
    private static final int MENU_VIEW_CONTACT          = 12;
    private static final int MENU_ADD_TO_CONTACTS       = 13;

    private static final int MENU_EDIT_MESSAGE          = 14;
    private static final int MENU_VIEW_SLIDESHOW        = 16;
    private static final int MENU_VIEW_MESSAGE_DETAILS  = 17;
    private static final int MENU_DELETE_MESSAGE        = 18;
    private static final int MENU_SEARCH                = 19;
    private static final int MENU_DELIVERY_REPORT       = 20;
    private static final int MENU_FORWARD_MESSAGE       = 21;
    private static final int MENU_CALL_BACK             = 22;
    private static final int MENU_SEND_EMAIL            = 23;
    private static final int MENU_COPY_MESSAGE_TEXT     = 24;
    private static final int MENU_COPY_TO_SDCARD        = 25;
    private static final int MENU_INSERT_SMILEY         = 26;
    private static final int MENU_ADD_ADDRESS_TO_CONTACTS = 27;
    private static final int MENU_LOCK_MESSAGE          = 28;
    private static final int MENU_UNLOCK_MESSAGE        = 29;
    private static final int MENU_SAVE_RINGTONE         = 30;
    private static final int MENU_PREFERENCES           = 31;
    /// M: google jb.mr1 patch
    private static final int MENU_GROUP_PARTICIPANTS    = 32;
    private static final int MENU_ADD_TO_BLACKLIST     = 33;
    private static final int MENU_REMOVE_FROM_BLACKLIST = 34;
    private static final int MENU_CHAT_SETTING          = 137; // add for chat setting

    /// M: add for ip message, context menu
    private static final int MENU_RETRY                 = 200;
    private static final int MENU_DELETE_IP_MESSAGE     = 201;
    private static final int MENU_SHARE                 = 202;
    private static final int MENU_SAVE_ATTACHMENT       = 205;
    private static final int MENU_VIEW_IP_MESSAGE       = 206;
    private static final int MENU_COPY = 207;
    private static final int MENU_SEND_VIA_TEXT_MSG = 208;
    private static final int MENU_EXPORT_SD_CARD = 209;
    private static final int MENU_FORWARD_IPMESSAGE = 210;
	//HQ_zhangjing add for al812 on2015-07-03 for resend the failed sms 
	private static final int MENU_RESEND_MESSAGE = 211;
    private static final int RECIPIENTS_MAX_LENGTH = 312;

    private static final int MESSAGE_LIST_QUERY_TOKEN = 9527;
    private static final int MESSAGE_LIST_QUERY_AFTER_DELETE_TOKEN = 9528;

    private static final int DELETE_MESSAGE_TOKEN  = 9700;
    /// M: add for ip message, query unread message
    private static final int MESSAGE_LIST_UNREAD_QUERY_TOKEN   = 9800;

    private static final int CHARS_REMAINING_BEFORE_COUNTER_SHOWN = 10;

    private static final long NO_DATE_FOR_DIALOG = -1L;

    private static final String KEY_EXIT_ON_SENT = "exit_on_sent";
    private static final String KEY_FORWARDED_MESSAGE = "forwarded_message";
    private static final String KEY_APPEND_MESSAGE = "append_attachment";

    private static final String EXIT_ECM_RESULT = "exit_ecm_result";

    // When the conversation has a lot of messages and a new message is sent, the list is scrolled
    // so the user sees the just sent message. If we have to scroll the list more than 20 items,
    // then a scroll shortcut is invoked to move the list near the end before scrolling.
    private static final int MAX_ITEMS_TO_INVOKE_SCROLL_SHORTCUT = 20;

    // Any change in height in the message list view greater than this threshold will not
    // cause a smooth scroll. Instead, we jump the list directly to the desired position.
    private static final int SMOOTH_SCROLL_THRESHOLD = 200;

    ///M: ALPS00592380: status of detection
    private static final int DETECT_INIT = 0;
    private static final int DETECT_ANGLE_BRACKETS = 1;
    private static final int DETECT_ANGLE_BRACKETS_WITH_WORD = 2;

    private ContentResolver mContentResolver;

    private BackgroundQueryHandler mBackgroundQueryHandler;

    private Conversation mConversation;     // Conversation we are working in

    private boolean mExitOnSent;            // Should we finish() after sending a message?
                                            // TODO: mExitOnSent is obsolete -- remove
    private boolean mForwardMessageMode;

    private View mTopPanel;                 // View containing the recipient and subject editors
    private View mBottomPanel;              // View containing the text editor, send button, ec.
    private EnhanceEditText mTextEditor;           // Text editor to type your message into
    private TextView mTextCounter;          // Shows the number of characters used in text editor
    private TextView mSendButtonMms;        // Press to send mms
    private ImageButton mSendButtonSms;     // Press to send sms
	/*HQ_zhangjing add for sms ui begin*/
	private TextView mTextCounterForDouble;
    private TextView mSub1SendButton;             
    private TextView mSub2SendButton;
	private View mTimeView;
	private LinearLayout mPanelContainer;
	private LinearLayout mUniqueSendBtnContainer;
	private LinearLayout mDoubleSendBtnContainer;
	/*HQ_zhangjing add end*/
    private EditText mSubjectTextEditor;    // Text editor for MMS subject

    private AttachmentEditor mAttachmentEditor;
    private View mAttachmentEditorScrollView;

    private MessageListView mMsgListView;        // ListView for messages in this conversation
    /// M: @{
    public MessageListAdapter mMsgListAdapter = null;  // and its corresponding ListAdapter
    /// @}
    private RecipientsEditor mRecipientsEditor;  // UI control for editing recipients
    private ImageButton mRecipientsPicker;       // UI control for recipients picker

    private boolean mIsKeyboardOpen;             // Whether the hardware keyboard is visible
    /// M: fix bug ALPS00419856, set TextEditor Height = four when unlock screen @{
    private boolean mIsSoftKeyBoardShow;
    private static final int SOFT_KEY_BOARD_MIN_HEIGHT = 150;
    /// @}
    private boolean mIsLandscape;                // Whether we're in landscape mode

    private boolean mPossiblePendingNotification;   // If the message list has changed, we may have
                                                    // a pending notification to deal with.

    private boolean mToastForDraftSave;   // Whether to notify the user that a draft is being saved

    private boolean mSentMessage;       // true if the user has sent a message while in this
                                        // activity. On a new compose message case, when the first
                                        // message is sent is a MMS w/ attachment, the list blanks
                                        // for a second before showing the sent message. But we'd
                                        // think the message list is empty, thus show the recipients
                                        // editor thinking it's a draft message. This flag should
                                        // help clarify the situation.

    private WorkingMessage mWorkingMessage;         // The message currently being composed.

    private AlertDialog mSmileyDialog;

    private boolean mWaitingForSubActivity;
    private int mLastRecipientCount;            // Used for warning the user on too many recipients.
    private AttachmentTypeSelectorAdapter mAttachmentTypeSelectorAdapter;

    private boolean mSendingMessage;    // Indicates the current message is sending, and shouldn't send again.

    private Intent mAddContactIntent;   // Intent used to add a new contact

    private Uri mTempMmsUri;            // Only used as a temporary to hold a slideshow uri
    private long mTempThreadId;         // Only used as a temporary to hold a threadId
    /// M: fix bug for ConversationList select all performance ,update selected threads array.@{
    private long mSelectedThreadId = 0;
    /// @}

    private AsyncDialog mAsyncDialog;   // Used for background tasks.

    private String mDebugRecipients;
    private int mLastSmoothScrollPosition;
    private boolean mScrollOnSend;      // Flag that we need to scroll the list to the end.

    private int mSavedScrollPosition = -1;  // we save the ListView's scroll position in onPause(),
                                            // so we can remember it after re-entering the activity.
                                            // If the value >= 0, then we jump to that line. If the
                                            // value is maxint, then we jump to the end.
    private long mLastMessageId;

    ///M: ALPS00726802, this int save the position in cursor of the clicked item
    private int mClickedItemPosition = -1;

    private boolean  mErrorDialogShown = true;
	//HQ_zhangjing 2015-11-12 modified for CQ HQ01455134 remove the invalid recipient
	private boolean showInvalidRecipintsDlg = true;
    /**
     * Whether this activity is currently running (i.e. not paused)
     */
    private boolean mIsRunning;
    // key for extras and icicles
    public static final String THREAD_ID = "thread_id";

    ///M: add for fix issue ALPS00380788
    private ProgressDialog mCellProgressDialog;

    private ImageView mWallPaper; //wall_paper

    /// M: fix bug ALPS00397146, removeThumbnailManager uri
    // (Content://media/external/images/media/) when it rotated
    private static final HashMap<String, Integer> sDegreeMap = new HashMap<String, Integer>();
    /// @}

/// M: add for ip message {@
    private static final String IPMSG_TAG = "Mms/ipmsg/compose";

    private static final int REQUEST_CODE_IPMSG_TAKE_PHOTO        = 200;
    private static final int REQUEST_CODE_IPMSG_RECORD_VIDEO      = 201;
    private static final int REQUEST_CODE_IPMSG_SHARE_CONTACT     = 203;
    private static final int REQUEST_CODE_IPMSG_CHOOSE_PHOTO      = 204;
    private static final int REQUEST_CODE_IPMSG_CHOOSE_VIDEO      = 205;
    private static final int REQUEST_CODE_IPMSG_RECORD_AUDIO      = 206;
    private static final int REQUEST_CODE_IPMSG_CHOOSE_AUDIO      = 208;
    private static final int REQUEST_CODE_IPMSG_SHARE_VCALENDAR   = 209;

    public static final int REQUEST_CODE_IPMSG_PICK_CONTACT      = 210;
    public static final int REQUEST_CODE_INVITE_FRIENDS_TO_CHAT  = 211;

    public static final int REQUEST_CODE_IPMSG_SHARE_FILE        = 214;
    public static final int REQUEST_CODE_IP_MSG_PICK_CONTACTS     = 215;

    private static final int SMS_CONVERT                            = 0;
    private static final int MMS_CONVERT                            = 1;
    private static final int SERVICE_IS_NOT_ENABLED                 = 0;
    private static final int RECIPIENTS_ARE_NOT_IP_MESSAGE_USER     = 1;
    // M: add logic for the current ipmessage user can not send ip message
    private static final int RECIPIENTS_IP_MESSAGE_NOT_SENDABLE     = 2;

    ///M: for forward ipMsg
    public static final String FORWARD_IPMESSAGE = "forwarded_ip_message";
    public static final String IP_MESSAGE_ID = "ip_msg_id";
    public static final String CHOICE_FILEMANAGER_ACTION = "com.mediatek.filemanager.ADD_FILE";
    public static final String FILE_SCHEMA = "file://";

    ///M: add for number balance ; add in OP09 project as a common function
    private static final String PICK_CONTACT_NUMBER_BALANCE = "NUMBER_BALANCE";

    //public static final String SELECTED_ID = "SELECTID";

    private String mPhotoFilePath = "";
    private String mDstPath = "";
    private int mDuration = 0;
    private String mCalendarSummary = "";

    // chat mode number
    private String mChatModeNumber = "";
    private String mBeforeTextChangeString = "";

    // ipmessage status
    private boolean mIsIpMessageRecipients = false;
    private boolean mIsIpServiceEnabled = false;

    private boolean mShowKeyBoardFromShare = false;
    private Object mWaitingImeChangedObject = new Object();

    // unread divider
    private boolean mShowUnreadDivider = true;
    private boolean mIsMarkMsgAsRead = true;

    ///M: for check whether or not can convert IpMessage to Common message.
    ///M: can not support translating media through common message , -1;
    private static final int MESSAGETYPE_UNSUPPORT = -1;
    private static final int MESSAGETYPE_COMMON = 0;
    private static final int MESSAGETYPE_TEXT = 1;
    private static final int MESSAGETYPE_MMS = 2;
    /*HQ_sunli 20150916 HQ01359292 begin*/
    private static final String HARASSMENT_INTERCEPTION_SERVICE =                                                                                               
    	      "com.huawei.harassmentinterception.service.HarassmentInterceptionService";	
	IBinder harassmentIntercepterService;
	IHarassmentInterceptionService hisStub;
    /*HQ_sunli 20150916 HQ01359292 end*/
    private String mIpMessageVcardName = "";

    private InputMethodManager mInputMethodManager = null;

    private ImageButton mShareButton;
    private SharePanel mSharePanel;
    private ImageView mIpMessageThumbnail;
    /// M: add for ipmessge for thumbnail delete
    private ImageView mIpMessageThumbnailDelete;
    private ImageButton mSendButtonIpMessage;     // press to send ipmessage

    private TextView mTypingStatus;
    private LinearLayout mTypingStatusView;
    private boolean mIsDestroyTypingThread = false;
    private long mLastSendTypingStatusTimestamp = 0L;

    ///M: for indentify that just send common message.
    private boolean mJustSendMsgViaCommonMsgThisTime = false;
    ///M: whether or not show invite friends to use ipmessage interface.
    private boolean mShowInviteMsg = false;

    ///M: working IP message
    private int mIpMessageDraftId = 0;
    private IpMessage mIpMessageDraft;
    private IpMessage mIpMessageForSend;
    private boolean mIsClearTextDraft = false;
    private AlertDialog mReplaceDialog = null;
    private int mCurrentChatMode = IpMessageConsts.ChatMode.XMS;

    ///M: ipmessage invite panel
    private View mInviteView;
    private TextView mInviteMsg;
    private Button mInvitePostiveBtn;
    private Button mInviteNegativeBtn;
    private static final String KEY_SELECTION_SUBID = "SUBID";
    private AlertDialog mPlaneModeDialog = null;
    ///M: actionbar customer view
    private View mActionBarCustomView;
    private TextView mTopTitle;
    private ImageView mMuteLogo;
    private TextView mTopSubtitle;
    private ImageView mFullIntegratedView;
	
    /*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display begin*/
	private ImageView mShoMoreMenu;
	boolean needShowMoreMenu = true;

	boolean shouldShowLast = true;
    /*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display end*/
    /*HQ_sunli 20151113 HQ01499362 begin*/
    private String mPowerMode="false";
    /*HQ_sunli 20151113 HQ01499362 end*/
    private boolean mHadToSlideShowEditor = false;

    /// M: record cell progress dialog is showing or not
    private boolean mIsCellDialogShowing = false;

    /// M: for ALPS00475359 op01 only, fix ANR when edit mms with deleting 1500 messages @{
    private Handler mSetWallPaperHandler = null;
    private static final int SET_WALLPAPER_FROM_RESOURCE = 10001;
    private static final int SET_WALLPAPER_FROM_BITMAP   = 10002;
    /// @} for ALPS00475359 end

	/*HQ_zhangjing on 2015-09-11 add vcard contacts dialog begin*/
	private final int ADD_TEXT_VCARD = 0;//add contacts by text
	private final int ADD_VCARD = 1;//add contacts by mms
	/*HQ_zhangjing on 2015-09-11 add vcard contacts dialog end*/
	
	

    private boolean mForwardingMessage = false;

    /// M: fix bug ALPS01258201, mAsyncTaskNum for count asyncTask number
    //  ShowRunnable can avoid dismiss progressDialog.
    private int mAsyncTaskNum;

    /// M: add for OP09, mms cc feature. UI control for editing cc recipients
    private RecipientsEditor mRecipientsCcEditor;

    /// M: fix bug ALPS01505548, delete VCard temp file
    private ArrayList<String> mVCardFiles = new ArrayList<String>();

    private ContextMenu mChipViewMenu = null;

    private boolean mIsActivityPaused = true;

    private StatusBarSelectorReceiver mStatusBarSelectorReceiver; 

	/*HQ_zhangjing 2015-08-10 modified for MMS merge begin*/
	public  ArrayList<Integer> mMenuitemResponseIds =new ArrayList<Integer>();
    private ArrayList<CharSequence> mMenuItemStringIds = new ArrayList<CharSequence>();
	/*HQ_zhangjing 2015-08-10 modified for MMS merge end*/
    private void dismissProgressDialog() {
        Log.d(TAG, "Composer reduce mAsyncTaskNum = " + (--mAsyncTaskNum));
        if (mAsyncTaskNum == 0) {
            runOnUiThread(new Runnable() {
                public void run() {
                    Log.d(TAG, "dimiss and reset ProgressDialog");
                    getAsyncDialog().dismissProgressDialog();
                    getAsyncDialog().resetProgressDialog();
                }
            });
        }
    }

    class ShowRunnable implements Runnable {
        public void run() {
        }
    }

    private Runnable mHideTypingStatusRunnable = new Runnable() {
        @Override
        public void run() {
            MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "notificationsReceived(): hide typing status.");
            if (null != mTypingStatusView) {
                mTypingStatusView.setVisibility(View.GONE);
            }
        }
    };

    private Runnable mShowTypingStatusRunnable = new Runnable() {
        @Override
        public void run() {
            MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "notificationsReceived(): show IM status view.");
            if (null != mTypingStatusView) {
                String text = "";
                if (mChatSenderName != null  && !TextUtils.isEmpty(mChatSenderName)) {
                    text = mChatSenderName + " " + IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                    .getSingleString(IpMessageConsts.string.ipmsg_typing_text);
                } else {
                    MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "mChatSenderName is null or empty");
                    return;
                }
                mTypingStatus.setText(text);
                mTypingStatusView.setVisibility(View.VISIBLE);
            }
        }
    };
    /// M: define runnable for query so that can be removed to solve cursor leak. @{
    private Runnable mQueryUnReadMsgListRunnable = new Runnable() {
        public void run() {
            /// M: modify for OP09 @{
            Uri uri = null;
            if (MmsConfig.isMassTextEnable()) {
                uri = mMmsComposePlugin.getConverationUri(null, mConversation.getThreadId());
            } else {
                uri = mConversation.getUri();
            }
            final Uri conversationUri = uri;
            /// @}
            if (conversationUri == null) {
                log("##### startMsgListQuery: conversationUri is null, bail!");
                return;
            }
            final long threadId = mConversation.getThreadId();
            final String unreadSelection = "read = 0";

            mBackgroundQueryHandler.startQuery(
                    MESSAGE_LIST_UNREAD_QUERY_TOKEN, threadId, conversationUri,
                    PROJECTION, unreadSelection, null, null);
        }
    };

    private Runnable mQueryMsgListRunnable = new Runnable() {
        public void run() {
            /// M: modify for OP09 @{
            Uri uri = null;
            if (MmsConfig.isMassTextEnable()) {
                uri = mMmsComposePlugin.getConverationUri(null, mConversation.getThreadId());
            } else {
                uri = mConversation.getUri();
            }
            final Uri conversationUri = uri;
            /// @}
            if (conversationUri == null) {
                log("##### startMsgListQuery: conversationUri is null, bail!");
                return;
            }
            final long threadId = mConversation.getThreadId();
            final String order = mShowUnreadDivider ? "read DESC" : null;

            mBackgroundQueryHandler.startQuery(
                    MESSAGE_LIST_QUERY_TOKEN, threadId, conversationUri,
                    PROJECTION, null, null, order);
        }
    };
    /// @}

    private Object mShowTypingLockObject = new Object();
    private Thread mShowTypingThread = new Thread(new Runnable() {
        @Override
        public void run() {
            String showingStr = IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                            .getSingleString(IpMessageConsts.string.ipmsg_typing_text);
            final String displayString0 = showingStr + ".    ";
            final String displayString1 = showingStr + "..   ";
            final String displayString2 = showingStr + "...  ";
            int i = 0;
            while (!mIsDestroyTypingThread) {
                while (null != mTypingStatusView && mTypingStatusView.getVisibility() != View.GONE) {
                    if (ComposeMessageActivity.this.isFinishing()) {
                        break;
                    }
                    switch (i % 3) {
                    case 0:
                        mIpMsgHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "mShowTypingThread: display 0.");
                                mTypingStatus.setText(displayString0);
                            }
                        });
                        i++;
                        break;
                    case 1:
                        mIpMsgHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "mShowTypingThread: display 1.");
                                mTypingStatus.setText(displayString1);
                            }
                        });
                        i++;
                        break;
                    case 2:
                        mIpMsgHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "mShowTypingThread: display 2.");
                                mTypingStatus.setText(displayString2);
                            }
                        });
                        i++;
                        break;
                    default:
                        break;
                    }
                    synchronized (this) {
                        try {
                            this.wait(1000);
                        } catch (InterruptedException e) {
                            MmsLog.d(TAG, "InterruptedException");
                        }
                    }
                }
                synchronized (mShowTypingLockObject) {
                    try {
                        mShowTypingLockObject.wait();
                    } catch (InterruptedException e) {
                        MmsLog.d(TAG, "InterruptedException");
                    }
                }
            }
            MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "mShowTypingThread: destroy thread.");
        }
    }, "showTypingThread");

    public Handler mIpMsgHandler = new Handler() {
        public void handleMessage(Message msg) {
            MmsLog.d(IPMSG_TAG, "handler msg type is: " + msg.what);
            MmsLog.d(IPMSG_TAG, "MmsConfig.getIpMessagServiceId() = "
                + ", MmsConfig.isServiceEnabled() = " + MmsConfig.isServiceEnabled(ComposeMessageActivity.this)
                + ", isNetworkConnected = " + isNetworkConnected(ComposeMessageActivity.this)
                + ", has SD = " + IpMessageUtils.getSDCardStatus());
            switch (msg.what) {
            case MessageConsts.ACTION_SHARE:
                if (!IpMessageUtils.getIpMessagePlugin(ComposeMessageActivity.this).isActualPlugin()) {
                    doMmsAction(msg);
                } else if (MmsConfig.isServiceEnabled(ComposeMessageActivity.this)
                        && isNetworkConnected(ComposeMessageActivity.this)
                        && IpMessageUtils.getSDCardStatus()) {
                    doMoreAction(msg);
                } else {
                    doMoreActionForMms(msg);
                }
                break;
            case MessageUtils.UPDATE_SENDBUTTON:
                mMessageSubId = (int)Settings.System.getLong(getContentResolver(),
                        Settings.System.SMS_SIM_SETTING,
                        Settings.System.DEFAULT_SIM_NOT_SET);
                if (mCurrentChatMode != IpMessageConsts.ChatMode.XMS) {
                        mIsIpServiceEnabled = MmsConfig
                                .isServiceEnabled(ComposeMessageActivity.this);
                }
                updateSendButtonState();
                break;
            default:
                MmsLog.d(IPMSG_TAG, "msg type: " + msg.what + "not handler");
                break;
            }
            super.handleMessage(msg);
        }
    };
/// @}

    @SuppressWarnings("unused")
    public static void log(String logMsg) {
        Thread current = Thread.currentThread();
        long tid = current.getId();
        StackTraceElement[] stack = current.getStackTrace();
        String methodName = stack[3].getMethodName();
        // Prepend current thread ID and name of calling method to the message.
        logMsg = "[" + tid + "] [" + methodName + "] " + logMsg;
        Log.d(TAG, logMsg);
    }

    //==========================================================
    // Inner classes
    //==========================================================

    private void editSlideshow() {
        // The user wants to edit the slideshow. That requires us to persist the slideshow to
        // disk as a PDU in saveAsMms. This code below does that persisting in a background
        // task. If the task takes longer than a half second, a progress dialog is displayed.
        // Once the PDU persisting is done, another runnable on the UI thread get executed to start
        // the SlideshowEditActivity.

        /// M: fix bug ALPS00520531, Do not load draft when compose is going to edit slideshow
//        mContentResolver.unregisterContentObserver(mDraftChangeObserver);

        getAsyncDialog().runAsync(new Runnable() {
            @Override
            public void run() {
                // This runnable gets run in a background thread.
                mTempMmsUri = mWorkingMessage.saveAsMms(false);
            }
        }, new Runnable() {
            @Override
            public void run() {
                // Once the above background thread is complete, this runnable is run
                // on the UI thread.
                if (mTempMmsUri == null) {
                    mWaitingForSubActivity = false;
                    return;
                }
                long threadId = mConversation.getThreadId();
                if (threadId <= 0) {
                    if (!mConversation.getRecipients().isEmpty()) {
                        threadId = mConversation.ensureThreadId();
                    }
                }
                /// M: add for fix ALPS01317511 @{
                if (MmsConfig.isSupportMessagingNotificationProxy()) {
                    mHadToSlideShowEditor = true;
                }
                /// @}
                Intent intent = new Intent(ComposeMessageActivity.this,
                        SlideshowEditActivity.class);
                intent.setData(mTempMmsUri);
                intent.putExtra("thread_id", threadId);
                startActivityForResult(intent, REQUEST_CODE_CREATE_SLIDESHOW);
            }
        // M: fix bug ALPS00351027
        }, R.string.sync_mms_to_db);
    }

    private final Handler mAttachmentEditorHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            MmsLog.d(TAG, "mAttachmentEditorHandler, handleMessage = " + msg.what);
            /// M: Code analyze 026, If the two clicks are too close. @{
            long oldTime = mAttachClickTime;
            mAttachClickTime = SystemClock.elapsedRealtime();
            if ((mAttachClickTime - oldTime < 500) && (mAttachClickTime - oldTime > 0)) {
                MmsLog.d(TAG, "mAttachmentEditorHandler, ignore a click if too close");
                return;
            }
            /// M: add for fix issue ALPS00873579 @{
            if (MmsConfig.getFolderModeEnabled() && mCompressingImage) {
                MmsLog.d("MmsTest", "is attaching image, can not operate attachment");
                return;
            }
            /// @}
            switch (msg.what) {
                case AttachmentEditor.MSG_EDIT_SLIDESHOW: {
                    /// M: Code analyze 024, If the click operator can be responsed. @{
                    if (mClickCanResponse) {
                        mClickCanResponse = false;
                        /// M: Code analyze 038, If the user is editing slideshow now.
                        /// Do not allow the activity finish but return directly when back key is entered. @{
                        mIsEditingSlideshow = true;
                        /// @}
                        editSlideshow();
                    }
                    /// @}
                    break;
                }
                case AttachmentEditor.MSG_SEND_SLIDESHOW: {
                    if (isPreparedForSending()) {
                        /// M: add for OP09 @{
                        if (MmsConfig.isDualSendButtonEnable() && msg != null) {
                            mSendSubIdForDualBtn = msg.getData().getInt("send_sub_id");
                        }
                        /// @}
                        /// M: Code analyze 028, Before sending message,check the recipients count
                        /// and add sub card selection dialog if multi sub cards exist.@{
                        // ComposeMessageActivity.this.confirmSendMessageIfNeeded();
                        checkRecipientsCount();
                        /// @}
                    }
                    break;
                }
                case AttachmentEditor.MSG_VIEW_IMAGE:
                case AttachmentEditor.MSG_PLAY_VIDEO:
                case AttachmentEditor.MSG_PLAY_AUDIO:
                case AttachmentEditor.MSG_PLAY_SLIDESHOW:
                    /// M: Code analyze 024, If the click operator can be responsed. @{
                    if (mClickCanResponse) {
                        mClickCanResponse = false;
                        viewMmsMessageAttachment(msg.what);
                    }
                    /// @}
                    /// M: Code analyze 051, Hide input keyboard.@{
                    hideInputMethod();
                    /// @}
                    break;

                case AttachmentEditor.MSG_REPLACE_IMAGE:
                    /// M: @{
                    getSharedPreferences("SetDefaultLayout", 0).edit().putBoolean("SetDefaultLayout", false).commit();
                    /// @}
                case AttachmentEditor.MSG_REPLACE_VIDEO:
                case AttachmentEditor.MSG_REPLACE_AUDIO:
                    /// M: Code analyze 051, Hide input keyboard.@{
                    hideInputMethod();
                    /// @}
                    showAddAttachmentDialog(false);
                    break;

                case AttachmentEditor.MSG_REMOVE_ATTACHMENT:
                    /// M: fix bug ALPS01538338
                    if (!mSendButtonCanResponse) {
                        MmsLog.d(TAG, "handle MSG_REMOVE_ATTACHMENT return");
                        return;
                    }
                    mWorkingMessage.removeAttachment(true);
                    break;

                //add for attachment enhance
                case AttachmentEditor.MSG_REMOVE_EXTERNAL_ATTACHMENT:
                    if (!mSendButtonCanResponse) {
                        MmsLog.d(TAG, "handle MSG_REMOVE_EXTERNAL_ATTACHMENT return");
                        return;
                    }
                    mWorkingMessage.removeExternalAttachment();
                    break;
                case AttachmentEditor.MSG_REMOVE_SLIDES_ATTACHMENT:
                    if (!mSendButtonCanResponse) {
                        MmsLog.d(TAG, "handle MSG_REMOVE_SLIDES_ATTACHMENT return");
                        return;
                    }
                    mWorkingMessage.removeSlidesAttachment();
                    break;
                default:
                    break;
            }
        }
    };


    private void viewMmsMessageAttachment(final int requestCode) {
        final SlideshowModel slideshow = mWorkingMessage.getSlideshow();
        if (slideshow == null) {
            /// M: ALPS01846425. If mms has been sent, slideshow is null. @{
//            throw new IllegalStateException("mWorkingMessage.getSlideshow() == null");
            MmsLog.e(TAG, "viewMmsMessageAttachment slideshow is null",
                    new IllegalStateException("mWorkingMessage.getSlideshow() == null"));
            return;
            /// @}
        }
        /// M: Code analyze 035, The audio becomes "simple" slideshow.
        /// Launch the slideshow activity or MmsPlayerActivity to play/view media attachment. @{
        SlideModel slideOne = slideshow.get(0);
        if (slideshow.isSimple() && slideOne != null && !slideOne.hasAudio()) {
            MessageUtils.viewSimpleSlideshow(this, slideshow);
        } else {

            // M: change feature ALPS01751464
            if (slideshow.isSimple() && slideOne != null && slideOne.hasAudio()) {
                MediaModel model = slideOne.getAudio();
                if (model != null && model.hasDrmContent()) {
                    MessageUtils.showDrmAlertDialog(ComposeMessageActivity.this);
                    return;
                }
            }

            // The user wants to view the slideshow. That requires us to persist the slideshow to
            // disk as a PDU in saveAsMms. This code below does that persisting in a background
            // task. If the task takes longer than a half second, a progress dialog is displayed.
            // Once the PDU persisting is done, another runnable on the UI thread get executed to
            // start the SlideshowActivity.
            getAsyncDialog().runAsync(new Runnable() {
                @Override
                public void run() {
                    // This runnable gets run in a background thread.
                    mTempMmsUri = mWorkingMessage.saveAsMms(false);
                }
            }, new Runnable() {
                @Override
                public void run() {
                    // Once the above background thread is complete, this runnable is run
                    // on the UI thread.
                    // Launch the slideshow activity to play/view.
                    Intent intent;
                    SlideModel slide = slideshow.get(0);
                    /// M: play the only audio directly
                    if ((slideshow.isSimple() && slide != null && slide.hasAudio())
                        || (requestCode == AttachmentEditor.MSG_PLAY_AUDIO)) {
                        intent = new Intent(ComposeMessageActivity.this, SlideshowActivity.class);
                    } else {
                        intent = new Intent(ComposeMessageActivity.this, MmsPlayerActivity.class);
                    }
                    intent.setData(mTempMmsUri);
                    if (mTempMmsUri == null) {
                        MmsLog.d(TAG, "viewMmsMessageAttachment mTempMmsUri == null");
                        return;
                    }
                    if (requestCode > 0) {
                        startActivityForResult(intent, requestCode);
                    } else {
                        startActivity(intent);
                    }
                    //MessageUtils.launchSlideshowActivity(ComposeMessageActivity.this, mTempMmsUri,
                    //         requestCode);
                    /// @}
                }
            // M: fix bug ALPS00351027
            }, R.string.sync_mms_to_db);
        }
    }

    // Whether or not we are currently enabled for SMS. This field is updated in onStart to make
    // sure we notice if the user has changed the default SMS app.
    private boolean mIsSmsEnabled;
    /*add by lihaizhou for Prohibit Msg for ChildMode by begin */
 private boolean isChildModeOn() 
	{
	        String isOn = ChildMode.getString(this.getContentResolver(),
	       		ChildMode.CHILD_MODE_ON);
	        if(isOn != null && "1".equals(isOn)){
	       	 return true;
	        }else {
	       	 return false;
			}
		       	 
	}
   private boolean isProhibitDeleteSmsmms() {
		     String isOn = ChildMode.getString(this.getContentResolver(),
		    		ChildMode.FORBID_DELETE_MESSAGE );
		     if(isOn != null && "1".equals(isOn) && isChildModeOn()){
		    	 return true;
		     }else {
		    	 return false;
			}	 
    }
  private boolean isProhibitSendSmsmms() {
		      String isOn = ChildMode.getString(this.getContentResolver(),
		     		ChildMode.FORBID_SEND_MESSAGE_ON );
		      if(isOn != null && "1".equals(isOn) && isChildModeOn()){
		     	 return true;
		      }else {
		     	 return false;
				}	 
		 }
/*add by lihaizhou for Prohibit Msg for ChildMode by end */
    private final Handler mMessageListItemHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            MessageItem msgItem = (MessageItem) msg.obj;
                /// M: move if into switch, we have item not use it.
                /// M: Code analyze, fix bug ALPS00358964
                switch (msg.what) {
                    case MessageListItem.MSG_LIST_DETAILS:
                        if (msgItem != null) {
                            showMessageDetails(msgItem);
                        }
                        break;

                    case MessageListItem.MSG_LIST_EDIT:
                        if (msgItem != null && mIsSmsEnabled) {
                            editMessageItem(msgItem);
                            updateSendButtonState();
                            drawBottomPanel();
                        }
                        break;

                    case MessageListItem.MSG_LIST_PLAY:
                        if (msgItem != null) {
                            switch (msgItem.mAttachmentType) {
                                case WorkingMessage.IMAGE:
                                case WorkingMessage.VIDEO:
                                case WorkingMessage.AUDIO:
                                case WorkingMessage.SLIDESHOW:
                                    MessageUtils.viewMmsMessageAttachment(ComposeMessageActivity.this,
                                            msgItem.mMessageUri, msgItem.mSlideshow,
                                            getAsyncDialog());
                                    break;
                            }
                        }
                        break;

                    /// M: Code analyze 039, When the cache add new item,
                    /// notifiy ComposeMessageAcitivity the data has been changed .@{
                    case MessageListAdapter.MSG_LIST_NEED_REFRASH: {
                        boolean isClearCache = msg.arg1 == MessageListAdapter.MESSAGE_LIST_REFRASH_WITH_CLEAR_CACHE;
                        MmsLog.d(MessageListAdapter.CACHE_TAG, "mMessageListItemHandler.handleMessage(): " +
                                    "run adapter notify in mMessageListItemHandler. isClearCache = " + isClearCache);
                        mMsgListAdapter.setClearCacheFlag(isClearCache);
                        mMsgListAdapter.notifyDataSetChanged();
                        return;
                    }
                    /// @
                    /// M:,Support messages multi-delete opeartor. @{
                    case MessageListItem.ITEM_CLICK: { // can be deleted!!!
                        mMsgListAdapter.changeSelectedState(msg.arg1);
                        return;
                    }
                    /// @}
                    case MessageListItem.MSG_LIST_RESEND_IPMSG:
                        long[][] allFailedIpMsgIds = getAllFailedIpMsgByThreadId(mConversation.getThreadId());
                        MmsLog.d(IPMSG_TAG, "mMessageListItemHandler.handleMessage(): Msg_list_reand_ipmsg, " +
                            "allFailedIpMsg len:" + allFailedIpMsgIds.length);
                        showResendConfirmDialg(msg.getData().getLong("MSG_ID"), msg.getData().getInt(
                            "SUB_ID"), allFailedIpMsgIds);
                        return;
                    case IMmsComposeExt.MSG_LIST_SHOW_MSGITEM_DETAIL:
                        if (MmsConfig.isMassTextEnable()) {
                            mMmsComposePlugin.showMassTextMsgDetail(ComposeMessageActivity.this, msgItem.mIpMessageId);
                        }
                        return;

                    default:
                        Log.w(TAG, "Unknown message: " + msg.what);
                        return;
                }
        }
    };

    private boolean showMessageDetails(MessageItem msgItem) {
        /// M: Code analyze 040, The function getMessageDetails use MessageItem but not cursor now.@{
        /*
        Cursor cursor = mMsgListAdapter.getCursorForItem(msgItem);
        if (cursor == null) {
            return false;
        }
        */
        ///M: for OP09
        if (MmsConfig.isMassTextEnable() && mMmsComposePlugin.showMassTextMsgDetail(this, msgItem.mIpMessageId)) {
            return true;
        }
        String messageDetails = MessageUtils.getMessageDetails(
               //ComposeMessageActivity.this, cursor, msgItem.mMessageSize);
                ComposeMessageActivity.this, msgItem);
        /// @}
        if (Build.TYPE.equals("eng")) {
            Log.d(TAG, "showMessageDetails. messageDetails:" + messageDetails);
        }
        new AlertDialog.Builder(ComposeMessageActivity.this)
                .setTitle(R.string.message_details_title)
                .setMessage(messageDetails)
                .setCancelable(true)
                .show();
        return true;
    }

    private final OnKeyListener mSubjectKeyListener = new OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }

            // When the subject editor is empty, press "DEL" to hide the input field.
            if ((keyCode == KeyEvent.KEYCODE_DEL) && (mSubjectTextEditor.length() == 0)) {
                showSubjectEditor(false);
                mWorkingMessage.setSubject(null, true);
                /// M: add for character counter
                updateCounter(mWorkingMessage.getText(), 0, 0, 0);
                return true;
            }
            return false;
        }
    };

    /**
     * Return the messageItem associated with the type ("mms" or "sms") and message id.
     * @param type Type of the message: "mms" or "sms"
     * @param msgId Message id of the message. This is the _id of the sms or pdu row and is
     * stored in the MessageItem
     * @param createFromCursorIfNotInCache true if the item is not found in the MessageListAdapter's
     * cache and the code can create a new MessageItem based on the position of the current cursor.
     * If false, the function returns null if the MessageItem isn't in the cache.
     * @return MessageItem or null if not found and createFromCursorIfNotInCache is false
     */
    private MessageItem getMessageItem(String type, long msgId,
            boolean createFromCursorIfNotInCache) {
        return mMsgListAdapter.getCachedMessageItem(type, msgId,
                createFromCursorIfNotInCache ? mMsgListAdapter.getCursor() : null);
    }

    private boolean isCursorValid() {
        // Check whether the cursor is valid or not.
        Cursor cursor = mMsgListAdapter.getCursor();
        if (cursor.isClosed() || cursor.isBeforeFirst() || cursor.isAfterLast()) {
            Log.e(TAG, "Bad cursor.", new RuntimeException());
            return false;
        }
        return true;
    }

    /*** M: remove Google default code
    private void resetCounter() {
        /// M: Code analyze 032, According to the message state to update text counter.@{
        mTextEditor.setText(mWorkingMessage.getText());
        /// M: once updateCounter.
        updateCounter(mWorkingMessage.getText(), 0, 0, 0);
        if (mWorkingMessage.requiresMms()) {
            mTextCounter.setVisibility(View.GONE);
        } else {
            mTextCounter.setVisibility(View.VISIBLE);
        }
        /// @}
    }
    */

    private void updateCounter(CharSequence text, int start, int before, int count) {
        if (!mIsSmsEnabled) {
            MmsLog.w(TAG, "updateCounter(): sms is disabled!");
            mTextCounter.setVisibility(View.GONE);
            return;
        }
        if (text == null) {
            MmsLog.w(TAG, "updateCounter(): text is null!");
            return;
        }
		
        if (text.length() == 0 && !IpMessageUtils.getIpMessagePlugin(this).isActualPlugin()) {
           /*HQ_sunli HQ01503617 20151117 begin*/
            if(!mWorkingMessage.hasAttachment()){
            mTextCounter.setVisibility(View.GONE);
            //mTextCounterForDouble.setVisibility(View.GONE);
            Log.d("HQWH","wanghui2");
            }
           /*HQ_sunli HQ01503617 20151117 end*/
            if (MmsConfig.isDualSendButtonEnable()) {
                int[] paramsOp09 = null;
                int encodingTypeOp09 = SmsMessage.ENCODING_UNKNOWN;
                if (MmsConfig.isEnableSmsEncodingType()) {
                    encodingTypeOp09 = MessageUtils.getSmsEncodingType(ComposeMessageActivity.this);
                }
                paramsOp09 = SmsMessage.calculateLength(text, false, encodingTypeOp09);
                final int remainingInCurrentMessageOp09 = paramsOp09[2];
                mUiHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                    mMmsComposePlugin.updateNewTextCounter(
                        0, mWorkingMessage.requiresMms(), remainingInCurrentMessageOp09, 1);
                    }
                }, 100);
            }
            /// @}
            mWorkingMessage.setLengthRequiresMms(false, true);
            return;
        }
        if (Build.TYPE.equals("eng")) {
            MmsLog.d(TAG, "updateCounter(): text = " + text);
        }
        if (IpMessageUtils.getIpMessagePlugin(this).isActualPlugin()
                && updateIpMessageCounter(text, start, before, count)) {
            return;
        }
        MmsLog.d(TAG, "updateCounter(): common message");
        /// M: Code analyze 031, Add encode type for calculating message lenght and always show
        /// text counter if it is in sms mode.@{
        /*
        WorkingMessage workingMessage = mWorkingMessage;
        if (workingMessage.requiresMms()) {
            // If we're not removing text (i.e. no chance of converting back to SMS
            // because of this change) and we're in MMS mode, just bail out since we
            // then won't have to calculate the length unnecessarily.
            final boolean textRemoved = (before > count);
            if (!textRemoved) {
                showSmsOrMmsSendButton(workingMessage.requiresMms());
                return;
            }
        }
        */
        int[] params = null;

        int encodingType = SmsMessage.ENCODING_UNKNOWN;

        if (MmsConfig.isEnableSmsEncodingType()) {
            encodingType = MessageUtils.getSmsEncodingType(ComposeMessageActivity.this);
        }
         //modify by lipeng for euro 7bit requirments
        if(encodingType == 1 && SystemProperties.get("ro.hq.mms.ap.sevenbit").equals("1")){//use 7bit coding
        	params = SmsMessage.calculateLength(text, true, encodingType);
        }else{
         params = SmsMessage.calculateLength(text, false, encodingType);
        }//end by zhailong
        //params = SmsMessage.calculateLength(text, false, encodingType);
            /* SmsMessage.calculateLength returns an int[4] with:
             *   int[0] being the number of SMS's required,
             *   int[1] the number of code units used,
             *   int[2] is the number of code units remaining until the next message.
             *   int[3] is the encoding type that should be used for the message.
             */
        final int msgCount = params[0];
        final int remainingInCurrentMessage = params[2];
        MmsLog.d(TAG, "updateCounter(): message remainingInCurrentMessage = " + remainingInCurrentMessage);
        /*
        if (!MmsConfig.getMultipartSmsEnabled()) {
            mWorkingMessage.setLengthRequiresMms(
                    msgCount >= MmsConfig.getSmsToMmsTextThreshold(), true);
        }

        // Show the counter only if:
        // - We are not in MMS mode
        // - We are going to send more than one message OR we are getting close
        boolean showCounter = false;
        if (!workingMessage.requiresMms() &&
                (msgCount > 1 ||
                 remainingInCurrentMessage <= CHARS_REMAINING_BEFORE_COUNTER_SHOWN)) {
            showCounter = true;
        }

        showSmsOrMmsSendButton(workingMessage.requiresMms());

        if (showCounter) {
            // Update the remaining characters and number of messages required.
            String counterText = msgCount > 1 ? remainingInCurrentMessage + " / " + msgCount
                    : String.valueOf(remainingInCurrentMessage);
            mTextCounter.setText(counterText);
            mTextCounter.setVisibility(View.VISIBLE);
        } else {
            mTextCounter.setVisibility(View.GONE);
        }
         */
        mWorkingMessage.setLengthRequiresMms(
            msgCount >= MmsConfig.getSmsToMmsTextThreshold(), true);
        MmsLog.d(TAG, "updateCounter(): message msgCount = " + msgCount + " TextThreshold() = " + MmsConfig.getSmsToMmsTextThreshold());
        /// M: Show the counter
        /// M: Update the remaining characters and number of messages required.
        if (msgCount >= MmsConfig.getSmsToMmsTextThreshold()) {
            mTextCounter.setVisibility(View.GONE);
            return;
        }
        mUiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                /// M: OP09 text counter {
				/*HQ_zhangjing 2015-11-02 modified for CQ HQ01478873 begin*/
                /*if (MmsConfig.isDualSendButtonEnable()) {
                    mMmsComposePlugin.updateNewTextCounter(mTextEditor.getLineCount(), mWorkingMessage.requiresMms(),
                        remainingInCurrentMessage, msgCount);
                    return;
                }*/
				/*HQ_zhangjing 2015-11-02 modified for CQ HQ01478873 end*/
                /// @}
                MmsLog.d(TAG, "updateCounter requiresMms = " + mWorkingMessage.requiresMms() + " line count = " + mTextEditor.getLineCount());
				/*HQ_zhangjing modified for sms ui begin*/
                if (mWorkingMessage.requiresMms() /*|| mTextEditor.getLineCount() <= 1*/) {
                    mTextCounter.setVisibility(View.VISIBLE);
					//mTextCounterForDouble.setVisibility(View.VISIBLE);
					mTextCounter.setText( getMmsSize() );
					mTextCounterForDouble.setText( getMmsSize() );
                    return;
                }
                mTextCounter.setVisibility(View.VISIBLE);
				showOrHideSendbtn();
                String counterText = remainingInCurrentMessage + "/" + msgCount;
                mTextCounter.setText(counterText);
				//mTextCounterForDouble.setVisibility(View.VISIBLE);
				mTextCounterForDouble.setText( counterText );
            }
        }, 100);
        /// @}
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        // requestCode >= 0 means the activity in question is a sub-activity.
        if (requestCode >= 0) {
            mWaitingForSubActivity = true;
        }
        
        /// M : FIX CR : ALPS01795853 @{
        if (mWorkingMessage.getText().toString().trim().length() != 0) {
            if (requestCode == REQUEST_CODE_ATTACH_IMAGE || requestCode == REQUEST_CODE_ATTACH_VIDEO
                    || requestCode == REQUEST_CODE_ADD_CONTACT || requestCode == REQUEST_CODE_ATTACH_SOUND
                    || requestCode == REQUEST_CODE_ATTACH_RINGTONE || requestCode == REQUEST_CODE_ATTACH_VCALENDAR
                    || requestCode == REQUEST_CODE_ATTACH_VCARD || requestCode == REQUEST_CODE_RECORD_SOUND
                    || requestCode == REQUEST_CODE_TAKE_PICTURE || requestCode == REQUEST_CODE_TAKE_VIDEO
                    || requestCode == REQUEST_CODE_TEXT_VCARD) {
                mWorkingMessage.setTruntoChooseAttach(true);
            }
        }
        /// @}
        
        
        if (mIsKeyboardOpen) {
            hideKeyboard();     // camera and other activities take a long time to hide the keyboard
        }

        /// M: Code analyze 041, Add exception handling for starting activity.@{
        if (null != intent && null != intent.getData()
                && intent.getData().getScheme().equals("mailto")) {
            try {
                super.startActivityForResult(intent, requestCode);
            } catch (ActivityNotFoundException e) {
                MmsLog.e(TAG, "[ActivityNotFoundException] Failed to startActivityForResult: " + intent);
                Intent mchooserIntent = Intent.createChooser(intent, null);
                super.startActivityForResult(mchooserIntent, requestCode);
            } catch (Exception e) {
                MmsLog.e(TAG, "[Exception] Failed to startActivityForResult: " + intent);
                Toast.makeText(this, getString(R.string.message_open_email_fail),
                      Toast.LENGTH_SHORT).show();
          }
        } else {
            try {
                super.startActivityForResult(intent, requestCode);
            } catch (ActivityNotFoundException e) {
                /// M: modify for ip message
                if (requestCode == REQUEST_CODE_PICK || requestCode == REQUEST_CODE_IPMSG_PICK_CONTACT) {
                    misPickContatct = false;
                    mShowingContactPicker = false;
                }
                Intent mchooserIntent = Intent.createChooser(intent, null);
                super.startActivityForResult(mchooserIntent, requestCode);
            }
        }
        /// @}
    }

    private void toastConvertInfo(boolean toMms) {
        final int resId = toMms ? R.string.converting_to_picture_message
                : R.string.converting_to_text_message;
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private class DeleteMessageListener implements OnClickListener {
        private final MessageItem mMessageItem;

        public DeleteMessageListener(MessageItem messageItem) {
            mMessageItem = messageItem;
        }

        @Override
        public void onClick(DialogInterface dialog, int whichButton) {
            dialog.dismiss();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    MmsLog.d(TAG, "DeleteMessageListener start");
                    if (mMessageItem.isMms()) {
                        WorkingMessage.removeThumbnailsFromCache(mMessageItem.getSlideshow());

                        MmsApp.getApplication().getPduLoaderManager()
                            .removePdu(mMessageItem.mMessageUri);
                        // Delete the message *after* we've removed the thumbnails because we
                        // need the pdu and slideshow for removeThumbnailsFromCache to work.
                    } else if (mMessageItem.mIpMessageId > 0) { /// M: add for ipmessage
                        /// M: delete ipmessage recorded in external db.
                        long [] ids = new long[1];
                        ids[0] = mMessageItem.mMsgId;
                        MmsLog.d(TAG, "delete ipmessage, id:" + ids[0]);
                        IpMessageUtils.getMessageManager(ComposeMessageActivity.this)
                                        .deleteIpMsg(ids, false);
                    }
                    /// M: google jb.mr1 patch, Conversation should scroll to the bottom
                    /// when incoming received @{
                    Boolean deletingLastItem = false;
                    Cursor cursor = mMsgListAdapter != null ? mMsgListAdapter.getCursor() : null;
                    if (cursor != null) {
                        cursor.moveToLast();
                        long msgId = cursor.getLong(COLUMN_ID);
                        deletingLastItem = msgId == mMessageItem.mMsgId;
                    }
                    Uri deleteUri = mMessageItem.mMessageUri;
                    MmsLog.d(TAG, "deleteUri " + deleteUri);
                    MmsLog.d(TAG, "deleteUri.host " + deleteUri.getHost());

                    /// M: for OP09 : delete mass text messages.
                    if (mMmsComposePlugin.deleteMassTextMsg(mBackgroundQueryHandler, mMessageItem.mMsgId, mMessageItem.mIpMessageId)) {
                        return;
                    }
                    mBackgroundQueryHandler.startDelete(DELETE_MESSAGE_TOKEN,
                            deletingLastItem, mMessageItem.mMessageUri,
                            mMessageItem.mLocked ? null : "locked=0", null);
                    /// @}
                    return;
                }
            }).start();
        }
    }

    private class DiscardDraftListener implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int whichButton) {
            mWorkingMessage.discard();
            dialog.dismiss();
            if (MmsConfig.isDualSendButtonEnable()) {
                hideInputMethod();
            }
            /// M: Code analyze 042, If you discard the draft message manually.@{
            mHasDiscardWorkingMessage = true;
            /// @}
            /// M: clear IP message draft.@{
            if (IpMessageUtils.getIpMessagePlugin(ComposeMessageActivity.this).isActualPlugin() && mIpMessageDraft != null) {
                if (mIpMessageDraft.getId() > 0) {
                    IpMessageUtils.getMessageManager(ComposeMessageActivity.this).deleteIpMsg(
                        new long[] { mIpMessageDraft.getId() }, true);
                }
                mIpMessageDraft = null;
            }
            /// @}
            finish();
        }
    }

    private class SendIgnoreInvalidRecipientListener implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int whichButton) {
            /// M: Code analyze 030, Check condition before sending message.@{
            checkConditionsAndSendMessage(true);
            /// @}
            dialog.dismiss();
        }
    }

    private class CancelSendingListener implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int whichButton) {
            if (isRecipientsEditorVisible()) {
                mRecipientsEditor.requestFocus();
            }
            dialog.dismiss();
            /// M: @{
            updateSendButtonState();
            /// @}
        }
    }

    /// M: fix bug ALPS00484778
    private ContactList mCutRecipients;

    private class CancelSendingListenerForInvalidRecipient implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int whichButton) {
            if (isRecipientsEditorVisible()) {
                if (mCutRecipients != null) {
                        mRecipientsEditor.removeChipChangedListener(mChipWatcher);
                        mRecipientsEditor.populate(new ContactList());
                        mRecipientsEditor.addChipChangedListener(mChipWatcher);
                        mRecipientsEditor.populate(mCutRecipients);
                        mCutRecipients = null;
                } else {
                    mRecipientsEditor.requestFocus();
                }
            }
            dialog.dismiss();
            /// M: @{
            updateSendButtonState();
            /// @}
        }
    }
    /// @}

    private void confirmSendMessageIfNeeded() {
        /// M: for length required MMS for op09@{
        if (MmsConfig.isChangeLengthRequiredMmsToSmsEnable()) {
            if (mMmsComposePlugin.needConfirmMmsToSms()) {
                confirmForChangeMmsToSms();
                return;
            } else {
                mMmsComposePlugin.setConfirmMmsToSms(true);
            }
        }
        /// @}
        if (!isRecipientsEditorVisible()) {
            /// M: Code analyze 030, Check condition before sending message.@{
            checkConditionsAndSendMessage(true);
            /// @}
            return;
        }
        //add by wanghui 
        Log.d("HQWH1","wuxiaolianxiren1");
        if(mRecipientsEditor.hasonenumber()&&(getResources().getBoolean(R.bool.number_check_movistar))){
            Log.d("HQWH1","wuxiaolianxiren2");
            Builder dialogBuilder = new AlertDialog.Builder(this);
             dialogBuilder.setCancelable(false);
              dialogBuilder.setTitle(getResources().getString(R.string.invalid_number));
                  dialogBuilder.setPositiveButton(android.R.string.ok, 
                             new AlertDialog.OnClickListener() {
                      public void onClick(DialogInterface dialog, int which) {
                          Log.d(TAG,"invalid number dialog dismiss");
                           dialog.dismiss(); 
                           updateSendButtonState();
                           	}
                      });	                 
                      AlertDialog dialog = dialogBuilder.create();
                     //dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
            dialog.show();   
            return;     
        }
        boolean isMms = mWorkingMessage.requiresMms();
		/*HQ_zhangjing 2015-11-12 modified for CQ HQ01455134 remove the invalid recipient begin*/
		if( !showInvalidRecipintsDlg ){
			if (mRecipientsEditor.hasInvalidRecipient(isMms)) {
				updateSendButtonState();
			}
			if( true ){//send direct and ignore the invalid recipients
				checkConditionsAndSendMessage(true);
				return;
			}
		}
		/*HQ_zhangjing 2015-11-12 modified for CQ HQ01455134 remove the invalid recipient end*/
		
        if (mRecipientsEditor.hasInvalidRecipient(isMms)) {
            /// M: Code analyze 054, Even if there are some invalid recipients , we also try to
            /// send messag.Now, We do not disgingush there are some or all invalid recipients. @{
            updateSendButtonState();
                String title = getResourcesString(R.string.has_invalid_recipient,
                        mRecipientsEditor.formatInvalidNumbers(isMms));
                new AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(title)
                    .setMessage(R.string.invalid_recipient_message)
                    .setPositiveButton(R.string.try_to_send,
                            new SendIgnoreInvalidRecipientListener())
                    .setNegativeButton(R.string.no, new CancelSendingListenerForInvalidRecipient())
                    .setOnKeyListener(new DialogInterface.OnKeyListener() {
                        @Override
                        public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                            if (keyCode == KeyEvent.KEYCODE_BACK) {
                                dialog.dismiss();
                            }
                            return false;
                        }
                    })
                    .show();
        /** M: support mms cc feature. CT requested. here use simple logic.
         *  check to and cc only show one dialog, if users ignore To invalid address,
         *  don't show cc invalid too.
         */
        } else if (isRecipientsCcEditorVisible() && mRecipientsCcEditor.hasInvalidRecipient(true)) {
            //updateSendButtonState();
            String title = getResourcesString(R.string.has_invalid_cc, mRecipientsCcEditor
                    .formatInvalidNumbers(true));
            new AlertDialog.Builder(this)
                .setCancelable(false)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(title).setMessage(R.string.invalid_recipient_message)
                .setPositiveButton(
                        R.string.try_to_send, new SendIgnoreInvalidRecipientListener())
                .setNegativeButton(R.string.no, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                        updateSendButtonState(true);
                    }
                }).show();
        } else {
             /// M: Code analyze 030, Check condition before sending message.(All recipients are valid.)@{
            checkConditionsAndSendMessage(true);
             /// @}
        }
    }

    /// M: Recipient Control refactory, don't use TextWatcher: replace with ChipWatcher,
    /// to reduce the frequency of notify. and to sync recipients. @{
    private final MTKRecipientEditTextView.ChipWatcher mChipWatcher = new MTKRecipientEditTextView.ChipWatcher() {
        public void onChipChanged(ArrayList<RecipientEntry> allChips, ArrayList<String> changedChipAddresses, String lastString) {
            /// M: ALPS01843842, when chip changed ,close the contact view context menu.
            if (mChipViewMenu != null) {
                mChipViewMenu.close();
                mChipViewMenu = null;
            }
            if (!isRecipientsEditorVisible()) {
                IllegalStateException e = new IllegalStateException("onChipChanged called with invisible mRecipientsEditor");
                Log.w(TAG, "ChipWatcher: onChipChanged called with invisible mRecipientsEditor");
                return;
            }

            Log.i(TAG, "ChipWatcher onChipChanged begin.");
            ContactList LastContacts = mRecipientsEditor.getContactsFromChipWatcher();
            int updateLimit = getLimitedContact();
            /// @}
			/*HQ_zhangjing 2015-11-12 modified for CQ HQ01455134 remove the invalid recipient begin*/
            if( !showInvalidRecipintsDlg && mWorkingMessage != null ){
				mRecipientsEditor.setIsMms(mWorkingMessage.requiresMms());
            }
			/*HQ_zhangjing 2015-11-12 modified for CQ HQ01455134 remove the invalid recipient begin*/
            mRecipientsEditor.parseRecipientsFromChipWatcher(allChips, changedChipAddresses, lastString, updateLimit);
            List<String> numbers = mRecipientsEditor.getNumbersFromChipWatcher();
            // google steps in textchange
            mWorkingMessage.setWorkingRecipients(numbers);
            /// M: google JB.MR1 patch, group mms
            boolean multiRecipients = numbers != null && numbers.size() > 1;
            boolean isGroupMms = MmsPreferenceActivity.getIsGroupMmsEnabled(ComposeMessageActivity.this)
                                                        && multiRecipients;
            mMsgListAdapter.setIsGroupConversation(isGroupMms);
            mWorkingMessage.setHasMultipleRecipients(multiRecipients, true);
            mWorkingMessage.setHasEmail(mRecipientsEditor.containsEmailFromChipWatcher(), true);
            int recipientCount = numbers.size();
            checkForTooManyRecipients(recipientCount);
            // google steps end
            ContactList contacts = mRecipientsEditor.getContactsFromChipWatcher();
            if (!contacts.equals(LastContacts) || (changedChipAddresses != null && changedChipAddresses.size() > 0)) {
                updateTitle(contacts);
            }
            updateSendButtonState();
            Log.i(TAG, "ChipWatcher onChipChanged end.");
        }
    };
    /// @}

    private void checkForTooManyRecipients(int recipientCount) {
        /// M: Code analyze 056,Now,the sms recipient limit is different from mms.
        /// We can set limit for sms or mms individually. @{
        final int recipientLimit = MmsConfig.getSmsRecipientLimit();
        /// @}
        if (recipientLimit != Integer.MAX_VALUE && recipientLimit > 0) {

            //final int recipientCount = recipientCount();
            boolean tooMany = recipientCount > recipientLimit;

            if (recipientCount != mLastRecipientCount) {
                // Don't warn the user on every character they type when they're over the limit,
                // only when the actual # of recipients changes.
                mLastRecipientCount = recipientCount;
                if (tooMany) {
                    String tooManyMsg = getString(R.string.too_many_recipients, recipientCount,
                            recipientLimit);
                    Toast.makeText(ComposeMessageActivity.this,
                            tooManyMsg, Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private final OnCreateContextMenuListener mRecipientsMenuCreateListener =
        new OnCreateContextMenuListener() {
        @Override
        public void onCreateContextMenu(ContextMenu menu, View v,
                ContextMenuInfo menuInfo) {
            mChipViewMenu = menu;
            String langage = Locale.getDefault().getLanguage();//add by lipeng for number display
            if (menuInfo != null) {
                Contact c = ((RecipientContextMenuInfo) menuInfo).recipient;
                RecipientsMenuClickListener l = new RecipientsMenuClickListener(c);
                //modify by lipeng for number display
                if(MessageUtils.isInteger(c.getName())&&(langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw"))){
                	menu.setHeaderTitle("\u202D"+c.getName()+"\u202C");
                }else{
                    menu.setHeaderTitle(c.getName());
                }//end by lipeng

                if (c.existsInDatabase()) {
                    menu.add(0, MENU_VIEW_CONTACT, 0, R.string.menu_view_contact)
                            .setOnMenuItemClickListener(l);
                /// M: Code analyze 043, Whether the address can be added to contacts app. @{
                } else if (MessageUtils.canAddToContacts(c)) {
                /// @}
                    menu.add(0, MENU_ADD_TO_CONTACTS, 0, R.string.menu_add_to_contacts)
                            .setOnMenuItemClickListener(l);
                }
            }
        }
    };

    private final class RecipientsMenuClickListener implements MenuItem.OnMenuItemClickListener {
        private final Contact mRecipient;

        RecipientsMenuClickListener(Contact recipient) {
            mRecipient = recipient;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                // Context menu handlers for the recipients editor.
                case MENU_VIEW_CONTACT: {
                    Uri contactUri = mRecipient.getUri();
                    /// M: fix bug ALPS00448677, update or delete Contact Chip
                    mInViewContact = mRecipient;
                    /// @}
                    Intent intent = new Intent(Intent.ACTION_VIEW, contactUri);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                    startActivityForResult(intent, REQUEST_CODE_VIEW_CONTACT);
                    return true;
                }
                case MENU_ADD_TO_CONTACTS: {
                    /// M: fix bug ALPS00448677, update or delete Contact Chip
                    mInViewContact = null;
                    /// @}
                    mAddContactIntent = ConversationList.createAddContactIntent(
                            mRecipient.getNumber());
                    ComposeMessageActivity.this.startActivityForResult(mAddContactIntent,
                            REQUEST_CODE_ADD_CONTACT);
                    return true;
                }
            }
            return false;
        }
    }

    private boolean canAddToContacts(Contact contact) {
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
        if (!(Mms.isEmailAddress(name) ||
                Telephony.Mms.isPhoneNumber(name) ||
                contact.isMe())) {
            return false;
        }
        return true;
    }

    private boolean isSpecialChar(char c) {
        return c == '*' || c == '%' || c == '$';
    }

    private void addPositionBasedMenuItems(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;

        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo");
            return;
        }
        final int position = info.position;

        addUriSpecificMenuItems(menu, v, position);
    }

    private Uri getSelectedUriFromMessageList(ListView listView, int position) {
        // If the context menu was opened over a uri, get that uri.
		/*HQ_zhangjing add for al812 mms ui begin*/
        View view = listView.getChildAt(position);
		MessageListItem msglistItem = null;
		if( view != null ){
			TimeAxisWidget taw = (TimeAxisWidget) view.findViewById(R.id.item_date);
			msglistItem = (MessageListItem)taw.getContent();
		}		
        //MessageListItem msglistItem = (MessageListItem) listView.getChildAt(position);
		/*HQ_zhangjing add for al812 mms ui end*/
        if (msglistItem == null) {
            // FIXME: Should get the correct view. No such interface in ListView currently
            // to get the view by position. The ListView.getChildAt(position) cannot
            // get correct view since the list doesn't create one child for each item.
            // And if setSelection(position) then getSelectedView(),
            // cannot get corrent view when in touch mode.
            return null;
        }

        TextView textView;
        CharSequence text = null;
        int selStart = -1;
        int selEnd = -1;

        //check if message sender is selected
        textView = (TextView) msglistItem.findViewById(R.id.text_view);
        if (textView != null) {
            text = textView.getText();
            selStart = textView.getSelectionStart();
            selEnd = textView.getSelectionEnd();
        }

        /// M: Code analyze 044,If sender is not being selected, it may be within the message body.@{
        if (selStart == -1) {
            textView = (TextView) msglistItem.findViewById(R.id.body_text_view);
            if (textView != null) {
                text = textView.getText();
                selStart = textView.getSelectionStart();
                selEnd = textView.getSelectionEnd();
            }
        }
        /// @}

        // Check that some text is actually selected, rather than the cursor
        // just being placed within the TextView.
        if (selStart != selEnd) {
            int min = Math.min(selStart, selEnd);
            int max = Math.max(selStart, selEnd);

            URLSpan[] urls = ((Spanned) text).getSpans(min, max,
                                                        URLSpan.class);

            if (urls.length == 1) {
                return Uri.parse(urls[0].getURL());
            }
        }

        //no uri was selected
        return null;
    }

    private void addUriSpecificMenuItems(ContextMenu menu, View v, int position) {
        Uri uri = getSelectedUriFromMessageList((ListView) v, position);

        if (uri != null) {
            Intent intent = new Intent(null, uri);
            intent.addCategory(Intent.CATEGORY_SELECTED_ALTERNATIVE);
            menu.addIntentOptions(0, 0, 0,
                    new android.content.ComponentName(this, ComposeMessageActivity.class),
                    null, intent, 0, null);
        }
    }

    private final void addCallAndContactMenuItems(
            ContextMenu menu, MsgListMenuClickListener l, MessageItem msgItem) {
        if (TextUtils.isEmpty(msgItem.mBody)) {
            return;
        }
        SpannableString msg = new SpannableString(msgItem.mBody);
        
        if(SystemProperties.get("ro.hq.mms.number.link").equals("1")){
            
            Linkify.addLinks(msg, Linkify.ALL_MMS);
        }else{
            
            Linkify.addLinks(msg, Linkify.ALL);
        }

        ArrayList<String> uris =
            MessageUtils.extractUris(msg.getSpans(0, msg.length(), URLSpan.class));
        /// M: Code analyze 022, Add bookmark. Clear the List.@{
        mURLs.clear();
        /// @}
        // Remove any dupes so they don't get added to the menu multiple times
        HashSet<String> collapsedUris = new HashSet<String>();
        for (String uri : uris) {
            collapsedUris.add(uri.toLowerCase());
        }
        for (String uriString : collapsedUris) {
            String prefix = null;
            int sep = uriString.indexOf(":");
            if (sep >= 0) {
                prefix = uriString.substring(0, sep);
                /// M: Code analyze 022, Add bookmark. @{
                if ("mailto".equalsIgnoreCase(prefix) || "tel".equalsIgnoreCase(prefix)) {
                    uriString = uriString.substring(sep + 1);
                }
                /// @}
            }
            Uri contactUri = null;
            boolean knownPrefix = true;
            if ("mailto".equalsIgnoreCase(prefix))  {
                contactUri = getContactUriForEmail(uriString);
            } else if ("tel".equalsIgnoreCase(prefix)) {
                contactUri = getContactUriForPhoneNumber(uriString);
            } else {
                knownPrefix = false;

                /// M: Code analyze 022, Add bookmark. Maybe exist multi URL address @{
                if (msgItem.isSms() && mURLs.size() <= 0) {
                    menu.add(0, MENU_ADD_TO_BOOKMARK, 0, R.string.menu_add_to_bookmark)
                    .setOnMenuItemClickListener(l);
                }
                /// @}

                /// M: Code analyze 022, Add bookmark. fix bug ALPS00783237 @{
                for (String uri : uris) {
                    if (uri != null && uri.equalsIgnoreCase(uriString)) {
                        mURLs.add(uri);
                        break;
                    }
                }
                /// @}
            }
            if (knownPrefix && contactUri == null ) { 
                Intent intent = ConversationList.createAddContactIntent(uriString);

                String addContactString = getString(R.string.menu_add_address_to_contacts,
                        uriString);
                menu.add(0, MENU_ADD_ADDRESS_TO_CONTACTS, 0, addContactString)
                    .setOnMenuItemClickListener(l)
                    .setIntent(intent);
            }
        }
        /// M: New plugin API @{
        if (msgItem.isMms() && msgItem.isDownloaded() && (mURLs.size() > 0)) {
            mMmsComposePlugin.addMmsUrlToBookMark(this, menu, MENU_ADD_TO_BOOKMARK, mURLs);
        }
        /// @}
    }

    private Uri getContactUriForEmail(String emailAddress) {
        Cursor cursor = SqliteWrapper.query(this, getContentResolver(),
                Uri.withAppendedPath(Email.CONTENT_LOOKUP_URI, Uri.encode(emailAddress)),
                new String[] { Email.CONTACT_ID, Contacts.DISPLAY_NAME }, null, null, null);

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(1);
                    if (!TextUtils.isEmpty(name)) {
                        return ContentUris.withAppendedId(Contacts.CONTENT_URI, cursor.getLong(0));
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    private Uri getContactUriForPhoneNumber(String phoneNumber) {
        Contact contact = Contact.get(phoneNumber, true);
        if (contact.existsInDatabase()) {
            return contact.getUri();
        }
        return null;
    }

    private final OnCreateContextMenuListener mMsgListMenuCreateListener =
        new OnCreateContextMenuListener() {
        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            if (!isCursorValid()) {
                return;
            }
            Cursor cursor = mMsgListAdapter.getCursor();
            String type = cursor.getString(COLUMN_MSG_TYPE);
            long msgId = cursor.getLong(COLUMN_ID);
            ///M: ALPS00726802, save the position in cursor of the clicked item
            mClickedItemPosition = cursor.getPosition();

            MmsLog.i(TAG, "onCreateContextMenu(): msgId=" + msgId);
            addPositionBasedMenuItems(menu, v, menuInfo);

            MessageItem msgItem = mMsgListAdapter.getCachedMessageItem(type, msgId, cursor);
            if (msgItem == null) {
                Log.e(TAG, "Cannot load message item for type = " + type
                        + ", msgId = " + msgId);
                return;
            }

            menu.setHeaderTitle(R.string.message_options);

            MsgListMenuClickListener l = new MsgListMenuClickListener(msgItem);

            if (mIsSmsEnabled) {
                if ((MmsConfig.isActivated(ComposeMessageActivity.this)
                        || MmsConfig.isServiceEnabled(ComposeMessageActivity.this)) && msgItem.mIpMessageId > 0) {
                    int ipStatus = IpMessageUtils.getMessageManager(ComposeMessageActivity.this).getStatus(msgItem.mMsgId);
                    if (ipStatus == IpMessageStatus.FAILED || ipStatus == IpMessageStatus.NOT_DELIVERED) {
                        IpMessage ipMessage = IpMessageUtils.getMessageManager(
                                ComposeMessageActivity.this).getIpMsgInfo(msgItem.mMsgId);
                        int commonType = canConvertIpMessageToMessage(ipMessage);
                        if (commonType == MESSAGETYPE_TEXT) {
                            menu.add(0, MENU_SEND_VIA_TEXT_MSG, 0,
                                        IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                                    .getSingleString(IpMessageConsts.string.ipmsg_send_via_text_msg))
                                    .setOnMenuItemClickListener(l);
                        } else if (commonType == MESSAGETYPE_MMS) {
                            menu.add(0, MENU_SEND_VIA_TEXT_MSG, 0,
                                        IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                                    .getSingleString(IpMessageConsts.string.ipmsg_send_via_mms))
                                    .setOnMenuItemClickListener(l);
                        }
                        menu.add(0, MENU_RETRY, 0,
                                        IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                                    .getSingleString(IpMessageConsts.string.ipmsg_retry))
                                    .setOnMenuItemClickListener(l);
                    }
                }
            }
            if (msgItem.isSms()) {
                if (msgItem.mIpMessageId > 0) {
                    IpMessage ipMessage = IpMessageUtils.getMessageManager(
                            ComposeMessageActivity.this).getIpMsgInfo(msgItem.mMsgId);
                    if (ipMessage != null && (ipMessage.getType() == IpMessageType.TEXT)) {
                        menu.add(0, MENU_COPY, 0, R.string.ipmsg_copy)
                                .setOnMenuItemClickListener(l);
                    }
                } else {
                    menu.add(0, MENU_COPY, 0, R.string.ipmsg_copy)
                    .setOnMenuItemClickListener(l);
					
					/*HQ_zhangjing add for al812 on 2015-07-03 for resend the failed sms begin*/
					if( msgItem.isFailedMessage() || msgItem.mDeliveryStatus  == MessageItem.DeliveryStatus.FAILED){
						Log.d(TAG,"resend the message" + " and msgItem.mDeliveryStatus = " + msgItem.mDeliveryStatus);
						menu.add(0, MENU_RESEND_MESSAGE, 0, R.string.menu_resend)
							.setOnMenuItemClickListener(l);
					}
					/*HQ_zhangjing add end*/
					
                }
            }

            // Forward is not available for undownloaded messages.
            if (mIsSmsEnabled) {
                if (msgItem.isSms() || (msgItem.isDownloaded() && isForwardable(msgId))) {
                    if (msgItem.mIpMessageId > 0) {
                        if (msgItem.mIpMessage instanceof IpAttachMessage) {
                            IpAttachMessage ipAttachMessage = (IpAttachMessage) msgItem.mIpMessage;
                            if (!ipAttachMessage.isInboxMsgDownloalable()) {
                                //menu.add(0, MENU_FORWARD_IPMESSAGE, 0, R.string.menu_forward).setOnMenuItemClickListener(l);
                            }
                        } else {
                            //menu.add(0, MENU_FORWARD_IPMESSAGE, 0, R.string.menu_forward).setOnMenuItemClickListener(l);
                        }
                    } else {
                        menu.add(0, MENU_FORWARD_MESSAGE, 0, R.string.menu_forward).setOnMenuItemClickListener(l);
                    }
                }
            }
            /// M: Fix ipmessage bug @{
            if (mIsSmsEnabled && msgItem.mIpMessageId > 0) {
            /// @}
                if (msgItem.mIpMessage instanceof IpAttachMessage) {
                    IpAttachMessage ipAttachMessage = (IpAttachMessage) msgItem.mIpMessage;
                    if (!ipAttachMessage.isInboxMsgDownloalable()) {
                        menu.add(0, MENU_SHARE, 0,
                             IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                        .getSingleString(IpMessageConsts.string.ipmsg_share))
                        .setOnMenuItemClickListener(l);
                    }
                } else {
                    menu.add(0, MENU_SHARE, 0,
                            IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                        .getSingleString(IpMessageConsts.string.ipmsg_share))
                        .setOnMenuItemClickListener(l);
                }
                menu.add(0, MENU_DELETE_IP_MESSAGE, 0,
                            IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                        .getSingleString(IpMessageConsts.string.ipmsg_delete))
                        .setOnMenuItemClickListener(l);
            } else if (mIsSmsEnabled) {
                menu.add(0, MENU_DELETE_MESSAGE, 0, R.string.delete_message)
                        .setOnMenuItemClickListener(l);
            }
			/*HQ_zhangjing 2015-10-07 modified for mms menu tree begin*/
            if (mIsSmsEnabled) {
                if (msgItem.isSms() && msgItem.mIpMessageId <= 0) {
                    if (mSubCount > 0 && !msgItem.isSending()) {
                        /// M: For OP09 Feature, replace "SUB" to "UIM". @{
                        String str = mStringReplacementPlugin.getStrings(IStringReplacementExt.SAVE_MSG_TO_CARD);
                        if (MmsConfig.isStringReplaceEnable() && str != null) {
                            menu.add(0, MENU_SAVE_MESSAGE_TO_SUB, 0, str).setOnMenuItemClickListener(l);
                        } else {
                            menu.add(0, MENU_SAVE_MESSAGE_TO_SUB, 0, R.string.save_message_to_sim)
                                    .setOnMenuItemClickListener(l);
                        } /// @}
                    }
			/*HQ_zhangjing 2015-10-07 modified for mms menu tree end*/
                }
            }

            if (msgItem.isMms()) {
                switch (msgItem.mAttachmentType) {
                    case WorkingMessage.SLIDESHOW:
                        menu.add(0, MENU_VIEW_SLIDESHOW, 0, R.string.view_slideshow)
                                .setOnMenuItemClickListener(l);
                        if (haveSomethingToCopyToSDCard(msgItem.mMsgId)) {
                            menu.add(0, MENU_COPY_TO_SDCARD, 0, R.string.copy_to_sdcard)
                                    .setOnMenuItemClickListener(l);
                        }
                        break;
                    default:
                        if (haveSomethingToCopyToSDCard(msgItem.mMsgId)) {
                            menu.add(0, MENU_COPY_TO_SDCARD, 0, R.string.copy_to_sdcard)
                                    .setOnMenuItemClickListener(l);
                        }
                        break;
                }
            }

            addCallAndContactMenuItems(menu, l, msgItem);
			/*HQ_zhangjing 2015-10-07 modified for mms menu tree begin*/
            //menu.add(0, MENU_VIEW_MESSAGE_DETAILS, 0, R.string.view_message_details)
                //.setOnMenuItemClickListener(l);
			/*HQ_zhangjing 2015-10-07 modified for mms menu tree end*/
            if (msgItem.mDeliveryStatus != MessageItem.DeliveryStatus.NONE || msgItem.mReadReport) {
                menu.add(0, MENU_DELIVERY_REPORT, 0, R.string.view_delivery_report)
                        .setOnMenuItemClickListener(l);
            }

            /// M: Code analyze 016, Add for select text copy. @{
            if (!TextUtils.isEmpty(msgItem.mBody)
                    && msgItem.mMessageType != PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) {
                Log.i(TAG, "onCreateContextMenu(): add select text menu");
                menu.add(0, MENU_SELECT_TEXT, 0, R.string.select_text)
                        .setOnMenuItemClickListener(l);
            }
            /// @}
            /// M: add for ipmessage, only text ipmessage can show select text
            if (msgItem.mIpMessageId > 0 && msgItem.mIpMessage != null) {
                if (msgItem.mIpMessage.getType() != IpMessageType.TEXT) {
                    menu.removeItem(MENU_SELECT_TEXT);
                }
            }
            /// @}
			/*HQ_zhangjing 2015-10-07 modified for mms menu tree begin*/
			/*
            if (mIsSmsEnabled) {
                if (msgItem.isSms() && msgItem.mIpMessageId <= 0) {
                    if (mSubCount > 0 && !msgItem.isSending()) {
                        /// M: For OP09 Feature, replace "SUB" to "UIM". @{
                        String str = mStringReplacementPlugin.getStrings(IStringReplacementExt.SAVE_MSG_TO_CARD);
                        if (MmsConfig.isStringReplaceEnable() && str != null) {
                            menu.add(0, MENU_SAVE_MESSAGE_TO_SUB, 0, str).setOnMenuItemClickListener(l);
                        } else {
                            menu.add(0, MENU_SAVE_MESSAGE_TO_SUB, 0, R.string.save_message_to_sim)
                                    .setOnMenuItemClickListener(l);
                        } /// @}
                    }
                }
            }*/
			/*HQ_zhangjing 2015-10-07 modified for mms menu tree end*/
            /// M: For OP09: split message apart @{
            if (MmsConfig.isMassTextEnable() && msgItem.mIpMessageId < 0) {
                mMmsComposePlugin.addSplitMessageContextMenu(menu, v, menuInfo, getComposeContext(),
                    msgItem.mIpMessageId, mConversation.getMessageCount());
            }
            /// M: @}
			/*HQ_zhangjing 2015-10-07 modified for mms menu tree begin*/
            if (mIsSmsEnabled) {
                if (msgItem.mLocked) {
                    menu.add(0, MENU_UNLOCK_MESSAGE, 0, R.string.menu_unlock)
                            .setOnMenuItemClickListener(l);
                } else if (mIsSmsEnabled) {
                    menu.add(0, MENU_LOCK_MESSAGE, 0, R.string.menu_lock)
                            .setOnMenuItemClickListener(l);
                }
            }

            menu.add(0, MENU_VIEW_MESSAGE_DETAILS, 0, R.string.view_message_details)
                .setOnMenuItemClickListener(l);
			/*HQ_zhangjing 2015-10-07 modified for mms menu tree end*/
        }
    };

    private void editMessageItem(MessageItem msgItem) {
        if ("sms".equals(msgItem.mType)) {
            if (mWorkingMessage.hasSlideshow()) {
                /// M: it's a real slideshow, ignore edit request, show a toast.
                String message = getString(R.string.failed_to_add_media, getString(R.string.viewer_title_sms));
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                return;
            } else {
                editSmsMessageItem(msgItem);
            }
        } else {
            editMmsMessageItem(msgItem);
            mWorkingMessage.setHasMmsDraft(true);
        }
        MessagingNotification.nonBlockingUpdateSendFailedNotification(ComposeMessageActivity.this);
        /// M: @{
        if ((msgItem.isFailedMessage() || msgItem.isSending() || (msgItem.mDeliveryStatus == MessageItem.DeliveryStatus.FAILED))
                && mMsgListAdapter.getCount() <= 1) {
            // For messages with bad addresses, let the user re-edit the recipients.
            initRecipientsEditor(null);
            /// M: Code analyze 046, Whether the recipientedit control has been initialized. @{
            isInitRecipientsEditor = true;
            /// @}
            mMsgListAdapter.changeCursor(null);
            invalidateOptionsMenu();
        }
    }

    private void editSmsMessageItem(MessageItem msgItem) {
        // When the message being edited is the only message in the conversation, the delete
        // below does something subtle. The trigger "delete_obsolete_threads_pdu" sees that a
        // thread contains no messages and silently deletes the thread. Meanwhile, the mConversation
        // object still holds onto the old thread_id and code thinks there's a backing thread in
        // the DB when it really has been deleted. Here we try and notice that situation and
        // clear out the thread_id. Later on, when Conversation.ensureThreadId() is called, we'll
        // create a new thread if necessary.
        synchronized (mConversation) {
            /// M: @{
            //if (mConversation.getMessageCount() <= 1) {
            if (mMsgListAdapter.getCursor().getCount() <= 1) {
            /// @}
                mConversation.clearThreadId();
                MessagingNotification.setCurrentlyDisplayedThreadId(
                    MessagingNotification.THREAD_NONE);
            }
        }
        // Delete the old undelivered SMS and load its content.
        Uri uri = ContentUris.withAppendedId(Sms.CONTENT_URI, msgItem.mMsgId);
        SqliteWrapper.delete(ComposeMessageActivity.this,
                mContentResolver, uri, null, null);

        mWorkingMessage.setConversation(mConversation);
        mWorkingMessage.setText(msgItem.mBody);
    }

    private void editMmsMessageItem(MessageItem msgItem) {
        /// M: fix bug ALPS00834025. ignore a click if too close.
        long oldTime = mEditClickTime;
        mEditClickTime = SystemClock.elapsedRealtime();
        if ((mEditClickTime - oldTime < 1500) && (mEditClickTime - oldTime > 0)) {
            MmsLog.e(TAG, "editMmsMessageItem, ignore a click if too close");
            return;
        }

        /// M: make a failed message editable, the count should reduce too.
        if (mConversation.getMessageCount() == 1) {
            mConversation.setMessageCount(0);
        }
        /// M: Discard the current message in progress.
        mWorkingMessage.discard();

        // Load the selected message in as the working message.
        WorkingMessage newWorkingMessage = WorkingMessage.load(this, msgItem.mMessageUri);
        if (newWorkingMessage == null) {
            MmsLog.e(TAG, "editMmsMessageItem, load returns null message");
            return;
        }
        mWorkingMessage = newWorkingMessage;
        mWorkingMessage.setConversation(mConversation);
        invalidateOptionsMenu();
        /// M: @{
        mAttachmentEditor.update(mWorkingMessage);
        updateTextEditorHeightInFullScreen();
        /// @}
        drawTopPanel(false);

        // WorkingMessage.load() above only loads the slideshow. Set the
        // subject here because we already know what it is and avoid doing
        // another DB lookup in load() just to get it.
        mWorkingMessage.setSubject(msgItem.mSubject, false);

        if (mWorkingMessage.hasSubject()) {
            showSubjectEditor(true);
        }
        /// M: add for mms cc feature. OP09 requested.
        if (mWorkingMessage.hasMmsCc()) {
            showRecipientsCcEditor(true);
        }

        /// M: fix bug ALPS00433858, update read==1(readed) when reload failed-mms
        final MessageItem item = msgItem;
        new Thread(new Runnable() {
            public void run() {
                // TODO Auto-generated method stub
                Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, item.mMsgId);
                ContentValues values = new ContentValues(1);
                values.put(Mms.READ, 1);
                SqliteWrapper.update(ComposeMessageActivity.this,
                        mContentResolver, uri, values, null, null);
            }
        }).start();
    }

    private void copyToClipboard(String str) {
        ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(null, str));
    }

    private void forwardMessage(final MessageItem msgItem) {
        /// M: Code analyze 051, Hide input keyboard (add for input method covered Compose UI issue).@{
        hideInputMethod();
        /// @}

        mTempThreadId = 0;
        // The user wants to forward the message. If the message is an mms message, we need to
        // persist the pdu to disk. This is done in a background task.
        // If the task takes longer than a half second, a progress dialog is displayed.
        // Once the PDU persisting is done, another runnable on the UI thread get executed to start
        // the ForwardMessageActivity.
        getAsyncDialog().runAsync(new Runnable() {
            @Override
            public void run() {
                // This runnable gets run in a background thread.
                if (msgItem.mType.equals("mms")) {
                    SendReq sendReq = new SendReq();
                    String subject = getString(R.string.forward_prefix);
                    if (msgItem.mSubject != null) {
                        subject += msgItem.mSubject;
                    }
                    sendReq.setSubject(new EncodedStringValue(subject));
                    sendReq.setBody(msgItem.mSlideshow.makeCopy());

                    mTempMmsUri = null;
                    try {
                        PduPersister persister =
                                PduPersister.getPduPersister(ComposeMessageActivity.this);
                        // Copy the parts of the message here.
                        /// M: google jb.mr1 patch, group mms
                        mTempMmsUri = persister.persist(sendReq, Mms.Draft.CONTENT_URI, true,
                                MmsPreferenceActivity
                                    .getIsGroupMmsEnabled(ComposeMessageActivity.this), null);
                        mTempThreadId = MessagingNotification.getThreadId(
                                ComposeMessageActivity.this, mTempMmsUri);
                    } catch (MmsException e) {
                        Log.e(TAG, "Failed to copy message: " + msgItem.mMessageUri);
                        runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(ComposeMessageActivity.this, R.string.cannot_save_message, Toast.LENGTH_SHORT).show();
                            }
                        });
                        return;
                    }
                }
            }
        }, new Runnable() {
            @Override
            public void run() {
                // Once the above background thread is complete, this runnable is run
                // on the UI thread.
                Intent intent = createIntent(ComposeMessageActivity.this, 0);
                if (MmsConfig.isNeedExitComposerAfterForward()) {
                    intent.putExtra(KEY_EXIT_ON_SENT, true);
                }
                intent.putExtra(KEY_FORWARDED_MESSAGE, true);
                if (mTempThreadId > 0) {
                    intent.putExtra("thread_id", mTempThreadId);
                }

                if (msgItem.mType.equals("sms")) {
                    /// M: Code analyze 001, Plugin opeartor. @{
                    String smsBody = msgItem.mBody;

                    Contact contact = Contact.get(msgItem.mAddress, false);
                    String nameAndNumber = Contact.formatNameAndNumber(contact.getName(), contact.getNumber(), "");
                    IMmsPreferenceExt prefPlugin = (IMmsPreferenceExt) MmsPluginManager.getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_PREFERENCE);
					/*HQ_zhangjing 2015-07-24 modified for sms forward begin*/
                    //smsBody = prefPlugin.formatSmsBody(ComposeMessageActivity.this, smsBody, nameAndNumber, msgItem.mBoxId);
					Log.d(TAG, "forward sms");
                    smsBody = formatSmsBody(ComposeMessageActivity.this, smsBody, nameAndNumber, msgItem.mBoxId);
					/*HQ_zhangjing 2015-07-24 modified for sms forward end*/
                    intent.putExtra("sms_body", smsBody);
                    /// @}
                } else {
                    intent.putExtra("msg_uri", mTempMmsUri);
                    String subject = getString(R.string.forward_prefix);
                    if (msgItem.mSubject != null) {
                        subject += msgItem.mSubject;
                    }
                    intent.putExtra("subject", subject);
                }
                // ForwardMessageActivity is simply an alias in the manifest for
                // ComposeMessageActivity. We have to make an alias because ComposeMessageActivity
                // launch flags specify singleTop. When we forward a message, we want to start a
                // separate ComposeMessageActivity. The only way to do that is to override the
                // singleTop flag, which is impossible to do in code. By creating an alias to the
                // activity, without the singleTop flag, we can launch a separate
                // ComposeMessageActivity to edit the forward message.
                intent.setClassName(ComposeMessageActivity.this,
                        "com.android.mms.ui.ForwardMessageActivity");
                startActivity(intent);
            }
        }, R.string.sync_mms_to_db);
    }

    /**
     * Context menu handlers for the message list view.
     */
    private final class MsgListMenuClickListener implements MenuItem.OnMenuItemClickListener {
        private MessageItem mMsgItem;

        public MsgListMenuClickListener(MessageItem msgItem) {
            mMsgItem = msgItem;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            if (mMsgItem == null) {
                return false;
            }
            if (mMsgItem.mIpMessageId > 0 && onIpMessageMenuItemClick(item, mMsgItem)) {
                return true;
            }

            switch (item.getItemId()) {
                case MENU_EDIT_MESSAGE:
                    editMessageItem(mMsgItem);
                    drawBottomPanel();
                    return true;
                /// M: for ipmessage copy
                case MENU_COPY:
                case MENU_COPY_MESSAGE_TEXT:
                    /// M: keep original string when copy, if delete '\r', search will be failed. @{
                    if (mMsgItem.mBody != null) {
                        copyToClipboard(mMsgItem.mBody);
                    } else {
                        MmsLog.i(TAG, "onMenuItemClick, mMsgItem.mBody == null");
                        return false;
                    }
                    /// @}
                    return true;
				/*HQ_zhangjing add for al812 on 2015-07-03 for resend the failed sms begin*/
				case MENU_RESEND_MESSAGE:
					//resend message
					simSelectionForSendFaildMsg(mMsgItem.mMsgId);
					return true;
				/*HQ_zhangjing add end*/
                case MENU_FORWARD_MESSAGE:
                    /// M: @{
                    final MessageItem mRestrictedItem = mMsgItem;
                    if (WorkingMessage.sCreationMode == 0 ||
                      !MessageUtils.isRestrictedType(ComposeMessageActivity.this, mMsgItem.mMsgId)) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                forwardMessage(mRestrictedItem);
                            }
                        });
                    } else if (WorkingMessage.sCreationMode == WorkingMessage.WARNING_TYPE) {
                        new AlertDialog.Builder(ComposeMessageActivity.this)
                        .setTitle(R.string.restricted_forward_title)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setMessage(R.string.restricted_forward_message)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    int createMode = WorkingMessage.sCreationMode;
                                    WorkingMessage.sCreationMode = 0;
                                    runOnUiThread(new Runnable() {
                                        public void run() {
                                            forwardMessage(mRestrictedItem);
                                        }
                                    });
                                    WorkingMessage.sCreationMode = createMode;
                                }
                            })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                    }
                    /// @}
                    return true;

                case MENU_VIEW_SLIDESHOW:
                    /// M: Code analyze 024, If the click operator can be responsed. @{
                    if (mClickCanResponse) {
                        mClickCanResponse = false;
                        MessageUtils.viewMmsMessageAttachment(ComposeMessageActivity.this,
                                ContentUris.withAppendedId(Mms.CONTENT_URI, mMsgItem.mMsgId), null,
                                getAsyncDialog());
                    return true;
                    }
                    /// @}
                case MENU_VIEW_MESSAGE_DETAILS:
                    return showMessageDetails(mMsgItem);

                case MENU_DELETE_MESSAGE: {
                    DeleteMessageListener l = new DeleteMessageListener(mMsgItem);

                    /// M: Code analyze 027,Add for deleting one message.@{
                    // (Why only query sms table?)
                    String where = Telephony.Mms._ID + "=" + mMsgItem.mMsgId;
                    String[] projection = new String[] { Sms.Inbox.THREAD_ID };
                    MmsLog.d(TAG, "where:" + where);
                    Cursor queryCursor = Sms.query(getContentResolver(), // query uri: content://sms
                            projection, where, null);
                    if (queryCursor.moveToFirst()) {
                        mThreadId = queryCursor.getLong(0);
                    }
                    if (queryCursor != null) {
                        queryCursor.close();
                    }
                    /// @}
                    confirmDeleteDialog(l, mMsgItem.mLocked);
                    return true;
                }
                case MENU_DELIVERY_REPORT:
                    /// M: add for OP09; @{
                    if (MmsConfig.isMassTextEnable()) {
                        showDeliveryReportForCT(mMsgItem.mMsgId, mMsgItem.mType, mMsgItem.mIpMessageId);
                        return true;
                    }
                    /// @}
                    showDeliveryReport(mMsgItem.mMsgId, mMsgItem.mType);
                    return true;

                case MENU_COPY_TO_SDCARD: {
                    /// M: Code analyze 021, Copy all valid parts of the attachment(pdu) to SD card.
                    /// This opeartor will be removed to a separate activity.  @{
                    /// M: new feature, change default disk when storage is full
                    long availSize = MessageUtils.getAvailableBytesInFileSystemAtGivenRoot
                                                    (StorageManagerEx.getDefaultPath());
                    boolean haveExSD = MessageUtils.existingSD(ComposeMessageActivity.this, true);
                    boolean haveInSD = MessageUtils.existingSD(ComposeMessageActivity.this, false);

                    /// M: fix bug ALPS00574679, modify toast string when haven't SD Card @{
                    if (!haveExSD && !haveInSD) {
                        Toast.makeText(ComposeMessageActivity.this,
                                getString(R.string.no_sdcard_suggestion),
                                Toast.LENGTH_LONG).show();
                        return false;
                    }
                    /// @}

                    if (mMsgItem.mMessageSize > availSize) {
                        if ((haveInSD && !haveExSD) || (!haveInSD && haveExSD)) {
                            Toast.makeText(ComposeMessageActivity.this,
                                    getString(R.string.export_disk_problem),
                                    Toast.LENGTH_LONG).show();
                            return false;
                        } else {
                            new AlertDialog.Builder(ComposeMessageActivity.this)
                            .setTitle(R.string.copy_to_sdcard_fail)
                            .setIconAttribute(android.R.attr.alertDialogIcon)
                            .setMessage(R.string.change_default_disk)
                            .setPositiveButton(R.string.change, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent("android.settings.MEMORY_CARD_SETTINGS");
                                    startActivity(intent);
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                        }
                    } else {
                        Intent i = new Intent(ComposeMessageActivity.this, MultiSaveActivity.class);
                        i.putExtra("msgid", mMsgItem.mMsgId);
                        IMmsAttachmentEnhanceExt attachEnhancePlugin =
                                        (IMmsAttachmentEnhanceExt) MmsPluginManager.getMmsPluginObject(
                                                                MmsPluginManager.MMS_PLUGIN_TYPE_MMS_ATTACHMENT_ENHANCE);

                        Log.i(TAG, "attachEnhancePlugin = " + attachEnhancePlugin);
                        if (attachEnhancePlugin != null) {
                            attachEnhancePlugin.setSaveAttachIntent(i, IMmsAttachmentEnhanceExt.MMS_SAVE_ALL_ATTACHMENT);
                            Log.i(TAG, "attachEnhancePlugin.setSaveAttachIntent MMS_SAVE_ALL_ATTACHMENT");
                        }
                        startActivityForResult(i, REQUEST_CODE_MULTI_SAVE);
                     }
                    return true;
                }

                case MENU_SAVE_RINGTONE: {
                    int resId = getDrmMimeSavedStringRsrc(mMsgItem.mMsgId,
                            saveRingtone(mMsgItem.mMsgId));
                    Toast.makeText(ComposeMessageActivity.this, resId, Toast.LENGTH_SHORT).show();
                    return true;
                }
                case MENU_LOCK_MESSAGE: {
                    lockMessage(mMsgItem, true);
                    return true;
                }
                case MENU_UNLOCK_MESSAGE: {
                    lockMessage(mMsgItem, false);
                    return true;
                }

                /// M: Code analyze 022, Add bookmark. Maybe exist multi URL addresses. @{
                case MENU_ADD_TO_BOOKMARK: {
                    if (mURLs.size() == 1) {
                        Browser.saveBookmark(ComposeMessageActivity.this, null, mURLs.get(0));
                    } else if (mURLs.size() > 1) {
                        CharSequence[] items = new CharSequence[mURLs.size()];
                        for (int i = 0; i < mURLs.size(); i++) {
                            items[i] = mURLs.get(i);
                        }
                        new AlertDialog.Builder(ComposeMessageActivity.this)
                            .setTitle(R.string.menu_add_to_bookmark)
                            .setIcon(MessageResource.drawable.ic_dialog_menu_generic)
                            .setItems(items, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    Browser.saveBookmark(ComposeMessageActivity.this, null, mURLs.get(which));
                                    }
                                })
                            .show();
                    }
                    return true;
                }

                /// M: Code analyze 007, Get information from Sub or save message to Sub. @{
                case MENU_SAVE_MESSAGE_TO_SUB: {
                    mSaveMsgThread = new SaveMsgThread(mMsgItem.mType, mMsgItem.mMsgId);
                    mSaveMsgThread.start();
                    return true;
                }
                /// @}

                /// M: Code analyze 016, Add for select text copy. @{
                case MENU_SELECT_TEXT: {
                    AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
                    Log.i(TAG, "onMenuItemClick(): info.position = " + info.position);
                    mMsgListAdapter.getItemId(info.position);
					/*HQ_zhanging 2015-07-22 modified for select text exception begin*/
					TimeAxisWidget taw = (TimeAxisWidget)((info.targetView).findViewById(R.id.item_date));
					MessageListItem msglistItem = (MessageListItem)taw.getContent();
                    //MessageListItem msglistItem = (MessageListItem) info.targetView;
					/*HQ_zhanging 2015-07-22 modified for select text exception end*/
                    if (msglistItem != null) {
                        Log.i(TAG, "msglistItem != null");
                        TextView textView = (TextView) msglistItem.findViewById(R.id.text_view);
                        AlertDialog.Builder builder = new AlertDialog.Builder(ComposeMessageActivity.this);
                        LayoutInflater factory = LayoutInflater.from(builder.getContext());
                        final View textEntryView = factory.inflate(R.layout.alert_dialog_text_entry, null);
                        EditText contentSelector = (EditText) textEntryView.findViewById(R.id.content_selector);
                        contentSelector.setText(textView.getText());
                        builder.setTitle(R.string.select_text)
                               .setView(textEntryView)
                               .setPositiveButton(R.string.yes, null)
                               .show();
                    }
                    return true;
                }
                case MENU_ADD_ADDRESS_TO_CONTACTS: {
                    mAddContactIntent = item.getIntent();
                    startActivityForResult(mAddContactIntent, REQUEST_CODE_ADD_CONTACT);
                    return true;
                }
                /// @}

                default:
                    return false;
            }
        }
    }


    private void lockMessage(final MessageItem msgItem, final boolean locked) {
        Uri uri;
        if ("sms".equals(msgItem.mType)) {
            uri = Sms.CONTENT_URI;
        } else {
            uri = Mms.CONTENT_URI;
        }
        final Uri lockUri = ContentUris.withAppendedId(uri, msgItem.mMsgId);

        final ContentValues values = new ContentValues(1);
        values.put("locked", locked ? 1 : 0);

        new Thread(new Runnable() {
            @Override
            public void run() {
                /// M: For OP09 @{
                if (MmsConfig.isMassTextEnable() && msgItem.mIpMessageId < 0) {
                    mMmsComposePlugin.lockMassTextMsg(getApplicationContext(), msgItem.mMsgId,
                            System.currentTimeMillis(), locked);
                    return;
                }
                /// @}
                getContentResolver().update(lockUri, values, null, null);
            }
        }, "ComposeMessageActivity.lockMessage").start();
    }

    /**
     * Looks to see if there are any valid parts of the attachment that can be copied to a SD card.
     * @param msgId
     */
    private boolean haveSomethingToCopyToSDCard(long msgId) {
        PduBody body = null;
        try {
            body = SlideshowModel.getPduBody(this,
                        ContentUris.withAppendedId(Mms.CONTENT_URI, msgId));
        } catch (MmsException e) {
            Log.e(TAG, "haveSomethingToCopyToSDCard can't load pdu body: " + msgId);
        }
        if (body == null) {
            return false;
        }

        boolean result = false;
        int partNum = body.getPartsNum();
        for (int i = 0; i < partNum; i++) {
            PduPart part = body.getPart(i);
            // M: fix bug ALPS00355917
            byte[] fileName = part.getFilename();
            String mSrc = null;
            if (fileName == null) {
                fileName = part.getContentLocation();
            }
            if (fileName != null) {
                mSrc = new String(fileName);
            }
            String type =  MessageUtils.getContentType(new String(part.getContentType()), mSrc);
            part.setContentType(type.getBytes());
            if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                log("[CMA] haveSomethingToCopyToSDCard: part[" + i + "] contentType=" + type);
            }
            /// M: Code analyze 057,Now, if the pdu type is "application/ogg" or
            /// vcard and vcalender attachment can be saved to sdcard.
            if (MmsContentType.isImageType(type) || MmsContentType.isVideoType(type) ||
                    MmsContentType.isAudioType(type) || DrmUtils.isDrmType(type) ||
                    "application/ogg".equalsIgnoreCase(type) || FileAttachmentModel.isSupportedFile(part)
                    /// M: fix bug ALPS00446644, support dcf (0ct-stream) file to save
                    || (mSrc != null && mSrc.toLowerCase().endsWith(".dcf"))) {
            /// @}
                result = true;
                break;
            }
        }

        /// M: add for attachment enhance
        // Justify weather there are attachments in parts but not in slides
        // SlideshowModel mSlideShowModel = mWorkingMessage.getSlideshow();
        if (MmsConfig.isSupportAttachEnhance()) {
            SlideshowModel mSlideShow = null;
            try {
                mSlideShow = SlideshowModel.createFromPduBody(this, body);
            } catch (MmsException e) {
                MmsLog.e(TAG, "Create from pdubody exception!");
            }

            if (mSlideShow != null) {
                if (mSlideShow.getAttachFiles() != null) {
                    if (mSlideShow.getAttachFiles().size() != 0) {
                        result = true;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Copies media from an Mms to the DrmProvider
     * @param msgId
     */
    private boolean saveRingtone(long msgId) {
        boolean result = true;
        PduBody body = null;
        try {
            body = SlideshowModel.getPduBody(this,
                        ContentUris.withAppendedId(Mms.CONTENT_URI, msgId));
        } catch (MmsException e) {
            Log.e(TAG, "copyToDrmProvider can't load pdu body: " + msgId);
        }
        if (body == null) {
            return false;
        }

        int partNum = body.getPartsNum();
        for (int i = 0; i < partNum; i++) {
            PduPart part = body.getPart(i);
            String type = new String(part.getContentType());

            if (DrmUtils.isDrmType(type)) {
                // All parts (but there's probably only a single one) have to be successful
                // for a valid result.
                result &= copyPart(part, Long.toHexString(msgId));
            }
        }
        return result;
    }

    /**
     * Returns true if any part is drm'd audio with ringtone rights.
     * @param msgId
     * @return true if one of the parts is drm'd audio with rights to save as a ringtone.
     */
    private boolean isDrmRingtoneWithRights(long msgId) {
        PduBody body = null;
        try {
            body = SlideshowModel.getPduBody(this,
                        ContentUris.withAppendedId(Mms.CONTENT_URI, msgId));
        } catch (MmsException e) {
            Log.e(TAG, "isDrmRingtoneWithRights can't load pdu body: " + msgId);
        }
        if (body == null) {
            return false;
        }

        int partNum = body.getPartsNum();
        for (int i = 0; i < partNum; i++) {
            PduPart part = body.getPart(i);
            String type = new String(part.getContentType());

            if (DrmUtils.isDrmType(type)) {
                String mimeType = MmsApp.getApplication().getDrmManagerClient()
                        .getOriginalMimeType(part.getDataUri());
                if (MmsContentType.isAudioType(mimeType) && DrmUtils.haveRightsForAction(part.getDataUri(),
                        OmaDrmStore.Action.RINGTONE)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if all drm'd parts are forwardable.
     * @param msgId
     * @return true if all drm'd parts are forwardable.
     */
    private boolean isForwardable(long msgId) {
        PduBody body = null;
        try {
            body = SlideshowModel.getPduBody(this,
                        ContentUris.withAppendedId(Mms.CONTENT_URI, msgId));
        } catch (MmsException e) {
            Log.e(TAG, "getDrmMimeType can't load pdu body: " + msgId);
        }
        if (body == null) {
            return false;
        }

        int partNum = body.getPartsNum();
        for (int i = 0; i < partNum; i++) {
            PduPart part = body.getPart(i);
            String type = new String(part.getContentType());
            if (DrmUtils.isDrmType(type) && !DrmUtils.haveRightsForAction(part.getDataUri(),
                    OmaDrmStore.Action.TRANSFER)) {
                    return false;
            }
        }
        return true;
    }

    private int getDrmMimeMenuStringRsrc(long msgId) {
        if (isDrmRingtoneWithRights(msgId)) {
            return R.string.save_ringtone;
        }
        return 0;
    }

    private int getDrmMimeSavedStringRsrc(long msgId, boolean success) {
        if (isDrmRingtoneWithRights(msgId)) {
            return success ? R.string.saved_ringtone : R.string.saved_ringtone_fail;
        }
        return 0;
    }

    /**
     * Copies media from an Mms to the "download" directory on the SD card. If any of the parts
     * are audio types, drm'd or not, they're copied to the "Ringtones" directory.
     * @param msgId
     */
    private boolean copyMedia(long msgId) {
        boolean result = true;
        PduBody body = null;
        try {
            body = SlideshowModel.getPduBody(this,
                        ContentUris.withAppendedId(Mms.CONTENT_URI, msgId));
        } catch (MmsException e) {
            Log.e(TAG, "copyMedia can't load pdu body: " + msgId);
        }
        if (body == null) {
            return false;
        }

        int partNum = body.getPartsNum();
        for (int i = 0; i < partNum; i++) {
            PduPart part = body.getPart(i);

            // all parts have to be successful for a valid result.
            result &= copyPart(part, Long.toHexString(msgId));
        }
        return result;
    }

    private boolean copyPart(PduPart part, String fallback) {
        Uri uri = part.getDataUri();
        String type = new String(part.getContentType());
        boolean isDrm = DrmUtils.isDrmType(type);
        if (isDrm) {
            type = MmsApp.getApplication().getDrmManagerClient()
                    .getOriginalMimeType(part.getDataUri());
        }
        if (!MmsContentType.isImageType(type) && !MmsContentType.isVideoType(type) &&
                !MmsContentType.isAudioType(type)) {
            return true;    // we only save pictures, videos, and sounds. Skip the text parts,
                            // the app (smil) parts, and other type that we can't handle.
                            // Return true to pretend that we successfully saved the part so
                            // the whole save process will be counted a success.
        }
        InputStream input = null;
        FileOutputStream fout = null;
        try {
            input = mContentResolver.openInputStream(uri);
            if (input instanceof FileInputStream) {
                FileInputStream fin = (FileInputStream) input;

                byte[] location = part.getName();
                if (location == null) {
                    location = part.getFilename();
                }
                if (location == null) {
                    location = part.getContentLocation();
                }

                String fileName;
                if (location == null) {
                    // Use fallback name.
                    fileName = fallback;
                } else {
                    // For locally captured videos, fileName can end up being something like this:
                    //      /mnt/sdcard/Android/data/com.android.mms/cache/.temp1.3gp
                    fileName = new String(location);
                }
                File originalFile = new File(fileName);
                fileName = originalFile.getName();  // Strip the full path of where the "part" is
                                                    // stored down to just the leaf filename.

                // Depending on the location, there may be an
                // extension already on the name or not. If we've got audio, put the attachment
                // in the Ringtones directory.
                String dir = Environment.getExternalStorageDirectory() + "/"
                                + (MmsContentType.isAudioType(type) ? Environment.DIRECTORY_RINGTONES :
                                    Environment.DIRECTORY_DOWNLOADS)  + "/";
                String extension;
                int index = fileName.lastIndexOf('.');
                if (index == -1) {
                    extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
                } else {
                    extension = fileName.substring(index + 1, fileName.length());
                    fileName = fileName.substring(0, index);
                }
                MmsLog.i(TAG, "Save part extension name is: " + extension);
                if (isDrm) {
                    extension += DrmUtils.getConvertExtension(type);
                }
                // Remove leading periods. The gallery ignores files starting with a period.
                fileName = fileName.replaceAll("^.", "");

                File file = getUniqueDestination(dir + fileName, extension);

                // make sure the path is valid and directories created for this file.
                File parentFile = file.getParentFile();
                if (!parentFile.exists() && !parentFile.mkdirs()) {
                    Log.e(TAG, "[MMS] copyPart: mkdirs for " + parentFile.getPath() + " failed!");
                    return false;
                }

                fout = new FileOutputStream(file);

                byte[] buffer = new byte[8000];
                int size = 0;
                while ((size = fin.read(buffer)) != -1) {
                    fout.write(buffer, 0, size);
                }

                // Notify other applications listening to scanner events
                // that a media file has been added to the sd card
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                        Uri.fromFile(file)));
            }
        } catch (IOException e) {
            // Ignore
            Log.e(TAG, "IOException caught while opening or reading stream", e);
            return false;
        } finally {
            if (null != input) {
                try {
                    input.close();
                } catch (IOException e) {
                    // Ignore
                    Log.e(TAG, "IOException caught while closing stream", e);
                    return false;
                }
            }
            if (null != fout) {
                try {
                    fout.close();
                } catch (IOException e) {
                    // Ignore
                    Log.e(TAG, "IOException caught while closing stream", e);
                    return false;
                }
            }
        }
        return true;
    }

    private File getUniqueDestination(String base, String extension) {
        File file = new File(base + "." + extension);

        for (int i = 2; file.exists(); i++) {
            file = new File(base + "_" + i + "." + extension);
        }
        return file;
    }

    private void showDeliveryReport(long messageId, String type) {
        Intent intent = new Intent(this, DeliveryReportActivity.class);
        intent.putExtra("message_id", messageId);
        intent.putExtra("message_type", type);

        startActivity(intent);
    }

    /**
     * M: Add for OP09;
     * @param messageId
     * @param type
     * @param groupId
     */
    private void showDeliveryReportForCT(long messageId, String type, long groupId) {
        Intent intent = new Intent(this, DeliveryReportActivity.class);
        intent.putExtra("message_id", messageId);
        intent.putExtra("message_type", type);
        mMmsUtils.setIntentDateForMassTextMessage(intent, groupId);

        startActivity(intent);
    }

    private final IntentFilter mHttpProgressFilter = new IntentFilter(PROGRESS_STATUS_ACTION);
    private boolean mHasRegisteredHttpProgressReceiver = false;

    private final BroadcastReceiver mHttpProgressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PROGRESS_STATUS_ACTION.equals(intent.getAction())) {
                long token = intent.getLongExtra("token",
                                    SendingProgressTokenManager.NO_TOKEN);
                if (token != mConversation.getThreadId()) {
                    return;
                }

                int progress = intent.getIntExtra("progress", 0);
                switch (progress) {
                    case PROGRESS_START:
                        setProgressBarVisibility(true);
                        break;
                    case PROGRESS_ABORT:
                    case PROGRESS_COMPLETE:
                        setProgressBarVisibility(false);
                        break;
                    default:
                        setProgress(100 * progress);
                }
            }
        }
    };

    private static ContactList sEmptyContactList;

    private ContactList getRecipients() {
        // If the recipients editor is visible, the conversation has
        // not really officially 'started' yet.  Recipients will be set
        // on the conversation once it has been saved or sent.  In the
        // meantime, let anyone who needs the recipient list think it
        // is empty rather than giving them a stale one.
        if (isRecipientsEditorVisible()) {
            if (sEmptyContactList == null) {
                sEmptyContactList = new ContactList();
            }
            return sEmptyContactList;
        }
        return mConversation.getRecipients();
    }

    private void updateTitle(ContactList list) {
        String title = null;
        String subTitle = null;
        Drawable avatarIcon = null;
        int integrationMode = 0;
        int cnt = list.size();
        String langage = Locale.getDefault().getLanguage();//add by lipeng for number display
        MmsLog.d(TAG, "updateTitle(): list.size()" + list.size());
        switch (cnt) {
            case 0: {
                /// M: only show "new message" string if list size is 0, if a number
                /// end without "," or ";" should not display on title.
                title = getString(R.string.new_message);
                break;
            }
            case 1: {
          //modify by lipeng for dispaly number at 2015-10-02
		  if(MessageUtils.isInteger(list.get(0).getName().toString())&&(langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw"))){
                title = "\u202D"+list.get(0).getName()+"\u202C";      // get name returns the number if there's no
                                                    			     // name available.
		  } else {
			    title = list.get(0).getName();
		  }//end lipeng
                Drawable sDefaultContactImage = this.getResources().getDrawable(
                    R.drawable.ic_contact_picture);
                avatarIcon = list.get(0).getAvatar(this, sDefaultContactImage,
                                                    mConversation != null ? mConversation.getThreadId() : -1);
                String number = list.get(0).getNumber();
				//modify by lipeng for dispaly number at 2015-10-02
				if ((langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw"))&&number!=null && MessageUtils.isInteger(number)) {
					number = "\u202D" + number + "\u202C";
				}//end by lipeng
                if (IpMessageUtils.getIpMessagePlugin(ComposeMessageActivity.this).isActualPlugin()) {
                    title = IpMessageUtils.getContactManager(this).getNameByNumber(number);
                    if (TextUtils.isEmpty(title)) {
						//modify by lipeng for dispaly number at 2015-10-02
                        if(MessageUtils.isInteger(list.get(0).getName().toString())&&(langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw"))){
                      	title = "\u202D"+list.get(0).getName()+"\u202C";
              		  }else{       			    
                        title = list.get(0).getName();
              		  }//end by lipeng
                    }
                }
                String numberAfterFormat;
                if (!title.equals(number)) {
                    if (number.startsWith(IpMessageConsts.JOYN_START)) {
                        numberAfterFormat = MessageUtils.formatNumber(number.substring(4),
                                this.getApplicationContext());
                    } else {
                        numberAfterFormat = MessageUtils.formatNumber(number,
                                this.getApplicationContext());
                    }
                    if (!title.equals(numberAfterFormat)) {
                        subTitle = numberAfterFormat;
                    }
                }
                integrationMode = IpMessageUtils.getContactManager(this).getIntegrationMode(number);
                /// M: For OP09
                if (MmsConfig.isNumberLocationEnable()) {
		       /*add by liruihong for HQ01428752 begin*/
		        if( title == null ||subTitle ==null  ){
                		MmsLog.d(TAG," title = "+subTitle+",subTitle = "+subTitle);
		        }else{
		            subTitle = mMmsComposePlugin.getNumberLocation(this, number);
			}
		       /*add by liruihong for HQ01428752 end*/

                }
                /// M: fix bug ALPS00488976, group mms @{
                if (mMsgListAdapter.isGroupConversation()) {
                    mMsgListAdapter.setIsGroupConversation(false);
                }
                /// @}
                break;
            }
            default: {
                // Handle multiple recipients
                avatarIcon = this.getResources().getDrawable(R.drawable.ic_contact_picture);
                title = list.formatNames(", ");
				//add by lipeng for dispaly number at 2015-10-02
            	if(langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw")){///RTL language
            		 title = list.formatNamesForLtr(", ");
            	}//end by lipeng
	        /*HQ_sunli  HQ01508305 20151127 begin*/
                 if((SystemProperties.get("ro.hq.smsgroup.recipient").equals("1"))&&!mWorkingMessage.requiresMms()){
		subTitle = getResources().getQuantityString(R.plurals.smsgroup_recipient_count, cnt, cnt);
		/*HQ_sunli  HQ01508305 20151127 end*/
		}else{
                subTitle = getResources().getQuantityString(R.plurals.recipient_count, cnt, cnt);
		}
                break;
            }
        }
        mDebugRecipients = list.serialize();

        ActionBar actionBar = getActionBar();
        if (actionBar == null) {
            return;
        }
        if (avatarIcon != null) {
            actionBar.setIcon(avatarIcon);
        }

        if (actionBar.getCustomView() == null) {
            actionBar.setCustomView(R.layout.actionbar_message_title);
        }
        mActionBarCustomView = actionBar.getCustomView();
        mTopTitle = (TextView) mActionBarCustomView.findViewById(R.id.tv_top_title);
        mTopSubtitle = (TextView) mActionBarCustomView.findViewById(R.id.tv_top_subtitle);
        mFullIntegratedView = (ImageView) mActionBarCustomView.findViewById(R.id.iv_full_integrated);
        Drawable integratedIcon = IpMessageUtils.getResourceManager(this).getSingleDrawable(IpMessageConsts.drawable.ipmsg_full_integrated);
        mFullIntegratedView.setImageDrawable(integratedIcon);
        if (integrationMode == IpMessageConsts.IntegrationMode.FULLY_INTEGRATED) {
            mFullIntegratedView.setVisibility(View.VISIBLE);
        } else {
            mFullIntegratedView.setVisibility(View.GONE);
        }
		    /*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display begin*/
		mShoMoreMenu = (ImageView) mActionBarCustomView.findViewById(R.id.menu_more);
		mShoMoreMenu.setOnClickListener( moreViewListener );
		/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display end*/

		
        asyncUpdateThreadMuteIcon();
        ///M: ALPS00772324 The recipient didn't shown completely after input a recipient's numbers
        ///M: and put the cursor to the text field.
        ///M: setMaxWidth to a very large value for textView can width can be wrap_content
        mTopTitle.setMaxWidth(3000);
        mTopTitle.setText(title);
        if (TextUtils.isEmpty(subTitle)) {
            if (cnt == 0) {
                actionBar.setIcon(R.drawable.ic_launcher_smsmms);
            }
            mTopSubtitle.setVisibility(View.GONE);
        } else {
            mTopSubtitle.setText(subTitle);
            mTopSubtitle.setVisibility(View.VISIBLE);
        }
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayShowTitleEnabled(false);
		/*HQ_zhangjing add for al812 mms ui begin*/
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setHomeButtonEnabled(false);
		/*HQ_zhangjing add for al812 mms ui end*/
    }

    private void asyncUpdateThreadMuteIcon() {
        MmsLog.d(TAG, "asyncUpdateThreadMuteIcon");
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean value = false;
                if (mConversation != null && mConversation.getThreadId() > 0) {
                    value = MessageUtils.checkNeedNotify(ComposeMessageActivity.this, mConversation.getThreadId(), null);
                } else {
                    value = MessageUtils.checkNeedNotify(ComposeMessageActivity.this, 0, null);
                }
                final boolean needNotify = value;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        MmsLog.d(TAG, "asyncUpdateThreadMuteIcon: meedNotify is " + needNotify);
                        ActionBar actionBar = getActionBar();
                        if (actionBar.getCustomView() == null) {
                            actionBar.setCustomView(R.layout.actionbar_message_title);
                        }
                        mActionBarCustomView = actionBar.getCustomView();
						/*HQ_zhangjing add for al812 mms ui begin*/
                        //mMuteLogo = (ImageView) mActionBarCustomView.findViewById(R.id.iv_silent);
                        //mMuteLogo.setVisibility(needNotify ? View.INVISIBLE : View.VISIBLE);
						/*HQ_zhangjing add for al812 mms ui end*/
						/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display begin*/
						mShoMoreMenu = (ImageView) mActionBarCustomView.findViewById(R.id.menu_more);
						mShoMoreMenu.setOnClickListener( moreViewListener );
						/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display end*/
                    }
                });
            }
        }, "check and update mute icon").start();
    }

    // Get the recipients editor ready to be displayed onscreen.
    private void initRecipientsEditor(Bundle bundle) {
        /// M: Code analyze 046, Whether the recipientedit control has been initialized. @{
        if (isRecipientsEditorVisible() && isInitRecipientsEditor) {
        /// @}
            return;
        }
        if (IpMessageUtils.getIpMessagePlugin(this).isActualPlugin() && mCurrentChatMode == IpMessageConsts.ChatMode.JOYN) {
            return;
        }
        // Must grab the recipients before the view is made visible because getRecipients()
        // returns empty recipients when the editor is visible.
        ContactList recipients = getRecipients();
        /// M: Code analyze 058, Remove exceed recipients.
        while (!recipients.isEmpty() && recipients.size() > RECIPIENTS_LIMIT_FOR_SMS) {
            recipients.remove(RECIPIENTS_LIMIT_FOR_SMS);
        }
        /// @}

        ViewStub stub = (ViewStub)findViewById(R.id.recipients_editor_stub);
        if (stub != null) {
            View stubView = stub.inflate();
            mRecipientsEditor = (RecipientsEditor) stubView.findViewById(R.id.recipients_editor);
            mRecipientsPicker = (ImageButton) stubView.findViewById(R.id.recipients_picker);
        } else {
            mRecipientsEditor = (RecipientsEditor)findViewById(R.id.recipients_editor);
            mRecipientsEditor.setVisibility(View.VISIBLE);
            mRecipientsPicker = (ImageButton)findViewById(R.id.recipients_picker);
            /// M: Code analyze 059, Set the pick button visible or
            /// invisible the same as recipient editor.
            mRecipientsPicker.setVisibility(View.VISIBLE);
            /// @}
        }
        mRecipientsPicker.setOnClickListener(this);
        mRecipientsEditor.removeChipChangedListener(mChipWatcher);
        mRecipientsEditor.addChipChangedListener(mChipWatcher);

        mRecipientsPicker.setEnabled(mIsSmsEnabled);
        mRecipientsEditor.setEnabled(mIsSmsEnabled);
        mRecipientsEditor.setFocusableInTouchMode(mIsSmsEnabled);
        mRecipientsEditor.setIsTouchable(mIsSmsEnabled);

        // M: indicate contain email address or not in RecipientsEditor candidates. @{
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ComposeMessageActivity.this);
        boolean showEmailAddress = prefs.getBoolean(GeneralPreferenceActivity.SHOW_EMAIL_ADDRESS, true);
        MmsLog.d(TAG, "initRecipientsEditor(), showEmailAddress = " + showEmailAddress);
        /// M: fix bug ALPS00380930
        if (mRecipientsEditor.getAdapter() == null) {
            ChipsRecipientAdapter chipsAdapter = new ChipsRecipientAdapter(this);
            chipsAdapter.setShowEmailAddress(showEmailAddress);
            mRecipientsEditor.setAdapter(chipsAdapter);
        } else {
            ((ChipsRecipientAdapter) mRecipientsEditor.getAdapter()).setShowEmailAddress(showEmailAddress);
        }
        // @}
        if (bundle == null) {
            mRecipientsEditor.populate(new ContactList());
            mRecipientsEditor.populate(recipients);
        }
        mRecipientsEditor.setOnCreateContextMenuListener(mRecipientsMenuCreateListener);
        // TODO : Remove the max length limitation due to the multiple phone picker is added and the
        // user is able to select a large number of recipients from the Contacts. The coming
        // potential issue is that it is hard for user to edit a recipient from hundred of
        // recipients in the editor box. We may redesign the editor box UI for this use case.
        // mRecipientsEditor.setFilters(new InputFilter[] {
        //         new InputFilter.LengthFilter(RECIPIENTS_MAX_LENGTH) });

        mRecipientsEditor.setOnSelectChipRunnable(new Runnable() {
            public void run() {
                // After the user selects an item in the pop-up contacts list, move the
                // focus to the text editor if there is only one recipient.  This helps
                // the common case of selecting one recipient and then typing a message,
                // but avoids annoying a user who is trying to add five recipients and
                // keeps having focus stolen away.
                if (mRecipientsEditor.getRecipientCount() == 1) {
                    // if we're in extract mode then don't request focus
                    final InputMethodManager inputManager = mInputMethodManager;
                    if (inputManager == null || !inputManager.isFullscreenMode()) {
                        if (mBottomPanel != null && mBottomPanel.getVisibility() == View.VISIBLE) {
                            mTextEditor.requestFocus();
                        }
                    }
                }
            }
        });

        mRecipientsEditor.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    /// M: add for ip message
                    MmsLog.d(IPMSG_TAG, "onFocusChange(): mRecipientsEditor get focus.");
                    showSharePanel(false);
                    if (mIsLandscape) {
                        mTextEditor.setMaxHeight(
                                mReferencedTextEditorTwoLinesHeight * mCurrentMaxHeight / mReferencedMaxHeight);
                    } else {
                        updateTextEditorHeightInFullScreen();
                    }
                }
                /// M: Fix ipmessage bug @{
                updateCurrentChatMode(null) ;
                /// @}
            }
        });

        /// M: add for ipmessage
        mRecipientsEditor.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                showSharePanel(false);
                return false;
            }
        });
        mRecipientsEditor.setChipProcessListener(new ChipProcessListener() {
            public void onChipProcessDone() {
                mIsPopulatingRecipients = false;
                if (mIsConvertToastDelayed) {
                    mIsConvertToastDelayed = false;
                    toastConvertInfo(mIsConvertMms);
                }
            }
        });

        // M: fix bug ALPS00355897
        // PhoneNumberFormatter.setPhoneNumberFormattingTextWatcher(this, mRecipientsEditor);

        mTopPanel.setVisibility(View.VISIBLE);
        /// M: Code analyze 013, Get contacts from Contact app . @{
        if (mIsRecipientHasIntentNotHandle && (mIntent != null)) {
            processPickResult(mIntent);
            mIsRecipientHasIntentNotHandle = false;
            mIntent = null;
        }
        /// @}

        /// M: add for ip message
        mCurrentNumberString = "";
        // For op09, if the mms process was killed, restore the cc status
        if (MmsConfig.isSupportSendMmsWithCc()
                && bundle != null
                && bundle.get("ccrecipients") != null) {
            showRecipientsCcEditor(true);
        }
    }

    //==========================================================
    // Activity methods
    //==========================================================

    public static boolean cancelFailedToDeliverNotification(Intent intent, Context context) {
        if (MessagingNotification.isFailedToDeliver(intent)) {
            // Cancel any failed message notifications
            MessagingNotification.cancelNotification(context,
                        MessagingNotification.MESSAGE_FAILED_NOTIFICATION_ID);
            return true;
        }
        return false;
    }

    public static boolean cancelFailedDownloadNotification(Intent intent, Context context) {
        if (MessagingNotification.isFailedToDownload(intent)) {
            // Cancel any failed download notifications
            MessagingNotification.cancelNotification(context,
                        MessagingNotification.DOWNLOAD_FAILED_NOTIFICATION_ID);
            return true;
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	   /*HQ_sunli 20150916 HQ01359292 begin*/	
    	 harassmentIntercepterService= ServiceManager.getService(HARASSMENT_INTERCEPTION_SERVICE);
    	 hisStub= IHarassmentInterceptionService.Stub.asInterface(harassmentIntercepterService);
    	   /*HQ_sunli 20150916 HQ01359292 end*/   
		   
		 //HQ_zhangjing 2015-11-12 modified for CQ HQ01455134 remove the invalid recipient 
		 showInvalidRecipintsDlg = getResources().getBoolean(R.bool.show_invalid_recipients_dialog);

		   
        mIsSmsEnabled = MmsConfig.isSmsEnabled(this);
        super.onCreate(savedInstanceState);
        MmsLog.d(TAG, "onCreate() enter!!");
        /// M: Code analyze 001, Plugin opeartor. @{
        initPlugin(this);
        /// @}
        /// M: add for fix ALPS01317511 @{
        if (MmsConfig.isSupportMessagingNotificationProxy()) {
            Intent intent = getIntent();
            if (intent.getBooleanExtra("notification", false)) {
                int thread_count = intent.getIntExtra("thread_count", 1);
                int messageCount = intent.getIntExtra("message_count", 1);
                if (thread_count > 1 || messageCount > 1) {
                    mHomeBox = FolderViewList.OPTION_INBOX;
                    goToConversationList();
                }
            }
        }
        /// @}
        mForwardingMessage = false;

        /// M: If cell dialog is showed before, do not show it. @{
        if (savedInstanceState != null
                && savedInstanceState.getBoolean("cell_dialog_showing", false)) {
            MmsLog.d(TAG, "cell progress dialog is showed before, do not show it now");
            savedInstanceState.putBundle("android:savedDialogs", null);
        }
        /// @}

        /// M: add for ipmessage
        if (MmsConfig.isServiceEnabled(this)) {
            boolean isServiceReady = IpMessageUtils.getServiceManager(this).serviceIsReady();
            MmsLog.d(IPMSG_TAG, "onCreate(): is ip service ready ?= " + isServiceReady);
            if (!isServiceReady) {
                MmsLog.d(IPMSG_TAG, "Turn on ipmessage service by Composer.");
                IpMessageUtils.getServiceManager(this).startIpService();
            }
        }
        /** add for ipmessage. {@
         *  move ipmessage variable init block from onResume to onCreate
         *  updateIpMessageCounter will use mIsMessageDefaultSimIpServiceEnabled, it's value must init early.
         *  fix alps01017659
         */
        mMessageSubId = (int) Settings.System.getLong(getContentResolver(),
                Settings.System.SMS_SIM_SETTING, Settings.System.DEFAULT_SIM_NOT_SET);
        mIsIpServiceEnabled = MmsConfig.isServiceEnabled(this);
        MmsLog.d(TAG, "mIsIpServiceEnabled =" + mIsIpServiceEnabled);
        /// @}

        mInputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        /// M: Code analyze 002,  If a new ComposeMessageActivity is created, kill old one
        Activity compose = sCompose == null ? null : sCompose.get();
        /// M Fix CR : ALPS01275735, which has mms draft for conversation A, enter it.
        /// change recipients froam A to B, then come a B notification , click the notification,
        /// enter the thread A, it will show wrong composer UI. because conversation from cache is the same. @{
        if (compose != null && compose instanceof ComposeMessageActivity) {
            Intent in = this.getIntent();
            /// M : Fix issue ALPS01767850 @{
            if (in.getBooleanExtra("from_notification", false)) {
                boolean b = ((ComposeMessageActivity)compose).hasValidRecipient();
                if (b) {
                    mLastThreadIdFromNotification = ((ComposeMessageActivity)compose).mConversation.ensureThreadId();
                    sTextEditorText = ((ComposeMessageActivity)compose).mWorkingMessage.getText().toString();
                }
            }
            /// @}
            
            long thid = in.getLongExtra("thread_id_from_notification", -1);
            if (((ComposeMessageActivity) compose).mConversation != null) {
                if (thid == ((ComposeMessageActivity) compose).mConversation.getThreadId()) {
                    ((ComposeMessageActivity) compose).mConversation.clearThreadId();
                }
            }
        }
        /// @}
        if (!MmsConfig.isMultiComposeEnable() && compose != null && !compose.isFinishing() && savedInstanceState == null) {
            compose.finish();
        }
        sCompose = new WeakReference(this);
        /// @}
        /// M: Code analyze 003,  Set or get max mms size.
        initMessageSettings();
        /// @}
        resetConfiguration(getResources().getConfiguration());
        /// M: Code analyze 004, Set max height for text editor. @{
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (mIsLandscape) {
            mCurrentMaxHeight = windowManager.getDefaultDisplay().getWidth();
        } else {
            mCurrentMaxHeight = windowManager.getDefaultDisplay().getHeight();
        }
        MmsLog.d(TAG, "onCreate(): mCurrentMaxHeight = " + mCurrentMaxHeight);
        /// @}
        setContentView(R.layout.compose_message_activity);
        setProgressBarVisibility(false);

        /// M: Code analyze 005, Set input mode. @{
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        /// @}

        // Initialize members for UI elements.
        initResourceRefs();
        initShareRessource();
        /// M: add for ip message
        if (MmsConfig.isServiceEnabled(this)) {
            initIpMessageResourceRefs();
        }

        /// M: Code analyze 006, Control Sub indicator on status bar. @{
        mStatusBarManager = (StatusBarManager) getSystemService(Context.STATUS_BAR_SERVICE);
        mComponentName = getComponentName();
        /// @}

        /// M: Code analyze 007, Get information from Sub or save message to Sub. @{
        mSubCount = 0;
        /// @}

        mContentResolver = getContentResolver();
        mBackgroundQueryHandler = new BackgroundQueryHandler(mContentResolver);

        initialize(savedInstanceState, 0);

        if (TRACE) {
            android.os.Debug.startMethodTracing("compose");
        }
        /// M: Code analyze 008,unkown . @{
        mDestroy = false;

        /// @}
        /// M: Code analyze 009,Show attachment dialog . @{
        mSoloAlertDialog = new SoloAlertDialog(this);
        /// @}
        /// M: Code analyze 007, Get information from Sub or save message to Sub.(Get all Sub info) @{
        mGetSubInfoRunnable.run();
        /// M: for ALPS00475359, fix ANR when edit mms with deleting 1500 messages @{
        if (MmsConfig.isSupportAsyncUpdateWallpaper()) {
            mSetWallPaperHandler = new Handler() {

                @Override
                public void handleMessage(Message msg) {
                    switch(msg.what) {
                    case SET_WALLPAPER_FROM_RESOURCE:
                        mWallPaper.setImageResource(msg.arg1);
                        break;
                    case SET_WALLPAPER_FROM_BITMAP:
                        mWallPaper.setImageDrawable((BitmapDrawable) msg.obj);
                        break;
                    default:
                        break;
                    }
                }
            };
        }
        /// @} for ALPS00475359 end
        /// M:
        if (!FeatureOption.MTK_GMO_ROM_OPTIMIZE) {
            changeWallPaper();
        }
        /// M: add for update sub state dynamically. @{
        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        /// M: for OP09 Feature: dual send button @{
        if (MmsConfig.isDualSendButtonEnable()) {
            intentFilter.addAction(TelephonyIntents.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_SUBINFO_CONTENT_CHANGE);
        }
        /// @}
        this.registerReceiver(mSubReceiver, intentFilter);
        /// @}

        mStatusBarSelectorReceiver = new StatusBarSelectorReceiver(this);
        IntentFilter statusBarSelectorIntentFilter = new IntentFilter(StatusBarSelectorReceiver.ACTION_MMS_ACCOUNT_CHANGED);
        registerReceiver(mStatusBarSelectorReceiver, statusBarSelectorIntentFilter);
    }

    private void showSubjectEditor(boolean show) {
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("showSubjectEditor: " + show);
        }

        if (mSubjectTextEditor == null) {
            // Don't bother to initialize the subject editor if
            // we're just going to hide it.
            if (show == false) {
                return;
            }
            mSubjectTextEditor = (EditText)findViewById(R.id.subject);
            /// M: Code analyze 068, Unknown. Why delete these code? @{
            /// mSubjectTextEditor.setFilters(new InputFilter[] {
            ///     new LengthFilter(MmsConfig.getMaxSubjectLength())});
            /// @}
            /// M: Code analyze 001, Plugin opeartor. @{
            mMmsComposePlugin.configSubjectEditor(mSubjectTextEditor);
            /// @}
       }

        mSubjectTextEditor.setOnKeyListener(show ? mSubjectKeyListener : null);

        mSubjectTextEditor.removeTextChangedListener(mSubjectEditorWatcher);
        if (show) {
            mSubjectTextEditor.addTextChangedListener(mSubjectEditorWatcher);
        }

        if ((mBottomPanel != null) && (mBottomPanel.getVisibility() == View.VISIBLE)) {
            if (!show) {
                mTextEditor.requestFocus();
            }
            //mSubjectTextEditor.setNextFocusDownId(R.id.embedded_text_editor);
            mSubjectTextEditor.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            mSubjectTextEditor.setImeActionLabel(getText(com.android.internal.R.string.ime_action_next), EditorInfo.IME_ACTION_NEXT);
        } else {
            //mSubjectTextEditor.setNextFocusDownId(-1);
            mSubjectTextEditor.setImeOptions(EditorInfo.IME_ACTION_DONE);
            mSubjectTextEditor.setImeActionLabel(getText(com.android.internal.R.string.ime_action_done), EditorInfo.IME_ACTION_DONE);
        }
        CharSequence subjectText = mWorkingMessage.getSubject();
        if (subjectText != null && show) {
            mSubjectTextEditor.setTextKeepState(subjectText);
            try {
                mSubjectTextEditor.setSelection(mSubjectTextEditor.getText().toString().length());
            } catch (IndexOutOfBoundsException e) {
                mSubjectTextEditor.setSelection(mSubjectTextEditor.getText().toString().length() - 1);
            }
        } else {
            mSubjectTextEditor.setText("");
        }

        mSubjectTextEditor.setVisibility(show ? View.VISIBLE : View.GONE);
        mSubjectTextEditor.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                showSharePanel(false);
                return false;
            }
        });
        mSubjectTextEditor.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus && isSharePanelShow()) {
                    showSharePanel(false);
                }
            }
        });
        mSubjectTextEditor.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_NEXT) {
                    if ((mBottomPanel != null) && (mBottomPanel.getVisibility() == View.VISIBLE)) {
                        mTextEditor.requestFocus();
                        return true;
                    }
                }
                return false;
            }
        });
		/*HQ_zhangjing add for al812 mms ui begin*/
        mSubjectTextEditor.setFilters(new InputFilter[] {
                new TextLengthFilter(MmsConfig.getMaxSubjectLength())});
		/*HQ_zhangjing add for al812 mms ui end*/
		
        hideOrShowTopPanel();
        mMmsComposePlugin.hideOrShowSubjectForCC(isRecipientsCcEditorVisible()
                && (mSubjectTextEditor != null) && isSharePanelShow()
                && mIsLandscape, mSubjectTextEditor);
    }

    private void hideOrShowTopPanel() {
        boolean anySubViewsVisible = (isSubjectEditorVisible() || isRecipientsEditorVisible() || isRecipientsCcEditorVisible());
        mTopPanel.setVisibility(anySubViewsVisible ? View.VISIBLE : View.GONE);
    }

    /// M: Fix ipmessage bug for ALPS 01566645 @{
    public void updateCurrentChatMode(Intent intent) {

            /// M: add for ip message, check recipients is ipmessage User
            mIsIpMessageRecipients = isCurrentRecipientIpMessageUser();
            /// M: modify for ipmessage because the cotnact status is useless and incorrect now @{
            boolean status = isCurrentIpmessageSendable();
            MmsLog.d(TAG, "mIsIpMessageRecipients = " + mIsIpMessageRecipients + "status = " + status);
            /// @}
            if (IpMessageUtils.getServiceManager(ComposeMessageActivity.this)
                    .getIntegrationMode() == IpMessageConsts.IntegrationMode.ISMS_MODE) {
            if (mIsIpMessageRecipients && status) {
                    mCurrentChatMode = IpMessageConsts.ChatMode.JOYN;
                } else {
                    mCurrentChatMode = IpMessageConsts.ChatMode.XMS;
                }
                return;
            }

            if (intent != null && intent.hasExtra("chatmode")) {
                mCurrentChatMode = intent.getIntExtra("chatmode", IpMessageConsts.ChatMode.XMS);
            } else {
                if (IpMessageUtils.getServiceManager(ComposeMessageActivity.this).getIntegrationMode() == IpMessageConsts.IntegrationMode.FULLY_INTEGRATED) {
                    MmsLog.d(TAG, "is full integrated mode");
                    /// M: modify for ipmessage because the cotnact status is useless and incorrect now
                    // if (mIsIpMessageRecipients && status != ContactStatus.OFFLINE) {
                    if (mIsIpMessageRecipients && status) {
                        mCurrentChatMode = IpMessageConsts.ChatMode.JOYN;
                    } else {
                        mCurrentChatMode = IpMessageConsts.ChatMode.XMS;
                    }
                } else {
                    if (null != mConversation.getRecipients() && mConversation.getRecipients().size() >= 1) {
                        if (mConversation.getRecipients().get(0).getNumber().startsWith(IpMessageConsts.JOYN_START)) {
                            mCurrentChatMode = IpMessageConsts.ChatMode.JOYN;
                        } else {
                            mCurrentChatMode = IpMessageConsts.ChatMode.XMS;
                        }
                    }
                }
            }

        MmsLog.d(TAG, "updateCurrentChatMode mCurrentChatMode = " + mCurrentChatMode);
    }
    /// @}

    /// M : Fix issue ALPS01767850 @{
    /// If there a mms draft with thread id 1, and create a new sms message with threadid 1
    /// Then send a threadid 1 message, and click the notification to enter the 1 thread
    /// The sms message will disapre which just edit in the editor box
    private boolean needLoadDraftWhileFromNotification(Intent intent) {
        if (intent == null) {
            return true;
        }
        // check from notification or not
        boolean isFromNotification = intent.getBooleanExtra("from_notification", false);
        
        if (!isFromNotification) {
            Log.d(TAG, "[needLoadDraftWhileFromNotification] NOT from notification, need load draft");
            return true;
        }
        
        // check exist text or not
        if (sTextEditorText == null || sTextEditorText.trim().length() == 0) {
            Log.d(TAG, "[needLoadDraftWhileFromNotification] NO EXIST text in the editor, need load draft");
            return true;
        }
        // check the thread id from notification and the thread id from last compose is the same
        boolean isSameThread = false;
        
        long threadIdFromNotification = intent.getLongExtra("thread_id_from_notification", -1);
        
        if (mLastThreadIdFromNotification == -1 || threadIdFromNotification == -1) {
            isSameThread = false;
        } else if (mLastThreadIdFromNotification == threadIdFromNotification) {
            isSameThread = true;
        }
        
        Log.d(TAG, "[needLoadDraftWhileFromNotification] mLastThreadIdFromNotification : " + mLastThreadIdFromNotification
                + ", threadIdFromNotification : " + threadIdFromNotification);
        
        if (!isSameThread) {
            Log.d(TAG, "[needLoadDraftWhileFromNotification] NOT the same thread, need load draft");
            return true;
        }
        
        mLastThreadIdFromNotification = -1;
        Log.d(TAG, "[needLoadDraftWhileFromNotification] return false");
        
        return false;
    }
    ///@}

    public void initialize(Bundle savedInstanceState, long originalThreadId) {
        /// M: Code analyze 010, Support dirtory mode. @{
        Intent intent = getIntent();
        boolean showInput = false;
        boolean hiderecipient = false;
        boolean isMustRecipientEditable = false;
        if (MmsConfig.getMmsDirMode()) {
            mHomeBox = intent.getIntExtra("folderbox", 0);
            showInput = intent.getBooleanExtra("showinput", false);
            hiderecipient = intent.getBooleanExtra("hiderecipient", false);
            isMustRecipientEditable = true;
        }
        /// @}

        // Create a new empty working message.
        mWorkingMessage = WorkingMessage.createEmpty(this);

        // Read parameters or previously saved state of this activity. This will load a new
        // mConversation
        initActivityState(savedInstanceState);

        /// M: Fix ipmessage bug @{
        updateCurrentChatMode(intent) ;
        /// @}

        if (LogTag.SEVERE_WARNING && originalThreadId != 0 &&
                originalThreadId == mConversation.getThreadId()) {
            LogTag.warnPossibleRecipientMismatch("ComposeMessageActivity.initialize: " +
                    " threadId didn't change from: " + originalThreadId, this);
        }

        if (Build.TYPE.equals("eng")) {
            log("savedInstanceState = " + savedInstanceState +
                ", intent = " + intent +
                ", originalThreadId = " + originalThreadId +
                ", mConversation = " + mConversation);
        }

        /// M: Code analyze 010, Support dirtory mode. @{
        if (!MmsConfig.getMmsDirMode()) {
            if (cancelFailedToDeliverNotification(getIntent(), this) && savedInstanceState == null) {
                // Show a pop-up dialog to inform user the message was
                // failed to deliver.
                undeliveredMessageDialog(getMessageDate(null));
            }
            cancelFailedDownloadNotification(getIntent(), this);
        }
        ///  @}
        // Set up the message history ListAdapter
        initMessageList();
        /// M: fix bug for ConversationList select all performance ,update selected threads array.@{
        mSelectedThreadId = mConversation.getThreadId();
        /// @}
        // Load the draft for this thread, if we aren't already handling
        // existing data, such as a shared picture or forwarded message.
        boolean isForwardedMessage = false;
        // We don't attempt to handle the Intent.ACTION_SEND when saveInstanceState is non-null.
        // saveInstanceState is non-null when this activity is killed. In that case, we already
        // handled the attachment or the send, so we don't try and parse the intent again.
        boolean intentHandled = savedInstanceState == null &&
        /// M: unknown @{
            (handleSendIntent() || (handleForwardedMessage() && !mConversation.hasDraft()));
        /// @}
        
        /// M : Fix issue ALPS01767850
        boolean need = needLoadDraftWhileFromNotification(intent);
        Log.d(TAG, "[initialize] need : " + need);
        if (!intentHandled && need) {
            MmsLog.d(TAG, "Composer init load Draft.");
            loadDraft();
        } else if (!need) {
            mTextEditor.setText(sTextEditorText);
        }
        
        sTextEditorText = null;
        
        // Let the working message know what conversation it belongs to
        mWorkingMessage.setConversation(mConversation);

        // Show the recipients editor if we don't have a valid thread. Hide it otherwise.
        /// M: @{
        //  if (mConversation.getThreadId() <= 0) {
        if (mConversation.getThreadId() <= 0L
            || (mConversation.getMessageCount() <= 0 && (intent.getAction() != null || mConversation.hasDraft()))
            || (mConversation.getThreadId() > 0L && mConversation.getMessageCount() <= 0)
            || isMustRecipientEditable) {
         /// @}
            // Hide the recipients editor so the call to initRecipientsEditor won't get
            // short-circuited.
            hideRecipientEditor();
            initRecipientsEditor(savedInstanceState);
            /// M: Code analyze 046, Whether the recipientedit control has been initialized. @{
            isInitRecipientsEditor = true;
            /// @}

            // Bring up the softkeyboard so the user can immediately enter recipients. This
            // call won't do anything on devices with a hard keyboard.
            if (mIsSmsEnabled) {
                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            }
        } else {
            hideRecipientEditor();
        }
        /// M: Code analyze 010, Support dirtory mode. @{
        if (MmsConfig.getMmsDirMode()) {
            if (showInput) {
                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            } else {
                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
            }
            if (hiderecipient) {
                if (isRecipientsEditorVisible()) {
                    hideRecipientEditor();
                }
            }
        }
        /// M: @{

        invalidateOptionsMenu();    // do after show/hide of recipients editor because the options
                                    // menu depends on the recipients, which depending upon the
                                    // visibility of the recipients editor, returns a different
                                    // value (see getRecipients()).

        updateSendButtonState();

        drawTopPanel(false);
        if (intentHandled) {
            // We're not loading a draft, so we can draw the bottom panel immediately.
            drawBottomPanel();
        }

        onKeyboardStateChanged();

        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("update title, mConversation=" + mConversation.toString());
        }

        // no longer update title in onResume, so set "New Message" here.
        updateTitle(new ContactList());

        if (isForwardedMessage && isRecipientsEditorVisible()) {
            // The user is forwarding the message to someone. Put the focus on the
            // recipient editor rather than in the message editor.
            mRecipientsEditor.requestFocus();
        }

        /// M: google JB.MR1 patch, group mms
        boolean isGroupMms = MmsPreferenceActivity.getIsGroupMmsEnabled(ComposeMessageActivity.this)
                                                && mConversation.getRecipients().size() > 1;
        mMsgListAdapter.setIsGroupConversation(isGroupMms);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        MmsLog.d(TAG, "onNewIntent: intent = " + intent.toString());
      /// M: add for fix ALPS01317511 @{
        if (!handleNotificationIntent(intent)) {
            processNewIntent(intent);
        }
        /// @}
    }

   private void processNewIntent(Intent intent) {
        setIntent(intent);
        /// M: reload working message if it is not correct.
        ///M: for fix alps01237242. when forward message for cmcc, don't reload working message @{
        if (!(MmsConfig.isNeedExitComposerAfterForward() == false && intent.getBooleanExtra(FORWARD_MESSAGE, false) == true)) {
            reloadWorkingMessage();
        }
        ///M @}
        mWaitingAttachment = false;

        if (mQuickTextDialog != null && mQuickTextDialog.isShowing()) {
            mQuickTextDialog.dismiss();
            mQuickTextDialog = null;
        }

        if (mSubSelectDialog != null && mSubSelectDialog.isShowing()) {
            mSubSelectDialog.dismiss();
            mSubSelectDialog = null;
        }

        Conversation conversation = null;
        mSentMessage = false;

        // If we have been passed a thread_id, use that to find our
        // conversation.

        // Note that originalThreadId might be zero but if this is a draft and we save the
        // draft, ensureThreadId gets called async from WorkingMessage.asyncUpdateDraftSmsMessage
        // the thread will get a threadId behind the UI thread's back.
        long originalThreadId = mConversation.getThreadId();
        long threadId = intent.getLongExtra("thread_id", 0);
        Uri intentUri = intent.getData();

        boolean sameThread = false;
        if (threadId > 0) {
            conversation = Conversation.get(getApplicationContext(), threadId, false);
        } else {
            if (mConversation.getThreadId() == 0) {
                // We've got a draft. Make sure the working recipients are synched
                // to the conversation so when we compare conversations later in this function,
                // the compare will work.
                mWorkingMessage.syncWorkingRecipients();
            }
            // Get the "real" conversation based on the intentUri. The intentUri might specify
            // the conversation by a phone number or by a thread id. We'll typically get a threadId
            // based uri when the user pulls down a notification while in ComposeMessageActivity and
            // we end up here in onNewIntent. mConversation can have a threadId of zero when we're
            // working on a draft. When a new message comes in for that same recipient, a
            // conversation will get created behind CMA's back when the message is inserted into
            // the database and the corresponding entry made in the threads table. The code should
            // use the real conversation as soon as it can rather than finding out the threadId
            // when sending with "ensureThreadId".
            conversation = Conversation.get(getApplicationContext(), intentUri, false);
        }

        /// M: Fix bug: ALPS00444760, The keyboard display under the MMS after
        // you tap shortcut enter this thread.
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        /// @}
        ///M: for fix alps01237242. when forward message for cmcc, reset configuration @{
        if (MmsConfig.isNeedExitComposerAfterForward() == false && intent.getBooleanExtra(FORWARD_MESSAGE, false) == true) {
            resetConfiguration(getResources().getConfiguration());
        }
        ///M @]
        if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("onNewIntent: data=" + intentUri + ", thread_id extra is " + threadId +
                    ", new conversation=" + conversation + ", mConversation=" + mConversation);
        }

        // this is probably paranoid to compare both thread_ids and recipient lists,
        // but we want to make double sure because this is a last minute fix for Froyo
        // and the previous code checked thread ids only.
        // (we cannot just compare thread ids because there is a case where mConversation
        // has a stale/obsolete thread id (=1) that could collide against the new thread_id(=1),
        // even though the recipient lists are different)
        sameThread = ((conversation.getThreadId() == mConversation.getThreadId() ||
                mConversation.getThreadId() == 0) &&
                conversation.equals(mConversation));

        if (sameThread) {
            log("onNewIntent: same conversation");
            /// M Fix CR:ALPS01081972 which cannot show received message in the composer @{
            if (intent.getBooleanExtra("finish", false) == true) {
                Activity compose = sCompose == null ? null : sCompose.get();
                if (compose != null && !compose.isFinishing()) {
                    Log.d("[Mms][ComposeMessageActivity]", "[onNewIntent] same conversation call finish composer");
                    compose.finish();
                    this.startActivity(intent);
                    return;
                }
            }
            /// @}
            if (mConversation.getThreadId() == 0) {
                mConversation = conversation;
                mWorkingMessage.setConversation(mConversation);
                updateThreadIdIfRunning();
                invalidateOptionsMenu();
            }
        } else {
            if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                log("onNewIntent: different conversation");
            }
            /// M: @{
            /// M: Don't let any markAsRead DB updates occur before we've loaded the messages for
            /// M: the thread.
            conversation.blockMarkAsRead(true);
            /// @}
            if ((!isRecipientsEditorVisible())
                    || (mRecipientsEditor.hasValidRecipient(mWorkingMessage.requiresMms()))) {
                    //For ALPS00457252
                    if (!mWorkingMessage.isWorthSaving()) {
                        mWorkingMessage.discard();
                    } else {
                        saveDraft(false); // if we've got a draft, save it first
                    }
            }
            /// if is not the same thread, to cancel progress dialog @{
            mUiHandler.removeCallbacks(this.mContactPickRunnable);
            if (mContactPickDialog != null && mContactPickDialog.isShowing()) {
                mContactPickDialog.dismiss();
            }
            mContactPickDialog = null;
            /// @}
            if (mAsyncDialog != null) {
                mAsyncDialog.dismissProgressDialog();
            }
            /// if from shortcut, finish origin compoer, and start again @{
            if (intent.getBooleanExtra("finish", false) == true) {
                Activity compose = sCompose == null ? null : sCompose.get();
                if (compose != null && !compose.isFinishing()) {
                    Log.d("[Mms][ComposeMessageActivity]", "[onNewIntent] call finish composer");
                    compose.finish();
                    this.startActivity(intent);
                    return;
                }
            }
            /// @}
            /// M: @{
            mMsgListAdapter.changeCursor(null);
            mConversation = conversation;
            /// @}
            /// M: add for ip message
            mCurrentNumberString = "";
            initialize(null, originalThreadId);
            /// M: add for attach dialog do not dismiss when enter other thread. @{
            if (!mSoloAlertDialog.needShow()) {
                mSoloAlertDialog.dismiss();
            }
            /// @}
            /// M: fix bug ALPS00941735
            mIsSameConv = false;
            MmsLog.d(TAG, "onNewIntent not same thread");
        }
        loadMessageContent();
        mSendSubIdForDualBtn = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        MmsLog.d(TAG, "onNewIntent get subId from intent = " + mSendSubIdForDualBtn);
        /// M:
        if (!isRecipientsEditorVisible() && !FeatureOption.MTK_GMO_ROM_OPTIMIZE) {
            changeWallPaper();
        }
        if (mMsgListAdapter != null && mMsgListAdapter.getCount() == 0) {
            showKeyBoard(true);
            if (mRecipientsEditor != null) {
                mRecipientsEditor.requestFocus();
            }
        }
   }

    private void sanityCheckConversation() {
        if (mWorkingMessage.getConversation() != mConversation) {
            LogTag.warnPossibleRecipientMismatch(
                    "ComposeMessageActivity: mWorkingMessage.mConversation=" +
                    mWorkingMessage.getConversation() + ", mConversation=" +
                    mConversation + ", MISMATCH!", this);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();

         /// M: For fix bug ALPS00542156 The "_" still display under the SubA after you change it to the "Always ask" or "SubB.@{
        sCompose = null;
        sCompose = new WeakReference(this);
        /// @}

        if (mWorkingMessage.isDiscarded()) {
            // If the message isn't worth saving, don't resurrect it. Doing so can lead to
            // a situation where a new incoming message gets the old thread id of the discarded
            // draft. This activity can end up displaying the recipients of the old message with
            // the contents of the new message. Recognize that dangerous situation and bail out
            // to the ConversationList where the user can enter this in a clean manner.
            mWorkingMessage.unDiscard();    // it was discarded in onStop().
            if (mWorkingMessage.isWorthSaving()) {
                if (LogTag.VERBOSE) {
                    log("onRestart: mWorkingMessage.unDiscard()");
                }
                //mWorkingMessage.unDiscard();    // it was discarded in onStop().

                sanityCheckConversation();
            } else if (isRecipientsEditorVisible()) {
                if (LogTag.VERBOSE) {
                    log("onRestart: goToConversationList");
                }
                goToConversationList();
            } else {
                if (LogTag.VERBOSE) {
                    log("onRestart: loadDraft");
                }
                /// M: @{
                //loadDraft();
                /// @}
                mWorkingMessage.setConversation(mConversation);
                mAttachmentEditor.update(mWorkingMessage);
                updateTextEditorHeightInFullScreen();
                invalidateOptionsMenu();
            }
        }
        /// M: For fix bug ALPS00537320 The chat wallpaper will change after you tap "OK".@{
        if (!isRecipientsEditorVisible() && !FeatureOption.MTK_GMO_ROM_OPTIMIZE) {
            changeWallPaper();
        }
        /// @}
    }

    @Override
    protected void onStart() {
        super.onStart();
        MmsLog.d(TAG, "onStart() enter!!");
        boolean isSmsEnabled = MmsConfig.isSmsEnabled(this);
        /*HQ_sunli 20151113 HQ01499362 begin*/
        mPowerMode=SystemProperties.get("sys.super_power_save","false"); 
        /*HQ_sunli 20151113 HQ01499362 end*/
        if (isSmsEnabled != mIsSmsEnabled) {
            mIsSmsEnabled = isSmsEnabled;
            invalidateOptionsMenu();
        }
        /// M: For fix bug ALPS00688631 The "_" still display under the SubA after you change it to the "Always ask" or "SubB.@{
        sCompose = null;
        sCompose = new WeakReference(this);
        /// @}
        /// M Fix CR : ALPS01257113, when sub select dialog showed in default sms. after change to
        /// non-default sms, dismiss the sub select dialog @{
        if (!mIsSmsEnabled) {
            if (mSubSelectDialog != null && mSubSelectDialog.isShowing()) {
                mSubSelectDialog.dismiss();
            }
        }
        /// @}

        /// M: Code analyze 036, Change text size if adjust font size.@{
        float textSize = MessageUtils.getPreferenceValueFloat(this, SettingListActivity.TEXT_SIZE, 14);//HQ_zhangjing modified for default text size
        setTextSize(textSize);
        if (mMmsTextSizeAdjustPlugin != null) {
            mMmsTextSizeAdjustPlugin.init(this, this);
            mMmsTextSizeAdjustPlugin.refresh();
        }
        /// @}

        /// M: Code analyze 013, Get contacts from Contact app . @{
        misPickContatct = false;
        /// @}
        // M:for ALPS01065027,just for compose messagelist in scrolling
        mMsgListView.setOnScrollListener(mScrollListener);
        mScrollListener.setIsNeedRefresh(true);
        mScrollListener.setThreadId(mConversation.getThreadId(), ComposeMessageActivity.this);
        initFocus();

        // Register a BroadcastReceiver to listen on HTTP I/O process.
        if (!mHasRegisteredHttpProgressReceiver) {
            mHasRegisteredHttpProgressReceiver = true;
            registerReceiver(mHttpProgressReceiver, mHttpProgressFilter);
        } else {
            MmsLog.e(TAG, "onStart: mHttpProgressReceiver has registered, onStart enter two times, but onStop has not been called.");
        }
        /// M: add for OP09
		/*HQ_zhangjing 2015-11-02 modified for CQ HQ01478873 */
        //mMmsComposePlugin.setIsShowDualButtonPanel(false);
        loadMessageContent();

        // Update the fasttrack info in case any of the recipients' contact info changed
        // while we were paused. This can happen, for example, if a user changes or adds
        // an avatar associated with a contact.
        /// M: @{
        if (mConversation.getThreadId() == 0) {
            mWorkingMessage.syncWorkingRecipients();
        }
        /// @}

        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("onStart: update title, mConversation=" + mConversation.toString());
        }

        ActionBar actionBar = getActionBar();
        /// M: Add log. @{
		/*HQ_zhangjing add for al812 mms ui begin*/
		/*
        if (actionBar == null) {
            MmsLog.e(TAG, "ACTION BAR is null, window feature FEATURE_ACTION_BAR: "
                    + getWindow().hasFeature(Window.FEATURE_ACTION_BAR)
                    + ", feature FEATURE_NO_TITLE: "
                    + getWindow().hasFeature(Window.FEATURE_NO_TITLE));
        } else {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
		*/
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setHomeButtonEnabled(false);
		/*HQ_zhangjing add for al812 mms ui begin*/
        /// @}

        /// M: Code analyze 047, Extra uri from message body and get number from uri.
        /// Then use this number to update contact cache. @{
        mNeedUpdateContactForMessageContent = true;
        /// @}
        /// M: add for mark message as read
        mIsMarkMsgAsRead = true;
        
        /// M: Code analyze 001, Plugin opeartor. @{
        IMmsDialogNotifyExt dialogPlugin =
                (IMmsDialogNotifyExt) MmsPluginManager.getMmsPluginObject(
                            MmsPluginManager.MMS_PLUGIN_TYPE_DIALOG_NOTIFY);
        dialogPlugin.closeMsgDialog();
        /// @}

        if (isRecipientsEditorVisible() && mIsSmsEnabled &&
                getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        ///M: WFC: Show notification ticker @ {
        if (SystemProperties.get("ro.mtk_wfc_support").equals("1")) {
            MessagingNotification.showWfcNotification(getApplicationContext());
        }
        /// @}
    }

    public void loadMessageContent() {
        // Don't let any markAsRead DB updates occur before we've loaded the messages for
        // the thread. Unblocking occurs when we're done querying for the conversation
        // items.
        MmsLog.d(TAG, "loadMessageContent()");
        ///M: for fix ALPS01026162, for folder mode, no content loaded.must not mark as read. @{
        if (!MmsConfig.getMmsDirMode()) {
            mConversation.blockMarkAsRead(true);
            mConversation.markAsRead();         // dismiss any notifications for this convo
            /// M: mark conversation as seen, update new messages notification.
            /// M: fix bug ALPS01065220. onStart() will update message seen = 1. But if before update start,
            /// do not update it.{@
            mConversation.setComposeIsPaused(false);
            /// @}
            mConversation.markAsSeen();
        }
        /// @}
        startMsgListQuery(MESSAGE_LIST_QUERY_TOKEN, 0);
        updateSendFailedNotification();
        updateDownloadFailedNotification();
        drawBottomPanel();
    }

    private void updateSendFailedNotification() {
        final long threadId = mConversation.getThreadId();
        if (threadId <= 0)
            return;

        // updateSendFailedNotificationForThread makes a database call, so do the work off
        // of the ui thread.
        new Thread(new Runnable() {
            @Override
            public void run() {
                MessagingNotification.updateSendFailedNotificationForThread(
                        ComposeMessageActivity.this, threadId);
            }
        }, "ComposeMessageActivity.updateSendFailedNotification").start();
    }

    private void updateDownloadFailedNotification() {
        final long threadId = mConversation.getThreadId();
        if (threadId <= 0)
            return;

        // updateSendFailedNotificationForThread makes a database call, so do the work off
        // of the ui thread.
        new Thread(new Runnable() {
            @Override
            public void run() {
                MessagingNotification.updateDownloadFailedNotificationForThread(
                        ComposeMessageActivity.this, threadId);
            }
        }, "ComposeMessageActivity.updateSendFailedNotificationForThread").start();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        /// M: Code analyze 033, Save some useful information in order to restore the draft when
        /// activity restarting.@{
        // super is commented for a bug work around see MessageUtils.showErrorDialog()
        super.onSaveInstanceState(outState); //why delete this line?

        // For op09, if the mms process was killed, restore the cc status
        if (MmsConfig.isSupportSendMmsWithCc()
                && mRecipientsCcEditor != null
                && isRecipientsCcEditorVisible()) {
            if (mRecipientsCcEditor.getRecipientCount() > 0) {
                ArrayList<String> numbers = (ArrayList<String>) (mRecipientsCcEditor.getNumbers());
                outState.putString("ccrecipients", TextUtils.join(";", numbers.toArray()));
            }
        }
        // save recipients of this coversation
        if (mRecipientsEditor != null && isRecipientsEditorVisible()) {
            // TODO need re-coding for below code
            //outState.putString("recipients", mRecipientsEditor.allNumberToString());
            /// M: We are compressing the image, so save the thread id in order to restore the
            /// M: draft when activity restarting.
            if (mCompressingImage) {
                outState.putLong("thread", mConversation.ensureThreadId());
            } else if (mRecipientsEditor.getRecipientCount() < 1) {
                outState.putLong("thread", mConversation.ensureThreadId());
                if ((mBackgroundQueryHandler != null) && (mConversation.getThreadId() > 0)) {
                    Conversation.asyncDeleteObsoleteThreadID(mBackgroundQueryHandler, mConversation.getThreadId());
                }
            } else if (mRecipientsEditor.getRecipientCount() > 0) {
                ArrayList<String> numbers = (ArrayList<String>) (mRecipientsEditor.getNumbers());
                outState.putString("recipients", TextUtils.join(";", numbers.toArray()));
            }
        } else {
            /// M: save the current thread id
            outState.putLong("thread", mConversation.getThreadId());
            MmsLog.i(TAG, "saved thread id:" + mConversation.getThreadId());
        }
        outState.putBoolean("compressing_image", mCompressingImage);
        /// @}
        mWorkingMessage.writeStateToBundle(outState);

        if (mExitOnSent) {
            outState.putBoolean(KEY_EXIT_ON_SENT, mExitOnSent);
        }
        if (mForwardMessageMode) {
            outState.putBoolean(KEY_FORWARDED_MESSAGE, mForwardMessageMode);
        }
        if (!mAppendAttachmentSign) {
            MmsLog.d(TAG, "onSaveInstanceState mAppendAttachmentSign : " + mAppendAttachmentSign);
            outState.putBoolean(KEY_APPEND_MESSAGE, mAppendAttachmentSign);
        }
        /// M: save ipmessage draft if needed.
        outState.putBoolean("saved_ipmessage_draft", mIpMessageDraft != null);
        /// M: save cell progress dialog is showing or not
        outState.putBoolean("cell_dialog_showing", mIsCellDialogShowing);
        saveIpMessageDraft();
    }

    /// M: fix bug ALPS01845996 when activity is visible should show phone account selector.
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        MmsLog.d(TAG, "onWindowFocusChanged hasFocus = " + hasFocus);
        if (!mIsActivityPaused && hasFocus && (UserHandle.myUserId() == UserHandle.USER_OWNER)) {
            MmsLog.d(TAG, "onWindowFocusChanged show status bar sub indicator");
            mIsShowSubIndicator = true;
            StatusBarSelectorCreator.getInstance(ComposeMessageActivity.this).showStatusBar();
        }
        if (hasFocus) {
            MessageListItem.sClickCanResponse = true;
        }
    }

    @Override
    protected void onResume() {
		/*HQ_zhangjing add for al812 mms ui begin*/
		if( (mMsgListAdapter != null && mMsgListAdapter.getCount() > 0) 
			|| (mConversation != null && mConversation.getMessageCount() > 0)){
			mTimeView.setVisibility(View.VISIBLE);
		}else{
			mTimeView.setVisibility(View.GONE);
		}
		/*HQ_zhangjing add for al812 mms ui end*/
	
        /// M: fix bug ALPS00444752, set false to enable to Show ContactPicker
        mShowingContactPicker = false;
        super.onResume();
        mIsActivityPaused = false;
        mNeedSaveDraftAfterStop = false;
        boolean isSmsEnabled = MmsConfig.isSmsEnabled(this);
        if (isSmsEnabled != mIsSmsEnabled) {
            mIsSmsEnabled = isSmsEnabled;
            invalidateOptionsMenu();
        }
        initFocus();
        getAsyncDialog().resetShowProgressDialog();
         /// M: Code analyze 005, Set input mode. @{
        Configuration config = getResources().getConfiguration();
        MmsLog.d(TAG, "onResume - config.orientation = " + config.orientation);
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            MmsLog.e(TAG, "onResume Set setSoftInputMode to 0x" +
                    Integer.toHexString(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN));
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        /// @}

        /// M: Code analyze 006, Control Sub indicator on status bar. @{
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        MmsLog.d(TAG, "onResume isKeyguardLocked = " + keyguardManager.isKeyguardLocked());
        if (!keyguardManager.isKeyguardLocked() && (UserHandle.myUserId() == UserHandle.USER_OWNER)) {
            mIsShowSubIndicator = true;
            StatusBarSelectorCreator.getInstance(ComposeMessageActivity.this).showStatusBar();
        }
        /// @}

        /// M: Code analyze 024, If the click operator can be responsed. @{
        //  button can response to start other activity
        mClickCanResponse = true;
        /// @}
        /// M: Code analyze 038, If the user is editing slideshow now.
        /// Do not allow the activity finish but return directly when back key is entered. @{
        mIsEditingSlideshow = false;
        /// @}
        /// M: add for OP09
		/*HQ_zhangjing 2015-11-02 modified for CQ HQ01478873 */
        //mMmsComposePlugin.setIsShowDualButtonPanel(false);
        if (mDrawBottomPanel) {
            drawBottomPanel();
        }
        // OLD: get notified of presence updates to update the titlebar.
        // NEW: we are using ContactHeaderWidget which displays presence, but updating presence
        //      there is out of our control.
        //Contact.startPresenceObserver();

        addRecipientsListeners();

        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("onResume: update title, mConversation=" + mConversation.toString());
        }

        // There seems to be a bug in the framework such that setting the title
        // here gets overwritten to the original title.  Do this delayed as a
        // workaround.
        if (isRecipientsEditorVisible()) {
            asyncUpdateThreadMuteIcon();
        } else {
            new Thread(new Runnable() {
                public void run() {
                    /// M: Fix CR ALPS00558119
                    /// When the Contact phone is long enough ,after add audio,
                    /// The title will show only phony number @{
                    ///@M:fix bug ALPS00871320 tablet has wide space to show more contact
                    int limite = getLimitedContact();
                    ///@
                    final ContactList recipients = getRecipients();
                    int min = Math.min(limite, recipients.size());
                    MmsLog.d(TAG, "onResume reload recipients size = " + recipients.size());
                    for (int i = 0; i < min; i++) {
                        recipients.get(i).reload();
                    }
                    mMessageListItemHandler.postDelayed(new Runnable() {
                        public void run() {
                            updateTitle(recipients);
                        }
                    }, 10);
                }
            }).start();
        }

        mIsRunning = true;
        updateThreadIdIfRunning();

        /// M:
        if (mIpMessageDraft != null) {
            MmsLog.d(TAG, "show IpMsg saveIpMessageForAWhile");
            saveIpMessageForAWhile(mIpMessageDraft);
            mSendButtonCanResponse = true;
        }
        /// M: add for ip message, notification listener
        IpMessageUtils.addIpMsgNotificationListeners(this, this);
        mIsIpServiceEnabled = MmsConfig.isServiceEnabled(this);

        /// M: add for ip message, Joyn chat under converaged inbox will set to
        // enable
        if (IpMessageUtils.getServiceManager(this).getIntegrationMode() == IpMessageConsts.IntegrationMode.CONVERGED_INBOX
                && mCurrentChatMode == IpMessageConsts.ChatMode.JOYN) {
            mIsIpServiceEnabled = true;
        }
        /// M:

        MmsLog.d(IPMSG_TAG, "onResume(): IpServiceEnabled = " + mIsIpServiceEnabled);
        if (mIsIpServiceEnabled && isNetworkConnected(getApplicationContext())) {
            if (mConversation.getThreadId() > 0 && mConversation.getRecipients() != null
                    && mConversation.getRecipients().size() == 1) {
                mChatModeNumber = mConversation.getRecipients().get(0).getNumber();
                /// M: Fix ipmessage bug for ALPS01674002 @{
                MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG,
                        "onResume(): enter Chat Mode. number = "
                                + mChatModeNumber);
                IpMessageUtils.getChatManager(this).enterChatMode(
                        mChatModeNumber);
                /// @}
            }
            /// M: remove "typing" feature for a while
//            if (!TextUtils.isEmpty(mTextEditor.getText().toString())) {
//                mIpMsgHandler.post(mSendTypingRunnable);
//            }
        }
        /// M: just update state but didn't notify will miss some mms/sms switch toast.
        //mWorkingMessage.updateStateForGroupMmsChanged();
//        mMmsComposePlugin.hideCtSendPanel();
        if (MmsConfig.isDualSendButtonEnable() && mSubCount < 1) {
            mMmsComposePlugin.hideDualButtonAndShowSrcButton();
            if (mWorkingMessage != null) {
                updateCounter(mWorkingMessage.getText(), 0, 0, 0);
            }
        }
        updateSendButtonState();
        if ((isRecipientsEditorVisible() && mRecipientsEditor.hasFocus())
                || (isSubjectEditorVisible() && mSubjectTextEditor.hasFocus())
                || ((mTextEditor != null && mTextEditor.getVisibility() == View.VISIBLE) && mTextEditor.hasFocus())) {
            showSharePanel(false);
        }
        if (mConversation.getRecipients().size() == 1 && mConversation.getMessageCount() > 0) {
            TextView remoteStrangerText = null;
            String number = mConversation.getRecipients().get(0).getNumber();
            boolean isStranger = IpMessageUtils.getContactManager(ComposeMessageActivity.this)
                    .isStrangerContact(number);
            if (isStranger) {
                remoteStrangerText = (TextView) findViewById(R.id.ipmsg_joyn_stranger_remind);
                if (remoteStrangerText != null) {
                    String reminderString = IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                            .getSingleString(IpMessageConsts.string.ipmsg_joyn_stranger_remind);
                    remoteStrangerText.setText(reminderString);
                    remoteStrangerText.setVisibility(View.VISIBLE);
                } else {
                    MmsLog.d(IPMSG_TAG, "remoteStrangerText is null!");
                }
            }
        }

        if (mOldThreadID == -1 && mConversation != null) {
            mOldThreadID = mConversation.getThreadId();
        }
       /*HQ_sunli 20151113 HQ01499362 begin*/
      if (mIsSmsEnabled && mPowerMode.equals("false")) {
       /*HQ_sunli 20151113 HQ01499362 end*/
            mShareButton.setClickable(true);
            mShareButton.setImageResource(R.drawable.ipmsg_share);
        } else {
            mShareButton.setClickable(false);
            mShareButton.setImageResource(R.drawable.ipmsg_share_disable);
        }
        /// @}
        /// M: add for fix ALPS01317511 @{
        if (MmsConfig.isSupportMessagingNotificationProxy()) {
            mHadToSlideShowEditor = false;
        }
        /// @}
        setCCRecipientEditorStatus(mIsSmsEnabled);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MmsLog.d(TAG, "onPause() enter!!");
        mIsActivityPaused = true;
        if (FeatureOption.MTK_GMO_ROM_OPTIMIZE) {
            MmsLog.d(TAG, "onPause() start Draft Service");
            startService(new Intent(this, DraftService.class));
        }
        /// M: fix bug ALPS00421362. Allow any blocked calls to update the thread's read status.
        /// M: fix bug ALPS01026162.for folder mode, no content loaded.must not mark as read.
        if (this.isFinishing() && !isRecipientsEditorVisible() && !MmsConfig.getMmsDirMode()) {
            mConversation.blockMarkAsRead(false);
            mConversation.markAsRead();
        }

        mDrawBottomPanel = true;

        // OLD: stop getting notified of presence updates to update the titlebar.
        // NEW: we are using ContactHeaderWidget which displays presence, but updating presence
        //      there is out of our control.
        //Contact.stopPresenceObserver();
        /// M: Code analyze 006, Control Sub indicator on status bar. @{
        mIsShowSubIndicator = false;
        //mStatusBarManager.hideSIMIndicator(mComponentName);
        StatusBarSelectorCreator.getInstance(this).hideStatusBar();
        /// @}

        removeRecipientsListeners();

        // remove any callback to display a progress spinner
        if (mAsyncDialog != null) {
            mAsyncDialog.clearPendingProgressDialog();
        }

        MessagingNotification.setCurrentlyDisplayedThreadId(MessagingNotification.THREAD_NONE);

        // Remember whether the list is scrolled to the end when we're paused so we can rescroll
        // to the end when resumed.
        if (mMsgListAdapter != null &&
                mMsgListView.getLastVisiblePosition() >= mMsgListAdapter.getCount() - 1) {
            mSavedScrollPosition = Integer.MAX_VALUE;
        } else {
            mSavedScrollPosition = mMsgListView.getFirstVisiblePosition();
        }
        if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            Log.v(TAG, "onPause: mSavedScrollPosition=" + mSavedScrollPosition);
        }

        mIsRunning = false;
        mErrorDialogShown = true;

        /// M: add for ip message, notification listener
        IpMessageUtils.removeIpMsgNotificationListeners(this, this);
        if (!TextUtils.isEmpty(mChatModeNumber)) {
            MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "onPause(): exit Chat Mode. number = " + mChatModeNumber);
            IpMessageUtils.getChatManager(this).exitFromChatMode(mChatModeNumber);
        }

        /// M: Stop in conversation notification sound
        MessagingNotification.stopInConversationNotificationSound();
        /// @}

        checkObsoleteThreadId();
    }

    @Override
    protected void onStop() {
        super.onStop();
        MmsLog.d(TAG, "onStop() enter!!");
//        mContentResolver.unregisterContentObserver(mDraftChangeObserver);
        /// M: fix bug ALPS01065220. onStart() will update message seen = 1. But if before update start,
        /// do not update it.{@
        mConversation.setComposeIsPaused(true);
        /// @}
        /// M: Code analyze 013, Get contacts from Contact app . @{
        if (misPickContatct) {
            stopDraftService();
            return;
        }
        /// @}
        // Allow any blocked calls to update the thread's read status.
      /// M: fix bug ALPS01026162.for folder mode, no content loaded.must not mark as read.
        if (!isRecipientsEditorVisible() && !MmsConfig.getMmsDirMode()) {
            mConversation.blockMarkAsRead(false);
            mConversation.markAsRead();
        }

        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("onStop: save draft");
        }

        /// M: If image is being compressed, wait for it
        if (isFinishing()) {
            waitForCompressing();
        }
        /// M: @{
        if ((!isRecipientsEditorVisible()) ||
                (mRecipientsEditor.hasValidRecipient(mWorkingMessage.requiresMms()))) {
            if (mMmsUtils != null
                && mMmsUtils.allowSafeDraft(this, MmsConfig.getDeviceStorageFullStatus(), false,
                    IMmsUtilsExt.TOAST_TYPE_FOR_SAVE_DRAFT)) {
                if (!mCompressingImage) {
                    saveDraft(true);
                } else {
                    mNeedSaveDraftAfterStop = true;
                    log("onStop: skip save draft");
                }
                if (mConversation.getMessageCount() == 0) {
                    ConversationListAdapter.removeSelectedState(mSelectedThreadId);
                }
            }
        }
        /// @}
        /// M: @{
        Log.v(TAG, "update MmsWidget");
        MmsWidgetProvider.notifyDatasetChanged(getApplicationContext());
        MmsLog.i(TAG, "onStop(): mWorkingMessage.isDiscarded() == " + mWorkingMessage.isDiscarded());
        /// @}
        // Cleanup the BroadcastReceiver.
        if (mHasRegisteredHttpProgressReceiver) {
            mHasRegisteredHttpProgressReceiver = false;
            unregisterReceiver(mHttpProgressReceiver);
        }

        /// M: remove "typing" feature for a while
//        if (mIsIpServiceEnabled) {
//            mIpMsgHandler.post(mSendStopTypingRunnable);
//        }
        /// M: add for mark message as read
        mIsMarkMsgAsRead = false;
        /// M: fix bug ALPS00380930, fix RecipientsAdapter cursor leak @{
        // RecipientsAdapter can not close the last cursor which returned by runQueryOnBackgroundThread
        //if (mRecipientsEditor != null && isFinishing()) {
        //    CursorAdapter recipientsAdapter = (CursorAdapter)mRecipientsEditor.getAdapter();
        //    if (recipientsAdapter != null) {
        //        recipientsAdapter.changeCursor(null);
        //    }
        //}
        /// @}

        // / M: fix bug ALPS00451836, remove FLAG_DISMISS_KEYGUARD flags
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                                   WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        stopDraftService();

        ///M: WFC: Stop notification ticker @ {
        if (SystemProperties.get("ro.mtk_wfc_support").equals("1")) {
            MessagingNotification.stopWfcNotification(getApplicationContext());
        }
    }

    @Override
    protected void onDestroy() {
        stopDraftService();
        /// M: fix bug ALPS00473488, delete ObsoleteThread through threadID when discard()
        if (mConversation.getMessageCount() == 0 && (!mWorkingMessage.isWorthSaving() || mWorkingMessage.isDiscarded())
                && isRecipientsEditorVisible() && mConversation.getDiscardThreadId() > 0) {
            Conversation.asyncDeleteObsoleteThreadID(mBackgroundQueryHandler, mConversation.getDiscardThreadId());
            mConversation.setDiscardThreadId(0);
        }
        mIsStartMultiDeleteActivity = false;
        /// @}
        MmsLog.d(TAG, "onDestroy()");
        if (TRACE) {
            android.os.Debug.stopMethodTracing();
        }

        mUiHandler.removeCallbacks(mContactPickRunnable);
        if (mContactPickDialog != null && mContactPickDialog.isShowing()) {
            mContactPickDialog.dismiss();
        }
        mContactPickDialog = null;

        unregisterReceiver(mSubReceiver);

        /// @}
        mDestroy = true;
        mScrollListener.destroyThread();
        /// M: Stop not started queries @{
        if (mBackgroundQueryHandler != null) {
            MmsLog.d(TAG, "clear pending queries in onDestroy");
            mBackgroundQueryHandler.cancelOperation(MESSAGE_LIST_QUERY_TOKEN);
            mBackgroundQueryHandler.cancelOperation(MESSAGE_LIST_UNREAD_QUERY_TOKEN);
            mBackgroundQueryHandler.removeCallbacks(mQueryUnReadMsgListRunnable);
            mBackgroundQueryHandler.removeCallbacks(mQueryMsgListRunnable);
        }
        /// @}
        if (mMsgListAdapter != null) {
            mMsgListAdapter.destroyTaskStack();
            /// M: we need unregister cursor, so no more callback
            mMsgListAdapter.changeCursor(null);
        /// M: Remove listener @{
            mMsgListAdapter.setOnDataSetChangedListener(null);
        /// @}
        }

        /// M: add for ipmessage
        mIsDestroyTypingThread = true;
        synchronized (mShowTypingLockObject) {
            mShowTypingLockObject.notifyAll();
        }
        if (SlideshowEditActivity.sCurrentActivity != null) {
            SlideshowEditActivity.sCurrentActivity.finish();
        } else {
            if (MmsPlayerActivity.sCurrentActivity != null) {
                MmsPlayerActivity.sCurrentActivity.finish();
            }
        }
        unregisterReceiver(mStatusBarSelectorReceiver);
        try{
            MessageListItem.getAlertDialog().dismiss();
        }catch (Exception e) {
            System.out.println("myDialog取消，失败！");
            // TODO: handle exception
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        MmsLog.d(TAG, "onConfigurationChanged-Start");
        super.onConfigurationChanged(newConfig);
        if (mSharePanel != null) {
            mSharePanel.resetShareItem();
        }
        if (LOCAL_LOGV) {
            Log.v(TAG, "onConfigurationChanged: " + newConfig);
        }

        if (resetConfiguration(newConfig)) {
            mMmsComposePlugin.hideOrShowSubjectForCC(isRecipientsCcEditorVisible()
                    && (mSubjectTextEditor != null) && isSharePanelShow()
                    && mIsLandscape, mSubjectTextEditor);
            // Have to re-layout the attachment editor because we have different layouts
            // depending on whether we're portrait or landscape.
            drawTopPanel(isSubjectEditorVisible());
        }
        onKeyboardStateChanged();
        MmsLog.d(TAG, "onConfigurationChanged-End");
    }

    private boolean mUpdateForScrnOrientationChanged = false;
    // returns true if landscape/portrait configuration has changed
    private boolean resetConfiguration(Configuration config) {
        MmsLog.d(TAG, "resetConfiguration-Start");
        mIsKeyboardOpen = config.keyboardHidden == KEYBOARDHIDDEN_NO;
        boolean isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        MmsLog.d(TAG, "resetConfiguration: isLandscape = " + isLandscape);
        /// M: Code analyze 004, Set max height for text editor. @{
        if ((mTextEditor != null) && (mTextEditor.getVisibility() == View.VISIBLE) && isLandscape) {
            mUiHandler.postDelayed(new Runnable() {
                public void run() {
                    MmsLog.d(TAG, "resetConfiguration(): mTextEditor.setMaxHeight: "
                            + mReferencedTextEditorThreeLinesHeight);
                    mTextEditor.setMaxHeight(mReferencedTextEditorTwoLinesHeight * mCurrentMaxHeight
                            / mReferencedMaxHeight);
                }
            }, 100);
        }
        /// @}

        MmsLog.d(IPMSG_TAG, "resetConfiguration(): isLandscape = " + isLandscape + ", mIsKeyboardOpen = " + mIsKeyboardOpen);
        if (!isLandscape && mIsLandscape == isLandscape && mIsSoftKeyBoardShow) {
            showSharePanel(false);
        }

        if (mIsLandscape != isLandscape) {
            MmsLog.d(TAG, "resetConfiguration-mUpdateForScrnOrientationChanged mIsLandscape = " + mIsLandscape);
            mUpdateForScrnOrientationChanged = true;
            mIsLandscape = isLandscape;
            MmsLog.d(TAG, "resetConfiguration-End");
            return true;
        }
        MmsLog.d(TAG, "resetConfiguration-End");
        return false;
    }

    private void onKeyboardStateChanged() {
        // If the keyboard is hidden, don't show focus highlights for
        // things that cannot receive input.
        mTextEditor.setEnabled(mIsSmsEnabled);
        if (!mIsSmsEnabled) {
            if (mRecipientsEditor != null) {
                mRecipientsEditor.setFocusableInTouchMode(false);
                mRecipientsEditor.setIsTouchable(false);
            }
            if (mSubjectTextEditor != null) {
                mSubjectTextEditor.setFocusableInTouchMode(false);
            }
            mTextEditor.setFocusableInTouchMode(false);
            mTextEditor.setHint(R.string.sending_disabled_not_default_app);
        } else if (mIsKeyboardOpen) {
            if (mRecipientsEditor != null) {
                mRecipientsEditor.setFocusableInTouchMode(true);
                mRecipientsEditor.setIsTouchable(true);
            }
            if (mSubjectTextEditor != null) {
                mSubjectTextEditor.setFocusableInTouchMode(true);
            }
            mTextEditor.setFocusableInTouchMode(true);
            /// M: add for ip message
            updateTextEditorHint();
        } else {
            if (mRecipientsEditor != null) {
                mRecipientsEditor.setFocusable(false);
                mRecipientsEditor.setIsTouchable(false);
            }
            if (mSubjectTextEditor != null) {
                mSubjectTextEditor.setFocusable(false);
            }
            mTextEditor.setFocusable(false);
            mTextEditor.setHint(R.string.open_keyboard_to_compose_message);
        }
    }

    @Override
    public void onUserInteraction() {
        checkPendingNotification();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyDown(): keyCode = " + keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DEL:
                if ((mMsgListAdapter != null) && mMsgListView.isFocused()) {
                    Cursor cursor;
                    try {
                        cursor = (Cursor) mMsgListView.getSelectedItem();
                    } catch (ClassCastException e) {
                        Log.e(TAG, "Unexpected ClassCastException.", e);
                        return super.onKeyDown(keyCode, event);
                    }

                    if (cursor != null) {
                        String type = cursor.getString(COLUMN_MSG_TYPE);
                        long msgId = cursor.getLong(COLUMN_ID);
                        MessageItem msgItem = mMsgListAdapter.getCachedMessageItem(type, msgId,
                                cursor);
                        if (msgItem != null) {
                            DeleteMessageListener l = new DeleteMessageListener(msgItem);
                            confirmDeleteDialog(l, msgItem.mLocked);
                        }
                        return true;
                    }
                }
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                /// M: @{
                break;
                /// @}
            case KeyEvent.KEYCODE_ENTER:
                /// M: Code analyze 028, Before sending message,check the recipients count
                /// and add sub card selection dialog if multi sim cards exist.@{
                /** Fix bug ALPS001070636
                if (isPreparedForSending()) {
                    checkRecipientsCount();
                    return true;
                } else {
                    unpreparedForSendingAlert();
                /// @}
                }
                **/
                break;
            case KeyEvent.KEYCODE_BACK:
                /// M:
                if (isSharePanelShow()) {
                    hideSharePanel();
                    return true;
                }
                /// M: Code analyze 038, If the user is editing slideshow now.
                /// Do not allow the activity finish but return directly when back key is entered. @{
                if (mIsEditingSlideshow) {
                    return true;
                }
                /// @}

                // M: when out of composemessageactivity,try to send read report
                if (FeatureOption.MTK_SEND_RR_SUPPORT) {
                    checkAndSendReadReport();
                }
                /// @}
                mIsCheckObsolete = true;
                exitComposeMessageActivity(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                });
                return true;
            case KeyEvent.KEYCODE_MENU:
                invalidateOptionsMenu();
                return false;
        }

        return super.onKeyDown(keyCode, event);
    }

    private void unpreparedForSendingAlert() {
        if (!isHasRecipientCount()) {
            new AlertDialog.Builder(this).setIconAttribute(
                    android.R.attr.alertDialogIcon).setTitle(
                    R.string.cannot_send_message).setMessage(
                    R.string.cannot_send_message_reason).setPositiveButton(
                    R.string.yes, new CancelSendingListener()).show();
        } else {
            new AlertDialog.Builder(this).setIconAttribute(
                    android.R.attr.alertDialogIcon).setTitle(
                    R.string.cannot_send_message).setMessage(
                    R.string.cannot_send_message_reason_no_content)
                    .setPositiveButton(R.string.yes,
                            new CancelSendingListener()).show();
        }
    }

    private void exitComposeMessageActivity(final Runnable exit) {
          VideoThumbnailCache.clear();
        // If the message is empty, just quit -- finishing the
        // activity will cause an empty draft to be deleted.
		/*HQ_zhangjing  add for al812 to add sms signature begin */
		String defaultSignature = getResources().getString(R.string.default_signature);
		String mSignatureStr = "\n"+getSharedPreferences(MessageUtils.DEFAULT_MMS_PREFERENCE_NAME, MODE_WORLD_READABLE).getString("key_signature_info",defaultSignature);
        CharSequence text = mWorkingMessage.getText();
		String subtext = text.toString();
        if ((!mWorkingMessage.isWorthSaving() && mIpMessageDraft == null )
			||( !mWorkingMessage.hasAttachment() && !mWorkingMessage.hasSlideshow() 
			&& mSignatureStr.equals(subtext)
			&& getSharedPreferences(MessageUtils.DEFAULT_MMS_PREFERENCE_NAME, MODE_WORLD_READABLE).getBoolean(GeneralPreferenceActivity.KEY_SIGNATURE,false))){
		/*HQ_zhangjing  add for al812 to add sms signature end */	
            /// M: Code analyze 042, If you discard the draft message manually.@{
            ConversationListAdapter.removeSelectedState(mSelectedThreadId);
            mWorkingMessage.discard();
            mHasDiscardWorkingMessage = true;
            /// @}
            /// M Fix CR: ALPS01222344. new message input a contact and some content, incoming a messae
            /// which is the same contact as the draft. it will not show draft icon in the conversationlist @{
            new Thread(new Runnable() {
                public void run() {
                    long threadId = mConversation.getThreadId();
                    boolean isHasDraft = DraftCache.refreshDraft(ComposeMessageActivity.this, threadId);
                    Log.d(TAG, "exitComposeMessageActivity, nothing to be save, reset threadId : "
                            + threadId + ", isHasDraft : " + isHasDraft);
                    DraftCache.getInstance().setDraftState(threadId, isHasDraft);
                }
            }, "Composer.resetDraftCache").start();
            /// @}
            exit.run();
            return;
        }

        if (isRecipientsEditorVisible() &&
                !mRecipientsEditor.hasValidRecipient(mWorkingMessage.requiresMms())) {
            MessageUtils.showDiscardDraftConfirmDialog(this, new DiscardDraftListener());
            return;
        }

        if (needSaveDraft()) {
            if (mMmsUtils != null
                && mMmsUtils.allowSafeDraft(this, MmsConfig.getDeviceStorageFullStatus(), true,
                    IMmsUtilsExt.TOAST_TYPE_FOR_SAVE_DRAFT)) {
                /// M: for requery searchactivity.
                SearchActivity.setNeedRequery();
                if (mIsSmsEnabled) {
                    SearchActivity.setWaitSaveDraft();
                }
                DraftCache.getInstance().setSavingDraft(true);
            }
        }
        mWorkingMessage.setNeedDeleteOldMmsDraft(true);
        mToastForDraftSave = true;
        exit.run();
    }

    private void goToConversationList() {
        finish();
        /// M: Code analyze 010, Support dirtory mode. @{
        if (MmsConfig.getMmsDirMode()) {
            Intent it = new Intent(this, FolderViewList.class);
            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            it.putExtra("floderview_key", mHomeBox);
            startActivity(it);
        } else {
        ///  @}
        /// M: add extra flags
        Intent it = new Intent(this, ConversationList.class);
        it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(it);
        }
    }

    private void hideRecipientEditor() {
        if (mRecipientsEditor != null) {
            Log.i(TAG, "hideRecipientEditor");
            mRecipientsEditor.removeChipChangedListener(mChipWatcher);
            mRecipientsEditor.setVisibility(View.GONE);
            /// M: Code analyze 059, Set the pick button visible or
            /// invisible the same as recipient editor.
            mRecipientsPicker.setVisibility(View.GONE);
            /// @}
            hideOrShowTopPanel();
            mRecipientsEditor.recycleBitmap();
        }
    }

    private boolean isRecipientsEditorVisible() {
        return (null != mRecipientsEditor)
                    && (View.VISIBLE == mRecipientsEditor.getVisibility());
    }

    private boolean isSubjectEditorVisible() {
        return (null != mSubjectTextEditor)
                    && (View.VISIBLE == mSubjectTextEditor.getVisibility());
    }

    @Override
    public void onAttachmentChanged() {
        // Have to make sure we're on the UI thread. This function can be called off of the UI
        // thread when we're adding multi-attachments
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                drawBottomPanel();
                updateSendButtonState();
                drawTopPanel(isSubjectEditorVisible());
                if (null != mRecipientsEditor) {
                    if (mWorkingMessage.hasSlideshow()) {
                        mRecipientsEditor.setImeActionLabel(getString(MessageResource.string.ime_action_done),
                                EditorInfo.IME_ACTION_DONE);
                        mRecipientsEditor.setImeOptions(EditorInfo.IME_ACTION_DONE);
                    } else {
                        mRecipientsEditor.setImeActionLabel(getString(MessageResource.string.ime_action_next),
                                EditorInfo.IME_ACTION_NEXT);
                        mRecipientsEditor.setImeOptions(EditorInfo.IME_ACTION_NEXT);
                    }
                }

                mInputMethodManager.restartInput(mRecipientsEditor);
            }
        });
    }
    private boolean mIsConvertToastDelayed = false;
    /// M: Code analyze 060, For bug ALPS00050082, When the protocol has been changed,
    /// whether show a toast . @{
    private boolean mIsConvertMms = false;
    @Override
    public void onProtocolChanged(final boolean mms, final boolean needToast) {
        // Have to make sure we're on the UI thread. This function can be called off of the UI
        // thread when we're adding multi-attachments
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //toastConvertInfo(mms);
                /// M: fix ALPS01033728, don't call showSmsOrMmsSendButton() here, method
                /// updateSendButtonState() will call it.
                //showSmsOrMmsSendButton(mms);
                updateSendButtonState();
                /// M: For OP09 DualSentButton Feature: @{
				/*HQ_zhangjing 2015-11-02 modified for CQ HQ01478873 */
                if (false && MmsConfig.isDualSendButtonEnable()) {
                    if (!mms) {
                        updateCounter(mWorkingMessage.getText(), 0, 0, 0);
                    }
                ///@}
                } else if (mms) {
                    // In the case we went from a long sms with a counter to an mms because
                    // the user added an attachment or a subject, hide the counter --
                    // it doesn't apply to mms.
					/*HQ_zhangjing 2015-10-22 modified for send btn begin*/
                    mTextCounter.setVisibility(View.VISIBLE);
					mTextCounter.setText( getMmsSize() );
					//mTextCounterForDouble.setVisibility(View.VISIBLE);
					mTextCounterForDouble.setText( getMmsSize() );
					/*HQ_zhangjing 2015-10-22 modified for send btn end*/
                } else {
                    if (mTextEditor.getLineCount() <= 1 && !MmsConfig.isDualSendButtonEnable()) {
                        mTextCounter.setVisibility(View.GONE);
                    } else {
                        mTextCounter.setVisibility(View.VISIBLE);
						/*HQ_zhangjing 2015-10-22 modified for send btn begin*/
						//mTextCounterForDouble.setVisibility(View.VISIBLE);
						showOrHideSendbtn();
						/*HQ_zhangjing 2015-10-22 modified for send btn end*/
                    }
                }
                if (needToast) {
                    if (mIsPopulatingRecipients) {
                        mIsConvertToastDelayed = true;
                        mIsConvertMms = mms;
                    } else {
                    toastConvertInfo(mms);
                }
            }
            }
        });
    }
    /// @}

    // Show or hide the Sms or Mms button as appropriate. Return the view so that the caller
    // can adjust the enableness and focusability.
    private View showSmsOrMmsSendButton(boolean isMms) {
        View showButton;
        View hideButton1;
        View hideButton2;
		/*HQ_zhangjing 2015-10-22 modified for send btn begin*/
		showButton = mSendButtonSms;
		hideButton1 = mSendButtonIpMessage;
		hideButton2 = mSendButtonMms;
/*		
        MmsLog.d(IPMSG_TAG, "showSmsOrMmsSendButton(): SendCommonMsgThisTime = " + mJustSendMsgViaCommonMsgThisTime
 + ", mIsMessageDefaultSimIpServiceEnabled = "
                + mIsIpServiceEnabled
            + ", isNetworkConnected(getApplicationContext() = " + isNetworkConnected(getApplicationContext())
            + ", mIpMessageDraft is not null ?= " + (mIpMessageDraft != null)
            + ", isCurrentRecipientIpMessageUser() = " + isCurrentRecipientIpMessageUser());
		
        if (isMms) {
            if (mSubCount == 0 || (isRecipientsEditorVisible() && TextUtils.isEmpty(mRecipientsEditor.getText()))
                    /// M: fix bug ALPS00563318, show gray mms_send_button
                    /// when haven't subject, text and attachment
                    || ((mSubjectTextEditor == null || (mSubjectTextEditor != null
                            && TextUtils.isEmpty(mSubjectTextEditor.getText().toString().trim())))
                            && mTextEditor != null
                            && TextUtils.isEmpty(mTextEditor.getText().toString().trim())
                            && !mWorkingMessage.hasAttachment())
                            || !mIsSmsEnabled) {
                mSendButtonMms.setCompoundDrawablesWithIntrinsicBounds(null, null, null,
                    getResources().getDrawable(R.drawable.ic_send_sms_unsend));
            } else {
                mSendButtonMms.setCompoundDrawablesWithIntrinsicBounds(null, null, null,
                    getResources().getDrawable(R.drawable.ic_send_ipmsg));
            }
            showButton = mSendButtonSms;
            hideButton1 = mSendButtonMms;
            hideButton2 = mSendButtonIpMessage;
        } else if (!mJustSendMsgViaCommonMsgThisTime
                && mIsIpServiceEnabled
                && isNetworkConnected(getApplicationContext())
                && ((mIpMessageDraft != null && recipientCount() > 0) || isCurrentRecipientIpMessageUser()
                        /// M: Fix ipmessage bug ,fix bug ALPS 01585547@{
                        /// M: Fix ipmessage bug for ALPS01674002
                        && mCurrentChatMode == IpMessageConsts.ChatMode.JOYN) && mIsSmsEnabled && isCurrentIpmessageSendable()) {
            showButton = mSendButtonIpMessage;
            hideButton1 = mSendButtonSms;
            hideButton2 = mSendButtonMms;
        } else {
            ///M: ipmessage is disabled,then forwarded multimedia message, When input recipient,the send button is still gray {@
            if ((mTextEditor.getText().toString().isEmpty() && mIpMessageDraft == null) || mSubCount == 0
                    || (isRecipientsEditorVisible() && TextUtils.isEmpty(mRecipientsEditor.getText()))
                    || recipientCount() > MmsConfig.getSmsRecipientLimit()
                    || !mIsSmsEnabled) {
            ///@}
            	if(!FeatureOption.HQ_MMS_SEND_EMPTY_MESSAGE){
            		//mSendButtonSms.setImageResource(R.drawable.ic_send_sms_unsend);
            		
					Log.d("send_btn","!FeatureOption.HQ_MMS_SEND_EMPTY_MESSAGE ");
					mUniqueSendBtnContainer.setVisibility( View.GONE );
					mDoubleSendBtnContainer.setVisibility( View.GONE );
            	}
            } else {
                //mSendButtonSms.setImageResource(R.drawable.ic_send_ipmsg);
            }
            showButton = mSendButtonSms;
            hideButton1 = mSendButtonIpMessage;
            hideButton2 = mSendButtonMms;
        }
*/
		/*if( mSubCount > 1){
			Log.d("send_btn","mSubCount > 1");
			showButton = (View)mDoubleSendBtnContainer;
		}*/
		/*HQ_zhangjing 2015-10-22 modified for send btn end*/
        if (showButton != null) {
            showButton.setVisibility(View.VISIBLE);
        }
        if (hideButton1 != null) {
            hideButton1.setVisibility(View.GONE);
        }
        if (hideButton2 != null) {
            hideButton2.setVisibility(View.GONE);
        }
        /// M: add for ip message, update text editor hint
        updateTextEditorHint();
        return showButton;
    }

    Runnable mResetMessageRunnable = new Runnable() {
        @Override
        public void run() {
            resetMessage();
        }
    };

    @Override
    public void onPreMessageSent() {
        runOnUiThread(mResetMessageRunnable);
    }

    @Override
    public void onMessageSent() {
        // This callback can come in on any thread; put it on the main thread to avoid
        // concurrency problems
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                /// M: @{
                mWaitingForSendMessage = false;
                /// @}
                // If we already have messages in the list adapter, it
                // will be auto-requerying; don't thrash another query in.
                // TODO: relying on auto-requerying seems unreliable when priming an MMS into the
                // outbox. Need to investigate.
//                if (mMsgListAdapter.getCount() == 0) {
                    if (LogTag.VERBOSE) {
                        log("onMessageSent");
                    }
                    startMsgListQuery(MESSAGE_LIST_QUERY_TOKEN, 0);
//                }

                // The thread ID could have changed if this is a new message that we just inserted
                // into the database (and looked up or created a thread for it)
                updateThreadIdIfRunning();
            }
        });
    }

    @Override
    public void onMaxPendingMessagesReached() {
        saveDraft(false);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ComposeMessageActivity.this, R.string.too_many_unsent_mms,
                        Toast.LENGTH_LONG).show();
                mSendingMessage = false;
                updateSendButtonState();
            }
        });
    }

    @Override
    public void onAttachmentError(final int error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                handleAddAttachmentError(error, R.string.type_picture);
                onMessageSent();        // now requery the list of messages
            }
        });
    }

    // We don't want to show the "call" option unless there is only one
    // recipient and it's a phone number.
    private boolean isRecipientCallable() {
        ContactList recipients = getRecipients();
        return (recipients.size() == 1 && !recipients.containsEmail());
    }
    /// M: Code analyze 061, Add video call menu.
    private void dialRecipient(Boolean isVideoCall) {
        if (isRecipientCallable()) {
            String number = getRecipients().get(0).getNumber();
            Intent dialIntent ;
            if (isVideoCall) {
                dialIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
                dialIntent.putExtra("com.android.phone.extra.video", true);
            } else {
                dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
            }
            hideInputMethod();
            startActivity(dialIntent);
        }
    }
    /// @}
/*HQ_sunli 20150916 HQ01359292 begin*/
     private boolean checkNumberBlocked(String Number) {
	   try
                    {
                    	Bundle localBundle = new Bundle();
                    	localBundle.putString("CHECK_PHONENUMBER", Number);
                    	int isBlock = -1;
                    	if(null != hisStub) {
                    		isBlock = hisStub.checkPhoneNumberFromBlockItem(localBundle, 0);
                    	}
                    	if(isBlock==0){
                        return true;
                        }
                        else {
                        return false;
                        }
                       
                    }
                    catch (RemoteException paramContext)
                    {
                    	paramContext.printStackTrace();
                    	return false;
      
    }
}
/*HQ_sunli 20150916 HQ01359292 end*/
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu) ;
		/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display begin*/
		mMenuitemResponseIds.clear();
		mMenuItemStringIds.clear();
		/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display end*/
        menu.clear();
        /// M: google JB.MR1 patch, group mms
        if (mIsSmsEnabled) {
            if (getRecipients().size() > 1) {
                menu.add(0, MENU_GROUP_PARTICIPANTS, 0, R.string.menu_group_participants);
				//HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display 
				addMenuIdAndStr(R.string.menu_group_participants,MENU_GROUP_PARTICIPANTS);
            }

            if (mWorkingMessage.hasSlideshow()) {
                menu.add(0, MENU_ADD_ATTACHMENT, 0, R.string.add_attachment);
				//HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display 
				addMenuIdAndStr(R.string.add_attachment,MENU_ADD_ATTACHMENT);
            }
        }
        /// M: whether has IPMsg APK or not. ture: has ; false: no;
        boolean hasIpMsgApk = MmsConfig.isServiceEnabled(this);
        /// M: the identity of whether the current users all are ipmessage user or not.
        boolean hasIpMsgUser = isCurrentRecipientIpMessageUser();
        /// M: true: the host has been activated.
        boolean hasActivatedHost = MmsConfig.isActivated(this);
        if (mIsSmsEnabled) {
            if (hasIpMsgUser && hasActivatedHost && !isRecipientsEditorVisible()) {
                menu.add(0, MENU_INVITE_FRIENDS_TO_CHAT, 0,
                        IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                    .getSingleString(IpMessageConsts.string.ipmsg_invite_friends_to_chat));
				/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
				addMenuIdAndStr(IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                    .getSingleString(IpMessageConsts.string.ipmsg_invite_friends_to_chat),
                                    MENU_INVITE_FRIENDS_TO_CHAT);
				/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
                if (IpMessageUtils.getServiceManager(ComposeMessageActivity.this).isFeatureSupported(
                        IpMessageConsts.FeatureId.EXPORT_CHAT)) {
                    menu.add(0, MENU_EXPORT_CHAT, 0,
                            IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                            .getSingleString(IpMessageConsts.string.ipmsg_export_chat));
				/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
					addMenuIdAndStr(IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                            .getSingleString(IpMessageConsts.string.ipmsg_export_chat),
                            MENU_EXPORT_CHAT);
				/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
                }
                if (mConversation.getRecipients().size() == 1 && (IpMessageUtils.getServiceManager(ComposeMessageActivity.this).getIntegrationMode() != IpMessageConsts.IntegrationMode.ISMS_MODE)) {
                    if (mCurrentChatMode == IpMessageConsts.ChatMode.JOYN) {
                        if (!IpMessageUtils.getServiceManager(ComposeMessageActivity.this).isAlwaysSendMessageByJoyn()) {
                            if (IpMessageUtils.getContactManager(this).getStatusByNumber(mConversation.getRecipients().getNumbers()[0]) == 0) {
                                menu.add(1, MENU_SEND_BY_SMS_IN_JOYN_MODE, 0, IpMessageUtils.getResourceManager(
                                        ComposeMessageActivity.this).getSingleString(
                                        IpMessageConsts.string.ipmsg_send_by_xms));
							/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
								addMenuIdAndStr(IpMessageUtils.getResourceManager(ComposeMessageActivity.this).getSingleString(IpMessageConsts.string.ipmsg_send_by_xms),
									MENU_SEND_BY_SMS_IN_JOYN_MODE);
							/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
                            }
                        }
                    }
                    if (mCurrentChatMode == IpMessageConsts.ChatMode.XMS
                            && IpMessageUtils.getContactManager(this).getStatusByNumber(mConversation.getRecipients().getNumbers()[0]) == 1) {
                        menu.add(1, MENU_SEND_BY_JOYN_IN_SMS_MODE, 0,
                                IpMessageUtils.getResourceManager(ComposeMessageActivity.this).getSingleString(IpMessageConsts.string.ipmsg_send_by_joyn));
						/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */		
						addMenuIdAndStr(IpMessageUtils.getResourceManager(ComposeMessageActivity.this).getSingleString(IpMessageConsts.string.ipmsg_send_by_joyn),
							MENU_SEND_BY_JOYN_IN_SMS_MODE);
						/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
                    }
                }
            } else if (!hasIpMsgUser && mIsIpServiceEnabled && mShowInviteMsg) {
                menu.add(0, MENU_INVITE_FRIENDS_TO_IPMSG, 0,
                    IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                .getSingleString(IpMessageConsts.string.ipmsg_invite_friends_to_ipmsg));
				/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
				addMenuIdAndStr(IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                .getSingleString(IpMessageConsts.string.ipmsg_invite_friends_to_ipmsg),
                                MENU_INVITE_FRIENDS_TO_IPMSG);
				/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
            }
        }
        if (!isRecipientsEditorVisible()) {
            MmsLog.d(TAG, "onPrepareOptionsMenu recipient editor is not visible!");
            if (getRecipients().size() == 1) {
                Contact contact = getRecipients().get(0);
                contact.reload(true);
                if (contact.existsInDatabase()) {
                    MmsLog.d(TAG, "onPrepareOptionsMenu contact is in database: " + contact.getUri());
                    menu.add(0, MENU_SHOW_CONTACT, 0, R.string.menu_view_contact)
                        .setIcon(R.drawable.ic_menu_recipients)
                        .setTitle(R.string.menu_view_contact)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);//HQ_zhangjing add for al812 mms ui
					/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
					addMenuIdAndStr(R.string.menu_view_contact,MENU_SHOW_CONTACT);
					/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
                    mQuickContact.assignContactUri(contact.getUri());
                } else if (MessageUtils.canAddToContacts(contact)) {
                    menu.add(0, MENU_CREATE_CONTACT, 0, R.string.menu_add_to_contacts)
                        .setIcon(R.drawable.ic_menu_recipients)
                        .setTitle(R.string.menu_add_to_contacts)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);//HQ_zhangjing add for al812 mms ui
					/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
					addMenuIdAndStr(R.string.menu_add_to_contacts,MENU_CREATE_CONTACT);
					/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
                    String number = contact.getNumber();
                    // add for joyn converged inbox mode
                    if (number.startsWith(IpMessageConsts.JOYN_START)) {
                        number = number.substring(4);
                    }
                    if (Mms.isEmailAddress(number)) {
                        mQuickContact.assignContactFromEmail(number, true);
                    } else {
                        mQuickContact.assignContactFromPhone(number, true);
                    }
                }
                if (IpMessageUtils.getServiceManager(ComposeMessageActivity.this).getIntegrationMode() !=
                    IpMessageConsts.IntegrationMode.NORMAL && (IpMessageUtils.getServiceManager(ComposeMessageActivity.this).getIntegrationMode() != IpMessageConsts.IntegrationMode.ISMS_MODE)) {
                    if (mIsSmsEnabled) {
                        if (mCurrentChatMode == IpMessageConsts.ChatMode.JOYN) {
                            String enterXmsChat = IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                    .getSingleString(IpMessageConsts.string.ipmsg_enter_xms_chat);
                            Drawable enterXmsIcon = getResources().getDrawable(R.drawable.ic_launcher_smsmms);
                            menu.add(0, MENU_JUMP_TO_XMS, 0, enterXmsChat).setIcon(enterXmsIcon).setTitle(enterXmsChat)
                            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);//HQ_zhangjing add for al812 mms ui
							/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
							addMenuIdAndStr(enterXmsChat,MENU_JUMP_TO_XMS);
							/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
                        } else {
                            if (IpMessageUtils.getServiceManager(ComposeMessageActivity.this).getIntegrationMode() == IpMessageConsts.IntegrationMode.CONVERGED_INBOX && IpMessageUtils.getContactManager(this).isIpMessageNumber(
                                    IpMessageConsts.JOYN_START + contact.getNumber())) {
                                String enterJoynChat = IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                        .getSingleString(IpMessageConsts.string.ipmsg_enter_joyn_chat);
                                Drawable enterJoynIcon = IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                        .getSingleDrawable(IpMessageConsts.drawable.enter_joyn_chat);
                                menu.add(0, MENU_JUMP_TO_JOYN, 0, enterJoynChat).setIcon(enterJoynIcon).setTitle(
                                        enterJoynChat).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);//HQ_zhangjing add for al812 mms ui
								/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
								addMenuIdAndStr(enterJoynChat,MENU_JUMP_TO_JOYN);
								/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
                            } else if (IpMessageUtils.getServiceManager(ComposeMessageActivity.this).getIntegrationMode() == IpMessageConsts.IntegrationMode.FULLY_INTEGRATED && IpMessageUtils.getContactManager(this).isIpMessageNumber(
                                    contact.getNumber())) {
                                String enterJoynChat = IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                        .getSingleString(IpMessageConsts.string.ipmsg_enter_joyn_chat);
                                Drawable enterJoynIcon = IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                        .getSingleDrawable(IpMessageConsts.drawable.enter_joyn_chat);
                                menu.add(0, MENU_JUMP_TO_JOYN, 0, enterJoynChat).setIcon(enterJoynIcon).setTitle(
                                        enterJoynChat).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);//HQ_zhangjing add for al812 mms ui
								/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
								addMenuIdAndStr(enterJoynChat,MENU_JUMP_TO_JOYN);
								/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
                            }
                        }
                    }
                }
            }
			/*HQ_zhangjing 2015-10-07 modified for mms menu tree begin*/
			/*
            if (mIsSmsEnabled) {
                menu.add(0, MENU_SELECT_MESSAGE, 0, R.string.select_message);
				addMenuIdAndStr(R.string.select_message,MENU_SELECT_MESSAGE);
            }*/
			/*HQ_zhangjing 2015-10-07 modified for mms menu tree end*/
            if (mIsSmsEnabled && hasActivatedHost) {
                MmsLog.d(IPMSG_TAG, "onPrepareOptionsMenu(): spam = " + mConversation.isSpam());
                if (mConversation.isSpam()) {
                    menu.add(0, MENU_REMOVE_SPAM, 0, R.string.remove_frome_spam);
					/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
					addMenuIdAndStr(R.string.remove_frome_spam,MENU_REMOVE_SPAM);
					/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
                } else {
                    menu.add(0, MENU_MARK_AS_SPAM, 0, R.string.mark_as_spam);
					/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
					addMenuIdAndStr(R.string.mark_as_spam,MENU_MARK_AS_SPAM);
					/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
                }
            }
        }

		  /*HQ_sunli 20150916 HQ01359292 begin*/
		if (null != getRecipients() && getRecipients().size() >= 1) {
			String number = getRecipients().get(0).getNumber();
		
		if (mMenuitemResponseIds.contains(MENU_REMOVE_FROM_BLACKLIST) && mMenuItemStringIds.contains(getString(R.string.menu_remove_from_black_list))) {
			mMenuitemResponseIds.remove((Object)MENU_REMOVE_FROM_BLACKLIST);
			mMenuItemStringIds.remove(getString(R.string.menu_remove_from_black_list));
		}
		
		if (mMenuitemResponseIds.contains(MENU_ADD_TO_BLACKLIST) && mMenuItemStringIds.contains(getString(R.string.menu_add_to_black_list))) {
			mMenuitemResponseIds.remove((Object)MENU_ADD_TO_BLACKLIST);
			mMenuItemStringIds.remove(getString(R.string.menu_add_to_black_list));
		}
		
		   if (checkNumberBlocked( number )==true) {
		   		addMenuIdAndStr(R.string.menu_remove_from_black_list,MENU_REMOVE_FROM_BLACKLIST);
			}else{
		   		addMenuIdAndStr(R.string.menu_add_to_black_list,MENU_ADD_TO_BLACKLIST);
		}
		}
			/*HQ_sunli 20150916 HQ01359292 end*/


        /// M: Code analyze 061, Add video call menu.
        TelephonyManager telephony = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephony != null && telephony.isVoiceCapable() && isRecipientCallable()) {
            MenuItem item = menu.add(0, MENU_CALL_RECIPIENT, 0, R.string.menu_call)
                .setIcon(R.drawable.ic_menu_call)
                .setTitle(R.string.menu_call);
			/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
			addMenuIdAndStr(R.string.menu_call,MENU_CALL_RECIPIENT);
			/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
            if (!isRecipientsEditorVisible()) {
                // If we're not composing a new message, show the call icon in the actionbar
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);//HQ_zhangjing add for al812 mms ui
            }
        /// @}
        }

        /// M: Code analyze 014, Add quick text. @{
        if (mIsSmsEnabled) {
            if (!mWorkingMessage.hasSlideshow() || (mSubjectTextEditor != null && mSubjectTextEditor.isFocused())) {
                menu.add(0, MENU_ADD_QUICK_TEXT, 0, R.string.menu_insert_quick_text).setIcon(
                    R.drawable.ic_menu_quick_text);
				/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
				/*addMenuIdAndStr(R.string.menu_insert_quick_text,MENU_ADD_QUICK_TEXT);*/
            }	/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
        }
        /// @}

        /// M: Code analyze 015, Add text vcard. @{
        /*if (mIsSmsEnabled) {
            if (!mWorkingMessage.hasSlideshow() && !hasIpMsgApk) {
                menu.add(0, MENU_ADD_TEXT_VCARD, 0, R.string.menu_insert_text_vcard);
				addMenuIdAndStr(R.string.menu_insert_text_vcard,MENU_ADD_TEXT_VCARD);
            }
        }*/
        /// @}
        if (mIsSmsEnabled) {
            if (!isSubjectEditorVisible() && !mMmsComposePlugin.getIsSubjectHide()) {
                menu.add(0, MENU_ADD_SUBJECT, 0, R.string.add_subject).setIcon(R.drawable.ic_menu_edit);
            }
        }
		/* HQ_zhangjing modified for delete the add to contact menu begin*/
		/*
        if (!hasIpMsgApk) {
            buildAddAddressToContactMenuItem(menu);
        }
        */
	/*HQ_zhangjing modified for delete the add to contact menu end*/
        if (LogTag.DEBUG_DUMP) {
            menu.add(0, MENU_DEBUG_DUMP, 0, R.string.menu_debug_dump);
			/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
			addMenuIdAndStr(R.string.menu_debug_dump,MENU_DEBUG_DUMP);
			/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
        }

        if (mIsSmsEnabled) {
            if (!isRecipientsEditorVisible()) {
                menu.add(0, MENU_CHAT_SETTING, 0, R.string.pref_setting_chat).setIcon(
                    android.R.drawable.ic_menu_preferences);
				/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
				/*addMenuIdAndStr(R.string.pref_setting_chat,MENU_CHAT_SETTING);*/
                /// M: Add for OP09: @{
                /*if (MmsConfig.isMassTextEnable()) {
                    ContactList cl = mConversation.getRecipients();
                    if (cl != null && cl.size() > 1) {
                        mMmsComposePlugin.addSplitThreadOptionMenu(menu, getComposeContext(), mConversation.getThreadId());
                    }
                }*/
				/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
                /// @}
            }
        }

        if (isRecipientsEditorVisible() && mIsSmsEnabled) {
            menu.add(0, MENU_DISCARD, 0, R.string.discard).setIcon(
                android.R.drawable.ic_menu_delete);
			/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
			addMenuIdAndStr(R.string.discard,MENU_DISCARD);
			/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
        }

        /// M: support mms cc feature. OP09 requested
        if (MmsConfig.isSupportSendMmsWithCc() && !isRecipientsCcEditorVisible() && mIsSmsEnabled) {
            /** M: this constraint is not so needed, just for simple.
             *  cc recipients can be added as mms subject, but more logic need add.
             */
            if (isRecipientsEditorVisible()) {
                menu.add(0, MENU_ADD_MMS_CC, 0, R.string.add_mms_cc);
				/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
				addMenuIdAndStr(R.string.add_mms_cc,MENU_ADD_MMS_CC);
				/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
            }
			/*HQ_zhangjing 2015-10-07 modified for mms menu tree begin*/
        }

        if (!isRecipientsEditorVisible() && mIsSmsEnabled ) {
			menu.add(0, MENU_SELECT_MESSAGE, 0, R.string.select_message);
			addMenuIdAndStr(R.string.select_message,MENU_SELECT_MESSAGE);
			/*HQ_zhangjing 2015-10-07 modified for mms menu tree end*/
        }		
		/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
		//return true;
        return false;
		/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
    }

    private void buildAddAddressToContactMenuItem(Menu menu) {
        // Look for the first recipient we don't have a contact for and create a menu item to
        // add the number to contacts.
        for (Contact c : getRecipients()) {
            /// M: Code analyze 043, Whether the address can be added to contacts app. @{
            if (!c.existsInDatabase() && MessageUtils.canAddToContacts(c)) {
            /// @}
                Intent intent = ConversationList.createAddContactIntent(c.getNumber());
                menu.add(0, MENU_ADD_ADDRESS_TO_CONTACTS, 0, R.string.menu_add_to_contacts)
                    .setIcon(android.R.drawable.ic_menu_add)
                    .setIntent(intent);
                break;
            }
        }
    }

	/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
	private void addMenuIdAndStr( int strId,int responseId){
		mMenuitemResponseIds.add(responseId);
		mMenuItemStringIds.add(getString(strId));
		
	}
	
	private void addMenuIdAndStr( CharSequence sequence,int responseId){
		mMenuitemResponseIds.add(responseId);
		mMenuItemStringIds.add(sequence);
		
	}

	private void showString(){
		Log.d("zhangjing","mMenuItemStringIds.size= " +mMenuItemStringIds.size() );
		for( int i = 0;i < mMenuItemStringIds.size();i++){
			Log.d("zhangjing","mMenuItemStringIds[ " + i+ " ]=" +  mMenuItemStringIds.get(i).toString() );
		}
	}
	/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */
    
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        onIpMsgOptionsItemSelected(item);
        mMmsComposePlugin.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case MENU_ADD_SUBJECT:
                showSubjectEditor(true);
                mWorkingMessage.setSubject("", true);
                /// M: Code analyze 052, Show input keyboard.@{
                mInputMethodManager.showSoftInput(getCurrentFocus(), InputMethodManager.SHOW_IMPLICIT);
                /// @}
                updateSendButtonState();
                mSubjectTextEditor.requestFocus();
                break;
            case MENU_ADD_ATTACHMENT:
                // Launch the add-attachment list dialog
                /// M: Code analyze 051, Hide input keyboard.@{
                hideInputMethod();
                /// M: Vcard and slides can be in same screen
                // add for attachment enhance
                if (MmsConfig.isSupportAttachEnhance()) {
                    // OP01
                    showAddAttachmentDialog(true);
                    MmsLog.d(TAG, "Attach: showAddAttachmentDialog(true)");
                } else {
                    showAddAttachmentDialog(!mWorkingMessage.hasAttachedFiles());
                    MmsLog.d(TAG, "Attach: showAddAttachmentDialog(!hasAttachedFiles)");
                }
                break;
            /// M: Code analyze 014, Add quick text. @{
            case MENU_ADD_QUICK_TEXT:
                showQuickTextDialog();
                break;
            /// @}
            /// M: Code analyze 015, Add text vcard. @{
            case MENU_ADD_TEXT_VCARD: {
                Intent intent = new Intent("android.intent.action.contacts.list.PICKMULTICONTACTS");
                intent.setType(Contacts.CONTENT_TYPE);
                startActivityForResult(intent, REQUEST_CODE_TEXT_VCARD);
                break;
            }
            /// @}
            case MENU_DISCARD:
                /// M: fix bug for ConversationList select all performance ,update selected threads array.@{
                ConversationListAdapter.removeSelectedState(mSelectedThreadId);
                /// @}
                /// M: Fix bug of inputmethod not disappear
                hideInputMethod();
                mWorkingMessage.discard();
                finish();
                break;
            case MENU_SEND:
                if (isPreparedForSending()) {
                    /// M: add for ip message, unread divider
                    mShowUnreadDivider = false;
                    /// M: Code analyze 028, Before sending message,check the recipients count
                    /// and add sub card selection dialog if multi sub cards exist.@{
                    updateSendButtonState(false);
                    checkRecipientsCount();
                    mSendButtonCanResponse = true;
                    /// @}
                }
                break;
            case MENU_SEARCH:
                onSearchRequested();
                break;
            case MENU_DELETE_THREAD:
                /// M: Code analyze 012, add for multi-delete @{
                Intent it = new Intent(this, MultiDeleteActivity.class);
                it.putExtra("thread_id", mConversation.getThreadId());
                startActivityForResult(it, REQUEST_CODE_FOR_MULTIDELETE);
                /// @}
                break;

            case android.R.id.home:
                mIsCheckObsolete = true;
                mConversation.setDiscardThreadId(mConversation.getThreadId());
            case MENU_CONVERSATION_LIST:
                exitComposeMessageActivity(new Runnable() {
                    @Override
                    public void run() {
                        goToConversationList();
                    }
                });
                break;
            case MENU_CALL_RECIPIENT:
                dialRecipient(false);
                break;
            /// M: Code analyze 061, Add video call menu.
            case MENU_CALL_RECIPIENT_BY_VT:
                dialRecipient(true);
                break;
            /// @}
            /// M: google jb.mr1 patch, group mms
            case MENU_GROUP_PARTICIPANTS: {
                Intent intent = new Intent(this, RecipientListActivity.class);
                intent.putExtra(THREAD_ID, mConversation.getThreadId());
                startActivityForResult(intent, REQUEST_CODE_GROUP_PARTICIPANTS);
                break;
            }
            case MENU_VIEW_CONTACT: {
                // View the contact for the first (and only) recipient.
                ContactList list = getRecipients();
                if (list.size() == 1 && list.get(0).existsInDatabase()) {
                    Uri contactUri = list.get(0).getUri();
                    Intent intent = new Intent(Intent.ACTION_VIEW, contactUri);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                    startActivity(intent);
                }
                break;
            }
            case MENU_ADD_ADDRESS_TO_CONTACTS:
                mAddContactIntent = item.getIntent();
                startActivityForResult(mAddContactIntent, REQUEST_CODE_ADD_CONTACT);
                break;
            case MENU_PREFERENCES: {
                Intent settingIntent = null;
                if (MmsConfig.isSupportTabSetting()) {
                    settingIntent = new Intent(this, MessageTabSettingActivity.class);
                } else {
                    settingIntent = new Intent(this, SettingListActivity.class);
                }
                startActivityIfNeeded(settingIntent, -1);
                break;
            }
            case MENU_DEBUG_DUMP:
                mWorkingMessage.dump();
                Conversation.dump();
                LogTag.dumpInternalTables(this);
                break;
            case MENU_ADD_TO_CONTACTS: {
                mAddContactIntent = ConversationList.createAddContactIntent(getRecipients().get(0).getNumber());
                startActivityForResult(mAddContactIntent, REQUEST_CODE_ADD_CONTACT);
                break;
            }
            /// M: show contact detail or create new contact. @{
            case MENU_SHOW_CONTACT:
            case MENU_CREATE_CONTACT:
                hideInputMethod();
                mQuickContact.onClick(mActionBarCustomView);
                break;
            /// @}
            case MENU_ADD_MMS_CC:
                mWorkingMessage.setMmsCc(null, true);
                showRecipientsCcEditor(true);
                updateSendButtonState();
                mInputMethodManager.showSoftInput(getCurrentFocus(),
                    InputMethodManager.SHOW_IMPLICIT);
                mRecipientsCcEditor.requestFocus();
                break;
            default:
                MmsLog.d(TAG, "unkown option.");
                break;
        }

        return true;
    }

    /// M: Add Jump to another chat in converaged inbox mode. @{
    private void jumpToJoynChat(boolean isToJoynChat, IpMessage ipMessage) {
        String number;
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SENDTO);
        MmsLog.d(TAG, "jumpToJoynChat ipMessage = " + ipMessage);
        if (isToJoynChat) {
            number = IpMessageConsts.JOYN_START + mConversation.getRecipients().get(0).getNumber();
            intent.putExtra("chatmode", IpMessageConsts.ChatMode.JOYN);
        } else {
            number = (mConversation.getRecipients().get(0).getNumber()).substring(4);
            intent.putExtra("chatmode", IpMessageConsts.ChatMode.XMS);
        }

        Uri uri = Uri.parse("smsto: " + number);
        intent.setData(uri);

        if (ipMessage != null) {
            if (ipMessage.getType() == IpMessageType.TEXT) {
                intent.putExtra("sms_body", ((IpTextMessage) ipMessage).getBody());
            } else if (ipMessage.getType() >= IpMessageType.PICTURE
                    && ipMessage.getType() <= IpMessageType.UNKNOWN_FILE) {
                intent.setAction(Intent.ACTION_SEND);
                intent.setComponent(new ComponentName("com.android.mms",
                        "com.android.mms.ui.ComposeMessageActivity"));
                intent = setIntent(intent, ipMessage);
            }
        }
        startActivity(intent);
    }
    /// @}

//    static class SystemProperties { // TODO, temp class to get unbundling working
//        static int getInt(String s, int value) {
//            return value;       // just return the default value or now
//        }
//    }

    private void addAttachment(int type, boolean append) {
        if (mMmsUtils != null
            && !mMmsUtils.allowSafeDraft(this, MmsConfig.getDeviceStorageFullStatus(), true,
                IMmsUtilsExt.TOAST_TYPE_FOR_ATTACH)) {
            return;
        }
        mWaitingAttachment = true;
        // Calculate the size of the current slide if we're doing a replace so the
        // slide size can optionally be used in computing how much room is left for an attachment.
        int currentSlideSize = 0;
        SlideshowModel slideShow = mWorkingMessage.getSlideshow();

        /// M: Code analyze 025, Add video or audio attachment and check the attachment size.@{
        if (append) {
            mAppendAttachmentSign = true;
        } else {
            mAppendAttachmentSign = false;
        }
        /// @}

        if (slideShow != null) {
            if (!(append && type == AttachmentTypeSelectorAdapter.ADD_SOUND)) {
                WorkingMessage.removeThumbnailsFromCache(slideShow);
            }
            SlideModel slide = slideShow.get(0);
            currentSlideSize = slide == null ? 0 : slide.getSlideSize();
        }
        /// M: Code analyze 025, Add video or audio attachment and check the attachment size.@{
        if ((type != AttachmentTypeSelectorAdapter.ADD_SLIDESHOW) && (type != AttachmentTypeSelectorAdapter.ADD_VCARD)
            && (!checkSlideCount(mAppendAttachmentSign))) {
            return;
        }
        /// @}
        switch (type) {
            case AttachmentTypeSelectorAdapter.ADD_IMAGE:
                MessageUtils.selectImage(this, REQUEST_CODE_ATTACH_IMAGE);
                break;

            case AttachmentTypeSelectorAdapter.TAKE_PICTURE: {
                MessageUtils.capturePicture(this, REQUEST_CODE_TAKE_PICTURE);
                break;
            }

            case AttachmentTypeSelectorAdapter.ADD_VIDEO:
                MessageUtils.selectVideo(this, REQUEST_CODE_ATTACH_VIDEO);
                break;

            case AttachmentTypeSelectorAdapter.RECORD_VIDEO: {
                /// M: Code analyze 025, Add video or audio attachment and check the attachment size.@{
                long sizeLimit = 0;
                if (mAppendAttachmentSign) {
                    sizeLimit = computeAttachmentSizeLimitForAppen(slideShow);
                } else {
                    sizeLimit = computeAttachmentSizeLimit(slideShow, currentSlideSize);
                }
                /// M: fix bug ALPS01221817 & ALPS01231411, Should subtract mText Size
                if ((slideShow == null || !mAppendAttachmentSign)
                        && !TextUtils.isEmpty(mWorkingMessage.getText())) {
                    int textSize = mWorkingMessage.getText().toString().getBytes().length;
                    sizeLimit -= textSize;
                }
                if (sizeLimit > MIN_SIZE_FOR_CAPTURE_VIDEO) {
                    MessageUtils.recordVideo(this, REQUEST_CODE_TAKE_VIDEO, sizeLimit);
                } else {
                    Toast.makeText(this,
                            getString(R.string.space_not_enough),
                            Toast.LENGTH_SHORT).show();
                }
                /// @}
            }
            break;

            case AttachmentTypeSelectorAdapter.ADD_SOUND:
                /// M: Code analyze 018, Add ringtone for sound attachment.  @{
                //MessageUtils.selectAudio(this, REQUEST_CODE_ATTACH_SOUND);
                AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
                alertBuilder.setTitle(getString(R.string.add_music));
                String[] items = new String[2];
                items[0] = getString(R.string.attach_ringtone);
                items[1] = getString(R.string.attach_sound);
                alertBuilder.setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                MessageUtils.selectRingtone(ComposeMessageActivity.this, REQUEST_CODE_ATTACH_RINGTONE);
                                break;
                            case 1:
                                if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                                    Toast.makeText(ComposeMessageActivity.this,
                                                    getString(R.string.Insert_sdcard), Toast.LENGTH_LONG).show();
                                    return;
                                }
                                MessageUtils.selectAudio(ComposeMessageActivity.this, REQUEST_CODE_ATTACH_SOUND);
                                break;
                            default:
                                break;
                        }
                    }
                });
                alertBuilder.create().show();
               /// @}
                break;

            case AttachmentTypeSelectorAdapter.RECORD_SOUND:
                /// M: Code analyze 025, Add video or audio attachment and check the attachment size.@{
                long sizeLimit = 0;
                if (mAppendAttachmentSign) {
                    sizeLimit = computeAttachmentSizeLimitForAppen(slideShow);
                } else {
                    sizeLimit = computeAttachmentSizeLimit(slideShow, currentSlideSize);
                }
                /// M: fix bug ALPS01221817 & ALPS01231411, Should subtract mText Size
                if ((slideShow == null || !mAppendAttachmentSign)
                        && !TextUtils.isEmpty(mWorkingMessage.getText())) {
                    int textSize = mWorkingMessage.getText().toString().getBytes().length;
                    sizeLimit -= textSize;
                }
                if (sizeLimit > ComposeMessageActivity.MIN_SIZE_FOR_RECORD_AUDIO) {
                    MessageUtils.recordSound(this, REQUEST_CODE_RECORD_SOUND, sizeLimit);
                } else {
                    Toast.makeText(this, getString(R.string.space_not_enough_for_audio), Toast.LENGTH_SHORT).show();
                }
                /// @}
                break;

            case AttachmentTypeSelectorAdapter.ADD_SLIDESHOW:
                editSlideshow();
                break;
            /// M: Code analyze 019, Add vcard attachment.@{
            case AttachmentTypeSelectorAdapter.ADD_VCARD:
                Intent intent = new Intent("android.intent.action.contacts.list.PICKMULTICONTACTS");
                intent.setType(Contacts.CONTENT_TYPE);
                startActivityForResult(intent, REQUEST_CODE_ATTACH_VCARD);
                break;
            /// @}
            /// M: Code analyze 020, Add vcalendar attachment.  @{
            case AttachmentTypeSelectorAdapter.ADD_VCALENDAR:
                Intent i = new Intent("android.intent.action.CALENDARCHOICE");
                i.setType("text/x-vcalendar");
                i.putExtra("request_type", 0);
                startActivityForResult(i, REQUEST_CODE_ATTACH_VCALENDAR);
                break;
            /// @}
            default:
                break;
        }
    }

    public static long computeAttachmentSizeLimit(SlideshowModel slideShow, int currentSlideSize) {
        // Computer attachment size limit. Subtract 1K for some text.
        /// M: Code analyze 003,  Set or get max mms size.
        long sizeLimit = MmsConfig.getUserSetMmsSizeLimit(true) - SlideshowModel.SLIDESHOW_SLOP;
        /// @}
        if (slideShow != null) {
            sizeLimit -= slideShow.getCurrentSlideshowSize();

            // We're about to ask the camera to capture some video (or the sound recorder
            // to record some audio) which will eventually replace the content on the current
            // slide. Since the current slide already has some content (which was subtracted
            // out just above) and that content is going to get replaced, we can add the size of the
            // current slide into the available space used to capture a video (or audio).
            sizeLimit += currentSlideSize;
        }
        return sizeLimit;
    }

    private void showAddAttachmentDialog(final boolean append) {
        /// M: Code analyze 009,Show attachment dialog . Create a new class to @{
        mSoloAlertDialog.show(append);
        /// @}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        if (LogTag.VERBOSE) {
            log("requestCode=" + requestCode + ", resultCode=" + resultCode + ", data=" + data);
//        }
        mWaitingForSubActivity = false;          // We're back!

        /// M : FIX CR : ALPS01795853 @{
        mWorkingMessage.setTruntoChooseAttach(false);
        ///@}
        
        // M: fix bug ALPS00354728
        boolean mNeedAppendAttachment = true;

        MmsLog.d(TAG, "onActivityResult mAppendAttachmentSign : " + mAppendAttachmentSign);
        if (mAppendAttachmentSign) {
            mNeedAppendAttachment = true;
        } else if (!mAppendAttachmentSign) {
            mNeedAppendAttachment = false;
        }

        /// @}
        if (mWorkingMessage.isFakeMmsForDraft()) {
            // We no longer have to fake the fact we're an Mms. At this point we are or we aren't,
            // based on attachments and other Mms attrs.
            mWorkingMessage.removeFakeMmsForDraft();
        }

        /// M: add for ipmessage
        if (IpMessageUtils.getIpMessagePlugin(this).isActualPlugin()) {
            onIpMsgActivityResult(ComposeMessageActivity.this, requestCode, resultCode, data);
        }

         /// M: Code analyze 012, add for multi-delete @{
         if (requestCode == REQUEST_CODE_FOR_MULTIDELETE && resultCode == RESULT_OK) {
             ContactList recipients = mConversation.getRecipients();
             mConversation = Conversation.upDateThread(ComposeMessageActivity.this, mConversation.getThreadId(), false);
             mIsStartMultiDeleteActivity = false;
             if (mConversation.getMessageCount() <= 0 || mConversation.getThreadId() <= 0) {
                 mMsgListAdapter.changeCursor(null);
                 if (needSaveDraft() && (recipients != null)) {
                     makeDraftEditable(recipients);
                 } else {
                     mWorkingMessage.discard();

                     /// M: Rebuild the contacts cache now that a thread and its associated unique
                     /// M: recipients have been deleted.
                     Contact.init(getApplicationContext());

                     /// M: Make sure the conversation cache reflects the threads in the DB.
                     Conversation.init(getApplicationContext());
                     /// M: fix bug for ConversationList select all performance ,update selected threads array.@{
                     ConversationListAdapter.removeSelectedState(mSelectedThreadId);
                     /// @}
                     finish();
                 }
             }
            return;
        }
        /// @}
        if (requestCode == REQUEST_CODE_PICK && MmsConfig.isSmsEnabled(this)) {
            mWorkingMessage.asyncDeleteDraftSmsMessage(mConversation);
        }

        /// M: fix bug ALPS00490684, update group mms state from GROUP_PARTICIPANTS to setting @{
        if (requestCode == REQUEST_CODE_GROUP_PARTICIPANTS) {
            boolean multiRecipients = mConversation.getRecipients().size() > 1;
            boolean isGroupMms = MmsPreferenceActivity.getIsGroupMmsEnabled(ComposeMessageActivity.this)
                && multiRecipients;
            mMsgListAdapter.setIsGroupConversation(isGroupMms);
            mWorkingMessage.setHasMultipleRecipients(multiRecipients, true);
            mWorkingMessage.deleteGruoupMmsDraft();
        }
        /// @}

        if (requestCode == REQUEST_CODE_ADD_CONTACT) {
            // The user might have added a new contact. When we tell contacts to add a contact
            // and tap "Done", we're not returned to Messaging. If we back out to return to
            // messaging after adding a contact, the resultCode is RESULT_CANCELED. Therefore,
            // assume a contact was added and get the contact and force our cached contact to
            // get reloaded with the new info (such as contact name). After the
            // contact is reloaded, the function onUpdate() in this file will get called
            // and it will update the title bar, etc.
            if (!isRecipientsEditorVisible()) {
                if (mAddContactIntent != null) {
                    String address =
                        mAddContactIntent.getStringExtra(ContactsContract.Intents.Insert.EMAIL);
                    if (address == null) {
                        address =
                            mAddContactIntent.getStringExtra(ContactsContract.Intents.Insert.PHONE);
                    }
                    if (address != null) {
                        Contact contact = Contact.get(address, false);
                        if (contact != null) {
                            contact.reload();
                        }
                    }
                }
            }
        }

        if (resultCode != RESULT_OK && requestCode == REQUEST_CODE_CREATE_SLIDESHOW
                && mWorkingMessage != null) {
            mWorkingMessage.setForceUpdateThreadId(true);
        }

        if (resultCode != RESULT_OK) {
            if (LogTag.VERBOSE) log("bail due to resultCode=" + resultCode);
            mWaitingAttachment = false;
            return;
        }

        /// M: disable when non-default sms
        if (!MmsConfig.isSmsEnabled(this) && requestCode != REQUEST_CODE_ECM_EXIT_DIALOG
                && requestCode != REQUEST_CODE_MULTI_SAVE
                && requestCode != REQUEST_CODE_CREATE_SLIDESHOW) {
            Toast.makeText(ComposeMessageActivity.this,
                    R.string.compose_disabled_toast, Toast.LENGTH_SHORT).show();
            return;
        }

        switch (requestCode) {
            case REQUEST_CODE_CREATE_SLIDESHOW:
                if (data != null) {
                    mWaitingAttachment = false;
                    MmsLog.v(TAG, "begin REQUEST_CODE_CREATE_SLIDESHOW ");
                    WorkingMessage newMessage = WorkingMessage.load(this, data.getData());
                    int slideSize = 0;
                    if (newMessage != null && newMessage.getSlideshow() != null) {
                        slideSize = newMessage.getSlideshow().size();
                        MmsLog.v(TAG, "REQUEST_CODE_CREATE_SLIDESHOW newWorkingMessage Slideshow num = " + slideSize);
                    } else {
                        MmsLog.v(TAG, "REQUEST_CODE_CREATE_SLIDESHOW newWorkingMessage Slideshow = null or newMessage = null");
                    }

                    if (newMessage != null) {
                        /// M: Code analyze 053, If exist vcard attachment, move it before
                        /// creating a new slideshow.Because the Workingmessage object  has been
                        /// changed, reset subject and tell the convertion to user.@{
                        /// M: add for vcard, vcard is exclusive with other attaches, so remove them
                        if (newMessage.hasMediaAttachments()) {
                            newMessage.removeAllFileAttaches();
                        }
                        boolean isMmsBefore = mWorkingMessage.requiresMms();
                        newMessage.setSubject(mWorkingMessage.getSubject(), false);
                        /// M: support mms cc feature. OP09 requested.
                        newMessage.setMmsCc(mWorkingMessage.getMmsCc(), false);

                        /// M: fix bug ALPS01265824, need remove FileAttachment when text + attachmentSize > limit
                        if (newMessage.isRemoveFileAttachment()) {
                            int[] params = null;
                            int encodingType = SmsMessage.ENCODING_UNKNOWN;
                            if (MmsConfig.isEnableSmsEncodingType()) {
                                encodingType = MessageUtils.getSmsEncodingType(ComposeMessageActivity.this);
                            }
                            params = SmsMessage.calculateLength(newMessage.getText(), false, encodingType);
                            final int msgCount = params[0];
                            newMessage.setLengthRequiresMms(
                                    msgCount >= MmsConfig.getSmsToMmsTextThreshold(), false);

                            newMessage.removeAllFileAttaches();
                            newMessage.correctAttachmentState();
                        }

                        mWorkingMessage = newMessage;
                        //mWorkingMessage.setConversation(mConversation);  //move to load of WorkingMessage.
                        updateThreadIdIfRunning();
                        drawTopPanel(isSubjectEditorVisible());
                        updateSendButtonState();
                        invalidateOptionsMenu();

                        boolean isMmsAfter = mWorkingMessage.requiresMms();
                        if (isMmsAfter && !isMmsBefore) {
                            toastConvertInfo(true);
                        } else if (!isMmsAfter && isMmsBefore) {
                            toastConvertInfo(false);
                        } else if (!isMmsAfter && !isMmsBefore) {
                            mWorkingMessage.setForceUpdateThreadId(true);
                        }
                        /// @}

                        if (null != mRecipientsEditor) {
                            if (mWorkingMessage.hasSlideshow()) {
                                mRecipientsEditor.setImeActionLabel(getString(MessageResource.string.ime_action_done),
                                        EditorInfo.IME_ACTION_DONE);
                                mRecipientsEditor.setImeOptions(EditorInfo.IME_ACTION_DONE);
                            } else {
                                mRecipientsEditor.setImeActionLabel(getString(MessageResource.string.ime_action_next),
                                        EditorInfo.IME_ACTION_NEXT);
                                mRecipientsEditor.setImeOptions(EditorInfo.IME_ACTION_NEXT);
                            }
                        }
                    }
                }
                // M: fix bug ALPS00354728
                MmsLog.e(TAG, "In REQUEST_CODE_CREATE_SLIDESHOW ");
                MmsLog.e(TAG, "mNeedAppendAttachment = " + mNeedAppendAttachment);

                break;

            case REQUEST_CODE_TAKE_PICTURE: {
                // create a file based uri and pass to addImage(). We want to read the JPEG
                // data directly from file (using UriImage) instead of decoding it into a Bitmap,
                // which takes up too much memory and could easily lead to OOM.

                /// M: fix bug ALPS00408589
                String scrappath = TempFileProvider.getScrapPath(this);
                if (scrappath != null) {
                    File file = new File(scrappath);
                    Uri uri = Uri.fromFile(file);

                    // Remove the old captured picture's thumbnail from the cache
                    MmsApp.getApplication().getThumbnailManager().removeThumbnail(uri);

                    addImageAsync(uri, mNeedAppendAttachment);
                }
                break;
            }

            case REQUEST_CODE_ATTACH_IMAGE: {
                if (data != null) {
                    addImageAsync(data.getData(), mNeedAppendAttachment);
                }
                break;
            }
            case REQUEST_CODE_TAKE_VIDEO:
                Uri videoUri = TempFileProvider.renameScrapVideoFile(System.currentTimeMillis() + ".3gp", null, this);
                // Remove the old captured video's thumbnail from the cache
                MmsApp.getApplication().getThumbnailManager().removeThumbnail(videoUri);

                addVideoAsync(videoUri, mNeedAppendAttachment);      // can handle null videoUri
                break;

            case REQUEST_CODE_ATTACH_VIDEO:
                if (data != null) {
                    addVideoAsync(data.getData(), mNeedAppendAttachment);
                }
                break;

            case REQUEST_CODE_ATTACH_SOUND: {
                Uri uri = (Uri) data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                if (Settings.System.getUriFor(Settings.System.RINGTONE).equals(uri)) {
                    break;
                }
                addAudioAsync(data.getData(), mNeedAppendAttachment);
                break;
            }

            case REQUEST_CODE_RECORD_SOUND:
                if (data != null) {
                    addAudioAsync(data.getData(), mNeedAppendAttachment);
                }
                break;

            /// M: @{
            case REQUEST_CODE_ATTACH_RINGTONE:
                Uri uri = (Uri) data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                if (Settings.System.getUriFor(Settings.System.RINGTONE).equals(uri)) {
                    break;
                }
                addAudioAsync(uri, mNeedAppendAttachment);
                break;
            /// @}

            case REQUEST_CODE_ECM_EXIT_DIALOG:
                boolean outOfEmergencyMode = data.getBooleanExtra(EXIT_ECM_RESULT, false);
                if (outOfEmergencyMode) {
                    sendMessage(false);
                }
                break;

            case REQUEST_CODE_PICK:
                /// M: Code analyze 013, Get contacts from Contact app . @{
                if (data != null) {
                    if (mRecipientsEditor != null) {
                        processPickResult(data);
                    } else {
                        mIsRecipientHasIntentNotHandle = true;
                        mIntent = data;
                    }
                }
                misPickContatct = false;
                return;
                /// @}
            /// M: support mms cc feature. OP09 requested.
            case REQUEST_CODE_PICK_CC:
                if (data != null) {
                    processCCPickResult(data);
                    misPickContatct = false;
                }
                return;
            /// M: Code analyze 015, Add text vcard. @{
            case REQUEST_CODE_TEXT_VCARD:
                if (data != null) {
                    long[] contactIds = data.getLongArrayExtra("com.mediatek.contacts.list.pickcontactsresult");
                    addTextVCardAsync(contactIds);
                } else {
                    MmsLog.e(TAG, "data should not be null," + "requestCode=" + requestCode
                            + ", resultCode=" + resultCode + ", data=" + data);
                }
                misPickContatct = false;
                return;
            /// @}
            /// M: Code analyze 019, Add vcard attachment.@{
            case REQUEST_CODE_ATTACH_VCARD:
                asyncAttachVCardByContactsId(data, false);
                misPickContatct = false;
                isInitRecipientsEditor = false;
                return;
            /// @}
            /// M: Code analyze 020, Add vcalendar attachment.  @{
            case REQUEST_CODE_ATTACH_VCALENDAR:
                asyncAttachVCalendar(data.getData());
                misPickContatct = false;
                isInitRecipientsEditor = false;
                return;
            /// @}
            /// M: Code analyze 021, Copy all valid parts of the attachment(pdu) to SD card.
            /// This opeartor will be removed to a separate activity.  @{
            case REQUEST_CODE_MULTI_SAVE:
                boolean succeeded = false;
                if (data != null && data.hasExtra("multi_save_result")) {
                    succeeded = data.getBooleanExtra("multi_save_result", false);
                    int resId = succeeded ? R.string.copy_to_sdcard_success : R.string.copy_to_sdcard_fail;
                    Toast.makeText(ComposeMessageActivity.this, resId, Toast.LENGTH_SHORT).show();
                }
                return;
            /// @}
            default:
                if (LogTag.VERBOSE) log("bail due to unknown requestCode=" + requestCode);
                break;
        }
        /// M: @{
        isInitRecipientsEditor = false; /// why add this variable here???
    }

    /**
     * Handle duplicate selected contacts and put new selected contact to list.
     * @param mSelectContactsNumbers the result of selected contacts number.
     * @param editor the current recipientEditor.
     * @param contactsId the result of selected contacts id.
     * @param list the new select contacts.
     * @param allList all contacts.
     * @return whether has duplicate recipients.
     */
    private boolean processDuplicatePickResult(String mSelectContactsNumbers,
            RecipientsEditor editor, long[] contactsId,
            ContactList list, ContactList allList) {
        boolean isDuplicate = false;
        try {
            /// M: @{
            //list = ContactList.blockingGetByUris(uris);
            /// M: add for ip message
            /// M: To append recipients into RecipientsEditor, no need to load avatar,
            /// because Editor will query and notify avatar info to MMS later. If append
            /// 100 recipients, will saving almost 3s.
            Contact.sNeedLoadAvatar = false;
            ContactList selected = TextUtils.isEmpty(mSelectContactsNumbers) ?
                 ContactList.blockingGetByIds(contactsId) :
                     ContactList.getByNumbers(mSelectContactsNumbers, false, false);
            Contact.sNeedLoadAvatar = true;
            final List<String> numbers = editor.getNumbers();

            /** M: better merge strategy.
             * Avoid the use of mRecipientsEditor.contrcutionContactsFromInput()
             * all Contacts in selected list should be added.
             * */
            /// M: remove duplicated numbers and format
            List<String> selectedNumbers = Arrays.asList(selected.getProtosomaitcNumbers());
            if (selectedNumbers.size() < selected.size()) {
                isDuplicate = true;
            }
            String selectedNumberAfterFormat = "";
            if (numbers.size() > 0) {
                for (String number : numbers) {
                    if (!number.trim().equals("")) {
                        Contact c = Contact.get(number, false);
                        allList.add(c);
                    }
                }
                /// M: format existing numbers(remove "-" and " ")
                List<String> formatedNumbers = Arrays.asList(allList.getNumbers(true));
                for (String selectedNumber : selectedNumbers) {
                    selectedNumberAfterFormat = MessageUtils.parseMmsAddress(selectedNumber);
                    if (selectedNumberAfterFormat != null
                            && !selectedNumberAfterFormat.trim().equals("")) {
                        if (!formatedNumbers.contains(selectedNumberAfterFormat)) {
                            Contact c = Contact.get(selectedNumber, false);
                            list.add(c);
                        } else {
                            //M: ALPS01831885, fix contact cache error.
                            Contact c = Contact.get(selectedNumber, false);
                            c.removeFromCache();
                            isDuplicate = true;
                        }
                    }
                }
                allList.addAll(list);
            } else {
                for (String selectedNumber : selectedNumbers) {
                    selectedNumberAfterFormat = MessageUtils.parseMmsAddress(selectedNumber);
                    if (selectedNumberAfterFormat != null && !selectedNumber.trim().equals("")) {
                        Contact c = Contact.get(selectedNumber, false);
                        list.add(c);
                    }
                }
                allList.addAll(list);
            }
            /// @}
        } finally {
            Message msg = mUiHandler.obtainMessage();
            msg.what = MSG_DISMISS_CONTACT_PICK_DIALOG;
            mUiHandler.sendMessage(msg);
            return isDuplicate;
        }
    }

    private ProgressDialog mContactPickDialog;

    private Runnable mContactPickRunnable = new Runnable() {
        public void run() {
            if (mContactPickDialog != null) {
                mContactPickDialog.show();
            }
        }
    };

    private void processPickResult(final Intent data) {
        // The EXTRA_PHONE_URIS stores the phone's urls that were selected by user in the
        // multiple phone picker.
        /// M: Code analyze 013, Get contacts from Contact app . @{
        /*final Parcelable[] uris =
            data.getParcelableArrayExtra(Intents.EXTRA_PHONE_URIS);

        final int recipientCount = uris != null ? uris.length : 0;*/

        final long[] contactsId = data.getLongArrayExtra("com.mediatek.contacts.list.pickdataresult");
        //// M: Add in OP09 projcet as a common function ; @{
        String numbersSelectedFromRecent = mMmsComposePlugin.getNumbersFromIntent(data);

        if (numbersSelectedFromRecent == null || numbersSelectedFromRecent.length() < 1) {
        /// M: add for ip message {@
            numbersSelectedFromRecent = data.getStringExtra(IpMessageUtils.SELECTION_CONTACT_RESULT);
        }
        final String mSelectContactsNumbers = numbersSelectedFromRecent;
        /// @}
        Log.i(TAG, "processPickResult, data = " + data.toString() + ", contactsId = " + Arrays.toString(contactsId) + ", mSelectContactsNumbers = " + mSelectContactsNumbers);
        if ((contactsId == null || contactsId.length <= 0) && TextUtils.isEmpty(mSelectContactsNumbers)) {
            return;
        }
        int recipientCount = mRecipientsEditor.getRecipientCount();
        if (!TextUtils.isEmpty(mSelectContactsNumbers)) {
            recipientCount += mSelectContactsNumbers.split(";").length;
        } else {
            recipientCount += contactsId.length;
        }
        /// @}
        /// M: Code analyze 056,Now,the sms recipient limit is different from mms.
        /// We can set limit for sms or mms individually. @{
        final int recipientLimit = MmsConfig.getSmsRecipientLimit();
        /// @}
        if (recipientLimit != Integer.MAX_VALUE && recipientCount > recipientLimit) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.pick_too_many_recipients)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(getString(R.string.too_many_recipients, recipientCount, recipientLimit))
                    .setPositiveButton(android.R.string.ok, null)
                    .create().show();
            return;
        }

        /// M: @{
//        final Handler handler = new Handler();
        if (mContactPickDialog == null) {
            mContactPickDialog = new ProgressDialog(this);
            mContactPickDialog.setMessage(getText(R.string.adding_recipients));
            mContactPickDialog.setIndeterminate(true);
            mContactPickDialog.setCancelable(false);
        }

        // Only show the progress dialog if we can not finish off parsing the return data in 1s,
        // otherwise the dialog could flicker.
        mUiHandler.postDelayed(mContactPickRunnable, 500);
        new Thread(new Runnable() {
            public void run() {
                final ContactList list = new ContactList();
                final ContactList allList = new ContactList();
                final boolean isDuplicate = processDuplicatePickResult(mSelectContactsNumbers,
                        mRecipientsEditor, contactsId, list, allList);
                // TODO: there is already code to update the contact header widget and recipients
                // editor if the contacts change. we can re-use that code.
                final Runnable populateWorker = new Runnable() {
                    public void run() {
                        mConversation.setRecipients(allList);
                        if (list.size() > 0) {
                            // Fix ALPS01594370, if has attachment, recipient eidtor always on focus.
                            // And adding a recipient bebind the editable recipient is not allowed.
                            if ((mBottomPanel == null) || (mBottomPanel.getVisibility() != View.VISIBLE)) {
                                mRecipientsEditor.clearFocus();
                            }
                            mIsPopulatingRecipients = true;
                            mRecipientsEditor.populate(list);
                        }
                        if (mRecipientsEditor != null && isRecipientsEditorVisible()) {
                            mRecipientsEditor.requestFocus();
                        }
                        if (isDuplicate) {
                            Toast.makeText(ComposeMessageActivity.this, R.string.add_duplicate_recipients, Toast.LENGTH_SHORT).show();
                        }
                        /// M: Fix ipmessage bug @{
                        updateCurrentChatMode(null) ;
                        /// @}
                    }
                };
                mUiHandler.post(populateWorker);
            }
        }, "ComoseMessageActivity.processPickResult").start();
    }

    /**
     * Set CC pick contact result
     * @param data the result of select contacts.
     */
    private void processCCPickResult(final Intent data) {
        if (mRecipientsCcEditor == null) {
            Log.i(TAG, "mRecipientsCcEditor is null, cannot set selected contacts");
            return;
        }
        final long[] contactsId =
            data.getLongArrayExtra("com.mediatek.contacts.list.pickdataresult");
        //// M: Add in OP09 projcet as a common function ; @{
        String numbersSelectedFromRecent = mMmsComposePlugin.getNumbersFromIntent(data);

        if (numbersSelectedFromRecent == null || numbersSelectedFromRecent.length() < 1) {
            /// M: add for ip message {@
            numbersSelectedFromRecent =
                data.getStringExtra(IpMessageUtils.SELECTION_CONTACT_RESULT);
        }
        final String mSelectContactsNumbers = numbersSelectedFromRecent;
        /// @}
        Log.i(TAG, "processPickResult, data = " + data.toString()
                + ", contactsId = "
                + Arrays.toString(contactsId)
                + ", mSelectContactsNumbers = "
                + mSelectContactsNumbers);
        if ((contactsId == null || contactsId.length <= 0)
                && TextUtils.isEmpty(mSelectContactsNumbers)) {
            return;
        }

        // final Handler handler = new Handler();
        if (mContactPickDialog == null) {
            mContactPickDialog = new ProgressDialog(this);
            mContactPickDialog.setMessage(getText(R.string.adding_recipients));
            mContactPickDialog.setIndeterminate(true);
            mContactPickDialog.setCancelable(false);
        }

        // Only show the progress dialog if we can not finish off parsing the return data in 1s,
        // otherwise the dialog could flicker.
        mUiHandler.postDelayed(mContactPickRunnable, 500);
        new Thread(new Runnable() {
            public void run() {
                mUiHandler.post(new Runnable() {
                    public void run() {
                        final ContactList list = new ContactList();
                        final ContactList allList = new ContactList();
                        final boolean isDuplicate = processDuplicatePickResult(
                                mSelectContactsNumbers,
                                mRecipientsCcEditor,
                                contactsId,
                                list,
                                allList);
                        if (list.size() > 0) {
                            mRecipientsCcEditor.clearFocus();
                            mRecipientsCcEditor.populate(list);
                        }
                        mRecipientsCcEditor.requestFocus();
                        if (isDuplicate) {
                            Toast.makeText(ComposeMessageActivity.this,
                                    R.string.add_duplicate_recipients,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }, "ComoseMessageActivity.processCCPickResult").start();
    }

    private boolean mIsPopulatingRecipients = false;
    /// M: Code analyze 062, Resize image. @{
    private final ResizeImageResultCallback mResizeImageCallback = new ResizeImageResultCallback() {
        // TODO: make this produce a Uri, that's what we want anyway
        @Override
        public void onResizeResult(PduPart part, boolean append) {
            mNeedSaveAsMms = false;
            if (part == null) {
                MmsLog.d(TAG, "onResizeResult part == null");
                notifyCompressingDone();
                handleAddAttachmentError(WorkingMessage.UNKNOWN_ERROR, R.string.type_picture);
                return;
            }

            mWorkingMessage.setmResizeImage(true);
            Context context = ComposeMessageActivity.this;
            PduPersister persister = PduPersister.getPduPersister(context);
            int result;
            if (mWorkingMessage.isDiscarded()) {
                notifyCompressingDone();
                return;
            }
            Uri messageUri = mWorkingMessage.getMessageUri();
            if (null == messageUri) {
                try {
                    messageUri = mWorkingMessage.saveAsMms(true);
                } catch (IllegalStateException e) {
                    notifyCompressingDone();
                    MmsLog.e(TAG, e.getMessage() + ", go to ConversationList!");
                    goToConversationList();
                }
            }
            if (messageUri == null) {
                MmsLog.d(TAG, "onResizeResult messageUri == null");
                result = WorkingMessage.UNKNOWN_ERROR;
            } else {
                try {
                    /// M: it is modifying the mms draft, maybe interlaced with WorkingMessage.saveDraft!
                    Uri dataUri;
                    int mode;
                    synchronized (WorkingMessage.sDraftMmsLock) {
                        dataUri = persister.persistPart(part, ContentUris.parseId(messageUri), null);
                        mode = mWorkingMessage.sCreationMode;
                        mWorkingMessage.sCreationMode = 0;
                        result = mWorkingMessage.setAttachment(WorkingMessage.IMAGE, dataUri, append);
                        /// M: fix bug ALPS00914391, remove redundancy part
                        if (result != mWorkingMessage.OK && mWorkingMessage.getSlideshow() != null) {
                            try {
                                PduBody pb = mWorkingMessage.getSlideshow().toPduBody();
                                MessageUtils.updatePartsIfNeeded(mWorkingMessage.getSlideshow(),
                                        PduPersister.getPduPersister(context), messageUri, pb, null);
                                if (pb != null) {
                                    mWorkingMessage.getSlideshow().sync(pb);
                                }
                            }  catch (MmsException e) {
                                Log.e(TAG, "Cannot update the message: " + messageUri, e);
                            }
                        }

                    }
                    mWorkingMessage.sCreationMode = mode;
                    if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                        log("ResizeImageResultCallback: dataUri=" + dataUri);
                    }
                } catch (MmsException e) {
                    result = WorkingMessage.UNKNOWN_ERROR;
                }
            }

            /// M:added for bug ALPS00317889 begin,for not pop up alertDialog if
            // attachment size is reaching limited size
            if (!mShowDialogForMultiImage) {
                handleAddAttachmentError(result, R.string.type_picture);
            }
            if (result == WorkingMessage.MESSAGE_SIZE_EXCEEDED) {
                mShowDialogForMultiImage = true;
            }
            /// M:added for bug ALPS00317889 end
            if (result == WorkingMessage.OK) {
                try {
                    if (mWorkingMessage.saveAsMms(false) != null) {
                        mHasDiscardWorkingMessage = true;
                    }
                } catch (IllegalStateException e) {
                    MmsLog.e(TAG, e.getMessage() + ", go to ConversationList!");
                    notifyCompressingDone();
                    goToConversationList();
                }
                mNeedSaveDraftAfterStop = false;
            }
            notifyCompressingDone();
            mWaitingAttachment = false;
        }
    };
    /// @}

    private void handleAddAttachmentError(final int error, final int mediaTypeStringId) {
        if (error == WorkingMessage.OK) {
            return;
        }
        Log.d(TAG, "handleAddAttachmentError: " + error);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int titleId;
                int messageId;
                mWorkingMessage.removeFakeMmsForDraft();
                updateSendButtonState();

                MmsLog.d(TAG, "Error Code:" + error);
                switch(error) {
                /// M: Code analyze 063, For new feature ALPS00233419, Creation mode . @{
                case WorkingMessage.WARNING_TYPE:
                /// @}
                case WorkingMessage.UNKNOWN_ERROR:
                    Resources res = getResources();
                    String mediaType = res.getString(mediaTypeStringId);
                    String message = res.getString(R.string.error_add_attachment, mediaType);
                    Toast.makeText(ComposeMessageActivity.this, message, Toast.LENGTH_SHORT).show();
                    return;
                case WorkingMessage.UNSUPPORTED_TYPE:
                /// M: Code analyze 063, For new feature ALPS00233419, Creation mode . @{
                case WorkingMessage.RESTRICTED_TYPE:
                /// @}
                    titleId = R.string.unsupport_media_type;
                    messageId = R.string.select_different_media_type;
                    break;
                case WorkingMessage.MESSAGE_SIZE_EXCEEDED:
                    titleId = R.string.exceed_message_size_limitation;
		/*HQ_sunli 20151204 HQ01543494 begin*/
		if(SystemProperties.get("ro.hq.sms.attachment.limit").equals("1")){						
			switch (mediaTypeStringId) {
			case R.string.type_video:
			   messageId = R.string.failed_to_add_video_type;
                             break;
			case R.string.type_audio:
			   messageId = R.string.failed_to_add_audio_type;
                             break;
			case R.string.type_picture:
			   messageId = R.string.failed_to_add_picture_type;
                             break;
			default:
                           messageId = R.string.failed_to_add_image;
                             break;
			}
		}else
		/*HQ_sunli 20151204 HQ01543494 end*/
                    messageId = R.string.failed_to_add_image;
                    break;
                case WorkingMessage.IMAGE_TOO_LARGE:
                    titleId = R.string.failed_to_resize_image;
                    messageId = R.string.resize_image_error_information;
                    break;
                 /// M: Code analyze 063, For new feature ALPS00233419, Creation mode . @{
                case WorkingMessage.RESTRICTED_RESOLUTION:
                    titleId = R.string.select_different_media_type;
                    messageId = R.string.image_resolution_too_large;
                    break;
                /// @}
                default:
                    throw new IllegalArgumentException("unknown error " + error);
                }
                if (mErrorDialogShown) {
                    MessageUtils.showErrorDialog(ComposeMessageActivity.this, titleId, messageId,
                            0, 0);
                    mErrorDialogShown = false;
                }
            }
        });
    }

    /// M: Code analyze 064, Add image attachment. @{
    private void addImageAsync(final Uri uri, final boolean append) {
        mCompressingImage = true;
        getAsyncDialog().runAsync(new Runnable() {
            @Override
            public void run() {
                mShowDialogForMultiImage = false; /// M:added for bug ALPS00317889
                addImage(uri, append);
                if (mNeedSaveAsMms) {
                    saveAsMms(false);
                } else if (mNeedSaveDraftAfterStop) {
                    if ((!isRecipientsEditorVisible()) ||
                            (mRecipientsEditor.hasValidRecipient(mWorkingMessage.requiresMms()))) {
                        if (mMmsUtils != null
                            && mMmsUtils.allowSafeDraft(ComposeMessageActivity.this, MmsConfig.getDeviceStorageFullStatus(), false,
                                IMmsUtilsExt.TOAST_TYPE_FOR_SAVE_DRAFT)) {
                            saveDraft(true);
                            Log.v(TAG, "save draft if needed after compressing image after onStop and update MmsWidget");
                            MmsWidgetProvider.notifyDatasetChanged(getApplicationContext());
                        }
                    }
                }
             }
        }, null, R.string.adding_attachments_title);
    }

    private void addImage(final Uri uri, final boolean append) {
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("addImage: append=" + append + ", uri=" + uri);
        }
        mNeedSaveAsMms = true;
        int result = WorkingMessage.OK;
        try {
            if (append) {
                mWorkingMessage.checkSizeBeforeAppend();
            }
        } catch (ExceedMessageSizeException e) {
            result = WorkingMessage.MESSAGE_SIZE_EXCEEDED;
            notifyCompressingDone();
            handleAddAttachmentError(result, R.string.type_picture);
            mNeedSaveAsMms = false;
            return;
        }

        result = mWorkingMessage.setAttachment(WorkingMessage.IMAGE, uri, append);

        if (result != WorkingMessage.IMAGE_TOO_LARGE &&
                result != WorkingMessage.MESSAGE_SIZE_EXCEEDED) {
            mWaitingAttachment = false;
        }
        if (result == WorkingMessage.IMAGE_TOO_LARGE ||
            result == WorkingMessage.MESSAGE_SIZE_EXCEEDED) {
            MmsLog.d(TAG, "addImage: resize image " + uri);

            /// M: Adjust whether its a DRM IMAGE
            if (FeatureOption.MTK_DRM_APP) {
                if (!MessageUtils.checkUriContainsDrm(this, uri)) {
                    mToastCountForResizeImage++;
                    if (mToastCountForResizeImage == 1) {
                        MessageUtils.resizeImage(this, uri, mAttachmentEditorHandler, mResizeImageCallback, append,
                            true);
                    } else {
                        MessageUtils.resizeImage(this, uri, mAttachmentEditorHandler, mResizeImageCallback, append,
                            false);
                    }
                } else {
                    notifyCompressingDone();
                    handleAddAttachmentError(result, R.string.type_picture);
                    mNeedSaveAsMms = false;
                }
            } else {
                mToastCountForResizeImage++;
                if (mToastCountForResizeImage == 1) {
                    MessageUtils.resizeImage(this, uri, mAttachmentEditorHandler, mResizeImageCallback, append, true);
                } else {
                    MessageUtils.resizeImage(this, uri, mAttachmentEditorHandler, mResizeImageCallback, append, false);
                }
            }
            return;
        } else if (result == WorkingMessage.WARNING_TYPE) {
            mNeedSaveAsMms = false;
            notifyCompressingDone();
            runOnUiThread(new Runnable() {
                public void run() {
                    showConfirmDialog(uri, append, WorkingMessage.IMAGE, R.string.confirm_restricted_image);
                }
            });
            return;
        }
        notifyCompressingDone();
        if (result != WorkingMessage.OK) {
            mNeedSaveAsMms = false;
        }
        handleAddAttachmentError(result, R.string.type_picture);
    }
    /// @}

    /// M: Code analyze 065, Add video attachment. @{
    private void addVideoAsync(final Uri uri, final boolean append) {
        getAsyncDialog().runAsync(new Runnable() {
            @Override
            public void run() {
                addVideo(uri, append);
                saveAsMms(false);
           }
        }, null, R.string.adding_attachments_title);
    }

    private void addVideo(final Uri uri, final boolean append) {
        mNeedSaveAsMms = false;
        if (uri != null) {
            mNeedSaveAsMms = true;
            int result = WorkingMessage.OK;
            try {
                if (append) {
                    mWorkingMessage.checkSizeBeforeAppend();
                }
            } catch (ExceedMessageSizeException e) {
                result = WorkingMessage.MESSAGE_SIZE_EXCEEDED;
                handleAddAttachmentError(result, R.string.type_video);
                mNeedSaveAsMms = false;
                return;
            }
            result = mWorkingMessage.setAttachment(WorkingMessage.VIDEO, uri, append);
            if (result == WorkingMessage.WARNING_TYPE) {
                mNeedSaveAsMms = false;
                runOnUiThread(new Runnable() {
                    public void run() {
                        showConfirmDialog(uri, append, WorkingMessage.VIDEO, R.string.confirm_restricted_video);
                    }
                });
            } else {
                handleAddAttachmentError(result, R.string.type_video);
                if (result != WorkingMessage.OK) {
                    mNeedSaveAsMms = false;
                }
            }
        }
    }
    /// @}

    private void addAudioAsync(final Uri uri, final boolean append) {
        getAsyncDialog().runAsync(new Runnable() {
            @Override
            public void run() {
                addAudio(uri, append);
                saveAsMms(false);
            }
        }, null, R.string.adding_attachments_title);
    }

    /// M: remove unused method @{
    /*
    ///: Code analyze 067, Add audio attachment. @{
    private void addAudio(final Uri uri) {
        int result = WorkingMessage.OK;
        result = mWorkingMessage.setAttachment(WorkingMessage.AUDIO, uri, false);
        if (result == WorkingMessage.WARNING_TYPE) {
            runOnUiThread(new Runnable() {
                public void run() {
                    showConfirmDialog(uri, false, WorkingMessage.AUDIO, R.string.confirm_restricted_audio);
                }
            });
            return;
        }
        handleAddAttachmentError(result, R.string.type_audio);
    }
    /// @}
    */
    /// @}

    AsyncDialog getAsyncDialog() {
        if (mAsyncDialog == null) {
            mAsyncDialog = new AsyncDialog(this);
        }
        return mAsyncDialog;
    }

    public boolean getForwordingState() {
        return mForwardingMessage;
    }

    /// M: Code analyze 017, Handle forwarded message.(see:forwardMessage())@{
    private boolean handleForwardedMessage() {
        Intent intent = getIntent();

        // If this is a forwarded message, it will have an Intent extra
        // indicating so.  If not, bail out.
        if (!mForwardMessageMode) {
            mForwardingMessage = false;
            return false;
        }
        mForwardingMessage = true;

        Uri uri = intent.getParcelableExtra("msg_uri");

        if (Log.isLoggable(LogTag.APP, Log.DEBUG)) {
            log("handle forwarded message " + uri);
        }

        if (uri != null) {
            mWorkingMessage = WorkingMessage.load(this, uri);
            mWorkingMessage.setSubject(intent.getStringExtra("subject"), false);

            SlideshowModel mSlideshowModel = mWorkingMessage.getSlideshow();
            if (mSlideshowModel != null) {
                int mSsmSize = mSlideshowModel.size();
                for (int index = 0; index < mSsmSize; index++) {
                    SlideModel mSlideModel = mSlideshowModel.get(index);
                    if (mSlideModel != null) {
                        if (mSlideModel.hasText()) {
                            TextModel mTextModel = mSlideModel.getText();
                            String textChar = mTextModel.getText();
                            long textLength = textChar.length();
                            if (textLength > MmsConfig.getMaxTextLimit()) {
                                mTextModel.setText(textChar.substring(0, MmsConfig.getMaxTextLimit()));
                            }
                        }
                    }
                }
            }
        } else {
            String smsAddress = null;
            if (intent.hasExtra(SMS_ADDRESS)) {
                smsAddress = intent.getStringExtra(SMS_ADDRESS);
                if (smsAddress != null) {
                   //TODO need re-coding
                   //mRecipientsEditor.addRecipient(smsAddress, true);
                }
            }
            mWorkingMessage.setText(intent.getStringExtra(SMS_BODY));
        }
        /// M:
        if (intent.getBooleanExtra(FORWARD_IPMESSAGE, false)) {
            long ipMsgId = intent.getLongExtra(IP_MESSAGE_ID, 0);
            mIpMessageDraft = IpMessageUtils.getMessageManager(this).getIpMsgInfo(ipMsgId);
            mIpMessageDraft.setId(0);
            if (mIpMessageDraft.getType() == IpMessageType.PICTURE
                    && !TextUtils.isEmpty(((IpImageMessage) mIpMessageDraft).getCaption())) {
                mWorkingMessage.setText(((IpImageMessage) mIpMessageDraft).getCaption());
            } else if (mIpMessageDraft.getType() == IpMessageType.VOICE
                    && !TextUtils.isEmpty(((IpVoiceMessage) mIpMessageDraft).getCaption())) {
                mWorkingMessage.setText(((IpVoiceMessage) mIpMessageDraft).getCaption());
            } else if (mIpMessageDraft.getType() == IpMessageType.VIDEO
                    && !TextUtils.isEmpty(((IpVideoMessage) mIpMessageDraft).getCaption())) {
                mWorkingMessage.setText(((IpVideoMessage) mIpMessageDraft).getCaption());
            }
            saveIpMessageForAWhile(mIpMessageDraft);
        }

        // let's clear the message thread for forwarded messages
        mMsgListAdapter.changeCursor(null);

        return true;
    }
    /// @}

    // Handle send actions, where we're told to send a picture(s) or text.
    private boolean handleSendIntent() {
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return false;
        }
        /// M: Code analyze 066,Handle intent. @{
        /// M: add for saveAsMms
        mWorkingMessage.setConversation(mConversation);
        final String mimeType = intent.getType();
        String action = intent.getAction();
        MmsLog.i(TAG, "Get mimeType: " + mimeType);
        MmsLog.i(TAG, "Get action: " + action);
        /// M: disable when non-default sms
        if (!mIsSmsEnabled) {
            Toast.makeText(ComposeMessageActivity.this, R.string.compose_disabled_toast, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (Intent.ACTION_SEND.equals(action)) {
            if (extras.containsKey(Intent.EXTRA_STREAM)) {
                final Uri uri = (Uri)extras.getParcelable(Intent.EXTRA_STREAM);
                if (uri == null) {
                    MmsLog.i(TAG, "handleSendIntent uri == null");
                    return false;
                }
                if (mimeType.equals("text/plain")) {
                    String fileName = "";
                    if (uri != null) {
                        if (uri.getScheme() != null && uri.getScheme().equals("file")) {
                            String mUriStr = Uri.decode(uri.toString());
                            fileName = mUriStr.substring(mUriStr.lastIndexOf("/") + 1, mUriStr.length());
                        } else {
                            Cursor c = mContentResolver.query(uri, null, null, null, null);
                            if (c != null) {
                                try {
                                    if (c.getCount() == 1 && c.moveToFirst()) {
                                        fileName = c.getString(c.getColumnIndex(Images.Media.DISPLAY_NAME));
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    c.close();
                                }
                            }
                        }
                    }

                    String mMessage = this.getString(R.string.failed_to_add_media, fileName);
                    Toast.makeText(this, mMessage, Toast.LENGTH_SHORT).show();
                    return false;
                }
                getAsyncDialog().runAsync(new Runnable() {
                    @Override
                    public void run() {
                        /// M: fix bug ALPS00397146, removeThumbnailManager uri
                        // (Content://media/external/images/media/) when it rotated
                        String fileName = "";
                        int degree = 0;
                        String uriStr = uri.toString();
                        if (uriStr.startsWith("content://media/external/images/media")) {
                            Cursor c = mContentResolver.query(uri, null, null, null, null);
                            if (c != null) {
                                try {
                                    if (c.getCount() == 1 && c.moveToFirst()) {
                                        fileName = c.getString(c.getColumnIndex(Images.Media.DISPLAY_NAME));
                                        degree = c.getInt(c.getColumnIndex(Images.Media.ORIENTATION));
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    c.close();
                                }
                            }

                            if (sDegreeMap != null && sDegreeMap.containsKey(uriStr)) {
                                if (sDegreeMap.get(uriStr).intValue() != degree) {
                                    Uri thumbnailUri =
                                        Uri.parse(uriStr + ThumbnailManager.FLAG_FNAME + fileName);
                                    MmsApp.getApplication().getThumbnailManager()
                                                                    .removeThumbnail(thumbnailUri);
                                    sDegreeMap.remove(uriStr);
                                    sDegreeMap.put(uriStr, degree);
                                }
                            } else if (sDegreeMap != null) {
                                sDegreeMap.put(uriStr, degree);
                            }
                        }
                        /// @}
                        Uri tempUri = uri;
                        if (uri.toString().contains(TempFileProvider.SCRAP_VIDEO_URI.toString())) {
                            File file = new File(TempFileProvider.getScrapVideoPath(ComposeMessageActivity.this));
                            if (file != null && file.exists()) {
                                tempUri = Uri.fromFile(file);
                            }
                        }

                        String scheme = tempUri.getScheme();
                        if (scheme != null && scheme.equals("file")) {
                            /// M: fix bug ALPS01400468, workaround for GoogleDrive Audio Uri
                            if (uri.toString().contains(AudioModel.sAuthorityForGoogleDrive)) {
                                AudioModel.sTypeForGoogleDrive = mimeType;
                            }
                            addFileAttachment(mimeType, tempUri, false);
                        } else {
                            addAttachment(mimeType, tempUri, false);
                        }
                        SlideshowModel slides = mWorkingMessage.getSlideshow();
                        if (slides != null && (slides.size() > 0 || slides.sizeOfFilesAttach() > 0)) {
                            mWorkingMessage.saveAsMms(false);
                        }
                        MessageUtils.deleteVCardTempFiles(getApplicationContext(), mVCardFiles);
                    }
                }, null, R.string.adding_attachments_title);
                intent.setAction(SIGN_CREATE_AFTER_KILL_BY_SYSTEM);
                return true;
            } else if (extras.containsKey(Intent.EXTRA_TEXT)) {
                mWorkingMessage.setText(extras.getString(Intent.EXTRA_TEXT));
                intent.setAction(SIGN_CREATE_AFTER_KILL_BY_SYSTEM);
                return true;
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) &&
                extras.containsKey(Intent.EXTRA_STREAM)) {
            SlideshowModel slideShow = mWorkingMessage.getSlideshow();
            final ArrayList<Parcelable> uris = extras.getParcelableArrayList(Intent.EXTRA_STREAM);
            int currentSlideCount = slideShow != null ? slideShow.size() : 0;
            int importCount = uris.size();
            if (importCount + currentSlideCount > SlideshowEditor.MAX_SLIDE_NUM) {
                importCount = Math.min(SlideshowEditor.MAX_SLIDE_NUM - currentSlideCount,
                        importCount);
            }

            // Attach all the pictures/videos asynchronously off of the UI thread.
            // Show a progress dialog if adding all the slides hasn't finished
            // within half a second.
            final int numberToImport = importCount;
            MmsLog.i(TAG, "numberToImport: " + numberToImport);
            final WorkingMessage msg = mWorkingMessage;
            getAsyncDialog().runAsync(new Runnable() {
                @Override
                public void run() {
                    mToastCountForResizeImage = 0;
                    for (int i = 0; i < numberToImport; i++) {
                        Parcelable uri = uris.get(i);

                        String scheme = ((Uri) uri).getScheme();
                        String authority = ((Uri) uri).getAuthority();
                        if (scheme != null && scheme.equals("file")) {
                            // change "file://..." Uri to "Content://...., and attemp to add this attachment"
                            /// M: fix bug ALPS604911, change MmsContentType when share multi-file from FileManager @{
                            String type = MessageUtils.getContentType((Uri) uri);
                            if (TextUtils.isEmpty(type)) {
                                type = mimeType;
                            }
                            /// @}
                            addFileAttachment(type, (Uri) uri, true);
                        } else if (authority != null && (authority.contains("com.android.email")
                                || (authority.equals(MediaStore.AUTHORITY) && mimeType != null && mimeType.contains("*")))) {
                            addAttachment(mimeType, (Uri) uri, true);
                        } else {
                            String type = MessageUtils.queryContentType(ComposeMessageActivity.this, (Uri) uri);
                            if (TextUtils.isEmpty(type)) {
                                type = mimeType;
                            }
                            addAttachment(type, (Uri) uri, true);
                        }
                    }
                    mToastCountForResizeImage = 0;
                    SlideshowModel slides = mWorkingMessage.getSlideshow();
                    if (slides != null && (slides.size() > 0 || slides.sizeOfFilesAttach() > 0)) {
                        mWorkingMessage.saveAsMms(false);
                    }
                    MessageUtils.deleteVCardTempFiles(getApplicationContext(), mVCardFiles);
                }
            }, null, R.string.adding_attachments_title);
            intent.setAction(SIGN_CREATE_AFTER_KILL_BY_SYSTEM);
            return true;
        } else if (SIGN_CREATE_AFTER_KILL_BY_SYSTEM.equals(action)) {
        /// @}
            return true;
        }
        return false;
    }

    // mVideoUri will look like this: content://media/external/video/media
    private static final String mVideoUri = Video.Media.getContentUri("external").toString();
    // mImageUri will look like this: content://media/external/images/media
    private static final String mImageUri = Images.Media.getContentUri("external").toString();

    private void addAttachment(String type, Uri uri, boolean append) {
        if (uri != null) {
            // When we're handling Intent.ACTION_SEND_MULTIPLE, the passed in items can be
            // videos, and/or images, and/or some other unknown types we don't handle. When
            // a single attachment is "shared" the type will specify an image or video. When
            // there are multiple types, the type passed in is "*/*". In that case, we've got
            // to look at the uri to figure out if it is an image or video.
            boolean wildcard = "*/*".equals(type);
            MmsLog.i(TAG, "Got send intent mimeType :" + type);
            if (type.startsWith("image/") || (wildcard && uri.toString().startsWith(mImageUri))) {
                addImage(uri, append);
            } else if (type.startsWith("video/") ||
                    (wildcard && uri.toString().startsWith(mVideoUri))) {
                addVideo(uri, append);
            }
             /// M: Code analyze 067, Add audio attachment. @{
            else if (type.startsWith("audio/") || type.equals("application/ogg")
                || (wildcard && uri.toString().startsWith(mAudioUri))) {
                addAudio(uri, append);
            /// @}
            /// M: Code analyze 019, Add vcard attachment.  @{
            } else if (type.equalsIgnoreCase("text/x-vcard")) {
                VCardAttachment va = new VCardAttachment(ComposeMessageActivity.this);
                String fileName = va.getVCardFileNameByUri(uri);
                setFileAttachment(fileName, WorkingMessage.VCARD, false);
                mVCardFiles.add(fileName);
             /// M: Code analyze 020, Add vcalendar attachment.  @{
            } else if (type.equalsIgnoreCase("text/x-vcalendar")) {
                attachVCalendar(uri);
            } else {
                handleAddAttachmentError(WorkingMessage.UNSUPPORTED_TYPE, R.string.type_audio);
            }
            /// @}
        }
    }

    private String getResourcesString(int id, String mediaName) {
        Resources r = getResources();
        return r.getString(id, mediaName);
    }

    private void drawBottomPanel() {
        // Reset the counter for text editor.
        /// M: @{
        mDrawBottomPanel = false;
        /// M: remove Google default code
        // Reset the counter for text editor.
        //resetCounter();

        if (mWorkingMessage.hasSlideshow()) {
            if (mWorkingMessage.getIsUpdateAttachEditor()) {
                mBottomPanel.setVisibility(View.GONE);
                if (mSubjectTextEditor != null) {
                    //mSubjectTextEditor.setNextFocusDownId(-1);
                    mSubjectTextEditor.setImeOptions(EditorInfo.IME_ACTION_DONE);
                    mSubjectTextEditor.setImeActionLabel(getText(com.android.internal.R.string.ime_action_done), EditorInfo.IME_ACTION_DONE);
                }
                /// M: for OP09
				/*HQ_zhangjing 2015-11-02 modified for CQ HQ01478873 */
                //mMmsComposePlugin.hideDualButtonPanel();
                mAttachmentEditor.update(mWorkingMessage);
                mAttachmentEditor.requestFocus();
            } else {
                MmsLog.d(TAG, "drawBottomPanel, isUpdateAttachEditor == false");
            }
            if (mRecipientsEditor != null) {
                mRecipientsEditor.setEnabled(mIsSmsEnabled);
                mRecipientsEditor.setFocusableInTouchMode(mIsSmsEnabled);
                mRecipientsEditor.setIsTouchable(mIsSmsEnabled);
            }
            if (mSubjectTextEditor != null) {
                mSubjectTextEditor.setEnabled(mIsSmsEnabled);
                mSubjectTextEditor.setFocusableInTouchMode(mIsSmsEnabled);
            }
            if (mRecipientsPicker != null) {
                mRecipientsPicker.setEnabled(mIsSmsEnabled);
            }
            setCCRecipientEditorStatus(mIsSmsEnabled);
            return;
        }
        mAttachmentEditor.update(mWorkingMessage);
        updateTextEditorHeightInFullScreen();
        /// @}
        mBottomPanel.setVisibility(View.VISIBLE);
        if (mSubjectTextEditor != null) {
            //mSubjectTextEditor.setNextFocusDownId(R.id.embedded_text_editor);
            mSubjectTextEditor.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            mSubjectTextEditor.setImeActionLabel(getText(com.android.internal.R.string.ime_action_next), EditorInfo.IME_ACTION_NEXT);
        }
        //// M: For OP09;
			/*HQ_zhangjing 2015-11-02 modified for CQ HQ01478873 */
            //mMmsComposePlugin.showDualButtonPanel();
        CharSequence text = mWorkingMessage.getText();

        // TextView.setTextKeepState() doesn't like null input.
        mTextEditor.removeTextChangedListener(mTextEditorWatcher);
        if (mIsSmsEnabled) {
            mTextEditor.addTextChangedListener(mTextEditorWatcher);
            if (text != null) {
                mTextEditor.setTextKeepState(text);
                /// M: @{
                try {
					/*HQ_zhangjing  add for al812 to add sms signature begin */
					//mTextEditor.setSelection(mTextEditor.getText().toString().length());
					if(getSharedPreferences(MessageUtils.DEFAULT_MMS_PREFERENCE_NAME, MODE_WORLD_READABLE).getBoolean(GeneralPreferenceActivity.KEY_SIGNATURE,false)){
					   drawBottomEditText();
					}else{
						mTextEditor.setSelection(mTextEditor.getText().toString().length());
					}
					/*HQ_zhangjing  add for al812 to add sms signature end */
                } catch (IndexOutOfBoundsException e) {
                	/// guoxiaolong for monkey test crash @{
                	if(mTextEditor.getText().toString().length() > 0) {
                		mTextEditor.setSelection(mTextEditor.getText().toString().length() - 1);
                	}
                	/// @}
                }
                /// @}
            } else {
                mTextEditor.setText("");
            }
        } else {
            mTextEditor.setText("");
            mTextEditor.setHint(R.string.sending_disabled_not_default_app);
            mTextEditor.setFocusable(false);
        }
        mTextEditor.setEnabled(mIsSmsEnabled);
        mTextEditor.setFocusableInTouchMode(mIsSmsEnabled);

        if (mRecipientsEditor != null) {
            mRecipientsEditor.setEnabled(mIsSmsEnabled);
            mRecipientsEditor.setFocusableInTouchMode(mIsSmsEnabled);
            mRecipientsEditor.setIsTouchable(mIsSmsEnabled);
        }
        if (mSubjectTextEditor != null) {
            mSubjectTextEditor.setEnabled(mIsSmsEnabled);
            mSubjectTextEditor.setFocusableInTouchMode(mIsSmsEnabled);
        }
        if (mRecipientsPicker != null) {
            mRecipientsPicker.setEnabled(mIsSmsEnabled);
        }
        setCCRecipientEditorStatus(mIsSmsEnabled);
        /// M: add for character counter
        // Reset the counter for text editor.
        updateCounter(mWorkingMessage.getText(), 0, 0, 0);
    }

	/*HQ_zhangjing  add for al812 to add sms signature begin */
	private void drawBottomEditText(){
		
		CharSequence text = mWorkingMessage.getText(); 
		String defaultSignature = getResources().getString(R.string.default_signature);
        String mSignatureStr = "\n"+getSharedPreferences(MessageUtils.DEFAULT_MMS_PREFERENCE_NAME, MODE_WORLD_READABLE).getString("key_signature_info",defaultSignature);
        int len = text.length();
		int count = mSignatureStr.length();
	    if(mWorkingMessage.hasText()||mWorkingMessage.hasAttachment()||mWorkingMessage.hasSubject()){
		 return;
            }
        if(len >= count ){
        	 String subtext = text.subSequence(text.length() - count, text.length()).toString();
             if(mSignatureStr.equals(subtext)){
			 	mTextEditor.setSelection(text.length()-count);
             }else{
                 mTextEditor.setTextKeepState(text+mSignatureStr);
 				mTextEditor.setSelection(len);
 		     }
        }else{
                mTextEditor.setTextKeepState(text+mSignatureStr);
				mTextEditor.setSelection(len);
		}
        
	}
    /*HQ_zhangjing  add for al812 to add sms signature end */

    private void drawTopPanel(boolean showSubjectEditor) {
        /// M: why ? @{
        //boolean showingAttachment = mAttachmentEditor.update(mWorkingMessage);
        //mAttachmentEditorScrollView.setVisibility(showingAttachment ? View.VISIBLE : View.GONE);
        //mAttachmentEditorScrollView.setVisibility(showingAttachment ? View.VISIBLE : View.GONE);
        if (!(isRecipientsEditorVisible() && isRecipientsCcEditorVisible())) {
            showRecipientsCcEditor(mWorkingMessage.hasMmsCc());
        }

        boolean isHasSubject = false;
        if (mWorkingMessage == null) {
            isHasSubject = false;
        } else {
            isHasSubject = mWorkingMessage.hasSubject();
        }
        boolean isDeleteMode = false;
        if (mMsgListAdapter == null) {
            isDeleteMode = false;
        } else {
            isDeleteMode = mMsgListAdapter.mIsDeleteMode;
        }
        showSubjectEditor((showSubjectEditor || isHasSubject) && !isDeleteMode);
        mAttachmentEditor.update(mWorkingMessage);
        updateTextEditorHeightInFullScreen();
        /// @}
    }

    //==========================================================
    // Interface methods
    //==========================================================

    @Override
    public void onClick(View v) {
    	final View mView = v;
      /*add by lihaizhou for lihaizhou for forbid send Msg while ChildMode is on and the switch of Forbid send Msg is on at 2015-08-05 by begin */
      if (isProhibitSendSmsmms()) {
       Toast.makeText(ComposeMessageActivity.this, R.string.Prohibit_Send, Toast.LENGTH_SHORT).show();
       return;
       }
        /*add by lihaizhou for lihaizhou for forbid send Msg while ChildMode is on and the switch of Forbid send Msg is on at 2015-08-05 by end */

         /*add by lihaizhou for lihaizhou for ChildMode at 2015-07-23 by end*/  
       
        /// M: Code analyze 028, Before sending message,check the recipients count
        /// and add sub card selection dialog if multi sub cards exist.@{
        /*if ((v == mSendButtonSms || v == mSendButtonMms) && isPreparedForSending()){
            confirmSendMessageIfNeeded();
        }
        */
        /// M: add for ip message send button
		/*HQ_zhangjing add for al812 mms ui begin*/
        //if (v == mSendButtonSms || v == mSendButtonMms || v == mSendButtonIpMessage) {
        else if (v == mSub1SendButton || v == mSub2SendButton 
				|| v == mSendButtonSms || v == mSendButtonMms || v == mSendButtonIpMessage ) {
            if (mSendButtonCanResponse) {
                ///M: WFC: Show pop-up, if condition satisfy @ {
                //if (SystemProperties.get("ro.mtk_wfc_support").equals("1")) {
                    if (showWfcSendButtonPopUp()) return;
                //}
                /// @}
                mSendButtonCanResponse = false;
                if (isPreparedForSending()) {
					/*HQ_zhangjing 2015-11-12 modified for CQ HQ01455134 remove the invalid recipient begin*/
                	Log.d("invalid_lipeng","showInvalidRecipintsDlg:::"+showInvalidRecipintsDlg);
                	//if( true && mRecipientsEditor != null  || SystemProperties.get("ro.hq.mms.cancel.invalid.dialog").equals("1")   ){
					if( !showInvalidRecipintsDlg && mRecipientsEditor != null ){
						boolean isMms = false;
						if( mWorkingMessage != null ){
							isMms = mWorkingMessage.requiresMms();
							
						}
						
						if ( (mRecipientsEditor.hasInvalidRecipient(isMms))
							|| ( isRecipientsCcEditorVisible() && mRecipientsCcEditor.hasInvalidRecipient(true))) {
							Log.d(TAG,"ignore the invalid recipients and send direct");
							checkConditionsAndSendMessage(true);
							return;
						}
					}
					/*HQ_zhangjing 2015-11-12 modified for CQ HQ01455134 remove the invalid recipient begin*/
					if(mRecipientsEditor != null && SystemProperties.get("ro.hq.sms.remove.invalid").equals("1")){
						boolean isMms = false;
						if( mWorkingMessage != null ){
							isMms = mWorkingMessage.requiresMms();
							List<String> numbers = mRecipientsEditor.getNumbersFromChipWatcher();
							for (String number : numbers) { 
								if(!mRecipientsEditor.isNumMatches(number,isMms) ){
									AlertDialog.Builder dialogBuilder = null;
								    AlertDialog dialog = null;
								    
			                    	Log.d("invalid_lipeng","ro.hq.sms.remove.invalid");
									String title = getResourcesString(R.string.invalid_recipient,number);
									dialogBuilder = new AlertDialog.Builder(this);
								    dialogBuilder.setCancelable(false);
								    dialogBuilder.setTitle(title);

								    dialogBuilder.setPositiveButton(android.R.string.ok, 
								    	new AlertDialog.OnClickListener() {
								    	public void onClick(DialogInterface dialog, int which) {
								    	Log.d(TAG,"invalid number dialog dismiss");
								    		dialog.dismiss(); 
								    		mSendButtonCanResponse = true;
								    }
								   });	
								    					    
								    dialog = dialogBuilder.create();
								    //dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
								    dialog.show();
								    return;
								}
							}
						}
					}
                    
					
                	if (!(FeatureOption.HQ_MMS_SEND_EMPTY_MESSAGE)
                			                          || mWorkingMessage.hasAttachment()
                			                          || mWorkingMessage.hasText()) {
                        /// M: add for ip message, unread divider
                        mShowUnreadDivider = false;
                        /// M: Since sending message here, why not disable button 'Send'??
                        updateSendButtonState(false);
    					if(mSubInfoList.size() > 1){
    						if( v == mSub1SendButton ){
    							mSelectedSimId = (int) mSubInfoList.get(0).getSubscriptionId();
    						}else if( v == mSub2SendButton ){
    							mSelectedSimId = (int) mSubInfoList.get(1).getSubscriptionId();
    						}else{
								mSelectedSimId = (int) mSubInfoList.get( getForbiddenSimcard() -1 ).getSubscriptionId();
    						}
    					}else{
    						mSelectedSimId = (int) mSubInfoList.get(0).getSubscriptionId();
    					}
    					/*HQ_zhangjing add for al812 mms ui end */
                        checkRecipientsCount();
                        mUiHandler.sendEmptyMessageDelayed(MSG_RESUME_SEND_BUTTON, RESUME_BUTTON_INTERVAL);   
                	}else{
                		//modify by wangmingyue for HQ01549012 begin
                		if("1".equals(SystemProperties.get("ro.hq.dialog.ok.cancle"))){
                			AlertDialog mDialog = new AlertDialog.Builder(this)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setTitle(R.string.send_empty_message_title)
                            .setMessage(R.string.send_empty_message_alert)
                            .setPositiveButton(R.string.send_empty_message_no,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            // TODO Auto-generated method
                                            // stub
                                            // Since sending message here, why not disable button 'Send'??
                                        	mSendButtonCanResponse = true;
                                            dialog.dismiss();
                                        }
                                    })
                            .setNegativeButton(R.string.send_empty_message_yes,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            // TODO Auto-generated method
                                            // stub
                                        	mShowUnreadDivider = false;
                                            updateSendButtonState(false);
                                            // simSelection();
                                            if(mSubInfoList.size() > 1){
                        						if( mView == mSub1SendButton ){
                        							mSelectedSimId = (int) mSubInfoList.get(0).getSubscriptionId();
												/*HQ_zhangjing 2015-11-02 modified for CQ  HQ01482542 begin*/
                        						}else if( mView == mSub2SendButton ){
                        							mSelectedSimId = (int) mSubInfoList.get(1).getSubscriptionId();
                        						}else{
													mSelectedSimId = (int) mSubInfoList.get( getForbiddenSimcard() -1 ).getSubscriptionId();
                        						}
												/*HQ_zhangjing 2015-11-02 modified for CQ  HQ01482542 end*/
                        					}else{
                        						mSelectedSimId = (int) mSubInfoList.get(0).getSubscriptionId();
                        					}
                                            checkRecipientsCount();
                                           mUiHandler.sendEmptyMessageDelayed(MSG_RESUME_SEND_BUTTON, RESUME_BUTTON_INTERVAL);
                                            dialog.dismiss();
                                        }
                                    }).show();
                           mDialog.setOnKeyListener(new android.content.DialogInterface.OnKeyListener(){  
                               @Override  
                               public boolean onKey(DialogInterface dialog, int keyCode,KeyEvent event) {  
                                   switch (keyCode) {  
                                       case KeyEvent.KEYCODE_BACK:  
                                           Log.i(TAG,"KEYCODE_BACK");
                                           mSendButtonCanResponse = true;
                                           dialog.dismiss();  
                                           return true;  
                                   }  
                                   return false;  
                               }  
                           });
                		} else {
                			
                			AlertDialog mDialog = new AlertDialog.Builder(this)
                			.setIcon(android.R.drawable.ic_dialog_alert)
                			.setTitle(R.string.send_empty_message_title)
                			.setMessage(R.string.send_empty_message_alert)
                			.setPositiveButton(R.string.send_empty_message_yes,
                					new DialogInterface.OnClickListener() {
                				@Override
                				public void onClick(DialogInterface dialog, int which) {
                					// TODO Auto-generated method
                					// stub
                					// Since sending message here, why not disable button 'Send'??
                							mShowUnreadDivider = false;
                					updateSendButtonState(false);
                					// simSelection();
                					if(mSubInfoList.size() > 1){
                						if( mView == mSub1SendButton ){
                							mSelectedSimId = (int) mSubInfoList.get(0).getSubscriptionId();
                							/*HQ_zhangjing 2015-11-02 modified for CQ  HQ01482542 begin*/
                						}else if( mView == mSub2SendButton ){
                							mSelectedSimId = (int) mSubInfoList.get(1).getSubscriptionId();
                						}else{
                							mSelectedSimId = (int) mSubInfoList.get( getForbiddenSimcard() -1 ).getSubscriptionId();
                						}
                						/*HQ_zhangjing 2015-11-02 modified for CQ  HQ01482542 end*/
                					}else{
                						mSelectedSimId = (int) mSubInfoList.get(0).getSubscriptionId();
                					}
                					checkRecipientsCount();
                					mUiHandler.sendEmptyMessageDelayed(MSG_RESUME_SEND_BUTTON, RESUME_BUTTON_INTERVAL);
                					dialog.dismiss();
                				}
                			})
                			.setNegativeButton(R.string.send_empty_message_no,
                					new DialogInterface.OnClickListener() {
                				@Override
                				public void onClick(DialogInterface dialog, int which) {
                					// TODO Auto-generated method
                					// stub
                					mSendButtonCanResponse = true;
                					dialog.dismiss();
                				}
                			}).show();
                			mDialog.setOnKeyListener(new android.content.DialogInterface.OnKeyListener(){  
                				@Override  
                				public boolean onKey(DialogInterface dialog, int keyCode,KeyEvent event) {  
                					switch (keyCode) {  
                					case KeyEvent.KEYCODE_BACK:  
                						Log.i(TAG,"KEYCODE_BACK");
                						mSendButtonCanResponse = true;
                						dialog.dismiss();  
                						return true;  
                					}  
                					return false;  
                				}  
                			});
                		}
                    }
                	//modify by wangmingyue for HQ01549012 end
                	}      
            }
        /// @}
        } else if ((v == mRecipientsPicker)) {
            /// M: support mms cc feature. OP09 feature.
            if (isPickRecipientsCc()) {
                if (mRecipientsCcEditor.getRecipientCount() >= RECIPIENTS_LIMIT_CC) {
                    Toast.makeText(ComposeMessageActivity.this, R.string.cannot_add_recipient, Toast.LENGTH_SHORT).show();
                } else {
                    addContacts(RECIPIENTS_LIMIT_CC - mRecipientsCcEditor.getNumbers().size(),
                            REQUEST_CODE_PICK_CC);
                }
                return;
            }

             /// M: Code analyze 013, Get contacts from Contact app . @{
             //launchMultiplePhonePicker();
            if (recipientCount() >= RECIPIENTS_LIMIT_FOR_SMS) {
                Toast.makeText(ComposeMessageActivity.this, R.string.cannot_add_recipient, Toast.LENGTH_SHORT).show();
            } else if (IpMessageUtils.getServiceManager(this).isFeatureSupported(FeatureId.CONTACT_SELECTION)) {
                try {
                    Intent intent = new Intent(RemoteActivities.CONTACT);
                    intent.putExtra(RemoteActivities.KEY_REQUEST_CODE, REQUEST_CODE_IPMSG_PICK_CONTACT);
                    intent.putExtra(RemoteActivities.KEY_TYPE, SelectContactType.ALL);
                    IpMessageUtils.startRemoteActivityForResult(this, intent);
                } catch (ActivityNotFoundException e) {
                    misPickContatct = false;
                    Toast.makeText(this, this.getString(R.string.no_application_response), Toast.LENGTH_SHORT).show();
                    MmsLog.e(IPMSG_TAG, e.getMessage());
                }
            } else {
                /// M: fix bug ALPS00444752, dis-clickble when showing ContactPicker
                if (!mShowingContactPicker) {
                    addContacts(mRecipientsEditor != null ? (RECIPIENTS_LIMIT_FOR_SMS - mRecipientsEditor.getNumbers().size()) : RECIPIENTS_LIMIT_FOR_SMS, REQUEST_CODE_PICK);
                }
            }
             /// @}
        }
    }

    /// M: fix bug ALPS00444752, set false to enable to Show ContactPicker
    private boolean mShowingContactPicker = false;

    private void launchMultiplePhonePicker() {
        Intent intent = new Intent(Intents.ACTION_GET_MULTIPLE_PHONES);
        intent.addCategory("android.intent.category.DEFAULT");
        intent.setType(Phone.CONTENT_TYPE);
        // We have to wait for the constructing complete.
        ContactList contacts = mRecipientsEditor.constructContactsFromInput(true);
        int urisCount = 0;
        Uri[] uris = new Uri[contacts.size()];
        urisCount = 0;
        for (Contact contact : contacts) {
            if (Contact.CONTACT_METHOD_TYPE_PHONE == contact.getContactMethodType()) {
                    uris[urisCount++] = contact.getPhoneUri();
            }
        }
        if (urisCount > 0) {
            intent.putExtra(Intents.EXTRA_PHONE_URIS, uris);
        }
        startActivityForResult(intent, REQUEST_CODE_PICK);
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (event != null) {
            // if shift key is down, then we want to insert the '\n' char in the TextView;
            // otherwise, the default action is to send the message.
            if (!event.isShiftPressed()) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    return false;
                }
                /// M: Code analyze 028, Before sending message,check the recipients count
                /// and add sub card selection dialog if multi sub cards exist.@{
                if (isPreparedForSending()) {
                    checkRecipientsCount();
                } else {
                    unpreparedForSendingAlert();
                    }
                /// @}
                return true;
            }
            return false;
        }
        /// M: Code analyze 028, Before sending message,check the recipients count
        /// and add sub card selection dialog if multi sub cards exist.@{
        if (isPreparedForSending()) {
            //confirmSendMessageIfNeeded();
            checkRecipientsCount();
        } else {
            unpreparedForSendingAlert();
        }
        /// @}
        return true;
    }

    private final TextWatcher mTextEditorWatcher = new TextWatcher() {
        private int mStart;

        /// M: fix bug ALPS00612093, postDelay for ANR
        private Runnable mUpdateRunnable = new Runnable() {
            public void run() {
                updateSendButtonState();
            }
        };

        private Runnable mRunnable = new Runnable() {
            public void run() {
                Toast.makeText(ComposeMessageActivity.this, R.string.dialog_sms_limit,
                        Toast.LENGTH_SHORT).show();
            }
        };

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            mBeforeTextChangeString = s.toString();
            MmsLog.d(IPMSG_TAG, "beforeTextChanged count = " + count + " after =" + after);
            /* HQ_sunli HQ01503603 20151117 begin*/
             if((mWorkingMessage!=null)&&(mTextCounter.getVisibility() != View.VISIBLE)&&(mTextCounterForDouble.getVisibility() != View.VISIBLE)){
	        mTextCounter.setVisibility(View.VISIBLE);
		    //mTextCounterForDouble.setVisibility(View.VISIBLE);
		    mTextCounter.setText( getMmsSize() );
		    mTextCounterForDouble.setText( getMmsSize() );
	          }
            /* HQ_sunli HQ01503603 20151117 end*/
            //for after = 0, it means we are deleting characters in the text editor
            if (IpMessageUtils.getIpMessagePlugin(ComposeMessageActivity.this).isActualPlugin()
                    && IpMessageUtils.getServiceManager(ComposeMessageActivity.this)
                            .isFeatureSupported(IpMessageConsts.FeatureId.PARSE_EMO_WITHOUT_ACTIVATE)) {
                if (mTextEditor.checkIsMenuItemClicked()) {
                    return;
                }
                if (after == 0) {
                    if (count == 1) {
                        Editable edit = mTextEditor.getEditableText();
                        int length = edit.length();
                        int cursor = mTextEditor.getSelectionStart();
                        if (length == 0 || cursor == 0) {
                            return;
                        }
                        ImageSpan[] spans = edit.getSpans(0, cursor, ImageSpan.class);
                        ImageSpan span = null;
                        int index = 0;
                        if (null != spans && spans.length != 0) {
                            span = spans[spans.length - 1];
                            index = edit.getSpanEnd(span);
                        }
                        MmsLog.d(IPMSG_TAG, "beforeTextChanged cursor = " + cursor + " index = " + index);
                    }
                }
            }
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // This is a workaround for bug 1609057. Since onUserInteraction() is
            // not called when the user touches the soft keyboard, we pretend it was
            // called when textfields changes.  This should be removed when the bug
            // is fixed.
            onUserInteraction();

            mWorkingMessage.setText(s);
            /// M: fix bug ALPS00712509, show toast when paste many word and > 300k
            if (mWorkingMessage.getIsExceedSize()) {
                mWorkingMessage.setIsExceedSize(false);
                mUiHandler.removeCallbacks(mRunnable);
                mUiHandler.postDelayed(mRunnable, 200);
            }
            /// M: @{
            mAttachmentEditor.onTextChangeForOneSlide();
            /// @}

            /// M: fix bug ALPS00612093, postDelay for ANR
            mUiHandler.removeCallbacks(mUpdateRunnable);
            mUiHandler.postDelayed(mUpdateRunnable, 100);

            updateCounter(s, start, before, count);
            mStart = start;
            /// M: @{
            //ensureCorrectButtonHeight();
            /// @}
        }

        @Override
        public void afterTextChanged(Editable s) {
            MmsLog.d(IPMSG_TAG, "afterTextChanged");
            if (mIsIpServiceEnabled && !TextUtils.isEmpty(mChatModeNumber)) {
                if (s.length() > 0) {
                    MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "send chat mode, typing");
                    IpMessageUtils.getChatManager(ComposeMessageActivity.this)
                        .sendChatMode(mChatModeNumber, ContactStatus.TYPING);
                } else {
                    MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "send chat mode, stop typing");
                    IpMessageUtils.getChatManager(ComposeMessageActivity.this)
                        .sendChatMode(mChatModeNumber, ContactStatus.STOP_TYPING);
                }
            }
            /// M: remove "typing" feature for a while
//            if (mIsIpServiceEnabled && !TextUtils.isEmpty(mChatModeNumber)) {
//                if (TextUtils.isEmpty(mBeforeTextChangeString) && s.length() > 0) {
//                    mIpMsgHandler.post(mSendTypingRunnable);
//                    mLastSendTypingStatusTimestamp = System.currentTimeMillis();
//                } else {
//                    long currentTimeStamp = System.currentTimeMillis();
//                    if ((currentTimeStamp - mLastSendTypingStatusTimestamp) > 3000) {
//                        mIpMsgHandler.post(mSendTypingRunnable);
//                        mLastSendTypingStatusTimestamp = currentTimeStamp;
//                    }
//                }
//
//                mIpMsgHandler.removeCallbacks(mSendStopTypingRunnable);
//                if (s.length() == 0) {
//                    mIpMsgHandler.postDelayed(mSendStopTypingRunnable, 3000);
//                } else {
//                    mIpMsgHandler.postDelayed(mSendStopTypingRunnable, 20000);
//                }
//            }
        }
    };

    /**
     * Ensures that if the text edit box extends past two lines then the
     * button will be shifted up to allow enough space for the character
     * counter string to be placed beneath it.
     */
    /*** M: remove Google default code
    private void ensureCorrectButtonHeight() {
        int currentTextLines = mTextEditor.getLineCount();
        if (currentTextLines <= 2) {
            mTextCounter.setVisibility(View.GONE);
        }
        else if (currentTextLines > 2 && mTextCounter.getVisibility() == View.GONE) {
            // Making the counter invisible ensures that it is used to correctly
            // calculate the position of the send button even if we choose not to
            // display the text.
            mTextCounter.setVisibility(View.INVISIBLE);
        }
    }
    */

    private final TextWatcher mSubjectEditorWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            mWorkingMessage.setSubject(s, true);
            if (s != null && TextUtils.getTrimmedLength(s) > 0) {
                /// M: Code analyze 032, According to the message state to update text counter.@{
                mTextCounter.setVisibility(View.GONE);
                // / @}
                // updateSendButtonState();
            }
            // / M: for fixed ALPS00562999,when change subject to null,update send button.@{
            updateSendButtonState();
            // / @}
        }

        @Override
        public void afterTextChanged(Editable s) {
            String subjectString = s.toString();
            if (subjectString.length() == 0) {
                Log.d(TAG, "[mSubjectEditorWatcher.afterTextChanged] subject lenght is 0,"
                    + "current mms or sms : " + mWorkingMessage.requiresMms());
                if (!mWorkingMessage.requiresMms()) {
                    if (mConversation != null && mWorkingMessage != null) {
                        mWorkingMessage.asyncDeleteDraftMmsMessage(mConversation);
                        mWorkingMessage.clearConversation(mConversation, true);
                    }
                }
            }
        }
    };

    //==========================================================
    // Private methods
    //==========================================================

    /**
     * Initialize all UI elements from resources.
     */
    private void initResourceRefs() {
        /// M: Code analyze 004, Set max height for text editor. @{
        mHeightChangedLinearLayout = (HeightChangedLinearLayout) findViewById(R.id.changed_linear_layout);
        mHeightChangedLinearLayout.setLayoutSizeChangedListener(mLayoutSizeChangedListener);
        /// @}
        mMsgListView = (MessageListView) findViewById(R.id.history);
        mMsgListView.setDivider(null);      // no divider so we look like IM conversation.
        mMsgListView.setDividerHeight(getResources().getDimensionPixelOffset(R.dimen.ipmsg_message_list_divier_height));

        // called to enable us to show some padding between the message list and the
        // input field but when the message list is scrolled that padding area is filled
        // in with message content
        mMsgListView.setClipToPadding(false);

        /** M: 4.1  used this code.
        mMsgListView.setOnSizeChangedListener(new OnSizeChangedListener() {
            public void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
                // The message list view changed size, most likely because the keyboard
                // appeared or disappeared or the user typed/deleted chars in the message
                // box causing it to change its height when expanding/collapsing to hold more
                // lines of text.
                smoothScrollToEnd(false, height - oldHeight);
            }
        });
        */

        /// M: turn off children clipping because we draw the border outside of our own
        /// M: bounds at the bottom.  The background is also drawn in code to avoid drawing
        /// M: the top edge.
        mMsgListView.setClipChildren(false);

        mBottomPanel = findViewById(R.id.bottom_panel);
        mTextEditor = (EnhanceEditText) findViewById(R.id.embedded_text_editor);
        /// M: @{
        //  mTextEditor.setOnEditorActionListener(this);
        /// @}
        mTextEditor.removeTextChangedListener(mTextEditorWatcher);
        mTextEditor.addTextChangedListener(mTextEditorWatcher);
        mTextEditor.setFilters(new InputFilter[] {
                new TextLengthFilter(MmsConfig.getMaxTextLimit())});
        mTextEditor.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                MmsLog.d(IPMSG_TAG, "mTextEditor.onKey(): is keyCode KEYCODE_DEL ?= " + (keyCode == KeyEvent.KEYCODE_DEL)
                    + ", is event ACTION_DOWN ?= " + (event.getAction() == KeyEvent.ACTION_DOWN));
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                    String s = ((EditText) v).getText().toString();
                    int index = ((EditText) v).getSelectionStart();
                    MmsLog.d(IPMSG_TAG, "mTextEditor.onKey(): is text empty ?=" + TextUtils.isEmpty(s)
                        + ", index = " + index);
                    if (TextUtils.isEmpty(s) || index == 0) {
                        clearIpMessageDraft();
                    }
                }
                return false;
            }
        });
        mTextEditor.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (IpMessageUtils.getIpMessagePlugin(ComposeMessageActivity.this).isActualPlugin()
                        && IpMessageUtils.getServiceManager(ComposeMessageActivity.this)
                                .isFeatureSupported(IpMessageConsts.FeatureId.PARSE_EMO_WITHOUT_ACTIVATE)) {
                    if (mShowKeyBoardFromShare) {
                        showSharePanel(false);
                    }
                    if (mShowKeyBoardFromShare) {
                        updateFullScreenTextEditorHeight();
                    }
                } else {
                    if (mShowKeyBoardFromShare) {
                        showSharePanel(false);
                        updateFullScreenTextEditorHeight();
                    }
                }
                return false;
            }
        });

        mTextEditor.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right,
                    int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                MmsLog.d(IPMSG_TAG, "mTextEditor onLayoutChange mUpdateForScrnOrientationChanged = " + mUpdateForScrnOrientationChanged);
                if (mUpdateForScrnOrientationChanged) {
                    mUiHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            updateCounter(mWorkingMessage.getText(), 0, 0, 0);
                            /// M: Add for correct selection cursor position
                            mTextEditor.updateHandleView();
                        }
                    }, 1000);
                    mUpdateForScrnOrientationChanged = false;
                }
            }
        });

        mTextCounter = (TextView) findViewById(R.id.text_counter);
        mTextCounterForDouble = (TextView) findViewById(R.id.text_counter_for_double);
        mSendButtonMms = (TextView) findViewById(R.id.send_button_mms);
        mSendButtonSms = (ImageButton) findViewById(R.id.send_button_sms);
        mSendButtonMms.setOnClickListener(this);
        mSendButtonSms.setOnClickListener(this);
		
		/*HQ_zhangjing add for al812 mms ui begin */
		mSub1SendButton  =	(TextView)findViewById(R.id.sub0_send_button);
        mSub1SendButton.setOnClickListener(this);
        mTimeView = (View) findViewById(R.id.time_line_view);
		
		mSub2SendButton = (TextView)findViewById(R.id.sub1_send_button);
        mSub2SendButton.setOnClickListener(this);
		refreshSendBtnText();
		if (TelephonyManager.getDefault().isMultiSimEnabled()) {
	    Log.d(TAG,"support multi sim card");

		  mSub1SendButton.setVisibility(View.VISIBLE);
		  mSub2SendButton.setVisibility(View.VISIBLE);
		  //button_with_counter view should gone but need add counter view
		  }else{
		  //button_with_counter view should show 
		  }
		/*HQ_zhangjing add for al812 mms ui end */
		
        mTopPanel = findViewById(R.id.recipients_subject_linear);
        mTopPanel.setFocusable(false);
        mAttachmentEditor = (AttachmentEditor) findViewById(R.id.attachment_editor);
        mAttachmentEditor.setHandler(mAttachmentEditorHandler);
        //mAttachmentEditorScrollView = findViewById(R.id.attachment_editor_scroll_view);
        mQuickContact = (MmsQuickContactBadge) findViewById(R.id.avatar);
        mWallPaper = (ImageView) findViewById(R.id.wall_paper);
        /// M: For OP09
		/*HQ_zhangjing 2015-11-02 modified for CQ HQ01478873 begin*/
        /*if (MmsConfig.isDualSendButtonEnable()) {
            mMmsComposePlugin.initDualSendButtonLayout(this, (LinearLayout) this.findViewById(R.id.button_with_counter),
                (TextView) findViewById(R.id.ct_text_counter));
        }*/
		/*HQ_zhangjing 2015-11-02 modified for CQ HQ01478873 end*/
    }
     
   
    private void confirmDeleteDialog(OnClickListener listener, boolean locked) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        /// M: Code analyze 027,Add for deleting one message.@{
        // Set different title and icon for locked message.
        builder.setTitle(locked ? R.string.confirm_dialog_locked_title :
            R.string.confirm_dialog_title);
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        /// @}
        builder.setCancelable(true);
        builder.setMessage(locked ? R.string.confirm_delete_locked_message :
                    R.string.confirm_delete_message);
        builder.setPositiveButton(R.string.delete, listener);
        builder.setNegativeButton(R.string.no, null);
        //builder.show();
         /*add by lihaizhou for lihaizhou for ChildMode at 2015-07-23 by begin */
                if (isProhibitDeleteSmsmms()) {
					Toast.makeText(ComposeMessageActivity.this, R.string.Prohibit_delete, Toast.LENGTH_SHORT).show();
                 return;
                }
               else 
               {
               builder.show();
               }

             /*add by lihaizhou for lihaizhou for ChildMode at 2015-07-23 by end*/  
    }

    void undeliveredMessageDialog(long date) {
        String body;

        if (date >= 0) {
            body = getString(R.string.undelivered_msg_dialog_body,
                    MessageUtils.formatTimeStampString(this, date));
        } else {
            // FIXME: we can not get sms retry time.
            body = getString(R.string.undelivered_sms_dialog_body);
        }

        Toast.makeText(this, body, Toast.LENGTH_LONG).show();
    }

    private void startMsgListQuery() {
        startMsgListQuery(MESSAGE_LIST_QUERY_TOKEN, 100);
    }

    private void startMsgListQuery(final int token, int delay) {
        MmsLog.d(TAG, "startMsgListQuery, timeout=" + delay);
        /// M: Code analyze 010, Support dirtory mode. @{
        if (MmsConfig.getMmsDirMode()) {
            return;
        }
        /// @}
        if (isRecipientsEditorVisible()) {
            return;
        }
        /// M: For OP09 @{
        Uri conversationUriSrc = mConversation.getUri();
        if (MmsConfig.isMassTextEnable()) {
            conversationUriSrc = mMmsComposePlugin.getConverationUri(conversationUriSrc,
                mConversation.getThreadId());
        }
        final Uri conversationUri = conversationUriSrc;
        /// @}
        MmsLog.d(TAG, "startMsgListQuery, uri=" + conversationUri);
        if (conversationUri == null) {
            log("##### startMsgListQuery: conversationUri is null, bail!");
            return;
        }

        final long threadId = mConversation.getThreadId();
        if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("startMsgListQuery for " + conversationUri + ", threadId=" + threadId);
        }

        // Cancel any pending queries
        /// M: add for ip message, query unread message
        if (token == MESSAGE_LIST_QUERY_TOKEN) {
            mBackgroundQueryHandler.cancelOperation(MESSAGE_LIST_UNREAD_QUERY_TOKEN);
            mBackgroundQueryHandler.removeCallbacks(mQueryUnReadMsgListRunnable);
            mBackgroundQueryHandler.cancelOperation(MESSAGE_LIST_QUERY_TOKEN);
            mBackgroundQueryHandler.removeCallbacks(mQueryMsgListRunnable);
        }

        try {
            /// M: add for ipmessage
            if (token == MESSAGE_LIST_QUERY_TOKEN) {
                /// M: add for ip message, query unread message
                if (mShowUnreadDivider) {
                    mBackgroundQueryHandler.postDelayed(mQueryUnReadMsgListRunnable, delay);
                }
                mBackgroundQueryHandler.postDelayed(mQueryMsgListRunnable, delay);
                 return;
            }

            // Kick off the new query
            /// M: @{
            // mBackgroundQueryHandler.startQuery(
            mBackgroundQueryHandler.postDelayed(new Runnable() {
                public void run() {
                    /// M: If no listener, no need query anymore @{
                        MmsLog.d(TAG, "mListQueryRunnable, to query, " + "activity=" + ComposeMessageActivity.this);
                        if (mMsgListAdapter.getOnDataSetChangedListener() == null) {
                            MmsLog.d(TAG, "mListQueryRunnable, no listener");
                            return;
                        }
                    /// @}
                    mBackgroundQueryHandler.startQuery(
                            token,
                            threadId /* cookie */,
                            conversationUri,
                            PROJECTION,
                            null, null, null);
                }
            }, delay);
            /// @}
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    private void initMessageList() {
        if (mMsgListAdapter != null) {
            return;
        }

        String highlightString = getIntent().getStringExtra("highlight");
        Pattern highlight = highlightString == null
            ? null
        /// M: ALPS00619099, highlight all matched search string, not only on word border @ {
            : Pattern.compile(Pattern.quote(highlightString), Pattern.CASE_INSENSITIVE);
        /// @}

        // Initialize the list adapter with a null cursor.
        mMsgListAdapter = new MessageListAdapter(this, null, mMsgListView, true, highlight);
        /// M: Code analyze 010, Support dirtory mode. @{
        if (MmsConfig.getMmsDirMode()) {
            mMsgListView.setVisibility(View.GONE);
            return;
        }
        /// @}
        mMsgListAdapter.setOnDataSetChangedListener(mDataSetChangedListener);
        mMsgListAdapter.setMsgListItemHandler(mMessageListItemHandler);
        mMsgListView.setAdapter(mMsgListAdapter);
        mMsgListView.setSelector(android.R.color.transparent);
        mMsgListView.setItemsCanFocus(false);
        mMsgListView.setVisibility(View.VISIBLE);
        mMsgListView.setOnCreateContextMenuListener(mMsgListMenuCreateListener);
        mMsgListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
			/*HQ_zhangjing add for al812 mms ui begin*/
			//public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            public void onItemClick(AdapterView<?> parent, View itemView, int position, long id) {
				TimeAxisWidget taw = (TimeAxisWidget)itemView.findViewById(R.id.item_date);
				View view = taw.getContent();
			/*HQ_zhangjing add for al812 mms ui end*/
                MmsLog.d(MessageListItem.TAG_DIVIDER, "OnItemClickListener(): view = " + view);
                if (view != null) {
                    ((MessageListItem) view).onMessageListItemClick();
                }
            }
        });
        /// M: Code analyze 050, Add scroll listener and touch listener for MessageListView.@{
        /**
         * M: Adjust the velocity scale and friction of messages list view, and The below two values are just experience
         * values. For density > 2 which usually on FHD devices with larger(1.5x) friction, set Velocity scale to 0.6 For
         * density <= 2, still use Velocity scale 0.4
         */
        final float density = getResources().getDisplayMetrics().density;
        final float DEFAULT_DENSITY = 2.0f;
        final float FHD_VELOCITY_SCALE = 0.6f;
        final float DEFAULT_VELOCITY_SCALE = 0.4f;
        final float DEFAULT_FRICTION = 0.005f;
        Log.d(TAG, "getDisplayMetrics().density: " + density);

        mMsgListView.setVelocityScale(density > DEFAULT_DENSITY ? FHD_VELOCITY_SCALE : DEFAULT_VELOCITY_SCALE);
        mMsgListView.setFriction(DEFAULT_FRICTION);
        mMsgListView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                hideInputMethod();
                return false;
            }
        });
        /// @}
        /// M: Fix ipmessage bug @{
        /// M: add for ip message, online divider
        // if (MmsConfig.getIpMessagServiceId(this) == IpMessageServiceId.ISMS_SERVICE
        //        && mIsIpMessageRecipients && mConversation.getRecipients().size() == 1) {
        //    mMsgListAdapter.setOnlineDividerString(getOnlineDividerString(IpMessageUtils.getContactManager(this)
        //        .getStatusByNumber(mConversation.getRecipients().get(0).getNumber())));
        // }
        /// @}
    }

    private void loadDraft() {
        if (mWorkingMessage.isWorthSaving()) {
            Log.w(TAG, "loadDraft() called with non-empty working message");
            return;
        }

        /// M: add for load IP message draft.@{
        if (IpMessageUtils.getIpMessagePlugin(this).isActualPlugin()) {
            if (mIpMessageDraft != null) {
                Log.w(TAG, "loadDraft() called with non-empty IP message draft");
                return;
            } else if (loadIpMessagDraft()) {
                return;
            }
        }
        /// @}

        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("loadDraft() call WorkingMessage.loadDraft");
        }

        /// M:[Just Comment] 4.1 Code.  There may be a bug.
        mWorkingMessage = WorkingMessage.loadDraft(this, mConversation,
                new Runnable() {
                    @Override
                    public void run() {
                        drawTopPanel(false);
                        drawBottomPanel();
                        updateSendButtonState();
                    }
                });
//        if (mConversation != null && mConversation.getRecipients() != null
//                && mConversation.getRecipients().size() > 20) {
//            MmsLog.d(TAG, "register mDraftChangeObserver");
//            mContentResolver.registerContentObserver(
//                    Mms.CONTENT_URI, true, mDraftChangeObserver);
//        }
    }

    private void saveDraft(boolean isStopping) {
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            LogTag.debug("saveDraft");
        }
        /// M: add for save IP message draft.@{
        if (IpMessageUtils.getIpMessagePlugin(this).isActualPlugin() && isFinishing()) {
            if (mIpMessageDraft != null) {
                saveIpMessageDraft();
                return;
            }
            if (mIpMessageDraftId > 0) {
                IpMessageUtils.deleteIpMessageDraft(this, mConversation, mWorkingMessage);
            }
        }
        /// @}
        // TODO: Do something better here.  Maybe make discard() legal
        // to call twice and make isEmpty() return true if discarded
        // so it is caught in the clause above this one?
        if (mWorkingMessage.isDiscarded()) {
            return;
        }

        if (!mWaitingForSubActivity &&
                (!mWorkingMessage.isWorthSaving() && mIpMessageDraft == null) &&
                (!isRecipientsEditorVisible() || recipientCount() == 0
                /* M: ALPS01821584. discard when onStop */ || isStopping)) {
            if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                log("not worth saving, discard WorkingMessage and bail");
            }
            mWorkingMessage.discard(false);
            return;
        }
        /// M : Fix CR: ALPS00996195
        /// group message, more than one recipients, no need to saveDraft,
        /// is going to MultiDeleteActivity, just return. if contains draft, discard it @{
        if (mIsStartMultiDeleteActivity) {
            if (MmsPreferenceActivity.getIsGroupMmsEnabled(this) && !needSaveDraft()
                && mConversation.getRecipients().size() > 1) {
                Log.d("[Mms][Draft][ComposeMessageActivity]", "[saveDraft] group message & "
                        + " no need save drat & mIsStartMultiDeleteActivity true, then just return!");
                if (mConversation.hasDraft()) {
                    Log.d("[Mms][Draft][ComposeMessageActivity]", "[saveDraft] mConversation has draft, then delete it");
                    mWorkingMessage.discard(false);
                }
                return;
            }
            /// M Fix CR ALPS01201355, which will save draft the contact contains email address.
            /// when start multideleteactivity. if contains mms draft ,discard it @{
            if (getRecipients().containsEmail() && !needSaveDraft()) {
                Log.d("[Mms][Draft][ComposeMessageActivity]", "[saveDraft] contains email address"
                    + ",no need to save draft, just return!!");
                if (mConversation.hasDraft()) {
                    Log.d("[Mms][Draft][ComposeMessageActivity]", "[saveDraft] mConversation has draft, then delete it");
                    mWorkingMessage.discard(false);
                }
                return;
            }
            /// @}
        }
        /// @}

        /// M: add for mms cc feature. OP09 requested.
        if (MmsConfig.isSupportSendMmsWithCc() && mRecipientsCcEditor != null) {
            mWorkingMessage.setMmsCc(mRecipientsCcEditor.constructContactsFromInput(false), false);
        }

        Log.d("[Mms][Draft][ComposeMessageActivity]", "[saveDraft] call workingmessage.saveDraft");
        mWorkingMessage.saveDraft(isStopping);

        if (mToastForDraftSave && MmsConfig.isSmsEnabled(this)) {
            Toast.makeText(this, R.string.message_saved_as_draft,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isPreparedForSending() {
        /// M: Code analyze 029,Add sub card state as a ready condition. @{
        /*
        int recipientCount = recipientCount();

        return recipientCount > 0 && recipientCount <= MmsConfig.getRecipientLimit() &&
            (mWorkingMessage.hasAttachment() ||
                    mWorkingMessage.hasText() ||
                    mWorkingMessage.hasSubject());
        */
        if (isRecipientsEditorVisible()) {
            String recipientText = mRecipientsEditor.getText() == null ? "" : mRecipientsEditor.getText().toString();

            /// M: add for ip message
            if (FeatureOption.HQ_MMS_SEND_EMPTY_MESSAGE ){
            	return mSubCount > 0 && (recipientText != null && !recipientText.equals(""));
            }else{
	            return mSubCount > 0 && !TextUtils.isEmpty(recipientText) && mIsSmsEnabled
	                    && (mWorkingMessage.hasAttachment() || mWorkingMessage.hasText()
	                            || mWorkingMessage.hasSubject() || mIpMessageDraft != null);
            }
        
        } else {
        	if(FeatureOption.HQ_MMS_SEND_EMPTY_MESSAGE ){
        		return mSubCount > 0;
        	}else{
	            return mSubCount > 0 && mIsSmsEnabled && (mWorkingMessage.hasAttachment() || mWorkingMessage.hasText()
	                            || mWorkingMessage.hasSubject() || mIpMessageDraft != null);
        	}
        }
        /// @}
    }

    private int recipientCount() {
        int recipientCount;

        // To avoid creating a bunch of invalid Contacts when the recipients
        // editor is in flux, we keep the recipients list empty.  So if the
        // recipients editor is showing, see if there is anything in it rather
        // than consulting the empty recipient list.
        if (isRecipientsEditorVisible()) {
            recipientCount = mRecipientsEditor.getRecipientCount();
        } else {
            recipientCount = getRecipients().size();
        }
        return recipientCount;
    }

    private void sendMessage(boolean bCheckEcmMode) {
        if (mWorkingMessage.requiresMms() && (mWorkingMessage.hasSlideshow() || mWorkingMessage.hasAttachment())) {
            /// M: for Fixed ALPS00528150
            int attachmentSize = 0;
            int messageSize = 0;
            if (!MmsConfig.isSupportAttachEnhance()) {
                MmsLog.d(TAG, "Compose.sendMessage(): isSupportAttachmentEnhance is false");
                for (FileAttachmentModel fileAttachment : mWorkingMessage.getSlideshow()
                        .getAttachFiles()) {
                    attachmentSize += fileAttachment.getAttachSize();
                }
            }
            messageSize = mWorkingMessage.getCurrentMessageSize() + attachmentSize;
            MmsLog.d(TAG, "Compose.sendMessage(): messageSize=" + messageSize);
            if (messageSize > MmsConfig.getUserSetMmsSizeLimit(true)) {
                MessageUtils.showErrorDialog(ComposeMessageActivity.this,
                        R.string.exceed_message_size_limitation,
                        R.string.exceed_message_size_limitation, 0, 0);
                updateSendButtonState();
                return;
            }
        }

        if (bCheckEcmMode) {
            // TODO: expose this in telephony layer for SDK build
            String inEcm = SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE);
            if (Boolean.parseBoolean(inEcm)) {
                try {
                    startActivityForResult(
                            new Intent(TelephonyIntents.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS, null),
                            REQUEST_CODE_ECM_EXIT_DIALOG);
                    return;
                } catch (ActivityNotFoundException e) {
                    // continue to send message
                    Log.e(TAG, "Cannot find EmergencyCallbackModeExitDialog", e);
                }
            }
        }

        /// M: For OP09 4GDataOnly Feature; @{
        if (mMmsUtils.is4GDataOnly(this, mSelectedSubId)) {
            updateSendButtonState(true);
            return;
        }
        /// @}

        /// M: Fix bug ALPS00407718
        if (mExitOnSent) {
            hideKeyboard();
        }
        /// M: Code analyze 011, use another method for performance.(update mDebugRecipients)@{
        ContactList contactList = isRecipientsEditorVisible() ?
                mRecipientsEditor.constructContactsFromInput(false) : getRecipients();
        mDebugRecipients = contactList.serialize();
        /// @}

        /// M: support mms cc feature. Op09 requested.
        if (isRecipientsCcEditorVisible()) {
            MmsLog.d(TAG, "sendMessage, cc editor visible set cc.");
            mWorkingMessage.setMmsCc(mRecipientsCcEditor.constructContactsFromInput(false), false);
        }

        if (!mSendingMessage) {
            if (LogTag.SEVERE_WARNING) {
                String sendingRecipients = mConversation.getRecipients().serialize();
                if (!sendingRecipients.equals(mDebugRecipients)) {
                    String workingRecipients = mWorkingMessage.getWorkingRecipients();
                    if (!mDebugRecipients.equals(workingRecipients)) {
                        LogTag.warnPossibleRecipientMismatch("ComposeMessageActivity.sendMessage" +
                                " recipients in window: \"" +
                                mDebugRecipients + "\" differ from recipients from conv: \"" +
                                sendingRecipients + "\" and working recipients: " +
                                workingRecipients, this);
                    }
                }
                sanityCheckConversation();
            }

            // send can change the recipients. Make sure we remove the listeners first and then add
            // them back once the recipient list has settled.
            removeRecipientsListeners();

            /// M: If msg can be sent, AttachmentEditor can not be reponsed.
            mClickCanResponse = false;
            /// M:the method is extend to support gemini @{
            mWorkingMessage.send(mDebugRecipients, mSelectedSubId);
            MmsLog.d(TAG, "Compose.sendMessage(): after sendMessage. mConversation.ThreadId=" + mConversation.getThreadId()
                    + ", MessageCount=" + mConversation.getMessageCount());
            /// @}
            /** M:
             *   If message count is 0, it should be a new message.
             *   After tap send button, the sent message will have draft flag for a short time.
             *   That means, the message count will be 0 for a short time.
             *   If user tap home key in this short time, it will change the conversation id to 0 in the method savedraft().
             *   When the screen is back to Message Composer, it will query database with thread(conversation) id 0.
             *   So, it will query for nothing. The screen is always blank.
             *   Fix this issue by force to set message count with 1.
             */
            if (mConversation.getMessageCount() == 0) {
                mConversation.setMessageCount(1);
            }
            /// M: @{
            mWaitingForSendMessage = true;
            /// M: when tap fail icon, don't add recipients
            isInitRecipientsEditor = false;
            mMsgListView.setVisibility(View.VISIBLE);
            /// @}

            mSentMessage = true;
            mSendingMessage = true;
            addRecipientsListeners();

            mScrollOnSend = true;   // in the next onQueryComplete, scroll the list to the end.
            ///M: message has been sent via common message , so change the sign.
            mJustSendMsgViaCommonMsgThisTime = false;
            // M: reset mCutRecipients
            mCutRecipients = null;
        }
        // But bail out if we are supposed to exit after the message is sent.
        if (mExitOnSent) {
            /// M: fix bug ALPS00722349, update thread after forward message, avoid conversation
            /// draft state become abnormal.
            boolean isChecked = mConversation.isChecked();
            mConversation = Conversation.upDateThread(ComposeMessageActivity.this, mConversation.getThreadId(), false);
            mConversation.setIsChecked(isChecked);
            ///M: add for guarantee the message sent. @{
            mUiHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            }, 150);
            /// @}
        }
    }

    private void resetMessage() {
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("resetMessage");
        }

        /// M: should hide RecipientsEditor before TextEditor requestFocus. To avoid do
        /// afterTextChange after message sent.
        hideRecipientEditor();
        updateTitle(mConversation.getRecipients());

        if (mIpMessageForSend == null || mIsClearTextDraft) {
            // Make the attachment editor hide its view.
            mAttachmentEditor.hideView();
//            mAttachmentEditorScrollView.setVisibility(View.GONE);
            ///M: change the order between Editor.requestFocus & showSubjectEditor
            /// for fix issue ALPS00569570 @{
            // Focus to the text editor.
            mTextEditor.requestFocus();

            // Hide the subject editor.
            showSubjectEditor(false);
            /// @}

            /// M: add for mms cc feature. OP09 requested.
            showRecipientsCcEditor(false);

            // We have to remove the text change listener while the text editor gets cleared and
            // we subsequently turn the message back into SMS. When the listener is listening while
            // doing the clearing, it's fighting to update its counts and itself try and turn
            // the message one way or the other.
            mTextEditor.removeTextChangedListener(mTextEditorWatcher);

            // Clear the text box.
            TextKeyListener.clear(mTextEditor.getText());

            mWorkingMessage.clearConversation(mConversation, false);
            mWorkingMessage = WorkingMessage.createEmpty(this);
            mWorkingMessage.setConversation(mConversation);

            /// M: add for ip message, clear IP message draft
            mIpMessageDraftId = 0;
            clearIpMessageDraft();
        }

        if (IpMessageUtils.getIpMessagePlugin(this).isActualPlugin() && mCurrentChatMode != IpMessageConsts.ChatMode.XMS) {
            mIsIpServiceEnabled = MmsConfig.isServiceEnabled(this);
        }

        /// M: add for ip message, update online divider
        if (null != mConversation.getRecipients() && mConversation.getRecipients().size() == 1) {
            mIsIpMessageRecipients = isIpMessageRecipients(mConversation.getRecipients().get(0).getNumber());
            if (mIsIpServiceEnabled && isNetworkConnected(getApplicationContext())
                 /// M: Fix ipmessage bug for ALPS01674002
                    /* && mIsIpMessageRecipients*/) {
                if (!TextUtils.isEmpty(mChatModeNumber)
                        && isChatModeNumber(mConversation.getRecipients().get(0).getNumber())) {
                    MmsLog.d(IPMSG_TAG, "resetMessage(): update mChatModeNumber from " + mChatModeNumber + " to "
                        + mConversation.getRecipients().get(0).getNumber());
                    IpMessageUtils.getChatManager(this).exitFromChatMode(mChatModeNumber);
                    mChatModeNumber = mConversation.getRecipients().get(0).getNumber();
                    IpMessageUtils.getChatManager(this).enterChatMode(mChatModeNumber);
                } else if (TextUtils.isEmpty(mChatModeNumber)) {
                    MmsLog.d(IPMSG_TAG, "resetMessage(): update mChatModeNumber after send message, mChatModeNumber = "
                        + mChatModeNumber);
                    mChatModeNumber = mConversation.getRecipients().get(0).getNumber();
                    IpMessageUtils.getChatManager(this).enterChatMode(mChatModeNumber);
                }
            }
        }

        if ((mMessageSubId == Settings.System.DEFAULT_SIM_SETTING_ALWAYS_ASK || mMessageSubId == Settings.System.DEFAULT_SIM_NOT_SET)) {
            mSelectedSubId = 0;
        }

        drawBottomPanel();

        // "Or not", in this case.
        updateSendButtonState();

        if (mIpMessageForSend == null || mIsClearTextDraft) {
            // Our changes are done. Let the listener respond to text changes once again.
            mTextEditor.removeTextChangedListener(mTextEditorWatcher);
            mTextEditor.addTextChangedListener(mTextEditorWatcher);
        }

        // Close the soft on-screen keyboard if we're in landscape mode so the user can see the
        // conversation.
        ///M: but when the device was tablet, we can't hide the IME
        boolean isTablet = getResources().getBoolean(R.bool.isTablet);
        if (mIsLandscape && !isTablet) {
            hideKeyboard();
        }

        mLastRecipientCount = 0;
        mSendingMessage = false;
        invalidateOptionsMenu();
        /// M: update list, this must put after hideRecipientEditor(); to avoid a bug.
        startMsgListQuery(MESSAGE_LIST_QUERY_TOKEN, 0);
        /// M: support mms cc feature. OP09 requested.
        mLastRecipientCcCount = 0;
        /// M: reset flag after sending msg.
        mClickCanResponse = true;
   }

    private void hideKeyboard() {
        MmsLog.d(TAG, "hideKeyboard()");
        InputMethodManager inputMethodManager =
            (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mTextEditor.getWindowToken(), 0);
    }

    private void updateSendButtonState() {
		/*HQ_zhangjing add for al812 mms ui  */
        boolean enable = false;
        MmsLog.v(TAG, "lzd updateSendButtonState(): isPreparedForSending = " + isPreparedForSending());
        if (isPreparedForSending()) {
            /// M: Code analyze 049, Update send button or attachment editor state.@{
            MmsLog.v(TAG, "updateSendButtonState(): mSubCount = " + mSubCount);
            if (mSubCount > 0) {
                // When the type of attachment is slideshow, we should
                // also hide the 'Send' button since the slideshow view
                // already has a 'Send' button embedded.
                MmsLog.v(TAG,
                        "updateSendButtonState(): hasSlideshow = " + mWorkingMessage.hasSlideshow());
                if (!mWorkingMessage.hasSlideshow()) {
                    enable = true;
                } else {
                    mAttachmentEditor.setCanSend(true);
					enable = true;//HQ_zhangjing 2015-11-08 modified for CQ HQ01493261
                }
            }
        } else {
            // / @}
            mAttachmentEditor.setCanSend(false);
        }

        boolean requiresMms = mWorkingMessage.requiresMms();
        /// M: For OP09 @{
		/*HQ_zhangjing 2015-11-02 modified for CQ HQ01478873 begin*/
        /*if (MmsConfig.isDualSendButtonEnable()) {
            if (!requiresMms && (recipientCount() > MmsConfig.getSmsRecipientLimit())) {
                enable = false;
            }
            if (mSubCount < 1) {
                View sendButton = showSmsOrMmsSendButton(requiresMms);
                sendButton.setEnabled(enable);
                sendButton.setFocusable(enable);
            }
            mMmsComposePlugin.updateDualSendButtonStatue(enable, requiresMms);
            updateTextEditorHint();
            return;
        }*/
		/*HQ_zhangjing 2015-11-02 modified for CQ HQ01478873 end*/
        /// @}
        if (!requiresMms && (recipientCount() > MmsConfig.getSmsRecipientLimit())) {
            enable = false;
        }
        if (!MmsConfig.isSmsEnabled(this)) {
            enable = false;
        }
        if (MessageUtils.isUseSubSimulator()) {
            enable = true;
        }
		/*HQ_zhangjing 2015-10-22 modified for send btn begin*/
		boolean isAirplaneMode = isInAirplaneMode();
		enable = enable && !isAirplaneMode;

        View sendButton = showSmsOrMmsSendButton(requiresMms);
        if (sendButton != null) {
            sendButton.setEnabled(enable);
            sendButton.setFocusable(enable);
        }
		showOrHideSendbtn();
		updateMultiBtnState( enable );
		/*HQ_zhangjing 2015-10-22 modified for send btn end*/
    }

    private long getMessageDate(Uri uri) {
        if (uri != null) {
            Cursor cursor = SqliteWrapper.query(this, mContentResolver,
                    uri, new String[] { Mms.DATE }, null, null, null);
            if (cursor != null) {
                try {
                    if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                        return cursor.getLong(0) * 1000L;
                    }
                } finally {
                    cursor.close();
                }
            }
        }
        return NO_DATE_FOR_DIALOG;
    }

/// M: @{
//    private void initActivityState(Intent intent) {
//        // If we have been passed a thread_id, use that to find our conversation.
//        long threadId = intent.getLongExtra("thread_id", 0);
//        if (threadId > 0) {
//            if (LogTag.VERBOSE) log("get mConversation by threadId " + threadId);
//            mConversation = Conversation.get(this, threadId, false);
//        } else {
//            Uri intentData = intent.getData();
//            if (intentData != null) {
//                // try to get a conversation based on the data URI passed to our intent.
//                if (LogTag.VERBOSE) log("get mConversation by intentData " + intentData);
//                mConversation = Conversation.get(this, intentData, false);
//                mWorkingMessage.setText(getBody(intentData));
//            } else {
//                // special intent extra parameter to specify the address
//                String address = intent.getStringExtra("address");
//                if (!TextUtils.isEmpty(address)) {
//                    if (LogTag.VERBOSE) log("get mConversation by address " + address);
//                    mConversation = Conversation.get(this, ContactList.getByNumbers(address,
//                            false /* don't block */, true /* replace number */), false);
//                } else {
//                    if (LogTag.VERBOSE) log("create new conversation");
//                    mConversation = Conversation.createNew(this);
//                }
//            }
//        }
//        addRecipientsListeners();
//
//        mExitOnSent = intent.getBooleanExtra("exit_on_sent", false);
//        if (intent.hasExtra("sms_body")) {
//            mWorkingMessage.setText(intent.getStringExtra("sms_body"));
//        }
//        mWorkingMessage.setSubject(intent.getStringExtra("subject"), false);
//    }
    private void initActivityState(Bundle bundle) {
        Intent intent = getIntent();
        /// M: Code analyze 033, Save some useful information in order to restore the draft when
        /// activity restarting.@{
        mIsTooManyRecipients = false;
        if (bundle != null) {
            mCompressingImage = bundle.getBoolean("compressing_image", false);
            String recipientsStr = bundle.getString("recipients");
            int recipientCount = 0;
            if (recipientsStr != null) {
                recipientCount = recipientsStr.split(";").length;
                mConversation = Conversation.get(getApplicationContext(),
                    ContactList.getByNumbers(recipientsStr,
                            false /* don't block */, true /* replace number */), false);
            } else {
                Long threadId = bundle.getLong("thread", 0);
                mConversation = Conversation.get(getApplicationContext(), threadId, false);
            }
            // M: fix bug ALPS00352078
            mWorkingMessage.setConversation(mConversation);

            mExitOnSent = bundle.getBoolean(KEY_EXIT_ON_SENT, false);
            mForwardMessageMode = bundle.getBoolean(KEY_FORWARDED_MESSAGE, false);

            mWorkingMessage.readStateFromBundle(bundle);
            /// M: Code analyze 010, Support dirtory mode. @{
            if (MmsConfig.getMmsDirMode()) {
                mExitOnSent = true;
            }
            /// @}
            /// M: if there is ipmessage draft, we need the threadid to get it.
            boolean hasIpMessageDraft = bundle.getBoolean("saved_ipmessage_draft", false);
            if (!mCompressingImage && mConversation.hasDraft() && mConversation.getMessageCount() == 0 && !hasIpMessageDraft) {
                if (!mWorkingMessage.requiresMms()) {
                    Log.w(TAG, "delete sms draft");
                    mWorkingMessage.asyncDeleteDraftSmsMessage(mConversation);
                } else {
                    if (mWorkingMessage.getSlideshow() != null && mWorkingMessage.getSlideshow().size() == 1
                            && !mWorkingMessage.getSlideshow().get(0).hasAudio()
                            && !mWorkingMessage.getSlideshow().get(0).hasImage()
                            && !mWorkingMessage.getSlideshow().get(0).hasVideo()
                            && mWorkingMessage.getSlideshow().sizeOfFilesAttach() == 0) {
                        mWorkingMessage.asyncDeleteDraftMmsMessage(mConversation);
                        mWorkingMessage.removeAllFileAttaches();
                        mWorkingMessage.removeAttachment(false);
                    }
                }
                mWorkingMessage.clearConversation(mConversation, true);
            }
            if (recipientCount > RECIPIENTS_LIMIT_FOR_SMS) {
                mIsTooManyRecipients = true;
            }
            mCompressingImage = false;

            mAppendAttachmentSign = bundle.getBoolean(KEY_APPEND_MESSAGE, true);
            MmsLog.d(TAG, "initActivityState mAppendAttachmentSign : " + mAppendAttachmentSign);
            /// @}
            return;
        }
        /// M: Code analyze 019, Add vcard attachment.  @{
        String vCardContactsIds = intent.getStringExtra("multi_export_contacts");
        long[] contactsIds = null;
        if (vCardContactsIds != null && !vCardContactsIds.equals("")) {
            String[] vCardConIds = vCardContactsIds.split(",");
            MmsLog.e(TAG, "ComposeMessage.initActivityState(): vCardConIds.length" + vCardConIds.length);
            contactsIds = new long[vCardConIds.length];
            try {
                for (int i = 0; i < vCardConIds.length; i++) {
                    contactsIds[i] = Long.parseLong(vCardConIds[i]);
                }
            } catch (NumberFormatException e) {
                contactsIds = null;
            }
        }
        /// @}
        // If we have been passed a thread_id, use that to find our conversation.
        long threadId = intent.getLongExtra("thread_id", 0);
        if (threadId > 0) {
            if (LogTag.VERBOSE) log("get mConversation by threadId " + threadId);
            mConversation = Conversation.get(getApplicationContext(), threadId, false);
        /// M: Code analyze 019, Add vcard attachment.  @{
        } else if (contactsIds != null && contactsIds.length > 0) {
            //addTextVCard(contactsIds);
            addTextVCardAsync(contactsIds);
            mConversation = Conversation.createNew(getApplicationContext());
            return;
        /// @}
        } else {
            Uri intentData = intent.getData();
            /// M: Code analyze 034, If intent is SEND,just create a new empty thread,
            /// otherwise Conversation.get() will throw exception.
            String action = intent.getAction();
            if (intentData != null && (TextUtils.isEmpty(action) ||
                            !action.equals(Intent.ACTION_SEND))) {
                /// M: group-contact send message
                // try to get a conversation based on the data URI passed to our intent.
                if (intentData.getPathSegments().size() < 2) {
                    mConversation = mConversation.get(getApplicationContext(), ContactList.getByNumbers(
                           getStringForMultipleRecipients(Conversation.getRecipients(intentData)),
                                 false /* don't block */, true /* replace number */), false);
                } else {
                    mConversation = Conversation.get(getApplicationContext(), intentData, false);
                }
                /// @}
                mWorkingMessage.setText(getBody(intentData));
            } else {
                // special intent extra parameter to specify the address
                String address = intent.getStringExtra("address");
                if (!TextUtils.isEmpty(address)) {
                    if (LogTag.VERBOSE) log("get mConversation by address " + address);
                    mConversation = Conversation.get(getApplicationContext(), ContactList.getByNumbers(address,
                            false /* don't block */, true /* replace number */), false);
                } else {
                    if (LogTag.VERBOSE) log("create new conversation");
                    mConversation = Conversation.createNew(getApplicationContext());
                }
            }
        }
        //addRecipientsListeners();
        updateThreadIdIfRunning();

        mExitOnSent = intent.getBooleanExtra(KEY_EXIT_ON_SENT, false);
        mForwardMessageMode = intent.getBooleanExtra(KEY_FORWARDED_MESSAGE, false);
        if (intent.hasExtra("sms_body")) {
            /// M: Code analyze 017, Handle forwarded message.(see:forwardMessage()).
            /// Forward sms message and set sms body.@{
            String sms_body = intent.getStringExtra("sms_body");
            /// M: Modify for ALPS00759207
            mWorkingMessage.setText(sms_body);
            /// @}
        }
        mWorkingMessage.setSubject(intent.getStringExtra("subject"), false);

        /// M: Code analyze 010, Support dirtory mode. @{
        if (MmsConfig.getMmsDirMode()) {
            mExitOnSent = true;
        }
        /// @}
        mSendSubIdForDualBtn = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        MmsLog.d(TAG, "init get subId from intent = " + mSendSubIdForDualBtn);
    }

    private void initFocus() {
        if (!mIsKeyboardOpen) {
            return;
        }
        //add for OP09 CC recipents
        if (mRecipientsCcEditor != null && isRecipientsCcEditorVisible() &&
                mRecipientsCcEditor.isFocused() && (mRecipientsCcEditor.getRecipientCount() < 1)) {
                mRecipientsCcEditor.requestFocus();
                return;
        }
        // If the recipients editor is visible, there is nothing in it,
        // and the text editor is not already focused, focus the
        // recipients editor.
        if (isRecipientsEditorVisible()
                && TextUtils.isEmpty(mRecipientsEditor.getText())
                && !mTextEditor.isFocused()) {
            mRecipientsEditor.requestFocus();
            return;
        }

        // If we decided not to focus the recipients editor, focus the text editor.
        if (mSubjectTextEditor == null
                || (mSubjectTextEditor != null && !mSubjectTextEditor.isFocused())) {
            if ((mBottomPanel != null) && (mBottomPanel.getVisibility() == View.VISIBLE)) {
                mTextEditor.requestFocus();
            }
        }
    }

    private final MessageListAdapter.OnDataSetChangedListener
                    mDataSetChangedListener = new MessageListAdapter.OnDataSetChangedListener() {
        @Override
        public void onDataSetChanged(MessageListAdapter adapter) {
            mPossiblePendingNotification = true;
        }

        @Override
        public void onContentChanged(MessageListAdapter adapter) {
        /// M: @{
            if (mMsgListAdapter != null &&
                mMsgListAdapter.getOnDataSetChangedListener() != null) {
                MmsLog.d(TAG, "OnDataSetChangedListener is not cleared");
                /// M: add for ip message, unread divider
                mShowUnreadDivider = false;
                startMsgListQuery();
            } else {
                MmsLog.d(TAG, "OnDataSetChangedListener is cleared");
            }
        /// @}
        }
    };

    private void checkPendingNotification() {
        if (mPossiblePendingNotification && hasWindowFocus()) {
            /// M: add for ip message, remove mark as read
//            mConversation.markAsRead();
            mPossiblePendingNotification = false;
        }
    }

    /**
     * smoothScrollToEnd will scroll the message list to the bottom if the list is already near
     * the bottom. Typically this is called to smooth scroll a newly received message into view.
     * It's also called when sending to scroll the list to the bottom, regardless of where it is,
     * so the user can see the just sent message. This function is also called when the message
     * list view changes size because the keyboard state changed or the compose message field grew.
     *
     * @param force always scroll to the bottom regardless of current list position
     * @param listSizeChange the amount the message list view size has vertically changed
     */
    private void smoothScrollToEnd(boolean force, int listSizeChange) {
        int lastItemVisible = mMsgListView.getLastVisiblePosition();
        int lastItemInList = mMsgListAdapter.getCount() - 1;
        if (lastItemVisible < 0 || lastItemInList < 0) {
            if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                Log.v(TAG, "smoothScrollToEnd: lastItemVisible=" + lastItemVisible +
                        ", lastItemInList=" + lastItemInList +
                        ", mMsgListView not ready");
            }
            return;
        }

        View lastChildVisible =
                mMsgListView.getChildAt(lastItemVisible - mMsgListView.getFirstVisiblePosition());
        int lastVisibleItemBottom = 0;
        int lastVisibleItemHeight = 0;
        if (lastChildVisible != null) {
            lastVisibleItemBottom = lastChildVisible.getBottom();
            lastVisibleItemHeight = lastChildVisible.getHeight();
        }

        if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            Log.v(TAG, "smoothScrollToEnd newPosition: " + lastItemInList +
                    " mLastSmoothScrollPosition: " + mLastSmoothScrollPosition +
                    " first: " + mMsgListView.getFirstVisiblePosition() +
                    " lastItemVisible: " + lastItemVisible +
                    " lastVisibleItemBottom: " + lastVisibleItemBottom +
                    " lastVisibleItemBottom + listSizeChange: " +
                    (lastVisibleItemBottom + listSizeChange) +
                    " mMsgListView.getHeight() - mMsgListView.getPaddingBottom(): " +
                    (mMsgListView.getHeight() - mMsgListView.getPaddingBottom()) +
                    " listSizeChange: " + listSizeChange);
        }
        // Only scroll if the list if we're responding to a newly sent message (force == true) or
        // the list is already scrolled to the end. This code also has to handle the case where
        // the listview has changed size (from the keyboard coming up or down or the message entry
        // field growing/shrinking) and it uses that grow/shrink factor in listSizeChange to
        // compute whether the list was at the end before the resize took place.
        // For example, when the keyboard comes up, listSizeChange will be negative, something
        // like -524. The lastChild listitem's bottom value will be the old value before the
        // keyboard became visible but the size of the list will have changed. The test below
        // add listSizeChange to bottom to figure out if the old position was already scrolled
        // to the bottom. We also scroll the list if the last item is taller than the size of the
        // list. This happens when the keyboard is up and the last item is an mms with an
        // attachment thumbnail, such as picture. In this situation, we want to scroll the list so
        // the bottom of the thumbnail is visible and the top of the item is scroll off the screen.
        int listHeight = mMsgListView.getHeight();
        boolean lastItemTooTall = lastVisibleItemHeight > listHeight;
        boolean willScroll = force ||
                ((listSizeChange != 0 || lastItemInList != mLastSmoothScrollPosition) &&
                lastVisibleItemBottom + listSizeChange <=
                    listHeight - mMsgListView.getPaddingBottom());
        if (willScroll || (lastItemTooTall && lastItemInList == lastItemVisible)) {
            if (Math.abs(listSizeChange) > SMOOTH_SCROLL_THRESHOLD) {
                // When the keyboard comes up, the window manager initiates a cross fade
                // animation that conflicts with smooth scroll. Handle that case by jumping the
                // list directly to the end.
                if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                    Log.v(TAG, "keyboard state changed. setSelection=" + lastItemInList);
                }
                if (lastItemTooTall) {
                    // If the height of the last item is taller than the whole height of the list,
                    // we need to scroll that item so that its top is negative or above the top of
                    // the list. That way, the bottom of the last item will be exposed above the
                    // keyboard.
                    mMsgListView.setSelectionFromTop(lastItemInList,
                            listHeight - lastVisibleItemHeight);
                } else {
                    mMsgListView.setSelection(lastItemInList);
                }
            } else if (lastItemInList - lastItemVisible > MAX_ITEMS_TO_INVOKE_SCROLL_SHORTCUT) {
                if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                    Log.v(TAG, "too many to scroll, setSelection=" + lastItemInList);
                }
                mMsgListView.setSelection(lastItemInList);
            } else {
                if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                    Log.v(TAG, "smooth scroll to " + lastItemInList);
                }
                if (lastItemTooTall) {
                    // If the height of the last item is taller than the whole height of the list,
                    // we need to scroll that item so that its top is negative or above the top of
                    // the list. That way, the bottom of the last item will be exposed above the
                    // keyboard. We should use smoothScrollToPositionFromTop here, but it doesn't
                    // seem to work -- the list ends up scrolling to a random position.
                    mMsgListView.setSelectionFromTop(lastItemInList,
                            listHeight - lastVisibleItemHeight);
                } else {
                    mMsgListView.smoothScrollToPosition(lastItemInList);
                }
                mLastSmoothScrollPosition = lastItemInList;
            }
        }
    }

    private final class BackgroundQueryHandler extends ConversationQueryHandler {
        public BackgroundQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            /// M: @{
            MmsLog.d(TAG, "onQueryComplete, token=" + token + "activity=" + ComposeMessageActivity.this);
            /// @}
            switch(token) {
                case MESSAGE_LIST_QUERY_TOKEN:
                    /// M: reset unread count to 0 if not need to show unread divider. @{
                    if (!mShowUnreadDivider) {
                        mMsgListAdapter.setUnreadCount(0);
                    }
                    /// @}
                    if (cursor == null) {
                        MmsLog.w(TAG, "onQueryComplete, cursor is null.");
                        return;
                    }
                    /// M: If adapter or listener has been cleared, just close this cursor@{
                    if (mMsgListAdapter == null) {
                        MmsLog.w(TAG, "onQueryComplete, mMsgListAdapter is null.");
                        cursor.close();
                        return;
                    }
                    if (mMsgListAdapter.getOnDataSetChangedListener() == null) {
                        MmsLog.d(TAG, "OnDataSetChangedListener is cleared");
                        cursor.close();
                        return;
                    }
                    /// @}
                    if (isRecipientsEditorVisible()) {
                        MmsLog.d(TAG, "RecipientEditor visible, it means no messagelistItem!");
                        return;
                    }
                    // check consistency between the query result and 'mConversation'
                    long tid = (Long) cookie;

                    if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                        log("##### onQueryComplete: msg history result for threadId " + tid);
                    }
                    if (tid != mConversation.getThreadId()) {
                        log("onQueryComplete: msg history query result is for threadId " +
                                tid + ", but mConversation has threadId " +
                                mConversation.getThreadId() + " starting a new query");
                        if (cursor != null) {
                            cursor.close();
                        }
                        startMsgListQuery();
                        return;
                    }

                    // check consistency b/t mConversation & mWorkingMessage.mConversation
                    ComposeMessageActivity.this.sanityCheckConversation();

                    int newSelectionPos = -1;
                    long targetMsgId = getIntent().getLongExtra("select_id", -1);
                    if (targetMsgId != -1) {
                      if (cursor != null) {
                        cursor.moveToPosition(-1);
                        while (cursor.moveToNext()) {
                            long msgId = cursor.getLong(COLUMN_ID);
                            if (msgId == targetMsgId) {
                                newSelectionPos = cursor.getPosition();
                                break;
                            }
                          }
                        }
                    } else if (mSavedScrollPosition != -1) {
                        // mSavedScrollPosition is set when this activity pauses. If equals maxint,
                        // it means the message list was scrolled to the end. Meanwhile, messages
                        // could have been received. When the activity resumes and we were
                        // previously scrolled to the end, jump the list so any new messages are
                        // visible.
                        if (mSavedScrollPosition == Integer.MAX_VALUE) {
                            int cnt = mMsgListAdapter.getCount();
                            if (cnt > 0) {
                                // Have to wait until the adapter is loaded before jumping to
                                // the end.
                                newSelectionPos = cnt - 1;
                                mSavedScrollPosition = -1;
                            }
                        } else {
                            // remember the saved scroll position before the activity is paused.
                            // reset it after the message list query is done
                            newSelectionPos = mSavedScrollPosition;
                            mSavedScrollPosition = -1;
                        }
                    }
                    /// M: Code analyze 047, Extra uri from message body and get number from uri.
                    /// Then use this number to update contact cache. @{
                    if (mNeedUpdateContactForMessageContent) {
                        updateContactCache(cursor);
                        mNeedUpdateContactForMessageContent = false;
                    }
                    /// @}

                    mMsgListAdapter.changeCursor(cursor);

                    if (newSelectionPos != -1) {
                        /// M: remove bug ALPS00404266 patch, keep item top @{
                        mMsgListView.setSelection(newSelectionPos);     // jump the list to the pos
                        //View child = mMsgListView.getChildAt(newSelectionPos);
                        //int top = 0;
                        //if (child != null) {
                        //    top = child.getTop();
                        //}
                       // mMsgListView.setSelectionFromTop(newSelectionPos, top);
                        /// @}
                    } else {
                        /// M: google jb.mr1 patch, Conversation should scroll to the bottom
                        /// when incoming received @{
                        int count = mMsgListAdapter.getCount();
                        long lastMsgId = 0;
                        if (cursor != null && count > 0) {
                            cursor.moveToLast();
                            lastMsgId = cursor.getLong(COLUMN_ID);
                        }
                        // mScrollOnSend is set when we send a message. We always want to scroll
                        // the message list to the end when we send a message, but have to wait
                        // until the DB has changed. We also want to scroll the list when a
                        // new message has arrived.
                        smoothScrollToEnd(mScrollOnSend || lastMsgId != mLastMessageId, 0);
                        mLastMessageId = lastMsgId;
                        /// @}
                        mScrollOnSend = false;
						/*HQ_zhangjing modified for al812 begin*/
						if( shouldShowLast && mMsgListView != null && mMsgListAdapter != null ){
							mMsgListView.setSelection(mMsgListAdapter.getCount());
							Log.d(TAG,"zhangjing:count = " + mMsgListAdapter.getCount());
							shouldShowLast = false;
						}
						/*HQ_zhangjing modified for al812  end*/
                    }
                    // Adjust the conversation's message count to match reality. The
                    // conversation's message count is eventually used in
                    // WorkingMessage.clearConversation to determine whether to delete
                    // the conversation or not.
                    if (mMsgListAdapter.getCount() == 0 && mWaitingForSendMessage) {
                        mConversation.setMessageCount(1);
                    } else {
                        mConversation.setMessageCount(mMsgListAdapter.getCount());
                    }
                    updateThreadIdIfRunning();
                    cursor.moveToPosition(-1);
                    while (cursor.moveToNext()) {
                        int read = cursor.getInt(MessageListAdapter.COLUMN_MMS_READ);
                        read += cursor.getInt(MessageListAdapter.COLUMN_SMS_READ);
                        if (read == 0) {
                            mConversation.setHasUnreadMessages(true);
                            break;
                        }
                    }
					//zhangjing add time view visibile or not here and set the selection to the end
					/*HQ_zhangjing add for al812 mms ui begin*/
					if( (mMsgListAdapter != null && mMsgListAdapter.getCount() > 0) 
						|| (mConversation != null && mConversation.getMessageCount() > 0)){
						mTimeView.setVisibility(View.VISIBLE);
						Log.d(TAG,"zhangjing:timeView VISIBLE");
					}else{
						mTimeView.setVisibility(View.GONE);
						Log.d(TAG,"zhangjing:timeView GONE");
					}
					/*HQ_zhangjing add for al812 mms ui end*/
					
                    MmsLog.d(TAG, "onQueryComplete(): Conversation.ThreadId=" + mConversation.getThreadId()
                            + ", MessageCount=" + mConversation.getMessageCount());

                    // Once we have completed the query for the message history, if
                    // there is nothing in the cursor and we are not composing a new
                    // message, we must be editing a draft in a new conversation (unless
                    // mSentMessage is true).
                    // Show the recipients editor to give the user a chance to add
                    // more people before the conversation begins.
                    if (cursor != null && cursor.getCount() == 0 && !isRecipientsEditorVisible() && !mSentMessage) {
                        /// M: fix bug ALPS01098902, avoding checkObsoleteThreadId in this case
                        if (mSubSelectDialog != null && mSubSelectDialog.isShowing()
                                && mOldThreadID > 0 && mCutRecipients != null) {
                            mIsSameConv = false;
                        }
                        initRecipientsEditor(null);
                    }

                    // FIXME: freshing layout changes the focused view to an unexpected
                    // one, set it back to TextEditor forcely.
                    if (mSubjectTextEditor == null || (mSubjectTextEditor != null && !mSubjectTextEditor.isFocused()))
                    {
                        mTextEditor.requestFocus();
                    }

                    invalidateOptionsMenu();    // some menu items depend on the adapter's count

                    showInvitePanel();

                    /// M: add for ip message, unread divider, fix bug ALPS01428411
                    if (!mShowUnreadDivider && mIsMarkMsgAsRead) {
                        mConversation.blockMarkAsRead(false);
                        mConversation.markAsRead();
                    }
                    return;
                case MESSAGE_LIST_UNREAD_QUERY_TOKEN:
                    MmsLog.d(TAG, "onQueryComplete(): unread cursor = " + cursor +
                            ", show divider ?= " + mShowUnreadDivider);
                    MmsLog.d(TAG_DIVIDER, "compose.onQueryComplete(): unread cursor = " + cursor +
                            ", show divider ?=" + mShowUnreadDivider);
                    if (cursor != null) {
                        MmsLog.d(TAG, "onQueryComplete(): unread cursor count = " + cursor.getCount());
                        MmsLog.d(TAG_DIVIDER, "compose.onQueryComplete(): unread cursor count = " + cursor.getCount());
                    }
                    if (mShowUnreadDivider) {
                        if (cursor == null) {
                            MmsLog.w(TAG, "onQueryComplete(): case MESSAGE_LIST_UNREAD_QUERY_TOKEN, cursor is null.");
                            return;
                        }
                        MmsLog.d(TAG_DIVIDER, "compose.onQueryComplete(): unread cursor count = " + cursor.getCount());
                        mMsgListAdapter.setUnreadCount(cursor.getCount());
                        cursor.close();
                    }
                    ///M add for avoid cusor leak. fix issueALPS00442806 @{
                    else {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                    /// @}
                    return;

                case MESSAGE_LIST_QUERY_AFTER_DELETE_TOKEN:
                    // check consistency between the query result and 'mConversation'
                    tid = (Long) cookie;

                    if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                        log("##### onQueryComplete (after delete): msg history result for threadId "
                                + tid);
                    }
                    if (cursor == null) {
                        return;
                    }
                    if (tid > 0 && cursor.getCount() == 0) {
                        // We just deleted the last message and the thread will get deleted
                        // by a trigger in the database. Clear the threadId so next time we
                        // need the threadId a new thread will get created.
                        log("##### MESSAGE_LIST_QUERY_AFTER_DELETE_TOKEN clearing thread id: "
                                + tid);
                        Conversation conv = Conversation.get(getApplicationContext(), tid,
                                false);
                        if (conv != null) {
                            conv.clearThreadId();
                            conv.setDraftState(false);
                        }
                    }
                    cursor.close();
                    break;
                default:
                    MmsLog.d(TAG, "unknown token.");
                    break;
            }
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            super.onDeleteComplete(token, cookie, result);
            /// M: fix bug ALPS00351620; for requery searchactivity.
            SearchActivity.setNeedRequery();
            switch(token) {
                case ConversationList.DELETE_CONVERSATION_TOKEN:
                    /// M: @{
                    /*
                    mConversation.setMessageCount(0);
                    // fall through
                    */
                    try {
                        if (TelephonyManagerEx.getDefault().isTestIccCard(0)) {
                            MmsLog.d(TAG, "All threads has been deleted, send notification..");
                            SmsManager
                                    .getSmsManagerForSubscriptionId(
                                   SmsReceiverService.sLastIncomingSmsSubId).getDefault().setSmsMemoryStatus(true);
                        }
                    } catch (Exception ex) {
                        MmsLog.e(TAG, " " + ex.getMessage());
                    }
                    // Update the notification for new messages since they
                    // may be deleted.
                    MessagingNotification.nonBlockingUpdateNewMessageIndicator(
                            ComposeMessageActivity.this, MessagingNotification.THREAD_NONE, false);
                    // Update the notification for failed messages since they
                    // may be deleted.
                    updateSendFailedNotification();
                    MessagingNotification.updateDownloadFailedNotification(ComposeMessageActivity.this);
                    break;
                    /// @}
                case DELETE_MESSAGE_TOKEN:
                    /// M: google jb.mr1 patch, Conversation should scroll to the bottom
                    /// when incoming received @{
                    if (cookie instanceof Boolean && ((Boolean)cookie).booleanValue()) {
                        // If we just deleted the last message, reset the saved id.
                        mLastMessageId = 0;
                    }
                    /// @}
                    /// M: Code analyze 027,Add for deleting one message.@{
                    MmsLog.d(TAG, "onDeleteComplete(): before update mConversation, ThreadId = " + mConversation.getThreadId());
                    ContactList recipients = getRecipients();
                    mConversation = Conversation.upDateThread(ComposeMessageActivity.this, mConversation.getThreadId(), false);
                    mThreadCountManager.isFull(mThreadId, ComposeMessageActivity.this,
                            ThreadCountManager.OP_FLAG_DECREASE);
                    /// @}
                    // Update the notification for new messages since they
                    // may be deleted.
                    MessagingNotification.nonBlockingUpdateNewMessageIndicator(
                            ComposeMessageActivity.this, MessagingNotification.THREAD_NONE, false);
                    // Update the notification for failed messages since they
                    // may be deleted.
                    updateSendFailedNotification();
                    /// M: Code analyze 027,Add for deleting one message.@{
                    MessagingNotification.updateDownloadFailedNotification(ComposeMessageActivity.this);
                    MmsLog.d(TAG, "onDeleteComplete(): MessageCount = " + mConversation.getMessageCount() +
                            ", ThreadId = " + mConversation.getThreadId());
                    if (mConversation.getMessageCount() <= 0 || mConversation.getThreadId() <= 0L) {
                        mMsgListAdapter.changeCursor(null);
                        if (needSaveDraft() && (recipients != null)) {
                            if (!isRecipientsEditorVisible()) {
                                makeDraftEditable(recipients);
                            }
                        } else {
                            /// M: fix bug for ConversationList select all performance ,update selected threads array.@{
                            ConversationListAdapter.removeSelectedState(mSelectedThreadId);
                            /// @
                            finish();
                        }
                    }
                    /// @}
                    break;
            }
            // If we're deleting the whole conversation, throw away
            // our current working message and bail.
            if (token == ConversationList.DELETE_CONVERSATION_TOKEN) {
                ContactList recipients = mConversation.getRecipients();
                mWorkingMessage.discard();

                // Remove any recipients referenced by this single thread from the
                // contacts cache. It's possible for two or more threads to reference
                // the same contact. That's ok if we remove it. We'll recreate that contact
                // when we init all Conversations below.
                if (recipients != null) {
                    for (Contact contact : recipients) {
                        contact.removeFromCache();
                    }
                }

                // Make sure the conversation cache reflects the threads in the DB.
                Conversation.init(getApplicationContext());
                finish();
            } else if (token == DELETE_MESSAGE_TOKEN) {
                /// M: Code analyze 027,Add for deleting one message.@{
                // Check to see if we just deleted the last message
                startMsgListQuery(MESSAGE_LIST_QUERY_AFTER_DELETE_TOKEN, 0);
                /// @}
            }
        }
    }

    @Override
    public void onUpdate(final Contact updated) {
        /** M:
         * In a bad case ANR will happen. When many contact is update, onUpdate will be
         * invoked very frequently,and the code here will run many times. In mRecipientsEditor,
         * if there are 100[can be more?] recipients,
         * mRecipientsEditor.constructContactsFromInput is time cost.
         * ANR may happen if process many this message consequently.
         * so reduce the frequence, and touch event message have more changces to process.
         */
        if (isRecipientsEditorVisible()) {
            return;
        }
        if (Build.TYPE.equals("eng")) {
            log("[CMA] onUpdate contact updated: " + updated);
        }
        if (mPrevRunnable != null) {
            mMessageListItemHandler.removeCallbacks(mPrevRunnable);
        }
        mPrevRunnable = new Runnable() {
            public void run() {
                ContactList recipients = getRecipients();
                if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                    log("[CMA] onUpdate contact updated: " + updated);
                    log("[CMA] onUpdate recipients: " + recipients);
                }
                updateTitle(recipients);
                /// M: Contact is modified, update the menu icon of contact.
                invalidateOptionsMenu();

                // The contact information for one (or more) of the recipients has changed.
                // Rebuild the message list so each MessageItem will get the last contact info.
                ComposeMessageActivity.this.mMsgListAdapter.notifyDataSetChanged();
            }
        };
        /// M: Using an existing handler for the post, rather than conjuring up a new one.
        mMessageListItemHandler.postDelayed(mPrevRunnable, UPDATE_DELAY);
    }

    private void addRecipientsListeners() {
        Contact.addListener(this);
    }

    private void removeRecipientsListeners() {
        Contact.removeListener(this);
    }

    public static Intent createIntent(Context context, long threadId) {
        Intent intent = new Intent(context, ComposeMessageActivity.class);

        if (threadId > 0) {
            intent.setData(Conversation.getUri(threadId));
        }

        return intent;
    }

    private String getBody(Uri uri) {
        if (uri == null) {
            return null;
        }
        String urlStr = uri.getSchemeSpecificPart();
        if (!urlStr.contains("?")) {
            return null;
        }
        urlStr = urlStr.substring(urlStr.indexOf('?') + 1);
        String[] params = urlStr.split("&");
        for (String p : params) {
            if (p.startsWith("body=")) {
                try {
                    return URLDecoder.decode(p.substring(5), "UTF-8");
                } catch (UnsupportedEncodingException e) { }
            }
        }
        return null;
    }

    private void updateThreadIdIfRunning() {
        if (mIsRunning && mConversation != null) {
            if (mConversation.getMessageCount() > 0) {
                MessagingNotification.setCurrentlyDisplayedThreadId(mConversation.getThreadId());
            } else {
                MessagingNotification.setCurrentlyDisplayedThreadId(MessagingNotification.THREAD_NONE);
            }
        }
        // If we're not running, but resume later, the current thread ID will be set in onResume()
    }

     //////////////////////////////////////////////////////////////////////////////////////
     // MTK add

     /// M: Code analyze 012, add for multi-delete @{
     public static final int REQUEST_CODE_FOR_MULTIDELETE  = 110;
     /// @}

     /// M: Code analyze 025, Add video or audio attachment and check the attachment size.@{
     public static final int MIN_SIZE_FOR_CAPTURE_VIDEO    = 1024 * 10;  // 10K
     public static final int MIN_SIZE_FOR_RECORD_AUDIO = 1024 * 5; // 5K
     // M: fix bug ALPS00354728
     private boolean mAppendAttachmentSign = true;
     /// @}

     /// M: Code analyze 014, Add quick text. @{
     private static final int MENU_ADD_QUICK_TEXT         = 8;
     private AlertDialog mQuickTextDialog;
     /// @}

     /// M: Code analyze 015, Add text vcard. @{
     private static final int MENU_ADD_TEXT_VCARD         = 9;
     public static final int REQUEST_CODE_TEXT_VCARD       = 22;
     /// @}

     private static final int MENU_CALL_RECIPIENT_BY_VT  = 10;
     /// M: Code analyze 016, Add for select text copy. @{
     private static final int MENU_SELECT_TEXT             = 36;
     /// @}

     private static final String SIGN_CREATE_AFTER_KILL_BY_SYSTEM = "ForCreateAfterKilledBySystem";

     /// M: Code analyze 017, Handle forwarded message.(see:forwardMessage())@{
     public static final String SMS_ADDRESS = "sms_address";
     public static final String SMS_BODY = "sms_body";
     public static final String FORWARD_MESSAGE = "forwarded_message";
     /// @}

     // State variable indicating an image is being compressed, which may take a while.
     private boolean mCompressingImage = false;
     private int mToastCountForResizeImage = 0; // For indicate whether show toast message for
                        //resize image or not. If mToastCountForResizeImage equals 0, show toast.
     /// M: Code analyze 010, Support dirtory mode. @{
     private int mHomeBox = 0;
     /// @}
     private Toast mExceedMessageSizeToast = null;

     /// M: Code analyze 009,Show attachment dialog . @{
     private SoloAlertDialog mSoloAlertDialog;
     /// @}

     /// M: Code analyze 047, Extra uri from message body and get number from uri.
     /// Then use this number to update contact cache. @{
     private boolean mNeedUpdateContactForMessageContent = true;
     /// @}

     private boolean  mDrawBottomPanel = false;

     /// M: the member is only used by onUpdate
     private static final long UPDATE_DELAY = 100L;

     /// M: Code analyze 011, use another method for performance
     ///(use this to limit the contact query count) @{
     private static final int UPDATE_LIMIT_LANDSCAPE = 20;
     private static final int UPDATE_LIMIT_PORTRAIT = 20;
     /// @}

     ///@ M:fix bug ALPS00871320 tablet has wide space to show more contact
     private static final int UPDATE_LIMIT_LANDSCAPE_TABLET = 30;
     private static final int UPDATE_LIMIT_PORTRAIT_TABLET = 30;

     private int getLimitedContact() {
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        int updateLimit = 0;

        boolean isTablet = getResources().getBoolean(R.bool.isTablet);
        if (isPortrait) {
            updateLimit = isTablet ? UPDATE_LIMIT_PORTRAIT_TABLET
                    : UPDATE_LIMIT_PORTRAIT;
        } else {
            updateLimit = isTablet ? UPDATE_LIMIT_LANDSCAPE_TABLET
                    : UPDATE_LIMIT_LANDSCAPE;
        }
        return updateLimit;
     }
     
     ///@
     private Runnable mPrevRunnable;
     private boolean mNeedSaveAsMms = false;

     /// M: Code analyze 038, If the user is editing slideshow now.
     /// Do not allow the activity finish but return directly when back key is entered. @{
     private boolean mIsEditingSlideshow = false;
     /// @}

     /// M: Code analyze 026, If the two clicks are too close. @{
     private long mAttachClickTime = -1;
     private long mEditClickTime = -1;
     /// @}

     // mAudioUri will look like this: content://media/external/images/media
     private static final String mAudioUri = Audio.Media.getContentUri("external").toString();

     /// M: Code analyze 050, Add scroll listener and touch listener for MessageListView.@{
     private static int CHANGE_SCROLL_LISTENER_MIN_CURSOR_COUNT = 100;
     /*HQ_sunli  HQ01428291  20151014 begin*/
     private MyScrollListener mScrollListener = new MyScrollListener(
     /*HQ_sunli  HQ01428291  20151014 end*/
             CHANGE_SCROLL_LISTENER_MIN_CURSOR_COUNT, "MessageList_Scroll_Tread");
     /// @}

     /// M: Code analyze 018, Add ringtone for sound attachment.  @{
     public static final int REQUEST_CODE_ATTACH_RINGTONE  = 20;
     /// @}

     /// M: Code analyze 019, Add vcard attachment.  @{
     public static final int REQUEST_CODE_ATTACH_VCARD       = 21;
     /// @}

     /// M: Code analyze 020, Add vcalendar attachment.  @{
     public static final int REQUEST_CODE_ATTACH_VCALENDAR = 25;
     /// @}

     /// M: Code analyze 021, Copy all valid parts of the attachment(pdu) to SD card.
     /// This opeartor will be removed to a separate activity.    @{
     public static final int REQUEST_CODE_MULTI_SAVE       = 23;
     /// @}

     /// M: Code analyze 022, Add bookmark. @{
     private static final int MENU_ADD_TO_BOOKMARK         = 35;
     private ArrayList<String> mURLs = new ArrayList<String>();
     /// @}

     /// M: Code analyze 007, Get information from Sub or save message to Sub. @{
     private static final int MENU_SAVE_MESSAGE_TO_SUB     = 32;
     private static final int SUB_SELECT_FOR_SEND_MSG                     = 1;
     private static final int SUB_SELECT_FOR_SAVE_MSG_TO_SUB             = 2;
     private static final int MSG_QUIT_SAVE_MESSAGE_THREAD                 = 100;
     private static final int MSG_SAVE_MESSAGE_TO_SUB                     = 102;
     private static final int MSG_SAVE_MESSAGE_TO_SUB_AFTER_SELECT_SUB     = 104;
     private static final int MSG_SAVE_MESSAGE_TO_SUB_SUCCEED             = 106;
     private static final int MSG_SAVE_MESSAGE_TO_SUB_FAILED_GENERIC     = 108;
     private static final int MSG_SAVE_MESSAGE_TO_SUB_FAILED_SUB_FULL     = 110;

     private static final String SELECT_TYPE    = "Select_type";
     private int mSubCount; //The count of current sub cards.  0/1/2
    private List<SubscriptionInfo> mSubInfoList;
     private Handler mSaveMsgHandler = null;
     private Thread mSaveMsgThread = null;
     private AlertDialog mSubSelectDialog;
     private int mMessageSubId;
    private int mAssociatedSubId;
    private int mSelectedSubId;
	private int mSelectedSimId = 0;/*HQ_zhangjing add for al812 mms ui  */
     /// @}

     /// M: Code analyze 048, Add this can send msg from a marked sub card
     /// which is delivered in Intent.@{
     private int mSendSubIdForDualBtn = -1;
     /// @}

     /// M: Code analyze 006, Control Sub indicator on status bar. @{
     private StatusBarManager mStatusBarManager;
     private ComponentName mComponentName;
     private boolean mIsShowSubIndicator = true;
     /// @}

     /// M: Code analyze 056,Now,the sms recipient limit is different from mms.
     /// We can set limit for sms or mms individually. @{
     private static final int RECIPIENTS_LIMIT_FOR_SMS     = MmsConfig.getSmsRecipientLimit();
     /// @}
     private boolean mIsTooManyRecipients;     // Whether the recipients are too many

     /// M: Code analyze 046, Whether the recipientedit control has been initialized. @{
     private boolean isInitRecipientsEditor = true; // true, init mRecipientsEditor and add recipients;
                                                   // false, init mRecipientsEditor, but recipients
     /// @}

     private boolean mWaitingForSendMessage;

     /// M: Code analyze 023, Delete the char value of '\r' . @{
     private static final String STR_RN = "\\r\\n"; // for "\r\n"
     private static final String STR_CN = "\n"; // the char value of '\n'
     /// @}
     public static boolean mDestroy = false;

     /// M: Code analyze 027,Add for deleting one message.@{
     private ThreadCountManager mThreadCountManager = ThreadCountManager.getInstance();
     private Long mThreadId = -1L;
     /// @}

     /// M: Code analyze 002,  If a new ComposeMessageActivity is created, kill old one
     private static WeakReference<ComposeMessageActivity> sCompose = null;
     /// @}

     private static String sTextEditorText;
     private long mLastThreadIdFromNotification;
     
     private boolean mSendButtonCanResponse = true;    // can click send button
     private static final long RESUME_BUTTON_INTERVAL = 1000;
     private static final int MSG_RESUME_SEND_BUTTON  = 112;

     /// M: Code analyze 024, If the click operator can be responsed. @{
     boolean mClickCanResponse = true;           // can click button or some view items
     /// @}

     /// M: Code analyze 013, Get contacts from Contact app . @{
     // handle NullPointerException in onActivityResult() for pick up recipients
     private boolean mIsRecipientHasIntentNotHandle = false;
     private Intent mIntent = null;
     private boolean misPickContatct = false;
     /// @}

     /// M: Code analyze 004, Set max height for text editor. @{
     private HeightChangedLinearLayout mHeightChangedLinearLayout;
     private static final int mReferencedTextEditorTwoLinesHeight = 65;
     private static final int mReferencedTextEditorThreeLinesHeight = 110;
     private static final int mReferencedTextEditorFourLinesHeight    = 140;
     private static final int mReferencedTextEditorSevenLinesHeight = 224;
     private static final int mReferencedAttachmentEditorHeight     = 266;
     private static final int mReferencedMaxHeight                    = 800;
     private int mCurrentMaxHeight                                    = 800;
     /// @}

     /// M: Code analyze 001, Plugin opeartor. @{
     private IMmsComposeExt mMmsComposePlugin = null;
     private IMmsUtilsExt mMmsUtils = null;
     /// @}

     /// M: Code analyze 036, Change text size if adjust font size.@{
     private IMmsTextSizeAdjustExt mMmsTextSizeAdjustPlugin = null;
     /// @}

     /// M: Code analyze 042, If you discard the draft message manually.@{
     private boolean mHasDiscardWorkingMessage = false;
     /// @}

     /// M: Plug-in for OP09.
     private IStringReplacementExt mStringReplacementPlugin;

    /// M: fix bug ALPS00572383, delay show "suggested" on the SelectSubDialog @{
    private final HashMap<Integer, Integer> mHashSub = new HashMap<Integer, Integer>();
    private static final int MSG_SELECT_SUB_DIALOG_SHOW = 500;
    private int mAssociatedSubQueryDone;
    private int mSelectSubType;
    /// @}

    private static final int MSG_DISMISS_CONTACT_PICK_DIALOG = 9009;
    
    /// M:
    private Handler mUiHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_SAVE_MESSAGE_TO_SUB_SUCCEED:
                Toast.makeText(ComposeMessageActivity.this, R.string.save_message_to_sim_successful, Toast.LENGTH_SHORT).show();
                // M: fix for bug ALPS01468873
                //sendBroadcast(new Intent(ManageSimMessages.ACTION_NOTIFY_SIMMESSAGE_UPDATE));
                break;

            case MSG_SAVE_MESSAGE_TO_SUB_FAILED_GENERIC:
                Toast.makeText(ComposeMessageActivity.this, R.string.save_message_to_sim_unsuccessful, Toast.LENGTH_SHORT).show();
                break;

            case MSG_SAVE_MESSAGE_TO_SUB_FAILED_SUB_FULL:
                /// M: For OP09 Feature, replace "SIM" to "UIM". @{
                int slotId = -1;
                if (mSubCount == 1) {
                    slotId = mSubInfoList.get(0).getSimSlotIndex(); //getSlot();
                } else {
                    if (mSelectedSubId > 0) {
                        SubscriptionInfo subInfo = SubscriptionManager.from(
                                MmsApp.getApplication()).getActiveSubscriptionInfo(mSelectedSubId);
                        if (subInfo != null) {
                            slotId = subInfo.getSimSlotIndex();
                        }
                    }
                }
                String ctString = mStringReplacementPlugin.getStrings(IStringReplacementExt.UIM_FULL_TITLE);
                Toast.makeText(ComposeMessageActivity.this,
                        getString(R.string.save_message_to_sim_unsuccessful) + ". "
                        + ((MmsConfig.isStringReplaceEnable() && ctString != null && slotId == 0)
                                ? ctString : getString(R.string.sim_full_title)),
                        Toast.LENGTH_SHORT).show();
                /// @}
                break;
            /// M: Code analyze 007, Get information from Sub or save message to Sub. @{
            case MSG_SAVE_MESSAGE_TO_SUB: //multi sub cards
                String type = (String) msg.obj;
                long msgId = msg.arg1;
                saveMessageToSub(type, msgId);
                break;
            /// @}
            case MSG_RESUME_SEND_BUTTON:
                mSendButtonCanResponse = true;
                break;
            case MSG_SELECT_SUB_DIALOG_SHOW:
                /// M: fix bug ALPS00572383, delay show "suggested" on the SelectSimDialog @{
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        int position = -1;
                        if (mHashSub != null && mSelectSubType == SUB_SELECT_FOR_SEND_MSG) {
                            for (int i = 0; i < mHashSub.size(); i++) {
                                if (mHashSub != null && mHashSub.get(i) == mAssociatedSubQueryDone) {
                                    position = i;
                                    break;
                                }
                            }

                            if (position != -1 && mSubSelectDialog != null
                                                        && mSubSelectDialog.isShowing()) {
                                ListView listView = mSubSelectDialog.getListView();
                                if (listView != null) {
                                    View view = listView.getChildAt(position);
                                    if (view != null) {
                                       TextView textView =
                                           (TextView) view.findViewById(R.id.sim_suggested);
                                       if (textView != null) {
                                           textView.setText(getString(R.string.suggested));
                                       }
                                    }
                                }
                                mHashSub.clear();
                            }
                        }
                    }
                });
                break;
                /// @}
            case MSG_DISMISS_CONTACT_PICK_DIALOG:
                mUiHandler.removeCallbacks(mContactPickRunnable);
                if (mContactPickDialog != null && mContactPickDialog.isShowing()) {
                    mContactPickDialog.dismiss();
                }
                mContactPickDialog = null;
                break;
            default:
                MmsLog.d(TAG, "inUIHandler msg unhandled.");
                break;
            }
        }
    };

    /// M: Code analyze 007, Get information from Sub or save message to Sub. @{
    private final class SaveMsgThread extends Thread {
        private String msgType = null;
        private long msgId = 0;
        public SaveMsgThread(String type, long id) {
            msgType = type;
            msgId = id;
        }
        public void run() {
            Looper.prepare();
            if (null != Looper.myLooper()) {
                mSaveMsgHandler = new SaveMsgHandler(Looper.myLooper());
            }
            Message msg = mSaveMsgHandler.obtainMessage(MSG_SAVE_MESSAGE_TO_SUB);
            msg.arg1 = (int) msgId;
            msg.obj = msgType;
            if (mSubCount > 1) { // multi sub cards
                mUiHandler.sendMessage(msg);
            } else {
                mSaveMsgHandler.sendMessage(msg); //single sub card
            }
            Looper.loop();
        }
    }

    private final class SaveMsgHandler extends Handler {
        public SaveMsgHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_QUIT_SAVE_MESSAGE_THREAD: {
                    MmsLog.v(MmsApp.TXN_TAG, "exit save message thread");
                    getLooper().quit();
                    break;
                }

                case MSG_SAVE_MESSAGE_TO_SUB: { //single sub card
                    String type = (String) msg.obj;
                    long msgId = msg.arg1;
                    //saveMessageTo(type, msgId);
                    getMessageAndSaveToSub(type, msgId);
                    break;
                }

                case MSG_SAVE_MESSAGE_TO_SUB_AFTER_SELECT_SUB: {
                    Intent it = (Intent) msg.obj;
                    getMessageAndSaveToSub(it);
                    break;
                }

                default:
                    break;
            }
        }
    }

    private void saveMessageToSub(String msgType, long msgId) { //multi sub cards exist
        MmsLog.d(MmsApp.TXN_TAG, "save message to sub, message type:" + msgType
                + "; message id:" + msgId + "; sub count:" + mSubCount);

        Intent intent = new Intent();
        intent.putExtra("message_type", msgType);
        intent.putExtra("message_id", msgId);
        intent.putExtra(SELECT_TYPE, SUB_SELECT_FOR_SAVE_MSG_TO_SUB);
        mSelectSubType = SUB_SELECT_FOR_SAVE_MSG_TO_SUB;
        showSubSelectedDialog(intent);
    }

    private void getMessageAndSaveToSub(Intent intent) {
        MmsLog.v(MmsApp.TXN_TAG, "get message and save to sub, selected sub id = " + mSelectedSubId);
        String msgType = intent.getStringExtra("message_type");
        long msgId = intent.getLongExtra("message_id", 0);
        if (msgType == null) {
            //mSaveMsgHandler.sendEmptyMessage(MSG_SAVE_MESSAGE_TO_SIM_FAILED_GENERIC);
            mUiHandler.sendEmptyMessage(MSG_SAVE_MESSAGE_TO_SUB_FAILED_GENERIC);
            return;
        }
        getMessageAndSaveToSub(msgType, msgId);
    }

    private void getMessageAndSaveToSub(String msgType, long msgId) {
        int result = 0;
        ///M: ALPS00726802, get the orignal position when long click this item
        Cursor cursor = mMsgListAdapter.getCursor();
        if (mClickedItemPosition >= 0 && mClickedItemPosition < cursor.getCount()) {
            cursor.moveToPosition(mClickedItemPosition);
        }
        MessageItem msgItem = getMessageItem(msgType, msgId, true);
        if (msgItem == null || msgItem.mBody == null) {
            MmsLog.e(MmsApp.TXN_TAG, "getMessageAndSaveToSub, can not get Message Item.");
            return;
        }

        String scAddress = null;

        ArrayList<String> messages = null;
        messages = SmsManager.getDefault().divideMessage(msgItem.mBody);

        int smsStatus = 0;
        long timeStamp = 0;
        if (msgItem.isReceivedMessage()) {
            smsStatus = SmsManager.STATUS_ON_ICC_READ;
            timeStamp = msgItem.mSmsDate;
            scAddress = msgItem.getServiceCenter();
        } else if (msgItem.isSentMessage()) {
            smsStatus = SmsManager.STATUS_ON_ICC_SENT;
        } else if (msgItem.isFailedMessage()) {
            smsStatus = SmsManager.STATUS_ON_ICC_UNSENT;
        } else {
            MmsLog.w(MmsApp.TXN_TAG, "Unknown sms status");
        }
        int subId = -1;
        if (mSubCount == 1) {
            mSelectedSubId = (int) mSubInfoList.get(0).getSubscriptionId();
        }
        subId = mSelectedSubId;
        if (scAddress == null) {
                scAddress = TelephonyManagerEx.getDefault().getScAddress(subId);
        }
        if (Build.TYPE.equals("eng")) {
            MmsLog.d(MmsApp.TXN_TAG, "\t subId\t= " + subId);
            MmsLog.d(MmsApp.TXN_TAG, "\t scAddress\t= " + scAddress);
            MmsLog.d(MmsApp.TXN_TAG, "\t Address\t= " + msgItem.mAddress);
            MmsLog.d(MmsApp.TXN_TAG, "\t msgBody\t= " + msgItem.mBody);
            MmsLog.d(MmsApp.TXN_TAG, "\t smsStatus\t= " + smsStatus);
            MmsLog.d(MmsApp.TXN_TAG, "\t timeStamp\t= " + timeStamp);
            MmsLog.d(MmsApp.TXN_TAG, "\t messages size\t= " + messages.size());
        }

        /// M: add for ALPS01844319, check SIM whether locked first, return fail if locked. @{
        TelephonyManager telephony = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        int simState = telephony.getSimState(SubscriptionManager.getSlotId(subId));
        MmsLog.d(MmsApp.TXN_TAG, "\t simState \t= " + simState);
        // set result as fail if SIM locked.
        if (simState == TelephonyManager.SIM_STATE_PIN_REQUIRED) {
            result = SmsManager.RESULT_ERROR_GENERIC_FAILURE;
        } else {
            result = SmsManager.getSmsManagerForSubscriptionId(subId).copyTextMessageToIccCard(
                    scAddress, msgItem.mAddress, messages, smsStatus, timeStamp);
        }
        /// @}
        MmsLog.d(MmsApp.TXN_TAG, "\t result\t= " + result);

        /// M: Modify for OP09 : save massTextMsg to sub @{
        if (MmsConfig.isMassTextEnable() && msgItem.mIpMessageId < 0
            && result != SmsManager.RESULT_ERROR_SUCCESS
            && result != SmsManager.RESULT_ERROR_SIM_MEM_FULL) {
            result = mMmsComposePlugin.getSmsMessageAndSaveToSim(mConversation.getRecipients().getNumbers(), scAddress, messages,
                smsStatus, timeStamp, subId, result);
        }
        /// @}
        if (result == SmsManager.RESULT_ERROR_SUCCESS) {
            MmsLog.d(MmsApp.TXN_TAG, "save message to sub succeed.");
            mUiHandler.sendEmptyMessage(MSG_SAVE_MESSAGE_TO_SUB_SUCCEED);
        } else if (result == SmsManager.RESULT_ERROR_SIM_MEM_FULL) {
            MmsLog.w(MmsApp.TXN_TAG, "save message to sub failed: sub memory full.");
            mUiHandler.sendEmptyMessage(MSG_SAVE_MESSAGE_TO_SUB_FAILED_SUB_FULL);
        } else {
            MmsLog.w(MmsApp.TXN_TAG, "save message to sub failed: generic error.");
            mUiHandler.sendEmptyMessage(MSG_SAVE_MESSAGE_TO_SUB_FAILED_GENERIC);
        }
        mSaveMsgHandler.sendEmptyMessageDelayed(MSG_QUIT_SAVE_MESSAGE_THREAD, 5000);
    }

    Runnable mGetSubInfoRunnable = new Runnable() {
        public void run() {
            getSubInfoList();
            if (MmsConfig.isDualSendButtonEnable()) {
                if (mWorkingMessage.hasSlideshow()) {
                    updateSendButtonState();
                } else {
                    mMmsComposePlugin.setDualSendButtonType(mDualSendBtnListener);
                }
            }
        }
    };

     private void getSubInfoList() {
        mSubInfoList = SubscriptionManager.from(this).getActiveSubscriptionInfoList();
        mSubCount = (mSubInfoList != null && !mSubInfoList.isEmpty()) ? mSubInfoList.size() : 0;
        MmsLog.v(TAG, "ComposeMessageActivity.getSubInfoList(): mSubCount = " + mSubCount);
    }
/*HQ_zhangjing add for al812 on2015-07-03 for resend the failed sms begin*/
     private int getSubSelection() {
	 	Log.d(TAG,"mSubCount = " + mSubCount);
        if (mSendSubIdForDualBtn >= 1) {
            mSelectedSubId = mSendSubIdForDualBtn;
            mSendSubIdForDualBtn = -1;
            MmsLog.d(TAG, "send msg from mSendSubIdForDualBtn = " + mSelectedSubId);
        } else if (mSubCount == 1) {
            mSelectedSubId = (int) mSubInfoList.get(0).getSubscriptionId();
        } else if (mSubCount > 1) { // multi sub cards exist.
            Intent intent = new Intent();
            mSelectSubType = SUB_SELECT_FOR_SEND_MSG;
            intent.putExtra(SELECT_TYPE, SUB_SELECT_FOR_SEND_MSG);
            // getContactSIM
            if (isRecipientsEditorVisible()) {
                if (mRecipientsEditor.getRecipientCount() == 1/*isOnlyOneRecipient()*/) {
                    mAssociatedSubId = getContactSub(mRecipientsEditor.getNumbers().get(0)); // 152188888888 is a contact number
                } else {
                    mAssociatedSubId = -1;
                }
            } else {
                if (getRecipients().size() == 1/*isOnlyOneRecipient()*/) {
                    mAssociatedSubId = getContactSub(getRecipients().get(0).getNumber()); // 152188888888 is a contact number
                } else {
                    mAssociatedSubId = -1;
                }
            }
            MmsLog.d(TAG, "mAssociatedSubId = " + mAssociatedSubId);
            // getDefaultSIM()
            mMessageSubId = SubscriptionManager.getDefaultSmsSubId();
            MmsLog.d(TAG, "mMessageSubId = " + mMessageSubId);
            if (mMessageSubId == DefaultSmsSimSettings.ASK_USER_SUB_ID || 
                    (mMessageSubId == Settings.System.SMS_SIM_SETTING_AUTO && MmsConfig.isSupportAutoSelectSubId())) {
                // always ask, show SIM selection dialog
                showSubSelectedDialog(intent);
                updateSendButtonState();  //?????
            } else if (mMessageSubId == Settings.System.DEFAULT_SIM_NOT_SET) {
                /*
                 * not set default Sub:
                 * if recipients are morn than 2,or there is no associated Sub,
                 * show SIM selection dialog
                 * else send message via associated SIM
                 */
                if (mAssociatedSubId == -1) {
                    showSubSelectedDialog(intent);
                    updateSendButtonState(); //?????
                } else {
                    mSelectedSubId = mAssociatedSubId;
                    confirmSendMessageIfNeeded();  //?????
                }
            } else {
                /*
                 * default SIM:
                 * if recipients are morn than 2,or there is no associated SIM,
                 * send message via default SIM
                 * else show SIM selection dialog
                 */
                boolean isAssociatedSubExsit = false;
                List<SubscriptionInfo> subinfoList = SubscriptionManager.from(this).getActiveSubscriptionInfoList();
                int subCount = 0;
                if (subinfoList != null) {
                    subCount = subinfoList.size();
                }
                for (int slotId = 0; slotId < subCount; slotId++) {
                    SubscriptionInfo subInfo = subinfoList.get(slotId);
                    if (subInfo != null) {
                        int subId = subInfo.getSubscriptionId();
                        isAssociatedSubExsit = (subId == mAssociatedSubId ? true : false);
                        if (isAssociatedSubExsit) {
                            break;
                        }
                    }
                }
                if ((mAssociatedSubId == -1 || (mMessageSubId == mAssociatedSubId)
                        || !isAssociatedSubExsit) && MessageUtils.isSmsSubIdActive(this, mMessageSubId)) {
                    mSelectedSubId = (int) mMessageSubId;
                } else {
                    showSubSelectedDialog(intent);
                }
            }
        }
		return mSelectedSubId;
    }
	
     private void subSelection() {
		mSelectedSubId = mSelectedSimId;
		confirmSendMessageIfNeeded();

    }

/*HQ_zhangjing add for al812 on2015-07-03 for resend the failed sms end*/



    @Override
    public void onDialogClick(int subId, Intent intent) {
        mSelectedSubId = (int) subId;
        if (intent.getIntExtra(SELECT_TYPE, -1) == SUB_SELECT_FOR_SEND_MSG) {
            confirmSendMessageIfNeeded();
            mIsOneSubSelected = true;
        } else if (intent.getIntExtra(SELECT_TYPE, -1) == SUB_SELECT_FOR_SAVE_MSG_TO_SUB) {
            // getMessageAndSaveToSim(it);
            Message msg = mSaveMsgHandler.obtainMessage(MSG_SAVE_MESSAGE_TO_SUB_AFTER_SELECT_SUB);
            msg.obj = intent;
            // mSaveMsgHandler.sendMessageDelayed(msg, 60);
            mSaveMsgHandler.sendMessage(msg);
        }
    }

    @Override
    public void onCancelClick() {

    }

    @Override
    public void onDialogDismiss() {
        if (isRecipientsEditorVisible() && mCutRecipients != null) {
            mRecipientsEditor.removeChipChangedListener(mChipWatcher);
            mRecipientsEditor.populate(new ContactList());
            mRecipientsEditor.addChipChangedListener(mChipWatcher);
            mRecipientsEditor.populate(mCutRecipients);
            mCutRecipients = null;
            // / M : Fix CR ALPS01009525
            // / which will a empty thread in the widget that send group message
            // from contact @{
            if (mOldThreadID > 0) {
                Conversation.asyncDeleteObsoleteThreadID(mBackgroundQueryHandler, mOldThreadID);
            }
            // / @}
        } else if (mCutRecipients != null) {
            if (mConversation != null) {
                Log.d(TAG,
                        "onDismiss, mark old thread draft status to false: "
                                + mConversation.getThreadId());
                mConversation.setDraftState(false);
            }
            mWorkingMessage.syncWorkingRecipients();
            Conversation conv = mWorkingMessage.getConversation();
            long threadId = 0L;
            if (conv != null) {
                conv.ensureThreadId();
                threadId = conv.getThreadId();
            }
            // / M: fix bug ALPS00595715 @{
            mWorkingMessage.saveDraft(false);
            if (mIsOneSubSelected) {
                startMsgListQuery(MESSAGE_LIST_QUERY_TOKEN, 0);
                mIsOneSubSelected = false;
            } else {
                startActivity(createIntent(ComposeMessageActivity.this, threadId));
            }
            // /@}
            // / M: fix bug ALPS00595715 @{
            if (mOldThreadID > 0 && mOldThreadID != threadId) {
                mIsSameConv = false;
                MmsLog.d(TAG, "onDismiss not same thread");
            }
            mCutRecipients = null;
        }
    }

    private boolean mIsOneSubSelected = false;

    private void showSubSelectedDialog(Intent intent) {
        mIsOneSubSelected = false;
        SubSelectDialog subSelectDialog = new SubSelectDialog(this, this);

        /// M: For OP09 Feature, replace "Sub" to "UIM". @{
        String str = mStringReplacementPlugin.getStrings(IStringReplacementExt.SELECT_CARD);
        if (MmsConfig.isStringReplaceEnable() && str != null) {
            mSubSelectDialog = subSelectDialog.showSubSelectedDialog(true, str, intent);
        } else {
            mSubSelectDialog = subSelectDialog.showSubSelectedDialog(true, null, intent);
        }
        /// @}

        /// M: fix bug ALPS00572383, delay show "suggested" on the SelectSubDialog @{
        if (mAssociatedSubQueryDone != -1) {
            mUiHandler.sendEmptyMessage(MSG_SELECT_SUB_DIALOG_SHOW);
        }
        /// @}
    }
    /// @}
    /// M: Code analyze 003,  Set or get max mms size.
    private void initMessageSettings() {
        MessageUtils.setMmsLimitSize(this);
    }
    /// @}

    private void showConfirmDialog(Uri uri, boolean append, int type, int messageId) {
        if (isFinishing()) {
            return;
        }

        final Uri mRestrictedMidea = uri;
        final boolean mRestrictedAppend = append;
        final int mRestrictedType = type;

        new AlertDialog.Builder(ComposeMessageActivity.this)
        .setTitle(R.string.unsupport_media_type)
        .setIconAttribute(android.R.attr.alertDialogIcon)
        .setMessage(messageId)
        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public final void onClick(DialogInterface dialog, int which) {
                    /// M: disable when non-default sms
                    if (!mIsSmsEnabled) {
                        Toast.makeText(ComposeMessageActivity.this, R.string.compose_disabled_toast, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (mRestrictedMidea == null || mRestrictedType == WorkingMessage.TEXT
                        || mWorkingMessage.isDiscarded()) {
                        return;
                    }
                    getAsyncDialog().runAsync(new Runnable() {
                        public void run() {
                            /// M: fix bug ALPS01258201, show progessDialog
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    Log.d(TAG, "reCreate and show ProgressDialog");
                                    getAsyncDialog().reCreateProgressDialog(R.string.adding_attachments_title);
                                    getAsyncDialog().resetShowProgressDialog();
                                }
                            });
                            int createMode = WorkingMessage.sCreationMode;
                            WorkingMessage.sCreationMode = 0;
                            int result = mWorkingMessage.setAttachment(mRestrictedType, mRestrictedMidea,
                                mRestrictedAppend);
                            if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                                log("Restricted Midea: dataUri=" + mRestrictedMidea);
                            }
                            if (mRestrictedType == WorkingMessage.IMAGE
                                && (result == WorkingMessage.IMAGE_TOO_LARGE || result == WorkingMessage.MESSAGE_SIZE_EXCEEDED)) {
                                MmsLog.d(TAG, "showConfirmDialog: resize image " + mRestrictedMidea);
                                MessageUtils.resizeImage(ComposeMessageActivity.this, mRestrictedMidea, mAttachmentEditorHandler, mResizeImageCallback, mRestrictedAppend,
                                    true);
                                WorkingMessage.sCreationMode = createMode;
                                dismissProgressDialog();
                                return;
                            }
                            WorkingMessage.sCreationMode = createMode;
                            int typeId = R.string.type_picture;
                            if (mRestrictedType == WorkingMessage.AUDIO) {
                                typeId = R.string.type_audio;
                            } else if (mRestrictedType == WorkingMessage.VIDEO) {
                                typeId = R.string.type_video;
                            }
                            handleAddAttachmentError(result, typeId);
                            /// M: fix bug ALPS00726611, must save draft when click on WARNING_TYPE
                            if (result == WorkingMessage.OK) {
                                if (mWorkingMessage.saveAsMms(false) != null) {
                                    mHasDiscardWorkingMessage = true;
                                }
                            }
                            dismissProgressDialog();
                        }
                    }, new ShowRunnable(), R.string.adding_attachments_title);
                    Log.d(TAG, "Composer add mAsyncTaskNum = " + (++mAsyncTaskNum));
            }
        })
        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public final void onClick(DialogInterface dialog, int which) {
                mWorkingMessage.removeFakeMmsForDraft();
                updateSendButtonState();
            }
        })
        .setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface arg0) {
                mWorkingMessage.removeFakeMmsForDraft();
                updateSendButtonState();
            }
        })
        .show();
    }

     /// M: Code analyze 067, Add audio attachment. @{
    private void addAudio(final Uri uri, final boolean append) {
        mNeedSaveAsMms = false;
        if (uri != null) {
            mNeedSaveAsMms = true;
            int result = WorkingMessage.OK;
            try {
                if (append) {
                    mWorkingMessage.checkSizeBeforeAppend();
                }
            } catch (ExceedMessageSizeException e) {
                result = WorkingMessage.MESSAGE_SIZE_EXCEEDED;
                handleAddAttachmentError(result, R.string.type_audio);
                mNeedSaveAsMms = false;
                return;
            }
            result = mWorkingMessage.setAttachment(WorkingMessage.AUDIO, uri, append);
            if (result == WorkingMessage.WARNING_TYPE) {
                mNeedSaveAsMms = false;
                runOnUiThread(new Runnable() {
                    public void run() {
                        showConfirmDialog(uri, append, WorkingMessage.AUDIO, R.string.confirm_restricted_audio);
                    }
                });
            } else {
                handleAddAttachmentError(result, R.string.type_audio);
                if (result != WorkingMessage.OK) {
                    mNeedSaveAsMms = false;
                }
            }
        }
    }
    /// @}

    /// M: Code analyze 015, Add text vcard. @{
    private void addTextVCardAsync(final long[] contactsIds) {
        MmsLog.i(TAG, "compose.addTextVCardAsync(): contactsIds.length() = " + contactsIds.length);
        getAsyncDialog().runAsync(new Runnable() {
            public void run() {
                //addTextVCard(contactsIds);
               String textVCard = TextUtils.isEmpty(mTextEditor.getText()) ? "" : "\n";
               VCardAttachment tvc = new VCardAttachment(ComposeMessageActivity.this);
               final String textString = tvc.getTextVCardString(contactsIds, textVCard);
               runOnUiThread(new Runnable() {
               public void run() {
                   insertText(mTextEditor, textString);
               }

             });
           }
        }, null, R.string.menu_insert_text_vcard); // the string is ok for reuse[or use a new string].
    }
    /// @}

    private void addFileAttachment(String type, Uri uri, boolean append) {
        if (!addFileAttachment(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, uri, append)) {
            if (!addFileAttachment(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, uri, append)) {
                if (!addFileAttachment(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, uri, append)) {
                    MmsLog.i(TAG, "This file is not in media store(audio, video or image)," +
                            "attemp to add it like file uri");
                    addAttachment(type, (Uri) uri, append);
                }
            }
        }
    }

    private boolean addFileAttachment(Uri mediaStoreUri, Uri uri, boolean append) {
        String path = uri.getPath();
        if (path != null) {
            Cursor c = getContentResolver().query(mediaStoreUri,
                    new String[] {MediaStore.MediaColumns._ID, Audio.Media.MIME_TYPE}, MediaStore.MediaColumns.DATA + "=?",
                    new String[] {path}, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        Uri contentUri = Uri.withAppendedPath(mediaStoreUri, c.getString(0));
                        MmsLog.i(TAG, "Get id in MediaStore:" + c.getString(0));
                        MmsLog.i(TAG, "Get content type in MediaStore:" + c.getString(1));
                        MmsLog.i(TAG, "Get uri in MediaStore:" + contentUri);

                        String contentType = c.getString(1);
                        addAttachment(contentType, contentUri, append);
                        return true;
                    } else {
                        MmsLog.i(TAG, "MediaStore:" + mediaStoreUri.toString() + " has not this file");
                    }
                } finally {
                    c.close();
                }
            }
        }
        return false;
    }

    private boolean isHasRecipientCount() {
        int recipientCount = recipientCount();
        return (recipientCount > 0 && recipientCount < RECIPIENTS_LIMIT_FOR_SMS);
    }

    private String getResourcesString(int id) {
        Resources r = getResources();
        return r.getString(id);
    }

    /// M: Code analyze 030, Check condition before sending message.@{
    private void checkConditionsAndSendMessage(final boolean bCheckEcmMode) {
        // check pin
        if (mSelectedSubId <= 0) {
            mSelectedSubId = SubscriptionManager.getDefaultSubId();
        }
        // add CellConnMgr feature
        final CellConnMgr cellConnMgr = new CellConnMgr(getApplicationContext());
        final int state = cellConnMgr.getCurrentState(mSelectedSubId, CellConnMgr.STATE_FLIGHT_MODE
              | CellConnMgr.STATE_SIM_LOCKED | CellConnMgr.STATE_RADIO_OFF);
        MmsLog.d(TAG,"CellConnMgr, state is " + state);
        ///M: WFC: Do not show pop-up, if wfc is OFF @ {
        if (!MessageUtils.isWfcOn(this) &&
            (((state & CellConnMgr.STATE_FLIGHT_MODE) == CellConnMgr.STATE_FLIGHT_MODE ) ||
            ((state & CellConnMgr.STATE_RADIO_OFF) == CellConnMgr.STATE_RADIO_OFF ) ||
            ((state & (CellConnMgr.STATE_FLIGHT_MODE | CellConnMgr.STATE_RADIO_OFF))
                == (CellConnMgr.STATE_FLIGHT_MODE | CellConnMgr.STATE_RADIO_OFF))))  {
            final ArrayList<String> stringArray = cellConnMgr.getStringUsingState(mSelectedSubId, state);
            MmsLog.d(TAG,"CellConnMgr, stringArray length is " + stringArray.size());
            if(stringArray.size() == 4){
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(stringArray.get(0));
                builder.setMessage(stringArray.get(1));
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        mTextEditor.requestFocus();
                        updateSendButtonState(true);
                    }
                });
                mPlaneModeDialog = builder.show();
                mPlaneModeDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        dialog.dismiss();
                        mPlaneModeDialog = null;
                        updateSendButtonState(true);
                    }
                });
            }
        } else if((state & CellConnMgr.STATE_SIM_LOCKED) == CellConnMgr.STATE_SIM_LOCKED ){
            final ArrayList<String> stringArray = cellConnMgr.getStringUsingState(mSelectedSubId, state);
            MmsLog.d(TAG,"CellConnMgr, stringArray length is " + stringArray.size());
            if(stringArray.size() == 4){
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(stringArray.get(0));
                builder.setCancelable(true);
                builder.setMessage(stringArray.get(1));
                builder.setPositiveButton(stringArray.get(2), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                            cellConnMgr.handleRequest(mSelectedSubId, state);
                            mTextEditor.requestFocus();
                            updateSendButtonState(true);
                    }
                });
                builder.setNegativeButton(stringArray.get(3), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            mTextEditor.requestFocus();
                            updateSendButtonState(true);
                    }
                });
                builder.show();
            }
        } else {
            checkIpMessageBeforeSendMessage(bCheckEcmMode);
        }
   }
   /// @}
	/*HQ_zhangjing add for al812 mms ui begin */
   private void refreshSendBtnText(){
	   String operator1Name = getResourcesString( R.string.send );
	   String operator2Name = getResourcesString( R.string.send );
	   Log.d("zhangjing","mSubCount = " + mSubCount);
	   if( mSubCount > 1 ){
	   	   Log.d("zhangjing","mSubCount = " + mSubCount);
		   operator1Name = mSubInfoList.get(0).getDisplayName().toString();
		   operator2Name = mSubInfoList.get(1).getDisplayName().toString();
	   	   Log.d("zhangjing","operator1Name = " + operator1Name + ",operator2Name = " + operator2Name);
		   mSub1SendButton.setText( operator1Name );
		   mSub2SendButton.setText( operator2Name );
	   }
   }
   private void showSendBtn(){
   	
   	}
/*HQ_zhangjing add for al812 mms ui end */

/*HQ_zhangjing add for al812 on2015-07-03 for resend the failed sms begin*/
	public synchronized void resendFailedMessage(long msgId,int subId) {
	Log.d(TAG,"msgId = " + msgId +" subId = " + subId);
	/*
		ContentValues values = new ContentValues(3);
   
		values.put(Sms.TYPE, Sms.MESSAGE_TYPE_QUEUED);
		//values.put(Sms.STATUS, Sms.STATUS_PENDING);
		values.put(Sms.DATE, System.currentTimeMillis());
		values.put(Sms.SUBSCRIPTION_ID, subId);
		
		try {
			SqliteWrapper.update(getApplicationContext(), getContentResolver(),
					Outbox.CONTENT_URI, values, "type = "
							+ Sms.MESSAGE_TYPE_FAILED + " AND  _id = " + String.valueOf(msgId), null);
		} catch (SQLiteDiskIOException e) {
			Log.e("MMSLog","SQLiteDiskIOException caught while move outbox message to queue box",e);
		}
		
		//send resend intent to service
		Uri msgUri = ContentUris.withAppendedId(Sms.CONTENT_URI, msgId);
		Intent intent  = new Intent("android.huaqin.action.RESEND",
				msgUri,
				ComposeMessageActivity.this,
				SmsReceiver.class);
		intent.putExtra("simId", subId);
		sendBroadcast(intent);
		*/
		Uri msgUri = ContentUris.withAppendedId(Sms.CONTENT_URI, msgId);
		Long date = System.currentTimeMillis();
		ContentValues values = new ContentValues(2);
		values.put(Sms.DATE, date);
		values.put(Sms.SUBSCRIPTION_ID, subId);
		getContentResolver().update(msgUri, values, null, null);
		
		// Move the failed message from failed box to queued box.
		boolean isMoved = Sms.moveMessageToFolder(this, msgUri, Sms.MESSAGE_TYPE_QUEUED, 0);
		Log.d(TAG, "Move message to queued box: " + isMoved);
		
		// Send Broadcast and handled by the SmsReceiverService
		Intent intent  = new Intent("android.huaqin.action.RESEND",
				msgUri,
				ComposeMessageActivity.this,
				SmsReceiver.class);
		intent.putExtra("subId", subId);
		sendBroadcast(intent);
	}
   
	private void simSelectionForSendFaildMsg(long msgId){
		Log.d(TAG,"mSubCount = "  + mSubCount);
		if (mSubCount == 0) {
			// SendButton can't click in this case
		} else if (mSubCount == 1) {
			if(mSubInfoList != null && mSubInfoList.size() > 0){
				mSelectedSimId = (int) mSubInfoList.get(0).getSubscriptionId();
			}
			resendFailedMessage(msgId,mSelectedSimId);
		}
		else if (mSubCount > 1) {
			//mSelectedSimId = (int) mSubInfoList.get(0).getSubscriptionId();//select the sim id
			mSelectedSimId = getSubSelection();
			resendFailedMessage(msgId,mSelectedSimId);
		}
	}
	//at   2012
	/*HQ_zhangjing 2015-10-22 modified for send btn begin*/
	private String getMmsSize(){
		int totalSize = 0;
		String mmsSize = "";
        if ( mWorkingMessage != null && mWorkingMessage.hasAttachment()) {
            totalSize = mWorkingMessage.getCurrentMessageSize();
			totalSize = (totalSize - 1) / 1024 + 1;
			mmsSize = totalSize + "K/" + MmsConfig.getUserSetMmsSizeLimit(false) + "K";
        }
		return mmsSize;
	}

    private boolean isInAirplaneMode(){
		int airplaneMode = Settings.System.getInt(mContentResolver,
				Settings.System.AIRPLANE_MODE_ON, 0) ;
		return (airplaneMode == 1 );
    }

	private int getForbiddenSimcard(){
        /* dualSimMode: 0 => both are off, 1 => SIM1 is on, 2 => SIM2 is on, 3 => both is on */
        int dualSimMode = Settings.System.getInt(mContentResolver,
                Settings.System.MSIM_MODE_SETTING, -1);
		Log.d("send_btn","dualSimMode = " + dualSimMode);
		return dualSimMode;
		
	}
    private void showOrHideSendbtn(){
		boolean isAirplaneMode = isInAirplaneMode();
		int forbidenSimCard = getForbiddenSimcard();
		Log.d("send_btn","mSubCount = " + mSubCount);
	   	if( mSubCount > 1 ){
			mUniqueSendBtnContainer.setVisibility( View.GONE );
			if( isPreparedForSending() ){
				if( isAirplaneMode || forbidenSimCard != 3 ){
					mDoubleSendBtnContainer.setVisibility( View.GONE );
					mUniqueSendBtnContainer.setVisibility( View.VISIBLE );
					if(mWorkingMessage != null && mWorkingMessage.hasSlideshow() && mAttachmentEditor != null ){
						mAttachmentEditor.hideSendBtn( true );
					}
				}else{
					mDoubleSendBtnContainer.setVisibility( View.VISIBLE );
					String recipientText ="";
					if(mRecipientsEditor == null){
						 recipientText = " ";
					}else{
					     recipientText = mRecipientsEditor.getText() == null ? "" : mRecipientsEditor.getText().toString();
					}
					boolean showTextCount = mSubCount > 0 && !TextUtils.isEmpty(recipientText) && mIsSmsEnabled
											&& (mWorkingMessage.hasAttachment() || mWorkingMessage.hasText()
													|| mWorkingMessage.hasSubject() || mIpMessageDraft != null);
					Log.d("HQWH","showTextCount="+showTextCount);
					if( !showTextCount ){
						//mTextCounterForDouble.setVisibility(View.VISIBLE);
						mTextCounterForDouble.setText("160/0");
					}

					
					if(mWorkingMessage != null && mWorkingMessage.hasSlideshow() && mAttachmentEditor != null ){
						mAttachmentEditor.hideSendBtn( false );
					}
				}
			}else{
				mDoubleSendBtnContainer.setVisibility( View.GONE );
			}
			
	   	}else if( mSubCount > 0 ){
			if( isPreparedForSending() ){
				mUniqueSendBtnContainer.setVisibility( View.VISIBLE );
			}else{
				mUniqueSendBtnContainer.setVisibility( View.GONE );
			}
			mDoubleSendBtnContainer.setVisibility( View.GONE );
	   	}else{
			mDoubleSendBtnContainer.setVisibility( View.GONE );
			mUniqueSendBtnContainer.setVisibility( View.GONE );
	   	}		
	}
   private void updateMultiBtnState( boolean shouldEnabled ){

		boolean isAirplaneMode = isInAirplaneMode();
		int forbidenSimCard = getForbiddenSimcard();
		boolean enable = shouldEnabled;
   		if( mSubCount > 1 && !isAirplaneMode && forbidenSimCard == 3){//display two send btn
			Log.d("send_btn","TelephonyManager.getDefault().isMultiSimEnabled() and mSubCount == 2");

			boolean isSim1Enabled = false;
			boolean isSim2Enabled = false;

			isSim1Enabled = enable;
			isSim2Enabled = enable;
			//the above code is neeed to take consider agagin
			mSub1SendButton.setEnabled(isSim1Enabled);
			mSub1SendButton.setFocusable(isSim1Enabled);
			
			mSub2SendButton.setEnabled(isSim2Enabled);
			mSub2SendButton.setFocusable(isSim2Enabled);
			
			refreshSendBtnText();
   		}else{//display one send btn
   			enable = enable  && forbidenSimCard != 0;
			boolean requiresMms = mWorkingMessage.requiresMms();
			View sendButton = showSmsOrMmsSendButton(requiresMms);
			if (sendButton != null) {
				sendButton.setEnabled(enable);
				sendButton.setFocusable(enable);

			}
   		
   		}
		
		  
   	}

    /// M: Code analyze 049, Update send button or attachment editor state.@{
    private void updateSendButtonState(final boolean enable) {
		/*HQ_zhangjing add for al812 mms ui begin */
		boolean isAirplaneMode = isInAirplaneMode();
		boolean enabled = enable && !isAirplaneMode;
		/*HQ_zhangjing add for al812 mms ui end */
        if (!mWorkingMessage.hasSlideshow()) {
            View sendButton = showSmsOrMmsSendButton(mWorkingMessage.requiresMms());
            /// M: for OP09
			/*HQ_zhangjing 2015-11-02 modified for CQ HQ01478873 begin*/
            /*if (false && MmsConfig.isDualSendButtonEnable()) {
                // mMmsComposePlugin.setCTSendButtonType();
                if (mSubCount < 1) {
                    sendButton.setEnabled(enabled);
                    sendButton.setFocusable(enabled);
                }
                mMmsComposePlugin.updateDualSendButtonStatue(enabled, mWorkingMessage.requiresMms());
                return;
            }*/
			/*HQ_zhangjing 2015-11-02 modified for CQ HQ01478873 end*/
            sendButton.setEnabled(enabled);
            sendButton.setFocusable(enabled);
        } else {
            mAttachmentEditor.setCanSend(enabled && (mSubCount > 0));
        }
		/*HQ_zhangjing add for al812 mms ui begin */
		showOrHideSendbtn();
		updateMultiBtnState( enabled );
		/*HQ_zhangjing add for al812 mms ui end */

    }
    /// @}

    private void insertText(EditText edit, String insertText) {
        int where = edit.getSelectionStart();

        if (where == -1) {
            edit.append(insertText);
        } else {
            edit.getText().insert(where, insertText);
        }
    }

    /**
     * This filter will constrain edits not to make the length of the text
     * greater than the specified length.
     */
    class TextLengthFilter extends InputFilter.LengthFilter {
        public TextLengthFilter(int max) {
            super(max);
            mMaxLength = max;
            mExceedMessageSizeToast = Toast.makeText(ComposeMessageActivity.this,
                    R.string.exceed_editor_size_limitation,
                    Toast.LENGTH_SHORT);
        }

        public CharSequence filter(CharSequence source, int start, int end,
                                   Spanned dest, int dstart, int dend) {
            if (Build.TYPE.equals("eng")) {
                MmsLog.d(TAG, "TextLengthFilter source = " + source +
                        " start = " + start + " end = " + end + " dstart = "
                        + dstart + " dend = " + dend + " dest length = " + dest.length());
            }
            int keep = mMaxLength - (dest.length() - (dend - dstart));
            if (keep < (end - start)) {
                mExceedMessageSizeToast.show();
                mInputMethodManager.restartInput(ComposeMessageActivity.this.getWindow().getCurrentFocus());
            }
            return super.filter(source, start, end, dest, dstart, dend);
            /*
            if (keep <= 0) {
                return "";
            } else if (keep >= end - start) {
                return null; // keep original
            } else {
                return source.subSequence(start, start + keep);
            }
            */
        }

        private int mMaxLength;
    }

    /// M: Code analyze 051, Hide input keyboard.@{
    private void hideInputMethod() {
        MmsLog.d(TAG, "hideInputMethod()");
        if (this.getWindow() != null && this.getWindow().getCurrentFocus() != null) {
            mInputMethodManager.hideSoftInputFromWindow(this.getWindow().getCurrentFocus().getWindowToken(), 0);
        }
    }
    /// @}

    // toast there are too many recipients.
    private void toastTooManyRecipients(int recipientCount) {
        final String tooManyRecipients = getString(R.string.too_many_recipients, recipientCount, RECIPIENTS_LIMIT_FOR_SMS);
        mUiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ComposeMessageActivity.this, tooManyRecipients, Toast.LENGTH_LONG).show();
            }
        }, 1000);
    }

    /// M: Code analyze 013, Get contacts from Contact app . @{
    private void addContacts(int pickCount, int requestCode) {
        /// M: @{
        /*Intent intent = new Intent("android.intent.action.CONTACTSMULTICHOICE");
          intent.setType(Phone.CONTENT_ITEM_TYPE);
          intent.putExtra("request_email", true);
          intent.putExtra("pick_count", pickCount);
          misPickContatct = true;
          startActivityForResult(intent, REQUEST_CODE_PICK_CONTACT);*/
        try {
            /// M: fix bug ALPS00444752, set true to disable to Show ContactPicker
            mShowingContactPicker = true;
            misPickContatct = true;
            Intent intent = new Intent(MessageUtils.ACTION_CONTACT_SELECTION);
            intent.setType(Phone.CONTENT_TYPE);
            /// M: OP09 add For pick limit: set number balance for picking contacts; As a common function
            intent.putExtra(PICK_CONTACT_NUMBER_BALANCE, pickCount);
			//HQ_zhangjing 2015-10-09 modified for CQ HQ01435286
			intent.putExtra("pick_contacts_for_sms",1);
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            mShowingContactPicker = false;
            misPickContatct = false;
            /// M: add for mms cc feature. OP09 requested.
            mPickRecipientsCc = false;
            Toast.makeText(this, this.getString(R.string.no_application_response), Toast.LENGTH_SHORT).show();
            MmsLog.e(TAG, e.getMessage());
        }
        /// @}
    }

    private int getContactSub(final String num) {
        class Int {
            private int value = -1;
            public void  set(int n) {
                value = n;
            }
            public int get() {
                return value;
            }
        }
        final Int subID = new Int();
        final Object dbQueryLock = new Object();
        final Context mContextTemp = this.getApplicationContext();
        // query the db in another thread.
        new Thread(new Runnable() {
            public void run() {
                int subId = -1;
                String number = num;
                String formatNumber = MessageUtils.formatNumber(number, mContextTemp);
                String TrimFormatNumber = formatNumber;
                if (formatNumber != null) {
                    TrimFormatNumber = formatNumber.replace(" ", "");
                }
                Cursor associateSubCursor = ComposeMessageActivity.this.getContentResolver().query(
                    Data.CONTENT_URI,
                    new String[]{ContactsContract.Data.SIM_ASSOCIATION_ID},
                    Data.MIMETYPE + "='" + CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                    + "' AND (" + Data.DATA1 + "=?" +
                    " OR " + Data.DATA1 + "=?" +
                    " OR " + Data.DATA4 + "=?" +
                    ") AND (" + ContactsContract.Data.SIM_ASSOCIATION_ID + "!= -1)",
                    new String[]{number, formatNumber, TrimFormatNumber},
                    null
                );

                if ((null != associateSubCursor) && (associateSubCursor.getCount() > 0)) {
                    associateSubCursor.moveToFirst();
                    // Get only one record is OK
                    subId = (Integer) associateSubCursor.getInt(0);
                } else {
                    subId = -1;
                }
                if (associateSubCursor != null) {
                    associateSubCursor.close();
                }
                synchronized (dbQueryLock) {
                    MmsLog.d(MmsApp.TXN_TAG, "before notify");
                    subID.set(subId);
                    dbQueryLock.notify();
                    /// M: fix bug ALPS00572383, delay show "suggested" on the SelectSimDialog @{
                    mAssociatedSubQueryDone = subId;
                }
                /// @}
            }
        }).start();
        // change UI thread wait from 500ms to 1000ms at most, for CR ALPS00721717
        synchronized (dbQueryLock) {
            try {
                MmsLog.d(MmsApp.TXN_TAG, "before wait");
                dbQueryLock.wait(1000);
            } catch (InterruptedException e) {
                //time out
            }
            MmsLog.d(MmsApp.TXN_TAG, "subID = " + subID.get());
            return subID.get();
        }
    }

    private void checkRecipientsCount() {
        //if (isRecipientsEditorVisible()) {
        //mRecipientsEditor.structLastRecipient();
        //}
//        hideInputMethod();
        if (mMmsUtils != null
            && !mMmsUtils.allowSafeDraft(this, MmsConfig.getDeviceStorageFullStatus(), true,
                IMmsUtilsExt.TOAST_TYPE_FOR_SEND_MSG)) {
            updateSendButtonState();
            return;
        }
        final int mmsLimitCount = MmsConfig.getMmsRecipientLimit();
        /// M: support mms cc feature. OP09 requested. add cc check.
        if (mWorkingMessage.requiresMms() && (recipientCount() > mmsLimitCount || isRecipientsCcTooMany())) {
            /// M: support mms cc feature. OP09 requested.
            String message = "";
            if (recipientCount() > mmsLimitCount) {
                message = getString(R.string.max_recipients_message, mmsLimitCount);
            }
            if (isRecipientsCcTooMany()) {
                if (!message.equals("")) {
                    message += "\r\n";
                }
                message += getString(R.string.max_cc_message, RECIPIENTS_LIMIT_CC);
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.max_recipients_title);
            builder.setIconAttribute(android.R.attr.alertDialogIcon);
            builder.setCancelable(true);
            builder.setMessage(message);
            builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            /*
                             * If entering an existing thread, #mRecipientsEditor never gets initialized.
                             * So, when mRecipientsEditor is not visible, it might be null.
                             */
                            List<String> recipientsList;
                            if (isRecipientsEditorVisible()) {
                                recipientsList = mRecipientsEditor.getNumbers();
                            } else {
                                recipientsList = new ArrayList<String>(Arrays.asList(getRecipients().getNumbers()));
                            }
                            List<String> newRecipientsList = new ArrayList<String>();

                            if (recipientCount() > mmsLimitCount * 2) {
                                for (int i = 0; i < mmsLimitCount; i++) {
                                    newRecipientsList.add(recipientsList.get(i));
                                }
                                mWorkingMessage.setWorkingRecipients(newRecipientsList);
                            } else {
                                for (int i = recipientCount() - 1; i >= mmsLimitCount; i--) {
                                    recipientsList.remove(i);
                                }
                                mWorkingMessage.setWorkingRecipients(recipientsList);
                                /// M: fix bug ALPS00432629
                                newRecipientsList = recipientsList;
                            }
                            subSelection();

                            /// M: fix bug ALPS00432629, update title
                            /// when recipientsList cut to 20 @{
                            ContactList list = ContactList.getByNumbers(newRecipientsList, false);
                            mCutRecipients = list;
                        }
                    });
                }
            });
            builder.setNegativeButton(R.string.no, null);
            builder.show();
            updateSendButtonState();
        } else {
            /** M:
             * fix CR ALPS00069541
             * if the message copy from sub card with unknown recipient
             * the recipient will be ""
             */
            if (isRecipientsEditorVisible() &&
                    "".equals(mRecipientsEditor.getText().toString().replaceAll(";", "").replaceAll(",", "").trim())) {
                new AlertDialog.Builder(this)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setTitle(R.string.cannot_send_message)
                        .setMessage(R.string.cannot_send_message_reason)
                        .setPositiveButton(R.string.yes, new CancelSendingListener())
                        .show();
            } else if (!isRecipientsEditorVisible() && "".equals(mConversation.getRecipients().serialize().replaceAll(";", "").replaceAll(",", ""))) {
                new AlertDialog.Builder(this)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setTitle(R.string.cannot_send_message)
                        .setMessage(R.string.cannot_send_message_reason)
                        .setPositiveButton(R.string.yes, new CancelSendingListener())
                        .show();
            } else {
                /// M: cmcc feature, reply message with the card directly if only one card related in conversation
                if (MmsConfig.getFolderModeEnabled()) {
                    /// M: modify in op09; The Folder mode and dual send button can not coexist in this code block;
                    if (!MmsConfig.isDualSendButtonEnable() || MmsConfig.isSupportAutoSelectSubId()) {
                        mSendSubIdForDualBtn = getAutoSelectSubId();
                        MmsLog.d(TAG, "mSendSubIdForDualBtn=" + mSendSubIdForDualBtn);
                    }
                }
                subSelection();
            }
        }
    }

    /// this function is for cmcc only
    private int getAutoSelectSubId() {
        int subId = -1;
        boolean isValid = false;
        int subIdinSetting = SubscriptionManager.getDefaultSmsSubId();
        if (subIdinSetting == (int) Settings.System.SMS_SIM_SETTING_AUTO) {
            subId = getIntent().getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID && !MmsConfig.getMmsDirMode()) {
                if (mMsgListAdapter != null) {
                    Cursor c = mMsgListAdapter.getCursor();
                    if (c != null && c.moveToLast()) {
                        int count = c.getCount();
                        MmsLog.d(TAG, "count = " + count);
                        subId = mMsgListAdapter.getSubIdFromCursor(c);
                    }
                }
            }
            for (int i = 0; i < mSubCount; i++) {
                if (mSubInfoList.get(i).getSubscriptionId() == subId) {
                    isValid = true;
                    break;
                }
            }
            if (!isValid) {
                subId = -1;
            }
        }
        return subId;
    }

    /// M: Code analyze 002,  If a new ComposeMessageActivity is created, kill old one
    public static Activity getComposeContext() {
        return sCompose == null ? null : sCompose.get();
    }
    /// @}

   @Override
    public void onShutDown() {
       /// M: fix bug ALPS01539513
       if (mWorkingMessage != null && mWorkingMessage.requiresMms()
               && mWorkingMessage.getSlideshow() != null
               && !mWorkingMessage.getSlideshow().needUpdate()) {
           Log.d(TAG, "onShutDown return");
           return;
       }
       /// M: don't save draft again if Compose is finished.
       if (!this.isFinishing()) {
           saveDraft(false);
       }
       /// @}
    }

    /*
    this function is add for read report
    */
    private final int READ_REPORT_DISABLED                      = 0;
    private final int READ_REPORT_SINGLE_MODE_ENABLED           = 1;
    private final int READ_REPORT_GEMINI_MODE_ENABLED           = 2;
//    private final int READ_REPORT_GEMINI_MODE_ENABLED_SLOT_1    = 4;
//    private final int READ_REPORT_GEMINI_MODE_ENABLED_BOTH      = READ_REPORT_GEMINI_MODE_ENABLED_SLOT_0|READ_REPORT_GEMINI_MODE_ENABLED_SLOT_1;

    private void checkAndSendReadReport() {
        final Context ct = ComposeMessageActivity.this;
        final long threadId = mConversation.getThreadId();
        MmsLog.d(MmsApp.TXN_TAG, "checkAndSendReadReport,threadId:" + threadId);
        new Thread(new Runnable() {
            public void run() {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ComposeMessageActivity.this);
                int rrAllowed = READ_REPORT_DISABLED;
                /// @}
                // / M: [ALPS00465911] [3G Gemini+]JE when Message -> settings
                // -> cell center -> back to idle @{
                int[] totalSubIds = SubscriptionManager.from(ComposeMessageActivity.this).getActiveSubscriptionIdList();
                MmsLog.d(MmsApp.TXN_TAG, "checkAndSendReadReport,totalSubIds = "
                        + totalSubIds.length);
                // / @}
                rrAllowed = READ_REPORT_GEMINI_MODE_ENABLED;

                MmsLog.d(MmsApp.TXN_TAG, "rrAllowed=" + rrAllowed);
                // if read report is off, mark the mms read report status readed.
                if (rrAllowed == READ_REPORT_DISABLED) {
                    ContentValues values = new ContentValues(1);
                    String where = Mms.THREAD_ID + " = " + threadId + " and " + Mms.READ_REPORT + " = 128";
                    // update uri inbox is not used, must indicate here.
                    where += " and " + Mms.MESSAGE_BOX + " = " + Mms.MESSAGE_BOX_INBOX;
                    values.put(Mms.READ_REPORT, PduHeaders.READ_STATUS__DELETED_WITHOUT_BEING_READ);
                    SqliteWrapper.update(ct, ct.getContentResolver(), Mms.Inbox.CONTENT_URI,
                                        values,
                                        where,
                                        null);
                    return;
                }
                if (rrAllowed > READ_REPORT_DISABLED) {
                    StringBuilder suffix = new StringBuilder();
                    switch (rrAllowed) {
                        case READ_REPORT_SINGLE_MODE_ENABLED:
                            // nothing to do in single card mode
                            break;
                        case READ_REPORT_GEMINI_MODE_ENABLED:
                            boolean isAppendAnd = true;
                            for (int subId : totalSubIds) {
                                if (prefs.getBoolean(Long.toString(subId) + "_"
                                        + MmsPreferenceActivity.READ_REPORT_AUTO_REPLY, false)) {
                                    MmsLog.e(MmsApp.TXN_TAG, "readReport supported on subId: "
                                            + subId);
                                    // slot i has card and read report on
                                    if (isAppendAnd) {
                                        suffix.append(Telephony.Mms.SUBSCRIPTION_ID + " = " + subId);
                                        isAppendAnd = false;
                                    } else {
                                        suffix.append(" or " + Telephony.Mms.SUBSCRIPTION_ID + " = " + subId);
                                    }
                                } else {
                                    MmsLog.e(MmsApp.TXN_TAG, "mark subId" + subId + " card readed");
                                    markReadReportProcessed(ct, threadId, subId);
                                }
                            }
                            if (!TextUtils.isEmpty(suffix.toString())) {
                                suffix.insert(0, " and (");
                                suffix.append(") ");
                            }
                            break;
                        default:
                            MmsLog.e(MmsApp.TXN_TAG, "impossible value for rrAllowed.");
                            break;
                        }
                    boolean networkOk = true;/*((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE))
                                        .getNetworkInfo(ConnectivityManager.TYPE_MOBILE_MMS).isAvailable();*/
                    int airplaneMode = Settings.System.getInt(ct.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0);
                    //network not ok.next time will try.
                    if ((networkOk != true) || (airplaneMode == 1)) {
                        MmsLog.d(MmsApp.TXN_TAG, "networkok:" + networkOk + ",airplaneMode:" + airplaneMode);
                        return;
                    }
                    Cursor cs = null;
                    try {
                        String where = Mms.THREAD_ID + " = " + threadId + " and " + Mms.READ_REPORT + " = 128" + suffix.toString();
                        cs = SqliteWrapper.query(ct, ct.getContentResolver(), Mms.Inbox.CONTENT_URI,
                                                new String[]{Mms._ID, Telephony.Mms.SUBSCRIPTION_ID},
                                                where,
                                                null, null);
                        if (cs != null) {
                            final int count = cs.getCount();
                            if (count > 0) {
                                //mark the ones need send read report status to pending as 130.
                                ContentValues values = new ContentValues(1);
                                values.put(Mms.READ_REPORT, PduHeaders.READ_STATUS__DELETED_WITHOUT_BEING_READ);
                                // update uri inbox is not used, must indicate here.
                                where += " and " + Mms.MESSAGE_BOX + " = " + Mms.MESSAGE_BOX_INBOX;
                                SqliteWrapper.update(ct, ct.getContentResolver(), Mms.Inbox.CONTENT_URI,
                                                    values,
                                                    where,
                                                    null);
                                //show a toast.
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        Toast.makeText(ComposeMessageActivity.this,
                                                    ct.getResources().getQuantityString(R.plurals.read_report_toast_msg, count, count),
                                                    Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                            startSendReadReport(cs);
                        }
                    } catch (Exception e) {
                        MmsLog.e(MmsApp.TXN_TAG, "exception happend when scan read report!:" + e.getMessage());
                    } finally {
                        if (cs != null) {
                            cs.close();
                        }
                    }
                }
            }

            private void markReadReportProcessed(Context ct, long threadId, int subId) {
                ContentValues values = new ContentValues(1);
                values.put(Mms.READ_REPORT, PduHeaders.READ_STATUS__DELETED_WITHOUT_BEING_READ);
                String where = Mms.THREAD_ID + " = " + threadId + " and " + Mms.READ_REPORT + " = 128"
                                    + " and " + Telephony.Mms.SUBSCRIPTION_ID + " = " + subId;
                // update uri inbox is not used, must indicate here.
                where += " and " + Mms.MESSAGE_BOX + " = " + Mms.MESSAGE_BOX_INBOX;
                SqliteWrapper.update(ct, ct.getContentResolver(), Mms.Inbox.CONTENT_URI,
                                    values,
                                    where,
                                    null);
            }

            private void startSendReadReport(final Cursor cursor) {
                cursor.moveToPosition(-1);
                while (cursor.moveToNext()) {
                    MmsLog.d(MmsApp.TXN_TAG, "send an intent for read report.");
                    long msgId = cursor.getLong(0);
                    Intent rrIntent = new Intent(ct, TransactionService.class);
                    rrIntent.putExtra(TransactionBundle.URI, Mms.Inbox.CONTENT_URI + "/" + msgId); //the uri of mms that need send rr
                    rrIntent.putExtra(TransactionBundle.TRANSACTION_TYPE, Transaction.READREC_TRANSACTION);

                    int subId = cursor.getInt(1);
                    MmsLog.d(MmsApp.TXN_TAG, "subId:" + subId);
                    rrIntent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);

                    ct.startService(rrIntent);
                }
            }
        }).start();
    }

    /**
     * Remove the number which is the same as any one before;
     * When the count of recipients over the limit, make a toast and remove the recipients over the limit.
     * @param recipientsString the numbers slipt by ','.
     * @return recipientsString the numbers slipt by ',' after modified.
     */
    private String getStringForMultipleRecipients(String recipientsString) {
        recipientsString = recipientsString.replaceAll(",", ";");
        String[] recipients_all = recipientsString.split(";");
        List<String> recipientsList = new ArrayList<String>();
        for (String recipient : recipients_all) {
            recipientsList.add(recipient);
        }

        Set<String> recipientsSet = new HashSet<String>();
        recipientsSet.addAll(recipientsList);

        if (recipientsSet.size() > RECIPIENTS_LIMIT_FOR_SMS) {
            toastTooManyRecipients(recipients_all.length);
        }

        recipientsList.clear();
        recipientsList.addAll(recipientsSet);

        recipientsString = "";
        int count = recipientsList.size() > RECIPIENTS_LIMIT_FOR_SMS ? RECIPIENTS_LIMIT_FOR_SMS : recipientsList.size();
        StringBuffer buf = new StringBuffer();
        buf.append(recipientsString);
        for (int i = 0; i < count; i++) {
            if (i == (count - 1)) {
                buf.append(recipientsList.get(i));
            } else {
                buf.append(recipientsList.get(i) + ";");
            }
        }
        recipientsString = buf.toString();
        return recipientsString;
    }

    public Conversation getConversation() {
        return mConversation;
    }

    /// M: Code analyze 014, Add quick text. @{
    private void showQuickTextDialog() {
        mQuickTextDialog = null;
        //if (mQuickTextDialog == null) {
            List<String> quickTextsList = new ArrayList<String>();

            /// M: new feature, add default quick text when frist "insert quick text" @{
            if (MmsConfig.getInitQuickText()) {
                String[] defaultQuickTexts = getResources().getStringArray(R.array.default_quick_texts);
                for (int i = 0; i < defaultQuickTexts.length; i++) {
                    quickTextsList.add(defaultQuickTexts[i]);
                }
            } else {
                // add user's quick text
                if (MmsConfig.getQuicktexts().size() == 0) {
                    MmsConfig.updateAllQuicktexts();
                }
                quickTextsList = MmsConfig.getQuicktexts();
            }
            /// @}

            List<Map<String, ?>> entries = new ArrayList<Map<String, ?>>();
            for (String text : quickTextsList) {
                HashMap<String, Object> entry = new HashMap<String, Object>();
                entry.put("text", text);
                entries.add(entry);
            }

            final SimpleAdapter qtAdapter = new SimpleAdapter(this, entries, R.layout.quick_text_list_item,
                    new String[] {"text"}, new int[] {R.id.quick_text});

            AlertDialog.Builder qtBuilder = new AlertDialog.Builder(this);

            qtBuilder.setTitle(getString(R.string.select_quick_text));
            qtBuilder.setCancelable(true);
            qtBuilder.setAdapter(qtAdapter, new DialogInterface.OnClickListener() {
                @SuppressWarnings("unchecked")
                public final void onClick(DialogInterface dialog, int which) {
                    HashMap<String, Object> item = (HashMap<String, Object>) qtAdapter.getItem(which);
                    if (mSubjectTextEditor != null && mSubjectTextEditor.isFocused()) {
                        insertText(mSubjectTextEditor, (String) item.get("text"));
                    } else {
                        insertText(mTextEditor, (String) item.get("text"));
                    }
                    dialog.dismiss();
                }
            });
            mQuickTextDialog = qtBuilder.create();
        //}
        mQuickTextDialog.show();
    }
    /// @}

    /// M: Code analyze 006, Control Sub indicator on status bar. @{
    @Override
    public void onSubInforChanged() {
        MmsLog.i(MmsApp.LOG_TAG, "onSubInforChanged(): Composer");
        // show SMS indicator
        if (!isFinishing() && mIsShowSubIndicator) {
            MmsLog.i(MmsApp.LOG_TAG, "Hide current indicator and show new one.");
//            mStatusBarManager.hideSIMIndicator(mComponentName);
//            mStatusBarManager.showSIMIndicator(mComponentName, Settings.System.SMS_SIM_SETTING);
            StatusBarSelectorCreator creator = StatusBarSelectorCreator
                    .getInstance(ComposeMessageActivity.this);
            creator.updateStatusBarData();

        }
        /// M: add for ipmessage
        mMessageSubId = (int) Settings.System.getLong(getContentResolver(),
                Settings.System.SMS_SIM_SETTING,
                Settings.System.DEFAULT_SIM_NOT_SET);
        mIsIpServiceEnabled = MmsConfig.isServiceEnabled(this);
        mSelectedSubId = (int) mMessageSubId;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                /// M: For OP09;
				/*HQ_zhangjing 2015-11-02 modified for CQ HQ01478873 begin*/
                /*if (MmsConfig.isDualSendButtonEnable()) {
                    mMmsComposePlugin.setDualSendButtonType(mDualSendBtnListener);
                }*/
				/*HQ_zhangjing 2015-11-02 modified for CQ HQ01478873 end*/
                updateSendButtonState();
            }
        });
    }
    /// @}

    /// M: Code analyze 004, Set max height for text editor. @{
    private final HeightChangedLinearLayout.LayoutSizeChangedListener mLayoutSizeChangedListener =
            new HeightChangedLinearLayout.LayoutSizeChangedListener() {
        private int mMaxHeight = 0;
        @Override
        public void onLayoutSizeChanged(int w, int h, int oldw, int oldh) {
            /// M: fix bug ALPS00419856, set TextEditor Height = four when unlock screen @{
            if (h - oldh > SOFT_KEY_BOARD_MIN_HEIGHT) {
                mIsSoftKeyBoardShow = false;
            } else {
                mIsSoftKeyBoardShow = true;
            }
            /// @}
            mMaxHeight = (h > mMaxHeight) ? h : mMaxHeight;
            synchronized (mWaitingImeChangedObject) {
                mWaitingImeChangedObject.notifyAll();
                MmsLog.d(TAG, "onLayoutSizeChanged(): object notified.");
            }
            if (h == oldh || mTextEditor == null || mTextEditor.getVisibility() == View.GONE) {
                return;
            }
            MmsLog.d(TAG, "onLayoutSizeChanged(): mIsLandscape = " + mIsLandscape);

            if (!mIsLandscape) {
                if (h > oldh
                        && !isSharePanelShow()
                        && !mShowKeyBoardFromShare) {
                    updateTextEditorHeightInFullScreen();
                } else if (h > oldh
                        && !isSharePanelShow()
                        && !mShowKeyBoardFromShare
                        && !IpMessageUtils.getIpMessagePlugin(ComposeMessageActivity.this)
                                .isActualPlugin()) {
                    updateTextEditorHeightInFullScreen();
                } else {
                    mUiHandler.postDelayed(new Runnable() {
                        public void run() {
                            MmsLog.d(TAG, "onLayoutSizeChanged(): mTextEditor.setMaxHeight: "
                                    + mReferencedTextEditorFourLinesHeight);
                            mTextEditor.setMaxHeight(mReferencedTextEditorFourLinesHeight
                                    * mCurrentMaxHeight / mReferencedMaxHeight);
                        }
                    }, 100);
                }
            }
        }
    };

    private void updateTextEditorHeightInFullScreen() {
        if (mIsLandscape || mTextEditor == null || mTextEditor.getVisibility() == View.GONE) {
            return;
        }
        mUiHandler.postDelayed(new Runnable() {
            public void run() {
                MmsLog.d(TAG, "updateTextEditorHeightInFullScreen()");
                updateFullScreenTextEditorHeight();
            }
        }, 100);
    }

    private void updateFullScreenTextEditorHeight() {
        if (mAttachmentEditor.getVisibility() == View.VISIBLE
                && mAttachmentEditor.getHeight() > 0
                && !mWorkingMessage.hasSlideshow()) {
            MmsLog.d(TAG, "updateTextEditorHeight(): mTextEditor.setMaxHeight: "
                    + (mReferencedTextEditorSevenLinesHeight
                            * mCurrentMaxHeight / mReferencedMaxHeight));
            if (mIsLandscape) {
                mTextEditor.setMaxHeight(
                        mReferencedTextEditorTwoLinesHeight * mCurrentMaxHeight / mReferencedMaxHeight);
            } else {
                if (mIsSoftKeyBoardShow) {
                    mTextEditor.setMaxHeight(mReferencedTextEditorFourLinesHeight
                            * mCurrentMaxHeight / mReferencedMaxHeight);
                } else {
                    mTextEditor.setMaxHeight(mReferencedTextEditorSevenLinesHeight
                            * mCurrentMaxHeight / mReferencedMaxHeight);
                }
            }
        } else {
            /// M: fix bug ALPS00419856, set TextEditor Height = four when unlock screen @{
            if (mIsSoftKeyBoardShow && !mIsLandscape) {
                MmsLog.d(TAG, "updateFullScreenTextEditorHeight() updateTextEditorHeight()" +
                            ": mTextEditor.setMaxHeight: " + (mReferencedTextEditorFourLinesHeight
                                * mCurrentMaxHeight / mReferencedMaxHeight));
                mTextEditor.setMaxHeight(mReferencedTextEditorFourLinesHeight
                        * mCurrentMaxHeight / mReferencedMaxHeight);
            /// @}
            } else if (!mIsSoftKeyBoardShow && !mIsLandscape) {
                MmsLog.d(TAG, "updateTextEditorHeight(): mTextEditor.setMaxHeight: "
                    + ((mReferencedTextEditorSevenLinesHeight + mReferencedAttachmentEditorHeight)
                            * mCurrentMaxHeight / mReferencedMaxHeight));
                if (!isSharePanelShow()) {
                    mTextEditor.setMaxHeight(
                            (mReferencedTextEditorSevenLinesHeight + mReferencedAttachmentEditorHeight)
                            * mCurrentMaxHeight / mReferencedMaxHeight);
                }
            } else {
                MmsLog.d(TAG, "updateTextEditorHeight(): mTextEditor.setMaxHeight: "
                    + (mReferencedTextEditorTwoLinesHeight * mCurrentMaxHeight
                            / mReferencedMaxHeight));
                mTextEditor.setMaxHeight(mReferencedTextEditorTwoLinesHeight * mCurrentMaxHeight
                            / mReferencedMaxHeight);
            }
        }
    }
    /// @}

   @Override
    public void startActivity(Intent intent) {
        try {
            super.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Intent mChooserIntent = Intent.createChooser(intent, null);
            super.startActivity(mChooserIntent);
        }
    }

    /**
     * Simple cache to prevent having to load the same PduBody again and again for the same uri.
     */

    private boolean needSaveDraft() {
        return ((!isRecipientsEditorVisible())
                    || (mRecipientsEditor.hasValidRecipient(mWorkingMessage.requiresMms())))
                && !mWorkingMessage.isDiscarded()
                && (mWorkingMessage.isWorthSaving() || mIpMessageDraft != null);
    }

    public void onPreMmsSent() {
        startMsgListQuery(MESSAGE_LIST_QUERY_TOKEN, 0);
    }

    private boolean checkSlideCount(boolean append) {
        String mMsg = this.getString(R.string.cannot_add_slide_anymore);
        Toast mToast = Toast.makeText(this, mMsg, Toast.LENGTH_SHORT);
        int mSlideCount = 0;
        SlideshowModel slideShow = mWorkingMessage.getSlideshow();
        if (slideShow != null) {
            mSlideCount = slideShow.size();
        }
        if (mSlideCount >= SlideshowEditor.MAX_SLIDE_NUM && append) {
            mToast.show();
            return false;
        }
        return true;
    }

    /// M: Code analyze 003,  Set or get max mms size.
    public static long computeAttachmentSizeLimitForAppen(SlideshowModel slideShow) {
        long sizeLimit = MmsConfig.getUserSetMmsSizeLimit(true) - SlideshowModel.MMS_SLIDESHOW_INIT_SIZE;
        if (slideShow != null) {
            sizeLimit -= slideShow.getCurrentSlideshowSize();
        }
        if (sizeLimit > 0) {
            return sizeLimit;
        }
        return 0;
    }
    /// @}

    /// M: Code analyze 009,Show attachment dialog . @{
    private class SoloAlertDialog extends AlertDialog {
        private AlertDialog mAlertDialog;

        private SoloAlertDialog(Context context) {
            super(context);
        }

        public boolean needShow() {
            return mAlertDialog == null || !mAlertDialog.isShowing();
        }

        public void show(final boolean append) {
            if (!needShow()) {
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setIcon(R.drawable.ic_dialog_attach);
            builder.setTitle(R.string.add_attachment);

            // if (mAttachmentTypeSelectorAdapter == null) {
            // add for vcard, if there is a real slideshow, hide vCard
            int mode = AttachmentTypeSelectorAdapter.MODE_WITH_SLIDESHOW;
            if (mWorkingMessage.hasSlideshow()) {
                mode |= AttachmentTypeSelectorAdapter.MODE_WITHOUT_FILE_ATTACHMENT;
            } else {
                mode |= AttachmentTypeSelectorAdapter.MODE_WITH_FILE_ATTACHMENT;
            }
            if (MessageUtils.isVCalendarAvailable(ComposeMessageActivity.this)) {
                mode |= AttachmentTypeSelectorAdapter.MODE_WITH_VCALENDAR;
            }
            mAttachmentTypeSelectorAdapter = new AttachmentTypeSelectorAdapter(getContext(), mode);
            // }
            builder.setAdapter(mAttachmentTypeSelectorAdapter,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            addAttachment(mAttachmentTypeSelectorAdapter.buttonToCommand(which), append);
                            dialog.dismiss();
                        }
                    });
            mAlertDialog = builder.show();
        }

        public void dismiss() {
            if (mAlertDialog != null && mAlertDialog.isShowing()) {
                mAlertDialog.dismiss();
            }
            super.dismiss();
        }
    }
    /// @}

    private void waitForCompressing() {
        synchronized (ComposeMessageActivity.this) {
            while (mCompressingImage) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    MmsLog.e(TAG, "intterrupted exception e ", e);
                }
            }
        }
    }

    private void notifyCompressingDone() {
        synchronized (ComposeMessageActivity.this) {
            mCompressingImage = false;
            notify();
        }
    }

    private boolean mCompressingVCardFinished = false;

    /// M: change return value to int. if attachFile.length is 0,will return WorkingMessage.UNKNOWN_ERROR
    /// The return value will be used in asyncAttachVCardByContactsId to judge wether need to saveAsMms or not
    private int setFileAttachment(final String fileName, final int type, final boolean append) {
        final File attachFile = getFileStreamPath(fileName);
        MmsLog.d(TAG, "setFileAttachment(): attachFile.exists()?=" + attachFile.exists() +
                        ", attachFile.length()=" + attachFile.length());
        final Resources res = getResources();
        if (attachFile.exists() && attachFile.length() > 0) {
            Uri attachUri = Uri.fromFile(attachFile);
            int result = WorkingMessage.OK;
            try {
                if (append) {
                    mWorkingMessage.checkSizeBeforeAppend();
                }
            } catch (ExceedMessageSizeException e) {
                result = WorkingMessage.MESSAGE_SIZE_EXCEEDED;
                handleAddAttachmentError(result, R.string.type_common_file);
                return result;
            }
            result = mWorkingMessage.setAttachment(type, attachUri, append);
            handleAddAttachmentError(result, R.string.type_common_file);
            mCompressingVCardFinished = true;
            return result;
        } else {
            mUiHandler.post(new Runnable() {
                public void run() {
                    Toast.makeText(ComposeMessageActivity.this,
                            res.getString(R.string.failed_to_add_media, fileName), Toast.LENGTH_SHORT).show();
                }
            });
            return WorkingMessage.UNKNOWN_ERROR;
        }
    }


    private void asyncAttachVCalendar(final Uri eventUri) {
        if (eventUri == null) {
            return;
        }
        getAsyncDialog().runAsync(new Runnable() {
            public void run() {
                attachVCalendar(eventUri);
                /// call saveAsMms to save the draft @{
                mWorkingMessage.saveAsMms(false);
                /// @}
            }
        }, null, R.string.adding_attachments_title);
    }

    private void attachVCalendar(Uri eventUri) {
        if (eventUri == null) {
            Log.e(TAG, "attachVCalendar, oops uri is null");
            return;
        }
        int result = WorkingMessage.OK;

        /// M: for ALPS00644734
        /// M: add Vcalendar will delect all others attachment, so no need check current size
        /// M: OP01 add Vcalendar is append, so still need check @{
        if (mAppendAttachmentSign && MmsConfig.isSupportAttachEnhance()) {
            try {
                mWorkingMessage.checkSizeBeforeAppend();
            } catch (ExceedMessageSizeException e) {
                result = WorkingMessage.MESSAGE_SIZE_EXCEEDED;
                handleAddAttachmentError(result, R.string.type_picture);
                return;
            }
            // add vcalender and OP01
            result = mWorkingMessage.setAttachment(WorkingMessage.VCALENDAR, eventUri, true);
        } else {
            // replace or Not OP01
            result = mWorkingMessage.setAttachment(WorkingMessage.VCALENDAR, eventUri, false);
        }
        /// @}
        handleAddAttachmentError(result, R.string.type_common_file);
    }

    /// M: Code analyze 019, Add vcard attachment.  @{
    private void asyncAttachVCardByContactsId(final Intent data, final boolean isAddingIpMsgVCardFile) {
        if (data == null) {
            return;
        }
        getAsyncDialog().runAsyncInThreadPool(new Runnable() {
            public void run() {
                long[] contactsId = data.getLongArrayExtra("com.mediatek.contacts.list.pickcontactsresult");
                VCardAttachment va = new VCardAttachment(ComposeMessageActivity.this);
                int result = WorkingMessage.OK;
                if (isAddingIpMsgVCardFile) {
                    mIpMessageVcardName = va.getVCardFileNameByContactsId(contactsId, true);
                    mDstPath = IpMessageUtils.getCachePath(ComposeMessageActivity.this) + mIpMessageVcardName;
                    MmsLog.d(IPMSG_TAG, "asyncAttachVCardByContactsId(): mIpMessageVcardName = " + mIpMessageVcardName
                        + ", mDstPath = " + mDstPath);
                    mIpMsgHandler.postDelayed(mSendVcard, 100);
                } else {
                    String fileName = va.getVCardFileNameByContactsId(contactsId, false);
                    /// M: add for attachmentEnhance Modify ALPS00474719 @{
                    if (mAppendAttachmentSign && MmsConfig.isSupportAttachEnhance()) {
                        // add vcard and OP01
                        result = setFileAttachment(fileName, WorkingMessage.VCARD, true);
                    } else {
                        // replace or Not OP01
                        result = setFileAttachment(fileName, WorkingMessage.VCARD, false);
                    }

                    /// Fix CR : ALPS00970618
                    /// Fix CR : ALPS01035223 Add VCard with 1000 contacts cause exceed message limitation
                    /// the result should be OK, that do saveAsMms or there will be errors @{
                    if (result == WorkingMessage.OK) {
                        Log.d("[Mms][Composer]", "asyncAttachVCardByContactsId result is ok");
                        /// Fix CR ALPS01011718 @{
                        if (mCompressingVCardFinished && mWorkingMessage.isDiscarded()) {
                            mWorkingMessage.unDiscard();
                        }
                        /// @}
                        mWorkingMessage.saveAsMms(false);
                    }
                    /// @}
                    MessageUtils.deleteVCardTempFile(getApplicationContext(), fileName);
                }
            }
        }, null, R.string.adding_attachments_title);
    }
    /// @}

    /// M: Code analyze 047, Extra uri from message body and get number from uri.
    /// Then use this number to update contact cache. @{
    private void updateContactCache(Cursor cursor) {
        if (cursor != null) {
            Set<SpannableString> msgs = new HashSet<SpannableString>();
            while (cursor.moveToNext()) {
                String smsBody = cursor.getString(MessageListAdapter.COLUMN_SMS_BODY);

                if (smsBody == null) {
                    continue;
                }

                SpannableString msg = new SpannableString(smsBody);
                msgs.add(msg);
            }
            // update the contact cache in an async thread to avoid ANR
            updateContactCacheAsync(msgs);
        }
    }

    private void updateContactCacheAsync(final Set<SpannableString> msgs) {
        for (SpannableString msg : msgs) {
            Linkify.addLinks(msg, Linkify.ALL);
        }
        new Thread(new Runnable() {
            public void run() {
                Set<String> uriSet = new HashSet<String>();
                for (SpannableString msg : msgs) {
                    List<String> uris = MessageUtils.extractUris(msg.getSpans(0, msg.length(),
                        URLSpan.class));
                    for (String uri : uris) {
                        uriSet.add(uri);
                    }
                }
                for (String uri : uriSet) {
                    String[] body = uri.toLowerCase().split("tel:");
                    if (body.length > 1) {
                        Contact.get(body[1].trim(), false);
                    }
                }
            }
        }).start();
    }
    /// @}

    /// M: Code analyze 001, Plugin opeartor. @{
    private void initPlugin(Context context) {
        mMmsComposePlugin = (IMmsComposeExt) MPlugin.createInstance(
                                 IMmsComposeExt.class.getName(),context);
        MmsLog.d(TAG, "operator mMmsComposePlugin = " + mMmsComposePlugin);
        if(mMmsComposePlugin == null){
            mMmsComposePlugin = new DefaultMmsComposeExt(context);
            MmsLog.d(TAG, "default mMmsComposePlugin = " + mMmsComposePlugin);
        }
        /// M: Code analyze 036, Change text size if adjust font size.@{
        mMmsTextSizeAdjustPlugin = (IMmsTextSizeAdjustExt)
            MmsPluginManager.getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_TEXT_SIZE_ADJUST);
        /// @}
        mMmsUtils = (IMmsUtilsExt) MmsPluginManager.getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_MESSAGE_UTILS);

        /// M: For OP09 Plug-in. @{
        mStringReplacementPlugin = (IStringReplacementExt) MmsPluginManager
                .getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_STRING_REPLACEMENT);
        /// @}
    }
    /// @}

    /// M: Code analyze 036, Change text size if adjust font size.@{
    public void setTextSize(float size) {
        if (mTextEditor != null) {
            mTextEditor.setTextSize(size);
        }
        if (mMsgListAdapter != null) {
            mMsgListAdapter.setTextSize(size);
        }

        if (mMsgListView != null && mMsgListView.getVisibility() == View.VISIBLE) {
            int count = mMsgListView.getChildCount();
            for (int i = 0; i < count; i++) {
			/*HQ_zhangjing add for al812 mms ui begin*/
				View itemView = mMsgListView.getChildAt(i);
				MessageListItem item = null;
				if( itemView != null ){
					TimeAxisWidget taw = (TimeAxisWidget) itemView.findViewById(R.id.item_date);
					item = (MessageListItem)taw.getContent();
				}
                //MessageListItem item =  (MessageListItem) mMsgListView.getChildAt(i);
				/*HQ_zhangjing add for al812 mms ui end*/
                if (item != null) {
                    item.setBodyTextSize(size);
                }
            }
        }
    }
    /// @}

    public boolean  dispatchTouchEvent(MotionEvent ev) {
        boolean ret = false;
        /// M: Code analyze 001, Plugin opeartor. @{
        if (mMmsTextSizeAdjustPlugin != null) {
                ret = mMmsTextSizeAdjustPlugin.dispatchTouchEvent(ev);
            }
        /// @}
        if (!ret) {
            ret = super.dispatchTouchEvent(ev);
        }
        return ret;
    }

    /// M: add for common
    private void initShareRessource() {
        mShareButton = (ImageButton) findViewById(R.id.share_button);
        mShareButton.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mShowKeyBoardFromShare) {
                    showSharePanelOrKeyboard(false, true);
                } else {
                    showSharePanelOrKeyboard(true, false);
                    mTextEditor.requestFocus();
                }
            }
        });
        mSharePanel = (SharePanel) findViewById(R.id.share_panel);
		mPanelContainer = (LinearLayout)findViewById( R.id.panel_container );
        mSharePanel.setHandler(mIpMsgHandler);
		/*HQ_zhangjing add for al812 mms ui begin */
		mUniqueSendBtnContainer = (LinearLayout)findViewById( R.id.button_with_counter );
		mDoubleSendBtnContainer = (LinearLayout)findViewById( R.id.button_send_double );
		/*HQ_zhangjing add for al812 mms ui end */
        showSharePanelOrKeyboard(false, false);
    }
	/*HQ_zhangjing add for al812 mms ui begin */
	private void showSendButton(boolean isShow)
	{
			if(mSub2SendButton!=null && mSub1SendButton != null)
			 {
				  
					   if(TelephonyManager.getDefault().isMultiSimEnabled())
						 {
								if(isShow)
								 {
									  mSub1SendButton.setVisibility(View.VISIBLE);
					  mSub2SendButton.setVisibility(View.VISIBLE);
					  mTextCounterForDouble.setVisibility(View.VISIBLE);
				 }
				 else{
                                         mSub1SendButton.setVisibility(View.GONE);
					 mSub2SendButton.setVisibility(View.GONE);
					 mTextCounterForDouble.setVisibility(View.GONE);
				 }				
			 }							 
		  }
	}
	/*HQ_zhangjing add for al812 mms ui end */
    private void showSharePanel(boolean isShow) {
        if (null != mSharePanel) {
			/*HQ_zhangjing add for al812 mms ui  */
			showSendButton(!isShow);
        /*HQ_sunli 20151113 HQ01499362 begin*/	
           if (!mIsSmsEnabled || mPowerMode.equals("true")) {
        /*HQ_sunli 20151113 HQ01499362 end*/
                mSharePanel.setVisibility(View.GONE);
				
				/*HQ_zhangjing add for al812 mms ui  */
				mPanelContainer.setVisibility(View.GONE);
                mShareButton.setClickable(false);
                mShareButton.setImageResource(R.drawable.ipmsg_share_disable);
                return;
            }
            if (isShow) {
                mSharePanel.setVisibility(View.VISIBLE);
				/*HQ_zhangjing add for al812 mms ui  */
				mPanelContainer.setVisibility(View.VISIBLE);
                mShareButton.setImageResource(R.drawable.ipmsg_keyboard);
            } else {
                mSharePanel.setVisibility(View.GONE);
				/*HQ_zhangjing add for al812 mms ui  */
				mPanelContainer.setVisibility(View.GONE);
                mShareButton.setImageResource(R.drawable.ipmsg_share);
            }
            mShareButton.setClickable(true);
            mShowKeyBoardFromShare = isShow;
            mMmsComposePlugin.hideOrShowSubjectForCC(isRecipientsCcEditorVisible()
                    && (mSubjectTextEditor != null) && isSharePanelShow()
                    && mIsLandscape, mSubjectTextEditor);
        }
    }

    private void showKeyBoard(boolean isShow) {
        if (isShow && mIsSmsEnabled) {
            mTextEditor.requestFocus();
            mInputMethodManager.showSoftInput(mTextEditor, 0);
            mIsKeyboardOpen = true;
        } else {
            hideInputMethod();
        }
    }

    public boolean isSharePanelShow() {
        if (null != mSharePanel && mSharePanel.isShown()) {
            return true;
        }
        return false;
    }

    public void showSharePanelOrKeyboard(final boolean isShowShare, final boolean isShowKeyboard) {
        if (isShowShare && isShowKeyboard) {
            MmsLog.w(IPMSG_TAG, "Can not show both SharePanel and Keyboard");
            return;
        }

        MmsLog.d(TAG, "showSharePanelOrKeyboard(): isShowShare = " + isShowShare
                + ", isShowKeyboard = " + isShowKeyboard + ", mIsSoftKeyBoardShow = "
                + mIsSoftKeyBoardShow);
        if (!isShowKeyboard && mIsSoftKeyBoardShow && !mIsLandscape) {
            if (!isShowShare && mShowKeyBoardFromShare) {
                showSharePanel(isShowShare);
            }
            mShowKeyBoardFromShare = isShowShare;
            showKeyBoard(isShowKeyboard);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    synchronized (mWaitingImeChangedObject) {
                        try {
                            /// M: fix bug ALPS00447850, wait HideSoftKeyBoard longer
                            int waitTime = 300;
                            MmsLog.d(TAG, "showSharePanelOrKeyboard(): object start wait.");
                            mWaitingImeChangedObject.wait(waitTime);
                            MmsLog.d(TAG, "c(): object end wait.");
                        } catch (InterruptedException e) {
                            MmsLog.d(TAG, "InterruptedException");
                        }
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isShowShare) {
                                showSharePanel(isShowShare);
                                if (mIsLandscape) {
                                    mTextEditor.setMaxHeight(mReferencedTextEditorTwoLinesHeight * mCurrentMaxHeight / mReferencedMaxHeight);
                                } else {
                                    mTextEditor.setMaxHeight(mReferencedTextEditorFourLinesHeight * mCurrentMaxHeight / mReferencedMaxHeight);
                                }
                            } else {
                                MmsLog.d(TAG, "showSharePanelOrKeyboard(): new thread.");
                                updateFullScreenTextEditorHeight();
                            }
                        }
                    });
                }
            }).start();
        } else {
            if (isShowShare && !isShowKeyboard && mIsLandscape) {
                showKeyBoard(isShowKeyboard);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (mWaitingImeChangedObject) {
                            try {
                                /// M: fix bug ALPS01297085, wait HideSoftKeyBoard longer
                                int waitTime = 100;
                                MmsLog.d(TAG, "showSharePanelOrKeyboard() mIsLandscape: object start wait.");
                                mWaitingImeChangedObject.wait(waitTime);
                                MmsLog.d(TAG, "c(): mIsLandscape object end wait.");
                            } catch (InterruptedException e) {
                                MmsLog.d(TAG, "InterruptedException");
                            }
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showSharePanel(isShowShare);
                                mTextEditor.setMaxHeight(mReferencedTextEditorTwoLinesHeight
                                        * mCurrentMaxHeight / mReferencedMaxHeight);
                            }
                        });
                    }
                }).start();
            } else {
                showKeyBoard(isShowKeyboard);
                showSharePanel(isShowShare);
                if (isShowShare || isShowKeyboard) {
                    if (mIsLandscape) {
                        mTextEditor.setMaxHeight(
                                mReferencedTextEditorTwoLinesHeight * mCurrentMaxHeight / mReferencedMaxHeight);
                    } else {
                        mTextEditor.setMaxHeight(mReferencedTextEditorFourLinesHeight * mCurrentMaxHeight / mReferencedMaxHeight);
                    }
                } else {
                    MmsLog.d(TAG, "showSharePanelOrKeyboard()");
                    updateFullScreenTextEditorHeight();
                }
            }
        }
    }

    public void hideSharePanel() {
        MmsLog.d(TAG, "hideSharePanel()");
        showSharePanelOrKeyboard(false, false);
        updateFullScreenTextEditorHeight();
    }

    private static void toastNoRecipients(Context context) {
        Toast.makeText(context, IpMessageUtils.getResourceManager(context)
            .getSingleString(IpMessageConsts.string.ipmsg_need_input_recipients), Toast.LENGTH_SHORT).show();
    }

    public void setHandler(Handler handler) {
        mIpMsgHandler = handler;
        if (null != mSharePanel) {
            mSharePanel.setHandler(mIpMsgHandler);
        }
    }

    private void initIpMessageResourceRefs() {
        mIpMessageThumbnail = (ImageView) findViewById(R.id.ip_message_thumbnail);
        /// M: add for ipmessage for thumbnail delete @{
        mIpMessageThumbnailDelete = (ImageView) findViewById(R.id.ip_message_thumbnail_delete);
        mIpMessageThumbnail.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearIpMessageDraft();
            }
        });
        /// @}
        mSendButtonIpMessage = (ImageButton) findViewById(R.id.send_button_ipmsg);
        mSendButtonIpMessage.setOnClickListener(this);

        mTypingStatus = (TextView) findViewById(R.id.ip_message_typing_status);
        mTypingStatusView = (LinearLayout) findViewById(R.id.ip_message_typing_status_view);

        mInviteView = findViewById(R.id.invitation_linear);
        mInviteMsg = (TextView) findViewById(R.id.tv_invite_msg);
        mInvitePostiveBtn = (Button) findViewById(R.id.bt_invite_postive);
        mInviteNegativeBtn = (Button) findViewById(R.id.bt_invite_negative);
    }

    private void doMmsAction(Message msg) {
        int commonAttachmentType = 0;
        Bundle bundle = msg.getData();
        int action = bundle.getInt(SharePanel.SHARE_ACTION);
        switch (action) {
		/*HQ_zhangjing modified for al812 ui begin begin*/
		/*
		case SharePanel.ADD_EMOTICONS:
			//showEmoticonPanel(true);	
			showSharePanelOrKeyboard(false,false);		
			return; 
			*/
		/*HQ_zhangjing modified for al812 ui begin end*/	
        case SharePanel.TAKE_PICTURE:
            commonAttachmentType = AttachmentTypeSelectorAdapter.TAKE_PICTURE;
            break;

        case SharePanel.RECORD_VIDEO:
            commonAttachmentType = AttachmentTypeSelectorAdapter.RECORD_VIDEO;
            break;

        case SharePanel.RECORD_SOUND:
            commonAttachmentType = AttachmentTypeSelectorAdapter.RECORD_SOUND;
            break;

        case SharePanel.ADD_VCARD:
			/*HQ_zhangjing on 2015-09-11 add vcard contacts dialog begin*/
			showShareVcardDialog();
			hideSharePanel();
            return;
			/*HQ_zhangjing on 2015-09-11 add vcard contacts dialog end*/
			
			/*HQ_zhangjing 2015-09-14 add for CQ HQ01310146 begin*/
		case SharePanel.ADD_SUBJECT:
			showSubjectEditor(true);
			mWorkingMessage.setSubject("", true);
			/// M: Code analyze 052, Show input keyboard.@{
			mInputMethodManager.showSoftInput(getCurrentFocus(), InputMethodManager.SHOW_IMPLICIT);
			/// @}
			updateSendButtonState();
			mSubjectTextEditor.requestFocus();
			hideSharePanel();
			return;
		case SharePanel.ADD_COMMON_PHRASE:
			showQuickTextDialog();
			hideSharePanel();
			return;
		/*HQ_zhangjing 2015-09-14 add for CQ HQ01310146  end*/
				
        case SharePanel.ADD_IMAGE:
            commonAttachmentType = AttachmentTypeSelectorAdapter.ADD_IMAGE;
            break;

        case SharePanel.ADD_VIDEO:
            commonAttachmentType = AttachmentTypeSelectorAdapter.ADD_VIDEO;
            break;


        case SharePanel.ADD_SOUND:
            commonAttachmentType = AttachmentTypeSelectorAdapter.ADD_SOUND;
            break;

        case SharePanel.ADD_VCALENDAR:
            commonAttachmentType = AttachmentTypeSelectorAdapter.ADD_VCALENDAR;
            break;

        case SharePanel.ADD_SLIDESHOW:
            commonAttachmentType = AttachmentTypeSelectorAdapter.ADD_SLIDESHOW;
            break;

        default:
            MmsLog.e(IPMSG_TAG, "doMoreActionForMms(): invalid share action type: " + action);
            hideSharePanel();
            return;
        }
        /// M: add for attachment enhance, Vcard and slides can be in same screen
        if (MmsConfig.isSupportAttachEnhance()) {
            // OP01
            addAttachment(commonAttachmentType, true);
            MmsLog.e(TAG, "attach: addAttachment(commonAttachmentType, true)");
        } else {
            addAttachment(commonAttachmentType, !mWorkingMessage.hasAttachedFiles());
            MmsLog.e(TAG, "attach: addAttachment(commonAttachmentType, !hasAttachedFiles())");
        }
        /// @}

        hideSharePanel();
    }
	
	/*HQ_zhangjing on 2015-09-11 add vcard contacts dialog begin*/
	private void showShareVcardDialog(){
		Builder builder = new AlertDialog.Builder(this);
		builder.setItems(getResources().getStringArray(R.array.vcard_type),
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							switch (which) {
								case ADD_TEXT_VCARD:
									Log.d(TAG,"add contact by text");
									Intent intentText = new Intent("android.intent.action.contacts.list.PICKMULTICONTACTS");
									intentText.setType(Contacts.CONTENT_TYPE);
									startActivityForResult(intentText, REQUEST_CODE_TEXT_VCARD);
									break;
								case ADD_VCARD:
									Log.d(TAG,"add contact by vcard");
									Intent intentVcard = new Intent("android.intent.action.contacts.list.PICKMULTICONTACTS");
									intentVcard.setType(Contacts.CONTENT_TYPE);
									startActivityForResult(intentVcard, REQUEST_CODE_ATTACH_VCARD);
									break;
								default:
									break;
							}
						}
					});
		builder.create().show();
	}
	/*HQ_zhangjing on 2015-09-11 add vcard contacts dialog end*/
    private void doMoreAction(Message msg) {
        Bundle bundle = msg.getData();
        int action = bundle.getInt(SharePanel.SHARE_ACTION);
        ///M: If recipient is not a user-IPMessage,Send IP multi-media message via MMS/SMS {@
        /// M: Fix ipmessage bug for ALPS01674002
        if ((mWorkingMessage.requiresMms()
                || mCurrentChatMode == IpMessageConsts.ChatMode.XMS || !isCurrentIpmessageSendable())
                && action != SharePanel.IPMSG_SHARE_FILE) {
            doMoreActionForMms(msg);
            return;
        }
        ///@}
        boolean isNoRecipient = isRecipientsEditorVisible() && TextUtils.isEmpty(mRecipientsEditor.getText().toString());
        switch (action) {
        case SharePanel.IPMSG_TAKE_PHOTO:
                if (isNoRecipient) {
                toastNoRecipients(this);
                return;
            }
            takePhoto();
            break;

        case SharePanel.IPMSG_RECORD_VIDEO:
                if (isNoRecipient) {
                toastNoRecipients(this);
                return;
            }
            recordVideo();
            break;

        case SharePanel.IPMSG_SHARE_CONTACT:
            if (isNoRecipient) {
                toastNoRecipients(this);
                return;
            }
            shareContact();
            break;

        case SharePanel.IPMSG_CHOOSE_PHOTO:
                if (isNoRecipient) {
                toastNoRecipients(this);
                return;
            }
            choosePhoto();
            break;

        case SharePanel.IPMSG_CHOOSE_VIDEO:
                if (isNoRecipient) {
                toastNoRecipients(this);
                return;
            }
            chooseVideo();
            break;

        case SharePanel.IPMSG_RECORD_AUDIO:
                if (isNoRecipient) {
                toastNoRecipients(this);
                return;
            }
            recordAudio();
            break;

        case SharePanel.IPMSG_CHOOSE_AUDIO:
                if (isNoRecipient) {
                toastNoRecipients(this);
                return;
            }
            chooseAudio();
            break;

        case SharePanel.IPMSG_SHARE_CALENDAR:
            if (isNoRecipient) {
                toastNoRecipients(this);
                return;
            }
            shareCalendar();
            break;

        case SharePanel.IPMSG_SHARE_SLIDESHOW:
            showSharePanel(false);
            mIsEditingSlideshow = true;
            addAttachment(AttachmentTypeSelectorAdapter.ADD_SLIDESHOW, !mWorkingMessage.hasAttachedFiles());
            break;
        case SharePanel.IPMSG_SHARE_FILE:
                MmsLog.v(IPMSG_TAG, "doMoreAction(): select from file manager: " + action);
                if (isNoRecipient) {
                    toastNoRecipients(this);
                    return;
                }
            startFileManager();
            break;

        default:
            MmsLog.e(IPMSG_TAG, "doMoreAction(): invalid share action type: " + action);
            break;
        }
        hideSharePanel();
    }

    private void doMoreActionForMms(Message msg) {
        int commonAttachmentType = 0;
        Bundle bundle = msg.getData();
        int action = bundle.getInt(SharePanel.SHARE_ACTION);
        switch (action) {
        case SharePanel.IPMSG_TAKE_PHOTO:
            commonAttachmentType = AttachmentTypeSelectorAdapter.TAKE_PICTURE;
            break;

        case SharePanel.IPMSG_RECORD_VIDEO:
            commonAttachmentType = AttachmentTypeSelectorAdapter.RECORD_VIDEO;
            break;

        case SharePanel.IPMSG_SHARE_CONTACT:
            commonAttachmentType = AttachmentTypeSelectorAdapter.ADD_VCARD;
            break;

        case SharePanel.IPMSG_CHOOSE_PHOTO:
            commonAttachmentType = AttachmentTypeSelectorAdapter.ADD_IMAGE;
            break;

        case SharePanel.IPMSG_CHOOSE_VIDEO:
            commonAttachmentType = AttachmentTypeSelectorAdapter.ADD_VIDEO;
            break;

        case SharePanel.IPMSG_RECORD_AUDIO:
            commonAttachmentType = AttachmentTypeSelectorAdapter.RECORD_SOUND;
            break;

        case SharePanel.IPMSG_CHOOSE_AUDIO:
            commonAttachmentType = AttachmentTypeSelectorAdapter.ADD_SOUND;
            break;

        case SharePanel.IPMSG_SHARE_CALENDAR:
            commonAttachmentType = AttachmentTypeSelectorAdapter.ADD_VCALENDAR;
            break;

        case SharePanel.IPMSG_SHARE_SLIDESHOW:
            commonAttachmentType = AttachmentTypeSelectorAdapter.ADD_SLIDESHOW;
            break;

        default:
            MmsLog.e(IPMSG_TAG, "doMoreActionForMms(): invalid share action type: " + action);
                hideSharePanel();
            return;
        }
        addAttachment(commonAttachmentType, !mWorkingMessage.hasAttachedFiles());
        hideSharePanel();
    }

    public void takePhoto() {
        Intent imageCaptureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        String fileName = System.currentTimeMillis() + ".jpg";
        mPhotoFilePath = MmsConfig.getPicTempPath(this) + File.separator + fileName;
        mDstPath = mPhotoFilePath;
        File out = new File(mPhotoFilePath);
        Uri uri = Uri.fromFile(out);
        imageCaptureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        imageCaptureIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
        try {
            startActivityForResult(imageCaptureIntent, REQUEST_CODE_IPMSG_TAKE_PHOTO);
        } catch (Exception e) {
            Toast.makeText(this,
                            IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                        .getSingleString(IpMessageConsts.string.ipmsg_no_app),
                            Toast.LENGTH_SHORT).show();
            MmsLog.e(IPMSG_TAG, "takePhoto()", e);
        }
    }

    public void choosePhoto() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_PICK);
        intent.setType("image/*");
        String fileName = System.currentTimeMillis() + ".jpg";
        mPhotoFilePath = MmsConfig.getPicTempPath(this) + File.separator + fileName;
        mDstPath = mPhotoFilePath;
        try {
            startActivityForResult(intent, REQUEST_CODE_IPMSG_CHOOSE_PHOTO);
        } catch (Exception e) {
            Toast.makeText(this,
                            IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                        .getSingleString(IpMessageConsts.string.ipmsg_no_app),
                            Toast.LENGTH_SHORT).show();
            MmsLog.e(IPMSG_TAG, "choosePhoto()", e);
        }
    }

    public void recordVideo() {
        int durationLimit = MessageUtils.getVideoCaptureDurationLimit();
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
        intent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, (long) 300 * 1024);
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, durationLimit);
        try {
            startActivityForResult(intent, REQUEST_CODE_IPMSG_RECORD_VIDEO);
        } catch (Exception e) {
            Toast.makeText(this,
                            IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                        .getSingleString(IpMessageConsts.string.ipmsg_no_app),
                            Toast.LENGTH_SHORT).show();
            MmsLog.e(IPMSG_TAG, "recordVideo()", e);
        }
    }

    public void chooseVideo() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("video/*");
        try {
            startActivityForResult(intent, REQUEST_CODE_IPMSG_CHOOSE_VIDEO);
        } catch (Exception e) {
            Toast.makeText(this,
                            IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                        .getSingleString(IpMessageConsts.string.ipmsg_no_app),
                            Toast.LENGTH_SHORT).show();
            MmsLog.e(IPMSG_TAG, "chooseVideo()", e);
        }
    }

    public void recordAudio() {
        Intent intent = new Intent(RemoteActivities.AUDIO);
        intent.putExtra(RemoteActivities.KEY_REQUEST_CODE, REQUEST_CODE_IPMSG_RECORD_AUDIO);
        intent.putExtra(IpMessageConsts.RemoteActivities.KEY_SIZE, 300L * 1024);
        IpMessageUtils.startRemoteActivityForResult(this, intent);
    }

    public void shareContact() {
        Intent intent = new Intent("android.intent.action.contacts.list.PICKMULTICONTACTS");
        intent.setType(Contacts.CONTENT_TYPE);
        startActivityForResult(intent, REQUEST_CODE_IPMSG_SHARE_CONTACT);
    }

    public void chooseAudio() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setTitle(getString(R.string.add_music));
        String[] items = new String[2];
        items[0] = getString(R.string.attach_ringtone);
        items[1] = getString(R.string.attach_sound);
        alertBuilder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        MessageUtils.selectRingtone(ComposeMessageActivity.this, REQUEST_CODE_IPMSG_CHOOSE_AUDIO);
                        break;
                    case 1:
                        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                            Toast.makeText(getApplicationContext(),
                                getString(R.string.Insert_sdcard), Toast.LENGTH_LONG).show();
                            return;
                        }
                        MessageUtils.selectAudio(ComposeMessageActivity.this, REQUEST_CODE_IPMSG_CHOOSE_AUDIO);
                        break;
                   default:
                        break;
                }
            }
        });
        alertBuilder.create().show();
    }

    public void shareCalendar() {
        Intent intent = new Intent("android.intent.action.CALENDARCHOICE");
        intent.setType("text/x-vcalendar");
        intent.putExtra("request_type", 0);
        try {
            startActivityForResult(intent, REQUEST_CODE_IPMSG_SHARE_VCALENDAR);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this,
                            IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                        .getSingleString(IpMessageConsts.string.ipmsg_no_app),
                            Toast.LENGTH_SHORT).show();
            MmsLog.e(IPMSG_TAG, "shareCalendar()", e);
        }
    }

    private long genThreadIdFromContacts(Intent data) {
        final long[] contactsId = data.getLongArrayExtra("com.mediatek.contacts.list.pickdataresult");
        if (contactsId == null || contactsId.length <= 0) {
            Log.d(TAG, "[genThreadIdFromContacts] get recipients from contacts is null");
            return 0;
        }
        final ContactList aList = new ContactList();
        final ContactList list = new ContactList();
        ContactList selected = ContactList.blockingGetByIds(contactsId);
        List<String> selectedNumbers = Arrays.asList(selected.getProtosomaitcNumbers());
        final List<String> numbers = Arrays.asList(mConversation.getRecipients().getProtosomaitcNumbers());
        String selectedNumberAfterFormat = "";
        if (numbers.size() > 0) {
            for (String number : numbers) {
                if (!number.trim().equals("")) {
                    Contact c = Contact.get(number, false);
                    aList.add(c);
                }
            }
            /// M: format existing numbers(remove "-" and " ")
            List<String> formatedNumbers = Arrays.asList(aList.getNumbers(true));
            for (String selectedNumber : selectedNumbers) {
                selectedNumberAfterFormat = MessageUtils.parseMmsAddress(selectedNumber);
                if (selectedNumberAfterFormat != null && !selectedNumberAfterFormat.trim().equals("") && !formatedNumbers.contains(selectedNumberAfterFormat)) {
                    Contact c = Contact.get(selectedNumber, false);
                    list.add(c);
                }
            }
            aList.addAll(list);
        }
        if (aList == null || aList.size() <= 0) {
            return 0;
        }
        long id = Conversation.getOrCreateThreadId(getApplicationContext(), aList);
        return id;
    }

    private void onIpMsgActivityResult(Context context, int requestCode, int resultCode, Intent data) {
        MmsLog.d(IPMSG_TAG, "onIpMsgActivityResult(): requestCode = " + requestCode + ", resultCode = " + resultCode
                + ", data = " + data);
        if (resultCode != RESULT_OK) {
            MmsLog.d(IPMSG_TAG, "bail due to resultCode=" + resultCode);
            return;
        }

        /// M: add for ip message
        switch (requestCode) {
            case REQUEST_CODE_INVITE_FRIENDS_TO_CHAT:
                List<String> allList = new ArrayList<String>();
                ContactList contactList = mConversation.getRecipients();
                if (contactList != null && contactList.size() > 0) {
                    for (Iterator it = contactList.iterator(); it.hasNext(); ) {
                        Contact c = (Contact) it.next();
                        allList.add(IpMessageUtils.getContactManager(this).getContactIdByNumber(c.getNumber()) + "");
                    }
                }

                String[] mSelectContactsIds = data.getStringArrayExtra(IpMessageUtils.SELECTION_CONTACT_RESULT);
                if (mSelectContactsIds != null && mSelectContactsIds.length > 0) {
                    String idStr = "";
                    for (int index = 0; index < mSelectContactsIds.length; index++) {
                        idStr = mSelectContactsIds[index];
                        if (!allList.contains(idStr)) {
                            allList.add(idStr);
                        }
                    }
                }

                if (allList != null && allList.size() > 0) {
                    Intent intent = new Intent(RemoteActivities.NEW_GROUP_CHAT);
                    intent.putExtra(RemoteActivities.KEY_SIM_ID, data.getIntExtra(KEY_SELECTION_SUBID, 0));
                    intent.putExtra(RemoteActivities.KEY_ARRAY, allList.toArray(new String[allList
                            .size()]));
                    IpMessageUtils.startRemoteActivity(this, intent);
                    allList = null;
                } else {
                    MmsLog.d(IPMSG_TAG, "onActivityResult(): SELECT_CONTACT get contact id is NULL!");
                }
                return;
            case REQUEST_CODE_IP_MSG_PICK_CONTACTS:
                Activity composer = sCompose == null ? null : sCompose.get();
                if (composer != null && !composer.isFinishing()) {
                    composer.finish();
                }
                long threadId = genThreadIdFromContacts(data);
                if (threadId <= 0) {
                    Log.d(TAG, "[onIpMsgActivityResult] return thread id <= 0");
                    break;
                }
                Intent it = ComposeMessageActivity.createIntent(getApplicationContext(), threadId);
                startActivity(it);
                break;
        case REQUEST_CODE_IPMSG_TAKE_PHOTO:
            if (!IpMessageUtils.isValidAttach(mDstPath, false)) {
                Toast.makeText(this,
                                IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                            .getSingleString(IpMessageConsts.string.ipmsg_err_file),
                                Toast.LENGTH_SHORT).show();
                return;
            }
            if (!IpMessageUtils.isPic(mDstPath)) {
                Toast.makeText(this,
                                IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                            .getSingleString(IpMessageConsts.string.ipmsg_invalid_file_type),
                                Toast.LENGTH_SHORT).show();
                return;
            }
            if (IpMessageUtils.getSettingsManager(this).isPicNeedResize()) {
                new Thread(mResizePic, "ipmessage_resize_pic").start();
            } else {
                sendImage(requestCode);
            }
            return;

        case REQUEST_CODE_IPMSG_RECORD_VIDEO:
            if (!getVideoOrPhoto(data, requestCode)) {
                Toast.makeText(this,
                                IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                            .getSingleString(IpMessageConsts.string.ipmsg_err_file),
                                Toast.LENGTH_SHORT).show();
                return;
            }
            if (!IpMessageUtils.isVideo(mDstPath)) {
                Toast.makeText(this,
                                IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                            .getSingleString(IpMessageConsts.string.ipmsg_invalid_file_type),
                                Toast.LENGTH_SHORT).show();
                return;
            }
            if (!IpMessageUtils.isFileStatusOk(this, mDstPath)) {
                MmsLog.e(TAG, "onIpMsgActivityResult(): record video failed, invalid file");
                return;
            }
            mIpMsgHandler.postDelayed(mSendVideo, 100);
            return;

        case REQUEST_CODE_IPMSG_SHARE_CONTACT:
            asyncAttachVCardByContactsId(data, true);
            return;

        case REQUEST_CODE_IPMSG_CHOOSE_PHOTO:
            if (!getVideoOrPhoto(data, requestCode)) {
                Toast.makeText(this,
                                IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                            .getSingleString(IpMessageConsts.string.ipmsg_err_file),
                                Toast.LENGTH_SHORT).show();
                return;
            }
            if (!IpMessageUtils.isPic(mDstPath)) {
                Toast.makeText(this,
                                IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                            .getSingleString(IpMessageConsts.string.ipmsg_invalid_file_type),
                                Toast.LENGTH_SHORT).show();
                return;
            }
            if (IpMessageUtils.getSettingsManager(this).isPicNeedResize()) {
                new Thread(mResizePic).start();
            } else {
                sendImage(requestCode);
            }
            return;

        case REQUEST_CODE_IPMSG_CHOOSE_VIDEO:
        	getVideoOrPhoto(data, requestCode);
            if (!IpMessageUtils.isVideo(mDstPath)) {
                Toast.makeText(this,
                                IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                            .getSingleString(IpMessageConsts.string.ipmsg_invalid_file_type),
                                Toast.LENGTH_SHORT).show();
                return;
            }
            if (!IpMessageUtils.isFileStatusOk(this, mDstPath)) {
                MmsLog.e(TAG, "onIpMsgActivityResult(): choose video failed, invalid file");
                return;
            }
            int mmsSizeLimit = 300 * 1024;
            if (IpMessageUtils.getFileSize(mDstPath) > mmsSizeLimit) {
                Toast.makeText(this,
                                IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                            .getSingleString(IpMessageConsts.string.ipmsg_file_limit),
                                Toast.LENGTH_SHORT).show();
                return;
            }
            mIpMsgHandler.postDelayed(mSendVideo, 100);
            return;

        case REQUEST_CODE_IPMSG_RECORD_AUDIO:
            if (!getVideoOrPhoto(data, requestCode)) {
                return;
            }
            if (!IpMessageUtils.isAudio(mDstPath)) {
                Toast.makeText(this,
                                IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                            .getSingleString(IpMessageConsts.string.ipmsg_invalid_file_type),
                                Toast.LENGTH_SHORT).show();
                return;
            }
            if (!IpMessageUtils.isFileStatusOk(this, mDstPath)) {
                MmsLog.e(TAG, "onIpMsgActivityResult(): record audio failed, invalid file");
                return;
            }
            mIpMsgHandler.postDelayed(mSendAudio, 100);
            return;
        case REQUEST_CODE_IPMSG_CHOOSE_AUDIO:
            if (data != null) {
                Uri uri = (Uri) data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                if (Settings.System.getUriFor(Settings.System.RINGTONE).equals(uri)) {
                    return;
                }
                if (getAudio(data)) {
                    mIpMsgHandler.postDelayed(mSendAudio, 100);
                }
            }
            return;
        case REQUEST_CODE_IPMSG_SHARE_VCALENDAR:
            String calendar = data.getDataString();
            if (TextUtils.isEmpty(calendar)) {
                return;
            }
            getCalendar(this, calendar);
            mIpMsgHandler.postDelayed(mSendCalendar, 100);
            return;
        case REQUEST_CODE_IPMSG_PICK_CONTACT:
            if (null != data) {
                processPickResult(data);
            } else {
              mIsRecipientHasIntentNotHandle = true;
              mIntent = data;
            }
            misPickContatct = false;
            return;
        case REQUEST_CODE_IPMSG_SHARE_FILE:
            if (data != null) {
                sendFileViaJoyn(data);
            } else {
                MmsLog.d(TAG, "doInBackground() mData is null,that is onActivityResult() get a null intent");
            }
            return;
        default:
            break;
        }
    }

    private Runnable mResizePic = new Runnable() {
        @Override
        public void run() {
            MmsLog.d(IPMSG_TAG, "mResizePic(): start resize pic.");
            byte[] img = IpMessageUtils.resizeImg(mPhotoFilePath, (float) 500);
            if (null == img) {
                return;
            }
            MmsLog.d(IPMSG_TAG, "mResizePic(): put stream to file.");
            try {
                IpMessageUtils.nmsStream2File(img, mDstPath);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
            MmsLog.d(IPMSG_TAG, "mResizePic(): post send pic.");
            mIpMsgHandler.postDelayed(mSendPic, 100);
        }
    };

    private boolean sendMessageForIpMsg(final IpMessage ipMessage, final String log,
                                    boolean isSendSecondTextMessage, final boolean isDelDraft) {
        MmsLog.d(IPMSG_TAG, "sendMessageForIpMsg(): start.");
        if (mWorkingMessage.requiresMms() && mIpMessageForSend == null) {
            mIpMessageDraft = ipMessage;
            convertIpMessageToMmsOrSms(true);
            updateSendButtonState();
            return false;
        }
        int subId = 0;
        if (mSubInfoList.size() > 1) {
            MmsLog.d(IPMSG_TAG, "sendMessageForIpMsg(): More than two Sub card!");
            if (mSelectedSubId > 0) {
                MmsLog.d(IPMSG_TAG, "sendMessageForIpMsg(): has selected Sub, subId = " + mSelectedSubId);
                subId = mSelectedSubId;
            } else {
                MmsLog.d(IPMSG_TAG, "sendMessageForIpMsg(): no selected Sub, check recipients count, " +
                        "and show Sub selection dialog if need.");
                if (mIpMessageForSend == null) {
                    mIpMessageDraft = ipMessage;
                }
                checkRecipientsCount();
                return false;
            }
        } else if (mSubInfoList.size() == 1) {
            subId = mSubInfoList.get(0).getSubscriptionId();
        } else {
            MmsLog.d(IPMSG_TAG, "sendMessageForIpMsg(): No Sub card! Convert to MMS!");
            if (mIpMessageForSend == null) {
                mIpMessageDraft = ipMessage;
                convertIpMessageToMmsOrSms(true);
                updateSendButtonState();
                return false;
            } else {
                toastNoSubCard(this);
                return false;
            }
        }
        String to = getCurrentNumberString();
        MmsLog.d(IPMSG_TAG, "sendMessageForIpMsg(): subId = " + subId + ", to = " + to);
        ipMessage.setSimId((int) subId);
        ipMessage.setTo(to);
        if (TextUtils.isEmpty(ipMessage.getTo()) && mIpMessageForSend == null) {
            saveIpMessageForAWhile(ipMessage);
            updateSendButtonState();
            mSendButtonCanResponse = true;
            return false;
        }

        if (!MmsConfig.isServiceEnabled(this)) {
            if (mIpMessageForSend == null) {
                mIpMessageDraft = ipMessage;
            }
            if (ipMessage.getType() == IpMessageType.TEXT) {
                showIpMessageConvertToMmsOrSmsDialog(SMS_CONVERT, SERVICE_IS_NOT_ENABLED);
            } else {
                showIpMessageConvertToMmsOrSmsDialog(MMS_CONVERT, SERVICE_IS_NOT_ENABLED);
            }
            return false;
        }

        /// M: Fix ipmessage bug for ALPS01674002 @{
        boolean isCurrentIpUser = isCurrentRecipientIpMessageUser();
        if (!isCurrentIpUser || !isCurrentIpmessageSendable()) {
            if (mIpMessageForSend == null) {
                mIpMessageDraft = ipMessage;
            }
            if (ipMessage.getType() == IpMessageType.TEXT) {
                showIpMessageConvertToMmsOrSmsDialog(SMS_CONVERT,
                        !isCurrentIpUser ? RECIPIENTS_ARE_NOT_IP_MESSAGE_USER
                                : RECIPIENTS_IP_MESSAGE_NOT_SENDABLE);
            } else {
                showIpMessageConvertToMmsOrSmsDialog(MMS_CONVERT,
                        !isCurrentIpUser ? RECIPIENTS_ARE_NOT_IP_MESSAGE_USER
                                : RECIPIENTS_IP_MESSAGE_NOT_SENDABLE);
            }
            /// @}
            return false;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                MmsLog.d(IPMSG_TAG, "sendMessageForIpMsg(): calling API: saveIpMsg().");
                int ret = -1;
                ipMessage.setStatus(IpMessageStatus.OUTBOX);

                ret = IpMessageUtils.getMessageManager(ComposeMessageActivity.this)
                        .saveIpMsg(ipMessage, IpMessageSendMode.AUTO);

                if (ret < 0) {
                    MmsLog.w(IPMSG_TAG, "sendMessageForIpMsg(): " + log);
                } else {
                    if (ipMessage.getType() == IpMessageType.TEXT) {
                        mWorkingMessage
                                .asyncDeleteDraftSmsMessage(mConversation);
                    }
                }
                mScrollOnSend = true;   // in the next onQueryComplete, scroll the list to the end.
            }
        }).start();

        if (isSendSecondTextMessage && ipMessage.getType() != IpMessageType.TEXT
                && TextUtils.isEmpty(IpMessageUtils.getIpMessageCaption(ipMessage))
                && mTextEditor != null && mTextEditor.getVisibility() == View.VISIBLE
                && !TextUtils.isEmpty(mTextEditor.getText().toString())) {
            IpTextMessage ipTextMessage = new IpTextMessage();
            ipTextMessage.setBody(mTextEditor.getText().toString());
            ipTextMessage.setSimId((int) subId);
            ipTextMessage.setTo(to);
            ipTextMessage.setStatus(IpMessageStatus.OUTBOX);

            final IpMessage ipTextMessageForSend = ipTextMessage;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    MmsLog.d(IPMSG_TAG, "sendMessageForIpMsg(): send second text IP message, calling API: saveIpMsg().");
                    int ret = -1;
                    ret = IpMessageUtils.getMessageManager(ComposeMessageActivity.this)
                            .saveIpMsg(ipTextMessageForSend, IpMessageSendMode.AUTO);
                    if (ret < 0) {
                        MmsLog.w(IPMSG_TAG, "sendMessageForIpMsg(): send second text IP message failed!");
                    }
                    mScrollOnSend = true;   // in the next onQueryComplete, scroll the list to the end.
                }
            }).start();
        }
        mWorkingMessage.syncWorkingRecipients();
        mConversation.guaranteeThreadId();
        if (mIpMessageForSend == null) {
            onPreMessageSent();
            MmsLog.d(IPMSG_TAG, "sendMessageForIpMsg(): after guaranteeThreadId(), threadId = " +
                                mConversation.getThreadId());
        } else {
            resetMessage();
            mIpMessageForSend = null;
        }
        onMessageSent();
        return true;
    }

    private void sendIpTextMessage() {
        String body = mTextEditor.getText().toString();
        if (TextUtils.isEmpty(body)) {
            MmsLog.w(IPMSG_TAG, "sendIpTextMessage(): No content for sending!");
            return;
        }
        IpTextMessage msg = new IpTextMessage();
        msg.setBody(body);
        msg.setType(IpMessageType.TEXT);
        mIpMessageForSend = msg;
        mIsClearTextDraft = true;
        sendMessageForIpMsg(msg, "send text msg failed", false, false);
    }

    private Runnable mSendAudio = new Runnable() {
        public void run() {
            /// M: cracks, wait activity resume, ensure dialog context valid.
            if (!isFinishing() && !mIsRunning) {
                mIpMsgHandler.postDelayed(mSendAudio, 100);
                MmsLog.d(IPMSG_TAG, "mSendAudio, wait activity resume.");
                return;
            }

            if (IpMessageUtils.isExistsFile(mDstPath) && IpMessageUtils.getFileSize(mDstPath) != 0) {
                IpVoiceMessage msg = new IpVoiceMessage();
                msg.setPath(mDstPath);
                msg.setDuration(mDuration);
                msg.setType(IpMessageType.VOICE);
                mIpMessageForSend = msg;
                sendMessageForIpMsg(msg, "send Audio msg failed", false, false);
                mIpMsgHandler.removeCallbacks(mSendAudio);
            }
        }
    };

    private Runnable mSendPic = new Runnable() {
        public void run() {
            /// M: cracks, wait activity resume, ensure dialog context valid.
            if (!isFinishing() && !mIsRunning) {
                mIpMsgHandler.postDelayed(mSendPic, 100);
                MmsLog.d(IPMSG_TAG, "mSendPic, wait activity resume.");
                return;
            }

            MmsLog.d(IPMSG_TAG, "mSendPic(): start.");
            if (IpMessageUtils.isExistsFile(mDstPath) && IpMessageUtils.getFileSize(mDstPath) != 0) {
                MmsLog.d(IPMSG_TAG, "mSendPic(): start send image.");
                sendImage(REQUEST_CODE_IPMSG_TAKE_PHOTO);
                mIpMsgHandler.removeCallbacks(mSendPic);
//                refreshAndScrollList();
            }
            MmsLog.d(IPMSG_TAG, "mSendPic(): end.");
        }
    };

    private Runnable mSendVideo = new Runnable() {
        public void run() {
            /// M: cracks, wait activity resume, ensure dialog context valid.
            if (!isFinishing() && !mIsRunning) {
                mIpMsgHandler.postDelayed(mSendVideo, 100);
                MmsLog.d(IPMSG_TAG, "mSendVideo, wait activity resume.");
                return;
            }

            MmsLog.d(IPMSG_TAG, "mSendVideo(): start send video. Path = " + mDstPath);
            if (IpMessageUtils.isExistsFile(mDstPath) && IpMessageUtils.getFileSize(mDstPath) != 0) {
                IpVideoMessage msg = new IpVideoMessage();
                msg.setPath(mDstPath);
                msg.setDuration(mDuration);
                msg.setType(IpMessageType.VIDEO);
                mIpMessageForSend = msg;
                sendMessageForIpMsg(msg, "send Video msg failed", false, false);
                mIpMsgHandler.removeCallbacks(mSendVideo);
//                refreshAndScrollList();
            }
        }
    };

    private Runnable mSendVcard = new Runnable() {
        public void run() {
            /// M: cracks, wait activity resume, ensure dialog context valid.
            if (!isFinishing() && !mIsRunning) {
                mIpMsgHandler.postDelayed(mSendVcard, 100);
                MmsLog.d(IPMSG_TAG, "mSendVcard, wait activity resume.");
                return;
            }

            if (IpMessageUtils.isExistsFile(mDstPath) && IpMessageUtils.getFileSize(mDstPath) != 0) {
                IpVCardMessage msg = new IpVCardMessage();
                msg.setPath(mDstPath);
                msg.setName(mIpMessageVcardName);
                msg.setType(IpMessageType.VCARD);
                mIpMessageForSend = msg;
                sendMessageForIpMsg(msg, "send vcard msg failed", false, false);
                mIpMsgHandler.removeCallbacks(mSendVcard);
//                refreshAndScrollList();
            }
        }
    };

    private Runnable mSendCalendar = new Runnable() {
        public void run() {
            /// M: cracks, wait activity resume, ensure dialog context valid.
            if (!isFinishing() && !mIsRunning) {
                mIpMsgHandler.postDelayed(mSendCalendar, 100);
                MmsLog.d(IPMSG_TAG, "mSendCalendar, wait activity resume.");
                return;
            }

            if (IpMessageUtils.isExistsFile(mDstPath) && IpMessageUtils.getFileSize(mDstPath) != 0) {
                IpVCalendarMessage msg = new IpVCalendarMessage();
                msg.setPath(mDstPath);
                msg.setSummary(mCalendarSummary);
                msg.setType(IpMessageType.CALENDAR);
                mIpMessageForSend = msg;
                sendMessageForIpMsg(mIpMessageForSend, "send vCalendar msg failed", false, false);
                mIpMsgHandler.removeCallbacks(mSendCalendar);
            }
        }
    };

    public boolean getVideoOrPhoto(Intent data, int requestCode) {
        if (null == data) {
            MmsLog.e(IPMSG_TAG, "getVideoOrPhoto(): take video error, result intent is null.");
            return false;
        }

        Uri uri = data.getData();
        Cursor cursor = null;
        if (requestCode == REQUEST_CODE_IPMSG_TAKE_PHOTO || requestCode == REQUEST_CODE_IPMSG_CHOOSE_PHOTO) {
            final String[] selectColumn = { "_data" };
            cursor = mContentResolver.query(uri, selectColumn, null, null, null);
        } else {
            final String[] selectColumn = { "_data", "duration" };
            cursor = mContentResolver.query(uri, selectColumn, null, null, null);
        }
        if (null == cursor) {
            if (requestCode == REQUEST_CODE_IPMSG_RECORD_AUDIO) {
                mDstPath = uri.getEncodedPath();
                mDuration = data.getIntExtra("audio_duration", 0);
                mDuration = mDuration / 1000 == 0 ? 1 : mDuration / 1000;
            } else {
                mPhotoFilePath = uri.getEncodedPath();
                mDstPath = mPhotoFilePath;
            }
            return true;
        }
        if (0 == cursor.getCount()) {
            cursor.close();
            MmsLog.e(IPMSG_TAG, "getVideoOrPhoto(): take video cursor getcount is 0");
            return false;
        }
        cursor.moveToFirst();
        if (requestCode == REQUEST_CODE_IPMSG_TAKE_PHOTO || requestCode == REQUEST_CODE_IPMSG_CHOOSE_PHOTO) {
            mPhotoFilePath = cursor.getString(cursor.getColumnIndex("_data"));
            mDstPath = mPhotoFilePath;
        } else {
            mDstPath = cursor.getString(cursor.getColumnIndex("_data"));
            mDuration = cursor.getInt(cursor.getColumnIndex("duration"));
            mDuration = mDuration / 1000 == 0 ? 1 : mDuration / 1000;
        }
        if (null != cursor && !cursor.isClosed()) {
            cursor.close();
        }
        return true;
    }

    private boolean getAudio(Intent data) {
        Uri uri = (Uri) data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        if (Settings.System.getUriFor(Settings.System.RINGTONE).equals(uri)) {
            return false;
        }
        if (null == uri) {
            uri = data.getData();
        }
        if (null == uri) {
            MmsLog.e(IPMSG_TAG, "getAudio(): choose audio failed, uri is null");
            return false;
        }
        final String scheme = uri.getScheme();
        if (scheme.equals("file")) {
            mDstPath = uri.getEncodedPath();
        } else {
            ContentResolver cr = getContentResolver();
            Cursor c = cr.query(uri, null, null, null, null);
            c.moveToFirst();
            mDstPath = c.getString(c.getColumnIndexOrThrow(Audio.Media.DATA));
            c.close();
        }

        if (!IpMessageUtils.isAudio(mDstPath)) {
            Toast.makeText(this,
                            IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                        .getSingleString(IpMessageConsts.string.ipmsg_invalid_file_type),
                            Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!IpMessageUtils.getServiceManager(this).isFeatureSupported(FeatureId.FILE_TRANSACTION)
                && !IpMessageUtils.isFileStatusOk(this, mDstPath)) {
            MmsLog.e(IPMSG_TAG, "getAudio(): choose audio failed, invalid file");
            return false;
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String dur = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (dur != null) {
                mDuration = Integer.parseInt(dur);
                mDuration = mDuration / 1000 == 0 ? 1 : mDuration / 1000;
            }
        } catch (Exception ex) {
            MmsLog.e(IPMSG_TAG, "getAudio(): MediaMetadataRetriever failed to get duration for " + uri.getPath(), ex);
            return false;
        } finally {
            retriever.release();
        }
        return true;
    }

    private void sendImage(int requestCode) {
        IpImageMessage msg = new IpImageMessage();
        msg.setType(IpMessageType.PICTURE);
        msg.setPath(mDstPath);
        mIpMessageForSend = msg;
        MmsLog.d(IPMSG_TAG, "sendImage(): start send message.");
        sendMessageForIpMsg(msg, "sendImage(): send pic msg failed", false, false);
    }

    private void sendUnknownMsg(String path) {
        IpAttachMessage msg = new IpAttachMessage();
        msg.setPath(path);
        msg.setType(IpMessageType.UNKNOWN_FILE);
        mIpMessageForSend = msg;
        sendMessageForIpMsg(mIpMessageForSend, "sendUnknownMsg(): send unknown msg failed", false, false);
    }

    public void getCalendar(Context context, String calendar) {
        Uri calendarUri = Uri.parse(calendar);
        InputStream is = null;
        OutputStream os = null;
        Cursor cursor = getContentResolver().query(calendarUri, null, null, null, null);
        if (null != cursor) {
            if (0 == cursor.getCount()) {
                MmsLog.e(IPMSG_TAG, "getCalendar(): take calendar cursor getcount is 0");
            } else {
                cursor.moveToFirst();
                mCalendarSummary = cursor.getString(0);
                if (mCalendarSummary != null) {
                    int sub = mCalendarSummary.lastIndexOf(".");
                    mCalendarSummary = mCalendarSummary.substring(0, sub);
                }
            }
            cursor.close();

            String fileName = System.currentTimeMillis() + ".vcs";
            mDstPath = MmsConfig.getVcalendarTempPath(this) + File.separator + fileName;

            File file = new File(mDstPath);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            file.delete();
            try {
                if (!file.createNewFile()) {
                    return;
                }
            } catch (IOException e) {
                MmsLog.e(IPMSG_TAG, "getCalendar()", e);
                return;
            }
            try {
                is = context.getContentResolver().openInputStream(calendarUri);
                os = new BufferedOutputStream(new FileOutputStream(file));
            } catch (FileNotFoundException e) {
                MmsLog.e(IPMSG_TAG, "getCalendar()", e);
            }
            byte[] buffer = new byte[256];
            try {
                if (is != null && os != null) {
                    for (int len = 0; (len = is.read(buffer)) != -1;) {
                        os.write(buffer, 0, len);
                    }
                    is.close();
                    os.close();
                }
            } catch (IOException e) {
                MmsLog.e(IPMSG_TAG, "getCalendar()", e);
            }
        }
    }

    private String mCurrentNumberString = "";
    private String getCurrentNumberString() {
        if (isRecipientsEditorVisible()) {
            return TextUtils.join(",", mRecipientsEditor.getNumbers());
        }
        if (TextUtils.isEmpty(mCurrentNumberString)) {
            mCurrentNumberString = TextUtils.join(",", mConversation.getRecipients().getNumbers());
        }
        return mCurrentNumberString;
    }

    private String getNameViaContactId(long contactId) {
        if (contactId <= 0) {
            MmsLog.w(IPMSG_TAG, "getNameViaContactId(): contactId is invalid!");
            return null;
        }

        String displayName = "";

        Cursor cursor = mContentResolver.query(Contacts.CONTENT_URI, new String[] { Contacts.DISPLAY_NAME },
                Contacts._ID + "=?", new String[] { String.valueOf(contactId) }, null);
        if (cursor != null && cursor.moveToFirst()) {
            displayName = cursor.getString(cursor.getColumnIndex(Contacts.DISPLAY_NAME));
        }
        if (cursor != null) {
            cursor.close();
        }

        return displayName == null ? "" : displayName;
    }

    private boolean mIsStartMultiDeleteActivity = false;

    private boolean mSendSmsInJoynMode = false;

    private boolean mSendJoynMsgInSmsMode = false;

    private void onIpMsgOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_INVITE_FRIENDS_TO_CHAT:
                if (mCurrentChatMode == IpMessageConsts.ChatMode.XMS) {
                if (IpMessageUtils.getServiceManager(this).isFeatureSupported(IpMessageConsts.FeatureId.EXTEND_GROUP_CHAT)) {
                    int curCount = mConversation.getRecipients().size();
                    int pickCount = RECIPIENTS_LIMIT_FOR_SMS - curCount;
                    addContacts(pickCount, REQUEST_CODE_IP_MSG_PICK_CONTACTS);
                    break;
                }
            }
            Intent intent = new Intent(RemoteActivities.CONTACT);
            intent.putExtra(RemoteActivities.KEY_REQUEST_CODE, REQUEST_CODE_INVITE_FRIENDS_TO_CHAT);
            intent.putExtra(RemoteActivities.KEY_TYPE, SelectContactType.IP_MESSAGE_USER);
            intent.putExtra(RemoteActivities.KEY_ARRAY, mConversation.getRecipients().getNumbers());
            IpMessageUtils.startRemoteActivityForResult(this, intent);
             break;
        case MENU_INVITE_FRIENDS_TO_IPMSG:
            String contentText = mTextEditor.getText().toString();
            if (TextUtils.isEmpty(contentText)) {
                mTextEditor.setText(IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                                .getSingleString(IpMessageConsts.string.ipmsg_invite_friends_content));
            } else {
                IpMessageUtils.showInviteIpMsgConfirmDialog(this, new InviteFriendsToIpMsgListener());
            }
            break;
        case MENU_SEND_BY_SMS_IN_JOYN_MODE:
            mSendSmsInJoynMode = true;
            onClick(mSendButtonSms);
            break;
        case MENU_SEND_BY_JOYN_IN_SMS_MODE:
            mSendJoynMsgInSmsMode = true;
            onClick(mSendButtonIpMessage);
            break;
        case MENU_SELECT_MESSAGE:
            Intent intentSelectMessage = new Intent(this, MultiDeleteActivity.class);
            intentSelectMessage.putExtra("thread_id", mConversation.getThreadId());
            startActivityForResult(intentSelectMessage, REQUEST_CODE_FOR_MULTIDELETE);
            mIsStartMultiDeleteActivity = true;
            break;
        case MENU_MARK_AS_SPAM:
            final Conversation conversationForMarkSpam = mConversation;
            final boolean isSpamFromMark = mConversation.isSpam();
            mConversation.setSpam(true);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String numbers = TextUtils.join(",", conversationForMarkSpam.getRecipients().getNumbers());
                    int contactId = IpMessageUtils.getContactManager(ComposeMessageActivity.this)
                                                    .getContactIdByNumber(numbers);
                    int[] contactIds = {contactId};
                    if (!IpMessageUtils.getContactManager(ComposeMessageActivity.this)
                            .addContactToSpamList(contactIds)) {
                        MmsLog.w(IPMSG_TAG, "onIpMsgOptionsItemSelected(): Mark as spam failed!");
                        mConversation.setSpam(isSpamFromMark);
                    ///M: Update thread mute icon {@
                    } else {
                        asyncUpdateThreadMuteIcon();
                    }
                    ///@}
                }
            }).start();
            break;
        case MENU_REMOVE_SPAM:
            final Conversation conversationForRemoveSpam = mConversation;
            final boolean isSpamFromRemove = mConversation.isSpam();
            mConversation.setSpam(false);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String numbers = TextUtils.join(",", conversationForRemoveSpam.getRecipients().getNumbers());
                    int contactId = IpMessageUtils.getContactManager(ComposeMessageActivity.this)
                        .getContactIdByNumber(numbers);
                    int[] contactIds = {contactId};
                    if (!IpMessageUtils.getContactManager(ComposeMessageActivity.this)
                            .deleteContactFromSpamList(contactIds)) {
                        MmsLog.w(IPMSG_TAG, "onIpMsgOptionsItemSelected(): Remove spam failed!");
                        mConversation.setSpam(isSpamFromRemove);
                    ///M: Update thread mute icon {@
                    } else {
                        asyncUpdateThreadMuteIcon();
                    }
                    ///@}
                }
            }).start();
            break;
        case MENU_VIEW_ALL_MEDIA:
            Intent intentViewAllMedia = new Intent(RemoteActivities.ALL_MEDIA);
            intentViewAllMedia.putExtra(RemoteActivities.KEY_THREAD_ID, mConversation.getThreadId());
            IpMessageUtils.startRemoteActivity(ComposeMessageActivity.this, intentViewAllMedia);
            break;
        case MENU_CHAT_SETTING:
            long chatThreadId = -1L;
            if (!isRecipientsEditorVisible()) {
                chatThreadId = mConversation.getThreadId();
            }
            if (chatThreadId != -1L) {
                Intent i = new Intent(ComposeMessageActivity.this, ChatPreferenceActivity.class);
                i.putExtra("chatThreadId", chatThreadId);
                startActivity(i);
            }
            break;
        /// M: Add Jump to another chat in converaged inbox mode. @{
        case MENU_JUMP_TO_JOYN:
            if (IpMessageUtils.getServiceManager(ComposeMessageActivity.this).getIntegrationMode() ==
                IpMessageConsts.IntegrationMode.FULLY_INTEGRATED) {
                    mIsIpServiceEnabled = true;
                mCurrentChatMode = IpMessageConsts.ChatMode.JOYN;
                showSmsOrMmsSendButton(false);
                invalidateOptionsMenu();
            } else if (IpMessageUtils.getServiceManager(ComposeMessageActivity.this).getIntegrationMode() ==
                IpMessageConsts.IntegrationMode.CONVERGED_INBOX) {
                jumpToJoynChat(true, null);
            }
            break;
        case MENU_JUMP_TO_XMS:
            if (IpMessageUtils.getServiceManager(ComposeMessageActivity.this).getIntegrationMode() ==
                IpMessageConsts.IntegrationMode.FULLY_INTEGRATED) {
                    mIsIpServiceEnabled = false;
                mCurrentChatMode = IpMessageConsts.ChatMode.XMS;
                showSmsOrMmsSendButton(false);
                invalidateOptionsMenu();
            } else if (IpMessageUtils.getServiceManager(ComposeMessageActivity.this).getIntegrationMode() ==
                IpMessageConsts.IntegrationMode.CONVERGED_INBOX) {
                jumpToJoynChat(false, null);
            }
            break;
        /// @}

        /// M: Add export chat fuature. @{
        case MENU_EXPORT_CHAT:
            IpMessageUtils.getChatManager(ComposeMessageActivity.this).exportChat(mConversation.getThreadId());
            break;
        /// @}
        default:
            break;
        }
    }

    private void updateTextEditorHint() {
        if (mIsIpServiceEnabled && isNetworkConnected(getApplicationContext())) {
            if (null != mIpMessageDraft
                    || (!mWorkingMessage.requiresMms() && isCurrentRecipientIpMessageUser() && mCurrentChatMode == IpMessageConsts.ChatMode.JOYN)) {
                /// M: Fix ipmessage bug ,fix bug ALPS 01585547@{
                if (mIsSmsEnabled) {
                    mTextEditor.setHint(IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                            .getSingleString(IpMessageConsts.string.ipmsg_hint));
                } else {
                    mTextEditor.setHint(R.string.sending_disabled_not_default_app);
                }
                /// @}
                mTextEditor.setFilters(new InputFilter[] {
                    new TextLengthFilter(MmsConfig.getMaxTextLimit())});
                updateCounter(mWorkingMessage.getText(), 0, 0, 0);
                return;
            }
        }
        if (mIsSmsEnabled) {
        mTextEditor.setHint(R.string.ipmsg_sms_hint);
        } else {
            mTextEditor.setHint(R.string.sending_disabled_not_default_app);
        }
        mTextEditor.setFilters(new InputFilter[] {
            new TextLengthFilter(MmsConfig.getMaxTextLimit())});
        updateCounter(mWorkingMessage.getText(), 0, 0, 0);
    }

    private void saveIpMessageForAWhile(IpMessage ipMessage) {
        MmsLog.d(IPMSG_TAG, "saveIpMessageForAWhile(): mIpMessageDraft = " + mIpMessageDraft
            + ", mIpMessageForSend = " + mIpMessageForSend);
        if (mIpMessageDraft != null && mIpMessageForSend != null) {
            showReplaceAttachDialog();
            return;
        }
        mIpMessageDraft = ipMessage;
        mIpMessageForSend = null;
        if (mWorkingMessage.requiresMms()) {
            convertIpMessageToMmsOrSms(true);
            updateSendButtonState();
            return;
        }
        switch (mIpMessageDraft.getType()) {
        case IpMessageType.TEXT:
            IpTextMessage textMessage = (IpTextMessage) mIpMessageDraft;
            mTextEditor.setText(textMessage.getBody());
            break;
        case IpMessageType.PICTURE:
            IpImageMessage imageMessage = (IpImageMessage) mIpMessageDraft;
            Bitmap imageBitmap = BitmapFactory.decodeFile(imageMessage.getPath());
            createThumbnailForIpMessage(imageBitmap, 0, null, null);
            break;
        case IpMessageType.VOICE:
            createThumbnailForIpMessage(null, R.drawable.ic_launcher_record_audio, null, null);
            break;
        case IpMessageType.VCARD:
            createThumbnailForIpMessage(null, R.drawable.ic_vcard_attach, null, null);
            break;
        case IpMessageType.VIDEO:
            IpVideoMessage videoMessage = (IpVideoMessage) mIpMessageDraft;
            Bitmap videoBitmap = ThumbnailUtils.createVideoThumbnail(videoMessage.getPath(), Thumbnails.MICRO_KIND);
            createThumbnailForIpMessage(videoBitmap, 0, null, null);
            break;
        case IpMessageType.CALENDAR:
            createThumbnailForIpMessage(null, R.drawable.ic_vcalendar_attach, null, null);
            break;
        case IpMessageType.UNKNOWN_FILE:
        case IpMessageType.COUNT:
            MmsLog.e(IPMSG_TAG, "saveIpMessageForAWhile(): Unknown IP message type. type = " + mIpMessageDraft.getType());
            break;
        case IpMessageType.GROUP_CREATE_CFG:
        case IpMessageType.GROUP_ADD_CFG:
        case IpMessageType.GROUP_QUIT_CFG:
            /// M: group chat type
            MmsLog.e(IPMSG_TAG, "saveIpMessageForAWhile(): Group IP message type. type = " + mIpMessageDraft.getType());
            break;
        default:
            MmsLog.e(IPMSG_TAG, "saveIpMessageForAWhile(): Error IP message type. type = " + mIpMessageDraft.getType());
            break;
        }
        mJustSendMsgViaCommonMsgThisTime = false;
        updateTextEditorHint();
    }

    private void createThumbnailForIpMessage(Bitmap bitmap, int resId, Drawable drawable, Uri uri) {
        mIpMessageThumbnail.setVisibility(View.VISIBLE);
        /// M: add for ipmessage for thumbnail delete
        mIpMessageThumbnailDelete.setVisibility(View.VISIBLE);
        if (bitmap != null) {
            mIpMessageThumbnail.setImageBitmap(bitmap);
        } else if (resId > 0) {
            mIpMessageThumbnail.setImageResource(resId);
        } else if (drawable != null) {
            mIpMessageThumbnail.setImageDrawable(drawable);
        } else if (uri != null) {
            mIpMessageThumbnail.setImageURI(uri);
        }
        return;
    }

    private String getOnlineDividerString(int currentRecipientStatus) {
        MmsLog.d(TAG_DIVIDER, "compose.getOnlineDividerString(): currentRecipientStatus = " + currentRecipientStatus);
        if (MmsConfig.isActivated(this) && mIsIpMessageRecipients) {
            switch (currentRecipientStatus) {
            case ContactStatus.OFFLINE:
                MmsLog.d(TAG_DIVIDER, "compose.getOnlineDividerString(): OFFLINE");
                
                String onlineTimeString = IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                    .getSingleString(IpMessageConsts.string.ipmsg_divider_never_online);
                MmsLog.d(TAG_DIVIDER, "compose.getOnlineDividerString(): OFFLINE");
                return onlineTimeString;
            case ContactStatus.ONLINE:
            case ContactStatus.TYPING:
            case ContactStatus.RECORDING:
                String name = IpMessageUtils.getContactManager(ComposeMessageActivity.this).
                getNameByNumber(mConversation.getRecipients().get(0).getNumber());
                return String.format(IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                        .getSingleString(IpMessageConsts.string.ipmsg_divider_online), name);
            case ContactStatus.STATUSCOUNT:
                MmsLog.d(TAG_DIVIDER, "compose.getOnlineDividerString(): STATUSCOUNT");
                break;
            default:
                MmsLog.w(TAG_DIVIDER, "compose.getOnlineDividerString(): unknown user status!");
                break;
            }
        }
        return "";
    }

    private String formatLastOnlineTime(int time) {
        return MessageUtils.formatTimeStampString(this, (long) (time * 1000L), true);
    }

    private void checkIpMessageBeforeSendMessage(boolean bCheckEcmMode) {
        if (mCurrentChatMode != IpMessageConsts.ChatMode.XMS) {
            mIsIpServiceEnabled = MmsConfig.isServiceEnabled(ComposeMessageActivity.this);
        }
        mIsIpMessageRecipients = isCurrentRecipientIpMessageUser();
        MmsLog.d(IPMSG_TAG, "checkIpMessageBeforeSendMessage(): isServiceEnable = "
                + mIsIpServiceEnabled +
                                ", mIsIpMessageRecipients = " + mIsIpMessageRecipients +
                                ", mIpMessageDraft is null ?= " + (null == mIpMessageDraft) +
                                ", mIpMessageForSend is null ?= " + (null == mIpMessageForSend));
        if (mIpMessageForSend != null) {
            sendMessageForIpMsg(mIpMessageForSend, "send fail from new ip message", false, true);
            return;
        }

        if (null != mIpMessageDraft) {
            // Has IP message draft
            if (!mIsIpServiceEnabled || !isNetworkConnected(getApplicationContext())) {
                /// M: disabled service
                if (mIpMessageDraft.getType() == IpMessageType.TEXT) {
                    showIpMessageConvertToMmsOrSmsDialog(SMS_CONVERT, SERVICE_IS_NOT_ENABLED);
                } else {
                    showIpMessageConvertToMmsOrSmsDialog(MMS_CONVERT, SERVICE_IS_NOT_ENABLED);
                }
                return;
            }

            /// M: Fix ipmessage bug for ALPS01674002 @{
            if (!mIsIpMessageRecipients || !isCurrentIpmessageSendable()) {
                /// M: non-IP message User
                if (mIpMessageDraft.getType() == IpMessageType.TEXT) {
                    showIpMessageConvertToMmsOrSmsDialog(
                            SMS_CONVERT,
                            !mIsIpMessageRecipients ? RECIPIENTS_ARE_NOT_IP_MESSAGE_USER
                                    : RECIPIENTS_IP_MESSAGE_NOT_SENDABLE);
                } else {
                    showIpMessageConvertToMmsOrSmsDialog(
                            MMS_CONVERT,
                            !mIsIpMessageRecipients ? RECIPIENTS_ARE_NOT_IP_MESSAGE_USER
                                    : RECIPIENTS_IP_MESSAGE_NOT_SENDABLE);
                }
                /// @}
                return;
            }

            if (sendMessageForIpMsg(mIpMessageDraft, "send fail from draft ip message", true, true)) {
                mIpMessageDraftId = 0;
                clearIpMessageDraft();
            }
            return;
        } else {
            // No IP message draft
            if (!mJustSendMsgViaCommonMsgThisTime && mIsIpServiceEnabled
                    && isNetworkConnected(getApplicationContext()) && mIsIpMessageRecipients
                    /// M: Fix ipmessage bug for ALPS01674002
                    && mCurrentChatMode == IpMessageConsts.ChatMode.JOYN && isCurrentIpmessageSendable()) {
                boolean isSmsCanConvertToIpmessage = !mWorkingMessage.requiresMms();
                boolean isMmsCanConvertToIpmessage = canMmsConvertToIpMessage();
                if (isSmsCanConvertToIpmessage) {
                    /// M: show convert to ipmessage dialog
//                    showMmsOrSmsConvertToIpMessageDialog(SMS_CONVERT, bCheckEcmMode);

                    if (!mSendSmsInJoynMode || mSendJoynMsgInSmsMode) {
                    /// M: send IP text message
                        sendIpTextMessage();
                        return;
                    }
                }
                if (isMmsCanConvertToIpmessage) {
                    showMmsOrSmsConvertToIpMessageDialog(MMS_CONVERT, bCheckEcmMode);
                    return;
                }
            }
            sendMessage(bCheckEcmMode);
            mSendSmsInJoynMode = false;
            mSendJoynMsgInSmsMode = false;
        }
    }

    private void showIpMessageConvertToMmsOrSmsDialog(int mode, int convertReason) {
        String message = "";
        if (convertReason == SERVICE_IS_NOT_ENABLED) {
            message = mode == SMS_CONVERT ?
                IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                            .getSingleString(IpMessageConsts.string.ipmsg_convert_to_sms_for_service)
                : IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                            .getSingleString(IpMessageConsts.string.ipmsg_convert_to_mms_for_service);
        } else if (convertReason == RECIPIENTS_ARE_NOT_IP_MESSAGE_USER) {
            message = mode == SMS_CONVERT ?
                IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                            .getSingleString(IpMessageConsts.string.ipmsg_convert_to_sms_for_recipients)
                : IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                .getSingleString(IpMessageConsts.string.ipmsg_convert_to_mms_for_recipients);
        }
        /// M: Fix ipmessage bug for ALPS01674002 @{
        else if (convertReason == RECIPIENTS_IP_MESSAGE_NOT_SENDABLE) {
            message = mode == SMS_CONVERT ? IpMessageUtils.getResourceManager(
                    ComposeMessageActivity.this).getSingleString(
                    IpMessageConsts.string.ipmsg_ip_msg_not_sendable_to_sms)
                    : IpMessageUtils
                            .getResourceManager(ComposeMessageActivity.this)
                            .getSingleString(
                                    IpMessageConsts.string.ipmsg_ip_msg_not_sendable_to_mms);
        }
        /// @}
        new AlertDialog.Builder(this)
            .setTitle(mode == SMS_CONVERT ?
                        IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                    .getSingleString(IpMessageConsts.string.ipmsg_convert_to_sms)
                        : IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                    .getSingleString(IpMessageConsts.string.ipmsg_convert_to_mms))
            .setMessage(message)
            .setPositiveButton(IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                            .getSingleString(IpMessageConsts.string.ipmsg_continue),
            new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (mIpMessageDraft != null) {
                        convertIpMessageToMmsOrSms(true);
                    }
                    if (mIpMessageForSend != null) {
                        if (mIpMessageForSend.getType() == IpMessageType.TEXT) {
                            MmsLog.e(IPMSG_TAG, "convertIpMessageToMmsOrSms(): convert IP message to SMS.");
                            if (mIpMessageForSend.getSimId() > 0) {
                                mSelectedSubId = mIpMessageForSend.getSimId();
                            }
                            mIpMessageForSend = null;
                            checkConditionsAndSendMessage(true);
                        } else {
                            mIpMessageDraft = mIpMessageForSend;
                            mIpMessageForSend = null;
                            convertIpMessageToMmsOrSms(true);
                        }
                    }
                    dialog.dismiss();
                }
            })
            .setNegativeButton(android.R.string.cancel,
                    new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    MmsLog.d(IPMSG_TAG, "showIpMessageConvertToMmsOrSmsDialog(): cancel.");
                    if (mIpMessageForSend != null) {
                        mIpMessageForSend = null;
                    } else {
                        saveIpMessageForAWhile(mIpMessageDraft);
                    }
                    mSelectedSubId = 0;
                    updateSendButtonState();
                    mSendButtonCanResponse = true;
                    dialog.dismiss();
                }
            })
            .setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        updateSendButtonState();
                    }
                    return false;
                }
            })
            .show();
    }

    /**
     * M: for checking which type IpMessage can be converted to.
     * @param ipMessage
     * @return
     */
    private int canConvertIpMessageToMessage(IpMessage ipMessage) {
        if (ipMessage == null) {
            return MESSAGETYPE_UNSUPPORT;
        }
        switch (ipMessage.getType()) {
            case IpMessageType.TEXT:
                return MESSAGETYPE_TEXT;
            case IpMessageType.PICTURE:
            case IpMessageType.VOICE:
            case IpMessageType.VCARD:
            case IpMessageType.VIDEO:
                return MESSAGETYPE_MMS;
            case IpMessageType.UNKNOWN_FILE:
            case IpMessageType.COUNT:
            case IpMessageType.GROUP_CREATE_CFG:
            case IpMessageType.GROUP_ADD_CFG:
            case IpMessageType.GROUP_QUIT_CFG:
                return MESSAGETYPE_UNSUPPORT;
            default:
                return MESSAGETYPE_UNSUPPORT;
        }
    }

    private boolean convertIpMessageToMmsOrSms(boolean isAppend) {
        MmsLog.d(IPMSG_TAG, "convertIpMessageToMmsOrSms(): IP message type = " + mIpMessageDraft.getType());

        switch (mIpMessageDraft.getType()) {
        case IpMessageType.TEXT:
            MmsLog.d(IPMSG_TAG, "convertIpMessageToMmsOrSms(): convert to SMS.");
            IpTextMessage textMessage = (IpTextMessage) mIpMessageDraft;
            mWorkingMessage.setText(textMessage.getBody());
            break;
        case IpMessageType.PICTURE:
            MmsLog.d(IPMSG_TAG, "convertIpMessageToMmsOrSms(): convert to MMS from image.");
            IpImageMessage imageMessage = (IpImageMessage) mIpMessageDraft;
            MmsLog.d(IPMSG_TAG, "convertIpMessageToMmsOrSms(): imagePath = " + imageMessage.getPath());
            if (!TextUtils.isEmpty(imageMessage.getCaption())) {
                mWorkingMessage.setText(imageMessage.getCaption());
                if (mTextEditor != null && mTextEditor.getVisibility() == View.VISIBLE) {
                    mTextEditor.setText(imageMessage.getCaption());
                }
            }
            File imageFile = new File(imageMessage.getPath());
            Uri imageUri = Uri.fromFile(imageFile);
//            addImageAsync(imageUri, MmsContentType.IMAGE_JPG, true);
            addImage(imageUri, isAppend);
            saveAsMms(true);
            break;
        case IpMessageType.VOICE:
            MmsLog.d(IPMSG_TAG, "convertIpMessageToMmsOrSms(): convert to MMS from voice.");
            IpVoiceMessage voiceMessage = (IpVoiceMessage) mIpMessageDraft;
            if (!TextUtils.isEmpty(voiceMessage.getCaption())) {
                mWorkingMessage.setText(voiceMessage.getCaption());
                if (mTextEditor != null && mTextEditor.getVisibility() == View.VISIBLE) {
                    mTextEditor.setText(voiceMessage.getCaption());
                }
            }
            File voiceFile = new File(voiceMessage.getPath());
            Uri voiceUri = Uri.fromFile(voiceFile);
            addAudio(voiceUri, isAppend);
            saveAsMms(true);
            break;
        case IpMessageType.VCARD:
            MmsLog.d(IPMSG_TAG, "convertIpMessageToMmsOrSms(): convert to MMS from vCard.");
            IpVCardMessage vCardMessage = (IpVCardMessage) mIpMessageDraft;
            File vCardFile = new File(vCardMessage.getPath());
            Uri vCardUri = Uri.fromFile(vCardFile);
            VCardAttachment va = new VCardAttachment(ComposeMessageActivity.this);
            String fileName = va.getVCardFileNameByUri(vCardUri);
            setFileAttachment(fileName, WorkingMessage.VCARD, false);
            saveAsMms(true);
            MessageUtils.deleteVCardTempFile(getApplicationContext(), fileName);
            break;
        case IpMessageType.VIDEO:
            MmsLog.d(IPMSG_TAG, "convertIpMessageToMmsOrSms(): convert to MMS from video.");
            IpVideoMessage videoMessage = (IpVideoMessage) mIpMessageDraft;
            if (!TextUtils.isEmpty(videoMessage.getCaption())) {
                mWorkingMessage.setText(videoMessage.getCaption());
                if (mTextEditor != null && mTextEditor.getVisibility() == View.VISIBLE) {
                    mTextEditor.setText(videoMessage.getCaption());
                }
            }
            File videoFile = new File(videoMessage.getPath());
            Uri videoUri = Uri.fromFile(videoFile);
            addVideo(videoUri, isAppend);
            saveAsMms(true);
            break;
        case IpMessageType.CALENDAR:
            MmsLog.d(IPMSG_TAG, "convertIpMessageToMmsOrSms(): convert to MMS from vCalendar.");
            IpVCalendarMessage vCalendarMessage = (IpVCalendarMessage) mIpMessageDraft;
            File vCalendarFile = new File(vCalendarMessage.getPath());
            Uri vCalendarUri = Uri.fromFile(vCalendarFile);
            attachVCalendar(vCalendarUri);
            saveAsMms(true);
            break;
        case IpMessageType.UNKNOWN_FILE:
        case IpMessageType.COUNT:
            MmsLog.w(IPMSG_TAG, "convertIpMessageToMmsOrSms(): Unknown IP message type. type = " +
                                mIpMessageDraft.getType());
            return false;
        case IpMessageType.GROUP_CREATE_CFG:
        case IpMessageType.GROUP_ADD_CFG:
        case IpMessageType.GROUP_QUIT_CFG:
            /// M: group chat type
            MmsLog.w(IPMSG_TAG, "convertIpMessageToMmsOrSms(): Group IP message type. type = " +
                                mIpMessageDraft.getType());
            return false;
        default:
            MmsLog.w(IPMSG_TAG, "convertIpMessageToMmsOrSms(): Error IP message type. type = " +
                                mIpMessageDraft.getType());
            return false;
        }
        if (mIpMessageDraft.getSimId() > 0) {
            mSelectedSubId = mIpMessageDraft.getSimId();
        }
        IpMessageUtils.deleteIpMessageDraft(this, mConversation, mWorkingMessage);
        mIpMessageDraftId = 0;
        clearIpMessageDraft();
//        checkConditionsAndSendMessage(true);
        return true;
    }

    private void showMmsOrSmsConvertToIpMessageDialog(int mode, final boolean bCheckEcmMode) {
        new AlertDialog.Builder(this)
            .setTitle(IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                    .getSingleString(IpMessageConsts.string.ipmsg_convert_to_ipmsg))
            .setMessage(mode == SMS_CONVERT ?
                IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                            .getSingleString(IpMessageConsts.string.ipmsg_sms_convert_to_ipmsg)
                : IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                            .getSingleString(IpMessageConsts.string.ipmsg_mms_convert_to_ipmsg))
            .setPositiveButton(IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                            .getSingleString(IpMessageConsts.string.ipmsg_switch),
                     new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    convertMmsOrSmsToIpMessage();
                    dialog.dismiss();
                }
            })
//                    .setNeutralButton(R.string.delete, new DeleteButtonListener())
            .setNegativeButton(mode == SMS_CONVERT ?
                            IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                        .getSingleString(IpMessageConsts.string.ipmsg_keep_sms)
                            : IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                            .getSingleString(IpMessageConsts.string.ipmsg_keep_mms),
                    new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    sendMessage(bCheckEcmMode);
                    dialog.dismiss();
                }
            })
            .setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        updateSendButtonState();
                    }
                    return false;
                }
            })
            .show();
    }

    private void convertMmsOrSmsToIpMessage() {
        MmsLog.w(IPMSG_TAG, "convertMmsOrSmsToIpMessage()");
        if (!mWorkingMessage.isWorthSaving()) {
            MmsLog.w(IPMSG_TAG, "convertMmsOrSmsToIpMessage(): No content for convert!");
            return;
        }
        if (mWorkingMessage.requiresMms()) {
            SlideshowModel slideshowModel = mWorkingMessage.getSlideshow();
            if (slideshowModel.size() == 1 &&
                    !(slideshowModel.get(0).hasAudio() && slideshowModel.get(0).hasImage()) &&
                    (slideshowModel.get(0).hasAudio() || slideshowModel.get(0).hasImage()
                            || slideshowModel.get(0).hasVideo())) {
                MmsLog.w(IPMSG_TAG, "convertMmsOrSmsToIpMessage(): Convert slide media!");
                if (mSubjectTextEditor != null && mSubjectTextEditor.getVisibility() == View.VISIBLE) {
                    showSubjectEditor(false);
                }
                convertSlideToIpMessage(slideshowModel.get(0));
                mWorkingMessage.removeAttachment(false);
                drawBottomPanel();
                updateSendButtonState();
            } else if (slideshowModel.sizeOfFilesAttach() == 1) {
                MmsLog.w(IPMSG_TAG, "convertMmsOrSmsToIpMessage(): Convert file attachment!");
                if (convertAttachmentToIpMessage(slideshowModel.getAttachFiles().get(0))) {
                    if (mSubjectTextEditor != null && mSubjectTextEditor.getVisibility() == View.VISIBLE) {
                        showSubjectEditor(false);
                    }
                    mWorkingMessage.removeAttachment(false);
                    drawBottomPanel();
                    updateSendButtonState();
                } else {
                    MmsLog.w(IPMSG_TAG, "");
                }
            } else if (slideshowModel.size() > 1) {
                MmsLog.w(IPMSG_TAG, "convertMmsOrSmsToIpMessage(): More than 1 slide for convert!");
            } else {
                MmsLog.w(IPMSG_TAG, "convertMmsOrSmsToIpMessage(): No slide! for convert");
            }
        } else {
            sendIpTextMessage();
            mTextEditor.setText("");
        }
    }

    private void convertSlideToIpMessage(SlideModel slideModel) {
        if (null == slideModel) {
            MmsLog.w(IPMSG_TAG, "convertSlideToIpMessage(): No content for convert! slide is null!");
            return;
        }
        MmsLog.w(IPMSG_TAG, "convertSlideToIpMessage(): start");
        if (slideModel.hasVideo()) {
            if (convertVideoToIpMessage(slideModel.getVideo())) {
                mIpMsgHandler.postDelayed(mSendVideo, 100);
            }
            return;
        }
        if (slideModel.hasAudio()) {
            if (convertAudioToIpMessage(slideModel.getAudio())) {
                mIpMsgHandler.postDelayed(mSendAudio, 100);
            }
            return;
        }
        if (slideModel.hasImage()) {
            if (convertImageToIpMessage(slideModel.getImage())) {
                mIpMsgHandler.postDelayed(mSendPic, 100);
            }
            return;
        }
        if (slideModel.hasText()) {
            // do nothings
            return;
        }
    }

    private boolean convertVideoToIpMessage(VideoModel videoModel) {
        if (null == videoModel) {
            MmsLog.w(IPMSG_TAG, "convertVideoToIpMessage(): video is null.");
            return false;
        }
        InputStream is = null;
        FileOutputStream os = null;
        try {
            MmsLog.d(IPMSG_TAG, "convertVideoToIpMessage(): contentType = " + videoModel.getContentType()
                    + ", mediaSize = " + videoModel.getMediaSize() + ", duration = " + videoModel.getDuration()
                    + ", src = " + videoModel.getSrc() + ", uri = " + videoModel.getUri());

            String fileName = "";
            if (videoModel.getSrc().lastIndexOf(".") > -1) {
                fileName = videoModel.getSrc();
            } else {
                fileName = videoModel.getSrc() + "." + MimeTypeMap.getSingleton()
                        .getExtensionFromMimeType(videoModel.getContentType());
            }

            mPhotoFilePath = MmsConfig.getVideoTempPath(this) + File.separator + fileName;
            mDstPath = mPhotoFilePath;
            mDuration = videoModel.getDuration();
            MmsLog.d(IPMSG_TAG, "convertVideoToIpMessage(): mDstPath = " + mDstPath);

            File file = new File(mDstPath);
            is = this.getContentResolver().openInputStream(videoModel.getUri());
            os = new FileOutputStream(file);
            byte[] buffer = new byte[2048];
            for (int len = 0; (len = is.read(buffer)) != -1; ) {
                os.write(buffer, 0, len);
            }
        } catch (FileNotFoundException e) {
            MmsLog.w(IPMSG_TAG, "convertVideoToIpMessage(): can not found this part file!", e);
            return false;
        } catch (IOException e) {
            MmsLog.w(IPMSG_TAG, "convertVideoToIpMessage(): IOException happened.", e);
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
                MmsLog.w(IPMSG_TAG, "convertVideoToIpMessage(): IOException happened.2", e);
            }
        }
        return true;
    }

    private boolean convertAudioToIpMessage(AudioModel audioModel) {
        if (null == audioModel) {
            MmsLog.w(IPMSG_TAG, "convertAudioToIpMessage(): audio is null.");
            return false;
        }
        InputStream is = null;
        FileOutputStream os = null;
        try {
            MmsLog.d(IPMSG_TAG, "convertAudioToIpMessage(): contentType = " + audioModel.getContentType()
                    + ", mediaSize = " + audioModel.getMediaSize() + ", duration = " + audioModel.getDuration()
                    + ", src = " + audioModel.getSrc() + ", uri = " + audioModel.getUri());

            String fileName = "";
            if (audioModel.getSrc().lastIndexOf(".") > -1) {
                fileName = audioModel.getSrc();
            } else {
                fileName = audioModel.getSrc() + "." + MimeTypeMap.getSingleton()
                        .getExtensionFromMimeType(audioModel.getContentType());
            }

            mPhotoFilePath = MmsConfig.getAudioTempPath(this) + File.separator + fileName;
            mDstPath = mPhotoFilePath;
            int oneKiloMillisecond = 1000;
            mDuration = audioModel.getDuration() / oneKiloMillisecond;
            MmsLog.d(IPMSG_TAG, "convertAudioToIpMessage(): mDstPath = " + mDstPath);

            File file = new File(mDstPath);
            is = this.getContentResolver().openInputStream(audioModel.getUri());
            os = new FileOutputStream(file);
            byte[] buffer = new byte[2048];
            for (int len = 0; (len = is.read(buffer)) != -1; ) {
                os.write(buffer, 0, len);
            }
        } catch (FileNotFoundException e) {
            MmsLog.w(IPMSG_TAG, "convertAudioToIpMessage(): can not found this part file!", e);
            return false;
        } catch (IOException e) {
            MmsLog.w(IPMSG_TAG, "convertAudioToIpMessage(): IOException happened.", e);
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
                MmsLog.w(IPMSG_TAG, "convertAudioToIpMessage(): IOException happened.2", e);
            }
        }
        return true;
    }

    private boolean convertImageToIpMessage(ImageModel imageModel) {
        if (null == imageModel) {
            MmsLog.w(IPMSG_TAG, "convertImageToIpMessage(): video is null.");
            return false;
        }
        InputStream is = null;
        FileOutputStream os = null;
        try {
            MmsLog.d(IPMSG_TAG, "convertImageToIpMessage(): contentType = " + imageModel.getContentType()
                    + ", mediaSize = " + imageModel.getMediaSize() + ", duration = " + imageModel.getDuration()
                    + ", src = " + imageModel.getSrc() + ", uri = " + imageModel.getUri());

            String fileName = "";
            if (imageModel.getSrc().lastIndexOf(".") > -1) {
                fileName = imageModel.getSrc();
            } else {
                fileName = imageModel.getSrc() + "." + MimeTypeMap.getSingleton()
                        .getExtensionFromMimeType(imageModel.getContentType());
            }

            mPhotoFilePath = MmsConfig.getPicTempPath(this) + File.separator + fileName;
            mDstPath = mPhotoFilePath;
            mDuration = imageModel.getDuration();
            MmsLog.d(IPMSG_TAG, "convertImageToIpMessage(): mDstPath = " + mDstPath);

            File file = new File(mDstPath);
            is = this.getContentResolver().openInputStream(imageModel.getUri());
            os = new FileOutputStream(file);
            byte[] buffer = new byte[2048];
            for (int len = 0; (len = is.read(buffer)) != -1; ) {
                os.write(buffer, 0, len);
            }
        } catch (FileNotFoundException e) {
            MmsLog.w(IPMSG_TAG, "convertImageToIpMessage(): can not found this part file!", e);
            return false;
        } catch (IOException e) {
            MmsLog.w(IPMSG_TAG, "convertImageToIpMessage(): IOException happened.", e);
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
                MmsLog.w(IPMSG_TAG, "convertImageToIpMessage(): IOException happened.2", e);
            }
        }
        return true;
    }

    private boolean convertAttachmentToIpMessage(FileAttachmentModel fileAttachmentModel) {
        if (null == fileAttachmentModel) {
            MmsLog.w(IPMSG_TAG, "convertSlideToIpMessage(): No content for convert! fileAttachment is null!");
            return false;
        }

        if (fileAttachmentModel.getContentType().equals(MmsContentType.TEXT_VCARD)) {
            MmsLog.w(IPMSG_TAG, "convertSlideToIpMessage(): vCard");
            if (convertVCardToIpMessage((VCardModel) fileAttachmentModel)) {
                mIpMsgHandler.postDelayed(mSendVcard, 100);
                return true;
            } else {
                return false;
            }
        }

        if (fileAttachmentModel.getContentType().equals(MmsContentType.TEXT_VCALENDAR)) {
            MmsLog.w(IPMSG_TAG, "convertSlideToIpMessage(): vCalendar");
            if (convertVCalendarToIpMessage((VCalendarModel) fileAttachmentModel)) {
                mIpMsgHandler.postDelayed(mSendCalendar, 100);
                return true;
            } else {
                return false;
            }
        }
        MmsLog.w(IPMSG_TAG, "convertSlideToIpMessage(): Unsupport file attachment. content-type = "
            + fileAttachmentModel.getContentType());
        return false;
    }

    private boolean convertVCardToIpMessage(VCardModel vCardModel) {
        if (null == vCardModel) {
            MmsLog.w(IPMSG_TAG, "convertVCardToIpMessage(): vCard is null.");
            return false;
        }
        InputStream is = null;
        FileOutputStream os = null;
        try {
            MmsLog.d(IPMSG_TAG, "convertVCardToIpMessage(): contentType = " + vCardModel.getContentType()
                    + ", attachSize = " + vCardModel.getAttachSize()
                    + ", src = " + vCardModel.getSrc() + ", uri = " + vCardModel.getUri());

            String fileName = "";
            if (vCardModel.getSrc().lastIndexOf(".") > -1) {
                fileName = vCardModel.getSrc();
            } else {
                fileName = vCardModel.getSrc() + "." + MimeTypeMap.getSingleton()
                        .getExtensionFromMimeType(vCardModel.getContentType());
            }
            mIpMessageVcardName = fileName;
            mDstPath = MmsConfig.getVcardTempPath(this) + File.separator + fileName;
            MmsLog.d(IPMSG_TAG, "convertVCardToIpMessage(): mDstPath = " + mDstPath);

            File file = new File(mDstPath);
            is = this.getContentResolver().openInputStream(vCardModel.getUri());
            os = new FileOutputStream(file);
            byte[] buffer = new byte[2048];
            for (int len = 0; (len = is.read(buffer)) != -1; ) {
                os.write(buffer, 0, len);
            }
        } catch (FileNotFoundException e) {
            MmsLog.w(IPMSG_TAG, "convertVCardToIpMessage(): can not found this part file!", e);
            return false;
        } catch (IOException e) {
            MmsLog.w(IPMSG_TAG, "convertVCardToIpMessage(): IOException happened.", e);
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
                MmsLog.w(IPMSG_TAG, "convertVCardToIpMessage(): IOException happened.2", e);
            }
        }
        return true;
    }

    private boolean convertVCalendarToIpMessage(VCalendarModel vCalendarModel) {
        if (null == vCalendarModel) {
            MmsLog.w(IPMSG_TAG, "convertVCalendarToIpMessage(): vCard is null.");
            return false;
        }
        InputStream is = null;
        FileOutputStream os = null;
        try {
            MmsLog.d(IPMSG_TAG, "convertVCalendarToIpMessage(): contentType = " + vCalendarModel.getContentType()
                    + ", attachSize = " + vCalendarModel.getAttachSize()
                    + ", src = " + vCalendarModel.getSrc() + ", uri = " + vCalendarModel.getUri());

            String fileName = "";
            if (vCalendarModel.getSrc().lastIndexOf(".") > -1) {
                fileName = vCalendarModel.getSrc();
            } else {
                fileName = vCalendarModel.getSrc() + "." + MimeTypeMap.getSingleton()
                        .getExtensionFromMimeType(vCalendarModel.getContentType());
            }
            mCalendarSummary = fileName;
            mDstPath = MmsConfig.getVcalendarTempPath(this) + File.separator + fileName;
            MmsLog.d(IPMSG_TAG, "convertVCalendarToIpMessage(): mDstPath = " + mDstPath);

            File file = new File(mDstPath);
            is = this.getContentResolver().openInputStream(vCalendarModel.getUri());
            os = new FileOutputStream(file);
            byte[] buffer = new byte[2048];
            for (int len = 0; (len = is.read(buffer)) != -1; ) {
                os.write(buffer, 0, len);
            }
        } catch (FileNotFoundException e) {
            MmsLog.w(IPMSG_TAG, "convertVCalendarToIpMessage(): can not found this part file!", e);
            return false;
        } catch (IOException e) {
            MmsLog.w(IPMSG_TAG, "convertVCalendarToIpMessage(): IOException happened.", e);
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
                MmsLog.w(IPMSG_TAG, "convertVCalendarToIpMessage(): IOException happened.2", e);
            }
        }
        return true;
    }

    /** M:
     * whether the current recipient(s) are ipmessage user or not
     * @return
     */
    private boolean isCurrentRecipientIpMessageUser() {
        /// M: fix ALPS01033728, return false while ipmessage plug out.
        if (!IpMessageUtils.getIpMessagePlugin(this).isActualPlugin()) {
            return false;
        }
        if (isRecipientsEditorVisible()) {
            List<String> numbers = mRecipientsEditor.getNumbers();
            if (numbers != null && numbers.size() > 0) {
                for (String number : numbers) {
                    if (!IpMessageUtils.getContactManager(this).isIpMessageNumber(number)) {
                        return false;
                    }
                }
                return true;
            }
        } else {
            /// M: Fix ipmessage bug @{
            String[] numbers = mConversation.getRecipients().getNumbers();
            if (numbers != null && numbers.length > 0) {
                for (String number : numbers) {
                    if (!IpMessageUtils.getContactManager(this).isIpMessageNumber(number)) {
                        return false;
                    }
                }
                return true;
            }
            /// @}
        }
        return false;
    }

   /// M: Fix ipmessage bug ,fix bug ALPS 01556382@{
    private boolean isCurrentIpmessageSendable() {
        if (!IpMessageUtils.getIpMessagePlugin(this).isActualPlugin()) {
            return false;
        }
        if (isRecipientsEditorVisible()) {
            List<String> numbers = mRecipientsEditor.getNumbers();
            if (numbers != null && numbers.size() == 1) {
                String number = numbers.get(0);
                if (IpMessageUtils.getContactManager(this).getStatusByNumber(number) == ContactStatus.OFFLINE) {
                    return false;
                } else {
                    return true;
                }
            }
        } else {
            String[] numbers = mConversation.getRecipients().getNumbers();
            if (numbers != null && numbers.length == 1) {
                String number = numbers[0];
                if (IpMessageUtils.getContactManager(this).getStatusByNumber(number) == ContactStatus.OFFLINE) {
                    return false;
                } else {
                    return true;
                }
            }
        }
        return true;
    }
    /// @}

    private boolean isIpMessageRecipients(String number) {
        mIsIpMessageRecipients = IpMessageUtils.getContactManager(this).isIpMessageNumber(number);
        return mIsIpMessageRecipients;
    }

    private Runnable mHideReminderRunnable = new Runnable() {
        @Override
        public void run() {
            MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "notificationsReceived(): hide reminder.");
            if (null != mTypingStatusView) {
                mTypingStatusView.setVisibility(View.GONE);
            }
        }
    };

    //show mms has cost reminder view
    private Runnable mShowMmsReminderRunnable = new Runnable() {
        @Override
        public void run() {
            MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "notificationsReceived(): show mms reminder view.");
            if (null != mTypingStatusView) {
                mTypingStatus.setText(IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                        .getSingleString(IpMessageConsts.string.ipmsg_mms_cost_remind));
                mTypingStatusView.setVisibility(View.VISIBLE);
            }
        }
    };

  //show joyn has cost reminder view
    private Runnable mShowJoynReminderRunnable = new Runnable() {
        @Override
        public void run() {
            MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "notificationsReceived(): show joyn reminder view.");
            if (null != mTypingStatusView) {
                mTypingStatus.setText(IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                        .getSingleString(IpMessageConsts.string.ipmsg_joyn_cost_remind));
                mTypingStatusView.setVisibility(View.VISIBLE);
            }
        }
    };

    private final int MMS_COST_REMINDER = 0;
    private final int JOYN_COST_REMINDER = 1;

    private void showReminderView(int type) {
        if (type == MMS_COST_REMINDER) {
            mIpMsgHandler.removeCallbacks(mShowMmsReminderRunnable);
            mIpMsgHandler.post(mShowMmsReminderRunnable);
        } else if (type == JOYN_COST_REMINDER) {
            mIpMsgHandler.removeCallbacks(mShowJoynReminderRunnable);
            mIpMsgHandler.post(mShowJoynReminderRunnable);
        }
        mIpMsgHandler.removeCallbacks(mHideReminderRunnable);
        mIpMsgHandler.postDelayed(mHideReminderRunnable, 2000);
    }

    private String mChatSenderName = "";
    @Override
    public void notificationsReceived(Intent intent) {
        MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "compose.notificationsReceived(): start, intent = " + intent);
        String action = intent.getAction();
        if (TextUtils.isEmpty(action)) {
            return;
        }
        switch (IpMessageUtils.getActionTypeByAction(action)) {
        /// M: toast and update UI after activate ipmessage service @{
        case IpMessageUtils.IPMSG_REG_STATUS_ACTION:
            int regStatus = intent.getIntExtra(IpMessageConsts.RegStatus.REGSTATUS, 0);
            switch (regStatus) {
            case IpMessageConsts.RegStatus.REG_OVER:
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (mUiHandler != null) {
                            mUiHandler.sendEmptyMessage(MessageUtils.UPDATE_SENDBUTTON);
                        }
                        Toast.makeText(
                                getApplicationContext(),
                                IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                        .getSingleString(
                                                IpMessageConsts.string.ipmsg_nms_enable_success),
                                Toast.LENGTH_SHORT).show();
                    }
                });
                break;
            default:
                break;
            }
            MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "notificationsReceived(): regStatus = "
                    + regStatus);
            break;
        /// @}
        case IpMessageUtils.IPMSG_IM_STATUS_ACTION:
            MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "notificationsReceived(): IM status, mChatModeNumber = "
                + mChatModeNumber);
                if (!mIsIpServiceEnabled || !isNetworkConnected(getApplicationContext())
                    || isRecipientsEditorVisible() || mConversation.getRecipients().size() != 1
                    || !mIsIpMessageRecipients || TextUtils.isEmpty(mChatModeNumber)) {
                return;
            }
            String number = intent.getStringExtra(IpMessageConsts.NUMBER);
            MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "notificationsReceived(): number = " + number
                + ", mChatModeNumber = " + mChatModeNumber);
            if (!TextUtils.isEmpty(number) && isChatModeNumber(number)) {
                int status = IpMessageUtils.getContactManager(this).getStatusByNumber(mChatModeNumber);
                MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "notificationsReceived(): IM status. number = " + number
                    + ", status = " + status);
                Contact contact = Contact.get(number, false);
                mChatSenderName = contact.getName();
                switch (status) {
                case ContactStatus.TYPING:
                    mIpMsgHandler.post(mShowTypingStatusRunnable);
                    break;
                case ContactStatus.STOP_TYPING:
                    mIpMsgHandler.post(mHideTypingStatusRunnable);
                    break;
                case ContactStatus.RECORDING:
                case ContactStatus.STOP_RECORDING:
                    return;
                case ContactStatus.ONLINE:
                case ContactStatus.OFFLINE:
                    MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "notificationsReceived(): user online status changed, "
                        + "number = " + number + ", mChatModeNumber = " + mChatModeNumber);
                    mMsgListAdapter.setOnlineDividerString(getOnlineDividerString(IpMessageUtils.getContactManager(this)
                        .getStatusByNumber(mConversation.getRecipients().get(0).getNumber())));
                    mMsgListAdapter.updateOnlineDividerTime();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mMsgListAdapter.notifyDataSetChanged();
                            /// M: Fix ipmessage bug @{
                            int preChatMode = mCurrentChatMode ;
                            updateCurrentChatMode(null) ;
                            if (preChatMode != mCurrentChatMode) {
                                showSmsOrMmsSendButton(false);
                                invalidateOptionsMenu();
                            }
                            /// @}
                        }
                    });
                    return;
                case ContactStatus.STATUSCOUNT:
                default:
                    return;
                }
            }
            break;
        case IpMessageUtils.IPMSG_ERROR_ACTION:
        case IpMessageUtils.IPMSG_NEW_MESSAGE_ACTION:
        case IpMessageUtils.IPMSG_REFRESH_CONTACT_LIST_ACTION:
        case IpMessageUtils.IPMSG_REFRESH_GROUP_LIST_ACTION:
        case IpMessageUtils.IPMSG_SERCIVE_STATUS_ACTION:
        case IpMessageUtils.IPMSG_SAVE_HISTORY_ACTION:
        case IpMessageUtils.IPMSG_ACTIVATION_STATUS_ACTION:
            break;
        case IpMessageUtils.IPMSG_IP_MESSAGE_STATUS_ACTION:
            if (mMsgListAdapter != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mMsgListAdapter.setClearCacheFlag(true);
                        mMsgListAdapter.notifyDataSetChanged();
                    }
                });
            }
            break;
        /// M: Fix ipmessage bug ,fix bug ALPS 01585545@{
        case IpMessageUtils.IPMSG_SIM_INFO_ACTION:
            showInvitePanel();
            break;
        /// @}
        case IpMessageUtils.IPMSG_DOWNLOAD_ATTACH_STATUS_ACTION:
        case IpMessageUtils.IPMSG_SET_PROFILE_RESULT_ACTION:
        case IpMessageUtils.IPMSG_BACKUP_MSG_STATUS_ACTION:
        case IpMessageUtils.IPMSG_RESTORE_MSG_STATUS_ACTION:
        default:
            return;
        }
    }

    private boolean onIpMessageMenuItemClick(MenuItem menuItem, MessageItem msgItem) {
        MmsLog.d(IPMSG_TAG, "onIpMessageMenuItemClick(): ");
        switch (menuItem.getItemId()) {

        case MENU_SEND_VIA_TEXT_MSG:
            if (IpMessageUtils.getServiceManager(this).getIntegrationMode() == IpMessageConsts.IntegrationMode.CONVERGED_INBOX) {
                    jumpToJoynChat(false, msgItem.mIpMessage);
            } else {
                if (mWorkingMessage.requiresMms() || !TextUtils.isEmpty(mTextEditor.getText().toString())
                        || mIpMessageDraft != null) {
                    showDiscardCurrentMessageDialog(msgItem.mMsgId);
                } else {
                    sendViaMmsOrSms(msgItem.mMsgId);
                }
            }
            return true;

        case MENU_RETRY:
                IpMessageUtils.getMessageManager(this).resendMessage(msgItem.mMsgId,
                        msgItem.getSubId());
            return true;

        case MENU_FORWARD_IPMESSAGE:
        	 MmsLog.d(TAG, "MENU_FORWARD_IPMESSAGE");
             hideInputMethod();
             forwardIpMsg(ComposeMessageActivity.this, msgItem.mMsgId);
             return true;

        case MENU_DELETE_IP_MESSAGE:
            MmsLog.d(IPMSG_TAG, "onIpMessageMenuItemClick(): Delete IP message");
            DeleteMessageListener delMsgListener = new DeleteMessageListener(msgItem);
            String where = Telephony.Mms._ID + "=" + msgItem.mMsgId;
            String[] projection = new String[] { Sms.Inbox.THREAD_ID };
            MmsLog.d(IPMSG_TAG, "onIpMessageMenuItemClick(): where = " + where);
            Cursor queryCursor = Sms.query(getContentResolver(), projection, where, null);
            if (queryCursor.moveToFirst()) {
                mThreadId = queryCursor.getLong(0);
                queryCursor.close();
            }
            confirmDeleteDialog(delMsgListener, msgItem.mLocked);
            return true;

        case MENU_SHARE:
            IpMessage ipMsp = IpMessageUtils.getMessageManager(this).getIpMsgInfo(msgItem.mMsgId);
            shareIpMsg(ipMsp);
            return true;

        case MENU_EXPORT_SD_CARD:
            IpMessage ipMessageForSave = IpMessageUtils.getMessageManager(this).getIpMsgInfo(msgItem.mMsgId);
            MmsLog.d(IPMSG_TAG, "onIpMessageMenuItemClick(): Save IP message. msgId = " + msgItem.mMsgId
                + ", type = " + ipMessageForSave.getType());
            if (ipMessageForSave.getType() >= IpMessageType.PICTURE
                    && ipMessageForSave.getType() < IpMessageType.UNKNOWN_FILE) {
                saveMsgInSDCard((IpAttachMessage) ipMessageForSave);
            }
            return true;

        case MENU_VIEW_IP_MESSAGE:
            IpMessage ipMessageForView = IpMessageUtils.getMessageManager(this).getIpMsgInfo(msgItem.mMsgId);
            MmsLog.d(IPMSG_TAG, "onIpMessageMenuItemClick(): View IP message. msgId = " + msgItem.mMsgId
                + ", type = " + ipMessageForView.getType());
            if (ipMessageForView.getType() >= IpMessageType.PICTURE
                    && ipMessageForView.getType() < IpMessageType.UNKNOWN_FILE) {
                openMedia(msgItem.mMsgId, (IpAttachMessage) ipMessageForView);
            }
            return true;

        default:
            break;
        }
        return false;
    }

    private void saveMsgInSDCard(IpAttachMessage ipAttachMessage) {
        if (!IpMessageUtils.getSDCardStatus()) {
            IpMessageUtils.createLoseSDCardNotice(this,
                                    IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                                .getSingleString(IpMessageConsts.string.ipmsg_cant_save));
            return;
        }

        long availableSpace = IpMessageUtils.getSDcardAvailableSpace();
        int size = ipAttachMessage.getSize();

        if (availableSpace <= IpMessageUtils.SDCARD_SIZE_RESERVED || availableSpace <= size) {
            String tips = IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                            .getSingleString(IpMessageConsts.string.ipmsg_sdcard_space_not_enough);
            Toast.makeText(this, tips, Toast.LENGTH_LONG).show();
            return;
        }

        String source = ipAttachMessage.getPath();
        if (TextUtils.isEmpty(source)) {
            MmsLog.e(IPMSG_TAG, "saveMsgInSDCard(): save ipattachmessage failed, source empty!");
            Toast.makeText(this, getString(R.string.copy_to_sdcard_fail), Toast.LENGTH_SHORT).show();
            return;
        }

        String attName = source.substring(source.lastIndexOf("/") + 1);
        String dstFile = "";
        dstFile = IpMessageUtils.getCachePath(this) + attName;
        int i = 1;
        while (IpMessageUtils.isExistsFile(dstFile)) {
            dstFile = IpMessageUtils.getCachePath(this) + "(" + i + ")" + attName;
            i++;
        }
        IpMessageUtils.copy(source, dstFile);
        String saveSuccess = String.format(
                                        IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                                    .getSingleString(IpMessageConsts.string.ipmsg_save_file),
                                        dstFile);
        Toast.makeText(this, saveSuccess, Toast.LENGTH_SHORT).show();
    }

    private void openMedia(long msgId, IpAttachMessage ipAttachMessage) {
        if (ipAttachMessage == null) {
            MmsLog.e(IPMSG_TAG, "openMedia(): ipAttachMessage is null!", new Exception());
            return;
        }
        MmsLog.d(IPMSG_TAG, "openMedia(): ipAttachMessage type = " + ipAttachMessage.getType());

        if (ipAttachMessage.getType() == IpMessageType.VCARD) {
            IpVCardMessage msg = (IpVCardMessage) ipAttachMessage;
            if (TextUtils.isEmpty(msg.getPath())) {
                MmsLog.e(IPMSG_TAG, "openMedia(): open vCard failed.");
                return;
            }
            if (!IpMessageUtils.getSDCardStatus()) {
                IpMessageUtils.createLoseSDCardNotice(this,
                        IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                    .getSingleString(IpMessageConsts.string.ipmsg_cant_share));
                return;
            }
            if (MessageUtils.getAvailableBytesInFileSystemAtGivenRoot(StorageManagerEx.getDefaultPath())
                    < msg.getSize()) {
                Toast.makeText(this, getString(R.string.export_disk_problem), Toast.LENGTH_LONG).show();
            }
            String dest = IpMessageUtils.getCachePath(this) + "temp"
                + msg.getPath().substring(msg.getPath().lastIndexOf(".vcf"));
            IpMessageUtils.copy(msg.getPath(), dest);
            File vcardFile = new File(dest);
            Uri vcardUri = Uri.fromFile(vcardFile);
            Intent i = new Intent();
            i.setAction(android.content.Intent.ACTION_VIEW);
            i.setDataAndType(vcardUri, "text/x-vcard");
            startActivity(i);
        } else if (ipAttachMessage.getType() == IpMessageType.CALENDAR) {
            IpVCalendarMessage msg = (IpVCalendarMessage) ipAttachMessage;
            if (TextUtils.isEmpty(msg.getPath())) {
                MmsLog.e(IPMSG_TAG, "openMedia(): open vCalendar failed.");
                return;
            }
            if (!IpMessageUtils.getSDCardStatus()) {
                IpMessageUtils.createLoseSDCardNotice(this,
                        IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                    .getSingleString(IpMessageConsts.string.ipmsg_cant_share));
                return;
            }
            if (MessageUtils.getAvailableBytesInFileSystemAtGivenRoot(StorageManagerEx.getDefaultPath())
                    < msg.getSize()) {
                Toast.makeText(this, getString(R.string.export_disk_problem),
                    Toast.LENGTH_LONG).show();
            }
            String dest = IpMessageUtils.getCachePath(this) + "temp"
                + msg.getPath().substring(msg.getPath().lastIndexOf(".vcs"));
            IpMessageUtils.copy(msg.getPath(), dest);
            File calendarFile = new File(dest);
            Uri calendarUri = Uri.fromFile(calendarFile);
            Intent intent = new Intent();
            intent.setAction(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(calendarUri, "text/x-vcalendar");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                MmsLog.e(IPMSG_TAG, "can't open calendar");
            }
        } else {
            Intent intent = new Intent(RemoteActivities.MEDIA_DETAIL);
            intent.putExtra(RemoteActivities.KEY_MESSAGE_ID, msgId);
            IpMessageUtils.startRemoteActivity(this, intent);
        }
    }

    private class InviteFriendsToIpMsgListener implements OnClickListener {
        public void onClick(DialogInterface dialog, int whichButton) {
            mTextEditor.setText(IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                            .getSingleString(IpMessageConsts.string.ipmsg_invite_friends_content));

        }
    }

    private void changeWallPaper() {
        MmsLog.d(IPMSG_TAG, "changeWallPaper(): thread id = " + mConversation.getThreadId());
        /// M: for ALPS00475359 op01 only, fix ANR when edit mms with deleting 1500 messages @{
        if (MmsConfig.isSupportAsyncUpdateWallpaper()) {
            asyncUpdateWallPaper();
        } else {
            if ((mConversation.getThreadId() > 0 && setWallPaper(mConversation.getThreadId()))
                    || setWallPaper(0L)) {
                return;
            }
//        mHeightChangedLinearLayout.setBackgroundColor(getResources().getColor(R.color.list_background));
            mWallPaper.setImageResource(R.color.list_background);
        }
        /// @}
    }

    /// add for ALPS00475359 op01 only, fix ANR when edit mms with deleting 1500 messages @{
    private void asyncUpdateWallPaper() {
        new Thread(new Runnable() {
            public void run() {
                MmsLog.d(TAG, "changeWallPaper(): thread id = " + mConversation.getThreadId());
                if ((mConversation.getThreadId() > 0 && setWallPaper(mConversation.getThreadId()))
                        || setWallPaper(0L)) {
                    return;
                }
                Message msg = mSetWallPaperHandler.obtainMessage(SET_WALLPAPER_FROM_RESOURCE, R.color.list_background, 0);
                msg.sendToTarget();
            }
        }, "AsyncChangeWallPaper").start();
    }
    /// @}

    private boolean setWallPaper(long threadId) {
        MmsLog.d(IPMSG_TAG, "setWallPaper(): threadId = " + threadId);
        Uri tempuri = Uri.withAppendedPath(Telephony.MmsSms.CONTENT_URI, "thread_settings");
        Cursor cursor = getContentResolver().query(tempuri,
            new String[] {Telephony.ThreadSettings._ID, Telephony.ThreadSettings.WALLPAPER},
            Telephony.ThreadSettings.THREAD_ID + " = " + threadId, null, null);
        long threadSettingId = 0L;
        String filePath = null;
        if (null != cursor) {
            if (cursor.moveToFirst()) {
                threadSettingId = cursor.getLong(0);
                filePath = cursor.getString(1);
            }
            cursor.close();
        }
        if (TextUtils.isEmpty(filePath)) {
            return false;
        }
        MmsLog.d(IPMSG_TAG, "setWallPaper(): threadSettingId = " + threadSettingId + ", filePath = " + filePath);
        if (filePath.startsWith("content://") || filePath.startsWith("file://")) {
            Uri imageUri = Uri.parse(filePath);
            InputStream is = null;
            try {
                is = this.getContentResolver().openInputStream(imageUri);
                BitmapDrawable bd = new BitmapDrawable(is);
                // mHeightChangedLinearLayout.setBackgroundDrawable(bd);
                /// M: for ALPS00475359 op01 only, fix ANR when edit mms with deleting 1500 messages @{
                if (MmsConfig.isSupportAsyncUpdateWallpaper()) {
                    Message msg = mSetWallPaperHandler.obtainMessage(SET_WALLPAPER_FROM_BITMAP, bd);
                    msg.sendToTarget();
                } else {
                    mWallPaper.setImageDrawable(bd);
                }
                /// @}
                return true;
            } catch (FileNotFoundException e) {
                MmsLog.w(IPMSG_TAG, "Unknown wall paper resource! filePath = " + filePath);
            } catch (OutOfMemoryError e) {
                MmsLog.w(IPMSG_TAG, "OutOfMemoryError.", e);
            } finally {
                if (null != is) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        MmsLog.w(IPMSG_TAG, "IOException happened when close InputStream.", e);
                    }
                }
            }
        } else if (filePath.startsWith(IpMessageUtils.WALLPAPER_PATH)) {
            Uri imageUri = ContentUris.withAppendedId(tempuri, threadId);
            InputStream is = null;
            try {
                is = this.getContentResolver().openInputStream(imageUri);
                BitmapDrawable bd = new BitmapDrawable(is);
                /// M: for ALPS00475359 op01 only, fix ANR when edit mms with deleting 1500 messages @{
                if (MmsConfig.isSupportAsyncUpdateWallpaper()) {
                    Message msg = mSetWallPaperHandler.obtainMessage(SET_WALLPAPER_FROM_BITMAP, bd);
                    msg.sendToTarget();
                } else {
                    // mHeightChangedLinearLayout.setBackgroundDrawable(bd);
                    mWallPaper.setImageDrawable(bd);
                }
                /// @}
                return true;
            } catch (FileNotFoundException e) {
                MmsLog.w(IPMSG_TAG, "Unknown wall paper resource! filePath = " + filePath, e);
            } catch (NullPointerException e) {
                MmsLog.w(IPMSG_TAG, "NullPointerException.", e);
            } catch (OutOfMemoryError e) {
                MmsLog.w(IPMSG_TAG, "OutOfMemoryError.", e);
            } finally {
                if (null != is) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        MmsLog.w(IPMSG_TAG, "IOException happened when close InputStream.", e);
                    }
                    MmsLog.w(IPMSG_TAG, "Unknown wall paper resource! filePath = " + filePath);
                }
            }
        } else if (TextUtils.isDigitsOnly(filePath)) {
            int resId = 0;
            try {
                resId = Integer.parseInt(filePath);
                if (resId > 0) {
                    /// M: for ALPS00475359 op01 only, fix ANR when edit mms with deleting 1500 messages @{
                    if (MmsConfig.isSupportAsyncUpdateWallpaper()) {
                        Message msg = mSetWallPaperHandler.obtainMessage(SET_WALLPAPER_FROM_RESOURCE, resId, 0);
                        msg.sendToTarget();
                    } else {
                        // mHeightChangedLinearLayout.setBackgroundResource(resId);
                        mWallPaper.setImageResource(resId);
                    }
                    /// @}
                    return true;
                }
            } catch (NumberFormatException e) {
                MmsLog.w(IPMSG_TAG, "Unknown wall paper resource id! resourceId = " + resId);
            }
            MmsLog.w(IPMSG_TAG, "Unknown wall paper resource! filePath = " + filePath);
        }
        return false;
    }

    /**
     * M:
     * @param ipMessage
     */
    private void shareIpMsg(IpMessage ipMessage) {
        if (null == ipMessage) {
            MmsLog.d(IPMSG_TAG, "shareIpMsg(): message item is null!");
            return;
        }
        if (ipMessage instanceof IpAttachMessage) {
            if (!IpMessageUtils.getSDCardStatus()) {
                IpMessageUtils.createLoseSDCardNotice(this,
                        IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                    .getSingleString(IpMessageConsts.string.ipmsg_cant_share));
                return;
            }
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent = setIntent(intent, ipMessage);
        intent.putExtra(Intent.EXTRA_SUBJECT, IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                                            .getSingleString(IpMessageConsts.string.ipmsg_logo));
        try {
            startActivity(Intent.createChooser(intent,
                    IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                .getSingleString(IpMessageConsts.string.ipmsg_share_title)));
        } catch (Exception e) {
            MmsLog.d(IPMSG_TAG, "shareIpMsg(): Exception:" + e.toString());
        }
    }

    private Intent setIntent(Intent intent, IpMessage ipMessage) {
        if (ipMessage.getType() == IpMessageType.TEXT) {
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, ((IpTextMessage) ipMessage).getBody());
        } else if (ipMessage.getType() == IpMessageType.PICTURE) {
            IpImageMessage msg = (IpImageMessage) ipMessage;
            int index = msg.getPath().lastIndexOf(".");
            String end = msg.getPath().substring(index);
            String dest = IpMessageUtils.getCachePath(this) + "temp" + end;
            IpMessageUtils.copy(msg.getPath(), dest);
            intent.setType("image/*");
            File f = new File(dest);
            Uri u = Uri.fromFile(f);
            intent.putExtra(Intent.EXTRA_STREAM, u);
            if (!TextUtils.isEmpty(msg.getCaption())) {
                intent.putExtra(SMS_BODY, msg.getCaption());
                intent.putExtra(Intent.EXTRA_TEXT, msg.getCaption());
            }
        } else if (ipMessage.getType() == IpMessageType.VOICE) {
            IpVoiceMessage msg = (IpVoiceMessage) ipMessage;
            int index = msg.getPath().lastIndexOf("/");
            String name = msg.getPath().substring(index);
            String dest = IpMessageUtils.getCachePath(this) + name;
            IpMessageUtils.copy(msg.getPath(), dest);
            intent.setType("audio/*");
            File f = new File(dest);
            Uri u = Uri.fromFile(f);
            intent.putExtra(Intent.EXTRA_STREAM, u);
            if (!TextUtils.isEmpty(msg.getCaption())) {
                intent.putExtra(SMS_BODY, msg.getCaption());
                intent.putExtra(Intent.EXTRA_TEXT, msg.getCaption());
            }
        } else if (ipMessage.getType() == IpMessageType.VCARD) {
            IpVCardMessage msg = (IpVCardMessage) ipMessage;
            int index = msg.getPath().lastIndexOf("/");
            String name = msg.getPath().substring(index);
            String dest = IpMessageUtils.getCachePath(this) + name;
            IpMessageUtils.copy(msg.getPath(), dest);
            File f = new File(dest);
            Uri u = Uri.fromFile(f);
            intent.setDataAndType(u, "text/x-vcard");
            intent.putExtra(Intent.EXTRA_STREAM, u);
        } else if (ipMessage.getType() == IpMessageType.VIDEO) {
            IpVideoMessage msg = (IpVideoMessage) ipMessage;
            int index = msg.getPath().lastIndexOf("/");
            String name = msg.getPath().substring(index);
            String dest = IpMessageUtils.getCachePath(this) + name;
            IpMessageUtils.copy(msg.getPath(), dest);
            intent.setType("video/*");
            File f = new File(dest);
            Uri u = Uri.fromFile(f);
            intent.putExtra(Intent.EXTRA_STREAM, u);
            if (!TextUtils.isEmpty(msg.getCaption())) {
                intent.putExtra(SMS_BODY, msg.getCaption());
                intent.putExtra(Intent.EXTRA_TEXT, msg.getCaption());
            }
        } else if (ipMessage.getType() == IpMessageType.CALENDAR) {
            IpVCalendarMessage msg = (IpVCalendarMessage) ipMessage;
            int index = msg.getPath().lastIndexOf("/");
            String name = msg.getPath().substring(index);
            String dest = IpMessageUtils.getCachePath(this) + name;
            IpMessageUtils.copy(msg.getPath(), dest);
            File f = new File(dest);
            Uri uri = Uri.fromFile(f);
            intent.setType("text/x-vcalendar");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
        } else {
            intent.setType("unknown");
        }
        return intent;
    }

    /**
     * M:
     * @param ids
     */
    private void showResendConfirmDialg(final long currentMsgId, final long currentSubId,
            final long[][] allFailedIpMsgIds) {
        IpMessage ipMessage = IpMessageUtils.getMessageManager(this).getIpMsgInfo(currentMsgId);
        if (ipMessage == null) {
            MmsLog.e(IPMSG_TAG, "showResendConfirmDialg(): ipMessage is null.", new Exception());
            return;
        }
        /// M: add for fix ALPS01600871 @{
        if (!mIsSmsEnabled) {
            return;
        }
        /// @}
        String title = "";
        if (ipMessage.getStatus() == IpMessageStatus.FAILED) {
            title = IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                .getSingleString(IpMessageConsts.string.ipmsg_failed_title);
        } else {
            title = IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                .getSingleString(IpMessageConsts.string.ipmsg_not_delivered_title);
        }
        String sendViaMsg = "";
        if (ipMessage.getType() == IpMessageType.TEXT) {
            sendViaMsg = IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                        .getSingleString(IpMessageConsts.string.ipmsg_resend_via_sms);
        } else {
            sendViaMsg = IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                        .getSingleString(IpMessageConsts.string.ipmsg_resend_via_mms);
        }
        List<String> buttonList = new ArrayList<String>();
        buttonList.add(IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                    .getSingleString(IpMessageConsts.string.ipmsg_try_again));
        if (allFailedIpMsgIds != null && allFailedIpMsgIds.length > 1) {
            buttonList.add(IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                        .getSingleString(IpMessageConsts.string.ipmsg_try_all_again));
        }
        buttonList.add(sendViaMsg);
        final int buttonCount = buttonList.size();
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(title);
        ArrayAdapter<String> resendAdapter = new ArrayAdapter<String>(this, R.layout.resend_dialog_item,
                R.id.resend_item, buttonList);
        builder.setAdapter(resendAdapter, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final int tryAgain = 0;
                final int tryAllAgain = 1;
                final int sendViaMmsOrSms = 2;
                switch (which) {
                case tryAgain:
                    IpMessageUtils.getMessageManager(ComposeMessageActivity.this).resendMessage(currentMsgId,
                        (int) currentSubId);
                    break;
                case tryAllAgain:
                    if (buttonCount == 3) {
                        for (int index = 0; index < allFailedIpMsgIds.length; index++) {
                            IpMessageUtils.getMessageManager(ComposeMessageActivity.this)
                                    .resendMessage(allFailedIpMsgIds[index][0],
                                        (int) allFailedIpMsgIds[index][1]);
                        }
                        break;
                    } else if (buttonCount != 2) {
                        break;
                    } else {
                        /// M: listSize == 2, run case sendViaMmsOrSms.
                        // fall through
                    }
                case sendViaMmsOrSms:
                    if (mWorkingMessage.requiresMms() || !TextUtils.isEmpty(mTextEditor.getText().toString())
                        || mIpMessageDraft != null) {
                        showDiscardCurrentMessageDialog(currentMsgId);
                    } else {
                        sendViaMmsOrSms(currentMsgId);
                    }
                    break;
                default:
                    break;
                }
            }
        });
        builder.show();

    }

    private void sendViaMmsOrSms(long currentMsgId) {
        mJustSendMsgViaCommonMsgThisTime = true;
        final IpMessage ipMessage = IpMessageUtils.getMessageManager(
                ComposeMessageActivity.this).getIpMsgInfo(currentMsgId);
        if (ipMessage != null) {
            mIpMessageDraft = ipMessage;
            if (convertIpMessageToMmsOrSms(false)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (isSubjectEditorVisible()) {
                            showSubjectEditor(false);
                            mWorkingMessage.setSubject(null, true);
                        }
                        drawBottomPanel();
                        boolean isMms = mWorkingMessage.requiresMms();
                        showSmsOrMmsSendButton(isMms  ? true : false);
                    }
                });
                IpMessageUtils.getMessageManager(ComposeMessageActivity.this)
                        .deleteIpMsg(new long[] {currentMsgId}, true);
            }
        }
    }

    private void showDiscardCurrentMessageDialog(final long currentMsgId) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.discard)
            .setMessage(IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                    .getSingleString(IpMessageConsts.string.ipmsg_resend_discard_message))
            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            })
            .setPositiveButton(IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                            .getSingleString(IpMessageConsts.string.ipmsg_continue),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sendViaMmsOrSms(currentMsgId);
                    }
                }
            ).show();
    }
    /**
     * M:
     * @param threadId
     * @return
     */
    private long[][] getAllFailedIpMsgByThreadId(long threadId) {
        Cursor cursor = getContentResolver()
.query(Sms.CONTENT_URI, new String[] {
                Sms._ID, Telephony.TextBasedSmsColumns.SUBSCRIPTION_ID
        },
                    "thread_id = " + threadId + " and ipmsg_id > 0 and type = " + Sms.MESSAGE_TYPE_FAILED, null, null);
        try {
            if (cursor == null) {
                return null;
            }
            long[][] count = new long[cursor.getCount()][2];
            int index = 0;
            while (cursor.moveToNext()) {
                count[index][0] = cursor.getLong(0);
                count[index][1] = cursor.getLong(1);
                index++;
            }
            return count;
        } finally {
            cursor.close();
        }
    }

    /**
     * M:
     *
     */
    public void hiddeInvitePanel() {
        if (mInviteView != null) {
            mInviteView.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * M:
     *
     */
    private void showInvitePanel() {
        if (mInviteView == null ||
            mConversation == null ||
            mConversation.getThreadId() <= 0 ||
            isRecipientsEditorVisible()) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hiddeInvitePanel();
                    }
                });
            return;
        }
    }

    /**
     * M:
     * @param msgId
     * @return
     */
    private boolean forwardIpMsg(Context context, long msgId) {
        if (msgId <= 0) {
            return false;
        }
        Intent intent = ComposeMessageActivity.createIntent(context, 0);
        intent.putExtra(FORWARD_MESSAGE, true); //"forwarded_message", boolean
        intent.putExtra(FORWARD_IPMESSAGE, true); //"ip_msg_media_path", boolean
        intent.putExtra(IP_MESSAGE_ID, msgId); //"ip_msg_id", long
        intent.setClassName(context, "com.android.mms.ui.ForwardMessageActivity");
        context.startActivity(intent);
        return true;
    }

    private Runnable mSendTypingRunnable = new Runnable() {
        @Override
        public void run() {
            MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "send chat mode, typing");
            if (!TextUtils.isEmpty(mChatModeNumber)) {
                IpMessageUtils.getChatManager(ComposeMessageActivity.this)
                    .sendChatMode(mChatModeNumber, ContactStatus.TYPING);
            }
        }
    };

    private Runnable mSendStopTypingRunnable = new Runnable() {
        @Override
        public void run() {
            MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "send chat mode, stop typing");
            if (!TextUtils.isEmpty(mChatModeNumber)) {
                IpMessageUtils.getChatManager(ComposeMessageActivity.this)
                    .sendChatMode(mChatModeNumber, ContactStatus.STOP_TYPING);
            }
        }
    };

    private void clearIpMessageDraft() {
        MmsLog.d(IPMSG_TAG, "clearIpMessageDraft()");
        mIpMessageDraft = null;
        if (null != mIpMessageThumbnail) {
            mIpMessageThumbnail.setVisibility(View.GONE);
            /// M: add for ipmessage for thumbnail delete
            if (null != mIpMessageThumbnailDelete) {
                mIpMessageThumbnailDelete.setVisibility(View.GONE);
            }
        }
        updateSendButtonState();
    }

    private static void toastNoSubCard(Context context) {
        Toast.makeText(context,
                        IpMessageUtils.getResourceManager(context)
                                    .getSingleString(IpMessageConsts.string.ipmsg_no_sim_card),
                        Toast.LENGTH_LONG).show();
    }

    private boolean isChatModeNumber(String number) {
        if (TextUtils.isEmpty(mChatModeNumber) || TextUtils.isEmpty(number)) {
            return false;
        }
        if (mChatModeNumber.equals(number) || mChatModeNumber.endsWith(number) || number.endsWith(mChatModeNumber)) {
            return true;
        }
        return false;
    }

    /// M:added for bug ALPS00317889
    private boolean mShowDialogForMultiImage = false;

    private boolean isNetworkConnected(Context context) {
        boolean isNetworkConnected = false;
        ConnectivityManager connManager = (ConnectivityManager) getSystemService(
            ComposeMessageActivity.this.CONNECTIVITY_SERVICE);
        State state = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
        if (State.CONNECTED == state) {
            isNetworkConnected = true;
        }
        if (!isNetworkConnected) {
            state = connManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).getState();
            if (State.CONNECTED == state) {
                isNetworkConnected = true;
            }
        }
        return isNetworkConnected;
    }

    private NetworkInfo getActiveNetworkInfo(Context context) {
        ConnectivityManager connectivity =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) {
            return null;
        }
        return connectivity.getActiveNetworkInfo();
    }

    private boolean canMmsConvertToIpMessage() {
        if (!mWorkingMessage.requiresMms()) {
            return false;
        }
        if (mWorkingMessage.getSlideshow() == null) {
            return false;
        }
        if (mWorkingMessage.hasSubject()) {
            return false;
        }
        if (mWorkingMessage.getSlideshow().getAttachFiles() != null &&
                mWorkingMessage.getSlideshow().getAttachFiles().size() == 1) {
            MmsLog.d(IPMSG_TAG, "canMmsConvertToIpMessage(): Can not convert file attachment,"
                + " but vCard");
            String contenttype = mWorkingMessage.getSlideshow().getAttachFiles().get(0).getContentType();
            return (contenttype.equals(MmsContentType.TEXT_VCARD)
                    || contenttype.equals(MmsContentType.TEXT_VCALENDAR));
        }
        boolean isMmsCanConvertToIpmessage = !mWorkingMessage.hasSubject()
            && mWorkingMessage.getSlideshow() != null
            && mWorkingMessage.getSlideshow().size() == 1;
        if (isMmsCanConvertToIpmessage && mWorkingMessage.getSlideshow().get(0) != null) {
            SlideModel slideModel = mWorkingMessage.getSlideshow().get(0);
            if (slideModel.hasAudio() && slideModel.hasImage()) {
                return false;
            }
            boolean hasOverCaptionLimit = slideModel.hasText()
                    && slideModel.getText().getText().length() > MmsConfig.getMaxTextLimit();
            if (slideModel.hasAudio() && (!hasOverCaptionLimit || (!slideModel.hasText()))) {
                return true;
            } else if (slideModel.hasImage() && (!hasOverCaptionLimit || (!slideModel.hasText()))) {
                return true;
            } else if (slideModel.hasVideo() && (!hasOverCaptionLimit || (!slideModel.hasText()))) {
                return true;
            }
        }
        return false;
    }

    private void showReplaceAttachDialog() {
        if (mReplaceDialog != null) {
            return; /// M: shown already.
        }
        mReplaceDialog = new AlertDialog.Builder(this)
            .setTitle(IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                    .getSingleString(IpMessageConsts.string.ipmsg_replace_attach))
            .setMessage(IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
                                    .getSingleString(IpMessageConsts.string.ipmsg_replace_attach_msg))
            .setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mIpMessageDraft = null;
                        mReplaceDialog = null;
                        saveIpMessageForAWhile(mIpMessageForSend);
                    }
                }
            )
            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mIpMessageForSend = null;
                        mReplaceDialog = null;
                    }
                }
            )
            .create();
        mReplaceDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mReplaceDialog = null;
            }
        });
        mReplaceDialog.show();
    }
    /// M: show contact detail or create new contact. @{
    private static final int MENU_SHOW_CONTACT          = 121;
    private static final int MENU_CREATE_CONTACT        = 122;
    private MmsQuickContactBadge mQuickContact;
    /// @}

    /// M: Ip message add jump to another chat menu. @{
    private static final int MENU_JUMP_TO_JOYN          = 221;
    private static final int MENU_JUMP_TO_XMS           = 222;
    /// @}

    /// M: Ip message add export chat menu. @{
    private static final int MENU_EXPORT_CHAT           = 223;
    /// @}

    private boolean updateIpMessageCounter(CharSequence text, int start, int before, int count) {
        MmsLog.d(TAG, "updateIpMessageCounter()");
        if (mIsIpServiceEnabled && isNetworkConnected(getApplicationContext())
                && IpMessageUtils.getSDCardStatus() && isCurrentRecipientIpMessageUser()
                && !mWorkingMessage.requiresMms() && !mJustSendMsgViaCommonMsgThisTime
                && text.length() > 0) {
            final int length = text.length();
            mUiHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    MmsLog.d(IPMSG_TAG, "updateIpMessageCounter(): IP text message, mTextEditor.getLineCount() = "
                        + mTextEditor.getLineCount());
					/*HQ_zhangjing add for al812 mms ui begin */
					mTextCounter.setVisibility(View.VISIBLE);
					mTextCounter.setText(length + "/" + MmsConfig.getMaxTextLimit());
					//mTextCounterForDouble.setVisibility(View.VISIBLE);
					mTextCounterForDouble.setText(length + "/" + MmsConfig.getMaxTextLimit());
					//mUniqueSendBtnContainer.setVisibility( View.VISIBLE );
					showOrHideSendbtn();
					/*HQ_zhangjing add for al812 mms ui end */
                }
            }, 100);
            return true;
        }
        return false;
    }

    /// M: fix bug ALPS00414023, update sub state dynamically. @{
    private BroadcastReceiver mSubReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action != null && action.equals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED)) {
                    /// M: fix bug ALPS00429274, dismiss Dialog when SUB_CHANGED @{
                    if (mSubSelectDialog != null && mSubSelectDialog.isShowing()) {
                        mSubSelectDialog.dismiss();
                    }
                    /// @}
                    /// M: Fix Bug: ALPS00503002 ;
                    getSubInfoList();
					/*HQ_zhangjing add for al812 mms ui */
					refreshSendBtnText();
                    updateSendButtonState();
                    MessageUtils.subInfoMap.clear();
                    mMsgListAdapter.notifyDataSetChanged();
                    // show SMS indicator
                    if (!isFinishing() && mIsShowSubIndicator) {
                        MmsLog.d(TAG, "Hide current indicator and show new one.");
                        StatusBarSelectorCreator creator = StatusBarSelectorCreator
                                .getInstance(ComposeMessageActivity.this);
                        creator.updateStatusBarData();
                    }
            /// M: Modify For OP09 feature: dualSendBtn @{
            } else if (MmsConfig.isDualSendButtonEnable() && action != null
                && (action.equals(TelephonyIntents.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED)
                        || action.equals(TelephonyIntents.ACTION_SUBINFO_CONTENT_CHANGE))) {
                mGetSubInfoRunnable.run();
            }
            /// @}
        }
    };

    private void saveAsMms(boolean notify) {
        if (mMmsUtils != null
            && !mMmsUtils.allowSafeDraft(this, MmsConfig.getDeviceStorageFullStatus(), false,
                IMmsUtilsExt.TOAST_TYPE_FOR_SAVE_DRAFT)) {
            return;
        }
        if (mNeedSaveAsMms) {
            mNeedSaveAsMms = false;
            if (mWorkingMessage.saveAsMms(notify) != null) {
                mHasDiscardWorkingMessage = true;
            }
        }
    }

    /// M: add for IP message draft. @{
    private void saveIpMessageDraft() {
        if (mIpMessageDraft == null) {
            MmsLog.e(IPMSG_TAG, "saveIpMessageDraft(): mIpMessageDraft is null!", new Exception());
            return;
        }
        mIpMessageDraft.setStatus(IpMessageStatus.DRAFT);
        mIpMessageDraft = IpMessageUtils.setIpMessageCaption(mIpMessageDraft, mWorkingMessage.getText().toString());

        if (mSubInfoList.size() > 0) {
            for (SubscriptionInfo subInfo : mSubInfoList) {
                if (MmsConfig.isServiceEnabled(this)) {
                    mIpMessageDraft.setSimId(subInfo.getSubscriptionId());
                    MmsLog.d(IPMSG_TAG, "saveIpMessageDraft(): Set IP message draft Sub. subId = "
                            + subInfo.getSubscriptionId());
                    break;
                }
            }
            if (mIpMessageDraft.getSimId() <= 0) {
                MmsLog.e(IPMSG_TAG, "saveIpMessageDraft(): No enabled service Sub card!",
                        new Exception());
                return;
            }
        } else {
            MmsLog.e(IPMSG_TAG, "saveIpMessageDraft(): No Sub card.", new Exception());
            return;
        }

        mIpMessageDraft.setTo(getCurrentNumberString());
        mConversation.setDraftState(true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                MmsLog.d(IPMSG_TAG, "saveIpMessageDraft(): calling API: saveIpMsg().");
                int ret = -1;
                ret = IpMessageUtils.getMessageManager(ComposeMessageActivity.this)
                    .saveIpMsg(mIpMessageDraft, IpMessageSendMode.AUTO);
                if (ret < 0) {
                    MmsLog.w(IPMSG_TAG, "saveIpMessageDraft(): save IP message draft failed.");
                    mConversation.setDraftState(false);
                    if (mToastForDraftSave) {
                        MmsApp.getToastHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MmsApp.getApplication().getApplicationContext(),
                                    R.string.cannot_save_message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } else {
                    MmsLog.d(IPMSG_TAG, "saveIpMessageDraft(): save IP message draft successfully.");
                    if (mToastForDraftSave) {
                        MmsApp.getToastHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MmsApp.getApplication().getApplicationContext(),
                                    R.string.message_saved_as_draft, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private boolean loadIpMessagDraft() {
        MmsLog.d(IPMSG_TAG, "loadIpMessagDraft()");
        if (mIpMessageDraft != null) {
            MmsLog.w(IPMSG_TAG, "loadIpMessagDraft(): mIpMessageDraft is not null!", new Exception());
            return false;
        }
        IpMessage ipMessage = IpMessageUtils.readIpMessageDraft(this, mConversation, mWorkingMessage);
        if (ipMessage != null) {
            mIpMessageDraftId = ipMessage.getId();
            String caption = IpMessageUtils.getIpMessageCaption(ipMessage);
            MmsLog.d(IPMSG_TAG, "loadIpMessagDraft(): ipMessage is not null, mIpMessageDraftId = " + mIpMessageDraftId
                + "caption = " + caption);
            if (!TextUtils.isEmpty(caption)) {
                mWorkingMessage.setText(caption);
                mTextEditor.setText(caption);
            }
            saveIpMessageForAWhile(ipMessage);
            return true;
        }
        MmsLog.w(IPMSG_TAG, "loadIpMessagDraft(): ipMessage is null!");
        return false;
    }
    /// @}

    /// M: after delete the last message of one thread, don't finish this activity if have draft. @{
    private void makeDraftEditable(ContactList recipients) {
        if (mConversation.getThreadId() > 0) {
            Uri threadSettingsUri = mConversation.getThreadSettingsUri();
            if (threadSettingsUri != null) {
                ContentValues values = new ContentValues(5);
                values.put(Telephony.ThreadSettings.WALLPAPER, "");
                values.put(Telephony.ThreadSettings.MUTE, 0);
                values.put(Telephony.ThreadSettings.NOTIFICATION_ENABLE, 1);
                values.put(Telephony.ThreadSettings.RINGTONE, "");
                values.put(Telephony.ThreadSettings.VIBRATE, true);
                getContentResolver().update(threadSettingsUri, values, null, null);
            }
        }
        if (!mConversation.getRecipients().equals(recipients)) {
            mConversation.setRecipients(recipients);
            MmsLog.d(TAG, "makeDraftEditable, do not equal");
        } else {
            MmsLog.d(TAG, "makeDraftEditable, equal");
            mWorkingMessage.asyncDeleteDraftSmsMessage(mConversation);
            mConversation.clearThreadId();
        }
        mWorkingMessage.setConversation(mConversation);
        updateThreadIdIfRunning();
        invalidateOptionsMenu();
        hideRecipientEditor();
        initRecipientsEditor(null);
        isInitRecipientsEditor = true;
    }
    /// @}

    private static final int CELL_PROGRESS_DIALOG = 1;

    @Override
    protected Dialog onCreateDialog(int id) {
        ProgressDialog dialog = null;
        if (id == CELL_PROGRESS_DIALOG) {
            dialog = new ProgressDialog(ComposeMessageActivity.this);
            // mProgressDialog.setTitle(getText(titleResId));
            dialog.setMessage(getString(R.string.sum_search_networks));
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            // / M: fix bug ALPS00451836, remove FLAG_DISMISS_KEYGUARD flags
            if (getWindow() != null) {
                getWindow().clearFlags(
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            }
        }
        return dialog;
    }

   /**
    * M: For OP09 buttonListener.
    * @return
    */
    ViewOnClickListener mDualSendBtnListener = new ViewOnClickListener() {

        public void setSelectedSubId(int subId) {
            mSendSubIdForDualBtn = subId;
        }

        @Override
        public void onClick(View V) {
            if (mSendButtonCanResponse) {
                mSendButtonCanResponse = false;
                if (isPreparedForSending()) {
                    /// M: add for ip message, unread divider
                    mShowUnreadDivider = false;
                    checkRecipientsCount();
                    mUiHandler.sendEmptyMessageDelayed(MSG_RESUME_SEND_BUTTON, RESUME_BUTTON_INTERVAL);

                    /// M: pop-up dialog when request delivery report which is diabled in roaming status. @{
                    SharedPreferences prefs = PreferenceManager
                            .getDefaultSharedPreferences(ComposeMessageActivity.this);
                    boolean requestDeliveryReport = false;
                    requestDeliveryReport = prefs.getBoolean(Long.toString(mSelectedSubId) + "_"
                            + SmsPreferenceActivity.SMS_DELIVERY_REPORT_MODE, false);

                    if (requestDeliveryReport) {
                        if (MmsConfig.isAllowDRWhenRoaming(ComposeMessageActivity.this, (int) mSelectedSubId)) {
                            /// M: Enable the dialog again when not in roaming status.
                            mMmsComposePlugin.enableDRWarningDialog(getApplicationContext(), true, (int) mSelectedSubId);
                        } else {
                            /// M: delivery report not allowed.
                            mMmsComposePlugin.showDisableDRDialog(ComposeMessageActivity.this, (int) mSelectedSubId);
                        }
                    }
                    /// @}
                } else {
                    mSendButtonCanResponse = true;
                    if (!isHasRecipientCount()) {
                        new AlertDialog.Builder(ComposeMessageActivity.this).setIconAttribute(
                            android.R.attr.alertDialogIcon).setTitle(R.string.cannot_send_message).setMessage(
                            R.string.cannot_send_message_reason).setPositiveButton(R.string.yes,
                            new CancelSendingListener()).show();
                    } else {
                        new AlertDialog.Builder(ComposeMessageActivity.this).setIconAttribute(
                            android.R.attr.alertDialogIcon).setTitle(R.string.cannot_send_message).setMessage(
                            R.string.cannot_send_message_reason_no_content).setPositiveButton(R.string.yes,
                            new CancelSendingListener()).show();
                    }
                }
            }
        }
    };

    /// M: fix bug ALPS00941735, set Obsolete ThreadId and except it when ConversationList query
    long mOldThreadID = -1;

    boolean mIsCheckObsolete = false;

    boolean mIsSameConv = true;

    private void checkObsoleteThreadId() {
        if (isRecipientsEditorVisible()) {
            List<String> numbers =  mRecipientsEditor.getNumbers();
            if (numbers != null && numbers.size() > 0) {
                if (mOldThreadID > 0 && mIsCheckObsolete
                    && mIsSameConv && !mWorkingMessage.isDiscarded() && !MmsConfig.getMmsDirMode()
                    && !mIsIpServiceEnabled) {
                    mIsCheckObsolete = false;
                    mWorkingMessage.syncWorkingRecipients();
                    long newThreadId = mWorkingMessage.getConversation().ensureThreadId();
                    if (mOldThreadID != newThreadId) {
                        DraftManager.sEditingThread.add(mOldThreadID);
                        MmsLog.d(TAG, "new recipent list != old, old Obsolete thread id = "
                            + mOldThreadID);
                    }
                }
            }
        }
    }

    /*
     * M: reload working message when working message is not correct.
     */
    private void reloadWorkingMessage() {
        if (mTempMmsUri == null) {
            return;
        }
        WorkingMessage newMessage = WorkingMessage.load(this, mTempMmsUri);

        if (newMessage != null) {
            if (newMessage.hasMediaAttachments()) {
                newMessage.removeAllFileAttaches();
            }
            boolean isMmsBefore = mWorkingMessage.requiresMms();
            newMessage.setSubject(mWorkingMessage.getSubject(), false);

            mWorkingMessage = newMessage;
            updateThreadIdIfRunning();

            boolean isMmsAfter = mWorkingMessage.requiresMms();
            if (!isMmsAfter && !isMmsBefore) {
                mWorkingMessage.setForceUpdateThreadId(true);
            }
        }
    }

    /**
     * M: For Just not send length required mms with Slot one.<br>
     * This function:check the mms is whether legnth required mms.
     *
     * @return
     */
    public boolean isLengthRequiredMms() {
        MmsLog.d(TAG, "isLengthRequiredMms Checked");
        if (mWorkingMessage == null) {
            return false;
        }
        if (!mWorkingMessage.requiresMms()) {
            return false;
        }
        if (mWorkingMessage.hasSubject()) {
            return false;
        }
        if (mWorkingMessage.hasAttachedFiles()) {
            return false;
        }
        if (mWorkingMessage.hasAttachment()) {
            return false;
        }
        if ((mWorkingMessage.getState() & 1) > 0) {
            return false;
        }
        SlideshowModel ssm = mWorkingMessage.getSlideshow();
        if (ssm != null && ssm.size() != 1) {
            return false;
        } else if (ssm != null && ssm.get(0) != null && !ssm.get(0).hasText()) {
            return false;
        }
        int slotId = SubscriptionManager.from(MmsApp.getApplication())
                .getActiveSubscriptionInfo(mSelectedSubId).getSimSlotIndex();
        if (slotId != 0) {
            return false;
        }
        if (!TelephonyManager.getDefault().isNetworkRoaming(mSelectedSubId)) {
            return false;
        }
        if (ssm == null && mWorkingMessage.hasText()) {
            return true;
        }
        return true;
    }

    /**
     * M: For Just not send length required mms with Slot one.<br>
     */
    public void confirmForChangeMmsToSms() {
        if (!isLengthRequiredMms()) {
            MmsLog.d(TAG, "isLengthRequiredMms Checked false");
            mMmsComposePlugin.setConfirmMmsToSms(false);
            confirmSendMessageIfNeeded();
            return;
        }
        MmsLog.d(TAG, "isLengthRequiredMms Checked true");
        new AlertDialog.Builder(this)
            .setTitle("")
            .setIconAttribute(android.R.attr.alertDialogIcon)
            .setMessage(mStringReplacementPlugin.getStrings(IStringReplacementExt.CHANGE_MMS_TO_SMS))
            .setPositiveButton(R.string.yes, new OnClickListener() {
                @Override
                public void onClick(DialogInterface arg0, int arg1) {
                    mMmsComposePlugin.setConfirmMmsToSms(false);
                    mWorkingMessage.setLengthRequiresMms(false, false);
                    confirmSendMessageIfNeeded();
                }
            }).setNegativeButton(R.string.no, new OnClickListener() {
                @Override
                public void onClick(DialogInterface arg0, int arg1) {
                    mMmsComposePlugin.setConfirmMmsToSms(true);
                    mWorkingMessage.setLengthRequiresMms(true, false);
                    updateSendButtonState();
                }
            }).setCancelable(true).show();
    }
    private void startFileManager() {
        MmsLog.e(TAG, "startFileManager()");
        Intent intent = new Intent(CHOICE_FILEMANAGER_ACTION);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        if (FeatureOption.MTK_DRM_APP) {
            intent.putExtra(OmaDrmStore.DrmExtra.EXTRA_DRM_LEVEL, OmaDrmStore.DrmExtra.LEVEL_SD);
        }
        startActivityForResult(intent, this.REQUEST_CODE_IPMSG_SHARE_FILE);
    }

    private boolean sendFileViaJoyn(Intent data) {
        String fileName = Uri.decode(data.getDataString());
        if (fileName != null && fileName.startsWith(FILE_SCHEMA)) {
            String fileFullPath = fileName.substring(FILE_SCHEMA.length(), fileName.length());
            if (fileFullPath != null) {
                Uri contentUri = null;
                Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        new String[] {MediaStore.MediaColumns._ID, Images.Media.MIME_TYPE}, MediaStore.MediaColumns.DATA + "=?",
                        new String[] {fileFullPath}, null);
                if (c != null && c.getCount() != 0 && c.moveToFirst()) {
                    contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getString(0));
                } else {
                    if (c != null) {
                        c.close();
                    }
                    c = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            new String[] {MediaStore.MediaColumns._ID, Audio.Media.MIME_TYPE}, MediaStore.MediaColumns.DATA + "=?",
                            new String[] {fileFullPath}, null);
                    if (c != null && c.getCount() != 0 && c.moveToFirst()) {
                        contentUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getString(0));
                    } else {
                        if (c != null) {
                            c.close();
                        }
                        c = getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                new String[] {MediaStore.MediaColumns._ID, Video.Media.MIME_TYPE}, MediaStore.MediaColumns.DATA + "=?",
                                new String[] {fileFullPath}, null);
                        if (c != null && c.getCount() != 0 && c.moveToFirst()) {
                            contentUri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getString(0));
                        }
                    }
                }
                try {
                    if (c != null && c.getCount() != 0 && c.moveToFirst()) {
                        MmsLog.i(TAG, "Get id in MediaStore:" + c.getString(0));
                        MmsLog.i(TAG, "Get content type in MediaStore:" + c.getString(1));
                        MmsLog.i(TAG, "Get uri in MediaStore:" + contentUri);
                        String contentType = c.getString(1);
                        data.setData(contentUri);
                        if (contentType.startsWith("image/")) {
                            onIpMsgActivityResult(this, REQUEST_CODE_IPMSG_CHOOSE_PHOTO, RESULT_OK, data);
                        } else if (contentType.startsWith("video/")) {
                            onIpMsgActivityResult(this, REQUEST_CODE_IPMSG_CHOOSE_VIDEO, RESULT_OK, data);
                        } else if (contentType.startsWith("audio/")) {
                            onIpMsgActivityResult(this, REQUEST_CODE_IPMSG_CHOOSE_AUDIO, RESULT_OK, data);
                        }
                        return true;
                    } else {
                        sendUnknownMsg(fileFullPath);
                        MmsLog.e(TAG, "MediaStore:" + MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + " has not this file");
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
            }
        }
        return false;
    }

    /// M: Whether attachment is being added or not. @{
    private boolean mWaitingAttachment = false;
    public boolean isWaitingAttachment() {
        return mWaitingAttachment;
    }

    public void setWaitingAttachment(boolean waitingAttachment) {
        mWaitingAttachment = waitingAttachment;
    }
    /// @}

    /// M: add for CMCC feature fix ALPS01317511 @{
    public boolean isWorthSaving() {
        boolean ret = false;
        if (mWorkingMessage != null) {
            ret = mWorkingMessage.isWorthSaving();
        }
        MmsLog.d(TAG, "isWorthSaving: ret = " + ret);
        return ret;
    }

    public boolean hasValidRecipient() {
        boolean ret = true;
        if (isRecipientsEditorVisible() && !mRecipientsEditor.hasValidRecipient(mWorkingMessage.requiresMms())) {
            ret = false;
        }
        MmsLog.d(TAG, "hasValidRecipient: ret = " + ret);
        return ret;
    }

    private boolean handleNotificationIntent(final Intent intent) {
        boolean ret = false;
        if (MmsConfig.isSupportMessagingNotificationProxy() && intent.getBooleanExtra("notification", false)) {
            if (mHadToSlideShowEditor) {
                if (mTempMmsUri != null) {
                    WorkingMessage newMessage = WorkingMessage.load(ComposeMessageActivity.this, mTempMmsUri);
                    if (newMessage != null) {
                        if (newMessage.hasMediaAttachments()) {
                            newMessage.removeAllFileAttaches();
                        }
                        newMessage.setSubject(mWorkingMessage.getSubject(), false);
                        mWorkingMessage = newMessage;
                        updateThreadIdIfRunning();
                        drawTopPanel(isSubjectEditorVisible());
                        updateSendButtonState();
                        invalidateOptionsMenu();
                    }
                }
            }
            if (mWorkingMessage.isWorthSaving() && isRecipientsEditorVisible() &&
                    !mRecipientsEditor.hasValidRecipient(mWorkingMessage.requiresMms())) {
                ret = true;
                MessageUtils.showDiscardDraftConfirmDialog(this, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mWorkingMessage.discard();
                        int thread_count = intent.getIntExtra("thread_count", 1);
                        int messageCount = intent.getIntExtra("message_count", 1);
                        if (thread_count > 1 || messageCount > 1) {
                            mHomeBox = FolderViewList.OPTION_INBOX;
                            goToConversationList();
                        } else {
                            processNewIntent(intent);
                            updateTitle(mConversation.getRecipients());
                        }
                    }
                });
            }
        }
        MmsLog.d(TAG, "handleNotificationIntent, ret = " + ret);
        return ret;
    }
    /// @}

    /// M: add for mms cc feature, OP09 requested. @{
    private static final int RECIPIENTS_LIMIT_CC = MmsConfig.getMmsRecipientLimit();
    private int mLastRecipientCcCount = 0;
    private final MTKRecipientEditTextView.ChipWatcher mCCChipWatcher =
        new MTKRecipientEditTextView.ChipWatcher() {
        public void onChipChanged(ArrayList<RecipientEntry> allChips,
                ArrayList<String> changedChipAddresses, String lastString) {
            if (mChipViewMenu != null) {
                mChipViewMenu.close();
                mChipViewMenu = null;
            }
            if (!isRecipientsCcEditorVisible()) {
                Log.w(TAG, "ChipWatcher: onChipChanged called with invisible mRecipientsCcEditor");
                return;
            }

            mRecipientsCcEditor.parseRecipientsFromChipWatcher(allChips,
                    changedChipAddresses, lastString, RECIPIENTS_LIMIT_CC);
            List<String> numbers = mRecipientsCcEditor.getNumbersFromChipWatcher();

            int recipientCount = numbers.size();
            if (recipientCount == 0) {
                mRecipientsCcEditor.setHint(getString(R.string.to_hint_ipmsg));
            }
            boolean tooMany = recipientCount > RECIPIENTS_LIMIT_CC;
            if (recipientCount != mLastRecipientCcCount) {
                // Don't warn the user on every character they type when they're over the limit,
                // only when the actual # of recipients changes.
                mLastRecipientCcCount = recipientCount;
                if (tooMany) {
                    String tooManyMsg = getString(R.string.too_many_recipients, recipientCount,
                            RECIPIENTS_LIMIT_CC);
                    Toast.makeText(ComposeMessageActivity.this,
                            tooManyMsg, Toast.LENGTH_LONG).show();
                }
            }

            if (MmsConfig.isSupportSendMmsWithCc()) {
                mWorkingMessage.setMmsCc(mRecipientsCcEditor.constructContactsFromInput(false),
                        true);
                updateCounter(mTextEditor.getText(), 0, 0, 0);
            }
        }
    };

    private void showRecipientsCcEditor(boolean show) {
        if (mRecipientsCcEditor == null) {
            if (!show) {
                return;
            }
            mRecipientsCcEditor = (RecipientsEditor) findViewById(R.id.recipients_cc_editor);
        }

        if (!show) {
            mRecipientsCcEditor.setOnKeyListener(null);
            mRecipientsCcEditor.removeChipChangedListener(mCCChipWatcher);
            mRecipientsCcEditor.setVisibility(View.GONE);
            hideOrShowTopPanel();
            MmsLog.d(TAG, "hide cc editor.");
            return;
        }

        // M: indicate contain email address or not in RecipientsEditor candidates. @{
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ComposeMessageActivity.this);
        boolean showEmailAddress = prefs.getBoolean(GeneralPreferenceActivity.SHOW_EMAIL_ADDRESS, true);
        MmsLog.d(TAG, "showRecipientsCcEditor(), showEmailAddress = " + showEmailAddress);
        if (mRecipientsCcEditor.getAdapter() == null) {
            ChipsRecipientAdapter chipsAdapter = new ChipsRecipientAdapter(this);
            chipsAdapter.setShowEmailAddress(showEmailAddress);
            mRecipientsCcEditor.setAdapter(chipsAdapter);
        } else {
            ((ChipsRecipientAdapter) mRecipientsCcEditor.getAdapter()).setShowEmailAddress(showEmailAddress);
        }
        // @}

        // Must grab the recipients before the view is made visible because getRecipients()
        // returns empty recipients when the editor is visible.
        ContactList recipients = mWorkingMessage.getMmsCc();
        while (!recipients.isEmpty() && recipients.size() > RECIPIENTS_LIMIT_CC) {
            recipients.remove(RECIPIENTS_LIMIT_CC);
        }
        /// @}
        mRecipientsCcEditor.populate(new ContactList());
        mRecipientsCcEditor.populate(recipients);

        setCCRecipientEditorStatus(mIsSmsEnabled);

        mRecipientsCcEditor.setOnCreateContextMenuListener(mRecipientsMenuCreateListener);
        mRecipientsCcEditor.addChipChangedListener(mCCChipWatcher);

        mRecipientsCcEditor.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                showSharePanel(false);
                return false;
            }
        });

        mRecipientsCcEditor.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }

                // When the subject editor is empty, press "DEL" to hide the input field.
                if ((keyCode == KeyEvent.KEYCODE_DEL) && (mRecipientsCcEditor.length() == 0)) {
                    showRecipientsCcEditor(false);
                    mWorkingMessage.setMmsCc(null, true);
                    return true;
                }
                return false;
            }
        });

        mRecipientsCcEditor.setVisibility(View.VISIBLE);
        hideOrShowTopPanel();
    }

    private boolean isRecipientsCcEditorVisible() {
        return (mRecipientsCcEditor != null) && (mRecipientsCcEditor.getVisibility() == View.VISIBLE);
    }

    private boolean mPickRecipientsCc = false;
    private boolean isPickRecipientsCc() {
        return ((mRecipientsCcEditor != null) && mRecipientsCcEditor.hasFocus()) || mPickRecipientsCc;
    }

    private boolean isRecipientsCcTooMany() {
        return (mRecipientsCcEditor != null) && mRecipientsCcEditor.getRecipientCount() > RECIPIENTS_LIMIT_CC;
    }
    /// @}

    private void stopDraftService() {
        if (FeatureOption.MTK_GMO_ROM_OPTIMIZE) {
            MmsLog.d(TAG, "stop Draft Service");
            stopService(new Intent(this, DraftService.class));
        }
    }

    /**
     * Set CC Editor's Status.
     * @param enable true: enable; false: disable.
     */
    private void setCCRecipientEditorStatus(boolean enable) {
        if (mRecipientsCcEditor != null && isRecipientsCcEditorVisible()) {
            mRecipientsCcEditor.setEnabled(enable);
            mRecipientsCcEditor.setFocusableInTouchMode(enable);
            mRecipientsCcEditor.setIsTouchable(enable);
        }
    }

    /*
     * M: ALPS01956117. If image is compressing after onStop,
     * async save draft operation should be delayed. 
     */
    private boolean mNeedSaveDraftAfterStop = false;

    ///M: WFC: Show pop-up @ {
    private boolean showWfcSendButtonPopUp() {
        //MmsLog.d(TAG, "default voice sub id:"+SubscriptionManager.getDefaultVoiceSubId());
        //ImsManager mImsManager = ImsManager.getInstance(this, SubscriptionManager.getDefaultVoiceSubId());
        //MmsLog.d(TAG, "wfc status code:"+mImsManager.getWfcStatusCode());
        if (MessagingNotification.doShowWfcPopup(getApplicationContext())) {
            new AlertDialog.Builder(this)
                .setCancelable(false)
                //.setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.wfc_no_cellular_network)
                .setMessage(R.string.wfc_connect_to_available_wifi)
                .setPositiveButton(R.string.OK, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                    }
            }).show();
            return true;
        }
        return false;
    }
   /// @} 

   
	/*HQ_zhangjing 2015-07-24 modified for sms forward begin*/
    public String formatSmsBody(Context context, String smsBody, String nameAndNumber, int boxId) {
        Log.d(TAG, "Call formatSmsBody 1");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean smsForwardWithSender = prefs.getBoolean(SmsPreferenceActivity.SMS_FORWARD_WITH_SENDER, true);
        Log.d(TAG, "forwardMessage(): SMS Forward With Sender ?= " + smsForwardWithSender);
        if (smsForwardWithSender) {
              if (boxId == Mms.MESSAGE_BOX_INBOX) {
                  nameAndNumber = getString(R.string.forward_from) + nameAndNumber + ":\n";
                  nameAndNumber += smsBody;
              } else {
                  nameAndNumber = getString(R.string.forward_byself) + ":\n";
                  nameAndNumber += smsBody;
              }
              return nameAndNumber;
        }
        return smsBody;
    }

    public String formatSmsBody(Context context, String smsBody, String nameAndNumber, Cursor cursor) {
        Log.d(TAG, "Call formatSmsBody 2");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean smsForwardWithSender = prefs.getBoolean(SmsPreferenceActivity.SMS_FORWARD_WITH_SENDER, true);
        Log.d(TAG, "forwardMessage(): SMS Forward With Sender ?= " + smsForwardWithSender);

        if (smsForwardWithSender) {
            long mStatus = cursor.getLong(cursor.getColumnIndexOrThrow("status"));
            if (mStatus == SmsManager.STATUS_ON_ICC_READ
                            || mStatus == SmsManager.STATUS_ON_ICC_UNREAD) {
                   // it is a inbox sms
                   Log.d(TAG, "It is a inbox sms");
                   smsBody += "\n" + getString(R.string.forward_from);
                   smsBody += nameAndNumber;
            }
        }
        return smsBody;
    }
	/*HQ_zhangjing 2015-07-24 modified for sms forward end*/

	/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  begin */
	View.OnClickListener moreViewListener = new View.OnClickListener(){
			@Override
			public void onClick(View view) {
				switch( view.getId() ){
					case R.id.menu_more:
						showMoreMenuItem();
						break;
					default:
						break;
				}
			}
		};

	private class MenuViewClickListener implements DialogInterface.OnClickListener{
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch (mMenuitemResponseIds.get(which)){
			            case MENU_ADD_SUBJECT:
			                showSubjectEditor(true);
			                mWorkingMessage.setSubject("", true);
			                /// M: Code analyze 052, Show input keyboard.@{
			                mInputMethodManager.showSoftInput(getCurrentFocus(), InputMethodManager.SHOW_IMPLICIT);
			                /// @}
			                updateSendButtonState();
			                mSubjectTextEditor.requestFocus();
			                break;
			            case MENU_ADD_ATTACHMENT:
			                // Launch the add-attachment list dialog
			                /// M: Code analyze 051, Hide input keyboard.@{
			                hideInputMethod();
			                /// M: Vcard and slides can be in same screen
			                // add for attachment enhance
			                if (MmsConfig.isSupportAttachEnhance()) {
			                    // OP01
			                    showAddAttachmentDialog(true);
			                    MmsLog.d(TAG, "Attach: showAddAttachmentDialog(true)");
			                } else {
			                    showAddAttachmentDialog(!mWorkingMessage.hasAttachedFiles());
			                    MmsLog.d(TAG, "Attach: showAddAttachmentDialog(!hasAttachedFiles)");
			                }
			                break;
			            /// M: Code analyze 014, Add quick text. @{
			            case MENU_ADD_QUICK_TEXT:
			                showQuickTextDialog();
			                break;
			            /// @}
			            /// M: Code analyze 015, Add text vcard. @{
			            case MENU_ADD_TEXT_VCARD: {
			                Intent intent = new Intent("android.intent.action.contacts.list.PICKMULTICONTACTS");
			                intent.setType(Contacts.CONTENT_TYPE);
			                startActivityForResult(intent, REQUEST_CODE_TEXT_VCARD);
			                break;
			            }
			            /// @}
			            case MENU_DISCARD:
			                /// M: fix bug for ConversationList select all performance ,update selected threads array.@{
			                ConversationListAdapter.removeSelectedState(mSelectedThreadId);
			                /// @}
			                /// M: Fix bug of inputmethod not disappear
			                hideInputMethod();
			                mWorkingMessage.discard();
			                finish();
			                break;
			            case MENU_SEND:
			                if (isPreparedForSending()) {
			                    /// M: add for ip message, unread divider
			                    mShowUnreadDivider = false;
			                    /// M: Code analyze 028, Before sending message,check the recipients count
			                    /// and add sub card selection dialog if multi sub cards exist.@{
			                    updateSendButtonState(false);
			                    checkRecipientsCount();
			                    mSendButtonCanResponse = true;
			                    /// @}
			                }
			                break;
			            case MENU_SEARCH:
			                onSearchRequested();
			                break;
			            case MENU_DELETE_THREAD:
			                /// M: Code analyze 012, add for multi-delete @{
			                Intent it = new Intent(ComposeMessageActivity.this, MultiDeleteActivity.class);
			                it.putExtra("thread_id", mConversation.getThreadId());
			                startActivityForResult(it, REQUEST_CODE_FOR_MULTIDELETE);
			                /// @}
			                break;

			            case android.R.id.home:
			                mIsCheckObsolete = true;
			                mConversation.setDiscardThreadId(mConversation.getThreadId());
			            case MENU_CONVERSATION_LIST:
			                exitComposeMessageActivity(new Runnable() {
			                    @Override
			                    public void run() {
			                        goToConversationList();
			                    }
			                });
			                break;
			            case MENU_CALL_RECIPIENT:
			                dialRecipient(false);
			                break;
			            /// M: Code analyze 061, Add video call menu.
			            case MENU_CALL_RECIPIENT_BY_VT:
			                dialRecipient(true);
			                break;
			            /// @}
			            /// M: google jb.mr1 patch, group mms
			            case MENU_GROUP_PARTICIPANTS: {
			                Intent intent = new Intent(ComposeMessageActivity.this, RecipientListActivity.class);
			                intent.putExtra(THREAD_ID, mConversation.getThreadId());
			                startActivityForResult(intent, REQUEST_CODE_GROUP_PARTICIPANTS);
			                break;
			            }
			            case MENU_VIEW_CONTACT: {
			                // View the contact for the first (and only) recipient.
			                ContactList list = getRecipients();
			                if (list.size() == 1 && list.get(0).existsInDatabase()) {
			                    Uri contactUri = list.get(0).getUri();
			                    Intent intent = new Intent(Intent.ACTION_VIEW, contactUri);
			                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
			                    startActivity(intent);
			                }
			                break;
			            }
			            case MENU_ADD_ADDRESS_TO_CONTACTS:
			                //mAddContactIntent = item.getIntent();
			                //startActivityForResult(mAddContactIntent, REQUEST_CODE_ADD_CONTACT);
			                break;
			            case MENU_PREFERENCES: {
			                Intent settingIntent = null;
			                if (MmsConfig.isSupportTabSetting()) {
			                    settingIntent = new Intent(ComposeMessageActivity.this, MessageTabSettingActivity.class);
			                } else {
			                    settingIntent = new Intent(ComposeMessageActivity.this, SettingListActivity.class);
			                }
			                startActivityIfNeeded(settingIntent, -1);
			                break;
			            }
			            case MENU_DEBUG_DUMP:
			                mWorkingMessage.dump();
			                Conversation.dump();
			                //LogTag.dumpInternalTables(this);
			                break;
			            case MENU_ADD_TO_CONTACTS: {
			                mAddContactIntent = ConversationList.createAddContactIntent(getRecipients().get(0).getNumber());
			                startActivityForResult(mAddContactIntent, REQUEST_CODE_ADD_CONTACT);
			                break;
			            }
			            /// M: show contact detail or create new contact. @{
			            case MENU_SHOW_CONTACT:
                                        // modified by zhenghao  HQ01445978 2015-10-26
                                        hideInputMethod();
                                        Contact contact = getRecipients().get(0);
                                        if (contact !=null ){
                                            Uri contact_uri = contact.getUri();
                                        
                                            if (contact_uri != null){
                                                Intent quick_intent = QuickContact.composeQuickContactsIntent(
					                              ComposeMessageActivity.this, (Rect) null, contact_uri,
					                              QuickContactActivity.MODE_FULLY_EXPANDED, null);
                                                startActivity(quick_intent);
                                            }
                                        }
                                        break;
                                        //modified by zhenghao end !
			            case MENU_CREATE_CONTACT:
			                hideInputMethod();
			                mQuickContact.onClick(mActionBarCustomView);
			                break;
			            /// @}
			            case MENU_ADD_MMS_CC:
			                mWorkingMessage.setMmsCc(null, true);
			                showRecipientsCcEditor(true);
			                updateSendButtonState();
			                mInputMethodManager.showSoftInput(getCurrentFocus(),
			                    InputMethodManager.SHOW_IMPLICIT);
			                mRecipientsCcEditor.requestFocus();
			                break;
						case MENU_INVITE_FRIENDS_TO_CHAT:
									if (mCurrentChatMode == IpMessageConsts.ChatMode.XMS) {
									if (IpMessageUtils.getServiceManager(ComposeMessageActivity.this).isFeatureSupported(IpMessageConsts.FeatureId.EXTEND_GROUP_CHAT)) {
										int curCount = mConversation.getRecipients().size();
										int pickCount = RECIPIENTS_LIMIT_FOR_SMS - curCount;
										addContacts(pickCount, REQUEST_CODE_IP_MSG_PICK_CONTACTS);
										break;
									}
								}
								Intent intent = new Intent(RemoteActivities.CONTACT);
								intent.putExtra(RemoteActivities.KEY_REQUEST_CODE, REQUEST_CODE_INVITE_FRIENDS_TO_CHAT);
								intent.putExtra(RemoteActivities.KEY_TYPE, SelectContactType.IP_MESSAGE_USER);
								intent.putExtra(RemoteActivities.KEY_ARRAY, mConversation.getRecipients().getNumbers());
								IpMessageUtils.startRemoteActivityForResult(ComposeMessageActivity.this, intent);
								 break;
							case MENU_INVITE_FRIENDS_TO_IPMSG:
								String contentText = mTextEditor.getText().toString();
								if (TextUtils.isEmpty(contentText)) {
									mTextEditor.setText(IpMessageUtils.getResourceManager(ComposeMessageActivity.this)
																	.getSingleString(IpMessageConsts.string.ipmsg_invite_friends_content));
								} else {
									IpMessageUtils.showInviteIpMsgConfirmDialog(ComposeMessageActivity.this, new InviteFriendsToIpMsgListener());
								}
								break;
							case MENU_SEND_BY_SMS_IN_JOYN_MODE:
								mSendSmsInJoynMode = true;
								ComposeMessageActivity.this.onClick(mSendButtonSms);
								break;
							case MENU_SEND_BY_JOYN_IN_SMS_MODE:
								mSendJoynMsgInSmsMode = true;
								ComposeMessageActivity.this.onClick(mSendButtonIpMessage);
								break;
							case MENU_SELECT_MESSAGE:
								Intent intentSelectMessage = new Intent(ComposeMessageActivity.this, MultiDeleteActivity.class);
								intentSelectMessage.putExtra("thread_id", mConversation.getThreadId());
								startActivityForResult(intentSelectMessage, REQUEST_CODE_FOR_MULTIDELETE);
								mIsStartMultiDeleteActivity = true;
								break;
							case MENU_MARK_AS_SPAM:
								final Conversation conversationForMarkSpam = mConversation;
								final boolean isSpamFromMark = mConversation.isSpam();
								mConversation.setSpam(true);
								new Thread(new Runnable() {
									@Override
									public void run() {
										String numbers = TextUtils.join(",", conversationForMarkSpam.getRecipients().getNumbers());
										int contactId = IpMessageUtils.getContactManager(ComposeMessageActivity.this)
																		.getContactIdByNumber(numbers);
										int[] contactIds = {contactId};
										if (!IpMessageUtils.getContactManager(ComposeMessageActivity.this)
												.addContactToSpamList(contactIds)) {
											MmsLog.w(IPMSG_TAG, "onIpMsgOptionsItemSelected(): Mark as spam failed!");
											mConversation.setSpam(isSpamFromMark);
										///M: Update thread mute icon {@
										} else {
											asyncUpdateThreadMuteIcon();
										}
										///@}
									}
								}).start();
								break;
							case MENU_REMOVE_SPAM:
								final Conversation conversationForRemoveSpam = mConversation;
								final boolean isSpamFromRemove = mConversation.isSpam();
								mConversation.setSpam(false);
								new Thread(new Runnable() {
									@Override
									public void run() {
										String numbers = TextUtils.join(",", conversationForRemoveSpam.getRecipients().getNumbers());
										int contactId = IpMessageUtils.getContactManager(ComposeMessageActivity.this)
											.getContactIdByNumber(numbers);
										int[] contactIds = {contactId};
										if (!IpMessageUtils.getContactManager(ComposeMessageActivity.this)
												.deleteContactFromSpamList(contactIds)) {
											MmsLog.w(IPMSG_TAG, "onIpMsgOptionsItemSelected(): Remove spam failed!");
											mConversation.setSpam(isSpamFromRemove);
										///M: Update thread mute icon {@
										} else {
											asyncUpdateThreadMuteIcon();
										}
										///@}
									}
								}).start();
								break;
							case MENU_VIEW_ALL_MEDIA:
								Intent intentViewAllMedia = new Intent(RemoteActivities.ALL_MEDIA);
								intentViewAllMedia.putExtra(RemoteActivities.KEY_THREAD_ID, mConversation.getThreadId());
								IpMessageUtils.startRemoteActivity(ComposeMessageActivity.this, intentViewAllMedia);
								break;
							case MENU_CHAT_SETTING:
								long chatThreadId = -1L;
								if (!isRecipientsEditorVisible()) {
									chatThreadId = mConversation.getThreadId();
								}
								if (chatThreadId != -1L) {
									Intent i = new Intent(ComposeMessageActivity.this, ChatPreferenceActivity.class);
									i.putExtra("chatThreadId", chatThreadId);
									startActivity(i);
								}
								break;
							/// M: Add Jump to another chat in converaged inbox mode. @{
							case MENU_JUMP_TO_JOYN:
								if (IpMessageUtils.getServiceManager(ComposeMessageActivity.this).getIntegrationMode() ==
									IpMessageConsts.IntegrationMode.FULLY_INTEGRATED) {
										mIsIpServiceEnabled = true;
									mCurrentChatMode = IpMessageConsts.ChatMode.JOYN;
									showSmsOrMmsSendButton(false);
									invalidateOptionsMenu();
								} else if (IpMessageUtils.getServiceManager(ComposeMessageActivity.this).getIntegrationMode() ==
									IpMessageConsts.IntegrationMode.CONVERGED_INBOX) {
									jumpToJoynChat(true, null);
								}
								break;
							case MENU_JUMP_TO_XMS:
								if (IpMessageUtils.getServiceManager(ComposeMessageActivity.this).getIntegrationMode() ==
									IpMessageConsts.IntegrationMode.FULLY_INTEGRATED) {
										mIsIpServiceEnabled = false;
									mCurrentChatMode = IpMessageConsts.ChatMode.XMS;
									showSmsOrMmsSendButton(false);
									invalidateOptionsMenu();
								} else if (IpMessageUtils.getServiceManager(ComposeMessageActivity.this).getIntegrationMode() ==
									IpMessageConsts.IntegrationMode.CONVERGED_INBOX) {
									jumpToJoynChat(false, null);
								}
								break;
							/// @}
							
							/// M: Add export chat fuature. @{
							case MENU_EXPORT_CHAT:
								IpMessageUtils.getChatManager(ComposeMessageActivity.this).exportChat(mConversation.getThreadId());
								break;
 /*HQ_sunli 20150916 HQ01359292 begin*/ 
              case MENU_ADD_TO_BLACKLIST: {
                	   try
                    {    
                     if (null != getRecipients() && getRecipients().size() >= 1){
                    	Bundle localBundle = new Bundle();
                    	localBundle.putString("BLOCK_PHONENUMBER", getRecipients().get(0).getNumber());
                    	localBundle.putString("BLOCK_CONTACTNAME", getRecipients().get(0).getName());
                    	int isBlock = -1;
                    	if(null != hisStub) {
                    		isBlock=hisStub.addPhoneNumberBlockItem(localBundle, 0, 0);
                    	}

                        if(isBlock==0){
                        Toast.makeText(ComposeMessageActivity.this,getString(R.string.add_to_black_list),Toast.LENGTH_SHORT).show(); 
                      }
                    }
                    }
                    catch (RemoteException paramContext)
                    {
                    	paramContext.printStackTrace();
                    }

                        break;
                }
                case MENU_REMOVE_FROM_BLACKLIST:{

                	 try
                 {
                 if (null != getRecipients() && getRecipients().size() >= 1){
                  	Bundle localBundle = new Bundle();
                   localBundle.putString("BLOCK_PHONENUMBER", getRecipients().get(0).getNumber());
                   int isRemove = -1;
                   if(null != hisStub) {
                	   isRemove=hisStub.removePhoneNumberBlockItem(localBundle, 0, 0);
                   }
                   if(isRemove==0){
                   Toast.makeText(ComposeMessageActivity.this,getString(R.string.remove_from_black_list), Toast.LENGTH_SHORT).show();             
                     }
                  }
                  }
                   catch (Exception paramContext)
                  {
                    paramContext.printStackTrace();
                  }
                          break;
                }
 /*HQ_sunli 20150916 HQ01359292 end*/ 
			            default:
			                MmsLog.d(TAG, "unkown option.and mMenuitemResponseIds.get(which) = " + mMenuitemResponseIds.get(which));
			                break;
			        }
			mMenuitemResponseIds.clear();
			mMenuItemStringIds.clear();
			
		}
	}

	private MenuViewClickListener menuViewListener = new MenuViewClickListener();
	private void showMoreMenuItem(){
		Builder builder = new AlertDialog.Builder(this);
		if( mMenuItemStringIds.isEmpty() || mMenuitemResponseIds.isEmpty() ){
			constructMenuStr();
		}
		if( mMenuItemStringIds.isEmpty() || mMenuitemResponseIds.isEmpty()){
			Log.d(TAG,"zhangjing:mMenuItemStringIds is empty and return");
			return;
		}
		CharSequence[] menuStrs = new CharSequence[ mMenuItemStringIds.size()];
		mMenuItemStringIds.toArray(menuStrs);
		builder.setItems(menuStrs,menuViewListener);
		builder.create().show();
	}

	private void constructMenuStr(){
        if (!isRecipientsEditorVisible()){
            MmsLog.d(TAG, "onPrepareOptionsMenu recipient editor is not visible!");
            if (getRecipients().size() == 1) {
                Contact contact = getRecipients().get(0);
                contact.reload(true);
                if (contact.existsInDatabase()) {
                    MmsLog.d(TAG, "onPrepareOptionsMenu contact is in database: " + contact.getUri());
					addMenuIdAndStr(R.string.menu_view_contact,MENU_SHOW_CONTACT);
                    mQuickContact.assignContactUri(contact.getUri());
                } else if (MessageUtils.canAddToContacts(contact)) {
					addMenuIdAndStr(R.string.menu_add_to_contacts,MENU_CREATE_CONTACT);
                    String number = contact.getNumber();
                    // add for joyn converged inbox mode
                    if (number.startsWith(IpMessageConsts.JOYN_START)) {
                        number = number.substring(4);
                    }
                    if (Mms.isEmailAddress(number)) {
                        mQuickContact.assignContactFromEmail(number, true);
                    } else {
                        mQuickContact.assignContactFromPhone(number, true);
                    }
                }
            }
			
        }

		if (null != getRecipients() && getRecipients().size() >= 1) {
			String number = getRecipients().get(0).getNumber();
			
		   if (checkNumberBlocked( number )==true) {
				addMenuIdAndStr(R.string.menu_remove_from_black_list,MENU_REMOVE_FROM_BLACKLIST);
			}else{
				addMenuIdAndStr(R.string.menu_add_to_black_list,MENU_ADD_TO_BLACKLIST);
			}
		}

		TelephonyManager telephony = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		if (telephony != null && telephony.isVoiceCapable() && isRecipientCallable()) {
			addMenuIdAndStr(R.string.menu_call,MENU_CALL_RECIPIENT);
		/// @}
		}

		 if (!isRecipientsEditorVisible() && mIsSmsEnabled ) {
			addMenuIdAndStr(R.string.select_message,MENU_SELECT_MESSAGE);
        }		
	}

	/*HQ_zhangjing 2015-08-10 modified for compose actionBar background and moremenu style and display  end */

}
