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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import android.provider.ChildMode;//add by lihaizhou for ChildMode at 2015-08-04
import com.android.internal.telephony.PhoneConstants;
import com.android.mms.LogTag;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;
import com.android.mms.PDebug;
import com.android.mms.transaction.MessagingNotification;
import com.android.mms.transaction.SmsRejectedReceiver;
import com.android.mms.ui.CustomMenu.DropDownMenu;
import com.android.mms.util.DraftCache;
import com.android.mms.util.Recycler;
import com.android.mms.util.StatusBarSelectorCreator;
import com.android.mms.widget.MmsWidgetProvider;
import com.google.android.mms.pdu.PduHeaders;

import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.database.sqlite.SqliteWrapper;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.Settings;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Threads;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnKeyListener;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;





/// M:
import com.android.mms.MmsConfig;
import com.android.mms.MmsApp;
import com.android.mms.draft.DraftManager;
import com.android.mms.transaction.CBMessagingNotification;
import com.android.mms.transaction.SmsReceiverService;
import com.android.mms.transaction.WapPushMessagingNotification;
import com.android.mms.transaction.MmsSystemEventReceiver.OnSubInforChangedListener;
import com.android.mms.util.FeatureOption;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.res.Resources;
import android.provider.Telephony;
import android.provider.Telephony.Sms;
import android.telephony.SmsManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.net.Uri;
import android.text.TextUtils;
import android.app.StatusBarManager;

import com.mediatek.mwi.MwiListActivity;
import com.android.mms.MmsPluginManager;
import com.mediatek.mms.ext.IMmsConversationExt;
import com.mediatek.mms.ext.IMmsConversationHost;
import com.mediatek.mms.ext.IMmsDialogNotifyExt;
import com.mediatek.mms.ext.IAppGuideExt;
import com.android.mms.util.MmsLog;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.ipmsg.ui.ConversationEmptyView;
import com.mediatek.nmsg.util.IpMessageNmsgUtil;
import com.mediatek.ipmsg.util.IpMessageUtils;

import java.io.File;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo.State;
import android.os.Environment;
import android.provider.Telephony.Sms.Conversations;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;

import com.mediatek.mms.ipmessage.INotificationsListener;
import com.mediatek.mms.ipmessage.IpMessageConsts;
import com.mediatek.mms.ipmessage.IpMessageConsts.RemoteActivities;
import com.mediatek.mms.ipmessage.IpMessageConsts.SelectContactType;
import com.android.internal.telephony.TelephonyIntents;
/// M: add for ipmessage

import com.android.mms.util.StatusBarSelectorReceiver;
/*HQ_zhangjing add for al812 mms ui begin*/
import android.view.inputmethod.InputMethodManager;

/**
 * This activity provides a list view of existing conversations.
 */
