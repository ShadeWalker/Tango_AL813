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

import java.util.List;
import java.util.Locale;

import com.android.mms.R;
import com.android.mms.data.CBMessage;
import com.android.mms.data.Contact;
import com.android.mms.data.Conversation;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Checkable;
import android.widget.QuickContactBadge;
import android.widget.RelativeLayout;
import android.widget.TextView;

/// M:
import android.provider.Telephony.Mms;
import android.text.TextUtils;
import android.widget.ImageView;
import android.util.AndroidException;
import android.widget.LinearLayout;

import com.android.mms.MmsApp;
import com.android.mms.R;
import com.android.mms.util.FeatureOption;

import com.android.mms.util.MmsLog;

import android.provider.Telephony;


/// M: add for ipmessage
import com.mediatek.ipmsg.util.IpMessageUtils;
import com.android.mms.MmsConfig;
import com.android.mms.MmsPluginManager;
import com.mediatek.mms.ext.IMmsConversationListItemExt;
import com.mediatek.mms.ext.IMmsUtilsExt;
import com.mediatek.mms.ipmessage.IpMessageConsts;
import com.mediatek.mms.ext.DefaultMmsConversationListItemExt;
import com.mediatek.common.MPlugin;
/*HQ_zhangjing add for al812 mms ui */
import android.widget.CheckBox;


/**
 * This class manages the view for given conversation.
 */