public class ConversationList extends ListActivity implements DraftCache.OnDraftChangedListener,
        /// M:add interface
        OnSubInforChangedListener, IMmsConversationHost, INotificationsListener {
    private static final String TAG = "ConversationList";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = DEBUG;

    private static final int THREAD_LIST_QUERY_TOKEN       = 1701;
    private static final int UNREAD_THREADS_QUERY_TOKEN    = 1702;
    public static final int DELETE_CONVERSATION_TOKEN      = 1801;
    public static final int HAVE_LOCKED_MESSAGES_TOKEN     = 1802;
    public static final int DELETE_OBSOLETE_THREADS_TOKEN  = 1803;

    // IDs of the context menu items for the list of conversations.
    public static final int MENU_DELETE               = 0;
    public static final int MENU_VIEW                 = 1;
    public static final int MENU_VIEW_CONTACT         = 2;
    public static final int MENU_ADD_TO_CONTACTS      = 3;
    private ThreadListQueryHandler mQueryHandler;
    private ConversationListAdapter mListAdapter;
    private SharedPreferences mPrefs;
    private Handler mHandler;
    private boolean mNeedToMarkAsSeen;
    private TextView mUnreadConvCount;
    private MenuItem mSearchItem;
    /// M: fix bug ALPS00374917, cancel sim_sms menu when haven't sim card
    private MenuItem mSimSmsItem;
    private SearchView mSearchView;
    private View mSmsPromoBannerView;

    /// M: add for OP09 advanced search @{
    private AdvancedSearchView mAdvancedSearchView;
    private ImageButton mImageSearchBtn;
    /// @}

    /// Google JB MR1.1 patch. conversation list can restore scroll position
    private int mSavedFirstVisiblePosition = AdapterView.INVALID_POSITION;
    private int mSavedFirstItemOffset;
	
	/*HQ_zhangjing add for al812 mms ui begin*/
    private static int mDeleteCounter = 0;
    private static boolean isCheckBox = false;
	private boolean mMultiChoiceMode = false;
	/*HQ_zhangjing add for al812 mms ui end*/
	
    // keys for extras and icicles
    private final static String LAST_LIST_POS = "last_list_pos";
    private final static String LAST_LIST_OFFSET = "last_list_offset";

    private static final String CHECKED_MESSAGE_LIMITS = "checked_message_limits";

    // Whether or not we are currently enabled for SMS. This field is updated in onResume to make
    // sure we notice if the user has changed the default SMS app.
    private boolean mIsSmsEnabled;
    private Toast mComposeDisabledToast;

    /// M: new members
    private static final String CONV_TAG = "Mms/convList";
    /// M: Code analyze 002, For new feature ALPS00041233, msim enhancment check in . @{
    private StatusBarManager mStatusBarManager;
    /// @}
    /// M: Code analyze 001, For new feature ALPS00131956, wappush: add new params . @{
    private int mType;
    private static final String WP_TAG = "Mms/WapPush";
    /// @}
    /// M: Code analyze 004, For bug ALPS00247476, ensure the scroll smooth . @{
    private static final int CHANGE_SCROLL_LISTENER_MIN_CURSOR_COUNT = 100;
    /// @}
    /// M: Code analyze 004, For bug ALPS00247476, ensure the scroll smooth . @{
    private MyScrollListener mScrollListener =
                    new MyScrollListener(CHANGE_SCROLL_LISTENER_MIN_CURSOR_COUNT, "ConversationList_Scroll_Tread");
    /// @}

    /// M: Code analyze 007, For bug ALPS00242955, If adapter data is valid . @{
    private boolean mDataValid;
    /// @}
    /// M: Code analyze 008, For bug ALPS00250948, disable search in multi-select status . @{
    private boolean mDisableSearchFalg = false;
    /// M: Code analyze 005, For new feature ALPS00247476, add selectAll/unSelectAll . @{
    private ModeCallback mActionModeListener = new ModeCallback();
    private ActionMode mActionMode;
    /// @}
    /// M: Optimize select all performance, save actionmode status and reduce select time. @{
    private static String ACTIONMODE = "actionMode";
    private static String NEED_RESTORE_ADAPTER_STATE = "needRestore";
    private boolean mIsNeedRestoreAdapterState = false;
    private static String SELECT_THREAD_IDS = "selectThreadIds";
    private long[] mListSelectedThreads;
    /// @}

    /// M: Code analyze 009, For bug ALPS00270910, Default SIM card icon shown in status bar
    /// is incorrect, need to get current sim information . @{
    private static Activity sActivity = null;
    /// @}

    /// M: Code analyze 009, For new feature, plugin . @{
    private IMmsConversationExt mMmsConversationPlugin = null;
    /// @}

    /** M: this is used to record the fontscale, if it is > 1.1[1.1 is big style]
     *  we need make the content view of conversationlistitem to be one line
     *  or it will overlapping with the above from view.
     */
    private float mFontScale;
    public static final float MAX_FONT_SCALE = 1.1f;

    /// M: add for ipmessage
    /// M: add for display unread thread count
    private static final int MAX_DISPLAY_UNREAD_COUNT = 99;
    private static final String DISPLAY_UNREAD_COUNT_CONTENT_FOR_ABOVE_99 = "99+";

    /// M: add for ipmessage {@
    private static final String IPMSG_TAG = "Mms/ipmsg/ConvList";

    /// M: add for drop down list
    public static final int OPTION_CONVERSATION_LIST_ALL         = 0;
    public static final int OPTION_CONVERSATION_LIST_GROUP_CHATS = 1;
    public static final int OPTION_CONVERSATION_LIST_SPAM        = 2;
    public static final int OPTION_CONVERSATION_LIST_JOYN        = 3;
    public static final int OPTION_CONVERSATION_LIST_XMS         = 4;

    private static final String DROP_DOWN_KEY_NAME   = "drop_down_menu_text";
    private ListView mListView; // we need this to update empty view.
    private View mEmptyViewDefault;
    private ConversationEmptyView mEmptyView;
    private ArrayAdapter<String> mDropdownAdapter;
    private AccountDropdownPopup mAccountDropdown;
    private Context mContext = null;
    public static int sConversationListOption = OPTION_CONVERSATION_LIST_ALL;

    private View mConversationSpinner;
	
    /*HQ_zhangjing add for al812 mms ui begin*/
    private View timeView;
    private MenuItem deleteMenuView;
    /*HQ_zhangjing add for al812 mms ui end*/
	
    private TextView mSpinnerTextView;
    private ProgressDialog mSaveChatHistory;
    boolean mIsSendEmail = false;

    private int mTypingCounter;
    private LinearLayout mNetworkStatusBar;
    private BroadcastReceiver mNetworkStateReceiver;

    private static final String SAVE_HISTORY_MIMETYPE_ZIP = "application/zip";
    private static final String SAVE_HISTORY_SUFFIX = ".zip";
    private static final String SAVE_HISTORY_MIMETYPE_TEXT = "text/plain";

    /// M: Remove cursor leak @{
    private boolean mNeedQuery = false;    //If onContentChanged is called, set it means we need query again to refresh list
    private boolean mIsInActivity = false; //If activity is not displayed, no need do query
    /// @}

    private boolean mIsJoynChanged;
    private boolean mIsFirstCreate;
    private AlertDialog mDeleteAlertDialog;

    private static boolean sIsDeleting = false;

    // add for mutli user
    private boolean isUserHasPerUsingMms = true;

    private StatusBarSelectorReceiver mStatusBarSelectorReceiver;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PDebug.EndAndStart("enterMms()", "ConversationList.onCreate");
        /// M: Code analyze 009, For bug ALPS00270910, Default SIM card icon shown in status
        /// bar is incorrect, need to get current sim information . @{
        sActivity = ConversationList.this;
        /// @}
        /// M: Code analyze 010, new feature, MTK_OP01_PROTECT_START . @{
        Intent intent;
        boolean dirMode;
        dirMode = MmsConfig.getMmsDirMode();
        if (MmsConfig.getFolderModeEnabled() && dirMode) {
            intent = new Intent(this, FolderViewList.class);
            intent.putExtra("floderview_key", FolderViewList.OPTION_INBOX); // show inbox by default
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            finish();
            startActivity(intent);
        }
        /// @}

        setContentView(R.layout.conversation_list_screen);
        mSmsPromoBannerView = findViewById(R.id.banner_sms_promo);
        /// M: Code analyze 002, For new feature ALPS00041233, msim enhancment check in . @{
        mStatusBarManager = (StatusBarManager) getSystemService(Context.STATUS_BAR_SERVICE);
        /// @}
        mQueryHandler = new ThreadListQueryHandler(getContentResolver());

        mListView = getListView();
        mListView.setOnCreateContextMenuListener(mConvListOnCreateContextMenuListener);
        mListView.setOnKeyListener(mThreadListKeyListener);
        /// M: Code analyze 005, For new feature ALPS00247476, add selectAll/unSelectAll . @{
        mListView.setOnScrollListener(mScrollListener);
        /// @}

        /// M: add for ipmessage
        if (MmsConfig.isServiceEnabled(this)) {
            IpMessageUtils.addIpMsgNotificationListeners(this, this);
        }

        // Tell the list view which view to display when the list is empty
        mEmptyViewDefault = findViewById(R.id.empty);
        mEmptyView = (ConversationEmptyView) findViewById(R.id.empty2);
		
    /*HQ_zhangjing add for al812 mms ui begin*/
        mSearchView = (SearchView) findViewById(R.id.search_view);
        mSearchView.setOnQueryTextListener(mQueryTextListener);
        timeView = (View) findViewById(R.id.time_line_view);
    /*HQ_zhangjing add for al812 mms ui end*/
	
        mNetworkStatusBar = (LinearLayout) findViewById(R.id.no_itnernet_view);
        TextView networkStatusTextView = ((TextView) mNetworkStatusBar.findViewById(R.id.no_internet_text));
        if (networkStatusTextView != null) {
            networkStatusTextView.setText(IpMessageUtils.getResourceManager(this)
                .getSingleString(IpMessageConsts.string.ipmsg_no_internet));
        }

        mListView.setOnItemLongClickListener(new ItemLongClickListener());
        /// M: Optimize select all performance, restore Actionmode status. @{
        if (savedInstanceState != null) {
            mIsNeedRestoreAdapterState = savedInstanceState.getBoolean(NEED_RESTORE_ADAPTER_STATE, false);
        } else {
            mIsNeedRestoreAdapterState = false;
        }
        /// @}
        initListAdapter();

        mContext = ConversationList.this;

        mHandler = new Handler();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean checkedMessageLimits = mPrefs.getBoolean(CHECKED_MESSAGE_LIMITS, false);
        if (DEBUG) Log.v(TAG, "checkedMessageLimits: " + checkedMessageLimits);
        if (!checkedMessageLimits) {
            runOneTimeStorageLimitCheckForLegacyMessages();
        }

        /** M: get fontscale
         *  we only need to set it to true if needed
         *  font scale change will make this activity create again
         */
        mFontScale = getResources().getConfiguration().fontScale;
        if (mFontScale > MAX_FONT_SCALE) {
            MmsLog.d(TAG, "system fontscale is:" + mFontScale);
            mListAdapter.setSubjectSingleLineMode(true);
        }

        /// Google JB MR1.1 patch. conversation list can restore scroll position
        if (savedInstanceState != null) {
            mSavedFirstVisiblePosition = savedInstanceState.getInt(LAST_LIST_POS,
                    AdapterView.INVALID_POSITION);
            mSavedFirstItemOffset = savedInstanceState.getInt(LAST_LIST_OFFSET, 0);
        } else {
            mSavedFirstVisiblePosition = AdapterView.INVALID_POSITION;
            mSavedFirstItemOffset = 0;
        }
        mIsJoynChanged = true;
        mIsFirstCreate = true;
        PDebug.EndAndStart("ConversationList.onCreate", "onCreate -> onStart");
        /// M: add for update sub state dynamically. @{
        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        registerReceiver(mSubReceiver, intentFilter);
        /// @}

        mStatusBarSelectorReceiver = new StatusBarSelectorReceiver(this);
        IntentFilter statusBarSelectorIntentFilter = new IntentFilter(StatusBarSelectorReceiver.ACTION_MMS_ACCOUNT_CHANGED);
        registerReceiver(mStatusBarSelectorReceiver, statusBarSelectorIntentFilter);
    }

    private void setupActionBar() {
        ActionBar actionBar = getActionBar();

        ViewGroup v = (ViewGroup)LayoutInflater.from(this)
            .inflate(R.layout.conversation_list_actionbar, null);
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                ActionBar.DISPLAY_SHOW_CUSTOM);
        actionBar.setCustomView(v,
                new ActionBar.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT,
                        ActionBar.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_VERTICAL | Gravity.RIGHT));

        mUnreadConvCount = (TextView)v.findViewById(R.id.unread_conv_count);
    }

    private final ConversationListAdapter.OnContentChangedListener mContentChangedListener =
        new ConversationListAdapter.OnContentChangedListener() {

        @Override
        public void onContentChanged(ConversationListAdapter adapter) {
            /// M: Remove cursor leak and reduce needless query @{
            /* Only need when activity is shown*/
            MmsLog.d(TAG, "onContentChanged begin");
            if (mIsInActivity) {
                mNeedQuery = true;
                startAsyncQuery();
            }
            /// @}
        }
    };

    private void initListAdapter() {
        mListAdapter = new ConversationListAdapter(this, null);
        /** M: now this code is useless and will lead to a JE, comment it.
         *  listener is set in onStart
         */
        //mListAdapter.setOnContentChangedListener(mContentChangedListener);
        setListAdapter(mListAdapter);
        getListView().setRecyclerListener(mListAdapter);
    }

    private void initSmsPromoBanner() {
        /// M: add for Mutli-user, show 'user is not allowed to use SMS' alert if user has no
        // permission to use SMS. @{
        ImageView defaultSmsAppIconImageView =
            (ImageView)mSmsPromoBannerView.findViewById(R.id.banner_sms_default_app_icon);
        TextView permissionAlertView = (TextView) mSmsPromoBannerView
                .findViewById(R.id.sms_permission_alert);
        LinearLayout disabledAlertView = (LinearLayout) mSmsPromoBannerView
                .findViewById(R.id.sms_disabled_alert);
        if (!isUserHasPerUsingMms) {
            mSmsPromoBannerView.setClickable(false);
            permissionAlertView.setVisibility(View.VISIBLE);
            disabledAlertView.setVisibility(View.GONE);
            defaultSmsAppIconImageView.setImageDrawable(getResources().getDrawable(
                    R.drawable.ic_launcher_smsmms));
            return;
        } else {
            mSmsPromoBannerView.setClickable(true);
            permissionAlertView.setVisibility(View.GONE);
            disabledAlertView.setVisibility(View.VISIBLE);
        }
        /// @}
        final PackageManager packageManager = getPackageManager();
        final String smsAppPackage = Telephony.Sms.getDefaultSmsPackage(this);

        // Get all the data we need about the default app to properly render the promo banner. We
        // try to show the icon and name of the user's selected SMS app and have the banner link
        // to that app. If we can't read that information for any reason we leave the fallback
        // text that links to Messaging settings where the user can change the default.
        Drawable smsAppIcon = null;
        ApplicationInfo smsAppInfo = null;
        try {
            smsAppIcon = packageManager.getApplicationIcon(smsAppPackage);
            smsAppInfo = packageManager.getApplicationInfo(smsAppPackage, 0);
        } catch (NameNotFoundException e) {
        }
        final Intent smsAppIntent = packageManager.getLaunchIntentForPackage(smsAppPackage);

        // If we got all the info we needed
        if (smsAppIcon != null && smsAppInfo != null && smsAppIntent != null) {
            defaultSmsAppIconImageView.setImageDrawable(smsAppIcon);
            TextView smsPromoBannerTitle =
                    (TextView)mSmsPromoBannerView.findViewById(R.id.banner_sms_promo_title);
            String message = getResources().getString(R.string.banner_sms_promo_title_application,
                    smsAppInfo.loadLabel(packageManager));
            smsPromoBannerTitle.setText(message);

            mSmsPromoBannerView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(smsAppIntent);
                }
            });
        } else {
            // Otherwise the banner will be left alone and will launch settings
            mSmsPromoBannerView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Launch settings
                    Intent settingIntent = null;
                    if (MmsConfig.isSupportTabSetting()) {
                        settingIntent = new Intent(ConversationList.this, MessageTabSettingActivity.class);
                    } else {
                        settingIntent = new Intent(ConversationList.this, SettingListActivity.class);
                    }
                    startActivityIfNeeded(settingIntent, -1);
                }
            });
        }
    }

    /**
     * Checks to see if the number of MMS and SMS messages are under the limits for the
     * recycler. If so, it will automatically turn on the recycler setting. If not, it
     * will prompt the user with a message and point them to the setting to manually
     * turn on the recycler.
     */
    public synchronized void runOneTimeStorageLimitCheckForLegacyMessages() {
        if (Recycler.isAutoDeleteEnabled(this)) {
            if (DEBUG) Log.v(TAG, "recycler is already turned on");
            // The recycler is already turned on. We don't need to check anything or warn
            // the user, just remember that we've made the check.
            markCheckedMessageLimit();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (Recycler.checkForThreadsOverLimit(ConversationList.this)) {
                    if (DEBUG) Log.v(TAG, "checkForThreadsOverLimit TRUE");
                    // Dang, one or more of the threads are over the limit. Show an activity
                    // that'll encourage the user to manually turn on the setting. Delay showing
                    // this activity until a couple of seconds after the conversation list appears.
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Intent intent = new Intent(ConversationList.this,
                                    WarnOfStorageLimitsActivity.class);
                            startActivity(intent);
                        }
                    }, 2000);
                /** M: comment this else block
                } else {
                    if (DEBUG) Log.v(TAG, "checkForThreadsOverLimit silently turning on recycler");
                    // No threads were over the limit. Turn on the recycler by default.
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            SharedPreferences.Editor editor = mPrefs.edit();
                            editor.putBoolean(GeneralPreferenceActivity.AUTO_DELETE, true);
                            editor.apply();
                        }
                    });
                */
                }
                // Remember that we don't have to do the check anymore when starting MMS.
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        markCheckedMessageLimit();
                    }
                });
            }
        }, "ConversationList.runOneTimeStorageLimitCheckForLegacyMessages").start();
    }

    /**
     * Mark in preferences that we've checked the user's message limits. Once checked, we'll
     * never check them again, unless the user wipe-data or resets the device.
     */
    private void markCheckedMessageLimit() {
        if (DEBUG) Log.v(TAG, "markCheckedMessageLimit");
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(CHECKED_MESSAGE_LIMITS, true);
        editor.apply();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // Handle intents that occur after the activity has already been created.
        startAsyncQuery();
    }

    @Override
    protected void onStart() {
        super.onStart();
        PDebug.EndAndStart("onCreate -> onStart", "ConversationList.onStart");
        /// M: Code analyze 010, new feature, MTK_OP01_PROTECT_START . @{
        MmsConfig.setMmsDirMode(false);
        MmsLog.i(TAG, "[Performance test][Mms] loading data start time ["
            + System.currentTimeMillis() + "]");
        // ipmessage is activited.
        MmsLog.d(TAG, "mIsJoynChanged is " + mIsJoynChanged);
        if (mIsJoynChanged) {
            mIsJoynChanged = false;
            MmsLog.d(TAG, "MmsConfig.isActivated is " + "" +  MmsConfig.isActivated(this));
            if (MmsConfig.isActivated(this) && (IpMessageUtils.getServiceManager(this).getDisableServiceStatus() != IpMessageConsts.DisableServiceStatus.DISABLE_PERMANENTLY)) {
                Conversation.setActivated(true);
                initSpinnerListAdapter();
                setTitle("");
                mEmptyViewDefault.setVisibility(View.GONE);
                mEmptyView.setVisibility(View.VISIBLE);
                mListView.setEmptyView(mEmptyView);
            } else {
                MmsLog.d(TAG, "normal message layout");
                Conversation.setActivated(false);
                setupActionBar();
                setTitle(R.string.app_label);
                mEmptyView.setVisibility(View.GONE);
                mEmptyViewDefault.setVisibility(View.VISIBLE);
                mListView.setEmptyView(mEmptyViewDefault);
            }
            if (!mIsFirstCreate) {
                invalidateOptionsMenu();
            }
        }
        PDebug.Start("startAsyncQuery()");
        startAsyncQuery();
        // if ipmessage is actived, the menu sendinvitation will shown/hide if enabled/disabled.
        // so we need refresh menu.
        /// this menu is removed, so comment it
        if (MmsConfig.isServiceEnabled(this)) {
            if (mNetworkStateReceiver == null) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                mNetworkStateReceiver = new NetworkStateReceiver();
                registerReceiver(mNetworkStateReceiver, filter);
            }
        }
        /// @}
        MessagingNotification.cancelNotification(getApplicationContext(),
                SmsRejectedReceiver.SMS_REJECTED_NOTIFICATION_ID);

        DraftCache.getInstance().addOnDraftChangedListener(this);

        mNeedToMarkAsSeen = true;

        /// M: setOnContentChangedListener here, it will be removed in onStop @{
        mIsInActivity = true;
        if (mListAdapter != null) {
            MmsLog.d(TAG, "set onContentChanged listener");
            mListAdapter.setOnContentChangedListener(mContentChangedListener);
        }
        /// @}
        // We used to refresh the DraftCache here, but
        // refreshing the DraftCache each time we go to the ConversationList seems overly
        // aggressive. We already update the DraftCache when leaving CMA in onStop() and
        // onNewIntent(), and when we delete threads or delete all in CMA or this activity.
        // I hope we don't have to do such a heavy operation each time we enter here.
        /// M: Code analyze 0014, For new feature, third party may add/delete
        /// draft, and we must refresh to check this.
        /// M: to resolve ALPS00812509, do not refresh in onStart() to avoid frequently
        /// db operation while switch launcher and ConversationList.
        //DraftCache.getInstance().refresh();
        /// @}

        // we invalidate the contact cache here because we want to get updated presence
        // and any contact changes. We don't invalidate the cache by observing presence and contact
        // changes (since that's too untargeted), so as a tradeoff we do it here.
        // If we're in the middle of the app initialization where we're loading the conversation
        // threads, don't invalidate the cache because we're in the process of building it.
        // TODO: think of a better way to invalidate cache more surgically or based on actual
        // TODO: changes we care about
        if (!Conversation.loadingThreads()) {
            Contact.invalidateCache();
        }

        /// M: Code analyze 012, new feature, mms dialog notify . @{
        IMmsDialogNotifyExt dialogPlugin =
                    (IMmsDialogNotifyExt) MmsPluginManager.getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_DIALOG_NOTIFY);
        dialogPlugin.closeMsgDialog();
        /// @}

        /// M: add for ALPS01766374 ipMessage merge to L @{
        IpMessageNmsgUtil.nmsgCheckService();
        /// @}

        PDebug.EndAndStart("ConversationList.onStart", "onStart -> onResume");
    }

    @Override
    protected void onStop() {
        super.onStop();

        /// M: @{
        mIsInActivity = false;
        /// @}

        DraftCache.getInstance().removeOnDraftChangedListener(this);

        if (mQueryHandler != null) {
            MmsLog.d(TAG, "cancel undone queries in onStop");
            mQueryHandler.cancelOperation(THREAD_LIST_QUERY_TOKEN);
            mQueryHandler.cancelOperation(UNREAD_THREADS_QUERY_TOKEN);
            mNeedQuery = false;
        }
        /// @}

        // Simply setting the choice mode causes the previous choice mode to finish and we exit
        // multi-select mode (if we're in it) and remove all the selections.
        /// M:comment a line
        //getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);

        /// M: comment this line
        //mListAdapter.changeCursor(null);
    }

    @Override
    public void onDraftChanged(final long threadId, final boolean hasDraft) {
        // Run notifyDataSetChanged() on the main thread.
        mQueryHandler.post(new Runnable() {
            @Override
            public void run() {
                if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                    log("onDraftChanged: threadId=" + threadId + ", hasDraft=" + hasDraft);
                }
                mListAdapter.notifyDataSetChanged();
            }
        });
    }

    private void startAsyncQuery() {
        try {
            /// M: add for ipmessage
            if (Conversation.getActivated()) {
                String selection = null;
                mNeedQuery = false;

                switch (sConversationListOption) {
                case OPTION_CONVERSATION_LIST_ALL:
                    MmsLog.d(IPMSG_TAG, "startAsyncQuery(): query for all messages except spam");
                    mSpinnerTextView.setText(IpMessageUtils.getResourceManager(this)
                        .getSingleString(IpMessageConsts.string.ipmsg_conversation_list_all));
                    selection = "threads._id not in (SELECT DISTINCT "
                                + Sms.THREAD_ID
                                + " FROM thread_settings WHERE spam=1) ";
                    Conversation.startQueryExtend(mQueryHandler, THREAD_LIST_QUERY_TOKEN, selection);
                    Conversation.startQuery(mQueryHandler, UNREAD_THREADS_QUERY_TOKEN, Threads.READ + "=0"
                            + " and " + selection);
                    break;
                case OPTION_CONVERSATION_LIST_GROUP_CHATS:
                    MmsLog.d(IPMSG_TAG, "startAsyncQuery(): query for group messages");
                    mSpinnerTextView.setText(IpMessageUtils.getResourceManager(this)
                        .getSingleString(IpMessageConsts.string.ipmsg_conversation_list_group_chats));
                    selection = "threads._id IN (SELECT DISTINCT " + Sms.THREAD_ID
                            + " FROM thread_settings WHERE spam=0)"
                            + " AND threads.recipient_ids IN (SELECT _id FROM canonical_addresses" + " WHERE "
                            + "SUBSTR(address, 1, 4) = '" + IpMessageConsts.GROUP_START + "'" + ")";

                    Conversation.startQueryExtend(mQueryHandler, THREAD_LIST_QUERY_TOKEN, selection);
                    Conversation.startQuery(mQueryHandler, UNREAD_THREADS_QUERY_TOKEN, Threads.READ + "=0"
                            + " and " + selection);
                    break;
                case OPTION_CONVERSATION_LIST_SPAM:
                    mSpinnerTextView.setText(IpMessageUtils.getResourceManager(this)
                        .getSingleString(IpMessageConsts.string.ipmsg_conversation_list_spam));
                    //selection = Threads.SPAM + "=1 OR _ID in (SELECT DISTINCT " + Sms.THREAD_ID + " FROM sms WHERE "
                    //        + Sms.SPAM + "=1) ";
                    selection = "threads._id IN (SELECT DISTINCT " + Sms.THREAD_ID + " FROM thread_settings WHERE spam=1) ";
                    MmsLog.d(IPMSG_TAG, "startAsyncQuery(): query for spam messages, selection = " + selection);
                    Conversation.startQueryExtend(mQueryHandler, THREAD_LIST_QUERY_TOKEN, selection);
                    Conversation.startQuery(mQueryHandler, UNREAD_THREADS_QUERY_TOKEN, Threads.READ + "=0"
                            + " and " + selection);
                    break;
                case OPTION_CONVERSATION_LIST_JOYN:
                    MmsLog.d(IPMSG_TAG, "startAsyncQuery(): query for joyn messages");
                    mSpinnerTextView.setText(IpMessageUtils.getResourceManager(this)
                            .getSingleString(IpMessageConsts.string.ipmsg_conversation_list_joyn));
                    selection = "threads._id IN (SELECT DISTINCT " + Sms.THREAD_ID
                            + " FROM thread_settings WHERE spam=0)"
                            + " AND threads.recipient_ids IN (SELECT _id FROM canonical_addresses" + " WHERE "
                            + "SUBSTR(address, 1, 4) = '" + IpMessageConsts.JOYN_START + "'" + ")";

                    Conversation.startQueryExtend(mQueryHandler, THREAD_LIST_QUERY_TOKEN, selection);
                    Conversation.startQuery(mQueryHandler, UNREAD_THREADS_QUERY_TOKEN, Threads.READ + "=0"
                            + " and " + selection);
                    break;
                case OPTION_CONVERSATION_LIST_XMS:
                    MmsLog.d(IPMSG_TAG, "startAsyncQuery(): query for xms messages");
                    mSpinnerTextView.setText(IpMessageUtils.getResourceManager(this)
                            .getSingleString(IpMessageConsts.string.ipmsg_conversation_list_xms));
                    selection = "threads._id IN (SELECT DISTINCT " + Sms.THREAD_ID
                            + " FROM thread_settings WHERE spam=0)"
                            + " AND threads.recipient_ids NOT IN (SELECT _id FROM canonical_addresses" + " WHERE "
                            + "SUBSTR(address, 1, 4) = '" + IpMessageConsts.JOYN_START 
                            + "' or SUBSTR(address, 1, 4) = '" + IpMessageConsts.GROUP_START + "'" + ")";

                    Conversation.startQueryExtend(mQueryHandler, THREAD_LIST_QUERY_TOKEN, selection);
                    Conversation.startQuery(mQueryHandler, UNREAD_THREADS_QUERY_TOKEN, Threads.READ + "=0"
                            + " and " + selection);
                    break;
                default:
                    break;
                }
                /// M: update dropdown list
                mDropdownAdapter = getDropDownMenuData(mDropdownAdapter, sConversationListOption);
            } else {
                /// M: @{
                mNeedQuery = false;
                /// M: fix bug ALPS00941735, except Obsolete ThreadId when query
                ((TextView) (mEmptyViewDefault)).setText(R.string.loading_conversations);
                if (DraftManager.sEditingThread.isEmpty()) {
                    MmsLog.d(TAG, "DraftManager.sEditingThread = Empty");
                    Conversation.startQueryForAll(mQueryHandler, THREAD_LIST_QUERY_TOKEN);
                } else {
                    long exceptID = getExceptId();
                    MmsLog.d(TAG, "DraftManager except ThreadId = " + exceptID);
                    Conversation.startQuery(mQueryHandler, THREAD_LIST_QUERY_TOKEN, "threads._id<>" + exceptID);
                }
                Conversation.startQuery(mQueryHandler, UNREAD_THREADS_QUERY_TOKEN, "allunread");
            }
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    SearchView.OnQueryTextListener mQueryTextListener = new SearchView.OnQueryTextListener() {
        @Override
        public boolean onQueryTextSubmit(String query) {
            Intent intent = new Intent();
            intent.setClass(ConversationList.this, SearchActivity.class);
            intent.putExtra(SearchManager.QUERY, query);
            startActivity(intent);
            mSearchItem.collapseActionView();
            return true;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            /// M: Add for OP09; @{
            if (MmsConfig.isAdvanceSearchEnable()) {
                if (newText == null || newText.equals("")) {
                    if (mImageSearchBtn != null) {
                        mImageSearchBtn.setVisibility(View.VISIBLE);
                    }
                } else {
  /*HQ_sunli 20150818 HQ01328028 begin*/
                    if (mImageSearchBtn != null) {
  /*HQ_sunli 20150818 HQ01328028 end*/
                    mImageSearchBtn.setVisibility(View.GONE);
                     }
                }
                return true;
            }
            /// @}
            return false;
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MmsLog.d(TAG, "onCreateOptionsMenu enter!!");
        /// M: for ALPS01861847, add for mutli user @{
        if (!isUserHasPerUsingMms) {
            MmsLog.d(TAG, "onCreateOptionsMenu user has no permission");
            return false;
        }
        /// @}
        getMenuInflater().inflate(R.menu.conversation_list_menu, menu);

        mSearchItem = menu.findItem(R.id.search);
        /// M: Add for OP09 advance search @{
        //mSearchView = (SearchView) mSearchItem.getActionView();
		
		/*HQ_zhangjing add for al812 mms ui begin*/
        /*
        mAdvancedSearchView = (AdvancedSearchView) mSearchItem.getActionView();
        if (MmsConfig.isAdvanceSearchEnable()) {
            mImageSearchBtn = mAdvancedSearchView.getImageSearchBtn();
            mImageSearchBtn.setVisibility(View.VISIBLE);
            mImageSearchBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(ConversationList.this, AdvancedSearchActivity.class));
                }
            });
        }
        mSearchView = mAdvancedSearchView.getSearchView();
        /// @}
        mSearchView.setOnQueryTextListener(mQueryTextListener);
        mSearchView.setQueryHint(getString(R.string.search_hint));
        mSearchView.setIconifiedByDefault(true);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);

        if (searchManager != null) {
            SearchableInfo info = searchManager.getSearchableInfo(this.getComponentName());
            mSearchView.setSearchableInfo(info);
        }
	*/
	/*
        MenuItem cellBroadcastItem = menu.findItem(R.id.action_cell_broadcasts);
        if (cellBroadcastItem != null) {
            // Enable link to Cell broadcast activity depending on the value in config.xml.
            boolean isCellBroadcastAppLinkEnabled = this.getResources().getBoolean(
                    com.android.internal.R.bool.config_cellBroadcastAppLinks);
            try {
                if (isCellBroadcastAppLinkEnabled) {
                    PackageManager pm = getPackageManager();
                    if (pm.getApplicationEnabledSetting("com.android.cellbroadcastreceiver")
                            == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                        isCellBroadcastAppLinkEnabled = false;  // CMAS app disabled
                    }
                }
            } catch (IllegalArgumentException ignored) {
                isCellBroadcastAppLinkEnabled = false;  // CMAS app not installed
            }
            if (!isCellBroadcastAppLinkEnabled) {
                cellBroadcastItem.setVisible(false);
            }
        }
	  */
	  /*HQ_zhangjing add for al812 mms ui end*/
        /*HQ_sunli 20150803 HQ01306167 begin*/
        menu.removeItem(R.id.action_cell_broadcasts); 
        /*HQ_sunli 20150803 HQ01306167 end*/
        /// M: add for ipmessage menu
        if (MmsConfig.isActivated(this)) {
            MenuItem item = menu.findItem(R.id.create_group_chat);
            if (item != null &&
                IpMessageUtils.getServiceManager(this).isFeatureSupported(IpMessageConsts.FeatureId.GROUP_MESSAGE)) {
                item.setVisible(true);
            }
            /*
            if (MmsConfig.isServiceEnabled(this)) {
                item = menu.findItem(R.id.send_invitations);
                item.setVisible(true);
            } else {
                item = menu.findItem(R.id.send_invitations);
                item.setVisible(false);
            }
            */
        }

        /// M: Code analyze 009, For new feature, plugin . @{
        if (mMmsConversationPlugin == null) {
            initPlugin(this);
        }
        mMmsConversationPlugin.addOptionMenu(menu, MmsConfig.getPluginMenuIDBase());
        /// @}

        /// M: for mwi. @{
        if (FeatureOption.MTK_MWI_SUPPORT) {
            MenuItem mwiItem = menu.findItem(R.id.action_mwi);
            mwiItem.setVisible(true);
        }
        /// @}
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MmsLog.d(TAG, "onPrepareOptionsMenu enter!!");
        /// M: for ALPS01861847, add for mutli user @{
        if (!isUserHasPerUsingMms) {
            MmsLog.d(TAG, "onPrepareOptionsMenu user has no permission");
            return false;
        }
        /// @}
        mMmsConversationPlugin.onPrepareOptionsMenu(menu);
        mOptionsMenu = menu ;
        setDeleteMenuVisible(menu);
        MenuItem item;
        item = menu.findItem(R.id.action_compose_new);
        if (item != null ){
            // Dim compose if SMS is disabled because it will not work (will show a toast)
            item.getIcon().setAlpha(MmsConfig.isSmsEnabled(this) ? 255 : 127);
        }
        if (!LogTag.DEBUG_DUMP) {
            item = menu.findItem(R.id.action_debug_dump);
            if (item != null) {
                item.setVisible(false);
            }
        }

        /// M: Code analyze 011, add code for omacp . @{
        item = menu.findItem(R.id.action_omacp);
        item.setVisible(false);
        Context otherAppContext = null;
        try {
            otherAppContext = this.createPackageContext("com.mediatek.omacp",
                    Context.CONTEXT_IGNORE_SECURITY);
        } catch (Exception e) {
            MmsLog.e(CONV_TAG, "ConversationList NotFoundContext");
        }
        if (null != otherAppContext) {
            SharedPreferences sp = otherAppContext.getSharedPreferences("omacp",
                    MODE_WORLD_READABLE | MODE_MULTI_PROCESS);
            boolean omaCpShow = sp.getBoolean("configuration_msg_exist", false);
            if (omaCpShow) {
                item.setVisible(true);
            }
        }
        /// @}

        MenuItem createGroupItem = menu.findItem(R.id.create_group_chat);
        /// M: add for ipmessage menu
        if (MmsConfig.isActivated(this)) {
            if (createGroupItem != null) {
                if (IpMessageUtils.getServiceManager(this).getDisableServiceStatus() == IpMessageConsts.DisableServiceStatus.DISABLE_TEMPORARY) {
                    createGroupItem.setEnabled(false);
                    createGroupItem.getIcon().setAlpha(127);
                } else if (IpMessageUtils.getServiceManager(this).getDisableServiceStatus() == IpMessageConsts.DisableServiceStatus.DISABLE_PERMANENTLY) {
                    createGroupItem.setVisible(false);
                } else {
                    createGroupItem.setVisible(true);
                    createGroupItem.getIcon().setAlpha(255);
                }
            }
            if (item != null) {
                // Dim compose if SMS is disabled because it will not work (will show a toast)
                item.getIcon().setAlpha(mIsSmsEnabled ? 255 : 127);
            }
        } else {
            if (createGroupItem != null) {
                createGroupItem.setVisible(false);
            }
        }
        item = menu.findItem(R.id.action_wappush);
        item.setVisible(true);

        //add for multi user feature
        if (UserHandle.myUserId() != UserHandle.USER_OWNER) {
            MenuItem itemSetting = menu.findItem(R.id.action_settings);
            MenuItem itemSimInfo = menu.findItem(R.id.action_siminfo);
            itemSetting.setVisible(false);
            itemSimInfo.setVisible(false);
        }

        return true;
    }

    @Override
    public boolean onSearchRequested() {
        if (mSearchItem != null) {
            mSearchItem.expandActionView();
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        /// M: Code analyze 009, For new feature, plugin . @{
        if (mMmsConversationPlugin.onOptionsItemSelected(item)) {
            return true;
        }
        /// @}

        /// M: add for ipmessage menu
        if (MmsConfig.isActivated(this)) {
            switch (item.getItemId()) {
            case R.id.create_group_chat:
                ///M: iSMS activation Statistics {@
                if (mIsSmsEnabled) {
                        if (MmsConfig.isActivated(this)) {
                        Intent createGroupIntent = new Intent(RemoteActivities.CONTACT);
                        createGroupIntent.putExtra(RemoteActivities.KEY_TYPE,
                                SelectContactType.IP_MESSAGE_USER);
                        createGroupIntent.putExtra(RemoteActivities.KEY_REQUEST_CODE,
                                REQUEST_CODE_SELECT_CONTACT_FOR_GROUP);
                        IpMessageUtils.startRemoteActivity(this, createGroupIntent);
                    } else {
                        return true;
                    }
                } else {
                    Toast.makeText(
                            this,
                            IpMessageUtils.getResourceManager(ConversationList.this)
                                    .getSingleString(
                                            IpMessageConsts.string.ipmsg_nms_mms_not_default),
                            Toast.LENGTH_LONG).show();
                }
              ///@}
                break;
            case R.id.send_invitations:
                // TODO send invitation
                Intent createGroupIntent2 = new Intent(RemoteActivities.CONTACT);
                createGroupIntent2.putExtra(RemoteActivities.KEY_TYPE, SelectContactType.NOT_IP_MESSAGE_USER);
                createGroupIntent2.putExtra(RemoteActivities.KEY_REQUEST_CODE, REQUEST_CODE_INVITE);
                IpMessageUtils.startRemoteActivity(this, createGroupIntent2);
                break;
            default:
                break;
            }
        }

        switch(item.getItemId()) {
            case R.id.action_compose_new:
                if (mIsSmsEnabled) {
                    createNewMessage();
                } else {
                    // Display a toast letting the user know they can not compose.
                    if (mComposeDisabledToast == null) {
                        mComposeDisabledToast = Toast.makeText(this,
                                R.string.compose_disabled_toast, Toast.LENGTH_SHORT);
                    }
                    mComposeDisabledToast.show();
                }
                break;
            case R.id.action_delete_all:
                /// M: ip message don't delete all threads, always delete selected.
                if (MmsConfig.isActivated(this)) {
                    ArrayList<Long> threadIds = new ArrayList<Long>();
                    ListView listView = getListView();
                    ConversationListAdapter adapter = (ConversationListAdapter) listView.getAdapter();
                    int num = adapter.getCount();
                    for (int position = 0; position < num; position++) {
                        Cursor cursor = (Cursor) listView.getItemAtPosition(position);
                        Conversation conv = Conversation.getFromCursor(ConversationList.this, cursor);
                        threadIds.add(conv.getThreadId());
                    }
                    confirmDeleteThreads(threadIds, mQueryHandler);
                } else {
                    // The invalid threadId of -1 means all threads here.
                    confirmDeleteThread(-1L, mQueryHandler);
                }
                break;
            case R.id.action_settings:
                Intent settingIntent = null;
                if (MmsConfig.isSupportTabSetting()) {
                    settingIntent = new Intent(this, MessageTabSettingActivity.class);
                } else {
                    settingIntent = new Intent(this, SettingListActivity.class);
                }
                startActivityIfNeeded(settingIntent, -1);
                break;
            /// M: Code analyze 011, add omacp to option menu . @{
            case R.id.action_omacp:
                Intent omacpintent = new Intent();
                omacpintent.setClassName("com.mediatek.omacp", "com.mediatek.omacp.message.OmacpMessageList");
                omacpintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityIfNeeded(omacpintent, -1);
                break;
            /// @}
            case R.id.action_wappush:
                Intent wpIntent = new Intent(this, WPMessageActivity.class);
                startActivity(wpIntent);
                break;
            case R.id.action_debug_dump:
                LogTag.dumpInternalTables(this);
                break;
			/*HQ_zhangjing add for al812 mms ui begin*/	
            /*case R.id.action_cell_broadcasts:
                Intent cellBroadcastIntent = new Intent(Intent.ACTION_MAIN);
                cellBroadcastIntent.setComponent(new ComponentName(
                        "com.android.cellbroadcastreceiver",
                        "com.android.cellbroadcastreceiver.CellBroadcastListActivity"));
                cellBroadcastIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(cellBroadcastIntent);
                } catch (ActivityNotFoundException ignored) {
                    Log.e(TAG, "ActivityNotFoundException for CellBroadcastListActivity");
                }
                return true;*/
            case R.id.search:
				if(mSearchView.getVisibility() == View.VISIBLE){
                    mSearchView.setFocusable(true);
                    mSearchView.requestFocus();
                    InputMethodManager inputMethodManager =
                        (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputMethodManager.toggleSoftInput(0,InputMethodManager.HIDE_NOT_ALWAYS);
				}
                break;
			 /*HQ_zhangjing add for al812 mms ui end*/	
            case R.id.action_mwi:
                Intent mwiIntent = new Intent(this, MwiListActivity.class);
                startActivity(mwiIntent);
                break;
			/*HQ_zhangjing 2015-09-02 for add blacklist menu begin*/
			case R.id.action_blacklist:
				blackListManager();
				break;
			/*HQ_zhangjing 2015-09-02 for add blacklist menu end*/
            default:
                return true;
        }
        return false;
    }
	
	/*HQ_zhangjing 2015-09-02 for add blacklist menu begin*/
	public void blackListManager(){
		try {
				ComponentName componetName = new ComponentName("com.huawei.systemmanager","com.huawei.harassmentinterception.ui.InterceptionActivity"); 
				Intent LifeServiceIntent = new Intent();
				LifeServiceIntent.setComponent(componetName);
				mContext.startActivity(LifeServiceIntent);
			} catch (Exception e) {
				// TODO: handle exception
				Toast.makeText(mContext, R.string.black_list_manager_exception,
						Toast.LENGTH_SHORT).show();
			}
	}
	/*HQ_zhangjing 2015-09-02 for add blacklist menu end*/
	
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // Note: don't read the thread id data from the ConversationListItem view passed in.
        // It's unreliable to read the cached data stored in the view because the ListItem
        // can be recycled, and the same view could be assigned to a different position
        // if you click the list item fast enough. Instead, get the cursor at the position
        // clicked and load the data from the cursor.
        // (ConversationListAdapter extends CursorAdapter, so getItemAtPosition() should
        // return the cursor object, which is moved to the position passed in)
        Cursor cursor  = (Cursor) getListView().getItemAtPosition(position);
        /// M: Code analyze 015, For bug,  add cursor == null check . @{
        if (cursor == null) {
            return;
        }
        /// @}
        MmsLog.d(TAG, "onListItemClick: pos=" + position);
        Conversation conv = Conversation.from(this, cursor);
        long threadId = conv.getThreadId();
        /// M: Code analyze 005, For new feature ALPS00247476, handle click item with ActionMode . @{
        if (mActionMode != null) {
            boolean checked = mListAdapter.isContainThreadId(threadId);
            mActionModeListener.setItemChecked(position, !checked, null);
            mActionModeListener.updateActionMode();
            if (mListAdapter != null) {
                mListAdapter.notifyDataSetChanged();
            }
            return;
        }
        /// @}

        if (LogTag.VERBOSE) {
            Log.d(TAG, "onListItemClick: pos=" + position + ", view=" + v + ", threadId=" + threadId);
        }

        /// M: Fix ipmessage bug
        viewThread(conv, conv.getType()) ;
    }

    /// M: Fix ipmessage bug @{
    private void viewThread(Conversation conv, int type) {

        long threadId = conv.getThreadId();
        /// M: add for ALPS01766374 ipMessage merge to L @{
        if (IpMessageNmsgUtil.startNmsgActivity(this, conv, IpMessageNmsgUtil.OpenType.SMS_LIST)) {
            return;
        }
        /// @}

        /// M: Code analyze 001, For new feature ALPS00131956, wappush: modify
        /// the calling of openThread, add one parameter. @{
        MmsLog.i(WP_TAG, "ConversationList: " + "conv.getType() is : " + conv.getType());
        /// M: add for ipmessage
        if (MmsConfig.isServiceEnabled(this)) {
            /// M: add for ipmessage, handle group thread
            ContactList list = conv.getRecipients();
            String number = null;
            if (list == null || list.size() < 1) {
                // there is no recipients!
                MmsLog.d(TAG, "a thread with no recipients, threadId:" + threadId);
                number = "";
            } else {
                number = conv.getRecipients().get(0).getNumber();
            }
            MmsLog.i(IPMSG_TAG, "open thread by number " + number);
            if (conv.getRecipients().size() == 1 && number.startsWith(IpMessageConsts.GROUP_START)) {
                MmsLog.i(IPMSG_TAG, "open group thread by thread id " + threadId);
                conv.markAsSeen();
                openIpMsgThread(threadId);
                return;
            }
        }
        openThread(threadId, type);
        /// @}
    }
    /// @}

    private void createNewMessage() {
        startActivity(ComposeMessageActivity.createIntent(this, 0));
    }

    /// M: Code analyze 001, For new feature ALPS00131956, the method is extended. @{
    private void openThread(long threadId, int type) {
        switch (type) {
        case Telephony.Threads.WAPPUSH_THREAD:
                startActivity(WPMessageActivity.createIntent(this, threadId));
            break;
        case Telephony.Threads.CELL_BROADCAST_THREAD:
                startActivity(CBMessageListActivity.createIntent(this, threadId));
            break;
        default:
                startActivity(ComposeMessageActivity.createIntent(this, threadId));
            break;
        }
    }
    /// @}

    public static Intent createAddContactIntent(String address) {
        // address must be a single recipient
        Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        intent.setType(Contacts.CONTENT_ITEM_TYPE);
        if (Mms.isEmailAddress(address)) {
            intent.putExtra(ContactsContract.Intents.Insert.EMAIL, address);
        } else {
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, address);
            intent.putExtra(ContactsContract.Intents.Insert.PHONE_TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

        return intent;
    }

    private final OnCreateContextMenuListener mConvListOnCreateContextMenuListener =
        new OnCreateContextMenuListener() {
        @Override
        public void onCreateContextMenu(ContextMenu menu, View v,
                ContextMenuInfo menuInfo) {
            Cursor cursor = mListAdapter.getCursor();
            if (cursor == null || cursor.getPosition() < 0) {
                return;
            }
            Conversation conv = Conversation.from(ConversationList.this, cursor);
            /// M: Code analyze 001, For new feature ALPS00131956, wappush: get
            /// the added mType value. @{
            mType = conv.getType();
            MmsLog.i(WP_TAG, "ConversationList: " + "mType is : " + mType);
            /// @}

            ContactList recipients = conv.getRecipients();
            menu.setHeaderTitle(recipients.formatNames(","));

            AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) menuInfo;
            menu.add(0, MENU_VIEW, 0, R.string.menu_view);

            // Only show if there's a single recipient
            if (recipients.size() == 1) {
                // do we have this recipient in contacts?
                if (recipients.get(0).existsInDatabase()) {
                    menu.add(0, MENU_VIEW_CONTACT, 0, R.string.menu_view_contact);
                } else {
                    /// M: Fix ipmessage bug @{
                    String number = recipients.get(0).getNumber();
                    MmsLog.i(IPMSG_TAG, "show menu_add_to_contacts by number " + number);
                    if (MmsConfig.isServiceEnabled(mContext) || recipients.size() != 1
                            || !number.startsWith(IpMessageConsts.GROUP_START)) {
                        menu.add(0, MENU_ADD_TO_CONTACTS, 0, R.string.menu_add_to_contacts);
                }
                    /// @}
                }
            }
            if (mIsSmsEnabled) {
                menu.add(0, MENU_DELETE, 0, R.string.menu_delete);
            }
        }
    };
    
    /*add by lihaizhou for Prohibit Msg for ChildMode by begin */
 private boolean isChildModeOn() 
	{
	        String isOn = ChildMode.getString(mContext.getContentResolver(),
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
   
   /*add by lihaizhou for Prohibit Msg for ChildMode by end */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        Cursor cursor = mListAdapter.getCursor();
        if (cursor != null && cursor.getPosition() >= 0) {
            Conversation conv = Conversation.from(ConversationList.this, cursor);
            long threadId = conv.getThreadId();
            switch (item.getItemId()) {
            case MENU_DELETE: {
                confirmDeleteThread(threadId, mQueryHandler);
                break;
            }
            case MENU_VIEW: {
                /// M: Fix ipmessage bug @{
                /// M: Code analyze 001, For new feature ALPS00131956,
                /// wappush: method is changed. @{
                /// openThread(threadId, mType);
                /// @}
                viewThread(conv, mType) ;
                /// @}
                break;
            }
            case MENU_VIEW_CONTACT: {
                Contact contact = conv.getRecipients().get(0);
                Intent intent = new Intent(Intent.ACTION_VIEW, contact.getUri());
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                startActivity(intent);
                break;
            }
            case MENU_ADD_TO_CONTACTS: {
                String address = conv.getRecipients().get(0).getNumber();
                startActivity(createAddContactIntent(address));
                break;
            }
            default:
                break;
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // We override this method to avoid restarting the entire
        // activity when the keyboard is opened (declared in
        // AndroidManifest.xml).  Because the only translatable text
        // in this activity is "New Message", which has the full width
        // of phone to work with, localization shouldn't be a problem:
        // no abbreviated alternate words should be needed even in
        // 'wide' languages like German or Russian.

        super.onConfigurationChanged(newConfig);
        if (DEBUG) Log.v(TAG, "onConfigurationChanged: " + newConfig);
    }

    /**
     * Start the process of putting up a dialog to confirm deleting a thread,
     * but first start a background query to see if any of the threads or thread
     * contain locked messages so we'll know how detailed of a UI to display.
     * @param threadId id of the thread to delete or -1 for all threads
     * @param handler query handler to do the background locked query
     */
    public static void confirmDeleteThread(long threadId, AsyncQueryHandler handler) {
        ArrayList<Long> threadIds = null;
        if (threadId != -1) {
            threadIds = new ArrayList<Long>();
            threadIds.add(threadId);
        }
        confirmDeleteThreads(threadIds, handler);
    }

    /**
     * Start the process of putting up a dialog to confirm deleting threads,
     * but first start a background query to see if any of the threads
     * contain locked messages so we'll know how detailed of a UI to display.
     * @param threadIds list of threadIds to delete or null for all threads
     * @param handler query handler to do the background locked query
     */
    public static void confirmDeleteThreads(Collection<Long> threadIds, AsyncQueryHandler handler) {
        Conversation.startQueryHaveLockedMessages(handler, threadIds,
                HAVE_LOCKED_MESSAGES_TOKEN);
    }

    /**
     * Build and show the proper delete thread dialog. The UI is slightly different
     * depending on whether there are locked messages in the thread(s) and whether we're
     * deleting single/multiple threads or all threads.
     * @param listener gets called when the delete button is pressed
     * @param threadIds the thread IDs to be deleted (pass null for all threads)
     * @param hasLockedMessages whether the thread(s) contain locked messages
     * @param context used to load the various UI elements
     */
    private void confirmDeleteThreadDialog(final DeleteThreadListener listener,
            Collection<Long> threadIds,
            boolean hasLockedMessages,
            Context context) {
        View contents = View.inflate(context, R.layout.delete_thread_dialog_view, null);
        TextView msg = (TextView)contents.findViewById(R.id.message);

        if (threadIds == null) {
            msg.setText(R.string.confirm_delete_all_conversations);
        } else {
            // Show the number of threads getting deleted in the confirmation dialog.
            int cnt = threadIds.size();
            msg.setText(context.getResources().getQuantityString(
                    R.plurals.confirm_delete_conversation, cnt, cnt));
            
        }

        final CheckBox checkbox = (CheckBox)contents.findViewById(R.id.delete_locked);
        if (!hasLockedMessages) {
            checkbox.setVisibility(View.GONE);
        } else {
            listener.setDeleteLockedMessage(checkbox.isChecked());
            checkbox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.setDeleteLockedMessage(checkbox.isChecked());
    				/*HQ_zhangjing add for al812 mms ui */
                    isCheckBox=checkbox.isChecked();
                }
            });
        }
        /// M: Code analyze 023, For bug ALPS00268161, when delete one MMS, one sms will not be deleted . @{
        Cursor cursor = null;
        int smsId = 0;
        int mmsId = 0;
        cursor = context.getContentResolver().query(Sms.CONTENT_URI,
                new String[] {"max(_id)"}, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                smsId = cursor.getInt(0);
                MmsLog.d(TAG, "confirmDeleteThreadDialog max SMS id = " + smsId);
                }
            } finally {
                cursor.close();
                cursor = null;
            }
        }
        cursor = context.getContentResolver().query(Mms.CONTENT_URI,
                new String[] {"max(_id)"}, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                mmsId = cursor.getInt(0);
                MmsLog.d(TAG, "confirmDeleteThreadDialog max MMS id = " + mmsId);
                }
            } finally {
                cursor.close();
                cursor = null;
            }
        }
        listener.setMaxMsgId(mmsId, smsId);
        /// @}
         
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.confirm_dialog_title)
            .setIconAttribute(android.R.attr.alertDialogIcon)
            .setCancelable(true)
            .setPositiveButton(R.string.delete, listener)
            .setNegativeButton(R.string.no, null)
            .setView(contents);
        mDeleteAlertDialog = builder.create();
        /*add by lihaizhou for lihaizhou for ChildMode at 2015-07-23 by begin */
                if (isProhibitDeleteSmsmms()) {
                    Toast.makeText(this, R.string.Prohibit_delete, Toast.LENGTH_SHORT).show();//zhangjing
                    mDeleteAlertDialog.dismiss();
                }
               else 
               {
               mDeleteAlertDialog.show();
               }

             /*add by lihaizhou for lihaizhou for ChildMode at 2015-07-23 by end*/        
    }

    private final OnKeyListener mThreadListKeyListener = new OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DEL: {
                        long id = getListView().getSelectedItemId();
                        if (id > 0) {
                            confirmDeleteThread(id, mQueryHandler);
                        }
                        return true;
                    }
                }
            }
            return false;
        }
    };

    public static class DeleteThreadListener implements OnClickListener {
        private final Collection<Long> mThreadIds;
        private final AsyncQueryHandler mHandler;
        private final Context mContext;
        private boolean mDeleteLockedMessages;
        /// M: Code analyze 023, For bug ALPS00268161, when delete one MMS, one
        /// sms will not be deleted. . @{
        private int mMaxMmsId;
        private int mMaxSmsId;

        public void setMaxMsgId(int mmsId, int smsId) {
            mMaxMmsId = mmsId;
            mMaxSmsId = smsId;
        }
        /// @}

        public DeleteThreadListener(Collection<Long> threadIds, AsyncQueryHandler handler,
                Context context) {
            mThreadIds = threadIds;
            mHandler = handler;
            mContext = context;
        }


        public void setDeleteLockedMessage(boolean deleteLockedMessages) {
            mDeleteLockedMessages = deleteLockedMessages;
        }

        @Override
        public void onClick(DialogInterface dialog, final int whichButton) {
            MessageUtils.handleReadReport(mContext, mThreadIds,
                    PduHeaders.READ_STATUS__DELETED_WITHOUT_BEING_READ, new Runnable() {
                @Override
                public void run() {
                    /// M: fix ALPS01524674, mThreadIds is a weak reference to mSelectedThreadIds.
                    /// if delete once, mThreadIds.size() will be updated to 0.
                    if (mThreadIds != null && mThreadIds.size() == 0) {
                        return;
                    }
                    /// M: Code analyze 013, For bug ALPS00046358 , The method about the
                    /// handler with progress dialog functio . @{
                    showProgressDialog();
                    /// @}
                    sIsDeleting = true;
                    /// M: delete ipmessage in ipmessage db
                    IpMessageUtils.deleteIpMessage(mContext, mThreadIds, mMaxSmsId);

                    int token = DELETE_CONVERSATION_TOKEN;
                    /// M: wappush: do not need modify the code here, but delete function in provider has been modified.
                    /// M: fix bug ALPS00415754, add some useful log
                    MmsLog.d(TAG, "before delete threads in conversationList, mThreadIds.size = " + (mThreadIds == null ? "null" : mThreadIds.size()));
                    if (mThreadIds == null) {
                        /// M: Code analyze 023, For bug ALPS00268161, when delete one
                        /// MMS, one sms will not be deleted. . @{
                        Conversation.startDeleteAll(mHandler, token, mDeleteLockedMessages, mMaxMmsId, mMaxSmsId);
                        /// @}
                        /// M: modify for fix ALPS01071334, move to onDeleteCompleted(). in some case, when refresh run, the messages have not
                        ///     been deleted all, the draft state has not been changed, so draftcache is wrong
                        //DraftCache.getInstance().refresh();
                    } else if (mThreadIds.size() <= 1) {
                        /// @}
                        for (long threadId : mThreadIds) {
                            /// M: Code analyze 023, For bug ALPS00268161, when delete one
                            /// MMS, one sms will not be deleted . @{
                            Conversation.startDelete(mHandler, token, mDeleteLockedMessages,
                                    threadId, mMaxMmsId, mMaxSmsId);
                            /// @}
                            DraftCache.getInstance().setDraftState(threadId, false);
                        }
                    } else if (mThreadIds.size() > 1) {
                        /// M: Fix bug ALPS00780175, The 1300 threads deleting will cost more than
                        /// 10 minutes. Avoid to delete multi threads one by one, let MmsSmsProvider
                        /// handle this action. @{
                        String[] threadIds = new String[mThreadIds.size()];
                        int i = 0;
                        for (long thread : mThreadIds) {
                            threadIds[i++] = String.valueOf(thread);
                            DraftCache.getInstance().setDraftState(thread, false);
                        }
                        Conversation.startMultiDelete(mHandler, token, mDeleteLockedMessages,
                                threadIds, mMaxMmsId, mMaxSmsId);
                        /// @}
                    }
                    MmsLog.d(TAG, "after delete threads in conversationList");
                    /// @}
                }
                /// M: Code analyze 013, For bug ALPS00046358 , The method about the handler
                /// with progress dialog functio . @{
                private void showProgressDialog() {
                    if (mHandler instanceof BaseProgressQueryHandler) {
                        ((BaseProgressQueryHandler) mHandler).setProgressDialog(
                                DeleteProgressDialogUtil.getProgressDialog(mContext));
                        ((BaseProgressQueryHandler) mHandler).showProgressDialog();
                    }
                }
                /// @}
            });
            dialog.dismiss();
        }
    }


    private final Runnable mDeleteObsoleteThreadsRunnable = new Runnable() {
        @Override
        public void run() {
            if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                LogTag.debug("mDeleteObsoleteThreadsRunnable getSavingDraft(): " +
                        DraftCache.getInstance().getSavingDraft());
            }
            if (DraftCache.getInstance().getSavingDraft()) {
                // We're still saving a draft. Try again in a second. We don't want to delete
                // any threads out from under the draft.
                mHandler.postDelayed(mDeleteObsoleteThreadsRunnable, 1000);
            } else {
                /// M: Code analyze 024, For bug ALPS00234739 , draft can't be
                /// saved after share the edited picture to the same ricipient, so
                ///Remove old Mms draft in conversation list instead of compose view . @{
                MessageUtils.asyncDeleteOldMms();
                /// @}
                Conversation.asyncDeleteObsoleteThreads(mQueryHandler,
                        DELETE_OBSOLETE_THREADS_TOKEN);
            }
        }
    };

    private final class ThreadListQueryHandler extends BaseProgressQueryHandler {
        public ThreadListQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        // Test code used for various scenarios where its desirable to insert a delay in
        // responding to query complete. To use, uncomment out the block below and then
        // comment out the @Override and onQueryComplete line.
//        @Override
//        protected void onQueryComplete(final int token, final Object cookie, final Cursor cursor) {
//            mHandler.postDelayed(new Runnable() {
//                public void run() {
//                    myonQueryComplete(token, cookie, cursor);
//                    }
//            }, 2000);
//        }
//
//        protected void myonQueryComplete(int token, Object cookie, Cursor cursor) {

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
              /// M: Code analyze 015, For bug,  add cursor == null check . @{
              MmsLog.d(TAG, "onQueryComplete mNeedQuery = " + mNeedQuery +
                            " mIsInActivity = " + mIsInActivity);
              if (cursor == null) {
              /// M: Decrease query counter and do next query if any request  @{
                  setProgressBarIndeterminateVisibility(false);
                  if (mNeedQuery && mIsInActivity) {
                      MmsLog.d(TAG, "onQueryComplete cursor == null startAsyncQuery");
                      startAsyncQuery();
                  }
                  return;
              /// @}
              }
            /// @}
            switch (token) {
            case THREAD_LIST_QUERY_TOKEN:
                /// M: If no listener for content change, means no need to refresh list @{
                if (mListAdapter.getOnContentChangedListener() == null) {
                    cursor.close();
                    return;
                }
                /// @}
                MmsLog.d(TAG, "onQueryComplete cursor count is " + cursor.getCount());
                /// M: add for ipmessage, update Empty View
                updateEmptyView(cursor);
                PDebug.EndAndStart("startAsyncQuery()", "onQueryComplete -> changeCursor");
                mListAdapter.changeCursor(cursor);
				/*HQ_zhangjing add for al812 mms ui begin*/
                if (mListAdapter.getCount() == 0) {
                    ((TextView)(getListView().getEmptyView())).setText(R.string.no_conversations);
                    mSearchView.setVisibility(View.GONE);
                    timeView.setVisibility(View.GONE);
                }else{
                    mSearchView.setVisibility(View.VISIBLE);
                    timeView.setVisibility(View.VISIBLE);
                }
				/*HQ_zhangjing add for al812 mms ui end*/
				
				
                /// M: make a timer to update the list later, the time should update.
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        mListAdapter.notifyDataSetChanged();
                    }
                }, 60000);

                if (!MmsConfig.isActivated(ConversationList.this)) {
                    if (mListAdapter.getCount() == 0 && getListView().getEmptyView() instanceof TextView) {
                        ((TextView) (getListView().getEmptyView())).setText(R.string.no_conversations);
                    }
                }
                /** M: add code @{ */
                if (!Conversation.isInitialized()) {
                    Conversation.init(getApplicationContext());
                } else {
                    Conversation.removeInvalidCache(cursor);
                }
                /** @} */

                if (mNeedToMarkAsSeen) {
                    mNeedToMarkAsSeen = false;
                /// M: Code analyze 016, For new feature, wappush: method is changed . @{
                    Conversation.markAllConversationsAsSeen(getApplicationContext(),
                            Conversation.MARK_ALL_MESSAGE_AS_SEEN);

                    // Delete any obsolete threads. Obsolete threads are threads that aren't
                    // referenced by at least one message in the pdu or sms tables. We only call
                    // this on the first query (because of mNeedToMarkAsSeen).
                    mHandler.post(mDeleteObsoleteThreadsRunnable);
                }

                /// M: Code analyze 005, For new feature ALPS00247476 . @{
                if (mActionMode != null) {
                    mActionModeListener.updateActionMode();
                }
                /// @}
                /// M: Fix bug ALPS00416081
                setDeleteMenuVisible(mOptionsMenu);

                /// Google JB MR1.1 patch. conversation list can restore scroll position
                if (mSavedFirstVisiblePosition != AdapterView.INVALID_POSITION) {
                    // Restore the list to its previous position.
                    getListView().setSelectionFromTop(mSavedFirstVisiblePosition,
                            mSavedFirstItemOffset);
                    mSavedFirstVisiblePosition = AdapterView.INVALID_POSITION;
                }
                break;

            case UNREAD_THREADS_QUERY_TOKEN:
                int count = 0;
                if (cursor != null) {
                    try {
                        /// M: modified for ipmsg unread message. @{
                        if (Conversation.getActivated()) {
                            count = cursor.getCount();
                        } else if (cursor.moveToFirst()) {
                            count = cursor.getInt(0);
                        }
                        MmsLog.d(TAG, "get threads unread message count = " + count);
                        /// @}
                    } finally {
                        cursor.close();
                    }
                }
                /// M: modified for unread count display
                if (count > MAX_DISPLAY_UNREAD_COUNT) {
                    mUnreadConvCount.setText(DISPLAY_UNREAD_COUNT_CONTENT_FOR_ABOVE_99);
                } else {
                    mUnreadConvCount.setText(count > 0 ? Integer.toString(count) : null);
                }
                break;

            case HAVE_LOCKED_MESSAGES_TOKEN:
                /// M: add a log
                MmsLog.d(TAG, "onQueryComplete HAVE_LOCKED_MESSAGES_TOKEN");
                @SuppressWarnings("unchecked")
                Collection<Long> threadIds = (Collection<Long>) cookie;
                ListView listView = getListView();
                ConversationListAdapter adapter = (ConversationListAdapter) listView.getAdapter();
                if (adapter != null && threadIds != null) {
                    Cursor c = adapter.getCursor();
                    /// M: ip message don't delete all threads, always delete selected.
                    if (c != null && c.getCount() == threadIds.size() && !MmsConfig.isActivated(ConversationList.this)) {
                        threadIds = null;
                    }
                }
                confirmDeleteThreadDialog(new DeleteThreadListener(threadIds, mQueryHandler,
                        ConversationList.this), threadIds,
                        cursor != null && cursor.getCount() > 0,
                        ConversationList.this);
                if (cursor != null) {
                    cursor.close();
                }
                break;

            default:
                Log.e(TAG, "onQueryComplete called with unknown token " + token);
            }

            /// M: Do next query if any requested @{
            if (mNeedQuery && mIsInActivity) {
                startAsyncQuery();
            }
            /// @}
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            /// M: comment it
            //super.onDeleteComplete(token, cookie, result);
            sIsDeleting = false;
            switch (token) {
            case DELETE_CONVERSATION_TOKEN:
                long threadId = cookie != null ? (Long)cookie : -1;     // default to all threads
                ///M add for CMCC performance auto test case
                Log.i(TAG, "[CMCC Performance test][Message] delete message end [" + System.currentTimeMillis() + "]" + "threadId=" + threadId);
                if (threadId == -1) {
                    // Rebuild the contacts cache now that all threads and their associated unique
                    // recipients have been deleted.
                    Contact.init(ConversationList.this);
                    ///M: add for fix bug ALPS01071334. after delete all threads, refresh DraftCache
                    DraftCache.getInstance().refresh();
                }
                /// M: threadId == -2 is multidelete. for fix bug ALPS01071334
                else if (threadId != -2) {
                    // Remove any recipients referenced by this single thread from the
                    // contacts cache. It's possible for two or more threads to reference
                    // the same contact. That's ok if we remove it. We'll recreate that contact
                    // when we init all Conversations below.
                    Conversation conv = Conversation.get(ConversationList.this, threadId, false);
                    if (conv != null) {
                        ContactList recipients = conv.getRecipients();
                        for (Contact contact : recipients) {
                            contact.removeFromCache();
                        }
                    }
                }
                // Make sure the conversation cache reflects the threads in the DB.
                Conversation.init(ConversationList.this);
                if (mActionMode != null) {
                        mHandler.postDelayed(new Runnable() {
                            public void run() {
                                if (mActionMode != null && !isFinishing()) {
                                    mActionMode.finish();
                                }
                            }
                        }, 300);
                }

                try {
                    if (TelephonyManagerEx.getDefault().isTestIccCard(0)) {
                        MmsLog.d(CONV_TAG, "All threads has been deleted, send notification..");
                            SmsManager
                                    .getSmsManagerForSubscriptionId(
                                SmsReceiverService.sLastIncomingSmsSubId).getDefault().setSmsMemoryStatus(true);
                    }
                } catch (Exception ex) {
                    MmsLog.e(CONV_TAG, " " + ex.getMessage());
                }

                // Update the notification for new messages since they
                // may be deleted.
                MessagingNotification.nonBlockingUpdateNewMessageIndicator(ConversationList.this,
                        MessagingNotification.THREAD_NONE, false);
                // Update the notification for failed messages since they
                // may be deleted.
                MessagingNotification.nonBlockingUpdateSendFailedNotification(ConversationList.this);
                /// M: update download failed messages since they may be deleted too.
                MessagingNotification.updateDownloadFailedNotification(ConversationList.this);

                /// M: Code analyze 001, For new feature ALPS00131956,
                /// wappush: Update the notification for new WAP Push/CB
                /// messages. @{
                if (FeatureOption.MTK_WAPPUSH_SUPPORT) {
                    WapPushMessagingNotification.nonBlockingUpdateNewMessageIndicator(ConversationList.this,
                                                                                WapPushMessagingNotification.THREAD_NONE);
                }
                /// @}
                    /// M: Code analyze 006, For bug ALPS00291435, solve no
                    /// response while deleting 50 messages . @{
                CBMessagingNotification.updateNewMessageIndicator(ConversationList.this);
                    /// @}
                // Make sure the list reflects the delete
                /// M: comment this line
                // startAsyncQuery();.-
                /** M: fix bug ALPS00357750 @{ */
                dismissProgressDialog();
                /** @} */
                /** M: show a toast
                if (DeleteThreadListener.sDeleteNumber > 0) {
                    int count = DeleteThreadListener.sDeleteNumber;
                    String toastString = ConversationList.this.getResources().getQuantityString(
                            R.plurals.deleted_conversations, count, count);
                    Toast.makeText(ConversationList.this, toastString, Toast.LENGTH_SHORT).show();
                    DeleteThreadListener.sDeleteNumber = 0;
                }
                */
                /// M: google android4.2 patch
                MmsWidgetProvider.notifyDatasetChanged(getApplicationContext());
                /// @}
                break;

            case DELETE_OBSOLETE_THREADS_TOKEN:
                // Nothing to do here.
                MmsLog.d(TAG, "DraftManager.sEditingThread.clear()");
                DraftManager.sEditingThread.clear();
                break;
            }
        }
    }
    /// M: Code analyze 005, For new feature ALPS00247476, replace multichoicemode by longClickListener . @{
    private class ModeCallback implements ActionMode.Callback {
        private View mMultiSelectActionBarView;
        /// M:
			/*HQ_zhangjing add for al812 mms ui begin*/
			private TextView mSelectedConvCount;
        	//private MenuItem mDeleteItem;
			/*HQ_zhangjing add for al812 mms ui end*/
        private HashSet<Long> mSelectedThreadIds;


        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = getMenuInflater();
            /// M: Optimize select all performance, restore actionmode status. @{
            mListAdapter.clearstate();
            if (mIsNeedRestoreAdapterState) {
                for (int i = 0; i < mListSelectedThreads.length; i++) {
                    mListAdapter.setSelectedState(mListSelectedThreads[i]);
                }
                mIsNeedRestoreAdapterState = false;
            } else {
                Log.d(TAG, "onCreateActionMode: no need to restore adapter state");
            }
            /// @}
            mSelectedThreadIds = new HashSet<Long>();
				inflater.inflate(R.menu.conversation_multi_select_menu, menu);
				/*HQ_zhangjing add for al812 mms ui */
				deleteMenuView = menu.findItem(R.id.delete);
	
				if (mMultiSelectActionBarView == null) {
					mMultiSelectActionBarView = LayoutInflater.from(ConversationList.this)
						.inflate(R.layout.conversation_list_multi_select_actionbar, null);
	
					/*HQ_zhangjing add for al812 mms ui begin*/
					mSelectedConvCount =
						(TextView)mMultiSelectActionBarView.findViewById(R.id.selected_conv_count);
					/*HQ_zhangjing add for al812 mms ui end*/	
				}
				mode.setCustomView(mMultiSelectActionBarView);
				/*HQ_zhangjing add for al812 mms ui */
				((TextView)mMultiSelectActionBarView.findViewById(R.id.title))
					.setText(R.string.select_conversations);
            /// M: Code analyze 008, For bug ALPS00250948, disable search in
            // multi-select status . @{
            mDisableSearchFalg = true;
            /// @}
            /// M: Code analyze 005, For new feature ALPS00247476, set long clickable . @{
            getListView().setLongClickable(false);
			/*HQ_zhangjing add for al812 mms ui */
			mListAdapter.setMultiChoiceMode(true);
            /// @}
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            if (mMultiSelectActionBarView == null) {
                ViewGroup v = (ViewGroup)LayoutInflater.from(ConversationList.this)
                    .inflate(R.layout.conversation_list_multi_select_actionbar, null);
                mode.setCustomView(v);
		/*HQ_zhangjing add for al812 mms ui begin*/
		/*
                /// M: change select tips style
                mSelectionTitle = (Button) mMultiSelectActionBarView.findViewById(R.id.selection_menu);
                //mSelectedConvCount = (TextView)v.findViewById(R.id.selected_conv_count);

            }
            /// M: redesign selection action bar and add shortcut in common version. @{
            if (mCustomMenu == null) {
                mCustomMenu = new CustomMenu(ConversationList.this);
            }
            mSelectionMenu = mCustomMenu.addDropDownMenu(mSelectionTitle, R.menu.selection);
					mSelectedConvCount = (TextView)v.findViewById(R.id.selected_conv_count);
            mCustomMenu.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    if (mSelectionTitle != null) {
                        mSelectionTitle.setEnabled(false);
                    }
                    if (mListAdapter.getCount() == mListAdapter.getSelectedThreadsList().size()) {
                        setAllItemChecked(mActionMode, false);
                    } else {
                        setAllItemChecked(mActionMode, true);
                    }
                    return false;
                }
            });
			*/
					mSelectedConvCount = (TextView)v.findViewById(R.id.selected_conv_count);
				}
			
			/*HQ_zhangjing add for al812 mms ui end*/
            /// @}
            /// M:
            if (sConversationListOption == OPTION_CONVERSATION_LIST_SPAM) {
                MenuItem item = menu.findItem(R.id.mark_as_spam);
                if (item != null) {
                    item.setVisible(false);
                }
                item = menu.findItem(R.id.mark_as_nonspam);
                if (item != null) {
                    item.setVisible(true);
                }
            } else {
                MenuItem item = menu.findItem(R.id.mark_as_spam);
                if (item != null) {
                    if (!MmsConfig.isActivated(ConversationList.this)) {
                        item.setVisible(false);
                    } else {
                        item.setVisible(true);
                    }
                }
                item = menu.findItem(R.id.mark_as_nonspam);
                if (item != null) {
                    item.setVisible(false);
                }
            }
            return true;
        }

        @Override
        public boolean onActionItemClicked(final ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
				/*HQ_zhangjing add for al812 mms ui begin*/
				case R.id.select_all:
                    if (mListAdapter.getCount() == mListAdapter.getSelectedThreadsList().size()) {
                        setAllItemChecked(mActionMode, false);
                    } else {
                        setAllItemChecked(mActionMode, true);
						//item.setIcon(R.drawable.ic_menu_clear_select);
                    }
				break;
				/*HQ_zhangjing add for al812 mms ui end*/
                case R.id.delete:
                    if (mSelectedThreadIds.size() > 0) {
                        Log.v(TAG, "ConversationList->ModeCallback: delete");
                        if (mDeleteAlertDialog != null && mDeleteAlertDialog.isShowing()) {
                            MmsLog.d(TAG, "no need to show delete dialog");
                        } else {
                            confirmDeleteThreads(mSelectedThreadIds, mQueryHandler);
                        }
                    } else {
                        item.setEnabled(false);
                    }
                    break;

            case R.id.mark_as_spam:
                MmsLog.d(TAG, "click mark as spam!");
                final HashSet<Long> threadIds2 = (HashSet<Long>) mSelectedThreadIds.clone();
                OnClickListener listener = new OnClickListener() {
                    public void onClick(DialogInterface dialog, final int whichButton) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                int[] contactIds = new int[threadIds2.size()];
                                int i = 0;
                                for (Long threadId : threadIds2) {
                                    Conversation conversationForMarkSpam = Conversation.get(
                                        ConversationList.this, threadId.longValue(), false);
                                    String numbers = TextUtils.join(",",
                                                        conversationForMarkSpam.getRecipients().getNumbers());
                                    int contactId = IpMessageUtils.getContactManager(ConversationList.this)
                                        .getContactIdByNumber(numbers);
                                    contactIds[i] = contactId;
                                    i++;
                                    MmsLog.d(TAG, "threadId:" + threadId + ", contactId:" + contactId);
                                }
                                IpMessageUtils.getContactManager(ConversationList.this).addContactToSpamList(contactIds);
                            }
                        }).start();
                        mode.finish();
                    }
                };
                AlertDialog.Builder builder = new AlertDialog.Builder(ConversationList.this);
                builder.setTitle(R.string.mark_as_spam)
                    .setCancelable(true)
                    .setPositiveButton(
                            IpMessageUtils.getResourceManager(ConversationList.this)
                                            .getSingleString(IpMessageConsts.string.ipmsg_continue),
                            listener)
                    .setNegativeButton(R.string.Cancel, null)
                    .setMessage(IpMessageUtils.getResourceManager(ConversationList.this)
                        .getSingleString(IpMessageConsts.string.ipmsg_mark_as_spam_tips))
                    .show();
                break;
            case R.id.mark_as_nonspam:
                MmsLog.d(TAG, "click mark as nonspam!");
                final HashSet<Long> threadIds = (HashSet<Long>) mSelectedThreadIds.clone();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        int[] contactIds = new int[threadIds.size()];
                        int i = 0;
                        for (Long threadId : threadIds) {
                            Conversation conversationForMarkSpam = Conversation.get(
                                ConversationList.this, threadId.longValue(), false);
                            String numbers = TextUtils.join(",", conversationForMarkSpam.getRecipients().getNumbers());
                            int contactId = IpMessageUtils.getContactManager(ConversationList.this)
                                .getContactIdByNumber(numbers);
                            contactIds[i] = contactId;
                            i++;
                            MmsLog.d(TAG, "threadId:" + threadId + ", contactId:" + contactId);
                        }
                        IpMessageUtils.getContactManager(ConversationList.this).deleteContactFromSpamList(contactIds);
                    }
                }).start();
                mode.finish();
                break;
                default:
                    break;
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            /// M: Code analyze 008, For bug ALPS00250948, disable search in multi-select status . @{
            mDisableSearchFalg = false;
            /// @}
            mListAdapter.clearstate();
            /// M: Code analyze 005, For new feature ALPS00247476, add selectAll/unSelectAll . @{
            getListView().setLongClickable(true);
			/*HQ_zhangjing add for al812 mms ui begin*/
            //mSelectionMenu.dismiss();
			mListAdapter.uncheckAll();
			mSelectedThreadIds = null;
			mListAdapter.setMultiChoiceMode(false);
			/*HQ_zhangjing add for al812 mms ui end*/
			
            mActionMode = null;
            if (mListAdapter != null) {
                mListAdapter.notifyDataSetChanged();
            }
            /// @}
        }

        public void setItemChecked(int position, boolean checked, Cursor cursor) {
            ListView listView = getListView();
            if (cursor == null) {
                cursor = (Cursor) listView.getItemAtPosition(position);
            } else {
                cursor.moveToPosition(position);
            }
            long threadId = cursor.getLong(0);
            boolean isChecked = mListAdapter.isContainThreadId(threadId);
            if (checked == isChecked) {
                return;
            }
			
            if (checked) {
                mListAdapter.setSelectedState(threadId);
            } else {
                mListAdapter.removeSelectedState(threadId);
            }
			
			//Conversation conv = Conversation.from(ConversationList.this, cursor);
			//conv.setIsChecked(checked);//have some problem
			
        }
        /// @}

        private void updateActionMode() {
            mSelectedThreadIds = mListAdapter.getSelectedThreadsList();
            int checkedNum = mSelectedThreadIds.size();
            /// M: Code analyze 018, For bug, enable or diable mDeleteItem menu . @{
            /*HQ_zhangjing add for al812 mms ui begin*/
			if (deleteMenuView != null) {
                if (checkedNum > 0) {
                    deleteMenuView.setEnabled(true);
                } else {
                    deleteMenuView.setEnabled(false);
                }
                /// @}
			/*HQ_zhangjing add for al812 mms ui end*/
            }
            /// M: exit select mode if no item select
            if (checkedNum <= 0 && mActionMode != null) {
                mActionMode.finish();
                ///M: add for fix ALPS01448613, when checkedNum == 0, dismiss the deleteAlertDialog. @{
                if (mDeleteAlertDialog != null && mDeleteAlertDialog.isShowing()) {
                    mDeleteAlertDialog.dismiss();
                    mDeleteAlertDialog = null;
                }
                /// @}
            }
            if (mActionMode != null) {
                mActionMode.invalidate();
            }
			/*HQ_zhangjing add for al812 mms ui begin*/
			mSelectedConvCount.setText(Integer.toString(checkedNum));
				((TextView)mMultiSelectActionBarView.findViewById(R.id.title))
				.setText(getString(R.string.select_conversations));
			
			
            //updateSelectionTitle();
			/*HQ_zhangjing add for al812 mms ui end*/
        }

        /// M: Code analyze 005, For new feature ALPS00247476, select all messages . @{
        private void setAllItemChecked(ActionMode mode, final boolean checked) {
            mListAdapter.clearstate();
            if (checked) {
                deleteMenuView.setEnabled(false);
            }

            Cursor cursor = null;
            String selection = null;
            // / M: ipmessage query.
            if (checked) {
                if (MmsConfig.isActivated(ConversationList.this)) {
                    switch (sConversationListOption) {
                        case OPTION_CONVERSATION_LIST_ALL:
                            MmsLog.d(TAG, "setAllItemChecked(): query for all messages except spam");
                            selection = "threads._id not in (SELECT DISTINCT " + Sms.THREAD_ID
                                    + " FROM thread_settings WHERE spam=1) ";
                            cursor = getContext().getContentResolver().query(
                                    Conversation.sAllThreadsUriExtend,
                                    Conversation.ALL_THREADS_PROJECTION_EXTEND, selection, null,
                                    Conversations.DEFAULT_SORT_ORDER);
                            break;
                        case OPTION_CONVERSATION_LIST_GROUP_CHATS:
                            MmsLog.d(TAG, "setAllItemChecked(): query for group messages");
                            selection = "threads._id IN (SELECT DISTINCT "
                                    + Sms.THREAD_ID
                                    + " FROM thread_settings WHERE spam=0)"
                                    + " AND threads.recipient_ids IN (SELECT _id FROM canonical_addresses"
                                    + " WHERE " + "SUBSTR(address, 1, 4) = '"
                                    + IpMessageConsts.GROUP_START + "'" + ")";
                            cursor = getContext().getContentResolver().query(
                                    Conversation.sAllThreadsUriExtend,
                                    Conversation.ALL_THREADS_PROJECTION_EXTEND, selection, null,
                                    Conversations.DEFAULT_SORT_ORDER);
                            break;
                        case OPTION_CONVERSATION_LIST_SPAM:
                            // selection = Threads.SPAM +
                            // "=1 OR _ID in (SELECT DISTINCT " + Sms.THREAD_ID +
                            // " FROM sms WHERE "
                            // + Sms.SPAM + "=1) ";
                            selection = "threads._id IN (SELECT DISTINCT " + Sms.THREAD_ID
                                    + " FROM thread_settings WHERE spam=1) ";
                            MmsLog.d(TAG, "setAllItemChecked(): query for spam messages, selection = "
                                    + selection);
                            cursor = getContext().getContentResolver().query(
                                    Conversation.sAllThreadsUriExtend,
                                    Conversation.ALL_THREADS_PROJECTION_EXTEND, selection, null,
                                    Conversations.DEFAULT_SORT_ORDER);
                            break;
                        case OPTION_CONVERSATION_LIST_JOYN:
                            selection = "threads._id IN (SELECT DISTINCT " + Sms.THREAD_ID
                            + " FROM thread_settings WHERE spam=0)"
                            + " AND threads.recipient_ids IN (SELECT _id FROM canonical_addresses" + " WHERE "
                            + "SUBSTR(address, 1, 4) = '" + IpMessageConsts.JOYN_START + "'" + ")";
                            MmsLog.d(TAG, "setAllItemChecked(): query for joyn messages, selection = "
                                    + selection);
                            cursor = getContext().getContentResolver().query(
                                    Conversation.sAllThreadsUriExtend,
                                    Conversation.ALL_THREADS_PROJECTION_EXTEND, selection, null,
                                    Conversations.DEFAULT_SORT_ORDER);
                            break;
                        case OPTION_CONVERSATION_LIST_XMS:
                            selection = "threads._id IN (SELECT DISTINCT " + Sms.THREAD_ID
                            + " FROM thread_settings WHERE spam=0)"
                            + " AND threads.recipient_ids NOT IN (SELECT _id FROM canonical_addresses" + " WHERE "
                            + "SUBSTR(address, 1, 4) = '" + IpMessageConsts.JOYN_START + "'" + ")";
                            MmsLog.d(TAG, "setAllItemChecked(): query for xms messages, selection = "
                                    + selection);
                            cursor = getContext().getContentResolver().query(
                                    Conversation.sAllThreadsUriExtend,
                                    Conversation.ALL_THREADS_PROJECTION_EXTEND, selection, null,
                                    Conversations.DEFAULT_SORT_ORDER);
                            break;
                        default:
                            MmsLog.d(TAG, "status error! not at any type.");
                            break;
                    }
                } else {
                    cursor = getContext().getContentResolver().query(Conversation.sAllThreadsUriExtend,
                            Conversation.ALL_THREADS_PROJECTION_EXTEND, null, null,
                            Conversations.DEFAULT_SORT_ORDER);
                }
                try {
                    if (cursor != null) {
                        MmsLog.d(TAG, "select all, cursor count is " + cursor.getCount());
                        for (int position = 0; position < cursor.getCount(); position++) {
                            setItemChecked(position, checked, cursor);
                        }
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }

            updateActionMode();
            // / M: Code analyze 018, For bug, enable or diable
            // mDeleteItem menu . @{
            if (checked) {
				/*HQ_zhangjing add for al812 mms ui begin*/
                deleteMenuView.setEnabled(true);
            } else {
				/*HQ_zhangjing add for al812 mms ui begin*/
                deleteMenuView.setEnabled(false);
            }
            // / @}

            if (mListAdapter != null) {
                mListAdapter.notifyDataSetChanged();
            }
        }
        /// @}

    }

    private void log(String format, Object... args) {
        String s = String.format(format, args);
        Log.d(TAG, "[" + Thread.currentThread().getId() + "] " + s);
    }

    private boolean mIsShowSIMIndicator = true;
    /// M: Code analyze 003, For new feature ALPS00242732, SIM indicator UI is not good . @{
    @Override
    public void onSubInforChanged() {
        MmsLog.i(MmsApp.LOG_TAG, "onSimInforChanged(): Conversation List");
        /// M: show SMS indicator
        if (!isFinishing() && mIsShowSIMIndicator) {
            MmsLog.i(MmsApp.LOG_TAG, "Hide current indicator and show new one.");
//            mStatusBarManager.hideSIMIndicator(getComponentName());
//            mStatusBarManager.showSIMIndicator(getComponentName(), Settings.System.SMS_SIM_SETTING);
            StatusBarSelectorCreator creator = StatusBarSelectorCreator
                    .getInstance(ConversationList.this);
            creator.updateStatusBarData();

        }
    }
    /// @}

    /// M: Code analyze 009, For bug ALPS00270910, Default SIM card icon shown
    /// in status bar is incorrect, need to get current sim information . @{
    public static Activity getContext() {
        return sActivity;
    }
    /// @}

    /// M: Code analyze 005, For new feature ALPS00247476, long click Listenner . @{
    class ItemLongClickListener implements  ListView.OnItemLongClickListener {

        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            mActionMode = startActionMode(mActionModeListener);
            Log.e(TAG, "OnItemLongClickListener");
            mActionModeListener.setItemChecked(position, true, null);
            mActionModeListener.updateActionMode();
            if (mListAdapter != null) {
                mListAdapter.notifyDataSetChanged();
            }
            return true;
        }
    }
    /// @}

    private Runnable mResumeRunnable = new Runnable() {
        @Override
        public void run() {
            // / M: Code analyze 003, For new feature ALPS00242732, SIM
            // indicator UI is not good . @{
            final ComponentName name = getComponentName();
            if (UserHandle.myUserId() == UserHandle.USER_OWNER) {
                mIsShowSIMIndicator = true;
                //mStatusBarManager.hideSIMIndicator(name);
                //mStatusBarManager.showSIMIndicator(name, Settings.System.SMS_SIM_SETTING);
                StatusBarSelectorCreator.getInstance(ConversationList.this).showStatusBar();
            }
            // / @}
            // / M: add for application guide. @{
            IAppGuideExt appGuideExt = (IAppGuideExt) MmsPluginManager
                    .getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_APPLICATION_GUIDE);
            appGuideExt.showAppGuide("MMS");
            // / @}
            mIsFirstCreate = false;
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        PDebug.EndAndStart("onStart -> onResume", "ConversationList.onResume");

        // add for multi user
        isUserHasPerUsingMms = !UserManager.get(getApplicationContext()).hasUserRestriction(
                UserManager.DISALLOW_SMS);

        boolean isSmsEnabled = MmsConfig.isSmsEnabled(this);
        if (isSmsEnabled != mIsSmsEnabled) {
            mIsSmsEnabled = isSmsEnabled;
            if (!mIsFirstCreate) {
                invalidateOptionsMenu();
            }
        }
        // Multi-select is used to delete conversations. It is disabled if we are not the sms app.
        ListView listView = getListView();
        if (mIsSmsEnabled) {
            if (listView.getOnItemLongClickListener() == null) {
                listView.setOnItemLongClickListener(new ItemLongClickListener());
            }
        } else {
            listView.setOnItemLongClickListener(null);
            if (mActionMode != null) {
                mActionMode.finish();
                if (mDeleteAlertDialog != null && mDeleteAlertDialog.isShowing()) {
                    mDeleteAlertDialog.dismiss();
                    mDeleteAlertDialog = null;
                }
            }
        }

        // Show or hide the SMS promo banner
        if (mIsSmsEnabled || MmsConfig.isSmsPromoDismissed(this)) {
            mSmsPromoBannerView.setVisibility(View.GONE);
        } else {
            initSmsPromoBanner();
            mSmsPromoBannerView.setVisibility(View.VISIBLE);
        }

        ComposeMessageActivity.mDestroy = true;
        mHandler.postDelayed(mResumeRunnable, 400);
        PDebug.End("ConversationList.onResume");
        // M: add for ALPS01846474, dismiss option menu if airplane mode changed.
        registerReceiver(mAirPlaneReceiver, new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED));
    }

    /// M: Code analyze 001, For new feature ALPS00242732, SIM indicator UI is not good . @{
    @Override
    protected void onPause() {
        mHandler.removeCallbacks(mResumeRunnable);
//        mStatusBarManager.hideSIMIndicator(getComponentName());
        StatusBarSelectorCreator.getInstance(ConversationList.this).hideStatusBar();
        mIsShowSIMIndicator = false;
        super.onPause();

        /// Google JB MR1.1 patch. conversation list can restore scroll position
        // Remember where the list is scrolled to so we can restore the scroll position
        // when we come back to this activity and *after* we complete querying for the
        // conversations.
        ListView listView = getListView();
        mSavedFirstVisiblePosition = listView.getFirstVisiblePosition();
        View firstChild = listView.getChildAt(0);
        mSavedFirstItemOffset = (firstChild == null) ? 0 : firstChild.getTop();
        // M: add for ALPS01846474, dismiss option menu if airplane mode changed.
        unregisterReceiver(mAirPlaneReceiver);
    }
    /// @}

    @Override
    protected void onDestroy() {
        /// M: Remove not start queries, and close the last cursor hold be adapter@{
        MmsLog.d(TAG, "onDestroy");
        if (mQueryHandler != null) {
            mQueryHandler.removeCallbacksAndMessages(null);
            mQueryHandler.cancelOperation(THREAD_LIST_QUERY_TOKEN);
            mQueryHandler.cancelOperation(UNREAD_THREADS_QUERY_TOKEN);
        }
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        if (mListAdapter != null) {
            MmsLog.d(TAG, "clear it");
            mListAdapter.setOnContentChangedListener(null);
            mListAdapter.changeCursor(null);
        }
        /// @}

        /// M: Code analyze 004, For bug ALPS00247476, ensure the scroll smooth . @{
        mScrollListener.destroyThread();
        /// @}

        /// M: add for ipmessage
        if (mNetworkStateReceiver != null) {
            unregisterReceiver(mNetworkStateReceiver);
            mNetworkStateReceiver = null;
        }
        if (MmsConfig.isServiceEnabled(this)) {
            IpMessageUtils.removeIpMsgNotificationListeners(this, this);
        }

        if (mActionMode != null) {
            mListAdapter.clearstate();
            mActionMode = null;
        }
        mDeleteAlertDialog = null;
        unregisterReceiver(mSubReceiver);
        unregisterReceiver(mStatusBarSelectorReceiver);
        super.onDestroy();
    }

    /// M: fix ALPS01535674,ALPS01597191. don't dimiss action mode when back from ComposeMessageActivity. @{
    private boolean mBackKeyisDown = false;
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {

        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_BACK && !mBackKeyisDown) {
            MmsLog.d(TAG, "ignore action_up");
            mBackKeyisDown = false;
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            mBackKeyisDown = true;
        }
        return super.dispatchKeyEvent(event);
    }
    /// @}

    /// M: Code analyze 008, For bug ALPS00250948, disable search in
    /// multi-select status . @{
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mDisableSearchFalg) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_SEARCH:
                    // do nothing since we don't want search box which may cause UI crash
                    // TODO: mapping to other useful feature
                    return true;
                default:
                    break;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
      /// @}

    /// M: Code analyze 020, For bug ALPS00050455, enhance the performance of
    /// launch time . @{
    @Override
    public void onBackPressed() {
        if (isTaskRoot()) {
            /** M: Instead of stopping, simply push this to the back of the stack.
             * This is only done when running at the top of the stack;
             * otherwise, we have been launched by someone else so need to
             * allow the user to go back to the caller.
             */
            if (mSearchItem != null && mSearchItem.isActionViewExpanded()) {
                mSearchItem.collapseActionView();
            } else if (FeatureOption.MTK_PERF_RESPONSE_TIME) {
                super.onBackPressed();
            } else {
                moveTaskToBack(false);
            }
        } else {
            super.onBackPressed();
        }
    }

    /// @}

    /// M: Code analyze 009, For new feature, plugin . @{
    private void initPlugin(Context context) {
        mMmsConversationPlugin = (IMmsConversationExt) MmsPluginManager
        .getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_MMS_CONV);
        mMmsConversationPlugin.init(this);
    }

    /// @}

    /// M: Code analyze 013, For bug ALPS00046358 , The base class about the
    /// handler with progress dialog functio . @{
    public abstract static class BaseProgressQueryHandler extends AsyncQueryHandler {
        private NewProgressDialog mDialog;
        private int mProgress;

        public BaseProgressQueryHandler(ContentResolver resolver) {
            super(resolver);
        }

        /** M:
         * Sets the progress dialog.
         * @param dialog the progress dialog.
         */
        public void setProgressDialog(NewProgressDialog dialog) {
            // Patch back ALPS00457128 which the "deleting" progress display for a long time
            if (mDialog == null) {
                mDialog = dialog;
            }
        }

        /** M:
         * Sets the max progress.
         * @param max the max progress.
         */
        public void setMax(int max) {
            if (mDialog != null) {
                mDialog.setMax(max);
            }
        }

        /** M:
         * Shows the progress dialog. Must be in UI thread.
         */
        public void showProgressDialog() {
            if (mDialog != null) {
                mDialog.show();
            }
        }

        /** M:
         * Rolls the progress as + 1.
         * @return if progress >= max.
         */
        protected boolean progress() {
            if (mDialog != null) {
                return ++mProgress >= mDialog.getMax();
            } else {
                return false;
            }
        }

        /** M: fix bug ALPS00351620
         * Dismisses the progress dialog.
         */
        protected void dismissProgressDialog() {
            // M: fix bug ALPS00357750
            if (mDialog == null) {
                MmsLog.e(TAG, "mDialog is null!");
                return;
            }

            mDialog.setDismiss(true);
            try {
                mDialog.dismiss();
            } catch (IllegalArgumentException e) {
                // if parent activity is destroyed,and code come here, will happen this.
                // just catch it.
                MmsLog.d(TAG, "ignore IllegalArgumentException");
            }
            mDialog = null;
        }
    }

    /// @}

    /// M: Code analyze 009, For new feature, plugin . @{
    public void showSimSms() {
        int subCount = SubscriptionManager.from(this).getActiveSubscriptionInfoCount();
        if (subCount > 1) {
            Intent simSmsIntent = new Intent();
            simSmsIntent.setClass(this, SubSelectActivity.class);
            simSmsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            simSmsIntent.putExtra(SmsPreferenceActivity.PREFERENCE_KEY,
                    SettingListActivity.SMS_MANAGE_SIM_MESSAGES);
            simSmsIntent.putExtra(SmsPreferenceActivity.PREFERENCE_TITLE_ID,
                    R.string.pref_title_manage_sim_messages);
            startActivity(simSmsIntent);
        } else if (subCount == 1) {
            Intent simSmsIntent = new Intent();
            simSmsIntent.setClass(this, ManageSimMessages.class);
            simSmsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            simSmsIntent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, SubscriptionManager
                    .from(MmsApp.getApplication()).getActiveSubscriptionInfoList().get(0)
                    .getSubscriptionId());
            startActivity(simSmsIntent);
        } else {
            Toast.makeText(ConversationList.this, R.string.no_sim_1, Toast.LENGTH_SHORT).show();
        } 
    }

    public void changeMode() {
        MmsConfig.setMmsDirMode(true);
        MessageUtils.updateNotification(this);
        Intent it = new Intent(this, FolderViewList.class);
        it.putExtra("floderview_key", FolderViewList.OPTION_INBOX); // show inbox by default
        startActivity(it);
        finish();
    }
    /// @}

    private void setupActionBar2() {
        ActionBar actionBar = getActionBar();

        ViewGroup v = (ViewGroup) LayoutInflater.from(this)
                .inflate(R.layout.conversation_list_actionbar2, null);
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM, ActionBar.DISPLAY_SHOW_CUSTOM);
        actionBar.setCustomView(v,
                new ActionBar.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT,
                        ActionBar.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_VERTICAL | Gravity.LEFT));

        mUnreadConvCount = (TextView) v.findViewById(R.id.unread_conv_count);
        mSpinnerTextView = (TextView) v.findViewById(R.id.conversation_list_name);
        mConversationSpinner = (View) v.findViewById(R.id.conversation_list_spinner);
        View unreadConvCountLayout = v.findViewById(R.id.unread_layout);
        unreadConvCountLayout.setVisibility(View.VISIBLE);
        if (MmsConfig.isActivated(this)) {
            mSpinnerTextView.setText(IpMessageUtils.getResourceManager(this)
                .getSingleString(IpMessageConsts.string.ipmsg_conversation_list_all));
            mConversationSpinner.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mDropdownAdapter.getCount() > 0) {
                        mAccountDropdown.show();
                    }
                }
            });
        } else {
            // hide views if no plugin exist
            mSpinnerTextView.setVisibility(View.GONE);
            mConversationSpinner.setVisibility(View.GONE);
        }
    }

    // / M: add for ipmessage: spam, group chats {@
    private void initSpinnerListAdapter() {
        mDropdownAdapter = new ArrayAdapter<String>(this, R.layout.conversation_list_title_drop_down_item,
                R.id.drop_down_menu_text, new ArrayList<String>());
        mDropdownAdapter = getDropDownMenuData(mDropdownAdapter, OPTION_CONVERSATION_LIST_ALL);
        setupActionBar2();

        mAccountDropdown = new AccountDropdownPopup(mContext);
        mAccountDropdown.setAdapter(mDropdownAdapter);
   }

    private ArrayAdapter<String> getDropDownMenuData(ArrayAdapter<String> adapter, int dropdownStatus) {
        if (null == adapter) {
            return null;
        }
        mDropdownAdapter.clear();

        Resources res = getResources();
        if (dropdownStatus != OPTION_CONVERSATION_LIST_ALL) {
            adapter.add(IpMessageUtils.getResourceManager(this)
                .getSingleString(IpMessageConsts.string.ipmsg_conversation_list_all));
        }

        if (dropdownStatus != OPTION_CONVERSATION_LIST_GROUP_CHATS) {
            adapter.add(IpMessageUtils.getResourceManager(this)
                .getSingleString(IpMessageConsts.string.ipmsg_conversation_list_group_chats));
        }

        if (dropdownStatus != OPTION_CONVERSATION_LIST_SPAM) {
            adapter.add(IpMessageUtils.getResourceManager(this)
                .getSingleString(IpMessageConsts.string.ipmsg_conversation_list_spam));
        }

        if (IpMessageUtils.getServiceManager(mContext).getIntegrationMode() == IpMessageConsts.IntegrationMode.CONVERGED_INBOX) {
            if (dropdownStatus != OPTION_CONVERSATION_LIST_JOYN) {
                adapter.add(IpMessageUtils.getResourceManager(this)
                        .getSingleString(IpMessageConsts.string.ipmsg_conversation_list_joyn));
            }

            if (dropdownStatus != OPTION_CONVERSATION_LIST_XMS) {
                adapter.add(IpMessageUtils.getResourceManager(this)
                        .getSingleString(IpMessageConsts.string.ipmsg_conversation_list_xms));
            }
        }
        return adapter;
    }

    // Based on Spinner.DropdownPopup
    private class AccountDropdownPopup extends ListPopupWindow {
        public AccountDropdownPopup(Context context) {
            super(context);
            setAnchorView(mConversationSpinner);
            setModal(true);
            // Add for fix pop window width not match every device issue
            int width = mContext.getResources().getDimensionPixelSize(R.dimen.popup_min_width);
            setWidth(width);

            setOnItemClickListener(new OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                    onAccountSpinnerItemClicked(position);
                    dismiss();
                }
            });
        }

        @Override
        public void show() {
            setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
            super.show();
            // List view is instantiated in super.show(), so we need to do this after...
            getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        }
    }

    private void onAccountSpinnerItemClicked(int position) {
        switch (sConversationListOption) {
        case OPTION_CONVERSATION_LIST_ALL:
            position++;
            break;
        case OPTION_CONVERSATION_LIST_GROUP_CHATS:
            if (position > 0) {
                position++;
            }
            break;
        case OPTION_CONVERSATION_LIST_SPAM:
            if (position > 1) {
                position++;
            }
            break;
        case OPTION_CONVERSATION_LIST_JOYN:
            if (position > 2) {
                position++;
            }
            break;
        case OPTION_CONVERSATION_LIST_XMS:
            if (position > 1) {
                //position++;
            }
            break;
        default:
            break;
        }
        switch (position) {
            case OPTION_CONVERSATION_LIST_ALL:
                sConversationListOption = OPTION_CONVERSATION_LIST_ALL;
                mSpinnerTextView.setText(IpMessageUtils.getResourceManager(this)
                    .getSingleString(IpMessageConsts.string.ipmsg_conversation_list_all));
                mDropdownAdapter = getDropDownMenuData(mDropdownAdapter, sConversationListOption);
                mDropdownAdapter.notifyDataSetChanged();
                break;
            case OPTION_CONVERSATION_LIST_GROUP_CHATS:
                sConversationListOption = OPTION_CONVERSATION_LIST_GROUP_CHATS;
                mSpinnerTextView.setText(IpMessageUtils.getResourceManager(this)
                    .getSingleString(IpMessageConsts.string.ipmsg_conversation_list_group_chats));
                mDropdownAdapter = getDropDownMenuData(mDropdownAdapter, sConversationListOption);
                mDropdownAdapter.notifyDataSetChanged();
                break;
            case OPTION_CONVERSATION_LIST_SPAM:
                sConversationListOption = OPTION_CONVERSATION_LIST_SPAM;
                mSpinnerTextView.setText(IpMessageUtils.getResourceManager(this)
                    .getSingleString(IpMessageConsts.string.ipmsg_conversation_list_spam));
                mDropdownAdapter = getDropDownMenuData(mDropdownAdapter, sConversationListOption);
                mDropdownAdapter.notifyDataSetChanged();
                break;
            case OPTION_CONVERSATION_LIST_JOYN:
                sConversationListOption = OPTION_CONVERSATION_LIST_JOYN;
                mSpinnerTextView.setText(IpMessageUtils.getResourceManager(this)
                        .getSingleString(IpMessageConsts.string.ipmsg_conversation_list_joyn));
                mDropdownAdapter = getDropDownMenuData(mDropdownAdapter, sConversationListOption);
                mDropdownAdapter.notifyDataSetChanged();
                break;
            case OPTION_CONVERSATION_LIST_XMS:
                sConversationListOption = OPTION_CONVERSATION_LIST_XMS;
                mSpinnerTextView.setText(IpMessageUtils.getResourceManager(this)
                        .getSingleString(IpMessageConsts.string.ipmsg_conversation_list_xms));
                mDropdownAdapter = getDropDownMenuData(mDropdownAdapter, sConversationListOption);
                mDropdownAdapter.notifyDataSetChanged();
                break;
            default:
                break;
        }
        startAsyncQuery();
        invalidateOptionsMenu();
    }
    /// @}
    private static final int REQUEST_CODE_SELECT_CONTACT_FOR_GROUP = 100;
    private static final int REQUEST_CODE_INVITE = 101;
    private static final String KEY_SELECTION_SIMID = "SIMID";

    /// M: add for ipmessage
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        MmsLog.d(IPMSG_TAG, "onActivityResult(): requestCode=" + requestCode + ", resultCode=" + resultCode);
        if (resultCode != RESULT_OK) {
            MmsLog.d(IPMSG_TAG, "onActivityResult(): result is not OK.");
            return;
        }
        switch (requestCode) {
        case REQUEST_CODE_SELECT_CONTACT_FOR_GROUP:
            String[] mSelectContactsIds = data.getStringArrayExtra(IpMessageUtils.SELECTION_CONTACT_RESULT);
            if (mSelectContactsIds != null) {
                for (String contactId : mSelectContactsIds) {
                    MmsLog.d(IPMSG_TAG, "onActivityResult(): SELECT_CONTACT get contact id = " + contactId);
                }
                Intent intent = new Intent(RemoteActivities.NEW_GROUP_CHAT);
                intent.putExtra(RemoteActivities.KEY_SIM_ID, data.getIntExtra(KEY_SELECTION_SIMID, 0));
                intent.putExtra(RemoteActivities.KEY_ARRAY, mSelectContactsIds);
                IpMessageUtils.startRemoteActivity(this, intent);
                mSelectContactsIds = null;
            } else {
                MmsLog.d(IPMSG_TAG, "onActivityResult(): SELECT_CONTACT get contact id is NULL!");
            }
            break;
        case REQUEST_CODE_INVITE:
            final String mSelectContactsNumbers = data.getStringExtra(IpMessageUtils.SELECTION_CONTACT_RESULT);

            if (mSelectContactsNumbers != null) {
                MmsLog.d(IPMSG_TAG, "mSelectContactsNumbers:" + mSelectContactsNumbers);
                StringBuilder numberString = new StringBuilder();
                Intent it = new Intent(this, ComposeMessageActivity.class);
                it.setAction(Intent.ACTION_SENDTO);
                Uri uri = Uri.parse("smsto:" + mSelectContactsNumbers);
                it.setData(uri);
                it.putExtra("sms_body",
                            IpMessageUtils.getResourceManager(this)
                                        .getSingleString(IpMessageConsts.string.ipmsg_invite_friends_content));
                startActivity(it);
            } else {
                MmsLog.d(IPMSG_TAG, "onActivityResult(): INVITE get contact id is NULL!");
            }
            break;
        default:
            MmsLog.d(IPMSG_TAG, "onActivityResult(): default return.");
            return;
        }
    }

    private void openIpMsgThread(final long threadId) {
        Intent intent = new Intent(RemoteActivities.CHAT_DETAILS_BY_THREAD_ID);
        intent.putExtra(RemoteActivities.KEY_THREAD_ID, threadId);
        intent.putExtra(RemoteActivities.KEY_NEED_NEW_TASK, false);
        IpMessageUtils.startRemoteActivity(this, intent);
    }

    @Override
    public void notificationsReceived(Intent intent) {
        MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "convList.notificationsReceived(): start, intent = " + intent);
        String action = intent.getAction();
        MmsLog.d(TAG, "IpMessageUtils.getActionTypeByAction(action) is " + IpMessageUtils.getActionTypeByAction(action));
        if (TextUtils.isEmpty(action)) {
            return;
        }
        switch (IpMessageUtils.getActionTypeByAction(action)) {
        /// M: add for ipmessage register toast @{
        case IpMessageUtils.IPMSG_REG_STATUS_ACTION:
            int regStatus = intent.getIntExtra(IpMessageConsts.RegStatus.REGSTATUS, 0);
            switch (regStatus) {
            case IpMessageConsts.RegStatus.REG_OVER:
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(
                                getApplicationContext(),
                                IpMessageUtils.getResourceManager(ConversationList.this)
                                        .getSingleString(
                                                IpMessageConsts.string.ipmsg_nms_enable_success),
                                Toast.LENGTH_SHORT).show();
                    }
                });
                break;

            default:
                break;

            }
            break;
        /// @}
        case IpMessageUtils.IPMSG_ERROR_ACTION:
            // do nothing
            return;
        case IpMessageUtils.IPMSG_NEW_MESSAGE_ACTION:
//            public static final String IP_MESSAGE_KEY = "IpMessageKey";
            break;
        case IpMessageUtils.IPMSG_REFRESH_CONTACT_LIST_ACTION:
            break;
        case IpMessageUtils.IPMSG_REFRESH_GROUP_LIST_ACTION:
            break;
        case IpMessageUtils.IPMSG_SERCIVE_STATUS_ACTION:
//            public static final int ON  = 1;
//            public static final int OFF = 0;
            break;
        case IpMessageUtils.IPMSG_IM_STATUS_ACTION:
            /** M: show typing feature is off for performance issue now.
            String number = intent.getStringExtra(IpMessageConsts.NUMBER);
            int status = IpMessageUtils.getContactManager(this).getStatusByNumber(number);
            MmsLog.d(TAG, "notificationsReceived(): IM status. number = " + number
                + ", status = " + status);
            if (mTypingCounter > 10) {
                return;
            }
            ContactList contact = new ContactList();
            contact.add(Contact.get(number, false));
            Conversation conv = Conversation.getCached(this, contact);
            if (conv == null) {
                MmsLog.w(TAG, "the number is not in conversation cache!");
                return;
            }
            //long threadId = conv.getThreadId();
            //MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "number query threadId:" + threadId);
            switch (status) {
            case ContactStatus.TYPING:
                conv.setTyping(true);
                MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "start typing");
                mTypingCounter++;
                runOnUiThread(new Runnable() {
                    public void run() {
                        mListAdapter.notifyDataSetChanged();
                    }
                });
                break;
            case ContactStatus.STOP_TYPING:
                conv.setTyping(false);
                MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "stop typing");
                mTypingCounter--;
                runOnUiThread(new Runnable() {
                    public void run() {
                        mListAdapter.notifyDataSetChanged();
                    }
                });
                break;
            default:
                MmsLog.d(IpMessageUtils.IPMSG_NOTIFICATION_TAG, "ignore a status:" + status);
                break;
            }
            */
            break;

        case IpMessageUtils.IPMSG_ACTIVATION_STATUS_ACTION:
            break;
        case IpMessageUtils.IPMSG_IP_MESSAGE_STATUS_ACTION:
            // handle this notification in MessageListItem
            break;
        case IpMessageUtils.IPMSG_DOWNLOAD_ATTACH_STATUS_ACTION:
            // handle this notification in MessageListItem
            break;
        case IpMessageUtils.IPMSG_SET_PROFILE_RESULT_ACTION:
            break;
        case IpMessageUtils.IPMSG_BACKUP_MSG_STATUS_ACTION:
            break;
        case IpMessageUtils.IPMSG_RESTORE_MSG_STATUS_ACTION:
            break;
        case IpMessageUtils.IPMSG_UPDATE_GROUP_INFO:
            int groupId = intent.getIntExtra(IpMessageConsts.UpdateGroup.GROUP_ID, -1);
            MmsLog.d(TAG, "update group info,group id:" + groupId);
            String number = IpMessageUtils.getContactManager(this).getNumberByEngineId((short) groupId);
            MmsLog.d(TAG, "group number:" + number);
            Contact contact = Contact.get(number, false);
            if (contact != null) {
                contact.setName(null);
                contact.clearAvatar();
            }
            runOnUiThread(new Runnable() {
                public void run() {
                    if (mListAdapter != null) {
                        mListAdapter.notifyDataSetChanged();
                    }
                }
            });
            break;
        case IpMessageUtils.IPMSG_IPMESSAGE_CONTACT_UPDATE:
            /** M: ipmessage plugin send this event when
             *  1. system contact info is changed, we may need update group avatar
             *  2. self head icon is changed, we may need update group avatar
             *  3. a ipmessage head icon is updated,  need update avatar
             *  if a system contact avatar is updated, and it is in a group.
             *  we will not receive a IPMSG_UPDATE_GROUP_INFO event,
             *  so we need invalid the group avatar cache and re-fetch it.
             */
            Contact.invalidateGroupCache();
            runOnUiThread(new Runnable() {
                public void run() {
                    if (mListAdapter != null) {
                        mListAdapter.notifyDataSetChanged();
                    }
                }
            });
            break;
        case IpMessageUtils.IPMSG_SIM_INFO_ACTION:
            /// M: for a special case, boot up enter mms quickly may be not get right status.
            if (MmsConfig.isActivated(this)) {
                /// M: init ipmessage view
                runOnUiThread(new Runnable() {
                    public void run() {
                        Conversation.setActivated(true);
                        initSpinnerListAdapter();
                        setTitle("");
                        mEmptyViewDefault.setVisibility(View.GONE);
                        mEmptyView.setVisibility(View.VISIBLE);
                        mListView.setEmptyView(mEmptyView);
                        invalidateOptionsMenu();
                    }
                });
            } else {
                MmsLog.d(TAG, "normal message layout");
                runOnUiThread(new Runnable() {
                    public void run() {
                        setupActionBar();
                        setTitle(R.string.app_label);
                    }
                });
            }
            break;
        // Add for joyn
        case IpMessageUtils.IPMSG_DISABLE_SERVICE_STATUS_ACTION:
            mIsJoynChanged = true;
            break;
        default:
            break;
        }
    }

    private void  updateEmptyView(Cursor cursor) {
        MmsLog.d(TAG, "active:" + MmsConfig.isActivated(this));
        MmsLog.d(TAG, "cursor count:" + cursor.getCount());
        if (MmsConfig.isActivated(this) && (cursor != null) && (cursor.getCount() == 0)) {
            // when there is no items, show a view
            MmsLog.d(TAG, "sConversationListOption:" + sConversationListOption);
            switch (sConversationListOption) {
            case OPTION_CONVERSATION_LIST_ALL:
            case OPTION_CONVERSATION_LIST_XMS:
            case OPTION_CONVERSATION_LIST_JOYN:
                mEmptyView.setAllChatEmpty();
                break;
            case OPTION_CONVERSATION_LIST_GROUP_CHATS:
                mEmptyView.setGroupChatEmpty(true);
                break;
            case OPTION_CONVERSATION_LIST_SPAM:
                mEmptyView.setSpamEmpty(true);
                break;
            default:
                MmsLog.w(TAG, "unkown position!");
                break;
            }
        }
    }

    // a receiver to moniter the network status.
    private class NetworkStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = false;
            ConnectivityManager connManager =
                    (ConnectivityManager) getSystemService(ConversationList.this.CONNECTIVITY_SERVICE);
            State state = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
            if (State.CONNECTED == state) {
                success = true;
            }
            if (!success) {
                state = connManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).getState();
                if (State.CONNECTED == state) {
                    success = true;
                }
            }
            ConversationList.this.showInternetStatusBar(!success);
        }
    }

    private void showInternetStatusBar(boolean show) {
        if (show) {
            mNetworkStatusBar.setVisibility(View.VISIBLE);
        } else {
            mNetworkStatusBar.setVisibility(View.GONE);
        }
    }

    /// M: Fix bug ALPS00416081 @{
    private Menu mOptionsMenu;

    private void setDeleteMenuVisible(Menu menu) {
        if (menu != null) {
            MenuItem item = menu.findItem(R.id.action_delete_all);
            if (item != null) {
                mDataValid = mListAdapter.isDataValid();
                item.setVisible(mListAdapter.getCount() > 0 && mIsSmsEnabled);
            }
        }
    }
    /// @}

    /// M: redesign selection action bar and add shortcut in common version. @{
    private CustomMenu mCustomMenu;
    private DropDownMenu mSelectionMenu;
    private MenuItem mSelectionMenuItem;

    private void updateSelectionTitle() {
        if (mSelectionMenuItem != null) {
            if (mListAdapter.getCount() == mListAdapter.getSelectedThreadsList().size()) {
                mSelectionMenuItem.setTitle(R.string.unselect_all);
            } else {
                mSelectionMenuItem.setTitle(R.string.select_all);
            }
        }
    }
    /// @}

    /// Google JB MR1.1 patch. conversation list can restore scroll position
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        /// M: Optimize select all performance, save actionmode status. @{
        if (mActionMode != null && !sIsDeleting) {
            Log.d(TAG, "onSaveInstanceState: mActionMode not null");
            outState.putBoolean(ACTIONMODE, true);
            outState.putBoolean(NEED_RESTORE_ADAPTER_STATE, true);
            HashSet<Long> selectedThreadSet = mListAdapter.getSelectedThreadsList();
            Long[] selectList = (Long[]) selectedThreadSet.toArray(new Long[selectedThreadSet.size()]);
            long[] selectedThreadsList = new long[selectList.length];
            for (int i = 0; i < selectList.length; i++) {
                selectedThreadsList[i] = selectList[i].longValue();
            }
            outState.putLongArray(SELECT_THREAD_IDS, selectedThreadsList);
            Log.d(TAG, "onSaveInstanceState--selectThreadIds:" + selectedThreadsList.toString());
        }
        /// @}
        outState.putInt(LAST_LIST_POS, mSavedFirstVisiblePosition);
        outState.putInt(LAST_LIST_OFFSET, mSavedFirstItemOffset);
    }

    /// M: Optimize select all performance, restore actionmode status. @{
    @Override
    protected void  onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        if (state.getBoolean(ACTIONMODE, false)) {
            mListSelectedThreads = state.getLongArray(SELECT_THREAD_IDS);
            mActionMode = this.startActionMode(mActionModeListener);
        }
    }
    /// @}

    private long getExceptId() {
        long exceptID = 0;
        for (long id : DraftManager.sEditingThread) {
            MmsLog.d(TAG, "getExceptId() id = " + id);
            if (id > exceptID) {
                exceptID = id;
            }
        }
        return exceptID;
    }

    /// M: fix bug ALPS00998351, solute the issue "All of the threads still
    /// highlight after you back to all thread view". @{
    public boolean isActionMode() {
        return (mActionMode != null);
    }
    /// @}

    /**
     * M: add for ALPS01846474, dismiss option menu when AirPlane mode changed.
     */
    private final BroadcastReceiver mAirPlaneReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            closeOptionsMenu();
        }
    };

    private BroadcastReceiver mSubReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED)) {
                    if (!isFinishing() && mIsShowSIMIndicator) {
                        MmsLog.d(TAG, "Hide current indicator and show new one.");
                        StatusBarSelectorCreator creator = StatusBarSelectorCreator
                                .getInstance(ConversationList.this);
                        creator.updateStatusBarData();
                    }
            }
        }
    };
}