public class ConversationListItem extends RelativeLayout implements Contact.UpdateListener,
            Checkable {
    private static final String TAG = "ConversationListItem";
    private static final boolean DEBUG = false;

    private TextView mSubjectView;
    private TextView mFromView;
    private TextView mDateView;
    private View mAttachmentView;
    private ImageView mErrorIndicator;
    /// M:
    private MmsQuickContactBadge mAvatarView;
	/*HQ_zhangjing add for al812 mms ui */
    private CheckBox mCheckBox;

    private static Drawable sDefaultContactImage;

    // For posting UI update Runnables from other threads:
    private Handler mHandler = new Handler();

    private Conversation mConversation;

    public static final StyleSpan STYLE_BOLD = new StyleSpan(Typeface.BOLD);

    private Context mContext;
    /// M: add for new common feature.
    private View mMuteView;
    private TextView mUnreadView;
    private static final int MAX_UNREAD_MESSAGES_COUNT = 999;
    private static final String MAX_UNREAD_MESSAGES_STRING = "999+";
    private static final int MAX_READ_MESSAGES_COUNT = 9999;
    private static final String MAX_READ_MESSAGES_STRING = "9999+";

    private boolean mSubjectSingleLine;

    /// M: OP09 Plug-in. @{
    private IMmsConversationListItemExt mMmsConversationListItemPlugin = null;
    /// @}

    /// M: New feature for rcse, adding IntegrationMode. @{
    private ImageView mFullIntegrationmModeView;
    /// @}

    public ConversationListItem(Context context) {
        super(context);
        mContext = context;
    }

    public ConversationListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        if (sDefaultContactImage == null) {
            sDefaultContactImage = context.getResources().getDrawable(R.drawable.ic_contact_picture);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        initPlugin(this.getContext());

        mFromView = (TextView) findViewById(R.id.from);
        mSubjectView = (TextView) findViewById(R.id.subject);
        mMmsConversationListItemPlugin.setViewSize(mSubjectView);
        mDateView = (TextView) findViewById(R.id.date);
        mAttachmentView = findViewById(R.id.attachment);
        mErrorIndicator = (ImageView) findViewById(R.id.error);
        mAvatarView = (MmsQuickContactBadge) findViewById(R.id.avatar);
        /// M: add for ipmessage
        mMuteView = findViewById(R.id.mute);
        mUnreadView = (TextView) findViewById(R.id.unread);
		/*HQ_zhangjing add for al812 mms ui */
		mCheckBox = (CheckBox)findViewById(R.id.select_check_box);
        /// M: New feature for rcse, adding IntegrationMode. @{
        mFullIntegrationmModeView = (ImageView) findViewById(R.id.fullintegrationmode);
        /// @}
    }

    public Conversation getConversation() {
        return mConversation;
    }

    /**
     * Only used for header binding.
     */
    public void bind(String title, String explain) {
        mFromView.setText(title);
        mSubjectView.setText(explain);
    }

    private CharSequence formatMessage() {
        final int color = android.R.styleable.Theme_textColorSecondary;
        /// M: Code analyze 029, For new feature ALPS00111828, add CellBroadcast feature . @{
        String from = null;
        String langage = Locale.getDefault().getLanguage();//add by lipeng for number display
        if (mConversation.getType() == Telephony.Threads.CELL_BROADCAST_THREAD) {
            int channelId = 0;
            if (mConversation.getRecipients().size() == 0) {
                return null;
            }
            MmsLog.e(TAG, "recipients = " + mConversation.getRecipients().formatNames(", "));
            String number = mConversation.getRecipients().get(0).getNumber();
            if (!TextUtils.isEmpty(number)) {
                try {
                    channelId = Integer.parseInt(number);
                } catch (NumberFormatException e) {
                    MmsLog.e(TAG, "format number error!");
                }
            }

            String name = "";
            List<SubscriptionInfo> subInfoList = SubscriptionManager.from(mContext).getActiveSubscriptionInfoList();
            int subCount = (subInfoList != null && !subInfoList.isEmpty()) ? subInfoList.size() : 0;
            for (int i = 0; i < subCount; i++) {
                name = CBMessage.getCBChannelName(subInfoList.get(i).getSubscriptionId(), channelId);
                if (name != mContext.getString(R.string.cb_default_channel_name)) {
                    break;
                }
            }

            if (TextUtils.isEmpty(name)) {
                name = MmsApp.getApplication().getApplicationContext()
                        .getString(R.string.cb_default_channel_name);
            }
            try {
                from = name + "(" + channelId + ")";
            } catch (NumberFormatException e) {
                MmsLog.e(TAG, "format recipient number error!");
            }
        } else {
            /// M: add for ip message
            if (MmsConfig.isServiceEnabled(mContext) && mConversation.getRecipients().size() == 1) {
                Contact contact = mConversation.getRecipients().get(0);
                MmsLog.d("avatar", "ConvListItem.formatMessage(): number = " + contact.getNumber()
                    + ", name = " + contact.getName());
                if (contact.getNumber().startsWith(IpMessageConsts.GROUP_START)
                        || contact.getNumber().startsWith(IpMessageConsts.JOYN_START)) {
                    if (mConversation.getThreadId() == 0) {
                        contact.setThreadId(mConversation.getThreadId());
                    }
                    if (TextUtils.isEmpty(contact.getName()) || contact.getNumber().equals(contact.getName())) {
                        contact.setName(
                            IpMessageUtils.getContactManager(mContext).getNameByThreadId(mConversation.getThreadId()));
                    }
                    MmsLog.d("avatar", "ConvListItem.formatMessage(): number = " + contact.getNumber()
                        + ", group name = " + contact.getName());
                }
            }
            //from = mConversation.getRecipients().formatNames(", ");
			// modify by lipeng for number display
			if (MessageUtils.isInteger(mConversation.getRecipients().formatNames(", ")
					.toString())&&(langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw"))) {
				from = "\u202D"
						+ mConversation.getRecipients().formatNames(", ")
						+ "\u202C";
			} else {
				from = mConversation.getRecipients().formatNames(", ");
			}// end lipeng
        }

        if (TextUtils.isEmpty(from)) {
            from = mContext.getString(android.R.string.unknownName);
        }
        /// @}

        SpannableStringBuilder buf = new SpannableStringBuilder(from);

        /// M:
        int before = buf.length();
        if (!mConversation.hasUnreadMessages()) {
            if (mConversation.getMessageCount() > 1) {
                MmsLog.d("unread", "formatMessage(): Thread " + mConversation.getThreadId() + " has no unread message.");
                int count = mConversation.getMessageCount();
                /// M: add for op09 @{
                if (MmsConfig.isMassTextEnable()) {
                    int opCount = mMmsConversationListItemPlugin.getMessageCountAndShowSimType(this.getContext(),
                            mConversation.getUri(), (ImageView) findViewById(R.id.sim_type_conv),
                            mConversation.getRecipients().size(), mConversation.hasDraft());
                    MmsLog.d(TAG, "opCount: readCount:" + opCount);
                    if (opCount > 0) {
                        count = opCount;
                    }
                }
                /// @}
                if (count > MAX_READ_MESSAGES_COUNT) {
                    buf.append("  " + MAX_READ_MESSAGES_STRING);
                } else {
                    buf.append("  " +"(" + count + ")");
                }
                buf.setSpan(new ForegroundColorSpan(mContext.getResources().getColor(R.color.message_count_color)),
                        before, buf.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            } else if (MmsConfig.isMassTextEnable() && mConversation.getMessageCount() <= 1) {
                mMmsConversationListItemPlugin.getMessageCountAndShowSimType(this.getContext(),
                    mConversation.getUri(), (ImageView) findViewById(R.id.sim_type_conv),
                    mConversation.getRecipients().size(), mConversation.hasDraft());
            }
        }
/*HQ_zhangjing modified for CQ HQ01355691 begin*/
/** M: Remove Google default code*/
        if (mConversation.hasDraft()) {
           // buf.append(mContext.getResources().getString(R.string.draft_separator));
            int beforeLen = buf.length();
            int size;
            buf.append(",  " + mContext.getResources().getString(R.string.has_draft));
            size = android.R.style.TextAppearance_Small;
            buf.setSpan(new TextAppearanceSpan(mContext, size, color), beforeLen + 1,
                    buf.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            buf.setSpan(new ForegroundColorSpan(
                    mContext.getResources().getColor(R.drawable.text_color_red)),
                    beforeLen + 1, buf.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        }
/*HQ_zhangjing modified for CQ HQ01355691 end*/
        // Unread messages are shown in bold
        if (mConversation.hasUnreadMessages()) {
			/*HQ_zhangjing add for CQ HQ01387629 begin*/
            buf.setSpan(new ForegroundColorSpan(
                    mContext.getResources().getColor(R.drawable.text_color_red)), 
			/*HQ_zhangjing add for CQ HQ01387629 end*/
                    0, buf.length(),
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        }

        return buf;
    }

    /// M: add for ipmessage,fix bug ALPS01608034 @{
    private void updateGroupAvatarView(Drawable drawable) {
        Contact contact = mConversation.getRecipients().get(0);
        mAvatarView.setImageDrawable(drawable);
        mAvatarView.setVisibility(View.GONE);//HQ_zhangjing add for al812 mms ui
        mAvatarView.setThreadId(mConversation.getThreadId());
        mAvatarView.setGroupAvator(true);
        mAvatarView.assignContactUri(contact.getUri());
    }
    /// @}


    private void updateAvatarView() {
        Drawable avatarDrawable;
        mAvatarView.setGroupAvator(false);
        if (mConversation.getRecipients().size() == 1) {
            final Contact contact = mConversation.getRecipients().get(0);
            // / M: add for ipmessage
            boolean isGroup = contact.getNumber().startsWith(
                    IpMessageConsts.GROUP_START);
            if (Build.TYPE.equals("eng")) {
                MmsLog.d("avatar", "ConvListItem.updateAvatarView(): isGroup = "
                        + isGroup + ", number = " + contact.getNumber());
            }
            /// M: add for ipmessage,fix bug ALPS01608034 @{
            if (isGroup) {
                MmsLog.d("avatar",
                        "ConvListItem.updateAvatarView(): get avatart by threadId, threadId = "
                                + mConversation.getThreadId());
                Handler avatarHandler = new Handler() {
                    public void handleMessage(Message msg) {
                        if (msg.what > 0) {
                            Drawable drawable = contact.getGroupAvatar();
                            if (null != drawable) {
                                updateGroupAvatarView(drawable);
                                MmsLog
                                        .d("avatar",
                                                "ConvListItem.updateAvatarView(): set group avatar.");
                                return;
                            }
                        }
                    }
                };

                avatarDrawable = contact.getGroupAvatar(avatarHandler, mContext,
                        mConversation.getThreadId());
                MmsLog.d("avatar",
                        "ConvListItem.updateAvatarView(): bitmap is null ?= "
                                + (null == avatarDrawable));
                if (avatarDrawable != null) {
                    updateGroupAvatarView(avatarDrawable);
                }
            }
            /// @}
            avatarDrawable = contact.getAvatar(mContext, sDefaultContactImage, mConversation.getThreadId());

            /// M: fix bug ALPS00400483, same as 319320, clear all data of mAvatarView firstly. @{
            mAvatarView.assignContactUri(null);
            /// @}

            /// M: Code analyze 030, For new feature ALPS00241750, Add email address
            /// to email part in contact . @{
            String number = contact.getNumber();
            // add for joyn converged inbox mode
            if (number.startsWith(IpMessageConsts.JOYN_START)) {
                number = number.substring(4);
            }
            if (Mms.isEmailAddress(number)) {
                mAvatarView.assignContactFromEmail(number, true);
            } else {
                if (contact.existsInDatabase()) {
                    mAvatarView.assignContactUri(contact.getUri());
                } else {
                    mAvatarView.assignContactFromPhone(number, true);
                }
                /// @}
            }
        } else {
            // TODO get a multiple recipients asset (or do something else)
            avatarDrawable = sDefaultContactImage;
            mAvatarView.assignContactUri(null);
        }
        mAvatarView.setImageDrawable(avatarDrawable);
        mAvatarView.setVisibility(View.GONE);//HQ_zhangjing add for al812 mms ui
    }

    private void updateFromView() {
        mFromView.setText(formatMessage());
        updateAvatarView();
    }

    private Runnable mUpdateFromViewRunnable = new Runnable() {
        public void run() {
            updateFromView();
        }
    };

    public void onUpdate(Contact updated) {
        //if (Log.isLoggable(LogTag.CONTACT, Log.DEBUG)) {
        if (Build.TYPE.equals("eng")) {
            Log.v(TAG, "onUpdate: " + this + " contact: " + updated);
        }
        //}
        /// M: fix blank screen issue. if there are 1000 threads, 1 recipient each thread,
        /// and 8 list items in each screen, onUpdate() will be called 8000 times.
        /// mUpdateFromViewRunnable run in UI thread will blocking the other things.
        /// remove blocked mUpdateFromViewRunnable.
        mHandler.removeCallbacks(mUpdateFromViewRunnable);
        mHandler.post(mUpdateFromViewRunnable);
    }

    public final void bind(Context context, final Conversation conversation) {
        //if (DEBUG) Log.v(TAG, "bind()");

        mConversation = conversation;

        updateBackground(conversation);
        String langage = Locale.getDefault().getLanguage();//add by lipeng for number display
        /// M: update read view
        if (mConversation.hasUnreadMessages()) {
            int unreadCount = mConversation.getUnreadMessageCount();
            /// M: add for op09 @{
            if (MmsConfig.isMassTextEnable()) {
                int opCount = mMmsConversationListItemPlugin.getMessageCountAndShowSimType(this.getContext(),
                        mConversation.getUri(), (ImageView) findViewById(R.id.sim_type_conv),
                        mConversation.getRecipients().size(), mConversation.hasDraft());
                MmsLog.d(TAG, "unReadMessageCount:" + opCount);
                if (opCount > 0) {
                    unreadCount = opCount;
                }
            }
            /// @}
            String unreadString = null;
            if (unreadCount > MAX_UNREAD_MESSAGES_COUNT) {
                unreadString = MAX_UNREAD_MESSAGES_STRING;
            } else {
                unreadString = "" + unreadCount;
            }
			//HQ_zhangjing add for CQ HQ01387629 
            mUnreadView.setText("(" + unreadString + ")");
            mUnreadView.setVisibility(View.VISIBLE);
        } else {
            mUnreadView.setVisibility(View.GONE);
        }

//        LayoutParams attachmentLayout = (LayoutParams)mAttachmentView.getLayoutParams();
        boolean hasError = conversation.hasError();
        // When there's an error icon, the attachment icon is left of the error icon.
        // When there is not an error icon, the attachment icon is left of the date text.
        // As far as I know, there's no way to specify that relationship in xml.
        /// M @{
        // if (hasError) {
        //     attachmentLayout.addRule(RelativeLayout.LEFT_OF, R.id.error);
        // } else {
        //     attachmentLayout.addRule(RelativeLayout.LEFT_OF, R.id.date);
        // }
        /// @}

        boolean hasAttachment = conversation.hasAttachment();
        mAttachmentView.setVisibility(hasAttachment ? VISIBLE : GONE);

        /// M: Code analyze 031, For bug ALPS00235723, The crolling performance of message . @{
        // Date
        mDateView.setVisibility(GONE);//HQ_zhangjing add for al812 mms ui
        /// M:
        if (ConversationList.sConversationListOption == ConversationList.OPTION_CONVERSATION_LIST_SPAM
                && mConversation.isSpam()) {
            mDateView.setText(MessageUtils.formatTimeStampStringExtend(context, conversation.getSpamDate()));
        } else {
            /// M: Change for OP09 feature. @{
            IMmsUtilsExt mMmsUtilsPlugin  = (IMmsUtilsExt) MmsPluginManager.getMmsPluginObject(MmsPluginManager.MMS_PLUGIN_TYPE_MESSAGE_UTILS);
            String dateStr = MessageUtils.formatTimeStampStringExtend(context, conversation.getDate());
            if (MmsConfig.isFormatDateAndTimeStampEnable()) {
                dateStr = mMmsUtilsPlugin.formatDateAndTimeStampString(context, conversation.getDate(), conversation
                        .getDateSent(), false, dateStr);
            }
            mDateView.setText(dateStr);
            /// @}
        }

        // From.
        mFromView.setVisibility(VISIBLE);
        if (mConversation.getType() == Telephony.Threads.IP_MESSAGE_GUIDE_THREAD) {
            // this is ipmessage guide thread
            mFromView.setText(IpMessageUtils.getResourceManager(context)
                .getSingleString(IpMessageConsts.string.ipmsg_service_title));
        } else {
            mFromView.setText(formatMessage());
        /// @}

        /// M: this local variable has never been used. delete google default code.
        // Register for updates in changes of any of the contacts in this conversation.
        // ContactList contacts = conversation.getRecipients();

            if (DEBUG) {
                Log.v(TAG, "bind: contacts.addListeners " + this);
            }
            Contact.addListener(this);
        }
        /// M: New feature for rcse, adding IntegrationMode. @{
        if (MmsConfig.isActivated(context) && mConversation.getIsFullIntegrationMode()) {
            mFullIntegrationmModeView.setVisibility(View.VISIBLE);
            mFullIntegrationmModeView.setBackgroundDrawable(IpMessageUtils.getResourceManager(context)
                    .getSingleDrawable(IpMessageConsts.drawable.ipmsg_full_integrated));
        } else {
            mFullIntegrationmModeView.setVisibility(View.GONE);
        }

        /// @}
        // Subject
//        SmileyParser parser = SmileyParser.getInstance();
//        mSubjectView.setText(parser.addSmileySpans(conversation.getSnippet()));

        if (mSubjectSingleLine) {
            mSubjectView.setSingleLine(true);
        }
        /// M: Code analyze 031, For bug ALPS00235723, The crolling performance of message . @{
        mSubjectView.setVisibility(VISIBLE);
        /// @}
        /// M:
        if (mConversation.getTyping()) {
            mSubjectView.setText(IpMessageUtils.getResourceManager(context)
                .getSingleString(IpMessageConsts.string.ipmsg_typing));
        } else if (mConversation.getType() == Telephony.Threads.IP_MESSAGE_GUIDE_THREAD) {
            mSubjectView.setText(IpMessageUtils.getResourceManager(context)
                .getSingleString(IpMessageConsts.string.ipmsg_introduction));
        } else {
            //mSubjectView.setText(conversation.getSnippet());
			// modify by lipeng for number display
			if (conversation.getSnippet() != null
					&& MessageUtils.isInteger(conversation.getSnippet())&&(langage.startsWith("ar")||langage.startsWith("fa")||langage.startsWith("iw"))) {
				mSubjectView.setText("\u202D" + conversation.getSnippet()
						+ "\u202C");
			} else {
				mSubjectView.setText(conversation.getSnippet());
			}// end by lipeng
        }
 //       LayoutParams subjectLayout = (LayoutParams)mSubjectView.getLayoutParams();
 //       // We have to make the subject left of whatever optional items are shown on the right.
 //       subjectLayout.addRule(RelativeLayout.LEFT_OF, hasAttachment ? R.id.attachment :
  //          (hasError ? R.id.error : R.id.date));

        // Transmission error indicator.
        /// M: Code analyze 032, For new feature ALPS00347707, add for wap push error icon . @{
        if (FeatureOption.MTK_WAPPUSH_SUPPORT && hasError) {
            if (conversation.getType() == Telephony.Threads.WAPPUSH_THREAD) {
                mErrorIndicator.setImageResource(R.drawable.alert_wappush_si_expired);
            } else {
                mErrorIndicator.setImageResource(R.drawable.ic_list_alert_sms_failed);
            }
        }
        /// @}
        mErrorIndicator.setVisibility(hasError ? VISIBLE : GONE);

        /// M: add for ipmessage
        if (mConversation.getType() == Telephony.Threads.IP_MESSAGE_GUIDE_THREAD) {
            mAvatarView.assignContactUri(null);
            //Drawable image = context.getResources().getDrawable(R.drawable.ipmsg_service);
            Drawable image = IpMessageUtils.getResourceManager(context)
                                        .getSingleDrawable(IpMessageConsts.drawable.ipmsg_service);
            mAvatarView.setImageDrawable(image);
            mAvatarView.setVisibility(View.GONE);//HQ_zhangjing add for al812 mms ui
        } else {
            updateAvatarView();
            /// M:
            mMuteView.setVisibility(View.GONE);
            if (conversation.isMute()) {
                mMuteView.setVisibility(View.VISIBLE);
            }
        }

        ///M: add for cmcc, show draft icon when conversation has draft.  @{
        mMmsConversationListItemPlugin.showDraftIcon(this.getContext(),
                mConversation.hasDraft(), (LinearLayout) findViewById(R.id.iconlist));
        ///}@
    }

    private void updateBackground(Conversation conversation) {
        int backgroundId;
		//HQ_zhangjing add for al812 mms ui
		mCheckBox.setChecked(false);
        /// M: fix bug ALPS00998351, solute the issue "All of the threads still
        /// highlight after you back to all thread view". @{
        ConversationList conversationList = (ConversationList) ConversationList.getContext();
        /// @}
		/*HQ_zhangjing add for al812 mms ui begin*/
		/*
        if (conversationList.isActionMode() && conversation.isChecked()) {
            backgroundId = R.drawable.list_selected_holo_light;
        } else if (conversation.hasUnreadMessages()) {
            backgroundId = R.drawable.conversation_item_background_unread;
        } else {
            backgroundId = R.drawable.conversation_item_background_read;
        }
        Drawable background = mContext.getResources().getDrawable(backgroundId);

        setBackgroundDrawable(background);
		*/
        if (conversation.isChecked()) {
            mCheckBox.setChecked(true);
        }		
		/*HQ_zhangjing add for al812 mms ui end*/
    }

    public final void unbind() {
        if (DEBUG) {
            Log.v(TAG, "unbind: contacts.removeListeners " + this);
        }
        // Unregister contact update callbacks.
        Contact.removeListener(this);
    }
    public void setChecked(boolean checked) {
        mConversation.setIsChecked(checked);
        updateBackground(mConversation);
    }

    public boolean isChecked() {
        return mConversation.isChecked();
    }

    public void toggle() {
        mConversation.setIsChecked(!mConversation.isChecked());
    }

    /// M: Code analyze 031, For bug ALPS00235723, The crolling performance of message . @{
    public void bindDefault(Conversation conversation) {
        MmsLog.d(TAG, "bindDefault().");
        if (conversation  != null) {
            updateBackground(conversation);
        }
        mAttachmentView.setVisibility(GONE);
        mDateView.setVisibility(View.GONE);
        mFromView.setText(R.string.refreshing);
        mSubjectView.setVisibility(GONE);
        mErrorIndicator.setVisibility(GONE);
        mAvatarView.setImageDrawable(sDefaultContactImage);
        /// M:
        mMuteView.setVisibility(View.GONE);
    }
    /// @}

    /// M: Code analyze 029, For new feature ALPS00111828, add CellBroadcast feature . @{
    private  String parseNumberForCb(String address) {
        StringBuilder builder = new StringBuilder();
        int len = address.length();

        for (int i = 0; i < len; i++) {
            char c = address.charAt(i);
            if (Character.isDigit(c)) {
                builder.append(c);
            }
        }
        return builder.toString();
    }
    /// @}

    public void setSubjectSingleLineMode(boolean value) {
        mSubjectSingleLine = value;
    }

    /// M: Make sure listeners are removed so that ConversationList instance can be released @{
    @Override
    protected void onDetachedFromWindow() {
        Log.v(TAG, "onDetachedFromWindow!!!");
        super.onDetachedFromWindow();
        Contact.removeListener(this);
    }
    /// @}

    /**
     * M: init Plugin
     *
     * @param context
     */
    private void initPlugin(Context context) {
       mMmsConversationListItemPlugin = (IMmsConversationListItemExt) 
            MPlugin.createInstance(IMmsConversationListItemExt.class.getName(),context);
       MmsLog.d(TAG, "operator mMmsConversationListItemPlugin = "
                + mMmsConversationListItemPlugin);
       if(mMmsConversationListItemPlugin == null){ 
            mMmsConversationListItemPlugin = new DefaultMmsConversationListItemExt(context);
            MmsLog.d(TAG, "default mMmsConversationListItemPlugin = "
                + mMmsConversationListItemPlugin);
        }
    }
}
